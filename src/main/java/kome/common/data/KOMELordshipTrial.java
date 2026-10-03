package kome.common.data;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import static kome.common.data.KOMEKnightCommission.*;

/** Saved Lordship attempt. The commission encounter is reused, but never earns commission credit. */
public final class KOMELordshipTrial {
    public enum Scenario {
        RELIEF_FORCE(Type.SETTLEMENT_DEFENSE), BORDER_PATROL(Type.BORDER_INCURSION), PROTECTED_EXPEDITION(Type.DANGEROUS_ESCORT);
        final Type objectiveType;
        Scenario(Type type) { objectiveType=type; }
    }
    public final Scenario scenario;
    public final KOMEKnightCommission objective;
    public final List<String> guardClasses=new ArrayList<String>();
    public int requiredSurvivors;
    public long finishedAt;
    public boolean forceReleased;
    public String failureReason="";

    public KOMELordshipTrial(Scenario scenario,KOMEKnightCommission objective,List<String> guards) {
        if(scenario==null||objective==null||objective.type!=scenario.objectiveType||guards.size()<3||guards.size()>5)
            throw new IllegalArgumentException("Invalid Lordship force");
        this.scenario=scenario;this.objective=objective;guardClasses.addAll(guards);
        requiredSurvivors=(guards.size()+1)/2;
    }
    public int confirmedSurvivors() {
        int count=0;for(Actor actor:objective.actors)if(actor.role==Role.GUARD&&!actor.dead)count++;return count;
    }
    public boolean ready() { return objective.stage==Stage.READY_TO_REPORT; }
    public NBTTagCompound writeToNBT() {
        NBTTagCompound n=new NBTTagCompound();n.setInteger("Version",1);n.setString("Scenario",scenario.name());
        n.setTag("Objective",objective.writeToNBT());n.setInteger("RequiredSurvivors",requiredSurvivors);
        n.setBoolean("ForceReleased",forceReleased);n.setLong("Finished",finishedAt);n.setString("FailureReason",failureReason);
        NBTTagList guards=new NBTTagList();for(String type:guardClasses){NBTTagCompound t=new NBTTagCompound();t.setString("Class",type);guards.appendTag(t);}n.setTag("GuardClasses",guards);return n;
    }
    public static KOMELordshipTrial readFromNBT(NBTTagCompound n) {
        if(n==null||!n.hasKey("Scenario"))return null;
        try {
            List<String> guards=new ArrayList<String>();NBTTagList list=n.getTagList("GuardClasses",10);
            if(list.tagCount()<3||list.tagCount()>5)return null;
            for(int i=0;i<list.tagCount();i++){String type=list.getCompoundTagAt(i).getString("Class");if(type.isEmpty())return null;guards.add(type);}
            KOMELordshipTrial t=new KOMELordshipTrial(Scenario.valueOf(n.getString("Scenario")),KOMEKnightCommission.readFromNBT(n.getCompoundTag("Objective")),guards);
            // The survival rule is authoritative, never relaxed by saved data.
            t.forceReleased=n.getBoolean("ForceReleased");t.finishedAt=n.getLong("Finished");t.failureReason=n.getString("FailureReason");return t;
        } catch(IllegalArgumentException invalid) { return null; }
    }
}
