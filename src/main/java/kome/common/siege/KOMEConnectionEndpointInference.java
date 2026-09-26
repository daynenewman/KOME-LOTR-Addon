package kome.common.siege;

import kome.common.siege.geometry.KOMEGeometryPredicates;
import kome.common.siege.geometry.KOMEPolygonValidator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TreeMap;

/** Pure editor helper. Successful endpoints must still be persisted explicitly. */
public final class KOMEConnectionEndpointInference {
    public enum Status {
        SUCCESS,
        TRANSITION_NOT_FOUND,
        INVALID_TRANSITION_GEOMETRY,
        NO_ENDPOINT_CANDIDATES,
        EXTERIOR_NOT_ESTABLISHED,
        TOO_MANY_NORMAL_ENDPOINTS,
        AMBIGUOUS_CORRIDOR
    }
    private KOMEConnectionEndpointInference() {}

    public static Result infer(KOMESiegeComplex complex,String transitionZoneId){
        KOMETransitionZone transition=complex==null?null:complex.findTransitionZone(transitionZoneId);
        if(transition==null)return Result.failure(Status.TRANSITION_NOT_FOUND);
        if(!transition.getPrism().hasValidYRange()
                ||!KOMEPolygonValidator.validate(transition.getPrism().getPolygon(),transition.getId()).isValid())
            return Result.failure(Status.INVALID_TRANSITION_GEOMETRY);
        for(KOMETransitionZone other:complex.getTransitionZones())
            if(other!=transition&&KOMEGeometryPredicates.prismsHaveInteriorOverlap(transition.getPrism(),other.getPrism()))
                return Result.failure(Status.AMBIGUOUS_CORRIDOR);
        for(KOMEWallZone wall:complex.getWallZones())
            if(KOMEGeometryPredicates.prismsHaveInteriorOverlap(transition.getPrism(),wall.getPrism()))
                return Result.failure(Status.AMBIGUOUS_CORRIDOR);

        TreeMap<String,KOMENormalSegment> unique=new TreeMap<String,KOMENormalSegment>();
        for(KOMENormalSegment normal:complex.getNormalSegments())
            if(KOMEGeometryPredicates.prismsTouchOrOverlap(transition.getPrism(),normal.getPrism()))
                unique.put(normal.getId(),normal);
        List<KOMENormalSegment> candidates=new ArrayList<KOMENormalSegment>(unique.values());
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
