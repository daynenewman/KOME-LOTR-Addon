package kome.client;

import cpw.mods.fml.client.FMLClientHandler;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import kome.common.KOMEAccessFixture;
import lotr.client.gui.LOTRGuiMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEMapRenderLifecycleTest {
    // Bypass only GL allocation/deletion. Exercise the real legacy TextureManager registry
    // and production disconnect/reload callbacks, not a detached cache implementation.
    public static class RecordingTexture extends DynamicTexture {
        int deletes;
        public RecordingTexture() { super(1, 1); }
        @Override public void deleteGlTexture() { deletes++; }
    }

    @Test public void repeatedDisconnectAndReloadReleaseRegisteredObjectsWithoutGrowingRegistry() throws Exception {
        TextureManager manager = new TextureManager(null);
        Minecraft client = KOMEAccessFixture.allocate(Minecraft.class);
        client.renderEngine = manager;
        Field clientField = field(FMLClientHandler.class, "client");
        Object oldClient = clientField.get(FMLClientHandler.instance());
        String[] slots = {"claimed", "desertShade", "label", "troopMarker", "bridgeMarker"};
        Map<Field, Object> saved = new HashMap<Field, Object>();
        for (String slot : slots) for (String suffix : new String[] {"Texture", "TextureLocation"}) {
            Field f = field(KOMEConquestMapOverlay.class, slot + suffix); saved.put(f, f.get(null));
        }
        try {
            clientField.set(FMLClientHandler.instance(), client);
            for (int cycle = 0; cycle < 50; cycle++) {
                RecordingTexture[] textures = new RecordingTexture[slots.length];
                ResourceLocation[] locations = new ResourceLocation[slots.length];
                for (int i = 0; i < slots.length; i++) {
                    textures[i] = KOMEAccessFixture.allocate(RecordingTexture.class);
                    locations[i] = KOMEConquestMapOverlay.registerMapTexture(manager, "test_" + slots[i], textures[i]);
                    field(KOMEConquestMapOverlay.class, slots[i] + "Texture").set(null, textures[i]);
                    field(KOMEConquestMapOverlay.class, slots[i] + "TextureLocation").set(null, locations[i]);
                    assertSame(textures[i], manager.getTexture(locations[i]));
                }
                KOMEConquestMapOverlay.resetClientMapState();
                assertEquals(1, textures[0].deletes);
                assertNotSame(textures[0], manager.getTexture(locations[0]));
                new KOMEConquestMapOverlay().onResourceManagerReload(null);
                for (int i = 0; i < slots.length; i++) {
                    assertEquals(1, textures[i].deletes);
                    assertNotSame(textures[i], manager.getTexture(locations[i]));
                    assertNull(field(KOMEConquestMapOverlay.class, slots[i] + "Texture").get(null));
                    assertNull(field(KOMEConquestMapOverlay.class, slots[i] + "TextureLocation").get(null));
                    ITextureObject released = manager.getTexture(locations[i]);
                    released.loadTexture(null); // Reload cannot resurrect a deleted texture name.
                    assertEquals(0, released.getGlTextureId());
                }
                assertEquals(5, ((Map<?, ?>) field(TextureManager.class, "mapTextureObjects").get(manager)).size());
                assertTrue(((Map<?, ?>) field(TextureManager.class, "mapTextureCounters").get(manager)).isEmpty());
            }
        } finally {
            for (Map.Entry<Field, Object> e : saved.entrySet()) e.getKey().set(null, e.getValue());
            clientField.set(FMLClientHandler.instance(), oldClient);
        }
    }

    @Test public void failureBeforeRegistrationStillReleasesAllocatedTexture() throws Exception {
        TextureManager manager = new TextureManager(null);
        RecordingTexture texture = KOMEAccessFixture.allocate(RecordingTexture.class);
        KOMEConquestMapOverlay.releaseMapTexture(manager, null, texture);
        assertEquals(1, texture.deletes);
        assertTrue(((Map<?, ?>) field(TextureManager.class, "mapTextureObjects").get(manager)).isEmpty());
    }

    @Test public void changedDrawingPassesRestoreTheirGlStateOnNormalAndExceptionalExit() throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream stream = KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")) {
            new ClassReader(stream).accept(node, 0);
        }
        for (String name : new String[] {"drawGeometry", "drawMapTexture"}) {
            MethodNode method = null;
            for (Object item : node.methods) if (((MethodNode) item).name.equals(name)) method = (MethodNode) item;
            assertNotNull(method);
            int mask = 0, pops = 0;
            for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) insn;
                if (!call.owner.equals("org/lwjgl/opengl/GL11")) continue;
                if (call.name.equals("glPushAttrib")) {
                    AbstractInsnNode value = insn.getPrevious();
                    mask = value instanceof LdcInsnNode ? ((Number) ((LdcInsnNode) value).cst).intValue() : ((IntInsnNode) value).operand;
                }
                if (call.name.equals("glPopAttrib")) pops++;
            }
            int required = GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT;
            if (name.equals("drawMapTexture")) required |= GL11.GL_TEXTURE_BIT;
            assertEquals(required, mask & required);
            assertEquals("Normal and finally-handler restoration", 2, pops);
            boolean finallyHandler = false;
            for (Object item : method.tryCatchBlocks) if (((TryCatchBlockNode) item).type == null) finallyHandler = true;
            assertTrue(finallyHandler);
        }
        // This verifies production bytecode discipline, not live GL-driver behavior.
    }

    @Test public void borderViewportRetainsTerrainCenterDespiteNativeMarkerIntegerTruncation() throws Exception {
        LOTRGuiMap map = KOMEAccessFixture.allocate(LOTRGuiMap.class);
        java.lang.reflect.Method transform = LOTRGuiMap.class.getDeclaredMethod("transformMapCoords", float.class, float.class);
        transform.setAccessible(true);
        String[] names = {"mapXMin", "mapXMax", "mapYMin", "mapYMax", "mapWidth", "mapHeight"};
        int[] saved = new int[names.length];
        for (int i = 0; i < names.length; i++) saved[i] = field(LOTRGuiMap.class, names[i]).getInt(null);
        try {
            for (int[] bounds : new int[][] {{10,411,20,321,401,301}, {0,1280,0,720,1280,720}}) {
                for (int i = 0; i < names.length; i++) field(LOTRGuiMap.class, names[i]).setInt(null, bounds[i]);
                for (float zoom : new float[] {0.125F,0.5F,1,4,16}) {
                    field(LOTRGuiMap.class, "posX").setFloat(map, 975.25F);
                    field(LOTRGuiMap.class, "posY").setFloat(map, 730.75F);
                    field(LOTRGuiMap.class, "zoomScale").setFloat(map, zoom);
                    KOMEMapViewport view = KOMEConquestMapOverlay.viewport(map);
                    for (float x : new float[] {0,810,950,975,1083,3200}) for (float y : new float[] {0,58,713,727,735,4000}) {
                        float[] nativePoint = (float[]) transform.invoke(map,x,y);
                        // Bundled LOTR marker transform uses integer width/2; terrain's UV
                        // transform uses floating width/zoom/2. Preserve terrain and hover alignment.
                        double dx = (bounds[4] % 2) / 2.0, dy = (bounds[5] % 2) / 2.0;
                        assertEquals(nativePoint[0] + dx, view.screenX(x), 0.001);
                        assertEquals(nativePoint[1] + dy, view.screenY(y), 0.001);
                        assertEquals(x, view.mapX(nativePoint[0] + dx), 0.001);
                        assertEquals(y, view.mapY(nativePoint[1] + dy), 0.001);
                    }
                }
            }
        } finally { for (int i = 0; i < names.length; i++) field(LOTRGuiMap.class, names[i]).setInt(null, saved[i]); }
    }

    public static class RecordingTessellator extends net.minecraft.client.renderer.Tessellator {
        final java.util.List<Integer> sizes = new java.util.ArrayList<Integer>();
        int vertices, totalVertices, starts, colors;
        public RecordingTessellator() { super(); }
        @Override public void startDrawingQuads() { assertEquals(0, vertices); starts++; }
        @Override public void setColorRGBA_I(int rgb, int alpha) { assertEquals(0x123456, rgb); assertEquals(0xDD, alpha); colors++; }
        @Override public void addVertex(double x, double y, double z) {
            int quad = totalVertices / 4, vertex = totalVertices % 4;
            assertEquals(quad + (vertex == 1 || vertex == 2 ? 1 : 0), x, 0);
            assertEquals(vertex < 2 ? 1 : 0, y, 0); assertEquals(0, z, 0);
            vertices++; totalVertices++;
        }
        @Override public int draw() { assertTrue(vertices > 0); sizes.add(vertices / 4); vertices = 0; return 0; }
    }

    @Test public void productionBatchBoundsTessellatorGrowthWithoutLosingOrReorderingQuads() {
        RecordingTessellator tess = new RecordingTessellator();
        KOMEConquestMapOverlay.GeometryBatch batch = new KOMEConquestMapOverlay.GeometryBatch(tess);
        batch.finish(); assertEquals(0, tess.starts);
        for (int i = 0; i < 5000; i++) batch.quad(i, 0, i + 1, 1, 0xDD123456);
        batch.finish(); batch.finish();
        assertEquals(java.util.Arrays.asList(2047, 2047, 906), tess.sizes);
        assertEquals(5000, tess.colors); assertEquals(20000, tess.totalVertices);
        assertEquals(3, tess.starts);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
}
