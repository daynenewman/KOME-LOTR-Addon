package kome.common.data;

import kome.common.KOMEReflection;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-side authorization and revocation rules for kingless forces. */
public final class KOMEWartimeStewardshipService {
    private KOMEWartimeStewardshipService() {
    }

    public static boolean isAuthorized(KOMEWorldData data, String nativeFaction, String controllerFaction) {
        return data != null && !data.hasFactionKing(nativeFaction)
            && !KOMEWarService.authorizedSameSideWars(data, nativeFaction, controllerFaction).isEmpty();
    }

    public static boolean isAuthorized(KOMEWorldData data, String nativeFaction, String controllerFaction, UUID controller) {
        return KOMEWarService.supportingKingDecision(data, nativeFaction, controllerFaction, controller).allowed;
    }

    public static KOMEWarService.AuthorizationDecision controllerDecision(KOMEWorldData data,
            KOMEArmyCompany company, UUID actor) {
        if (data == null || company == null || actor == null || !actor.equals(company.temporaryController))
            return KOMEWarService.AuthorizationDecision.deny("The player is not the recorded temporary controller.");
        String controllerFaction = KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(actor));
        return KOMEWarService.supportingKingDecision(data, nativeFaction(company), controllerFaction, actor);
    }

    public static Set<String> authorizedOpponents(KOMEWorldData data, KOMEArmyCompany company) {
        Set<String> result = new HashSet<String>();
        if (data == null || company == null || !KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
            return result;
        }
        String nativeFaction = nativeFaction(company);
        for (String warId : company.authorizedWarIds) {
            KOMEWar war = data.wars.get(warId);
            if (war != null && war.isActive() && war.sideOf(nativeFaction) > 0) {
                result.addAll(war.getOpposingFactions(nativeFaction));
            }
        }
        return result;
    }

    public static boolean canEnter(KOMEWorldData data, KOMEArmyCompany company, String tileOwner, boolean retreat) {
        if (data == null || company == null) return false;
        String nativeFaction = nativeFaction(company);
        String owner = KOMEAlliance.normalizeFactionKey(tileOwner);
        if (nativeFaction.equals(owner) || data.canFactionUseMilitaryPassage(nativeFaction, owner)) return true;
        if (retreat) return false;
        return KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)
            && authorizedOpponents(data, company).contains(owner);
    }

    public static void authorizeCompany(KOMEWorldData data, KOMEArmyCompany company, String controllerFaction,
            String reason, long nowMillis) {
        authorizeCompany(data, company, controllerFaction, reason, nowMillis, true);
    }

    private static void authorizeCompany(KOMEWorldData data, KOMEArmyCompany company, String controllerFaction,
            String reason, long nowMillis, boolean auditRevalidation) {
        if (data == null || company == null) return;
        String nativeFaction = nativeFaction(company);
        KOMEWarService.AuthorizationDecision decision = KOMEWarService.supportingKingDecision(data,
            nativeFaction, controllerFaction, company.temporaryController);
        if (!decision.allowed) return;
        List<KOMEWar> wars = decision.wars;
        UUID previousController = company.temporaryController;
        List<String> previousWars = new ArrayList<String>(company.authorizedWarIds);
        boolean previouslyAuthorized = !previousWars.isEmpty();
        company.nativeFaction = nativeFaction;
        company.authorizedWarIds.clear();
        for (KOMEWar war : wars) {
            company.authorizedWarIds.add(war.id);
            upsertAuthorization(war, company, controllerFaction, nowMillis);
        }
        company.authorizationReason = reason == null ? "Friends-or-better diplomacy and same-side active war" : reason;
        company.stewardshipCreated = company.stewardshipCreated || hasStewardshipUnits(data, company);
        company.stewardshipReservation = reservation(data, company);
        company.withdrawalState = KOMEArmyCompany.CLEANUP_NONE;
        boolean materiallyChanged = (previousController == null
                ? company.temporaryController != null
                : !previousController.equals(company.temporaryController))
            || !previousWars.equals(company.authorizedWarIds);
        if (auditRevalidation && (!previouslyAuthorized || materiallyChanged)) {
            data.recordCompanyDelegationAudit(nowMillis, "STEWARDSHIP_GRANTED", company, null, "",
                company.temporaryController, company.temporaryControllerName, company.authorizationReason);
        }
        data.markDirty();
    }

    public static boolean revalidateCompany(KOMEWorldData data, KOMEArmyCompany company, long nowMillis, String reason) {
        if (data == null || company == null || !KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
            return true;
        }
        String nativeFaction = nativeFaction(company);
        String controllerFaction = company.temporaryController == null ? inferSupportingFaction(data, company)
            : KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(company.temporaryController));
        if (company.temporaryController == null && controllerFaction.length() > 0) {
            UUID replacement = data.getFactionKingId(controllerFaction);
            if (replacement != null && KOMEWarService.supportingKingDecision(data, nativeFaction, controllerFaction, replacement).allowed) {
                company.temporaryController = replacement;
                company.temporaryControllerName = data.getFactionKingName(controllerFaction);
            }
        }
        KOMEWarService.AuthorizationDecision decision = KOMEWarService.supportingKingDecision(data,
            nativeFaction, controllerFaction, company.temporaryController);
        if (decision.allowed) {
            authorizeCompany(data, company, controllerFaction, reason, nowMillis, true);
            data.recordCompanyDelegationAudit(nowMillis, "STEWARDSHIP_REVALIDATED", company, null, "",
                company.temporaryController, company.temporaryControllerName, reason);
            return true;
        }
        company.temporaryController = null;
        company.temporaryControllerName = "";
        company.delegationRevocationReason = decision.reason.length() > 0 ? decision.reason
            : reason == null ? "Wartime Stewardship authorization ended" : reason;
        company.withdrawalState = company.stewardshipCreated
            ? KOMEArmyCompany.CLEANUP_WITHDRAWAL : KOMEArmyCompany.CLEANUP_NONE;
        KOMEArmyMovementOrder order = data.armyMovements.get(company.movementOrderId);
        if (order != null && order.isMoving()) {
            order.status = KOMEArmyMovementOrder.WAR_ENDED_HALTED;
            order.accessChoice = "RETREAT_ONLY";
            order.accessLossReason = company.delegationRevocationReason;
            order.accessLostAtMillis = nowMillis;
            order.nextStepDepartureMillis = 0L;
            order.nextStepAvailableMillis = 0L;
            order.haltAfterArrival = true;
            company.status = KOMEArmyCompany.WAR_ENDED_HALTED;
        } else if (company.stewardshipCreated) {
            company.withdrawalState = isSafeTile(data, company.currentTile, nativeFaction)
                ? KOMEArmyCompany.CLEANUP_DEMOBILIZATION : KOMEArmyCompany.CLEANUP_WITHDRAWAL;
            company.status = KOMEArmyCompany.WAR_ENDED_HALTED;
        } else {
            company.clearTemporaryController(company.delegationRevocationReason);
        }
        markAuthorizationStates(data, company.id, "REVOKED", company.delegationRevocationReason, nowMillis);
        data.recordCompanyDelegationAudit(nowMillis, "STEWARDSHIP_REVOKED", company, null, "", null, "",
            company.delegationRevocationReason);
        data.markDirty();
        return false;
    }

    public static void revalidateAll(KOMEWorldData data, long nowMillis, String reason) {
        if (data == null) return;
        for (KOMEArmyCompany company : new ArrayList<KOMEArmyCompany>(data.armyCompanies.values())) {
            revalidateCompany(data, company, nowMillis, reason);
        }
    }

    public static void beginWarEnding(KOMEWorldData data, KOMEWar war, String reason, long nowMillis) {
        if (data == null || war == null) return;
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (company != null && company.authorizedWarIds.contains(war.id)) {
                revalidateCompany(data, company, nowMillis, reason);
            }
        }
        markWarAuthorizationStates(war, "WITHDRAWAL", nowMillis);
        data.markDirty();
    }

    public static boolean cleanupSafeForFinalize(KOMEWorldData data, KOMEWar war) {
        if (data == null || war == null) return false;
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (company == null || !company.authorizedWarIds.contains(war.id)) continue;
            if (company.stewardshipCreated
                    && !KOMEArmyCompany.CLEANUP_ADMIN.equals(company.withdrawalState)
                    && !KOMEArmyCompany.CLEANUP_NONE.equals(company.withdrawalState)) return false;
        }
        return true;
    }

    /** Demobilizes stewardship-created forces only after they physically reach a safe tile. */
    public static int demobilizeIfSafe(KOMEWorldData data, KOMEArmyCompany company, World world, long nowMillis) {
        if (data == null || company == null || world == null || !company.stewardshipCreated || company.isMoving()
                || KOMEArmyCompany.CLEANUP_NONE.equals(company.withdrawalState)
                || !isSafeTile(data, company.currentTile, nativeFaction(company))) return 0;
        int removed = 0;
        int pending = 0;
        for (java.util.UUID unitId : new ArrayList<java.util.UUID>(company.units)) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record == null || !KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION.equals(record.sourceType)) continue;
            KOMEPledgeReleaseTombstone tombstone = data.pledgeReleaseTombstones.get(unitId);
            if (tombstone == null) {
                tombstone = new KOMEPledgeReleaseTombstone();
                tombstone.unitUuid = unitId;
                tombstone.formerOwner = record.owner;
                tombstone.formerFaction = nativeFaction(company);
                tombstone.fundingSource = record.sourceType;
                tombstone.sourceTile = record.sourceTileId;
                tombstone.sourceFaction = record.sourceFaction;
                tombstone.sourcePlayer = record.sourcePlayer;
                tombstone.populationType = record.type;
                tombstone.populationAmount = Math.max(0, record.cost);
                tombstone.releaseReason = "Wartime Stewardship demobilization";
                tombstone.createdTimestamp = nowMillis;
                data.pledgeReleaseTombstones.put(unitId, tombstone);
            }
            if (!tombstone.populationReturned) {
                KOMETilePopulation pool = data.getFundingPool(record);
                if (pool == null) {
                    tombstone.quarantined = true;
                    data.pledgeReleaseQuarantine.put(unitId, record.writeToNBT());
                } else {
                    pool.release(record.type, record.cost);
                    tombstone.populationReturned = true;
                    record.populationReturned = true;
                }
            }
            Entity entity = findLoaded(world, unitId);
            if (entity != null) {
                KOMEReflection.setDead(entity);
                tombstone.entityRemoved = true;
            } else {
                pending++;
            }
            record.movingEntityData = null;
            record.stationedEntityData = null;
            if (tombstone.complete()) tombstone.completedTimestamp = nowMillis;
            data.removeUnitFromCompany(record);
            data.hiredUnits.remove(unitId);
            removed++;
        }
        company.clearTemporaryController("Wartime Stewardship demobilized at a safe tile");
        company.stewardshipReservation = 0;
        company.withdrawalState = pending > 0 ? KOMEArmyCompany.CLEANUP_ADMIN : KOMEArmyCompany.CLEANUP_NONE;
        company.status = KOMEArmyCompany.STATIONED;
        if (company.units.isEmpty()) data.armyCompanies.remove(company.id);
        markAuthorizationStates(data, company.id, pending > 0 ? "PENDING_ENTITY_REMOVAL" : "DEMOBILIZED",
            "Stewardship company demobilized", nowMillis);
        data.recordCompanyDelegationAudit(nowMillis, "STEWARDSHIP_DEMOBILIZED", company, null, "", null, "",
            pending > 0 ? "Pending loaded-entity removal" : "Safe-tile demobilization");
        if (removed > 0) data.markDirty();
        return removed;
    }

    private static Entity findLoaded(World world, java.util.UUID unitId) {
        if (world == null || unitId == null) return null;
        for (Object object : world.loadedEntityList) {
            if (object instanceof Entity && unitId.equals(KOMEReflection.getEntityUUID((Entity) object))) return (Entity) object;
        }
        return null;
    }

    public static boolean isSafeTile(KOMEWorldData data, String tileId, String nativeFaction) {
        KOMEConquestTile tile = data == null ? null : data.conquestTiles.get(KOMEConquestTile.normalizeId(tileId));
        if (tile == null || !tile.isClaimed()) return false;
        String owner = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
        String nativeKey = KOMEAlliance.normalizeFactionKey(nativeFaction);
        return nativeKey.equals(owner) || data.canFactionUseMilitaryPassage(nativeKey, owner);
    }

    public static String nativeFaction(KOMEArmyCompany company) {
        String nativeFaction = KOMEAlliance.normalizeFactionKey(company == null ? "" : company.nativeFaction);
        return nativeFaction.length() == 0 && company != null
            ? KOMEAlliance.normalizeFactionKey(company.faction) : nativeFaction;
    }

    /**
     * Repairs only canonical record-to-company links for an existing kingless faction's
     * defensive companies. It deliberately never changes faction, owner, funding source,
     * or equipment: those remain native-unit provenance.  Runtime entity equipment is
     * reconciled by the existing company load/rebuild path.
     */
    public static RepairResult reconcileDefensiveUnits(KOMEWorldData data, String faction, long nowMillis) {
        String nativeFaction = KOMEAlliance.normalizeFactionKey(faction);
        if (data == null || nativeFaction.length() == 0) return RepairResult.deny("A native faction is required.");
        if (data.hasFactionKing(nativeFaction)) return RepairResult.deny("The faction has a recognized ruler; kingless defensive stewardship is not active.");
        int linked = 0;
        int controllers = 0;
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (company == null || !nativeFaction.equals(nativeFaction(company))
                    || !KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)
                    || !hasStewardshipUnits(data, company)) continue;
            // Never copy a stale temporary controller. Revalidation owns revocation,
            // withdrawal, and cleanup when the authorization is no longer legal.
            if (!revalidateCompany(data, company, nowMillis, "Defensive stewardship repair revalidation")) continue;
            int companyLinksBefore = linked;
            int companyControllersBefore = controllers;
            for (java.util.UUID unitId : new ArrayList<java.util.UUID>(company.units)) {
                KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                if (record == null || !(KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION.equals(record.sourceType)
                        || "MILITARY_T3_STEWARDSHIP".equals(record.benefitSource))) continue;
                if (!company.id.equals(record.companyId)) {
                    record.companyId = company.id;
                    record.companyName = company.name;
                    linked++;
                }
                if (KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)
                        && company.temporaryController != null
                        && (!company.temporaryController.equals(record.controller)
                        || !company.controllerAuthority.equals(record.controllerAuthority))) {
                    record.controller = company.temporaryController;
                    record.controllerAuthority = company.controllerAuthority;
                    controllers++;
                }
            }
            if (linked > companyLinksBefore || controllers > companyControllersBefore) {
                data.recordCompanyDelegationAudit(nowMillis, "STEWARDSHIP_DEFENSE_RECONCILED", company, null, "",
                    company.temporaryController, company.temporaryControllerName,
                    "Canonical defensive unit/company links reconciled");
            }
        }
        if (linked > 0 || controllers > 0) data.markDirty();
        return RepairResult.allow(linked, controllers);
    }

    public static final class RepairResult {
        public final boolean allowed;
        public final String reason;
        public final int repairedLinks;
        public final int repairedControllers;

        private RepairResult(boolean allowed, String reason, int repairedLinks, int repairedControllers) {
            this.allowed = allowed;
            this.reason = reason == null ? "" : reason;
            this.repairedLinks = repairedLinks;
            this.repairedControllers = repairedControllers;
        }
        public static RepairResult deny(String reason) { return new RepairResult(false, reason, 0, 0); }
        public static RepairResult allow(int links, int controllers) {
            return new RepairResult(true, "Defensive records reconciled without changing native ownership.", links, controllers);
        }
    }

    private static boolean hasStewardshipUnits(KOMEWorldData data, KOMEArmyCompany company) {
        for (java.util.UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null && KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION.equals(record.sourceType)) return true;
        }
        return false;
    }

    private static int reservation(KOMEWorldData data, KOMEArmyCompany company) {
        int result = 0;
        for (java.util.UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null && KOMEHiredUnitRecord.SOURCE_STEWARDSHIP_RESERVATION.equals(record.sourceType)
                    && !record.populationReturned) result += Math.max(0, record.cost);
        }
        return result;
    }

    private static void upsertAuthorization(KOMEWar war, KOMEArmyCompany company, String controllerFaction, long nowMillis) {
        KOMEWar.StewardshipAuthorization found = null;
        for (KOMEWar.StewardshipAuthorization authorization : war.stewardshipAuthorizations) {
            if (company.id.equals(authorization.companyId)) { found = authorization; break; }
        }
        if (found == null) {
            found = new KOMEWar.StewardshipAuthorization();
            found.companyId = company.id;
            found.createdAtMillis = nowMillis;
            war.stewardshipAuthorizations.add(found);
        }
        found.nativeFaction = nativeFaction(company);
        found.controllerFaction = KOMEAlliance.normalizeFactionKey(controllerFaction);
        found.controller = company.temporaryController;
        found.controllerName = company.temporaryControllerName;
        found.reservation = Math.max(company.stewardshipReservation, company.totalPopulation);
        found.state = "ACTIVE";
        found.dormantReason = "";
        found.updatedAtMillis = nowMillis;
        war.lastUpdatedAtMillis = Math.max(war.lastUpdatedAtMillis, nowMillis);
    }

    private static void markAuthorizationStates(KOMEWorldData data, String companyId, String state, String reason, long nowMillis) {
        for (KOMEWar war : data.wars.values()) {
            for (KOMEWar.StewardshipAuthorization authorization : war.stewardshipAuthorizations) {
                if (companyId.equals(authorization.companyId)) {
                    authorization.state = state;
                    authorization.dormantReason = reason == null ? "" : reason;
                    if (reason != null && reason.length() > 0) {
                        authorization.revocationHistory.add(nowMillis + ": " + reason);
                        while (authorization.revocationHistory.size() > 50) authorization.revocationHistory.remove(0);
                    }
                    authorization.updatedAtMillis = nowMillis;
                }
            }
        }
    }

    private static void markWarAuthorizationStates(KOMEWar war, String state, long nowMillis) {
        for (KOMEWar.StewardshipAuthorization authorization : war.stewardshipAuthorizations) {
            authorization.state = state;
            authorization.dormantReason = state;
            authorization.updatedAtMillis = nowMillis;
        }
    }

    private static String inferSupportingFaction(KOMEWorldData data, KOMEArmyCompany company) {
        String nativeFaction = nativeFaction(company);
        String pair = company.delegationAlliancePair == null ? "" : company.delegationAlliancePair;
        String[] parts = pair.split("\\|", -1);
        if (parts.length == 2) {
            String first = KOMEAlliance.normalizeFactionKey(parts[0]);
            String second = KOMEAlliance.normalizeFactionKey(parts[1]);
            if (nativeFaction.equals(first)) return second;
            if (nativeFaction.equals(second)) return first;
        }
        for (KOMEWar war : data.wars.values()) {
            for (KOMEWar.StewardshipAuthorization authorization : war.stewardshipAuthorizations) {
                if (company.id.equals(authorization.companyId) && authorization.controllerFaction.length() > 0)
                    return authorization.controllerFaction;
            }
        }
        return "";
    }
}
