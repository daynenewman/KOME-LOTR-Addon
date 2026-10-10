package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Explicit, server-authoritative Campaign Detachment composition changes.
 * Physical evidence is validation only; it never supplies strategic location.
 */
public final class KOMECompanyReorganizationService {
    public static final KOMECompanyReorganizationService INSTANCE =
        new KOMECompanyReorganizationService(
            KOMECompanyCoherenceService.INSTANCE, new NoOpMutationHook());

    public enum Operation { SPLIT, MERGE }

    public enum Code {
        SUCCESS,
        INVALID_REQUEST,
        COMPANY_NOT_FOUND,
        NOT_AUTHORIZED,
        EMPTY_COMPANY,
        INVALID_MEMBER_SELECTION,
        WHOLE_COMPANY_SELECTED,
        MEMBER_NOT_IN_PARENT,
        MEMBER_NOT_ELIGIBLE,
        INCOHERENT,
        PHYSICAL_CONTRADICTION,
        ROUTE_ACTIVE,
        TRANSFER_PENDING,
        WITHDRAWAL_ACTIVE,
        MUTATION_HOLD,
        CONFLICT_COMMITTED,
        AUTHORITY_STATE_UNSAFE,
        SAME_COMPANY,
        DIFFERENT_OWNER,
        INCOMPATIBLE_FACTION,
        DIFFERENT_STRATEGIC_TILE,
        ALLOCATION_FAILED,
        MUTATION_FAILED
    }

    public static final class Result {
        public final boolean success;
        public final Code code;
        public final Operation operation;
        public final String primaryCompanyId;
        public final String secondaryCompanyId;
        public final KOMEArmyCompany primaryCompany;
        public final KOMEArmyCompany secondaryCompany;
        public final String reason;

        private Result(boolean success, Code code, Operation operation,
                KOMEArmyCompany primary, KOMEArmyCompany secondary,
                String primaryId, String secondaryId, String reason) {
            this.success = success;
            this.code = code;
            this.operation = operation;
            primaryCompany = primary;
            secondaryCompany = secondary;
            primaryCompanyId = clean(primaryId);
            secondaryCompanyId = clean(secondaryId);
            this.reason = clean(reason);
        }

        private static Result success(Operation operation, KOMEArmyCompany primary,
                KOMEArmyCompany secondary, String secondaryId) {
            return new Result(true, Code.SUCCESS, operation, primary, secondary,
                primary == null ? "" : primary.id, secondaryId, "");
        }

        private static Result failure(Operation operation, Code code,
                String primaryId, String secondaryId, String reason) {
            return new Result(false, code, operation, null, null,
                primaryId, secondaryId, reason);
        }
    }

    interface MutationHook {
        void afterMutation(Operation operation, KOMEArmyCompany primary,
            KOMEArmyCompany secondary);
    }

    private static final class NoOpMutationHook implements MutationHook {
        @Override public void afterMutation(Operation operation,
                KOMEArmyCompany primary, KOMEArmyCompany secondary) { }
    }

    private static final class Failure {
        final Code code;
        final String reason;

        Failure(Code code, String reason) {
            this.code = code;
            this.reason = reason;
        }
    }

    private final KOMECompanyCoherenceService coherence;
    private final MutationHook mutationHook;

    KOMECompanyReorganizationService(KOMECompanyCoherenceService coherence,
            MutationHook mutationHook) {
        if (coherence == null) throw new IllegalArgumentException("coherence");
        if (mutationHook == null) throw new IllegalArgumentException("mutationHook");
        this.coherence = coherence;
        this.mutationHook = mutationHook;
    }

    public Result split(KOMEWorldData data, UUID actor, String parentId,
            Collection<UUID> selectedUnits) {
        return split(data, actor, false, parentId, selectedUnits);
    }

