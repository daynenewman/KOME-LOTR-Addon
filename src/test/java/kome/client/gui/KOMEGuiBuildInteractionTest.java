package kome.client.gui;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import kome.common.KOMEAccessFixture;
import kome.common.network.KOMEPacketBuildAction;
import kome.common.network.KOMEPacketConquestCaptureGui;
import kome.common.network.KOMEPacketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real screen, button layout, vanilla/Forge mouse dispatch and outgoing packets;
 * only native rendering/audio and the network transport are replaced. */
public class KOMEGuiBuildInteractionTest {
    private SimpleNetworkWrapper previous;
    private RecordingNetwork network;
    private TestMinecraft minecraft;
    private Screen screen;

    @Before public void setup() throws Exception {
        previous = KOMEPacketHandler.network;
        network = KOMEAccessFixture.allocate(RecordingNetwork.class);
        network.sent = new ArrayList<IMessage>();
        KOMEPacketHandler.network = network;
        minecraft = KOMEAccessFixture.allocate(TestMinecraft.class);
        minecraft.sound = KOMEAccessFixture.allocate(SilentSound.class);
        KOMEPacketConquestCaptureGui data = new KOMEPacketConquestCaptureGui();
        data.tileId = "T132"; data.ownerFaction = "dunedain"; data.viewerFaction = "dunedain";
        // Eligible server-snapshot input: owner generation has separate production-builder tests.
        data.selectablePopulationOwners.add("dunedain");
        data.viewerDimension = 100; data.viewerX = 24127.25; data.viewerY = 67; data.viewerZ = -834.75;
        screen = new Screen(data);
        screen.prepare(minecraft, KOMEAccessFixture.allocate(TestFont.class), 788, 458);
        minecraft.currentScreen = screen;
        screen.initGui();
    }

    @After public void cleanup() { KOMEPacketHandler.network = previous; }

    @Test public void openingClickAndReleaseOnlyOpenEditableForm() throws Exception {
        assertTrue(network.sent.isEmpty()); // Initialization sends nothing.
        screen.click("Create Build", 0);
        assertTrue("Opening click must not create a Build", network.sent.isEmpty());
        screen.release();
        screen.updateScreen();
        assertTrue(network.sent.isEmpty());
        assertSame(screen, minecraft.currentScreen);
        assertNotNull(screen.button("Confirm Build"));
        assertEquals("", field("buildNameField").getText());
        assertEquals("0.00", field("buildHoursField").getText());
    }

    @Test public void separateConfirmationSendsExactlyOneRequestWithEditedValues() throws Exception {
        screen.click("Create Build", 0);
        screen.release();
        assertTrue(network.sent.isEmpty());
        field("buildNameField").setText("Manual Weathertop Build");
        field("buildHoursField").setText("1.25");
        screen.click("Confirm Build", 1); // Right-click is not confirmation.
        assertTrue(network.sent.isEmpty());
        screen.click("Confirm Build", 0);
        screen.release();
        screen.updateScreen();
        assertEquals(1, network.sent.size());
        KOMEPacketBuildAction sent = (KOMEPacketBuildAction) network.sent.get(0);
        assertEquals("create", sent.action);
        assertEquals("Manual Weathertop Build", sent.text);
        assertEquals(125L, sent.centiHours);
        assertEquals("dunedain", sent.populationFaction);
        assertEquals("T132", sent.tileId);
        assertEquals(100, sent.dimension);
        assertEquals(24127.25, sent.x, 0);
        assertEquals(-834.75, sent.z, 0);
    }

    @Test public void returningToBuildListCancelsWithoutSubmission() throws Exception {
        screen.click("Create Build", 0);
        screen.release();
        field("buildNameField").setText("Cancelled");
        screen.click("Build List", 0);
        screen.release();
        assertNotNull(screen.button("Create Build"));
        assertTrue(network.sent.isEmpty());
    }

    @Test public void escapeClosesFormWithoutSubmission() throws Exception {
        screen.click("Create Build", 0);
        screen.release();
        field("buildNameField").setText("Cancelled by Escape");
        screen.escape();
        assertNull(minecraft.currentScreen);
        assertTrue(network.sent.isEmpty());
    }

    @Test public void scaledOpeningClickDoesNotReachNewConfirmButton() throws Exception {
        screen.prepare(minecraft, KOMEAccessFixture.allocate(TestFont.class), 394, 229);
        screen.initGui();
        screen.click("Create Build", 0);
        screen.release();
        assertNotNull(screen.button("Confirm Build"));
        assertTrue(network.sent.isEmpty());
    }

    private GuiTextField field(String name) throws Exception {
        Field field = KOMEGuiConquestCapture.class.getDeclaredField(name);
        field.setAccessible(true);
        return (GuiTextField) field.get(screen);
    }

    private static final class Screen extends KOMEGuiConquestCapture {
        Screen(KOMEPacketConquestCaptureGui data) { super(data); }
        void prepare(Minecraft client, FontRenderer font, int w, int h) {
            mc = client; fontRendererObj = font; width = w; height = h;
        }
        GuiButton button(String label) {
            for (Object value : buttonList) {
                GuiButton button = (GuiButton) value;
                if (label.equals(button.displayString)) return button;
            }
            throw new AssertionError("Missing button: " + label);
        }
        void click(String label, int mouseButton) {
            GuiButton button = button(label);
            float scale = Math.min(1F, Math.min(width / 788F, height / 458F));
            mouseClicked(Math.round((button.xPosition + button.width / 2) * scale),
                Math.round((button.yPosition + button.height / 2) * scale), mouseButton);
        }
        void release() { mouseMovedOrUp(0, 0, 0); }
        void escape() { keyTyped((char) 27, 1); }
    }

    public static final class RecordingNetwork extends SimpleNetworkWrapper {
        List<IMessage> sent;
        private RecordingNetwork() { super("unused"); }
        @Override public void sendToServer(IMessage message) { sent.add(message); }
    }

    public static final class TestMinecraft extends Minecraft {
        SoundHandler sound;
        private TestMinecraft() { super(null, 1, 1, false, false, null, null, null, null, "test", null, null); }
        @Override public SoundHandler getSoundHandler() { return sound; }
        @Override public void displayGuiScreen(GuiScreen next) { currentScreen = next; }
        @Override public void setIngameFocus() { }
    }

    public static final class SilentSound extends SoundHandler {
        private SilentSound() { super(null, null); }
        @Override public void playSound(ISound sound) { }
    }

    public static final class TestFont extends FontRenderer {
        private TestFont() { super(null, null, null, false); }
        @Override public int getStringWidth(String text) { return text == null ? 0 : text.length() * 6; }
        @Override public String trimStringToWidth(String text, int width, boolean reverse) {
            int count = Math.min(text.length(), Math.max(0, width / 6));
            return reverse ? text.substring(text.length() - count) : text.substring(0, count);
        }
    }
}
