package kome.common.data;

import java.math.BigDecimal;
import java.math.BigInteger;
import kome.common.config.KOMEConfigRegistry;

/** Derived audit row; not persisted because Build data, ownership, and config are authoritative. */
public final class KOMEPopulationRateContribution {
    public final String buildId, displayName, tileId, populationFaction, currentController, receivingFaction, status, multiplier;
    public final long approvedCentiHours, multiplierBasisPoints;
    public final BigInteger originalRateUnits, currentRateUnits;
    public KOMEPopulationRateContribution(String buildId, String displayName, String tileId, String populationFaction,
            String currentController, String receivingFaction, long approvedCentiHours, BigInteger originalRateUnits,
            BigInteger currentRateUnits, String status, long multiplierBasisPoints) {
        if (approvedCentiHours < 0L || originalRateUnits == null || currentRateUnits == null
                || originalRateUnits.signum() < 0 || currentRateUnits.signum() < 0)
            throw new IllegalArgumentException("Population time and rates must be nonnegative");
        if (multiplierBasisPoints < 0L || multiplierBasisPoints > KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE)
            throw new IllegalArgumentException("Population multiplier must be between 0 and 10,000 basis points");
        this.buildId = buildId; this.displayName = displayName; this.tileId = tileId;
        this.populationFaction = populationFaction; this.currentController = currentController; this.receivingFaction = receivingFaction;
        this.approvedCentiHours = approvedCentiHours;
        this.originalRateUnits = originalRateUnits; this.currentRateUnits = currentRateUnits;
        this.status = status; this.multiplierBasisPoints = multiplierBasisPoints;
        this.multiplier = BigDecimal.valueOf(multiplierBasisPoints)
                .divide(BigDecimal.valueOf(KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE)).stripTrailingZeros().toPlainString();
    }
    public String formatOriginalRate() { return KOMEPopulationProjection.formatRate(originalRateUnits); }
    public String formatCurrentRate() { return KOMEPopulationProjection.formatRate(currentRateUnits); }
}
