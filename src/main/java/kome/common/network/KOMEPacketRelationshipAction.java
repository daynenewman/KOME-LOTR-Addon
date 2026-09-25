package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.*;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

public class KOMEPacketRelationshipAction implements IMessage {
    public static final int MASTER=0,LIEGE=1,TALK=0,SERVICE=1,LEAVE=2;
    public int entityId,relationship,action;
    public KOMEPacketRelationshipAction() {}
    public KOMEPacketRelationshipAction(int i,int r,int a){entityId=i;relationship=r;action=a;}
    public void fromBytes(ByteBuf b){entityId=b.readInt();relationship=b.readByte();action=b.readByte();}
    public void toBytes(ByteBuf b){b.writeInt(entityId);b.writeByte(relationship);b.writeByte(action);}
    public static void sendHub(EntityPlayerMP p,LOTREntityNPC n,int r){KOMEPacketHandler.network.sendTo(new KOMEPacketRelationshipHub(n.getEntityId(),r,n.getNPCName(),n.getFaction()==null?"":n.getFaction().factionName()),p);}

    public static class Handler implements IMessageHandler<KOMEPacketRelationshipAction,IMessage> {
        public IMessage onMessage(KOMEPacketRelationshipAction m,MessageContext c) {
            EntityPlayerMP p=c.getServerHandler().playerEntity;
            Entity e=KOMEReflection.getWorld(p).getEntityByID(m.entityId);
            if(!(e instanceof LOTREntityNPC)||!e.isEntityAlive()||p.getDistanceSqToEntity(e)>64){p.addChatMessage(new ChatComponentText("That relationship NPC is no longer available."));return null;}
            LOTREntityNPC n=(LOTREntityNPC)e;
            KOMEWorldData data=KOMEWorldData.get(p.worldObj);
            KOMEPlayerProgression progression=data.getProgression(KOMEReflection.getEntityUUID(p));
            KOMESerfKnightProgression s=progression.getSerfKnightProgression();
            boolean exact=m.relationship==MASTER?s.getSerfdomMaster().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(n)):s.getProspectiveLiege().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(n));
            if(!exact){p.addChatMessage(new ChatComponentText("That is not your current relationship NPC."));return null;}
            if(m.action==LEAVE){
                KOMESerfKnightTrialAssignment assignment=s.getTrialAssignment();
                KOMESerfKnightService.Result result=m.relationship==MASTER?KOMESerfKnightService.leaveSerfdomMaster(s):KOMESerfKnightService.leaveProspectiveLiege(s);
                if(!result.success){p.addChatMessage(new ChatComponentText(result.reason));return null;}
                KOMESerfKnightEscortService.cleanup(p,assignment);KOMESerfKnightRecoveryService.cleanup(p,assignment);KOMESerfKnightDefenseService.cleanup(p,assignment);
                KOMEProgressionNpcRoles.syncPlayer(data,p.getUniqueID());
                data.markDirty();KOMEProgressionAutoCompleter.syncPlayer(p,progression);
                p.addChatMessage(new ChatComponentText(m.relationship==MASTER?"You are no longer serving your Serfdom Master.":"You are no longer pledged to your prospective liege."));return null;
            }
            if(m.action==TALK){n.interactFirst(p);return null;}
            if(m.action!=SERVICE){p.addChatMessage(new ChatComponentText("Unknown relationship action."));return null;}
            if(m.relationship==MASTER){KOMEPacketSerfdomMasterAction.sendMenu(p,n);return null;}
            if(!validLiegeService(p,progression,data,n)){p.addChatMessage(new ChatComponentText("That prospective liege relationship is no longer valid."));return null;}
            if(s.getTrialId().length()!=0){
                if("recovery".equals(s.getTrialId())) {
                    if(!KOMESerfKnightRecoveryService.deliver(p,progression,n)) KOMESerfKnightRecoveryService.activate(p,progression,n);
                } else if("defense".equals(s.getTrialId())) KOMESerfKnightDefenseService.activate(p,progression,n); else KOMESerfKnightEscortService.activate(p,progression,n);
                if(!s.isTrialCompleted()) KOMEProgressionNpcSpeech.say(p,n,KOMESerfKnightService.existingTrialSpeech(s.getTrialAssignment()));
                return null;
            }
            long day=KOMESerfKnightService.calendarDayNow();
            if(!KOMESerfKnightService.mayIssueAssignment(s,day,p.getUniqueID())){KOMEProgressionNpcSpeech.say(p,n,"You have done enough for one day. Return when I have work for you.");return null;}
            // The player-aware overload preserves the canonical assignTrial(s,p.worldObj.rand,day) route while adding only the runtime test identity.
            KOMESerfKnightService.Result result=KOMESerfKnightService.assignTrial(s,p.worldObj.rand,day,p.getUniqueID());
            if(!result.success){p.addChatMessage(new ChatComponentText(result.reason));return null;}
            if("recovery".equals(s.getTrialId())) KOMESerfKnightRecoveryService.activate(p,progression,n); else if("defense".equals(s.getTrialId())) KOMESerfKnightDefenseService.activate(p,progression,n); else KOMESerfKnightEscortService.activate(p,progression,n); data.markDirty(); KOMEProgressionAutoCompleter.syncPlayer(p,progression);
            KOMEProgressionNpcSpeech.say(p,n,KOMESerfKnightService.trialSpeech(s.getTrialAssignment()));
            return null;
        }
        private static boolean validLiegeService(EntityPlayerMP p,KOMEPlayerProgression progression,KOMEWorldData data,LOTREntityNPC n){
            KOMESerfKnightProgression s=progression.getSerfKnightProgression();
            return progression.getCanonicalRank()==KOMEProgressionRank.SERF&&s.getSerfdomMaster().isSet()&&KOMESerfKnightService.allDutiesComplete(s)&&(!s.hasActiveAssignment()||s.getTrialId().length()!=0)&&
                !s.isLockedOut(KOMESerfKnightService.calendarDayNow())&&LOTRLevelData.getData(p).getPledgeFaction()!=null&&LOTRLevelData.getData(p).getPledgeFaction()==n.getFaction()&&
                KOMEProgressionFactionResolver.matches(s.getSerfdomMaster().factionKey,n.getFaction())&&!n.isChild()&&KOMEProgressionNpcRankService.isValidFactionNpc(n)&&KOMEProgressionLords.isCombatUnitHiringNpc(n)&&
                KOMEProgressionNpcRankService.effectiveRank(data,n)==KOMEProgressionNpcRank.LORD&&n.hiredNPCInfo!=null&&!n.hiredNPCInfo.isActive;
        }
    }
}
