package kome.client;

import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Opt-in, render-thread CPU diagnostics. No GL queries, files, identities or world coordinates. */
final class KOMEMapProfiler {
    static final boolean ENABLED=Boolean.getBoolean("kome.mapProfile");
    static final long WINDOW_NANOS=10_000_000_000L;
    static final int MAX_REPORTS=60, WORST_FRAMES=4;
    enum Stage { CANDIDATES, PARTITION, BUILD_TOTAL, VERTICES, TERRITORY_PASS, BORDER_GEOMETRY, STREAM_REPLAY, DRAW_ARRAYS }
    enum Count { HIT, MISS, EVICTION, COVERAGE_REJECT, ZOOM_REJECT, BUILD, PAN_REBUILD, ZOOM_REBUILD,
        OWNER_PROJECTION_CHANGE, OWNERSHIP_INVALIDATION, COLOR_INVALIDATION, PREFERENCE_INVALIDATION, GEOMETRY_CLEAR,
        CONTENT_REBUILD, PREFERENCE_REBUILD, GEOMETRY_REBUILD, COLD_REBUILD,
        UNRETAINED, STREAM_ONLY, STREAM_DRAW, ARRAY_DRAW, GEOMETRY_BATCH, DEFERRED, PARTIAL_REUSE, COMPLETE_ATTEMPT, COMPLETE_REJECT, SETTLED_REBUILD }
    private static final KOMEMapProfiler LIVE=ENABLED?new KOMEMapProfiler(System::nanoTime,
        line->cpw.mods.fml.common.FMLLog.info("%s",line)):null;
    private final LongSupplier clock;
    private final Consumer<String> output;
    private final Stats[] modes={new Stats(),new Stats()};
    private final long[] frameCounts=new long[Count.values().length],frameTimes=new long[Stage.values().length];
    private long windowStart,frameStart,lastStart,interval,retained,peak,quads;
    private int reports,mode,previousMode=-1,frames,pending;
    private boolean open,stopped,pan,zoom;
    private KOMEMapViewport previousView;
    private double scale;
    private int width,height;
    private KOMETerritoryStyle.Palette palette=KOMETerritoryStyle.Palette.CLASSIC_LOTR;
    private KOMETerritoryStyle.Opacity opacity=KOMETerritoryStyle.Opacity.HALF;
    private boolean tileBorders,layer;

    KOMEMapProfiler(LongSupplier clock,Consumer<String> output){this.clock=clock;this.output=output;windowStart=clock.getAsLong();}
    static long start(){return ENABLED?System.nanoTime():0;}
    static void elapsed(Stage stage,long start){if(ENABLED)LIVE.time(stage,System.nanoTime()-start);}
    static void duration(Stage stage,long nanos){if(ENABLED)LIVE.time(stage,nanos);}
    static void count(Count count){if(ENABLED)LIVE.event(count);}
    static void begin(KOMETerritoryStyle style,boolean visible){if(ENABLED) {
        LIVE.palette=style.palette;LIVE.opacity=style.opacity;LIVE.tileBorders=style.showTileBorders;LIVE.layer=visible;
        LIVE.beginFrame(!style.mode.fillsTerritory());
    }}
    static void end(){if(ENABLED)LIVE.endFrame();}
    static void close(){if(ENABLED)LIVE.closeCapture();}
    static void view(KOMEMapViewport view){if(ENABLED)LIVE.request(view);}
    static void rebuild(){if(ENABLED)LIVE.build();}
    static void cache(long bytes,int count){if(ENABLED)LIVE.cacheState(bytes,count);}
    static void mesh(int quads){if(ENABLED)LIVE.quads=quads;}

