package kome.common.command;

import kome.common.KOMEReflection;
import kome.common.data.*;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * Server-thread application boundary for strategic company orders. No packets or presentation.
 * Decisions are extracted from the troop command; physical staging, departure/debit and arrival
 * remain in its existing runtime. Evaluation never repairs records or grants movement credit.
 * Arrival validation retains the existing world/chunk availability checks (it may load chunks).
 * Evaluation includes BFS, member/coherence and conflict checks, and per-step arrival validation.
 * Destination-wide callers must bound/rate-limit requests; this boundary adds no lifecycle caches.
 */
public final class KOMECompanyMovementService {
    public static final KOMECompanyMovementService INSTANCE =
        new KOMECompanyMovementService(new KOMECommandTroops(), System::currentTimeMillis);
    private final KOMECommandTroops runtime;
    private final LongSupplier clock;
    private static final String INVALID_ACTOR_REASON = "A server player and authoritative world are required.";

    KOMECompanyMovementService(KOMECommandTroops runtime, LongSupplier clock) {
        this.runtime = Objects.requireNonNull(runtime);
        this.clock = Objects.requireNonNull(clock);
    }

    public enum Status { BLOCKED, CAN_START_AND_ARRIVE_NOW, CAN_START_BUT_CONTINUES_LATER }
    public enum ScheduleMode { DAILY, TIMED_OVERRIDE }
    /** Expected arrival consequence only; downstream live arrival authority still owns commitment. */
    public enum Consequence { NONE, EXPECTED_CREATE_CONFLICT, EXPECTED_JOIN_CONFLICT }
    public enum FailureStage { NONE, VALIDATION, ROUTE, ARRIVAL, ALLOWANCE, SUBMISSION }

    public static final class Denial {
        public final String code, reason;
        public final FailureStage stage;
        private Denial(String code, String reason, FailureStage stage) {
            this.code = code; this.reason = reason; this.stage = stage;
        }
    }
    public static final class Blocker {
        public final String fromTile, toTile, code, reason;
        public final int priority;
        private Blocker(RouteBlocker blocker) {
            fromTile = blocker.fromTile; toTile = blocker.toTile;
            code = blocker.code; reason = blocker.reason; priority = blocker.priority;
        }
    }
    public static final class Arrival {
        public final int dimension;
        public final double x, y, z;
        public final String source;
        private Arrival(SpawnTarget target) {
            dimension = target.dimensionId; x = target.x; y = target.y; z = target.z;
            source = target.label;
        }
    }
    /** Detached immutable snapshot; never exposes companies, member records, orders or conflicts. */
    public static final class Evaluation {
        public final String companyId, companyName, currentTile, destinationTile;
        public final int unitCount, population, mountedPopulation, groundPopulation;
        public final int tilesPerDay, remainingAllowance, distanceTiles;
        public final Status status;
        public final ScheduleMode scheduleMode;
        public final boolean canStartNow, canArriveNow, exhausted, legalRoute, activelyCommitted;
        public final List<String> routeTiles, edgeNotes;
        public final List<Blocker> blockers;
        /** Canonical daily timing, also retained for unchanged legacy packet presentation. */
        public final KOMEMovementRoutePreview.Timing dailyTiming;
        public final long evaluatedAtMillis;
        public final Arrival arrival;
        public final Denial denial;
        public final Consequence consequence;
        public final String heldConflictId, heldConflictTile, destinationConflictId, previewFingerprint;
        public final long heldConflictRevision, destinationConflictRevision;
        /** Rich service guard; previewFingerprint remains the unchanged legacy packet token. */
        public final Confirmation confirmation;

