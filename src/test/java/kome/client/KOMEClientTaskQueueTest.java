package kome.client;

import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import kome.common.KOMEAddon;
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

    @Test public void conquestPublishesSevenValidatedMapsAndRevisionInExactlyOneClientTask() throws Exception {
        RecordingProxy proxy = proxy(); KOMECommonProxy previous = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        KOMEClientData client = KOMEClientData.INSTANCE;
        List<Map> maps = Arrays.<Map>asList(client.armyCompanies, client.conquestTiles, client.armyMovements,
                client.troopSummaries, client.routeEdges, client.tileWaypointLinksByTileId, client.builds);
        List<Map> saved = new ArrayList<Map>(); for (Map map : maps) saved.add(new HashMap(map));
        int revision = client.conquestRevision;
        try {
            for (Map map : maps) map.clear();
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
            for (int i = 0; i < maps.size(); i++) { maps.get(i).clear(); maps.get(i).putAll(saved.get(i)); }
            client.conquestRevision = revision; KOMEAddon.proxy = previous;
        }
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
        assertTrue(client.contains("ClientDisconnectionFromServerEvent"));
        assertTrue(client.contains("clientTasks.resetSession(false, this::resetClientSessionState)"));
        for (String name : new String[] {"PopulationGui", "PopulationUnitsGui", "ConquestCaptureGui", "ConquestData", "CompanyListGui"}) {
            String packet = source("common/network/KOMEPacket" + name + ".java");
            assertTrue(packet.contains("copyForPublication(")); assertTrue(packet.contains("enqueueClientTask("));
            assertFalse(packet.contains("displayGuiScreen("));
        }
    }

    private static KOMEClientTaskQueue connected() { KOMEClientTaskQueue queue = new KOMEClientTaskQueue(); queue.resetSession(true, () -> {}); queue.drain(); return queue; }
    private static void row(KOMEPacketConquestData packet, String key, NBTTagCompound value) {
        NBTTagList list = new NBTTagList(); list.appendTag(value); packet.data.setTag(key, list);
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
        proxy.queue = connected(); proxy.calls = new ArrayList<String>(); proxy.threads = new ArrayList<Thread>(); return proxy;
    }
    private static class RecordingProxy extends KOMECommonProxy {
        KOMEClientTaskQueue queue; List<String> calls; List<Thread> threads;
        @Override public void enqueueClientTask(Runnable task) { queue.enqueue(task); }
        private void display(String call) { calls.add(call); threads.add(Thread.currentThread()); }
        @Override public void displayPopulationGui(KOMEPacketPopulationGui packet) { display("population:" + packet.playerName); }
        @Override public void displayPopulationUnitsGui(KOMEPacketPopulationUnitsGui packet) { display("units:" + packet.playerName); }
        @Override public void displayConquestCaptureGui(KOMEPacketConquestCaptureGui packet) { display("capture:" + packet.tileId); }
        @Override public void displayCompanyListGui(String tile, String name, List companies, boolean create) { display("companies:" + tile); }
    }
    private static String repeat(char ch, int length) { char[] chars = new char[length]; Arrays.fill(chars, ch); return new String(chars); }
    private static String source(String path) throws Exception { return new String(Files.readAllBytes(Paths.get("src/main/java/kome/" + path)), StandardCharsets.UTF_8); }
}
