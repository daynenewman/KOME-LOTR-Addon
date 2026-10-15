package kome.common.data;

import java.io.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.*;
import kome.common.KOMEAccessFixture;
import kome.common.network.KOMEPacketJoinBattleViewResponse;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.Packet;
import net.minecraft.world.chunk.Chunk;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.*;
import static kome.common.data.KOMEJoinBattleEgressService.Failure;

/** Real surface search, physical transfer, receipts, capped audit, retry and NBT restart. */
public class KOMEJoinBattleSameTileFallbackTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    @After public void reset(){KOMEJoinBattleEgressService.INSTANCE.resetSession();KOMEJoinBattleActionTokenService.INSTANCE.clearForTests();}

    @Test public void localFailureUsesSavedCanonicalArrivalAndVerifiedClosureAllowsUnrelatedJoin() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();
        Pose original=receipt(f).getReturnAnchor();
        assertFalse(KOMEJoinBattlePhysicalAccess.resolveReturnPlacement(f.world,original,f.access.player,null).valid);
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            KOMEJoinBattleEgressService.Preparation p=KOMEJoinBattlePhysicalAccess.prepareEgress(f.access.data,f.access.player,receipt(f));
            assertTrue(p.failureReason,p.ready);assertEquals(80.5D,p.playerTarget.x,0D);assertEquals(74D,p.playerTarget.y,0D);
            assertEquals("T100",tile(p.playerTarget));
            assertTrue(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(f.access.data,f.access.player,100L,new Physical(f)));
            KOMEJoinBattleSurfaceRecoveryTest.assertClosed(f.access.data);
            assertEquals(80.5D,f.access.player.posX,0D);assertEquals(74D,f.access.player.posY,0D);
            assertEquals(original,receipt(f).getReturnAnchor());
            KOMEJoinBattleSurfaceRecoveryTest.assertUnrelatedJoin(f.access.data,f);
        }
    }

    @Test public void packagedArrivalIsUsedWhenSavedRallyIsAbsentOrInvalid() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();
        KOMETileGameplayDefaults.Point point=KOMETileGameplayDefaults.get().getArrivalDefault("T100");
        // Keep the real packaged coordinates; move only the deterministic test raster/terrain.
        int x=(int)Math.floor(point.x),z=(int)Math.floor(point.z);
        publish(point.dimensionId(),-x+80,-z+9);
        f.world.provider.dimensionId=point.dimensionId();f.access.player.dimension=point.dimensionId();
        Pose source=new Pose(point.dimensionId(),point.x-72D,82.5D,point.z,0F,0F);
        KOMEJoinBattleDeploymentReceipt r=KOMEJoinBattleReturnGeometryTest.returnReceipt(f,source);
        f.world.groundFilter=(bx,bz)->Math.abs(bx-x)<=4&&Math.abs(bz-z)<=4;
        for(boolean invalidSaved:new boolean[]{false,true}){
            if(invalidSaved)f.access.data.setTileWaypoint("T100",KOMETileWaypoint.RALLY,point.dimensionId(),point.x+100D,80D,point.z,"bad",true);
            else f.access.data.clearTileWaypoint("T100",KOMETileWaypoint.RALLY);
            try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
                KOMEJoinBattleEgressService.Preparation p=KOMEJoinBattlePhysicalAccess.prepareEgress(f.access.data,f.access.player,r);
                assertTrue(p.failureReason,p.ready);assertEquals(point.x,p.playerTarget.x,0D);assertEquals(point.z,p.playerTarget.z,0D);
                assertEquals("T100",tile(p.playerTarget));
            }
        }
    }

    @Test public void packagedDefaultOutsideItsTileIsRejectedEvenWhenGroundExistsThere() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();f.access.data.clearTileWaypoint("T100",KOMETileWaypoint.RALLY);
        KOMETileGameplayDefaults.Point point=KOMETileGameplayDefaults.get().getArrivalDefault("T100");
        assertNotEquals("T100",KOMETileWorldResolver.INSTANCE.resolveWorldPosition(point.dimensionId(),point.x,point.z).tileId);
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            KOMEJoinBattleEgressService.Preparation p=KOMEJoinBattlePhysicalAccess.prepareEgress(f.access.data,f.access.player,receipt(f));
            assertFalse(p.ready);assertEquals(Failure.SOURCE_TILE_FALLBACK_OUTSIDE_TILE,p.failure);
            assertPending(f,100L);assertNull(receipt(f).getClosureOutcome());
        }
    }

    @Test public void fallbackSearchRejectsAdjacentTileAndIncludesCenterAndSameTileColumns() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();
        f.access.data.setTileWaypoint("T100",KOMETileWaypoint.RALLY,0,95.5D,82.5D,9.5D,"boundary",true);
        f.world.groundFilter=(x,z)->x>=96;
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            assertPending(f,100L);assertEquals(Failure.LOCAL_SURFACE_NOT_FOUND,lastCategory(f.access.data));
            f.world.groundFilter=(x,z)->x>=96||x==94;
            KOMEJoinBattleEgressService.Preparation p=KOMEJoinBattlePhysicalAccess.prepareEgress(f.access.data,f.access.player,receipt(f));
            assertTrue(p.failureReason,p.ready);assertEquals(94.5D,p.playerTarget.x,0D);assertEquals("T100",tile(p.playerTarget));
        }
    }

    @Test public void gapAndWrongDimensionSavedRalliesAreRejected() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();
        for(int dimension:new int[]{0,77}){
            f.access.data.setTileWaypoint("T100",KOMETileWaypoint.RALLY,dimension,500D,80D,9D,"bad",true);
            KOMEJoinBattleReturnAuthority.Anchors anchors=KOMEJoinBattleReturnAuthority.resolve(f.access.data,"T100",receipt(f).getReturnAnchor(),KOMETileWorldResolver.INSTANCE.readView());
            assertTrue(anchors.poses.isEmpty());assertTrue(anchors.rejected);
        }
    }

    @Test public void unresolvedOriginalSourceKeepsLocalRecoveryButCannotInventSourceFromRallyOrBattle() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();
        KOMETileWorldResolver.INSTANCE.invalidate();
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            assertPending(f,100L);assertEquals(Failure.SOURCE_TILE_UNRESOLVED,lastCategory(f.access.data));
            assertEquals(140D,f.access.player.posX,0D);
            f.world.groundFilter=(x,z)->x==8&&z==9;
            KOMEJoinBattleEgressService.Preparation p=KOMEJoinBattlePhysicalAccess.prepareEgress(f.access.data,f.access.player,receipt(f));
            assertTrue(p.failureReason,p.ready);assertEquals(8.5D,p.playerTarget.x,0D);
        }
    }

    @Test public void unavailableSavedDimensionStaysPendingWithDurableSpecificReason() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();
        KOMEWorldData data=new KOMEWorldData();
        data.getJoinBattleDeploymentReceipts().publishNew(KOMEJoinBattleReturnGeometryTest.returnReceipt(f,new Pose(77,8.5D,82.5D,9.5D,0F,0F)));
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(data,f.access.player,100L,new Physical(f)));
            assertEquals(State.PENDING_EGRESS,data.getJoinBattleDeploymentReceipts().get("JB1").getState());
            assertEquals(Failure.RETURN_DIMENSION_UNAVAILABLE,lastCategory(data));
            assertTrue(KOMEJoinBattleEgressService.INSTANCE.pendingReason(data,data.getJoinBattleDeploymentReceipts().get("JB1")).contains("saved return dimension is unavailable"));
        }
    }

    @Test public void repeatedFailuresAreDeduplicatedAcrossRestartAndChangedFailureUpdatesAuditAndUi() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();f.world.noGround=true;
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            Physical physical=new Physical(f);
            assertPending(f,100L);assertEquals(1,failures(f.access.data).size());
            for(long now:new long[]{5100L,60100L,120100L})assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(f.access.data,f.access.player,now,physical));
            assertEquals(1,failures(f.access.data).size());
            KOMEAuditEntry first=failures(f.access.data).get(0);
            assertEquals("JB1",first.subject);assertEquals(f.access.player.id.toString(),first.actor);
            assertTrue(first.details.contains("conflict=CF1"));assertTrue(first.details.contains("sourceTile=T100"));
            KOMEWorldData loaded=restart(f.access.data);
            KOMEJoinBattleEgressService.INSTANCE.resetSession();
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(loaded,f.access.player,200L,physical));
            assertEquals(1,failures(loaded).size());
            KOMETileWorldResolver.INSTANCE.invalidate();
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(loaded,f.access.player,60200L,physical));
            assertEquals(2,failures(loaded).size());assertEquals(Failure.SOURCE_TILE_UNRESOLVED,lastCategory(loaded));
            KOMEJoinBattleService.Projection view=KOMEJoinBattleService.INSTANCE.evaluate(loaded,f.access.player,"T100");
            String text=KOMEPacketJoinBattleViewResponse.forPlayer(view,f.access.player.id,loaded).reasonText;
            assertTrue(text,text.contains("original tile could not be resolved"));assertTrue(text.contains("retry"));
            assertFalse(text.contains("JB1"));assertFalse(text.contains("CF1"));assertFalse(text.contains(f.access.player.id.toString()));
            KOMEJoinBattleEgressService.INSTANCE.resetSession();
            assertEquals(text,KOMEJoinBattleEgressService.INSTANCE.pendingReason(loaded,loaded.getJoinBattleDeploymentReceipts().get("JB1")));
        }
    }

    @Test public void temporaryFailureAfterRestartRetriesAtCanonicalFallbackAndClosesOnce() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();f.world.noGround=true;
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            Physical p=new Physical(f);assertPending(f,100L);
            KOMEWorldData loaded=restart(f.access.data);KOMEJoinBattleEgressService.INSTANCE.resetSession();
            f.world.noGround=false;KOMEJoinBattleEgressService.INSTANCE.onLogin(loaded,f.access.player);
            assertTrue(KOMEJoinBattleEgressService.INSTANCE.tick(loaded,f.access.player,200L,p));
            KOMEJoinBattleSurfaceRecoveryTest.assertClosed(loaded);assertEquals(80.5D,f.access.player.posX,0D);
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.tick(loaded,f.access.player,60200L,p));assertEquals(1,p.applies);
        }
    }

    @Test public void mountedFallbackTransfersExactUuidSnapshotAndRiderBeforeClosure() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();EntityHorse mount=mount(f);
        Pose base=new Pose(0,8.5D,82.5D,9.5D,0F,0F);
        KOMEJoinBattleDeploymentReceipt r=mountedReceipt(f,mount,base);
        publishFixtureReceipt(f,r);
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            KOMEJoinBattleEgressService.Preparation prepared=KOMEJoinBattlePhysicalAccess.prepareEgress(f.access.data,f.access.player,r);
            assertTrue(prepared.failureReason,prepared.ready);assertTrue(prepared.transferRecordedMount);
            assertEquals(80.5D,prepared.mountTarget.x,0D);assertEquals(74D,prepared.mountTarget.y,0D);
            assertNotNull(prepared.mountSnapshot);assertFalse(mount.isDead);assertSame(mount,f.access.player.ridingEntity);
            Physical p=new Physical(f);
            assertTrue(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(f.access.data,f.access.player,100L,p));
            KOMEJoinBattleSurfaceRecoveryTest.assertClosed(f.access.data);
            assertTrue(p.armed);assertEquals(MountDisposition.RETURNED_WITH_PLAYER,receipt(f).getMountDisposition());
            Entity returned=f.access.player.ridingEntity;
            assertNotSame(mount,returned);assertTrue(mount.isDead);assertEquals(mount.getUniqueID(),returned.getUniqueID());
            assertEquals(EntityList.getEntityString(mount),EntityList.getEntityString(returned));assertSame(f.access.player,returned.riddenByEntity);
            assertEquals(80.5D,returned.posX,0D);assertEquals(base,receipt(f).getMountSourceAnchor());
            assertNull(receipt(f).getTemporaryMountNbt());assertEquals(MountTransferPhase.TERMINAL,receipt(f).getMountTransferPhase());
            int copies=0;for(Object e:f.world.loadedEntityList)if(e instanceof Entity&&!((Entity)e).isDead&&mount.getUniqueID().equals(((Entity)e).getUniqueID()))copies++;
            assertEquals(1,copies);
        }
    }

    @Test public void mountedFallbackWithoutSafeSurfaceKeepsPairAndReceiptPending() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();EntityHorse mount=mount(f);
        publishFixtureReceipt(f,mountedReceipt(f,mount,new Pose(0,8.5D,82.5D,9.5D,0F,0F)));
        f.world.noGround=true;
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            assertPending(f,100L);assertSame(mount,f.access.player.ridingEntity);assertFalse(mount.isDead);
            assertEquals(MountTransferPhase.DEPLOYMENT_COMPLETE,receipt(f).getMountTransferPhase());
            assertNull(receipt(f).getTemporaryMountNbt());
        }
    }

    @Test public void restartAfterInterruptedMountedFallbackRecoversExactPairAndFinishesWithoutDuplicate() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();EntityHorse mount=mount(f);
        publishFixtureReceipt(f,mountedReceipt(f,mount,new Pose(0,8.5D,82.5D,9.5D,0F,0F)));
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            Physical interrupted=new Physical(f){
                @Override public KOMEJoinBattleEgressService.PhysicalResult egress(EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt r,KOMEJoinBattleEgressService.Preparation p){
                    KOMEJoinBattleEgressService.PhysicalResult moved=super.egress(player,r,p);
                    assertTrue(moved.failureReason,moved.completed);
                    return KOMEJoinBattleEgressService.PhysicalResult.failed("Simulated interruption before durable closure.");
                }
            };
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(f.access.data,f.access.player,100L,interrupted));
            assertEquals(MountTransferPhase.EGRESS_TRANSFER_PENDING,receipt(f).getMountTransferPhase());
            Entity survivor=f.access.player.ridingEntity;assertEquals(80.5D,survivor.posX,0D);
            KOMEWorldData loaded=restart(f.access.data);KOMEJoinBattleEgressService.INSTANCE.resetSession();
            Physical retry=new Physical(f);KOMEJoinBattleEgressService.INSTANCE.onLogin(loaded,f.access.player);
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.tick(loaded,f.access.player,200L,retry));
            assertEquals(MountTransferPhase.DEPLOYMENT_COMPLETE,loaded.getJoinBattleDeploymentReceipts().get("JB1").getMountTransferPhase());
            assertSame(survivor,f.access.player.ridingEntity);assertEquals(0,retry.applies);
            assertTrue(KOMEJoinBattleEgressService.INSTANCE.tick(loaded,f.access.player,60200L,retry));
            KOMEJoinBattleSurfaceRecoveryTest.assertClosed(loaded);assertSame(survivor,f.access.player.ridingEntity);
            assertEquals(1,retry.applies);assertEquals(mount.getUniqueID(),survivor.getUniqueID());
        }
    }

    @Test public void missingArmedMountSnapshotCannotUseReadyFallbackToFabricateCompletion() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();EntityHorse mount=mount(f);
        NBTTagCompound snapshot=new NBTTagCompound();snapshot.setString("id","Horse"); // Missing source position.
        publishFixtureReceipt(f,KOMEJoinBattleDeploymentReceipt.copyOf(
            mountedReceipt(f,mount,new Pose(0,8.5D,82.5D,9.5D,0F,0F)))
            .mountTransferPhase(MountTransferPhase.EGRESS_TRANSFER_PENDING).temporaryMountNbt(snapshot).build());
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            assertPending(f,100L);assertEquals(Failure.MOUNT_RETURN_UNRESOLVED,lastCategory(f.access.data));
            assertNotNull(receipt(f).getTemporaryMountNbt());assertSame(mount,f.access.player.ridingEntity);
        }
    }

    @Test public void formalRetreatStrategicGateWinsEvenWhenOnlyCanonicalFallbackIsReady() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();order.id="M1";order.companyId="C1";
        order.formalRetreatBatch=new KOMEFormalRetreatBatch(UUID.randomUUID(),f.access.player.id,"CF1","T100","JB1",3L,
            Arrays.asList(new KOMEFormalRetreatBatch.Member("C1","M1",f.access.player.id,Arrays.asList("T100","T101"),1,0,
                KOMEFormalRetreatBatch.Progress.PREPARED)),KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING,false);
        f.access.data.armyMovements.put(order.id,order);
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding ignored=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            assertTrue(KOMEJoinBattlePhysicalAccess.prepareEgress(f.access.data,f.access.player,receipt(f)).ready);
            Physical p=new Physical(f);assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(f.access.data,f.access.player,100L,p));
            assertEquals(0,p.prepares);assertEquals(0,p.applies);assertTrue(failures(f.access.data).isEmpty());
            assertEquals(State.PENDING_EGRESS,receipt(f).getState());
        }
    }

    @Test public void auditRealPackagedArrivalsReportsLegacyOutsidePoints() {
        List<String> outside=new ArrayList<String>();
        for(String tile:KOMETileTestResources.real().colorsById().keySet()){
            KOMETileGameplayDefaults.Point p=KOMETileGameplayDefaults.get().getArrivalDefault(tile);if(p==null)continue;
            KOMETileResolution r=KOMETileTestResources.real().resolve(p.dimensionId(),(int)Math.floor(p.x),(int)Math.floor(p.z));
            if(r.status!=KOMETileResolution.Status.RESOLVED||!tile.equals(r.tileId))outside.add(tile+"->"+r.status+"/"+r.tileId);
        }
        Collections.sort(outside);System.out.println("KOM91 packaged arrivals outside own tile: "+outside);
        assertFalse("Packaged historical exceptions must not be accepted implicitly",outside.isEmpty());
    }

    static KOMEJoinBattleReturnGeometryTest.Fixture fixture() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=KOMEJoinBattleSurfaceRecoveryTest.fixture();
        publish(0,0,0);KOMEJoinBattleSurfaceRecoveryTest.pending(f.access.data,f);
        f.access.data.setTileWaypoint("T100",KOMETileWaypoint.RALLY,0,80.5D,82.5D,9.5D,"saved arrival",true);
        f.world.groundFilter=(x,z)->x>=76&&x<=84&&z>=5&&z<=13;
        f.access.player.setPosition(140D,74D,9.5D);
        return f;
    }
    static void publish(int dimension,int originX,int originZ) throws Exception {
        BufferedImage image=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xFF000001);image.setRGB(1,0,0xFF000002);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        KOMETileWorldResolver.INSTANCE.publish(KOMETileRasterSnapshot.load(new ByteArrayInputStream(bytes.toByteArray()),
            new ByteArrayInputStream("0,0,1=T100\n0,0,2=T101\n".getBytes(StandardCharsets.UTF_8)),
            new KOMETileRasterSnapshot.Transform(dimension,originX,originZ,1,192,32),
            new HashSet<String>(Arrays.asList("T100","T101")),Collections.<String>emptySet()));
    }
    static String tile(Pose p){return KOMETileWorldResolver.INSTANCE.resolveWorldPosition(p.dimensionId,p.x,p.z).tileId;}
    static KOMEJoinBattleDeploymentReceipt receipt(KOMEJoinBattleReturnGeometryTest.Fixture f){return f.access.data.getJoinBattleDeploymentReceipts().get("JB1");}
    static void assertPending(KOMEJoinBattleReturnGeometryTest.Fixture f,long now){
        assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(f.access.data,f.access.player,now,new Physical(f)));
        assertEquals(State.PENDING_EGRESS,receipt(f).getState());assertNull(receipt(f).getClosureOutcome());
    }
    static List<KOMEAuditEntry> failures(KOMEWorldData data){
        List<KOMEAuditEntry> result=new ArrayList<KOMEAuditEntry>();
        for(KOMEAuditEntry e:KOMEAuditService.entries(data))if(e.domain.equals("join-battle")&&e.action.equals("egress-pending"))result.add(e);
        return result;
    }
    static Failure lastCategory(KOMEWorldData data){List<KOMEAuditEntry> list=failures(data);return Failure.valueOf(list.get(list.size()-1).reason.split(": ",2)[0]);}
    static KOMEWorldData restart(KOMEWorldData data){
        Optional<KOMETileRasterSnapshot> previous=KOMETileWorldResolver.INSTANCE.snapshot();
        KOMETileWorldResolver.INSTANCE.publish(KOMETileTestResources.real());
        try{
            data.initializeIntegratedWorld();NBTTagCompound saved=new NBTTagCompound();data.writeToNBT(saved);
            KOMEWorldData loaded=new KOMEWorldData("fallback-restart");loaded.readFromNBT(saved);return loaded;
        }finally{if(previous.isPresent())KOMETileWorldResolver.INSTANCE.publish(previous.get());else KOMETileWorldResolver.INSTANCE.invalidate();}
    }
    static void publishFixtureReceipt(KOMEJoinBattleReturnGeometryTest.Fixture f,KOMEJoinBattleDeploymentReceipt receipt){
        f.access.data.getJoinBattleDeploymentReceipts().replaceFrom(KOMEJoinBattleDeploymentRegistry.restore(
            Collections.singletonMap(receipt.getReceiptId(),receipt),2L));
    }
    static EntityHorse mount(KOMEJoinBattleReturnGeometryTest.Fixture f) throws Exception {
        EntityHorse mount=new EntityHorse(f.world);mount.setPosition(140D,74D,9.5D);f.world.loadedEntityList.add(mount);
        f.access.player.mountEntity(mount);mount.updateRiderPosition();
        Tracking tracker=KOMEAccessFixture.allocate(Tracking.class);tracker.rider=f.access.player;tracker.tracked=new HashSet<Entity>();f.world.tracker=tracker;
        return mount;
    }
    static KOMEJoinBattleDeploymentReceipt mountedReceipt(KOMEJoinBattleReturnGeometryTest.Fixture f,EntityHorse mount,Pose base){
        Pose rider=new Pose(base.dimensionId,base.x,base.y+mount.getMountedYOffset()+f.access.player.getYOffset(),base.z,base.yaw,base.pitch);
        return KOMEJoinBattleDeploymentReceipt.copyOf(receipt(f)).returnAnchor(rider).enteredMounted(true)
            .mountUuid(mount.getUniqueID()).mountEntityType(EntityList.getEntityString(mount)).mountProfile(MountProfile.VANILLA_HORSE)
            .mountSourceAnchor(base).mountTransferPhase(MountTransferPhase.DEPLOYMENT_COMPLETE).build();
    }
    static class Physical implements KOMEJoinBattleEgressService.PhysicalAccess {
        final KOMEJoinBattleReturnGeometryTest.Fixture f;int prepares,applies;boolean armed;
        Physical(KOMEJoinBattleReturnGeometryTest.Fixture f){this.f=f;}
        public EntityPlayerMP findOnline(UUID id){return id.equals(f.access.player.id)?f.access.player:null;}
        public KOMEJoinBattleEgressService.Preparation prepare(EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt r){return prepare(f.access.data,player,r);}
        public KOMEJoinBattleEgressService.Preparation prepare(KOMEWorldData data,EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt r){prepares++;return KOMEJoinBattlePhysicalAccess.prepareEgress(data,player,r);}
        public KOMEJoinBattleEgressService.PhysicalResult egress(EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt r,KOMEJoinBattleEgressService.Preparation p){
            applies++;armed=r.getMountTransferPhase()==MountTransferPhase.EGRESS_TRANSFER_PENDING&&r.getTemporaryMountNbt()!=null;
            return KOMEJoinBattlePhysicalAccess.egress(player,r,p);
        }
    }
    /** Deterministic native tracker availability; terrain, entity recreation and rider operations are real. */
    public static class Tracking extends EntityTracker {
        EntityPlayerMP rider;Set<Entity> tracked;
        private Tracking(){super(null);}
        @Override public void func_85172_a(EntityPlayerMP player,Chunk chunk){for(Object e:player.worldObj.loadedEntityList)if(e instanceof Entity&&!((Entity)e).isDead)tracked.add((Entity)e);}
        @Override public Set<net.minecraft.entity.player.EntityPlayer> getTrackingPlayers(Entity entity){return tracked.contains(entity)?Collections.<net.minecraft.entity.player.EntityPlayer>singleton(rider):Collections.<net.minecraft.entity.player.EntityPlayer>emptySet();}
        @Override public void removeEntityFromAllTrackingPlayers(Entity entity){tracked.remove(entity);}
        @Override public void func_151247_a(Entity entity,Packet packet){}
    }
}
