package kome.common.command;

import kome.common.data.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import org.junit.Rule;
import java.util.Arrays;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMEMovementDepartureGuardTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private final KOMEWorldData data = new KOMEWorldData("guard");
    private KOMEArmyCompany company;
    private KOMEHiredUnitRecord unit;

    private KOMEArmyMovementOrder route() {
        for (String id : Arrays.asList("T001", "T002", "T003")) {
            KOMEConquestTile tile = new KOMEConquestTile(id); tile.claim("gondor", 0); data.conquestTiles.put(id, tile);
        }
        data.setRouteEdge("T001", "T002", KOMEConquestRouteEdge.OPEN, "fixture", 0, 0, 64, 0, "test");
        company = new KOMEArmyCompany(); company.id = "C1"; company.owner = UUID.randomUUID();
        company.currentTile = "T001"; company.faction = "gondor"; company.movementAllowance = 1;
        data.armyCompanies.put(company.id, company);
        KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(1);
        order.id = "M1"; order.companyId = company.id; order.ownerFaction = "gondor";
        order.currentTile = "T001"; order.routeTiles.addAll(Arrays.asList("T001", "T002", "T003"));
        order.currentRouteIndex = 0; order.nextRouteIndex = 1; order.finalRouteIndex = 2;
        order.nextTile = "T002"; order.currentStepDestinationTile = "T002";
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP; order.dailyStepsRemaining = 1;
        unit = new KOMEHiredUnitRecord(); unit.entity = UUID.randomUUID(); unit.owner = company.owner;
        unit.currentTile = company.currentTile; unit.companyId = company.id; unit.movementOrderId = order.id;
        unit.stationedEntityData = new NBTTagCompound(); unit.stationedEntityData.setFloat("HealF", 5.125F);
        data.hiredUnits.put(unit.entity, unit); order.units.add(unit.entity); company.units.add(unit.entity);
        data.armyMovements.put(order.id, order); return order;
    }

    @Test public void queuedGuardUsesActualNextEdgeNotStaleDestinationFields() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEArmyMovementOrder order = route();
            order.nextTile = order.currentStepDestinationTile = "T003"; // stale, forbidden tile is the actual next T002
            data.conquestTiles.get("T002").setCurrentRulingFaction("");
            NBTTagCompound before = unit.writeToNBT();
            assertTrue(KOMECommandTroops.processWaitingStepDepartures(data, null, 1L));
            assertEquals(KOMEArmyMovementOrder.ACCESS_HALTED, order.status);
            assertEquals(1, company.movementAllowance); assertEquals(before, unit.writeToNBT());
            assertTrue(order.accessLossReason.contains("T002")); assertEquals(0, order.currentRouteIndex);
        }
    }

    @Test public void missingNewlyBlockedAndRemovedPassRejectBeforeStagingOrWorldResolution() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            for (String type : new String[]{"missing", KOMEConquestRouteEdge.MOUNTAIN, KOMEConquestRouteEdge.RIVER, KOMEConquestRouteEdge.BLOCKED}) {
                KOMEArmyMovementOrder order = route();
                data.setRouteEdge("T001", "T002", KOMEConquestRouteEdge.MOUNTAIN_PASS, "pass",0,0,64,0,"test");
                if (type.equals("missing")) data.removeRouteEdgeOverride("T001", "T002");
                else data.setRouteEdge("T001", "T002", type, "changed",0,0,64,0,"test");
                NBTTagCompound before = unit.writeToNBT();
                assertTrue(KOMECommandTroops.processWaitingStepDepartures(data, null, 1));
                assertEquals(type.equals("missing") ? "ROUTE_EDGE_MISSING" : "ROUTE_EDGE_BLOCKED", order.lastSpawnFailureCode);
                assertEquals(before, unit.writeToNBT()); assertEquals(1, company.movementAllowance);
                assertEquals(0, order.completedSteps);
            }
        }
    }

    @Test public void explicitBridgeAndPassAreAcceptedByTheCommonGuard() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            for (String type : new String[]{KOMEConquestRouteEdge.BRIDGE, KOMEConquestRouteEdge.MOUNTAIN_PASS}) {
                KOMEArmyMovementOrder order = route(); data.setRouteEdge("T001", "T002", type,"override",0,0,64,0,"test");
                assertTrue(KOMEMovementDepartureGuard.evaluate(data, order).allowed());
            }
        }
    }

    @Test public void operatorClockAdvanceCannotFabricateRouteProgressOrSpendOnIllegalEdge() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEArmyMovementOrder order = route(); data.removeRouteEdgeOverride("T001", "T002");
            java.lang.reflect.Method advance = KOMECommandTroops.class.getDeclaredMethod("advanceMovementOrder",KOMEWorldData.class,KOMEArmyMovementOrder.class,int.class,long.class);
            advance.setAccessible(true); advance.invoke(new KOMECommandTroops(),data,order,99,1L);
            assertEquals(0, order.currentRouteIndex); assertEquals(0, order.completedSteps);
            assertEquals(1, company.movementAllowance); assertNull(unit.movingEntityData);
            assertEquals("ROUTE_EDGE_MISSING", order.lastSpawnFailureCode);
        }
    }
}
