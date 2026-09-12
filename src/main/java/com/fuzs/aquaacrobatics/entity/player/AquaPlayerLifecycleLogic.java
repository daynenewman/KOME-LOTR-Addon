package com.fuzs.aquaacrobatics.entity.player;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;

import com.fuzs.aquaacrobatics.entity.EntitySize;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.integration.charactercreation.CharacterCreationIntegration;

/** Shared lifecycle and sleep state semantics; ASM retains the required super dispatch. */
public final class AquaPlayerLifecycleLogic {

    private AquaPlayerLifecycleLogic() {}

    /** Runs before EntityPlayer's inherited preparePlayerToSpawn implementation. */
    public static void beforePreparePlayerToSpawn(EntityPlayer player) {
        player.yOffset = 1.62F;
        ((IPlayerResizeable) player).setPose(Pose.STANDING);
    }

    /** Runs after EntityPlayer's inherited preparePlayerToSpawn implementation. */
    public static void afterPreparePlayerToSpawn(EntityPlayer player) {
        player.setHealth(player.getMaxHealth());
        player.deathTime = 0;
    }

    /** Replaces only sleepInBedAt's accepted vanilla setSize(0.2F, 0.2F) invocation. */
    public static void onSleepSetSize(EntityPlayer player, float width, float height) {
        ((IPlayerResizeable) player).setPose(Pose.SLEEPING);
    }

    public static void onDeath(EntityPlayer player) {
        ((IPlayerResizeable) player).setPose(Pose.DYING);
    }

    public static void onDeath(EntityPlayerMP player) {
        onDeath((EntityPlayer) player);
    }

    public static float defaultEyeHeight(EntityPlayer player) {
        if (!PlayerMovementMode.useModernPlayerMovement(player)) return 1.62F;
        if (((IPlayerResizeable) player).getPose() != Pose.SWIMMING) return 1.62F;

        float desired = swimmingEyeHeight(player);
        return CharacterCreationIntegration.composeDefaultEyeHeight(player, desired);
    }

    public static float swimmingEyeHeight(EntityPlayer player) {
        // This is the legacy EntityPlayer#getEyeHeight component, not the full
        // feet-relative camera height. Aqua's canonical swim origin is:
        // yOffset 0.28 + eyeHeight 0.12 = 0.40 blocks above the physical feet.
        return 0.12F;
    }

    public static float swimmingEyeHeight(EntityPlayerMP player) {
        return swimmingEyeHeight((EntityPlayer) player);
    }

    public static float defaultEyeHeight(EntityPlayerMP player) {
        return defaultEyeHeight((EntityPlayer) player);
    }

    public static boolean hasSwimmingEyeHeight(EntityPlayer player) {
        return PlayerMovementMode.useModernPlayerMovement(player)
            && ((IPlayerResizeable) player).getPose() == Pose.SWIMMING;
    }

    public static boolean hasSwimmingEyeHeight(EntityPlayerMP player) {
        return hasSwimmingEyeHeight((EntityPlayer) player);
    }

    public static EntitySize resizeSize(EntityPlayer player, Pose pose) {
        if (!PlayerMovementMode.useModernPlayerMovement(player)) {
            if (pose == Pose.SLEEPING) return AquaPoseLogic.SLEEPING_SIZE;
            return CharacterCreationIntegration.getStandingSize(player, false);
        }
        return ((IPlayerResizeable) player).getSize(pose);
    }

    public static EntitySize resizeSize(EntityPlayerMP player, Pose pose) {
        return resizeSize((EntityPlayer) player, pose);
    }
}
