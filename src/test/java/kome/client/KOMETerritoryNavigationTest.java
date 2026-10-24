package kome.client;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import kome.common.data.KOMETileRasterSnapshot;
import kome.common.data.KOMETileTestResources;
import org.junit.Test;
import static org.junit.Assert.*;

/** Simulated monotonic time; no sleeps, display, world or GL context. */
public class KOMETerritoryNavigationTest {
    private final Map<Integer,String> owners=new HashMap<>();
    private final Map<Integer,Integer> colors=new HashMap<>();
    private static final long MS=1_000_000;

    @Test public void coldMovingMapWaitsFor250MillisecondsOfStableNativeTransform()throws Exception {
        KOMEMapBorders borders=fixture();KOMEMapViewport v=view(1600,2000,1);
        assertNull(request(borders,v,0));assertEquals(0,borders.territoryBuilds);
        assertNull(request(borders,v,249));assertEquals(0,borders.territoryBuilds);
        KOMEMapBorders.TerritoryFrame exact=request(borders,v,250);
        assertNotNull(exact);assertTrue(exact.covers(v));assertEquals(1,borders.territoryBuilds);
        for(int i=0;i<20;i++)assertSame(exact,request(borders,v,500+i*20));
        assertEquals(1,borders.territoryBuilds);
    }

    @Test public void panAcrossCoverageNeverRebuildsUntilTheLastMovementSettles()throws Exception {
        KOMEMapBorders b=fixture();KOMEMapViewport v=view(1600,2000,1);
        KOMEMapBorders.TerritoryFrame first=b.territoryFrame(v,owners,colors);first.mesh.vertices();
        assertSame(first,request(b,v,0));
        KOMEMapViewport moved=null;
        for(int i=1;i<=20;i++) {
            moved=view(1600+i*100,2000,1);KOMEMapBorders.TerritoryFrame shown=request(b,moved,i*16);
            assertEquals("No partition/build during continuous pan",1,b.territoryBuilds);
            if(shown!=null)assertTrue(shown.mesh.verticesPrepared());
        }
        assertNull("Disjoint cached coverage leaves a blank, not fabricated borders",request(b,view(10000,2000,1),400));
        assertEquals(1,b.territoryBuilds);
        assertNull(request(b,moved,500));assertEquals(1,b.territoryBuilds);
        assertNull(request(b,moved,749));assertEquals(1,b.territoryBuilds);
        KOMEMapBorders.TerritoryFrame finalFrame=request(b,moved,750);
        assertTrue(finalFrame.covers(moved));assertEquals(2,b.territoryBuilds);
    }

    @Test public void movementAtDeadlineAndNativeZoomAnimationPostponeColdBuilds()throws Exception {
        KOMEMapBorders b=fixture();request(b,view(1600,2000,1),0);
        assertNull(request(b,view(1700,2000,1),250));
        for(int i=0;i<7;i++)assertNull(b.navigationTerritoryFrame(view(1700,2000,1+i*.1),owners,colors,true,1,(300+i*16)*MS));
        assertEquals(0,b.territoryBuilds);
        KOMEMapViewport end=view(1700,2000,2);
        assertNull(request(b,end,430));assertNull(request(b,end,679));
        assertTrue(request(b,end,680).covers(end));assertEquals(1,b.territoryBuilds);
    }

    @Test public void preparedPartialReuseIsClippedAndNeverInventsNewColoredAreas()throws Exception {
        KOMEMapBorders b=fixture();KOMEMapViewport start=view(1600,2000,1),moved=view(1900,2000,1);
        KOMEMapBorders.TerritoryFrame old=b.territoryFrame(start,owners,colors);old.mesh.vertices();request(b,start,0);
        KOMEMapBorders.TerritoryFrame shown=request(b,moved,16);assertSame(old,shown);assertFalse(shown.covers(moved));
        List<double[]> actual=rects(shown,moved),expected=rects(exact(b,moved),moved);
        assertCovered(actual,expected);
        for(double[] r:actual){assertTrue(r[0]>=shown.clipLeft(moved)-1E-9);assertTrue(r[2]<=shown.clipRight(moved)+1E-9);}
        KOMEMapBorders.TerritoryFrame settled=request(b,moved,266);assertTrue(settled.covers(moved));assertEqual(settled,exact(b,moved),moved);
        assertEquals(2,b.territoryBuilds);
    }

