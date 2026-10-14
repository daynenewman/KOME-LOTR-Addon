package kome.common.data;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import static kome.common.data.KOMEKnightCommission.*;

/** Saved Lordship attempt. The commission encounter is reused, but never earns commission credit. */
public final class KOMELordshipTrial {
    public enum Scenario {
        RELIEF_FORCE(Type.SETTLEMENT_DEFENSE), BORDER_PATROL(Type.BORDER_INCURSION), PROTECTED_EXPEDITION(Type.DANGEROUS_ESCORT), NATIVE_INVASION(Type.BORDER_INCURSION);
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
    public KOMEProgressionNpcRef authority=KOMEProgressionNpcRef.EMPTY;
    public boolean nativeInvasion, nativeVictory, playerDied, invasionEnded;
    public String invasionId="", invasionType="";
    public int invasionRemaining, invasionSize, controllerMissingTicks;
    public int acknowledgedLosses;

    public KOMELordshipTrial(Scenario scenario,KOMEKnightCommission objective,List<String> guards) {
        if(scenario==null||objective==null||objective.type!=scenario.objectiveType||guards.size()<3||guards.size()>5)
            throw new IllegalArgumentException("Invalid Lordship force");
        this.scenario=scenario;this.objective=objective;guardClasses.addAll(guards);
        requiredSurvivors=0;
    }
    public int confirmedSurvivors() {
        int count=0;for(Actor actor:objective.actors)if(actor.role==Role.GUARD&&!actor.dead)count++;return count;
    }
    public boolean ready() { return objective.stage==Stage.READY_TO_REPORT; }
    public NBTTagCompound writeToNBT() {
        NBTTagCompound n=new NBTTagCompound();n.setInteger("Version",2);n.setString("Scenario",scenario.name());
        n.setTag("Authority",authority.writeToNBT());n.setBoolean("NativeInvasion",nativeInvasion);n.setBoolean("NativeVictory",nativeVictory);
        n.setBoolean("PlayerDied",playerDied);n.setBoolean("InvasionEnded",invasionEnded);n.setString("InvasionId",invasionId);n.setString("InvasionType",invasionType);
        n.setInteger("InvasionRemaining",invasionRemaining);n.setInteger("InvasionSize",invasionSize);n.setInteger("ControllerMissingTicks",controllerMissingTicks);
        n.setInteger("AcknowledgedLosses",acknowledgedLosses);
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
            // Version 1 field encounters continue with their saved objective and no casualty quota.
            t.authority=KOMEProgressionNpcRef.readFromNBT(n.getCompoundTag("Authority"));t.nativeInvasion=n.getBoolean("NativeInvasion");
            t.nativeVictory=n.getBoolean("NativeVictory");t.playerDied=n.getBoolean("PlayerDied");t.invasionEnded=n.getBoolean("InvasionEnded");
            t.invasionId=n.getString("InvasionId");t.invasionType=n.getString("InvasionType");t.invasionRemaining=n.getInteger("InvasionRemaining");t.invasionSize=n.getInteger("InvasionSize");t.controllerMissingTicks=n.getInteger("ControllerMissingTicks");
            t.acknowledgedLosses=Math.max(0,Math.min(5,n.getInteger("AcknowledgedLosses")));
            if(!t.invasionId.isEmpty())UUID.fromString(t.invasionId);
            if(t.nativeInvasion&&lotr.common.world.spawning.LOTRInvasions.forName(t.invasionType)==null)return null;
            t.forceReleased=n.getBoolean("ForceReleased");t.finishedAt=n.getLong("Finished");t.failureReason=n.getString("FailureReason");return t;
        } catch(IllegalArgumentException invalid) { return null; }
    }
}
