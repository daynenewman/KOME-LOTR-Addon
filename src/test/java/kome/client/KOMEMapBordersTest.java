package kome.client;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import javax.imageio.ImageIO;
import kome.common.data.*;
import lotr.client.gui.LOTRGuiMap;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEMapBordersTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private static final int A = 0xFF102030, B = 0xFF405060, C = 0xFF708090;

    @Test public void compositedCornerOpacityIsOneStrongestBandRatherThanRepeatedSourceAlpha() throws Exception {
        int[] pixels=new int[20*20];Arrays.fill(pixels,A);
        KOMEMapBorders b=borders(fixture(20,20,pixels));
        KOMEMapViewport view=new KOMEMapViewport(0,0,20,20,10,10,1);
        for(double[] sample:new double[][]{{5.25,5.25,36},{3.75,3.75,96},{2.25,2.25,176},{0.75,0.75,255},
                {5.25,2.25,176},{10,10,0}}) {
            double[] output={0,0,0,0};int[] contributions={0};
            b.drawTerritories(view,Collections.singletonMap(A & 0xFFFFFF,"gondor"),
                Collections.singletonMap(A & 0xFFFFFF,0xF9F9F9),(x0,y0,x1,y1,c)->{
                    if(sample[0]<x0 || sample[0]>=x1 || sample[1]<y0 || sample[1]>=y1)return;
                    double alpha=(c>>>24)/255D;
                    for(int channel=0;channel<3;channel++) {
                        int rgb=c>>(16-channel*8)&255;
                        output[channel]=rgb/255D*alpha+output[channel]*(1-alpha);
                    }
                    output[3]=alpha+output[3]*(1-alpha);contributions[0]++;
                });
            assertEquals("Actual source-over alpha at "+sample[0]+","+sample[1],sample[2]/255D,output[3],1e-10);
            assertEquals("White faction's premultiplied color",249/255D*sample[2]/255D,output[0],1e-10);
            assertTrue("A border sample must be submitted at most once",contributions[0]<=1);
        }
    }

    @Test public void disjointMeshRetainsEveryClippedBandOnIrregularLandAndUnownedHoles() throws Exception {
        int[] pixels=new int[24*24];
        for(int y=0;y<24;y++)for(int x=0;x<24;x++) {
            pixels[y*24+x]=(x<7 || y<9 || x>=15 && y<19)?A:B;
            if(x>=10 && x<14 && y>=4 && y<16 || x>=20 && y>=20)pixels[y*24+x]=0;
        }
        KOMEMapBorders b=borders(fixture(24,24,pixels));KOMEMapViewport view=new KOMEMapViewport(0,0,24,24,12,12,1);
        Map<Integer,String> owners=new HashMap<>();owners.put(A & 0xFFFFFF,"a");owners.put(B & 0xFFFFFF,"b");
        Map<Integer,Integer> colors=new HashMap<>();colors.put(A & 0xFFFFFF,0xF9F9F9);colors.put(B & 0xFFFFFF,0xF9F9F9);
        KOMETerritoryMesh.Rectangles raw=new KOMETerritoryMesh.Rectangles(),drawn=new KOMETerritoryMesh.Rectangles();
        Method original=KOMEMapBorders.class.getDeclaredMethod("drawTerritoriesUncached",KOMEMapViewport.class,Map.class,Map.class,KOMEMapBorders.LayerSink.class);
        original.setAccessible(true);original.invoke(b,view,owners,colors,(KOMEMapBorders.LayerSink)raw::add);
        b.drawTerritories(view,owners,colors,(x0,y0,x1,y1,c)->drawn.add(x0,y0,x1,y1,c,0));
        for(double y=0.25;y<24;y+=0.5)for(double x=0.25;x<24;x+=0.5) {
            int expected=0,count=0;double alpha=0;
            for(int i=0;i<raw.size;i++)if(contains(raw,i,x,y))expected=Math.max(expected,raw.argb[i]>>>24);
            for(int i=0;i<drawn.size;i++)if(contains(drawn,i,x,y)){double a=(drawn.argb[i]>>>24)/255D;alpha=a+alpha*(1-a);count++;}
            assertEquals(expected/255D,alpha,1e-12);assertTrue(count<=1);
            if(pixels[(int)y*24+(int)x]==0)assertEquals(0,alpha,0);
        }
    }

    private static boolean contains(KOMETerritoryMesh.Rectangles rectangles,int i,double x,double y) {
        int p=i*4;return x>=rectangles.xy[p] && x<rectangles.xy[p+2] && y>=rectangles.xy[p+1] && y<rectangles.xy[p+3];
    }

    @Test public void destinationBoundaryIsExactIndependentOfHoverAndTracksPanZoom() throws Exception {
        KOMEMapBorders borders=borders(fixture(3,1,A,B,C));
        for(double zoom:new double[]{4,16,32})for(double pan:new double[]{0,0.5,1}) {
            KOMEMapViewport view=new KOMEMapViewport(0,0,300,200,pan,0,zoom);
            List<double[]> quads=new ArrayList<double[]>();
            borders.drawDestination(view,B&0xFFFFFF,(x0,y0,x1,y1,color)->{
                assertEquals(0xFF69DDE8,color);quads.add(new double[]{x0,y0,x1,y1});
            });
            assertEquals(4,quads.size());int vertical=0;
            for(double[] q:quads)if(q[2]-q[0]==3) {
                double mapX=view.mapX((q[0]+q[2])/2);
                assertTrue(mapX==1||mapX==2);vertical++;
            }
            assertEquals(2,vertical);
        }
        final int[] count={0};borders.drawDestination(new KOMEMapViewport(0,0,300,200,0,0,1),0,(a,b,c,d,e)->count[0]++);
        assertEquals(0,count[0]);
    }
    @Test public void differentNeighborsHaveOneExactSharedEdgeAndNoMutation() throws Exception {
        KOMETileRasterSnapshot s = fixture(2, 1, A, B);
        int[] copy = s.copyArgbPixels(), before = copy.clone();
        KOMEMapBorders borders = new KOMEMapBorders.Cache().get(s, copy);
        assertArrayEquals(before, copy);
        assertArrayEquals(before, s.copyArgbPixels());
        List<String> edges = units(borders);
        assertEquals(7, edges.size());
        assertEquals(1, Collections.frequency(edges, key(true, 1, 0, A, B)));
        assertEquals(brute(copy, 2, 1), new HashSet<String>(edges));
        Arrays.fill(copy, 0);
        assertEquals(edges, units(borders)); // Cache owns only its immutable derived primitive arrays.
    }

    @Test public void sameTileHasNoInternalEdgeAndOuterEdgesAreExplicit() throws Exception {
        KOMEMapBorders b = borders(fixture(3, 2, A,A,A,A,A,A));
        assertEquals(4, b.edgeRuns());
        assertEquals(10, units(b).size());
        for (String edge : units(b)) assertFalse(edge.endsWith("/" + A + "/" + A));
    }

    @Test public void alphaDifferencesDoNotInventTileBoundaries() throws Exception {
        assertEquals(4,borders(fixture(2,1,A,0x40102030)).edgeRuns());
    }

    @Test public void hoverRestylesSingleSharedEdgeAndNeverChangesGeometry() throws Exception {
        KOMEMapBorders b=borders(fixture(2,1,A,B));
        List<String> before=units(b);
        KOMEMapViewport v=new KOMEMapViewport(0,0,40,20,1,0.5,20);
        final int[] shared={0};
        b.drawBorders(v,A&0xFFFFFF,(x0,y0,x1,y1,color)->{
            if(x0<20&&x1>20&&y1-y0==20){assertEquals(KOMEMapBorders.HOVER_COLOR,color);assertEquals(1.5,x1-x0,0);shared[0]++;}
        });
        assertEquals(1,shared[0]);assertEquals(before,units(b));
    }

    @Test public void allOuterEdgesPaintInsideTheMapWithoutOffMapQuads() throws Exception {
        KOMEMapBorders b=borders(fixture(1,1,A));
        KOMEMapViewport v=new KOMEMapViewport(0,0,100,100,0,0,10);
        final int[] count={0};
        b.drawBorders(v,0,(x0,y0,x1,y1,c)->{assertTrue(x0>=50&&y0>=50&&x1<=60&&y1<=60);count[0]++;});
        assertEquals(4,count[0]);
        count[0]=0;b.drawBorders(new KOMEMapViewport(0,0,10,10,-100,-100,10),0,(x0,y0,x1,y1,c)->count[0]++);
        assertEquals(0,count[0]);
    }

    @Test public void riverGapRetainsFullWidthEvenWithHighlightedBank() throws Exception {
        KOMETileRasterSnapshot s = fixture(4, 1, A,0,0,B);
        KOMEMapBorders b = borders(s);
        KOMEMapViewport v = new KOMEMapViewport(0,0,40,10,2,0.5,10);
        List<double[]> quads = new ArrayList<double[]>();
        b.drawBorders(v, A & 0xFFFFFF, (x0,y0,x1,y1,color) -> quads.add(new double[] {x0,y0,x1,y1,color}));
        assertFalse(quads.isEmpty());
        for (double[] q : quads) assertTrue("Stroke entered river: " + Arrays.toString(q), q[2] <= 10 || q[0] >= 30);
        b.drawHighlight(v, A & 0xFFFFFF, (x0,y0,x1,y1,color) -> assertTrue(x1 <= 10));
        assertEquals(KOMETileResolution.Status.IN_BOUNDS_GAP, s.resolve(100,1,0).status);
        assertEquals(KOMETileResolution.Status.IN_BOUNDS_GAP, s.resolve(100,2,0).status);
    }

    @Test public void junctionDiagonalStairStepsAndTransparentRgbMatchBruteForce() throws Exception {
        for (int[] pixels : new int[][] {
                {A,A,B, A,C,B, C,C,B}, {A,A,B, A,B,B, B,B,B},
                {A,0x00102030,B, A,0x10405060,B, A,0,B}}) {
            KOMETileRasterSnapshot s = fixture(3,3,pixels);
            List<String> actual = units(borders(s));
            assertEquals(new HashSet<String>(actual).size(), actual.size());
            assertEquals(brute(s.copyArgbPixels(),3,3), new HashSet<String>(actual));
        }
    }

    @Test public void viewportCullingEqualsBruteVisibleSegmentFilter() throws Exception {
        KOMEMapBorders b = borders(fixture(4,3,A,A,B,B, A,C,C,B,0,C,0,B));
        List<String> all = new ArrayList<String>();
        b.visitEdges(-1,-1,5,4,(v,l,a,z,c,d)->all.add(v+","+l+","+a+","+z+","+c+","+d));
        Random random = new Random(420);
        for (int trial=0;trial<60;trial++) {
            double left=random.nextDouble()*7-2, top=random.nextDouble()*6-2;
            double right=left+0.1+random.nextDouble()*3,bottom=top+0.1+random.nextDouble()*3;
            Set<String> expected=new HashSet<String>(),actual=new HashSet<String>();
            for (String segment:all) {
                String[] f=segment.split(",");boolean v=Boolean.parseBoolean(f[0]);int l=Integer.parseInt(f[1]),a=Integer.parseInt(f[2]),z=Integer.parseInt(f[3]);
                if(v ? l>=left&&l<=right&&a<bottom&&z>top : l>=top&&l<=bottom&&a<right&&z>left) expected.add(segment);
            }
            b.visitEdges(left,top,right,bottom,(v,l,a,z,c,d)->actual.add(v+","+l+","+a+","+z+","+c+","+d));
            assertEquals(expected,actual);
        }
    }

    @Test public void constantGuiStrokeAndClipAcrossZoomPanAndResize() throws Exception {
        KOMEMapBorders b = borders(fixture(2,1,A,B));
        for (double zoom : new double[] {2,4,16}) for (int width : new int[] {101,202,801}) {
            KOMEMapViewport v = new KOMEMapViewport(10,20,10+width,121,1,0.5,zoom);
            double at=v.screenX(1);
            final int[] shared={0};
            b.drawBorders(v,0,(x0,y0,x1,y1,color)->{
                assertTrue(x0>=v.left&&x1<=v.right&&y0>=v.top&&y1<=v.bottom);
                if(x0<at&&x1>at&&y1-y0==zoom) {shared[0]++;assertEquals(1.0,x1-x0,0);assertEquals(at,(x1+x0)/2,0);}
            });
            assertEquals(1,shared[0]);
            assertEquals(1,v.mapX(at),0);
        }
    }

    @Test public void anisotropicMaskToMapScaleUsesAuthoritativeTransform() throws Exception {
        BufferedImage image = new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,A); image.setRGB(1,0,B);
        KOMETileRasterSnapshot s=load(image,new KOMETileRasterSnapshot.Transform(100,2,3,128,4,3),Collections.<String>emptySet());
        KOMEMapBorders b=borders(s);
        KOMEMapViewport v=new KOMEMapViewport(0,0,40,30,2,1.5,10);
        final int[] found={0};b.drawBorders(v,0,(x0,y0,x1,y1,c)->{if(x0==19.5&&x1==20.5&&y0==0&&y1==30)found[0]++;});
        assertEquals(1,found[0]);
        assertEquals("T001",s.resolve(100,-1,-384).tileId);
        assertEquals("T002",s.resolve(100,0,-384).tileId);
    }

    @Test public void realNegativeCoordinatesAndAllMaskCornersUseSharedOverlayTransform() throws Exception {
        KOMETileRasterSnapshot s=KOMETileTestResources.real();
        String[] names={"mapXMin","mapXMax","mapYMin","mapYMax"};int[] saved=new int[4];
        LOTRGuiMap map=kome.common.KOMEAccessFixture.allocate(LOTRGuiMap.class);
        try {
            for(int i=0;i<4;i++) saved[i]=field(LOTRGuiMap.class,names[i]).getInt(null);
            for(int[] bounds:new int[][]{{10,411,20,321},{0,1280,0,720}}) {
                for(int i=0;i<4;i++)field(LOTRGuiMap.class,names[i]).setInt(null,bounds[i]);
                for(float zoom:new float[]{0.5F,1,4,8}) {
                    field(LOTRGuiMap.class,"posX").setFloat(map,810.25F);
                    field(LOTRGuiMap.class,"posY").setFloat(map,730.75F);
                    field(LOTRGuiMap.class,"zoomScale").setFloat(map,zoom);
                    KOMEMapViewport v=KOMEConquestMapOverlay.viewport(map);
                    for(double x:new double[]{0,809,810,975,3200})for(double y:new double[]{0,727,730,4000}) {
                        assertEquals(x,v.mapX(v.screenX(x)),0);
                        assertEquals(y,v.mapY(v.screenY(y)),0);
                    }
                    assertEquals(-128,(809-s.transform.originX)*s.transform.scale);
                    assertEquals(-384,(727-s.transform.originZ)*s.transform.scale);
                }
            }
        } finally {for(int i=0;i<4;i++)field(LOTRGuiMap.class,names[i]).setInt(null,saved[i]);}
    }

    @Test public void productionRenderGuardSkipsCollapsedOrNonfiniteViewportDuringResize() throws Exception {
        LOTRGuiMap map=kome.common.KOMEAccessFixture.allocate(LOTRGuiMap.class);
        String[] names={"mapXMin","mapXMax","mapYMin","mapYMax"};int[] old=new int[4];
        Method guard=KOMEConquestMapOverlay.class.getDeclaredMethod("shouldSkipMap",LOTRGuiMap.class);guard.setAccessible(true);
        try {
            for(int i=0;i<4;i++){old[i]=field(LOTRGuiMap.class,names[i]).getInt(null);field(LOTRGuiMap.class,names[i]).setInt(null,i%2==0?10:110);}
            field(LOTRGuiMap.class,"zoomScale").setFloat(map,1);
            assertEquals(false,guard.invoke(null,map));
            field(LOTRGuiMap.class,"mapXMax").setInt(null,10);assertEquals(true,guard.invoke(null,map));
            field(LOTRGuiMap.class,"mapXMax").setInt(null,110);
            field(LOTRGuiMap.class,"mapYMax").setInt(null,5);assertEquals(true,guard.invoke(null,map));
            field(LOTRGuiMap.class,"mapYMax").setInt(null,110);
            field(LOTRGuiMap.class,"zoomScale").setFloat(map,Float.NaN);assertEquals(true,guard.invoke(null,map));
            field(LOTRGuiMap.class,"zoomScale").setFloat(map,1);assertEquals(false,guard.invoke(null,map));
        } finally {for(int i=0;i<4;i++)field(LOTRGuiMap.class,names[i]).setInt(null,old[i]);}
    }

    @Test public void snapshotReplacementFailureAndExplicitClearNeverReuseOldBorders() throws Exception {
        KOMETileRasterSnapshot s=fixture(2,1,A,B), changed=fixture(2,1,A,A);
        KOMEMapBorders.Cache cache=new KOMEMapBorders.Cache();
        KOMEMapBorders old=cache.get(s,s.copyArgbPixels());
        assertSame(old,cache.get(s,null));
        KOMEMapBorders next=cache.get(changed,changed.copyArgbPixels());
        assertNotSame(old,next);assertEquals(4,next.edgeRuns());
        assertNull(cache.get(null,null));
        assertNotSame(next,cache.get(changed,changed.copyArgbPixels()));
        try {cache.get(s,new int[0]);fail();}catch(IllegalArgumentException expected) { }
        assertNull(cache.get(null,null));
        assertEquals(7,cache.get(s,s.copyArgbPixels()).edgeRuns());
    }

    @Test public void productionDisconnectReloadAndMissingResolverClearStaleGeometry() throws Exception {
        KOMETileRasterSnapshot s=KOMETileTestResources.real();
        Field raster=field(KOMEConquestMapOverlay.class,"tileRaster"), edges=field(KOMEConquestMapOverlay.class,"mapBorders");
        Object oldRaster=raster.get(null),oldEdges=edges.get(null);
        try {
            for(int action=0;action<3;action++) {
                KOMEMapBorders cached=borders(fixture(1,1,A));
                for(double zoom:new double[]{10,20,40})cached.territoryFrame(new KOMEMapViewport(0,0,40,20,.5,.5,zoom),
                    Collections.singletonMap(A & 0xFFFFFF,"gondor"),Collections.singletonMap(A & 0xFFFFFF,0xF9F9F9)).mesh.vertices();
                assertTrue(cached.retainedTerritoryBytes()>0);
                raster.set(null,s);edges.set(null,cached);
                if(action==0)KOMEConquestMapOverlay.resetClientMapState();
                if(action==1)new KOMEConquestMapOverlay().onResourceManagerReload(null);
                if(action==2){KOMETileWorldResolver.INSTANCE.invalidate();Method m=KOMEConquestMapOverlay.class.getDeclaredMethod("ensureTileMaskLoaded");m.setAccessible(true);assertEquals(false,m.invoke(null));}
                assertNull(raster.get(null));assertNull(edges.get(null));
                assertEquals(0,cached.retainedTerritoryBytes());assertEquals(0,cached.retainedTerritoryFrames());
                assertTrue(((Map<?,?>)field(KOMEConquestMapOverlay.class,"tileColorsById").get(null)).isEmpty());
            }
        } finally {raster.set(null,oldRaster);edges.set(null,oldEdges);}
    }

    @Test public void retiredColorsRemainGapAndHaveNoPrivateMetadataInCache() throws Exception {
        BufferedImage image=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,A);image.setRGB(1,0,B);
        KOMETileRasterSnapshot s=load(image,new KOMETileRasterSnapshot.Transform(100,0,0,1,2,1),Collections.singleton("T002"));
        assertEquals(KOMETileResolution.Status.IN_BOUNDS_GAP,s.resolve(100,1,0).status);
        KOMEMapBorders b=borders(s);assertEquals(4,b.edgeRuns());
        for(String edge:units(b))assertFalse(edge.contains("/"+B));
        String overlay=new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/KOMEConquestMapOverlay.java")),StandardCharsets.UTF_8);
        assertFalse(overlay.contains("reset_conquest_borders_thin.png"));
        assertFalse(overlay.contains("isTileEdge("));
        assertTrue(overlay.contains("KOMETileWorldResolver.INSTANCE"));
    }

    @Test public void pathologicalCheckerboardFailsBoundedConstruction() throws Exception {
        int[] pixels=new int[800*800];for(int y=0;y<800;y++)for(int x=0;x<800;x++)pixels[y*800+x]=((x+y)&1)==0?A:B;
        KOMETileRasterSnapshot s=fixture(800,800,pixels);
        try{borders(s);fail("Run cap must reject pathological presentation");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("exceeds"));}
        assertEquals("T001",s.resolve(100,0,0).tileId); // Failure cannot invalidate canonical lookup.
    }

    @Test public void actualV2BoundariesMatchEveryPixelAndProduceBoundedPresentationReport() throws Exception {
        KOMETileRasterSnapshot s=KOMETileTestResources.real();int[] pixels=s.copyArgbPixels();
        long start=System.nanoTime();KOMEMapBorders b=new KOMEMapBorders.Cache().get(s,pixels);long nanos=System.nanoTime()-start;
        long expected=0;for(int y=0;y<s.height;y++)for(int x=0;x<s.width;x++) {
            int a=label(pixels[y*s.width+x]);
            if(a!=labelAt(pixels,s.width,s.height,x-1,y))expected++;
            if(a!=labelAt(pixels,s.width,s.height,x,y-1))expected++;
            if(x==s.width-1&&a!=0)expected++;
            if(y==s.height-1&&a!=0)expected++;
        }
        final long[] actual={0};
        b.visitEdges(-1,-1,s.width+1,s.height+1,(v,l,a,z,c,d)->{
            actual[0]+=z-a;
            for(int i=a;i<z;i++) {
                assertEquals(c,v?labelAt(pixels,s.width,s.height,l-1,i):labelAt(pixels,s.width,s.height,i,l-1));
                assertEquals(d,v?labelAt(pixels,s.width,s.height,l,i):labelAt(pixels,s.width,s.height,i,l));
                assertNotEquals(c,d);
            }
        });assertEquals(expected,actual[0]);
        assertRealEdge(b,s,975,727,"T149","T132");
        assertRealEdge(b,s,957,746,"T171","T132");
        assertRealEdge(b,s,950,713,"T171","T149");
        assertEquals(KOMETileResolution.Status.IN_BOUNDS_GAP,s.resolve(s.transform.dimension,34944,640).status);
        assertEquals(KOMETileResolution.Status.IN_BOUNDS_GAP,s.resolve(s.transform.dimension,189568,-86016).status);
        assertTrue(b.primitiveBytes()<16_100_000);
        Path report=Paths.get("build/reports/tile-borders");Files.createDirectories(report);
        List<String> stats=new ArrayList<String>();
        stats.add("preprocessMillis="+nanos/1_000_000.0);stats.add("edgeRuns="+b.edgeRuns());stats.add("fillRuns="+b.fillRuns());stats.add("primitiveBytes="+b.primitiveBytes());stats.add("unitEdges="+actual[0]);
        stats.add("java="+System.getProperty("java.version"));stats.add("os="+System.getProperty("os.name"));stats.add("processors="+Runtime.getRuntime().availableProcessors());
        Map<Integer,String> owners=new HashMap<>();Map<Integer,Integer> colors=new HashMap<>();
        // Synthetic alternating ownership on real geometry, not a claim about a server snapshot.
        for(Map.Entry<String,Integer> tile:s.colorsById().entrySet()) {
            boolean dark=(tile.getKey().hashCode() & 1)==0;owners.put(tile.getValue(),dark?"mordor":"gondor");
            colors.put(tile.getValue(),dark?0x351F1F:0xF9F9F9);
        }
        Map<Integer,String> packagedOwners=new HashMap<>();Map<Integer,Integer> packagedColors=new HashMap<>();
        for(String line:Files.readAllLines(Paths.get("src/main/resources/assets/kome/config/kome_tile_ownership_defaults.csv"),StandardCharsets.UTF_8)) {
            String[] fields=line.split(",",3);Integer mask=s.colorsById().get(fields[0]);
            if(mask==null || fields.length<2 || fields[1].isEmpty())continue;
            String owner=KOMEAlliance.normalizeFactionKey(fields[1]);if(owner.isEmpty())continue;
            packagedOwners.put(mask,owner);packagedColors.put(mask,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(owner));
        }
        for(double zoom:new double[]{0.25,0.5,1,2,4,8}) {
            KOMEMapViewport v=new KOMEMapViewport(0,0,1200,700,1000,730,zoom);final int[] counts={0,0};
            b.drawBorders(v,0,(x0,y0,x1,y1,c)->counts[0]++);
            b.drawHighlight(v,s.colorsById().get("T132"),(x0,y0,x1,y1,c)->counts[1]++);
            stats.add("zoom="+zoom+" borderQuads="+counts[0]+" hoverQuads="+counts[1]);
            final int[] territoryQuads={0};long drawStart=System.nanoTime();
            b.drawTerritories(v,owners,colors,(x0,y0,x1,y1,c)->{
                assertTrue(x0>=v.left && x1<=v.right && y0>=v.top && y1<=v.bottom);territoryQuads[0]++;
            });
            stats.add("zoom="+zoom+" territoryQuads="+territoryQuads[0]+" territoryCpuMillis="+(System.nanoTime()-drawStart)/1_000_000.0);
            recordMeshMetrics(stats,b,v,owners,colors,"fragmented");
            territoryQuads[0]=0;drawStart=System.nanoTime();
            b.drawTerritories(v,packagedOwners,packagedColors,(x0,y0,x1,y1,c)->territoryQuads[0]++);
            stats.add("zoom="+zoom+" packagedOwnerQuads="+territoryQuads[0]+" packagedOwnerCpuMillis="+(System.nanoTime()-drawStart)/1_000_000.0);
            territoryQuads[0]=0;drawStart=System.nanoTime();
            b.drawTerritories(v,packagedOwners,packagedColors,(x0,y0,x1,y1,c)->territoryQuads[0]++);
            stats.add("zoom="+zoom+" cachedPackagedOwnerQuads="+territoryQuads[0]+" cachedPackagedOwnerCpuMillis="+(System.nanoTime()-drawStart)/1_000_000.0);
            recordMeshMetrics(stats,b,v,packagedOwners,packagedColors,"packaged");
            if(zoom==0.25) {
                KOMEMapBorders.TerritoryFrame before=b.territoryFrame(v,packagedOwners,packagedColors);
                long rebuildStart=System.nanoTime();
                KOMEMapBorders.TerritoryFrame outside=b.territoryFrame(new KOMEMapViewport(v.left,v.top,v.right,v.bottom,v.posX+96/v.zoom,v.posY,v.zoom),packagedOwners,packagedColors);
                assertNotSame(before,outside);stats.add("pan96RebuildCpuMillis="+(System.nanoTime()-rebuildStart)/1_000_000.0);
                rebuildStart=System.nanoTime();
                assertNotSame(outside,b.territoryFrame(new KOMEMapViewport(v.left,v.top,v.right,v.bottom,v.posX,v.posY,v.zoom*2),packagedOwners,packagedColors));
                stats.add("zoomRebuildCpuMillis="+(System.nanoTime()-rebuildStart)/1_000_000.0);
            }
        }
        Files.write(report.resolve("metrics.txt"),stats,StandardCharsets.UTF_8);
        StringBuilder output=new StringBuilder("region\tleft\ttop\tright\tbottom\tlayer\tx0\ty0\tx1\ty1\targb\n");
        int[][] regions={{955,715,995,755},{938,730,978,770},{1063,715,1103,755},{930,693,970,733}};
        String[] names={"pilot","land","river","junction"};
        for(int n=0;n<regions.length;n++) {
            final int[] r=regions[n];final String name=names[n];
            KOMEMapViewport v=new KOMEMapViewport(0,0,600,600,(r[0]+r[2])/2.0,(r[1]+r[3])/2.0,15);
            b.drawBorders(v,0,(x0,y0,x1,y1,c)->output.append(name+"\t"+r[0]+"\t"+r[1]+"\t"+r[2]+"\t"+r[3]+"\tborder\t"+x0+"\t"+y0+"\t"+x1+"\t"+y1+"\t"+Integer.toHexString(c)+"\n"));
        }
        KOMEMapViewport selected=new KOMEMapViewport(0,0,600,600,958,750,15);
        b.drawHighlight(selected,s.colorsById().get("T132"),(x0,y0,x1,y1,c)->output.append("selected\t938\t730\t978\t770\tfill\t"+x0+"\t"+y0+"\t"+x1+"\t"+y1+"\t"+Integer.toHexString(c)+"\n"));
        b.drawBorders(selected,s.colorsById().get("T132"),(x0,y0,x1,y1,c)->output.append("selected\t938\t730\t978\t770\tborder\t"+x0+"\t"+y0+"\t"+x1+"\t"+y1+"\t"+Integer.toHexString(c)+"\n"));
        Files.write(report.resolve("preview-quads.tsv"),output.toString().getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(pixels,s.copyArgbPixels());
    }

    @Test public void territoryBordersSuppressSameOwnerButKeepDifferentOwnersWithIdenticalColors() throws Exception {
        KOMEMapBorders b = borders(fixture(2,1,A,B));
        Map<Integer,String> owners = new HashMap<>();
        Map<Integer,Integer> colors = new HashMap<>();
        owners.put(A & 0xFFFFFF,"gondor"); owners.put(B & 0xFFFFFF,"gondor");
        colors.put(A & 0xFFFFFF,0xFFFFFF); colors.put(B & 0xFFFFFF,0xFFFFFF);
        KOMEMapViewport v = new KOMEMapViewport(0,0,40,20,1,0.5,20);
        final int[] shared = {0};
        KOMEMapBorders.QuadSink sink = (x0,y0,x1,y1,c) -> {
            if (x0<=19.25 && x1>19.25 && y0<=10 && y1>10) shared[0]++;
            assertTrue(x0>=0 && x1<=40 && y0>=0 && y1<=20);
        };
        b.drawTerritories(v,owners,colors,sink); assertEquals(0,shared[0]);
        owners.put(B & 0xFFFFFF,"isengard");
        b.drawTerritories(v,owners,colors,sink); assertEquals(1,shared[0]);
        List<Integer> strokes = new ArrayList<>();
        b.drawBorders(v,0,true,(x0,y0,x1,y1,c)->strokes.add(c)); assertTrue(strokes.isEmpty());
        b.drawBorders(v,A & 0xFFFFFF,true,(x0,y0,x1,y1,c)->strokes.add(c));
        assertFalse(strokes.isEmpty()); for(int c:strokes) assertEquals(KOMEMapBorders.HOVER_COLOR,c);
    }

    @Test public void territoryBordersKeepGapClearAndContrastBothBlackAndWhite() throws Exception {
        KOMEMapBorders b = borders(fixture(3,1,A,0,B));
        Map<Integer,String> owners = new HashMap<>(); Map<Integer,Integer> colors = new HashMap<>();
        owners.put(A & 0xFFFFFF,"mordor");owners.put(B & 0xFFFFFF,"gondor");
        colors.put(A & 0xFFFFFF,0);colors.put(B & 0xFFFFFF,0xFFFFFF);
        Set<Integer> strokes = new HashSet<>();
        b.drawTerritories(new KOMEMapViewport(0,0,30,10,1.5,0.5,10),owners,colors,(x0,y0,x1,y1,c)->{
            assertTrue(x1<=10 || x0>=20); strokes.add(c);
        });
        assertTrue(strokes.contains(0xFF000000));assertTrue(strokes.contains(0xFFFFFFFF));
        assertTrue(strokes.contains(0xFFB0AD9F));assertFalse(strokes.contains(0xFF161418));
        for(int c:strokes)assertTrue((c>>>24)==255 || (c>>>24)==176 || (c>>>24)==96 || (c>>>24)==36);
        final int[] count={0};b.drawTerritories(new KOMEMapViewport(0,0,30,10,1.5,0.5,10),
            Collections.emptyMap(),colors,(x0,y0,x1,y1,c)->count[0]++);assertEquals(0,count[0]);
    }

    @Test public void wideFactionBandsFadeInwardAcrossRasterCellsWithoutDarkCasing() throws Exception {
        int[] pixels=new int[20*20];Arrays.fill(pixels,A);
        KOMEMapBorders b=borders(fixture(20,20,pixels));
        Map<Integer,String> owners=Collections.singletonMap(A & 0xFFFFFF,"gondor");
        Map<Integer,Integer> colors=Collections.singletonMap(A & 0xFFFFFF,0xF9F9F9);
        Set<String> bands=new HashSet<>();
        b.drawTerritories(new KOMEMapViewport(0,0,20,20,10,10,1),owners,colors,(x0,y0,x1,y1,c)->{
            assertEquals(0xF9F9F9,c & 0xFFFFFF);
            assertTrue(x0>=0 && y0>=0 && x1<=20 && y1<=20);
            if(y0<=9 && y1>=10 && x1<=6)bands.add(x0+":"+x1+":"+(c>>>24));
        });
        assertTrue(bands.contains("0.0:1.5:255"));assertTrue(bands.contains("1.5:3.0:176"));
        assertTrue(bands.contains("3.0:4.5:96"));assertTrue(bands.contains("4.5:6.0:36"));
        final int[] cues={0};
        b.drawDestination(new KOMEMapViewport(0,0,20,20,10,10,1),A & 0xFFFFFF,(x0,y0,x1,y1,c)->cues[0]++);
        assertTrue(cues[0]>0);
    }

    @Test public void productionTileEdgePreferencePreservesHoverAndFactionBoundaries() throws Exception {
        Field geometry=field(KOMEConquestMapOverlay.class,"mapBorders");Object oldGeometry=geometry.get(null);
        Field ownerField=field(KOMEConquestMapOverlay.class,"territoryOwners"),colorField=field(KOMEConquestMapOverlay.class,"territoryColors");
        Object oldOwners=ownerField.get(null),oldColors=colorField.get(null);
        KOMETerritoryStyle style=KOMEConquestMapOverlay.territoryStyle;
        KOMETerritoryStyle.DisplayMode oldMode=style.mode;boolean oldEdges=style.showTileBorders;
        try {
            geometry.set(null,borders(fixture(2,1,A,B)));
            Map<Integer,String> owners=new HashMap<>();owners.put(A & 0xFFFFFF,"gondor");owners.put(B & 0xFFFFFF,"mordor");ownerField.set(null,owners);
            Map<Integer,Integer> colors=new HashMap<>();colors.put(A & 0xFFFFFF,0xF9F9F9);colors.put(B & 0xFFFFFF,0x351F1F);colorField.set(null,colors);
            KOMEMapViewport view=new KOMEMapViewport(0,0,40,20,1,0.5,20);
            List<Integer> drawn=new ArrayList<>();KOMEMapBorders.QuadSink sink=(x0,y0,x1,y1,c)->drawn.add(c);
            style.mode=KOMETerritoryStyle.DisplayMode.FULL_COLOR;style.showTileBorders=false;
            KOMEConquestMapOverlay.drawTerritoryGeometry(view,0,sink);assertTrue(drawn.isEmpty());
            KOMEConquestMapOverlay.drawTerritoryGeometry(view,A & 0xFFFFFF,sink);
            assertFalse(drawn.isEmpty());for(int c:drawn)assertEquals(KOMEMapBorders.HOVER_COLOR,c);
            drawn.clear();style.showTileBorders=true;
            KOMEConquestMapOverlay.drawTerritoryGeometry(view,0,sink);assertTrue(drawn.contains(KOMEMapBorders.BORDER_COLOR));
            drawn.clear();style.mode=KOMETerritoryStyle.DisplayMode.BORDERS_ONLY;style.showTileBorders=false;
            KOMEConquestMapOverlay.drawTerritoryGeometry(view,0,sink);
            assertTrue(drawn.contains(0xFFF9F9F9));assertTrue(drawn.contains(0xFF351F1F));
            assertFalse(drawn.contains(KOMEMapBorders.BORDER_COLOR));
            drawn.clear();style.showTileBorders=true;KOMEConquestMapOverlay.drawTerritoryGeometry(view,0,sink);
            assertTrue(drawn.contains(KOMEMapBorders.BORDER_COLOR));assertTrue(drawn.contains(0xFFF9F9F9));
        } finally {geometry.set(null,oldGeometry);ownerField.set(null,oldOwners);colorField.set(null,oldColors);style.mode=oldMode;style.showTileBorders=oldEdges;}
    }

    @Test public void wideBorderFrameCacheTracksViewOwnershipColorsAndFailedConstruction() throws Exception {
        KOMEMapBorders b=borders(fixture(2,1,A,B));
        Field frame=field(KOMEMapBorders.class,"territoryFrame");
        Map<Integer,String> owners=new HashMap<>();owners.put(A & 0xFFFFFF,"gondor");owners.put(B & 0xFFFFFF,"mordor");
        Map<Integer,Integer> colors=new HashMap<>();colors.put(A & 0xFFFFFF,0xF9F9F9);colors.put(B & 0xFFFFFF,0x351F1F);
        KOMEMapViewport view=new KOMEMapViewport(0,0,40,20,1,0.5,20);
        List<String> first=new ArrayList<>(),second=new ArrayList<>();
        b.drawTerritories(view,owners,colors,(x0,y0,x1,y1,c)->first.add(x0+","+y0+","+x1+","+y1+","+c));
        Object cached=frame.get(b);assertNotNull(cached);
        b.drawTerritories(new KOMEMapViewport(0,0,40,20,1,0.5,20),new HashMap<>(owners),new HashMap<>(colors),
            (x0,y0,x1,y1,c)->second.add(x0+","+y0+","+x1+","+y1+","+c));
        assertSame(cached,frame.get(b));assertEquals(first,second);
        KOMEMapBorders.TerritoryFrame retained=(KOMEMapBorders.TerritoryFrame)cached;
        java.nio.ByteBuffer vertices=retained.mesh.vertices();
        KOMEMapViewport panned=new KOMEMapViewport(0,0,40,20,1+32D/20,0.5,20);
        assertSame(retained,b.territoryFrame(panned,owners,colors));assertSame(vertices,retained.mesh.vertices());
        assertSame(retained,b.territoryFrame(new KOMEMapViewport(-5,-5,45,25,1,0.5,20),owners,colors));
        colors.put(A & 0xFFFFFF,0x123456);b.drawTerritories(view,owners,colors,(x0,y0,x1,y1,c)->{});
        assertNotSame(cached,frame.get(b));cached=frame.get(b);
        owners.put(B & 0xFFFFFF,"gondor");b.drawTerritories(view,owners,colors,(x0,y0,x1,y1,c)->{});
        assertNotSame(cached,frame.get(b));cached=frame.get(b);
        view=new KOMEMapViewport(0,0,40,20,1,0.5,10);
        try{b.drawTerritories(view,owners,colors,(x0,y0,x1,y1,c)->{throw new IllegalStateException("sink");});fail();}
        catch(IllegalStateException expected){}
        assertNull(frame.get(b));b.drawTerritories(view,owners,colors,(x0,y0,x1,y1,c)->{});
        assertNotSame(cached,frame.get(b));assertNotNull(frame.get(b));
        Object beforePan=frame.get(b);
        b.territoryFrame(new KOMEMapViewport(0,0,40,20,1+96D/10,0.5,10),owners,colors);
        assertNotSame(beforePan,frame.get(b));
    }

    private static void assertRealEdge(KOMEMapBorders b,KOMETileRasterSnapshot s,int x,int y,String left,String right) {
        final int[] found={0};b.visitEdges(x,y,x+0.1,y+1,(v,l,a,z,c,d)->{
            if(v&&l==x&&a<=y&&z>y){assertEquals(s.colorsById().get(left).intValue(),c&0xFFFFFF);assertEquals(s.colorsById().get(right).intValue(),d&0xFFFFFF);found[0]++;}
        });assertEquals(1,found[0]);
    }

    private static void recordMeshMetrics(List<String> stats,KOMEMapBorders b,KOMEMapViewport view,
            Map<Integer,String> owners,Map<Integer,Integer> colors,String fixture) {
        KOMEMapBorders.TerritoryFrame frame=b.territoryFrame(view,owners,colors);
        assertTrue("Both real-raster fixtures must retain their complete meshes",frame.mesh.cacheable());
        long start=System.nanoTime();java.nio.ByteBuffer vertices=frame.mesh.vertices();
        stats.add("zoom="+view.zoom+" fixture="+fixture+" submittedQuads="+frame.mesh.quads()+" vertices="+frame.mesh.quads()*4
            +" drawArraysCalls="+(frame.mesh.quads()==0?0:1)+" vertexPreparationCpuMillis="+(System.nanoTime()-start)/1_000_000.0
            +" retainedPrimitiveBytes="+frame.mesh.retainedBytes());
        KOMEMapViewport pan=new KOMEMapViewport(view.left,view.top,view.right,view.bottom,view.posX+32/view.zoom,view.posY,view.zoom);
        long checksum=0;boolean reused=true;start=System.nanoTime();
        for(int i=0;i<512;i++) {
            KOMEMapBorders.TerritoryFrame current=b.territoryFrame((i&1)==0?view:pan,owners,colors);
            reused &= current==frame && current.mesh.vertices()==vertices;checksum+=current.mesh.vertices().remaining();
        }
        double micros=(System.nanoTime()-start)/512_000D;
        assertTrue(reused);assertEquals((long)vertices.remaining()*512,checksum);
        stats.add("zoom="+view.zoom+" fixture="+fixture+" retainedIdleAndPan32PreparationCpuMicros="+micros+" pan32Rebuilds=0");
    }
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static KOMEMapBorders borders(KOMETileRasterSnapshot s){return new KOMEMapBorders.Cache().get(s,s.copyArgbPixels());}
    private static KOMETileRasterSnapshot fixture(int width,int height,int... pixels)throws Exception{
        BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,width,height,pixels,0,width);
        return load(image,new KOMETileRasterSnapshot.Transform(100,0,0,1,width,height),Collections.<String>emptySet());
    }
    private static KOMETileRasterSnapshot load(BufferedImage image,KOMETileRasterSnapshot.Transform t,Set<String> retired)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        return KOMETileRasterSnapshot.load(new ByteArrayInputStream(bytes.toByteArray()),new ByteArrayInputStream("16,32,48=T001\n64,80,96=T002\n112,128,144=T003\n".getBytes(StandardCharsets.UTF_8)),t,new HashSet<String>(Arrays.asList("T001","T002","T003")),retired);
    }
    private static int label(int c){return(c>>>24)>24?0xFF000000|(c&0xFFFFFF):0;}
    private static int labelAt(int[] p,int w,int h,int x,int y){return x<0||y<0||x>=w||y>=h?0:label(p[y*w+x]);}
    private static String key(boolean v,int line,int along,int a,int b){return(v?"V":"H")+"/"+line+"/"+along+"/"+a+"/"+b;}
    private static List<String> units(KOMEMapBorders b){List<String> result=new ArrayList<String>();b.visitEdges(-1,-1,b.width+1,b.height+1,(v,l,a,z,c,d)->{for(int i=a;i<z;i++)result.add(key(v,l,i,c,d));});return result;}
    private static Set<String> brute(int[] p,int w,int h){Set<String> r=new HashSet<String>();for(int y=0;y<=h;y++)for(int x=0;x<=w;x++){
        if(y<h){int a=labelAt(p,w,h,x-1,y),b=labelAt(p,w,h,x,y);if(a!=b)r.add(key(true,x,y,a,b));}
        if(x<w){int a=labelAt(p,w,h,x,y-1),b=labelAt(p,w,h,x,y);if(a!=b)r.add(key(false,y,x,a,b));}
    }return r;}
}
