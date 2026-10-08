package kome.common.data;

import java.time.Instant;
import java.util.Objects;
import net.minecraft.nbt.NBTTagCompound;
import static kome.common.data.KOMEConflictContracts.*;

/** Movement consequences of an explicitly validated release. Never determines a battle outcome. */
public final class KOMEConflictMovementHandoff {
    private KOMEConflictMovementHandoff() { }
    public enum Outcome { RESUME_ROUTE, DEFEATED, DESTROYED, FORMAL_RETREAT }
    public enum Code {
        ROUTE_RESUMED, WAITING_FOR_ALLOWANCE, ROUTE_ALREADY_COMPLETE, NEXT_EDGE_ILLEGAL,
        CANCELED_BY_DEFEAT, CANCELED_BY_DESTRUCTION, CANCELED_BY_FORMAL_RETREAT,
        REPLAYED, CONTRADICTORY_OUTCOME, STALE_HANDOFF, INVALID_RELEASE
    }
    public static final class Result {
        public final Code code;
        public final String reason;
        private Result(Code code, String reason) { this.code = code; this.reason = reason; }
        public boolean accepted() {
            return code != Code.CONTRADICTORY_OUTCOME && code != Code.STALE_HANDOFF && code != Code.INVALID_RELEASE;
        }
    }

