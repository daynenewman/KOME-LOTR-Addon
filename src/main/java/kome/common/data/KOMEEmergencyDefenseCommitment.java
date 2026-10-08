package kome.common.data;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Durable, conflict-scoped native defender authority. It is not a company or reserve bank. */
public final class KOMEEmergencyDefenseCommitment {
    public enum State { DEPLOYING, ACTIVE, ENDING, DEMOBILIZED }
    public enum Disposition { PENDING, ACTIVE, DEAD, SURVIVED_REFUNDED, FAILED_REFUNDED }

    public static final class Defender {
        public final String intentId;
        public final UUID entityUuid;
        public final int populationCost;
        public final long createdAtMillis;
        public final Disposition disposition;
        public final boolean populationRefunded;
        public final String diagnostic;

        public Defender(String intentId, UUID entityUuid, int populationCost,
                long createdAtMillis, Disposition disposition,
                boolean populationRefunded, String diagnostic) {
            if (intentId == null || !intentId.matches("ED-[A-Z0-9]+-[1-9][0-9]*"))
                throw new IllegalArgumentException("Invalid emergency defender intent ID.");
            if (entityUuid == null || populationCost <= 0 || createdAtMillis < 0L
                    || disposition == null)
                throw new IllegalArgumentException("Incomplete emergency defender authority.");
            if (populationRefunded != (disposition == Disposition.SURVIVED_REFUNDED
                    || disposition == Disposition.FAILED_REFUNDED))
                throw new IllegalArgumentException("Emergency defender refund state is inconsistent.");
            this.intentId = intentId;
            this.entityUuid = entityUuid;
            this.populationCost = populationCost;
            this.createdAtMillis = createdAtMillis;
            this.disposition = disposition;
            this.populationRefunded = populationRefunded;
            this.diagnostic = diagnostic == null ? "" : diagnostic;
        }

        public Defender with(Disposition next, boolean refunded, String reason) {
            return new Defender(intentId, entityUuid, populationCost, createdAtMillis,
                next, refunded, reason);
        }
    }

    public final String conflictId;
    public final String tileId;
    public final String nativeFaction;
    public final long attackedTileProductionRate;
    public final long totalFactionProductionRate;
    public final long availablePopulationBasisCenti;
    public final String templateId;
    public final int defenderPopulationCost;
    public final int calculatedUnitCount;
    public final long populationCommittedCenti;
    public final long createdAtMillis;
    public final State state;
    public final Map<String, Defender> defenders;
    public final String diagnostic;

