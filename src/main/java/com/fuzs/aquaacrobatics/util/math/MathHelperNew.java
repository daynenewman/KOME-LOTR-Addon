package com.fuzs.aquaacrobatics.util.math;


import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class MathHelperNew {

    public static float lerp(float pct, float start, float end) {
        return start + pct * (end - start);
    }

    @SideOnly(Side.CLIENT)
    public static boolean epsilonEquals(float p_180185_0_, float p_180185_1_) {
        return Math.abs(p_180185_1_ - p_180185_0_) < 1.0E-5F;
    }
}
