package kome.common.siege.geometry;

import java.math.BigInteger;
import java.util.List;

/** Exact integer predicates for authored tactical geometry. */
public final class KOMEGeometryPredicates {
    public enum SegmentIntersection { NONE, TOUCH, PROPER, COLLINEAR_OVERLAP }
    private KOMEGeometryPredicates() {}

    public static int orientation(KOMEXZPoint a,KOMEXZPoint b,KOMEXZPoint p){
        BigInteger ax=BigInteger.valueOf((long)b.getX()-a.getX());
        BigInteger az=BigInteger.valueOf((long)b.getZ()-a.getZ());
        BigInteger px=BigInteger.valueOf((long)p.getX()-a.getX());
        BigInteger pz=BigInteger.valueOf((long)p.getZ()-a.getZ());
        return ax.multiply(pz).subtract(az.multiply(px)).signum();
    }
    public static boolean isOnSegment(KOMEXZPoint a,KOMEXZPoint b,KOMEXZPoint p){
        return orientation(a,b,p)==0&&p.getX()>=Math.min(a.getX(),b.getX())&&p.getX()<=Math.max(a.getX(),b.getX())
            &&p.getZ()>=Math.min(a.getZ(),b.getZ())&&p.getZ()<=Math.max(a.getZ(),b.getZ());
    }
    public static SegmentIntersection segmentIntersection(KOMEXZPoint a1,KOMEXZPoint a2,KOMEXZPoint b1,KOMEXZPoint b2){
        int o1=orientation(a1,a2,b1),o2=orientation(a1,a2,b2),o3=orientation(b1,b2,a1),o4=orientation(b1,b2,a2);
        if(o1==0&&o2==0&&o3==0&&o4==0){
            int start,end;
            if(a1.getX()!=a2.getX()||b1.getX()!=b2.getX()){
                start=Math.max(Math.min(a1.getX(),a2.getX()),Math.min(b1.getX(),b2.getX()));
                end=Math.min(Math.max(a1.getX(),a2.getX()),Math.max(b1.getX(),b2.getX()));
            }else{
                start=Math.max(Math.min(a1.getZ(),a2.getZ()),Math.min(b1.getZ(),b2.getZ()));
                end=Math.min(Math.max(a1.getZ(),a2.getZ()),Math.max(b1.getZ(),b2.getZ()));
            }
            return end<start?SegmentIntersection.NONE:end==start?SegmentIntersection.TOUCH:SegmentIntersection.COLLINEAR_OVERLAP;
        }
        if(o1*o2<0&&o3*o4<0)return SegmentIntersection.PROPER;
        if(o1==0&&isOnSegment(a1,a2,b1)||o2==0&&isOnSegment(a1,a2,b2)
                ||o3==0&&isOnSegment(b1,b2,a1)||o4==0&&isOnSegment(b1,b2,a2))return SegmentIntersection.TOUCH;
        return SegmentIntersection.NONE;
    }

    public static boolean polygonsTouchOrOverlap(KOMEPolygon a,KOMEPolygon b){
        if(!boundsTouch(a,b))return false;
        List<KOMEXZPoint> av=a.getVertices(),bv=b.getVertices();
        for(int i=0;i<av.size();i++)for(int j=0;j<bv.size();j++)
            if(segmentIntersection(av.get(i),av.get((i+1)%av.size()),bv.get(j),bv.get((j+1)%bv.size()))!=SegmentIntersection.NONE)return true;
        return anyContained(av,b,false)||anyContained(bv,a,false);
    }

    /** Positive-area X/Z interior intersection; boundary-only contact is false. */
    public static boolean polygonsHaveInteriorOverlap(KOMEPolygon a,KOMEPolygon b){
        if(!boundsInterior(a,b))return false;
        List<KOMEXZPoint> av=a.getVertices(),bv=b.getVertices();
        int areaA=a.getSignedAreaTwice().signum(),areaB=b.getSignedAreaTwice().signum();
        for(int i=0;i<av.size();i++)for(int j=0;j<bv.size();j++){
            KOMEXZPoint a1=av.get(i),a2=av.get((i+1)%av.size()),b1=bv.get(j),b2=bv.get((j+1)%bv.size());
            SegmentIntersection hit=segmentIntersection(a1,a2,b1,b2);
            if(hit==SegmentIntersection.PROPER)return true;
            if(hit==SegmentIntersection.COLLINEAR_OVERLAP&&areaA!=0&&areaB!=0&&sameInteriorSide(a1,a2,b1,b2,areaA,areaB))return true;
        }
        return anyContained(av,b,true)||anyContained(bv,a,true);
    }

