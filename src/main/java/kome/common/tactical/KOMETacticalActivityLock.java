package kome.common.tactical;

import java.util.Locale;
import kome.common.data.KOMEWorldData;
import kome.common.siege.KOMESiegeComplex;

/**
 * Read-only integration seam for the future authoritative conflict system. No activity state is owned or persisted here.
 * Providers are queried on the server thread and must not mutate data or load worlds. Install per KOMEWorldData instance.
 */
public final class KOMETacticalActivityLock {
    public interface Provider {
        boolean isLocked(String tileId, int dimensionId, String canonicalComplexId);
    }
    public static final Provider UNLOCKED = (tile, dimension, complex) -> false;
    private KOMETacticalActivityLock() { }

    public static void requireUnlocked(KOMEWorldData data, KOMESiegeComplex complex) {
        if (complex != null) requireUnlocked(data, complex.getTileId(), complex.getDimensionId(), complex.getComplexId());
    }
    public static void requireUnlocked(KOMEWorldData data, String tile, int dimension, String complex) {
        String id = required(complex), tileId = required(tile);
        if (data.isTacticalActivityLocked(tileId, dimension, id)) throw new LockedException(tileId, dimension, id);
    }
    /** Ownership comes from the tactical assignment, even if the Build itself is missing or has stale location metadata. */
    public static void requireBuildUnlocked(KOMEWorldData data, String buildId) {
        if (data == null) return;
        KOMETacticalConfiguration config = data.getTacticalConfigurationSnapshot();
        String owner = config.findAssignedComplexId(buildId).orElse(null);
        if (owner != null) requireUnlocked(data, config.findComplex(owner));
    }
    /** Tile-owned staging remains independent; only complexes explicitly referencing this area are affected. */
    public static void requireAreaUnlocked(KOMEWorldData data, String areaId) {
        for (KOMESiegeComplex complex : data.getTacticalConfigurationSnapshot().getComplexesById().values())
            if (complex.getPreferredForceDeploymentAreaId().orElse("").equals(areaId)) requireUnlocked(data, complex);
    }
    private static String required(String value) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Tactical lock identity is required.");
        return value.trim().toUpperCase(Locale.ROOT);
    }
    public static final class LockedException extends IllegalArgumentException {
        private LockedException(String tile, int dimension, String complex) {
            super("ACTIVITY_LOCKED: Siege Complex " + complex + " on tile " + tile + " (dimension " + dimension
                + ") is locked by current activity. Authored configuration and assigned defensive accounting cannot change.");
        }
    }
}
