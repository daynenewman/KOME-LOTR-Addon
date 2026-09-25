package kome.common.data;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Runtime-only operator test switch; deliberately outside progression persistence. */
public final class KOMESerfKnightCadenceOverride {
    private static final Set<UUID> BYPASS = new HashSet<UUID>();
    private KOMESerfKnightCadenceOverride() { }
    public static synchronized boolean isEnabled(UUID player) { return player != null && BYPASS.contains(player); }
    public static synchronized void set(UUID player, boolean enabled) { if (player == null) return; if (enabled) BYPASS.add(player); else BYPASS.remove(player); }
    public static synchronized void clear(UUID player) { if (player != null) BYPASS.remove(player); }
    static synchronized void clearAll() { BYPASS.clear(); }
}
