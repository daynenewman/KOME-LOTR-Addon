package kome.common.command;

import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEDiplomacyRecord;
import kome.common.data.KOMEDiplomacyRelation;
import kome.common.data.KOMEWorldData;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;

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

        boolean changed = KOMECommandTroops.markCommittedStepForAccessLoss(data, order, 300L);

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
        assertFalse(KOMECommandTroops.markCommittedStepForAccessLoss(data, order, 400L));
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

    private static void claim(KOMEWorldData data, String id, String faction) {
        KOMEConquestTile tile = new KOMEConquestTile(id);
        tile.claim(faction, 0L);
        data.conquestTiles.put(tile.id, tile);
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
