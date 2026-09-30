package kome.client;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEProgressionTrackerOverlayTest {
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
