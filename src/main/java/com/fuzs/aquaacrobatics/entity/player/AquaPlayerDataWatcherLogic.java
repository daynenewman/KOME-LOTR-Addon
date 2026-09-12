package com.fuzs.aquaacrobatics.entity.player;

import net.minecraft.entity.player.EntityPlayer;

import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.Pose;

/** Shared, Mixin-independent semantics for Aqua's existing player DataWatchers. */
public final class AquaPlayerDataWatcherLogic {

    private AquaPlayerDataWatcherLogic() {}

    public static void register(EntityPlayer player) {
        player.getDataWatcher().addObject(ConfigHandler.MiscellaneousConfig.poseId, Pose.STANDING.ordinal());
        if (ConfigHandler.MovementConfig.enableToggleCrawling) {
            player.getDataWatcher().addObject(ConfigHandler.MiscellaneousConfig.CrawlingId, 0);
        }
    }

    public static Pose getPose(EntityPlayer player) {
        try {
            return Pose.values()[player.getDataWatcher()
                .getWatchableObjectInt(ConfigHandler.MiscellaneousConfig.poseId)];
        } catch (Exception e) {
            return Pose.STANDING;
        }
    }

    public static void setPose(EntityPlayer player, Pose pose) {
        player.getDataWatcher().updateObject(ConfigHandler.MiscellaneousConfig.poseId, pose.ordinal());
    }

    public static boolean isForcingCrawling(EntityPlayer player) {
        return AquaMovementLogic.canForceCrawling(player) && player.getDataWatcher()
            .getWatchableObjectInt(ConfigHandler.MiscellaneousConfig.CrawlingId) == 1;
    }

    public static void setForcingCrawling(EntityPlayer player, boolean flag) {
        if (!ConfigHandler.MovementConfig.enableToggleCrawling) return;
        if (flag && !AquaMovementLogic.canForceCrawling(player)) return;
        player.getDataWatcher().updateObject(ConfigHandler.MiscellaneousConfig.CrawlingId, flag ? 1 : 0);
    }

    /** Preserves the prior callback order: Aqua resize work, then Entity's callback. */
    public static void onDataWatcherChanged(EntityPlayer player, int watcherId) {
        if (watcherId != ConfigHandler.MiscellaneousConfig.poseId || !player.worldObj.isRemote || player.isRiding()) {
            return;
        }

        IPlayerResizeable resizeable = (IPlayerResizeable) player;
        Pose pose = getPose(player);
        // Preserve the old callback's getSize evaluation before derived eye state.
        resizeable.getSize(pose);
        AquaPlayerState state = resizeable.getAquaPlayerState();
        state.playerEyeHeight = AquaPoseLogic.getEyeHeight(player, pose, player.eyeHeight, resizeable.isResizingAllowed());
        state.previousEyeHeight = player.eyeHeight;
        resizeable.recalculateSize();
    }
}