    /** Optional movement receipt schema, independent of the root and ConflictRecord schemas. */
    public static final class Receipt {
        private static final String LEGACY_QUARANTINE = "LEGACY_FORMAL_RETREAT_GROUP_UNRESOLVED: ";
        public final String conflictId, orderId, companyId, reason;
        public final long conflictRevision, appliedAtMillis;
        public final Outcome outcome;
        public final Code code;
        private Receipt(String conflict, long revision, String order, String company,
                Outcome outcome, long at, Code code, String reason) {
            if (conflict == null || !conflict.matches("CF[1-9][0-9]*") || revision < 1
                    || order == null || order.trim().isEmpty() || company == null
                    || company.trim().isEmpty() || outcome == null || at < 0 || code == null || reason == null
                    || !(outcome == Outcome.RESUME_ROUTE && (code == Code.ROUTE_RESUMED
                            || code == Code.WAITING_FOR_ALLOWANCE || code == Code.ROUTE_ALREADY_COMPLETE || code == Code.NEXT_EDGE_ILLEGAL)
                        || outcome == Outcome.DEFEATED && code == Code.CANCELED_BY_DEFEAT
                        || outcome == Outcome.DESTROYED && code == Code.CANCELED_BY_DESTRUCTION
                        || outcome == Outcome.FORMAL_RETREAT && code == Code.CANCELED_BY_FORMAL_RETREAT))
                throw new IllegalArgumentException("Invalid conflict movement receipt");
            conflictId = conflict; conflictRevision = revision; orderId = order;
            companyId = company; this.outcome = outcome; appliedAtMillis = at;
            this.code = code; this.reason = reason;
        }
        public boolean isLegacyQuarantined() {
            return outcome == Outcome.FORMAL_RETREAT && reason.startsWith(LEGACY_QUARANTINE);
        }
        Receipt quarantineLegacy() {
            return isLegacyQuarantined() ? this : new Receipt(conflictId, conflictRevision,
                orderId, companyId, outcome, appliedAtMillis, code, LEGACY_QUARANTINE + reason);
        }
        public NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("SchemaVersion", 2);
            tag.setBoolean("HasRelease", true);
            tag.setString("ConflictId", conflictId); tag.setLong("ConflictRevision", conflictRevision);
            tag.setString("OrderId", orderId); tag.setString("CompanyId", companyId);
            tag.setString("Outcome", outcome.name()); tag.setLong("AppliedAtMillis", appliedAtMillis);
            tag.setString("Code", code.name()); tag.setString("Reason", reason); return tag;
        }
        public static Receipt read(NBTTagCompound tag) {
            for (String key : new String[]{"ConflictId", "OrderId", "CompanyId", "Outcome", "Code", "Reason"})
                if (!tag.hasKey(key, 8)) throw new IllegalArgumentException("Missing handoff " + key);
            if (!tag.hasKey("SchemaVersion", 3)
                    || !(tag.getInteger("SchemaVersion") == 1 || tag.getInteger("SchemaVersion") == 2
                        && tag.hasKey("HasRelease",1) && tag.getBoolean("HasRelease"))
                    || !tag.hasKey("ConflictRevision", 4) || !tag.hasKey("AppliedAtMillis", 4))
                throw new IllegalArgumentException("Invalid handoff schema/metadata");
            return new Receipt(tag.getString("ConflictId"), tag.getLong("ConflictRevision"),
                tag.getString("OrderId"), tag.getString("CompanyId"), Outcome.valueOf(tag.getString("Outcome")),
                tag.getLong("AppliedAtMillis"), Code.valueOf(tag.getString("Code")), tag.getString("Reason"));
        }
    }

    static NBTTagCompound writeEnvelope(Receipt release,KOMEFormalRetreatBatch batch){
        NBTTagCompound tag=release==null?new NBTTagCompound():release.write();
        tag.setInteger("SchemaVersion",2);tag.setBoolean("HasRelease",release!=null);
        if(batch!=null)tag.setTag("RetreatBatch",batch.write());
        return tag;
    }

    static void readEnvelope(KOMEArmyMovementOrder order,NBTTagCompound tag){
        if(!tag.hasKey("SchemaVersion",3))throw new IllegalArgumentException("Missing movement release schema");
        int version=tag.getInteger("SchemaVersion");
        if(version==1){
            if(tag.hasKey("RetreatBatch")||tag.hasKey("HasRelease"))
                throw new IllegalArgumentException("Legacy release cannot carry v2 authority");
            order.conflictRelease=Receipt.read(tag);return;
        }
        if(version!=2||!tag.hasKey("HasRelease",1))
            throw new IllegalArgumentException("Unsupported movement release schema");
        if(tag.getBoolean("HasRelease"))order.conflictRelease=Receipt.read(tag);
        else for(String key:new String[]{"ConflictId","ConflictRevision","OrderId","CompanyId","Outcome","AppliedAtMillis","Code","Reason"})
            if(tag.hasKey(key))throw new IllegalArgumentException("Unapplied envelope contains release authority");
        if(tag.hasKey("RetreatBatch")){
            if(!tag.hasKey("RetreatBatch",10))throw new IllegalArgumentException("Invalid retreat batch");
            order.formalRetreatBatch=KOMEFormalRetreatBatch.read(tag.getCompoundTag("RetreatBatch"));
        }
        if(order.conflictRelease==null&&order.formalRetreatBatch==null)
            throw new IllegalArgumentException("Empty movement release envelope");
    }

    /** Consume a canonical ENDED snapshot; an unresolved end alone never implies victory. */
    public static Result applyEndedConflict(KOMEWorldData data, String tileId,
            ExpectedConflict expected, String orderId, Outcome outcome, Context context) {
        if (data == null) return result(Code.INVALID_RELEASE, "World movement authority is required.");
        data.ensureWritable();
        KOMEArmyMovementOrder order = data.armyMovements.get(orderId);
        Result replay = replay(order, expected, outcome);
        if (replay != null) return replay;
        KOMEConflictRecord conflict = data.getConflictService().get(tileId);
        if (outcome == null || context == null || expected == null || conflict == null
                || conflict.isActive() || !conflict.getConflictId().equals(expected.conflictId)
                || conflict.getRevision() != expected.revision
                || context.timestampMillis < conflict.getEndedAtMillis())
            return result(Code.INVALID_RELEASE, "An exact ended conflict and explicit movement outcome are required.");
        KOMEConflictRecord.Commitment commitment = order == null ? null : conflict.getCommitments().get(order.companyId);
        if (commitment == null || !order.id.equals(commitment.movementOrderId))
            return result(Code.STALE_HANDOFF, "Release does not identify this conflict's saved movement order.");
        Result invalid = validateOrder(data, order, conflict, outcome);
        if (invalid != null) return invalid;
        return publish(data, order, conflict, outcome, context);
    }

    /** Explicit single-detachment release while other commitments may remain active. */
    public static Result releaseCommitment(KOMEWorldData data, ValidatedDepartureRequest request,
            String orderId, Outcome outcome, Context context) {
        if (data == null || request == null || context == null || outcome == null)
            return result(Code.INVALID_RELEASE, "Validated departure and explicit outcome are required.");
        data.ensureWritable();
        KOMEArmyMovementOrder order = data.armyMovements.get(orderId);
        // The receipt records the revision published by the canonical release operation.
        if (request.expectedConflict.isAbsent() || request.expectedConflict.revision == Long.MAX_VALUE)
            return result(Code.INVALID_RELEASE, "Exact non-exhausted conflict revision is required.");
        ExpectedConflict released = ExpectedConflict.at(request.expectedConflict.conflictId,
            request.expectedConflict.revision + 1L);
        Result replay = replay(order, released, outcome);
        if (replay != null) return replay;
        KOMEConflictRecord before = data.getConflictService().get(request.tileId);
        KOMEConflictRecord.Commitment commitment = before == null ? null : before.getCommitments().get(request.detachmentId);
        if (order == null || !request.detachmentId.equals(order.companyId) || commitment == null
                || !order.id.equals(commitment.movementOrderId))
            return result(Code.STALE_HANDOFF, "Departure does not identify the held movement order.");
        Result invalid = validateOrder(data, order, before, outcome);
        if (invalid != null) return invalid;
        // Complete every movement-side check (including configuration access) before releasing
        // conflict authority. Publication below performs no additional world/config lookups.
        Plan prepared = prepare(data, order, before.getConflictId(), released.revision, outcome, context);
        KOMEConflictService.Result release = data.getConflictService().releaseValidatedCommitment(data, request, context);
        if (!release.isSuccess()) return result(Code.INVALID_RELEASE, release.code + ": " + release.reason);
        return publish(data, order, prepared, context);
    }

    private static Result replay(KOMEArmyMovementOrder order, ExpectedConflict expected, Outcome outcome) {
        if (order == null || order.conflictRelease == null) return null;
        Receipt receipt = order.conflictRelease;
        if (receipt.isLegacyQuarantined())
            return result(Code.INVALID_RELEASE, "Incomplete legacy Formal Retreat requires operator review; accepted group authority is missing.");
        if (expected == null || !receipt.conflictId.equals(expected.conflictId)
                || receipt.conflictRevision != expected.revision)
            return result(Code.STALE_HANDOFF, "Release event does not match the accepted handoff.");
        return outcome == receipt.outcome ? result(Code.REPLAYED, "Movement handoff already applied.")
            : result(Code.CONTRADICTORY_OUTCOME, "This release already recorded " + receipt.outcome + ".");
    }

    private static Result validateOrder(KOMEWorldData data, KOMEArmyMovementOrder order,
            KOMEConflictRecord conflict, Outcome outcome) {
        if (order == null || !(KOMEArmyMovementOrder.CONFLICT_HELD.equals(order.status)
                && conflict.getConflictId().equals(order.conflictHoldId)
                || KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED.equals(order.status))
                || !conflict.getTileId().equals(KOMEConquestTile.normalizeId(order.currentTile)))
            return result(Code.STALE_HANDOFF, "Only this conflict's paused route may receive a handoff.");
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        if (company == null) return outcome == Outcome.RESUME_ROUTE
            ? result(Code.INVALID_RELEASE, "Missing company cannot resume a route.") : null;
        if (!order.id.equals(company.movementOrderId)
                || !conflict.getTileId().equals(KOMEConquestTile.normalizeId(company.currentTile))
                || !Objects.equals(order.owner, company.owner)
                || !KOMEAlliance.normalizeFactionKey(order.ownerFaction).equals(KOMEAlliance.normalizeFactionKey(company.faction)))
            return result(Code.STALE_HANDOFF, "Company ownership, tile or newer movement link disagrees with this release.");
        if (outcome == Outcome.RESUME_ROUTE) {
            if (KOMECompanyCoherenceService.INSTANCE.assess(data, company).status == KOMECompanyCoherenceService.Status.INCOHERENT)
                return result(Code.INVALID_RELEASE, "Incoherent company cannot resume a route.");
            if (KOMEConflictMovementService.isActivelyCommitted(data, company.id) && !conflict.isActive())
                return result(Code.INVALID_RELEASE, "Company is committed to another active conflict.");
            if (order.routeTiles.isEmpty() || order.currentRouteIndex < 0
                    || order.currentRouteIndex >= order.routeTiles.size()
                    || !order.currentTile.equals(KOMEConquestTile.normalizeId(order.routeTiles.get(order.currentRouteIndex)))
                    || !new java.util.HashSet<java.util.UUID>(order.units).equals(new java.util.HashSet<java.util.UUID>(company.units)))
                return result(Code.INVALID_RELEASE, "Saved route/cohort cannot be resumed safely.");
            for (java.util.UUID unitId : order.units) {
                KOMEHiredUnitRecord unit = data.hiredUnits.get(unitId);
                if (unit == null || !KOMEHiredUnitClassification.isCampaignUnit(unit)
                        || !order.companyId.equals(unit.companyId) || !order.id.equals(unit.movementOrderId)
                        || !order.currentTile.equals(KOMEConquestTile.normalizeId(unit.currentTile)))
                    return result(Code.INVALID_RELEASE, "Saved movement cohort cannot be resolved.");
            }
        }
        return null;
    }

    private static Result publish(KOMEWorldData data, KOMEArmyMovementOrder order,
            KOMEConflictRecord conflict, Outcome outcome, Context context) {
        return publish(data, order, prepare(data, order, conflict.getConflictId(), conflict.getRevision(), outcome, context), context);
    }

    private static final class Plan {
        final Receipt receipt;
        final long nextBoundary;
        Plan(Receipt receipt, long nextBoundary) { this.receipt = receipt; this.nextBoundary = nextBoundary; }
    }
    private static Plan prepare(KOMEWorldData data, KOMEArmyMovementOrder order,
            String conflictId, long revision, Outcome outcome, Context context) {
        Code code; String reason;
        boolean complete = order.currentRouteIndex == order.routeTiles.size() - 1;
        KOMEMovementDepartureGuard.Decision guard = outcome == Outcome.RESUME_ROUTE && !complete
            ? KOMEMovementDepartureGuard.evaluate(data, order) : null;
        long nextBoundary = 0L;
        if (outcome == Outcome.RESUME_ROUTE && !complete && guard.allowed()
                && KOMEMovementDayService.remaining(data, order) == 0)
            nextBoundary = KOMEMovementDayService.schedule().nextBoundary(Instant.ofEpochMilli(context.timestampMillis)).toEpochMilli();
        if (outcome != Outcome.RESUME_ROUTE) {
            code = outcome == Outcome.DEFEATED ? Code.CANCELED_BY_DEFEAT
                : outcome == Outcome.DESTROYED ? Code.CANCELED_BY_DESTRUCTION : Code.CANCELED_BY_FORMAL_RETREAT;
            reason = "Route canceled by " + outcome + ".";
        } else if (complete) { code = Code.ROUTE_ALREADY_COMPLETE; reason = "Saved route already reached its final tile; arrival is not replayed."; }
        else if (!guard.allowed()) { code = Code.NEXT_EDGE_ILLEGAL; reason = guard.code + ": " + guard.reason; }
        else if (nextBoundary != 0L) { code = Code.WAITING_FOR_ALLOWANCE; reason = "Route released; waiting for movement allowance."; }
        else { code = Code.ROUTE_RESUMED; reason = "Route released; next legal departure may resume."; }
        return new Plan(new Receipt(conflictId, revision, order.id,
            order.companyId, outcome, context.timestampMillis, code, reason), nextBoundary);
    }
    private static Result publish(KOMEWorldData data, KOMEArmyMovementOrder order, Plan plan, Context context) {
        Receipt receipt = plan.receipt;
        Outcome outcome = receipt.outcome; Code code = receipt.code; String reason = receipt.reason;
        boolean complete = code == Code.ROUTE_ALREADY_COMPLETE;
        long nextBoundary = plan.nextBoundary;
        order.conflictRelease = receipt;
        order.conflictHoldId = ""; order.conflictHeldAtMillis = 0L;
        order.arrivalMillis = 0L; order.nextStepDepartureMillis = nextBoundary;
        order.nextStepAvailableMillis = nextBoundary; order.spawnRetryPaused = false;
        order.pendingSpawnReason = reason;
        order.status = outcome != Outcome.RESUME_ROUTE ? KOMEArmyMovementOrder.CANCELLED
            : complete ? KOMEArmyMovementOrder.ARRIVED : code != Code.NEXT_EDGE_ILLEGAL
                ? KOMEArmyMovementOrder.WAITING_NEXT_STEP : KOMEArmyMovementOrder.STOPPED;
        order.stopped = code == Code.NEXT_EDGE_ILLEGAL;
        if (outcome == Outcome.RESUME_ROUTE && !complete) {
            order.nextRouteIndex = order.currentRouteIndex + 1;
            order.nextTile = KOMEConquestTile.normalizeId(order.routeTiles.get(order.nextRouteIndex));
            order.currentStepOriginTile = order.currentTile;
            order.currentStepDestinationTile = order.nextTile;
        }
        boolean unlink = outcome != Outcome.RESUME_ROUTE || complete;
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        if (company != null) {
            company.status = KOMEArmyCompany.STATIONED; company.updatedAtMillis = context.timestampMillis;
            if (unlink) company.movementOrderId = "";
        }
        if (unlink) for (java.util.UUID unitId : order.units) {
            KOMEHiredUnitRecord unit = data.hiredUnits.get(unitId);
            if (unit != null && order.id.equals(unit.movementOrderId)) unit.movementOrderId = "";
        }
        data.updateMovementHistory(order, outcome != Outcome.RESUME_ROUTE ? KOMEMovementHistoryRecord.CANCELLED
            : complete ? KOMEMovementHistoryRecord.ARRIVED : code == Code.NEXT_EDGE_ILLEGAL
                ? KOMEMovementHistoryRecord.FAILED : KOMEMovementHistoryRecord.ACTIVE);
        KOMEAuditService.record(data, context.timestampMillis, "MOVEMENT", code.name(), context.actor,
            order.id, context.reason, "conflict=" + receipt.conflictId + ";revision=" + receipt.conflictRevision + ";outcome=" + outcome);
        data.markDirty(); return result(code, reason);
    }
    private static Result result(Code code, String reason) { return new Result(code, reason); }
}
