package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Canonical, bounded repair of persisted Campaign Detachment membership links.
 * Recruitment provenance, native LOTR squadron metadata, and physical position
 * are deliberately never grouping authorities.
 */
public final class KOMECompanyReconciliationService {
    public static final KOMECompanyReconciliationService INSTANCE =
        new KOMECompanyReconciliationService(KOMECompanyCoherenceService.INSTANCE);

    public enum IssueCode {
        INVALID_MEMBER_REMOVED, DUPLICATE_REFERENCE_REMOVED,
        RECORD_LINK_REPAIRED, COMPANY_LINK_REPAIRED,
        UNRESOLVED_RECORD_LINK_CLEARED, AMBIGUOUS_MEMBERSHIP_CLEARED,
        EMPTY_COMPANY_REMOVED, EMPTY_COMPANY_RETAINED_FOR_MOVEMENT,
        MOVEMENT_LINK_REPAIRED, MOVEMENT_COHORT_CONFLICT,
        PHYSICAL_TILE_CONTRADICTION, STRATEGIC_INCOHERENCE
    }

    public static final class Issue {
        public final IssueCode code;
        public final UUID unitId;
        public final String companyId;
        public final String detail;

        private Issue(IssueCode code, UUID unitId, String companyId, String detail) {
            this.code = code;
            this.unitId = unitId;
            this.companyId = clean(companyId);
            this.detail = clean(detail);
        }
    }

    public static final class Result {
        public final int retainedMemberships, recordLinksRepaired, companyLinksRepaired;
        public final int duplicateMembershipsRemoved, ambiguousUnitsUnassigned;
        public final int invalidMembersRemoved, emptyCompaniesRemoved;
        public final int emptyCompaniesRetainedForMovement, totalsRecomputed;
        public final int movementLinksRepaired, movementConflicts;
        public final int incoherentCompanies, physicalContradictions, unknownPhysicalMembers;
        public final List<Issue> issues;
        private final boolean changed;

        private Result(Builder builder) {
            retainedMemberships = builder.retainedMemberships;
            recordLinksRepaired = builder.recordLinksRepaired;
            companyLinksRepaired = builder.companyLinksRepaired;
            duplicateMembershipsRemoved = builder.duplicateMembershipsRemoved;
            ambiguousUnitsUnassigned = builder.ambiguousUnitsUnassigned;
            invalidMembersRemoved = builder.invalidMembersRemoved;
            emptyCompaniesRemoved = builder.emptyCompaniesRemoved;
            emptyCompaniesRetainedForMovement = builder.emptyCompaniesRetainedForMovement;
            totalsRecomputed = builder.totalsRecomputed;
            movementLinksRepaired = builder.movementLinksRepaired;
            movementConflicts = builder.movementConflicts;
            incoherentCompanies = builder.incoherentCompanies;
            physicalContradictions = builder.physicalContradictions;
            unknownPhysicalMembers = builder.unknownPhysicalMembers;
            issues = Collections.unmodifiableList(new ArrayList<Issue>(builder.issues));
            changed = builder.changed;
        }

        public boolean changed() { return changed; }
    }

    private static final class Builder {
        int retainedMemberships, recordLinksRepaired, companyLinksRepaired;
        int duplicateMembershipsRemoved, ambiguousUnitsUnassigned;
        int invalidMembersRemoved, emptyCompaniesRemoved;
        int emptyCompaniesRetainedForMovement, totalsRecomputed;
        int movementLinksRepaired, movementConflicts;
        int incoherentCompanies, physicalContradictions, unknownPhysicalMembers;
        boolean changed;
        final List<Issue> issues = new ArrayList<Issue>();

        void issue(IssueCode code, UUID unitId, String companyId, String detail) {
            issues.add(new Issue(code, unitId, companyId, detail));
        }

        Result build() { return new Result(this); }
    }

    private final KOMECompanyCoherenceService coherence;

    KOMECompanyReconciliationService(KOMECompanyCoherenceService coherence) {
        if (coherence == null) throw new IllegalArgumentException("coherence");
        this.coherence = coherence;
    }

    public Result reconcile(KOMEWorldData data) { return reconcile(data, null); }

