package kome.client;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import kome.common.data.KOMETileRasterSnapshot;

/** Derived client presentation only. No ownership, world state, GL objects, or alternate lookup. */
final class KOMEMapBorders {
    static final double TERRITORY_WIDTH = 6;
    private static final int[] TERRITORY_ALPHA = {255, 176, 96, 36};
    static final int MAX_RUNS = 1_000_000;
    static final int BORDER_COLOR = 0xDD493922;
    static final int HOVER_COLOR = 0xEEFFE04A;
    private final Runs horizontal, vertical, fills;
    private TerritoryFrame territoryFrame, animatedFrame;
    private final KOMETerritoryFrameCache territoryFrames = new KOMETerritoryFrameCache();
    private final KOMEMapNavigation navigation=new KOMEMapNavigation();
    static final int MAX_COMPLETE_CANDIDATES=200_000;
    private int rejectedComplete;
    private Map<Integer,String> attemptOwners;
    private Map<Integer,Integer> attemptColors;
    long completeAttempts,completeRejects;
    private KOMEMapViewport unretainedView;
    private Map<Integer,String> unretainedOwners;
    private Map<Integer,Integer> unretainedColors;
    private boolean warnedUnretained;
    long territoryBuilds, territoryHits, territoryMisses, territoryUnretainedBuilds;
    long territoryBuildNanos, territoryWorstBuildNanos;
    private KOMETerritoryStyle.Palette presentationPalette;
    private KOMETerritoryStyle.DisplayMode presentationMode;
    private KOMETerritoryStyle.Opacity presentationOpacity;
    private boolean presentationEdges;
    final int width, height;
    final double mapPerCellX, mapPerCellY;

    private KOMEMapBorders(KOMETileRasterSnapshot source, int[] pixels) {
        width = source.width; height = source.height;
        if (pixels.length != (long) width * height) throw new IllegalArgumentException("Raster copy dimensions disagree");
        mapPerCellX = (double) source.transform.mapWidth / width;
        mapPerCellY = (double) source.transform.mapHeight / height;
        // O(cells) once per immutable snapshot. Primitive runs, never per-cell objects.
        horizontal = extract(pixels, false, false, MAX_RUNS);
        vertical = extract(pixels, true, false, MAX_RUNS - horizontal.count());
        fills = extract(pixels, false, true, MAX_RUNS - horizontal.count() - vertical.count());
    }

    interface EdgeSink {
        void edge(boolean vertical, int line, int start, int end, int negativeColor, int positiveColor);
    }
    interface QuadSink {
        void quad(double x0, double y0, double x1, double y1, int argb);
    }

    /** One client-thread cache; identity is the actual immutable resolver snapshot, not a revision guess. */
    static final class Cache {
        private KOMETileRasterSnapshot source;
        private KOMEMapBorders geometry;
        KOMEMapBorders get(KOMETileRasterSnapshot next, int[] decodedCopy) {
            if (next == null) { clear(); return null; }
            if (source != next) {
                clear(); // A failed construction must not leave borders from a different snapshot.
                KOMEMapBorders complete = new KOMEMapBorders(next, decodedCopy);
                geometry = complete;
                source = next;
            }
            return geometry;
        }
        void clear() { if(geometry!=null)geometry.clearTerritoryFrames();source = null; geometry = null; }
    }

