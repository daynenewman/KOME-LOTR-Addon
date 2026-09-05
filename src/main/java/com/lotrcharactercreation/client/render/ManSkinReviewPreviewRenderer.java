package com.lotrcharactercreation.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.client.model.PlayerManModelAdapter;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ManSkinReviewPreviewRenderer {

    private static final float MODEL_UNIT = 0.0625F;
    private static final float VANILLA_PLAYER_RENDER_SCALE = 0.9375F;

    private final PlayerManModelAdapter model = new PlayerManModelAdapter();

    public void draw(EntityPlayer player, PlayerSex sex, ResourceLocation texture, int centerX, int bottomY, int scale,
        float mouseOffsetX, float mouseOffsetY, float partialTicks) {
        if (player == null || sex == null || texture == null) {
            return;
        }

        float bodyYaw = (float) Math.atan(mouseOffsetX / 40.0F) * 35.0F;
        float headYaw = (float) Math.atan(mouseOffsetX / 40.0F) * 20.0F;
        float headPitch = -((float) Math.atan(mouseOffsetY / 40.0F)) * 20.0F;

        model.resetPlayerPresentation();
        model.configurePlayerPresentation(sex);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glEnable(GL11.GL_COLOR_MATERIAL);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL12.GL_RESCALE_NORMAL);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glTranslatef(centerX, bottomY, 100.0F);
            GL11.glScalef(-scale, scale, scale);
            GL11.glRotatef(135.0F, 0.0F, 1.0F, 0.0F);
            RenderHelper.enableStandardItemLighting();
            GL11.glRotatef(-135.0F, 0.0F, 1.0F, 0.0F);
            GL11.glRotatef(180.0F + bodyYaw, 0.0F, 1.0F, 0.0F);
            GL11.glScalef(VANILLA_PLAYER_RENDER_SCALE, VANILLA_PLAYER_RENDER_SCALE, VANILLA_PLAYER_RENDER_SCALE);
            GL11.glTranslatef(0.0F, -24.0F * MODEL_UNIT - 0.0078125F, 0.0F);

            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(texture);
            model.isChild = false;
            model.onGround = 0.0F;
            model.render(player, 0.0F, 0.0F, player.ticksExisted + partialTicks, headYaw, headPitch, MODEL_UNIT);
        } finally {
            model.resetPlayerPresentation();
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
