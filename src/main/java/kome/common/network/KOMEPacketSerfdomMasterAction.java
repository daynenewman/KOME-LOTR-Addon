package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEPlayerProgression;
import kome.common.data.KOMEProgressionNpcRef;
import kome.common.data.KOMEProgressionNpcSpeech;
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
    public static final int SERVE=0, REQUEST_DUTY=1, VIEW_DUTY=2, HIGHLIGHT=3, DELIVER_PROVISIONS=4, DELIVER_PROFESSION=5;
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
    private static String dutyStatus(KOMESerfKnightProgression state){String active=state.getActiveAssignmentKind();if("provisioning".equals(active)){kome.common.data.KOMESerfProvisioningAssignment a=kome.common.data.KOMESerfProvisioningAssignment.readFromNBT(state.getDuty(kome.common.data.KOMESerfKnightDutyType.PROVISIONING).getAssignmentData());if(a!=null)return "Provisioning duty - active";}if("profession".equals(active)){kome.common.data.KOMESerfProfessionAssignment a=kome.common.data.KOMESerfProfessionAssignment.readFromNBT(state.getDuty(kome.common.data.KOMESerfKnightDutyType.PROFESSION).getAssignmentData());if(a!=null)return "Profession duty - active";}if(active.length()!=0){KOMESerfKnightDutyType type=KOMESerfKnightDutyType.forKey(active);return type==null?"Trial of Knighthood - assigned":type.displayName+" duty - assigned; details pending";}KOMESerfKnightDutyType next=kome.common.data.KOMESerfKnightService.nextDuty(state);if(next==null)return "All Serfdom duties complete.";return kome.common.data.KOMESerfKnightService.mayIssueAssignment(state,kome.common.data.KOMESerfKnightService.calendarDayNow())?"Next: "+next.displayName+" duty":"Return another day for your next duty.";}
    public static class Handler implements IMessageHandler<KOMEPacketSerfdomMasterAction,IMessage>{public IMessage onMessage(KOMEPacketSerfdomMasterAction message,MessageContext ctx){
        EntityPlayerMP player=ctx.getServerHandler().playerEntity; if(message.action<SERVE||message.action>DELIVER_PROFESSION){player.addChatMessage(new ChatComponentText("Unknown Serfdom Master action."));return null;}
        Entity entity=KOMEReflection.getWorld(player).getEntityByID(message.entityId); if(!(entity instanceof LOTREntityNPC)){player.addChatMessage(new ChatComponentText("That Serfdom Master is no longer available."));return null;}
        LOTREntityNPC npc=(LOTREntityNPC)entity; KOMEWorldData data=KOMEWorldData.get(KOMEReflection.getWorld(player));
        if(message.action==SERVE){KOMESerfdomMasterService.Result result=KOMESerfdomMasterService.serve(player,data,npc);if(result.success){KOMEProgressionNpcSpeech.welcomeSerf(player,npc,result.enteredSerfdom);kome.common.data.KOMEProgressionAutoCompleter.syncPlayer(player,data.getProgression(KOMEReflection.getEntityUUID(player)));sendMenu(player,npc);}else player.addChatMessage(new ChatComponentText(result.reason));return null;}
        KOMESerfdomMasterService.Result validation=KOMESerfdomMasterService.validateCurrentMasterInteraction(player,data,npc,true);if(!validation.success){player.addChatMessage(new ChatComponentText(validation.reason));return null;}
        KOMEPlayerProgression progression=data.getProgression(KOMEReflection.getEntityUUID(player));KOMESerfKnightProgression state=progression.getSerfKnightProgression();if(!state.getSerfdomMaster().hasSameIdentity(kome.common.data.KOMEProgressionNpcRankService.referenceOf(npc))){player.addChatMessage(new ChatComponentText("You may only speak to your current Serfdom Master."));return null;}
        if(message.action==REQUEST_DUTY){KOMESerfKnightDutyType requested=kome.common.data.KOMESerfKnightService.nextDuty(state);KOMESerfdomMasterService.Result result=KOMESerfdomMasterService.requestDuty(player,data,npc);if(result.success){KOMEProgressionNpcSpeech.assignDuty(player,npc,requested);kome.common.data.KOMEProgressionAutoCompleter.syncPlayer(player,progression);}else if(isSameDay(result.reason))KOMEProgressionNpcSpeech.sameDay(player,npc);else player.addChatMessage(new ChatComponentText(result.reason));sendMenu(player,npc);}
        else if(message.action==HIGHLIGHT)KOMESerfdomMasterService.highlightMaster(player,data);
        else if(message.action==DELIVER_PROVISIONS){kome.common.data.KOMESerfKnightDutyType active=kome.common.data.KOMESerfKnightDutyType.forKey(state.getActiveAssignmentKind());kome.common.data.KOMESerfProvisioningAssignment assignment=active==kome.common.data.KOMESerfKnightDutyType.PROVISIONING?kome.common.data.KOMESerfProvisioningAssignment.readFromNBT(state.getDuty(active).getAssignmentData()):null;if(assignment==null){player.addChatMessage(new ChatComponentText("No valid Provisioning duty is active."));return null;}int delivered=kome.common.data.KOMESerfProvisioningService.deliver(assignment,player.inventory);if(delivered<=0){KOMEProgressionNpcSpeech.noMatchingProvisions(player,npc);return null;}state.setDutyAssignmentData(active,assignment.writeToNBT());if(assignment.complete())kome.common.data.KOMESerfKnightService.completeDuty(progression,active);data.markDirty();player.inventoryContainer.detectAndSendChanges();kome.common.data.KOMEProgressionAutoCompleter.syncPlayer(player,progression);if(assignment.complete())KOMEProgressionNpcSpeech.completedProvisions(player,npc);else KOMEProgressionNpcSpeech.partialProvisions(player,npc);sendMenu(player,npc);}
        else if(message.action==DELIVER_PROFESSION){kome.common.data.KOMESerfKnightDutyType active=kome.common.data.KOMESerfKnightDutyType.forKey(state.getActiveAssignmentKind());kome.common.data.KOMESerfProfessionAssignment assignment=active==kome.common.data.KOMESerfKnightDutyType.PROFESSION?kome.common.data.KOMESerfProfessionAssignment.readFromNBT(state.getDuty(active).getAssignmentData()):null;if(assignment==null){player.addChatMessage(new ChatComponentText("No valid Profession duty is active."));return null;}int delivered=kome.common.data.KOMESerfProfessionService.deliver(assignment,player.inventory);if(delivered<=0){KOMEProgressionNpcSpeech.noMatchingProfessionMaterials(player,npc);return null;}state.setDutyAssignmentData(active,assignment.writeToNBT());kome.common.data.KOMESerfProfessionService.completeIfReady(progression,assignment);data.markDirty();player.inventoryContainer.detectAndSendChanges();kome.common.data.KOMEProgressionAutoCompleter.syncPlayer(player,progression);if(assignment.complete())KOMEProgressionNpcSpeech.completedProfessionMaterials(player,npc);else KOMEProgressionNpcSpeech.partialProfessionMaterials(player,npc);sendMenu(player,npc);}
        else KOMEProgressionNpcSpeech.viewDuty(player,npc,state);
        return null;
    }}
    private static boolean isSameDay(String reason){return reason!=null&&reason.contains("already received a progression task today");}
}
