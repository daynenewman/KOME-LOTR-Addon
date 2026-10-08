package kome.common.siege.validation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class KOMEValidationIssue implements Comparable<KOMEValidationIssue> {
    private final KOMEValidationSeverity severity;
    private final KOMEValidationCode code;
    private final String message;
    private final List<String> subjectIds;
    public KOMEValidationIssue(KOMEValidationSeverity severity,KOMEValidationCode code,String message,String... subjectIds){
        if(severity==null||code==null)throw new IllegalArgumentException();
        this.severity=severity;this.code=code;this.message=message==null?"":message;
        List<String> ids=new ArrayList<String>(Arrays.asList(subjectIds==null?new String[0]:subjectIds));
        for(int i=0;i<ids.size();i++)ids.set(i,ids.get(i)==null?"":ids.get(i));
        Collections.sort(ids);this.subjectIds=Collections.unmodifiableList(ids);
    }
    public KOMEValidationSeverity getSeverity(){return severity;}
    public KOMEValidationCode getCode(){return code;}
    public String getMessage(){return message;}
    public List<String> getSubjectIds(){return subjectIds;}
    @Override public int compareTo(KOMEValidationIssue other){
        int value=severity.compareTo(other.severity);if(value==0)value=code.compareTo(other.code);
        if(value==0)value=subjectIds.toString().compareTo(other.subjectIds.toString());
        return value==0?message.compareTo(other.message):value;
    }
}
