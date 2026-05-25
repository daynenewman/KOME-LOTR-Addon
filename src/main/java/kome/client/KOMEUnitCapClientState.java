package kome.client;

import java.util.HashMap;
import java.util.Map;

public class KOMEUnitCapClientState {
    private static final Map<Integer, Integer> caps = new HashMap<>();

    public static int getCap(int entityId) {
        Integer cap = caps.get(Integer.valueOf(entityId));
        return cap == null ? -1 : cap.intValue();
    }

    public static void setCap(int entityId, int cap) {
        caps.put(Integer.valueOf(entityId), Integer.valueOf(cap));
    }
}
