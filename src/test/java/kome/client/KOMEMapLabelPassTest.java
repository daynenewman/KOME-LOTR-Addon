package kome.client;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMETileRasterSnapshot;
import lotr.client.gui.LOTRGuiMap;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.junit.*;
import org.lwjgl.opengl.GL11;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEMapLabelPassTest {
    @Test public void geographyIconsDrawInTheNativeHookInsteadOfAboveWaypointsInPost()throws Exception {
        MethodNode post=method("onDrawMap");
        for(String name:new String[]{"drawAutomaticBridgeMarkers","drawAutomaticRiverBlockMarkers","drawGeographyLayer"})
            assertEquals("Geography must not cover native names in Post",0,count(post,name));
        assertEquals(1,count(method("onBeforeNativeMapLabels"),"renderEarlyGeography"));
    }
    private final Map<Field,Object> saved=new HashMap<>();
    private LOTRGuiMap map;
    private final KOMEConquestMapOverlay overlay=new KOMEConquestMapOverlay();
    @Before public void setup() throws Exception {
        map=KOMEAccessFixture.allocate(LOTRGuiMap.class);
        for(String name:new String[]{"earlyTerritoryMap","warnedEarlyTerritoryFailure","earlyGeographyMap","earlyBridgeTooltip","earlyRiverTooltip","warnedEarlyGeographyFailure","earlyBuildMap","buildSpritesMap","earlyBuildTooltip","warnedEarlyBuildFailure"}) {
            Field field=field(name);saved.put(field,field.get(null));
        }
        beginFrame();
    }
    @After public void cleanup() throws Exception {for(Map.Entry<Field,Object> entry:saved.entrySet())entry.getKey().set(null,entry.getValue());}

    @Test public void buildsRunOnceBeforeNativeTextAndPostOnlyConsumesTheirTooltip()throws Exception {
        int[] draws={0};KOMEConquestMapOverlay.renderEarlyBuilds(map,()->{
            draws[0]++;try{field("earlyBuildTooltip").set(null,Arrays.asList("Build"));}catch(Exception e){throw new AssertionError(e);}
        });
        KOMEConquestMapOverlay.renderEarlyBuilds(map,()->draws[0]++);assertEquals(1,draws[0]);
        assertSame(map,field("buildSpritesMap").get(null));
        overlay.onDrawMap(new GuiScreenEvent.DrawScreenEvent.Post(map,10,20,.5F));
        assertNull(field("earlyBuildMap").get(null));assertNull(field("earlyBuildTooltip").get(null));
        assertSame("Successful sprite pass remains eligible for subsequent input",map,field("buildSpritesMap").get(null));assertEquals(1,draws[0]);
        beginFrame();assertNull(field("buildSpritesMap").get(null));
    }

    @Test public void failedOrUnsupportedBuildHookHasNoPostRetryOrInvisiblePickingAndLifecycleClearsState()throws Exception {
        int[] draws={0};KOMEConquestMapOverlay.renderEarlyBuilds(map,()->{draws[0]++;throw new IllegalStateException("injected Build failure");});
        KOMEConquestMapOverlay.renderEarlyBuilds(map,()->draws[0]++);assertEquals(1,draws[0]);assertNull(field("buildSpritesMap").get(null));
        overlay.onDrawMap(new GuiScreenEvent.DrawScreenEvent.Post(map,10,20,.5F));assertEquals(1,draws[0]);
        beginFrame();KOMEConquestMapOverlay.renderEarlyBuilds(map,()->{});overlay.onResourceManagerReload(null);assertNull(field("buildSpritesMap").get(null));
        KOMEConquestMapOverlay.renderEarlyBuilds(map,()->{});KOMEConquestMapOverlay.resetClientMapState();assertNull(field("earlyBuildMap").get(null));
    }

    @Test public void geographyAttemptRunsOnceAndTooltipsAreConsumedOnlyForTheSameFrameAndMap()throws Exception {
        int[] draws={0};
        KOMEConquestMapOverlay.renderEarlyGeography(map,()->{
            draws[0]++;try{field("earlyBridgeTooltip").set(null,Arrays.asList("Bridge"));}catch(Exception e){throw new AssertionError(e);}
        });
        KOMEConquestMapOverlay.renderEarlyGeography(map,()->draws[0]++);
        assertEquals(1,draws[0]);assertSame(map,field("earlyGeographyMap").get(null));
        overlay.onDrawMap(new GuiScreenEvent.DrawScreenEvent.Post(map,10,20,.5F));
        assertNull(field("earlyGeographyMap").get(null));assertNull(field("earlyBridgeTooltip").get(null));
        KOMEConquestMapOverlay.renderEarlyGeography(map,()->draws[0]++);
        beginFrame();assertNull(field("earlyGeographyMap").get(null));
        KOMEConquestMapOverlay.renderEarlyGeography(map,()->draws[0]++);
        LOTRGuiMap other=KOMEAccessFixture.allocate(LOTRGuiMap.class);
        overlay.onDrawMap(new GuiScreenEvent.DrawScreenEvent.Post(other,10,20,.5F));
        assertNull(field("earlyGeographyMap").get(null));assertEquals(3,draws[0]);
    }

    @Test public void failedEarlyGeographyHasNoPostRetryAndReloadDisconnectClearItsState()throws Exception {
        int[] draws={0};KOMEConquestMapOverlay.renderEarlyGeography(map,()->{draws[0]++;throw new IllegalStateException("injected geography failure");});
        KOMEConquestMapOverlay.renderEarlyGeography(map,()->draws[0]++);
        assertEquals(1,draws[0]);assertSame(map,field("earlyGeographyMap").get(null));
        assertNull(field("earlyBridgeTooltip").get(null));assertNull(field("earlyRiverTooltip").get(null));
        overlay.onResourceManagerReload(null);assertNull(field("earlyGeographyMap").get(null));
        KOMEConquestMapOverlay.renderEarlyGeography(map,()->{});KOMEConquestMapOverlay.resetClientMapState();
        assertNull(field("earlyGeographyMap").get(null));assertNull(field("earlyRiverTooltip").get(null));
    }

    @Test public void geographyEarlyPassIncludesAutomaticManualAndRouteSpritesWithoutMeshesOrNativeRedraws()throws Exception {
        MethodNode layer=method("drawGeographyLayer");
        for(String name:new String[]{"drawAutomaticBridgeMarkers","drawAutomaticRiverBlockMarkers","drawRouteEdgeMarkers"})assertEquals(name,1,count(layer,name));
        assertEquals(2,count(layer,"drawRouteBridgeIcons")); // Active movements and preview.
        for(String name:new String[]{"drawTerritoryLayer","drawGeometry","territoryFrame","renderLabels","renderWaypoints","drawTroopMarkers","drawBuildMarkers","drawActiveMovementRoutes","drawRoutePreview"})assertEquals(name,0,count(layer,name));
        assertBooleanCall(layer,"drawRouteEdgeMarkers",true);
        assertBooleanCall(method("drawRouteBridgeIcons"),"drawRouteSpecialEdgeMarkers",true);
        assertBooleanCall(method("drawRouteEdgeMarkers"),"drawRouteEdgeMarkers",false);
        assertEquals("Route-specific geography never restores old passage diamonds",0,
            count(method("drawRouteSpecialEdgeMarkers"),"drawDiamond"));
        assertEquals("Active bridge icons follow committed remaining route",1,count(layer,"remainingTiles"));
        for(String type:new String[]{kome.common.data.KOMEConquestRouteEdge.BRIDGE,kome.common.data.KOMEConquestRouteEdge.MOUNTAIN_PASS}) {
            kome.common.data.KOMEConquestRouteEdge edge=new kome.common.data.KOMEConquestRouteEdge();edge.edgeType=type;
            assertEquals(type.equals(kome.common.data.KOMEConquestRouteEdge.BRIDGE),KOMEConquestMapOverlay.geographyEdgePass(edge,true));
            assertNotEquals(KOMEConquestMapOverlay.geographyEdgePass(edge,true),KOMEConquestMapOverlay.geographyEdgePass(edge,false));
        }
    }

    @Test public void productionRoutePassUsesRemainingTilesAndNoHistoricalDiamonds()throws Exception {
        MethodNode active=method("drawActiveMovementRoutes");
        assertEquals(1,count(active,"remainingTiles"));
        assertEquals(1,count(active,"projectRouteMapTiles"));
        assertEquals(1,count(active,"drawMarchingTrack"));
        MethodNode preview=method("drawRoutePreview");
        assertEquals(1,count(preview,"drawMarchingTrack"));
        assertEquals(1,count(preview,"projectRouteMapTiles"));
        assertEquals(1,count(method("drawCompanyDestinationPreview"),"drawMarchingTrack"));
        for(String obsolete:new String[]{"drawRouteOriginMarker","drawRouteIntermediatePoint","drawDiamond"}){
            assertEquals(obsolete,0,count(active,obsolete));
            assertEquals(obsolete,0,count(preview,obsolete));
        }
        assertEquals("No route-specific destination diamond",0,
            count(method("drawMarchingTrack"),"drawRouteDestinationMarker"));
        assertEquals("A route has one colored stroke, without a dark underlay",1,
            count(method("drawMarchingTrack"),"drawMarchingStroke"));
        assertEquals(0,count(method("drawMarchingStroke"),"glLineWidth"));
        assertEquals(1,count(method("drawMarchingStroke"),"glBegin"));
        assertEquals(1,count(method("drawMarchingStroke"),"emitRouteQuadsIndexed"));
        assertTrue("Committed orders use the active per-leg style",hasField(active,"ACTIVE"));
        assertTrue("Unsubmitted routes use the opaque preview style",hasField(preview,"PREVIEW"));
        for(AbstractInsnNode step:method("drawMarchingStroke").instructions.toArray())
            if(step instanceof MethodInsnNode&&((MethodInsnNode)step).name.equals("glBegin"))
                assertEquals("Reliable GUI-pixel thickness uses filled quads",GL11.GL_QUADS,number(previous(step)));
    }
    @Test public void troopDisplayPreferenceOwnsEveryCompanyRouteVisualButNotGeography() throws Exception {
        for(String method:new String[]{"drawActiveMovementRoutes","drawRoutePreview",
                "drawCompanyDestinationPreview","drawRouteBridgeIcons"})
            assertEquals(method,1,count(method(method),"companyMovementVisualsVisible"));
        assertEquals(0,count(method("drawRouteEdgeMarkers"),"companyMovementVisualsVisible"));
        assertEquals(0,count(method("drawAutomaticBridgeMarkers"),"companyMovementVisualsVisible"));
        assertEquals(0,count(method("drawAutomaticRiverBlockMarkers"),"companyMovementVisualsVisible"));
        assertEquals(1,count(method("drawRoutePreviewPanel"),"routePreviewDisplayText"));
        Field visibility=field("showTroopMarkers");boolean previous=visibility.getBoolean(null);
        try {
            visibility.setBoolean(null,false);
            assertFalse(KOMEConquestMapOverlay.companyMovementVisualsVisible());
            assertTrue(KOMEConquestMapOverlay.routePreviewDisplayText().contains("hidden"));
            visibility.setBoolean(null,true);
            assertTrue(KOMEConquestMapOverlay.companyMovementVisualsVisible());
            assertFalse(KOMEConquestMapOverlay.routePreviewDisplayText().contains("hidden"));
        }finally{visibility.setBoolean(null,previous);}
    }

    private static void assertBooleanCall(MethodNode method,String name,boolean value) {
        boolean found=false;
        for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode&&((MethodInsnNode)step).name.equals(name)) {
            assertEquals(value?Opcodes.ICONST_1:Opcodes.ICONST_0,previous(step).getOpcode());found=true;
        }
        assertTrue(name,found);
    }

    @Test public void successfulSkippedAndFailedNativePassesCannotBeBlendedAgainInPost() {
        int[] draws={0};
        KOMEConquestMapOverlay.renderBeforeNativeLabels(map,()->draws[0]++);
        assertTrue(KOMEConquestMapOverlay.consumeEarlyTerritoryPass(map));
        assertFalse(KOMEConquestMapOverlay.consumeEarlyTerritoryPass(map));assertEquals(1,draws[0]);
        beginFrame();KOMEConquestMapOverlay.onBeforeNativeMapLabels(map,10,20); // Invalid zero viewport skips GL.
        assertTrue(KOMEConquestMapOverlay.consumeEarlyTerritoryPass(map));
        beginFrame();KOMEConquestMapOverlay.renderBeforeNativeLabels(map,()->{draws[0]++;throw new IllegalStateException("injected render failure");});
        assertTrue("Native drawing continues; Post cannot retry a partly rendered layer",KOMEConquestMapOverlay.consumeEarlyTerritoryPass(map));
        assertEquals(2,draws[0]);
    }

    @Test public void frameAndMapChangesPreventStaleOwnershipAndActualPostConsumesIt() throws Exception {
        KOMEConquestMapOverlay.onBeforeNativeMapLabels(map,10,20);
        beginFrame();assertFalse(KOMEConquestMapOverlay.consumeEarlyTerritoryPass(map)); // Native hook absent this frame.
        KOMEConquestMapOverlay.onBeforeNativeMapLabels(map,10,20);
        assertFalse(KOMEConquestMapOverlay.consumeEarlyTerritoryPass(KOMEAccessFixture.allocate(LOTRGuiMap.class)));
        KOMEConquestMapOverlay.onBeforeNativeMapLabels(map,10,20);
        overlay.onDrawMap(new GuiScreenEvent.DrawScreenEvent.Post(map,10,20,.5F));
        assertNull(field("earlyTerritoryMap").get(null));
        assertTrue(KOMEConquestMapOverlay.class.getMethod("onMapDrawStart",GuiScreenEvent.DrawScreenEvent.Pre.class)
            .getAnnotation(cpw.mods.fml.common.eventhandler.SubscribeEvent.class).receiveCanceled());
    }

    @Test public void geometryClearDuringInitialLoadingCannotEraseTheCompletedEarlyPassMarker() throws Exception {
        java.lang.reflect.Method clear=KOMEConquestMapOverlay.class.getDeclaredMethod("clearTileGeometry");clear.setAccessible(true);
        KOMEConquestMapOverlay.renderBeforeNativeLabels(map,()->{
            try{clear.invoke(null);}catch(Exception failure){throw new AssertionError(failure);}
        });
        assertTrue(KOMEConquestMapOverlay.consumeEarlyTerritoryPass(map));
        KOMEConquestMapOverlay.onBeforeNativeMapLabels(map,10,20);overlay.onResourceManagerReload(null);
        assertFalse(KOMEConquestMapOverlay.consumeEarlyTerritoryPass(map));
        KOMEConquestMapOverlay.onBeforeNativeMapLabels(map,10,20);KOMEConquestMapOverlay.resetClientMapState();
        assertFalse(KOMEConquestMapOverlay.consumeEarlyTerritoryPass(map));
    }

    @Test public void postFallbackIsGuardedAndKeepsControlsRoutesCuesAndTooltipsInPost() throws Exception {
        MethodNode post=method("onDrawMap");int consumeLocal=-1,guardedFallbacks=0;
        for(AbstractInsnNode step:post.instructions.toArray()) {
            if(step instanceof MethodInsnNode) {
                String name=((MethodInsnNode)step).name;
                if(name.equals("consumeEarlyTerritoryPass"))consumeLocal=((VarInsnNode)next(step)).var;
                assertFalse("No direct duplicate territory drawing in Post",Arrays.asList("drawClaimedTexture","prepareTerritoryColors","drawHighlightTexture","drawGeometry").contains(name));
            }
            if(step instanceof JumpInsnNode && step.getOpcode()==Opcodes.IFNE && previous(step) instanceof VarInsnNode
                    && ((VarInsnNode)previous(step)).var==consumeLocal) {
                for(AbstractInsnNode body=step.getNext();body!=((JumpInsnNode)step).label;body=body.getNext())
                    if(body instanceof MethodInsnNode && ((MethodInsnNode)body).name.equals("drawTerritoryLayer"))guardedFallbacks++;
            }
        }
        assertTrue(consumeLocal>=0);assertEquals(1,guardedFallbacks);
        assertEquals(1,count(post,"drawTerritoryLayer"));
        assertEquals("Visible on normal, hidden-layer and unavailable-mask paths",3,count(post,"drawToggleButtons"));
        for(String name:new String[]{"drawSelectorDestination","drawTileCue","drawActiveMovementRoutes","drawRoutePreview","drawTileTooltip","drawRoutePreviewPanel"})
            assertEquals(name,1,count(post,name));
        MethodNode early=method("drawTerritoryLayer");assertEquals(1,count(early,"drawGeometry"));assertEquals(1,count(early,"drawClaimedTexture"));
        for(String name:new String[]{"setupZoomVariables","restoreMapViewport","updateMapViewportState","renderLabels","renderWaypoints","clearTerritoryFrames"})assertEquals(name,0,count(early,name));
    }

    @Test public void earlyLayerRestoresNativeDepthAlphaTextureAndMatrixStateOnEveryExit() throws Exception {
        MethodNode method=method("drawTerritoryLayer");int mask=0;Set<Integer> disabled=new HashSet<>();boolean noDepthWrites=false;
        for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode) {
            String name=((MethodInsnNode)step).name;
            if(name.equals("glPushAttrib"))mask=number(previous(step));
            if(name.equals("glDisable"))disabled.add(number(previous(step)));
            if(name.equals("glDepthMask"))noDepthWrites=previous(step).getOpcode()==Opcodes.ICONST_0;
        }
        int required=GL11.GL_ENABLE_BIT|GL11.GL_COLOR_BUFFER_BIT|GL11.GL_CURRENT_BIT|GL11.GL_TEXTURE_BIT|GL11.GL_DEPTH_BUFFER_BIT;
        assertEquals(required,mask&required);assertTrue(noDepthWrites);
        assertTrue(disabled.contains(GL11.GL_DEPTH_TEST));assertTrue(disabled.contains(GL11.GL_ALPHA_TEST));
        assertEquals(1,count(method,"glPushMatrix"));
        assertEquals("Normal, missing-mask return, exception",3,count(method,"glPopMatrix"));assertEquals(3,count(method,"glPopAttrib"));
        assertTrue(method.tryCatchBlocks.stream().anyMatch(handler->handler.type==null));
        assertEquals("Texture initialization is inside the protected scope",1,count(method,"ensureTileMaskLoaded"));
        for(AbstractInsnNode step:method.instructions.toArray())assertNotEquals("No native camera/view mutation",Opcodes.PUTFIELD,step.getOpcode());
    }

    @Test public void movingThePassDoesNotAddMeshRequestsOrRebuildsAcrossZoomAndPostFrames() throws Exception {
        KOMETileRasterSnapshot raster=kome.common.data.KOMETileTestResources.real();
        KOMEMapBorders borders=new KOMEMapBorders.Cache().get(raster,raster.copyArgbPixels());
        Map<Integer,String> owners=new HashMap<>();Map<Integer,Integer> colors=new HashMap<>();
        for(int color:raster.colorsById().values()){owners.put(color,"gondor");colors.put(color,0xF9F9F9);}
        int[] requests={0};
        for(int frame=0;frame<20;frame++) {
            beginFrame();final int index=frame;double zoom=frame==0?1:frame==19?2:1+frame/20D;
            KOMEMapViewport view=new KOMEMapViewport(0,0,120,90,1000,730,zoom);
            Runnable draw=()->{requests[0]++;borders.territoryFrame(view,owners,colors,index>0&&index<19,1);};
            KOMEConquestMapOverlay.renderBeforeNativeLabels(map,draw);
            if(!KOMEConquestMapOverlay.consumeEarlyTerritoryPass(map))draw.run();
        }
        assertEquals(20,requests[0]);assertEquals(2,borders.territoryBuilds);assertEquals(18,borders.territoryHits);
        assertEquals(2,borders.retainedTerritoryFrames());assertTrue(borders.retainedTerritoryBytes()<=KOMETerritoryFrameCache.MAX_BYTES);
    }

    private void beginFrame(){overlay.onMapDrawStart(new GuiScreenEvent.DrawScreenEvent.Pre(map,10,20,.5F));}
    private static Field field(String name)throws Exception{Field field=KOMEConquestMapOverlay.class.getDeclaredField(name);field.setAccessible(true);return field;}
    private static MethodNode method(String name)throws Exception{ClassNode node=new ClassNode();try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(in).accept(node,0);}for(MethodNode method:node.methods)if(method.name.equals(name))return method;throw new AssertionError(name);}
    private static int count(MethodNode method,String name){int count=0;for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode&&((MethodInsnNode)step).name.equals(name))count++;return count;}
    private static boolean hasField(MethodNode method,String name){for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof FieldInsnNode&&((FieldInsnNode)step).name.equals(name))return true;return false;}
    private static AbstractInsnNode previous(AbstractInsnNode step){do{step=step.getPrevious();}while(step!=null&&step.getOpcode()<0);return step;}
    private static AbstractInsnNode next(AbstractInsnNode step){do{step=step.getNext();}while(step!=null&&step.getOpcode()<0);return step;}
    private static int number(AbstractInsnNode step){return step instanceof LdcInsnNode?((Number)((LdcInsnNode)step).cst).intValue():((IntInsnNode)step).operand;}
}
