package kome.common.data;

import java.util.UUID;

/** Sole permission boundary for treating a tracked hired unit as a campaign unit. */
public final class KOMEHiredUnitClassification {
    private KOMEHiredUnitClassification() {
    }

    public static KOMEHiredUnitClass getUnitClass(KOMEHiredUnitRecord record) {
        return record == null ? KOMEHiredUnitClass.ORDINARY : record.persistedUnitClass();
    }

    public static boolean isCampaignUnit(KOMEHiredUnitRecord record) {
        return getUnitClass(record) == KOMEHiredUnitClass.CAMPAIGN;
    }

    public static boolean isCampaignUnit(KOMEWorldData data, UUID entityId) {
        return data != null && entityId != null && isCampaignUnit(data.hiredUnits.get(entityId));
    }

    /** Strategic membership survives unloading, movement pauses and Formal Retreat. */
    public static boolean isWithinCommittedCampaignLifespan(KOMEWorldData data, KOMEHiredUnitRecord record) {
        if (data == null || record == null || data.hiredUnits.get(record.entity) != record
                || !isCampaignUnit(record) || record.populationReturned) return false;
        KOMEOrdinaryBattleRecoveryRegistry.Target target = data.getOrdinaryBattleRecovery().targetForPhysical(record.entity);
        if (target != null && target.terminal) return false;
        return !KOMECampaignHealth.versioned(record.survivingHealth)
            || !KOMECampaignHealthProjection.read(record.survivingHealth).terminal;
    }

    public static KOMEHiredUnitRecord requireCampaignUnit(KOMEHiredUnitRecord record) {
        if (!isCampaignUnit(record)) {
            throw new IllegalArgumentException();
        }
        return record;
    }

    /** Reserved for an explicit, validated recruitment or mobilization transaction. */
    static void assignForCampaignWorkflow(KOMEHiredUnitRecord record) {
        if (record == null) {
            throw new IllegalArgumentException();
        }
        record.assignPersistedUnitClass(KOMEHiredUnitClass.CAMPAIGN);
    }
}
