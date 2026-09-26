package kome.common.siege.geometry;

/** Immutable finite vertical prism over an authored X/Z polygon. */
public final class KOMEPolygonPrism {
    private final KOMEPolygon polygon;
    private final int minYInclusive,maxYExclusive;
    public KOMEPolygonPrism(KOMEPolygon polygon,int minYInclusive,int maxYExclusive){
        if(polygon==null)throw new IllegalArgumentException("A prism polygon is required.");
        this.polygon=polygon;this.minYInclusive=minYInclusive;this.maxYExclusive=maxYExclusive;
    }
    public static KOMEPolygonPrism fromInclusiveBlockHeights(KOMEPolygon polygon,int minY,int maxY){
        if(maxY==Integer.MAX_VALUE)throw new IllegalArgumentException("Inclusive maximum Y cannot be Integer.MAX_VALUE.");
        return new KOMEPolygonPrism(polygon,minY,maxY+1);
    }
    public KOMEPolygon getPolygon(){return polygon;}
    public int getMinYInclusive(){return minYInclusive;}
    public int getMaxYExclusive(){return maxYExclusive;}
    public int getMaxYInclusive(){return maxYExclusive-1;}
    public int getMinX(){return polygon.getMinX();} public int getMaxX(){return polygon.getMaxX();}
    public int getMinZ(){return polygon.getMinZ();} public int getMaxZ(){return polygon.getMaxZ();}
    public boolean hasValidYRange(){return minYInclusive<maxYExclusive;}
    /** Half-open block Y membership; BOUNDARY describes only the X/Z polygon. */
    public KOMEPointClassification classify(int x,int y,int z){
        return y<minYInclusive||y>=maxYExclusive?KOMEPointClassification.OUTSIDE:polygon.classify(new KOMEXZPoint(x,z));
    }
    @Override public boolean equals(Object o){
        if(!(o instanceof KOMEPolygonPrism))return false;KOMEPolygonPrism p=(KOMEPolygonPrism)o;
        return minYInclusive==p.minYInclusive&&maxYExclusive==p.maxYExclusive&&polygon.equals(p.polygon);
    }
    @Override public int hashCode(){return 31*(31*polygon.hashCode()+minYInclusive)+maxYExclusive;}
}
