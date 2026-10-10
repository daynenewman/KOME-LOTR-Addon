package kome.common.data;

import java.util.HashSet;
import java.util.Set;

/** Recovery reservations are independent of current commitments and commander identity. */
public final class KOMEFormalRetreatAuthority {
    private KOMEFormalRetreatAuthority() { }

    public static boolean hasUnresolvedFormalRetreatBatchAuthority(KOMEArmyMovementOrder order) {
        return order != null && order.formalRetreatBatch != null && !order.formalRetreatBatch.finalized();
    }

    public static KOMEFormalRetreatBatch reservation(KOMEWorldData data, String company, String orderId) {
        KOMEFormalRetreatBatch found = null;
        for (KOMEArmyMovementOrder holder : data.armyMovements.values()) {
            if (!hasUnresolvedFormalRetreatBatchAuthority(holder)) continue;
            for (KOMEFormalRetreatBatch.Member member : holder.formalRetreatBatch.members) {
                if (!member.companyId.equals(company) && !member.orderId.equals(orderId)) continue;
                if (found != null && !found.id.equals(holder.formalRetreatBatch.id))
                    throw new IllegalArgumentException("Overlapping unfinished Formal Retreat authority.");
                found = holder.formalRetreatBatch;
            }
        }
        return found;
    }

    public static boolean protectsCompany(KOMEWorldData data, String companyId) {
        if (reservation(data, companyId, null) != null) return true;
        for (KOMEArmyMovementOrder order : data.armyMovements.values())
            if (companyId.equals(order.companyId) && isQuarantined(order)) return true;
        return false;
    }

    public static boolean isQuarantined(KOMEArmyMovementOrder order) {
        return order != null && order.conflictRelease != null && order.conflictRelease.isLegacyQuarantined();
    }

    /** Ordinary commands cannot rewrite authority owned by an accepted recovery workflow. */
    public static String ordinaryMovementBlockReason(KOMEWorldData data, String companyId, String orderId) {
        if (reservation(data, companyId, orderId) != null)
            return "This movement order is reserved by an unfinished Formal Retreat.";
        if (isQuarantined(data.armyMovements.get(orderId))
                || companyId != null && protectsCompany(data, companyId))
            return "Incomplete legacy Formal Retreat requires operator review; movement is quarantined.";
        return "";
    }

    /** Latch incomplete legacy evidence in the existing persisted release reason, never a guessed group. */
    public static void quarantineIncompleteLegacy(KOMEWorldData data) {
        Set<String> batched = new HashSet<String>();
        for (KOMEArmyMovementOrder holder : data.armyMovements.values())
            if (holder.formalRetreatBatch != null)
                for (KOMEFormalRetreatBatch.Member member : holder.formalRetreatBatch.members)
                    batched.add(member.orderId);
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (batched.contains(order.id) || order.conflictRelease == null || isQuarantined(order)
                    || order.conflictRelease.outcome != KOMEConflictMovementHandoff.Outcome.FORMAL_RETREAT
                    || order.retreating && order.completedSteps >= 1) continue;
            data.ensureWritable();
            order.conflictRelease = order.conflictRelease.quarantineLegacy();
            data.markDirty();
        }
    }
}
