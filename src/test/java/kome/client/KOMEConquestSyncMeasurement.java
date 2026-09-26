package kome.client;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.net.*;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import kome.common.*;
import kome.common.data.*;
import kome.common.network.*;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.*;

/** Opt-in loopback transport measurement, not a graphics/FML-login or multiplayer test. */
public final class KOMEConquestSyncMeasurement {
    public static void main(String[] args) throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID = 100;
            if (!KOMETileWorldResolver.INSTANCE.reloadBundled()) throw new IllegalStateException(KOMETileWorldResolver.INSTANCE.loadDiagnostic());
            KOMEAccessFixture fixture = new KOMEAccessFixture();
            try (InputStream in = new FileInputStream(args[0])) {
                fixture.data.readFromNBT(CompressedStreamTools.readCompressed(in).getCompoundTag("data"));
            }
            Transport network = KOMEAccessFixture.allocate(Transport.class);
            network.start();
            SimpleNetworkWrapper previous = KOMEPacketHandler.network;
            KOMECommonProxy oldProxy = KOMEAddon.proxy;
            Client client = new Client(); KOMEAddon.proxy = client;
            KOMEPacketHandler.network = network;
            try {
                List<String> results = new ArrayList<String>();
                java.lang.reflect.Method publish;
                try { publish = KOMEPacketConquestData.class.getMethod("sendIfChanged", KOMEWorldData.class, EntityPlayerMP.class); }
                catch (NoSuchMethodException baseline) { publish = KOMEPacketConquestData.class.getMethod("sendChunked", KOMEWorldData.class, EntityPlayerMP.class); }
                // Warm the codec and model paths equally, then measure full snapshot requests.
                for (int i=0;i<10;i++) { KOMEPacketConquestData.sendChunked(fixture.data, fixture.player); network.await(); }
                for (String phase : new String[]{"idle", "active"}) {
                    network.reset(); long start=System.nanoTime();
                    long cpu=0;
                    long worst=0, changeLatency=0, worstChange=0; int changes=0;
                    for(int i=0;i<100;i++) {
                        // Ten meaningful public owner changes amid ninety redundant requests.
                        if(phase.equals("active") && i%10==0) {
                            KOMEConquestTile tile=fixture.data.conquestTiles.get("T132");
                            tile.claim(i%20==0?"gondor":"dunedain",i);
                            tile.claimedAtMillis=1000+i; changes++;
                        }
                        long request=System.nanoTime();
                        long cpuStart=ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
                        publish.invoke(null,fixture.data,fixture.player);
                        cpu+=ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime()-cpuStart;
                        network.await(); long latency=System.nanoTime()-request; worst=Math.max(worst,latency);
                        if(phase.equals("active") && i%10==0) { changeLatency+=latency; worstChange=Math.max(worstChange,latency); }
                    }
                    long wall=System.nanoTime()-start;
                    String row="phase="+phase+" requests=100 actualChanges="+changes
                        +" packets="+network.sent+" handlerCalls="+network.received
                        +" payloadBytes="+network.payload+" tcpStreamBytes="+network.streamBytes
                        +" completedSnapshots="+network.completed+" elapsedNs="+wall
                        +" serverThreadCpuNs="+cpu+" encodeNs="+network.encodeNanos
                        +" clientDecodeNs="+network.decodeNanos+" clientHandlerPublishNs="+network.handlerNanos
                        +" maxRequestThroughPublicationNs="+worst
                        +" meanChangedRequestThroughPublicationNs="+(changes==0?0:changeLatency/changes)
                        +" maxChangedRequestThroughPublicationNs="+worstChange;
                    results.add(row); System.out.println(row);
                }
                Files.write(Paths.get(args[1]),results,StandardCharsets.UTF_8);
            } finally {network.close();KOMEPacketHandler.network=previous;KOMEAddon.proxy=oldProxy;}
        }
    }
    private static final class Client extends KOMECommonProxy {
        private final KOMEClientTaskQueue tasks = new KOMEClientTaskQueue(() -> true);
        private final KOMEConquestSnapshotPublisher publisher = new KOMEConquestSnapshotPublisher(tasks);
        Client() { tasks.resetSession(true, () -> {}); tasks.drain(); }
        @Override public void acceptConquestSnapshotChunk(KOMEPacketConquestData.PublicationChunk chunk) {
            publisher.accept(chunk);
        }
    }
    private static final class Transport extends SimpleNetworkWrapper implements AutoCloseable {
        Socket sender,receiver; ServerSocket listen; DataOutputStream out; Thread worker;
        volatile Throwable failure; volatile long received,completed,decodeNanos,handlerNanos;
        long sent,payload,streamBytes,encodeNanos;
        private Transport(){super("unused");}
        void start() throws Exception {
            listen=new ServerSocket(0,1,InetAddress.getLoopbackAddress());
            sender=new Socket(InetAddress.getLoopbackAddress(),listen.getLocalPort());
            receiver=listen.accept();sender.setTcpNoDelay(true);receiver.setTcpNoDelay(true);
            out=new DataOutputStream(sender.getOutputStream());
            worker=new Thread(() -> {
                try(DataInputStream input=new DataInputStream(receiver.getInputStream())) {
                    while(true) {
                        int length=input.readInt();if(length<0)break;
                        byte[] bytes=new byte[length];input.readFully(bytes);
                        ByteBuf buffer=Unpooled.wrappedBuffer(bytes);
                        try {
                            long t=System.nanoTime();KOMEPacketConquestData packet=new KOMEPacketConquestData();
                            packet.fromBytes(buffer);decodeNanos+=System.nanoTime()-t;
                            t=System.nanoTime();new KOMEPacketConquestData.Handler().onMessage(packet,null);
                            handlerNanos+=System.nanoTime()-t;if(packet.complete)completed++;
                        } finally {buffer.release();}
                        received++;
                    }
                } catch(Throwable t){failure=t;}
            },"KOM74 loopback client");worker.start();
        }
        @Override public void sendTo(IMessage message,EntityPlayerMP player) {
            try {
                ByteBuf buffer=Unpooled.buffer();byte[] bytes;
                try {long t=System.nanoTime();message.toBytes(buffer);encodeNanos+=System.nanoTime()-t;
                    bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);
                } finally {buffer.release();}
                out.writeInt(bytes.length);out.write(bytes);out.flush();
                payload+=bytes.length;streamBytes+=bytes.length+4;sent++;
            } catch(Exception e){throw new IllegalStateException(e);}
        }
        void await() throws Exception {
            long deadline=System.nanoTime()+30_000_000_000L;
            while(received!=sent){if(failure!=null)throw new AssertionError(failure);
                if(System.nanoTime()>deadline)throw new AssertionError("Loopback receive timeout");Thread.yield();}
        }
        void reset() throws Exception {await();sent=received=completed=payload=streamBytes=encodeNanos=decodeNanos=handlerNanos=0;}
        public void close() throws Exception {out.writeInt(-1);out.flush();worker.join(10000);sender.close();receiver.close();listen.close();}
    }
}
