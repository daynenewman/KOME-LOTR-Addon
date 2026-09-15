package kome.common.data;

import java.math.BigDecimal;
import kome.common.config.KOMEConfigRegistry;

/** Derived audit row; not persisted because Build data, ownership, and config are authoritative. */
public final class KOMEPopulationRateContribution {
    public final String buildId, displayName, tileId, populationFaction, currentController, receivingFaction, status, multiplier;
    public final long approvedCentiHours, multiplierBasisPoints;
    public final KOMEPopulationRate originalRate, currentRate;
    public KOMEPopulationRateContribution(String buildId, String displayName, String tileId, String populationFaction,
            String currentController, long approvedCentiHours, KOMEPopulationRate originalRate,
            KOMEPopulationRate currentRate, String status) {
        this(buildId, displayName, tileId, populationFaction, currentController, "", approvedCentiHours, originalRate, currentRate, status, "0");
    }
    public KOMEPopulationRateContribution(String buildId, String displayName, String tileId, String populationFaction,
            String currentController, String receivingFaction, long approvedCentiHours, KOMEPopulationRate originalRate,
            KOMEPopulationRate currentRate, String status, String multiplier) {
        this(buildId, displayName, tileId, populationFaction, currentController, receivingFaction, approvedCentiHours,
                originalRate, currentRate, status, new BigDecimal(multiplier)
                        .multiply(BigDecimal.valueOf(KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE)).longValueExact());
    }
    public KOMEPopulationRateContribution(String buildId, String displayName, String tileId, String populationFaction,
            String currentController, String receivingFaction, long approvedCentiHours, KOMEPopulationRate originalRate,
            KOMEPopulationRate currentRate, String status, long multiplierBasisPoints) {
        if (multiplierBasisPoints < 0L || multiplierBasisPoints > KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE)
            throw new IllegalArgumentException("Population multiplier must be between 0 and 10,000 basis points");
        this.buildId = buildId; this.displayName = displayName; this.tileId = tileId;
        this.populationFaction = populationFaction; this.currentController = currentController; this.receivingFaction = receivingFaction;
        this.approvedCentiHours = approvedCentiHours; this.originalRate = originalRate;
        this.currentRate = currentRate; this.status = status; this.multiplierBasisPoints = multiplierBasisPoints;
        this.multiplier = BigDecimal.valueOf(multiplierBasisPoints)
                .divide(BigDecimal.valueOf(KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE)).stripTrailingZeros().toPlainString();
    }
}
