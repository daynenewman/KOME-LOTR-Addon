package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEConquestTile;
import kome.common.network.KOMEPacketConquestOpenCapture;
import kome.common.network.KOMEPacketHandler;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class KOMEConquestMapOverlay {
    private static final ResourceLocation TILE_ID_MASK = new ResourceLocation("kome:map/reset_conquest_tile_ids.png");
    private static final ResourceLocation TILE_ID_MAP = new ResourceLocation("kome:map/reset_conquest_tile_ids.txt");
    private static final ResourceLocation BORDER_GUIDE = new ResourceLocation("kome:map/reset_conquest_borders_thin.png");
    private static final ResourceLocation LABELS = new ResourceLocation("kome:map/reset_conquest_labels.png");
    private static final int UNCLAIMED_HIGHLIGHT_COLOR = 0xFFFFFF;
    private static final int HIGHLIGHT_FILL_ALPHA = 0x44;
    private static final int HIGHLIGHT_EDGE_ALPHA = 0xF0;
    private static final int CLAIM_FILL_ALPHA = 0x88;
    private static final int CLAIM_EDGE_ALPHA = 0xCC;
    private static final Map<String, Integer> CONQUEST_FACTION_COLORS = createFactionColors();
    private static BufferedImage tileMaskImage;
    private static int[] tileMaskPixels;
    private static int highlightedTileColor;
    private static int highlightedFactionColor;
    private static int renderedClaimRevision = -1;
    private static DynamicTexture highlightTexture;
    private static DynamicTexture claimedTexture;
    private static ResourceLocation highlightTextureLocation;
    private static ResourceLocation claimedTextureLocation;
    private static boolean showConquestTiles = true;
    private static final Map<Integer, String> tileIdsByColor = new HashMap<>();
    private static final Map<String, Integer> tileColorsById = new HashMap<>();
    private static final Map<String, Field> lotrMapFields = new HashMap<>();
    private boolean wasRightMouseDown;
    private boolean wasLeftMouseDown;

    @SubscribeEvent
    public void onDrawMap(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof LOTRGuiMap)) {
            return;
        }
        LOTRGuiMap map = (LOTRGuiMap) event.gui;
        if (shouldSkipMap(map)) {
            return;
        }

        drawToggleButton(map, event.mouseX, event.mouseY);
        if (!showConquestTiles) {
            return;
        }

        int tileColor = getHoveredTileColor(map, event.mouseX, event.mouseY);
        drawClaimedTexture(map);
        if (tileColor != 0) {
            drawHighlightTexture(map, tileColor, getClaimedFactionColor(tileColor));
        }
        drawMapTexture(map, BORDER_GUIDE, 1.0f);
        drawMapTexture(map, LABELS, 1.0f);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        GuiScreen screen = KOMEMinecraftClient.currentScreen();
        boolean leftMouseDown = Mouse.isButtonDown(0);
        if (leftMouseDown && !wasLeftMouseDown && screen instanceof LOTRGuiMap) {
            LOTRGuiMap map = (LOTRGuiMap) screen;
            if (!shouldSkipMap(map)) {
                int screenWidth = KOMEMinecraftClient.screenWidth(screen);
                int screenHeight = KOMEMinecraftClient.screenHeight(screen);
                int mouseX = Mouse.getX() * screenWidth / KOMEMinecraftClient.displayWidth();
                int mouseY = screenHeight - Mouse.getY() * screenHeight / KOMEMinecraftClient.displayHeight() - 1;
                if (isOverToggleButton(map, mouseX, mouseY)) {
                    showConquestTiles = !showConquestTiles;
                    clearHighlightTexture();
                }
            }
        }
        wasLeftMouseDown = leftMouseDown;

        boolean rightMouseDown = Mouse.isButtonDown(1);
        if (!showConquestTiles || !rightMouseDown || wasRightMouseDown || !(screen instanceof LOTRGuiMap)) {
            wasRightMouseDown = rightMouseDown;
            return;
        }
        LOTRGuiMap map = (LOTRGuiMap) screen;
        if (shouldSkipMap(map)) {
            wasRightMouseDown = rightMouseDown;
            return;
        }
        int screenWidth = KOMEMinecraftClient.screenWidth(screen);
        int screenHeight = KOMEMinecraftClient.screenHeight(screen);
        int mouseX = Mouse.getX() * screenWidth / KOMEMinecraftClient.displayWidth();
        int mouseY = screenHeight - Mouse.getY() * screenHeight / KOMEMinecraftClient.displayHeight() - 1;
        int tileColor = getHoveredTileColor(map, mouseX, mouseY);
        if (tileColor == 0) {
            wasRightMouseDown = rightMouseDown;
            return;
        }
        String tileId = tileIdsByColor.get(tileColor);
        if (tileId != null) {
            KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestOpenCapture(tileId));
        }
        wasRightMouseDown = rightMouseDown;
    }

    private static int getHoveredTileColor(LOTRGuiMap map, int mouseX, int mouseY) {
        int mapXMin = mapInt("mapXMin");
        int mapXMax = mapInt("mapXMax");
        int mapYMin = mapInt("mapYMin");
        int mapYMax = mapInt("mapYMax");
        int mapWidth = mapInt("mapWidth");
        int mapHeight = mapInt("mapHeight");
        double zoomScale = mapNumber(map, "zoomScale");
        if (mouseX < mapXMin || mouseX >= mapXMax || mouseY < mapYMin || mouseY >= mapYMax) {
            return 0;
        }
        if (!ensureTileMaskLoaded()) {
            return 0;
        }
        int mapX = (int) Math.floor(mapNumber(map, "posX") + (mouseX - mapXMin - mapWidth / 2.0) / zoomScale);
        int mapY = (int) Math.floor(mapNumber(map, "posY") + (mouseY - mapYMin - mapHeight / 2.0) / zoomScale);
        int imageX = mapX * tileMaskImage.getWidth() / LOTRGenLayerWorld.imageWidth;
        int imageY = mapY * tileMaskImage.getHeight() / LOTRGenLayerWorld.imageHeight;
        if (imageX < 0 || imageY < 0 || imageX >= tileMaskImage.getWidth() || imageY >= tileMaskImage.getHeight()) {
            return 0;
        }
        int color = tileMaskPixels[imageY * tileMaskImage.getWidth() + imageX];
        return isClaimableColor(color) ? (color & 0xFFFFFF) : 0;
    }

    private static boolean ensureTileMaskLoaded() {
        if (tileMaskImage != null) {
            return true;
        }
        try {
            InputStream input = KOMEMinecraftClient.resourceManager().getResource(TILE_ID_MASK).getInputStream();
            tileMaskImage = ImageIO.read(input);
            input.close();
            tileMaskPixels = new int[tileMaskImage.getWidth() * tileMaskImage.getHeight()];
            tileMaskImage.getRGB(0, 0, tileMaskImage.getWidth(), tileMaskImage.getHeight(), tileMaskPixels, 0, tileMaskImage.getWidth());
            loadTileIdMap();
            highlightTexture = new DynamicTexture(tileMaskImage.getWidth(), tileMaskImage.getHeight());
            highlightTextureLocation = KOMEMinecraftClient.textureManager().getDynamicTextureLocation("kome_conquest_hover", highlightTexture);
            claimedTexture = new DynamicTexture(tileMaskImage.getWidth(), tileMaskImage.getHeight());
            claimedTextureLocation = KOMEMinecraftClient.textureManager().getDynamicTextureLocation("kome_conquest_claimed", claimedTexture);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void loadTileIdMap() throws Exception {
        tileIdsByColor.clear();
        tileColorsById.clear();
        InputStream input = KOMEMinecraftClient.resourceManager().getResource(TILE_ID_MAP).getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));
        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) {
                continue;
            }
            int equals = line.indexOf('=');
            if (equals <= 0 || equals >= line.length() - 1) {
                continue;
            }
            String[] rgb = line.substring(0, equals).split(",");
            if (rgb.length != 3) {
                continue;
            }
            int red = Integer.parseInt(rgb[0].trim());
            int green = Integer.parseInt(rgb[1].trim());
            int blue = Integer.parseInt(rgb[2].trim());
            int color = red << 16 | green << 8 | blue;
            String tileId = line.substring(equals + 1).trim();
            tileIdsByColor.put(color, tileId);
            tileColorsById.put(tileId, color);
        }
        reader.close();
    }

    private static boolean isClaimableColor(int argb) {
        return (argb >>> 24) > 24 && tileIdsByColor.containsKey(argb & 0xFFFFFF);
    }

    private static void drawToggleButton(LOTRGuiMap map, int mouseX, int mouseY) {
        int x = toggleButtonX();
        int y = toggleButtonY();
        boolean hover = isOverToggleButton(map, mouseX, mouseY);
        int fill = showConquestTiles ? 0xDD2E5D27 : 0xDD4F2A2A;
        Gui.drawRect(x, y, x + 10, y + 10, 0xFF1B1208);
        Gui.drawRect(x + 1, y + 1, x + 9, y + 9, hover ? 0xFFE8C46A : fill);
        KOMEMinecraftClient.fontRenderer().drawString("C", x + 2, y + 1, hover ? 0xFF1B1208 : 0xFFFFFFFF);
        if (hover) {
            String label = showConquestTiles ? "Hide conquest tiles" : "Show conquest tiles";
            KOMEMinecraftClient.fontRenderer().drawStringWithShadow(label, x - KOMEMinecraftClient.fontRenderer().getStringWidth(label) - 4, y + 1, 0xFFFFFF);
        }
    }

    private static boolean isOverToggleButton(LOTRGuiMap map, int mouseX, int mouseY) {
        int x = toggleButtonX();
        int y = toggleButtonY();
        return mouseX >= x && mouseX < x + 10 && mouseY >= y && mouseY < y + 10;
    }

    private static int toggleButtonX() {
        return mapInt("mapXMax") - 86;
    }

    private static int toggleButtonY() {
        return mapInt("mapYMin") + 6;
    }

    private static void drawClaimedTexture(LOTRGuiMap map) {
        if (!ensureTileMaskLoaded()) {
            return;
        }
        if (renderedClaimRevision != KOMEClientData.INSTANCE.conquestRevision) {
            rebuildClaimedTexture();
        }
        drawMapTexture(map, claimedTextureLocation, 1.0f);
    }

    private static void rebuildClaimedTexture() {
        int[] textureData = claimedTexture.getTextureData();
        Arrays.fill(textureData, 0);
        int width = tileMaskImage.getWidth();
        int height = tileMaskImage.getHeight();
        Map<Integer, Integer> claimedColors = new HashMap<>();
        for (Object object : KOMEClientData.INSTANCE.conquestTiles.values()) {
            KOMEConquestTile tile = (KOMEConquestTile) object;
            if (tile == null || !tile.isClaimed()) {
                continue;
            }
            Integer maskColor = tileColorsById.get(KOMEConquestTile.normalizeId(tile.id));
            if (maskColor != null) {
                claimedColors.put(maskColor, factionArgb(tile.ownerFaction));
            }
        }
        for (int i = 0; i < tileMaskPixels.length; i++) {
            int maskColor = tileMaskPixels[i] & 0xFFFFFF;
            Integer factionColor = claimedColors.get(maskColor);
            if (factionColor != null && (tileMaskPixels[i] >>> 24) > 24) {
                int alpha = isTileEdge(i, maskColor, width, height) ? CLAIM_EDGE_ALPHA : CLAIM_FILL_ALPHA;
                textureData[i] = alpha << 24 | factionColor.intValue();
            }
        }
        claimedTexture.updateDynamicTexture();
        renderedClaimRevision = KOMEClientData.INSTANCE.conquestRevision;
    }

    private static int factionArgb(String factionName) {
        LOTRFaction faction = LOTRFaction.forName(factionName);
        if (faction != null) {
            Integer conquestColor = CONQUEST_FACTION_COLORS.get(faction.codeName());
            if (conquestColor != null) {
                return conquestColor.intValue();
            }
        }
        int hash = factionName == null ? 0 : factionName.toLowerCase().hashCode();
        int red = 80 + Math.abs(hash & 0x7F);
        int green = 80 + Math.abs(hash >> 8 & 0x7F);
        int blue = 80 + Math.abs(hash >> 16 & 0x7F);
        return red << 16 | green << 8 | blue;
    }

    private static Map<String, Integer> createFactionColors() {
        Map<String, Integer> colors = new HashMap<>();
        colors.put("HOBBIT", 0xA8E04F);
        colors.put("BREE", 0xC97932);
        colors.put("RANGER_NORTH", 0x1F5A2E);
        colors.put("BLUE_MOUNTAINS", 0x2F6BFF);
        colors.put("HIGH_ELF", 0x7CE7FF);
        colors.put("GUNDABAD", 0x8B4513);
        colors.put("ANGMAR", 0x8A5CFF);
        colors.put("WOOD_ELF", 0x009E3D);
        colors.put("DOL_GULDUR", 0x8C1AFF);
        colors.put("DALE", 0xFF8C1A);
        colors.put("DURINS_FOLK", 0x0057D9);
        colors.put("LOTHLORIEN", 0xFFD400);
        colors.put("DUNLAND", 0xA0522D);
        colors.put("ISENGARD", 0x5F6878);
        colors.put("FANGORN", 0x1E7F00);
        colors.put("ROHAN", 0x8CD600);
        colors.put("GONDOR", 0xD9E8FF);
        colors.put("MORDOR", 0x050505);
        colors.put("DORWINION", 0xD63AD6);
        colors.put("RHUDEL", 0xE6A800);
        colors.put("NEAR_HARAD", 0xFF3B30);
        colors.put("MORWAITH", 0xFFB347);
        colors.put("TAURETHRIM", 0x007A5E);
        colors.put("HALF_TROLL", 0x9E6B43);
        colors.put("UTUMNO", 0x5A0000);
        return colors;
    }

    private static void drawHighlightTexture(LOTRGuiMap map, int tileColor, int factionColor) {
        if (tileColor != highlightedTileColor || factionColor != highlightedFactionColor) {
            int[] textureData = highlightTexture.getTextureData();
            Arrays.fill(textureData, 0);
            int width = tileMaskImage.getWidth();
            int height = tileMaskImage.getHeight();
            int fillColor = HIGHLIGHT_FILL_ALPHA << 24 | factionColor;
            int edgeColor = HIGHLIGHT_EDGE_ALPHA << 24 | factionColor;
            for (int i = 0; i < tileMaskPixels.length; i++) {
                if ((tileMaskPixels[i] & 0xFFFFFF) == tileColor && (tileMaskPixels[i] >>> 24) > 24) {
                    textureData[i] = isTileEdge(i, tileColor, width, height) ? edgeColor : fillColor;
                }
            }
            highlightTexture.updateDynamicTexture();
            highlightedTileColor = tileColor;
            highlightedFactionColor = factionColor;
        }

        drawMapTexture(map, highlightTextureLocation, 1.0f);
    }

    private static void clearHighlightTexture() {
        highlightedTileColor = 0;
        highlightedFactionColor = 0;
        if (highlightTexture != null) {
            Arrays.fill(highlightTexture.getTextureData(), 0);
            highlightTexture.updateDynamicTexture();
        }
    }

    private static int getClaimedFactionColor(int tileColor) {
        String tileId = tileIdsByColor.get(tileColor);
        if (tileId == null) {
            return UNCLAIMED_HIGHLIGHT_COLOR;
        }
        KOMEConquestTile tile = (KOMEConquestTile) KOMEClientData.INSTANCE.conquestTiles.get(tileId);
        return tile != null && tile.isClaimed() ? factionArgb(tile.ownerFaction) : UNCLAIMED_HIGHLIGHT_COLOR;
    }

    private static void drawMapTexture(LOTRGuiMap map, ResourceLocation texture, float alpha) {
        int mapWidth = mapInt("mapWidth");
        int mapHeight = mapInt("mapHeight");
        int mapXMin = mapInt("mapXMin");
        int mapXMax = mapInt("mapXMax");
        int mapYMin = mapInt("mapYMin");
        int mapYMax = mapInt("mapYMax");
        double zoomScale = mapNumber(map, "zoomScale");
        double posX = mapNumber(map, "posX");
        double posY = mapNumber(map, "posY");
        double mapScaleX = mapWidth / zoomScale;
        double mapScaleY = mapHeight / zoomScale;
        double minU = (posX - mapScaleX / 2.0f) / LOTRGenLayerWorld.imageWidth;
        double maxU = (posX + mapScaleX / 2.0f) / LOTRGenLayerWorld.imageWidth;
        double minV = (posY - mapScaleY / 2.0f) / LOTRGenLayerWorld.imageHeight;
        double maxV = (posY + mapScaleY / 2.0f) / LOTRGenLayerWorld.imageHeight;

        int x0 = mapXMin;
        int x1 = mapXMax;
        int y0 = mapYMin;
        int y1 = mapYMax;
        if (minU < 0.0) {
            x0 = mapXMin + (int) Math.round((0.0 - minU) * LOTRGenLayerWorld.imageWidth * zoomScale);
            minU = 0.0;
        }
        if (maxU > 1.0) {
            x1 = mapXMax - (int) Math.round((maxU - 1.0) * LOTRGenLayerWorld.imageWidth * zoomScale);
            maxU = 1.0;
        }
        if (minV < 0.0) {
            y0 = mapYMin + (int) Math.round((0.0 - minV) * LOTRGenLayerWorld.imageHeight * zoomScale);
            minV = 0.0;
        }
        if (maxV > 1.0) {
            y1 = mapYMax - (int) Math.round((maxV - 1.0) * LOTRGenLayerWorld.imageHeight * zoomScale);
            maxV = 1.0;
        }
        if (x1 <= x0 || y1 <= y0) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha);
        KOMEMinecraftClient.textureManager().bindTexture(texture);
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.addVertexWithUV(x0, y1, 0.0, minU, maxV);
        tess.addVertexWithUV(x1, y1, 0.0, maxU, maxV);
        tess.addVertexWithUV(x1, y0, 0.0, maxU, minV);
        tess.addVertexWithUV(x0, y0, 0.0, minU, minV);
        tess.draw();
        GL11.glPopAttrib();
    }

    private static boolean shouldSkipMap(LOTRGuiMap map) {
        return mapBoolean(map, "isConquestGrid") || mapBoolean(map, "hasOverlay") || mapNumber(map, "zoomScale") <= 0.0D;
    }

    private static boolean mapBoolean(LOTRGuiMap map, String name) {
        return ((Boolean) mapFieldValue(map, name)).booleanValue();
    }

    private static double mapNumber(LOTRGuiMap map, String name) {
        return ((Number) mapFieldValue(map, name)).doubleValue();
    }

    private static int mapInt(String name) {
        return ((Number) mapFieldValue(null, name)).intValue();
    }

    private static Object mapFieldValue(LOTRGuiMap map, String name) {
        try {
            Field field = lotrMapFields.get(name);
            if (field == null) {
                field = LOTRGuiMap.class.getDeclaredField(name);
                field.setAccessible(true);
                lotrMapFields.put(name, field);
            }
            return field.get(map);
        } catch (Exception e) {
            throw new RuntimeException("Could not read LOTR map field " + name, e);
        }
    }

    private static boolean isTileEdge(int index, int tileColor, int width, int height) {
        int x = index % width;
        int y = index / width;
        return x == 0 || x == width - 1 || y == 0 || y == height - 1
                || (tileMaskPixels[index - 1] & 0xFFFFFF) != tileColor
                || (tileMaskPixels[index + 1] & 0xFFFFFF) != tileColor
                || (tileMaskPixels[index - width] & 0xFFFFFF) != tileColor
                || (tileMaskPixels[index + width] & 0xFFFFFF) != tileColor;
    }
}
