package kome.common.data;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEServerTileAwarenessIsolationTest {
    @Test public void dedicatedServerCanLoadHooksAndQueryWithoutClientOrWorldData() throws Exception {
        String[] paths = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) urls[i] = new File(paths[i]).toURI().toURL();
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader().getParent()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("kome.client.") || name.startsWith("lotr.client.")
                    || name.startsWith("net.minecraft.client.") || name.startsWith("org.lwjgl.")
                    || name.equals("kome.common.data.KOMEWorldData"))
                    throw new ClassNotFoundException("Forbidden awareness dependency: " + name);
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> tracker = loader.loadClass("kome.common.data.KOMEServerTileAwareness");
            Object service = tracker.getField("INSTANCE").get(null);
            Class<?> hooks = loader.loadClass("kome.common.data.KOMETileAwarenessEvents");
            hooks.getDeclaredMethods(); // Resolve every handler's legacy event type.
            hooks.getConstructor().newInstance();
            tracker.getMethod("startSession").invoke(service);
            Object result = tracker.getMethod("current", UUID.class).invoke(service, UUID.randomUUID());
            assertEquals("NOT_TRACKED", result.getClass().getField("availability").get(result).toString());
            tracker.getMethod("stopSession").invoke(service);
            assertEquals(0, tracker.getMethod("trackedCount").invoke(service));
        }
    }

    @Test public void addonLifecycleAndDefaultHooksDriveTheActualSingleton() throws Exception {
        kome.common.KOMEAddon addon = kome.common.KOMEAccessFixture.allocate(kome.common.KOMEAddon.class);
        KOMEServerTileAwareness service = KOMEServerTileAwareness.INSTANCE;
        addon.serverAboutToStart(null);
        try {
            KOMEServerTileAwarenessTest.TestWorld world = KOMEServerTileAwarenessTest.world(173);
            KOMEServerTileAwarenessTest.Player player =
                KOMEServerTileAwarenessTest.player(world, UUID.randomUUID());
            KOMETileAwarenessEvents hooks = new KOMETileAwarenessEvents();
            hooks.onLogin(new cpw.mods.fml.common.gameevent.PlayerEvent.PlayerLoggedInEvent(player));
            hooks.onServerTick(new cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent(
                cpw.mods.fml.common.gameevent.TickEvent.Phase.END));
            assertEquals(KOMEServerTileAwareness.Availability.AVAILABLE,
                service.current(player.getUniqueID()).availability);
        } finally { addon.serverStopped(null); }
        assertEquals(0, service.trackedCount());
        assertEquals(KOMEServerTileAwareness.Availability.SERVER_STOPPED,
            service.current(UUID.randomUUID()).availability);
    }

    @Test public void wiringAndMutationBoundariesAreExplicit() throws Exception {
        String tracker = source("common/data/KOMEServerTileAwareness.java");
        String hooks = source("common/data/KOMETileAwarenessEvents.java");
        for (String banned : new String[] {"KOMEWorldData.", "markDirty(", "writeToNBT(", "sendTo",
            "getChunkFrom", "loadChunk(", "loadedEntityList)", "net.minecraft.client", "lotr.client"}) {
            assertFalse(banned, tracker.contains(banned));
            assertFalse(banned, hooks.contains(banned));
        }
        String addon = source("common/KOMEAddon.java"), proxy = source("common/KOMECommonProxy.java");
        assertTrue(addon.contains("serverAboutToStart(FMLServerAboutToStartEvent event) {\n"
            + "        kome.common.data.KOMEServerTileAwareness.INSTANCE.startSession();"));
        assertTrue(addon.contains("serverStopped(FMLServerStoppedEvent event)"));
        assertTrue(addon.contains("KOMEServerTileAwareness.INSTANCE.stopSession();"));
        assertTrue(proxy.contains("MinecraftForge.EVENT_BUS.register(tileAwareness);"));
        assertTrue(proxy.contains("FMLCommonHandler.instance().bus().register(tileAwareness);"));
        assertTrue(hooks.contains("event.phase == TickEvent.Phase.END"));
    }

    private static String source(String relative) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/kome/" + relative)), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
