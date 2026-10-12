package kome.common.data;

import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.Assert.*;

/** KOM-59: real command BFS, effective-edge authority and transactional WorldData reload. */
public class KOMEIthilienRouteCorrectionTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private LOTRFactionRelations.Relation oldRelation;

    @Before public void neutralPassageFixture() {
        oldRelation = LOTRFactionRelations.getRelations(KOMEAlliance.findLotrFaction("gondor"), KOMEAlliance.findLotrFaction("rohan"));
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction("gondor"),
            KOMEAlliance.findLotrFaction("rohan"), LOTRFactionRelations.Relation.NEUTRAL);
    }
    @After public void restorePassageFixture() {
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction("gondor"),
            KOMEAlliance.findLotrFaction("rohan"), oldRelation);
    }

    @Test public void authorizedNewRoutesAreDirectBothWaysBeforeAndAfterReload() throws Exception {
        KOMEWorldData data = ownedTiles();
        for (int load = 0; load < 2; load++) {
            for (String[] direction : directions()) {
                assertEquals("true:" + Arrays.toString(direction), route(data, direction));
                assertEquals(KOMEConquestRouteEdge.OPEN, data.getRouteEdge(direction[0], direction[1]).edgeType);
                assertFalse(data.getRouteEdge(direction[0], direction[1]).manual);
                assertTrue(KOMEMovementDepartureGuard.evaluate(data, queued(data, direction)).allowed());
            }
            data = reload(data);
        }
    }

    @Test public void savedRiverAndBlockedOverridesStillRejectDirectRoutesAndDeparturesAfterReload() throws Exception {
        for (String type : new String[] {KOMEConquestRouteEdge.RIVER, KOMEConquestRouteEdge.BLOCKED}) {
            KOMEWorldData data = ownedTiles();
            data.setRouteEdge("T351", "T352", type, "Retained staff barrier", 100, 79488, 80, 55424, "staff-fixture");
            for (int load = 0; load < 2; load++) {
                KOMEConquestRouteEdge edge = data.getRouteEdge("T352", "T351");
                assertTrue(edge.manual);
                assertEquals(type, edge.edgeType);
                assertEquals("Retained staff barrier", edge.name);
                assertEquals("staff-fixture", edge.createdBy);
                assertEquals(100, edge.markerDimension);
                assertEquals(79488, edge.markerX, 0);
                assertEquals(80, edge.markerY, 0);
                assertEquals(55424, edge.markerZ, 0);
                for (String[] direction : directions()) {
                    assertEquals("true:[" + direction[0] + ", T358, " + direction[1] + "]", route(data, direction));
                    KOMEMovementDepartureGuard.Decision denial = KOMEMovementDepartureGuard.evaluate(data, queued(data, direction));
                    assertFalse(denial.allowed());
                    assertEquals("ROUTE_EDGE_BLOCKED", denial.code);
                }
                data = reload(data);
            }
            data.conquestTiles.remove("T358");
            for (String[] direction : directions()) assertEquals("false:[]", route(data, direction));
            assertTrue(data.removeRouteEdgeOverride("T351", "T352"));
            for (String[] direction : directions()) assertEquals("true:" + Arrays.toString(direction), route(data, direction));
        }
    }

    @Test public void openEdgeDoesNotBypassOriginOrDestinationPassagePermissions() throws Exception {
        for (String deniedTile : new String[] {"T351", "T352"}) {
            KOMEWorldData data = ownedTiles();
            data.getConquestTileIfPresent(deniedTile).claim("rohan", 0L);
            data = reload(data);
            for (String[] direction : directions()) {
                assertTrue(data.getRouteEdge(direction[0], direction[1]).isPassable());
                assertEquals("false:[]", route(data, direction));
            }
            String[] entering = deniedTile.equals("T351") ? directions()[0] : directions()[1];
            KOMEMovementDepartureGuard.Decision denial = KOMEMovementDepartureGuard.evaluate(data, queued(data, entering));
            assertFalse(denial.allowed());
            assertEquals("ACCESS_LOST", denial.code);
        }
    }

    @Test public void existingQueuedDetourSurvivesRepeatedReloadWithoutReplanning() throws Exception {
        KOMEWorldData data = ownedTiles();
        KOMEArmyMovementOrder order = queued(data, new String[] {"T352", "T358", "T351"});
        order.nextStepDepartureMillis = 12345L;
        // Existing canonical orders carry both; zero availability invokes legacy load normalization.
        order.nextStepAvailableMillis = 12345L;
        order.arrivalDimension = 100;
        order.arrivalX = 80001.5;
        order.arrivalY = 81;
        order.arrivalZ = 56001.5;
        String savedOrder = order.writeToNBT().toString();
        for (int load = 0; load < 2; load++) {
            data = reload(data);
            order = data.armyMovements.get("M-KOM59");
            assertNotNull(order);
            assertEquals(Arrays.asList("T352", "T358", "T351"), order.routeTiles);
            assertEquals("Stored order fields must not be rewritten", savedOrder, order.writeToNBT().toString());
            assertNotNull("Queued company must remain authoritative", data.armyCompanies.get(order.companyId));
            assertEquals("T352", data.armyCompanies.get(order.companyId).currentTile);
            assertTrue(KOMEMovementDepartureGuard.evaluate(data, order).allowed());
            assertEquals("true:[T352, T351]", route(data, directions()[0]));
        }
    }

    @Test public void automaticRiverMarkerIsAbsentWhileOtherMarkersRemain() {
        assertEquals(376, KOMETileGameplayDefaults.get().markers().size());
        assertEquals(318, KOMEConquestTileDefaults.getAutomaticRiverBlockerMarkers().size());
        assertEquals(58, KOMEConquestTileDefaults.getAutomaticBridgeMarkers().size());
        for (KOMEConquestTileDefaults.AutomaticRiverBlockerMarker marker : KOMEConquestTileDefaults.getAutomaticRiverBlockerMarkers()) {
            assertFalse("No automatic T351/T352 river marker",
                KOMEConquestRouteEdge.key(marker.fromTile, marker.toTile).equals("T351|T352"));
            assertFalse(marker.x == 79488.0 && marker.y == 80.0 && marker.z == 55424.0);
        }
    }

    private static String[][] directions() {
        return new String[][] {{"T352", "T351"}, {"T351", "T352"}};
    }
    private static String route(KOMEWorldData data, String[] direction) throws Exception {
        return KOMETileGameplayParityTest.route(data, direction[0], direction[direction.length - 1]);
    }
    private static KOMEWorldData ownedTiles() {
        KOMEWorldData data = new KOMEWorldData("kom59-disposable");
        for (String id : new String[] {"T351", "T352", "T358"}) {
            KOMEConquestTile tile = new KOMEConquestTile(id);
            tile.claim("gondor", 0L);
            data.conquestTiles.put(id, tile);
        }
        return data;
    }
    private static KOMEWorldData reload(KOMEWorldData data) {
        NBTTagCompound nbt = new NBTTagCompound();
        data.writeToNBT(nbt);
        KOMEWorldData restored = new KOMEWorldData("kom59-reloaded");
        restored.readFromNBT(nbt);
        assertFalse(restored.getLoadFailureReason(), restored.isWriteBlocked());
        return restored;
    }
    private static KOMEArmyMovementOrder queued(KOMEWorldData data, String[] tiles) {
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C-KOM59";
        company.owner = UUID.fromString("00000000-0000-0000-0000-000000000059");
        company.currentTile = tiles[0];
        company.faction = "gondor";
        company.movementOrderId = "M-KOM59";
        data.armyCompanies.put(company.id, company);
        KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(2);
        order.id = "M-KOM59";
        order.companyId = company.id;
        order.owner = company.owner;
        order.ownerFaction = "gondor";
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.originTile = tiles[0];
        order.destinationTile = tiles[tiles.length - 1];
        order.currentTile = tiles[0];
        order.nextTile = tiles[1];
        order.currentStepOriginTile = tiles[0];
        order.currentStepDestinationTile = tiles[1];
        order.finalDestinationTile = order.destinationTile;
        order.finalRouteIndex = tiles.length - 1;
        order.routeTiles.addAll(Arrays.asList(tiles));
        order.traveledRouteTiles.add(tiles[0]);
        order.totalSteps = tiles.length - 1;
        data.armyMovements.put(order.id, order);
        return order;
    }
}
