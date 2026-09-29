package kome.common.data;

import java.util.UUID;

/** Read-only totals for an explicit set of CAMPAIGN hired-unit records. */
public final class KOMECampaignUnitTotals {
    public final int unitCount;
    public final int population;
    public final int mountedPopulation;
    public final int groundPopulation;

    private KOMECampaignUnitTotals(int unitCount, int population,
            int mountedPopulation, int groundPopulation) {
        this.unitCount = unitCount;
        this.population = population;
        this.mountedPopulation = mountedPopulation;
        this.groundPopulation = groundPopulation;
    }

    public static KOMECampaignUnitTotals of(KOMEWorldData data, Iterable<UUID> unitIds) {
        int units = 0;
        int total = 0;
        int mounted = 0;
        int ground = 0;
        if (data != null && unitIds != null) {
            for (UUID unitId : unitIds) {
                KOMEHiredUnitRecord record = unitId == null ? null : data.hiredUnits.get(unitId);
                if (!KOMEHiredUnitClassification.isCampaignUnit(record) || record.farmhand) {
                    continue;
                }
                int cost = Math.max(0, record.cost);
                units++;
                total = saturatedAdd(total, cost);
                if (record.mounted) mounted = saturatedAdd(mounted, cost);
                else ground = saturatedAdd(ground, cost);
            }
        }
        return new KOMECampaignUnitTotals(units, total, mounted, ground);
    }

    private static int saturatedAdd(int left, int right) {
        long value = (long) left + right;
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
