package kome.common.network;

import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import kome.common.KOMEAccessFixture;
import kome.common.KOMETestServerSession;
import kome.common.data.KOMETileTestResources;
import kome.common.data.KOMEServerRecordBuilder;
import org.junit.*;
import static org.junit.Assert.*;

/** Measured inert queue/real projection fixtures; timings are observations, never TPS assertions. */
public class KOMEServerQueueLoadMeasurementTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    @Test public void measureBoundsAndDrainWithRealServerRecordProjections()throws Exception{
        List<String> report=new ArrayList<String>();
        report.add("Java="+System.getProperty("java.version")+"; processors="+Runtime.getRuntime().availableProcessors());
        report.add("Limits: pending=1024, perConnection=32, attemptsPerTick=64, softBudgetNanos=2000000");
        report.add("Inert registered players and transport; actual KOMEServerRecordBuilder; no live clients/TPS/FPS claim.");
        KOMEAccessFixture[] fixtures=new KOMEAccessFixture[20];
        for(int i=0;i<fixtures.length;i++)fixtures[i]=new KOMEAccessFixture();
        fixtures[0].data.initializeIntegratedWorld();
        for(int i=0;i<200;i++)fixtures[0].data.playerNames.put(new UUID(0x70L,i),"SavedPlayer"+i);
        for(int i=0;i<20;i++){
            kome.common.data.KOMEWar war=new kome.common.data.KOMEWar();war.id="MeasurementWar"+i;war.status=kome.common.data.KOMEWar.ENDED;
            war.sideOneFactions.add("gondor");war.sideTwoFactions.add("mordor");fixtures[0].data.wars.put(war.id,war);
        }
        report.add("Fixture: 200 saved offline player rows, 20 historical wars, "+fixtures[0].data.conquestTiles.size()+" canonical tiles.");
        for(KOMEAccessFixture f:fixtures)f.player.worldObj=fixtures[0].world;
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous=KOMEPacketHandler.network;
        Map<UUID,Integer> rebuilds=new HashMap<UUID,Integer>();List<Long> projectionTimes=new ArrayList<Long>();
        try(KOMETestServerSession session=new KOMETestServerSession(fixtures)){
            KOMEPacketHandler.network=fixtures[0].network;
            KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest> handler=new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>(
                new KOMEPacketServerRecordRequest.Handler(()->0L,player->{
                    long start=System.nanoTime();List rows=KOMEServerRecordBuilder.build(player.worldObj,player.canCommandSenderUseCommand(2,"kome"));
                    projectionTimes.add(System.nanoTime()-start);Integer old=rebuilds.get(player.getUniqueID());rebuilds.put(player.getUniqueID(),old==null?1:old+1);return rows;
                }));
            long start=System.nanoTime();
            for(KOMEAccessFixture f:fixtures)for(int n=0;n<100;n++)handler.onMessage(new KOMEPacketServerRecordRequest(0L),f.context);
            long admission=System.nanoTime()-start;int peak=KOMEPacketHandler.pendingServerTaskCount();
            assertEquals(640,peak);int attempts=0,maxAttempts=0,ticks=0,firstTick=0;List<Long> drainTimes=new ArrayList<Long>();
            while(KOMEPacketHandler.pendingServerTaskCount()>0){
                start=System.nanoTime();int count=KOMEPacketHandler.runPendingServerTasks();long elapsed=System.nanoTime()-start;
                assertTrue(count>0 && count<=64);assertTrue(KOMEPacketHandler.pendingServerTaskCount()<=1024);
                if(ticks++==0)firstTick=count;attempts+=count;maxAttempts=Math.max(maxAttempts,count);drainTimes.add(elapsed);
            }
            assertEquals(640,attempts);assertEquals(20,rebuilds.size());for(Integer count:rebuilds.values())assertEquals(Integer.valueOf(1),count);
            report.add(String.format(Locale.ROOT,"record_flood players=20 offered=2000 admitted=%d rejected=%d peak_pending=%d drain_invocations=%d total_attempts=%d first_tick_attempts=%d max_tick_attempts=%d projections=%d admission_ms=%.3f",peak,2000-peak,peak,ticks,attempts,firstTick,maxAttempts,rebuilds.size(),admission/1e6));
            int completed=0,resets=0;
            for(cpw.mods.fml.common.network.simpleimpl.IMessage message:fixtures[0].network.messages){
                KOMEPacketServerRecordData data=(KOMEPacketServerRecordData)message;if(data.complete)completed++;if(data.reset)resets++;
            }
            assertEquals(20,completed);assertEquals(20,resets);
            report.add("Logical complete responses="+completed+"; response chunks="+fixtures[0].network.messages.size());
            report.add(summary("real_projection_ms",projectionTimes));report.add(summary("record_drain_invocation_ms",drainTimes));
        }finally{KOMEPacketHandler.network=previous;}
        // Separate global-cap flood measures admission pressure from many independent identities.
        for(int round=0;round<5;round++){
            KOMEServerTaskQueue queue=new KOMEServerTaskQueue(1024,32,64,2_000_000L);long session=queue.open();AtomicInteger calls=new AtomicInteger();
            long start=System.nanoTime();int accepted=0;
            for(int n=0;n<100000;n++)if(queue.offer(session,new Object(),calls::incrementAndGet,null)==KOMEServerTaskQueue.Admission.ACCEPTED)accepted++;
            long admission=System.nanoTime()-start;assertEquals(1024,accepted);int ticks=0,maxAttempts=0;long drainTotal=0;
            while(queue.pending()>0){start=System.nanoTime();int count=queue.drain(error->{throw new AssertionError(error);},System::nanoTime);drainTotal+=System.nanoTime()-start;ticks++;maxAttempts=Math.max(maxAttempts,count);assertTrue(count>0&&count<=64);}
            assertEquals(1024,calls.get());
            report.add(String.format(Locale.ROOT,"global_flood round=%d offered=100000 admitted=1024 rejected=98976 peak_pending=1024 drain_invocations=%d max_tick_attempts=%d admission_ms=%.3f total_drain_ms=%.3f",round,ticks,maxAttempts,admission/1e6,drainTotal/1e6));
        }
        Path file=Paths.get("build/kom70-measurements.txt");Files.write(file,report,StandardCharsets.UTF_8);
        for(String line:report)System.out.println(line);
    }
    private static String summary(String label,List<Long> values){
        Collections.sort(values);long sum=0;for(long n:values)sum+=n;
        return String.format(Locale.ROOT,"%s samples=%d mean=%.3f p50=%.3f p95=%.3f max=%.3f",label,values.size(),sum/(double)values.size()/1e6,values.get(values.size()/2)/1e6,values.get((int)Math.ceil(values.size()*.95)-1)/1e6,values.get(values.size()-1)/1e6);
    }
}
