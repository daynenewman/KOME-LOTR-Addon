package kome.common.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class KOMEGateSizeCalculatorTest {
    private static final double APPROXIMATE_TOLERANCE = 0.0002D;

    @Test public void baselineAndUndersizedCasesFollowLockedClearanceFormula() {
        assertEquals(1.0D, KOMEGateSizeCalculator.multiplier(3, 4), 0.0D);
        assertEquals(0.75D, KOMEGateSizeCalculator.multiplier(3, 3), 0.0D);
        assertEquals(0.50D, KOMEGateSizeCalculator.multiplier(2, 3), 0.0D);
        assertEquals(2.0D / 3.0D, KOMEGateSizeCalculator.multiplier(2, 6), 0.0D);
        assertEquals(0.75D, KOMEGateSizeCalculator.multiplier(4, 3), 0.0D);
        assertEquals(1.0D / 6.0D, KOMEGateSizeCalculator.multiplier(1, 2), 0.0D);
    }

    @Test public void largeGateCasesFollowCanonicalDiminishingReturnCurve() {
        assertEquals(1.1670D, KOMEGateSizeCalculator.multiplier(4, 5), APPROXIMATE_TOLERANCE);
        assertEquals(1.3068D, KOMEGateSizeCalculator.multiplier(5, 6), APPROXIMATE_TOLERANCE);
        assertEquals(1.5159D, KOMEGateSizeCalculator.multiplier(6, 8), APPROXIMATE_TOLERANCE);
        assertEquals(1.6699D, KOMEGateSizeCalculator.multiplier(7, 9), APPROXIMATE_TOLERANCE);
        assertEquals(1.8313D, KOMEGateSizeCalculator.multiplier(8, 10), APPROXIMATE_TOLERANCE);
        assertEquals(2.0D, KOMEGateSizeCalculator.multiplier(9, 11), 0.0D);
    }

    @Test public void oneDimensionGrowthCannotEarnLargeGateBonus() {
        assertEquals(1.0D, KOMEGateSizeCalculator.multiplier(3, 8), 0.0D);
        assertEquals(1.0D, KOMEGateSizeCalculator.multiplier(12, 4), 0.0D);
    }

    @Test public void dimensionsBeyondTargetNeverExceedTwoHundredPercent() {
        assertEquals(2.0D, KOMEGateSizeCalculator.multiplier(10, 12), 0.0D);
        assertEquals(2.0D, KOMEGateSizeCalculator.multiplier(1000, 1000), 0.0D);
        assertTrue(KOMEGateSizeCalculator.multiplier(Integer.MAX_VALUE, Integer.MAX_VALUE) <= 2.0D);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nonPositiveDimensionsAreRejected() {
        KOMEGateSizeCalculator.multiplier(0, 4);
    }
}
