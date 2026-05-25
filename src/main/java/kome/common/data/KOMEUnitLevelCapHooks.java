package kome.common.data;

import kome.common.KOMEReflection;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.world.World;

import java.util.UUID;

public class KOMEUnitLevelCapHooks {
    public static int getMaximumLevel(LOTREntityNPC npc) {
        if (npc == null || !npc.hiredNPCInfo.isActive) {
            return 0;
        }
        World world = KOMEReflection.getWorld(npc);
        if (world == null || KOMEReflection.isRemote(world)) {
            return 0;
        }
        KOMEHiredUnitRecord record = KOMEWorldData.get(world).hiredUnits.get(KOMEReflection.getEntityUUID(npc));
        return record == null ? 0 : Math.max(0, record.levelCap);
    }

    public static void enforceCap(LOTREntityNPC npc) {
        int cap = getMaximumLevel(npc);
        if (cap <= 0) {
            return;
        }
        if (npc.hiredNPCInfo.xpLevel > cap) {
            int extraLevels = npc.hiredNPCInfo.xpLevel - cap;
            IAttributeInstance attrHealth = npc.getEntityAttribute(SharedMonsterAttributes.maxHealth);
            attrHealth.setBaseValue(Math.max(attrHealth.getBaseValue() - extraLevels, 1.0));
            npc.hiredNPCInfo.xpLevel = cap;
            npc.setHealth(Math.min(npc.getHealth(), npc.getMaxHealth()));
        }
        int capXP = LOTRHiredNPCInfo.totalXPForLevel(cap);
        if (npc.hiredNPCInfo.xp > capXP) {
            npc.hiredNPCInfo.xp = capXP;
        }
        KOMEReflection.markHiredInfoDirty(npc.hiredNPCInfo);
    }

    public static KOMEHiredUnitRecord getRecord(World world, UUID entityID) {
        return KOMEWorldData.get(world).hiredUnits.get(entityID);
    }
}
