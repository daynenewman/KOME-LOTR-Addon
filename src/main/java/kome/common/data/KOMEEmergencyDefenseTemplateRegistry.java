package kome.common.data;

import lotr.common.LOTRMod;
import lotr.common.enchant.LOTREnchantment;
import lotr.common.enchant.LOTREnchantmentHelper;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRUnitTradeEntry;
import lotr.common.entity.npc.LOTRUnitTradeEntries;
import lotr.common.item.LOTRWeaponStats;
import net.minecraft.entity.EntityList;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Explicit one-template-per-native-faction Emergency Defense roster. */
public final class KOMEEmergencyDefenseTemplateRegistry {
    private static final String RESOURCE =
        "assets/kome/config/kome_emergency_defense_templates.csv";
    private static final Map<String, Template> TEMPLATES = load();

    private KOMEEmergencyDefenseTemplateRegistry() { }

    public static Template require(String factionId) {
        Template template = TEMPLATES.get(KOMEAlliance.normalizeFactionKey(factionId));
        if (template == null)
            throw new IllegalArgumentException("No Emergency Defense template for faction.");
        return template;
    }

    public static Map<String, Template> all() { return TEMPLATES; }

    public static void validateDefinitions() {
        if (!TEMPLATES.keySet().equals(new java.util.HashSet<String>(
                KOMEAlliance.allFactionKeys())))
            throw new IllegalStateException("Emergency Defense faction coverage is incomplete.");
        for (Template template : TEMPLATES.values()) {
            if (template.entException) continue;
            requireItemField(template.weaponField);
            for (String armor : template.armorFields)
                if (armor.length() > 0) requireItemField(armor);
        }
    }

    /** Called from the live integrated-server lifecycle after LOTR entity/item bootstrap. */
    public static void validateLivePolicies(World world) {
        if (world == null) throw new IllegalArgumentException("Live template validation needs a world.");
        validateDefinitions();
        java.util.List<String> failures = new java.util.ArrayList<String>();
        for (Template template : TEMPLATES.values()) {
            try {
                template.validateEquipmentPolicy();
                if (template.populationCost(world) <= 0)
                    throw new IllegalStateException("population cost is invalid");
            } catch (RuntimeException failure) {
                failures.add(template.id + " (faction=" + template.faction + "): "
                    + (failure.getMessage() == null
                        ? failure.getClass().getSimpleName() : failure.getMessage()));
            }
        }
        if (!failures.isEmpty())
            throw new IllegalStateException("Emergency Defense live template validation failed: "
                + failures);
    }

    private static void requireItemField(String field) {
        try {
            if (!Item.class.isAssignableFrom(LOTRMod.class.getField(field).getType()))
                throw new IllegalArgumentException("Not a LOTR item field: " + field);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalArgumentException("Unknown LOTR equipment field: " + field, failure);
        }
    }

    public static final class Template {
        public final String id, faction, source, weaponField;
        public final int entryIndex, nativeArmorExceptionProtection;
        public final String[] armorFields;
        public final boolean lightArmorException, entException, nativeArmorException;
        public final String nativeArmorExceptionReason;

        private Template(String faction, String source, int index, String weapon,
                String[] armor, boolean light, boolean ent, boolean nativeArmor,
                int nativeArmorProtection, String nativeArmorReason) {
            this.faction = KOMEAlliance.normalizeFactionKey(faction);
            this.source = source;
            entryIndex = index;
            weaponField = weapon;
            armorFields = armor;
            lightArmorException = light;
            entException = ent;
            nativeArmorException = nativeArmor;
            nativeArmorExceptionProtection = nativeArmorProtection;
            nativeArmorExceptionReason = nativeArmorReason == null
                ? "" : nativeArmorReason.trim();
            id = "EDT-" + this.faction.toUpperCase(java.util.Locale.ROOT);
            int exceptionCount = (light ? 1 : 0) + (ent ? 1 : 0) + (nativeArmor ? 1 : 0);
            if (exceptionCount > 1)
                throw new IllegalArgumentException("Conflicting armor exceptions for " + id);
            if (nativeArmor) {
                if (nativeArmorProtection <= 0 || nativeArmorProtection >= 20
                        || nativeArmorExceptionReason.length() == 0)
                    throw new IllegalArgumentException(
                        "Invalid native-equipment armor exception for " + id);
            } else if (nativeArmorProtection != 0
                    || nativeArmorExceptionReason.length() != 0)
                throw new IllegalArgumentException("Unexpected native armor exception data for " + id);
        }

