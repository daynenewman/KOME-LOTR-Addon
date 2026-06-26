package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

public class KOMEConquestTile {
    public String id;
    public int waypointLevel;
    public String defaultRulingFaction = "";
    public String currentRulingFaction = "";
    public String mapRegion = "";
    public String ownerFaction = "";
    public String pendingTransferFromFaction = "";
    public String pendingTransferToFaction = "";
    public long claimedWorldTime;
    public UUID claimedByUuid;
    public String claimedByName = "";
    public long claimedAtMillis;
    public int anchorDimension;
    public double anchorX;
    public double anchorY;
    public double anchorZ;
    public boolean hasAnchor;

    public KOMEConquestTile(String id) {
        this.id = normalizeId(id);
    }

    public boolean isClaimed() {
        syncCurrentFromLegacy();
        return KOMEAlliance.normalizeFactionKey(currentRulingFaction).length() > 0;
    }

    public void claim(String faction, long worldTime) {
        claim(faction, worldTime, null, "");
    }

    public void claim(String faction, long worldTime, UUID playerUuid, String playerName) {
        setCurrentRulingFaction(faction);
        clearPendingTransfer();
        claimedWorldTime = worldTime;
        claimedByUuid = playerUuid;
        claimedByName = valueOrBlank(playerName);
        claimedAtMillis = System.currentTimeMillis();
    }

    public void proposeTransfer(String fromFaction, String toFaction) {
        pendingTransferFromFaction = KOMEAlliance.normalizeFactionKey(fromFaction);
        pendingTransferToFaction = KOMEAlliance.normalizeFactionKey(toFaction);
    }

    public boolean hasPendingTransfer() {
        return pendingTransferFromFaction.trim().length() > 0 && pendingTransferToFaction.trim().length() > 0;
    }

    public void clearPendingTransfer() {
        pendingTransferFromFaction = "";
        pendingTransferToFaction = "";
    }

    public void clear() {
        setCurrentRulingFaction("");
        clearPendingTransfer();
        claimedWorldTime = 0L;
        claimedByUuid = null;
        claimedByName = "";
        claimedAtMillis = 0L;
        hasAnchor = false;
    }

    public void clearOwnershipOnly() {
        setCurrentRulingFaction("");
        clearPendingTransfer();
        claimedWorldTime = 0L;
        claimedByUuid = null;
        claimedByName = "";
        claimedAtMillis = 0L;
    }

    public void resetOwnershipToDefault(long worldTime) {
        String faction = KOMEAlliance.normalizeFactionKey(defaultRulingFaction);
        if (faction.length() == 0) {
            clearOwnershipOnly();
            return;
        }
        setCurrentRulingFaction(faction);
        clearPendingTransfer();
        claimedWorldTime = worldTime;
        claimedByUuid = null;
        claimedByName = "";
        claimedAtMillis = System.currentTimeMillis();
    }

    public void setCurrentRulingFaction(String faction) {
        currentRulingFaction = KOMEAlliance.normalizeFactionKey(faction);
        ownerFaction = currentRulingFaction;
    }

    public void setWaypointDefaults(int level, String defaultFaction, String region) {
        waypointLevel = level >= 1 && level <= 3 ? level : 0;
        defaultRulingFaction = KOMEAlliance.normalizeFactionKey(defaultFaction);
        mapRegion = valueOrBlank(region);
    }

    public String currentRulingFaction() {
        syncCurrentFromLegacy();
        return currentRulingFaction;
    }

    public void setAnchor(int dimension, double x, double y, double z) {
        anchorDimension = dimension;
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        hasAnchor = true;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        id = normalizeId(nbt.getString("Id"));
        waypointLevel = nbt.getInteger("WaypointLevel");
        defaultRulingFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("DefaultRulingFaction"));
        currentRulingFaction = KOMEAlliance.normalizeFactionKey(nbt.hasKey("CurrentRulingFaction") ? nbt.getString("CurrentRulingFaction") : nbt.getString("OwnerFaction"));
        ownerFaction = currentRulingFaction;
        mapRegion = nbt.getString("MapRegion");
        pendingTransferFromFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("PendingTransferFromFaction"));
        pendingTransferToFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("PendingTransferToFaction"));
        claimedWorldTime = nbt.getLong("ClaimedWorldTime");
        String claimedBy = nbt.getString("ClaimedByUuid");
        claimedByUuid = claimedBy.length() == 0 ? null : UUID.fromString(claimedBy);
        claimedByName = nbt.getString("ClaimedByName");
        claimedAtMillis = nbt.getLong("ClaimedAtMillis");
        hasAnchor = nbt.getBoolean("HasAnchor");
        anchorDimension = nbt.getInteger("AnchorDimension");
        anchorX = nbt.getDouble("AnchorX");
        anchorY = nbt.getDouble("AnchorY");
        anchorZ = nbt.getDouble("AnchorZ");
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", normalizeId(id));
        syncCurrentFromLegacy();
        nbt.setInteger("WaypointLevel", waypointLevel);
        nbt.setString("DefaultRulingFaction", KOMEAlliance.normalizeFactionKey(defaultRulingFaction));
        nbt.setString("CurrentRulingFaction", KOMEAlliance.normalizeFactionKey(currentRulingFaction));
        nbt.setString("MapRegion", valueOrBlank(mapRegion));
        nbt.setString("OwnerFaction", KOMEAlliance.normalizeFactionKey(currentRulingFaction));
        nbt.setString("PendingTransferFromFaction", KOMEAlliance.normalizeFactionKey(pendingTransferFromFaction));
        nbt.setString("PendingTransferToFaction", KOMEAlliance.normalizeFactionKey(pendingTransferToFaction));
        nbt.setLong("ClaimedWorldTime", claimedWorldTime);
        nbt.setString("ClaimedByUuid", claimedByUuid == null ? "" : claimedByUuid.toString());
        nbt.setString("ClaimedByName", valueOrBlank(claimedByName));
        nbt.setLong("ClaimedAtMillis", claimedAtMillis);
        nbt.setBoolean("HasAnchor", hasAnchor);
        nbt.setInteger("AnchorDimension", anchorDimension);
        nbt.setDouble("AnchorX", anchorX);
        nbt.setDouble("AnchorY", anchorY);
        nbt.setDouble("AnchorZ", anchorZ);
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

    private void syncCurrentFromLegacy() {
        String current = KOMEAlliance.normalizeFactionKey(currentRulingFaction);
        String legacy = KOMEAlliance.normalizeFactionKey(ownerFaction);
        if (current.length() == 0 && legacy.length() > 0) {
            currentRulingFaction = legacy;
        }
        ownerFaction = KOMEAlliance.normalizeFactionKey(currentRulingFaction);
    }
}
