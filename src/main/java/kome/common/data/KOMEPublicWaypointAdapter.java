package kome.common.data;
import java.util.UUID;
import lotr.common.world.map.*;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/** Transient native-compatible marker/travel target; never inserted into native saved lists. */
public final class KOMEPublicWaypointAdapter extends LOTRCustomWaypoint {
    public static final UUID NAMESPACE=UUID.fromString("9e760ea7-6bde-4b7f-b8f1-1d7875837921");
    public final KOMEPublicWaypointRegistry.View view;
    public KOMEPublicWaypointAdapter(KOMEPublicWaypointRegistry.View view) {
        super(view.record.name,LOTRWaypoint.worldToMapX(view.record.x),LOTRWaypoint.worldToMapZ(view.record.z),
            view.record.x,view.record.y,view.record.z,view.record.wireId);
        this.view=view;
    }
    @Override public int getID(){ return view.record.wireId; }
    @Override public UUID getSharingPlayerID(){ return NAMESPACE; }
    @Override public String getSharingPlayerName(){ return "KOME public waypoints"; }
    @Override public boolean isShared(){ return true; }
    @Override public boolean isSharedHidden(){ return false; }
    @Override public boolean isSharedUnlocked(){ return true; }
    @Override public boolean canUnlockShared(EntityPlayer p){ return false; }
    @Override public void setSharedHidden(boolean hidden){ }
    @Override public void rename(String name){ }
    @Override public int getXCoord(){ return view.record.x; }
    @Override public int getZCoord(){ return view.record.z; }
    @Override public int getYCoordSaved(){ return view.record.y; }
    @Override public double getX(){ return LOTRWaypoint.worldToMapX(view.record.x); }
    @Override public double getY(){ return LOTRWaypoint.worldToMapZ(view.record.z); }
    @Override public String getCodeName(){ return "kome:"+view.record.id; }
    @Override public String getDisplayName(){ return view.record.name; }
    @Override public int getYCoord(World world,int x,int z) {
        if(view.record.source==KOMEPublicWaypoint.Source.NATIVE)
            return LOTRWaypoint.waypointForName(view.record.sourceKey).getYCoord(world,x,z);
        return super.getYCoord(world,x,z); // Existing native safe-height search only mutates the disposable adapter.
    }
    public boolean nativeEligible(EntityPlayer player,String faction) {
        if(player==null) return false;
        if(view.record.source!=KOMEPublicWaypoint.Source.NATIVE) return true;
        LOTRWaypoint point=LOTRWaypoint.waypointForName(view.record.sourceKey);
        return point!=null && (KOMEAlliance.normalizeFactionKey(faction).isEmpty()
            ?point.hasPlayerUnlocked(player):KOMEWaypointAccessService.hasNativeProgression(player,point));
    }
    @Override public boolean hasPlayerUnlocked(EntityPlayer player) {
        return KOMEWaypointAccessService.evaluatePlayer(player,this,true).finalAllowed;
    }
    @Override public String getLoreText(EntityPlayer player) {
        KOMEPublicWaypointRegistry.View current=KOMEPublicWaypointBridge.current(player,view.record.id);
        if(current==null) return "Public destination is no longer available.";
        return "KOME public waypoint | Tile "+current.record.tileId+" | Level "+current.record.level
            +"\nDefault owner: "+display(current.defaultOwner)+" | Current owner: "+display(current.currentOwner)
            +"\n"+KOMEWaypointAccessService.evaluatePlayer(player,this,true).reason;
    }
    private static String display(String owner){ return owner.isEmpty()?"Unclaimed":KOMEAlliance.displayFactionName(owner); }
}
