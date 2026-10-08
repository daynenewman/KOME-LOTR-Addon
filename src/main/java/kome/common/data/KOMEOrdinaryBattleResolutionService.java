package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;

/**
 * Ordinary conquest preflight and publication. This service creates no parallel ownership or
 * conflict authority: it composes the Phase-1 resolver, KOMEWorldData ownership, canonical war
 * capture history, and the KOM-47 conflict/movement ending transaction.
 */
public final class KOMEOrdinaryBattleResolutionService {
    public static final String CAPTURE_METHOD_PREFIX = "ORDINARY_CONFLICT_";
    public static final KOMEOrdinaryBattleResolutionService INSTANCE =
        new KOMEOrdinaryBattleResolutionService(new EvaluationSource() {
            public KOMECombatStateEvaluator.Result evaluate(KOMEWorldData data,
                    KOMEConflictRecord conflict) {
                return new KOMECombatStateEvaluator().evaluate(data, conflict);
            }
        }, new FortificationSource() {
            public KOMEFortificationContextResolver.Decision evaluate(KOMEWorldData data,
                    String tileId) {
                return KOMEFortificationContextResolver.evaluate(data, tileId);
            }
        }, new NotificationSink() {
            public void resolved(Plan plan, KOMEConflictRecord ended) {
                String change = plan.ownershipChanges
                    ? " Ownership: " + display(plan.currentOwner) + " -> "
                        + display(plan.winnerFactionId) + "."
                    : plan.captureHistoryAlreadyRecorded
                        ? " Ownership changed to " + display(plan.winnerFactionId) + "."
                    : " Ownership remains with " + display(plan.winnerFactionId) + ".";
                KOMENotificationService.timeSensitive("Battle " + ended.getConflictId()
                    + " on " + ended.getTileId() + " resolved." + change);
            }
        }, PublicationObserver.NONE, ResolutionFinalizer.NONE);

    public enum Code {
        READY,
        NO_CONFLICT,
        ALREADY_RESOLVED,
        NOT_ORDINARY,
        WAITING_EPISODE_CESSATION,
        WAITING_RESPONSE_WINDOW,
        HOSTILITY_ACTIVE,
        DEFERRED_UNKNOWN_AUTHORITY,
        DEFERRED_FORTIFIED,
        DEFERRED_NO_WINNER,
        CAPTURE_HISTORY_CONTRADICTION,
        MOVEMENT_HANDOFF_CONTRADICTION,
        STALE_RETRY,
        RESOLVED_NO_TRANSFER,
        RESOLVED_OWNERSHIP_CHANGED,
        RECONCILIATION_PENDING,
        PUBLICATION_REJECTED
    }

    public static final class Plan {
        public final Code code;
        public final String conflictId;
        public final long expectedConflictRevision;
        public final String tileId;
        public final String currentOwner;
        public final String defaultOwner;
        public final String winnerFactionId;
        public final boolean ownershipChanges;
        public final boolean responseTimerComplete;
        public final KOMECombatStateEvaluator.Code militaryCode;
        public final boolean hostilePairPresent;
        public final KOMEFortificationContextResolver.Status fortificationStatus;
        public final Map<String, List<String>> survivingCampaignCommitments;
        public final List<String> movementHoldOrderIds;
        public final boolean captureHistoryRequired;
        public final boolean captureHistoryAlreadyRecorded;
        public final String captureMethod;
        public final String reason;

