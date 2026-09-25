package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;

/** Versioned, server-authoritative state for one assigned Trial of Knighthood. */
public final class KOMESerfKnightTrialAssignment {
    public static final int VERSION = 1;
    public enum Stage { ASSIGNED, ACTIVE, OBJECTIVE_COMPLETE, FAILED }
    public final String trialId, assignmentToken, factionKey;
    public final KOMEProgressionNpcRef liege;
    public final long assignedEpochDay;
    public final Stage stage;
    public final int storyVariant;
    public final NBTTagCompound data;

    public KOMESerfKnightTrialAssignment(String trial, String token, KOMEProgressionNpcRef lord, String faction, long day, Stage currentStage, int variant, NBTTagCompound futureData) {
        trialId=trial==null?"":trial; assignmentToken=token==null?"":token; liege=lord==null?KOMEProgressionNpcRef.EMPTY:lord; factionKey=faction==null?"":faction.toLowerCase(); assignedEpochDay=day; stage=currentStage==null?Stage.ASSIGNED:currentStage; storyVariant=Math.max(0,variant); data=futureData==null?new NBTTagCompound():(NBTTagCompound)futureData.copy();
    }
    public static KOMESerfKnightTrialAssignment create(KOMESerfKnightTrial trial, KOMEProgressionNpcRef liege, long day, int storyVariant) {
        return new KOMESerfKnightTrialAssignment(trial.id, UUID.randomUUID().toString(), liege, liege.factionKey, day, Stage.ASSIGNED, storyVariant, new NBTTagCompound());
    }
    public boolean isValidFor(String canonicalTrial, KOMEProgressionNpcRef canonicalLiege) {
        try { UUID.fromString(assignmentToken); } catch (Exception e) { return false; }
        return KOMESerfKnightTrial.forId(trialId)!=null && trialId.equals(canonicalTrial) && liege.isSet() && canonicalLiege!=null && canonicalLiege.hasSameIdentity(liege) && factionKey.length()!=0 && assignedEpochDay>=0L;
    }
    public KOMESerfKnightTrialAssignment withStage(Stage value, NBTTagCompound futureData) { return new KOMESerfKnightTrialAssignment(trialId,assignmentToken,liege,factionKey,assignedEpochDay,value,storyVariant,futureData==null?data:futureData); }
    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag=new NBTTagCompound(); tag.setInteger("Version",VERSION); tag.setString("Trial",trialId); tag.setString("Token",assignmentToken); tag.setTag("Liege",liege.writeToNBT()); tag.setString("Faction",factionKey); tag.setLong("AssignedDay",assignedEpochDay); tag.setString("Stage",stage.name()); tag.setInteger("Story",storyVariant); tag.setTag("Data",data.copy()); return tag;
    }
    public static KOMESerfKnightTrialAssignment readFromNBT(NBTTagCompound tag) {
        if(tag==null||tag.getInteger("Version")!=VERSION)return null; Stage stage;try{stage=Stage.valueOf(tag.getString("Stage"));}catch(Exception e){return null;} return new KOMESerfKnightTrialAssignment(tag.getString("Trial"),tag.getString("Token"),KOMEProgressionNpcRef.readFromNBT(tag.getCompoundTag("Liege")),tag.getString("Faction"),tag.getLong("AssignedDay"),stage,tag.getInteger("Story"),tag.hasKey("Data",10)?tag.getCompoundTag("Data"):new NBTTagCompound());
    }
}
