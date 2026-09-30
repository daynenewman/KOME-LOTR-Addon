package kome.client;

import java.util.Arrays;
import kome.common.data.KOMETileRasterSnapshot;

/** Derived client presentation only. No ownership, world state, GL objects, or alternate lookup. */
final class KOMEMapBorders {
    static final int MAX_RUNS = 1_000_000;
    static final int BORDER_COLOR = 0xDD493922;
    static final int HOVER_COLOR = 0xEEFFE04A;
    private final Runs horizontal, vertical, fills;
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
        void clear() { source = null; geometry = null; }
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
        double sx = view.zoom * mapPerCellX, sy = view.zoom * mapPerCellY;
        visitEdges(view.mapX(view.left - 1.5) / mapPerCellX, view.mapY(view.top - 1.5) / mapPerCellY,
            view.mapX(view.right + 1.5) / mapPerCellX, view.mapY(view.bottom + 1.5) / mapPerCellY,
            (verticalAxis, line, start, end, a, b) -> {
                boolean hover = hoverRgb != 0 && ((a != 0 && (a & 0xFFFFFF) == hoverRgb)
                    || (b != 0 && (b & 0xFFFFFF) == hoverRgb));
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
