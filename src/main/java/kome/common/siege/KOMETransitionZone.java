package kome.common.siege;
import kome.common.siege.geometry.KOMEPolygonPrism;
public final class KOMETransitionZone extends KOMEAbstractSiegeZone {
    public KOMETransitionZone(String id,String label,KOMEPolygonPrism prism){super(id,label,prism);}
    @Override public boolean equals(Object o){return o instanceof KOMETransitionZone&&baseEquals((KOMETransitionZone)o);}
    @Override public int hashCode(){return baseHashCode();}
}
