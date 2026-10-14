package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Canonical Campaign Hire boundary for admitting one new CAMPAIGN unit to a local
 * strategic detachment. Recruitment provenance and native LOTR squadron metadata
 * are deliberately not grouping authorities.
 */
public final class KOMECampaignCompanyAdmissionService {
    public static final KOMECampaignCompanyAdmissionService INSTANCE =
        new KOMECampaignCompanyAdmissionService(KOMECompanyCoherenceService.INSTANCE);

    public enum Code {
        ADMITTED_EXISTING,
        CREATED_NEW,
        INVALID_RECORD,
        ALREADY_ASSIGNED,
        ALLOCATION_FAILED,
        MUTATION_FAILED
    }

    public static final class Result {
        public final boolean success;
        public final Code code;
        public final KOMEArmyCompany company;
        public final boolean createdNew;
        public final int eligibleCandidateCount;
        public final String reason;

        private Result(boolean success, Code code, KOMEArmyCompany company,
                boolean createdNew, int eligibleCandidateCount, String reason) {
            this.success = success;
            this.code = code;
            this.company = company;
            this.createdNew = createdNew;
            this.eligibleCandidateCount = eligibleCandidateCount;
            this.reason = clean(reason);
        }

        private static Result success(Code code, KOMEArmyCompany company,
                boolean createdNew, int candidateCount) {
            return new Result(true, code, company, createdNew, candidateCount, "");
        }

        private static Result failure(Code code, int candidateCount, String reason) {
            return new Result(false, code, null, false, candidateCount, reason);
        }
    }

    private final KOMECompanyCoherenceService coherence;

    KOMECampaignCompanyAdmissionService(KOMECompanyCoherenceService coherence) {
        if (coherence == null) throw new IllegalArgumentException("coherence");
        this.coherence = coherence;
    }

    public Result admit(KOMEWorldData data, KOMEHiredUnitRecord record,
            String ownerName, String strategicTile) {
        if (data == null) {
            return Result.failure(Code.INVALID_RECORD, 0,
                "Authoritative KOME world data is unavailable.");
        }
        if (KOMESeasonResetService.active(data)) return Result.failure(Code.INVALID_RECORD, 0, "Campaign hiring is paused during season reset");
        synchronized (data) {
            String tile = KOMEConquestTile.normalizeId(strategicTile);
            String invalid = validateNewMember(data, record, tile);
            if (invalid.length() > 0) {
                return Result.failure(hasExistingMembership(data, record)
                    ? Code.ALREADY_ASSIGNED : Code.INVALID_RECORD, 0, invalid);
            }
            String faction = data.resolveCampaignCompanyFaction(record);
            KOMEGovernanceService.Decision governance = KOMEGovernanceService.militaryAction(data, record.owner, faction);
            if (!governance.allowed) return Result.failure(Code.INVALID_RECORD, 0, governance.reason);
            if (faction.length() == 0) {
                return Result.failure(Code.INVALID_RECORD, 0,
                    "The campaign unit has no authoritative strategic faction.");
            }

            List<KOMEArmyCompany> candidates = eligibleCandidates(data, record,
                tile, faction);
            if (candidates.size() == 1
                    && isEligibleCandidate(data, candidates.get(0), record,
                        tile, faction)) {
                return attach(data, record, candidates.get(0), ownerName,
                    tile, faction, false, candidates.size());
            }
            return createAndAttach(data, record, ownerName, tile, faction,
                candidates.size());
        }
    }

