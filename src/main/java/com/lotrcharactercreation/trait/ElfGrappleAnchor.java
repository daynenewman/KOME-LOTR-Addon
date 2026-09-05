package com.lotrcharactercreation.trait;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

public final class ElfGrappleAnchor {

    public static final double TARGET_HEIGHT_FRACTION = 1.00D;
    public static final double TARGET_WIDTH_BACK_FACTOR = 0.35D;
    public static final double MINIMUM_BACK_DISTANCE = 0.20D;
    public static final double SMALL_TARGET_HEIGHT = 2.00D;
    public static final double LARGE_TARGET_HEIGHT = 3.00D;
    public static final double MINIMUM_LARGE_TARGET_CORRECTION = 0.25D;
    public static final double MAXIMUM_SMALL_TARGET_CORRECTION = 0.75D;

    private ElfGrappleAnchor() {}

    public static void apply(EntityPlayer player, EntityLivingBase target) {
        double yawRadians = Math.toRadians(target.rotationYaw);
        double backDistance = Math.max(MINIMUM_BACK_DISTANCE, target.width * TARGET_WIDTH_BACK_FACTOR);
        double x = target.posX + Math.sin(yawRadians) * backDistance;
        double smallTargetBlend = Math.max(
            0.0D,
            Math.min(1.0D, (LARGE_TARGET_HEIGHT - target.height) / (LARGE_TARGET_HEIGHT - SMALL_TARGET_HEIGHT)));
        double heightCorrection = MINIMUM_LARGE_TARGET_CORRECTION
            + smallTargetBlend * (MAXIMUM_SMALL_TARGET_CORRECTION - MINIMUM_LARGE_TARGET_CORRECTION);
        double feetY = target.posY + target.height * TARGET_HEIGHT_FRACTION - heightCorrection;
        double entityY = feetY + player.yOffset - player.ySize;
        double z = target.posZ - Math.cos(yawRadians) * backDistance;
        player.setPosition(x, entityY, z);
    }
}
