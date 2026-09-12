package com.enovak.lotrmoremobs.compat;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import net.minecraft.entity.Entity;

/**
 * Selects the damage amount at the exact point where LOTR normally replaces
 * non-creative Gandalf damage with zero.
 */
public final class MortalGandalfDamageHook {
    private MortalGandalfDamageHook() {
    }

    public static float selectDamage(
            Object gandalf,
            float originalAmount
    ) {
        if (!MumakilConfig.mortalGandalf
                || !(gandalf instanceof Entity)) {
            return 0.0F;
        }

        Entity entity = (Entity)gandalf;
        if (entity.worldObj == null || entity.worldObj.isRemote) {
            return 0.0F;
        }
        return originalAmount;
    }
}
