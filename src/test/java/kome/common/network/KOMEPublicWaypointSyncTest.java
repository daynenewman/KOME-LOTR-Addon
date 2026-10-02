package kome.common.network;
import java.util.*;
import java.lang.reflect.Field;
import java.io.IOException;
import cpw.mods.fml.common.*;
import cpw.mods.fml.common.network.*;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.common.network.simpleimpl.*;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.*;
import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import net.minecraft.network.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Real Forge encoder and actual NetworkManager write promises, plus atomic/lifecycle receiver cases. */
public class KOMEPublicWaypointSyncTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    SimpleNetworkWrapper previous; KOMEAccessFixture fixture; Transport transport;
    @Before public void before()throws Exception {
        previous=KOMEPacketHandler.network; fixture=new KOMEAccessFixture(); fixture.data.initializeIntegratedWorld();
        KOMEPublicWaypointSync.reset(); transport=new Transport(fixture); KOMEPacketHandler.network=transport.network;
    }
    @After public void after(){ KOMEPublicWaypointSync.reset(); KOMEPacketHandler.network=previous; transport.close(); }
    KOMEPublicWaypoint create(){ return fixture.data.publicWaypoints.approve(fixture.data,"Village",KOMETileTestResources.dimension(),
        KOMETileTestResources.x(),72,KOMETileTestResources.z(),20,KOMEPublicWaypoint.Source.PUBLIC,"","console",1,null); }
    void send(){ KOMEPublicWaypointSync.send(fixture.data,fixture.player); }
    @Test public void joinsReceiveFullStateAndOwnerRenameLevelMoveRemovePropagate(){
        KOMEPublicWaypoint r=create(); send(); assertEquals(1,transport.delivered.size());
        send(); assertEquals(1,transport.attempts); long rev=fixture.data.publicWaypoints.revision();
        fixture.data.conquestTiles.get(r.tileId).setCurrentRulingFaction("gondor"); send();
        assertEquals(rev,fixture.data.publicWaypoints.revision()); assertEquals("gondor",transport.last().entries.get(0).currentOwner);
        fixture.data.publicWaypoints.rename(fixture.data,r.id,"Changed","console",2); send();
        fixture.data.publicWaypoints.level(fixture.data,r.id,35,"console",3); send();
        fixture.data.publicWaypoints.move(fixture.data,r.id,r.dimension,r.x,r.y+1,r.z,"console",4); send();
        assertEquals("Changed",transport.last().entries.get(0).record.name); assertEquals(35,transport.last().entries.get(0).record.level);
        assertEquals(73,transport.last().entries.get(0).record.y);
        KOMEPublicWaypointSync.forget(fixture.player); int n=transport.attempts; send(); assertEquals(n+1,transport.attempts);
        fixture.data.publicWaypoints.remove(fixture.data,r.id,"console",5); send(); assertTrue(transport.last().entries.isEmpty());
    }
    @Test public void realFailedWriteRetriesFullPublicationOnNextCheckWithoutCallbackLoop()throws Exception{
        create(); transport.failAt=1; send(); assertEquals(1,transport.attempts); assertEquals(1,transport.errors.size());
        assertTrue(transport.delivered.isEmpty()); assertTrue(transport.wire.isOpen());
        int pending=KOMEPacketHandler.pendingServerTaskCount(); send(); assertEquals(2,transport.attempts);
        assertEquals(pending,KOMEPacketHandler.pendingServerTaskCount()); send(); assertEquals(2,transport.attempts);
        assertTrue(transport.attempted.get(1).sequence>transport.attempted.get(0).sequence);
    }
    @Test public void staleFailureCannotInvalidateNewerSuccessAndStaleSuccessCannotHideNewerFailure()throws Exception{
        KOMEPublicWaypoint r=create(); transport.holdAt=1; send(); send(); assertEquals(1,transport.attempts);
        fixture.data.publicWaypoints.level(fixture.data,r.id,21,"console",2); send(); transport.finishHeld(false); send(); assertEquals(2,transport.attempts);
        transport.holdAt=3; fixture.data.publicWaypoints.level(fixture.data,r.id,22,"console",3); send();
        transport.failAt=4; fixture.data.publicWaypoints.level(fixture.data,r.id,23,"console",4); send(); transport.finishHeld(true); send();
        assertEquals(5,transport.attempts); assertEquals(23,transport.last().entries.get(0).record.level);
    }
    @Test public void connectionSwitchAndRemovalFenceOldPacketsWithoutDuplicatePresentation(){
        KOMEPublicWaypoint r=create(); send(); Object a=new Object(),b=new Object();
        KOMEPublicWaypointClientState state=new KOMEPublicWaypointClientState(); state.start(a);
        assertTrue(state.accept(a,transport.last())); assertEquals(1,state.snapshot().entries.size());
        assertFalse(state.accept(a,transport.last())); state.start(b); assertTrue(state.snapshot().entries.isEmpty());
        assertFalse(state.accept(a,transport.last())); KOMEPublicWaypointSync.forget(fixture.player); send();
        assertTrue(state.accept(b,transport.last())); fixture.data.publicWaypoints.remove(fixture.data,r.id,"console",2); send();
        assertTrue(state.accept(b,transport.last())); assertTrue(state.snapshot().entries.isEmpty());
        assertFalse(state.accept(b,transport.attempted.get(0))); state.start(null); assertFalse(state.accept(b,transport.last()));
    }
    @Test public void partialAndConflictingChunksDoNotReplaceLastGoodSnapshot(){
        KOMEPublicWaypoint r=create(); send(); Object connection=new Object();
        KOMEPublicWaypointClientState state=new KOMEPublicWaypointClientState(); state.start(connection); assertTrue(state.accept(connection,transport.last()));
        KOMEPacketPublicWaypoints.Chunk old=transport.last(); KOMEPublicWaypointSnapshot stable=state.snapshot();
        KOMEPacketPublicWaypoints.Chunk part=new KOMEPacketPublicWaypoints.Chunk(old.session,old.sequence+1,0,2,old.entries,Collections.<String,UUID>emptyMap());
        assertFalse(state.accept(connection,part)); assertSame(stable,state.snapshot());
        KOMEPacketPublicWaypoints.Chunk duplicate=new KOMEPacketPublicWaypoints.Chunk(old.session,old.sequence+1,1,2,old.entries,Collections.<String,UUID>emptyMap());
        assertFalse(state.accept(connection,duplicate)); assertSame(stable,state.snapshot());
        KOMEPacketPublicWaypoints.Chunk empty=new KOMEPacketPublicWaypoints.Chunk(old.session,old.sequence+2,0,1,Collections.<KOMEPublicWaypointRegistry.View>emptyList(),Collections.<String,UUID>emptyMap());
        assertTrue(state.accept(connection,empty)); assertTrue(state.snapshot().entries.isEmpty());
    }
    @Test public void malformedWireAndTrailingBytesAreRejectedBeforePublication(){
        create(); send(); ByteBuf b=Unpooled.buffer();
        try {
            new KOMEPacketPublicWaypoints(transport.last()).toBytes(b); b.writeByte(99);
            try {new KOMEPacketPublicWaypoints().fromBytes(b); fail("Trailing byte accepted");} catch(IllegalArgumentException expected){}
            b.clear(); KOMEPopulationWire.writeHeader(b); KOMEPublicWaypointSnapshot.uuid(b,UUID.randomUUID()); b.writeLong(1);
            b.writeInt(0); b.writeInt(1); b.writeInt(33);
            try {new KOMEPacketPublicWaypoints().fromBytes(b); fail("Unbounded rows accepted");} catch(IllegalArgumentException expected){}
        } finally {b.release();}
    }
    @Test public void multiChunkCutoverIsAtomicAndFitsNativeCustomPayloadBounds(){
        KOMEPublicWaypoint r=create(); Map<String,UUID> aliases=new TreeMap<String,UUID>();
        for(int i=0;i<100;i++) aliases.put(new UUID(1,i)+":"+i,UUID.randomUUID());
        KOMEPublicWaypointSnapshot snapshot=new KOMEPublicWaypointSnapshot(fixture.data.publicWaypoints.views(fixture.data),aliases);
        List<KOMEPacketPublicWaypoints.Chunk> chunks=KOMEPacketPublicWaypoints.split(UUID.randomUUID(),1,snapshot); assertTrue(chunks.size()>1);
        KOMEPublicWaypointClientState state=new KOMEPublicWaypointClientState(); Object source=new Object(); state.start(source);
        Collections.reverse(chunks);
        for(int i=0;i<chunks.size();i++) {
            ByteBuf b=Unpooled.buffer(); try {
                new KOMEPacketPublicWaypoints(chunks.get(i)).toBytes(b); assertTrue(b.readableBytes()<28000);
                KOMEPacketPublicWaypoints decoded=new KOMEPacketPublicWaypoints(); decoded.fromBytes(b);
                assertEquals(i==chunks.size()-1,state.accept(source,decoded.publication()));
            } finally {b.release();}
        }
        assertEquals(100,state.snapshot().cutover.size()); assertEquals(1,state.snapshot().entries.size());
    }
    static void set(Class<?> c,Object obj,String name,Object value)throws Exception {Field f=c.getDeclaredField(name); f.setAccessible(true); f.set(obj,value);}
    static final class Transport {
        final SimpleNetworkWrapper network; final FMLEmbeddedChannel encoder; final EmbeddedChannel wire;
        final List<KOMEPacketPublicWaypoints.Chunk> attempted=new ArrayList<KOMEPacketPublicWaypoints.Chunk>(),delivered=new ArrayList<KOMEPacketPublicWaypoints.Chunk>();
        final List<Throwable> errors=new ArrayList<Throwable>(); int attempts,failAt=-1,holdAt=-1; ChannelPromise held;
        Transport(KOMEAccessFixture fixture)throws Exception {
            SimpleIndexedCodec codec=new SimpleIndexedCodec(); codec.addDiscriminator(38,KOMEPacketPublicWaypoints.class);
            encoder=new FMLEmbeddedChannel(new DummyModContainer(new ModMetadata()),"pubwpreview",Side.SERVER,codec);
            network=KOMEAccessFixture.allocate(SimpleNetworkWrapper.class); EnumMap<Side,FMLEmbeddedChannel> channels=new EnumMap<Side,FMLEmbeddedChannel>(Side.class);
            channels.put(Side.SERVER,encoder); set(SimpleNetworkWrapper.class,network,"channels",channels);
            wire=new EmbeddedChannel(new ChannelOutboundHandlerAdapter(){@Override public void write(ChannelHandlerContext ctx,Object message,ChannelPromise promise){
                FMLProxyPacket encoded=(FMLProxyPacket)message;
                try {
                    assertEquals(38,encoded.payload().getUnsignedByte(0)); assertTrue(encoded.payload().readableBytes()<28001);
                    KOMEPacketPublicWaypoints decoded=new KOMEPacketPublicWaypoints(); decoded.fromBytes(encoded.payload().slice(1,encoded.payload().readableBytes()-1));
                    attempted.add(decoded.publication()); attempts++;
                    if(attempts==failAt)promise.setFailure(new IOException("injected write failure"));
                    else if(attempts==holdAt)held=promise; else {delivered.add(decoded.publication()); promise.setSuccess();}
                } finally {encoded.payload().release();}
            }},new ChannelInboundHandlerAdapter(){@Override public void exceptionCaught(ChannelHandlerContext ctx,Throwable error){errors.add(error);}});
            NetworkManager manager=new NetworkManager(false); set(NetworkManager.class,manager,"channel",wire);
            set(NetHandlerPlayServer.class,fixture.player.playerNetServerHandler,"netManager",manager);
        }
        KOMEPacketPublicWaypoints.Chunk last(){return attempted.get(attempted.size()-1);}
        void finishHeld(boolean success)throws Exception {
            assertNotNull(held); Thread t=new Thread(()->{if(success)held.setSuccess();else held.setFailure(new IOException("delayed write failure"));}); t.start(); t.join(); held=null;
        }
        void close(){wire.finish();encoder.finish();}
    }
}
