package kome.common.data;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One-time faction-side quota rolls with bounded stack-equivalent effort weighting. */
public final class KOMEAllianceQuotaPool {
    private static final String PREFIX = "REQ3";
    public static final String VALID = "VALID";
    public static final String INVALID_REQUIREMENT = "INVALID_REQUIREMENT";

    private static final Spec[] SPECS = new Spec[] {
        spec(KOMEAlliance.CIVIL, 1, "minecraft", "bread", 0, 1, 1280, "common_raw"),
        spec(KOMEAlliance.CIVIL, 1, "minecraft", "cooked_beef", 0, 1, 1280, "common_raw"),
        spec(KOMEAlliance.CIVIL, 1, "minecraft", "carrot", 0, 1, 1280, "common_raw"),
        spec(KOMEAlliance.CIVIL, 1, "minecraft", "baked_potato", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.CIVIL, 2, "minecraft", "book", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.CIVIL, 2, "minecraft", "paper", 0, 1, 1280, "common_raw"),
        spec(KOMEAlliance.CIVIL, 2, "minecraft", "glass", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.CIVIL, 2, "minecraft", "wool", -1, 1, 1280, "common_raw"),

        spec(KOMEAlliance.TRADE, 1, "minecraft", "wheat", 0, 1, 1280, "common_raw"),
        spec(KOMEAlliance.TRADE, 1, "minecraft", "carrot", 0, 1, 1280, "common_raw"),
        spec(KOMEAlliance.TRADE, 1, "minecraft", "baked_potato", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.TRADE, 1, "minecraft", "leather", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.TRADE, 1, "minecraft", "chest", 0, 2, 960, "processed_common"),
        spec(KOMEAlliance.TRADE, 2, "minecraft", "book", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.TRADE, 2, "minecraft", "glass", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.TRADE, 2, "minecraft", "leather", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.TRADE, 2, "minecraft", "iron_ingot", 0, 4, 480, "valuable_stackable"),
        spec(KOMEAlliance.TRADE, 2, "minecraft", "gold_ingot", 0, 4, 320, "valuable_stackable"),

        spec(KOMEAlliance.MILITARY, 1, "minecraft", "bread", 0, 1, 1280, "common_raw"),
        spec(KOMEAlliance.MILITARY, 1, "minecraft", "cooked_beef", 0, 1, 1280, "common_raw"),
        spec(KOMEAlliance.MILITARY, 1, "minecraft", "leather", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.MILITARY, 1, "minecraft", "arrow", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.MILITARY, 1, "minecraft", "iron_sword", 0, 16, 64, "basic_equipment"),
        spec(KOMEAlliance.MILITARY, 2, "minecraft", "iron_ingot", 0, 4, 480, "valuable_stackable"),
        spec(KOMEAlliance.MILITARY, 2, "minecraft", "leather", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.MILITARY, 2, "minecraft", "arrow", 0, 2, 640, "processed_common"),
        spec(KOMEAlliance.MILITARY, 2, "minecraft", "cooked_beef", 0, 1, 1280, "common_raw"),
        spec(KOMEAlliance.MILITARY, 2, "minecraft", "iron_chestplate", 0, 32, 32, "expensive_equipment"),
        spec(KOMEAlliance.MILITARY, 2, "minecraft", "diamond_chestplate", 0, 64, 16, "high_cost_equipment"),
        spec(KOMEAlliance.MILITARY, 3, "minecraft", "iron_block", 0, 8, 240, "rare_stackable"),
        spec(KOMEAlliance.MILITARY, 3, "minecraft", "iron_ingot", 0, 4, 480, "valuable_stackable"),
        spec(KOMEAlliance.MILITARY, 3, "minecraft", "golden_carrot", 0, 4, 480, "valuable_stackable"),
        spec(KOMEAlliance.MILITARY, 3, "minecraft", "chest", 0, 2, 960, "processed_common")
    };

    private KOMEAllianceQuotaPool() {
    }

    private static Spec spec(String type, int tier, String modId, String itemName, int metadata,
            int effortWeight, int maximumQuantity, String category) {
        return new Spec(type, tier, modId, itemName, metadata, effortWeight, maximumQuantity, category);
    }

