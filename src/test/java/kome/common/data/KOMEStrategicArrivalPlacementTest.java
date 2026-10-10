package kome.common.data;

import java.lang.reflect.Method;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import static kome.common.data.KOMEStrategicArrivalPlacement.*;
import static org.junit.Assert.*;

public class KOMEStrategicArrivalPlacementTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    @Before public void config() throws Exception { config = new KOMEPopulationTestConfig(); }
    @After public void cleanup() throws Exception {
        config.close();
        relation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        relation("rohan", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        relation("rohan", "gondor", LOTRFactionRelations.Relation.NEUTRAL);
    }
    @Test public void defaultOrdinaryDecisionDoesNotMutateIdentityOrCredit() {
        KOMEConflictMovementServiceTest.Fixture f = KOMEConflictMovementServiceTest.hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation prepared = f.prepare("M1", 10L);
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        assertTrue(preflight(f.data, order, "T101", prepared.receipt).ordinaryAllowed());
        assertEquals("M1:ARRIVAL:1:10", order.strategicArrival.eventId);
        assertEquals("C1", order.strategicArrival.companyId); assertEquals("gondor", order.strategicArrival.factionId);
        assertEquals("T101", order.strategicArrival.tileId); assertEquals(10L, order.strategicArrival.acceptedAtMillis);
        assertEquals(0, f.data.armyCompanies.get("C1").movementAllowance);
        assertEquals("T100", f.data.armyCompanies.get("C1").currentTile);
        assertNull(f.data.getConflictService().get("T101"));
    }
    @Test public void exteriorPolicyIsCalledBeforePhysicalPlacementAndNeverFallsBackInside() {
        KOMEConflictMovementServiceTest.Fixture f = KOMEConflictMovementServiceTest.hostileFixture(false, false);
        f.prepare("M1", 10L); KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        int units = f.data.hiredUnits.size(); final int[] calls = {0};
        f.data.setStrategicArrivalPlacementProvider(event -> {
            calls[0]++; assertEquals("T100", f.data.armyCompanies.get("C1").currentTile);
            assertEquals(0, order.spawnAttemptCount); assertEquals(0L, order.lastChunkLoadAttemptMillis);
            assertEquals("C1", event.companyId); assertEquals("T101", event.tileId);
            return Directive.EXTERIOR_REQUIRED;
        });
        kome.common.command.KOMECommandTroops.processArrivals(f.data, null, 10L, false);
        assertEquals(1, calls[0]); assertEquals(KOMEArmyMovementOrder.PENDING_SPAWN, order.status);
        assertEquals("EXTERIOR_PLACEMENT_UNAVAILABLE", order.lastSpawnFailureCode);
        assertEquals(0, order.spawnAttemptCount); assertEquals(0L, order.lastChunkLoadAttemptMillis);
        assertEquals(units, f.data.hiredUnits.size()); assertNull(f.data.getConflictService().get("T101"));
        assertEquals(0, order.currentRouteIndex);
        f.data.setStrategicArrivalPlacementProvider(ORDINARY);
        assertEquals(Code.EXTERIOR_PLACEMENT_UNAVAILABLE, preflight(f.data, order, "T101", f.prepare("M1", 10L).receipt).code);
    }
    @Test public void pendingExteriorSurvivesRootRestartWithoutProviderOrDuplicateCommitment() {
        KOMEConflictMovementServiceTest.Fixture f = KOMEConflictMovementServiceTest.hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation prepared = f.prepare("M1", 10L);
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        f.data.setStrategicArrivalPlacementProvider(event -> Directive.EXTERIOR_REQUIRED);
        assertEquals(Code.EXTERIOR_PLACEMENT_UNAVAILABLE, preflight(f.data, order, "T101", prepared.receipt).code);
        NBTTagCompound root = new NBTTagCompound(); f.data.writeToNBT(root);
        KOMEWorldData loaded = new KOMEWorldData("restart"); loaded.readFromNBT(root);
        KOMEArmyMovementOrder restored = loaded.armyMovements.get("M1");
        KOMEConflictMovementService.ArrivalPreparation retry = KOMEConflictMovementService.prepareLegalArrival(loaded, restored, "T101");
        assertTrue(retry.reason, retry.ready());
        assertEquals(prepared.receipt.movementOrderId, retry.receipt.movementOrderId);
        assertEquals(prepared.receipt.acceptedAtMillis, retry.receipt.acceptedAtMillis);
        loaded.setDirty(false);
        for (int i = 0; i < 3; i++) assertEquals(Code.EXTERIOR_PLACEMENT_UNAVAILABLE,
            preflight(loaded, restored, "T101", retry.receipt).code);
        assertEquals(order.strategicArrival.eventId, restored.strategicArrival.eventId);
        assertEquals(f.data.hiredUnits.keySet(), loaded.hiredUnits.keySet());
        assertNull(loaded.getConflictService().get("T101")); assertFalse(loaded.isDirty());
        assertEquals(0, loaded.armyCompanies.get("C1").movementAllowance);
    }
    @Test public void originalCohortsAndLaterReliefAreDistinctAuthoritativeFacts() {
        KOMEConflictMovementServiceTest.Fixture f = KOMEConflictMovementServiceTest.hostileFixture(true, true);
        KOMEConflictMovementService.ArrivalPreparation original = f.prepare("M1", 10L);
        Decision creation = preflight(f.data, f.data.armyMovements.get("M1"), "T101", original.receipt);
        assertTrue(creation.context.createsConflict);
        assertEquals(java.util.Collections.singletonList("C2"), creation.context.originalGarrisonIds);
        assertEquals(KOMEConflictRecord.EntryOrigin.LEGAL_ARRIVAL, creation.context.origin);
        try { creation.context.originalGarrisonIds.clear(); fail(); } catch (UnsupportedOperationException expected) { }
        f.publish("M1", "T101"); KOMEConflictRecord conflict = f.commit(original);
        assertEquals(KOMEConflictRecord.EntryOrigin.ORIGINAL_GARRISON, conflict.getCommitments().get("C2").origin);
        relation("rohan", "mordor", LOTRFactionRelations.Relation.ALLY);
        relation("rohan", "gondor", LOTRFactionRelations.Relation.ENEMY);
        f.company("C3", "rohan", "T100"); f.order("M3", "C3", "T100", "T101");
        KOMEConflictMovementService.ArrivalPreparation relief = f.prepare("M3", 20L);
        assertTrue(relief.reason, relief.ready());
        final Context[] observed = {null};
        f.data.setStrategicArrivalPlacementProvider(event -> { observed[0] = event; return Directive.EXTERIOR_REQUIRED; });
        assertEquals(Code.EXTERIOR_PLACEMENT_UNAVAILABLE, preflight(f.data, f.data.armyMovements.get("M3"), "T101", relief.receipt).code);
        assertEquals(KOMEConflictRecord.EntryOrigin.RELIEF, observed[0].origin);
        assertFalse(observed[0].createsConflict); assertTrue(observed[0].originalGarrisonIds.isEmpty());
        assertEquals(conflict.getConflictId(), observed[0].conflictId);
        assertEquals(conflict.getRevision(), observed[0].conflictRevision);
    }
    @Test public void ordinaryFriendlyArrivalStillCarriesServerReliefClassification() {
        KOMEConflictMovementServiceTest.Fixture f = KOMEConflictMovementServiceTest.hostileFixture(true, true);
        KOMEConflictMovementService.ArrivalPreparation original = f.prepare("M1", 10L);
        f.publish("M1", "T101"); f.commit(original);
        relation("rohan", "mordor", LOTRFactionRelations.Relation.ALLY);
        f.company("C3", "rohan", "T100"); KOMEArmyMovementOrder order = f.order("M3", "C3", "T100", "T101");
        order.hostileAttackDestination = ""; order.arrivalMillis = 20L;
        assertEquals(KOMEConflictRecord.EntryOrigin.RELIEF, preflight(f.data, order, "T101", null).context.origin);
    }
    @Test public void providerFailureOrNullDecisionFailsClosedBeforePlacement() {
        KOMEConflictMovementServiceTest.Fixture f = KOMEConflictMovementServiceTest.hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation original = f.prepare("M1", 10L);
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        f.data.setStrategicArrivalPlacementProvider(event -> null);
        assertEquals(Code.POLICY_UNAVAILABLE, preflight(f.data, order, "T101", original.receipt).code);
        f.data.setStrategicArrivalPlacementProvider(event -> { throw new IllegalStateException("unavailable"); });
        assertEquals(Code.POLICY_UNAVAILABLE, preflight(f.data, order, "T101", original.receipt).code);
        assertNull(f.data.getConflictService().get("T101")); assertEquals(0, order.currentRouteIndex);
    }
    @Test public void pendingIdentitySurvivesButClassificationRefreshesWhenConflictBegins() {
        KOMEConflictMovementServiceTest.Fixture f = KOMEConflictMovementServiceTest.hostileFixture(true, true);
        relation("rohan", "mordor", LOTRFactionRelations.Relation.ALLY);
        relation("rohan", "gondor", LOTRFactionRelations.Relation.ENEMY);
        f.company("C3", "rohan", "T100"); KOMEArmyMovementOrder later = f.order("M3", "C3", "T100", "T101");
        later.arrivalMillis = 5L;
        Context before = preflight(f.data, later, "T101", null).context;
        assertEquals(KOMEConflictRecord.EntryOrigin.LEGAL_ARRIVAL, before.origin);
        assertEquals("", before.conflictId);
        KOMEConflictMovementService.ArrivalPreparation attack = f.prepare("M1", 10L);
        f.publish("M1", "T101"); KOMEConflictRecord conflict = f.commit(attack);
        f.data.setStrategicArrivalPlacementProvider(event -> event.origin == KOMEConflictRecord.EntryOrigin.RELIEF
            ? Directive.EXTERIOR_REQUIRED : Directive.ORDINARY);
        Decision after = preflight(f.data, later, "T101", null);
        assertEquals(before.eventId, after.context.eventId);
        assertEquals(5L, after.context.acceptedAtMillis);
        assertEquals(KOMEConflictRecord.EntryOrigin.RELIEF, after.context.origin);
        assertEquals(conflict.getConflictId(), after.context.conflictId);
        assertEquals(Code.EXTERIOR_PLACEMENT_UNAVAILABLE, after.code);
        assertFalse(conflict.getCommitments().containsKey("C3"));
    }
    @Test public void callerCannotInjectDetachedOrderReceiptOrDestinationIdentity() {
        KOMEConflictMovementServiceTest.Fixture f = KOMEConflictMovementServiceTest.hostileFixture(false, false);
        KOMEConflictMovementService.ArrivalPreparation original = f.prepare("M1", 10L);
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        KOMEArmyMovementOrder claim = new KOMEArmyMovementOrder(); claim.readFromNBT(order.writeToNBT());
        assertEquals(Code.INVALID_ARRIVAL, preflight(f.data, claim, "T101", original.receipt).code);
        assertEquals(Code.INVALID_ARRIVAL, preflight(f.data, order, "T102", original.receipt).code);
        f.company("C9", "gondor", "T100"); f.order("M9", "C9", "T100", "T101");
        assertEquals(Code.INVALID_ARRIVAL, preflight(f.data, f.data.armyMovements.get("M9"), "T101", original.receipt).code);
        assertNull(order.strategicArrival);
    }
    @Test public void eventSchemaIsStrictAndDecodeIsAtomic() {
        KOMEConflictMovementServiceTest.Fixture f = KOMEConflictMovementServiceTest.hostileFixture(false, false);
        preflight(f.data, f.data.armyMovements.get("M1"), "T101", f.prepare("M1", 10L).receipt);
        NBTTagCompound tag = f.data.armyMovements.get("M1").writeToNBT();
        tag.getCompoundTag("StrategicArrival").setString("Origin", "CLIENT_RELIEF");
        try { new KOMEArmyMovementOrder().readFromNBT(tag); fail(); } catch (IllegalArgumentException expected) { }
        NBTTagCompound root = new NBTTagCompound(); f.data.writeToNBT(root);
        root.getTagList("ArmyMovements", 10).getCompoundTagAt(0).getCompoundTag("StrategicArrival").setInteger("SchemaVersion", 99);
        KOMEWorldData live = new KOMEWorldData("live"); live.playerNames.put(new java.util.UUID(1, 1), "retained");
        NBTTagCompound original = (NBTTagCompound) root.copy();
        try { live.readFromNBT(root); fail(); } catch (RuntimeException expected) { }
        assertEquals(original, root); assertEquals("retained", live.playerNames.get(new java.util.UUID(1, 1)));
        assertTrue(live.armyMovements.isEmpty());
    }
    @Test public void normalPhysicalPipelineCompletesAndClearsEventOnlyForNextAcceptedLeg() throws Exception {
        KOMEWorldData data = new KOMEWorldData("ordinary");
        Class<?> fixture = kome.common.command.KOMECampaignBoundaryMovementTest.class;
        Method route = fixture.getDeclaredMethod("route", KOMEWorldData.class, int.class); route.setAccessible(true);
        Method world = fixture.getDeclaredMethod("world"); world.setAccessible(true);
        KOMEArmyMovementOrder order = (KOMEArmyMovementOrder) route.invoke(null, data, 1);
        final int[] calls = {0}; data.setStrategicArrivalPlacementProvider(event -> {
            calls[0]++; assertEquals(0, order.currentRouteIndex); assertEquals(0, order.spawnAttemptCount);
            return Directive.ORDINARY;
        });
        kome.common.command.KOMECommandTroops.processMovementTick(data, (net.minecraft.world.World) world.invoke(null), 10L);
        assertEquals(1, calls[0]); assertEquals(KOMEArmyMovementOrder.ARRIVED, order.status);
        assertEquals("T002", data.armyCompanies.get(order.companyId).currentTile);
        assertEquals(0, data.armyCompanies.get(order.companyId).movementAllowance);
        assertNotNull(order.strategicArrival);
        NBTTagCompound root = new NBTTagCompound(); data.writeToNBT(root);
        assertEquals(KOMEWorldData.KOME_DATA_SCHEMA_VERSION, root.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertFalse(root.toString().contains("strategicArrivalPlacementProvider"));
    }
    private static void relation(String first, String second, LOTRFactionRelations.Relation value) {
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction(first), KOMEAlliance.findLotrFaction(second), value);
    }
}
