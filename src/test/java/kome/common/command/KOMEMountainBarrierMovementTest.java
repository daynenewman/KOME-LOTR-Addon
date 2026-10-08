package kome.common.command;

import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceAuthority;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEConflictMovementService;
import kome.common.data.KOMEConquestRouteEdge;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEConquestTileDefaults;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEMovementAccessService;
import kome.common.data.KOMEPopulationTestConfig;
import kome.common.data.KOMETileTestResources;
import kome.common.data.KOMEWorldData;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import org.junit.Rule;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** KOM-80: rendered contacts, route edges and territorial access are separate authorities. */
public class KOMEMountainBarrierMovementTest {
    private KOMEPopulationTestConfig movementConfig;
    @org.junit.Before public void movementConfig() throws Exception { movementConfig = new KOMEPopulationTestConfig(); }
    @org.junit.After public void closeMovementConfig() throws Exception { movementConfig.close(); }
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    private static final long NOW = 123456789L;
    private static final String[][] REPORTED_CONTACTS = {
        {"T654", "T435"}, {"T654", "T455"}, {"T420", "T400"},
        {"T329", "T355"}, {"T352", "T356"}, {"T218", "T220"}
    };
    private static final String[][] LEGAL_CROSSINGS = {
        {"T239", "T232"}, {"T232", "T220"}, {"T220", "T223"},
        {"T223", "T233"}, {"T409", "T420"}, {"T425", "T435"},
        {"T444", "T455"}, {"T322", "T329"}, {"T329", "T338"}
    };

    @Test public void reportedContactsDoNotSupplyDirectStrategicRoutesInEitherDirection() throws Exception {
        for (String[] pair : REPORTED_CONTACTS) {
            for (String[] direction : directions(pair)) {
                KOMEWorldData data = ownedTiles(direction);
                String message = Arrays.toString(direction);
                assertNull(message, data.getRouteEdge(direction[0], direction[1]));
                assertFalse(message, data.getRouteNeighbors(direction[0]).contains(direction[1]));
                Route route = findRoute(data, direction[0], direction[1]);
                assertFalse(message, route.valid);
                assertTrue(message, route.tiles.isEmpty());
            }
        }
    }

    @Test public void nearbyAuditedMountainContactsCannotSupplyDirectRoutesOrDepartures() throws Exception {
        World world = world();
        for (String[] pair : new String[][] {
                {"T220", "T231"}, {"T220", "T233"}, {"T223", "T231"},
                {"T232", "T233"}, {"T346", "T356"}, {"T356", "T357"}, {"T444", "T654"}}) {
            for (String[] direction : directions(pair)) {
                KOMEWorldData data = ownedTiles(direction);
                assertNull(Arrays.toString(direction), data.getRouteEdge(direction[0], direction[1]));
                assertFalse(data.getRouteNeighbors(direction[0]).contains(direction[1]));
                assertFalse(findRoute(data, direction[0], direction[1]).valid);
                KOMEArmyMovementOrder order = queued(data, direction);
                assertTrue(KOMEMovementAccessService.isMovementStepAuthorized(data, order));
                assertFalse(Arrays.toString(direction), depart(data, order, world));
                assertUncommitted(order, direction, "ROUTE_EDGE_MISSING");
            }
        }
    }

