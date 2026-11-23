package com.lotrcharactercreation.trait;

/** Arithmetic used only after the server has identified an eligible Uruk melee attack. */
public final class UrukHeavyBlowRules {

    private UrukHeavyBlowRules() {}

    public static float chance(float rage, float cost, float maximumRage) {
        if (!(rage >= cost) || maximumRage <= 0.0F) {
            return 0.0F;
        }
        return Math.max(0.0F, Math.min(1.0F, rage / maximumRage));
    }

    public static float remainingRage(float rage, float cost) {
        return rage - cost;
    }
}
