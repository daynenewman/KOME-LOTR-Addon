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
    @Rule public final org.junit.rules.TemporaryFolder preferences = new org.junit.rules.TemporaryFolder();
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private final Map<Field,Object> saved = new LinkedHashMap<Field,Object>();
    private final Map<Map,Map> savedMaps = new IdentityHashMap<Map,Map>();
    private LOTRGuiMap map;
    private KOMEMapRadialMenu menu;
    private RecordingNetwork network;
    private Object selectorConnection;
    @Before public void setup() throws Exception {
        save(field(KOMEClientConfig.class,"instance"));field(KOMEClientConfig.class,"instance").set(null,null);
        map=KOMEAccessFixture.allocate(LOTRGuiMap.class); map.width=800; map.height=600;
        for (String name : new String[] {"mapXMin","mapXMax","mapYMin","mapYMax","mapWidth","mapHeight","mapWidgets"}) save(field(LOTRGuiMap.class,name));
        for (String name : new String[] {"tileRaster","showConquestTiles","destinationCompanyId","routePreviewMove","radialMap","radialLeftCaptured","radialDragMap","nativeWaypointPicked","companySelector","originWorkflow","rally","unitsBrowser","clusterPanel","companyPreview","selectorOnLeft","rootJoin","directJoinScreen","directJoinFromRoot","directJoinViewCorrelation","directJoinSession"}) save(field(KOMEConquestMapOverlay.class,name));
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
        field(KOMEConquestMapOverlay.class,"nativeWaypointPicked").setBoolean(null,false);
        field(KOMEConquestMapOverlay.class,"destinationCompanyId").set(null,""); field(KOMEConquestMapOverlay.class,"routePreviewMove").set(null,null);
        new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));
        menu=(KOMEMapRadialMenu)field(KOMEConquestMapOverlay.class,"radialMenu").get(null);
        save(field(kome.common.KOMEAddon.class,"proxy"));
        save(field(KOMEPacketHandler.class,"network")); network=KOMEAccessFixture.allocate(RecordingNetwork.class);
        network.sent=new ArrayList<IMessage>();network.rootDiscovery=new ArrayList<IMessage>();network.autoRootDiscovery=true; KOMEPacketHandler.network=network;
        save(field(KOMEConquestMapOverlay.class,"buildSpritesMap"));
        field(KOMEConquestMapOverlay.class,"buildSpritesMap").set(null,map); // Native terrain-exit pass succeeded this frame.
    }
    @After public void cleanup() throws Exception {
        new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));
        for (Map.Entry<Map,Map> e : savedMaps.entrySet()) {e.getKey().clear();e.getKey().putAll(e.getValue());}
        for (Map.Entry<Field,Object> e : saved.entrySet()) e.getKey().set(null,e.getValue());
    }
    @Test public void territoryControlsConsumeClicksAndDragWithoutNetworkOrRadialSelection() throws Exception {
        KOMETerritoryStyle style=KOMEConquestMapOverlay.territoryStyle;
        KOMETerritoryStyle.Palette oldPalette=style.palette;
        KOMETerritoryStyle.DisplayMode oldMode=style.mode;
        KOMETerritoryStyle.Opacity oldOpacity=style.opacity;boolean oldEdges=style.showTileBorders;
        save(field(KOMEConquestMapOverlay.class,"showRiverBlockMarkers"));
        try {
            assertEquals(0,KOMEConquestMapOverlay.territoryControlAt(9,35));
            assertEquals(1,KOMEConquestMapOverlay.territoryControlAt(9,53));
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,9,35,0));
            assertEquals(oldPalette.next(),style.palette);assertEquals(oldMode,style.mode);
            assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
            assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,false));
            assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,9,53,0));
            assertEquals(oldMode.next(),style.mode);
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,9,53,1));
            assertEquals(oldMode.next(),style.mode);
            assertFalse(menu.isOpen());assertTrue(network.sent.isEmpty());
            assertEquals(-1,KOMEConquestMapOverlay.territoryControlAt(22,53));
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,9,71,0));
            assertEquals(oldOpacity.next(),style.opacity);assertEquals(oldEdges,style.showTileBorders);
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,9,79,0));
            assertEquals(!oldEdges,style.showTileBorders);
            boolean rivers=field(KOMEConquestMapOverlay.class,"showRiverBlockMarkers").getBoolean(null);
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,785,35,0));
            assertEquals(!rivers,field(KOMEConquestMapOverlay.class,"showRiverBlockMarkers").getBoolean(null));
            assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
            assertTrue(network.sent.isEmpty());assertEquals(oldOpacity.next(),style.opacity);
        } finally {style.palette=oldPalette;style.mode=oldMode;style.opacity=oldOpacity;style.showTileBorders=oldEdges;}
    }
    @Test public void companyControlCyclesThreeStatesWithoutSubmittingMovement() throws Exception {
        KOMEMapPreferences prior=KOMEConquestMapOverlay.mapPreferences();
        try {
            KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences());
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,785,63,0));
            assertEquals(KOMEMapPreferences.CompanyDisplay.SHIELDS_AND_DESCRIPTORS,
                KOMEConquestMapOverlay.mapPreferences().companyDisplay);
            assertTrue(KOMEConquestMapOverlay.companyMovementVisualsVisible());
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,785,63,0));
            assertEquals(KOMEMapPreferences.CompanyDisplay.HIDE_COMPANIES,
                KOMEConquestMapOverlay.mapPreferences().companyDisplay);
            assertFalse(KOMEConquestMapOverlay.companyMovementVisualsVisible());
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,785,63,0));
            assertEquals(KOMEMapPreferences.CompanyDisplay.SHIELDS_ONLY,
                KOMEConquestMapOverlay.mapPreferences().companyDisplay);
            assertTrue(network.sent.isEmpty());
        } finally {KOMEConquestMapOverlay.applyMapPreferences(prior);}
    }

    @Test public void coincidentUnloadedCompaniesUseActualTopmostShieldForTooltipInEitherInputOrder()throws Exception {
        preserve(KOMEClientData.INSTANCE.armyCompanies);KOMEClientData.INSTANCE.armyCompanies.clear();
        save(field(KOMEConquestMapOverlay.class,"showTroopMarkers"));field(KOMEConquestMapOverlay.class,"showTroopMarkers").setBoolean(null,true);
        // Prepared native sprites, as after a successful resource reload; no GL upload in this input fixture.
        save(field(KOMEMapMarkerArt.class,"validated"));save(field(KOMEMapMarkerArt.class,"available"));
        Map<KOMEMapMarkerArt.Sprite,KOMEMapMarkerArt.Sprite> prepared=new IdentityHashMap<>();
        for(String faction:new String[]{"rohan","gondor"}){KOMEMapMarkerArt.Sprite sprite=KOMEMapMarkerArt.shield(faction);prepared.put(sprite,sprite);}
        field(KOMEMapMarkerArt.class,"available").set(null,prepared);field(KOMEMapMarkerArt.class,"validated").setBoolean(null,true);
        List<KOMEUnitMapMarker> old=new ArrayList<>(KOMEClientData.INSTANCE.unitMapMarkers);
        try {
            KOMEClientData.INSTANCE.tileWaypointLinksByTileId.remove("T001");
            KOMETileRasterSnapshot raster=(KOMETileRasterSnapshot)field(KOMEConquestMapOverlay.class,"tileRaster").get(null);
            pan((float)(2292D*LOTRGenLayerWorld.imageWidth/raster.width),(float)(58D*LOTRGenLayerWorld.imageHeight/raster.height),1);
            KOMEUnitMapMarker a=companyMarker("company-A","Riders","rohan",30),z=companyMarker("company-Z","Guard","rohan",70);
            for(boolean reverse:new boolean[]{false,true}) {
                KOMEClientData.INSTANCE.unitMapMarkers.clear();KOMEClientData.INSTANCE.unitMapMarkers.addAll(reverse?Arrays.asList(z,a):Arrays.asList(a,z));
                List<KOMEUnitMapMarker> drawn=new ArrayList<>();List<KOMEMapMarkerArt.Sprite> art=new ArrayList<>();
                List<String> tooltip=KOMEConquestMapOverlay.renderCompanyMarkers(map,400,300,(m,s,b,p)->{
                    drawn.add(m);art.add(s);assertEquals(401,b.x+b.width/2);assertEquals(301,b.y+b.height/2);assertEquals(9,b.width);assertEquals(9,b.height);
                });
                assertEquals("Overlapping shields should submit one cluster",1,drawn.size());
                assertEquals("2 companies",tooltip.get(0));
                assertFalse(tooltip.toString().contains("Campaign population"));
                assertEquals(Arrays.asList(a),drawn);assertEquals(Arrays.asList(KOMEMapMarkerArt.shield("rohan")),art);
                List<KOMEUnitMapMarker> singletonDraws=new ArrayList<>();
                List<KOMECompanyShieldClusters.Group> clusterDraws=new ArrayList<>();
                KOMEConquestMapOverlay.renderCompanyMarkers(map,400,300,
                    (m,s,b,p)->singletonDraws.add(m),(group,p)->clusterDraws.add(group));
                assertTrue("Clustered member labels must not use the singleton draw path",singletonDraws.isEmpty());
                assertEquals(1,clusterDraws.size());assertEquals(2,clusterDraws.get(0).members.size());
                assertSame(KOMEMapMarkerArt.shield("rohan"),clusterDraws.get(0).art);
                List<String> clicked=new ArrayList<>();
                kome.common.KOMEAddon.proxy=new kome.common.KOMECommonProxy(){
                    @Override public void displayCompanyClusterRoster(List<String> ids,Object sourceMap){assertSame(map,sourceMap);clicked.addAll(ids);}
                };
                assertTrue(KOMEConquestMapOverlay.onMapMouseClicked(map,400,300,0));
                assertEquals(Arrays.asList("company-A","company-Z"),clicked);
                assertTrue("Cluster click does not issue a gameplay packet",network.sent.isEmpty());
                KOMEConquestMapOverlay.onNativeWaypointClick(map);
                assertFalse("A native waypoint pick keeps precedence over the cluster",
                    KOMEConquestMapOverlay.onMapMouseClicked(map,400,300,0));
                assertEquals(2,clicked.size());
                field(KOMEConquestMapOverlay.class,"nativeWaypointPicked").setBoolean(null,false);
            }
            KOMEArmyCompany foreign=KOMEClientData.INSTANCE.armyCompanies.get("company-Z");
            foreign.faction="gondor";foreign.nativeFaction="gondor";
            List<KOMEUnitMapMarker> mixedDraws=new ArrayList<>();
            List<KOMEMapMarkerArt.Sprite> mixedArt=new ArrayList<>();
            List<KOMEMapIconPresentation.Bounds> mixedBounds=new ArrayList<>();
            List<KOMECompanyShieldClusters.Group> mixedClusters=new ArrayList<>();
            KOMEConquestMapOverlay.renderCompanyMarkers(map,400,300,
                (m,s,b,p)->{mixedDraws.add(m);mixedArt.add(s);mixedBounds.add(b);},
                (group,p)->mixedClusters.add(group));
            assertEquals(Arrays.asList(a,z),mixedDraws);
            assertEquals(Arrays.asList(KOMEMapMarkerArt.shield("rohan"),KOMEMapMarkerArt.shield("gondor")),mixedArt);
            assertTrue(mixedClusters.isEmpty());
            assertFalse("Different faction shields must be separately selectable",
                mixedBounds.get(0).contains(mixedBounds.get(1).x+mixedBounds.get(1).width/2,
                    mixedBounds.get(1).y+mixedBounds.get(1).height/2));
            foreign.faction="rohan";foreign.nativeFaction="rohan";
            float x=field(LOTRGuiMap.class,"posX").getFloat(map),y=field(LOTRGuiMap.class,"posY").getFloat(map);
            pan(x+389,y+261,1);
            java.lang.reflect.Method owns=KOMEConquestMapOverlay.class.getDeclaredMethod("mapUiOwnsPointer",LOTRGuiMap.class,int.class,int.class);owns.setAccessible(true);assertEquals(true,owns.invoke(null,map,11,39));
            List<KOMEUnitMapMarker> covered=new ArrayList<>();assertNull(KOMEConquestMapOverlay.renderCompanyMarkers(map,11,39,(m,s,b,p)->covered.add(m)));assertEquals(1,covered.size());pan(x,y,1);
            z.dimensionId=-1;List<KOMEUnitMapMarker> drawn=new ArrayList<>();
            assertEquals("Riders",KOMEConquestMapOverlay.renderCompanyMarkers(map,400,300,(m,s,b,p)->drawn.add(m)).get(0));assertEquals(Arrays.asList(a),drawn);
            z.dimensionId=a.dimensionId;z.unitCount=1;z.x=99999999;z.z=99999999;
            assertEquals("Riders",KOMEConquestMapOverlay.renderCompanyMarkers(map,400,300,(m,s,b,p)->{}).get(0));
            field(KOMEConquestMapOverlay.class,"showTroopMarkers").setBoolean(null,false);
            assertNull(KOMEConquestMapOverlay.renderCompanyMarkers(map,400,300,(m,s,b,p)->fail("Hidden layer must not submit a shield")));
        }finally{KOMEClientData.INSTANCE.unitMapMarkers.clear();KOMEClientData.INSTANCE.unitMapMarkers.addAll(old);}
    }

    private KOMEUnitMapMarker companyMarker(String id,String name,String faction,int population) {
        KOMEArmyCompany company=new KOMEArmyCompany();company.id=id;company.name=name;company.faction=faction;company.nativeFaction=faction;company.currentTile="T001";
        KOMEClientData.INSTANCE.armyCompanies.put(id,company);
        KOMEUnitMapMarker marker=new KOMEUnitMapMarker();marker.entityId=id;marker.companyName=name;marker.currentTile="T001";
        marker.population=population;marker.dimensionId=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;return marker;
    }

    @Test public void physicalAndSpottedMarkersUseDistinctExistingProjectionSources()throws Exception{
        preserve(KOMEClientData.INSTANCE.armyCompanies);
        KOMEUnitMapMarker own=companyMarker("own-physical","Own","rohan",45);
        own.unitCount=1;own.x=0;own.z=0;
        KOMEUnitMapMarker foreign=companyMarker("~recon:opaque","","isengard",0);
        KOMEClientData.INSTANCE.armyCompanies.get(foreign.entityId).status=KOMECompanyMapProjection.SPOTTED_STATUS;
        foreign.unitCount=0;
        java.lang.reflect.Method position=KOMEConquestMapOverlay.class.getDeclaredMethod(
            "companyMarkerScreenPosition",LOTRGuiMap.class,KOMEUnitMapMarker.class);
        position.setAccessible(true);
        java.lang.reflect.Method tileAnchor=KOMEConquestMapOverlay.class.getDeclaredMethod(
            "tileAnchorScreenPosition",LOTRGuiMap.class,String.class);
        tileAnchor.setAccessible(true);
        java.lang.reflect.Method worldX=KOMEConquestMapOverlay.class.getDeclaredMethod(
            "worldScreenX",LOTRGuiMap.class,double.class);
        worldX.setAccessible(true);
        java.lang.reflect.Method worldY=KOMEConquestMapOverlay.class.getDeclaredMethod(
            "worldScreenY",LOTRGuiMap.class,double.class);
        worldY.setAccessible(true);
        int[] actualOwn=(int[])position.invoke(null,map,own);
        int[] actualForeign=(int[])position.invoke(null,map,foreign);
        assertArrayEquals(new int[]{(Integer)worldX.invoke(null,map,own.x),
            (Integer)worldY.invoke(null,map,own.z)},actualOwn);
        assertArrayEquals((int[])tileAnchor.invoke(null,map,"T001"),actualForeign);
        assertFalse(Arrays.equals(actualOwn,actualForeign));
    }
    @Test public void tenCompanyBadgeCannotHideVisibleShieldAtLeftViewportEdge() throws Exception {
        preserve(KOMEClientData.INSTANCE.armyCompanies);
        save(field(KOMEConquestMapOverlay.class,"showTroopMarkers"));
        save(field(KOMEMapMarkerArt.class,"validated"));save(field(KOMEMapMarkerArt.class,"available"));
        List<KOMEUnitMapMarker> previous=new ArrayList<KOMEUnitMapMarker>(KOMEClientData.INSTANCE.unitMapMarkers);
        try {
            field(KOMEConquestMapOverlay.class,"showTroopMarkers").setBoolean(null,true);
            KOMEMapMarkerArt.Sprite shield=KOMEMapMarkerArt.shield("gondor");
            Map<KOMEMapMarkerArt.Sprite,KOMEMapMarkerArt.Sprite> art=new IdentityHashMap<KOMEMapMarkerArt.Sprite,KOMEMapMarkerArt.Sprite>();
            art.put(shield,shield);
            field(KOMEMapMarkerArt.class,"available").set(null,art);
            field(KOMEMapMarkerArt.class,"validated").setBoolean(null,true);
            KOMEClientData.INSTANCE.armyCompanies.clear();KOMEClientData.INSTANCE.unitMapMarkers.clear();
            pan(LOTRGenLayerWorld.originX+396,LOTRGenLayerWorld.originZ,1);
            for(int i=0;i<10;i++){
                KOMEUnitMapMarker marker=companyMarker("edge-"+i,"Company "+i,"gondor",10);
                marker.unitCount=1;marker.x=marker.z=0;marker.soldierCount=3;
                KOMEClientData.INSTANCE.unitMapMarkers.add(marker);
            }
            List<KOMECompanyShieldClusters.Group> drawn=new ArrayList<KOMECompanyShieldClusters.Group>();
            KOMEConquestMapOverlay.renderCompanyMarkers(map,4,300,(m,s,b,p)->fail("Expected one cluster"),
                (group,p)->drawn.add(group));
            assertEquals("Badge clipping must not remove the primary shield",1,drawn.size());
            assertEquals(10,drawn.get(0).members.size());
            assertTrue(drawn.get(0).iconBounds.visible(KOMEConquestMapOverlay.viewport(map)));
            assertTrue(drawn.get(0).contains(4,300));
            assertFalse(drawn.get(0).contains(-1,300));
            float[][] edges={{LOTRGenLayerWorld.originX-394,LOTRGenLayerWorld.originZ,794,300},
                {LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ+296,400,4},
                {LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ-294,400,594}};
            for(float[] edge:edges){
                pan(edge[0],edge[1],1);drawn.clear();
                KOMEConquestMapOverlay.renderCompanyMarkers(map,(int)edge[2],(int)edge[3],
                    (m,s,b,p)->fail("Expected one cluster"),(group,p)->drawn.add(group));
                assertEquals("Visible primary shield survives each viewport edge",1,drawn.size());
                assertTrue(drawn.get(0).iconBounds.visible(KOMEConquestMapOverlay.viewport(map)));
                assertTrue(drawn.get(0).contains((int)edge[2],(int)edge[3]));
            }
            pan(LOTRGenLayerWorld.originX+500,LOTRGenLayerWorld.originZ,1);
            drawn.clear();
            KOMEConquestMapOverlay.renderCompanyMarkers(map,-100,300,
                (m,s,b,p)->fail("Offscreen singleton"),(group,p)->drawn.add(group));
            assertTrue("Entirely offscreen shield remains hidden",drawn.isEmpty());
        } finally {
            KOMEClientData.INSTANCE.unitMapMarkers.clear();KOMEClientData.INSTANCE.unitMapMarkers.addAll(previous);
        }
    }
    @Test public void clusterOwnsOverlappingBuildPressInEitherInputOrder() throws Exception {
        preserve(KOMEClientData.INSTANCE.armyCompanies);preserve(KOMEClientData.INSTANCE.builds);
        List<KOMEUnitMapMarker> old=new ArrayList<>(KOMEClientData.INSTANCE.unitMapMarkers);
        save(field(KOMEConquestMapOverlay.class,"showTroopMarkers"));
        save(field(KOMEConquestMapOverlay.class,"showBuildMarkers"));
        save(field(KOMEMapMarkerArt.class,"validated"));save(field(KOMEMapMarkerArt.class,"available"));
        Map<KOMEMapMarkerArt.Sprite,KOMEMapMarkerArt.Sprite> prepared=new IdentityHashMap<>();
        for(KOMEMapMarkerArt.Sprite art:new KOMEMapMarkerArt.Sprite[]{
                KOMEMapMarkerArt.shield("gondor"),KOMEMapMarkerArt.banner("gondor")})prepared.put(art,art);
        field(KOMEMapMarkerArt.class,"available").set(null,prepared);
        field(KOMEMapMarkerArt.class,"validated").setBoolean(null,true);
        field(KOMEConquestMapOverlay.class,"showTroopMarkers").setBoolean(null,true);
        field(KOMEConquestMapOverlay.class,"showBuildMarkers").setBoolean(null,true);
        try {
            KOMEClientData.INSTANCE.armyCompanies.clear();KOMEClientData.INSTANCE.builds.clear();
            KOMEClientData.INSTANCE.unitMapMarkers.clear();
            pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,1);
            KOMEUnitMapMarker a=companyMarker("A","First","gondor",30);
            KOMEUnitMapMarker b=companyMarker("B","Second","gondor",40);
            a.unitCount=b.unitCount=1;a.x=b.x=0;a.z=b.z=0;
            KOMEClientData.INSTANCE.unitMapMarkers.add(a);KOMEClientData.INSTANCE.unitMapMarkers.add(b);
            KOMEPlayerBuild build=new KOMEPlayerBuild();build.id="build";build.tileId="T001";
            build.type=KOMEBuildType.NORMAL;build.populationFaction="gondor";
            build.dimension=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;
            KOMEClientData.INSTANCE.builds.put(build.id,build);
            List<String> opened=new ArrayList<>();
            kome.common.KOMEAddon.proxy=new kome.common.KOMECommonProxy(){
                @Override public void displayCompanyClusterRoster(List<String> ids,Object sourceMap){assertSame(map,sourceMap);opened.addAll(ids);}
            };
            for(boolean pollFirst:new boolean[]{true,false}) {
                field(KOMEConquestMapOverlay.class,"nativeWaypointPicked").setBoolean(null,false);
                if(pollFirst)assertFalse("Polling must not also request the Build",
                    KOMEConquestMapOverlay.requestBuildDetailsAt(map,400,300));
                assertTrue(KOMEConquestMapOverlay.onMapMouseClicked(map,400,300,0));
                if(!pollFirst)assertFalse("Later polling must not request the Build",
                    KOMEConquestMapOverlay.requestBuildDetailsAt(map,400,300));
                assertTrue(network.sent.isEmpty());
            }
            assertEquals(Arrays.asList("A","B","A","B"),opened);
            Field ready=field(KOMEClientData.class,"completeConquestSnapshot");
            boolean wasReady=ready.getBoolean(KOMEClientData.INSTANCE);
            ready.setBoolean(KOMEClientData.INSTANCE,true);
            try {
                List<String> ids=Arrays.asList("A","B");long epoch=KOMEClientData.INSTANCE.clientSessionEpoch;
                assertNotNull(KOMEGuiCompanyClusterRoster.currentRows(ids,epoch,map));
                KOMEConquestMapOverlay.openCompanyClusterPanel(map,ids);
                KOMEGuiCompanyClusterRoster panel=(KOMEGuiCompanyClusterRoster)field(KOMEConquestMapOverlay.class,"clusterPanel").get(null);
                assertNotNull("Cluster selection stays on the map",panel);
                assertSame(map,panel.sourceMap());
                assertFalse("Uncovered map remains navigable",KOMEConquestMapOverlay.clusterPanelOwnsPointer(map,400,300));
                KOMEGuiCompanyClusterRoster.PanelLayout layout=new KOMEGuiCompanyClusterRoster.PanelLayout(
                    KOMEConquestMapOverlay.viewport(map),96);
                assertTrue("Panel owns only its visible rectangle",
                    KOMEConquestMapOverlay.clusterPanelOwnsPointer(map,layout.panel.x+10,layout.rowTop+5));
                assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,layout.panel.x+10,layout.rowTop+5,0));
                assertEquals("A",panel.selectedId());
                assertTrue("Panel wheel never becomes native map zoom",
                    KOMEConquestMapOverlay.onMapSelectorWheel(map,layout.panel.x+10,layout.rowTop+5,-120));
                assertFalse("Wheel outside panel remains native zoom",
                    KOMEConquestMapOverlay.onMapSelectorWheel(map,400,300,-120));
                assertTrue(network.sent.isEmpty());
                assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,
                    layout.close.x+1,layout.close.y+1,0));
                assertNull("Dismissed panel cannot refresh or reopen itself",
                    field(KOMEConquestMapOverlay.class,"clusterPanel").get(null));
                KOMEConquestMapOverlay.validateClusterPanelLifecycle();
                assertNull(field(KOMEConquestMapOverlay.class,"clusterPanel").get(null));
                KOMEConquestMapOverlay.openCompanyClusterPanel(map,ids);
                panel=(KOMEGuiCompanyClusterRoster)field(KOMEConquestMapOverlay.class,"clusterPanel").get(null);
                int revision=KOMEClientData.INSTANCE.conquestRevision;
                KOMEClientData.INSTANCE.conquestRevision++;
                assertNotNull("Unrelated territory updates preserve the cluster roster",
                    KOMEGuiCompanyClusterRoster.currentRows(ids,epoch,map));
                KOMEConquestMapOverlay.validateClusterPanelLifecycle();
                assertSame(panel,field(KOMEConquestMapOverlay.class,"clusterPanel").get(null));
                KOMEClientData.INSTANCE.conquestRevision=revision;
                b.x=40D*LOTRGenLayerWorld.scale;pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,.125F);
                assertNotNull("Projected far-zoom overlap remains current",
                    KOMEGuiCompanyClusterRoster.currentRows(ids,epoch,map));
                pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,1);
                assertNull("Zoom separation invalidates the clicked group",
                    KOMEGuiCompanyClusterRoster.currentRows(ids,epoch,map));
                KOMEConquestMapOverlay.validateClusterPanelLifecycle();
                assertNull(field(KOMEConquestMapOverlay.class,"clusterPanel").get(null));
                b.x=0;
                KOMEClientData.INSTANCE.unitMapMarkers.clear();
                KOMEClientData.INSTANCE.unitMapMarkers.add(a);KOMEClientData.INSTANCE.unitMapMarkers.add(b);
                assertNotNull("Equivalent marker replacement preserves the roster",
                    KOMEGuiCompanyClusterRoster.currentRows(ids,epoch,map));
                assertTrue(new KOMEGuiCompanyClusterRoster(ids,map).stillCurrent());
                KOMEUnitMapMarker c=companyMarker("C","Third","gondor",20);
                c.unitCount=1;c.x=c.z=0;KOMEClientData.INSTANCE.unitMapMarkers.add(c);
                assertNull("A newly overlapping company changes exact roster membership",
                    KOMEGuiCompanyClusterRoster.currentRows(ids,epoch,map));
                KOMEClientData.INSTANCE.unitMapMarkers.remove(c);
                KOMEClientData.INSTANCE.armyCompanies.remove("C");
                b.x=10000;
                assertNull("Company departure invalidates the roster",
                    KOMEGuiCompanyClusterRoster.currentRows(ids,epoch,map));
                assertFalse("Open UI must close after marker departure",
                    new KOMEGuiCompanyClusterRoster(ids,map).stillCurrent());
                b.x=0;
                KOMEGuiCompanyClusterRoster oldSession=new KOMEGuiCompanyClusterRoster(ids,map);
                KOMEConquestMapOverlay.openCompanyClusterPanel(map,ids);
                assertNotNull(field(KOMEConquestMapOverlay.class,"clusterPanel").get(null));
                KOMEClientData.INSTANCE.clientSessionEpoch++;
                try {
                    assertFalse("World/session replacement fences the open roster",oldSession.stillCurrent());
                    KOMEConquestMapOverlay.validateClusterPanelLifecycle();
                    assertNull(field(KOMEConquestMapOverlay.class,"clusterPanel").get(null));
                }
                finally {KOMEClientData.INSTANCE.clientSessionEpoch=epoch;}
                KOMEConquestMapOverlay.openCompanyClusterPanel(map,ids);
                KOMEArmyCompany companyB=KOMEClientData.INSTANCE.armyCompanies.remove("B");
                assertNull("Revocation hides rows before another draw",
                    KOMEGuiCompanyClusterRoster.currentRows(ids,epoch,map));
                KOMEConquestMapOverlay.validateClusterPanelLifecycle();
                assertNull("Revocation closes the map-side panel",
                    field(KOMEConquestMapOverlay.class,"clusterPanel").get(null));
                KOMEClientData.INSTANCE.armyCompanies.put("B",companyB);
            } finally {ready.setBoolean(KOMEClientData.INSTANCE,wasReady);}
            KOMEConquestMapOverlay.onNativeWaypointClick(map);
            assertFalse(KOMEConquestMapOverlay.onMapMouseClicked(map,400,300,0));
            assertFalse("Native waypoint owns the press",
                KOMEConquestMapOverlay.requestBuildDetailsAt(map,400,300));
            assertEquals(4,opened.size());
            KOMEClientData.INSTANCE.armyCompanies.remove("B");
            assertFalse("Revoked cluster cannot reopen",KOMEConquestMapOverlay.onMapMouseClicked(map,400,300,0));
            KOMEClientData.INSTANCE.unitMapMarkers.clear();
            field(KOMEConquestMapOverlay.class,"nativeWaypointPicked").setBoolean(null,false);
            assertSame("Build remains clickable without a cluster",build,pickBuild(map,400,300));
            assertTrue("Ordinary Build selection still sends a detail request",
                KOMEConquestMapOverlay.requestBuildDetailsAt(map,400,300));
            assertEquals(1,network.sent.size());
            assertTrue(network.sent.get(0) instanceof KOMEPacketConquestOpenCapture);
        } finally {KOMEClientData.INSTANCE.unitMapMarkers.clear();KOMEClientData.INSTANCE.unitMapMarkers.addAll(old);}
    }
    @Test public void spottedShieldDrawsAtTileButHasNoDetailTooltipOrCostDeduction()throws Exception {
        preserve(KOMEClientData.INSTANCE.armyCompanies);KOMEClientData.INSTANCE.armyCompanies.clear();
        save(field(KOMEConquestMapOverlay.class,"showTroopMarkers"));field(KOMEConquestMapOverlay.class,"showTroopMarkers").setBoolean(null,true);
        save(field(KOMEMapMarkerArt.class,"validated"));save(field(KOMEMapMarkerArt.class,"available"));
        Map<KOMEMapMarkerArt.Sprite,KOMEMapMarkerArt.Sprite> prepared=new IdentityHashMap<>();
        prepared.put(KOMEMapMarkerArt.shield("mordor"),KOMEMapMarkerArt.shield("mordor"));
        field(KOMEMapMarkerArt.class,"available").set(null,prepared);field(KOMEMapMarkerArt.class,"validated").setBoolean(null,true);
        List<KOMEUnitMapMarker> old=new ArrayList<>(KOMEClientData.INSTANCE.unitMapMarkers);
        try {
            KOMEArmyCompany spotted=new KOMEArmyCompany();spotted.id="~recon:T001:mordor:1";
            spotted.faction=spotted.nativeFaction="mordor";spotted.currentTile="T001";
            spotted.status=kome.common.network.KOMECompanyMapProjection.SPOTTED_STATUS;
            KOMEClientData.INSTANCE.armyCompanies.put(spotted.id,spotted);
            KOMEUnitMapMarker marker=new KOMEUnitMapMarker();marker.entityId=spotted.id;
            marker.currentTile="T001";marker.soldierCount=3;
            marker.dimensionId=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;
            KOMEClientData.INSTANCE.unitMapMarkers.clear();KOMEClientData.INSTANCE.unitMapMarkers.add(marker);
            KOMEClientData.INSTANCE.tileWaypointLinksByTileId.remove("T001");
            KOMETileRasterSnapshot raster=(KOMETileRasterSnapshot)field(KOMEConquestMapOverlay.class,"tileRaster").get(null);
            pan((float)(2292D*LOTRGenLayerWorld.imageWidth/raster.width),
                (float)(58D*LOTRGenLayerWorld.imageHeight/raster.height),1);
            List<KOMEUnitMapMarker> drawn=new ArrayList<>();
            assertNull(KOMEConquestMapOverlay.renderCompanyMarkers(map,400,300,(m,art,b,p)->{
                drawn.add(m);assertSame(KOMEMapMarkerArt.shield("mordor"),art);
            }));
            assertEquals(Arrays.asList(marker),drawn);
            java.lang.reflect.Method tooltip=KOMEConquestMapOverlay.class.getDeclaredMethod("liveUnitTooltip",KOMEUnitMapMarker.class);
            tooltip.setAccessible(true);assertNull(tooltip.invoke(null,marker));
            java.lang.reflect.Method counts=KOMEConquestMapOverlay.class.getDeclaredMethod("liveCompanyStationedPopulationByTile");
            counts.setAccessible(true);
            assertEquals("Spotted marker reserves its visible tile but contributes no private cost",
                Integer.valueOf(0),((Map)counts.invoke(null)).get("T001"));
        }finally{KOMEClientData.INSTANCE.unitMapMarkers.clear();KOMEClientData.INSTANCE.unitMapMarkers.addAll(old);}
    }
    @Test public void nativeZoomStopsSubmitProgressiveCompanyAndBuildBounds()throws Exception {
        preserve(KOMEClientData.INSTANCE.armyCompanies);preserve(KOMEClientData.INSTANCE.builds);
        List<KOMEUnitMapMarker> old=new ArrayList<>(KOMEClientData.INSTANCE.unitMapMarkers);
        save(field(KOMEConquestMapOverlay.class,"showTroopMarkers"));
        save(field(KOMEMapMarkerArt.class,"validated"));save(field(KOMEMapMarkerArt.class,"available"));
        Map<KOMEMapMarkerArt.Sprite,KOMEMapMarkerArt.Sprite> prepared=new IdentityHashMap<>();
        for(KOMEMapMarkerArt.Sprite art:new KOMEMapMarkerArt.Sprite[]{KOMEMapMarkerArt.shield("gondor"),KOMEMapMarkerArt.banner("gondor"),KOMEMapMarkerArt.CASTLE})prepared.put(art,art);
        field(KOMEMapMarkerArt.class,"available").set(null,prepared);field(KOMEMapMarkerArt.class,"validated").setBoolean(null,true);
        field(KOMEConquestMapOverlay.class,"showTroopMarkers").setBoolean(null,true);
        try {
            KOMEClientData.INSTANCE.armyCompanies.clear();KOMEClientData.INSTANCE.unitMapMarkers.clear();
            KOMEClientData.INSTANCE.tileWaypointLinksByTileId.remove("T001");
            KOMEUnitMapMarker company=companyMarker("same-company","Minas Tirith Company","gondor",25);
            KOMEClientData.INSTANCE.unitMapMarkers.add(company);
            KOMEPlayerBuild normal=new KOMEPlayerBuild(),defensive=new KOMEPlayerBuild();
            normal.id="normal";normal.displayName="Normal";normal.type=KOMEBuildType.NORMAL;normal.populationFaction="gondor";
            defensive.id="defensive";defensive.type=KOMEBuildType.DEFENSIVE;
            normal.dimension=defensive.dimension=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;
            KOMEClientData.INSTANCE.builds.clear();KOMEClientData.INSTANCE.builds.put(normal.id,normal);KOMEClientData.INSTANCE.builds.put(defensive.id,defensive);
            KOMETileRasterSnapshot raster=(KOMETileRasterSnapshot)field(KOMEConquestMapOverlay.class,"tileRaster").get(null);
            float tileX=(float)(2292D*LOTRGenLayerWorld.imageWidth/raster.width);
            float tileY=(float)(58D*LOTRGenLayerWorld.imageHeight/raster.height);
            float[] nativeZoom={.5F,1,2,4,8,16};
            int[] shield={9,9,12,14,17,18};
            int[][] banner={{6,12},{6,12},{7,14},{8,16},{9,19},{10,20}};
            int[] castle={6,6,7,8,9,10};
            for(int i=0;i<nativeZoom.length;i++) {
                pan(tileX,tileY,nativeZoom[i]);
                List<KOMEMapIconPresentation.Bounds> companyDrawn=new ArrayList<>();
                KOMEConquestMapOverlay.renderCompanyMarkers(map,-1,-1,(m,art,b,p)->{
                    assertSame(company,m);assertSame(KOMEMapMarkerArt.shield("gondor"),art);companyDrawn.add(b);
                });
                assertEquals("company submission at native zoom "+nativeZoom[i],1,companyDrawn.size());
                KOMEMapIconPresentation.Bounds shieldBounds=companyDrawn.get(0);
                assertEquals(shield[i],shieldBounds.width);assertEquals(shield[i],shieldBounds.height);
                assertTrue(shieldBounds.contains(shieldBounds.x,shieldBounds.y));
                assertFalse(shieldBounds.contains(shieldBounds.x+shieldBounds.width,shieldBounds.y));
                assertEquals("Minas Tirith Company",KOMEConquestMapOverlay.renderCompanyMarkers(map,
                    shieldBounds.x+shieldBounds.width/2,shieldBounds.y+shieldBounds.height/2,(m,art,b,p)->{}).get(0));
                pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,nativeZoom[i]);
                Map<String,KOMEMapIconPresentation.Bounds> buildDrawn=new HashMap<>();
                KOMEConquestMapOverlay.renderBuildMarkers(map,-1,-1,(b,bounds,p)->buildDrawn.put(b.id,bounds));
                assertEquals("Build submission at native zoom "+nativeZoom[i],2,buildDrawn.size());
                assertEquals(banner[i][0],buildDrawn.get("normal").width);assertEquals(banner[i][1],buildDrawn.get("normal").height);
                assertEquals(castle[i],buildDrawn.get("defensive").width);assertEquals(castle[i],buildDrawn.get("defensive").height);
                KOMEMapIconPresentation.Bounds normalBounds=buildDrawn.get("normal");
                int hoverX=normalBounds.x+normalBounds.width/2,hoverY=normalBounds.y+normalBounds.height/2;
                assertEquals("normal",((KOMEPlayerBuild)pickBuild(map,hoverX,hoverY)).id);
                assertEquals("Normal (normal)",KOMEConquestMapOverlay.renderBuildMarkers(map,hoverX,hoverY,(b,box,p)->{}).get(0));
            }
        }finally{KOMEClientData.INSTANCE.unitMapMarkers.clear();KOMEClientData.INSTANCE.unitMapMarkers.addAll(old);}
    }
    private static Object pickBuild(LOTRGuiMap map,int x,int y)throws Exception {
        java.lang.reflect.Method method=KOMEConquestMapOverlay.class.getDeclaredMethod("buildMarkerAt",LOTRGuiMap.class,int.class,int.class);
        method.setAccessible(true);return method.invoke(null,map,x,y);
    }
    @Test public void strategicCompanyUsesExistingTileAnchorWithoutClaimingPhysicalCombatStateOrDoubleCounting()throws Exception {
        preserve(KOMEClientData.INSTANCE.armyCompanies);KOMEClientData.INSTANCE.armyCompanies.clear();
        List<KOMEUnitMapMarker> old=new ArrayList<>(KOMEClientData.INSTANCE.unitMapMarkers);
        try {
            KOMEArmyCompany company=new KOMEArmyCompany();company.id="C1";company.currentTile="T001";company.nativeFaction="rohan";company.totalPopulation=50;
            KOMEClientData.INSTANCE.armyCompanies.put(company.id,company);
            KOMEUnitMapMarker marker=new KOMEUnitMapMarker();marker.entityId="C1";marker.currentTile="T001";
            marker.dimensionId=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;marker.population=50;marker.x=999999;marker.z=-999999;
            java.lang.reflect.Method anchor=KOMEConquestMapOverlay.class.getDeclaredMethod("companyMarkerScreenPosition",LOTRGuiMap.class,KOMEUnitMapMarker.class);anchor.setAccessible(true);
            KOMEClientData.INSTANCE.tileWaypointLinksByTileId.remove("T001");
            KOMETileRasterSnapshot raster=(KOMETileRasterSnapshot)field(KOMEConquestMapOverlay.class,"tileRaster").get(null);
            pan((float)(2292D*LOTRGenLayerWorld.imageWidth/raster.width),(float)(58D*LOTRGenLayerWorld.imageHeight/raster.height),1);
            assertArrayEquals(new int[]{400,300},(int[])anchor.invoke(null,map,marker));
            java.lang.reflect.Method tooltip=KOMEConquestMapOverlay.class.getDeclaredMethod("liveUnitTooltip",KOMEUnitMapMarker.class);tooltip.setAccessible(true);
            List<String> lines=(List<String>)tooltip.invoke(null,marker);
            assertTrue(lines.contains("Loaded units: 0"));assertTrue(lines.toString().contains("Strategic tile presence"));
            assertFalse(lines.toString().contains("Combat-capable"));assertFalse(lines.toString().contains("Company center"));
            KOMEClientData.INSTANCE.unitMapMarkers.clear();KOMEClientData.INSTANCE.unitMapMarkers.add(marker);KOMEClientData.INSTANCE.unitMapMarkers.add(marker);
            java.lang.reflect.Method counts=KOMEConquestMapOverlay.class.getDeclaredMethod("liveCompanyStationedPopulationByTile");counts.setAccessible(true);
            assertEquals(Integer.valueOf(50),((Map)counts.invoke(null)).get("T001"));
            company.movementOrderId="move";assertNull(anchor.invoke(null,map,marker));assertTrue(((Map)counts.invoke(null)).isEmpty());company.movementOrderId="";
            company.currentTile="T002";assertNull(anchor.invoke(null,map,marker));company.currentTile="T001";
            centers().remove("T001");assertNull("Never fabricate a missing tile anchor",anchor.invoke(null,map,marker));centers().put("T001",new int[]{2292,58});
            marker.unitCount=1;assertNotEquals(400,((int[])anchor.invoke(null,map,marker))[0]);
            marker.unitCount=0;KOMEClientData.INSTANCE.armyCompanies.clear();assertNull(anchor.invoke(null,map,marker));
        }finally{KOMEClientData.INSTANCE.unitMapMarkers.clear();KOMEClientData.INSTANCE.unitMapMarkers.addAll(old);}
    }

    @Test public void unsupportedOrFailedBuildSeamNeverLeavesInvisibleBuildClickable()throws Exception {
        preserve(KOMEClientData.INSTANCE.builds);KOMEClientData.INSTANCE.builds.clear();
        save(field(KOMEConquestMapOverlay.class,"showBuildMarkers"));field(KOMEConquestMapOverlay.class,"showBuildMarkers").setBoolean(null,true);
        KOMEPlayerBuild build=new KOMEPlayerBuild();build.id="";build.dimension=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;KOMEClientData.INSTANCE.builds.put(build.id,build);pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,1);
        java.lang.reflect.Method pick=KOMEConquestMapOverlay.class.getDeclaredMethod("buildMarkerAt",LOTRGuiMap.class,int.class,int.class);pick.setAccessible(true);
        field(KOMEConquestMapOverlay.class,"buildSpritesMap").set(null,null);assertNull(pick.invoke(null,map,398,298));
        field(KOMEConquestMapOverlay.class,"buildSpritesMap").set(null,map);assertSame(build,pick.invoke(null,map,398,298));
    }

    @Test public void mapControlsUseNativeTenPixelSquaresAndFourteenPixelRows() throws Exception {
        assertEquals(10,field(KOMEConquestMapOverlay.class,"TOGGLE_BUTTON_SIZE").getInt(null));
        java.lang.reflect.Method y=KOMEConquestMapOverlay.class.getDeclaredMethod("territoryControlY",int.class);y.setAccessible(true);
        assertEquals(14,(int)(Integer)y.invoke(null,1)-(int)(Integer)y.invoke(null,0));
    }

    @Test public void rightColumnAlignsWithNativeEdgeBelowWaypointActionsAndBuildClickPersists()throws Exception {
        java.lang.reflect.Method x=KOMEConquestMapOverlay.class.getDeclaredMethod("toggleButtonX",int.class);x.setAccessible(true);
        java.lang.reflect.Method y=KOMEConquestMapOverlay.class.getDeclaredMethod("toggleButtonY",int.class);y.setAccessible(true);
        assertEquals(784,x.invoke(null,4));assertEquals(76,y.invoke(null,4));
        assertEquals(20,y.invoke(null,0));
        java.lang.reflect.Method left=KOMEConquestMapOverlay.class.getDeclaredMethod("territoryControlY",int.class);left.setAccessible(true);
        java.lang.reflect.Method hover=KOMEConquestMapOverlay.class.getDeclaredMethod("isOverToggleButton",int.class,int.class,int.class,int.class);hover.setAccessible(true);
        for(int row=0;row<5;row++) {
            int top=(Integer)y.invoke(null,row);assertEquals(20+14*row,top);
            assertEquals(true,hover.invoke(null,784,top,784,top));
            assertEquals(true,hover.invoke(null,784,top,793,top+9));
            assertEquals(false,hover.invoke(null,784,top,784,top-1));
            assertEquals(false,hover.invoke(null,784,top,794,top));
            assertEquals(false,hover.invoke(null,784,top,784,top+10));
            if(row<4)assertEquals(34+14*row,left.invoke(null,row));
        }
        save(field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome"));
        field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome").set(null,preferences.getRoot());
        java.io.File file=new java.io.File(preferences.getRoot(),"kome-client.cfg");
        KOMEMapPreferences old=KOMEConquestMapOverlay.mapPreferences();
        try {
            new KOMEClientConfig(file);KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences());
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,785,77,0));
            assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
            new KOMEClientConfig(file);
            assertFalse(new net.minecraftforge.common.config.Configuration(file).get("map","buildMarkers",true).getBoolean(true));
            assertTrue(network.sent.isEmpty());
        }finally{KOMEConquestMapOverlay.applyMapPreferences(old);}
    }

    @Test public void hiddenBuildPreferenceSuppressesPickingAndDrawingWithoutChangingBuildRecord()throws Exception {
        save(field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome"));
        field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome").set(null,preferences.getRoot());
        preserve(KOMEClientData.INSTANCE.builds);KOMEClientData.INSTANCE.builds.clear();
        KOMEPlayerBuild build=new KOMEPlayerBuild();build.id="";build.dimension=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;
        KOMEClientData.INSTANCE.builds.put(build.id,build);
        java.lang.reflect.Method wx=KOMEConquestMapOverlay.class.getDeclaredMethod("worldScreenX",LOTRGuiMap.class,double.class);wx.setAccessible(true);
        java.lang.reflect.Method wy=KOMEConquestMapOverlay.class.getDeclaredMethod("worldScreenY",LOTRGuiMap.class,double.class);wy.setAccessible(true);
        java.lang.reflect.Method pick=KOMEConquestMapOverlay.class.getDeclaredMethod("buildMarkerAt",LOTRGuiMap.class,int.class,int.class);pick.setAccessible(true);
        pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,1); // Put world zero visibly on the map.
        int x=(Integer)wx.invoke(null,map,build.x),y=(Integer)wy.invoke(null,map,build.z);
        KOMEMapPreferences old=KOMEConquestMapOverlay.mapPreferences();
        try {
            KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences());assertSame(build,pick.invoke(null,map,x,y));
            java.io.File file=new java.io.File(preferences.getRoot(),"kome-client.cfg");
            net.minecraftforge.common.config.Configuration config=new net.minecraftforge.common.config.Configuration(file);
            config.get("map","buildMarkers",true).set(false);config.save();
            KOMEConquestMapOverlay.applyMapPreferences(new KOMEClientConfig(file).mapPreferences());
            assertNull("Hidden Build icons must not remain clickable",pick.invoke(null,map,x,y));
            java.lang.reflect.Method draw=KOMEConquestMapOverlay.class.getDeclaredMethod("drawBuildMarkers",LOTRGuiMap.class,int.class,int.class);draw.setAccessible(true);
            assertNull("Hidden draw path needs no GL context",draw.invoke(null,map,x,y));
            assertSame(build,KOMEClientData.INSTANCE.builds.get(""));assertTrue(build.markerVisible);assertTrue(build.active);
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,785,77,0));
            assertSame("Turning the layer back on restores picking immediately",build,pick.invoke(null,map,x,y));
            assertTrue(new KOMEClientConfig(file).mapPreferences().builds);assertTrue(network.sent.isEmpty());
        }finally{KOMEConquestMapOverlay.applyMapPreferences(old);}
    }

    @Test public void automaticBridgeClippingUsesSpriteCanvasAtEveryViewportEdge() {
        KOMEMapViewport view=new KOMEMapViewport(0,0,800,600,0,0,1);
        for(float zoom:new float[]{1,2,.5F,.25F,.125F,16}) {
            pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,zoom);
            int[][] anchors={{(int)Math.ceil(3*zoom),300},{800-(int)Math.ceil(3*zoom),300},
                {400,(int)Math.ceil(zoom)},{400,600-(int)Math.ceil(2*zoom)}};
            for(int[] anchor:anchors) {
                KOMEMapIconPresentation.MapBounds sprite=KOMEMapIconPresentation.geography(zoom,anchor[0],anchor[1],6,3);
                assertTrue("Fixture sprite fits on map",sprite.visible(view));
                KOMEMapIconPresentation.MapBounds actual=KOMEConquestMapOverlay.automaticBridgeBounds(map,anchor[0],anchor[1]);
                assertTrue("A visible PNG must not be clipped by the old halo at zoom "+zoom,actual.visible(view));
                assertEquals(6*zoom,actual.width,0);assertEquals(3*zoom,actual.height,0);
                assertEquals(sprite.x,actual.x,0);assertEquals(sprite.y,actual.y,0);
            }
            assertFalse(KOMEConquestMapOverlay.automaticBridgeBounds(map,0,300).visible(view));
        }
    }

    @Test public void smallerBridgeLogicalCanvasStillScalesExactlyWithNativeZoom() {
        for(float zoom:new float[]{.125F,.25F,.5F,.73F,1,2,16}) {
            pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,zoom);
            KOMEMapIconPresentation.MapBounds b=KOMEConquestMapOverlay.automaticBridgeBounds(map,400,300);
            assertEquals(6D*zoom,b.width,0);assertEquals(3D*zoom,b.height,0);
            assertEquals(400-3D*zoom,b.x,0);assertEquals(300D-zoom,b.y,0);
        }
    }

    @Test public void smallerRiverCanvasScalesExactlyAndKeepsItsProjectedAnchorAndHitBounds() {
        KOMEMapViewport view=new KOMEMapViewport(0,0,800,600,0,0,1);
        for(float zoom:new float[]{.125F,.25F,.5F,.73F,1,2,16}) {
            pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,zoom);
            KOMEMapIconPresentation.MapBounds b=KOMEConquestMapOverlay.riverMarkerBounds(map,400,300);
            assertEquals(4D*zoom,b.width,0);assertEquals(2D*zoom,b.height,0);
            assertEquals(400-2D*zoom,b.x,0);assertEquals(300D-zoom,b.y,0);
            assertTrue(b.visible(view));assertTrue(b.contains(400,300));
            assertFalse(b.contains(b.x-.001,b.y));assertFalse(b.contains(b.x+b.width,b.y));
            assertFalse(KOMEConquestMapOverlay.riverMarkerBounds(map,0,300).visible(view));
        }
    }

    @Test public void automaticBridgeHoverRejectsFormerBadgePixelsAndHonorsPointerPrecedence() {
        for(float zoom:new float[]{1,2,.5F,.25F,.125F,16}) {
            pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,zoom);
            KOMEMapIconPresentation.MapBounds sprite=KOMEConquestMapOverlay.automaticBridgeBounds(map,400,300);
            assertTrue(KOMEConquestMapOverlay.automaticBridgeHovered(map,sprite,400,300));
            for(int y=299-(int)Math.ceil(6*zoom);y<=301+(int)Math.ceil(6*zoom);y++)
                assertEquals("Hover must match PNG canvas at zoom "+zoom+" y "+y,sprite.contains(400,y),KOMEConquestMapOverlay.automaticBridgeHovered(map,sprite,400,y));
            for(int x=399-(int)Math.ceil(6*zoom);x<=401+(int)Math.ceil(6*zoom);x++)
                assertEquals(sprite.contains(x,300),KOMEConquestMapOverlay.automaticBridgeHovered(map,sprite,x,300));
            KOMEMapIconPresentation.MapBounds underControl=KOMEConquestMapOverlay.automaticBridgeBounds(map,785,21);
            assertFalse("Map control owns pointer over a bridge",KOMEConquestMapOverlay.automaticBridgeHovered(map,underControl,785,21));
            KOMEMapIconPresentation.MapBounds clipped=KOMEConquestMapOverlay.automaticBridgeBounds(map,0,300);
            assertFalse("Clipped sprites cannot supply tooltips",KOMEConquestMapOverlay.automaticBridgeHovered(map,clipped,0,300));
        }
    }

    @Test public void partiallyOverlappingBuildsPickTheLastDrawnMarker()throws Exception {
        overlappingBuildPick(false);
    }
    @Test public void coincidentBuildsPickTheLastDrawnMarker()throws Exception {
        overlappingBuildPick(true);
    }
    private void overlappingBuildPick(boolean coincident)throws Exception {
        preserve(KOMEClientData.INSTANCE.builds);
        KOMEMapPreferences old=KOMEConquestMapOverlay.mapPreferences();
        java.lang.reflect.Method pick=KOMEConquestMapOverlay.class.getDeclaredMethod("buildMarkerAt",LOTRGuiMap.class,int.class,int.class);pick.setAccessible(true);
        try {
            KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences());
            for(float zoom:new float[]{.125F,.5F,1,16}) {
                pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,zoom);
                KOMEPlayerBuild a=new KOMEPlayerBuild(),b=new KOMEPlayerBuild();a.id="a";b.id="b";
                a.dimension=b.dimension=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;
                if(coincident)b.x=-LOTRGenLayerWorld.scale/(double)zoom; // Compensate only the existing ID offset in this fixture.
                KOMEClientData.INSTANCE.builds.clear();KOMEClientData.INSTANCE.builds.put("a",a);KOMEClientData.INSTANCE.builds.put("b",b);
                assertSame("The visible upper Build must own the overlap at zoom "+zoom,b,pick.invoke(null,map,400,298));
            }
        }finally{KOMEConquestMapOverlay.applyMapPreferences(old);}
    }

    @Test public void buildDrawHoverAndPickAgreeForEveryOverlapPixelRegardlessOfInsertionOrder()throws Exception {
        preserve(KOMEClientData.INSTANCE.builds);
        KOMEMapPreferences old=KOMEConquestMapOverlay.mapPreferences();
        java.lang.reflect.Method pick=KOMEConquestMapOverlay.class.getDeclaredMethod("buildMarkerAt",LOTRGuiMap.class,int.class,int.class);pick.setAccessible(true);
        try {
            KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences());
            // Equal String hashes ensure insertion order really changes a HashMap bucket's iteration order.
            assertEquals("Aa".hashCode(),"BB".hashCode());
            for(float zoom:new float[]{.125F,.25F,.375F,.5F,.675F,.75F,.875F,1,16})for(boolean coincident:new boolean[]{false,true})for(boolean reverse:new boolean[]{false,true}) {
                pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,zoom);
                KOMEPlayerBuild a=new KOMEPlayerBuild(),b=new KOMEPlayerBuild();a.id="Aa";b.id="BB";a.displayName="Lower";b.displayName="Upper";
                a.type=KOMEBuildType.NORMAL;b.type=KOMEBuildType.DEFENSIVE;a.populationFaction="gondor";
                a.dimension=b.dimension=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;
                b.x=coincident?0:2D*LOTRGenLayerWorld.scale/zoom;
                KOMEClientData.INSTANCE.builds.clear();
                KOMEClientData.INSTANCE.builds.put(reverse?b.id:a.id,reverse?b:a);KOMEClientData.INSTANCE.builds.put(reverse?a.id:b.id,reverse?a:b);
                List<KOMEPlayerBuild> drawn=new ArrayList<>();Map<KOMEPlayerBuild,KOMEMapIconPresentation.Bounds> bounds=new IdentityHashMap<>();
                KOMEConquestMapOverlay.renderBuildMarkers(map,-1,-1,(build,box,p)->{drawn.add(build);bounds.put(build,box);});
                assertEquals(Arrays.asList(a,b),drawn);
                assertEquals(400,bounds.get(a).x+bounds.get(a).width/2);assertEquals(301,bounds.get(a).y+bounds.get(a).height/2);
                assertEquals(coincident?400:402,bounds.get(b).x+bounds.get(b).width/2);
                assertEquals(0,a.x,0);assertEquals(coincident?0:2D*LOTRGenLayerWorld.scale/zoom,b.x,0);
                assertEquals(KOMEMapMarkerArt.banner(a.populationFaction).bounds(KOMEMapIconPresentation.at(zoom),0,0).width,bounds.get(a).width);
                assertEquals(KOMEMapMarkerArt.banner(a.populationFaction).bounds(KOMEMapIconPresentation.at(zoom),0,0).height,bounds.get(a).height);
                assertEquals(KOMEMapMarkerArt.CASTLE.bounds(KOMEMapIconPresentation.at(zoom),0,0).height,bounds.get(b).height);
                int minX=Math.min(bounds.get(a).x,bounds.get(b).x)-1,minY=Math.min(bounds.get(a).y,bounds.get(b).y)-1;
                int maxX=Math.max(bounds.get(a).x+bounds.get(a).width,bounds.get(b).x+bounds.get(b).width);
                int maxY=Math.max(bounds.get(a).y+bounds.get(a).height,bounds.get(b).y+bounds.get(b).height);
                for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++) {
                    KOMEPlayerBuild expected=null;
                    for(KOMEPlayerBuild build:drawn)if(bounds.get(build).contains(x,y))expected=build;
                    assertSame(expected,pick.invoke(null,map,x,y));
                    List<String> tooltip=KOMEConquestMapOverlay.renderBuildMarkers(map,x,y,(build,box,p)->{});
                    if(expected==null)assertNull(tooltip);else assertEquals(expected.displayName+" ("+expected.id+")",tooltip.get(0));
                }
            }
        }finally{KOMEConquestMapOverlay.applyMapPreferences(old);}
    }

    @Test public void filteredUpperBuildNeverObscuresVisibleLowerBuildOrOwnsItsTooltip()throws Exception {
        preserve(KOMEClientData.INSTANCE.builds);
        KOMEMapPreferences old=KOMEConquestMapOverlay.mapPreferences();
        java.lang.reflect.Method pick=KOMEConquestMapOverlay.class.getDeclaredMethod("buildMarkerAt",LOTRGuiMap.class,int.class,int.class);pick.setAccessible(true);
        try {
            KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences());
            for(float zoom:new float[]{.125F,1})for(int filter=0;filter<5;filter++) {
                pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,zoom);
                KOMEPlayerBuild a=new KOMEPlayerBuild(),b=new KOMEPlayerBuild();a.id="Aa";b.id="BB";a.displayName="Visible";
                a.dimension=b.dimension=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;
                if(filter==0)b.markerVisible=false;if(filter==1)b.active=false;if(filter==2)b.dimension++;
                if(filter==3)b.x=10000000;
                KOMEClientData.INSTANCE.builds.clear();KOMEClientData.INSTANCE.builds.put(a.id,a);KOMEClientData.INSTANCE.builds.put(b.id,filter==4?null:b);
                List<KOMEPlayerBuild> drawn=new ArrayList<>();
                List<String> tooltip=KOMEConquestMapOverlay.renderBuildMarkers(map,400,301,(build,box,p)->drawn.add(build));
                assertEquals(Collections.singletonList(a),drawn);assertSame(a,pick.invoke(null,map,400,301));assertEquals("Visible (Aa)",tooltip.get(0));
                field(KOMEConquestMapOverlay.class,"showBuildMarkers").setBoolean(null,false);
                assertNull(KOMEConquestMapOverlay.renderBuildMarkers(map,400,301,(build,box,p)->fail("Hidden layer must not draw")));
                assertNull(pick.invoke(null,map,400,301));field(KOMEConquestMapOverlay.class,"showBuildMarkers").setBoolean(null,true);
                field(KOMEConquestMapOverlay.class,"destinationCompanyId").set(null,"company");
                assertNull(KOMEConquestMapOverlay.renderBuildMarkers(map,400,301,(build,box,p)->{}));assertNull(pick.invoke(null,map,400,301));
                field(KOMEConquestMapOverlay.class,"destinationCompanyId").set(null,"");
            }
        }finally{KOMEConquestMapOverlay.applyMapPreferences(old);}
    }

    @Test public void farZoomBuildPickingUsesReducedVisibleBoundsAndRejectsOffscreenMarkers()throws Exception {
        preserve(KOMEClientData.INSTANCE.builds);KOMEClientData.INSTANCE.builds.clear();
        KOMEPlayerBuild build=new KOMEPlayerBuild();build.id="";build.dimension=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;KOMEClientData.INSTANCE.builds.put("",build);
        java.lang.reflect.Method pick=KOMEConquestMapOverlay.class.getDeclaredMethod("buildMarkerAt",LOTRGuiMap.class,int.class,int.class);pick.setAccessible(true);
        KOMEMapPreferences old=KOMEConquestMapOverlay.mapPreferences();
        try {
            KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences());pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,.125F);
            assertSame(build,pick.invoke(null,map,398,298));
            assertNull("Old padded picking must not extend beyond the reduced icon",pick.invoke(null,map,402,298));
            field(KOMEConquestMapOverlay.class,"showConquestTiles").setBoolean(null,false);
            assertNull("Hidden conquest layer must not leave an interactive Build",pick.invoke(null,map,398,298));
            field(KOMEConquestMapOverlay.class,"showConquestTiles").setBoolean(null,true);
            pan(LOTRGenLayerWorld.originX+100000,LOTRGenLayerWorld.originZ,1);
            java.lang.reflect.Method wx=KOMEConquestMapOverlay.class.getDeclaredMethod("worldScreenX",LOTRGuiMap.class,double.class);wx.setAccessible(true);
            assertNull("An undrawn off-map Build must not remain clickable",pick.invoke(null,map,(Integer)wx.invoke(null,map,0D)-2,298));
        }finally{KOMEConquestMapOverlay.applyMapPreferences(old);}
    }

    @Test public void missingNativeAndFallbackArtworkCannotLeaveInvisibleBuildsInteractive()throws Exception {
        preserve(KOMEClientData.INSTANCE.builds);save(field(KOMEMapMarkerArt.class,"available"));save(field(KOMEMapMarkerArt.class,"validated"));
        KOMEMapPreferences old=KOMEConquestMapOverlay.mapPreferences();
        try {
            KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences());pan(LOTRGenLayerWorld.originX,LOTRGenLayerWorld.originZ,1);
            KOMEPlayerBuild build=new KOMEPlayerBuild();build.id="a";build.type=KOMEBuildType.NORMAL;build.populationFaction="gondor";build.dimension=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;
            KOMEClientData.INSTANCE.builds.clear();KOMEClientData.INSTANCE.builds.put(build.id,build);
            KOMEMapMarkerArt.reload((net.minecraft.client.resources.IResourceManager)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{net.minecraft.client.resources.IResourceManager.class},(p,m,args)->{throw new java.io.FileNotFoundException();}));
            assertNull(KOMEConquestMapOverlay.renderBuildMarkers(map,400,300,(b,box,policy)->fail("No visible sprite")));
            java.lang.reflect.Method pick=KOMEConquestMapOverlay.class.getDeclaredMethod("buildMarkerAt",LOTRGuiMap.class,int.class,int.class);pick.setAccessible(true);assertNull(pick.invoke(null,map,400,300));
        }finally{KOMEConquestMapOverlay.applyMapPreferences(old);}
    }

    @Test public void buildMarkerUnderVisualControlCannotLeakDestinationPreviewOrCapturePanelInput()throws Exception {
        preserve(KOMEClientData.INSTANCE.builds);KOMEClientData.INSTANCE.builds.clear();
        KOMEPlayerBuild build=new KOMEPlayerBuild();build.id="";build.dimension=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;
        build.x=(2292-LOTRGenLayerWorld.originX)*(double)LOTRGenLayerWorld.scale;
        build.z=(58-LOTRGenLayerWorld.originZ)*(double)LOTRGenLayerWorld.scale;KOMEClientData.INSTANCE.builds.put("",build);
        java.lang.reflect.Method pick=KOMEConquestMapOverlay.class.getDeclaredMethod("buildMarkerAt",LOTRGuiMap.class,int.class,int.class);pick.setAccessible(true);
        save(field(KOMEConquestMapOverlay.class,"destinationOriginTile"));
        KOMEMapPreferences old=KOMEConquestMapOverlay.mapPreferences();
        try(DirectClient client=new DirectClient()) {
            KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences());
            for(float zoom:new float[]{.125F,1,16}) {
                // Keep a real claimable tile under the pointer, and the offset Build icon above it.
                pan(2292+(400-785)/zoom,58+(300-21)/zoom,zoom);
                build.x=(2292+2/zoom-LOTRGenLayerWorld.originX)*(double)LOTRGenLayerWorld.scale;
                build.z=(58+2/zoom-LOTRGenLayerWorld.originZ)*(double)LOTRGenLayerWorld.scale;
                assertNull("Native-style control owns the pointer above the marker",pick.invoke(null,map,785,21));
                field(KOMEConquestMapOverlay.class,"destinationCompanyId").set(null,"company");field(KOMEConquestMapOverlay.class,"destinationOriginTile").set(null,"T100");
                assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,785,21,1));
                KOMEConquestMapOverlay.selectDestinationAt(map,785,21);
                assertTrue(client.player.messages.isEmpty());assertTrue(network.sent.isEmpty());
            }
        }finally{KOMEConquestMapOverlay.applyMapPreferences(old);}
    }

    @Test public void changingThePaletteControlPersistsAcrossClientRestart() throws Exception {
        save(field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome"));
        field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome").set(null,preferences.getRoot());
        java.io.File file=new java.io.File(preferences.getRoot(),"kome-client.cfg");
        KOMETerritoryStyle.Palette palette=KOMEConquestMapOverlay.territoryStyle.palette;
        try {
            new KOMEClientConfig(file);KOMEConquestMapOverlay.territoryStyle.palette=KOMETerritoryStyle.Palette.CLASSIC_LOTR;
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,9,35,0));
            new KOMEClientConfig(file);
            net.minecraftforge.common.config.Configuration restored=new net.minecraftforge.common.config.Configuration(file);
            assertEquals("CURRENT_KOME",restored.get("map","factionPalette","missing").getString());
        } finally {KOMEConquestMapOverlay.territoryStyle.palette=palette;}
    }

    @Test public void everyMapControlRestoresItsValueAfterSimulatedRestart() throws Exception {
        save(field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome"));field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome").set(null,preferences.getRoot());
        KOMEMapPreferences old=KOMEConquestMapOverlay.mapPreferences();java.io.File file=new java.io.File(preferences.getRoot(),"kome-client.cfg");
        try {
            new KOMEClientConfig(file);KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences());
            for(int control=0;control<9;control++) {
                assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,control<4?9:785,control<4?35+control*14:21+(control-4)*14,0));
                KOMEMapPreferences selected=KOMEConquestMapOverlay.mapPreferences();
                KOMEConquestMapOverlay.applyMapPreferences(new KOMEMapPreferences());
                KOMEConquestMapOverlay.applyMapPreferences(new KOMEClientConfig(file).mapPreferences());
                assertEquals(selected,KOMEConquestMapOverlay.mapPreferences());
            }
            assertTrue(network.sent.isEmpty());
        }finally{KOMEConquestMapOverlay.applyMapPreferences(old);}
    }

    @Test public void destinationForcedVisibilityAndIgnoredClicksNeverOverwriteSavedLayerChoice() throws Exception {
        save(field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome"));field(cpw.mods.fml.relauncher.FMLInjectionData.class,"minecraftHome").set(null,preferences.getRoot());
        for(String name:new String[]{"destinationCompanyName","destinationOriginTile","waitForDestinationClickRelease"})save(field(KOMEConquestMapOverlay.class,name));
        KOMEMapPreferences old=KOMEConquestMapOverlay.mapPreferences();java.io.File file=new java.io.File(preferences.getRoot(),"kome-client.cfg");
        try {
            KOMEClientConfig config=new KOMEClientConfig(file);config.setMapPreferences(new KOMEMapPreferences().withConquest(false));
            KOMEConquestMapOverlay.applyMapPreferences(config.mapPreferences());KOMEConquestMapOverlay.beginDestinationSelection("company","Company","T001");
            assertTrue(KOMEConquestMapOverlay.mapPreferences().conquest);
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,9,35,0));
            assertFalse(new KOMEClientConfig(file).mapPreferences().conquest);
            byte[] bytes=java.nio.file.Files.readAllBytes(file.toPath());
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,785,35,0));
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,9,35,1));
            assertArrayEquals(bytes,java.nio.file.Files.readAllBytes(file.toPath()));assertTrue(network.sent.isEmpty());
        }finally{KOMEConquestMapOverlay.applyMapPreferences(old);}
    }

    @Test public void coveredNativeStyleControlsBelongToThePanelForBothTooltipAndClickPicking() throws Exception {
        connectSelector();openSelector();KOMEMapPreferences before=KOMEConquestMapOverlay.mapPreferences();int requests=network.sent.size();
        for(boolean left:new boolean[]{true,false}) {
            field(KOMEConquestMapOverlay.class,"selectorOnLeft").setBoolean(null,left);int x=left?9:785,y=left?35:21;
            assertTrue(selectorLayout().panel.contains(x,y));assertTrue(KOMEConquestMapOverlay.mapControlCovered(map,x,y));
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,x,y,0));
            assertEquals(before,KOMEConquestMapOverlay.mapPreferences());assertEquals(requests,network.sent.size());
            assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        }
    }

    @Test public void rightColumnIsOneNativePitchBelowVisibleButtonsInEveryWaypointVisibilityState()throws Exception {
        java.lang.reflect.Method setup=LOTRGuiMap.class.getDeclaredMethod("setupMapWidgets");setup.setAccessible(true);setup.invoke(map);
        java.lang.reflect.Method anchor=KOMEConquestMapOverlay.class.getDeclaredMethod("toggleButtonY");anchor.setAccessible(true);
        List<LOTRGuiMapWidget> widgets=(List)field(LOTRGuiMap.class,"mapWidgets").get(null);
        // Use the real native widgets: add at 6, delete/hide at 20, rename at 34, share at 48.
        for(int[] size:new int[][]{{312,200},{320,240},{800,600},{1920,1080}})for(int inset:new int[]{0,30}) {
            field(LOTRGuiMap.class,"mapXMin").setInt(null,inset);field(LOTRGuiMap.class,"mapYMin").setInt(null,inset);
            field(LOTRGuiMap.class,"mapXMax").setInt(null,inset+size[0]);field(LOTRGuiMap.class,"mapYMax").setInt(null,inset+size[1]);
            field(LOTRGuiMap.class,"mapWidth").setInt(null,size[0]);field(LOTRGuiMap.class,"mapHeight").setInt(null,size[1]);
            for(int state=0;state<8;state++) {
                for(LOTRGuiMapWidget widget:widgets)widget.visible=widget.getMapYPos(size[1])==6;
                int last=6;
                for(int row=0;row<3;row++)if((state&(1<<row))!=0) {
                    int y=20+row*14;last=y;
                    for(LOTRGuiMapWidget widget:widgets)if(widget.getMapXPos(size[0])==size[0]-16&&widget.getMapYPos(size[1])==y)widget.visible=true;
                }
                int first=(Integer)anchor.invoke(null);
                assertEquals("One native pitch, without reserving hidden waypoint actions",14,first-inset-last);
                assertEquals("Four clear pixels below the visible native group",4,first-inset-(last+10));
                for(int row=0;row<5;row++) {
                    int x=size[0]-16,y=first-inset+row*14;assertTrue(y+10<=size[1]);
                    for(int px=x;px<x+10;px++)for(int py=y;py<y+10;py++)for(LOTRGuiMapWidget widget:widgets)
                        assertFalse("Visible native widget overlaps KOME",widget.isMouseOver(px,py,size[0],size[1]));
                }
            }
        }
    }

    @Test public void conquestHitboxesNeverIntersectActualNativeWidgetsAtSupportedGuiSizes() throws Exception {
        java.lang.reflect.Method setup=LOTRGuiMap.class.getDeclaredMethod("setupMapWidgets");setup.setAccessible(true);setup.invoke(map);
        java.lang.reflect.Method bx=KOMEConquestMapOverlay.class.getDeclaredMethod("toggleButtonX",int.class);bx.setAccessible(true);
        java.lang.reflect.Method by=KOMEConquestMapOverlay.class.getDeclaredMethod("toggleButtonY");by.setAccessible(true);
        java.lang.reflect.Method ty=KOMEConquestMapOverlay.class.getDeclaredMethod("territoryControlY",int.class);ty.setAccessible(true);
        List<LOTRGuiMapWidget> widgets=(List)field(LOTRGuiMap.class,"mapWidgets").get(null);
        assertFalse(widgets.isEmpty());for(LOTRGuiMapWidget widget:widgets)widget.visible=true;
        for(int[] size:new int[][]{{312,200},{320,240},{427,240},{640,360},{800,600},{1280,720},{1920,1080}}) {
            for(int inset:new int[]{0,30}) {
                field(LOTRGuiMap.class,"mapXMin").setInt(null,inset);field(LOTRGuiMap.class,"mapYMin").setInt(null,inset);
                field(LOTRGuiMap.class,"mapXMax").setInt(null,size[0]+inset);field(LOTRGuiMap.class,"mapYMax").setInt(null,size[1]+inset);
                field(LOTRGuiMap.class,"mapWidth").setInt(null,size[0]);field(LOTRGuiMap.class,"mapHeight").setInt(null,size[1]);
                for(int control=0;control<9;control++) {
                    int x=control<5?(Integer)bx.invoke(null,control):inset+6;
                    int y=control<5?(Integer)by.invoke(null)+control*14:(Integer)ty.invoke(null,control-5);
                    int width=10,height=10;
                    assertTrue(x>=inset&&x+width<=inset+size[0]);assertTrue(y>=inset&&y+height<=inset+size[1]);
                    for(int px=x;px<x+width;px++)for(int py=y;py<y+height;py++)for(LOTRGuiMapWidget widget:widgets)
                        assertFalse("Native widget overlaps conquest control "+control+" at "+px+","+py,
                            widget.isMouseOver(px-inset,py-inset,size[0],size[1]));
                }
                LOTRGuiMapWidget zoom=(LOTRGuiMapWidget)field(LOTRGuiMap.class,"widgetZoomOut").get(map);
                assertFalse(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,inset+zoom.getMapXPos(size[0]),inset+zoom.getMapYPos(size[1]),0));
            }
        }
    }

    @Test public void destinationPollDoesNotPreviewMoveThroughTerritoryControlsButAcceptsUncoveredMap() throws Exception {
        save(field(KOMEConquestMapOverlay.class,"destinationOriginTile"));
        try(DirectClient client=new DirectClient()) {
            field(KOMEConquestMapOverlay.class,"destinationCompanyId").set(null,"test-company");
            field(KOMEConquestMapOverlay.class,"destinationOriginTile").set(null,"T100");
            java.lang.reflect.Method ty=KOMEConquestMapOverlay.class.getDeclaredMethod("territoryControlY",int.class);ty.setAccessible(true);
            for(int row=0;row<4;row++) {
                int x=9,y=(Integer)ty.invoke(null,row)+1;
                pan(2292+400-x,58+300-y,1);
                assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,x,y,1));
                KOMEConquestMapOverlay.selectDestinationAt(map,x,y);
                assertTrue("Control click issued previewmove: "+client.player.messages,client.player.messages.isEmpty());
            }
            java.lang.reflect.Method bx=KOMEConquestMapOverlay.class.getDeclaredMethod("toggleButtonX",int.class);bx.setAccessible(true);
            java.lang.reflect.Method by=KOMEConquestMapOverlay.class.getDeclaredMethod("toggleButtonY");by.setAccessible(true);
            for(int control=0;control<5;control++) {
                int x=(Integer)bx.invoke(null,control)+1,y=(Integer)by.invoke(null)+control*14+1;
                pan(2292+400-x,58+300-y,1);
                KOMEMapPreferences before=KOMEConquestMapOverlay.mapPreferences();
                assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,x,y,0));
                assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
                assertEquals(before,KOMEConquestMapOverlay.mapPreferences());
                assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,x,y,1));
                KOMEConquestMapOverlay.selectDestinationAt(map,x,y);
                assertTrue(client.player.messages.isEmpty());
            }
            java.lang.reflect.Method setup=LOTRGuiMap.class.getDeclaredMethod("setupMapWidgets");setup.setAccessible(true);setup.invoke(map);
            for(LOTRGuiMapWidget widget:(List<LOTRGuiMapWidget>)field(LOTRGuiMap.class,"mapWidgets").get(null)) {
                widget.visible=true;int x=widget.getMapXPos(800)+1,y=widget.getMapYPos(600)+1;
                pan(2292+400-x,58+300-y,1);KOMEConquestMapOverlay.selectDestinationAt(map,x,y);
                assertTrue(client.player.messages.isEmpty());
            }
            List<GuiButton> buttons=(List)field(GuiScreen.class,"buttonList").get(map);
            buttons.add(new GuiButton(99,390,290,20,20,"Native action"));
            pan(2292,58,1);KOMEConquestMapOverlay.selectDestinationAt(map,400,300);
            assertTrue(client.player.messages.isEmpty());buttons.clear();
            pan(2292,58,1);KOMEConquestMapOverlay.selectDestinationAt(map,400,300);
            assertEquals(Collections.singletonList("/troops previewmove test-company T001"),client.player.messages);
        }
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
        assertTrue(clickRoot(3)); assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());
        assertTrue(menu.isOpen()); assertTrue(network.sent.isEmpty());
        assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,false)); assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
    }
    @Test public void unitsWithoutConnectionAndDisabledBattleShellSendNothing() {
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
        KOMEMapRadialMenu.Segment back=menu.segments().get(3);assertTrue(click(200+(int)Math.round(back.labelX()),200+(int)Math.round(back.labelY()),0));assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());
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
    @Test public void hidingCompanyRoutesKeepsAnOpenMovePreviewAndSendsNoMovement() throws Exception {
        save(field(KOMEConquestMapOverlay.class,"showTroopMarkers"));
        KOMEPacketCompanyMoveConfirmGui preview=new KOMEPacketCompanyMoveConfirmGui();
        preview.companyId="C1";preview.routeTiles.add("T001");preview.routeTiles.add("T002");
        KOMEConquestMapOverlay.beginRoutePreview(preview);
        field(KOMEConquestMapOverlay.class,"showTroopMarkers").setBoolean(null,false);
        assertFalse(KOMEConquestMapOverlay.companyMovementVisualsVisible());
        assertSame(preview,field(KOMEConquestMapOverlay.class,"routePreviewMove").get(null));
        assertEquals(2,((List)field(KOMEConquestMapOverlay.class,"routePreviewTiles").get(null)).size());
        assertTrue(KOMEConquestMapOverlay.routePreviewDisplayText().contains("hidden"));
        assertTrue(network.sent.isEmpty());
        field(KOMEConquestMapOverlay.class,"showTroopMarkers").setBoolean(null,true);
        assertTrue(KOMEConquestMapOverlay.companyMovementVisualsVisible());
        assertSame(preview,field(KOMEConquestMapOverlay.class,"routePreviewMove").get(null));
        assertFalse(KOMEConquestMapOverlay.routePreviewDisplayText().contains("hidden"));
        assertTrue(network.sent.isEmpty());
    }
    @Test public void inspectionSuppressionCoversTheWholeInteractionAndRestoresAfterClose() {
        assertFalse(KOMEConquestMapOverlay.suppressMapInspection(map));
        assertFalse(KOMEConquestMapOverlay.suppressMapInspection(null));
        click(400,300,1); assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
        assertFalse(click(400,300,0)); // Fallback center is still pass-through, without a fictitious waypoint.
        assertTrue(menu.isOpen()); assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
        clickRoot(0); assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
        clickRoot(3); assertTrue(KOMEConquestMapOverlay.suppressMapInspection(map));
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
                KOMEMapRadialMenu.Segment back=menu.segments().get(3);assertTrue(click(400+(int)Math.round(back.labelX()*scale),300+(int)Math.round(back.labelY()*scale),0));
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
        for(KOMECompanyDestinationPanel.Control c:Arrays.asList(KOMECompanyDestinationPanel.Control.ROWS_UP,KOMECompanyDestinationPanel.Control.ROWS_DOWN)) {
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
        field(KOMEConquestMapOverlay.class,"selectorOnLeft").setBoolean(null,false);
        java.lang.reflect.Method bx=KOMEConquestMapOverlay.class.getDeclaredMethod("toggleButtonX",int.class);bx.setAccessible(true);
        java.lang.reflect.Method by=KOMEConquestMapOverlay.class.getDeclaredMethod("toggleButtonY");by.setAccessible(true);
        int x=((Integer)bx.invoke(null,0))+1,y=((Integer)by.invoke(null))+1;
        assertTrue(KOMEConquestMapOverlay.selectorOwnsPointer(map,x,y));
        assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,x,y,0));
        assertTrue(field(KOMEConquestMapOverlay.class,"showConquestTiles").getBoolean(null));
        field(KOMEConquestMapOverlay.class,"selectorOnLeft").setBoolean(null,true);
        KOMETerritoryStyle.Palette palette=KOMEConquestMapOverlay.territoryStyle.palette;
        assertTrue(KOMEConquestMapOverlay.selectorOwnsPointer(map,9,35));
        assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,9,35,0));
        assertEquals(palette,KOMEConquestMapOverlay.territoryStyle.palette);
        assertPreviewRequestsOnly();
    }
    @Test public void disabledSegmentConsumesWaypointClickWithoutActionOrMapDrag() throws Exception {
        click(400,300,1);clickRoot(1);KOMEMapRadialMenu.Segment disabled=menu.segments().get(0);
        assertFalse(disabled.action.enabled);
        assertTrue(nativeWaypointClick(lotr.common.world.map.LOTRWaypoint.values()[1],
            400+(int)Math.round(disabled.labelX()),300+(int)Math.round(disabled.labelY())));
        assertEquals(KOMEMapRadialMenu.State.BATTLE,menu.state());assertNull(field(LOTRGuiMap.class,"selectedWaypoint").get(map));
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
    @Test public void wheelReachesEighteenCompaniesWithFullCrossPageWindowsAndBack() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();Set<String> seen=new HashSet<String>();List<List<String>> windows=new ArrayList<List<String>>();
        int served=0;
        while(true) {
            if(selector().phase()==KOMECompanyDestinationSelector.Phase.LOADING) {
                KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(network.sent.size()-1);
                int from=request.afterCompanyId.isEmpty()?1:Integer.parseInt(request.afterCompanyId.substring(1))+1;
                List<KOMECompanyDestinationOptions.Row> rows=new ArrayList<KOMECompanyDestinationOptions.Row>();
                for(int i=from;i<=Math.min(18,from+7);i++)rows.add(mapRow(String.format(Locale.ROOT,"C%02d",i),kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW));
                publish(proxy,KOMECompanyDestinationSelectorTest.page(request,KOMECompanyDestinationOptions.Result.OK,rows,from+7<18,0));served++;
            }
            List<String> current=new ArrayList<String>();for(int i=selector().rowOffset();i<selector().rowOffset()+4;i++)current.add(selector().rows().get(i).companyId);
            assertEquals(4,current.size());seen.addAll(current);windows.add(current);
            if(!selector().canBrowse(1,0))break;
            KOMECompanyDestinationPanel.Rect box=selectorLayout().row(0);assertEquals(0,KOMEConquestMapOverlay.selectorWheelDelta(map,box.x+1,box.y+1,-120));
        }
        assertEquals(18,seen.size());assertEquals(3,served);
        assertEquals(Arrays.asList("C06","C07","C08","C09"),windows.get(5));
        for(int i=windows.size()-2;i>=0;i--) {
            clickControl(KOMECompanyDestinationPanel.Control.ROWS_UP);List<String> actual=new ArrayList<String>();
            for(int j=selector().rowOffset();j<selector().rowOffset()+4;j++)actual.add(selector().rows().get(j).companyId);
            assertEquals(windows.get(i),actual);
        }
        assertEquals(3,network.sent.size());assertPreviewRequestsOnly();
    }
    @Test public void moveHereConsumesBudgetContinuationThroughTheLiveOverlayAdapter() throws Exception {
        budgetContinuationFromRadial(false);
    }
    @Test public void attackConsumesBudgetContinuationAndRetainsAdaptiveJoin() throws Exception {
        budgetContinuationFromRadial(true);
    }
    private void budgetContinuationFromRadial(boolean attack) throws Exception {
        try(BattlePresentationClient client=new BattlePresentationClient()) {
            KOMEClientProxy proxy=connectSelector();
            if(attack) {
                client.owner("mordor");click(400,300,1);clickRoot(1);
                assertEquals(KOMEMapRadialMenu.Action.ATTACK,menu.segments().get(0).action);
                assertTrue(click(400,240,0));
            } else openSelector();
            KOMEPacketCompanyDestinationRequest first=(KOMEPacketCompanyDestinationRequest)network.sent.get(network.sent.size()-1);
            publish(proxy,budgetRadialPage(first,"A",true,attack));
            assertEquals(KOMECompanyDestinationSelector.Phase.READY,selector().phase());
            assertEquals("A",selector().rows().get(0).companyId);
            int sent=network.sent.size();clickControl(KOMECompanyDestinationPanel.Control.RETRY);
            assertEquals(sent+1,network.sent.size());
            KOMEPacketCompanyDestinationRequest next=(KOMEPacketCompanyDestinationRequest)network.sent.get(sent);
            assertEquals("A",next.afterCompanyId);assertEquals(KOMECompanyDestinationSelectorTest.TOKEN,next.catalogueToken);
            assertEquals(first.session,next.session);assertEquals(first.destination,next.destination);
            assertEquals("A",selector().rows().get(0).companyId);
            publish(proxy,budgetRadialPage(next,"B",false,attack));
            assertEquals(2,selector().rows().size());
            selector().select("B");assertTrue(selector().canConfirm());
            assertEquals(attack,selector().canConfirmAndJoin());
            int accepted=network.sent.size();publish(proxy,budgetRadialPage(first,"A",true,attack));
            assertEquals(accepted,network.sent.size());assertEquals("B",selector().selectedCompanyId());
        }
    }
    private KOMECompanyDestinationOptions budgetRadialPage(KOMEPacketCompanyDestinationRequest request,String id,boolean more,boolean attack) {
        KOMECompanyDestinationOptions.Row row=new KOMECompanyDestinationOptions.Row(id,"Company "+id,"T002","T001",
            KOMECompanyDestinationOptions.Entitlement.FOOT_OR_MIXED,2,2,1,Arrays.asList("T002","T001"),
            kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW,
            kome.common.command.KOMECompanyMovementService.ScheduleMode.DAILY,true,true,true,true,0,10000,20000,
            "","","",-1,attack?kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT:
                kome.common.command.KOMECompanyMovementService.Consequence.NONE,"",-1,
            KOMECompanyDestinationSelectorTest.HASH,KOMECompanyDestinationSelectorTest.HASH);
        return KOMECompanyDestinationSelectorTest.page(request,more?KOMECompanyDestinationOptions.Result.ARRIVAL_BUDGET_EXCEEDED:
            KOMECompanyDestinationOptions.Result.OK,Collections.singletonList(row),more,0);
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
            assertEquals("T001",KOMEConquestMapOverlay.selectorDestinationTile(map));assertEquals("T001",battle.destination);assertEquals(8,battle.limit);assertEquals(KOMECompanyDestinationSelector.Phase.LOADING,selector().phase());
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
        clickControl(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN);assertEquals(1,network.sent.size());
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
        clickControl(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN);assertEquals(1,network.sent.size());
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
        @Override public void sendChatMessage(String message){messages.add(message);}
    }
    @Test public void filterControlRetainsVisibleSelectionAndClearsHiddenRoutePreview() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();
        KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(0);
        centers().put("T002",new int[]{2280,58});centers().put("T050",new int[]{2290,58});
        publish(proxy,KOMECompanyDestinationSelectorTest.page(request,KOMECompanyDestinationOptions.Result.OK,
            Arrays.asList(mapRow("I",kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW),
                mapRow("L",kome.common.command.KOMECompanyMovementService.Status.CAN_START_BUT_CONTINUES_LATER)),false,0));
        KOMECompanyDestinationPanel.Rect later=selectorLayout().row(1);assertTrue(click(later.x+1,later.y+1,0));
        assertEquals("L",selector().selected().companyId);assertFalse(preview().points.isEmpty());
        KOMECompanyDestinationPreview old=preview();clickControl(KOMECompanyDestinationPanel.Control.FILTER_READY);
        assertEquals(KOMECompanyDestinationSelector.Filter.READY,selector().filter());assertEquals("L",selector().selected().companyId);
        assertEquals(old.points.size(),preview().points.size());assertTrue(selector().canConfirm());
        clickControl(KOMECompanyDestinationPanel.Control.FILTER_ARRIVE);assertEquals(KOMECompanyDestinationSelector.Filter.ARRIVE_NOW,selector().filter());
        assertNull(selector().selected());assertFalse(selector().canConfirm());assertTrue(preview().points.isEmpty());assertEquals(1,network.sent.size());
    }
    @Test public void moveAndBattleEntriesShareTheSameFilterAndWheelContinuation() throws Exception {
        for(boolean battle:new boolean[]{false,true})try(DirectClient client=new DirectClient()) {
            client.mc.thePlayer=null; // Battle context needs no native player bootstrap in this input fixture.
            KOMEClientProxy proxy=connectSelector();
            if(battle) {
                click(400,300,1);clickRoot(1);KOMEPacketJoinBattleViewRequest context=(KOMEPacketJoinBattleViewRequest)network.sent.get(0);
                proxy.acceptJoinBattleView(battleView(context,KOMEConflictRecord.State.ORDINARY),selectorConnection);assertTrue(click(400,240,0));
            }else openSelector();
            KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(network.sent.size()-1);
            publish(proxy,KOMECompanyDestinationSelectorTest.page(request,KOMECompanyDestinationOptions.Result.OK,
                Arrays.asList(mapRow("I",kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW),
                    mapRow("I2",kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW),
                    mapRow("I3",kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW),
                    mapRow("I4",kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW),
                    mapRow("L",kome.common.command.KOMECompanyMovementService.Status.CAN_START_BUT_CONTINUES_LATER),
                    mapRow("Z",kome.common.command.KOMECompanyMovementService.Status.BLOCKED)),true,0));
            clickControl(KOMECompanyDestinationPanel.Control.FILTER_READY);assertEquals(KOMECompanyDestinationSelector.Filter.READY,selector().filter());assertEquals(5,selector().rows().size());
            int sent=network.sent.size();KOMECompanyDestinationPanel.Rect row=selectorLayout().row(0);
            assertEquals(0,KOMEConquestMapOverlay.selectorWheelDelta(map,row.x+1,row.y+1,-120));assertEquals(sent,network.sent.size());
            assertEquals(0,KOMEConquestMapOverlay.selectorWheelDelta(map,row.x+1,row.y+1,-120));assertEquals(sent+1,network.sent.size());
            KOMEPacketCompanyDestinationRequest next=(KOMEPacketCompanyDestinationRequest)network.sent.get(sent);
            assertEquals("Z",next.afterCompanyId);assertEquals(8,next.limit);assertEquals(KOMECompanyDestinationSelector.Filter.READY,selector().filter());
            for(IMessage message:network.sent)assertTrue(message instanceof KOMEPacketCompanyDestinationRequest||message instanceof KOMEPacketJoinBattleViewRequest);
            network.sent.clear();
        }
    }
    @Test public void panelOwnsWheelClicksAndDragAtSeveralGuiScalesWhileOutsideDeltaPassesThrough() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(0);
        List<KOMECompanyDestinationOptions.Row> rows=new ArrayList<KOMECompanyDestinationOptions.Row>();
        for(int i=0;i<8;i++)rows.add(mapRow("C"+i,kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW));
        publish(proxy,KOMECompanyDestinationSelectorTest.page(request,KOMECompanyDestinationOptions.Result.OK,rows,false,0));
        float zoom=field(LOTRGuiMap.class,"zoomScale").getFloat(map);
        for(int[] size:new int[][]{{320,240},{480,270},{640,360},{960,540}}) {
            map.width=size[0];map.height=size[1];field(LOTRGuiMap.class,"mapXMax").setInt(null,size[0]);field(LOTRGuiMap.class,"mapYMax").setInt(null,size[1]);
            field(LOTRGuiMap.class,"mapWidth").setInt(null,size[0]);field(LOTRGuiMap.class,"mapHeight").setInt(null,size[1]);
            KOMECompanyDestinationPanel.Layout layout=selectorLayout();selector().scrollRows(-100,layout.capacity);KOMECompanyDestinationPanel.Rect row=layout.row(0);
            assertEquals(0,KOMEConquestMapOverlay.selectorWheelDelta(map,row.x+1,row.y+1,-120));assertEquals(1,selector().rowOffset());
            assertEquals(-120,KOMEConquestMapOverlay.selectorWheelDelta(map,2,2,-120));assertEquals(zoom,field(LOTRGuiMap.class,"zoomScale").getFloat(map),0);
            assertTrue(click(row.x+1,row.y+1,0));assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
            assertTrue(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,row.x+1,row.y+1,1));
            assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,false));assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
            assertNull(field(LOTRGuiMap.class,"selectedWaypoint").get(map));
        }
        assertEquals(1,network.sent.size());
    }
    @Test public void filteredEmptyRepliesFollowBoundedSearchAndLoadingWheelNeverFloodsRequests() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(0);
        publish(proxy,KOMECompanyDestinationSelectorTest.page(request,KOMECompanyDestinationOptions.Result.OK,
            Collections.singletonList(mapRow("B0",kome.common.command.KOMECompanyMovementService.Status.BLOCKED)),true,0));
        clickControl(KOMECompanyDestinationPanel.Control.FILTER_READY);
        for(int i=1;i<=KOMECompanyDestinationSelector.MAX_SEARCH_PAGES;i++) {
            assertEquals(i+1,network.sent.size());KOMECompanyDestinationPanel.Rect row=selectorLayout().row(0);
            for(int wheel=0;wheel<20;wheel++)assertEquals(0,KOMEConquestMapOverlay.selectorWheelDelta(map,row.x+1,row.y+1,-120));
            assertEquals(i+1,network.sent.size());request=(KOMEPacketCompanyDestinationRequest)network.sent.get(i);
            publish(proxy,KOMECompanyDestinationSelectorTest.page(request,KOMECompanyDestinationOptions.Result.OK,
                Collections.singletonList(mapRow("B"+i,kome.common.command.KOMECompanyMovementService.Status.BLOCKED)),true,0));
        }
        assertEquals(4,network.sent.size());assertEquals(KOMECompanyDestinationSelector.Phase.READY,selector().phase());
        assertTrue(selector().message(0).contains("keep looking"));assertFalse(selector().canConfirm());assertTrue(preview().points.isEmpty());
        for(int render=0;render<100;render++)selectorLayout();assertEquals(4,network.sent.size());
    }

    @Test public void joinFirstClickSendsNothingSecondClickOnceAndRefreshBackCloseEscapeDisarm() throws Exception {
        for(int action=0;action<5;action++)try(DirectClient client=new DirectClient()) {
            client.mc.thePlayer=null;KOMEClientProxy proxy=connectSelector();openSelector();
            deliverSelectorRow(proxy,kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT);
            KOMECompanyDestinationPanel.Rect row=selectorLayout().row(0);click(row.x+1,row.y+1,0);
            KOMECompanyDestinationSelector original=selector();KOMECompanyDestinationPanel.Rect join=selectorLayout().controls.get(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN);
            clickControl(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN);assertEquals(1,network.sent.size());assertTrue(original.joinArmed(System.nanoTime()/1000000L));
            if(action==0) {
                for(int rapid=0;rapid<4;rapid++)assertTrue(click(join.x+1,join.y+1,0));
                assertEquals(2,network.sent.size());assertTrue(network.sent.get(1) instanceof KOMEPacketCompanyMovementRequest);
                for(IMessage packet:network.sent)assertFalse(packet instanceof KOMEPacketJoinBattleSelectionRequest);
            }else if(action==1)clickControl(KOMECompanyDestinationPanel.Control.RETRY);
            else if(action==2)clickControl(KOMECompanyDestinationPanel.Control.BACK);
            else if(action==3)clickControl(KOMECompanyDestinationPanel.Control.CLOSE);
            else assertTrue(KOMEConquestMapOverlay.onMapKeyTyped(map,org.lwjgl.input.Keyboard.KEY_ESCAPE));
            assertFalse(original.joinArmed(System.nanoTime()/1000000L));network.sent.clear();
        }
    }
    @Test public void destinationCueFollowsSelectorContextWithoutOwningMapInput() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();
        assertEquals("T001",KOMEConquestMapOverlay.selectorDestinationTile(map));
        assertEquals("",KOMEConquestMapOverlay.selectorDestinationTile(KOMEAccessFixture.allocate(LOTRGuiMap.class)));
        assertFalse(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,2,2,0));
        assertEquals(120,KOMEConquestMapOverlay.selectorWheelDelta(map,2,2,120));
        selector().open("T050");assertEquals("T050",KOMEConquestMapOverlay.selectorDestinationTile(map));
        selector().close();assertEquals("",KOMEConquestMapOverlay.selectorDestinationTile(map));
        selector().open("T050");proxy.destinationOptions().start(new Object());
        assertEquals("",KOMEConquestMapOverlay.selectorDestinationTile(map));
    }
    @Test public void displayOnlyScrollbarCapturesClicksAndDragAndNeverPansOrZoomsMap() throws Exception {
        KOMEClientProxy proxy=connectSelector();openSelector();deliverSelectorRow(proxy);
        KOMECompanyDestinationPanel.Rect track=selectorLayout().scrollTrack;float zoom=field(LOTRGuiMap.class,"zoomScale").getFloat(map);
        assertTrue(click(track.x+1,track.y+1,0));assertNull(selector().selected());assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
        assertEquals(0,KOMEConquestMapOverlay.selectorWheelDelta(map,track.x+1,track.y+1,-120));
        assertEquals(zoom,field(LOTRGuiMap.class,"zoomScale").getFloat(map),0);assertEquals(1,network.sent.size());
    }
    @Test public void everyVisibleRowUsesCanonicalIdentityBeforeAndAfterCachedPageRevalidationForEveryEntryPath() throws Exception {
        for(int entry=0;entry<3;entry++)try(BattlePresentationClient client=new BattlePresentationClient()) {
            new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));client.owner("mordor");network.sent.clear();KOMEClientProxy proxy=connectSelector();
            if(entry==0)openSelector();
            else {
                click(400,300,1);clickRoot(1);
                KOMEPacketJoinBattleViewRequest context=(KOMEPacketJoinBattleViewRequest)network.sent.get(0);
                proxy.acceptJoinBattleView(battleView(context,entry==1?null:KOMEConflictRecord.State.ORDINARY),selectorConnection);
                click(400,240,0);
            }
            assertEquals("T001",KOMEConquestMapOverlay.selectorDestinationTile(map));
            deliverSelectionPage(proxy);
            for(int visible=0;visible<4;visible++)clickAndAssertSelection(proxy,visible);
            for(int i=0;i<4;i++)clickControl(KOMECompanyDestinationPanel.Control.ROWS_DOWN);
            assertEquals(4,selector().rowOffset());
            for(int visible=0;visible<4;visible++)clickAndAssertSelection(proxy,visible);
            clickControl(KOMECompanyDestinationPanel.Control.ROWS_DOWN);deliverSelectionPage(proxy);
            assertEquals("C06",selector().visibleRow(0).companyId);
            assertEquals("C09",selector().visibleRow(3).companyId);
            for(int visible=0;visible<4;visible++)clickAndAssertSelection(proxy,visible);
            clickControl(KOMECompanyDestinationPanel.Control.ROWS_DOWN);
            for(int visible=0;visible<4;visible++)clickAndAssertSelection(proxy,visible);
            clickControl(KOMECompanyDestinationPanel.Control.CLEAR);
            assertEquals("",selector().selectedCompanyId());
            clickAndAssertSelection(proxy,0);selector().open("T050");
            assertEquals("",selector().selectedCompanyId());
        }
    }
    @Test public void singletonCompanyMarkerRenderingHasNoSelectionOrHoverEmphasisPath() throws Exception {
        String source=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/main/java/kome/client/KOMEConquestMapOverlay.java")),java.nio.charset.StandardCharsets.UTF_8);
        int start=source.indexOf("private static List<String> drawLiveUnitMarkers");
        String renderer=source.substring(start,source.indexOf("},(group,presentation)->",start));
        assertTrue(renderer.contains("drawTexturedMarker("));assertTrue(renderer.contains("drawTroopCount("));
        String clusterRenderer=source.substring(source.indexOf("},(group,presentation)->",start),
            source.indexOf("interface CompanyMarkerSink",start));
        assertTrue(clusterRenderer.contains("drawCompanyCluster("));
        assertFalse(clusterRenderer.contains("drawTroopCount("));
        assertFalse(renderer.contains("companySelector"));assertFalse(renderer.contains("selectedCompanyId"));
        assertFalse(renderer.contains("hover"));assertFalse(renderer.contains("emphasized"));
        assertFalse(renderer.contains("Gui.drawRect"));assertFalse(renderer.contains("ROUTE_GOLD"));assertFalse(renderer.contains("ROUTE_DARK"));
        String call="drawLiveUnitMarkers(map, event.mouseX, event.mouseY)";
        assertEquals(source.indexOf(call),source.lastIndexOf(call));assertTrue(source.indexOf(call)>=0);
        for(java.lang.reflect.Method method:KOMEConquestMapOverlay.class.getDeclaredMethods()) {
            assertFalse(Arrays.asList("companyMarkerEmphasized","companyMarkerInPass","updateSelectorHover").contains(method.getName()));
        }
        for(java.lang.reflect.Method method:KOMECompanyDestinationSelector.class.getDeclaredMethods()) {
            assertFalse(Arrays.asList("hover","emphasizedCompany","markerEmphasized").contains(method.getName()));
        }
        for(Field field:KOMECompanyDestinationSelector.class.getDeclaredFields())assertNotEquals("hoveredCompany",field.getName());
        assertEquals(String.class,KOMECompanyDestinationSelector.class.getDeclaredField("selectedCompanyId").getType());
        // Panel hover styling remains local to the panel and selection remains ID-based.
        String panel=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
            "src/main/java/kome/client/KOMECompanyDestinationPanel.java")),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(panel.contains("model.isSelected(row.companyId)"));assertTrue(panel.contains("selected||hover"));
        assertTrue(source.contains("drawSelectorDestination(map)"));
    }    @Test public void rootMultipleCompaniesOpenFreshChooserOnFirstClickAndSubmitOnce() throws Exception {
        for(boolean success:new boolean[]{true,false})try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;pan(2292,58,1);network.autoRootDiscovery=false;network.sent.clear();KOMEClientProxy proxy=connectSelector();
            assertTrue(click(400,300,1));assertEquals(1,network.sent.size());assertEquals(5,menu.segments().size());
            KOMEPacketJoinBattleViewRequest initial=(KOMEPacketJoinBattleViewRequest)network.sent.get(0);
            proxy.acceptJoinBattleView(KOMERootJoinBattleTest.allowed(initial),selectorConnection);
            assertEquals(6,menu.segments().size());assertTrue(clickRoot(0));
            assertEquals(2,network.sent.size());assertSame(map,client.mc.currentScreen);
            assertEquals(0,client.mc.screenChanges);
            assertEquals(6,menu.segments().size());assertFalse(menu.enabled(KOMEMapRadialMenu.Action.JOIN_BATTLE));
            assertEquals("Loading Join Battle...",menu.label(menu.segments().get(0).action));
            KOMEPacketJoinBattleViewRequest fresh=(KOMEPacketJoinBattleViewRequest)network.sent.get(1);
            assertNotEquals(initial.correlation,fresh.correlation);
            for(int rapid=0;rapid<10;rapid++)click(400,243,0);assertEquals(2,network.sent.size());
            KOMEPacketJoinBattleViewResponse current=KOMERootJoinBattleTest.allowed(fresh);
            current.companies.clear();KOMERootJoinBattleTest.addRow(current,"CURRENT_COMPANY",true);KOMERootJoinBattleTest.addRow(current,"OTHER_COMPANY",true);
            proxy.acceptJoinBattleView(current,selectorConnection);
            assertTrue(client.mc.currentScreen instanceof kome.client.gui.KOMEGuiJoinBattle);
            kome.client.gui.KOMEGuiJoinBattle gui=(kome.client.gui.KOMEGuiJoinBattle)client.mc.currentScreen;
            java.lang.reflect.Field modelField=field(gui.getClass(),"model");
            kome.client.gui.KOMEJoinBattleViewModel model=(kome.client.gui.KOMEJoinBattleViewModel)modelField.get(gui);
            assertEquals("",model.selectedCompanyId());assertEquals("CURRENT_COMPANY",model.view().companyRows().get(0).companyId);
            assertFalse(model.canSubmit());assertEquals(2,network.sent.size());
            invokeChooserButton(gui,100);assertEquals("CURRENT_COMPANY",model.selectedCompanyId());
            invokeChooserButton(gui,1);invokeChooserButton(gui,1);assertEquals(3,network.sent.size());
            KOMEPacketJoinBattleSelectionRequest selection=(KOMEPacketJoinBattleSelectionRequest)network.sent.get(2);
            assertEquals("CURRENT_COMPANY",selection.companyId);assertEquals(current.conflictId,selection.conflictId);
            assertEquals(current.conflictRevision,selection.conflictRevision);assertEquals(current.actionToken,selection.actionToken);
            for(IMessage packet:network.sent)assertFalse(packet instanceof KOMEPacketCompanyMovementRequest);
            KOMEPacketJoinBattleSelectionResult result=directResult(selection,current,success);
            if(!success){result.reason=KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE;result.message="No safe standing position is available.";}
            client.mc.thePlayer=client.player;
            // A retired map handler must not intercept the visible chooser's request.
            field(KOMEConquestMapOverlay.class,"directJoinScreen").set(null,new kome.client.gui.KOMEGuiJoinBattle(current));
            result=kome.common.network.KOMEPopulationWire.copyForPublication(result,KOMEPacketJoinBattleSelectionResult::new);
            proxy.acceptJoinBattleSelectionResult(result,selectorConnection);proxy.acceptJoinBattleSelectionResult(result,selectorConnection);gui.acceptSelection(result);
            if(success){assertNull(client.mc.currentScreen);assertEquals(2,client.mc.screenChanges);assertEquals(1,client.player.messages.size());}
            else {assertSame(gui,client.mc.currentScreen);assertEquals(result.message,gui.status());assertTrue(client.player.messages.isEmpty());}
            assertEquals(3,network.sent.size());
        }
    }
    @Test public void soleFreshCompanyArmsThenRevalidatesAndDirectlyJoinsWithoutOpeningChooser() throws Exception {
        for(int outcome=0;outcome<4;outcome++)try(DirectClient client=new DirectClient()) {
            client.mc.thePlayer=null;pan(2292,58,1);network.autoRootDiscovery=false;network.sent.clear();
            KOMEClientProxy proxy=connectSelector();click(400,300,1);
            // Old radial evidence deliberately says multiple; the activation read decides.
            proxy.acceptJoinBattleView(KOMERootJoinBattleTest.allowed((KOMEPacketJoinBattleViewRequest)network.sent.get(0)),selectorConnection);
            clickRoot(0);assertEquals(2,network.sent.size());assertSame(map,client.mc.currentScreen);
            KOMEPacketJoinBattleViewRequest first=(KOMEPacketJoinBattleViewRequest)network.sent.get(1);
            proxy.acceptJoinBattleView(KOMERootJoinBattleTest.one(first),selectorConnection);
            assertEquals("Confirm",menu.label(KOMEMapRadialMenu.Action.JOIN_BATTLE));
            assertEquals(2,network.sent.size());assertEquals(0,client.mc.screenChanges);
            clickRoot(0);assertEquals(3,network.sent.size());
            KOMEPacketJoinBattleViewRequest second=(KOMEPacketJoinBattleViewRequest)network.sent.get(2);
            assertNotEquals(first.correlation,second.correlation);
            KOMEPacketJoinBattleViewResponse latest=KOMERootJoinBattleTest.allowed(second);
            if(outcome!=1)latest.companies.clear();
            if(outcome!=1&&outcome!=2)KOMERootJoinBattleTest.addRow(latest,"FRESH_SOLE",true);
            if(outcome==2){latest.reason=KOMEJoinBattleService.Reason.WITHDRAWN;latest.reasonText="You have withdrawn.";}
            proxy.acceptJoinBattleView(latest,selectorConnection);
            if(outcome==1) {
                assertTrue(client.mc.currentScreen instanceof kome.client.gui.KOMEGuiJoinBattle);assertEquals(3,network.sent.size());
                kome.client.gui.KOMEJoinBattleViewModel model=(kome.client.gui.KOMEJoinBattleViewModel)field(kome.client.gui.KOMEGuiJoinBattle.class,"model").get(client.mc.currentScreen);
                assertEquals("",model.selectedCompanyId());continue;
            }
            if(outcome==2){assertSame(map,client.mc.currentScreen);assertEquals(5,menu.segments().size());assertEquals(3,network.sent.size());continue;}
            assertSame(map,client.mc.currentScreen);assertEquals(0,client.mc.screenChanges);assertEquals(4,network.sent.size());
            KOMEPacketJoinBattleSelectionRequest join=(KOMEPacketJoinBattleSelectionRequest)network.sent.get(3);
            assertEquals("FRESH_SOLE",join.companyId);assertEquals(latest.actionToken,join.actionToken);
            assertEquals(latest.conflictId,join.conflictId);assertEquals(latest.conflictRevision,join.conflictRevision);
            for(int rapid=0;rapid<8;rapid++)click(400,243,0);assertEquals(4,network.sent.size());
            KOMEPacketJoinBattleSelectionResult result=directResult(join,latest,outcome==0);
            if(outcome==3){result.reason=KOMEJoinBattleService.Reason.SAFE_POSITION_UNAVAILABLE;result.message="No safe standing position.";}
            client.mc.thePlayer=client.player;proxy.acceptJoinBattleSelectionResult(result,selectorConnection);proxy.acceptJoinBattleSelectionResult(result,selectorConnection);
            if(outcome==0){assertNull(client.mc.currentScreen);assertEquals(1,client.mc.screenChanges);assertEquals(1,client.player.messages.size());}
            else {assertTrue(client.mc.currentScreen instanceof kome.client.gui.KOMEGuiJoinBattle);assertEquals(result.message,((kome.client.gui.KOMEGuiJoinBattle)client.mc.currentScreen).status());}
            assertEquals(4,network.sent.size());
        }
    }
    @Test public void soleRootDirectResultCannotCloseWrongScreenSessionOrDuplicateAction() throws Exception {
        for(int invalidation=0;invalidation<5;invalidation++)try(DirectClient client=new DirectClient()) {
            client.mc.thePlayer=null;pan(2292,58,1);network.autoRootDiscovery=false;network.sent.clear();
            KOMEClientProxy proxy=connectSelector();click(400,300,1);
            proxy.acceptJoinBattleView(KOMERootJoinBattleTest.one((KOMEPacketJoinBattleViewRequest)network.sent.get(0)),selectorConnection);
            clickRoot(0);proxy.acceptJoinBattleView(KOMERootJoinBattleTest.one((KOMEPacketJoinBattleViewRequest)network.sent.get(1)),selectorConnection);
            clickRoot(0);KOMEPacketJoinBattleViewResponse view=KOMERootJoinBattleTest.one((KOMEPacketJoinBattleViewRequest)network.sent.get(2));
            proxy.acceptJoinBattleView(view,selectorConnection);
            KOMEPacketJoinBattleSelectionResult result=directResult((KOMEPacketJoinBattleSelectionRequest)network.sent.get(3),view,true);
            GuiScreen expected=map;
            if(invalidation==0)result.correlation=UUID.randomUUID();
            if(invalidation==1){expected=new GuiScreen();client.mc.displayGuiScreen(expected);}
            if(invalidation==2)proxy.destinationOptions().start(new Object());
            if(invalidation==3)proxy.destinationOptions().start(selectorConnection);
            if(invalidation==4)result.current.currentDeploymentConflict="WRONG";
            int changes=client.mc.screenChanges;client.mc.thePlayer=client.player;
            proxy.acceptJoinBattleSelectionResult(result,selectorConnection);
            assertSame(expected,client.mc.currentScreen);assertEquals(changes,client.mc.screenChanges);
            assertEquals(4,network.sent.size());assertTrue(client.player.messages.isEmpty());
        }
    }
    @Test public void mapOwningMovementSelectorAlreadyInheritsNonPausingLOTRScreen() {
        assertFalse(map.doesGuiPauseGame());
    }
    @Test public void rootChooserRejectsWrongActionReplacementAndSessionResults() throws Exception {
        for(int invalidation=0;invalidation<4;invalidation++)try(DirectClient client=new DirectClient()) {
            client.mc.thePlayer=null;pan(2292,58,1);network.autoRootDiscovery=false;network.sent.clear();
            KOMEClientProxy proxy=connectSelector();click(400,300,1);
            proxy.acceptJoinBattleView(KOMERootJoinBattleTest.allowed((KOMEPacketJoinBattleViewRequest)network.sent.get(0)),selectorConnection);
            clickRoot(0);clickRoot(0);
            KOMEPacketJoinBattleViewResponse view=KOMERootJoinBattleTest.allowed((KOMEPacketJoinBattleViewRequest)network.sent.get(1));
            proxy.acceptJoinBattleView(view,selectorConnection);
            kome.client.gui.KOMEGuiJoinBattle gui=(kome.client.gui.KOMEGuiJoinBattle)client.mc.currentScreen;
            invokeChooserButton(gui,100);invokeChooserButton(gui,1);
            KOMEPacketJoinBattleSelectionResult result=directResult((KOMEPacketJoinBattleSelectionRequest)network.sent.get(2),view,true);
            net.minecraft.client.gui.GuiScreen owner=gui;
            if(invalidation==0)result.correlation=UUID.randomUUID();
            if(invalidation==1){owner=new kome.client.gui.KOMEGuiJoinBattle(view);client.mc.displayGuiScreen(owner);}
            if(invalidation==2)proxy.destinationOptions().start(new Object());
            if(invalidation==3)proxy.destinationOptions().start(selectorConnection);
            int changes=client.mc.screenChanges;client.mc.thePlayer=client.player;
            proxy.acceptJoinBattleSelectionResult(result,selectorConnection);
            assertSame(owner,client.mc.currentScreen);assertEquals(changes,client.mc.screenChanges);
            assertTrue(client.player.messages.isEmpty());assertEquals(3,network.sent.size());
        }
    }    @Test public void rootFreshEligibilityLossNeverOpensChooserAndDynamicRefreshRemovesGhostJoin() throws Exception {
        try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;network.autoRootDiscovery=false;KOMEClientProxy proxy=connectSelector();
            click(400,300,1);KOMEPacketJoinBattleViewRequest first=(KOMEPacketJoinBattleViewRequest)network.sent.get(0);
            proxy.acceptJoinBattleView(KOMERootJoinBattleTest.allowed(first),selectorConnection);
            assertEquals(0,menu.hit(0,-57));clickRoot(0);clickRoot(0);
            KOMEPacketJoinBattleViewRequest fresh=(KOMEPacketJoinBattleViewRequest)network.sent.get(1);
            KOMEPacketJoinBattleViewResponse denied=KOMERootJoinBattleTest.allowed(fresh);denied.reason=KOMEJoinBattleService.Reason.WITHDRAWN;denied.reasonText="You have withdrawn.";
            proxy.acceptJoinBattleView(denied,selectorConnection);
            assertSame(map,client.mc.currentScreen);assertEquals(0,client.mc.screenChanges);assertEquals(5,menu.segments().size());
            assertEquals(-1,menu.hit(0,-57));assertFalse(KOMEConquestMapOverlay.radialOwnsPointer(map,400,243));assertTrue(menu.status().contains("withdrawn"));
            KOMEConquestMapOverlay.refreshRootJoinBattle();fresh=(KOMEPacketJoinBattleViewRequest)network.sent.get(2);
            proxy.acceptJoinBattleView(KOMERootJoinBattleTest.allowed(fresh),selectorConnection);assertEquals(6,menu.segments().size());
            KOMEConquestMapOverlay.refreshRootJoinBattle();fresh=(KOMEPacketJoinBattleViewRequest)network.sent.get(3);
            denied=KOMERootJoinBattleTest.allowed(fresh);denied.conflictState=KOMEConflictRecord.State.ENDED;
            proxy.acceptJoinBattleView(denied,selectorConnection);assertEquals(5,menu.segments().size());assertEquals(-1,menu.hit(0,-57));
            for(int frame=0;frame<100;frame++)KOMEConquestMapOverlay.radialOwnsPointer(map,400,243);
            assertEquals(4,network.sent.size());assertEquals(-1,menu.hit(0,0));
        }
    }
    @Test public void rootArmClearsOnEscapeCloseTileOtherActionStaleSessionAndScreenReplacement() throws Exception {
        for(int clear=0;clear<7;clear++)try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;pan(2292,58,1);network.autoRootDiscovery=false;network.sent.clear();KOMEClientProxy proxy=connectSelector();
            click(400,300,1);KOMEPacketJoinBattleViewRequest first=(KOMEPacketJoinBattleViewRequest)network.sent.get(0);
            proxy.acceptJoinBattleView(KOMERootJoinBattleTest.allowed(first),selectorConnection);clickRoot(0);
            KOMERootJoinBattle root=(KOMERootJoinBattle)field(KOMEConquestMapOverlay.class,"rootJoin").get(null);
            proxy.acceptJoinBattleView(KOMERootJoinBattleTest.one((KOMEPacketJoinBattleViewRequest)network.sent.get(1)),selectorConnection);
            assertTrue(root.armed(System.nanoTime()/1000000L));
            if(clear==0)KOMEConquestMapOverlay.onMapKeyTyped(map,org.lwjgl.input.Keyboard.KEY_ESCAPE);
            if(clear==1)clickRoot(3);
            if(clear==2){centers().put("T050",new int[]{2291,58});assertTrue(KOMEConquestMapOverlay.focusRadialForTile(map,"T050"));}
            if(clear==3)clickRoot(5);
            if(clear==4)proxy.acceptJoinBattleView(KOMERootJoinBattleTest.allowed(first),selectorConnection);
            if(clear==5){proxy.destinationOptions().start(new Object());KOMEConquestMapOverlay.radialOwnsPointer(map,400,243);}
            if(clear==6)new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));
            assertFalse(root.armed(System.nanoTime()/1000000L));
            for(IMessage packet:network.sent)assertTrue(packet instanceof KOMEPacketJoinBattleViewRequest);
            assertFalse(client.mc.currentScreen instanceof kome.client.gui.KOMEGuiJoinBattle);
            // The first stale root response can never reopen a chooser after these context changes.
            proxy.acceptJoinBattleView(KOMERootJoinBattleTest.allowed(first),selectorConnection);
            assertFalse(client.mc.currentScreen instanceof kome.client.gui.KOMEGuiJoinBattle);
        }
    }
    @Test public void knownTileFocusHookKeepsZoomAndLateRootReplyCannotCancelMovementDirectJoin() throws Exception {
        try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;KOMEClientProxy proxy=connectSelector();
            pan(100,200,2);assertFalse(KOMEConquestMapOverlay.focusRadialForTile(map,"unknown"));
            assertTrue(KOMEConquestMapOverlay.focusRadialForTile(map,"T001"));
            double[] anchor=KOMEConquestMapOverlay.radialAnchorMapPosition("T001");
            assertEquals(anchor[0],field(LOTRGuiMap.class,"posX").getFloat(map),0);assertEquals(anchor[1],field(LOTRGuiMap.class,"posY").getFloat(map),0);
            assertEquals(2,field(LOTRGuiMap.class,"zoomScale").getFloat(map),0);
            new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));pan(2292,58,1);network.sent.clear();network.rootDiscovery.clear();
            prepareDirect(proxy,kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT);
            assertEquals(1,network.rootDiscovery.size());
            KOMEPacketJoinBattleViewRequest old=(KOMEPacketJoinBattleViewRequest)network.rootDiscovery.get(0);
            proxy.acceptJoinBattleView(KOMERootJoinBattleTest.allowed(old),selectorConnection);
            assertEquals(KOMECompanyDestinationSelector.Phase.JOINING,selector().phase());assertEquals(3,network.sent.size());
            proxy.acceptJoinBattleView(directView("company",true,1),selectorConnection);
            assertEquals(4,network.sent.size());assertTrue(network.sent.get(3) instanceof KOMEPacketJoinBattleSelectionRequest);
        }
    }
    private KOMECompanyOriginWorkflow origin()throws Exception{return (KOMECompanyOriginWorkflow)field(KOMEConquestMapOverlay.class,"originWorkflow").get(null);}
    private void openOrigin(KOMEClientProxy proxy)throws Exception{
        click(400,300,1);clickRoot(0);assertEquals("Move To...",menu.segments().get(1).action.label);clickRoot(1);
        assertTrue(network.sent.isEmpty());assertNull(selector());assertEquals(KOMECompanyOriginWorkflow.Stage.PICK,origin().stage());
        assertEquals("T001",origin().source());assertEquals("",origin().selectedCompanyId());assertEquals("T001",KOMEConquestMapOverlay.originSourceTile(map));
        centers().put("T050",new int[]{2296,58});
        double x=KOMETileTestResources.x()/(double)LOTRGenLayerWorld.scale+LOTRGenLayerWorld.originX;
        double y=KOMETileTestResources.z()/(double)LOTRGenLayerWorld.scale+LOTRGenLayerWorld.originZ;
        centers().put("T100",new int[]{(int)x,(int)y});
    }
    private KOMECompanyDestinationOptions originPage(KOMEPacketCompanyOriginRequest request,KOMECompanyDestinationOptions.Row row){
        return new KOMECompanyDestinationOptions(request.session,request.sequence,request.destination,KOMECompanyDestinationOptions.Result.OK,
            Collections.singletonList(row),KOMECompanyDestinationSelectorTest.TOKEN,row.companyId,false);
    }
    private void selectOriginRow()throws Exception{
        KOMECompanyDestinationPanel.Rect r=selectorLayout().row(0);assertTrue(click(r.x+2,r.y+2,0));
        assertEquals(KOMECompanyOriginWorkflow.Stage.PREVIEW,origin().stage());assertEquals("C03",origin().selectedCompanyId());
    }
    @Test public void originPickUsesRightClickTileAtCurrentPanZoomLeftDragNeverChoosesAndBackIsStepwise()throws Exception{
        try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;KOMEClientProxy proxy=connectSelector();openOrigin(proxy);
            assertFalse(click(400,300,0));assertTrue(KOMEConquestMapOverlay.mapDragButtonDown(map,true));assertTrue(network.sent.isEmpty());
            assertEquals(120,KOMEConquestMapOverlay.selectorWheelDelta(map,400,300,120));assertFalse(KOMEConquestMapOverlay.radialOwnsPointer(map,400,243));
            int[] destination=centers().get("T100");pan(destination[0],destination[1],2);assertTrue(click(400,300,1));
            KOMEPacketCompanyOriginRequest query=(KOMEPacketCompanyOriginRequest)network.sent.get(0);
            assertFalse(query.targeted);assertEquals("T001",query.source);assertEquals("T100",query.destination);assertEquals("",query.company);
            assertEquals("T001",menu.tileId());assertEquals("T001",origin().source());assertEquals("T100",origin().destination());assertEquals(KOMECompanyOriginWorkflow.Stage.SOURCE,origin().stage());
            KOMECompanyDestinationOptions.Row row=KOMECompanyDestinationSelectorTest.row("C03",kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW);
            proxy.acceptCompanyDestinationOptions(originPage(query,row),selectorConnection);
            assertNull(selector().selected());assertFalse(selector().canConfirm());assertTrue(selector().sourceScoped());
            assertEquals("T001",KOMEConquestMapOverlay.originSourceTile(map));assertEquals("T100",KOMEConquestMapOverlay.selectorDestinationTile(map));
            selectOriginRow();assertEquals(row.route,selector().selectedRoute());assertFalse(preview().points.isEmpty());assertTrue(selector().canConfirm());
            assertEquals(1,network.sent.size());clickControl(KOMECompanyDestinationPanel.Control.BACK);
            assertEquals(KOMECompanyOriginWorkflow.Stage.SOURCE,origin().stage());assertEquals("T100",origin().destination());assertEquals("",origin().selectedCompanyId());assertTrue(preview().points.isEmpty());
            KOMEPacketCompanyOriginRequest fresh=(KOMEPacketCompanyOriginRequest)network.sent.get(1);assertNotEquals(query.session,fresh.session);
            proxy.acceptCompanyDestinationOptions(originPage(query,row),selectorConnection);assertEquals(KOMECompanyDestinationSelector.Phase.LOADING,selector().phase());
            proxy.acceptCompanyDestinationOptions(originPage(fresh,row),selectorConnection);
            clickControl(KOMECompanyDestinationPanel.Control.BACK);assertEquals(KOMECompanyOriginWorkflow.Stage.PICK,origin().stage());assertNull(selector());assertEquals("",origin().destination());
            assertEquals("T001",origin().source());assertEquals("",KOMEConquestMapOverlay.selectorDestinationTile(map));
            assertTrue(click(12,50,0));assertNull(origin());assertEquals(KOMEMapRadialMenu.State.MOVEMENT,menu.state());assertEquals("T001",menu.tileId());
            // Restore source anchoring before the radial Back gesture.
            pan(2292,58,1);clickRoot(3);assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());
            for(IMessage packet:network.sent)assertFalse(packet instanceof KOMEPacketCompanyMovementRequest);
        }
    }
    @Test public void originNewDestinationRejectsOldEvidenceAndEmptySourceNeverFallsBackToGlobalCatalogue()throws Exception{
        try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;KOMEClientProxy proxy=connectSelector();openOrigin(proxy);KOMEConquestMapOverlay.chooseOriginDestination(map,"T100");
            KOMEPacketCompanyOriginRequest old=(KOMEPacketCompanyOriginRequest)network.sent.get(0);
            clickControl(KOMECompanyDestinationPanel.Control.BACK);KOMEConquestMapOverlay.chooseOriginDestination(map,"T200");
            KOMEPacketCompanyOriginRequest fresh=(KOMEPacketCompanyOriginRequest)network.sent.get(1);assertEquals("T001",fresh.source);assertEquals("T200",fresh.destination);
            proxy.acceptCompanyDestinationOptions(originPage(old,KOMECompanyDestinationSelectorTest.row("C03",kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW)),selectorConnection);
            assertTrue(selector().rows().isEmpty());assertNull(selector().selected());assertFalse(selector().canConfirm());assertTrue(preview().points.isEmpty());
            proxy.acceptCompanyDestinationOptions(new KOMECompanyDestinationOptions(fresh.session,fresh.sequence,fresh.destination,KOMECompanyDestinationOptions.Result.OK,Collections.emptyList(),KOMECompanyDestinationSelectorTest.TOKEN,"",false),selectorConnection);
            assertEquals(KOMECompanyDestinationSelector.Phase.READY,selector().phase());assertTrue(selector().message(0).contains("source T001"));assertFalse(selector().canConfirm());
            for(IMessage packet:network.sent)assertTrue(packet instanceof KOMEPacketCompanyOriginRequest);
        }
    }
    @Test public void originSourceChooserRequiresClickEvenWithOneCompanyAndPreservesImmediateLaterAndBlockedRules()throws Exception{
        for(kome.common.command.KOMECompanyMovementService.Status status:kome.common.command.KOMECompanyMovementService.Status.values())try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;network.sent.clear();KOMEClientProxy proxy=connectSelector();openOrigin(proxy);KOMEConquestMapOverlay.chooseOriginDestination(map,"T100");
            KOMEPacketCompanyOriginRequest query=(KOMEPacketCompanyOriginRequest)network.sent.get(0);
            KOMECompanyDestinationOptions.Row row=KOMECompanyDestinationSelectorTest.row("C03",status);
            proxy.acceptCompanyDestinationOptions(originPage(query,row),selectorConnection);assertEquals(KOMECompanyOriginWorkflow.Stage.SOURCE,origin().stage());assertNull(selector().selected());assertFalse(selector().canConfirm());
            KOMECompanyDestinationPanel.Layout chooser=(KOMECompanyDestinationPanel.Layout)invokeSelectorLayout();
            assertFalse(chooser.controls.containsKey(KOMECompanyDestinationPanel.Control.CONFIRM));assertFalse(chooser.controls.containsKey(KOMECompanyDestinationPanel.Control.FILTER_READY));
            selectOriginRow();assertEquals(status!=kome.common.command.KOMECompanyMovementService.Status.BLOCKED,selector().canConfirm());assertFalse(selector().canConfirmAndJoin());
            if(status==kome.common.command.KOMECompanyMovementService.Status.BLOCKED){assertEquals(row.denialDetail,KOMECompanyDestinationSelector.reason(selector().selected()));clickControl(KOMECompanyDestinationPanel.Control.CLEAR);assertEquals("",origin().selectedCompanyId());assertEquals(KOMECompanyOriginWorkflow.Stage.SOURCE,origin().stage());}
            else {clickControl(KOMECompanyDestinationPanel.Control.CONFIRM);assertEquals(2,network.sent.size());assertTrue(network.sent.get(1) instanceof KOMEPacketCompanyMovementRequest);assertEquals("C03",((KOMEPacketCompanyMovementRequest)network.sent.get(1)).companyId);}
        }
    }
    private Object invokeSelectorLayout()throws Exception{
        java.lang.reflect.Method method=KOMEConquestMapOverlay.class.getDeclaredMethod("selectorLayout",LOTRGuiMap.class);method.setAccessible(true);return method.invoke(null,map);
    }
    @Test public void originHostileCreateAndJoinReuseTwoClickMovementAndFreshDirectJoin()throws Exception{
        for(kome.common.command.KOMECompanyMovementService.Consequence consequence:new kome.common.command.KOMECompanyMovementService.Consequence[]{
                kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT,
                kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_JOIN_CONFLICT})try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;network.sent.clear();KOMEClientProxy proxy=connectSelector();openOrigin(proxy);
            KOMEConquestMapOverlay.chooseOriginDestination(map,"T100");
            KOMEPacketCompanyOriginRequest target=(KOMEPacketCompanyOriginRequest)network.sent.get(0);
            KOMECompanyDestinationOptions.Row row=KOMECompanyDestinationSelectorTest.row("C03",
                kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW,KOMECompanyDestinationOptions.Entitlement.FOOT_OR_MIXED,
                KOMECompanyDestinationOptions.EvaluationState.COMPLETE,consequence);
            proxy.acceptCompanyDestinationOptions(new KOMECompanyDestinationOptions(target.session,target.sequence,target.destination,
                KOMECompanyDestinationOptions.Result.OK,Arrays.asList(row),"","C03",false),selectorConnection);
            selectOriginRow();assertEquals("C03",selector().selectedCompanyId());assertTrue(selector().canConfirmAndJoin());
            KOMECompanyDestinationPanel.Rect joinBox=selectorLayout().controls.get(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN);
            clickControl(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN);assertEquals(1,network.sent.size());
            assertTrue(selector().joinArmed(System.nanoTime()/1000000L));
            clickControl(KOMECompanyDestinationPanel.Control.CONFIRM_JOIN);assertEquals(2,network.sent.size());
            KOMEPacketCompanyMovementRequest move=(KOMEPacketCompanyMovementRequest)network.sent.get(1);
            assertEquals("C03",move.companyId);assertEquals("T100",move.destination);
            assertTrue(click(joinBox.x+2,joinBox.y+2,0));assertEquals(2,network.sent.size());
            KOMECompanyMovementResult arrival=new KOMECompanyMovementResult(move.session,move.sequence,move.previewSequence,move.companyId,move.destination,
                KOMECompanyMovementResult.Outcome.ARRIVED,"M3",KOMEArmyMovementOrder.CONFLICT_HELD,"T100",2,2,"","");
            proxy.acceptCompanyMovementResult(arrival,selectorConnection);
            assertEquals(3,network.sent.size());KOMEPacketJoinBattleViewRequest fresh=(KOMEPacketJoinBattleViewRequest)network.sent.get(2);
            assertEquals("T100",fresh.tileId);
            KOMEPacketJoinBattleViewResponse view=KOMERootJoinBattleTest.allowed(fresh);
            if(consequence==kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_JOIN_CONFLICT){
                view.conflictId=row.destinationConflictId;view.conflictRevision=row.destinationConflictRevision;
            }
            view.companies.clear();KOMERootJoinBattleTest.addRow(view,"C03",true);
            proxy.acceptJoinBattleView(view,selectorConnection);assertEquals(4,network.sent.size());assertSame(map,client.mc.currentScreen);
            KOMEPacketJoinBattleSelectionRequest join=(KOMEPacketJoinBattleSelectionRequest)network.sent.get(3);assertEquals("C03",join.companyId);
            client.mc.thePlayer=client.player;proxy.acceptJoinBattleSelectionResult(directResult(join,view,true),selectorConnection);
            assertNull(client.mc.currentScreen);assertNull(origin());assertEquals(1,client.player.messages.size());assertEquals(4,network.sent.size());
        }
    }
    @Test public void originEscapeSessionCloseAndSourceReplacementRemoveGhostMode()throws Exception{
        for(int clear=0;clear<4;clear++)try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;network.sent.clear();KOMEClientProxy proxy=connectSelector();openOrigin(proxy);KOMEConquestMapOverlay.chooseOriginDestination(map,"T100");
            KOMEPacketCompanyOriginRequest old=(KOMEPacketCompanyOriginRequest)network.sent.get(0);
            if(clear==0)assertTrue(KOMEConquestMapOverlay.onMapKeyTyped(map,org.lwjgl.input.Keyboard.KEY_ESCAPE));
            if(clear==1)new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));
            if(clear==2){proxy.destinationOptions().start(new Object());KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,400,300,0);}
            if(clear==3){centers().put("T050",new int[]{2291,58});KOMEConquestMapOverlay.focusRadialForTile(map,"T050");}
            assertNull(origin());assertNull(selector());assertEquals("",KOMEConquestMapOverlay.originSourceTile(map));
            proxy.acceptCompanyDestinationOptions(originPage(old,KOMECompanyDestinationSelectorTest.row("C03",kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW)),selectorConnection);assertNull(origin());
            assertFalse(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,400,300,0));
        }
    }
    private void invokeChooserButton(kome.client.gui.KOMEGuiJoinBattle gui,int id) throws Exception {
        GuiButton button=null;for(Object value:(List)field(GuiScreen.class,"buttonList").get(gui))if(((GuiButton)value).id==id)button=(GuiButton)value;
        assertNotNull(button);java.lang.reflect.Method action=gui.getClass().getDeclaredMethod("actionPerformed",GuiButton.class);
        action.setAccessible(true);action.invoke(gui,button);
    }    private void deliverSelectionPage(KOMEClientProxy proxy) throws Exception {
        KOMEPacketCompanyDestinationRequest request=(KOMEPacketCompanyDestinationRequest)network.sent.get(network.sent.size()-1);
        int from=request.afterCompanyId.isEmpty()?1:Integer.parseInt(request.afterCompanyId.substring(1))+1;
        List<KOMECompanyDestinationOptions.Row> rows=new ArrayList<KOMECompanyDestinationOptions.Row>();
        for(int i=from;i<=Math.min(20,from+7);i++) {
            KOMECompanyDestinationOptions.Row r=mapRow(String.format(Locale.ROOT,"C%02d",i),kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW);
            rows.add(new KOMECompanyDestinationOptions.Row(r.evaluationState,r.companyId,"Same company name",r.currentTile,r.destinationTile,r.entitlement,
                r.tilesPerDay,r.remainingAllowance,r.distance,r.route,r.status,r.scheduleMode,r.canStartNow,r.canArriveNow,r.legalRoute,r.timingAvailable,
                r.futureBoundaries,r.nextBoundaryMillis,r.estimatedCompletionMillis,r.denialCode,r.denialDetail,r.heldConflictId,r.heldConflictRevision,
                r.consequence,r.destinationConflictId,r.destinationConflictRevision,r.legacyFingerprint,r.confirmationFingerprint));
        }
        publish(proxy,KOMECompanyDestinationSelectorTest.page(request,KOMECompanyDestinationOptions.Result.OK,rows,from+7<20,0));
    }
    private void clickAndAssertSelection(KOMEClientProxy proxy,int visible) throws Exception {
        KOMECompanyDestinationOptions.Row backing=selector().visibleRow(visible);String company=backing.companyId;
        if(selector().isSelected(company))selector().clearSelection();
        KOMECompanyDestinationPanel.Rect box=selectorLayout().row(visible);assertEquals(4,selectorLayout().capacity);
        assertTrue(click(box.x+1,box.y+1,0));
        assertEquals(company,selector().selectedCompanyId());assertTrue(selector().isSelected(company));


        // Selection and route presentation remain independent of the deferred map-marker feature.
        assertEquals(backing.route,selector().selectedRoute());
        assertEquals(company,selector().selectedCompanyId());
        if(selector().phase()==KOMECompanyDestinationSelector.Phase.LOADING) {
            assertFalse(selector().canConfirm());assertNull(selector().confirm());
            deliverSelectionPage(proxy);
            assertNotSame(backing,selector().selected());
            assertEquals(company,selector().selectedCompanyId());assertTrue(selector().isSelected(company));

        }
        assertTrue(selector().canConfirm());
    }    private static KOMECompanyDestinationOptions.Row mapRow(String id,kome.common.command.KOMECompanyMovementService.Status status) {
        boolean legal=status!=kome.common.command.KOMECompanyMovementService.Status.BLOCKED;
        boolean immediate=status==kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW;
        return new KOMECompanyDestinationOptions.Row(id,"Company "+id,"T002","T001",KOMECompanyDestinationOptions.Entitlement.MOUNTED,4,4,2,
            legal?Arrays.asList("T002","T050","T001"):Collections.<String>emptyList(),status,kome.common.command.KOMECompanyMovementService.ScheduleMode.DAILY,
            legal,immediate,legal,legal,immediate?0:1,10000,20000,legal?"":"CONFLICT_HELD",legal?"":"Held","",-1,
            kome.common.command.KOMECompanyMovementService.Consequence.NONE,"",-1,legal?KOMECompanyDestinationSelectorTest.HASH:"",legal?KOMECompanyDestinationSelectorTest.HASH:"");
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
    @Test public void moveAndBothBattleRallyEntriesUseCompactTwoClickFlowWithoutPanelOrPlayerDeployment() throws Exception {
        for(int entry=0;entry<3;entry++)try(DirectClient client=new DirectClient()) {
            client.mc.thePlayer=null;network.sent.clear();KOMEClientProxy proxy=connectSelector();
            click(400,300,1);clickRoot(entry==0?0:1);
            if(entry>0){menu.battleContext(entry==2,entry==1);network.sent.clear();}
            int index=entry==0?2:1;assertTrue(clickRoot(index));
            assertEquals(1,network.sent.size());KOMEPacketCompanyRallyRequest request=(KOMEPacketCompanyRallyRequest)network.sent.get(0);
            assertEquals(KOMEPacketCompanyRallyRequest.Action.OPEN,request.action);assertEquals("T001",request.destination);
            assertEquals("Loading Rally...",menu.label(KOMEMapRadialMenu.Action.RALLY_HERE));assertFalse(menu.enabled(KOMEMapRadialMenu.Action.RALLY_HERE));
            assertNull(selector());assertNull(field(KOMEConquestMapOverlay.class,"originWorkflow").get(null));assertTrue(preview().points.isEmpty());
            assertSame(map,client.mc.currentScreen);assertTrue(menu.isOpen());assertEquals("T001",KOMEConquestMapOverlay.selectorDestinationTile(map));
            KOMECompanyRallyClient model=rally();UUID token=UUID.randomUUID();
            assertTrue(KOMEConquestMapOverlay.radialOwnsPointer(map,400+(int)Math.round(menu.segments().get(index).labelX()),300+(int)Math.round(menu.segments().get(index).labelY())));
            assertFalse(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,700,100,0)); // Former panel rectangle is native map space.
            assertEquals(-120,KOMEConquestMapOverlay.selectorWheelDelta(map,700,100,-120));
            for(int i=0;i<6;i++){
                assertTrue(clickRoot(index));assertEquals(1,network.sent.size()); // Loading clicks never restart or confirm.
                proxy.acceptCompanyRally(new KOMECompanyRallySnapshot(request.session,token,"T001",
                    i==5?KOMECompanyRallySnapshot.Phase.READY:KOMECompanyRallySnapshot.Phase.LOADING,i+1,6,i+1,i+1,0,0,"6 will rally",
                    new KOMECompanyRallySnapshot.Entry(i,mapRow("C"+(i+1),kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW),"gondor",true,KOMECompanyRallySnapshot.Outcome.PREVIEW,"Will rally")),selectorConnection);
            }
            assertTrue(model.canConfirm());assertEquals("Confirm Rally",menu.label(KOMEMapRadialMenu.Action.RALLY_HERE));assertEquals("6 ready / 0 skipped",menu.status());
            assertEquals(6,model.readyCompanyIds().size());assertEquals(1,network.sent.size());assertTrue(preview().points.isEmpty());
            for(int rapid=0;rapid<4;rapid++)assertTrue(clickRoot(index));
            assertEquals(2,network.sent.size());assertEquals(KOMEPacketCompanyRallyRequest.Action.CONFIRM,((KOMEPacketCompanyRallyRequest)network.sent.get(1)).action);
            assertEquals("Rallying...",menu.label(KOMEMapRadialMenu.Action.RALLY_HERE));assertFalse(model.canConfirm());
            for(IMessage packet:network.sent)assertTrue(packet instanceof KOMEPacketCompanyRallyRequest);
            client.mc.thePlayer=client.player;double x=client.player.posX,y=client.player.posY,z=client.player.posZ;
            KOMECompanyRallySnapshot done=new KOMECompanyRallySnapshot(request.session,token,"T001",KOMECompanyRallySnapshot.Phase.DONE,14,6,6,6,6,6,"6 companies rallied. 0 skipped.",null);
            proxy.acceptCompanyRally(done,selectorConnection);proxy.acceptCompanyRally(done,selectorConnection);
            assertEquals(Collections.singletonList(done.message),client.player.messages);assertEquals(done.message,menu.status());
            assertEquals("Rally Here",menu.label(KOMEMapRadialMenu.Action.RALLY_HERE));assertFalse(model.canConfirm());assertSame(map,client.mc.currentScreen);
            assertEquals(x,client.player.posX,0);assertEquals(y,client.player.posY,0);assertEquals(z,client.player.posZ,0);assertEquals(0,client.mc.screenChanges);
            assertTrue(KOMEConquestMapOverlay.onMapKeyTyped(map,1));assertFalse(model.active());assertEquals("",KOMEConquestMapOverlay.selectorDestinationTile(map));
        }
    }
    @Test public void compactRallyActionTileSessionEscapeAndMapCloseDiscardConfirmation() throws Exception {
        for(int action=0;action<6;action++)try(DirectClient client=new DirectClient()) {
            client.mc.thePlayer=null;network.sent.clear();KOMEClientProxy proxy=connectSelector();click(400,300,1);clickRoot(0);clickRoot(2);
            KOMECompanyRallyClient model=rally();KOMEPacketCompanyRallyRequest old=(KOMEPacketCompanyRallyRequest)network.sent.get(0);
            KOMECompanyRallySnapshot ready=rallyReady(old);proxy.acceptCompanyRally(ready,selectorConnection);assertTrue(model.canConfirm());
            if(action==0)clickRoot(3); // Back is another radial action.
            if(action==1)clickRoot(0); // Move Here must invalidate Rally before opening its selector.
            if(action==2)assertTrue(KOMEConquestMapOverlay.onMapKeyTyped(map,1));
            if(action==3)new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));
            if(action==4){proxy.destinationOptions().start(new Object());KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,2,2,0);}
            if(action==5){centers().put("T050",new int[]{2300,58});assertTrue(KOMEConquestMapOverlay.focusRadialForTile(map,"T050"));assertEquals("T050",menu.tileId());}
            assertFalse(model.active());assertFalse(model.canConfirm());proxy.acceptCompanyRally(ready,selectorConnection);assertFalse(model.canConfirm());
            assertEquals("Rally Here",menu.label(KOMEMapRadialMenu.Action.RALLY_HERE));
            for(IMessage packet:network.sent)if(packet instanceof KOMEPacketCompanyRallyRequest)assertNotEquals(KOMEPacketCompanyRallyRequest.Action.CONFIRM,((KOMEPacketCompanyRallyRequest)packet).action);
        }
    }
    @Test public void compactRallyZeroErrorAndTimeoutNeverConfirmAndRetryStartsNewPreview() throws Exception {
        for(int failure=0;failure<3;failure++)try(DirectClient client=new DirectClient()) {
            client.mc.thePlayer=null;network.sent.clear();KOMEClientProxy proxy=connectSelector();click(400,300,1);clickRoot(0);clickRoot(2);
            KOMECompanyRallyClient oldModel=rally();KOMEPacketCompanyRallyRequest old=(KOMEPacketCompanyRallyRequest)network.sent.get(0);
            if(failure==0)proxy.acceptCompanyRally(new KOMECompanyRallySnapshot(old.session,UUID.randomUUID(),"T001",KOMECompanyRallySnapshot.Phase.READY,1,0,0,0,0,0,"0 will rally. 0 skipped.",null),selectorConnection);
            if(failure==1)proxy.acceptCompanyRally(new KOMECompanyRallySnapshot(old.session,new UUID(0,0),"T001",KOMECompanyRallySnapshot.Phase.ERROR,0,0,0,0,0,0,"Rally supports at most 64 Campaign companies. No partial Rally was prepared.",null),selectorConnection);
            if(failure==2){proxy.acceptCompanyRally(rallyReady(old),selectorConnection);field(KOMECompanyRallyClient.class,"expiresAt").setLong(oldModel,0);KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,2,2,0);}
            assertFalse(oldModel.canConfirm());assertEquals("Rally Here",menu.label(KOMEMapRadialMenu.Action.RALLY_HERE));
            assertTrue(menu.status().contains(failure==0?"No companies can arrive now":failure==1?"No partial":"expired"));
            assertTrue(clickRoot(2));assertEquals(3,network.sent.size());KOMEPacketCompanyRallyRequest fresh=(KOMEPacketCompanyRallyRequest)network.sent.get(2);
            assertEquals(KOMEPacketCompanyRallyRequest.Action.CANCEL,((KOMEPacketCompanyRallyRequest)network.sent.get(1)).action);
            assertEquals(KOMEPacketCompanyRallyRequest.Action.OPEN,fresh.action);assertNotEquals(old.session,fresh.session);assertFalse(oldModel.active());
            proxy.acceptCompanyRally(rallyReady(old),selectorConnection);assertFalse(rally().canConfirm());assertEquals("Loading Rally...",menu.label(KOMEMapRadialMenu.Action.RALLY_HERE));
        }
    }
    private KOMECompanyRallyClient rally()throws Exception{return (KOMECompanyRallyClient)field(KOMEConquestMapOverlay.class,"rally").get(null);}
    private KOMECompanyRallySnapshot rallyReady(KOMEPacketCompanyRallyRequest request){
        return new KOMECompanyRallySnapshot(request.session,UUID.randomUUID(),request.destination,KOMECompanyRallySnapshot.Phase.READY,1,1,1,1,0,0,"1 will rally.",
            new KOMECompanyRallySnapshot.Entry(0,mapRow("C1",kome.common.command.KOMECompanyMovementService.Status.CAN_START_AND_ARRIVE_NOW),"gondor",true,KOMECompanyRallySnapshot.Outcome.PREVIEW,"Will rally"));
    }
    private KOMECompanyUnitsBrowser units()throws Exception{return (KOMECompanyUnitsBrowser)field(KOMEConquestMapOverlay.class,"unitsBrowser").get(null);}
    private KOMECompanyDestinationPanel.Layout unitsLayout(){return KOMECompanyUnitsPanel.layout(KOMEConquestMapOverlay.viewport(map),false);}
    private void unitsControl(KOMECompanyDestinationPanel.Control control){KOMECompanyDestinationPanel.Rect r=unitsLayout().controls.get(control);assertTrue(click(r.x+2,r.y+2,0));}
    @Test public void unitsUsesOnlySourceMetadataSelectsForInspectionAndBackRestoresSameRootTile()throws Exception{
        try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;KOMEClientProxy proxy=connectSelector();click(400,300,1);assertTrue(clickRoot(4));
            assertEquals(1,network.sent.size());KOMEPacketCompanyOriginRequest request=(KOMEPacketCompanyOriginRequest)network.sent.get(0);
            assertEquals("T001",request.source);assertFalse(request.targeted);assertEquals("",request.destination);assertEquals("",request.company);
            assertTrue(units().active());assertNull(selector());assertNull(origin());assertEquals("T001",KOMEConquestMapOverlay.selectorDestinationTile(map));
            assertFalse(KOMEConquestMapOverlay.radialOwnsPointer(map,400,243));
            proxy.acceptCompanySourceOptions(KOMECompanyUnitsBrowserTest.page(request,9),selectorConnection);
            for(int row=0;row<4;row++){
                KOMECompanyDestinationPanel.Rect r=unitsLayout().row(row);assertTrue(click(r.x+2,r.y+2,0));
                assertEquals(String.format(Locale.ROOT,"C%02d",row+1),units().selectedCompanyId());assertTrue(KOMECompanyUnitsPanel.selected(units(),units().visible(row)));
            }
            assertEquals(1,network.sent.size());assertTrue(preview().points.isEmpty());assertEquals(0,client.mc.screenChanges);
            assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));
            KOMECompanyDestinationPanel.Rect row=unitsLayout().row(0);float zoom=field(LOTRGuiMap.class,"zoomScale").getFloat(map);
            for(int i=0;i<5;i++)assertEquals(0,KOMEConquestMapOverlay.selectorWheelDelta(map,row.x+2,row.y+2,-120));
            assertEquals(2,network.sent.size());KOMEPacketCompanyOriginRequest next=(KOMEPacketCompanyOriginRequest)network.sent.get(1);assertEquals("C08",next.after);
            proxy.acceptCompanySourceOptions(KOMECompanyUnitsBrowserTest.page(next,9),selectorConnection);assertEquals("C04",units().selectedCompanyId());
            assertEquals(120,KOMEConquestMapOverlay.selectorWheelDelta(map,2,2,120));assertEquals(zoom,field(LOTRGuiMap.class,"zoomScale").getFloat(map),0);
            assertFalse(KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,2,2,0));
            unitsControl(KOMECompanyDestinationPanel.Control.CLEAR);assertEquals("",units().selectedCompanyId());
            unitsControl(KOMECompanyDestinationPanel.Control.BACK);assertNull(units());assertEquals("T001",menu.tileId());assertEquals(KOMEMapRadialMenu.State.ROOT,menu.state());assertEquals("",KOMEConquestMapOverlay.selectorDestinationTile(map));
            for(IMessage packet:network.sent)assertTrue(packet instanceof KOMEPacketCompanyOriginRequest);
        }
    }
    @Test public void unitsRefreshEmptyStateAndAllCleanupPathsRejectLateMetadataWithoutMutations()throws Exception{
        for(int action=0;action<6;action++)try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;network.sent.clear();KOMEClientProxy proxy=connectSelector();click(400,300,1);clickRoot(4);
            KOMEPacketCompanyOriginRequest request=(KOMEPacketCompanyOriginRequest)network.sent.get(0);proxy.acceptCompanySourceOptions(KOMECompanyUnitsBrowserTest.page(request,1),selectorConnection);
            KOMECompanyUnitsBrowser original=units();KOMECompanyDestinationPanel.Rect row=unitsLayout().row(0);click(row.x+1,row.y+1,0);assertEquals("C01",original.selectedCompanyId());
            if(action==0){unitsControl(KOMECompanyDestinationPanel.Control.RETRY);KOMEPacketCompanyOriginRequest fresh=(KOMEPacketCompanyOriginRequest)network.sent.get(1);assertNotEquals(request.session,fresh.session);assertEquals("T001",fresh.source);proxy.acceptCompanySourceOptions(KOMECompanyUnitsBrowserTest.page(request,1),selectorConnection);assertTrue(units().rows().isEmpty());proxy.acceptCompanySourceOptions(KOMECompanyUnitsBrowserTest.page(fresh,0),selectorConnection);assertTrue(units().message().contains("No Campaign companies"));assertEquals("",units().selectedCompanyId());continue;}
            if(action==1)assertTrue(KOMEConquestMapOverlay.onMapKeyTyped(map,1));
            if(action==2)unitsControl(KOMECompanyDestinationPanel.Control.CLOSE);
            if(action==3)new KOMEConquestMapOverlay().onMapScreenChange(new GuiOpenEvent(null));
            if(action==4){proxy.destinationOptions().start(new Object());KOMEConquestMapOverlay.onMapSelectorMouseClicked(map,2,2,0);}
            if(action==5){centers().put("T050",new int[]{2300,58});assertTrue(KOMEConquestMapOverlay.focusRadialForTile(map,"T050"));}
            assertNull(units());assertFalse(original.active());assertEquals("",original.selectedCompanyId());assertEquals("",KOMEConquestMapOverlay.selectorDestinationTile(map));
            proxy.acceptCompanySourceOptions(KOMECompanyUnitsBrowserTest.page(request,1),selectorConnection);assertNull(units());
            for(IMessage packet:network.sent)assertTrue(packet instanceof KOMEPacketCompanyOriginRequest);
        }
    }
    @Test public void unitsPanelOwnsInputAtSupportedGuiSizesAndHasNoMarkerHighlightPath()throws Exception{
        try(DirectClient client=new DirectClient()){
            client.mc.thePlayer=null;KOMEClientProxy proxy=connectSelector();click(400,300,1);clickRoot(4);KOMEPacketCompanyOriginRequest request=(KOMEPacketCompanyOriginRequest)network.sent.get(0);proxy.acceptCompanySourceOptions(KOMECompanyUnitsBrowserTest.page(request,8),selectorConnection);
            for(int[] size:new int[][]{{320,240},{480,270},{640,360},{960,540}}){map.width=size[0];map.height=size[1];field(LOTRGuiMap.class,"mapXMax").setInt(null,size[0]);field(LOTRGuiMap.class,"mapYMax").setInt(null,size[1]);field(LOTRGuiMap.class,"mapWidth").setInt(null,size[0]);field(LOTRGuiMap.class,"mapHeight").setInt(null,size[1]);
                KOMECompanyDestinationPanel.Rect r=unitsLayout().row(0);assertEquals(0,KOMEConquestMapOverlay.selectorWheelDelta(map,r.x+1,r.y+1,-120));assertTrue(click(r.x+1,r.y+1,0));assertFalse(KOMEConquestMapOverlay.mapDragButtonDown(map,true));assertEquals(-120,KOMEConquestMapOverlay.selectorWheelDelta(map,2,2,-120));
            }
            String source=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("src/main/java/kome/client/KOMEConquestMapOverlay.java")),java.nio.charset.StandardCharsets.UTF_8);int start=source.indexOf("private static List<String> drawLiveUnitMarkers");String markers=source.substring(start,source.indexOf("private static List<String> drawBuildMarkers",start));assertFalse(markers.contains("unitsBrowser"));assertFalse(markers.contains("selectedCompanyId"));
            for(IMessage packet:network.sent)assertTrue(packet instanceof KOMEPacketCompanyOriginRequest);
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
        List<IMessage> sent,rootDiscovery;
        boolean autoRootDiscovery;
        public RecordingNetwork() {super("radial-test");}
        @Override public void sendToServer(IMessage message) {
            // Existing movement/native-input fixtures model an ordinary ineligible root.
            // Keep its new read-only discovery queries separately; root-flow tests record all packets.
            if(autoRootDiscovery&&message instanceof KOMEPacketJoinBattleViewRequest)try {
                KOMERootJoinBattle root=(KOMERootJoinBattle)field(KOMEConquestMapOverlay.class,"rootJoin").get(null);
                if(root!=null&&root.pending()==message&&!root.chooserPending()) {
                    rootDiscovery.add(message);
                    KOMEPacketJoinBattleViewResponse denied=new KOMEPacketJoinBattleViewResponse();
                    denied.tileId=((KOMEPacketJoinBattleViewRequest)message).tileId;denied.correlation=((KOMEPacketJoinBattleViewRequest)message).correlation;
                    denied.reason=kome.common.data.KOMEJoinBattleService.Reason.NO_ACTIVE_CONFLICT;
                    denied.reasonText="No active conflict.";KOMEConquestMapOverlay.acceptJoinBattleView(denied);return;
                }
            } catch(Exception failure){throw new AssertionError(failure);}
            sent.add(message);
        }
    }
}
