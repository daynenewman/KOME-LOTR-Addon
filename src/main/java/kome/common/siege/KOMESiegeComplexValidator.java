package kome.common.siege;

import kome.common.siege.geometry.KOMEGeometryPredicates;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEPolygonValidator;
import kome.common.siege.validation.KOMEValidationCode;
import kome.common.siege.validation.KOMEValidationIssue;
import kome.common.siege.validation.KOMEValidationResult;
import kome.common.siege.validation.KOMEValidationSeverity;
import kome.common.tactical.KOMEForceDeploymentArea;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Pure, deterministic validation of authored Siege Complex geometry and topology. */
public final class KOMESiegeComplexValidator {
    public KOMEValidationResult validate(KOMESiegeComplex complex){
        if(complex==null)throw new IllegalArgumentException("A Siege Complex is required.");
        List<KOMEValidationIssue> issues=new ArrayList<KOMEValidationIssue>();
        if(complex.getRevision()<0)add(issues,KOMEValidationCode.INVALID_COMPLEX_REVISION,"Revision cannot be negative.",complex.getComplexId());
        Map<String,KOMENormalSegment> normals=new TreeMap<String,KOMENormalSegment>();
        Map<String,KOMEWallZone> walls=new TreeMap<String,KOMEWallZone>();
        Map<String,KOMETransitionZone> transitions=new TreeMap<String,KOMETransitionZone>();
        Map<String,String> allZoneTypes=new TreeMap<String,String>();
        indexZones(complex,issues,normals,walls,transitions,allZoneTypes);
        validateZoneGeometry(complex,issues);
        validateWallReferences(complex,issues,normals);
        validateStrongholdOverlaps(complex,issues);
        validateTransitionOverlaps(complex,issues);
        validateConnections(complex,issues,normals,transitions,allZoneTypes);
        return new KOMEValidationResult(issues);
    }

    /** Validates only the preferred reference against a caller-resolved tile area; absence is valid. */
    public KOMEValidationResult validatePreferredForceDeploymentArea(KOMESiegeComplex complex,
            KOMEForceDeploymentArea resolvedArea){
        if(complex==null)throw new IllegalArgumentException("A Siege Complex is required.");
        List<KOMEValidationIssue> issues=new ArrayList<KOMEValidationIssue>();
        if(!complex.getPreferredForceDeploymentAreaId().isPresent())return new KOMEValidationResult(issues);
        String preferredId=complex.getPreferredForceDeploymentAreaId().get();
        if(resolvedArea==null||!preferredId.equals(resolvedArea.getAreaId())){
            add(issues,KOMEValidationCode.PREFERRED_DEPLOYMENT_AREA_UNKNOWN,
                "Explicit preferred Force Deployment Area does not resolve.",complex.getComplexId(),preferredId);
            return new KOMEValidationResult(issues);
        }
        if(!complex.getTileId().equals(resolvedArea.getTileId()))
            add(issues,KOMEValidationCode.PREFERRED_DEPLOYMENT_AREA_WRONG_TILE,
                "Preferred Force Deployment Area must belong to the complex's conquest tile.",complex.getComplexId(),preferredId);
        if(complex.getDimensionId()!=resolvedArea.getDimensionId())
            add(issues,KOMEValidationCode.PREFERRED_DEPLOYMENT_AREA_WRONG_DIMENSION,
                "Preferred Force Deployment Area dimension must match the complex.",complex.getComplexId(),preferredId);
        return new KOMEValidationResult(issues);
    }

