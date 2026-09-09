package com.enovak.lotrmoremobs.render;

/**
 * Narrow render-call context for Mumak previews. The default is always the
 * ordinary world path; callers that own a special preview entry point must
 * push and restore their context in a finally block.
 */
public final class MumakilRenderContext {
    public enum Type {
        WORLD,
        HORSE_INVENTORY,
        HIRING_PREVIEW,
        SPAWN_CAGE
    }

    private static final ThreadLocal<Type> ACTIVE =
            new ThreadLocal<Type>() {
                @Override
                protected Type initialValue() {
                    return Type.WORLD;
                }
            };

    private MumakilRenderContext() {
    }

    public static Type get() {
        return ACTIVE.get();
    }

    public static Type push(Type context) {
        Type previous = ACTIVE.get();
        ACTIVE.set(context == null ? Type.WORLD : context);
        return previous;
    }

    public static void restore(Type previous) {
        ACTIVE.set(previous == null ? Type.WORLD : previous);
    }
}
