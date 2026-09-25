package kome.common.data;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.UUID;
import lotr.common.entity.npc.LOTREntityGondorSoldier;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.WorldServer;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEHiredUnitLifecycleTest {
    @org.junit.Rule public final kome.common.data.KOMETileTestResources geometry =
        new kome.common.data.KOMETileTestResources();

    @Test public void projectionIsIdenticalWithLoadedDeadEntityUnloadedWorldAndNoServer() throws Exception {
        KOMEWorldData data = data();
        KOMEHiredUnitRecord record = unit(data);
        Field serverField = null;
        for (Field field : MinecraftServer.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == MinecraftServer.class) serverField = field;
        }
        assertNotNull(serverField); serverField.setAccessible(true);
        Object previous = serverField.get(null);
        try {
            MinecraftServer server = inert(DedicatedServer.class);
            WorldServer world = inert(WorldServer.class);
            world.loadedEntityList = new ArrayList<Entity>();
            LOTREntityGondorSoldier npc = inert(LOTREntityGondorSoldier.class);
            for (Field field : Entity.class.getDeclaredFields()) {
                if (field.getType() == UUID.class) { field.setAccessible(true); field.set(npc, record.entity); }
            }
            npc.isDead = true; // Retained record, known inactive entity: projection must not inspect it.
            world.loadedEntityList.add(npc);
            server.worldServers = new WorldServer[] {world}; serverField.set(null, server);
            data.setDirty(false);
            assertActive(data, 4000L);
            world.loadedEntityList.clear(); assertActive(data, 4000L);
            server.worldServers = new WorldServer[] {null}; assertActive(data, 4000L);
            serverField.set(null, null); assertActive(data, 4000L);
            assertFalse(data.isDirty()); assertSame(record, data.hiredUnits.get(record.entity));
            for (int i = 0; i < 3; i++) { data = reload(data); assertActive(data, 4000L); }
        } finally { serverField.set(null, previous); }
    }

    @Test public void deathAndDismissalRemoveExactlyOnceWithoutRefundAcrossRestart() {
        for (String reason : new String[] {"Unit died", "Unit dismissed"}) {
            KOMEWorldData data = data(); KOMEHiredUnitRecord record = unit(data);
            // A stationary inter-step record has a valid route but no virtual snapshot.
            KOMEArmyMovementOrder order = route(data, record);
            order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
            assertSame(record, data.removeTerminatedHiredUnit(record.entity, reason));
            assertFalse(order.units.contains(record.entity)); assertActive(data, 0L);
            data.setDirty(false); int auditSize = data.centralAudit.size();
            assertNull(data.removeTerminatedHiredUnit(record.entity, reason));
            assertFalse(data.isDirty()); assertEquals(auditSize, data.centralAudit.size());
            for (int i = 0; i < 3; i++) {
                data = reload(data); assertFalse(data.hiredUnits.containsKey(record.entity));
                assertActive(data, 0L); assertEquals(2450L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
            }
        }
    }

    @Test public void validVirtualMovementSurvivesDespawnAndRestart() {
        KOMEWorldData data = data(); KOMEHiredUnitRecord record = unit(data);
        route(data, record); record.movingEntityData = new NBTTagCompound();
        record.movingEntityData.setString("id", "LOTR.GondorSoldier");
        data.setDirty(false);
        assertTrue(data.isVirtualMovingHiredUnit(record));
        assertNull(data.removeTerminatedHiredUnit(record.entity, "Virtual despawn"));
        assertEquals(0, data.reconcileHiredUnitMovementLinks()); assertFalse(data.isDirty());
        for (int i = 0; i < 3; i++) {
            data = reload(data); assertActive(data, 4000L);
            assertTrue(data.isVirtualMovingHiredUnit(data.hiredUnits.get(record.entity)));
        }
    }

    @Test public void invalidLinkIsRepairedOnlyAtLifecycleWithoutDiscardingSnapshotOrInvestment() {
        for (String fault : new String[] {"missing", "terminal", "membership", "company"}) {
            KOMEWorldData data = data(); KOMEHiredUnitRecord record = unit(data);
            KOMEArmyMovementOrder order = route(data, record);
            record.movingEntityData = new NBTTagCompound(); record.movingEntityData.setString("Saved", "unit");
            if (fault.equals("missing")) data.armyMovements.clear();
            if (fault.equals("terminal")) order.status = KOMEArmyMovementOrder.ARRIVED;
            if (fault.equals("membership")) order.units.clear();
            if (fault.equals("company")) order.companyId = "another-company";
            data.setDirty(false); assertActive(data, 4000L);
            assertEquals("M1", record.movementOrderId); assertFalse(data.isDirty());
            assertEquals(1, data.reconcileHiredUnitMovementLinks());
            assertEquals("", record.movementOrderId); assertNull(record.movingEntityData);
            assertEquals("unit", record.stationedEntityData.getString("Saved"));
            assertEquals(40, record.populationSpent); assertEquals("gondor", record.populationOwningFaction);
            assertEquals(2450L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor")); assertActive(data, 4000L);
            data.setDirty(false); assertEquals(0, data.reconcileHiredUnitMovementLinks()); assertFalse(data.isDirty());
            assertSame(record, data.removeTerminatedHiredUnit(record.entity, "Unit dismissed")); assertActive(data, 0L);
        }
    }

    @Test public void projectionHasNoEntityQueriesAndLifecycleHooksOwnRemovalAndRepair() throws Exception {
        String service = source("KOMEPopulationService.java");
        String projection = source("KOMEPopulationProjection.java");
        for (String forbidden : new String[] {"getServer()", "loadedEntityList", "getEntityByID", "isEntityAlive()"}) {
            assertFalse(forbidden, service.contains(forbidden)); assertFalse(forbidden, projection.contains(forbidden));
        }
        String events = source("KOMEEvents.java");
        assertTrue(events.contains("data.reconcileHiredUnitMovementLinks();"));
        assertTrue(events.contains("data.removeTerminatedHiredUnit(entityId"));
        assertTrue(events.contains("event.phase != TickEvent.Phase.START"));
        assertTrue(events.contains("data.isVirtualMovingHiredUnit(movingRecord)"));
        String troops = new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/command/KOMECommandTroops.java")), StandardCharsets.UTF_8);
        assertTrue(troops.contains("data.isVirtualMovingHiredUnit(record) && !isArrivalSpawnInProgress(data, record)"));
    }

    @Test public void danglingLabelCannotQualifyForArrivalProtection() {
        KOMEWorldData data = data(); KOMEHiredUnitRecord record = unit(data);
        KOMEArmyMovementOrder order = route(data, record); order.status = KOMEArmyMovementOrder.SPAWNING;
        assertTrue(kome.common.command.KOMECommandTroops.isArrivalSpawnInProgress(data, record));
        order.units.clear(); assertFalse(kome.common.command.KOMECommandTroops.isArrivalSpawnInProgress(data, record));
        order.units.add(record.entity); order.companyId = "wrong";
        assertFalse(kome.common.command.KOMECommandTroops.isArrivalSpawnInProgress(data, record));
        data.armyMovements.clear(); assertFalse(kome.common.command.KOMECommandTroops.isArrivalSpawnInProgress(data, record));
    }

    private static KOMEWorldData data() {
        KOMEWorldData data = new KOMEWorldData("lifecycle"); data.grantFactionPopulationCenti("gondor", 2450L); return data;
    }
    private static KOMEHiredUnitRecord unit(KOMEWorldData data) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord(); record.entity = UUID.randomUUID(); record.owner = UUID.randomUUID();
        record.populationOwningFaction = "gondor"; record.sourceFaction = "gondor";
        record.populationSpent = 40; record.cost = 25; record.companyId = "C1";
        data.hiredUnits.put(record.entity, record); return record;
    }
    private static KOMEArmyMovementOrder route(KOMEWorldData data, KOMEHiredUnitRecord record) {
        KOMEArmyCompany company = new KOMEArmyCompany(); company.id = record.companyId;
        company.owner = record.owner; company.faction = "gondor"; company.units.add(record.entity);
        company.movementOrderId = "M1"; data.armyCompanies.put(company.id, company);
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder(); order.id = "M1"; order.companyId = record.companyId;
        order.status = KOMEArmyMovementOrder.MOVING; order.units.add(record.entity); record.movementOrderId = order.id;
        data.armyMovements.put(order.id, order); return order;
    }
    private static void assertActive(KOMEWorldData data, long centi) {
        assertEquals(BigInteger.valueOf(centi), KOMEPopulationProjection.of(data, "gondor").activePopulationCenti);
    }
    private static KOMEWorldData reload(KOMEWorldData data) {
        NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag);
        KOMEWorldData copy = new KOMEWorldData("reloaded"); copy.readFromNBT(tag); return copy;
    }
    private static String source(String name) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/data/" + name)), StandardCharsets.UTF_8);
    }
    @SuppressWarnings("unchecked") private static <T> T inert(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe"); Field field = unsafe.getDeclaredField("theUnsafe"); field.setAccessible(true);
        return (T) unsafe.getMethod("allocateInstance", Class.class).invoke(field.get(null), type);
    }
}