    private static void indexZones(KOMESiegeComplex complex,List<KOMEValidationIssue> issues,
            Map<String,KOMENormalSegment> normals,Map<String,KOMEWallZone> walls,
            Map<String,KOMETransitionZone> transitions,
            Map<String,String> allTypes){
        for(KOMENormalSegment zone:complex.getNormalSegments())index(zone,"NORMAL",normals,allTypes,issues);
        for(KOMEWallZone zone:complex.getWallZones())index(zone,"WALL",walls,allTypes,issues);
        for(KOMETransitionZone zone:complex.getTransitionZones())index(zone,"TRANSITION",transitions,allTypes,issues);
    }
    private static <T extends KOMESiegeZone> void index(T zone,String type,Map<String,T> sameType,
            Map<String,String> allTypes,List<KOMEValidationIssue> issues){
        String id=zone.getId();
        if(id.length()==0)add(issues,KOMEValidationCode.BLANK_ZONE_ID,"Zone IDs cannot be blank.",type);
        String prior=allTypes.put(id,type);
        if(prior!=null)add(issues,KOMEValidationCode.DUPLICATE_ZONE_ID,"Zone IDs must be unique across all zone types.",id,prior,type);
        if(!sameType.containsKey(id))sameType.put(id,zone);
    }
    private static void validateZoneGeometry(KOMESiegeComplex complex,List<KOMEValidationIssue> issues){
        for(KOMESiegeZone zone:allZones(complex)){
            issues.addAll(KOMEPolygonValidator.validate(zone.getPrism().getPolygon(),zone.getId()).getIssues());
            if(!zone.getPrism().hasValidYRange())add(issues,KOMEValidationCode.PRISM_MALFORMED_Y,
                "Prism minimum Y must be below maximum-exclusive Y.",zone.getId());
        }
    }
    private static List<KOMESiegeZone> allZones(KOMESiegeComplex complex){
        List<KOMESiegeZone> zones=new ArrayList<KOMESiegeZone>();zones.addAll(complex.getNormalSegments());
        zones.addAll(complex.getWallZones());zones.addAll(complex.getTransitionZones());return zones;
    }
    private static void validateWallReferences(KOMESiegeComplex complex,List<KOMEValidationIssue> issues,
            Map<String,KOMENormalSegment> normals){
        for(KOMEWallZone wall:complex.getWallZones()){
            if(wall.getAccessibleFromNormalSegmentIds().isEmpty())add(issues,KOMEValidationCode.WALL_ACCESS_EMPTY,
                "A Wall Zone requires at least one legitimate Normal Segment access source.",wall.getId());
            for(String id:wall.getAccessibleFromNormalSegmentIds())if(!normals.containsKey(id))
                add(issues,KOMEValidationCode.WALL_ACCESS_UNKNOWN_NORMAL,"Wall access must reference an existing Normal Segment.",wall.getId(),id);
        }
    }
    private static void validateStrongholdOverlaps(KOMESiegeComplex complex,List<KOMEValidationIssue> issues){
        List<KOMENormalSegment> normals=complex.getNormalSegments();List<KOMEWallZone> walls=complex.getWallZones();
        for(int i=0;i<normals.size();i++)for(int j=i+1;j<normals.size();j++)
            overlap(issues,KOMEValidationCode.NORMAL_NORMAL_OVERLAP,normals.get(i),normals.get(j));
        for(KOMENormalSegment normal:normals)for(KOMEWallZone wall:walls)
            overlap(issues,KOMEValidationCode.NORMAL_WALL_OVERLAP,normal,wall);
        for(int i=0;i<walls.size();i++)for(int j=i+1;j<walls.size();j++)
            overlap(issues,KOMEValidationCode.WALL_WALL_OVERLAP,walls.get(i),walls.get(j));
    }
    private static void validateTransitionOverlaps(KOMESiegeComplex complex,List<KOMEValidationIssue> issues){
        List<KOMETransitionZone> transitions=complex.getTransitionZones();
        for(int i=0;i<transitions.size();i++)for(int j=i+1;j<transitions.size();j++)
            overlap(issues,KOMEValidationCode.TRANSITION_TRANSITION_OVERLAP,transitions.get(i),transitions.get(j));
        for(KOMETransitionZone transition:transitions)for(KOMEWallZone wall:complex.getWallZones())
            overlap(issues,KOMEValidationCode.TRANSITION_WALL_OVERLAP,transition,wall);
    }
    private static void overlap(List<KOMEValidationIssue> issues,KOMEValidationCode code,KOMESiegeZone a,KOMESiegeZone b){
        if(validGeometry(a.getPrism())&&validGeometry(b.getPrism())
                &&KOMEGeometryPredicates.prismsHaveInteriorOverlap(a.getPrism(),b.getPrism()))
            add(issues,code,"Tactical zone interiors may not occupy the same 3D volume.",a.getId(),b.getId());
    }
    private static boolean validGeometry(KOMEPolygonPrism prism){
        return prism.hasValidYRange()&&KOMEPolygonValidator.validate(prism.getPolygon(),"").isValid();
    }

