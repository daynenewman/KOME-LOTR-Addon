package kome.common.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Extensible registry of Trial of Knighthood definitions. */
public final class KOMESerfKnightTrial {
    private static final Map<String, KOMESerfKnightTrial> BY_ID = new HashMap<String, KOMESerfKnightTrial>();
    public final String id, displayName, description;
    public final int order;
    private KOMESerfKnightTrial(String id, String displayName, String description, int order) { this.id=id; this.displayName=displayName; this.description=description; this.order=order; }
    public static synchronized KOMESerfKnightTrial register(String id, String displayName, String description, int order) {
        if (id == null || !id.matches("[a-z0-9_]+") || displayName == null || displayName.trim().length() == 0 || description == null || description.trim().length() == 0 || order < 0) throw new IllegalArgumentException("Invalid trial definition");
        if (BY_ID.containsKey(id)) throw new IllegalStateException("Duplicate trial id: " + id);
        KOMESerfKnightTrial trial = new KOMESerfKnightTrial(id, displayName, description, order); BY_ID.put(id, trial); return trial;
    }
    public static KOMESerfKnightTrial forId(String id) { return id == null ? null : BY_ID.get(id); }
    public static List<KOMESerfKnightTrial> all() {
        List<KOMESerfKnightTrial> trials = new ArrayList<KOMESerfKnightTrial>(BY_ID.values());
        Collections.sort(trials, new java.util.Comparator<KOMESerfKnightTrial>() { public int compare(KOMESerfKnightTrial a, KOMESerfKnightTrial b) { int c=a.order-b.order; return c != 0 ? c : a.id.compareTo(b.id); } });
        return Collections.unmodifiableList(trials);
    }
    static { register("escort", "Escort", "Escort a faction NPC safely through dangerous territory.", 0); register("recovery", "Recovery", "Locate and recover a missing NPC, patrol, caravan, livestock group, or similar charge.", 1); register("defense", "Defense", "Accompany a patrol or defend a small location against a modest threat.", 2); }
}
