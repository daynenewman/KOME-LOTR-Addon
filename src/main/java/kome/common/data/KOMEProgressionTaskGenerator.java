package kome.common.data;

import java.util.Random;

public class KOMEProgressionTaskGenerator {
    private static final String[] RANDOMS = new String[] {
        "Find or obtain: Fir Wood Slab",
        "Find or obtain: Uruk",
        "Find or obtain: Lebennin Banner Bearer",
        "Find or obtain: Golden Goblet",
        "Find or obtain: Arrow",
        "Find or obtain: Clay Tiling Wall",
        "Find or obtain: Seagull",
        "Find or obtain: Coast Southron Tavern",
        "Find or obtain: Carved Cargon Brick",
        "Find or obtain: Ancient Sword Blade",
        "Find or obtain: Galadhrim Brick",
        "Find or obtain: Ender Chest",
        "Find or obtain: Vodka",
        "Find or obtain: Mordor Spear",
        "Find or obtain: Hardened Clay",
        "Find or obtain: Rhunic Fire-pot",
        "Find or obtain: Yellow Fine Glass Pane",
        "Find or obtain: Block of Redstone",
        "Find or obtain: Golden Axe",
        "Find or obtain: Hill-troll Chieftain",
        "Find or obtain: Bree-land Village",
        "Find or obtain: Chestnut Wood Planks",
        "Find or obtain: Silver Ingot",
        "Find or obtain: Wood-elven Spear",
        "Find or obtain: Bree-land Stables",
        "Find or obtain: Mallorn Door",
        "Find or obtain: Rohan Leggings",
        "Find or obtain: Obsidian Dwarven Brick Stairs",
        "Find or obtain: Raw Mutton",
        "Find or obtain: Alloy Forge",
        "Find or obtain: Mallorn Ent",
        "Find or obtain: Elven Treehouse"
    };

    private static final QuotaOption[] FOODS = new QuotaOption[] {
        stacks("Melon", 9), units("Dalish Pastry", 73), stacks("Poisonous Potato", 23), stacks("Roast Chestnut", 17),
        units("Torog Stew", 91), stacks("Cooked Venison", 27), stacks("Almond", 12), units("Melon Soup", 36),
        stacks("Cooked Lion Meat", 14), stacks("Raw Salmon", 30), units("Hobbit Pancake with Syrup", 35),
        stacks("Cooked Fish", 15), stacks("Rhino Meat", 10), stacks("Leek", 24), stacks("Cranberries", 29),
        stacks("Cherry", 32), stacks("Mutton", 31), stacks("Porkchop", 7), units("Mushroom Pie", 60),
        units("Chocolate Marchpane", 89), stacks("Morgul-shroom", 5), stacks("Green Grapes", 13),
        stacks("Rabbit Meat", 18), stacks("Cooked Camel Meat", 28), stacks("Lettuce", 25), units("Shish Kebab", 109),
        stacks("Suspicious Meat", 62), stacks("Orange", 68), stacks("Cooked Rhino Meat", 90),
        stacks("Golden Carrot", 77), stacks("Cooked Potato", 112), stacks("Lembas", 17), stacks("Salt", 28),
        units("Banana Bread", 92), stacks("Pear", 100), units("Hobbit Pancake", 99), stacks("Maggoty Bread", 108),
        stacks("Egg", 42), stacks("Cookie", 95), stacks("Raspberries", 105), stacks("Lion Meat", 118),
        stacks("Yam", 37), stacks("Steak", 31), stacks("Mirk-shroom", 13), stacks("Corn", 111),
        stacks("Cooked Rabbit Meat", 51), stacks("Blueberries", 30), stacks("Camel Meat", 103)
    };

    private static final DrinkOption[] DRINKS = new DrinkOption[] {
        drink("Cider", 114), drink("Orange Juice", 119), drink("Corn Liquor", 41), drink("Lime Liqueur", 105),
        drink("Pomegranate Juice", 82), drink("Termite Tequila", 104), drink("Plum Kvass", 95),
        drink("Chocolate Milk", 68), drink("Huorn Leaf", 33), drink("Apple Juice", 93),
        drink("Lemon Liqueur", 126), drink("Bottle of Poison", 37), drink("Grape Juice", 46),
        drink("Milk", 53), drink("Athelas Brew", 88), drink("Mead", 117), drink("Maple Beer", 80),
        drink("Water", 52), drink("Melon Liqueur", 112), drink("Arak", 98), drink("Wine", 54),
        drink("Taurethrim Cocoa", 128), drink("Rum", 55), drink("Sunfruit", 76), drink("Vodka", 111),
        drink("Torog Draught", 84), drink("Mango Juice", 64), drink("Pomegranate Wine", 71),
        drink("Ale", 78), drink("Ent-draught", 103), drink("Miruvor", 61), drink("Morgul-draught", 107),
        drink("Orc Draught", 73), drink("Dwarven Tonic", 125)
    };

