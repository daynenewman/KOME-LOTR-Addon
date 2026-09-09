package com.enovak.lotrmoremobs.siege.gate;

public final class GateAnimation {

    public static final int OPENING_DURATION_TICKS = 40;
    public static final int CLOSING_DURATION_TICKS = 40;
    public static final int AUTO_CLOSE_DELAY_TICKS = 200;
    public static final float FULL_OPEN_ANGLE_DEGREES = 90.0F;

    private GateAnimation() {
    }

    public static float getSmoothedProgress(float progress) {
        float clamped = Math.max(0.0F, Math.min(progress, 1.0F));
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }

    public static float getLeafAngleDegrees(
            GateOrientation orientation,
            GateOpeningDirection openingDirection,
            GateHinge hinge,
            float openProgress
    ) {
        if (orientation == null
                || openingDirection == null
                || hinge == null
                || hinge.getSide() == null) {
            return 0.0F;
        }
        return FULL_OPEN_ANGLE_DEGREES
                * Math.max(0.0F, Math.min(openProgress, 1.0F))
                * orientation.getForwardRotationSign(hinge.getSide())
                * openingDirection.getRotationMultiplier();
    }
}
