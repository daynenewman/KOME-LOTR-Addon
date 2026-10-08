package kome.common.data;

import kome.common.KOMEAccessFixture;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.world.ChunkEvent;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.UUID;

import static org.junit.Assert.*;

public class KOMEHiredUnitPhysicalLocatorTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void optionalLocatorIsNotInferredFromStationedEntityData() {
        KOMEHiredUnitRecord source = bareRecord(UUID.randomUUID(), true);
        source.stationedEntityData = new NBTTagCompound();
        source.stationedEntityData.setDouble("PosX", KOMETileTestResources.x());
        source.stationedEntityData.setDouble("PosZ", KOMETileTestResources.z());
        KOMEHiredUnitRecord restored = read(source.writeToNBT());
        assertNull(restored.getPhysicalLocator());
    }

    @Test public void locatorRoundTripsWithoutChangingRootOrConflictSchemas() throws Exception {
        Fixture f = fixture(true);
        assertTrue(observe(f, Capture.LIVE, 101L, false));
        NBTTagCompound saved = f.record.writeToNBT();
        KOMEHiredUnitRecord restored = read(saved);
        assertLocator(restored.getPhysicalLocator(), f.entity.getUniqueID(),
            KOMEHiredUnitPhysicalLocator.CaptureKind.LIVE_OBSERVATION,
            f.entity.posX, f.entity.posY, f.entity.posZ);
        assertEquals(11, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        assertEquals(2, KOMEConflictPersistence.DATA_SCHEMA_VERSION);
    }

    @Test public void strictNestedCodecRejectsFutureSchemaUuidChunkTileCoordinatesAndKind() throws Exception {
        Fixture f = fixture(true); observe(f, Capture.LIVE, 101L, false);
        NBTTagCompound good = f.record.writeToNBT();
        reject(mutated(good, tag -> tag.setInteger("PhysicalLocatorDataSchemaVersion", 2)));
        reject(mutated(good, tag -> tag.setString("Entity", UUID.randomUUID().toString())));
        reject(mutated(good, tag -> tag.setInteger("ChunkX", tag.getInteger("ChunkX") + 1)));
        reject(mutated(good, tag -> tag.setString("PhysicalTile", differentKnownTile())));
        reject(mutated(good, tag -> tag.setDouble("X", Double.NaN)));
        reject(mutated(good, tag -> tag.setString("CaptureKind", "HISTORIC_SNAPSHOT")));
    }

    @Test public void loadedCampaignObservationIsChangeDrivenAndRefreshesExactCheckpoint() throws Exception {
        Fixture f = fixture(true); f.data.setDirty(false);
        assertTrue(observe(f, Capture.LIVE, 100L, false));
        KOMEHiredUnitPhysicalLocator first = f.record.getPhysicalLocator();
        f.data.setDirty(false);
        f.entity.setPosition(f.entity.posX + 0.25D, f.entity.posY, f.entity.posZ + 0.25D);
        assertFalse(observe(f, Capture.LIVE, 101L, false));
        assertFalse(f.data.isDirty());
        assertSame(first, f.record.getPhysicalLocator());
        assertTrue(observe(f, Capture.LIVE, 102L, true));
        assertEquals(f.entity.posX, f.record.getPhysicalLocator().getX(), 0D);
        assertEquals(102L, f.record.getPhysicalLocator().getObservedAtMillis());
    }

    @Test public void chunkChangeUpdatesAndUnsupportedDimensionInvalidates() throws Exception {
        Fixture f = fixture(true); observe(f, Capture.LIVE, 100L, false);
        double[] next = sameTileDifferentChunk(f.entity.posX, f.entity.posZ);
        f.entity.setPosition(next[0], f.entity.posY, next[1]);
        assertTrue(observe(f, Capture.LIVE, 101L, false));
        assertEquals(floor(next[0]) >> 4, f.record.getPhysicalLocator().getChunkX());
        f.world.provider.dimensionId = 999999;
        assertTrue(observe(f, Capture.LIVE, 102L, false));
        assertNull("A dimension without canonical tile geometry cannot retain a verified locator",
            f.record.getPhysicalLocator());
    }

    @Test public void actualChunkUnloadCapturesExactLivePositionNotStationedSnapshot() throws Exception {
        Fixture f = fixture(true);
        double[] unload = sameTileDifferentChunk(f.entity.posX, f.entity.posZ);
        f.entity.setPosition(unload[0] + 0.375D, 71.25D, unload[1] + 0.625D);
        f.record.stationedEntityData = new NBTTagCompound();
        f.record.stationedEntityData.setDouble("X", f.entity.posX - 8000D);
        f.record.stationedEntityData.setDouble("Z", f.entity.posZ - 8000D);
        Chunk chunk = new Chunk(f.world, floor(f.entity.posX) >> 4, floor(f.entity.posZ) >> 4);
        chunk.entityLists[0].add(f.entity);
        new KOMEEvents().onCampaignChunkUnload(new ChunkEvent.Unload(chunk));
        assertLocator(f.record.getPhysicalLocator(), f.entity.getUniqueID(),
            KOMEHiredUnitPhysicalLocator.CaptureKind.CHUNK_UNLOAD,
            f.entity.posX, f.entity.posY, f.entity.posZ);
    }

    @Test public void rekeyClearsOldIdentityAndVerifiedReplacementInstallsNewLocator() throws Exception {
        Fixture f = fixture(true); observe(f, Capture.LIVE, 100L, false);
        UUID replacement = UUID.randomUUID();
        f.company.units.set(0, replacement);
        f.data.hiredUnits.remove(f.record.entity);
        f.record.entity = replacement;
        f.data.hiredUnits.put(replacement, f.record);
        assertTrue(KOMEHiredUnitPhysicalLocatorService.clear(f.data, f.record));
        LocatorPig newEntity = new LocatorPig(f.world, replacement);
        newEntity.setPosition(f.entity.posX, f.entity.posY, f.entity.posZ);
        assertTrue(KOMEHiredUnitPhysicalLocatorService.observe(f.data, f.record, newEntity,
            KOMEHiredUnitPhysicalLocator.CaptureKind.STRATEGIC_RECONSTRUCTION, 200L, true));
        assertLocator(f.record.getPhysicalLocator(), replacement,
            KOMEHiredUnitPhysicalLocator.CaptureKind.STRATEGIC_RECONSTRUCTION,
            newEntity.posX, newEntity.posY, newEntity.posZ);
    }

    @Test public void virtualTerminalAndOrdinaryStatesCannotRetainLocatorAuthority() throws Exception {
        Fixture f = fixture(true); observe(f, Capture.LIVE, 100L, false);
        f.record.movingEntityData = new NBTTagCompound();
        assertTrue(observe(f, Capture.LIVE, 101L, false));
        assertNull(f.record.getPhysicalLocator());

        f.record.movingEntityData = null;
        observe(f, Capture.LIVE, 102L, false);
        KOMEHiredUnitRecord removed = f.data.removeTerminatedHiredUnit(f.record.entity, "test death");
        assertNotNull(removed); assertNull(removed.getPhysicalLocator());

        Fixture ordinary = fixture(false);
        assertFalse(observe(ordinary, Capture.LIVE, 100L, false));
        assertNull(ordinary.record.getPhysicalLocator());
    }

    @Test public void saveRestartPreservesVerifiedLocatorAndOldRecordStaysLocatorless() throws Exception {
        Fixture f = fixture(true); observe(f, Capture.UNLOAD, 100L, true);
        KOMEHiredUnitRecord restored = read(f.record.writeToNBT());
        assertEquals(f.record.getPhysicalLocator().getObservedAtMillis(),
            restored.getPhysicalLocator().getObservedAtMillis());
        KOMEHiredUnitRecord legacy = read(bareRecord(UUID.randomUUID(), true).writeToNBT());
        assertNull(legacy.getPhysicalLocator());
    }

    @Test public void malformedNestedLocatorRejectsWorldAtomicallyAndWriteBlocks() throws Exception {
        Fixture source = fixture(true); observe(source, Capture.LIVE, 100L, false);
        source.data.initializeIntegratedWorld();
        NBTTagCompound saved = new NBTTagCompound(); source.data.writeToNBT(saved);
        NBTTagList units = saved.getTagList("HiredUnits", 10);
        assertEquals(1, units.tagCount());
        units.getCompoundTagAt(0).getCompoundTag(KOMEHiredUnitPhysicalLocator.TAG)
            .setInteger("ChunkX", Integer.MAX_VALUE);
        KOMEWorldData target = new KOMEWorldData("locator-atomic");
        UUID sentinel = UUID.randomUUID(); target.hiredUnits.put(sentinel, bareRecord(sentinel, false));
        try { target.readFromNBT(saved); fail("Malformed locator loaded"); }
        catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("physical locator")
                || expected.getCause() != null);
        }
        assertTrue(target.isWriteBlocked());
        assertTrue(target.hiredUnits.containsKey(sentinel));
        assertFalse(target.hiredUnits.containsKey(source.record.entity));
    }

    @Test public void productionLifecycleUsesLiveEntitiesAndNeverStationedDataAsLocatorInput() throws Exception {
        String events = text("src/main/java/kome/common/data/KOMEEvents.java");
        String movement = text("src/main/java/kome/common/command/KOMECommandTroops.java");
        String awareness = text("src/main/java/kome/common/data/KOMEServerTileAwareness.java");
        assertTrue(events.contains("CaptureKind.CHUNK_UNLOAD"));
        assertTrue(events.contains("CaptureKind.LIVE_OBSERVATION"));
        assertTrue(movement.contains("CaptureKind.STRATEGIC_RECONSTRUCTION"));
        assertTrue(movement.contains("KOMEHiredUnitPhysicalLocatorService.clear(data, record);"));
        assertFalse(awareness.contains("PhysicalLocator"));
        assertFalse(KOMEHiredUnitRecord.class.getDeclaredField("physicalLocator")
            .getType().equals(NBTTagCompound.class));
    }

    private enum Capture { LIVE, UNLOAD }

    private static boolean observe(Fixture f, Capture capture, long time, boolean exact) {
        return KOMEHiredUnitPhysicalLocatorService.observe(f.data, f.record, f.entity,
            capture == Capture.UNLOAD ? KOMEHiredUnitPhysicalLocator.CaptureKind.CHUNK_UNLOAD
                : KOMEHiredUnitPhysicalLocator.CaptureKind.LIVE_OBSERVATION,
            time, exact);
    }

    private static Fixture fixture(boolean campaign) throws Exception {
        KOMEAccessFixture access = new KOMEAccessFixture();
        access.world.provider.dimensionId = KOMETileTestResources.dimension();
        LocatorPig entity = new LocatorPig(access.world, UUID.randomUUID());
        entity.setPosition(KOMETileTestResources.x() + 0.5D, 70D,
            KOMETileTestResources.z() + 0.5D);
        KOMEHiredUnitRecord record = bareRecord(entity.getUniqueID(), campaign);
        record.currentTile = "T100";
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C1"; company.currentTile = "T100"; company.units.add(record.entity);
        record.companyId = company.id;
        access.data.hiredUnits.put(record.entity, record);
        access.data.armyCompanies.put(company.id, company);
        return new Fixture(access.data, access.world, entity, record, company);
    }

    private static KOMEHiredUnitRecord bareRecord(UUID id, boolean campaign) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = id; record.owner = UUID.randomUUID(); record.sourcePlayer = record.owner;
        record.currentTile = "T100"; record.sourceTileId = "T100";
        if (campaign) KOMEHiredUnitClassification.assignForCampaignWorkflow(record);
        return record;
    }

    private static KOMEHiredUnitRecord read(NBTTagCompound nbt) {
        KOMEHiredUnitRecord result = new KOMEHiredUnitRecord(); result.readFromNBT(nbt); return result;
    }

    private static void reject(NBTTagCompound nbt) {
        try { read(nbt); fail("Malformed locator loaded"); }
        catch (IllegalArgumentException expected) { }
    }

    private interface Mutation { void apply(NBTTagCompound locator); }
    private static NBTTagCompound mutated(NBTTagCompound source, Mutation mutation) {
        NBTTagCompound copy = (NBTTagCompound) source.copy();
        mutation.apply(copy.getCompoundTag(KOMEHiredUnitPhysicalLocator.TAG));
        return copy;
    }

    private static String differentKnownTile() {
        return "T100".equals(KOMETileTestResources.real().resolve(
            KOMETileTestResources.dimension(), KOMETileTestResources.x(),
            KOMETileTestResources.z()).tileId) ? "T101" : "T100";
    }

    private static double[] sameTileDifferentChunk(double x, double z) {
        int original = floor(x) >> 4;
        for (int blocks = 16; blocks <= 256; blocks += 16) {
            for (int sign : new int[] {1, -1}) {
                double candidate = x + sign * blocks;
                KOMETileResolution resolved = KOMETileWorldResolver.INSTANCE.resolveWorldPosition(
                    KOMETileTestResources.dimension(), candidate, z);
                if (resolved.status == KOMETileResolution.Status.RESOLVED
                        && "T100".equals(resolved.tileId) && (floor(candidate) >> 4) != original)
                    return new double[] {candidate, z};
            }
        }
        throw new AssertionError("T100 fixture has no nearby second chunk");
    }

    private static void assertLocator(KOMEHiredUnitPhysicalLocator locator, UUID id,
            KOMEHiredUnitPhysicalLocator.CaptureKind kind, double x, double y, double z) {
        assertNotNull(locator); assertEquals(id, locator.getEntityId());
        assertEquals(kind, locator.getCaptureKind());
        assertEquals(x, locator.getX(), 0D); assertEquals(y, locator.getY(), 0D);
        assertEquals(z, locator.getZ(), 0D); assertEquals("T100", locator.getPhysicalTileId());
        assertEquals(floor(x) >> 4, locator.getChunkX());
        assertEquals(floor(z) >> 4, locator.getChunkZ());
    }

    private static int floor(double value) { return (int) Math.floor(value); }

    private static String text(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }

    private static final class Fixture {
        final KOMEWorldData data; final KOMEAccessFixture.TestWorld world;
        final LocatorPig entity; final KOMEHiredUnitRecord record; final KOMEArmyCompany company;
        Fixture(KOMEWorldData data, KOMEAccessFixture.TestWorld world, LocatorPig entity,
                KOMEHiredUnitRecord record, KOMEArmyCompany company) {
            this.data = data; this.world = world; this.entity = entity;
            this.record = record; this.company = company;
        }
    }

    private static final class LocatorPig extends EntityPig {
        private final UUID id;
        LocatorPig(net.minecraft.world.World world, UUID id) { super(world); this.id = id; }
        @Override public UUID getUniqueID() { return id; }
    }
}
