package kome.common.siege.geometry;

import kome.common.siege.validation.KOMEValidationCode;
import kome.common.siege.validation.KOMEValidationIssue;
import kome.common.siege.validation.KOMEValidationResult;
import kome.common.siege.validation.KOMEValidationSeverity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure structural validation for a simple authored polygon. */
public final class KOMEPolygonValidator {
    private KOMEPolygonValidator() {}
    public static KOMEValidationResult validate(KOMEPolygon polygon,String subjectId){
        List<KOMEValidationIssue> issues=new ArrayList<KOMEValidationIssue>();
        List<KOMEXZPoint> points=polygon.getVertices();int count=points.size();
        Set<KOMEXZPoint> distinct=new HashSet<KOMEXZPoint>(points);
        if(distinct.size()<3)add(issues,KOMEValidationCode.POLYGON_TOO_FEW_DISTINCT_VERTICES,"A polygon requires at least three distinct vertices.",subjectId);
        boolean consecutive=false;
        for(int i=0;i<count;i++)if(points.get(i).equals(points.get((i+1)%count))){consecutive=true;break;}
        if(consecutive){
            add(issues,KOMEValidationCode.POLYGON_CONSECUTIVE_DUPLICATE,"Consecutive polygon vertices cannot be equal.",subjectId);
            add(issues,KOMEValidationCode.POLYGON_ZERO_LENGTH_EDGE,"Polygon edges must have positive length.",subjectId);
        }
        if(hasNonAdjacentRepeat(points))add(issues,KOMEValidationCode.POLYGON_REPEATED_VERTEX,"A nonadjacent repeated vertex creates a pinch or self-touch.",subjectId);
        if(polygon.getSignedAreaTwice().signum()==0)add(issues,KOMEValidationCode.POLYGON_ZERO_AREA,"Polygon signed area must be nonzero.",subjectId);
        if(hasInvalidIntersection(points))add(issues,KOMEValidationCode.POLYGON_SELF_INTERSECTION,"Polygon edges may not cross, overlap, or self-touch.",subjectId);
        return new KOMEValidationResult(issues);
    }
    private static boolean hasNonAdjacentRepeat(List<KOMEXZPoint> points){
        int n=points.size();
        for(int i=0;i<n;i++)for(int j=i+1;j<n;j++)
            if(points.get(i).equals(points.get(j))&&j!=i+1&&!(i==0&&j==n-1))return true;
        return false;
    }
    private static boolean hasInvalidIntersection(List<KOMEXZPoint> points){
        int n=points.size();if(n<2)return false;
        for(int i=0;i<n;i++)for(int j=i+1;j<n;j++){
            boolean adjacent=j==i+1||i==0&&j==n-1;
            KOMEGeometryPredicates.SegmentIntersection hit=KOMEGeometryPredicates.segmentIntersection(
                points.get(i),points.get((i+1)%n),points.get(j),points.get((j+1)%n));
            if(adjacent){if(hit==KOMEGeometryPredicates.SegmentIntersection.PROPER
                    ||hit==KOMEGeometryPredicates.SegmentIntersection.COLLINEAR_OVERLAP)return true;}
            else if(hit!=KOMEGeometryPredicates.SegmentIntersection.NONE)return true;
        }
        return false;
    }
    private static void add(List<KOMEValidationIssue> issues,KOMEValidationCode code,String message,String subject){
        issues.add(new KOMEValidationIssue(KOMEValidationSeverity.ERROR,code,message,subject));
    }
}
