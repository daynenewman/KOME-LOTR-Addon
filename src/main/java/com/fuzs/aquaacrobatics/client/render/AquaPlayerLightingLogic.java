package com.fuzs.aquaacrobatics.client.render;

import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.opengl.GL11;

import com.fuzs.aquaacrobatics.integration.charactercreation.CharacterCreationIntegration;

/**
 * Character Creation temporarily rewrites the local player's yOffset while its
 * racial renderer is active. Reapply the lightmap immediately before that
 * renderer delegates into RenderPlayer, using the physical (pre-render) yOffset.
 */
public final class AquaPlayerLightingLogic {

    private AquaPlayerLightingLogic() {}

    public static void applyCharacterCreationLightmap(Object playerObject, float partialTicks) {
        if (!(playerObject instanceof EntityPlayer)) return;

        EntityPlayer player = (EntityPlayer) playerObject;
        if (!CharacterCreationIntegration.hasCharacterCreationRace(player)) return;

        CharacterCreationIntegration.BodyProfile profile = CharacterCreationIntegration.getBodyProfile(player);
        if (!profile.fromCharacterCreation || "MAN".equals(profile.raceName)) return;

        double renderCompensation = CharacterCreationIntegration.getLocalRenderYOffsetCompensation(player);
        if (Math.abs(renderCompensation) < 1.0E-6D) return;

        float renderYOffset = player.yOffset;
        int brightness;
        try {
            // Restore only the yOffset that Character Creation changed for this
            // render tick, let vanilla calculate the exact packed light value, then
            // put the racial render offset back before drawing.
            player.yOffset = (float) ((double) renderYOffset - renderCompensation);
            brightness = player.getBrightnessForRender(partialTicks);
        } finally {
            player.yOffset = renderYOffset;
        }

        int low = brightness % 65536;
        int high = brightness / 65536;
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, (float) low, (float) high);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