        private Plan(Code code, KOMEConflictRecord conflict, KOMEConquestTile tile,
                String winner, KOMECombatStateEvaluator.Result military,
                KOMEFortificationContextResolver.Decision fortification,
                Map<String, List<String>> survivors, List<String> holds,
                boolean historyRequired, boolean historyRecorded, String reason) {
            this.code = code;
            conflictId = conflict == null ? "" : conflict.getConflictId();
            expectedConflictRevision = conflict == null ? -1L : conflict.getRevision();
            tileId = conflict == null ? tile == null ? "" : tile.id : conflict.getTileId();
            currentOwner = tile == null ? "" : KOMEAlliance.normalizeFactionKey(
                tile.projectRulingFaction());
            defaultOwner = tile == null ? "" : KOMEAlliance.normalizeFactionKey(
                tile.defaultRulingFaction);
            winnerFactionId = KOMEAlliance.normalizeFactionKey(winner);
            ownershipChanges = winnerFactionId.length() > 0
                && !winnerFactionId.equals(currentOwner);
            responseTimerComplete = conflict != null
                && conflict.getResponseTimer().status == TimerStatus.COMPLETE;
            militaryCode = military == null ? KOMECombatStateEvaluator.Code.INVALID_CONFLICT
                : military.code;
            hostilePairPresent = military != null && military.hostilePairPresent;
            fortificationStatus = fortification == null
                ? KOMEFortificationContextResolver.Status.AMBIGUOUS
                : fortification.status;
            survivingCampaignCommitments = immutableMap(survivors);
            movementHoldOrderIds = immutable(holds);
            captureHistoryRequired = historyRequired;
            captureHistoryAlreadyRecorded = historyRecorded;
            captureMethod = conflict == null ? "" : captureMethod(conflict.getConflictId());
            this.reason = reason == null ? "" : reason;
        }

        public boolean ready() { return code == Code.READY; }
    }

    public static final class Result {
        public final Code code;
        public final Plan plan;
        public final KOMEConflictRecord endedConflict;
        public final int movementHoldsReleased;
        public final String reason;

        private Result(Code code, Plan plan, KOMEConflictRecord ended,
                int released, String reason) {
            this.code = code;
            this.plan = plan;
            endedConflict = ended;
            movementHoldsReleased = released;
            this.reason = reason == null ? "" : reason;
        }

        public boolean resolved() {
            return code == Code.RESOLVED_NO_TRANSFER
                || code == Code.RESOLVED_OWNERSHIP_CHANGED;
        }
    }

    interface EvaluationSource {
        KOMECombatStateEvaluator.Result evaluate(KOMEWorldData data,
            KOMEConflictRecord conflict);
    }

    interface ObjectiveSource {
        KOMEConflictLifecycleService.ObjectiveContest inspect(KOMEWorldData data,
            KOMEConflictRecord conflict);
    }

    interface FortificationSource {
        KOMEFortificationContextResolver.Decision evaluate(KOMEWorldData data,
            String tileId);
    }

    interface NotificationSink {
        void resolved(Plan plan, KOMEConflictRecord ended);
    }

    /** Test/fault-injection boundary. Production does not mutate between these stages. */
    interface PublicationObserver {
        PublicationObserver NONE = new PublicationObserver() {
            public void beforeFinalValidation(KOMEWorldData data, Plan plan) { }
            public void afterCaptureHistory(KOMEWorldData data, Plan plan) { }
            public void afterOwnership(KOMEWorldData data, Plan plan) { }
        };
        void beforeFinalValidation(KOMEWorldData data, Plan plan);
        void afterCaptureHistory(KOMEWorldData data, Plan plan);
        void afterOwnership(KOMEWorldData data, Plan plan);
    }

    /**
     * Deliberate Phase-4 seam. It is read-only/no-op until a durable once-only recovery ledger
     * exists; recovery must eventually complete here before ConflictRecord closure.
     */
    interface ResolutionFinalizer {
        ResolutionFinalizer NONE = new ResolutionFinalizer() {
            public void prepare(KOMEWorldData data, Plan plan) { }
        };
        void prepare(KOMEWorldData data, Plan plan);
    }

    private static final class HoldInspection {
        final List<String> orderIds;
        final String error;
        HoldInspection(List<String> ids, String error) {
            orderIds = ids;
            this.error = error;
        }
    }

    private static final class CaptureInspection {
        final boolean recorded;
        final String error;
        CaptureInspection(boolean recorded, String error) {
            this.recorded = recorded;
            this.error = error;
        }
    }

