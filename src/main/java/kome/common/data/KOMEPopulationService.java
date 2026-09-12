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

    /** Debits the one canonical faction bank for a new combat hire. */
    public static boolean tryDebitCombatHire(KOMEWorldData data, String faction, int amount) {
        return trySpend(data, faction, amount);
    }

    /** Used only to undo a debit when the same hire transaction cannot commit. */
    public static void rollbackCombatHireDebit(KOMEWorldData data, String faction, int amount) {
        grant(data, faction, amount);
    }

    /** Marks a newly-created combat record as funded by the canonical faction bank. */
    public static void recordCombatHirePayment(KOMEHiredUnitRecord record, String faction) {
        if (record == null) {
            throw new IllegalArgumentException("Hired-unit record is required");
        }
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        if (normalizedFaction.length() == 0) {
            throw new IllegalArgumentException("Combat hire requires a nonblank paying faction");
        }
        record.sourceType = KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK;
        record.sourceFaction = normalizedFaction;
        record.allocationTileId = "";
        record.allocationFaction = "";
        record.allocationPlayer = null;
        record.populationOwningFaction = normalizedFaction;
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
