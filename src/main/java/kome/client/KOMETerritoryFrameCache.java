package kome.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A few stable zooms, one aggregate reservation including lazily prepared vertex buffers. */
final class KOMETerritoryFrameCache {
    static final long MAX_BYTES = 25_000_000;
    static final int MAX_FRAMES = 3;
    private final long budget;
    private final List<KOMEMapBorders.TerritoryFrame> frames = new ArrayList<>();
    private long reserved, peak;
    long evictions,coverageRejections;

    KOMETerritoryFrameCache() { this(MAX_BYTES); }
    KOMETerritoryFrameCache(long budget) { this.budget=budget; }

    boolean prepare(Map<Integer,String> owners,Map<Integer,Integer> colors) {
        if(frames.isEmpty())return false;
        if(frames.get(0).contentMatches(owners,colors))return false;
        if(KOMEMapProfiler.ENABLED) {
            if(!frames.get(0).owners.equals(owners))KOMEMapProfiler.count(KOMEMapProfiler.Count.OWNERSHIP_INVALIDATION);
            if(!frames.get(0).colors.equals(colors))KOMEMapProfiler.count(KOMEMapProfiler.Count.COLOR_INVALIDATION);
        }
        clear();return true;
    }

    KOMEMapBorders.TerritoryFrame find(KOMEMapViewport coverage,Map<Integer,String> owners,Map<Integer,Integer> colors,boolean exactZoom) {
        for(int i=frames.size()-1;i>=0;i--) {
            KOMEMapBorders.TerritoryFrame frame=frames.get(i);
            boolean covered=frame.covers(coverage);if(!covered)coverageRejections++;
            if(KOMEMapProfiler.ENABLED) {
                if(!covered)KOMEMapProfiler.count(KOMEMapProfiler.Count.COVERAGE_REJECT);
                if(exactZoom&&frame.view.zoom!=coverage.zoom)KOMEMapProfiler.count(KOMEMapProfiler.Count.ZOOM_REJECT);
            }
            if((!exactZoom || frame.view.zoom==coverage.zoom) && covered && frame.contentMatches(owners,colors)) {
                frames.remove(i);frames.add(frame);return frame;
            }
        }
        return null;
    }

    boolean retain(KOMEMapBorders.TerritoryFrame frame) {
        long cost=cost(frame);
        if(cost>budget && frame.mesh.retainedBytes()==frame.mesh.quads()*36L) {
            // Retain complete geometry without a direct buffer if one compiled mesh cannot fit.
            frame.streamOnly=true;cost=cost(frame);
            KOMEMapProfiler.count(KOMEMapProfiler.Count.STREAM_ONLY);
        }
        if(cost>budget)return false; // Existing exact streaming fallback; never retain beyond the aggregate budget.
        while(frames.size()>=MAX_FRAMES || reserved+cost>budget) {
            reserved-=cost(frames.remove(0));evictions++;KOMEMapProfiler.count(KOMEMapProfiler.Count.EVICTION);
        }
        frames.add(frame);reserved+=cost;peak=Math.max(peak,reserved);return true;
    }

    /** Settled-only recovery: discard optional padding, reuse the existing disjoint partition.
     * Use the established streaming path to avoid reserving another direct buffer. */
    KOMEMapBorders.TerritoryFrame retainViewport(KOMEMapBorders.TerritoryFrame frame,KOMEMapViewport view) {
        if(frame.view.zoom!=view.zoom || !frame.covers(view))return null;
        long available=budget-1024L-128L*(frame.owners.size()+frame.colors.size());
        if(available<0)return null;
        KOMETerritoryMesh clipped=frame.mesh.clipped(view,frame.view,(int)Math.min(Integer.MAX_VALUE,available/36));
        if(clipped==null)return null;
        KOMEMapBorders.TerritoryFrame visible=new KOMEMapBorders.TerritoryFrame(view,frame.owners,frame.colors,clipped);
        visible.streamOnly=true;
        if(!retain(visible))return null;
        KOMEMapProfiler.cache(reserved,frames.size());return visible;
    }

    /** No preparation/replay on the navigation path. Partial coverage is intentionally permitted. */
    KOMEMapBorders.TerritoryFrame prepared(KOMEMapViewport view,boolean exact) {
        for(int pass=0;pass<(exact?1:2);pass++)for(int i=frames.size()-1;i>=0;i--) {
            KOMEMapBorders.TerritoryFrame f=frames.get(i);
            if(f.streamOnly||!f.mesh.verticesPrepared())continue;
            if(pass==0&&f.view.zoom!=view.zoom)continue;
            boolean covered=f.covers(view);
            if(exact&&!covered){coverageRejections++;KOMEMapProfiler.count(KOMEMapProfiler.Count.COVERAGE_REJECT);}
            if(exact?covered:f.intersects(view)){frames.remove(i);frames.add(f);return f;}
        }
        return null;
    }

    static long cost(KOMEMapBorders.TerritoryFrame frame) {
        long primitive=frame.mesh.quads()*(frame.streamOnly || !frame.mesh.cacheable()?36L:100L);
        // Conservative allowance for frame objects, map tables and copied entries; strings/keys are shared.
        return primitive+1024L+128L*(frame.owners.size()+frame.colors.size());
    }
    void clear(){frames.clear();reserved=0;KOMEMapProfiler.cache(0,0);}
    boolean contains(KOMEMapBorders.TerritoryFrame frame){return frames.contains(frame);}
    long completeBudget(){return budget/MAX_FRAMES;}
    long reservedBytes(){return reserved;}
    long peakBytes(){return peak;}
    int size(){return frames.size();}
}