    private Runs extract(int[] pixels, boolean verticalAxis, boolean fill, int limit) {
        int lines = (verticalAxis ? width : height) + (fill ? 0 : 1);
        int length = verticalAxis ? height : width;
        int[] offsets = new int[lines + 1];
        Builder builder = new Builder(limit);
        for (int line = 0; line < lines; line++) {
            offsets[line] = builder.size / 4;
            int start = 0, previousA = 0, previousB = 0;
            for (int along = 0; along <= length; along++) {
                int a = 0, b = 0;
                if (along < length) {
                    if (fill) {
                        a = label(pixels[line * width + along]);
                    } else if (verticalAxis) {
                        a = line == 0 ? 0 : label(pixels[along * width + line - 1]);
                        b = line == width ? 0 : label(pixels[along * width + line]);
                    } else {
                        a = line == 0 ? 0 : label(pixels[(line - 1) * width + along]);
                        b = line == height ? 0 : label(pixels[line * width + along]);
                    }
                    if (!fill && a == b) { a = 0; b = 0; }
                }
                if (a != previousA || b != previousB) {
                    if (previousA != 0 || previousB != 0) builder.add(start, along, previousA, previousB);
                    start = along; previousA = a; previousB = b;
                }
            }
        }
        offsets[lines] = builder.size / 4;
        return new Runs(offsets, Arrays.copyOf(builder.values, builder.size));
    }

    private static int label(int argb) { return (argb >>> 24) > 24 ? 0xFF000000 | (argb & 0xFFFFFF) : 0; }
    int edgeRuns() { return horizontal.count() + vertical.count(); }
    int fillRuns() { return fills.count(); }
    long primitiveBytes() { return horizontal.bytes() + vertical.bytes() + fills.bytes(); }

    /** Cull by indexed grid line, then binary-search the first intersecting run on that line. */
    void visitEdges(double left, double top, double right, double bottom, EdgeSink sink) {
        visit(horizontal, false, left, top, right, bottom, sink);
        visit(vertical, true, top, left, bottom, right, sink);
    }

    private static void visit(Runs runs, boolean verticalAxis, double minAlong, double minLine,
            double maxAlong, double maxLine, EdgeSink sink) {
        int first = (int) Math.max(0, Math.ceil(minLine));
        int last = (int) Math.min(runs.offsets.length - 2, Math.floor(maxLine));
        for (int line = first; line <= last; line++) {
            int end = runs.offsets[line + 1];
            for (int i = runs.first(line, minAlong); i < end; i++) {
                int p = i * 4;
                if (runs.values[p] >= maxAlong) break;
                sink.edge(verticalAxis, line, runs.values[p], runs.values[p + 1], runs.values[p + 2], runs.values[p + 3]);
            }
        }
    }

    void drawBorders(KOMEMapViewport view, int hoverRgb, QuadSink sink) {
        drawBorders(view, hoverRgb, false, sink);
    }

    void drawBorders(KOMEMapViewport view, int hoverRgb, boolean hoverOnly, QuadSink sink) {
        double sx = view.zoom * mapPerCellX, sy = view.zoom * mapPerCellY;
        visitEdges(view.mapX(view.left - 1.5) / mapPerCellX, view.mapY(view.top - 1.5) / mapPerCellY,
            view.mapX(view.right + 1.5) / mapPerCellX, view.mapY(view.bottom + 1.5) / mapPerCellY,
            (verticalAxis, line, start, end, a, b) -> {
                boolean hover = hoverRgb != 0 && ((a != 0 && (a & 0xFFFFFF) == hoverRgb)
                    || (b != 0 && (b & 0xFFFFFF) == hoverRgb));
                if (hoverOnly && !hover) return;
                double stroke = Math.min(hover ? 1.5 : 1.0, verticalAxis ? sx : sy);
                // Shared edge: one centered stroke. Gap/outer edge: paint solely on assigned land.
                double negative = a == 0 ? 0 : b == 0 ? stroke : stroke / 2;
                double positive = b == 0 ? 0 : a == 0 ? stroke : stroke / 2;
                double at = verticalAxis ? view.screenX(line * mapPerCellX) : view.screenY(line * mapPerCellY);
                if (verticalAxis) {
                    clipped(view, at - negative, view.screenY(start * mapPerCellY),
                        at + positive, view.screenY(end * mapPerCellY), hover ? HOVER_COLOR : BORDER_COLOR, sink);
                } else {
                    clipped(view, view.screenX(start * mapPerCellX), at - negative,
                        view.screenX(end * mapPerCellX), at + positive, hover ? HOVER_COLOR : BORDER_COLOR, sink);
                }
            });
    }

