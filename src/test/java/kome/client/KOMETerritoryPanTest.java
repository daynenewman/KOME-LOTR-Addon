package kome.client;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import kome.common.data.KOMETileRasterSnapshot;
import kome.common.data.KOMETileTestResources;
import org.junit.*;
import static org.junit.Assert.*;

/** Complete-map reuse at far zoom, compared with the original exact padded-viewport construction. */
public class KOMETerritoryPanTest {
    private final Map<Integer,String> owners=new HashMap<>();
    private final Map<Integer,Integer> colors=new HashMap<>();
    private static Object loggingSide;
    @BeforeClass public static void initializeHeadlessProfilerLogging()throws Exception {
        if(KOMEMapProfiler.ENABLED){Field side=cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");side.setAccessible(true);loggingSide=side.get(null);side.set(null,cpw.mods.fml.relauncher.Side.CLIENT);}
    }
    @AfterClass public static void restoreHeadlessProfilerLogging()throws Exception {
        if(KOMEMapProfiler.ENABLED){Field side=cpw.mods.fml.relauncher.FMLRelaunchLog.class.getDeclaredField("side");side.setAccessible(true);side.set(null,loggingSide);}
    }

    @Test public void ordinaryFarZoomPansAndReversalReuseOneCompleteMeshWithExactVisibleOutput()throws Exception {
        KOMEMapBorders borders=fixture();KOMEMapBorders.TerritoryFrame first=null;
        for(KOMEMapViewport view:trace()) {
            KOMEMapBorders.TerritoryFrame frame=borders.territoryFrame(view,owners,colors);
            if(first==null)first=frame;
            assertVisibleEquals(legacy(borders,view),frame,view);assertTrue(frame.covers(view));
            assertSame("Far-zoom panning must not repartition unchanged whole-map borders",first,frame);
        }
        assertEquals(1,borders.territoryBuilds);assertTrue(borders.peakTerritoryBytes()<=KOMETerritoryFrameCache.MAX_BYTES);
    }

    @Test public void newlyExposedMapEdgesGapsAndSimilarlyColoredOwnersStayExactDuringRapidPans()throws Exception {
        KOMEMapBorders borders=fixture();colors.put(0x405060,0xF9F9F9);
        for(double[] point:new double[][]{{1600,2000},{-2000,-1000},{5200,5000},{1600,5000},{1600,-1000},{9000,9000},{1600,2000}}) {
            KOMEMapViewport view=view(point[0],point[1],.125);KOMEMapBorders.TerritoryFrame frame=borders.territoryFrame(view,owners,colors);
            assertVisibleEquals(legacy(borders,view),frame,view);assertTrue(frame.covers(view));
        }
        assertEquals(1,borders.territoryBuilds);
    }

    @Test public void interruptedZoomNeedsExactSettledWidthAndNeverUsesIncompleteCoverage()throws Exception {
        KOMEMapBorders borders=fixture();KOMEMapBorders.TerritoryFrame far=borders.territoryFrame(view(1600,2000,.125),owners,colors);
        for(double zoom:new double[]{.14,.19,.23,.18,.13}) {
            KOMEMapViewport view=view(2500,2800,zoom);
            assertSame(far,borders.territoryFrame(view,owners,colors,true,.125));
        }
        KOMEMapViewport settled=view(2500,2800,.25);KOMEMapBorders.TerritoryFrame exact=borders.territoryFrame(settled,owners,colors);
        assertNotSame(far,exact);assertEquals(.25,exact.view.zoom,0);assertVisibleEquals(legacy(borders,settled),exact,settled);
        KOMEMapViewport uncovered=view(2500,100,.25);KOMEMapBorders.TerritoryFrame changed=borders.territoryFrame(uncovered,owners,colors);
        assertNotSame(exact,changed);assertVisibleEquals(legacy(borders,uncovered),changed,uncovered);
        assertSame(far,borders.territoryFrame(view(1600,2000,.125),owners,colors));
    }

    @Test public void ownershipPaletteGeometryAndBoundedEvictionStillInvalidateFarMeshes()throws Exception {
        KOMEMapBorders borders=fixture();KOMEMapViewport view=view(1600,2000,.125);
        KOMETerritoryStyle style=new KOMETerritoryStyle();style.mode=KOMETerritoryStyle.DisplayMode.BORDERS_ONLY;borders.territoryPresentation(style);
        KOMEMapBorders.TerritoryFrame first=borders.territoryFrame(view,owners,colors);
        owners.put(0x405060,"gondor");KOMEMapBorders.TerritoryFrame changed=borders.territoryFrame(view,owners,colors);assertNotSame(first,changed);
        assertVisibleEquals(legacy(borders,view),changed,view);
        style.palette=KOMETerritoryStyle.Palette.CURRENT_KOME;colors.put(0x102030,0x123456);borders.territoryPresentation(style);
        first=borders.territoryFrame(view,owners,colors);assertNotSame(changed,first);assertVisibleEquals(legacy(borders,view),first,view);
        KOMETerritoryFrameCache cache=new KOMETerritoryFrameCache(KOMETerritoryFrameCache.cost(first));assertTrue(cache.retain(first));
        changed=legacy(borders,view(1600,2000,.25));assertTrue(cache.retain(changed));assertTrue(cache.size()<=1);
        assertTrue(cache.peakBytes()<=KOMETerritoryFrameCache.cost(first));
        for(double zoom:new double[]{.25,.5,1,2})borders.territoryFrame(view(1600,2000,zoom),owners,colors);
        assertEquals(3,borders.retainedTerritoryFrames());assertTrue(borders.peakTerritoryBytes()<=KOMETerritoryFrameCache.MAX_BYTES);
        borders.clearTerritoryFrames();assertEquals(0,borders.retainedTerritoryBytes());
        assertNotSame(first,borders.territoryFrame(view,owners,colors));
    }

    @Test public void smallerViewportCannotClaimUnbuiltWholeMapCoverage()throws Exception {
        KOMEMapBorders borders=fixture();KOMEMapViewport first=new KOMEMapViewport(0,0,320,180,1600,2000,.125);
        KOMEMapBorders.TerritoryFrame initial=borders.territoryFrame(first,owners,colors);assertFalse(initial.completeMap);
        KOMEMapViewport exposed=new KOMEMapViewport(0,0,320,180,1600,100,.125);
        assertFalse(initial.covers(exposed));KOMEMapBorders.TerritoryFrame rebuilt=borders.territoryFrame(exposed,owners,colors);
        assertNotSame(initial,rebuilt);assertVisibleEquals(legacy(borders,exposed),rebuilt,exposed);
    }

    @Test public void fullColorDispatchDoesNotConstructOrRetainFarZoomMeshes()throws Exception {
        KOMEMapBorders borders=fixture();Field geometry=KOMEConquestMapOverlay.class.getDeclaredField("mapBorders");geometry.setAccessible(true);
        Object previous=geometry.get(null);KOMEMapPreferences preferences=KOMEConquestMapOverlay.mapPreferences();
        try {
            geometry.set(null,borders);KOMEConquestMapOverlay.territoryStyle.mode=KOMETerritoryStyle.DisplayMode.FULL_COLOR;
            KOMEConquestMapOverlay.territoryStyle.showTileBorders=false;
            for(KOMEMapViewport view:trace())KOMEConquestMapOverlay.drawTerritoryGeometry(view,0,(x,y,r,b,c)->fail("Full Color must not emit territory border meshes"));
            assertEquals(0,borders.territoryBuilds);assertEquals(0,borders.retainedTerritoryFrames());
        }finally{geometry.set(null,previous);KOMEConquestMapOverlay.applyMapPreferences(preferences);}
    }

    @Test public void comparableRealRasterPanBenchmark()throws Exception {
        KOMETileRasterSnapshot raster=KOMETileTestResources.real();owners.clear();colors.clear();
        for(String line:Files.readAllLines(Paths.get("src/main/resources/assets/kome/config/kome_tile_ownership_defaults.csv"),StandardCharsets.UTF_8)) {
            String[] parts=line.split(",",3);Integer mask=raster.colorsById().get(parts[0]);if(mask==null||parts.length<2||parts[1].isEmpty())continue;
            String owner=kome.common.data.KOMEAlliance.normalizeFactionKey(parts[1]);if(owner.isEmpty())continue;
            owners.put(mask,owner);colors.put(mask,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(owner));
        }
        KOMEMapBorders borders=new KOMEMapBorders.Cache().get(raster,raster.copyArgbPixels());
        List<Long> construction=new ArrayList<>(),partition=new ArrayList<>();long vertexNanos=0,evictions=0,rejects=0;int prepared=0,panBuilds=0,index=0;
        KOMETerritoryStyle style=new KOMETerritoryStyle();style.mode=KOMETerritoryStyle.DisplayMode.BORDERS_ONLY;
        for(KOMEMapViewport view:trace()) {
            KOMEMapProfiler.begin(style,true);long builds=borders.territoryBuilds,before=borders.territoryBuildNanos;
            KOMEMapBorders.TerritoryFrame frame=borders.territoryFrame(view,owners,colors);assertTrue(frame.covers(view));
            boolean built=borders.territoryBuilds!=builds;if(built){construction.add(borders.territoryBuildNanos-before);if(index>0)panBuilds++;}
            if(!frame.mesh.verticesPrepared()){prepared++;frame.mesh.vertices();}
            KOMEMapProfiler.end();
            if(KOMEMapProfiler.ENABLED) {
                long[] times=profile("frameTimes"),counts=profile("frameCounts");
                if(built)partition.add(times[KOMEMapProfiler.Stage.PARTITION.ordinal()]);
                vertexNanos+=times[KOMEMapProfiler.Stage.VERTICES.ordinal()];evictions+=counts[KOMEMapProfiler.Count.EVICTION.ordinal()];rejects+=counts[KOMEMapProfiler.Count.COVERAGE_REJECT.ordinal()];
            }
            index++;
        }
        KOMEMapProfiler.close();Collections.sort(construction);Collections.sort(partition);
        String metrics="Synthetic real-raster 794x399 scale=0.125; 66 horizontal/vertical pan and reverse requests; no GL/FPS measurement.\n"
            +"profiled="+KOMEMapProfiler.ENABLED+" builds="+borders.territoryBuilds+" panBuilds="+panBuilds+" hits="+borders.territoryHits+" misses="+borders.territoryMisses
            +" evictions="+evictions+" coverageRejects="+rejects+" medianBuildMs="+median(construction)+" worstBuildMs="+ms(construction.get(construction.size()-1))
            +" partitionTotalMs="+ms(sum(partition))+" medianPartitionMs="+median(partition)+" worstPartitionMs="+(partition.isEmpty()?0:ms(partition.get(partition.size()-1)))
            +" vertexPreparations="+prepared+" vertexPreparationMs="+ms(vertexNanos)+" peakReservedBytes="+borders.peakTerritoryBytes()+" unretained="+borders.territoryUnretainedBuilds+"\n";
        Path path=Paths.get("build/reports/territory-pan/"+(KOMEMapProfiler.ENABLED?"profiled":"unprofiled")+"-metrics.txt");Files.createDirectories(path.getParent());Files.write(path,metrics.getBytes(StandardCharsets.UTF_8));
        assertEquals("A complete far-zoom map must not rebuild on each coverage exit",1,borders.territoryBuilds);
        assertTrue(borders.peakTerritoryBytes()<=KOMETerritoryFrameCache.MAX_BYTES);assertEquals(0,borders.territoryUnretainedBuilds);
    }

    private KOMEMapBorders fixture()throws Exception {
        BufferedImage image=new BufferedImage(64,80,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<80;y++)for(int x=0;x<64;x++)image.setRGB(x,y,(x==20&&y<65)||(y==40&&x>35)?0:x<32+(y%7)?0xFF102030:0xFF405060);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        KOMETileRasterSnapshot raster=KOMETileRasterSnapshot.load(new ByteArrayInputStream(bytes.toByteArray()),new ByteArrayInputStream("16,32,48=T001\n64,80,96=T002\n".getBytes(StandardCharsets.UTF_8)),
            new KOMETileRasterSnapshot.Transform(100,0,0,1,3200,4000),new HashSet<>(Arrays.asList("T001","T002")),Collections.emptySet());
        owners.put(0x102030,"gondor");owners.put(0x405060,"mordor");colors.put(0x102030,0xF9F9F9);colors.put(0x405060,0x351F1F);
        return new KOMEMapBorders.Cache().get(raster,raster.copyArgbPixels());
    }
    private KOMEMapBorders.TerritoryFrame legacy(KOMEMapBorders borders,KOMEMapViewport v)throws Exception {
        KOMEMapViewport padded=new KOMEMapViewport(v.left-64,v.top-64,v.right+64,v.bottom+64,v.posX,v.posY,v.zoom);
        KOMETerritoryMesh.Rectangles input=new KOMETerritoryMesh.Rectangles();
        Method method=KOMEMapBorders.class.getDeclaredMethod("drawTerritoriesUncached",KOMEMapViewport.class,Map.class,Map.class,KOMEMapBorders.LayerSink.class);method.setAccessible(true);
        method.invoke(borders,padded,owners,colors,(KOMEMapBorders.LayerSink)input::add);
        return new KOMEMapBorders.TerritoryFrame(padded,owners,colors,KOMETerritoryMesh.union(input));
    }
    private static List<KOMEMapViewport> trace() {
        List<KOMEMapViewport> views=new ArrayList<>();for(int direction:new int[]{1,-1})for(int step=0;step<=32;step++) {
            int i=direction==1?step:32-step;views.add(view(1600+(i-16)*24/.125,2000+(i%9-4)*24/.125,.125));
        }return views;
    }
    private static KOMEMapViewport view(double x,double y,double zoom){return new KOMEMapViewport(0,0,794,399,x,y,zoom);}
    private static void assertVisibleEquals(KOMEMapBorders.TerritoryFrame expected,KOMEMapBorders.TerritoryFrame actual,KOMEMapViewport view) {
        List<double[]> a=new ArrayList<>(),b=new ArrayList<>();List<Integer> ac=new ArrayList<>(),bc=new ArrayList<>();
        expected.mesh.draw(view,expected.view,(x,y,r,d,c)->{a.add(new double[]{x,y,r,d});ac.add(c);});
        actual.mesh.draw(view,actual.view,(x,y,r,d,c)->{b.add(new double[]{x,y,r,d});bc.add(c);});
        assertCovered(a,ac,b,bc);assertCovered(b,bc,a,ac);
    }
    private static void assertCovered(List<double[]> a,List<Integer> ac,List<double[]> b,List<Integer> bc) {
        for(int i=0;i<a.size();i++) {double[] r=a.get(i);double area=(r[2]-r[0])*(r[3]-r[1]),covered=0;
            for(int j=0;j<b.size();j++)if(ac.get(i).equals(bc.get(j))) {double[] s=b.get(j);covered+=Math.max(0,Math.min(r[2],s[2])-Math.max(r[0],s[0]))*Math.max(0,Math.min(r[3],s[3])-Math.max(r[1],s[1]));}
            assertEquals("Exact disjoint visible coverage and color",area,covered,Math.max(1,area)*1e-8);
        }
    }
    private static long[] profile(String name)throws Exception {Field live=KOMEMapProfiler.class.getDeclaredField("LIVE");live.setAccessible(true);Field f=KOMEMapProfiler.class.getDeclaredField(name);f.setAccessible(true);return (long[])f.get(live.get(null));}
    private static long sum(List<Long> list){long total=0;for(long n:list)total+=n;return total;}
    private static double ms(long nanos){return nanos/1_000_000D;}
    private static double median(List<Long> sorted){return sorted.isEmpty()?0:(sorted.get((sorted.size()-1)/2)/2D+sorted.get(sorted.size()/2)/2D)/1_000_000D;}
}
