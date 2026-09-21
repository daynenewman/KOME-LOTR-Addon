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
        private void cancel() { assigned = false; assignmentData = null; }
    }
    private KOMEProgressionNpcRef serfdomMaster = KOMEProgressionNpcRef.EMPTY;
    private KOMEProgressionNpcRef prospectiveLiege = KOMEProgressionNpcRef.EMPTY;
    private KOMEProgressionNpcRef deceasedMaster = KOMEProgressionNpcRef.EMPTY;
    private KOMEProgressionNpcRef deceasedLiege = KOMEProgressionNpcRef.EMPTY;
    private final EnumMap<KOMESerfKnightDutyType, Duty> duties = new EnumMap<KOMESerfKnightDutyType, Duty>(KOMESerfKnightDutyType.class);
    private String trialId = "";
    private boolean trialCompleted, partingGiftReceived, promoted;
    private boolean masterReplacementRequired, liegeReplacementRequired;
    private long betrayalLockoutUntilDay;
    private long lastAssignmentEpochDay = -1L;
    public KOMESerfKnightProgression() { for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values()) duties.put(type, new Duty()); }
    public KOMEProgressionNpcRef getSerfdomMaster() { return serfdomMaster; }
    public KOMEProgressionNpcRef getProspectiveLiege() { return prospectiveLiege; }
    public KOMEProgressionNpcRef getDeceasedMaster() { return deceasedMaster; }
    public KOMEProgressionNpcRef getDeceasedLiege() { return deceasedLiege; }
    public boolean isMasterReplacementRequired() { return masterReplacementRequired; }
    public boolean isLiegeReplacementRequired() { return liegeReplacementRequired; }
    public long getBetrayalLockoutUntilDay() { return betrayalLockoutUntilDay; }
    public long getLastAssignmentEpochDay() { return lastAssignmentEpochDay; }
    public boolean hasActiveAssignment() { return getActiveAssignmentKind().length() != 0; }
    /** Derived assignment authority; never persisted separately. */
    public String getActiveAssignmentKind() { for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values()) { Duty duty=duties.get(type); if(duty.assigned&&!duty.completed)return type.key; } return trialId.length()!=0&&!trialCompleted ? "trial" : ""; }
    public boolean isLockedOut(long calendarDay) { return calendarDay < betrayalLockoutUntilDay; }
    public Duty getDuty(KOMESerfKnightDutyType type) { return type == null ? null : duties.get(type); }
    public String getTrialId() { return trialId; }
    public boolean isTrialCompleted() { return trialCompleted; }
    public boolean hasPartingGift() { return partingGiftReceived; }
    public boolean isPromoted() { return promoted; }
    /** Derived solely from canonical facts; it is deliberately not serialized. */
    public KOMESerfKnightPhase getPhase() {
        if (promoted) return KOMESerfKnightPhase.COMPLETE;
        if (!allDutiesComplete()) return KOMESerfKnightPhase.SERFDOM_DUTIES;
        if (trialCompleted) return partingGiftReceived ? KOMESerfKnightPhase.READY_FOR_KNIGHT : KOMESerfKnightPhase.PARTING_GIFT_PENDING;
        if (!prospectiveLiege.isSet() || trialId.length() == 0) return KOMESerfKnightPhase.SEEKING_LIEGE;
        if (!trialCompleted) return KOMESerfKnightPhase.TRIAL_ASSIGNED;
        return KOMESerfKnightPhase.PARTING_GIFT_PENDING;
    }
    void setSerfdomMaster(KOMEProgressionNpcRef value) { serfdomMaster = value; masterReplacementRequired=false; }
    void setProspectiveLiege(KOMEProgressionNpcRef value) { prospectiveLiege = value; liegeReplacementRequired=false; }
    void assignDuty(KOMESerfKnightDutyType type, NBTTagCompound data) { duties.get(type).assign(data); }
    public void setDutyAssignmentData(KOMESerfKnightDutyType type, NBTTagCompound data) { if(type!=null && duties.get(type).assigned) duties.get(type).assignmentData=data==null?null:(NBTTagCompound)data.copy(); }
    void completeDuty(KOMESerfKnightDutyType type) { duties.get(type).complete(); }
    void setTrial(String id) { trialId = id; trialCompleted = false; }
    void setTrialCompleted() { trialCompleted = true; }
    void setPartingGiftReceived() { partingGiftReceived = true; }
    void setPromoted() { promoted = true; }
    void setLastAssignmentEpochDay(long day) { lastAssignmentEpochDay=day; }
    void lockoutUntil(long day) { betrayalLockoutUntilDay=Math.max(betrayalLockoutUntilDay,day); }
    void handleMasterDeath(boolean betrayal) { deceasedMaster=serfdomMaster; serfdomMaster=KOMEProgressionNpcRef.EMPTY; masterReplacementRequired=true; if(betrayal) { for(Duty duty:duties.values()) duty.clear(); prospectiveLiege=KOMEProgressionNpcRef.EMPTY; trialId=""; trialCompleted=false; partingGiftReceived=false; liegeReplacementRequired=true; } else for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values()) { Duty duty=duties.get(type); if(duty.assigned&&!duty.completed)duty.cancel(); } }
    void handleLiegeDeath(boolean betrayal) { deceasedLiege=prospectiveLiege; prospectiveLiege=KOMEProgressionNpcRef.EMPTY; liegeReplacementRequired=true; if(betrayal || !trialCompleted) { trialId=""; trialCompleted=false; partingGiftReceived=false; } }
    void leaveSerfdomMaster() { serfdomMaster=KOMEProgressionNpcRef.EMPTY; masterReplacementRequired=false; deceasedMaster=KOMEProgressionNpcRef.EMPTY; for(Duty duty:duties.values())duty.clear(); prospectiveLiege=KOMEProgressionNpcRef.EMPTY; deceasedLiege=KOMEProgressionNpcRef.EMPTY; liegeReplacementRequired=false; trialId=""; trialCompleted=false; partingGiftReceived=false; promoted=false; }
    void leaveProspectiveLiege() { prospectiveLiege=KOMEProgressionNpcRef.EMPTY; deceasedLiege=KOMEProgressionNpcRef.EMPTY; liegeReplacementRequired=false; trialId=""; trialCompleted=false; partingGiftReceived=false; promoted=false; }
    public void reset() { serfdomMaster=KOMEProgressionNpcRef.EMPTY; prospectiveLiege=KOMEProgressionNpcRef.EMPTY; deceasedMaster=KOMEProgressionNpcRef.EMPTY; deceasedLiege=KOMEProgressionNpcRef.EMPTY; for (Duty duty : duties.values()) duty.clear(); trialId=""; trialCompleted=false; partingGiftReceived=false; promoted=false; masterReplacementRequired=false; liegeReplacementRequired=false; betrayalLockoutUntilDay=0L; lastAssignmentEpochDay=-1L; }
    private boolean allDutiesComplete() { for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values()) if (!duties.get(type).completed) return false; return true; }
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound(); tag.setTag("Master", serfdomMaster.writeToNBT()); tag.setTag("Liege", prospectiveLiege.writeToNBT()); tag.setTag("DeceasedMaster", deceasedMaster.writeToNBT()); tag.setTag("DeceasedLiege", deceasedLiege.writeToNBT()); tag.setBoolean("MasterReplacement",masterReplacementRequired); tag.setBoolean("LiegeReplacement",liegeReplacementRequired); tag.setLong("BetrayalLockoutUntilDay",betrayalLockoutUntilDay); if(lastAssignmentEpochDay>=0L)tag.setLong("LastAssignmentEpochDay",lastAssignmentEpochDay); tag.setString("Trial", trialId); tag.setBoolean("TrialCompleted", trialCompleted); tag.setBoolean("PartingGift", partingGiftReceived); tag.setBoolean("Promoted", promoted);
        NBTTagCompound dutyTag = new NBTTagCompound(); for (KOMESerfKnightDutyType type : KOMESerfKnightDutyType.values()) { Duty duty=duties.get(type); NBTTagCompound entry=new NBTTagCompound(); entry.setBoolean("Assigned", duty.assigned); entry.setBoolean("Completed", duty.completed); if(duty.assignmentData != null) entry.setTag("Data", duty.assignmentData.copy()); dutyTag.setTag(type.key, entry); } tag.setTag("Duties", dutyTag); return tag;
    }
    public void readFromNBT(NBTTagCompound tag) {
        reset(); if (tag == null) return; serfdomMaster=KOMEProgressionNpcRef.readFromNBT(tag.getCompoundTag("Master")); prospectiveLiege=KOMEProgressionNpcRef.readFromNBT(tag.getCompoundTag("Liege")); deceasedMaster=KOMEProgressionNpcRef.readFromNBT(tag.getCompoundTag("DeceasedMaster")); deceasedLiege=KOMEProgressionNpcRef.readFromNBT(tag.getCompoundTag("DeceasedLiege")); masterReplacementRequired=tag.getBoolean("MasterReplacement"); liegeReplacementRequired=tag.getBoolean("LiegeReplacement"); betrayalLockoutUntilDay=Math.max(0L,tag.getLong("BetrayalLockoutUntilDay")); long savedDay=tag.hasKey("LastAssignmentEpochDay")?tag.getLong("LastAssignmentEpochDay"):-1L; lastAssignmentEpochDay=KOMEProgressionCalendar.isSanePersistedDay(savedDay)?savedDay:-1L; trialId=tag.getString("Trial"); trialCompleted=tag.getBoolean("TrialCompleted"); partingGiftReceived=tag.getBoolean("PartingGift"); promoted=tag.getBoolean("Promoted");
        NBTTagCompound dutyTag=tag.getCompoundTag("Duties"); for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values()) { NBTTagCompound entry=dutyTag.getCompoundTag(type.key); Duty duty=duties.get(type); if(entry.getBoolean("Assigned")) duty.assign(entry.hasKey("Data", 10) ? entry.getCompoundTag("Data") : null); if(entry.getBoolean("Completed") && duty.assigned) duty.complete(); }
        reconcile();
    }
    private void reconcile() {
        boolean activeDutyFound=false; for(KOMESerfKnightDutyType type:KOMESerfKnightDutyType.values()){Duty duty=duties.get(type);if(duty.assigned&&!duty.completed){if(activeDutyFound)duty.cancel();else activeDutyFound=true;}}
        // A duty wins deterministically over a concurrently serialized unfinished trial.
        if(activeDutyFound && !trialCompleted) { trialId=""; trialCompleted=false; partingGiftReceived=false; }
        if (!allDutiesComplete()) { prospectiveLiege=KOMEProgressionNpcRef.EMPTY; trialId=""; trialCompleted=false; partingGiftReceived=false; promoted=false; return; }
        if (!prospectiveLiege.isSet() && !trialCompleted) { trialId=""; trialCompleted=false; partingGiftReceived=false; promoted=false; return; }
        if (KOMESerfKnightTrial.forId(trialId) == null) { trialId=""; trialCompleted=false; partingGiftReceived=false; promoted=false; return; }
        if (!trialCompleted) { partingGiftReceived=false; promoted=false; return; }
        if (!partingGiftReceived) promoted=false;
    }
}
