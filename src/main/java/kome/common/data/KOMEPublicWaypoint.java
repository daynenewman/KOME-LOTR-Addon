package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** Canonical approved destination. No independently mutable ownership or gameplay-level policy. */
public final class KOMEPublicWaypoint {
    public enum Source { PUBLIC, NATIVE, MIGRATED }
    public final UUID id;
    /** Negative, never reused; scoped by the addon travel namespace rather than native custom IDs. */
    public final int wireId;
    public final String tileId, name, sourceKey, approvedBy;
    public final Source source;
    public final int dimension, x, y, z, level;
    public final long approvedAt, modifiedAt;
    public final UUID proposalId;

    KOMEPublicWaypoint(UUID id, int wireId, String tileId, String name, Source source,
            String sourceKey, int dimension, int x, int y, int z, int level,
            String approvedBy, long approvedAt, long modifiedAt, UUID proposalId) {
        if (id == null || wireId >= 0 || wireId == Integer.MIN_VALUE || source == null) throw new IllegalArgumentException("Invalid waypoint identity/source");
        this.id = id;
        this.wireId = wireId;
        this.tileId = canonicalTile(tileId);
        this.name = validName(name);
        this.source = source;
        this.sourceKey = text(sourceKey, 128, true);
        if (source == Source.PUBLIC && !this.sourceKey.isEmpty()
                || source != Source.PUBLIC && this.sourceKey.isEmpty())
            throw new IllegalArgumentException("Source identity does not match waypoint source");
        if (source == Source.MIGRATED) legacyIdentity(this.sourceKey);
        this.dimension = dimension;
        this.x = x; this.y = y; this.z = z;
        if (y < 0 || y > 255 || level < 0) throw new IllegalArgumentException("Invalid height/level");
        this.level = level;
        this.approvedBy = actor(approvedBy);
        if (approvedAt < 0 || modifiedAt < approvedAt) throw new IllegalArgumentException("Invalid approval timestamps");
        this.approvedAt = approvedAt; this.modifiedAt = modifiedAt;
        this.proposalId = proposalId;
    }

    KOMEPublicWaypoint changed(String tile, String displayName, int dim, int wx, int wy, int wz,
            int newLevel, long now) {
        if (now < modifiedAt) throw new IllegalArgumentException("Stale administrative timestamp");
        return new KOMEPublicWaypoint(id, wireId, tile, displayName, source, sourceKey,
            dim, wx, wy, wz, newLevel, approvedBy, approvedAt, now, proposalId);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound n = new NBTTagCompound();
        n.setString("Id", id.toString()); n.setInteger("WireId", wireId);
        n.setString("Tile", tileId); n.setString("Name", name);
        n.setString("Source", source.name()); n.setString("SourceKey", sourceKey);
        n.setInteger("Dimension", dimension); n.setInteger("X", x);
        n.setInteger("Y", y); n.setInteger("Z", z); n.setInteger("Level", level);
        n.setString("ApprovedBy", approvedBy); n.setLong("ApprovedAt", approvedAt);
        n.setLong("ModifiedAt", modifiedAt);
        n.setString("Proposal", proposalId == null ? "" : proposalId.toString());
        return n;
    }

    static KOMEPublicWaypoint read(NBTTagCompound n) {
        for (String key : new String[] {"Id","Tile","Name","Source","SourceKey","ApprovedBy","Proposal"})
            require(n, key, 8);
        for (String key : new String[] {"WireId","Dimension","X","Y","Z","Level"}) require(n, key, 3);
        require(n, "ApprovedAt", 4); require(n, "ModifiedAt", 4);
        String proposal = n.getString("Proposal");
        return new KOMEPublicWaypoint(uuid(n.getString("Id")), n.getInteger("WireId"),
            n.getString("Tile"), n.getString("Name"), Source.valueOf(n.getString("Source")),
            n.getString("SourceKey"), n.getInteger("Dimension"), n.getInteger("X"), n.getInteger("Y"),
            n.getInteger("Z"), n.getInteger("Level"), n.getString("ApprovedBy"),
            n.getLong("ApprovedAt"), n.getLong("ModifiedAt"), proposal.isEmpty() ? null : uuid(proposal));
    }

    /** Public wire projection omits submitter/reviewer/audit data. */
    public static KOMEPublicWaypoint presentation(UUID id,int wire,String tile,String name,Source source,String key,
            int dimension,int x,int y,int z,int level) {
        return new KOMEPublicWaypoint(id,wire,tile,name,source,key,dimension,x,y,z,level,"console",0,0,null);
    }
    public static String validName(String name) { return text(name, 64, false); }

    static String text(String s, int max, boolean empty) {
        if (s == null || s.length() > max || !s.equals(s.trim()) || !empty && s.isEmpty())
            throw new IllegalArgumentException("Invalid text (maximum " + max + ")");
        for (int i = 0; i < s.length(); i++)
            if (Character.isISOControl(s.charAt(i)) || s.charAt(i) == '\u00a7'
                    || Character.isSurrogate(s.charAt(i)) && (i + 1 >= s.length()
                    || !Character.isHighSurrogate(s.charAt(i)) || !Character.isLowSurrogate(s.charAt(++i))))
                throw new IllegalArgumentException("Text contains control/formatting or invalid Unicode");
        return s;
    }

    static String canonicalTile(String tile) {
        if (tile == null || tile.isEmpty() || !tile.equals(KOMEConquestTile.normalizeId(tile)))
            throw new IllegalArgumentException("Noncanonical tile identity");
        return tile;
    }

    static UUID uuid(String raw) {
        if (raw == null) throw new IllegalArgumentException("Missing UUID");
        UUID id = UUID.fromString(raw);
        if (!id.toString().equals(raw)) throw new IllegalArgumentException("Noncanonical UUID");
        return id;
    }

    static String actor(String value) {
        if (!"console".equals(value)) uuid(value);
        return value;
    }

    static String legacyIdentity(String value) {
        int colon = value.indexOf(':');
        if (colon != 36 || value.lastIndexOf(':') != colon)
            throw new IllegalArgumentException("Invalid legacy owner:ID");
        uuid(value.substring(0, colon));
        int id = Integer.parseInt(value.substring(colon + 1));
        if (id < 0 || !Integer.toString(id).equals(value.substring(colon + 1)))
            throw new IllegalArgumentException("Invalid legacy custom ID");
        return value;
    }

    static void require(NBTTagCompound n, String key, int type) {
        if (!n.hasKey(key, type)) throw new IllegalArgumentException("Missing/wrong type: " + key);
    }
}
