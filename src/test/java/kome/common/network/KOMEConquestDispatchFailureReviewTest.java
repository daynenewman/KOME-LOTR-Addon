package kome.common.network;

import cpw.mods.fml.common.DummyModContainer;
import cpw.mods.fml.common.ModMetadata;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.common.network.simpleimpl.SimpleIndexedCodec;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEEvents;
import kome.common.data.KOMEPopulationTestConfig;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import org.junit.*;
import static org.junit.Assert.*;

/** Real Forge encoding + NetworkManager dispatch; faults affect actual Netty write promises. */
public class KOMEConquestDispatchFailureReviewTest {
    private SimpleNetworkWrapper previous;
    private KOMEPopulationTestConfig config;
    private KOMEAccessFixture fixture;
    private final List<Transport> transports = new ArrayList<Transport>();
    private Transport transport;
    private int chunks;

    @Before public void setUp() throws Exception {
        previous = KOMEPacketHandler.network;
        config = new KOMEPopulationTestConfig();
        fixture = new KOMEAccessFixture();
        KOMEPacketConquestData.clearSentSnapshots();
        for (int i = 1; i <= 49; i++) {
            KOMEConquestTile tile = new KOMEConquestTile(String.format("T%03d", i));
            tile.claim("dunedain", 1); fixture.data.conquestTiles.put(tile.id, tile);
        }
        transport = install(fixture);
        send(); chunks = transport.attempts;
        assertTrue("Exercise a multi-chunk publication", chunks > 1);
    }
    @After public void tearDown() throws Exception {
        KOMEPacketConquestData.clearSentSnapshots();
        KOMEPacketHandler.network = previous;
        for (Transport wire : transports) wire.close();
        config.close();
    }
    private Transport install(KOMEAccessFixture target) throws Exception {
        Transport wire = new Transport(target);
        transports.add(wire); KOMEPacketHandler.network = wire.network; return wire;
    }
    private void send() { KOMEPacketConquestData.sendIfChanged(fixture.data, fixture.player); }
    private void change(String faction) { fixture.data.conquestTiles.get("T001").claim(faction, 2); }
    private void assertComplete(int start) {
        List<KOMEPacketConquestData> packets = transport.attempted.subList(start, transport.attempted.size());
        assertEquals(chunks, packets.size()); int resets = 0, completes = 0;
        for (KOMEPacketConquestData packet : packets) {
            if (packet.reset) resets++; if (packet.complete) completes++;
        }
        assertEquals(1, resets); assertEquals(1, completes);
        assertTrue(packets.get(0).reset); assertTrue(packets.get(chunks - 1).complete);
    }

