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

    public static boolean has(EntityPlayer player, String permissionID) {
        if (player == null || permissionID == null) {
            return false;
        }
        KOMEProgressionAchievement permission = KOMEProgressionAchievement.forID(permissionID);
        if (permission == null) {
            return false;
        }
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        UUID playerID = KOMEReflection.getEntityUUID(player);
        return data.getProgression(playerID).isCompleted(permission);
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
