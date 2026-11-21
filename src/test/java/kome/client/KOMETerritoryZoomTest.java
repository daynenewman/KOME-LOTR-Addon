package kome.client;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMETileRasterSnapshot;
import lotr.client.gui.LOTRGuiMap;
import org.junit.*;
import static org.junit.Assert.*;

/** Uses the actual native interpolation routine, without a Minecraft process or GL context. */
public class KOMETerritoryZoomTest {
    private LOTRGuiMap map;
    private final Map<Field,Object> saved=new LinkedHashMap<>();
    private final Map<Integer,String> owners=new HashMap<>();
    private final Map<Integer,Integer> colors=new HashMap<>();
    @Before public void setup() throws Exception {
        map=KOMEAccessFixture.allocate(LOTRGuiMap.class);
        for(String name:new String[]{"mapXMin","mapXMax","mapYMin","mapYMax","mapWidth","mapHeight","zoomPower"}) {
            Field f=field(name);saved.put(f,f.get(null));
        }
        for(String name:new String[]{"mapXMin","mapYMin"})field(name).setInt(null,0);
        field("mapXMax").setInt(null,40);field("mapYMax").setInt(null,30);
        field("mapWidth").setInt(null,40);field("mapHeight").setInt(null,30);
        field("posX").setFloat(map,48);field("posY").setFloat(map,48);
        owners.put(0x102030,"gondor");owners.put(0x405060,"mordor");
        colors.put(0x102030,0xF9F9F9);colors.put(0x405060,0x351F1F);
        assertEquals(6,field("zoomTicksMax").getInt(null));
    }
    @After public void cleanup() throws Exception {for(Map.Entry<Field,Object> e:saved.entrySet())e.getKey().set(null,e.getValue());}

    @Test public void sixNativeTicksIncludingPartialFramesReuseStableMeshesZoomingIn() throws Exception {assertNativeSequence(0,1);}
    @Test public void sixNativeTicksIncludingPartialFramesReuseStableMeshesZoomingOut() throws Exception {assertNativeSequence(1,0);}

    @Test public void reverseAndInterruptedZoomsKeepOneCoveredStableMeshAndMatchNativePartialTickPositions() throws Exception {
        KOMEMapBorders borders=fixture();nativeFrame(0,0,0,0);KOMEMapBorders.TerritoryFrame start=request(borders);
        java.nio.ByteBuffer buffer=start.mesh.vertices();
        for(int[] direction:new int[][]{{0,1},{1,0},{0,1},{1,2},{2,1}}) {
            for(int ticks=6;ticks>=3;ticks--)for(float partial:new float[]{0,.5F,.9F}) {
                nativeFrame(direction[0],direction[1],ticks,partial);
                KOMEMapBorders.TerritoryFrame current=request(borders);assertSame(start,current);assertSame(buffer,current.mesh.vertices());
                assertAligned(current,KOMEConquestMapOverlay.viewport(map));
            }
        }
        nativeFrame(0,0,0,0);assertSame(start,request(borders));assertEquals(1,borders.territoryBuilds);
    }

    @Test public void outwardZoomPlansWiderCoverageOnceRatherThanStretchingAnIncompleteMesh() throws Exception {
        dimensions(320,240);field("posX").setFloat(map,192);field("posY").setFloat(map,192);
        KOMEMapBorders borders=fixture(384);nativeFrame(1,1,0,0);KOMEMapBorders.TerritoryFrame start=request(borders);
        KOMEMapViewport envelope=new KOMEMapViewport(0,0,320,240,192,192,1);
        assertFalse(start.covers(envelope));
        KOMEMapBorders.TerritoryFrame coverage=null;
        for(int ticks=6;ticks>0;ticks--)for(float partial:new float[]{0,.5F}) {
            nativeFrame(1,0,ticks,partial);KOMEMapBorders.TerritoryFrame current=request(borders);
            if(coverage==null)coverage=current;else assertSame(coverage,current);
            assertNotSame(start,current);assertTrue(current.covers(envelope));assertAligned(current,KOMEConquestMapOverlay.viewport(map));
        }
        nativeFrame(0,0,0,0);KOMEMapBorders.TerritoryFrame settled=request(borders);assertSame(coverage,settled);
        assertEquals(1,settled.view.zoom,0);assertEquals(2,borders.territoryBuilds);assertEquals(0,borders.territoryUnretainedBuilds);
        // The gap at map X=60..64 was outside the original mesh. New visible land must retain its border.
        assertSample(settled,envelope,65.25,192.25,255);
        assertSample(settled,envelope,69.25,192.25,36);assertSample(settled,envelope,70.25,192.25,0);
        assertSample(settled,envelope,62.25,192.25,0);
    }

