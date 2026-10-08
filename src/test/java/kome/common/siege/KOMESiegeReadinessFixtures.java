package kome.common.siege;

import java.util.Arrays;
import java.util.Collections;
import kome.common.siege.geometry.KOMEPolygonPrism;

/** Shared authoring fixtures with actual valid corridors; no live siege state or world records. */
public final class KOMESiegeReadinessFixtures {
    private KOMESiegeReadinessFixtures() { }
    public static KOMEPolygonPrism prism(int x1, int z1, int x2, int z2) {
        return KOMESiegeFixtures.prism(x1, z1, x2, z2, 0, 10);
    }
    public static KOMESiegeComplex minimal(String id, String tile, int dimension, KOMEDefensiveGateRef gate) {
        KOMESiegeConnection entry = new KOMESiegeConnection("ENTRY", KOMESiegeAreaRef.exterior(),
            KOMESiegeAreaRef.normal("A"), "T_ENTRY", gate);
        return new KOMESiegeComplex(id, tile, dimension, 17L,
            Collections.singletonList(new KOMENormalSegment("A", "Courtyard", prism(0, 0, 10, 10))),
            Collections.emptyList(), Collections.singletonList(new KOMETransitionZone("T_ENTRY", "Entry", prism(-2, 2, 0, 4))),
            null, Collections.singletonList(entry));
    }
    public static KOMESiegeComplex empty(String id, String tile, int dimension) {
        return new KOMESiegeComplex(id, tile, dimension, 19L, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), null, Collections.emptyList());
    }
    public static KOMESiegeComplex preferred(KOMESiegeComplex complex, String areaId) {
        return new KOMESiegeComplex(complex.getComplexId(), complex.getTileId(), complex.getDimensionId(), complex.getRevision(),
            complex.getNormalSegments(), complex.getWallZones(), complex.getTransitionZones(), areaId, complex.getConnections());
    }
    public static KOMESiegeComplex branching() {
        KOMESiegeComplex base = minimal("FORT", "T100", 0, null);
        return new KOMESiegeComplex("FORT", "T100", 0, 17L,
            Arrays.asList(base.getNormalSegments().get(0), new KOMENormalSegment("B", "B", prism(12, 0, 22, 10)),
                new KOMENormalSegment("C", "C", prism(0, 12, 10, 22))), Collections.emptyList(),
            Arrays.asList(base.getTransitionZones().get(0), new KOMETransitionZone("T_AB", "AB", prism(10, 2, 12, 4)),
                new KOMETransitionZone("T_AC", "AC", prism(2, 10, 4, 12))), null,
            Arrays.asList(base.getConnections().get(0), KOMESiegeConnection.gateLess("AB", KOMESiegeAreaRef.normal("A"),
                KOMESiegeAreaRef.normal("B"), "T_AB"), KOMESiegeConnection.gateLess("AC", KOMESiegeAreaRef.normal("C"),
                KOMESiegeAreaRef.normal("A"), "T_AC")));
    }
}
