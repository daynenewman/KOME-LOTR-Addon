package com.enovak.lotrmoremobs.siege.gate;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import net.minecraft.entity.EntityLiving;
import net.minecraft.world.World;
import com.enovak.lotrmoremobs.siege.SiegeRegistry;

/**
 * Provides the narrow server-side PathFinder GatePart classification hook.
 */
public final class SiegeGateAiPathHelper {

    private SiegeGateAiPathHelper() {
    }

    public static boolean shouldTreatKnownGatePartAsClear(
            Object blockObject,
            Object entityObject,
            int x,
            int y,
            int z
    ) {
        if (blockObject != SiegeRegistry.gatePart) {
            return false;
        }

        if (!MumakilConfig.enableSiegeGates) {
            return false;
        }

        if (!(entityObject instanceof EntityLiving)
                || entityObject instanceof EntityBattleRam) {
            return false;
        }

        World world = ((EntityLiving)entityObject).worldObj;
        return world != null
                && !world.isRemote
                && SiegeGateNpcSightHelper.isGatePartOpenToAi(
                world,
                x,
                y,
                z
        );
    }
}
