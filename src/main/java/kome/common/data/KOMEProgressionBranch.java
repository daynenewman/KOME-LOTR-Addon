package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Extensible registry of canonical advancement branches. */
public final class KOMEProgressionBranch {
    private static final Map<String, KOMEProgressionBranch> BY_ID = new HashMap<String, KOMEProgressionBranch>();

    public final String id;
    public final String displayName;
    public final boolean specialty;
    public final Set<String> eligibleFactionKeys;
    public final int order;

    private KOMEProgressionBranch(String id, String displayName, boolean specialty, Set<String> eligibleFactionKeys, int order) {
        this.id = id;
        this.displayName = displayName;
        this.specialty = specialty;
        this.eligibleFactionKeys = Collections.unmodifiableSet(new HashSet<String>(eligibleFactionKeys));
        this.order = order;
    }

    public static synchronized KOMEProgressionBranch register(String id, String displayName, boolean specialty, Set<String> eligibleFactionKeys, int order) {
        String normalizedId = normalizeRequired(id, "branch id");
        if (displayName == null || displayName.trim().length() == 0 || order < 0) throw new IllegalArgumentException("Invalid progression branch metadata");
        Set<String> factions = normalizeFactions(eligibleFactionKeys);
        if (specialty != !factions.isEmpty()) throw new IllegalArgumentException("Specialty branches must have factions; core branches must be faction-neutral");
        if (BY_ID.containsKey(normalizedId)) throw new IllegalStateException("Duplicate progression branch id: " + normalizedId);
        KOMEProgressionBranch branch = new KOMEProgressionBranch(normalizedId, displayName, specialty, factions, order);
        BY_ID.put(normalizedId, branch);
        return branch;
    }

    public static KOMEProgressionBranch forId(String id) {
        if (id == null) return null;
        return BY_ID.get(id.trim().toLowerCase(Locale.ROOT));
    }

    public static List<KOMEProgressionBranch> all() {
        List<KOMEProgressionBranch> branches = new ArrayList<KOMEProgressionBranch>(BY_ID.values());
        Collections.sort(branches, new java.util.Comparator<KOMEProgressionBranch>() {
            public int compare(KOMEProgressionBranch first, KOMEProgressionBranch second) {
                int orderComparison = first.order - second.order;
                return orderComparison != 0 ? orderComparison : first.id.compareTo(second.id);
            }
        });
        return Collections.unmodifiableList(branches);
    }

    public boolean isEligibleForFaction(String factionKey) {
        return !specialty || (factionKey != null && eligibleFactionKeys.contains(factionKey.trim().toLowerCase(Locale.ROOT)));
    }

    public static boolean isEligibleForFaction(String branchId, String factionKey) {
        KOMEProgressionBranch branch = forId(branchId);
        return branch != null && branch.isEligibleForFaction(factionKey);
    }

    static void validateSchema() {
        if (BY_ID.isEmpty()) throw new IllegalStateException("No progression branches registered");
        Set<String> ids = new HashSet<String>();
        for (KOMEProgressionBranch branch : BY_ID.values()) {
            if (!ids.add(branch.id) || branch.order < 0 || branch.displayName.trim().length() == 0
                    || branch.specialty == branch.eligibleFactionKeys.isEmpty()) {
                throw new IllegalStateException("Invalid progression branch schema: " + branch.id);
            }
            for (String faction : branch.eligibleFactionKeys) normalizeRequired(faction, "eligible faction key");
        }
    }

    private static Set<String> normalizeFactions(Set<String> values) {
        Set<String> normalized = new HashSet<String>();
        if (values != null) for (String value : values) normalized.add(normalizeRequired(value, "eligible faction key"));
        return normalized;
    }

    private static String normalizeRequired(String value, String name) {
        if (value == null) throw new IllegalArgumentException("Missing " + name);
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_]+")) throw new IllegalArgumentException("Invalid " + name + ": " + value);
        return normalized;
    }

    private static Set<String> factions(String... values) {
        Set<String> factions = new HashSet<String>();
        Collections.addAll(factions, values);
        return factions;
    }

    static {
        register("exploration_adventure", "Exploration and Adventure", false, Collections.<String>emptySet(), 0);
        register("craft_lore", "Craft & Lore", false, Collections.<String>emptySet(), 1);
        register("husbandry_farming", "Husbandry and Farming", false, Collections.<String>emptySet(), 2);
        register("warfare_siegecraft", "Warfare and Siegecraft", false, Collections.<String>emptySet(), 3);
        register("rohan_horsemanship", "Horsemanship", true, factions("rohan"), 4);
        validateSchema();
    }
}
