package kome.common.data;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import kome.common.KOMEAccessFixture;
import lotr.common.entity.npc.LOTREntityMordorWarg;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.*;

/** Real vanilla block/entity collision queries: no mocked collision list hiding null-entity failures. */
public class KOMEJoinBattleReturnGeometryTest {
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void fullJoinPrepareResolvesBesideNativeNearbyEntityWithoutNullQuery() throws Exception {
        Fixture f=new Fixture();f.world.loadedEntityList.clear();
        f.world.provider.dimensionId=KOMETileTestResources.dimension();
        f.access.player.dimension=KOMETileTestResources.dimension();
        f.access.pledge(lotr.common.fac.LOTRFaction.GONDOR);
        KOMEWorldData data=f.access.data;
        KOMEArmyCompany company=new KOMEArmyCompany();
        company.id="C1";company.name="Exact";company.owner=f.access.player.id;
        company.faction=company.nativeFaction="gondor";company.currentTile="T100";
        company.totalPopulation=company.groundPopulation=20;
        data.armyCompanies.put(company.id,company);data.lastKnownPlayerFactions.put(company.owner,"gondor");
        KOMEJoinBattleCompanyAnchorServiceTest.AnchorNpc npc=
            KOMEAccessFixture.allocate(KOMEJoinBattleCompanyAnchorServiceTest.AnchorNpc.class);
        npc.id=java.util.UUID.randomUUID();npc.alive=true;npc.setUniqueID(npc.id);npc.worldObj=f.world;
        npc.width=1.2F;npc.height=1.8F;
        set(Entity.class,npc,"boundingBox",AxisAlignedBB.getBoundingBox(0,0,0,0,0,0));
        npc.setPosition(KOMETileTestResources.x()+.5D,74D,KOMETileTestResources.z()+.5D);
        f.world.loadedEntityList.add(npc);
        KOMEHiredUnitRecord unit=new KOMEHiredUnitRecord();unit.entity=npc.id;unit.owner=unit.sourcePlayer=company.owner;
        unit.companyId=company.id;unit.companyName=company.name;unit.currentTile=unit.sourceTileId="T100";
        unit.unitFaction=unit.populationOwningFaction="gondor";unit.type=KOMEPopulationType.OFFENSIVE;
        unit.cost=unit.baseCost=unit.populationSpent=20;unit.assignPersistedUnitClass(KOMEHiredUnitClass.CAMPAIGN);
        company.units.add(unit.entity);data.hiredUnits.put(unit.entity,unit);
        KOMEConflictRecord conflict=data.getConflictService().start("T100",KOMEConflictRecord.State.ORDINARY,
            KOMEConflictContracts.ExpectedConflict.absent(),java.util.Collections.<KOMEConflictContracts.GarrisonSeed>emptyList(),
            new KOMEConflictContracts.Context(10L,"test","prepare")).record;
        conflict=data.getConflictService().beginFactionParticipation("T100",
            KOMEConflictContracts.ExpectedConflict.at(conflict.getConflictId(),conflict.getRevision()),
            "gondor",new KOMEConflictContracts.Context(11L,"test","prepare")).record;
        conflict=data.getConflictService().commit("T100",
            KOMEConflictContracts.ExpectedConflict.at(conflict.getConflictId(),conflict.getRevision()),
            new KOMEConflictContracts.CommitmentInput("C1",KOMEHiredUnitClass.CAMPAIGN,
                KOMEConflictRecord.EntryOrigin.LEGAL_ARRIVAL,"M1"),
            new KOMEConflictContracts.Context(12L,"test","prepare")).record;
        try(kome.common.KOMETestServerSession session=new kome.common.KOMETestServerSession(f.access)){
            session.server.worldServers=new net.minecraft.world.WorldServer[]{f.world};
            assertEquals(74,lotr.common.LOTRMod.getTrueTopBlock(f.world,
                (int)Math.floor(npc.posX),(int)Math.floor(npc.posZ)));
            KOMEStrategicDeploymentResolver.Validation probe=KOMEStrategicDeploymentResolver.resolveAround(
                f.world,"T100",npc.posX,npc.posY,npc.posZ,24,f.access.player.width,f.access.player.height);
            assertTrue(probe.reason,probe.valid);
            KOMEJoinBattleEntryService.Preparation result=KOMEJoinBattlePhysicalAccess.INSTANCE.prepare(
                data,f.access.player,company,"T100",conflict.getConflictId(),conflict.getRevision());
            assertEquals(result.reason.toString(),KOMEJoinBattleService.Reason.ALLOWED,result.reason);
            Pose target=result.destination;
            AxisAlignedBB body=AxisAlignedBB.getBoundingBox(target.x-.3D,target.y,target.z-.3D,
                target.x+.3D,target.y+1.8D,target.z+.3D);
            assertFalse(npc.boundingBox.intersectsWith(body));
            try{f.world.getCollidingBoundingBoxes(null,body);fail("Old shared resolver would dereference null");}
            catch(NullPointerException expected){}
            assertTrue(KOMEJoinBattlePhysicalAccess.validatePlayerPlacement(f.world,target,f.access.player));
        }
    }
    @Test public void soleDestinationSurvivorRollbackPreservesCurrentNativeState() throws Exception {
        for(boolean changed:new boolean[]{false,true}) {
            Fixture f=new Fixture();f.world.loadedEntityList.clear();
            net.minecraft.entity.passive.EntityHorse survivor=new net.minecraft.entity.passive.EntityHorse(f.world);
            survivor.setPosition(144D,74D,144D);survivor.setHealth(20F);
            net.minecraft.nbt.NBTTagCompound historical=new net.minecraft.nbt.NBTTagCompound();
            assertTrue(survivor.writeMountToNBT(historical));
            Pose source=new Pose(0,8D,74D,9D,0F,0F),destination=new Pose(0,144D,74D,144D,0F,0F);
            KOMEJoinBattleDeploymentReceipt receipt=KOMEJoinBattleDeploymentReceipt.builder()
                .receiptId("JB1").actionToken("survivor").playerId(f.access.player.id)
                .conflictId("CF1").tileId("T100").acceptedConflictRevision(1L).factionId("gondor")
                .selectedCompanyId("C1").createdAtMillis(10L).updatedAtMillis(20L)
                .state(State.PENDING_ENTRY).returnAnchor(source).deploymentDestination(destination)
                .participationRecovery(ParticipationRecovery.REGISTRATION_REQUIRED)
                .enteredMounted(true).mountUuid(survivor.getUniqueID())
                .mountEntityType(net.minecraft.entity.EntityList.getEntityString(survivor))
                .mountProfile(MountProfile.VANILLA_HORSE).mountSourceAnchor(source)
                .mountTransferPhase(MountTransferPhase.DESTINATION_PUBLICATION_PENDING).temporaryMountNbt(historical).build();
            if(changed) {
                survivor.setHealth(7F);survivor.setHorseTamed(true);survivor.setHorseVariant(513);
                survivor.setHorseSaddled(true);survivor.setTemper(27);
                survivor.setCurrentItemOrArmor(0,new net.minecraft.item.ItemStack(net.minecraft.init.Items.iron_sword));
                // Native horse inventory is persisted independently of entity equipment.
                net.minecraft.nbt.NBTTagCompound state=new net.minecraft.nbt.NBTTagCompound();survivor.writeMountToNBT(state);
                state.setInteger("Type",1);state.setBoolean("ChestedHorse",true);
                net.minecraft.nbt.NBTTagList items=new net.minecraft.nbt.NBTTagList();
                net.minecraft.nbt.NBTTagCompound item=new net.minecraft.nbt.NBTTagCompound();
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.apple,3).writeToNBT(item);item.setByte("Slot",(byte)2);
                items.appendTag(item);state.setTag("Items",items);survivor.readFromNBT(state);
            }
            net.minecraft.nbt.NBTTagCompound current=new net.minecraft.nbt.NBTTagCompound();survivor.writeMountToNBT(current);
            if(changed)assertFalse(KOMEJoinBattlePhysicalAccess.semanticallyEquivalentMountNbt(historical,current));
            f.world.loadedEntityList.add(survivor);
            net.minecraft.entity.passive.EntityHorse other=new net.minecraft.entity.passive.EntityHorse(f.world);
            other.setPosition(145D,74D,144D);other.setHorseTamed(true);f.world.loadedEntityList.add(other);
            KOMEJoinBattlePhysicalAccess.RollbackMountResolution plan=KOMEJoinBattlePhysicalAccess.resolveRollbackMounts(
                f.access.player,receipt,java.util.Arrays.<Entity>asList(survivor,other),null);
            assertTrue(plan.reason.toString(),plan.success());assertSame(survivor,plan.source);assertTrue(plan.remove.isEmpty());
            assertTrue(plan.destinationSurvivor);
            assertTrue(f.access.data.getConflictService().start("T100",KOMEConflictRecord.State.ORDINARY,
                KOMEConflictContracts.ExpectedConflict.absent(),java.util.Collections.<KOMEConflictContracts.GarrisonSeed>emptyList(),
                new KOMEConflictContracts.Context(1L,"test","survivor recovery")).isSuccess());
            f.access.data.getJoinBattleDeploymentReceipts().publishNew(receipt);
            KOMEJoinBattleDeploymentReceipt rebased=KOMEJoinBattlePhysicalAccess.rebaseRollbackSnapshot(f.access.data,receipt,survivor);
            assertFalse("Snapshot is durable BEFORE consuming the sole survivor",survivor.isDead);
            net.minecraft.nbt.NBTTagCompound saved=new net.minecraft.nbt.NBTTagCompound();f.access.data.writeToNBT(saved);
            KOMEWorldData loaded=new KOMEWorldData();loaded.readFromNBT(saved);
            KOMEJoinBattleDeploymentReceipt restarted=loaded.getJoinBattleDeploymentReceipts().get(receipt.getReceiptId());
            assertTrue(KOMEJoinBattlePhysicalAccess.semanticallyEquivalentMountNbt(current,restarted.getTemporaryMountNbt()));
            survivor.setDead(); // Simulate interruption immediately after exact old-instance consumption.
            KOMEJoinBattlePhysicalAccess.Recreation result=KOMEJoinBattlePhysicalAccess.recreateDetailed(
                restarted.getTemporaryMountNbt(),f.world,source,restarted.getMountUuid(),
                restarted.getMountEntityType(),restarted.getMountProfile());
            assertTrue(result.reason.toString(),result.success());
            net.minecraft.entity.passive.EntityHorse returned=(net.minecraft.entity.passive.EntityHorse)result.entity;
            net.minecraft.nbt.NBTTagCompound restored=new net.minecraft.nbt.NBTTagCompound();returned.writeMountToNBT(restored);
            assertTrue(KOMEJoinBattlePhysicalAccess.semanticallyEquivalentMountNbt(current,restored));
            assertEquals(changed?7F:20F,returned.getHealth(),0F);
            if(changed)assertEquals(3,restored.getTagList("Items",10).getCompoundTagAt(0).getByte("Count"));
            assertNotEquals(survivor.getEntityId(),returned.getEntityId());assertEquals(source.x,returned.posX,0D);
            assertEquals(receipt.getMountUuid(),returned.getUniqueID());assertFalse(returned.isDead);
            assertFalse(other.isDead);assertEquals(145D,other.posX,0D);
            int live=0;for(Object candidate:f.world.loadedEntityList)if(!((Entity)candidate).isDead
                &&receipt.getMountUuid().equals(((Entity)candidate).getUniqueID()))live++;
            assertEquals(1,live);
            loaded.getJoinBattleDeploymentReceipts().restoreEntrySource(KOMEJoinBattleDeploymentReceipt.copyOf(restarted)
                .mountTransferPhase(MountTransferPhase.NOT_STARTED).temporaryMountNbt(null).build());
            returned.setDead(); // Genuine later death after verified restoration has no snapshot authority.
            assertNull(loaded.getJoinBattleDeploymentReceipts().get(receipt.getReceiptId()).getTemporaryMountNbt());
        }
    }

    @Test public void armedEgressUnsafePoseClearsSpawnFlagBeforeSynchronization() throws Exception {
        Fixture f=new Fixture();f.world.loadedEntityList.clear();f.world.noGround=true;
        net.minecraft.entity.passive.EntityHorse source=new net.minecraft.entity.passive.EntityHorse(f.world);
        source.setPosition(8D,74D,9D);
        net.minecraft.nbt.NBTTagCompound snapshot=new net.minecraft.nbt.NBTTagCompound();
        assertTrue(source.writeMountToNBT(snapshot));
        Pose target=new Pose(0,20D,74D,20D,0F,0F);
        KOMEJoinBattleDeploymentReceipt receipt=KOMEJoinBattleDeploymentReceipt.builder()
            .receiptId("JB1").actionToken("armed-egress").playerId(f.access.player.id)
            .conflictId("CF1").tileId("T100").acceptedConflictRevision(1L).factionId("gondor")
            .selectedCompanyId("C1").createdAtMillis(10L).updatedAtMillis(20L)
            .state(State.PENDING_EGRESS).deployedAtMillis(12L).egressRequestedAtMillis(20L)
            .egressReason("test").returnAnchor(target).deploymentDestination(new Pose(0,8D,74D,9D,0F,0F))
            .participationRecovery(ParticipationRecovery.PREEXISTING_ACTIVE)
            .enteredMounted(true).mountUuid(source.getUniqueID())
            .mountEntityType(net.minecraft.entity.EntityList.getEntityString(source))
            .mountProfile(MountProfile.VANILLA_HORSE).mountSourceAnchor(target)
            .mountTransferPhase(MountTransferPhase.EGRESS_TRANSFER_PENDING).temporaryMountNbt(snapshot).build();
        Field field=net.minecraftforge.common.DimensionManager.class.getDeclaredField("worlds");
        field.setAccessible(true);
        java.util.Map<Integer,net.minecraft.world.WorldServer> worlds=
            (java.util.Map<Integer,net.minecraft.world.WorldServer>)field.get(null);
        net.minecraft.world.WorldServer previous=worlds.put(0,f.world);
        try(kome.common.KOMETestServerSession session=new kome.common.KOMETestServerSession(f.access)){
            session.server.worldServers=new net.minecraft.world.WorldServer[]{f.world};
            String failure=KOMEJoinBattlePhysicalAccess.recoverArmedEgress(f.access.player,receipt);
            assertEquals("Mounted return recovery pose is not currently safe.",failure);
            assertTrue(f.world.spawnSawForce);
            assertEquals(1,f.world.loadedEntityList.size());
            Entity restored=(Entity)f.world.loadedEntityList.get(0);
            assertEquals(source.getUniqueID(),restored.getUniqueID());
            assertFalse(restored.forceSpawn);
            assertNotNull(receipt.getTemporaryMountNbt());
            assertEquals(failure,KOMEJoinBattlePhysicalAccess.recoverArmedEgress(f.access.player,receipt));
            assertEquals(1,f.world.loadedEntityList.size());
        }finally{if(previous==null)worlds.remove(0);else worlds.put(0,previous);}
    }

    @Test public void jb6CapturesRiderAndMountBaseSeparatelyAndRiderHeightRoundTrips() throws Exception {
        Fixture f=new Fixture();
        f.access.player.mountEntity(f.mount);f.mount.updateRiderPosition();
        assertEquals(74D,f.mount.posY,0D);
        assertEquals(74.850000023841858D,f.access.player.posY,1.0E-7D);
        Pose rider=f.playerPose(),base=f.mountPose();
        KOMEJoinBattleEntryService.Preparation prepared=KOMEJoinBattleEntryService.Preparation.mounted(
            rider,new Pose(0,100D,74D,100D,0F,0F),f.mount.getUniqueID(),
            "lotr.MordorWarg",MountProfile.LOTR_WARG,base,new net.minecraft.nbt.NBTTagCompound());
        assertSame(rider,prepared.returnAnchor);assertSame(base,prepared.mountSource);
        assertEquals(base.y,rider.y-f.mount.getMountedYOffset()-f.access.player.getYOffset(),1.0E-7D);
        assertTrue(KOMEJoinBattlePhysicalAccess.validateReturnPlacement(f.world,base,f.access.player,f.mount));
        assertFalse("rider height is not a supported mount base",
            KOMEJoinBattlePhysicalAccess.validateReturnPlacement(f.world,rider,f.access.player,f.mount));
    }

    @Test public void vanillaNullEntitySupportQueryThrowsOnceReturnedPairIsPresent() throws Exception {
        Fixture f=new Fixture();AxisAlignedBB support=f.supportBox();
        assertFalse("real ground is present",f.world.func_147461_a(support).isEmpty());
        try { f.world.getCollidingBoundingBoxes(null,support);fail("vanilla dereferences null query entity"); }
        catch(NullPointerException expected) { }
    }

    @Test public void exactMountedReturnIsSafeBeforeAndAfterPairArrives() throws Exception {
        Fixture f=new Fixture();Pose target=f.mountPose();
        f.mount.setPosition(100D,74D,100D);f.access.player.setPosition(100D,75D,100D);
        assertTrue(KOMEJoinBattlePhysicalAccess.validateReturnPlacement(f.world,target,f.access.player,f.mount));
        f.mount.setPosition(target.x,target.y,target.z);
        f.access.player.mountEntity(f.mount);f.mount.updateRiderPosition();
        assertTrue("final support query must remain valid with the returned pair present",
            KOMEJoinBattlePhysicalAccess.validateReturnPlacement(f.world,target,f.access.player,f.mount));
        assertSame(f.mount,f.access.player.ridingEntity);assertSame(f.access.player,f.mount.riddenByEntity);
    }

    @Test public void exactReturnSearchUsesSavedMountBaseNotRiderHeight() throws Exception {
        Fixture f=new Fixture();Pose base=f.mountPose();
        f.access.player.mountEntity(f.mount);f.mount.updateRiderPosition();
        KOMEJoinBattlePhysicalAccess.ReturnResolution result=
            KOMEJoinBattlePhysicalAccess.resolveReturnPlacement(f.world,base,f.access.player,f.mount);
        assertTrue(result.reason,result.valid);assertSame(base,result.pose);
        assertEquals(74D,result.pose.y,0D);
    }

    @Test public void obstructedBaseUsesBoundedNearbyBaseAndDerivedRiderHeadroom() throws Exception {
        Fixture f=new Fixture();Pose base=f.mountPose();
        f.world.obstruction=AxisAlignedBB.getBoundingBox(base.x-.7D,74D,base.z-.7D,
            base.x+.7D,78D,base.z+.7D);
        KOMEJoinBattlePhysicalAccess.ReturnResolution result=
            KOMEJoinBattlePhysicalAccess.resolveReturnPlacement(f.world,base,f.access.player,f.mount);
        assertTrue(result.reason,result.valid);
        assertEquals(74D,result.pose.y,0D);
        assertTrue(Math.abs(base.x-result.pose.x)<=8D&&Math.abs(base.z-result.pose.z)<=8D);
        assertTrue(KOMEJoinBattlePhysicalAccess.validateReturnPlacement(f.world,result.pose,f.access.player,f.mount));
    }

    @Test public void mountAndRiderObstructionsAreRejectedIndependently() throws Exception {
        Fixture f=new Fixture();f.access.player.mountEntity(f.mount);f.mount.updateRiderPosition();
        Pose base=f.mountPose();
        f.world.obstruction=AxisAlignedBB.getBoundingBox(base.x-.1D,74.1D,base.z-.1D,
            base.x+.1D,74.2D,base.z+.1D);
        assertFalse(KOMEJoinBattlePhysicalAccess.validateReturnPlacement(f.world,base,f.access.player,f.mount));
        double riderTop=base.y+f.mount.getMountedYOffset()+f.access.player.getYOffset()
            +f.access.player.height;
        f.world.obstruction=AxisAlignedBB.getBoundingBox(base.x-.1D,riderTop-.2D,base.z-.1D,
            base.x+.1D,riderTop-.1D,base.z+.1D);
        assertFalse(KOMEJoinBattlePhysicalAccess.validateReturnPlacement(f.world,base,f.access.player,f.mount));
    }

    @Test public void noTerrainSupportCannotBeSuppliedByTheMountItself() throws Exception {
        Fixture f=new Fixture();f.world.noGround=true;
        assertFalse(KOMEJoinBattlePhysicalAccess.validateReturnPlacement(f.world,f.mountPose(),f.access.player,f.mount));
        assertFalse(KOMEJoinBattlePhysicalAccess.resolveReturnPlacement(f.world,f.mountPose(),f.access.player,f.mount).valid);
        assertFalse(f.mount.isDead);assertSame(f.mount,f.world.loadedEntityList.get(0));
    }

    @Test public void genuinelyUnsafeReturnReportsObstructionWithoutMovingOrDestroyingPair() throws Exception {
        Fixture f=new Fixture();f.access.player.mountEntity(f.mount);f.mount.updateRiderPosition();
        Pose before=f.mountPose();f.world.noGround=true;
        KOMEJoinBattlePhysicalAccess.ReturnResolution result=
            KOMEJoinBattlePhysicalAccess.resolveReturnPlacement(f.world,before,f.access.player,f.mount);
        assertFalse(result.valid);assertTrue(result.reason.contains("no nearby safe position"));
        assertEquals(before.x,f.mount.posX,0D);assertEquals(before.y,f.mount.posY,0D);
        assertSame(f.mount,f.access.player.ridingEntity);assertSame(f.access.player,f.mount.riddenByEntity);
        assertFalse(f.mount.isDead);assertEquals(1,f.world.loadedEntityList.size());
    }

    @Test public void unrelatedOwnedMountIsNeitherSupportNorIgnoredCollision() throws Exception {
        Fixture f=new Fixture();Entity other=new LOTREntityMordorWarg(f.world);
        other.setPosition(f.mount.posX,f.mount.posY,f.mount.posZ);f.world.loadedEntityList.add(other);
        assertNotEquals(f.mount.getUniqueID(),other.getUniqueID());
        assertFalse(KOMEJoinBattlePhysicalAccess.validateReturnPlacement(f.world,f.mountPose(),f.access.player,f.mount));
        assertFalse(other.isDead);assertFalse(f.mount.isDead);assertEquals(2,f.world.loadedEntityList.size());
    }

    @Test public void unmountedStandingPoseIsNotAdjustedByMountedOffsets() throws Exception {
        Fixture f=new Fixture();f.world.loadedEntityList.clear();f.access.player.setPosition(100D,74D,100D);
        Pose standing=new Pose(0,8.5D,74D,9.5D,15F,20F);
        KOMEJoinBattlePhysicalAccess.ReturnResolution result=
            KOMEJoinBattlePhysicalAccess.resolveReturnPlacement(f.world,standing,f.access.player,null);
        assertTrue(result.reason,result.valid);assertSame(standing,result.pose);
    }

    @Test public void unmountedNativeNearbyEntityQueryIsNullSafeWithoutIgnoringCollisions() throws Exception {
        Fixture f=new Fixture();
        double x=f.mount.posX+f.mount.width/2D+f.access.player.width/2D+.1D;
        Pose target=new Pose(0,x,74D,f.mount.posZ,0F,0F);
        AxisAlignedBB body=AxisAlignedBB.getBoundingBox(x-.3D,74D,target.z-.3D,x+.3D,75.8D,target.z+.3D);
        try{f.world.getCollidingBoundingBoxes(null,body);fail("Old native query should dereference null with a nearby entity");}
        catch(NullPointerException expected){}
        assertTrue(KOMEJoinBattlePhysicalAccess.validatePlayerPlacement(f.world,target,f.access.player));
        f.mount.setPosition(target.x,target.y,target.z);
        assertFalse(KOMEJoinBattlePhysicalAccess.validatePlayerPlacement(f.world,target,f.access.player));
        f.world.loadedEntityList.clear();
        f.world.obstruction=body;
        assertFalse(KOMEJoinBattlePhysicalAccess.validatePlayerPlacement(f.world,target,f.access.player));
    }

    private static final class Fixture {
        // Uses native World collision implementations; only terrain/entity storage is deterministic.
        final KOMEAccessFixture access=new KOMEAccessFixture();
        final GeometryWorld world=KOMEAccessFixture.allocate(GeometryWorld.class);
        final LOTREntityMordorWarg mount;
        Fixture() throws Exception {
            set(World.class,world,"provider",new WorldProviderSurface());
            set(World.class,world,"collidingBoundingBoxes",new ArrayList<AxisAlignedBB>());
            world.terrainProvider=(IChunkProvider)java.lang.reflect.Proxy.newProxyInstance(
                IChunkProvider.class.getClassLoader(),new Class[]{IChunkProvider.class},(proxy,method,args)->{
                    if(method.getReturnType()==boolean.class)return true;
                    if(method.getReturnType()==Chunk.class)
                        return world.getChunkFromChunkCoords((Integer)args[0],(Integer)args[1]);
                    if(method.getReturnType()==int.class)return 1;
                    return null;
                });
            world.loadedEntityList=new ArrayList();world.playerEntities=new ArrayList();
            access.player.worldObj=world;access.player.width=.6F;access.player.height=1.8F;access.player.yOffset=0F;
            set(Entity.class,access.player,"boundingBox",AxisAlignedBB.getBoundingBox(0,0,0,0,0,0));
            access.player.setPosition(100D,74D,100D);world.playerEntities.add(access.player);
            mount=new LOTREntityMordorWarg(world);mount.setPosition(8.3980262724D,74D,9.714419076619D);
            world.loadedEntityList.add(mount);
        }
        Pose mountPose(){return new Pose(0,mount.posX,mount.posY,mount.posZ,0F,0F);}
        Pose playerPose(){return new Pose(0,access.player.posX,access.player.posY,access.player.posZ,0F,0F);}
        AxisAlignedBB supportBox(){double half=mount.width/2D;return AxisAlignedBB.getBoundingBox(
            mount.posX-half,mount.posY-.2D,mount.posZ-half,mount.posX+half,mount.posY+.01D,mount.posZ+half);}
    }
    private static void set(Class<?> type,Object target,String name,Object value) throws Exception {
        Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
    }
    public static final class GeometryWorld extends net.minecraft.world.WorldServer {
        private static final Block GROUND=new Block(Material.ground){};
        private static final Block AIR=new Block(Material.air){
            @Override public AxisAlignedBB getCollisionBoundingBoxFromPool(World world,int x,int y,int z){return null;}
        };
        boolean noGround,spawnSawForce;AxisAlignedBB obstruction;
        IChunkProvider terrainProvider;
        private GeometryWorld(){super(null,null,"unused",0,null,null);}
        @Override protected IChunkProvider createChunkProvider(){return null;}
        @Override protected int func_152379_p(){return 0;}
        @Override public Entity getEntityByID(int id){return null;}
        @Override public boolean spawnEntityInWorld(Entity entity){
            spawnSawForce=entity.forceSpawn;loadedEntityList.add(entity);return true;
        }
        @Override public void updateEntityWithOptionalForce(Entity entity,boolean force){}
        @Override public IChunkProvider getChunkProvider(){return terrainProvider;}
        @Override public Chunk getChunkFromChunkCoords(int x,int z){
            return new Chunk(this,x,z){@Override public int getTopFilledSegment(){return 64;}};
        }
        @Override public boolean blockExists(int x,int y,int z){return true;}
        @Override public int getHeightValue(int x,int z){return 74;}
        @Override public int getTopSolidOrLiquidBlock(int x,int z){return 74;}
        @Override public Block getBlock(int x,int y,int z){return !noGround&&y==73?GROUND:AIR;}
        @Override public List func_147461_a(AxisAlignedBB box){
            List result=super.func_147461_a(box);
            if(obstruction!=null&&obstruction.intersectsWith(box))result.add(obstruction);
            return result;
        }
        @Override public List getEntitiesWithinAABBExcludingEntity(Entity excluded,AxisAlignedBB box){
            List result=new ArrayList();
            for(Object object:loadedEntityList)addCandidate(result,(Entity)object,excluded,box);
            for(Object object:playerEntities)addCandidate(result,(Entity)object,excluded,box);
            return result;
        }
        private static void addCandidate(List result,Entity entity,Entity excluded,AxisAlignedBB box){
            if(entity!=excluded&&!entity.isDead&&entity.boundingBox.intersectsWith(box)&&!result.contains(entity))result.add(entity);
        }
    }
}
