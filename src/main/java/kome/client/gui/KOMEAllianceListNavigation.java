package kome.client.gui;

/** Row-based list navigation shared by the two diplomacy columns. */
final class KOMEAllianceListNavigation {
    private int scroll;
    private int selected = -1;

    void normalize(int size, int visibleRows) {
        int safeSize = Math.max(0, size);
        int safeRows = Math.max(1, visibleRows);
        scroll = clamp(scroll, 0, Math.max(0, safeSize - safeRows));
        if (safeSize == 0) {
            selected = -1;
        } else if (selected < 0 || selected >= safeSize) {
            selected = 0;
        }
    }

    void wheel(int wheelDelta, int size, int visibleRows) {
        normalize(size, visibleRows);
        if (wheelDelta != 0) {
            scroll = clamp(
                scroll + (wheelDelta < 0 ? 1 : -1),
                0,
                Math.max(0, size - Math.max(1, visibleRows)));
        }
    }

    boolean selectVisibleRow(int visibleRow, int size, int visibleRows) {
        normalize(size, visibleRows);
        if (visibleRow < 0 || visibleRow >= Math.max(1, visibleRows)) {
            return false;
        }
        int index = scroll + visibleRow;
        if (index < 0 || index >= size) {
            return false;
        }
        selected = index;
        return true;
    }

    int getScroll() {
        return scroll;
    }

    int getSelected() {
        return selected;
    }

    boolean isSelectedVisible(int visibleRows) {
        return selected >= scroll && selected < scroll + Math.max(1, visibleRows);
    }

    static int visibleRows(int screenHeight, int top, int footerHeight, int rowHeight) {
        int available = Math.max(rowHeight, screenHeight - top - footerHeight);
        return Math.max(1, available / Math.max(1, rowHeight));
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