    /** Ownership identity, rather than palette RGB, decides whether an edge matters. */
    void drawTerritories(KOMEMapViewport view, Map<Integer, String> owners, Map<Integer, Integer> colors, QuadSink sink) {
        TerritoryFrame frame=territoryFrame(view,owners,colors);
        try {frame.mesh.draw(view,frame.view,sink);}
        catch(RuntimeException failure){clearTerritoryFrames();throw failure;}
    }

    TerritoryFrame territoryFrame(KOMEMapViewport view,Map<Integer,String> owners,Map<Integer,Integer> colors) {
        return territoryFrame(view,owners,colors,false,view.zoom);
    }

    TerritoryFrame navigationTerritoryFrame(KOMEMapViewport view,Map<Integer,String> owners,Map<Integer,Integer> colors,
            boolean animating,double minimumZoom,long now) {
        KOMEMapProfiler.view(view);
        if(territoryFrames.prepare(owners,colors))clearTerritoryFrames();
        if(unretainedView!=null&&(!unretainedOwners.equals(owners)||!unretainedColors.equals(colors))) {
            unretainedView=null;unretainedOwners=null;unretainedColors=null;navigation.clear();
        }
        boolean settled=navigation.settled(view,animating,now);
        TerritoryFrame found=territoryFrames.prepared(view,true);
        if(found!=null){territoryHits++;KOMEMapProfiler.count(KOMEMapProfiler.Count.HIT);return found;}
        if(settled) {
            if(sameView(unretainedView,view))return territoryFrames.prepared(view,false);
            long before=territoryBuilds;
            TerritoryFrame exact=territoryFrame(view,owners,colors,false,view.zoom,true);
            if(territoryBuilds!=before)KOMEMapProfiler.count(KOMEMapProfiler.Count.SETTLED_REBUILD);
            if(!territoryFrames.contains(exact)) {
                TerritoryFrame visible=territoryFrames.retainViewport(exact,view);
                if(visible!=null)exact=visible;
            }
            if(!territoryFrames.contains(exact)) {
                // An impossible retained request must not turn into an expensive idle-frame loop.
                // Keep only its small input certificate, never the over-budget mesh itself.
                unretainedView=view;unretainedOwners=exact.owners;unretainedColors=exact.colors;
                if(!warnedUnretained) {
                    warnedUnretained=true;java.util.logging.Logger.getLogger("KOME").warning("Visible border geometry exceeds retained budget even without padding/direct vertices; borders may remain incomplete until view/content changes. Repeated idle reconstruction suppressed.");
                }
                return territoryFrames.prepared(view,false);
            }
            if(!exact.streamOnly&&exact.mesh.cacheable())exact.mesh.vertices();
            return exact;
        }
        found=territoryFrames.prepared(view,false);
        KOMEMapProfiler.count(KOMEMapProfiler.Count.DEFERRED);
        if(found!=null){territoryHits++;KOMEMapProfiler.count(KOMEMapProfiler.Count.HIT);KOMEMapProfiler.count(KOMEMapProfiler.Count.PARTIAL_REUSE);}
        else{territoryMisses++;KOMEMapProfiler.count(KOMEMapProfiler.Count.MISS);}
        return found;
    }

