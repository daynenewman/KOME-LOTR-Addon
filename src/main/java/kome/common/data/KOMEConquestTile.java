package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

public class KOMEConquestTile {
    public String id;
    public String ownerFaction = "";
    public String pendingTransferFromFaction = "";
    public String pendingTransferToFaction = "";
    public long claimedWorldTime;

    public KOMEConquestTile(String id) {
        this.id = normalizeId(id);
    }

    public boolean isClaimed() {
        return ownerFaction != null && !ownerFaction.trim().isEmpty();
    }

    public void claim(String faction, long worldTime) {
        ownerFaction = valueOrBlank(faction);
        clearPendingTransfer();
        claimedWorldTime = worldTime;
    }

    public void proposeTransfer(String fromFaction, String toFaction) {
        pendingTransferFromFaction = valueOrBlank(fromFaction);
        pendingTransferToFaction = valueOrBlank(toFaction);
    }

    public boolean hasPendingTransfer() {
        return pendingTransferFromFaction.trim().length() > 0 && pendingTransferToFaction.trim().length() > 0;
    }

    public void clearPendingTransfer() {
        pendingTransferFromFaction = "";
        pendingTransferToFaction = "";
    }

    public void clear() {
        ownerFaction = "";
        clearPendingTransfer();
        claimedWorldTime = 0L;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        id = normalizeId(nbt.getString("Id"));
        ownerFaction = nbt.getString("OwnerFaction");
        pendingTransferFromFaction = nbt.getString("PendingTransferFromFaction");
        pendingTransferToFaction = nbt.getString("PendingTransferToFaction");
        claimedWorldTime = nbt.getLong("ClaimedWorldTime");
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", normalizeId(id));
        nbt.setString("OwnerFaction", valueOrBlank(ownerFaction));
        nbt.setString("PendingTransferFromFaction", valueOrBlank(pendingTransferFromFaction));
        nbt.setString("PendingTransferToFaction", valueOrBlank(pendingTransferToFaction));
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
