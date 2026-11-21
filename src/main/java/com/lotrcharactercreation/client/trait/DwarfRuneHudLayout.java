package com.lotrcharactercreation.client.trait;

/** Pure GUI-pixel layout and fill calculations for the Dwarf resource rows. */
public final class DwarfRuneHudLayout {

    public static final int RUNE_WIDTH = 7;
    public static final int RUNE_HEIGHT = 9;
    public static final int ADVANCE = 8;
    public static final int ROW_SEPARATION = 2;
    public static final int ENDURANCE_RUNES = 9;
    public static final int FEASTING_RUNES = 8;
    private static final int HOTBAR_HALF_WIDTH = 91;
    private static final int HOTBAR_HEIGHT = 22;
    private static final int SIDE_GAP = 6;
    private static final int EDGE_MARGIN = 2;

    private DwarfRuneHudLayout() {}

    public static int rowWidth(int runeCount) {
        return runeCount * ADVANCE - 1;
    }

    public static float fillWidth(float resource, float maximum, int runeCount) {
        if (Float.isNaN(resource) || maximum <= 0.0F) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(rowWidth(runeCount), resource / maximum * rowWidth(runeCount)));
    }

    public static float litRuneWidth(float rowFillWidth, int runeIndex) {
        return Math.max(0.0F, Math.min(RUNE_WIDTH, rowFillWidth - runeIndex * ADVANCE));
    }

    public static boolean show(boolean active, boolean hasPlayer, boolean creative, boolean hideGui,
        boolean foodRendered) {
        return active && hasPlayer && !creative && !hideGui && foodRendered;
    }

    public static boolean warn(boolean exhausted) {
        return exhausted;
    }

    public static boolean fitsBesideHotbar(int scaledWidth) {
        return hotbarLeft(scaledWidth) - SIDE_GAP - rowWidth(ENDURANCE_RUNES) >= EDGE_MARGIN
            && hotbarRight(scaledWidth) + SIDE_GAP + rowWidth(FEASTING_RUNES)
                <= scaledWidth - EDGE_MARGIN;
    }

    public static int enduranceX(int scaledWidth) {
        if (fitsBesideHotbar(scaledWidth)) {
            return hotbarLeft(scaledWidth) - SIDE_GAP - rowWidth(ENDURANCE_RUNES);
        }
        if (!fitsFallbackSideBySide(scaledWidth)) {
            return centeredX(scaledWidth, ENDURANCE_RUNES);
        }
        return clampToScreen(hotbarLeft(scaledWidth), scaledWidth, ENDURANCE_RUNES);
    }

    public static int feastingX(int scaledWidth) {
        if (fitsBesideHotbar(scaledWidth)) {
            return hotbarRight(scaledWidth) + SIDE_GAP;
        }
        if (!fitsFallbackSideBySide(scaledWidth)) {
            return centeredX(scaledWidth, FEASTING_RUNES);
        }
        return clampToScreen(hotbarRight(scaledWidth) - rowWidth(FEASTING_RUNES),
            scaledWidth, FEASTING_RUNES);
    }

    public static int enduranceY(int scaledWidth, int scaledHeight, int forgeLeftHeight,
        int forgeRightHeight) {
        int y = rowY(scaledWidth, scaledHeight, forgeLeftHeight, forgeRightHeight);
        return !fitsBesideHotbar(scaledWidth) && !fitsFallbackSideBySide(scaledWidth)
            ? Math.max(0, y - RUNE_HEIGHT - ROW_SEPARATION) : y;
    }

    public static int feastingY(int scaledWidth, int scaledHeight, int forgeLeftHeight,
        int forgeRightHeight) {
        return rowY(scaledWidth, scaledHeight, forgeLeftHeight, forgeRightHeight);
    }

    private static int rowY(int width, int height, int leftHeight, int rightHeight) {
        if (fitsBesideHotbar(width)) {
            return height - HOTBAR_HEIGHT + (HOTBAR_HEIGHT - RUNE_HEIGHT) / 2;
        }
        // The narrow-screen fallback clears Forge's taller status/overlay stack.
        return Math.max(0, height - Math.max(leftHeight, rightHeight) - RUNE_HEIGHT - 2);
    }

    private static boolean fitsFallbackSideBySide(int width) {
        return clampToScreen(hotbarRight(width) - rowWidth(FEASTING_RUNES), width,
                FEASTING_RUNES)
            >= clampToScreen(hotbarLeft(width), width, ENDURANCE_RUNES)
                + rowWidth(ENDURANCE_RUNES) + 2;
    }

    private static int centeredX(int width, int runeCount) {
        return clampToScreen((width - rowWidth(runeCount)) / 2, width, runeCount);
    }

    private static int clampToScreen(int x, int width, int runeCount) {
        return Math.max(0, Math.min(x, Math.max(0, width - rowWidth(runeCount))));
    }

    private static int hotbarLeft(int width) {
        return width / 2 - HOTBAR_HALF_WIDTH;
    }

    private static int hotbarRight(int width) {
        return width / 2 + HOTBAR_HALF_WIDTH;
    }
}
