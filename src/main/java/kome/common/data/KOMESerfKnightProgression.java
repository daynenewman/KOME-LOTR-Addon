package kome.common.data;

import java.util.EnumMap;
import net.minecraft.nbt.NBTTagCompound;

/** Persistent, domain-only state for the revised Serf-to-Knight path. */
public final class KOMESerfKnightProgression {
    public static final class Duty {
        private boolean assigned, completed;
        private NBTTagCompound assignmentData;
        public boolean isAssigned() { return assigned; }
        public boolean isCompleted() { return completed; }
        public NBTTagCompound getAssignmentData() { return assignmentData == null ? null : (NBTTagCompound) assignmentData.copy(); }
        private void assign(NBTTagCompound data) { assigned = true; assignmentData = data == null ? null : (NBTTagCompound) data.copy(); }
        private void complete() { completed = true; }
        private void clear() { assigned = false; completed = false; assignmentData = null; }
    }
    private KOMEProgressionNpcRef serfdomMaster = KOMEProgressionNpcRef.EMPTY;
    private KOMEProgressionNpcRef prospectiveLiege = KOMEProgressionNpcRef.EMPTY;
    private final EnumMap<KOMESerfKnightDutyType, Duty> duties = new EnumMap<KOMESerfKnightDutyType, Duty>(KOMESerfKnightDutyType.class);
    private String trialId = "";
    private boolean trialCompleted, partingGiftReceived, promoted;
    public KOMESerfKnightProgression() { for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values()) duties.put(type, new Duty()); }
    public KOMEProgressionNpcRef getSerfdomMaster() { return serfdomMaster; }
    public KOMEProgressionNpcRef getProspectiveLiege() { return prospectiveLiege; }
    public Duty getDuty(KOMESerfKnightDutyType type) { return type == null ? null : duties.get(type); }
    public String getTrialId() { return trialId; }
    public boolean isTrialCompleted() { return trialCompleted; }
    public boolean hasPartingGift() { return partingGiftReceived; }
    public boolean isPromoted() { return promoted; }
    /** Derived solely from canonical facts; it is deliberately not serialized. */
    public KOMESerfKnightPhase getPhase() {
        if (promoted) return KOMESerfKnightPhase.COMPLETE;
        if (!serfdomMaster.isSet() || !allDutiesComplete()) return KOMESerfKnightPhase.SERFDOM_DUTIES;
        if (!prospectiveLiege.isSet() || trialId.length() == 0) return KOMESerfKnightPhase.SEEKING_LIEGE;
        if (!trialCompleted) return KOMESerfKnightPhase.TRIAL_ASSIGNED;
        if (!partingGiftReceived) return KOMESerfKnightPhase.PARTING_GIFT_PENDING;
        return KOMESerfKnightPhase.READY_FOR_KNIGHT;
    }
    void setSerfdomMaster(KOMEProgressionNpcRef value) { serfdomMaster = value; }
    void setProspectiveLiege(KOMEProgressionNpcRef value) { prospectiveLiege = value; }
    void assignDuty(KOMESerfKnightDutyType type, NBTTagCompound data) { duties.get(type).assign(data); }
    void completeDuty(KOMESerfKnightDutyType type) { duties.get(type).complete(); }
    void setTrial(String id) { trialId = id; trialCompleted = false; }
    void setTrialCompleted() { trialCompleted = true; }
    void setPartingGiftReceived() { partingGiftReceived = true; }
    void setPromoted() { promoted = true; }
    public void reset() { serfdomMaster=KOMEProgressionNpcRef.EMPTY; prospectiveLiege=KOMEProgressionNpcRef.EMPTY; for (Duty duty : duties.values()) duty.clear(); trialId=""; trialCompleted=false; partingGiftReceived=false; promoted=false; }
    private boolean allDutiesComplete() { for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values()) if (!duties.get(type).completed) return false; return true; }
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setTag("Master", serfdomMaster.writeToNBT()); tag.setTag("Liege", prospectiveLiege.writeToNBT()); tag.setString("Trial", trialId); tag.setBoolean("TrialCompleted", trialCompleted); tag.setBoolean("PartingGift", partingGiftReceived); tag.setBoolean("Promoted", promoted);
        NBTTagCompound dutyTag = new NBTTagCompound(); for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values()) { Duty duty=duties.get(type); NBTTagCompound entry=new NBTTagCompound(); entry.setBoolean("Assigned", duty.assigned); entry.setBoolean("Completed", duty.completed); if(duty.assignmentData != null) entry.setTag("Data", duty.assignmentData.copy()); dutyTag.setTag(type.key, entry); } tag.setTag("Duties", dutyTag); return tag;
    }
    public void readFromNBT(NBTTagCompound tag) {
        reset(); if (tag == null) return; serfdomMaster=KOMEProgressionNpcRef.readFromNBT(tag.getCompoundTag("Master")); prospectiveLiege=KOMEProgressionNpcRef.readFromNBT(tag.getCompoundTag("Liege")); trialId=tag.getString("Trial"); trialCompleted=tag.getBoolean("TrialCompleted"); partingGiftReceived=tag.getBoolean("PartingGift"); promoted=tag.getBoolean("Promoted");
        NBTTagCompound dutyTag=tag.getCompoundTag("Duties"); for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values()) { NBTTagCompound entry=dutyTag.getCompoundTag(type.key); Duty duty=duties.get(type); if(entry.getBoolean("Assigned")) duty.assign(entry.hasKey("Data", 10) ? entry.getCompoundTag("Data") : null); if(entry.getBoolean("Completed") && duty.assigned) duty.complete(); }
        reconcile();
    }
    private void reconcile() {
        if (!serfdomMaster.isSet()) { reset(); return; }
        if (!allDutiesComplete()) { prospectiveLiege=KOMEProgressionNpcRef.EMPTY; trialId=""; trialCompleted=false; partingGiftReceived=false; promoted=false; return; }
        if (!prospectiveLiege.isSet()) { trialId=""; trialCompleted=false; partingGiftReceived=false; promoted=false; return; }
        if (KOMESerfKnightTrial.forId(trialId) == null) { trialId=""; trialCompleted=false; partingGiftReceived=false; promoted=false; return; }
        if (!trialCompleted) { partingGiftReceived=false; promoted=false; return; }
        if (!partingGiftReceived) promoted=false;
    }
}
