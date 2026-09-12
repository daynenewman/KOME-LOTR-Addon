package kome.common.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Stateless policy for applying a validated effective-configuration change. */
public final class KOMEConfigChangeGuard {
    private KOMEConfigChangeGuard() { }
    public static ChangeDecision evaluate(KOMEConfigChangeSet changes,
            KOMEConfigRegistry.RuntimeActivity activity) {
        if (changes.isEmpty()) return ChangeDecision.allowed();
        if (activity.isDailyTransactionInProgress()) return ChangeDecision.deferred(
                "An in-progress daily transaction must use one coherent configuration snapshot",
                Collections.<String>emptyList());
        List<String> blocking = new ArrayList<String>();
        if (activity.isActiveSiegeInProgress()) {
            Collection<String> locked = activity.getActiveSiegeLockedConfigKeys();
            for (KOMEConfigChangeSet.Entry entry : changes.getEntries()) {
                if (locked != null && locked.contains(entry.getCanonicalKey())) blocking.add(entry.getCanonicalKey());
            }
        }
        if (!blocking.isEmpty()) return ChangeDecision.deferred(
                "Active Siege has locked the changed configuration keys", blocking);
        return ChangeDecision.allowed();
    }
}
