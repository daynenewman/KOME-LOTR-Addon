package kome.common.data;

import lotr.common.fac.LOTRFactionRelations;

import java.util.Locale;

/** KOME's workflow/display names for LOTR's authoritative five-rung relation ladder. */
public enum KOMEDiplomacyRelation {
    MORTAL_ENEMIES("mortal_enemy", "Mortal Enemy", 0, LOTRFactionRelations.Relation.MORTAL_ENEMY),
    ENEMIES("enemy", "Enemy", 1, LOTRFactionRelations.Relation.ENEMY),
    NEUTRAL("neutral", "Neutral", 2, LOTRFactionRelations.Relation.NEUTRAL),
    FRIENDS("friends", "Friend", 3, LOTRFactionRelations.Relation.FRIEND),
    ALLIES("allies", "Ally", 4, LOTRFactionRelations.Relation.ALLY);

    public final String key;
    public final String displayName;
    private final int rank;
    private final LOTRFactionRelations.Relation lotrRelation;

    KOMEDiplomacyRelation(String key, String displayName, int rank,
            LOTRFactionRelations.Relation lotrRelation) {
        this.key = key;
        this.displayName = displayName;
        this.rank = rank;
        this.lotrRelation = lotrRelation;
    }

    public int rank() {
        return rank;
    }

    public LOTRFactionRelations.Relation toLotrRelation() {
        return lotrRelation;
    }

    public KOMEDiplomacyRelation oneStepWorse() {
        return rank <= MORTAL_ENEMIES.rank ? null : values()[ordinal() - 1];
    }

    public static KOMEDiplomacyRelation fromLotrRelation(LOTRFactionRelations.Relation relation) {
        if (relation != null) {
            for (KOMEDiplomacyRelation value : values()) {
                if (value.lotrRelation == relation) {
                    return value;
                }
            }
        }
        return NEUTRAL;
    }

    public static KOMEDiplomacyRelation parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Diplomacy relation is required");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        if ("mortal_enemies".equals(normalized) || "mortalenemy".equals(normalized)) {
            normalized = "mortal_enemy";
        } else if ("enemies".equals(normalized)) {
            normalized = "enemy";
        } else if ("friend".equals(normalized)) {
            normalized = "friends";
        } else if ("ally".equals(normalized)) {
            normalized = "allies";
        }
        for (KOMEDiplomacyRelation relation : values()) {
            if (relation.key.equals(normalized)) {
                return relation;
            }
        }
        throw new IllegalArgumentException("Unknown diplomacy relation: " + value);
    }
}