        public LOTREntityNPC create(World world) {
            LOTREntityNPC npc = instantiate(world);
            if (!entException) equip(npc);
            return npc;
        }

        private LOTREntityNPC instantiate(World world) {
            LOTRUnitTradeEntry trade = trade();
            LOTREntityNPC npc = null;
            try { npc = trade.getOrCreateHiredNPC(world); }
            catch (RuntimeException missingEntityRegistry) {
                // Dedicated tests may not bootstrap Forge's EntityList. The exact same native
                // class remains authoritative; production normally takes the registered path.
            }
            if (npc == null) {
                try {
                    npc = (LOTREntityNPC) trade.entityClass
                        .getConstructor(World.class).newInstance(world);
                    npc.initCreatureForHire(null);
                    npc.refreshCurrentAttackMode();
                } catch (ReflectiveOperationException unavailable) {
                    throw new IllegalStateException(
                        "Emergency defender template did not create an NPC.", unavailable);
                }
            }
            if (npc.hiredNPCInfo.isActive || npc.hiredNPCInfo.getHiringPlayerUUID() != null)
                throw new IllegalStateException("Emergency defender unexpectedly has player ownership.");
            String nativeFaction = npc.getFaction() == null ? ""
                : KOMEAlliance.normalizeFactionKey(npc.getFaction().codeName());
            if (!faction.equals(nativeFaction))
                throw new IllegalStateException("Emergency defender native faction mismatch: " + id);
            return npc;
        }

        public int populationCost(World world) {
            // Population price is entity type + base health authority; equipment is deliberately
            // irrelevant and is finalized only for a physical deployment candidate.
            LOTREntityNPC probe = instantiate(world);
            try {
                String entityId = EntityList.getEntityString(probe);
                return KOMEUnitPopulationCostService.calculate(entityId,
                    (int) Math.ceil(probe.getMaxHealth()), false, false);
            } finally { probe.setDead(); }
        }

        /** Startup/test validation without constructing an entity or requiring a loaded world. */
        public int validateEquipmentPolicy() {
            if (entException) return 0;
            ItemStack weapon = stack(weaponField);
            add(weapon, LOTREnchantment.strong4);
            add(weapon, LOTREnchantment.meleeSpeed1);
            add(weapon, LOTREnchantment.meleeReach1);
            int protection = 0;
            for (int index = 0; index < armorFields.length; index++) {
                if (armorFields[index].length() == 0) continue;
                ItemStack armor = stack(armorFields[index]);
                applyArmorPolicy(armor, index == 3);
                protection += LOTRWeaponStats.getArmorProtection(armor);
            }
            validateArmorProtection(protection);
            return protection;
        }

        void validateArmorProtection(int protection) {
            if (nativeArmorException) {
                if (protection != nativeArmorExceptionProtection)
                    throw new IllegalStateException("Emergency Defense native armor exception "
                        + id + " expected " + nativeArmorExceptionProtection + " but finalized at "
                        + protection + ": " + nativeArmorExceptionReason);
            } else if (!lightArmorException && !entException && protection < 20)
                throw new IllegalStateException("Emergency Defense armor below 20 for " + id
                    + ": " + protection);
        }

        private LOTRUnitTradeEntry trade() {
            if (entException) return KOMEMusterNativeRoster.nativeTrade(
                new KOMEMusterNativeRoster.Definition(faction, "ENT", 0, 1));
            try {
                LOTRUnitTradeEntries table = (LOTRUnitTradeEntries)
                    LOTRUnitTradeEntries.class.getField(source).get(null);
                if (entryIndex < 0 || entryIndex >= table.tradeEntries.length)
                    throw new IllegalArgumentException("Emergency Defense trade index is invalid: " + id);
                return table.tradeEntries[entryIndex];
            } catch (ReflectiveOperationException failure) {
                throw new IllegalArgumentException("Emergency Defense trade source is invalid: " + id, failure);
            }
        }

