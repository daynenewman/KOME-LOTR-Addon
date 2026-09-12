package kome.common.data;

import java.util.Map;
import kome.common.config.KOMEConfigRegistry;

/** The only calculator for KOME combat-unit population cost. */
public final class KOMEUnitPopulationCostService {
    public static final int MOUNTED_SURCHARGE = 25;
    private KOMEUnitPopulationCostService() { }

    public static int calculate(String entityId, int maxHealth, boolean mounted, boolean farmhand) {
        return calculate(entityId, maxHealth, mounted, farmhand,
                KOMEConfigRegistry.population().getUnitPopulationCostOverrides());
    }

    static int calculate(String entityId, int maxHealth, boolean mounted, boolean farmhand,
            Map<String, Integer> overrides) {
        if (farmhand) return 0;
        String key = entityId == null ? "" : entityId.trim().toLowerCase(java.util.Locale.ROOT);
        Integer override = overrides == null ? null : overrides.get(key);
        if (override != null) return Math.max(1, override.intValue());
        long cost = Math.max(1, maxHealth);
        if (mounted) cost += MOUNTED_SURCHARGE;
        return cost > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) cost;
    }

    public static int reconcileBankedUnitCost(KOMEWorldData data, KOMEHiredUnitRecord record,
            int calculatedCost) {
        if (data == null || record == null || record.farmhand) return 0;
        int desired = Math.max(1, calculatedCost);
        int spent = Math.max(Math.max(0, record.populationSpent), Math.max(0, record.cost));
        int extra = Math.max(0, desired - spent);
        if (extra > 0 && !KOMEPopulationService.tryDebitCombatHire(data, record.populationOwningFaction, extra)) return -1;
        record.cost = desired;
        record.populationSpent = Math.max(spent, desired);
        return extra;
    }
}
