package kome.common.data;

import java.util.Collection;
import java.util.List;
import java.math.BigInteger;

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

    /** Debits the one canonical faction bank for a new combat hire. */
    public static boolean tryDebitCombatHire(KOMEWorldData data, String faction, int amount) {
        return trySpendCenti(data, faction, wholeToCenti(amount));
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
     * The hired-unit index retains living units including virtual movement/unloaded units.
     * Death, dismissal and completed cleanup remove them from that index. Reading it never
     * performs reconciliation or treats an unloaded unit as dead.
     */
    public static BigInteger getActivePopulationCenti(KOMEWorldData data, String faction) {
        return getExactActivePopulationCenti(faction, livingRecords(data));
    }

    /**
     * Membership in the persisted hired-unit index is the lifecycle authority. Entity loading,
     * server availability and movement flags cannot change this read-only projection.
     * Terminal events remove records; server START resolves invalid movement links.
     */
    public static java.util.List<KOMEHiredUnitRecord> livingRecords(KOMEWorldData data) {
        java.util.List<KOMEHiredUnitRecord> living = new java.util.ArrayList<KOMEHiredUnitRecord>();
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record != null) living.add(record);
        }
        return java.util.Collections.unmodifiableList(living);
    }

    public static long getInvestmentCenti(KOMEHiredUnitRecord record) {
        return record == null || record.farmhand ? 0L : wholeToCenti(record.populationSpent);
    }

    public static BigInteger getCompanyInvestmentCenti(KOMEWorldData data, KOMEArmyCompany company) {
        BigInteger total = BigInteger.ZERO;
        java.util.Set<java.util.UUID> members = new java.util.HashSet<java.util.UUID>(company.units);
        for (KOMEHiredUnitRecord record : livingRecords(data)) {
            if (members.contains(record.entity)) total = total.add(BigInteger.valueOf(getInvestmentCenti(record)));
        }
        return total;
    }

    public static BigInteger getExactActivePopulationCenti(String faction,
            Collection<KOMEHiredUnitRecord> livingRecords) {
        String normalized = KOMEAlliance.normalizeFactionKey(faction);
        BigInteger total = BigInteger.ZERO;
        if (normalized.isEmpty() || livingRecords == null) return total;
        for (KOMEHiredUnitRecord record : livingRecords) {
            if (record != null && !record.farmhand && normalized.equals(populationFaction(record))) {
                total = total.add(BigInteger.valueOf(getInvestmentCenti(record)));
            }
        }
        return total;
    }

    public static BigInteger getRepresentedPopulationCenti(KOMEWorldData data, String faction) {
        String normalized = KOMEAlliance.normalizeFactionKey(faction);
        return getActivePopulationCenti(data, normalized).add(BigInteger.valueOf(normalized.isEmpty()
                ? 0L : getAvailablePopulationCenti(data, normalized)));
    }

    public static List<KOMEPopulationRateContribution> getPopulationRateContributions(KOMEWorldData data) {
        return KOMEPopulationRateService.getPopulationRateContributions(data);
    }

    /** Stable provenance, never the owner's current pledge or a tile's current controller. */
    public static String populationFaction(KOMEHiredUnitRecord record) {
        String owningFaction = KOMEAlliance.normalizeFactionKey(record.populationOwningFaction);
        return owningFaction.length() > 0 ? owningFaction : KOMEAlliance.normalizeFactionKey(record.sourceFaction);
    }

    public static long wholeToCenti(int wholePopulation) {
        if (wholePopulation < 0) {
            throw new IllegalArgumentException("Population must not be negative: " + wholePopulation);
        }
        return Math.multiplyExact((long) wholePopulation, CENTI_PER_POPULATION);
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
