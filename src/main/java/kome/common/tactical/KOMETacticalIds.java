package kome.common.tactical;

import java.util.Locale;

/** Identity rules for tile-owned tactical configuration, independent of local siege IDs. */
public final class KOMETacticalIds {
    private KOMETacticalIds() {}

    public static String forceDeploymentArea(String value) {
        String id = lookup(value);
        if (id.isEmpty()) throw new IllegalArgumentException("Force Deployment Area ID is required.");
        return id;
    }

    /** Matches KOMEWorldData.getBuild lookup semantics; mutation callers separately reject blank keys. */
    public static String buildLookup(String value) {
        return lookup(value);
    }

    static String lookup(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
