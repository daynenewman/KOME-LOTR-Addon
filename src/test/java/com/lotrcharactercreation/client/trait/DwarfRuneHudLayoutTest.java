package com.lotrcharactercreation.client.trait;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;

import javax.imageio.ImageIO;

import org.junit.Test;

import com.lotrcharactercreation.network.DwarfTraitStateMessage;
import com.lotrcharactercreation.trait.DwarfTraitService;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class DwarfRuneHudLayoutTest {

    private static final String[][] EXPECTED_GLYPHS = {
        {"#.....#", "#.....#", "#.....#", "#.....#", "##....#", "#.#...#", "#..#..#", "#...#.#", "#....##"},
        {"#..#..#", "#..#..#", "#..#..#", ".#.#.#.", ".#.#.#.", "..###..", "...#...", "...#...", "...#..."},
        {"#......", "##.....", "#.#....", "#..#...", "#......", "##.....", "#.#....", "#..#...", "#......"},
        {"...#...", "..#.#..", ".#...#.", "#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#."},
        {"...#...", "..#.#..", ".#...#.", "#.....#", "...#..#", "...#...", "...#...", "...#...", "...#..."},
        {"#......", "##.....", "#.#....", "#..#...", "#...#..", "#....#.", "#.....#", "#.....#", "#.....#"},
        {"...#...", "...#...", "...#...", "...#...", "...#...", "..#.#..", ".#...#.", "#.....#", "......."},
        {"......#", "....#.#", "..#...#", "#.....#", "..#...#", "....#.#", "......#", "......#", "......#"},
        {"#......", "##.....", "#.#....", "#..#...", "#......", "#......", "#......", "#......", "#......"},
        {"...#...", "...#...", "...#...", "...#...", "...#...", "...#...", "...#...", "...#...", "...#..."},
        {"#...#.#", "#..#..#", "#.#...#", "##....#", "#......", "#......", "#......", "#......", "#......"}
    };

    @Test
    public void resourceFillClampsAtBothEndsAndKeepsFractionalRuneProgress() {
        int count = DwarfRuneHudLayout.ENDURANCE_RUNES;
        assertEquals(0.0F, DwarfRuneHudLayout.fillWidth(-1, 100, count), 0.0F);
        assertEquals(0.0F, DwarfRuneHudLayout.fillWidth(Float.NaN, 100, count), 0.0F);
        assertEquals(71.0F, DwarfRuneHudLayout.fillWidth(110, 100, count), 0.0F);
        assertEquals(35.5F, DwarfRuneHudLayout.fillWidth(50, 100, count), 0.0F);
        assertEquals(3.5F, DwarfRuneHudLayout.litRuneWidth(35.5F, 4), 0.0F);
        assertEquals(7.0F, DwarfRuneHudLayout.litRuneWidth(35.5F, 3), 0.0F);
        assertEquals(0.0F, DwarfRuneHudLayout.litRuneWidth(35.5F, 5), 0.0F);
        assertEquals(31.5F, DwarfRuneHudLayout.fillWidth(10, DwarfTraitService.MAX_FEAST,
            DwarfRuneHudLayout.FEASTING_RUNES), 0.0F);
    }

    @Test
    public void exhaustionWarningFollowsSynchronizedFlagEvenDuringRecovery() {
        assertTrue(DwarfRuneHudLayout.warn(true));
        assertFalse(DwarfRuneHudLayout.warn(false));
    }

    @Test
    public void rowsFlankTheScaledVanillaHotbarAtItsVerticalCenter() {
        assertTrue(DwarfRuneHudLayout.fitsBesideHotbar(480));
        assertEquals(72, DwarfRuneHudLayout.enduranceX(480));
        assertEquals(337, DwarfRuneHudLayout.feastingX(480));
        assertEquals(254, DwarfRuneHudLayout.enduranceY(480, 270, 49, 49));
        assertEquals(254, DwarfRuneHudLayout.feastingY(480, 270, 49, 59));
        assertEquals(2, DwarfRuneHudLayout.enduranceX(340)); // first width with both sides clear
        assertFalse(DwarfRuneHudLayout.fitsBesideHotbar(338));
        assertEquals(214, DwarfRuneHudLayout.enduranceY(480, 230, 49, 49));
    }

    @Test
    public void narrowScreensClearStatusRowsAndNeverOverlapOrClipRuneRows() {
        assertFalse(DwarfRuneHudLayout.fitsBesideHotbar(320));
        assertEquals(69, DwarfRuneHudLayout.enduranceX(320));
        assertEquals(188, DwarfRuneHudLayout.feastingX(320));
        assertEquals(170, DwarfRuneHudLayout.enduranceY(320, 230, 49, 49));
        assertEquals(160, DwarfRuneHudLayout.feastingY(320, 230, 49, 59));
        assertEquals(113, DwarfRuneHudLayout.feastingX(176));
        assertTrue(DwarfRuneHudLayout.enduranceX(176) + DwarfRuneHudLayout.rowWidth(9) + 2
            <= DwarfRuneHudLayout.feastingX(176));
        assertEquals(31, DwarfRuneHudLayout.enduranceX(134));
        assertEquals(35, DwarfRuneHudLayout.feastingX(134));
        assertEquals(159, DwarfRuneHudLayout.enduranceY(134, 230, 49, 49));
        assertEquals(170, DwarfRuneHudLayout.feastingY(134, 230, 49, 49));
        assertEquals(211, DwarfRuneHudLayout.feastingY(320, 271, 49, 49));
    }

    @Test
    public void visibilityRequiresTheVanillaFoodPassAndNormalHudConditions() {
        assertTrue(DwarfRuneHudLayout.show(true, true, false, false, true));
        assertFalse(DwarfRuneHudLayout.show(false, true, false, false, true));
        assertFalse(DwarfRuneHudLayout.show(true, false, false, false, true));
        assertFalse(DwarfRuneHudLayout.show(true, true, true, false, true));
        assertFalse(DwarfRuneHudLayout.show(true, true, false, true, true));
        assertFalse(DwarfRuneHudLayout.show(true, true, false, false, false));
    }

    @Test
    public void atlasExactlyMatchesAllApprovedGlyphPatternsAndTransparentPixels() throws Exception {
        BufferedImage image = ImageIO.read(getClass().getResourceAsStream(
            "/assets/lotrcharactercreation/textures/gui/dwarf_cirth.png"));
        assertNotNull(image);
        assertEquals(128, image.getWidth());
        assertEquals(16, image.getHeight());
        assertEquals(11, EXPECTED_GLYPHS.length);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int glyph = x / 8;
                int glyphX = x % 8;
                int glyphY = y - 2;
                boolean expectedRunePixel = glyph < EXPECTED_GLYPHS.length
                    && glyphX < DwarfRuneHudLayout.RUNE_WIDTH
                    && glyphY >= 0
                    && glyphY < DwarfRuneHudLayout.RUNE_HEIGHT
                    && EXPECTED_GLYPHS[glyph][glyphY].charAt(glyphX) == '#';
                int expectedArgb = expectedRunePixel ? 0xFFFFFFFF : 0x00000000;
                assertEquals("atlas pixel at " + x + "," + y, expectedArgb, image.getRGB(x, y));
            }
        }
    }

    @Test
    public void existingDwarfWireStateRetainsAllFourFields() {
        DwarfTraitStateMessage sent = new DwarfTraitStateMessage(true, 63.25F, 17, true);
        ByteBuf buffer = Unpooled.buffer();
        sent.toBytes(buffer);
        DwarfTraitStateMessage received = new DwarfTraitStateMessage();
        received.fromBytes(buffer);
        assertTrue(received.isActive());
        assertEquals(63.25F, received.getStamina(), 0.0F);
        assertEquals(17, received.getFeast());
        assertTrue(received.isExhausted());
        assertEquals(0, buffer.readableBytes());
    }
}