    public static String assignmentId(String type, int targetTier) {
        return KOMEAlliance.normalizeType(type) + ".t" + Math.max(1, targetTier) + ".items";
    }

    public static Requirement rollOnce(KOMEWorldData data, KOMEAlliance alliance, String type, int targetTier, String faction) {
        return roll(alliance, data, type, targetTier, faction, 0, false);
    }

    /** Standard-difficulty compatibility overload used by model tests and older callers. */
    public static Requirement rollOnce(KOMEAlliance alliance, String type, int targetTier, String faction) {
        return roll(alliance, null, type, targetTier, faction, 0, false);
    }

    public static Requirement reroll(KOMEWorldData data, KOMEAlliance alliance, String type, int targetTier,
            String faction, int salt) {
        if (alliance == null) {
            return null;
        }
        String normalizedType = KOMEAlliance.normalizeType(type);
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        Requirement replacement = select(data, alliance, normalizedType, targetTier, normalizedFaction, salt);
        if (replacement == null || !recoverDeliveredGoods(alliance, normalizedFaction,
                assignmentId(normalizedType, targetTier))) {
            return null;
        }
        alliance.setAssignment(normalizedFaction, assignmentId(normalizedType, targetTier), replacement.encode());
        return replacement;
    }

    public static Requirement reroll(KOMEAlliance alliance, String type, int targetTier, String faction, int salt) {
        return reroll(null, alliance, type, targetTier, faction, salt);
    }

    public static Requirement resolve(KOMEWorldData data, KOMEAlliance alliance, String faction, String type, int tier) {
        if (alliance == null) {
            return null;
        }
        Requirement saved = parse(alliance.getAssignment(faction, assignmentId(type, tier)));
        return saved == null ? null : validate(data, saved, type, tier);
    }

