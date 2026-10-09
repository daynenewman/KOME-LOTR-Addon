package kome.client;

import java.lang.reflect.Field;
import java.util.*;
import cpw.mods.fml.common.network.simpleimpl.*;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import kome.common.network.*;
import lotr.client.gui.*;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import net.minecraft.client.gui.*;
import net.minecraftforge.client.event.GuiOpenEvent;
import org.junit.*;
import static org.junit.Assert.*;

/** Exercises production click/anchor adapters without GL, a display, or a Minecraft process. */
public class KOMEMapRadialInteractionTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private final Map<Field,Object> saved = new LinkedHashMap<Field,Object>();
    private final Map<Map,Map> savedMaps = new IdentityHashMap<Map,Map>();
    private LOTRGuiMap map;
    private KOMEMapRadialMenu menu;
    private RecordingNetwork network;
    @Before public void setup() throws Exception {
        map=KOMEAccessFixture.allocate(LOTRGuiMap.class); map.width=800; map.height=600;
        for (String name : new String[] {"mapXMin","mapXMax","mapYMin","mapYMax","mapWidth","mapHeight","mapWidgets"}) save(field(LOTRGuiMap.class,name));
        for (String name : new String[] {"tileRaster","showConquestTiles","destinationCompanyId","routePreviewMove","radialMap","radialLeftCaptured","radialDragMap"}) save(field(KOMEConquestMapOverlay.class,name));
        for (String name : new String[] {"tileColorsById","tileIdsByColor","tileCentersById"}) preserve((Map)field(KOMEConquestMapOverlay.class,name).get(null));
        preserve(KOMEClientData.INSTANCE.tileWaypointLinksByTileId);
        field(LOTRGuiMap.class,"mapXMin").setInt(null,0); field(LOTRGuiMap.class,"mapXMax").setInt(null,800);
        field(LOTRGuiMap.class,"mapYMin").setInt(null,0); field(LOTRGuiMap.class,"mapYMax").setInt(null,600);
        field(LOTRGuiMap.class,"mapWidth").setInt(null,800); field(LOTRGuiMap.class,"mapHeight").setInt(null,600);
        field(LOTRGuiMap.class,"mapWidgets").set(null,new ArrayList<LOTRGuiMapWidget>());
        field(GuiScreen.class,"buttonList").set(map,new ArrayList<GuiButton>());
        pan(2292,58,1);
        KOMETileRasterSnapshot raster=KOMETileTestResources.real(); field(KOMEConquestMapOverlay.class,"tileRaster").set(null,raster);
        colors().putAll(raster.colorsById()); ids().putAll(raster.idsByColor()); centers().put("T001",new int[] {2292,58});
        field(KOMEConquestMapOverlay.class,"showConquestTiles").setBoolean(null,true);
        field(KOMEConquestMapOverlay.class,"destinationCompanyId").set(null,""); field(KOMEConquestMapOverlay.class,"routePreviewMove").set(null,null);
        new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));
        menu=(KOMEMapRadialMenu)field(KOMEConquestMapOverlay.class,"radialMenu").get(null);
        save(field(KOMEPacketHandler.class,"network")); network=KOMEAccessFixture.allocate(RecordingNetwork.class);
        network.sent=new ArrayList<IMessage>(); KOMEPacketHandler.network=network;
    }
    @After public void cleanup() throws Exception {
        new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));
        for (Map.Entry<Map,Map> e : savedMaps.entrySet()) {e.getKey().clear();e.getKey().putAll(e.getValue());}
        for (Map.Entry<Field,Object> e : saved.entrySet()) e.getKey().set(null,e.getValue());
    }
    @Test public void rightClickOpensRingWithoutAnyNetworkRequest() {
        assertTrue(click(400,300,1)); assertEquals("T001",menu.tileId()); assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state()); assertTrue(network.sent.isEmpty());
    }
    @Test public void emptyMapAndOutsideViewportDoNotCloseExistingRing() {
        click(400,300,1); pan(2291,58,1); assertFalse(click(400,300,1)); assertEquals("T001",menu.tileId());
        assertFalse(click(800,300,1)); assertTrue(menu.isOpen()); assertTrue(network.sent.isEmpty());
    }
    @Test public void anotherTileReplacesSelectionAndRestoresRoot() throws Exception {
        click(400,300,1); clickRoot(0);
        double x=KOMETileTestResources.x()/(double)LOTRGenLayerWorld.scale+LOTRGenLayerWorld.originX;
        double y=KOMETileTestResources.z()/(double)LOTRGenLayerWorld.scale+LOTRGenLayerWorld.originZ;
        centers().put("T100",new int[] {(int)x,(int)y}); pan((float)x,(float)y,1);
        assertTrue(click(400,300,1)); assertEquals("T100",menu.tileId()); assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());
    }
    @Test public void centerAndGapsPreserveNativeWaypointClickAndDragging() {
        click(400,300,1); assertFalse(click(400,300,0)); assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        assertFalse(click(400,240,0)); assertTrue(menu.isOpen()); assertTrue(network.sent.isEmpty());
    }
    @Test public void movementDisabledMoveAndBackCaptureGestureWithoutRequests() {
        click(400,300,1); assertTrue(clickRoot(0)); assertEquals(KOMEMapRadialMenu.State.MOVEMENT,menu.state());
        assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        assertTrue(click(400,240,0)); assertEquals(KOMEMapRadialMenu.State.MOVEMENT,menu.state());
        assertTrue(click(400,360,0)); assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());
        assertTrue(menu.isOpen()); assertTrue(network.sent.isEmpty());
        assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,false)); assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
    }
    @Test public void disabledCompaniesAndBattleShellSendNothing() {
        click(400,300,1); assertTrue(clickRoot(4)); assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());
        assertTrue(clickRoot(1)); assertEquals(KOMEMapRadialMenu.State.BATTLE,menu.state());
        assertTrue(click(400,240,0)); assertEquals(KOMEMapRadialMenu.State.BATTLE,menu.state());
        assertTrue(click(400,360,0)); assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state()); assertTrue(network.sent.isEmpty());
    }
    @Test public void infoUsesOnlyTheExistingTileInformationIntent() {
        click(400,300,1); assertTrue(clickRoot(2)); assertEquals(1,network.sent.size());
        assertTrue(network.sent.get(0) instanceof KOMEPacketConquestOpenCapture); assertEquals("T001",((KOMEPacketConquestOpenCapture)network.sent.get(0)).tileId);
    }
    @Test public void escapeClosesInteractionFirstAndOtherWaypointKeysPassThrough() {
        click(400,300,1); assertFalse(KOMEConquestMapOverlay.onMapKeyTyped(map,33)); assertTrue(menu.isOpen());
        assertTrue(KOMEConquestMapOverlay.onMapKeyTyped(map,1)); assertFalse(menu.isOpen());
        assertFalse(KOMEConquestMapOverlay.onMapKeyTyped(map,1));
    }
    @Test public void changingOrClosingScreenInvalidatesInteractionButResizeDoesNot() {
        click(400,300,1); KOMEConquestMapOverlay overlay=new KOMEConquestMapOverlay();
        overlay.onMapScreenChange(new GuiOpenEvent(map)); assertTrue(menu.isOpen());
        overlay.onMapScreenChange(new GuiOpenEvent(null)); assertFalse(menu.isOpen());
        assertFalse(KOMEConquestMapOverlay.onMapKeyTyped(map,1));
    }
    @Test public void projectedWaypointAnchorWinsAndWrongDimensionFallsBack() {
        KOMETileWaypointLink link=new KOMETileWaypointLink(); link.tileId="T001";link.dimensionId=KOMETileTestResources.dimension();
        link.waypointWorldX=KOMETileTestResources.worldX(2400);link.waypointWorldZ=KOMETileTestResources.worldZ(100);
        KOMEClientData.INSTANCE.tileWaypointLinksByTileId.put("T001",link);
        double[] anchor=KOMEConquestMapOverlay.radialAnchorMapPosition("T001");
        assertEquals(2400,anchor[0],0);assertEquals(100,anchor[1],0);
        link.dimensionId++;anchor=KOMEConquestMapOverlay.radialAnchorMapPosition("T001");assertArrayEquals(new double[] {2292,58},anchor,0);
    }
    @Test public void panZoomRecomputePositionWithoutClampingOrChangingMenu() {
        click(400,300,1); clickRoot(0); double[] anchor=KOMEConquestMapOverlay.radialAnchorMapPosition("T001");
        pan(2392,108,2);KOMEMapViewport view=KOMEConquestMapOverlay.viewport(map);
        assertEquals(200,view.screenX(anchor[0]),0);assertEquals(200,view.screenY(anchor[1]),0);
        assertTrue(click(200,260,0));assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());
        pan(3292,58,2);view=KOMEConquestMapOverlay.viewport(map);assertTrue(view.screenX(anchor[0]) < -KOMEMapRadialMenu.OUTER_RADIUS);
        assertTrue(menu.isOpen());assertEquals("T001",menu.tileId());assertTrue(network.sent.isEmpty());
    }
    @Test public void nativeWidgetsAndStandardButtonsTakePriorityOverRing() throws Exception {
        click(400,300,1); ((List)field(LOTRGuiMap.class,"mapWidgets").get(null)).add(new LOTRGuiMapWidget(330,230,60,"test",0,0));
        assertFalse(clickRoot(0));assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());
        ((List)field(LOTRGuiMap.class,"mapWidgets").get(null)).clear();
        ((List)field(GuiScreen.class,"buttonList").get(map)).add(new GuiButton(1,330,250,60,20,"test"));
        assertFalse(clickRoot(0));assertTrue(network.sent.isEmpty());
    }
    @Test public void aFreshOutsidePressDoesNotRetainThePriorSegmentDragCapture() {
        click(400,300,1); clickRoot(0); assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        assertFalse(click(400,300,0)); assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
    }
    @Test public void nativeWaypointIdentityRecoversAnAnchorWithoutInventingZeroCoordinates() {
        lotr.common.world.map.LOTRWaypoint waypoint=lotr.common.world.map.LOTRWaypoint.values()[0];
        KOMETileWaypointLink link=new KOMETileWaypointLink();link.tileId="T001";link.dimensionId=KOMETileTestResources.dimension();link.lotrWaypointKey=waypoint.getCodeName();
        KOMEClientData.INSTANCE.tileWaypointLinksByTileId.put("T001",link);
        double[] anchor=KOMEConquestMapOverlay.radialAnchorMapPosition("T001");
        assertEquals(waypoint.getXCoord()/(double)LOTRGenLayerWorld.scale+LOTRGenLayerWorld.originX,anchor[0],0);
        assertEquals(waypoint.getZCoord()/(double)LOTRGenLayerWorld.scale+LOTRGenLayerWorld.originZ,anchor[1],0);
        link.lotrWaypointKey="missing_waypoint";assertArrayEquals(new double[] {2292,58},KOMEConquestMapOverlay.radialAnchorMapPosition("T001"),0);
    }
    @Test public void legacyDestinationAndRoutePreviewModesKeepInputPriority() throws Exception {
        field(KOMEConquestMapOverlay.class,"destinationCompanyId").set(null,"C1");assertFalse(click(400,300,1));assertFalse(menu.isOpen());
        field(KOMEConquestMapOverlay.class,"destinationCompanyId").set(null,"");
        KOMEPacketCompanyMoveConfirmGui preview=new KOMEPacketCompanyMoveConfirmGui();
        field(KOMEConquestMapOverlay.class,"routePreviewMove").set(null,preview);
        List routes=(List)field(KOMEConquestMapOverlay.class,"routePreviewTiles").get(null);List previous=new ArrayList(routes);
        try {routes.add("T001");assertFalse(click(400,300,1));assertFalse(menu.isOpen());}finally{routes.clear();routes.addAll(previous);}
        assertTrue(network.sent.isEmpty());
    }
    @Test public void inspectionSuppressionCoversTheWholeInteractionAndRestoresAfterClose() {
        assertFalse(KOMEConquestMapOverlay.suppressMapInspection(map));
        assertFalse(KOMEConquestMapOverlay.suppressMapInspection(null));
        click(400,300,1); assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
        assertFalse(click(400,300,0)); // Fallback center is still pass-through, without a fictitious waypoint.
        assertTrue(menu.isOpen()); assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
        clickRoot(0); assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
        click(400,360,0); assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
        assertTrue(clickRoot(3)); assertFalse(menu.isOpen());
        assertFalse(KOMEConquestMapOverlay.suppressMapInspection(map)); assertEquals("",menu.tileId());
        assertFalse(KOMEConquestMapOverlay.onMapKeyTyped(map,1)); assertTrue(network.sent.isEmpty());
        // Closing is still a segment-origin press, never a map drag.
        assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(map));
        assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,false));
        assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        click(400,300,1); assertTrue(KOMEConquestMapOverlay.onMapKeyTyped(map,1));
        assertFalse(KOMEConquestMapOverlay.suppressMapInspection(map));
    }
    @Test public void waypointCenterClosesBeforePassingTheSameClickToNativeSelection() {
        click(400,300,1);
        KOMETileWaypointLink link = new KOMETileWaypointLink(); link.tileId="T001"; link.dimensionId=KOMETileTestResources.dimension();
        link.lotrWaypointKey=lotr.common.world.map.LOTRWaypoint.values()[0].getCodeName();
        KOMEClientData.INSTANCE.tileWaypointLinksByTileId.put("T001",link);
        double[] anchor=KOMEConquestMapOverlay.radialAnchorMapPosition("T001");
        pan((float)anchor[0],(float)anchor[1],1);
        assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
        assertFalse("The original native picker selects first, and the same click continues",nativeWaypointClick(lotr.common.world.map.LOTRWaypoint.values()[0],403,300));
        assertFalse(menu.isOpen()); assertEquals("",menu.tileId());
        assertFalse(KOMEConquestMapOverlay.suppressMapInspection(map));
        assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true)); assertTrue(network.sent.isEmpty());
    }
    @Test public void projectedCoordinatesAloneDoNotInventANativeWaypointClick() {
        click(400,300,1);
        KOMETileWaypointLink link=new KOMETileWaypointLink();link.tileId="T001";link.dimensionId=KOMETileTestResources.dimension();
        link.waypointWorldX=KOMETileTestResources.worldX(2292);link.waypointWorldZ=KOMETileTestResources.worldZ(58);
        KOMEClientData.INSTANCE.tileWaypointLinksByTileId.put("T001",link);
        assertFalse(click(406,300,0));assertTrue(menu.isOpen());
        assertFalse(click(400,300,0));assertTrue(menu.isOpen());
        assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
    }
    @Test public void screenCleanupAndAnotherMapDoNotKeepInspectionSuppressed() throws Exception {
        click(400,300,1); assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
        assertFalse(KOMEConquestMapOverlay.suppressMapInspection(KOMEAccessFixture.allocate(LOTRGuiMap.class)));
        new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));
        assertFalse(KOMEConquestMapOverlay.suppressMapInspection(map));
        assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
    }
    @Test public void nativeWaypointOnAnotherTileClosesRadialWithoutReplacingItsNativeSelection() throws Exception {
        click(400,300,1); assertEquals("T001",menu.tileId());
        lotr.common.world.map.LOTRWaypoint other=lotr.common.world.map.LOTRWaypoint.values()[1];
        KOMETileWaypointLink link=new KOMETileWaypointLink();link.tileId="T100";link.dimensionId=KOMETileTestResources.dimension();
        link.lotrWaypointKey=other.getCodeName();KOMEClientData.INSTANCE.tileWaypointLinksByTileId.put("T100",link);
        double[] anchor=KOMEConquestMapOverlay.radialAnchorMapPosition("T100");pan((float)anchor[0],(float)anchor[1],1);
        assertFalse(nativeWaypointClick(other,400,300));
        assertFalse(menu.isOpen());assertFalse(KOMEConquestMapOverlay.suppressMapInspection(map));
        assertSame(other,field(LOTRGuiMap.class,"selectedWaypoint").get(map));
        assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));assertTrue(network.sent.isEmpty());
    }
    @Test public void nativeWaypointWinsEvenWhenItsTargetOverlapsARadialSegment() throws Exception {
        click(400,300,1);
        lotr.common.world.map.LOTRWaypoint other=lotr.common.world.map.LOTRWaypoint.values()[1];
        assertFalse(nativeWaypointClick(other,360,260)); // Move's otherwise selectable grid cells.
        assertFalse(menu.isOpen());assertSame(other,field(LOTRGuiMap.class,"selectedWaypoint").get(map));
        assertTrue(network.sent.isEmpty());
    }
    @Test public void actualScaledResolutionFactorAlsoControlsProductionHitTesting() throws Exception {
        net.minecraft.client.Minecraft client=KOMEAccessFixture.allocate(net.minecraft.client.Minecraft.class);
        client.displayWidth=1920;client.displayHeight=1080;
        client.gameSettings=KOMEAccessFixture.allocate(net.minecraft.client.settings.GameSettings.class);
        field(net.minecraft.client.Minecraft.class,"mcLanguageManager").set(client,KOMEAccessFixture.allocate(NonUnicodeLanguage.class));
        Field slot=field(cpw.mods.fml.client.FMLClientHandler.class,"client");
        Object before=slot.get(cpw.mods.fml.client.FMLClientHandler.instance());
        try {
            slot.set(cpw.mods.fml.client.FMLClientHandler.instance(),client);
            for (int factor : new int[] {1,2,3,4}) {
                client.gameSettings.guiScale=factor;
                double scale=KOMEMapRadialMenu.scaleForGuiFactor(factor);
                assertEquals(scale,KOMEConquestMapOverlay.radialScale(),1e-9);
                click(400,300,1);
                KOMEMapRadialMenu.Segment move=menu.segments().get(0);
                assertTrue(click(400+(int)Math.round(move.labelX()*scale),300+(int)Math.round(move.labelY()*scale),0));
                assertEquals(KOMEMapRadialMenu.State.MOVEMENT,menu.state());
                assertTrue(click(400,300+(int)Math.round(57*scale),0));
                assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());
            }
            client.gameSettings.guiScale=3;client.gameSettings.forceUnicodeFont=true;
            assertEquals("Use the effective factor (2), not the requested setting (3)",1,KOMEConquestMapOverlay.radialScale(),0);
            client.gameSettings.forceUnicodeFont=false;client.gameSettings.guiScale=0;
            assertEquals(KOMEMapRadialMenu.scaleForGuiFactor(4),KOMEConquestMapOverlay.radialScale(),1e-9);
        } finally { slot.set(cpw.mods.fml.client.FMLClientHandler.instance(),before); }
    }
    public static class NonUnicodeLanguage extends net.minecraft.client.resources.LanguageManager {
        public NonUnicodeLanguage() { super(null,"en_US"); }
        @Override public boolean isCurrentLocaleUnicode() { return false; }
    }
    private boolean nativeWaypointClick(lotr.common.world.map.LOTRWaypoint waypoint,int x,int y) {
        try { field(LOTRGuiMap.class,"selectedWaypoint").set(map,waypoint); } catch (Exception failure) { throw new AssertionError(failure); }
        KOMEConquestMapOverlay.onNativeWaypointClick(map);
        return click(x,y,0);
    }
    private boolean clickRoot(int index) {
        KOMEMapRadialMenu.Segment segment=menu.segments().get(index);
        return click(400+(int)Math.round(segment.labelX()),300+(int)Math.round(segment.labelY()),0);
    }
    private boolean click(int x,int y,int button) {return KOMEConquestMapOverlay.onMapMouseClicked(map,x,y,button);}
    private void pan(float x,float y,float zoom) {try{field(LOTRGuiMap.class,"posX").setFloat(map,x);field(LOTRGuiMap.class,"posY").setFloat(map,y);field(LOTRGuiMap.class,"zoomScale").setFloat(map,zoom);}catch(Exception e){throw new AssertionError(e);}}
    private Map<String,Integer> colors() throws Exception {return (Map)field(KOMEConquestMapOverlay.class,"tileColorsById").get(null);}
    private Map<Integer,String> ids() throws Exception {return (Map)field(KOMEConquestMapOverlay.class,"tileIdsByColor").get(null);}
    private Map<String,int[]> centers() throws Exception {return (Map)field(KOMEConquestMapOverlay.class,"tileCentersById").get(null);}
    private void save(Field f) throws Exception {saved.put(f,f.get(null));}
    private void preserve(Map map) {savedMaps.put(map,new HashMap(map));map.clear();}
    private static Field field(Class<?> type,String name) throws Exception {Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    public static class RecordingNetwork extends SimpleNetworkWrapper {
        List<IMessage> sent;
        public RecordingNetwork() {super("radial-test");}
        @Override public void sendToServer(IMessage message) {sent.add(message);}
    }
}