    TerritoryFrame territoryFrame(KOMEMapViewport view,Map<Integer,String> owners,Map<Integer,Integer> colors,
            boolean nativeZoomAnimating,double nativeMinimumZoom) {
        return territoryFrame(view,owners,colors,nativeZoomAnimating,nativeMinimumZoom,false);
    }
    private TerritoryFrame territoryFrame(KOMEMapViewport view,Map<Integer,String> owners,Map<Integer,Integer> colors,
            boolean nativeZoomAnimating,double nativeMinimumZoom,boolean affordableWholeMap) {
        KOMEMapProfiler.view(view);
        if(territoryFrames.prepare(owners,colors)){territoryFrame=null;animatedFrame=null;}
        if(!nativeZoomAnimating)animatedFrame=null;
        double coverageZoom=nativeZoomAnimating && Double.isFinite(nativeMinimumZoom) && nativeMinimumZoom>0
            ?Math.min(view.zoom,nativeMinimumZoom):view.zoom;
        KOMEMapViewport coverage=new KOMEMapViewport(view.left,view.top,view.right,view.bottom,view.posX,view.posY,coverageZoom);
        TerritoryFrame found=null;
        if(nativeZoomAnimating && animatedFrame!=null && animatedFrame.contentMatches(owners,colors) && animatedFrame.covers(coverage))found=animatedFrame;
        if(found==null)found=territoryFrames.find(coverage,owners,colors,!nativeZoomAnimating);
        if(found!=null){territoryHits++;territoryFrame=found;if(nativeZoomAnimating)animatedFrame=found;
            KOMEMapProfiler.count(KOMEMapProfiler.Count.HIT);KOMEMapProfiler.cache(territoryFrames.reservedBytes(),territoryFrames.size());return found;}
        territoryMisses++;territoryFrame=null;animatedFrame=null;
        KOMEMapProfiler.count(KOMEMapProfiler.Count.MISS);KOMEMapProfiler.rebuild();
        long start=System.nanoTime();
        KOMEMapViewport wholeCoverage=completeCoverage(coverage);
        if(affordableWholeMap && wholeCoverage!=null) {
            TerritoryFrame whole=affordableComplete(wholeCoverage,owners,colors);
            if(whole!=null) {
                long elapsed=System.nanoTime()-start;territoryBuilds++;territoryBuildNanos+=elapsed;territoryWorstBuildNanos=Math.max(territoryWorstBuildNanos,elapsed);
                KOMEMapProfiler.duration(KOMEMapProfiler.Stage.BUILD_TOTAL,elapsed);
                if(!territoryFrames.retain(whole))throw new IllegalStateException("Preflighted complete mesh exceeded retained budget");
                territoryFrame=whole;KOMEMapProfiler.mesh(whole.mesh.quads());KOMEMapProfiler.cache(territoryFrames.reservedBytes(),territoryFrames.size());return whole;
            }
        }
        KOMEMapViewport padded=new KOMEMapViewport(coverage.left-64,coverage.top-64,coverage.right+64,coverage.bottom+64,coverage.posX,coverage.posY,coverage.zoom);
        // Preserve the eager geometry oracle. Live rejected complete candidates must instead
        // fall through to ordinary partial coverage, not bypass the bounded admission above.
        boolean completeMap=!affordableWholeMap && wholeCoverage!=null;
        if(completeMap)padded=wholeCoverage;
        KOMETerritoryMesh.Rectangles candidates=new KOMETerritoryMesh.Rectangles();
        long profileStart=KOMEMapProfiler.start();
        drawTerritoriesUncached(padded,owners,colors,candidates::add);
        KOMEMapProfiler.elapsed(KOMEMapProfiler.Stage.CANDIDATES,profileStart);
        TerritoryFrame complete=new TerritoryFrame(padded,owners,colors,KOMETerritoryMesh.union(candidates),completeMap);
        long elapsed=System.nanoTime()-start;territoryBuilds++;territoryBuildNanos+=elapsed;territoryWorstBuildNanos=Math.max(territoryWorstBuildNanos,elapsed);
        KOMEMapProfiler.duration(KOMEMapProfiler.Stage.BUILD_TOTAL,elapsed);
        if(territoryFrames.retain(complete)){territoryFrame=complete;if(nativeZoomAnimating)animatedFrame=complete;}
        else {territoryUnretainedBuilds++;KOMEMapProfiler.count(KOMEMapProfiler.Count.UNRETAINED);}
        KOMEMapProfiler.cache(territoryFrames.reservedBytes(),territoryFrames.size());KOMEMapProfiler.mesh(complete.mesh.quads());
        return complete;
    }

