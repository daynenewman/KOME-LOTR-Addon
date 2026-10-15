package kome.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.Assert.*;

public class KOMEProgressionTrackerOverlayTest {
    @Test public void completeTrackerPassRestoresAttributesAndMatrixOnSuccessAndException() throws Exception {
        MethodNode draw = method("drawTracker");
        List<String> scopeCalls = new ArrayList<String>();
        TryCatchBlockNode guard = null;
        for (TryCatchBlockNode block : draw.tryCatchBlocks) if (block.type == null) { guard = block; break; }
        assertNotNull("Complete drawing must have a finally handler", guard);
        int start = draw.instructions.indexOf(guard.start), end = draw.instructions.indexOf(guard.end);
        int protectedDrawingCalls = 0;
        List<String> expectedDrawing = new ArrayList<String>(Arrays.asList("bindTexture", "drawTexturedModalRect",
            "drawAlignmentText", "drawSplitString", "renderItemAndEffectIntoGUI", "drawDragOutline"));
        for (AbstractInsnNode instruction : draw.instructions.toArray()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            int position = draw.instructions.indexOf(call);
            if (call.owner.equals("org/lwjgl/opengl/GL11")) {
                if (call.name.equals("glPushAttrib")) {
                    assertTrue(position < start);
                    assertEquals(GL11.GL_ALL_ATTRIB_BITS, ((Number) ((LdcInsnNode) call.getPrevious()).cst).intValue());
                    scopeCalls.add(call.name);
                } else if (call.name.equals("glPushMatrix")) {
                    assertTrue(position < start); scopeCalls.add(call.name);
                } else if (call.name.equals("glPopMatrix") || call.name.equals("glPopAttrib")) {
                    assertTrue(position >= end); scopeCalls.add(call.name);
                } else {
                    assertTrue("GL mutation outside restoration scope: " + call.name, position >= start && position < end);
                }
            }
            if (Arrays.asList("bindTexture", "drawTexturedModalRect", "drawAlignmentText", "drawSplitString",
                    "renderItemAndEffectIntoGUI", "drawDragOutline").contains(call.name)) {
                assertTrue("Drawing outside restoration scope: " + call.name, position >= start && position < end);
                if (expectedDrawing.remove(call.name)) protectedDrawingCalls++;
            }
        }
        assertTrue("All texture, progress, text, item and edit paths must be present", expectedDrawing.isEmpty());
        assertEquals(6, protectedDrawingCalls);
        assertEquals(Arrays.asList("glPushAttrib", "glPushMatrix", "glPopMatrix", "glPopAttrib",
            "glPopMatrix", "glPopAttrib"), scopeCalls);
        assertEquals(Arrays.asList("glPopMatrix", "glPopAttrib"), cleanupCalls(guard.end, false));
        assertEquals(Arrays.asList("glPopMatrix", "glPopAttrib"), cleanupCalls(guard.handler, true));
        // Bytecode discipline only: no GL context or framebuffer correctness is claimed.
    }

    @Test public void hudAndChatRedrawShareTheSameProtectedTrackerPass() throws Exception {
        for (String name : Arrays.asList("onHud", "onChatDraw")) {
            int draws = 0;
            for (AbstractInsnNode instruction : method(name).instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                assertFalse("Entry point must not bypass isolation", call.owner.equals("org/lwjgl/opengl/GL11"));
                if (call.owner.equals("kome/client/KOMEProgressionTrackerOverlay") && call.name.equals("drawTracker")) {
                    assertEquals("(Lnet/minecraft/client/Minecraft;Z)V", call.desc); draws++;
                }
            }
            assertEquals("One shared draw path for " + name, 1, draws);
        }
    }

    private static MethodNode method(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream stream = KOMEProgressionTrackerOverlay.class.getResourceAsStream("KOMEProgressionTrackerOverlay.class")) {
            assertNotNull(stream); new ClassReader(stream).accept(node, 0);
        }
        for (MethodNode method : node.methods) if (method.name.equals(name)) return method;
        throw new AssertionError("Missing method " + name);
    }

    private static List<String> cleanupCalls(AbstractInsnNode first, boolean exceptional) {
        List<String> calls = new ArrayList<String>();
        for (AbstractInsnNode instruction = first; instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (call.owner.equals("org/lwjgl/opengl/GL11")) calls.add(call.name);
            }
            if (instruction.getOpcode() == Opcodes.ATHROW) { assertTrue(exceptional); return calls; }
            if (instruction.getOpcode() == Opcodes.GOTO || instruction.getOpcode() == Opcodes.RETURN) {
                assertFalse("Exception handler must rethrow after restoration", exceptional); return calls;
            }
        }
        throw new AssertionError("Cleanup has no terminating control flow");
    }

    @Test
    public void trackerUsesNativeLotrVisualLanguageAndChatDragEditing()
            throws Exception {
        String source=
            new String(
                Files.readAllBytes(
                    Paths.get(
                        "src/main/java/kome/client/KOMEProgressionTrackerOverlay.java")),
                StandardCharsets.UTF_8);

        assertTrue(
            source.contains(
                "new ResourceLocation(\"lotr:gui/quest/tracker.png\")"));

        assertTrue(
            source.contains(
                "private static final RenderItem RENDER_ITEM"));

        assertTrue(
            source.contains(
                "RENDER_ITEM"));

        assertFalse(
            source.contains(
                "LOTRGuiMiniquestTracker.guiTexture"));

        assertFalse(
            source.contains(
                "LOTRGuiMiniquestTracker.renderItem"));

        assertTrue(
            source.contains(
                "LOTRTickHandlerClient.drawAlignmentText"));

        assertTrue(
            source.contains(
                "LOTRConfig.trackingQuestRight"));

        assertTrue(
            source.contains(
                "minecraft.currentScreen instanceof GuiChat"));

        assertTrue(
            source.contains(
                "Mouse.isButtonDown(0)"));

        assertTrue(
            source.contains(
                "progressionTracker.x"));

        assertTrue(
            source.contains(
                "progressionTracker.y"));

        assertTrue(
            source.contains(
                "GOLD_R=0.84F"));

        assertTrue(
            source.contains(
                "normalizedX"));

        assertTrue(
            source.contains(
                "normalizedY"));

        assertTrue(
            source.contains(
                "DAILY_COMPLETE_TICKS=100"));

        assertTrue(
            source.contains(
                "\"daily_complete\".equals(snapshot.iconKey)"));

        assertTrue(
            source.contains(
                "dailyCompleteTicks--"));

        assertTrue(
            source.contains(
                "return new ItemStack(Items.paper);"));

        assertFalse(
            source.contains(
                "KOMEAddon.sealedMessage"));
    }

    @Test
    public void clientProxyRegistersBothRenderAndTickSidesAndAcceptsSnapshots()
            throws Exception {
        String source=
            new String(
                Files.readAllBytes(
                    Paths.get(
                        "src/main/java/kome/client/KOMEClientProxy.java")),
                StandardCharsets.UTF_8);

        assertTrue(
            source.contains(
                "MinecraftForge.EVENT_BUS.register(progressionTrackerOverlay)"));

        assertTrue(
            source.contains(
                "FMLCommonHandler.instance().bus().register(progressionTrackerOverlay)"));

        assertTrue(
            source.contains(
                "updateProgressionTracker("));

        assertTrue(
            source.contains(
                "progressionTrackerOverlay.update(snapshot)"));

        assertTrue(
            source.contains(
                "progressionTrackerOverlay.resetSession()"));
    }
}