    private static final String[] DRINK_TOXICITIES = new String[] {
        "Weak", "Light", "Moderate", "Strong", "Potent"
    };

    private static final String[] DRINK_VESSELS = new String[] {
        "wine glass", "waterskin", "skull cup", "glass bottle", "wooden goblet",
        "mug", "ceramic mug", "ale horn", "copper goblet", "silver goblet", "gold goblet",
        "golden alehorn"
    };

    private static final QuotaOption[] DROPS = new QuotaOption[] {
        stacks("Cooked Chicken", 1), units("Galadhrim Sword", 25), units("Orc Draught", 26),
        stacks("Uruk Steel Ingot", 7), units("Milk", 21), units("Dwarven Pickaxe", 10),
        units("Umbaric Scimitar", 16), units("Gondorian Sword", 32), units("Rohirric Helmet", 20),
        units("Galadhrim Leggings", 29), units("Rivendell Hoe", 22), units("Rohirric Leggings", 14),
        stacks("Red Grapes", 24), units("Mordor Hoe", 13), units("Poisoned Uruk Dagger", 27),
        units("Medium Pouch", 30), units("Dalish Pastry", 17), stacks("Maggoty Bread", 31),
        stacks("Mutton", 19), units("Wood-elven Chestplate", 18)
    };

    private static final String[] FELL_BEASTS = new String[] {
        "Achieve Balrog Slayer, or die trying",
        "Achieve Wrath of the Forest or Long Live the Chieftain"
    };

    public static boolean canRoll(String id) {
        KOMEProgressionAchievement achievement = KOMEProgressionAchievement.forID(id);
        if (achievement == null || "baseline".equals(achievement.group)) {
            return false;
        }
        return achievement.requirement.toLowerCase().contains("random task")
            || id.startsWith("serf.food_quota")
            || "serf.drink_quota".equals(id)
            || id.startsWith("knight.drop_quota")
            || id.startsWith("knight.faction_")
            || "lord.fell_beast".equals(id);
    }

    public static String roll(String id, long seed) {
        Random random = new Random(seed ^ id.hashCode());
        if (id.startsWith("serf.food_quota")) {
            return pick(random, FOODS).describe();
        }
        if ("serf.drink_quota".equals(id)) {
            return pick(random, DRINKS).describe(pick(random, DRINK_TOXICITIES), pick(random, DRINK_VESSELS));
        }
        if (id.startsWith("knight.drop_quota")) {
            return pick(random, DROPS).describe();
        }
        if (id.startsWith("knight.faction_")) {
            return KOMEProgressionFactionQuotas.describe(pick(random, KOMEProgressionFactionQuotas.FACTIONS));
        }
        if ("lord.fell_beast".equals(id)) {
            return pick(random, FELL_BEASTS);
        }
        return pick(random, RANDOMS);
    }

    private static String pick(Random random, String[] values) {
        return values[random.nextInt(values.length)];
    }

    private static QuotaOption pick(Random random, QuotaOption[] values) {
        return values[random.nextInt(values.length)];
    }

    private static DrinkOption pick(Random random, DrinkOption[] values) {
        return values[random.nextInt(values.length)];
    }

    private static QuotaOption units(String item, int amount) {
        return new QuotaOption(item, amount, "units");
    }

    private static QuotaOption stacks(String item, int amount) {
        return new QuotaOption(item, amount, "stacks");
    }

    private static DrinkOption drink(String name, int amount) {
        return new DrinkOption(name, amount);
    }

    private static class QuotaOption {
        private final String item;
        private final int amount;
        private final String unit;

        private QuotaOption(String item, int amount, String unit) {
            this.item = item;
            this.amount = amount;
            this.unit = unit;
        }

        private String describe() {
            return "Collect " + amount + " " + unit + " of " + item;
        }
    }

    private static class DrinkOption {
        private final String name;
        private final int amount;

        private DrinkOption(String name, int amount) {
            this.name = name;
            this.amount = amount;
        }

        private String describe(String toxicity, String vessel) {
            return "Bring " + amount + " units of " + name + " in a " + vessel + " (toxicity: " + toxicity + ")";
        }
    }
}
