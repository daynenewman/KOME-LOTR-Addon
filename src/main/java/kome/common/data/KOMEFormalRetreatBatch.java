package kome.common.data;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/** Immutable exact-group recovery authority, owned by ConflictMovementRelease schema 2. */
public final class KOMEFormalRetreatBatch {
    public enum Progress { PREPARED, RELEASED, DEPARTED, COMPLETE }
    public enum Phase { STRATEGIC_PENDING, STRATEGIC_COMPLETE, ABANDONMENT_RECONCILED,
        EGRESS_HANDED_OFF, FINALIZED }
    public static final class Member {
        public final String companyId, orderId;
        public final UUID owner;
        public final List<String> route;
        public final int allowanceBefore, allowanceAfter;
        public final Progress progress;
        public Member(String company,String order,UUID owner,List<String> route,int before,
                int after,Progress progress){
            companyId=KOMEConflictContracts.companyId(company);
            if(order==null||!order.matches("M[1-9][0-9]*")||owner==null||progress==null
                    ||before<0||before>2||after!=Math.max(0,before-1)
                    ||route==null||route.size()<2||route.size()>4096)
                throw new IllegalArgumentException("Invalid retreat batch member");
            orderId=order;this.owner=owner;this.allowanceBefore=before;
            this.allowanceAfter=after;this.progress=progress;
            List<String> canonical=new ArrayList<String>();
            for(String tile:route){
                String id=KOMEConflictContracts.tile(tile);
                if(!id.equals(tile)||!canonical.isEmpty()&&canonical.get(canonical.size()-1).equals(id))
                    throw new IllegalArgumentException("Invalid retreat batch route");
                canonical.add(id);
            }
            this.route=Collections.unmodifiableList(canonical);
        }
        Member advance(Progress next){
            if(next.ordinal()<progress.ordinal())throw new IllegalArgumentException("Retreat member cannot regress");
            return new Member(companyId,orderId,owner,route,allowanceBefore,allowanceAfter,next);
        }
    }
    public final UUID id, commander;
    public final String conflictId, tileId, receiptId, leaderOrderId;
    public final long acceptedAtMillis;
    public final List<Member> members;
    public final Phase phase;
    public final boolean abandoned;

