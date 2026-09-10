package com.lotrcharactercreation.client.render;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import net.minecraft.item.Item;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;

import org.junit.Test;

public class RacePlayerRendererTest {

    private static final float FLOAT_TOLERANCE = 0.000001F;

    @Test
    public void castFishingRodKeepsUncastRodCorrectionSource() {
        ItemStack fishingRod = new ItemStack(new ItemFishingRod());
        ItemStack renderedStick = new ItemStack(new Item().setFull3D());

        ItemStack uncastSource = RacePlayerRenderer.selectHobbitHeldItemForCorrection(fishingRod, fishingRod);
        ItemStack castSource = RacePlayerRenderer.selectHobbitHeldItemForCorrection(fishingRod, renderedStick);
        float uncastCorrection = RacePlayerRenderer.getHobbitHeldItemYCorrection(uncastSource);
        float castCorrection = RacePlayerRenderer.getHobbitHeldItemYCorrection(castSource);

        assertSame(fishingRod, uncastSource);
        assertSame(fishingRod, castSource);
        assertTrue(castSource.getItem().isFull3D());
        assertTrue(castSource.getItem().shouldRotateAroundWhenRendering());
        assertEquals(0.0F, uncastCorrection, FLOAT_TOLERANCE);
        assertEquals(uncastCorrection, castCorrection, FLOAT_TOLERANCE);
        assertTrue(uncastCorrection < 0.0625F);
    }

    @Test
    public void substitutedStickHasTheDifferentDefaultTransformCategory() {
        ItemStack renderedStick = new ItemStack(new Item().setFull3D());
        ItemStack correctionSource = RacePlayerRenderer
            .selectHobbitHeldItemForCorrection(renderedStick, renderedStick);

        assertSame(renderedStick, correctionSource);
        assertTrue(correctionSource.getItem().isFull3D());
        assertFalse(correctionSource.getItem().shouldRotateAroundWhenRendering());
        assertEquals(
            -0.1125F,
            RacePlayerRenderer.calculateHobbitHeldItemYCorrection(false, false, false),
            FLOAT_TOLERANCE);
    }

    @Test
    public void ordinaryNonRodItemsRetainRenderedItemClassification() {
        Item rotatingFull3DItem = new Item() {

            @Override
            public boolean shouldRotateAroundWhenRendering() {
                return true;
            }
        }.setFull3D();
        ItemStack originalItem = new ItemStack(rotatingFull3DItem);
        ItemStack renderedItem = new ItemStack(new Item().setFull3D());

        ItemStack correctionSource = RacePlayerRenderer
            .selectHobbitHeldItemForCorrection(originalItem, renderedItem);

        assertSame(renderedItem, correctionSource);
        assertFalse(correctionSource.getItem().shouldRotateAroundWhenRendering());
        assertEquals(
            -0.1125F,
            RacePlayerRenderer.calculateHobbitHeldItemYCorrection(true, false, false),
            FLOAT_TOLERANCE);
        assertEquals(
            -0.05F,
            RacePlayerRenderer.calculateHobbitHeldItemYCorrection(false, true, false),
            FLOAT_TOLERANCE);
        assertEquals(
            0.0625F,
            RacePlayerRenderer.calculateHobbitHeldItemYCorrection(false, false, true),
            FLOAT_TOLERANCE);
    }
}
