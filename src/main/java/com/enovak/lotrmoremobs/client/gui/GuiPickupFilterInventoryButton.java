package com.enovak.lotrmoremobs.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Ten-pixel inventory entry button, using the same three-state beveled style
 * as LOTR's restock-pouch button with a compact hopper glyph.
 */
public class GuiPickupFilterInventoryButton extends GuiButton {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(
                    "lotrmoremobs",
                    "textures/gui/pickup_filter.png"
            );

    private static final int TEXTURE_U = 218;
    private static final int TEXTURE_V = 0;
    private static final int BUTTON_SIZE = 10;

    public GuiPickupFilterInventoryButton(
            int id,
            int x,
            int y
    ) {
        super(id, x, y, BUTTON_SIZE, BUTTON_SIZE, "");
    }

    @Override
    public void drawButton(
            Minecraft mc,
            int mouseX,
            int mouseY
    ) {
        if (!visible) {
            return;
        }

        mc.getTextureManager().bindTexture(TEXTURE);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        field_146123_n = mouseX >= xPosition
                && mouseY >= yPosition
                && mouseX < xPosition + width
                && mouseY < yPosition + height;

        int hoverState = getHoverState(field_146123_n);
        drawTexturedModalRect(
                xPosition,
                yPosition,
                TEXTURE_U,
                TEXTURE_V + hoverState * BUTTON_SIZE,
                BUTTON_SIZE,
                BUTTON_SIZE
        );
        mouseDragged(mc, mouseX, mouseY);
    }
}