    @Test public void unpreparedMeshesDoNotAllocateVertexBuffersWhileMoving()throws Exception {
        KOMEMapBorders b=fixture();KOMEMapViewport v=view(1600,2000,1);
        KOMEMapBorders.TerritoryFrame initial=b.territoryFrame(v,owners,colors);assertFalse(initial.mesh.verticesPrepared());
        assertNull(request(b,v,0));assertNull(request(b,view(1640,2000,1),20));assertFalse(initial.mesh.verticesPrepared());
        assertSame(initial,request(b,view(1640,2000,1),270));assertTrue(initial.mesh.verticesPrepared());
        assertEquals(1,b.territoryBuilds);
    }

    @Test public void ownershipAndPaletteChangesNeverReuseOldFactionsWhileNavigating()throws Exception {
        KOMEMapBorders b=fixture();KOMEMapViewport v=view(1600,2000,1);
        KOMEMapBorders.TerritoryFrame old=b.territoryFrame(v,owners,colors);old.mesh.vertices();request(b,v,0);
        owners.put(0x102030,"rohan");colors.put(0x102030,0x358727);
        assertNull(request(b,v,20));assertEquals(1,b.territoryBuilds);
        KOMEMapBorders.TerritoryFrame next=request(b,v,270);assertNotSame(old,next);assertEquals("rohan",next.owners.get(0x102030));
        colors.put(0x102030,0x123456);assertNull(request(b,view(1700,2000,1),300));
        next=request(b,view(1700,2000,1),550);assertEquals(Integer.valueOf(0x123456),next.colors.get(0x102030));
    }

    @Test public void resizeModeAndAppearanceChangesRequireFreshSettlingWithoutStaleCoverage()throws Exception {
        KOMEMapBorders b=fixture();KOMETerritoryStyle style=new KOMETerritoryStyle();style.mode=KOMETerritoryStyle.DisplayMode.BORDERS_ONLY;b.territoryPresentation(style);
        KOMEMapViewport normal=new KOMEMapViewport(0,0,312,200,1600,2000,1),full=view(1600,2000,1);
        request(b,normal,0);KOMEMapBorders.TerritoryFrame initial=request(b,normal,250);assertNotNull(initial);
        KOMEMapBorders.TerritoryFrame moving=request(b,full,260);assertSame(initial,moving);assertFalse(moving.covers(full));
        assertSame(initial,request(b,full,509));assertTrue(request(b,full,510).covers(full));
        for(int preference=0;preference<5;preference++) {
            if(preference==0)style.palette=KOMETerritoryStyle.Palette.CURRENT_KOME;
            if(preference==1)style.showTileBorders=!style.showTileBorders;
            if(preference==2)style.opacity=KOMETerritoryStyle.Opacity.FULL;
            if(preference==3)style.mode=KOMETerritoryStyle.DisplayMode.FULL_COLOR;
            if(preference==4)style.mode=KOMETerritoryStyle.DisplayMode.BORDERS_ONLY;
            b.territoryPresentation(style);long at=1000+preference*300;
            assertEquals(0,b.retainedTerritoryFrames());assertNull(request(b,normal,at));assertNotNull(request(b,normal,at+250));
        }
        b.clearTerritoryFrames();assertNull(request(b,normal,3000));assertEquals(0,b.retainedTerritoryBytes());
    }

    @Test public void eligibleCompleteMeshesAtBothFarZoomsReuseAcrossPansWithinBudget()throws Exception {
        KOMEMapBorders b=fixture();KOMEMapBorders.TerritoryFrame last=null;long time=0;
        for(double zoom:new double[]{.125,.25}) {
            KOMEMapViewport normal=new KOMEMapViewport(0,0,zoom==.125?794:900,zoom==.125?399:1000,1600,2000,zoom);
            request(b,normal,time);last=request(b,normal,time+250);assertTrue(last.completeMap);assertTrue(last.mesh.verticesPrepared());
            for(int i=0;i<5;i++){KOMEMapViewport pan=view(500+i*1000,100+i*900,zoom);assertSame(last,request(b,pan,time+300+i*16));assertEqual(last,exact(b,pan),pan);}
            time+=1000;
        }
        assertEquals(2,b.territoryBuilds);assertEquals(2,b.completeAttempts);assertTrue(b.peakTerritoryBytes()<=KOMETerritoryFrameCache.MAX_BYTES);
    }