    private final EvaluationSource evaluationSource;
    private final ObjectiveSource objectiveSource;
    private final FortificationSource fortificationSource;
    private final NotificationSink notificationSink;
    private final PublicationObserver publicationObserver;
    private final ResolutionFinalizer finalizer;

    KOMEOrdinaryBattleResolutionService(EvaluationSource evaluation,
            FortificationSource fortification, NotificationSink notifications,
            PublicationObserver observer, ResolutionFinalizer finalizer) {
        this(evaluation, new ObjectiveSource() {
            public KOMEConflictLifecycleService.ObjectiveContest inspect(KOMEWorldData data,
                    KOMEConflictRecord conflict) {
                return KOMEConflictLifecycleService.INSTANCE.inspectOrdinaryObjective(data, conflict);
            }
        }, fortification, notifications, observer, finalizer);
    }

    KOMEOrdinaryBattleResolutionService(EvaluationSource evaluation, ObjectiveSource objective,
            FortificationSource fortification, NotificationSink notifications,
            PublicationObserver observer, ResolutionFinalizer finalizer) {
        if (evaluation == null || fortification == null || notifications == null
                || objective == null || observer == null || finalizer == null)
            throw new IllegalArgumentException("Ordinary resolution dependencies are required.");
        evaluationSource = evaluation;
        objectiveSource = objective;
        fortificationSource = fortification;
        notificationSink = notifications;
        publicationObserver = observer;
        this.finalizer = finalizer;
    }

    public List<Result> processAll(KOMEWorldData data, long worldTime,
            long nowMillis) {
        if (data == null) throw new IllegalArgumentException("World data is required.");
        List<String> tiles = new ArrayList<String>(data.getConflictService().records().keySet());
        Collections.sort(tiles);
        List<Result> results = new ArrayList<Result>();
        for (String tile : tiles) {
            KOMEConflictRecord conflict = data.getConflictService().get(tile);
            if (conflict != null && conflict.isActive()
                    && conflict.getState() == State.ORDINARY
                    && conflict.getCombatEpisode().sequence > 0L)
                results.add(resolve(data, tile, worldTime, nowMillis));
        }
        return Collections.unmodifiableList(results);
    }

    public Result resolve(KOMEWorldData data, String tileId, long worldTime,
            long nowMillis) {
        Plan plan = preflight(data, tileId);
        return plan.ready() ? publish(data, plan, worldTime, nowMillis)
            : new Result(plan.code, plan, null, 0, plan.reason);
    }