    @Test public void realRouteSearchMayDetourButNeverTreatsReportedContactAsAnEdge() throws Exception {
        KOMEWorldData data = ownedTiles(KOMEConquestTileDefaults.getKnownTileIds().toArray(new String[0]));
        for (String[] pair : new String[][] {{"T654", "T435"}, {"T654", "T455"}, {"T218", "T220"}}) {
            for (String[] direction : directions(pair)) {
                Route route = findRoute(data, direction[0], direction[1]);
                assertFalse(Arrays.toString(direction), route.valid);
                assertTrue(route.tiles.isEmpty());
            }
        }
        // Goal-oriented tie breaking selects different legal alternatives in the reverse direction.
        for (String[] expected : new String[][] {
                {"T420", "T409", "T400"}, {"T400", "T409", "T420"},
                {"T329", "T331", "T355"}, {"T355", "T338", "T329"},
                {"T352", "T358", "T375", "T381", "T391", "T356"},
                {"T356", "T391", "T357", "T346", "T326", "T352"}}) {
            Route route = findRoute(data, expected[0], expected[expected.length - 1]);
            assertTrue(Arrays.toString(expected), route.valid);
            assertEquals(Arrays.asList(expected), route.tiles);
            for (int i = 1; i < route.tiles.size(); i++) {
                KOMEConquestRouteEdge edge = data.getRouteEdge(route.tiles.get(i - 1), route.tiles.get(i));
                assertNotNull(route.tiles.toString(), edge);
                assertTrue(route.tiles.toString(), edge.isPassable());
            }
        }
    }

    @Test public void knownCaradhrasCorridorAndNearbyBridgesRemainDirectRoutesAndCanDepart() throws Exception {
        World world = world();
        for (int i = 0; i < LEGAL_CROSSINGS.length; i++) {
            String[] pair = LEGAL_CROSSINGS[i];
            for (String[] direction : directions(pair)) {
                KOMEWorldData data = ownedTiles(direction);
                KOMEConquestRouteEdge edge = data.getRouteEdge(direction[0], direction[1]);
                assertNotNull(Arrays.toString(direction), edge);
                assertTrue(edge.isPassable());
                assertEquals(i < 4 ? KOMEConquestRouteEdge.OPEN : KOMEConquestRouteEdge.BRIDGE, edge.edgeType);
                Route route = findRoute(data, direction[0], direction[1]);
                assertTrue(Arrays.toString(direction), route.valid);
                assertEquals(Arrays.asList(direction), route.tiles);
                KOMEArmyMovementOrder order = queued(data, direction);
                assertTrue(KOMEMovementAccessService.isMovementStepAuthorized(data, order));
                assertTrue(depart(data, order, world));
                assertEquals(KOMEArmyMovementOrder.MOVING, order.status);
                assertEquals(1, order.dailyStepsRemaining);
                assertEquals(direction[1], order.arrivalPointTileId);
                assertEquals(NOW, order.stepDepartureMillis);
            }
        }
    }

    @Test public void staleDirectOrdersAcrossReportedContactsCannotCommitOrSpendAllowance() throws Exception {
        World world = world();
        for (String[] pair : REPORTED_CONTACTS) {
            for (String[] direction : directions(pair)) {
                KOMEWorldData data = ownedTiles(direction);
                KOMEArmyMovementOrder order = queued(data, direction);
                // Friendly ownership passes the independent territorial check; it cannot supply an edge.
                assertTrue(KOMEMovementAccessService.isMovementStepAuthorized(data, order));
                assertFalse(Arrays.toString(direction), depart(data, order, world));
                assertUncommitted(order, direction, "ROUTE_EDGE_MISSING");
            }
        }
    }

