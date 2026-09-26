package kome.common.siege.validation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class KOMEValidationResult {
    private final List<KOMEValidationIssue> issues;
    public KOMEValidationResult(List<KOMEValidationIssue> issues){
        List<KOMEValidationIssue> copy=new ArrayList<KOMEValidationIssue>(issues);
        Collections.sort(copy);this.issues=Collections.unmodifiableList(copy);
    }
    public List<KOMEValidationIssue> getIssues(){return issues;}
    public boolean isValid(){
        for(KOMEValidationIssue issue:issues)if(issue.getSeverity()==KOMEValidationSeverity.ERROR)return false;
        return true;
    }
    public boolean hasCode(KOMEValidationCode code){
        for(KOMEValidationIssue issue:issues)if(issue.getCode()==code)return true;
        return false;
    }
}