    /** Closed polygon containment used by endpoint inference. */
    public static boolean polygonContainsPolygon(KOMEPolygon outer,KOMEPolygon inner){
        if(!outer.hasBounds()||!inner.hasBounds()||inner.getMinX()<outer.getMinX()||inner.getMaxX()>outer.getMaxX()
                ||inner.getMinZ()<outer.getMinZ()||inner.getMaxZ()>outer.getMaxZ())return false;
        for(KOMEXZPoint point:inner.getVertices())if(outer.classify(point)==KOMEPointClassification.OUTSIDE)return false;
        List<KOMEXZPoint> a=outer.getVertices(),b=inner.getVertices();
        for(int i=0;i<a.size();i++)for(int j=0;j<b.size();j++)
            if(segmentIntersection(a.get(i),a.get((i+1)%a.size()),b.get(j),b.get((j+1)%b.size()))==SegmentIntersection.PROPER)return false;
        return true;
    }

    public static boolean prismsHaveInteriorOverlap(KOMEPolygonPrism a,KOMEPolygonPrism b){
        return Math.max(a.getMinYInclusive(),b.getMinYInclusive())<Math.min(a.getMaxYExclusive(),b.getMaxYExclusive())
            &&polygonsHaveInteriorOverlap(a.getPolygon(),b.getPolygon());
    }
    public static boolean prismsTouchOrOverlap(KOMEPolygonPrism a,KOMEPolygonPrism b){
        return Math.max(a.getMinYInclusive(),b.getMinYInclusive())<=Math.min(a.getMaxYExclusive(),b.getMaxYExclusive())
            &&polygonsTouchOrOverlap(a.getPolygon(),b.getPolygon());
    }
    public static boolean prismContainsPrism(KOMEPolygonPrism outer,KOMEPolygonPrism inner){
        return outer.getMinYInclusive()<=inner.getMinYInclusive()&&outer.getMaxYExclusive()>=inner.getMaxYExclusive()
            &&polygonContainsPolygon(outer.getPolygon(),inner.getPolygon());
    }

    private static boolean boundsTouch(KOMEPolygon a,KOMEPolygon b){
        return a.hasBounds()&&b.hasBounds()&&Math.max(a.getMinX(),b.getMinX())<=Math.min(a.getMaxX(),b.getMaxX())
            &&Math.max(a.getMinZ(),b.getMinZ())<=Math.min(a.getMaxZ(),b.getMaxZ());
    }
    private static boolean boundsInterior(KOMEPolygon a,KOMEPolygon b){
        return a.hasBounds()&&b.hasBounds()&&Math.max(a.getMinX(),b.getMinX())<Math.min(a.getMaxX(),b.getMaxX())
            &&Math.max(a.getMinZ(),b.getMinZ())<Math.min(a.getMaxZ(),b.getMaxZ());
    }
    private static boolean anyContained(List<KOMEXZPoint> points,KOMEPolygon polygon,boolean strict){
        for(KOMEXZPoint point:points){KOMEPointClassification c=polygon.classify(point);
            if(c==KOMEPointClassification.INTERIOR||!strict&&c==KOMEPointClassification.BOUNDARY)return true;}
        return false;
    }
    private static boolean sameInteriorSide(KOMEXZPoint a1,KOMEXZPoint a2,KOMEXZPoint b1,KOMEXZPoint b2,int areaA,int areaB){
        BigInteger ax=BigInteger.valueOf((long)a2.getX()-a1.getX()),az=BigInteger.valueOf((long)a2.getZ()-a1.getZ());
        BigInteger bx=BigInteger.valueOf((long)b2.getX()-b1.getX()),bz=BigInteger.valueOf((long)b2.getZ()-b1.getZ());
        int alignment=ax.multiply(bx).add(az.multiply(bz)).signum();
        return areaA==(alignment>=0?areaB:-areaB);
    }
}
