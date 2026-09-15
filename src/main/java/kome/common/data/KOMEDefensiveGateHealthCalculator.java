package kome.common.data;

import java.math.BigDecimal;
import java.util.OptionalDouble;

/**
 * Pure accounting calculation for a defensive gate. It does not mutate or synchronize
 * the physical siege-gate subsystem.
 */
public final class KOMEDefensiveGateHealthCalculator {
    /** Status of the automatic calculation; effective HP may still exist through an override. */
    public enum Status {
        AVAILABLE,
        NOT_DEFENSIVE_BUILD,
        GATE_RECORD_UNAVAILABLE,
        HP_PER_APPROVED_HOUR_UNAVAILABLE,
        INVALID_HP_PER_APPROVED_HOUR,
        DIMENSIONS_UNAVAILABLE,
        INVALID_DIMENSIONS
    }

    private KOMEDefensiveGateHealthCalculator() {
    }

    /**
     * Authoritative calculation boundary. Resolving by stable ID proves that the record is a
     * current child of the supplied parent Build instead of trusting a detached record object.
     */
    public static Result calculate(KOMEPlayerBuild parentBuild, String gateRecordId,
            OptionalDouble gateHpPerApprovedHour) {
        if (parentBuild == null || !parentBuild.isDefensive()) {
            return Result.automaticUnavailable(Status.NOT_DEFENSIVE_BUILD, null);
        }
        KOMEDefensiveGateRecord gateRecord = parentBuild.getDefensiveGateRecord(gateRecordId);
        if (gateRecord == null) {
            return Result.automaticUnavailable(Status.GATE_RECORD_UNAVAILABLE, null);
        }
        if (gateHpPerApprovedHour == null || !gateHpPerApprovedHour.isPresent()) {
            return Result.automaticUnavailable(Status.HP_PER_APPROVED_HOUR_UNAVAILABLE, gateRecord);
        }

        double hpPerHour = gateHpPerApprovedHour.getAsDouble();
        if (Double.isNaN(hpPerHour) || Double.isInfinite(hpPerHour) || hpPerHour <= 0.0D) {
            return Result.automaticUnavailable(Status.INVALID_HP_PER_APPROVED_HOUR, gateRecord);
        }
        if (gateRecord.dimensionDetectionStatus == KOMEDefensiveGateRecord.DimensionDetectionStatus.INVALID
                || gateRecord.detectedWidth < 0 || gateRecord.detectedHeight < 0
                || gateRecord.detectedProjectedArea < 0
                || (gateRecord.dimensionDetectionStatus == KOMEDefensiveGateRecord.DimensionDetectionStatus.RELIABLE
                    && !gateRecord.hasReliableDetectedDimensions())
                || (gateRecord.adminConfirmedWidth != null && gateRecord.adminConfirmedWidth.intValue() <= 0)
                || (gateRecord.adminConfirmedHeight != null && gateRecord.adminConfirmedHeight.intValue() <= 0)) {
            return Result.automaticUnavailable(Status.INVALID_DIMENSIONS, gateRecord);
        }

        int width = gateRecord.effectiveWidth();
        int height = gateRecord.effectiveHeight();
        if (width <= 0 || height <= 0) {
            return Result.automaticUnavailable(Status.DIMENSIONS_UNAVAILABLE, gateRecord);
        }

        final double sizeMultiplier;
        try {
            sizeMultiplier = KOMEGateSizeCalculator.multiplier(width, height);
        } catch (IllegalArgumentException ignored) {
            return Result.automaticUnavailable(Status.INVALID_DIMENSIONS, gateRecord);
        }

        BigDecimal approvedHours = BigDecimal.valueOf(parentBuild.approvedDefensiveHalfHours())
            .divide(BigDecimal.valueOf(2L));
        BigDecimal calculatedMaxHp = approvedHours
            .multiply(BigDecimal.valueOf(hpPerHour))
            .multiply(BigDecimal.valueOf(sizeMultiplier))
            .stripTrailingZeros();
        boolean overridden = gateRecord.hasAdminMaxHpOverride();
        BigDecimal effectiveMaxHp = overridden
            ? BigDecimal.valueOf(gateRecord.adminMaxHpOverride.intValue()) : calculatedMaxHp;
        return Result.available(calculatedMaxHp, effectiveMaxHp, sizeMultiplier, width, height,
            gateRecord.effectiveDimensionProvenance(), overridden);
    }

