package kome.common.data;

import java.util.*;

/** Source-only metadata: no destination, route, ETA or movement permission is implied. */
public final class KOMECompanySourceOptions {
    public static final class Row {
        public final String companyId,name,state;
        public final int units,allowance,tilesPerDay;
        public final long populationCenti;
        public final boolean mounted,held;
        public Row(String id,String name,int units,long population,boolean mounted,int allowance,int speed,String state,boolean held){
            companyId=id;this.name=name;this.units=units;populationCenti=population;this.mounted=mounted;
            this.allowance=allowance;tilesPerDay=speed;this.state=state;this.held=held;
        }
    }
    public final UUID session;
    public final long sequence,retryAfterMillis;
    public final String source,token,next;
    public final KOMECompanyDestinationOptions.Result result;
    public final List<Row> rows;
    public final boolean more;
    public KOMECompanySourceOptions(UUID session,long sequence,String source,KOMECompanyDestinationOptions.Result result,
            List<Row> rows,String token,String next,boolean more,long retry) {
        this.session=session;this.sequence=sequence;this.source=source;this.result=result;
        this.rows=Collections.unmodifiableList(new ArrayList<Row>(rows));this.token=token;this.next=next;this.more=more;retryAfterMillis=retry;
    }
}