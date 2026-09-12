package kome.common.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import kome.common.KOMEReflection;
import kome.common.config.KOMEConfigRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Sole authority for deciding whether restricted gear may become active.  Inventory ownership,
 * storage, and trophy display deliberately do not invoke a restriction denial.
 */
public final class KOMEGearRestrictionService {
    public enum Category { MITHRIL, UTUMNO, GONDOLIN, MALLORN, MORGUL, GALVORN, STONE_TOOL, BOSS, FUTURE }
    public enum Action { POSSESS, STORE, DISPLAY, HOLD, EQUIP_ARMOR, USE, NPC_EQUIP }
    public enum Reason { ALLOWED, UNRESTRICTED, POSSESSION_ALLOWED, MISSING_PROGRESSION, FACTION_NOT_PERMITTED, NPC_NOT_PERMITTED, BOSS_EXCLUSIVE }

    public static final class Decision {
        public final boolean allowed;
        public final Category category;
        public final Reason reason;
        private Decision(boolean allowed, Category category, Reason reason) { this.allowed = allowed; this.category = category; this.reason = reason; }
        public String playerMessage() {
            if (allowed) return "";
            if (reason == Reason.MISSING_PROGRESSION) return "You have not unlocked the progression required to use this " + category.name().toLowerCase(Locale.ROOT) + " item.";
            if (reason == Reason.FACTION_NOT_PERMITTED) return "Your faction is not permitted to use this " + category.name().toLowerCase(Locale.ROOT) + " item.";
            if (reason == Reason.BOSS_EXCLUSIVE) return "This boss-exclusive item cannot be used by players.";
            return "This " + category.name().toLowerCase(Locale.ROOT) + " item cannot be equipped or used here.";
        }
    }

    public static final class Rule {
        public final Category category;
        public final String itemId, gearPermission, armorPermission;
        public final Set<String> permittedFactions;
        public final boolean npcAllowed, bossExclusive;
        public Rule(Category category, String itemId, String gearPermission, String armorPermission,
                Set<String> permittedFactions, boolean npcAllowed, boolean bossExclusive) {
            if (category == null || normalize(itemId).length() == 0) throw new IllegalArgumentException("Gear rules require a category and item registry ID");
            this.category = category; this.itemId = normalize(itemId); this.gearPermission = normalize(gearPermission);
            this.armorPermission = normalize(armorPermission);
            Set<String> factions = new LinkedHashSet<String>();
            if (permittedFactions != null) for (String faction : permittedFactions) {
                String value = normalize(faction); if (value.length() == 0) throw new IllegalArgumentException("Faction keys must not be blank"); factions.add(value);
            }
            this.permittedFactions = Collections.unmodifiableSet(factions);
            this.npcAllowed = npcAllowed; this.bossExclusive = bossExclusive;
        }
    }

    private static final Map<String, Rule> DEFAULT_RULES = defaults();
    private KOMEGearRestrictionService() { }

