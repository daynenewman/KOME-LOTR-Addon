package com.fuzs.aquaacrobatics.entity.projectile;

import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import com.fuzs.aquaacrobatics.config.ConfigHandler;

/** Shared projectile policy preserved from the former EntityThrowable Mixin. */
public final class AquaThrowableLogic {

    private AquaThrowableLogic() {}

    public static boolean isNewProjectile(EntityThrowable throwable) {
        return ConfigHandler.MovementConfig.newProjectileBehavior && throwable.getClass()
            .getName()
            .startsWith("net.minecraft.");
    }

    public static MovingObjectPosition rayTraceThroughLiquid(boolean isNewProjectile, World world, Vec3 start, Vec3 end) {
        return isNewProjectile ? world.func_147447_a(start, end, false, true, false) : world.rayTraceBlocks(start, end);
    }
}