    @Test public void coverageChecksAreExplicitAtTheBoundaryAndPanReuseDoesNotReconstructEveryFrame() throws Exception {
        KOMEMapBorders borders=fixture();nativeFrame(0,0,0,0);KOMEMapBorders.TerritoryFrame first=request(borders);
        double boundary=first.view.mapX(first.view.left)+20;
        assertTrue(first.covers(new KOMEMapViewport(0,0,40,30,boundary,48,1)));
        assertFalse(first.covers(new KOMEMapViewport(0,0,40,30,boundary-0.001,48,1)));
        for(int i=0;i<25;i++){field("posX").setFloat(map,48+i*8);request(borders);}
        for(int i=24;i>=0;i--){field("posX").setFloat(map,48+i*8);request(borders);}
        assertTrue(borders.territoryBuilds<=4);assertTrue(borders.territoryHits>40);
        assertTrue(borders.retainedTerritoryFrames()<=KOMETerritoryFrameCache.MAX_FRAMES);
        assertTrue(borders.peakTerritoryBytes()<=KOMETerritoryFrameCache.MAX_BYTES);
    }

    @Test public void territoryAndPresentationChangesDuringAnimationCannotReuseStaleGeometry() throws Exception {
        KOMEMapBorders borders=fixture();KOMETerritoryStyle style=new KOMETerritoryStyle();style.mode=KOMETerritoryStyle.DisplayMode.BORDERS_ONLY;
        borders.territoryPresentation(style);nativeFrame(0,1,4,.5F);KOMEMapBorders.TerritoryFrame previous=request(borders);
        owners.put(0x405060,"gondor");colors.put(0x405060,0xF9F9F9);
        KOMEMapBorders.TerritoryFrame changed=request(borders);assertNotSame(previous,changed);assertTrue(changed.contentMatches(owners,colors));
        previous=changed;
        style.palette=KOMETerritoryStyle.Palette.CURRENT_KOME;borders.territoryPresentation(style);
        assertEquals(0,borders.retainedTerritoryBytes());changed=request(borders);assertNotSame(previous,changed);
        style.mode=KOMETerritoryStyle.DisplayMode.FULL_COLOR;borders.territoryPresentation(style);assertEquals(0,borders.retainedTerritoryFrames());
        style.mode=KOMETerritoryStyle.DisplayMode.BORDERS_ONLY;borders.territoryPresentation(style);previous=request(borders);assertNotSame(changed,previous);
        style.opacity=style.opacity.next();borders.territoryPresentation(style);assertNotSame(previous,request(borders));
        previous=request(borders);style.showTileBorders=true;borders.territoryPresentation(style);assertNotSame(previous,request(borders));
    }

