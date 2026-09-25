package kome.client;

import java.lang.reflect.Field;
import kome.common.data.KOMEProgressionOfferBridge;
import lotr.common.quest.LOTRMiniQuest;

/** Client-only reflection keeps the core patch independent of LOTR's private GUI fields. */
public final class KOMEProgressionOfferClientBridge {
    private static Field quest, sent;
    private KOMEProgressionOfferClientBridge(){}
    public static boolean preservePassiveClose(Object gui){try{if(quest==null){quest=gui.getClass().getDeclaredField("theMiniQuest");quest.setAccessible(true);sent=gui.getClass().getDeclaredField("sentClosePacket");sent.setAccessible(true);}Object value=quest.get(gui);if(value instanceof LOTRMiniQuest&&KOMEProgressionOfferBridge.isExternalOffer((LOTRMiniQuest)value)&&!sent.getBoolean(gui)){sent.setBoolean(gui,true);return true;}}catch(Exception ignored){}return false;}
}
