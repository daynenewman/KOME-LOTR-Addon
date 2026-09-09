package com.enovak.lotrmoremobs.siege.ram;

public enum BattleRamState {
    FOLLOW_COMMANDER,
    MOVE_TO_GATE,
    ATTACK_GATE,
    PAUSED;

    public static BattleRamState fromName(String name) {
        if (name != null) {
            try {
                return valueOf(name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return FOLLOW_COMMANDER;
    }
}
