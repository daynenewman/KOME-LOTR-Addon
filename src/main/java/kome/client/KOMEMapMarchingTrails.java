package kome.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEConquestTile;

/** Client-only remaining-route selection and viewport-bounded map-space dashes. */
final class KOMEMapMarchingTrails {
    static final double DASH_MAP=4.0, GAP_MAP=2.0;
    static final long ILLUMINATION_CYCLE_MILLIS=1500L;
    static final Style ACTIVE=new Style(0x33261C,1.5F,false);
    static final Style PREVIEW=new Style(0x3E9FD8,1.5F,true);
    static final class Style {
        final int rgb;
        final float width;
        final boolean preview;
        private Style(int rgb,float width,boolean preview){this.rgb=rgb;this.width=width;this.preview=preview;}
        int colorAt(double zoom,int legIndex){
            int alpha=preview?255:KOMEMapIconPresentation.at(zoom).alpha;
            if(!preview&&legIndex>0)alpha=(alpha+2)/4;
            return alpha<<24|rgb;
        }
    }
    static int colorForDash(int firstColor,int futureColor,int legIndex,long dashIndex,
            int illuminated,boolean animate){
        return legIndex==0&&(!animate||dashIndex<illuminated)?firstColor:futureColor;
    }
    static boolean animates(KOMEArmyMovementOrder order){
        if(order==null||order.stopped||order.spawnRetryPaused||order.retreating)return false;
        String status=order.status;
        return KOMEArmyMovementOrder.MOVING.equals(status)
            || KOMEArmyMovementOrder.PENDING_SPAWN.equals(status)
            || KOMEArmyMovementOrder.SPAWNING.equals(status)
            || KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(status);
    }
    static int firstLegDashCount(double length){
        if(!(length>0))return 0;
        return (int)Math.min(Integer.MAX_VALUE,Math.ceil(length/(DASH_MAP+GAP_MAP)));
    }
    static int illuminatedDashes(int count,long nowMillis){
        if(count<=0)return 0;
        long phase=Math.floorMod(nowMillis,ILLUMINATION_CYCLE_MILLIS);
        return 1+(int)(phase*count/ILLUMINATION_CYCLE_MILLIS);
    }
    interface SegmentSink {void segment(double x1,double y1,double x2,double y2);}
    private interface IndexedSegmentSink {void segment(int leg,long dash,double x1,double y1,double x2,double y2);}
    interface QuadSink {void quad(int leg,double ax,double ay,double bx,double by,
        double cx,double cy,double dx,double dy);}
    interface IndexedQuadSink {void quad(int leg,long dash,double ax,double ay,double bx,double by,
        double cx,double cy,double dx,double dy);}

    static List<String> remainingTiles(KOMEArmyMovementOrder order){
        if(order==null||!order.isMoving()||order.routeTiles.size()<2)return Collections.emptyList();
        int index=order.currentRouteIndex;
        if(index<0||index>=order.routeTiles.size()-1)return Collections.emptyList();
        String current=KOMEConquestTile.normalizeId(order.currentTile);
        if(!KOMEConquestTile.isCanonicalTileId(current)
                ||!current.equals(KOMEConquestTile.normalizeId(order.routeTiles.get(index))))return Collections.emptyList();
        List<String> result=new ArrayList<String>(order.routeTiles.size()-index);
        for(int i=index;i<order.routeTiles.size();i++){
            String tile=KOMEConquestTile.normalizeId(order.routeTiles.get(i));
            if(!KOMEConquestTile.isCanonicalTileId(tile))return Collections.emptyList();
            result.add(tile);
        }
        return result;
    }

    static void emitMapDashes(double x1,double y1,double x2,double y2,KOMEMapViewport view,SegmentSink sink){
        double length=Math.hypot(x2-x1,y2-y1);
        emitMapDashes(x1,y1,x2,y2,view,length,0,0,new double[2],
            (leg,dash,a,b,c,d)->sink.segment(a,b,c,d));
    }
    private static void emitMapDashes(double x1,double y1,double x2,double y2,KOMEMapViewport view,
            double length,double offset,int leg,double[] interval,IndexedSegmentSink sink){
        double sx1=view.screenX(x1),sy1=view.screenY(y1);
        double sx2=view.screenX(x2),sy2=view.screenY(y2);
        if(Math.max(sx1,sx2)<view.left||Math.min(sx1,sx2)>view.right
                ||Math.max(sy1,sy2)<view.top||Math.min(sy1,sy2)>view.bottom)return;
        if(length<0.001)return;
        double screenDx=sx2-sx1,screenDy=sy2-sy1;
        interval[0]=0.0;interval[1]=1.0;
        if(!clip(-screenDx,sx1-view.left,interval)||!clip(screenDx,view.right-sx1,interval)
                ||!clip(-screenDy,sy1-view.top,interval)||!clip(screenDy,view.bottom-sy1,interval))return;
        double visibleStart=interval[0]*length,visibleEnd=interval[1]*length;
        double pitch=DASH_MAP+GAP_MAP;
        for(long dash=(long)Math.floor((offset+visibleStart)/pitch);
                dash*pitch-offset<visibleEnd;dash++){
            double start=Math.max(visibleStart,dash*pitch-offset);
            double end=Math.min(visibleEnd,dash*pitch+DASH_MAP-offset);
            if(end<=start)continue;
            double from=start/length,to=end/length;
            sink.segment(leg,dash,sx1+screenDx*from,sy1+screenDy*from,
                sx1+screenDx*to,sy1+screenDy*to);
        }
    }
    private static boolean clip(double p,double q,double[] interval){
        if(Math.abs(p)<1e-12)return q>=0;
        double ratio=q/p;
        if(p<0){if(ratio>interval[1])return false;interval[0]=Math.max(interval[0],ratio);}
        else {if(ratio<interval[0])return false;interval[1]=Math.min(interval[1],ratio);}
        return true;
    }
    static void emitRoute(List<double[]> points,KOMEMapViewport view,SegmentSink sink){
        emitIndexedRoute(points,view,(leg,dash,x1,y1,x2,y2)->sink.segment(x1,y1,x2,y2));
    }
    private static void emitIndexedRoute(List<double[]> points,KOMEMapViewport view,IndexedSegmentSink sink){
        double[] interval=new double[2]; // Reuse one clipping interval for every leg in the route.
        double offset=0;
        for(int i=1;i<points.size();i++){
            double[] a=points.get(i-1),b=points.get(i);
            if(a==null||b==null){offset=0;continue;}
            double length=Math.hypot(b[0]-a[0],b[1]-a[1]);
            emitMapDashes(a[0],a[1],b[0],b[1],view,length,offset,i-1,interval,sink);
            offset+=length;
        }
    }
    static void emitRouteQuads(List<double[]> points,KOMEMapViewport view,double width,QuadSink sink){
        emitRouteQuadsIndexed(points,view,width,(leg,dash,ax,ay,bx,by,cx,cy,dx,dy)->
            sink.quad(leg,ax,ay,bx,by,cx,cy,dx,dy));
    }
    static void emitRouteQuadsIndexed(List<double[]> points,KOMEMapViewport view,double width,IndexedQuadSink sink){
        emitIndexedRoute(points,view,(leg,dash,x1,y1,x2,y2)->{
            double length=Math.hypot(x2-x1,y2-y1);
            if(length<0.001)return;
            double nx=-(y2-y1)*width/(2*length),ny=(x2-x1)*width/(2*length);
            sink.quad(leg,dash,x1+nx,y1+ny,x2+nx,y2+ny,x2-nx,y2-ny,x1-nx,y1-ny);
        });
    }
}
