package com.enovak.lotrmoremobs.siege.gate;

import com.enovak.lotrmoremobs.siege.ram.EntityBattleRam;
import net.minecraft.entity.EntityLiving;

/**
 * Marks only the dynamic extent of an authoritative standard-AI sight ray.
 */
public final class SiegeGateNpcSightContext {

    private static final ThreadLocal<Integer> DEPTH =
            new ThreadLocal<Integer>();

    private SiegeGateNpcSightContext() {
    }

    public static boolean enter(EntityLiving observer) {
        if (observer == null
                || observer instanceof EntityBattleRam
                || observer.worldObj == null
                || observer.worldObj.isRemote) {
            return false;
        }
        Integer depth = DEPTH.get();
        DEPTH.set(Integer.valueOf(depth == null ? 1 : depth.intValue() + 1));
        return true;
    }

    public static void exit() {
        Integer depth = DEPTH.get();
        if (depth == null || depth.intValue() <= 1) {
            DEPTH.remove();
        } else {
            DEPTH.set(Integer.valueOf(depth.intValue() - 1));
        }
    }

    public static boolean isActive() {
        Integer depth = DEPTH.get();
        return depth != null && depth.intValue() > 0;
    }
}
