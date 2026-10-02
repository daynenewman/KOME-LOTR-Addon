package kome.common.data;

import java.util.*;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** KOME-owned persisted approval authority. Mutators are called only by trusted server services/commands. */
public final class KOMEPublicWaypointRegistry {
    public static final int SCHEMA = 1;
    public static final int MAX_RECORDS = 1024, MAX_HISTORY = 32768, MAX_QUARANTINE = 4096;
    private final SortedMap<UUID, KOMEPublicWaypoint> records = new TreeMap<UUID, KOMEPublicWaypoint>();
    private final List<NBTTagCompound> history = new ArrayList<NBTTagCompound>();
    private final List<NBTTagCompound> quarantine = new ArrayList<NBTTagCompound>();
    private final SortedMap<String, UUID> cutover = new TreeMap<String, UUID>();
    private long nextWire = 1, revision;

    public long revision() { return revision; }
    public List<KOMEPublicWaypoint> records() {
        return Collections.unmodifiableList(new ArrayList<KOMEPublicWaypoint>(records.values()));
    }
    public KOMEPublicWaypoint get(UUID id) { return records.get(id); }
    public KOMEPublicWaypoint forTile(String tile) {
        for (KOMEPublicWaypoint r : records.values()) if (r.tileId.equals(tile)) return r;
        return null;
    }
    public List<NBTTagCompound> history() { return copyRows(history); }
    public List<NBTTagCompound> quarantine() { return copyRows(quarantine); }
    public Map<String, UUID> cutoverIdentities() {
        return Collections.unmodifiableMap(new TreeMap<String, UUID>(cutover));
    }

    /** A read-only canonical projection; capture never overwrites waypoint level or persists owners. */
    public View view(KOMEWorldData data, UUID id) {
        KOMEPublicWaypoint r = get(id);
        if (r == null || invalidAssociation(data, r) != null) return null;
        KOMEConquestTile tile = data.conquestTiles.get(r.tileId);
        return new View(r, KOMEAlliance.normalizeFactionKey(tile.defaultRulingFaction),
            tile.projectRulingFaction());
    }
    public List<View> views(KOMEWorldData data) {
        List<View> result = new ArrayList<View>();
        for (UUID id : records.keySet()) {
            View v = view(data, id); if (v != null) result.add(v);
        }
        return Collections.unmodifiableList(result);
    }
    public static final class View {
        public final KOMEPublicWaypoint record;
        public final String defaultOwner, currentOwner;
        View(KOMEPublicWaypoint r, String defaultOwner, String currentOwner) {
            record = r; this.defaultOwner = defaultOwner; this.currentOwner = currentOwner;
        }
    }

    public KOMEPublicWaypoint approve(KOMEWorldData data, String name, int dimension,
            int x, int y, int z, int level, KOMEPublicWaypoint.Source source, String sourceKey,
            String actor, long now, UUID proposalId) {
        writable(data);
        String tile = resolveTile(data, dimension, x, z);
        if (forTile(tile) != null) throw new IllegalArgumentException("Tile already has an approved waypoint: " + tile);
        if (records.size() >= MAX_RECORDS || nextWire > Integer.MAX_VALUE || revision == Long.MAX_VALUE)
            throw new IllegalStateException("Waypoint registry capacity exhausted");
        KOMEPublicWaypoint r = new KOMEPublicWaypoint(UUID.randomUUID(), -(int) nextWire,
            tile, name, source, sourceKey, dimension, x, y, z, level, actor, now, now, proposalId);
        String invalid = invalidAssociation(data, r);
        if (invalid != null) throw new IllegalArgumentException(invalid);
        if (source == KOMEPublicWaypoint.Source.MIGRATED && cutover.containsKey(sourceKey))
            throw new IllegalArgumentException("Legacy identity already converted");
        if (source == KOMEPublicWaypoint.Source.MIGRATED && cutover.size() >= MAX_RECORDS)
            throw new IllegalStateException("Cutover identity capacity exhausted");
        prepareAudit(actor, now, "APPROVE", r.id, null, r.writeToNBT(), "Approved destination");
        records.put(r.id, r); nextWire++; revision++;
        if (source == KOMEPublicWaypoint.Source.MIGRATED) cutover.put(sourceKey, r.id);
        data.markDirty();
        return r;
    }