    @Test public void removedPassOverrideCannotAuthorizePreviouslyQueuedDeparture() throws Exception {
        World world = world();
        for (String[] direction : directions(new String[] {"T218", "T220"})) {
            String preceding = "T218".equals(direction[0]) ? "T188" : "T223";
            KOMEWorldData data = ownedTiles(preceding, direction[0], direction[1]);
            setEdge(data, direction, KOMEConquestRouteEdge.MOUNTAIN_PASS);
            Route legalRoute = findRoute(data, preceding, direction[1]);
            assertTrue(legalRoute.valid);
            assertEquals(Arrays.asList(preceding, direction[0], direction[1]), legalRoute.tiles);
            KOMEArmyMovementOrder order = queued(data, direction);
            order.routeTiles.clear(); order.routeTiles.addAll(legalRoute.tiles);
            order.originTile = preceding; order.currentRouteIndex = 1;
            order.nextRouteIndex = 2; order.finalRouteIndex = 2;
            order.totalSteps = 2; order.distanceTiles = 2; order.completedSteps = 1;
            order.traveledRouteTiles.add(0, preceding);
            // Persisted queued orders retain the accepted route after an operator removes a passage.
            KOMEArmyMovementOrder restored = new KOMEArmyMovementOrder();
            restored.readFromNBT(order.writeToNBT());
            data.armyMovements.put(restored.id, restored);
            assertTrue(data.removeRouteEdgeOverride(direction[0], direction[1]));
            assertFalse(findRoute(data, preceding, direction[1]).valid);
            assertTrue(KOMEMovementAccessService.isMovementStepAuthorized(data, restored));
            assertFalse(depart(data, restored, world));
            assertEquals("ROUTE_EDGE_MISSING", restored.lastSpawnFailureCode);
            assertEquals(KOMEArmyMovementOrder.WAITING_NEXT_STEP, restored.status);
            assertEquals(2, restored.dailyStepsRemaining);
            assertEquals(direction[0], restored.currentTile);
            assertEquals(direction[1], restored.nextTile);
            assertEquals(1, restored.currentRouteIndex);
            assertEquals(2, restored.nextRouteIndex);
            assertEquals(1, restored.completedSteps);
            assertEquals(legalRoute.tiles, restored.routeTiles);
            assertEquals(Arrays.asList(preceding, direction[0]), restored.traveledRouteTiles);
            assertEquals(0L, restored.stepDepartureMillis);
            assertEquals(0L, restored.arrivalMillis);
        }
    }

    @Test public void currentMountainRiverAndBlockedOverridesRejectFormerlyLegalDeparture() throws Exception {
        World world = world();
        for (String type : new String[] {KOMEConquestRouteEdge.MOUNTAIN,
                KOMEConquestRouteEdge.RIVER, KOMEConquestRouteEdge.BLOCKED}) {
            for (String[] direction : directions(new String[] {"T409", "T420"})) {
                KOMEWorldData data = ownedTiles(direction);
                assertTrue(findRoute(data, direction[0], direction[1]).valid);
                KOMEArmyMovementOrder order = queued(data, direction);
                setEdge(data, direction, type);
                assertFalse(findRoute(data, direction[0], direction[1]).valid);
                assertTrue(KOMEMovementAccessService.isMovementStepAuthorized(data, order));
                assertFalse(type + Arrays.toString(direction), depart(data, order, world));
                assertUncommitted(order, direction, "ROUTE_EDGE_BLOCKED");
            }
        }
    }

    @Test public void explicitOpenBridgeAndMountainPassOverridesRemainAuthoritative() throws Exception {
        World world = world();
        for (String type : new String[] {KOMEConquestRouteEdge.OPEN,
                KOMEConquestRouteEdge.BRIDGE, KOMEConquestRouteEdge.MOUNTAIN_PASS}) {
            for (String[] direction : directions(new String[] {"T218", "T220"})) {
                KOMEWorldData data = ownedTiles(direction);
                setEdge(data, direction, type);
                Route route = findRoute(data, direction[0], direction[1]);
                assertTrue(type, route.valid);
                assertEquals(Arrays.asList(direction), route.tiles);
                KOMEArmyMovementOrder order = queued(data, direction);
                assertTrue(type, depart(data, order, world));
                assertEquals(KOMEArmyMovementOrder.MOVING, order.status);
                assertEquals(1, order.dailyStepsRemaining);
                assertEquals(direction[1], order.arrivalPointTileId);
            }
        }
    }

