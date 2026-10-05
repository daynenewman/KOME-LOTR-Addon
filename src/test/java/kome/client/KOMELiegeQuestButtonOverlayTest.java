package kome.client;

import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import lotr.client.gui.LOTRGuiUnitTradeInteract;
import net.minecraft.client.gui.GuiButton;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMELiegeQuestButtonOverlayTest {
    private static final int QUEST_BUTTON_ID = 26020;

    @Test
    public void eligibleNativeOfferGuiGetsQuestAndReopenGetsItAgain() {
        LOTRGuiUnitTradeInteract firstGui = gui();
        List<GuiButton> firstButtons = ordinaryButtons(firstGui);

        KOMELiegeQuestButtonOverlay.syncQuestButton(firstGui, firstButtons, true);
        assertNotNull(find(firstButtons, QUEST_BUTTON_ID));
        assertNotNull(find(firstButtons, 0));
        assertNotNull(find(firstButtons, 1));

        // Closing a GUI does not mutate eligibility. A newly opened normal GUI
        // receives Quest again from the same synchronized eligibility state.
        LOTRGuiUnitTradeInteract reopenedGui = gui();
        List<GuiButton> reopenedButtons = ordinaryButtons(reopenedGui);
        KOMELiegeQuestButtonOverlay.syncQuestButton(reopenedGui, reopenedButtons, true);
        assertNotNull(find(reopenedButtons, QUEST_BUTTON_ID));
    }

    @Test
    public void ineligibleGuiNeverGetsQuestAndLosesAnyStaleButton() {
        LOTRGuiUnitTradeInteract gui = gui();
        List<GuiButton> buttons = ordinaryButtons(gui);
        buttons.add(new GuiButton(QUEST_BUTTON_ID, 0, 0, "Quest"));

        KOMELiegeQuestButtonOverlay.syncQuestButton(gui, buttons, false);

        assertNull(find(buttons, QUEST_BUTTON_ID));
        assertNotNull(find(buttons, 0));
        assertNotNull(find(buttons, 1));
    }

    @Test
    public void overlayUsesOneExactNpcAvailabilityHelperWithNativeOfferFallback() throws Exception {
        String source=new String(Files.readAllBytes(Paths.get(
            "src/main/java/kome/client/KOMELiegeQuestButtonOverlay.java")),StandardCharsets.UTF_8);
        assertTrue(source.contains("isStandingTrialAvailableFor("));
        assertTrue(source.contains("hasNativeStandingTrialOffer(localPlayer, npc)"));
        assertTrue(source.contains("npc.questInfo.getOfferFor(localPlayer)"));
        assertTrue(source.contains("current != npc"));
        assertTrue(source.contains("current.getUniqueID().equals(npc.getUniqueID())"));
        assertFalse(source.contains("canRequestStandingTrialFrom"));
    }

    private static LOTRGuiUnitTradeInteract gui() {
        LOTRGuiUnitTradeInteract gui = new LOTRGuiUnitTradeInteract(null);
        gui.width = 320;
        gui.height = 240;
        return gui;
    }

    private static List<GuiButton> ordinaryButtons(LOTRGuiUnitTradeInteract gui) {
        List<GuiButton> buttons = new ArrayList<GuiButton>();
        int y = gui.height / 5 * 3;
        buttons.add(new GuiButton(0, gui.width / 2 - 65, y, 60, 20, "Talk"));
        buttons.add(new GuiButton(1, gui.width / 2 + 5, y, 60, 20, "Hire"));
        return buttons;
    }

    private static GuiButton find(List<GuiButton> buttons, int id) {
        for (GuiButton button : buttons) if (button.id == id) return button;
        return null;
    }
}
