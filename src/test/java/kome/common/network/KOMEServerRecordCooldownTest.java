package kome.common.network;
import org.junit.Test;
import static org.junit.Assert.*;
public class KOMEServerRecordCooldownTest {
    @Test public void oneHundredRequestsHaveOneAdmissionAndOneClearDuplicateNotice(){
        KOMEServerRecordCooldown limiter=new KOMEServerRecordCooldown(1024);Object a=new Object();int accepted=0,notices=0;
        for(int i=0;i<100;i++){KOMEServerRecordCooldown.Result r=limiter.acquire(a,0L,2000);if(r.accepted)accepted++;if(r.notify)notices++;}
        assertEquals(1,accepted);assertEquals(1,notices);assertFalse(limiter.acquire(a,1_999_999_999L,2000).accepted);
        assertTrue(limiter.acquire(a,2_000_000_000L,2000).accepted);assertTrue(limiter.acquire(new Object(),2_000_000_000L,2000).accepted);
    }
    @Test public void capacityCannotEvictLiveLimitsEvenAfterExpiry(){
        KOMEServerRecordCooldown limiter=new KOMEServerRecordCooldown(2);Object a=new Object(),b=new Object(),c=new Object();
        assertTrue(limiter.acquire(a,0,2000).accepted);assertTrue(limiter.acquire(b,0,2000).accepted);
        assertEquals(-1,limiter.acquire(c,0,2000).retryMillis);assertFalse(limiter.acquire(a,0,2000).accepted);assertEquals(2,limiter.size());
        assertFalse(limiter.acquire(c,2_000_000_000L,2000).accepted);
        limiter.forget(a);assertTrue(limiter.acquire(c,2_000_000_000L,2000).accepted);assertEquals(2,limiter.size());
        limiter.clear();assertEquals(0,limiter.size());assertTrue(limiter.acquire(c,2_000_000_000L,2000).accepted);
    }
    @Test public void nanoTimeWraparoundPreservesElapsedComparison(){
        KOMEServerRecordCooldown limiter=new KOMEServerRecordCooldown(2);Object a=new Object();long start=Long.MAX_VALUE-1_000_000_000L;
        assertTrue(limiter.acquire(a,start,2000).accepted);assertFalse(limiter.acquire(a,start+1_999_999_999L,2000).accepted);
        assertTrue(limiter.acquire(a,start+2_000_000_000L,2000).accepted);
    }
}