    void territoryPresentation(KOMETerritoryStyle style) {
        if(presentationPalette!=style.palette || presentationMode!=style.mode || presentationOpacity!=style.opacity || presentationEdges!=style.showTileBorders) {
            KOMEMapProfiler.count(KOMEMapProfiler.Count.PREFERENCE_INVALIDATION);
            clearTerritoryFrames();presentationPalette=style.palette;presentationMode=style.mode;presentationOpacity=style.opacity;presentationEdges=style.showTileBorders;
        }
    }
    void clearTerritoryFrames(){territoryFrames.clear();territoryFrame=null;animatedFrame=null;navigation.clear();rejectedComplete=0;attemptOwners=null;attemptColors=null;unretainedView=null;unretainedOwners=null;unretainedColors=null;warnedUnretained=false;}

    private static boolean sameView(KOMEMapViewport a,KOMEMapViewport b) {
        return a!=null&&a.left==b.left&&a.top==b.top&&a.right==b.right&&a.bottom==b.bottom&&a.posX==b.posX&&a.posY==b.posY&&a.zoom==b.zoom;
    }

    /** Complete coverage is worthwhile only inside the already accepted padded footprint. */
    private KOMEMapViewport completeCoverage(KOMEMapViewport coverage) {
        if(coverage.zoom>.25)return null;
        double mapWidth=width*mapPerCellX,mapHeight=height*mapPerCellY;
        if(mapWidth*coverage.zoom>coverage.right-coverage.left+128
                || mapHeight*coverage.zoom>coverage.bottom-coverage.top+128)return null;
        KOMEMapViewport centered=new KOMEMapViewport(coverage.left-64,coverage.top-64,coverage.right+64,coverage.bottom+64,
            mapWidth/2,mapHeight/2,coverage.zoom);
        return centered.mapX(centered.left)<=0 && centered.mapY(centered.top)<=0
            && centered.mapX(centered.right)>=mapWidth && centered.mapY(centered.bottom)>=mapHeight?centered:null;
    }

    private TerritoryFrame affordableComplete(KOMEMapViewport coverage,Map<Integer,String> owners,Map<Integer,Integer> colors) {
        int bit=coverage.zoom==.125?1:coverage.zoom==.25?2:0;
        if(bit==0)return null;
        if(attemptOwners==null||!attemptOwners.equals(owners)||!attemptColors.equals(colors)) {
            attemptOwners=new HashMap<>(owners);attemptColors=new HashMap<>(colors);rejectedComplete=0;
        }
        if((rejectedComplete&bit)!=0)return null;
        completeAttempts++;KOMEMapProfiler.count(KOMEMapProfiler.Count.COMPLETE_ATTEMPT);
        long overhead=1024L+128L*(owners.size()+colors.size());
        int maxQuads=(int)Math.max(0,(territoryFrames.completeBudget()-overhead)/100);
        try {
            KOMETerritoryMesh.Rectangles candidates=new KOMETerritoryMesh.Rectangles(MAX_COMPLETE_CANDIDATES);
            long profileStart=KOMEMapProfiler.start();
            try{drawTerritoriesUncached(coverage,owners,colors,candidates::add);}
            finally{KOMEMapProfiler.elapsed(KOMEMapProfiler.Stage.CANDIDATES,profileStart);}
            KOMETerritoryMesh mesh=KOMETerritoryMesh.union(candidates,maxQuads);
            TerritoryFrame frame=new TerritoryFrame(coverage,owners,colors,mesh,true);
            if(mesh.cacheable()&&KOMETerritoryFrameCache.cost(frame)<=territoryFrames.completeBudget())return frame;
        }catch(KOMETerritoryMesh.LimitExceeded unsuitable){ /* Bound work before expensive output growth. */ }
        rejectedComplete|=bit;completeRejects++;KOMEMapProfiler.count(KOMEMapProfiler.Count.COMPLETE_REJECT);return null;
    }
    long retainedTerritoryBytes(){return territoryFrames.reservedBytes();}
    long peakTerritoryBytes(){return territoryFrames.peakBytes();}
    int retainedTerritoryFrames(){return territoryFrames.size();}

