package kome.common.command;

import java.util.UUID;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import kome.common.command.KOMECompanyMovementService.Evaluation;
import lotr.common.entity.npc.LOTREntityGondorSoldier;
import lotr.common.fac.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.command.KOMECompanyMovementServiceTest.Fixture;

/** Real map submission, NPC recreation, verified physical publication and canonical conflict commit. */
public class KOMECompanyMapMovementArrivalTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    @Before public void before() throws Exception {config=new KOMEPopulationTestConfig();}
    @After public void after() throws Exception {
        LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.MORDOR,LOTRFactionRelations.Relation.NEUTRAL);
        config.close();
    }
    @Test public void immediateArrivalRecreatesPhysicalNpcAndReportsArrived() throws Exception {arrive(false,false);}
    @Test public void hostileCreateCommitsOnlyAfterVerifiedPhysicalArrival() throws Exception {arrive(true,false);}
    @Test public void hostileJoinCommitsToExistingConflictAfterVerifiedPhysicalArrival() throws Exception {arrive(true,true);}

    private void arrive(boolean hostile,boolean join) throws Exception {
        String type="kome.test.MapArrivalSoldier";
        Object previousClass=EntityList.stringToClassMapping.put(type,ArrivalSoldier.class);
        Object previousName=EntityList.classToStringMapping.put(ArrivalSoldier.class,type);
        try(Fixture f=new Fixture(false,1)) {
            KOMEAccessFixture physical=new KOMEAccessFixture();
            physical.world.flatTerrain=true;physical.world.spawnSucceeds=true;
            physical.world.mapStorage.setData("KOME_ServerRules",f.data);f.player.worldObj=physical.world;
            physical.world.testChunkProvider=(IChunkProvider)java.lang.reflect.Proxy.newProxyInstance(
                IChunkProvider.class.getClassLoader(),new Class[]{IChunkProvider.class},(proxy,method,args)->{
                    if(method.getReturnType()==boolean.class)return true;
                    if(method.getReturnType()==Chunk.class)return new Chunk(physical.world,(Integer)args[0],(Integer)args[1]);
                    if(method.getReturnType()==int.class)return 1;
                    return null;
                });
            java.lang.reflect.Field provider=World.class.getDeclaredField("chunkProvider");provider.setAccessible(true);
            provider.set(physical.world,physical.world.testChunkProvider);
            // Reuse the native codec-fixture convention: suppress watcher broadcasts during NPC decoding only.
            physical.world.isRemote=true;
            ArrivalSoldier source=new ArrivalSoldier(physical.world);source.setHealth(5.375F);source.setPosition(10,65,20);
            f.unit.stationedEntityData=KOMEEntitySnapshots.snapshot(source);assertNotNull(f.unit.stationedEntityData);
            physical.world.isRemote=false;
            KOMEConflictRecord existing=null;
            if(join)existing=f.hostileConflict();else if(hostile)f.hostileDestination();
            if(hostile&&!join) {
                // A recently active recognized ruler canonically defers Emergency Defense mobilization.
                UUID ruler=new UUID(9,7);f.data.lastKnownPlayerFactions.put(ruler,"mordor");
                assertTrue(KOMERulerService.assignRuler(f.data,"mordor",ruler,"Fixture ruler"));
            }
            Evaluation preview=f.evaluate();assertTrue(preview.denial==null?"":preview.denial.reason,preview.allowed());
            UUID oldEntity=f.unit.entity;
            KOMECompanyMovementResult result=KOMECompanyMapMovementSubmission.submit(f.service,f.data,f.player,UUID.randomUUID(),1,1,
                "C1",f.destination,preview.previewFingerprint,preview.confirmation.serviceFingerprint);
            KOMEArmyMovementOrder order=f.data.armyMovements.get(result.orderId);assertNotNull(result.detail,order);
            assertEquals(order.pendingSpawnReason,KOMECompanyMovementResult.Outcome.ARRIVED,result.outcome);
            assertEquals(0,f.company.movementAllowance);assertEquals(f.destination,f.company.currentTile);
            assertEquals(f.destination,f.unit.currentTile);assertEquals(1,order.completedSteps);
            assertNull(f.unit.movingEntityData);assertNotNull(f.unit.stationedEntityData);
            assertNotEquals(oldEntity,f.unit.entity);assertSame(f.unit,f.data.hiredUnits.get(f.unit.entity));
            assertEquals(1,physical.world.loadedEntityList.size());
            Entity arrived=(Entity)physical.world.loadedEntityList.get(0);
            assertTrue(arrived instanceof ArrivalSoldier);assertEquals(f.unit.entity,arrived.getUniqueID());assertFalse(arrived.isDead);
            assertEquals(5.375F,((ArrivalSoldier)arrived).getHealth(),0F);
            if(hostile) {
                KOMEConflictRecord conflict=f.data.getConflictService().get(f.destination);assertNotNull(conflict);
                if(join)assertEquals(existing.getConflictId(),conflict.getConflictId());
                assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,order.status);
                KOMEConflictRecord.Commitment commitment=conflict.getCommitments().get("C1");assertNotNull(commitment);
                assertEquals(!join,commitment.validatedEvent.createdConflict);
                assertEquals("mordor",commitment.validatedEvent.authorityFactionId);
                assertEquals(order.id,commitment.movementOrderId);assertEquals(order.id,f.company.movementOrderId);
            } else {
                assertEquals(KOMEArmyMovementOrder.ARRIVED,order.status);assertTrue(f.company.movementOrderId.isEmpty());
                assertNull(f.data.getConflictService().get(f.destination));
            }
            int audits=f.data.centralAudit.size();assertFalse(KOMECompanyMapMovementSubmission.submit(f.service,f.data,f.player,
                UUID.randomUUID(),2,1,"C1",f.destination,preview.previewFingerprint,preview.confirmation.serviceFingerprint).accepted());
            assertEquals(1,f.data.armyMovements.size());assertEquals(0,f.company.movementAllowance);
            assertEquals(audits,f.data.centralAudit.size());assertEquals(1,physical.world.loadedEntityList.size());
        } finally {
            if(previousClass==null)EntityList.stringToClassMapping.remove(type);else EntityList.stringToClassMapping.put(type,previousClass);
            if(previousName==null)EntityList.classToStringMapping.remove(ArrivalSoldier.class);else EntityList.classToStringMapping.put(ArrivalSoldier.class,previousName);
        }
    }
    public static final class ArrivalSoldier extends LOTREntityGondorSoldier {
        public ArrivalSoldier(World world) {super(world);hiredNPCInfo=new ArrivalHiredInfo(this);}
        @Override protected void entityInit() {
            boolean remote=worldObj.isRemote;worldObj.isRemote=true;
            try {super.entityInit();} finally {worldObj.isRemote=remote;}
        }
        @Override public void readEntityFromNBT(NBTTagCompound nbt) {
            boolean remote=worldObj.isRemote;worldObj.isRemote=true;
            try {super.readEntityFromNBT(nbt);} finally {worldObj.isRemote=remote;}
        }
        @Override public void setAttackTarget(net.minecraft.entity.EntityLivingBase target,boolean speak) {
            // Arrival halt clears targeting; plain JUnit has no Forge combat-hook bootstrap.
            assertNull(target);
        }
    }
    private static final class ArrivalHiredInfo extends lotr.common.entity.npc.LOTRHiredNPCInfo {
        ArrivalHiredInfo(ArrivalSoldier npc) {super(npc);}
        @Override public void sendClientPacket(boolean full) { /* Inert transport; native halt remains real. */ }
    }
}