    private static void validateConnections(KOMESiegeComplex complex,List<KOMEValidationIssue> issues,
            Map<String,KOMENormalSegment> normals,Map<String,KOMETransitionZone> transitions,
            Map<String,String> allZoneTypes){
        Set<String> connectionIds=new HashSet<String>();Map<String,Integer> transitionUse=new HashMap<String,Integer>();
        for(KOMESiegeConnection connection:complex.getConnections()){
            if(connection.getId().length()==0)add(issues,KOMEValidationCode.BLANK_CONNECTION_ID,"Connection IDs cannot be blank.");
            if(!connectionIds.add(connection.getId()))add(issues,KOMEValidationCode.DUPLICATE_CONNECTION_ID,
                "Connection IDs must be unique.",connection.getId());
            Integer uses=transitionUse.get(connection.getTransitionZoneId());
            transitionUse.put(connection.getTransitionZoneId(),uses==null?1:uses+1);
            validateConnection(complex,connection,issues,normals,transitions,allZoneTypes);
        }
        for(Map.Entry<String,Integer> entry:transitionUse.entrySet())if(entry.getValue()>1)
            add(issues,KOMEValidationCode.CONNECTION_TRANSITION_REUSED,"A Transition Zone may belong to only one Connection.",entry.getKey());
        for(KOMETransitionZone transition:complex.getTransitionZones())if(!transitionUse.containsKey(transition.getId()))
            add(issues,KOMEValidationCode.TRANSITION_UNUSED,"Every Transition Zone must be assigned to one Connection.",transition.getId());
        validateDuplicateCorridors(complex,issues,transitions);
    }