        private Evaluation(KOMEWorldData data, KOMEArmyCompany company, String id, String destination,
                RouteResult route, KOMEMovementRoutePreview.Timing timing, SpawnTarget arrival,
                Denial denial, long now, String fingerprint) {
            companyId = id; companyName = company == null ? "" : company.name;
            currentTile = company == null ? "" : company.currentTile; destinationTile = destination;
            unitCount = company == null ? 0 : company.units.size();
            population = company == null ? 0 : company.totalPopulation;
            mountedPopulation = company == null ? 0 : company.mountedPopulation;
            groundPopulation = company == null ? 0 : company.groundPopulation;
            tilesPerDay = company == null ? 0 : company.getTilesPerDay();
            remainingAllowance = company == null ? 0 : Math.max(0, Math.min(company.movementAllowance, tilesPerDay));
            routeTiles = immutable(route == null ? Collections.<String>emptyList() : route.routeTiles);
            edgeNotes = immutable(route == null ? Collections.<String>emptyList() : route.edgeNotes);
            List<Blocker> details = new ArrayList<Blocker>();
            if (route != null) for (RouteBlocker blocker : route.blockers) details.add(new Blocker(blocker));
            blockers = immutable(details); legalRoute = route != null && route.valid;
            distanceTiles = route == null ? 0 : route.distance();
            scheduleMode = isDaily(data) ? ScheduleMode.DAILY : ScheduleMode.TIMED_OVERRIDE;
            exhausted = company != null && remainingAllowance == 0;
            canStartNow = denial == null;
            canArriveNow = canStartNow && (isDaily(data) ? distanceTiles <= remainingAllowance : distanceTiles <= 1);
            status = !canStartNow ? Status.BLOCKED : canArriveNow ? Status.CAN_START_AND_ARRIVE_NOW
                : Status.CAN_START_BUT_CONTINUES_LATER;
            dailyTiming = timing; this.arrival = arrival == null ? null : new Arrival(arrival);
            this.denial = denial; evaluatedAtMillis = now; previewFingerprint = fingerprint;
            KOMEConflictRecord held = company == null ? null
                : KOMEConflictMovementService.activeConflictForDetachment(data, company.id);
            activelyCommitted = company != null && KOMEConflictMovementService.isActivelyCommitted(data, company.id);
            heldConflictId = held == null ? "" : held.getConflictId();
            heldConflictTile = held == null ? "" : held.getTileId();
            heldConflictRevision = held == null ? -1L : held.getRevision();
            DestinationConsequence target = destinationConsequence(data, company, destination);
            destinationConflictId = target.conflictId; destinationConflictRevision = target.revision;
            consequence = target.consequence;
            confirmation = denial == null ? confirmation(data, company, destination, fingerprint, target) : null;
        }
        public boolean allowed() { return denial == null; }
        // A fresh mutable copy is used only by the legacy command's presentation adapter.
        RouteResult routeForPresentation() {
            RouteResult copy = new RouteResult(); copy.valid = legalRoute;
            copy.routeTiles.addAll(routeTiles); copy.edgeNotes.addAll(edgeNotes);
            if (denial != null) { copy.failureCode = denial.code; copy.failureReason = denial.reason; }
            for (Blocker b : blockers) copy.blockers.add(new RouteBlocker(b.fromTile, b.toTile, b.reason, b.priority, b.code));
            return copy;
        }
    }
    /** Opaque, detached service confirmation; this is stale-state evidence, never authorization. */
    public static final class Confirmation {
        public final String companyId, destinationTile, legacyFingerprint, serviceFingerprint;
        private Confirmation(String companyId, String destinationTile, String legacyFingerprint, String serviceFingerprint) {
            this.companyId = companyId; this.destinationTile = destinationTile;
            this.legacyFingerprint = legacyFingerprint; this.serviceFingerprint = serviceFingerprint;
        }
    }
    private static final class DestinationConsequence {
        final String conflictId; final long revision; final Consequence consequence;
        DestinationConsequence(String id, long revision, Consequence consequence) {
            conflictId = id; this.revision = revision; this.consequence = consequence;
        }
    }
    private static DestinationConsequence destinationConsequence(KOMEWorldData data, KOMEArmyCompany company, String destination) {
        KOMEConflictRecord target = company == null ? null : data.getConflictService().get(destination);
        boolean active = target != null && target.isActive();
        boolean hostile = company != null && KOMEConflictMovementService
            .evaluateHostileDestination(data, company, destination, true).allowed();
        return new DestinationConsequence(active ? target.getConflictId() : "", active ? target.getRevision() : -1L,
            !hostile ? Consequence.NONE : active ? Consequence.EXPECTED_JOIN_CONFLICT : Consequence.EXPECTED_CREATE_CONFLICT);
    }
    private static Confirmation confirmation(KOMEWorldData data, KOMEArmyCompany company, String destination,
            String legacyFingerprint, DestinationConsequence target) {
        StringBuilder snapshot = new StringBuilder();
        appendGuard(snapshot, "KOM84-1"); appendGuard(snapshot, company.id); appendGuard(snapshot, destination);
        appendGuard(snapshot, legacyFingerprint); appendGuard(snapshot, company.owner);
        appendGuard(snapshot, company.faction); appendGuard(snapshot, company.nativeFaction);
        appendGuard(snapshot, company.controllerAuthority); appendGuard(snapshot, company.temporaryController);
        List<String> members = new ArrayList<String>();
        for (UUID id : company.units) members.add(String.valueOf(id));
        Collections.sort(members); appendGuard(snapshot, members.size());
        for (String id : members) appendGuard(snapshot, id);
        appendGuard(snapshot, isDaily(data) ? ScheduleMode.DAILY : ScheduleMode.TIMED_OVERRIDE);
        appendGuard(snapshot, data.movementSecondsPerTileOverride); appendGuard(snapshot, data.movementTotalSecondsOverride);
        appendGuard(snapshot, company.getTilesPerDay()); appendGuard(snapshot, KOMEMovementDayService.schedule().signature());
        appendGuard(snapshot, target.consequence); appendGuard(snapshot, target.conflictId); appendGuard(snapshot, target.revision);
        // Exclude observation time, global dirty state and unrelated conflicts/companies.
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(snapshot.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return new Confirmation(company.id, destination, legacyFingerprint, hex.toString());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable", impossible); }
    }
    private static void appendGuard(StringBuilder out, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        out.append(text.length()).append(':').append(text).append('|');
    }
    static void requireCurrentConfirmation(KOMEWorldData data, KOMEArmyCompany company, String destination,
            String currentLegacyFingerprint, Confirmation expected) {
        if (expected == null) return; // Existing CLI/packet keeps its established legacy semantics.
        Confirmation current = confirmation(data, company, destination, currentLegacyFingerprint,
            destinationConsequence(data, company, destination));
        if (!Objects.equals(expected.serviceFingerprint, current.serviceFingerprint))
            throw new MovementDenied("STALE_PREVIEW", "Movement preview is stale; preview the route again before confirming.");
    }
    public static final class Submission {
        public final boolean accepted;
        public final String companyId, orderId, orderStatus, currentTile;
        public final int completedSteps, distanceTiles;
        public final List<String> routeTiles;
        public final Denial denial;
        private Submission(String companyId, KOMEArmyMovementOrder order, Denial denial) {
            accepted = order != null; this.companyId = companyId; this.denial = denial;
            orderId = order == null ? "" : order.id; orderStatus = order == null ? "" : order.status;
            currentTile = order == null ? "" : order.currentTile;
            completedSteps = order == null ? 0 : order.completedSteps;
            distanceTiles = order == null ? 0 : order.distanceTiles;
            routeTiles = immutable(order == null ? Collections.<String>emptyList() : order.routeTiles);
        }
    }
    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }
    private static boolean isDaily(KOMEWorldData data) {
        return data == null || data.movementSecondsPerTileOverride <= 0 && data.movementTotalSecondsOverride <= 0;
    }
    public Evaluation evaluate(EntityPlayerMP actor, String companyId, String destination) {
        return evaluate(serverDataFor(actor), actor, companyId, destination);
    }
    /** Explicit authoritative world data supports server callers and deterministic domain tests. */
    public Evaluation evaluate(KOMEWorldData data, EntityPlayerMP actor, String companyId, String destination) {
        String id = companyId == null ? "" : companyId;
        String tile = KOMEConquestTile.normalizeId(destination);
        long now = 0L; KOMEArmyCompany company = null; RouteResult route = null;
        KOMEMovementRoutePreview.Timing timing = null; SpawnTarget arrival = null;
        FailureStage stage = FailureStage.VALIDATION;
        try {
            requireActor(data, actor);
            if (!KOMEConquestTile.isCanonicalTileId(tile))
                throw new MovementDenied("INVALID_DESTINATION", "Invalid conquest tile: " + destination);
            company = validateCompanyMove(actor, data, KOMEReflection.getEntityUUID(actor), id, tile);
            stage = FailureStage.ROUTE;
            route = findLegalRoute(data, company.currentTile, tile, company.faction, company);
            validateStewardshipRoute(data, company, route);
            if (!route.valid) return new Evaluation(data, company, id, tile, route, null, null,
                new Denial(route.failureCode, route.failureReason, stage), clock.getAsLong(), "");
            stage = FailureStage.ARRIVAL;
            SpawnTarget routeArrival = KOMECommandTroops.validateRouteArrivalTargets(data, KOMEReflection.getWorld(actor), route);
            if (!routeArrival.valid) throw new MovementDenied("ARRIVAL_UNAVAILABLE", routeArrival.failureReason);
            arrival = KOMECommandTroops.requireArrivalTarget(data, KOMEReflection.getWorld(actor), tile);
            if (!arrival.valid) throw new MovementDenied("ARRIVAL_UNAVAILABLE", arrival.failureReason);
            // Match the legacy preview: sample timing after route and chunk/arrival validation.
            now = clock.getAsLong();
            timing = KOMEMovementRoutePreview.timing(company, route.distance(), now);
            stage = FailureStage.ALLOWANCE;
            if (isDaily(data) && timing.exhausted)
                throw new MovementDenied("NO_ALLOWANCE", "This company has no movement allowance remaining for the current movement day.");
            String fingerprint = KOMECommandTroops.previewToken(data, company, tile, route, arrival);
            return new Evaluation(data, company, id, tile, route, timing, arrival, null, now, fingerprint);
        } catch (WrongUsageException denied) {
            // Only reveal a known company's details to a currently authorized controller.
            KOMEArmyCompany found = data == null ? null : data.armyCompanies.get(id);
            if (found != null && validActor(actor) && canPlayerControlCompany(data, actor, found)) company = found;
            return new Evaluation(data, company, id, tile, route, timing, arrival,
                new Denial(denialCode(denied), denied.getMessage(), stage), timing == null ? clock.getAsLong() : now, "");
        }
    }
    /** Compatibility entry point for the legacy fingerprint; richer confirmations use submitConfirmed. */
    public Submission submit(EntityPlayerMP actor, String companyId, String destination, String expectedFingerprint) {
        return submit(serverDataFor(actor), actor, companyId, destination, expectedFingerprint);
    }
    public Submission submit(KOMEWorldData data, EntityPlayerMP actor, String companyId, String destination,
            Evaluation expected) {
        if (data == null || !validActor(actor)) return rejected(companyId, "INVALID_ACTOR", INVALID_ACTOR_REASON);
        if (expected == null || !Objects.equals(companyId, expected.companyId)
                || !KOMEConquestTile.normalizeId(destination).equals(expected.destinationTile))
            return rejected(companyId, "STALE_PREVIEW", "Movement preview is stale; preview the route again before confirming.");
        return submitConfirmed(data, actor, companyId, destination, expected.confirmation);
    }
    /** Legacy token compatibility for the existing packet/command adapter. New UI uses submitConfirmed. */
    public Submission submit(KOMEWorldData data, EntityPlayerMP actor, String companyId, String destination,
            String expectedFingerprint) {
        if (data == null || !validActor(actor)) return rejected(companyId, "INVALID_ACTOR", INVALID_ACTOR_REASON);
        if (expectedFingerprint == null || expectedFingerprint.isEmpty())
            return rejected(companyId, "STALE_PREVIEW", "Movement confirmation has expired; preview the route again.");
        return execute(data, actor, companyId, destination, expectedFingerprint, null);
    }
    // Preserve the existing CLI's explicit no-preview form; new callers use submit with a guard.
    Submission submitUnpreviewed(KOMEWorldData data, EntityPlayerMP actor, String companyId, String destination) {
        return execute(data, actor, companyId, destination, "", null);
    }
    public Submission submitConfirmed(EntityPlayerMP actor, String companyId, String destination, Confirmation expected) {
        return submitConfirmed(serverDataFor(actor), actor, companyId, destination, expected);
    }
    public Submission submitConfirmed(KOMEWorldData data, EntityPlayerMP actor, String companyId, String destination,
            Confirmation expected) {
        if (data == null || !validActor(actor)) return rejected(companyId, "INVALID_ACTOR", INVALID_ACTOR_REASON);
        if (expected == null || !Objects.equals(companyId, expected.companyId)
                || !KOMEConquestTile.normalizeId(destination).equals(expected.destinationTile))
            return rejected(companyId, "STALE_PREVIEW", "Movement preview is stale; preview the route again before confirming.");
        return execute(data, actor, companyId, destination, expected.legacyFingerprint, expected);
    }
    private Submission execute(KOMEWorldData data, EntityPlayerMP actor, String companyId, String destination,
            String fingerprint, Confirmation expected) {
        try {
            requireActor(data, actor);
            String tile = KOMEConquestTile.normalizeId(destination);
            if (!KOMEConquestTile.isCanonicalTileId(tile))
                throw new MovementDenied("INVALID_DESTINATION", "Invalid conquest tile: " + destination);
            // The runtime repeats validation, route/arrival checks and fingerprint comparison;
            // it never consumes Evaluation or trusts its eligibility/path/allowance.
            return new Submission(companyId, runtime.executeCompanyMove(actor, data,
                KOMEReflection.getEntityUUID(actor), companyId, tile, fingerprint, expected), null);
        } catch (WrongUsageException denied) {
            return rejected(companyId, denialCode(denied), denied.getMessage());
        }
    }
    private static Submission rejected(String id, String code, String reason) {
        return new Submission(id, null, new Denial(code, reason, FailureStage.SUBMISSION));
    }
    private static boolean validActor(EntityPlayerMP actor) {
        return actor != null && actor.worldObj != null && !actor.worldObj.isRemote
            && actor.worldObj.provider != null && KOMEReflection.getEntityUUID(actor) != null;
    }
    private static KOMEWorldData serverDataFor(EntityPlayerMP actor) {
        // Do not touch MapStorage (or create a world-data root) for an invalid caller.
        return validActor(actor) && KOMEReflection.getMapStorage(actor.worldObj) != null
            ? KOMEWorldData.get(actor.worldObj) : null;
    }
    private static void requireActor(KOMEWorldData data, EntityPlayerMP actor) {
        if (data == null || !validActor(actor))
            throw new MovementDenied("INVALID_ACTOR", INVALID_ACTOR_REASON);
    }
    static void requireOrdinaryMovementAllowed(KOMEWorldData data, String companyId, String orderId) {
        String reason = KOMEFormalRetreatAuthority.ordinaryMovementBlockReason(data, companyId, orderId);
        if (!reason.isEmpty()) throw new MovementDenied("FORMAL_RETREAT_BLOCKED", reason);
    }
    static final class MovementDenied extends WrongUsageException {
        final String code;
        MovementDenied(String code, String reason) { super(reason); this.code = code; }
    }
    private static String denialCode(WrongUsageException denied) {
        if (denied instanceof MovementDenied) return ((MovementDenied) denied).code;
        String reason = denied.getMessage().toLowerCase(Locale.ROOT);
        if (reason.contains("stale") || reason.contains("expired")) return "STALE_PREVIEW";
        if (reason.contains("allowance")) return "NO_ALLOWANCE";
        return "MOVEMENT_REJECTED";
    }

