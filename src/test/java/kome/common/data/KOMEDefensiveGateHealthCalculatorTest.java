package kome.common.data;

import org.junit.Test;

import java.math.BigDecimal;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class KOMEDefensiveGateHealthCalculatorTest {
    @Test public void everyNestedGateUsesFullParentApprovedHourBaseIndependently() {
        KOMEPlayerBuild build = defensiveBuild(10000L);
        KOMEDefensiveGateRecord first = addAutomaticGate(build, 3, 4);
        KOMEDefensiveGateRecord second = addAutomaticGate(build, 3, 4);

        KOMEDefensiveGateHealthCalculator.Result firstResult = calculate(build, first, 84.0D);
        KOMEDefensiveGateHealthCalculator.Result secondResult = calculate(build, second, 84.0D);

        assertTrue(firstResult.isCalculatedMaxHpAvailable());
        assertTrue(secondResult.isCalculatedMaxHpAvailable());
        assertDecimalEquals("8400", firstResult.getCalculatedMaxHp());
        assertDecimalEquals("8400", secondResult.getCalculatedMaxHp());
        assertDecimalEquals("8400", firstResult.getEffectiveMaxHp());
        assertDecimalEquals("8400", secondResult.getEffectiveMaxHp());
    }

    @Test public void approvedPlaytestDefaultProducesCanonicalSizeExamples() {
        KOMEPlayerBuild build = defensiveBuild(10000L);
        assertDecimalEquals("10000", calculate(build, addAutomaticGate(build, 3, 4), 100.0D)
            .getCalculatedMaxHp());
        assertDecimalEquals("5000", calculate(build, addAutomaticGate(build, 2, 3), 100.0D)
            .getCalculatedMaxHp());
        assertDecimalEquals("20000", calculate(build, addAutomaticGate(build, 9, 11), 100.0D)
            .getCalculatedMaxHp());
    }

    @Test public void approvedCentiHoursAreConvertedToRealHoursExactly() {
        KOMEPlayerBuild build = defensiveBuild(150L);
        KOMEDefensiveGateHealthCalculator.Result result =
            calculate(build, addAutomaticGate(build, 3, 4), 10.0D);
        assertTrue(result.isCalculatedMaxHpAvailable());
        assertDecimalEquals("15", result.getCalculatedMaxHp());
    }

    @Test public void missingConfiguredRateStillMakesAutomaticAndEffectiveUnavailableWithoutOverride() {
        KOMEPlayerBuild build = defensiveBuild(1000L);
        KOMEDefensiveGateRecord gate = addAutomaticGate(build, 3, 4);
        KOMEDefensiveGateHealthCalculator.Result result = KOMEDefensiveGateHealthCalculator.calculate(
            build, gate.id, OptionalDouble.empty());
        assertFalse(result.isCalculatedMaxHpAvailable());
        assertFalse(result.isEffectiveMaxHpAvailable());
        assertFalse(result.isAvailable());
        assertEquals(KOMEDefensiveGateHealthCalculator.Status.HP_PER_APPROVED_HOUR_UNAVAILABLE,
            result.getStatus());
    }

    @Test public void nonPositiveAndNonFiniteRatesAreRejected() {
        for (double invalid : new double[] {0.0D, -1.0D, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            KOMEPlayerBuild build = defensiveBuild(1000L);
            KOMEDefensiveGateRecord gate = addAutomaticGate(build, 3, 4);
            KOMEDefensiveGateHealthCalculator.Result result = calculate(build, gate, invalid);
            assertFalse(result.isCalculatedMaxHpAvailable());
            assertFalse(result.isEffectiveMaxHpAvailable());
            assertEquals(KOMEDefensiveGateHealthCalculator.Status.INVALID_HP_PER_APPROVED_HOUR,
                result.getStatus());
        }
    }

    @Test public void unavailableAndInvalidDimensionsHaveDistinctAutomaticResults() {
        KOMEPlayerBuild missingBuild = defensiveBuild(1000L);
        KOMEDefensiveGateRecord missing = addGate(missingBuild);
        KOMEDefensiveGateHealthCalculator.Result missingResult = calculate(missingBuild, missing, 10.0D);
        assertEquals(KOMEDefensiveGateHealthCalculator.Status.DIMENSIONS_UNAVAILABLE,
            missingResult.getStatus());

        KOMEPlayerBuild invalidBuild = defensiveBuild(1000L);
        KOMEDefensiveGateRecord invalid = addAutomaticGate(invalidBuild, 3, 4);
        invalid.detectedWidth = -1;
        KOMEDefensiveGateHealthCalculator.Result invalidResult = calculate(invalidBuild, invalid, 10.0D);
        assertEquals(KOMEDefensiveGateHealthCalculator.Status.INVALID_DIMENSIONS,
            invalidResult.getStatus());
    }

    @Test public void resultSeparatesCalculatedAndOverriddenEffectiveMaxHp() {
        KOMEPlayerBuild build = defensiveBuild(10000L);
        KOMEDefensiveGateRecord gate = addAutomaticGate(build, 3, 4);
        gate.setAdminMaxHpOverride(10000, null, "Admin", 10L, "Reviewed value");
        KOMEDefensiveGateHealthCalculator.Result result = calculate(build, gate, 84.0D);
        assertTrue(result.isCalculatedMaxHpAvailable());
        assertTrue(result.isEffectiveMaxHpAvailable());
        assertTrue(result.isAdminOverrideApplied());
        assertDecimalEquals("8400", result.getCalculatedMaxHp());
        assertDecimalEquals("10000", result.getEffectiveMaxHp());
    }

    @Test public void overrideSuppliesEffectiveHpWhenConfiguredRateIsMissing() {
        KOMEPlayerBuild build = defensiveBuild(10000L);
        KOMEDefensiveGateRecord gate = addAutomaticGate(build, 3, 4);
        gate.setAdminMaxHpOverride(5000, null, "Admin", 10L, "Temporary value");
        KOMEDefensiveGateHealthCalculator.Result result = KOMEDefensiveGateHealthCalculator.calculate(
            build, gate.id, OptionalDouble.empty());
        assertFalse(result.isCalculatedMaxHpAvailable());
        assertTrue(result.isEffectiveMaxHpAvailable());
        assertTrue(result.isAvailable());
        assertTrue(result.isAdminOverrideApplied());
        assertNull(result.getCalculatedMaxHp());
        assertDecimalEquals("5000", result.getEffectiveMaxHp());
        assertEquals(KOMEDefensiveGateHealthCalculator.Status.HP_PER_APPROVED_HOUR_UNAVAILABLE,
            result.getStatus());
    }

    @Test public void overrideSuppliesEffectiveHpWhenDimensionsAreUnavailable() {
        KOMEPlayerBuild build = defensiveBuild(10000L);
        KOMEDefensiveGateRecord gate = addGate(build);
        gate.setAdminMaxHpOverride(5000, null, "Admin", 10L, "Temporary value");
        KOMEDefensiveGateHealthCalculator.Result result = calculate(build, gate, 100.0D);
        assertFalse(result.isCalculatedMaxHpAvailable());
        assertTrue(result.isEffectiveMaxHpAvailable());
        assertDecimalEquals("5000", result.getEffectiveMaxHp());
        assertEquals(KOMEDefensiveGateHealthCalculator.Status.DIMENSIONS_UNAVAILABLE,
            result.getStatus());
    }

    @Test public void overrideSuppliesEffectiveHpWhenAutomaticGeometryIsInvalid() {
        KOMEPlayerBuild build = defensiveBuild(10000L);
        KOMEDefensiveGateRecord gate = addAutomaticGate(build, 3, 4);
        gate.detectedProjectedArea = 11;
        gate.setAdminMaxHpOverride(5000, null, "Admin", 10L, "Temporary value");
        KOMEDefensiveGateHealthCalculator.Result result = calculate(build, gate, 100.0D);
        assertFalse(result.isCalculatedMaxHpAvailable());
        assertTrue(result.isEffectiveMaxHpAvailable());
        assertDecimalEquals("5000", result.getEffectiveMaxHp());
        assertEquals(KOMEDefensiveGateHealthCalculator.Status.INVALID_DIMENSIONS,
            result.getStatus());
    }

    @Test public void clearingOverrideRestoresAutomaticEffectiveResult() {
        KOMEPlayerBuild build = defensiveBuild(10000L);
        KOMEDefensiveGateRecord gate = addAutomaticGate(build, 3, 4);
        gate.setAdminMaxHpOverride(5000, null, "Admin", 10L, "Temporary value");
        gate.clearAdminMaxHpOverride(11L);
        KOMEDefensiveGateHealthCalculator.Result result = calculate(build, gate, 100.0D);
        assertTrue(result.isCalculatedMaxHpAvailable());
        assertTrue(result.isEffectiveMaxHpAvailable());
        assertFalse(result.isAdminOverrideApplied());
        assertDecimalEquals("10000", result.getCalculatedMaxHp());
        assertDecimalEquals("10000", result.getEffectiveMaxHp());
    }

    @Test public void clearingOverrideLeavesEffectiveUnavailableWhenAutomaticIsUnavailable() {
        KOMEPlayerBuild build = defensiveBuild(10000L);
        KOMEDefensiveGateRecord gate = addGate(build);
        gate.setAdminMaxHpOverride(5000, null, "Admin", 10L, "Temporary value");
        gate.clearAdminMaxHpOverride(11L);
        KOMEDefensiveGateHealthCalculator.Result result = calculate(build, gate, 100.0D);
        assertFalse(result.isCalculatedMaxHpAvailable());
        assertFalse(result.isEffectiveMaxHpAvailable());
        assertEquals(KOMEDefensiveGateHealthCalculator.Status.DIMENSIONS_UNAVAILABLE,
            result.getStatus());
    }

    @Test public void staleManualDimensionsFailThroughActualCalculationPath() {
        KOMEPlayerBuild build = defensiveBuild(10000L);
        KOMEDefensiveGateRecord gate = addGate(build);
        gate.capturedStructureRevision = 4;
        gate.dimensionDetectionStatus = KOMEDefensiveGateRecord.DimensionDetectionStatus.AMBIGUOUS;
        gate.setAdminConfirmedDimensions(6, 8, 4, null, "Admin", 10L, "Measured");
        assertTrue(calculate(build, gate, 100.0D).isCalculatedMaxHpAvailable());

        gate.capturedStructureRevision = 5;
        KOMEDefensiveGateHealthCalculator.Result stale = calculate(build, gate, 100.0D);
        assertFalse(stale.isCalculatedMaxHpAvailable());
        assertEquals(KOMEDefensiveGateHealthCalculator.Status.DIMENSIONS_UNAVAILABLE,
            stale.getStatus());
    }

    @Test public void calculationRejectsARecordIdThatIsNotNestedUnderParent() {
        KOMEPlayerBuild build = defensiveBuild(1000L);
        addAutomaticGate(build, 3, 4);
        KOMEDefensiveGateRecord detached = new KOMEDefensiveGateRecord();
        detached.id = "G999";
        detached.setAdminMaxHpOverride(5000, null, "Admin", 10L, "Detached");
        KOMEDefensiveGateHealthCalculator.Result result = KOMEDefensiveGateHealthCalculator.calculate(
            build, detached.id, OptionalDouble.of(100.0D));
        assertFalse(result.isEffectiveMaxHpAvailable());
        assertEquals(KOMEDefensiveGateHealthCalculator.Status.GATE_RECORD_UNAVAILABLE,
            result.getStatus());
    }

    @Test public void normalBuildCannotProduceDefensiveGateHealth() {
        KOMEPlayerBuild normal = defensiveBuild(1000L);
        normal.type = KOMEBuildType.NORMAL;
        KOMEDefensiveGateHealthCalculator.Result result = KOMEDefensiveGateHealthCalculator.calculate(
            normal, "G1", OptionalDouble.of(10.0D));
        assertEquals(KOMEDefensiveGateHealthCalculator.Status.NOT_DEFENSIVE_BUILD, result.getStatus());
        assertFalse(result.isEffectiveMaxHpAvailable());
    }

    @Test public void defensiveCentiHoursRemainExcludedFromNormalBuildPopulationSources() {
        KOMEPlayerBuild build = defensiveBuild(10000L);
        KOMEWorldData data = new KOMEWorldData("test");
        data.builds.put(build.id, build);
        assertTrue(KOMEBuildService.activeNormalBuilds(data).isEmpty());
        assertEquals(10000L, build.approvedDefensiveCentiHours());
    }

    private static KOMEPlayerBuild defensiveBuild(long approvedCentiHours) {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = "B1";
        build.type = KOMEBuildType.DEFENSIVE;
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = "H1";
        contribution.centiHours = approvedCentiHours;
        contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution);
        return build;
    }

    private static KOMEDefensiveGateRecord addGate(KOMEPlayerBuild build) {
        KOMEDefensiveGateRecord record = new KOMEDefensiveGateRecord();
        record.id = build.allocateDefensiveGateRecordId();
        record.gateUuid = UUID.randomUUID();
        record.gateDimension = Integer.valueOf(0);
        record.controllerX = Integer.valueOf(0);
        record.controllerY = Integer.valueOf(64);
        record.controllerZ = Integer.valueOf(0);
        record.capturedStructureRevision = 1;
        build.addDefensiveGateRecord(record);
        return record;
    }

    private static KOMEDefensiveGateRecord addAutomaticGate(KOMEPlayerBuild build,
            int width, int height) {
        KOMEDefensiveGateRecord record = addGate(build);
        record.detectedWidth = width;
        record.detectedHeight = height;
        record.detectedProjectedArea = width * height;
        record.dimensionDetectionStatus = KOMEDefensiveGateRecord.DimensionDetectionStatus.RELIABLE;
        return record;
    }

    private static KOMEDefensiveGateHealthCalculator.Result calculate(KOMEPlayerBuild build,
            KOMEDefensiveGateRecord record, double rate) {
        return KOMEDefensiveGateHealthCalculator.calculate(build, record.id, OptionalDouble.of(rate));
    }

    private static void assertDecimalEquals(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
