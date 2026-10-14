package kome.common.network;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;

/** Server-thread-only token bucket: burst two, refill one per second. No gameplay projections are cached. */
final class KOMECompanyDestinationRateLimit {
    private static final long SECOND = 1_000_000_000L;
    private static final int BURST = 2;
    private final int capacity;
    private final Map<Object,Bucket> buckets = new IdentityHashMap<Object,Bucket>();
    KOMECompanyDestinationRateLimit(int capacity) {
        if(capacity<1)throw new IllegalArgumentException("Rate-limit capacity required");
        this.capacity=capacity;
    }
    Result acquire(Object connection,long received,long now) {
        if(connection==null||now-received<0)throw new IllegalArgumentException("Invalid destination intake");
        Bucket bucket=buckets.get(connection);
        if(bucket==null) {
            if(buckets.size()>=capacity)return new Result(false,-1);
            bucket=new Bucket(received);buckets.put(connection,bucket);
        }
        // At most two refills, regardless of elapsed time. Refilled tokens retain their creation time:
        // an old queued request cannot consume credit that did not exist when it was received.
        while(bucket.refilling && now-bucket.nextRefill>=0 && bucket.tokens.size()<BURST) {
            bucket.tokens.addLast(bucket.nextRefill);bucket.nextRefill+=SECOND;
            if(bucket.tokens.size()==BURST)bucket.refilling=false;
        }
        Long available=bucket.tokens.peekFirst();
        if(available!=null && received-available>=0) {
            bucket.tokens.removeFirst();
            if(!bucket.refilling) {bucket.refilling=true;bucket.nextRefill=now+SECOND;}
            return new Result(true,0);
        }
        long next=available==null?bucket.nextRefill:available;
        long remaining=next-now;
        return new Result(false,remaining<=0?0:(remaining+999_999L)/1_000_000L);
    }
    void forget(Object connection) {buckets.remove(connection);}
    void clear() {buckets.clear();}
    int size() {return buckets.size();}
    private static final class Bucket {
        final ArrayDeque<Long> tokens=new ArrayDeque<Long>(BURST);
        long nextRefill;
        boolean refilling;
        Bucket(long received) {tokens.add(received);tokens.add(received);}
    }
    static final class Result {
        final boolean accepted;
        final long retryMillis;
        Result(boolean accepted,long retryMillis) {this.accepted=accepted;this.retryMillis=retryMillis;}
    }
}
