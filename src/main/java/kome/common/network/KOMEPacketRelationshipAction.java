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
    public static void sendHub(EntityPlayerMP p,LOTREntityNPC n,int r){KOMEPlayerProgression progression=KOMEWorldData.get(p.worldObj).getProgression(p.getUniqueID());boolean allowService=r==MASTER||progression.getCanonicalRank()==KOMEProgressionRank.SERF;KOMEPacketHandler.network.sendTo(new KOMEPacketRelationshipHub(n.getEntityId(),r,n.getNPCName(),n.getFaction()==null?"":n.getFaction().factionName(),allowService),p);}

    public static class Handler implements IMessageHandler<KOMEPacketRelationshipAction,IMessage> {
        public IMessage onMessage(KOMEPacketRelationshipAction m,MessageContext c) {
            EntityPlayerMP p=c.getServerHandler().playerEntity;
            Entity e=KOMEReflection.getWorld(p).getEntityByID(m.entityId);
            if(!(e instanceof LOTREntityNPC)||!e.isEntityAlive()||p.getDistanceSqToEntity(e)>64){p.addChatMessage(new ChatComponentText("That relationship NPC is no longer available."));return null;}
            LOTREntityNPC n=(LOTREntityNPC)e;
            KOMEWorldData data=KOMEWorldData.get(p.worldObj);
            KOMEPlayerProgression progression=data.getProgression(KOMEReflection.getEntityUUID(p));
            KOMESerfKnightProgression s=progression.getSerfKnightProgression();
            boolean exact=m.relationship==MASTER?s.getSerfdomMaster().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(n)):s.getLiege().hasSameIdentity(KOMEProgressionNpcRankService.referenceOf(n));
            boolean prospectiveTrialRequest=m.relationship==LIEGE&&m.action==SERVICE&&KOMEProgressionOfferBridge.canRequestLiegeOfferFrom(p,n);
            if(!exact&&!prospectiveTrialRequest){p.addChatMessage(new ChatComponentText("That is not your current relationship NPC."));return null;}
            if(m.action==LEAVE){
                KOMEProgressionEncounterCleanup.cleanup(p,progression);
                KOMESerfKnightService.Result result=m.relationship==MASTER?KOMESerfKnightService.leaveSerfdomMaster(s):KOMESerfKnightService.leaveLiege(progression);
                if(!result.success){p.addChatMessage(new ChatComponentText(result.reason));return null;}
                KOMEProgressionNpcRoles.syncPlayer(data,p.getUniqueID());
                data.markDirty();KOMEProgressionAutoCompleter.syncPlayer(p,progression);
                p.addChatMessage(new ChatComponentText(m.relationship==MASTER?"You are no longer serving your Master.":"You are no longer pledged to your Liege."));return null;
            }
            if(m.action==TALK){n.interactFirst(p);return null;}
            if(m.action!=SERVICE){p.addChatMessage(new ChatComponentText("Unknown relationship action."));return null;}
            if(m.relationship==MASTER){KOMEPacketSerfdomMasterAction.sendMenu(p,n);return null;}
            if(KOMELiegeProgressionInteraction.ownsQuest(p,n)&&KOMELiegeProgressionInteraction.quest(p,n))return null;
            if(KOMEProgressionOfferBridge.canReplaceLiegeFrom(p,n)){KOMEProgressionOfferBridge.openStandingTrialOffer(p,n);return null;}
            p.addChatMessage(new ChatComponentText("Complete service with your current superior. Separate promotion trials have been retired."));
            return null;
        }
    }
}