    public KOMEEmergencyDefenseCommitment(String conflictId, String tileId,
            String nativeFaction, long tileRate, long totalRate,
            long availableBasisCenti, String templateId, int defenderCost,
            int unitCount, long committedCenti, long createdAtMillis,
            State state, Map<String, Defender> defenders, String diagnostic) {
        if (conflictId == null || !conflictId.matches("CF[1-9][0-9]*"))
            throw new IllegalArgumentException("Invalid emergency-defense conflict ID.");
        String tile = KOMEConquestTile.normalizeId(tileId);
        String faction = KOMEAlliance.normalizeFactionKey(nativeFaction);
        if (!KOMEConquestTile.isCanonicalTileId(tile) || faction.length() == 0
                || tileRate < 0L || totalRate < 0L || availableBasisCenti < 0L
                || templateId == null || templateId.trim().length() == 0
                || defenderCost <= 0 || unitCount < 0 || committedCenti < 0L
                || createdAtMillis < 0L || state == null || defenders == null)
            throw new IllegalArgumentException("Invalid emergency-defense commitment.");
        long expected = Math.multiplyExact(Math.multiplyExact((long) unitCount,
            (long) defenderCost), KOMEPopulationService.CENTI_PER_POPULATION);
        if (expected != committedCenti || defenders.size() != unitCount)
            throw new IllegalArgumentException("Emergency-defense whole-unit commitment mismatch.");
        Map<String, Defender> copy = new LinkedHashMap<String, Defender>();
        boolean hasPending = false;
        boolean hasRefunded = false;
        boolean allFinal = true;
        for (Map.Entry<String, Defender> entry : defenders.entrySet()) {
            Defender defender = entry.getValue();
            if (defender == null || !entry.getKey().equals(defender.intentId)
                    || defender.populationCost != defenderCost
                    || copy.put(entry.getKey(), defender) != null)
                throw new IllegalArgumentException("Invalid emergency defender registry.");
            hasPending |= defender.disposition == Disposition.PENDING;
            hasRefunded |= defender.populationRefunded;
            allFinal &= defender.disposition == Disposition.DEAD
                || defender.populationRefunded;
        }
        if ((state == State.DEPLOYING || state == State.ACTIVE) && hasRefunded)
            throw new IllegalArgumentException(
                "Active Emergency Defense cannot contain refunded defenders.");
        if (state == State.ACTIVE && hasPending)
            throw new IllegalArgumentException(
                "Active Emergency Defense must have a fully materialized cohort.");
        if (state == State.DEMOBILIZED && !allFinal)
            throw new IllegalArgumentException(
                "Demobilized Emergency Defense must have final population dispositions.");
        this.conflictId = conflictId;
        this.tileId = tile;
        this.nativeFaction = faction;
        attackedTileProductionRate = tileRate;
        totalFactionProductionRate = totalRate;
        availablePopulationBasisCenti = availableBasisCenti;
        this.templateId = templateId.trim();
        defenderPopulationCost = defenderCost;
        calculatedUnitCount = unitCount;
        populationCommittedCenti = committedCenti;
        this.createdAtMillis = createdAtMillis;
        this.state = state;
        this.defenders = Collections.unmodifiableMap(copy);
        this.diagnostic = diagnostic == null ? "" : diagnostic;
    }

    public long refundableCenti() {
        long result = 0L;
        for (Defender defender : defenders.values())
            if (!defender.populationRefunded && defender.disposition != Disposition.DEAD)
                result = Math.addExact(result, KOMEPopulationService.wholeToCenti(
                    defender.populationCost));
        return result;
    }

    public int count(Disposition disposition) {
        int result = 0;
        for (Defender defender : defenders.values())
            if (defender.disposition == disposition) result++;
        return result;
    }

    public KOMEEmergencyDefenseCommitment withDefender(Defender defender, State nextState,
            String reason) {
        Map<String, Defender> next = new LinkedHashMap<String, Defender>(defenders);
        if (!next.containsKey(defender.intentId))
            throw new IllegalArgumentException("Unknown emergency defender intent.");
        next.put(defender.intentId, defender);
        return new KOMEEmergencyDefenseCommitment(conflictId, tileId, nativeFaction,
            attackedTileProductionRate, totalFactionProductionRate,
            availablePopulationBasisCenti, templateId, defenderPopulationCost,
            calculatedUnitCount, populationCommittedCenti, createdAtMillis,
            nextState, next, reason);
    }

    public KOMEEmergencyDefenseCommitment withState(State nextState, String reason) {
        return new KOMEEmergencyDefenseCommitment(conflictId, tileId, nativeFaction,
            attackedTileProductionRate, totalFactionProductionRate,
            availablePopulationBasisCenti, templateId, defenderPopulationCost,
            calculatedUnitCount, populationCommittedCenti, createdAtMillis,
            nextState, defenders, reason);
    }

    public static int allocationUnits(long availableCenti, BigInteger tileRate,
            BigInteger totalRate, int populationCost) {
        if (availableCenti < 0L || tileRate == null || totalRate == null
                || tileRate.signum() < 0 || totalRate.signum() < 0 || populationCost <= 0)
            throw new IllegalArgumentException("Invalid emergency-defense allocation inputs.");
        if (tileRate.signum() == 0 || totalRate.signum() == 0) return 0;
        BigInteger denominator = totalRate.multiply(BigInteger.valueOf(
            KOMEPopulationService.wholeToCenti(populationCost)));
        BigInteger units = BigInteger.valueOf(availableCenti).multiply(tileRate)
            .divide(denominator);
        return units.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0
            ? Integer.MAX_VALUE : units.intValue();
    }
}
