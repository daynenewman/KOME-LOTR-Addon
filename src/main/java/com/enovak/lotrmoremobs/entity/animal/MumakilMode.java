package com.enovak.lotrmoremobs.entity.animal;

public enum MumakilMode {
    BABY_WILD(0, true, false),
    BABY_TAMED(1, true, true),
    ADULT_WILD(2, false, false),
    ADULT_TAMED(3, false, true),
    HIRED_WAR(4, false, false);

    private final int id;
    private final boolean baby;
    private final boolean tamed;

    MumakilMode(int id, boolean baby, boolean tamed) {
        this.id = id;
        this.baby = baby;
        this.tamed = tamed;
    }

    public int getId() {
        return this.id;
    }

    public boolean isBaby() {
        return this.baby;
    }

    public boolean isAdult() {
        return !this.baby;
    }

    public boolean isTamed() {
        return this.tamed;
    }

    public static MumakilMode fromId(int id) {
        MumakilMode[] modes = values();

        for (int i = 0; i < modes.length; ++i) {
            if (modes[i].id == id) {
                return modes[i];
            }
        }

        return null;
    }
}
