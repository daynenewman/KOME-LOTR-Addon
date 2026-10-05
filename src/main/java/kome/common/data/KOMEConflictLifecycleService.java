package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;

/** Read-only readiness/inspection plus narrowly proven derived-link repair for KOM-17. */
public final class KOMEConflictLifecycleService {
    public static final KOMEConflictLifecycleService INSTANCE =
        new KOMEConflictLifecycleService();

    private KOMEConflictLifecycleService() { }

    public static final class HostilePair {
        public final String firstFactionId;
        public final String secondFactionId;
        public final Hostility hostility;

        private HostilePair(String first, String second, Hostility hostility) {
            firstFactionId = first;
            secondFactionId = second;
            this.hostility = hostility;
        }

        @Override public String toString() {
            return firstFactionId + "/" + secondFactionId + "=" + hostility;
        }
    }

    public static final class Diagnostics {
        public final String tileId;
        public final KOMEConflictRecord record;
        public final List<String> activeFactionIds;
        public final List<HostilePair> factionPairs;
        public final List<String> unresolvedCommitments;
        public final List<String> movementHolds;
        public final boolean hostilePairPresent;
        public final boolean unknownHostilityPresent;
        public final boolean confidentlyNoHostilePair;

        private Diagnostics(String tile, KOMEConflictRecord record,
                List<String> factions, List<HostilePair> pairs,
                List<String> unresolved, List<String> holds) {
            tileId = tile;
            this.record = record;
            activeFactionIds = immutable(factions);
            factionPairs = immutable(pairs);
            unresolvedCommitments = immutable(unresolved);
            movementHolds = immutable(holds);
            boolean hostile = false;
            boolean unknown = false;
            for (HostilePair pair : pairs) {
                hostile |= pair.hostility == Hostility.HOSTILE;
                unknown |= pair.hostility == Hostility.UNKNOWN;
            }
            hostilePairPresent = hostile;
            unknownHostilityPresent = unknown;
            confidentlyNoHostilePair = record != null && record.isActive()
                && !hostile && !unknown && unresolved.isEmpty();
        }
    }

    private enum RepairKind {
        LINK_COMPANY_ORDER, LINK_UNIT_ORDER, RELEASE_ENDED_HOLD
    }

    private static final class RepairAction {
        final RepairKind kind;
        final String orderId;
        final String companyId;
        final UUID unitId;
        final String description;

        RepairAction(RepairKind kind, String orderId, String companyId,
                UUID unitId, String description) {
            this.kind = kind;
            this.orderId = orderId;
            this.companyId = companyId;
            this.unitId = unitId;
            this.description = description;
        }
    }

    public static final class RepairPlan {
        public final String tileId;
        public final String conflictId;
        public final List<String> actions;
        public final List<String> unresolved;
        private final List<RepairAction> executable;

        private RepairPlan(String tile, String conflictId,
                List<RepairAction> actions, List<String> unresolved) {
            tileId = tile;
            this.conflictId = conflictId;
            executable = immutable(actions);
            List<String> descriptions = new ArrayList<String>();
            for (RepairAction action : actions) descriptions.add(action.description);
            this.actions = immutable(descriptions);
            this.unresolved = immutable(unresolved);
        }

        public boolean hasChanges() { return !actions.isEmpty(); }
    }

    public static final class RepairResult {
        public final RepairPlan plan;
        public final int changesApplied;

        private RepairResult(RepairPlan plan, int changes) {
            this.plan = plan;
            changesApplied = changes;
        }
    }