    // Original review failure retained, now at the underlying connection rather than the void wrapper.
    @Test public void retriesCompleteSnapshotAfterRecoverableNettyWriteFailure() {
        change("gondor"); transport.failAt = chunks * 2;
        send(); assertEquals(chunks * 2, transport.attempts);
        assertEquals(1, transport.errors.size()); assertTrue(transport.wire.isOpen());
        assertFalse(transport.delivered.get(transport.delivered.size() - 1).complete);
        send(); assertEquals("Incomplete publication must retry all chunks", chunks * 3, transport.attempts);
        assertComplete(chunks * 2);
        send(); assertEquals("Successful retry suppresses unchanged requests", chunks * 3, transport.attempts);
    }
    @Test public void firstAndMiddleChunkFailuresAlsoPermitFullRetry() {
        for (int position : new int[]{1, 2}) {
            int start = transport.attempts; transport.failAt = start + position;
            KOMEPacketConquestData.sendChunked(fixture.data, fixture.player);
            send(); assertEquals(start + chunks * 2, transport.attempts);
            assertComplete(start + chunks);
        }
    }
    @Test public void delayedCurrentFailureIsReadOnServerThreadWithoutCallbackCacheMutation() throws Exception {
        change("gondor"); transport.holdAt = chunks * 2; send();
        Object current = cache().get(fixture.player);
        int tasks = KOMEPacketHandler.pendingServerTaskCount();
        send(); assertEquals("Pending identical requests coalesce", chunks * 2, transport.attempts);
        transport.finishHeld(false);
        assertSame("Callback only changes its transport receipt", current, cache().get(fixture.player));
        assertEquals("No callback tasks or retry loop", tasks, KOMEPacketHandler.pendingServerTaskCount());
        send(); assertEquals(chunks * 3, transport.attempts); assertComplete(chunks * 2);
    }
    @Test public void delayedOlderPublicationFailureCannotInvalidateNewerSuccess() throws Exception {
        change("gondor"); transport.holdAt = chunks * 2; send();
        change("rohan"); send(); int sent = transport.attempts;
        Object current = cache().get(fixture.player);
        transport.finishHeld(false); send();
        assertEquals(sent, transport.attempts); assertSame(current, cache().get(fixture.player));
    }
    @Test public void delayedOlderSuccessCannotHideNewerFailure() throws Exception {
        change("gondor"); transport.holdAt = chunks * 2; send();
        change("rohan"); transport.failAt = chunks * 3; send();
        transport.finishHeld(true); send();
        assertEquals(chunks * 4, transport.attempts); assertComplete(chunks * 3);
    }
    @Test public void failureAfterDataRevertsStillRetriesTheOriginalProjection() throws Exception {
        long originalTime = fixture.data.conquestTiles.get("T001").claimedAtMillis;
        change("gondor"); transport.holdAt = chunks * 2; send();
        transport.finishHeld(false);
        fixture.data.conquestTiles.get("T001").claim("dunedain", 1);
        fixture.data.conquestTiles.get("T001").claimedAtMillis = originalTime;
        send(); assertEquals(chunks * 3, transport.attempts); assertComplete(chunks * 2);
    }
    @Test public void logoutReconnectWithSameUuidIgnoresOldFailure() throws Exception {
        change("gondor"); transport.holdAt = chunks * 2; send(); Transport old = transport;
        new KOMEEvents().onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent(fixture.player));
        KOMEAccessFixture replacement = new KOMEAccessFixture(); replacement.player.id = fixture.player.id;
        Transport next = install(replacement);
        KOMEPacketConquestData.sendIfChanged(fixture.data, replacement.player);
        old.finishHeld(false);
        KOMEPacketConquestData.sendIfChanged(fixture.data, replacement.player);
        assertEquals(chunks, next.attempts); assertFalse(cache().containsKey(fixture.player));
    }
    @Test public void newConnectionOnSamePlayerIgnoresOldFailure() throws Exception {
        change("gondor"); transport.holdAt = chunks * 2; send(); Transport old = transport;
        fixture.player.playerNetServerHandler = KOMEAccessFixture.allocate(NetHandlerPlayServer.class);
        fixture.player.playerNetServerHandler.playerEntity = fixture.player;
        transport = install(fixture); send(); old.finishHeld(false); send();
        assertEquals(chunks, transport.attempts);
    }
    @Test public void serverSessionResetIgnoresOldFailure() throws Exception {
        change("gondor"); transport.holdAt = chunks * 2; send();
        new KOMEEvents().resetSessionState(); send(); transport.finishHeld(false); send();
        assertEquals(chunks * 3, transport.attempts);
    }
    @Test public void completedAndPendingSuccessfulWritesSuppressUnchangedRequests() throws Exception {
        for (int i = 0; i < 20; i++) send(); assertEquals(chunks, transport.attempts);
        change("gondor"); transport.holdAt = chunks * 2; send();
        for (int i = 0; i < 20; i++) send(); assertEquals(chunks * 2, transport.attempts);
        transport.finishHeld(true);
        for (int i = 0; i < 20; i++) send(); assertEquals(chunks * 2, transport.attempts);
    }
    @Test public void closedConnectionDoesNotEnqueueAndReplacementReceivesFullSnapshot() throws Exception {
        change("gondor"); transport.wire.close(); send();
        assertEquals(chunks, transport.attempts);
        fixture.player.playerNetServerHandler = KOMEAccessFixture.allocate(NetHandlerPlayServer.class);
        fixture.player.playerNetServerHandler.playerEntity = fixture.player;
        transport = install(fixture); send(); assertEquals(chunks, transport.attempts);
    }
    private static Map<?, ?> cache() throws Exception {
        Field field = KOMEPacketConquestData.class.getDeclaredField("SENT"); field.setAccessible(true);
        return (Map<?, ?>) field.get(null);
    }
    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static final class Transport {
        final SimpleNetworkWrapper network;
        final FMLEmbeddedChannel encoder;
        final EmbeddedChannel wire;
        final List<KOMEPacketConquestData> attempted = new ArrayList<KOMEPacketConquestData>();
        final List<KOMEPacketConquestData> delivered = new ArrayList<KOMEPacketConquestData>();
        final List<Throwable> errors = new ArrayList<Throwable>();
        int attempts, failAt = -1, holdAt = -1;
        ChannelPromise held;
        Transport(KOMEAccessFixture fixture) throws Exception {
            SimpleIndexedCodec codec = new SimpleIndexedCodec();
            codec.addDiscriminator(13, KOMEPacketConquestData.class);
            encoder = new FMLEmbeddedChannel(new DummyModContainer(new ModMetadata()), "kom74review", Side.SERVER, codec);
            network = KOMEAccessFixture.allocate(SimpleNetworkWrapper.class);
            EnumMap<Side, FMLEmbeddedChannel> channels = new EnumMap<Side, FMLEmbeddedChannel>(Side.class);
            channels.put(Side.SERVER, encoder); set(SimpleNetworkWrapper.class, network, "channels", channels);
            wire = new EmbeddedChannel(new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext ctx, Object message, ChannelPromise promise) {
                    FMLProxyPacket encoded = (FMLProxyPacket) message;
                    try {
                        assertEquals(13, encoded.payload().getUnsignedByte(0));
                        KOMEPacketConquestData decoded = new KOMEPacketConquestData();
                        decoded.fromBytes(encoded.payload().slice(1, encoded.payload().readableBytes() - 1));
                        attempted.add(decoded); attempts++;
                        if (attempts == failAt) promise.setFailure(new IOException("injected write failure"));
                        else if (attempts == holdAt) held = promise;
                        else { delivered.add(decoded); promise.setSuccess(); }
                    } finally { encoded.payload().release(); }
                }
            }, new ChannelInboundHandlerAdapter() {
                @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable error) { errors.add(error); }
            });
            NetworkManager manager = new NetworkManager(false);
            set(NetworkManager.class, manager, "channel", wire);
            set(NetHandlerPlayServer.class, fixture.player.playerNetServerHandler, "netManager", manager);
        }
        void finishHeld(boolean success) throws Exception {
            assertNotNull(held);
            Thread netty = new Thread(() -> {
                if (success) held.setSuccess(); else held.setFailure(new IOException("delayed write failure"));
            }, "review-netty-completion");
            netty.start(); netty.join(); held = null;
        }
        void close() { wire.finish(); encoder.finish(); }
    }
}
