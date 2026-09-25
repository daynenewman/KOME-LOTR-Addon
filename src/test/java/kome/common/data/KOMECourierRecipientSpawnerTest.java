package kome.common.data;

import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.*;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.spawning.*;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECourierRecipientSpawnerTest {
    private static final class TestContainer extends Container {public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player){return true;}}
    public static final class NativeList extends LOTRBiomeSpawnList {
        private NativeList(){super("test");}
        @Override public List<LOTRSpawnEntry> getAllSpawnEntries(World world){
            return Arrays.asList(new LOTRSpawnEntry(LOTREntityGondorMan.class,10,1,1),
                new LOTRSpawnEntry(LOTREntityGondorianCaptain.class,2,1,1),
                new LOTRSpawnEntry(LOTREntityGondorBannerBearer.class,1,1,1));
        }
    }
    @Test public void nativeBiomeMetadataYieldsOrdinaryCaptainAndTraderButExcludesSpecial() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();fixture.world.rand=new Random(4);fixture.world.isRemote=true;
        KOMECourierGeographyTest.TestBiome biome=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);
        biome.npcSpawnList=KOMEAccessFixture.allocate(NativeList.class);
        assertEquals(LOTRFaction.GONDOR,new LOTREntityGondorMan(fixture.world).getFaction());
        assertEquals(LOTRFaction.GONDOR,new LOTREntityGondorBaker(fixture.world).getFaction());
        List<Class<? extends LOTREntityNPC>> types=KOMECourierRecipientSpawner.eligibleClasses(fixture.world,biome,LOTRFaction.GONDOR);
        assertTrue(types.toString(),types.contains(LOTREntityGondorMan.class));
        assertTrue(types.contains(LOTREntityGondorianCaptain.class));
        assertTrue(types.toString(),types.contains(LOTREntityGondorBaker.class));
        assertFalse(types.contains(LOTREntityGondorBannerBearer.class));
        assertTrue(Collections.frequency(types,LOTREntityGondorMan.class)>Collections.frequency(types,LOTREntityGondorianCaptain.class));
    }
    @Test public void replacementIdentityIsStableAcrossReloadAndChangesAfterRealDeath(){
        KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","rohan",0,1000,64,1000);
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.create(master,lotr.common.world.map.LOTRWaypoint.EDORAS);
        UUID first=KOMECourierRecipientSpawner.recipientId(assignment);
        assertEquals(first,KOMECourierRecipientSpawner.recipientId(KOMESerfCourierAssignment.readFromNBT(assignment.writeToNBT())));
        assignment.recipientDeaths++;
        assertNotEquals(first,KOMECourierRecipientSpawner.recipientId(assignment));
    }
    @Test public void safeSearchNeverProbesUnloadedNeighborChunks() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();fixture.world.isRemote=true;fixture.world.rand=new Random(9);fixture.world.flatTerrain=true;
        KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","gondor",0,1000,64,1000);
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.create(master,lotr.common.world.map.LOTRWaypoint.MINAS_TIRITH);
        final int chunkX=((int)assignment.destinationX)>>4,chunkZ=((int)assignment.destinationZ)>>4;
        fixture.world.testChunkProvider=(IChunkProvider)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IChunkProvider.class},
            (proxy,method,args)->"chunkExists".equals(method.getName())?((Integer)args[0]).intValue()==chunkX&&((Integer)args[1]).intValue()==chunkZ:method.getReturnType()==boolean.class?false:null);
        fixture.player.posX=assignment.destinationX;fixture.player.posZ=assignment.destinationZ;
        assertNull(KOMECourierRecipientSpawner.safeLoadedPosition(fixture.world,fixture.player,assignment,new LOTREntityGondorMan(fixture.world)));
        assertEquals("unloaded columns must never reach terrain access",0,fixture.world.terrainProbes);
    }
    @Test public void oldLowercaseDorwinionAssignmentPassesFactionGuardAndSpawns() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();fixture.world.isRemote=true;fixture.world.rand=new Random(11);
        fixture.world.flatTerrain=true;fixture.world.spawnSucceeds=true;
        fixture.world.testChunkProvider=(IChunkProvider)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{IChunkProvider.class},
            (proxy,method,args)->"chunkExists".equals(method.getName())?true:method.getReturnType()==boolean.class?false:null);
        KOMECourierGeographyTest.TestBiome biome=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);
        biome.npcSpawnList=KOMEAccessFixture.allocate(NativeList.class);
        KOMECourierGeographyTest.TestManager manager=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestManager.class);
        manager.biome=biome;fixture.world.provider.worldChunkMgr=manager;
        KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","dorwinion",0,1000,64,1000);
        KOMESerfCourierAssignment current=KOMESerfCourierAssignment.create(master,lotr.common.world.map.LOTRWaypoint.DORWINION_COURT);
        net.minecraft.nbt.NBTTagCompound old=current.writeToNBT();old.setInteger("Version",5);old.setString("DestinationFactionKey","dorwinion");
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.readFromNBT(old);
        fixture.player.posX=assignment.destinationX;fixture.player.posZ=assignment.destinationZ;
        LOTREntityNPC spawned=KOMECourierRecipientSpawner.spawn(fixture.player,assignment);
        assertNotNull("lowercase saved Dorwinion must reach native recipient creation",spawned);
        assertSame(LOTRFaction.DORWINION,spawned.getFaction());
        assertEquals(assignment.token,spawned.getEntityData().getString(KOMECourierRecipientSpawner.TOKEN));
    }
    @Test public void playableFactionsHaveAtLeastOneNativeIndependentRecipientClass() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();fixture.world.rand=new Random(4);fixture.world.isRemote=true;
        KOMECourierGeographyTest.TestBiome biome=kome.common.KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);
        biome.npcSpawnList=kome.common.KOMEAccessFixture.allocate(NativeList.class);
        List<String> missing=new ArrayList<String>();
        for(LOTRFaction faction:LOTRFaction.getPlayableAlignmentFactions())
            if(KOMECourierRecipientSpawner.eligibleClasses(fixture.world,biome,faction).isEmpty())missing.add(faction.codeName());
        assertTrue(missing.toString(),missing.isEmpty());
    }
    @Test public void naturallyLoadedFlatSiteCreatesOneMarkedNativeRecipientAndFailedSpawnDoesNotBind() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();fixture.world.isRemote=true;fixture.world.rand=new Random(7);
        fixture.world.flatTerrain=true;fixture.world.testChunkProvider=(IChunkProvider)java.lang.reflect.Proxy.newProxyInstance(
            getClass().getClassLoader(),new Class[]{IChunkProvider.class},(proxy,method,args)->
                "chunkExists".equals(method.getName())?true:method.getReturnType()==boolean.class?false:null);
        KOMECourierGeographyTest.TestBiome biome=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestBiome.class);
        biome.npcSpawnList=KOMEAccessFixture.allocate(NativeList.class);
        KOMECourierGeographyTest.TestManager manager=KOMEAccessFixture.allocate(KOMECourierGeographyTest.TestManager.class);
        manager.biome=biome;fixture.world.provider.worldChunkMgr=manager;
        KOMEProgressionNpcRef master=new KOMEProgressionNpcRef(UUID.randomUUID().toString(),"Master","gondor",0,1000,64,1000);
        KOMESerfCourierAssignment assignment=KOMESerfCourierAssignment.create(master,lotr.common.world.map.LOTRWaypoint.MINAS_TIRITH);
        fixture.player.posX=assignment.destinationX;fixture.player.posZ=assignment.destinationZ;
        assertTrue(fixture.world.getActualHeight()>67);assertTrue(fixture.world.getChunkProvider().chunkExists(0,0));
        assertTrue(fixture.world.getBlock(10,64,10).getMaterial().isSolid());assertTrue(fixture.world.isAirBlock(10,65,10));
        assertNotNull(KOMECourierRecipientSpawner.safeLoadedPosition(fixture.world,fixture.player,assignment,new LOTREntityGondorMan(fixture.world)));
        assertNull(KOMECourierRecipientSpawner.spawn(fixture.player,assignment));
        assertFalse(assignment.recipient.isSet());assertTrue(fixture.world.loadedEntityList.isEmpty());
        fixture.world.spawnSucceeds=true;
        LOTREntityNPC npc=KOMECourierRecipientSpawner.spawn(fixture.player,assignment);
        assertNotNull(npc);assertEquals(assignment.token,npc.getEntityData().getString(KOMECourierRecipientSpawner.TOKEN));
        assertEquals(KOMECourierRecipientSpawner.recipientId(assignment),npc.getUniqueID());
        assertSame(npc,KOMECourierRecipientSpawner.findOwned(fixture.world,assignment.token));
        assertEquals(1,fixture.world.loadedEntityList.size());
        LOTREntityNPC duplicate=new LOTREntityGondorMan(fixture.world);duplicate.setUniqueID(npc.getUniqueID());
        duplicate.getEntityData().setString(KOMECourierRecipientSpawner.TOKEN,assignment.token);
        assertTrue(KOMECourierRecipientSpawner.duplicateOwned(duplicate));
        LOTREntityNPC unrelated=new LOTREntityGondorMan(fixture.world);unrelated.setUniqueID(npc.getUniqueID());
        assertFalse(KOMECourierRecipientSpawner.duplicateOwned(unrelated));
        fixture.world.isRemote=false;fixture.player.dimension=assignment.dimension;
        fixture.player.inventory=new InventoryPlayer(fixture.player);fixture.player.inventoryContainer=new TestContainer();
        fixture.pledge(LOTRFaction.GONDOR);
        KOMEPlayerProgression progression=fixture.data.getProgression(fixture.player.id);progression.setCanonicalRank(KOMEProgressionRank.SERF);
        KOMESerfKnightProgression state=progression.getSerfKnightProgression();state.setSerfdomMaster(master);
        assertTrue(KOMESerfKnightService.assignDuty(state,KOMESerfKnightDutyType.COURIER,assignment.writeToNBT(),10L).success);
        fixture.player.inventory.mainInventory[0]=KOMECourierService.message(assignment,fixture.player,master);
        fixture.player.inventory.mainInventory[1]=KOMECourierService.message(assignment,fixture.player,master);
        assertEquals("",KOMEVisualLocationService.markersFor(progression).get(1).entityUuid);
        fixture.world.testWorldTime=100L;fixture.player.ticksExisted=7; // World and player clocks differ after login.
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous=kome.common.network.KOMEPacketHandler.network;
        try{kome.common.network.KOMEPacketHandler.network=fixture.network;
            KOMECourierService.tickPlayer(fixture.player);
            assertEquals("arrival must bind despite player tick phase",1,fixture.world.loadedEntityList.size());
            KOMESerfCourierAssignment bound=KOMESerfCourierAssignment.readFromNBT(state.getDuty(KOMESerfKnightDutyType.COURIER).getAssignmentData());
            assertEquals(npc.getUniqueID().toString(),bound.recipient.entityUuid);
            int books=0;for(net.minecraft.item.ItemStack stack:fixture.player.inventory.mainInventory)if(KOMECourierService.matching(stack,bound,fixture.player.id,master))books++;
            assertEquals(1,books);
            assertTrue(KOMEProgressionNpcRoles.protects(fixture.data,npc.getUniqueID()));
            KOMEVisualMarker courier=KOMEVisualLocationService.markersFor(progression).get(1);
            assertEquals(KOMEVisualMarker.Role.COURIER,courier.role);assertEquals(npc.getUniqueID().toString(),courier.entityUuid);
            assertEquals(npc.posX,courier.x,0D);
            assertSame("reload must rediscover the same owned entity",npc,KOMECourierRecipientSpawner.findOwned(fixture.world,bound.token));
            assertEquals(1,fixture.world.loadedEntityList.size());
            fixture.player.posX=npc.posX;fixture.player.posY=npc.posY;fixture.player.posZ=npc.posZ;
            assertTrue(KOMECourierService.deliverToRecipient(fixture.player,fixture.data,npc));
            assertFalse(KOMEProgressionNpcRoles.protects(fixture.data,npc.getUniqueID()));
            assertTrue(npc.isEntityAlive());assertFalse(npc.getEntityData().hasKey(KOMECourierRecipientSpawner.TOKEN));
            assertEquals(1,KOMEVisualLocationService.markersFor(progression).size());
            assertTrue(npc.canDespawn());
            assertTrue(KOMECourierService.reportToMaster(fixture.player,fixture.data,progression));
            assertTrue(state.getDuty(KOMESerfKnightDutyType.COURIER).isCompleted());
        }finally{kome.common.network.KOMEPacketHandler.network=previous;}
    }
}
