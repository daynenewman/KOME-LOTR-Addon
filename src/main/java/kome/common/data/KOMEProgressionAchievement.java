package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class KOMEProgressionAchievement {
    public static final List<KOMEProgressionAchievement> ALL;
    private static final Map<String, KOMEProgressionAchievement> BY_ID;
    private static final Set<String> AUTO_MANAGED_IDS = new HashSet<>();

    public final String id;
    public final String group;
    public final String category;
    public final String title;
    public final String requirement;
    public final boolean defaultUnlocked;
    /** Null for legacy definitions until they are intentionally migrated into the canonical tree. */
    public final String branch;
    public final KOMEProgressionRank rank;
    public final int tier;
    public final KOMEProgressionCompletionMode completionMode;
    public final String rewardId;

    private KOMEProgressionAchievement(String id, String group, String category, String title, String requirement, boolean defaultUnlocked) {
        this(id, group, category, title, requirement, defaultUnlocked, null, null, 0, null, null);
    }

    private KOMEProgressionAchievement(String id, String group, String category, String title, String requirement, boolean defaultUnlocked,
            String branch, KOMEProgressionRank rank, int tier, KOMEProgressionCompletionMode completionMode, String rewardId) {
        this.id = id;
        this.group = group;
        this.category = category;
        this.title = title;
        this.requirement = requirement;
        this.defaultUnlocked = defaultUnlocked;
        this.branch = branch;
        this.rank = rank;
        this.tier = tier;
        this.completionMode = completionMode;
        this.rewardId = rewardId;
    }

    /** Creates an opt-in canonical definition without changing the legacy achievement registry. */
    public static KOMEProgressionAchievement canonical(String id, String title, String requirement, String branchId,
            KOMEProgressionRank rank, int tier, KOMEProgressionCompletionMode completionMode, String rewardId) {
        KOMEProgressionBranch registeredBranch = KOMEProgressionBranch.forId(branchId);
        validateCanonicalMetadata(id, registeredBranch, rank, tier, completionMode);
        return new KOMEProgressionAchievement(id, "", "", title, requirement, false, registeredBranch.id, rank, tier,
                completionMode, rewardId);
    }

    public static KOMEProgressionAchievement forID(String id) {
        return id == null ? null : BY_ID.get(id.toLowerCase());
    }

    public static List<KOMEProgressionAchievement> forGroup(String group) {
        List<KOMEProgressionAchievement> matches = new ArrayList<>();
        for (KOMEProgressionAchievement achievement : ALL) {
            if (achievement.group.equalsIgnoreCase(group)) {
                matches.add(achievement);
            }
        }
        return matches;
    }

    /**
     * Player-facing Advancement-tab projection. Rank quotas, relationship steps,
     * and standing transitions remain in the registry for compatibility and the
     * Ranks system, but are not duplicated in the Advancements tab.
     */
    public static List<KOMEProgressionAchievement> forAdvancementsGroup(String group) {
        List<KOMEProgressionAchievement> matches = new ArrayList<>();
        for (KOMEProgressionAchievement achievement : ALL) {
            if (!achievement.group.equalsIgnoreCase(group)) continue;
            if ("baseline".equalsIgnoreCase(group)
                    || "Advancement".equalsIgnoreCase(achievement.category)) {
                matches.add(achievement);
            }
        }
        return matches;
    }

    public static boolean isGroup(String group) {
        return !forGroup(group).isEmpty();
    }

    public static boolean isAutoManaged(String id) {
        return id != null && AUTO_MANAGED_IDS.contains(id.toLowerCase());
    }

    private static KOMEProgressionAchievement a(List<KOMEProgressionAchievement> list, String id, String group, String category, String title, String requirement) {
        return a(list, id, group, category, title, requirement, false);
    }

    private static KOMEProgressionAchievement a(List<KOMEProgressionAchievement> list, String id, String group, String category, String title, String requirement, boolean defaultUnlocked) {
        KOMEProgressionAchievement achievement = new KOMEProgressionAchievement(id, group, category, title, requirement, defaultUnlocked);
        list.add(achievement);
        return achievement;
    }

    private static void auto(String... ids) {
        for (String id : ids) {
            AUTO_MANAGED_IDS.add(id);
        }
    }

    static {
        List<KOMEProgressionAchievement> list = new ArrayList<>();

        a(list, "baseline.wood", "baseline", "Crafting & Gear", "Woodworking", "Make and use anything of wood, including boats.", true);
        a(list, "baseline.simple_weapons", "baseline", "War & Population", "Improvised Weapons", "Use conkers, snowballs, stones, and plates as weapons.", true);
        a(list, "baseline.land_food", "baseline", "Food & Drink", "Food of the Land", "Eat food of the land, such as berries and wild tree foods.", true);
        a(list, "baseline.non_gear_tables", "baseline", "Crafting & Gear", "Workbench Use", "Use crafting tables for non-gear items.", true);
        a(list, "baseline.fast_travel", "baseline", "Travel", "Fast Travel", "Fast travel.");
        a(list, "baseline.fire", "baseline", "Other", "Fire and Light", "Use torches and light fires.");
        a(list, "baseline.stonework", "baseline", "Crafting & Gear", "Stonework", "Work with stone.");
        a(list, "baseline.cooking", "baseline", "Food & Drink", "Cooking", "Cook food.");
        a(list, "baseline.alcohol_pipeweed", "baseline", "Food & Drink", "Drink and Smoke", "Smoke and drink alcoholic beverages.");
        a(list, "baseline.hunting", "baseline", "War & Population", "Hunt and Kill", "Hunt and kill intentionally.");
        a(list, "baseline.house_waypoints", "baseline", "Travel", "Home and Custom Waypoints", "Own a house and make custom waypoints.");
        a(list, "baseline.farming", "baseline", "Food & Drink", "Farming", "Grow crops, have livestock, and fish.");
        a(list, "baseline.npc_trade", "baseline", "Other", "NPC Trade", "Trade with NPCs.");
        a(list, "baseline.miniquests", "baseline", "Other", "NPC Mini-Quests", "Legacy milestone; LOTR mini-quests are always available under LOTR rules.");
        a(list, "baseline.non_faction_gear", "baseline", "Crafting & Gear", "Non-Faction Gear", "Make non-faction gear.");
        a(list, "baseline.non_faction_armor", "baseline", "Crafting & Gear", "Non-Faction Armor", "Wear non-faction armor.");
        a(list, "baseline.brewing", "baseline", "Food & Drink", "Brewing", "Brew beverages.");
        a(list, "baseline.mounts", "baseline", "Travel", "Mounts", "Own mounts.");
        a(list, "baseline.pledge", "baseline", "War & Population", "Pledge", "Pledge to a faction.");
        a(list, "baseline.faction_armor", "baseline", "Crafting & Gear", "Faction Armor", "Wear faction armor.");
        a(list, "baseline.utumno_gear", "baseline", "Crafting & Gear", "Utumno Gear", "Wear and use Utumno gear.");
        a(list, "baseline.pouches", "baseline", "Other", "Pouches", "Use pouches.");
        a(list, "baseline.meat", "baseline", "Food & Drink", "Meat", "Eat meat.");
        a(list, "baseline.fellowship", "baseline", "War & Population", "Fellowship", "Form a fellowship.");
        a(list, "baseline.faction_gear", "baseline", "Crafting & Gear", "Faction Gear", "Craft faction gear.");
        a(list, "baseline.take_waypoints", "baseline", "War & Population", "Take Waypoints", "Take waypoints.");
        a(list, "baseline.reclaim_waypoints", "baseline", "War & Population", "Reclaim Waypoints", "Reclaim lost waypoints of your faction.");
        a(list, "baseline.protective_banners", "baseline", "War & Population", "Protective Banners", "Use protective banners.");
        a(list, "baseline.scrolls_modifiers", "baseline", "Crafting & Gear", "Scrolls and Modifiers", "Use scrolls and Utumno modifiers.");
        a(list, "baseline.redstone", "baseline", "Other", "Redstone", "Use redstone.");
        a(list, "baseline.hire_units", "baseline", "War & Population", "Hire Units", "Hire units.");
        a(list, "baseline.grow_population", "baseline", "War & Population", "Grow Population", "Grow your population.");
        a(list, "baseline.free_war", "baseline", "War & Population", "War Season Context", "Learn how the canonical War Season and conflict systems operate.");
        a(list, "baseline.mithril_gear", "baseline", "Crafting & Gear", "Mithril Gear", "Craft mithril gear.");
        a(list, "baseline.enchanted_books", "baseline", "Crafting & Gear", "Enchanted Books", "Craft and use enchanted books.");

        a(list, "wanderer.expert_traveler", "wanderer", "Advancement", "Expert Traveler", "Walk or fall a total of 10 km in game.");
        a(list, "wanderer.red_flower", "wanderer", "Advancement", "Man's Red Flower", "Complete the assigned random task.");
        a(list, "wanderer.masonry", "wanderer", "Advancement", "Masonry", "Complete the assigned random task.");
        a(list, "wanderer.learn_to_cook", "wanderer", "Advancement", "Learn to Cook", "Cook or make food in the presence of an NPC that sells food.");
        a(list, "wanderer.smoke_drink", "wanderer", "Advancement", "Well, If You Insist", "Complete the assigned random task.");
        a(list, "wanderer.dangerous_business", "wanderer", "Quota", "Dangerous Business", "Explore 20 regions of Middle-earth.");
        a(list, "wanderer.travel_30km", "wanderer", "Quota", "30 km Traveler", "Travel a total of 30 km in game across all travel modes.");
        a(list, "wanderer.find_serf_lord", "wanderer", "Entering Service", "Enter Service", "Take service with a Master and enter your first faction rank.");
        a(list, "wanderer.place_weapon_holder", "wanderer", "Entering Service", "Place Weapon Holder", "Place a weapon holder near the area your prospective Master roams.");
        a(list, "wanderer.hoe_agreement", "wanderer", "Entering Service", "Hoe Agreement", "Place a hoe on your Master's weapon holder.");

        a(list, "serf.bartering", "serf", "Advancement", "Bartering", "Complete the assigned random task.");
        a(list, "serf.quest_seeker", "serf", "Advancement", "Quest Seeker", "Complete 5 native LOTR mini-quests for NPCs.");
        a(list, "serf.smithery", "serf", "Advancement", "Smithery", "Craft 5 unique metal gear pieces near an allied NPC that sells weapons, armor, or tools.");
        a(list, "serf.brewing", "serf", "Advancement", "Mmm... Good Stuff", "Complete the assigned random task.");
        a(list, "serf.non_faction_armor", "serf", "Advancement", "This Feels Better", "Complete the assigned random task.");
        a(list, "serf.food_quota_1", "serf", "Rank Quota", "Food Quota 1", "Complete your first assigned food quota.");
        a(list, "serf.food_quota_2", "serf", "Rank Quota", "Food Quota 2", "Complete your second assigned food quota.");
        a(list, "serf.drink_quota", "serf", "Rank Quota", "Drink Quota", "Complete your assigned drink quota.");
        a(list, "serf.deliver_quota", "serf", "Rank Quota", "Deliver Quota", "Bring the food and drink quota to your Master.");
        a(list, "serf.alignment_100", "serf", "Rank Quota", "Master Faction Alignment", "Hold at least +150 alignment to your Master's faction.");
        a(list, "serf.find_knight_lord", "serf", "Trial of Standing", "Find a Worthy Liege", "Find an eligible prospective Liege.");
        a(list, "serf.place_weapon_holder", "serf", "Trial of Standing", "Place Weapon Holder", "Place a weapon holder near the area your Liege roams.");
        a(list, "serf.pledge", "serf", "Trial of Standing", "Pledge to Liege's Faction", "Pledge after reaching +100 alignment, or +150 for elite factions.");
        a(list, "serf.tame_ride", "serf", "Trial of Standing", "Tame a Ride", "Find, tame, and name a ride.");
        a(list, "serf.defeat_invasion", "serf", "Trial of Standing", "Defeat Invasion", "Fight and defeat an enemy invasion of your faction's caliber.");
        a(list, "serf.sword_agreement", "serf", "Trial of Standing", "Sword Agreement", "Place a sword on the weapon holder.");
        a(list, "serf.record_knight_lord", "serf", "Trial of Standing", "Record Liege", "Add the name and screenshot/picture of your Liege.");
        a(list, "serf.assign_knight_quota", "serf", "Trial of Standing", "Assign Rank Quota", "Assign two enemy drops and two factions for alignment quotas.");
        a(list, "serf.title_knight", "serf", "Trial of Standing", "Earn New Standing", "Advance to your faction's next progression title.");

        a(list, "knight.fanny_pack", "knight", "Advancement", "Fanny Pack", "Complete the assigned random task.");
        a(list, "knight.meat", "knight", "Advancement", "The Finer Things of Life", "Complete the assigned random task.");
        a(list, "knight.craftsman", "knight", "Advancement", "Craftsman", "Craft 8, or the max available, unique faction gear pieces near one of your faction's smiths/traders.");
        a(list, "knight.fellowship", "knight", "Advancement", "You Have My Sword", "Complete the assigned random task.");
        a(list, "knight.alignment_2000", "knight", "Rank Quota", "Liege Faction Alignment", "Hold 2,000 alignment to the faction of your Liege.");
        a(list, "knight.hooligan", "knight", "Rank Quota", "Hooligan", "Kill a foe while drunk.");
        a(list, "knight.speared", "knight", "Rank Quota", "Speared", "Score a spear kill from at least 50m.");
        a(list, "knight.deliver_drops", "knight", "Rank Quota", "Deliver Drop Quota", "Bring the drop quota to your Liege.");
        a(list, "knight.drop_quota_1", "knight", "Rank Quota", "Drop Quota 1", "Complete your first assigned drop quota.");
        a(list, "knight.drop_quota_2", "knight", "Rank Quota", "Drop Quota 2", "Complete your second assigned drop quota.");
        a(list, "knight.faction_1", "knight", "Rank Quota", "Faction 1", "Roll a faction and earn +/-500 alignment, or +/-750 for elite factions.");
        a(list, "knight.faction_2", "knight", "Rank Quota", "Faction 2", "Roll a faction and earn +/-500 alignment, or +/-750 for elite factions.");
        a(list, "knight.choose_capital", "knight", "Rise in Standing", "Choose Capital", "If your faction has no capital, choose an owned waypoint as yours and announce it.");
        a(list, "knight.crown_king", "knight", "Rise in Standing", "Crown a King", "If your faction has no king, crown a worthy NPC and move them to the capital.");
        a(list, "knight.record_king", "knight", "Rise in Standing", "Record King", "Add the name and screenshot/picture of your new king.");
        a(list, "knight.conquest_fellowship", "knight", "Rise in Standing", "Conquest Fellowship", "Ask an admin to add you to the Conquest Fellowship and accept in game.");
        a(list, "knight.title_lord", "knight", "Rise in Standing", "Earn New Standing", "Advance to your faction's next progression title.");

        a(list, "lord.redstone", "lord", "Advancement", "What Is This Magic", "Complete the assigned random task.");
        a(list, "lord.smithing_apprentice", "lord", "Advancement", "Smithing Apprentice", "Complete the assigned random task.");
        a(list, "lord.early_beginnings", "lord", "Advancement", "Early Beginnings", "Connect 5 of your faction's NPC builds to roads.");
        a(list, "lord.protective_banners", "lord", "Advancement", "You Shall Not Pass", "Complete the assigned random task.");
        a(list, "lord.alignment_3000", "lord", "Rank Quota", "Ruler Faction Alignment", "Hold 3,000 alignment to the faction of your ruler.");
        a(list, "lord.global_alignment", "lord", "Rank Quota", "Global Alignment", "Hold at least +/-250 alignment, or +/-500 for elite factions, to every non-neutral faction.");
        a(list, "lord.capture_5_waypoints", "lord", "Rank Quota", "Capture 5 Waypoints", "Capture 5 enemy waypoints chosen by your player king, or chosen by you for an NPC king.");
        a(list, "lord.fell_beast", "lord", "Highest Standing", "Fell Beast", "Assign and fight a fell beast.");
        a(list, "lord.fluttering_by", "lord", "Highest Standing", "Fluttering By", "Catch a butterfly in a jar.");
        a(list, "lord.title_prince_king", "lord", "Highest Standing", "Reach Highest Rank", "Advance to your faction's highest progression title.");
        a(list, "lord.declare_kingship", "lord", "Highest Standing", "Declare Rulership", "If first in your faction to complete Highest Standing, declare rulership in Discord.");

        a(list, "prince_king.new_growth", "prince_king", "Advancement", "New Growth", "Complete the assigned random task.");
        a(list, "prince_king.this_is_war", "prince_king", "Advancement", "This Is War", "Slay 3,000 enemies of your faction.");
        a(list, "prince_king.true_silver", "prince_king", "Advancement", "True Silver", "Earn the Mithril Adventurer's Shield.");
        a(list, "prince_king.master_smith", "prince_king", "Advancement", "Master Smith", "Complete the assigned random task.");
        Map<String, KOMEProgressionAchievement> byID = new HashMap<>();
        for (KOMEProgressionAchievement achievement : list) {
            if (byID.put(achievement.id, achievement) != null) throw new IllegalStateException("Duplicate progression achievement id: " + achievement.id);
        }
        auto(
            "wanderer.expert_traveler", "wanderer.dangerous_business", "wanderer.travel_30km", "wanderer.find_serf_lord",
            "serf.quest_seeker", "serf.brewing", "serf.food_quota_1", "serf.food_quota_2", "serf.drink_quota",
            "serf.deliver_quota", "serf.alignment_100", "serf.pledge", "serf.defeat_invasion", "serf.title_knight",
            "knight.fanny_pack", "knight.alignment_2000", "knight.hooligan", "knight.speared", "knight.deliver_drops",
            "knight.drop_quota_1", "knight.drop_quota_2", "knight.faction_1", "knight.faction_2",
            "lord.early_beginnings", "lord.alignment_3000", "lord.global_alignment", "lord.protective_banners",
            "lord.fell_beast", "lord.fluttering_by",
            "prince_king.true_silver"
        );
        ALL = Collections.unmodifiableList(list);
        BY_ID = Collections.unmodifiableMap(byID);
        validateSchema();
    }

    private static void validateSchema() {
        KOMEProgressionRank.validateSchema();
        KOMEProgressionBranch.validateSchema();
        validateCanonicalDefinitions(ALL);
    }

    /** Validates a future canonical definition set before it is installed by a later progression slice. */
    public static void validateCanonicalDefinitions(Iterable<KOMEProgressionAchievement> definitions) {
        if (definitions == null) throw new IllegalArgumentException("Missing canonical progression definitions");
        KOMEProgressionBranch.validateSchema();
        Set<String> canonicalIds = new HashSet<String>();
        for (KOMEProgressionAchievement achievement : definitions) {
            if (achievement == null) throw new IllegalArgumentException("Null canonical progression definition");
            if (achievement.branch != null || achievement.rank != null || achievement.completionMode != null) {
                KOMEProgressionBranch registeredBranch = KOMEProgressionBranch.forId(achievement.branch);
                validateCanonicalMetadata(achievement.id, registeredBranch, achievement.rank, achievement.tier,
                        achievement.completionMode);
                if (!canonicalIds.add(achievement.id)) throw new IllegalStateException("Duplicate canonical progression id: " + achievement.id);
            }
        }
    }

    private static void validateCanonicalMetadata(String id, KOMEProgressionBranch branch, KOMEProgressionRank rank, int tier,
            KOMEProgressionCompletionMode completionMode) {
        if (id == null || id.trim().length() == 0 || branch == null || rank == null || tier <= 0 || completionMode == null) {
            throw new IllegalArgumentException("Invalid canonical progression achievement metadata");
        }
    }
}
