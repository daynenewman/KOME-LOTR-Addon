package kome.common.network;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import kome.common.data.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.server.MinecraftServer;

/** Server-thread complete publications, with a separate receipt for each actual Netty write attempt. */
public final class KOMEPublicWaypointSync {
    private KOMEPublicWaypointSync() { }
    private static UUID session=UUID.randomUUID();
    private static long sequence;
    private static final Map<EntityPlayerMP,Sent> sent=new IdentityHashMap<EntityPlayerMP,Sent>();
    public static void reset() { sent.clear(); session=UUID.randomUUID(); sequence=0; }
    public static void forget(EntityPlayerMP player) { sent.remove(player); }
    public static void send(KOMEWorldData data,EntityPlayerMP player) { send(data,player,KOMEPublicWaypointSnapshot.from(data)); }
    public static void tick(MinecraftServer server) {
        if(server==null || server.getConfigurationManager()==null) return;
        Map<KOMEWorldData,KOMEPublicWaypointSnapshot> projections=new IdentityHashMap<KOMEWorldData,KOMEPublicWaypointSnapshot>();
        for(Object entry:server.getConfigurationManager().playerEntityList) {
            EntityPlayerMP player=(EntityPlayerMP)entry; KOMEWorldData data=KOMEWorldData.get(player.worldObj);
            KOMEPublicWaypointSnapshot projection=projections.get(data);
            if(projection==null) { projection=KOMEPublicWaypointSnapshot.from(data); projections.put(data,projection); }
            send(data,player,projection);
        }
    }
    private static void send(KOMEWorldData data,EntityPlayerMP player,KOMEPublicWaypointSnapshot projection) {
        if(player==null) return;
        NetHandlerPlayServer connection=player.playerNetServerHandler;
        Sent prior=sent.get(player);
        if(prior!=null && prior.data==data && prior.connection==connection && !prior.failed.get() && prior.projection.equals(projection)) return;
        if(sequence==Long.MAX_VALUE) throw new IllegalStateException("Public waypoint publication sequence exhausted");
        Sent publication=new Sent(data,connection,projection);
        List<KOMEPacketPublicWaypoints.Chunk> chunks=KOMEPacketPublicWaypoints.split(session,++sequence,projection);
        if(sent.size()>=1024 && !sent.containsKey(player)) sent.remove(sent.keySet().iterator().next());
        sent.put(player,publication);
        if(connection==null || connection.netManager==null || !connection.netManager.isChannelOpen()) {
            publication.failed.set(true); return;
        }
        try {
            for(KOMEPacketPublicWaypoints.Chunk chunk:chunks) {
                Packet packet=KOMEPacketHandler.network.getPacketFrom(new KOMEPacketPublicWaypoints(chunk));
                if(packet==null) throw new IllegalStateException("Public waypoint encoding produced no packet");
                connection.netManager.scheduleOutboundPacket(packet,publication);
            }
        } catch(RuntimeException error) {
            publication.failed.set(true);
            System.err.println("[KOME] Public waypoint publication failed: "+error.getMessage());
        }
        // A write success is not a client application acknowledgement. Failed writes permit a new sequence on the next periodic check.
    }
    private static final class Sent implements GenericFutureListener<Future<? super Void>> {
        final KOMEWorldData data;
        final NetHandlerPlayServer connection;
        final KOMEPublicWaypointSnapshot projection;
        final AtomicBoolean failed=new AtomicBoolean();
        Sent(KOMEWorldData data,NetHandlerPlayServer connection,KOMEPublicWaypointSnapshot projection) {
            this.data=data; this.connection=connection; this.projection=projection;
        }
        @Override public void operationComplete(Future<? super Void> result) { if(!result.isSuccess()) failed.set(true); }
    }
}
