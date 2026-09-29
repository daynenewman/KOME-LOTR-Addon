package kome.common.data;

/** Population transaction for a native LOTR combat hire; it never enrolls a campaign unit. */
final class KOMENativeHireRegistrationService {
    private KOMENativeHireRegistrationService() {
    }

    static boolean registerOrdinaryCombatHire(KOMEWorldData data,
            KOMEHiredUnitRecord record, String payingFaction) {
        if (data == null || record == null || record.entity == null || record.owner == null) {
            throw new IllegalArgumentException("A complete hired-unit record is required.");
        }
        if (KOMEHiredUnitClassification.isCampaignUnit(record)) {
            throw new IllegalArgumentException("Native LOTR hire registration only accepts ORDINARY units.");
        }
        KOMEHiredUnitRecord existing = data.hiredUnits.get(record.entity);
        if (existing != null) {
            if (existing != record) {
                throw new IllegalStateException("A different hired-unit record already uses this entity ID.");
            }
            return true;
        }
        KOMEPopulationService.CombatHireDebit debit = KOMEPopulationService.beginCombatHireDebit(
            data, payingFaction, Math.max(0, record.populationSpent));
        if (debit == null) {
            return false;
        }
        try {
            data.hiredUnits.put(record.entity, record);
            debit.commit();
            return true;
        } catch (RuntimeException failure) {
            data.hiredUnits.remove(record.entity);
            debit.rollback();
            throw failure;
        }
    }
}
