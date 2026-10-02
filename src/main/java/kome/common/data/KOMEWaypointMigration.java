package kome.common.data;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lotr.common.LOTRLevelData;
import lotr.common.fellowship.LOTRFellowship;
import lotr.common.fellowship.LOTRFellowshipData;
import lotr.common.world.map.LOTRCustomWaypoint;

/** Explicit read-only inventory and token-checked conversion. Native records remain owned by LOTR. */
public final class KOMEWaypointMigration {
    private KOMEWaypointMigration() { }
    public static final int MAX_INVENTORY=4096;
    public static final class Entry {
        public final String identity,name;
        public final int dimension,x,y,z;
        public Entry(UUID owner,int customId,String name,int dimension,int x,int y,int z) {
            if(owner==null || customId<0) throw new IllegalArgumentException("Invalid legacy identity");
            identity=owner+":"+customId; this.name=name; this.dimension=dimension; this.x=x; this.y=y; this.z=z;
        }
    }
    public static final class Row {
        public final Entry entry;
        public final String tile,problem;
        Row(Entry entry,String tile,String problem) { this.entry=entry; this.tile=tile; this.problem=problem; }
        public boolean eligible() { return problem.isEmpty(); }
    }
    public static final class Report {
        public final UUID fellowship;
        public final String token;
        public final List<Row> rows;
        Report(UUID fs,String token,List<Row> rows) {
            fellowship=fs; this.token=token; this.rows=Collections.unmodifiableList(rows);
        }
    }
    /** Native records do not store a dimension. The reviewer must explicitly supply the intended dimension. */
    public static List<Entry> inventory(UUID fellowship,int confirmedDimension) {
        LOTRFellowship fs=LOTRFellowshipData.getActiveFellowship(fellowship);
        if(fs==null) throw new IllegalArgumentException("Chosen fellowship is not active");
        List<Entry> result=new ArrayList<Entry>();
        List<UUID> owners=new ArrayList<UUID>(fs.getAllPlayerUUIDs()); Collections.sort(owners);
        for(UUID owner:owners) for(LOTRCustomWaypoint point:LOTRLevelData.getData(owner).getCustomWaypoints()) {
            if(!point.getSharedFellowshipIDs().contains(fellowship)) continue;
            if(result.size()>=MAX_INVENTORY) throw new IllegalArgumentException("Legacy inventory limit exceeded");
            result.add(new Entry(owner,point.getID(),point.getCodeName(),confirmedDimension,
                point.getXCoord(),point.getYCoordSaved(),point.getZCoord()));
        }
        return Collections.unmodifiableList(result);
    }
    public static Report dryRun(KOMEWorldData data,UUID fellowship,List<Entry> inventory) {
        if(data==null || fellowship==null || inventory==null || inventory.size()>MAX_INVENTORY)
            throw new IllegalArgumentException("Missing or oversized inventory");
        List<Entry> sorted=new ArrayList<Entry>(inventory);
        Collections.sort(sorted,Comparator.comparing((Entry e)->e.identity).thenComparing(e->String.valueOf(e.name))
            .thenComparingInt(e->e.dimension).thenComparingInt(e->e.x).thenComparingInt(e->e.y).thenComparingInt(e->e.z));
        List<Row> initial=new ArrayList<Row>(); Map<String,Integer> ids=new HashMap<String,Integer>(),tiles=new HashMap<String,Integer>();
        for(Entry e:sorted) {
            String tile="",problem="";
            try {
                KOMEPublicWaypoint.legacyIdentity(e.identity);
                tile=KOMEPublicWaypointRegistry.resolveTile(data,e.dimension,e.x,e.z);
                KOMEPublicWaypoint.validName(e.name);
                if(e.y<0 || e.y>255) throw new IllegalArgumentException("Legacy height unavailable/out of range");
                if(data.publicWaypoints.forTile(tile)!=null) problem="Tile already has approved destination";
                if(data.publicWaypoints.cutoverIdentities().containsKey(e.identity)) problem="Legacy identity already cut over";
            } catch(IllegalArgumentException bad) { problem=bad.getMessage(); }
            ids.put(e.identity,ids.getOrDefault(e.identity,0)+1);
            if(!tile.isEmpty()) tiles.put(tile,tiles.getOrDefault(tile,0)+1);
            initial.add(new Row(e,tile,problem));
        }
        List<Row> rows=new ArrayList<Row>();
        for(Row r:initial) {
            String problem=r.problem;
            if(ids.get(r.entry.identity)>1) problem="Duplicate legacy identity; all contenders withheld";
            else if(!r.tile.isEmpty() && tiles.get(r.tile)>1) problem="Several legacy destinations share tile; explicit design resolution required";
            rows.add(new Row(r.entry,r.tile,problem));
        }
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            feed(digest,fellowship.toString()); feed(digest,Long.toString(data.publicWaypoints.revision()));
            for(Row r:rows) for(String value:new String[]{r.entry.identity,String.valueOf(r.entry.name),Integer.toString(r.entry.dimension),
                    Integer.toString(r.entry.x),Integer.toString(r.entry.y),Integer.toString(r.entry.z),r.tile,r.problem}) feed(digest,value);
            StringBuilder hex=new StringBuilder(); for(byte b:digest.digest()) hex.append(String.format(Locale.ROOT,"%02x",b&255));
            return new Report(fellowship,hex.toString(),rows);
        } catch(java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void feed(MessageDigest digest,String value) {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte)(bytes.length>>>24)); digest.update((byte)(bytes.length>>>16));
        digest.update((byte)(bytes.length>>>8)); digest.update((byte)bytes.length); digest.update(bytes);
    }
    /** Caller supplies a fresh inventory read in the same authoritative server operation. */
    public static List<KOMEPublicWaypoint> convert(KOMEWorldData data,UUID fellowship,List<Entry> freshInventory,
            String expectedToken,Collection<String> selection,String actor,long now) {
        Report current=dryRun(data,fellowship,freshInventory);
        if(!current.token.equals(expectedToken)) throw new IllegalArgumentException("Dry run is stale; inspect a new inventory");
        Set<String> requested=new HashSet<String>(selection);
        if(requested.size()!=selection.size() || requested.isEmpty()) throw new IllegalArgumentException("Empty/duplicate conversion selection");
        List<Entry> chosen=new ArrayList<Entry>();
        for(Row row:current.rows) if(requested.remove(row.entry.identity)) {
            if(!row.eligible()) throw new IllegalArgumentException("Conversion blocked: "+row.entry.identity+" "+row.problem);
            chosen.add(row.entry);
        }
        if(!requested.isEmpty()) throw new IllegalArgumentException("Selection not present in chosen fellowship inventory");
        return data.publicWaypoints.importLegacy(data,chosen,actor,now);
    }
}
