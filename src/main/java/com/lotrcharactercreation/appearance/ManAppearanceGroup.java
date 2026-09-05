package com.lotrcharactercreation.appearance;

public enum ManAppearanceGroup {

    ANGMAR("angmar", "Angmar"),
    BREE_LAND("bree_land", "Bree-land"),
    DALE("dale", "Dale"),
    DORWINION("dorwinion", "Dorwinion"),
    DUNEDAIN_NORTH("dunedain_north", "Dúnedain of the North"),
    DUNLAND("dunland", "Dunland"),
    GONDOR("gondor", "Gondor"),
    MORDOR("mordor", "Mordor"),
    MORWAITH("morwaith", "Morwaith"),
    NEAR_HARAD("near_harad", "Near Harad"),
    EASTERLINGS("easterlings", "Easterlings"),
    ROHAN("rohan", "Rohan"),
    TAURETHRIM("taurethrim", "Taurethrim"),
    WANDERER("wanderer", "Wanderer");

    private final String serializedId;
    private final String displayName;

    ManAppearanceGroup(String serializedId, String displayName) {
        this.serializedId = serializedId;
        this.displayName = displayName;
    }

    public String getSerializedId() {
        return serializedId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static ManAppearanceGroup findBySerializedId(String serializedId) {
        if (serializedId == null) {
            return null;
        }

        for (ManAppearanceGroup group : values()) {
            if (group.serializedId.equals(serializedId)) {
                return group;
            }
        }

        return null;
    }
}
