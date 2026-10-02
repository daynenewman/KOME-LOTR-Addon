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
    public static final int MAX_PROPOSALS = 4096, MAX_PENDING_PER_TILE = 8;
    private final SortedMap<UUID, KOMEWaypointProposal> proposals = new TreeMap<UUID, KOMEWaypointProposal>();
    private volatile Map<String,UUID> cutoverRead=Collections.emptyMap();
    private void publishCutover(){ cutoverRead=Collections.unmodifiableMap(new TreeMap<String,UUID>(cutover)); }
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
    /** Bounded reason-only inspection avoids copying the retained original NBT evidence. */
    public List<String> quarantineReasons(UUID id, int limit) {
        if (id == null || limit < 1 || limit > 20)
            throw new IllegalArgumentException("Quarantine reason sample must have 1-20 rows");
        List<String> result = new ArrayList<String>();
        for (NBTTagCompound row : quarantine) {
            if (id.toString().equals(row.getCompoundTag("Original").getString("Id"))) {
                result.add(row.getString("Section") + ": " + row.getString("Reason"));
                if (result.size() == limit) break;
            }
        }
        return Collections.unmodifiableList(result);
    }
    public Map<String, UUID> cutoverIdentities() {
        return cutoverRead;
    }

    /** A read-only canonical projection; capture never overwrites waypoint level or persists owners. */
    public View view(KOMEWorldData data, UUID id) {
        KOMEPublicWaypoint r = get(id);
        if(data==null || data.isWriteBlocked()) return null;
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
        public View(KOMEPublicWaypoint r, String defaultOwner, String currentOwner) {
            record = r; this.defaultOwner = defaultOwner; this.currentOwner = currentOwner;
        }
    }

    public KOMEPublicWaypoint approve(KOMEWorldData data, String name, int dimension,
            int x, int y, int z, int level, KOMEPublicWaypoint.Source source, String sourceKey,
            String actor, long now, UUID proposalId) {
        writable(data);
        if(proposalId!=null) throw new IllegalArgumentException("Use the revalidated proposal approval workflow");
        KOMEPublicWaypoint r = prepareRecord(data,name,dimension,x,y,z,level,source,sourceKey,actor,now,proposalId,nextWire);
        prepareAudit(actor, now, "APPROVE", r.id, null, r.writeToNBT(), "Approved destination");
        install(r); data.markDirty(); return r;
    }
    private KOMEPublicWaypoint prepareRecord(KOMEWorldData data, String name, int dimension,
            int x, int y, int z, int level, KOMEPublicWaypoint.Source source, String sourceKey,
            String actor, long now, UUID proposalId, long allocation) {
        String tile = resolveTile(data, dimension, x, z);
        if (forTile(tile) != null) throw new IllegalArgumentException("Tile already has an approved waypoint: " + tile);
        if (records.size() >= MAX_RECORDS || allocation > Integer.MAX_VALUE || revision == Long.MAX_VALUE)
            throw new IllegalStateException("Waypoint registry capacity exhausted");
        KOMEPublicWaypoint r = new KOMEPublicWaypoint(UUID.randomUUID(), -(int) allocation,
            tile, name, source, sourceKey, dimension, x, y, z, level, actor, now, now, proposalId);
        String invalid = invalidAssociation(data, r);
        if (invalid != null) throw new IllegalArgumentException(invalid);
        if (source == KOMEPublicWaypoint.Source.MIGRATED && cutover.containsKey(sourceKey))
            throw new IllegalArgumentException("Legacy identity already converted");
        if (source == KOMEPublicWaypoint.Source.MIGRATED && cutover.size() >= MAX_RECORDS)
            throw new IllegalStateException("Cutover identity capacity exhausted");
        return r;
    }
    private void install(KOMEPublicWaypoint r) {
        records.put(r.id,r); nextWire++; revision++;
        if (r.source == KOMEPublicWaypoint.Source.MIGRATED) { cutover.put(r.sourceKey,r.id); publishCutover(); }
    }

    public List<KOMEWaypointProposal> proposals() {
        return Collections.unmodifiableList(new ArrayList<KOMEWaypointProposal>(proposals.values()));
    }
    public KOMEWaypointProposal proposal(UUID id) { return proposals.get(id); }
    public KOMEWaypointProposal propose(KOMEWorldData data, UUID submitter, String submitterName,
            String name, int dim, int x, int y, int z, long now) {
        writable(data); String tile=resolveTile(data,dim,x,z);
        if (forTile(tile)!=null) throw new IllegalArgumentException("Tile already has an approved waypoint");
        if (proposals.size()>=MAX_PROPOSALS) throw new IllegalStateException("Proposal history capacity exhausted");
        int pending=0;
        for(KOMEWaypointProposal q:proposals.values()) if(q.status==KOMEWaypointProposal.Status.PENDING && q.tileId.equals(tile)) {
            pending++;
            if(q.submitter.equals(submitter)) throw new IllegalArgumentException("You already have a pending proposal on this tile");
        }
        if(pending>=MAX_PENDING_PER_TILE) throw new IllegalArgumentException("Tile pending proposal limit reached");
        KOMEWaypointProposal q=new KOMEWaypointProposal(UUID.randomUUID(),submitter,submitterName,tile,name,dim,x,y,z,
            0,now,KOMEWaypointProposal.Status.PENDING,"","",now,0,null);
        prepareAudit(submitter.toString(),now,"PROPOSE",q.id,null,q.writeToNBT(),"Submitted for review");
        proposals.put(q.id,q); revision++; data.markDirty(); return q;
    }
    private KOMEWaypointProposal pending(UUID id,long expected) {
        KOMEWaypointProposal q=proposals.get(id);
        if(q==null || q.status!=KOMEWaypointProposal.Status.PENDING || q.version!=expected)
            throw new IllegalArgumentException("Proposal no longer pending or review version is stale");
        return q;
    }
    public KOMEWaypointProposal adjust(KOMEWorldData data, UUID id, long expected, String name,
            int dim,int x,int y,int z,int level,String actor,String reason,long now) {
        writable(data); KOMEWaypointProposal before=pending(id,expected);
        String tile=resolveTile(data,dim,x,z);
        if(forTile(tile)!=null) throw new IllegalArgumentException("Destination tile already occupied");
        int pending=0;
        for(KOMEWaypointProposal other:proposals.values()) if(!other.id.equals(id)
                && other.status==KOMEWaypointProposal.Status.PENDING && other.tileId.equals(tile)) {
            pending++;
            if(other.submitter.equals(before.submitter)) throw new IllegalArgumentException("Submitter already has a pending proposal on destination tile");
        }
        if(pending>=MAX_PENDING_PER_TILE) throw new IllegalArgumentException("Destination pending limit reached");
        KOMEWaypointProposal after=before.reviewed(tile,name,dim,x,y,z,level,KOMEWaypointProposal.Status.PENDING,
            actor,reason,now,null);
        prepareAudit(actor,now,"ADJUST",id,before.writeToNBT(),after.writeToNBT(),reason);
        proposals.put(id,after); revision++; data.markDirty(); return after;
    }
    public KOMEPublicWaypoint approveProposal(KOMEWorldData data,UUID id,long expected,String actor,String reason,long now) {
        writable(data); KOMEWaypointProposal before=pending(id,expected);
        if(history.size()>MAX_HISTORY-2) throw new IllegalStateException("Waypoint audit capacity exhausted");
        KOMEPublicWaypoint r=prepareRecord(data,before.name,before.dimension,before.x,before.y,before.z,before.level,
            KOMEPublicWaypoint.Source.PUBLIC,"",actor,now,id,nextWire);
        if(!r.tileId.equals(before.tileId)) throw new IllegalArgumentException("Proposal geometry changed; adjust and review again");
        KOMEWaypointProposal after=before.reviewed(before.tileId,before.name,before.dimension,before.x,before.y,before.z,
            before.level,KOMEWaypointProposal.Status.APPROVED,actor,reason,now,r.id);
        NBTTagCompound approval=audit(actor,now,"APPROVE",r.id,null,r.writeToNBT(),reason);
        NBTTagCompound review=audit(actor,now,"REVIEW_APPROVE",id,before.writeToNBT(),after.writeToNBT(),reason);
        history.add(approval); history.add(review); install(r); proposals.put(id,after); data.markDirty(); return r;
    }
    public void reject(KOMEWorldData data,UUID id,long expected,String actor,String reason,long now) {
        writable(data); KOMEWaypointProposal before=pending(id,expected);
        KOMEWaypointProposal after=before.reviewed(before.tileId,before.name,before.dimension,before.x,before.y,before.z,
            before.level,KOMEWaypointProposal.Status.REJECTED,actor,reason,now,null);
        prepareAudit(actor,now,"REJECT",id,before.writeToNBT(),after.writeToNBT(),reason);
        proposals.put(id,after); revision++; data.markDirty();
    }

    /** All validation/audit construction precedes publication; no native/fellowship writes. */
    public List<KOMEPublicWaypoint> importLegacy(KOMEWorldData data,List<KOMEWaypointMigration.Entry> entries,String actor,long now) {
        writable(data);
        if(entries.isEmpty() || entries.size()>MAX_RECORDS-records.size() || entries.size()>MAX_RECORDS-cutover.size()
                || entries.size()>MAX_HISTORY-history.size() || nextWire+entries.size()-1>Integer.MAX_VALUE
                || revision>Long.MAX_VALUE-entries.size()) throw new IllegalArgumentException("Import capacity exceeded or empty selection");
        Set<String> tiles=new HashSet<String>(), aliases=new HashSet<String>();
        List<KOMEPublicWaypoint> prepared=new ArrayList<KOMEPublicWaypoint>();
        List<NBTTagCompound> audits=new ArrayList<NBTTagCompound>();
        for(KOMEWaypointMigration.Entry entry:entries) {
            KOMEPublicWaypoint r=prepareRecord(data,entry.name,entry.dimension,entry.x,entry.y,entry.z,0,
                KOMEPublicWaypoint.Source.MIGRATED,entry.identity,actor,now,null,nextWire+prepared.size());
            if(!tiles.add(r.tileId) || !aliases.add(entry.identity)) throw new IllegalArgumentException("Ambiguous import selection");
            prepared.add(r); audits.add(audit(actor,now,"MIGRATE",r.id,null,r.writeToNBT(),"Explicit legacy cutover"));
        }
        history.addAll(audits); for(KOMEPublicWaypoint r:prepared) install(r); data.markDirty();
        return Collections.unmodifiableList(prepared);
    }
    public void rollbackLegacy(KOMEWorldData data,String identity,String actor,long now) {
        writable(data); KOMEPublicWaypoint.legacyIdentity(identity);
        UUID id=cutover.get(identity);
        if(id==null) throw new IllegalArgumentException("No cutover identity to roll back");
        KOMEPublicWaypoint record=get(id);
        if(record!=null && (record.source!=KOMEPublicWaypoint.Source.MIGRATED || !record.sourceKey.equals(identity)))
            throw new IllegalArgumentException("Inconsistent cutover target; inspect before rollback");
        NBTTagCompound prior=new NBTTagCompound(); prior.setString("Legacy",identity); prior.setString("Waypoint",id.toString());
        if(record!=null) prior.setTag("Record",record.writeToNBT());
        prepareAudit(actor,now,"ROLLBACK",id,prior,null,"Restore original legacy visibility without modifying native data");
        records.remove(id); cutover.remove(identity); publishCutover(); revision++; data.markDirty();
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

    /** Repair a public link from unchanged coordinates; native/cutover provenance is excluded. */
    KOMEPublicWaypoint repairPublicLink(KOMEWorldData data, UUID id, String actor, long now) {
        KOMEPublicWaypoint before = requireRecord(id);
        if (before.source != KOMEPublicWaypoint.Source.PUBLIC)
            throw new IllegalArgumentException("Only public waypoint links can be repaired");
        String tile = resolveTile(data, before.dimension, before.x, before.z);
        KOMEPublicWaypoint occupant = forTile(tile);
        if (tile.equals(before.tileId) || occupant != null && !occupant.id.equals(id))
            throw new IllegalArgumentException("Link is valid or destination tile is occupied");
        return replace(data, before, before.changed(tile, before.name, before.dimension,
            before.x, before.y, before.z, before.level, now), actor, now, "REPAIR_LINK");
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
        history.add(audit(actor,now,action,id,before,after,reason));
    }
    private NBTTagCompound audit(String actor,long now,String action,UUID id,NBTTagCompound before,NBTTagCompound after,String reason) {
        KOMEPublicWaypoint.actor(actor); KOMEPublicWaypoint.text(reason,256,true);
        if (now < 0) throw new IllegalArgumentException("Invalid review time");
        NBTTagCompound n = new NBTTagCompound();
        n.setString("Entity", id.toString()); n.setString("Actor", actor);
        n.setString("Action", action); n.setLong("At", now); n.setString("Reason", reason);
        if (before != null) n.setTag("Before", before.copy());
        if (after != null) n.setTag("After", after.copy());
        return n;
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
        n.setTag("Cutover", aliases);
        NBTTagList pending=new NBTTagList();
        for(KOMEWaypointProposal q:proposals.values()) pending.appendTag(q.writeToNBT());
        n.setTag("Proposals",pending); return n;
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
        if(n.hasKey("Proposals")) {
            List<KOMEWaypointProposal> parsed=new ArrayList<KOMEWaypointProposal>();
            List<NBTTagCompound> originals=new ArrayList<NBTTagCompound>();
            Map<UUID,Integer> proposalIds=new HashMap<UUID,Integer>();
            for(NBTTagCompound row:copyList(list(n,"Proposals",MAX_PROPOSALS))) {
                try {
                    KOMEWaypointProposal q=KOMEWaypointProposal.read(row);
                    if(!data.conquestTiles.containsKey(q.tileId) || !KOMEConquestTileDefaults.getKnownTileIds().contains(q.tileId))
                        throw new IllegalArgumentException("Orphaned proposal tile");
                    parsed.add(q); originals.add(row); increment(proposalIds,q.id);
                } catch(RuntimeException bad) { result.quarantine("Proposals",row,bad.getMessage()); }
            }
            Map<String,Integer> submitterTiles=new HashMap<String,Integer>(), pendingTiles=new HashMap<String,Integer>();
            for(KOMEWaypointProposal q:parsed) if(q.status==KOMEWaypointProposal.Status.PENDING) {
                increment(submitterTiles,q.submitter+":"+q.tileId); increment(pendingTiles,q.tileId);
            }
            for(int i=0;i<parsed.size();i++) {
                KOMEWaypointProposal q=parsed.get(i);
                if(proposalIds.get(q.id)>1 || q.status==KOMEWaypointProposal.Status.PENDING
                        && (submitterTiles.get(q.submitter+":"+q.tileId)>1 || pendingTiles.get(q.tileId)>MAX_PENDING_PER_TILE))
                    result.quarantine("Proposals",originals.get(i),"Conflicting proposal identity or pending limits; all contenders withheld");
                else result.proposals.put(q.id,q);
            }
        }
        for(Map.Entry<String,UUID> alias:result.cutover.entrySet()) {
            KOMEPublicWaypoint r=result.records.get(alias.getValue());
            if(r!=null && (r.source!=KOMEPublicWaypoint.Source.MIGRATED || !r.sourceKey.equals(alias.getKey())))
                throw new IllegalArgumentException("Cutover target does not match exact migrated source");
        }
        result.publishCutover(); return result;
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
        proposals.clear(); proposals.putAll(other.proposals);
        // Rows are private and never mutated after construction; public readers receive deep copies.
        // Candidate publication transfers these immutable-by-encapsulation rows without invoking NBT parsing/copy hooks.
        history.clear(); history.addAll(other.history);
        quarantine.clear(); quarantine.addAll(other.quarantine);
        cutover.clear(); cutover.putAll(other.cutover); cutoverRead=other.cutoverRead;
        nextWire = other.nextWire; revision = other.revision;
    }
}
