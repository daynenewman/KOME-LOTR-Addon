package kome.common.siege;

import kome.common.siege.geometry.KOMEPolygonPrism;

abstract class KOMEAbstractSiegeZone implements KOMESiegeZone {
    private final String id,label;
    private final KOMEPolygonPrism prism;
    KOMEAbstractSiegeZone(String id,String label,KOMEPolygonPrism prism){
        if(prism==null)throw new IllegalArgumentException("Zone geometry is required.");
        this.id=KOMESiegeIds.id(id);this.label=label==null?"":label.trim();this.prism=prism;
    }
    public final String getId(){return id;}
    public final String getLabel(){return label;}
    public final KOMEPolygonPrism getPrism(){return prism;}
    final boolean baseEquals(KOMEAbstractSiegeZone other){return id.equals(other.id)&&label.equals(other.label)&&prism.equals(other.prism);}
    final int baseHashCode(){return 31*(31*id.hashCode()+label.hashCode())+prism.hashCode();}
}
