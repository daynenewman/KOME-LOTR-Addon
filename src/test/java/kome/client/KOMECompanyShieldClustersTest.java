package kome.client;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEUnitMapMarker;
import kome.common.network.KOMECompanyMapProjection;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECompanyShieldClustersTest {
    private final KOMEMapViewport view=new KOMEMapViewport(0,0,800,600,0,0,1);
    private KOMECompanyShieldClusters.Entry at(String id,String faction,int x,int y,int units,double zoom){
        KOMEUnitMapMarker marker=new KOMEUnitMapMarker();marker.entityId=id;
        marker.soldierCount=units;marker.currentTile="T100";
        KOMEMapMarkerArt.Sprite art=KOMEMapMarkerArt.shield(faction);
        return new KOMECompanyShieldClusters.Entry(marker,art,
            art.bounds(KOMEMapIconPresentation.at(zoom),x,y),faction,false);
    }
    private List<KOMECompanyShieldClusters.Group> groups(double zoom,KOMECompanyShieldClusters.Entry... entries){
        return KOMECompanyShieldClusters.group(Arrays.asList(entries),KOMEMapIconPresentation.at(zoom),view);
    }
    private KOMECompanyShieldClusters.Group coincident(KOMEMapViewport viewport,double zoom,int x,int y,int count){
        List<KOMECompanyShieldClusters.Entry> entries=new ArrayList<KOMECompanyShieldClusters.Entry>();
        for(int i=0;i<count;i++)entries.add(at("edge-"+i,"gondor",x,y,i+1,zoom));
        return KOMECompanyShieldClusters.group(entries,KOMEMapIconPresentation.at(zoom),viewport).get(0);
    }
    @Test public void twoDigitBadgeNeverCreatesOutsideViewportOrGhostClickTargets(){
        KOMECompanyShieldClusters.Group left=coincident(view,1,4,100,10);
        assertEquals(10,left.members.size());
        assertTrue(left.iconBounds.visible(view));
        assertFalse("Offset shields may cross the viewport while the primary shield remains visible",left.hitBounds.visible(view));
        assertTrue(left.compositeNeedsClip());
        assertTrue(left.contains(left.iconBounds.x,left.iconBounds.y+4));
        assertTrue("Visible badge pixels remain interactive",left.contains(0,left.badgeY));
        assertFalse(left.contains(-1,left.badgeY));
        KOMECompanyShieldClusters.Group oneDigit=coincident(view,1,4,100,9);
        assertTrue(oneDigit.iconBounds.visible(view));
        assertTrue(oneDigit.compositeNeedsClip());
        assertFalse(oneDigit.badgeNeedsClip());
        List<KOMECompanyShieldClusters.Entry> wideFontEntries=new ArrayList<KOMECompanyShieldClusters.Entry>();
        for(int i=0;i<10;i++)wideFontEntries.add(at("wide-"+i,"gondor",4,100,1,1));
        KOMECompanyShieldClusters.Group wideFont=KOMECompanyShieldClusters.group(
            wideFontEntries,KOMEMapIconPresentation.at(1),view,count->20).get(0);
        assertTrue("Resource-pack font metrics widen only the badge clip",wideFont.badgeNeedsClip());
        assertTrue(wideFont.visibleShield());
        assertFalse(wideFont.contains(-1,wideFont.badgeY));
    }
    @Test public void clusteredShieldsUseCompactLayeredGeometryAtEveryNativeZoom(){
        for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}){
            KOMECompanyShieldClusters.Group cluster=coincident(view,zoom,100,100,10);
            int expected=3;
            assertEquals("Cluster remains visibly layered even when labels are hidden",
                expected,cluster.shieldLayers.size());
            assertTrue(cluster.hitBounds.width>cluster.iconBounds.width);
            assertTrue(cluster.hitBounds.width<=cluster.iconBounds.width+4);
            assertTrue(cluster.hitBounds.height<=cluster.iconBounds.height+2);
            for(KOMEMapIconPresentation.Bounds layer:cluster.shieldLayers){
                assertEquals(cluster.iconBounds.width,layer.width);
                assertEquals(cluster.iconBounds.height,layer.height);
                assertEquals(cluster.iconBounds.alpha,layer.alpha);
                assertTrue(cluster.contains(layer.x+layer.width/2,layer.y+layer.height/2));
            }
            List<KOMEMapIconPresentation.Bounds> submitted=new ArrayList<KOMEMapIconPresentation.Bounds>();
            KOMEConquestMapOverlay.renderClusterLayers(cluster,(art,bounds)->{
                assertSame("Every layer uses the existing native or fallback shield resource",cluster.art,art);
                submitted.add(bounds);
            });
            assertEquals(cluster.shieldLayers,submitted);
            KOMECompanyShieldClusters.Group single=groups(zoom,at("alone","gondor",100,100,3,zoom)).get(0);
            assertEquals(1,single.shieldLayers.size());
            assertSame(single.iconBounds,single.shieldLayers.get(0));
        }
    }
    @Test public void highZoomTriangleHasTwoTopShieldsAndOneBottomWithoutMovingBadge(){
        for(double zoom:new double[]{1,2,4,8,16}){
            KOMECompanyShieldClusters.Group group=coincident(view,zoom,100,100,10);
            KOMEMapIconPresentation.Bounds primary=group.iconBounds;
            int offset=Math.max(1,Math.min(2,Math.round(primary.width/6.0F)));
            assertEquals(3,group.shieldLayers.size());
            assertEquals(primary.x-offset,group.shieldLayers.get(0).x);
            assertEquals(primary.y-1,group.shieldLayers.get(0).y);
            assertEquals(primary.x+offset,group.shieldLayers.get(1).x);
            assertEquals(primary.y-1,group.shieldLayers.get(1).y);
            assertEquals(primary.x,group.shieldLayers.get(2).x);
            assertEquals(primary.y+1,group.shieldLayers.get(2).y);
            assertEquals(primary.x-offset,group.compositeBounds.x);
            assertEquals(primary.y-1,group.compositeBounds.y);
            assertEquals(primary.width+2*offset,group.compositeBounds.width);
            assertEquals(primary.height+2,group.compositeBounds.height);
            assertEquals("Badge remains tied to the unchanged composite bottom",primary.y+primary.height-6,group.badgeY);
            assertEquals(primary.x+primary.width+offset-1-12*KOMEConquestMapOverlay.TROOP_COUNT_TEXT_SCALE,
                group.badgeTextX,0.001F);
            assertEquals((int)Math.floor(group.badgeTextX-KOMEConquestMapOverlay.TROOP_COUNT_TEXT_SCALE),group.badgeX);
            assertTrue(group.contains(primary.x+primary.width/2,primary.y+primary.height));
        }
        for(double zoom:new double[]{.125,.25,.5}){
            KOMECompanyShieldClusters.Group low=coincident(view,zoom,100,100,10);
            int offset=Math.max(1,Math.min(2,Math.round(low.iconBounds.width/6.0F)));
            assertEquals(3,low.shieldLayers.size());
            assertEquals(low.iconBounds.x-offset,low.shieldLayers.get(0).x);
            assertEquals(low.iconBounds.x+offset,low.shieldLayers.get(1).x);
            assertEquals(low.iconBounds.y-1,low.shieldLayers.get(0).y);
            assertEquals(low.iconBounds.y-1,low.shieldLayers.get(1).y);
            assertEquals(low.iconBounds.x,low.shieldLayers.get(2).x);
            assertEquals(low.iconBounds.y+1,low.shieldLayers.get(2).y);
            assertEquals("Hidden badge keeps its original anchor",low.iconBounds.y+low.iconBounds.height-7,low.badgeY);
            assertEquals(low.iconBounds.x+low.iconBounds.width-1-12*KOMEConquestMapOverlay.TROOP_COUNT_TEXT_SCALE,
                low.badgeTextX,0.001F);
        }
    }
    @Test public void singletonCountMovesDownTwoPixelsOnlyWhenNativeLabelsAreVisible(){
        for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}){
            KOMEMapIconPresentation presentation=KOMEMapIconPresentation.at(zoom);
            KOMEMapIconPresentation.Bounds shield=KOMEMapMarkerArt.shield("gondor").bounds(presentation,100,100);
            assertEquals(shield.y-5.0F,KOMEConquestMapOverlay.singletonUnitCountTextY(shield.y),0.001F);
            assertTrue("Label origin remains above the shield",KOMEConquestMapOverlay.singletonUnitCountTextY(shield.y)<shield.y);
            assertEquals(zoom>=1,presentation.labels);
        }
        assertEquals("Generic troop-summary label stays at its original offset",-7.0F,
            KOMEConquestMapOverlay.genericTroopCountTextY(0),0.001F);
    }
    @Test public void clippedCompositeKeepsVisibleShieldAndOnlyVisiblePixelsInteractive(){
        KOMECompanyShieldClusters.Group left=coincident(view,1,4,100,10);
        assertTrue(left.visibleShield());
        assertTrue(left.compositeNeedsClip());
        assertTrue(left.contains(0,100));
        assertFalse(left.contains(-1,100));
        KOMECompanyShieldClusters.Group right=coincident(view,1,794,100,10);
        assertTrue(right.visibleShield());
        assertTrue(right.compositeNeedsClip());
        assertFalse(right.contains(800,100));
        KOMECompanyShieldClusters.Group top=coincident(view,1,100,3,10);
        assertTrue(top.visibleShield());
        assertTrue(top.compositeNeedsClip());
        assertFalse(top.contains(100,-1));
        KOMECompanyShieldClusters.Group bottom=coincident(view,1,100,594,10);
        assertTrue(bottom.visibleShield());
        assertTrue(bottom.compositeNeedsClip());
        assertFalse(bottom.contains(100,600));
    }
    @Test public void viewportEdgesAndNativeZoomsKeepPrimaryShieldAsVisibilityAuthority(){
        for(KOMEMapViewport viewport:new KOMEMapViewport[]{view,new KOMEMapViewport(0,0,312,200,0,0,1)})
            for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}){
                KOMEMapMarkerArt.Sprite shield=KOMEMapMarkerArt.shield("gondor");
                KOMEMapIconPresentation presentation=KOMEMapIconPresentation.at(zoom);
                KOMEMapIconPresentation.Bounds sample=shield.bounds(presentation,50,50);
                int halfX=sample.width/2,halfY=sample.height/2;
                int midX=(int)(viewport.left+viewport.right)/2,midY=(int)(viewport.top+viewport.bottom)/2;
                int[][] anchors={{halfX,midY},{(int)viewport.right-halfX-2,midY},
                    {midX,halfY},{midX,(int)viewport.bottom-halfY-2},{midX,midY}};
                for(int[] anchor:anchors){
                    KOMECompanyShieldClusters.Group group=coincident(viewport,zoom,anchor[0],anchor[1],10);
                    assertTrue("Primary shield remains within viewport",group.iconBounds.visible(viewport));
                    assertTrue(group.contains(group.iconBounds.x+group.iconBounds.width/2,
                        group.iconBounds.y+group.iconBounds.height/2));
                    assertFalse(group.contains((int)viewport.left-1,midY));
                    assertFalse(group.contains((int)viewport.right,midY));
                    assertFalse(group.contains(midX,(int)viewport.top-1));
                    assertFalse(group.contains(midX,(int)viewport.bottom));
                }
                KOMECompanyShieldClusters.Group outside=coincident(viewport,zoom,-100,-100,10);
                assertFalse(outside.iconBounds.visible(viewport));
            }
    }
    @Test public void singletonKeepsItsShieldAndUnitCountWhileClusterCountsCompaniesOnly(){
        KOMECompanyShieldClusters.Entry one=at("A","gondor",100,100,3,1);
        KOMECompanyShieldClusters.Group single=groups(1,one).get(0);
        assertFalse(single.clustered());assertEquals(3,single.members.get(0).marker.soldierCount);
        assertSame(one.art,single.art);assertSame(one.bounds,single.hitBounds);
        KOMECompanyShieldClusters.Group paired=groups(1,one,at("B","gondor",103,100,90,1)).get(0);
        assertTrue(paired.clustered());assertEquals(2,paired.members.size());
        assertEquals(Arrays.asList("A","B"),paired.ids());
        assertTrue(paired.contains(paired.badgeX,paired.badgeY));
        assertFalse(paired.contains(paired.hitBounds.x+paired.hitBounds.width,paired.badgeY));
    }
    @Test public void transitiveOverlapAndPacketOrderProduceTheSameGroups(){
        KOMECompanyShieldClusters.Entry a=at("A","gondor",100,100,3,1),
            b=at("B","gondor",107,100,5,1),c=at("C","gondor",114,100,2,1),
            d=at("D","gondor",250,100,8,1);
        List<KOMECompanyShieldClusters.Group> forward=groups(1,a,b,c,d),reverse=groups(1,d,c,b,a);
        assertEquals(2,forward.size());assertEquals(Arrays.asList("A","B","C"),forward.get(0).ids());
        assertEquals(Arrays.asList("D"),forward.get(1).ids());
        assertEquals(forward.get(0).ids(),reverse.get(0).ids());
        assertEquals(forward.get(1).ids(),reverse.get(1).ids());
        assertSame(a.art,forward.get(0).art);
    }
    @Test public void zoomSeparationUsesProjectedRectanglesAndLoadedPositions(){
        KOMECompanyShieldClusters.Entry farA=at("A","rohan",100,100,3,.125),
            farB=at("B","rohan",103,100,4,.125);
        assertEquals(1,groups(.125,farA,farB).size());
        KOMECompanyShieldClusters.Entry nearA=at("A","rohan",100,100,3,1),
            nearB=at("B","rohan",125,100,4,1);
        assertEquals(2,groups(1,nearA,nearB).size());
        // The cluster is anchored to a real member; grouping never alters either source anchor.
        assertSame(farA.bounds,groups(.125,farA,farB).get(0).members.get(0).bounds);
        assertEquals(125,nearB.bounds.x+nearB.bounds.width/2);
    }
    @Test public void overlappingFactionsRetainSeparateShieldsAndUniqueIds(){
        KOMECompanyShieldClusters.Entry a=at("~spot:1","mordor",100,100,50,1),
            b=at("own","gondor",101,100,3,1);
        List<KOMECompanyShieldClusters.Group> result=groups(1,b,a,a);
        assertEquals(2,result.size());
        assertEquals(Arrays.asList("own"),result.get(0).ids());
        assertEquals(Arrays.asList("~spot:1"),result.get(1).ids());
        assertSame(b.art,result.get(0).art);
        assertSame(a.art,result.get(1).art);
        assertFalse(overlap(result.get(0).hitBounds,result.get(1).hitBounds));
        assertTrue(result.get(0).contains(result.get(0).iconBounds.x+4,result.get(0).iconBounds.y+4));
        assertFalse(result.get(1).contains(result.get(0).iconBounds.x+4,result.get(0).iconBounds.y+4));
    }
    private static boolean overlap(KOMEMapIconPresentation.Bounds a,KOMEMapIconPresentation.Bounds b){
        return a.x<b.x+b.width&&b.x<a.x+a.width&&a.y<b.y+b.height&&b.y<a.y+a.height;
    }
    @Test public void severalFactionsKeepHomogeneousCountsAndStableSeparatedHitboxes(){
        KOMECompanyShieldClusters.Entry[] entries={
            at("g1","gondor",100,100,3,1),at("g2","gondor",100,100,5,1),
            at("r1","rohan",100,100,2,1),at("r2","rohan",100,100,4,1),
            at("m1","mordor",100,100,7,1)};
        List<KOMECompanyShieldClusters.Group> forward=groups(1,entries);
        List<KOMECompanyShieldClusters.Entry> reverse=new ArrayList<KOMECompanyShieldClusters.Entry>(Arrays.asList(entries));
        Collections.reverse(reverse);
        List<KOMECompanyShieldClusters.Group> backward=KOMECompanyShieldClusters.group(reverse,
            KOMEMapIconPresentation.at(1),view);
        assertEquals(3,forward.size());
        assertEquals(Arrays.asList("g1","g2"),forward.get(0).ids());
        assertEquals(Arrays.asList("m1"),forward.get(1).ids());
        assertEquals(Arrays.asList("r1","r2"),forward.get(2).ids());
        for(int i=0;i<forward.size();i++){
            assertEquals(forward.get(i).iconBounds.x,backward.get(i).iconBounds.x);
            assertEquals(forward.get(i).iconBounds.y,backward.get(i).iconBounds.y);
            assertSame(entries[i==0?0:i==1?4:2].art,forward.get(i).art);
            for(int j=0;j<i;j++)assertFalse(overlap(forward.get(i).hitBounds,forward.get(j).hitBounds));
        }
        assertTrue(forward.get(0).clustered());assertTrue(forward.get(2).clustered());
        assertFalse(forward.get(1).clustered());
        assertEquals(7,forward.get(1).members.get(0).marker.soldierCount);
    }
    @Test public void sameStrategicFactionWithDistinctValidArtNeverBecomesGeneric(){
        KOMECompanyShieldClusters.Entry nativeGondor=at("a","gondor",100,100,2,1);
        KOMECompanyShieldClusters.Entry variant=new KOMECompanyShieldClusters.Entry(
            at("b","gondor",100,100,4,1).marker,KOMEMapMarkerArt.shield("rohan"),
            KOMEMapMarkerArt.shield("rohan").bounds(KOMEMapIconPresentation.at(1),100,100),"gondor",false);
        List<KOMECompanyShieldClusters.Group> result=groups(1,nativeGondor,variant);
        assertEquals(2,result.size());
        assertSame(nativeGondor.art,result.get(0).art);
        assertSame(variant.art,result.get(1).art);
        assertFalse(overlap(result.get(0).hitBounds,result.get(1).hitBounds));
    }
    @Test public void separateFactionShieldsStayAtTheirOriginalAnchorsAtEveryZoom(){
        for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}){
            KOMECompanyShieldClusters.Entry a=at("a","gondor",100,100,3,zoom),
                b=at("b","mordor",180,100,4,zoom);
            List<KOMECompanyShieldClusters.Group> result=groups(zoom,a,b);
            assertEquals(2,result.size());
            assertSame(a.bounds,result.get(0).iconBounds);
            assertSame(b.bounds,result.get(1).iconBounds);
        }
    }
    @Test public void paintedGapDoesNotMoveNearbyDifferentFactionShields(){
        KOMEMapMarkerArt.Sprite gondor=KOMEMapMarkerArt.shield("gondor"),
            rohan=KOMEMapMarkerArt.shield("rohan");
        KOMEMapMarkerArt.VisibleRect oldG=gondor.visible,oldR=rohan.visible;
        try{
            gondor.visible=rohan.visible=new KOMEMapMarkerArt.VisibleRect(.25F,.75F,1F);
            KOMECompanyShieldClusters.Entry a=at("a","gondor",100,100,1,1),
                b=at("b","rohan",108,100,1,1);
            List<KOMECompanyShieldClusters.Group> result=groups(1,a,b);
            assertSame(a.bounds,result.get(0).iconBounds);
            assertSame("A positive painted gap must preserve the original anchor",b.bounds,result.get(1).iconBounds);
        }finally{gondor.visible=oldG;rohan.visible=oldR;}
    }
    @Test public void separatedReplacementDropsPreviousCollisionOffset(){
        List<KOMECompanyShieldClusters.Group> collision=groups(1,
            at("a","gondor",100,100,1,1),at("b","rohan",100,100,1,1));
        assertEquals(2,collision.size());
        KOMECompanyShieldClusters.Entry a=at("a","gondor",100,100,1,1),
            b=at("b","rohan",115,100,1,1);
        List<KOMECompanyShieldClusters.Group> separated=groups(1,a,b);
        assertSame("Current noncolliding marker keeps its original submitted bounds",a.bounds,separated.get(0).iconBounds);
        assertSame("Old separation is never cached on the replacement",b.bounds,separated.get(1).iconBounds);
    }
    @Test public void descriptorExtentDoesNotMoveVerticallySeparatedShieldFaces(){
        KOMECompanyShieldClusters.Entry upper=at("a","gondor",100,100,1,1),
            lower=at("b","rohan",100,110,1,1);
        upper.marker.movementItemId="minecraft:iron_boots";
        int movementTop=KOMECompanyDescriptorRenderer.movementY(upper.bounds,upper.art);
        int movementSize=KOMECompanyDescriptorRenderer.movementSize(upper.bounds);
        assertTrue("The lower movement decoration reaches the neighboring shield rectangle",
            movementTop+movementSize>lower.bounds.y);
        List<KOMECompanyShieldClusters.Group> result=groups(1,upper,lower);
        assertSame(upper.bounds,result.get(0).iconBounds);
        assertSame(lower.bounds,result.get(1).iconBounds);
    }
    @Test public void collidingFactionsSeparateMinimallyAndKeepTheirMidpoint(){
        KOMEMapMarkerArt.Sprite gondor=KOMEMapMarkerArt.shield("gondor"),
            rohan=KOMEMapMarkerArt.shield("rohan");
        KOMEMapMarkerArt.VisibleRect oldG=gondor.visible,oldR=rohan.visible;
        try{
            gondor.visible=rohan.visible=new KOMEMapMarkerArt.VisibleRect(.25F,.75F,1F);
            List<KOMECompanyShieldClusters.Group> result=groups(1,
                at("a","gondor",100,100,1,1),at("b","rohan",100,100,1,1));
            double leftRight=result.get(0).iconBounds.x+result.get(0).iconBounds.width*.75;
            double rightLeft=result.get(1).iconBounds.x+result.get(1).iconBounds.width*.25;
            assertTrue("Painted faces need a visible gap",rightLeft-leftRight>=1);
            assertTrue("Gap stays compact",rightLeft-leftRight<=2.5);
            int paintedRightPixel=(int)Math.floor(result.get(0).paintedBounds.right-.01);
            int paintedY=(int)Math.floor((result.get(0).paintedBounds.top
                +result.get(0).paintedBounds.bottom)/2);
            assertTrue(result.get(0).contains(paintedRightPixel,paintedY));
            assertFalse("Transparent rectangle margin must not steal the neighboring shield's click",
                result.get(1).contains(paintedRightPixel,paintedY));
            double midpoint=(result.get(0).iconBounds.x+result.get(0).iconBounds.width/2.0
                +result.get(1).iconBounds.x+result.get(1).iconBounds.width/2.0)/2;
            assertEquals(100,midpoint,1.0);
        }finally{gondor.visible=oldG;rohan.visible=oldR;}
    }
    @Test public void exactPaintedTouchSeparatesAndFactionOrderPersistsAcrossZoom(){
        KOMEMapMarkerArt.Sprite isengard=KOMEMapMarkerArt.shield("isengard"),
            rohan=KOMEMapMarkerArt.shield("rohan");
        KOMEMapMarkerArt.VisibleRect oldI=isengard.visible,oldR=rohan.visible;
        try{
        isengard.visible=rohan.visible=KOMEMapMarkerArt.VisibleRect.FULL;
        for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16}){
            KOMECompanyShieldClusters.Entry a=at("a","isengard",100,100,1,zoom),
                b=at("b","rohan",100,100,1,zoom);
            List<KOMECompanyShieldClusters.Group> result=groups(zoom,a,b);
            assertTrue(result.get(0).iconBounds.x<result.get(1).iconBounds.x);
            assertFalse(overlap(result.get(0).iconBounds,result.get(1).iconBounds));
        }
        KOMECompanyShieldClusters.Entry left=at("a","isengard",100,100,1,1),
            right=at("b","rohan",109,100,1,1);
        List<KOMECompanyShieldClusters.Group> touching=groups(1,left,right);
        assertTrue("Exact painted-edge contact receives a small separating gap",
            touching.get(1).iconBounds.x>right.bounds.x);
        }finally{isengard.visible=oldI;rohan.visible=oldR;}
    }
    @Test public void sameTileOrderingIgnoresOpaqueIdOrderAndPansWithoutReshuffle(){
        KOMECompanyShieldClusters.Entry isengard=at("z-opaque","isengard",100,100,1,1),
            rohan=at("a-opaque","rohan",100,100,1,1);
        List<KOMECompanyShieldClusters.Group> groups=groups(1,rohan,isengard);
        assertEquals(2,groups.size());
        KOMECompanyShieldClusters.Group left=groups.get(1),right=groups.get(0);
        assertEquals("isengard",left.members.get(0).faction);
        assertTrue(left.iconBounds.x<right.iconBounds.x);
        List<KOMECompanyShieldClusters.Group> panned=groups(1,
            at("z-opaque","isengard",137,111,1,1),at("a-opaque","rohan",137,111,1,1));
        assertEquals(left.iconBounds.x+37,panned.get(1).iconBounds.x);
        assertEquals(right.iconBounds.x+37,panned.get(0).iconBounds.x);
        assertEquals(left.iconBounds.y+11,panned.get(1).iconBounds.y);
    }
    @Test public void edgeCollisionKeepsBothShieldsInsideViewportWithoutGhostClicks(){
        KOMEMapViewport narrow=new KOMEMapViewport(0,0,50,50,0,0,1);
        for(int x:new int[]{5,44}){
            KOMECompanyShieldClusters.Entry a=at("a","isengard",x,25,1,1),
                b=at("b","rohan",x,25,1,1);
            List<KOMECompanyShieldClusters.Group> result=KOMECompanyShieldClusters.group(
                Arrays.asList(a,b),KOMEMapIconPresentation.at(1),narrow);
            assertEquals(2,result.size());
            for(KOMECompanyShieldClusters.Group group:result){
                assertTrue(group.visibleShield());
                assertFalse(group.contains(-1,25));
                assertFalse(group.contains(50,25));
            }
            assertTrue(result.get(0).paintedBounds.right<result.get(1).paintedBounds.left);
        }
    }
    @Test public void duplicateIdentityPrefersLoadedMarkerIndependentOfPacketOrder(){
        KOMECompanyShieldClusters.Entry strategic=at("A","gondor",100,100,3,1);
        KOMECompanyShieldClusters.Entry loaded=at("A","gondor",150,100,3,1);
        loaded.marker.unitCount=1;
        for(List<KOMECompanyShieldClusters.Entry> order:Arrays.asList(
                Arrays.asList(strategic,loaded),Arrays.asList(loaded,strategic))){
            List<KOMECompanyShieldClusters.Group> groups=KOMECompanyShieldClusters.group(
                order,KOMEMapIconPresentation.at(1),view);
            assertEquals(1,groups.size());assertEquals(1,groups.get(0).members.size());
            assertSame(loaded.marker,groups.get(0).members.get(0).marker);
        }
    }
    @Test public void mixedLoadedAndUnloadedClusterUsesProjectedMidpoint(){
        KOMECompanyShieldClusters.Entry unloaded=at("A","gondor",100,100,3,1);
        KOMECompanyShieldClusters.Entry loaded=at("B","gondor",103,100,5,1);
        loaded.marker.unitCount=2;
        KOMECompanyShieldClusters.Group cluster=groups(1,unloaded,loaded).get(0);
        assertEquals(102,cluster.iconBounds.x+cluster.iconBounds.width/2);
        assertEquals(100,unloaded.bounds.x+unloaded.bounds.width/2);
    }
    @Test public void threeMemberTransitiveClusterUsesAllProjectedCenters(){
        KOMECompanyShieldClusters.Entry a=at("A","gondor",100,100,3,1),
            b=at("B","gondor",107,102,5,1),c=at("C","gondor",114,104,2,1);
        b.marker.currentTile="T101";c.marker.currentTile="T102"; // GUI overlap crosses tile identity.
        KOMECompanyShieldClusters.Group cluster=groups(1,a,b,c).get(0);
        int centerX=(int)Math.round((a.bounds.x+a.bounds.width/2.0+
            b.bounds.x+b.bounds.width/2.0+c.bounds.x+c.bounds.width/2.0)/3);
        int centerY=(int)Math.round((a.bounds.y+a.bounds.height/2.0+
            b.bounds.y+b.bounds.height/2.0+c.bounds.y+c.bounds.height/2.0)/3);
        assertEquals(centerX,cluster.iconBounds.x+cluster.iconBounds.width/2);
        assertEquals(centerY,cluster.iconBounds.y+cluster.iconBounds.height/2);
        assertTrue(cluster.contains(centerX,centerY));
        assertFalse(cluster.contains(100,100));
        KOMECompanyShieldClusters.Group pair=groups(1,a,b).get(0);
        assertNotEquals("Membership changes move the displayed centroid",
            pair.iconBounds.x,cluster.iconBounds.x);
        KOMECompanyShieldClusters.Group panned=groups(1,
            at("A","gondor",120,110,3,1),at("B","gondor",127,112,5,1),
            at("C","gondor",134,114,2,1)).get(0);
        assertEquals(cluster.iconBounds.x+20,panned.iconBounds.x);
        assertEquals(cluster.iconBounds.y+10,panned.iconBounds.y);
    }
    @Test public void clusterBadgeUsesShieldBottomCornerAndOrdinaryLabelThreshold(){
        KOMECompanyShieldClusters.Group cluster=groups(1,
            at("A","gondor",100,100,3,1),at("B","gondor",103,100,5,1)).get(0);
        assertTrue(cluster.badgeX>=cluster.iconBounds.x);
        assertTrue(cluster.badgeY>=cluster.iconBounds.y);
        assertTrue(cluster.badgeY+7>=cluster.iconBounds.y+cluster.iconBounds.height-1);
        assertEquals("Subdued warm-gold badge retains full text opacity",0xFFE2BD73,
            KOMEConquestMapOverlay.CLUSTER_BADGE_TEXT_COLOR);
        assertNotEquals("Singleton unit labels retain their cream foreground",0xFFF6EEC8,
            KOMEConquestMapOverlay.CLUSTER_BADGE_TEXT_COLOR);
        for(double zoom:new double[]{.125,.25,.5,1,2,4,8,16})
            assertEquals(KOMEMapIconPresentation.at(zoom).labels,
                KOMECompanyShieldClusters.badgeVisible(KOMEMapIconPresentation.at(zoom)));
    }
    @Test public void mapSidePanelFitsBelowLayerControlsInNormalAndFullscreenViews(){
        for(KOMEMapViewport viewport:new KOMEMapViewport[]{view,
                new KOMEMapViewport(0,0,312,200,0,0,1)}){
            KOMEGuiCompanyClusterRoster.PanelLayout layout=
                new KOMEGuiCompanyClusterRoster.PanelLayout(viewport,96);
            assertTrue(layout.panel.y>=94); // Last right layer button ends at y=86.
            assertTrue(layout.panel.x>=viewport.left);
            assertTrue(layout.panel.x+layout.panel.width<=viewport.right);
            assertTrue(layout.panel.y+layout.panel.height<=viewport.bottom);
            assertTrue(layout.capacity>=1);
            assertFalse(layout.panel.contains(50,50));
        }
    }
    @Test public void rosterResolvesCurrentProjectionAndNeverCopiesSpottedPrivateDetails() throws Exception {
        KOMEClientData data=KOMEClientData.INSTANCE;
        java.util.Map<String,KOMEArmyCompany> oldCompanies=new java.util.HashMap<String,KOMEArmyCompany>(data.armyCompanies);
        List<KOMEUnitMapMarker> oldMarkers=new ArrayList<KOMEUnitMapMarker>(data.unitMapMarkers);
        Field ready=KOMEClientData.class.getDeclaredField("completeConquestSnapshot");ready.setAccessible(true);
        Field validated=KOMEMapMarkerArt.class.getDeclaredField("validated");validated.setAccessible(true);
        boolean wasReady=ready.getBoolean(data),wasValidated=validated.getBoolean(null);
        try {
            data.armyCompanies.clear();data.unitMapMarkers.clear();validated.setBoolean(null,false);
            KOMEArmyCompany own=new KOMEArmyCompany();own.id="own";own.faction="gondor";
            own.nativeFaction="gondor";own.currentTile="T100";own.status=KOMEArmyCompany.STATIONED;
            own.name="Guard";own.totalPopulation=90;
            KOMEArmyCompany spotted=new KOMEArmyCompany();spotted.id="~spot:1";spotted.faction="mordor";
            spotted.nativeFaction="mordor";spotted.currentTile="T100";
            spotted.status=KOMECompanyMapProjection.SPOTTED_STATUS;spotted.name="Private name";
            spotted.totalPopulation=500;
            data.armyCompanies.put(own.id,own);data.armyCompanies.put(spotted.id,spotted);
            KOMEUnitMapMarker ownMarker=new KOMEUnitMapMarker();ownMarker.entityId="own";
            ownMarker.currentTile="T100";ownMarker.companyName="Guard";ownMarker.soldierCount=3;
            KOMEUnitMapMarker foreign=new KOMEUnitMapMarker();foreign.entityId="~spot:1";
            foreign.currentTile="T100";foreign.companyName="Leaked private name";
            foreign.soldierCount=5;foreign.population=500;
            data.unitMapMarkers.add(ownMarker);data.unitMapMarkers.add(foreign);
            KOMEArmyCompany other=new KOMEArmyCompany();other.id="other";other.faction="gondor";
            other.currentTile="T100";other.status=KOMEArmyCompany.STATIONED;
            data.armyCompanies.put(other.id,other);
            KOMEUnitMapMarker otherMarker=new KOMEUnitMapMarker();otherMarker.entityId="other";
            otherMarker.currentTile="T100";otherMarker.soldierCount=99;
            data.unitMapMarkers.add(otherMarker);
            ready.setBoolean(data,true);long epoch=data.clientSessionEpoch;
            List<KOMEGuiCompanyClusterRoster.Row> rows=KOMEGuiCompanyClusterRoster.currentRows(
                Arrays.asList("own","~spot:1"),epoch);
            assertNotNull(rows);assertEquals(2,rows.size());assertEquals("Guard",rows.get(0).name);
            assertFalse("Tile-wide third company cannot enter the clicked cluster roster",
                rows.stream().anyMatch(row->"other".equals(row.id)));
            assertEquals("",rows.get(1).name);assertTrue(rows.get(1).spotted);
            assertEquals(5,rows.get(1).units);assertNotEquals(foreign.population,rows.get(1).units);
            own.status=KOMECompanyMapProjection.SPOTTED_STATUS;
            assertEquals("",KOMEGuiCompanyClusterRoster.currentRows(
                Arrays.asList("own","~spot:1"),epoch).get(0).name);
            data.armyCompanies.remove(spotted.id);
            assertNull(KOMEGuiCompanyClusterRoster.currentRows(Arrays.asList("own","~spot:1"),epoch));
            assertNull(KOMEGuiCompanyClusterRoster.currentRows(Arrays.asList("own","~spot:1"),epoch+1));
        } finally {
            data.armyCompanies.clear();data.armyCompanies.putAll(oldCompanies);
            data.unitMapMarkers.clear();data.unitMapMarkers.addAll(oldMarkers);
            ready.setBoolean(data,wasReady);validated.setBoolean(null,wasValidated);
        }
    }
}
