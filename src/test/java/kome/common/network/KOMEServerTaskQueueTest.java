package kome.common.network;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEServerTaskQueueTest {
    private static void failTask(RuntimeException error) { throw new AssertionError(error); }
    @Test public void floodIsBoundedAndIndependentPlayerStillAdmittedWithCoalescedNotice() {
        KOMEServerTaskQueue queue=new KOMEServerTaskQueue(1024,32,64,2_000_000L);
        long session=queue.open(); Object a=new Object(),b=new Object();
        AtomicInteger calls=new AtomicInteger(),notices=new AtomicInteger();
        for(int n=0;n<100;n++) assertEquals(n<32?KOMEServerTaskQueue.Admission.ACCEPTED:KOMEServerTaskQueue.Admission.FULL,
            queue.offer(session,a,calls::incrementAndGet,notices::incrementAndGet));
        assertEquals(KOMEServerTaskQueue.Admission.ACCEPTED,queue.offer(session,b,calls::incrementAndGet,null));
        assertEquals(33,queue.pending()); assertEquals(33,queue.drain(KOMEServerTaskQueueTest::failTask,()->0L));
        assertEquals(33,calls.get()); assertEquals(1,notices.get()); assertEquals(0,queue.pending());
    }
    @Test public void globalSaturationDropsNewestAndNeverAllocatesRejectedLanes() {
        KOMEServerTaskQueue queue=new KOMEServerTaskQueue(1024,32,64,2_000_000L);
        long session=queue.open(); AtomicInteger calls=new AtomicInteger();
        for(int n=0;n<1024;n++) assertEquals(KOMEServerTaskQueue.Admission.ACCEPTED,
            queue.offer(session,new Object(),calls::incrementAndGet,null));
        for(int n=0;n<10000;n++) assertEquals(KOMEServerTaskQueue.Admission.FULL,queue.offer(session,new Object(),calls::incrementAndGet,null));
        assertEquals(1024,queue.pending()); assertEquals(64,queue.drain(KOMEServerTaskQueueTest::failTask,()->0L));
        assertEquals(960,queue.pending()); assertEquals(64,calls.get());
        while(queue.pending()>0) queue.drain(KOMEServerTaskQueueTest::failTask,()->0L);
        assertEquals(1024,calls.get());
    }
    @Test public void roundRobinPreservesEachConnectionsFifoAndTickCountBudget() {
        KOMEServerTaskQueue queue=new KOMEServerTaskQueue(100,10,4,2_000_000L);
        long session=queue.open(); Object a=new Object(),b=new Object(); List<String> order=new ArrayList<String>();
        for(int n=0;n<6;n++){final int index=n;queue.offer(session,a,()->order.add("a"+index),null);}
        queue.offer(session,b,()->order.add("b0"),null);queue.offer(session,b,()->order.add("b1"),null);
        assertEquals(4,queue.drain(KOMEServerTaskQueueTest::failTask,()->0L));
        assertEquals(Arrays.asList("a0","b0","a1","b1"),order);
        queue.drain(KOMEServerTaskQueueTest::failTask,()->0L);
        assertEquals(Arrays.asList("a0","b0","a1","b1","a2","a3","a4","a5"),order);
    }
    @Test public void elapsedBudgetStopsBetweenIndivisibleTasksWithoutStarvingOtherLane() {
        KOMEServerTaskQueue queue=new KOMEServerTaskQueue(100,10,64,2_000_000L);
        long session=queue.open(); long[] now={0}; List<String> order=new ArrayList<String>(); Object a=new Object();
        queue.offer(session,a,()->{order.add("a0");now[0]+=3_000_000L;},null);
        queue.offer(session,a,()->order.add("a1"),null);queue.offer(session,new Object(),()->order.add("b0"),null);
        assertEquals(1,queue.drain(KOMEServerTaskQueueTest::failTask,()->now[0]));
        assertEquals(2,queue.drain(KOMEServerTaskQueueTest::failTask,()->now[0]));
        assertEquals(Arrays.asList("a0","b0","a1"),order);
    }
    @Test public void newTasksWaitForNextTickAndFailureRunsOnce() {
        KOMEServerTaskQueue queue=new KOMEServerTaskQueue(100,10,64,2_000_000L);
        long session=queue.open(); AtomicInteger failures=new AtomicInteger(),calls=new AtomicInteger();Object a=new Object();
        queue.offer(session,a,()->{queue.offer(session,a,calls::incrementAndGet,null);throw new IllegalStateException();},null);
        queue.offer(session,new Object(),calls::incrementAndGet,null);
        assertEquals(2,queue.drain(e->failures.incrementAndGet(),()->0L));assertEquals(1,calls.get());
        assertEquals(1,queue.drain(e->failures.incrementAndGet(),()->0L));assertEquals(2,calls.get());assertEquals(1,failures.get());
    }
    @Test public void stopAndRestartFenceOldAdmissionsAndInProgressDrain() {
        KOMEServerTaskQueue queue=new KOMEServerTaskQueue(10,10,10,2_000_000L);
        long old=queue.open();AtomicInteger calls=new AtomicInteger();Object a=new Object();long[] current={old};
        queue.offer(old,a,()->{queue.close();current[0]=queue.open();queue.offer(current[0],a,calls::incrementAndGet,null);},null);
        queue.offer(old,a,()->calls.addAndGet(100),null);
        assertEquals(1,queue.drain(KOMEServerTaskQueueTest::failTask,()->0L));assertEquals(0,calls.get());
        assertEquals(KOMEServerTaskQueue.Admission.CLOSED,queue.offer(old,a,calls::incrementAndGet,null));
        assertEquals(1,queue.drain(KOMEServerTaskQueueTest::failTask,()->0L));assertEquals(1,calls.get());
        queue.close();assertEquals(0,queue.pending());assertEquals(KOMEServerTaskQueue.Admission.CLOSED,queue.offer(current[0],a,calls::incrementAndGet,null));
    }
    @Test public void concurrentFloodAdmissionCannotOverrunBoundsOrDuplicateWork() throws Exception {
        KOMEServerTaskQueue queue=new KOMEServerTaskQueue(1024,32,64,2_000_000L);
        long session=queue.open();AtomicInteger accepted=new AtomicInteger(),executed=new AtomicInteger();
        ExecutorService pool=Executors.newFixedThreadPool(8);List<Future<?>> jobs=new ArrayList<Future<?>>();
        try{
            for(int t=0;t<8;t++){Object key=new Object();jobs.add(pool.submit(()->{
                for(int n=0;n<10000;n++) if(queue.offer(session,key,executed::incrementAndGet,null)==KOMEServerTaskQueue.Admission.ACCEPTED)accepted.incrementAndGet();
            }));}
            for(Future<?> job:jobs)job.get(30,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
        assertEquals(256,accepted.get());assertEquals(256,queue.pending());
        while(queue.pending()>0)queue.drain(KOMEServerTaskQueueTest::failTask,()->0L);
        assertEquals(accepted.get(),executed.get());
    }
    @Test public void forgettingConnectionRemovesOnlyItsPendingTasks() {
        KOMEServerTaskQueue queue=new KOMEServerTaskQueue(10,10,10,2_000_000L);long session=queue.open();Object a=new Object(),b=new Object();
        AtomicInteger calls=new AtomicInteger();queue.offer(session,a,()->calls.addAndGet(100),null);queue.offer(session,b,calls::incrementAndGet,null);
        queue.forget(a);assertEquals(1,queue.pending());queue.drain(KOMEServerTaskQueueTest::failTask,()->0L);assertEquals(1,calls.get());
    }
}