    /** Immutable result that keeps Phase 1 free of integer rounding and physical-gate limits. */
    public static final class Result {
        private final Status status;
        private final BigDecimal calculatedMaxHp;
        private final BigDecimal effectiveMaxHp;
        private final double sizeMultiplier;
        private final int effectiveWidth;
        private final int effectiveHeight;
        private final KOMEDefensiveGateRecord.EffectiveDimensionProvenance dimensionProvenance;
        private final boolean adminOverrideApplied;

        private Result(Status status, BigDecimal calculatedMaxHp, BigDecimal effectiveMaxHp,
                double sizeMultiplier, int effectiveWidth, int effectiveHeight,
                KOMEDefensiveGateRecord.EffectiveDimensionProvenance dimensionProvenance,
                boolean adminOverrideApplied) {
            this.status = status;
            this.calculatedMaxHp = calculatedMaxHp;
            this.effectiveMaxHp = effectiveMaxHp;
            this.sizeMultiplier = sizeMultiplier;
            this.effectiveWidth = effectiveWidth;
            this.effectiveHeight = effectiveHeight;
            this.dimensionProvenance = dimensionProvenance;
            this.adminOverrideApplied = adminOverrideApplied;
        }

        private static Result automaticUnavailable(Status status,
                KOMEDefensiveGateRecord gateRecord) {
            boolean overridden = gateRecord != null && gateRecord.hasAdminMaxHpOverride();
            BigDecimal effectiveMaxHp = overridden
                ? BigDecimal.valueOf(gateRecord.adminMaxHpOverride.intValue()) : null;
            return new Result(status, null, effectiveMaxHp, Double.NaN,
                gateRecord == null ? 0 : gateRecord.effectiveWidth(),
                gateRecord == null ? 0 : gateRecord.effectiveHeight(),
                gateRecord == null
                    ? KOMEDefensiveGateRecord.EffectiveDimensionProvenance.UNAVAILABLE
                    : gateRecord.effectiveDimensionProvenance(),
                overridden);
        }

        private static Result available(BigDecimal calculatedMaxHp, BigDecimal effectiveMaxHp,
                double sizeMultiplier, int effectiveWidth, int effectiveHeight,
                KOMEDefensiveGateRecord.EffectiveDimensionProvenance dimensionProvenance,
                boolean adminOverrideApplied) {
            return new Result(Status.AVAILABLE, calculatedMaxHp, effectiveMaxHp, sizeMultiplier,
                effectiveWidth, effectiveHeight, dimensionProvenance, adminOverrideApplied);
        }

        /** Compatibility shorthand for effective availability. */
        public boolean isAvailable() { return isEffectiveMaxHpAvailable(); }
        public boolean isCalculatedMaxHpAvailable() { return calculatedMaxHp != null; }
        public boolean isEffectiveMaxHpAvailable() { return effectiveMaxHp != null; }
        public Status getStatus() { return status; }
        public BigDecimal getCalculatedMaxHp() { return calculatedMaxHp; }
        public BigDecimal getEffectiveMaxHp() { return effectiveMaxHp; }
        public double getSizeMultiplier() { return sizeMultiplier; }
        public int getEffectiveWidth() { return effectiveWidth; }
        public int getEffectiveHeight() { return effectiveHeight; }
        public KOMEDefensiveGateRecord.EffectiveDimensionProvenance getDimensionProvenance() {
            return dimensionProvenance;
        }
        public boolean isAdminOverrideApplied() { return adminOverrideApplied; }
    }
}
