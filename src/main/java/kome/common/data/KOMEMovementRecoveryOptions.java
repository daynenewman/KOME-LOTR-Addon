package kome.common.data;

/** Derived player-facing recovery choices for a halted strategic movement order. */
public final class KOMEMovementRecoveryOptions {
    public final boolean canStay;
    public final boolean canRetreat;
    public final String retreatTargetTile;
    public final String retreatBlockedReason;
    public final int retreatRouteIndex;
    public final boolean canResume;
    public final String resumeBlockedReason;
    public final String accessLossReason;
    public final String currentTile;
    public final String nextTile;
    public final String destinationTile;

    private KOMEMovementRecoveryOptions(boolean canStay, boolean canRetreat, String retreatTargetTile,
            String retreatBlockedReason, int retreatRouteIndex, boolean canResume, String resumeBlockedReason, String accessLossReason,
            String currentTile, String nextTile, String destinationTile) {
        this.canStay = canStay;
        this.canRetreat = canRetreat;
        this.retreatTargetTile = retreatTargetTile;
        this.retreatBlockedReason = retreatBlockedReason;
        this.retreatRouteIndex = retreatRouteIndex;
        this.canResume = canResume;
        this.resumeBlockedReason = resumeBlockedReason;
        this.accessLossReason = accessLossReason;
        this.currentTile = currentTile;
        this.nextTile = nextTile;
        this.destinationTile = destinationTile;
    }

    public static KOMEMovementRecoveryOptions forOrder(KOMEWorldData data, KOMEArmyMovementOrder order) {
        String current = order == null ? "" : KOMEConquestTile.normalizeId(order.currentTile);
        String next = order == null ? "" : KOMEConquestTile.normalizeId(order.nextTile);
        String destination = order == null ? "" : KOMEConquestTile.normalizeId(order.destinationTile);
        String loss = order == null || order.accessLossReason == null ? "" : order.accessLossReason;
        if (order == null) {
            return new KOMEMovementRecoveryOptions(false, false, "",
                "No legal retreat destination exists on the recorded traveled route.", -1, false,
                "No movement order is available.", loss, current, next, destination);
        }
        boolean halted = KOMEArmyMovementOrder.ACCESS_HALTED.equals(order.status)
            || KOMEArmyMovementOrder.HOLDING.equals(order.status)
            || KOMEArmyMovementOrder.STOPPED.equals(order.status)
            || KOMEArmyMovementOrder.WAR_ENDED_HALTED.equals(order.status);
        boolean retreatOnly = KOMEArmyMovementOrder.WAR_ENDED_HALTED.equals(order.status)
            || "RETREAT_ONLY".equals(order.accessChoice);
        int retreatIndex = retreatOnly
            ? KOMEMovementAccessService.findStewardshipRetreatRouteIndex(data, order)
            : KOMEMovementAccessService.findRetreatRouteIndex(data, order);
        String retreatTarget = retreatIndex < 0 || retreatIndex >= order.traveledRouteTiles.size()
            ? "" : KOMEConquestTile.normalizeId(order.traveledRouteTiles.get(retreatIndex));
        boolean canRetreat = halted && retreatTarget.length() > 0 && !retreatTarget.equals(current);
        boolean canStay = (KOMEArmyMovementOrder.ACCESS_HALTED.equals(order.status)
            || KOMEArmyMovementOrder.HOLDING.equals(order.status)) && !retreatOnly;
        boolean canResume = false;
        String resumeReason;
        if (retreatOnly) {
            resumeReason = "This war-ended stewardship company may retreat only.";
        } else if (!halted || KOMEArmyMovementOrder.STOPPED.equals(order.status) && loss.length() == 0) {
            resumeReason = "Resume is available only for a halted movement order.";
        } else {
            String prospective = KOMEMovementAccessService.resolveProspectiveForwardStep(order);
            if (prospective.length() == 0) {
                resumeReason = "No remaining forward route or movement step is available.";
            } else {
                canResume = KOMEMovementAccessService.isMovementStepAuthorized(data, order, current, prospective, false);
                resumeReason = canResume ? "" : "The route is still unauthorized: "
                    + KOMEMovementAccessService.movementAccessReason(data, order, prospective);
            }
        }
        String retreatReason = canRetreat ? "" : "No legal retreat destination exists on the recorded traveled route.";
        if (retreatOnly && !canRetreat) {
            retreatReason = "No legal stewardship retreat destination exists on the recorded traveled route.";
        }
        return new KOMEMovementRecoveryOptions(canStay, canRetreat, retreatTarget, retreatReason, retreatIndex,
            canResume, resumeReason, loss, current, next, destination);
    }
}
