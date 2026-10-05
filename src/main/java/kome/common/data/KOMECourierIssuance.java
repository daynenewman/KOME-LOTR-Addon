package kome.common.data;

import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.player.EntityPlayerMP;

/** One server-authoritative issuance transaction shared by clicks and packets. */
public final class KOMECourierIssuance {
    interface Dialogue {
        void say(EntityPlayerMP player,LOTREntityNPC npc,String text);
        default void completed(EntityPlayerMP player,LOTREntityNPC npc){say(player,npc,"Your correspondence has been accounted for.");}
        default void replacementMenu(EntityPlayerMP player,LOTREntityNPC npc){}
    }
    private static final Dialogue NATIVE_DIALOGUE=new Dialogue(){
        public void say(EntityPlayerMP player,LOTREntityNPC npc,String text){KOMEProgressionNpcSpeech.say(player,npc,text);}
        @Override public void completed(EntityPlayerMP player,LOTREntityNPC npc){KOMEProgressionNpcSpeech.completeCourier(player,npc);}
        @Override public void replacementMenu(EntityPlayerMP player,LOTREntityNPC npc){kome.common.network.KOMEPacketSerfdomMasterAction.sendMenu(player,npc);}
    };
    private KOMECourierIssuance() { }
    public static boolean initial(EntityPlayerMP p,KOMEWorldData world,LOTREntityNPC npc) {
        if(p==null||world==null||npc==null||p.worldObj.isRemote)return false;
        synchronized(world) {
            KOMESerfKnightProgression state=world.getProgression(p.getUniqueID()).getSerfKnightProgression();
            KOMESerfCourierAssignment a=active(state);if(a==null||!state.getSerfdomMaster().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc)))return false;
            if(a.documentIssued)return true;
            freezeCorrespondence(a,state.getSerfdomMaster(),npc);
            a.documentIssued=true;a.nextReplacementWorldTime=p.worldObj.getTotalWorldTime()+KOMESerfCourierAssignment.REPLACEMENT_COOLDOWN;
            if(KOMECourierService.dropMessageFromMaster(p,npc,a,state.getSerfdomMaster())==null)return false;
            state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,a.writeToNBT());world.markDirty();return true;
        }
    }
    public static boolean interact(EntityPlayerMP p,KOMEWorldData world,LOTREntityNPC npc) {
        return interact(p,world,npc,NATIVE_DIALOGUE);
    }
    public static boolean requestReplacement(EntityPlayerMP p,KOMEWorldData world,LOTREntityNPC npc) {
        return interact(p,world,npc,NATIVE_DIALOGUE,true);
    }
    /** Tests use inert dialogue, exercising the same world/state transaction without a Forge connection. */
    static boolean interact(EntityPlayerMP p,KOMEWorldData world,LOTREntityNPC npc,Dialogue dialogue) {
        return interact(p,world,npc,dialogue,false);
    }
    static boolean interact(EntityPlayerMP p,KOMEWorldData world,LOTREntityNPC npc,Dialogue dialogue,boolean replacementRequested) {
        if(p==null||world==null||npc==null||p.worldObj.isRemote)return false;
        synchronized(world) {
            KOMEPlayerProgression progression=world.getProgression(p.getUniqueID());
            KOMESerfKnightProgression state=progression.getSerfKnightProgression();
            KOMESerfCourierAssignment a=active(state);
            if(a==null||!state.getSerfdomMaster().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(npc))
                    ||!KOMESerfdomMasterService.validateCurrentMasterInteraction(p,world,npc,true).success)return false;
            if(KOMECourierService.reportToMaster(p,world,progression)){dialogue.completed(p,npc);return true;}
            if(a.stage==KOMESerfCourierAssignment.Stage.DELIVERED)return true;
            long now=p.worldObj.getTotalWorldTime();
            if(a.letterText.length()==0){freezeCorrespondence(a,state.getSerfdomMaster(),npc);save(world,state,a);}
            if(!a.documentIssued){initial(p,world,npc);return true;}
            if(KOMECourierService.hasDispatch(p,a,state.getSerfdomMaster())) {
                dialogue.say(p,npc,"Attend to the letter already entrusted to you.");
                return true;
            }
            if(!replacementRequested) {
                dialogue.say(p,npc,"My letter must reach its proper hands. If it is lost, ask me for a replacement.");
                dialogue.replacementMenu(p,npc);
                return true;
            }
            // Legacy duties have an issued revision-zero book, but no saved clock.
            if(a.nextReplacementWorldTime==0L){a.nextReplacementWorldTime=now+KOMESerfCourierAssignment.REPLACEMENT_COOLDOWN;save(world,state,a);}
            long remaining=a.replacementTicksRemaining(now);
            if(remaining>0){dialogue.say(p,npc,"Attend to the letter already entrusted to you. I will write another in "+cooldownText(remaining)+".");return true;}
            if(a.replacementDismisses(now)) {
                dialogue.say(p,npc,"I warned you plainly. Your service in my household is ended.");
                KOMECourierService.cleanup(p,a,state.getSerfdomMaster());
                KOMESerfKnightService.leaveSerfdomMaster(state);
                KOMEProgressionNpcRoles.syncPlayer(world,p.getUniqueID());world.markDirty();
                KOMEProgressionAutoCompleter.syncPlayer(p,progression);return true;
            }
            a.documentRevision++;a.replacements++;a.nextReplacementWorldTime=now+KOMESerfCourierAssignment.REPLACEMENT_COOLDOWN;
            if(KOMECourierService.dropMessageFromMaster(p,npc,a,state.getSerfdomMaster())==null)return true;
            save(world,state,a);
            dialogue.say(p,npc,warning(a.masterFactionKey,a.masterRole,a.replacements));
            KOMEProgressionAutoCompleter.syncPlayer(p,progression);return true;
        }
    }
    private static KOMESerfCourierAssignment active(KOMESerfKnightProgression state) {
        return "courier".equals(state.getActiveAssignmentKind())?KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData()):null;
    }
    private static void freezeCorrespondence(KOMESerfCourierAssignment a,KOMEProgressionNpcRef master,LOTREntityNPC npc){
        if(a.recipientName.length()==0&&a.recipient.isSet())a.recipientName=a.recipient.displayName;
        if(a.masterRole.length()==0)a.masterRole=KOMESerfProfessionClassifier.classify(npc).key;
        if(a.letterText.length()==0)a.letterText=KOMECourierCorrespondence.compose(a,master);
    }
    private static void save(KOMEWorldData world,KOMESerfKnightProgression state,KOMESerfCourierAssignment a){state.setDutyAssignmentData(KOMESerfKnightDutyType.COURIER,a.writeToNBT());world.markDirty();}
    public static String cooldownText(long ticks){long seconds=(ticks+19L)/20L;return (seconds/60L)+":"+(seconds%60L<10?"0":"")+(seconds%60L);}
    static String warning(String faction,String role,int count) {
        String key=KOMEAlliance.normalizeFactionKey(faction);
        String tone=key.equals("rohan")?"You carry my household's word."
            :key.equals("durinsfolk")||key.equals("bluemountains")?"A dwarf's word is worth more than careless hands."
            :key.equals("highelves")||key.equals("woodelf")||key.equals("lothlorien")?"Trust is not lightly given."
            :key.equals("mordor")||key.equals("angmar")||key.equals("gundabad")||key.equals("dolguldur")||key.equals("isengard")||key.equals("halftroll")?"I have no use for a careless servant."
            :key.equals("harad")||key.equals("morwaith")||key.equals("taurethrim")||key.equals("rhudel")?"You have been trusted with my name and word.":"My good name travels with that letter.";
        String work="smith".equals(role)?" I have work at the forge, not time to copy letters.":"vintner".equals(role)?" The vines cannot wait while I repeat this work.":"merchant".equals(role)?" My dealings depend on a faithful messenger.":"";
        return tone+work+(count==1?" This is displeasing. Take this fresh copy and guard it."
            :count==2?" Enough carelessness. I expect this second copy to reach its proper hands."
            :" This is your final warning and last replacement. Ask for another after this, and I will dismiss you from my service.");
    }
}
