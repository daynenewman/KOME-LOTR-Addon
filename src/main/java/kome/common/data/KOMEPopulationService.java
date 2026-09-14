package kome.common.data;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Canonical faction-bank mutations plus informational active-population projections. */
public final class KOMEPopulationService {
    public static final long CENTI_PER_POPULATION = 100L;

    private KOMEPopulationService() {
    }

    public static long getAvailablePopulationCenti(KOMEWorldData data, String faction) {
        if (data == null) {
            throw new IllegalArgumentException("World data is required");
        }
        KOMEFactionPopulation population = data.getFactionPopulationIfPresent(faction);
        return population == null ? 0L : population.getAvailablePopulationCenti();
    }

    public static boolean trySpendCenti(KOMEWorldData data, String faction, long amountCenti) {
        if (data == null) {
            throw new IllegalArgumentException("World data is required");
        }
        return data.trySpendFactionPopulationCenti(faction, amountCenti);
    }

    public static void grantCenti(KOMEWorldData data, String faction, long amountCenti) {
        if (data == null) {
            throw new IllegalArgumentException("World data is required");
        }
        data.grantFactionPopulationCenti(faction, amountCenti);
    }

    /** Whole-unit compatibility projection for current int packet/UI fields. Fractions are display-only until Checkpoint F. */
    public static int getAvailablePopulation(KOMEWorldData data, String faction) {
        return centiToWholeFloorSaturated(getAvailablePopulationCenti(data, faction));
    }

    /** Whole-unit gameplay compatibility boundary; canonical mutation remains centi-based. */
    public static boolean trySpend(KOMEWorldData data, String faction, int amount) {
        return trySpendCenti(data, faction, wholeToCenti(amount));
    }

    /** Whole-unit payout/admin compatibility boundary; canonical mutation remains centi-based. */
    public static void grant(KOMEWorldData data, String faction, int amount) {
        grantCenti(data, faction, wholeToCenti(amount));
    }

    /** Debits the one canonical faction bank for a new combat hire. */
    public static boolean tryDebitCombatHire(KOMEWorldData data, String faction, int amount) {
        return trySpend(data, faction, amount);
    }

    /** Begins an atomic hire debit. Only this token can roll back its still-uncommitted debit. */
    public static CombatHireDebit beginCombatHireDebit(KOMEWorldData data, String faction, int wholeCost) {
        long amountCenti = wholeToCenti(wholeCost);
        if (!trySpendCenti(data, faction, amountCenti)) {
            return null;
        }
        return new CombatHireDebit(data, KOMEAlliance.normalizeFactionKey(faction), amountCenti);
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

    /** Stewardship keeps native provenance while using the same canonical faction bank. */
    public static void recordStewardshipCombatHirePayment(KOMEHiredUnitRecord record, String nativeFaction) {
        recordCombatHirePayment(record, nativeFaction);
        record.sourceType = KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION;
    }

    /**
     * Active population is informational only. Callers must provide records known
     * to be living: hired records alone do not persist an authoritative liveness flag.
     */
    public static long getActivePopulationCenti(String faction, Collection<KOMEHiredUnitRecord> livingRecords) {
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        if (normalizedFaction.length() == 0 || livingRecords == null) {
            return 0L;
        }
        long totalCenti = 0L;
        for (KOMEHiredUnitRecord record : livingRecords) {
            if (record == null || record.farmhand || !normalizedFaction.equals(populationFaction(record))) {
                continue;
            }
            long investedCenti = wholeToCenti(Math.max(0, record.populationSpent));
            totalCenti = saturatingAdd(totalCenti, investedCenti);
        }
        return totalCenti;
    }

    /** Whole-unit compatibility projection for current packet/UI fields. */
    public static int getActivePopulation(String faction, Collection<KOMEHiredUnitRecord> livingRecords) {
        return centiToWholeFloorSaturated(getActivePopulationCenti(faction, livingRecords));
    }

    public static KOMEPopulationRate getDailyPopulationRate(KOMEWorldData data, String faction) {
        return KOMEPopulationRateService.getDailyPopulationRate(data, faction);
    }
    public static Map<String, KOMEPopulationRate> getAllDailyPopulationRates(KOMEWorldData data) {
        return KOMEPopulationRateService.getAllDailyPopulationRates(data);
    }
    public static List<KOMEPopulationRateContribution> getPopulationRateContributions(KOMEWorldData data) {
        return KOMEPopulationRateService.getPopulationRateContributions(data);
    }

    private static String populationFaction(KOMEHiredUnitRecord record) {
        String owningFaction = KOMEAlliance.normalizeFactionKey(record.populationOwningFaction);
        return owningFaction.length() > 0 ? owningFaction : KOMEAlliance.normalizeFactionKey(record.sourceFaction);
    }

    public static long wholeToCenti(int wholePopulation) {
        if (wholePopulation < 0) {
            throw new IllegalArgumentException("Population must not be negative: " + wholePopulation);
        }
        return Math.multiplyExact((long) wholePopulation, CENTI_PER_POPULATION);
    }

    private static int centiToWholeFloorSaturated(long centi) {
        long whole = centi / CENTI_PER_POPULATION;
        return whole > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) whole;
    }

    private static long saturatingAdd(long left, long right) {
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    public static final class CombatHireDebit {
        private final KOMEWorldData data;
        private final String faction;
        private final long amountCenti;
        private boolean closed;

        private CombatHireDebit(KOMEWorldData data, String faction, long amountCenti) {
            this.data = data;
            this.faction = faction;
            this.amountCenti = amountCenti;
        }

        public long getAmountCenti() {
            return amountCenti;
        }

        public void commit() {
            ensureOpen();
            closed = true;
        }

        public void rollback() {
            ensureOpen();
            data.grantFactionPopulationCenti(faction, amountCenti);
            closed = true;
        }

        private void ensureOpen() {
            if (closed) {
                throw new IllegalStateException("Combat-hire debit transaction is already closed.");
            }
        }
    }
}
