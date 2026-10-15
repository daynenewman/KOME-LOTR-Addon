package kome.common.data;

import java.util.ArrayList;
import java.util.List;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.Pose;
import static kome.common.data.KOMEJoinBattleEgressService.Failure;

/** Read-only canonical arrival candidates. No nearest-tile inference or legacy exceptions. */
final class KOMEJoinBattleReturnAuthority {
    static final class Anchors {
        final List<Pose> poses=new ArrayList<Pose>();
        final List<String> authorities=new ArrayList<String>();
        boolean rejected;
        Failure unavailableFailure(){return rejected?Failure.SOURCE_TILE_FALLBACK_OUTSIDE_TILE
            :Failure.SOURCE_TILE_FALLBACK_UNAVAILABLE;}
    }

    static Anchors resolve(KOMEWorldData data,String tile,Pose orientation,
            KOMETileWorldResolver.ReadView tiles) {
        Anchors result=new Anchors();
        KOMETileWaypoint rally=data==null?null:data.getTileWaypoint(tile,KOMETileWaypoint.RALLY);
        if(rally!=null)add(result,tiles,tile,orientation,rally.dimensionId,rally.x,rally.y,rally.z,"saved RALLY");
        KOMETileGameplayDefaults.Point point=KOMETileGameplayDefaults.get().getArrivalDefault(tile);
        if(point!=null)add(result,tiles,tile,orientation,point.dimensionId(),point.x,point.y,point.z,"packaged arrival");
        return result;
    }

    private static void add(Anchors result,KOMETileWorldResolver.ReadView tiles,String tile,
            Pose orientation,int dimension,double x,double y,double z,String authority) {
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)){
            result.rejected=true;return;
        }
        KOMETileResolution resolved=tiles.resolveWorldPosition(dimension,x,z);
        if(dimension!=orientation.dimensionId||resolved.status!=KOMETileResolution.Status.RESOLVED
                ||!tile.equals(resolved.tileId)){
            result.rejected=true;return;
        }
        for(Pose pose:result.poses)if(pose.x==x&&pose.y==y&&pose.z==z)return;
        result.poses.add(new Pose(dimension,x,y,z,orientation.yaw,orientation.pitch));
        result.authorities.add(authority);
    }
}
