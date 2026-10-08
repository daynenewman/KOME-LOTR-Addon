package kome.common.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import kome.common.command.KOMECommandTroops;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.entity.ai.LOTREntityAIFollowHiringPlayer;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.block.material.Material;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.storage.MapStorage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual END event, KOM-60 sampler, loaded-entity adapter, collision checks and vanilla position API. */
public class KOMECampaignTileConfinementHookTest {
    private KOMETileWorldResolver resolver;
    private KOMEServerTileAwareness awareness;
    private KOMETileAwarenessEvents hooks;
    private KOMECampaignTileConfinementService guard;
    private SafeWorld world;
    private KOMEWorldData data;

    @Before public void setup() throws Exception {
        resolver = new KOMETileWorldResolver(); resolver.publish(KOMEServerTileAwarenessTest.snapshot(1, 2, 0));
        guard = new KOMECampaignTileConfinementService(resolver);
        awareness = new KOMEServerTileAwareness(resolver); awareness.setBoundaryGuard(guard); awareness.startSession();
        hooks = new KOMETileAwarenessEvents(awareness);
        world = KOMEAccessFixture.allocate(SafeWorld.class);
        java.lang.reflect.Field field = World.class.getDeclaredField("provider"); field.setAccessible(true);
        field.set(world, new WorldProviderSurface()); world.provider.dimensionId = 173;
        java.lang.reflect.Field random = World.class.getDeclaredField("rand"); random.setAccessible(true);
        random.set(world, new java.util.Random(46L));
        world.entities = new HashMap<Integer, Entity>(); world.mapStorage = new MapStorage(null);
        data = new KOMEWorldData("guard-hook"); world.mapStorage.setData("KOME_ServerRules", data);
        data.conquestTiles.put("T001", new KOMEConquestTile("T001"));
        data.conquestTiles.put("T002", new KOMEConquestTile("T002"));
        data.conquestTiles.get("T001").setAnchor(173, -32, 64, -1);
        data.conquestTiles.get("T002").setAnchor(173, 32, 64, -1);
    }
    @After public void stop() { awareness.stopSession(); }
    private void endTick() { hooks.onServerTick(new cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent(
        cpw.mods.fml.common.gameevent.TickEvent.Phase.END)); }
    private LoadedUnit unit(boolean campaign) throws Exception {
        LoadedUnit u = KOMEAccessFixture.allocate(LoadedUnit.class); u.alive = true;
        KOMEServerTileAwarenessTest.initialize(world, u, UUID.randomUUID());
        u.width = .6F; u.height = 1.8F;
        java.lang.reflect.Field box = Entity.class.getDeclaredField("boundingBox"); box.setAccessible(true);
        box.set(u, AxisAlignedBB.getBoundingBox(0, 0, 0, .6, 1.8, .6));
        u.setPosition(-64, 64, -1); u.navigation = new PathNavigate(u, world);
        u.hiredNPCInfo = new NativeHiredInfo(u); u.hiredNPCInfo.isActive = true;
        // Seed existing squadron metadata without the absent Forge watcher/network stack.
        java.lang.reflect.Field squadron = LOTRHiredNPCInfo.class.getDeclaredField("hiredSquadron"); squadron.setAccessible(true);
        squadron.set(u.hiredNPCInfo, "Native Alpha"); u.hiredNPCInfo.xp = 127; u.hiredNPCInfo.xpLevel = 4;
        java.lang.reflect.Field owner = LOTRHiredNPCInfo.class.getDeclaredField("hiringPlayerUUID"); owner.setAccessible(true);
        UUID ownerId = UUID.randomUUID(); owner.set(u.hiredNPCInfo, ownerId);
        KOMEHiredUnitRecord r = new KOMEHiredUnitRecord(); r.entity = u.getUniqueID(); r.owner = ownerId;
        r.currentTile = "T001"; r.sourceTileId = "T002"; r.lotrCompanyValue = "Native Alpha";
        if (campaign) KOMEHiredUnitClassification.assignForCampaignWorkflow(r);
        data.hiredUnits.put(r.entity, r);
        hooks.onJoin(new net.minecraftforge.event.entity.EntityJoinWorldEvent(u, world)); return u;
    }
    @Test public void illegalDisplacementReturnPreservesExactFractionalRiderAndMountHealth() throws Exception {
        LoadedUnit rider = unit(true), mount = unit(false);
        for (LoadedUnit unit : new LoadedUnit[]{rider, mount}) {
            java.lang.reflect.Field watcher = Entity.class.getDeclaredField("dataWatcher"); watcher.setAccessible(true);
            net.minecraft.entity.DataWatcher dataWatcher = new net.minecraft.entity.DataWatcher(unit);
            dataWatcher.addObject(6, Float.valueOf(20F)); watcher.set(unit, dataWatcher);
            unit.getAttributeMap().registerAttribute(net.minecraft.entity.SharedMonsterAttributes.maxHealth).setBaseValue(20D);
        }
        rider.setHealth(3.125F); mount.setHealth(8.875F); rider.mountEntity(mount);
        KOMEHiredUnitRecord record = data.hiredUnits.get(rider.getUniqueID()); record.mounted = true;
        KOMECampaignHealth.observe(record, rider);
        rider.setPosition(64, 64, -1); mount.setPosition(64, 64, -1); endTick();
        assertTrue(rider.hiredNPCInfo.isHalted()); assertEquals(-32D, rider.posX, 0D);
        assertEquals(3.125F, rider.getHealth(), 0F); assertEquals(8.875F, mount.getHealth(), 0F);
        assertEquals(3.125F, record.writeToNBT().getCompoundTag("SurvivingHealth").getFloat("Current"), 0F);
    }
    @Test public void actualLoadedReturnStationsHaltsRefreshesKom60AndPreservesNativeIdentity() throws Exception {
        LoadedUnit u = unit(true); endTick();
        assertTrue(u.hiredNPCInfo.shouldFollowPlayer()); assertFalse(u.hiredNPCInfo.isHalted());
        UUID owner = u.hiredNPCInfo.getHiringPlayerUUID(); LOTRHiredNPCInfo.Task task = u.hiredNPCInfo.getTask();
        u.setPosition(64, 64, -1); u.motionX = 5; u.motionZ = 3; u.fallDistance = 12;
        endTick();
        assertEquals(-32, u.posX, 0); assertEquals(0, u.motionX, 0); assertEquals(0, u.fallDistance, 0);
        assertEquals("T001", awareness.current(u.getUniqueID()).observation().get().location.tileId);
        assertEquals(KOMEServerTileAwareness.Availability.AVAILABLE, awareness.current(u.getUniqueID()).availability);
        assertTrue(u.hiredNPCInfo.isActive); assertTrue(u.hiredNPCInfo.teleportAutomatically);
        assertTrue(u.hiredNPCInfo.isHalted()); assertFalse(u.hiredNPCInfo.shouldFollowPlayer());
        assertEquals(owner, u.hiredNPCInfo.getHiringPlayerUUID()); assertSame(task, u.hiredNPCInfo.getTask());
        assertEquals("Native Alpha", u.hiredNPCInfo.getSquadron());
        assertEquals(127, u.hiredNPCInfo.xp); assertEquals(4, u.hiredNPCInfo.xpLevel);
        assertEquals(1, ((NativeHiredInfo) u.hiredNPCInfo).haltCalls);
        assertEquals("T002", data.hiredUnits.get(u.getUniqueID()).sourceTileId);
        assertEquals("Native Alpha", data.hiredUnits.get(u.getUniqueID()).lotrCompanyValue);
    }
    @Test public void actualNativeSummonTeleportAcrossBoundaryIsCorrectedAtEnd() throws Exception {
        LoadedUnit u = unit(true); endTick(); summonToOwner(u, 64);
        assertEquals("T002", resolver.resolveWorldPosition(173, u.posX, u.posZ).tileId);
        endTick(); assertEquals(-32, u.posX, 0); assertTrue(u.hiredNPCInfo.isHalted());
        assertEquals("T001", data.hiredUnits.get(u.getUniqueID()).currentTile);
    }
    @Test public void actualNativeSameTileSummonAndOrdinaryCrossTileSummonRemainLegal() throws Exception {
        LoadedUnit campaign = unit(true); endTick(); summonToOwner(campaign, -100);
        double summonedX = campaign.posX; endTick(); assertEquals(summonedX, campaign.posX, 0);
        assertTrue(campaign.hiredNPCInfo.shouldFollowPlayer()); assertFalse(campaign.hiredNPCInfo.isHalted());
        LoadedUnit ordinary = unit(false); endTick(); summonToOwner(ordinary, 64);
        summonedX = ordinary.posX; endTick(); assertEquals(summonedX, ordinary.posX, 0);
        assertEquals("T002", awareness.current(ordinary.getUniqueID()).observation().get().location.tileId);
        assertTrue(ordinary.hiredNPCInfo.shouldFollowPlayer()); assertFalse(ordinary.hiredNPCInfo.isHalted());
    }
    private void summonToOwner(LoadedUnit u, double x) throws Exception {
        ownerAt(u, x);
        assertTrue("Real native summon operation must relocate", u.hiredNPCInfo.tryTeleportToHiringPlayer(true));
    }
    private void ownerAt(LoadedUnit u, double x) throws Exception {
        KOMEServerTileAwarenessTest.Player owner = KOMEServerTileAwarenessTest.player(world, u.hiredNPCInfo.getHiringPlayerUUID());
        java.lang.reflect.Field box = Entity.class.getDeclaredField("boundingBox"); box.setAccessible(true);
        box.set(owner, AxisAlignedBB.getBoundingBox(x - .3, 64, -64.3, x + .3, 65.8, -63.7));
        owner.posX = x; owner.posY = 64; owner.posZ = -64; world.owner = owner;
    }
    @Test public void loadedMountedReturnMovesMountAndStopsMomentumWithoutUnhiring() throws Exception {
        LoadedUnit rider = unit(true), mount = unit(false);
        rider.ridingEntity = mount; mount.riddenByEntity = rider; endTick();
        rider.setPosition(64, 64, -1); mount.setPosition(64, 64, -1); mount.motionX = 2;
        rider.motionY = mount.motionY = 3; rider.fallDistance = mount.fallDistance = 12;
        endTick(); assertEquals(-32, rider.posX, 0); assertEquals(-32, mount.posX, 0);
        assertEquals(0, mount.motionX, 0); assertSame(mount, rider.ridingEntity); assertTrue(rider.hiredNPCInfo.isActive);
        assertSame(rider, mount.riddenByEntity); assertEquals(0, rider.motionY, 0); assertEquals(0, mount.motionY, 0);
        assertEquals(0, rider.fallDistance, 0); assertEquals(0, mount.fallDistance, 0);
        assertTrue(rider.hiredNPCInfo.isHalted()); assertTrue(mount.hiredNPCInfo.shouldFollowPlayer());
    }
    @Test public void unchangedCampaignChecksReuseGeometryAndOrdinaryUnitsNeverEnterService() throws Exception {
        LoadedUnit campaign = unit(true);
        for (int i = 0; i < 300; i++) unit(false);
        endTick(); long resolutions = awareness.resolutionCount();
        for (int i = 0; i < 100; i++) endTick();
        assertEquals(301, resolutions); assertEquals(resolutions, awareness.resolutionCount());
        assertEquals(101, guard.inspectionCount()); assertEquals(0, guard.correctionAttemptCount());
        campaign.setPosition(-63, 64, -1); endTick(); assertEquals(resolutions + 1, awareness.resolutionCount());
        assertTrue(campaign.hiredNPCInfo.shouldFollowPlayer()); assertEquals(0, ((NativeHiredInfo) campaign.hiredNPCInfo).haltCalls);
        System.out.println("KOM46_GUARD loadedHires=301 campaign=1 stationaryTicks=100 addedGeometryResolutions=0"
            + " campaignInspections=100 ordinaryInspections=0 extraEntityScans=0");
    }
    @Test public void unchangedPhysicalTileStillChecksChangedStrategicAuthority() throws Exception {
        LoadedUnit u = unit(true); endTick();
        // Newly contradictory company authority is diagnosed even with no physical transition.
        KOMEArmyCompany c = new KOMEArmyCompany(); c.id = "C1"; c.currentTile = "T002"; c.units.add(u.getUniqueID());
        KOMEHiredUnitRecord r = data.hiredUnits.get(u.getUniqueID()); r.companyId = c.id; data.armyCompanies.put(c.id, c);
        endTick(); assertEquals(-64, u.posX, 0);
        assertEquals("STRATEGIC_CONTRADICTION", data.centralAudit.get(0).action);
        assertFalse(u.hiredNPCInfo.isHalted());
    }
    @Test public void staleGeometrySampleCannotTriggerReturn() throws Exception {
        LoadedUnit u = unit(true); endTick(); u.setPosition(64, 64, -1);
        KOMETileWorldResolver.ReadView old = resolver.readView();
        KOMETileResolution oldLocation = old.resolveWorldPosition(173, 64, -1);
        resolver.publish(KOMEServerTileAwarenessTest.snapshot(2, 1, 0));
        assertFalse(guard.onSample(u, oldLocation, old, awareness.currentTick()));
        assertEquals(64, u.posX, 0); assertEquals(0, guard.correctionAttemptCount());
        endTick(); assertEquals(64, u.posX, 0);
        assertFalse(u.hiredNPCInfo.isHalted());
    }
    @Test public void unloadedIncarnationDropsTransientStateAndNeverMovesOrHaltsUnloadedUnit() throws Exception {
        LoadedUnit u = unit(true); endTick(); world.entities.remove(u.getEntityId());
        u.setPosition(64, 64, -1); endTick(); assertEquals(64, u.posX, 0);
        assertEquals(0, awareness.trackedCount()); assertEquals(0, guard.correctionAttemptCount());
        assertFalse(u.hiredNPCInfo.isHalted());
    }
    @Test public void safeStationRequiresSupportCollisionClearanceAndNoLiquid() throws Exception {
        LoadedUnit u = unit(true);
        assertTrue(KOMECommandTroops.isSafeConfinementPosition(u, -64, 64, -1));
        world.noFloor = true; assertFalse(KOMECommandTroops.isSafeConfinementPosition(u, -64, 64, -1));
        world.noFloor = false; world.collision = true; assertFalse(KOMECommandTroops.isSafeConfinementPosition(u, -64, 64, -1));
        world.collision = false; world.liquid = true; assertFalse(KOMECommandTroops.isSafeConfinementPosition(u, -64, 64, -1));
    }
    @Test public void actualArrivalHelperUsesCanonicalStationAfterResetNotOldSafePosition() throws Exception {
        LoadedUnit u = unit(true); endTick();
        data.conquestTiles.get("T001").setAnchor(173, -32, 64, -1);
        guard.reset(); u.setPosition(64, 64, -1);
        double[] candidate = KOMECommandTroops.findSafeConfinementPosition(data, u, "T001", resolver.readView());
        assertNotNull(candidate); assertEquals(-32, candidate[0], 0); assertEquals(-1, candidate[2], 0);
        assertEquals(64, u.posX, 0); // Placement selection never leaves the live unit at a probe.
        endTick(); assertEquals("T001", awareness.current(u.getUniqueID()).observation().get().location.tileId);
        assertEquals("RETURNED_TO_PLACEMENT", data.centralAudit.get(0).action);
        assertTrue(u.hiredNPCInfo.isHalted());
    }
    @Test public void actualArrivalHelperExhaustionLatchesWithoutRepeatedEntityRelocation() throws Exception {
        LoadedUnit u = unit(true); data.conquestTiles.get("T001").setAnchor(173, -32, 64, -1);
        world.collision = true; u.setPosition(64, 64, -1); endTick();
        assertEquals(64, u.posX, 0); long attempts = guard.correctionAttemptCount();
        // Candidates outside this edge-adjacent tile are rejected before terrain probing.
        assertTrue(world.topQueries > 0 && world.topQueries <= 48); assertTrue(world.collisionChecks <= 1 + 48 * 6);
        int searches = world.topQueries, checks = world.collisionChecks;
        for (int i = 0; i < 200; i++) endTick();
        assertEquals(attempts, guard.correctionAttemptCount()); assertEquals(1, data.centralAudit.size());
        assertFalse(u.isDead);
        assertEquals(searches, world.topQueries); assertEquals(checks, world.collisionChecks);
        assertFalse(u.hiredNPCInfo.isHalted());
    }

