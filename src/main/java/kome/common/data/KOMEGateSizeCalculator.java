package kome.common.data;

/** Pure deterministic V1 gate-opening size policy. */
public final class KOMEGateSizeCalculator {
    public static final int BASELINE_WIDTH = 5;
    public static final int BASELINE_HEIGHT = 5;
    public static final double BASELINE_AREA = 25.0D;
    public static final int MAXIMUM_TARGET_WIDTH = 12;
    public static final int MAXIMUM_TARGET_HEIGHT = 12;
    public static final double MAXIMUM_TARGET_AREA = 144.0D;
    public static final double MAXIMUM_MULTIPLIER = 1.5D;
    public static final double LARGE_GATE_EXPONENT = 0.75D;

    private KOMEGateSizeCalculator() {
    }

    public static double multiplier(int width, int height) {
        return multiplier(width, height, Parameters.defaults());
    }

    public static double multiplier(int width, int height, Parameters parameters) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Gate opening width and height must be positive.");
        }
        if (parameters == null) {
            throw new IllegalArgumentException("Gate size parameters are required.");
        }

        double area = (double) width * (double) height;
        double baselineArea = (double) parameters.baselineWidth * parameters.baselineHeight;
        if (width < parameters.baselineWidth || height < parameters.baselineHeight) {
            return Math.min(area / baselineArea,
                Math.min(width / (double) parameters.baselineWidth,
                    height / (double) parameters.baselineHeight));
        }

        double fullBonusArea = (double) parameters.fullBonusWidth * parameters.fullBonusHeight;
        double areaProgress = clamp((area - baselineArea) / (fullBonusArea - baselineArea));
        double widthProgress = clamp((width - parameters.baselineWidth)
            / (double) (parameters.fullBonusWidth - parameters.baselineWidth));
        double heightProgress = clamp((height - parameters.baselineHeight)
            / (double) (parameters.fullBonusHeight - parameters.baselineHeight));
        double progress = Math.min(areaProgress, Math.min(widthProgress, heightProgress));
        double multiplier = 1.0D + (parameters.maxSizeMultiplier - 1.0D)
            * Math.pow(progress, parameters.curveExponent);
        return Math.min(parameters.maxSizeMultiplier, multiplier);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }

    /** Immutable validated balance inputs. Production callers obtain these from KOME config. */
    public static final class Parameters {
        private final int baselineWidth;
        private final int baselineHeight;
        private final int fullBonusWidth;
        private final int fullBonusHeight;
        private final double maxSizeMultiplier;
        private final double curveExponent;

        public Parameters(int baselineWidth, int baselineHeight, int fullBonusWidth,
                int fullBonusHeight, double maxSizeMultiplier, double curveExponent) {
            if (baselineWidth <= 0 || baselineHeight <= 0) {
                throw new IllegalArgumentException("Gate baseline dimensions must be positive.");
            }
            if (fullBonusWidth <= baselineWidth || fullBonusHeight <= baselineHeight) {
                throw new IllegalArgumentException("Gate full-bonus dimensions must exceed the baseline.");
            }
            if (!finite(maxSizeMultiplier) || maxSizeMultiplier < 1.0D) {
                throw new IllegalArgumentException("Gate maximum size multiplier must be finite and at least 1.0.");
            }
            if (!finite(curveExponent) || curveExponent <= 0.0D) {
                throw new IllegalArgumentException("Gate size curve exponent must be finite and positive.");
            }
            this.baselineWidth = baselineWidth;
            this.baselineHeight = baselineHeight;
            this.fullBonusWidth = fullBonusWidth;
            this.fullBonusHeight = fullBonusHeight;
            this.maxSizeMultiplier = maxSizeMultiplier;
            this.curveExponent = curveExponent;
        }

        public static Parameters defaults() {
            return new Parameters(BASELINE_WIDTH, BASELINE_HEIGHT, MAXIMUM_TARGET_WIDTH,
                MAXIMUM_TARGET_HEIGHT, MAXIMUM_MULTIPLIER, LARGE_GATE_EXPONENT);
        }

        public int getBaselineWidth() { return baselineWidth; }
        public int getBaselineHeight() { return baselineHeight; }
        public int getFullBonusWidth() { return fullBonusWidth; }
        public int getFullBonusHeight() { return fullBonusHeight; }
        public double getMaxSizeMultiplier() { return maxSizeMultiplier; }
        public double getCurveExponent() { return curveExponent; }

        private static boolean finite(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value);
        }
    }
}
