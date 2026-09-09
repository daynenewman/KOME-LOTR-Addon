package com.fuzs.aquaacrobatics.client.model;

/**
 * Marks the single ModelBiped#setRotationAngles call used to render the local first-person arm.
 */
public final class FirstPersonArmRenderContext {

    private static final ThreadLocal<Integer> DEPTH = new ThreadLocal<>();

    private FirstPersonArmRenderContext() {}

    public static void push() {
        Integer depth = DEPTH.get();
        DEPTH.set(depth == null ? 1 : depth + 1);
    }

    public static void pop() {
        Integer depth = DEPTH.get();
        if (depth == null || depth <= 1) {
            DEPTH.remove();
        } else {
            DEPTH.set(depth - 1);
        }
    }

    public static boolean isActive() {
        Integer depth = DEPTH.get();
        return depth != null && depth > 0;
    }
}
