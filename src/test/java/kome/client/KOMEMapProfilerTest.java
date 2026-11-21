package kome.client;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEMapProfilerTest {
    @Test public void disabledByDefaultAndFacadeDoesNotReadClockOrCreateCapture() {
        assertFalse(KOMEMapProfiler.ENABLED);assertEquals(0,KOMEMapProfiler.start());
        KOMEMapProfiler.begin(new KOMETerritoryStyle(),true);KOMEMapProfiler.count(KOMEMapProfiler.Count.BUILD);KOMEMapProfiler.end();KOMEMapProfiler.close();
    }
    @Test public void summariesSeparateModesMeasureStagesAndKeepOnlyFourWorstFramesPerMode() {
        AtomicLong clock=new AtomicLong(1);List<String> logs=new ArrayList<>();KOMEMapProfiler p=new KOMEMapProfiler(clock::get,logs::add);
        for(int i=0;i<30;i++) {
            p.beginFrame(i>=15);p.time(KOMEMapProfiler.Stage.PARTITION,123000);p.event(KOMEMapProfiler.Count.HIT);
            p.request(new KOMEMapViewport(0,0,800,600,100+i,200,.25));p.cacheState(20_000_000,3);
            clock.addAndGet(60_000_000);p.endFrame();
        }
        assertTrue("No per-frame output",logs.isEmpty());p.closeCapture();assertEquals(1,logs.size());String report=logs.get(0);
        assertTrue(report.contains("FULL_COLOR frames=15"));assertTrue(report.contains("BORDERS_ONLY frames=15"));
        assertTrue(report.contains("PARTITION{calls=15,totalMs=1.845"));assertTrue(report.contains("HIT=15"));
        assertTrue(report.contains("intervalsOver50ms=14"));assertTrue(report.contains("peakReservedBytes=20000000"));
        assertEquals(8,occurrences(report,"\n worst "));assertFalse(report.contains("posX"));assertFalse(report.contains("posY"));
        assertTrue(report.contains("viewport=800x600"));
    }
    @Test public void reportsArePeriodicCappedAndCloseDoesNotTurnAnAwayIntervalIntoASpike() {
        AtomicLong clock=new AtomicLong(1);List<String> logs=new ArrayList<>();KOMEMapProfiler p=new KOMEMapProfiler(clock::get,logs::add);
        for(int i=0;i<KOMEMapProfiler.MAX_REPORTS+5;i++) {
            p.beginFrame(true);clock.addAndGet(KOMEMapProfiler.WINDOW_NANOS);p.endFrame();
        }
        assertEquals(KOMEMapProfiler.MAX_REPORTS,logs.size());assertTrue(logs.get(logs.size()-1).contains("capture limit reached"));
        p.closeCapture();assertEquals(KOMEMapProfiler.MAX_REPORTS,logs.size());
        logs.clear();p=new KOMEMapProfiler(clock::get,logs::add);p.beginFrame(false);clock.addAndGet(1000);p.endFrame();p.closeCapture();
        clock.addAndGet(100_000_000_000L);p.beginFrame(false);clock.addAndGet(1000);p.endFrame();p.closeCapture();
        assertFalse(logs.get(logs.size()-1).contains("intervalsOver50ms=1"));
    }
    @Test public void rebuildReasonsAndFallbackCountersRemainBoundedAndDoNotMutateViews() {
        AtomicLong clock=new AtomicLong(1);List<String> logs=new ArrayList<>();KOMEMapProfiler p=new KOMEMapProfiler(clock::get,logs::add);
        p.beginFrame(true);KOMEMapViewport first=new KOMEMapViewport(0,0,800,600,100,200,.25);p.request(first);p.build();
        p.request(new KOMEMapViewport(0,0,800,600,101,200,.5));p.event(KOMEMapProfiler.Count.OWNER_PROJECTION_CHANGE);
        p.event(KOMEMapProfiler.Count.PREFERENCE_INVALIDATION);p.build();p.event(KOMEMapProfiler.Count.UNRETAINED);p.event(KOMEMapProfiler.Count.COVERAGE_REJECT);
        p.cacheState(24_000_000,3);clock.addAndGet(1000);p.endFrame();p.closeCapture();String report=logs.get(0);
        for(String item:new String[]{"BUILD=2","COLD_REBUILD=1","PAN_REBUILD=1","ZOOM_REBUILD=1","OWNER_PROJECTION_CHANGE=1","CONTENT_REBUILD=1","PREFERENCE_REBUILD=1","UNRETAINED=1","COVERAGE_REJECT=1"})assertTrue(item,report.contains(item));
        assertEquals(.25,first.zoom,0);assertEquals(100,first.posX,0);
    }
    @Test public void productionBoundariesAreWiredWithoutGpuQueries()throws Exception {
        for(Class<?> type:new Class<?>[]{KOMEMapBorders.class,KOMETerritoryFrameCache.class,KOMETerritoryMesh.class,KOMEConquestMapOverlay.class}) {
            ClassNode node=new ClassNode();try(InputStream in=type.getResourceAsStream(type.getSimpleName()+".class")){new ClassReader(in).accept(node,0);}
            boolean connected=false;
            for(MethodNode method:node.methods)for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)step;if(call.owner.equals("kome/client/KOMEMapProfiler"))connected=true;
                assertFalse("No new synchronous GPU timing",call.name.equals("glFinish")||call.name.equals("glGetQueryObject"));
            }
            assertTrue("Production instrumentation missing in "+type,connected);
        }
    }
    private static int occurrences(String text,String needle){return (text.length()-text.replace(needle,"").length())/needle.length();}
}
