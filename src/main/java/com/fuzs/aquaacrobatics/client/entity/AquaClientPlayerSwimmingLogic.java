package com.fuzs.aquaacrobatics.client.entity;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;
import net.minecraft.client.entity.EntityClientPlayerMP;

import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.fuzs.aquaacrobatics.integration.charactercreation.CharacterCreationIntegration;

public final class AquaClientPlayerSwimmingLogic {
    private AquaClientPlayerSwimmingLogic() {}
    public static boolean isActuallySneaking(EntityClientPlayerMP player) { return player.isSneaking(); }
    public static boolean isForcedDown(EntityClientPlayerMP player) { return PlayerMovementMode.useModernPlayerMovement(player) && ((IPlayerResizeable) player).isResizingAllowed() && !player.capabilities.isFlying && (((IPlayerResizeable) player).getPose() == Pose.CROUCHING || ((IPlayerResizeable) player).isVisuallySwimming()); }
    public static boolean isUsingSwimmingAnimation(EntityClientPlayerMP player) { return PlayerMovementMode.useModernPlayerMovement(player) && ((IPlayerSPSwimming) player).isUsingSwimmingAnimation(player.movementInput.moveForward, player.movementInput.moveStrafe); }
    public static boolean isUsingSwimmingAnimation(EntityClientPlayerMP player, float forward, float strafe) { if (!PlayerMovementMode.useModernPlayerMovement(player)) return false; IPlayerSPSwimming swimming = (IPlayerSPSwimming) player; return swimming.canSwim() ? swimming.isMovingForward(forward, strafe) : (ConfigHandler.MovementConfig.sidewaysSprinting ? forward >= 0.8F || Math.abs(strafe) > 0.8F : forward >= 0.8F); }
    public static boolean canSwim(EntityClientPlayerMP player) { return PlayerMovementMode.useModernPlayerMovement(player) && ((IPlayerResizeable) player).getEyesInWaterPlayer(); }
    public static boolean isMovingForward(EntityClientPlayerMP player, float forward, float strafe) { return PlayerMovementMode.useModernPlayerMovement(player) && (forward > 1.0E-5F || ConfigHandler.MovementConfig.sidewaysSwimming && Math.abs(strafe) > 1.0E-5F); }
    public static boolean canPerformElytraTakeoff(EntityClientPlayerMP player) { return false; }

    /**
     * 1.7.10 sends both boundingBox.minY (feet) and posY (stance) in movement packets.
     * Character Creation can temporarily leave those values farther apart than the
     * vanilla server accepts while a racial body is transitioning between poses.
     * Normalize only an actually-illegal stance for the duration of packet assembly;
     * the player's real client position is restored immediately afterward.
     */
    public static double beginLegalNetworkStance(EntityClientPlayerMP player) {
        double originalPosY = player.posY;
        if (!PlayerMovementMode.useModernPlayerMovement(player)
            || !CharacterCreationIntegration.hasCharacterCreationRace(player)
            || player.boundingBox == null) return originalPosY;

        double feetY = player.boundingBox.minY;
        double stance = originalPosY - feetY;
        if (Double.isNaN(stance) || Double.isInfinite(stance) || stance < 0.1D || stance > 1.65D) {
            double safeStance = ((IPlayerResizeable) player).getPose() == Pose.SWIMMING ? 0.28D : 1.62D;
            player.posY = feetY + safeStance;
        }
        return originalPosY;
    }

    public static void endLegalNetworkStance(EntityClientPlayerMP player, double originalPosY) {
        player.posY = originalPosY;
    }
}
