package kome.common.siege.geometry;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immutable ordered X/Z polygon; invalid authoring shapes remain available to validation. */
public final class KOMEPolygon {
    private final List<KOMEXZPoint> vertices;
    private final boolean hasBounds;
    private final int minX, maxX, minZ, maxZ;
    private final BigInteger signedAreaTwice;

    public KOMEPolygon(List<KOMEXZPoint> source) {
        vertices = immutableCopy(source);
        hasBounds = !vertices.isEmpty();
        int[] bounds = bounds(vertices);
        minX=bounds[0]; maxX=bounds[1]; minZ=bounds[2]; maxZ=bounds[3];
        signedAreaTwice = area(vertices);
    }
    private static List<KOMEXZPoint> immutableCopy(List<KOMEXZPoint> source){
        if(source==null)throw new IllegalArgumentException();
        List<KOMEXZPoint> copy=new ArrayList<KOMEXZPoint>(source);
        if(copy.contains(null))throw new IllegalArgumentException();
        return Collections.unmodifiableList(copy);
    }
    private static int[] bounds(List<KOMEXZPoint> points){
        if(points.isEmpty())return new int[]{0,0,0,0};
        int loX=Integer.MAX_VALUE,hiX=Integer.MIN_VALUE,loZ=Integer.MAX_VALUE,hiZ=Integer.MIN_VALUE;
        for(KOMEXZPoint p:points){loX=Math.min(loX,p.getX());hiX=Math.max(hiX,p.getX());loZ=Math.min(loZ,p.getZ());hiZ=Math.max(hiZ,p.getZ());}
        return new int[]{loX,hiX,loZ,hiZ};
    }
    private static BigInteger area(List<KOMEXZPoint> points){
        BigInteger sum=BigInteger.ZERO;
        for(int i=0;i<points.size();i++){
            KOMEXZPoint a=points.get(i),b=points.get((i+1)%points.size());
            sum=sum.add(BigInteger.valueOf(a.getX()).multiply(BigInteger.valueOf(b.getZ())))
                .subtract(BigInteger.valueOf(b.getX()).multiply(BigInteger.valueOf(a.getZ())));
        }
        return sum;
    }
    public static KOMEPolygon of(KOMEXZPoint... points){return new KOMEPolygon(Arrays.asList(points));}
    public List<KOMEXZPoint> getVertices(){return vertices;}
    public int size(){return vertices.size();}
    public boolean hasBounds(){return hasBounds;}
    public int getMinX(){return minX;} public int getMaxX(){return maxX;}
    public int getMinZ(){return minZ;} public int getMaxZ(){return maxZ;}
    public BigInteger getSignedAreaTwice(){return signedAreaTwice;}

    /** Exact integer winding classification, independent of polygon orientation. */
    public KOMEPointClassification classify(KOMEXZPoint point){
        if(point==null||vertices.size()<3||!hasBounds||point.getX()<minX||point.getX()>maxX
                ||point.getZ()<minZ||point.getZ()>maxZ)return KOMEPointClassification.OUTSIDE;
        int winding=0;
        for(int i=0;i<vertices.size();i++){
            KOMEXZPoint a=vertices.get(i),b=vertices.get((i+1)%vertices.size());
            int orientation=KOMEGeometryPredicates.orientation(a,b,point);
            if(orientation==0&&KOMEGeometryPredicates.isOnSegment(a,b,point))return KOMEPointClassification.BOUNDARY;
            if(a.getZ()<=point.getZ()){if(b.getZ()>point.getZ()&&orientation>0)winding++;}
            else if(b.getZ()<=point.getZ()&&orientation<0)winding--;
        }
        return winding==0?KOMEPointClassification.OUTSIDE:KOMEPointClassification.INTERIOR;
    }
    @Override public boolean equals(Object o){return o instanceof KOMEPolygon&&vertices.equals(((KOMEPolygon)o).vertices);}
    @Override public int hashCode(){return vertices.hashCode();}
}