    private List<KOMEArmyCompany> eligibleCandidates(KOMEWorldData data,
            KOMEHiredUnitRecord record, String tile, String faction) {
        List<KOMEArmyCompany> result = new ArrayList<KOMEArmyCompany>();
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (isEligibleCandidate(data, company, record, tile, faction)) {
                result.add(company);
            }
        }
        return result;
    }

    private boolean isEligibleCandidate(KOMEWorldData data, KOMEArmyCompany company,
            KOMEHiredUnitRecord record, String tile, String faction) {
        if (company == null || company.owner == null
                || !company.owner.equals(record.owner) || company.units.isEmpty()) {
            return false;
        }
        if (!tile.equals(KOMEConquestTile.normalizeId(company.currentTile))) return false;
        if (!faction.equals(KOMEAlliance.normalizeFactionKey(company.faction))) return false;
        if (!faction.equals(KOMEWartimeStewardshipService.nativeFaction(company))) return false;
        if (!KOMEArmyCompany.STATIONED.equals(company.status)) return false;
        if (KOMEConflictMovementService.isActivelyCommitted(data, company.id)) return false;
        if (!clean(company.movementOrderId).isEmpty()
                || hasActiveOrderForCompany(data, company.id)) return false;
        if (!KOMEArmyCompany.CLEANUP_NONE.equals(company.withdrawalState)) return false;
        if (company.transferRecipient != null || company.transferOfferedBy != null) return false;
        return coherence.assess(data, company).status
            != KOMECompanyCoherenceService.Status.INCOHERENT;
    }

    private static boolean hasActiveOrderForCompany(KOMEWorldData data,
            String companyId) {
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order != null && clean(companyId).equals(clean(order.companyId))
                    && order.isMoving()) return true;
        }
        return false;
    }

    private Result createAndAttach(KOMEWorldData data, KOMEHiredUnitRecord record,
            String ownerName, String tile, String faction, int candidateCount) {
        String id;
        try {
            id = data.nextCampaignCompanyId();
        } catch (RuntimeException failure) {
            return Result.failure(Code.ALLOCATION_FAILED, candidateCount,
                "A stable Campaign Detachment ID could not be allocated: "
                    + safeMessage(failure));
        }
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = id;
        company.owner = record.owner;
        company.ownerName = clean(ownerName).length() == 0
            ? data.safePlayerName(record.owner) : clean(ownerName);
        company.faction = faction;
        company.nativeFaction = faction;
        company.currentTile = tile;
        company.sourceTileId = KOMEConquestTile.normalizeId(record.sourceTileId);
        if (company.sourceTileId.length() == 0) company.sourceTileId = tile;
        company.name = data.defaultCampaignCompanyName(tile);
        company.lotrCompanyValue = clean(record.lotrCompanyValue);
        company.source = KOMEArmyCompany.SOURCE_CAMPAIGN_RECRUITMENT;
        company.status = KOMEArmyCompany.STATIONED;
        company.movementOrderId = "";
        company.createdAtMillis = System.currentTimeMillis();
        company.updatedAtMillis = company.createdAtMillis;
        data.armyCompanies.put(company.id, company);
        Result result = attach(data, record, company, ownerName, tile, faction,
            true, candidateCount);
        if (!result.success) data.armyCompanies.remove(company.id);
        return result;
    }

    private Result attach(KOMEWorldData data, KOMEHiredUnitRecord record,
            KOMEArmyCompany company, String ownerName, String strategicTile,
            String faction, boolean createdNew, int candidateCount) {
        String invalid = validateNewMember(data, record, strategicTile);
        if (invalid.length() > 0 || !createdNew && !isEligibleCandidate(data,
                company, record, strategicTile, faction)) {
            return Result.failure(hasExistingMembership(data, record)
                ? Code.ALREADY_ASSIGNED : Code.INVALID_RECORD, candidateCount,
                invalid.length() == 0
                    ? "The local Campaign Detachment changed before admission."
                    : invalid);
        }

        NBTTagCompound companyBefore = company.writeToNBT();
        String priorCompanyId = clean(record.companyId);
        String priorCompanyName = clean(record.companyName);
        long priorAssignedAt = record.companyAssignedAtMillis;
        UUID priorAssignedBy = record.companyAssignedBy;
        String priorAssignedByName = clean(record.companyAssignedByName);
        try {
            company.units.add(record.entity);
            record.companyId = company.id;
            record.companyName = company.name;
            record.companyAssignedAtMillis = System.currentTimeMillis();
            record.companyAssignedBy = record.owner;
            record.companyAssignedByName = clean(ownerName).length() == 0
                ? company.ownerName : clean(ownerName);
            data.recalculateCampaignCompanyComposition(company);
            if (createdNew) KOMEMovementDayService.initializeNewCompany(company, company.createdAtMillis);
            else KOMEMovementDayService.cap(company);
            if (!company.units.contains(record.entity)
                    || !company.id.equals(record.companyId)) {
                throw new IllegalStateException("Company and record membership did not agree.");
            }
            data.markDirty();
            return Result.success(createdNew ? Code.CREATED_NEW
                : Code.ADMITTED_EXISTING, company, createdNew, candidateCount);
        } catch (RuntimeException failure) {
            company.readFromNBT(companyBefore);
            record.companyId = priorCompanyId;
            record.companyName = priorCompanyName;
            record.companyAssignedAtMillis = priorAssignedAt;
            record.companyAssignedBy = priorAssignedBy;
            record.companyAssignedByName = priorAssignedByName;
            return Result.failure(Code.MUTATION_FAILED, candidateCount,
                "Campaign Detachment admission was rolled back: "
                    + safeMessage(failure));
        }
    }

    private static String validateNewMember(KOMEWorldData data,
            KOMEHiredUnitRecord record, String tile) {
        if (record == null || record.entity == null || record.owner == null) {
            return "The campaign unit record is incomplete.";
        }
        if (data.hiredUnits.get(record.entity) != record) {
            return "The campaign unit is not the authoritative tracked record.";
        }
        if (!KOMEHiredUnitClassification.isCampaignUnit(record)) {
            return "Only explicit CAMPAIGN units may enter a Campaign Detachment.";
        }
        if (record.farmhand || record.type != KOMEPopulationType.OFFENSIVE) {
            return "The unit is not an eligible campaign combat unit.";
        }
        if (record.isMoving()) return "The unit already has strategic movement state.";
        if (tile.length() == 0
                || !tile.equals(KOMEConquestTile.normalizeId(record.currentTile))) {
            return "The unit is not stationed at the authoritative recruitment tile.";
        }
        if (hasExistingMembership(data, record)) {
            return "The unit is already assigned to a Campaign Detachment.";
        }
        return "";
    }

    private static boolean hasExistingMembership(KOMEWorldData data,
            KOMEHiredUnitRecord record) {
        if (record == null) return false;
        if (!clean(record.companyId).isEmpty()) return true;
        if (record.entity == null) return false;
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (company != null && company.units.contains(record.entity)) return true;
        }
        return false;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure == null ? "unknown failure" : failure.getMessage();
        return clean(message == null || message.length() == 0
            ? failure.getClass().getSimpleName() : message);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
