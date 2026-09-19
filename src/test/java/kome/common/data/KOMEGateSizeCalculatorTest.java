package kome.common.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KOMEGateSizeCalculatorTest {
    private static final double APPROXIMATE_TOLERANCE = 0.0002D;

    @Test public void baselineAndUndersizedCasesFollowLockedClearanceFormula() {
        assertEquals(1.0D, KOMEGateSizeCalculator.multiplier(5, 5), 0.0D);
        assertEquals(0.8D, KOMEGateSizeCalculator.multiplier(5, 4), 0.0D);
        assertEquals(0.8D, KOMEGateSizeCalculator.multiplier(4, 5), 0.0D);
        assertEquals(0.48D, KOMEGateSizeCalculator.multiplier(3, 4), 0.0D);
        assertEquals(0.4D, KOMEGateSizeCalculator.multiplier(2, 6), 0.0D);
        assertEquals(0.08D, KOMEGateSizeCalculator.multiplier(1, 2), 0.0D);
    }

    @Test public void largeGateCasesFollowCanonicalDiminishingReturnCurve() {
        assertEquals(1.0D + 0.5D * Math.pow(11.0D / 119.0D, 0.75D),
            KOMEGateSizeCalculator.multiplier(6, 6), APPROXIMATE_TOLERANCE);
        assertEquals(1.0D + 0.5D * Math.pow(24.0D / 119.0D, 0.75D),
            KOMEGateSizeCalculator.multiplier(7, 7), APPROXIMATE_TOLERANCE);
        assertEquals(1.0D + 0.5D * Math.pow(39.0D / 119.0D, 0.75D),
            KOMEGateSizeCalculator.multiplier(8, 8), APPROXIMATE_TOLERANCE);
        assertEquals(1.0D + 0.5D * Math.pow(75.0D / 119.0D, 0.75D),
            KOMEGateSizeCalculator.multiplier(10, 10), APPROXIMATE_TOLERANCE);
        assertEquals(1.5D, KOMEGateSizeCalculator.multiplier(12, 12), 0.0D);
    }

    @Test public void oneDimensionGrowthCannotEarnLargeGateBonus() {
        assertEquals(1.0D, KOMEGateSizeCalculator.multiplier(5, 10), 0.0D);
        assertEquals(1.0D, KOMEGateSizeCalculator.multiplier(20, 5), 0.0D);
    }

    @Test public void dimensionsBeyondTargetNeverExceedConfiguredOnePointFiveCap() {
        assertEquals(1.5D, KOMEGateSizeCalculator.multiplier(13, 13), 0.0D);
        assertEquals(1.5D, KOMEGateSizeCalculator.multiplier(1000, 1000), 0.0D);
        assertTrue(KOMEGateSizeCalculator.multiplier(Integer.MAX_VALUE, Integer.MAX_VALUE) <= 1.5D);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveDimensionsAreRejected() {
        KOMEGateSizeCalculator.multiplier(0, 4);
    }

    @Test public void customBalanceParametersChangeBaselineTargetCapAndCurve() {
        KOMEGateSizeCalculator.Parameters custom = new KOMEGateSizeCalculator.Parameters(
            2, 2, 6, 6, 1.5D, 0.5D);
        assertEquals(1.0D, KOMEGateSizeCalculator.multiplier(2, 2, custom), 0.0D);
        assertEquals(1.5D, KOMEGateSizeCalculator.multiplier(6, 6, custom), 0.0D);
        assertEquals(1.0D + 0.5D * Math.sqrt(0.375D),
            KOMEGateSizeCalculator.multiplier(4, 4, custom), 0.0D);
    }

    @Test public void invalidBalanceParametersAreRejected() {
        assertInvalid(new Runnable() { public void run() {
            new KOMEGateSizeCalculator.Parameters(0, 4, 9, 11, 2.0D, 0.75D); }});
        assertInvalid(new Runnable() { public void run() {
            new KOMEGateSizeCalculator.Parameters(3, 4, 3, 11, 2.0D, 0.75D); }});
        assertInvalid(new Runnable() { public void run() {
            new KOMEGateSizeCalculator.Parameters(3, 4, 9, 11, Double.NaN, 0.75D); }});
        assertInvalid(new Runnable() { public void run() {
            new KOMEGateSizeCalculator.Parameters(3, 4, 9, 11, 2.0D, 0.0D); }});
    }

    private static void assertInvalid(Runnable runnable) {
        try {
            runnable.run();
            throw new AssertionError("Expected invalid size parameters to fail");
        } catch (IllegalArgumentException expected) {
        }
    }
}
