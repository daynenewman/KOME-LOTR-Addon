package kome.client;

import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import kome.common.KOMEAddon;
import kome.common.KOMEAccessFixture;
import kome.common.KOMECommonProxy;
import kome.common.data.*;
import kome.common.network.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEClientTaskQueueTest {
    @Test public void startTickDrainsFifoSnapshotAndFailureDoesNotBlockLaterTasks() {
        KOMEClientTaskQueue queue = connected(); List<Integer> order = new ArrayList<Integer>();
        queue.enqueue(() -> { order.add(1); queue.enqueue(() -> order.add(4)); });
        queue.enqueue(() -> { throw new IllegalStateException("Injected client publication failure"); });
        queue.enqueue(() -> order.add(3));
        queue.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END)); assertTrue(order.isEmpty());
        queue.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
        assertEquals(Arrays.asList(1, 3), order); assertEquals(1, queue.pendingTasks());
        queue.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START)); assertEquals(Arrays.asList(1, 3, 4), order);
    }

    @Test public void queueRejectsNewestAtBoundAndDisconnectDiscardsOldSession() {
        KOMEClientTaskQueue queue = connected(); List<Integer> calls = new ArrayList<Integer>();
        for (int i = 0; i < KOMEClientTaskQueue.MAX_PENDING_TASKS; i++) queue.enqueue(() -> calls.add(1));
        try { queue.enqueue(() -> calls.add(2)); fail("overflow"); } catch (RejectedExecutionException expected) { }
        assertEquals(KOMEClientTaskQueue.MAX_PENDING_TASKS, queue.pendingTasks());
        queue.resetSession(false, () -> calls.add(0));
        assertTrue(calls.isEmpty()); assertEquals(1, queue.pendingTasks());
        try { queue.enqueue(() -> calls.add(3)); fail("disconnected"); } catch (RejectedExecutionException expected) { }
        queue.drain(); assertEquals(Arrays.asList(0), calls);
        queue.resetSession(true, () -> calls.add(4)); queue.enqueue(() -> calls.add(5)); queue.drain();
        assertEquals(Arrays.asList(0, 4, 5), calls);
    }

    @Test public void latestPublicationReplacesPendingValueAndDisconnectedSessionIgnoresIt() {
        KOMEClientTaskQueue queue = connected(); List<Integer> calls = new ArrayList<Integer>();
        assertTrue(queue.enqueueLatest("snapshot", () -> calls.add(1)));
        assertTrue(queue.enqueueLatest("snapshot", () -> calls.add(2)));
        assertEquals(1, queue.pendingTasks());
        queue.drain(); assertEquals(Arrays.asList(2), calls);

        assertTrue(queue.enqueueLatest("snapshot", () -> calls.add(3)));
        queue.resetSession(false, () -> calls.add(4));
        assertFalse(queue.enqueueLatest("snapshot", () -> calls.add(5)));
        queue.drain(); assertEquals(Arrays.asList(2, 4), calls);
    }

    @Test public void fourGuiHandlersDeepCopyAndOnlyDisplayOnClientDrain() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        try {
            KOMEPacketPopulationGui population = new KOMEPacketPopulationGui(); population.playerName = "Before";
            KOMEPacketPopulationUnitsGui units = new KOMEPacketPopulationUnitsGui(); units.playerName = "Before";
            KOMEPacketConquestCaptureGui capture = new KOMEPacketConquestCaptureGui(); capture.tileId = "T1";
            KOMEPacketCompanyListGui companies = new KOMEPacketCompanyListGui(); companies.tileId = "T1";
            onNetwork(() -> {
                new KOMEPacketPopulationGui.Handler().onMessage(population, null);
                new KOMEPacketPopulationUnitsGui.Handler().onMessage(units, null);
                new KOMEPacketConquestCaptureGui.Handler().onMessage(capture, null);
                new KOMEPacketCompanyListGui.Handler().onMessage(companies, null);
            });
            assertTrue(proxy.calls.isEmpty()); assertEquals(4, proxy.queue.pendingTasks());
            population.playerName = "After"; units.playerName = "After"; capture.tileId = "T2"; companies.tileId = "T2";
            proxy.queue.drain();
            assertEquals(Arrays.asList("population:Before", "units:Before", "capture:T1", "companies:T1"), proxy.calls);
            for (Thread thread : proxy.threads) assertSame(Thread.currentThread(), thread);
        } finally { KOMEAddon.proxy = previous; }
    }

    @Test public void movementPreviewHandlersPublishDetachedStateOnClientQueue() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        try {
            KOMEPacketCompanyMoveConfirmGui confirm = new KOMEPacketCompanyMoveConfirmGui();
            confirm.companyId = "C1"; confirm.destinationTile = "T376"; confirm.previewToken = "before";
            confirm.routeTiles.addAll(Arrays.asList("T388", "T379", "T376"));
            KOMEPacketCompanyMovePreviewResult denied = new KOMEPacketCompanyMovePreviewResult();
            denied.companyId = "C2"; denied.failureCode = "MOUNTAIN_EDGE_BLOCKED";
            onNetwork(() -> {
                new KOMEPacketCompanyMoveConfirmGui.Handler().onMessage(confirm, null);
                new KOMEPacketCompanyMovePreviewResult.Handler().onMessage(denied, null);
            });
            assertTrue(proxy.calls.isEmpty()); assertEquals(2, proxy.queue.pendingTasks());
            confirm.destinationTile = "T999"; confirm.previewToken = "after"; confirm.routeTiles.clear();
            denied.failureCode = "after";
            proxy.queue.drain();
            assertEquals(Arrays.asList("move:C1:T376:before:3", "move-denied:C2:MOUNTAIN_EDGE_BLOCKED"), proxy.calls);
            for (Thread thread : proxy.threads) assertSame(Thread.currentThread(), thread);
        } finally { KOMEAddon.proxy = previous; }
    }

    @Test public void clientProxyOpensMapBeforePublishingPreviewState() throws Exception {
        String source = source("client/KOMEClientProxy.java");
        int method = source.indexOf("displayCompanyMoveConfirmGui");
        int open = source.indexOf("openPreservedMap()", method);
        int publish = source.indexOf("beginRoutePreview(message)", method);
        assertTrue(method >= 0 && open > method && publish > open);
        int denied = source.indexOf("displayCompanyMovePreviewResult", publish);
        int deniedOpen = source.indexOf("openPreservedMap()", denied);
        int deniedPublish = source.indexOf("showCompanyMovePreviewResult(message)", denied);
        assertTrue(deniedOpen > denied && deniedPublish > deniedOpen);
    }

    @Test public void conquestPublishesSevenValidatedMapsAndRevisionInExactlyOneClientTask() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        KOMEClientData client = KOMEClientData.INSTANCE;
        List<Map> maps = Arrays.<Map>asList(client.armyCompanies, client.conquestTiles, client.armyMovements,
                client.troopSummaries, client.routeEdges, client.tileWaypointLinksByTileId, client.builds);
        SavedClientState saved = new SavedClientState();
        int revision = client.conquestRevision;
        try {
            saved.clear();
            KOMEPacketConquestData packet = new KOMEPacketConquestData(); packet.reset = true; packet.complete = true;
            KOMEArmyCompany company = new KOMEArmyCompany(); company.id = "C1";
            row(packet, "ArmyCompanies", company.writeToNBT());
            KOMEConquestTile tile = new KOMEConquestTile("T1"); tile.claim("gondor", 0L); row(packet, "ConquestTiles", tile.projectToNBT());
            KOMEArmyMovementOrder movement = new KOMEArmyMovementOrder(); movement.id = "M1"; movement.status = KOMEArmyMovementOrder.MOVING;
            row(packet, "ArmyMovements", movement.writeToNBT());
            KOMETileTroopSummary summary = new KOMETileTroopSummary(); summary.tileId = "T1";
            summary.population = new KOMEPopulationProjection("gondor", 2450L, BigInteger.ZERO, BigInteger.ZERO, false, 0L);
            row(packet, "TroopSummaries", summary.writeToNBT());
            row(packet, "RouteEdges", new KOMEConquestRouteEdge("T1", "T2", "open").writeToNBT());
            KOMETileWaypointLink link = new KOMETileWaypointLink(); link.tileId = "T1"; link.lotrWaypointKey = "waypoint";
            row(packet, "TileWaypointLinks", link.writeToNBT());
            NBTTagCompound marker = new NBTTagCompound(); marker.setString("Id", "B1"); marker.setString("BuildType", "NORMAL");
            marker.setBoolean("Active", true); marker.setBoolean("MarkerVisible", true); row(packet, "BuildMarkers", marker);
            onNetwork(() -> new KOMEPacketConquestData.Handler().onMessage(packet, null));
            assertEquals(1, proxy.queue.pendingTasks()); for (Map map : maps) assertTrue(map.isEmpty());
            assertEquals(revision, client.conquestRevision);
            packet.data = new NBTTagCompound(); packet.reset = false; packet.complete = false;
            proxy.queue.drain();
            for (Map map : maps) assertEquals(1, map.size()); assertEquals(revision + 1, client.conquestRevision);
            assertEquals(2450L, client.troopSummaries.get("T1").population.availablePopulationCenti);
        } finally {
            saved.restore(); KOMEAddon.proxy = previous;
        }
    }

    @Test public void resetIntermediateCompletePublishesAtomicallyAndNewResetReplacesIncompleteGeneration() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        SavedClientState saved = new SavedClientState(); KOMEClientData client = KOMEClientData.INSTANCE;
        int revision = client.conquestRevision;
        try {
            saved.clear();
            KOMEConquestTile sentinel = new KOMEConquestTile("T9000"); sentinel.claim("rohan", 0L);
            client.conquestTiles.put(sentinel.id, sentinel);

            onNetwork(() -> new KOMEPacketConquestData.Handler().onMessage(tilePacket("T1", false, true), null));
            assertEquals(0, proxy.queue.pendingTasks());
            onNetwork(() -> {
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T10", true, false), null);
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T11", false, false), null);
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T20", true, false), null);
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T21", false, true), null);
            });
            assertEquals(1, proxy.queue.pendingTasks());
            assertSame(sentinel, client.conquestTiles.get(sentinel.id));
            assertEquals(revision, client.conquestRevision);
            proxy.queue.drain();
            assertEquals(new HashSet<String>(Arrays.asList("T20", "T21")), client.conquestTiles.keySet());
            assertFalse(client.conquestTiles.containsKey("T10"));
            assertFalse(client.conquestTiles.containsKey("T11"));
            assertEquals(revision + 1, client.conquestRevision);
        } finally { saved.restore(); KOMEAddon.proxy = previous; }
    }

    @Test public void rapidCompletedRefreshesCoalescePastOldQueueLimitAndPublishLatestOnly() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        SavedClientState saved = new SavedClientState(); KOMEClientData client = KOMEClientData.INSTANCE;
        int revision = client.conquestRevision;
        try {
            saved.clear();
            onNetwork(() -> {
                for (int i = 0; i < KOMEClientTaskQueue.MAX_PENDING_TASKS + 72; i++)
                    new KOMEPacketConquestData.Handler().onMessage(tilePacket("T" + (1000 + i), true, true), null);
            });
            assertEquals(1, proxy.queue.pendingTasks());
            assertEquals(revision, client.conquestRevision);
            proxy.queue.drain();
            assertEquals(Collections.singleton("T1199"), client.conquestTiles.keySet());
            assertEquals(revision + 1, client.conquestRevision);
        } finally { saved.restore(); KOMEAddon.proxy = previous; }
    }

    @Test public void realChunkerCanExceedOldTaskBoundButPublishesOneCompleteSnapshot() throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        RecordingProxy proxy = proxy(); KOMECommonProxy previousProxy = KOMEAddon.proxy;
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previousNetwork = KOMEPacketHandler.network;
        SavedClientState saved = new SavedClientState(); KOMEClientData client = KOMEClientData.INSTANCE;
        int revision = client.conquestRevision;
        try {
            saved.clear(); KOMEAddon.proxy = proxy; KOMEPacketHandler.network = fixture.network;
            KOMEWorldData data = new KOMEWorldData("large-conquest-snapshot");
            for (int i = 0; i < 3100; i++) {
                KOMEConquestTile tile = new KOMEConquestTile("T" + (10000 + i));
                tile.claim("gondor", i); data.conquestTiles.put(tile.id, tile);
            }
            KOMEPacketConquestData.sendChunked(data, fixture.player);
            assertTrue(fixture.network.messages.size() > KOMEClientTaskQueue.MAX_PENDING_TASKS);
            int resets = 0, completes = 0;
            for (IMessage value : fixture.network.messages) {
                KOMEPacketConquestData packet = (KOMEPacketConquestData) value;
                if (packet.reset) resets++;
                if (packet.complete) completes++;
            }
            assertEquals(1, resets); assertEquals(1, completes);

            onNetwork(() -> {
                for (IMessage value : fixture.network.messages)
                    new KOMEPacketConquestData.Handler().onMessage((KOMEPacketConquestData) value, null);
            });
            assertEquals(1, proxy.queue.pendingTasks());
            assertTrue(client.conquestTiles.isEmpty()); assertEquals(revision, client.conquestRevision);
            proxy.queue.drain();
            assertEquals(3100, client.conquestTiles.size());
            assertEquals(3100, client.troopSummaries.size());
            assertEquals(revision + 1, client.conquestRevision);
        } finally {
            saved.restore(); KOMEAddon.proxy = previousProxy; KOMEPacketHandler.network = previousNetwork;
        }
    }

    @Test public void disconnectIgnoresInFlightPacketsAndReconnectRequiresFreshResetSnapshot() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        SavedClientState saved = new SavedClientState(); KOMEClientData client = KOMEClientData.INSTANCE;
        int revision = client.conquestRevision;
        try {
            saved.clear();
            KOMEConquestTile sentinel = new KOMEConquestTile("T9000"); sentinel.claim("rohan", 0L);
            client.conquestTiles.put(sentinel.id, sentinel);
            onNetwork(() -> new KOMEPacketConquestData.Handler().onMessage(tilePacket("T1", true, false), null));

            proxy.resetSession(false, () -> {});
            onNetwork(() -> {
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T2", false, true), null);
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T3", true, true), null);
            });
            assertEquals(1, proxy.queue.pendingTasks());
            proxy.queue.drain(); assertSame(sentinel, client.conquestTiles.get(sentinel.id));
            assertEquals(revision, client.conquestRevision);

            proxy.resetSession(true, () -> {});
            onNetwork(() -> {
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T4", false, true), null);
                new KOMEPacketConquestData.Handler().onMessage(tilePacket("T5", true, true), null);
            });
            assertEquals(2, proxy.queue.pendingTasks());
            proxy.queue.drain();
            assertEquals(Collections.singleton("T5"), client.conquestTiles.keySet());
            assertEquals(revision + 1, client.conquestRevision);
        } finally { saved.restore(); KOMEAddon.proxy = previous; }
    }

    @Test public void invalidGuiAndLaterConquestRowsEnqueueNothing() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        try {
            KOMEPacketPopulationGui invalid = new KOMEPacketPopulationGui(); invalid.playerName = repeat('x', 4097);
            try { new KOMEPacketPopulationGui.Handler().onMessage(invalid, null); fail("invalid text"); } catch (IllegalArgumentException expected) { }
            KOMEPacketConquestData conquest = new KOMEPacketConquestData();
            NBTTagCompound invalidMarker = new NBTTagCompound(); invalidMarker.setString("BuildType", "UNKNOWN");
            row(conquest, "BuildMarkers", invalidMarker);
            try { new KOMEPacketConquestData.Handler().onMessage(conquest, null); fail("invalid marker"); } catch (IllegalArgumentException expected) { }
            assertEquals(0, proxy.queue.pendingTasks()); assertTrue(proxy.calls.isEmpty());
        } finally { KOMEAddon.proxy = previous; }
    }

    @Test public void commonInitializationDoesNotReferenceClientQueueAndClientProxyOwnsRegistration() throws Exception {
        String common = source("common/KOMECommonProxy.java");
        assertFalse(common.contains("kome.client")); assertFalse(common.contains("KOMEClientTaskQueue"));
        assertFalse(source("common/KOMEAddon.java").contains("new KOMEClientTaskQueue"));
        String client = source("client/KOMEClientProxy.java");
        assertTrue(client.contains("bus().register(clientTasks)"));
        assertTrue(client.contains("Minecraft.getMinecraft().func_152345_ab()"));
        assertTrue(client.contains("ClientDisconnectionFromServerEvent"));
        assertTrue(client.contains("clientTasks.resetSession(false, this::resetClientSessionState)"));
        assertTrue(client.contains("conquestSnapshots.resetSession()"));
        String clientData = source("common/data/KOMEClientData.java");
        String reset = clientData.substring(clientData.indexOf("public void resetClientState()"));
        assertFalse(reset.contains("conquestRevision++"));
        for (String name : new String[] {"PopulationGui", "PopulationUnitsGui", "ConquestCaptureGui", "CompanyListGui"}) {
            String packet = source("common/network/KOMEPacket" + name + ".java");
            assertTrue(packet.contains("copyForPublication(")); assertTrue(packet.contains("enqueueClientTask("));
            assertFalse(packet.contains("displayGuiScreen("));
        }
        String conquest = source("common/network/KOMEPacketConquestData.java");
        assertTrue(conquest.contains("copyForPublication("));
        assertTrue(conquest.contains("acceptConquestSnapshotChunk("));
        assertFalse(conquest.contains("enqueueClientTask("));
    }

    @Test public void clientThreadConquestBurstDoesNotOverflowOrLoseResetAndCompletion() throws Exception {
        RecordingProxy proxy = proxy(); Thread clientThread = Thread.currentThread();
        proxy.queue = new KOMEClientTaskQueue(() -> Thread.currentThread() == clientThread);
        proxy.snapshots = new KOMEConquestSnapshotPublisher(proxy.queue);
        KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        KOMEClientData client = KOMEClientData.INSTANCE;
        List<Map> maps = Arrays.<Map>asList(client.armyCompanies, client.conquestTiles, client.capitalTilesByFaction,
                client.armyMovements, client.troopSummaries, client.routeEdges, client.tileWaypointLinksByTileId, client.builds);
        List<Map> saved = new ArrayList<Map>(); for (Map map : maps) saved.add(new HashMap(map));
        int revision = client.conquestRevision;
        try {
            proxy.queue.resetSession(true, () -> client.conquestTiles.clear()); proxy.queue.drain();
            // Same production handler and reset/continuation/complete sequence as a hire-triggered sync.
            // Four queue capacities between ticks would disconnect the previous implementation.
            int count = KOMEClientTaskQueue.MAX_PENDING_TASKS * 4;
            for (int i = 0; i < count; i++) {
                KOMEPacketConquestData packet = new KOMEPacketConquestData();
                packet.reset = i == 0; packet.complete = i == count - 1;
                KOMEConquestTile tile = new KOMEConquestTile("T" + (i + 1)); tile.claim("angmar", 0L);
                row(packet, "ConquestTiles", tile.projectToNBT());
                io.netty.buffer.ByteBuf wire=io.netty.buffer.Unpooled.buffer();
                KOMEPacketConquestData received=new KOMEPacketConquestData();
                try {packet.toBytes(wire);received.fromBytes(wire);}finally{wire.release();}
                new KOMEPacketConquestData.Handler().onMessage(received, null);
                assertEquals(0, proxy.queue.pendingTasks());
                assertEquals(revision + (packet.complete ? 1 : 0), client.conquestRevision);
            }
            assertEquals(count, client.conquestTiles.size());
            assertTrue(client.conquestTiles.containsKey("T1"));
            assertTrue(client.conquestTiles.containsKey("T" + count));
        } finally {
            for (int i = 0; i < maps.size(); i++) { maps.get(i).clear(); maps.get(i).putAll(saved.get(i)); }
            client.conquestRevision = revision; KOMEAddon.proxy = previous;
        }
    }

    @Test public void clientDispatchPreservesOlderWorkAndBoundsOffThreadIngress() throws Exception {
        Thread clientThread = Thread.currentThread();
        KOMEClientTaskQueue queue = new KOMEClientTaskQueue(() -> Thread.currentThread() == clientThread);
        List<Integer> calls = new ArrayList<Integer>();
        queue.resetSession(true, () -> calls.add(0)); queue.drain();
        onNetwork(() -> { for (int i = 0; i < KOMEClientTaskQueue.MAX_PENDING_TASKS; i++) queue.enqueue(() -> calls.add(1)); });
        assertEquals(1, calls.size());
        queue.enqueue(() -> calls.add(2));
        assertEquals(KOMEClientTaskQueue.MAX_PENDING_TASKS + 2, calls.size());
        assertEquals(Integer.valueOf(0), calls.get(0)); assertEquals(Integer.valueOf(2), calls.get(calls.size() - 1));
        assertEquals(0, queue.pendingTasks());
        queue.resetSession(false, () -> calls.add(3)); queue.drain();
        try { queue.enqueue(() -> calls.add(99)); fail("Disconnected work must not run"); } catch (RejectedExecutionException expected) { }
        queue.resetSession(true, () -> calls.add(4)); queue.enqueue(() -> calls.add(5));
        assertEquals(Arrays.asList(3, 4, 5), calls.subList(calls.size() - 3, calls.size()));
    }

    @Test public void immediateDispatchDoesNotRecursivelyDrainTasksCreatedByTasks() {
        KOMEClientTaskQueue queue = new KOMEClientTaskQueue(() -> true);
        List<Integer> calls = new ArrayList<Integer>(); queue.resetSession(true, () -> {}); queue.drain();
        queue.enqueue(() -> { calls.add(1); queue.enqueue(() -> calls.add(2)); assertEquals(0, queue.drain()); });
        assertEquals(Collections.singletonList(1), calls); assertEquals(1, queue.pendingTasks());
        queue.drain(); assertEquals(Arrays.asList(1, 2), calls);
    }

    @Test public void decodedConquestHandoffAvoidsReencodingAndIsolatesPublicMutation() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous=KOMEAddon.proxy; KOMEAddon.proxy=proxy;
        KOMEClientData client=KOMEClientData.INSTANCE; Map saved=new HashMap(client.conquestTiles);
        int revision=client.conquestRevision;
        try {
            for(String owner : Arrays.asList("angmar","gondor")) {
                KOMEPacketConquestData source=new KOMEPacketConquestData(); source.reset=true;
                KOMEConquestTile tile=new KOMEConquestTile("T001");tile.claim(owner,0L);
                row(source,"ConquestTiles",tile.projectToNBT());
                // Initialize a full generation as required by dev; decode through the actual wire reader.
                io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();
                KOMEPacketConquestData decoded=new KOMEPacketConquestData(){
                    @Override public void toBytes(io.netty.buffer.ByteBuf b){throw new AssertionError("decoded packet reencoded");}
                };
                try {source.toBytes(bytes);decoded.fromBytes(bytes);}finally{bytes.release();}
                decoded.data.setString("ConquestTiles","mutated after decode");decoded.complete=false;decoded.reset=true;
                onNetwork(() -> new KOMEPacketConquestData.Handler().onMessage(decoded,null));
                assertEquals(1,proxy.queue.pendingTasks());
                proxy.queue.drain();
                assertEquals(owner,((KOMEConquestTile)client.conquestTiles.get("T001")).projectRulingFaction());
            }
            // Both snapshots were materialized off-thread and published in order.
        } finally {
            proxy.queue.drain();
            assertEquals("gondor",((KOMEConquestTile)client.conquestTiles.get("T001")).projectRulingFaction());
            assertEquals(revision+2,client.conquestRevision);
            client.conquestTiles.clear();client.conquestTiles.putAll(saved);client.conquestRevision=revision;KOMEAddon.proxy=previous;
        }
    }

    @Test public void completedOwnershipColorsSurviveFollowingPartialResetUntilCompletion() throws Exception {
        RecordingProxy proxy=proxy();KOMECommonProxy previous=KOMEAddon.proxy;KOMEAddon.proxy=proxy;
        KOMEClientData client=KOMEClientData.INSTANCE;Map saved=new HashMap(client.conquestTiles);int revision=client.conquestRevision;
        Field colorField=KOMEConquestMapOverlay.class.getDeclaredField("tileColorsById");colorField.setAccessible(true);
        Map colors=(Map)colorField.get(null);Map oldColors=new HashMap(colors);
        try {
            colors.clear();colors.put("T001",1);colors.put("T002",2);
            KOMEPacketConquestData full=new KOMEPacketConquestData();full.reset=true;
            NBTTagList rows=new NBTTagList();
            for(String id:Arrays.asList("T001","T002")){KOMEConquestTile tile=new KOMEConquestTile(id);tile.claim("angmar",0);rows.appendTag(tile.projectToNBT());}
            full.data.setTag("ConquestTiles",rows);new KOMEPacketConquestData.Handler().onMessage(full,null);proxy.queue.drain();
            Map<Integer,Integer> completed=KOMEConquestMapOverlay.effectiveClaimColors();assertEquals(2,completed.size());
            KOMEPacketConquestData partial=new KOMEPacketConquestData();partial.reset=true;partial.complete=false;
            KOMEConquestTile one=new KOMEConquestTile("T002");one.claim("gondor",0);row(partial,"ConquestTiles",one.projectToNBT());
            new KOMEPacketConquestData.Handler().onMessage(partial,null);proxy.queue.drain();
            // Renderer may run after completion N but before completion N+1. Do not read the partial batch.
            assertEquals(completed,KOMEConquestMapOverlay.effectiveClaimColors());
            new KOMEPacketConquestData.Handler().onMessage(new KOMEPacketConquestData(),null);proxy.queue.drain();
            Map<Integer,Integer> next=KOMEConquestMapOverlay.effectiveClaimColors();assertEquals(1,next.size());
            assertFalse(next.containsKey(1));assertNotEquals(completed.get(2),next.get(2));
        }finally{client.conquestTiles.clear();client.conquestTiles.putAll(saved);client.completeConquestUpdate();client.conquestRevision=revision;colors.clear();colors.putAll(oldColors);KOMEAddon.proxy=previous;}
    }

    private static KOMEClientTaskQueue connected() { KOMEClientTaskQueue queue = new KOMEClientTaskQueue(); queue.resetSession(true, () -> {}); queue.drain(); return queue; }
    private static KOMEPacketConquestData tilePacket(String tileId, boolean reset, boolean complete) {
        KOMEPacketConquestData packet = new KOMEPacketConquestData();
        packet.reset = reset; packet.complete = complete;
        KOMEConquestTile tile = new KOMEConquestTile(tileId); tile.claim("gondor", 0L);
        row(packet, "ConquestTiles", tile.projectToNBT()); return packet;
    }
    private static void row(KOMEPacketConquestData packet, String key, NBTTagCompound value) {
        NBTTagList list = new NBTTagList(); list.appendTag(value); packet.data.setTag(key, list);
    }

    private static final class SavedClientState {
        private final KOMEClientData client = KOMEClientData.INSTANCE;
        private final List<Map> maps = Arrays.<Map>asList(client.armyCompanies, client.conquestTiles,
            client.capitalTilesByFaction, client.armyMovements, client.troopSummaries,
            client.routeEdges, client.tileWaypointLinksByTileId, client.builds);
        private final List<Map> values = new ArrayList<Map>();
        private final int revision = client.conquestRevision;

        private SavedClientState() {
            for (Map map : maps) values.add(new HashMap(map));
        }

        private void clear() {
            for (Map map : maps) map.clear();
        }

        private void restore() {
            for (int i = 0; i < maps.size(); i++) {
                maps.get(i).clear(); maps.get(i).putAll(values.get(i));
            }
            client.conquestRevision = revision;
        }
    }

    private static void onNetwork(Runnable action) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread network = new Thread(() -> { try { action.run(); } catch (Throwable error) { failure.set(error); } }, "simulated-Netty");
        network.start(); network.join(10000L); assertFalse("Handler must return", network.isAlive());
        if (failure.get() != null) throw new AssertionError(failure.get());
    }
    private static RecordingProxy proxy() throws Exception {
        // Do not initialize the three imported mods merely to observe GUI dispatch.
        Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field singleton = unsafe.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        RecordingProxy proxy = (RecordingProxy) unsafe.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), RecordingProxy.class);
        proxy.queue = connected(); proxy.snapshots = new KOMEConquestSnapshotPublisher(proxy.queue);
        proxy.calls = new ArrayList<String>(); proxy.threads = new ArrayList<Thread>(); return proxy;
    }
    private static class RecordingProxy extends KOMECommonProxy {
        KOMEClientTaskQueue queue; KOMEConquestSnapshotPublisher snapshots;
        List<String> calls; List<Thread> threads;
        @Override public void enqueueClientTask(Runnable task) { queue.enqueue(task); }
        @Override public void acceptConquestSnapshotChunk(KOMEPacketConquestData.PublicationChunk chunk) {
            snapshots.accept(chunk);
        }
        void resetSession(boolean connected, Runnable reset) {
            snapshots.resetSession(); queue.resetSession(connected, reset);
        }
        private void display(String call) { calls.add(call); threads.add(Thread.currentThread()); }
        @Override public void displayPopulationGui(KOMEPacketPopulationGui packet) { display("population:" + packet.playerName); }
        @Override public void displayPopulationUnitsGui(KOMEPacketPopulationUnitsGui packet) { display("units:" + packet.playerName); }
        @Override public void displayConquestCaptureGui(KOMEPacketConquestCaptureGui packet) { display("capture:" + packet.tileId); }
        @Override public void displayCompanyListGui(String tile, String name, List companies, boolean create) { display("companies:" + tile); }
        @Override public void displayCompanyMoveConfirmGui(KOMEPacketCompanyMoveConfirmGui packet) {
            display("move:" + packet.companyId + ":" + packet.destinationTile + ":" + packet.previewToken
                + ":" + packet.routeTiles.size());
        }
        @Override public void displayCompanyMovePreviewResult(KOMEPacketCompanyMovePreviewResult packet) {
            display("move-denied:" + packet.companyId + ":" + packet.failureCode);
        }
    }
    private static String repeat(char ch, int length) { char[] chars = new char[length]; Arrays.fill(chars, ch); return new String(chars); }
    private static String source(String path) throws Exception { return new String(Files.readAllBytes(Paths.get("src/main/java/kome/" + path)), StandardCharsets.UTF_8); }
}
