package kome.common.data;

import java.util.Arrays;
import java.util.Collections;
import kome.common.siege.KOMEDefensiveGateRef;
import kome.common.siege.KOMENormalSegment;
import kome.common.siege.KOMESiegeAreaRef;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeConnection;
import kome.common.siege.KOMETransitionZone;
import kome.common.siege.KOMEWallZone;
import kome.common.siege.geometry.KOMEPolygon;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEXZPoint;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.KOMETacticalConfigurationCodec;
import net.minecraft.nbt.NBTTagCompound;

/** Persistence fixtures installed through root NBT, without a tactical mutation service or live World. */
final class KOMETacticalWorldDataFixtures {
    private KOMETacticalWorldDataFixtures() {}

    static NBTTagCompound section() {
        KOMETacticalConfiguration configuration = new KOMETacticalConfiguration();
        configuration.addComplex(complex("WEST", "FIELD", 0, 7));
        configuration.addComplex(complex("EAST", "UNRESOLVED", 100, 19));
        configuration.addForceDeploymentArea(area("FIELD", "T277", -1, 11));
        configuration.addForceDeploymentArea(area("SECOND_FIELD", "T277", -1, 12));
        configuration.addForceDeploymentArea(area("OPEN_FIELD", "T278", 0, 13));
        configuration.assignBuild("B1", "WEST");
        configuration.assignBuild("B2", "EAST");
        configuration.assignBuild("B-MISSING", "WEST");
        NBTTagCompound section = KOMETacticalConfigurationCodec.encode(configuration);
        section.setLong("Revision", 731L);
        return section;
    }

    static KOMESiegeComplex complex(String id, String preferred, int offset, long revision) {
        return new KOMESiegeComplex(id, "T277", -1, revision,
            Arrays.asList(new KOMENormalSegment("a", "Outer", prism(offset)), new KOMENormalSegment("inside", "Inner", prism(offset + 12))),
            Collections.singletonList(new KOMEWallZone("wall", "Wall", prism(offset + 30), Arrays.asList("inside", "a"))),
            Arrays.asList(new KOMETransitionZone("gate", "Gate", prism(offset - 12)), new KOMETransitionZone("link", "Link", prism(offset + 11))),
            preferred, Arrays.asList(KOMESiegeConnection.gated("ENTRY", KOMESiegeAreaRef.exterior(), KOMESiegeAreaRef.normal("a"),
                "gate", new KOMEDefensiveGateRef("B-MISSING", "gone-gate")),
                KOMESiegeConnection.gateLess("LINK", KOMESiegeAreaRef.normal("a"), KOMESiegeAreaRef.normal("inside"), "link")));
    }

    static KOMEForceDeploymentArea area(String id, String tile, int dimension, long revision) {
        return new KOMEForceDeploymentArea(id, tile, dimension, "Staging", prism(-30), revision);
    }

    static KOMEPolygonPrism prism(int offset) {
        return new KOMEPolygonPrism(KOMEPolygon.of(new KOMEXZPoint(offset, 0), new KOMEXZPoint(offset + 10, 0),
            new KOMEXZPoint(offset + 10, 10), new KOMEXZPoint(offset, 10)), -10, 100);
    }
}
