package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

public class KOMEConquestTile {
    public String id;
    public String ownerFaction = "";
    public long claimedWorldTime;

    public KOMEConquestTile(String id) {
        this.id = normalizeId(id);
    }

    public boolean isClaimed() {
        return ownerFaction != null && !ownerFaction.trim().isEmpty();
    }

    public void claim(String faction, long worldTime) {
        ownerFaction = valueOrBlank(faction);
        claimedWorldTime = worldTime;
    }

    public void clear() {
        ownerFaction = "";
        claimedWorldTime = 0L;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        id = normalizeId(nbt.getString("Id"));
        ownerFaction = nbt.getString("OwnerFaction");
        claimedWorldTime = nbt.getLong("ClaimedWorldTime");
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", normalizeId(id));
        nbt.setString("OwnerFaction", valueOrBlank(ownerFaction));
        nbt.setLong("ClaimedWorldTime", claimedWorldTime);
        return nbt;
    }

    public static String normalizeId(String id) {
        return id == null ? "" : id.trim().toUpperCase();
    }

    public static boolean isCanonicalTileId(String id) {
        return normalizeId(id).matches("[A-Z]+[0-9]+");
    }

    private static String valueOrBlank(String value) {
        return value == null ? "" : value;
    }
}
