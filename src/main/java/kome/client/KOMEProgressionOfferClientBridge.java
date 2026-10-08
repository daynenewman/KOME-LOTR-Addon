package kome.client;

import java.lang.reflect.Field;
import kome.common.data.KOMEProgressionOfferBridge;
import lotr.common.quest.LOTRMiniQuest;

/** Client-only reflection keeps the core patch independent of LOTR's private GUI fields. */
public final class KOMEProgressionOfferClientBridge {
    private static Field quest, sent;
    public KOMEProgressionOfferClientBridge(){}
    @cpw.mods.fml.common.eventhandler.SubscribeEvent
    public void respond(net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Pre event){
        if(!(event.gui instanceof lotr.client.gui.LOTRGuiMiniquestOffer))return;
        try{
            Field q=event.gui.getClass().getDeclaredField("theMiniQuest");q.setAccessible(true);Object value=q.get(event.gui);
            if(!(value instanceof kome.common.data.KOMESerfdomOfferQuest))return;
            Field a=event.gui.getClass().getDeclaredField("buttonAccept"),d=event.gui.getClass().getDeclaredField("buttonDecline"),n=event.gui.getClass().getDeclaredField("theNPC"),s=event.gui.getClass().getDeclaredField("sentClosePacket");
            a.setAccessible(true);d.setAccessible(true);n.setAccessible(true);s.setAccessible(true);
            if(event.button!=a.get(event.gui)&&event.button!=d.get(event.gui))return;
            lotr.common.entity.npc.LOTREntityNPC npc=(lotr.common.entity.npc.LOTREntityNPC)n.get(event.gui);
            kome.common.network.KOMEPacketHandler.network.sendToServer(new kome.common.network.KOMEPacketMasterOfferResponse(npc.getEntityId(),((LOTRMiniQuest)value).questUUID.toString(),event.button==a.get(event.gui)));
            s.setBoolean(event.gui,true);event.setCanceled(true);net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(null);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException("Native Master offer fields changed",failure);}
    }
    public static boolean preservePassiveClose(Object gui){try{if(quest==null){quest=gui.getClass().getDeclaredField("theMiniQuest");quest.setAccessible(true);sent=gui.getClass().getDeclaredField("sentClosePacket");sent.setAccessible(true);}Object value=quest.get(gui);if(value instanceof LOTRMiniQuest&&KOMEProgressionOfferBridge.isExternalOffer((LOTRMiniQuest)value)&&!(value instanceof kome.common.data.KOMELiegeOfferQuest&&(((kome.common.data.KOMELiegeOfferQuest)value).isStandingTrialOffer()||((kome.common.data.KOMELiegeOfferQuest)value).isReplacementOffer()))&&!sent.getBoolean(gui)){sent.setBoolean(gui,true);return true;}}catch(Exception ignored){}return false;}
}
