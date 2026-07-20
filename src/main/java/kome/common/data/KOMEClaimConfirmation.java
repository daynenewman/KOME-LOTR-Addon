package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/** Short-lived server proof that the claimant saw the hostile/alliance consequences. */
public class KOMEClaimConfirmation {
    public UUID player;
    public String tileId = "";
    public String expectedOwner = "";
    public String expectedAllianceState = "";
    public long expiresAtMillis;

    public boolean matches(UUID playerId, String tile, String owner, String allianceState, long now) {
        return playerId != null && playerId.equals(player)
            && KOMEConquestTile.normalizeId(tile).equals(tileId)
            && KOMEAlliance.normalizeFactionKey(owner).equals(expectedOwner)
            && safe(allianceState).equals(expectedAllianceState)
            && now <= expiresAtMillis;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Player", player == null ? "" : player.toString());
        nbt.setString("TileId", tileId);
        nbt.setString("ExpectedOwner", expectedOwner);
        nbt.setString("ExpectedAllianceState", expectedAllianceState);
        nbt.setLong("ExpiresAtMillis", expiresAtMillis);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        try { player = UUID.fromString(nbt.getString("Player")); }
        catch (IllegalArgumentException ignored) { player = null; }
        tileId = KOMEConquestTile.normalizeId(nbt.getString("TileId"));
        expectedOwner = KOMEAlliance.normalizeFactionKey(nbt.getString("ExpectedOwner"));
        expectedAllianceState = nbt.getString("ExpectedAllianceState");
        expiresAtMillis = Math.max(0L, nbt.getLong("ExpiresAtMillis"));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
