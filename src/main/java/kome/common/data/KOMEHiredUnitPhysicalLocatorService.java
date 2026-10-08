package kome.common.data;

import net.minecraft.entity.Entity;

import java.util.UUID;

/** Sole writer for durable Campaign-unit physical locator authority. */
public final class KOMEHiredUnitPhysicalLocatorService {
    private KOMEHiredUnitPhysicalLocatorService() { }

    public static boolean observe(KOMEWorldData data, KOMEHiredUnitRecord record,
            Entity entity, KOMEHiredUnitPhysicalLocator.CaptureKind kind,
            long observedAtMillis, boolean refreshExactPosition) {
        if (data == null || record == null || entity == null || entity.worldObj == null
                || entity.worldObj.provider == null || kind == null) return false;
        UUID entityId = entity.getUniqueID();
        if (!KOMEHiredUnitClassification.isCampaignUnit(record)
                || entityId == null || !entityId.equals(record.entity)
                || data.hiredUnits.get(entityId) != record
                || !entity.isEntityAlive() || record.populationReturned
                || record.farmhand || record.type != KOMEPopulationType.OFFENSIVE
                || record.movingEntityData != null || !coherentCompany(data, record)) {
            return clear(data, record);
        }
        int dimension = entity.worldObj.provider.dimensionId;
        KOMETileResolution resolved = KOMETileWorldResolver.INSTANCE.resolveWorldPosition(
            dimension, entity.posX, entity.posZ);
        String strategicTile = KOMEConquestTile.normalizeId(record.currentTile);
        if (resolved.status != KOMETileResolution.Status.RESOLVED
                || !strategicTile.equals(resolved.tileId)) return clear(data, record);
        int chunkX = floor(entity.posX) >> 4;
        int chunkZ = floor(entity.posZ) >> 4;
        KOMEHiredUnitPhysicalLocator previous = record.getPhysicalLocator();
        boolean addressChanged = previous == null
            || !previous.sameAddress(entityId, dimension, chunkX, chunkZ, resolved.tileId);
        boolean lifecycleRefresh = previous == null || previous.getCaptureKind() != kind;
        if (!addressChanged && !lifecycleRefresh && !refreshExactPosition) return false;
        if (!addressChanged && !lifecycleRefresh && previous.sameObservation(
                entity.posX, entity.posY, entity.posZ, kind)) return false;
        KOMEHiredUnitPhysicalLocator locator = KOMEHiredUnitPhysicalLocator.verified(
            entityId, dimension, entity.posX, entity.posY, entity.posZ,
            resolved.tileId, observedAtMillis, kind);
        record.replacePhysicalLocator(locator);
        data.markDirty();
        return true;
    }

    public static boolean clear(KOMEWorldData data, KOMEHiredUnitRecord record) {
        if (record == null || record.getPhysicalLocator() == null) return false;
        record.clearPhysicalLocator();
        if (data != null) data.markDirty();
        return true;
    }

    private static boolean coherentCompany(KOMEWorldData data, KOMEHiredUnitRecord record) {
        String companyId = record.companyId == null ? "" : record.companyId;
        KOMEArmyCompany company = data.armyCompanies.get(companyId);
        return company != null && company.units.contains(record.entity)
            && KOMEConquestTile.normalizeId(company.currentTile).equals(
                KOMEConquestTile.normalizeId(record.currentTile));
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
