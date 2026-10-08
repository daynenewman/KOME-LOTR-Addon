package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import kome.common.data.*;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.quest.LOTRMiniQuest;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** Master acceptance carries the displayed offer UUID; native bool/entity packets cannot replay it. */
public final class KOMEPacketMasterOfferResponse implements IMessage {
    private int entityId;private String offer="";private boolean accepted;
    public KOMEPacketMasterOfferResponse(){}
    public KOMEPacketMasterOfferResponse(int entityId,String offer,boolean accepted){this.entityId=entityId;this.offer=offer;this.accepted=accepted;}
    public void fromBytes(ByteBuf b){entityId=b.readInt();offer=ByteBufUtils.readUTF8String(b);accepted=b.readBoolean();}
    public void toBytes(ByteBuf b){b.writeInt(entityId);ByteBufUtils.writeUTF8String(b,offer);b.writeBoolean(accepted);}
    public static class Handler implements IMessageHandler<KOMEPacketMasterOfferResponse,IMessage>{
        public IMessage onMessage(KOMEPacketMasterOfferResponse message,MessageContext context){
            EntityPlayerMP player=context.getServerHandler().playerEntity;Entity entity=player.worldObj.getEntityByID(message.entityId);
            if(!(entity instanceof LOTREntityNPC))return null;LOTREntityNPC npc=(LOTREntityNPC)entity;
            LOTRMiniQuest current=npc.questInfo==null?null:npc.questInfo.getOfferFor(player);
            if(!(current instanceof KOMESerfdomOfferQuest)||current.questUUID==null||!current.questUUID.toString().equals(message.offer)){
                player.addChatMessage(new ChatComponentText("That offer of service has expired."));return null;
            }
            KOMEProgressionOfferBridge.handleResponse(npc.questInfo,player,message.accepted);return null;
        }
    }
}
