package kome.common.tactical;

import java.util.Locale;

/** Identity rules for tile-owned tactical configuration, independent of local siege IDs. */
public final class KOMETacticalIds {
    private KOMETacticalIds() {}

    public static String forceDeploymentArea(String value) {
        String id = value == null ? "" : value.trim();
        if (id.isEmpty()) throw new IllegalArgumentException("Force Deployment Area ID is required.");
        return id.toUpperCase(Locale.ROOT);
    }
}
