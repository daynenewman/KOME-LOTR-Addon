package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

/**
 * Change-driven persistence and population reconciliation for a loaded hired unit.
 * The ordinary living-tick path must stay cheap: entity NBT is only serialized at a
 * staggered checkpoint or when an observed persisted property actually changes.
 */
final class KOMEHiredUnitLiveUpdateService {
    static final int SNAPSHOT_INTERVAL_TICKS = 200;

    interface SnapshotSupplier {
        NBTTagCompound snapshot();
    }

    static final class Observation {
        final String unitName;
        final String unitEntityId;
        final int level;
        final int baseCost;
        final boolean mounted;
        final int calculatedCost;

        Observation(String unitName, String unitEntityId, int level, int baseCost,
                boolean mounted, int calculatedCost) {
            this.unitName = clean(unitName);
            this.unitEntityId = clean(unitEntityId);
            this.level = Math.max(1, level);
            this.baseCost = Math.max(0, baseCost);
            this.mounted = mounted;
            this.calculatedCost = Math.max(0, calculatedCost);
        }
    }

    static final class Result {
        final boolean rejected;
        final int requiredExtra;
        final boolean persisted;
        final boolean published;
        final boolean snapshotAttempted;

        private Result(boolean rejected, int requiredExtra, boolean persisted,
                boolean published, boolean snapshotAttempted) {
            this.rejected = rejected;
            this.requiredExtra = requiredExtra;
            this.persisted = persisted;
            this.published = published;
            this.snapshotAttempted = snapshotAttempted;
        }

        static Result rejected(int requiredExtra) {
            return new Result(true, Math.max(0, requiredExtra), false, false, false);
        }
    }

    private KOMEHiredUnitLiveUpdateService() { }

    static Result update(KOMEWorldData data, KOMEHiredUnitRecord record,
            Observation observation, long worldTime, boolean skipSnapshot,
            SnapshotSupplier snapshotSupplier) {
        if (data == null || record == null || observation == null) {
            return new Result(false, 0, false, false, false);
        }

        boolean observedMounted = record.mounted || observation.mounted;
        int oldCost = record.cost;
        int oldSpent = record.populationSpent;
        int debit = 0;
        if (!record.farmhand && record.isFactionPopulationBankFunded()) {
            int desiredCost = Math.max(1, observation.calculatedCost);
            if (desiredCost != record.cost || record.populationSpent < desiredCost) {
                debit = KOMEUnitPopulationCostService.reconcileBankedUnitCost(
                    data, record, desiredCost);
                if (debit < 0) {
                    int required = Math.max(0, desiredCost
                        - Math.max(record.populationSpent, record.cost));
                    return Result.rejected(required);
                }
            }
        }

        boolean authoritativeChanged = oldCost != record.cost
            || oldSpent != record.populationSpent;
        boolean persistedChanged = authoritativeChanged;

        if (!same(record.unitName, observation.unitName)) {
            record.unitName = observation.unitName;
            persistedChanged = true;
        }
        if ((record.unitEntityId == null || record.unitEntityId.length() == 0)
                && observation.unitEntityId.length() > 0) {
            record.unitEntityId = observation.unitEntityId;
            persistedChanged = true;
        }
        if (record.mounted != observedMounted) {
            record.mounted = observedMounted;
            persistedChanged = true;
            authoritativeChanged = true;
        }
        if (record.isFactionPopulationBankFunded()) {
            if (record.level != observation.level) {
                record.level = observation.level;
                persistedChanged = true;
            }
            if (record.baseCost != observation.baseCost) {
                record.baseCost = observation.baseCost;
                persistedChanged = true;
            }
        } else if (record.level <= 0) {
            record.level = observation.level;
            persistedChanged = true;
        }

        boolean snapshotAttempted = false;
        if (!record.isMoving() && snapshotSupplier != null
                && (record.stationedEntityData == null || persistedChanged
                    || !skipSnapshot && isSnapshotDue(record.entity, worldTime))) {
            snapshotAttempted = true;
            NBTTagCompound current = snapshotSupplier.snapshot();
            if (current != null && !current.equals(record.stationedEntityData)) {
                record.stationedEntityData = current;
                persistedChanged = true;
            }
        }

        if (authoritativeChanged) {
            data.refreshCompanyCompositionFor(record);
        }
        if (persistedChanged && debit <= 0) {
            // A successful positive debit already dirtied this same WorldSavedData.
            data.markDirty();
        }
        if (authoritativeChanged) {
            data.syncConquestTiles();
        }
        return new Result(false, 0, persistedChanged, authoritativeChanged, snapshotAttempted);
    }

    static boolean isSnapshotDue(UUID entityId, long worldTime) {
        int offset = entityId == null ? 0 : Math.floorMod(entityId.hashCode(), SNAPSHOT_INTERVAL_TICKS);
        return Math.floorMod(worldTime, (long) SNAPSHOT_INTERVAL_TICKS) == offset;
    }

    private static boolean same(String left, String right) {
        return clean(left).equals(clean(right));
    }

    private static String clean(String value) {
        return value == null ? "" : value;
    }
}
