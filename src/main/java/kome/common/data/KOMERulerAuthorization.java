package kome.common.data;

import java.util.UUID;

/** Canonical authorization boundary for actions reserved for a recognized political ruler. */
public final class KOMERulerAuthorization {
    private KOMERulerAuthorization() {
    }

    public static boolean canActAsRuler(KOMEWorldData data, String faction, UUID actor) {
        return data != null && KOMERulerService.isRuler(data, faction, actor);
    }
}
