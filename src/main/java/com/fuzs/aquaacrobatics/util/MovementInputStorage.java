package com.fuzs.aquaacrobatics.util;

import net.minecraft.client.settings.GameSettings;
import net.minecraft.util.MovementInput;

public class MovementInputStorage extends MovementInput {

    public int sprintToggleTimer;
    public boolean isFlying;
    public boolean isSprinting;
    public boolean isStartingToFly;

    public void copyFrom(MovementInput movement) {

        this.moveStrafe = movement.moveStrafe;
        this.moveForward = movement.moveForward;
        this.jump = movement.jump;
        this.sneak = movement.sneak;
    }

    public static void updatePlayerMoveState(MovementInput movement, GameSettings gameSettings, boolean isCrouching) {

        movement.moveForward = gameSettings.keyBindForward.getIsKeyPressed()
            == gameSettings.keyBindBack.getIsKeyPressed() ? 0.0F
                : gameSettings.keyBindForward.getIsKeyPressed() ? 1.0F : -1.0F;
        movement.moveStrafe = gameSettings.keyBindLeft.getIsKeyPressed() == gameSettings.keyBindRight.getIsKeyPressed()
            ? 0.0F
            : gameSettings.keyBindLeft.getIsKeyPressed() ? 1.0F : -1.0F;
        movement.jump = gameSettings.keyBindJump.getIsKeyPressed();
        movement.sneak = gameSettings.keyBindSneak.getIsKeyPressed();
        if (isCrouching) {

            movement.moveStrafe = (float) ((double) movement.moveStrafe * 0.3);
            movement.moveForward = (float) ((double) movement.moveForward * 0.3);
        }
    }

}
