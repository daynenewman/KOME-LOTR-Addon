package com.enovak.lotrmoremobs.siege.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;

import org.lwjgl.opengl.GL11;

public class GuiGateAppearanceButton
        extends GuiButton {

    private static final ResourceLocation ICON_TEXTURE =
            new ResourceLocation(
                    "lotrmoremobs",
                    "textures/gui/gate_controller_cube_icon.png"
            );

    /*
     * The supplied button icon should be a 16x16 PNG.
     */
    private static final int SOURCE_SIZE = 16;

    /*
     * Leave a 3px border around it inside the standard 20x20 button.
     */
    private static final int DRAW_SIZE = 14;

    public GuiGateAppearanceButton(
            int id,
            int x,
            int y
    ) {
        super(
                id,
                x,
                y,
                20,
                20,
                ""
        );
    }

    @Override
    public void drawButton(
            Minecraft minecraft,
            int mouseX,
            int mouseY
    ) {
        if (!visible) {
            return;
        }

        /*
         * Keep the normal Minecraft button background and hover state.
         */
        super.drawButton(
                minecraft,
                mouseX,
                mouseY
        );

        minecraft
                .getTextureManager()
                .bindTexture(
                        ICON_TEXTURE
                );

        GL11.glColor4f(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );

        GL11.glEnable(
                GL11.GL_BLEND
        );

        GL11.glBlendFunc(
                GL11.GL_SRC_ALPHA,
                GL11.GL_ONE_MINUS_SRC_ALPHA
        );

        int iconX =
                xPosition
                        + (width - DRAW_SIZE)
                        / 2;

        int iconY =
                yPosition
                        + (height - DRAW_SIZE)
                        / 2;

        /*
         * Unlike drawTexturedModalRect(), this supports a standalone
         * 16x16 texture instead of assuming a 256x256 texture sheet.
         */
        func_152125_a(
                iconX,
                iconY,
                0,
                0,
                SOURCE_SIZE,
                SOURCE_SIZE,
                DRAW_SIZE,
                DRAW_SIZE,
                SOURCE_SIZE,
                SOURCE_SIZE
        );

        GL11.glColor4f(
                1.0F,
                1.0F,
                1.0F,
                1.0F
        );
    }
}