    private static void validateConnection(KOMESiegeComplex complex,KOMESiegeConnection connection,
            List<KOMEValidationIssue> issues,Map<String,KOMENormalSegment> normals,
            Map<String,KOMETransitionZone> transitions,Map<String,String> allTypes){
        KOMESiegeAreaRef a=connection.getEndpointA(),b=connection.getEndpointB();
        if(a.equals(b))add(issues,KOMEValidationCode.CONNECTION_SAME_ENDPOINT,"A Connection cannot link an area to itself.",connection.getId());
        if(a.isExterior()&&b.isExterior())add(issues,KOMEValidationCode.CONNECTION_EXTERIOR_EXTERIOR,
            "Exterior-to-Exterior Connections are not tactical edges.",connection.getId());
        boolean endpointsValid=validateEndpoint(connection,a,issues,normals,allTypes)
            &validateEndpoint(connection,b,issues,normals,allTypes);
        KOMETransitionZone transition=transitions.get(connection.getTransitionZoneId());
        if(transition==null)add(issues,KOMEValidationCode.CONNECTION_TRANSITION_UNKNOWN,
            "Connection Transition Zone does not exist.",connection.getId(),connection.getTransitionZoneId());
        if(connection.isGated()){
            KOMEDefensiveGateRef gate=connection.getGateRef().get();
            if(gate.getBuildId().length()==0)add(issues,KOMEValidationCode.GATE_REF_BLANK_BUILD_ID,
                "Logical GateRef requires a DEFENSIVE Build ID.",connection.getId());
            if(gate.getGateRecordId().length()==0)add(issues,KOMEValidationCode.GATE_REF_BLANK_RECORD_ID,
                "Logical GateRef requires a per-Build gate record ID.",connection.getId());
        }
        if(transition==null||!endpointsValid||!validGeometry(transition.getPrism()))return;
        Set<String> declaredNormals=new HashSet<String>();boolean exterior=a.isExterior()||b.isExterior();
        if(a.isNormal())declaredNormals.add(a.getNormalSegmentId());if(b.isNormal())declaredNormals.add(b.getNormalSegmentId());
        for(String id:declaredNormals){KOMENormalSegment normal=normals.get(id);
            if(!KOMEGeometryPredicates.prismsTouchOrOverlap(transition.getPrism(),normal.getPrism()))
                add(issues,KOMEValidationCode.CONNECTION_TRANSITION_MISSES_ENDPOINT,
                    "Transition Zone must touch or intersect each declared endpoint in 3D.",connection.getId(),transition.getId(),id);}
        for(KOMENormalSegment normal:complex.getNormalSegments())
            if(!declaredNormals.contains(normal.getId())&&KOMEGeometryPredicates.prismsTouchOrOverlap(transition.getPrism(),normal.getPrism()))
                add(issues,KOMEValidationCode.CONNECTION_TRANSITION_THIRD_NORMAL,
                    "Transition Zone also intersects an unrelated Normal Segment.",connection.getId(),transition.getId(),normal.getId());
        if(exterior&&declaredNormals.size()==1){
            KOMENormalSegment normal=normals.get(declaredNormals.iterator().next());
            if(KOMEGeometryPredicates.prismContainsPrism(normal.getPrism(),transition.getPrism()))
                add(issues,KOMEValidationCode.CONNECTION_EXTERIOR_NOT_ESTABLISHED,
                    "Transition Zone is wholly contained by its Normal endpoint and does not establish Exterior.",connection.getId(),transition.getId());
        }
    }

    private static boolean validateEndpoint(KOMESiegeConnection connection,KOMESiegeAreaRef endpoint,
            List<KOMEValidationIssue> issues,Map<String,KOMENormalSegment> normals,Map<String,String> allTypes){
        if(endpoint.isExterior())return true;String id=endpoint.getNormalSegmentId();
        if(normals.containsKey(id))return true;
        if(allTypes.containsKey(id))add(issues,KOMEValidationCode.CONNECTION_ENDPOINT_NOT_NORMAL,
            "Only Normal Segments and Exterior may be Connection endpoints.",connection.getId(),id,allTypes.get(id));
        else add(issues,KOMEValidationCode.CONNECTION_ENDPOINT_UNKNOWN,"Connection endpoint does not exist.",connection.getId(),id);
        return false;
    }

    private static void validateDuplicateCorridors(KOMESiegeComplex complex,List<KOMEValidationIssue> issues,
            Map<String,KOMETransitionZone> transitions){
        List<KOMESiegeConnection> connections=complex.getConnections();
        for(int i=0;i<connections.size();i++)for(int j=i+1;j<connections.size();j++){
            KOMESiegeConnection a=connections.get(i),b=connections.get(j);
            if(!a.getEndpointA().equals(b.getEndpointA())||!a.getEndpointB().equals(b.getEndpointB()))continue;
            KOMETransitionZone first=transitions.get(a.getTransitionZoneId()),second=transitions.get(b.getTransitionZoneId());
            if(a.getTransitionZoneId().equals(b.getTransitionZoneId())||(first!=null&&second!=null&&first.getPrism().equals(second.getPrism())))
                add(issues,KOMEValidationCode.CONNECTION_DUPLICATE_CORRIDOR,
                    "Connections with the same endpoints must use distinct unambiguous corridors.",a.getId(),b.getId());
        }
    }

    private static void add(List<KOMEValidationIssue> issues,KOMEValidationCode code,String message,String... subjects){
        issues.add(new KOMEValidationIssue(KOMEValidationSeverity.ERROR,code,message,subjects));
    }
}
