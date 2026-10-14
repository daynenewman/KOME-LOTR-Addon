package kome.common.data;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import net.minecraft.nbt.*;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import kome.common.KOMEAccessFixture;
import static org.junit.Assert.*;
import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;

/** Full root persistence/startup gates, then the same campaign tick entry used by KOMEEvents. */
public class KOMEOrdinaryBattleRestartTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Rule public KOMETileTestResources geometry=new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    private Field session;
    private Object previousSession;
    private static final long EPOCH=1_790_000_000_000L;
    private final Map<KOMEWorldData,KOMEPopulationPayoutRuntime> runtimes=new IdentityHashMap<>();
    private final List<String> messages=new ArrayList<String>();
    @Before public void setup() throws Exception {
        config=new KOMEPopulationTestConfig();
        session=KOMERecoveryStartup.class.getDeclaredField("session");session.setAccessible(true);
        previousSession=session.get(null);session.set(null,null);
        KOMENotificationService.setMinecraftBroadcaster(messages::add);
        KOMEOrdinaryBattleLifecycleService.INSTANCE.resetRuntimeState();
    }
    @After public void cleanup() throws Exception {
        KOMERecoveryStartup.stopSession();session.set(null,previousSession);
        KOMEOrdinaryBattleLifecycleService.INSTANCE.resetRuntimeState();
        KOMENotificationService.resetMinecraftBroadcaster();config.close();
    }
    private KOMEWorldData partial(boolean defender) throws Exception {
        KOMEOrdinaryBattleRecoveryFoundationTest.Fixture f=new KOMEOrdinaryBattleRecoveryFoundationTest.Fixture(defender);
        f.data.initializeIntegratedWorld();
        KOMEConflictMovementService.applyConflictHold(f.data,f.data.armyMovements.get("M1"),f.record(),110);
        tick(f.data,200);tick(f.data,20_200);
        assertEquals(20_000,record(f.data).getResponseTimer().elapsedMillis);
        return f.data;
    }
    private void tick(KOMEWorldData data,long now) throws Exception {
        now+=EPOCH;
        KOMEAccessFixture physical=new KOMEAccessFixture();physical.world.mapStorage.setData("KOME_ServerRules",data);
        Field info=World.class.getDeclaredField("worldInfo");info.setAccessible(true);info.set(physical.world,new WorldInfo(new NBTTagCompound()));
        physical.world.testWorldTime=now;
        KOMEPopulationPayoutRuntime runtime=runtimes.computeIfAbsent(data,ignored->new KOMEPopulationPayoutRuntime());
        runtime.onStartup(data,java.time.Instant.ofEpochMilli(now));
        KOMEEvents.processCampaignTick(data,physical.world,now,runtime);
    }
    private static KOMEConflictRecord record(KOMEWorldData data){return data.getConflictService().get("T101");}
    private File persist(KOMEWorldData data) throws Exception {
        File directory=temporary.newFolder();
        try(KOMERecoveryJournalManager journal=new KOMERecoveryJournalManager(directory,new Object(),KOMERecoveryJournalIO.FILES)){journal.bootstrap();}
        KOMERecoveryRootCrashTest.writeRoot(directory,KOMERecoveryRootCrashTest.save(data));
        return directory;
    }
    private void restart(File directory,KOMERecoveryRootCrashTest.LoadedCheck check) throws Exception {
        KOMEOrdinaryBattleLifecycleService.INSTANCE.resetRuntimeState();
        KOMERecoveryRootCrashTest.load(directory,check);
    }
    private void noRestartAnnouncement(){assertFalse(messages.toString(),messages.stream().anyMatch(x->x.contains("began response episode")));}
    @Test public void activeTimerResumesRemainingServerTimeAndPublishesClosureExactlyOnce() throws Exception {
        File directory=persist(partial(false));
        restart(directory,data->{
            assertTrue(data.recoveryStartup.diagnostic(),KOMERecoveryStartup.permits(data,record(data)));
            tick(data,1_000_000);assertEquals(20_000,record(data).getResponseTimer().elapsedMillis);
            tick(data,1_005_000);assertEquals(25_000,record(data).getResponseTimer().elapsedMillis);
            tick(data,1_039_999);assertEquals(TimerStatus.RUNNING,record(data).getResponseTimer().status);
            assertEquals("mordor",data.getConquestTileIfPresent("T101").projectRulingFaction());
            tick(data,1_040_000);assertEquals(TimerStatus.COMPLETE,record(data).getResponseTimer().status);
            assertEquals("CF1:E1",record(data).getCombatEpisode().episodeId);
            assertEquals(State.ENDED,record(data).getState());assertEquals("gondor",data.getConquestTileIfPresent("T101").projectRulingFaction());
            assertEquals(KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED,data.armyMovements.get("M1").status);
            assertEquals(1,data.wars.values().stream().mapToInt(war->war.tileCaptureHistory.size()).sum());
            assertEquals(1,messages.stream().filter(x->x.contains("on T101 resolved.")).count());
            NBTTagCompound completed=KOMERecoveryRootCrashTest.save(data);
            tick(data,1_050_000);
            NBTTagCompound repeated=KOMERecoveryRootCrashTest.save(data);
            for(String key:new String[]{"Wars","ArmyMovements",KOMEConflictPersistence.RECORDS_KEY})assertEquals(key,completed.getTag(key),repeated.getTag(key));
            KOMERecoveryRootCrashTest.writeRoot(directory,completed);noRestartAnnouncement();
        });
        restart(directory,data->{tick(data,2_000_000);tick(data,2_010_000);
            assertEquals(State.ENDED,record(data).getState());assertEquals(1,record(data).getCombatEpisode().sequence);
            assertEquals(1,data.wars.values().stream().mapToInt(war->war.tileCaptureHistory.size()).sum());
            assertEquals(1,messages.stream().filter(x->x.contains("on T101 resolved.")).count());noRestartAnnouncement();});
    }
    @Test public void completeUnresolvedHostileEpisodeSurvivesRestart() throws Exception {
        KOMEWorldData data=partial(true);tick(data,60_200);assertEquals(TimerStatus.COMPLETE,record(data).getResponseTimer().status);
        File directory=persist(data);
        restart(directory,loaded->{long revision=record(loaded).getRevision();tick(loaded,1_000_000);tick(loaded,1_060_000);
            assertEquals(State.ORDINARY,record(loaded).getState());assertEquals(TimerStatus.COMPLETE,record(loaded).getResponseTimer().status);
            assertEquals(revision,record(loaded).getRevision());assertEquals("CF1:E1",record(loaded).getCombatEpisode().episodeId);noRestartAnnouncement();});
    }
    @Test public void absentObjectiveStopsExistingEpisodeWithoutCountingOfflineTime() throws Exception {
        KOMEWorldData data=partial(false);
        // An independently published owner change removes the offensive objective contest.
        data.getConquestTileIfPresent("T101").setCurrentRulingFaction("gondor");
        File directory=persist(data);
        restart(directory,loaded->{tick(loaded,1_000_000);assertEquals(EpisodeState.INACTIVE,record(loaded).getCombatEpisode().state);
            assertEquals(20_000,record(loaded).getResponseTimer().elapsedMillis);tick(loaded,1_060_000);
            assertEquals("CF1:E1",record(loaded).getCombatEpisode().episodeId);noRestartAnnouncement();});
    }
    @Test public void repeatedRestartsRetainOneEpisodeAndExcludeEachOfflineInterval() throws Exception {
        File directory=persist(partial(false));
        for(int i=1;i<=3;i++){final int n=i;restart(directory,data->{long now=n*1_000_000L;
            tick(data,now);assertEquals(20_000+(n-1)*5_000,record(data).getResponseTimer().elapsedMillis);
            tick(data,now+5_000);assertEquals(20_000+n*5_000,record(data).getResponseTimer().elapsedMillis);
            assertEquals("CF1:E1",record(data).getCombatEpisode().episodeId);noRestartAnnouncement();
            KOMERecoveryRootCrashTest.writeRoot(directory,KOMERecoveryRootCrashTest.save(data));});}
    }
    @Test public void conflictingUnprovenSourceAliasQuarantinesAndExplainsTimerStall() throws Exception {
        KOMEWorldData data=partial(false);KOMEHiredUnitRecord unit=data.hiredUnits.values().iterator().next();
        UUID logical=data.getOrdinaryBattleRecovery().targetForPhysical(unit.entity).id;
        KOMECampaignHealthProjection current=new KOMECampaignHealthProjection(logical,KOMEOrdinaryBattleRecoveryRegistry.Component.RIDER,unit.entity,20,0,UUID.randomUUID(),false,null);
        unit.survivingHealth=current.write();File directory=persist(data);
        File regionDirectory=new File(directory,"MiddleEarth/region");assertTrue(regionDirectory.mkdirs());
        KOMECampaignHealthProjection old=new KOMECampaignHealthProjection(logical,current.component,UUID.randomUUID(),20,0,current.transition,false,null);
        NBTTagCompound entity=new NBTTagCompound();entity.setLong("UUIDMost",old.alias.getMostSignificantBits());entity.setLong("UUIDLeast",old.alias.getLeastSignificantBits());entity.setFloat("HealF",old.hp);entity.setTag(KOMECampaignHealthProjection.ENTITY_TAG,old.write());
        NBTTagList entities=new NBTTagList();entities.appendTag(entity);NBTTagCompound chunk=new NBTTagCompound();chunk.setTag("Entities",entities);
        net.minecraft.world.chunk.storage.RegionFile region=new net.minecraft.world.chunk.storage.RegionFile(new File(regionDirectory,"r.0.0.mca"));
        try(DataOutputStream out=region.getChunkDataOutputStream(0,0)){CompressedStreamTools.write(chunk,out);}finally{region.close();}
        restart(directory,loaded->{assertEquals(KOMERecoveryStartup.State.READY,loaded.recoveryStartup.state());
            assertFalse(KOMERecoveryStartup.permits(loaded,record(loaded)));tick(loaded,1_000_000);tick(loaded,1_060_000);
            assertEquals(20_000,record(loaded).getResponseTimer().elapsedMillis);
            assertTrue(KOMEConflictLifecycleService.INSTANCE.inspectionLines(loaded,"T101").toString().contains("Ordinary lifecycle blocked:"));
            assertTrue(loaded.recoveryStartup.diagnostic().contains("Health identity mismatch"));noRestartAnnouncement();});
    }
}