    /** Updates open quantities after difficulty/base-requirement changes without changing item identity. */
    public static boolean reconcileConfiguredQuantities(KOMEWorldData data) {
        if (data == null) {
            return false;
        }
        boolean changed = false;
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null) {
                continue;
            }
            for (String faction : new String[] {alliance.factionA, alliance.factionB}) {
                KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(faction);
                for (String type : new String[] {KOMEAlliance.CIVIL, KOMEAlliance.TRADE, KOMEAlliance.MILITARY}) {
                    for (int tier = 1; tier <= KOMEAlliance.maxTier(type); tier++) {
                        if (ledger != null && ledger.getCompletedTier(type) >= tier) {
                            continue;
                        }
                        String id = assignmentId(type, tier);
                        Requirement old = parse(alliance.getAssignment(faction, id));
                        if (old == null) {
                            continue;
                        }
                        Spec spec = findSpec(old.modId, old.itemName, old.metadata, type, tier);
                        Requirement adjusted;
                        if (spec == null || !enabled(data, spec)) {
                            adjusted = old.invalid("Pool entry is missing or disabled", max(data, spec), category(spec));
                        } else {
                            int weight = weight(data, spec);
                            int stacks = data.getAllianceItemStackEquivalents(type, tier);
                            int required = KOMEAllianceRequirements.weightedItemQuantity(stacks, weight, data.allianceDifficulty);
                            int maximum = max(data, spec);
                            adjusted = required > maximum
                                ? old.invalid("Calculated quantity " + required + " exceeds configured maximum " + maximum,
                                    maximum, spec.category)
                                : old.withRequired(required, weight, maximum, spec.category);
                        }
                        if (!old.encode().equals(adjusted.encode())) {
                            alliance.setAssignment(faction, id, adjusted.encode());
                            changed = true;
                        }
                    }
                }
            }
        }
        if (changed) {
            data.markDirty();
        }
        return changed;
    }

    /** Validates existing open rolls after load or per-item configuration changes without resizing valid rolls. */
    public static boolean validateExistingRequirements(KOMEWorldData data) {
        if (data == null) {
            return false;
        }
        boolean changed = false;
        for (KOMEAlliance alliance : data.alliances.values()) {
            if (alliance == null) {
                continue;
            }
            for (String faction : new String[] {alliance.factionA, alliance.factionB}) {
                KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(faction);
                for (String type : new String[] {KOMEAlliance.CIVIL, KOMEAlliance.TRADE, KOMEAlliance.MILITARY}) {
                    for (int tier = 1; tier <= KOMEAlliance.maxTier(type); tier++) {
                        if (ledger != null && ledger.getCompletedTier(type) >= tier) {
                            continue;
                        }
                        String id = assignmentId(type, tier);
                        Requirement old = parse(alliance.getAssignment(faction, id));
                        if (old == null) {
                            continue;
                        }
                        Requirement validated = validate(data, old, type, tier);
                        if (!old.encode().equals(validated.encode())) {
                            alliance.setAssignment(faction, id, validated.encode());
                            changed = true;
                        }
                    }
                }
            }
        }
        if (changed) {
            data.markDirty();
        }
        return changed;
    }

    public static Requirement parse(String encoded) {
        if (encoded == null || !(encoded.startsWith(PREFIX + "|") || encoded.startsWith("REQ2|"))) {
            return null;
        }
        String[] parts = encoded.split("\\|", -1);
        if (encoded.startsWith("REQ2|") && parts.length == 5) {
            try {
                String[] registry = parts[1].split(":", 2);
                int metadata = Integer.parseInt(parts[2]);
                int required = Integer.parseInt(parts[3]);
                if (registry.length != 2 || resolveItem(registry[0], registry[1]) == null || required <= 0) {
                    return null;
                }
                return new Requirement(registry[0], registry[1], metadata, required, 1, parts[4], 0,
                    "legacy", VALID, "");
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (parts.length < 8) {
            return null;
        }
        try {
            String modId = parts[1];
            String itemName = parts[2];
            int metadata = Integer.parseInt(parts[3]);
            int required = Integer.parseInt(parts[4]);
            int weight = KOMEAllianceRequirements.normalizeWeight(Integer.parseInt(parts[5]));
            if (resolveItem(modId, itemName) == null || required <= 0) {
                return null;
            }
            String state = parts.length > 8 && INVALID_REQUIREMENT.equals(parts[8]) ? INVALID_REQUIREMENT : VALID;
            int maximum = parts.length > 9 ? parsePositive(parts[9]) : 0;
            String category = parts.length > 10 ? parts[10] : "";
            String reason = parts.length > 11 ? parts[11] : "";
            return new Requirement(modId, itemName, metadata, required, weight, parts[7], maximum,
                category, state, reason);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public static int weightedQuantityForTest(int stackEquivalents, int weight, String difficulty) {
        return KOMEAllianceRequirements.weightedItemQuantity(stackEquivalents, weight, difficulty);
    }

    static int eligibleQuantityCountForTest(int stackEquivalents, String difficulty, int[] weights,
            int[] maximums, boolean[] enabled) {
        int count = 0;
        int length = Math.min(weights == null ? 0 : weights.length, maximums == null ? 0 : maximums.length);
        for (int i = 0; i < length; i++) {
            if ((enabled == null || i < enabled.length && enabled[i])
                    && KOMEAllianceRequirements.weightedItemQuantity(stackEquivalents, weights[i], difficulty) <= maximums[i]) {
                count++;
            }
        }
        return count;
    }

    static Requirement validateQuantityForTest(int required, int weight, int maximum, boolean enabled) {
        Requirement requirement = new Requirement("test", "item", 0, required, weight, "Test item",
            maximum, "test", VALID, "");
        if (!enabled) {
            return requirement.invalid("Pool entry is disabled", maximum, "test");
        }
        return required > maximum
            ? requirement.invalid("Rolled quantity exceeds configured maximum", maximum, "test")
            : requirement.valid(maximum, "test");
    }

    static boolean recoverDeliveredGoodsForTest(KOMEAlliance alliance, String faction, String id,
            ItemStack recoverySample) {
        return recoverDeliveredGoods(alliance, faction, id, recoverySample);
    }

    public static EntryView findEntry(KOMEWorldData data, String registryToken) {
        ItemRef ref = ItemRef.parse(registryToken);
        if (ref == null) {
            return null;
        }
        Spec first = null;
        Set<String> tracks = new LinkedHashSet<String>();
        int minimumTier = Integer.MAX_VALUE;
        for (Spec spec : SPECS) {
            if (spec.matches(ref)) {
                if (first == null) {
                    first = spec;
                }
                tracks.add(spec.type);
                minimumTier = Math.min(minimumTier, spec.tier);
            }
        }
        if (first == null) {
            return null;
        }
        StringBuilder allowed = new StringBuilder();
        for (String track : tracks) {
            if (allowed.length() > 0) {
                allowed.append(',');
            }
            allowed.append(track);
        }
        return new EntryView(first.key(), weight(data, first), max(data, first), enabled(data, first),
            first.category, allowed.toString(), minimumTier);
    }

    private static Requirement roll(KOMEAlliance alliance, KOMEWorldData data, String type, int targetTier,
            String faction, int salt, boolean force) {
        if (alliance == null || !KOMEAlliance.isValidType(type) || targetTier < 1
                || targetTier > KOMEAlliance.maxTier(type)) {
            return null;
        }
        String normalizedType = KOMEAlliance.normalizeType(type);
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        String id = assignmentId(normalizedType, targetTier);
        Requirement existing = resolve(data, alliance, normalizedFaction, normalizedType, targetTier);
        if (!force && existing != null) {
            return existing;
        }
        Requirement selected = select(data, alliance, normalizedType, targetTier, normalizedFaction, salt);
        if (selected != null) {
            alliance.setAssignment(normalizedFaction, id, selected.encode());
        }
        return selected;
    }

    private static Requirement select(KOMEWorldData data, KOMEAlliance alliance, String type, int targetTier,
            String faction, int salt) {
        if (alliance == null || !KOMEAlliance.isValidType(type) || targetTier < 1
                || targetTier > KOMEAlliance.maxTier(type)) {
            return null;
        }
        List<Requirement> eligible = new ArrayList<Requirement>();
        int stacks = data == null ? KOMEAllianceRequirements.standardItemStackEquivalents(type, targetTier)
            : data.getAllianceItemStackEquivalents(type, targetTier);
        String difficulty = data == null ? KOMEAllianceRequirements.STANDARD : data.allianceDifficulty;
        for (Spec spec : SPECS) {
            Item item = resolveItem(spec.modId, spec.itemName);
            if (!spec.matches(type, targetTier) || item == null || !enabled(data, spec)) {
                continue;
            }
            int effectiveWeight = weight(data, spec);
            int required = KOMEAllianceRequirements.weightedItemQuantity(stacks, effectiveWeight, difficulty);
            int maximum = max(data, spec);
            if (required > maximum) {
                continue;
            }
            ItemStack sample = new ItemStack(item, 1, spec.metadata < 0 ? 0 : spec.metadata);
            eligible.add(new Requirement(spec.modId, spec.itemName, spec.metadata, required, effectiveWeight,
                sample.getDisplayName(), maximum, spec.category, VALID, ""));
        }
        if (eligible.isEmpty()) {
            return null;
        }
        int selected = positiveHash(alliance.getPairKey() + "|" + type + "|" + targetTier + "|"
            + faction + "|" + salt) % eligible.size();
        return eligible.get(selected);
    }

    private static Requirement validate(KOMEWorldData data, Requirement saved, String type, int tier) {
        Spec spec = findSpec(saved.modId, saved.itemName, saved.metadata, type, tier);
        if (spec == null) {
            return saved.invalid("Rolled item is no longer an eligible pool entry", 0, saved.category);
        }
        int maximum = max(data, spec);
        if (!enabled(data, spec)) {
            return saved.invalid("Pool entry is disabled", maximum, spec.category);
        }
        if (saved.requiredUnits > maximum) {
            return saved.invalid("Rolled quantity " + saved.requiredUnits + " exceeds configured maximum " + maximum,
                maximum, spec.category);
        }
        return saved.valid(maximum, spec.category);
    }

    private static boolean recoverDeliveredGoods(KOMEAlliance alliance, String faction, String id) {
        KOMEAllianceFactionLedger ledger = alliance.getFactionLedger(faction);
        Requirement previous = parse(alliance.getAssignment(faction, id));
        int delivered = alliance.getDelivered(faction, id);
        int alreadyClaimable = ledger == null ? 0 : Math.min(delivered, ledger.getClaimAmount(id));
        int recover = Math.max(0, delivered - alreadyClaimable);
        Item item = previous == null ? null : resolveItem(previous.modId, previous.itemName);
        ItemStack sample = item == null || previous == null ? null
            : new ItemStack(item, 1, previous.metadata < 0 ? 0 : previous.metadata);
        return recoverDeliveredGoods(alliance, faction, id, sample);
    }

    private static boolean recoverDeliveredGoods(KOMEAlliance alliance, String faction, String id,
            ItemStack recoverySample) {
        KOMEAllianceFactionLedger ledger = alliance == null ? null : alliance.getFactionLedger(faction);
        int delivered = alliance == null ? 0 : alliance.getDelivered(faction, id);
        int alreadyClaimable = ledger == null ? 0 : Math.min(delivered, ledger.getClaimAmount(id));
        int recover = Math.max(0, delivered - alreadyClaimable);
        if (recover > 0 && (ledger == null || recoverySample == null)) {
            return false;
        }
        while (recover > 0) {
            ItemStack stack = recoverySample.copy();
            stack.stackSize = 1;
            int amount = Math.min(recover, Math.max(1, stack.getMaxStackSize()));
            stack.stackSize = amount;
            ledger.addRecoveryStack(stack);
            recover -= amount;
        }
        if (alliance != null) {
            alliance.setDelivered(faction, id, 0);
        }
        return true;
    }

    private static Spec findSpec(String modId, String itemName, int metadata, String type, int tier) {
        for (Spec spec : SPECS) {
            if (spec.modId.equalsIgnoreCase(modId) && spec.itemName.equalsIgnoreCase(itemName)
                    && (spec.metadata < 0 || metadata < 0 || spec.metadata == metadata) && spec.matches(type, tier)) {
                return spec;
            }
        }
        return null;
    }

    private static int weight(KOMEWorldData data, Spec spec) {
        return spec == null ? 1 : data == null ? spec.effortWeight : data.getAllianceQuotaWeight(spec.key(), spec.effortWeight);
    }

    private static int max(KOMEWorldData data, Spec spec) {
        return spec == null ? 0 : data == null ? spec.maximumQuantity : data.getAllianceQuotaMaximum(spec.key(), spec.maximumQuantity);
    }

    private static boolean enabled(KOMEWorldData data, Spec spec) {
        return spec != null && (data == null || data.isAllianceQuotaItemEnabled(spec.key(), true));
    }

    private static String category(Spec spec) {
        return spec == null ? "" : spec.category;
    }

    private static Item resolveItem(String modId, String itemName) {
        try {
            return GameRegistry.findItem(modId, itemName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int positiveHash(String value) {
        int hash = value == null ? 0 : value.hashCode();
        return hash == Integer.MIN_VALUE ? 0 : Math.abs(hash);
    }

    private static int parsePositive(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public static class Requirement {
        public final String modId;
        public final String itemName;
        public final int metadata;
        public final int requiredUnits;
        public final int effortWeight;
        /** Compatibility name for existing GUI code. */
        public final int pointValue;
        public final String displayName;
        public final int maximumQuantity;
        public final String category;
        public final String state;
        public final String invalidReason;

        private Requirement(String modId, String itemName, int metadata, int requiredUnits, int effortWeight,
                String displayName, int maximumQuantity, String category, String state, String invalidReason) {
            this.modId = modId;
            this.itemName = itemName;
            this.metadata = metadata;
            this.requiredUnits = Math.max(1, requiredUnits);
            this.effortWeight = KOMEAllianceRequirements.normalizeWeight(effortWeight);
            this.pointValue = this.effortWeight;
            this.displayName = clean(displayName == null || displayName.length() == 0 ? itemName : displayName);
            this.maximumQuantity = Math.max(0, maximumQuantity);
            this.category = clean(category);
            this.state = INVALID_REQUIREMENT.equals(state) ? INVALID_REQUIREMENT : VALID;
            this.invalidReason = clean(invalidReason);
        }

        public boolean isValid() {
            return VALID.equals(state);
        }

        public boolean matches(ItemStack stack) {
            return isValid() && stack != null && stack.getItem() == resolveItem(modId, itemName)
                && (metadata < 0 || stack.getItemDamage() == metadata);
        }

        public String display() {
            return isValid() ? "Deliver " + requiredUnits + " " + displayName
                : INVALID_REQUIREMENT + ": " + invalidReason;
        }

        private Requirement withRequired(int required, int weight, int maximum, String displayCategory) {
            return new Requirement(modId, itemName, metadata, required, weight, displayName, maximum,
                displayCategory, VALID, "");
        }

        private Requirement valid(int maximum, String displayCategory) {
            return new Requirement(modId, itemName, metadata, requiredUnits, effortWeight, displayName,
                maximum, displayCategory, VALID, "");
        }

        private Requirement invalid(String reason, int maximum, String displayCategory) {
            return new Requirement(modId, itemName, metadata, requiredUnits, effortWeight, displayName,
                maximum, displayCategory, INVALID_REQUIREMENT, reason);
        }

        private String encode() {
            return PREFIX + "|" + modId + "|" + itemName + "|" + metadata + "|" + requiredUnits + "|"
                + effortWeight + "|items|" + displayName + "|" + state + "|" + maximumQuantity + "|"
                + category + "|" + invalidReason;
        }

        private static String clean(String value) {
            return value == null ? "" : value.replace('|', ' ').replace('\t', ' ');
        }
    }

    public static final class EntryView {
        public final String key;
        public final int effortWeight;
        public final int maximumQuantity;
        public final boolean enabled;
        public final String category;
        public final String allowedTracks;
        public final int minimumTier;

        private EntryView(String key, int effortWeight, int maximumQuantity, boolean enabled, String category,
                String allowedTracks, int minimumTier) {
            this.key = key;
            this.effortWeight = effortWeight;
            this.maximumQuantity = maximumQuantity;
            this.enabled = enabled;
            this.category = category;
            this.allowedTracks = allowedTracks;
            this.minimumTier = minimumTier;
        }
    }

    private static final class Spec {
        final String type;
        final int tier;
        final String modId;
        final String itemName;
        final int metadata;
        final int effortWeight;
        final int maximumQuantity;
        final String category;

        private Spec(String type, int tier, String modId, String itemName, int metadata, int effortWeight,
                int maximumQuantity, String category) {
            this.type = type;
            this.tier = tier;
            this.modId = modId;
            this.itemName = itemName;
            this.metadata = metadata;
            this.effortWeight = effortWeight;
            this.maximumQuantity = maximumQuantity;
            this.category = category;
        }

        boolean matches(String requestedType, int requestedTier) {
            return type.equals(KOMEAlliance.normalizeType(requestedType)) && tier == requestedTier;
        }

        boolean matches(ItemRef ref) {
            return modId.equalsIgnoreCase(ref.modId) && itemName.equalsIgnoreCase(ref.itemName)
                && (!ref.metadataSpecified || metadata < 0 || metadata == ref.metadata);
        }

        String key() {
            return modId.toLowerCase() + ":" + itemName.toLowerCase() + ":" + metadata;
        }
    }

    private static final class ItemRef {
        final String modId;
        final String itemName;
        final int metadata;
        final boolean metadataSpecified;

        private ItemRef(String modId, String itemName, int metadata, boolean metadataSpecified) {
            this.modId = modId;
            this.itemName = itemName;
            this.metadata = metadata;
            this.metadataSpecified = metadataSpecified;
        }

        static ItemRef parse(String value) {
            if (value == null) {
                return null;
            }
            String[] parts = value.trim().toLowerCase().split(":");
            if (parts.length != 2 && parts.length != 3 || parts[0].length() == 0 || parts[1].length() == 0) {
                return null;
            }
            if (parts.length == 2) {
                return new ItemRef(parts[0], parts[1], 0, false);
            }
            try {
                return new ItemRef(parts[0], parts[1], Integer.parseInt(parts[2]), true);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
