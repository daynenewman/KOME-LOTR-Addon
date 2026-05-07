package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Arrays;

public class KOMEConquestMapOverlay {
    private static final ResourceLocation HITMAP = new ResourceLocation("kome:map/reset_conquest_hitmap.png");
    private static final ResourceLocation BORDER_GUIDE = new ResourceLocation("kome:map/reset_conquest_borders_thin.png");
    private static final ResourceLocation LABELS = new ResourceLocation("kome:map/reset_conquest_labels.png");
    private static final int HIGHLIGHT_FILL_COLOR = 0x22FFF060;
    private static final int HIGHLIGHT_EDGE_COLOR = 0xEEFFE04A;
    private static BufferedImage hitmapImage;
    private static int[] hitmapPixels;
    private static int[] componentIds;
    private static int nextComponentId = 1;
    private static int highlightedComponentId;
    private static DynamicTexture highlightTexture;
    private static ResourceLocation highlightTextureLocation;

    @SubscribeEvent
    public void onDrawMap(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof LOTRGuiMap)) {
            return;
        }
        LOTRGuiMap map = (LOTRGuiMap) event.gui;
        if (map.isConquestGrid || map.hasOverlay || map.zoomScale <= 0.0f) {
            return;
        }

        int componentId = getHoveredComponentId(map, event.mouseX, event.mouseY);
        if (componentId > 0) {
            drawHighlightTexture(map, componentId);
        }
        drawMapTexture(map, BORDER_GUIDE, 1.0f);
        drawMapTexture(map, LABELS, 1.0f);
    }

    private static int getHoveredComponentId(LOTRGuiMap map, int mouseX, int mouseY) {
        if (mouseX < LOTRGuiMap.mapXMin || mouseX >= LOTRGuiMap.mapXMax || mouseY < LOTRGuiMap.mapYMin || mouseY >= LOTRGuiMap.mapYMax) {
            return 0;
        }
        if (!ensureHitmapLoaded()) {
            return 0;
        }
        int mapX = (int) Math.floor(map.posX + (mouseX - LOTRGuiMap.mapXMin - LOTRGuiMap.mapWidth / 2.0) / map.zoomScale);
        int mapY = (int) Math.floor(map.posY + (mouseY - LOTRGuiMap.mapYMin - LOTRGuiMap.mapHeight / 2.0) / map.zoomScale);
        if (mapX < 0 || mapY < 0 || mapX >= hitmapImage.getWidth() || mapY >= hitmapImage.getHeight()) {
            return 0;
        }
        int index = mapY * hitmapImage.getWidth() + mapX;
        if (!isClaimablePixel(hitmapPixels[index])) {
            return 0;
        }
        if (componentIds[index] == 0) {
            fillComponent(mapX, mapY, nextComponentId++);
        }
        return componentIds[index];
    }

    private static boolean ensureHitmapLoaded() {
        if (hitmapImage != null) {
            return true;
        }
        try {
            Minecraft mc = Minecraft.getMinecraft();
            InputStream input = mc.getResourceManager().getResource(HITMAP).getInputStream();
            hitmapImage = ImageIO.read(input);
            input.close();
            hitmapPixels = new int[hitmapImage.getWidth() * hitmapImage.getHeight()];
            hitmapImage.getRGB(0, 0, hitmapImage.getWidth(), hitmapImage.getHeight(), hitmapPixels, 0, hitmapImage.getWidth());
            componentIds = new int[hitmapPixels.length];
            highlightTexture = new DynamicTexture(hitmapImage.getWidth(), hitmapImage.getHeight());
            highlightTextureLocation = mc.getTextureManager().getDynamicTextureLocation("kome_conquest_hover", highlightTexture);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void fillComponent(int startX, int startY, int componentId) {
        int width = hitmapImage.getWidth();
        int height = hitmapImage.getHeight();
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(startY * width + startX);
        while (!queue.isEmpty()) {
            int index = queue.removeFirst();
            if (componentIds[index] != 0 || !isClaimablePixel(hitmapPixels[index])) {
                continue;
            }
            int x = index % width;
            int y = index / width;
            componentIds[index] = componentId;
            if (x > 0) {
                queue.add(index - 1);
            }
            if (x < width - 1) {
                queue.add(index + 1);
            }
            if (y > 0) {
                queue.add(index - width);
            }
            if (y < height - 1) {
                queue.add(index + width);
            }
        }
    }

    private static boolean isClaimablePixel(int argb) {
        int alpha = argb >>> 24;
        int red = argb >> 16 & 255;
        int green = argb >> 8 & 255;
        int blue = argb & 255;
        return alpha > 24 && !(red < 70 && green < 70 && blue < 70);
    }

    private static void drawHighlightTexture(LOTRGuiMap map, int componentId) {
        if (componentId != highlightedComponentId) {
            int[] textureData = highlightTexture.getTextureData();
            Arrays.fill(textureData, 0);
            int width = hitmapImage.getWidth();
            int height = hitmapImage.getHeight();
            for (int i = 0; i < componentIds.length; i++) {
                if (componentIds[i] == componentId) {
                    textureData[i] = isComponentEdge(i, componentId, width, height) ? HIGHLIGHT_EDGE_COLOR : HIGHLIGHT_FILL_COLOR;
                }
            }
            highlightTexture.updateDynamicTexture();
            highlightedComponentId = componentId;
        }

        drawMapTexture(map, highlightTextureLocation, 1.0f);
    }

    private static void drawMapTexture(LOTRGuiMap map, ResourceLocation texture, float alpha) {
        Minecraft mc = Minecraft.getMinecraft();
        float mapScaleX = LOTRGuiMap.mapWidth / map.zoomScale;
        float mapScaleY = LOTRGuiMap.mapHeight / map.zoomScale;
        double minU = (double) (map.posX - mapScaleX / 2.0f) / LOTRGenLayerWorld.imageWidth;
        double maxU = (double) (map.posX + mapScaleX / 2.0f) / LOTRGenLayerWorld.imageWidth;
        double minV = (double) (map.posY - mapScaleY / 2.0f) / LOTRGenLayerWorld.imageHeight;
        double maxV = (double) (map.posY + mapScaleY / 2.0f) / LOTRGenLayerWorld.imageHeight;

        int x0 = LOTRGuiMap.mapXMin;
        int x1 = LOTRGuiMap.mapXMax;
        int y0 = LOTRGuiMap.mapYMin;
        int y1 = LOTRGuiMap.mapYMax;
        if (minU < 0.0) {
            x0 = LOTRGuiMap.mapXMin + (int) Math.round((0.0 - minU) * LOTRGenLayerWorld.imageWidth * map.zoomScale);
            minU = 0.0;
        }
        if (maxU > 1.0) {
            x1 = LOTRGuiMap.mapXMax - (int) Math.round((maxU - 1.0) * LOTRGenLayerWorld.imageWidth * map.zoomScale);
            maxU = 1.0;
        }
        if (minV < 0.0) {
            y0 = LOTRGuiMap.mapYMin + (int) Math.round((0.0 - minV) * LOTRGenLayerWorld.imageHeight * map.zoomScale);
            minV = 0.0;
        }
        if (maxV > 1.0) {
            y1 = LOTRGuiMap.mapYMax - (int) Math.round((maxV - 1.0) * LOTRGenLayerWorld.imageHeight * map.zoomScale);
            maxV = 1.0;
        }
        if (x1 <= x0 || y1 <= y0) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha);
        mc.getTextureManager().bindTexture(texture);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x0, y1, 0.0, minU, maxV);
        tess.addVertexWithUV(x1, y1, 0.0, maxU, maxV);
        tess.addVertexWithUV(x1, y0, 0.0, maxU, minV);
        tess.addVertexWithUV(x0, y0, 0.0, minU, minV);
        tess.draw();
        GL11.glPopAttrib();
    }

    private static boolean isComponentEdge(int index, int componentId, int width, int height) {
        int x = index % width;
        int y = index / width;
        return x == 0 || x == width - 1 || y == 0 || y == height - 1
                || componentIds[index - 1] != componentId
                || componentIds[index + 1] != componentId
                || componentIds[index - width] != componentId
                || componentIds[index + width] != componentId;
    }
}
