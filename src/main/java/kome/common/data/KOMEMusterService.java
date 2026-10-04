package kome.common.data;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import kome.common.config.KOMEConfigRegistry;

/** Server call/scheduling boundary. Never mutates Available Population or invents conflict state. */
public final class KOMEMusterService {
    private static final SecureRandom SEEDS = new SecureRandom();
    private KOMEMusterService() { }
    public interface RosterSource { List<KOMEMusterRoster.Unit> resolve(String faction); }
    public enum CapitalState { CLEAR, ENCIRCLED, UNKNOWN }
    /** Implemented by the conflict/deployment owner. Delivery must be durable and idempotent by record.key(). */
    public interface ArrivalAuthority {
        CapitalState capitalState(KOMEWorldData data, KOMEFactionCapitalRecord capital);
        String deliver(KOMEWorldData data, KOMEMusterRecord record) throws Exception;
    }
    private static final ArrivalAuthority UNAVAILABLE = new ArrivalAuthority() {
        public CapitalState capitalState(KOMEWorldData data, KOMEFactionCapitalRecord capital) { return CapitalState.UNKNOWN; }
        public String deliver(KOMEWorldData data, KOMEMusterRecord record) {
            throw new IllegalStateException("Conflict/deployment authority is not implemented.");
        }
    };
    public enum Code { SUCCESS, NOT_AUTHORIZED, WRONG_PHASE, ALREADY_USED, NO_CAPITAL, NO_THREAT,
        NO_AFFORDABLE_ROSTER, INVALID_REQUEST, ROSTER_UNAVAILABLE }
    public static final class Result {
        public final Code code;
        public final String reason;
        public final KOMEMusterRecord record;
        Result(Code code, String reason, KOMEMusterRecord record) {
            this.code = code; this.reason = reason; this.record = record;
        }
        public boolean success() { return code == Code.SUCCESS; }
    }
    public static Result call(KOMEWorldData data, String faction, UUID actor, long now, RosterSource source) {
        return call(data, faction, actor, now, source, SEEDS.nextLong());
    }
    static Result call(KOMEWorldData data, String faction, UUID actor, long now, RosterSource source, long seed) {
        if (data == null || source == null || actor == null || now < 0)
            return denied(Code.INVALID_REQUEST, "A server world, player, clock and native roster source are required.");
        synchronized (data) {
            data.ensureWritable();
            String key = KOMEAlliance.normalizeFactionKey(faction);
            if (!KOMERulerAuthorization.canActAsRuler(data, key, actor))
                return denied(Code.NOT_AUTHORIZED, "Only the recognized faction ruler may call muster.");
            if (!data.warSeason.isPopulationPayoutEnabled())
                return denied(Code.WRONG_PHASE, "Muster may be called only during War or Finale.");
            String callKey = key + "|" + data.warSeason.seasonId;
            if (data.civilianMusters.containsKey(callKey))
                return denied(Code.ALREADY_USED, "This faction has already called muster in this season.");
            KOMEGovernanceService.Decision governance = KOMEGovernanceService.militaryAction(data, actor, key);
            if (!governance.allowed) return denied(Code.NOT_AUTHORIZED, governance.reason);
            KOMEFactionCapitalRecord capital = KOMEFactionCapitalService.getCapital(data, key);
            if (capital == null) return denied(Code.NO_CAPITAL, "No authoritative faction capital is available.");
            KOMEConfigRegistry.ValidatedConfig config = KOMEConfigRegistry.currentValidated();
            if (!hasThreat(data, key, capital.getCapitalTileId(), config.getMuster().getThreatDistanceTiles()))
                return denied(Code.NO_THREAT, "No coherent enemy campaign company is within muster distance of the capital.");
            BigInteger rate = KOMEPopulationRateService.getExactDailyPopulationRates(data, config.getPopulation()).get(key);
            if (rate == null) rate = BigInteger.ZERO;
            BigInteger budget = KOMEPopulationService.musterBudgetUnits(rate,
                config.getMuster().getBudgetDailyPopulationMultiplier());
            if (budget.signum() <= 0) return denied(Code.NO_AFFORDABLE_ROSTER, "Daily Population Rate cannot fund a muster unit.");
            KOMEMusterRecord record;
            try {
                KOMEMusterRoster.Selection selection = KOMEMusterRoster.select(key, budget, seed, source.resolve(key));
                if (selection.counts.isEmpty()) return denied(Code.NO_AFFORDABLE_ROSTER,
                    "No eligible native unit fits the exact muster budget; season use was preserved.");
                int delay = config.getMuster().getArrivalDelayHours();
                long due = Math.addExact(now, Math.multiplyExact((long) delay, 3_600_000L));
                record = new KOMEMusterRecord(key, data.warSeason.seasonId, actor, now, due, seed,
                    capital, rate, config.getMuster().getBudgetDailyPopulationMultiplier(), delay, selection);
            } catch (RuntimeException failure) {
                return denied(Code.ROSTER_UNAVAILABLE, "Muster could not be prepared: " + failure.getMessage());
            }
            KOMEAuditEntry audit = new KOMEAuditEntry(now, "MUSTER", "CALL", actor.toString(), record.key(),
                "Civilian muster scheduled", "seed=" + seed + ";rateUnits=" + rate + ";budgetUnits=" + budget
                    + ";spentUnits=" + record.spentUnits + ";capital=" + capital.getCapitalTileId()
                    + ";due=" + record.dueAtMillis + ";roster=" + record.rosterSummary());
            List<KOMEAuditEntry> oldAudit = new ArrayList<KOMEAuditEntry>(data.centralAudit);
            boolean dirty = data.isDirty();
            try {
                data.civilianMusters.put(record.key(), record);
                KOMEAuditService.appendPrepared(data, audit);
                data.markDirty();
            } catch (RuntimeException failure) {
                data.civilianMusters.remove(record.key());
                data.centralAudit.clear(); data.centralAudit.addAll(oldAudit); data.setDirty(dirty);
                throw failure;
            }
            return new Result(Code.SUCCESS, "Muster scheduled for " + record.dueAtMillis
                + "; physical arrival awaits authoritative capital safety confirmation.", record);
        }
    }
    public static List<KOMEMusterRecord> records(KOMEWorldData data, String faction) {
        synchronized (data) {
            String key = KOMEAlliance.normalizeFactionKey(faction);
            List<KOMEMusterRecord> result = new ArrayList<KOMEMusterRecord>();
            for (String id : new java.util.TreeSet<String>(data.civilianMusters.keySet())) {
                KOMEMusterRecord record = data.civilianMusters.get(id);
                if (record.faction.equals(key)) result.add(record);
            }
            return Collections.unmodifiableList(result);
        }
    }
    static boolean hasThreat(KOMEWorldData data, String faction, String capital, int radius) {
        Set<String> reachable = withinDistance(data, capital, radius);
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (company == null || !reachable.contains(KOMEConquestTile.normalizeId(company.currentTile))) continue;
            KOMECompanyCoherenceService.Assessment assessment = KOMECompanyCoherenceService.INSTANCE.assess(data, company);
            if (assessment.status != KOMECompanyCoherenceService.Status.INCOHERENT
                    && KOMECompanyDiplomacyAuthorization.isOpenlyHostile(data, faction, assessment.faction)) return true;
        }
        return false;
    }
    static Set<String> withinDistance(KOMEWorldData data, String origin, int radius) {
        if (radius < 0) throw new IllegalArgumentException("Negative strategic distance.");
        Set<String> seen = new HashSet<String>();
        Set<String> frontier = new HashSet<String>();
        seen.add(origin); frontier.add(origin);
        for (int distance = 0; distance < radius && !frontier.isEmpty(); distance++) {
            Set<String> next = new HashSet<String>();
            for (String tile : frontier) for (String neighbor : data.getRouteNeighbors(tile)) {
                KOMEConquestRouteEdge edge = data.getRouteEdge(tile, neighbor);
                if (edge != null && edge.isPassable() && seen.add(neighbor)) next.add(neighbor);
            }
            frontier = next;
        }
        return seen;
    }
    public static int processDue(KOMEWorldData data, long now) { return processDue(data, now, UNAVAILABLE); }
    public static int processDue(KOMEWorldData data, long now, ArrivalAuthority authority) {
        if (data == null || authority == null || now < 0) throw new IllegalArgumentException("Arrival context required.");
        synchronized (data) {
            data.ensureWritable();
            int delivered = 0;
            for (String id : new java.util.TreeSet<String>(data.civilianMusters.keySet())) {
                KOMEMusterRecord record = data.civilianMusters.get(id);
                if (record.getStatus() == KOMEMusterRecord.Status.ARRIVED || now < record.dueAtMillis) continue;
                // A failed external delivery may have committed effects. Explicit confirmation/retry is required.
                if ("DEPLOYMENT_CONFIRMATION_REQUIRED".equals(record.getPendingReason())) continue;
                String reason = "";
                if (record.seasonId != data.warSeason.seasonId || !data.warSeason.isPopulationPayoutEnabled())
                    reason = "SEASON_POLICY_TBD";
                else {
                    try {
                        CapitalState state = authority.capitalState(data, record.capital);
                        if (state == CapitalState.CLEAR) {
                            // Persist intent before invoking an external factory; reentrant ticks cannot redeliver.
                            record.pending("DEPLOYMENT_CONFIRMATION_REQUIRED");
                            KOMEAuditService.record(data, now, "MUSTER", "DELIVERY_ATTEMPT", "SERVER", id,
                                "Muster delivery requires a durable receipt", "capital=" + record.capital.getCapitalTileId());
                            String receipt = authority.deliver(data, record);
                            record.arrive(now, receipt);
                            data.markDirty();
                            KOMEAuditService.record(data, now, "MUSTER", "ARRIVE", "SERVER", id,
                                "Muster arrival confirmed", "receipt=" + receipt + ";roster=" + record.rosterSummary());
                            delivered++;
                        } else reason = state == CapitalState.ENCIRCLED
                            ? "ENCIRCLED_CAPITAL_POLICY_TBD:" + KOMEConfigRegistry.muster().getEncircledCapitalArrivalPolicy()
                            : "CAPITAL_CONFLICT_STATE_UNKNOWN";
                    } catch (Exception failure) {
                        reason = "DEPLOYMENT_CONFIRMATION_REQUIRED";
                        KOMEAuditService.record(data, now, "MUSTER", "PENDING", "SERVER", id,
                            "Muster delivery could not be confirmed", failure.getClass().getSimpleName()
                                + ": " + failure.getMessage());
                    }
                }
                if (!reason.isEmpty() && record.pending(reason)) {
                    KOMEAuditService.record(data, now, "MUSTER", "PENDING", "SERVER", id,
                        "Scheduled muster retained without deployment", reason);
                }
            }
            return delivered;
        }
    }
    private static Result denied(Code code, String reason) { return new Result(code, reason, null); }

    /** Explicit retry boundary for the deployment owner after reconciling any earlier partial delivery. */
    public static boolean retryConfirmedDelivery(KOMEWorldData data, String callKey) {
        synchronized (data) {
            data.ensureWritable();
            KOMEMusterRecord record = data.civilianMusters.get(callKey);
            if (record == null || !"DEPLOYMENT_CONFIRMATION_REQUIRED".equals(record.getPendingReason())) return false;
            record.pending("CAPITAL_CONFLICT_STATE_UNKNOWN");
            data.markDirty();
            return true;
        }
    }
}