    @Test public void stableMeshLruAndAggregateReservationsBoundLazyVertexMemory() throws Exception {
        KOMEMapBorders borders=fixture();List<KOMEMapBorders.TerritoryFrame> frames=new ArrayList<>();
        for(int power=-1;power<=2;power++){nativeFrame(power,power,0,0);frames.add(request(borders));}
        assertEquals(3,borders.retainedTerritoryFrames());long builds=borders.territoryBuilds;
        nativeFrame(0,0,0,0);assertSame(frames.get(1),request(borders));assertEquals(builds,borders.territoryBuilds);
        nativeFrame(-1,-1,0,0);request(borders);assertEquals(builds+1,borders.territoryBuilds);
        long budget=KOMETerritoryFrameCache.cost(frames.get(0))+KOMETerritoryFrameCache.cost(frames.get(1))-1;
        KOMETerritoryFrameCache cache=new KOMETerritoryFrameCache(budget);
        assertTrue(cache.retain(frames.get(0)));assertTrue(cache.retain(frames.get(1)));assertEquals(1,cache.size());
        long reservation=cache.reservedBytes();frames.get(1).mesh.vertices();
        assertEquals(reservation,cache.reservedBytes());assertTrue(frames.get(1).mesh.retainedBytes()<=reservation);
        assertTrue(cache.peakBytes()<=budget);cache.clear();assertEquals(0,cache.reservedBytes());
        // A complete mesh can remain retained in streaming form if direct vertices alone would exceed a budget.
        KOMEMapBorders.TerritoryFrame stream=frames.get(2);
        long streamCost=stream.mesh.quads()*36L+1024+128L*(stream.owners.size()+stream.colors.size());
        cache=new KOMETerritoryFrameCache(streamCost);assertTrue(cache.retain(stream));assertTrue(stream.streamOnly);
        assertTrue(cache.reservedBytes()<=streamCost);
        // If even complete streamed geometry cannot fit, the budget must win. This remains
        // the unresolved fallback: it cannot be reused on the following animated frame.
        cache=new KOMETerritoryFrameCache(streamCost-1);assertFalse(cache.retain(stream));
        assertEquals(0,cache.size());assertEquals(0,cache.reservedBytes());
    }

    @Test public void geometryReplacementAndExplicitClearReleaseAllStableZooms() throws Exception {
        KOMEMapBorders borders=fixture();for(int power=0;power<3;power++){nativeFrame(power,power,0,0);request(borders).mesh.vertices();}
        assertTrue(borders.retainedTerritoryBytes()>0);borders.clearTerritoryFrames();assertEquals(0,borders.retainedTerritoryBytes());
        KOMETileRasterSnapshot raster=smallRaster(96);KOMEMapBorders.Cache geometry=new KOMEMapBorders.Cache();
        KOMEMapBorders old=geometry.get(raster,raster.copyArgbPixels());nativeFrame(0,0,0,0);request(old);
        geometry.get(smallRaster(80),smallRaster(80).copyArgbPixels());assertEquals(0,old.retainedTerritoryBytes());
    }

    @Test public void animatedAffineKeepsCornerAlphaSingleAndSettledWidthExactlySixPixels() throws Exception {
        field("posX").setFloat(map,0);field("posY").setFloat(map,0);KOMEMapBorders borders=fixture();
        nativeFrame(0,0,0,0);KOMEMapBorders.TerritoryFrame first=request(borders);
        for(int ticks=6;ticks>0;ticks--)for(float partial:new float[]{0,.5F,.9F}) {
            nativeFrame(0,1,ticks,partial);KOMEMapBorders.TerritoryFrame current=request(borders);assertSame(first,current);
            assertSample(current,KOMEConquestMapOverlay.viewport(map),5.25,5.25,36);
        }
        nativeFrame(1,1,0,0);KOMEMapBorders.TerritoryFrame settled=request(borders);assertEquals(2,settled.view.zoom,0);
        assertSample(settled,KOMEConquestMapOverlay.viewport(map),2.625,2.625,36);
        assertSample(settled,KOMEConquestMapOverlay.viewport(map),3.125,3.125,0);
        assertSample(settled,KOMEConquestMapOverlay.viewport(map),.375,.375,255);
    }

