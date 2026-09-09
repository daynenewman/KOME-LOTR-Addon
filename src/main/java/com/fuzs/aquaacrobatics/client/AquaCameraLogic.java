package com.fuzs.aquaacrobatics.client;

import net.minecraft.entity.player.EntityPlayer;

import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.integration.charactercreation.CharacterCreationIntegration;
/** Modern pose-driven camera values, independent of the hook used to apply them. */
public final class AquaCameraLogic {

    public static final float STANDING_EYE_HEIGHT = 1.62F;
    public static final float CROUCHING_EYE_HEIGHT = 1.27F;
    public static final float SWIMMING_EYE_HEIGHT = 0.4F;

    private AquaCameraLogic() {}

    public static float getPoseEyeHeight(EntityPlayer player, Pose pose) {
        float heightScale = CharacterCreationIntegration.getHeightScale(player);
        float standingEyeHeight = CharacterCreationIntegration.getStandingEyeHeight(player);
        switch (pose) {
            case CROUCHING:
                return standingEyeHeight - (STANDING_EYE_HEIGHT - CROUCHING_EYE_HEIGHT) * heightScale;
            case SWIMMING:
            case FALL_FLYING:
            case SPIN_ATTACK:
                // Match the canonical Aqua 0.60-block crawl/swim box instead of
                // scaling the camera downward with a short race's standing body.
                return SWIMMING_EYE_HEIGHT;
            default:
                return standingEyeHeight;
        }
    }

    public static float interpolate(float current, float target) {
        return current + (target - current) * 0.5F;
    }

}