    /** Machine-readable default mapping; configured rules replace an item mapping by stable registry ID. */
    public static Map<String, Rule> rules() {
        Map<String, Rule> result = new LinkedHashMap<String, Rule>(DEFAULT_RULES);
        for (KOMEConfigRegistry.GearRuleSetting setting : KOMEConfigRegistry.gear().getRulesByItemId().values()) {
            Category category;
            try { category = Category.valueOf(setting.getCategory().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { throw new IllegalStateException("Unknown configured gear category: " + setting.getCategory(), e); }
            result.put(normalize(setting.getItemId()), new Rule(category, setting.getItemId(), setting.getGearPermission(),
                    setting.getArmorPermission(), setting.getPermittedFactions(), setting.isNpcAllowed(), setting.isBossExclusive()));
        }
        return Collections.unmodifiableMap(result);
    }

    public static Rule ruleFor(ItemStack stack) { return stack == null ? null : rules().get(itemId(stack)); }
    public static Rule ruleForId(String itemId) { return rules().get(normalize(itemId)); }

    public static Decision evaluatePlayer(EntityPlayer player, ItemStack stack, Action action) {
        Rule rule = ruleFor(stack);
        if (rule == null) return new Decision(true, null, Reason.UNRESTRICTED);
        String permission = action == Action.EQUIP_ARMOR ? rule.armorPermission : rule.gearPermission;
        String faction = player == null ? "" : KOMEWorldData.get(KOMEReflection.getWorld(player)).getPlayerFactionKey(KOMEReflection.getEntityUUID(player));
        return evaluate(rule, action, permission.length() == 0 || KOMEProgressionPermissions.has(player, permission), faction);
    }

    /** Deterministic form used by UI/admin consumers and tests without a live world. */
    public static Decision evaluate(Rule rule, Action action, boolean hasProgression, String faction) {
        if (rule == null) return new Decision(true, null, Reason.UNRESTRICTED);
        if (isPassive(action)) return new Decision(true, rule.category, Reason.POSSESSION_ALLOWED);
        if (rule.bossExclusive) return new Decision(false, rule.category, Reason.BOSS_EXCLUSIVE);
        String permission = action == Action.EQUIP_ARMOR ? rule.armorPermission : rule.gearPermission;
        if (permission.length() != 0 && !hasProgression) return new Decision(false, rule.category, Reason.MISSING_PROGRESSION);
        if (!rule.permittedFactions.isEmpty() && !rule.permittedFactions.contains(normalize(faction))) return new Decision(false, rule.category, Reason.FACTION_NOT_PERMITTED);
        return new Decision(true, rule.category, Reason.ALLOWED);
    }

    /** NPC legality deliberately has no player progression dependency. */
    public static Decision evaluateNpc(String npcFaction, ItemStack stack) {
        Rule rule = ruleFor(stack);
        if (rule == null) return new Decision(true, null, Reason.UNRESTRICTED);
        return evaluateNpc(rule, npcFaction);
    }

    public static Decision evaluateNpc(Rule rule, String npcFaction) {
        if (rule == null) return new Decision(true, null, Reason.UNRESTRICTED);
        if (!rule.npcAllowed || rule.bossExclusive) return new Decision(false, rule.category, rule.bossExclusive ? Reason.BOSS_EXCLUSIVE : Reason.NPC_NOT_PERMITTED);
        if (!rule.permittedFactions.isEmpty() && !rule.permittedFactions.contains(normalize(npcFaction))) return new Decision(false, rule.category, Reason.FACTION_NOT_PERMITTED);
        return new Decision(true, rule.category, Reason.ALLOWED);
    }

    /** A future rule can be represented in data without changing any event handler. */
    public static Map<String, Rule> withRule(Map<String, Rule> base, Rule rule) {
        Map<String, Rule> copy = new LinkedHashMap<String, Rule>(base == null ? Collections.<String, Rule>emptyMap() : base);
        copy.put(rule.itemId, rule);
        return Collections.unmodifiableMap(copy);
    }

    public static boolean isStoneTool(ItemStack stack) {
        return stack != null && ruleFor(stack) != null && ruleFor(stack).category == Category.STONE_TOOL;
    }

    private static boolean isPassive(Action action) { return action == Action.POSSESS || action == Action.STORE || action == Action.DISPLAY; }
    private static String itemId(ItemStack stack) { Object id = Item.itemRegistry.getNameForObject(stack.getItem()); return normalize(id == null ? "" : id.toString()); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }
    private static Set<String> noFactions() { return Collections.emptySet(); }
    private static void add(Map<String, Rule> rules, Category category, String permission, String armorPermission, String... ids) {
        for (String id : ids) rules.put(normalize(id), new Rule(category, id, permission, armorPermission, noFactions(), true, false));
    }
    private static Map<String, Rule> defaults() {
        Map<String, Rule> rules = new LinkedHashMap<String, Rule>();
        add(rules, Category.MITHRIL, "baseline.mithril_gear", "baseline.mithril_gear", "lotr:shovelMithril", "lotr:pickaxeMithril", "lotr:axeMithril", "lotr:swordMithril", "lotr:hoeMithril", "lotr:helmetMithril", "lotr:bodyMithril", "lotr:legsMithril", "lotr:bootsMithril", "lotr:spearMithril", "lotr:daggerMithril", "lotr:battleaxeMithril", "lotr:hammerMithril", "lotr:mithrilCrossbow", "lotr:daggerMithrilPoisoned", "lotr:horseArmorMithril", "lotr:helmetDwarvenMithril", "lotr:bodyDwarvenMithril", "lotr:legsDwarvenMithril", "lotr:bootsDwarvenMithril", "lotr:halberdMithril", "lotr:mattockMithril", "lotr:mithrilMail");
        add(rules, Category.UTUMNO, "baseline.utumno_gear", "baseline.utumno_gear", "lotr:helmetUtumno", "lotr:bodyUtumno", "lotr:legsUtumno", "lotr:bootsUtumno", "lotr:swordUtumno", "lotr:daggerUtumno", "lotr:daggerUtumnoPoisoned", "lotr:spearUtumno", "lotr:battleaxeUtumno", "lotr:hammerUtumno", "lotr:utumnoBow", "lotr:utumnoPickaxe");
        add(rules, Category.GONDOLIN, "baseline.faction_gear", "baseline.faction_armor", "lotr:swordGondolin", "lotr:helmetGondolin", "lotr:bodyGondolin", "lotr:legsGondolin", "lotr:bootsGondolin");
        add(rules, Category.MALLORN, "baseline.non_faction_gear", "baseline.non_faction_armor", "lotr:shovelMallorn", "lotr:pickaxeMallorn", "lotr:axeMallorn", "lotr:swordMallorn", "lotr:hoeMallorn", "lotr:mallornBow", "lotr:maceMallornCharred");
        add(rules, Category.MORGUL, "baseline.faction_gear", "baseline.faction_armor", "lotr:morgulBlade", "lotr:helmetMorgul", "lotr:bodyMorgul", "lotr:legsMorgul", "lotr:bootsMorgul", "lotr:horseArmorMorgul");
        add(rules, Category.GALVORN, "baseline.faction_gear", "baseline.faction_armor", "lotr:helmetGalvorn", "lotr:bodyGalvorn", "lotr:legsGalvorn", "lotr:bootsGalvorn");
        add(rules, Category.STONE_TOOL, "baseline.stonework", "baseline.stonework", "minecraft:stone_sword", "minecraft:stone_pickaxe", "minecraft:stone_axe", "minecraft:stone_shovel", "minecraft:stone_hoe", "lotr:spearStone");
        return Collections.unmodifiableMap(rules);
    }
}