    interface LayerSink { void quad(double x0,double y0,double x1,double y1,int argb,int priority); }

    private void drawTerritoriesUncached(KOMEMapViewport view, Map<Integer, String> owners, Map<Integer, Integer> colors, LayerSink sink) {
        visitEdges(view.mapX(view.left - TERRITORY_WIDTH) / mapPerCellX, view.mapY(view.top - TERRITORY_WIDTH) / mapPerCellY,
            view.mapX(view.right + TERRITORY_WIDTH) / mapPerCellX, view.mapY(view.bottom + TERRITORY_WIDTH) / mapPerCellY,
            (axis, line, start, end, a, b) -> {
                String ownerA = owners.get(a & 0xFFFFFF), ownerB = owners.get(b & 0xFFFFFF);
                if (java.util.Objects.equals(ownerA, ownerB)) return;
                double at = axis ? view.screenX(line * mapPerCellX) : view.screenY(line * mapPerCellY);
                double from = axis ? view.screenY(start * mapPerCellY) : view.screenX(start * mapPerCellX);
                double to = axis ? view.screenY(end * mapPerCellY) : view.screenX(end * mapPerCellX);
                if (ownerA != null) territoryStroke(view, axis, at, -1, from, to, ownerA, colors.get(a & 0xFFFFFF), owners, sink);
                if (ownerB != null) territoryStroke(view, axis, at, 1, from, to, ownerB, colors.get(b & 0xFFFFFF), owners, sink);
            });
    }

    /** Compact mesh and direct vertices survive pans/resizes inside the 64-GUI-pixel padded region. */
    static final class TerritoryFrame {
        final KOMEMapViewport view;
        final Map<Integer,String> owners;
        final Map<Integer,Integer> colors;
        final KOMETerritoryMesh mesh;
        final boolean completeMap;
        boolean streamOnly;
        TerritoryFrame(KOMEMapViewport view,Map<Integer,String> owners,Map<Integer,Integer> colors,KOMETerritoryMesh mesh) {
            this(view,owners,colors,mesh,false);
        }
        private TerritoryFrame(KOMEMapViewport view,Map<Integer,String> owners,Map<Integer,Integer> colors,KOMETerritoryMesh mesh,boolean completeMap) {
            this.view=view;this.owners=new HashMap<>(owners);this.colors=new HashMap<>(colors);this.mesh=mesh;
            this.completeMap=completeMap;
        }
        boolean matches(KOMEMapViewport v,Map<Integer,String> owners,Map<Integer,Integer> colors) {
            return view.zoom==v.zoom && covers(v) && contentMatches(owners,colors);
        }
        boolean covers(KOMEMapViewport v) {
            // Every owned stroke is clipped to the finite raster. No missing geometry can
            // be exposed by panning a complete-map mesh; exact settled zoom is checked by the cache.
            if(completeMap)return true;
            return v.mapX(v.left)>=view.mapX(view.left) && v.mapX(v.right)<=view.mapX(view.right)
                && v.mapY(v.top)>=view.mapY(view.top) && v.mapY(v.bottom)<=view.mapY(view.bottom);
        }
        boolean contentMatches(Map<Integer,String> owners,Map<Integer,Integer> colors) {
            return this.owners.equals(owners) && this.colors.equals(colors);
        }
        double clipLeft(KOMEMapViewport v){return covers(v)?v.left:Math.max(v.left,v.screenX(view.mapX(view.left)));}
        double clipTop(KOMEMapViewport v){return covers(v)?v.top:Math.max(v.top,v.screenY(view.mapY(view.top)));}
        double clipRight(KOMEMapViewport v){return covers(v)?v.right:Math.min(v.right,v.screenX(view.mapX(view.right)));}
        double clipBottom(KOMEMapViewport v){return covers(v)?v.bottom:Math.min(v.bottom,v.screenY(view.mapY(view.bottom)));}
        boolean intersects(KOMEMapViewport v){return clipRight(v)>clipLeft(v)&&clipBottom(v)>clipTop(v);}
    }

