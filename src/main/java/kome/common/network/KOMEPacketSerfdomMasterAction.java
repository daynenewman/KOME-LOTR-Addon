package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEPlayerProgression;
import kome.common.data.KOMEProgressionNpcRef;
import kome.common.data.KOMESerfKnightDutyType;
import kome.common.data.KOMESerfKnightProgression;
import kome.common.data.KOMESerfdomMasterService;
import kome.common.data.KOMEWorldData;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** Server-authoritative canonical Serfdom Master actions. */
public class KOMEPacketSerfdomMasterAction implements IMessage {
    public static final int SERVE=0, REQUEST_DUTY=1, VIEW_DUTY=2, HIGHLIGHT=3;
    public int entityId, action;
    public KOMEPacketSerfdomMasterAction() { }
    public KOMEPacketSerfdomMasterAction(int entityId,int action){this.entityId=entityId;this.action=action;}
    public void fromBytes(ByteBuf buf){entityId=buf.readInt();action=buf.readByte();}
    public void toBytes(ByteBuf buf){buf.writeInt(entityId);buf.writeByte(action);}
    public static void sendMenu(EntityPlayerMP player,LOTREntityNPC npc){
        KOMEWorldData data=KOMEWorldData.get(KOMEReflection.getWorld(player)); KOMEPlayerProgression progression=data.getProgression(KOMEReflection.getEntityUUID(player)); KOMESerfKnightProgression state=progression.getSerfKnightProgression(); KOMEProgressionNpcRef ref=kome.common.data.KOMEProgressionNpcRankService.referenceOf(npc);
        int mode=state.getSerfdomMaster().hasSameIdentity(ref)?1:(state.isMasterReplacementRequired()?2:(state.getSerfdomMaster().isSet()?3:0));
        String duty=dutyStatus(state); String faction=npc.getFaction()==null?"":npc.getFaction().factionName();
        KOMEPacketHandler.network.sendTo(new KOMEPacketSerfdomMasterMenu(npc.getEntityId(),npc.getNPCName(),faction,mode,duty),player);
    }
    private static String dutyStatus(KOMESerfKnightProgression state){String active=state.getActiveAssignmentKind();if(active.length()!=0){KOMESerfKnightDutyType type=KOMESerfKnightDutyType.forKey(active);return type==null?"Trial of Knighthood — assigned":type.displayName+" duty — assigned; details pending";}KOMESerfKnightDutyType next=kome.common.data.KOMESerfKnightService.nextDuty(state);if(next==null)return "All Serfdom duties complete.";return kome.common.data.KOMESerfKnightService.mayIssueAssignment(state,kome.common.data.KOMESerfKnightService.calendarDayNow())?"Next: "+next.displayName+" duty":"Return another day for your next duty.";}
    public static class Handler implements IMessageHandler<KOMEPacketSerfdomMasterAction,IMessage>{public IMessage onMessage(KOMEPacketSerfdomMasterAction message,MessageContext ctx){
        EntityPlayerMP player=ctx.getServerHandler().playerEntity; if(message.action<SERVE||message.action>HIGHLIGHT){player.addChatMessage(new ChatComponentText("Unknown Serfdom Master action."));return null;}
        Entity entity=KOMEReflection.getWorld(player).getEntityByID(message.entityId); if(!(entity instanceof LOTREntityNPC)){player.addChatMessage(new ChatComponentText("That Serfdom Master is no longer available."));return null;}
        LOTREntityNPC npc=(LOTREntityNPC)entity; KOMEWorldData data=KOMEWorldData.get(KOMEReflection.getWorld(player));
        if(message.action==SERVE){KOMESerfdomMasterService.Result result=KOMESerfdomMasterService.serve(player,data,npc);player.addChatMessage(new ChatComponentText(result.success?"You now serve "+npc.getNPCName()+" as your Serfdom Master.":result.reason));if(result.success)sendMenu(player,npc);return null;}
        KOMESerfdomMasterService.Result validation=KOMESerfdomMasterService.validate(player,data,npc,true);if(!validation.success){player.addChatMessage(new ChatComponentText(validation.reason));return null;}
        KOMESerfKnightProgression state=data.getProgression(KOMEReflection.getEntityUUID(player)).getSerfKnightProgression();if(!state.getSerfdomMaster().hasSameIdentity(kome.common.data.KOMEProgressionNpcRankService.referenceOf(npc))){player.addChatMessage(new ChatComponentText("You may only speak to your current Serfdom Master."));return null;}
        if(message.action==REQUEST_DUTY){KOMESerfdomMasterService.Result result=KOMESerfdomMasterService.requestDuty(player,data,npc);player.addChatMessage(new ChatComponentText(result.success?"You received your next Serfdom duty. Details are pending.":sameDayFlavor(result.reason)));sendMenu(player,npc);}
        else if(message.action==HIGHLIGHT)KOMESerfdomMasterService.highlightMaster(player,data);
        else player.addChatMessage(new ChatComponentText(dutyStatus(state)));
        return null;
    }}
    private static String sameDayFlavor(String reason){return reason!=null&&reason.contains("already received a progression task today")?"You have done enough for today. Return to me later, and I will have another task for you.":reason;}
}
