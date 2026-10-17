package kome.common.data;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Authoritative, machine-readable progression gate inventory and rank prerequisite policy. */
public final class KOMEProgressionPermissionRegistry {
    public enum Status { CANONICAL_ENFORCED, CANONICAL_HANDOFF, OBSOLETE, INFORMATIONAL }
    public static final class Gate {
        public final String progressionId, intendedUnlock, enforcementSite; public final Status status;
        private Gate(String id, String unlock, String site, Status status) { progressionId=id; intendedUnlock=unlock; enforcementSite=site; this.status=status; }
    }
    private static final Map<String, Gate> GATES;
    static {
        Map<String, Gate> gates = new LinkedHashMap<String, Gate>();
        gate(gates,"baseline.alcohol_pipeweed","Historical record","Historical record; native gameplay and protection rules",Status.OBSOLETE);
        gate(gates,"baseline.cooking","Historical record","Historical record; native gameplay and protection rules",Status.OBSOLETE);
        gate(gates,"baseline.farming","Historical record","Historical record; native gameplay and protection rules",Status.OBSOLETE);
        gate(gates,"baseline.fire","Historical record","Historical record; native gameplay and protection rules",Status.OBSOLETE);
        gate(gates,"baseline.meat","Historical record","Historical record; native gameplay and protection rules",Status.OBSOLETE);
        gate(gates,"baseline.miniquests","Legacy mini-quest milestone","No gameplay gate",Status.INFORMATIONAL);
        gate(gates,"baseline.mounts","Historical record","Historical record; native gameplay and protection rules",Status.OBSOLETE);
        gate(gates,"baseline.npc_trade","Native NPC trading","Retained legacy identifier; native LOTR eligibility",Status.OBSOLETE);
        gate(gates,"baseline.pledge","Historical record","Historical record; native gameplay and protection rules",Status.OBSOLETE);
        gate(gates,"baseline.pouches","Native pouch use","Retained legacy identifier; native LOTR rules",Status.OBSOLETE);
        gate(gates,"baseline.stonework","Historical record","Historical record; native gameplay and protection rules",Status.OBSOLETE);
        gate(gates,"baseline.fast_travel","Fast travel","KOMEWaypointAccessService",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.take_waypoints","Historical record","Manual claims retired; canonical battle resolution and ruler transfer services own territory",Status.OBSOLETE);
        gate(gates,"baseline.reclaim_waypoints","Historical record","Manual claims retired; canonical battle resolution and ruler transfer services own territory",Status.OBSOLETE);
        gate(gates,"baseline.hire_units","Historical record","Native ordinary hiring; Campaign recruitment uses current rank and Crown authority in KOMECampaignRecruitmentService",Status.OBSOLETE);
        gate(gates,"baseline.grow_population","Historical record","No addon action permission; owning native/domain system applies",Status.INFORMATIONAL);
        gate(gates,"baseline.fellowship","Historical record","No addon action permission; owning native/domain system applies",Status.INFORMATIONAL);
        gate(gates,"baseline.redstone","Historical record","No addon action permission; owning native/domain system applies",Status.INFORMATIONAL);
        gate(gates,"baseline.protective_banners","Historical record","No addon action permission; owning native/domain system applies",Status.INFORMATIONAL);
        gate(gates,"baseline.faction_gear","Faction gear","KOMEGearRestrictionService action gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.faction_armor","Faction armor","KOMEGearRestrictionService armor gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.non_faction_gear","Non-faction gear","KOMEGearRestrictionService action gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.non_faction_armor","Non-faction armor","KOMEGearRestrictionService armor gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.mithril_gear","Mithril gear","KOMEGearRestrictionService action/armor gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.utumno_gear","Utumno gear","KOMEGearRestrictionService action/armor gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.free_war","War-season context","Informational: war lifecycle is canonical service/season driven",Status.INFORMATIONAL);
        GATES=Collections.unmodifiableMap(gates);
    }
    private KOMEProgressionPermissionRegistry() { }

    /** Rank eligibility metadata, separate from the retained achievement inventory. */
    public enum Ability {
        BUILD_SUBMISSION("build.submit", KOMEProgressionRank.COMMONER, "Faction build submissions", "Submit legitimate faction work; existing review and time rules apply.", "paper", "Construction permission; Build manager/reviewer", "KOMEPacketBuildAction; KOMEBuildService"),
        START_RETURN("travel.start", KOMEProgressionRank.COMMONER, "Starting waypoint return", "Return to your saved character-creation waypoint under native travel rules. Click to request travel.", "compass", "Saved original start; native travel and territory rules", "KOMEWaypointAccessService; KOMEPacketWaypointTravelRequest"),
        MILITARY_EQUIPMENT("equipment.standard", KOMEProgressionRank.KNIGHT, "Faction military equipment", "Standard equipment follows native LOTR rules, including before Knight standing. Crafting and exceptional items retain their own requirements.", "iron_chestplate", "Native faction/alignment and item rules", "Native LOTR items; KOMEGearRestrictionService"),
        BATTLE_SERVICE("battle.join", KOMEProgressionRank.KNIGHT, "Company & battle service", "Join legal formal battles beside your faction's committed campaign company. This does not grant company ownership or recruitment authority.", "iron_sword", "Committed faction company; legal active conflict", "KOMEJoinBattleService"),
        COMPANY_COMMAND("company.command", KOMEProgressionRank.LORD, "Command companies", "Receive and command legally owned or delegated companies. Existing legal command and safe withdrawal remain available after rank correction.", "map", "Company ownership or existing lawful delegation", "KOMECompanyTransferService; KOMECompanyDiplomacyAuthorization; KOMECompanyCommandAuthority"),
        CAMPAIGN_RECRUITMENT("campaign.recruit", KOMEProgressionRank.LORD, "Recruit campaign troops", "Raise strategic troops with Crown authority and faction population. Company control does not authorize recruitment spending.", "gold_nugget", "Recognized ruler; KOM-87 delegation pending; population", "KOMECampaignRecruitmentService"),
        STRATEGIC_ORDERS("company.orders", KOMEProgressionRank.LORD, "Issue strategic orders", "Order a legally controlled company along valid strategic routes. New hostile operations outside a declared war require the recognized ruler; route and conflict checks still apply.", "compass", "Lawful company command; legal route and current state", "KOMECommandTroops.validateCompanyMove");

        public final String id, title, description, icon, dependencies, enforcementSite;
        public final KOMEProgressionRank minimumRank;
        Ability(String id, KOMEProgressionRank rank, String title, String description,
                String icon, String dependencies, String site) {
            this.id=id; minimumRank=rank; this.title=title; this.description=description;
            this.icon=icon; this.dependencies=dependencies; enforcementSite=site;
        }
        public static Ability forId(String id) {
            for (Ability ability : values()) if (ability.id.equals(id)) return ability;
            return null;
        }
    }
    private static void gate(Map<String,Gate> m,String id,String unlock,String site,Status status){m.put(id,new Gate(id,unlock,site,status));}
    public static Map<String, Gate> gates() { return GATES; }
    public static Gate gate(String id) { return id == null ? null : GATES.get(id.toLowerCase(java.util.Locale.ROOT)); }
    public static boolean canComplete(KOMEPlayerProgression progression, KOMEProgressionAchievement achievement) {
        if (progression == null || achievement == null) return false;
        KOMEHigherRankTransitionService.Transition transition =
            KOMEHigherRankTransitionService.forMarker(achievement.id);
        if (transition != null)
            return progression.getCanonicalRank() == transition.fromRank
                && KOMEHigherRankTransitionService.requirementsComplete(progression, transition);
        KOMEProgressionRank required = requiredCanonicalRank(achievement.group);
        return required == null || progression.getCanonicalRank().order >= required.order;
    }
    public static String prerequisiteText(KOMEProgressionAchievement achievement) {
        KOMEProgressionRank required = achievement == null ? null : requiredCanonicalRank(achievement.group);
        return required == null || required == KOMEProgressionRank.WANDERER
            ? "" : " Requires reaching the corresponding canonical faction rank.";
    }
    public static String requirementText(KOMEProgressionAchievement achievement) {
        return achievement == null ? "" : achievement.requirement + prerequisiteText(achievement);
    }
    private static KOMEProgressionRank requiredCanonicalRank(String group) {
        if (group == null || "baseline".equalsIgnoreCase(group) || "wanderer".equalsIgnoreCase(group))
            return KOMEProgressionRank.WANDERER;
        if ("serf".equalsIgnoreCase(group)) return KOMEProgressionRank.SERF;
        if ("knight".equalsIgnoreCase(group)) return KOMEProgressionRank.KNIGHT;
        if ("lord".equalsIgnoreCase(group)) return KOMEProgressionRank.LORD;
        if ("prince_king".equalsIgnoreCase(group)) return KOMEProgressionRank.PRINCE;
        return null;
    }
}