    @Test public void obstructedStationOriginUsesBoundedNearbySafePlacementAndRestoresProbe() throws Exception {
        LoadedUnit u = unit(true); world.obstructOrigin = true; u.setPosition(64, 64, -1);
        double[] candidate = KOMECommandTroops.findSafeConfinementPosition(data, u, "T001", resolver.readView());
        assertNotNull(candidate); assertEquals(64, u.posX, 0); assertTrue(candidate[0] != -32 || candidate[2] != -1);
        assertEquals("T001", resolver.resolveWorldPosition(173, candidate[0], candidate[2]).tileId);
        assertTrue(Math.hypot(candidate[0] + 32, candidate[2] + 1) < 25);
        assertTrue(world.topQueries > 0 && world.topQueries <= 48);
        endTick(); assertEquals(candidate[0], u.posX, 0); assertTrue(u.hiredNPCInfo.isHalted());
    }

    @Test public void canonicalArrivalWaypointTakesPriorityOverLegacyPhysicalTileAnchor() throws Exception {
        LoadedUnit u = unit(true); endTick();
        data.setTileWaypoint("T001", KOMETileWaypoint.RALLY, 173, -48, 64, -1, "Manual", true);
        u.setPosition(-0.01, 64, -1); endTick(); assertFalse(u.hiredNPCInfo.isHalted());
        u.setPosition(64, 64, -1); endTick();
        assertEquals(-48, u.posX, 0); assertTrue(u.hiredNPCInfo.isHalted());
    }

