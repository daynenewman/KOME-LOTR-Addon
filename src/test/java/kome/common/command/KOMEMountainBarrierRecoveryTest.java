package kome.common.command;

import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEConquestRouteEdge;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEMovementHistoryRecord;
import kome.common.data.KOMEMovementRecoveryOptions;
import kome.common.data.KOMEPopulationTestConfig;
import kome.common.data.KOMETileTestResources;
import kome.common.data.KOMEWorldData;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.IChatComponent;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** Exercises actual recovery commands without a live server or entity spawning. */
public class KOMEMountainBarrierRecoveryTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void ordinaryOwnerCannotResumeAPlainStoppedOrderAcrossAnIllegalEdge() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            for (String restriction : restrictions()) {
                Fixture f = new Fixture("T218", "T220", restriction, false);
                KOMEHiredUnitRecord unit = f.addUnit();
                f.command("stop");
                assertEquals(KOMEArmyMovementOrder.STOPPED, f.order.status);
                assertEquals("", f.order.accessLossReason);
                assertFalse(KOMEMovementRecoveryOptions.forOrder(f.data, f.order).canResume);
                NBTTagCompound orderBefore = f.order.writeToNBT();
                NBTTagCompound companyBefore = f.company.writeToNBT();
                NBTTagCompound unitBefore = unit.writeToNBT();
                NBTTagCompound historyBefore = f.data.movementHistory.get(f.order.id).writeToNBT();

