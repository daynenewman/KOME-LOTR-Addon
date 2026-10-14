package kome.common.data;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import kome.common.KOMEAccessFixture;
import kome.common.KOMETestServerSession;
import net.minecraft.nbt.*;
import net.minecraft.world.*;
import net.minecraft.world.storage.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static kome.common.data.KOMERecoveryJournalRecord.*;
import static kome.common.data.KOMERecoveryJournalTestSupport.*;
import static kome.common.data.KOMEConflictContracts.*;

/** Actual root NBT -> MapStorage -> root-load callback, with an independently forced filesystem WAL. */
public class KOMERecoveryRootCrashTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Rule public KOMETileTestResources geometry=new KOMETileTestResources();
    private Field session;
    private Object previousSession;
    private KOMEPopulationTestConfig config;
    @Before public void isolate() throws Exception {
        session=KOMERecoveryStartup.class.getDeclaredField("session"); session.setAccessible(true); previousSession=session.get(null); session.set(null,null);
        config=new KOMEPopulationTestConfig();
    }
    @After public void restore() throws Exception { KOMERecoveryStartup.stopSession(); session.set(null,previousSession); config.close(); }

    static NBTTagCompound save(KOMEWorldData data) { NBTTagCompound n=new NBTTagCompound(); data.writeToNBT(n); return n; }
    static void writeRoot(File directory,NBTTagCompound payload) throws Exception {
        File folder=new File(directory,"data"); folder.mkdirs();
        NBTTagCompound root=new NBTTagCompound(); root.setTag("data",payload);
        try(OutputStream out=new FileOutputStream(new File(folder,"KOME_ServerRules.dat"))) { CompressedStreamTools.writeCompressed(root,out); }
    }
    interface LoadedCheck { void check(KOMEWorldData data) throws Exception; }
    static void load(File directory,LoadedCheck check) throws Exception {
        try(KOMETestServerSession server=new KOMETestServerSession()) {
            WorldServer world=KOMEAccessFixture.allocate(WorldServer.class);
            Field provider=World.class.getDeclaredField("provider"); provider.setAccessible(true); provider.set(world,new WorldProviderSurface());
            ISaveHandler handler=(ISaveHandler)Proxy.newProxyInstance(KOMERecoveryRootCrashTest.class.getClassLoader(),new Class<?>[]{ISaveHandler.class},(proxy,method,args)-> {
                if(method.getName().equals("getWorldDirectory")) return directory;
                if(method.getName().equals("getMapFileFromName")) return new File(new File(directory,"data"),args[0]+".dat");
                return null;
            });
            Field saveHandler=World.class.getDeclaredField("saveHandler"); saveHandler.setAccessible(true); saveHandler.set(world,handler);
            world.mapStorage=new MapStorage(handler); server.server.worldServers=new WorldServer[]{world};
            KOMERecoveryStartup.beginSession(server.server);
            try { new KOMEEvents().onRecoveryRootLoad(new net.minecraftforge.event.world.WorldEvent.Load(world)); check.check(KOMEWorldData.get(world)); }
            finally { KOMERecoveryStartup.stopSession(); }
        }
    }
    @Test public void lineageAheadOfFullStrategicRootQuarantinesBothAliasesAndPreservesReferences() throws Exception { lineageRestart(false); }
    @Test public void completelyPublishedRekeyRestartsWithoutQuarantine() throws Exception { lineageRestart(true); }
    @Test public void lineageQuarantineFencesLaterCommitmentBeforeItsFirstEpisode() throws Exception { lineageRestart(false,true); }
    private void lineageRestart(boolean published) throws Exception { lineageRestart(published,false); }
    private void lineageRestart(boolean published,boolean laterConflict) throws Exception {
        config.set("battle.disableNormalHealingDuringCampaignCommitment","true");
        File directory=temporary.newFolder();
        KOMEConflictMovementServiceTest.Fixture f=KOMEConflictMovementServiceTest.hostileFixture(false,false);
        KOMEWorldData data=f.data; data.initializeIntegratedWorld();
        KOMEHiredUnitRecord unit=data.hiredUnits.get(data.armyCompanies.get("C1").units.get(0));
        UUID original=unit.entity, replacement=UUID.randomUUID();
        data.hiredUnits.remove(original); unit.entity=RIDER_ALIAS;
        data.armyCompanies.get("C1").units.set(0,RIDER_ALIAS); data.armyMovements.get("M1").units.set(0,RIDER_ALIAS);
        KOMECampaignHealthProjection health=new KOMECampaignHealthProjection(RIDER,Component.RIDER,RIDER_ALIAS,3.25F,4,new UUID(4,1),false,null);
        unit.survivingHealth=health.write(); data.hiredUnits.put(unit.entity,unit);
        unit.replacePhysicalLocator(KOMEHiredUnitPhysicalLocator.verified(RIDER_ALIAS,KOMETileTestResources.dimension(),
            KOMETileTestResources.x(),65,KOMETileTestResources.z(),"T100",10,KOMEHiredUnitPhysicalLocator.CaptureKind.LIVE_OBSERVATION));
        // An ended older episode is independent of the current strategic movement.
        data.getConflictService().replaceFrom(new KOMEConflictService(2));
        data.getOrdinaryBattleRecovery().replaceFrom(KOMEOrdinaryBattleRecoveryRegistry.restore(context().targets,Collections.singletonMap("CF1:E1",context().episode)));
        if(laterConflict) {
            KOMEConflictRecord next=data.getConflictService().start("T100",KOMEConflictRecord.State.ORDINARY,ExpectedConflict.absent(),Collections.emptyList(),new Context(30,"test","later conflict")).record;
            assertTrue(data.getConflictService().commit("T100",ExpectedConflict.at(next.getConflictId(),next.getRevision()),
                new CommitmentInput("C1",KOMEHiredUnitClass.CAMPAIGN,KOMEConflictRecord.EntryOrigin.LEGAL_ARRIVAL,"M1"),new Context(31,"test","later commitment")).isSuccess());
        }
        NBTTagCompound oldRoot=save(data);
        KOMERecoveryStartup startup=new KOMERecoveryStartup(net.minecraft.server.MinecraftServer.getServer());
        KOMERecoveryJournalManager manager=new KOMERecoveryJournalManager(directory,new Object(),KOMERecoveryJournalIO.FILES);
        startup.initialize(manager,oldRoot,data); session.set(null,startup);
        try {
            startup.append(context());
            KOMERecoveryStartup.stageAlias(health,replacement);
            KOMEOrdinaryBattleRecoveryService.reconstructed(data,RIDER_ALIAS,replacement,null,null);
            assertEquals(2,manager.view().lastSequence);
            if(published) {
            data.hiredUnits.remove(RIDER_ALIAS); unit.entity=replacement;
            KOMECampaignHealth.reconstructed(unit,RIDER_ALIAS,replacement,null,null); data.hiredUnits.put(replacement,unit);
            data.armyCompanies.get("C1").units.set(0,replacement); data.armyMovements.get("M1").units.set(0,replacement);
            unit.replacePhysicalLocator(KOMEHiredUnitPhysicalLocator.verified(replacement,KOMETileTestResources.dimension(),
                KOMETileTestResources.x(),65,KOMETileTestResources.z(),"T100",20,KOMEHiredUnitPhysicalLocator.CaptureKind.STRATEGIC_RECONSTRUCTION));
            }
            writeRoot(directory,published?save(data):oldRoot);
        } finally { startup.stop(); session.set(null,null); }
        load(directory,loaded->{
            assertEquals(loaded.recoveryStartup.diagnostic(),KOMERecoveryStartup.State.READY,loaded.recoveryStartup.state());
            assertEquals(published,loaded.recoveryStartup.permitsWork("CF1",RIDER));
            if(laterConflict) {
                assertEquals("CF2",loaded.getConflictService().get("T100").getConflictId());
                assertFalse(KOMERecoveryStartup.permits(loaded,loaded.getConflictService().get("T100")));
                assertEquals(oldRoot.getTag(KOMEConflictPersistence.RECORDS_KEY),save(loaded).getTag(KOMEConflictPersistence.RECORDS_KEY));
            }
            UUID strategic=published?replacement:RIDER_ALIAS;
            assertNotNull(loaded.hiredUnits.get(strategic)); assertEquals(strategic,loaded.armyCompanies.get("C1").units.get(0));
            assertEquals(strategic,loaded.armyMovements.get("M1").units.get(0));
            assertEquals(unit.getPhysicalLocator().writeToNBT(),loaded.hiredUnits.get(strategic).getPhysicalLocator().writeToNBT());
            assertEquals(replacement,loaded.getOrdinaryBattleRecovery().target(RIDER).physicalId);
            assertTrue(KOMEHiredUnitClassification.isWithinCommittedCampaignLifespan(loaded,loaded.hiredUnits.get(strategic)));
            assertSame(loaded.hiredUnits.get(strategic),loaded.campaignAssociations.owner(replacement));
            assertSame(loaded.hiredUnits.get(strategic),loaded.campaignAssociations.owner(RIDER_ALIAS));
            assertTrue(KOMECampaignHealingPolicy.blocksNormalHealing(loaded,replacement));
            assertTrue(KOMECampaignHealingPolicy.blocksNormalHealing(loaded,RIDER_ALIAS));
            if(!published) assertTrue(loaded.recoveryStartup.diagnostic().contains("LINEAGE ahead of strategic UUID publication"));
            assertEquals(3.25F,KOMECampaignHealthProjection.read(loaded.hiredUnits.get(strategic).survivingHealth).hp,0);
        });
    }

    @Test public void productionResolutionPrepareThenStaleRootRestartCannotRepublishEffects() throws Exception {
        File directory=temporary.newFolder();
        KOMEOrdinaryBattleRecoveryFoundationTest.Fixture f=new KOMEOrdinaryBattleRecoveryFoundationTest.Fixture(true);
        KOMEWorldData data=f.data; data.initializeIntegratedWorld();
        KOMEAccessFixture physical=new KOMEAccessFixture(); physical.world.mapStorage.setData("KOME_ServerRules",data);
        KOMERecoveryExecutorTest.Living rider=new KOMERecoveryExecutorTest.Living(physical.world);
        KOMEHiredUnitRecord unit=data.hiredUnits.get(f.member("C1")); KOMERecoveryExecutorTest.alias(rider,unit.entity);
        rider.setHealth(3.25F); KOMECampaignHealth.observe(unit,rider); physical.world.loadedEntityList.add(rider);
        // A genuine hostile episode already exists. The defender dies through the canonical producer.
        UUID defender=f.member("C2"); KOMEOrdinaryBattleRecoveryService.terminal(data,defender,Disposition.DEAD,120,"test irreversible death");
        KOMEConflictService.Result departure=data.getConflictService().releaseValidatedCommitment(data,
            new ValidatedDepartureRequest("T101","C2","mordor",ExpectedConflict.at(f.record().getConflictId(),f.record().getRevision())),
            new Context(121,"test","destroyed stationary defender"));
        assertTrue(departure.reason,departure.isSuccess());
        data.hiredUnits.remove(defender); data.armyCompanies.get("C2").units.clear();
        KOMEConflictRecord c=f.record();
        KOMEConflictService.Result checkpoint=data.getConflictService().checkpointCombat("T101",ExpectedConflict.at(c.getConflictId(),c.getRevision()),
            c.getCombatEpisode(),new KOMEConflictRecord.TimerSlot(KOMEConflictRecord.TimerStatus.COMPLETE,60000,60000,130L,c.getCombatEpisode().episodeId),
            KOMEConflictRecord.TimerSlot.unstarted(),new Context(130,"test","elapsed response"));
        assertTrue(checkpoint.reason,checkpoint.isSuccess());
        KOMEConflictMovementService.applyConflictHold(data,data.armyMovements.get("M1"),f.record(),130);
        data.getJoinBattleDeploymentReceipts().publishNew(KOMEJoinBattleDeploymentReceipt.builder()
            .receiptId("JB1").actionToken("stale-root-join").playerId(UUID.randomUUID()).conflictId("CF1").tileId("T101")
            .acceptedConflictRevision(f.record().getRevision()).factionId("gondor").selectedCompanyId("C1")
            .createdAtMillis(130).updatedAtMillis(130).returnAnchor(new KOMEJoinBattleDeploymentReceipt.Pose(0,1,65,1,0,0))
            .participationRecovery(KOMEJoinBattleDeploymentReceipt.ParticipationRecovery.REGISTRATION_REQUIRED).build());
        NBTTagCompound stale=save(data); writeRoot(directory,stale);
        KOMERecoveryStartup startup=new KOMERecoveryStartup(net.minecraft.server.MinecraftServer.getServer());
        KOMERecoveryJournalManager manager=new KOMERecoveryJournalManager(directory,new Object(),KOMERecoveryJournalIO.FILES);
        startup.initialize(manager,stale,data);
        try {
            assertEquals(KOMERecoveryStartup.State.READY,startup.state());
            KOMEOrdinaryBattleResolutionService.Result resolution=KOMEOrdinaryBattleResolutionService.INSTANCE.resolve(data,"T101",200,200);
            assertTrue(resolution.reason,resolution.resolved());
            assertEquals("gondor",data.getConquestTileIfPresent("T101").projectRulingFaction());
            UUID logical=data.getOrdinaryBattleRecovery().targetForPhysical(unit.entity).id;
            KOMERecoveryReservations.Result prepared=KOMERecoveryReservations.prepare(data,"CF1","CF1:E1",Component.RIDER,logical,rider);
            assertEquals(KOMERecoveryReservations.Status.RESERVED,prepared.status);
            assertEquals(2,manager.view().lastSequence); assertEquals(3.25F,rider.getHealth(),0);
        } finally { startup.stop(); }
        load(directory,loaded->{
            assertEquals(loaded.recoveryStartup.diagnostic(),KOMERecoveryStartup.State.READY,loaded.recoveryStartup.state());
            assertTrue(loaded.recoveryStartup.diagnostic().contains("quarantined conflicts=[CF1]"));
            assertFalse(loaded.recoveryStartup.permitsWork("CF1",null));
            NBTTagCompound before=save(loaded);
            assertEquals(0,KOMERecoveryExecutor.processCampaignTick(loaded));
            assertFalse(KOMEOrdinaryBattleResolutionService.INSTANCE.resolve(loaded,"T101",300,300).resolved());
            assertEquals(before,save(loaded));
            assertEquals("mordor",loaded.getConquestTileIfPresent("T101").projectRulingFaction());
            for(String key:new String[]{"Wars","ArmyMovements",KOMEConflictPersistence.JOIN_BATTLE_RECEIPTS_KEY,KOMEConflictPersistence.JOIN_BATTLE_SEQUENCE_KEY})
                assertEquals(key,stale.getTag(key),before.getTag(key));
            assertEquals(3.25F,KOMECampaignHealthProjection.read(loaded.hiredUnits.get(unit.entity).survivingHealth).hp,0);
            assertEquals(2,loaded.recoveryStartup.journal().lastSequence);
        });
    }
}
