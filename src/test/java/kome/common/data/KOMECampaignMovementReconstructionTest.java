package kome.common.data;

import kome.common.KOMEAccessFixture;
import kome.common.command.KOMECommandTroops;
import lotr.common.entity.npc.LOTREntityGondorMan;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import org.junit.*;
import java.lang.reflect.Method;
import java.util.*;
import static org.junit.Assert.*;

/** Real departure/arrival services and join-world callback, with inert terrain/network only. */
public class KOMECampaignMovementReconstructionTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    private KOMEAccessFixture f;
    private KOMEWorldData data;
    private KOMEHiredUnitRecord record;
    private KOMEArmyCompany company;
    private KOMEArmyMovementOrder order;
    private MovementNpc source;
    private UUID oldId;
    private NBTTagCompound before;
    private int joins;
    private Object oldClass, oldName;
    private static final String TYPE = "kome.test.movement-health";

    @Before public void setup() throws Exception {
        config = new KOMEPopulationTestConfig();
        f = new KOMEAccessFixture(); data = f.data;
        f.world.flatTerrain = true; f.world.spawnSucceeds = true;
        f.world.testChunkProvider = (net.minecraft.world.chunk.IChunkProvider)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class[]{net.minecraft.world.chunk.IChunkProvider.class},(proxy,method,args)->{
                if(method.getReturnType()==boolean.class)return true;
                if(method.getReturnType()==net.minecraft.world.chunk.Chunk.class)return new net.minecraft.world.chunk.Chunk(f.world,(Integer)args[0],(Integer)args[1]);
                return null;
            });
        java.lang.reflect.Field chunks=World.class.getDeclaredField("chunkProvider");chunks.setAccessible(true);chunks.set(f.world,f.world.testChunkProvider);
        f.world.rand = new Random(3); f.world.playerEntities.clear();
        f.world.provider.dimensionId = KOMETileTestResources.dimension();
        oldClass = EntityList.stringToClassMapping.put(TYPE, MovementNpc.class);
        oldName = EntityList.classToStringMapping.put(MovementNpc.class, TYPE);
        for (String id : Arrays.asList("T100", "T101")) {
            KOMEConquestTile tile = new KOMEConquestTile(id); tile.claim("gondor", 0);
            tile.hasAnchor = true; tile.anchorDimension = f.world.provider.dimensionId;
            int[] point=tilePoint(id);
            tile.anchorX = point[0]; tile.anchorY = 65; tile.anchorZ = point[1];
            data.conquestTiles.put(id, tile);
        }
        data.setRouteEdge("T100", "T101", KOMEConquestRouteEdge.OPEN, "test", 0, 0, 65, 0, "test");
        source = new MovementNpc(f.world); source.setHealth(7.375F);
        source.setPosition(KOMETileTestResources.x(), 65, KOMETileTestResources.z());
        oldId = source.getUniqueID(); f.world.loadedEntityList.add(source);
        record = new KOMEHiredUnitRecord(); record.entity = oldId; record.owner = UUID.randomUUID();
        source.hiredNPCInfo.isActive=true;
        java.lang.reflect.Field hiringPlayer=lotr.common.entity.npc.LOTRHiredNPCInfo.class.getDeclaredField("hiringPlayerUUID");
        hiringPlayer.setAccessible(true);hiringPlayer.set(source.hiredNPCInfo,record.owner);
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        record.currentTile = "T100"; record.companyId = "C1"; record.movementOrderId = "M1";
        record.populationSpent = 20; record.sourceTileId = "T100";
        data.hiredUnits.put(oldId, record);
        record.stationedEntityData = KOMEEntitySnapshots.snapshot(record, source);
        before = (NBTTagCompound)record.survivingHealth.copy();
        company = new KOMEArmyCompany(); company.id = "C1"; company.owner = record.owner;
        company.faction = "gondor"; company.currentTile = "T100"; company.units.add(oldId);
        company.movementOrderId = "M1"; company.movementAllowance = 1;
        data.armyCompanies.put(company.id, company);
        order = KOMEArmyMovementOrder.newRoute(1); order.id = "M1"; order.owner = record.owner;
        order.companyId = company.id; order.ownerFaction = "gondor";
        order.originTile = order.currentTile = "T100"; order.destinationTile = order.nextTile = "T101";
        order.routeTiles.addAll(Arrays.asList("T100", "T101")); order.units.add(oldId);
        order.currentRouteIndex = 0; order.nextRouteIndex = order.finalRouteIndex = 1;
        order.arrivalDimension = f.world.provider.dimensionId;
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP; order.dailyStepsRemaining = 1;
        data.armyMovements.put(order.id, order);
        f.world.spawnCallback = entity -> {
            joins++;
            KOMEEvents events=new KOMEEvents();EntityJoinWorldEvent event=new EntityJoinWorldEvent(entity,f.world);
            events.onEntityJoinWorld(event);assertFalse(event.isCanceled());events.onRecoveryEntityLoaded(event);
            assertSame("source remains canonical during synchronous join", record, data.hiredUnits.get(oldId));
            assertFalse(data.hiredUnits.containsKey(entity.getUniqueID()));
        };
    }
    @After public void teardown() throws Exception {
        if(oldClass==null)EntityList.stringToClassMapping.remove(TYPE);else EntityList.stringToClassMapping.put(TYPE,oldClass);
        if(oldName==null)EntityList.classToStringMapping.remove(MovementNpc.class);else EntityList.classToStringMapping.put(MovementNpc.class,oldName);
        if(config!=null)config.close();
    }
    private static int[] tilePoint(String id) {
        KOMETileRasterSnapshot raster=KOMETileTestResources.real();int color=raster.colorsById().get(id);
        int[] pixels=raster.copyArgbPixels();
        for(int i=0;i<pixels.length;i++)if((pixels[i]>>>24)>24&&(pixels[i]&0xFFFFFF)==color)
            return new int[]{KOMETileTestResources.worldX(i%raster.width),KOMETileTestResources.worldZ(i/raster.width)};
        throw new AssertionError(id);
    }
    private net.minecraft.entity.passive.EntityPig mount() {
        net.minecraft.entity.passive.EntityPig mount=new net.minecraft.entity.passive.EntityPig(f.world);
        mount.setHealth(4.625F);mount.setPosition(source.posX,source.posY,source.posZ);source.mountEntity(mount);
        f.world.loadedEntityList.add(mount);record.mounted=true;
        record.stationedEntityData=KOMEEntitySnapshots.snapshot(source);
        KOMECampaignHealth.observe(record,source);before=(NBTTagCompound)record.survivingHealth.copy();
        return mount;
    }
    private void depart() throws Exception {
        Method m = KOMECommandTroops.class.getDeclaredMethod("processWaitingStepDepartures", KOMEWorldData.class, World.class, long.class);
        m.setAccessible(true); m.invoke(null, data, f.world, 100L);
        assertNotNull(order.lastSpawnFailureDetails, record.movingEntityData);
        f.world.loadedEntityList.removeIf(value -> ((Entity)value).isDead);
        assertTrue(source.isDead); assertFalse(f.world.loadedEntityList.contains(source));
        assertEquals(KOMEArmyMovementOrder.MOVING, order.status);
    }
    private void arrive() { KOMECommandTroops.processArrivals(data, f.world, 200L, false); f.world.loadedEntityList.removeIf(value -> ((Entity)value).isDead); }
    private void retry() { order.spawnRetryPaused=false;order.nextSpawnRetryMillis=0L;order.status=KOMEArmyMovementOrder.PENDING_SPAWN;arrive(); }
    private void assertRetained() {
        data.reconcileHiredUnitMovementLinks();
        assertSame(record, data.hiredUnits.get(oldId));assertEquals(1,data.hiredUnits.size());
        assertEquals(oldId,record.entity);assertEquals(oldId,company.units.get(0));assertEquals(oldId,order.units.get(0));
        assertEquals("M1",record.movementOrderId);assertNotNull(record.movingEntityData);
        assertEquals(before,record.survivingHealth);assertEquals(20,record.populationSpent);
        assertFalse(record.populationReturned);assertEquals(0,order.completedSteps);
        assertTrue(f.world.loadedEntityList.isEmpty());
    }
    private void assertArrived() {
        assertEquals(order.lastSpawnFailureDetails,1,order.completedSteps);
        assertEquals(1,data.hiredUnits.size());assertNotEquals(oldId,record.entity);
        assertSame(record,data.hiredUnits.get(record.entity));assertFalse(data.hiredUnits.containsKey(oldId));
        assertEquals(record.entity,company.units.get(0));assertEquals(record.entity,order.units.get(0));
        assertEquals("T101",company.currentTile);assertEquals("T101",record.currentTile);
        assertNull(record.movingEntityData);assertEquals(record.mounted?2:1,f.world.loadedEntityList.size());
        Entity physical=(Entity)f.world.loadedEntityList.stream().filter(value -> ((Entity)value).getUniqueID().equals(record.entity)).findFirst().get();
        assertEquals(record.entity,physical.getUniqueID());assertFalse(physical.isDead);assertTrue(source.isDead);
        KOMECampaignHealthProjection p=KOMECampaignHealthProjection.read(record.survivingHealth);
        KOMECampaignHealthProjection prior=KOMECampaignHealthProjection.read(before);
        assertEquals(7.375F,((MovementNpc)physical).getHealth(),0F);
        assertEquals(prior.target,p.target);assertEquals(prior.revision,p.revision);
        assertEquals(prior.transition,p.transition);assertEquals(prior.fence,p.fence);assertEquals(record.entity,p.alias);
        arrive();assertEquals(1,order.completedSteps);assertEquals(record.mounted?2:1,f.world.loadedEntityList.size());
    }
    @Test public void productionDepartureJoinRekeyAndArrivalPreserveHealthAndAuthority() throws Exception {
        depart();arrive();assertArrived();assertEquals(1,joins);
        NBTTagCompound root=new NBTTagCompound();data.writeToNBT(root);
        KOMEWorldData restarted=new KOMEWorldData();restarted.readFromNBT(root);
        assertFalse(restarted.hiredUnits.containsKey(oldId));
        KOMEHiredUnitRecord saved=restarted.hiredUnits.get(record.entity);
        assertNotNull(saved);assertEquals(record.survivingHealth,saved.survivingHealth);
        assertEquals(record.entity,restarted.armyCompanies.get("C1").units.get(0));
        assertEquals(record.entity,restarted.armyMovements.get("M1").units.get(0));
    }
    @Test public void throwingJoinCallbackRetainsRecordAndSnapshotAcrossRetriesAndRestart() throws Exception {
        depart(); final java.util.function.Consumer<Entity> actual=f.world.spawnCallback;
        f.world.spawnCallback=entity->{actual.accept(entity);throw new IllegalArgumentException("injected join rejection");};
        for(int i=0;i<3;i++){retry();assertRetained();assertTrue(order.lastSpawnFailureDetails.contains("injected join rejection"));}
        NBTTagCompound root=new NBTTagCompound();data.writeToNBT(root);
        KOMEWorldData restarted=new KOMEWorldData();restarted.readFromNBT(root);data=restarted;
        f.world.mapStorage.setData("KOME_ServerRules",data);
        record=data.hiredUnits.get(oldId);company=data.armyCompanies.get("C1");order=data.armyMovements.get("M1");
        assertRetained();f.world.spawnCallback=actual;retry();assertArrived();
    }
    @Test public void healthRekeyRejectionRollsBackBeforeStrategicPublication() throws Exception {
        depart();final java.util.function.Consumer<Entity> actual=f.world.spawnCallback;
        f.world.spawnCallback=entity->{actual.accept(entity);record.survivingHealth.setString("PhysicalAlias",UUID.randomUUID().toString());};
        arrive();assertRetained();assertEquals("RECOVERY_IDENTITY_CONTRADICTION",order.lastSpawnFailureCode);
        f.world.spawnCallback=actual;retry();assertArrived();
    }
    @Test public void mountedMovementPreservesBothLogicalTargetsAndExactHp() throws Exception {
        Entity oldMount=mount();
        KOMECampaignHealthProjection previous=KOMECampaignHealthProjection.read(before.getCompoundTag("Riding"));
        depart();arrive();assertArrived();assertTrue(oldMount.isDead);
        KOMECampaignHealthProjection current=KOMECampaignHealthProjection.read(record.survivingHealth.getCompoundTag("Riding"));
        assertEquals(previous.target,current.target);assertEquals(previous.revision,current.revision);
        assertEquals(previous.transition,current.transition);assertEquals(previous.fence,current.fence);
        assertNotEquals(oldMount.getUniqueID(),current.alias);assertEquals(4.625F,current.hp,0F);
    }
    @Test public void throwingRiderJoinCleansUpAlreadySpawnedMountAndRetainsBothSnapshots() throws Exception {
        mount();depart();final java.util.function.Consumer<Entity> actual=f.world.spawnCallback;
        f.world.spawnCallback=entity->{actual.accept(entity);if(entity instanceof MovementNpc)throw new IllegalArgumentException("rider rejected after mount");};
        arrive();assertRetained();assertTrue(order.lastSpawnFailureDetails.contains("rider rejected after mount"));
        f.world.spawnCallback=actual;retry();assertArrived();
    }
    /** JUnit lacks the launch transformer: invoke its NBT bridge around the real native codec. */
    public static class MovementNpc extends LOTREntityGondorMan {
        private static World constructing(World world){world.isRemote=true;return world;}
        public MovementNpc(World world){super(constructing(world));world.isRemote=false;
            hiredNPCInfo=new lotr.common.entity.npc.LOTRHiredNPCInfo(this){@Override public void halt(){ /* inert AI/network */ }};
        }
        @Override public void readFromNBT(NBTTagCompound nbt){
            KOMECampaignHealthEntity.beginRead(this,nbt);
            worldObj.isRemote=true;
            try{super.readFromNBT(nbt);}finally{worldObj.isRemote=false;}
            KOMECampaignHealthEntity.endRead(this,nbt);
        }
    }
}
