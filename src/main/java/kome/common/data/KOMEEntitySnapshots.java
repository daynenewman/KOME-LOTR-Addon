package kome.common.data;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;

public final class KOMEEntitySnapshots {
    private KOMEEntitySnapshots() {
    }

    public static NBTTagCompound snapshot(Entity entity) {
        if (entity == null || entity.isDead) {
            return null;
        }
        NBTTagCompound snapshot = new NBTTagCompound();
        if (!entity.writeMountToNBT(snapshot)) return null;
        acknowledge(entity, snapshot);
        return snapshot;
    }

    private static void acknowledge(Entity entity, NBTTagCompound snapshot) {
        KOMECampaignHealthEntity.endWrite(entity, snapshot);
        Entity mount = kome.common.KOMEReflection.getRidingEntity(entity);
        if (mount != null && snapshot.hasKey("Riding", 10)) acknowledge(mount, snapshot.getCompoundTag("Riding"));
    }
    public static NBTTagCompound snapshot(KOMEHiredUnitRecord record, Entity entity) {
        KOMECampaignHealth.observe(record, entity);
        return snapshot(entity);
    }
}
