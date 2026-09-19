package kome.common.data;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.management.KOMEGateManagementSnapshot;
import cpw.mods.fml.relauncher.FMLInjectionData;
import kome.common.config.KOMEConfigRegistry;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KOMEGateManagementSnapshotTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass public static void initializeForgeConfigurationBasePath() throws Exception {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, new File(".").getAbsoluteFile());
    }

    @Test public void selectionListsOnlyDefensiveBuildsRegardlessOfLocation() throws Exception {
        KOMEConfigRegistry.load(new File(temporaryFolder.newFolder(), "kome.cfg"));
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild defensive = build("B1", "Far Fortress", KOMEBuildType.DEFENSIVE,
            10000L, 100000, -100000);
        KOMEPlayerBuild normal = build("B2", "Nearby House", KOMEBuildType.NORMAL,
            10000L, 0, 0);
        data.builds.put(defensive.id, defensive);
        data.builds.put(normal.id, normal);
        KOMEGateManagementSnapshot snapshot = KOMEGateManagementSnapshot.create(data,
            regularInspection(UUID.randomUUID(), 1), 1000,
            Collections.<KOMEGateManagementSnapshot.BrokenRecord>emptyList(), true);
        assertFalse(snapshot.isLinked());
        assertEquals(1, snapshot.getEligibleBuilds().size());
        assertEquals("B1", snapshot.getEligibleBuilds().get(0).getBuildId());
        assertTrue(snapshot.getEligibleBuilds().get(0).getLabel().contains("100h"));
        assertTrue(snapshot.getEligibleBuilds().get(0).getLabel().contains("10,000 HP"));
    }

    @Test public void projectedSelectionHpAlwaysUsesCurrentApprovedHours() throws Exception {
        KOMEConfigRegistry.load(new File(temporaryFolder.newFolder(), "kome.cfg"));
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = build("B1", "Fortress", KOMEBuildType.DEFENSIVE,
            10000L, 0, 0);
        data.builds.put(build.id, build);
        KOMEPhysicalGateInspection.Result inspection = regularInspection(UUID.randomUUID(), 1);
        assertTrue(snapshot(data, inspection).getEligibleBuilds().get(0).getLabel()
            .contains("10,000 HP"));
        build.contributions.get(0).centiHours = 20000L;
        assertTrue(snapshot(data, inspection).getEligibleBuilds().get(0).getLabel()
            .contains("20,000 HP"));
    }

    @Test public void irregularLinkedGateReportsConfirmationNeeded() throws Exception {
        KOMEConfigRegistry.load(new File(temporaryFolder.newFolder(), "kome.cfg"));
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = build("B1", "Fortress", KOMEBuildType.DEFENSIVE,
            10000L, 0, 0);
        data.builds.put(build.id, build);
        UUID uuid = UUID.randomUUID();
        KOMEPhysicalGateInspection.Result irregular = irregularInspection(uuid, 1);
        assertTrue(KOMEDefensiveGateLinkService.link(data, build, irregular, null, "Admin",
            true, 10L).isSuccessful());
        KOMEGateManagementSnapshot snapshot = snapshot(data, irregular);
        assertTrue(snapshot.isLinked());
        assertTrue(snapshot.needsDimensionConfirmation());
        assertEquals("Needs confirmation", snapshot.getGateSizeLabel());
        assertTrue(snapshot.getProjectedMaxHpLabel().contains("confirm dimensions"));
    }

    @Test public void unlinkedGateDisplaysCanonicalServerDefaultSource() throws Exception {
        KOMEConfigRegistry.load(new File(temporaryFolder.newFolder(), "kome.cfg"));
        KOMEGateManagementSnapshot snapshot = KOMEGateManagementSnapshot.create(
            new KOMEWorldData("test"), regularInspection(UUID.randomUUID(), 1), 1234,
            Collections.<KOMEGateManagementSnapshot.BrokenRecord>emptyList(), true);
        assertFalse(snapshot.isLinked());
        assertEquals(1234, snapshot.getServerDefaultMaxHp());
        assertEquals("1,234 (Server Default)", snapshot.getProjectedMaxHpLabel());
    }

    @Test public void liveRevisionMismatchNeverDisplaysStaleManualConfirmation() throws Exception {
        KOMEConfigRegistry.load(new File(temporaryFolder.newFolder(), "kome.cfg"));
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = build("B1", "Fortress", KOMEBuildType.DEFENSIVE,
            10000L, 0, 0);
        data.builds.put(build.id, build);
        UUID uuid = UUID.randomUUID();
        KOMEPhysicalGateInspection.Result revisionOne = irregularInspection(uuid, 1);
        KOMEDefensiveGateRecord record = KOMEDefensiveGateLinkService.link(data, build,
            revisionOne, null, "Admin", true, 10L).getRecord();
        assertTrue(KOMEDefensiveGateLinkService.confirmDimensions(data, build, record.getId(),
            revisionOne, 5, 7, null, "Admin", true, 11L).isSuccessful());

        KOMEGateManagementSnapshot staleView = snapshot(data, irregularInspection(uuid, 2));
        assertTrue(staleView.needsDimensionConfirmation());
        assertEquals("Needs confirmation", staleView.getGateSizeLabel());
    }

    @Test public void rebuiltControllerOffersExplicitRelinkInsteadOfFreshLink() throws Exception {
        KOMEConfigRegistry.load(new File(temporaryFolder.newFolder(), "kome.cfg"));
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = build("B1", "Fortress", KOMEBuildType.DEFENSIVE,
            10000L, 0, 0);
        data.builds.put(build.id, build);
        KOMEDefensiveGateRecord old = KOMEDefensiveGateLinkService.link(data, build,
            regularInspection(UUID.randomUUID(), 1), null, "Admin", true, 10L).getRecord();
        KOMEPhysicalGateInspection.Result replacement = regularInspection(UUID.randomUUID(), 1);
        KOMEGateManagementSnapshot view = KOMEGateManagementSnapshot.create(data, replacement,
            1000, Collections.singletonList(
                new KOMEGateManagementSnapshot.BrokenRecord(build, old)), true);
        assertFalse(view.isLinked());
        assertTrue(view.isRelinkRequired());
        assertTrue(view.getEligibleBuilds().isEmpty());
        assertEquals(1, view.getRelinkOptions().size());
        assertEquals("G1", view.getRelinkOptions().get(0).getRecordId());
    }

    private static KOMEGateManagementSnapshot snapshot(KOMEWorldData data,
            KOMEPhysicalGateInspection.Result inspection) {
        return KOMEGateManagementSnapshot.create(data, inspection, 1000,
            Collections.<KOMEGateManagementSnapshot.BrokenRecord>emptyList(), true);
    }

    private static KOMEPlayerBuild build(String id, String name, KOMEBuildType type,
            long centiHours, double x, double z) {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = id;
        build.displayName = name;
        build.type = type;
        build.active = true;
        build.x = x;
        build.z = z;
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = "H1";
        contribution.centiHours = centiHours;
        contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution);
        return build;
    }

    private static KOMEPhysicalGateInspection.Result regularInspection(UUID uuid, int revision) {
        return inspection(uuid, revision, regularParts());
    }

    private static KOMEPhysicalGateInspection.Result irregularInspection(UUID uuid, int revision) {
        List<GatePartData> parts = regularParts();
        for (int i = 0; i < parts.size(); i++) {
            GatePartData part = parts.get(i);
            if (part.getRelativeX() == 2 && part.getRelativeY() == 3) {
                parts.remove(i);
                break;
            }
        }
        return inspection(uuid, revision, parts);
    }

    private static KOMEPhysicalGateInspection.Result inspection(UUID uuid, int revision,
            List<GatePartData> parts) {
        return KOMEPhysicalGateInspection.inspect(new KOMEPhysicalGateInspection.Snapshot(
            0, 10, 64, 20, uuid, revision, true, false, true,
            GateOrientation.WIDTH_X, GateOpeningDirection.FORWARD,
            new GateHinge(1, 0), new GateHinge(5, 0), parts));
    }

    private static List<GatePartData> regularParts() {
        List<GatePartData> parts = new ArrayList<GatePartData>();
        for (int y = 0; y < 5; y++) {
            parts.add(new GatePartData(1, y, 0, GateLeaf.LEFT));
            parts.add(new GatePartData(2, y, 0, GateLeaf.LEFT));
            parts.add(new GatePartData(3, y, 0, GateLeaf.SPLIT_CENTER));
            parts.add(new GatePartData(4, y, 0, GateLeaf.RIGHT));
            parts.add(new GatePartData(5, y, 0, GateLeaf.RIGHT));
        }
        return parts;
    }
}
