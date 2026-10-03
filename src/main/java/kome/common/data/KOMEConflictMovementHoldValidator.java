package kome.common.data;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Shared fail-closed validation for lifecycle changes to conflict-owned movement holds. */
final class KOMEConflictMovementHoldValidator {
    enum LinkPolicy { REQUIRE_COMPLETE, ALLOW_MISSING_EXPECTED }

    private KOMEConflictMovementHoldValidator() {}

    static String validate(KOMEWorldData data, KOMEConflictRecord conflict,
            KOMEArmyMovementOrder order, LinkPolicy links) {
        if (data == null || conflict == null || order == null || links == null)
            return "Conflict-held movement authority is incomplete.";
        if (!KOMEArmyMovementOrder.CONFLICT_HELD.equals(order.status))
            return "Movement order is not in conflict-held state.";
        if (!conflict.getConflictId().equals(clean(order.conflictHoldId)))
            return "Movement order claims a different Conflict ID.";
        if (!conflict.getCommitments().containsKey(clean(order.companyId)))
            return "Conflict does not contain the held Campaign Detachment commitment.";

        String tile = KOMEConquestTile.normalizeId(order.currentTile);
        String attack = KOMEConquestTile.normalizeId(order.hostileAttackDestination);
        String destination = KOMEConquestTile.normalizeId(order.destinationTile);
        String finalDestination = KOMEConquestTile.normalizeId(
            clean(order.finalDestinationTile).length() == 0
                ? order.destinationTile : order.finalDestinationTile);
        if (tile.length() == 0 || !conflict.getTileId().equals(tile))
            return "Held order current tile does not match the conflict tile.";
        if (!tile.equals(attack))
            return "Held order hostile attack destination does not match the conflict tile.";
        if (!tile.equals(destination) || !tile.equals(finalDestination))
            return "Held order final destination does not match its conflict arrival.";
        if (order.routeTiles.isEmpty())
            return "Held order has no preserved strategic route.";
        int last = order.routeTiles.size() - 1;
        if (!finalDestination.equals(KOMEConquestTile.normalizeId(order.routeTiles.get(last))))
            return "Held order preserved route does not end at its final destination.";
        if (order.currentRouteIndex < 0 || order.currentRouteIndex > last
                || !tile.equals(KOMEConquestTile.normalizeId(
                    order.routeTiles.get(order.currentRouteIndex))))
            return "Held order route index does not identify the conflict tile.";
        if (order.finalRouteIndex != last || order.currentRouteIndex != last)
            return "Held order route indexes do not describe a completed hostile terminal arrival.";

        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        if (company == null) return "Held Campaign Detachment is missing.";
        if (!Objects.equals(company.owner, order.owner))
            return "Held movement order owner disagrees with its Campaign Detachment.";
        if (!KOMEAlliance.normalizeFactionKey(company.faction).equals(
                KOMEAlliance.normalizeFactionKey(order.ownerFaction)))
            return "Held movement order faction disagrees with its Campaign Detachment.";
        if (!KOMEArmyCompany.STATIONED.equals(company.status))
            return "Held Campaign Detachment is not strategically stationed.";
        if (!tile.equals(KOMEConquestTile.normalizeId(company.currentTile)))
            return "Held Campaign Detachment tile disagrees with the conflict tile.";

        Set<UUID> companyUnits = new HashSet<UUID>(company.units);
        Set<UUID> orderUnits = new HashSet<UUID>(order.units);
        if (companyUnits.contains(null) || orderUnits.contains(null)
                || companyUnits.size() != company.units.size()
                || orderUnits.size() != order.units.size()
                || !companyUnits.equals(orderUnits))
            return "Held movement cohort disagrees with Campaign Detachment membership.";

        boolean allowMissing = links == LinkPolicy.ALLOW_MISSING_EXPECTED;
        String companyOrder = clean(company.movementOrderId);
        if (!order.id.equals(companyOrder) && !(allowMissing && companyOrder.length() == 0))
            return "Campaign Detachment movement link disagrees with its held order.";
        for (UUID unitId : order.units) {
            KOMEHiredUnitRecord unit = data.hiredUnits.get(unitId);
            if (unit == null) return "Held movement cohort contains a missing unit record.";
            if (!KOMEHiredUnitClassification.isCampaignUnit(unit))
                return "Held movement cohort contains a non-CAMPAIGN unit.";
            if (!order.companyId.equals(clean(unit.companyId)))
                return "Held unit record disagrees with Campaign Detachment membership.";
            if (!tile.equals(KOMEConquestTile.normalizeId(unit.currentTile)))
                return "Held unit strategic tile disagrees with the conflict tile.";
            String unitOrder = clean(unit.movementOrderId);
            if (!order.id.equals(unitOrder) && !(allowMissing && unitOrder.length() == 0))
                return "Held unit movement link disagrees with its order.";
        }

        KOMECompanyCoherenceService.Assessment coherence = allowMissing
            ? KOMECompanyCoherenceService.INSTANCE.assessMovementLinkCandidate(
                data, company, order)
            : KOMECompanyCoherenceService.INSTANCE.assess(data, company);
        if (coherence.status == KOMECompanyCoherenceService.Status.INCOHERENT) {
            String detail = coherence.issues.isEmpty() ? "unknown coherence defect"
                : coherence.issues.get(0).detail;
            return "Held Campaign Detachment is incoherent: " + detail;
        }
        return "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
