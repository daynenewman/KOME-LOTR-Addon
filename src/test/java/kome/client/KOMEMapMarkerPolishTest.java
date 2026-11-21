package kome.client;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import kome.common.KOMEAccessFixture;
import lotr.client.gui.LOTRGuiMap;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEMapMarkerPolishTest {
    @Test public void shieldOnlyGrowsOnePixelAtNativeScaleTwo() {
        KOMEMapIconPresentation atTwo=KOMEMapIconPresentation.at(2);
        for(KOMEMapMarkerArt.Sprite shield:new KOMEMapMarkerArt.Sprite[]{
                KOMEMapMarkerArt.shield("gondor"),KOMEMapMarkerArt.NEUTRAL_SHIELD}) {
            KOMEMapIconPresentation.Bounds bounds=shield.bounds(atTwo,400,300);
            assertEquals(12,bounds.width);assertEquals(12,bounds.height);
            assertEquals(400,bounds.x+bounds.width/2);assertEquals(300,bounds.y+bounds.height/2);
        }
        assertEquals(7,KOMEMapMarkerArt.banner("gondor").bounds(atTwo,400,300).width);
        assertEquals(14,KOMEMapMarkerArt.banner("gondor").bounds(atTwo,400,300).height);
        assertEquals(7,KOMEMapMarkerArt.CASTLE.bounds(atTwo,400,300).width);
    }
    @Test public void nativeSettledZoomPowersReachTheSpritePolicyAsEightDistinctScales()throws Exception {
        LOTRGuiMap map=KOMEAccessFixture.allocate(LOTRGuiMap.class);
        Field power=LOTRGuiMap.class.getDeclaredField("zoomPower");power.setAccessible(true);
        Field ticks=LOTRGuiMap.class.getDeclaredField("zoomTicks");ticks.setAccessible(true);
        Field scale=LOTRGuiMap.class.getDeclaredField("zoomScale");scale.setAccessible(true);
        int savedPower=power.getInt(null);
        try {
            for(int p=-3;p<=4;p++) {
                power.setInt(null,p);ticks.setInt(map,0);map.setupZoomVariables(0);
                double nativeScale=Math.pow(2,p);
                assertEquals(nativeScale,scale.getFloat(map),0);
                KOMEMapIconPresentation.Bounds bounds=KOMEMapMarkerArt.shield("gondor").bounds(
                    KOMEMapIconPresentation.at(scale.getFloat(map)),400,300);
                assertEquals(new int[]{7,8,9,9,12,14,17,18}[p+3],bounds.width);
            }
        }finally{power.setInt(null,savedPower);}
    }
    @Test public void fullZoomArtBoundsKeepApprovedTargetsAndOpacity()throws Exception {
        KOMEMapMarkerArt.Sprite[] arts={KOMEMapMarkerArt.shield("rohan"),KOMEMapMarkerArt.banner("rohan"),KOMEMapMarkerArt.CASTLE,KOMEMapMarkerArt.NEUTRAL_SHIELD,KOMEMapMarkerArt.NEUTRAL_BANNER};
        int[][] targets={{18,18},{10,20},{10,10},{18,18},{10,20}};
        for(int i=0;i<arts.length;i++)for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}) {
            KOMEMapIconPresentation p=KOMEMapIconPresentation.at(zoom);
            KOMEMapIconPresentation.Bounds b=arts[i].bounds(p,400,300);
            assertEquals(p.alpha,b.alpha);assertEquals(400,b.x+b.width/2);assertEquals(300,b.y+b.height/2);
            if(zoom<=.25){KOMEMapIconPresentation.Bounds old=p.bounds(400,300,arts[i].width,arts[i].height);assertEquals(old.width,b.width);assertEquals(old.height,b.height);}
            if(zoom>=16){assertEquals(targets[i][0],b.width);assertEquals(targets[i][1],b.height);}
            assertTrue(b.contains(b.x,b.y));assertFalse(b.contains(b.x+b.width,b.y));
        }
        for(KOMEMapMarkerArt.Sprite art:arts) {
            int previousWidth=0,previousHeight=0;
            for(int step=0;step<=100;step++) {
                KOMEMapIconPresentation.Bounds b=art.bounds(KOMEMapIconPresentation.at(Math.pow(2,-1+5*step/100D)),400,300);
                assertTrue(b.width>=previousWidth);assertTrue(b.height>=previousHeight);previousWidth=b.width;previousHeight=b.height;
            }
        }
    }
    @Test public void easedSizesPreserveLowerZoomAndGrowContinuouslyWithMatchingVisibleInteractionBounds() {
        double[] zooms={.125,.25,.5,1,2,4,8,16};
        int[] alphas={64,128,191,255,255,255,255,255};
        int[][] shields={{7,7},{8,8},{9,9},{9,9},{12,12},{14,14},{17,17},{18,18}};
        int[][] banners={{5,9},{5,11},{6,12},{6,12},{7,14},{8,16},{9,19},{10,20}};
        int[][] castles={{5,5},{5,5},{6,6},{6,6},{7,7},{8,8},{9,9},{10,10}};
        KOMEMapMarkerArt.Sprite[] arts={KOMEMapMarkerArt.shield("rohan"),KOMEMapMarkerArt.banner("rohan"),KOMEMapMarkerArt.CASTLE};
        int[][][] expected={shields,banners,castles};
        for(int i=0;i<arts.length;i++)for(int z=0;z<zooms.length;z++) {
            KOMEMapIconPresentation.Bounds b=arts[i].bounds(KOMEMapIconPresentation.at(zooms[z]),400,300);
            assertEquals(expected[i][z][0],b.width);assertEquals(expected[i][z][1],b.height);assertEquals(alphas[z],b.alpha);
            assertTrue(b.contains(b.x,b.y));assertTrue(b.contains(b.x+b.width-1,b.y+b.height-1));
            assertFalse(b.contains(b.x-1,b.y));assertFalse(b.contains(b.x+b.width,b.y));assertFalse(b.contains(b.x,b.y+b.height));
            assertTrue(b.visible(new KOMEMapViewport(b.x,b.y,b.x+b.width,b.y+b.height,0,0,1)));
            assertFalse(b.visible(new KOMEMapViewport(b.x+1,b.y,b.x+b.width,b.y+b.height,0,0,1)));
        }
        // Dense animated zooms exercise the same continuous curve between stops, rounded once to GUI pixels.
        for(KOMEMapMarkerArt.Sprite art:arts) {
            KOMEMapIconPresentation.Bounds previous=art.bounds(KOMEMapIconPresentation.at(.125),400,300);
            for(int step=1;step<=700;step++) {
                KOMEMapIconPresentation.Bounds b=art.bounds(KOMEMapIconPresentation.at(Math.pow(2,-3+step*.01)),400,300);
                assertTrue(b.width>=previous.width&&b.width-previous.width<=1);
                assertTrue(b.height>=previous.height&&b.height-previous.height<=1);previous=b;
            }
        }
    }
    @Test public void halfZoomRawSizesRemainContinuousAndMonotonicWhileAlphaAndGeographyUseActualZoom() {
        int[][] dimensions={{11,11,18,18},{7,14,10,20},{7,7,10,10}};
        for(int[] size:dimensions) {
            double[] previous=rawSize(.125,size);
            for(int step=1;step<=10000;step++) {
                double zoom=Math.pow(2,-3+7*step/10000D);double[] current=rawSize(zoom,size);
                for(int axis=0;axis<2;axis++) {
                    assertTrue("No inward-zoom shrink at "+zoom,current[axis]>=previous[axis]);
                    assertTrue("No raw size jump at "+zoom,current[axis]-previous[axis]<.02);
                }
                KOMEMapIconPresentation p=KOMEMapIconPresentation.at(zoom);
                double detail=Math.max(0,Math.min(1,(Math.log(zoom)/Math.log(2)+3)/3));
                assertEquals((int)Math.round(64+191*detail),p.alpha);
                previous=current;
            }
            for(double boundary:new double[]{.5,Math.pow(2,1.4),1,4})for(int axis=0;axis<2;axis++) {
                double epsilon=1e-7,center=rawSize(boundary,size)[axis];
                double left=(center-rawSize(boundary-epsilon,size)[axis])/epsilon;
                double right=(rawSize(boundary+epsilon,size)[axis]-center)/epsilon;
                assertEquals("Continuous slope at local adjustment edge",left,right,.005);
            }
        }
        for(double zoom:new double[]{.125,.25,.375,.5,.675,.75,.875,1}) {
            KOMEMapIconPresentation.MapBounds geography=KOMEMapIconPresentation.geography(zoom,400,300,6,3);
            assertEquals(6*zoom,geography.width,0);assertEquals(3*zoom,geography.height,0);
        }
    }
    private static double[] rawSize(double zoom,int[] size) {
        KOMEMapIconPresentation p=KOMEMapIconPresentation.at(zoom);
        double bump=size[0]==11&&size[2]==18?p.shieldBump:0;
        return new double[]{size[0]*p.spriteScale+(size[2]-size[0])*p.spriteEnlargement+bump,
            size[1]*p.spriteScale+(size[3]-size[1])*p.spriteEnlargement+bump};
    }
    @Test public void buildsSubmitInGuardedTerrainExitAndNeverAgainInPost()throws Exception {
        assertEquals(0,calls(method("onDrawMap"),"drawBuildMarkers"));
        assertEquals(1,calls(method("onBeforeNativeMapLabels"),"renderEarlyBuilds"));
        assertEquals(1,calls(method("drawBuildLayer"),"drawBuildMarkers"));
        assertEquals(0,calls(method("drawBuildLayer"),"drawTerritoryLayer"));
    }
    @Test public void productionHasNoDeletedTroopsPngReferenceAndPopulationIsNotClaimedAsCaptureOwner()throws Exception {
        ClassNode n=node();
        for(MethodNode m:n.methods)for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof LdcInsnNode) {
            Object value=((LdcInsnNode)step).cst;
            if(value instanceof String){assertFalse(((String)value).contains("troopsicon.png"));if(m.name.equals("renderBuildMarkers"))assertFalse(((String)value).startsWith("Owner: "));}
        }
    }
    private static ClassNode node()throws Exception {ClassNode n=new ClassNode();try(InputStream s=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(s).accept(n,0);}return n;}
    private static MethodNode method(String name)throws Exception{for(MethodNode m:node().methods)if(m.name.equals(name))return m;throw new AssertionError("Missing "+name);}
    private static int calls(MethodNode m,String name){int n=0;for(AbstractInsnNode s:m.instructions.toArray())if(s instanceof MethodInsnNode&&((MethodInsnNode)s).name.equals(name))n++;return n;}
}
