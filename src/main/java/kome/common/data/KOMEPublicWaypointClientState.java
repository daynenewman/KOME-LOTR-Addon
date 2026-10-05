package kome.common.data;
import java.util.*;
import kome.common.network.KOMEPacketPublicWaypoints;

/** Connection identity and publication sequence fence late packets; complete immutable state only. */
public final class KOMEPublicWaypointClientState {
    public static final KOMEPublicWaypointClientState INSTANCE=new KOMEPublicWaypointClientState();
    private static final KOMEPublicWaypointSnapshot EMPTY=new KOMEPublicWaypointSnapshot(Collections.<KOMEPublicWaypointRegistry.View>emptyList(),Collections.<String,UUID>emptyMap());
    private volatile KOMEPublicWaypointSnapshot snapshot=EMPTY;
    private Object connection;
    private UUID serverSession;
    private long publishedSequence,assemblingSequence;
    private KOMEPacketPublicWaypoints.Chunk[] pending;
    public synchronized void start(Object connection) {
        this.connection=connection; serverSession=null; publishedSequence=0; assemblingSequence=0; pending=null; snapshot=EMPTY;
    }
    public KOMEPublicWaypointSnapshot snapshot() { return snapshot; }
    public synchronized boolean accept(Object source,KOMEPacketPublicWaypoints.Chunk chunk) {
        if(connection==null || source!=connection || chunk.sequence<=publishedSequence) return false;
        if(serverSession!=null && !serverSession.equals(chunk.session)) return false;
        if(serverSession==null) serverSession=chunk.session;
        if(chunk.sequence<assemblingSequence) return false;
        if(chunk.sequence>assemblingSequence) { assemblingSequence=chunk.sequence; pending=new KOMEPacketPublicWaypoints.Chunk[chunk.total]; }
        if(pending==null || pending.length!=chunk.total) { pending=null; return false; }
        // Retransmissions may repeat an entire publication after a failed write. Conflicting repeated chunks are invalid.
        KOMEPacketPublicWaypoints.Chunk prior=pending[chunk.index];
        if(prior!=null && !same(prior,chunk)) { pending=null; return false; }
        pending[chunk.index]=chunk;
        for(KOMEPacketPublicWaypoints.Chunk part:pending) if(part==null) return false;
        List<KOMEPublicWaypointRegistry.View> entries=new ArrayList<KOMEPublicWaypointRegistry.View>();
        Map<String,UUID> aliases=new TreeMap<String,UUID>();
        try {
            for(KOMEPacketPublicWaypoints.Chunk part:pending) {
                entries.addAll(part.entries);
                for(Map.Entry<String,UUID> alias:part.cutover.entrySet()) if(aliases.put(alias.getKey(),alias.getValue())!=null)
                    throw new IllegalArgumentException("Duplicate alias across chunks");
            }
            KOMEPublicWaypointSnapshot complete=new KOMEPublicWaypointSnapshot(entries,aliases);
            snapshot=complete; publishedSequence=chunk.sequence; pending=null; return true;
        } catch(IllegalArgumentException bad) { pending=null; return false; }
    }
    private static boolean same(KOMEPacketPublicWaypoints.Chunk a,KOMEPacketPublicWaypoints.Chunk b) {
        if(!a.cutover.equals(b.cutover) || a.entries.size()!=b.entries.size()) return false;
        for(int i=0;i<a.entries.size();i++) {
            KOMEPublicWaypointRegistry.View x=a.entries.get(i),y=b.entries.get(i);
            if(!KOMEPublicWaypointSnapshot.samePublic(x.record,y.record) || !x.defaultOwner.equals(y.defaultOwner) || !x.currentOwner.equals(y.currentOwner)) return false;
        }
        return true;
    }
}
