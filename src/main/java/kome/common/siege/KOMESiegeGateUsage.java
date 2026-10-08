package kome.common.siege;

import java.util.*;
import kome.common.siege.validation.*;
import kome.common.tactical.KOMETacticalIds;

/** Pure complex-local logical gate cardinality. Historical invalid authoring remains representable. */
public final class KOMESiegeGateUsage {
    private KOMESiegeGateUsage() { }
    private static KOMEDefensiveGateRef key(KOMEDefensiveGateRef ref) {
        return new KOMEDefensiveGateRef(KOMETacticalIds.buildLookup(ref.getBuildId()), ref.getGateRecordId());
    }
    public static boolean sameGate(KOMEDefensiveGateRef a, KOMEDefensiveGateRef b) {
        return a == null ? b == null : b != null && key(a).equals(key(b));
    }
    public static List<String> otherConnectionIds(KOMESiegeComplex complex, String connectionId, KOMEDefensiveGateRef ref) {
        SortedSet<String> ids = new TreeSet<>();
        for (KOMESiegeConnection c : complex.getConnections()) if (!c.getId().equals(connectionId)
                && c.isGated() && sameGate(c.getGateRef().get(), ref)) ids.add(c.getId());
        return Collections.unmodifiableList(new ArrayList<>(ids));
    }
    private static Map<KOMEDefensiveGateRef,List<String>> usage(KOMESiegeComplex complex) {
        Map<KOMEDefensiveGateRef,List<String>> result = new TreeMap<>(Comparator.comparing(KOMEDefensiveGateRef::getBuildId)
            .thenComparing(KOMEDefensiveGateRef::getGateRecordId));
        if (complex != null) for (KOMESiegeConnection c : complex.getConnections()) if (c.isGated())
            result.computeIfAbsent(key(c.getGateRef().get()), ignored -> new ArrayList<>()).add(c.getId());
        for (List<String> ids : result.values()) Collections.sort(ids);
        return result;
    }
    public static KOMEValidationResult validate(KOMESiegeComplex complex) {
        List<KOMEValidationIssue> issues = new ArrayList<>();
        for (Map.Entry<KOMEDefensiveGateRef,List<String>> entry : usage(complex).entrySet()) if (entry.getValue().size() > 1) {
            List<String> subjects = new ArrayList<>(entry.getValue()); subjects.add(complex.getComplexId());
            subjects.add(entry.getKey().getBuildId()); subjects.add(entry.getKey().getGateRecordId());
            issues.add(new KOMEValidationIssue(KOMEValidationSeverity.ERROR, KOMEValidationCode.CONNECTION_GATE_REUSED,
                describe(complex, entry.getKey(), entry.getValue()), subjects.toArray(new String[0])));
        }
        return new KOMEValidationResult(issues);
    }
    /** Reject newly attached duplicate pairs, but allow unchanged/subset historical conflicts to be inspected or repaired. */
    public static void requireNoNewConflicts(KOMESiegeComplex original, KOMESiegeComplex draft) {
        Map<KOMEDefensiveGateRef,List<String>> before = usage(original);
        for (Map.Entry<KOMEDefensiveGateRef,List<String>> entry : usage(draft).entrySet()) if (entry.getValue().size() > 1) {
            List<String> remaining = new ArrayList<>(before.getOrDefault(entry.getKey(), Collections.emptyList()));
            for (String id : entry.getValue()) if (!remaining.remove(id))
                throw new IllegalArgumentException(describe(draft, entry.getKey(), entry.getValue()));
        }
    }
    private static String describe(KOMESiegeComplex complex, KOMEDefensiveGateRef gate, List<String> connections) {
        return "Complex " + complex.getComplexId() + ": logical gate " + gate.getBuildId() + " / " + gate.getGateRecordId()
            + " is used by Connections " + connections + ". One logical gate may guard only one Connection in this complex.";
    }
}
