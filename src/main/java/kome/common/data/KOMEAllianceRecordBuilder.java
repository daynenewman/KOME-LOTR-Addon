package kome.common.data;

import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class KOMEAllianceRecordBuilder {
    private static final Set<UUID> operatorViewers = Collections.synchronizedSet(new HashSet<UUID>());

    public static void setOperatorView(EntityPlayer viewer, boolean requested) {
        if (viewer == null) {
            return;
        }
        UUID viewerId = kome.common.KOMEReflection.getEntityUUID(viewer);
        boolean operator = viewer.canCommandSenderUseCommand(2, "alliance");
        if (resolveOperatorView(operator, requested)) {
            operatorViewers.add(viewerId);
        } else {
            operatorViewers.remove(viewerId);
        }
    }

    public static void clearOperatorView(EntityPlayer viewer) {
        if (viewer != null) {
            operatorViewers.remove(kome.common.KOMEReflection.getEntityUUID(viewer));
        }
    }

    public static void resetSessionState() {
        operatorViewers.clear();
    }

    static boolean resolveOperatorView(boolean operator, boolean requested) {
        return operator && requested;
    }

    static boolean includeViewerRecord(boolean operatorView, boolean participant) {
        return operatorView || participant;
    }

    public static List build(KOMEWorldData data, EntityPlayer viewer) {
        List lines = new ArrayList();
        List allianceLines = new ArrayList();
        String viewerFactionKey = getViewerFactionKey(data, viewer);
        boolean operator = viewer == null || viewer.canCommandSenderUseCommand(2, "alliance");
        boolean operatorView = viewer == null || resolveOperatorView(operator,
            operatorViewers.contains(kome.common.KOMEReflection.getEntityUUID(viewer)));
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || !alliance.hasAnyAlliance() && !alliance.hasRecoverableGoods()) {
                continue;
            }
            if (!includeViewerRecord(operatorView, alliance.involves(viewerFactionKey))) {
                continue;
            }
            allianceLines.add(formatAlliance(data, alliance, viewerFactionKey, viewer, operator));
        }
        Collections.sort(allianceLines);
        lines.add("SUMMARY\t" + allianceLines.size());
        lines.add("VIEWER\t" + viewerFactionKey + "\t" + getViewerFactionName(data, viewer)
            + "\t" + (isViewerKing(data, viewer, viewerFactionKey) ? "1" : "0")
            + "\t" + (data.hasFactionKing(viewerFactionKey) ? "1" : "0")
            + "\t" + (operator ? "1" : "0")
            + "\t" + (operatorView ? "1" : "0"));
        lines.add("CONFIG\t" + data.allianceDifficulty + "\t" + KOMEAllianceRequirements.multiplier(data.allianceDifficulty)
            + "\t" + (data.waypointRestrictionEnabled ? "1" : "0")
            + "\t" + (viewer != null && data.hasWaypointRestrictionBypass(kome.common.KOMEReflection.getEntityUUID(viewer)) ? "1" : "0")
            + "\t" + data.successionGraceDefaultMillis + "\t" + data.contributionGraceDefaultMillis);
        for (String type : new String[] {KOMEAlliance.CIVIL, KOMEAlliance.TRADE, KOMEAlliance.MILITARY}) {
            for (int tier = 1; tier <= KOMEAlliance.maxTier(type); tier++) {
                lines.add("REQUIREMENT\t" + type + "\t" + tier + "\t"
                    + data.getAllianceItemStackEquivalents(type, tier) + "\t"
                    + data.getAllianceActivityRequirement(type, tier) + "\t"
                    + data.getAlliancePopulationRequirement(type, tier));
            }
        }
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction()) {
                String key = KOMEAlliance.normalizeFactionKey(faction.codeName());
                if (viewer != null && viewerFactionKey.length() > 0 && !key.equals(viewerFactionKey)) {
                    boolean viewerKing = isViewerKing(data, viewer, viewerFactionKey);
                    boolean receiverKing = data.hasFactionKing(key);
                    boolean senderKing = data.hasFactionKing(viewerFactionKey);
                    KOMEAllianceAuthority.Decision civil = KOMEAllianceAuthority.decideRequestAlliance(
                        KOMEAlliance.CIVIL, viewerFactionKey, key, viewerFactionKey, viewerKing, senderKing,
                        receiverKing, KOMEAllianceAuthority.getDefaultRelation(viewerFactionKey, key));
                    KOMEAllianceAuthority.Decision military = KOMEAllianceAuthority.decideRequestAlliance(
                        KOMEAlliance.MILITARY, viewerFactionKey, key, viewerFactionKey, viewerKing, senderKing,
                        receiverKing, KOMEAllianceAuthority.getDefaultRelation(viewerFactionKey, key));
                    KOMEAllianceAuthority.Decision trade = KOMEAllianceAuthority.decideRequestAlliance(
                        KOMEAlliance.TRADE, viewerFactionKey, key, viewerFactionKey, viewerKing, senderKing,
                        receiverKing, KOMEAllianceAuthority.getDefaultRelation(viewerFactionKey, key));
                    lines.add("REQUEST_OPTION\t" + key + "\t" + flag(civil.allowed) + "\t"
                        + flag(military.allowed) + "\t" + flag(trade.allowed) + "\t" + flag(receiverKing));
                }
                if (data.hasFactionKing(key) && (operatorView || key.equals(viewerFactionKey) || isVisiblePartner(data, viewerFactionKey, key))) {
                    lines.add("KING\t" + key + "\t" + data.getFactionKingName(key));
                }
            }
        }
        lines.addAll(allianceLines);
        List trackLines = new ArrayList();
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || !alliance.hasAnyAlliance() || !includeViewerRecord(operatorView, alliance.involves(viewerFactionKey))) {
                continue;
            }
            String side = alliance.involves(viewerFactionKey) ? viewerFactionKey : alliance.factionA;
            String other = alliance.getOtherFaction(side);
            for (String type : new String[] {KOMEAlliance.CIVIL, KOMEAlliance.TRADE, KOMEAlliance.MILITARY}) {
                if (alliance.getStatus(type) == KOMEAllianceTrackStatus.NONE) {
                    continue;
                }
                trackLines.add(formatTrack(data, alliance, side, other, type, viewer, operator));
                trackLines.add(formatTrack(data, alliance, other, side, type, viewer, operator));
            }
        }
        Collections.sort(trackLines);
        lines.addAll(trackLines);
        List militaryLines = new ArrayList();
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null || alliance.getStatus(KOMEAlliance.MILITARY) == KOMEAllianceTrackStatus.NONE
                    || !includeViewerRecord(operatorView, alliance.involves(viewerFactionKey))) {
                continue;
            }
            String side = alliance.involves(viewerFactionKey) ? viewerFactionKey : alliance.factionA;
            String other = alliance.getOtherFaction(side);
            militaryLines.add(formatMilitaryContext(data, alliance, side, other));
            militaryLines.add(formatMilitaryContext(data, alliance, other, side));
            for (KOMEArmyCompany company : data.armyCompanies.values()) {
                String companyLine = formatMilitaryCompany(data, alliance, side, other, company, viewer);
                if (companyLine.length() > 0) militaryLines.add(companyLine);
                companyLine = formatMilitaryCompany(data, alliance, other, side, company, viewer);
                if (companyLine.length() > 0) militaryLines.add(companyLine);
            }
        }
        Collections.sort(militaryLines);
        lines.addAll(militaryLines);
        return lines;
    }

    private static String formatMilitaryContext(KOMEWorldData data, KOMEAlliance alliance,
            String nativeFaction, String supportingFaction) {
        long now = System.currentTimeMillis();
        int effectiveTier = new KOMEAllianceAuthority(data).getEffectiveTier(
            alliance, supportingFaction, KOMEAlliance.MILITARY, now);
        boolean nativeHasKing = data.hasFactionKing(nativeFaction);
        UUID nativeKing = data.getFactionKingId(nativeFaction);
        UUID supportingKing = data.getFactionKingId(supportingFaction);
        boolean directlyOpposed = KOMEWarService.findActiveOpposition(data, nativeFaction, supportingFaction) != null;
        List<KOMEWar> authorizingWars = KOMEWarService.authorizedSameSideWars(data, nativeFaction, supportingFaction);
        List<String> warNames = new ArrayList<String>();
        Set<String> opponents = new HashSet<String>();
        for (KOMEWar war : authorizingWars) {
            warNames.add(war.displayName.length() == 0 ? war.id : war.displayName);
            opponents.addAll(war.getOpposingFactions(nativeFaction));
        }
        String state;
        String reason;
        if (effectiveTier < 3) {
            state = alliance.getFactionTier(supportingFaction, KOMEAlliance.MILITARY) >= 3 ? "SUSPENDED" : "LOCKED";
            reason = "An effectively active mutual Military T3 alliance is required.";
        } else if (directlyOpposed) {
            state = "CONTRADICTION";
            reason = "Direct active opposition overrides Military T3 authority until an operator resolves the coalition.";
        } else if (supportingKing == null) {
            state = "DORMANT";
            reason = nativeHasKing ? "Voluntary Delegation is dormant because the supporting faction has no recognized pledged king."
                : "Wartime Stewardship is dormant because the supporting faction has no recognized pledged king.";
        } else if (nativeHasKing) {
            state = "VOLUNTARY_DELEGATION";
            reason = "The recognized native king may delegate personally owned companies to the recognized supporting king.";
        } else if (authorizingWars.isEmpty()) {
            state = "DORMANT";
            reason = "No active same-side war currently authorizes Wartime Stewardship.";
        } else {
            state = "ACTIVE";
            reason = "Recognized supporting king; active same-side war and effective Military T3.";
        }
        for (KOMEWar war : KOMEWarService.sortedWars(data)) {
            KOMEWar.MilitarySupportEnrollment enrollment = war.supportEnrollment(nativeFaction, supportingFaction, false);
            if (enrollment != null && ("CONTRADICTION".equals(enrollment.state) || "OPERATOR_REMOVED".equals(enrollment.state))) {
                state = enrollment.state;
                reason = enrollment.reason;
                break;
            }
        }
        int eligible = data.getKinglessStewardshipGlobalCap(nativeFaction);
        int used = data.getKinglessStewardshipReserved(nativeFaction);
        int available = data.getKinglessStewardshipAvailable(nativeFaction);
        return "MILITARY_CONTEXT\t" + alliance.getPairKey() + "\t" + nativeFaction + "\t" + supportingFaction
            + "\t" + state + "\t" + safe(data.getFactionKingName(nativeFaction)) + "\t"
            + safe(nativeKing == null ? "" : nativeKing.toString()) + "\t" + safe(data.getFactionKingName(supportingFaction))
            + "\t" + safe(supportingKing == null ? "" : supportingKing.toString()) + "\t"
            + safe(joinStrings(warNames)) + "\t" + safe(joinStrings(new ArrayList<String>(opponents))) + "\t"
            + eligible + "\t" + used + "\t" + available + "\t" + safe(reason);
    }

    private static String formatMilitaryCompany(KOMEWorldData data, KOMEAlliance alliance,
            String nativeFaction, String supportingFaction, KOMEArmyCompany company, EntityPlayer viewer) {
        if (company == null || !nativeFaction.equals(KOMEWartimeStewardshipService.nativeFaction(company))) return "";
        String pair = alliance.getPairKey();
        String controllerFaction = company.temporaryController == null ? ""
            : KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(company.temporaryController));
        boolean matches = pair.equals(company.delegationAlliancePair) || supportingFaction.equals(controllerFaction);
        if (!matches && KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
            for (String warId : company.authorizedWarIds) {
                KOMEWar war = data.wars.get(warId);
                KOMEWar.MilitarySupportEnrollment enrollment = war == null ? null
                    : war.supportEnrollment(nativeFaction, supportingFaction, false);
                if (enrollment != null) {
                    matches = true;
                    break;
                }
            }
        }
        if (!matches) return "";
        UUID viewerId = viewer == null ? null : kome.common.KOMEReflection.getEntityUUID(viewer);
        boolean temporaryAuthority = viewerId != null && viewerId.equals(company.temporaryController)
            && new KOMEAllianceAuthority(data).canControlTemporaryCompany(company, viewerId).allowed;
        String actions = temporaryAuthority ? "View, Dispatch, Continue, Halt, Stay, Retreat, Resume" : "View";
        KOMEArmyMovementOrder movement = data.armyMovements.get(company.movementOrderId);
        String movementState = movement == null ? company.status : movement.status;
        String owner = company.ownerName.length() == 0 && company.owner != null ? company.owner.toString() : company.ownerName;
        String controller = company.temporaryControllerName.length() == 0 && company.temporaryController != null
            ? company.temporaryController.toString() : company.temporaryControllerName;
        return "MILITARY_COMPANY\t" + pair + "\t" + nativeFaction + "\t" + supportingFaction + "\t"
            + safe(company.id) + "\t" + safe(company.name) + "\t" + safe(owner) + "\t" + safe(controller) + "\t"
            + flag(company.temporaryController != null && data.isFactionKing(supportingFaction, company.temporaryController)) + "\t"
            + safe(joinStrings(new ArrayList<String>(company.authorizedWarIds))) + "\t" + safe(company.tendency) + "\t"
            + safe(movementState) + "\t" + safe(company.withdrawalState) + "\t"
            + safe(company.delegationRevocationReason.length() > 0 ? company.delegationRevocationReason : company.authorizationReason)
            + "\t" + Math.max(0, company.totalPopulation) + "\t" + safe(actions);
    }

    private static String formatTrack(KOMEWorldData data, KOMEAlliance alliance, String side, String partner,
            String type, EntityPlayer viewer, boolean admin) {
        int tier = alliance.getFactionTier(side, type);
        int target = tier >= 0 && tier < KOMEAlliance.maxTier(type) ? tier + 1 : 0;
        KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(side);
        KOMEAllianceQuotaPool.Requirement quota = target == 0 ? null
            : KOMEAllianceQuotaPool.resolve(data, alliance, side, type, target);
        int itemDelivered = quota == null ? 0 : alliance.getDelivered(side, KOMEAllianceQuotaPool.assignmentId(type, target));
        int activityRequired = target == 0 ? 0 : data.getAllianceActivityRequirement(type, target);
        int activityProgress = activityProgress(data, alliance, side, type, target);
        int populationRequired = target == 0 ? 0 : data.getAlliancePopulationRequirement(type, target);
        int populationProgress = KOMEAlliance.MILITARY.equals(type)
            ? alliance.getDelivered(side, KOMEAllianceProgressionService.OFFENSIVE_CAPACITY_MAX) : 0;
        boolean completed = target > 0 && ledger != null && ledger.getCompletedTier(type) >= target;
        boolean viewerHasAuthority = admin || viewer != null && data.isFactionKing(side, kome.common.KOMEReflection.getEntityUUID(viewer));
        boolean viewerCanManage = viewerHasAuthority && target > 0 && !completed
            && alliance.getStatus(type) == KOMEAllianceTrackStatus.ACTIVE && (quota == null || quota.isValid());
        String actionReason = quota != null && !quota.isValid()
            ? KOMEAllianceQuotaPool.INVALID_REQUIREMENT + ": " + quota.invalidReason
            : completed ? "This faction side already completed the target tier"
            : target <= 0 ? "This track has no remaining tier"
            : alliance.getStatus(type) != KOMEAllianceTrackStatus.ACTIVE ? "This track is not active"
            : viewerCanManage ? "Allowed by server authority"
            : "Only staff or the contributing faction king may mutate this track";
        long now = System.currentTimeMillis();
        return "TRACK\t" + alliance.getPairKey() + "\t" + side + "\t" + partner + "\t" + type + "\t"
            + alliance.getStatus(type).key + "\t" + tier + "\t" + target + "\t"
            + safe(quota == null ? "" : quota.displayName) + "\t" + (quota == null ? 0 : quota.requiredUnits) + "\t" + itemDelivered + "\t"
            + safe(activityLabel(type, target)) + "\t" + activityRequired + "\t" + activityProgress + "\t"
            + populationRequired + "\t" + populationProgress + "\t" + (completed ? "1" : "0") + "\t"
            + (ledger != null && ledger.kinglessWaived ? "1" : "0") + "\t"
            + (ledger != null && ledger.isContributionGraceActive(now) ? "1" : "0") + "\t"
            + (ledger == null ? 0L : ledger.graceEndMillis) + "\t"
            + (ledger != null && ledger.isSuccessionActive(now) ? "1" : "0") + "\t"
            + (ledger == null ? 0L : ledger.successionEndMillis) + "\t"
            + safe(benefit(data, side, partner, type, tier)) + "\t" + (viewerCanManage ? "1" : "0") + "\t" + safe(actionReason);
    }

    private static int activityProgress(KOMEWorldData data, KOMEAlliance alliance, String side, String type, int tier) {
        if (tier <= 0) {
            return 0;
        }
        if (KOMEAlliance.CIVIL.equals(type)) {
            return tier == 2 ? alliance.getDelivered(side, KOMEAllianceProgressionService.ALLIED_TRADES) : 0;
        }
        if (KOMEAlliance.TRADE.equals(type)) {
            return alliance.getDelivered(side, KOMEAllianceProgressionService.ALLIED_TRADES);
        }
        return alliance.getDelivered(side, KOMEAllianceProgressionService.ELIGIBLE_KILLS);
    }

    private static String activityLabel(String type, int tier) {
        if (tier <= 0 || KOMEAlliance.CIVIL.equals(type) && tier == 1) {
            return "None";
        }
        return KOMEAlliance.MILITARY.equals(type) ? "Cumulative eligible NPC kills" : "Cumulative allied trades";
    }

    private static String benefit(KOMEWorldData data, String side, String partner, String type, int tier) {
        if (tier <= 0) {
            return "Accepted base alliance";
        }
        if (!KOMEAlliance.MILITARY.equals(type) || tier < 3) {
            return KOMEAllianceBenefits.display(type, tier);
        }
        if (data.hasFactionKing(side)) return "Voluntary Delegation: the native king may delegate personally owned companies and reclaim them immediately.";
        List<KOMEWar> wars = KOMEWarService.authorizedSameSideWars(data, side, partner);
        if (wars.isEmpty()) return "Wartime Stewardship is dormant. It activates when both factions join the same side of an active war and the native faction has no king.";
        List<String> names = new ArrayList<String>();
        Set<String> opponents = new HashSet<String>();
        for (KOMEWar war : wars) {
            names.add(war.displayName.length() == 0 ? war.id : war.displayName);
            opponents.addAll(war.getOpposingFactions(side));
        }
        return "Wartime Stewardship Active. " + displayFaction(side) + " and " + displayFaction(partner)
            + " are on the same side of " + joinStrings(names) + ". Authorized opposing factions: "
            + joinStrings(new ArrayList<String>(opponents)) + ". Available " + displayFaction(side)
            + " offensive population: " + data.getKinglessStewardshipReserved(side) + "/"
            + data.getKinglessStewardshipGlobalCap(side) + " used/eligible.";
    }

    private static String joinStrings(List<String> values) {
        Collections.sort(values);
        StringBuilder out = new StringBuilder();
        for (String value : values) {
            if (value == null || value.length() == 0) continue;
            if (out.length() > 0) out.append(", ");
            out.append(value);
        }
        return out.length() == 0 ? "none" : out.toString();
    }

    public static List build(KOMEWorldData data) {
        return build(data, null);
    }

    private static String formatAlliance(KOMEWorldData data, KOMEAlliance alliance, String viewerFaction, EntityPlayer viewer, boolean admin) {
        String contributor = alliance.involves(viewerFaction) ? viewerFaction : alliance.factionA;
        String ally = alliance.getOtherFaction(contributor);
        KOMEAllianceFactionLedger contributorLedger = alliance.getFactionLedger(contributor);
        KOMEAllianceFactionLedger allyLedger = alliance.getFactionLedger(ally);
        return "ALLIANCE\t"
            + alliance.factionA + "\t"
            + alliance.factionB + "\t"
            + displayFaction(alliance.factionA) + "\t"
            + displayFaction(alliance.factionB) + "\t"
            + alliance.getFactionTier(contributor, KOMEAlliance.CIVIL) + "\t"
            + alliance.getFactionTier(contributor, KOMEAlliance.MILITARY) + "\t"
            + alliance.getFactionTier(contributor, KOMEAlliance.TRADE) + "\t"
            + alliance.lastUpdatedBy + "\t"
            + alliance.updatedWorldTime + "\t"
            + safe(alliance.getAssignment(contributor, "military.food")) + "\t"
            + alliance.getDelivered(contributor, "military.food") + "\t"
            + safe(alliance.getAssignment(contributor, "trade.food")) + "\t"
            + alliance.getDelivered(contributor, "trade.food") + "\t"
            + alliance.getDelivered(contributor, KOMEAllianceProgressionService.ALLIED_TRADES) + "\t"
            + alliance.getDelivered(contributor, "military.kills") + "\t"
            + alliance.getDelivered(contributor, "trade.t2.coins") + "\t"
            + alliance.getDelivered(contributor, KOMEAllianceProgressionService.ALLIED_TRADES) + "\t"
            + data.getAllianceActivityRequirement(KOMEAlliance.MILITARY, Math.max(1,
                Math.min(3, alliance.getFactionTier(contributor, KOMEAlliance.MILITARY) + 1))) + "\t"
            + alliance.getDelivered(contributor, KOMEAllianceProgressionService.OFFENSIVE_CAPACITY_MAX) + "\t"
            + alliance.getStatus(KOMEAlliance.CIVIL).key + "\t"
            + alliance.getStatus(KOMEAlliance.TRADE).key + "\t"
            + alliance.getStatus(KOMEAlliance.MILITARY).key + "\t"
            + contributor + "\t" + ally + "\t"
            + completed(contributorLedger, KOMEAlliance.CIVIL) + "\t"
            + completed(contributorLedger, KOMEAlliance.TRADE) + "\t"
            + completed(contributorLedger, KOMEAlliance.MILITARY) + "\t"
            + completed(allyLedger, KOMEAlliance.CIVIL) + "\t"
            + completed(allyLedger, KOMEAlliance.TRADE) + "\t"
            + completed(allyLedger, KOMEAlliance.MILITARY) + "\t"
            + waived(contributorLedger) + "\t" + waived(allyLedger) + "\t"
            + deadline(contributorLedger) + "\t" + deadline(allyLedger) + "\t"
            + safe(alliance.getPendingReceiver(KOMEAlliance.CIVIL)) + "\t"
            + safe(alliance.getPendingReceiver(KOMEAlliance.TRADE)) + "\t"
            + safe(alliance.getPendingReceiver(KOMEAlliance.MILITARY)) + "\t"
            + safe(quotaSummary(data, alliance, contributor, KOMEAlliance.CIVIL)) + "\t"
            + safe(quotaSummary(data, alliance, contributor, KOMEAlliance.MILITARY)) + "\t"
            + safe(quotaSummary(data, alliance, contributor, KOMEAlliance.TRADE)) + "\t"
            + safe(quotaSummary(data, alliance, ally, KOMEAlliance.CIVIL)) + "\t"
            + safe(quotaSummary(data, alliance, ally, KOMEAlliance.MILITARY)) + "\t"
            + safe(quotaSummary(data, alliance, ally, KOMEAlliance.TRADE)) + "\t"
            + "None\tNone\t"
            + (admin || viewer != null && data.isFactionKing(contributor, kome.common.KOMEReflection.getEntityUUID(viewer)) ? "1" : "0");
    }

    private static String quotaSummary(KOMEWorldData data, KOMEAlliance alliance, String faction, String type) {
        int currentTier = alliance.getFactionTier(faction, type);
        if (currentTier == KOMEAlliance.PENDING) {
            return "Pending acceptance";
        }
        if (currentTier < 0) {
            return "No track";
        }
        int targetTier = currentTier + 1;
        if (targetTier > KOMEAlliance.maxTier(type)) {
            return "Track complete";
        }
        KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(faction);
        if (ledger != null && ledger.kinglessWaived) {
            return "T" + targetTier + ": kingless contribution waived";
        }
        if (ledger != null && ledger.getCompletedTier(type) >= targetTier) {
            return "T" + targetTier + ": side complete";
        }
        String id = KOMEAllianceQuotaPool.assignmentId(type, targetTier);
        KOMEAllianceQuotaPool.Requirement requirement = KOMEAllianceQuotaPool.resolve(data, alliance, faction, type, targetTier);
        if (requirement == null) {
            return "T" + targetTier + ": quota not rolled";
        }
        if (!requirement.isValid()) {
            return "T" + targetTier + ": " + KOMEAllianceQuotaPool.INVALID_REQUIREMENT + " - " + requirement.invalidReason;
        }
        int delivered = Math.min(requirement.requiredUnits, alliance.getDelivered(faction, id));
        return "T" + targetTier + ": " + requirement.displayName + " " + delivered + "/" + requirement.requiredUnits
            + " (" + requirement.pointValue + " points each)";
    }

    private static boolean isVisiblePartner(KOMEWorldData data, String viewerFaction, String candidate) {
        if (viewerFaction == null || viewerFaction.length() == 0) {
            return false;
        }
        KOMEAlliance alliance = data.getAlliance(viewerFaction, candidate, false);
        return alliance != null && (alliance.hasAnyAlliance() || alliance.hasRecoverableGoods());
    }

    private static int completed(KOMEAllianceFactionLedger ledger, String type) {
        return ledger == null ? 0 : ledger.getCompletedTier(type);
    }

    private static int waived(KOMEAllianceFactionLedger ledger) {
        return ledger != null && ledger.kinglessWaived ? 1 : 0;
    }

    private static long deadline(KOMEAllianceFactionLedger ledger) {
        if (ledger == null) {
            return 0L;
        }
        return Math.max(ledger.graceEndMillis, ledger.successionEndMillis);
    }

    private static String safe(String value) {
        return value == null ? "" : value.replace('\t', ' ');
    }

    private static String flag(boolean value) {
        return value ? "1" : "0";
    }

    private static String displayFaction(String key) {
        return KOMEAlliance.displayFactionName(key);
    }

    private static String getViewerFactionKey(KOMEWorldData data, EntityPlayer viewer) {
        LOTRFaction faction = getViewerFaction(data, viewer);
        return faction == null ? "" : KOMEAlliance.normalizeFactionKey(faction.codeName());
    }

    private static String getViewerFactionName(KOMEWorldData data, EntityPlayer viewer) {
        LOTRFaction faction = getViewerFaction(data, viewer);
        return faction == null ? "No pledged faction" : faction.factionName();
    }

    private static LOTRFaction getViewerFaction(KOMEWorldData data, EntityPlayer viewer) {
        if (viewer == null) {
            return null;
        }
        LOTRFaction pledge = LOTRLevelData.getData(viewer).getPledgeFaction();
        if (pledge != null) {
            return pledge;
        }
        KOMEPlayerProgression progression = data.getProgression(kome.common.KOMEReflection.getEntityUUID(viewer));
        return findFaction(progression.getPledgedLordFaction());
    }

    private static boolean isViewerKing(KOMEWorldData data, EntityPlayer viewer, String factionKey) {
        return viewer != null && factionKey != null && factionKey.length() > 0
            && data.isFactionKing(factionKey, kome.common.KOMEReflection.getEntityUUID(viewer));
    }

    private static LOTRFaction findFaction(String value) {
        LOTRFaction direct = LOTRFaction.forName(value);
        if (direct != null) {
            return direct;
        }
        String normalized = KOMEAlliance.normalizeFactionKey(value);
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction()
                && (KOMEAlliance.normalizeFactionKey(faction.codeName()).equals(normalized)
                || KOMEAlliance.normalizeFactionKey(faction.factionName()).equals(normalized))) {
                return faction;
            }
        }
        return null;
    }
}
