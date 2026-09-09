package com.fuzs.aquaacrobatics.client;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.fuzs.aquaacrobatics.util.math.MathHelperNew;

/**
 * Client-tick-owned first-person camera state. Rendering only interpolates the
 * state established after the local player's tick has completed.
 */
public final class AquaCameraState {

    public static final AquaCameraState INSTANCE = new AquaCameraState();

    private EntityPlayer player;
    private double previousFeetY;
    private double feetY;
    private float previousEyeHeight;
    private float eyeHeight;

    private AquaCameraState() {}

    public void tick(Minecraft minecraft) {
        EntityPlayer localPlayer = minecraft.thePlayer;
        if (localPlayer == null || minecraft.renderViewEntity != localPlayer
                || !PlayerMovementMode.useModernPlayerMovement(localPlayer)) {
            this.reset();
            return;
        }

        IPlayerResizeable resizeable = (IPlayerResizeable) localPlayer;
        double currentFeetY = getPhysicalFeetY(localPlayer);
        float targetEyeHeight = AquaCameraLogic.getPoseEyeHeight(localPlayer, resizeable.getPose());
        if (this.player != localPlayer) {
            this.player = localPlayer;
            this.previousFeetY = currentFeetY;
            this.feetY = currentFeetY;
            this.previousEyeHeight = targetEyeHeight;
            this.eyeHeight = targetEyeHeight;
            return;
        }

        this.previousFeetY = this.feetY;
        this.feetY = currentFeetY;
        this.previousEyeHeight = this.eyeHeight;
        this.eyeHeight = AquaCameraLogic.interpolate(this.eyeHeight, targetEyeHeight);
    }

    public float getLegacyCameraCorrection(EntityPlayer player, float partialTicks) {
        if (this.player != player) {
            // The normal initialization path is the client tick. This fallback only
            // keeps the very first pre-tick render feet-relative without owning state.
            double interpolatedEntityY = player.prevPosY + (player.posY - player.prevPosY) * partialTicks;
            return (float) (interpolatedEntityY - player.boundingBox.minY
                - AquaCameraLogic.getPoseEyeHeight(player, ((IPlayerResizeable) player).getPose()));
        }

        double interpolatedEntityY = player.prevPosY + (player.posY - player.prevPosY) * partialTicks;
        double interpolatedFeetY = this.previousFeetY + (this.feetY - this.previousFeetY) * partialTicks;
        float interpolatedEyeHeight = MathHelperNew.lerp(partialTicks, this.previousEyeHeight, this.eyeHeight);
        return (float) (interpolatedEntityY - interpolatedFeetY - interpolatedEyeHeight);
    }

    private void reset() {
        this.player = null;
    }

    private static double getPhysicalFeetY(EntityPlayer player) {
        // Aqua's client pose resize directly rewrites height while preserving
        // boundingBox.minY. During the resize tick yOffset changes after posY
        // was established, so posY - yOffset + ySize is not the physical base.
        return player.boundingBox.minY;
    }
}
