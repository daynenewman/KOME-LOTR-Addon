package com.enovak.lotrmoremobs.entity.animal;

/**
 * Save-stable origin of a complete Mumak war formation.
 */
public enum MumakilFormationOrigin {
    NONE(0),
    PLAYER_HIRED(1),
    NATURAL_NEAR_HARAD(2),
    INVASION_NEAR_HARAD(3),
    CREATIVE_SPAWN_EGG(4),
    CONQUEST_NEAR_HARAD(5);

    private final int id;

    MumakilFormationOrigin(int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public static MumakilFormationOrigin fromId(int id) {
        MumakilFormationOrigin[] values = values();
        for (int i = 0; i < values.length; ++i) {
            if (values[i].id == id) {
                return values[i];
            }
        }
        return NONE;
    }
}
