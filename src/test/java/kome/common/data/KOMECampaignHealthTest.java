package kome.common.data;

import kome.common.KOMEAccessFixture;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.junit.Test;
import java.lang.reflect.Method;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMECampaignHealthTest {
    private KOMEHiredUnitRecord record() {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID(); record.owner = UUID.randomUUID();
        KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        return record;
    }
    private NBTTagCompound snapshot(float rider, float mount) {
        NBTTagCompound snapshot = new NBTTagCompound();
        snapshot.setFloat("HealF", rider); snapshot.setShort("Health", (short) Math.ceil(rider));
        if (mount > 0F) snapshot.setTag("Riding", snapshot(mount, -1F));
        return snapshot;
    }

    @Test public void exactFractionalRiderAndMountSurviveNativeSnapshotReconstructionAndPreparation() throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        EntityPig rider = new EntityPig(fixture.world), mount = new EntityPig(fixture.world);
        rider.setHealth(3.375F); mount.setHealth(7.625F); rider.mountEntity(mount);
        KOMEHiredUnitRecord record = record(); record.mounted = true;
        record.movingEntityData = KOMEEntitySnapshots.snapshot(record, rider);
        assertEquals(3.375F, record.movingEntityData.getFloat("HealF"), 0F);
        assertEquals(7.625F, record.movingEntityData.getCompoundTag("Riding").getFloat("HealF"), 0F);
        KOMEHiredUnitRecord restarted = new KOMEHiredUnitRecord(); restarted.readFromNBT(record.writeToNBT());
        NBTTagCompound movement = KOMECampaignHealth.movementSnapshot(restarted, restarted.movingEntityData);
        Class<?> command = kome.common.command.KOMECommandTroops.class;
        Method create = command.getDeclaredMethod("createEntityTree", NBTTagCompound.class, World.class); create.setAccessible(true);
        Method prepare = command.getDeclaredMethod("prepareMovementRespawnEntity", Entity.class); prepare.setAccessible(true);
        EntityLivingBase reconstructed = (EntityLivingBase) create.invoke(null, movement, fixture.world);
        assertNotNull(reconstructed);
        for (int retry = 0; retry < 3; retry++) prepare.invoke(null, reconstructed);
        assertEquals(3.375F, reconstructed.getHealth(), 0F);
        assertEquals(7.625F, ((EntityLivingBase) reconstructed.ridingEntity).getHealth(), 0F);
    }

    @Test public void fresherDamageOverridesStaleStationedSnapshotAndSaveReadsCurrentHpWithoutWaitingForTick() throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        EntityPig rider = new EntityPig(fixture.world), mount = new EntityPig(fixture.world);
        rider.setHealth(9F); mount.setHealth(9F); rider.mountEntity(mount);
        KOMEHiredUnitRecord record = record(); record.mounted = true;
        record.stationedEntityData = KOMEEntitySnapshots.snapshot(record, rider);
        rider.setHealth(2.125F); mount.setHealth(4.875F); // damage after the last stored full snapshot
        KOMEHiredUnitRecord restored = new KOMEHiredUnitRecord(); restored.readFromNBT(record.writeToNBT());
        for (int retry = 0; retry < 3; retry++) {
            NBTTagCompound movement = KOMECampaignHealth.movementSnapshot(restored, restored.stationedEntityData);
            assertEquals(2.125F, movement.getFloat("HealF"), 0F);
            assertEquals(4.875F, movement.getCompoundTag("Riding").getFloat("HealF"), 0F);
            restored.movingEntityData = movement;
            KOMEHiredUnitRecord next = new KOMEHiredUnitRecord(); next.readFromNBT(restored.writeToNBT()); restored = next;
        }
    }

    @Test public void unloadAndReloadCannotReplaceKnownDamageWithStaleHigherHealth() throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        EntityPig original = new EntityPig(fixture.world); original.setHealth(1.875F);
        KOMEHiredUnitRecord record = record(); KOMECampaignHealth.observe(record, original);
        record.healthObservedEntity = null; // unload detaches physical authority
        KOMEHiredUnitRecord restored = new KOMEHiredUnitRecord(); restored.readFromNBT(record.writeToNBT());
        EntityPig stale = new EntityPig(fixture.world); stale.setHealth(9F);
        KOMECampaignHealth.reconcileLoaded(restored, stale);
        assertEquals(1.875F, stale.getHealth(), 0F);
        stale.setHealth(1.25F); KOMECampaignHealth.observe(restored, stale);
        assertEquals(1.25F, restored.survivingHealth.getFloat("Current"), 0F);
    }

    @Test public void mountAttachedAfterRiderJoinCannotReplaceFresherSavedDamage() throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        EntityPig rider = new EntityPig(fixture.world), mount = new EntityPig(fixture.world);
        rider.mountEntity(mount); rider.setHealth(4.125F); mount.setHealth(2.875F);
        KOMEHiredUnitRecord record = record(); record.mounted = true; KOMECampaignHealth.observe(record, rider);
        KOMEHiredUnitRecord restored = new KOMEHiredUnitRecord(); restored.readFromNBT(record.writeToNBT());
        rider.mountEntity(null); rider.setHealth(9F); mount.setHealth(9F);
        KOMECampaignHealth.reconcileLoaded(restored, rider); // rider joins before Riding is attached
        assertEquals(4.125F, rider.getHealth(), 0F);
        rider.mountEntity(mount); KOMECampaignHealth.observe(restored, rider);
        assertEquals(2.875F, mount.getHealth(), 0F);
        assertEquals(2.875F, restored.survivingHealth.getCompoundTag("Riding").getFloat("Current"), 0F);
        // Subsequent real live observations remain fresh; this is not a permanent healing cap.
        mount.setHealth(3.125F); KOMECampaignHealth.observe(restored, rider);
        assertEquals(3.125F, restored.survivingHealth.getCompoundTag("Riding").getFloat("Current"), 0F);
    }

    @Test public void corruptMissingAndNonpositiveSurvivorHealthNeverBecomesMaxHp() {
        for (float invalid : new float[]{0F, -1F, Float.NaN, Float.POSITIVE_INFINITY}) {
            expectFailure(record(), snapshot(invalid, -1F));
        }
        expectFailure(record(), new NBTTagCompound());
        NBTTagCompound corrupt = snapshot(4F, -1F); corrupt.setString("HealF", "bad");
        expectFailure(record(), corrupt);
        KOMEHiredUnitRecord record = record(); record.survivingHealth = new NBTTagCompound();
        record.survivingHealth.setFloat("Current", 0F);
        expectFailure(record, snapshot(20F, -1F));
    }

    @Test public void invalidMountHealthAndMissingMountAuthorityFailExplicitly() {
        NBTTagCompound rider = snapshot(4.25F, 1F); rider.getCompoundTag("Riding").removeTag("HealF");
        rider.getCompoundTag("Riding").setFloat("Health", 0F); expectFailure(record(), rider);
        KOMEHiredUnitRecord record = record(); record.survivingHealth = new NBTTagCompound();
        record.survivingHealth.setFloat("Current", 4.25F);
        expectFailure(record, snapshot(4.25F, 2.5F));
    }

    @Test public void snapshotsAreDetachedAndFractionalLegacyHealthRemainsExact() {
        NBTTagCompound saved = snapshot(6.125F, 3.75F), before = (NBTTagCompound) saved.copy();
        NBTTagCompound movement = KOMECampaignHealth.movementSnapshot(record(), saved);
        assertEquals(before, saved); assertEquals(6.125F, movement.getFloat("HealF"), 0F);
        movement.setFloat("HealF", 1F); assertEquals(before, saved);
    }

    @Test public void actualChunkUnloadCapturesDamageAndDetachesThePhysicalReference() throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture(); EntityPig entity = new EntityPig(fixture.world);
        KOMEHiredUnitRecord record = record(); record.entity = entity.getUniqueID();
        fixture.data.hiredUnits.put(record.entity, record);
        entity.setHealth(9F); record.stationedEntityData = KOMEEntitySnapshots.snapshot(record, entity);
        entity.setHealth(2.375F); fixture.data.setDirty(false);
        net.minecraft.world.chunk.Chunk chunk = new net.minecraft.world.chunk.Chunk(fixture.world, 0, 0);
        chunk.entityLists[0].add(entity);
        new KOMEEvents().onCampaignChunkUnload(new net.minecraftforge.event.world.ChunkEvent.Unload(chunk));
        assertNull(record.healthObservedEntity); assertTrue(fixture.data.isDirty());
        assertEquals(2.375F, KOMECampaignHealth.movementSnapshot(record, record.stationedEntityData).getFloat("HealF"), 0F);
    }

    @Test public void mountDamageHookTracksRiderAndReadsExactPostEventHealthOnSave() throws Exception {
        KOMEAccessFixture fixture = new KOMEAccessFixture();
        EntityPig rider = new EntityPig(fixture.world), mount = new EntityPig(fixture.world);
        rider.mountEntity(mount); rider.setHealth(4.625F); mount.setHealth(9F);
        KOMEHiredUnitRecord record = record(); record.entity = rider.getUniqueID(); record.mounted = true;
        fixture.data.hiredUnits.put(record.entity, record); fixture.data.setDirty(false);
        new KOMEEvents().onCampaignHealthDamage(new net.minecraftforge.event.entity.living.LivingHurtEvent(mount, net.minecraft.util.DamageSource.generic, 2F));
        mount.setHealth(6.125F); // native damage is applied after the event
        assertTrue(fixture.data.isDirty());
        NBTTagCompound health = record.writeToNBT().getCompoundTag("SurvivingHealth");
        assertEquals(4.625F, health.getFloat("Current"), 0F);
        assertEquals(6.125F, health.getCompoundTag("Riding").getFloat("Current"), 0F);
    }

    private void expectFailure(KOMEHiredUnitRecord record, NBTTagCompound snapshot) {
        NBTTagCompound before = (NBTTagCompound) snapshot.copy();
        try { KOMECampaignHealth.movementSnapshot(record, snapshot); fail("Invalid HP must not reconstruct a survivor"); }
        catch (IllegalArgumentException expected) { assertEquals(before.toString(), snapshot.toString()); }
    }
}