    private void territoryStroke(KOMEMapViewport v, boolean axis, double at, int direction,
            double from, double to, String owner, Integer rgb, Map<Integer, String> owners, LayerSink sink) {
        if (rgb == null) return;
        for (int band = 0; band < TERRITORY_ALPHA.length; band++) {
            double a = at + direction * band * TERRITORY_WIDTH / TERRITORY_ALPHA.length;
            double b = at + direction * (band + 1) * TERRITORY_WIDTH / TERRITORY_ALPHA.length;
            ownedStroke(v, axis, Math.min(a, b), Math.max(a, b), from, to,
                TERRITORY_ALPHA[band] << 24 | rgb, 4 - band, owner, owners, sink);
        }
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        // Dark native factions retain a narrow warm-light rim. No dark casing on light factions.
        if (299 * r + 587 * g + 114 * b < 90000) {
            ownedStroke(v, axis, Math.min(at, at + direction * 0.5), Math.max(at, at + direction * 0.5),
                from, to, rimColor(rgb), 5, owner, owners, sink);
        }
    }

    private static int rimColor(int rgb) {
        int result=0xFF000000;
        for(int shift=0;shift<=16;shift+=8) {
            int base=rgb>>shift&255,light=0xF4F0DC>>shift&255;
            result|=((base*(255-184)+light*184+127)/255)<<shift;
        }
        return result;
    }
    /** Clip wide strokes to cached assigned spans, including same-owner neighbouring tiles.
     * This permits a real GUI-width border at low zoom without painting across gaps/other owners. */
    private void ownedStroke(KOMEMapViewport v, boolean axis, double low, double high,
            double from, double to, int color, int priority, String owner, Map<Integer, String> owners, LayerSink sink) {
        double x0 = Math.max(v.left, axis ? low : from), x1 = Math.min(v.right, axis ? high : to);
        double y0 = Math.max(v.top, axis ? from : low), y1 = Math.min(v.bottom, axis ? to : high);
        if (x1 <= x0 || y1 <= y0) return;
        double minX = v.mapX(x0) / mapPerCellX, maxX = v.mapX(x1) / mapPerCellX;
        int firstY = (int) Math.max(0, Math.floor(v.mapY(y0) / mapPerCellY));
        int lastY = (int) Math.min(height - 1, Math.ceil(v.mapY(y1) / mapPerCellY) - 1);
        double pendingLeft = Double.NaN, pendingRight = 0, pendingTop = 0, pendingBottom = 0;
        for (int y = firstY; y <= lastY; y++) {
            for (int i = fills.first(y, minX); i < fills.offsets[y + 1]; i++) {
                int p = i * 4;
                if (fills.values[p] >= maxX) break;
                if (!owner.equals(owners.get(fills.values[p + 2] & 0xFFFFFF))) continue;
                int start = fills.values[p], end = fills.values[p + 1];
                while (i + 1 < fills.offsets[y + 1] && fills.values[(i + 1) * 4] == end
                        && owner.equals(owners.get(fills.values[(i + 1) * 4 + 2] & 0xFFFFFF))) {
                    i++; end = fills.values[i * 4 + 1];
                }
                double left = Math.max(x0, v.screenX(start * mapPerCellX)), right = Math.min(x1, v.screenX(end * mapPerCellX));
                double top = Math.max(y0, v.screenY(y * mapPerCellY)), bottom = Math.min(y1, v.screenY((y + 1) * mapPerCellY));
                if (right <= left || bottom <= top) continue;
                // Coalesce identical spans across raster rows: wide horizontal bands need not emit a quad per cell.
                if (pendingLeft == left && pendingRight == right && pendingBottom == top) pendingBottom = bottom;
                else {
                    if (!Double.isNaN(pendingLeft)) sink.quad(pendingLeft, pendingTop, pendingRight, pendingBottom, color, priority);
                    pendingLeft = left; pendingRight = right; pendingTop = top; pendingBottom = bottom;
                }
            }
        }
        if (!Double.isNaN(pendingLeft)) sink.quad(pendingLeft, pendingTop, pendingRight, pendingBottom, color, priority);
    }