    public Result split(KOMEWorldData data, UUID actor, boolean administrator,
            String parentId, Collection<UUID> selectedUnits) {
        final Operation operation = Operation.SPLIT;
        String requestedParent = clean(parentId);
        if (data == null || actor == null || requestedParent.length() == 0
                || selectedUnits == null) {
            return Result.failure(operation, Code.INVALID_REQUEST,
                requestedParent, "", "Split request data is incomplete.");
        }
        synchronized (data) {
            data.ensureWritable();
            KOMEArmyCompany parent = data.armyCompanies.get(requestedParent);
            if (parent == null) {
                return Result.failure(operation, Code.COMPANY_NOT_FOUND,
                    requestedParent, "", "The parent Campaign Detachment does not exist.");
            }
            Failure blocked = validateMutationCompany(data, parent, actor, administrator);
            if (blocked != null) return failure(operation, blocked, parent.id, "");

            LinkedHashSet<UUID> selected = new LinkedHashSet<UUID>();
            for (UUID unitId : selectedUnits) {
                if (unitId == null || !selected.add(unitId)) {
                    return Result.failure(operation, Code.INVALID_MEMBER_SELECTION,
                        parent.id, "", "Every selected unit UUID must be explicit and unique.");
                }
            }
            if (selected.isEmpty()) {
                return Result.failure(operation, Code.INVALID_MEMBER_SELECTION,
                    parent.id, "", "Select at least one Campaign Detachment member.");
            }
            if (selected.size() >= new LinkedHashSet<UUID>(parent.units).size()) {
                return Result.failure(operation, Code.WHOLE_COMPANY_SELECTED,
                    parent.id, "", "A split must leave the parent detachment non-empty.");
            }
            Failure selectionFailure = validateSplitSelection(data, parent, selected);
            if (selectionFailure != null)
                return failure(operation, selectionFailure, parent.id, "");

            String childId;
            try {
                childId = data.nextCampaignCompanyId();
            } catch (RuntimeException allocationFailure) {
                return Result.failure(operation, Code.ALLOCATION_FAILED,
                    parent.id, "", "A stable child detachment ID could not be allocated: "
                        + safeMessage(allocationFailure));
            }

            blocked = validateMutationCompany(data, parent, actor, administrator);
            if (blocked != null) return failure(operation, blocked, parent.id, childId);
            selectionFailure = validateSplitSelection(data, parent, selected);
            if (selectionFailure != null)
                return failure(operation, selectionFailure, parent.id, childId);

            NBTTagCompound parentBefore = parent.writeToNBT();
            Map<UUID, NBTTagCompound> recordsBefore = snapshotRecords(data, parent.units);
            KOMEArmyCompany child = newChild(data, parent, childId);
            try {
                data.armyCompanies.put(child.id, child);
                long now = System.currentTimeMillis();
                String actorName = data.safePlayerName(actor);
                for (UUID unitId : selected) {
                    KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                    parent.units.remove(unitId);
                    child.units.add(unitId);
                    assignRecord(record, child, actor, actorName, now);
                }
                data.recalculateCampaignCompanyComposition(parent);
                data.recalculateCampaignCompanyComposition(child);
                KOMEMovementDayService.split(parent, child);
                mutationHook.afterMutation(operation, parent, child);
                requireValidSplit(data, parent, child, selected);
                data.markDirty();
                return Result.success(operation, parent, child, child.id);
            } catch (RuntimeException mutationFailure) {
                parent.readFromNBT(parentBefore);
                restoreRecords(data, recordsBefore);
                data.armyCompanies.remove(child.id);
                return Result.failure(operation, Code.MUTATION_FAILED,
                    parent.id, child.id, "Campaign Detachment split was rolled back: "
                        + safeMessage(mutationFailure));
            }
        }
    }

    public Result merge(KOMEWorldData data, UUID actor,
            String survivorId, String absorbedId) {
        return merge(data, actor, false, survivorId, absorbedId);
    }

