package com.lotrcharactercreation.creation;

public enum CharacterCreationStage {

    RACE("race"),
    SEX("sex"),
    FACTION("faction"),
    APPEARANCE("appearance"),
    CONFIRMATION("confirmation"),
    COMPLETE("complete");

    private final String serializedId;

    CharacterCreationStage(String serializedId) {
        this.serializedId = serializedId;
    }

    public String getSerializedId() {
        return serializedId;
    }

    public static CharacterCreationStage findBySerializedId(String serializedId) {
        if (serializedId != null) {
            for (CharacterCreationStage stage : values()) {
                if (stage.serializedId.equals(serializedId)) {
                    return stage;
                }
            }
        }
        return null;
    }
}
