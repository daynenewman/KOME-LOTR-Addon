package kome.common.data;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** One ordinary charge, distinct service history; never grants an achievement or rank. */
public final class KOMEKnightServiceRecord {
    private KOMEKnightCommission assignment;
    private final EnumSet<KOMEKnightCommission.Type> completed=EnumSet.noneOf(KOMEKnightCommission.Type.class);
    private final List<KOMEKnightCommission> history=new ArrayList<KOMEKnightCommission>();
    public synchronized KOMEKnightCommission assignment() { return assignment; }
    public synchronized Set<KOMEKnightCommission.Type> completedTypes() { return Collections.unmodifiableSet(EnumSet.copyOf(completed)); }
    /** Political credit requires a reported deed with known faction provenance. */
    public synchronized Set<KOMEKnightCommission.Type> qualifyingTypes(String faction) {
        EnumSet<KOMEKnightCommission.Type> result=EnumSet.noneOf(KOMEKnightCommission.Type.class);
        lotr.common.fac.LOTRFaction served=KOMEProgressionFactionResolver.resolve(faction);
        if(served!=null)for(KOMEKnightCommission a:history)
            if(a.stage==KOMEKnightCommission.Stage.REPORTED&&KOMEProgressionFactionResolver.matches(a.faction,served))result.add(a.type);
        return Collections.unmodifiableSet(result);
    }
    private boolean recorded(String token) { for(KOMEKnightCommission a:history)if(a.token.equals(token))return true;return false; }
    public synchronized List<KOMEKnightCommission> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }
    public synchronized boolean offer(KOMEKnightCommission a) { if(a==null||assignment!=null||recorded(a.token)||a.stage==KOMEKnightCommission.Stage.REPORTED)return false;assignment=a;return true; }
    public synchronized boolean accept(KOMEProgressionRank rank,KOMEProgressionNpcRef currentLiege,String pledge) {
        if(rank!=KOMEProgressionRank.KNIGHT||assignment==null||assignment.stage!=KOMEKnightCommission.Stage.OFFERED||currentLiege==null||!currentLiege.isSet()||!assignment.liege.hasSameIdentity(currentLiege)||!KOMEProgressionRelationshipLifecycle.sameFaction(assignment.faction,pledge)||!KOMEProgressionRelationshipLifecycle.sameFaction(currentLiege.factionKey,pledge))return false;
        assignment.stage=KOMEKnightCommission.Stage.ACCEPTED;return true;
    }
    public synchronized List<KOMEKnightCommission.Type> eligible(Collection<KOMEKnightCommission.Type> available) {
        List<KOMEKnightCommission.Type> result=new ArrayList<KOMEKnightCommission.Type>();for(KOMEKnightCommission.Type type:available)if(!completed.contains(type))result.add(type);return result;
    }
    public synchronized List<KOMEKnightCommission.Type> eligible(Collection<KOMEKnightCommission.Type> available,String faction) {
        List<KOMEKnightCommission.Type> result=new ArrayList<KOMEKnightCommission.Type>();
        Set<KOMEKnightCommission.Type> credited=qualifyingTypes(faction);
        for(KOMEKnightCommission.Type type:available)if(!credited.contains(type))result.add(type);return result;
    }
    public synchronized boolean report(KOMEProgressionNpcRef liege) {
        if(assignment==null||assignment.stage!=KOMEKnightCommission.Stage.READY_TO_REPORT||recorded(assignment.token)||!assignment.liege.hasSameIdentity(liege)||!KOMEProgressionRelationshipLifecycle.sameFaction(assignment.faction,liege.factionKey))return false;
        KOMEKnightCommission evidence=KOMEKnightCommission.readFromNBT(assignment.writeToNBT());if(evidence==null)return false;
        assignment.stage=KOMEKnightCommission.Stage.REPORTED;evidence.stage=KOMEKnightCommission.Stage.REPORTED;
        completed.add(assignment.type);history.add(evidence);trimHistory();assignment=null;return true;
    }
    public synchronized void discardOffer() { if(assignment!=null&&assignment.stage==KOMEKnightCommission.Stage.OFFERED)assignment=null; }
    public synchronized void clearFailed() { if(assignment!=null&&assignment.stage==KOMEKnightCommission.Stage.FAILED){history.add(assignment);trimHistory();assignment=null;} }
    /** Failed attempts are bounded; successful reports retain their Liege and faction provenance. */
    private void trimHistory() { while(history.size()>32){int remove=-1;for(int i=0;i<history.size();i++)if(history.get(i).stage==KOMEKnightCommission.Stage.FAILED){remove=i;break;}if(remove<0)break;history.remove(remove);} }
    public synchronized void reset() { assignment=null;completed.clear();history.clear(); }
    void reconcileRank(KOMEProgressionRank rank){if(assignment!=null&&rank!=KOMEProgressionRank.KNIGHT){assignment.threatResolved=assignment.threatResolved||assignment.stage==KOMEKnightCommission.Stage.READY_TO_REPORT;assignment.stage=KOMEKnightCommission.Stage.FAILED;clearFailed();}}
    public synchronized NBTTagCompound writeToNBT() { NBTTagCompound n=new NBTTagCompound();n.setInteger("Version",1);if(assignment!=null)n.setTag("Assignment",assignment.writeToNBT());NBTTagList c=new NBTTagList();for(KOMEKnightCommission.Type type:completed){NBTTagCompound t=new NBTTagCompound();t.setString("Type",type.name());c.appendTag(t);}n.setTag("CompletedTypes",c);NBTTagList h=new NBTTagList();for(KOMEKnightCommission a:history)h.appendTag(a.writeToNBT());n.setTag("History",h);return n; }
    public synchronized void readFromNBT(NBTTagCompound n) {
        reset();if(n==null)return;
        NBTTagList c=n.getTagList("CompletedTypes",10);for(int i=0;i<c.tagCount();i++)try{completed.add(KOMEKnightCommission.Type.valueOf(c.getCompoundTagAt(i).getString("Type")));}catch(IllegalArgumentException ignored){}
        NBTTagList h=n.getTagList("History",10);
        for(int i=0;i<h.tagCount();i++){KOMEKnightCommission a=KOMEKnightCommission.readFromNBT(h.getCompoundTagAt(i));
            if(a!=null&&!recorded(a.token)&&(a.stage==KOMEKnightCommission.Stage.REPORTED||a.stage==KOMEKnightCommission.Stage.FAILED)){
                history.add(a);if(a.stage==KOMEKnightCommission.Stage.REPORTED)completed.add(a.type);
            }
        }
        trimHistory();assignment=KOMEKnightCommission.readFromNBT(n.getCompoundTag("Assignment"));
        // A stale active report is not proof of a new deed. Only history grants credit.
        if(assignment!=null&&(recorded(assignment.token)||assignment.stage==KOMEKnightCommission.Stage.REPORTED))assignment=null;
    }
}
