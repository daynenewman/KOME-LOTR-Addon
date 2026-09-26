package kome.common.siege;
import kome.common.siege.geometry.KOMEPolygonPrism;
public final class KOMEExteriorDeploymentArea extends KOMEAbstractSiegeZone {
    public KOMEExteriorDeploymentArea(String id,String label,KOMEPolygonPrism prism){super(id,label,prism);}
    @Override public boolean equals(Object o){return o instanceof KOMEExteriorDeploymentArea&&baseEquals((KOMEExteriorDeploymentArea)o);}
    @Override public int hashCode(){return baseHashCode();}
}
