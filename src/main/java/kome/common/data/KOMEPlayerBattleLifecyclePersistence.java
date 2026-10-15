package kome.common.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import static kome.common.data.KOMEPlayerBattleLifecycle.*;

/** Standalone strict v1 NBT format. Not installed in WorldData or any existing persistence section. */
public final class KOMEPlayerBattleLifecyclePersistence {
    public static final int FORMAT_VERSION = 1;
    private static final Set<String> ROOT_KEYS = keys("Version", "Records", "TerminalConflicts");
    private static final Set<String> ROW_KEYS = keys("PlayerId", "ConflictId", "TileId", "OriginalReceiptId",
        "Generation", "Cause", "InterruptedAtMillis", "EligibleAtMillis", "InterruptionActive",
        "ReenteredAtMillis", "FormalRetreatBar", "FormalRetreatAtMillis", "TerminalAtMillis");
    private static final Set<String> TERMINAL_KEYS = keys("ConflictId", "TerminalAtMillis");

    private KOMEPlayerBattleLifecyclePersistence() { }

    public static NBTTagCompound write(KOMEPlayerBattleLifecycleRegistry registry) {
        require(registry != null, "Lifecycle registry is required.");
        KOMEPlayerBattleLifecycleRegistry.Snapshot snapshot = registry.snapshot();
        KOMEPlayerBattleLifecycleRegistry.restore(snapshot.records.values(), snapshot.terminalConflicts);
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("Version", FORMAT_VERSION);
        NBTTagList rows = new NBTTagList();
        for (KOMEPlayerBattleLifecycle record : snapshot.records.values()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("PlayerId", record.key.playerId.toString());
            row.setString("ConflictId", record.key.conflictId);
            row.setString("TileId", record.tileId);
            row.setString("OriginalReceiptId", record.originalReceiptId);
            row.setLong("Generation", record.generation);
            row.setString("Cause", record.cause == null ? "" : record.cause.name());
            optional(row, "InterruptedAtMillis", record.interruptedAtMillis);
            optional(row, "EligibleAtMillis", record.eligibleAtMillis);
            row.setBoolean("InterruptionActive", record.interruptionActive);
            optional(row, "ReenteredAtMillis", record.reenteredAtMillis);
            row.setBoolean("FormalRetreatBar", record.isFormalRetreatBarred());
            optional(row, "FormalRetreatAtMillis", record.formalRetreatAtMillis);
            optional(row, "TerminalAtMillis", record.terminalAtMillis);
            rows.appendTag(row);
        }
        root.setTag("Records", rows);
        NBTTagList terminal = new NBTTagList();
        for (Map.Entry<String, Long> entry : snapshot.terminalConflicts.entrySet()) {
            NBTTagCompound row = new NBTTagCompound();
            row.setString("ConflictId", entry.getKey());
            row.setLong("TerminalAtMillis", entry.getValue());
            terminal.appendTag(row);
        }
        root.setTag("TerminalConflicts", terminal);
        return root;
    }

    /** Decode a detached registry or throw; missing/malformed input is never fresh empty state. */
    public static KOMEPlayerBattleLifecycleRegistry read(NBTTagCompound root) {
        knownKeys(root, ROOT_KEYS);
        type(root, "Version", 3);
        require(root.getInteger("Version") == FORMAT_VERSION, "Unsupported player lifecycle format version.");
        Map<String, Long> terminal = new TreeMap<String, Long>();
        NBTTagList terminalRows = list(root, "TerminalConflicts");
        for (int i = 0; i < terminalRows.tagCount(); i++) {
            NBTTagCompound row = terminalRows.getCompoundTagAt(i);
            knownKeys(row, TERMINAL_KEYS);
            String conflict = identity(text(row, "ConflictId"), "CF");
            long at = number(row, "TerminalAtMillis");
            require(at >= 0, "Terminal timestamp cannot be negative.");
            require(terminal.put(conflict, at) == null, "Duplicate terminal conflict marker.");
        }
        List<KOMEPlayerBattleLifecycle> decoded = new ArrayList<KOMEPlayerBattleLifecycle>();
        NBTTagList rows = list(root, "Records");
        for (int i = 0; i < rows.tagCount(); i++) {
            NBTTagCompound row = rows.getCompoundTagAt(i);
            knownKeys(row, ROW_KEYS);
            String player = text(row, "PlayerId");
            UUID playerId = UUID.fromString(player);
            require(playerId.toString().equals(player), "Noncanonical player UUID.");
            Key key = new Key(playerId, text(row, "ConflictId"));
            String cause = text(row, "Cause");
            Long retreatAt = optional(row, "FormalRetreatAtMillis");
            require(bool(row, "FormalRetreatBar") == (retreatAt != null), "Retreat bar and timestamp disagree.");
            decoded.add(new KOMEPlayerBattleLifecycle(key, text(row, "TileId"), text(row, "OriginalReceiptId"),
                number(row, "Generation"), cause.isEmpty() ? null : Cause.valueOf(cause),
                optional(row, "InterruptedAtMillis"), optional(row, "EligibleAtMillis"),
                bool(row, "InterruptionActive"), optional(row, "ReenteredAtMillis"),
                retreatAt, optional(row, "TerminalAtMillis")));
        }
        return KOMEPlayerBattleLifecycleRegistry.restore(decoded, terminal);
    }

    private static void knownKeys(NBTTagCompound tag, Set<String> known) {
        require(tag != null, "Lifecycle compound is required.");
        for (Object key : tag.func_150296_c()) require(known.contains(key), "Unknown lifecycle field: " + key);
    }
    private static Set<String> keys(String... values) {
        return java.util.Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }
    private static void type(NBTTagCompound tag, String key, int type) {
        require(tag.hasKey(key, type), "Missing or wrong-typed lifecycle field: " + key);
    }
    private static String text(NBTTagCompound tag, String key) { type(tag, key, 8); return tag.getString(key); }
    private static long number(NBTTagCompound tag, String key) { type(tag, key, 4); return tag.getLong(key); }
    private static boolean bool(NBTTagCompound tag, String key) {
        type(tag, key, 1);
        byte value = tag.getByte(key);
        require(value == 0 || value == 1, "Lifecycle boolean must be zero or one: " + key);
        return value == 1;
    }
    private static Long optional(NBTTagCompound tag, String key) { return tag.hasKey(key) ? number(tag, key) : null; }
    private static void optional(NBTTagCompound tag, String key, Long value) { if (value != null) tag.setLong(key, value); }
    private static NBTTagList list(NBTTagCompound tag, String key) {
        type(tag, key, 9);
        NBTTagList rows = (NBTTagList) tag.getTag(key);
        require(rows.tagCount() == 0 || rows.func_150303_d() == 10, "Lifecycle list must contain compounds: " + key);
        return rows;
    }
}
