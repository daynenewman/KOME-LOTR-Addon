package kome.common.data;

import kome.common.KOMEReflection;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.UUID;

public class KOMEProgressionPermissions {
    public static final String ALCOHOL_PIPEWEED = "baseline.alcohol_pipeweed";
    public static final String COOKING = "baseline.cooking";
    public static final String FARMING = "baseline.farming";
    public static final String FIRE = "baseline.fire";
    public static final String MEAT = "baseline.meat";
    public static final String MINIQUESTS = "baseline.miniquests";
    public static final String MOUNTS = "baseline.mounts";
    public static final String NPC_TRADE = "baseline.npc_trade";
    public static final String PLEDGE = "baseline.pledge";
    public static final String POUCHES = "baseline.pouches";
    public static final String STONEWORK = "baseline.stonework";
    public static final String GROW_POPULATION = "baseline.grow_population";
    public static final String HIRE_UNITS = "baseline.hire_units";
    public static final String TAKE_WAYPOINTS = "baseline.take_waypoints";
    public static final String RECLAIM_WAYPOINTS = "baseline.reclaim_waypoints";
    public static final String FAST_TRAVEL = "baseline.fast_travel";
    public static final String FACTION_GEAR = "baseline.faction_gear";
    public static final String FACTION_ARMOR = "baseline.faction_armor";
    public static final String NON_FACTION_GEAR = "baseline.non_faction_gear";
    public static final String NON_FACTION_ARMOR = "baseline.non_faction_armor";
    public static final String MITHRIL_GEAR = "baseline.mithril_gear";
    public static final String UTUMNO_GEAR = "baseline.utumno_gear";

    public static boolean has(EntityPlayer player, String permissionID) {
        if (player == null || permissionID == null) {
            return false;
        }
        // These identifiers survive as history, not restrictions on native gameplay.
        if (isRetiredNativeGate(permissionID)) return true;
        KOMEProgressionAchievement permission = KOMEProgressionAchievement.forID(permissionID);
        if (permission == null) {
            return false;
        }
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        if (!data.isProgressionEnabled()) {
            return true;
        }
        UUID playerID = KOMEReflection.getEntityUUID(player);
        KOMEPlayerProgression progression = data.progressions.get(playerID);
        // Legacy title flags are compatibility history, never present Lord authority.
        if(TAKE_WAYPOINTS.equals(permissionID)||RECLAIM_WAYPOINTS.equals(permissionID))
            return progression!=null&&progression.getCanonicalRank().order>=KOMEProgressionRank.LORD.order
                &&progression.isCompleted(permission);
        // Permission reads must not create a record, including on a denied forged request.
        return permission.defaultUnlocked || progression != null && progression.isCompleted(permission);
    }

    public static boolean require(EntityPlayer player, String permissionID) {
        if (has(player, permissionID)) {
            return true;
        }
        if (player != null) {
            deny(player, "You have not unlocked " + getTitle(permissionID) + " yet.");
        }
        return false;
    }

    public static boolean isRetiredNativeGate(String id) {
        return NPC_TRADE.equals(id) || POUCHES.equals(id) || HIRE_UNITS.equals(id)
            || FIRE.equals(id) || COOKING.equals(id) || STONEWORK.equals(id)
            || FARMING.equals(id) || MEAT.equals(id) || ALCOHOL_PIPEWEED.equals(id)
            || MOUNTS.equals(id);
    }

    /** New recruitment only. Existing companies, retreat and Emergency Defense use their own authority. */
    public static String campaignRecruitmentDenial(KOMEWorldData data, UUID actor,
            String faction, boolean administrative) {
        if (data == null || actor == null) return "Campaign recruitment authority is unavailable.";
        if (administrative) return "";
        KOMEPlayerProgression progression = data.progressions.get(actor);
        if (progression == null || progression.getCanonicalRank().order < KOMEProgressionRank.LORD.order)
            return "Campaign recruitment requires Lord standing. Ordinary native followers remain available under LOTR rules.";
        String key = KOMEAlliance.normalizeFactionKey(faction);
        if (key.isEmpty() || !key.equals(KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(actor))))
            return "Campaign recruitment requires a current pledge to the population-owning faction.";
        if (KOMERulerAuthorization.canActAsRuler(data, key, actor)) return "";
        return "Lord standing does not authorize spending the faction bank. Crown recruitment warrants and delegated population authority (KOM-87) are not implemented yet.";
    }

    public static void deny(EntityPlayer player, String message) {
        if (player != null && message != null) {
            player.addChatMessage(new ChatComponentText(EnumChatFormatting.RED + message));
        }
    }

    private static String getTitle(String permissionID) {
        KOMEProgressionAchievement permission = KOMEProgressionAchievement.forID(permissionID);
        return permission == null ? permissionID : permission.title;
    }
}