    public Plan preflight(KOMEWorldData data, String tileId) {
        if (data == null) throw new IllegalArgumentException("World data is required.");
        KOMEConflictRecord conflict = data.getConflictService().get(tileId);
        KOMEConquestTile tile = data.getConquestTileIfPresent(tileId);
        if (conflict == null)
            return plan(Code.NO_CONFLICT, null, tile, "", null, null,
                emptySurvivors(), Collections.<String>emptyList(), false, false,
                "No ConflictRecord exists for this tile.");
        if (!conflict.isActive())
            return plan(Code.ALREADY_RESOLVED, conflict, tile, "", null, null,
                emptySurvivors(), Collections.<String>emptyList(), false, false,
                "ConflictRecord is already ended.");
        if (conflict.getState() != State.ORDINARY)
            return plan(Code.DEFERRED_FORTIFIED, conflict, tile, "", null,
                KOMEFortificationContextResolver.Decision.fortified(
                    "Encirclement requires fortified resolution."), emptySurvivors(),
                Collections.<String>emptyList(), false, false,
                "Encirclement cannot use ordinary conquest resolution.");
        if (tile == null)
            return plan(Code.DEFERRED_UNKNOWN_AUTHORITY, conflict, null, "", null,
                null, emptySurvivors(), Collections.<String>emptyList(), false,
                false, "Canonical conquest tile authority is missing.");
        // Objective response episodes remain ACTIVE while an offensive commitment contests
        // the ruler. COMPLETE timing and absence of a hostile military pair permit resolution.
        if (conflict.getCombatEpisode().sequence == 0L
                || conflict.getResponseTimer().status != TimerStatus.COMPLETE)
            return plan(Code.WAITING_RESPONSE_WINDOW, conflict, tile, "", null,
                null, emptySurvivors(), Collections.<String>emptyList(), false,
                false, "The response timer is not COMPLETE.");

        KOMEFortificationContextResolver.Decision fortification;
        KOMECombatStateEvaluator.Result military;
        try {
            fortification = fortificationSource.evaluate(data, conflict.getTileId());
        } catch (RuntimeException failure) {
            fortification = KOMEFortificationContextResolver.Decision.ambiguous(
                "Defensive context evaluation failed.");
        }
        if (fortification == null
                || fortification.status != KOMEFortificationContextResolver.Status.CLEAR)
            return plan(Code.DEFERRED_FORTIFIED, conflict, tile, "", null,
                fortification, emptySurvivors(), Collections.<String>emptyList(),
                false, false, fortification == null
                    ? "Defensive context is unavailable." : fortification.reason);
        try {
            military = evaluationSource.evaluate(data, conflict);
        } catch (RuntimeException failure) {
            military = null;
        }
        Map<String, List<String>> survivors = survivors(military);
        if (military == null
                || military.code != KOMECombatStateEvaluator.Code.RESOLVED
                || military.unknownAuthorityPresent)
            return plan(Code.DEFERRED_UNKNOWN_AUTHORITY, conflict, tile, "",
                military, fortification, survivors, Collections.<String>emptyList(),
                false, false, "Military presence or diplomacy is unresolved.");
        if (military.hostilePairPresent)
            return plan(Code.HOSTILITY_ACTIVE, conflict, tile, "", military,
                fortification, survivors, Collections.<String>emptyList(), false,
                false, "Hostile committed military factions remain.");

        KOMEOrdinaryBattleOwnershipResolver.Plan ownership =
            KOMEOrdinaryBattleOwnershipResolver.resolve(conflict,
                tile.defaultRulingFaction, true, military, fortification);
        if (!ownership.hasWinner())
            return plan(ownership.code == KOMEOrdinaryBattleOwnershipResolver.Code.DEFERRED_FORTIFIED
                    || ownership.code == KOMEOrdinaryBattleOwnershipResolver.Code.DEFERRED_ENCIRCLEMENT
                    ? Code.DEFERRED_FORTIFIED
                    : ownership.code == KOMEOrdinaryBattleOwnershipResolver.Code.HOSTILE_CONTEST_ACTIVE
                        ? Code.HOSTILITY_ACTIVE
                        : ownership.code == KOMEOrdinaryBattleOwnershipResolver.Code.TIMING_INCOMPLETE
                            ? Code.WAITING_RESPONSE_WINDOW
                            : ownership.code == KOMEOrdinaryBattleOwnershipResolver.Code.DEFERRED_UNKNOWN_AUTHORITY
                                ? Code.DEFERRED_UNKNOWN_AUTHORITY : Code.DEFERRED_NO_WINNER,
                conflict, tile, "", military, fortification, survivors,
                Collections.<String>emptyList(), false, false, ownership.reason);

        HoldInspection holds = inspectHolds(data, conflict);
        if (holds.error.length() > 0)
            return plan(Code.MOVEMENT_HANDOFF_CONTRADICTION, conflict, tile,
                ownership.winnerFactionId, military, fortification, survivors,
                holds.orderIds, false, false, holds.error);

        String method = captureMethod(conflict.getConflictId());
        String current = KOMEAlliance.normalizeFactionKey(tile.projectRulingFaction());
        CaptureInspection capture = inspectCaptureHistory(data, method,
            conflict.getTileId(), current, ownership.winnerFactionId);
        if (capture.error.length() > 0)
            return plan(Code.CAPTURE_HISTORY_CONTRADICTION, conflict, tile,
                ownership.winnerFactionId, military, fortification, survivors,
                holds.orderIds, false, false, capture.error);
        boolean recorded = capture.recorded;
        boolean changes = !ownership.winnerFactionId.equals(current);
        // A coalition selection may transfer administration between two surviving allies.
        // That is an ownership change, but it is not a hostile capture and must not fabricate
        // a war between those allies. An absent former owner's loss is the hostile conquest.
        boolean history = changes && current.length() > 0
            && !survivors.containsKey(current) && !recorded;
        if (history) {
            // An old COMPLETE timer is not permission to conquer a ruler that is no longer
            // hostile. Reuse the abandonment/continuity predicate for a new hostile capture.
            // Existing capture receipts and allied administration selection retain their rules.
            KOMEConflictLifecycleService.ObjectiveContest objective = objectiveSource.inspect(data, conflict);
            if (!objective.objectiveContested)
                return plan(objective.status == KOMEConflictLifecycleService.ObjectiveContestStatus.UNKNOWN
                        ? Code.DEFERRED_UNKNOWN_AUTHORITY : Code.DEFERRED_NO_WINNER,
                    conflict, tile, "", military, fortification, survivors, holds.orderIds,
                    false, false, objective.reason);
        }
        return plan(Code.READY, conflict, tile, ownership.winnerFactionId,
            military, fortification, survivors, holds.orderIds, history,
            recorded, "Ordinary conquest resolution is ready for publication.");
    }

