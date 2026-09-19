package com.enovak.lotrmoremobs.siege.tile;

import com.enovak.lotrmoremobs.siege.gate.GateHinge;
import com.enovak.lotrmoremobs.siege.gate.GateLeaf;
import com.enovak.lotrmoremobs.siege.gate.GateOpeningDirection;
import com.enovak.lotrmoremobs.siege.gate.GateOrientation;
import com.enovak.lotrmoremobs.siege.gate.GatePartData;
import com.enovak.lotrmoremobs.siege.management.KOMEGateManagementSnapshot;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import kome.common.data.KOMEBuildContribution;
import kome.common.data.KOMEBuildType;
import kome.common.data.KOMEDefensiveGateHealthCalculator;
import kome.common.data.KOMEDefensiveGateLinkService;
import kome.common.data.KOMEGateSizeCalculator;
import kome.common.data.KOMEPhysicalGateInspection;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEWorldData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TileEntitySiegeGateKOMEHealthTest {
    @BeforeClass public static void registerTestTileEntityMapping() {
        TileEntity.addMapping(TileEntitySiegeGate.class,
            "lotrmoremobs:test_kome_gate_health");
    }

    @Test public void initialKomeStateSetsAndPersistsPhysicalCurrentAndMaximum() throws Exception {
        TileEntitySiegeGate gate = gateWithHealth(1000, 275);
        assertFalse(gate.isKomeHealthInitialized());

        applyValidatedKomeState(gate, 5000);

        assertEquals(5000, gate.getMaxHealth());
        assertEquals(5000, gate.getCurrentHealth());
        assertTrue(gate.isKomeHealthInitialized());

        NBTTagCompound saved = new NBTTagCompound();
        gate.writeToNBT(saved);
        assertEquals(5000, saved.getInteger("MaxHealth"));
        assertEquals(5000, saved.getInteger("CurrentHealth"));
        assertTrue(saved.getBoolean("KOMEHealthInitialized"));

        TileEntitySiegeGate restored = new TileEntitySiegeGate();
        restored.readFromNBT(saved);
        assertEquals(5000, restored.getMaxHealth());
        assertEquals(5000, restored.getCurrentHealth());
        assertTrue(restored.isKomeHealthInitialized());
    }

    @Test public void persistedInitializationMarkerPreventsRestartRelinkHealing()
            throws Exception {
        TileEntitySiegeGate gate = gateWithHealth(1000, 275);
        applyValidatedKomeState(gate, 5000);
        setPersistedHealth(gate, 5000, 3200, true);

        NBTTagCompound saved = new NBTTagCompound();
        gate.writeToNBT(saved);
        TileEntitySiegeGate restored = new TileEntitySiegeGate();
        restored.readFromNBT(saved);
        applyValidatedKomeState(restored, 7500);

        assertTrue(restored.isKomeHealthInitialized());
        assertEquals(7500, restored.getMaxHealth());
        assertEquals(3200, restored.getCurrentHealth());
    }

    @Test public void unlinkAndRelinkNeverHealAndLowerMaximumClampsCurrentHealth()
            throws Exception {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild first = defensiveBuild("B1", 5000L);
        KOMEPlayerBuild stronger = defensiveBuild("B2", 7500L);
        KOMEPlayerBuild lower = defensiveBuild("B3", 2500L);
        data.builds.put(first.id, first);
        data.builds.put(stronger.id, stronger);
        data.builds.put(lower.id, lower);
        KOMEPhysicalGateInspection.Result inspection = inspection();
        TileEntitySiegeGate gate = gateWithHealth(1000, 275);

        KOMEDefensiveGateLinkService.OperationResult initial =
            KOMEDefensiveGateLinkService.linkAndInitializePhysicalHealth(data, first,
                inspection, null, "Admin", true, 10L, physicalApplication(gate, 5000));
        assertTrue(initial.isSuccessful());
        assertEquals(5000, gate.getCurrentHealth());

        setPersistedHealth(gate, 5000, 3200, true);
        assertTrue(KOMEDefensiveGateLinkService.unlink(data, first,
            initial.getRecord().getId(), null, "Admin", true, 11L).isSuccessful());
        KOMEDefensiveGateLinkService.OperationResult sameBuild =
            KOMEDefensiveGateLinkService.linkAndInitializePhysicalHealth(data, first,
                inspection, null, "Admin", true, 12L, physicalApplication(gate, 5000));
        assertTrue(sameBuild.isSuccessful());
        assertEquals(5000, gate.getMaxHealth());
        assertEquals(3200, gate.getCurrentHealth());

        assertTrue(KOMEDefensiveGateLinkService.unlink(data, first,
            sameBuild.getRecord().getId(), null, "Admin", true, 13L).isSuccessful());
        KOMEDefensiveGateLinkService.OperationResult strongerBuild =
            KOMEDefensiveGateLinkService.linkAndInitializePhysicalHealth(data, stronger,
                inspection, null, "Admin", true, 14L, physicalApplication(gate, 7500));
        assertTrue(strongerBuild.isSuccessful());
        assertEquals(7500, gate.getMaxHealth());
        assertEquals(3200, gate.getCurrentHealth());

        assertTrue(KOMEDefensiveGateLinkService.unlink(data, stronger,
            strongerBuild.getRecord().getId(), null, "Admin", true, 15L).isSuccessful());
        assertTrue(KOMEDefensiveGateLinkService.linkAndInitializePhysicalHealth(data, lower,
            inspection, null, "Admin", true, 16L, physicalApplication(gate, 2500))
            .isSuccessful());
        assertEquals(2500, gate.getMaxHealth());
        assertEquals(2500, gate.getCurrentHealth());
    }

    @Test public void snapshotAndProjectionCannotHealDamagedLinkedPhysicalGate() {
        TileEntitySiegeGate gate = gateWithHealth(5000, 3200, true);
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerBuild build = defensiveBuild(5000L);
        data.builds.put(build.id, build);
        KOMEPhysicalGateInspection.Result inspection = inspection();
        KOMEDefensiveGateLinkService.OperationResult linked =
            KOMEDefensiveGateLinkService.link(data, build, inspection, null,
                "Admin", true, 10L);
        assertTrue(linked.isSuccessful());

        assertTrue(KOMEDefensiveGateLinkService.refresh(data, build,
            linked.getRecord().getId(), inspection, null, "Admin", true, 11L)
            .isSuccessful());

        KOMEGateManagementSnapshot.create(data, inspection, 1000,
            Collections.<KOMEGateManagementSnapshot.BrokenRecord>emptyList(), true);
        KOMEDefensiveGateHealthCalculator.calculateAutomaticMaxHp(
            build.approvedDefensiveCentiHours(), 100.0D,
            inspection.getDetectedWidth(), inspection.getDetectedHeight(),
            KOMEGateSizeCalculator.Parameters.defaults());

        assertEquals(5000, gate.getMaxHealth());
        assertEquals(3200, gate.getCurrentHealth());
    }

    private static TileEntitySiegeGate gateWithHealth(int maxHealth, int currentHealth) {
        return gateWithHealth(maxHealth, currentHealth, false);
    }

    private static TileEntitySiegeGate gateWithHealth(int maxHealth, int currentHealth,
            boolean initialized) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("MaxHealth", maxHealth);
        nbt.setInteger("CurrentHealth", currentHealth);
        nbt.setBoolean("KOMEHealthInitialized", initialized);
        TileEntitySiegeGate gate = new TileEntitySiegeGate();
        gate.readFromNBT(nbt);
        return gate;
    }

    private static void setPersistedHealth(TileEntitySiegeGate gate, int maxHealth,
            int currentHealth, boolean initialized) {
        NBTTagCompound nbt = new NBTTagCompound();
        gate.writeToNBT(nbt);
        nbt.setInteger("MaxHealth", maxHealth);
        nbt.setInteger("CurrentHealth", currentHealth);
        nbt.setBoolean("KOMEHealthInitialized", initialized);
        gate.readFromNBT(nbt);
    }

    private static void applyValidatedKomeState(TileEntitySiegeGate gate, int maxHp)
            throws Exception {
        Method method = TileEntitySiegeGate.class.getDeclaredMethod(
            "applyKomeLinkedHealthState", Integer.TYPE);
        method.setAccessible(true);
        method.invoke(gate, Integer.valueOf(maxHp));
    }

    private static KOMEDefensiveGateLinkService.PhysicalHealthApplication
            physicalApplication(final TileEntitySiegeGate gate, final int maxHp) {
        return new KOMEDefensiveGateLinkService.PhysicalHealthApplication() {
            public boolean canApply() {
                return true;
            }

            public boolean apply() {
                try {
                    applyValidatedKomeState(gate, maxHp);
                    return true;
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }
        };
    }

    private static KOMEPlayerBuild defensiveBuild(long approvedCentiHours) {
        return defensiveBuild("B1", approvedCentiHours);
    }

    private static KOMEPlayerBuild defensiveBuild(String id, long approvedCentiHours) {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = id;
        build.displayName = "Fortress";
        build.type = KOMEBuildType.DEFENSIVE;
        build.active = true;
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = "H1";
        contribution.centiHours = approvedCentiHours;
        contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution);
        return build;
    }

    private static KOMEPhysicalGateInspection.Result inspection() {
        List<GatePartData> parts = new ArrayList<GatePartData>();
        for (int y = 0; y < 4; y++) {
            parts.add(new GatePartData(1, y, 0, GateLeaf.LEFT));
            parts.add(new GatePartData(2, y, 0, GateLeaf.SPLIT_CENTER));
            parts.add(new GatePartData(3, y, 0, GateLeaf.RIGHT));
        }
        return KOMEPhysicalGateInspection.inspect(new KOMEPhysicalGateInspection.Snapshot(
            0, 10, 64, 20, UUID.randomUUID(), 1, true, false, true,
            GateOrientation.WIDTH_X, GateOpeningDirection.FORWARD,
            new GateHinge(1, 0), new GateHinge(3, 0), parts));
    }
}
