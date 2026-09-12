package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** Persisted faction-level permission for future construction on one controlled tile. */
public final class KOMEForeignConstructionPermission {
    public String tileId = "", grantingFaction = "", granteeFaction = "";
    public UUID grantedBy;
    public long grantedAtMillis;

    public String key() { return key(tileId, grantingFaction, granteeFaction); }
    public static String key(String tileId, String grantingFaction, String granteeFaction) {
        return KOMEConquestTile.normalizeId(tileId) + "|" + KOMEAlliance.normalizeFactionKey(grantingFaction) + "|" + KOMEAlliance.normalizeFactionKey(granteeFaction);
    }
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("Tile", KOMEConquestTile.normalizeId(tileId));
        tag.setString("GrantingFaction", KOMEAlliance.normalizeFactionKey(grantingFaction)); tag.setString("GranteeFaction", KOMEAlliance.normalizeFactionKey(granteeFaction));
        if (grantedBy != null) tag.setString("GrantedBy", grantedBy.toString()); tag.setLong("GrantedAt", Math.max(0L, grantedAtMillis)); return tag;
    }
    public boolean readFromNBT(NBTTagCompound tag) {
        tileId = KOMEConquestTile.normalizeId(tag.getString("Tile")); grantingFaction = KOMEAlliance.normalizeFactionKey(tag.getString("GrantingFaction")); granteeFaction = KOMEAlliance.normalizeFactionKey(tag.getString("GranteeFaction"));
        grantedAtMillis = Math.max(0L, tag.getLong("GrantedAt")); grantedBy = null;
        try { if (tag.hasKey("GrantedBy")) grantedBy = UUID.fromString(tag.getString("GrantedBy")); } catch (IllegalArgumentException ignored) { }
        return tileId.length() > 0 && grantingFaction.length() > 0 && granteeFaction.length() > 0;
    }
}
