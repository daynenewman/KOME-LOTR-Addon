package com.fuzs.aquaacrobatics.entity.player;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;

import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.EntitySize;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.integration.charactercreation.CharacterCreationIntegration;
import com.fuzs.aquaacrobatics.integration.efr.EFRIntegration;

/** Shared pose selection, dimensions, and clearance calculations. */
public final class AquaPoseLogic {

    public static final EntitySize STANDING_SIZE = EntitySize.flexible(0.6F, 1.8F);
    public static final EntitySize SLEEPING_SIZE = EntitySize.fixed(0.2F, 0.2F);
    public static final EntitySize SWIMMING_SIZE = EntitySize.flexible(0.6F, 0.6F);
    public static final EntitySize CROUCHING_SIZE = EntitySize.flexible(0.6F, 1.5F);

    private AquaPoseLogic() {}

    public static EntitySize getSize(Pose pose) {
        switch (pose) {
            case SLEEPING:
            case DYING:
                return SLEEPING_SIZE;
            case FALL_FLYING:
            case SWIMMING:
            case SPIN_ATTACK:
                return SWIMMING_SIZE;
            case CROUCHING:
                return CROUCHING_SIZE;
            default:
                return STANDING_SIZE;
        }
    }

    /**
     * Player-aware dimensions. Character Creation owns the racial standing body;
     * Aqua derives pose dimensions from that base instead of replacing it with
     * vanilla 0.6 x 1.8 dimensions.
     */
    public static EntitySize getSize(EntityPlayer player, Pose pose) {
        CharacterCreationIntegration.BodyProfile body = CharacterCreationIntegration.getBodyProfile(player);
        switch (pose) {
            case SLEEPING:
                return SLEEPING_SIZE;
            case FALL_FLYING:
            case SWIMMING:
            case SPIN_ATTACK:
                // Keep Aqua's proven 0.60-block crawl/swim geometry for every race.
                // Character Creation still owns racial width, but scaling the pose height
                // made short races sit below the canonical Aqua collision/camera origin.
                return EntitySize.flexible(body.width, SWIMMING_SIZE.height);
            case CROUCHING:
                return EntitySize.flexible(body.width, body.height * (CROUCHING_SIZE.height / STANDING_SIZE.height));
            case DYING:
                return new EntitySize(body.width, body.height, false);
            default:
                return EntitySize.flexible(body.width, body.height);
        }
    }

    public static Pose choosePose(EntityPlayer player, IPlayerResizeable resizeable) {
        if (resizeable.getShouldBeDead()) return Pose.DYING;
        if (player.isPlayerSleeping()) return Pose.SLEEPING;
        if (!PlayerMovementMode.useModernPlayerMovement(player)) return Pose.STANDING;

        boolean swimmingClear = isPoseClear(player, resizeable, Pose.SWIMMING);
        boolean crouchingClear = isPoseClear(player, resizeable, Pose.CROUCHING);
        boolean standingClear = isPoseClear(player, resizeable, Pose.STANDING);
        if (EFRIntegration.isElytraFlying(player)) return Pose.FALL_FLYING;
        if (resizeable.isForcingCrawling() || resizeable.isSwimming()) return Pose.SWIMMING;
        if (!standingClear) {
            if (canUseCrouchPose(player) && crouchingClear) return Pose.CROUCHING;
            if (ConfigHandler.MovementConfig.enableCrawling && swimmingClear) return Pose.SWIMMING;
            return resizeable.getPose();
        }
        if (resizeable.isActuallySneaking() && canUseCrouchPose(player) && crouchingClear) return Pose.CROUCHING;
        return Pose.STANDING;
    }

    /** Applies the existing post-player-tick pose boundary. */
    public static void updatePose(EntityPlayer player, IPlayerResizeable resizeable) {
        boolean modern = PlayerMovementMode.useModernPlayerMovement(player);
        if (!modern) {
            resizeable.setSwimming(false);
            resizeable.setForcingCrawling(false);
        }

        Pose pose = choosePose(player, resizeable);
        if (player.worldObj.isRemote) {
            // Preserve Aqua's native 1.7.10 pose origin for every race. In the
            // 0.60-high swim/crawl pose, yOffset 0.28 plus getEyeHeight 0.12
            // places the logical interaction eye at the canonical 0.40 above feet.
            // Character Creation's renderer compensates its racial model around
            // this value during the render tick.
            player.yOffset = modern && pose == Pose.SWIMMING ? 0.28F : 1.62F;
        }
        resizeable.setPose(pose);

        // Preserve Phase 2A's two-step ordering. setPose can synchronously notify
        // the client DataWatcher path, which recalculates size/eye bookkeeping.
        // Re-read the resulting pose only after that mutation has completed rather
        // than carrying the pre-notification local value into camera state.
        updateEyeHeight(player, resizeable);
    }

    private static void updateEyeHeight(EntityPlayer player, IPlayerResizeable resizeable) {
        AquaPlayerState state = resizeable.getAquaPlayerState();
        if (player.eyeHeight != state.previousEyeHeight) {
            Pose pose = resizeable.getPose();
            // Preserve the Phase 2A post-DataWatcher getSize evaluation (including
            // its existing DYING-size Mixin special case) before eye bookkeeping.
            resizeable.getSize(pose);
            state.playerEyeHeight = getEyeHeight(player, pose, player.eyeHeight, resizeable.isResizingAllowed());
            state.previousEyeHeight = player.eyeHeight;
        }
    }

    public static boolean canUseCrouchPose(EntityPlayer player) {
        return !player.capabilities.isFlying && (player.onGround || !player.isInWater()) && !player.isOnLadder();
    }

    public static boolean isPoseClear(EntityPlayer player, IPlayerResizeable resizeable, Pose pose) {
        return player.worldObj.getCollidingBoundingBoxes(player, getBoundingBox(player, resizeable, pose)).isEmpty();
    }

    public static AxisAlignedBB getBoundingBox(EntityPlayer player, IPlayerResizeable resizeable, Pose pose) {
        EntitySize size = resizeable.getSize(pose);
        float halfWidth = size.width / 2.0F;
        return AxisAlignedBB.getBoundingBox(
            player.posX - halfWidth,
            player.boundingBox.minY,
            player.posZ - halfWidth,
            player.posX + halfWidth,
            player.boundingBox.minY + size.height,
            player.posZ + halfWidth);
    }

    public static float getEyeHeight(EntityPlayer player, Pose pose, float vanillaEyeHeight, boolean resizingAllowed) {
        if (pose == Pose.SLEEPING || pose == Pose.DYING) return 0.2F;
        float heightScale = CharacterCreationIntegration.getHeightScale(player);
        switch (pose) {
            case SWIMMING:
                // Legacy 1.7.10 players use yOffset + getEyeHeight as the
                // interaction eye. 0.28 + 0.12 = Aqua's canonical 0.40.
                return AquaPlayerLifecycleLogic.swimmingEyeHeight(player);
            case FALL_FLYING:
            case SPIN_ATTACK:
                return vanillaEyeHeight;
            case CROUCHING:
                return (resizingAllowed ? 0.35F : 0.08F) * heightScale;
            default:
                return vanillaEyeHeight;
        }
    }
}
