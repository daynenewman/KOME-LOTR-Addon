package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

public class KOMEHiredUnitRecord {
    public UUID entity;
    public UUID owner;
    public KOMEPopulationType type = KOMEPopulationType.OFFENSIVE;
    public int cost = 25;
    public int baseCost = 25;
    public boolean farmhand;
    public boolean mounted;
    public String unitName = "";

    public void readFromNBT(NBTTagCompound nbt) {
        entity = UUID.fromString(nbt.getString("Entity"));
        owner = UUID.fromString(nbt.getString("Owner"));
        KOMEPopulationType readType = KOMEPopulationType.forName(nbt.getString("Type"));
        type = readType == null ? KOMEPopulationType.OFFENSIVE : readType;
        cost = nbt.getInteger("Cost");
        baseCost = nbt.hasKey("BaseCost") ? nbt.getInteger("BaseCost") : 25;
        farmhand = nbt.getBoolean("Farmhand");
        mounted = nbt.getBoolean("Mounted");
        unitName = nbt.getString("UnitName");
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Entity", entity.toString());
        nbt.setString("Owner", owner.toString());
        nbt.setString("Type", type.key);
        nbt.setInteger("Cost", cost);
        nbt.setInteger("BaseCost", baseCost);
        nbt.setBoolean("Farmhand", farmhand);
        nbt.setBoolean("Mounted", mounted);
        nbt.setString("UnitName", unitName == null ? "" : unitName);
        return nbt;
    }
}
