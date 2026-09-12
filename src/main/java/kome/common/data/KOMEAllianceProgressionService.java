package kome.common.data;

/**
 * Directional four-stage alliance progression.
 *
 * Ordinary gameplay events refresh readiness only. A recognized acting king must
 * explicitly claim the next stage.
 */
public final class KOMEAllianceProgressionService {
    public static final String ALLIED_TRADES = "allied.trades";
    public static final String ELIGIBLE_KILLS = "military.kills";
    public static final String OFFENSIVE_CAPACITY_MAX = "military.population.max";

    private KOMEAllianceProgressionService() {
    }

    /** Compatibility event hook: update fixed milestones but never auto-claim a stage. */
    public static boolean refreshFactionCompletion(KOMEWorldData data, KOMEAlliance alliance, String faction,
            String ignoredType, long worldTime) {
        return refreshCurrentStageReadiness(data, alliance, faction, System.currentTimeMillis());
    }

    public static boolean refreshCurrentStageReadiness(KOMEWorldData data, KOMEAlliance alliance,
            String faction, long nowMillis) {
        if (data == null || alliance == null
                || alliance.getRelationshipStatus() != KOMEAllianceTrackStatus.ACTIVE) return false;
        String acting = KOMEAlliance.normalizeFactionKey(faction);
        KOMEAllianceStageProgress progress = alliance.getStageProgress(acting);
        if (progress == null || progress.stage >= 4) return false;
        int target = progress.stage + 1;
        boolean before = progress.isFixedComplete(target);
        if (fixedMilestoneComplete(data, alliance, acting, target)) {
            progress.markFixedComplete(target, nowMillis);
        }
        if (!before && progress.isFixedComplete(target)) {
            data.markDirty();
            return true;
        }
        return false;
    }

    public static Decision claimNextStage(KOMEWorldData data, KOMEAlliance alliance, String faction,
            boolean actingKing, long worldTime, long nowMillis) {
        if (data == null || alliance == null
                || alliance.getRelationshipStatus() != KOMEAllianceTrackStatus.ACTIVE) {
            return Decision.deny("No active formal alliance exists.");
        }
        String acting = KOMEAlliance.normalizeFactionKey(faction);
        KOMEAllianceStageProgress progress = alliance.getStageProgress(acting);
        if (progress == null) return Decision.deny("That faction is not part of this alliance.");
        if (!actingKing) return Decision.deny("Only the acting faction's recognized king may claim a stage.");
        if (progress.stage >= 4) return Decision.deny("Military Partnership is already unlocked.");
        int target = progress.stage + 1;
        KOMEAllianceQuotaPool.Requirement quota = resolveStageQuota(data, alliance, acting, target);
        if (quota == null || !quota.isValid()) return Decision.deny("The Stage " + target + " quota is missing or invalid.");
        String requirementId = stageRequirementId(target);
        if (alliance.getDelivered(acting, requirementId) < quota.requiredUnits) {
            return Decision.deny("The Stage " + target + " goods quota is incomplete.");
        }
        if (!fixedMilestoneComplete(data, alliance, acting, target)) {
            return Decision.deny(fixedMilestoneDescription(data, alliance, acting, target));
        }
        progress.markFixedComplete(target, nowMillis);
        alliance.setFactionStage(acting, target, "Stage " + target + " claimed", worldTime, nowMillis);
        if (target >= 4) {
            KOMEWarService.reconcileAutomaticMilitarySupport(data, nowMillis, "Stage 4 became effective");
            KOMEWartimeStewardshipService.revalidateAll(data, nowMillis, "Stage 4 became effective");
        }
        data.markDirty();
        return Decision.allow(target);
    }

    public static KOMEAllianceQuotaPool.Requirement rollStageQuota(KOMEWorldData data, KOMEAlliance alliance,
            String faction, int targetStage) {
        String type = quotaType(targetStage);
        int tier = quotaTier(targetStage);
        return KOMEAllianceQuotaPool.rollOnce(data, alliance, type, tier, faction);
    }

    public static KOMEAllianceQuotaPool.Requirement resolveStageQuota(KOMEWorldData data, KOMEAlliance alliance,
            String faction, int targetStage) {
        String type = quotaType(targetStage);
        int tier = quotaTier(targetStage);
        KOMEAllianceQuotaPool.Requirement result = KOMEAllianceQuotaPool.resolve(data, alliance, faction, type, tier);
        return result == null ? rollStageQuota(data, alliance, faction, targetStage) : result;
    }

    public static String stageRequirementId(int targetStage) {
        return KOMEAllianceQuotaPool.assignmentId(quotaType(targetStage), quotaTier(targetStage));
    }

    public static boolean fixedMilestoneComplete(KOMEWorldData data, KOMEAlliance alliance,
            String actingFaction, int targetStage) {
        if (targetStage <= 2) return true;
        String partner = alliance.getOtherFaction(actingFaction);
        if (targetStage == 3) {
            int approved = KOMEBuildService.approvedHalfHoursForPartner(data, actingFaction, partner);
            return approved >= Math.max(1, data.allianceStageThreeRequiredHalfHours);
        }
        KOMEAllianceStageProgress progress = alliance.getStageProgress(actingFaction);
        return progress != null && progress.qualifyingDeploymentAtMillis > 0L
            && progress.qualifyingWarId.length() > 0 && progress.qualifyingCompanyId.length() > 0;
    }

