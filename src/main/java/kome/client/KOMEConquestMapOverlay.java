package kome.client;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEConquestRouteEdge;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEConquestTileDefaults;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEUnitMapMarker;
import kome.common.data.KOMETileWaypointLink;
import kome.common.data.KOMETileTroopSummary;
import kome.common.data.KOMETileRasterSnapshot;
import kome.common.data.KOMETileResolution;
import kome.common.data.KOMETileWorldResolver;
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
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KOMEConquestMapOverlay implements net.minecraft.client.resources.IResourceManagerReloadListener {
    private static final ResourceLocation DESERT_SHADE = new ResourceLocation("kome:map/reset_conquest_desert_shade.png");
    private static final ResourceLocation LABELS = new ResourceLocation("kome:map/reset_conquest_labels.png");
    private static final ResourceLocation BRIDGE_MARKER_ART = new ResourceLocation("kome:textures/gui/bridge.png");
    private static final ResourceLocation RIVER_MARKER_ART = new ResourceLocation("kome:textures/gui/river.png");
    private static final int TROOP_MARKER_WIDTH = 9;
    private static final int TROOP_MARKER_HEIGHT = 15;
    private static final int BRIDGE_MARKER_WIDTH = 6;
    private static final int BRIDGE_MARKER_HEIGHT = 3;
    private static final int RIVER_MARKER_WIDTH = 4;
    private static final int RIVER_MARKER_HEIGHT = 2;
    private static final float TROOP_COUNT_TEXT_SCALE = 0.70F;
    private static final int ROUTE_LINE_COLOR = 0xE0D6B45A;
    private static final int ROUTE_LINE_SHADOW = 0xCC3A2A12;
    private static final int ROUTE_COMPLETED_COLOR = 0xAA8C7440;
    private static final int ROUTE_REMAINING_COLOR = 0xE8E8C46A;
    private static final int ROUTE_CURRENT_COLOR = 0xFF6FCBFF;
    private static final int ROUTE_GOLD = 0xFFE8C46A;
    private static final int ROUTE_DARK = 0xFF3A2A12;
    private static final int ROUTE_DESTINATION = 0xFF7A1F25;
    private static final int TOGGLE_BUTTON_SIZE = 10;
    private static final int TOGGLE_BUTTON_GAP = 4;
    private static final int TOGGLE_BUTTON_TOP_MARGIN = 34; // Native Zoom Out ends at map-relative Y=30.
    private static KOMETileRasterSnapshot tileRaster;
    private static KOMETileRasterSnapshot failedRaster;
    private static final KOMEMapBorders.Cache borderCache = new KOMEMapBorders.Cache();
    private static KOMEMapBorders mapBorders;
    private static LOTRGuiMap earlyTerritoryMap;
    private static boolean warnedEarlyTerritoryFailure;
    private static LOTRGuiMap earlyGeographyMap;
    private static List<String> earlyBridgeTooltip, earlyRiverTooltip;
    private static boolean warnedEarlyGeographyFailure;
    private static LOTRGuiMap earlyBuildMap, buildSpritesMap;
    private static List<String> earlyBuildTooltip;
    private static boolean warnedEarlyBuildFailure;
    private static int[] tileMaskPixels;
    private static int renderedClaimRevision = -1;
    static final KOMETerritoryStyle territoryStyle = new KOMETerritoryStyle();
    private static KOMETerritoryStyle.Palette renderedPalette;
    private static KOMETerritoryStyle.Opacity renderedOpacity;
    private static Map<Integer, Integer> territoryColors = java.util.Collections.emptyMap();
    private static Map<Integer, String> territoryOwners = java.util.Collections.emptyMap();
    private static boolean claimTextureDirty = true;
    private static final ClaimTextureState claimTextureState = new ClaimTextureState();
    private static DynamicTexture claimedTexture;
    private static DynamicTexture desertShadeTexture;
    private static DynamicTexture labelTexture;
    private static ResourceLocation claimedTextureLocation;
    private static ResourceLocation desertShadeTextureLocation;
    private static ResourceLocation labelTextureLocation;
    private static boolean showConquestTiles = true;
    private static boolean showRiverBlockMarkers = true;
    private static boolean showBridgeMarkers = true;
    private static boolean showTroopMarkers = true;
    private static boolean showBuildMarkers = true;
    static KOMEMapPreferences mapPreferences() {
        return new KOMEMapPreferences(territoryStyle.palette,territoryStyle.mode,territoryStyle.opacity,territoryStyle.showTileBorders,
            showConquestTiles,showRiverBlockMarkers,showBridgeMarkers,showTroopMarkers,showBuildMarkers);
    }
    static void applyMapPreferences(KOMEMapPreferences preferences) {
        territoryStyle.palette=preferences.palette;territoryStyle.mode=preferences.mode;territoryStyle.opacity=preferences.opacity;
        territoryStyle.showTileBorders=preferences.tileBorders;showConquestTiles=preferences.conquest;
        showRiverBlockMarkers=preferences.rivers;showBridgeMarkers=preferences.bridges;showTroopMarkers=preferences.troops;
        showBuildMarkers=preferences.builds;
        if(mapBorders!=null)mapBorders.territoryPresentation(territoryStyle);
        renderedPalette=null;renderedOpacity=null;claimTextureDirty=true;
    }
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
    private static final KOMEMapRadialMenu radialMenu = new KOMEMapRadialMenu();
    private static LOTRGuiMap radialMap;
    private static boolean radialLeftCaptured;
    private static LOTRGuiMap radialDragMap;
    private static Field mapButtonsField;
    private static KOMECompanyDestinationSelector companySelector;
    private static KOMECompanyOriginWorkflow originWorkflow;
    private static KOMECompanyRallyClient rally;
    private static KOMECompanyUnitsBrowser unitsBrowser;
    private static kome.client.gui.KOMEGuiJoinBattle directJoinScreen;
    private static boolean directJoinFromRoot;
    private static java.util.UUID directJoinViewCorrelation, directJoinSession;
    private static KOMECompanyDestinationPreview companyPreview = KOMECompanyDestinationPreview.EMPTY;
    private static kome.common.network.KOMEPacketJoinBattleViewRequest battleContextRequest;
    private static KOMERootJoinBattle rootJoin;
    private static boolean selectorOnLeft;
    private boolean wasRightMouseDown;
    private boolean wasLeftMouseDown;
    private boolean wasEscapeDown;

    public static void beginDestinationSelection(String companyId, String companyName, String originTile) {
        closeRadialInteraction();
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
        closeRadialInteraction();
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
        closeRadialInteraction();
        clearDestinationSelection();
        clearRoutePreview();
        clearRoutePreviewError();
    }

    public static void resetClientMapState() {
        KOMEMapProfiler.close();
        resetDestinationSelection();
        activeMapInstance = null;
        hasSavedMapViewport = false;
        clearTileGeometry();
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

    /** Reset before every native draw, including canceled draws and screens overriding drawScreen. */
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.HIGHEST, receiveCanceled = true)
    public void onMapDrawStart(GuiScreenEvent.DrawScreenEvent.Pre event) {
        earlyTerritoryMap = null;
        clearEarlyGeography();
        clearEarlyBuilds();
        if(KOMEMapMarkerTrace.ENABLED&&event.gui instanceof LOTRGuiMap&&!event.isCanceled())KOMEMapMarkerTrace.beginFrame(event.renderPartialTicks);
        if(KOMEMapProfiler.ENABLED&&event.gui instanceof LOTRGuiMap&&!event.isCanceled())KOMEMapProfiler.begin(territoryStyle,showConquestTiles);
    }

    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void onMapProfileEnd(GuiScreenEvent.DrawScreenEvent.Post event) {
        if(KOMEMapMarkerTrace.ENABLED&&event.gui instanceof LOTRGuiMap)KOMEMapMarkerTrace.endFrame();
        if(KOMEMapProfiler.ENABLED&&event.gui instanceof LOTRGuiMap)KOMEMapProfiler.end();
    }

    /** Called at the validated terrain exit, before compass, roads, players, waypoints and labels.
     * The original hook name remains stable; native icons and text are never called or copied here. */
    public static void onBeforeNativeMapLabels(LOTRGuiMap map, int mouseX, int mouseY) {
        if(KOMEMapMarkerTrace.ENABLED)KOMEMapMarkerTrace.observe(map);
        renderBeforeNativeLabels(map, () -> drawTerritoryLayer(map, mouseX, mouseY));
        renderEarlyGeography(map, () -> drawGeographyLayer(map, mouseX, mouseY));
        renderEarlyBuilds(map, () -> drawBuildLayer(map, mouseX, mouseY));
    }

    static void renderEarlyBuilds(LOTRGuiMap map,Runnable render) {
        if(earlyBuildMap==map)return;
        earlyBuildTooltip=null;
        try {render.run();buildSpritesMap=map;}
        catch(RuntimeException failure) {
            earlyBuildTooltip=null;buildSpritesMap=null;
            if(!warnedEarlyBuildFailure) {
                warnedEarlyBuildFailure=true;
                java.util.logging.Logger.getLogger("KOME").warning("Early Build drawing failed; native map drawing continues: "+failure);
            }
        }finally{earlyBuildMap=map;}
    }

    private static void drawBuildLayer(LOTRGuiMap map,int mouseX,int mouseY) {
        if(shouldSkipMap(map)||!showConquestTiles)return;
        earlyBuildTooltip=drawBuildMarkers(map,mouseX,mouseY);
    }

    private static void clearEarlyBuilds() {
        earlyBuildMap=buildSpritesMap=null;earlyBuildTooltip=null;
    }

    static void renderEarlyGeography(LOTRGuiMap map,Runnable render) {
        if(earlyGeographyMap==map)return;
        earlyBridgeTooltip=earlyRiverTooltip=null;
        try {render.run();}
        catch(RuntimeException failure) {
            earlyBridgeTooltip=earlyRiverTooltip=null;
            if(!warnedEarlyGeographyFailure) {
                warnedEarlyGeographyFailure=true;
                java.util.logging.Logger.getLogger("KOME").warning("Early geography drawing failed; native map drawing continues: "+failure);
            }
        }finally{earlyGeographyMap=map;}
    }

    private static void clearEarlyGeography() {
        earlyGeographyMap=null;
        earlyBridgeTooltip=earlyRiverTooltip=null;
    }

    /** Only sprites move to the native terrain exit. Native text is never redrawn. */
    private static void drawGeographyLayer(LOTRGuiMap map,int mouseX,int mouseY) {
        if(shouldSkipMap(map)||!showConquestTiles||!ensureTileMaskLoaded())return;
        earlyBridgeTooltip=drawAutomaticBridgeMarkers(map,mouseX,mouseY);
        earlyRiverTooltip=drawAutomaticRiverBlockMarkers(map,mouseX,mouseY);
        drawRouteEdgeMarkers(map,true);
        if(isPreviewingRoute())drawRouteBridgeIcons(map,routePreviewTiles);
        for(Object object:KOMEClientData.INSTANCE.armyMovements.values())if(object instanceof KOMEArmyMovementOrder) {
            KOMEArmyMovementOrder order=(KOMEArmyMovementOrder)object;
            if(order.isMoving()&&order.routeTiles.size()>=2)drawRouteBridgeIcons(map,order.routeTiles);
        }
    }

    private static void drawRouteBridgeIcons(LOTRGuiMap map,Iterable<String> route) {
        List<String> tiles=new ArrayList<>();List<int[]> points=new ArrayList<>();
        for(String tile:route) {
            String normalized=KOMEConquestTile.normalizeId(tile);int[] point=tileAnchorScreenPosition(map,normalized);
            if(point!=null){tiles.add(normalized);points.add(point);}
        }
        drawRouteSpecialEdgeMarkers(map,tiles,points,true);
    }

    /** Frame ownership includes failed/skipped attempts, independently of the rendering backend. */
    static void renderBeforeNativeLabels(LOTRGuiMap map, Runnable render) {
        try {
            render.run();
        } catch (RuntimeException failure) {
            if (!warnedEarlyTerritoryFailure) {
                warnedEarlyTerritoryFailure = true;
                java.util.logging.Logger.getLogger("KOME").warning("Early territory drawing failed; native map drawing continues: "+failure);
            }
        } finally {
            // Initial mask loading can clear geometry; record ownership only after that lifecycle work.
            earlyTerritoryMap = map; // Also owns skipped/failed attempts; never blend a partial pass twice.
        }
    }

    static boolean consumeEarlyTerritoryPass(LOTRGuiMap map) {
        boolean rendered = earlyTerritoryMap == map;
        earlyTerritoryMap = null;
        return rendered;
    }

    /** One shared pass for the native seam and the legacy Post fallback on rejected bytecode. */
    private static void drawTerritoryLayer(LOTRGuiMap map, int mouseX, int mouseY) {
        if (shouldSkipMap(map) || !showConquestTiles) return;
        long profileStart=KOMEMapProfiler.start();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT
            | GL11.GL_TEXTURE_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glPushMatrix();
        try {
            // Leave native label/waypoint depth contents and the incoming alpha test untouched.
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            if (!ensureTileMaskLoaded()) return;
            int tileColor = getHoveredTileColor(map, mouseX, mouseY);
            prepareTerritoryColors();
            if(KOMEMapProfiler.ENABLED&&territoryStyle.mode.fillsTerritory())KOMEMapProfiler.view(viewport(map));
            if (territoryStyle.mode.fillsTerritory()) {
                drawClaimedTexture(map);
                drawMapTexture(map, getDesertShadeTextureLocation(), 1.0f);
            }
            if (tileColor != 0 && territoryStyle.mode.fillsTerritory()) drawHighlightTexture(map, tileColor);
            drawGeometry(map, tileColor, false);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            KOMEMapProfiler.elapsed(KOMEMapProfiler.Stage.TERRITORY_PASS,profileStart);
        }
    }

    @SubscribeEvent
    public void onDrawMap(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof LOTRGuiMap)) {
            return;
        }
        LOTRGuiMap map = (LOTRGuiMap) event.gui;
        if(KOMEMapMarkerTrace.ENABLED)KOMEMapMarkerTrace.observe(map);
        boolean territoryRendered = consumeEarlyTerritoryPass(map);
        List<String> automaticBridgeTooltip=earlyGeographyMap==map?earlyBridgeTooltip:null;
        List<String> riverBlockTooltip=earlyGeographyMap==map?earlyRiverTooltip:null;
        List<String> buildTooltip=earlyBuildMap==map?earlyBuildTooltip:null;
        earlyBuildMap=null;earlyBuildTooltip=null;
        clearEarlyGeography(); // No Post fallback: never cover native waypoint names on an unsupported seam.
        updateMapViewportState(map);
        validateCompanySelectorLifecycle();
        if (shouldSkipMap(map)) {
            return;
        }

        if (isChoosingDestination()) {
            drawDestinationInstruction(map);
        }
        if (!showConquestTiles) {
            drawToggleButtons(map, event.mouseX, event.mouseY);
            return;
        }

        if (!ensureTileMaskLoaded()) {
            drawToggleButtons(map, event.mouseX, event.mouseY);
            return;
        }
        int tileColor = getHoveredTileColor(map, event.mouseX, event.mouseY);
        int radialHover = radialHit(map, event.mouseX, event.mouseY);
        if (!territoryRendered) drawTerritoryLayer(map, event.mouseX, event.mouseY);
        drawSelectorDestination(map);
        drawTileCue(map,originSourceTile(map));
        drawMapTexture(map, getLabelTextureLocation(), 1.0f);
        drawActiveMovementRoutes(map);
        drawRoutePreview(map);
        drawCompanyDestinationPreview(map);
        drawRouteEdgeMarkers(map);
        drawTroopMarkers(map);
        List<String> liveUnitTooltip = drawLiveUnitMarkers(map, event.mouseX, event.mouseY);
        if (!suppressMapInspection(map) && tileColor != 0 && automaticBridgeTooltip == null && riverBlockTooltip == null
                && liveUnitTooltip == null && buildTooltip == null) {
            drawTileTooltip(map, tileColor, event.mouseX, event.mouseY);
        }
        if (suppressMapInspection(map)) {
            // Command mode and tile/marker inspection mode are mutually exclusive.
        } else if (automaticBridgeTooltip != null) {
            drawAutomaticBridgeTooltip(map, automaticBridgeTooltip, event.mouseX, event.mouseY);
        } else if (riverBlockTooltip != null) {
            drawMarkerTooltip(map, riverBlockTooltip, event.mouseX, event.mouseY);
        } else if (liveUnitTooltip != null) {
            drawMarkerTooltip(map, liveUnitTooltip, event.mouseX, event.mouseY);
        } else if (buildTooltip != null) {
            drawMarkerTooltip(map, buildTooltip, event.mouseX, event.mouseY);
        }
        drawToggleButtons(map, event.mouseX, event.mouseY);
        drawRoutePreviewPanel(map, event.mouseX, event.mouseY);
        drawRouteErrorPanel(map);
        if (radialMap == map && radialMenu.isOpen() && !companySelectorOpen() && !originActive() && !unitsActive()) {
            double[] anchor = radialAnchorMapPosition(radialMenu.tileId());
            if (anchor != null) {
                KOMEMapViewport view = viewport(map);
                KOMEMapRadialRenderer.draw(radialMenu, view, view.screenX(anchor[0]), view.screenY(anchor[1]), radialHover,
                    KOMEMinecraftClient.fontRenderer(), radialScale());
            }
        }
    }

    /** Last relevant map layer. Native marker depth must not occlude panel fills or text. */
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    public void onDrawCompanySelector(GuiScreenEvent.DrawScreenEvent.Post event) {
        if (!(event.gui instanceof LOTRGuiMap)) return;
        LOTRGuiMap map=(LOTRGuiMap)event.gui;
        validateCompanySelectorLifecycle();
        if (radialMap!=map || (!companySelectorOpen()&&!originActive()&&!unitsActive()) || shouldSkipMap(map)) return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            if(unitsActive())KOMECompanyUnitsPanel.draw(unitsBrowser,unitsLayout(map),KOMEMinecraftClient.fontRenderer(),event.mouseX,event.mouseY);
            else if(originPicking())drawOriginPrompt(map,event.mouseX,event.mouseY);
            else KOMECompanyDestinationPanel.draw(companySelector,selectorLayout(map),KOMEMinecraftClient.fontRenderer(),
                event.mouseX,event.mouseY,previewClock());
        } finally {GL11.glPopAttrib();}
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        GuiScreen screen = KOMEMinecraftClient.currentScreen();
        updateMapViewportState(screen);
        validateCompanySelectorLifecycle();
        if ((radialMap != null && screen != radialMap) || (radialDragMap != null && screen != radialDragMap)) closeRadialInteraction();
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
        if (leftMouseDown && !wasLeftMouseDown && !radialLeftCaptured && screen instanceof LOTRGuiMap) {
            LOTRGuiMap map = (LOTRGuiMap) screen;
            if (!shouldSkipMap(map)) {
                int screenWidth = KOMEMinecraftClient.screenWidth(screen);
                int screenHeight = KOMEMinecraftClient.screenHeight(screen);
                int mouseX = Mouse.getX() * screenWidth / KOMEMinecraftClient.displayWidth();
                int mouseY = screenHeight - Mouse.getY() * screenHeight / KOMEMinecraftClient.displayHeight() - 1;
                if (unitsActive() || rallyActive() || originActive() || selectorOwnsPointer(map,mouseX,mouseY) || radialOwnsPointer(map,mouseX,mouseY)) {
                    // Panel input cannot also activate the independently polled KOME controls.
                } else if (isPreviewingRoute() && isOverRouteConfirmButton(map, mouseX, mouseY)) {
                    KOMEPacketHandler.network.sendToServer(new kome.common.network.KOMEPacketTroopGuiAction(
                        "move", routePreviewMove.companyId, routePreviewMove.destinationTile,
                        routePreviewMove.originTile, routePreviewMove.previewToken));
                    clearRoutePreview();
                    KOMEMinecraftClient.closePlayerScreen();
                } else if (isPreviewingRoute() && isOverRouteCancelButton(map, mouseX, mouseY)) {
                    clearRoutePreview();
                } else if (!isChoosingDestination() && !isOverMapControl(map, mouseX, mouseY)) {
                    KOMEPlayerBuild build = buildMarkerAt(map, mouseX, mouseY);
                    if (build != null) {
                        KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestOpenCapture(build.tileId, build.id));
                    }
                }
            }
        }
        wasLeftMouseDown = leftMouseDown;

        boolean rightMouseDown = Mouse.isButtonDown(1);
        if (!isChoosingDestination() || isPreviewingRoute() || !showConquestTiles || !rightMouseDown || wasRightMouseDown || !(screen instanceof LOTRGuiMap)) {
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
        selectDestinationAt(map, mouseX, mouseY);
        wasRightMouseDown = rightMouseDown;
    }

    /** Coordinate adapter for the independently polled right-click destination path. */
    static void selectDestinationAt(LOTRGuiMap map, int mouseX, int mouseY) {
        if (shouldSkipMap(map) || mapUiOwnsPointer(map, mouseX, mouseY)) return;
        int tileColor = getHoveredTileColor(map, mouseX, mouseY);
        if (tileColor == 0) {
            return;
        }
        String tileId = tileIdsByColor.get(tileColor);
        if (isChoosingDestination()) {
            if (tileId != null && !destinationOriginTile.equals(KOMEConquestTile.normalizeId(tileId))) {
                clearRoutePreviewError();
                KOMEMinecraftClient.sendChat("/troops previewmove " + destinationCompanyId + " " + tileId);
            }
        }
    }

    @SubscribeEvent
    public void onMapScreenChange(net.minecraftforge.client.event.GuiOpenEvent event) {
        if(event.gui!=buildSpritesMap)clearEarlyBuilds();
        if(KOMEMapMarkerTrace.ENABLED&&!(event.gui instanceof LOTRGuiMap))KOMEMapMarkerTrace.close();
        if(KOMEMapProfiler.ENABLED&&!(event.gui instanceof LOTRGuiMap))KOMEMapProfiler.close();
        if ((radialMap != null && event.gui != radialMap) || (radialDragMap != null && event.gui != radialDragMap)) closeRadialInteraction();
    }

    private static void closeRadialInteraction() {
        battleContextRequest=null;
        closeUnits();closeRally();
        if(originWorkflow!=null)originWorkflow.clear();originWorkflow=null;
        if(rootJoin!=null)rootJoin.close();
        closeCompanySelector(); radialMenu.close(); radialMap = null; releaseRadialCapture();
    }

    /** Called after LOTR's native waypoint picker and controls, before its base GUI handler. */
    public static boolean onMapMouseClicked(LOTRGuiMap map, int mouseX, int mouseY, int button) {
        validateCompanySelectorLifecycle();
        if (button == 0) releaseRadialCapture(); // A fresh press starts a new gesture, even between render frames.
        if (shouldSkipMap(map) || !showConquestTiles || isChoosingDestination() || isPreviewingRoute()
                || !viewport(map).contains(mouseX, mouseY) || mapUiOwnsPointer(map, mouseX, mouseY)) return false;
        if (button == 1) {
            int color = getHoveredTileColor(map, mouseX, mouseY);
            String tile = tileIdsByColor.get(color);
            // A gap or unusable anchor leaves the existing interaction intact.
            return openRadialAt(map,tile);
        }
        return false;
    }

    private static boolean openRadialAt(LOTRGuiMap map,String tile) {
        if(!KOMEConquestTile.isCanonicalTileId(tile)||radialAnchorMapPosition(tile)==null)return false;
        closeRadialInteraction();radialMenu.open(tile);radialMap=map;refreshRootJoinBattle();return true;
    }
    /** Small future notification seam: an already-open map and a currently known tile only. */
    public static boolean focusRadialForTile(LOTRGuiMap map,String tile) {
        if(map==null||KOMEMinecraftClient.get()==null||KOMEMinecraftClient.currentScreen()!=map||!showConquestTiles||shouldSkipMap(map)
                ||isChoosingDestination()||isPreviewingRoute()||!tileColorsById.containsKey(tile))return false;
        double[] anchor=radialAnchorMapPosition(tile);if(anchor==null)return false;
        try {
            setMapFieldValue(map,"posX",(float)anchor[0]);setMapFieldValue(map,"posY",(float)anchor[1]);
            setMapFieldValue(map,"prevPosX",(float)anchor[0]);setMapFieldValue(map,"prevPosY",(float)anchor[1]);
            setMapFieldValue(map,"posXMove",0);setMapFieldValue(map,"posYMove",0);
        } catch(Exception unavailable){return false;}
        return openRadialAt(map,tile);
    }
    /** Explicit refresh seam; called on open/return, never by a render tick or polling timer. */
    public static void refreshRootJoinBattle() {
        if(radialMap==null||radialMenu.state()!=KOMEMapRadialMenu.State.ROOT||KOMEMinecraftClient.get()==null||KOMEMinecraftClient.currentScreen()!=radialMap)return;
        if(!(kome.common.KOMEAddon.proxy instanceof KOMEClientProxy))return;
        KOMECompanyDestinationClientState state=((KOMEClientProxy)kome.common.KOMEAddon.proxy).destinationOptions();
        if(rootJoin==null||!rootJoin.boundTo(state))rootJoin=new KOMERootJoinBattle(state);
        kome.common.network.KOMEPacketJoinBattleViewRequest request=rootJoin.open(radialMenu.tileId());
        syncRootJoin();
        if(request!=null)KOMEPacketHandler.network.sendToServer(request);
    }
    private static void syncRootJoin() {
        boolean active=rootJoin!=null&&radialMap!=null&&radialMenu.state()==KOMEMapRadialMenu.State.ROOT
            &&radialMenu.tileId().equals(rootJoinTile());
        long now=previewClock();boolean available=active&&rootJoin.available(now);
        boolean busy=active&&rootJoin.chooserPending();
        radialMenu.rootJoin(available||busy,available&&rootJoin.armed(now),active?rootJoin.status():"",busy);
    }
    private static String rootJoinTile(){return rootJoin==null?"":rootJoin.tile();}
    private static long previewClock() { return System.nanoTime() / 1000000L; }

    private static void closeCompanySelector() {
        clearDirectJoin();
        if (companySelector != null) companySelector.close();
        companySelector = null; companyPreview = KOMECompanyDestinationPreview.EMPTY;
    }
    private static void clearDirectJoin() {
        if(directJoinScreen!=null)directJoinScreen.onGuiClosed();
        directJoinScreen=null;directJoinViewCorrelation=null;directJoinSession=null;directJoinFromRoot=false;
    }
    private static boolean companySelectorOpen() { return companySelector != null && companySelector.isOpen(); }
    private static void validateCompanySelectorLifecycle() {
        syncRootJoin();
        if(unitsBrowser!=null&&!unitsBrowser.active()){closeRadialInteraction();return;}
        if(rally!=null&&!rally.active()){closeRadialInteraction();return;}
        syncRally();
        if(originWorkflow!=null&&!originWorkflow.active()){closeRadialInteraction();return;}
        if (companySelector != null && !companySelector.isOpen()) closeRadialInteraction();
        if(directJoinScreen!=null&&!(directJoinFromRoot
                ?rootJoin!=null&&rootJoin.deploying()&&java.util.Objects.equals(directJoinSession,rootJoin.session())
                :companySelector!=null&&companySelector.ownsJoinContinuation(directJoinSession)))clearDirectJoin();
    }
    private static void openCompanySelector(LOTRGuiMap map) {
        if (companySelectorOpen()) return;
        if (!(kome.common.KOMEAddon.proxy instanceof KOMEClientProxy)) return;
        closeCompanySelector();
        battleContextRequest=null;
        KOMEClientProxy proxy = (KOMEClientProxy)kome.common.KOMEAddon.proxy;
        companySelector = new KOMECompanyDestinationSelector(proxy.destinationOptions());
        double[] anchor = radialAnchorMapPosition(radialMenu.tileId());
        KOMEMapViewport view = viewport(map);
        selectorOnLeft = anchor != null && view.screenX(anchor[0]) > (view.left + view.right) / 2;
        sendDestinationRequest(companySelector.open(radialMenu.tileId()));
    }
    private static boolean unitsActive(){return unitsBrowser!=null&&unitsBrowser.active();}
    private static KOMECompanyDestinationPanel.Layout unitsLayout(LOTRGuiMap map){return KOMECompanyUnitsPanel.layout(viewport(map),selectorOnLeft);}
    private static void closeUnits(){if(unitsBrowser!=null)unitsBrowser.clear();unitsBrowser=null;}
    private static void sendUnits(kome.common.network.KOMEPacketCompanyOriginRequest request){if(request!=null)KOMEPacketHandler.network.sendToServer(request);}
    private static void openUnits(){
        if(!(kome.common.KOMEAddon.proxy instanceof KOMEClientProxy))return;
        closeCompanySelector();closeUnits();battleContextRequest=null;
        unitsBrowser=new KOMECompanyUnitsBrowser(((KOMEClientProxy)kome.common.KOMEAddon.proxy).destinationOptions());
        selectorOnLeft=false;sendUnits(unitsBrowser.open(radialMenu.tileId()));
    }
    public static void acceptCompanySourceOptions(kome.common.data.KOMECompanySourceOptions response){
        validateCompanySelectorLifecycle();
        if(radialMap!=null&&KOMEMinecraftClient.currentScreen()==radialMap&&unitsActive())unitsBrowser.accept(response,previewClock());
    }
    private static boolean unitsInput(LOTRGuiMap map,int x,int y,int button){
        KOMECompanyDestinationPanel.Layout layout=unitsLayout(map);
        if(!viewport(map).contains(x,y)||!layout.panel.contains(x,y))return false;
        if(button==0){
            KOMECompanyDestinationPanel.Control control=layout.controlAt(x,y);
            if(control==KOMECompanyDestinationPanel.Control.BACK){closeUnits();refreshRootJoinBattle();}
            else if(control==KOMECompanyDestinationPanel.Control.CLOSE)closeRadialInteraction();
            else if(control==KOMECompanyDestinationPanel.Control.CLEAR)unitsBrowser.clearSelection();
            else if(control==KOMECompanyDestinationPanel.Control.RETRY&&!unitsBrowser.loading())sendUnits(unitsBrowser.refresh());
            else if(control==KOMECompanyDestinationPanel.Control.ROWS_UP||control==KOMECompanyDestinationPanel.Control.ROWS_DOWN)
                sendUnits(unitsBrowser.scroll(control==KOMECompanyDestinationPanel.Control.ROWS_UP?-1:1,layout.capacity,previewClock()));
            else if(control==null)unitsBrowser.select(layout.rowAt(x,y,unitsBrowser.rows().size()-unitsBrowser.offset()));
            radialLeftCaptured=true;radialDragMap=map;
        }
        return true;
    }
    private static boolean rallyActive(){return rally!=null&&rally.active();}
    private static void sendRally(kome.common.network.KOMEPacketCompanyRallyRequest request){if(request!=null)KOMEPacketHandler.network.sendToServer(request);}
    private static void syncRally(){radialMenu.rally(rallyActive()?rally.label():"Rally Here",!rallyActive()||rally.enabled(),rallyActive()?rally.summary():"");}
    private static void closeRally(){
        if(rally!=null)sendRally(rally.cancel());rally=null;radialMenu.rally("Rally Here",true,"");
    }
    private static void clickRally(){
        if(rallyActive()&&rally.canConfirm()){sendRally(rally.confirm());syncRally();return;}
        if(rallyActive()&&!rally.canRefresh())return;
        if(!(kome.common.KOMEAddon.proxy instanceof KOMEClientProxy))return;
        closeCompanySelector();closeRally();battleContextRequest=null;
        rally=new KOMECompanyRallyClient(((KOMEClientProxy)kome.common.KOMEAddon.proxy).destinationOptions());
        sendRally(rally.open(radialMenu.tileId()));syncRally();
    }
    public static void acceptCompanyRally(kome.common.data.KOMECompanyRallySnapshot snapshot){
        validateCompanySelectorLifecycle();
        if(radialMap==null||KOMEMinecraftClient.currentScreen()!=radialMap||!rallyActive()||!rally.accept(snapshot))return;
        syncRally();
        if(snapshot.phase==kome.common.data.KOMECompanyRallySnapshot.Phase.DONE&&KOMEMinecraftClient.player()!=null)
            KOMEMinecraftClient.player().addChatMessage(new net.minecraft.util.ChatComponentText(snapshot.message));
    }
    private static boolean originActive(){return originWorkflow!=null&&originWorkflow.active();}
    static String originSourceTile(LOTRGuiMap map){return radialMap==map&&originActive()?originWorkflow.source():"";}
    private static boolean originPicking(){return originActive()&&originWorkflow.stage()==KOMECompanyOriginWorkflow.Stage.PICK;}
    private static void openOriginSelector(LOTRGuiMap map){
        if(!(kome.common.KOMEAddon.proxy instanceof KOMEClientProxy))return;
        closeCompanySelector();battleContextRequest=null;
        originWorkflow=new KOMECompanyOriginWorkflow(((KOMEClientProxy)kome.common.KOMEAddon.proxy).destinationOptions());
        selectorOnLeft=false;originWorkflow.open(radialMenu.tileId());
    }
    private static KOMECompanyDestinationPanel.Rect originPrompt(LOTRGuiMap map){
        KOMEMapViewport v=viewport(map);return new KOMECompanyDestinationPanel.Rect((int)v.left+8,(int)v.top+8,Math.min(340,(int)(v.right-v.left)-16),56);
    }
    private static void drawOriginPrompt(LOTRGuiMap map,int mx,int my){
        KOMECompanyDestinationPanel.Rect p=originPrompt(map);net.minecraft.client.gui.FontRenderer font=KOMEMinecraftClient.fontRenderer();
        KOMEGuiTheme.drawSubPanel(p.x,p.y,p.width,p.height);
        font.drawString(KOMEGuiTheme.trimToWidth(font,"Choose destination from "+originWorkflow.source(),p.width-16),p.x+8,p.y+6,KOMEGuiTheme.COLOR_GOLD);
        font.drawString(KOMEGuiTheme.trimToWidth(font,"Source "+originWorkflow.source()+" | Right-click a tile",p.width-16),p.x+8,p.y+20,KOMEGuiTheme.COLOR_TEXT_LIGHT);
        font.drawString("Back",p.x+8,p.y+40,KOMEGuiTheme.COLOR_TEXT_LIGHT);
        font.drawString("Cancel",p.x+p.width-52,p.y+40,KOMEGuiTheme.COLOR_TEXT_LIGHT);
    }
    /** Right-click chooses a canonical tile at the current pan/zoom. Left drag and wheel stay native. */
    private static boolean originInput(LOTRGuiMap map,int x,int y,int button){
        if(!originPicking())return false;
        KOMEMapViewport view=viewport(map);if(!view.contains(x,y))return false;
        if(originPicking()){
            KOMECompanyDestinationPanel.Rect p=originPrompt(map);
            if(p.contains(x,y)){
                if(button==0&&y>=p.y+34){
                    if(x>=p.x+p.width/2)closeRadialInteraction();else {originWorkflow.clear();originWorkflow=null;}
                    radialLeftCaptured=true;radialDragMap=map;
                }
                return true;
            }
            if(button!=1||isOverMapControl(map,x,y))return false;
            String tile=tileIdsByColor.get(getHoveredTileColor(map,x,y));
            if(tile!=null)chooseOriginDestination(map,tile);
            return true;
        }
        return false;
    }
    static void chooseOriginDestination(LOTRGuiMap map,String tile){
        if(!originPicking()||radialMap!=map||!kome.common.data.KOMEConquestTile.isCanonicalTileId(tile))return;
        openOriginCompanies(tile);
    }
    private static void openOriginCompanies(String tile){
        String source=originWorkflow.source();closeCompanySelector();
        KOMEClientProxy proxy=(KOMEClientProxy)kome.common.KOMEAddon.proxy;
        companySelector=new KOMECompanyDestinationSelector(proxy.destinationOptions());
        kome.common.network.KOMEPacketCompanyDestinationRequest query=companySelector.openSource(source,tile);
        originWorkflow.chooseDestination(tile);companyPreview=KOMECompanyDestinationPreview.EMPTY;sendDestinationRequest(query);
    }
    private static void sendDestinationRequest(kome.common.network.KOMEPacketCompanyDestinationRequest request) {
        syncCompanyPreview();
        if (request != null) KOMEPacketHandler.network.sendToServer(companySelector!=null&&companySelector.originQuery()?companySelector.originRequest(request):request);
    }
    public static void acceptCompanyDestinationOptions(kome.common.data.KOMECompanyDestinationOptions response) {
        if (companySelector != null && companySelector.accept(response, previewClock())) {
            if(originActive()&&originWorkflow.stage()==KOMECompanyOriginWorkflow.Stage.PREVIEW&&companySelector.selected()==null&&companySelector.phase()!=KOMECompanyDestinationSelector.Phase.LOADING)originWorkflow.chooser();
            syncCompanyPreview();
            kome.common.network.KOMEPacketCompanyDestinationRequest followup=companySelector.takeBrowseFollowup();
            if(followup!=null)sendDestinationRequest(followup);
        }
    }
    public static void acceptCompanyMovementResult(kome.common.data.KOMECompanyMovementResult result) {
        if(companySelector==null)return;
        kome.common.network.KOMEPacketCompanyDestinationRequest refresh=companySelector.acceptMovement(result);
        companyPreview=KOMECompanyDestinationPreview.EMPTY;
        if(refresh!=null)sendDestinationRequest(refresh);
        kome.common.network.KOMEPacketJoinBattleViewRequest join=companySelector.joinViewRequest();
        if(join!=null)KOMEPacketHandler.network.sendToServer(join);
    }
    public static void cancelMovementJoinIntent(kome.common.data.KOMECompanyMovementResult result) {
        if(companySelector!=null&&companySelector.isDuplicateArrival(result))return;
        cancelMovementJoinIntent();
    }
    public static void cancelMovementJoinIntent() {
        clearDirectJoin();
        if(companySelector!=null)companySelector.cancelJoinIntent();
    }
    public static void acceptJoinBattleView(kome.common.network.KOMEPacketJoinBattleViewResponse view) {
        validateCompanySelectorLifecycle();
        if(radialMap==null||KOMEMinecraftClient.currentScreen()!=radialMap)return;
        if(rootJoin!=null&&rootJoin.owns(view.correlation)) {
            if(radialMenu.state()!=KOMEMapRadialMenu.State.ROOT)return;
            KOMERootJoinBattle.Reception result=rootJoin.accept(view,previewClock());syncRootJoin();
            if(result==KOMERootJoinBattle.Reception.OPEN_CHOOSER) {
                closeRadialInteraction();KOMEMinecraftClient.displayGui(new kome.client.gui.KOMEGuiJoinBattle(view));
            } else if(result==KOMERootJoinBattle.Reception.DIRECT_JOIN) {
                kome.client.gui.KOMEGuiJoinBattle screen=new kome.client.gui.KOMEGuiJoinBattle(view);
                directJoinScreen=screen;directJoinFromRoot=true;
                directJoinViewCorrelation=view.correlation;directJoinSession=rootJoin.session();
                if(!screen.beginRootEntry(radialMap)) {
                    directJoinScreen=null;directJoinFromRoot=false;directJoinViewCorrelation=null;directJoinSession=null;
                    closeRadialInteraction();KOMEMinecraftClient.displayGui(screen);
                }
            } else if(result==KOMERootJoinBattle.Reception.DENIED&&KOMEMinecraftClient.player()!=null)
                KOMEMinecraftClient.player().addChatMessage(new net.minecraft.util.ChatComponentText(rootJoin.status()));
            return;
        }
        if(battleContextRequest!=null&&battleContextRequest.correlation.equals(view.correlation)) {
            String tile=battleContextRequest.tileId;battleContextRequest=null;
            if(radialMenu.state()==KOMEMapRadialMenu.State.BATTLE&&tile.equals(view.tileId)&&tile.equals(radialMenu.tileId()))
                radialMenu.battleContext(!view.conflictId.isEmpty()&&view.conflictState!=null&&view.conflictState!=kome.common.data.KOMEConflictRecord.State.ENDED,knownHostileTile(tile));
            return;
        }
        if(radialMenu.state()==KOMEMapRadialMenu.State.ROOT&&rootJoin!=null&&rootJoin.current()){
            rootJoin.accept(view,previewClock());syncRootJoin();validateCompanySelectorLifecycle();return;
        }
        if(companySelector==null)return;
        if(directJoinScreen!=null) {
            if(!view.correlation.equals(directJoinViewCorrelation)||!directJoinScreen.matchesEntryView(view))cancelMovementJoinIntent();
            return;
        }
        String company=companySelector.acceptJoinView(view);if(company==null)return;
        kome.client.gui.KOMEGuiJoinBattle screen=new kome.client.gui.KOMEGuiJoinBattle(view,company);
        // Retain the map/selector session until the direct request completes or is cancelled.
        directJoinScreen=screen;directJoinViewCorrelation=view.correlation;directJoinSession=companySelector.sessionId();
        if(screen.beginPreferredEntry(radialMap)) {
            companySelector.joinStatus("Company moved successfully. Joining battle...");return;
        }
        directJoinScreen=null;directJoinViewCorrelation=null;directJoinSession=null;directJoinFromRoot=false;
        closeRadialInteraction();
        KOMEMinecraftClient.displayGui(screen);
    }
    /** Proxy has already checked the connection; the selector still owns movement/view correlation. */
    public static boolean acceptDirectJoinResult(kome.common.network.KOMEPacketJoinBattleSelectionResult result) {
        boolean owned=directJoinScreen!=null;
        validateCompanySelectorLifecycle();
        if(directJoinScreen==null){
            if(owned)KOMEJoinBattleDiagnostics.event("retained map/direct handler discarded result: continuation/session invalidated");
            return owned;
        }
        boolean continuation=directJoinFromRoot
            ?rootJoin!=null&&rootJoin.deploying()&&java.util.Objects.equals(directJoinSession,rootJoin.session())
            :companySelector!=null&&companySelector.ownsJoinContinuation(directJoinSession);
        if(radialMap==null||KOMEMinecraftClient.currentScreen()!=radialMap||!continuation) {
            KOMEJoinBattleDiagnostics.event("retained map/direct handler discarded result; close skipped: screen/continuation ownership mismatch");
            clearDirectJoin();return true;
        }
        KOMEJoinBattleDiagnostics.event(directJoinFromRoot?"result consumed by root direct handler":"result consumed by movement direct handler");
        kome.client.gui.KOMEGuiJoinBattle screen=directJoinScreen;
        if(screen.acceptSelection(result)) {
            if(result.status==kome.common.network.KOMEPacketJoinBattleSelectionResult.Status.REJECTED) {
                directJoinScreen=null;directJoinViewCorrelation=null;directJoinSession=null;directJoinFromRoot=false;
                screen.useOwnScreen();closeRadialInteraction();KOMEMinecraftClient.displayGui(screen);
            }else if(result.status==kome.common.network.KOMEPacketJoinBattleSelectionResult.Status.ENTRY_PENDING&&!directJoinFromRoot) {
                companySelector.joinStatus("Company moved successfully. "+screen.status());
            }
        }
        return true;
    }
    private static boolean knownHostileTile(String tile) {
        net.minecraft.client.Minecraft client=KOMEMinecraftClient.get();
        net.minecraft.entity.player.EntityPlayer player=client==null?null:client.thePlayer;
        if(player==null)return false;
        lotr.common.fac.LOTRFaction pledge=lotr.common.LOTRLevelData.getData(player).getPledgeFaction();
        String owner=KOMEClientData.INSTANCE.conquestRenderOwners().get(tile);
        if(pledge==null||!pledge.isPlayableAlignmentFaction()||owner==null||owner.isEmpty())return false;
        return knownHostileOwner(pledge.codeName(),owner);
    }
    static boolean knownHostileOwner(String pledge,String owner) {
        lotr.common.fac.LOTRFaction faction=kome.common.data.KOMEAlliance.findLotrFaction(pledge);
        if(faction==null||!faction.isPlayableAlignmentFaction()||owner==null||owner.isEmpty()||kome.common.data.KOMEAlliance.findLotrFaction(owner)==null)return false;
        kome.common.data.KOMEDiplomacyRelation relation=kome.common.data.KOMEDiplomacyService.getRelation(KOMEClientData.INSTANCE,pledge,owner);
        return relation==kome.common.data.KOMEDiplomacyRelation.ENEMIES||relation==kome.common.data.KOMEDiplomacyRelation.MORTAL_ENEMIES;
    }
    /** Discard cached conflict wording; refresh only on an explicit entry/return to Battle. */
    private static void refreshBattleContext() {
        battleContextRequest=null;
        radialMenu.battleContext(false,knownHostileTile(radialMenu.tileId()));
        if(kome.common.KOMEAddon.proxy instanceof KOMEClientProxy&&((KOMEClientProxy)kome.common.KOMEAddon.proxy).destinationOptions().isConnected()) {
            battleContextRequest=new kome.common.network.KOMEPacketJoinBattleViewRequest(radialMenu.tileId(),java.util.UUID.randomUUID());
            KOMEPacketHandler.network.sendToServer(battleContextRequest);
        }
    }
    private static KOMECompanyDestinationPanel.Layout selectorLayout(LOTRGuiMap map) {
        KOMECompanyDestinationPanel.Layout layout = new KOMECompanyDestinationPanel.Layout(viewport(map), selectorOnLeft,companySelectorOpen()&&companySelector.canConfirmAndJoin());
        if(companySelectorOpen()&&companySelector.originQuery())for(KOMECompanyDestinationPanel.Control c:new KOMECompanyDestinationPanel.Control[]{KOMECompanyDestinationPanel.Control.FILTER_ALL,KOMECompanyDestinationPanel.Control.FILTER_READY,KOMECompanyDestinationPanel.Control.FILTER_ARRIVE})layout.controls.remove(c);
        if(originActive()&&originWorkflow.stage()==KOMECompanyOriginWorkflow.Stage.SOURCE)
            for(KOMECompanyDestinationPanel.Control c:new KOMECompanyDestinationPanel.Control[]{KOMECompanyDestinationPanel.Control.CONFIRM,KOMECompanyDestinationPanel.Control.CONFIRM_JOIN,KOMECompanyDestinationPanel.Control.CLEAR})layout.controls.remove(c);
        if (companySelectorOpen()) companySelector.scrollRows(0,layout.capacity);
        return layout;
    }

    /** Only visible UI cells/rectangles run before native picking; radial gaps/center remain pass-through. */
    static boolean radialOwnsPointer(LOTRGuiMap map,int x,int y) {
        return map!=null && !companySelectorOpen() && !originActive() && !unitsActive() && showConquestTiles && !shouldSkipMap(map)
            && !isChoosingDestination() && !isPreviewingRoute() && radialHit(map,x,y)>=0;
    }
    private static boolean onMapRadialSegmentMouseClicked(LOTRGuiMap map,int mouseX,int mouseY,int button) {
        if (!radialOwnsPointer(map,mouseX,mouseY)) return false;
        if (button!=0) return true; // Segment-owned auxiliary clicks never reach the map underneath.
        int index=radialHit(map,mouseX,mouseY);
        KOMEMapRadialMenu.Action clicked=radialMenu.segments().get(index).action;
        if(clicked!=KOMEMapRadialMenu.Action.RALLY_HERE)closeRally();
        KOMEMapRadialMenu.Action action = radialMenu.activate(index);
        if(clicked==KOMEMapRadialMenu.Action.JOIN_BATTLE) {
            kome.common.network.KOMEPacketJoinBattleViewRequest request=rootJoin==null?null:rootJoin.click(previewClock());
            syncRootJoin();if(request!=null)KOMEPacketHandler.network.sendToServer(request);
        } else if(rootJoin!=null)rootJoin.cancelAction();
        syncRootJoin();
        if(action==KOMEMapRadialMenu.Action.BACK)refreshRootJoinBattle();
        if (action == KOMEMapRadialMenu.Action.INFO)
            KOMEPacketHandler.network.sendToServer(new KOMEPacketConquestOpenCapture(radialMenu.tileId()));
        if(action==KOMEMapRadialMenu.Action.COMPANIES)openUnits();
        if(action==KOMEMapRadialMenu.Action.RALLY_HERE)clickRally();
        if (action == KOMEMapRadialMenu.Action.MOVE_TO)openOriginSelector(map);
        if (action == KOMEMapRadialMenu.Action.MOVE_HERE) openCompanySelector(map);
        if(action==KOMEMapRadialMenu.Action.REINFORCE||action==KOMEMapRadialMenu.Action.ATTACK)openCompanySelector(map);
        if(action==KOMEMapRadialMenu.Action.BATTLE)refreshBattleContext();
        if (action == KOMEMapRadialMenu.Action.CLOSE) closeRadialInteraction();
        // Keep the closing press captured until release, even though the interaction itself is now cleared.
        radialLeftCaptured = true; radialDragMap = map;
        return true; // Disabled segments consume their own click without issuing any order/query.
    }

    /** Selector panel first, then the rendered radial button cells, before native picking. */
    public static boolean onMapSelectorMouseClicked(LOTRGuiMap map, int mouseX, int mouseY, int button) {
        validateCompanySelectorLifecycle();
        if (button == 0) releaseRadialCapture();
        if (!shouldSkipMap(map) && handleTerritoryControl(map, mouseX, mouseY, button)) return true;
        if(unitsActive()&&radialMap==map&&!shouldSkipMap(map)&&unitsInput(map,mouseX,mouseY,button))return true;
        if(originActive()&&radialMap==map&&!shouldSkipMap(map)&&originInput(map,mouseX,mouseY,button))return true;
        if (!selectorOwnsPointer(map,mouseX,mouseY)) return onMapRadialSegmentMouseClicked(map,mouseX,mouseY,button);
        KOMECompanyDestinationPanel.Layout layout = selectorLayout(map);
        if (!layout.panel.contains(mouseX,mouseY)) return false;
        if (button == 0) {
            KOMECompanyDestinationPanel.Control control = layout.controlAt(mouseX,mouseY);
            if (control != null && KOMECompanyDestinationPanel.enabled(control,companySelector,layout,previewClock())) {
                switch (control) {
                    case ROWS_UP:
                        browseCompanies(-1,layout.capacity);break;
                    case ROWS_DOWN:
                        browseCompanies(1,layout.capacity);break;
                    case FILTER_ALL:case FILTER_READY:case FILTER_ARRIVE:
                        KOMECompanyDestinationSelector.Filter filter=control==KOMECompanyDestinationPanel.Control.FILTER_ALL?KOMECompanyDestinationSelector.Filter.ALL:
                            control==KOMECompanyDestinationPanel.Control.FILTER_READY?KOMECompanyDestinationSelector.Filter.READY:KOMECompanyDestinationSelector.Filter.ARRIVE_NOW;
                        sendDestinationRequest(companySelector.setFilter(filter));
                        syncCompanyPreview();break;
                    case CONFIRM:
                        kome.common.network.KOMEPacketCompanyMovementRequest order=companySelector.confirm();
                        companyPreview=KOMECompanyDestinationPreview.EMPTY;
                        if(order!=null)KOMEPacketHandler.network.sendToServer(order);
                        break;
                    case CONFIRM_JOIN:
                        kome.common.network.KOMEPacketCompanyMovementRequest combined=companySelector.confirmJoinClick(previewClock());
                        syncCompanyPreview();
                        if(combined!=null)KOMEPacketHandler.network.sendToServer(combined);
                        break;
                    case RETRY:
                        if(originActive()){companySelector.clearSelection();originWorkflow.chooser();}
                        sendDestinationRequest(companySelector.retry(previewClock()));companyPreview=KOMECompanyDestinationPreview.EMPTY;break;
                    case BACK:
                        if(originWorkflow!=null){
                            if(originWorkflow.stage()==KOMECompanyOriginWorkflow.Stage.PREVIEW)openOriginCompanies(originWorkflow.destination());
                            else {closeCompanySelector();originWorkflow.pickAgain();}break;
                        }
                        closeCompanySelector();
                        if(radialMenu.state()==KOMEMapRadialMenu.State.BATTLE)refreshBattleContext();
                        break;
                    case CLEAR: companySelector.clearSelection();if(originActive())originWorkflow.chooser();companyPreview=KOMECompanyDestinationPreview.EMPTY; break;
                    case CLOSE: closeRadialInteraction(); break;
                }
            } else if (control == null) {
                int index = layout.rowAt(mouseX,mouseY,companySelector.rows().size()-companySelector.rowOffset());
                kome.common.data.KOMECompanyDestinationOptions.Row row=companySelector.visibleRow(index);
                if (row != null) {
                    sendDestinationRequest(companySelector.selectForBrowse(row.companyId));
                    if(originActive()){if(companySelector.isSelected(row.companyId))originWorkflow.preview(row);else originWorkflow.chooser();}
                    companyPreview=KOMECompanyDestinationPreview.of(companySelector.selectedRoute(),KOMEConquestMapOverlay::radialAnchorMapPosition);
                }
            }
            radialLeftCaptured=true;radialDragMap=map;
        }
        return true;
    }

    static boolean selectorOwnsPointer(LOTRGuiMap map,int mouseX,int mouseY) {
        return map!=null && radialMap==map && companySelectorOpen() && !shouldSkipMap(map)
            && viewport(map).contains(mouseX,mouseY) && selectorLayout(map).panel.contains(mouseX,mouseY);
    }
    /** Native wheel poll seam; map zoom keeps the original wheel value outside this panel. */
    public static int mapMouseWheel(LOTRGuiMap map) {
        int delta=Mouse.getEventDWheel();
        net.minecraft.client.Minecraft client=KOMEMinecraftClient.get();
        if (client==null || client.displayWidth<=0 || client.displayHeight<=0) return delta;
        int x=Mouse.getEventX()*map.width/client.displayWidth;
        int y=map.height-Mouse.getEventY()*map.height/client.displayHeight-1;
        return selectorWheelDelta(map,x,y,delta);
    }
    static int selectorWheelDelta(LOTRGuiMap map,int x,int y,int delta) {
        return onMapSelectorWheel(map,x,y,delta)?0:delta;
    }
    static boolean onMapSelectorWheel(LOTRGuiMap map,int x,int y,int delta) {
        validateCompanySelectorLifecycle();
        if(unitsActive()&&radialMap==map&&unitsLayout(map).panel.contains(x,y)){if(delta!=0)sendUnits(unitsBrowser.scroll(delta>0?-1:1,unitsLayout(map).capacity,previewClock()));return true;}
        if (!selectorOwnsPointer(map,x,y)) return false;
        if (delta!=0) browseCompanies(delta>0?-1:1,selectorLayout(map).capacity);
        return true;
    }

    private static void browseCompanies(int direction,int capacity) {
        sendDestinationRequest(companySelector.browse(direction,capacity,previewClock()));
        syncCompanyPreview();
    }
    private static void syncCompanyPreview() {
        companyPreview=KOMECompanyDestinationPreview.of(companySelector.selectedRoute(),KOMEConquestMapOverlay::radialAnchorMapPosition);
    }
    static String selectorDestinationTile(LOTRGuiMap map) {
        return radialMap==map?(unitsActive()?unitsBrowser.tile():rallyActive()?rally.destination():companySelectorOpen()?companySelector.destination():""):"";
    }
    private static void drawSelectorDestination(LOTRGuiMap map) {
        drawTileCue(map,selectorDestinationTile(map));
    }
    private static void drawTileCue(LOTRGuiMap map,String tile) {
        Integer color=tileColorsById.get(tile);
        if(mapBorders==null||color==null)return;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GeometryBatch batch=new GeometryBatch(Tessellator.instance);
            mapBorders.drawDestination(viewport(map),color,batch);batch.finish();
        } finally {GL11.glPopAttrib();}
    }
    private static void drawCompanyDestinationPreview(LOTRGuiMap map) {
        if (radialMap != map || !companySelectorOpen() || companyPreview.points.isEmpty()) return;
        KOMEMapViewport view=viewport(map);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_SCISSOR_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            KOMEGuiTheme.enableScissor(KOMEMinecraftClient.get(),(int)view.left,(int)view.top,(int)(view.right-view.left),(int)(view.bottom-view.top));
            for(int i=0;i<companyPreview.points.size();i++) {
                KOMECompanyDestinationPreview.Point point=companyPreview.points.get(i);
                if(point==null)continue;
                int x=(int)Math.round(view.screenX(point.x)),y=(int)Math.round(view.screenY(point.y));
                if(i>0) {
                    KOMECompanyDestinationPreview.Point previous=companyPreview.points.get(i-1);
                    if(previous!=null) {
                        int px=(int)Math.round(view.screenX(previous.x)),py=(int)Math.round(view.screenY(previous.y));
                        drawLine(px,py,x,y,ROUTE_LINE_SHADOW,2.25F);drawLine(px,py,x,y,ROUTE_LINE_COLOR,1.25F);
                    }
                }
                if(i==0)drawRouteOriginMarker(x,y);
                else if(i==companyPreview.points.size()-1)drawRouteDestinationMarker(x,y);
                else drawRouteIntermediatePoint(x,y);
            }
        } finally { GL11.glPopAttrib(); }
    }

    /** Native picker success, for ANY waypoint. No visibility/travel rules are duplicated here. */
    public static void onNativeWaypointClick(LOTRGuiMap map) {
        if(originActive())return;
        if (radialMap == map && radialMenu.isOpen()) closeRadialInteraction();
    }

    static double radialScale() {
        net.minecraft.client.Minecraft client = KOMEMinecraftClient.get();
        if (client == null || client.gameSettings == null) return 1;
        net.minecraft.client.gui.ScaledResolution resolution =
            new net.minecraft.client.gui.ScaledResolution(client, client.displayWidth, client.displayHeight);
        return KOMEMapRadialMenu.scaleForGuiFactor(resolution.getScaleFactor());
    }

    /** Escape dismisses the interaction first; native map/waypoint keys otherwise remain untouched. */
    public static boolean onMapKeyTyped(LOTRGuiMap map, int key) {
        if (key != Keyboard.KEY_ESCAPE || radialMap != map || !radialMenu.isOpen() || shouldSkipMap(map)) return false;
        closeRadialInteraction(); return true;
    }

    /** LOTR polls drag state during drawing, independently of mouseClicked. Capture only a segment-origin gesture. */
    public static boolean isMapDragButtonDown(int button, LOTRGuiMap map) {
        return mapDragButtonDown(map, Mouse.isButtonDown(button));
    }
    static boolean mapDragButtonDown(LOTRGuiMap map, boolean down) {
        if (!down) releaseRadialCapture();
        return down && !(radialDragMap == map && radialLeftCaptured);
    }

    private static void releaseRadialCapture() { radialLeftCaptured = false; radialDragMap = null; }

    /** Presentation-only guard shared by native waypoint and KOME inspection renderers. */
    public static boolean suppressMapInspection(LOTRGuiMap map) {
        return map != null && radialMap == map && radialMenu.isOpen() && showConquestTiles && !shouldSkipMap(map);
    }

    private static int radialHit(LOTRGuiMap map, int mouseX, int mouseY) {
        syncRootJoin();
        if (radialMap != map || !radialMenu.isOpen() || !viewport(map).contains(mouseX, mouseY)) return -1;
        double[] anchor = radialAnchorMapPosition(radialMenu.tileId());
        if (anchor == null) return -1;
        KOMEMapViewport view = viewport(map);
        return radialMenu.hit(mouseX - view.screenX(anchor[0]), mouseY - view.screenY(anchor[1]), radialScale());
    }

    /** Same projected waypoint and raster-center sources used by the existing map markers, in map space. */
    static double[] radialAnchorMapPosition(String tileId) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        double[] waypoint = radialWaypointMapPosition(tile);
        if (waypoint != null) return waypoint;
        int[] center = tileCentersById.get(tile);
        if (center == null || tileRaster == null) return null;
        return new double[] {center[0] * (double) tileRaster.transform.mapWidth / tileRaster.width,
            center[1] * (double) tileRaster.transform.mapHeight / tileRaster.height};
    }

    private static double[] radialWaypointMapPosition(String tile) {
        KOMETileWaypointLink link = KOMEClientData.INSTANCE.tileWaypointLinksByTileId.get(tile);
        if (link != null && link.dimensionId == LOTRDimension.MIDDLE_EARTH.dimensionID) {
            lotr.common.world.map.LOTRWaypoint waypoint = link.resolveWaypoint();
            if (waypoint != null || link.waypointWorldX != 0 || link.waypointWorldZ != 0) {
                int x = waypoint == null ? link.waypointWorldX : waypoint.getXCoord();
                int z = waypoint == null ? link.waypointWorldZ : waypoint.getZCoord();
                return new double[] {x / (double) LOTRGenLayerWorld.scale + LOTRGenLayerWorld.originX,
                    z / (double) LOTRGenLayerWorld.scale + LOTRGenLayerWorld.originZ};
            }
        }
        return null;
    }

    private static boolean isOverMapControl(LOTRGuiMap map, int mouseX, int mouseY) {
        if (territoryControlAt(mouseX, mouseY) >= 0 || isOverConquestToggleButton(map, mouseX, mouseY) || isOverRiverBlockToggleButton(map, mouseX, mouseY)
                || isOverBridgeToggleButton(map, mouseX, mouseY) || isOverTroopToggleButton(map, mouseX, mouseY)
                || isOverToggleButton(toggleButtonX(4),toggleButtonY(4),mouseX,mouseY)) return true;
        for (Object object : (List) mapFieldValue(null, "mapWidgets")) {
            lotr.client.gui.LOTRGuiMapWidget widget = (lotr.client.gui.LOTRGuiMapWidget) object;
            if (widget.isMouseOver(mouseX - mapInt("mapXMin"), mouseY - mapInt("mapYMin"), mapInt("mapWidth"), mapInt("mapHeight"))) return true;
        }
        List buttons;
        try {
            if (mapButtonsField == null) mapButtonsField = cpw.mods.fml.relauncher.ReflectionHelper.findField(GuiScreen.class, "buttonList", "field_146292_n");
            buttons = (List) mapButtonsField.get(map);
        } catch (IllegalAccessException failure) { throw new IllegalStateException("Could not read native map controls", failure); }
        if (buttons != null) for (Object object : buttons) {
            net.minecraft.client.gui.GuiButton button = (net.minecraft.client.gui.GuiButton) object;
            if (button.visible && KOMEGuiTheme.isHovered(mouseX, mouseY, button.xPosition, button.yPosition, button.width, button.height)) return true;
        }
        return false;
    }

    /** Shared by event picking and independently polled destination selection. */
    private static boolean mapUiOwnsPointer(LOTRGuiMap map, int x, int y) {
        return isOverMapControl(map, x, y) || selectorOwnsPointer(map, x, y) || radialOwnsPointer(map, x, y)
            || unitsActive() || rallyActive() || originActive()
            || isOverRouteConfirmButton(map, x, y) || isOverRouteCancelButton(map, x, y);
    }

    private static int getHoveredTileColor(LOTRGuiMap map, int mouseX, int mouseY) {
        KOMEMapViewport view = viewport(map);
        if (!view.contains(mouseX, mouseY) || !ensureTileMaskLoaded()) return 0;
        double mapX = view.mapX(mouseX);
        double mapY = view.mapY(mouseY);
        KOMETileResolution resolved = tileAtMapPosition(LOTRDimension.MIDDLE_EARTH.dimensionID, mapX, mapY);
        Integer color = tileColorsById.get(resolved.tileId);
        return color == null ? 0 : color;
    }

    private static void updateMapViewportState(GuiScreen screen) {
        if (!(screen instanceof LOTRGuiMap)) {
            activeMapInstance = null;
            return;
        }
        LOTRGuiMap map = (LOTRGuiMap) screen;
        if (KOMEProgressionMapFocus.isObjectiveView(map)) return;
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
        KOMETileRasterSnapshot current = KOMETileWorldResolver.INSTANCE.snapshot().orElse(null);
        if (current == null) { clearTileGeometry(); return false; }
        if (tileRaster == current) return true;
        if (failedRaster == current) return false;
        clearTileGeometry();
        try {
            tileMaskPixels = current.copyArgbPixels();
            mapBorders = borderCache.get(current, tileMaskPixels);
            claimedTexture = new DynamicTexture(current.width, current.height);
            claimedTextureLocation = registerMapTexture(KOMEMinecraftClient.textureManager(), "kome_conquest_claimed", claimedTexture);
            tileIdsByColor.putAll(current.idsByColor());
            tileColorsById.putAll(current.colorsById());
            computeTileCenters(current.width, current.height);
            tileRaster = current;
            return true;
        } catch (RuntimeException e) {
            clearTileGeometry();
            failedRaster = current; // Retry only a new snapshot or explicit resource reload, not every frame.
            cpw.mods.fml.common.FMLLog.warning("KOME map geometry unavailable: %s", e.toString());
            return false;
        }
    }

    private static void clearTileGeometry() {
        KOMEMapProfiler.count(KOMEMapProfiler.Count.GEOMETRY_CLEAR);
        earlyTerritoryMap = null;
        warnedEarlyTerritoryFailure = false;
        clearEarlyGeography();warnedEarlyGeographyFailure=false;
        clearEarlyBuilds();warnedEarlyBuildFailure=false;
        releaseMapTexture(claimedTextureLocation, claimedTexture);
        claimedTextureLocation = null;
        claimedTexture = null;
        tileRaster = null;
        failedRaster = null;
        tileMaskPixels = null;
        if(mapBorders!=null)mapBorders.clearTerritoryFrames();
        mapBorders = null;
        borderCache.clear();
        tileIdsByColor.clear();
        tileColorsById.clear();
        tileCentersById.clear();
        renderedClaimRevision = -1;
        claimTextureState.clear();
        renderedPalette = null;
        renderedOpacity = null;
        territoryColors = java.util.Collections.emptyMap();
        territoryOwners = java.util.Collections.emptyMap();
        claimTextureDirty = true;
    }

    @Override
    public void onResourceManagerReload(net.minecraft.client.resources.IResourceManager resources) {
        if(KOMEMapMarkerTrace.ENABLED)KOMEMapMarkerTrace.close();
        KOMEMapProfiler.close();
        clearTileGeometry();
        releaseMapTexture(desertShadeTextureLocation, desertShadeTexture);
        releaseMapTexture(labelTextureLocation, labelTexture);
        desertShadeTextureLocation = labelTextureLocation = null;
        desertShadeTexture = labelTexture = null;
        KOMEMapMarkerArt.reload(resources);
        // Bundled authoritative geometry is not replaced by client resource packs.
    }

    // 1.7.10 TextureManager.deleteTexture leaves its object (and pixel array) registered.
    // Reuse three bounded slots and replace released objects with a pixel-free sentinel.
    private static final ITextureObject RELEASED_MAP_TEXTURE = new ITextureObject() {
        @Override public void loadTexture(IResourceManager resources) { }
        @Override public int getGlTextureId() { return 0; }
    };

    static ResourceLocation registerMapTexture(TextureManager manager, String name, DynamicTexture texture) {
        ResourceLocation location = new ResourceLocation("kome:dynamic/" + name);
        manager.loadTexture(location, texture);
        return location;
    }

    private static void releaseMapTexture(ResourceLocation location, DynamicTexture texture) {
        if (location == null && texture == null) return;
        releaseMapTexture(KOMEMinecraftClient.textureManager(), location, texture);
    }

    static void releaseMapTexture(TextureManager manager, ResourceLocation location, DynamicTexture texture) {
        // Also handles allocation/upload failure before a location was registered.
        if (texture != null) texture.deleteGlTexture();
        if (location != null) manager.loadTexture(location, RELEASED_MAP_TEXTURE);
    }

    private static ResourceLocation getDesertShadeTextureLocation() {
        if (desertShadeTextureLocation == null) {
            desertShadeTextureLocation = loadDynamicTexture("kome_conquest_desert_shade", DESERT_SHADE);
        }
        return desertShadeTextureLocation;
    }

    private static ResourceLocation getLabelTextureLocation() {
        if (labelTextureLocation == null) {
            labelTextureLocation = loadDynamicTexture("kome_conquest_labels", LABELS);
        }
        return labelTextureLocation;
    }

    private static ResourceLocation getBridgeMarkerTextureLocation() {
        return BRIDGE_MARKER_ART; // Whole RGBA canvas, including padding/partial alpha; resource-manager reloads apply.
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
            if (resource == DESERT_SHADE) {
                desertShadeTexture = texture;
            } else if (resource == LABELS) {
                labelTexture = texture;
            }
            return registerMapTexture(KOMEMinecraftClient.textureManager(), name, texture);
        } catch (Exception e) {
            return null;
        }
    }

    private static void computeTileCenters(int width, int height) {
        Map<Integer, long[]> totals = new HashMap<Integer, long[]>();
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

    private static void drawToggleButtons(LOTRGuiMap map, int mouseX, int mouseY) {
        String[] labels = {"Palette: " + territoryStyle.palette.label, "Territory: " + territoryStyle.mode.label,
            "Territory opacity: " + territoryStyle.opacity.percent + "% (full color)",
            "Individual tile borders: " + (territoryStyle.showTileBorders ? "On" : "Off"),
            showConquestTiles?"Hide conquest layer":"Show conquest layer",showRiverBlockMarkers?"Hide river blockers":"Show river blockers",
            showBridgeMarkers?"Hide bridge markers":"Show bridge markers",showTroopMarkers?"Hide troop markers":"Show troop markers",
            showBuildMarkers?"Hide Build markers":"Show Build markers"};
        boolean[] active={true,true,true,territoryStyle.showTileBorders,showConquestTiles,showRiverBlockMarkers,showBridgeMarkers,showTroopMarkers,showBuildMarkers};
        String tooltip=null;
        for(int control=0;control<9;control++) {
            int row=control<4?control:control-4,x=control<4?territoryControlX():toggleButtonX(row);
            int y=control<4?territoryControlY(row):toggleButtonY(row);
            boolean interactive=control<4||!isChoosingDestination();
            KOMEMapControlRenderer.draw(control,x,y,active[control],interactive,territoryStyle);
            if(isOverToggleButton(x,y,mouseX,mouseY)&&!mapControlCovered(map,mouseX,mouseY))
                tooltip=labels[control]+(interactive?"":" (unavailable while choosing a destination)");
        }
        if(tooltip!=null)KOMEMapControlRenderer.tooltip(map,tooltip,mouseX,mouseY);
    }

    private static int territoryControlX() { return mapInt("mapXMin") + 6; }
    private static int territoryControlY(int row) { return mapInt("mapYMin") + TOGGLE_BUTTON_TOP_MARGIN + row * (TOGGLE_BUTTON_SIZE + TOGGLE_BUTTON_GAP); }
    static int territoryControlAt(int x, int y) {
        for (int row = 0; row < 4; row++) {
            if (isOverToggleButton(territoryControlX(), territoryControlY(row), x, y)) return row;
        }
        return -1;
    }
    private static boolean handleTerritoryControl(LOTRGuiMap map, int x, int y, int button) {
        int row = territoryControlAt(x, y), layer = -1;
        for (int i = 0; i < 5; i++) if (isOverToggleButton(toggleButtonX(i), toggleButtonY(i), x, y)) layer = i;
        if ((row < 0 && layer < 0) || mapControlCovered(map,x,y)) return false;
        if (button == 0) {
            if (row == 0) territoryStyle.palette = territoryStyle.palette.next();
            else if (row == 1) territoryStyle.mode = territoryStyle.mode.next();
            else if (row == 2) territoryStyle.opacity = territoryStyle.opacity.next();
            else if (row == 3) territoryStyle.showTileBorders = !territoryStyle.showTileBorders;
            else if (!isChoosingDestination()) {
                if (layer == 0) showConquestTiles = !showConquestTiles;
                else if (layer == 1) showRiverBlockMarkers = !showRiverBlockMarkers;
                else if (layer == 2) showBridgeMarkers = !showBridgeMarkers;
                else if (layer == 3) showTroopMarkers = !showTroopMarkers;
                else if (layer == 4) showBuildMarkers = !showBuildMarkers;
            }
            if(row>=0 || !isChoosingDestination()) {
                KOMEClientConfig config=KOMEClientConfig.get();
                if(config!=null) {
                    KOMEMapPreferences selected=mapPreferences();
                    // Destination workflows can force the conquest layer on without changing its saved choice.
                    if(layer!=0)selected=selected.withConquest(config.mapPreferences().conquest);
                    config.setMapPreferences(selected);
                }
            }
            radialLeftCaptured = true;
            radialDragMap = map;
        }
        return true; // Auxiliary clicks also belong to the visible control, never to the map below.
    }

    static boolean mapControlCovered(LOTRGuiMap map,int x,int y) {
        return unitsActive() || rallyActive() || originActive() || selectorOwnsPointer(map,x,y) || radialOwnsPointer(map,x,y);
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

    private static boolean isOverConquestToggleButton(LOTRGuiMap map, int mouseX, int mouseY) {
        return isOverToggleButton(toggleButtonX(0), toggleButtonY(0), mouseX, mouseY);
    }

    private static boolean isOverBridgeToggleButton(LOTRGuiMap map, int mouseX, int mouseY) {
        return isOverToggleButton(toggleButtonX(2), toggleButtonY(2), mouseX, mouseY);
    }

    private static boolean isOverRiverBlockToggleButton(LOTRGuiMap map, int mouseX, int mouseY) {
        return isOverToggleButton(toggleButtonX(1), toggleButtonY(1), mouseX, mouseY);
    }

    private static boolean isOverTroopToggleButton(LOTRGuiMap map, int mouseX, int mouseY) {
        return isOverToggleButton(toggleButtonX(3), toggleButtonY(3), mouseX, mouseY);
    }

    private static boolean isOverToggleButton(int x, int y, int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + TOGGLE_BUTTON_SIZE && mouseY >= y && mouseY < y + TOGGLE_BUTTON_SIZE;
    }

    private static int toggleButtonX(int index) {
        return mapInt("mapXMax") - 16;
    }

    private static int toggleButtonY(int index) { return toggleButtonY() + index * (TOGGLE_BUTTON_SIZE + TOGGLE_BUTTON_GAP); }
    private static int toggleButtonY() {
        // Native waypoint actions below y=6 are conditional. Reserve only visible widgets.
        int width=mapInt("mapWidth"),height=mapInt("mapHeight"),x=width-16,top=20;
        List widgets=(List)mapFieldValue(null,"mapWidgets");
        if(widgets!=null)for(Object object:widgets) {
            lotr.client.gui.LOTRGuiMapWidget widget=(lotr.client.gui.LOTRGuiMapWidget)object;
            int nativeX=widget.getMapXPos(width);
            if(widget.visible&&nativeX<x+TOGGLE_BUTTON_SIZE&&nativeX+widget.width>x)
                top=Math.max(top,widget.getMapYPos(height)+widget.width+TOGGLE_BUTTON_GAP);
        }
        return mapInt("mapYMin") + top;
    }

    private static void drawClaimedTexture(LOTRGuiMap map) {
        if (!ensureTileMaskLoaded()) {
            return;
        }
        updateClaimedTexture();
        drawMapTexture(map, claimedTextureLocation, 1.0f);
    }

    /** Keep the upload boundary separate from binding/drawing, including when returning from borders-only. */
    static void updateClaimedTexture() {
        if (!territoryStyle.mode.fillsTerritory()) return;
        prepareTerritoryColors();
        if (claimTextureDirty) {
            claimTextureState.update(territoryColors, territoryStyle.opacity.alpha, KOMEConquestMapOverlay::rebuildClaimedTexture);
            claimTextureDirty = false;
        }
    }

    private static void prepareTerritoryColors() {
        if(mapBorders!=null)mapBorders.territoryPresentation(territoryStyle);
        if (renderedOpacity != territoryStyle.opacity) {
            renderedOpacity = territoryStyle.opacity;
            claimTextureDirty = true;
        }
        if (renderedClaimRevision == KOMEClientData.INSTANCE.conquestRevision && renderedPalette == territoryStyle.palette) return;
        Map<Integer, String> owners = new HashMap<>();
        for (Map.Entry<String, String> tile : KOMEClientData.INSTANCE.conquestRenderOwners().entrySet()) {
            Integer mask = tileColorsById.get(tile.getKey());
            if (mask != null) owners.put(mask, KOMETerritoryStyle.ownerKey(tile.getValue()));
        }
        territoryColors = effectiveClaimColors();
        if(KOMEMapProfiler.ENABLED&&!territoryOwners.equals(owners))KOMEMapProfiler.count(KOMEMapProfiler.Count.OWNER_PROJECTION_CHANGE);
        territoryOwners = owners;
        renderedClaimRevision = KOMEClientData.INSTANCE.conquestRevision;
        renderedPalette = territoryStyle.palette;
        claimTextureDirty = true;
    }

    /** Only the already public client tile projection can contribute ownership colors. */
    static Map<Integer, Integer> effectiveClaimColors() {
        Map<Integer, Integer> claimedColors = new HashMap<>();
        for (Map.Entry<String, String> tile : KOMEClientData.INSTANCE.conquestRenderOwners().entrySet()) {
            Integer maskColor = tileColorsById.get(tile.getKey());
            if (maskColor != null) claimedColors.put(maskColor, factionArgb(tile.getValue()));
        }
        return claimedColors;
    }

    /** Exact small-map comparison, never a geometry scan or a hash-only decision. */
    static final class ClaimTextureState {
        private Map<Integer, Integer> rendered;
        private int renderedAlpha = -1;
        boolean update(Map<Integer, Integer> colors, java.util.function.Consumer<Map<Integer, Integer>> rebuild) {
            return update(colors, 255, rebuild);
        }
        boolean update(Map<Integer, Integer> colors, int alpha, java.util.function.Consumer<Map<Integer, Integer>> rebuild) {
            if (rendered != null && renderedAlpha == alpha && rendered.equals(colors)) return false;
            Map<Integer, Integer> snapshot = java.util.Collections.unmodifiableMap(new HashMap<>(colors));
            rebuild.accept(snapshot);
            rendered = snapshot; // Do not cache a failed upload.
            renderedAlpha = alpha;
            return true;
        }
        void clear() { rendered = null; renderedAlpha = -1; }
    }

    private static void rebuildClaimedTexture(Map<Integer, Integer> claimedColors) {
        int[] textureData = claimedTexture.getTextureData();
        Arrays.fill(textureData, 0);
        for (int i = 0; i < tileMaskPixels.length; i++) {
            int maskColor = tileMaskPixels[i] & 0xFFFFFF;
            Integer factionColor = claimedColors.get(maskColor);
            if (factionColor != null && (tileMaskPixels[i] >>> 24) > 24) {
                textureData[i] = territoryStyle.opacity.alpha << 24 | factionColor.intValue();
            }
        }
        claimedTexture.updateDynamicTexture();
    }

    private static int factionArgb(String factionName) {
        return territoryStyle.palette.rgb(factionName);
    }

    private static void drawHighlightTexture(LOTRGuiMap map, int tileColor) {
        drawGeometry(map, tileColor, true);
    }

    /** Keep batches below the legacy Tessellator's grow/shrink threshold (32 ints per quad). */
    static final class GeometryBatch implements KOMEMapBorders.QuadSink {
        static final int MAX_QUADS = (0x10000 - 32) / 32;
        private final Tessellator tess;
        private int quads;
        GeometryBatch(Tessellator tess) { this.tess = tess; }
        @Override public void quad(double x0, double y0, double x1, double y1, int argb) {
            if (quads == MAX_QUADS) finish();
            if (quads == 0) tess.startDrawingQuads();
            tess.setColorRGBA_I(argb & 0xFFFFFF, argb >>> 24);
            tess.addVertex(x0, y1, 0); tess.addVertex(x1, y1, 0);
            tess.addVertex(x1, y0, 0); tess.addVertex(x0, y0, 0);
            quads++;
        }
        void finish() {
            if (quads == 0) return;
            tess.draw();
            KOMEMapProfiler.count(KOMEMapProfiler.Count.GEOMETRY_BATCH);
            quads = 0;
        }
    }

    private static void drawGeometry(LOTRGuiMap map, int hoverColor, boolean fill) {
        if (mapBorders == null) return;
        long profileStart=KOMEMapProfiler.start();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GeometryBatch batch = new GeometryBatch(Tessellator.instance);
            if (fill) mapBorders.drawHighlight(viewport(map), hoverColor, batch);
            else drawTerritoryGeometry(viewport(map), hoverColor, batch,
                mapNumber(map,"zoomTicks")>0,mapNumber(map,"zoomScaleStable"));
            batch.finish();
        } finally {
            GL11.glPopAttrib();
            KOMEMapProfiler.elapsed(KOMEMapProfiler.Stage.BORDER_GEOMETRY,profileStart);
        }
    }

    /** Shared style dispatch, independent of GL binding so visual preferences can be checked directly. */
    static void drawTerritoryGeometry(KOMEMapViewport view, int hoverColor, KOMEMapBorders.QuadSink sink) {
        drawTerritoryGeometry(view,hoverColor,sink,false,view.zoom);
    }

    static void drawTerritoryGeometry(KOMEMapViewport view,int hoverColor,KOMEMapBorders.QuadSink sink,
            boolean nativeZoomAnimating,double nativeMinimumZoom) {
        if (!territoryStyle.mode.fillsTerritory()) {
            if(sink instanceof GeometryBatch) {
                KOMEMapBorders.TerritoryFrame frame=mapBorders.navigationTerritoryFrame(view,territoryOwners,territoryColors,nativeZoomAnimating,nativeMinimumZoom,System.nanoTime());
                if(frame!=null)drawTerritoryMesh(frame,view,(GeometryBatch)sink);
            }
            else if(nativeZoomAnimating){KOMEMapBorders.TerritoryFrame frame=mapBorders.territoryFrame(view,territoryOwners,territoryColors,true,nativeMinimumZoom);frame.mesh.draw(view,frame.view,sink);}
            else mapBorders.drawTerritories(view, territoryOwners, territoryColors, sink);
        }
        mapBorders.drawBorders(view, hoverColor, !territoryStyle.showTileBorders, sink);
    }

    // LWJGL 2 glGetInteger(IntBuffer) validates 16 remaining ints, even for the four-value scissor box.
    private static final java.nio.IntBuffer territoryScissor = org.lwjgl.BufferUtils.createIntBuffer(16);

    /** One cached OpenGL 1.1 client-array submission. No per-frame Tessellator replay for retained meshes. */
    private static void drawTerritoryMesh(KOMEMapBorders.TerritoryFrame frame,KOMEMapViewport view,GeometryBatch fallback) {
        KOMETerritoryMesh mesh=frame.mesh;
        KOMEMapProfiler.mesh(mesh.quads());
        if(frame.streamOnly || !mesh.cacheable()) {
            long profileStart=KOMEMapProfiler.start();KOMEMapProfiler.count(KOMEMapProfiler.Count.STREAM_DRAW);
            try{mesh.draw(view,frame.view,fallback);}
            finally{KOMEMapProfiler.elapsed(KOMEMapProfiler.Stage.STREAM_REPLAY,profileStart);}return;
        }
        if(mesh.quads()==0)return;
        java.nio.ByteBuffer vertices=mesh.vertices();
        org.lwjgl.opengl.ContextCapabilities capabilities=org.lwjgl.opengl.GLContext.getCapabilities();
        boolean nativeVbo=capabilities.OpenGL15,hasVbo=nativeVbo || capabilities.GL_ARB_vertex_buffer_object;
        int previousBuffer=hasVbo?GL11.glGetInteger(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER_BINDING):0;
        boolean clipped=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int oldX=0,oldY=0,oldWidth=0,oldHeight=0;
        if(clipped){territoryScissor.clear();GL11.glGetInteger(GL11.GL_SCISSOR_BOX,territoryScissor);
            oldX=territoryScissor.get(0);oldY=territoryScissor.get(1);oldWidth=territoryScissor.get(2);oldHeight=territoryScissor.get(3);}
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_SCISSOR_BIT | GL11.GL_CURRENT_BIT);
        GL11.glPushClientAttrib(GL11.GL_CLIENT_VERTEX_ARRAY_BIT);
        GL11.glPushMatrix();
        try {
            if(hasVbo)bindTerritoryArrayBuffer(nativeVbo,0);
            // A temporarily reused partial mesh owns only its constructed coverage. Clip inward.
            int clipLeft=(int)Math.ceil(frame.clipLeft(view)),clipTop=(int)Math.ceil(frame.clipTop(view));
            int clipRight=(int)Math.floor(frame.clipRight(view)),clipBottom=(int)Math.floor(frame.clipBottom(view));
            KOMEGuiTheme.enableScissor(KOMEMinecraftClient.get(),clipLeft,clipTop,Math.max(0,clipRight-clipLeft),Math.max(0,clipBottom-clipTop));
            if(clipped){territoryScissor.clear();GL11.glGetInteger(GL11.GL_SCISSOR_BOX,territoryScissor);
                int x=Math.max(oldX,territoryScissor.get(0)),y=Math.max(oldY,territoryScissor.get(1));
                int right=Math.min(oldX+oldWidth,territoryScissor.get(0)+territoryScissor.get(2));
                int bottom=Math.min(oldY+oldHeight,territoryScissor.get(1)+territoryScissor.get(3));
                GL11.glScissor(x,y,Math.max(0,right-x),Math.max(0,bottom-y));}
            GL11.glTranslated(view.offsetXFrom(frame.view),view.offsetYFrom(frame.view),0);
            GL11.glScaled(view.scaleFrom(frame.view),view.scaleFrom(frame.view),1);
            GL11.glInterleavedArrays(GL11.GL_C4UB_V3F,KOMETerritoryMesh.VERTEX_BYTES,vertices);
            long profileStart=KOMEMapProfiler.start();
            GL11.glDrawArrays(GL11.GL_QUADS,0,mesh.quads()*4);
            KOMEMapProfiler.elapsed(KOMEMapProfiler.Stage.DRAW_ARRAYS,profileStart);KOMEMapProfiler.count(KOMEMapProfiler.Count.ARRAY_DRAW);
        } finally {
            GL11.glPopMatrix();GL11.glPopClientAttrib();
            if(hasVbo)bindTerritoryArrayBuffer(nativeVbo,previousBuffer);
            GL11.glPopAttrib();
        }
    }

    private static void bindTerritoryArrayBuffer(boolean nativeVbo,int buffer) {
        if(nativeVbo)org.lwjgl.opengl.GL15.glBindBuffer(org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER,buffer);
        else org.lwjgl.opengl.ARBVertexBufferObject.glBindBufferARB(org.lwjgl.opengl.ARBVertexBufferObject.GL_ARRAY_BUFFER_ARB,buffer);
    }

    static KOMEMapViewport viewport(LOTRGuiMap map) {
        return new KOMEMapViewport(mapInt("mapXMin"), mapInt("mapYMin"), mapInt("mapXMax"), mapInt("mapYMax"),
            mapNumber(map, "posX"), mapNumber(map, "posY"), mapNumber(map, "zoomScale"));
    }

    /** Used by the live hover renderer; a missing public projection is not a known zero. */
    static void appendPopulationTooltip(List<String> lines, KOMETileTroopSummary summary) {
        if (summary != null) {
            if (!summary.population.faction.isEmpty()) {
                lines.add("Faction Available Population: " + kome.common.data.KOMEPopulationProjection.formatCenti(summary.population.availablePopulationCenti));
                lines.add("Faction Active Population: " + kome.common.data.KOMEPopulationProjection.formatCenti(summary.population.activePopulationCenti));
                lines.add("Faction Daily Population Rate: " + kome.common.data.KOMEPopulationProjection.formatRate(summary.population.dailyRateUnits));
            }
            if (summary.stationedOffensivePop > 0 || summary.stationedMountedPop > 0 || summary.stationedDefensivePop > 0) {
                lines.add("Stationed: Off " + summary.stationedOffensivePop + ", Mounted " + summary.stationedMountedPop + ", Def " + summary.stationedDefensivePop);
            }
            if (summary.movingPop > 0 || summary.incomingPop > 0) {
                lines.add("Moving: Out " + summary.movingPop + ", In " + summary.incomingPop);
            }
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
            lines.add("Current Ruling Faction: " + KOMEAlliance.displayFactionName(owner));
        }
        for (Map.Entry<String, String> capital
                : KOMEClientData.INSTANCE.capitalTilesByFaction.entrySet()) {
            if (tileId.equals(KOMEConquestTile.normalizeId(capital.getValue())))
                lines.add("Capital of: " + KOMEAlliance.displayFactionName(capital.getKey()));
        }
        KOMETileWaypointLink waypointLink = (KOMETileWaypointLink) KOMEClientData.INSTANCE.tileWaypointLinksByTileId.get(tileId);
        lines.add("LOTR Waypoint: " + (waypointLink == null ? "Missing" : waypointLink.displayName()));
        appendPopulationTooltip(lines, summary);
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
        if (!showTroopMarkers) {
            return;
        }
        if (!ensureTileMaskLoaded()) {
            return;
        }
        KOMEMapIconPresentation presentation=markerPresentation(map);KOMEMapViewport view=viewport(map);
        Map<String, Integer> hiddenStationedPopulationByTile = liveCompanyStationedPopulationByTile();
        for (Object object : KOMEClientData.INSTANCE.troopSummaries.values()) {
            KOMETileTroopSummary summary = (KOMETileTroopSummary) object;
            if (summary == null || !summary.hasAnyTroops()) {
                continue;
            }
            String tileId = KOMEConquestTile.normalizeId(summary.tileId);
            int hiddenStationedPop = mapIntValue(hiddenStationedPopulationByTile, tileId);
            int visibleStationedPop = Math.max(0, summary.stationedPop - hiddenStationedPop);
            if (visibleStationedPop <= 0 && summary.movingPop <= 0 && summary.incomingPop <= 0) {
                continue;
            }
            int[] screen = tileAnchorScreenPosition(map, tileId);
            if (screen == null) {
                continue;
            }
            int screenX = screen[0];
            int screenY = screen[1];
            if (screenX < mapInt("mapXMin") || screenX > mapInt("mapXMax") || screenY < mapInt("mapYMin") || screenY > mapInt("mapYMax")) {
                continue;
            }
            KOMEMapIconPresentation.Bounds bounds=presentation.bounds(screenX,screenY,TROOP_MARKER_WIDTH,TROOP_MARKER_HEIGHT);
            if(!bounds.visible(view))continue;
            int markerX=bounds.x,markerY=bounds.y;
            if(!presentation.labels)continue;
            FontRenderer font = KOMEMinecraftClient.fontRenderer();
            int primaryPop = visibleStationedPop > 0 ? visibleStationedPop : summary.incomingPop > 0 ? summary.incomingPop : summary.movingPop;
            drawTroopCount(font, markerX, markerY, bounds.width, bounds.height, primaryPop);
            int sideTextY = markerY + 4;
            if (summary.movingPop > 0 && primaryPop != summary.movingPop) {
                font.drawStringWithShadow(">" + abbreviatePop(summary.movingPop), markerX + bounds.width + 2, sideTextY, 0xFFFFD966);
                sideTextY += 10;
            }
            if (summary.incomingPop > 0 && primaryPop != summary.incomingPop) {
                font.drawStringWithShadow("<" + abbreviatePop(summary.incomingPop), markerX + bounds.width + 2, sideTextY, 0xFF6FCBFF);
            }
        }
    }

    private static List<String> drawLiveUnitMarkers(LOTRGuiMap map, int mouseX, int mouseY) {
        return renderCompanyMarkers(map,mouseX,mouseY,(marker,art,bounds,presentation)->{
            if(KOMEMapMarkerTrace.ENABLED)drawTexturedMarker(KOMEMapMarkerTrace.Kind.COMPANY_SHIELD,marker.entityId,art,bounds);
            else drawTexturedMarker(art,bounds);
            if(presentation.labels)drawTroopCount(KOMEMinecraftClient.fontRenderer(),bounds.x,bounds.y,bounds.width,bounds.height,marker.population);
        });
    }

    interface CompanyMarkerSink {
        void draw(KOMEUnitMapMarker marker,KOMEMapMarkerArt.Sprite art,KOMEMapIconPresentation.Bounds bounds,KOMEMapIconPresentation presentation);
    }

    static List<String> renderCompanyMarkers(LOTRGuiMap map,int mouseX,int mouseY,CompanyMarkerSink sink) {
        if (!showTroopMarkers || KOMEClientData.INSTANCE.unitMapMarkers.isEmpty()) {
            return null;
        }
        KOMEMapIconPresentation presentation=markerPresentation(map);KOMEMapViewport view=viewport(map);
        int mapXMin = mapInt("mapXMin");
        int mapXMax = mapInt("mapXMax");
        int mapYMin = mapInt("mapYMin");
        int mapYMax = mapInt("mapYMax");
        KOMEUnitMapMarker tooltipMarker=null;
        boolean inspect=!mapUiOwnsPointer(map,mouseX,mouseY);
        for (KOMEUnitMapMarker marker : orderedCompanyMarkers()) {
            if (marker == null || marker.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID || !companyMarkerCurrent(marker)) {
                continue;
            }
            int[] anchor=companyMarkerScreenPosition(map,marker);
            if(anchor==null)continue;
            int screenX=anchor[0],screenY=anchor[1];
            if (screenX < mapXMin || screenX > mapXMax || screenY < mapYMin || screenY > mapYMax) {
                continue;
            }
            KOMEMapMarkerArt.Sprite art=KOMEMapMarkerArt.displayed(KOMEMapMarkerArt.company(marker,KOMEClientData.INSTANCE.armyCompanies));
            if(art==null)continue;
            KOMEMapIconPresentation.Bounds bounds=art.bounds(presentation,screenX,screenY);
            if(!bounds.visible(view))continue;
            sink.draw(marker,art,bounds,presentation);
            if (inspect && bounds.contains(mouseX,mouseY)) tooltipMarker=marker;
        }
        return tooltipMarker==null?null:liveUnitTooltip(tooltipMarker);
    }

    /** Stable company identity orders submission; the last actually visible overlap owns inspection. */
    private static List<KOMEUnitMapMarker> orderedCompanyMarkers() {
        List<KOMEUnitMapMarker> markers=new ArrayList<>(KOMEClientData.INSTANCE.unitMapMarkers);
        markers.sort(java.util.Comparator.nullsFirst(java.util.Comparator.comparing(
            (KOMEUnitMapMarker marker)->marker.entityId,java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))));
        return markers;
    }

    private static List<String> drawBuildMarkers(LOTRGuiMap map, int mouseX, int mouseY) {
        return renderBuildMarkers(map,mouseX,mouseY,KOMEConquestMapOverlay::drawBuildMarker);
    }

    interface BuildMarkerSink {
        void draw(KOMEPlayerBuild build,KOMEMapIconPresentation.Bounds bounds,KOMEMapIconPresentation presentation);
    }

    /** One ordered presentation traversal; the final visible overlap owns both tooltip and picking. */
    static List<String> renderBuildMarkers(LOTRGuiMap map,int mouseX,int mouseY,BuildMarkerSink sink) {
        if (!showBuildMarkers || !showConquestTiles) return null;
        KOMEMapIconPresentation presentation=markerPresentation(map);KOMEMapViewport view=viewport(map);
        KOMEPlayerBuild hovered=null;
        boolean inspect=!isChoosingDestination()&&!mapUiOwnsPointer(map,mouseX,mouseY);
        for (Map.Entry<String,KOMEPlayerBuild> entry : orderedBuildMarkers()) {
            KOMEPlayerBuild build=entry.getValue();
            KOMEMapIconPresentation.Bounds bounds=visibleBuildBounds(map,build,presentation,view);
            if(bounds==null)continue;
            sink.draw(build,bounds,presentation);
            if(inspect&&bounds.contains(mouseX,mouseY))hovered=build;
        }
        if(hovered==null)return null;
        List<String> tooltip=new ArrayList<String>();
        tooltip.add(hovered.displayName + " (" + hovered.id + ")");
        tooltip.add("Population allegiance: " + KOMEAlliance.displayFactionName(hovered.populationFaction));
        tooltip.add("Tile: " + hovered.tileId + " | " + coord(hovered.x) + ", " + coord(hovered.y) + ", " + coord(hovered.z));
        tooltip.add("Left-click: open Build details");
        return tooltip;
    }

    private static void drawBuildMarker(KOMEPlayerBuild build,KOMEMapIconPresentation.Bounds bounds,KOMEMapIconPresentation presentation) {
        KOMEMapMarkerArt.Sprite art=KOMEMapMarkerArt.build(build);
        if(!KOMEMapMarkerTrace.ENABLED){drawTexturedMarker(art,bounds);return;}
        KOMEMapMarkerTrace.Kind kind=build.isDefensive()?KOMEMapMarkerTrace.Kind.DEFENSIVE_CASTLE:
            build.isNormal()?KOMEMapMarkerTrace.Kind.NORMAL_BANNER:null;
        drawTexturedMarker(kind,build.id,art,bounds);
    }

    /** Projection keys are unique Build identities. Ascending key order draws the greatest key on top. */
    private static List<Map.Entry<String,KOMEPlayerBuild>> orderedBuildMarkers() {
        List<Map.Entry<String,KOMEPlayerBuild>> entries=new ArrayList<>(KOMEClientData.INSTANCE.builds.entrySet());
        entries.sort(Map.Entry.comparingByKey(java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())));
        return entries;
    }

    private static KOMEMapIconPresentation.Bounds visibleBuildBounds(LOTRGuiMap map,KOMEPlayerBuild build,
            KOMEMapIconPresentation presentation,KOMEMapViewport view) {
        if(build==null||!build.active||!build.markerVisible||build.dimension!=LOTRDimension.MIDDLE_EARTH.dimensionID)return null;
        if(KOMEMapMarkerArt.displayed(KOMEMapMarkerArt.build(build))==null)return null;
        KOMEMapIconPresentation.Bounds bounds=buildMarkerBounds(map,build,presentation);
        return bounds.visible(view)?bounds:null;
    }

    private static KOMEPlayerBuild buildMarkerAt(LOTRGuiMap map, int mouseX, int mouseY) {
        if (buildSpritesMap!=map || !showBuildMarkers || !showConquestTiles || isChoosingDestination() || mapUiOwnsPointer(map,mouseX,mouseY)) return null;
        KOMEMapIconPresentation presentation=markerPresentation(map);KOMEMapViewport view=viewport(map);
        KOMEPlayerBuild target=null;
        for (Map.Entry<String,KOMEPlayerBuild> entry : orderedBuildMarkers()) {
            KOMEPlayerBuild build=entry.getValue();
            KOMEMapIconPresentation.Bounds bounds=visibleBuildBounds(map,build,presentation,view);
            if(bounds!=null&&bounds.contains(mouseX,mouseY))target=build;
        }
        return target;
    }

    private static KOMEMapIconPresentation markerPresentation(LOTRGuiMap map){return KOMEMapIconPresentation.at(mapNumber(map,"zoomScale"));}
    private static KOMEMapIconPresentation.Bounds buildMarkerBounds(LOTRGuiMap map,KOMEPlayerBuild build,KOMEMapIconPresentation presentation) {
        KOMEMapMarkerArt.Sprite art=KOMEMapMarkerArt.build(build);
        return art.bounds(presentation,worldScreenX(map,build.x)+buildMarkerOffset(build.id,true),
            worldScreenY(map,build.z)+buildMarkerOffset(build.id,false));
    }

    private static int buildMarkerOffset(String id, boolean horizontal) {
        int hash = id == null ? 0 : id.hashCode();
        int shifted = horizontal ? hash : hash >>> 8;
        return Math.abs(shifted % 5) - 2;
    }

    private static int coord(double value) {
        return (int) Math.floor(value);
    }

    private static Map<String, Integer> liveCompanyStationedPopulationByTile() {
        Map<String, Integer> populationByTile = new HashMap<String, Integer>();
        java.util.Set<String> counted=new java.util.HashSet<>();
        if (KOMEClientData.INSTANCE.unitMapMarkers.isEmpty()) {
            return populationByTile;
        }
        for (Object object : KOMEClientData.INSTANCE.unitMapMarkers) {
            KOMEUnitMapMarker marker = (KOMEUnitMapMarker) object;
            if (marker == null || marker.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID
                    || !companyMarkerCurrent(marker) || !counted.add(marker.entityId)) {
                continue;
            }
            String tileId = markerStationedTile(marker);
            int population = Math.max(0, marker.population);
            if (tileId.length() > 0 && population > 0) {
                populationByTile.put(tileId, Integer.valueOf((int)Math.min(Integer.MAX_VALUE,
                    (long)mapIntValue(populationByTile, tileId) + population)));
            }
        }
        return populationByTile;
    }

    private static String markerStationedTile(KOMEUnitMapMarker marker) {
        String tile = KOMEConquestTile.normalizeId(marker == null ? "" : marker.currentTile);
        if (tile.length() > 0) {
            return tile;
        }
        if (marker == null || marker.entityId == null || marker.entityId.length() == 0) {
            return "";
        }
        Object companyObject = KOMEClientData.INSTANCE.armyCompanies.get(marker.entityId);
        if (companyObject instanceof KOMEArmyCompany) {
            KOMEArmyCompany company = (KOMEArmyCompany) companyObject;
            return KOMEConquestTile.normalizeId(company.currentTile);
        }
        return "";
    }

    private static int mapIntValue(Map<String, Integer> values, String key) {
        Integer value = values.get(key);
        return value == null ? 0 : value.intValue();
    }

    static KOMETileResolution tileAtWorld(int dimension, double worldX, double worldZ) {
        return KOMETileWorldResolver.INSTANCE.resolveWorldPosition(dimension, worldX, worldZ);
    }

    static KOMETileResolution tileAtMapPosition(int dimension, double mapX, double mapY) {
        return KOMETileWorldResolver.INSTANCE.resolveMapPosition(dimension, mapX, mapY);
    }

    private static List<String> liveUnitTooltip(KOMEUnitMapMarker marker) {
        List<String> lines = new ArrayList<String>();
        lines.add(marker.companyName == null || marker.companyName.length() == 0 ? "Company" : marker.companyName);
        lines.add("Loaded units: " + marker.unitCount);
        lines.add("Campaign population: " + marker.population + (marker.mounted ? " mounted" : " mixed/ground"));
        if(marker.unitCount>0)lines.add(marker.haltedProtected ? "Halted: Protected / Inactive" : "Active: Vulnerable / Combat-capable");
        else lines.add("Strategic tile presence; NPC position/combat state unavailable");
        String stationedTile = markerStationedTile(marker);
        if (stationedTile.length() > 0) {
            lines.add("Stationed tile: " + stationedTile);
        }
        if(marker.unitCount<=0)return lines;
        String positionTile = tileAtWorld(marker.dimensionId, marker.x, marker.z).tileId;
        if (positionTile.length() > 0 && stationedTile.length() > 0 && !positionTile.equals(stationedTile)) {
            lines.add("Position tile: " + positionTile);
        }
        lines.add("Company center: " + Math.round(marker.x) + ", " + Math.round(marker.y) + ", " + Math.round(marker.z));
        return lines;
    }

    private static int[] companyMarkerScreenPosition(LOTRGuiMap map,KOMEUnitMapMarker marker) {
        if(!companyMarkerCurrent(marker))return null;
        return marker.unitCount>0?new int[]{worldScreenX(map,marker.x),worldScreenY(map,marker.z)}
            :tileAnchorScreenPosition(map,marker.currentTile);
    }

    private static boolean companyMarkerCurrent(KOMEUnitMapMarker marker) {
        KOMEArmyCompany company=KOMEClientData.INSTANCE.armyCompanies.get(marker.entityId);
        // Neutral physical art is allowed only before the first canonical complete snapshot.
        // Afterwards, absence is authoritative even if an older marker packet arrives later.
        // Strategic-only presence needs the matching current stationed projection, never tile ownership.
        if(company==null)return marker.unitCount>0&&!KOMEClientData.INSTANCE.hasCompleteConquestSnapshot();
        return !company.isMoving()&&(KOMEArmyCompany.STATIONED.equals(company.status)||KOMEArmyCompany.WAR_ENDED_HALTED.equals(company.status))
            && markerStationedTile(marker).equals(KOMEConquestTile.normalizeId(company.currentTile));
    }

    private static int[] tileAnchorScreenPosition(LOTRGuiMap map, String tileId) {
        String normalized = KOMEConquestTile.normalizeId(tileId);
        KOMETileWaypointLink waypointLink = (KOMETileWaypointLink) KOMEClientData.INSTANCE.tileWaypointLinksByTileId.get(normalized);
        if (waypointLink != null && (Math.abs(waypointLink.waypointWorldX) > 0.001D || Math.abs(waypointLink.waypointWorldZ) > 0.001D)) {
            return new int[] {worldScreenX(map, waypointLink.waypointWorldX), worldScreenY(map, waypointLink.waypointWorldZ)};
        }
        int[] center = tileCentersById.get(normalized);
        if (center == null) {
            return null;
        }
        return new int[] {mapScreenX(map, center[0]), mapScreenY(map, center[1])};
    }

    private static void drawRouteEdgeMarkers(LOTRGuiMap map) {
        drawRouteEdgeMarkers(map,false);
    }

    private static void drawRouteEdgeMarkers(LOTRGuiMap map,boolean bridges) {
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
            if(!geographyEdgePass(edge,bridges))continue;
            if (bridge) {
                if (!showBridgeMarkers) {
                    continue;
                }
                drawGeographyTexture(map,getBridgeMarkerTextureLocation(),screenX,screenY,BRIDGE_MARKER_WIDTH,BRIDGE_MARKER_HEIGHT);
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

    private static List<String> drawAutomaticBridgeMarkers(LOTRGuiMap map, int mouseX, int mouseY) {
        if (!showBridgeMarkers || !ensureTileMaskLoaded()) {
            return null;
        }
        KOMEMapViewport view=viewport(map);
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
            KOMEMapIconPresentation.MapBounds icon=automaticBridgeBounds(map,screenX,screenY);
            if(!icon.visible(view))continue;
            drawGeographyTexture(getBridgeMarkerTextureLocation(),icon);
            if (automaticBridgeHovered(map,icon,mouseX,mouseY)) {
                tooltip = new ArrayList<String>();
                tooltip.add("Automatic Bridge");
                tooltip.add(marker.getTilePairLabel());
                tooltip.add("XYZ: " + Math.round(marker.x) + ", " + Math.round(marker.y) + ", " + Math.round(marker.z));
            }
        }
        return tooltip;
    }

    static KOMEMapIconPresentation.MapBounds automaticBridgeBounds(LOTRGuiMap map,int screenX,int screenY) {
        return KOMEMapIconPresentation.geography(mapNumber(map,"zoomScale"),screenX,screenY,BRIDGE_MARKER_WIDTH,BRIDGE_MARKER_HEIGHT);
    }

    static boolean automaticBridgeHovered(LOTRGuiMap map,KOMEMapIconPresentation.MapBounds icon,int mouseX,int mouseY) {
        return icon.contains(mouseX,mouseY)&&icon.visible(viewport(map))&&!mapUiOwnsPointer(map,mouseX,mouseY);
    }

    static KOMEMapIconPresentation.MapBounds riverMarkerBounds(LOTRGuiMap map,int screenX,int screenY) {
        return KOMEMapIconPresentation.geography(mapNumber(map,"zoomScale"),screenX,screenY,RIVER_MARKER_WIDTH,RIVER_MARKER_HEIGHT);
    }

    private static List<String> drawAutomaticRiverBlockMarkers(LOTRGuiMap map, int mouseX, int mouseY) {
        if (!showRiverBlockMarkers || !ensureTileMaskLoaded()) {
            return null;
        }
        KOMEMapViewport view=viewport(map);
        int mapXMin = mapInt("mapXMin");
        int mapXMax = mapInt("mapXMax");
        int mapYMin = mapInt("mapYMin");
        int mapYMax = mapInt("mapYMax");
        List<String> tooltip = null;
        for (KOMEConquestTileDefaults.AutomaticRiverBlockerMarker marker : KOMEConquestTileDefaults.getAutomaticRiverBlockerMarkers()) {
            if (marker.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID) {
                continue;
            }
            int screenX = worldScreenX(map, marker.x);
            int screenY = worldScreenY(map, marker.z);
            if (screenX < mapXMin || screenX > mapXMax || screenY < mapYMin || screenY > mapYMax) {
                continue;
            }
            KOMEMapIconPresentation.MapBounds bounds=riverMarkerBounds(map,screenX,screenY);
            if(!bounds.visible(view))continue;
            drawGeographyTexture(RIVER_MARKER_ART,bounds);
            if (bounds.contains(mouseX,mouseY) && !mapUiOwnsPointer(map,mouseX,mouseY)) {
                tooltip = new ArrayList<String>();
                tooltip.add("River Block");
                tooltip.add(marker.getTilePairLabel());
                tooltip.add("/troops route unblock " + marker.fromTile + " " + marker.toTile);
            }
        }
        return tooltip;
    }

    private static void drawAutomaticBridgeTooltip(LOTRGuiMap map, List<String> lines, int mouseX, int mouseY) {
        drawMarkerTooltip(map, lines, mouseX, mouseY);
    }

    private static void drawMarkerTooltip(LOTRGuiMap map, List<String> lines, int mouseX, int mouseY) {
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
            int[] point = tileAnchorScreenPosition(map, normalized);
            if (point != null) {
                points.add(point);
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
                int[] point = tileAnchorScreenPosition(map, normalized);
                if (point != null) {
                    points.add(point);
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
                } else {
                    drawRouteIntermediatePoint(point[0], point[1]);
                }
            }
        }
    }

    private static void drawRouteSpecialEdgeMarkers(LOTRGuiMap map, List<String> tiles, List<int[]> points) {
        drawRouteSpecialEdgeMarkers(map,tiles,points,false);
    }

    private static void drawRouteSpecialEdgeMarkers(LOTRGuiMap map,List<String> tiles,List<int[]> points,boolean bridges) {
        for (int i = 1; i < tiles.size() && i < points.size(); i++) {
            KOMEConquestRouteEdge edge = (KOMEConquestRouteEdge) KOMEClientData.INSTANCE.routeEdges.get(KOMEConquestRouteEdge.key(tiles.get(i - 1), tiles.get(i)));
            if (edge == null || !edge.isSpecialPassage()) {
                continue;
            }
            if(!geographyEdgePass(edge,bridges))continue;
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
                drawGeographyTexture(map,getBridgeMarkerTextureLocation(),markerX,markerY,BRIDGE_MARKER_WIDTH,BRIDGE_MARKER_HEIGHT);
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
        Gui.drawRect(x, y, x + w, y + 124, 0xC81B1208);
        Gui.drawRect(x + 2, y + 2, x + w - 2, y + 18, 0xCC5A1118);
        font.drawString("Confirm Movement Route", x + 10, y + 6, 0xFFE8C46A);
        String route = routePreviewMove.originTile + " -> " + routePreviewMove.destinationTile
            + " | " + routePreviewMove.distanceTiles + " step(s) | allowance "
            + routePreviewMove.remainingAllowance + "/" + routePreviewMove.tilesPerDay;
        font.drawString(KOMEGuiTheme.trimToWidth(font, route, w - 20), x + 10, y + 25, 0xFFFFFFFF);
        font.drawString(KOMEGuiTheme.trimToWidth(font, formatRoutePreviewTiles(), w - 20), x + 10, y + 39, 0xFFBFE8FF);
        font.drawString(KOMEGuiTheme.trimToWidth(font, routePreviewMove.movementStatusText, w - 20),
            x + 10, y + 52, 0xFFBFA77A);
        String boundary = routePreviewMove.requiredMovementBoundaries == 0 ? "Arrival can complete with current allowance"
            : routePreviewMove.requiredMovementBoundaries + " movement boundary(s); next "
                + routePreviewMove.nextMovementBoundaryText;
        font.drawString(KOMEGuiTheme.trimToWidth(font, boundary, w - 220), x + 10, y + 66, 0xFFBFA77A);
        font.drawString(KOMEGuiTheme.trimToWidth(font, "Estimated completion: "
            + routePreviewMove.estimatedCompletionText, w - 220), x + 10, y + 80, 0xFFBFA77A);
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
        return mapInt("mapYMax") - 138;
    }

    private static int routeButtonY() {
        return routePreviewPanelY() + 102;
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

    /** One batch per primitive icon; disjoint rings must not become many individual GUI draws. */
    private static void drawMarkerShapes(java.util.function.Consumer<KOMEMapIconPresentation.RectSink> shapes) {
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT|GL11.GL_CURRENT_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_DEPTH_BUFFER_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GeometryBatch batch=new GeometryBatch(Tessellator.instance);
            shapes.accept((x,y,r,b,c)->batch.quad(x,y,r,b,c));batch.finish();
        }finally{GL11.glPopAttrib();}
    }

    static boolean geographyEdgePass(KOMEConquestRouteEdge edge,boolean bridges) {
        return KOMEConquestRouteEdge.BRIDGE.equals(edge.edgeType)==bridges;
    }

    private static void drawStrategicTexture(LOTRGuiMap map,ResourceLocation texture,int anchorX,int anchorY,int width,int height) {
        KOMEMapIconPresentation.Bounds bounds=markerPresentation(map).bounds(anchorX,anchorY,width,height);
        if(bounds.visible(viewport(map)))drawTexturedMarker(texture,bounds);
    }

    private static void drawGeographyTexture(LOTRGuiMap map,ResourceLocation texture,int anchorX,int anchorY,int width,int height) {
        KOMEMapIconPresentation.MapBounds bounds=KOMEMapIconPresentation.geography(mapNumber(map,"zoomScale"),anchorX,anchorY,width,height);
        if(bounds.visible(viewport(map)))drawGeographyTexture(texture,bounds);
    }

    private static void drawGeographyTexture(ResourceLocation texture,KOMEMapIconPresentation.MapBounds bounds) {
        drawTexturedMarker(texture,bounds.x,bounds.y,bounds.width,bounds.height,bounds.alpha/255F);
    }

    private static void drawTexturedMarker(ResourceLocation texture,KOMEMapIconPresentation.Bounds bounds) {
        drawTexturedMarker(texture,bounds.x,bounds.y,bounds.width,bounds.height,bounds.alpha/255F);
    }

    private static void drawTexturedMarker(KOMEMapMarkerArt.Sprite sprite,KOMEMapIconPresentation.Bounds bounds) {
        KOMEMapMarkerArt.Sprite art=KOMEMapMarkerArt.displayed(sprite);
        if(art!=null)drawTexturedMarker(art.texture,bounds.x,bounds.y,bounds.width,bounds.height,bounds.alpha/255F,art.u0,art.v0,art.u1,art.v1);
    }

    private static void drawTexturedMarker(KOMEMapMarkerTrace.Kind kind,String id,
            KOMEMapMarkerArt.Sprite sprite,KOMEMapIconPresentation.Bounds bounds) {
        if(!KOMEMapMarkerTrace.ENABLED||kind==null){drawTexturedMarker(sprite,bounds);return;}
        KOMEMapMarkerTrace.beginMarker(kind,id);
        try {drawTexturedMarker(sprite,bounds);}
        finally {KOMEMapMarkerTrace.endMarker();}
    }

    private static void drawTexturedMarker(ResourceLocation texture, double x, double y, double width, double height,float alpha) {
        drawTexturedMarker(texture,x,y,width,height,alpha,0,0,1,1);
    }

    private static void drawTexturedMarker(ResourceLocation texture,double x,double y,double width,double height,float alpha,
            double u0,double v0,double u1,double v1) {
        if (texture == null) {
            return;
        }
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        try {
            if(KOMEMapMarkerArt.owns(texture)) {
                if(!KOMEMapMarkerArt.bind(texture))return;
            } else KOMEMinecraftClient.textureManager().bindTexture(texture);
            GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, alpha);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            // Native textures retain the filtering chosen by Minecraft/resource-pack metadata.
            if("kome".equals(texture.getResourceDomain())) {
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
                GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            }
            if(KOMEMapMarkerTrace.ENABLED)KOMEMapMarkerTrace.submitted(texture,x,y,width,height,alpha,u0,v0,u1,v1);
            Tessellator tessellator = Tessellator.instance;
            tessellator.startDrawingQuads();
            tessellator.addVertexWithUV(x, y + height, 0.0D, u0, v1);
            tessellator.addVertexWithUV(x + width, y + height, 0.0D, u1, v1);
            tessellator.addVertexWithUV(x + width, y, 0.0D, u1, v0);
            tessellator.addVertexWithUV(x, y, 0.0D, u0, v0);
            tessellator.draw();
        } finally {GL11.glPopAttrib();}
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
        double mapX = imageX * (double) LOTRGenLayerWorld.imageWidth / tileRaster.width;
        return (int) Math.round(mapInt("mapXMin") + mapInt("mapWidth") / 2.0 + (mapX - mapNumber(map, "posX")) * mapNumber(map, "zoomScale"));
    }

    private static int mapScreenY(LOTRGuiMap map, int imageY) {
        double mapY = imageY * (double) LOTRGenLayerWorld.imageHeight / tileRaster.height;
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
        KOMEMapViewport view = viewport(map);
        // Clip in GUI space, then invert the SAME transform used by borders and hit-testing.
        double x0 = Math.max(view.left, view.screenX(0));
        double x1 = Math.min(view.right, view.screenX(tileRaster.transform.mapWidth));
        double y0 = Math.max(view.top, view.screenY(0));
        double y1 = Math.min(view.bottom, view.screenY(tileRaster.transform.mapHeight));
        if (x1 <= x0 || y1 <= y0) return;
        double minU = view.mapX(x0) / tileRaster.transform.mapWidth;
        double maxU = view.mapX(x1) / tileRaster.transform.mapWidth;
        double minV = view.mapY(y0) / tileRaster.transform.mapHeight;
        double maxV = view.mapY(y1) / tileRaster.transform.mapHeight;

        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_TEXTURE_BIT);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha);
            KOMEMinecraftClient.textureManager().bindTexture(texture);
            Tessellator tess = Tessellator.instance;
            tess.startDrawingQuads();
            tess.addVertexWithUV(x0, y1, 0.0, minU, maxV);
            tess.addVertexWithUV(x1, y1, 0.0, maxU, maxV);
            tess.addVertexWithUV(x1, y0, 0.0, maxU, minV);
            tess.addVertexWithUV(x0, y0, 0.0, minU, minV);
            tess.draw();
        } finally {
            GL11.glPopAttrib();
        }
    }

    private static boolean shouldSkipMap(LOTRGuiMap map) {
        double zoom = mapNumber(map, "zoomScale");
        // A minimized/tiny window can briefly have no drawable map rectangle.
        return mapBoolean(map, "isConquestGrid") || mapBoolean(map, "hasOverlay")
            || !Double.isFinite(zoom) || zoom <= 0.0D
            || !Double.isFinite(mapNumber(map, "posX")) || !Double.isFinite(mapNumber(map, "posY"))
            || mapInt("mapXMax") <= mapInt("mapXMin") || mapInt("mapYMax") <= mapInt("mapYMin");
    }

    private static boolean mapBoolean(LOTRGuiMap map, String name) {
        return ((Boolean) mapFieldValue(map, name)).booleanValue();
    }

    static double mapNumber(LOTRGuiMap map, String name) {
        return ((Number) mapFieldValue(map, name)).doubleValue();
    }

    static int mapInt(String name) {
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

}
