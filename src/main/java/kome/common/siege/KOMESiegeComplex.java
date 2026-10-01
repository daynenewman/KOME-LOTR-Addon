package kome.common.siege;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable tactical geometry definition with a stable, caller-supplied complex identity.
 * The tile ID identifies its associated location; multiple complexes may share that tile.
 * Zone IDs, Connection IDs, references and Exterior are local to this containing complex.
 */
public final class KOMESiegeComplex {
    private final String complexId;
    private final String tileId;
    private final int dimensionId;
    private final long revision;
    private final List<KOMENormalSegment> normalSegments;
    private final List<KOMEWallZone> wallZones;
    private final List<KOMETransitionZone> transitionZones;
    private final List<KOMEExteriorDeploymentArea> exteriorDeploymentAreas;
    private final List<KOMESiegeConnection> connections;

    public KOMESiegeComplex(String complexId,String tileId,int dimensionId,long revision,
            Collection<KOMENormalSegment> normals,Collection<KOMEWallZone> walls,
            Collection<KOMETransitionZone> transitions,Collection<KOMEExteriorDeploymentArea> exteriorAreas,
            Collection<KOMESiegeConnection> connections){
        this.complexId=KOMESiegeIds.complex(complexId);
        this.tileId=KOMESiegeIds.tile(tileId);this.dimensionId=dimensionId;this.revision=revision;
        normalSegments=copy(normals);wallZones=copy(walls);transitionZones=copy(transitions);
        exteriorDeploymentAreas=copy(exteriorAreas);this.connections=copy(connections);
    }
    private static <T> List<T> copy(Collection<T> source){
        if(source==null)return Collections.emptyList();List<T> result=new ArrayList<T>(source);
        if(result.contains(null))throw new IllegalArgumentException("Siege Complex collections cannot contain null.");
        return Collections.unmodifiableList(result);
    }
    /** Opaque identity retained across definition revisions, independent of tile and dimension. */
    public String getComplexId(){return complexId;}
    public String getTileId(){return tileId;} public int getDimensionId(){return dimensionId;}
    public long getRevision(){return revision;}
    public List<KOMENormalSegment> getNormalSegments(){return normalSegments;}
    public List<KOMEWallZone> getWallZones(){return wallZones;}
    public List<KOMETransitionZone> getTransitionZones(){return transitionZones;}
    public List<KOMEExteriorDeploymentArea> getExteriorDeploymentAreas(){return exteriorDeploymentAreas;}
    public List<KOMESiegeConnection> getConnections(){return connections;}

    public KOMENormalSegment findNormalSegment(String id){
        String key=KOMESiegeIds.id(id);for(KOMENormalSegment item:normalSegments)if(item.getId().equals(key))return item;return null;
    }
    public KOMEWallZone findWallZone(String id){
        String key=KOMESiegeIds.id(id);for(KOMEWallZone item:wallZones)if(item.getId().equals(key))return item;return null;
    }
    public KOMETransitionZone findTransitionZone(String id){
        String key=KOMESiegeIds.id(id);for(KOMETransitionZone item:transitionZones)if(item.getId().equals(key))return item;return null;
    }
    public KOMEExteriorDeploymentArea findExteriorDeploymentArea(String id){
        String key=KOMESiegeIds.id(id);for(KOMEExteriorDeploymentArea item:exteriorDeploymentAreas)if(item.getId().equals(key))return item;return null;
    }
    /** Complex-local Stronghold footprint is Normal Segments plus Wall Zones only. */
    public Set<String> getStrongholdZoneIds(){
        TreeSet<String> ids=new TreeSet<String>();for(KOMENormalSegment item:normalSegments)ids.add(item.getId());
        for(KOMEWallZone item:wallZones)ids.add(item.getId());return Collections.unmodifiableSet(ids);
    }
    /** This complex's explicit Connection graph only; Wall accessibility never contributes adjacency. */
    public Set<String> getAdjacentNormalSegmentIds(String normalId){
        String key=KOMESiegeIds.id(normalId);TreeSet<String> result=new TreeSet<String>();
        for(KOMESiegeConnection c:connections){
            KOMESiegeAreaRef a=c.getEndpointA(),b=c.getEndpointB();
            if(a.isNormal()&&b.isNormal()){
                if(a.getNormalSegmentId().equals(key))result.add(b.getNormalSegmentId());
                else if(b.getNormalSegmentId().equals(key))result.add(a.getNormalSegmentId());
            }
        }
        return Collections.unmodifiableSet(result);
    }
    /** Resolves a local endpoint, including Exterior, only within this complex. */
    public List<KOMESiegeConnection> getConnectionsFor(KOMESiegeAreaRef endpoint){
        List<KOMESiegeConnection> result=new ArrayList<KOMESiegeConnection>();
        for(KOMESiegeConnection connection:connections)if(connection.connects(endpoint))result.add(connection);
        return Collections.unmodifiableList(result);
    }
}
