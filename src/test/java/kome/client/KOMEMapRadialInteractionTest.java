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
    private KOMEClientProxy connectSelector() throws Exception {
        KOMEClientProxy proxy=KOMEAccessFixture.allocate(KOMEClientProxy.class);
        selectorConnection=new Object();proxy.destinationOptions().start(selectorConnection);kome.common.KOMEAddon.proxy=proxy;return proxy;
    }
    private void openSelector() {click(400,300,1);clickRoot(0);assertTrue(click(400,240,0));}
    private KOMECompanyDestinationSelector selector() throws Exception {return (KOMECompanyDestinationSelector)field(KOMEConquestMapOverlay.class,"companySelector").get(null);}
    private KOMECompanyDestinationPreview preview() throws Exception {return (KOMECompanyDestinationPreview)field(KOMEConquestMapOverlay.class,"companyPreview").get(null);}
    private KOMECompanyDestinationPanel.Layout selectorLayout() throws Exception {return new KOMECompanyDestinationPanel.Layout(KOMEConquestMapOverlay.viewport(map),field(KOMEConquestMapOverlay.class,"selectorOnLeft").getBoolean(null));}
    private void clickControl(KOMECompanyDestinationPanel.Control control) throws Exception {
        KOMECompanyDestinationPanel.Rect rect=selectorLayout().controls.get(control);assertTrue(click(rect.x+2,rect.y+2,0));
    }
    private void publish(KOMEClientProxy proxy,KOMECompanyDestinationOptions response) {proxy.acceptCompanyDestinationOptions(response,selectorConnection);}
    private void deliverSelectorRow(KOMEClientProxy proxy) throws Exception {
        KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(network.sent.size()-1);
        centers().put("T002",new int[] {2280,58});centers().put("T050",new int[] {2290,58});
        KOMECompanyDestinationOptions.Row row=new KOMECompanyDestinationOptions.Row("company","Company company","T002","T001",
            KOMECompanyDestinationOptions.Entitlement.MOUNTED,4,4,2,Arrays.asList("T002","T050","T001"),
            kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW,
            kome.common.command.KOMECompanyMovementService.ScheduleMode.DAILY,true,true,true,true,0,10000,20000,
            "","","",-1,kome.common.command.KOMECompanyMovementService.Consequence.NONE,"",-1,"legacy","confirmation");
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