    @Test public void normalEighthZoomKeepsSmallerPartialMeshWithoutWholeMapAttempt()throws Exception {
        assertConservativePartial(312,200,.125);
    }

    @Test public void fullscreenQuarterZoomKeepsSmallerPartialMeshWithoutWholeMapAttempt()throws Exception {
        assertConservativePartial(794,399,.25);
    }

    @Test public void fullscreenEighthZoomPreservesAcceptedCompleteCoverage()throws Exception {
        KOMEMapBorders b=real(false);KOMEMapViewport v=view(1600,2000,.125);
        assertNull(request(b,v,0));KOMEMapBorders.TerritoryFrame complete=request(b,v,250);
        assertTrue(complete.completeMap);assertTrue(complete.mesh.verticesPrepared());assertEquals(1,b.completeAttempts);
        for(int i=0;i<12;i++)assertSame(complete,request(b,view(i*800-1000,i*600-1000,.125),300+i*16));
        assertEquals(1,b.territoryBuilds);assertTrue(b.retainedTerritoryBytes()<=KOMETerritoryFrameCache.MAX_BYTES);
    }

    @Test public void unretainablePaddingRecoversExactSettledViewportWithoutAnotherPartition()throws Exception {
        KOMEMapBorders b=fixture();KOMEMapViewport v=view(1600,2000,1);
        KOMEMapBorders.TerritoryFrame padded=b.territoryFrame(v,owners,colors);
        int visible=rects(padded,v).size();long overhead=1024L+128L*(owners.size()+colors.size());
        long budget=overhead+visible*36L;assertTrue(budget<overhead+padded.mesh.quads()*36L);
        b.clearTerritoryFrames();java.lang.reflect.Field f=KOMEMapBorders.class.getDeclaredField("territoryFrames");f.setAccessible(true);f.set(b,new KOMETerritoryFrameCache(budget));
        assertNull(request(b,v,0));assertNull(request(b,v,249));long before=b.territoryBuilds;
        KOMEMapBorders.TerritoryFrame recovered=request(b,v,250);
        assertNotNull("Discarding optional padding must recover a retainable exact viewport",recovered);
        assertTrue(recovered.covers(v));assertTrue(recovered.streamOnly);assertFalse(recovered.mesh.verticesPrepared());
        assertEquals(visible,recovered.mesh.quads());assertEqual(recovered,padded,v);assertEquals(before+1,b.territoryBuilds);
        for(int i=0;i<20;i++)assertSame(recovered,request(b,v,500+i*16));
        assertEquals(before+1,b.territoryBuilds);assertTrue(b.retainedTerritoryBytes()<=budget);
        assertNull(request(b,view(1900,2000,1),1000));assertEquals(before+1,b.territoryBuilds);
        owners.put(0x102030,"rohan");assertNull(request(b,v,1100));assertEquals(0,b.retainedTerritoryBytes());
    }

    private void assertConservativePartial(int width,int height,double zoom)throws Exception {
        KOMEMapBorders b=real(false);KOMEMapViewport v=new KOMEMapViewport(0,0,width,height,1600,2000,zoom);
        assertNull(request(b,v,0));KOMEMapBorders.TerritoryFrame partial=request(b,v,250);
        assertFalse("Whole source does not fit the padded viewport",partial.completeMap);assertEquals(0,b.completeAttempts);
        KOMEMapBorders.TerritoryFrame expected=exact(b,v);
        assertArrayEquals(expected.mesh.rectangles.xy,partial.mesh.rectangles.xy,0);
        assertArrayEquals(expected.mesh.rectangles.argb,partial.mesh.rectangles.argb);
        long builds=b.territoryBuilds;for(int i=0;i<20;i++)assertSame(partial,request(b,v,500+i*16));assertEquals(builds,b.territoryBuilds);
    }

