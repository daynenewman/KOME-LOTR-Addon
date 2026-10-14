package kome.common.data;

import java.util.UUID;

/** Bounded incremental publication. One immutable projected company per message, or a header only. */
public final class KOMECompanyRallySnapshot {
    public static final int MAX_CANDIDATES=64,MAX_SESSIONS=8;
    public static final long LIFETIME_MILLIS=120000L;
    public enum Phase { LOADING, READY, EXECUTING, DONE, ERROR }
    public enum Outcome { PREVIEW, RALLIED, SKIPPED, PENDING }
    public static final class Entry {
        public final int index;
        public final KOMECompanyDestinationOptions.Row company;
        public final String faction,reason;
        public final boolean included;
        public final Outcome outcome;
        public Entry(int index,KOMECompanyDestinationOptions.Row company,String faction,boolean included,Outcome outcome,String reason){
            this.index=index;this.company=company;this.faction=faction;this.included=included;this.outcome=outcome;this.reason=reason;
        }
    }
    public final UUID session,token;
    public final String destination,message;
    public final Phase phase;
    public final int revision,total,evaluated,included,processed,rallied;
    public final Entry entry;
    public KOMECompanyRallySnapshot(UUID session,UUID token,String destination,Phase phase,int revision,int total,
            int evaluated,int included,int processed,int rallied,String message,Entry entry){
        this.session=session;this.token=token;this.destination=destination;this.phase=phase;this.revision=revision;this.total=total;
        this.evaluated=evaluated;this.included=included;this.processed=processed;this.rallied=rallied;this.message=message;this.entry=entry;
    }
}