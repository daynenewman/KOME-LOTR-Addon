package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceAuthority;
import kome.common.data.KOMEBuildContribution;
import kome.common.data.KOMEBuildService;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEPlayerTilePopulationAllocation;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMETilePopulation;
import kome.common.data.KOMETileWaypointLink;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMEClaimConfirmation;
import kome.common.data.KOMEWar;
import kome.common.data.KOMEWarService;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;

public class KOMEPacketConquestOpenCapture implements IMessage {
    public String tileId;
    public String focusBuildId = "";

    public KOMEPacketConquestOpenCapture() {
    }

    public KOMEPacketConquestOpenCapture(String tileId) {
        this.tileId = tileId;
    }

    public KOMEPacketConquestOpenCapture(String tileId, String focusBuildId) {
        this.tileId = tileId;
        this.focusBuildId = focusBuildId == null ? "" : focusBuildId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tileId = ByteBufUtils.readUTF8String(buf);
        focusBuildId = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, tileId);
        ByteBufUtils.writeUTF8String(buf, focusBuildId == null ? "" : focusBuildId);
    }

    public static void sendTileCommand(EntityPlayerMP player, String requestedTileId) {
        sendTileCommand(player, requestedTileId, "");
    }

    public static void sendTileCommand(EntityPlayerMP player, String requestedTileId, String focusBuildId) {
        String tileId = KOMEConquestTile.normalizeId(requestedTileId);
        if (tileId.isEmpty() || !KOMEConquestTile.isCanonicalTileId(tileId)) {
            return;
        }
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        KOMEConquestTile tile = data.getConquestTile(tileId);
        String viewerFaction = KOMEAlliance.normalizeFactionKey(getPlayerFaction(data, player));
        String ownerFaction = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
        String pendingToFaction = KOMEAlliance.normalizeFactionKey(tile.pendingTransferToFaction);
        java.util.UUID viewerId = KOMEReflection.getEntityUUID(player);
        boolean canEditPopulation = canEditPopulation(data, player, tile);
        data.rebuildArmyCompaniesForPlayer(KOMEReflection.getWorld(player), viewerId);
        TroopSummary summary = summarizeTroops(data, ownerFaction, tileId, viewerId);
        boolean canMoveTroops = hasControllableCompanyAtTile(data, viewerId, viewerFaction, tileId, player.canCommandSenderUseCommand(2, "troops"));
        int offensiveTotal = data.getEffectiveUsablePopulation(tileId, ownerFaction, KOMEPopulationType.OFFENSIVE);
        int offensiveUsed = data.getEffectiveUsedPopulation(tileId, ownerFaction, KOMEPopulationType.OFFENSIVE);
        int defensiveTotal = data.getEffectiveUsablePopulation(tileId, ownerFaction, KOMEPopulationType.DEFENSIVE);
        int defensiveUsed = data.getEffectiveUsedPopulation(tileId, ownerFaction, KOMEPopulationType.DEFENSIVE);
        int farmhandTotal = 0;
        int farmhandUsed = 0;
        for (KOMETilePopulation population : data.getTilePopulationPools(tileId)) {
            boolean ownerPool = KOMEAlliance.normalizeFactionKey(population.sourceFaction).equals(ownerFaction);
            farmhandTotal += ownerPool ? population.farmhandTotal : population.farmhandTotal / 2;
            farmhandUsed += Math.min(population.farmhandUsed, ownerPool ? population.farmhandTotal : population.farmhandTotal / 2);
        }
        boolean canClaim = viewerFaction.length() > 0 && !viewerFaction.equals(ownerFaction);
        boolean ownerKing = data.isFactionKing(ownerFaction, KOMEReflection.getEntityUUID(player));
        boolean canTransfer = tile.isClaimed() && viewerFaction.equals(ownerFaction) && ownerKing;
        boolean canAccept = tile.hasPendingTransfer() && viewerFaction.equals(pendingToFaction) && data.isFactionKing(pendingToFaction, KOMEReflection.getEntityUUID(player));
        boolean canCancel = tile.hasPendingTransfer() && viewerFaction.equals(ownerFaction) && ownerKing;
        int offensiveAllocated = data.getTotalAllocated(tileId, ownerFaction, KOMEPopulationType.OFFENSIVE);
        int defensiveAllocated = data.getTotalAllocated(tileId, ownerFaction, KOMEPopulationType.DEFENSIVE);
        KOMEPlayerTilePopulationAllocation myAllocation = data.getAllocation(tileId, ownerFaction, viewerId);
        String activeRecruitmentTile = data.getActiveRecruitmentTile(viewerId, viewerFaction);
        boolean canSetRecruitmentTile = data.canUseRecruitmentTile(viewerId, viewerFaction, tileId);
        KOMETileWaypointLink waypointLink = data.getTileWaypointLink(tileId);
        StringBuilder allocationSummary = new StringBuilder();
        java.util.List<KOMEPlayerTilePopulationAllocation> allocations = data.getAllocationsForTile(tileId, ownerFaction);
        int shownAllocations = 0;
        for (KOMEPlayerTilePopulationAllocation allocation : allocations) {
            if (shownAllocations >= 2) {
                break;
            }
            if (allocationSummary.length() > 0) {
                allocationSummary.append("; ");
            }
            allocationSummary.append(allocation.playerName.length() == 0 ? "Player" : allocation.playerName)
                .append(" O ").append(allocation.offensiveUsed).append("/").append(allocation.offensiveAllocated)
                .append(" D ").append(allocation.defensiveUsed).append("/").append(allocation.defensiveAllocated);
            shownAllocations++;
        }
        if (allocations.size() > shownAllocations) {
            allocationSummary.append("; +").append(allocations.size() - shownAllocations).append(" more");
        }
        KOMEPacketConquestCaptureGui packet = new KOMEPacketConquestCaptureGui(tile.id, ownerFaction, tile.pendingTransferFromFaction, tile.pendingTransferToFaction, viewerFaction, summary.offensivePop, summary.defensivePop, summary.mountedPop, summary.groundPop, summary.incomingPop, summary.outgoingPop, summary.incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAccept, canCancel, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myAllocation == null ? 0 : myAllocation.offensiveAllocated, myAllocation == null ? 0 : myAllocation.offensiveUsed, myAllocation == null ? 0 : myAllocation.defensiveAllocated, myAllocation == null ? 0 : myAllocation.defensiveUsed, tile.claimedByName, allocationSummary.toString(), data.hasFactionKing(ownerFaction), summary.myOffensivePop, summary.myDefensivePop, summary.myMountedPop, summary.myGroundPop, activeRecruitmentTile, canSetRecruitmentTile, waypointLink == null ? "" : waypointLink.lotrWaypointKey, waypointLink == null ? "" : waypointLink.displayName(), waypointLink == null ? "" : waypointLink.waypointRegion, tile.waypointLevel, tile.currentRulingFaction(), tile.defaultRulingFaction, tile.mapRegion);
        populateBuildAndPoolViews(packet, data, player, tile, viewerFaction, ownerFaction, viewerId);
        packet.focusBuildId = focusBuildId == null ? "" : focusBuildId;
        if (canClaim && ownerFaction.length() > 0) {
            boolean alliedConfirmation = KOMEWarService.requiresHostileConfirmation(data, viewerFaction, ownerFaction);
            KOMEClaimConfirmation confirmation = data.conquestClaimConfirmations.get(viewerId);
            long now = System.currentTimeMillis();
            packet.claimConfirmationArmed = alliedConfirmation && confirmation != null && confirmation.expiresAtMillis >= now
                && tileId.equals(confirmation.tileId) && ownerFaction.equals(confirmation.expectedOwner)
                && KOMEWarService.allianceFingerprint(data, viewerFaction, ownerFaction).equals(confirmation.expectedAllianceState);
            StringBuilder warning = new StringBuilder("This tile is controlled by ").append(KOMEAlliance.displayFactionName(ownerFaction))
                .append(". Capturing it is a hostile act.");
            if (alliedConfirmation) warning.append(" Capturing it will immediately end all direct Civil, Trade, and Military agreements between ")
                .append(KOMEAlliance.displayFactionName(viewerFaction)).append(" and ").append(KOMEAlliance.displayFactionName(ownerFaction))
                .append(", revoke delegated authority, and begin or update a war. Confirmation expires 30 seconds after the first click.");
            java.util.List<KOMEWar> sameSide = KOMEWarService.findActiveSameSide(data, viewerFaction, ownerFaction);
            if (!sameSide.isEmpty()) warning.append(" WARNING: current same-side coalition membership will become contradictory and require operator correction.");
            packet.claimWarning = warning.toString();
            KOMEWar existingWar = KOMEWarService.findActiveOpposition(data, viewerFaction, ownerFaction);
            packet.claimWarDestination = existingWar == null ? "This claim will create a new two-side coalition war."
                : "This claim will append a tile-capture event to " + (existingWar.displayName.length() == 0 ? existingWar.id : existingWar.displayName + " (" + existingWar.id + ")") + ".";
        }
        KOMEPacketHandler.network.sendTo(packet, player);
    }

    private static void populateBuildAndPoolViews(KOMEPacketConquestCaptureGui packet, KOMEWorldData data,
            EntityPlayerMP player, KOMEConquestTile tile, String viewerFaction, String controller, java.util.UUID viewerId) {
        boolean admin = player.canCommandSenderUseCommand(2, "build");
        for (KOMEPlayerBuild build : KOMEBuildService.buildsInTile(data, tile.id, false)) {
            KOMEPacketConquestCaptureGui.BuildView view = new KOMEPacketConquestCaptureGui.BuildView();
            view.id = build.id;
            view.name = build.displayName;
            view.populationFaction = build.populationFaction;
            view.builder = build.builderName;
            view.manager = build.managerName;
            view.dimension = build.dimension;
            view.x = build.x;
            view.y = build.y;
            view.z = build.z;
            view.buildType = build.type.key;
            view.approvedHalfHours = build.approvedHalfHours();
            view.pendingCount = build.pendingCount();
            view.status = buildStatus(data, viewerFaction, controller, build.populationFaction);
            view.canManage = admin || KOMEBuildService.isManager(build, viewerId);
            KOMEBuildService.Decision delete = KOMEBuildService.canDeleteBuild(
                data, build, viewerId, admin);
            KOMEBuildService.Decision destroy = KOMEBuildService.canDestroyEnemyBuild(
                data, build, viewerId, viewerFaction, admin);
            view.canDestroy = destroy.allowed;
            if (delete.allowed) {
                view.destroyMode = "delete";
            } else if (destroy.allowed) {
                view.destroyMode = "destroy";
            } else if (view.canManage) {
                view.destroyMode = "delete";
                view.destroyReason = delete.reason;
            } else {
                view.destroyMode = "destroy";
                view.destroyReason = destroy.reason;
            }
            for (KOMEBuildContribution contribution : build.sortedContributions()) {
                KOMEPacketConquestCaptureGui.ContributionView contributionView =
                    new KOMEPacketConquestCaptureGui.ContributionView();
                contributionView.id = contribution.id;
                contributionView.player = contribution.contributorName;
                contributionView.faction = contribution.contributorFaction;
                contributionView.halfHours = contribution.halfHours;
                contributionView.status = contribution.status;
                view.contributions.add(contributionView);
            }
            packet.builds.add(view);
        }
        packet.viewerDimension = player.worldObj.provider.dimensionId;
        packet.viewerX = player.posX;
        packet.viewerY = player.posY;
        packet.viewerZ = player.posZ;
    }

    private static String buildStatus(KOMEWorldData data, String viewerFaction, String controller, String buildOwner) {
        String viewer = KOMEAlliance.normalizeFactionKey(viewerFaction);
        String owner = KOMEAlliance.normalizeFactionKey(buildOwner);
        if (owner.equals(viewer)) return owner.equals(controller) ? "Owned" : "Captured";
        lotr.common.fac.LOTRFactionRelations.Relation relation = KOMEAllianceAuthority.getCurrentRelation(viewer, owner);
        if (relation == lotr.common.fac.LOTRFactionRelations.Relation.ALLY) return "Allied";
        if (relation == lotr.common.fac.LOTRFactionRelations.Relation.FRIEND) return "Friendly";
        if (relation == lotr.common.fac.LOTRFactionRelations.Relation.ENEMY
                || relation == lotr.common.fac.LOTRFactionRelations.Relation.MORTAL_ENEMY) return "Enemy";
        return "Neutral";
    }

    public static boolean canEditPopulation(KOMEWorldData data, EntityPlayerMP player, KOMEConquestTile tile) {
        if (player.canCommandSenderUseCommand(2, "population")) {
            return tile != null && tile.isClaimed();
        }
        if (tile == null || !tile.isClaimed()) {
            return false;
        }
        return data.isFactionKing(tile.currentRulingFaction(), KOMEReflection.getEntityUUID(player));
    }

    private static String getPlayerFaction(KOMEWorldData data, EntityPlayerMP player) {
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        return pledge == null ? "" : KOMEAlliance.normalizeFactionKey(pledge.codeName());
    }

    private static TroopSummary summarizeTroops(KOMEWorldData data, String ownerFaction, String tileId, java.util.UUID viewerId) {
        TroopSummary summary = new TroopSummary();
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record == null || !tileId.equals(KOMEConquestTile.normalizeId(record.currentTile))) {
                continue;
            }
            if (record.movementOrderId != null && record.movementOrderId.length() > 0) {
                continue;
            }
            if (kome.common.data.KOMEHaltedUnitProtection.isProtectedRecord(data, null, record)) {
                continue;
            }
            if (viewerId.equals(record.owner)) {
                if (record.type == KOMEPopulationType.DEFENSIVE) {
                    summary.myDefensivePop += record.cost;
                } else if (!record.farmhand) {
                    summary.myOffensivePop += record.cost;
                    if (record.mounted) {
                        summary.myMountedPop += record.cost;
                    } else {
                        summary.myGroundPop += record.cost;
                    }
                }
            }
            String faction = data.getPlayerFactionKey(record.owner);
            if (!kome.common.data.KOMEAlliance.normalizeFactionKey(ownerFaction).equals(kome.common.data.KOMEAlliance.normalizeFactionKey(faction))) {
                continue;
            }
            if (record.type == KOMEPopulationType.DEFENSIVE) {
                summary.defensivePop += record.cost;
            } else if (!record.farmhand) {
                summary.offensivePop += record.cost;
                if (record.mounted) {
                    summary.mountedPop += record.cost;
                } else {
                    summary.groundPop += record.cost;
                }
            }
        }
        long now = System.currentTimeMillis();
        long eta = Long.MAX_VALUE;
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order == null || !order.isMoving()) {
                continue;
            }
            if (!kome.common.data.KOMEAlliance.normalizeFactionKey(ownerFaction).equals(kome.common.data.KOMEAlliance.normalizeFactionKey(order.ownerFaction))) {
                continue;
            }
            if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)) {
                if (tileId.equals(activeStepOrigin(order))) {
                    summary.offensivePop += order.population;
                    summary.mountedPop += order.mountedPopulation;
                    summary.groundPop += order.groundPopulation;
                }
                continue;
            }
            if (tileId.equals(activeStepOrigin(order))) {
                summary.outgoingPop += order.population;
            }
            if (tileId.equals(activeStepDestination(order))) {
                summary.incomingPop += order.population;
                eta = Math.min(eta, order.getRemainingMillis(now));
            }
        }
        summary.incomingEtaMillis = eta == Long.MAX_VALUE ? 0L : eta;
        return summary;
    }

    private static boolean hasControllableCompanyAtTile(KOMEWorldData data, java.util.UUID viewerId, String viewerFaction, String tileId, boolean admin) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        for (Object object : data.armyCompanies.values()) {
            if (!(object instanceof kome.common.data.KOMEArmyCompany)) {
                continue;
            }
            kome.common.data.KOMEArmyCompany company = (kome.common.data.KOMEArmyCompany) object;
            if (company != null && viewerId.equals(company.owner) && !company.units.isEmpty()
                    && companyHasPresenceAtTile(data, company, tile)) {
                return true;
            }
        }
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record != null && viewerId.equals(record.owner) && !record.farmhand
                    && record.type == KOMEPopulationType.OFFENSIVE && !record.isMoving()
                    && tile.equals(KOMEConquestTile.normalizeId(record.currentTile))
                    && record.lotrCompanyValue != null && record.lotrCompanyValue.length() > 0) {
                return true;
            }
        }
        if (admin && data.canFactionStandOnTile(tile, viewerFaction)) {
            for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
                if (record != null && viewerId.equals(record.owner) && !record.farmhand
                        && record.type == KOMEPopulationType.OFFENSIVE && !record.isMoving()
                        && tile.equals(KOMEConquestTile.normalizeId(record.currentTile))
                        && (record.companyId == null || record.companyId.length() == 0)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean companyHasPresenceAtTile(KOMEWorldData data, kome.common.data.KOMEArmyCompany company, String tileId) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        if (tile.equals(KOMEConquestTile.normalizeId(company.currentTile))) {
            return true;
        }
        KOMEArmyMovementOrder order = data.armyMovements.get(company.movementOrderId);
        if (order != null && KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)
                && tile.equals(activeStepOrigin(order))) {
            return true;
        }
        for (java.util.UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null && tile.equals(KOMEConquestTile.normalizeId(record.currentTile))) {
                return true;
            }
        }
        return false;
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestOpenCapture, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestOpenCapture message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            sendTileCommand(player, message.tileId, message.focusBuildId);
            return null;
        }
    }

    private static class TroopSummary {
        int offensivePop;
        int defensivePop;
        int mountedPop;
        int groundPop;
        int incomingPop;
        int outgoingPop;
        long incomingEtaMillis;
        int myOffensivePop;
        int myDefensivePop;
        int myMountedPop;
        int myGroundPop;
    }

    private static String activeStepOrigin(KOMEArmyMovementOrder order) {
        if (order == null) {
            return "";
        }
        String current = KOMEConquestTile.normalizeId(order.currentTile);
        if (current.length() > 0) {
            return current;
        }
        String value = KOMEConquestTile.normalizeId(order.currentStepOriginTile);
        if (value.length() > 0) {
            return value;
        }
        return order.routeTiles.size() > order.currentRouteIndex
            ? KOMEConquestTile.normalizeId(order.routeTiles.get(order.currentRouteIndex))
            : KOMEConquestTile.normalizeId(order.originTile);
    }

    private static String activeStepDestination(KOMEArmyMovementOrder order) {
        if (order == null) {
            return "";
        }
        String next = KOMEConquestTile.normalizeId(order.nextTile);
        if (next.length() > 0) {
            return next;
        }
        String value = KOMEConquestTile.normalizeId(order.currentStepDestinationTile);
        if (value.length() > 0) {
            return value;
        }
        return order.routeTiles.size() > order.nextRouteIndex
            ? KOMEConquestTile.normalizeId(order.routeTiles.get(order.nextRouteIndex))
            : KOMEConquestTile.normalizeId(order.destinationTile);
    }
}