    /** Null owner reconciles the complete canonical company collection. */
    public Result reconcile(KOMEWorldData data, UUID onlyOwner) {
        if (data == null) throw new IllegalArgumentException("data");
        synchronized (data) {
            data.ensureWritable();
            Builder result = new Builder();
            Set<String> touchedCompanies = new HashSet<String>();
            removeIntrinsicInvalidMembers(data, onlyOwner, result, touchedCompanies);
            reconcileRecordMemberships(data, onlyOwner, result, touchedCompanies);
            reconcileMovementLinks(data, onlyOwner, result, touchedCompanies);
            removeEmptyCompanies(data, onlyOwner, result, touchedCompanies);
            recomputeTotals(data, onlyOwner, result, touchedCompanies);
            assessRemainingCompanies(data, onlyOwner, result, touchedCompanies);
            if (result.changed) data.markDirty();
            return result.build();
        }
    }

    private static void removeIntrinsicInvalidMembers(KOMEWorldData data, UUID onlyOwner,
            Builder result, Set<String> touchedCompanies) {
        for (KOMEArmyCompany company : sortedCompanies(data)) {
            Set<UUID> seen = new LinkedHashSet<UUID>();
            for (UUID unitId : new ArrayList<UUID>(company.units)) {
                KOMEHiredUnitRecord record = unitId == null ? null : data.hiredUnits.get(unitId);
                if (!inScope(onlyOwner, company, record)) continue;
                if (unitId != null && !seen.add(unitId)) {
                    removeOne(company.units, unitId);
                    changedCompany(company, touchedCompanies, result);
                    result.duplicateMembershipsRemoved++;
                    result.issue(IssueCode.DUPLICATE_REFERENCE_REMOVED, unitId, company.id,
                        "Removed a duplicate member entry inside one Campaign Detachment.");
                } else if (!eligibleRecord(unitId, record)) {
                    removeAll(company.units, unitId);
                    changedCompany(company, touchedCompanies, result);
                    result.invalidMembersRemoved++;
                    result.issue(IssueCode.INVALID_MEMBER_REMOVED, unitId, company.id,
                        invalidMemberReason(record));
                    if (record != null && inRecordScope(onlyOwner, record)
                            && clean(company.id).equals(clean(record.companyId))) clearRecordCompany(record);
                }
            }
        }
    }

