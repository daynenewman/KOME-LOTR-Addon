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

        a(list, "baseline.wood", "baseline", "Crafting & Gear", "Woodworking", "Craft and use wooden items, including boats.", true);
        a(list, "baseline.simple_weapons", "baseline", "War & Population", "Improvised Weapons", "Fight with improvised weapons.", true);
        a(list, "baseline.land_food", "baseline", "Food & Drink", "Food of the Land", "Eat wild foods from Middle-earth.", true);
        a(list, "baseline.non_gear_tables", "baseline", "Crafting & Gear", "Workbench Use", "Craft ordinary items at a workbench.", true);
        a(list, "baseline.fast_travel", "baseline", "Travel", "Fast Travel", "Use fast travel.");
        a(list, "baseline.fire", "baseline", "Other", "Fire and Light", "Historical only. Fire and lighting follow native and world rules.");
        a(list, "baseline.stonework", "baseline", "Crafting & Gear", "Stonework", "Historical only. Stoneworking needs no KOME unlock.");
        a(list, "baseline.cooking", "baseline", "Food & Drink", "Cooking", "Historical only. Cooking needs no KOME unlock.");
        a(list, "baseline.alcohol_pipeweed", "baseline", "Food & Drink", "Drink and Smoke", "Historical only. Drink and pipeweed follow LOTR rules.");
        a(list, "baseline.hunting", "baseline", "War & Population", "Hunt and Kill", "Historical only. Hunting follows native rules.");
        a(list, "baseline.house_waypoints", "baseline", "Travel", "Home and Custom Waypoints", "Historical only. Homes and custom waypoints are not unlocked here.");
        a(list, "baseline.farming", "baseline", "Food & Drink", "Farming", "Historical only. Farming, fishing and breeding need no KOME unlock.");
        a(list, "baseline.npc_trade", "baseline", "Other", "NPC Trade", "Legacy only. Trading follows native alignment and hostility rules.");
        a(list, "baseline.miniquests", "baseline", "Other", "NPC Mini-Quests", "Legacy only. Miniquests follow native LOTR rules.");
        a(list, "baseline.non_faction_gear", "baseline", "Crafting & Gear", "Non-Faction Gear", "Listed Mallorn items follow existing restrictions; not a general crafting unlock.");
        a(list, "baseline.non_faction_armor", "baseline", "Crafting & Gear", "Non-Faction Armor", "Selected armor follows item rules; not a general armor unlock.");
        a(list, "baseline.brewing", "baseline", "Food & Drink", "Brewing", "Historical only. Barrel brewing follows native rules.");
        a(list, "baseline.mounts", "baseline", "Travel", "Mounts", "Historical only. Riding and taming follow native rules.");
        a(list, "baseline.pledge", "baseline", "War & Population", "Pledge", "Historical only. Your native LOTR pledge determines faction membership.");
        a(list, "baseline.faction_armor", "baseline", "Crafting & Gear", "Faction Armor", "Exceptional armor follows item restrictions; ordinary faction armor does not.");
        a(list, "baseline.utumno_gear", "baseline", "Crafting & Gear", "Utumno Gear", "Utumno gear follows special item rules; no general unlock exists.");
        a(list, "baseline.pouches", "baseline", "Other", "Pouches", "Legacy only. Pouches follow native rules.");
        a(list, "baseline.meat", "baseline", "Food & Drink", "Meat", "Historical only. Hunting and eating need no KOME unlock.");
        a(list, "baseline.fellowship", "baseline", "War & Population", "Fellowship", "Historical only. Fellowships follow native rules.");
        a(list, "baseline.faction_gear", "baseline", "Crafting & Gear", "Faction Gear", "Selected exceptional gear follows item restrictions; not a general crafting unlock.");
        a(list, "baseline.take_waypoints", "baseline", "War & Population", "Take Waypoints", "Historical only. Rank cannot claim tiles or declare war.");
        a(list, "baseline.reclaim_waypoints", "baseline", "War & Population", "Reclaim Waypoints", "Historical only. Reclaiming territory requires an actual KOME victory.");
        a(list, "baseline.protective_banners", "baseline", "War & Population", "Protective Banners", "Historical only. Protective banners follow native rules.");
        a(list, "baseline.scrolls_modifiers", "baseline", "Crafting & Gear", "Scrolls and Modifiers", "Historical only. No general scroll or modifier unlock exists.");
        a(list, "baseline.redstone", "baseline", "Other", "Redstone", "Historical only. Redstone needs no KOME unlock.");
        a(list, "baseline.hire_units", "baseline", "War & Population", "Hire Units", "Historical only. Native hires follow native rules; campaign recruitment needs Crown authority.");
        a(list, "baseline.grow_population", "baseline", "War & Population", "Grow Population", "Historical only. Builds and the faction bank govern population; no resources are granted.");
        a(list, "baseline.free_war", "baseline", "War & Population", "War Season Context", "Learn how war seasons and faction conflicts work.");
        a(list, "baseline.mithril_gear", "baseline", "Crafting & Gear", "Mithril Gear", "Mithril gear follows special item restrictions; this grants no crafting rights.");
        a(list, "baseline.enchanted_books", "baseline", "Crafting & Gear", "Enchanted Books", "Historical only. No general enchanted-book unlock exists.");

        a(list, "wanderer.expert_traveler", "wanderer", "Advancement", "Expert Traveler", "Earn the native 10 km travel achievement to unlock KOME fast travel.");
        a(list, "wanderer.red_flower", "wanderer", "Advancement", "Man's Red Flower", "Legacy random task. Fire and light need no unlock.");
        a(list, "wanderer.masonry", "wanderer", "Advancement", "Masonry", "Legacy random task. Stone tools need no unlock.");
        a(list, "wanderer.learn_to_cook", "wanderer", "Advancement", "Learn to Cook", "Legacy only. Cooking is available; this record does not verify crafting.");
        a(list, "wanderer.smoke_drink", "wanderer", "Advancement", "Well, If You Insist", "Legacy random task. Food and drink follow native rules.");
        a(list, "wanderer.dangerous_business", "wanderer", "Quota", "Dangerous Business", "Discover 20 regions of Middle-earth.");
        a(list, "wanderer.travel_30km", "wanderer", "Quota", "30 km Traveler", "Travel 30 km by any means.");
        a(list, "wanderer.find_serf_lord", "wanderer", "Allegiance", "Pledge Allegiance", "A faction pledge establishes allegiance without changing your rank.");
        a(list, "wanderer.place_weapon_holder", "wanderer", "Entering Service", "Place Weapon Holder", "Legacy only. Not required for promotion.");
        a(list, "wanderer.hoe_agreement", "wanderer", "Entering Service", "Hoe Agreement", "Legacy only. Not required for promotion.");

        a(list, "serf.bartering", "serf", "Advancement", "Bartering", "Legacy random task. Trading follows native eligibility.");
        a(list, "serf.quest_seeker", "serf", "Advancement", "Quest Seeker", "Complete five native LOTR miniquests.");
        a(list, "serf.smithery", "serf", "Advancement", "Smithery", "Legacy only. Governs listed Mallorn items; crafting details are unverified.");
        a(list, "serf.brewing", "serf", "Advancement", "Mmm... Good Stuff", "Earn the native brewing achievement; brewing is already available.");
        a(list, "serf.non_faction_armor", "serf", "Advancement", "This Feels Better", "Legacy random task. Exceptional armor restrictions still apply.");
        a(list, "serf.food_quota_1", "serf", "Rank Quota", "Food Quota 1", "Complete your first food quota.");
        a(list, "serf.food_quota_2", "serf", "Rank Quota", "Food Quota 2", "Complete your second food quota.");
        a(list, "serf.drink_quota", "serf", "Rank Quota", "Drink Quota", "Complete your drink quota.");
        a(list, "serf.deliver_quota", "serf", "Rank Quota", "Deliver Quota", "Deliver the food and drink quota to your Master.");
        a(list, "serf.alignment_100", "serf", "Rank Quota", "Master Faction Alignment", "Reach 150 alignment with your Master's faction.");
        a(list, "serf.find_knight_lord", "serf", "Standing History", "Find a Worthy Liege", "Legacy only. Not required for promotion.");
        a(list, "serf.place_weapon_holder", "serf", "Standing History", "Place Weapon Holder", "Legacy only. Not required for promotion.");
        a(list, "serf.pledge", "serf", "Standing History", "Faction Allegiance", "Remain pledged to the faction you serve.");
        a(list, "serf.tame_ride", "serf", "Standing History", "Tame a Ride", "Legacy only. Riding and taming follow native rules.");
        a(list, "serf.defeat_invasion", "serf", "Standing History", "Defeat Invasion", "Defeat an invasion threatening your faction.");
        a(list, "serf.sword_agreement", "serf", "Standing History", "Sword Agreement", "Legacy only. Not required for promotion.");
        a(list, "serf.record_knight_lord", "serf", "Standing History", "Record Liege", "Legacy only. Not required for promotion.");
        a(list, "serf.assign_knight_quota", "serf", "Standing History", "Assign Rank Quota", "Legacy only. Not required for promotion.");
        a(list, "serf.title_knight", "serf", "Standing History", "Earn Knight Standing", "Report three duties and reach 150 alignment to earn Knight standing.");

        a(list, "knight.fanny_pack", "knight", "Advancement", "Fanny Pack", "Earn the native pouch achievement; pouch use needs no unlock.");
        a(list, "knight.meat", "knight", "Advancement", "The Finer Things of Life", "Legacy random task. Eating meat needs no unlock.");
        a(list, "knight.craftsman", "knight", "Advancement", "Craftsman", "Legacy only. Governs selected gear; crafting details are unverified.");
        a(list, "knight.fellowship", "knight", "Advancement", "You Have My Sword", "Legacy random task. Fellowships follow native rules.");
        a(list, "knight.alignment_2000", "knight", "Rank Quota", "Liege Faction Alignment", "Reach 2,000 alignment with your Liege's faction.");
        a(list, "knight.hooligan", "knight", "Rank Quota", "Hooligan", "Defeat an enemy while drunk.");
        a(list, "knight.speared", "knight", "Rank Quota", "Speared", "Defeat an enemy with a spear from at least 50 m.");
        a(list, "knight.deliver_drops", "knight", "Rank Quota", "Deliver Drop Quota", "Deliver the required loot to your Liege.");
        a(list, "knight.drop_quota_1", "knight", "Rank Quota", "Drop Quota 1", "Complete your first loot quota.");
        a(list, "knight.drop_quota_2", "knight", "Rank Quota", "Drop Quota 2", "Complete your second loot quota.");
        a(list, "knight.faction_1", "knight", "Rank Quota", "Faction 1", "Legacy objective: earn ±500 faction alignment (±750 for elite factions).");
        a(list, "knight.faction_2", "knight", "Rank Quota", "Faction 2", "Legacy objective: earn ±500 faction alignment (±750 for elite factions).");
        a(list, "knight.choose_capital", "knight", "Rise in Standing", "Choose Capital", "Historical only. This does not choose a capital or grant authority.");
        a(list, "knight.crown_king", "knight", "Rise in Standing", "Crown a King", "Historical only. This does not appoint a ruler.");
        a(list, "knight.record_king", "knight", "Rise in Standing", "Record King", "Historical only. Ruler authority is recorded separately.");
        a(list, "knight.conquest_fellowship", "knight", "Rise in Standing", "Conquest Fellowship", "Historical only. No battle or ownership rights are granted.");
        a(list, "knight.title_lord", "knight", "Rise in Standing", "Earn Lord Standing", "Report three commissions and reach 2,000 alignment to earn Lord standing.");

        a(list, "lord.redstone", "lord", "Advancement", "What Is This Magic", "Legacy random task. Redstone needs no KOME unlock.");
        a(list, "lord.smithing_apprentice", "lord", "Advancement", "Smithing Apprentice", "Legacy random task. No scroll or modifier unlock is granted.");
        a(list, "lord.early_beginnings", "lord", "Advancement", "Early Beginnings", "Record 200 faction population across troops and bank. No resources are granted.");
        a(list, "lord.protective_banners", "lord", "Advancement", "You Shall Not Pass", "Earn the native banner achievement; banner use follows LOTR rules.");
        a(list, "lord.alignment_3000", "lord", "Rank Quota", "Ruler Faction Alignment", "Legacy Prince requirement: reach 3,000 alignment with your faction.");
        a(list, "lord.global_alignment", "lord", "Rank Quota", "Global Alignment", "Legacy Prince requirement: reach ±250 alignment with each faction (±500 for elite factions).");
        a(list, "lord.capture_5_waypoints", "lord", "Rank Quota", "Capture 5 Waypoints", "Legacy Prince record. This does not prove five actual KOME conquests.");
        a(list, "lord.fell_beast", "lord", "Highest Standing", "Fell Beast", "Legacy Prince requirement: defeat the assigned native foe; not an active Hunt quest.");
        a(list, "lord.fluttering_by", "lord", "Highest Standing", "Fluttering By", "Legacy Prince requirement: earn the butterfly achievement.");
        a(list, "lord.title_prince_king", "lord", "Highest Standing", "Reach Highest Rank", "Complete the legacy Prince requirements. This grants neither Kingship nor a fief.");
        a(list, "lord.declare_kingship", "lord", "Highest Standing", "Declare Rulership", "Historical only. This does not appoint a faction ruler.");

        a(list, "prince_king.new_growth", "prince_king", "Advancement", "New Growth", "Legacy random task. Builds and faction systems govern population; no resources are granted.");
        a(list, "prince_king.this_is_war", "prince_king", "Advancement", "This Is War", "Legacy only. The 3,000-enemy count is unverified; this grants no war authority.");
        a(list, "prince_king.true_silver", "prince_king", "Advancement", "True Silver", "Earn the Mithril Adventurer's Shield.");
        a(list, "prince_king.master_smith", "prince_king", "Advancement", "Master Smith", "Legacy random task. No enchanted-book crafting rights are granted.");
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