    public Result merge(KOMEWorldData data, UUID actor, boolean administrator,
            String survivorId, String absorbedId) {
        final Operation operation = Operation.MERGE;
        String requestedSurvivor = clean(survivorId);
        String requestedAbsorbed = clean(absorbedId);
        if (data == null || actor == null || requestedSurvivor.length() == 0
                || requestedAbsorbed.length() == 0) {
            return Result.failure(operation, Code.INVALID_REQUEST,
                requestedSurvivor, requestedAbsorbed, "Merge request data is incomplete.");
        }
        if (requestedSurvivor.equals(requestedAbsorbed)) {
            return Result.failure(operation, Code.SAME_COMPANY,
                requestedSurvivor, requestedAbsorbed,
                "Select two distinct Campaign Detachments.");
        }
        synchronized (data) {
            data.ensureWritable();
            KOMEArmyCompany survivor = data.armyCompanies.get(requestedSurvivor);
            KOMEArmyCompany absorbed = data.armyCompanies.get(requestedAbsorbed);
            if (survivor == null || absorbed == null) {
                return Result.failure(operation, Code.COMPANY_NOT_FOUND,
                    requestedSurvivor, requestedAbsorbed,
                    "Both Campaign Detachments must exist.");
            }
            Failure compatibility = validateMergeCompatibility(survivor, absorbed);
            if (compatibility != null)
                return failure(operation, compatibility, survivor.id, absorbed.id);
            Failure survivorBlocked =
                validateMutationCompany(data, survivor, actor, administrator);
            if (survivorBlocked != null)
                return failure(operation, survivorBlocked, survivor.id, absorbed.id);
            Failure absorbedBlocked =
                validateMutationCompany(data, absorbed, actor, administrator);
            if (absorbedBlocked != null)
                return failure(operation, absorbedBlocked, survivor.id, absorbed.id);
            survivorBlocked = validateMutationCompany(
                data, survivor, actor, administrator);
            absorbedBlocked = validateMutationCompany(
                data, absorbed, actor, administrator);
            compatibility = validateMergeCompatibility(survivor, absorbed);
            if (survivorBlocked != null)
                return failure(operation, survivorBlocked, survivor.id, absorbed.id);
            if (absorbedBlocked != null)
                return failure(operation, absorbedBlocked, survivor.id, absorbed.id);
            if (compatibility != null)
                return failure(operation, compatibility, survivor.id, absorbed.id);

            NBTTagCompound survivorBefore = survivor.writeToNBT();
            NBTTagCompound absorbedBefore = absorbed.writeToNBT();
            List<UUID> affected = new ArrayList<UUID>(survivor.units);
            affected.addAll(absorbed.units);
            Map<UUID, NBTTagCompound> recordsBefore = snapshotRecords(data, affected);
            try {
                long now = System.currentTimeMillis();
                String actorName = data.safePlayerName(actor);
                for (UUID unitId : new ArrayList<UUID>(absorbed.units)) {
                    KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                    if (!survivor.units.contains(unitId)) survivor.units.add(unitId);
                    assignRecord(record, survivor, actor, actorName, now);
                }
                absorbed.units.clear();
                data.armyCompanies.remove(absorbed.id);
                data.recalculateCampaignCompanyComposition(survivor);
                KOMEMovementDayService.merge(survivor, absorbed);
                mutationHook.afterMutation(operation, survivor, absorbed);
                requireValidMerge(data, survivor, absorbed.id, recordsBefore.keySet());
                data.markDirty();
                return Result.success(operation, survivor, null, absorbed.id);
            } catch (RuntimeException mutationFailure) {
                survivor.readFromNBT(survivorBefore);
                absorbed.readFromNBT(absorbedBefore);
                data.armyCompanies.put(survivor.id, survivor);
                data.armyCompanies.put(absorbed.id, absorbed);
                restoreRecords(data, recordsBefore);
                return Result.failure(operation, Code.MUTATION_FAILED,
                    survivor.id, absorbed.id, "Campaign Detachment merge was rolled back: "
                        + safeMessage(mutationFailure));
            }
        }
    }

