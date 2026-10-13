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
    private Object selectorConnection;
    @Before public void setup() throws Exception {
        map=KOMEAccessFixture.allocate(LOTRGuiMap.class); map.width=800; map.height=600;
        for (String name : new String[] {"mapXMin","mapXMax","mapYMin","mapYMax","mapWidth","mapHeight","mapWidgets"}) save(field(LOTRGuiMap.class,name));
        for (String name : new String[] {"tileRaster","showConquestTiles","destinationCompanyId","routePreviewMove","radialMap","radialLeftCaptured","radialDragMap","companySelector","companyPreview","selectorOnLeft"}) save(field(KOMEConquestMapOverlay.class,name));
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
        save(field(kome.common.KOMEAddon.class,"proxy"));
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
    @Test public void movementWithoutClientConnectionAndBackCaptureGestureWithoutRequests() {
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
    @Test public void visibleSegmentsTakePriorityOverOverlappingNativeWidgetsAndStandardButtons() throws Exception {
        click(400,300,1); ((List)field(LOTRGuiMap.class,"mapWidgets").get(null)).add(new LOTRGuiMapWidget(330,230,60,"test",0,0));
        assertTrue(clickRoot(0));assertEquals(KOMEMapRadialMenu.State.MOVEMENT,menu.state());
        menu.open("T001");
        ((List)field(LOTRGuiMap.class,"mapWidgets").get(null)).clear();
        ((List)field(GuiScreen.class,"buttonList").get(map)).add(new GuiButton(1,330,250,60,20,"test"));
        assertTrue(clickRoot(0));assertEquals(KOMEMapRadialMenu.State.MOVEMENT,menu.state());assertTrue(network.sent.isEmpty());
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
    @Test public void enabledSegmentWinsBeforeOverlappingNativeWaypointSelection() throws Exception {
        click(400,300,1);
        lotr.common.world.map.LOTRWaypoint other=lotr.common.world.map.LOTRWaypoint.values()[1];
        KOMEMapRadialMenu.Segment move=menu.segments().get(0);
        assertTrue(nativeWaypointClick(other,400+(int)Math.round(move.labelX()),300+(int)Math.round(move.labelY())));
        assertEquals(KOMEMapRadialMenu.State.MOVEMENT,menu.state());assertNull(field(LOTRGuiMap.class,"selectedWaypoint").get(map));
        assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
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
    @Test public void moveHereRequestsOneBoundedPageAndBackRestoresSameMoveSubmenu() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();
        assertEquals(1,network.sent.size());
        KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(0);
        assertEquals("T001",request.destination);assertEquals(8,request.limit);
        assertEquals(KOMECompanyDestinationSelector.Phase.LOADING,selector().phase());
        assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
        clickControl(KOMECompanyDestinationPanel.Control.BACK);
        assertNull(field(KOMEConquestMapOverlay.class,"companySelector").get(null));
        assertEquals("T001",menu.tileId());assertEquals(KOMEMapRadialMenu.State.MOVEMENT,menu.state());
        publish(proxy,KOMECompanyDestinationSelectorTest.page(request,
            KOMECompanyDestinationOptions.Result.OK,Collections.<KOMECompanyDestinationOptions.Row>emptyList(),false,0));
        assertNull(field(KOMEConquestMapOverlay.class,"companySelector").get(null));assertTrue(preview().points.isEmpty());
        assertPreviewRequestsOnly();
    }
    @Test public void acceptedRowSelectsCachedAuthoritativePointsAndClearNeverSubmits() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();deliverSelectorRow(proxy);
        KOMECompanyDestinationPanel.Layout layout=selectorLayout();KOMECompanyDestinationPanel.Rect row=layout.row(0);
        assertTrue(click(row.x+4,row.y+4,0));
        assertEquals("company",selector().selected().companyId);assertEquals(3,preview().points.size());
        assertEquals(2280,preview().points.get(0).x,0);assertEquals(2292,preview().points.get(2).x,0);
        assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        KOMECompanyDestinationPreview cached=preview();pan(2392,108,2);
        assertSame(cached,preview()); // Pan/zoom transforms points; it does not re-resolve the route.
        clickControl(KOMECompanyDestinationPanel.Control.CLEAR);assertNull(selector().selected());assertTrue(preview().points.isEmpty());
        assertPreviewRequestsOnly();assertNull(field(KOMEConquestMapOverlay.class,"routePreviewMove").get(null));
    }
    @Test public void nativeWaypointOutsidePanelClosesSelectorBeforeSameNativeClickContinues() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();deliverSelectorRow(proxy);
        selector().select("company");
        assertFalse(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,400,300,0));
        lotr.common.world.map.LOTRWaypoint other=lotr.common.world.map.LOTRWaypoint.values()[1];
        assertFalse(nativeWaypointClick(other,400,300));
        assertFalse(menu.isOpen());assertNull(field(KOMEConquestMapOverlay.class,"companySelector").get(null));
        assertSame(other,field(LOTRGuiMap.class,"selectedWaypoint").get(map));assertTrue(preview().points.isEmpty());
        assertFalse(KOMEConquestMapOverlay.suppressMapInspection(map));assertPreviewRequestsOnly();
    }
    @Test public void selectorControlsOwnClickBeforeNativePickerAndOutsideMapDragPassesThrough() throws Exception {
        connectSelector();openSelector();
        KOMECompanyDestinationPanel.Layout layout=selectorLayout();
        assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,layout.panel.x+10,layout.panel.y+10,0));
        assertTrue(menu.isOpen());assertNull(field(LOTRGuiMap.class,"selectedWaypoint").get(map));
        assertFalse(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,400,300,0));
        assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        assertFalse(click(400,300,0));assertTrue(menu.isOpen());assertPreviewRequestsOnly();
    }
    @Test public void escapeScreenChangeAndSessionReplacementClearPendingInteraction() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();assertTrue(KOMEConquestMapOverlay.onMapKeyTyped(map,1));
        assertFalse(menu.isOpen());assertTrue(preview().points.isEmpty());
        openSelector();new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));assertFalse(menu.isOpen());
        openSelector();proxy.destinationOptions().start(new Object());
        assertFalse(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,400,300,0));
        assertFalse(menu.isOpen());assertNull(field(KOMEConquestMapOverlay.class,"companySelector").get(null));assertTrue(preview().points.isEmpty());
        assertPreviewRequestsOnly();
    }
    @Test public void rightClickReplacementClearsSelectorAndOldResponseCannotRestoreIt() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();
        KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(0);
        double x=KOMETileTestResources.x()/(double)LOTRGenLayerWorld.scale+LOTRGenLayerWorld.originX;
        double y=KOMETileTestResources.z()/(double)LOTRGenLayerWorld.scale+LOTRGenLayerWorld.originZ;
        centers().put("T100",new int[] {(int)x,(int)y});pan((float)x,(float)y,1);
        assertTrue(click(400,300,1));assertEquals("T100",menu.tileId());assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());
        publish(proxy,KOMECompanyDestinationSelectorTest.page(request,
            KOMECompanyDestinationOptions.Result.OK,Collections.<KOMECompanyDestinationOptions.Row>emptyList(),false,0));
        assertNull(field(KOMEConquestMapOverlay.class,"companySelector").get(null));assertTrue(preview().points.isEmpty());assertPreviewRequestsOnly();
    }
    @Test public void panelPagingAndRowsWinOverOverlappingNativeControlsAndWaypointTargets() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();deliverSelectorRow(proxy);
        KOMECompanyDestinationPanel.Layout layout=selectorLayout();
        for(KOMECompanyDestinationPanel.Control c:Arrays.asList(KOMECompanyDestinationPanel.Control.PREVIOUS,KOMECompanyDestinationPanel.Control.NEXT)) {
            KOMECompanyDestinationPanel.Rect box=layout.controls.get(c);
            ((List)field(GuiScreen.class,"buttonList").get(map)).add(new GuiButton(1,box.x,box.y,box.width,box.height,"underlying filter"));
            ((List)field(LOTRGuiMap.class,"mapWidgets").get(null)).add(new LOTRGuiMapWidget(box.x,box.y,box.width,"filter",0,0));
            assertTrue(KOMEConquestMapOverlay.selectorOwnsPointer(map,box.x+1,box.y+1));
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,box.x+1,box.y+1,0));
            assertTrue(menu.isOpen());
        }
        KOMECompanyDestinationPanel.Rect row=layout.row(0);
        ((List)field(GuiScreen.class,"buttonList").get(map)).add(new GuiButton(2,row.x,row.y,row.width,row.height,"waypoint beneath"));
        assertTrue(click(row.x+1,row.y+1,0));assertNotNull(selector().selected());
        assertNull(field(LOTRGuiMap.class,"selectedWaypoint").get(map));assertPreviewRequestsOnly();
        assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true)); // Captured even after dragging out of panel.
        assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,false));
        assertFalse(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,400,300,0));
        assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
    }
    @Test public void selectorOwnsWheelAndAllPointerButtonsWithoutModalOutsideMap() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();
        KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(0);
        List<KOMECompanyDestinationOptions.Row> rows=new ArrayList<KOMECompanyDestinationOptions.Row>();
        for(int i=0;i<8;i++)rows.add(KOMECompanyDestinationSelectorTest.row("C"+i,kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW));
        publish(proxy,KOMECompanyDestinationSelectorTest.page(request,KOMECompanyDestinationOptions.Result.OK,rows,false,0));
        map.height=360;field(LOTRGuiMap.class,"mapYMax").setInt(null,360);field(LOTRGuiMap.class,"mapHeight").setInt(null,360);
        KOMECompanyDestinationPanel.Layout layout=selectorLayout();
        int x=layout.panel.x+10,y=layout.panel.y+10;
        assertTrue(KOMEConquestMapOverlay.onMapSelectorWheel(map,x,y,-120));assertEquals(1,selector().rowOffset());
        assertTrue(KOMEConquestMapOverlay.onMapSelectorWheel(map,x,y,120));assertEquals(0,selector().rowOffset());
        for(int button:new int[]{0,1,2})assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,x,y,button));
        assertFalse(KOMEConquestMapOverlay.onMapSelectorWheel(map,400,300,-120));
        ((List)field(GuiScreen.class,"buttonList").get(map)).add(new GuiButton(3,390,290,20,20,"outside filter"));
        assertFalse(click(400,300,0));assertTrue(menu.isOpen());assertPreviewRequestsOnly();
    }
    @Test public void selectorAlsoOccludesTheIndependentlyPolledKomeFilterBounds() throws Exception {
        connectSelector();openSelector();
        java.lang.reflect.Method bx=KOMEConquestMapOverlay.class.getDeclaredMethod("toggleButtonX",int.class);bx.setAccessible(true);
        java.lang.reflect.Method by=KOMEConquestMapOverlay.class.getDeclaredMethod("toggleButtonY");by.setAccessible(true);
        int x=((Integer)bx.invoke(null,0))+1,y=((Integer)by.invoke(null))+1;
        assertTrue(KOMEConquestMapOverlay.selectorOwnsPointer(map,x,y));
        assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,x,y,0));
        assertTrue(field(KOMEConquestMapOverlay.class,"showConquestTiles").getBoolean(null));
        assertPreviewRequestsOnly();
    }
    @Test public void disabledSegmentConsumesWaypointClickWithoutActionOrMapDrag() throws Exception {
        click(400,300,1);KOMEMapRadialMenu.Segment disabled=menu.segments().get(4);
        assertFalse(disabled.action.enabled);
        assertTrue(nativeWaypointClick(lotr.common.world.map.LOTRWaypoint.values()[1],
            400+(int)Math.round(disabled.labelX()),300+(int)Math.round(disabled.labelY())));
        assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());assertNull(field(LOTRGuiMap.class,"selectedWaypoint").get(map));
        assertTrue(network.sent.isEmpty());assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
    }
    @Test public void nativeWaypointInGapCenterAndOutsideReceivesOriginalClick() throws Exception {
        lotr.common.world.map.LOTRWaypoint target=lotr.common.world.map.LOTRWaypoint.values()[1];
        for(int[] point:new int[][]{{400,240},{400,300},{400,210}}) {
            field(LOTRGuiMap.class,"selectedWaypoint").set(map,null);click(400,300,1);
            assertFalse(KOMEConquestMapOverlay.radialOwnsPointer(map,point[0],point[1]));
            assertFalse(nativeWaypointClick(target,point[0],point[1]));assertFalse(menu.isOpen());
            assertSame(target,field(LOTRGuiMap.class,"selectedWaypoint").get(map));
            assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        }
        assertTrue(network.sent.isEmpty());
    }
    @Test public void rootAndNestedOwnershipExactlyMatchesDynamicRenderedPixelCells() {
        click(400,300,1);
        for(KOMEMapRadialMenu.State state:new KOMEMapRadialMenu.State[]{KOMEMapRadialMenu.State.ROOT,KOMEMapRadialMenu.State.MOVEMENT,KOMEMapRadialMenu.State.BATTLE}) {
            menu.open("T001");if(state==KOMEMapRadialMenu.State.MOVEMENT)menu.activate(0);if(state==KOMEMapRadialMenu.State.BATTLE)menu.activate(1);
            assertEquals(state,menu.state());
            for(int y=-76;y<=76;y+=2)for(int x=-76;x<=76;x+=2)
                assertEquals(menu.hit(x,y,1)>=0,KOMEConquestMapOverlay.radialOwnsPointer(map,400+x,300+y));
            assertFalse(click(400,300,0));assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        }
        assertTrue(network.sent.isEmpty());
    }
    @Test public void confirmMoveOwnsOverlappingNativeControlAndOnlyExplicitClickSubmits() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();deliverSelectorRow(proxy);
        KOMECompanyDestinationPanel.Rect row=selectorLayout().row(0);assertTrue(click(row.x+2,row.y+2,0));
        assertPreviewRequestsOnly();assertFalse(preview().points.isEmpty());
        KOMECompanyDestinationPanel.Rect confirm=selectorLayout().controls.get(KOMECompanyDestinationPanel.Control.CONFIRM);
        ((List<GuiButton>)field(GuiScreen.class,"buttonList").get(map)).add(new GuiButton(999,confirm.x,confirm.y,confirm.width,confirm.height,"native control"));
        assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,confirm.x+2,confirm.y+2,0));
        assertEquals(2,network.sent.size());assertTrue(network.sent.get(1) instanceof KOMEPacketCompanyMovementRequest);
        assertEquals(KOMECompanyDestinationSelector.Phase.SUBMITTING,selector().phase());assertTrue(preview().points.isEmpty());
        assertNull(field(LOTRGuiMap.class,"selectedWaypoint").get(map));assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        assertTrue(click(confirm.x+2,confirm.y+2,0));assertEquals(2,network.sent.size());
        KOMEPacketCompanyMovementRequest move=(KOMEPacketCompanyMovementRequest)network.sent.get(1);
        KOMECompanyMovementResult result=new KOMECompanyMovementResult(move.session,move.sequence,move.previewSequence,move.companyId,move.destination,
            KOMECompanyMovementResult.Outcome.ORDER_ACCEPTED,"M1","waiting_next_step","T002",1,2,"","");
        proxy.acceptCompanyMovementResult(result,selectorConnection);
        assertEquals(3,network.sent.size());assertTrue(network.sent.get(2) instanceof KOMEPacketCompanyDestinationRequest);
        assertEquals(KOMECompanyDestinationSelector.Phase.LOADING,selector().phase());assertTrue(preview().points.isEmpty());assertTrue(selector().rows().isEmpty());
        assertTrue(selector().notice().contains("Arrival not completed"));
        proxy.acceptCompanyMovementResult(result,selectorConnection);assertEquals(3,network.sent.size());
        assertSame(map,field(KOMEConquestMapOverlay.class,"radialMap").get(null)); // Remains on the map.
    }
    @Test public void visiblePagingArrowsAndNextReachAllEighteenCompaniesAndPreviousReturnsToLastWindow() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();Set<String> seen=new HashSet<String>();
        for(int serverPage=0;serverPage<3;serverPage++) {
            KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(network.sent.size()-1);
            if(serverPage>0)assertEquals("C"+(serverPage*8),request.afterCompanyId);
            List<KOMECompanyDestinationOptions.Row> rows=new ArrayList<KOMECompanyDestinationOptions.Row>();
            for(int i=serverPage*8+1;i<=Math.min(18,(serverPage+1)*8);i++)rows.add(KOMECompanyDestinationSelectorTest.row("C"+i,
                kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW));
            publish(proxy,KOMECompanyDestinationSelectorTest.page(request,KOMECompanyDestinationOptions.Result.OK,rows,serverPage<2,0));
            while(true) {
                KOMECompanyDestinationPanel.Layout layout=selectorLayout();int offset=selector().rowOffset(),sent=network.sent.size();
                for(int i=offset;i<Math.min(rows.size(),offset+layout.capacity);i++)assertTrue(seen.add(selector().rows().get(i).companyId));
                if(offset+layout.capacity>=rows.size())break;
                clickControl(KOMECompanyDestinationPanel.Control.NEXT);assertEquals(sent,network.sent.size());
            }
            if(serverPage<2) {
                clickControl(KOMECompanyDestinationPanel.Control.ROWS_DOWN);
                assertEquals(serverPage+2,network.sent.size());assertEquals(KOMECompanyDestinationSelector.Phase.LOADING,selector().phase());
            }
        }
        assertEquals(18,seen.size());assertFalse(selector().hasNext());
        clickControl(KOMECompanyDestinationPanel.Control.ROWS_UP);
        assertEquals(4,network.sent.size());assertEquals("C8",((KOMEPacketCompanyDestinationRequest)network.sent.get(3)).afterCompanyId);
        List<KOMECompanyDestinationOptions.Row> previous=new ArrayList<KOMECompanyDestinationOptions.Row>();
        for(int i=9;i<=16;i++)previous.add(KOMECompanyDestinationSelectorTest.row("C"+i,kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW));
        publish(proxy,KOMECompanyDestinationSelectorTest.page((KOMEPacketCompanyDestinationRequest)network.sent.get(3),KOMECompanyDestinationOptions.Result.OK,previous,true,0));
        int capacity=selectorLayout().capacity;assertEquals(7/capacity*capacity,selector().rowOffset());assertPreviewRequestsOnly();
    }
    @Test public void activeBattleShortcutOpensTheSameSelectorAndBoundedDestinationRequestAsMoveHere() throws Exception {
        KOMEClientProxy proxy=connectSelector();
        net.minecraft.client.Minecraft client=KOMEAccessFixture.allocate(net.minecraft.client.Minecraft.class);client.currentScreen=map;
        Field slot=field(cpw.mods.fml.client.FMLClientHandler.class,"client");Object before=slot.get(cpw.mods.fml.client.FMLClientHandler.instance());
        try {
            slot.set(cpw.mods.fml.client.FMLClientHandler.instance(),client);
            click(400,300,1);clickRoot(1);assertEquals(1,network.sent.size());
            KOMEPacketJoinBattleViewRequest context=(KOMEPacketJoinBattleViewRequest)network.sent.get(0);
            KOMEPacketJoinBattleViewResponse view=new KOMEPacketJoinBattleViewResponse();view.tileId=context.tileId;view.correlation=context.correlation;
            view.conflictId="CF_REAL";view.conflictState=KOMEConflictRecord.State.ORDINARY;
            proxy.acceptJoinBattleView(view,selectorConnection);assertEquals(KOMEMapRadialMenu.Action.REINFORCE,menu.segments().get(0).action);
            assertTrue(click(400,240,0));assertEquals(2,network.sent.size());
            KOMEPacketCompanyDestinationRequest battle=(KOMEPacketCompanyDestinationRequest)network.sent.get(1);
            assertEquals("T001",battle.destination);assertEquals(8,battle.limit);assertEquals(KOMECompanyDestinationSelector.Phase.LOADING,selector().phase());
            clickControl(KOMECompanyDestinationPanel.Control.BACK);click(400,360,0);clickRoot(0);click(400,240,0);
            assertTrue(network.sent.get(2) instanceof KOMEPacketJoinBattleViewRequest);
            KOMEPacketCompanyDestinationRequest movement=(KOMEPacketCompanyDestinationRequest)network.sent.get(3);
            assertEquals(battle.destination,movement.destination);assertEquals(battle.limit,movement.limit);
            assertTrue(battle.afterCompanyId.isEmpty());assertTrue(movement.afterCompanyId.isEmpty());assertNotEquals(battle.session,movement.session);
        } finally {slot.set(cpw.mods.fml.client.FMLClientHandler.instance(),before);}
    }
    @Test public void cancelledAndWrongConnectionBattleContextCannotReviveInteraction() throws Exception {
        KOMEClientProxy proxy=connectSelector();click(400,300,1);clickRoot(1);
        KOMEPacketJoinBattleViewRequest context=(KOMEPacketJoinBattleViewRequest)network.sent.get(0);
        KOMEPacketJoinBattleViewResponse view=new KOMEPacketJoinBattleViewResponse();view.tileId=context.tileId;view.correlation=context.correlation;view.conflictId="CF1";view.conflictState=KOMEConflictRecord.State.ORDINARY;
        proxy.acceptJoinBattleView(view,new Object());assertEquals(KOMEMapRadialMenu.Action.BATTLE_ACTIONS,menu.segments().get(0).action);
        new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));proxy.acceptJoinBattleView(view,selectorConnection);
        assertFalse(menu.isOpen());assertEquals(1,network.sent.size());
    }
    @Test public void combinedButtonWaitsForCommittedArrivalAndClosingCancelsLateJoinView() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();deliverSelectorRow(proxy,kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT);
        KOMECompanyDestinationPanel.Rect row=selectorLayout().row(0);assertTrue(click(row.x+2,row.y+2,0));
        assertTrue(selector().canConfirmAndJoin());assertNotNull(selectorLayout().controls.get(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN));
        clickControl(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN);
        assertEquals(2,network.sent.size());KOMEPacketCompanyMovementRequest move=(KOMEPacketCompanyMovementRequest)network.sent.get(1);
        KOMECompanyMovementResult result=new KOMECompanyMovementResult(move.session,move.sequence,move.previewSequence,move.companyId,move.destination,
            KOMECompanyMovementResult.Outcome.ARRIVED,"M1",KOMEArmyMovementOrder.CONFLICT_HELD,move.destination,2,2,"","");
        proxy.acceptCompanyMovementResult(result,selectorConnection);assertEquals(3,network.sent.size());
        KOMEPacketJoinBattleViewRequest join=(KOMEPacketJoinBattleViewRequest)network.sent.get(2);assertEquals(move.destination,join.tileId);
        assertEquals(KOMECompanyDestinationSelector.Phase.JOINING,selector().phase());
        new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));
        KOMEPacketJoinBattleViewResponse late=new KOMEPacketJoinBattleViewResponse();late.tileId=join.tileId;late.correlation=join.correlation;
        proxy.acceptJoinBattleView(late,selectorConnection);assertEquals(3,network.sent.size());assertFalse(menu.isOpen());
    }
    @Test public void backAfterHostileOwnershipBecomesFriendlyDoesNotRestoreAttackOrAcceptOldContext() throws Exception {
        try(BattlePresentationClient client=new BattlePresentationClient()) {
            KOMEClientProxy proxy=connectSelector();client.owner("mordor");click(400,300,1);clickRoot(1);
            KOMEPacketJoinBattleViewRequest old=(KOMEPacketJoinBattleViewRequest)network.sent.get(0);
            assertEquals(KOMEMapRadialMenu.Action.ATTACK,menu.segments().get(0).action);click(400,240,0);
            client.owner("gondor");clickControl(KOMECompanyDestinationPanel.Control.BACK);
            assertEquals(KOMEMapRadialMenu.Action.BATTLE_ACTIONS,menu.segments().get(0).action);
            KOMEPacketJoinBattleViewRequest fresh=(KOMEPacketJoinBattleViewRequest)network.sent.get(2);assertNotEquals(old.correlation,fresh.correlation);
            proxy.acceptJoinBattleView(battleView(old,KOMEConflictRecord.State.ORDINARY),selectorConnection);
            assertEquals(KOMEMapRadialMenu.Action.BATTLE_ACTIONS,menu.segments().get(0).action);
            proxy.acceptJoinBattleView(battleView(fresh,null),selectorConnection);
            assertEquals(KOMEMapRadialMenu.Action.BATTLE_ACTIONS,menu.segments().get(0).action);assertEquals(3,network.sent.size());
        }
    }
    @Test public void backAfterConflictStartsRefreshesAttackToReinforce() throws Exception {
        try(BattlePresentationClient client=new BattlePresentationClient()) {
            KOMEClientProxy proxy=connectSelector();client.owner("mordor");click(400,300,1);clickRoot(1);click(400,240,0);
            clickControl(KOMECompanyDestinationPanel.Control.BACK);
            assertEquals(KOMEMapRadialMenu.Action.ATTACK,menu.segments().get(0).action);
            KOMEPacketJoinBattleViewRequest fresh=(KOMEPacketJoinBattleViewRequest)network.sent.get(2);
            proxy.acceptJoinBattleView(battleView(fresh,KOMEConflictRecord.State.ORDINARY),selectorConnection);
            assertEquals(KOMEMapRadialMenu.Action.REINFORCE,menu.segments().get(0).action);assertEquals(3,network.sent.size());
        }
    }
    @Test public void backAfterConflictEndsDoesNotRestoreReinforceAndReturnsCurrentAttack() throws Exception {backAfterConflictEnds("mordor",KOMEMapRadialMenu.Action.ATTACK);}
    @Test public void backAfterConflictEndsOnFriendlyTileReturnsDisabledShell() throws Exception {backAfterConflictEnds("gondor",KOMEMapRadialMenu.Action.BATTLE_ACTIONS);}
    private void backAfterConflictEnds(String owner,KOMEMapRadialMenu.Action expected) throws Exception {
        try(BattlePresentationClient client=new BattlePresentationClient()) {
            KOMEClientProxy proxy=connectSelector();client.owner("mordor");click(400,300,1);clickRoot(1);
            KOMEPacketJoinBattleViewRequest original=(KOMEPacketJoinBattleViewRequest)network.sent.get(0);
            proxy.acceptJoinBattleView(battleView(original,KOMEConflictRecord.State.ORDINARY),selectorConnection);
            assertEquals(KOMEMapRadialMenu.Action.REINFORCE,menu.segments().get(0).action);click(400,240,0);
            client.owner(owner);clickControl(KOMECompanyDestinationPanel.Control.BACK);
            assertEquals(expected,menu.segments().get(0).action);assertNull(selector());assertEquals(KOMEMapRadialMenu.State.BATTLE,menu.state());
            KOMEPacketJoinBattleViewRequest fresh=(KOMEPacketJoinBattleViewRequest)network.sent.get(2);
            assertEquals(original.tileId,fresh.tileId);assertNotEquals(original.correlation,fresh.correlation);
            proxy.acceptJoinBattleView(battleView(fresh,KOMEConflictRecord.State.ENDED),selectorConnection);
            assertEquals(expected,menu.segments().get(0).action);
            for(IMessage message:network.sent)assertTrue(message instanceof KOMEPacketJoinBattleViewRequest||message instanceof KOMEPacketCompanyDestinationRequest);
        }
    }
    @Test public void createAndJoinDirectlySubmitExactMovedCompanyWithoutDisplayingPicker() throws Exception {
        for(kome.common.command.KOMECompanyMovementService.Consequence consequence:Arrays.asList(
                kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT,
                kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_JOIN_CONFLICT)) {
            try(DirectClient client=new DirectClient()) {
                KOMEClientProxy proxy=connectSelector();KOMECompanyMovementResult arrival=prepareDirect(proxy,consequence);
                KOMEPacketJoinBattleViewResponse view=directView("company",true,1);
                // Another legal company must never replace the company explicitly moved.
                view.companies.add(companyRow("other",true,"Other company"));
                proxy.acceptJoinBattleView(view,selectorConnection);
                assertSame(map,client.mc.currentScreen);assertEquals(0,client.mc.screenChanges);assertEquals(4,network.sent.size());
                KOMEPacketJoinBattleSelectionRequest join=(KOMEPacketJoinBattleSelectionRequest)network.sent.get(3);
                assertEquals(arrival.companyId,join.companyId);assertEquals(view.tileId,join.tileId);
                assertEquals(view.conflictId,join.conflictId);assertEquals(view.conflictRevision,join.conflictRevision);assertEquals(view.actionToken,join.actionToken);
                proxy.acceptCompanyMovementResult(arrival,selectorConnection);proxy.acceptJoinBattleView(view,selectorConnection);
                assertEquals("Duplicate arrival/view must not send another Join",4,network.sent.size());
                KOMEPacketJoinBattleSelectionResult result=directResult(join,view,true);
                proxy.acceptJoinBattleSelectionResult(result,selectorConnection);
                assertNull(client.mc.currentScreen);assertEquals(1,client.mc.screenChanges);assertEquals(result.message,client.player.messages.get(0));
                proxy.acceptJoinBattleSelectionResult(result,selectorConnection);
                assertEquals(1,client.mc.screenChanges);assertEquals(4,network.sent.size());
            }
            network.sent.clear();
        }
    }
    @Test public void directDenialPreservesMovementAndOpensUsableNormalGuiWithoutAnotherRequest() throws Exception {
        try(DirectClient client=new DirectClient()) {
            KOMEClientProxy proxy=connectSelector();KOMECompanyMovementResult arrival=prepareDirect(proxy,kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_JOIN_CONFLICT);
            KOMEPacketJoinBattleViewResponse view=directView("company",true,1);view.companies.add(companyRow("alternate",true,"Alternate"));
            proxy.acceptJoinBattleView(view,selectorConnection);KOMEPacketJoinBattleSelectionRequest join=(KOMEPacketJoinBattleSelectionRequest)network.sent.get(3);
            KOMEPacketJoinBattleSelectionResult denied=directResult(join,view,false);proxy.acceptJoinBattleSelectionResult(denied,selectorConnection);
            assertTrue(client.mc.currentScreen instanceof kome.client.gui.KOMEGuiJoinBattle);
            kome.client.gui.KOMEGuiJoinBattle screen=(kome.client.gui.KOMEGuiJoinBattle)client.mc.currentScreen;
            assertEquals(denied.message,screen.status());assertEquals(4,network.sent.size());assertTrue(client.player.messages.isEmpty());
            assertEquals(KOMECompanyMovementResult.Outcome.ARRIVED,arrival.outcome);assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,arrival.orderStatus);
            assertTrue(field(kome.client.gui.KOMEGuiJoinBattle.class,"afterMovement").getBoolean(screen));
            assertEquals("",((kome.client.gui.KOMEJoinBattleViewModel)field(kome.client.gui.KOMEGuiJoinBattle.class,"model").get(screen)).selectedCompanyId());
            // Fallback allows an explicit alternate company choice, without automatically choosing it.
            java.lang.reflect.Method action=kome.client.gui.KOMEGuiJoinBattle.class.getDeclaredMethod("actionPerformed",GuiButton.class);action.setAccessible(true);
            action.invoke(screen,new GuiButton(101,0,0,"Alternate"));action.invoke(screen,new GuiButton(1,0,0,"Join"));
            assertEquals(5,network.sent.size());assertEquals("alternate",((KOMEPacketJoinBattleSelectionRequest)network.sent.get(4)).companyId);
            assertEquals(1,network.sent.stream().filter(p->p instanceof KOMEPacketCompanyMovementRequest).count());
        }
    }
    @Test public void missingBlockedOrAmbiguousMovedCompanyFallsBackWithoutGuessing() throws Exception {
        for(int scenario=0;scenario<6;scenario++) {
            try(DirectClient client=new DirectClient()) {
                KOMEClientProxy proxy=connectSelector();prepareDirect(proxy,kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT);
                KOMEPacketJoinBattleViewResponse view=directView(scenario==0?"other":"company",scenario!=1,scenario==2?2:1);
                view.companies.add(companyRow("alternate",true,"Alternate"));
                if(scenario==3)view.actionToken="";
                if(scenario==4){view.reason=KOMEJoinBattleService.Reason.WITHDRAWN;view.reasonText="You have withdrawn.";}
                if(scenario==5)view.currentDeploymentConflict="CF_EXISTING";
                proxy.acceptJoinBattleView(view,selectorConnection);
                assertEquals(3,network.sent.size());assertTrue(client.mc.currentScreen instanceof kome.client.gui.KOMEGuiJoinBattle);
                kome.client.gui.KOMEJoinBattleViewModel model=(kome.client.gui.KOMEJoinBattleViewModel)field(kome.client.gui.KOMEGuiJoinBattle.class,"model").get(client.mc.currentScreen);
                assertNotEquals("alternate",model.selectedCompanyId());
                if(scenario==2)assertEquals("company",model.selectedCompanyId());
                assertTrue(field(kome.client.gui.KOMEGuiJoinBattle.class,"afterMovement").getBoolean(client.mc.currentScreen));
                for(Object button:(List)field(GuiScreen.class,"buttonList").get(client.mc.currentScreen))
                    if(((GuiButton)button).id>=100)assertFalse(((GuiButton)button).displayString.contains("[company]"));
            }
            network.sent.clear();
        }
    }
    @Test public void staleViewCloseScreenDestinationAndReconnectCannotSubmitDelayedJoin() throws Exception {
        for(int lifecycle=0;lifecycle<7;lifecycle++) {
            try(DirectClient client=new DirectClient()) {
                KOMEClientProxy proxy=connectSelector();KOMECompanyMovementResult arrival=prepareDirect(proxy,kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT);
                KOMEPacketJoinBattleViewResponse view=directView("company",true,1);Object connection=selectorConnection;
                if(lifecycle==0)assertTrue(KOMEConquestMapOverlay.onMapKeyTyped(map,org.lwjgl.input.Keyboard.KEY_ESCAPE));
                if(lifecycle==1)client.mc.displayGuiScreen(new GuiScreen());
                if(lifecycle==2)selector().open("T002");
                if(lifecycle==3){connection=new Object();proxy.destinationOptions().start(connection);}
                if(lifecycle==4)view.correlation=UUID.randomUUID();
                if(lifecycle==5)view.tileId="T002";
                if(lifecycle==6)proxy.acceptCompanyMovementResult(new KOMECompanyMovementResult(UUID.randomUUID(),arrival.sequence,arrival.previewSequence,
                    arrival.companyId,arrival.destination,arrival.outcome,arrival.orderId,arrival.orderStatus,arrival.currentTile,2,2,"",""),connection);
                proxy.acceptJoinBattleView(view,connection);assertEquals(3,network.sent.size());
            }
            network.sent.clear();
        }
    }
    @Test public void replacedSessionOrScreenCannotCloseFromLateDirectResult() throws Exception {
        for(int lifecycle=0;lifecycle<4;lifecycle++) {
            try(DirectClient client=new DirectClient()) {
                KOMEClientProxy proxy=connectSelector();prepareDirect(proxy,kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_JOIN_CONFLICT);
                KOMEPacketJoinBattleViewResponse view=directView("company",true,1);proxy.acceptJoinBattleView(view,selectorConnection);
                KOMEPacketJoinBattleSelectionResult success=directResult((KOMEPacketJoinBattleSelectionRequest)network.sent.get(3),view,true);
                Object connection=selectorConnection;
                if(lifecycle==0)assertTrue(KOMEConquestMapOverlay.onMapKeyTyped(map,org.lwjgl.input.Keyboard.KEY_ESCAPE));
                if(lifecycle==1)client.mc.displayGuiScreen(new GuiScreen());
                if(lifecycle==2)selector().open("T002");
                if(lifecycle==3){connection=new Object();proxy.destinationOptions().start(connection);}
                GuiScreen replacement=client.mc.currentScreen;int changes=client.mc.screenChanges;
                proxy.acceptJoinBattleSelectionResult(success,connection);assertSame(replacement,client.mc.currentScreen);assertEquals(changes,client.mc.screenChanges);assertEquals(4,network.sent.size());
            }
            network.sent.clear();
        }
    }
    @Test public void wrongDirectResultAndStaleViewCannotCloseOrSendAnotherDeployment() throws Exception {
        try(DirectClient client=new DirectClient()) {
            KOMEClientProxy proxy=connectSelector();prepareDirect(proxy,kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_JOIN_CONFLICT);
            KOMEPacketJoinBattleViewResponse view=directView("company",true,1);proxy.acceptJoinBattleView(view,selectorConnection);
            KOMEPacketJoinBattleSelectionResult wrong=directResult((KOMEPacketJoinBattleSelectionRequest)network.sent.get(3),view,true);wrong.correlation=UUID.randomUUID();
            proxy.acceptJoinBattleSelectionResult(wrong,selectorConnection);assertSame(map,client.mc.currentScreen);
            view.correlation=UUID.randomUUID();proxy.acceptJoinBattleView(view,selectorConnection);
            KOMEPacketJoinBattleSelectionResult late=directResult((KOMEPacketJoinBattleSelectionRequest)network.sent.get(3),view,true);
            proxy.acceptJoinBattleSelectionResult(late,selectorConnection);assertSame(map,client.mc.currentScreen);assertEquals(4,network.sent.size());
        }
    }
    @Test public void reusedViewUuidWithChangedConflictRevisionAbortsDirectContinuation() throws Exception {
        try(DirectClient client=new DirectClient()) {
            KOMEClientProxy proxy=connectSelector();prepareDirect(proxy,kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT);
            KOMEPacketJoinBattleViewResponse view=directView("company",true,1);proxy.acceptJoinBattleView(view,selectorConnection);
            KOMEPacketJoinBattleSelectionResult late=directResult((KOMEPacketJoinBattleSelectionRequest)network.sent.get(3),view,true);
            view.conflictRevision++;proxy.acceptJoinBattleView(view,selectorConnection);proxy.acceptJoinBattleSelectionResult(late,selectorConnection);
            assertSame(map,client.mc.currentScreen);assertEquals(0,client.mc.screenChanges);assertEquals(4,network.sent.size());
        }
    }
    private KOMECompanyMovementResult prepareDirect(KOMEClientProxy proxy,kome.common.command.KOMECompanyMovementService.Consequence consequence) throws Exception {
        openSelector();deliverSelectorRow(proxy,consequence);KOMECompanyDestinationPanel.Rect row=selectorLayout().row(0);assertTrue(click(row.x+2,row.y+2,0));
        clickControl(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN);assertEquals(2,network.sent.size());
        KOMEPacketCompanyMovementRequest move=(KOMEPacketCompanyMovementRequest)network.sent.get(1);
        KOMECompanyMovementResult arrived=new KOMECompanyMovementResult(move.session,move.sequence,move.previewSequence,move.companyId,move.destination,
            KOMECompanyMovementResult.Outcome.ARRIVED,"M1",KOMEArmyMovementOrder.CONFLICT_HELD,move.destination,2,2,"","");
        proxy.acceptCompanyMovementResult(arrived,selectorConnection);assertEquals(3,network.sent.size());return arrived;
    }
    private KOMEPacketJoinBattleViewResponse directView(String company,boolean selectable,int copies) throws Exception {
        KOMEPacketJoinBattleViewRequest request=(KOMEPacketJoinBattleViewRequest)network.sent.get(2);
        KOMEPacketJoinBattleViewResponse view=battleView(request,KOMEConflictRecord.State.ORDINARY);view.conflictRevision=17;
        view.reason=KOMEJoinBattleService.Reason.ALLOWED;view.reasonText="Allowed";view.actionToken="0123456789abcdef0123456789abcdef";
        for(int i=0;i<copies;i++)view.companies.add(companyRow(company,selectable,"Company anchor "+i));
        return KOMEPopulationWire.copyForPublication(view,KOMEPacketJoinBattleViewResponse::new);
    }
    private static KOMEPacketJoinBattleViewResponse.CompanyRow companyRow(String company,boolean selectable,String name) throws Exception {
        java.lang.reflect.Constructor<KOMEPacketJoinBattleViewResponse.CompanyRow> constructor=KOMEPacketJoinBattleViewResponse.CompanyRow.class.getDeclaredConstructor(
            String.class,String.class,String.class,int.class,boolean.class);constructor.setAccessible(true);return constructor.newInstance(company,name,"gondor",2,selectable);
    }
    private static KOMEPacketJoinBattleSelectionResult directResult(KOMEPacketJoinBattleSelectionRequest request,KOMEPacketJoinBattleViewResponse view,boolean success) {
        KOMEPacketJoinBattleSelectionResult result=new KOMEPacketJoinBattleSelectionResult();result.correlation=request.correlation;
        result.status=success?KOMEPacketJoinBattleSelectionResult.Status.DEPLOYED:KOMEPacketJoinBattleSelectionResult.Status.REJECTED;
        result.reason=success?KOMEJoinBattleService.Reason.ALLOWED:KOMEJoinBattleService.Reason.WITHDRAWN;
        result.message=success?"Deployment confirmed.":"You have withdrawn from this conflict.";result.receiptId="JB_DIRECT";
        result.current=KOMEPopulationWire.copyForPublication(view,KOMEPacketJoinBattleViewResponse::new);
        if(success){result.current.currentDeploymentTile=request.tileId;result.current.currentDeploymentConflict=request.conflictId;result.current.currentDeploymentState="IN_BATTLE";}
        return result;
    }
    private final class DirectClient implements AutoCloseable {
        final Field slot=field(cpw.mods.fml.client.FMLClientHandler.class,"client");
        final Field singleton=field(net.minecraft.client.Minecraft.class,"theMinecraft");
        final Object previous=slot.get(cpw.mods.fml.client.FMLClientHandler.instance()),previousSingleton=singleton.get(null);
        final DirectMinecraft mc=KOMEAccessFixture.allocate(DirectMinecraft.class);
        final DirectPlayer player=KOMEAccessFixture.allocate(DirectPlayer.class);
        DirectClient() throws Exception {
            player.messages=new ArrayList<String>();mc.thePlayer=player;mc.currentScreen=map;
            slot.set(cpw.mods.fml.client.FMLClientHandler.instance(),mc);singleton.set(null,mc);
        }
        public void close() throws Exception {new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));slot.set(cpw.mods.fml.client.FMLClientHandler.instance(),previous);singleton.set(null,previousSingleton);}
    }
    public static class DirectMinecraft extends net.minecraft.client.Minecraft {
        int screenChanges;
        private DirectMinecraft(){super(null,0,0,false,false,null,null,null,null,null,null,null);}
        @Override public void displayGuiScreen(GuiScreen next) {
            new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(next));
            if(currentScreen!=null&&currentScreen!=next)currentScreen.onGuiClosed();
            currentScreen=next;screenChanges++;
            if(next!=null)try {field(GuiScreen.class,"mc").set(next,this);next.width=800;next.height=600;next.initGui();}
            catch(Exception failure){throw new AssertionError(failure);}
        }
    }
    public static class DirectPlayer extends net.minecraft.client.entity.EntityClientPlayerMP {
        List<String> messages;
        private DirectPlayer(){super(null,null,null,null,null);}
        @Override public void addChatMessage(net.minecraft.util.IChatComponent message){messages.add(message.getUnformattedText());}
    }
    private static KOMEPacketJoinBattleViewResponse battleView(KOMEPacketJoinBattleViewRequest request,KOMEConflictRecord.State state) {
        KOMEPacketJoinBattleViewResponse view=new KOMEPacketJoinBattleViewResponse();view.tileId=request.tileId;view.correlation=request.correlation;
        view.conflictState=state;view.conflictId=state==null?"":"CF_CURRENT";return view;
    }
    /** Inert client with a native pledge and a published ownership snapshot. */
    private final class BattlePresentationClient implements AutoCloseable {
        final Field slot=field(cpw.mods.fml.client.FMLClientHandler.class,"client");
        final Field owners=field(KOMEClientData.class,"conquestRenderOwners");
        final Object oldClient=slot.get(cpw.mods.fml.client.FMLClientHandler.instance()),oldOwners=owners.get(KOMEClientData.INSTANCE);
        final UUID id=new UUID(84,300);
        final Map<UUID,lotr.common.LOTRPlayerData> nativePlayers=(Map)field(lotr.common.LOTRLevelData.class,"playerDataMap").get(null);
        final lotr.common.LOTRPlayerData oldPlayer=nativePlayers.get(id);
        final lotr.common.fac.LOTRFactionRelations.Relation oldRelation=KOMEAllianceAuthority.getCurrentRelation("gondor","mordor");
        BattlePresentationClient() throws Exception {
            net.minecraft.client.Minecraft client=KOMEAccessFixture.allocate(net.minecraft.client.Minecraft.class);client.currentScreen=map;
            net.minecraft.client.entity.EntityClientPlayerMP player=KOMEAccessFixture.allocate(net.minecraft.client.entity.EntityClientPlayerMP.class);
            field(net.minecraft.entity.Entity.class,"entityUniqueID").set(player,id);player.worldObj=new KOMEAccessFixture().world;
            lotr.common.LOTRPlayerData data=new lotr.common.LOTRPlayerData(id);
            field(lotr.common.LOTRPlayerData.class,"pledgeFaction").set(data,lotr.common.fac.LOTRFaction.GONDOR);nativePlayers.put(id,data);
            client.thePlayer=player;slot.set(cpw.mods.fml.client.FMLClientHandler.instance(),client);
            lotr.common.fac.LOTRFactionRelations.overrideRelations(lotr.common.fac.LOTRFaction.GONDOR,lotr.common.fac.LOTRFaction.MORDOR,lotr.common.fac.LOTRFactionRelations.Relation.ENEMY);
        }
        void owner(String faction) throws Exception {owners.set(KOMEClientData.INSTANCE,Collections.singletonMap("T001",faction));}
        @Override public void close() throws Exception {
            slot.set(cpw.mods.fml.client.FMLClientHandler.instance(),oldClient);owners.set(KOMEClientData.INSTANCE,oldOwners);
            if(oldPlayer==null)nativePlayers.remove(id);else nativePlayers.put(id,oldPlayer);
            lotr.common.fac.LOTRFactionRelations.overrideRelations(lotr.common.fac.LOTRFaction.GONDOR,lotr.common.fac.LOTRFaction.MORDOR,oldRelation);
        }
    }
    private KOMEClientProxy connectSelector() throws Exception {
        KOMEClientProxy proxy=KOMEAccessFixture.allocate(KOMEClientProxy.class);
        selectorConnection=new Object();proxy.destinationOptions().start(selectorConnection);kome.common.KOMEAddon.proxy=proxy;return proxy;
    }
    private void openSelector() {click(400,300,1);clickRoot(0);assertTrue(click(400,240,0));}
    private KOMECompanyDestinationSelector selector() throws Exception {return (KOMECompanyDestinationSelector)field(KOMEConquestMapOverlay.class,"companySelector").get(null);}
    private KOMECompanyDestinationPreview preview() throws Exception {return (KOMECompanyDestinationPreview)field(KOMEConquestMapOverlay.class,"companyPreview").get(null);}
    private KOMECompanyDestinationPanel.Layout selectorLayout() throws Exception {return new KOMECompanyDestinationPanel.Layout(KOMEConquestMapOverlay.viewport(map),field(KOMEConquestMapOverlay.class,"selectorOnLeft").getBoolean(null),selector()!=null&&selector().canConfirmAndJoin());}
    private void clickControl(KOMECompanyDestinationPanel.Control control) throws Exception {
        KOMECompanyDestinationPanel.Rect rect=selectorLayout().controls.get(control);assertTrue(click(rect.x+2,rect.y+2,0));
    }
    private void publish(KOMEClientProxy proxy,KOMECompanyDestinationOptions response) {proxy.acceptCompanyDestinationOptions(response,selectorConnection);}
    private void deliverSelectorRow(KOMEClientProxy proxy) throws Exception {
        deliverSelectorRow(proxy,kome.common.command.KOMECompanyMovementService.Consequence.NONE);
    }
    private void deliverSelectorRow(KOMEClientProxy proxy,kome.common.command.KOMECompanyMovementService.Consequence consequence) throws Exception {
        KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(network.sent.size()-1);
        centers().put("T002",new int[] {2280,58});centers().put("T050",new int[] {2290,58});
        KOMECompanyDestinationOptions.Row row=new KOMECompanyDestinationOptions.Row("company","Company company","T002","T001",
            KOMECompanyDestinationOptions.Entitlement.MOUNTED,4,4,2,Arrays.asList("T002","T050","T001"),
            kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW,
            kome.common.command.KOMECompanyMovementService.ScheduleMode.DAILY,true,true,true,true,0,10000,20000,
            "","","",-1,consequence,"",-1,KOMECompanyDestinationSelectorTest.HASH,KOMECompanyDestinationSelectorTest.HASH);
        publish(proxy,KOMECompanyDestinationSelectorTest.page(request,
            KOMECompanyDestinationOptions.Result.OK,Collections.singletonList(row),false,0));
    }
    private void assertPreviewRequestsOnly() {for(IMessage message:network.sent)assertTrue(message instanceof KOMEPacketCompanyDestinationRequest);}
    public static class NonUnicodeLanguage extends net.minecraft.client.resources.LanguageManager {
        public NonUnicodeLanguage() { super(null,"en_US"); }
        @Override public boolean isCurrentLocaleUnicode() { return false; }
    }
    private boolean nativeWaypointClick(lotr.common.world.map.LOTRWaypoint waypoint,int x,int y) {
        if (KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,x,y,0)) return true;
        try { field(LOTRGuiMap.class,"selectedWaypoint").set(map,waypoint); } catch (Exception failure) { throw new AssertionError(failure); }
        KOMEConquestMapOverlay.onNativeWaypointClick(map);
        return KOMEConquestMapOverlay.onMapMouseClicked(map,x,y,0);
    }
    private boolean clickRoot(int index) {
        KOMEMapRadialMenu.Segment segment=menu.segments().get(index);
        return click(400+(int)Math.round(segment.labelX()),300+(int)Math.round(segment.labelY()),0);
    }
    private boolean click(int x,int y,int button) {return KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,x,y,button)||KOMEConquestMapOverlay.onMapMouseClicked(map,x,y,button);}
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
