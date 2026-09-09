package com.fuzs.aquaacrobatics.entity.player;

import net.minecraft.entity.player.EntityPlayer;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;

import com.fuzs.aquaacrobatics.entity.EntitySize;
import com.fuzs.aquaacrobatics.entity.Pose;

/** Shared size metadata semantics used by the ASM-owned EntityPlayer facades. */
public final class AquaPlayerSizeMetadataLogic {

    private AquaPlayerSizeMetadataLogic() {}

    /**
     * Preserves EntityPlayerMixin's constructor-return order: size first, then
     * the derived eye height whose resize gate reads that size through getWidth/Height.
     */
    public static void initialize(EntityPlayer player) {
        AquaPlayerState state = ((IAquaPlayerStateHolder) player).getAquaPlayerState();
        state.size = AquaPoseLogic.getSize(player, Pose.STANDING);
        state.playerEyeHeight = getEyeHeight(player, Pose.STANDING, state.size);
    }

    public static float getEyeHeight(EntityPlayer player, Pose pose, EntitySize size) {
        if (!PlayerMovementMode.useModernPlayerMovement(player)) return player.eyeHeight;
        return AquaPoseLogic.getEyeHeight(
            player,
            pose,
            player.eyeHeight,
            ((IPlayerResizeable) player).isResizingAllowed());
    }

    /** Preserves the former getSize body plus its inseparable DYING Mixin override. */
    public static EntitySize getSize(EntityPlayer player, Pose pose) {
        return AquaPoseLogic.getSize(player, pose);
    }
}
