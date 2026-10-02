package kome.common.network;
import java.util.*;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.*;
import kome.common.KOMEAddon;
import kome.common.data.*;

/** Bounded snapshot chunks; a client publishes only a validated complete snapshot. */
public final class KOMEPacketPublicWaypoints implements IMessage {
    public static final int ROWS=32,MAX_CHUNKS=64,MAX_BYTES=28000;
    private Chunk chunk;
    public KOMEPacketPublicWaypoints() { }
    public KOMEPacketPublicWaypoints(Chunk chunk) { this.chunk=chunk; }
    public static final class Chunk {
        public final UUID session;
        public final long sequence;
        public final int index,total;
        public final List<KOMEPublicWaypointRegistry.View> entries;
        public final Map<String,UUID> cutover;
        public Chunk(UUID session,long sequence,int index,int total,List<KOMEPublicWaypointRegistry.View> entries,Map<String,UUID> aliases) {
            if(session==null || sequence<1 || total<1 || total>MAX_CHUNKS || index<0 || index>=total
                    || entries.size()>ROWS || aliases.size()>ROWS || !entries.isEmpty() && !aliases.isEmpty())
                throw new IllegalArgumentException("Invalid public snapshot chunk");
            this.session=session; this.sequence=sequence; this.index=index; this.total=total;
            this.entries=Collections.unmodifiableList(new ArrayList<KOMEPublicWaypointRegistry.View>(entries));
            this.cutover=Collections.unmodifiableMap(new TreeMap<String,UUID>(aliases));
        }
    }
    public static List<Chunk> split(UUID session,long sequence,KOMEPublicWaypointSnapshot snapshot) {
        int n=Math.max(1,(snapshot.entries.size()+ROWS-1)/ROWS+(snapshot.cutover.size()+ROWS-1)/ROWS);
        List<Chunk> result=new ArrayList<Chunk>();
        for(int i=0;i<snapshot.entries.size();i+=ROWS) result.add(new Chunk(session,sequence,result.size(),n,
            snapshot.entries.subList(i,Math.min(i+ROWS,snapshot.entries.size())),Collections.<String,UUID>emptyMap()));
        List<Map.Entry<String,UUID>> aliases=new ArrayList<Map.Entry<String,UUID>>(snapshot.cutover.entrySet());
        for(int i=0;i<aliases.size();i+=ROWS) {
            Map<String,UUID> part=new TreeMap<String,UUID>();
            for(int j=i;j<Math.min(i+ROWS,aliases.size());j++) part.put(aliases.get(j).getKey(),aliases.get(j).getValue());
            result.add(new Chunk(session,sequence,result.size(),n,Collections.<KOMEPublicWaypointRegistry.View>emptyList(),part));
        }
        if(result.isEmpty()) result.add(new Chunk(session,sequence,0,1,Collections.<KOMEPublicWaypointRegistry.View>emptyList(),Collections.<String,UUID>emptyMap()));
        return result;
    }
    @Override public void toBytes(ByteBuf destination) {
        if(chunk==null) throw new IllegalArgumentException("Missing public snapshot chunk");
        KOMEPopulationWire.writePacket(destination,b->{
            KOMEPopulationWire.writeHeader(b); KOMEPublicWaypointSnapshot.uuid(b,chunk.session); b.writeLong(chunk.sequence);
            b.writeInt(chunk.index); b.writeInt(chunk.total); b.writeInt(chunk.entries.size());
            for(KOMEPublicWaypointRegistry.View v:chunk.entries) KOMEPublicWaypointSnapshot.writeView(b,v);
            b.writeInt(chunk.cutover.size());
            for(Map.Entry<String,UUID> a:chunk.cutover.entrySet()) { KOMEPopulationWire.writeText(b,a.getKey()); KOMEPublicWaypointSnapshot.uuid(b,a.getValue()); }
            if(b.readableBytes()>MAX_BYTES) throw new IllegalArgumentException("Public chunk too large");
        });
    }
    @Override public void fromBytes(ByteBuf b) {
        chunk=null;
        if(b.readableBytes()>MAX_BYTES) throw new IllegalArgumentException("Oversized public chunk");
        KOMEPopulationWire.readHeader(b); UUID session=KOMEPublicWaypointSnapshot.uuid(b); long sequence=b.readLong();
        int index=b.readInt(),total=b.readInt(),count=b.readInt();
        if(count<0 || count>ROWS) throw new IllegalArgumentException("Public row limit");
        List<KOMEPublicWaypointRegistry.View> entries=new ArrayList<KOMEPublicWaypointRegistry.View>();
        for(int i=0;i<count;i++) entries.add(KOMEPublicWaypointSnapshot.readView(b));
        count=b.readInt(); if(count<0 || count>ROWS) throw new IllegalArgumentException("Alias row limit");
        Map<String,UUID> aliases=new TreeMap<String,UUID>();
        for(int i=0;i<count;i++) {
            String key=KOMEPopulationWire.readText(b); UUID id=KOMEPublicWaypointSnapshot.uuid(b);
            if(aliases.put(key,id)!=null) throw new IllegalArgumentException("Duplicate cutover identity");
        }
        KOMEPopulationWire.requireFullyRead(b);
        // Validate per-chunk fields without requiring a migrated record's alias to arrive in the same chunk.
        for(String key:aliases.keySet()) {
            int colon=key.indexOf(':'); if(colon!=36) throw new IllegalArgumentException("Malformed legacy identity");
            UUID owner=UUID.fromString(key.substring(0,colon)); int custom=Integer.parseInt(key.substring(colon+1));
            if(!key.equals(owner+":"+custom) || custom<0) throw new IllegalArgumentException("Noncanonical legacy identity");
        }
        chunk=new Chunk(session,sequence,index,total,entries,aliases);
    }
    public Chunk publication(){ return chunk; }
    public static final class Handler implements IMessageHandler<KOMEPacketPublicWaypoints,IMessage> {
        @Override public IMessage onMessage(KOMEPacketPublicWaypoints message,MessageContext context) {
            KOMEPacketPublicWaypoints checked=KOMEPopulationWire.copyForPublication(message,KOMEPacketPublicWaypoints::new);
            KOMEAddon.proxy.acceptPublicWaypoints(checked.chunk,context.netHandler); return null;
        }
    }
}