        private void equip(LOTREntityNPC npc) {
            ItemStack weapon = stack(weaponField);
            add(weapon, LOTREnchantment.strong4);
            add(weapon, LOTREnchantment.meleeSpeed1);
            add(weapon, LOTREnchantment.meleeReach1);
            npc.npcItemsInv.setMeleeWeapon(weapon.copy());
            npc.npcItemsInv.setMeleeWeaponMounted(weapon.copy());
            npc.npcItemsInv.setIdleItem(weapon.copy());
            npc.npcItemsInv.setIdleItemMounted(weapon.copy());
            npc.setCurrentItemOrArmor(0, weapon.copy());
            int protection = 0;
            for (int index = 0; index < armorFields.length; index++) {
                if (armorFields[index].length() == 0) continue;
                ItemStack armor = stack(armorFields[index]);
                applyArmorPolicy(armor, index == 3);
                npc.setCurrentItemOrArmor(index == 0 ? 4 : index == 1 ? 3 : index == 2 ? 2 : 1,
                    armor.copy());
                protection += LOTRWeaponStats.getArmorProtection(armor);
            }
            validateArmorProtection(protection);
            npc.refreshCurrentAttackMode();
        }

        /** Applies the maximum legal LOTR protection policy for this individual armor piece. */
        static void applyArmorPolicy(ItemStack armor, boolean boots) {
            applyStrongestCompatible(armor,
                LOTREnchantment.protect2, LOTREnchantment.protect1);
            applyStrongestCompatible(armor,
                LOTREnchantment.protectRanged3,
                LOTREnchantment.protectRanged2,
                LOTREnchantment.protectRanged1);
            if (boots) applyStrongestCompatible(armor,
                LOTREnchantment.protectFall3,
                LOTREnchantment.protectFall2,
                LOTREnchantment.protectFall1);
        }

        /** Candidates must be ordered strongest-first; no illegal modifier is forced. */
        static LOTREnchantment applyStrongestCompatible(ItemStack stack,
                LOTREnchantment... candidates) {
            for (LOTREnchantment candidate : candidates) {
                if (candidate.canApply(stack, false)
                        && LOTREnchantmentHelper.checkEnchantCompatible(stack, candidate)) {
                    LOTREnchantmentHelper.setHasEnchant(stack, candidate);
                    return candidate;
                }
            }
            return null;
        }

        private static ItemStack stack(String field) {
            if (field == null || field.length() == 0)
                throw new IllegalArgumentException("Missing Emergency Defense equipment field.");
            try {
                Item item = (Item) LOTRMod.class.getField(field).get(null);
                if (item == null) throw new IllegalArgumentException("Null LOTR item: " + field);
                return new ItemStack(item);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalArgumentException("Unknown LOTR equipment field: " + field, failure);
            }
        }

        private void add(ItemStack stack, LOTREnchantment enchantment) {
            if (!enchantment.canApply(stack, false)
                    || !LOTREnchantmentHelper.checkEnchantCompatible(stack, enchantment))
                throw new IllegalStateException("Emergency Defense template " + id
                    + " (faction=" + faction + ", item=" + stack.getDisplayName()
                    + ") is incompatible with required LOTR modifier "
                    + enchantment.enchantName + ".");
            LOTREnchantmentHelper.setHasEnchant(stack, enchantment);
        }
    }

    private static Map<String, Template> load() {
        InputStream input = KOMEEmergencyDefenseTemplateRegistry.class.getClassLoader()
            .getResourceAsStream(RESOURCE);
        if (input == null) throw new IllegalStateException("Missing Emergency Defense templates.");
        Map<String, Template> result = new LinkedHashMap<String, Template>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"))) {
            if (!"faction_id,source,entry_index,weapon,helmet,body,legs,boots,light_exception,ent_exception,native_equipment_exception,native_exception_protection,native_exception_reason"
                    .equals(reader.readLine()))
                throw new IllegalArgumentException("Invalid Emergency Defense template header.");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().length() == 0 || line.startsWith("#")) continue;
                String[] f = line.split(",", -1);
                if (f.length != 13) throw new IllegalArgumentException("Invalid template row: " + line);
                Template template = new Template(f[0], f[1], Integer.parseInt(f[2]), f[3],
                    new String[] {f[4], f[5], f[6], f[7]},
                    Boolean.parseBoolean(f[8]), Boolean.parseBoolean(f[9]),
                    Boolean.parseBoolean(f[10]), Integer.parseInt(f[11]), f[12]);
                if (result.put(template.faction, template) != null)
                    throw new IllegalArgumentException("Duplicate Emergency Defense faction.");
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read Emergency Defense templates.", failure);
        }
        if (!result.keySet().equals(new java.util.HashSet<String>(KOMEAlliance.allFactionKeys())))
            throw new IllegalArgumentException("Emergency Defense templates must cover all factions.");
        return Collections.unmodifiableMap(result);
    }
}