    @Test public void retreatAcrossPreviouslyTraveledTilesStillNeedsAPassableCurrentEdge() throws Exception {
        World world = world();
        for (String type : new String[] {"missing", KOMEConquestRouteEdge.MOUNTAIN,
                KOMEConquestRouteEdge.RIVER, KOMEConquestRouteEdge.BLOCKED}) {
            for (String[] direction : directions(new String[] {"T218", "T220"})) {
                KOMEWorldData data = ownedTiles(direction);
                if (!"missing".equals(type)) setEdge(data, direction, type);
                KOMEArmyMovementOrder order = queued(data, direction);
                order.retreating = true;
                order.traveledRouteTiles.add(direction[1]);
                assertTrue(KOMEMovementAccessService.isMovementStepAuthorized(data, order));
                assertFalse(type, depart(data, order, world));
                assertEquals("missing".equals(type) ? "ROUTE_EDGE_MISSING" : "ROUTE_EDGE_BLOCKED",
                    order.lastSpawnFailureCode);
                assertEquals(KOMEArmyMovementOrder.WAITING_NEXT_STEP, order.status);
                assertEquals(2, order.dailyStepsRemaining);
                assertEquals(direction[0], order.currentTile);
                assertEquals(0, order.currentRouteIndex);
                assertEquals(Arrays.asList(direction), order.traveledRouteTiles);
                assertEquals(0L, order.stepDepartureMillis);
                setEdge(data, direction, KOMEConquestRouteEdge.MOUNTAIN_PASS);
                assertTrue(depart(data, order, world));
                assertEquals(1, order.dailyStepsRemaining);
                assertEquals(KOMEArmyMovementOrder.MOVING, order.status);
            }
        }
    }

