package kome.common.data;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import kome.common.KOMEAccessFixture;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.passive.EntityPig;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.*;
import net.minecraft.world.storage.*;
import net.minecraftforge.common.DimensionManager;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Combined KOM-18 and dev authority through production codecs, snapshots and checkpoints. */
public class KOMEOrdinaryBattleDevIntegrationTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    @Rule public final TemporaryFolder files = new TemporaryFolder();
    private KOMEPopulationTestConfig config;
    private Field session;
    private Object previousSession;
    @Before public void setup() throws Exception {
        config = new KOMEPopulationTestConfig();
        session = KOMERecoveryStartup.class.getDeclaredField("session"); session.setAccessible(true);
        previousSession = session.get(null); session.set(null, null);
    }
    @After public void cleanup() throws Exception {
        KOMERecoveryStartup.stopSession(); session.set(null, previousSession); config.close();
    }

    @Test public void rootElevenV3MigratesThroughRealStartupWithoutLosingRecoveryOrHealth() throws Exception {
        KOMEOrdinaryBattleRecoveryFoundationTest.Fixture f = new KOMEOrdinaryBattleRecoveryFoundationTest.Fixture(true);
        f.data.initializeIntegratedWorld();
        KOMEConflictMovementService.applyConflictHold(f.data, f.data.armyMovements.get("M1"), f.record(), 110);
        f.registry().end(f.record(), 200);
        f.registry().prepareGrants("CF1", "CF1:E1", 5F);
        KOMEHiredUnitRecord unit = f.data.hiredUnits.get(f.member("C1"));
        unit.survivingHealth = new KOMECampaignHealthProjection(f.registry().targetForPhysical(unit.entity).id,
            KOMEOrdinaryBattleRecoveryRegistry.Component.RIDER, unit.entity, 3.25F, 4, UUID.randomUUID(), false, null).write();
        NBTTagCompound old = KOMERecoveryRootCrashTest.save(f.data);
        old.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 11);
        for (String key : new String[]{"SeasonReset", "GovernanceSchema", "PlayerGovernance", "DailyJournal"}) old.removeTag(key);
        File directory = files.newFolder();
        KOMERecoveryRootCrashTest.writeRoot(directory, old);
        KOMERecoveryRootCrashTest.load(directory, loaded -> {
            assertEquals(KOMERecoveryStartup.State.READY, loaded.recoveryStartup.state());
            NBTTagCompound saved = KOMERecoveryRootCrashTest.save(loaded);
            assertEquals(12, saved.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
            assertEquals(3, saved.getInteger(KOMEConflictPersistence.SCHEMA_KEY));
            for (String key : new String[]{KOMEConflictPersistence.RECOVERY_KEY, KOMEConflictPersistence.RECORDS_KEY,
                    KOMEConflictPersistence.SEQUENCE_KEY, KOMEConflictPersistence.JOIN_BATTLE_SEQUENCE_KEY,
                    KOMEConflictPersistence.JOIN_BATTLE_RECEIPTS_KEY, "HiredUnits", "ArmyCompanies", "ArmyMovements"})
                assertEquals(key, old.getTag(key), saved.getTag(key));
            assertTrue(saved.hasKey("SeasonReset", 10)); assertTrue(saved.hasKey("DailyJournal", 10));
            assertEquals(0, loaded.recoveryStartup.journal().lastSequence);
        });
    }

    @Test public void devRootTwelveV2RetainsGovernanceResetAndDailyWhileAddingOnlyIncompleteRecovery() {
        KOMEOrdinaryBattleRecoveryFoundationTest.Fixture f = new KOMEOrdinaryBattleRecoveryFoundationTest.Fixture(true);
        f.data.initializeIntegratedWorld();
        KOMEPlayerGovernance g = new KOMEPlayerGovernance(f.data.armyCompanies.get("C1").owner, 1, "W1", "gondor", "",
            KOMEPlayerGovernance.State.SUBMITTED, 1, 1, "retained defeat");
        f.data.playerGovernance.put(g.key(), g);
        f.data.dailyJournal.anchor = f.data.dailyJournal.boundary = 100;
        f.data.dailyJournal.nextStage = 2; f.data.dailyJournal.status = "RUNNING";
        NBTTagCompound old = KOMERecoveryRootCrashTest.save(f.data);
        old.setInteger(KOMEConflictPersistence.SCHEMA_KEY, 2); old.removeTag(KOMEConflictPersistence.RECOVERY_KEY);
        KOMEWorldData loaded = new KOMEWorldData(); loaded.readFromNBT(old);
        NBTTagCompound saved = KOMERecoveryRootCrashTest.save(loaded);
        for (String key : new String[]{"SeasonReset", "GovernanceSchema", "PlayerGovernance", "DailyJournal",
                KOMEConflictPersistence.RECORDS_KEY, KOMEConflictPersistence.JOIN_BATTLE_RECEIPTS_KEY,
                KOMEConflictPersistence.JOIN_BATTLE_SEQUENCE_KEY, KOMEConflictPersistence.SEQUENCE_KEY})
            assertEquals(key, old.getTag(key), saved.getTag(key));
        KOMEOrdinaryBattleRecoveryRegistry.Episode episode = loaded.getOrdinaryBattleRecovery().get("CF1", "CF1:E1");
        assertEquals(KOMEOrdinaryBattleRecoveryRegistry.Coverage.LEGACY_INCOMPLETE, episode.coverage);
        assertTrue(episode.participants.isEmpty()); assertTrue(episode.grants.isEmpty());
        assertFalse(episode.actualHostileCombatObserved);
    }

    @Test public void mountedHealthAcknowledgmentAndResetReceiptIsolationCoexist() throws Exception {
        KOMEAccessFixture f = new KOMEAccessFixture();
        EntityPig rider = new EntityPig(f.world), mount = new EntityPig(f.world);
        rider.setHealth(7.5F); mount.setHealth(3.25F); rider.mountEntity(mount);
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord(); unit.entity = rider.getUniqueID(); unit.owner = UUID.randomUUID();
        unit.mounted = true; KOMEHiredUnitClassification.assignForCampaignWorkflow(unit); f.data.hiredUnits.put(unit.entity, unit);
        unit.stationedEntityData = KOMEEntitySnapshots.snapshot(rider);
        unit.movingEntityData = KOMEEntitySnapshots.snapshot(unit, rider);
        NBTTagCompound before = (NBTTagCompound) unit.movingEntityData.copy();
        assertEquals(KOMECampaignHealthEntity.projection(rider).write(), before.getCompoundTag(KOMECampaignHealthProjection.ENTITY_TAG));
        assertEquals(KOMECampaignHealthEntity.projection(mount).write(), before.getCompoundTag("Riding").getCompoundTag(KOMECampaignHealthProjection.ENTITY_TAG));
        rider.getEntityData().setString(KOMESeasonResetDeployment.RECEIPT, "1:C1");
        mount.getEntityData().setString(KOMESeasonResetDeployment.RECEIPT, "1:C1");
        assertEquals(before, unit.movingEntityData);
        rider.setHealth(2.125F); mount.setHealth(1.625F); KOMECampaignHealth.refresh(unit);
        NBTTagCompound health = (NBTTagCompound) unit.survivingHealth.copy();
        EntityLivingBase restored = (EntityLivingBase) KOMESeasonResetDeployment.restoreVirtualUnit(unit, f.world);
        assertEquals(unit.entity, restored.getUniqueID()); assertEquals(mount.getUniqueID(), restored.ridingEntity.getUniqueID());
        assertEquals(2.125F, restored.getHealth(), 0F); assertEquals(1.625F, ((EntityLivingBase)restored.ridingEntity).getHealth(), 0F);
        assertEquals(before, unit.movingEntityData); assertEquals(health, unit.survivingHealth);
    }

    @Test public void dailyAndResetCheckpointsUseRootStorageEvenWithSeparateDimensionStorage() throws Exception {
        File directory = files.newFolder();
        KOMEWorldData initial = new KOMEWorldData(); initial.initializeIntegratedWorld();
        KOMERecoveryRootCrashTest.writeRoot(directory, KOMERecoveryRootCrashTest.save(initial));
        KOMERecoveryRootCrashTest.load(directory, data -> {
            WorldServer root = MinecraftServer.getServer().worldServers[0];
            WorldServer other = KOMEAccessFixture.allocate(WorldServer.class);
            WorldProviderSurface provider = new WorldProviderSurface(); provider.dimensionId = 7;
            Field field = World.class.getDeclaredField("provider"); field.setAccessible(true); field.set(other, provider);
            other.mapStorage = new MapStorage(null);
            Field worlds = DimensionManager.class.getDeclaredField("worlds"); worlds.setAccessible(true); Object previous = worlds.get(null);
            Hashtable<Integer,WorldServer> registered = new Hashtable<Integer,WorldServer>(); registered.put(0, root); registered.put(7, other);
            worlds.set(null, registered);
            try {
                data.dailyJournal.lastObserved = 123;
                KOMEWorldCheckpoint.forWorld(data, other).save(data);
                assertEquals(123, savedRoot(directory).getCompoundTag("DailyJournal").getLong("LastObserved"));
                data.dailyJournal.lastObserved = 456; data.checkpointReset(other);
                assertEquals(456, savedRoot(directory).getCompoundTag("DailyJournal").getLong("LastObserved"));
                assertNull(other.mapStorage.loadData(KOMEWorldData.class, KOMEWorldData.DATA_NAME));
            } finally { worlds.set(null, previous); }
        });
    }
    private static NBTTagCompound savedRoot(File directory) throws Exception {
        try (InputStream in = new FileInputStream(new File(directory, "data/KOME_ServerRules.dat"))) {
            return CompressedStreamTools.readCompressed(in).getCompoundTag("data");
        }
    }
}
