package kome.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import kome.common.KOMEAddon;
import kome.common.KOMECommonProxy;
import kome.common.network.KOMEPacketTacticalEditSnapshot;
import kome.common.siege.KOMESiegeReadinessFixtures;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.edit.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual packet/proxy/queue publication with an inert Minecraft client; no GUI/window required. */
public class KOMETacticalEditClientTest {
    private Field clientField;
    private Object previousClient;
    private KOMECommonProxy previousProxy;
    private KOMEClientProxy proxy;
    private KOMEClientTaskQueue queue;
    private WorldClient world;
    private final UUID playerId = UUID.randomUUID(), token = UUID.randomUUID();
    @Before public void setup() throws Exception {
        for (Field field : Minecraft.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Minecraft.class) clientField = field;
        }
        assertNotNull(clientField); clientField.setAccessible(true); previousClient = clientField.get(null);
        Minecraft client = KOMEAccessFixture.allocate(Minecraft.class);
        EntityClientPlayerMP player = KOMEAccessFixture.allocate(EntityClientPlayerMP.class);
        Field uuid = Entity.class.getDeclaredField("entityUniqueID"); uuid.setAccessible(true); uuid.set(player, playerId);
        player.dimension = -1; client.thePlayer = player;
        world = KOMEAccessFixture.allocate(WorldClient.class);
        Field remote = World.class.getDeclaredField("isRemote"); remote.setAccessible(true); remote.set(world, true);
        client.theWorld = world; player.worldObj = world; clientField.set(null, client);
        proxy = KOMEAccessFixture.allocate(KOMEClientProxy.class); queue = new KOMEClientTaskQueue();
        Field tasks = KOMEClientProxy.class.getDeclaredField("clientTasks"); tasks.setAccessible(true); tasks.set(proxy, queue);
        Field snapshots = KOMEClientProxy.class.getDeclaredField("conquestSnapshots"); snapshots.setAccessible(true); snapshots.set(proxy, new KOMEConquestSnapshotPublisher(queue));
        previousProxy = KOMEAddon.proxy; KOMEAddon.proxy = proxy;
        queue.resetSession(true, () -> {}); queue.drain();
    }
    @After public void cleanup() throws Exception {
        KOMEAddon.proxy = previousProxy; clientField.set(null, previousClient);
    }
    private KOMEPacketTacticalEditSnapshot packet(long generation, long publication, long sequence) {
        KOMETacticalEditScope scope = new KOMETacticalEditScope(KOMETacticalEditScope.Type.TILE_FORCE_DEPLOYMENT_AREA, "T100", null, "FIELD", -1);
        KOMETacticalEditDraft draft = new KOMETacticalEditDraft(new KOMEForceDeploymentArea("FIELD", "T100", -1, "Draft " + sequence,
            KOMESiegeReadinessFixtures.prism(0, 0, 10, 10), 7));
        return new KOMEPacketTacticalEditSnapshot(KOMETacticalEditSessionManager.Status.UPDATED,
            new KOMETacticalEditSnapshot(playerId, token, scope, generation, publication, 10, 7, sequence, 10, false, draft, null));
    }
    private void send(KOMEPacketTacticalEditSnapshot packet) { new KOMEPacketTacticalEditSnapshot.Handler().onMessage(packet, null); }
    @Test public void queuedSnapshotsPublishOnClientThreadAndOutOfOrderArrivalCannotReplaceNewerDraft() {
        send(packet(1, 2, 1)); send(packet(1, 1, 0)); assertNull(proxy.getTacticalEditorSnapshot());
        assertEquals(2, queue.drain()); assertEquals("Draft 1", proxy.getTacticalEditorSnapshot().getDraft().getArea().getLabel());
    }
    @Test public void disconnectDropsQueuedOldSnapshotsAndConnectionResetAllowsNewStream() {
        send(packet(1, 1, 0)); queue.drain(); send(packet(1, 2, 1));
        proxy.onClientDisconnect(null); queue.drain(); assertNull(proxy.getTacticalEditorSnapshot());
        send(packet(1, 3, 2)); assertEquals(0, queue.pendingTasks());
        proxy.onClientConnect(null); queue.drain(); send(packet(1, 1, 0)); queue.drain(); assertNotNull(proxy.getTacticalEditorSnapshot());
    }
    @Test public void dimensionWorldUnloadClearsScopeAndRejectsPendingOldSessionReplies() {
        send(packet(1, 1, 0)); queue.drain(); send(packet(1, 2, 1));
        proxy.onClientWorldUnload(new WorldEvent.Unload(world)); queue.drain(); assertNull(proxy.getTacticalEditorSnapshot());
        send(packet(2, 3, 0)); queue.drain(); assertNotNull(proxy.getTacticalEditorSnapshot());
    }
    @Test public void firstUnseenOpenQueuedBeforeWorldUnloadCannotCreateMirrorAfterward() {
        KOMETacticalEditSnapshot first = packet(1, 1, 0).getSnapshot();
        send(new KOMEPacketTacticalEditSnapshot(KOMETacticalEditSessionManager.Status.OPENED, first));
        assertNull(proxy.getTacticalEditorSnapshot());
        proxy.onClientWorldUnload(new WorldEvent.Unload(world));
        queue.drain(); assertNull(proxy.getTacticalEditorSnapshot()); assertNull(proxy.getTacticalEditorStatus());
        send(packet(2, 2, 0)); queue.drain(); assertEquals(2, proxy.getTacticalEditorSnapshot().getGeneration());
    }
    @Test public void previouslyUnseenNewGenerationQueuedBeforeUnloadIsDiscarded() {
        send(packet(1, 1, 0)); queue.drain(); send(packet(2, 2, 0));
        proxy.onClientWorldUnload(new WorldEvent.Unload(world));
        queue.drain(); assertNull(proxy.getTacticalEditorSnapshot()); assertNull(proxy.getTacticalEditorStatus());
        send(packet(3, 3, 0)); queue.drain(); assertEquals(3, proxy.getTacticalEditorSnapshot().getGeneration());
    }
    @Test public void oldLifecycleTaskCannotReplaceCurrentSnapshotEvenWithHigherServerCounters() throws Exception {
        // Hold a network publication until after a new lifecycle's valid publication.
        // Server counters alone would accept this previously unseen generation.
        send(packet(3, 100, 0));
        Field tasks = KOMEClientTaskQueue.class.getDeclaredField("tasks"); tasks.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Queue<Runnable> pending = (java.util.Queue<Runnable>) tasks.get(queue);
        Runnable oldPublication = pending.remove();
        proxy.onClientWorldUnload(new WorldEvent.Unload(world));
        send(packet(2, 2, 0)); queue.drain();
        oldPublication.run(); assertEquals(2, proxy.getTacticalEditorSnapshot().getGeneration());
        assertEquals(2, proxy.getTacticalEditorSnapshot().getPublicationSequence());
    }
    @Test public void connectionLifecycleEpochRejectsHeldPublicationsAcrossReset() throws Exception {
        send(packet(3, 100, 0));
        Field tasks = KOMEClientTaskQueue.class.getDeclaredField("tasks"); tasks.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.Queue<Runnable> pending = (java.util.Queue<Runnable>) tasks.get(queue);
        Runnable oldPublication = pending.remove();
        proxy.onClientDisconnect(null); queue.drain(); proxy.onClientConnect(null); queue.drain();
        send(packet(1, 1, 0)); queue.drain(); oldPublication.run();
        assertEquals(1, proxy.getTacticalEditorSnapshot().getGeneration());
    }
    @Test public void excessClientQueueWorkIsBoundedAndCannotPublishEarly() {
        for (int i = 1; i <= 200; i++) send(packet(1, i, i));
        assertEquals(KOMEClientTaskQueue.MAX_PENDING_TASKS, queue.pendingTasks()); assertNull(proxy.getTacticalEditorSnapshot());
        queue.drain(); assertEquals(KOMEClientTaskQueue.MAX_PENDING_TASKS, proxy.getTacticalEditorSnapshot().getPublicationSequence());
    }
}
