package com.lotrcharactercreation.client.trait;

/** Pure calculations for the Uruk-hai edge effect. */
public final class UrukRageHudModel {

    public static final float MAX_RAGE = 100.0F;
    public static final float READY_RAGE = 50.0F;
    public static final int FLASH_TICKS = 5;
    public static final float MAX_NORMAL_OPACITY = 0.60F;
    /** Maximum outward stroke growth in source-atlas pixels. */
    public static final float MAX_THICKNESS_SWELL = 1.0F;
    public static final int HEARTBEAT_FRAMES = 9;
    public static final int HEARTBEAT_ATLAS_COLUMNS = 4;
    private static final float SLOW_HEARTBEAT_TICKS = 30.0F;
    private static final float FAST_HEARTBEAT_TICKS = 16.0F;

    private UrukRageHudModel() {}

    public static float clampRage(float rage) {
        if (Float.isNaN(rage)) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(MAX_RAGE, rage));
    }

    public static boolean isReady(float rage) {
        return clampRage(rage) >= READY_RAGE;
    }

    public static float baseIntensity(float rage) {
        float clamped = clampRage(rage);
        return MAX_NORMAL_OPACITY * Math.min(clamped, READY_RAGE) / READY_RAGE;
    }

    public static float heartbeatPeriodTicks(float rage) {
        float clamped = clampRage(rage);
        float strength = (clamped - READY_RAGE) / (MAX_RAGE - READY_RAGE);
        return SLOW_HEARTBEAT_TICKS - (SLOW_HEARTBEAT_TICKS - FAST_HEARTBEAT_TICKS)
            * Math.max(0.0F, strength);
    }

    public static float thicknessSwelling(float rage, float visualTicks) {
        float clamped = clampRage(rage);
        if (clamped < READY_RAGE) {
            return 0.0F;
        }
        float period = heartbeatPeriodTicks(clamped);
        float phase = positiveRemainder(visualTicks, period) / period;
        float beat = triangularPulse(phase, 0.10F, 0.07F)
            + 0.55F * triangularPulse(phase, 0.30F, 0.06F);
        return MAX_THICKNESS_SWELL * Math.min(1.0F, beat);
    }

    public static float flashBlend(int flashTicksRemaining) {
        return flashTicksRemaining > 0 ? 1.0F : 0.0F;
    }

    public static int heartbeatFrame(float rage, float visualTicks, int flashTicksRemaining) {
        if (flashTicksRemaining > 0) {
            return 0;
        }
        return Math.round(thicknessSwelling(rage, visualTicks) / MAX_THICKNESS_SWELL
            * (HEARTBEAT_FRAMES - 1));
    }

    public static float normalRed(float rage) {
        return isReady(rage) ? 0.78F : 0.62F;
    }

    public static float intensity(float rage, float visualTicks, int flashTicksRemaining) {
        if (flashTicksRemaining > 0) {
            return 1.0F;
        }
        return baseIntensity(rage);
    }

    public static float colorChannel(float normal, float flash, float blend) {
        return normal + (flash - normal) * blend;
    }

    public static boolean shouldRender(boolean active, boolean hasWorld, boolean alive, boolean hideGui,
        float rage, int flashTicksRemaining) {
        return active && hasWorld && alive && !hideGui
            && (clampRage(rage) > 0.0F || flashTicksRemaining > 0);
    }

    private static float triangularPulse(float value, float center, float halfWidth) {
        return Math.max(0.0F, 1.0F - Math.abs(value - center) / halfWidth);
    }

    private static float positiveRemainder(float value, float divisor) {
        float remainder = value % divisor;
        return remainder < 0.0F ? remainder + divisor : remainder;
    }
}