    /** Persistent destination boundary, independent of pointer hover and route geometry. */
    void drawDestination(KOMEMapViewport view,int rgb,QuadSink sink) {
        if(rgb==0)return;
        visitEdges(view.mapX(view.left-3)/mapPerCellX,view.mapY(view.top-3)/mapPerCellY,
            view.mapX(view.right+3)/mapPerCellX,view.mapY(view.bottom+3)/mapPerCellY,
            (verticalAxis,line,start,end,a,b)->{
                if((a==0||(a&0xFFFFFF)!=rgb)&&(b==0||(b&0xFFFFFF)!=rgb))return;
                double at=verticalAxis?view.screenX(line*mapPerCellX):view.screenY(line*mapPerCellY);
                if(verticalAxis)clipped(view,at-1.5,view.screenY(start*mapPerCellY),at+1.5,view.screenY(end*mapPerCellY),0xFF69DDE8,sink);
                else clipped(view,view.screenX(start*mapPerCellX),at-1.5,view.screenX(end*mapPerCellX),at+1.5,0xFF69DDE8,sink);
            });
    }
    /** Hover fill uses visible cached spans, not a mask scan/upload on every tile change. */
    void drawHighlight(KOMEMapViewport view, int hoverRgb, QuadSink sink) {
        if (hoverRgb == 0) return;
        double minX = view.mapX(view.left) / mapPerCellX, maxX = view.mapX(view.right) / mapPerCellX;
        int firstY = (int) Math.max(0, Math.floor(view.mapY(view.top) / mapPerCellY));
        int lastY = (int) Math.min(height - 1, Math.floor(view.mapY(view.bottom) / mapPerCellY));
        for (int y = firstY; y <= lastY; y++) {
            for (int i = fills.first(y, minX); i < fills.offsets[y + 1]; i++) {
                int p = i * 4;
                if (fills.values[p] >= maxX) break;
                if ((fills.values[p + 2] & 0xFFFFFF) != hoverRgb) continue;
                clipped(view, view.screenX(fills.values[p] * mapPerCellX), view.screenY(y * mapPerCellY),
                    view.screenX(fills.values[p + 1] * mapPerCellX), view.screenY((y + 1) * mapPerCellY), 0x22FFF060, sink);
            }
        }
    }

    private static void clipped(KOMEMapViewport v, double x0, double y0, double x1, double y1, int color, QuadSink sink) {
        x0 = Math.max(x0, v.left); y0 = Math.max(y0, v.top);
        x1 = Math.min(x1, v.right); y1 = Math.min(y1, v.bottom);
        if (x1 > x0 && y1 > y0) sink.quad(x0, y0, x1, y1, color);
    }

    private static final class Runs {
        final int[] offsets, values;
        Runs(int[] offsets, int[] values) { this.offsets = offsets; this.values = values; }
        int count() { return values.length / 4; }
        long bytes() { return 4L * (offsets.length + values.length); }
        int first(int line, double minAlong) {
            int lo = offsets[line], hi = offsets[line + 1];
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (values[mid * 4 + 1] <= minAlong) lo = mid + 1; else hi = mid;
            }
            return lo;
        }
    }

    private static final class Builder {
        int[] values;
        int size;
        final int limit;
        Builder(int limit) { this.limit = limit; values = new int[Math.min(4096, limit * 4)]; }
        void add(int start, int end, int a, int b) {
            if (size / 4 >= limit) throw new IllegalArgumentException("Tile border cache exceeds " + MAX_RUNS + " runs");
            if (size + 4 > values.length) values = Arrays.copyOf(values, Math.min(limit * 4, Math.max(4, values.length * 2)));
            values[size++] = start; values[size++] = end; values[size++] = a; values[size++] = b;
        }
    }
}