    public Result publish(KOMEWorldData data, Plan expected, long worldTime,
            long nowMillis) {
        if (data == null || expected == null || !expected.ready()
                || worldTime < 0L || nowMillis < 0L)
            return new Result(Code.PUBLICATION_REJECTED, expected, null, 0,
                "A ready plan and nonnegative clocks are required.");
        data.ensureWritable();
        publicationObserver.beforeFinalValidation(data, expected);
        Plan current = preflight(data, expected.tileId);
        if (!sameAuthority(expected, current))
            return new Result(Code.STALE_RETRY, current, null, 0,
                "Conflict, ownership, military, fortification, or movement authority changed since preflight.");

        boolean historyPublished = false;
        boolean ownershipPublished = false;
        try {
            if (current.captureHistoryRequired) {
                KOMEWar war = KOMEWarService.recordHostileCapture(data,
                    current.tileId, current.currentOwner, current.winnerFactionId,
                    null, "Ordinary battle", nowMillis, current.captureMethod);
                if (war == null)
                    return new Result(Code.PUBLICATION_REJECTED, current, null, 0,
                        "Canonical hostile-capture history could not be created.");
                historyPublished = true;
                publicationObserver.afterCaptureHistory(data, current);
            }
            if (current.ownershipChanges) {
                KOMEConquestTile tile = data.getConquestTileIfPresent(current.tileId);
                if (tile == null || !current.currentOwner.equals(
                        KOMEAlliance.normalizeFactionKey(tile.projectRulingFaction())))
                    return new Result(historyPublished ? Code.RECONCILIATION_PENDING
                        : Code.STALE_RETRY, current, null, 0,
                        "Tile ownership changed during publication.");
                data.claimTile(tile, current.winnerFactionId, worldTime, null,
                    "Ordinary battle");
                // Capture history is the durable reconciliation receipt and is intentionally
                // written first. Revalidate again after ownership publication so movement and
                // progression observe the final canonical owner, matching the normal claim path.
                KOMEMovementAccessService.revalidateAll(data, nowMillis);
                KOMEAllianceProgressionService.scanQualifyingWarDeployments(data,
                    nowMillis);
                ownershipPublished = true;
                publicationObserver.afterOwnership(data, current);
            }

            // Phase 4 installs only a durable, replay-safe recovery implementation here.
            finalizer.prepare(data, current);
            KOMEConflictRecord live = data.getConflictService().get(current.tileId);
            if (live == null || !live.isActive()
                    || !current.conflictId.equals(live.getConflictId())
                    || current.expectedConflictRevision != live.getRevision())
                return new Result(historyPublished || ownershipPublished
                    ? Code.RECONCILIATION_PENDING : Code.STALE_RETRY,
                    current, null, 0,
                    "Conflict changed before final closure; a later tick must reconcile.");
            KOMEConflictService.EndResult ended = data.getConflictService()
                .endWithMovementHandoff(data, current.tileId,
                    ExpectedConflict.at(current.conflictId,
                        current.expectedConflictRevision),
                    new Context(nowMillis, "ordinary-battle",
                        "ordinary conquest resolved for " + current.winnerFactionId),
                    KOMEConflictService.EndSource.LIFECYCLE);
            if (!ended.isSuccess())
                return new Result(historyPublished || ownershipPublished
                    ? Code.RECONCILIATION_PENDING
                    : ended.conflictResult.code == KOMEConflictContracts.Code.STALE_REVISION
                        || ended.conflictResult.code == KOMEConflictContracts.Code.STALE_CONFLICT_ID
                            ? Code.STALE_RETRY : Code.PUBLICATION_REJECTED,
                    current, null, 0, ended.conflictResult.code + ": "
                        + ended.conflictResult.reason);

            KOMEAuditService.record(data, nowMillis, "CONFLICT", "ORDINARY_RESOLUTION",
                "ordinary-battle", ended.conflictResult.record.getConflictId(),
                "Ordinary battle resolved", "tile=" + current.tileId
                    + ";winner=" + current.winnerFactionId
                    + ";ownershipChanged=" + (current.ownershipChanges
                        || current.captureHistoryAlreadyRecorded)
                    + ";releasedHolds=" + ended.movementHoldsReleased);
            data.syncConquestTiles();
            notificationSink.resolved(current, ended.conflictResult.record);
            return new Result(current.ownershipChanges
                    || current.captureHistoryAlreadyRecorded
                        ? Code.RESOLVED_OWNERSHIP_CHANGED
                        : Code.RESOLVED_NO_TRANSFER,
                current, ended.conflictResult.record,
                ended.movementHoldsReleased, "Ordinary conquest resolution published.");
        } catch (RuntimeException failure) {
            return new Result(historyPublished || ownershipPublished
                ? Code.RECONCILIATION_PENDING : Code.PUBLICATION_REJECTED,
                current, null, 0, "Resolution publication failed"
                    + (failure.getMessage() == null ? "." : ": " + failure.getMessage()));
        }
    }

