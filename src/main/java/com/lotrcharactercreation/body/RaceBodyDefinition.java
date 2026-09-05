package com.lotrcharactercreation.body;

import com.lotrcharactercreation.race.PlayerRace;

public enum RaceBodyDefinition {

    MAN(PlayerRace.MAN, 0.60F, 1.80F, Float.NaN, 1.0F, false),
    ELF(PlayerRace.ELF, 0.60F, 1.80F, 1.53F, 1.0F, true),
    DWARF(PlayerRace.DWARF, 0.50F, 1.50F, 1.275F, 0.8125F, true),
    HOBBIT(PlayerRace.HOBBIT, 0.45F, 1.20F, 1.02F, 0.75F, true),
    ORC(PlayerRace.ORC, 0.50F, 1.55F, 1.3175F, 0.85F, true),
    URUK_HAI(PlayerRace.URUK_HAI, 0.60F, 1.80F, 1.53F, 1.0F, true);

    private final PlayerRace race;
    private final float width;
    private final float height;
    private final float targetEyeHeight;
    private final float renderScale;
    private final boolean prototypeSizeEnabled;

    RaceBodyDefinition(PlayerRace race, float width, float height, float targetEyeHeight, float renderScale,
        boolean prototypeSizeEnabled) {
        this.race = race;
        this.width = width;
        this.height = height;
        this.targetEyeHeight = targetEyeHeight;
        this.renderScale = renderScale;
        this.prototypeSizeEnabled = prototypeSizeEnabled;
    }

    public PlayerRace getRace() {
        return race;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public boolean hasTargetEyeHeight() {
        return !Float.isNaN(targetEyeHeight);
    }

    public float getTargetEyeHeight() {
        return targetEyeHeight;
    }

    public float getRenderScale() {
        return renderScale;
    }

    public boolean isPrototypeSizeEnabled() {
        return prototypeSizeEnabled;
    }

    public static RaceBodyDefinition forRace(PlayerRace race) {
        if (race != null) {
            for (RaceBodyDefinition definition : values()) {
                if (definition.race == race) {
                    return definition;
                }
            }
        }

        return MAN;
    }
}