    public KOMEPublicWaypoint rename(KOMEWorldData data, UUID id, String name, String actor, long now) {
        KOMEPublicWaypoint r = requireRecord(id);
        return replace(data, r, r.changed(r.tileId, name, r.dimension, r.x, r.y, r.z, r.level, now),
            actor, now, "RENAME");
    }
    public KOMEPublicWaypoint level(KOMEWorldData data, UUID id, int level, String actor, long now) {
        KOMEPublicWaypoint r = requireRecord(id);
        return replace(data, r, r.changed(r.tileId, r.name, r.dimension, r.x, r.y, r.z, level, now),
            actor, now, "LEVEL");
    }
    public KOMEPublicWaypoint move(KOMEWorldData data, UUID id, int dim, int x, int y, int z,
            String actor, long now) {
        KOMEPublicWaypoint r = requireRecord(id);
        String tile = resolveTile(data, dim, x, z);
        KOMEPublicWaypoint occupant = forTile(tile);
        if (occupant != null && !occupant.id.equals(id)) throw new IllegalArgumentException("Destination tile occupied");
        return replace(data, r, r.changed(tile, r.name, dim, x, y, z, r.level, now), actor, now, "MOVE");
    }
    public void remove(KOMEWorldData data, UUID id, String actor, long now) {
        writable(data); KOMEPublicWaypoint r = requireRecord(id);
        prepareAudit(actor, now, "REMOVE", id, r.writeToNBT(), null, "Removed destination");
        records.remove(id); revision++; data.markDirty();
        // Explicit cutover aliases survive removal: a deleted public destination must not reappear as a legacy copy.
    }
    private KOMEPublicWaypoint replace(KOMEWorldData data, KOMEPublicWaypoint before,
            KOMEPublicWaypoint after, String actor, long now, String action) {
        writable(data); String invalid = invalidAssociation(data, after);
        if (invalid != null) throw new IllegalArgumentException(invalid);
        prepareAudit(actor, now, action, before.id, before.writeToNBT(), after.writeToNBT(), "Administrative change");
        records.put(after.id, after); revision++; data.markDirty(); return after;
    }
    private KOMEPublicWaypoint requireRecord(UUID id) {
        KOMEPublicWaypoint r = get(id);
        if (r == null) throw new IllegalArgumentException("Unknown approved waypoint");
        return r;
    }
    private void writable(KOMEWorldData data) {
        if (data == null || data instanceof KOMEClientData || data.publicWaypoints != this)
            throw new IllegalStateException("Canonical server authority required");
        data.ensureWritable();
        if (history.size() >= MAX_HISTORY || revision == Long.MAX_VALUE)
            throw new IllegalStateException("Waypoint audit capacity exhausted");
    }

    static String resolveTile(KOMEWorldData data, int dimension, int x, int z) {
        if (data == null) throw new IllegalArgumentException("Missing world authority");
        KOMETileResolution r = KOMETileWorldResolver.INSTANCE.resolve(dimension, x, z);
        if (r.status != KOMETileResolution.Status.RESOLVED)
            throw new IllegalArgumentException("Destination is not a canonical tile: " + r.status);
        if (!data.conquestTiles.containsKey(r.tileId)) throw new IllegalArgumentException("Orphaned conquest tile");
        return r.tileId;
    }
    static String invalidAssociation(KOMEWorldData data, KOMEPublicWaypoint r) {
        try {
            if (!r.tileId.equals(resolveTile(data, r.dimension, r.x, r.z)))
                return "Position does not belong to linked tile";
            if (r.source == KOMEPublicWaypoint.Source.NATIVE) {
                LOTRWaypoint nativePoint = LOTRWaypoint.waypointForName(r.sourceKey);
                if (nativePoint == null || nativePoint.isHidden() || !nativePoint.getCodeName().equals(r.sourceKey)
                        || nativePoint.getXCoord() != r.x || nativePoint.getZCoord() != r.z
                        || nativePoint.getYCoordSaved() != r.y)
                    return "Invalid native waypoint association; native geometry cannot be moved";
            }
            return null;
        } catch (IllegalArgumentException invalid) { return invalid.getMessage(); }
    }