    void beginFrame(boolean borders) {
        if(stopped)return;
        long now=clock.getAsLong();mode=borders?1:0;
        interval=lastStart!=0&&previousMode==mode?now-lastStart:0;
        lastStart=now;previousMode=mode;frameStart=now;open=true;
        Arrays.fill(frameCounts,0);Arrays.fill(frameTimes,0);quads=0;
    }
    void endFrame() {
        if(stopped||!open)return;
        long now=clock.getAsLong(),draw=now-frameStart;open=false;
        Stats s=modes[mode];s.frames++;s.draw+=draw;s.worstDraw=Math.max(s.worstDraw,draw);
        if(interval>0){s.intervals++;s.interval+=interval;s.worstInterval=Math.max(s.worstInterval,interval);if(interval>=50_000_000L)s.spikes++;}
        long score=Math.max(draw,interval);
        int slot=0;for(int i=1;i<WORST_FRAMES;i++)if(s.worstScore[i]<s.worstScore[slot])slot=i;
        if(score>s.worstScore[slot]) {
            s.worstScore[slot]=score;
            s.worst[slot]="drawMs="+ms(draw)+" startIntervalMs="+ms(interval)+" zoom="+scale+" viewport="+width+"x"+height
                +" palette="+palette+" opacity="+opacity.percent+" tileBorders="+tileBorders+" conquest="+layer
                +" quads="+quads+" vertices="+(quads*4)+" reservedBytes="+retained+" counts="+nonzero(Count.values(),frameCounts)
                +" stageMs="+timings(frameTimes);
        }
        if(now-windowStart>=WINDOW_NANOS)report("periodic",now);
    }
    void time(Stage stage,long nanos) {
        if(stopped)return;int at=stage.ordinal();Stats s=modes[mode];s.nanos[at]+=Math.max(0,nanos);s.calls[at]++;
        s.max[at]=Math.max(s.max[at],nanos);if(open)frameTimes[at]+=Math.max(0,nanos);
    }
    void event(Count count) {
        if(stopped)return;int at=count.ordinal();modes[mode].counts[at]++;if(open)frameCounts[at]++;
        if(count==Count.OWNER_PROJECTION_CHANGE||count==Count.OWNERSHIP_INVALIDATION||count==Count.COLOR_INVALIDATION)pending|=1;
        if(count==Count.PREFERENCE_INVALIDATION)pending|=2;
        if(count==Count.GEOMETRY_CLEAR)pending|=4;
    }
    void request(KOMEMapViewport v) {
        if(stopped)return;
        pan=previousView!=null&&(previousView.posX!=v.posX||previousView.posY!=v.posY);
        zoom=previousView!=null&&previousView.zoom!=v.zoom;
        previousView=v;scale=v.zoom;width=(int)(v.right-v.left);height=(int)(v.bottom-v.top);
    }
    void build() {
        if(stopped)return;event(Count.BUILD);
        if(pan)event(Count.PAN_REBUILD);if(zoom)event(Count.ZOOM_REBUILD);
        if((pending&1)!=0)event(Count.CONTENT_REBUILD);if((pending&2)!=0)event(Count.PREFERENCE_REBUILD);
        if((pending&4)!=0)event(Count.GEOMETRY_REBUILD);
        if(!pan&&!zoom&&pending==0)event(Count.COLD_REBUILD);pending=0;
    }
    void cacheState(long bytes,int count) {
        if(stopped)return;retained=bytes;frames=count;peak=Math.max(peak,bytes);
        modes[mode].peakBytes=Math.max(modes[mode].peakBytes,bytes);modes[mode].peakFrames=Math.max(modes[mode].peakFrames,count);
    }
    void closeCapture() {
        open=false;long now=clock.getAsLong();if(!stopped)report("map-close-or-reload",now);
        windowStart=now;lastStart=0;previousMode=-1;previousView=null;pan=zoom=false;
    }
    private void report(String reason,long now) {
        boolean any=false;for(Stats s:modes)if(s.frames>0)any=true;
        if(!any)return;
        StringBuilder line=new StringBuilder("[KOME-MAP-PROFILE] report=").append(++reports).append('/').append(MAX_REPORTS)
            .append(" reason=").append(reason).append(" windowSeconds=").append(ms(now-windowStart)/1000)
            .append(" retainedBytes=").append(retained).append(" retainedFrames=").append(frames).append(" capturePeakBytes=").append(peak)
            .append(" budgetBytes=").append(KOMETerritoryFrameCache.MAX_BYTES).append(" CPU_ONLY");
        for(int m=0;m<2;m++) {
            Stats s=modes[m];if(s.frames==0)continue;
            line.append("\n mode=").append(m==0?"FULL_COLOR":"BORDERS_ONLY").append(" frames=").append(s.frames)
                .append(" meanDrawMs=").append(ms(s.draw)/s.frames).append(" worstDrawMs=").append(ms(s.worstDraw))
                .append(" meanStartIntervalMs=").append(s.intervals==0?0:ms(s.interval)/s.intervals).append(" worstStartIntervalMs=").append(ms(s.worstInterval))
                .append(" intervalsOver50ms=").append(s.spikes).append(" peakReservedBytes=").append(s.peakBytes).append(" peakFrames=").append(s.peakFrames)
                .append(" counts=").append(nonzero(Count.values(),s.counts));
            for(Stage stage:Stage.values()) {int at=stage.ordinal();if(s.calls[at]>0)line.append(" ").append(stage).append("{calls=").append(s.calls[at])
                .append(",totalMs=").append(ms(s.nanos[at])).append(",meanMs=").append(ms(s.nanos[at])/s.calls[at]).append(",maxMs=").append(ms(s.max[at])).append('}');}
            for(String worst:s.worst)if(worst!=null)line.append("\n worst ").append(m==0?"FULL_COLOR":"BORDERS_ONLY").append(' ').append(worst);
            s.clear();
        }
        if(reports==MAX_REPORTS){line.append("\n capture limit reached; restart client for another capture");stopped=true;}
        output.accept(line.toString());windowStart=now;
    }
    private static double ms(long nanos){return Math.round(nanos/1000.0)/1000.0;}
    private static String nonzero(Enum<?>[] names,long[] values) {
        StringBuilder out=new StringBuilder("{");for(int i=0;i<names.length;i++)if(values[i]!=0)out.append(names[i]).append('=').append(values[i]).append(',');return out.append('}').toString();
    }
    private static String timings(long[] values) {
        StringBuilder out=new StringBuilder("{");for(Stage stage:Stage.values())if(values[stage.ordinal()]!=0)out.append(stage).append('=').append(ms(values[stage.ordinal()])).append(',');return out.append('}').toString();
    }
    private static final class Stats {
        long frames,draw,worstDraw,interval,intervals,worstInterval,spikes,peakBytes,peakFrames;
        final long[] counts=new long[Count.values().length],nanos=new long[Stage.values().length],calls=new long[Stage.values().length],max=new long[Stage.values().length],worstScore=new long[WORST_FRAMES];
        final String[] worst=new String[WORST_FRAMES];
        void clear(){frames=draw=worstDraw=interval=intervals=worstInterval=spikes=peakBytes=peakFrames=0;
            Arrays.fill(counts,0);Arrays.fill(nanos,0);Arrays.fill(calls,0);Arrays.fill(max,0);Arrays.fill(worstScore,0);Arrays.fill(worst,null);}
    }
}
