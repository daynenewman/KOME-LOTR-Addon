package kome.common.data;

import java.util.Collection;

/** Read-only access to the canonical faction bank and informational active population. */
public final class KOMEPopulationService {
    private KOMEPopulationService() {
    }

    public static int getAvailablePopulation(KOMEWorldData data, String faction) {
        if (data == null) {
            throw new IllegalArgumentException("World data is required");
        }
        KOMEFactionPopulation population = data.getFactionPopulationIfPresent(faction);
        return population == null ? 0 : population.getAvailablePopulation();
    }

    public static boolean trySpend(KOMEWorldData data, String faction, int amount) {
        if (data == null) {
            throw new IllegalArgumentException("World data is required");
        }
        return data.trySpendFactionPopulation(faction, amount);
    }

    public static void grant(KOMEWorldData data, String faction, int amount) {
        if (data == null) {
            throw new IllegalArgumentException("World data is required");
        }
        data.grantFactionPopulation(faction, amount);
    }

    /**
     * Active population is informational only. Callers must provide records known
     * to be living: hired records alone do not persist an authoritative liveness flag.
     */
    public static int getActivePopulation(String faction, Collection<KOMEHiredUnitRecord> livingRecords) {
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        if (normalizedFaction.length() == 0 || livingRecords == null) {
            return 0;
        }
        int total = 0;
        for (KOMEHiredUnitRecord record : livingRecords) {
            if (record == null || record.farmhand || !normalizedFaction.equals(populationFaction(record))) {
                continue;
            }
            total = saturatingAdd(total, Math.max(0, record.cost));
        }
        return total;
    }

    private static String populationFaction(KOMEHiredUnitRecord record) {
        String owningFaction = KOMEAlliance.normalizeFactionKey(record.populationOwningFaction);
        return owningFaction.length() > 0 ? owningFaction : KOMEAlliance.normalizeFactionKey(record.sourceFaction);
    }

    private static int saturatingAdd(int left, int right) {
        long total = (long) left + right;
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }
}
