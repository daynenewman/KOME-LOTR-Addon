package com.enovak.lotrmoremobs.siege.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

public class GuiGateFactionArrowButton
        extends GuiButton {

    private final boolean leftArrow;

    public GuiGateFactionArrowButton(
            int id,
            int x,
            int y,
            boolean leftArrow
    ) {
        super(
                id,
                x,
                y,
                20,
                20,
                ""
        );

        this.leftArrow =
                leftArrow;
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

        super.drawButton(
                minecraft,
                mouseX,
                mouseY
        );

        boolean hovered =
                mouseX >= xPosition
                        && mouseY >= yPosition
                        && mouseX < xPosition + width
                        && mouseY < yPosition + height;

        int color;

        if (!enabled) {
            color =
                    0x777777;

        } else if (hovered) {
            color =
                    0xFFFFFF;

        } else {
            color =
                    0xE0E0E0;
        }

        String arrow =
                leftArrow
                        ? "<"
                        : ">";

        drawCenteredString(
                minecraft.fontRenderer,
                arrow,
                xPosition + width / 2,
                yPosition + 6,
                color
        );
    }
}