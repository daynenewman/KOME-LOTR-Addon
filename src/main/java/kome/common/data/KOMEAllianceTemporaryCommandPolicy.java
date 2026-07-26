package kome.common.data;

import java.util.Locale;

/** Explicit Stage 4 temporary-controller whitelist. Everything else is owner-only. */
public final class KOMEAllianceTemporaryCommandPolicy {
    private KOMEAllianceTemporaryCommandPolicy() {
    }

    public static boolean allows(String action) {
        String key = action == null ? "" : action.trim().toLowerCase(Locale.ROOT);
        return "view".equals(key) || "dispatch".equals(key) || "continue".equals(key)
            || "halt".equals(key) || "stop".equals(key) || "stay".equals(key)
            || "retreat".equals(key) || "resume".equals(key);
    }
}
