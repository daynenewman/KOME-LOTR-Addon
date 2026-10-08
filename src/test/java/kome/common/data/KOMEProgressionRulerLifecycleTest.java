package kome.common.data;
import java.util.*;
import java.lang.reflect.Proxy;
import kome.common.KOMEAccessFixture;
import lotr.common.LOTRDimension;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.DataWatcher;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.chunk.IChunkProvider;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEProgressionRulerLifecycleTest {
    @org.junit.Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    @Test public void kingDeathStartsExactlyOneTenMinuteTimerAndNeverRespawnsEarly(){
        KOMEWorldData data=new KOMEWorldData("rulers");UUID id=UUID.randomUUID();
        KOMEProgressionNpcRankService.assignElevatedRank(data,id,"rohan",KOMEProgressionNpcRank.KING,"Aldor",true);
        assertTrue(KOMEProgressionRulerService.noteDeath(data,id));KOMEProgressionNpcRankRecord r=data.progressionNpcRanks.get(id);
        KOMEProgressionRulerService.advance(r,599999);assertEquals(1,r.respawnRemainingMillis);assertFalse(KOMEProgressionRulerService.canRespawn(r));
        assertFalse(KOMEProgressionRulerService.noteDeath(data,id));assertEquals(1,r.respawnRemainingMillis);
        KOMEProgressionRulerService.advance(r,1);assertTrue(KOMEProgressionRulerService.canRespawn(r));assertEquals(KOMEProgressionNpcRank.KING,r.rank);
    }
    @Test public void persistenceKeepsIncarnationLastPositionAndRemainingRuntime(){
        KOMEProgressionNpcRankRecord r=new KOMEProgressionNpcRankRecord(UUID.randomUUID(),"rohan",KOMEProgressionNpcRank.KING,"Aldor");
        r.rulerInitialized=true;r.incarnation=5;r.respawnRemainingMillis=421000;r.rulerLocation=new KOMEProgressionNpcRef(r.npcUuid.toString(),"King Aldor","rohan",100,1,65,2);
        KOMEProgressionNpcRankRecord loaded=KOMEProgressionNpcRankRecord.readFromNBT(r.writeToNBT());
        assertEquals(r.npcUuid,loaded.npcUuid);assertEquals(421000,loaded.respawnRemainingMillis);assertEquals(5,loaded.incarnation);
        assertEquals(r.rulerLocation.entityUuid,loaded.rulerLocation.entityUuid);assertEquals(0,loaded.lastRuntimeNanos);
    }
    @Test public void duplicateKingSaveRowsReconcileToOneDeterministicSlot(){
        KOMEWorldData data=new KOMEWorldData("rulers");data.initializeIntegratedWorld();
        UUID low=UUID.fromString("00000000-0000-0000-0000-000000000001"),high=UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        data.progressionNpcRanks.put(high,new KOMEProgressionNpcRankRecord(high,"rohan",KOMEProgressionNpcRank.KING,"Other"));
        data.progressionNpcRanks.put(low,new KOMEProgressionNpcRankRecord(low,"rohan",KOMEProgressionNpcRank.KING,"Aldor"));
        NBTTagCompound saved=new NBTTagCompound();data.writeToNBT(saved);KOMEWorldData loaded=new KOMEWorldData("rulers");loaded.readFromNBT(saved);
        assertEquals(1,loaded.progressionNpcRanks.size());assertEquals(low,KOMEProgressionRulerService.slot(loaded,"rohan").npcUuid);
    }
    @Test public void everySupportedCapitalProvidesExactlyOneCrownAndPlayerKingDoesNotHideWorldIdentity(){
        KOMEWorldData data=new KOMEWorldData("rulers");data.initializeIntegratedWorld();
        for(String faction:KOMEProgressionNativeAuthority.definitions().keySet()){
            assertNotNull(KOMEFactionCapitalService.getCapital(data,faction));
            KOMEProgressionNpcRankService.assignElevatedRank(data,UUID.randomUUID(),faction,KOMEProgressionNpcRank.KING,"Ruler",true);
        }
        assertEquals(23,KOMEProgressionRulerService.markers(data).size());KOMERulerService.assignRuler(data,"rohan",UUID.randomUUID(),"Player King");
        assertEquals(23,KOMEProgressionRulerService.markers(data).size());
        for(KOMEVisualMarker m:KOMEProgressionRulerService.markers(data)){assertEquals(KOMEVisualMarker.Role.RULER,m.role);assertTrue(m.isRelationship());assertEquals(LOTRDimension.MIDDLE_EARTH.dimensionID,m.dimension);}
    }
    @Test public void liveCapitalSpawnIsSafeSingletonAndRespawnsWithSameIdentity()throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.data.initializeIntegratedWorld();set(net.minecraft.world.World.class,f.world,"worldInfo",new net.minecraft.world.storage.WorldInfo(new NBTTagCompound()));set(net.minecraft.world.WorldProvider.class,f.world.provider,"worldObj",f.world);f.world.flatTerrain=true;f.world.spawnSucceeds=true;f.world.rand=new Random(17);f.world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID;
        f.world.testChunkProvider=(IChunkProvider)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IChunkProvider.class},(p,m,a)->m.getName().equals("chunkExists")?true:m.getName().equals("provideChunk")||m.getName().equals("loadChunk")?chunk(f.world,(Integer)a[0],(Integer)a[1]):m.getReturnType()==boolean.class?false:m.getReturnType()==int.class?0:null);
        set(net.minecraft.world.World.class,f.world,"chunkProvider",f.world.testChunkProvider);
        KOMEProgressionRulerService.NpcFactory previous=KOMEProgressionRulerService.npcFactory;
        KOMEProgressionRulerService.npcFactory=(world,d)->{
            if(!d.faction.equals("rohan"))return null;
            try{TestRuler npc=KOMEAccessFixture.allocate(TestRuler.class);npc.worldObj=world;npc.setUniqueID(UUID.randomUUID());npc.width=.6F;npc.height=1.8F;set(net.minecraft.entity.Entity.class,npc,"boundingBox",AxisAlignedBB.getBoundingBox(0,0,0,1,2,1));
                npc.units=KOMEAccessFixture.allocate(LOTRUnitTradeEntries.class);LOTRUnitTradeEntry unit=KOMEAccessFixture.allocate(LOTRUnitTradeEntry.class);unit.task=LOTRHiredNPCInfo.Task.WARRIOR;set(LOTRUnitTradeEntries.class,npc.units,"tradeEntries",new LOTRUnitTradeEntry[]{unit});
                DataWatcher watcher=new DataWatcher(npc);watcher.addObject(10,"");watcher.addObject(11,(byte)0);set(net.minecraft.entity.Entity.class,npc,"dataWatcher",watcher);npc.familyInfo=new InertFamily(npc);return npc;
            }catch(Exception e){throw new RuntimeException(e);}
        };
        try{
            KOMEProgressionRulerService.tick(f.data,f.world);KOMEProgressionNpcRankRecord slot=KOMEProgressionRulerService.slot(f.data,"rohan");assertTrue(slot.rulerSpawned);
            TestRuler king=(TestRuler)f.world.loadedEntityList.get(0);UUID id=king.getUniqueID();assertEquals(slot.npcUuid,id);
            KOMEFactionCapitalRecord capital=KOMEFactionCapitalService.getCapital(f.data,"rohan");
            assertTrue(king.getDistanceSq(capital.getDeploymentX(),65,capital.getDeploymentZ())<24*24);assertEquals(65,king.posY,0);
            assertEquals(24,king.homeRadius);assertEquals("",king.getCustomNameTag());assertTrue(king.familyInfo.getName().contains(", King of "));
            KOMEProgressionRulerService.tick(f.data,f.world);assertEquals(1,f.world.loadedEntityList.size());
            assertTrue(KOMEProgressionRulerService.noteDeath(f.data,id));king.setDead();
            KOMEProgressionRulerService.advance(slot,599999);slot.lastRuntimeNanos=0;KOMEProgressionRulerService.tick(f.data,f.world);assertEquals(1,f.world.loadedEntityList.size());
            KOMEProgressionRulerService.advance(slot,1);KOMEProgressionRulerService.tick(f.data,f.world);assertEquals(2,f.world.loadedEntityList.size());
            TestRuler restored=(TestRuler)f.world.loadedEntityList.get(1);assertEquals(id,restored.getUniqueID());assertEquals(KOMEProgressionNpcRank.KING,slot.rank);assertTrue(slot.rulerSpawned);assertEquals(2,slot.incarnation);
            assertTrue(KOMEProgressionRulerService.reconcileJoin(f.data,king)); // obsolete incarnation cannot return
            KOMEProgressionEncounterMarker.stampRuler(restored,slot.incarnation);restored.setUniqueID(UUID.randomUUID());
            assertTrue("Copied ruler NBT cannot gain a second identity",KOMEProgressionRulerService.reconcileJoin(f.data,restored));
            restored.setUniqueID(id);
            assertFalse(KOMEProgressionRulerService.noteDeath(f.data,UUID.randomUUID()));
        }finally{KOMEProgressionRulerService.npcFactory=previous;}
    }
    @Test public void unsafeCapitalDoesNotSpawnOrConsumeCooldownCompletion()throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.data.initializeIntegratedWorld();f.world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID+1;
        KOMEProgressionRulerService.tick(f.data,f.world);assertNull(KOMEProgressionRulerService.slot(f.data,"rohan"));
    }
    private static net.minecraft.world.chunk.Chunk chunk(net.minecraft.world.World world,int x,int z){net.minecraft.world.chunk.Chunk c=new net.minecraft.world.chunk.Chunk(world,x,z);c.getBlockStorageArray()[4]=new net.minecraft.world.chunk.storage.ExtendedBlockStorage(64,true);java.util.Arrays.fill(c.heightMap,65);return c;}
    @Test public void timerAdvancesWithoutPlayersOrLoadedCapitalAndWaitsSafelyForPlacement()throws Exception{
        KOMEAccessFixture f=new KOMEAccessFixture();f.data.initializeIntegratedWorld();
        set(net.minecraft.world.World.class,f.world,"worldInfo",new net.minecraft.world.storage.WorldInfo(new NBTTagCompound()));set(net.minecraft.world.WorldProvider.class,f.world.provider,"worldObj",f.world);
        f.world.provider.dimensionId=LOTRDimension.MIDDLE_EARTH.dimensionID;f.world.playerEntities.clear();
        f.world.testChunkProvider=(IChunkProvider)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IChunkProvider.class},(p,m,a)->m.getReturnType()==boolean.class?false:m.getReturnType()==int.class?0:null);
        UUID id=UUID.randomUUID();KOMEProgressionNpcRankService.assignElevatedRank(f.data,id,"rohan",KOMEProgressionNpcRank.KING,"Aldor",true);
        KOMEProgressionRulerService.noteDeath(f.data,id);KOMEProgressionNpcRankRecord slot=f.data.progressionNpcRanks.get(id);slot.lastRuntimeNanos=0;
        KOMEProgressionRulerService.tick(f.data,f.world,1_000_000_000L);KOMEProgressionRulerService.tick(f.data,f.world,61_000_000_000L);
        assertEquals(540000L,slot.respawnRemainingMillis);assertTrue(f.world.loadedEntityList.isEmpty());
        KOMEProgressionRulerService.tick(f.data,f.world,601_000_000_000L);
        assertEquals(0,slot.respawnRemainingMillis);assertFalse(slot.rulerSpawned);assertEquals(id,KOMEProgressionRulerService.slot(f.data,"rohan").npcUuid);
    }
    private static void set(Class<?> c,Object target,String name,Object value)throws Exception{java.lang.reflect.Field f=c.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
    public static class TestRuler extends LOTREntityRohirrimMarshal {
        LOTRUnitTradeEntries units;
        @Override public LOTRUnitTradeEntries getUnits(){return units;}
        int homeRadius;private TestRuler(){super(null);}
        @Override public boolean isEntityAlive(){return !isDead;}
        @Override public boolean isChild(){return false;}
        @Override public String getNPCName(){return "Aldor";}
        @Override public void onArtificalSpawn(){}
        @Override public void setupNPCName(){}
        @Override public void setHomeArea(int x,int y,int z,int radius){homeRadius=radius;}
        @Override public void writeToNBT(NBTTagCompound tag){tag.setLong("UUIDMost",getUniqueID().getMostSignificantBits());tag.setLong("UUIDLeast",getUniqueID().getLeastSignificantBits());}
        @Override public void readFromNBT(NBTTagCompound tag){setUniqueID(new UUID(tag.getLong("UUIDMost"),tag.getLong("UUIDLeast")));}
    }
    public static class InertFamily extends LOTRFamilyInfo {
        String name="Aldor";InertFamily(LOTREntityNPC npc){super(npc);}
        @Override public void setName(String value){name=value;}
        @Override public String getName(){return name;}
    }
}
