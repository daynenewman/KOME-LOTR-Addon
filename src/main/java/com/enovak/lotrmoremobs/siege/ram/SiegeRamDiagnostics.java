package com.enovak.lotrmoremobs.siege.ram;

import cpw.mods.fml.common.FMLLog;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Temporary Phase 1C runtime instrumentation. Keys are deliberately runtime
 * only, bounded, and cleared with their world/client session; no ownership or
 * gameplay decision may depend on this class.
 */
public final class SiegeRamDiagnostics {

    private static final boolean ENABLED = false;
    private static final String PREFIX = "[SiegeRamDiag]";
    private static final int MAX_ONCE_KEYS = 8192;
    private static final Set<String> SERVER_ONCE = new HashSet<String>();
    private static final Set<String> CLIENT_ONCE = new HashSet<String>();

    private SiegeRamDiagnostics() {
    }

    public static void server(String event, String fields) {
        if (ENABLED) {
            FMLLog.info(PREFIX + "[SERVER][" + event + "] " + fields);
        }
    }

    public static synchronized void serverOnce(
            int dimension, String key, String event, String fields
    ) {
        if (!ENABLED) {
            return;
        }
        String scoped = dimension + ":" + key;
        if (remember(SERVER_ONCE, scoped)) {
            server(event, fields);
        }
    }

    public static void client(String event, String fields) {
        if (ENABLED) {
            FMLLog.info(PREFIX + "[CLIENT][" + event + "] " + fields);
        }
    }

    public static synchronized void clientOnce(
            String key, String event, String fields
    ) {
        if (!ENABLED) {
            return;
        }
        if (remember(CLIENT_ONCE, key)) {
            client(event, fields);
        }
    }

    public static synchronized void clearServerDimension(int dimension) {
        String prefix = dimension + ":";
        Iterator<String> iterator = SERVER_ONCE.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().startsWith(prefix)) {
                iterator.remove();
            }
        }
    }

    public static synchronized void clearClientSession() {
        CLIENT_ONCE.clear();
    }

    private static boolean remember(Set<String> keys, String key) {
        if (keys.contains(key)) {
            return false;
        }
        if (keys.size() >= MAX_ONCE_KEYS) {
            return false;
        }
        keys.add(key);
        return true;
    }
}
