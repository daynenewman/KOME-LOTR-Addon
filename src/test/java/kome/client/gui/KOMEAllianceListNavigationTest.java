package kome.client.gui;

import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEAllianceListNavigationTest {
    @Test
    public void currentRelationsScrollToAndSelectBottomEntryAtNormalHeight() {
        int visible = KOMEAllianceListNavigation.visibleRows(240, 94, 34, 11);
        assertTrue(visible > 0);
        assertTrue(visible < 40);

        KOMEAllianceListNavigation navigation = new KOMEAllianceListNavigation();
        navigation.normalize(40, visible);
        for (int i = 0; i < 100; i++) {
            navigation.wheel(-120, 40, visible);
        }

        assertEquals(40 - visible, navigation.getScroll());
        assertTrue(navigation.selectVisibleRow(visible - 1, 40, visible));
        assertEquals(39, navigation.getSelected());
        assertTrue(navigation.isSelectedVisible(visible));
    }

    @Test
    public void wheelKeepsSelectionStableAndInvalidRowsCannotRetargetActions() {
        KOMEAllianceListNavigation navigation = new KOMEAllianceListNavigation();
        navigation.normalize(30, 8);
        assertTrue(navigation.selectVisibleRow(4, 30, 8));
        assertEquals(4, navigation.getSelected());

        navigation.wheel(-120, 30, 8);
        assertEquals(4, navigation.getSelected());
        assertFalse(navigation.selectVisibleRow(8, 30, 8));
        assertEquals(4, navigation.getSelected());
    }

    @Test
    public void requestListUsesTheSameOverflowNavigation() {
        KOMEAllianceListNavigation navigation = new KOMEAllianceListNavigation();
        navigation.normalize(25, 6);
        for (int i = 0; i < 25; i++) {
            navigation.wheel(-120, 25, 6);
        }
        assertEquals(19, navigation.getScroll());
        assertTrue(navigation.selectVisibleRow(5, 25, 6));
        assertEquals(24, navigation.getSelected());
    }
}
