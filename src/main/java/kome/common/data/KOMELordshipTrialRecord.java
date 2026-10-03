package kome.common.data;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import static kome.common.data.KOMEKnightCommission.Stage;

/** One ordinary trial, with bounded attempt history and retained successful evidence. */
public final class KOMELordshipTrialRecord {
    private KOMELordshipTrial assignment;
    private final List<KOMELordshipTrial> history=new ArrayList<KOMELordshipTrial>();
    public synchronized KOMELordshipTrial assignment() { return assignment; }
    public synchronized List<KOMELordshipTrial> history() { return Collections.unmodifiableList(new ArrayList<>(history)); }
    private boolean recorded(String token){for(KOMELordshipTrial t:history)if(t.objective.token.equals(token))return true;return false;}
    public synchronized boolean offer(KOMELordshipTrial trial) { if(assignment!=null||trial==null||recorded(trial.objective.token)||trial.objective.stage==Stage.REPORTED)return false;assignment=trial;return true; }
    public synchronized void archive() {
        if(assignment==null)return;
        if(!recorded(assignment.objective.token))history.add(assignment);assignment=null;
        while(history.size()>16){int remove=0;for(int i=0;i<history.size();i++)if(history.get(i).objective.stage==Stage.FAILED){remove=i;break;}history.remove(remove);}
    }
    public synchronized void reset() { assignment=null;history.clear(); }
    public synchronized NBTTagCompound writeToNBT() {
        NBTTagCompound n=new NBTTagCompound();n.setInteger("Version",1);if(assignment!=null)n.setTag("Assignment",assignment.writeToNBT());
        NBTTagList h=new NBTTagList();for(KOMELordshipTrial trial:history)h.appendTag(trial.writeToNBT());n.setTag("History",h);return n;
    }
    public synchronized void readFromNBT(NBTTagCompound n) {
        reset();if(n==null)return;assignment=KOMELordshipTrial.readFromNBT(n.getCompoundTag("Assignment"));
        NBTTagList h=n.getTagList("History",10);for(int i=Math.max(0,h.tagCount()-16);i<h.tagCount();i++){
            KOMELordshipTrial t=KOMELordshipTrial.readFromNBT(h.getCompoundTagAt(i));
            if(t!=null&&!recorded(t.objective.token)&&(t.objective.stage==Stage.REPORTED||t.objective.stage==Stage.FAILED))history.add(t);
        }
        if(assignment!=null&&(recorded(assignment.objective.token)||assignment.objective.stage==Stage.REPORTED))assignment=null;
    }
    /** Invalid rank state cannot keep command roles alive. Entity markers reconcile on load. */
    void reconcileRank(KOMEProgressionRank rank){if(assignment!=null&&rank!=KOMEProgressionRank.KNIGHT){assignment.objective.stage=Stage.FAILED;assignment.failureReason="rank";archive();}}
}
