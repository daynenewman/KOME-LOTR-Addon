package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Canonical explicit mutation and read boundary for political faction rulers. */
public final class KOMERulerService {
    private KOMERulerService() {
    }

    public static UUID getRuler(KOMEWorldData data, String factionKey) {
        return data == null ? null : data.readFactionKingId(factionKey);
    }

    public static String getRulerName(KOMEWorldData data, String factionKey) {
        return data == null ? "" : data.readFactionKingName(factionKey);
    }

    public static boolean hasRuler(KOMEWorldData data, String factionKey) {
        return getRuler(data, factionKey) != null;
    }

    public static boolean isRuler(KOMEWorldData data, String factionKey, UUID playerID) {
        return playerID != null && playerID.equals(getRuler(data, factionKey));
    }

    /** Explicitly assigns a ruler, replacing any existing ruler and stale cross-faction record. */
    public static boolean assignRuler(KOMEWorldData data, String factionKey, UUID playerID, String playerName) {
        if (data == null || playerID == null) {
            return false;
        }
        String key = normalize(factionKey);
        if (key.length() == 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Map.Entry<String, UUID> entry : data.factionKingRecordsSnapshot().entrySet()) {
            if (playerID.equals(entry.getValue()) && !key.equals(entry.getKey())) {
                changed |= removeRecord(data, entry.getKey(), now);
            }
        }
        UUID existing = data.readFactionKingId(key);
        if (existing != null && !playerID.equals(existing)) {
            changed |= removeRecord(data, key, now);
            existing = null;
        }
        String cleanName = playerName == null ? "" : playerName.trim();
        if (existing == null) {
            data.writeFactionKingRecord(key, playerID, cleanName);
            data.onFactionKingGained(key, now);
            data.markDirty();
            return true;
        }
        if (!cleanName.isEmpty() && !cleanName.equals(data.readFactionKingName(key))) {
            data.writeFactionKingRecord(key, playerID, cleanName);
            data.markDirty();
            changed = true;
        }
        return changed;
    }

    /** Explicitly removes the ruler for a faction; missing rulers are harmless and idempotent. */
    public static boolean removeRuler(KOMEWorldData data, String factionKey) {
        if (data == null) {
            return false;
        }
        return removeRecord(data, normalize(factionKey), System.currentTimeMillis());
    }

    /** Compatibility operation for legacy faction-change code that must remove a player's office. */
    public static boolean removeRulerHeldBy(KOMEWorldData data, UUID playerID) {
        if (data == null || playerID == null) {
            return false;
        }
        boolean changed = false;
        long now = System.currentTimeMillis();
        for (Map.Entry<String, UUID> entry : data.factionKingRecordsSnapshot().entrySet()) {
            if (playerID.equals(entry.getValue())) {
                changed |= removeRecord(data, entry.getKey(), now);
            }
        }
        return changed;
    }

    /** Repairs one existing record only; it never creates a ruler. */
    public static RepairResult repair(KOMEWorldData data, String factionKey, UUID authoritativePlayerID, String authoritativeName) {
        if (data == null) {
            return new RepairResult(false, "No world data");
        }
        String key = normalize(factionKey);
        if (key.length() == 0) {
            return new RepairResult(false, "Invalid faction key");
        }
        UUID current = data.readFactionKingId(key);
        if (current == null) {
            return new RepairResult(false, "No ruler record; no ruler invented");
        }
        String currentName = data.readFactionKingName(key);
        if (authoritativePlayerID != null && !authoritativePlayerID.equals(current)) {
            return new RepairResult(false, "Authoritative player does not match stored ruler");
        }
        String suppliedName = authoritativeName == null ? "" : authoritativeName.trim();
        if (suppliedName.length() > 0 && !suppliedName.equals(currentName)) {
            data.writeFactionKingRecord(key, current, suppliedName);
            data.markDirty();
            return new RepairResult(true, "Repaired cached ruler name");
        }
        if (currentName == null || currentName.trim().length() == 0) {
            return new RepairResult(false, "Ruler UUID is valid; cached name remains unresolved");
        }
        return new RepairResult(false, "Ruler record is valid");
    }

    public static List<RepairResult> repairAll(KOMEWorldData data) {
        if (data == null) {
            return Collections.emptyList();
        }
        List<RepairResult> results = new ArrayList<RepairResult>();
        for (String faction : data.factionKingRecordsSnapshot().keySet()) {
            results.add(repair(data, faction, null, null));
        }
        return results;
    }

    private static boolean removeRecord(KOMEWorldData data, String key, long now) {
        if (key == null || key.length() == 0 || !data.removeFactionKingRecord(key)) {
            return false;
        }
        data.onFactionKingLost(key, now);
        data.markDirty();
        return true;
    }

    private static String normalize(String factionKey) {
        return KOMEAlliance.normalizeFactionKey(factionKey);
    }

    public static final class RepairResult {
        public final boolean changed;
        public final String reason;

        private RepairResult(boolean changed, String reason) {
            this.changed = changed;
            this.reason = reason;
        }
    }
}
