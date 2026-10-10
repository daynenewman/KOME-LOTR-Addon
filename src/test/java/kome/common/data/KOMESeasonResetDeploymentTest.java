package kome.common.data;

import java.lang.reflect.Field;
import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTREntityRohanMan;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.profiler.Profiler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.*;
import net.minecraft.world.chunk.*;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraftforge.common.DimensionManager;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMESeasonResetDeploymentTest {
    @Rule public KOMETileTestResources geometry=new KOMETileTestResources();

    @Test public void realAdapterMovesExistingMountedObjectsWithoutChangingUuidHpOrSavedSnapshots() throws Exception {
        try(Session s=new Session()) {
            TestNpc rider=s.npc(3.25F), mount=s.npc(7.125F);
            rider.ridingEntity=mount; mount.riddenByEntity=rider;
            s.entry.units.add(rider.getUniqueID());
            s.world.loadedEntityList.add(rider); s.world.loadedEntityList.add(mount);
            KOMEHiredUnitRecord record=new KOMEHiredUnitRecord(); record.entity=rider.getUniqueID(); record.owner=UUID.randomUUID();
            record.stationedEntityData=new NBTTagCompound(); record.stationedEntityData.setString("Sentinel","unchanged");
            s.data.hiredUnits.put(record.entity,record);
            NBTTagCompound snapshot=(NBTTagCompound)record.stationedEntityData.copy();
            UUID id=rider.getUniqueID(), mountId=mount.getUniqueID();
            assertEquals("",s.adapter.apply(s.data,s.entry,"1:C1"));
            assertSame(rider,KOMESeasonResetDeployment.find(id)); assertSame(mount,rider.ridingEntity);
            assertEquals(mountId,mount.getUniqueID());
            assertEquals(3.25F,rider.getHealth(),0F); assertEquals(7.125F,mount.getHealth(),0F);
            assertEquals(snapshot,record.stationedEntityData);
            assertTrue(KOMESeasonResetDeployment.inTile(rider,s.entry.destination));
            assertEquals(rider.posX,mount.posX,0D); assertEquals(rider.posZ,mount.posZ,0D);
            assertEquals(0,s.world.spawns);
            KOMEFactionCapitalRecord capital=KOMEFactionCapitalService.getCapital(s.data,"gondor");
            assertFalse("Ordinary deployment must still reject the returned unit's occupied volume",
                KOMEStrategicDeploymentResolver.validateStored(s.world,capital.getCapitalTileId(),
                    capital.getDeploymentDimensionId(),capital.getDeploymentX(),capital.getDeploymentY(),capital.getDeploymentZ()).valid);
            int moves=s.world.moves;
            assertEquals("",s.adapter.apply(s.data,s.entry,"1:C1"));
            assertEquals(moves,s.world.moves); assertEquals(0,s.world.spawns);
        }
    }

    @Test public void partialReturnRetryPlacesRemainingSurvivorWithoutMovingOrOverlappingReturnedUnit() throws Exception {
        try(Session s=new Session()) {
            TestNpc first=s.npc(3.25F);s.entry.units.add(first.getUniqueID());s.world.loadedEntityList.add(first);
            KOMEHiredUnitRecord record=new KOMEHiredUnitRecord();record.entity=first.getUniqueID();record.owner=UUID.randomUUID();s.data.hiredUnits.put(record.entity,record);
            assertEquals("",s.adapter.apply(s.data,s.entry,"1:C1"));
            double x=first.posX,z=first.posZ;
            TestNpc remaining=s.npc(2.75F);s.entry.units.add(remaining.getUniqueID());s.world.loadedEntityList.add(remaining);
            KOMEHiredUnitRecord other=new KOMEHiredUnitRecord();other.entity=remaining.getUniqueID();other.owner=record.owner;s.data.hiredUnits.put(other.entity,other);
            assertEquals("",s.adapter.apply(s.data,s.entry,"1:C1"));
            assertEquals(x,first.posX,0D);assertEquals(z,first.posZ,0D);
            assertFalse(first.boundingBox.intersectsWith(remaining.boundingBox));
            assertEquals(3.25F,first.getHealth(),0F);assertEquals(2.75F,remaining.getHealth(),0F);
            assertEquals("1:C1",remaining.getEntityData().getString(KOMESeasonResetDeployment.RECEIPT));
            assertEquals(0,s.world.spawns);
        }
    }

    @Test public void observedOriginLocatorIsDurablyClearedBeforeMountedReturn() throws Exception {
        try(Session s=new Session()) {
            TestNpc rider=s.npc(3.25F),mount=s.npc(7.125F);
            rider.ridingEntity=mount;mount.riddenByEntity=rider;
            s.world.loadedEntityList.add(rider);s.world.loadedEntityList.add(mount);
            s.entry.units.add(rider.getUniqueID());
            KOMEArmyCompany company=new KOMEArmyCompany();company.id="C1";company.owner=UUID.randomUUID();
            company.currentTile=s.entry.origin;company.units.add(rider.getUniqueID());
            s.data.armyCompanies.put(company.id,company);
            KOMEHiredUnitRecord record=new KOMEHiredUnitRecord();record.entity=rider.getUniqueID();
            record.owner=company.owner;record.companyId=company.id;record.currentTile=s.entry.origin;
            record.mounted=true;KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
            s.data.hiredUnits.put(record.entity,record);
            assertTrue(KOMEHiredUnitPhysicalLocatorService.observe(s.data,record,rider,
                KOMEHiredUnitPhysicalLocator.CaptureKind.LIVE_OBSERVATION,1L,true));
            assertEquals(s.entry.origin,record.getPhysicalLocator().getPhysicalTileId());
            final int[] checkpoints={0};
            KOMESeasonResetDeployment adapter=new KOMESeasonResetDeployment(s.world,
                (world,entities,touched)->{
                    assertNull(record.getPhysicalLocator());
                    record.currentTile=s.entry.destination;
                    company.currentTile=s.entry.destination;
                    record.writeToNBT(); // The old origin locator made strategic publication unsaveable.
                    return "";
                },data->{
                    assertNull("Persist locator invalidation before relocation",record.getPhysicalLocator());
                    assertTrue(KOMESeasonResetDeployment.inTile(rider,s.entry.origin));
                    record.writeToNBT();checkpoints[0]++;
                });
            assertEquals("",adapter.apply(s.data,s.entry,"1:C1"));
            assertEquals(1,checkpoints[0]);
            assertSame(mount,rider.ridingEntity);
            assertEquals(3.25F,rider.getHealth(),0F);assertEquals(7.125F,mount.getHealth(),0F);
            int moves=s.world.moves;
            assertEquals("",adapter.apply(s.data,s.entry,"1:C1"));
            assertEquals(moves,s.world.moves);assertEquals(1,checkpoints[0]);
            assertTrue(KOMEHiredUnitPhysicalLocatorService.observe(s.data,record,rider,
                KOMEHiredUnitPhysicalLocator.CaptureKind.LIVE_OBSERVATION,2L,true));
            assertEquals(s.entry.destination,record.getPhysicalLocator().getPhysicalTileId());
            record.writeToNBT();
        }
    }

    @Test public void unloadedStationarySnapshotNeverCreatesAReplacementAndUnsafeAnchorDoesNotMoveUnit() throws Exception {
        try(Session s=new Session()) {
            TestNpc npc=s.npc(2.75F); s.entry.units.add(npc.getUniqueID());
            KOMEHiredUnitRecord unit=new KOMEHiredUnitRecord(); unit.entity=npc.getUniqueID();
            unit.stationedEntityData=new NBTTagCompound(); unit.stationedEntityData.setFloat("HealF",2.75F);
            s.data.hiredUnits.put(unit.entity,unit);
            assertTrue(s.adapter.apply(s.data,s.entry,"1:C1").contains("Load company unit"));
            assertEquals(0,s.world.spawns);
            s.world.loadedEntityList.add(npc); s.world.blocked=true;
            double origin=npc.posX;
            assertFalse(s.adapter.apply(s.data,s.entry,"1:C1").isEmpty());
            assertEquals(origin,npc.posX,0D); assertEquals(2.75F,npc.getHealth(),0F);
            assertFalse(npc.getEntityData().hasKey(KOMESeasonResetDeployment.RECEIPT));
        }
    }

    @Test public void nativeUnloadedUnitsNeedNoDeploymentAndMissingDimensionStaysPending() throws Exception {
        try(Session s=new Session()) {
            s.registry.clear(); s.entry.units.add(UUID.randomUUID());
            assertTrue(s.adapter.apply(s.data,s.entry,"1:C1").contains("dimension"));
            s.entry.returnRequired=false; s.entry.capital=null;
            assertEquals("",s.adapter.apply(s.data,s.entry,"1:C1"));
            assertEquals(0,s.world.spawns);
        }
    }

    @Test public void snapshotHydrationKeepsUuidsFractionalHpMountsAndOriginalNbt() {
        Object previous=EntityList.stringToClassMapping.put("kom28-fixture",SnapshotEntity.class);
        Object previousName=EntityList.classToStringMapping.put(SnapshotEntity.class,"kom28-fixture");
        try {
            SnapshotEntity rider=new SnapshotEntity(null), mount=new SnapshotEntity(null);
            rider.health=3.25F; mount.health=7.125F; rider.ridingEntity=mount; mount.riddenByEntity=rider;
            NBTTagCompound saved=new NBTTagCompound(); assertTrue(rider.writeMountToNBT(saved));
            NBTTagCompound original=(NBTTagCompound)saved.copy();
            Entity restored=KOMESeasonResetDeployment.restoreTree(saved,null);
            assertNotNull(restored); assertNotSame(rider,restored);
            assertEquals(rider.getUniqueID(),restored.getUniqueID());
            assertEquals(mount.getUniqueID(),restored.ridingEntity.getUniqueID());
            assertEquals(3.25F,((SnapshotEntity)restored).health,0F);
            assertEquals(7.125F,((SnapshotEntity)restored.ridingEntity).health,0F);
            assertEquals(original,saved);
            saved.setFloat("HealF",0F); assertNull(KOMESeasonResetDeployment.restoreTree(saved,null));
            saved.setFloat("HealF",Float.NaN); assertNull(KOMESeasonResetDeployment.restoreTree(saved,null));
        } finally {
            if(previous==null)EntityList.stringToClassMapping.remove("kom28-fixture");else EntityList.stringToClassMapping.put("kom28-fixture",previous);
            if(previousName==null)EntityList.classToStringMapping.remove(SnapshotEntity.class);else EntityList.classToStringMapping.put(SnapshotEntity.class,previousName);
        }
    }

    @Test public void virtualResetUsesFresherSurvivorHealthAndRejectsUnresolvedMountedHealth() {
        Object previous=EntityList.stringToClassMapping.put("kom28-health",SnapshotEntity.class);
        Object previousName=EntityList.classToStringMapping.put(SnapshotEntity.class,"kom28-health");
        try {
            SnapshotEntity rider=new SnapshotEntity(null), mount=new SnapshotEntity(null);
            rider.health=20F; mount.health=30F; rider.ridingEntity=mount; mount.riddenByEntity=rider;
            KOMEHiredUnitRecord record=new KOMEHiredUnitRecord();
            record.movingEntityData=new NBTTagCompound(); assertTrue(rider.writeMountToNBT(record.movingEntityData));
            NBTTagCompound stored=(NBTTagCompound)record.movingEntityData.copy();
            record.survivingHealth=new NBTTagCompound(); record.survivingHealth.setFloat("Current",3.25F);
            NBTTagCompound mountHealth=new NBTTagCompound(); mountHealth.setFloat("Current",7.125F);
            record.survivingHealth.setTag("Riding",mountHealth);
            Entity restored=KOMESeasonResetDeployment.restoreVirtualUnit(record,null);
            assertEquals(3.25F,((SnapshotEntity)restored).health,0F);
            assertEquals(7.125F,((SnapshotEntity)restored.ridingEntity).health,0F);
            assertEquals(rider.getUniqueID(),restored.getUniqueID());
            assertEquals(mount.getUniqueID(),restored.ridingEntity.getUniqueID());
            assertEquals(stored,record.movingEntityData);
            record.survivingHealth.removeTag("Riding");
            assertThrows(IllegalArgumentException.class,()->KOMESeasonResetDeployment.restoreVirtualUnit(record,null));
            assertEquals(stored,record.movingEntityData);
        } finally {
            if(previous==null)EntityList.stringToClassMapping.remove("kom28-health");else EntityList.stringToClassMapping.put("kom28-health",previous);
            if(previousName==null)EntityList.classToStringMapping.remove(SnapshotEntity.class);else EntityList.classToStringMapping.put(SnapshotEntity.class,previousName);
        }
    }

    @Test public void capturedMountedSnapshotDoesNotAliasLiveForgeReceipts() {
        Object previousName=EntityList.classToStringMapping.put(SnapshotEntity.class,"kom28-snapshot");
        try {
            SnapshotEntity rider=new SnapshotEntity(null), mount=new SnapshotEntity(null);
            rider.health=3.25F;mount.health=7.125F;rider.ridingEntity=mount;mount.riddenByEntity=rider;
            rider.getEntityData().setString("Sentinel","rider");mount.getEntityData().setString("Sentinel","mount");
            NBTTagCompound snapshot=KOMEEntitySnapshots.snapshot(rider), before=(NBTTagCompound)snapshot.copy();
            rider.getEntityData().setString(KOMESeasonResetDeployment.RECEIPT,"1:C1");
            mount.getEntityData().setString(KOMESeasonResetDeployment.RECEIPT,"1:C1");
            assertEquals(before,snapshot);
            assertEquals(3.25F,snapshot.getFloat("HealF"),0F);
            assertEquals(7.125F,snapshot.getCompoundTag("Riding").getFloat("HealF"),0F);
        } finally {
            if(previousName==null)EntityList.classToStringMapping.remove(SnapshotEntity.class);
            else EntityList.classToStringMapping.put(SnapshotEntity.class,previousName);
        }
    }

    @Test public void obsoleteVirtualEntityIsRejectedWhileReceiptBearingRestorationIsAccepted() throws Exception {
        try(Session s=new Session()) {
            TestNpc npc=s.npc(3.25F); s.entry.units.add(npc.getUniqueID()); s.entry.virtualUnits.add(npc.getUniqueID());
            s.data.seasonReset.seasonId=1; s.data.seasonReset.companies.put("C1",s.entry);
            KOMEHiredUnitRecord record=new KOMEHiredUnitRecord();record.entity=npc.getUniqueID();record.seasonReturnToken="1:C1";record.seasonReturnVirtual=true;
            s.data.hiredUnits.put(record.entity,record);
            assertTrue(KOMESeasonResetDeployment.rejectStaleVirtual(s.data,npc));
            npc.getEntityData().setString(KOMESeasonResetDeployment.RECEIPT,"1:C1");
            assertFalse(KOMESeasonResetDeployment.rejectStaleVirtual(s.data,npc));
        }
    }

    @Test public void queuedChunkUnloadDoesNotRejectTheLegitimateReloadedRider() throws Exception {
        try(Session s=new Session()) {
            TestNpc old=s.npc(3.25F), incoming=s.npc(3.25F);
            incoming.setUniqueID(old.getUniqueID());
            KOMEHiredUnitRecord record=new KOMEHiredUnitRecord();record.entity=old.getUniqueID();record.seasonReturnToken="1:C1";
            s.data.hiredUnits.put(record.entity,record);s.world.loadedEntityList.add(old);
            assertTrue(KOMESeasonResetDeployment.rejectStaleVirtual(s.data,incoming));
            s.world.queueUnload(old);
            assertFalse("Queued unload is not a competing live entity",KOMESeasonResetDeployment.rejectStaleVirtual(s.data,incoming));
            assertNull(KOMESeasonResetDeployment.find(record.entity));
        }
    }

    @Test public void queuedUnloadStillRequiresVirtualReceiptAndRejectsAnotherActiveCopy() throws Exception {
        try(Session s=new Session()) {
            TestNpc old=s.npc(3.25F), incoming=s.npc(3.25F), duplicate=s.npc(3.25F);
            incoming.setUniqueID(old.getUniqueID());duplicate.setUniqueID(old.getUniqueID());
            KOMEHiredUnitRecord record=new KOMEHiredUnitRecord();record.entity=old.getUniqueID();record.seasonReturnToken="1:C1";record.seasonReturnVirtual=true;
            s.data.hiredUnits.put(record.entity,record);s.world.loadedEntityList.add(old);s.world.queueUnload(old);
            assertTrue(KOMESeasonResetDeployment.rejectStaleVirtual(s.data,incoming));
            incoming.getEntityData().setString(KOMESeasonResetDeployment.RECEIPT,"1:C1");
            assertFalse(KOMESeasonResetDeployment.rejectStaleVirtual(s.data,incoming));
            s.world.loadedEntityList.add(incoming);duplicate.getEntityData().setString(KOMESeasonResetDeployment.RECEIPT,"1:C1");
            assertSame(incoming,KOMESeasonResetDeployment.find(record.entity));
            assertTrue(KOMESeasonResetDeployment.rejectStaleVirtual(s.data,duplicate));
        }
    }

    @Test public void nativeChunkReadbackRejectsMissingReceiptsWrongHpAndStaleOriginCopies() throws Exception {
        try(Session s=new Session()) {
            java.nio.file.Path directory=java.nio.file.Files.createTempDirectory("kom28-chunk-receipt-");
            try {
                TestNpc npc=s.npc(3.25F); npc.posX=0; npc.posY=64; npc.posZ=0; npc.getEntityData().setString(KOMESeasonResetDeployment.RECEIPT,"1:C1");
                net.minecraft.world.gen.ChunkProviderServer provider=KOMEAccessFixture.allocate(TestProvider.class);
                ReceiptLoader loader=new ReceiptLoader(directory.toFile(),npc);
                provider.currentChunkLoader=loader; s.world.customProvider=provider;
                Chunk destination=KOMEAccessFixture.allocate(TestChunk.class);
                java.util.Set<Chunk> chunks=new LinkedHashSet<Chunk>(); chunks.add(destination);
                List<Entity> units=Collections.<Entity>singletonList(npc);
                assertEquals("",KOMESeasonResetDeployment.saveEntities(s.world,units,chunks));
                loader.hp=3F;
                assertTrue(KOMESeasonResetDeployment.saveEntities(s.world,units,chunks).contains("HP"));
                loader.hp=3.25F; loader.receipt="wrong";
                assertTrue(KOMESeasonResetDeployment.saveEntities(s.world,units,chunks).contains("receipt"));
                loader.receipt="1:C1";
                Chunk origin=KOMEAccessFixture.allocate(TestChunk.class);
                Field x=Chunk.class.getDeclaredField("xPosition");x.setAccessible(true);x.setInt(origin,1);chunks.add(origin);
                assertTrue(KOMESeasonResetDeployment.saveEntities(s.world,units,chunks).contains("Origin chunk"));
                loader.fail=true;
                assertTrue(KOMESeasonResetDeployment.saveEntities(s.world,units,chunks).contains("Cannot confirm"));
            } finally {
                net.minecraft.world.chunk.storage.RegionFileCache.clearRegionFileReferences();
                try(java.util.stream.Stream<java.nio.file.Path> paths=java.nio.file.Files.walk(directory)) {
                    for(java.nio.file.Path path:(Iterable<java.nio.file.Path>)paths.sorted(java.util.Comparator.reverseOrder())::iterator)java.nio.file.Files.delete(path);
                }
            }
        }
    }

    static class TestProvider extends net.minecraft.world.gen.ChunkProviderServer {
        private TestProvider(){super(null,null,null);}
        public boolean canSave(){return true;}
    }
    static class ReceiptLoader extends net.minecraft.world.chunk.storage.AnvilChunkLoader {
        final Entity entity; float hp=3.25F; String receipt="1:C1"; boolean fail;
        ReceiptLoader(java.io.File directory,Entity entity){super(directory);this.entity=entity;}
        public void saveChunk(World world,Chunk chunk) throws java.io.IOException {
            if(fail)throw new java.io.IOException("test write failure");
            NBTTagCompound unit=new NBTTagCompound(); UUID id=entity.getUniqueID();
            unit.setLong("UUIDMost",id.getMostSignificantBits());unit.setLong("UUIDLeast",id.getLeastSignificantBits());unit.setFloat("HealF",hp);
            NBTTagCompound receiptTag=new NBTTagCompound();receiptTag.setString(KOMESeasonResetDeployment.RECEIPT,receipt);unit.setTag("ForgeData",receiptTag);
            net.minecraft.nbt.NBTTagList pos=new net.minecraft.nbt.NBTTagList();
            pos.appendTag(new net.minecraft.nbt.NBTTagDouble(entity.posX));pos.appendTag(new net.minecraft.nbt.NBTTagDouble(entity.posY));pos.appendTag(new net.minecraft.nbt.NBTTagDouble(entity.posZ));unit.setTag("Pos",pos);
            net.minecraft.nbt.NBTTagList entities=new net.minecraft.nbt.NBTTagList();entities.appendTag(unit);
            NBTTagCompound level=new NBTTagCompound();level.setTag("Entities",entities);NBTTagCompound root=new NBTTagCompound();root.setTag("Level",level);
            try(java.io.DataOutputStream output=net.minecraft.world.chunk.storage.RegionFileCache.getChunkOutputStream(chunkSaveLocation,chunk.xPosition,chunk.zPosition)) {
                net.minecraft.nbt.CompressedStreamTools.write(root,output);
            }
        }
        public void saveExtraData(){ }
    }

    @Test public void staleMountRejectionSurvivesJournalRolloverAndUnitSaveLoad() throws Exception {
        try(Session s=new Session()) {
            SnapshotEntity mount=new SnapshotEntity(s.world);
            KOMEHiredUnitRecord unit=new KOMEHiredUnitRecord();unit.entity=UUID.randomUUID();unit.owner=UUID.randomUUID();
            unit.seasonReturnToken="1:C1";unit.seasonReturnVirtual=true;unit.movingEntityData=new NBTTagCompound();
            NBTTagCompound savedMount=new NBTTagCompound();savedMount.setLong("UUIDMost",mount.getUniqueID().getMostSignificantBits());savedMount.setLong("UUIDLeast",mount.getUniqueID().getLeastSignificantBits());
            unit.movingEntityData.setTag("Riding",savedMount);
            KOMEHiredUnitRecord loaded=new KOMEHiredUnitRecord();loaded.readFromNBT(unit.writeToNBT());s.data.hiredUnits.put(loaded.entity,loaded);
            s.data.seasonReset=new KOMESeasonResetState();
            assertTrue(KOMESeasonResetDeployment.rejectStaleVirtual(s.data,mount));
            mount.getEntityData().setString(KOMESeasonResetDeployment.RECEIPT,"1:C1");
            assertFalse(KOMESeasonResetDeployment.rejectStaleVirtual(s.data,mount));
        }
    }

    static class Session implements AutoCloseable {
        final KOMEWorldData data=new KOMEWorldData("physical-reset");
        final KOMESeasonResetState.Return entry=new KOMESeasonResetState.Return();
        final TestWorld world;
        final KOMESeasonResetDeployment adapter;
        final Hashtable<Integer,WorldServer> registry=new Hashtable<Integer,WorldServer>();
        final Field worlds; final Object previous;
        int nextEntityId=1;
        Session() throws Exception {
            data.initializeIntegratedWorld(); entry.capital=KOMEFactionCapitalService.getCapital(data,"gondor");
            entry.companyId="C1"; entry.nativeFaction="gondor"; entry.returnRequired=true;
            entry.destination=entry.capital.getCapitalTileId(); entry.origin=KOMEFactionCapitalService.getCapitalTileId(data,"mordor");
            world=KOMEAccessFixture.allocate(TestWorld.class);
            Field provider=World.class.getDeclaredField("provider"); provider.setAccessible(true);
            WorldProviderSurface surface=new WorldProviderSurface(); surface.dimensionId=entry.capital.getDeploymentDimensionId(); provider.set(world,surface);
            world.loadedEntityList=new ArrayList<Entity>(); world.chunk=KOMEAccessFixture.allocate(TestChunk.class);
            worlds=DimensionManager.class.getDeclaredField("worlds"); worlds.setAccessible(true); previous=worlds.get(null);
            registry.put(surface.dimensionId,world); worlds.set(null,registry);
            adapter=new KOMESeasonResetDeployment(world,(w,entities,touched)->"",d->KOMESeasonResetServiceTest.save(d));
        }
        TestNpc npc(float health) throws Exception {
            TestNpc npc=KOMEAccessFixture.allocate(TestNpc.class); npc.worldObj=world; npc.dimension=world.provider.dimensionId;
            npc.width=.6F; npc.height=1.8F; npc.setUniqueID(UUID.randomUUID());npc.setEntityId(nextEntityId++);
            Field watcher=Entity.class.getDeclaredField("dataWatcher"); watcher.setAccessible(true);
            DataWatcher dataWatcher=new DataWatcher(npc); dataWatcher.addObject(6,Float.valueOf(health)); watcher.set(npc,dataWatcher);
            Field box=Entity.class.getDeclaredField("boundingBox"); box.setAccessible(true); box.set(npc,AxisAlignedBB.getBoundingBox(0,64,0,.6,65.8,.6));
            KOMEFactionCapitalRecord origin=KOMEFactionCapitalService.getCapital(data,"mordor");
            npc.posX=origin.getDeploymentX(); npc.posY=64; npc.posZ=origin.getDeploymentZ(); return npc;
        }
        public void close() throws Exception { worlds.set(null,previous); }
    }
    public static class SnapshotEntity extends Entity {
        float health;
        public SnapshotEntity(World world) { super(world); }
        protected void entityInit() { }
        protected void readEntityFromNBT(NBTTagCompound tag) { health=tag.getFloat("HealF"); }
        protected void writeEntityToNBT(NBTTagCompound tag) { tag.setFloat("HealF",health); tag.setFloat("Health",health); }
    }
    static class TestNpc extends LOTREntityRohanMan { private TestNpc() { super(null); } }
    static class TestWorld extends WorldServer {
        static final Block SOLID=new Solid(); Chunk chunk; IChunkProvider customProvider; int spawns,moves; boolean blocked;
        private TestWorld(){super((MinecraftServer)null,(ISaveHandler)null,"test",0,(WorldSettings)null,(Profiler)null);}
        protected IChunkProvider createChunkProvider(){return null;}
        protected int func_152379_p(){return 0;}
        public Entity getEntityByID(int id){return null;}
        public IChunkProvider getChunkProvider(){if(customProvider!=null)return customProvider;return (IChunkProvider)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{IChunkProvider.class},(proxy,method,args)-> {
            if(method.getName().equals("chunkExists"))return true;
            if(method.getName().equals("provideChunk") || method.getName().equals("loadChunk"))return chunk;
            throw new AssertionError(method.getName());
        });}
        public Chunk getChunkFromChunkCoords(int x,int z){return chunk;}
        public Chunk getChunkFromBlockCoords(int x,int z){return chunk;}
        public boolean blockExists(int x,int y,int z){return true;}
        public int getActualHeight(){return 256;}
        public int getTopSolidOrLiquidBlock(int x,int z){return 64;}
        public Block getBlock(int x,int y,int z){return SOLID;}
        public List func_147461_a(AxisAlignedBB box){return blocked?Collections.singletonList(box):Collections.emptyList();}
        public boolean isAnyLiquid(AxisAlignedBB box){return false;}
        public List getEntitiesWithinAABBExcludingEntity(Entity excluded,AxisAlignedBB box){
            List<Entity> result=new ArrayList<Entity>();
            for(Object value:loadedEntityList){
                Entity entity=(Entity)value;
                if(entity!=excluded&&!entity.isDead&&entity.boundingBox!=null&&entity.boundingBox.intersectsWith(box))
                    result.add(entity);
            }
            return result;
        }
        public List getCollidingBoundingBoxes(Entity e,AxisAlignedBB box){throw new AssertionError("Terrain clearance must not use a null-entity collision query");}
        public void updateEntityWithOptionalForce(Entity e,boolean force){moves++;}
        public boolean spawnEntityInWorld(Entity e){spawns++;loadedEntityList.add(e);return true;}
        void queueUnload(Entity e){if(unloadedEntityList==null)unloadedEntityList=new ArrayList<Entity>();unloadedEntityList.add(e);}
    }
    static class TestChunk extends Chunk {
        private TestChunk(){super((World)null,0,0);}
        public int getTopFilledSegment(){return 48;}
    }
    static class Solid extends Block { Solid(){super(Material.rock);} }
}
