package kome.common.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Raises canonical conquest levels from currently approved Build hours. The tile level is the durable high-water mark. */
public final class KOMEConquestBuildPromotionService {
    private static final long LEVEL_3_CENTI_HOURS = 50L * KOMEBuildTime.CENTI_HOURS_PER_HOUR;

    private KOMEConquestBuildPromotionService() { }

    /** Recompute only the affected tile after a canonical Build approval mutation. */
    public static boolean promoteForTile(KOMEWorldData data, String tileId) {
        String id = KOMEConquestTile.normalizeId(tileId);
        if (!isActiveTile(data, id)) return false;
        long approved = 0L;
        for (KOMEPlayerBuild build : data.builds.values()) {
            if (qualifies(build, id)) approved = cappedAdd(approved, build.approvedCentiHours());
            if (approved == LEVEL_3_CENTI_HOURS) break;
        }
        return promote(data, id, approved);
    }

    /** Reconcile an existing world after its authoritative Builds and tiles have both loaded. */
    public static boolean promoteLoadedTiles(KOMEWorldData data) {
        Map<String, Long> approvedByTile = new HashMap<String, Long>();
        Set<String> activeTiles = KOMEConquestTileDefaults.getKnownTileIds();
        for (KOMEPlayerBuild build : data.builds.values()) {
            if (build == null || !build.active || !qualifyingType(build.type)) continue;
            String id = KOMEConquestTile.normalizeId(build.tileId);
            if (!activeTiles.contains(id) || !data.conquestTiles.containsKey(id)) continue;
            long prior = approvedByTile.containsKey(id) ? approvedByTile.get(id) : 0L;
            approvedByTile.put(id, cappedAdd(prior, build.approvedCentiHours()));
        }
        boolean changed = false;
        for (Map.Entry<String, Long> entry : approvedByTile.entrySet()) {
            changed |= promote(data, entry.getKey(), entry.getValue());
        }
        return changed;
    }

    private static boolean qualifies(KOMEPlayerBuild build, String tileId) {
        return build != null && build.active && qualifyingType(build.type)
            && tileId.equals(KOMEConquestTile.normalizeId(build.tileId));
    }

    private static boolean qualifyingType(KOMEBuildType type) {
        return type == KOMEBuildType.NORMAL || type == KOMEBuildType.DEFENSIVE;
    }

    private static boolean isActiveTile(KOMEWorldData data, String tileId) {
        return data != null && !data.isWriteBlocked() && tileId.length() > 0
            && !KOMEConquestTileDefaults.isRetiredTile(tileId)
            && KOMEConquestTileDefaults.getKnownTileIds().contains(tileId)
            && data.conquestTiles.containsKey(tileId);
    }

    private static long cappedAdd(long prior, long amount) {
        KOMEBuildTime.requireNonnegative(amount);
        return amount >= LEVEL_3_CENTI_HOURS - prior ? LEVEL_3_CENTI_HOURS : prior + amount;
    }

    private static boolean promote(KOMEWorldData data, String tileId, long approvedCentiHours) {
        KOMEConquestTile tile = data.conquestTiles.get(tileId);
        int earned = approvedCentiHours >= LEVEL_3_CENTI_HOURS ? 3 : approvedCentiHours > 0L ? 2 : 1;
        if (tile.waypointLevel >= earned) return false;
        tile.waypointLevel = earned;
        data.markDirty();
        return true;
    }
}
