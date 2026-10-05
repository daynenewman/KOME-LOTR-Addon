package kome.common.data;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import kome.common.siege.KOMEDefensiveGateRef;
import kome.common.siege.KOMESiegeAreaRef;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeConnection;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.KOMETacticalConfigurationCodec;
import net.minecraft.nbt.NBTTagCompound;
import static org.junit.Assert.*;

/** Real root-load and KOM-10 link fixtures; no mutable tactical authority is exposed to tests. */
final class KOMETacticalMembershipFixtures {
    private KOMETacticalMembershipFixtures() { }

    static KOMEWorldData world() { return install(new KOMEWorldData("test")); }

    static <T extends KOMEWorldData> T install(T destination) {
        KOMEWorldData source = new KOMEWorldData("source");
        source.initializeIntegratedWorld();
        addBuild(source, "B1");
        addBuild(source, "B2");
        KOMETacticalConfiguration configuration = new KOMETacticalConfiguration();
        configuration.addComplex(complex("A", "T100", KOMETileTestResources.dimension(),
            gate("Z_ENTRY", "b1", "G1"), gate("ENTRY", "B1", "G1")));
        configuration.addComplex(complex("B", "T100", KOMETileTestResources.dimension(), gate("ENTRY", "B1", "G1")));
        configuration.addComplex(complex("OTHER_TILE", "T101", KOMETileTestResources.dimension()));
        configuration.addComplex(complex("OTHER_DIMENSION", "T100", KOMETileTestResources.dimension() + 1));
        installConfiguration(source, destination, configuration);
        destination.setDirty(false);
        return destination;
    }

    static void installConfiguration(KOMEWorldData source, KOMEWorldData destination, KOMETacticalConfiguration configuration) {
        NBTTagCompound root = save(source);
        root.setTag("TacticalConfiguration", KOMETacticalConfigurationCodec.encode(configuration));
        destination.readFromNBT(root);
    }

    static KOMEPlayerBuild addBuild(KOMEWorldData data, String id) {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = id; build.displayName = id; build.tileId = "T100";
        build.dimension = KOMETileTestResources.dimension();
        build.x = KOMETileTestResources.x(); build.y = 64; build.z = KOMETileTestResources.z();
        build.populationFaction = "gondor"; build.type = KOMEBuildType.DEFENSIVE;
        data.builds.put(id, build);
        return build;
    }

    static KOMESiegeComplex complex(String id, String tile, int dimension, KOMESiegeConnection... connections) {
        // Empty geometry is deliberately representable; this service is not a readiness validator.
        return new KOMESiegeComplex(id, tile, dimension, 17L, Collections.emptyList(), Collections.emptyList(),
            Collections.emptyList(), "UNRESOLVED", Arrays.asList(connections));
    }

    static KOMESiegeConnection gate(String id, String build, String record) {
        return KOMESiegeConnection.gated(id, KOMESiegeAreaRef.exterior(), KOMESiegeAreaRef.normal("a"),
            "gate", new KOMEDefensiveGateRef(build, record));
    }

    static long revision(KOMEWorldData data) { return data.getTacticalConfigurationSnapshot().getRevision(); }

    static void assign(KOMEWorldData data, String build, String complex) {
        assertTrue(KOMETacticalMembershipService.assignBuild(data, build, complex, revision(data)).isChanged());
    }

    static KOMEDefensiveGateRecord link(KOMEWorldData data, String build) {
        KOMEDefensiveGateLinkService.OperationResult result = KOMEDefensiveGateLinkService.link(data, data.getBuild(build),
            inspection(UUID.randomUUID(), 1, build.equals("B1") ? 10 : 30), null, "Admin", true, 10L);
        assertTrue(result.getMessage(), result.isSuccessful());
        return result.getRecord();
    }

    static KOMEPhysicalGateInspection.Result inspection(UUID uuid, int revision, int x) {
        return KOMEPhysicalGateInspection.inspect(new KOMEPhysicalGateInspection.Snapshot(
            KOMETileTestResources.dimension(), x, 64, 20, uuid, revision, true, false, true,
            GateOrientation.WIDTH_X, GateOpeningDirection.FORWARD, new GateHinge(1, 0), new GateHinge(3, 0),
            KOMEPhysicalGateInspectionTest.regularParts()));
    }

    static NBTTagCompound save(KOMEWorldData data) {
        NBTTagCompound root = new NBTTagCompound();
        data.writeToNBT(root);
        return root;
    }
}
