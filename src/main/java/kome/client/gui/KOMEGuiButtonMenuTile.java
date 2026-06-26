package kome.client.gui;

import lotr.client.gui.LOTRGuiButtonMenu;
import lotr.client.gui.LOTRGuiMenu;
import lotr.client.gui.LOTRGuiMenuBase;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class KOMEGuiButtonMenuTile extends LOTRGuiButtonMenu {
    private final ResourceLocation tileTexture;

    public KOMEGuiButtonMenuTile(LOTRGuiMenu gui, int id, int x, int y, Class<? extends LOTRGuiMenuBase> screenClass, String label, String imageName) {
        super(gui, id, x, y, screenClass, label, -1);
        tileTexture = new ResourceLocation("kome", "textures/gui/menu/" + imageName);
    }

    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        field_146123_n = KOMEGuiTheme.isHovered(mouseX, mouseY, xPosition, yPosition, width, height);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, enabled ? 1.0F : 0.45F);
        mc.getTextureManager().bindTexture(tileTexture);
        drawFullTexture(xPosition, yPosition, width, height);
        if (field_146123_n && enabled) {
            Gui.drawRect(xPosition, yPosition, xPosition + width, yPosition + 1, KOMEGuiTheme.COLOR_GOLD);
            Gui.drawRect(xPosition, yPosition + height - 1, xPosition + width, yPosition + height, KOMEGuiTheme.COLOR_GOLD);
            Gui.drawRect(xPosition, yPosition, xPosition + 1, yPosition + height, KOMEGuiTheme.COLOR_GOLD);
            Gui.drawRect(xPosition + width - 1, yPosition, xPosition + width, yPosition + height, KOMEGuiTheme.COLOR_GOLD);
        }
        if (!enabled) {
            Gui.drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 0x99000000);
        }
        mouseDragged(mc, mouseX, mouseY);
        GL11.glPopAttrib();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void drawFullTexture(int x, int y, int width, int height) {
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, zLevel, 0.0D, 1.0D);
        tessellator.addVertexWithUV(x + width, y + height, zLevel, 1.0D, 1.0D);
        tessellator.addVertexWithUV(x + width, y, zLevel, 1.0D, 0.0D);
        tessellator.addVertexWithUV(x, y, zLevel, 0.0D, 0.0D);
        tessellator.draw();
    }
}