    @Test public void realRasterSixTickZoomPolicyBenchmark() throws Exception {
        dimensions(1200,700);field("posX").setFloat(map,1000);field("posY").setFloat(map,730);
        KOMETileRasterSnapshot raster=kome.common.data.KOMETileTestResources.real();owners.clear();colors.clear();
        for(String line:Files.readAllLines(Paths.get("src/main/resources/assets/kome/config/kome_tile_ownership_defaults.csv"),StandardCharsets.UTF_8)) {
            String[] parts=line.split(",",3);Integer mask=raster.colorsById().get(parts[0]);
            if(mask==null || parts.length<2 || parts[1].isEmpty())continue;
            String owner=kome.common.data.KOMEAlliance.normalizeFactionKey(parts[1]);if(owner.isEmpty())continue;
            owners.put(mask,owner);colors.put(mask,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(owner));
        }
        List<String> metrics=new ArrayList<>();metrics.add("Native setupZoomVariables: 6 ticks x 4 partial frames plus warm start and settled frame; no GL context.");
        metrics.add("Before = exact-zoom single-frame policy replay using the same geometry generator; RED independently established 25 baseline meshes.");
        for(int[] direction:new int[][]{{-2,-1},{-1,-2}}) {
            KOMEMapBorders before=new KOMEMapBorders.Cache().get(raster,raster.copyArgbPixels());
            benchmark(before,direction[0],direction[1],true,"before",metrics);
            KOMEMapBorders after=new KOMEMapBorders.Cache().get(raster,raster.copyArgbPixels());
            benchmark(after,direction[0],direction[1],false,"after-first",metrics);
            benchmark(after,direction[0],direction[1],false,"after-return",metrics);
        }
        Path directory=Paths.get("build/reports/territory-zoom-phase1c");Files.createDirectories(directory);
        Files.write(directory.resolve("metrics.txt"),metrics,StandardCharsets.UTF_8);
    }

    private void benchmark(KOMEMapBorders borders,int from,int to,boolean legacy,String label,List<String> metrics) throws Exception {
        long beforeBuilds=borders.territoryBuilds,beforeHits=borders.territoryHits,beforeMisses=borders.territoryMisses;
        List<Long> builds=new ArrayList<>();long[] preparation={0};int[] prepared={0},draws={0};double[] previousZoom={Double.NaN};
        nativeFrame(from,from,0,0);benchmarkFrame(borders,legacy,builds,preparation,prepared,draws,previousZoom);
        for(int ticks=6;ticks>0;ticks--)for(float partial:new float[]{0,.25F,.5F,.75F}) {
            nativeFrame(from,to,ticks,partial);benchmarkFrame(borders,legacy,builds,preparation,prepared,draws,previousZoom);
        }
        nativeFrame(to,to,0,0);benchmarkFrame(borders,legacy,builds,preparation,prepared,draws,previousZoom);
        Collections.sort(builds);double median=builds.isEmpty()?0:(builds.get((builds.size()-1)/2)/2D+builds.get(builds.size()/2)/2D)/1_000_000;
        long count=borders.territoryBuilds-beforeBuilds;
        assertEquals(legacy?25:label.equals("after-return")?0:2,count);assertEquals(0,borders.territoryUnretainedBuilds);
        assertTrue(borders.peakTerritoryBytes()<=KOMETerritoryFrameCache.MAX_BYTES);
        metrics.add(label+" "+from+"->"+to+" rebuilds="+count+" hits="+(borders.territoryHits-beforeHits)+" misses="+(borders.territoryMisses-beforeMisses)
            +" medianBuildMs="+median+" worstBuildMs="+(builds.isEmpty()?0:builds.get(builds.size()-1)/1_000_000D)
            +" peakReservedBytes="+borders.peakTerritoryBytes()+" vertexPreparations="+prepared[0]+" vertexPreparationMs="+preparation[0]/1_000_000D
            +" expectedGlSubmissions="+draws[0]+" unretained="+borders.territoryUnretainedBuilds);
    }

    private void benchmarkFrame(KOMEMapBorders borders,boolean legacy,List<Long> builds,long[] preparation,int[] prepared,int[] draws,double[] previousZoom) throws Exception {
        KOMEMapViewport view=KOMEConquestMapOverlay.viewport(map);
        if(legacy && previousZoom[0]!=view.zoom)borders.clearTerritoryFrames();previousZoom[0]=view.zoom;
        long before=borders.territoryBuildNanos;
        KOMEMapBorders.TerritoryFrame frame=legacy?borders.territoryFrame(view,owners,colors):request(borders);
        if(before!=borders.territoryBuildNanos)builds.add(borders.territoryBuildNanos-before);
        assertTrue(frame.covers(view));
        if(!frame.streamOnly && frame.mesh.cacheable() && frame.mesh.quads()>0) {
            boolean fresh=!frame.mesh.verticesPrepared();long start=System.nanoTime();frame.mesh.vertices();preparation[0]+=System.nanoTime()-start;
            if(fresh)prepared[0]++;draws[0]++;
        }
    }