    private void prepareAudit(String actor, long now, String action, UUID id,
            NBTTagCompound before, NBTTagCompound after, String reason) {
        KOMEPublicWaypoint.actor(actor);
        if (now < 0) throw new IllegalArgumentException("Invalid review time");
        NBTTagCompound n = new NBTTagCompound();
        n.setString("Entity", id.toString()); n.setString("Actor", actor);
        n.setString("Action", action); n.setLong("At", now); n.setString("Reason", reason);
        if (before != null) n.setTag("Before", before.copy());
        if (after != null) n.setTag("After", after.copy());
        history.add(n);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound n = new NBTTagCompound();
        n.setInteger("Schema", SCHEMA); n.setLong("NextWire", nextWire); n.setLong("Revision", revision);
        NBTTagList rows = new NBTTagList();
        for (KOMEPublicWaypoint r : records.values()) rows.appendTag(r.writeToNBT());
        n.setTag("Records", rows); n.setTag("History", rowList(history)); n.setTag("Quarantine", rowList(quarantine));
        NBTTagList aliases = new NBTTagList();
        for (Map.Entry<String, UUID> entry : cutover.entrySet()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("Legacy", entry.getKey()); row.setString("Waypoint", entry.getValue().toString());
            aliases.appendTag(row);
        }
        n.setTag("Cutover", aliases); return n;
    }

