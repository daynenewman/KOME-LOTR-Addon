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
        return entity.writeMountToNBT(snapshot) ? snapshot : null;
    }
}
