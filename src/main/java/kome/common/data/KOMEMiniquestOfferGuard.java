package kome.common.data;

import java.lang.reflect.Field;
import kome.common.KOMEReflection;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTREntityQuestInfo;
import net.minecraft.entity.player.EntityPlayer;

/** Player-specific guard around LOTR's native canOfferQuestsTo predicate. */
public final class KOMEMiniquestOfferGuard {
    private static Field npcField;
    private KOMEMiniquestOfferGuard(){}

    public static boolean allowOffer(LOTREntityQuestInfo info,EntityPlayer player){
        if(info==null||player==null||player.worldObj==null||player.worldObj.isRemote)return true;
        LOTREntityNPC npc=npc(info);if(npc==null)return true;
        // Preserve interaction with already accepted native quests; only new offers are suppressed.
        if(!LOTRLevelData.getData(player).getMiniQuestsForEntity(npc,true).isEmpty())return true;
        return !isRelationshipNpc(player,npc);
    }

    static boolean isRelationshipNpc(EntityPlayer player,LOTREntityNPC npc){
        if(player==null||npc==null||player.worldObj==null||player.worldObj.isRemote)return false;
        KOMEPlayerProgression progression=KOMEWorldData.get(KOMEReflection.getWorld(player))
            .getProgression(KOMEReflection.getEntityUUID(player));
        String id=KOMEReflection.getEntityUUID(npc).toString();
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();
        return id.equals(state.getSerfdomMaster().entityUuid)||id.equals(state.getProspectiveLiege().entityUuid);
    }

    private static LOTREntityNPC npc(LOTREntityQuestInfo info){
        try{if(npcField==null){npcField=LOTREntityQuestInfo.class.getDeclaredField("theNPC");npcField.setAccessible(true);}return (LOTREntityNPC)npcField.get(info);}catch(Throwable ignored){return null;}
    }
}