    private static HoldInspection inspectHolds(KOMEWorldData data,
            KOMEConflictRecord conflict) {
        List<String> ids = new ArrayList<String>();
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order == null || !KOMEArmyMovementOrder.CONFLICT_HELD.equals(order.status))
                continue;
            String hold = clean(order.conflictHoldId);
            boolean related = conflict.getTileId().equals(
                    KOMEConquestTile.normalizeId(order.currentTile))
                || conflict.getCommitments().containsKey(clean(order.companyId))
                || conflict.getConflictId().equals(hold);
            if (!related) continue;
            if (!conflict.getConflictId().equals(hold))
                return new HoldInspection(immutable(ids),
                    "A related movement order claims another conflict hold.");
            String issue = KOMEConflictMovementHoldValidator.validate(data, conflict,
                order, KOMEConflictMovementHoldValidator.LinkPolicy.REQUIRE_COMPLETE);
            if (issue.length() > 0)
                return new HoldInspection(immutable(ids), issue);
            ids.add(order.id);
        }
        Collections.sort(ids);
        return new HoldInspection(immutable(ids), "");
    }

    private static Map<String, List<String>> survivors(
            KOMECombatStateEvaluator.Result military) {
        Map<String, List<String>> result = new TreeMap<String, List<String>>();
        if (military != null) for (KOMECombatStateEvaluator.MilitaryFaction faction
                : military.factions.values())
            result.put(faction.factionId,
                new ArrayList<String>(faction.campaignDetachmentIds));
        return result;
    }

    private static CaptureInspection inspectCaptureHistory(KOMEWorldData data,
            String method, String tileId, String currentOwner, String winner) {
        KOMEWar.TileCaptureEvent found = null;
        for (KOMEWar war : data.wars.values()) {
            if (war == null) continue;
            for (KOMEWar.TileCaptureEvent event : war.tileCaptureHistory)
                if (method.equals(event.claimMethod)) {
                    if (found != null)
                        return new CaptureInspection(false,
                            "Multiple hostile-capture receipts claim this conflict.");
                    found = event;
                }
        }
        if (found == null) return new CaptureInspection(false, "");
        String former = KOMEAlliance.normalizeFactionKey(found.formerOwner);
        String next = KOMEAlliance.normalizeFactionKey(found.newOwner);
        String current = KOMEAlliance.normalizeFactionKey(currentOwner);
        String selected = KOMEAlliance.normalizeFactionKey(winner);
        if (!KOMEConquestTile.normalizeId(tileId).equals(found.tileId)
                || !selected.equals(next) || former.length() == 0
                || former.equals(next)
                || !current.equals(selected) && !current.equals(former))
            return new CaptureInspection(false,
                "Hostile-capture receipt identity contradicts current resolution authority.");
        return new CaptureInspection(true, "");
    }

    private static String captureMethod(String conflictId) {
        return CAPTURE_METHOD_PREFIX + conflictId;
    }

    /**
     * Narrow reservation for a receipt-bearing Phase-3 publication. Even a contradictory
     * receipt must reach preflight's existing diagnostics rather than be hidden by abandonment.
     * Receipt-free ownership transfers still require Phase-4B durable resolution intent.
     */
    static boolean hasCapturePublication(KOMEWorldData data, KOMEConflictRecord conflict) {
        String method = captureMethod(conflict.getConflictId());
        for (KOMEWar war : data.wars.values()) {
            if (war == null) continue;
            for (KOMEWar.TileCaptureEvent event : war.tileCaptureHistory)
                if (method.equals(event.claimMethod)) return true;
        }
        return false;
    }

    private static boolean sameAuthority(Plan expected, Plan current) {
        return current != null && current.ready()
            && expected.conflictId.equals(current.conflictId)
            && expected.expectedConflictRevision == current.expectedConflictRevision
            && expected.tileId.equals(current.tileId)
            && expected.currentOwner.equals(current.currentOwner)
            && expected.defaultOwner.equals(current.defaultOwner)
            && expected.winnerFactionId.equals(current.winnerFactionId)
            && expected.responseTimerComplete == current.responseTimerComplete
            && expected.militaryCode == current.militaryCode
            && expected.hostilePairPresent == current.hostilePairPresent
            && expected.fortificationStatus == current.fortificationStatus
            && expected.survivingCampaignCommitments.equals(
                current.survivingCampaignCommitments)
            && expected.movementHoldOrderIds.equals(current.movementHoldOrderIds)
            && expected.captureHistoryRequired == current.captureHistoryRequired
            && expected.captureHistoryAlreadyRecorded
                == current.captureHistoryAlreadyRecorded;
    }

    private static Plan plan(Code code, KOMEConflictRecord conflict,
            KOMEConquestTile tile, String winner,
            KOMECombatStateEvaluator.Result military,
            KOMEFortificationContextResolver.Decision fortification,
            Map<String, List<String>> survivors, List<String> holds,
            boolean historyRequired, boolean historyRecorded, String reason) {
        return new Plan(code, conflict, tile, winner, military, fortification,
            survivors, holds, historyRequired, historyRecorded, reason);
    }

    private static Map<String, List<String>> emptySurvivors() {
        return Collections.emptyMap();
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    private static Map<String, List<String>> immutableMap(
            Map<String, List<String>> values) {
        Map<String, List<String>> result = new LinkedHashMap<String, List<String>>();
        if (values != null) for (Map.Entry<String, List<String>> entry
                : new TreeMap<String, List<String>>(values).entrySet())
            result.put(entry.getKey(), immutable(entry.getValue()));
        return Collections.unmodifiableMap(result);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String display(String faction) {
        String normalized = KOMEAlliance.normalizeFactionKey(faction);
        return normalized.length() == 0 ? "unclaimed"
            : KOMEAlliance.displayFactionName(normalized);
    }
}