    private Failure validateMutationCompany(KOMEWorldData data,
            KOMEArmyCompany company, UUID actor, boolean administrator) {
        if (KOMESeasonResetService.active(data)) return new Failure(Code.MUTATION_HOLD, "Season reset is pending");
        if (KOMEFormalRetreatAuthority.protectsCompany(data, company.id))
            return new Failure(Code.MUTATION_HOLD, "An unfinished Formal Retreat reserves this detachment.");
        if (company.owner == null || !administrator && !company.owner.equals(actor)) {
            return new Failure(Code.NOT_AUTHORIZED,
                "Only the detachment owner or an operator may reorganize it.");
        }
        if (company.units.isEmpty()) {
            return new Failure(Code.EMPTY_COMPANY,
                "An empty Campaign Detachment cannot be reorganized.");
        }
        KOMEConflictRecord conflict =
            KOMEConflictMovementService.activeConflictForDetachment(data, company.id);
        if (conflict != null
                || KOMEConflictMovementService.isActivelyCommitted(data, company.id)) {
            return new Failure(Code.CONFLICT_COMMITTED,
                "Campaign Detachment is committed to active conflict "
                    + (conflict == null ? "(ambiguous authority)" : conflict.getConflictId())
                    + ".");
        }
        if (company.transferRecipient != null || company.transferOfferedBy != null) {
            return new Failure(Code.TRANSFER_PENDING,
                "Resolve the pending ownership transfer before reorganizing.");
        }
        if (!KOMEArmyCompany.CLEANUP_NONE.equals(clean(company.withdrawalState))) {
            return new Failure(Code.WITHDRAWAL_ACTIVE,
                "Resolve withdrawal or demobilization state before reorganizing.");
        }
        if (KOMEArmyCompany.WAR_ENDED_HALTED.equals(company.status)) {
            return new Failure(Code.MUTATION_HOLD,
                "This detachment is held by an established strategic cleanup state.");
        }
        if (unsafeAuthorityState(data, company)) {
            return new Failure(Code.AUTHORITY_STATE_UNSAFE,
                "Temporary, delegated, stewardship, or war authority must be resolved before reorganizing.");
        }
        if (hasRouteState(data, company)) {
            return new Failure(Code.ROUTE_ACTIVE,
                "Cancel the detachment's route before reorganizing.");
        }
        if (!KOMEArmyCompany.STATIONED.equals(company.status)) {
            return new Failure(Code.MUTATION_HOLD,
                "The detachment is not in a mutable stationed state.");
        }
        KOMECompanyCoherenceService.Assessment assessment =
            coherence.assess(data, company);
        if (assessment.physicallyContradictoryMembers > 0) {
            return new Failure(Code.PHYSICAL_CONTRADICTION,
                "A loaded CAMPAIGN member is physically outside the strategic detachment tile.");
        }
        if (assessment.status == KOMECompanyCoherenceService.Status.INCOHERENT) {
            return new Failure(Code.INCOHERENT,
                "The Campaign Detachment is incoherent and must be repaired before reorganization.");
        }
        return null;
    }

    private static Failure validateSplitSelection(KOMEWorldData data,
            KOMEArmyCompany parent, Set<UUID> selected) {
        for (UUID unitId : selected) {
            if (!parent.units.contains(unitId)) {
                return new Failure(Code.MEMBER_NOT_IN_PARENT,
                    "Selected unit " + unitId + " does not belong to the parent detachment.");
            }
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (!eligibleMember(data, parent, unitId, record)) {
                return new Failure(Code.MEMBER_NOT_ELIGIBLE,
                    "Selected unit " + unitId + " is not an eligible stationed CAMPAIGN member.");
            }
        }
        return null;
    }