    public static String fixedMilestoneDescription(KOMEWorldData data, KOMEAlliance alliance,
            String actingFaction, int targetStage) {
        if (targetStage <= 2) return "No additional fixed milestone.";
        if (targetStage == 3) {
            int current = KOMEBuildService.approvedHalfHoursForPartner(data, actingFaction,
                alliance.getOtherFaction(actingFaction));
            return "Stage 3 requires " + KOMEHalfHourService.displayHours(data.allianceStageThreeRequiredHalfHours)
                + " approved Build hours for the partner; current "
                + KOMEHalfHourService.displayHours(current) + ".";
        }
        return "Stage 4 requires a new qualifying company deployment in partner-controlled land during an active shared defensive war.";
    }

    public static boolean recordQualifyingWarDeployment(KOMEWorldData data, KOMEAlliance alliance,
            String progressingFaction, String partnerFaction, KOMEArmyCompany company, KOMEWar war,
            String tileId, long nowMillis) {
        if (data == null || alliance == null || company == null || war == null || !war.isActive()) return false;
        String progressing = KOMEAlliance.normalizeFactionKey(progressingFaction);
        String partner = KOMEAlliance.normalizeFactionKey(partnerFaction);
        KOMEAllianceStageProgress progress = alliance.getStageProgress(progressing);
        KOMEConquestTile tile = data.conquestTiles.get(KOMEConquestTile.normalizeId(tileId));
        if (progress == null || progress.stage != 3 || !hasValidLivingUnit(data, company, progressing)
                || !progressing.equals(KOMEAlliance.normalizeFactionKey(company.faction))
                || company.owner == null
                || !progressing.equals(KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(company.owner)))
                || tile == null || !partner.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))
                || !war.sameSide(progressing, partner) || !war.isDefendingFaction(partner)
                || progress.claimedAtMillis[3] <= 0L
                || war.activeMembershipAddedAt(progressing) <= progress.claimedAtMillis[3]) {
            return false;
        }
        progress.qualifyingWarId = war.id;
        progress.qualifyingCompanyId = company.id;
        progress.qualifyingDeploymentAtMillis = Math.max(1L, nowMillis);
        progress.markFixedComplete(4, nowMillis);
        data.markDirty();
        return true;
    }

    /**
     * Re-evaluates the two allowed Stage 4 event orders: a company was already in
     * partner land when its faction joined, or it arrived after joining.
     */
    public static boolean scanQualifyingWarDeployments(KOMEWorldData data, long nowMillis) {
        if (data == null) return false;
        boolean changed = false;
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || alliance.getRelationshipStatus() != KOMEAllianceTrackStatus.ACTIVE) continue;
            String[] actors = {alliance.factionA, alliance.factionB};
            for (String actor : actors) {
                KOMEAllianceStageProgress progress = alliance.getStageProgress(actor);
                if (progress == null || progress.stage != 3 || progress.qualifyingDeploymentAtMillis > 0L) continue;
                String partner = alliance.getOtherFaction(actor);
                for (KOMEWar war : data.wars.values()) {
                    if (war == null || !war.isActive() || !war.isDefendingFaction(partner)
                            || !war.sameSide(actor, partner)) continue;
                    for (KOMEArmyCompany company : data.armyCompanies.values()) {
                        if (company == null || !actor.equals(KOMEAlliance.normalizeFactionKey(company.faction))
                                || !hasValidLivingUnit(data, company, actor)) continue;
                        if (recordQualifyingWarDeployment(data, alliance, actor, partner, company, war,
                                company.currentTile, nowMillis)) {
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private static boolean hasValidLivingUnit(KOMEWorldData data, KOMEArmyCompany company, String faction) {
        if (data == null || company == null || company.owner == null) return false;
        String expectedFaction = KOMEAlliance.normalizeFactionKey(faction);
        for (java.util.UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null && !record.populationReturned && !record.farmhand
                    && record.type == KOMEPopulationType.OFFENSIVE
                    && company.owner.equals(record.owner)
                    && expectedFaction.equals(KOMEAlliance.normalizeFactionKey(record.unitFaction))) {
                return true;
            }
        }
        return false;
    }

    private static String quotaType(int targetStage) {
        if (targetStage <= 1) return KOMEAlliance.CIVIL;
        if (targetStage == 2) return KOMEAlliance.TRADE;
        return KOMEAlliance.MILITARY;
    }

    public static String stageQuotaType(int targetStage) {
        return quotaType(targetStage);
    }

    private static int quotaTier(int targetStage) {
        if (targetStage <= 1) return 1;
        if (targetStage == 2) return 2;
        return targetStage == 3 ? 2 : 3;
    }

    public static int stageQuotaTier(int targetStage) {
        return quotaTier(targetStage);
    }

    public static final class Decision {
        public final boolean allowed;
        public final int claimedStage;
        public final String reason;

        private Decision(boolean allowed, int claimedStage, String reason) {
            this.allowed = allowed;
            this.claimedStage = claimedStage;
            this.reason = reason == null ? "" : reason;
        }

        public static Decision allow(int stage) {
            return new Decision(true, stage, "");
        }

        public static Decision deny(String reason) {
            return new Decision(false, 0, reason);
        }
    }
}
