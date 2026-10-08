package kome.common.data;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import lotr.common.entity.npc.LOTREntityNPC;

/** Current relationship owns Quest. Prospective relationships still use their existing offer. */
public final class KOMELiegeProgressionInteraction {
    public enum Action { COMMISSION, STATUS, REPORT, TRIAL, TRIAL_REPORT, UNAVAILABLE }
    private KOMELiegeProgressionInteraction() {}
    public static boolean ownsQuest(EntityPlayer player,LOTREntityNPC npc) {
        if(!KOMECurrentLiege.valid(player,npc))return false;
        KOMEProgressionRank rank=KOMEWorldData.get(player.worldObj).getProgression(player.getUniqueID()).getCanonicalRank();
        return rank==KOMEProgressionRank.SERF||rank==KOMEProgressionRank.KNIGHT;
    }
    public static Action action(KOMEPlayerProgression p,double alignment,String faction) {
        if(p==null||p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT)return Action.UNAVAILABLE;
        KOMELordshipTrial trial=p.getLordship().assignment();
        if(trial!=null)return trial.ready()?Action.TRIAL_REPORT:Action.TRIAL;
        KOMEKnightCommission a=p.getKnightService().assignment();
        if(a!=null)return a.stage==KOMEKnightCommission.Stage.READY_TO_REPORT?Action.REPORT:
            a.stage==KOMEKnightCommission.Stage.OFFERED||a.stage==KOMEKnightCommission.Stage.FAILED?Action.COMMISSION:Action.STATUS;
        if(KOMELordshipTrialService.prerequisites(p,alignment,faction))return Action.TRIAL;
        return p.getKnightService().qualifyingTypes(faction).size()<KOMEKnightCommission.Type.values().length?Action.COMMISSION:Action.UNAVAILABLE;
    }
    public static boolean quest(EntityPlayerMP player,LOTREntityNPC liege) {
        if(!ownsQuest(player,liege)||player.getDistanceSqToEntity(liege)>64)return false;
        KOMEWorldData world=KOMEWorldData.get(player.worldObj);
        synchronized(world){
            KOMEPlayerProgression p=world.getProgression(player.getUniqueID());
            if(p.getCanonicalRank()!=KOMEProgressionRank.KNIGHT)return false;
            Action action=action(p,lotr.common.LOTRLevelData.getData(player).getAlignment(liege.getFaction()),liege.getFaction().codeName());
            if(action==Action.TRIAL||action==Action.TRIAL_REPORT){
                if(KOMELordshipTrialService.prepare(player,liege)==null)KOMEProgressionNpcSpeech.say(player,liege,"I cannot arrange a safe trial here. Return when a suitable destination is available.");
                else KOMELordshipTrialService.acceptOrReport(player,liege);
            }else if(action==Action.COMMISSION||action==Action.STATUS||action==Action.REPORT){
                if(KOMEKnightCommissionService.prepare(player,liege)==null)KOMEProgressionNpcSpeech.say(player,liege,"I cannot arrange a safe commission here. Explore our lands and return after finding shelter among our people.");
                else KOMEKnightCommissionService.acceptOrReport(player,liege);
            }else KOMEProgressionNpcSpeech.say(player,liege,"You have fulfilled the service I can offer for now. Continue building your standing with our people.");
            KOMEProgressionAutoCompleter.syncPlayer(player,p);return true;
        }
    }
}