                try {
                    f.command("resume");
                    fail("Plain stopped route must reject Resume: " + restriction);
                } catch (WrongUsageException expected) {
                    assertEquals(orderBefore, f.order.writeToNBT());
                    assertEquals(companyBefore, f.company.writeToNBT());
                    assertEquals(unitBefore, unit.writeToNBT());
                    assertEquals(historyBefore, f.data.movementHistory.get(f.order.id).writeToNBT());
                    assertEquals(0, f.world.chunkRequests);
                    assertEquals(0, f.data.arrivalPublications);
                }
            }
        }
    }

    @Test public void operatorCompleteCannotTurnARejectedQueuedStepIntoAnArrival() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            for (String restriction : restrictions()) {
                Fixture f = new Fixture("T218", "T220", restriction, true);
                f.command("complete");

                assertEquals(restriction, KOMEArmyMovementOrder.WAITING_NEXT_STEP, f.order.status);
                assertEquals("missing".equals(restriction) ? "ROUTE_EDGE_MISSING" : "ROUTE_EDGE_BLOCKED",
                    f.order.lastSpawnFailureCode);
                assertTrue(f.order.pendingSpawnReason.contains(f.order.lastSpawnFailureCode));
                assertEquals(2, f.order.dailyStepsRemaining);
                assertEquals("T218", f.order.currentTile);
                assertEquals("T218", f.company.currentTile);
                assertEquals("T220", f.order.nextTile);
                assertEquals(0, f.order.currentRouteIndex);
                assertEquals(1, f.order.nextRouteIndex);
                assertEquals(0, f.order.completedSteps);
                assertEquals(Arrays.asList("T218"), f.order.traveledRouteTiles);
                assertEquals(0L, f.order.stepDepartureMillis);
                assertEquals(0, f.data.arrivalPublications);
                assertEquals(0, f.world.chunkRequests);
            }
        }
    }

    @Test public void operatorCompleteOnALegalQueuedCrossingPublishesArrivalOnlyOnce() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            Fixture f = new Fixture("T220", "T223", "default", true);
            assertTrue(f.data.getRouteEdge("T220", "T223").isPassable());
            f.command("complete");

            assertEquals(KOMEArmyMovementOrder.ARRIVED, f.order.status);
            assertEquals("T223", f.order.currentTile);
            assertEquals("T223", f.company.currentTile);
            assertEquals(1, f.order.completedSteps);
            assertEquals(1, f.order.dailyStepsRemaining);
            assertEquals(Arrays.asList("T220", "T223"), f.order.traveledRouteTiles);
            assertEquals(1, f.data.arrivalPublications);
            assertEquals(KOMEMovementHistoryRecord.ARRIVED, f.data.movementHistory.get(f.order.id).status);
        }
    }

    private static String[] restrictions() {
        return new String[] {"missing", KOMEConquestRouteEdge.MOUNTAIN,
            KOMEConquestRouteEdge.RIVER, KOMEConquestRouteEdge.BLOCKED};
    }

    private static final class Fixture {
        final ObservedData data = new ObservedData();
        final FixtureWorld world;
        final FixturePlayer player;
        final KOMEArmyCompany company = new KOMEArmyCompany();
        final KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(2);

        Fixture(String origin, String destination, String restriction, boolean operator) throws Exception {
            world = allocate(FixtureWorld.class);
            set(World.class, world, "provider", new WorldProviderSurface());
            world.loadedEntityList = new ArrayList<Entity>(); world.playerEntities = new ArrayList();
            player = allocate(FixturePlayer.class);
            player.id = UUID.randomUUID(); player.operator = operator;
            player.messages = new ArrayList<String>(); player.worldObj = world;
            set(Entity.class, player, "entityUniqueID", player.id);
            for (String id : new String[] {origin, destination}) {
                KOMEConquestTile tile = new KOMEConquestTile(id);
                tile.claim("gondor", 0L); tile.setAnchor(0, 10.0D, 64.0D, 20.0D);
                data.conquestTiles.put(id, tile);
            }
            if (!"missing".equals(restriction) && !"default".equals(restriction))
                data.setRouteEdge(origin, destination, restriction, "fixture", 0, 10, 64, 20, "test");
            company.id = "C-KOM80"; company.owner = player.id; company.faction = "gondor";
            company.currentTile = origin; company.status = KOMEArmyCompany.MOVING;
            company.movementOrderId = "M-KOM80";
            data.armyCompanies.put(company.id, company);
            order.id = company.movementOrderId; order.companyId = company.id; order.owner = player.id;
            order.ownerFaction = "gondor"; order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
            order.originTile = origin; order.destinationTile = destination; order.finalDestinationTile = destination;
            order.currentTile = origin; order.nextTile = destination;
            order.currentStepOriginTile = origin; order.currentStepDestinationTile = destination;
            order.currentRouteIndex = 0; order.nextRouteIndex = 1; order.finalRouteIndex = 1;
            order.distanceTiles = 1; order.totalSteps = 1;
            order.routeTiles.addAll(Arrays.asList(origin, destination)); order.traveledRouteTiles.add(origin);
            order.arrivalDimension = 0; order.arrivalX = 10; order.arrivalY = 64; order.arrivalZ = 20;
            order.arrivalMillis = 1L;
            data.armyMovements.put(order.id, order);
        }

        KOMEHiredUnitRecord addUnit() {
            KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
            unit.entity = UUID.randomUUID(); unit.owner = player.id;
            NBTTagCompound tag = unit.writeToNBT(); tag.setString("UnitClass", "CAMPAIGN"); unit.readFromNBT(tag);
            unit.currentTile = order.currentTile; unit.companyId = company.id; unit.movementOrderId = order.id;
            unit.stationedEntityData = new NBTTagCompound();
            unit.stationedEntityData.setString("fixture", "preserved stationed unit");
            data.hiredUnits.put(unit.entity, unit); company.units.add(unit.entity); order.units.add(unit.entity);
            return unit;
        }

        void command(String action) throws Exception {
            Method method = KOMECommandTroops.class.getDeclaredMethod("handleMovementAdmin",
                ICommandSender.class, EntityPlayerMP.class, KOMEWorldData.class, String[].class);
            method.setAccessible(true);
            try {
                method.invoke(new KOMECommandTroops(), player, player, data,
                    new String[] {"movement", action, order.id});
            } catch (InvocationTargetException failed) {
                Throwable cause = failed.getCause();
                if (cause instanceof RuntimeException) throw (RuntimeException) cause;
                if (cause instanceof Error) throw (Error) cause;
                throw new AssertionError(cause);
            }
        }
    }

    private static final class ObservedData extends KOMEWorldData {
        int arrivalPublications;
        ObservedData() { super("mountain-recovery"); }
        @Override public void updateMovementHistory(KOMEArmyMovementOrder order, String status) {
            if (KOMEMovementHistoryRecord.ARRIVED.equals(status)) arrivalPublications++;
            super.updateMovementHistory(order, status);
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type));
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static final class FixturePlayer extends EntityPlayerMP {
        UUID id;
        boolean operator;
        List<String> messages;
        private FixturePlayer() { super(null, null, null, null); }
        @Override public UUID getUniqueID() { return id; }
        @Override public String getCommandSenderName() { return "MountainRecoveryTester"; }
        @Override public boolean canCommandSenderUseCommand(int level, String command) { return operator; }
        @Override public void addChatMessage(IChatComponent message) { messages.add(message.getUnformattedText()); }
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
