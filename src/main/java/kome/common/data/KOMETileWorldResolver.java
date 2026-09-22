package kome.common.data;

import lotr.common.LOTRDimension;
import lotr.common.world.genlayer.LOTRGenLayerWorld;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;

/** Canonical common-side service. Loading is explicit; lookup never initializes or changes world data. */
public final class KOMETileWorldResolver {
    public static final KOMETileWorldResolver INSTANCE = new KOMETileWorldResolver();
    static final String MASK = "assets/kome/map/reset_conquest_tile_ids.png";
    static final String MAPPING = "assets/kome/map/reset_conquest_tile_ids.txt";
    private volatile State state = new State(null, "Tile raster has not been initialized");

    // Only INSTANCE is constructed by production code; package access supports isolated service tests.
    KOMETileWorldResolver() { }

    public KOMETileResolution resolve(int dimension, int worldX, int worldZ) {
        State captured = state;
        return resolve(captured, dimension, worldX, worldZ);
    }

    private static KOMETileResolution resolve(State captured, int dimension, int x, int z) {
        return captured.snapshot == null
            ? KOMETileResolution.unavailable(KOMETileResolution.Status.INVALID_SNAPSHOT, dimension, x, z, captured.diagnostic)
            : captured.snapshot.resolve(dimension, x, z);
    }

    /** Existing entity/packet doubles are floored once to their integer block, without rewriting stored positions. */
    public KOMETileResolution resolveWorldPosition(int dimension, double x, double z) {
        State captured = state;
        try {
            return resolve(captured, dimension, block(x), block(z));
        } catch (IllegalArgumentException | ArithmeticException e) {
            return invalidCoordinate(dimension, e);
        }
    }

    /** UI/LOTR map-position compatibility boundary. All world-to-mask decisions remain in the snapshot. */
    public KOMETileResolution resolveMapPosition(int dimension, double mapX, double mapY) {
        State captured = state;
        if (captured.snapshot == null) {
            // Without a transform, a supplied map point has no known world coordinate.
            return new KOMETileResolution(KOMETileResolution.Status.INVALID_SNAPSHOT, dimension,
                0, 0, false, 0L, 0L, false, "", captured.diagnostic);
        }
        KOMETileRasterSnapshot.Transform transform = captured.snapshot.transform;
        try {
            int x = decimal(mapX).subtract(BigDecimal.valueOf(transform.originX))
                .multiply(BigDecimal.valueOf(transform.scale)).setScale(0, RoundingMode.FLOOR).intValueExact();
            int z = decimal(mapY).subtract(BigDecimal.valueOf(transform.originZ))
                .multiply(BigDecimal.valueOf(transform.scale)).setScale(0, RoundingMode.FLOOR).intValueExact();
            return resolve(captured, dimension, x, z);
        } catch (IllegalArgumentException | ArithmeticException e) {
            return invalidCoordinate(dimension, e);
        }
    }

    private static BigDecimal decimal(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) throw new IllegalArgumentException("Coordinate must be finite");
        return new BigDecimal(value); // Preserve the exact supplied binary value; no intermediate floating-point transform.
    }

    private static int block(double value) {
        return decimal(value).setScale(0, RoundingMode.FLOOR).intValueExact();
    }

    private static KOMETileResolution invalidCoordinate(int dimension, RuntimeException e) {
        return KOMETileResolution.unavailable(KOMETileResolution.Status.INVALID_COORDINATE, dimension, 0, 0,
            "Coordinate is not a supported integer block position: " + e.getMessage());
    }

    public Optional<KOMETileRasterSnapshot> snapshot() { return Optional.ofNullable(state.snapshot); }
    public String loadDiagnostic() { return state.diagnostic; }

    /** Explicit future invalidation; callers must load a new validated snapshot before activation. */
    public synchronized void invalidate() { state = new State(null, "Tile raster explicitly invalidated; reload required"); }

    /** Candidate construction completes before the single volatile publication. Readers never take this lock. */
    synchronized boolean reload(InputStream image, InputStream mapping, KOMETileRasterSnapshot.Transform transform,
            Set<String> knownIds, Set<String> retiredIds) {
        try {
            publish(KOMETileRasterSnapshot.load(image, mapping, transform, knownIds, retiredIds));
            return true;
        } catch (IOException | IllegalArgumentException | ArithmeticException | IllegalStateException e) {
            state = new State(state.snapshot, "Tile raster load rejected: " + e.getMessage());
            return false;
        }
    }

    synchronized void publish(KOMETileRasterSnapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Cannot publish a null tile raster");
        state = new State(snapshot, "Validated tile raster " + snapshot.width + "x" + snapshot.height);
    }

    /** Bundled common resources only: resource packs cannot redefine server tile geometry. */
    public synchronized boolean reloadBundled() {
        ClassLoader loader = KOMETileWorldResolver.class.getClassLoader();
        try (InputStream map = LOTRGenLayerWorld.class.getClassLoader().getResourceAsStream("assets/lotr/map/map.png");
                InputStream mask = loader.getResourceAsStream(MASK);
                InputStream mapping = loader.getResourceAsStream(MAPPING)) {
            int[] dimensions = mapDimensions(map);
            if ((LOTRGenLayerWorld.imageWidth > 0 && LOTRGenLayerWorld.imageWidth != dimensions[0])
                    || (LOTRGenLayerWorld.imageHeight > 0 && LOTRGenLayerWorld.imageHeight != dimensions[1])) {
                throw new IOException("Loaded LOTR map dimensions disagree with its packaged map");
            }
            KOMETileRasterSnapshot.Transform transform = new KOMETileRasterSnapshot.Transform(
                LOTRDimension.MIDDLE_EARTH.dimensionID, LOTRGenLayerWorld.originX, LOTRGenLayerWorld.originZ,
                LOTRGenLayerWorld.scale, dimensions[0], dimensions[1]);
            return reload(mask, mapping, transform, KOMEConquestTileDefaults.getKnownTileIds(),
                KOMEConquestTileDefaults.getRetiredTileIds());
        } catch (IOException | IllegalArgumentException | ArithmeticException | IllegalStateException e) {
            state = new State(state.snapshot, "Tile raster load rejected: " + e.getMessage());
            return false;
        }
    }

    // Read only the same map's header, not LOTR biome generation or a WorldData initialization path.
    private static int[] mapDimensions(InputStream stream) throws IOException {
        if (stream == null) throw new IOException("Missing authoritative LOTR map resource");
        try (ImageInputStream input = new MemoryCacheImageInputStream(stream)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("Invalid authoritative LOTR map image");
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                KOMETileRasterSnapshot.validateDimensions(width, height);
                return new int[] {width, height};
            } finally { reader.dispose(); }
        }
    }

    private static final class State {
        final KOMETileRasterSnapshot snapshot;
        final String diagnostic;
        State(KOMETileRasterSnapshot snapshot, String diagnostic) {
            this.snapshot = snapshot;
            this.diagnostic = diagnostic;
        }
    }
}