    @Test public void rapidZoomReversalReusesOnlyPreparedCurrentContentAndSettlesToExactWidth()throws Exception {
        KOMEMapBorders b=fixture();KOMEMapViewport initial=view(1600,2000,1);
        KOMEMapBorders.TerritoryFrame first=b.territoryFrame(initial,owners,colors);first.mesh.vertices();request(b,initial,0);
        for(int i=0;i<8;i++) {
            double zoom=new double[]{1.1,1.5,2,1.6,.9,.7,.8,1.2}[i];
            assertSame(first,b.navigationTerritoryFrame(view(1600,2000,zoom),owners,colors,true,.5,(20+i*16)*MS));
        }
        KOMEMapViewport end=view(1600,2000,.5);assertSame(first,request(b,end,200));assertEquals(1,b.territoryBuilds);
        KOMEMapBorders.TerritoryFrame settled=request(b,end,450);assertEquals(.5,settled.view.zoom,0);assertEqual(settled,exact(b,end),end);
        assertEquals(2,b.territoryBuilds);
    }

    @Test public void deferredProductionPathIsWiredWithoutChangingFullColorOrNativeLayers()throws Exception {
        org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();
        try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new org.objectweb.asm.ClassReader(in).accept(node,0);}
        boolean policy=false,clock=false,clip=false;
        for(org.objectweb.asm.tree.MethodNode m:node.methods)for(org.objectweb.asm.tree.AbstractInsnNode step:m.instructions.toArray())if(step instanceof org.objectweb.asm.tree.MethodInsnNode) {
            String name=((org.objectweb.asm.tree.MethodInsnNode)step).name;
            if(m.name.equals("drawTerritoryGeometry")&&name.equals("navigationTerritoryFrame"))policy=true;
            if(m.name.equals("drawTerritoryGeometry")&&name.equals("nanoTime"))clock=true;
            if(m.name.equals("drawTerritoryMesh")&&name.equals("clipLeft"))clip=true;
        }
        assertTrue(policy);assertTrue(clock);assertTrue(clip);
    }

    @Test public void fullMapFeasibilityOnRealRasterAndOwnership()throws Exception {
        KOMETileRasterSnapshot raster=KOMETileTestResources.real();
        for(String line:Files.readAllLines(Paths.get("src/main/resources/assets/kome/config/kome_tile_ownership_defaults.csv"),StandardCharsets.UTF_8)) {
            String[] p=line.split(",",3);if(p.length<2)continue;Integer mask=raster.colorsById().get(p[0]);if(mask==null)continue;
            String owner=kome.common.data.KOMEAlliance.normalizeFactionKey(p[1]);if(owner.isEmpty())continue;
            owners.put(mask,owner);colors.put(mask,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(owner));
        }
        KOMEMapBorders b=new KOMEMapBorders.Cache().get(raster,raster.copyArgbPixels());StringBuilder out=new StringBuilder("Synthetic CPU; whole native source; no GL submission/FPS.\n");
        for(boolean fragmented:new boolean[]{false,true}) {
            if(fragmented)for(Map.Entry<String,Integer> e:raster.colorsById().entrySet()){owners.put(e.getValue(),e.getKey());colors.put(e.getValue(),0x358727);}
            for(double zoom:new double[]{.125,.25}) {
                KOMEMapViewport full=new KOMEMapViewport(-64,-64,3200*zoom+64,4000*zoom+64,1600,2000,zoom);
                KOMETerritoryMesh.Rectangles input=new KOMETerritoryMesh.Rectangles();long start=System.nanoTime();
                generate(b,full,input);long generated=System.nanoTime();KOMETerritoryMesh mesh=KOMETerritoryMesh.union(input);long partitioned=System.nanoTime();
                KOMEMapBorders.TerritoryFrame frame=new KOMEMapBorders.TerritoryFrame(full,owners,colors,mesh);
                long beforeVertices=System.nanoTime();if(mesh.cacheable())mesh.vertices();long afterVertices=System.nanoTime();
                out.append("fragmented=").append(fragmented).append(" zoom=").append(zoom).append(" candidates=").append(input.size)
                    .append(" quads=").append(mesh.quads()).append(" vertices=").append(mesh.quads()*4).append(" reservation=").append(KOMETerritoryFrameCache.cost(frame))
                    .append(" candidatesMs=").append((generated-start)/1E6).append(" partitionMs=").append((partitioned-generated)/1E6)
                    .append(" buildMs=").append((partitioned-start)/1E6).append(" vertexMs=").append((afterVertices-beforeVertices)/1E6).append('\n');
            }
        }
        Path report=Paths.get("build/reports/territory-navigation/feasibility.txt");Files.createDirectories(report.getParent());Files.write(report,out.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test public void rejectedCompleteCandidateIsNotRetriedForStableInputsAndPartialPathStaysAccurate()throws Exception {
        KOMEMapBorders b=real(true);KOMEMapViewport first=view(1600,2000,.125);
        request(b,first,0);KOMEMapBorders.TerritoryFrame partial=request(b,first,250);
        assertFalse(partial.completeMap);assertEquals(1,b.completeAttempts);assertEquals(1,b.completeRejects);assertTrue(partial.covers(first));
        KOMEMapViewport next=view(1600,2700,.125);
        request(b,next,300);KOMEMapBorders.TerritoryFrame rebuilt=request(b,next,550);
        assertTrue(rebuilt.covers(next));assertEquals(1,b.completeAttempts);assertEquals(1,b.completeRejects);
        long builds=b.territoryBuilds;for(int i=0;i<10;i++)assertSame(rebuilt,request(b,next,1000+i*16));assertEquals(builds,b.territoryBuilds);
        assertTrue(b.peakTerritoryBytes()<=KOMETerritoryFrameCache.MAX_BYTES);assertTrue(b.retainedTerritoryFrames()<=3);
        colors.put(colors.keySet().iterator().next(),0x123456);assertNull(request(b,next,2000));request(b,next,2250);assertEquals(2,b.completeAttempts);
    }

    @Test public void navigationEvictionStaysWithinThreeFramesAndAggregateBudget()throws Exception {
        KOMEMapBorders b=fixture();long now=0;
        for(double zoom:new double[]{.125,.25,.5,1,2,.125}) {
            KOMEMapViewport v=view(1600,2000,zoom);request(b,v,now);KOMEMapBorders.TerritoryFrame f=request(b,v,now+250);
            assertNotNull(f);assertEquals(zoom,f.view.zoom,0);assertTrue(f.covers(v));
            assertTrue(b.retainedTerritoryFrames()<=3);assertTrue(b.retainedTerritoryBytes()<=KOMETerritoryFrameCache.MAX_BYTES);now+=500;
        }
        assertTrue(cache(b).evictions>=3);b.clearTerritoryFrames();assertEquals(0,b.retainedTerritoryBytes());assertNull(request(b,view(1600,2000,1),4000));
    }

    @Test public void impossibleRetentionDoesNotLoopBuildOrRetainAnOverBudgetMesh()throws Exception {
        KOMEMapBorders b=fixture();java.lang.reflect.Field f=KOMEMapBorders.class.getDeclaredField("territoryFrames");f.setAccessible(true);f.set(b,new KOMETerritoryFrameCache(1024));
        KOMEMapViewport v=view(1600,2000,1);assertNull(request(b,v,0));assertNull(request(b,v,250));assertEquals(1,b.territoryBuilds);
        for(int i=0;i<30;i++)assertNull(request(b,v,500+i*16));assertEquals(1,b.territoryBuilds);assertEquals(0,b.retainedTerritoryBytes());
        KOMEMapViewport changed=view(1700,2000,1);assertNull(request(b,changed,2000));request(b,changed,2250);assertEquals(2,b.territoryBuilds);
        owners.put(0x102030,"rohan");assertNull(request(b,changed,3000));request(b,changed,3250);assertEquals(3,b.territoryBuilds);
        KOMEMapViewport far=view(1600,2000,.25);assertNull(request(b,far,4000));assertNull(request(b,far,4250));
        assertEquals(0,b.completeRejects);assertEquals(4,b.territoryBuilds);assertNull(request(b,far,5000));assertEquals(4,b.territoryBuilds);
    }

    @Test public void completeConstructionLimitsAbortWithoutPublishingAFragmentOrChangingDefaultUnion() {
        KOMETerritoryMesh.Rectangles input=new KOMETerritoryMesh.Rectangles(2);
        input.add(0,0,1,1,0xFF358727,1);input.add(3,0,4,1,0xFF358727,1);
        try{input.add(6,0,7,1,0xFF358727,1);fail("Candidate work cap");}catch(KOMETerritoryMesh.LimitExceeded expected){}
        try{KOMETerritoryMesh.union(input,1);fail("Output reservation cap");}catch(KOMETerritoryMesh.LimitExceeded expected){}
        assertEquals(2,KOMETerritoryMesh.union(input).quads());
    }

    @Test public void comparableRealNavigationBenchmark()throws Exception {
        StringBuilder out=new StringBuilder("Synthetic CPU, sequential warmed JVM; 16 snake-grid map-space pans (48/96 map units), simulated 16ms intervals, then 250ms quiet; ends on an owned boundary. No GL/FPS.\n");
        for(double[] spec:new double[][]{{312,200,.125},{794,399,.25},{312,200,1},{794,399,1}})for(boolean deferred:new boolean[]{false,true}) {
            KOMEMapBorders b=real(false);double[] anchor=ownedAnchor(b);KOMEMapViewport first=new KOMEMapViewport(0,0,spec[0],spec[1],anchor[0],anchor[1]-192,spec[2]);
            long initialStart=System.nanoTime();KOMEMapBorders.TerritoryFrame initial;
            if(deferred){assertNull(request(b,first,0));initial=request(b,first,250);}else initial=b.territoryFrame(first,owners,colors);
            if(initial.mesh.cacheable()&&!initial.streamOnly)initial.mesh.vertices();double initialMs=(System.nanoTime()-initialStart)/1E6;
            long initialBuilds=b.territoryBuilds,initialNanos=b.territoryBuildNanos;KOMEMapViewport last=first;
            long submitted=0;int blank=0,incomplete=0;
            for(int i=1;i<=16;i++) {
                int row=(i-1)/4,col=(row&1)==0?3-(i-1)%4:(i-1)%4;
                last=new KOMEMapViewport(0,0,spec[0],spec[1],anchor[0]+(col-3)*96,anchor[1]+(row-3)*48,spec[2]);
                KOMEMapBorders.TerritoryFrame frame=deferred?request(b,last,300+i*16):b.territoryFrame(last,owners,colors);
                if(frame==null)blank++;else {
                    if(frame.mesh.cacheable()&&!frame.streamOnly)frame.mesh.vertices();submitted+=frame.mesh.quads()*4L;if(!frame.covers(last))incomplete++;
                }
            }
            long during=b.territoryBuilds-initialBuilds,duringNanos=b.territoryBuildNanos-initialNanos,beforeSettle=b.territoryBuilds;
            KOMEMapBorders.TerritoryFrame finalFrame=deferred?request(b,last,300+16*16+250):b.territoryFrame(last,owners,colors);
            assertTrue(finalFrame.covers(last));assertTrue("Final viewport includes a real owned boundary",finalFrame.mesh.quads()>0);if(deferred)assertEquals(0,during);
            KOMETerritoryFrameCache c=cache(b);
            out.append("deferred=").append(deferred).append(" viewport=").append((int)spec[0]).append('x').append((int)spec[1]).append(" zoom=").append(spec[2])
                .append(" initialMs=").append(initialMs).append(" panBuilds=").append(during).append(" settleBuilds=").append(b.territoryBuilds-beforeSettle)
                .append(" totalBuilds=").append(b.territoryBuilds).append(" panConstructionMs=").append(duringNanos/1E6).append(" totalConstructionMs=").append(b.territoryBuildNanos/1E6)
                .append(" hits=").append(b.territoryHits).append(" misses=").append(b.territoryMisses).append(" evictions=").append(c.evictions).append(" coverageRejects=").append(c.coverageRejections)
                .append(" peakReserved=").append(b.peakTerritoryBytes()).append(" panWouldSubmitVertices=").append(submitted).append(" finalVertices=").append(finalFrame.mesh.quads()*4)
                .append(" blankFrames=").append(blank).append(" partialFrames=").append(incomplete).append('\n');
        }
        Path path=Paths.get("build/reports/territory-navigation/comparison.txt");Files.createDirectories(path.getParent());Files.write(path,out.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Test public void comparableDeferredPoliciesBenchmark()throws Exception {
        StringBuilder out=new StringBuilder("Same deferred policy and real ownership; 16 snake-grid pans at 16ms, then 250ms quiet. Compare saved RED and GREEN deferred-policy.txt; no GL/FPS.\n");
        for(double[] spec:new double[][]{{312,200,.125},{794,399,.125},{794,399,.25},{794,399,1}}) {
            KOMEMapBorders b=real(false);double[] anchor=ownedAnchor(b);
            KOMEMapViewport first=new KOMEMapViewport(0,0,spec[0],spec[1],anchor[0],anchor[1]-192,spec[2]);
            assertNull(request(b,first,0));KOMEMapBorders.TerritoryFrame initial=request(b,first,250);
            boolean complete=initial.completeMap;long initialBuilds=b.territoryBuilds,initialNanos=b.territoryBuildNanos;
            KOMEMapViewport last=first;long submitted=0;int blank=0,incomplete=0;
            for(int i=1;i<=16;i++) {
                int row=(i-1)/4,col=(row&1)==0?3-(i-1)%4:(i-1)%4;
                last=new KOMEMapViewport(0,0,spec[0],spec[1],anchor[0]+(col-3)*96,anchor[1]+(row-3)*48,spec[2]);
                KOMEMapBorders.TerritoryFrame frame=request(b,last,300+i*16);
                if(frame==null)blank++;else {submitted+=frame.mesh.quads()*4L;if(!frame.covers(last))incomplete++;}
            }
            long during=b.territoryBuilds-initialBuilds,beforeSettle=b.territoryBuilds;
            assertEquals(0,during);assertEquals(initialNanos,b.territoryBuildNanos);
            KOMEMapBorders.TerritoryFrame end=request(b,last,806);assertTrue(end.covers(last));assertTrue(end.mesh.quads()>0);
            long settled=b.territoryBuilds-beforeSettle;for(int i=0;i<16;i++)assertSame(end,request(b,last,900+i*16));
            assertEquals(beforeSettle+settled,b.territoryBuilds);
            KOMETerritoryFrameCache c=cache(b);
            out.append("viewport=").append((int)spec[0]).append('x').append((int)spec[1]).append(" zoom=").append(spec[2])
                .append(" complete=").append(complete).append(" panBuilds=").append(during).append(" settleBuilds=").append(settled)
                .append(" totalBuilds=").append(b.territoryBuilds).append(" initialConstructionMs=").append(initialNanos/1E6)
                .append(" totalConstructionMs=").append(b.territoryBuildNanos/1E6).append(" worstConstructionMs=").append(b.territoryWorstBuildNanos/1E6)
                .append(" peakReserved=").append(b.peakTerritoryBytes()).append(" panWouldSubmitVertices=").append(submitted)
                .append(" finalVertices=").append(end.mesh.quads()*4L).append(" hits=").append(b.territoryHits).append(" misses=").append(b.territoryMisses)
                .append(" evictions=").append(c.evictions).append(" coverageRejects=").append(c.coverageRejections)
                .append(" blankFrames=").append(blank).append(" partialFrames=").append(incomplete).append('\n');
        }
        Path path=Paths.get("build/reports/territory-navigation/deferred-policy.txt");Files.createDirectories(path.getParent());Files.write(path,out.toString().getBytes(StandardCharsets.UTF_8));
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
    private KOMEMapBorders real(boolean fragmented)throws Exception {
        owners.clear();colors.clear();KOMETileRasterSnapshot raster=KOMETileTestResources.real();
        for(String line:Files.readAllLines(Paths.get("src/main/resources/assets/kome/config/kome_tile_ownership_defaults.csv"),StandardCharsets.UTF_8)) {
            String[] p=line.split(",",3);if(p.length<2)continue;Integer mask=raster.colorsById().get(p[0]);if(mask==null)continue;
            String owner=kome.common.data.KOMEAlliance.normalizeFactionKey(p[1]);if(owner.isEmpty())continue;owners.put(mask,owner);colors.put(mask,KOMETerritoryStyle.Palette.CLASSIC_LOTR.rgb(owner));
        }
        if(fragmented)for(Map.Entry<String,Integer> e:raster.colorsById().entrySet()){owners.put(e.getValue(),e.getKey());colors.put(e.getValue(),0x358727);}
        return new KOMEMapBorders.Cache().get(raster,raster.copyArgbPixels());
    }
    private static KOMETerritoryFrameCache cache(KOMEMapBorders b)throws Exception {
        java.lang.reflect.Field f=KOMEMapBorders.class.getDeclaredField("territoryFrames");f.setAccessible(true);return (KOMETerritoryFrameCache)f.get(b);
    }
    private double[] ownedAnchor(KOMEMapBorders b) {
        List<double[]> anchors=new ArrayList<>();
        b.visitEdges(0,0,b.width,b.height,(axis,line,start,end,a,c)->{
            String left=owners.get(a&0xFFFFFF),right=owners.get(c&0xFFFFFF);
            if(!Objects.equals(left,right))anchors.add(new double[]{(axis?line:(start+end)/2D)*b.mapPerCellX,(axis?(start+end)/2D:line)*b.mapPerCellY});
        });
        assertFalse(anchors.isEmpty());return anchors.get(anchors.size()/2);
    }
    private KOMEMapBorders.TerritoryFrame request(KOMEMapBorders b,KOMEMapViewport v,long milliseconds){return b.navigationTerritoryFrame(v,owners,colors,false,v.zoom,milliseconds*MS);}
    private static KOMEMapViewport view(double x,double y,double zoom){return new KOMEMapViewport(0,0,794,399,x,y,zoom);}
    private void generate(KOMEMapBorders b,KOMEMapViewport v,KOMETerritoryMesh.Rectangles input)throws Exception {
        Method m=KOMEMapBorders.class.getDeclaredMethod("drawTerritoriesUncached",KOMEMapViewport.class,Map.class,Map.class,KOMEMapBorders.LayerSink.class);m.setAccessible(true);
        m.invoke(b,v,owners,colors,(KOMEMapBorders.LayerSink)input::add);
    }
    private KOMEMapBorders.TerritoryFrame exact(KOMEMapBorders b,KOMEMapViewport v)throws Exception {
        KOMEMapViewport pad=new KOMEMapViewport(v.left-64,v.top-64,v.right+64,v.bottom+64,v.posX,v.posY,v.zoom);
        KOMETerritoryMesh.Rectangles input=new KOMETerritoryMesh.Rectangles();generate(b,pad,input);
        return new KOMEMapBorders.TerritoryFrame(pad,owners,colors,KOMETerritoryMesh.union(input));
    }
    private static List<double[]> rects(KOMEMapBorders.TerritoryFrame f,KOMEMapViewport v) {
        List<double[]> out=new ArrayList<>();f.mesh.draw(v,f.view,(x,y,r,d,c)->out.add(new double[]{x,y,r,d,c}));return out;
    }
    private static void assertEqual(KOMEMapBorders.TerritoryFrame a,KOMEMapBorders.TerritoryFrame b,KOMEMapViewport v){List<double[]> aa=rects(a,v),bb=rects(b,v);assertCovered(aa,bb);assertCovered(bb,aa);}
    private static void assertCovered(List<double[]> a,List<double[]> b) {
        for(double[] r:a){double area=(r[2]-r[0])*(r[3]-r[1]),covered=0;for(double[] s:b)if(r[4]==s[4])covered+=Math.max(0,Math.min(r[2],s[2])-Math.max(r[0],s[0]))*Math.max(0,Math.min(r[3],s[3])-Math.max(r[1],s[1]));assertEquals(area,covered,Math.max(1,area)*1E-8);}
    }
}