    @Test public void withinTileCombatAndFollowingDoNotHalt() throws Exception {
        LoadedUnit u = unit(true); u.hiredNPCInfo.inCombat = true;
        for (double x : new double[] {-127, -64, -0.01}) { u.setPosition(x, 64, -1); endTick(); }
        assertTrue(u.hiredNPCInfo.inCombat); assertTrue(u.hiredNPCInfo.shouldFollowPlayer());
        assertEquals(0, ((NativeHiredInfo) u.hiredNPCInfo).haltCalls); assertEquals(0, guard.correctionAttemptCount());
    }

    @Test public void committedLegalArrivalDoesNotHaltButWaitingNextStepIllegalCrossingDoes() throws Exception {
        LoadedUnit u = unit(true); endTick(); KOMEHiredUnitRecord r = data.hiredUnits.get(u.getUniqueID());
        KOMEArmyCompany c = new KOMEArmyCompany(); c.id = "C1"; c.currentTile = "T002"; c.units.add(r.entity);
        r.companyId = c.id; r.currentTile = "T002"; data.armyCompanies.put(c.id, c);
        KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(2); order.id = "M1"; order.companyId = c.id;
        order.currentTile = "T002"; order.nextTile = "T001"; order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        r.movementOrderId = c.movementOrderId = order.id; data.armyMovements.put(order.id, order);
        u.setPosition(64, 64, -1); endTick(); assertEquals(64, u.posX, 0);
        assertTrue(u.hiredNPCInfo.shouldFollowPlayer()); assertEquals(0, guard.correctionAttemptCount());
        net.minecraft.nbt.NBTTagCompound before = order.writeToNBT(), recordBefore = r.writeToNBT(), companyBefore = c.writeToNBT();
        u.setPosition(-64, 64, -1); endTick(); assertEquals(32, u.posX, 0); assertTrue(u.hiredNPCInfo.isHalted());
        assertEquals(before, order.writeToNBT()); assertEquals(recordBefore, r.writeToNBT()); assertEquals(companyBefore, c.writeToNBT());
        assertEquals("T002", awareness.current(u.getUniqueID()).observation().get().location.tileId);
    }

