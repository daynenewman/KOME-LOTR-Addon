package kome.common.data;

/** Pure deterministic V1 gate-opening size policy. */
public final class KOMEGateSizeCalculator {
    public static final int BASELINE_WIDTH = 3;
    public static final int BASELINE_HEIGHT = 4;
    public static final double BASELINE_AREA = 12.0D;
    public static final int MAXIMUM_TARGET_WIDTH = 9;
    public static final int MAXIMUM_TARGET_HEIGHT = 11;
    public static final double MAXIMUM_TARGET_AREA = 99.0D;
    public static final double MAXIMUM_MULTIPLIER = 2.0D;
    private static final double LARGE_GATE_EXPONENT = 0.75D;

    private KOMEGateSizeCalculator() {
    }

    public static double multiplier(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Gate opening width and height must be positive.");
        }

        double area = (double) width * (double) height;
        if (width < BASELINE_WIDTH || height < BASELINE_HEIGHT) {
            return Math.min(area / BASELINE_AREA,
                Math.min(width / (double) BASELINE_WIDTH, height / (double) BASELINE_HEIGHT));
        }

        double areaProgress = clamp((area - BASELINE_AREA) / (MAXIMUM_TARGET_AREA - BASELINE_AREA));
        double widthProgress = clamp((width - BASELINE_WIDTH)
            / (double) (MAXIMUM_TARGET_WIDTH - BASELINE_WIDTH));
        double heightProgress = clamp((height - BASELINE_HEIGHT)
            / (double) (MAXIMUM_TARGET_HEIGHT - BASELINE_HEIGHT));
        double progress = Math.min(areaProgress, Math.min(widthProgress, heightProgress));
        return Math.min(MAXIMUM_MULTIPLIER, 1.0D + Math.pow(progress, LARGE_GATE_EXPONENT));
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
