package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Shared reverse-traveled-corridor planner used by ordinary and conflict formal retreat. */
public final class KOMEMovementRetreatService {
    private KOMEMovementRetreatService() { }

    public static final class Plan {
        public final String orderId;
        public final List<String> routeTiles;
        public final String destinationTile;

        private Plan(String orderId, List<String> route) {
            this.orderId = orderId;
            this.routeTiles = Collections.unmodifiableList(new ArrayList<String>(route));
            this.destinationTile = route.get(route.size() - 1);
        }
    }

    public static Plan prepare(KOMEWorldData data, KOMEArmyMovementOrder order,
            boolean allowConflictHeld) {
        if (data == null || order == null)
            throw new IllegalArgumentException("Movement order is required.");
        boolean normal = KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)
            || KOMEArmyMovementOrder.ACCESS_HALTED.equals(order.status)
            || KOMEArmyMovementOrder.STOPPED.equals(order.status)
            || KOMEArmyMovementOrder.HOLDING.equals(order.status)
            || KOMEArmyMovementOrder.WAR_ENDED_HALTED.equals(order.status);
        if (!normal && !(allowConflictHeld
                && KOMEArmyMovementOrder.CONFLICT_HELD.equals(order.status)))
            throw new IllegalArgumentException(
                "Retreat is available only after the company has physically reached a route tile.");
        String current = KOMEConquestTile.normalizeId(order.currentTile);
        List<String> traveled = new ArrayList<String>(order.traveledRouteTiles);
        if (traveled.isEmpty()) traveled.add(current);
        int currentIndex = traveled.lastIndexOf(current);
        if (currentIndex < 0)
            throw new IllegalArgumentException(
                "The current tile is absent from the recorded traveled route.");
        boolean stewardshipRetreat = KOMEArmyMovementOrder.WAR_ENDED_HALTED.equals(order.status)
            || "RETREAT_ONLY".equals(order.accessChoice);
        int safeIndex = stewardshipRetreat
            ? KOMEMovementAccessService.findStewardshipRetreatRouteIndex(data, order)
            : KOMEMovementAccessService.findRetreatRouteIndex(data, order);
        if (safeIndex < 0)
            throw new IllegalArgumentException(
                "No legal retreat destination exists on the recorded traveled route.");
        List<String> reverse = new ArrayList<String>();
        for (int i = currentIndex; i >= safeIndex; i--) {
            String tile = KOMEConquestTile.normalizeId(traveled.get(i));
            if (reverse.isEmpty() || !tile.equals(reverse.get(reverse.size() - 1)))
                reverse.add(tile);
        }
        if (reverse.size() < 2)
            throw new IllegalArgumentException(
                "The company is already at the nearest legal retreat tile.");
        return new Plan(order.id, reverse);
    }

    public static void publish(KOMEWorldData data, KOMEArmyMovementOrder order,
            Plan plan, long nowMillis) {
        if (data == null || order == null || plan == null || !plan.orderId.equals(order.id))
            throw new IllegalArgumentException("Retreat plan no longer matches its movement order.");
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        if (company != null && company.movementOrderId != null
                && company.movementOrderId.length() > 0
                && !(order.id.equals(company.movementOrderId)
                    || isAcceptedFormalRetreat(order)))
            throw new IllegalArgumentException("Retreat company no longer matches its movement order.");
        order.routeTiles.clear();
        order.routeTiles.addAll(plan.routeTiles);
        order.originTile = order.currentTile;
        order.destinationTile = plan.destinationTile;
        order.finalDestinationTile = plan.destinationTile;
        order.currentRouteIndex = 0;
        order.nextRouteIndex = 1;
        order.finalRouteIndex = order.routeTiles.size() - 1;
        order.totalSteps = order.routeTiles.size() - 1;
        order.distanceTiles = order.totalSteps;
        order.completedSteps = 0;
        order.currentStepOriginTile = order.currentTile;
        order.currentStepDestinationTile = order.routeTiles.get(1);
        order.nextTile = order.currentStepDestinationTile;
        order.retreating = true;
        order.haltAfterArrival = false;
        order.accessChoice = "RETREAT";
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.nextStepDepartureMillis = nowMillis;
        order.nextStepAvailableMillis = nowMillis;
        order.spawnRetryPaused = false;
        for (UUID unitId : order.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null) record.movementOrderId = order.id;
        }
        if (company != null) {
            company.status = KOMEArmyCompany.MOVING;
            company.movementOrderId = order.id;
            company.updatedAtMillis = nowMillis;
        }
        data.updateMovementHistory(order, KOMEMovementHistoryRecord.ACTIVE);
        data.markDirty();
    }

    static Plan acceptedPlan(KOMEArmyMovementOrder order,List<String> route){
        if(order==null||route==null||route.size()<2||!order.currentTile.equals(route.get(0)))
            throw new IllegalArgumentException("Accepted retreat route no longer matches its origin");
        return new Plan(order.id,route);
    }

    public static boolean isAcceptedFormalRetreat(KOMEArmyMovementOrder order) {
        return order != null && !KOMEFormalRetreatAuthority.isQuarantined(order)
            && order.retreating && order.conflictRelease != null
            && KOMEConflictMovementHandoff.Outcome.FORMAL_RETREAT
                == order.conflictRelease.outcome;
    }

    /** Only the forced step that actually exits the conflict gets the allowance exception. */
    public static boolean isImmediateFormalRetreatStep(KOMEArmyMovementOrder order) {
        return isAcceptedFormalRetreat(order) && order.currentRouteIndex == 0
            && order.completedSteps == 0;
    }
}
