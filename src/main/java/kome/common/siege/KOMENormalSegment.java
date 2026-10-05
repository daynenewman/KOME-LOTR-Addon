package kome.common.siege;
import kome.common.siege.geometry.KOMEPolygonPrism;
public final class KOMENormalSegment extends KOMEAbstractSiegeZone {
    public KOMENormalSegment(String id,String label,KOMEPolygonPrism prism){super(id,label,prism);}
    @Override public boolean equals(Object o){return o instanceof KOMENormalSegment&&baseEquals((KOMENormalSegment)o);}
    @Override public int hashCode(){return baseHashCode();}
}
