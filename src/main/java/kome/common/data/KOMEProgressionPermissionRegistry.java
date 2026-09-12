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
    private static final String[] RANKS = { "baseline", "wanderer", "serf", "knight", "lord", "prince_king" };
    private static final Map<String, Gate> GATES;
    static {
        Map<String, Gate> gates = new LinkedHashMap<String, Gate>();
        gate(gates,"baseline.alcohol_pipeweed","Alcohol and pipeweed","KOMEEvents item-use gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.cooking","Cooking","KOMEEvents furnace gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.farming","Farming","KOMEEvents interaction/use gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.fire","Fire and light","KOMEEvents place/use gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.meat","Meat consumption","KOMEEvents item-use gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.miniquests","NPC mini-quests","KOMEEvents interaction gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.mounts","Mount use","KOMEEvents mount gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.npc_trade","NPC trading","KOMEEvents container gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.pledge","Pledging to a lord","KOMEProgressionLords",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.pouches","Pouch use","KOMEEvents interaction/container gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.stonework","Stone tools","KOMEGearRestrictionService action/crafting gate",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.fast_travel","Fast travel","KOMEWaypointAccessService",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.take_waypoints","Hostile waypoint/tile capture","KOMEPacketConquestClaim",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.reclaim_waypoints","Faction waypoint/tile reclaim","KOMEPacketConquestClaim",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.hire_units","Combat recruitment","KOMEEvents.handleHiredUnit",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.grow_population","Population administration","KOMECommandPopulation",Status.CANONICAL_ENFORCED);
        gate(gates,"baseline.fellowship","LOTR fellowship API","No addon action site",Status.CANONICAL_HANDOFF);
        gate(gates,"baseline.redstone","Redstone use","No addon action site",Status.CANONICAL_HANDOFF);
        gate(gates,"baseline.protective_banners","Protective banners","No addon action site",Status.CANONICAL_HANDOFF);
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
    private static void gate(Map<String,Gate> m,String id,String unlock,String site,Status status){m.put(id,new Gate(id,unlock,site,status));}
    public static Map<String, Gate> gates() { return GATES; }
    public static Gate gate(String id) { return id == null ? null : GATES.get(id.toLowerCase(java.util.Locale.ROOT)); }
    public static boolean canComplete(KOMEPlayerProgression progression, KOMEProgressionAchievement achievement) {
        if (progression == null || achievement == null) return false;
        int rank = rankIndex(achievement.group); if (rank <= 0) return true;
        for (KOMEProgressionAchievement prior : KOMEProgressionAchievement.forGroup(RANKS[rank - 1])) if (!progression.isCompleted(prior)) return false;
        return true;
    }
    public static String prerequisiteText(KOMEProgressionAchievement achievement) {
        int rank = achievement == null ? -1 : rankIndex(achievement.group);
        return rank <= 0 ? "" : " Requires completion of the " + displayRank(RANKS[rank - 1]) + " progression.";
    }
    public static String requirementText(KOMEProgressionAchievement achievement) {
        return achievement == null ? "" : achievement.requirement + prerequisiteText(achievement);
    }
    private static int rankIndex(String group) { for(int i=0;i<RANKS.length;i++) if(RANKS[i].equalsIgnoreCase(group)) return i; return -1; }
    private static String displayRank(String rank) { return "prince_king".equals(rank) ? "Prince/King" : Character.toUpperCase(rank.charAt(0))+rank.substring(1); }
}
