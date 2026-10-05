package kome.common.siege;

import kome.common.siege.geometry.KOMEPolygonPrism;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Static legal access sources only; this set never creates segment-to-segment transit. */
public final class KOMEWallZone extends KOMEAbstractSiegeZone {
    private final Set<String> accessibleFromNormalSegmentIds;
    public KOMEWallZone(String id,String label,KOMEPolygonPrism prism,Collection<String> accessibleFrom){
        super(id,label,prism);TreeSet<String> copy=new TreeSet<String>();
        if(accessibleFrom!=null)for(String value:accessibleFrom)copy.add(KOMESiegeIds.id(value));
        accessibleFromNormalSegmentIds=Collections.unmodifiableSet(copy);
    }
    public Set<String> getAccessibleFromNormalSegmentIds(){return accessibleFromNormalSegmentIds;}
    @Override public boolean equals(Object o){return o instanceof KOMEWallZone&&baseEquals((KOMEWallZone)o)
        &&accessibleFromNormalSegmentIds.equals(((KOMEWallZone)o).accessibleFromNormalSegmentIds);}
    @Override public int hashCode(){return 31*baseHashCode()+accessibleFromNormalSegmentIds.hashCode();}
}