    private void reconcileRecordMemberships(KOMEWorldData data, UUID onlyOwner,
            Builder result, Set<String> touchedCompanies) {
        for (UUID unitId : allKnownUnitIds(data)) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            List<KOMEArmyCompany> references = containingCompanies(data, unitId);
            if (!inScope(onlyOwner, record, references)) continue;
            if (!eligibleRecord(unitId, record)) {
                removeReferences(references, unitId, null, touchedCompanies, result,
                    IssueCode.INVALID_MEMBER_REMOVED);
                if (record != null && inRecordScope(onlyOwner, record)) {
                    if (clean(record.companyId).length() > 0 || clean(record.companyName).length() > 0) {
                        clearRecordCompany(record);
                        result.changed = true;
                    }
                    clearRecordMovement(record, result);
                }
                continue;
            }

            String explicitId = clean(record.companyId);
            KOMEArmyCompany explicit = data.armyCompanies.get(explicitId);
            KOMEArmyCompany chosen = null;
            if (explicit != null) {
                if (structurallyCompatible(data, explicit, record)
                        && movementCompatible(data, explicit, record)) {
                    chosen = explicit;
                } else {
                    result.ambiguousUnitsUnassigned++;
                    result.issue(IssueCode.AMBIGUOUS_MEMBERSHIP_CLEARED, unitId,
                        explicitId, "The record's explicit detachment was structurally or movement-incompatible; no replacement was inferred.");
                }
            } else {
                List<KOMEArmyCompany> plausible = new ArrayList<KOMEArmyCompany>();
                for (KOMEArmyCompany company : references)
                    if (recoverableMembership(data, company, record)) plausible.add(company);
                if (plausible.size() == 1) {
                    chosen = plausible.get(0);
                } else if (plausible.size() > 1) {
                    result.ambiguousUnitsUnassigned++;
                    result.issue(IssueCode.AMBIGUOUS_MEMBERSHIP_CLEARED, unitId, "",
                        "Multiple plausible detachments stored this unit; all strategic memberships were removed.");
                } else if (explicitId.length() > 0) {
                    result.issue(IssueCode.UNRESOLVED_RECORD_LINK_CLEARED, unitId,
                        explicitId, "The record referenced no existing recoverable detachment.");
                }
            }

            if (chosen == null) {
                removeReferences(references, unitId, null, touchedCompanies, result,
                    IssueCode.DUPLICATE_REFERENCE_REMOVED);
                if (explicitId.length() > 0 || clean(record.companyName).length() > 0) {
                    clearRecordCompany(record);
                    result.changed = true;
                }
                clearRecordMovement(record, result);
                continue;
            }

            if (!chosen.units.contains(unitId)) {
                chosen.units.add(unitId);
                changedCompany(chosen, touchedCompanies, result);
                result.companyLinksRepaired++;
                result.issue(IssueCode.COMPANY_LINK_REPAIRED, unitId, chosen.id,
                    "Restored the company-side member link from the explicit record identity.");
            }
            while (occurrences(chosen.units, unitId) > 1) {
                removeOne(chosen.units, unitId);
                changedCompany(chosen, touchedCompanies, result);
                result.duplicateMembershipsRemoved++;
            }
            removeReferences(references, unitId, chosen, touchedCompanies, result,
                IssueCode.DUPLICATE_REFERENCE_REMOVED);
            if (!clean(chosen.id).equals(explicitId)
                    || !clean(chosen.name).equals(clean(record.companyName))) {
                record.companyId = clean(chosen.id);
                record.companyName = KOMEHiredUnitRecord.normalizeCompanyName(chosen.name);
                result.recordLinksRepaired++;
                result.changed = true;
                result.issue(IssueCode.RECORD_LINK_REPAIRED, unitId, chosen.id,
                    "Restored the record-side link to its sole recoverable detachment.");
            }
            result.retainedMemberships++;
        }
    }

    private boolean recoverableMembership(KOMEWorldData data, KOMEArmyCompany company,
            KOMEHiredUnitRecord record) {
        if (!structurallyCompatible(data, company, record)
                || !movementCompatible(data, company, record)) return false;
        String companyTile = KOMEConquestTile.normalizeId(company.currentTile);
        if (companyTile.length() == 0
                || !companyTile.equals(KOMEConquestTile.normalizeId(record.currentTile))) return false;
        KOMECompanyCoherenceService.Assessment assessment = coherence.assess(data, company);
        KOMECompanyCoherenceService.MemberAssessment member = assessment.member(record.entity);
        if (member == null || member.physicalAgreement
                == KOMECompanyCoherenceService.PhysicalAgreement.DISAGREES) return false;
        for (KOMECompanyCoherenceService.MemberAssessment candidate : assessment.members) {
            KOMEHiredUnitRecord candidateRecord = data.hiredUnits.get(candidate.entityId);
            if (!eligibleRecord(candidate.entityId, candidateRecord)
                    || !structurallyCompatible(data, company, candidateRecord)
                    || !companyTile.equals(KOMEConquestTile.normalizeId(candidateRecord.currentTile))
                    || candidate.physicalAgreement
                        == KOMECompanyCoherenceService.PhysicalAgreement.DISAGREES
                    || !movementCompatible(data, company, candidateRecord)) return false;
            String candidateExplicit = clean(candidateRecord.companyId);
            if (candidateExplicit.length() > 0 && data.armyCompanies.containsKey(candidateExplicit)
                    && !clean(company.id).equals(candidateExplicit)) return false;
        }
        return true;
    }

    private static void reconcileMovementLinks(KOMEWorldData data, UUID onlyOwner,
            Builder result, Set<String> touchedCompanies) {
        for (KOMEArmyCompany company : sortedCompanies(data)) {
            if (!inScope(onlyOwner, company, null) && !touchedCompanies.contains(company.id)) continue;
            List<KOMEArmyMovementOrder> active = activeOrdersForCompany(data, company.id);
            if (active.size() > 1) {
                movementConflict(result, company.id,
                    "Multiple active movement orders reference this detachment; cohorts were not mutated.");
                continue;
            }
            if (active.isEmpty()) {
                if (clean(company.movementOrderId).length() > 0
                        || KOMEArmyCompany.MOVING.equals(company.status)) {
                    company.movementOrderId = "";
                    if (KOMEArmyCompany.MOVING.equals(company.status))
                        company.status = KOMEArmyCompany.STATIONED;
                    changedCompany(company, touchedCompanies, result);
                    movementRepair(result, null, company.id,
                        "Cleared a missing, terminal, or foreign company movement-order link.");
                }
                for (UUID unitId : new ArrayList<UUID>(company.units)) {
                    KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                    if (record != null && clean(record.movementOrderId).length() > 0)
                        clearRecordMovement(record, result);
                }
                continue;
            }

            KOMEArmyMovementOrder order = active.get(0);
            Set<UUID> companyCohort = uniqueUnits(company.units);
            Set<UUID> orderCohort = uniqueUnits(order.units);
            if (!companyCohort.equals(orderCohort)) {
                movementConflict(result, company.id, "Movement order " + clean(order.id)
                    + " and detachment membership disagree; neither cohort was expanded or reassigned.");
                continue;
            }
            if (!clean(order.id).equals(clean(company.movementOrderId))) {
                company.movementOrderId = clean(order.id);
                changedCompany(company, touchedCompanies, result);
                movementRepair(result, null, company.id,
                    "Restored the company-side link to its exact active movement cohort.");
            }
            for (UUID unitId : companyCohort) {
                KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                if (record != null && !clean(order.id).equals(clean(record.movementOrderId))) {
                    record.movementOrderId = clean(order.id);
                    result.changed = true;
                    movementRepair(result, unitId, company.id,
                        "Restored the record-side link to an exact active movement cohort.");
                }
            }
        }

        for (KOMEHiredUnitRecord record : sortedRecords(data)) {
            if (!inRecordScope(onlyOwner, record)) continue;
            if (data.armyCompanies.get(clean(record.companyId)) == null
                    && clean(record.movementOrderId).length() > 0)
                clearRecordMovement(record, result);
        }
    }

    private static void removeEmptyCompanies(KOMEWorldData data, UUID onlyOwner,
            Builder result, Set<String> touchedCompanies) {
        for (KOMEArmyCompany company : sortedCompanies(data)) {
            if (!company.units.isEmpty()
                    || !inScope(onlyOwner, company, null) && !touchedCompanies.contains(company.id)) continue;
            if (!activeOrdersForCompany(data, company.id).isEmpty()) {
                result.emptyCompaniesRetainedForMovement++;
                result.issue(IssueCode.EMPTY_COMPANY_RETAINED_FOR_MOVEMENT, null,
                    company.id, "Retained an empty detachment because an active movement cohort still references it.");
                continue;
            }
            data.armyCompanies.remove(company.id);
            result.emptyCompaniesRemoved++;
            result.changed = true;
            result.issue(IssueCode.EMPTY_COMPANY_REMOVED, null, company.id,
                "Removed an empty inactive Campaign Detachment; its stable ID remains consumed.");
        }
    }

    private static void recomputeTotals(KOMEWorldData data, UUID onlyOwner,
            Builder result, Set<String> touchedCompanies) {
        for (KOMEArmyCompany company : sortedCompanies(data)) {
            if (!inScope(onlyOwner, company, null) && !touchedCompanies.contains(company.id)) continue;
            int total = 0, mounted = 0, ground = 0;
            for (UUID unitId : uniqueUnits(company.units)) {
                KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                if (!eligibleRecord(unitId, record)
                        || !clean(company.id).equals(clean(record.companyId))) continue;
                int cost = Math.max(0, record.cost);
                total += cost;
                if (record.mounted) mounted += cost;
                else ground += cost;
            }
            result.totalsRecomputed++;
            if (company.totalPopulation != total || company.mountedPopulation != mounted
                    || company.groundPopulation != ground) {
                company.totalPopulation = total;
                company.mountedPopulation = mounted;
                company.groundPopulation = ground;
                changedCompany(company, touchedCompanies, result);
            }
        }
    }

    private void assessRemainingCompanies(KOMEWorldData data, UUID onlyOwner,
            Builder result, Set<String> touchedCompanies) {
        for (KOMEArmyCompany company : sortedCompanies(data)) {
            if (!inScope(onlyOwner, company, null) && !touchedCompanies.contains(company.id)) continue;
            KOMECompanyCoherenceService.Assessment assessment = coherence.assess(data, company);
            result.unknownPhysicalMembers += assessment.physicallyUnknownMembers;
            result.physicalContradictions += assessment.physicallyContradictoryMembers;
            if (assessment.status == KOMECompanyCoherenceService.Status.INCOHERENT) {
                result.incoherentCompanies++;
                result.issue(IssueCode.STRATEGIC_INCOHERENCE, null, company.id,
                    "The retained detachment remains diagnostically incoherent; no tile, class, or cohort was inferred.");
            }
            for (KOMECompanyCoherenceService.Issue issue : assessment.issues) {
                if (issue.code == KOMECompanyCoherenceService.IssueCode.PHYSICAL_TILE_MISMATCH)
                    result.issue(IssueCode.PHYSICAL_TILE_CONTRADICTION, issue.memberId,
                        company.id, issue.detail);
            }
        }
    }

    private static boolean structurallyCompatible(KOMEWorldData data,
            KOMEArmyCompany company, KOMEHiredUnitRecord record) {
        if (company == null || record == null || company.owner == null
                || !company.owner.equals(record.owner)) return false;
        String expected = KOMEAlliance.normalizeFactionKey(data.resolveCampaignCompanyFaction(record));
        if (expected.length() == 0) return true;
        return expected.equals(KOMEAlliance.normalizeFactionKey(company.faction))
            && expected.equals(KOMEWartimeStewardshipService.nativeFaction(company));
    }

    private static boolean movementCompatible(KOMEWorldData data,
            KOMEArmyCompany company, KOMEHiredUnitRecord record) {
        List<KOMEArmyMovementOrder> active = activeOrdersForCompany(data, company.id);
        if (active.size() > 1) return false;
        if (active.size() == 1) {
            KOMEArmyMovementOrder order = active.get(0);
            return clean(order.id).equals(clean(record.movementOrderId))
                && order.units.contains(record.entity);
        }
        String recordOrder = clean(record.movementOrderId);
        if (recordOrder.length() == 0) return true;
        KOMEArmyMovementOrder order = data.armyMovements.get(recordOrder);
        return order == null || !order.isMoving();
    }

    private static List<KOMEArmyMovementOrder> activeOrdersForCompany(
            KOMEWorldData data, String companyId) {
        List<KOMEArmyMovementOrder> result = new ArrayList<KOMEArmyMovementOrder>();
        String id = clean(companyId);
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order != null && order.isMoving() && id.equals(clean(order.companyId)))
                result.add(order);
        }
        Collections.sort(result, new Comparator<KOMEArmyMovementOrder>() {
            @Override public int compare(KOMEArmyMovementOrder left, KOMEArmyMovementOrder right) {
                return clean(left.id).compareTo(clean(right.id));
            }
        });
        return result;
    }

    private static void clearRecordMovement(KOMEHiredUnitRecord record, Builder result) {
        if (record == null || clean(record.movementOrderId).length() == 0
                && record.movingEntityData == null) return;
        if (record.movingEntityData != null) {
            if (record.stationedEntityData == null)
                record.stationedEntityData = (NBTTagCompound) record.movingEntityData.copy();
            record.movingEntityData = null;
        }
        record.movementOrderId = "";
        result.changed = true;
        movementRepair(result, record.entity, record.companyId,
            "Cleared a record movement link that no coherent detachment cohort could authorize.");
    }

    private static void movementRepair(Builder result, UUID unitId, String companyId,
            String detail) {
        result.movementLinksRepaired++;
        result.issue(IssueCode.MOVEMENT_LINK_REPAIRED, unitId, companyId, detail);
    }

    private static void movementConflict(Builder result, String companyId, String detail) {
        result.movementConflicts++;
        result.issue(IssueCode.MOVEMENT_COHORT_CONFLICT, null, companyId, detail);
    }

    private static void clearRecordCompany(KOMEHiredUnitRecord record) {
        if (record == null) return;
        record.companyId = "";
        record.companyName = "";
    }

    private static void removeReferences(List<KOMEArmyCompany> references, UUID unitId,
            KOMEArmyCompany keep, Set<String> touchedCompanies, Builder result,
            IssueCode issueCode) {
        for (KOMEArmyCompany company : references) {
            if (company == keep) continue;
            int removed = removeAll(company.units, unitId);
            if (removed <= 0) continue;
            changedCompany(company, touchedCompanies, result);
            result.duplicateMembershipsRemoved += removed;
            result.issue(issueCode, unitId, company.id,
                "Removed a stale or non-authoritative company-side membership reference.");
        }
    }

    private static void changedCompany(KOMEArmyCompany company,
            Set<String> touchedCompanies, Builder result) {
        company.updatedAtMillis = System.currentTimeMillis();
        touchedCompanies.add(company.id);
        result.changed = true;
    }

    private static boolean eligibleRecord(UUID mapId, KOMEHiredUnitRecord record) {
        return record != null && mapId != null && mapId.equals(record.entity)
            && record.owner != null && KOMEHiredUnitClassification.isCampaignUnit(record)
            && !record.farmhand && record.type == KOMEPopulationType.OFFENSIVE;
    }

    private static String invalidMemberReason(KOMEHiredUnitRecord record) {
        if (record == null) return "Removed a member UUID with no tracked hired-unit record.";
        if (!KOMEHiredUnitClassification.isCampaignUnit(record))
            return "Removed an ORDINARY member from strategic detachment membership.";
        if (record.farmhand) return "Removed a farmhand from strategic detachment membership.";
        if (record.type != KOMEPopulationType.OFFENSIVE)
            return "Removed a non-offensive unit from strategic detachment membership.";
        return "Removed an incomplete or identity-mismatched hired-unit record.";
    }

    private static boolean inScope(UUID onlyOwner, KOMEArmyCompany company,
            KOMEHiredUnitRecord record) {
        return onlyOwner == null || company != null && onlyOwner.equals(company.owner)
            || record != null && onlyOwner.equals(record.owner);
    }

    private static boolean inScope(UUID onlyOwner, KOMEHiredUnitRecord record,
            List<KOMEArmyCompany> references) {
        if (onlyOwner == null || inRecordScope(onlyOwner, record)) return true;
        for (KOMEArmyCompany company : references)
            if (onlyOwner.equals(company.owner)) return true;
        return false;
    }

    private static boolean inRecordScope(UUID onlyOwner, KOMEHiredUnitRecord record) {
        return onlyOwner == null || record != null && onlyOwner.equals(record.owner);
    }

    private static List<KOMEArmyCompany> containingCompanies(KOMEWorldData data, UUID unitId) {
        List<KOMEArmyCompany> result = new ArrayList<KOMEArmyCompany>();
        for (KOMEArmyCompany company : data.armyCompanies.values())
            if (company != null && company.units.contains(unitId)) result.add(company);
        sortCompanies(result);
        return result;
    }

    private static List<KOMEArmyCompany> sortedCompanies(KOMEWorldData data) {
        List<KOMEArmyCompany> result = new ArrayList<KOMEArmyCompany>();
        for (KOMEArmyCompany company : data.armyCompanies.values())
            if (company != null) result.add(company);
        sortCompanies(result);
        return result;
    }

    private static void sortCompanies(List<KOMEArmyCompany> companies) {
        Collections.sort(companies, new Comparator<KOMEArmyCompany>() {
            @Override public int compare(KOMEArmyCompany left, KOMEArmyCompany right) {
                return clean(left.id).compareTo(clean(right.id));
            }
        });
    }

    private static List<KOMEHiredUnitRecord> sortedRecords(KOMEWorldData data) {
        List<KOMEHiredUnitRecord> result =
            new ArrayList<KOMEHiredUnitRecord>(data.hiredUnits.values());
        Collections.sort(result, new Comparator<KOMEHiredUnitRecord>() {
            @Override public int compare(KOMEHiredUnitRecord left, KOMEHiredUnitRecord right) {
                return String.valueOf(left == null ? null : left.entity)
                    .compareTo(String.valueOf(right == null ? null : right.entity));
            }
        });
        return result;
    }

    private static List<UUID> allKnownUnitIds(KOMEWorldData data) {
        Set<UUID> ids = new HashSet<UUID>(data.hiredUnits.keySet());
        for (KOMEArmyCompany company : data.armyCompanies.values())
            if (company != null) ids.addAll(company.units);
        ids.remove(null);
        List<UUID> result = new ArrayList<UUID>(ids);
        Collections.sort(result, new Comparator<UUID>() {
            @Override public int compare(UUID left, UUID right) {
                return left.toString().compareTo(right.toString());
            }
        });
        return result;
    }

    private static Set<UUID> uniqueUnits(List<UUID> units) {
        Set<UUID> result = new LinkedHashSet<UUID>();
        if (units != null) result.addAll(units);
        result.remove(null);
        return result;
    }

    private static int occurrences(List<UUID> values, UUID value) {
        int count = 0;
        for (UUID candidate : values)
            if (value == null ? candidate == null : value.equals(candidate)) count++;
        return count;
    }

    private static void removeOne(List<UUID> values, UUID value) {
        for (int i = values.size() - 1; i >= 0; i--) {
            UUID candidate = values.get(i);
            if (value == null ? candidate == null : value.equals(candidate)) {
                values.remove(i);
                return;
            }
        }
    }

    private static int removeAll(List<UUID> values, UUID value) {
        int removed = 0;
        for (int i = values.size() - 1; i >= 0; i--) {
            UUID candidate = values.get(i);
            if (value == null ? candidate == null : value.equals(candidate)) {
                values.remove(i);
                removed++;
            }
        }
        return removed;
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
