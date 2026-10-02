package kome.client;

import java.util.Arrays;
import kome.common.data.KOMETileRasterSnapshot;

/** Headless CPU geometry measurements; never calls GL or claims FPS. */
public final class KOMEMapGeometryMeasurement {
    private static volatile double sink;
    public static void measure(KOMETileRasterSnapshot s) {
        int[] pixels = s.copyArgbPixels(); KOMEMapBorders b = null; double[] load = new double[5];
        for (int i = 0; i < load.length; i++) {
            long start = System.nanoTime(); b = new KOMEMapBorders.Cache().get(s, pixels); load[i] = (System.nanoTime()-start)/1000000D;
        }
        report("borderExtractionMs", load);
        System.out.println("BORDER_CACHE primitiveBytes=" + b.primitiveBytes() + " edgeRuns=" + b.edgeRuns() + " fillRuns=" + b.fillRuns());
        int hover = s.colorsById().get("T401");
        for (int[] gui : new int[][] {{854,480}, {1920,1080}}) {
            for (double zoom : new double[] {0.15, 1, 8}) {
                double[] times = new double[5]; long quads = 0;
                final double[] sum = {0}; final long[] count = {0};
                KOMEMapBorders.QuadSink collect = (x0,y0,x1,y1,c) -> { sum[0] += x0 + y1; count[0]++; };
                for (int trial = -1; trial < times.length; trial++) {
                    long start = System.nanoTime(); count[0] = 0;
                    for (int frame = 0; frame < 200; frame++) {
                        KOMEMapViewport view = new KOMEMapViewport(0,0,gui[0],gui[1],2663 + (frame % 20),1412 + (frame % 20),zoom);
                        b.drawBorders(view, hover, collect); b.drawHighlight(view, hover, collect);
                    }
                    if (trial >= 0) times[trial] = (System.nanoTime()-start)/(200D*1000000D);
                    quads = count[0];
                }
                sink = sum[0]; report("headlessDrawMs gui="+gui[0]+"x"+gui[1]+" zoom="+zoom, times);
                System.out.println("DRAW quadsPerFrameAvg="+quads/200D+" excludesGL=true excludesFPS=true");
            }
        }
    }
    private static void report(String label, double[] values) {
        double[] sorted = values.clone(); Arrays.sort(sorted);
        System.out.println("MEASURE "+label+" trials="+Arrays.toString(values)+" median="+sorted[2]+" min="+sorted[0]+" max="+sorted[4]);
    }
}
