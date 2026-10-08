package kome.common.siege;

import kome.common.siege.geometry.KOMEPolygon;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEXZPoint;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

final class KOMESiegeFixtures {
    private KOMESiegeFixtures(){}
    static KOMEPolygon rectangle(int minX,int minZ,int maxX,int maxZ){
        return KOMEPolygon.of(new KOMEXZPoint(minX,minZ),new KOMEXZPoint(maxX,minZ),
            new KOMEXZPoint(maxX,maxZ),new KOMEXZPoint(minX,maxZ));
    }
    static KOMEPolygonPrism prism(int minX,int minZ,int maxX,int maxZ,int minY,int maxYExclusive){
        return new KOMEPolygonPrism(rectangle(minX,minZ,maxX,maxZ),minY,maxYExclusive);
    }
    static KOMENormalSegment normal(String id,int minX,int minZ,int maxX,int maxZ){
        return new KOMENormalSegment(id,id,prism(minX,minZ,maxX,maxZ,0,10));
    }
    static KOMETransitionZone transition(String id,int minX,int minZ,int maxX,int maxZ){
        return new KOMETransitionZone(id,id,prism(minX,minZ,maxX,maxZ,0,10));
    }
    static KOMEWallZone wall(String id,KOMEPolygonPrism prism,String... access){
        return new KOMEWallZone(id,id,prism,Arrays.asList(access));
    }
    static KOMESiegeComplex complex(Collection<KOMENormalSegment> normals,Collection<KOMEWallZone> walls,
            Collection<KOMETransitionZone> transitions,
            Collection<KOMESiegeConnection> connections){
        return new KOMESiegeComplex("FIXTURE-COMPLEX","t277",0,0,normals,walls,transitions,null,connections);
    }
    static <T> Collection<T> none(){return Collections.emptyList();}
}
