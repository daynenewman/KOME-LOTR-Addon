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
            // Permission flags remain inspectable history, not a player unlock checklist.
            if (!"baseline".equalsIgnoreCase(group)
                    && "Advancement".equalsIgnoreCase(achievement.category)) {
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
        a(list, "baseline.fire", "baseline", "Other", "Fire and Light", "Historical record. Ordinary fire and lighting need no KOME advancement; native and world protection rules still apply.");
        a(list, "baseline.stonework", "baseline", "Crafting & Gear", "Stonework", "Historical record. Ordinary stone recipes and tools need no KOME advancement.");
        a(list, "baseline.cooking", "baseline", "Food & Drink", "Cooking", "Historical record. Furnace and Hobbit oven cooking need no KOME advancement.");
        a(list, "baseline.alcohol_pipeweed", "baseline", "Food & Drink", "Drink and Smoke", "Historical record. Ordinary drinks and pipeweed follow native LOTR rules.");
        a(list, "baseline.hunting", "baseline", "War & Population", "Hunt and Kill", "Historical record. Normal hunting is ordinary native gameplay.");
        a(list, "baseline.house_waypoints", "baseline", "Travel", "Home and Custom Waypoints", "Historical record. Personal houses and native custom waypoints are not unlocked by this flag.");
        a(list, "baseline.farming", "baseline", "Food & Drink", "Farming", "Historical record. Tilling, fishing and animal breeding need no KOME advancement.");
        a(list, "baseline.npc_trade", "baseline", "Other", "NPC Trade", "Legacy record. Trading follows native LOTR alignment and hostility rules.");
        a(list, "baseline.miniquests", "baseline", "Other", "NPC Mini-Quests", "Legacy milestone; LOTR mini-quests are always available under LOTR rules.");
        a(list, "baseline.non_faction_gear", "baseline", "Crafting & Gear", "Non-Faction Gear", "Use of listed Mallorn items under existing item rules; not all non-faction crafting.");
        a(list, "baseline.non_faction_armor", "baseline", "Crafting & Gear", "Non-Faction Armor", "Listed or configured armor use under existing item rules; not all non-faction armor.");
        a(list, "baseline.brewing", "baseline", "Food & Drink", "Brewing", "Historical record. Native barrel brewing is not gated by this flag.");
        a(list, "baseline.mounts", "baseline", "Travel", "Mounts", "Historical record. Ordinary riding and taming follow native eligibility, not this flag.");
        a(list, "baseline.pledge", "baseline", "War & Population", "Pledge", "Historical record. Faction membership is the current native LOTR pledge.");
        a(list, "baseline.faction_armor", "baseline", "Crafting & Gear", "Faction Armor", "Use of listed or configured exceptional armor under existing item rules; not a universal faction armor gate.");
        a(list, "baseline.utumno_gear", "baseline", "Crafting & Gear", "Utumno Gear", "Use of listed Utumno equipment under existing exceptional item rules; no ordinary advancement unlock path is implemented.");
        a(list, "baseline.pouches", "baseline", "Other", "Pouches", "Legacy record. Pouches follow native LOTR rules and need no advancement unlock.");
        a(list, "baseline.meat", "baseline", "Food & Drink", "Meat", "Historical record. Normal hunting and food consumption need no KOME advancement.");
        a(list, "baseline.fellowship", "baseline", "War & Population", "Fellowship", "Historical record. Native fellowship eligibility is not controlled by this flag.");
        a(list, "baseline.faction_gear", "baseline", "Crafting & Gear", "Faction Gear", "Use of selected exceptional gear under existing item rules; not a universal faction crafting unlock.");
        a(list, "baseline.take_waypoints", "baseline", "War & Population", "Take Waypoints", "Historical record. Rank alone cannot claim tiles, certify battle victory or declare war.");
        a(list, "baseline.reclaim_waypoints", "baseline", "War & Population", "Reclaim Waypoints", "Historical record. Reclaim requires canonical conquest resolution; this flag is not a battle result.");
        a(list, "baseline.protective_banners", "baseline", "War & Population", "Protective Banners", "Historical record. Native protective-banner rules remain authoritative.");
        a(list, "baseline.scrolls_modifiers", "baseline", "Crafting & Gear", "Scrolls and Modifiers", "Historical record. No general KOME scroll or modifier use gate is implemented for this flag.");
        a(list, "baseline.redstone", "baseline", "Other", "Redstone", "Historical record. Ordinary redstone is not gated by this flag.");
        a(list, "baseline.hire_units", "baseline", "War & Population", "Hire Units", "Historical record. Ordinary hires follow native rules and population costs. Campaign recruitment requires current standing and Crown authority.");
        a(list, "baseline.grow_population", "baseline", "War & Population", "Grow Population", "Historical record. Approved Builds, developed rates and the faction bank govern population; this flag grants no resource.");
        a(list, "baseline.free_war", "baseline", "War & Population", "War Season Context", "Learn how the canonical War Season and conflict systems operate.");
        a(list, "baseline.mithril_gear", "baseline", "Crafting & Gear", "Mithril Gear", "Use of listed Mithril equipment under existing exceptional item rules; not a manufacturing permission.");
        a(list, "baseline.enchanted_books", "baseline", "Crafting & Gear", "Enchanted Books", "Historical record. No general KOME enchanted-book crafting or use gate is implemented for this flag.");

        a(list, "wanderer.expert_traveler", "wanderer", "Advancement", "Expert Traveler", "Earn the native LOTR 10 km travel achievement. It currently supplies KOME fast-travel permission.");
        a(list, "wanderer.red_flower", "wanderer", "Advancement", "Man's Red Flower", "Legacy self-reported random task. Retained as history; ordinary fire and light need no unlock.");
        a(list, "wanderer.masonry", "wanderer", "Advancement", "Masonry", "Legacy self-reported random task. Retained as history; ordinary stone tools need no unlock.");
        a(list, "wanderer.learn_to_cook", "wanderer", "Advancement", "Learn to Cook", "Legacy self-reported cooking record. Ordinary cooking is available; proximity and crafting are not verified by this record.");
        a(list, "wanderer.smoke_drink", "wanderer", "Advancement", "Well, If You Insist", "Legacy self-reported random task. Retained as history; ordinary consumption follows native rules.");
        a(list, "wanderer.dangerous_business", "wanderer", "Quota", "Dangerous Business", "Explore 20 regions of Middle-earth.");
        a(list, "wanderer.travel_30km", "wanderer", "Quota", "30 km Traveler", "Travel a total of 30 km in game across all travel modes.");
        a(list, "wanderer.find_serf_lord", "wanderer", "Allegiance", "Pledge Allegiance", "A successfully committed native faction pledge grants Serf standing.");
        a(list, "wanderer.place_weapon_holder", "wanderer", "Entering Service", "Place Weapon Holder", "Legacy historical record. It is not a current promotion requirement.");
        a(list, "wanderer.hoe_agreement", "wanderer", "Entering Service", "Hoe Agreement", "Legacy historical record. It is not a current promotion requirement.");

        a(list, "serf.bartering", "serf", "Advancement", "Bartering", "Legacy self-reported random task. Trading follows native LOTR eligibility and is not unlocked by this record.");
        a(list, "serf.quest_seeker", "serf", "Advancement", "Quest Seeker", "Complete 5 native LOTR mini-quests for NPCs.");
        a(list, "serf.smithery", "serf", "Advancement", "Smithery", "Legacy self-reported Smithery task. Controls listed Mallorn item use; crafting count and trader proximity are not server-verified.");
        a(list, "serf.brewing", "serf", "Advancement", "Mmm... Good Stuff", "Earn the native LOTR barrel-brewing achievement. This records an accomplishment, not permission to brew.");
        a(list, "serf.non_faction_armor", "serf", "Advancement", "This Feels Better", "Legacy self-reported random task. Existing exceptional item rules apply; this is not a universal armor unlock.");
        a(list, "serf.food_quota_1", "serf", "Rank Quota", "Food Quota 1", "Complete your first assigned food quota.");
        a(list, "serf.food_quota_2", "serf", "Rank Quota", "Food Quota 2", "Complete your second assigned food quota.");
        a(list, "serf.drink_quota", "serf", "Rank Quota", "Drink Quota", "Complete your assigned drink quota.");
        a(list, "serf.deliver_quota", "serf", "Rank Quota", "Deliver Quota", "Bring the food and drink quota to your Master.");
        a(list, "serf.alignment_100", "serf", "Rank Quota", "Master Faction Alignment", "Hold at least +150 alignment to your Master's faction.");
        a(list, "serf.find_knight_lord", "serf", "Standing History", "Find a Worthy Liege", "Legacy historical record. It is not a current promotion requirement.");
        a(list, "serf.place_weapon_holder", "serf", "Standing History", "Place Weapon Holder", "Legacy historical record. It is not a current promotion requirement.");
        a(list, "serf.pledge", "serf", "Standing History", "Faction Allegiance", "Maintain a legitimate pledge to the faction you serve.");
        a(list, "serf.tame_ride", "serf", "Standing History", "Tame a Ride", "Legacy self-reported mount record. Ordinary riding and taming follow native eligibility.");
        a(list, "serf.defeat_invasion", "serf", "Standing History", "Defeat Invasion", "Fight and defeat an enemy invasion of your faction's caliber.");
        a(list, "serf.sword_agreement", "serf", "Standing History", "Sword Agreement", "Legacy historical record. It is not a current promotion requirement.");
        a(list, "serf.record_knight_lord", "serf", "Standing History", "Record Liege", "Legacy historical record. It is not a current promotion requirement.");
        a(list, "serf.assign_knight_quota", "serf", "Standing History", "Assign Rank Quota", "Legacy historical record. It is not a current promotion requirement.");
        a(list, "serf.title_knight", "serf", "Standing History", "Earn Knight Standing", "Your current Master recognizes three reported duties and 150 current faction alignment.");

        a(list, "knight.fanny_pack", "knight", "Advancement", "Fanny Pack", "Earn the native LOTR pouch achievement. Pouch use does not require this accomplishment.");
        a(list, "knight.meat", "knight", "Advancement", "The Finer Things of Life", "Legacy self-reported random task. Retained as history; normal meat consumption needs no unlock.");
        a(list, "knight.craftsman", "knight", "Advancement", "Craftsman", "Legacy self-reported Craftsman task. Controls selected exceptional gear use; crafting count and smith proximity are not server-verified.");
        a(list, "knight.fellowship", "knight", "Advancement", "You Have My Sword", "Legacy self-reported random task. Native fellowship eligibility is not unlocked by this record.");
        a(list, "knight.alignment_2000", "knight", "Rank Quota", "Liege Faction Alignment", "Hold 2,000 alignment to the faction of your Liege.");
        a(list, "knight.hooligan", "knight", "Rank Quota", "Hooligan", "Kill a foe while drunk.");
        a(list, "knight.speared", "knight", "Rank Quota", "Speared", "Score a spear kill from at least 50m.");
        a(list, "knight.deliver_drops", "knight", "Rank Quota", "Deliver Drop Quota", "Bring the drop quota to your Liege.");
        a(list, "knight.drop_quota_1", "knight", "Rank Quota", "Drop Quota 1", "Complete your first assigned drop quota.");
        a(list, "knight.drop_quota_2", "knight", "Rank Quota", "Drop Quota 2", "Complete your second assigned drop quota.");
        a(list, "knight.faction_1", "knight", "Rank Quota", "Faction 1", "Roll a faction and earn +/-500 alignment, or +/-750 for elite factions.");
        a(list, "knight.faction_2", "knight", "Rank Quota", "Faction 2", "Roll a faction and earn +/-500 alignment, or +/-750 for elite factions.");
        a(list, "knight.choose_capital", "knight", "Rise in Standing", "Choose Capital", "Historical checklist record; does not designate a capital or grant political authority.");
        a(list, "knight.crown_king", "knight", "Rise in Standing", "Crown a King", "Historical checklist record; does not appoint a player ruler or create an NPC King.");
        a(list, "knight.record_king", "knight", "Rise in Standing", "Record King", "Historical checklist record; current ruler records are separate authority.");
        a(list, "knight.conquest_fellowship", "knight", "Rise in Standing", "Conquest Fellowship", "Historical checklist record; does not authorize campaign participation or ownership.");
        a(list, "knight.title_lord", "knight", "Rise in Standing", "Earn Knight Standing", "Your current Master recognizes three reported duties and 150 current faction alignment.");

        a(list, "lord.redstone", "lord", "Advancement", "What Is This Magic", "Legacy self-reported random task. Ordinary redstone needs no KOME unlock.");
        a(list, "lord.smithing_apprentice", "lord", "Advancement", "Smithing Apprentice", "Legacy self-reported random task. No general scroll or modifier permission reward is connected.");
        a(list, "lord.early_beginnings", "lord", "Advancement", "Early Beginnings", "Record at least 200 represented faction population, including active troop investment and available bank. No resource is granted.");
        a(list, "lord.protective_banners", "lord", "Advancement", "You Shall Not Pass", "Earn the native LOTR protective-banner achievement. Banner use follows native rules.");
        a(list, "lord.alignment_3000", "lord", "Rank Quota", "Ruler Faction Alignment", "Legacy Prince checklist: record reaching 3,000 alignment to your current pledged faction.");
        a(list, "lord.global_alignment", "lord", "Rank Quota", "Global Alignment", "Legacy Prince checklist: record absolute alignment of 250 to every playable faction, or 500 for elite factions.");
        a(list, "lord.capture_5_waypoints", "lord", "Rank Quota", "Capture 5 Waypoints", "Legacy self-reported Prince checklist milestone. This flag does not certify five server-validated conquest victories.");
        a(list, "lord.fell_beast", "lord", "Highest Standing", "Fell Beast", "Legacy Prince checklist: earn the native achievement for the assigned foe. This is not a bound Hunt objective.");
        a(list, "lord.fluttering_by", "lord", "Highest Standing", "Fluttering By", "Legacy Prince checklist: earn the native butterfly-catching achievement.");
        a(list, "lord.title_prince_king", "lord", "Highest Standing", "Reach Highest Rank", "Complete the current legacy Prince checklist to advance standing. This grants neither King office nor a fief appointment.");
        a(list, "lord.declare_kingship", "lord", "Highest Standing", "Declare Rulership", "Historical declaration record; does not appoint a recognized faction ruler.");

        a(list, "prince_king.new_growth", "prince_king", "Advancement", "New Growth", "Legacy self-reported random task. Builds and approved faction systems govern population; this record grants no resource.");
        a(list, "prince_king.this_is_war", "prince_king", "Advancement", "This Is War", "Legacy self-reported 3,000-enemy milestone; no authoritative counter or war-declaration power is connected.");
        a(list, "prince_king.true_silver", "prince_king", "Advancement", "True Silver", "Earn the Mithril Adventurer's Shield.");
        a(list, "prince_king.master_smith", "prince_king", "Advancement", "Master Smith", "Legacy self-reported random task. No general enchanted-book manufacturing permission is connected.");
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
