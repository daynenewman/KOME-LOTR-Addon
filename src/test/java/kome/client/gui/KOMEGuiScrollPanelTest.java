package kome.client.gui;

import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEGuiScrollPanelTest {
    @Test
    public void clampsScrollToContentRange() {
        KOMEGuiScrollPanel panel = new KOMEGuiScrollPanel().layout(10, 20, 100, 80, 230);
        panel.setScroll(999);
        assertEquals(150, panel.getScroll());
        panel.setScroll(-20);
        assertEquals(0, panel.getScroll());
    }

    @Test
    public void wheelMovesOnlyInsideViewport() {
        KOMEGuiScrollPanel panel = new KOMEGuiScrollPanel().layout(10, 20, 100, 80, 230);
        assertFalse(panel.wheel(5, 5, -120, 18));
        assertEquals(0, panel.getScroll());
        assertTrue(panel.wheel(20, 30, -120, 18));
        assertEquals(18, panel.getScroll());
        assertTrue(panel.wheel(20, 30, 120, 18));
        assertEquals(0, panel.getScroll());
    }

    @Test
    public void relayoutRetainsReasonablePositionAndClampsWhenContentShrinks() {
        KOMEGuiScrollPanel panel = new KOMEGuiScrollPanel().layout(0, 0, 100, 100, 400);
        panel.setScroll(180);
        panel.layout(0, 0, 120, 100, 420);
        assertEquals(180, panel.getScroll());
        panel.layout(0, 0, 120, 100, 160);
        assertEquals(60, panel.getScroll());
    }

    @Test
    public void shortContentHasNoScrollRange() {
        KOMEGuiScrollPanel panel = new KOMEGuiScrollPanel().layout(0, 0, 100, 100, 40);
        panel.setScroll(20);
        assertEquals(0, panel.getMaxScroll());
        assertEquals(0, panel.getScroll());
    }
}