    private static Failure validateMergeCompatibility(
            KOMEArmyCompany survivor, KOMEArmyCompany absorbed) {
        if (survivor.owner == null || absorbed.owner == null
                || !survivor.owner.equals(absorbed.owner)) {
            return new Failure(Code.DIFFERENT_OWNER,
                "Campaign Detachments with different owners cannot merge.");
        }
        String survivorFaction = faction(survivor.faction);
        String absorbedFaction = faction(absorbed.faction);
        String survivorNative = KOMEWartimeStewardshipService.nativeFaction(survivor);
        String absorbedNative = KOMEWartimeStewardshipService.nativeFaction(absorbed);
        if (survivorFaction.length() == 0 || !survivorFaction.equals(absorbedFaction)
                || !survivorNative.equals(absorbedNative)) {
            return new Failure(Code.INCOMPATIBLE_FACTION,
                "Campaign Detachment faction and native-faction identities must match.");
        }
        if (!tile(survivor.currentTile).equals(tile(absorbed.currentTile))) {
            return new Failure(Code.DIFFERENT_STRATEGIC_TILE,
                "Campaign Detachments must share the same strategic conquest tile.");
        }
        if (!clean(survivor.populationSource).equals(clean(absorbed.populationSource))) {
            return new Failure(Code.AUTHORITY_STATE_UNSAFE,
                "Different company-level population authority metadata cannot be combined safely.");
        }
        return null;
    }

    private static boolean eligibleMember(KOMEWorldData data,
            KOMEArmyCompany company, UUID unitId, KOMEHiredUnitRecord record) {
        if (record == null || record.entity == null || !unitId.equals(record.entity)
                || record.owner == null || !record.owner.equals(company.owner)) return false;
        if (data.hiredUnits.get(unitId) != record
                || !clean(company.id).equals(clean(record.companyId))) return false;
        if (!KOMEHiredUnitClassification.isCampaignUnit(record)
                || record.farmhand || record.type != KOMEPopulationType.OFFENSIVE) return false;
        if (!tile(company.currentTile).equals(tile(record.currentTile))
                || clean(record.movementOrderId).length() > 0) return false;
        String expectedFaction = faction(data.resolveCampaignCompanyFaction(record));
        return expectedFaction.length() == 0
            || expectedFaction.equals(faction(company.faction))
                && expectedFaction.equals(KOMEWartimeStewardshipService.nativeFaction(company));
    }

