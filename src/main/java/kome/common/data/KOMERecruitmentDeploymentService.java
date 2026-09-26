package kome.common.data;

import kome.common.KOMEReflection;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Resolves a live, safe, same-tile placement for a newly recruited unit. */
public final class KOMERecruitmentDeploymentService {
    private KOMERecruitmentDeploymentService() { }

    public static KOMEStrategicDeploymentResolver.Validation resolve(KOMEWorldData data,
            World world, String faction, String tileId) {
        return resolve(data, world, faction, tileId, null);
    }

    public static KOMEStrategicDeploymentResolver.Validation resolve(KOMEWorldData data,
            World world, String faction, String tileId, Entity hiredEntity) {
        String factionKey = KOMEAlliance.normalizeFactionKey(faction);
        String tileKey = KOMEConquestTile.normalizeId(tileId);
        KOMERecruitmentLocationService.Decision legality =
            KOMERecruitmentLocationService.evaluate(data, factionKey, tileKey);
        if (!legality.legal) {
            return KOMEStrategicDeploymentResolver.Validation.invalid(
                "Recruitment tile is no longer legal: " + legality.reason);
        }

        double x;
        double y;
        double z;
        int dimension;
        KOMEFactionCapitalRecord capital = KOMEFactionCapitalService.getCapital(data, factionKey);
        if (capital != null && tileKey.equals(capital.getCapitalTileId())) {
            dimension = capital.getDeploymentDimensionId();
            x = capital.getDeploymentX();
            y = capital.getDeploymentY();
            z = capital.getDeploymentZ();
        } else {
            KOMEConquestTile tile = data.getConquestTileIfPresent(tileKey);
            if (tile == null) {
                return KOMEStrategicDeploymentResolver.Validation.invalid(
                    "Canonical recruitment tile is unavailable: " + tileKey);
            }
            data.ensureDefaultArrivalPoint(tile);
            KOMETileWaypoint rally = data.getTileWaypoint(tileKey, KOMETileWaypoint.RALLY);
            if (rally == null) {
                return KOMEStrategicDeploymentResolver.Validation.invalid(
                    "Recruitment tile has no deterministic deployment seed: " + tileKey);
            }
            dimension = rally.dimensionId;
            x = rally.x;
            y = rally.y;
            z = rally.z;
        }

        if (world == null || world.provider == null
                || world.provider.dimensionId != dimension) {
            return KOMEStrategicDeploymentResolver.Validation.invalid(
                "The recruitment deployment world is unavailable or has the wrong dimension.");
        }
        Footprint footprint;
        try {
            footprint = footprint(hiredEntity);
        } catch (IllegalArgumentException invalidEntityTree) {
            return KOMEStrategicDeploymentResolver.Validation.invalid(
                invalidEntityTree.getMessage());
        }
        return KOMEStrategicDeploymentResolver.resolveAround(world, tileKey, x, y, z,
            KOMEStrategicDeploymentResolver.DEFAULT_SEARCH_RADIUS,
            footprint.width, footprint.height);
    }

    static Footprint footprint(Entity entity) {
        if (entity == null) return new Footprint(0.6D, 1.8D);
        List<Entity> riderToBase = new ArrayList<Entity>();
        Map<Entity, Boolean> seen = new IdentityHashMap<Entity, Boolean>();
        Entity current = entity;
        while (current != null) {
            if (seen.put(current, Boolean.TRUE) != null || riderToBase.size() >= 16)
                throw new IllegalArgumentException("Mounted recruitment entity tree is cyclic or too deep.");
            riderToBase.add(current);
            current = KOMEReflection.getRidingEntity(current);
        }
        return footprint(riderToBase);
    }

    private static Footprint footprint(List<Entity> riderToBase) {
        double requiredWidth = 0.0D;
        double baseOffset = 0.0D;
        double requiredHeight = 0.0D;
        for (int index = riderToBase.size() - 1; index >= 0; index--) {
            Entity part = riderToBase.get(index);
            double width = part.width;
            double height = part.height;
            if (!finite(width) || !finite(height) || width <= 0.0D || height <= 0.0D)
                throw new IllegalArgumentException("Mounted recruitment entity has an invalid footprint.");
            requiredWidth = Math.max(requiredWidth, width);
            requiredHeight = Math.max(requiredHeight, baseOffset + height);
            if (index > 0) {
                Entity rider = riderToBase.get(index - 1);
                double mountedOffset = part.getMountedYOffset() + rider.getYOffset();
                if (!finite(mountedOffset) || mountedOffset < 0.0D)
                    throw new IllegalArgumentException("Mounted recruitment entity has an invalid riding offset.");
                baseOffset += mountedOffset;
            }
        }
        return new Footprint(requiredWidth, requiredHeight);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    static final class Footprint {
        final double width;
        final double height;
        Footprint(double width, double height) {
            this.width = width;
            this.height = height;
        }
    }
}
