package kome.common.data;

import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTREntityGondorMan;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import org.junit.Test;
import lotr.common.fac.LOTRFactionRelations;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.State;

public class KOMEEmergencyDefenseMobilizationServiceTest {
    @org.junit.Rule public final KOMETileTestResources geometry =
        new KOMETileTestResources();
    @Test public void wholeUnitAllocationMatchesLockedExamples() {
        assertEquals(3, KOMEEmergencyDefenseCommitment.allocationUnits(13000L,
            BigInteger.valueOf(5), BigInteger.TEN, 20));
        assertEquals(4, KOMEEmergencyDefenseCommitment.allocationUnits(12000L,
            BigInteger.valueOf(5), BigInteger.valueOf(15), 10));
        assertEquals(0, KOMEEmergencyDefenseCommitment.allocationUnits(999L,
            BigInteger.ONE, BigInteger.ONE, 10));
    }

    @Test public void zeroProductionAllocatesNoMinimumAndNeverDividesByZero() {
        assertEquals(0, KOMEEmergencyDefenseCommitment.allocationUnits(100000L,
            BigInteger.ZERO, BigInteger.TEN, 10));
        assertEquals(0, KOMEEmergencyDefenseCommitment.allocationUnits(100000L,
            BigInteger.TEN, BigInteger.ZERO, 10));
    }

