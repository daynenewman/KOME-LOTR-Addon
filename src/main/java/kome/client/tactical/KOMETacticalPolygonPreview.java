package kome.client.tactical;

import java.util.*;
import kome.common.siege.geometry.*;

/** Shared exact world-grid corner selection and display-only polygon fill. No authoring conversion. */
public final class KOMETacticalPolygonPreview {
    private KOMETacticalPolygonPreview() { }
    /** West/east are -X/+X; north/south are -Z/+Z, independent of facing or ray-hit face. */
    public enum Corner {
        NW(0,0), NE(1,0), SE(1,1), SW(0,1);
        private final int dx,dz;
        Corner(int dx,int dz) { this.dx=dx; this.dz=dz; }
        public Corner next() { return values()[(ordinal()+1)%4]; }
        public KOMEXZPoint point(int x,int z) {
            if (dx == 1 && x == Integer.MAX_VALUE || dz == 1 && z == Integer.MAX_VALUE)
                throw new IllegalArgumentException("Corner exceeds integer geometry bounds.");
            return new KOMEXZPoint(x+dx,z+dz);
        }
    }
    /** Exact ear selection for the translucent preview only; never changes the draft polygon. */
    public static List<KOMEXZPoint> triangles(KOMEPolygon polygon) {
        if (!KOMEPolygonValidator.validate(polygon,"fill").isValid()) return Collections.emptyList();
        List<KOMEXZPoint> loop=new ArrayList<KOMEXZPoint>(polygon.getVertices()), result=new ArrayList<KOMEXZPoint>();
        int winding=polygon.getSignedAreaTwice().signum();
        while(loop.size()>3) {
            boolean found=false;
            for(int i=0;i<loop.size();i++) {
                KOMEXZPoint a=loop.get((i+loop.size()-1)%loop.size()), b=loop.get(i), c=loop.get((i+1)%loop.size());
                if(KOMEGeometryPredicates.orientation(a,b,c)*winding<=0) continue;
                boolean occupied=false;
                for(KOMEXZPoint p:loop) if(!p.equals(a)&&!p.equals(b)&&!p.equals(c)
                        && KOMEGeometryPredicates.orientation(a,b,p)*winding>=0
                        && KOMEGeometryPredicates.orientation(b,c,p)*winding>=0
                        && KOMEGeometryPredicates.orientation(c,a,p)*winding>=0) { occupied=true; break; }
                if(occupied) continue;
                result.add(a);result.add(b);result.add(c);loop.remove(i);found=true;break;
            }
            if(!found) return Collections.emptyList();
        }
        result.addAll(loop); return Collections.unmodifiableList(result);
    }
}
