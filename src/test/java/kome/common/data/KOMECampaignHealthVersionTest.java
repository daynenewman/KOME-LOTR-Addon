package kome.common.data;

import java.util.UUID;
import kome.common.KOMEAccessFixture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMECampaignHealthProjection.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.Component;

public class KOMECampaignHealthVersionTest {
    private static final class Fixture {
        final KOMEAccessFixture access = new KOMEAccessFixture();
        final EntityPig rider = new EntityPig(access.world), mount = new EntityPig(access.world);
        final KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        Fixture() throws Exception {
            rider.setHealth(4F); mount.setHealth(6F); record.entity = rider.getUniqueID(); record.owner = UUID.randomUUID();
            KOMEHiredUnitClassification.assignForCampaignWorkflow(record); access.data.hiredUnits.put(record.entity, record);
            KOMECampaignHealth.observe(record, rider);
        }
        KOMECampaignHealthProjection rider() { return read(record.survivingHealth); }
        void mount() { rider.mountEntity(mount); record.mounted = true;
            record.stationedEntityData = KOMEEntitySnapshots.snapshot(rider);
            KOMECampaignHealth.observe(record, rider); }
    }
    private KOMECampaignHealthProjection fenced(KOMECampaignHealthProjection p) {
        return new KOMECampaignHealthProjection(p.target, p.component, p.alias, p.hp, p.revision + 1, UUID.randomUUID(), p.terminal,
            new Fence(UUID.randomUUID(), 7, UUID.randomUUID()));
    }
    private void mutation(EntityPig e, float hp) {
        // Unit tests run without LaunchWrapper. Invoke exactly the production HEAD/RETURN bridge.
        KOMECampaignHealthEntity.beforeChange(e, hp); e.setHealth(hp); KOMECampaignHealthEntity.changed(e);
    }
    private void reject(Runnable r) { try { r.run(); fail("Must fail closed"); } catch (IllegalArgumentException expected) {
        assertTrue(expected.getMessage(), expected.getMessage().contains("CAMPAIGN_HEALTH"));
    } }
    @Test public void sameProjectionIsIdempotent() throws Exception {
        Fixture f = new Fixture(); KOMECampaignHealthProjection p = f.rider();
        KOMECampaignHealth.observe(f.record, f.rider); assertEquals(p.transition, KOMECampaignHealthEntity.projection(f.rider).transition);
        assertEquals(p.write(), f.rider().write()); assertSame(p, reconcile(p, p));
    }
    @Test public void newerPersistedIncreaseWinsOverStaleEntity() throws Exception {
        Fixture f = new Fixture(); KOMECampaignHealthProjection newer = f.rider().change(8F, false);
        f.record.survivingHealth = newer.write(); KOMECampaignHealth.reconcileLoaded(f.record, f.rider);
        assertEquals(8F, f.rider.getHealth(), 0F); assertEquals(newer.write(), f.rider().write());
    }
    @Test public void newerEntityDamageWinsOverOlderPersistedRecoveryLikeIncrease() throws Exception {
        Fixture f = new Fixture(); NBTTagCompound stale = f.record.survivingHealth;
        mutation(f.rider, 8F); mutation(f.rider, 2F); f.record.survivingHealth = stale;
        KOMECampaignHealth.reconcileLoaded(f.record, f.rider);
        assertEquals(2F, f.rider.getHealth(), 0F); assertEquals(2, f.rider().revision);
    }
    @Test public void contradictoryHpAtSameRevisionFailsClosed() throws Exception {
        Fixture f = new Fixture(); KOMECampaignHealthProjection p = f.rider();
        f.record.survivingHealth = new KOMECampaignHealthProjection(p.target,p.component,p.alias,8F,p.revision,p.transition,false,null).write();
        reject(() -> KOMECampaignHealth.reconcileLoaded(f.record, f.rider)); assertEquals(4F,f.rider.getHealth(),0F);
    }
    @Test public void contradictoryTransitionAtSameRevisionFailsClosed() throws Exception {
        Fixture f = new Fixture(); KOMECampaignHealthProjection p = f.rider();
        f.record.survivingHealth = new KOMECampaignHealthProjection(p.target,p.component,p.alias,p.hp,p.revision,UUID.randomUUID(),false,null).write();
        reject(() -> KOMECampaignHealth.reconcileLoaded(f.record, f.rider));
    }
    @Test public void fenceSurvivesDamageAndNativeHealing() throws Exception {
        Fixture f = new Fixture(); KOMECampaignHealthProjection p = fenced(f.rider());
        KOMECampaignHealthEntity.install(f.rider, p, f.record, false); KOMECampaignHealth.updateProjection(f.record,p);
        mutation(f.rider, 2F); assertEquals(p.fence, f.rider().fence);
        mutation(f.rider, 3F); assertEquals(p.fence, f.rider().fence); assertEquals(3, f.rider().revision);
    }
    @Test public void genuineMutationIncrementsOnceAndCopyDoesNotIncrement() throws Exception {
        Fixture f = new Fixture(); mutation(f.rider, 3F); UUID transition = f.rider().transition;
        mutation(f.rider, 3F); KOMECampaignHealth.observe(f.record,f.rider); f.record.writeToNBT();
        assertEquals(1,f.rider().revision); assertEquals(transition,f.rider().transition);
    }
    @Test public void riderReconstructionPreservesIdentityRevisionAndFence() throws Exception {
        Fixture f = new Fixture(); KOMECampaignHealthProjection p = fenced(f.rider()).change(3F,false);
        KOMECampaignHealthEntity.install(f.rider,p,f.record,true); KOMECampaignHealth.updateProjection(f.record,p);
        NBTTagCompound source = KOMEEntitySnapshots.snapshot(f.record,f.rider);
        f.record.healthObservedEntity=null; NBTTagCompound replacement=KOMECampaignHealth.movementSnapshot(f.record,source);
        KOMECampaignHealth.prepareReconstruction(replacement); KOMECampaignHealthProjection q=read(replacement.getCompoundTag(ENTITY_TAG));
        assertEquals(p.target,q.target); assertEquals(p.transition,q.transition); assertEquals(p.revision,q.revision);
        assertEquals(p.fence,q.fence); assertNotEquals(p.alias,q.alias); assertEquals(p.alias,read(source.getCompoundTag(ENTITY_TAG)).alias);
        f.record.entity=q.alias; KOMECampaignHealth.reconstructed(f.record,p.alias,q.alias,null,null);
        assertEquals(q.write(),read(f.record.survivingHealth).write());
    }
    @Test public void verifiedMountReconstructionPreservesIndependentAuthority() throws Exception {
        Fixture f=new Fixture(); f.mount();
        KOMECampaignHealthProjection p=fenced(KOMECampaignHealthEntity.projection(f.mount));
        KOMECampaignHealthEntity.install(f.mount,p,f.record,false); KOMECampaignHealth.updateProjection(f.record,p);
        NBTTagCompound source=KOMEEntitySnapshots.snapshot(f.record,f.rider);
        KOMECampaignHealth.prepareReconstruction(source); KOMECampaignHealthProjection q=read(source.getCompoundTag("Riding").getCompoundTag(ENTITY_TAG));
        assertEquals(p.target,q.target); assertEquals(p.fence,q.fence); assertEquals(p.revision,q.revision); assertNotEquals(p.alias,q.alias);
    }
    @Test public void dismountDoesNotTransferOldMountIdentityToReplacement() throws Exception {
        Fixture f=new Fixture(); f.mount(); UUID old=KOMECampaignHealthEntity.projection(f.mount).target;
        f.rider.mountEntity(null); KOMECampaignHealth.observe(f.record,f.rider);
        mutation(f.mount,5F); assertEquals(5F,read(KOMECampaignHealth.mountHealth(f.record.survivingHealth,f.mount.getUniqueID())).hp,0F);
        EntityPig other=new EntityPig(f.access.world); other.setHealth(9F); f.rider.mountEntity(other); KOMECampaignHealth.observe(f.record,f.rider);
        assertNull(KOMECampaignHealthEntity.projection(other)); assertEquals(9F,other.getHealth(),0F);
        assertEquals(old,read(KOMECampaignHealth.mountHealth(f.record.survivingHealth,f.mount.getUniqueID())).target);
    }
    @Test public void obsoleteNestedSnapshotCannotTargetUnrelatedMount() throws Exception {
        Fixture f=new Fixture(); f.mount(); NBTTagCompound source=KOMEEntitySnapshots.snapshot(f.record,f.rider);
        NBTTagCompound mount=source.getCompoundTag("Riding"); UUID wrong=UUID.randomUUID();
        mount.setLong("UUIDMost",wrong.getMostSignificantBits()); mount.setLong("UUIDLeast",wrong.getLeastSignificantBits());
        reject(() -> KOMECampaignHealth.movementSnapshot(f.record,source));
    }
    @Test public void terminalRiderCannotReconstructOrHeal() throws Exception {
        Fixture f=new Fixture(); NBTTagCompound source=KOMEEntitySnapshots.snapshot(f.record,f.rider);
        mutation(f.rider,0F); KOMECampaignHealthEntity.terminal(f.rider);
        reject(() -> KOMECampaignHealth.movementSnapshot(f.record,source)); reject(() -> mutation(f.rider,5F));
        assertEquals(0F,f.rider.getHealth(),0F); assertTrue(f.rider().terminal);
    }
    @Test public void terminalMountCannotReconstructFromOldNestedSnapshot() throws Exception {
        Fixture f=new Fixture(); f.mount(); NBTTagCompound source=KOMEEntitySnapshots.snapshot(f.record,f.rider);
        mutation(f.mount,0F); KOMECampaignHealthEntity.terminal(f.mount);
        f.rider.mountEntity(null); reject(() -> KOMECampaignHealth.movementSnapshot(f.record,source));
        assertTrue(read(KOMECampaignHealth.mountHealth(f.record.survivingHealth,f.mount.getUniqueID())).terminal);
    }
    @Test public void unloadRoundTripPreservesDetachedAuthority() throws Exception {
        Fixture f=new Fixture(); f.mount(); mutation(f.rider,2.375F); mutation(f.mount,5.125F);
        NBTTagCompound nbt=f.record.writeToNBT(); f.record.healthObservedEntity=null; f.record.healthObservedMount=null;
        KOMEHiredUnitRecord restored=new KOMEHiredUnitRecord(); restored.readFromNBT(nbt);
        assertEquals(f.record.survivingHealth,restored.survivingHealth);
        nbt.getCompoundTag("SurvivingHealth").setFloat("CurrentHP",99F); assertEquals(2.375F,read(restored.survivingHealth).hp,0F);
    }
    @Test public void entitySerializationAcknowledgmentIsDetachedAndCoherent() throws Exception {
        Fixture f=new Fixture(); NBTTagCompound nbt=KOMEEntitySnapshots.snapshot(f.record,f.rider); KOMECampaignHealthProjection old=read(nbt.getCompoundTag(ENTITY_TAG));
        mutation(f.rider,2F); assertEquals(old.write(),nbt.getCompoundTag(ENTITY_TAG));
        EntityPig reload=new EntityPig(f.access.world); KOMECampaignHealthEntity.beginRead(reload,nbt); reload.readFromNBT(nbt); KOMECampaignHealthEntity.endRead(reload,nbt);
        assertEquals(old.write(),KOMECampaignHealthEntity.projection(reload).write());
    }
    @Test public void savedHpAckContradictionFailsClosed() throws Exception {
        Fixture f=new Fixture(); NBTTagCompound nbt=KOMEEntitySnapshots.snapshot(f.record,f.rider); nbt.setFloat("HealF",8F);
        EntityPig reload=new EntityPig(f.access.world); KOMECampaignHealthEntity.beginRead(reload,nbt); reload.readFromNBT(nbt);
        reject(() -> KOMECampaignHealthEntity.endRead(reload,nbt));
    }
    @Test public void missingAcknowledgmentWithDifferentHpRemainsUnresolved() throws Exception {
        Fixture f=new Fixture(); NBTTagCompound nbt=KOMEEntitySnapshots.snapshot(f.record,f.rider); nbt.removeTag(ENTITY_TAG); nbt.setFloat("HealF",9F);
        EntityPig stale=new EntityPig(f.access.world); stale.readFromNBT(nbt);
        reject(() -> KOMECampaignHealth.reconcileLoaded(f.record,stale)); assertEquals(9F,stale.getHealth(),0F);
    }
    @Test public void worldFirstAndEntityFirstReconcileWithoutHealingLaterDamage() throws Exception {
        Fixture f=new Fixture(); KOMECampaignHealthProjection source=f.rider(), recoveryLike=fenced(source.change(8F,false));
        assertSame(recoveryLike,reconcile(source,recoveryLike));
        KOMECampaignHealthProjection damage=recoveryLike.change(1F,false);
        assertSame(damage,reconcile(recoveryLike,damage)); assertEquals(recoveryLike.fence,damage.fence);
    }
    @Test public void lostOrConflictingFenceFailsClosed() throws Exception {
        Fixture f=new Fixture(); KOMECampaignHealthProjection p=fenced(f.rider()), q=p.change(2F,false);
        KOMECampaignHealthProjection lost=new KOMECampaignHealthProjection(q.target,q.component,q.alias,q.hp,q.revision,q.transition,false,null);
        reject(() -> reconcile(p,lost));
    }
    @Test public void overflowRejectedBeforeSideEffect() throws Exception {
        Fixture f=new Fixture(); KOMECampaignHealthProjection p=f.rider();
        p=new KOMECampaignHealthProjection(p.target,p.component,p.alias,p.hp,Long.MAX_VALUE-1,p.transition,false,null);
        KOMECampaignHealthEntity.install(f.rider,p,f.record,false); reject(() -> mutation(f.rider,2F)); assertEquals(4F,f.rider.getHealth(),0F);
    }
    @Test public void malformedProjectionIsRejected() throws Exception {
        Fixture f=new Fixture();
        for(String key:new String[]{"HealthFormatVersion","LogicalTargetId","Component","PhysicalAlias","CurrentHP","HealthRevision","LastTransitionId","Terminal"}) {
            NBTTagCompound n=f.rider().write(); n.removeTag(key); reject(() -> read(n));
        }
        NBTTagCompound n=f.rider().write(); n.setFloat("CurrentHP",Float.NaN); reject(() -> read(n));
    }
    @Test public void dismountedMountReloadUsesNewerPersistedProjection() throws Exception {
        Fixture f=new Fixture(); f.mount(); NBTTagCompound old=KOMEEntitySnapshots.snapshot(f.record,f.rider).getCompoundTag("Riding");
        mutation(f.mount,3F); f.rider.mountEntity(null);
        EntityPig reload=new EntityPig(f.access.world); KOMECampaignHealthEntity.beginRead(reload,old); reload.readFromNBT(old); KOMECampaignHealthEntity.endRead(reload,old);
        KOMECampaignHealth.reconcileMount(f.access.data,reload); assertEquals(3F,reload.getHealth(),0F);
    }
    @Test public void healthObservationLeavesRecoveryRegistryAndSchemasUntouched() throws Exception {
        Fixture f=new Fixture(); f.mount(); KOMEOrdinaryBattleRecoveryRegistry registry=f.access.data.getOrdinaryBattleRecovery();


        // Health changes do not create episodes, grants, or WAL preparation.
        assertEquals(3,KOMEWorldData.CONFLICT_DATA_SCHEMA_VERSION); assertEquals(12,KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        assertTrue(registry.targets().isEmpty()); assertTrue(registry.episodes().isEmpty());
    }
    @Test public void directNbtRestoreCannotEraseNewerAuthorizedState() throws Exception {
        Fixture f=new Fixture(); NBTTagCompound old=KOMEEntitySnapshots.snapshot(f.record,f.rider);
        mutation(f.rider,8F); KOMECampaignHealthProjection newer=f.rider();
        KOMECampaignHealthEntity.beginRead(f.rider,old); f.rider.readFromNBT(old); KOMECampaignHealthEntity.endRead(f.rider,old);
        assertEquals(8F,f.rider.getHealth(),0F);assertEquals(newer.write(),KOMECampaignHealthEntity.projection(f.rider).write());
    }
    @Test public void destructionRecordedInWorldBlocksFurtherLoadedMutations() throws Exception {
        Fixture f=new Fixture(); KOMECampaignHealth.markTerminal(f.record,f.record.entity);
        reject(() -> mutation(f.rider,8F));assertEquals(4F,f.rider.getHealth(),0F);assertTrue(f.rider().terminal);
    }
    @Test public void missingOrWrongReconstructionLineageFailsBeforeRecordPublication() throws Exception {
        Fixture f=new Fixture(); NBTTagCompound before=(NBTTagCompound)f.record.survivingHealth.copy();
        reject(() -> KOMECampaignHealth.reconstructed(f.record,UUID.randomUUID(),UUID.randomUUID(),null,null));
        assertEquals(before,f.record.survivingHealth);
    }
    @Test public void oldGeometryCannotRecreateFormerMountAfterRemount() throws Exception {
        Fixture f=new Fixture(); f.mount(); NBTTagCompound old=KOMEEntitySnapshots.snapshot(f.record,f.rider);
        EntityPig other=new EntityPig(f.access.world); f.rider.mountEntity(other); KOMECampaignHealth.observe(f.record,f.rider);
        reject(() -> KOMECampaignHealth.movementSnapshot(f.record,old));
        NBTTagCompound current=KOMEEntitySnapshots.snapshot(f.record,f.rider);
        reject(() -> KOMECampaignHealth.movementSnapshot(f.record,current));
    }
    @Test public void oldMountedGeometryAfterDismountIsUnresolvedRatherThanResurrected() throws Exception {
        Fixture f=new Fixture(); f.mount(); NBTTagCompound old=KOMEEntitySnapshots.snapshot(f.record,f.rider);
        f.rider.mountEntity(null); KOMECampaignHealth.observe(f.record,f.rider);
        reject(() -> KOMECampaignHealth.movementSnapshot(f.record,old));
        assertFalse(KOMECampaignHealth.movementSnapshot(f.record,KOMEEntitySnapshots.snapshot(f.record,f.rider)).hasKey("Riding"));
    }
    @Test public void unloadedSharedMountLookupChoosesNewerRetainedAuthority() throws Exception {
        Fixture f=new Fixture(); f.mount(); KOMECampaignHealthProjection old=KOMECampaignHealthEntity.projection(f.mount);
        KOMEHiredUnitRecord other=new KOMEHiredUnitRecord(); other.entity=UUID.randomUUID(); KOMEHiredUnitClassification.assignForCampaignWorkflow(other);
        other.survivingHealth=new KOMECampaignHealthProjection(UUID.randomUUID(),Component.RIDER,other.entity,4F,0,UUID.randomUUID(),false,null).write();
        KOMECampaignHealth.updateProjection(other,old); f.access.data.hiredUnits.put(other.entity,other);
        mutation(f.mount,2F); KOMECampaignHealthProjection result=KOMECampaignHealth.persisted(f.access.data,old.target,Component.MOUNT);
        assertEquals(2F,result.hp,0F);assertEquals(1,result.revision);
        assertNull(KOMECampaignHealth.persisted(f.access.data,UUID.randomUUID(),Component.MOUNT));
    }
    @Test public void ordinaryUntrackedEntityRemainsUnaffected() throws Exception {
        Fixture f=new Fixture(); mutation(f.mount,7F); assertNull(KOMECampaignHealthEntity.projection(f.mount)); assertEquals(7F,f.mount.getHealth(),0F);
    }
}