    public KOMEFormalRetreatBatch(UUID id,UUID commander,String conflict,String tile,
            String receipt,long at,List<Member> members,Phase phase,boolean abandoned){
        if(id==null||commander==null||at<0||members==null||members.isEmpty()
                ||members.size()>256||phase==null)throw new IllegalArgumentException("Invalid retreat batch");
        this.id=id;this.commander=commander;
        conflictId=KOMEConflictIdAllocator.requireIdentity(conflict);
        tileId=KOMEConflictContracts.tile(tile);
        receiptId=receipt==null||receipt.isEmpty()?"":KOMEJoinBattleReceiptIdAllocator.requireIdentity(receipt);
        acceptedAtMillis=at;this.phase=phase;this.abandoned=abandoned;
        Set<String> companies=new HashSet<String>(),orders=new HashSet<String>();
        String previous="";
        for(Member m:members){
            if(m==null||!companies.add(m.companyId)||!orders.add(m.orderId)
                    ||m.companyId.compareTo(previous)<=0||!tileId.equals(m.route.get(0))
                    ||phase!=Phase.STRATEGIC_PENDING&&m.progress!=Progress.COMPLETE)
                throw new IllegalArgumentException("Invalid/duplicate retreat batch membership");
            previous=m.companyId;
        }
        if(abandoned&&phase.ordinal()<Phase.ABANDONMENT_RECONCILED.ordinal())
            throw new IllegalArgumentException("Retreat abandonment precedes reconciliation");
        this.members=Collections.unmodifiableList(new ArrayList<Member>(members));
        leaderOrderId=members.get(0).orderId;
    }
    public boolean finalized(){return phase==Phase.FINALIZED;}
    KOMEFormalRetreatBatch member(int index,Progress next){
        if(finalized())throw new IllegalArgumentException("Finalized retreat is immutable");
        List<Member> copy=new ArrayList<Member>(members);copy.set(index,copy.get(index).advance(next));
        return new KOMEFormalRetreatBatch(id,commander,conflictId,tileId,receiptId,acceptedAtMillis,copy,phase,abandoned);
    }
    KOMEFormalRetreatBatch advance(Phase next,boolean ended){
        if(next.ordinal()!=phase.ordinal()+1)throw new IllegalArgumentException("Retreat finalization cannot regress or skip");
        return new KOMEFormalRetreatBatch(id,commander,conflictId,tileId,receiptId,acceptedAtMillis,members,next,abandoned||ended);
    }
    NBTTagCompound write(){
        NBTTagCompound tag=new NBTTagCompound();
        tag.setString("BatchId",id.toString());tag.setString("Commander",commander.toString());
        tag.setString("ConflictId",conflictId);tag.setString("TileId",tileId);
        tag.setString("ReceiptId",receiptId);tag.setLong("AcceptedAtMillis",acceptedAtMillis);
        tag.setString("Phase",phase.name());tag.setBoolean("Abandoned",abandoned);
        NBTTagList list=new NBTTagList();
        for(Member m:members){
            NBTTagCompound row=new NBTTagCompound();row.setString("CompanyId",m.companyId);
            row.setString("OrderId",m.orderId);row.setString("Owner",m.owner.toString());
            row.setInteger("AllowanceBefore",m.allowanceBefore);row.setInteger("AllowanceAfter",m.allowanceAfter);
            row.setString("Progress",m.progress.name());NBTTagList route=new NBTTagList();
            for(String tile:m.route)route.appendTag(new NBTTagString(tile));
            row.setTag("Route",route);list.appendTag(row);
        }
        tag.setTag("Members",list);return tag;
    }
    static KOMEFormalRetreatBatch read(NBTTagCompound tag){
        require(tag,"AcceptedAtMillis",4);require(tag,"Abandoned",1);require(tag,"Members",9);
        NBTTagList list=tag.getTagList("Members",10);
        if(list.tagCount()==0||list.tagCount()>256)throw new IllegalArgumentException("Invalid retreat member list");
        List<Member> members=new ArrayList<Member>();
        for(int i=0;i<list.tagCount();i++){
            NBTTagCompound row=list.getCompoundTagAt(i);
            require(row,"AllowanceBefore",3);require(row,"AllowanceAfter",3);require(row,"Route",9);
            NBTTagList route=row.getTagList("Route",8);List<String> tiles=new ArrayList<String>();
            for(int j=0;j<route.tagCount();j++)tiles.add(route.getStringTagAt(j));
            members.add(new Member(text(row,"CompanyId"),text(row,"OrderId"),uuid(row,"Owner"),
                tiles,row.getInteger("AllowanceBefore"),row.getInteger("AllowanceAfter"),
                Progress.valueOf(text(row,"Progress"))));
        }
        return new KOMEFormalRetreatBatch(uuid(tag,"BatchId"),uuid(tag,"Commander"),
            text(tag,"ConflictId"),text(tag,"TileId"),text(tag,"ReceiptId"),tag.getLong("AcceptedAtMillis"),
            members,Phase.valueOf(text(tag,"Phase")),tag.getBoolean("Abandoned"));
    }
    private static void require(NBTTagCompound n,String key,int type){
        if(!n.hasKey(key,type))throw new IllegalArgumentException("Missing retreat batch "+key);
    }
    private static String text(NBTTagCompound n,String key){require(n,key,8);return n.getString(key);}
    private static UUID uuid(NBTTagCompound n,String key){
        String value=text(n,key);UUID id=UUID.fromString(value);
        if(!id.toString().equals(value))throw new IllegalArgumentException("Invalid retreat UUID");
        return id;
    }
    /** Reject duplicate authority; missing runtime references remain preserved, fail-closed obligations. */
    static void validateWorld(KOMEWorldData data){
        Set<UUID> ids=new HashSet<UUID>();Set<String> activeMembers=new HashSet<String>();
        Set<String> activeCompanies=new HashSet<String>();
        for(KOMEArmyMovementOrder holder:data.armyMovements.values()){
            KOMEFormalRetreatBatch batch=holder.formalRetreatBatch;
            if(batch==null)continue;
            if(!batch.leaderOrderId.equals(holder.id)||!ids.add(batch.id))
                throw new IllegalArgumentException("Duplicate or displaced retreat batch");
            if(!batch.finalized())for(Member member:batch.members)
                if(!activeMembers.add(member.orderId)||!activeCompanies.add(member.companyId))
                    throw new IllegalArgumentException("Overlapping unfinished retreat batches");
        }
    }
}