    @Test public void realNativeFollowExecutionStopsAfterOneIllegalReturnAndDoesNotPingPong() throws Exception {
        LoadedUnit u = unit(true); ownerAt(u, 64);
        LOTREntityAIFollowHiringPlayer follow = KOMEAccessFixture.allocate(LOTREntityAIFollowHiringPlayer.class);
        java.lang.reflect.Field npc = LOTREntityAIFollowHiringPlayer.class.getDeclaredField("theNPC"); npc.setAccessible(true); npc.set(follow, u);
        assertTrue(follow.shouldExecute()); endTick(); assertEquals(0, guard.correctionAttemptCount());
        u.setPosition(64, 64, -1); endTick(); assertEquals(-32, u.posX, 0);
        for (int i = 0; i < 200; i++) {
            assertFalse(follow.shouldExecute()); assertFalse(follow.continueExecuting()); endTick();
        }
        assertEquals(1, guard.correctionAttemptCount()); assertEquals(1, ((NativeHiredInfo) u.hiredNPCInfo).haltCalls);
        assertEquals(1, data.centralAudit.size()); assertEquals(-32, u.posX, 0);
    }

    @Test public void illegalReturnFromNativeGuardStateUsesSafeNativeHaltPath() throws Exception {
        LoadedUnit u = unit(true); u.hiredNPCInfo.guardMode = true;
        u.setPosition(64, 64, -1); endTick();
        assertFalse(u.hiredNPCInfo.isGuardMode()); assertTrue(u.hiredNPCInfo.isHalted());
    }

