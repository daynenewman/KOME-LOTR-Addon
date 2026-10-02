package kome.common.siege;

import kome.common.siege.geometry.KOMEGeometryPredicates;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEPolygonValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure editor helper. Successful endpoints must still be persisted explicitly. */
public final class KOMEConnectionEndpointInference {
    public enum Status {
        SUCCESS,
        TRANSITION_NOT_FOUND,
        INVALID_TRANSITION_GEOMETRY,
        NO_ENDPOINT_CANDIDATES,
        EXTERIOR_NOT_ESTABLISHED,
        TOO_MANY_NORMAL_ENDPOINTS,
        AMBIGUOUS_CORRIDOR,
        INVALID_NORMAL_GEOMETRY,
        DUPLICATE_NORMAL_ID
    }
    private KOMEConnectionEndpointInference() {}

    public static Result infer(KOMESiegeComplex complex,String transitionZoneId){
        KOMETransitionZone transition=complex==null?null:complex.findTransitionZone(transitionZoneId);
        if(transition==null)return Result.failure(Status.TRANSITION_NOT_FOUND);
        if(!transition.getPrism().hasValidYRange()
                ||!KOMEPolygonValidator.validate(transition.getPrism().getPolygon(),transition.getId()).isValid())
            return Result.failure(Status.INVALID_TRANSITION_GEOMETRY);
        // Even a distant duplicate makes an inferred local endpoint ID ambiguous.
        Set<String> normalIds=new HashSet<String>();
        for(KOMENormalSegment normal:complex.getNormalSegments())
            if(!normalIds.add(normal.getId()))return Result.failure(Status.DUPLICATE_NORMAL_ID);
        // Validate every potentially relevant normal before any exact intersection checks.
        // Whole-complex validation still diagnoses malformed geometry skipped here.
        List<KOMENormalSegment> relevant=new ArrayList<KOMENormalSegment>();
        for(KOMENormalSegment normal:complex.getNormalSegments()){
            if(!mayAffectTransition(normal.getPrism(),transition.getPrism()))continue;
            if(!normal.getPrism().hasValidYRange()
                    ||!KOMEPolygonValidator.validate(normal.getPrism().getPolygon(),normal.getId()).isValid())
                return Result.failure(Status.INVALID_NORMAL_GEOMETRY);
            relevant.add(normal);
        }
        for(KOMETransitionZone other:complex.getTransitionZones())
            if(other!=transition&&KOMEGeometryPredicates.prismsHaveInteriorOverlap(transition.getPrism(),other.getPrism()))
                return Result.failure(Status.AMBIGUOUS_CORRIDOR);
        for(KOMEWallZone wall:complex.getWallZones())
            if(KOMEGeometryPredicates.prismsHaveInteriorOverlap(transition.getPrism(),wall.getPrism()))
                return Result.failure(Status.AMBIGUOUS_CORRIDOR);

        List<KOMENormalSegment> candidates=new ArrayList<KOMENormalSegment>();
        for(KOMENormalSegment normal:relevant)
            if(KOMEGeometryPredicates.prismsTouchOrOverlap(transition.getPrism(),normal.getPrism()))
                candidates.add(normal);
        Collections.sort(candidates,new Comparator<KOMENormalSegment>(){
            public int compare(KOMENormalSegment a,KOMENormalSegment b){return a.getId().compareTo(b.getId());}
        });
        if(candidates.isEmpty())return Result.failure(Status.NO_ENDPOINT_CANDIDATES);
        if(candidates.size()>2)return Result.failure(Status.TOO_MANY_NORMAL_ENDPOINTS);
        if(candidates.size()==2)return Result.success(KOMESiegeAreaRef.normal(candidates.get(0).getId()),
            KOMESiegeAreaRef.normal(candidates.get(1).getId()));
        KOMENormalSegment only=candidates.get(0);
        if(KOMEGeometryPredicates.prismContainsPrism(only.getPrism(),transition.getPrism()))
            return Result.failure(Status.EXTERIOR_NOT_ESTABLISHED);
        return Result.success(KOMESiegeAreaRef.exterior(),KOMESiegeAreaRef.normal(only.getId()));
    }

    /** Only reliable closed bounds can establish separation; the transition is already validated. */
    private static boolean mayAffectTransition(KOMEPolygonPrism normal,KOMEPolygonPrism transition){
        if(!normal.getPolygon().hasBounds())return true;
        if(normal.getMaxX()<transition.getMinX()||normal.getMinX()>transition.getMaxX()
                ||normal.getMaxZ()<transition.getMinZ()||normal.getMinZ()>transition.getMaxZ())return false;
        // Malformed heights cannot prove Y separation; equality remains boundary contact.
        return !normal.hasValidYRange()||normal.getMaxYExclusive()>=transition.getMinYInclusive()
            &&normal.getMinYInclusive()<=transition.getMaxYExclusive();
    }

    public static final class Result {
        private final Status status;
        private final KOMESiegeAreaRef endpointA,endpointB;
        private Result(Status status,KOMESiegeAreaRef a,KOMESiegeAreaRef b){this.status=status;endpointA=a;endpointB=b;}
        static Result success(KOMESiegeAreaRef a,KOMESiegeAreaRef b){
            return a.compareTo(b)<=0?new Result(Status.SUCCESS,a,b):new Result(Status.SUCCESS,b,a);
        }
        static Result failure(Status status){return new Result(status,null,null);}
        public boolean isSuccessful(){return status==Status.SUCCESS;}
        public Status getStatus(){return status;}
        public KOMESiegeAreaRef getEndpointA(){return endpointA;}
        public KOMESiegeAreaRef getEndpointB(){return endpointB;}
    }
}