    static KOMEPublicWaypointRegistry read(KOMEWorldData data, NBTTagCompound n) {
        KOMEPublicWaypoint.require(n, "Schema", 3);
        if (n.getInteger("Schema") != SCHEMA) throw new IllegalArgumentException("Unsupported public waypoint schema");
        KOMEPublicWaypoint.require(n, "NextWire", 4); KOMEPublicWaypoint.require(n, "Revision", 4);
        KOMEPublicWaypointRegistry result = new KOMEPublicWaypointRegistry();
        result.nextWire = n.getLong("NextWire"); result.revision = n.getLong("Revision");
        if (result.nextWire < 1 || result.nextWire > (long) Integer.MAX_VALUE + 1 || result.revision < 0)
            throw new IllegalArgumentException("Invalid public waypoint counters");
        NBTTagList rows = list(n, "Records", MAX_RECORDS);
        List<KOMEPublicWaypoint> candidates = new ArrayList<KOMEPublicWaypoint>();
        Map<UUID, Integer> ids = new HashMap<UUID, Integer>();
        Map<Integer, Integer> wireIds = new HashMap<Integer, Integer>();
        Map<String, Integer> tiles = new HashMap<String, Integer>();
        List<NBTTagCompound> raw = new ArrayList<NBTTagCompound>();
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            try {
                KOMEPublicWaypoint r = KOMEPublicWaypoint.read(row);
                if (!data.conquestTiles.containsKey(r.tileId)
                        || !KOMEConquestTileDefaults.getKnownTileIds().contains(r.tileId))
                    throw new IllegalArgumentException("Orphaned tile");
                if (-(long) r.wireId >= result.nextWire) throw new IllegalArgumentException("Wire ID is beyond allocation counter");
                // Unavailable resources suspend queries/travel; do not destroy records due to a transient load failure.
                if (KOMETileWorldResolver.INSTANCE.snapshot().isPresent()) {
                    String invalid = invalidAssociation(data, r);
                    if (invalid != null) throw new IllegalArgumentException(invalid);
                }
                candidates.add(r); raw.add(row);
                increment(ids, r.id); increment(wireIds, r.wireId); increment(tiles, r.tileId);
            } catch (RuntimeException malformed) { result.quarantine("Records", row, malformed.getMessage()); }
        }
        for (int i = 0; i < candidates.size(); i++) {
            KOMEPublicWaypoint r = candidates.get(i);
            if (ids.get(r.id) > 1 || wireIds.get(r.wireId) > 1 || tiles.get(r.tileId) > 1)
                result.quarantine("Records", raw.get(i), "Conflicting ID, wire ID or tile; all contenders withheld");
            else result.records.put(r.id, r);
        }
        for (NBTTagCompound row : copyList(list(n, "History", MAX_HISTORY))) {
            KOMEPublicWaypoint.require(row, "Entity", 8); KOMEPublicWaypoint.uuid(row.getString("Entity"));
            KOMEPublicWaypoint.require(row, "Actor", 8); KOMEPublicWaypoint.actor(row.getString("Actor"));
            KOMEPublicWaypoint.require(row, "Action", 8); KOMEPublicWaypoint.text(row.getString("Action"), 32, false);
            KOMEPublicWaypoint.require(row, "At", 4);
            KOMEPublicWaypoint.require(row, "Reason", 8); KOMEPublicWaypoint.text(row.getString("Reason"), 256, true);
            if (row.getLong("At") < 0) throw new IllegalArgumentException("Invalid review timestamp");
            result.history.add(row);
        }
        for (NBTTagCompound row : copyList(list(n, "Quarantine", MAX_QUARANTINE))) {
            if (result.quarantine.size() >= MAX_QUARANTINE) throw new IllegalArgumentException("Quarantine capacity exceeded");
            result.quarantine.add(row);
        }
        for (NBTTagCompound row : copyList(list(n, "Cutover", MAX_RECORDS))) {
            KOMEPublicWaypoint.require(row, "Legacy", 8); KOMEPublicWaypoint.require(row, "Waypoint", 8);
            String legacy = KOMEPublicWaypoint.legacyIdentity(row.getString("Legacy"));
            UUID id = KOMEPublicWaypoint.uuid(row.getString("Waypoint"));
            if (result.cutover.put(legacy, id) != null) throw new IllegalArgumentException("Duplicate cutover identity");
        }
        for (KOMEPublicWaypoint r : result.records.values())
            if (r.source == KOMEPublicWaypoint.Source.MIGRATED
                    && !r.id.equals(result.cutover.get(r.sourceKey)))
                throw new IllegalArgumentException("Migrated destination lacks exact cutover identity");
        return result;
    }
    private void quarantine(String section, NBTTagCompound row, String reason) {
        if (quarantine.size() >= MAX_QUARANTINE) throw new IllegalArgumentException("Quarantine capacity exceeded");
        NBTTagCompound q = new NBTTagCompound();
        q.setString("Section", section); q.setString("Reason", reason == null ? "Malformed record" : reason);
        q.setTag("Original", row.copy()); quarantine.add(q);
    }
    static NBTTagList list(NBTTagCompound n, String key, int max) {
        KOMEPublicWaypoint.require(n, key, 9);
        NBTTagList list = (NBTTagList) n.getTag(key);
        if (list.tagCount() > max || list.func_150303_d() != 10
                && !(list.tagCount() == 0 && list.func_150303_d() == 0))
            throw new IllegalArgumentException("Invalid/bounded compound list: " + key);
        NBTTagList checked = (NBTTagList) list.copy();
        for (int i = checked.tagCount() - 1; i >= 0; i--)
            if (!(checked.removeTag(i) instanceof NBTTagCompound))
                throw new IllegalArgumentException("Invalid actual list element: " + key);
        return list;
    }
    private static <T> void increment(Map<T, Integer> counts, T key) {
        Integer previous = counts.get(key); counts.put(key, previous == null ? 1 : previous + 1);
    }
    private static List<NBTTagCompound> copyList(NBTTagList list) {
        List<NBTTagCompound> result = new ArrayList<NBTTagCompound>();
        for (int i = 0; i < list.tagCount(); i++) result.add((NBTTagCompound) list.getCompoundTagAt(i).copy());
        return result;
    }
    private static List<NBTTagCompound> copyRows(List<NBTTagCompound> rows) {
        List<NBTTagCompound> result = new ArrayList<NBTTagCompound>();
        for (NBTTagCompound row : rows) result.add((NBTTagCompound) row.copy());
        return Collections.unmodifiableList(result);
    }
    private static NBTTagList rowList(List<NBTTagCompound> rows) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound row : rows) list.appendTag(row.copy());
        return list;
    }
    void replaceFrom(KOMEPublicWaypointRegistry other) {
        records.clear(); records.putAll(other.records);
        // Rows are private and never mutated after construction; public readers receive deep copies.
        // Candidate publication transfers these immutable-by-encapsulation rows without invoking NBT parsing/copy hooks.
        history.clear(); history.addAll(other.history);
        quarantine.clear(); quarantine.addAll(other.quarantine);
        cutover.clear(); cutover.putAll(other.cutover);
        nextWire = other.nextWire; revision = other.revision;
    }
}