    /** Only networking is inert; native halt/ready/Follow/summon logic is exercised unchanged. */
    private static class NativeHiredInfo extends LOTRHiredNPCInfo {
        int haltCalls;
        NativeHiredInfo(LoadedUnit u) { super(u); }
        @Override public void halt() { super.halt(); haltCalls++; }
        @Override public void sendClientPacket(boolean full) { }
    }

    public static class LoadedUnit extends KOMEServerTileAwarenessTest.Unit {
        PathNavigate navigation;
        private LoadedUnit() { super(); }
        @Override public PathNavigate getNavigator() { return navigation; }
        // Native summon calls this after relocating. The inert fixture has no combat event bus.
        @Override public void setAttackTarget(net.minecraft.entity.EntityLivingBase target, boolean speak) { }
    }
    public static class SafeWorld extends KOMEServerTileAwarenessTest.TestWorld {
        private static final Block FLOOR = new Block(Material.rock) { };
        private static final Block AIR = new Block(Material.air) { };
        boolean noFloor, collision, liquid, obstructOrigin;
        int topQueries, collisionChecks;
        net.minecraft.entity.player.EntityPlayer owner;
        private SafeWorld() { super(); }
        @Override public boolean blockExists(int x, int y, int z) { return true; }
        @Override public int getBlockMetadata(int x, int y, int z) { return 0; }
        @Override public net.minecraft.entity.player.EntityPlayer func_152378_a(UUID id) {
            return owner != null && id.equals(owner.getUniqueID()) ? owner : null;
        }
        @Override public List func_147461_a(AxisAlignedBB box) { return new ArrayList(); }
        @Override public int getTopSolidOrLiquidBlock(int x, int z) { topQueries++; return 64; }
        @Override public net.minecraft.world.chunk.IChunkProvider getChunkProvider() { return null; }
        @Override public net.minecraft.world.chunk.Chunk getChunkFromChunkCoords(int x, int z) { return null; }
        @Override public boolean checkChunksExist(int a, int b, int c, int d, int e, int f) { return true; }
        @Override public Block getBlock(int x, int y, int z) { return noFloor || y != 63 ? AIR : FLOOR; }
        @Override public List getCollidingBoundingBoxes(Entity entity, AxisAlignedBB box) {
            collisionChecks++;
            boolean atOrigin = Math.abs((box.minX + box.maxX) / 2 + 32) < .01 && Math.abs((box.minZ + box.maxZ) / 2 + 1) < .01;
            List boxes = new ArrayList(); if (collision || obstructOrigin && atOrigin) boxes.add(box); return boxes;
        }
        @Override public boolean isAnyLiquid(AxisAlignedBB box) { return liquid; }
    }
}
