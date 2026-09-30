package kome.common.data;

import org.junit.Test;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import static org.junit.Assert.*;

public class KOMETileResolverIsolationTest {
    @Test public void dedicatedServerClassloaderLoadsAndResolvesWithoutClientOrWorldDataClasses() throws Exception {
        String[] paths = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) urls[i] = new File(paths[i]).toURI().toURL();
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader().getParent()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("kome.client.") || name.startsWith("lotr.client.")
                        || name.startsWith("net.minecraft.client.") || name.startsWith("org.lwjgl.")
                        || name.equals("kome.common.data.KOMEWorldData")) {
                    throw new ClassNotFoundException("Forbidden resolver dependency: " + name);
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> resolverClass = loader.loadClass("kome.common.data.KOMETileWorldResolver");
            Object resolver = resolverClass.getField("INSTANCE").get(null);
            assertEquals(Boolean.TRUE, resolverClass.getMethod("reloadBundled").invoke(resolver));
            Class<?> dimensionClass = loader.loadClass("lotr.common.LOTRDimension");
            Object middleEarth = dimensionClass.getField("MIDDLE_EARTH").get(null);
            int dimension = dimensionClass.getField("dimensionID").getInt(middleEarth);
            Object result = resolverClass.getMethod("resolve", int.class, int.class, int.class).invoke(resolver, dimension, 189696, -86016);
            assertEquals("T001", result.getClass().getField("tileId").get(result));
        }
    }

    @Test public void scopeAndMutationBoundariesStayExplicit() throws Exception {
        String snapshot = read("common/data/KOMETileRasterSnapshot.java");
        assertFalse(snapshot.contains("double")); assertFalse(snapshot.contains("float"));
        for (String file : new String[] {"KOMETileWorldResolver", "KOMETileRasterSnapshot", "KOMETileResolution"}) {
            String source = read("common/data/" + file + ".java");
            for (String banned : new String[] {"import net.minecraft.client", "import lotr.client", "import kome.client",
                    "markDirty(", "getConquestTile(", "KOMEWorldData.", "setCurrentRulingFaction(", "writeToNBT(", "sendToServer(", "Polygon"}) {
                assertFalse(file + ": " + banned, source.contains(banned));
            }
        }
        String defaults = read("common/data/KOMEConquestTileDefaults.java");
        assertFalse(defaults.contains("getNearestTileIdAtPixel")); assertFalse(defaults.contains("clampedX"));
        assertFalse(defaults.contains("Math.round(mapX"));
        String overlay = read("client/KOMEConquestMapOverlay.java");
        assertFalse(overlay.contains("tileMaskPixels[imageY"));
        assertFalse(overlay.contains("getResource(TILE_ID_MASK)"));
        String command = read("common/command/KOMECommandConquest.java");
        assertTrue(command.indexOf("\"resolve\".equalsIgnoreCase") < command.indexOf("KOMEWorldData.get("));
        String build = read("common/data/KOMEBuildService.java");
        assertTrue(build.indexOf("validateCoordinates(tileId, dimension") < build.indexOf("new KOMEPlayerBuild()"));
        assertTrue(build.indexOf("validateCoordinates(tileId, dimension") < build.indexOf("nextBuildId()"));
    }

    @Test public void malformedIdentityResourceFailsExplicitlyWithoutPublishingPartialIds() throws Exception {
        String[] paths = System.getProperty("java.class.path").split(File.pathSeparator);
        URL[] urls = new URL[paths.length];
        for (int i = 0; i < paths.length; i++) urls[i] = new File(paths[i]).toURI().toURL();
        try (URLClassLoader loader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader().getParent()) {
            @Override public java.io.InputStream getResourceAsStream(String name) {
                if ("assets/kome/map/reset_conquest_tile_ids.txt".equals(name)) {
                    return new java.io.ByteArrayInputStream("0,0,1=T001\n0,0,1=T002\n".getBytes(StandardCharsets.UTF_8));
                }
                return super.getResourceAsStream(name);
            }
        }) {
            Class<?> authority = loader.loadClass("kome.common.data.KOMEConquestTileDefaults");
            for (int attempt = 0; attempt < 2; attempt++) {
                try { authority.getMethod("getKnownTileIds").invoke(null); fail("Malformed authority must fail explicitly"); }
                catch (java.lang.reflect.InvocationTargetException expected) {
                    assertTrue(expected.getCause() instanceof IllegalStateException);
                    assertTrue(expected.getCause().getCause().getMessage().contains("Duplicate tile color"));
                }
            }
        }
    }

    private static String read(String file) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/kome/" + file)), StandardCharsets.UTF_8);
    }
}