    @Test public void deferredObservedConflictPromotesOnceUsingThresholdCrossingState()
            throws Exception {
        relation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        try {
            KOMEAccessFixture fixture = new KOMEAccessFixture();
            KOMEWorldData data = world();
            data.writeFactionKingRecord("gondor", UUID.randomUUID(), "King");
            KOMEEmergencyDefenseService.INSTANCE.recordRecruitmentActivity(data,
                "gondor", 1, true,
                KOMEEmergencyDefenseService.RecruitmentSource.PLAYER_COMBAT_HIRE, 100L);
            long threshold = KOMEEmergencyDefenseService.INSTANCE
                .configuredInactivityThresholdMillis();
            KOMEEmergencyDefenseMobilizationService.Plan deferred =
                KOMEEmergencyDefenseMobilizationService.INSTANCE
                    .prepareForCreatedConflict(data, fixture.world, "CF1", "T100",
                        "gondor", 110L);
            assertTrue(deferred.applicable);
            assertNull(deferred.commitment);
            assertNotNull(deferred.observation);
            createAuthoritativeAttack(data, "T100", "gondor", "mordor", 110L, true);
            assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE
                .publishPlan(data, deferred));
            assertTrue(data.emergencyDefenseCommitments.isEmpty());
            assertEquals(1, data.emergencyDefenseObservations.size());

            data.grantFactionPopulationCenti("gondor", 6000L);
            addDevelopedProduction(data, "T100", "gondor", 20);
            KOMETileWaypoint rally = new KOMETileWaypoint("T100", KOMETileWaypoint.RALLY);
            rally.set(0, 0D, 65D, 0D, "test", true);
            data.tileWaypoints.put("T100|rally", rally);
            NBTTagCompound saved = save(data);
            KOMEWorldData restored = new KOMEWorldData("restored");
            restored.readFromNBT(saved);
            assertEquals(1, restored.emergencyDefenseObservations.size());

            fixture.world.isRemote = true; // Fixture avoids LOTR's WorldServer watcher cast.
            int cost = KOMEEmergencyDefenseTemplateRegistry.require("gondor")
                .populationCost(fixture.world);
            int expectedUnits = (int) (6000L
                / (cost * KOMEPopulationService.CENTI_PER_POPULATION));
            fixture.world.flatTerrain = false; // placement unavailable; publication must not care
            KOMEEmergencyDefenseMobilizationService.INSTANCE.processPending(restored,
                fixture.world, 100L + threshold);
            fixture.world.isRemote = false;
            KOMEEmergencyDefenseCommitment commitment =
                restored.emergencyDefenseCommitments.get("CF1");
            assertNotNull(commitment);
            assertEquals(6000L, commitment.availablePopulationBasisCenti);
            assertTrue(commitment.attackedTileProductionRate > 0L);
            assertEquals(commitment.attackedTileProductionRate,
                commitment.totalFactionProductionRate);
            assertEquals(expectedUnits, commitment.calculatedUnitCount);
            assertEquals(0, restored.emergencyDefenseObservations.size());
            assertEquals(6000L - (long) expectedUnits * cost
                    * KOMEPopulationService.CENTI_PER_POPULATION,
                KOMEPopulationService.getAvailablePopulationCenti(restored, "gondor"));
            assertEquals(0, restored.getConflictService().get("T100")
                .getOriginalGarrison().size());
            long afterFirst = KOMEPopulationService.getAvailablePopulationCenti(
                restored, "gondor");
            KOMEEmergencyDefenseMobilizationService.INSTANCE.processPending(restored,
                null, 100L + threshold + 1L);
            assertEquals(afterFirst, KOMEPopulationService.getAvailablePopulationCenti(
                restored, "gondor"));
            assertEquals(expectedUnits, restored.emergencyDefenseCommitments.get("CF1")
                .calculatedUnitCount);
            assertEquals(expectedUnits, restored.emergencyDefenseCommitments.get("CF1")
                .defenders.size());
        } finally {
            relation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        }
    }

    @Test public void endingBeforeThresholdExpiresDeferredReceiptWithoutDebit()
            throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        KOMEWorldData data = world();
        data.writeFactionKingRecord("gondor", UUID.randomUUID(), "King");
        KOMEEmergencyDefenseService.INSTANCE.recordRecruitmentActivity(data,
            "gondor", 1, true,
            KOMEEmergencyDefenseService.RecruitmentSource.PLAYER_COMBAT_HIRE, 100L);
        KOMEEmergencyDefenseMobilizationService.Plan deferred =
            KOMEEmergencyDefenseMobilizationService.INSTANCE.prepareForCreatedConflict(
                data, fixture.world, "CF1", "T100", "gondor", 110L);
        createAuthoritativeAttack(data, "T100", "gondor", "mordor", 110L, false);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE
            .publishPlan(data, deferred));
        data.grantFactionPopulationCenti("gondor", 6000L);
        KOMEConflictRecord conflict = data.getConflictService().get("T100");
        KOMEConflictService.EndResult ended = data.getConflictService()
            .endWithMovementHandoff(data, "T100", ExpectedConflict.at(
                conflict.getConflictId(), conflict.getRevision()),
                new Context(120L, "test", "explicit end"),
                KOMEConflictService.EndSource.LIFECYCLE);
        assertEquals(Code.SUCCESS, ended.conflictResult.code);
        assertTrue(data.emergencyDefenseObservations.isEmpty());
        assertTrue(data.emergencyDefenseCommitments.isEmpty());
        assertEquals(6000L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
    }

    @Test public void schemaNineSectionTwoInventsNoReceiptForLegacyActiveConflict()
            throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        KOMEWorldData source = world();
        createAuthoritativeAttack(source, "T100", "gondor", "mordor", 10L, false);
        NBTTagCompound legacy = save(source);
        legacy.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 9);
        legacy.removeTag(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY);
        legacy.removeTag("TacticalConfiguration");
        legacy.setInteger(KOMEEmergencyDefensePersistence.SCHEMA_KEY, 2);
        legacy.removeTag(KOMEEmergencyDefensePersistence.OBSERVATIONS_KEY);
        KOMEWorldData loaded = new KOMEWorldData("legacy");
        loaded.readFromNBT(legacy);
        assertTrue(loaded.emergencyDefenseObservations.isEmpty());
        KOMEEmergencyDefenseMobilizationService.INSTANCE.processPending(loaded,
            fixture.world, 100000L);
        assertTrue(loaded.emergencyDefenseCommitments.isEmpty());
        assertTrue(loaded.isDirty());
    }

    @Test public void pendingIntentDeploysExactlyOnceAfterPlacementBecomesAvailable()
            throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        fixture.world.isRemote = true;
        fixture.world.provider.dimensionId = KOMETileTestResources.dimension();
        KOMEWorldData data = world();
        createAuthoritativeAttack(data, "T100", "fangorn", "mordor", 10L, false);
        data.grantFactionPopulationCenti("fangorn", 2000L);
        KOMETileWaypoint rally = new KOMETileWaypoint("T100", KOMETileWaypoint.RALLY);
        rally.set(KOMETileTestResources.dimension(), KOMETileTestResources.x(), 65D,
            KOMETileTestResources.z(), "test", true);
        data.tileWaypoints.put("T100|rally", rally);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(data,
            plan("CF1", "T100", "fangorn", 1, 20, 2000L)));

        fixture.world.flatTerrain = false;
        assertEquals(1, KOMEEmergencyDefenseMobilizationService.INSTANCE
            .processPending(data, fixture.world, 20L,
                (ignoredData, ignoredWorld, ignoredTile, ignoredNpc) ->
                    KOMEStrategicDeploymentResolver.Validation.invalid(
                        "Temporary deployment authority unavailable.")));
        assertEquals(KOMEEmergencyDefenseCommitment.Disposition.PENDING,
            data.emergencyDefenseCommitments.get("CF1").defenders.get("ED-1-1")
                .disposition);
        assertTrue(fixture.world.loadedEntityList.isEmpty());

        fixture.world.flatTerrain = true;
        fixture.world.spawnSucceeds = true;
        assertEquals(1, KOMEEmergencyDefenseMobilizationService.INSTANCE
            .processPending(data, fixture.world, 21L,
                (ignoredData, ignoredWorld, ignoredTile, ignoredNpc) ->
                    KOMEStrategicDeploymentResolver.Validation.valid(
                        new KOMEStrategicDeploymentResolver.Anchor(
                            KOMETileTestResources.dimension(),
                            KOMETileTestResources.x() + 0.5D, 65D,
                            KOMETileTestResources.z() + 0.5D))));
        KOMEEmergencyDefenseCommitment.Defender deployed =
            data.emergencyDefenseCommitments.get("CF1").defenders.get("ED-1-1");
        assertEquals(deployed.diagnostic,
            KOMEEmergencyDefenseCommitment.Disposition.ACTIVE, deployed.disposition);
        assertEquals(1, fixture.world.loadedEntityList.size());
        assertEquals(0, KOMEEmergencyDefenseMobilizationService.INSTANCE
            .processPending(data, fixture.world, 22L));
        assertEquals(1, fixture.world.loadedEntityList.size());
        assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(data, "fangorn"));
    }

    @Test public void lifecycleStatesRejectIncompleteActiveOrUnfinalizedDemobilizedAuthority() {
        Map<String, KOMEEmergencyDefenseCommitment.Defender> none =
            new LinkedHashMap<String, KOMEEmergencyDefenseCommitment.Defender>();
        try {
            new KOMEEmergencyDefenseCommitment("CF1", "T100", "gondor",
                1L, 1L, 2000L, "EDT-GONDOR", 20, 1, 2000L, 10L,
                KOMEEmergencyDefenseCommitment.State.ACTIVE, none, "invalid");
            fail("Incomplete ACTIVE cohort accepted.");
        } catch (IllegalArgumentException expected) { }
        try {
            new KOMEEmergencyDefenseCommitment("CF1", "T100", "gondor",
                1L, 1L, 2000L, "EDT-GONDOR", 20, 1, 2000L, 10L,
                KOMEEmergencyDefenseCommitment.State.DEMOBILIZED, none, "invalid");
            fail("Unrefunded DEMOBILIZED cohort accepted.");
        } catch (IllegalArgumentException expected) { }
    }

    @Test public void sequentialConflictCommitmentsCannotDoubleSpendPopulation() {
        KOMEWorldData data = world();
        data.grantFactionPopulationCenti("gondor", 13000L);
        createConflict(data, "T100", 10L);
        KOMEEmergencyDefenseMobilizationService.Plan first = plan("CF1", "T100",
            "gondor", 3, 20, 13000L);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(data, first));
        assertEquals(7000L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        createConflict(data, "T101", 20L);
        KOMEEmergencyDefenseMobilizationService.Plan second = plan("CF2", "T101",
            "gondor", 3, 20, 7000L);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(data, second));
        assertEquals(1000L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertFalse(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(data, second));
    }

    @Test public void defenderAuthorityHasNoPlayerOwnerOrCompanyIdentity() {
        KOMEEmergencyDefenseCommitment value = plan("CF1", "T100", "gondor",
            1, 20, 2000L).commitment;
        assertFalse(value.defenders.isEmpty());
        assertFalse(KOMEHiredUnitRecord.class.isAssignableFrom(
            KOMEEmergencyDefenseCommitment.Defender.class));
        assertFalse(KOMEArmyCompany.class.isAssignableFrom(
            KOMEEmergencyDefenseCommitment.class));
    }

    @Test public void battlefieldHomeMarkerAndAutonomousReturnAuthoritySurviveEntityReload()
            throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        fixture.world.isRemote = true;
        LOTREntityGondorMan original = new LOTREntityGondorMan(fixture.world);
        fixture.world.isRemote = false;
        UUID id = UUID.randomUUID(); original.setUniqueID(id);
        original.setLocationAndAngles(100.5D, 65D, 200.5D, 0F, 0F);
        KOMEEmergencyDefenseMobilizationService.establishBattlefieldHome(original,
            "ED-1-1", "CF1", 100, 65, 200);
        assertTrue(original.hasHome());
        assertEquals(100, original.getHomePosition().posX);
        assertEquals(65, original.getHomePosition().posY);
        assertEquals(200, original.getHomePosition().posZ);
        assertEquals(KOMEEmergencyDefenseMobilizationService.BATTLEFIELD_HOME_RADIUS,
            original.func_110174_bM(), 0F);
        assertEquals(1, homeTaskCount(original));
        assertFalse(original.hiredNPCInfo.isActive);

        NBTTagCompound savedMarker = (NBTTagCompound) original.getEntityData().copy();
        fixture.world.isRemote = true;
        LOTREntityGondorMan reloaded = new LOTREntityGondorMan(fixture.world);
        fixture.world.isRemote = false;
        reloaded.setUniqueID(id);
        for (Object rawKey : savedMarker.func_150296_c()) {
            String key = (String) rawKey;
            reloaded.getEntityData().setTag(key, savedMarker.getTag(key).copy());
        }
        KOMEEmergencyDefenseEntityMarker.Marker marker =
            KOMEEmergencyDefenseEntityMarker.read(reloaded);
        assertNotNull(marker); assertTrue(marker.hasHome);
        assertEquals(100, marker.homeX); assertEquals(65, marker.homeY);
        assertEquals(200, marker.homeZ);

        fixture.data.emergencyDefenseCommitments.put("CF1", activeCommitment(
            "CF1", "T100", "gondor", "ED-1-1", id));
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE
            .reconcileLoadedEntity(fixture.data, reloaded));
        assertTrue(reloaded.hasHome());
        assertEquals(100, reloaded.getHomePosition().posX);
        assertEquals(KOMEEmergencyDefenseMobilizationService.BATTLEFIELD_HOME_RADIUS,
            reloaded.func_110174_bM(), 0F);
        assertEquals(1, homeTaskCount(reloaded));
        assertFalse(reloaded.hiredNPCInfo.isActive);

        reloaded.setLocationAndAngles(130D, 65D, 200D, 0F, 0F);
        KOMEEmergencyDefenseHomeAI home = homeTask(reloaded);
        assertNotNull(home);
        assertFalse(reloaded.isWithinHomeDistanceCurrentPosition());
        assertTrue(KOMEEmergencyDefenseHomeAI.requiresReturn(reloaded));
    }

    @Test public void canceledDeathDoesNotConsumePopulationButConfirmedDeathDoes()
            throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        fixture.world.isRemote = true;
        LOTREntityGondorMan npc = new LOTREntityGondorMan(fixture.world);
        fixture.world.isRemote = false;
        UUID id = UUID.randomUUID(); npc.setUniqueID(id);
        fixture.data.emergencyDefenseCommitments.put("CF1", activeCommitment(
            "CF1", "T100", "gondor", "ED-1-1", id));
        KOMEEvents events = new KOMEEvents();
        LivingDeathEvent canceled = new LivingDeathEvent(npc, DamageSource.generic) {
            @Override public boolean isCancelable() { return true; }
        };
        assertTrue(canceled.isCancelable());
        canceled.setCanceled(true);
        events.onEmergencyDefenseDeath(canceled);
        assertEquals(KOMEEmergencyDefenseCommitment.Disposition.ACTIVE,
            fixture.data.emergencyDefenseCommitments.get("CF1")
                .defenders.get("ED-1-1").disposition);

        events.onEmergencyDefenseDeath(new LivingDeathEvent(npc, DamageSource.generic));
        assertEquals(KOMEEmergencyDefenseCommitment.Disposition.DEAD,
            fixture.data.emergencyDefenseCommitments.get("CF1")
                .defenders.get("ED-1-1").disposition);
        assertFalse(fixture.data.emergencyDefenseCommitments.get("CF1")
            .defenders.get("ED-1-1").populationRefunded);
    }

    @Test public void endedPendingIntentRefundsOnceButDeadDefenderNeverRefunds() {
        KOMEWorldData data = world();
        data.grantFactionPopulationCenti("gondor", 4000L);
        createConflict(data, "T100", 10L);
        KOMEEmergencyDefenseMobilizationService.Plan prepared = plan("CF1", "T100",
            "gondor", 2, 20, 4000L);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(data, prepared));
        assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        KOMEEmergencyDefenseCommitment value = data.emergencyDefenseCommitments.get("CF1");
        KOMEEmergencyDefenseCommitment.Defender first = value.defenders.get("ED-1-1");
        data.emergencyDefenseCommitments.put("CF1", value.withDefender(first.with(
            KOMEEmergencyDefenseCommitment.Disposition.DEAD, false, "confirmed"),
            value.state, ""));
        KOMEEmergencyDefenseMobilizationService.INSTANCE.onConflictEnded(data, "CF1", 20L);
        assertEquals(1, KOMEEmergencyDefenseMobilizationService.INSTANCE
            .processPending(data, null, 21L));
        assertEquals(2000L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertEquals(0, KOMEEmergencyDefenseMobilizationService.INSTANCE
            .processPending(data, null, 22L));
        assertEquals(2000L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertEquals(KOMEEmergencyDefenseCommitment.State.DEMOBILIZED,
            data.emergencyDefenseCommitments.get("CF1").state);
    }

    @Test public void activeMissingEntityRemainsUnresolvedAndIsNotRefunded() {
        KOMEWorldData data = world(); data.grantFactionPopulationCenti("gondor", 2000L);
        createConflict(data, "T100", 10L);
        KOMEEmergencyDefenseMobilizationService.Plan prepared = plan("CF1", "T100",
            "gondor", 1, 20, 2000L);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(data, prepared));
        KOMEEmergencyDefenseCommitment value = data.emergencyDefenseCommitments.get("CF1");
        KOMEEmergencyDefenseCommitment.Defender defender = value.defenders.values().iterator().next();
        data.emergencyDefenseCommitments.put("CF1", value.withDefender(defender.with(
            KOMEEmergencyDefenseCommitment.Disposition.ACTIVE, false, "deployed"),
            KOMEEmergencyDefenseCommitment.State.ACTIVE, ""));
        KOMEEmergencyDefenseMobilizationService.INSTANCE.onConflictEnded(data, "CF1", 20L);
        assertEquals(0, KOMEEmergencyDefenseMobilizationService.INSTANCE
            .processPending(data, null, 21L));
        assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
    }

    @Test public void commitmentRoundTripsWithoutDuplicateDebitOrIntent() {
        KOMEWorldData data = world();
        data.grantFactionPopulationCenti("gondor", 5000L);
        createConflict(data, "T100", 10L);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(data,
            plan("CF1", "T100", "gondor", 2, 20, 5000L)));
        NBTTagCompound saved = save(data);
        KOMEWorldData loaded = new KOMEWorldData("loaded"); loaded.readFromNBT(saved);
        assertEquals(1000L, KOMEPopulationService.getAvailablePopulationCenti(loaded, "gondor"));
        KOMEEmergencyDefenseCommitment value = loaded.emergencyDefenseCommitments.get("CF1");
        assertNotNull(value); assertEquals(2, value.defenders.size());
        assertFalse(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(loaded,
            plan("CF1", "T100", "gondor", 2, 20, 5000L)));
        assertEquals(1000L, KOMEPopulationService.getAvailablePopulationCenti(loaded, "gondor"));
    }

    @Test public void schemaNineSectionTwoPreservesExistingCommitmentsAndAddsNoReceipt() {
        KOMEWorldData data = world();
        data.grantFactionPopulationCenti("gondor", 5000L);
        createConflict(data, "T100", 10L);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(data,
            plan("CF1", "T100", "gondor", 2, 20, 5000L)));
        NBTTagCompound oldSection = save(data);
        oldSection.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 9);
        oldSection.removeTag(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY);
        oldSection.removeTag("TacticalConfiguration");
        oldSection.setInteger(KOMEEmergencyDefensePersistence.SCHEMA_KEY, 2);
        oldSection.removeTag(KOMEEmergencyDefensePersistence.OBSERVATIONS_KEY);

        KOMEWorldData loaded = new KOMEWorldData("loaded-v2");
        loaded.readFromNBT(oldSection);
        KOMEEmergencyDefenseCommitment value = loaded.emergencyDefenseCommitments.get("CF1");
        assertNotNull(value);
        assertEquals(2, value.calculatedUnitCount);
        assertEquals(4000L, value.populationCommittedCenti);
        assertTrue(loaded.emergencyDefenseObservations.isEmpty());
        assertEquals(1000L, KOMEPopulationService.getAvailablePopulationCenti(loaded, "gondor"));
        assertTrue(loaded.isDirty());
    }

    @Test public void malformedCommitmentLoadIsAtomic() {
        KOMEWorldData source = world(); createConflict(source, "T100", 10L);
        source.grantFactionPopulationCenti("gondor", 2000L);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(source,
            plan("CF1", "T100", "gondor", 1, 20, 2000L)));
        NBTTagCompound malformed = save(source);
        malformed.getTagList(KOMEEmergencyDefensePersistence.COMMITMENTS_KEY, 10)
            .getCompoundTagAt(0).setString("State", "BOGUS");
        KOMEWorldData target = world();
        try { target.readFromNBT(malformed); fail("Malformed commitment loaded."); }
        catch (IllegalStateException expected) { assertTrue(target.isWriteBlocked()); }
        assertTrue(target.emergencyDefenseCommitments.isEmpty());
    }

    @Test public void malformedDeferredObservationLoadIsAtomic() throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        KOMEWorldData source = world();
        source.writeFactionKingRecord("gondor", UUID.randomUUID(), "King");
        KOMEEmergencyDefenseService.INSTANCE.recordRecruitmentActivity(source,
            "gondor", 1, true,
            KOMEEmergencyDefenseService.RecruitmentSource.PLAYER_COMBAT_HIRE, 100L);
        KOMEEmergencyDefenseMobilizationService.Plan deferred =
            KOMEEmergencyDefenseMobilizationService.INSTANCE.prepareForCreatedConflict(
                source, fixture.world, "CF1", "T100", "gondor", 110L);
        createAuthoritativeAttack(source, "T100", "gondor", "mordor", 110L, false);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE
            .publishPlan(source, deferred));
        NBTTagCompound malformed = save(source);
        malformed.getTagList(KOMEEmergencyDefensePersistence.OBSERVATIONS_KEY, 10)
            .getCompoundTagAt(0).setString("Tile", "not-a-tile");

        KOMEWorldData target = world();
        target.emergencyDefenseActivities.put("mordor",
            KOMEEmergencyDefenseActivity.unknownHistory("mordor", 1L));
        try { target.readFromNBT(malformed); fail("Malformed observation loaded."); }
        catch (IllegalStateException expected) { assertTrue(target.isWriteBlocked()); }
        assertTrue(target.emergencyDefenseActivities.containsKey("mordor"));
        assertTrue(target.emergencyDefenseObservations.isEmpty());
    }

    @Test public void systemReserveSourceDoesNotResetRulerActivity() {
        KOMEWorldData data = world();
        assertFalse(KOMEEmergencyDefenseService.INSTANCE.recordRecruitmentActivity(data,
            "gondor", 20, true,
            KOMEEmergencyDefenseService.RecruitmentSource.SYSTEM_EMERGENCY_RESERVE, 10L));
        assertFalse(data.emergencyDefenseActivities.containsKey("gondor"));
    }

    @Test public void newPopulationDoesNotEnlargeAttackStartSnapshot() {
        KOMEWorldData data = world(); data.grantFactionPopulationCenti("gondor", 2000L);
        createConflict(data, "T100", 10L);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(data,
            plan("CF1", "T100", "gondor", 1, 20, 2000L)));
        data.grantFactionPopulationCenti("gondor", 10000L);
        assertEquals(1, data.emergencyDefenseCommitments.get("CF1").calculatedUnitCount);
        assertEquals(10000L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
    }

    @Test public void emergencyCohortDoesNotEnlargeEncirclementOriginalGarrison() {
        KOMEWorldData data = world();
        KOMEConflictService.Result started = data.getConflictService().start("T100",
            State.ENCIRCLEMENT, ExpectedConflict.absent(),
            java.util.Collections.<GarrisonSeed>emptyList(),
            new Context(10L, "test", "encirclement"));
        KOMEConflictService.Result defender = data.getConflictService()
            .beginFactionParticipation("T100", ExpectedConflict.at(
                started.record.getConflictId(), started.record.getRevision()), "gondor",
                new Context(11L, "test", "defender"));
        assertEquals(0, defender.record.getOriginalGarrison().size());
        data.grantFactionPopulationCenti("gondor", 2000L);
        assertTrue(KOMEEmergencyDefenseMobilizationService.INSTANCE.publishPlan(data,
            plan("CF1", "T100", "gondor", 1, 20, 2000L)));
        assertEquals(0, data.getConflictService().get("T100")
            .getOriginalGarrison().size());
        assertEquals(0, data.getConflictService().get("T100")
            .getCommitments().size());
    }

    @Test public void explicitTemplatesCoverEveryFactionAndReferenceRealLotrEquipment() {
        KOMEEmergencyDefenseTemplateRegistry.validateDefinitions();
        assertEquals(new java.util.HashSet<String>(KOMEAlliance.allFactionKeys()),
            KOMEEmergencyDefenseTemplateRegistry.all().keySet());
        for (KOMEEmergencyDefenseTemplateRegistry.Template template
                : KOMEEmergencyDefenseTemplateRegistry.all().values()) {
            if (!template.entException) assertFalse(template.weaponField.isEmpty());
        }
        KOMEEmergencyDefenseTemplateRegistry.Template rohan =
            KOMEEmergencyDefenseTemplateRegistry.require("rohan");
        assertEquals("ROHIRRIM_MARSHAL", rohan.source);
        assertEquals(0, rohan.entryIndex); // foot soldier, unlike mounted muster entries 2/3
        assertTrue(KOMEEmergencyDefenseTemplateRegistry.require("fangorn").entException);
        try {
            String source = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(
                "src/main/java/kome/common/data/KOMEEmergencyDefenseTemplateRegistry.java")),
                java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(source.contains("LOTREnchantment.strong4"));
            assertTrue(source.contains("LOTREnchantment.meleeSpeed1"));
            assertTrue(source.contains("LOTREnchantment.meleeReach1"));
            assertTrue(source.contains("LOTREnchantment.protect2"));
            assertTrue(source.contains("LOTREnchantment.protectRanged3"));
            assertTrue(source.contains("LOTREnchantment.protectFall3"));
            assertTrue(source.contains("LOTRWeaponStats.getArmorProtection"));
            assertTrue(source.contains("protection < 20"));
        } catch (java.io.IOException failure) { throw new AssertionError(failure); }
    }

    @Test public void everyConventionalTemplateUsesAWeaponClassSupportingRequiredLotrModifiers() {
        for (KOMEEmergencyDefenseTemplateRegistry.Template template
                : KOMEEmergencyDefenseTemplateRegistry.all().values()) {
            if (template.entException) continue;
            String weapon = template.weaponField.toLowerCase(java.util.Locale.ROOT);
            assertTrue(template.id + " must use a sword, scimitar, or battleaxe",
                weapon.startsWith("sword") || weapon.startsWith("scimitar")
                    || weapon.startsWith("battleaxe"));
        }
        assertEquals("swordBronze",
            KOMEEmergencyDefenseTemplateRegistry.require("hobbit").weaponField);
        assertRequiredWeaponModifiers(new net.minecraft.item.ItemStack(
            new lotr.common.item.LOTRItemSword(net.minecraft.item.Item.ToolMaterial.IRON)));
        assertRequiredWeaponModifiers(new net.minecraft.item.ItemStack(
            new lotr.common.item.LOTRItemBattleaxe(net.minecraft.item.Item.ToolMaterial.IRON)));
    }

    @Test public void armorPolicySelectsStrongestLegalCommonProtectionPerPiece() {
        net.minecraft.item.ItemStack twoPointHelmet = new net.minecraft.item.ItemStack(
            new lotr.common.item.LOTRItemArmor(lotr.common.item.LOTRMaterial.RANGER, 0));
        assertEquals(2, ((net.minecraft.item.ItemArmor) twoPointHelmet.getItem())
            .damageReduceAmount);
        assertFalse(lotr.common.enchant.LOTREnchantment.protect2
            .canApply(twoPointHelmet, false));
        assertTrue(lotr.common.enchant.LOTREnchantment.protect1
            .canApply(twoPointHelmet, false));
        KOMEEmergencyDefenseTemplateRegistry.Template.applyArmorPolicy(
            twoPointHelmet, false);
        assertFalse(lotr.common.enchant.LOTREnchantmentHelper.hasEnchant(
            twoPointHelmet, lotr.common.enchant.LOTREnchantment.protect2));
        assertTrue(lotr.common.enchant.LOTREnchantmentHelper.hasEnchant(
            twoPointHelmet, lotr.common.enchant.LOTREnchantment.protect1));

        net.minecraft.item.ItemStack cappedHelmet = new net.minecraft.item.ItemStack(
            new lotr.common.item.LOTRItemArmor(lotr.common.item.LOTRMaterial.MITHRIL, 0));
        assertFalse(lotr.common.enchant.LOTREnchantment.protect2
            .canApply(cappedHelmet, false));
        assertFalse(lotr.common.enchant.LOTREnchantment.protect1
            .canApply(cappedHelmet, false));
        KOMEEmergencyDefenseTemplateRegistry.Template.applyArmorPolicy(cappedHelmet, false);
        assertFalse(lotr.common.enchant.LOTREnchantmentHelper.hasEnchant(
            cappedHelmet, lotr.common.enchant.LOTREnchantment.protect2));
        assertFalse(lotr.common.enchant.LOTREnchantmentHelper.hasEnchant(
            cappedHelmet, lotr.common.enchant.LOTREnchantment.protect1));
    }

    @Test public void armorPolicySelectsStrongestLegalSpecialProtection() {
        net.minecraft.item.ItemStack chest = new net.minecraft.item.ItemStack(
            new net.minecraft.item.ItemArmor(net.minecraft.item.ItemArmor.ArmorMaterial.CLOTH,
                0, 1));
        net.minecraft.item.ItemStack legs = new net.minecraft.item.ItemStack(
            new net.minecraft.item.ItemArmor(net.minecraft.item.ItemArmor.ArmorMaterial.CLOTH,
                0, 2));
        net.minecraft.item.ItemStack boots = new net.minecraft.item.ItemStack(
            new net.minecraft.item.ItemArmor(net.minecraft.item.ItemArmor.ArmorMaterial.CLOTH,
                0, 3));
        KOMEEmergencyDefenseTemplateRegistry.Template.applyArmorPolicy(chest, false);
        KOMEEmergencyDefenseTemplateRegistry.Template.applyArmorPolicy(legs, false);
        KOMEEmergencyDefenseTemplateRegistry.Template.applyArmorPolicy(boots, true);
        for (net.minecraft.item.ItemStack armor : new net.minecraft.item.ItemStack[] {
                chest, legs, boots}) {
            assertTrue(lotr.common.enchant.LOTREnchantmentHelper.hasEnchant(
                armor, lotr.common.enchant.LOTREnchantment.protect2));
            assertTrue(lotr.common.enchant.LOTREnchantmentHelper.hasEnchant(
                armor, lotr.common.enchant.LOTREnchantment.protectRanged3));
        }
        assertTrue(lotr.common.enchant.LOTREnchantmentHelper.hasEnchant(
            boots, lotr.common.enchant.LOTREnchantment.protectFall3));
    }

    @Test public void templateArmorValidationRemainsFailClosedForTargetsAndExceptions() {
        for (KOMEEmergencyDefenseTemplateRegistry.Template template
                : KOMEEmergencyDefenseTemplateRegistry.all().values()) {
            if (template.entException) assertEquals(0, countConfiguredArmor(template));
            if (!template.entException && !template.lightArmorException)
                assertEquals(template.id + " must retain a complete conventional armor set",
                    4, countConfiguredArmor(template));
        }
        lotr.common.item.LOTRMaterial[] configuredFullArmorMaterials = {
            lotr.common.item.LOTRMaterial.BLUE_DWARVEN,
            lotr.common.item.LOTRMaterial.HIGH_ELVEN,
            lotr.common.item.LOTRMaterial.GUNDABAD_URUK,
            lotr.common.item.LOTRMaterial.ANGMAR,
            lotr.common.item.LOTRMaterial.WOOD_ELVEN,
            lotr.common.item.LOTRMaterial.DOL_GULDUR,
            lotr.common.item.LOTRMaterial.DALE,
            lotr.common.item.LOTRMaterial.DWARVEN,
            lotr.common.item.LOTRMaterial.GALADHRIM,
            lotr.common.item.LOTRMaterial.URUK,
            lotr.common.item.LOTRMaterial.ROHAN_MARSHAL,
            lotr.common.item.LOTRMaterial.GONDOR,
            lotr.common.item.LOTRMaterial.MORDOR,
            lotr.common.item.LOTRMaterial.DORWINION_ELF,
            lotr.common.item.LOTRMaterial.RHUN_GOLD,
            lotr.common.item.LOTRMaterial.TAUREDAIN_GOLD
        };
        assertEquals(16, configuredFullArmorMaterials.length);
        for (lotr.common.item.LOTRMaterial material : configuredFullArmorMaterials)
            assertTrue(finalizedProtection(material) >= 20);

        String[] nativeExceptionFactions = {"dunland", "harad", "morwaith", "halftroll"};
        lotr.common.item.LOTRMaterial[] nativeExceptionMaterials = {
            lotr.common.item.LOTRMaterial.DUNLENDING,
            lotr.common.item.LOTRMaterial.NEAR_HARAD,
            lotr.common.item.LOTRMaterial.MOREDAIN,
            lotr.common.item.LOTRMaterial.HALF_TROLL
        };
        for (int index = 0; index < nativeExceptionFactions.length; index++) {
            KOMEEmergencyDefenseTemplateRegistry.Template template =
                KOMEEmergencyDefenseTemplateRegistry.require(nativeExceptionFactions[index]);
            assertTrue(template.nativeArmorException);
            assertFalse(template.lightArmorException);
            assertFalse(template.entException);
            assertEquals(19, template.nativeArmorExceptionProtection);
            assertFalse(template.nativeArmorExceptionReason.isEmpty());
            int protection = finalizedProtection(nativeExceptionMaterials[index]);
            assertEquals(19, protection);
            template.validateArmorProtection(protection);
        }
        assertTrue(KOMEEmergencyDefenseTemplateRegistry.require("dunedain")
            .lightArmorException);
        assertEquals(19, finalizedProtection(lotr.common.item.LOTRMaterial.RANGER));
        assertEquals(0, countConfiguredArmor(
            KOMEEmergencyDefenseTemplateRegistry.require("hobbit")));
        assertEquals(0, countConfiguredArmor(
            KOMEEmergencyDefenseTemplateRegistry.require("bree")));
        assertTrue(KOMEEmergencyDefenseTemplateRegistry.require("fangorn").entException);
    }

    private static void assertRequiredWeaponModifiers(net.minecraft.item.ItemStack weapon) {
        lotr.common.enchant.LOTREnchantment[] required = {
            lotr.common.enchant.LOTREnchantment.strong4,
            lotr.common.enchant.LOTREnchantment.meleeSpeed1,
            lotr.common.enchant.LOTREnchantment.meleeReach1
        };
        for (lotr.common.enchant.LOTREnchantment modifier : required) {
            assertTrue(modifier.enchantName + " cannot apply to "
                + weapon.getItem().getClass().getSimpleName(),
                modifier.canApply(weapon, false));
            assertTrue(modifier.enchantName + " conflicts on "
                + weapon.getItem().getClass().getSimpleName(),
                lotr.common.enchant.LOTREnchantmentHelper.checkEnchantCompatible(
                    weapon, modifier));
            lotr.common.enchant.LOTREnchantmentHelper.setHasEnchant(weapon, modifier);
        }
    }

    private static KOMEEmergencyDefenseCommitment activeCommitment(String conflict,
            String tile, String faction, String intent, UUID entityId) {
        Map<String, KOMEEmergencyDefenseCommitment.Defender> defenders =
            new LinkedHashMap<String, KOMEEmergencyDefenseCommitment.Defender>();
        defenders.put(intent, new KOMEEmergencyDefenseCommitment.Defender(intent,
            entityId, 20, 10L, KOMEEmergencyDefenseCommitment.Disposition.ACTIVE,
            false, "deployed"));
        return new KOMEEmergencyDefenseCommitment(conflict, tile, faction, 1L, 1L,
            2000L, "EDT-GONDOR", 20, 1, 2000L, 10L,
            KOMEEmergencyDefenseCommitment.State.ACTIVE, defenders, "test");
    }

    private static int homeTaskCount(LOTREntityNPC npc) {
        int count = 0;
        for (Object raw : npc.tasks.taskEntries)
            if (((EntityAITasks.EntityAITaskEntry) raw).action
                    instanceof KOMEEmergencyDefenseHomeAI) count++;
        return count;
    }

    private static KOMEEmergencyDefenseHomeAI homeTask(LOTREntityNPC npc) {
        for (Object raw : npc.tasks.taskEntries) {
            net.minecraft.entity.ai.EntityAIBase action =
                ((EntityAITasks.EntityAITaskEntry) raw).action;
            if (action instanceof KOMEEmergencyDefenseHomeAI)
                return (KOMEEmergencyDefenseHomeAI) action;
        }
        throw new AssertionError("Emergency Defense home task is missing.");
    }

    private static int countConfiguredArmor(
            KOMEEmergencyDefenseTemplateRegistry.Template template) {
        int count = 0;
        for (String field : template.armorFields) if (!field.isEmpty()) count++;
        return count;
    }

    private static void assertAppliedModifiersAreLegal(net.minecraft.item.ItemStack armor) {
        java.util.List<lotr.common.enchant.LOTREnchantment> applied =
            lotr.common.enchant.LOTREnchantmentHelper.getEnchantList(armor);
        for (lotr.common.enchant.LOTREnchantment modifier : applied)
            assertTrue(modifier.enchantName,
                modifier.canApply(new net.minecraft.item.ItemStack(armor.getItem()), false));
        for (int left = 0; left < applied.size(); left++)
            for (int right = left + 1; right < applied.size(); right++) {
                assertTrue(applied.get(left).isCompatibleWith(applied.get(right)));
                assertTrue(applied.get(right).isCompatibleWith(applied.get(left)));
            }
    }

    private static int finalizedProtection(lotr.common.item.LOTRMaterial material) {
        int protection = 0;
        for (int slot = 0; slot < 4; slot++) {
            net.minecraft.item.ItemStack armor = new net.minecraft.item.ItemStack(
                new lotr.common.item.LOTRItemArmor(material, slot));
            KOMEEmergencyDefenseTemplateRegistry.Template.applyArmorPolicy(
                armor, slot == 3);
            assertAppliedModifiersAreLegal(armor);
            protection += lotr.common.item.LOTRWeaponStats.getArmorProtection(armor);
        }
        return protection;
    }

    private static KOMEEmergencyDefenseMobilizationService.Plan plan(String conflict,
            String tile, String faction, int units, int cost, long basis) {
        Map<String, KOMEEmergencyDefenseCommitment.Defender> defenders =
            new LinkedHashMap<String, KOMEEmergencyDefenseCommitment.Defender>();
        for (int i = 1; i <= units; i++) {
            String intent = "ED-" + conflict.substring(2) + "-" + i;
            defenders.put(intent, new KOMEEmergencyDefenseCommitment.Defender(intent,
                UUID.nameUUIDFromBytes(intent.getBytes()), cost, 10L,
                KOMEEmergencyDefenseCommitment.Disposition.PENDING, false, ""));
        }
        long committed = (long) units * cost * KOMEPopulationService.CENTI_PER_POPULATION;
        return KOMEEmergencyDefenseMobilizationService.Plan.ready(
            new KOMEEmergencyDefenseCommitment(conflict, tile, faction, 1L, 2L,
                basis, "EDT-" + faction.toUpperCase(), cost, units, committed, 10L,
                units == 0 ? KOMEEmergencyDefenseCommitment.State.ACTIVE
                    : KOMEEmergencyDefenseCommitment.State.DEPLOYING,
                defenders, "test"));
    }

    private static KOMEWorldData world() {
        KOMEWorldData data = new KOMEWorldData("test"); data.initializeIntegratedWorld(); return data;
    }

    private static void createConflict(KOMEWorldData data, String tile, long now) {
        KOMEConflictService.Result result = data.getConflictService().start(tile,
            State.ORDINARY, ExpectedConflict.absent(), java.util.Collections.emptyList(),
            new Context(now, "test", "test"));
        assertEquals(Code.SUCCESS, result.code);
        result = data.getConflictService().beginFactionParticipation(tile,
            ExpectedConflict.at(result.record.getConflictId(), result.record.getRevision()),
            "gondor", new Context(now + 1L, "test", "defender"));
        assertEquals(Code.SUCCESS, result.code);
    }

    private static KOMEConflictRecord createAuthoritativeAttack(KOMEWorldData data,
            String tileId, String defender, String attacker, long now,
            boolean encirclement) {
        KOMEConquestTile tile = data.getConquestTile(tileId);
        tile.claim(defender, now);
        ValidatedCommitmentRequest request = new ValidatedCommitmentRequest(tileId,
            "C1", attacker, ValidatedConflictAuthority.defender(defender), now,
            KOMEConflictRecord.EntryOrigin.LEGAL_ARRIVAL, "M1", encirclement,
            java.util.Collections.<GarrisonParticipantSeed>emptyList(),
            ExpectedConflict.absent());
        KOMEConflictContracts.DetachmentResolver resolver =
            new KOMEConflictContracts.DetachmentResolver() {
                @Override public DetachmentResolution resolve(String ignored) {
                    return new DetachmentResolution("C1", ReferenceStatus.RESOLVED,
                        KOMEHiredUnitClass.CAMPAIGN, attacker, tileId, "test");
                }
            };
        KOMEConflictContracts.HostilityResolver hostile =
            new KOMEConflictContracts.HostilityResolver() {
                @Override public Hostility resolve(String first, String second) {
                    return Hostility.HOSTILE;
                }
            };
        KOMEConflictService.Result result = data.getConflictService()
            .acceptValidatedCommitment(request, hostile, resolver,
                new Context(now, "test", "hostile arrival"));
        assertEquals(Code.SUCCESS, result.code);
        return result.record;
    }

    private static void addDevelopedProduction(KOMEWorldData data, String tileId,
            String faction, int hours) {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = "ED-PRODUCTION-" + tileId;
        build.tileId = tileId;
        build.populationFaction = faction;
        build.type = KOMEBuildType.NORMAL;
        build.active = true;
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = "H-" + tileId;
        contribution.centiHours = Math.multiplyExact((long) hours, 50L);
        contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution);
        build.developedNativeCentiHours = contribution.centiHours;
        data.builds.put(build.id, build);
    }

    private static void relation(String first, String second,
            LOTRFactionRelations.Relation relation) {
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction(first),
            KOMEAlliance.findLotrFaction(second), relation);
    }

    private static NBTTagCompound save(KOMEWorldData data) {
        NBTTagCompound nbt = new NBTTagCompound(); data.writeToNBT(nbt); return nbt;
    }
}
