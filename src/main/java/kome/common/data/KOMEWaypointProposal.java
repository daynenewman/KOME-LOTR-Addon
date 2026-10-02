package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** Persisted review workflow; a proposal is never a travel destination. */
public final class KOMEWaypointProposal {
    public enum Status { PENDING, APPROVED, REJECTED }
    public final UUID id, submitter, approvedWaypoint;
    public final String submitterName, tileId, name, reviewer, reason;
    public final int dimension, x, y, z, level;
    public final long submittedAt, reviewedAt, version;
    public final Status status;

    KOMEWaypointProposal(UUID id, UUID submitter, String submitterName, String tileId, String name,
            int dimension, int x, int y, int z, int level, long submittedAt, Status status,
            String reviewer, String reason, long reviewedAt, long version, UUID approvedWaypoint) {
        if (id == null || submitter == null || status == null || submittedAt < 0 || version < 0
                || y < 0 || y > 255 || level < 0 || reviewedAt < submittedAt)
            throw new IllegalArgumentException("Invalid proposal identity, coordinates or counters");
        this.id=id; this.submitter=submitter;
        this.submitterName=KOMEPublicWaypoint.text(submitterName,64,false);
        this.tileId=KOMEPublicWaypoint.canonicalTile(tileId); this.name=KOMEPublicWaypoint.validName(name);
        this.dimension=dimension; this.x=x; this.y=y; this.z=z; this.level=level;
        this.submittedAt=submittedAt; this.status=status; this.version=version;
        this.reason=KOMEPublicWaypoint.text(reason,256,true);
        if (!reviewer.isEmpty()) KOMEPublicWaypoint.actor(reviewer);
        if (status != Status.PENDING && reviewer.isEmpty()
                || status == Status.APPROVED != (approvedWaypoint != null))
            throw new IllegalArgumentException("Invalid proposal review state");
        this.reviewer=reviewer; this.reviewedAt=reviewedAt; this.approvedWaypoint=approvedWaypoint;
    }
    KOMEWaypointProposal reviewed(String tile, String display, int dim, int wx, int wy, int wz,
            int newLevel, Status state, String actor, String explanation, long now, UUID approved) {
        if (version == Long.MAX_VALUE || now < reviewedAt) throw new IllegalArgumentException("Stale review time/version");
        return new KOMEWaypointProposal(id,submitter,submitterName,tile,display,dim,wx,wy,wz,newLevel,
            submittedAt,state,actor,explanation,now,version+1,approved);
    }
    public NBTTagCompound writeToNBT() {
        NBTTagCompound n=new NBTTagCompound();
        n.setString("Id",id.toString()); n.setString("Submitter",submitter.toString());
        n.setString("SubmitterName",submitterName); n.setString("Tile",tileId); n.setString("Name",name);
        n.setInteger("Dimension",dimension); n.setInteger("X",x); n.setInteger("Y",y); n.setInteger("Z",z);
        n.setInteger("Level",level); n.setLong("SubmittedAt",submittedAt); n.setString("Status",status.name());
        n.setString("Reviewer",reviewer); n.setString("Reason",reason); n.setLong("ReviewedAt",reviewedAt);
        n.setLong("Version",version); n.setString("Approved",approvedWaypoint==null?"":approvedWaypoint.toString());
        return n;
    }
    static KOMEWaypointProposal read(NBTTagCompound n) {
        for(String k:new String[]{"Id","Submitter","SubmitterName","Tile","Name","Status","Reviewer","Reason","Approved"})
            KOMEPublicWaypoint.require(n,k,8);
        for(String k:new String[]{"Dimension","X","Y","Z","Level"}) KOMEPublicWaypoint.require(n,k,3);
        for(String k:new String[]{"SubmittedAt","ReviewedAt","Version"}) KOMEPublicWaypoint.require(n,k,4);
        String approved=n.getString("Approved");
        return new KOMEWaypointProposal(KOMEPublicWaypoint.uuid(n.getString("Id")),KOMEPublicWaypoint.uuid(n.getString("Submitter")),
            n.getString("SubmitterName"),n.getString("Tile"),n.getString("Name"),n.getInteger("Dimension"),
            n.getInteger("X"),n.getInteger("Y"),n.getInteger("Z"),n.getInteger("Level"),n.getLong("SubmittedAt"),
            Status.valueOf(n.getString("Status")),n.getString("Reviewer"),n.getString("Reason"),n.getLong("ReviewedAt"),
            n.getLong("Version"),approved.isEmpty()?null:KOMEPublicWaypoint.uuid(approved));
    }
}
