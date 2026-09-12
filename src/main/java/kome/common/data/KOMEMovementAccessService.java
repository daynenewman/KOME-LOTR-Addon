package kome.common.data;

import java.util.ArrayList;

/**
 * Authoritative access checks and external revalidation for ordinary strategic
 * company movement. Retreat and stewardship policy remain caller-owned.
 */
public final class KOMEMovementAccessService {
    private KOMEMovementAccessService() {
    }

    public static boolean revalidateAll(KOMEWorldData data, long nowMillis) {
        if (data == null || data.armyMovements.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (KOMEArmyMovementOrder order : new ArrayList<KOMEArmyMovementOrder>(data.armyMovements.values())) {
            if (order == null || order.retreating
                    || KOMEArmyMovementOrder.ACCESS_HALTED.equals(order.status)
                    || KOMEArmyMovementOrder.HOLDING.equals(order.status)
                    || KOMEArmyMovementOrder.STOPPED.equals(order.status)
                    || KOMEArmyMovementOrder.WAR_ENDED_HALTED.equals(order.status)
                    || KOMEArmyMovementOrder.ARRIVED.equals(order.status)
                    || KOMEArmyMovementOrder.CANCELLED.equals(order.status)) {
                continue;
            }
            if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)) {
                if (!isMovementStepAuthorized(data, order)) {
                    haltForAccessLoss(data, order, nowMillis, movementAccessReason(data, order));
                    changed = true;
                }
            } else if (KOMEArmyMovementOrder.MOVING.equals(order.status)
                    || KOMEArmyMovementOrder.PENDING_SPAWN.equals(order.status)
                    || KOMEArmyMovementOrder.SPAWNING.equals(order.status)
                    || KOMEArmyMovementOrder.SPAWN_BLOCKED.equals(order.status)) {
                if (!isMovementStepAuthorized(data, order)) {
                    changed |= markCommittedStepForAccessLoss(data, order, nowMillis);
                }
            }
        }
        if (changed) {
            data.markDirty();
        }
        return changed;
    }

    public static boolean isMovementStepAuthorized(KOMEWorldData data, KOMEArmyMovementOrder order) {
        return isMovementStepAuthorized(data, order, activeStepOrigin(order), activeStepDestination(order), order != null && order.retreating);
    }

    public static boolean isMovementStepAuthorized(KOMEWorldData data, KOMEArmyMovementOrder order,
            String origin, String destination, boolean retreat) {
        if (data == null || order == null) {
            return false;
        }
        return (retreat || isTileStandableForOrder(data, order, origin, false))
            && isTileStandableForOrder(data, order, destination, retreat);
    }

    public static String movementAccessReason(KOMEWorldData data, KOMEArmyMovementOrder order) {
        return movementAccessReason(data, order, activeStepDestination(order));
    }

    public static String movementAccessReason(KOMEWorldData data, KOMEArmyMovementOrder order, String destination) {
        KOMEConquestTile tile = data == null ? null : data.conquestTiles.get(destination);
        String owner = tile == null ? "unknown" : KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
        return "Military passage lost before entering " + destination + " (owner "
            + KOMEAlliance.displayFactionName(owner) + ").";
    }

    public static boolean markCommittedStepForAccessLoss(KOMEWorldData data,
            KOMEArmyMovementOrder order, long nowMillis) {
        if (order == null || order.haltAfterArrival) {
            return false;
        }
        order.haltAfterArrival = true;
        if (order.accessLossReason == null || order.accessLossReason.length() == 0) {
            order.accessLossReason = movementAccessReason(data, order);
        }
        if (order.accessLostAtMillis <= 0L) {
            order.accessLostAtMillis = nowMillis;
        }
        if (order.accessChoice == null || order.accessChoice.length() == 0) {
            order.accessChoice = "PENDING";
        }
        return true;
    }

    public static void haltForAccessLoss(KOMEWorldData data, KOMEArmyMovementOrder order,
            long nowMillis, String reason) {
        if (order == null) {
            return;
        }
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.accessLossReason = reason == null ? "Military passage is no longer valid." : reason;
        order.accessLostAtMillis = nowMillis;
        order.accessChoice = "PENDING";
        order.pendingSpawnReason = order.accessLossReason;
        order.nextStepDepartureMillis = 0L;
        order.nextStepAvailableMillis = 0L;
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        if (company != null) {
            company.status = KOMEArmyCompany.STATIONED;
            company.movementOrderId = order.id;
            company.updatedAtMillis = nowMillis;
        }
        data.updateMovementHistory(order, KOMEMovementHistoryRecord.FAILED);
    }

    public static boolean isTileStandableForOrder(KOMEWorldData data, KOMEArmyMovementOrder order,
            String tileId, boolean retreat) {
        String tileKey = KOMEConquestTile.normalizeId(tileId);
        KOMEConquestTile tile = data == null ? null : data.conquestTiles.get(tileKey);
        if (tile == null || !tile.isClaimed()) {
            return false;
        }
        String owner = KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
        String faction = KOMEAlliance.normalizeFactionKey(order == null ? "" : order.ownerFaction);
        KOMEArmyCompany company = order == null ? null : data.armyCompanies.get(order.companyId);
        if (owner.equals(faction) || data.canFactionUseMilitaryPassage(faction, owner)
                || company != null && KOMEWartimeStewardshipService.canEnter(data, company, owner, retreat)) {
            return true;
        }
        return retreat && order != null && order.traveledRouteTiles.contains(tileKey);
    }

    /** Returns the nearest earlier recorded route tile that is legal to remain on. */
    public static int findRetreatRouteIndex(KOMEWorldData data, KOMEArmyMovementOrder order) {
        if (data == null || order == null) {
            return -1;
        }
        String current = KOMEConquestTile.normalizeId(order.currentTile);
        ArrayList<String> traveled = new ArrayList<String>(order.traveledRouteTiles);
        if (traveled.isEmpty()) {
            traveled.add(current);
        }
        int currentIndex = traveled.lastIndexOf(current);
        if (currentIndex < 0) {
            return -1;
        }
        for (int i = currentIndex - 1; i >= 0; i--) {
            if (isTileStandableForOrder(data, order, traveled.get(i), false)) {
                return i;
            }
        }
        return -1;
    }

    /** Preserves stewardship withdrawal priority: native territory before allied staging. */
    public static int findStewardshipRetreatRouteIndex(KOMEWorldData data, KOMEArmyMovementOrder order) {
        if (data == null || order == null) {
            return -1;
        }
        ArrayList<String> traveled = traveledRoute(order);
        int currentIndex = traveled.lastIndexOf(KOMEConquestTile.normalizeId(order.currentTile));
        if (currentIndex < 0) {
            return -1;
        }
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        String nativeFaction = company == null ? KOMEAlliance.normalizeFactionKey(order.ownerFaction)
            : KOMEWartimeStewardshipService.nativeFaction(company);
        for (int i = currentIndex - 1; i >= 0; i--) {
            KOMEConquestTile tile = data.conquestTiles.get(KOMEConquestTile.normalizeId(traveled.get(i)));
            if (tile != null && nativeFaction.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
                return i;
            }
        }
        for (int i = currentIndex - 1; i >= 0; i--) {
            KOMEConquestTile tile = data.conquestTiles.get(KOMEConquestTile.normalizeId(traveled.get(i)));
            String owner = tile == null ? "" : KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction());
            if (owner.length() > 0 && data.canFactionUseMilitaryPassage(nativeFaction, owner)
                    && isTileStandableForOrder(data, order, traveled.get(i), false)) {
                return i;
            }
        }
        return -1;
    }

    /** Resolves only an actual remaining forward route step; final arrival is not resumable. */
    public static String resolveProspectiveForwardStep(KOMEArmyMovementOrder order) {
        if (order == null || order.routeTiles.isEmpty()) {
            return "";
        }
        int finalIndex = order.finalRouteIndex > 0 ? order.finalRouteIndex : order.routeTiles.size() - 1;
        if (order.currentRouteIndex >= finalIndex || order.nextRouteIndex <= order.currentRouteIndex
                || order.nextRouteIndex >= order.routeTiles.size()) {
            return "";
        }
        String destination = KOMEConquestTile.normalizeId(order.currentStepDestinationTile);
        if (destination.length() > 0) {
            return destination;
        }
        destination = KOMEConquestTile.normalizeId(order.nextTile);
        return destination.length() > 0 ? destination
            : KOMEConquestTile.normalizeId(order.routeTiles.get(order.nextRouteIndex));
    }

    private static ArrayList<String> traveledRoute(KOMEArmyMovementOrder order) {
        ArrayList<String> traveled = new ArrayList<String>(order.traveledRouteTiles);
        if (traveled.isEmpty()) {
            traveled.add(KOMEConquestTile.normalizeId(order.currentTile));
        }
        return traveled;
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
