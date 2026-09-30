package kome.common.data;

import org.junit.rules.ExternalResource;
import java.util.Optional;

/** Test-only scoped use of real packaged geometry; never replaces a production tile registry. */
public final class KOMETileTestResources extends ExternalResource {
    private static KOMETileRasterSnapshot real;
    private static int t100X, t100Z;
    private Optional<KOMETileRasterSnapshot> previous;

    public static synchronized KOMETileRasterSnapshot real() {
        if (real == null) {
            KOMETileWorldResolver loader = new KOMETileWorldResolver();
            if (!loader.reloadBundled()) throw new AssertionError(loader.loadDiagnostic());
            real = loader.snapshot().get();
            int color = real.colorsById().get("T100");
            int[] pixels = real.copyArgbPixels();
            boolean found = false;
            for (int i = 0; i < pixels.length; i++) {
                if ((pixels[i] >>> 24) > 24 && (pixels[i] & 0xFFFFFF) == color) {
                    t100X = worldX(i % real.width);
                    t100Z = worldZ(i / real.width);
                    if (!"T100".equals(real.resolve(dimension(), t100X, t100Z).tileId)) throw new AssertionError("Fixture control point");
                    found = true;
                    break;
                }
            }
            if (!found) throw new AssertionError("Real T100 fixture is missing");
        }
        return real;
    }

    public static int dimension() { return real().transform.dimension; }
    public static int x() { real(); return t100X; }
    public static int z() { real(); return t100Z; }
    public static int worldX(int pixel) {
        KOMETileRasterSnapshot s = real();
        return Math.toIntExact((long) pixel * s.transform.scale * s.transform.mapWidth / s.width
            - (long) s.transform.originX * s.transform.scale);
    }
    public static int worldZ(int pixel) {
        KOMETileRasterSnapshot s = real();
        return Math.toIntExact((long) pixel * s.transform.scale * s.transform.mapHeight / s.height
            - (long) s.transform.originZ * s.transform.scale);
    }

    @Override protected void before() {
        previous = KOMETileWorldResolver.INSTANCE.snapshot();
        KOMETileWorldResolver.INSTANCE.publish(real());
    }

    @Override protected void after() {
        if (previous.isPresent()) KOMETileWorldResolver.INSTANCE.publish(previous.get());
        else KOMETileWorldResolver.INSTANCE.invalidate();
    }
}
