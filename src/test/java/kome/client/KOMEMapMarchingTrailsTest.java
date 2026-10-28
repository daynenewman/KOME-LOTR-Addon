package kome.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.network.KOMECompanyMapProjection;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEMapMarchingTrailsTest {
    @Test public void mapSpaceDashAndGapProjectWithoutLegDivisionAtEveryZoom(){
        for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}){
            KOMEMapViewport view=new KOMEMapViewport(0,0,4000,4000,0,0,zoom);
            List<double[]> shortLeg=new ArrayList<double[]>(),longLeg=new ArrayList<double[]>();
            KOMEMapMarchingTrails.emitMapDashes(0,0,80,0,view,
                (x1,y1,x2,y2)->longLeg.add(new double[]{x1,y1,x2,y2}));
            KOMEMapMarchingTrails.emitMapDashes(0,0,32,0,view,
                (x1,y1,x2,y2)->shortLeg.add(new double[]{x1,y1,x2,y2}));
            assertEquals("Zoom "+zoom,14,longLeg.size());
            assertEquals("Zoom "+zoom,6,shortLeg.size());
            assertEquals(4*zoom,longLeg.get(0)[2]-longLeg.get(0)[0],.001);
            assertEquals(2*zoom,longLeg.get(1)[0]-longLeg.get(0)[2],.001);
            assertEquals("Natural clipped endpoint",2*zoom,longLeg.get(13)[2]-longLeg.get(13)[0],.001);
        }
    }
    @Test public void committedAndPreviewLegAlphaPoliciesDiffer(){
        int[] active={64,128,191,255,255,255,255,255};int i=0;
        for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}){
            assertEquals(active[i],KOMEMapMarchingTrails.ACTIVE.colorAt(zoom,0)>>>24);
            assertEquals((active[i]+2)/4,KOMEMapMarchingTrails.ACTIVE.colorAt(zoom,1)>>>24);
            assertEquals((active[i]+2)/4,KOMEMapMarchingTrails.ACTIVE.colorAt(zoom,7)>>>24);
            assertEquals(255,KOMEMapMarchingTrails.PREVIEW.colorAt(zoom,0)>>>24);
            assertEquals(255,KOMEMapMarchingTrails.PREVIEW.colorAt(zoom,7)>>>24);
            i++;
        }
        assertEquals(1.5F,KOMEMapMarchingTrails.ACTIVE.width,.001F);
        assertEquals(1.5F,KOMEMapMarchingTrails.PREVIEW.width,.001F);
    }
    @Test public void progressiveIlluminationAccumulatesInTravelOrderAndResets(){
        assertEquals(4,KOMEMapMarchingTrails.firstLegDashCount(20));
        assertEquals(1,KOMEMapMarchingTrails.illuminatedDashes(4,0));
        assertEquals(2,KOMEMapMarchingTrails.illuminatedDashes(4,375));
        assertEquals(3,KOMEMapMarchingTrails.illuminatedDashes(4,750));
        assertEquals(4,KOMEMapMarchingTrails.illuminatedDashes(4,1125));
        assertEquals(1,KOMEMapMarchingTrails.illuminatedDashes(4,1500));
        assertEquals(1,KOMEMapMarchingTrails.illuminatedDashes(4,3000));
        int lit=KOMEMapMarchingTrails.illuminatedDashes(4,375);
        for(int dash=0;dash<4;dash++){
            assertEquals(dash<2?255:64,KOMEMapMarchingTrails.colorForDash(
                0xFF33261C,0x4033261C,0,dash,lit,true)>>>24);
        }
    }
    @Test public void animationRespectsZoomFutureLegsPreviewsAndOrderStatus(){
        int[] expected={64,128,191,255,255,255,255,255};int i=0;
        for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}){
            int full=expected[i++],dim=(full+2)/4;
            int first=KOMEMapMarchingTrails.ACTIVE.colorAt(zoom,0);
            int future=KOMEMapMarchingTrails.ACTIVE.colorAt(zoom,1);
            assertEquals(full,KOMEMapMarchingTrails.colorForDash(first,future,0,0,1,true)>>>24);
            assertEquals(dim,KOMEMapMarchingTrails.colorForDash(first,future,0,1,1,true)>>>24);
            assertEquals(dim,KOMEMapMarchingTrails.colorForDash(first,future,1,0,4,true)>>>24);
            assertEquals(full,KOMEMapMarchingTrails.colorForDash(first,future,0,3,1,false)>>>24);
            int preview=KOMEMapMarchingTrails.PREVIEW.colorAt(zoom,0);
            assertEquals(255,KOMEMapMarchingTrails.colorForDash(preview,preview,0,3,1,false)>>>24);
            assertEquals(255,KOMEMapMarchingTrails.colorForDash(preview,preview,7,3,4,false)>>>24);
        }
        KOMEArmyMovementOrder order=order();
        for(String status:new String[]{KOMEArmyMovementOrder.MOVING,KOMEArmyMovementOrder.PENDING_SPAWN,
                KOMEArmyMovementOrder.SPAWNING,KOMEArmyMovementOrder.WAITING_NEXT_STEP}){
            order.status=status;assertTrue(KOMEMapMarchingTrails.animates(order));
        }
        for(String status:new String[]{KOMEArmyMovementOrder.SPAWN_BLOCKED,KOMEArmyMovementOrder.ACCESS_HALTED,
                KOMEArmyMovementOrder.HOLDING,KOMEArmyMovementOrder.CONFLICT_HELD,
                KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED,KOMEArmyMovementOrder.ARRIVED,
                KOMEArmyMovementOrder.CANCELLED,KOMEArmyMovementOrder.RETREATING}){
            order.status=status;assertFalse(KOMEMapMarchingTrails.animates(order));
        }
        order.status=KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.spawnRetryPaused=true;assertFalse(KOMEMapMarchingTrails.animates(order));
        order.spawnRetryPaused=false;order.stopped=true;
        assertFalse(KOMEMapMarchingTrails.animates(order));
        order.stopped=false;assertTrue(KOMEMapMarchingTrails.animates(order));
    }
    @Test public void indexedQuadsKeepAbsoluteTravelDirectionThroughClippingAndLowZoom(){
        for(double zoom:new double[]{.125,1,16}){
            List<long[]> indices=new ArrayList<long[]>();
            KOMEMapViewport view=new KOMEMapViewport(0,0,100,100,0,0,zoom);
            KOMEMapMarchingTrails.emitRouteQuadsIndexed(
                Arrays.asList(new double[]{0,0},new double[]{40,0},new double[]{80,0}),view,1.5,
                (leg,dash,ax,ay,bx,by,cx,cy,dx,dy)->indices.add(new long[]{leg,dash}));
            assertFalse(indices.isEmpty());
            long previous=-1;
            for(long[] indexed:indices)if(indexed[0]==0){assertTrue(indexed[1]>previous);previous=indexed[1];}
            assertEquals(0,indices.get(0)[1]);
        }
        List<double[]> reverse=new ArrayList<double[]>();
        KOMEMapMarchingTrails.emitRouteQuadsIndexed(
            Arrays.asList(new double[]{80,50},new double[]{20,50}),
            new KOMEMapViewport(0,0,100,100,50,50,1),1.5,
            (leg,dash,ax,ay,bx,by,cx,cy,dx,dy)->reverse.add(new double[]{dash,ax,bx}));
        assertEquals(10,reverse.size());
        assertEquals(0,reverse.get(0)[0],.001);
        assertTrue("First illuminated dash is nearest the committed origin",reverse.get(0)[1]>reverse.get(1)[1]);
        for(int i=1;i<reverse.size();i++)assertTrue(reverse.get(i)[0]>reverse.get(i-1)[0]);
    }
    @Test public void smallestZoomRetainsSubpixelMapDashesAndClippedEndpoints(){
        KOMEMapViewport view=new KOMEMapViewport(0,0,100,100,0,0,.125);
        List<double[]> spans=new ArrayList<double[]>();
        KOMEMapMarchingTrails.emitMapDashes(0,0,20,0,view,
            (x1,y1,x2,y2)->spans.add(new double[]{x1,y1,x2,y2}));
        assertEquals(4,spans.size());
        assertEquals(.5,spans.get(0)[2]-spans.get(0)[0],.001);
        assertEquals(.25,spans.get(1)[0]-spans.get(0)[2],.001);
        assertEquals(.25,spans.get(3)[2]-spans.get(3)[0],.001);
        for(double[] span:spans){
            assertTrue(span[0]>=view.left && span[2]<=view.right);
            assertTrue(span[2]>span[0]);
        }
    }
    private KOMEArmyMovementOrder order(){
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();
        order.status=KOMEArmyMovementOrder.MOVING;
        order.routeTiles.addAll(Arrays.asList("T100","T101","T102","T103"));
        order.currentTile="T100";order.currentRouteIndex=0;order.nextRouteIndex=1;
        return order;
    }
    @Test public void committedProgressRemovesEveryCompletedLeg(){
        KOMEArmyMovementOrder order=order();
        assertEquals(order.routeTiles,KOMEMapMarchingTrails.remainingTiles(order));
        order.currentTile="T101";order.currentRouteIndex=1;order.nextRouteIndex=2;
        order=KOMECompanyMapProjection.readMovement(KOMECompanyMapProjection.movementTag(order));
        for(String status:new String[]{KOMEArmyMovementOrder.WAITING_NEXT_STEP,
                KOMEArmyMovementOrder.MOVING,KOMEArmyMovementOrder.SPAWN_BLOCKED,
                KOMEArmyMovementOrder.ACCESS_HALTED,KOMEArmyMovementOrder.CONFLICT_HELD}){
            order.status=status;
            assertEquals(Arrays.asList("T101","T102","T103"),KOMEMapMarchingTrails.remainingTiles(order));
        }
        order.currentTile="T102";order.currentRouteIndex=2;order.nextRouteIndex=3;
        assertEquals(Arrays.asList("T102","T103"),KOMEMapMarchingTrails.remainingTiles(order));
        order.currentTile="T103";order.currentRouteIndex=3;
        assertTrue(KOMEMapMarchingTrails.remainingTiles(order).isEmpty());
    }
    @Test public void transitNeverFabricatesAProjectedPositionAndTerminalOrdersHaveNoTrail(){
        KOMEArmyMovementOrder order=order();
        order.status=KOMEArmyMovementOrder.PENDING_SPAWN;
        assertEquals("Transit starts at the last committed tile",order.routeTiles,
            KOMEMapMarchingTrails.remainingTiles(order));
        order.currentTile="T102";
        assertTrue("Incoherent progress must not invent a trail",KOMEMapMarchingTrails.remainingTiles(order).isEmpty());
        for(String status:new String[]{KOMEArmyMovementOrder.ARRIVED,KOMEArmyMovementOrder.CANCELLED}){
            order.status=status;order.currentTile="T100";
            assertTrue(KOMEMapMarchingTrails.remainingTiles(order).isEmpty());
        }
    }
    @Test public void dashesAreBoundedToTheViewportAndDoNotReconstructAnOffscreenLeg(){
        KOMEMapViewport view=new KOMEMapViewport(20,20,80,80,50,50,1);
        List<double[]> spans=new ArrayList<double[]>();
        KOMEMapMarchingTrails.emitMapDashes(0,50,100,50,view,(x1,y1,x2,y2)->spans.add(new double[]{x1,y1,x2,y2}));
        assertTrue(spans.size()>0 && spans.size()<12);
        for(double[] span:spans){
            assertTrue(span[0]>=20 && span[2]<=80);
            assertEquals(50.0,span[1],0.001);assertEquals(50.0,span[3],0.001);
            assertTrue(span[2]-span[0]<=4.001);
        }
        spans.clear();
        KOMEMapMarchingTrails.emitMapDashes(0,100,100,100,view,(x1,y1,x2,y2)->spans.add(new double[]{x1,y1,x2,y2}));
        assertTrue(spans.isEmpty());
        KOMEMapMarchingTrails.emitMapDashes(-1000000,50,1000000,50,view,
            (x1,y1,x2,y2)->spans.add(new double[]{x1,y1,x2,y2}));
        assertTrue("Even a very long projected leg emits only visible dashes",spans.size()<=11);
    }
    @Test public void reverseAndDiagonalLegsStayClippedWithoutChangingAnchors(){
        KOMEMapViewport view=new KOMEMapViewport(20,20,80,80,50,50,1);
        List<double[]> spans=new ArrayList<double[]>();
        KOMEMapMarchingTrails.emitMapDashes(100,100,0,0,view,
            (x1,y1,x2,y2)->spans.add(new double[]{x1,y1,x2,y2}));
        assertFalse(spans.isEmpty());
        for(double[] span:spans){
            for(double coordinate:span)assertTrue(coordinate>=20-0.001&&coordinate<=80+0.001);
            assertEquals(span[0],span[1],0.001);assertEquals(span[2],span[3],0.001);
            assertTrue(span[0]>=span[2]);
        }
    }
    @Test public void activeAndPreviewStylesUseCleanSingleColorsAndMarkerFade(){
        assertEquals(0x33261C,KOMEMapMarchingTrails.ACTIVE.rgb);
        assertEquals(0x3E9FD8,KOMEMapMarchingTrails.PREVIEW.rgb);
        assertEquals(1.5F,KOMEMapMarchingTrails.ACTIVE.width,.001F);
        assertEquals(1.5F,KOMEMapMarchingTrails.PREVIEW.width,.001F);
        int previous=-1,index=0;
        int[] expectedAlpha={64,128,191,255,255,255,255,255};
        for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}){
            int alpha=KOMEMapMarchingTrails.ACTIVE.colorAt(zoom,0)>>>24;
            assertEquals(KOMEMapIconPresentation.at(zoom).alpha,alpha);
            assertEquals("Zoom "+zoom,expectedAlpha[index++],alpha);
            assertEquals((alpha+2)/4,KOMEMapMarchingTrails.ACTIVE.colorAt(zoom,1)>>>24);
            assertEquals(255,KOMEMapMarchingTrails.PREVIEW.colorAt(zoom,0)>>>24);
            assertTrue(alpha>=previous);previous=alpha;
        }
        assertTrue("Far zoom is faint",(KOMEMapMarchingTrails.ACTIVE.colorAt(.125,0)>>>24)<=64);
    }
    @Test public void submittedQuadsHaveGuiPixelWidthAndVisibleDashedGaps(){
        List<double[]> quads=new ArrayList<double[]>();
        KOMEMapMarchingTrails.emitRouteQuads(Arrays.asList(new double[]{20,50},new double[]{80,50}),
            new KOMEMapViewport(0,0,100,100,50,50,1),KOMEMapMarchingTrails.ACTIVE.width,
            (leg,ax,ay,bx,by,cx,cy,dx,dy)->quads.add(new double[]{ax,ay,bx,by,cx,cy,dx,dy}));
        assertEquals(10,quads.size());
        for(int i=0;i<quads.size();i++){
            double[] quad=quads.get(i);
            assertEquals(4.0,quad[2]-quad[0],.001);
            assertEquals(KOMEMapMarchingTrails.ACTIVE.width,quad[1]-quad[5],.001);
            if(i>0)assertEquals(2.0,quad[0]-quads.get(i-1)[2],.001);
        }
    }
    @Test public void connectedLegsKeepOneMapSpaceDashPhaseAndLegIdentity(){
        List<double[]> points=Arrays.asList(new double[]{0,0},new double[]{20,0},new double[]{60,0});
        KOMEMapViewport view=new KOMEMapViewport(0,0,200,200,0,0,1);
        List<double[]> quads=new ArrayList<double[]>();
        KOMEMapMarchingTrails.emitRouteQuads(points,view,1.5,
            (leg,ax,ay,bx,by,cx,cy,dx,dy)->quads.add(new double[]{leg,ax,bx}));
        assertEquals(11,quads.size());
        assertEquals(0,quads.get(0)[0],.001);
        assertEquals(1,quads.get(4)[0],.001);
        assertEquals("The dash phase continues across the tile junction",120,quads.get(4)[1],.001);
        assertEquals(122,quads.get(4)[2],.001);
    }
    @Test public void panningTranslatesDashesWithoutChangingTheirMapSpaceLength(){
        KOMEMapViewport before=new KOMEMapViewport(0,0,200,200,0,0,2);
        KOMEMapViewport after=new KOMEMapViewport(0,0,200,200,5,0,2);
        List<double[]> a=new ArrayList<double[]>(),b=new ArrayList<double[]>();
        KOMEMapMarchingTrails.emitMapDashes(0,0,40,0,before,
            (x1,y1,x2,y2)->a.add(new double[]{x1,y1,x2,y2}));
        KOMEMapMarchingTrails.emitMapDashes(0,0,40,0,after,
            (x1,y1,x2,y2)->b.add(new double[]{x1,y1,x2,y2}));
        assertEquals(a.size(),b.size());
        for(int i=0;i<a.size();i++){
            assertEquals(10,a.get(i)[0]-b.get(i)[0],.001);
            assertEquals(a.get(i)[2]-a.get(i)[0],b.get(i)[2]-b.get(i)[0],.001);
        }
    }
    @Test public void overlappingCompanyRoutesStaySeparateAndDeterministic(){
        List<double[]> first=Arrays.asList(new double[]{10,40},new double[]{70,40});
        List<double[]> second=Arrays.asList(new double[]{10,40},new double[]{70,40});
        KOMEMapViewport view=new KOMEMapViewport(0,0,100,100,50,50,1);
        List<double[]> firstQuads=new ArrayList<double[]>(),secondQuads=new ArrayList<double[]>();
        KOMEMapMarchingTrails.emitRouteQuads(first,view,KOMEMapMarchingTrails.ACTIVE.width,
            (leg,ax,ay,bx,by,cx,cy,dx,dy)->firstQuads.add(new double[]{ax,ay,bx,by,cx,cy,dx,dy}));
        KOMEMapMarchingTrails.emitRouteQuads(second,view,KOMEMapMarchingTrails.ACTIVE.width,
            (leg,ax,ay,bx,by,cx,cy,dx,dy)->secondQuads.add(new double[]{ax,ay,bx,by,cx,cy,dx,dy}));
        assertEquals(firstQuads.size(),secondQuads.size());
        assertFalse(firstQuads.isEmpty());
        for(int i=0;i<firstQuads.size();i++)assertArrayEquals(firstQuads.get(i),secondQuads.get(i),.001);
        assertArrayEquals(new double[]{10,40},first.get(0),.001);
        assertArrayEquals(new double[]{70,40},second.get(1),.001);
    }
    @Test public void denseRoutesEmitOnlyVisibleLegs(){
        List<double[]> points=new ArrayList<double[]>();
        for(int i=0;i<500;i++)points.add(new double[]{-10000-i*20,50});
        points.add(null); // A missing anchor cannot join unrelated legal legs.
        points.add(new double[]{30,40});points.add(new double[]{70,60});
        int[] count={0};
        KOMEMapMarchingTrails.emitRoute(points,new KOMEMapViewport(0,0,100,100,50,50,1),
            (x1,y1,x2,y2)->count[0]++);
        assertTrue("Only the final visible legal leg emits dashes",count[0]>0&&count[0]<10);
    }
    @Test public void productionProjectionKeepsMissingIntermediateAnchorsAsGaps(){
        List<double[]> projected=KOMEConquestMapOverlay.projectRouteMapTiles(
            Arrays.asList("T100","T101","T102","T103"),tile->{
                if("T101".equals(tile))return null;
                int x=Integer.parseInt(tile.substring(1))-100;
                return new double[]{x*20,50};
            });
        assertEquals(4,projected.size());assertNull(projected.get(1));
        List<double[]> spans=new ArrayList<double[]>();
        KOMEMapMarchingTrails.emitRoute(projected,new KOMEMapViewport(0,0,100,100,50,50,1),
            (x1,y1,x2,y2)->spans.add(new double[]{x1,y1,x2,y2}));
        assertFalse(spans.isEmpty());
        for(double[] span:spans)assertTrue("Only the T102 -> T103 leg remains",span[0]>=40);
    }
}