    private void assertNativeSequence(int from,int to) throws Exception {
        KOMEMapBorders borders=fixture();Set<KOMEMapBorders.TerritoryFrame> built=Collections.newSetFromMap(new IdentityHashMap<>());
        nativeFrame(from,from,0,0);built.add(request(borders));
        for(int ticks=6;ticks>0;ticks--)for(float partial:new float[]{0,.25F,.5F,.75F}) {
            nativeFrame(from,to,ticks,partial);
            float exp=from+(to-from)*((6-ticks+partial)/6F);
            assertEquals((float)Math.pow(2,exp),KOMEConquestMapOverlay.viewport(map).zoom,0);
            built.add(request(borders));
        }
        nativeFrame(to,to,0,0);KOMEMapBorders.TerritoryFrame settled=request(borders);built.add(settled);
        assertEquals(Math.pow(2,to),settled.view.zoom,0);
        assertTrue("Six native ticks reconstructed "+built.size()+" distinct meshes",built.size()<=2);
    }

    private KOMEMapBorders.TerritoryFrame request(KOMEMapBorders borders) throws Exception {
        return borders.territoryFrame(KOMEConquestMapOverlay.viewport(map),owners,colors,
            field("zoomTicks").getInt(map)>0,field("zoomScaleStable").getFloat(map));
    }
    private void dimensions(int width,int height) throws Exception {
        field("mapXMax").setInt(null,width);field("mapYMax").setInt(null,height);field("mapWidth").setInt(null,width);field("mapHeight").setInt(null,height);
    }
    private static void assertAligned(KOMEMapBorders.TerritoryFrame frame,KOMEMapViewport view) {
        assertTrue(frame.covers(view));
        for(double x:new double[]{-100,0,12.25,47.5,100,384}) {
            assertEquals(view.screenX(x),frame.view.screenX(x)*view.scaleFrom(frame.view)+view.offsetXFrom(frame.view),1e-9);
            assertEquals(view.screenY(x),frame.view.screenY(x)*view.scaleFrom(frame.view)+view.offsetYFrom(frame.view),1e-9);
        }
    }
    private static void assertSample(KOMEMapBorders.TerritoryFrame frame,KOMEMapViewport view,double mapX,double mapY,int expectedAlpha) {
        double[] alpha={0};int[] count={0};double x=view.screenX(mapX),y=view.screenY(mapY);
        frame.mesh.draw(view,frame.view,(x0,y0,x1,y1,c)->{if(x>=x0 && x<x1 && y>=y0 && y<y1){double a=(c>>>24)/255D;alpha[0]=a+alpha[0]*(1-a);count[0]++;}});
        assertEquals(expectedAlpha/255D,alpha[0],1e-12);assertTrue(count[0]<=1);
    }
    private void nativeFrame(int from,int to,int ticks,float partial) throws Exception {
        field("prevZoomPower").setInt(map,from);field("zoomPower").setInt(null,to);field("zoomTicks").setInt(map,ticks);
        map.setupZoomVariables(partial);
    }
    private static Field field(String name) throws Exception {Field f=LOTRGuiMap.class.getDeclaredField(name);f.setAccessible(true);return f;}
    private static KOMEMapBorders fixture() throws Exception {return fixture(96);}
    private static KOMEMapBorders fixture(int size) throws Exception {
        KOMETileRasterSnapshot raster=smallRaster(size);return new KOMEMapBorders.Cache().get(raster,raster.copyArgbPixels());
    }
    private static KOMETileRasterSnapshot smallRaster(int size) throws Exception {
        BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<size;y++)for(int x=0;x<size;x++)image.setRGB(x,y,x>=60 && x<64 && y<size-36?0:x<size/2?0xFF102030:0xFF405060);
        ByteArrayOutputStream png=new ByteArrayOutputStream();ImageIO.write(image,"png",png);
        return KOMETileRasterSnapshot.load(new ByteArrayInputStream(png.toByteArray()),
            new ByteArrayInputStream("16,32,48=T001\n64,80,96=T002\n".getBytes(StandardCharsets.UTF_8)),
            new KOMETileRasterSnapshot.Transform(100,0,0,1,size,size),new HashSet<>(Arrays.asList("T001","T002")),Collections.emptySet());
    }
}
