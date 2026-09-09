package com.enovak.lotrmoremobs.siege.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

/**
 * Small hover-only help control used by the gate creation/edit screens.
 *
 * It deliberately keeps the standard Minecraft button presentation/hover
 * state, but never accepts a mouse press. This makes it read as an unobtrusive
 * help affordance without producing a click sound or implying an action.
 */
public class GuiGateBuildTipWidget extends GuiButton {

    public GuiGateBuildTipWidget(
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
                "?"
        );
    }

    @Override
    public boolean mousePressed(
            Minecraft minecraft,
            int mouseX,
            int mouseY
    ) {
        return false;
    }

    public boolean isMouseOver(
            int mouseX,
            int mouseY
    ) {
        return visible
                && mouseX >= xPosition
                && mouseX < xPosition + width
                && mouseY >= yPosition
                && mouseY < yPosition + height;
    }
}
