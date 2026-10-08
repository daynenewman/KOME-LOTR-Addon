package com.lotrcharactercreation.client.render;

/** Cursor offsets are cursor minus preview head centre, in screen coordinates. */
public final class AppearancePreviewOrientation {
    public final float bodyYaw,headYaw,headPitch;
    public static float headCentreY(float bottomY,float scale,float raceScale) {
        // Renderers translate the model by 24 units; the head centre is another 4 units above.
        return bottomY-scale*0.9375F*raceScale*(28F/16F+0.0078125F);
    }
    public AppearancePreviewOrientation(float x,float y) {
        // The model is mirrored on X and rotated 180 degrees. Match inventory yaw proportions.
        float horizontal=(float)Math.atan(x/40F);
        bodyYaw=-horizontal*20F;
        headYaw=-horizontal*20F; // relative head yaw: total yaw is 40 * atan.
        headPitch=(float)Math.atan(y/40F)*20F;
    }
}