    private static boolean unsafeAuthorityState(KOMEWorldData data,
            KOMEArmyCompany company) {
        if (company.temporaryController != null || company.delegatedBy != null
                || !KOMEArmyCompany.AUTHORITY_NATIVE.equals(
                    clean(company.controllerAuthority))
                || !company.authorizedWarIds.isEmpty()
                || company.stewardshipCreated
                || company.stewardshipReservation > 0) return true;
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record == null) continue;
            if (record.controller != null
                    && (record.owner == null || !record.owner.equals(record.controller)))
                return true;
            String authority = clean(record.controllerAuthority);
            if (authority.length() > 0
                    && !KOMEArmyCompany.AUTHORITY_NATIVE.equals(authority)) return true;
            if (clean(record.stewardshipWarIds).length() > 0
                    || "MILITARY_T3_STEWARDSHIP".equals(record.benefitSource)) return true;
        }
        return false;
    }

    private static boolean hasRouteState(KOMEWorldData data,
            KOMEArmyCompany company) {
        if (clean(company.movementOrderId).length() > 0
                || KOMEArmyCompany.MOVING.equals(company.status)) return true;
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order != null && clean(company.id).equals(clean(order.companyId))
                    && order.isMoving()) return true;
        }
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null && clean(record.movementOrderId).length() > 0) return true;
        }
        return false;
    }

    private static KOMEArmyCompany newChild(KOMEWorldData data,
            KOMEArmyCompany parent, String childId) {
        KOMEArmyCompany child = new KOMEArmyCompany();
        child.id = childId;
        child.owner = parent.owner;
        child.ownerName = clean(parent.ownerName).length() == 0
            ? data.safePlayerName(parent.owner) : parent.ownerName;
        child.faction = faction(parent.faction);
        child.nativeFaction = KOMEWartimeStewardshipService.nativeFaction(parent);
        child.name = data.defaultCampaignCompanyName(parent.currentTile);
        child.currentTile = tile(parent.currentTile);
        child.sourceTileId = tile(parent.sourceTileId);
        child.source = KOMEArmyCompany.SOURCE_CAMPAIGN_RECRUITMENT;
        child.status = KOMEArmyCompany.STATIONED;
        child.movementOrderId = "";
        child.tendency = KOMEArmyCompany.AGGRESSIVE.equals(parent.tendency)
            ? KOMEArmyCompany.AGGRESSIVE : KOMEArmyCompany.CONSERVATIVE;
        child.populationSource = clean(parent.populationSource);
        child.controllerAuthority = KOMEArmyCompany.AUTHORITY_NATIVE;
        child.withdrawalState = KOMEArmyCompany.CLEANUP_NONE;
        child.createdAtMillis = System.currentTimeMillis();
        child.updatedAtMillis = child.createdAtMillis;
        return child;
    }

    private static void assignRecord(KOMEHiredUnitRecord record,
            KOMEArmyCompany company, UUID actor, String actorName, long now) {
        record.companyId = company.id;
        record.companyName = company.name;
        record.companyAssignedAtMillis = now;
        record.companyAssignedBy = actor;
        record.companyAssignedByName = clean(actorName);
    }

    private void requireValidSplit(KOMEWorldData data, KOMEArmyCompany parent,
            KOMEArmyCompany child, Set<UUID> selected) {
        if (parent.units.isEmpty() || child.units.isEmpty()
                || child.units.size() != selected.size()) {
            throw new IllegalStateException("Split produced an empty or incomplete detachment.");
        }
        for (UUID unitId : selected) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (!child.units.contains(unitId) || parent.units.contains(unitId)
                    || record == null || !child.id.equals(record.companyId)) {
                throw new IllegalStateException("Split membership links did not agree.");
            }
        }
        requireCoherent(data, parent);
        requireCoherent(data, child);
    }

    private void requireValidMerge(KOMEWorldData data, KOMEArmyCompany survivor,
            String absorbedId, Collection<UUID> expectedUnits) {
        if (data.armyCompanies.containsKey(absorbedId)
                || survivor.units.size() != new LinkedHashSet<UUID>(expectedUnits).size()) {
            throw new IllegalStateException("Merge did not consume exactly two detachments.");
        }
        for (UUID unitId : expectedUnits) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (!survivor.units.contains(unitId) || record == null
                    || !survivor.id.equals(record.companyId)) {
                throw new IllegalStateException("Merge membership links did not agree.");
            }
        }
        requireCoherent(data, survivor);
    }

    private void requireCoherent(KOMEWorldData data, KOMEArmyCompany company) {
        if (coherence.assess(data, company).status
                == KOMECompanyCoherenceService.Status.INCOHERENT) {
            throw new IllegalStateException(
                "Reorganization produced an incoherent Campaign Detachment.");
        }
    }

    private static Map<UUID, NBTTagCompound> snapshotRecords(
            KOMEWorldData data, Collection<UUID> unitIds) {
        Map<UUID, NBTTagCompound> result =
            new LinkedHashMap<UUID, NBTTagCompound>();
        for (UUID unitId : new LinkedHashSet<UUID>(unitIds)) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null) result.put(unitId, record.writeToNBT());
        }
        return result;
    }

    private static void restoreRecords(KOMEWorldData data,
            Map<UUID, NBTTagCompound> snapshots) {
        for (Map.Entry<UUID, NBTTagCompound> entry : snapshots.entrySet()) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(entry.getKey());
            if (record != null) record.readFromNBT(entry.getValue());
        }
    }

    private static Result failure(Operation operation, Failure failure,
            String primaryId, String secondaryId) {
        return Result.failure(operation, failure.code, primaryId, secondaryId,
            failure.reason);
    }

    private static String safeMessage(Throwable failure) {
        if (failure == null) return "unknown failure";
        String message = clean(failure.getMessage());
        return message.length() == 0 ? failure.getClass().getSimpleName() : message;
    }

    private static String tile(String value) {
        return KOMEConquestTile.normalizeId(value);
    }

    private static String faction(String value) {
        return KOMEAlliance.normalizeFactionKey(value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
