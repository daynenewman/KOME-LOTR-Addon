package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEConquestRouteEdge;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEConquestTileDefaults;
import kome.common.data.KOMETileWaypointLink;
import kome.common.data.KOMETileTroopSummary;
import kome.client.gui.KOMEGuiTheme;
import kome.common.network.KOMEPacketCompanyMoveConfirmGui;
import kome.common.network.KOMEPacketCompanyMovePreviewResult;
import kome.common.network.KOMEPacketConquestOpenCapture;
import kome.common.network.KOMEPacketHandler;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KOMEConquestMapOverlay {
    private static final ResourceLocation TILE_ID_MASK = new ResourceLocation("kome:map/reset_conquest_tile_ids.png");
    private static final ResourceLocation TILE_ID_MAP = new ResourceLocation("kome:map/reset_conquest_tile_ids.txt");
    private static final ResourceLocation BORDER_GUIDE = new ResourceLocation("kome:map/reset_conquest_borders_thin.png");
    private static final ResourceLocation LABELS = new ResourceLocation("kome:map/reset_conquest_labels.png");
    private static final ResourceLocation TROOP_MARKER_ART = new ResourceLocation("kome:textures/gui/troopsicon.png");
    private static final ResourceLocation BRIDGE_MARKER_ART = new ResourceLocation("kome:textures/gui/bridge.png");
    private static final int HIGHLIGHT_FILL_COLOR = 0x22FFF060;
    private static final int HIGHLIGHT_EDGE_COLOR = 0xEEFFE04A;
    private static final int CLAIM_FILL_ALPHA = 0x88;
    private static final int CLAIM_EDGE_ALPHA = 0xCC;
    private static final int TROOP_MARKER_WIDTH = 9;
    private static final int TROOP_MARKER_HEIGHT = 15;
    private static final int BRIDGE_MARKER_WIDTH = 8;
    private static final int BRIDGE_MARKER_HEIGHT = 5;
    private static final float TROOP_COUNT_TEXT_SCALE = 0.70F;
    private static final int ROUTE_LINE_COLOR = 0xE0D6B45A;
    private static final int ROUTE_LINE_SHADOW = 0xCC3A2A12;
    private static final int ROUTE_COMPLETED_COLOR = 0xAA8C7440;
    private static final int ROUTE_REMAINING_COLOR = 0xE8E8C46A;
    private static final int ROUTE_CURRENT_COLOR = 0xFF6FCBFF;
    private static final int ROUTE_GOLD = 0xFFE8C46A;
    private static final int ROUTE_DARK = 0xFF3A2A12;
    private static final int ROUTE_DESTINATION = 0xFF7A1F25;
    private static final boolean SHOW_AUTOMATIC_BRIDGE_DEBUG = true;
    private static final Map<String, Integer> CONQUEST_FACTION_COLORS = createFactionColors();
    private static BufferedImage tileMaskImage;
    private static int[] tileMaskPixels;
    private static int highlightedTileColor;
    private static int renderedClaimRevision = -1;
    private static DynamicTexture highlightTexture;
    private static DynamicTexture claimedTexture;
    private static DynamicTexture borderGuideTexture;
    private static DynamicTexture labelTexture;
    private static DynamicTexture troopMarkerTexture;
    private static DynamicTexture bridgeMarkerTexture;
    private static ResourceLocation highlightTextureLocation;
    private static ResourceLocation claimedTextureLocation;
    private static ResourceLocation borderGuideTextureLocation;
    private static ResourceLocation labelTextureLocation;
    private static ResourceLocation troopMarkerTextureLocation;
    private static ResourceLocation bridgeMarkerTextureLocation;
    private static boolean showConquestTiles = true;
    private static final Map<Integer, String> tileIdsByColor = new HashMap<>();
    private static final Map<String, Integer> tileColorsById = new HashMap<>();
    private static final Map<String, int[]> tileCentersById = new HashMap<>();
    private static final Map<String, Field> lotrMapFields = new HashMap<>();
    private static String destinationCompanyId = "";
    private static String destinationCompanyName = "";
    private static String destinationOriginTile = "";
    private static boolean waitForDestinationClickRelease;
    private static KOMEPacketCompanyMoveConfirmGui routePreviewMove;
    private static KOMEPacketCompanyMovePreviewResult routePreviewError;
    private static final List<String> routePreviewTiles = new ArrayList<String>();
    private static LOTRGuiMap activeMapInstance;
    private static boolean hasSavedMapViewport;
    private static float savedMapPosX;
    private static float savedMapPosY;
    private static float savedMapZoomScale;
    private static float savedMapZoomScaleStable;
    private static float savedMapZoomExp;
    private boolean wasRightMouseDown;
    private boolean wasLeftMouseDown;
    private boolean wasEscapeDown;

    public static void beginDestinationSelection(String companyId, String companyName, String originTile) {
        destinationCompanyId = companyId == null ? "" : companyId;
        destinationCompanyName = companyName == null ? "Company" : companyName;
        destinationOriginTile = KOMEConquestTile.normalizeId(originTile);
        waitForDestinationClickRelease = true;
        clearRoutePreviewError();
        showConquestTiles = true;
    }

    public static boolean isChoosingDestination() {
        return destinationCompanyId.length() > 0;
    }

    private static void clearDestinationSelection() {
        destinationCompanyId = "";
        destinationCompanyName = "";
        destinationOriginTile = "";
        waitForDestinationClickRelease = false;
    }

    public static void beginRoutePreview(KOMEPacketCompanyMoveConfirmGui move) {
        clearDestinationSelection();
        clearRoutePreviewError();
        routePreviewMove = move;
        routePreviewTiles.clear();
        if (move != null) {
            for (String tile : move.routeTiles) {
                String normalized = KOMEConquestTile.normalizeId(tile);
                if (normalized.length() > 0) {
                    routePreviewTiles.add(normalized);
                }
            }
        }
        showConquestTiles = true;
    }

    private static boolean isPreviewingRoute() {
        return routePreviewMove != null && !routePreviewTiles.isEmpty();
    }

    private static void clearRoutePreview() {
        routePreviewMove = null;
        routePreviewTiles.clear();
    }

    private static void clearRoutePreviewError() {
        routePreviewError = null;
    }

    public static void showCompanyMovePreviewResult(KOMEPacketCompanyMovePreviewResult message) {
        if (message == null) {
            return;
        }
        if (message.valid) {
            clearRoutePreviewError();
            return;
        }
        clearRoutePreview();
        destinationCompanyId = message.companyId == null ? "" : message.companyId;
        destinationCompanyName = message.companyName == null || message.companyName.length() == 0 ? "Company" : message.companyName;
        destinationOriginTile = KOMEConquestTile.normalizeId(message.originTileId);
        waitForDestinationClickRelease = false;
        routePreviewError = message;
        showConquestTiles = true;
    }

    public static void resetDestinationSelection() {
        clearDestinationSelection();
        clearRoutePreview();
        clearRoutePreviewError();
    }

    public static void resetClientMapState() {
        resetDestinationSelection();
        activeMapInstance = null;
        hasSavedMapViewport = false;
    }

    public static void openPreservedMap() {
        GuiScreen current = KOMEMinecraftClient.currentScreen();
        if (current instanceof LOTRGuiMap) {
            restoreMapViewport((LOTRGuiMap) current);
            return;
        }
        LOTRGuiMap map = new LOTRGuiMap();
        KOMEMinecraftClient.displayGui(map);
        restoreMapViewport(map);
        activeMapInstance = map;
    }

    @SubscribeEvent
    public void onDrawMap(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof LOTRGuiMap)) {
            return;
        }
        LOTRGuiMap map = (LOTRGuiMap) event.gui;
        updateMapViewportState(map);
        if (shouldSkipMap(map)) {
            return;
        }

        drawToggleButton(map, event.mouseX, event.mouseY);
        if (isChoosingDestination()) {
            drawDestinationInstruction(map);
        }
        if (!showConquestTiles) {
            return;
        }

        int tileColor = getHoveredTileColor(map, event.mouseX, event.mouseY);
        drawClaimedTexture(map);
        if (tileColor != 0) {
            drawHighlightTexture(map, tileColor);
        }
        drawMapTexture(map, getBorderGuideTextureLocation(), 1.0f);
        drawMapTexture(map, getLabelTextureLocation(), 1.0f);
        drawActiveMovementRoutes(map);
        drawRoutePreview(map);
        List<String> automaticBridgeTooltip = drawAutomaticBridgeDebugMarkers(map, event.mouseX, event.mouseY);
        drawRouteEdgeMarkers(map);
        drawTroopMarkers(map);
        if (tileColor != 0 && automaticBridgeTooltip == null) {
            drawTileTooltip(map, tileColor, event.mouseX, event.mouseY);
        }
        if (automaticBridgeTooltip != null) {
            drawAutomaticBridgeTooltip(map, automaticBridgeTooltip, event.mouseX, event.mouseY);
        }
        drawRoutePreviewPanel(map, event.mouseX, event.mouseY);
        drawRouteErrorPanel(map);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        GuiScreen screen = KOMEMinecraftClient.currentScreen();
        updateMapViewportState(screen);
        if (screen == null) {
            if (isChoosingDestination()) {
                clearDestinationSelection();
            }
            if (isPreviewingRoute()) {
                clearRoutePreview();
            }
            return;
        }
        boolean escapeDown = Keyboard.isKeyDown(Keyboard.KEY_ESCAPE);
        if (escapeDown && !wasEscapeDown && isChoosingDestination()) {
            clearDestinationSelection();
        }
        wasEscapeDown = escapeDown;

        boolean leftMouseDown = Mouse.isButtonDown(0);
        if (!isChoosingDestination() && waitForDestinationClickRelease) {
            if (!leftMouseDown) {
                waitForDestinationClickRelease = false;
            }
            wasLeftMouseDown = leftMouseDown;
            return;
        }
        if (leftMouseDown && !wasLeftMouseDown && screen instanceof LOTRGuiMap) {
            LOTRGuiMap map = (LOTRGuiMap) screen;
            if (!shouldSkipMap(map)) {
                int screenWidth = KOMEMinecraftClient.screenWidth(screen);
                int screenHeight = KOMEMinecraftClient.screenHeight(screen);
                int mouseX = Mouse.getX() * screenWidth / KOMEMinecraftClient.displayWidth();
                int mouseY = screenHeight - Mouse.getY() * screenHeight / KOMEMinecraftClient.displayHeight() - 1;
                if (isPreviewingRoute() && isOverRouteConfirmButton(map, mouseX, mouseY)) {
                    KOMEMinecraftClient.sendChat("/troops movecompany " + routePreviewMove.companyId + " " + routePreviewMove.destinationTile);
                    clearRoutePreview();
                    KOMEMinecraftClient.closePlayerScreen();
                } else if (isPreviewingRoute() && isOverRouteCancelButton(map, mouseX, mouseY)) {
                    clearRoutePreview();
                } else if (!isChoosingDestination() && isOverToggleButton(map, mouseX, mouseY)) {
                    showConquestTiles = !showConquestTiles;
                    clearHighlightTexture();
                }
            }
        }
        wasLeftMouseDown = leftMouseDown;

        boolean rightMouseDown = Mouse.isButtonDown(1);
        if (isPreviewingRoute() || !showConquestTiles || !rightMouseDown || wasRightMouseDown || !(screen instanceof LOTRGuiMap)) {
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
        if (isChoosingDestination()) {
            if (tileId != null && !destinationOriginTile.equals(KOMEConquestTile.normalizeId(tileId))) {
                clearRoutePreviewError();
                KOMEMinecraftClient.sendChat("/troops previewmove " + destinationCompanyId + " " + tileId);
            }
            wasRightMouseDown = rightMouseDown;
            return;
        }
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

    private static void updateMapViewportState(GuiScreen screen) {
        if (!(screen instanceof LOTRGuiMap)) {
            activeMapInstance = null;
            return;
        }
        LOTRGuiMap map = (LOTRGuiMap) screen;
        if (shouldSkipMap(map)) {
            return;
        }
        if (map != activeMapInstance) {
            restoreMapViewport(map);
            activeMapInstance = map;
        }
        saveMapViewport(map);
    }

    private static void saveMapViewport(LOTRGuiMap map) {
        try {
            float posX = (float) mapNumber(map, "posX");
            float posY = (float) mapNumber(map, "posY");
            float zoomScale = (float) mapNumber(map, "zoomScale");
            if (Float.isNaN(posX) || Float.isNaN(posY) || Float.isNaN(zoomScale) || zoomScale <= 0.0F) {
                return;
            }
            savedMapPosX = posX;
            savedMapPosY = posY;
            savedMapZoomScale = zoomScale;
            savedMapZoomScaleStable = optionalMapFloat(map, "zoomScaleStable", zoomScale);
            savedMapZoomExp = optionalMapFloat(map, "zoomExp", zoomScale);
            hasSavedMapViewport = true;
        } catch (Exception ignored) {
        }
    }

    private static void restoreMapViewport(LOTRGuiMap map) {
        if (!hasSavedMapViewport || map == null) {
            return;
        }
        try {
            setMapFieldValue(map, "posX", savedMapPosX);
            setMapFieldValue(map, "posY", savedMapPosY);
            setMapFieldValue(map, "prevPosX", savedMapPosX);
            setMapFieldValue(map, "prevPosY", savedMapPosY);
            setMapFieldValue(map, "posXMove", 0.0F);
            setMapFieldValue(map, "posYMove", 0.0F);
            setMapFieldValue(map, "zoomScale", savedMapZoomScale);
            setMapFieldValue(map, "zoomScaleStable", savedMapZoomScaleStable);
            setMapFieldValue(map, "zoomExp", savedMapZoomExp);
        } catch (Exception ignored) {
        }
    }

    private static float optionalMapFloat(LOTRGuiMap map, String name, float fallback) {
        try {
            return (float) mapNumber(map, name);
        } catch (Exception ignored) {
            return fallback;
        }
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
        computeTileCenters();
            highlightTexture = new DynamicTexture(tileMaskImage.getWidth(), tileMaskImage.getHeight());
            highlightTextureLocation = KOMEMinecraftClient.textureManager().getDynamicTextureLocation("kome_conquest_hover", highlightTexture);
            claimedTexture = new DynamicTexture(tileMaskImage.getWidth(), tileMaskImage.getHeight());
            claimedTextureLocation = KOMEMinecraftClient.textureManager().getDynamicTextureLocation("kome_conquest_claimed", claimedTexture);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static ResourceLocation getBorderGuideTextureLocation() {
        if (borderGuideTextureLocation == null) {
            borderGuideTextureLocation = loadDynamicTexture("kome_conquest_borders", BORDER_GUIDE);
        }
        return borderGuideTextureLocation;
    }

    private static ResourceLocation getLabelTextureLocation() {
        if (labelTextureLocation == null) {
            labelTextureLocation = loadDynamicTexture("kome_conquest_labels", LABELS);
        }
        return labelTextureLocation;
    }

    private static ResourceLocation getTroopMarkerTextureLocation() {
        if (troopMarkerTextureLocation == null) {
            troopMarkerTextureLocation = loadMarkerTexture("kome_conquest_troop_marker", TROOP_MARKER_ART, true);
        }
        return troopMarkerTextureLocation;
    }

    private static ResourceLocation getBridgeMarkerTextureLocation() {
        if (bridgeMarkerTextureLocation == null) {
            bridgeMarkerTextureLocation = loadMarkerTexture("kome_conquest_bridge_marker", BRIDGE_MARKER_ART, false);
        }
        return bridgeMarkerTextureLocation;
    }

    private static ResourceLocation loadDynamicTexture(String name, ResourceLocation resource) {
        try {
            InputStream input = KOMEMinecraftClient.resourceManager().getResource(resource).getInputStream();
            BufferedImage image = ImageIO.read(input);
            input.close();
            if (image == null) {
                return null;
            }
            DynamicTexture texture = new DynamicTexture(image.getWidth(), image.getHeight());
            image.getRGB(0, 0, image.getWidth(), image.getHeight(), texture.getTextureData(), 0, image.getWidth());
            texture.updateDynamicTexture();
            if (resource == BORDER_GUIDE) {
                borderGuideTexture = texture;
            } else if (resource == LABELS) {
                labelTexture = texture;
            }
            return KOMEMinecraftClient.textureManager().getDynamicTextureLocation(name, texture);
        } catch (Exception e) {
            return null;
        }
    }

    private static ResourceLocation loadMarkerTexture(String name, ResourceLocation resource, boolean troop) {
        try {
            InputStream input = KOMEMinecraftClient.resourceManager().getResource(resource).getInputStream();
            BufferedImage image = ImageIO.read(input);
            input.close();
            if (image == null) {
                return null;
            }
            int[] bounds = markerContentBounds(image);
            if (bounds == null) {
                return null;
            }
            int width = bounds[2] - bounds[0] + 1;
            int height = bounds[3] - bounds[1] + 1;
            DynamicTexture texture = new DynamicTexture(width, height);
            int[] pixels = texture.getTextureData();
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = image.getRGB(bounds[0] + x, bounds[1] + y);
                    pixels[y * width + x] = isMarkerBackground(argb) ? 0 : argb | 0xFF000000;
                }
            }
            texture.updateDynamicTexture();
            if (troop) {
                troopMarkerTexture = texture;
            } else {
                bridgeMarkerTexture = texture;
            }
            return KOMEMinecraftClient.textureManager().getDynamicTextureLocation(name, texture);
        } catch (Exception e) {
            return null;
        }
    }

    private static int[] markerContentBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (!isMarkerBackground(image.getRGB(x, y))) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return null;
        }
        return new int[] {minX, minY, maxX, maxY};
    }

    private static boolean isMarkerBackground(int argb) {
        int alpha = argb >>> 24;
        if (alpha < 16) {
            return true;
        }
        int red = argb >> 16 & 255;
        int green = argb >> 8 & 255;
        int blue = argb & 255;
        int spread = Math.max(Math.max(Math.abs(red - green), Math.abs(red - blue)), Math.abs(green - blue));
        return red >= 222 && green >= 222 && blue >= 222 && spread <= 18;
    }

    private static void loadTileIdMap() throws Exception {
        tileIdsByColor.clear();
        tileColorsById.clear();
        tileCentersById.clear();
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

    private static void computeTileCenters() {
        Map<Integer, long[]> totals = new HashMap<Integer, long[]>();
        int width = tileMaskImage.getWidth();
        int height = tileMaskImage.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = tileMaskPixels[y * width + x];
                if (!isClaimableColor(argb)) {
                    continue;
                }
                int color = argb & 0xFFFFFF;
                long[] total = totals.get(color);
                if (total == null) {
                    total = new long[3];
                    totals.put(color, total);
                }
                total[0] += x;
                total[1] += y;
                total[2]++;
            }
        }
        for (Map.Entry<Integer, long[]> entry : totals.entrySet()) {
            String tile = tileIdsByColor.get(entry.getKey());
            long[] total = entry.getValue();
            if (tile != null && total[2] > 0) {
                tileCentersById.put(KOMEConquestTile.normalizeId(tile), new int[] {(int) (total[0] / total[2]), (int) (total[1] / total[2])});
            }
        }
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

    private static void drawDestinationInstruction(LOTRGuiMap map) {
        FontRenderer font = KOMEMinecraftClient.fontRenderer();
        String line = "Choose destination for " + destinationCompanyName;
        String help = "Right-click a tile to select | Left-drag to pan | Esc to cancel";
        int width = Math.max(font.getStringWidth(line), font.getStringWidth(help)) + 18;
        int x = (mapInt("mapXMin") + mapInt("mapXMax") - width) / 2;
        int y = mapInt("mapYMin") + 18;
        Gui.drawRect(x, y, x + width, y + 31, 0xE05A1118);
        Gui.drawRect(x + 2, y + 2, x + width - 2, y + 15, 0xAA1B1208);
        font.drawString(line, x + 9, y + 6, 0xFFE8C46A);
        font.drawString(help, x + 9, y + 19, 0xFFFFFFFF);
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
                claimedColors.put(maskColor, factionArgb(tile.currentRulingFaction()));
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
        Integer normalizedColor = CONQUEST_FACTION_COLORS.get(KOMEAlliance.normalizeFactionKey(factionName));
        if (normalizedColor != null) {
            return normalizedColor.intValue();
        }
        int hash = factionName == null ? 0 : factionName.toLowerCase().hashCode();
        int red = 80 + Math.abs(hash & 0x7F);
        int green = 80 + Math.abs(hash >> 8 & 0x7F);
        int blue = 80 + Math.abs(hash >> 16 & 0x7F);
        return red << 16 | green << 8 | blue;
    }

    private static Map<String, Integer> createFactionColors() {
        Map<String, Integer> colors = new HashMap<>();
        putFactionColor(colors, "ANGMAR", 0x7E8FA8);
        putFactionColor(colors, "GONDOR", 0xF7F7EF);
        putFactionColor(colors, "DURINS_FOLK", 0xC99A20);
        putFactionColor(colors, "DUNEDAIN", 0x1F5A36);
        putFactionColor(colors, "RANGER_NORTH", 0x1F5A36);
        putFactionColor(colors, "ROHAN", 0x8FC43A);
        putFactionColor(colors, "ISENGARD", 0x6A6A6A);
        putFactionColor(colors, "MORDOR", 0x0E0E0E);
        putFactionColor(colors, "RHUDEL", 0xB13D32);
        putFactionColor(colors, "WOOD_ELF", 0x22A060);
        putFactionColor(colors, "HARAD", 0xD24D20);
        putFactionColor(colors, "NEAR_HARAD", 0xD24D20);
        putFactionColor(colors, "HIGH_ELVES", 0x58BFEF);
        putFactionColor(colors, "HIGH_ELF", 0x58BFEF);
        putFactionColor(colors, "TAURETHRIM", 0x00A98B);
        putFactionColor(colors, "BREE", 0xC8A56A);
        putFactionColor(colors, "BLUE_MOUNTAINS", 0x2468C8);
        putFactionColor(colors, "DALE", 0xD9822B);
        putFactionColor(colors, "HOBBIT", 0x6FBF55);
        putFactionColor(colors, "LOTHLORIEN", 0xE4D34C);
        putFactionColor(colors, "DUNLAND", 0x805333);
        putFactionColor(colors, "MORWAITH", 0x7A1230);
        putFactionColor(colors, "HALF_TROLL", 0x737A35);
        putFactionColor(colors, "GUNDABAD", 0x465A70);
        putFactionColor(colors, "DORWINION", 0x8B3F8C);
        putFactionColor(colors, "DOL_GULDUR", 0x4C6F30);
        putFactionColor(colors, "FANGORN", 0x2B6B28);
        putFactionColor(colors, "NONE", 0x9A9A9A);
        return colors;
    }

    private static void putFactionColor(Map<String, Integer> colors, String factionKey, int color) {
        colors.put(factionKey, Integer.valueOf(color));
        colors.put(KOMEAlliance.normalizeFactionKey(factionKey), Integer.valueOf(color));
    }

    private static void drawHighlightTexture(LOTRGuiMap map, int tileColor) {
        if (tileColor != highlightedTileColor) {
            int[] textureData = highlightTexture.getTextureData();
            Arrays.fill(textureData, 0);
            int width = tileMaskImage.getWidth();
            int height = tileMaskImage.getHeight();
            for (int i = 0; i < tileMaskPixels.length; i++) {
                if ((tileMaskPixels[i] & 0xFFFFFF) == tileColor && (tileMaskPixels[i] >>> 24) > 24) {
                    textureData[i] = isTileEdge(i, tileColor, width, height) ? HIGHLIGHT_EDGE_COLOR : HIGHLIGHT_FILL_COLOR;
                }
            }
            highlightTexture.updateDynamicTexture();
            highlightedTileColor = tileColor;
        }

        drawMapTexture(map, highlightTextureLocation, 1.0f);
    }

    private static void clearHighlightTexture() {
        highlightedTileColor = 0;
        if (highlightTexture != null) {
            Arrays.fill(highlightTexture.getTextureData(), 0);
            highlightTexture.updateDynamicTexture();
        }
    }

    private static void drawTileTooltip(LOTRGuiMap map, int tileColor, int mouseX, int mouseY) {
        String tileId = tileIdsByColor.get(tileColor);
        if (tileId == null) {
            return;
        }
        List lines = new ArrayList();
        lines.add("Tile " + tileId);
        KOMEConquestTile tile = (KOMEConquestTile) KOMEClientData.INSTANCE.conquestTiles.get(tileId);
        KOMETileTroopSummary summary = (KOMETileTroopSummary) KOMEClientData.INSTANCE.troopSummaries.get(tileId);
        String owner = tile != null && tile.currentRulingFaction().length() > 0 ? tile.currentRulingFaction() : summary == null ? "" : summary.ownerFaction;
        if (owner.length() > 0) {
            lines.add("Current Ruling Faction: " + owner);
        }
        KOMETileWaypointLink waypointLink = (KOMETileWaypointLink) KOMEClientData.INSTANCE.tileWaypointLinksByTileId.get(tileId);
        lines.add("LOTR Waypoint: " + (waypointLink == null ? "Missing" : waypointLink.displayName()));
        if (summary != null) {
            if (summary.offensiveTotal > 0 || summary.offensiveUsed > 0) {
                lines.add("Offensive: " + summary.offensiveUsed + "/" + summary.offensiveTotal + " used, " + Math.max(0, summary.offensiveTotal - summary.offensiveUsed) + " free");
            }
            if (summary.defensiveTotal > 0 || summary.defensiveUsed > 0) {
                lines.add("Defensive: " + summary.defensiveUsed + "/" + summary.defensiveTotal + " used, " + Math.max(0, summary.defensiveTotal - summary.defensiveUsed) + " free");
            }
            if (summary.stationedOffensivePop > 0 || summary.stationedMountedPop > 0 || summary.stationedDefensivePop > 0) {
                lines.add("Stationed: Off " + summary.stationedOffensivePop + ", Mounted " + summary.stationedMountedPop + ", Def " + summary.stationedDefensivePop);
            }
            if (summary.movingPop > 0 || summary.incomingPop > 0) {
                lines.add("Moving: Out " + summary.movingPop + ", In " + summary.incomingPop);
            }
        }
        String movementText = getMovementTooltip(tileId);
        if (movementText.length() > 0) {
            lines.add(movementText);
        }
        FontRenderer font = KOMEMinecraftClient.fontRenderer();
        int width = 0;
        for (Object line : lines) {
            width = Math.max(width, font.getStringWidth(String.valueOf(line)));
        }
        int x = mouseX + 10;
        int y = mouseY + 10;
        int mapXMax = mapInt("mapXMax");
        int mapYMax = mapInt("mapYMax");
        if (x + width + 4 > mapXMax) {
            x = mouseX - width - 10;
        }
        int height = lines.size() * 10 + 2;
        if (y + height > mapYMax) {
            y = mouseY - height - 4;
        }
        Gui.drawRect(x - 3, y - 3, x + width + 3, y + height - 1, 0xC0000000);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFFFFF : i == lines.size() - 1 && movementText.length() > 0 ? 0xA8D8FF : 0xF0D8AA;
            font.drawStringWithShadow(String.valueOf(lines.get(i)), x, y + i * 10, color);
        }
    }

    private static void drawTroopMarkers(LOTRGuiMap map) {
        if (!ensureTileMaskLoaded()) {
            return;
        }
        for (Object object : KOMEClientData.INSTANCE.troopSummaries.values()) {
            KOMETileTroopSummary summary = (KOMETileTroopSummary) object;
            if (summary == null || !summary.hasAnyTroops()) {
                continue;
            }
            int[] center = tileCentersById.get(KOMEConquestTile.normalizeId(summary.tileId));
            if (center == null) {
                continue;
            }
            int screenX = mapScreenX(map, center[0]);
            int screenY = mapScreenY(map, center[1]);
            if (screenX < mapInt("mapXMin") || screenX > mapInt("mapXMax") || screenY < mapInt("mapYMin") || screenY > mapInt("mapYMax")) {
                continue;
            }
            FontRenderer font = KOMEMinecraftClient.fontRenderer();
            int markerX = screenX - TROOP_MARKER_WIDTH / 2;
            int markerY = screenY - TROOP_MARKER_HEIGHT / 2;
            drawTexturedMarker(getTroopMarkerTextureLocation(), markerX, markerY, TROOP_MARKER_WIDTH, TROOP_MARKER_HEIGHT);
            int primaryPop = summary.stationedPop > 0 ? summary.stationedPop : summary.incomingPop > 0 ? summary.incomingPop : summary.movingPop;
            drawTroopCount(font, markerX, markerY, TROOP_MARKER_WIDTH, TROOP_MARKER_HEIGHT, primaryPop);
            int sideTextY = markerY + 4;
            if (summary.movingPop > 0 && primaryPop != summary.movingPop) {
                font.drawStringWithShadow(">" + abbreviatePop(summary.movingPop), markerX + TROOP_MARKER_WIDTH + 2, sideTextY, 0xFFFFD966);
                sideTextY += 10;
            }
            if (summary.incomingPop > 0 && primaryPop != summary.incomingPop) {
                font.drawStringWithShadow("<" + abbreviatePop(summary.incomingPop), markerX + TROOP_MARKER_WIDTH + 2, sideTextY, 0xFF6FCBFF);
            }
        }
    }

    private static void drawRouteEdgeMarkers(LOTRGuiMap map) {
        if (KOMEClientData.INSTANCE.routeEdges.isEmpty()) {
            return;
        }
        FontRenderer font = KOMEMinecraftClient.fontRenderer();
        int mapXMin = mapInt("mapXMin");
        int mapXMax = mapInt("mapXMax");
        int mapYMin = mapInt("mapYMin");
        int mapYMax = mapInt("mapYMax");
        for (Object object : KOMEClientData.INSTANCE.routeEdges.values()) {
            KOMEConquestRouteEdge edge = (KOMEConquestRouteEdge) object;
            if (!shouldDrawRouteEdgeMarker(edge)) {
                continue;
            }
            int screenX = worldScreenX(map, edge.markerX);
            int screenY = worldScreenY(map, edge.markerZ);
            if (screenX < mapXMin || screenX > mapXMax || screenY < mapYMin || screenY > mapYMax) {
                continue;
            }
            boolean bridge = KOMEConquestRouteEdge.BRIDGE.equals(edge.edgeType);
            if (bridge) {
                drawTexturedMarker(getBridgeMarkerTextureLocation(), screenX - BRIDGE_MARKER_WIDTH / 2,
                    screenY - BRIDGE_MARKER_HEIGHT / 2, BRIDGE_MARKER_WIDTH, BRIDGE_MARKER_HEIGHT);
            } else {
                int fill = 0xFFE8D36E;
                Gui.drawRect(screenX - 4, screenY - 4, screenX + 5, screenY + 5, 0xCC000000);
                Gui.drawRect(screenX - 3, screenY - 3, screenX + 4, screenY + 4, 0xFF5A4516);
                Gui.drawRect(screenX - 2, screenY - 2, screenX + 3, screenY + 3, fill);
                String label = edge.name == null || edge.name.length() == 0 ? "Pass" : edge.name;
                font.drawStringWithShadow(label, screenX + 7, screenY - 4, fill);
            }
        }
    }

    private static List<String> drawAutomaticBridgeDebugMarkers(LOTRGuiMap map, int mouseX, int mouseY) {
        if (!SHOW_AUTOMATIC_BRIDGE_DEBUG || !ensureTileMaskLoaded()) {
            return null;
        }
        int mapXMin = mapInt("mapXMin");
        int mapXMax = mapInt("mapXMax");
        int mapYMin = mapInt("mapYMin");
        int mapYMax = mapInt("mapYMax");
        List<String> tooltip = null;
        for (KOMEConquestTileDefaults.AutomaticBridgeMarker marker : KOMEConquestTileDefaults.getAutomaticBridgeMarkers()) {
            if (marker.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID) {
                continue;
            }
            int screenX = worldScreenX(map, marker.x);
            int screenY = worldScreenY(map, marker.z);
            if (screenX < mapXMin || screenX > mapXMax || screenY < mapYMin || screenY > mapYMax) {
                continue;
            }
            drawDiamond(screenX, screenY, 5, 0xDD000000);
            drawDiamond(screenX, screenY, 4, 0xFFE8C46A);
            drawTexturedMarker(getBridgeMarkerTextureLocation(), screenX - BRIDGE_MARKER_WIDTH / 2,
                screenY - BRIDGE_MARKER_HEIGHT / 2, BRIDGE_MARKER_WIDTH, BRIDGE_MARKER_HEIGHT);
            if (Math.abs(mouseX - screenX) <= 7 && Math.abs(mouseY - screenY) <= 7) {
                tooltip = new ArrayList<String>();
                tooltip.add("Automatic Bridge");
                tooltip.add(marker.getTilePairLabel());
                tooltip.add("XYZ: " + Math.round(marker.x) + ", " + Math.round(marker.y) + ", " + Math.round(marker.z));
            }
        }
        return tooltip;
    }

    private static void drawAutomaticBridgeTooltip(LOTRGuiMap map, List<String> lines, int mouseX, int mouseY) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        FontRenderer font = KOMEMinecraftClient.fontRenderer();
        int width = 0;
        for (String line : lines) {
            width = Math.max(width, font.getStringWidth(line));
        }
        int x = mouseX + 10;
        int y = mouseY + 10;
        int mapXMax = mapInt("mapXMax");
        int mapYMax = mapInt("mapYMax");
        if (x + width + 6 > mapXMax) {
            x = mouseX - width - 12;
        }
        int height = lines.size() * 10 + 4;
        if (y + height > mapYMax) {
            y = mouseY - height - 6;
        }
        Gui.drawRect(x - 4, y - 4, x + width + 4, y + height, 0xD0181208);
        Gui.drawRect(x - 4, y - 4, x + width + 4, y + 7, 0xDD5B1F2A);
        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? 0xFFE8C46A : 0xFFF0D8AA;
            font.drawStringWithShadow(lines.get(i), x, y + i * 10, color);
        }
    }

    private static void drawRoutePreview(LOTRGuiMap map) {
        if (!isPreviewingRoute() || !ensureTileMaskLoaded()) {
            return;
        }
        List<int[]> points = new ArrayList<int[]>();
        List<String> tiles = new ArrayList<String>();
        for (String tile : routePreviewTiles) {
            String normalized = KOMEConquestTile.normalizeId(tile);
            int[] center = tileCentersById.get(normalized);
            if (center != null) {
                points.add(new int[] {mapScreenX(map, center[0]), mapScreenY(map, center[1])});
                tiles.add(normalized);
            }
        }
        if (points.size() < 1) {
            return;
        }
        for (int i = 1; i < points.size(); i++) {
            int[] previous = points.get(i - 1);
            int[] current = points.get(i);
            drawLine(previous[0], previous[1], current[0], current[1], ROUTE_LINE_SHADOW, 2.25F);
            drawLine(previous[0], previous[1], current[0], current[1], ROUTE_LINE_COLOR, 1.25F);
        }
        drawRouteSpecialEdgeMarkers(map, tiles, points);
        for (int i = 0; i < points.size(); i++) {
            int[] point = points.get(i);
            if (i == 0) {
                drawRouteOriginMarker(point[0], point[1]);
            } else if (i == points.size() - 1) {
                drawRouteDestinationMarker(point[0], point[1]);
            } else {
                drawRouteIntermediatePoint(point[0], point[1]);
            }
        }
    }

    private static void drawActiveMovementRoutes(LOTRGuiMap map) {
        if (KOMEClientData.INSTANCE.armyMovements.isEmpty() || !ensureTileMaskLoaded()) {
            return;
        }
        for (Object object : KOMEClientData.INSTANCE.armyMovements.values()) {
            if (!(object instanceof KOMEArmyMovementOrder)) {
                continue;
            }
            KOMEArmyMovementOrder order = (KOMEArmyMovementOrder) object;
            if (order == null || !order.isMoving() || order.routeTiles.size() < 2) {
                continue;
            }
            List<int[]> points = new ArrayList<int[]>();
            List<String> tiles = new ArrayList<String>();
            for (String tile : order.routeTiles) {
                String normalized = KOMEConquestTile.normalizeId(tile);
                int[] center = tileCentersById.get(normalized);
                if (center != null) {
                    points.add(new int[] {mapScreenX(map, center[0]), mapScreenY(map, center[1])});
                    tiles.add(normalized);
                }
            }
            if (points.size() < 2) {
                continue;
            }
            int currentIndex = Math.max(0, Math.min(order.currentRouteIndex, points.size() - 1));
            for (int i = 1; i < points.size(); i++) {
                int[] previous = points.get(i - 1);
                int[] current = points.get(i);
                int color = i <= currentIndex ? ROUTE_COMPLETED_COLOR : ROUTE_REMAINING_COLOR;
                float width = i <= currentIndex ? 1.0F : 1.35F;
                drawLine(previous[0], previous[1], current[0], current[1], ROUTE_LINE_SHADOW, width + 1.0F);
                drawLine(previous[0], previous[1], current[0], current[1], color, width);
            }
            drawRouteSpecialEdgeMarkers(map, tiles, points);
            for (int i = 0; i < points.size(); i++) {
                int[] point = points.get(i);
                if (i == 0) {
                    drawRouteOriginMarker(point[0], point[1]);
                } else if (i == points.size() - 1) {
                    drawRouteDestinationMarker(point[0], point[1]);
                } else if (i == currentIndex) {
                    drawDiamond(point[0], point[1], 4, ROUTE_DARK);
                    drawDiamond(point[0], point[1], 3, ROUTE_CURRENT_COLOR);
                    drawTexturedMarker(getTroopMarkerTextureLocation(), point[0] - TROOP_MARKER_WIDTH / 2,
                        point[1] - TROOP_MARKER_HEIGHT / 2, TROOP_MARKER_WIDTH, TROOP_MARKER_HEIGHT);
                } else {
                    drawRouteIntermediatePoint(point[0], point[1]);
                }
            }
        }
    }

    private static void drawRouteSpecialEdgeMarkers(LOTRGuiMap map, List<String> tiles, List<int[]> points) {
        for (int i = 1; i < tiles.size() && i < points.size(); i++) {
            KOMEConquestRouteEdge edge = (KOMEConquestRouteEdge) KOMEClientData.INSTANCE.routeEdges.get(KOMEConquestRouteEdge.key(tiles.get(i - 1), tiles.get(i)));
            if (edge == null || !edge.isSpecialPassage()) {
                continue;
            }
            int markerX;
            int markerY;
            if (edge.manual && edge.markerDimension == LOTRDimension.MIDDLE_EARTH.dimensionID) {
                markerX = worldScreenX(map, edge.markerX);
                markerY = worldScreenY(map, edge.markerZ);
            } else {
                int[] previous = points.get(i - 1);
                int[] current = points.get(i);
                markerX = (previous[0] + current[0]) / 2;
                markerY = (previous[1] + current[1]) / 2;
            }
            if (KOMEConquestRouteEdge.BRIDGE.equals(edge.edgeType)) {
                drawTexturedMarker(getBridgeMarkerTextureLocation(), markerX - BRIDGE_MARKER_WIDTH / 2,
                    markerY - BRIDGE_MARKER_HEIGHT / 2, BRIDGE_MARKER_WIDTH, BRIDGE_MARKER_HEIGHT);
            } else {
                drawDiamond(markerX, markerY, 4, ROUTE_DARK);
                drawDiamond(markerX, markerY, 3, ROUTE_GOLD);
                drawDiamond(markerX, markerY, 1, ROUTE_DARK);
            }
        }
    }

    private static void drawRouteIntermediatePoint(int x, int y) {
        drawDiamond(x, y, 2, ROUTE_DARK);
        drawDiamond(x, y, 1, ROUTE_GOLD);
    }

    private static void drawRouteOriginMarker(int x, int y) {
        drawDiamond(x, y, 4, ROUTE_DARK);
        drawDiamond(x, y, 3, ROUTE_GOLD);
        drawDiamond(x, y, 1, 0xFF4B3516);
    }

    private static void drawRouteDestinationMarker(int x, int y) {
        drawDiamond(x, y, 5, ROUTE_DARK);
        drawDiamond(x, y, 4, ROUTE_GOLD);
        drawDiamond(x, y, 2, ROUTE_DESTINATION);
    }

    private static void drawDiamond(int centerX, int centerY, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int halfWidth = radius - Math.abs(dy);
            Gui.drawRect(centerX - halfWidth, centerY + dy, centerX + halfWidth + 1, centerY + dy + 1, color);
        }
    }

    private static void drawLine(int x1, int y1, int x2, int y2, int argb, float width) {
        float alpha = (argb >>> 24 & 255) / 255.0F;
        float red = (argb >>> 16 & 255) / 255.0F;
        float green = (argb >>> 8 & 255) / 255.0F;
        float blue = (argb & 255) / 255.0F;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(width);
        GL11.glColor4f(red, green, blue, alpha);
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x1, y1);
        GL11.glVertex2d(x2, y2);
        GL11.glEnd();
        GL11.glPopAttrib();
    }

    private static void drawRoutePreviewPanel(LOTRGuiMap map, int mouseX, int mouseY) {
        if (!isPreviewingRoute()) {
            return;
        }
        FontRenderer font = KOMEMinecraftClient.fontRenderer();
        int x = routePreviewPanelX();
        int y = routePreviewPanelY();
        int w = routePreviewPanelWidth();
        Gui.drawRect(x, y, x + w, y + 78, 0xC81B1208);
        Gui.drawRect(x + 2, y + 2, x + w - 2, y + 18, 0xCC5A1118);
        font.drawString("Confirm Movement Route", x + 10, y + 6, 0xFFE8C46A);
        String route = routePreviewMove.originTile + " -> " + routePreviewMove.destinationTile
            + " | " + routePreviewMove.distanceTiles + " step(s) | first move immediate";
        font.drawString(KOMEGuiTheme.trimToWidth(font, route, w - 20), x + 10, y + 25, 0xFFFFFFFF);
        font.drawString(KOMEGuiTheme.trimToWidth(font, formatRoutePreviewTiles(), w - 20), x + 10, y + 39, 0xFFBFE8FF);
        font.drawString(KOMEGuiTheme.trimToWidth(font, "Full route completion: about " + formatDuration(routePreviewMove.travelMillis), w - 20),
            x + 10, y + 52, 0xFFBFA77A);
        drawRouteButton(routeConfirmButtonX(), routeButtonY(), 92, 18, "Confirm", isOverRouteConfirmButton(map, mouseX, mouseY), 0xDD2E5D27);
        drawRouteButton(routeCancelButtonX(), routeButtonY(), 92, 18, "Cancel", isOverRouteCancelButton(map, mouseX, mouseY), 0xDD4F2A2A);
    }

    private static void drawRouteButton(int x, int y, int w, int h, String label, boolean hover, int fill) {
        Gui.drawRect(x, y, x + w, y + h, 0xFF1B1208);
        Gui.drawRect(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xFFE8C46A : fill);
        FontRenderer font = KOMEMinecraftClient.fontRenderer();
        font.drawString(label, x + (w - font.getStringWidth(label)) / 2, y + 5, hover ? 0xFF1B1208 : 0xFFFFFFFF);
    }

    private static String formatRoutePreviewTiles() {
        if (routePreviewTiles.isEmpty()) {
            return "Route: none";
        }
        StringBuilder route = new StringBuilder("Route: ");
        for (int i = 0; i < routePreviewTiles.size(); i++) {
            if (i > 0) {
                route.append(" -> ");
            }
            if (i >= 6 && routePreviewTiles.size() > 8) {
                route.append("... -> ").append(routePreviewTiles.get(routePreviewTiles.size() - 1));
                break;
            }
            route.append(routePreviewTiles.get(i));
        }
        return route.toString();
    }

    private static int routePreviewPanelWidth() {
        return Math.min(460, Math.max(320, mapInt("mapXMax") - mapInt("mapXMin") - 48));
    }

    private static int routePreviewPanelX() {
        return (mapInt("mapXMin") + mapInt("mapXMax") - routePreviewPanelWidth()) / 2;
    }

    private static int routePreviewPanelY() {
        return mapInt("mapYMax") - 92;
    }

    private static int routeButtonY() {
        return routePreviewPanelY() + 56;
    }

    private static int routeConfirmButtonX() {
        return routePreviewPanelX() + routePreviewPanelWidth() - 198;
    }

    private static int routeCancelButtonX() {
        return routePreviewPanelX() + routePreviewPanelWidth() - 100;
    }

    private static boolean isOverRouteConfirmButton(LOTRGuiMap map, int mouseX, int mouseY) {
        return isPreviewingRoute() && mouseX >= routeConfirmButtonX() && mouseX < routeConfirmButtonX() + 92
            && mouseY >= routeButtonY() && mouseY < routeButtonY() + 18;
    }

    private static boolean isOverRouteCancelButton(LOTRGuiMap map, int mouseX, int mouseY) {
        return isPreviewingRoute() && mouseX >= routeCancelButtonX() && mouseX < routeCancelButtonX() + 92
            && mouseY >= routeButtonY() && mouseY < routeButtonY() + 18;
    }

    private static void drawRouteErrorPanel(LOTRGuiMap map) {
        if (!isChoosingDestination() || routePreviewError == null) {
            return;
        }
        FontRenderer font = KOMEMinecraftClient.fontRenderer();
        int width = Math.min(330, Math.max(260, mapInt("mapXMax") - mapInt("mapXMin") - 40));
        int x = mapInt("mapXMax") - width - 18;
        int y = Math.max(mapInt("mapYMin") + 44, mapInt("mapYMax") - 148);
        int height = 132;
        KOMEGuiTheme.drawMainPanel(x, y, width, height);
        KOMEGuiTheme.drawHeader(font, routePreviewError.failureTitle.length() == 0 ? "Route Blocked" : routePreviewError.failureTitle,
            x + 8, y + 8, width - 16);
        int textX = x + 16;
        int textY = y + 38;
        font.drawString("Destination: " + KOMEConquestTile.normalizeId(routePreviewError.destinationTileId),
            textX, textY, KOMEGuiTheme.COLOR_BAD);
        textY += 12;
        String summary = routePreviewError.failureSummary.length() == 0 ? "No legal route to this tile." : routePreviewError.failureSummary;
        textY = KOMEGuiTheme.drawWrappedText(font, summary, textX, textY, width - 32, KOMEGuiTheme.COLOR_TEXT);
        textY += 4;
        int rows = Math.min(5, routePreviewError.failureDetails.size());
        for (int i = 0; i < rows && textY < y + height - 24; i++) {
            String detail = KOMEGuiTheme.trimToWidth(font, String.valueOf(routePreviewError.failureDetails.get(i)), width - 34);
            font.drawString(detail, textX + 4, textY, KOMEGuiTheme.COLOR_TEXT_MUTED);
            textY += 10;
        }
        if (routePreviewError.hiddenDetailCount > 0 && textY < y + height - 18) {
            font.drawString("+" + routePreviewError.hiddenDetailCount + " more blocked checks",
                textX + 4, textY, KOMEGuiTheme.COLOR_WARN);
        }
        String action = routePreviewError.suggestedAction.length() == 0
            ? "Right-click another tile, or Esc to cancel."
            : routePreviewError.suggestedAction;
        font.drawString(KOMEGuiTheme.trimToWidth(font, action, width - 32), textX, y + height - 18, KOMEGuiTheme.COLOR_BORDER_RED);
    }

    private static boolean shouldDrawRouteEdgeMarker(KOMEConquestRouteEdge edge) {
        if (edge == null || !edge.manual || edge.markerDimension != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return false;
        }
        return KOMEConquestRouteEdge.BRIDGE.equals(edge.edgeType)
            || KOMEConquestRouteEdge.MOUNTAIN_PASS.equals(edge.edgeType);
    }

    private static void drawTexturedMarker(ResourceLocation texture, int x, int y, int width, int height) {
        if (texture == null) {
            return;
        }
        KOMEMinecraftClient.textureManager().bindTexture(texture);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0D, 0.0D, 1.0D);
        tessellator.addVertexWithUV(x + width, y + height, 0.0D, 1.0D, 1.0D);
        tessellator.addVertexWithUV(x + width, y, 0.0D, 1.0D, 0.0D);
        tessellator.addVertexWithUV(x, y, 0.0D, 0.0D, 0.0D);
        tessellator.draw();
        GL11.glPopAttrib();
    }

    private static void drawTroopCount(FontRenderer font, int markerX, int markerY, int markerWidth, int markerHeight, int population) {
        if (population <= 0) {
            return;
        }
        int scaledMaxWidth = Math.round(Math.max(markerWidth + 12, 24) / TROOP_COUNT_TEXT_SCALE);
        String text = formatTroopCount(font, population, scaledMaxWidth);
        float textWidth = font.getStringWidth(text) * TROOP_COUNT_TEXT_SCALE;
        float textX = markerX + markerWidth / 2.0F - textWidth / 2.0F;
        float textY = markerY - 7.0F;
        drawScaledMarkerText(font, text, textX - TROOP_COUNT_TEXT_SCALE, textY, 0xFF2A2116);
        drawScaledMarkerText(font, text, textX + TROOP_COUNT_TEXT_SCALE, textY, 0xFF2A2116);
        drawScaledMarkerText(font, text, textX, textY - TROOP_COUNT_TEXT_SCALE, 0xFF2A2116);
        drawScaledMarkerText(font, text, textX, textY + TROOP_COUNT_TEXT_SCALE, 0xFF2A2116);
        drawScaledMarkerText(font, text, textX, textY, 0xFFF6EEC8);
    }

    private static void drawScaledMarkerText(FontRenderer font, String text, float x, float y, int color) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0.0F);
        GL11.glScalef(TROOP_COUNT_TEXT_SCALE, TROOP_COUNT_TEXT_SCALE, 1.0F);
        font.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    private static String formatTroopCount(FontRenderer font, int population, int maxWidth) {
        String full = String.valueOf(population);
        if (font.getStringWidth(full) <= maxWidth || population < 1000) {
            return full;
        }
        int tenths = population / 100;
        String decimal = (tenths / 10) + "." + (tenths % 10) + "k";
        if (font.getStringWidth(decimal) <= maxWidth && population < 10000) {
            return decimal;
        }
        String compact = (population / 1000) + "k";
        return font.getStringWidth(compact) <= maxWidth ? compact : String.valueOf(population / 1000);
    }

    private static int mapScreenX(LOTRGuiMap map, int imageX) {
        double mapX = imageX * (double) LOTRGenLayerWorld.imageWidth / tileMaskImage.getWidth();
        return (int) Math.round(mapInt("mapXMin") + mapInt("mapWidth") / 2.0 + (mapX - mapNumber(map, "posX")) * mapNumber(map, "zoomScale"));
    }

    private static int mapScreenY(LOTRGuiMap map, int imageY) {
        double mapY = imageY * (double) LOTRGenLayerWorld.imageHeight / tileMaskImage.getHeight();
        return (int) Math.round(mapInt("mapYMin") + mapInt("mapHeight") / 2.0 + (mapY - mapNumber(map, "posY")) * mapNumber(map, "zoomScale"));
    }

    private static int worldScreenX(LOTRGuiMap map, double worldX) {
        double mapX = worldX / LOTRGenLayerWorld.scale + LOTRGenLayerWorld.originX;
        return (int) Math.round(mapInt("mapXMin") + mapInt("mapWidth") / 2.0 + (mapX - mapNumber(map, "posX")) * mapNumber(map, "zoomScale"));
    }

    private static int worldScreenY(LOTRGuiMap map, double worldZ) {
        double mapY = worldZ / LOTRGenLayerWorld.scale + LOTRGenLayerWorld.originZ;
        return (int) Math.round(mapInt("mapYMin") + mapInt("mapHeight") / 2.0 + (mapY - mapNumber(map, "posY")) * mapNumber(map, "zoomScale"));
    }

    private static String abbreviatePop(int pop) {
        return pop >= 1000 ? (pop / 1000) + "k" : String.valueOf(pop);
    }

    private static String getMovementTooltip(String tileId) {
        int outgoing = 0;
        int incoming = 0;
        int outgoingPop = 0;
        int incomingPop = 0;
        long now = System.currentTimeMillis();
        long soonest = Long.MAX_VALUE;
        for (Object object : KOMEClientData.INSTANCE.armyMovements.values()) {
            KOMEArmyMovementOrder order = (KOMEArmyMovementOrder) object;
            if (order == null || !order.isMoving()) {
                continue;
            }
            String stepOrigin = activeStepOrigin(order);
            String stepDestination = activeStepDestination(order);
            if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status) && tileId.equals(stepOrigin)) {
                incoming++;
                incomingPop += order.population;
                soonest = Math.min(soonest, Math.max(0L, order.nextStepAvailableMillis - now));
                continue;
            }
            if (tileId.equals(stepOrigin)) {
                outgoing++;
                outgoingPop += order.population;
            }
            if (tileId.equals(stepDestination)) {
                incoming++;
                incomingPop += order.population;
                soonest = Math.min(soonest, order.getRemainingMillis(now));
            }
        }
        if (incoming == 0 && outgoing == 0) {
            return "";
        }
        String text = "";
        if (incoming > 0) {
            text += "In " + incomingPop + " pop ETA " + formatDuration(soonest);
        }
        if (outgoing > 0) {
            if (text.length() > 0) {
                text += " | ";
            }
            text += "Out " + outgoingPop + " pop";
        }
        return text;
    }

    private static String activeStepOrigin(KOMEArmyMovementOrder order) {
        if (order == null) {
            return "";
        }
        String current = KOMEConquestTile.normalizeId(order.currentTile);
        if (current.length() > 0) {
            return current;
        }
        String value = KOMEConquestTile.normalizeId(order.currentStepOriginTile);
        if (value.length() > 0) {
            return value;
        }
        return order.routeTiles.size() > order.currentRouteIndex
            ? KOMEConquestTile.normalizeId(order.routeTiles.get(order.currentRouteIndex))
            : KOMEConquestTile.normalizeId(order.originTile);
    }

    private static String activeStepDestination(KOMEArmyMovementOrder order) {
        if (order == null) {
            return "";
        }
        String next = KOMEConquestTile.normalizeId(order.nextTile);
        if (next.length() > 0) {
            return next;
        }
        String value = KOMEConquestTile.normalizeId(order.currentStepDestinationTile);
        if (value.length() > 0) {
            return value;
        }
        return order.routeTiles.size() > order.nextRouteIndex
            ? KOMEConquestTile.normalizeId(order.routeTiles.get(order.nextRouteIndex))
            : KOMEConquestTile.normalizeId(order.destinationTile);
    }

    private static String formatDuration(long millis) {
        long minutes = Math.max(0L, (millis + 59999L) / 60000L);
        long days = minutes / 1440L;
        long hours = (minutes % 1440L) / 60L;
        long mins = minutes % 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
    }

    private static void drawMapTexture(LOTRGuiMap map, ResourceLocation texture, float alpha) {
        if (texture == null) {
            return;
        }
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
            return mapField(name).get(map);
        } catch (Exception e) {
            throw new RuntimeException("Could not read LOTR map field " + name, e);
        }
    }

    private static void setMapFieldValue(LOTRGuiMap map, String name, float value) throws Exception {
        Field field = mapField(name);
        Class type = field.getType();
        if (type == Float.TYPE || type == Float.class) {
            field.setFloat(map, value);
        } else if (type == Double.TYPE || type == Double.class) {
            field.setDouble(map, value);
        } else if (type == Integer.TYPE || type == Integer.class) {
            field.setInt(map, Math.round(value));
        } else {
            field.set(map, Float.valueOf(value));
        }
    }

    private static Field mapField(String name) throws NoSuchFieldException {
        Field field = lotrMapFields.get(name);
        if (field == null) {
            field = LOTRGuiMap.class.getDeclaredField(name);
            field.setAccessible(true);
            lotrMapFields.put(name, field);
        }
        return field;
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
