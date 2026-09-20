package kome.client;

import cpw.mods.fml.common.gameevent.TickEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import javax.imageio.ImageIO;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import lotr.common.LOTRDimension;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Production tick/lifecycle/configuration paths with the real resolver; no native window or server required. */
public class KOMECurrentTileHudTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private Minecraft client;
    private KOMEClientConfig config;
    private KOMECurrentTileHud hud;
    private File configFile;
    private Object previousLocale;
    private Object previousMinecraftHome;
    private Map<String, String> lotrTranslations;
    private String previousWeathertop;
    private Map<String, String> translations;
    private Map<String, KOMETileWaypointLink> previousLinks;
    private int previousDimension;

    @Before public void setup() throws Exception {
        previousMinecraftHome = field(cpw.mods.fml.relauncher.FMLInjectionData.class, "minecraftHome").get(null);
        field(cpw.mods.fml.relauncher.FMLInjectionData.class, "minecraftHome").set(null, temporary.getRoot());
        previousDimension = LOTRDimension.MIDDLE_EARTH.dimensionID;
        LOTRDimension.MIDDLE_EARTH.dimensionID = KOMETileTestResources.dimension();
        previousLinks = new HashMap<String, KOMETileWaypointLink>(KOMEClientData.INSTANCE.tileWaypointLinksByTileId);
        KOMEClientData.INSTANCE.tileWaypointLinksByTileId.clear();
        Field localeField = field(I18n.class, "i18nLocale");
        previousLocale = localeField.get(null);
        Object translate = field(net.minecraft.util.StringTranslate.class, "instance").get(null);
        lotrTranslations = (Map<String, String>) field(net.minecraft.util.StringTranslate.class, "languageList").get(translate);
        previousWeathertop = lotrTranslations.put("lotr.waypoint.WEATHERTOP", "Localized Weathertop");
        net.minecraft.client.resources.Locale locale = new net.minecraft.client.resources.Locale();
        translations = (Map<String, String>) field(locale.getClass(), "field_135032_a").get(locale);
        Properties text = new Properties();
        try (Reader reader = new InputStreamReader(getClass().getClassLoader()
                .getResourceAsStream("assets/kome/lang/en_US.lang"), StandardCharsets.UTF_8)) { text.load(reader); }
        for (String key : text.stringPropertyNames()) translations.put(key, text.getProperty(key));
        localeField.set(null, locale);
        client = KOMEAccessFixture.allocate(Minecraft.class);
        client.gameSettings = KOMEAccessFixture.allocate(GameSettings.class);
        client.gameSettings.language = "en_US";
        client.gameSettings.keyBindPlayerList = new KeyBinding("test.playerList", 0, "test");
        client.theWorld = world(KOMETileTestResources.dimension());
        client.thePlayer = player(client.theWorld, 189696, -86016);
        configFile = new File(temporary.getRoot(), "kome-client.cfg");
        config = new KOMEClientConfig(configFile);
        hud = new KOMECurrentTileHud(client, config);
        hud.startSession(hud.suspendSession());
    }

    @After public void cleanup() throws Exception {
        field(I18n.class, "i18nLocale").set(null, previousLocale);
        field(cpw.mods.fml.relauncher.FMLInjectionData.class, "minecraftHome").set(null, previousMinecraftHome);
        LOTRDimension.MIDDLE_EARTH.dimensionID = previousDimension;
        if (previousWeathertop == null) lotrTranslations.remove("lotr.waypoint.WEATHERTOP");
        else lotrTranslations.put("lotr.waypoint.WEATHERTOP", previousWeathertop);
        KOMEClientData.INSTANCE.tileWaypointLinksByTileId.clear();
        KOMEClientData.INSTANCE.tileWaypointLinksByTileId.putAll(previousLinks);
    }

    @Test public void exactBoundaryChangesTileOnlyAtEndOfTick() {
        move(89599.999999, 103936); tick();
        assertEquals("T444", hud.location().tileId);
        client.thePlayer.posX = 89600;
        hud.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.START));
        assertEquals("T444", hud.location().tileId);
        tick(); assertEquals("T454", hud.location().tileId);
        assertEquals("T454", hud.label()); assertTrue(hud.visible());
    }

    @Test public void approvedWeathertopLandBoundaryHasNoIntermediateGap() {
        move(Math.nextDown(21120D), -383.5); tick();
        assertEquals("T149", hud.location().tileId); assertEquals("T149", hud.label());
        move(21120D, -383.5); tick();
        assertEquals("T132", hud.location().tileId); assertEquals("T132", hud.label());
        move(Math.nextUp(21120D), -383.5); tick(); assertEquals("T132", hud.label());
        move(Math.nextDown(21120D), -383.5); tick(); assertEquals("T149", hud.label());
        assertTrue(hud.visible());
    }

    @Test public void tileGapOutsideAndBackNeverRetainAnOldName() {
        tick(); assertEquals("T001", hud.label());
        move(Math.nextDown(189696D), -86016); tick();
        assertEquals(KOMETileResolution.Status.IN_BOUNDS_GAP, hud.location().status);
        assertEquals("No tile", hud.label());
        move(189696D, -86016); tick(); assertEquals("T001", hud.label());
        move(-103681, 0); tick();
        assertEquals(KOMETileResolution.Status.OUTSIDE_MASK, hud.location().status);
        assertEquals("Outside mapped area", hud.label());
        move(24128, -832); tick(); assertEquals("T132", hud.location().tileId);
    }

    @Test public void teleportAndDimensionTransferUseTheCurrentWorldProvider() throws Exception {
        tick();
        client.theWorld = world(KOMETileTestResources.dimension() + 1);
        client.thePlayer.worldObj = client.theWorld;
        assertFalse(hud.visible()); // Do not show the old world between event and next tick.
        tick(); assertEquals(KOMETileResolution.Status.UNSUPPORTED_DIMENSION, hud.location().status);
        assertEquals("", hud.label());
        client.theWorld = world(KOMETileTestResources.dimension());
        client.thePlayer = player(client.theWorld, 24128, -832);
        tick(); assertEquals("T132", hud.location().tileId); assertTrue(hud.visible());
    }

    @Test public void missingWorldPlayerDeathAndRespawnClearPresentation() throws Exception {
        tick();
        WorldClient world = client.theWorld;
        client.theWorld = null; assertFalse(hud.visible()); tick();
        assertNull(hud.location()); assertEquals("", hud.label());
        client.theWorld = world; client.thePlayer = null; tick(); assertNull(hud.location());
        client.thePlayer = player(world, 189696, -86016); tick(); assertTrue(hud.visible());
        client.thePlayer.isDead = true; assertFalse(hud.visible()); tick(); assertNull(hud.location());
        client.thePlayer = player(world, 189568, -86016); tick(); assertEquals("No tile", hud.label());
        client.thePlayer.worldObj = world(KOMETileTestResources.dimension() + 1); tick(); assertNull(hud.location());
    }

    @Test public void actualProxyDisconnectQueueClearsBeforeReconnectPublication() throws Exception {
        tick(); assertTrue(hud.visible());
        KOMEClientProxy proxy = KOMEAccessFixture.allocate(KOMEClientProxy.class);
        KOMEClientTaskQueue queue = new KOMEClientTaskQueue();
        field(KOMEClientProxy.class, "clientTasks").set(proxy, queue);
        field(KOMEClientProxy.class, "currentTileHud").set(proxy, hud);
        proxy.onClientDisconnect(null);
        assertFalse(hud.visible()); assertNull(hud.location()); assertEquals("", hud.label());
        queue.drain(); tick(); assertNull(hud.location());
        proxy.onClientConnect(null);
        tick(); assertNull(hud.location()); // Still gated until queued session reset.
        client.theWorld = world(KOMETileTestResources.dimension());
        client.thePlayer = player(client.theWorld, 189568, -86016);
        queue.drain(); tick();
        assertEquals("No tile", hud.label()); assertTrue(hud.visible());
        proxy.onClientDisconnect(null);
        proxy.onClientConnect(null);
        proxy.onClientDisconnect(null); // Latest network transition wins.
        queue.drain(); tick(); assertNull(hud.location()); assertFalse(hud.visible());
    }

    @Test public void invalidSnapshotAndInvalidNumberReplacePreviousTileWithUnavailable() {
        tick(); assertEquals("T001", hud.label());
        KOMETileWorldResolver.INSTANCE.invalidate(); tick();
        assertEquals(KOMETileResolution.Status.INVALID_SNAPSHOT, hud.location().status);
        assertEquals("Tile unavailable", hud.label()); assertTrue(hud.visible());
        client.theWorld.provider.dimensionId = KOMETileTestResources.dimension() + 1; tick();
        assertFalse(hud.visible()); assertEquals("", hud.label()); // Hidden even if geometry is unavailable.
        client.theWorld.provider.dimensionId = KOMETileTestResources.dimension();
        client.thePlayer.posX = Double.NaN; tick();
        assertEquals(KOMETileResolution.Status.INVALID_COORDINATE, hud.location().status);
        assertEquals("Tile unavailable", hud.label());
    }

    @Test public void replacementChangesIdentityWithoutMovementAndUsesConfiguredDimension() throws Exception {
        tick(); assertEquals("T001", hud.label());
        publish(oneCell("T002", KOMETileTestResources.dimension(), 189696, -86016)); tick();
        assertEquals("T002", hud.label());
        LOTRDimension.MIDDLE_EARTH.dimensionID = 321;
        client.theWorld.provider.dimensionId = 321;
        publish(oneCell("T001", 321, 189696, -86016)); tick();
        assertEquals("T001", hud.label()); assertTrue(hud.visible());
    }

    @Test public void preferenceDefaultsOnAndRealKeyPressPersistsBothToggleDirections() {
        assertTrue(config.showCurrentTile()); assertEquals(0, hud.toggle.getKeyCode());
        tick(); assertTrue(hud.visible());
        hud.toggle.setKeyCode(247);
        KeyBinding.resetKeyBindingArrayAndHash();
        KeyBinding.onTick(247); tick();
        assertFalse(config.showCurrentTile()); assertFalse(hud.visible());
        assertFalse(new KOMEClientConfig(configFile).showCurrentTile());
        KeyBinding.onTick(247); tick();
        assertTrue(config.showCurrentTile()); assertTrue(hud.visible());
        assertTrue(new KOMEClientConfig(configFile).showCurrentTile());
        hud.toggle.setKeyCode(0); KeyBinding.resetKeyBindingArrayAndHash();
    }

    @Test public void screensF1DebugAndPlayerListHideWithoutChangingPreference() throws Exception {
        tick();
        client.gameSettings.hideGUI = true; assertFalse(hud.visible());
        client.gameSettings.hideGUI = false;
        client.gameSettings.showDebugInfo = true; assertFalse(hud.visible());
        client.gameSettings.showDebugInfo = false;
        client.currentScreen = new GuiScreen(); assertFalse(hud.visible());
        client.currentScreen = null;
        field(KeyBinding.class, "pressed").setBoolean(client.gameSettings.keyBindPlayerList, true);
        assertFalse(hud.visible());
        field(KeyBinding.class, "pressed").setBoolean(client.gameSettings.keyBindPlayerList, false);
        assertTrue(config.showCurrentTile()); assertTrue(hud.visible());
    }

    @Test public void namesUseExistingMetadataArrivingLateAndCacheUnchangedText() {
        move(24128, -832); tick(); assertEquals("T132", hud.label());
        KOMETileWaypointLink link = new KOMETileWaypointLink();
        link.tileId = "T132"; link.waypointDisplayName = "Existing place";
        KOMEClientData.INSTANCE.tileWaypointLinksByTileId.put(link.tileId, link);
        tick(); assertEquals("T132 - Existing place", hud.label());
        String same = hud.label(); tick(); assertSame(same, hud.label());
        link.lotrWaypointKey = LOTRWaypoint.WEATHERTOP.getCodeName();
        tick(); assertEquals("T132 - Localized Weathertop", hud.label());
        link.lotrWaypointKey = "unknown_waypoint"; link.waypointDisplayName = "";
        tick(); assertEquals("T132", hud.label());
        KOMEClientData.INSTANCE.tileWaypointLinksByTileId.clear();
        tick(); assertEquals("T132", hud.label());
    }

    @Test public void resourceReloadAndLanguageChangeReformatWithoutStaleText() {
        move(189568, -86016); tick(); assertEquals("No tile", hud.label());
        translations.put("kome.hud.tile.gap", "Translated gap");
        hud.onResourceManagerReload(null); tick(); assertEquals("Translated gap", hud.label());
        translations.put("kome.hud.tile.gap", "Another language");
        client.gameSettings.language = "test"; tick(); assertEquals("Another language", hud.label());
    }

    @Test public void lookupDoesNotCreateOrRepairClientOwnershipRecords() {
        KOMEConquestTile tile = new KOMEConquestTile("T001");
        tile.currentRulingFaction = " DUNEDAIN "; tile.ownerFaction = "mordor";
        Object previous = KOMEClientData.INSTANCE.conquestTiles.put(tile.id, tile);
        boolean dirty = KOMEClientData.INSTANCE.isDirty();
        try {
            KOMEClientData.INSTANCE.setDirty(false);
            tick(); move(189568, -86016); tick();
            assertEquals(" DUNEDAIN ", tile.currentRulingFaction); assertEquals("mordor", tile.ownerFaction);
            assertFalse(KOMEClientData.INSTANCE.isDirty());
        } finally {
            if (previous == null) KOMEClientData.INSTANCE.conquestTiles.remove(tile.id);
            else KOMEClientData.INSTANCE.conquestTiles.put(tile.id, (KOMEConquestTile) previous);
            KOMEClientData.INSTANCE.setDirty(dirty);
        }
    }

    @Test public void registrationRemainsClientOnlyAndRenderDoesNotResolve() throws Exception {
        String proxy = source("client/KOMEClientProxy.java");
        assertTrue(proxy.contains("registerKeyBinding(currentTileHud.toggle)"));
        assertTrue(proxy.contains("bus().register(currentTileHud)"));
        assertTrue(proxy.contains("EVENT_BUS.register(currentTileHud)"));
        for (String file : new String[] {"common/KOMECommonProxy.java", "common/KOMEAddon.java",
                "common/network/KOMEPacketHandler.java", "common/data/KOMEWorldData.java"}) {
            assertFalse(source(file).contains("KOMECurrentTileHud"));
            assertFalse(source(file).contains("KOMEClientConfig"));
        }
        String source = source("client/KOMECurrentTileHud.java");
        assertFalse(source.substring(source.indexOf("public void render(")).contains("resolveWorldPosition"));
        assertFalse(source.contains("sendToServer")); assertFalse(source.contains("getConquestTile("));
    }

    @Test public void olderConnectionResetCannotReactivateADisconnectedSession() {
        tick(); assertTrue(hud.visible());
        long staleConnect = hud.suspendSession();
        hud.suspendSession(); // Newer disconnect arrived while the old reset callback was running.
        hud.startSession(staleConnect);
        tick(); assertNull(hud.location()); assertFalse(hud.visible());
        hud.startSession(hud.suspendSession()); tick(); assertTrue(hud.visible());
    }

    @Test public void untranslatedWaypointKeyIsNotInventedAsATileName() {
        move(24128, -832);
        KOMETileWaypointLink link = new KOMETileWaypointLink();
        link.tileId = "T132"; link.lotrWaypointKey = LOTRWaypoint.WEATHERTOP.getCodeName();
        lotrTranslations.remove("lotr.waypoint.WEATHERTOP");
        KOMEClientData.INSTANCE.tileWaypointLinksByTileId.put(link.tileId, link);
        tick(); assertEquals("T132", hud.label());
        link.waypointDisplayName = "Existing fallback";
        tick(); assertEquals("T132 - Existing fallback", hud.label());
        link.lotrWaypointKey = null; link.waypointDisplayName = null;
        tick(); assertEquals("T132", hud.label());
    }
    private void tick() { hud.onClientTick(new TickEvent.ClientTickEvent(TickEvent.Phase.END)); }
    private void move(double x, double z) { client.thePlayer.posX = x; client.thePlayer.posZ = z; }
    private static WorldClient world(int dimension) throws Exception {
        WorldClient world = KOMEAccessFixture.allocate(WorldClient.class);
        WorldProviderSurface provider = new WorldProviderSurface(); provider.dimensionId = dimension;
        field(World.class, "provider").set(world, provider);
        return world;
    }
    private static EntityClientPlayerMP player(WorldClient world, double x, double z) throws Exception {
        EntityClientPlayerMP player = KOMEAccessFixture.allocate(EntityClientPlayerMP.class);
        player.worldObj = world; player.posX = x; player.posZ = z;
        return player;
    }
    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static KOMETileRasterSnapshot oneCell(String id, int dimension, int x, int z) throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF010203);
        ByteArrayOutputStream png = new ByteArrayOutputStream(); ImageIO.write(image, "png", png);
        return KOMETileRasterSnapshot.load(new ByteArrayInputStream(png.toByteArray()),
            new ByteArrayInputStream(("1,2,3=" + id).getBytes(StandardCharsets.UTF_8)),
            new KOMETileRasterSnapshot.Transform(dimension, -x, -z, 1, 1, 1),
            KOMEConquestTileDefaults.getKnownTileIds(), Collections.<String>emptySet());
    }
    private static void publish(KOMETileRasterSnapshot snapshot) throws Exception {
        Method method = KOMETileWorldResolver.class.getDeclaredMethod("publish", KOMETileRasterSnapshot.class);
        method.setAccessible(true); method.invoke(KOMETileWorldResolver.INSTANCE, snapshot);
    }
    private static String source(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/kome/" + file)), StandardCharsets.UTF_8);
    }
}
