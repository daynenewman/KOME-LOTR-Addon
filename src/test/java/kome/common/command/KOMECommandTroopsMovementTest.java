package kome.common.command;

import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEConquestClaimService;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEDiplomacyRecord;
import kome.common.data.KOMEDiplomacyRelation;
import kome.common.data.KOMEDiplomacyService;
import kome.common.data.KOMEMovementAccessService;
import kome.common.data.KOMEMovementHistoryRecord;
import kome.common.data.KOMEMovementRecoveryOptions;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMEWarService;
import kome.common.network.KOMECompanyGuiEntry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KOMECommandTroopsMovementTest {
    @Test
    public void queuedAccessLossHaltsWithoutChangingStrategicProgress() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEArmyMovementOrder order = order("T001", "T002", "T003");
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.currentRouteIndex = 0;
        order.nextRouteIndex = 1;
        order.currentTile = "T001";
        order.nextTile = "T002";
        order.traveledRouteTiles.add("T001");
        claim(data, "T001", "gondor");
        claim(data, "T002", "rohan");
        data.armyMovements.put(order.id, order);

        boolean changed = KOMECommandTroops.processWaitingStepDepartures(data, null, 200L);

        assertTrue(changed);
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertEquals("T001", order.currentTile);
        assertEquals("T002", order.nextTile);
        assertEquals(0, order.currentRouteIndex);
        assertEquals(1, order.nextRouteIndex);
        assertEquals(1, order.traveledRouteTiles.size());
        assertTrue(order.accessLossReason.contains("T002"));
        assertEquals(200L, order.accessLostAtMillis);
    }

    @Test
    public void committedStepAccessLossPreservesDestinationAndMarksHaltAfterArrival() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEArmyMovementOrder order = order("T001", "T002", "T003");
        order.status = KOMEArmyMovementOrder.MOVING;
        order.currentRouteIndex = 0;
        order.nextRouteIndex = 1;
        order.currentTile = "T001";
        order.nextTile = "T002";
        order.currentStepOriginTile = "T001";
        order.currentStepDestinationTile = "T002";
        order.arrivalPointTileId = "T002";
        order.arrivalPointSource = "T002 Rally Point";
        order.arrivalDimension = 0;
        order.arrivalX = 10.5D;
        order.arrivalY = 64.0D;
        order.arrivalZ = 20.5D;
        order.traveledRouteTiles.add("T001");
        data.armyMovements.put(order.id, order);

        boolean changed = KOMEMovementAccessService.markCommittedStepForAccessLoss(data, order, 300L);

        assertTrue(changed);
        assertTrue(order.haltAfterArrival);
        assertEquals("PENDING", order.accessChoice);
        assertEquals("T002", order.currentStepDestinationTile);
        assertEquals("T002", order.arrivalPointTileId);
        assertEquals(10.5D, order.arrivalX, 0.0D);
        assertEquals(0, order.currentRouteIndex);
        assertEquals(1, order.nextRouteIndex);
        assertEquals(1, order.traveledRouteTiles.size());
        assertEquals(300L, order.accessLostAtMillis);
        assertFalse(order.accessLossReason.length() == 0);

        order.accessLossReason = "Original canonical denial";
        assertFalse(KOMEMovementAccessService.markCommittedStepForAccessLoss(data, order, 400L));
        assertEquals("Original canonical denial", order.accessLossReason);
        assertEquals(300L, order.accessLostAtMillis);
    }

    @Test
    public void committedAccessHaltStateRoundTripsThroughNbt() {
        KOMEArmyMovementOrder order = order("T001", "T002", "T003");
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.haltAfterArrival = true;
        order.accessChoice = "PENDING";
        order.accessLossReason = "Active war blocks military passage into T002.";
        order.accessLostAtMillis = 500L;
        order.currentTile = "T002";
        order.nextTile = "T003";
        order.currentStepOriginTile = "T002";
        order.currentStepDestinationTile = "T003";
        order.currentRouteIndex = 1;
        order.nextRouteIndex = 2;
        order.traveledRouteTiles.add("T001");
        order.traveledRouteTiles.add("T002");

        KOMEArmyMovementOrder restored = new KOMEArmyMovementOrder();
        restored.readFromNBT(order.writeToNBT());

        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, restored.status);
        assertTrue(restored.haltAfterArrival);
        assertEquals("PENDING", restored.accessChoice);
        assertEquals(order.accessLossReason, restored.accessLossReason);
        assertEquals(500L, restored.accessLostAtMillis);
        assertEquals("T002", restored.currentTile);
        assertEquals("T003", restored.nextTile);
        assertEquals(1, restored.currentRouteIndex);
        assertEquals(2, restored.nextRouteIndex);
        assertEquals(order.traveledRouteTiles, restored.traveledRouteTiles);
    }

    @Test
    public void resumeWhilePassageIsIllegalDoesNotMutateHaltedOrder() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEArmyMovementOrder order = order("T001", "T002", "T003");
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.haltAfterArrival = true;
        order.retreating = true;
        order.accessChoice = "PENDING";
        order.accessLossReason = "Canonical Allies passage was revoked.";
        order.accessLostAtMillis = 600L;
        order.currentTile = "T001";
        order.nextTile = "T002";
        order.currentStepOriginTile = "T001";
        order.currentStepDestinationTile = "";
        order.currentRouteIndex = 0;
        order.nextRouteIndex = 1;
        order.traveledRouteTiles.add("T001");
        order.nextStepDepartureMillis = 700L;
        order.nextStepAvailableMillis = 701L;
        order.arrivalMillis = 702L;
        claim(data, "T001", "gondor");
        claim(data, "T002", "rohan");

        try {
            new KOMECommandTroops().resumeAccessHaltedRoute(commandSender(), data, order, 800L);
            throw new AssertionError("Resume should be rejected while passage is illegal");
        } catch (WrongUsageException expected) {
            assertTrue(expected.getMessage().contains("still unauthorized"));
        }

        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertTrue(order.haltAfterArrival);
        assertTrue(order.retreating);
        assertEquals("PENDING", order.accessChoice);
        assertEquals("Canonical Allies passage was revoked.", order.accessLossReason);
        assertEquals(600L, order.accessLostAtMillis);
        assertEquals("T001", order.currentTile);
        assertEquals("T002", order.nextTile);
        assertEquals("T001", order.currentStepOriginTile);
        assertEquals("", order.currentStepDestinationTile);
        assertEquals(0, order.currentRouteIndex);
        assertEquals(1, order.nextRouteIndex);
        assertEquals(new ArrayList<String>(java.util.Arrays.asList("T001")), order.traveledRouteTiles);
        assertEquals(700L, order.nextStepDepartureMillis);
        assertEquals(701L, order.nextStepAvailableMillis);
        assertEquals(702L, order.arrivalMillis);
    }

    @Test
    public void resumeAfterCanonicalAlliesRestorationEntersWaitingPathWithoutAdvancing() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEArmyMovementOrder order = order("T001", "T002", "T003");
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.haltAfterArrival = true;
        order.accessChoice = "PENDING";
        order.accessLossReason = "Canonical Allies passage was revoked.";
        order.accessLostAtMillis = 600L;
        order.currentTile = "T001";
        order.nextTile = "T002";
        order.currentStepOriginTile = "T001";
        order.currentStepDestinationTile = "";
        order.currentRouteIndex = 0;
        order.nextRouteIndex = 1;
        order.traveledRouteTiles.add("T001");
        claim(data, "T001", "gondor");
        claim(data, "T002", "rohan");
        KOMEDiplomacyRecord relation = new KOMEDiplomacyRecord("gondor", "rohan");
        relation.relation = KOMEDiplomacyRelation.ALLIES;
        data.canonicalDiplomacyRecords.put(relation.key(), relation);

        new KOMECommandTroops().resumeAccessHaltedRoute(commandSender(), data, order, 800L);

        assertEquals(KOMEArmyMovementOrder.WAITING_NEXT_STEP, order.status);
        assertFalse(order.haltAfterArrival);
        assertFalse(order.retreating);
        assertEquals("RESUME", order.accessChoice);
        assertEquals("T002", order.currentStepDestinationTile);
        assertEquals("T002", order.nextTile);
        assertEquals(0, order.currentRouteIndex);
        assertEquals(1, order.nextRouteIndex);
        assertEquals(1, order.traveledRouteTiles.size());
        assertEquals(800L, order.nextStepDepartureMillis);
        assertEquals(800L, order.nextStepAvailableMillis);
    }

    @Test
    public void diplomacyLossRevalidatesQueuedMovementImmediately() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.ALLIES);
        KOMEArmyMovementOrder order = queuedOrder();
        data.armyMovements.put(order.id, order);
        setRelation(data, KOMEDiplomacyRelation.FRIENDS);

        assertTrue(KOMEMovementAccessService.revalidateAll(data, 900L));
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertEquals("T001", order.currentTile);
        assertEquals(0, order.currentRouteIndex);
        assertEquals(1, order.nextRouteIndex);
        assertEquals(1, order.traveledRouteTiles.size());
        assertEquals(900L, order.accessLostAtMillis);
        assertTrue(order.accessLossReason.contains("T002"));
    }

    @Test
    public void diplomacyLossMarksCommittedMovementWithoutRewind() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.ALLIES);
        KOMEArmyMovementOrder order = inFlightOrder();
        data.armyMovements.put(order.id, order);
        setRelation(data, KOMEDiplomacyRelation.FRIENDS);

        assertTrue(KOMEMovementAccessService.revalidateAll(data, 901L));
        assertTrue(order.haltAfterArrival);
        assertEquals("T002", order.currentStepDestinationTile);
        assertEquals("T002", order.arrivalPointTileId);
        assertEquals(10.5D, order.arrivalX, 0.0D);
        assertEquals(0, order.currentRouteIndex);
        assertEquals(1, order.nextRouteIndex);
        assertEquals(1, order.traveledRouteTiles.size());
        assertEquals(901L, order.accessLostAtMillis);
    }

    @Test
    public void warCreationRevalidatesQueuedAndCommittedMovementImmediately() {
        KOMEWorldData queuedData = new KOMEWorldData("test");
        setRelation(queuedData, KOMEDiplomacyRelation.ALLIES);
        KOMEArmyMovementOrder queued = queuedOrder();
        queuedData.armyMovements.put(queued.id, queued);
        assertTrue(KOMEWarService.createWar(queuedData, "gondor", "rohan", "Test War", "test", 902L) != null);
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, queued.status);
        assertEquals("T001", queued.currentTile);
        assertEquals(0, queued.currentRouteIndex);

        KOMEWorldData inFlightData = new KOMEWorldData("test");
        setRelation(inFlightData, KOMEDiplomacyRelation.ALLIES);
        KOMEArmyMovementOrder inFlight = inFlightOrder();
        inFlightData.armyMovements.put(inFlight.id, inFlight);
        assertTrue(KOMEWarService.createWar(inFlightData, "gondor", "rohan", "Test War", "test", 903L) != null);
        assertTrue(inFlight.haltAfterArrival);
        assertEquals("T002", inFlight.currentStepDestinationTile);
        assertEquals("T002", inFlight.arrivalPointTileId);
        assertEquals(903L, inFlight.accessLostAtMillis);
    }

    @Test
    public void hostileCaptureOnExistingWarRevalidatesAfterFinalCaptureState() {
        KOMEWorldData data = new KOMEWorldData("test");
        claim(data, "T001", "rohan");
        claim(data, "T002", "mordor");
        assertTrue(KOMEWarService.createWar(data, "rohan", "mordor", "Existing War", "test", 910L) != null);

        KOMEArmyMovementOrder order = order("T001", "T002", "T003");
        order.ownerFaction = "rohan";
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.currentRouteIndex = 0;
        order.nextRouteIndex = 1;
        order.currentTile = "T001";
        order.nextTile = "T002";
        order.currentStepOriginTile = "T001";
        order.currentStepDestinationTile = "T002";
        order.traveledRouteTiles.add("T001");
        data.armyMovements.put(order.id, order);

        KOMEWarService.recordHostileCapture(data, "T003", "rohan", "mordor",
            UUID.randomUUID(), "Capture", 911L, "TEST_CAPTURE");

        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertTrue(order.accessLossReason.contains("T002"));
        assertEquals("T001", order.currentTile);
        assertEquals(0, order.currentRouteIndex);
        assertEquals(1, order.nextRouteIndex);
    }

    @Test
    public void captureCreatedWarRevalidatesOnlyAfterFinalCaptureState() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.ALLIES);
        KOMEArmyMovementOrder order = queuedOrder();
        data.armyMovements.put(order.id, order);

        KOMEWarService.recordHostileCapture(data, "T002", "rohan", "gondor",
            UUID.randomUUID(), "Capture", 920L, "TEST_CAPTURE");

        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertEquals(920L, order.accessLostAtMillis);
        assertTrue(order.accessLossReason.contains("T002"));
        assertEquals("T001", order.currentTile);
        assertEquals(0, order.currentRouteIndex);
        assertEquals(1, order.nextRouteIndex);
    }

    @Test
    public void restoringAlliesDoesNotAutoResumeHaltedMovement() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.FRIENDS);
        KOMEArmyMovementOrder order = inFlightOrder();
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.haltAfterArrival = true;
        order.accessChoice = "PENDING";
        data.armyMovements.put(order.id, order);
        setRelation(data, KOMEDiplomacyRelation.ALLIES);

        assertFalse(KOMEMovementAccessService.revalidateAll(data, 904L));
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertTrue(order.haltAfterArrival);
    }

    @Test
    public void repeatedCommittedRevalidationPreservesOriginalLossMetadata() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.FRIENDS);
        KOMEArmyMovementOrder order = inFlightOrder();
        data.armyMovements.put(order.id, order);

        assertTrue(KOMEMovementAccessService.revalidateAll(data, 905L));
        String reason = order.accessLossReason;
        assertFalse(KOMEMovementAccessService.revalidateAll(data, 906L));
        assertEquals(reason, order.accessLossReason);
        assertEquals(905L, order.accessLostAtMillis);
    }

    @Test
    public void pendingDiplomacyRequestHasNoMovementEffectButAcceptedIncreaseRevalidates() {
        KOMEWorldData data = new KOMEWorldData("test");
        claim(data, "T001", "gondor");
        claim(data, "T002", "rohan");
        KOMEArmyMovementOrder order = queuedOrder();
        data.armyMovements.put(order.id, order);
        UUID gondorKing = crown(data, "gondor", "Gondor King");
        UUID rohanKing = crown(data, "rohan", "Rohan King");

        assertTrue(KOMEDiplomacyService.requestIncrease(data, "gondor", "rohan",
            KOMEDiplomacyRelation.FRIENDS, gondorKing, 906L).accepted);
        assertEquals(KOMEDiplomacyRelation.NEUTRAL,
            KOMEDiplomacyService.getRelation(data, "gondor", "rohan"));
        assertEquals(KOMEArmyMovementOrder.WAITING_NEXT_STEP, order.status);
        assertEquals("", order.accessLossReason);
        assertEquals(0L, order.accessLostAtMillis);

        assertTrue(KOMEDiplomacyService.acceptPendingIncrease(
            data, "rohan", "gondor", rohanKing, 907L).accepted);
        assertEquals(KOMEDiplomacyRelation.FRIENDS,
            KOMEDiplomacyService.getRelation(data, "gondor", "rohan"));
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertTrue(order.accessLossReason.contains("T002"));
    }

    @Test
    public void runtimeOwnershipResetRevalidatesQueuedMovementWithoutChangingProgress() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.FRIENDS);
        KOMEArmyMovementOrder order = queuedOrder();
        data.armyMovements.put(order.id, order);
        data.conquestTiles.get("T001").defaultRulingFaction = "gondor";
        data.conquestTiles.get("T002").defaultRulingFaction = "mordor";

        assertTrue(data.resetConquestOwnershipToDefaults(1000L) > 0);
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertEquals("T001", order.currentTile);
        assertEquals(0, order.currentRouteIndex);
        assertEquals(1, order.nextRouteIndex);
        assertEquals(1, order.traveledRouteTiles.size());
        assertTrue(order.accessLossReason.contains("T002"));
        assertTrue(order.accessLostAtMillis > 0L);
    }

    @Test
    public void completedPlayerClaimRevalidatesQueuedMovement() {
        KOMEWorldData data = new KOMEWorldData("test");
        claim(data, "T001", "gondor");
        claim(data, "T002", "rohan");
        KOMEArmyMovementOrder order = queuedOrder();
        data.armyMovements.put(order.id, order);

        KOMEConquestClaimService.Result result = KOMEConquestClaimService.claim(
            data, data.conquestTiles.get("T002"), "mordor", UUID.randomUUID(), "Claimant", 0L, 920L);

        assertTrue(result.success);
        assertEquals("mordor", data.conquestTiles.get("T002").currentRulingFaction());
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertEquals("T001", order.currentTile);
        assertEquals(0, order.currentRouteIndex);
        assertEquals(1, order.nextRouteIndex);
        assertTrue(order.accessLossReason.contains("T002"));
    }

    @Test
    public void ownershipRestorationDoesNotAutoResumeAccessHaltedMovement() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.FRIENDS);
        KOMEArmyMovementOrder order = queuedOrder();
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.haltAfterArrival = true;
        order.accessChoice = "PENDING";
        data.armyMovements.put(order.id, order);

        data.conquestTiles.get("T002").claim("gondor", 1001L);
        assertFalse(KOMEMovementAccessService.revalidateAll(data, 1002L));
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertTrue(order.haltAfterArrival);
    }

    @Test
    public void restartRevalidatesQueuedMovementAfterAllStateIsLoaded() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.FRIENDS);
        KOMEArmyMovementOrder order = queuedOrder();
        data.armyMovements.put(order.id, order);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("restored");
        restored.readFromNBT(saved);

        KOMEArmyMovementOrder loaded = restored.armyMovements.get(order.id);
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, loaded.status);
        assertEquals("T001", loaded.currentTile);
        assertEquals(0, loaded.currentRouteIndex);
        assertEquals(1, loaded.nextRouteIndex);
        assertEquals(order.traveledRouteTiles, loaded.traveledRouteTiles);
        assertTrue(loaded.accessLossReason.contains("T002"));
        assertTrue(loaded.accessLostAtMillis > 0L);
    }

    @Test
    public void restartRevalidatesCommittedMovementWithoutRewindingOrDiscardingRetryState() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.FRIENDS);
        KOMEArmyMovementOrder order = inFlightOrder();
        order.status = KOMEArmyMovementOrder.PENDING_SPAWN;
        order.pendingSpawnReason = "Chunk retry";
        order.spawnAttemptCount = 3;
        order.nextSpawnRetryMillis = 1200L;
        order.spawnRetryPaused = true;
        data.armyMovements.put(order.id, order);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("restored");
        restored.readFromNBT(saved);

        KOMEArmyMovementOrder loaded = restored.armyMovements.get(order.id);
        assertEquals(KOMEArmyMovementOrder.PENDING_SPAWN, loaded.status);
        assertTrue(loaded.haltAfterArrival);
        assertEquals("T002", loaded.currentStepDestinationTile);
        assertEquals("T002", loaded.arrivalPointTileId);
        assertEquals(10.5D, loaded.arrivalX, 0.0D);
        assertEquals(3, loaded.spawnAttemptCount);
        assertEquals(1200L, loaded.nextSpawnRetryMillis);
        assertTrue(loaded.spawnRetryPaused);
        assertEquals(0, loaded.currentRouteIndex);
        assertEquals(1, loaded.nextRouteIndex);
        assertEquals(order.traveledRouteTiles, loaded.traveledRouteTiles);
    }

    @Test
    public void restartKeepsAlreadyHaltedMovementHaltedWhenPassageIsRestored() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.ALLIES);
        KOMEArmyMovementOrder order = queuedOrder();
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.haltAfterArrival = true;
        order.accessChoice = "PENDING";
        order.accessLossReason = "Previous canonical denial";
        order.accessLostAtMillis = 1300L;
        data.armyMovements.put(order.id, order);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("restored");
        restored.readFromNBT(saved);

        KOMEArmyMovementOrder loaded = restored.armyMovements.get(order.id);
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, loaded.status);
        assertTrue(loaded.haltAfterArrival);
        assertEquals("Previous canonical denial", loaded.accessLossReason);
        assertEquals(1300L, loaded.accessLostAtMillis);
    }

    @Test
    public void retreatProjectionUsesOnlyNearestEarlierRecordedLegalTile() {
        KOMEWorldData data = new KOMEWorldData("test");
        claim(data, "T001", "gondor");
        claim(data, "T002", "mordor");
        claim(data, "T003", "gondor");
        claim(data, "T004", "rohan");
        KOMEArmyMovementOrder order = order("T001", "T002", "T003", "T004");
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.currentTile = "T003";
        order.nextTile = "T004";
        order.currentStepOriginTile = "T003";
        order.currentStepDestinationTile = "T004";
        order.currentRouteIndex = 2;
        order.nextRouteIndex = 3;
        order.traveledRouteTiles.add("T001");
        order.traveledRouteTiles.add("T002");
        order.traveledRouteTiles.add("T003");

        KOMEMovementRecoveryOptions options = KOMEMovementRecoveryOptions.forOrder(data, order);

        assertTrue(options.canRetreat);
        assertEquals("T001", options.retreatTargetTile);
        assertEquals(0, options.retreatRouteIndex);
        assertTrue(KOMEMovementAccessService.isMovementStepAuthorized(data, order, "T003", "T002", true));
        assertFalse(KOMEMovementAccessService.isMovementStepAuthorized(data, order, "T003", "T004", false));

        data.armyMovements.put(order.id, order);
        new KOMECommandTroops().beginRetreat(commandSender(), data, order, 1500L);
        assertEquals(KOMEArmyMovementOrder.WAITING_NEXT_STEP, order.status);
        assertEquals("T001", order.destinationTile);
        assertEquals(KOMEMovementHistoryRecord.ACTIVE, data.movementHistory.get(order.id).status);
    }

    @Test
    public void noLegalRetreatTargetLeavesHaltedOrderUntouched() {
        KOMEWorldData data = new KOMEWorldData("test");
        claim(data, "T001", "mordor");
        claim(data, "T002", "mordor");
        claim(data, "T003", "gondor");
        KOMEArmyMovementOrder order = order("T001", "T002", "T003", "T004");
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.currentTile = "T003";
        order.nextTile = "T004";
        order.currentRouteIndex = 2;
        order.nextRouteIndex = 3;
        order.traveledRouteTiles.add("T001");
        order.traveledRouteTiles.add("T002");
        order.traveledRouteTiles.add("T003");
        data.armyMovements.put(order.id, order);

        KOMEMovementRecoveryOptions options = KOMEMovementRecoveryOptions.forOrder(data, order);

        assertFalse(options.canRetreat);
        assertTrue(options.retreatBlockedReason.contains("recorded traveled route"));
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertEquals("T003", order.currentTile);
        assertEquals(2, order.currentRouteIndex);
        assertEquals(3, order.nextRouteIndex);
    }

    @Test
    public void recoveryProjectionSeparatesResumeFromRetreatCorridorAccess() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.FRIENDS);
        KOMEArmyMovementOrder order = inFlightOrder();
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.haltAfterArrival = true;
        data.armyMovements.put(order.id, order);

        KOMEMovementRecoveryOptions blocked = KOMEMovementRecoveryOptions.forOrder(data, order);
        assertFalse(blocked.canResume);
        assertTrue(blocked.resumeBlockedReason.contains("Military passage lost"));

        setRelation(data, KOMEDiplomacyRelation.ALLIES);
        KOMEMovementRecoveryOptions restored = KOMEMovementRecoveryOptions.forOrder(data, order);
        assertTrue(restored.canResume);
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertTrue(order.haltAfterArrival);
    }

    @Test
    public void recoveryProjectionSerializesPlayerFacingAccessChoices() {
        KOMECompanyGuiEntry sent = new KOMECompanyGuiEntry();
        sent.canStay = true;
        sent.canRetreat = true;
        sent.canResume = false;
        sent.accessLossReason = "Military passage lost before entering T004 (owner Rohan).";
        sent.resumeBlockedReason = "The route is still unauthorized: canonical denial.";
        sent.retreatTargetTile = "T001";
        sent.currentTile = "T003";
        sent.nextTile = "T004";
        sent.intendedDestinationTile = "T004";
        sent.retreatBlockedReason = "";
        ByteBuf bytes = Unpooled.buffer();
        sent.toBytes(bytes);
        KOMECompanyGuiEntry received = new KOMECompanyGuiEntry();
        received.fromBytes(bytes);

        assertTrue(received.canStay);
        assertTrue(received.canRetreat);
        assertFalse(received.canResume);
        assertEquals(sent.accessLossReason, received.accessLossReason);
        assertEquals(sent.resumeBlockedReason, received.resumeBlockedReason);
        assertEquals("T001", received.retreatTargetTile);
        assertEquals("T003", received.currentTile);
        assertEquals("T004", received.nextTile);
        assertEquals("T004", received.intendedDestinationTile);
    }

    @Test
    public void warEndedHaltedProjectionRemainsRetreatOnly() {
        KOMEWorldData data = new KOMEWorldData("test");
        claim(data, "T001", "gondor");
        claim(data, "T002", "rohan");
        setRelation(data, KOMEDiplomacyRelation.ALLIES);
        KOMEArmyMovementOrder order = order("T001", "T002", "T003");
        order.status = KOMEArmyMovementOrder.WAR_ENDED_HALTED;
        order.currentTile = "T002";
        order.traveledRouteTiles.add("T001");
        order.traveledRouteTiles.add("T002");
        order.currentRouteIndex = 1;
        order.nextRouteIndex = 2;

        KOMEMovementRecoveryOptions options = KOMEMovementRecoveryOptions.forOrder(data, order);

        assertFalse(options.canStay);
        assertTrue(options.canRetreat);
        assertEquals("T001", options.retreatTargetTile);
        assertFalse(options.canResume);
        assertTrue(options.resumeBlockedReason.contains("retreat only"));
    }

    @Test
    public void finalRouteHaltDoesNotProjectOrAllowResume() {
        KOMEWorldData data = new KOMEWorldData("test");
        claim(data, "T001", "gondor");
        claim(data, "T002", "gondor");
        claim(data, "T003", "gondor");
        KOMEArmyMovementOrder order = order("T001", "T002", "T003");
        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
        order.haltAfterArrival = true;
        order.currentTile = "T003";
        order.nextTile = "";
        order.currentStepDestinationTile = "";
        order.currentRouteIndex = 2;
        order.nextRouteIndex = 2;
        order.finalRouteIndex = 2;
        order.traveledRouteTiles.add("T001");
        order.traveledRouteTiles.add("T002");
        order.traveledRouteTiles.add("T003");
        data.armyMovements.put(order.id, order);

        KOMEMovementRecoveryOptions options = KOMEMovementRecoveryOptions.forOrder(data, order);
        assertFalse(options.canResume);
        assertTrue(options.resumeBlockedReason.contains("No remaining forward route"));

        try {
            new KOMECommandTroops().resumeAccessHaltedRoute(commandSender(), data, order, 1600L);
            throw new AssertionError("Final-route Resume should be rejected");
        } catch (WrongUsageException expected) {
            assertTrue(expected.getMessage().contains("No remaining forward route"));
        }
        assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
        assertTrue(order.haltAfterArrival);
        assertEquals("T003", order.currentTile);
        assertEquals(2, order.currentRouteIndex);
        assertEquals(2, order.nextRouteIndex);
        assertEquals(3, order.traveledRouteTiles.size());
    }

    @Test
    public void movementHistoryTracksAccessHaltAndSuccessfulResume() {
        KOMEWorldData data = new KOMEWorldData("test");
        setRelation(data, KOMEDiplomacyRelation.FRIENDS);
        KOMEArmyMovementOrder order = queuedOrder();
        data.armyMovements.put(order.id, order);

        KOMEMovementAccessService.haltForAccessLoss(data, order, 1400L, "Canonical passage denied.");
        assertEquals(KOMEMovementHistoryRecord.FAILED, data.movementHistory.get(order.id).status);

        setRelation(data, KOMEDiplomacyRelation.ALLIES);
        new KOMECommandTroops().resumeAccessHaltedRoute(commandSender(), data, order, 1401L);
        assertEquals(KOMEMovementHistoryRecord.ACTIVE, data.movementHistory.get(order.id).status);
    }

    private static KOMEArmyMovementOrder order(String... route) {
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = "MOVE-1";
        order.ownerFaction = "gondor";
        for (String tile : route) {
            order.routeTiles.add(tile);
        }
        order.destinationTile = route[route.length - 1];
        order.finalDestinationTile = order.destinationTile;
        order.distanceTiles = route.length - 1;
        order.totalSteps = route.length - 1;
        return order;
    }

    private static KOMEArmyMovementOrder queuedOrder() {
        KOMEArmyMovementOrder order = order("T001", "T002", "T003");
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.currentRouteIndex = 0;
        order.nextRouteIndex = 1;
        order.currentTile = "T001";
        order.nextTile = "T002";
        order.currentStepOriginTile = "T001";
        order.currentStepDestinationTile = "T002";
        order.traveledRouteTiles.add("T001");
        return order;
    }

    private static KOMEArmyMovementOrder inFlightOrder() {
        KOMEArmyMovementOrder order = order("T001", "T002", "T003");
        order.status = KOMEArmyMovementOrder.MOVING;
        order.currentRouteIndex = 0;
        order.nextRouteIndex = 1;
        order.currentTile = "T001";
        order.nextTile = "T002";
        order.currentStepOriginTile = "T001";
        order.currentStepDestinationTile = "T002";
        order.arrivalPointTileId = "T002";
        order.arrivalPointSource = "T002 Rally Point";
        order.arrivalDimension = 0;
        order.arrivalX = 10.5D;
        order.arrivalY = 64.0D;
        order.arrivalZ = 20.5D;
        order.traveledRouteTiles.add("T001");
        return order;
    }

    private static void claim(KOMEWorldData data, String id, String faction) {
        KOMEConquestTile tile = new KOMEConquestTile(id);
        tile.claim(faction, 0L);
        data.conquestTiles.put(tile.id, tile);
    }

    private static UUID crown(KOMEWorldData data, String faction, String name) {
        UUID king = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(king, faction);
        assertTrue(data.claimFactionKing(faction, faction, king, name));
        return king;
    }

    private static void setRelation(KOMEWorldData data, KOMEDiplomacyRelation relation) {
        claim(data, "T001", "gondor");
        claim(data, "T002", "rohan");
        KOMEDiplomacyRecord record = new KOMEDiplomacyRecord("gondor", "rohan");
        record.relation = relation;
        data.canonicalDiplomacyRecords.put(record.key(), record);
    }

    private static ICommandSender commandSender() {
        return (ICommandSender) Proxy.newProxyInstance(
            KOMECommandTroopsMovementTest.class.getClassLoader(),
            new Class<?>[] {ICommandSender.class},
            (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0.0F;
        if (type == double.class) return 0.0D;
        if (type == char.class) return '\0';
        return null;
    }
}
