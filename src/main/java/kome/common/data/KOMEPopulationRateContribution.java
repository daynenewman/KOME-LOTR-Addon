package kome.common.data;

/** Derived audit row; not persisted because Build data, ownership, and config are authoritative. */
public final class KOMEPopulationRateContribution {
    public final String buildId, displayName, tileId, populationFaction, currentController, receivingFaction, status, multiplier;
    public final long approvedCentiHours;
    public final KOMEPopulationRate originalRate, currentRate;
    public KOMEPopulationRateContribution(String buildId, String displayName, String tileId, String populationFaction,
            String currentController, long approvedCentiHours, KOMEPopulationRate originalRate,
            KOMEPopulationRate currentRate, String status) {
        this(buildId, displayName, tileId, populationFaction, currentController, "", approvedCentiHours, originalRate, currentRate, status, "0");
    }
    public KOMEPopulationRateContribution(String buildId, String displayName, String tileId, String populationFaction,
            String currentController, String receivingFaction, long approvedCentiHours, KOMEPopulationRate originalRate,
            KOMEPopulationRate currentRate, String status, String multiplier) {
        this.buildId = buildId; this.displayName = displayName; this.tileId = tileId;
        this.populationFaction = populationFaction; this.currentController = currentController; this.receivingFaction = receivingFaction;
        this.approvedCentiHours = approvedCentiHours; this.originalRate = originalRate;
        this.currentRate = currentRate; this.status = status; this.multiplier = multiplier;
    }
}
