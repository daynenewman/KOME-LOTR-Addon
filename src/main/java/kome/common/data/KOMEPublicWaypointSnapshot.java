package kome.common.data;
import java.util.*;
import io.netty.buffer.ByteBuf;
import kome.common.network.KOMEPopulationWire;

/** Immutable public projection; no proposal, submitter, review or native persistence state. */
public final class KOMEPublicWaypointSnapshot {
    public final List<KOMEPublicWaypointRegistry.View> entries;
    public final Map<String,UUID> cutover;
    public KOMEPublicWaypointSnapshot(List<KOMEPublicWaypointRegistry.View> entries,Map<String,UUID> cutover) {
        if(entries.size()>KOMEPublicWaypointRegistry.MAX_RECORDS || cutover.size()>KOMEPublicWaypointRegistry.MAX_RECORDS)
            throw new IllegalArgumentException("Public snapshot capacity exceeded");
        Set<UUID> ids=new HashSet<UUID>(); Set<Integer> wires=new HashSet<Integer>(); Set<String> tiles=new HashSet<String>();
        List<KOMEPublicWaypointRegistry.View> copy=new ArrayList<KOMEPublicWaypointRegistry.View>();
        for(KOMEPublicWaypointRegistry.View view:entries) {
            KOMEPublicWaypoint r=view.record;
            if(!ids.add(r.id) || !wires.add(r.wireId) || !tiles.add(r.tileId)) throw new IllegalArgumentException("Duplicate public identity/tile");
            if(!KOMEConquestTileDefaults.getKnownTileIds().contains(r.tileId)) throw new IllegalArgumentException("Unknown public tile");
            owner(view.defaultOwner); owner(view.currentOwner);
            if(r.source==KOMEPublicWaypoint.Source.NATIVE) {
                lotr.common.world.map.LOTRWaypoint point=lotr.common.world.map.LOTRWaypoint.waypointForName(r.sourceKey);
                if(point==null || point.isHidden() || !point.getCodeName().equals(r.sourceKey) || point.getXCoord()!=r.x
                    || point.getYCoordSaved()!=r.y || point.getZCoord()!=r.z) throw new IllegalArgumentException("Invalid public native association");
            }
            copy.add(new KOMEPublicWaypointRegistry.View(KOMEPublicWaypoint.presentation(r.id,r.wireId,r.tileId,r.name,r.source,
                r.sourceKey,r.dimension,r.x,r.y,r.z,r.level),view.defaultOwner,view.currentOwner));
        }
        SortedMap<String,UUID> aliases=new TreeMap<String,UUID>();
        for(Map.Entry<String,UUID> alias:cutover.entrySet()) {
            KOMEPublicWaypoint.legacyIdentity(alias.getKey());
            if(alias.getValue()==null) throw new IllegalArgumentException("Missing cutover target");
            aliases.put(alias.getKey(),alias.getValue());
        }
        for(KOMEPublicWaypointRegistry.View v:copy) if(v.record.source==KOMEPublicWaypoint.Source.MIGRATED
                && !v.record.id.equals(aliases.get(v.record.sourceKey))) throw new IllegalArgumentException("Missing exact public cutover alias");
        for(Map.Entry<String,UUID> alias:aliases.entrySet()) for(KOMEPublicWaypointRegistry.View v:copy)
            if(v.record.id.equals(alias.getValue()) && (v.record.source!=KOMEPublicWaypoint.Source.MIGRATED || !v.record.sourceKey.equals(alias.getKey())))
                throw new IllegalArgumentException("Inconsistent public cutover target");
        this.entries=Collections.unmodifiableList(copy); this.cutover=Collections.unmodifiableMap(aliases);
    }
    private static void owner(String s) {
        KOMEPublicWaypoint.text(s,128,true);
        if(!s.equals(KOMEAlliance.normalizeFactionKey(s)) || !s.isEmpty() && KOMEAlliance.findLotrFaction(s)==null)
            throw new IllegalArgumentException("Noncanonical public owner");
    }
    public static KOMEPublicWaypointSnapshot from(KOMEWorldData data) {
        return new KOMEPublicWaypointSnapshot(data.publicWaypoints.views(data),data.publicWaypoints.cutoverIdentities());
    }
    public KOMEPublicWaypointRegistry.View byId(UUID id) {
        for(KOMEPublicWaypointRegistry.View v:entries) if(v.record.id.equals(id)) return v; return null;
    }
    public KOMEPublicWaypointRegistry.View byWire(int id) {
        for(KOMEPublicWaypointRegistry.View v:entries) if(v.record.wireId==id) return v; return null;
    }
    public static void writeView(ByteBuf b,KOMEPublicWaypointRegistry.View v) {
        KOMEPublicWaypoint r=v.record; uuid(b,r.id); b.writeInt(r.wireId);
        for(String s:new String[]{r.tileId,r.name,r.source.name(),r.sourceKey}) KOMEPopulationWire.writeText(b,s);
        b.writeInt(r.dimension); b.writeInt(r.x); b.writeInt(r.y); b.writeInt(r.z); b.writeInt(r.level);
        KOMEPopulationWire.writeText(b,v.defaultOwner); KOMEPopulationWire.writeText(b,v.currentOwner);
    }
    public static KOMEPublicWaypointRegistry.View readView(ByteBuf b) {
        UUID id=uuid(b); int wire=b.readInt(); String tile=KOMEPopulationWire.readText(b),name=KOMEPopulationWire.readText(b);
        KOMEPublicWaypoint.Source source=KOMEPublicWaypoint.Source.valueOf(KOMEPopulationWire.readText(b)); String key=KOMEPopulationWire.readText(b);
        KOMEPublicWaypoint r=KOMEPublicWaypoint.presentation(id,wire,tile,name,source,key,b.readInt(),b.readInt(),b.readInt(),b.readInt(),b.readInt());
        String defaultOwner=KOMEPopulationWire.readText(b),currentOwner=KOMEPopulationWire.readText(b); owner(defaultOwner); owner(currentOwner);
        return new KOMEPublicWaypointRegistry.View(r,defaultOwner,currentOwner);
    }
    public static void uuid(ByteBuf b,UUID id) { b.writeLong(id.getMostSignificantBits()); b.writeLong(id.getLeastSignificantBits()); }
    public static UUID uuid(ByteBuf b) { return new UUID(b.readLong(),b.readLong()); }
    @Override public boolean equals(Object value) {
        if(!(value instanceof KOMEPublicWaypointSnapshot)) return false;
        KOMEPublicWaypointSnapshot other=(KOMEPublicWaypointSnapshot)value;
        if(!cutover.equals(other.cutover) || entries.size()!=other.entries.size()) return false;
        for(int i=0;i<entries.size();i++) {
            KOMEPublicWaypointRegistry.View a=entries.get(i),b=other.entries.get(i);
            if(!samePublic(a.record,b.record) || !a.defaultOwner.equals(b.defaultOwner) || !a.currentOwner.equals(b.currentOwner)) return false;
        }
        return true;
    }
    public static boolean samePublic(KOMEPublicWaypoint a,KOMEPublicWaypoint b) {
        return KOMEPublicWaypointBridge.sameDestination(a,b) && a.name.equals(b.name) && a.level==b.level;
    }
    @Override public int hashCode() { return entries.size()*31+cutover.hashCode(); }
}
