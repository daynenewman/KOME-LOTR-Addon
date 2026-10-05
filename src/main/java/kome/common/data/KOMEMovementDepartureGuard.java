package kome.common.data;

/** Pure revalidation of the actual next saved route edge, before staging or chunk access. */
public final class KOMEMovementDepartureGuard {
    private KOMEMovementDepartureGuard() { }
    public static final class Decision {
        public final String code, reason;
        private Decision(String code, String reason) { this.code = code; this.reason = reason; }
        public boolean allowed() { return code.isEmpty(); }
    }
    public static Decision evaluate(KOMEWorldData data, KOMEArmyMovementOrder order) {
        if (data == null || order == null || order.routeTiles.size() < 2)
            return new Decision("INVALID_ROUTE", "No route step is available");
        int index = order.currentRouteIndex;
        if (index < 0 || index >= order.routeTiles.size() - 1)
            return new Decision("INVALID_ROUTE_INDEX", "No next route step is available");
        String origin = KOMEConquestTile.normalizeId(order.routeTiles.get(index));
        String destination = KOMEConquestTile.normalizeId(order.routeTiles.get(index + 1));
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        if (company != null && !origin.equals(KOMEConquestTile.normalizeId(company.currentTile)))
            return new Decision("INVALID_ROUTE_ORIGIN", "Saved route disagrees with the company's authoritative tile");
        if (!origin.equals(KOMEConquestTile.normalizeId(order.currentTile)))
            return new Decision("INVALID_ROUTE_ORIGIN", "Saved route does not depart from the authoritative current tile");
        KOMEConquestRouteEdge edge = data.getRouteEdge(origin, destination);
        if (edge == null) return new Decision("ROUTE_EDGE_MISSING", "No current route edge between " + origin + " and " + destination + ".");
        if (!edge.isPassable()) return new Decision("ROUTE_EDGE_BLOCKED", edge.describeBlock());
        if (!KOMEMovementAccessService.isMovementStepAuthorized(data, order, origin, destination, order.retreating))
            return new Decision("ACCESS_LOST", KOMEMovementAccessService.movementAccessReason(data, order, destination));
        return new Decision("", "");
    }
}
