package kome.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import kome.common.data.KOMEVisualMarker;

/** Current player's server-authored visual projection; never mutates progression. */
public final class KOMEVisualMarkerClientState {
    private static List<KOMEVisualMarker> markers = Collections.emptyList();
    private KOMEVisualMarkerClientState() { }

    public static void update(List<KOMEVisualMarker> values) {
        markers = values == null ? Collections.<KOMEVisualMarker>emptyList()
            : Collections.unmodifiableList(new ArrayList<KOMEVisualMarker>(values));
    }
    public static List<KOMEVisualMarker> markers() { return markers; }
    public static void clear() { markers = Collections.emptyList(); }
}
