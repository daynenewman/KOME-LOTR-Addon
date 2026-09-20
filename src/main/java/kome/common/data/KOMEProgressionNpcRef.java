package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import java.util.Locale;
import java.util.UUID;

/** Persisted identity and last known position of a progression NPC; never holds a live Entity. */
public final class KOMEProgressionNpcRef {
    public static final KOMEProgressionNpcRef EMPTY = new KOMEProgressionNpcRef("", "", "", 0, 0.0D, 0.0D, 0.0D);
    public final String entityUuid;
    public final String displayName;
    public final String factionKey;
    public final int dimension;
    public final double x, y, z;

    public KOMEProgressionNpcRef(String entityUuid, String displayName, String factionKey, int dimension, double x, double y, double z) {
        this.entityUuid = normalizeUuid(entityUuid);
        this.displayName = clean(displayName);
        this.factionKey = normalizeFactionKey(factionKey);
        if (this.entityUuid.length() == 0 && (this.displayName.length() != 0 || this.factionKey.length() != 0 || dimension != 0 || x != 0.0D || y != 0.0D || z != 0.0D)) throw new IllegalArgumentException("Unset NPC references cannot carry identity or location data.");
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public boolean isSet() { return entityUuid.length() != 0; }
    public boolean hasSameIdentity(KOMEProgressionNpcRef other) { return other != null && entityUuid.equals(other.entityUuid); }
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("EntityUUID", entityUuid); tag.setString("Name", displayName); tag.setString("Faction", factionKey);
        tag.setInteger("Dimension", dimension); tag.setDouble("X", x); tag.setDouble("Y", y); tag.setDouble("Z", z);
        return tag;
    }
    public static KOMEProgressionNpcRef readFromNBT(NBTTagCompound tag) {
        if (tag == null) return EMPTY;
        try { return new KOMEProgressionNpcRef(tag.getString("EntityUUID"), tag.getString("Name"), tag.getString("Faction"),
                tag.getInteger("Dimension"), tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")); }
        catch (IllegalArgumentException ignored) { return EMPTY; }
    }
    private static String clean(String value) { return value == null ? "" : value.trim(); }
    private static String normalizeUuid(String value) {
        String normalized = clean(value);
        if (normalized.length() == 0) return "";
        UUID parsed;
        try { parsed = UUID.fromString(normalized); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("NPC identity must be a UUID."); }
        if (!parsed.toString().equals(normalized.toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("NPC identity must be a canonical UUID.");
        return parsed.toString();
    }
    private static String normalizeFactionKey(String value) {
        String normalized = clean(value).toLowerCase(Locale.ROOT);
        if (normalized.length() != 0 && !normalized.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid NPC faction key.");
        return normalized;
    }
}
