package kome.client;

import java.lang.reflect.*;
import java.util.*;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMEVisualMarker;
import kome.common.network.KOMEPacketVisualMarkers;
import io.netty.buffer.*;
import lotr.client.gui.LOTRGuiMap;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionTestingPresentationTest {
    @Test public void colorQueryBufferSatisfiesRealLwjgl2BoundsCheck(){
        org.lwjgl.BufferChecks.checkBuffer(org.lwjgl.BufferUtils.createFloatBuffer(KOMEVisualRenderBridge.GL_QUERY_FLOATS),16);
    }
    private Field field(String name)throws Exception{Field f=LOTRGuiMap.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private KOMEVisualMarker marker(String id,double x){return new KOMEVisualMarker(KOMEVisualMarker.Role.ESCORT,id,"Traveler","",0,x,64,200);}
    @Test public void objectiveViewSurvivesConquestPreferenceRestoration()throws Exception{
        LOTRGuiMap map=KOMEAccessFixture.allocate(KOMEProgressionMapFocus.FocusedMap.class);KOMEVisualMarker target=marker("traveler",10000);KOMEProgressionMapFocus.focus(map,target);
        Method update=KOMEConquestMapOverlay.class.getDeclaredMethod("updateMapViewportState",net.minecraft.client.gui.GuiScreen.class);update.setAccessible(true);
        String[] bounds={"mapXMin","mapXMax","mapYMin","mapYMax"};int[] old=new int[4],values={10,610,20,420};
        KOMEConquestMapOverlay.resetClientMapState();
        try{
            for(int i=0;i<4;i++){old[i]=field(bounds[i]).getInt(null);field(bounds[i]).setInt(null,values[i]);}
            for(String name:new String[]{"savedMapPosX","savedMapPosY","savedMapZoomScale","savedMapZoomScaleStable","savedMapZoomExp"}){
                Field f=KOMEConquestMapOverlay.class.getDeclaredField(name);f.setAccessible(true);f.setFloat(null,name.equals("savedMapPosX")?900:name.equals("savedMapPosY")?800:name.equals("savedMapZoomExp")?0:1);
            }
            Field saved=KOMEConquestMapOverlay.class.getDeclaredField("hasSavedMapViewport");saved.setAccessible(true);saved.setBoolean(null,true);
            update.invoke(null,map);assertEquals(KOMEProgressionMapFocus.mapX(target.x),field("posX").getFloat(map),0);assertEquals(4,field("zoomPower").getInt(null));assertEquals(16,field("zoomScale").getFloat(map),0);
            Field savedX=KOMEConquestMapOverlay.class.getDeclaredField("savedMapPosX");savedX.setAccessible(true);assertEquals(900,savedX.getFloat(null),0);
            assertTrue(KOMEProgressionMapFocus.isObjectiveView(map));assertFalse(KOMEProgressionMapFocus.isObjectiveView(KOMEAccessFixture.allocate(LOTRGuiMap.class)));
        }finally{for(int i=0;i<4;i++)field(bounds[i]).setInt(null,old[i]);KOMEConquestMapOverlay.resetClientMapState();}
    }
    @Test public void markerReflectionWorksOnTheFocusedSubclass()throws Exception{
        Method prepare=KOMEVisualRenderBridge.class.getDeclaredMethod("prepareReflection",Class.class);prepare.setAccessible(true);
        assertEquals(Boolean.TRUE,prepare.invoke(null,KOMEProgressionMapFocus.FocusedMap.class));
    }
    @Test public void nativeCoordinateTransformCentersTargetInActualMapBounds()throws Exception{
        LOTRGuiMap map=KOMEAccessFixture.allocate(LOTRGuiMap.class);KOMEProgressionMapFocus.focus(map,marker("t",12345));
        String[] bounds={"mapXMin","mapXMax","mapYMin","mapYMax","mapWidth","mapHeight"};int[] old=new int[6],values={10,610,20,420,600,400};
        try{for(int i=0;i<bounds.length;i++){old[i]=field(bounds[i]).getInt(null);field(bounds[i]).setInt(null,values[i]);}
            Method transform=LOTRGuiMap.class.getDeclaredMethod("transformCoords",float.class,float.class);transform.setAccessible(true);
            float[] point=(float[])transform.invoke(map,12345F,200F);assertEquals(310,point[0],0.01);assertEquals(220,point[1],0.01);
            map.setupZoomVariables(0.5F);assertEquals(16,field("zoomScale").getFloat(map),0);assertEquals(field("posX").getFloat(map),field("prevPosX").getFloat(map),0);
        }finally{for(int i=0;i<bounds.length;i++)field(bounds[i]).setInt(null,old[i]);}
    }
    @Test public void movingMarkerAndReturnRelationshipUseOnlyCurrentObjective(){
        KOMEVisualMarker gift=new KOMEVisualMarker(KOMEVisualMarker.Role.MASTER_GIFT,"gift","Gift","",0,1,0,2);
        KOMEVisualMarker liege=new KOMEVisualMarker(KOMEVisualMarker.Role.KNIGHT_LIEGE,"current","Liege","",0,30,64,40,false,true);
        assertSame(liege,KOMEProgressionMapFocus.target(Arrays.asList(gift,liege),0));
        KOMEVisualMarker moved=marker("traveler",500);assertSame(moved,KOMEProgressionMapFocus.target(Arrays.asList(gift,moved),0));
        KOMEProgressionMapFocus.highlight(marker("traveler",100),0);assertTrue(KOMEProgressionMapFocus.isFocused(moved,9_999_999_999L));assertFalse(KOMEProgressionMapFocus.isFocused(moved,10_000_000_000L));KOMEProgressionMapFocus.clear();
    }
    @Test public void objectiveIdentityRoundTripsWithoutDuplicatingMarkers(){
        KOMEVisualMarker liege=new KOMEVisualMarker(KOMEVisualMarker.Role.KNIGHT_LIEGE,"current","Liege","",0,30,64,40,false,true);
        ByteBuf b=Unpooled.buffer();new KOMEPacketVisualMarkers(Arrays.asList(liege)).toBytes(b);KOMEPacketVisualMarkers decoded=new KOMEPacketVisualMarkers();decoded.fromBytes(b);
        assertEquals(1,decoded.markers.size());assertEquals(liege.signature(),decoded.markers.get(0).signature());assertEquals(0,b.readableBytes());b.release();
    }
    @Test public void destinationUsesAnExistingNativeShieldResource(){
        assertTrue(KOMEVisualRenderBridge.isDestination(marker("",100)));assertFalse(KOMEVisualRenderBridge.isDestination(marker("traveler",100)));
        net.minecraft.util.ResourceLocation texture=KOMEVisualRenderBridge.destinationShieldTexture();assertEquals("lotr",texture.getResourceDomain());
        assertNotNull(getClass().getResourceAsStream("/assets/lotr/"+texture.getResourcePath()));
    }
    @Test public void auraTargetsRaceAndHonorsVisibilityAndParticleLimits(){
        for(com.lotrcharactercreation.race.PlayerRace race:com.lotrcharactercreation.race.PlayerRace.values())assertEquals(race==com.lotrcharactercreation.race.PlayerRace.ELF,KOMEElvenFootGlow.visible(race,false,false,false,0,0));
        com.lotrcharactercreation.race.PlayerRace elf=com.lotrcharactercreation.race.PlayerRace.ELF;
        assertFalse(KOMEElvenFootGlow.visible(elf,true,false,false,0,0));assertFalse(KOMEElvenFootGlow.visible(elf,false,true,false,0,0));
        assertFalse(KOMEElvenFootGlow.visible(elf,false,false,true,0,0));assertFalse(KOMEElvenFootGlow.visible(elf,false,false,false,2,0));assertFalse(KOMEElvenFootGlow.visible(elf,false,false,false,0,625));
    }
    @Test public void actualAuraUsesParticleBatchWithoutGlWorldMutationOrNetwork()throws Exception{
        org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader("kome/client/KOMEElvenFootGlow").accept(node,0);int effects=0;
        for(org.objectweb.asm.tree.MethodNode m:node.methods)for(org.objectweb.asm.tree.AbstractInsnNode i:m.instructions.toArray())if(i instanceof org.objectweb.asm.tree.MethodInsnNode){
            org.objectweb.asm.tree.MethodInsnNode call=(org.objectweb.asm.tree.MethodInsnNode)i;assertFalse(call.owner,call.owner.contains("org/lwjgl/opengl")||call.owner.contains("network"));assertFalse(call.name,call.name.equals("spawnEntityInWorld")||call.name.equals("setBlock"));if(call.name.equals("addEffect"))effects++;
        }
        assertEquals(1,effects);
    }
    @Test public void focusedGuiReappliesCameraAfterNativeInitialization()throws Exception{
        org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader("kome/client/KOMEProgressionMapFocus$FocusedMap").accept(node,0);
        for(org.objectweb.asm.tree.MethodNode m:node.methods)if(m.name.equals("initGui")){
            int position=0,nativeInit=-1,focus=-1;for(org.objectweb.asm.tree.AbstractInsnNode i:m.instructions.toArray()){if(i instanceof org.objectweb.asm.tree.MethodInsnNode){org.objectweb.asm.tree.MethodInsnNode call=(org.objectweb.asm.tree.MethodInsnNode)i;if(call.owner.equals("lotr/client/gui/LOTRGuiMap")&&call.name.equals("initGui"))nativeInit=position;if(call.name.equals("focus"))focus=position;}position++;}
            assertTrue(nativeInit>=0);assertTrue(focus>nativeInit);return;
        }
        fail("No native initialization override");
    }
}