    @Test public void authorizedHostileTerminalAttackStillNeedsAPassableCurrentEdge() throws Exception {
        LOTRFactionRelations.Relation previous = KOMEAllianceAuthority.getCurrentRelation("gondor", "mordor");
        try {
            LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction("gondor"),
                KOMEAlliance.findLotrFaction("mordor"), LOTRFactionRelations.Relation.ENEMY);
            World world = world();
            for (String type : new String[] {"missing", KOMEConquestRouteEdge.MOUNTAIN,
                    KOMEConquestRouteEdge.RIVER, KOMEConquestRouteEdge.BLOCKED}) {
                for (String[] direction : directions(new String[] {"T218", "T220"})) {
                    KOMEWorldData data = ownedTiles(direction);
                    data.conquestTiles.get(direction[1]).setCurrentRulingFaction("mordor");
                    if (!"missing".equals(type)) setEdge(data, direction, type);
                    KOMEArmyMovementOrder order = queued(data, direction);
                    KOMEArmyCompany company = new KOMEArmyCompany();
                    company.id = "C-KOM80"; company.name = company.id; company.owner = UUID.randomUUID();
                    company.faction = company.nativeFaction = "gondor";
                    company.currentTile = direction[0]; company.status = KOMEArmyCompany.STATIONED;
                    company.movementOrderId = order.id;
                    KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
                    record.entity = UUID.randomUUID(); record.owner = record.sourcePlayer = company.owner;
                    record.companyId = record.companyName = company.id; record.movementOrderId = order.id;
                    record.currentTile = record.sourceTileId = direction[0];
                    record.sourceFaction = record.unitFaction = record.populationOwningFaction = "gondor";
                    record.cost = record.baseCost = record.populationSpent = 10;
                    record.mounted = true; company.movementAllowance = 2;
                    NBTTagCompound persisted = record.writeToNBT(); persisted.setString("UnitClass", "CAMPAIGN");
                    record.readFromNBT(persisted);
                    company.units.add(record.entity); company.totalPopulation = company.mountedPopulation = record.cost;
                    order.companyId = company.id; order.owner = company.owner; order.units.add(record.entity);
                    order.hostileAttackDestination = direction[1];
                    data.hiredUnits.put(record.entity, record); data.armyCompanies.put(company.id, company);
                    // Production coherence and current hostility authorize only the terminal tile exception.
                    assertFalse(KOMEMovementAccessService.isTileStandableForOrder(data, order, direction[1], false));
                    assertTrue(KOMEConflictMovementService.isAuthorizedHostileTerminalStep(data, order, direction[1]));
                    assertTrue(KOMEMovementAccessService.isMovementStepAuthorized(data, order));
                    assertFalse(type, depart(data, order, world));
                    assertUncommitted(order, direction,
                        "missing".equals(type) ? "ROUTE_EDGE_MISSING" : "ROUTE_EDGE_BLOCKED");
                    assertNull(data.getConflictService().get(direction[1]));
                    setEdge(data, direction, KOMEConquestRouteEdge.MOUNTAIN_PASS);
                    assertTrue(depart(data, order, world));
                    assertEquals(KOMEArmyMovementOrder.MOVING, order.status);
                    assertEquals(1, order.dailyStepsRemaining);
                    assertNull("Departure alone cannot publish a hostile arrival", data.getConflictService().get(direction[1]));
                }
            }
        } finally {
            LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction("gondor"),
                KOMEAlliance.findLotrFaction("mordor"), previous);
        }
    }

    @Test public void waitingTickRejectsIllegalEdgeBeforeStagingUnitsOrLoadingArrival() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            for (String type : new String[] {"missing", KOMEConquestRouteEdge.MOUNTAIN,
                    KOMEConquestRouteEdge.RIVER, KOMEConquestRouteEdge.BLOCKED}) {
                for (String[] direction : directions(new String[] {"T218", "T220"})) {
                    KOMEWorldData data = ownedTiles(direction);
                    if (!"missing".equals(type)) setEdge(data, direction, type);
                    KOMEArmyMovementOrder order = queued(data, direction);
                    KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
                    record.entity = UUID.randomUUID(); record.movementOrderId = order.id;
                    record.stationedEntityData = new NBTTagCompound();
                    record.stationedEntityData.setString("fixture", "stationed at origin");
                    NBTTagCompound before = (NBTTagCompound) record.stationedEntityData.copy();
                    data.hiredUnits.put(record.entity, record); order.units.add(record.entity);
                    FixtureWorld world = world();
                    assertTrue(KOMECommandTroops.processWaitingStepDepartures(data, world, NOW));
                    assertUncommitted(order, direction,
                        "missing".equals(type) ? "ROUTE_EDGE_MISSING" : "ROUTE_EDGE_BLOCKED");
                    assertNull("No unit may be staged for the rejected step", record.movingEntityData);
                    assertEquals(before, record.stationedEntityData);
                    assertEquals(order.id, record.movementOrderId);
                    assertEquals(Arrays.asList(record.entity), order.units);
                    assertEquals("No destination chunk access before route rejection", 0, world.chunkRequests);
                }
            }
        }
    }

    private static void assertUncommitted(KOMEArmyMovementOrder order, String[] direction, String code) {
        assertEquals(Arrays.toString(direction), KOMEArmyMovementOrder.WAITING_NEXT_STEP, order.status);
        assertEquals(code, order.lastSpawnFailureCode);
        assertEquals(2, order.dailyStepsRemaining);
        assertEquals(direction[0], order.currentTile);
        assertEquals(direction[1], order.nextTile);
        assertEquals(0, order.currentRouteIndex);
        assertEquals(1, order.nextRouteIndex);
        assertEquals(0, order.completedSteps);
        assertEquals(Arrays.asList(direction), order.routeTiles);
        assertEquals(Arrays.asList(direction[0]), order.traveledRouteTiles);
        assertEquals("", order.arrivalPointTileId);
        assertEquals(0.0D, order.arrivalX, 0.0D);
        assertEquals(0.0D, order.arrivalY, 0.0D);
        assertEquals(0.0D, order.arrivalZ, 0.0D);
        assertEquals(0L, order.stepDepartureMillis);
        assertEquals(0L, order.stepArrivalMillis);
        assertEquals(0L, order.arrivalMillis);
    }

    private static KOMEWorldData ownedTiles(String... ids) {
        KOMEWorldData data = new KOMEWorldData("mountain-barrier");
        for (String id : ids) {
            KOMEConquestTile tile = new KOMEConquestTile(id);
            tile.claim("gondor", 0L); tile.setAnchor(0, 10.0D, 64.0D, 20.0D);
            data.conquestTiles.put(id, tile);
        }
        return data;
    }

    private static KOMEArmyMovementOrder queued(KOMEWorldData data, String[] direction) {
        KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(2);
        KOMEArmyCompany company = new KOMEArmyCompany(); company.id = "C-KOM80";
        company.currentTile = direction[0]; company.mountedPopulation = 1;
        company.movementAllowance = 2; data.armyCompanies.put(company.id, company);
        order.companyId = company.id; order.dailyStepsRemaining = 2;
        order.id = "M-KOM80"; order.ownerFaction = "gondor";
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.originTile = direction[0]; order.destinationTile = direction[1];
        order.currentTile = direction[0]; order.nextTile = direction[1];
        order.currentStepOriginTile = direction[0]; order.currentStepDestinationTile = direction[1];
        order.finalDestinationTile = direction[1]; order.finalRouteIndex = 1;
        order.routeTiles.addAll(Arrays.asList(direction)); order.traveledRouteTiles.add(direction[0]);
        order.totalSteps = 1; data.armyMovements.put(order.id, order);
        return order;
    }

    private static void setEdge(KOMEWorldData data, String[] direction, String type) {
        data.setRouteEdge(direction[0], direction[1], type, "KOM-80 fixture", 0, 10, 64, 20, "test");
    }

    private static String[][] directions(String[] pair) {
        return new String[][] {pair, {pair[1], pair[0]}};
    }

    private static boolean depart(final KOMEWorldData data, final KOMEArmyMovementOrder order,
            final World world) throws Exception {
        final Method schedule = KOMECommandTroops.class.getDeclaredMethod("scheduleNextRouteStep",
            KOMEWorldData.class, KOMEArmyMovementOrder.class, World.class, long.class);
        schedule.setAccessible(true);
        return kome.common.data.KOMEMovementDayService.depart(data, order, true, () -> {
            try { return (Boolean) schedule.invoke(null, data, order, world, NOW); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        });
    }

    @SuppressWarnings("unchecked")
    private static Route findRoute(KOMEWorldData data, String from, String to) throws Exception {
        Method search = KOMECommandTroops.class.getDeclaredMethod("findLegalRoute",
            KOMEWorldData.class, String.class, String.class, String.class);
        search.setAccessible(true);
        Object result = search.invoke(new KOMECommandTroops(), data, from, to, "gondor");
        Field valid = result.getClass().getDeclaredField("valid"); valid.setAccessible(true);
        Field tiles = result.getClass().getDeclaredField("routeTiles"); tiles.setAccessible(true);
        return new Route(valid.getBoolean(result), new ArrayList<String>((List<String>) tiles.get(result)));
    }

    private static final class Route {
        final boolean valid;
        final List<String> tiles;
        Route(boolean valid, List<String> tiles) { this.valid = valid; this.tiles = tiles; }
    }

    /** Inert world only supplies available chunks; production route/arrival/departure logic remains real. */
    private static FixtureWorld world() throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        FixtureWorld world = (FixtureWorld) unsafe.getMethod("allocateInstance", Class.class)
            .invoke(singleton.get(null), FixtureWorld.class);
        Field provider = World.class.getDeclaredField("provider"); provider.setAccessible(true);
        provider.set(world, new WorldProviderSurface());
        world.loadedEntityList = new ArrayList<Entity>(); world.playerEntities = new ArrayList();
        return world;
    }

    private static final class FixtureWorld extends World {
        int chunkRequests;
        private FixtureWorld() {
            super((ISaveHandler) null, "test", (WorldProvider) null, (WorldSettings) null, (Profiler) null);
        }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public IChunkProvider getChunkProvider() { return null; }
        @Override public Chunk getChunkFromChunkCoords(int x, int z) { chunkRequests++; return null; }
        @Override public boolean blockExists(int x, int y, int z) { return true; }
    }
}
