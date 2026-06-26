package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEPlayerTilePopulationAllocation;
import kome.common.data.KOMETilePopulation;
import kome.common.data.KOMETileWaypointLink;
import kome.common.data.KOMEWorldData;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;

public class KOMEPacketConquestOpenCapture implements IMessage {
    public String tileId;

    public KOMEPacketConquestOpenCapture() {
    }

    public KOMEPacketConquestOpenCapture(String tileId) {
        this.tileId = tileId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tileId = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, tileId);
    }

    public static void sendTileCommand(EntityPlayerMP player, String requestedTileId) {
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
        boolean canMoveTroops = hasControllableCompanyAtTile(data, viewerId, tileId);
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
        KOMEPacketHandler.network.sendTo(new KOMEPacketConquestCaptureGui(tile.id, ownerFaction, tile.pendingTransferFromFaction, tile.pendingTransferToFaction, viewerFaction, summary.offensivePop, summary.defensivePop, summary.mountedPop, summary.groundPop, summary.incomingPop, summary.outgoingPop, summary.incomingEtaMillis, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandTotal, farmhandUsed, canClaim, canTransfer, canAccept, canCancel, canMoveTroops, canEditPopulation, offensiveAllocated, defensiveAllocated, myAllocation == null ? 0 : myAllocation.offensiveAllocated, myAllocation == null ? 0 : myAllocation.offensiveUsed, myAllocation == null ? 0 : myAllocation.defensiveAllocated, myAllocation == null ? 0 : myAllocation.defensiveUsed, tile.claimedByName, allocationSummary.toString(), data.hasFactionKing(ownerFaction), summary.myOffensivePop, summary.myDefensivePop, summary.myMountedPop, summary.myGroundPop, activeRecruitmentTile, canSetRecruitmentTile, waypointLink == null ? "" : waypointLink.lotrWaypointKey, waypointLink == null ? "" : waypointLink.displayName(), waypointLink == null ? "" : waypointLink.waypointRegion, tile.waypointLevel, tile.currentRulingFaction(), tile.defaultRulingFaction, tile.mapRegion), player);
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
        if (pledge != null) {
            return KOMEAlliance.normalizeFactionKey(pledge.codeName());
        }
        return data.getPlayerFactionKey(KOMEReflection.getEntityUUID(player));
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

    private static boolean hasControllableCompanyAtTile(KOMEWorldData data, java.util.UUID viewerId, String tileId) {
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
            sendTileCommand(player, message.tileId);
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
