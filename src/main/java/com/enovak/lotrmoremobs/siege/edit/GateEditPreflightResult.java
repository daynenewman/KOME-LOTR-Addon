package com.enovak.lotrmoremobs.siege.edit;

import com.enovak.lotrmoremobs.siege.gate.GateState;
import java.util.*;

/** Immutable server observation. READY performs no action and is never a commit authorization. */
public final class GateEditPreflightResult {
    public static final int MAX_ISSUES = 8;
    public static final class IssueCount {
        private final GateEditPreflightIssueCode code; private final int count;
        IssueCount(GateEditPreflightIssueCode code,int count){this.code=code;this.count=count;}
        public GateEditPreflightIssueCode getCode(){return code;} public int getCount(){return count;}
    }
    private final long generation, draftSequence;
    private final GateEditPreflightState state;
    private final GateEditStructuralValidation structural;
    private final boolean hasChanges, commitReady, repairActive, ramReserved;
    private final GateEditPreflightIssueCode primaryIssue;
    private final int changeCount, currentRevision;
    private final GateState gateState;
    private final List<IssueCount> issues;
    GateEditPreflightResult(long generation,long draftSequence,GateEditPreflightState state,GateEditStructuralValidation structural,boolean hasChanges,GateEditPreflightIssueCode primaryIssue,int changeCount,int currentRevision,GateState gateState,boolean repairActive,boolean ramReserved,List<IssueCount> issues) {
        this.generation=generation;this.draftSequence=draftSequence;this.state=state;this.structural=structural;this.hasChanges=hasChanges;this.commitReady=state==GateEditPreflightState.READY;
        this.primaryIssue=primaryIssue==null?GateEditPreflightIssueCode.NONE:primaryIssue;this.changeCount=changeCount;this.currentRevision=currentRevision;this.gateState=gateState;
        this.repairActive=repairActive;this.ramReserved=ramReserved;this.issues=Collections.unmodifiableList(new ArrayList<IssueCount>(issues));
    }
    public long getGeneration(){return generation;} public long getDraftSequence(){return draftSequence;} public GateEditPreflightState getState(){return state;}
    public GateEditStructuralValidation getStructural(){return structural;} public boolean isStructurallyValid(){return structural.isStructurallyValid();}
    public boolean hasChanges(){return hasChanges;} public boolean isCommitReady(){return commitReady;} public GateEditPreflightIssueCode getPrimaryIssue(){return primaryIssue;}
    public int getChangeCount(){return changeCount;} public int getCurrentRevision(){return currentRevision;} public GateState getGateState(){return gateState;}
    public boolean isRepairActive(){return repairActive;} public boolean isRamReserved(){return ramReserved;} public List<IssueCount> getIssues(){return issues;}
}
