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
    private void catalogue() {
        new kome.common.network.KOMEPacketTacticalAreaCatalog.Handler().onMessage(new kome.common.network.KOMEPacketTacticalAreaCatalog(
            new KOMETacticalAreaCatalog("T100", -1, 10, 0, 0, java.util.Collections.emptyList())), null);
    }
    @Test public void browserUsesSameLifecycleBarrierAndWorldUnloadClearsTileDraftOverlay() {
        catalogue(); proxy.onClientWorldUnload(new WorldEvent.Unload(world)); queue.drain();
        assertNull(proxy.getTacticalAreaEditor());
        catalogue(); queue.drain(); assertNotNull(proxy.getTacticalAreaEditor().getCatalog());
        proxy.getTacticalAreaEditor().accept(packet(1, 1, 0).getSnapshot(), KOMETacticalEditSessionManager.Status.OPENED);
        proxy.getTacticalAreaEditor().select(kome.client.tactical.KOMETacticalAreaEditor.Selection.VERTICES);
        assertNotNull(proxy.getTacticalAreaEditor().overlay(playerId, -1));
        proxy.onClientWorldUnload(new WorldEvent.Unload(world));
        assertNull(proxy.getTacticalAreaEditor().overlay(playerId, -1)); assertNull(proxy.getTacticalAreaEditor().getCatalog());
    }
    @Test public void mouseAndInteractSelectionAreConsumedWhileGameplayOutsideSelectionIsUntouched() throws Exception {
        catalogue(); queue.drain();
        Field controller = KOMEClientProxy.class.getDeclaredField("tacticalAreaEditor"); controller.setAccessible(true);
        kome.client.tactical.KOMETacticalAreaEditor local = new kome.client.tactical.KOMETacticalAreaEditor(request -> { });
        local.acceptCatalog(proxy.getTacticalAreaEditor().getCatalog()); controller.set(proxy, local);
        proxy.getTacticalAreaEditor().accept(packet(1, 1, 0).getSnapshot(), KOMETacticalEditSessionManager.Status.OPENED);
        kome.client.tactical.KOMETacticalAreaInteractionHandler input = new kome.client.tactical.KOMETacticalAreaInteractionHandler(proxy);
        net.minecraftforge.client.event.MouseEvent ordinary = mouseEvent(); input.mouse(ordinary);
        assertFalse(ordinary.isCanceled());
        proxy.getTacticalAreaEditor().select(kome.client.tactical.KOMETacticalAreaEditor.Selection.VERTICES);
        Minecraft.getMinecraft().objectMouseOver = new net.minecraft.util.MovingObjectPosition(5, 64, 7, 1, net.minecraft.util.Vec3.createVectorHelper(5, 64, 7));
        net.minecraftforge.client.event.MouseEvent right = mouseEvent();
        try {
            Field button = net.minecraftforge.client.event.MouseEvent.class.getDeclaredField("button"); button.setAccessible(true); button.setInt(right, 1);
            Field down = net.minecraftforge.client.event.MouseEvent.class.getDeclaredField("buttonstate"); down.setAccessible(true); down.setBoolean(right, true);
            // Ignore the advisory refresh packet in this inert client; geometry changes remain local.
            input.mouse(right);
            assertTrue(right.isCanceled());
            assertEquals(new kome.common.siege.geometry.KOMEXZPoint(5, 7), local.getConfirmedVertices().get(local.getConfirmedVertexCount()-1));
            int confirmed = local.getConfirmedVertexCount();
            // An opposite ray-hit face still chooses the SAME grid corner; duplicates are diagnosed.
            Minecraft.getMinecraft().objectMouseOver = new net.minecraft.util.MovingObjectPosition(5, 64, 7, 5,
                net.minecraft.util.Vec3.createVectorHelper(5.99, 64.99, 7.99));
            input.mouse(mousePress(1));
            assertEquals(confirmed,local.getConfirmedVertexCount());assertTrue(local.getMessage().contains("already a polygon vertex"));
            net.minecraftforge.client.event.MouseEvent left = mouseEvent();
            button.setInt(left, 0); down.setBoolean(left, true); input.mouse(left); assertTrue(left.isCanceled());
            net.minecraftforge.event.entity.player.PlayerInteractEvent interact = new net.minecraftforge.event.entity.player.PlayerInteractEvent(
                Minecraft.getMinecraft().thePlayer, net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK, 5, 64, 7, 1, world) {
                    @Override public boolean isCancelable() { return true; } // Forge's launch transformer supplies this in-game.
                };
            input.interact(interact); assertTrue(interact.isCanceled());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        proxy.getTacticalAreaEditor().reset(); net.minecraftforge.client.event.MouseEvent after = new net.minecraftforge.client.event.MouseEvent(); input.mouse(after);
        assertFalse(after.isCanceled());
    }
    private static net.minecraftforge.client.event.MouseEvent mouseEvent() {
        return new net.minecraftforge.client.event.MouseEvent() {
            @Override public boolean isCancelable() { return true; } // No Forge launch transformer in unit tests.
        };
    }
    @Test public void preciseCornerUsesTabChoiceRegardlessOfMouseHitFaceOrFraction() throws Exception {
        catalogue();queue.drain();
        kome.client.tactical.KOMETacticalAreaEditor local=new kome.client.tactical.KOMETacticalAreaEditor(request -> { });
        local.acceptCatalog(proxy.getTacticalAreaEditor().getCatalog());local.accept(packet(1,1,0).getSnapshot(),KOMETacticalEditSessionManager.Status.OPENED);
        Field controller=KOMEClientProxy.class.getDeclaredField("tacticalAreaEditor");controller.setAccessible(true);controller.set(proxy,local);
        local.clearVertices();local.select(kome.client.tactical.KOMETacticalAreaEditor.Selection.VERTICES);local.cycleCorner();
        kome.client.tactical.KOMETacticalAreaInteractionHandler input=new kome.client.tactical.KOMETacticalAreaInteractionHandler(proxy);
        for(int face:new int[]{1,5}) {
            Minecraft.getMinecraft().thePlayer.rotationYaw=face==1?0:180;
            Minecraft.getMinecraft().objectMouseOver=new net.minecraft.util.MovingObjectPosition(5,64,7,face,net.minecraft.util.Vec3.createVectorHelper(5.99,64.99,7.99));
            net.minecraftforge.client.event.MouseEvent click=mousePress(1);input.mouse(click);assertTrue(click.isCanceled());
            assertEquals(java.util.Collections.singletonList(new kome.common.siege.geometry.KOMEXZPoint(6,7)),local.getConfirmedVertices());
        }
        assertTrue(local.getMessage().contains("already a polygon vertex"));
        Minecraft.getMinecraft().objectMouseOver=new net.minecraft.util.MovingObjectPosition(9,64,11,2,net.minecraft.util.Vec3.createVectorHelper(9,64,11));
        input.mouse(mousePress(1));assertEquals(new kome.common.siege.geometry.KOMEXZPoint(10,11),local.getConfirmedVertices().get(1));
        assertEquals(kome.client.tactical.KOMETacticalPolygonPreview.Corner.NE,local.getCorner());
        local.cancel();assertTrue(local.getConfirmedVertices().isEmpty());assertFalse(local.consumesClicks(playerId,-1));
    }
    @Test public void tabIsConsumedOncePerPressOnlyDuringPolygonSelectionAndDoesNotRemapVanilla() throws Exception {
        catalogue();queue.drain();kome.client.tactical.KOMETacticalAreaEditor local=new kome.client.tactical.KOMETacticalAreaEditor(request->{ });
        local.acceptCatalog(proxy.getTacticalAreaEditor().getCatalog());local.accept(packet(1,1,0).getSnapshot(),KOMETacticalEditSessionManager.Status.OPENED);
        Field controller=KOMEClientProxy.class.getDeclaredField("tacticalAreaEditor");controller.setAccessible(true);controller.set(proxy,local);
        kome.client.tactical.KOMETacticalAreaInteractionHandler input=new kome.client.tactical.KOMETacticalAreaInteractionHandler(proxy);
        Field bindings=net.minecraft.client.settings.KeyBinding.class.getDeclaredField("keybindArray");bindings.setAccessible(true);
        java.util.List<Object> all=(java.util.List<Object>)bindings.get(null);java.util.List<Object> previous=new java.util.ArrayList<>(all);
        java.util.Set<String> categories=new java.util.HashSet<>(net.minecraft.client.settings.KeyBinding.getKeybinds());
        int tab=org.lwjgl.input.Keyboard.KEY_TAB;
        try {
            net.minecraft.client.settings.GameSettings settings=KOMEAccessFixture.allocate(net.minecraft.client.settings.GameSettings.class);
            settings.keyBindPlayerList=new net.minecraft.client.settings.KeyBinding("test.list",tab,"test.tactical");
            settings.keyBindAttack=new net.minecraft.client.settings.KeyBinding("test.attack",541,"test.tactical");
            settings.keyBindUseItem=new net.minecraft.client.settings.KeyBinding("test.use",542,"test.tactical");Minecraft.getMinecraft().gameSettings=settings;
            net.minecraft.client.settings.KeyBinding.setKeyBindState(tab,true);net.minecraft.client.settings.KeyBinding.onTick(tab);
            assertFalse(input.handleKey(tab,true,false));assertTrue(settings.keyBindPlayerList.getIsKeyPressed());assertTrue(settings.keyBindPlayerList.isPressed());
            local.select(kome.client.tactical.KOMETacticalAreaEditor.Selection.VERTICES);
            for(kome.client.tactical.KOMETacticalPolygonPreview.Corner expected:new kome.client.tactical.KOMETacticalPolygonPreview.Corner[]{
                    kome.client.tactical.KOMETacticalPolygonPreview.Corner.NE,kome.client.tactical.KOMETacticalPolygonPreview.Corner.SE,
                    kome.client.tactical.KOMETacticalPolygonPreview.Corner.SW,kome.client.tactical.KOMETacticalPolygonPreview.Corner.NW}) {
                net.minecraft.client.settings.KeyBinding.setKeyBindState(tab,true);net.minecraft.client.settings.KeyBinding.onTick(tab);
                assertTrue(input.handleKey(tab,true,false));assertEquals(expected,local.getCorner());
                assertFalse(settings.keyBindPlayerList.getIsKeyPressed());assertFalse(settings.keyBindPlayerList.isPressed());
                assertTrue(input.handleKey(tab,true,true));assertTrue(input.handleKey(tab,true,false));assertEquals(expected,local.getCorner());
                assertTrue(input.handleKey(tab,false,false));
            }
            assertEquals(4,local.getConfirmedVertexCount()); // cycling never confirms/removes a vertex
            assertEquals(tab,settings.keyBindPlayerList.getKeyCode());
            local.select(kome.client.tactical.KOMETacticalAreaEditor.Selection.UPPER_Y);
            net.minecraft.client.settings.KeyBinding.setKeyBindState(tab,true);net.minecraft.client.settings.KeyBinding.onTick(tab);
            assertFalse(input.handleKey(tab,true,false));assertTrue(settings.keyBindPlayerList.getIsKeyPressed());assertTrue(settings.keyBindPlayerList.isPressed());
            local.cancel();assertFalse(input.handleKey(tab,true,false));assertTrue(settings.keyBindPlayerList.getIsKeyPressed());
        } finally {
            all.clear();all.addAll(previous);net.minecraft.client.settings.KeyBinding.getKeybinds().clear();
            net.minecraft.client.settings.KeyBinding.getKeybinds().addAll(categories);net.minecraft.client.settings.KeyBinding.resetKeyBindingArrayAndHash();
        }
    }
    private static net.minecraftforge.client.event.RenderGameOverlayEvent.Pre overlay(net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType type) {
        return new net.minecraftforge.client.event.RenderGameOverlayEvent.Pre(new net.minecraftforge.client.event.RenderGameOverlayEvent(0,null,0,0),type) {
            @Override public boolean isCancelable() { return true; }
        };
    }
    @Test public void playerListOverlaySuppressionIsRestrictedToActivePolygonSelection() {
        catalogue();queue.drain();proxy.getTacticalAreaEditor().accept(packet(1,1,0).getSnapshot(),KOMETacticalEditSessionManager.Status.OPENED);
        kome.client.tactical.KOMETacticalAreaInteractionHandler input=new kome.client.tactical.KOMETacticalAreaInteractionHandler(proxy);
        net.minecraftforge.client.event.RenderGameOverlayEvent.Pre before=overlay(net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.PLAYER_LIST);input.playerList(before);assertFalse(before.isCanceled());
        proxy.getTacticalAreaEditor().select(kome.client.tactical.KOMETacticalAreaEditor.Selection.VERTICES);
        net.minecraftforge.client.event.RenderGameOverlayEvent.Pre active=overlay(net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.PLAYER_LIST);input.playerList(active);assertTrue(active.isCanceled());
        net.minecraftforge.client.event.RenderGameOverlayEvent.Pre hotbar=overlay(net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.HOTBAR);input.playerList(hotbar);assertFalse(hotbar.isCanceled());
        proxy.getTacticalAreaEditor().select(kome.client.tactical.KOMETacticalAreaEditor.Selection.NONE);
        net.minecraftforge.client.event.RenderGameOverlayEvent.Pre after=overlay(net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType.PLAYER_LIST);input.playerList(after);assertFalse(after.isCanceled());
    }
    @Test public void hudCountsAndListsActualDraftVerticesAndNamesTheHighlightedCandidate() throws Exception {
        catalogue();queue.drain();kome.client.tactical.KOMETacticalAreaEditor local=new kome.client.tactical.KOMETacticalAreaEditor(request->{ });
        local.acceptCatalog(proxy.getTacticalAreaEditor().getCatalog());local.accept(packet(1,1,0).getSnapshot(),KOMETacticalEditSessionManager.Status.OPENED);
        Field controller=KOMEClientProxy.class.getDeclaredField("tacticalAreaEditor");controller.setAccessible(true);controller.set(proxy,local);
        local.clearVertices();local.select(kome.client.tactical.KOMETacticalAreaEditor.Selection.VERTICES);
        local.worldPoint(0,60,0);local.worldPoint(4,60,0);local.worldPoint(0,60,4);
        local.cycleCorner();Minecraft.getMinecraft().objectMouseOver=new net.minecraft.util.MovingObjectPosition(8,64,9,5,net.minecraft.util.Vec3.createVectorHelper(8,64,9));
        java.util.ArrayList<String> left=new java.util.ArrayList<>();
        new kome.client.tactical.KOMETacticalAreaInteractionHandler(proxy).hud(new net.minecraftforge.client.event.RenderGameOverlayEvent.Text(
            new net.minecraftforge.client.event.RenderGameOverlayEvent(0,null,0,0),left,new java.util.ArrayList<>()));
        assertTrue(left.stream().anyMatch(s->s.contains("3 confirmed vertices / 3 distinct")));
        assertTrue(left.contains("1: X 0, Z 0"));assertTrue(left.contains("2: X 4, Z 0"));assertTrue(left.contains("3: X 0, Z 4"));
        assertTrue(left.contains("Next NE: X 9, Z 9"));assertFalse(local.geometryFeedback().isInvalid());
        assertFalse(left.stream().anyMatch(s->s.contains("POLYGON_TOO_FEW_DISTINCT_VERTICES")));
    }
    private static net.minecraftforge.client.event.MouseEvent mousePress(int value) throws ReflectiveOperationException {
        net.minecraftforge.client.event.MouseEvent event = mouseEvent();
        Field button = net.minecraftforge.client.event.MouseEvent.class.getDeclaredField("button"); button.setAccessible(true); button.setInt(event, value);
        Field down = net.minecraftforge.client.event.MouseEvent.class.getDeclaredField("buttonstate"); down.setAccessible(true); down.setBoolean(event, true);
        return event;
    }
    @Test public void preUnloadComplexPageCannotPopulateNewLifecycleEvenWithHigherRevision() throws Exception {
        catalogue(); queue.drain();
        Field controller = KOMEClientProxy.class.getDeclaredField("tacticalAreaEditor"); controller.setAccessible(true);
        kome.client.tactical.KOMETacticalAreaEditor local = new kome.client.tactical.KOMETacticalAreaEditor(request -> { });
        local.acceptCatalog(proxy.getTacticalAreaEditor().getCatalog()); controller.set(proxy, local); local.switchBrowser(true);
        new kome.common.network.KOMEPacketTacticalComplexCatalog.Handler().onMessage(new kome.common.network.KOMEPacketTacticalComplexCatalog(
            new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, -1, 999, 0, 0, java.util.Collections.emptyList())), null);
        Field tasks = KOMEClientTaskQueue.class.getDeclaredField("tasks"); tasks.setAccessible(true);
        java.util.Queue<Runnable> pending = (java.util.Queue<Runnable>) tasks.get(queue); Runnable obsolete = pending.remove();
        proxy.onClientWorldUnload(new WorldEvent.Unload(world));
        catalogue(); queue.drain(); local.switchBrowser(true); obsolete.run(); assertNull(local.getComplexCatalog());
        new kome.common.network.KOMEPacketTacticalComplexCatalog.Handler().onMessage(new kome.common.network.KOMEPacketTacticalComplexCatalog(
            new KOMETacticalComplexCatalog(KOMETacticalComplexCatalog.Kind.COMPLEXES, "T100", null, -1, 10, 0, 0, java.util.Collections.emptyList())), null);
        queue.drain(); assertEquals(10, local.getComplexCatalog().revision);
    }
    @Test public void selectionClearsHeldAndBufferedAttackUseWithoutSuppressingNormalGameplay() throws Exception {
        catalogue(); queue.drain();
        proxy.getTacticalAreaEditor().accept(packet(1, 1, 0).getSnapshot(), KOMETacticalEditSessionManager.Status.OPENED);
        proxy.getTacticalAreaEditor().select(kome.client.tactical.KOMETacticalAreaEditor.Selection.VERTICES);
        Field bindings = net.minecraft.client.settings.KeyBinding.class.getDeclaredField("keybindArray");
        bindings.setAccessible(true);
        @SuppressWarnings("unchecked") java.util.List<Object> all = (java.util.List<Object>) bindings.get(null);
        java.util.List<Object> previous = new java.util.ArrayList<Object>(all);
        java.util.Set<String> categories = new java.util.HashSet<String>(net.minecraft.client.settings.KeyBinding.getKeybinds());
        try {
            net.minecraft.client.settings.GameSettings settings = KOMEAccessFixture.allocate(net.minecraft.client.settings.GameSettings.class);
            settings.keyBindAttack = new net.minecraft.client.settings.KeyBinding("test.attack", 541, "test.tactical");
            settings.keyBindUseItem = new net.minecraft.client.settings.KeyBinding("test.use", 542, "test.tactical");
            Minecraft.getMinecraft().gameSettings = settings;
            net.minecraft.client.settings.KeyBinding.setKeyBindState(541, true);
            net.minecraft.client.settings.KeyBinding.setKeyBindState(542, true);
            net.minecraft.client.settings.KeyBinding.onTick(541); net.minecraft.client.settings.KeyBinding.onTick(542);
            kome.client.tactical.KOMETacticalAreaInteractionHandler input = new kome.client.tactical.KOMETacticalAreaInteractionHandler(proxy);
            input.tick(new cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent(cpw.mods.fml.common.gameevent.TickEvent.Phase.START));
            assertFalse(settings.keyBindAttack.getIsKeyPressed()); assertFalse(settings.keyBindUseItem.getIsKeyPressed());
            assertFalse(settings.keyBindAttack.isPressed()); assertFalse(settings.keyBindUseItem.isPressed());
            proxy.getTacticalAreaEditor().reset();
            net.minecraft.client.settings.KeyBinding.setKeyBindState(541, true);
            net.minecraft.client.settings.KeyBinding.setKeyBindState(542, true);
            net.minecraft.client.settings.KeyBinding.onTick(541); net.minecraft.client.settings.KeyBinding.onTick(542);
            input.tick(new cpw.mods.fml.common.gameevent.TickEvent.ClientTickEvent(cpw.mods.fml.common.gameevent.TickEvent.Phase.START));
            assertTrue(settings.keyBindAttack.getIsKeyPressed()); assertTrue(settings.keyBindUseItem.getIsKeyPressed());
            assertTrue(settings.keyBindAttack.isPressed()); assertTrue(settings.keyBindUseItem.isPressed());
        } finally {
            all.clear(); all.addAll(previous);
            net.minecraft.client.settings.KeyBinding.getKeybinds().clear();
            net.minecraft.client.settings.KeyBinding.getKeybinds().addAll(categories);
            net.minecraft.client.settings.KeyBinding.resetKeyBindingArrayAndHash();
        }
    }
}