    KOMEArmyCompany validateCompanyMove(EntityPlayerMP player, KOMEWorldData data, UUID owner, String companyId, String destination) {
        // Request validation must not repair companies before authorization. Login,
        // canonical lifecycle changes and movement ticks own reconciliation.
        KOMEArmyCompany company = data.armyCompanies.get(companyId);
        if (company == null) {
            throw new MovementDenied("UNKNOWN_COMPANY", "Unknown company " + companyId + ".");
        }
        if (!canPlayerControlCompany(data, player, company)) {
            throw new MovementDenied("UNAUTHORIZED", "You do not own " + company.name + ".");
        }
        String playerFaction = normalizeFaction(getPlayerFaction(data, player));
        String companyFaction = normalizeFaction(company.faction);
        boolean admin = player.canCommandSenderUseCommand(2, "troops");
        boolean temporary = company.isTemporarilyControlledBy(KOMEReflection.getEntityUUID(player));
        if (!admin && !playerFaction.equals(companyFaction) && !temporary) {
            throw new MovementDenied("FACTION_MISMATCH", "Company faction does not match your faction.");
        }
        requireOrdinaryMovementAllowed(data, company.id, null);
        KOMEConflictRecord activeConflict =
            KOMEConflictMovementService.activeConflictForDetachment(data, company.id);
        if (activeConflict != null
                || KOMEConflictMovementService.isActivelyCommitted(data, company.id)) {
            throw new MovementDenied("CONFLICT_HELD", company.name + " is committed to active conflict "
                + (activeConflict == null ? "(ambiguous authority)"
                    : activeConflict.getConflictId() + " at " + activeConflict.getTileId()) + ".");
        }
        // canPlayerControlCompany already checks live stewardship/delegation
        // authorization. Revocation and withdrawal cleanup stay in the lifecycle.
        if (company.isMoving()) {
            throw new MovementDenied("ALREADY_MOVING", company.name + " is already moving.");
        }
        if (company.units.isEmpty()) {
            throw new MovementDenied("EMPTY_COMPANY", company.name + " has no units.");
        }
        String origin = KOMEConquestTile.normalizeId(company.currentTile);
        destination = KOMEConquestTile.normalizeId(destination);
        if (origin.equals(destination)) {
            throw new MovementDenied("INVALID_DESTINATION", "Origin and destination must be different tiles.");
        }
        requireCompanyStandableTile(data, company, origin, "Origin");
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            String blocked = movementBlockReasonForCompanyUnit(record, company);
            if (blocked.length() > 0) {
                throw new MovementDenied("INELIGIBLE_MEMBER", company.name + " cannot move: " + blocked);
            }
        }
        KOMECompanyCoherenceService.Assessment coherence =
            KOMECompanyCoherenceService.INSTANCE.assess(data, company);
        if (coherence.status == KOMECompanyCoherenceService.Status.INCOHERENT) {
            throw new MovementDenied("INCOHERENT_COMPANY", company.name
                + " is strategically incoherent and cannot receive a movement order.");
        }
        return company;
    }

    void validateStewardshipRoute(KOMEWorldData data, KOMEArmyCompany company, RouteResult route) {
        if (company == null || route == null || !KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
            return;
        }
        for (String tileId : route.routeTiles) {
            KOMEConquestTile tile = data.conquestTiles.get(KOMEConquestTile.normalizeId(tileId));
            String owner = tile == null ? "" : normalizeFaction(tile.projectRulingFaction());
            if (!KOMEWartimeStewardshipService.canEnter(data, company, owner, false)) {
                route.valid = false;
                route.failureCode = "PASSAGE_DENIED";
                route.failureReason = "Wartime Stewardship cannot enter " + tileId + " ("
                    + displayFaction(owner) + "). Legal territory is native land, Stage 3 partner passage, or an opposing side of an authorized active war.";
                return;
            }
        }
    }

    String movementBlockReasonForCompanyUnit(KOMEHiredUnitRecord record, KOMEArmyCompany company) {
        if (company == null) {
            return "company data is missing.";
        }
        if (record == null) {
            return "a tracked unit record is missing.";
        }
        String unit = displayUnitId(record);
        if (!KOMEHiredUnitClassification.isCampaignUnit(record)) {
            return unit + " is ordinary; only campaign units can move strategically.";
        }
        if (record.farmhand) {
            return unit + " is a farmhand; farmhands cannot move in companies.";
        }
        if (record.type != KOMEPopulationType.OFFENSIVE) {
            return unit + " is defensive; only offensive units can move.";
        }
        if (!company.id.equals(record.companyId)) {
            return unit + " is assigned to company " + record.companyId + " instead of " + company.id + ".";
        }
        if (company.owner == null || !company.owner.equals(record.owner)) {
            return unit + " is owned by a different player than company " + company.id + ".";
        }
        if (record.isMoving()) {
            return unit + " is already attached to movement order " + record.movementOrderId + ".";
        }
        if (!KOMEConquestTile.normalizeId(company.currentTile).equals(KOMEConquestTile.normalizeId(record.currentTile))) {
            return unit + " is stationed at " + KOMEConquestTile.normalizeId(record.currentTile)
                + " instead of company tile " + KOMEConquestTile.normalizeId(company.currentTile) + ".";
        }
        return "";
    }

    static boolean canUseDelegatedCompanyControl(
            KOMEWorldData data, KOMEArmyCompany company, UUID actor) {
        if (data == null || company == null || actor == null
                || !KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)
                || !company.isTemporarilyControlledBy(actor)) {
            return false;
        }
        return KOMECompanyDiplomacyAuthorization.canContinueDelegation(
            data, KOMEWartimeStewardshipService.nativeFaction(company), actor).allowed;
    }

    boolean canPlayerControlCompany(KOMEWorldData data, EntityPlayerMP player, KOMEArmyCompany company) {
        if (player == null || company == null) {
            return false;
        }
        UUID actor = KOMEReflection.getEntityUUID(player);
        boolean temporary = KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)
            || KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority);
        if (KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)) {
            return canUseDelegatedCompanyControl(data, company, actor);
        }
        if (temporary) return new KOMEAllianceAuthority(data).canControlTemporaryCompany(company, actor).allowed;
        return player.canCommandSenderUseCommand(2, "troops") || actor.equals(company.owner);
    }

    private KOMEConquestTile requireCompanyStandableTile(KOMEWorldData data, KOMEArmyCompany company, String tileId, String role) {
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        KOMEConquestTile tile = data.conquestTiles.get(normalizedTile);
        String owner = tile == null ? "" : normalizeFaction(tile.projectRulingFaction());
        if (company != null && tile != null && owner.length() > 0
                && KOMEWartimeStewardshipService.canEnter(data, company, owner, false)) return tile;
        return requireFactionStandableTile(data, company == null ? "" : company.faction, tileId, role);
    }

    private KOMEConquestTile requireFactionStandableTile(KOMEWorldData data, String factionKey, String tileId, String role) {
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        KOMEConquestTile tile = data.conquestTiles.get(normalizedTile);
        if (tile == null) {
            throw new WrongUsageException(role + " tile " + normalizedTile + " was not found in conquest data.");
        }
        String owner = normalizeFaction(tile.projectRulingFaction());
        String companyFaction = normalizeFaction(factionKey);
        if (owner.length() == 0) {
            throw new WrongUsageException(role + " tile " + normalizedTile + " is not claimed.");
        }
        if (owner.equals(companyFaction) || data.canFactionUseMilitaryPassage(companyFaction, owner)) {
            return tile;
        }
        throw new WrongUsageException(role + " tile " + normalizedTile + " is owned by " + displayFaction(owner)
            + " (" + emptyKey(owner) + "), and " + displayFaction(companyFaction) + " (" + emptyKey(companyFaction)
            + ") has no LOTR Ally passage there.");
    }

    RouteResult findLegalRoute(KOMEWorldData data, String origin, String destination, String factionKey) {
        return findLegalRoute(data, origin, destination, factionKey, null);
    }

    RouteResult findLegalRoute(KOMEWorldData data, String origin, String destination, String factionKey,
            KOMEArmyCompany company) {
        RouteResult result = new RouteResult();
        String start = KOMEConquestTile.normalizeId(origin);
        String goal = KOMEConquestTile.normalizeId(destination);
        String faction = normalizeFaction(factionKey);
        if (faction.length() == 0) {
            KOMEConquestTile originTile = data == null ? null : data.getConquestTileIfPresent(start);
            faction = originTile == null ? "" : normalizeFaction(originTile.projectRulingFaction());
        }
        if (data == null) {
            result.failureCode = "GEOGRAPHY_UNAVAILABLE";
            result.failureReason = "Conquest data is unavailable.";
            return result;
        }
        if (start.length() == 0 || goal.length() == 0) {
            result.failureCode = "INVALID_DESTINATION";
            result.failureReason = "Origin and destination tiles are required.";
            return result;
        }
        if (start.equals(goal)) {
            result.failureCode = "INVALID_DESTINATION";
            result.failureReason = "Origin and destination must be different tiles.";
            return result;
        }
        KOMEConquestTile originTile = data.getConquestTileIfPresent(start);
        if (originTile == null) {
            result.failureCode = "UNKNOWN_GEOGRAPHY";
            result.failureReason = "Origin tile " + start + " was not found in conquest data.";
            return result;
        }
        String originOwner = normalizeFaction(originTile.projectRulingFaction());
        if (originOwner.length() == 0) {
            result.failureCode = "PASSAGE_DENIED";
            result.failureReason = "Origin tile " + start + " is not claimed.";
            return result;
        }
        if (!originOwner.equals(faction) && !data.canFactionUseMilitaryPassage(faction, originOwner)
                && (company == null || !KOMEWartimeStewardshipService.canEnter(data, company, originOwner, false))) {
            result.failureCode = "PASSAGE_DENIED";
            result.failureReason = "Origin tile " + start + " is owned by " + displayFaction(originOwner)
                + " (" + emptyKey(originOwner) + "), but moving faction is " + displayFaction(faction)
                + " (" + emptyKey(faction) + ") and has no LOTR Ally passage there.";
            return result;
        }
        String destinationBlock = routeTileBlockReason(data, goal, faction, true, company);
        if (destinationBlock != null) {
            result.failureCode = "PASSAGE_DENIED";
            result.failureReason = destinationBlock;
            return result;
        }

        Queue<String> queue = new LinkedList<String>();
        Set<String> visited = new HashSet<String>();
        Map<String, String> previous = new HashMap<String, String>();
        queue.add(start);
        visited.add(start);
        result.visitedTiles.add(start);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            List<String> neighbors = new ArrayList<String>(data.getRouteNeighbors(current));
            sortRouteNeighborsForGoal(data, current, neighbors, goal);
            for (String neighbor : neighbors) {
                String next = KOMEConquestTile.normalizeId(neighbor);
                if (next.length() == 0 || visited.contains(next)) {
                    continue;
                }
                KOMEConquestRouteEdge edge = data.getRouteEdge(current, next);
                if (edge == null) {
                    continue;
                }
                if (!edge.isPassable()) {
                    addRouteBlocker(result, current, next, edge.describeBlock(),
                        next.equals(goal) ? 100 : routeBlockPriority(edge), routeEdgeDenialCode(edge));
                    continue;
                }
                String tileBlock = routeTileBlockReason(data, next, faction, next.equals(goal), company);
                if (tileBlock != null) {
                    addRouteBlocker(result, current, next, tileBlock, next.equals(goal) ? 100 : 70, "PASSAGE_DENIED");
                    continue;
                }
                previous.put(next, current);
                if (next.equals(goal)) {
                    result.valid = true;
                    buildRoute(result, previous, start, goal);
                    addRouteEdgeNotes(data, result);
                    return result;
                }
                visited.add(next);
                result.visitedTiles.add(next);
                queue.add(next);
            }
        }
        result.failureReason = buildRouteFailureReason(result, start, goal, faction, visited);
        if (result.failureCode.length() == 0) {
            List<RouteBlocker> ordered = sortedRouteBlockers(result);
            result.failureCode = ordered.isEmpty() ? "ROUTE_EDGE_MISSING" : ordered.get(0).code;
        }
        return result;
    }

    private void sortRouteNeighborsForGoal(final KOMEWorldData data, final String current, List<String> neighbors, final String goal) {
        Collections.sort(neighbors, new Comparator<String>() {
            @Override
            public int compare(String first, String second) {
                String a = KOMEConquestTile.normalizeId(first);
                String b = KOMEConquestTile.normalizeId(second);
                int passable = routeEdgePassRank(data, current, a) - routeEdgePassRank(data, current, b);
                if (passable != 0) {
                    return passable;
                }
                double distance = routeCenterDistanceSq(a, goal) - routeCenterDistanceSq(b, goal);
                if (distance < 0.0D) {
                    return -1;
                }
                if (distance > 0.0D) {
                    return 1;
                }
                return a.compareTo(b);
            }
        });
    }

    private int routeEdgePassRank(KOMEWorldData data, String fromTile, String toTile) {
        KOMEConquestRouteEdge edge = data == null ? null : data.getRouteEdge(fromTile, toTile);
        if (edge == null) {
            return 3;
        }
        if (edge.isPassable()) {
            return edge.isSpecialPassage() ? 1 : 0;
        }
        return 2;
    }

    double routeCenterDistanceSq(String tileId, String goalTileId) {
        kome.common.data.KOMETileGameplayDefaults.Point tile = kome.common.data.KOMETileGameplayDefaults.get().getRouteReference(tileId);
        kome.common.data.KOMETileGameplayDefaults.Point goal = kome.common.data.KOMETileGameplayDefaults.get().getRouteReference(goalTileId);
        if (tile == null || goal == null) {
            return Double.MAX_VALUE;
        }
        double dx = tile.x - goal.x;
        double dz = tile.z - goal.z;
        return dx * dx + dz * dz;
    }

    private void addRouteBlocker(RouteResult result, String fromTile, String toTile, String reason, int priority,
            String code) {
        if (result == null || reason == null || reason.length() == 0) {
            return;
        }
        String from = KOMEConquestTile.normalizeId(fromTile);
        String to = KOMEConquestTile.normalizeId(toTile);
        for (RouteBlocker blocker : result.blockers) {
            if (blocker.fromTile.equals(from) && blocker.toTile.equals(to) && blocker.reason.equals(reason)) {
                blocker.priority = Math.max(blocker.priority, priority);
                return;
            }
        }
        if (result.blockers.size() >= 32 && priority < 100) {
            return;
        }
        result.blockers.add(new RouteBlocker(from, to, reason, priority, code));
    }

    private String routeEdgeDenialCode(KOMEConquestRouteEdge edge) {
        if (edge == null) return "ROUTE_EDGE_MISSING";
        if (KOMEConquestRouteEdge.MOUNTAIN.equals(edge.edgeType)) return "MOUNTAIN_EDGE_BLOCKED";
        if (KOMEConquestRouteEdge.RIVER.equals(edge.edgeType)) return "RIVER_EDGE_BLOCKED";
        return "ROUTE_EDGE_BLOCKED";
    }

    private int routeBlockPriority(KOMEConquestRouteEdge edge) {
        if (edge == null) {
            return 0;
        }
        if (KOMEConquestRouteEdge.RIVER.equals(edge.edgeType) || KOMEConquestRouteEdge.MOUNTAIN.equals(edge.edgeType)) {
            return 50;
        }
        return 20;
    }

    private String buildRouteFailureReason(RouteResult result, String start, String goal, String faction, Set<String> visited) {
        StringBuilder message = new StringBuilder();
        message.append("No legal route from ").append(start).append(" to ").append(goal).append(".");
        message.append(" Destination ").append(goal).append(" was not reachable through claimed ")
            .append(displayFaction(faction)).append(" tiles or partner tiles unlocked by an actual LOTR Ally relation.");
        if (visited == null || visited.size() <= 1) {
            message.append(" The origin has no legal outgoing route steps.");
        }
        List<RouteBlocker> blockers = new ArrayList<RouteBlocker>(result.blockers);
        Collections.sort(blockers, new Comparator<RouteBlocker>() {
            @Override
            public int compare(RouteBlocker first, RouteBlocker second) {
                if (first.priority != second.priority) {
                    return second.priority - first.priority;
                }
                int from = first.fromTile.compareTo(second.fromTile);
                return from != 0 ? from : first.toTile.compareTo(second.toTile);
            }
        });
        if (!blockers.isEmpty()) {
            message.append(" Blocked checks: ");
            int shown = Math.min(3, blockers.size());
            for (int i = 0; i < shown; i++) {
                if (i > 0) {
                    message.append("; ");
                }
                RouteBlocker blocker = blockers.get(i);
                message.append(blocker.fromTile).append(" -> ").append(blocker.toTile).append(": ")
                    .append(stripTrailingPeriod(blocker.reason));
            }
            if (blockers.size() > shown) {
                message.append("; +").append(blockers.size() - shown).append(" more.");
            }
        } else {
            message.append(" No adjacent path was found in the route graph.");
        }
        return message.toString();
    }

    private String routeTileBlockReason(KOMEWorldData data, String tileId, String factionKey, boolean destination) {
        return routeTileBlockReason(data, tileId, factionKey, destination, null);
    }

    private String routeTileBlockReason(KOMEWorldData data, String tileId, String factionKey, boolean destination,
            KOMEArmyCompany company) {
        String tileKey = KOMEConquestTile.normalizeId(tileId);
        KOMEConquestTile tile = data.getConquestTileIfPresent(tileKey);
        if (tile == null) {
            return "Tile " + tileKey + " was not found in conquest data.";
        }
        String owner = normalizeFaction(tile.projectRulingFaction());
        String faction = normalizeFaction(factionKey);
        if (owner.length() == 0) {
            return (destination ? "Destination tile " : "Tile ") + tileKey + " is not claimed.";
        }
        if (owner.equals(faction)) {
            return null;
        }
        if (data.canFactionUseMilitaryPassage(faction, owner)) {
            return null;
        }
        if (company != null && KOMEWartimeStewardshipService.canEnter(data, company, owner, false)) {
            return null;
        }
        if (destination && company != null
                && KOMEConflictMovementService.evaluateHostileDestination(
                    data, company, tileKey, true).allowed()) {
            return null;
        }
        if (destination) {
            return "Destination tile " + tileKey + " is owned by " + displayFaction(owner) + " ("
                + emptyKey(owner) + "), and this detachment has neither LOTR passage nor an authorized hostile "
                + "attack-destination commitment.";
        }
        return "Tile " + tileKey + " is controlled by " + displayFaction(owner) + " and no military passage permission exists.";
    }

    private void buildRoute(RouteResult result, Map<String, String> previous, String start, String goal) {
        List<String> route = new ArrayList<String>();
        String cursor = goal;
        route.add(cursor);
        while (!cursor.equals(start)) {
            cursor = previous.get(cursor);
            if (cursor == null || cursor.length() == 0) {
                result.valid = false;
                result.failureReason = "Route reconstruction failed.";
                result.routeTiles.clear();
                return;
            }
            route.add(cursor);
        }
        Collections.reverse(route);
        result.routeTiles.addAll(route);
    }

    private void addRouteEdgeNotes(KOMEWorldData data, RouteResult result) {
        for (int i = 1; i < result.routeTiles.size(); i++) {
            KOMEConquestRouteEdge edge = data.getRouteEdge(result.routeTiles.get(i - 1), result.routeTiles.get(i));
            if (edge != null && edge.isSpecialPassage()) {
                String name = edge.name == null || edge.name.length() == 0 ? "" : " " + edge.name;
                result.edgeNotes.add(KOMEConquestRouteEdge.displayEdgeType(edge.edgeType) + name
                    + " (" + edge.fromTile + " <-> " + edge.toTile + ")");
            }
        }
    }

    List<RouteBlocker> sortedRouteBlockers(RouteResult route) {
        List<RouteBlocker> blockers = new ArrayList<RouteBlocker>();
        if (route != null) {
            blockers.addAll(route.blockers);
        }
        Collections.sort(blockers, new Comparator<RouteBlocker>() {
            @Override
            public int compare(RouteBlocker first, RouteBlocker second) {
                if (first.priority != second.priority) {
                    return second.priority - first.priority;
                }
                int from = first.fromTile.compareTo(second.fromTile);
                return from != 0 ? from : first.toTile.compareTo(second.toTile);
            }
        });
        return blockers;
    }

    private String displayUnitId(KOMEHiredUnitRecord record) {
        if (record.unitName != null && record.unitName.length() > 0) {
            return record.unitName;
        }
        return record.entity == null ? "unknown" : record.entity.toString().substring(0, 8);
    }

    private String normalizeFaction(String key) {
        return KOMEAlliance.normalizeFactionKey(key);
    }

    private String emptyKey(String key) {
        return key == null || key.length() == 0 ? "none" : key;
    }

    private String getPlayerFaction(KOMEWorldData data, EntityPlayerMP player) {
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        return pledge == null ? "" : KOMEAlliance.normalizeFactionKey(pledge.codeName());
    }

    private String displayFaction(String key) {
        return KOMEAlliance.displayFactionName(key);
    }

    private String stripTrailingPeriod(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        while (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    static class RouteResult {
        public boolean valid;
        public String failureCode = "";
        public String failureReason = "";
        public final List<String> routeTiles = new ArrayList<String>();
        public final List<String> edgeNotes = new ArrayList<String>();
        public final List<RouteBlocker> blockers = new ArrayList<RouteBlocker>();
        public final Set<String> visitedTiles = new HashSet<String>();

        public int distance() {
            return Math.max(0, routeTiles.size() - 1);
        }
    }

    static class RouteBlocker {
        public final String fromTile;
        public final String toTile;
        public final String reason;
        public final String code;
        public int priority;

        private RouteBlocker(String fromTile, String toTile, String reason, int priority, String code) {
            this.fromTile = fromTile;
            this.toTile = toTile;
            this.reason = reason;
            this.code = code == null ? "ROUTE_BLOCKED" : code;
            this.priority = priority;
        }
    }

}
