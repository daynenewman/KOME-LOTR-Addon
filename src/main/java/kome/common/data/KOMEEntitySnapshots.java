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
        // Forge writes the live customEntityData tag by reference, including mounted entities.
        // A snapshot must not change when a later reset stamps the live tree's receipts.
        return entity.writeMountToNBT(snapshot) ? (NBTTagCompound) snapshot.copy() : null;
    }
}