    public Diagnostics diagnose(KOMEWorldData data, String tileId) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        KOMEConflictRecord record = data == null ? null
            : data.getConflictService().get(tile);
        List<String> factions = new ArrayList<String>();
        List<HostilePair> pairs = new ArrayList<HostilePair>();
        List<String> unresolved = new ArrayList<String>();
        List<String> holds = new ArrayList<String>();
        if (record != null) {
            for (FactionParticipation participation
                    : record.getFactionParticipation().values())
                if (participation.isActive()) factions.add(participation.factionId);
            Collections.sort(factions);
            for (int i = 0; i < factions.size(); i++) {
                for (int j = i + 1; j < factions.size(); j++) {
                    Hostility hostility = data.getConflictService().currentHostility(
                        data, factions.get(i), factions.get(j));
                    pairs.add(new HostilePair(factions.get(i), factions.get(j), hostility));
                }
            }
            if (record.isActive()) {
                for (String detachmentId : record.getCommitments().keySet()) {
                    String issue = commitmentIssue(data, record, detachmentId);
                    if (issue.length() > 0) unresolved.add(detachmentId + ": " + issue);
                }
            }
        }
        if (data != null) {
            for (KOMEArmyMovementOrder order : sortedOrders(data)) {
                if (tile.equals(KOMEConquestTile.normalizeId(order.currentTile))
                        && (KOMEArmyMovementOrder.CONFLICT_HELD.equals(order.status)
                            || KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED.equals(order.status)))
                    holds.add(order.id + "=" + order.status
                        + (order.conflictHoldId == null || order.conflictHoldId.isEmpty()
                            ? "" : ":" + order.conflictHoldId));
            }
        }
        return new Diagnostics(tile, record, factions, pairs, unresolved, holds);
    }

    public List<String> inspectionLines(KOMEWorldData data, String tileId) {
        Diagnostics diagnostic = diagnose(data, tileId);
        List<String> lines = new ArrayList<String>();
        KOMEConflictRecord record = diagnostic.record;
        if (record == null) {
            lines.add("Conflict " + diagnostic.tileId + ": none.");
            return lines;
        }
        lines.add("Conflict " + record.getConflictId() + " tile=" + record.getTileId()
            + " state=" + record.getState() + " revision=" + record.getRevision() + ".");
        lines.add("Created=" + record.getCreatedAtMillis() + "; ended="
            + (record.getEndedAtMillis() == null ? "-" : record.getEndedAtMillis())
            + "; last=" + record.getLastTransition().operation + " by "
            + record.getLastTransition().actor + " reason="
            + record.getLastTransition().reason + ".");
        for (Commitment commitment : record.getCommitments().values())
            lines.add("Commitment " + commitment.detachmentId + " origin="
                + commitment.origin + " faction=" + commitmentFaction(record, commitment)
                + " arrival=" + commitment.acceptedAtMillis + " order="
                + emptyAsDash(commitment.movementOrderId) + ".");
        for (FactionParticipation faction : record.getFactionParticipation().values())
            lines.add("Faction " + faction.factionId + " continuity="
                + faction.continuitySequence + " start=" + faction.startedAtMillis
                + " end=" + (faction.endedAtMillis == null ? "-" : faction.endedAtMillis) + ".");
        for (PlayerParticipation player : record.getPlayers().values())
            lines.add("Player " + player.playerId + " faction="
                + player.factionAtRegistration + " status=" + player.status
                + " start=" + player.startedAtMillis + " withdrawn="
                + (player.withdrawnAtMillis == null ? "-" : player.withdrawnAtMillis) + ".");
        for (GarrisonCohort cohort : record.getOriginalGarrison().values())
            lines.add("Original garrison " + cohort.detachmentId + " members="
                + cohort.members.size() + " unresolvedOrLiving="
                + cohort.unconfirmedTerminalMembers() + ".");
        lines.add("Timers response=" + timer(record.getResponseTimer())
            + "; capture=" + timer(record.getCaptureTimer()) + ".");
        lines.add("Combat episode=" + record.getCombatEpisode().episodeId + " state="
            + record.getCombatEpisode().state + " sequence="
            + record.getCombatEpisode().sequence + ".");
        if (record.getEncirclement() != null)
            lines.add("Encirclement start=" + record.getEncirclement().startedAtMillis
                + " checkpoint=" + record.getEncirclement().checkpointAtMillis
                + " elapsed=" + record.getEncirclement().liveElapsedMillis
                + " end=" + (record.getEncirclement().endedAtMillis == null
                    ? "-" : record.getEncirclement().endedAtMillis) + ".");
        lines.add("Siege catalog=" + record.getComplexCatalog().availability
            + " required=" + record.getComplexCatalog().requiredComplexIds + ".");
        for (ComplexSubstate complex : record.getComplexes().values())
            lines.add("Siege Complex " + complex.complexId + " state=" + complex.state
                + " assault=" + emptyAsDash(complex.activeAssaultId)
                + " checkpoints=" + complex.progress.securedSegmentIds
                + " lead=" + (complex.lead == null ? "-"
                    : complex.lead.factionId + "/" + complex.lead.actorId) + ".");
        lines.add("Live pairs=" + diagnostic.factionPairs + "; hostilePresent="
            + diagnostic.hostilePairPresent + "; unknown="
            + diagnostic.unknownHostilityPresent + "; confidentlyNoHostilePair="
            + diagnostic.confidentlyNoHostilePair + ".");
        lines.add("Movement holds=" + diagnostic.movementHolds + "; unresolved="
            + diagnostic.unresolvedCommitments + ".");
        for (ReferenceDiagnostic persisted : record.getDiagnostics().values())
            lines.add("Diagnostic " + persisted.kind + ":" + persisted.referenceId
                + " status=" + persisted.status + " reason=" + persisted.reason + ".");
        return lines;
    }

    public RepairPlan previewRepair(KOMEWorldData data, String tileId) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        KOMEConflictRecord conflict = data == null ? null
            : data.getConflictService().get(tile);
        List<RepairAction> actions = new ArrayList<RepairAction>();
        List<String> unresolved = new ArrayList<String>();
        if (data == null || conflict == null)
            return new RepairPlan(tile, "", actions,
                Collections.singletonList("No current ConflictRecord exists for this tile."));
        for (KOMEArmyMovementOrder order : sortedOrders(data)) {
            if (!tile.equals(KOMEConquestTile.normalizeId(order.currentTile))
                    || !KOMEArmyMovementOrder.CONFLICT_HELD.equals(order.status)) continue;
            if (!conflict.getConflictId().equals(trim(order.conflictHoldId))
                    || !conflict.getCommitments().containsKey(order.companyId)) {
                unresolved.add("Order " + order.id
                    + " has ambiguous conflict-hold authority; no repair inferred.");
                continue;
            }
            String validation = KOMEConflictMovementHoldValidator.validate(data, conflict,
                order, KOMEConflictMovementHoldValidator.LinkPolicy.ALLOW_MISSING_EXPECTED);
            if (validation.length() > 0) {
                unresolved.add("Order " + order.id + " is unsafe to repair: "
                    + validation);
                continue;
            }
            KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
            if (company == null || !tile.equals(KOMEConquestTile.normalizeId(company.currentTile))
                    || !new LinkedHashSet<UUID>(company.units).equals(
                        new LinkedHashSet<UUID>(order.units))) {
                unresolved.add("Order " + order.id
                    + " lacks an unambiguous matching detachment/cohort.");
                continue;
            }
            List<RepairAction> orderActions = new ArrayList<RepairAction>();
            if (trim(company.movementOrderId).isEmpty())
                orderActions.add(new RepairAction(RepairKind.LINK_COMPANY_ORDER,
                    order.id, company.id, null, "Restore detachment " + company.id
                        + " movement link to " + order.id + "."));
            else if (!order.id.equals(company.movementOrderId)) {
                unresolved.add("Detachment " + company.id
                    + " points to another movement order; no repair inferred.");
                continue;
            }
            boolean ambiguousUnit = false;
            for (UUID unitId : order.units) {
                KOMEHiredUnitRecord unit = data.hiredUnits.get(unitId);
                if (unit == null || !company.id.equals(unit.companyId)
                        || !tile.equals(KOMEConquestTile.normalizeId(unit.currentTile))) {
                    unresolved.add("Unit " + unitId
                        + " is missing or strategically incoherent; no repair inferred.");
                    ambiguousUnit = true;
                } else if (trim(unit.movementOrderId).isEmpty()) {
                    orderActions.add(new RepairAction(RepairKind.LINK_UNIT_ORDER,
                        order.id, company.id, unitId, "Restore unit " + unitId
                            + " movement link to " + order.id + "."));
                } else if (!order.id.equals(unit.movementOrderId)) {
                    unresolved.add("Unit " + unitId
                        + " points to another movement order; no repair inferred.");
                    ambiguousUnit = true;
                }
            }
            if (ambiguousUnit) continue;
            actions.addAll(orderActions);
            if (!conflict.isActive())
                actions.add(new RepairAction(RepairKind.RELEASE_ENDED_HOLD,
                    order.id, order.companyId, null,
                    "Release ended-conflict hold on order " + order.id
                        + " into post-conflict pause."));
        }
        return new RepairPlan(tile, conflict.getConflictId(), actions, unresolved);
    }

    public RepairResult applyRepair(KOMEWorldData data, String tileId,
            long timestampMillis, String actor) {
        RepairPlan plan = previewRepair(data, tileId);
        if (data == null || !plan.hasChanges()) return new RepairResult(plan, 0);
        data.ensureWritable();
        int changes = 0;
        for (RepairAction action : plan.executable) {
            KOMEArmyMovementOrder order = data.armyMovements.get(action.orderId);
            if (order == null) continue;
            if (action.kind == RepairKind.LINK_COMPANY_ORDER) {
                KOMEArmyCompany company = data.armyCompanies.get(action.companyId);
                if (company != null && trim(company.movementOrderId).isEmpty()) {
                    company.movementOrderId = order.id;
                    company.status = KOMEArmyCompany.STATIONED;
                    company.updatedAtMillis = timestampMillis;
                    changes++;
                }
            } else if (action.kind == RepairKind.LINK_UNIT_ORDER) {
                KOMEHiredUnitRecord unit = data.hiredUnits.get(action.unitId);
                if (unit != null && trim(unit.movementOrderId).isEmpty()) {
                    unit.movementOrderId = order.id;
                    changes++;
                }
            } else if (action.kind == RepairKind.RELEASE_ENDED_HOLD
                    && KOMEArmyMovementOrder.CONFLICT_HELD.equals(order.status)) {
                releaseEndedHold(order, plan.conflictId);
                data.updateMovementHistory(order,
                    KOMEMovementHistoryRecord.CONFLICT_RELEASED_PAUSED);
                changes++;
            }
        }
        if (changes > 0) {
            data.markDirty();
            KOMEAuditService.record(data, timestampMillis, "CONFLICT", "REPAIR",
                actor, plan.conflictId, "Applied deterministic conflict repair",
                "tile=" + plan.tileId + ";changes=" + changes);
        }
        return new RepairResult(plan, changes);
    }

    static void releaseEndedHold(KOMEArmyMovementOrder order, String conflictId) {
        order.status = KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED;
        order.conflictHoldId = "";
        order.conflictHeldAtMillis = 0L;
        order.arrivalMillis = 0L;
        order.nextStepAvailableMillis = 0L;
        order.nextStepDepartureMillis = 0L;
        order.spawnRetryPaused = false;
        order.pendingSpawnReason = "Conflict " + conflictId
            + " ended; route remains paused pending explicit future movement policy.";
    }

    private static String commitmentIssue(KOMEWorldData data,
            KOMEConflictRecord conflict, String detachmentId) {
        KOMEArmyCompany company = data.armyCompanies.get(detachmentId);
        if (company == null) return "Campaign Detachment is missing (not inferred dead).";
        if (!conflict.getTileId().equals(KOMEConquestTile.normalizeId(company.currentTile)))
            return "Strategic tile disagrees with the conflict tile.";
        if (company.units.isEmpty()) return "Campaign Detachment cohort is unresolved/empty.";
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord unit = data.hiredUnits.get(unitId);
            if (unit == null) return "A member reference is missing (not inferred dead).";
            if (!KOMEHiredUnitClassification.isCampaignUnit(unit))
                return "An ORDINARY member appears in a strategic commitment.";
        }
        KOMECompanyCoherenceService.Assessment coherence =
            KOMECompanyCoherenceService.INSTANCE.assess(data, company);
        return coherence.status == KOMECompanyCoherenceService.Status.INCOHERENT
            ? "Campaign Detachment is strategically incoherent." : "";
    }

    private static String commitmentFaction(KOMEConflictRecord record,
            Commitment commitment) {
        if (commitment.validatedEvent != null)
            return commitment.validatedEvent.detachmentFactionId;
        for (Commitment candidate : record.getCommitments().values()) {
            if (candidate.validatedEvent != null) {
                String faction = candidate.validatedEvent.originalGarrisonFactions
                    .get(commitment.detachmentId);
                if (faction != null) return faction;
            }
        }
        return "unknown";
    }

    private static String timer(TimerSlot timer) {
        return timer.status + " elapsed=" + timer.elapsedMillis + "/"
            + timer.durationSnapshotMillis + " episode=" + emptyAsDash(timer.episodeId);
    }

    private static List<KOMEArmyMovementOrder> sortedOrders(KOMEWorldData data) {
        List<KOMEArmyMovementOrder> orders =
            new ArrayList<KOMEArmyMovementOrder>(data.armyMovements.values());
        Collections.sort(orders, new Comparator<KOMEArmyMovementOrder>() {
            @Override public int compare(KOMEArmyMovementOrder left,
                    KOMEArmyMovementOrder right) {
                return trim(left == null ? "" : left.id).compareTo(
                    trim(right == null ? "" : right.id));
            }
        });
        List<KOMEArmyMovementOrder> nonnull = new ArrayList<KOMEArmyMovementOrder>();
        for (KOMEArmyMovementOrder order : orders) if (order != null) nonnull.add(order);
        return nonnull;
    }

    private static String emptyAsDash(String value) {
        String clean = trim(value);
        return clean.isEmpty() ? "-" : clean;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
}
