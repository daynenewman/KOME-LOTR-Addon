package kome.common.data;

import java.util.*;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.*;
import org.junit.Test;
import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static kome.common.data.KOMEOrdinaryBattleRecoveryRegistry.*;
import static org.junit.Assert.*;

public class KOMEOrdinaryBattleRecoveryFoundationTest {
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private static final String TILE = "T101";
    private static final UUID RIDER = new UUID(8,1), MOUNT = new UUID(8,2);

    @Test public void v2MigrationPreservesRecordsReceiptsAndBothAllocatorsExactly() {
        Fixture f = new Fixture(true);
        NBTTagCompound mount = new NBTTagCompound(); mount.setString("id","Horse"); mount.setFloat("HealF",13.5F);
        KOMEJoinBattleDeploymentRegistry receipts = f.data.getJoinBattleDeploymentReceipts();
        receipts.publishNew(KOMEJoinBattleDeploymentReceipt.builder().receiptId("JB1").actionToken("migration-mounted")
            .playerId(new UUID(9,1)).conflictId(f.record().getConflictId()).tileId(TILE).acceptedConflictRevision(1)
            .factionId("gondor").selectedCompanyId("C1").createdAtMillis(10).updatedAtMillis(10)
            .returnAnchor(new KOMEJoinBattleDeploymentReceipt.Pose(0,1,65,1,0,0))
            .participationRecovery(KOMEJoinBattleDeploymentReceipt.ParticipationRecovery.REGISTRATION_REQUIRED)
            .enteredMounted(true).mountUuid(MOUNT).mountEntityType("Horse")
            .mountProfile(KOMEJoinBattleDeploymentReceipt.MountProfile.VANILLA_HORSE)
            .mountSourceAnchor(new KOMEJoinBattleDeploymentReceipt.Pose(0,1,65,1,0,0))
            .mountTransferPhase(KOMEJoinBattleDeploymentReceipt.MountTransferPhase.SOURCE_SNAPSHOT_PERSISTED)
            .temporaryMountNbt(mount).build());
        NBTTagCompound old = section(f.data); old.setInteger(KOMEConflictPersistence.SCHEMA_KEY,2);
        old.removeTag(KOMEConflictPersistence.RECOVERY_KEY);
        KOMEConflictPersistence.Loaded loaded = KOMEConflictPersistence.readSection(old);
        NBTTagCompound upgraded = KOMEConflictPersistence.write(loaded.conflicts,loaded.joinBattleReceipts,loaded.recovery);
        assertTrue(loaded.migrated); assertFalse(loaded.migratedFromV1);
        for(String key:new String[]{KOMEConflictPersistence.SEQUENCE_KEY,KOMEConflictPersistence.RECORDS_KEY,
                KOMEConflictPersistence.JOIN_BATTLE_SEQUENCE_KEY,KOMEConflictPersistence.JOIN_BATTLE_RECEIPTS_KEY})
            assertEquals(key,old.getTag(key),upgraded.getTag(key));
        Episode marker=loaded.recovery.get("CF1","CF1:E1");
        assertEquals(Coverage.LEGACY_INCOMPLETE,marker.coverage);
        assertTrue(marker.participants.isEmpty()); assertTrue(marker.grants.isEmpty()); assertFalse(marker.actualHostileCombatObserved);
        assertEquals(3,upgraded.getInteger(KOMEConflictPersistence.SCHEMA_KEY));
        assertEquals(11,KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        assertEquals(1,KOMEHiredUnitPhysicalLocator.DATA_SCHEMA_VERSION);
    }
    @Test public void v1MigrationChainAndEndedV2DoNotInventHistory() {
        Fixture f=new Fixture(false); NBTTagCompound old=section(f.data);
        old.setInteger(KOMEConflictPersistence.SCHEMA_KEY,1); old.removeTag(KOMEConflictPersistence.RECOVERY_KEY);
        old.removeTag(KOMEConflictPersistence.JOIN_BATTLE_SEQUENCE_KEY); old.removeTag(KOMEConflictPersistence.JOIN_BATTLE_RECEIPTS_KEY);
        KOMEConflictPersistence.Loaded loaded=KOMEConflictPersistence.readSection(old);
        assertTrue(loaded.migratedFromV1); assertTrue(loaded.migrated);
        assertEquals(1,loaded.joinBattleReceipts.getNextReceiptSequence());
        assertEquals(1,loaded.recovery.episodes().size());
        ok(f.data.getConflictService().end(TILE,expected(f.record()),context(200)));
        old=section(f.data); old.setInteger(KOMEConflictPersistence.SCHEMA_KEY,2); old.removeTag(KOMEConflictPersistence.RECOVERY_KEY);
        assertTrue(KOMEConflictPersistence.readSection(old).recovery.episodes().isEmpty());
    }
    @Test public void migrationMarksWorldDirtyAndDoesNotReadCompanyMembershipAsHistory() {
        Fixture f=new Fixture(true); f.data.initializeIntegratedWorld(); NBTTagCompound old=save(f.data);
        old.setInteger(KOMEConflictPersistence.SCHEMA_KEY,2); old.removeTag(KOMEConflictPersistence.RECOVERY_KEY);
        KOMEWorldData loaded=new KOMEWorldData("migrated-recovery"); loaded.readFromNBT(old);
        assertTrue(loaded.isDirty()); assertEquals(11,save(loaded).getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertTrue(loaded.getOrdinaryBattleRecovery().get("CF1","CF1:E1").participants.isEmpty());
    }
    @Test public void episodeStartEnrollsOnlyCommittedCampaignAndLatchesActualHostilePair() {
        Fixture f=new Fixture(true); Episode e=f.episode();
        assertEquals(Coverage.COMPLETE,e.coverage); assertEquals(2,e.participants.size()); assertTrue(e.actualHostileCombatObserved);
        UUID unrelated=f.f.company("C9","gondor","T100").units.get(0);
        KOMEOrdinaryBattleRecoveryService.observe(f.data,f.record(),110);
        assertNull(f.registry().targetForPhysical(unrelated)); assertEquals(2,f.episode().participants.size());
        for(Participant p:e.participants.values()) assertEquals(Disposition.PARTICIPATING,p.disposition);
    }
    @Test public void unopposedContestNeverBecomesCombatEvidenceOrGrants() {
        Fixture f=new Fixture(false); assertFalse(f.episode().actualHostileCombatObserved);
        assertEquals(Coverage.COMPLETE,f.episode().coverage);
        f.registry().end(f.record(),200); f.registry().prepareGrants("CF1","CF1:E1",5);
        assertTrue(f.episode().grants.isEmpty());
    }
    @Test public void hostileEvidenceSurvivesLaterDiplomacyAndRestart() {
        Fixture f=new Fixture(true);
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction("gondor"),KOMEAlliance.findLotrFaction("mordor"),LOTRFactionRelations.Relation.ALLY);
        KOMEOrdinaryBattleRecoveryService.observe(f.data,f.record(),120);
        assertTrue(f.episode().actualHostileCombatObserved);
        assertTrue(KOMEOrdinaryBattleRecoveryRegistry.read(f.registry().write()).get("CF1","CF1:E1").actualHostileCombatObserved);
    }
    @Test public void midEpisodeLegalArrivalEnrollsOnceWithoutDuplicatingReinforcements() {
        Fixture f=new Fixture(false);
        KOMEConflictMovementService.ArrivalPreparation admission=f.f.joining("C3","gondor","T100","M3",150).preparation;
        assertTrue(admission.reason,admission.ready());
        f.f.publish("M3",TILE); f.f.commit(admission);
        assertEquals(2,f.episode().participants.size());
        KOMEOrdinaryBattleRecoveryService.observe(f.data,f.record(),160);
        assertEquals(2,f.episode().participants.size()); assertFalse(f.episode().actualHostileCombatObserved);
    }
    @Test public void formalRetreatCapturesEvidenceBeforeCommitmentRemovalAndSurvivesCleanup() {
        Fixture f=new Fixture(true); UUID rider=f.member("C1");
        // Clear only the latch in a detached valid test fixture to exercise the departure observation.
        NBTTagCompound saved=f.registry().write(); saved.getTagList("Episodes",10).getCompoundTagAt(0).setBoolean("ActualHostileCombatObserved",false);
        f.registry().replaceFrom(KOMEOrdinaryBattleRecoveryRegistry.read(saved));
        KOMEConflictMovementService.applyConflictHold(f.data,f.data.armyMovements.get("M1"),f.record(),110);
        KOMEConflictMovementHandoff.Result released=KOMEConflictMovementHandoff.releaseCommitment(f.data,
            new ValidatedDepartureRequest(TILE,"C1","gondor",expected(f.record())),"M1",
            KOMEConflictMovementHandoff.Outcome.FORMAL_RETREAT,context(120));
        assertTrue(released.reason,released.accepted());
        assertFalse(f.record().getCommitments().containsKey("C1"));
        Participant p=f.episode().participants.get(f.registry().targetForPhysical(rider).id);
        assertEquals(Disposition.FORMAL_RETREAT,p.disposition); assertEquals(Long.valueOf(120),p.exitedAt);
        assertTrue(f.episode().actualHostileCombatObserved);
        f.data.armyCompanies.remove("C1"); f.data.armyMovements.remove("M1");
        Episode reloaded=KOMEOrdinaryBattleRecoveryRegistry.read(f.registry().write()).get("CF1","CF1:E1");
        assertEquals(Disposition.FORMAL_RETREAT,reloaded.participants.get(p.id).disposition);
    }
    @Test public void deadAndDestroyedDispositionsRemainAfterUnitRemoval() {
        Fixture f=new Fixture(true); UUID dead=f.member("C1"), destroyed=f.member("C2");
        KOMEOrdinaryBattleRecoveryService.terminal(f.data,dead,Disposition.DEAD,120,"confirmed death");
        KOMEOrdinaryBattleRecoveryService.terminal(f.data,destroyed,Disposition.DESTROYED,121,"destruction");
        f.data.hiredUnits.remove(dead); f.data.hiredUnits.remove(destroyed);
        f.registry().end(f.record(),130); f.registry().prepareGrants("CF1","CF1:E1",5);
        assertTrue(f.episode().grants.isEmpty());
        assertEquals(Disposition.DEAD,f.episode().participants.get(f.registry().targetForPhysical(dead).id).disposition);
        assertEquals(Disposition.DESTROYED,f.episode().participants.get(f.registry().targetForPhysical(destroyed).id).disposition);
    }
    @Test public void verifiedRiderAndMountReconstructionPreservesLogicalIdentity() {
        KOMEConflictRecord c=record(); KOMEOrdinaryBattleRecoveryRegistry r=registry(c,true);
        UUID riderId=r.targetForPhysical(RIDER).id,mountId=r.targetForPhysical(MOUNT).id;
        UUID newRider=new UUID(8,3),newMount=new UUID(8,4);
        assertTrue(r.rekeyVerified(RIDER,newRider,Component.RIDER)); assertTrue(r.rekeyVerified(MOUNT,newMount,Component.MOUNT));
        r.enroll(c,newRider,"C2","gondor",110,newMount);
        assertEquals(1,r.get("CF1","CF1:E1").participants.size());
        r=KOMEOrdinaryBattleRecoveryRegistry.read(r.write());
        assertEquals(riderId,r.targetForPhysical(newRider).id); assertEquals(mountId,r.targetForPhysical(newMount).id);
        assertEquals(newRider,r.target(riderId).physicalId); assertEquals(2,r.target(mountId).aliases.size());
    }
    @Test public void rekeyRejectsCollisionsAndObsoleteAliases() {
        KOMEConflictRecord c=record(); KOMEOrdinaryBattleRecoveryRegistry r=registry(c,true);
        assertThrows(IllegalArgumentException.class,()->r.rekeyVerified(RIDER,MOUNT,Component.RIDER));
        UUID next=new UUID(8,3); r.rekeyVerified(RIDER,next,Component.RIDER);
        assertThrows(IllegalArgumentException.class,()->r.rekeyVerified(RIDER,new UUID(8,4),Component.RIDER));
    }
    @Test public void riderGrantAndSharedMountGrantAreUniqueAndPendingAcrossRestart() {
        KOMEConflictRecord c=record(); KOMEOrdinaryBattleRecoveryRegistry r=registry(c,true);
        r.enroll(c,new UUID(8,3),"C2","gondor",110,MOUNT);
        r.end(c,120); r.prepareGrants("CF1","CF1:E1",5); r.prepareGrants("CF1","CF1:E1",5);
        Episode e=KOMEOrdinaryBattleRecoveryRegistry.read(r.write()).get("CF1","CF1:E1");
        assertEquals(3,e.grants.size());
        for(Grant g:e.grants.values()) { assertEquals(GrantState.PENDING,g.state); assertNull(g.transition); assertEquals(5,g.amount,0); }
    }
    @Test public void unrelatedReplacementMountCannotInheritAnEarlierGrant() {
        KOMEConflictRecord c=record(); KOMEOrdinaryBattleRecoveryRegistry r=registry(c,true);
        UUID oldMountId=r.targetForPhysical(MOUNT).id;
        r.end(c,120); r.prepareGrants("CF1","CF1:E1",5);
        assertFalse(r.enroll(c,RIDER,"C1","gondor",130,new UUID(8,99)));
        assertNull(r.targetForPhysical(new UUID(8,99)));
        assertTrue(r.get("CF1","CF1:E1").grants.containsKey("MOUNT:"+oldMountId));
    }
    @Test public void deadMountIsTerminalAndNeverGetsAGrant() {
        KOMEConflictRecord c=record(); KOMEOrdinaryBattleRecoveryRegistry r=registry(c,true);
        r.terminalize(MOUNT,Disposition.DEAD,110,"mount death"); r.end(c,120); r.prepareGrants("CF1","CF1:E1",5);
        assertEquals(1,r.get("CF1","CF1:E1").grants.size());
    }
    @Test public void closureAndReplacementRetainOldObligations() {
        Fixture f=new Fixture(true);
        KOMEConflictService.EndResult ended=f.data.getConflictService().endWithMovementHandoff(f.data,TILE,expected(f.record()),context(200),KOMEConflictService.EndSource.LIFECYCLE);
        assertTrue(ended.conflictResult.reason,ended.isSuccess());
        assertEquals(2,f.episode().grants.size());
        ok(f.data.getConflictService().start(TILE,State.ORDINARY,expected(f.record()),Collections.<GarrisonSeed>emptyList(),context(210)));
        assertEquals("CF2",f.record().getConflictId());
        KOMEConflictPersistence.Loaded restored=KOMEConflictPersistence.readSection(section(f.data));
        assertEquals(2,restored.recovery.get("CF1","CF1:E1").grants.size());
    }
    @Test public void genuineHostileAbandonmentBindsNoTransferIntentAtCanonicalEnd() {
        Fixture f=new Fixture(true); String owner=f.data.getConquestTileIfPresent(TILE).projectRulingFaction();
        long revision=f.record().getRevision();
        assertTrue(f.data.getConflictService().endWithMovementHandoff(f.data,TILE,expected(f.record()),context(200),KOMEConflictService.EndSource.LIFECYCLE).isSuccess());
        Intent intent=f.episode().intent;
        assertNotNull(intent); assertEquals(ResolutionKind.NO_TRANSFER,intent.kind); assertEquals(revision,intent.expectedRevision);
        assertEquals(owner,intent.expectedOwner); assertEquals(owner,intent.winner); assertEquals(Long.valueOf(200),intent.closedAt);
        assertEquals(Stage.RECOVERY_PENDING,intent.stage); assertEquals(2,f.episode().grants.size());
        KOMERecoveryExecutor.progress(f.data,"CF1","CF1:E1"); assertEquals(Stage.RECOVERY_PENDING,f.episode().intent.stage);
        assertEquals(owner,f.data.getConquestTileIfPresent(TILE).projectRulingFaction());
    }
    @Test public void olderEpisodeUsesCanonicalPublishedOwnershipIdentityAndOwnAmount() {
        Fixture f=new Fixture(true); KOMEConflictRecord live=f.record();
        f.registry().end(live,150); f.registry().prepareGrants("CF1","CF1:E1",2.5F);
        Episode older=f.episode(); Map<String,Episode> episodes=new TreeMap<String,Episode>(); episodes.put(older.episodeId,older);
        Intent published=new Intent("CF1","CF1:E2",live.getRevision(),State.ORDINARY,ResolutionKind.CONQUEST,"mordor","gondor",5,Stage.RECOVERY_PENDING,null);
        episodes.put("CF1:E2",new Episode("CF1",TILE,"CF1:E2",160,190L,Coverage.COMPLETE,true,Collections.emptyMap(),published,Collections.emptyMap()));
        f.registry().replaceFrom(KOMEOrdinaryBattleRecoveryRegistry.restore(f.registry().targets(),episodes));
        f.registry().bindClosedConflict(live,"gondor",200);
        Intent bound=f.episode().intent; assertEquals(published.expectedRevision,bound.expectedRevision);
        assertEquals(published.kind,bound.kind); assertEquals(published.expectedOwner,bound.expectedOwner); assertEquals(published.winner,bound.winner);
        assertEquals(2.5F,bound.recoveryAmount,0); assertEquals(Long.valueOf(200),bound.closedAt);
    }
    @Test public void neutralCanonicalEndKeepsRecoveryWithoutInventingWinner() {
        Fixture f=new Fixture(true); f.registry().end(f.record(),150); f.registry().prepareGrants("CF1","CF1:E1",5);
        f.registry().bindClosedConflict(f.record(),"",200);
        Intent i=KOMEOrdinaryBattleRecoveryRegistry.read(f.registry().write()).get("CF1","CF1:E1").intent;
        assertEquals(ResolutionKind.NO_TRANSFER,i.kind); assertEquals("",i.expectedOwner); assertEquals("",i.winner);
        assertEquals(Long.valueOf(200),i.closedAt); assertEquals(2,f.episode().grants.size());
        assertThrows(IllegalArgumentException.class,()->new Intent("CF1","CF1:E1",1,State.ORDINARY,
            ResolutionKind.CONQUEST,"","",5,Stage.RECOVERY_PENDING,200L));
        assertThrows(IllegalArgumentException.class,()->new Intent("CF1","CF1:E1",1,State.ORDINARY,
            ResolutionKind.NO_TRANSFER,"","",5,Stage.PREPARED,null));
    }
    @Test public void exactIntentRetryReturnsSameIntentAndSnapshotsAmountWithoutSideEffects() {
        Fixture f=new Fixture(true); NBTTagCompound health=health(f.member("C1"),12); f.data.hiredUnits.get(f.member("C1")).survivingHealth=health;
        long revision=f.record().getRevision(); String owner=f.data.getConquestTileIfPresent(TILE).projectRulingFaction();
        Intent first=f.registry().prepareIntent(f.record(),revision,"CF1:E1",ResolutionKind.CONQUEST,"mordor","gondor",5);
        assertSame(first,f.registry().prepareIntent(f.record(),revision,"CF1:E1",ResolutionKind.CONQUEST,"mordor","gondor",99));
        assertEquals(5,first.recoveryAmount,0); assertEquals(Stage.PREPARED,first.stage);
        assertEquals(owner,f.data.getConquestTileIfPresent(TILE).projectRulingFaction()); assertEquals(12,health.getFloat("Current"),0);
        assertEquals(revision,f.record().getRevision()); assertTrue(f.episode().grants.isEmpty());
    }
    @Test public void staleRevisionWrongEpisodeAndConflictingIntentFailClosed() {
        Fixture f=new Fixture(false); long revision=f.record().getRevision();
        assertThrows(IllegalArgumentException.class,()->f.registry().prepareIntent(f.record(),revision-1,"CF1:E1",ResolutionKind.CONQUEST,"mordor","gondor",5));
        assertThrows(IllegalArgumentException.class,()->f.registry().prepareIntent(f.record(),revision,"CF1:E2",ResolutionKind.CONQUEST,"mordor","gondor",5));
        f.registry().prepareIntent(f.record(),revision,"CF1:E1",ResolutionKind.CONQUEST,"mordor","gondor",5);
        assertThrows(IllegalArgumentException.class,()->f.registry().prepareIntent(f.record(),revision,"CF1:E1",ResolutionKind.NO_TRANSFER,"mordor","mordor",5));
    }
    @Test public void preparedIntentBlocksAbandonmentWithoutCaptureReceipt() {
        Fixture f=new Fixture(false);
        f.registry().prepareIntent(f.record(),f.record().getRevision(),"CF1:E1",ResolutionKind.CONQUEST,"mordor","gondor",5);
        f.data.getConquestTileIfPresent(TILE).setCurrentRulingFaction("gondor");
        assertFalse(KOMEOrdinaryBattleResolutionService.hasCapturePublication(f.data,f.record()));
        assertEquals(KOMEConflictLifecycleService.OrdinaryAbandonmentCode.RESOLUTION_PENDING,
            KOMEConflictLifecycleService.INSTANCE.reconcileOrdinaryConflictAfterCommitmentChange(f.data,TILE,expected(f.record()),context(120)).code);
        assertTrue(f.record().isActive());
    }
    @Test public void intentStagesAreForwardOnlyAndCannotCompleteWithPendingGrants() {
        KOMEConflictRecord c=record(); KOMEOrdinaryBattleRecoveryRegistry r=registry(c,true);
        r.prepareIntent(c,c.getRevision(),"CF1:E1",ResolutionKind.CONQUEST,"mordor","gondor",5);
        r.advance("CF1","CF1:E1",Stage.PUBLICATION_STARTED); r.advance("CF1","CF1:E1",Stage.PUBLICATION_COMPLETE);
        r.advance("CF1","CF1:E1",Stage.RECOVERY_PENDING); r.closed(c,120); r.prepareGrants("CF1","CF1:E1",5);
        assertThrows(IllegalArgumentException.class,()->r.advance("CF1","CF1:E1",Stage.PREPARED));
        assertThrows(IllegalArgumentException.class,()->r.advance("CF1","CF1:E1",Stage.COMPLETE));
        Episode e=KOMEOrdinaryBattleRecoveryRegistry.read(r.write()).get("CF1","CF1:E1");
        assertEquals(Stage.RECOVERY_PENDING,e.intent.stage); assertEquals(Long.valueOf(120),e.intent.closedAt);
    }
    @Test public void malformedAndDuplicateRecoveryIdentitiesAreRejected() {
        KOMEConflictRecord c=record(); KOMEOrdinaryBattleRecoveryRegistry r=registry(c,true); r.end(c,120); r.prepareGrants("CF1","CF1:E1",5);
        for(int kind=0;kind<9;kind++) {
            NBTTagCompound n=r.write(); NBTTagCompound e=n.getTagList("Episodes",10).getCompoundTagAt(0);
            if(kind==0)n.getTagList("Episodes",10).appendTag(e.copy());
            if(kind==1)n.getTagList("Targets",10).appendTag(n.getTagList("Targets",10).getCompoundTagAt(0).copy());
            if(kind==2)e.getTagList("Participants",10).appendTag(e.getTagList("Participants",10).getCompoundTagAt(0).copy());
            if(kind==3)e.getTagList("Grants",10).appendTag(e.getTagList("Grants",10).getCompoundTagAt(0).copy());
            if(kind==4)e.setString("EpisodeId","CF2:E1");
            if(kind==5)e.setString("Coverage","UNKNOWN");
            if(kind==6)e.getTagList("Grants",10).getCompoundTagAt(0).setString("State","APPLIED");
            if(kind==7)e.getTagList("Grants",10).getCompoundTagAt(0).setFloat("Amount",Float.NaN);
            if(kind==8)e.setBoolean("ActualHostileCombatObserved",false);
            assertThrows("malformed kind="+kind,IllegalArgumentException.class,()->KOMEOrdinaryBattleRecoveryRegistry.read(n));
        }
    }
    @Test public void legacyCoverageCannotProduceGrantsEvenAfterProspectiveObservation() {
        KOMEConflictRecord c=record(); KOMEOrdinaryBattleRecoveryRegistry r=new KOMEOrdinaryBattleRecoveryRegistry();
        r.begin(c,Coverage.LEGACY_INCOMPLETE); r.enroll(c,RIDER,"C1","gondor",100,null); r.observeCombat(c,true); r.end(c,120);
        assertFalse(r.prepareGrants("CF1","CF1:E1",5)); assertTrue(r.get("CF1","CF1:E1").grants.isEmpty());
    }
    @Test public void malformedV3WorldLoadIsAtomicAndDoesNotDiscardExistingHistory() {
        Fixture f=new Fixture(true); f.data.initializeIntegratedWorld(); NBTTagCompound valid=save(f.data);
        KOMEWorldData target=new KOMEWorldData("atomic-recovery"); target.readFromNBT(valid);
        NBTTagCompound before=target.getOrdinaryBattleRecovery().write();
        NBTTagCompound bad=(NBTTagCompound)valid.copy();
        NBTTagList episodes=bad.getCompoundTag(KOMEConflictPersistence.RECOVERY_KEY).getTagList("Episodes",10);
        episodes.appendTag(episodes.getCompoundTagAt(0).copy());
        assertThrows(IllegalStateException.class,()->target.readFromNBT(bad));
        assertEquals(before,target.getOrdinaryBattleRecovery().write());
    }

    @Test public void capturedTerminalCompanyCleanupDoesNotEraseCompleteCoverage() {
        Fixture f = new Fixture(true); UUID dead = f.member("C2");
        KOMEOrdinaryBattleRecoveryService.terminal(f.data, dead, Disposition.DEAD, 120, "confirmed death");
        f.data.hiredUnits.remove(dead); f.data.armyCompanies.remove("C2");
        KOMEOrdinaryBattleRecoveryService.observe(f.data, f.record(), 130);
        assertEquals(Coverage.COMPLETE, f.episode().coverage);
        f.registry().end(f.record(), 140); f.registry().prepareGrants("CF1", "CF1:E1", 5);
        assertEquals(1, f.episode().grants.size());
    }
    @Test public void contradictoryEnrollmentDoesNotLeavePartialIdentityAuthority() {
        KOMEConflictRecord c = record(); KOMEOrdinaryBattleRecoveryRegistry r = new KOMEOrdinaryBattleRecoveryRegistry();
        r.begin(c, Coverage.COMPLETE); NBTTagCompound before = r.write();
        assertThrows(IllegalArgumentException.class, () -> r.enroll(c, RIDER, "C1", "gondor", 100, RIDER));
        assertEquals(before, r.write());
    }
    @Test public void prospectiveMountEnrollmentNeedsExactCurrentSurvivorAuthority() {
        Fixture f = new Fixture(false); UUID rider = f.member("C1");
        KOMEHiredUnitRecord unit = f.data.hiredUnits.get(rider); unit.mounted = true;
        KOMEArmyCompany company = f.data.armyCompanies.get("C1");
        company.mountedPopulation = company.totalPopulation; company.groundPopulation = 0;
        KOMEOrdinaryBattleRecoveryService.observe(f.data, f.record(), 110);
        UUID logical = f.registry().targetForPhysical(rider).id;
        assertTrue(f.episode().participants.get(logical).mountIds.isEmpty());
        unit.survivingHealth = health(rider, 12); unit.survivingHealth.setTag("Riding", health(MOUNT, 8));
        KOMEOrdinaryBattleRecoveryService.observe(f.data, f.record(), 120);
        assertEquals(Collections.singleton(f.registry().targetForPhysical(MOUNT).id), f.episode().participants.get(logical).mountIds);
        assertEquals(12F, unit.survivingHealth.getFloat("Current"), 0F);
        assertEquals(8F, unit.survivingHealth.getCompoundTag("Riding").getFloat("Current"), 0F);
    }
    @Test public void laterEpisodeCannotAcquireEarlierParticipantOrGrant() {
        KOMEConflictRecord first = record(); KOMEOrdinaryBattleRecoveryRegistry r = registry(first, false);
        r.end(first, 120); r.prepareGrants("CF1", "CF1:E1", 5);
        KOMEConflictService service = KOMEConflictService.restore(Collections.singletonMap(TILE, first), 2);
        KOMEConflictRecord stopped = ok(service.checkpointCombat(TILE, expected(first),
            new CombatEpisode(1, "CF1:E1", EpisodeState.INACTIVE, 100L, 120L),
            new TimerSlot(TimerStatus.PAUSED, 60000, 20, 120L, "CF1:E1"), TimerSlot.unstarted(), context(120)));
        KOMEConflictRecord second = ok(service.checkpointCombat(TILE, expected(stopped),
            new CombatEpisode(2, "CF1:E2", EpisodeState.ACTIVE, 130L, null),
            new TimerSlot(TimerStatus.RUNNING, 60000, 0, 130L, "CF1:E2"), TimerSlot.unstarted(), context(130)));
        r.begin(second, Coverage.COMPLETE); UUID later = new UUID(8,77);
        r.enroll(second, later, "C3", "gondor", 130, null);
        assertEquals(1, r.get("CF1", "CF1:E1").participants.size());
        assertFalse(r.get("CF1", "CF1:E1").participants.containsKey(r.targetForPhysical(later).id));
        assertTrue(r.get("CF1", "CF1:E2").grants.isEmpty());
        assertEquals(1, r.get("CF1", "CF1:E1").grants.size());
    }
    @Test public void laterIntentRetainsExistingGrantAmountAcrossConfigChanges() {
        KOMEConflictRecord c = record(); KOMEOrdinaryBattleRecoveryRegistry r = registry(c, true);
        r.end(c, 120); r.prepareGrants("CF1", "CF1:E1", 5);
        Intent intent = r.prepareIntent(c, c.getRevision(), "CF1:E1", ResolutionKind.CONQUEST, "mordor", "gondor", 9);
        assertEquals(5F, intent.recoveryAmount, 0F);
        assertEquals(r.write(), KOMEOrdinaryBattleRecoveryRegistry.read(r.write()).write());
    }
    @Test public void enrollmentAdoptsPreexistingHealthIdentityAndRejectsConflictingIdentity() {
        KOMEConflictRecord c=record(); KOMEOrdinaryBattleRecoveryRegistry r=new KOMEOrdinaryBattleRecoveryRegistry(); r.begin(c,Coverage.COMPLETE);
        UUID riderLogical=UUID.randomUUID(), mountLogical=UUID.randomUUID();
        r.enroll(c,RIDER,"C1","gondor",100,MOUNT,riderLogical,mountLogical);
        assertEquals(riderLogical,r.targetForPhysical(RIDER).id);assertEquals(mountLogical,r.targetForPhysical(MOUNT).id);
        assertFalse(r.enroll(c,RIDER,"C1","gondor",101,MOUNT,riderLogical,mountLogical));
        NBTTagCompound before=r.write();
        assertThrows(IllegalArgumentException.class,()->r.enroll(c,RIDER,"C1","gondor",102,MOUNT,UUID.randomUUID(),mountLogical));
        assertEquals(before,r.write());
    }
    @Test public void formalRetreatRetainsVersionedHealthAndDoesNotConsumePendingGrants() {
        Fixture f=new Fixture(true); UUID rider=f.member("C1"); KOMEHiredUnitRecord unit=f.data.hiredUnits.get(rider);
        KOMECampaignHealthProjection health=new KOMECampaignHealthProjection(f.registry().targetForPhysical(rider).id,
            Component.RIDER,rider,3.25F,7,UUID.randomUUID(),false,
            new KOMECampaignHealthProjection.Fence(UUID.randomUUID(),1,UUID.randomUUID()));
        unit.survivingHealth=health.write();
        KOMEConflictMovementService.applyConflictHold(f.data,f.data.armyMovements.get("M1"),f.record(),110);
        assertTrue(KOMEConflictMovementHandoff.releaseCommitment(f.data,
            new ValidatedDepartureRequest(TILE,"C1","gondor",expected(f.record())),"M1",
            KOMEConflictMovementHandoff.Outcome.FORMAL_RETREAT,context(120)).accepted());
        assertEquals(health.write(),unit.survivingHealth);
        KOMEHiredUnitRecord restored=new KOMEHiredUnitRecord();restored.readFromNBT(unit.writeToNBT());
        assertEquals(health.write(),restored.survivingHealth);
        for(Episode e:f.registry().episodes().values())for(Grant g:e.grants.values())assertNotEquals(GrantState.APPLIED,g.state);
    }
    private static KOMEConflictRecord record() {
        KOMEConflictService s=new KOMEConflictService();
        KOMEConflictRecord c=ok(s.start(TILE,State.ORDINARY,ExpectedConflict.absent(),Collections.<GarrisonSeed>emptyList(),context(10)));
        return ok(s.checkpointCombat(TILE,expected(c),new CombatEpisode(1,"CF1:E1",EpisodeState.ACTIVE,100L,null),
            new TimerSlot(TimerStatus.RUNNING,60000,0,100L,"CF1:E1"),TimerSlot.unstarted(),context(100)));
    }
    private static KOMEOrdinaryBattleRecoveryRegistry registry(KOMEConflictRecord c,boolean mounted) {
        KOMEOrdinaryBattleRecoveryRegistry r=new KOMEOrdinaryBattleRecoveryRegistry(); r.begin(c,Coverage.COMPLETE);
        r.enroll(c,RIDER,"C1","gondor",100,mounted?MOUNT:null); r.observeCombat(c,true); return r;
    }
    private static NBTTagCompound health(UUID physical,float hp) { NBTTagCompound h=new NBTTagCompound();h.setString("EntityId",physical.toString());h.setFloat("Current",hp);return h; }
    private static final class Fixture {
        final KOMEConflictMovementServiceTest.Fixture f=KOMEConflictMovementServiceTest.hostileFixture(false,false);
        final KOMEWorldData data=f.data;
        Fixture(boolean defender) {
            KOMEConflictMovementService.ArrivalPreparation arrival=f.prepare("M1",10); f.publish("M1",TILE); f.commit(arrival);
            if(defender) {
                f.company("C2","mordor",TILE);
                KOMEConflictRecord c=record();
                if(!c.getFactionParticipation().containsKey("mordor")) c=ok(data.getConflictService().beginFactionParticipation(TILE,expected(c),"mordor",context(20)));
                ok(data.getConflictService().commit(TILE,expected(c),new CommitmentInput("C2",KOMEHiredUnitClass.CAMPAIGN,EntryOrigin.LEGAL_ARRIVAL,""),context(30)));
            }
            KOMEOrdinaryBattleLifecycleService lifecycle=new KOMEOrdinaryBattleLifecycleService(
                (d,c)->new KOMECombatStateEvaluator().evaluate(d,c),(d,t)->KOMEFortificationContextResolver.Decision.clear(),
                (d,c)->KOMEOrdinaryBattlePlanner.responseDuration(1,1,2,3),(c,duration)->{},KOMEOrdinaryBattleLifecycleService.BeforePublication.NONE);
            assertEquals(KOMEOrdinaryBattleLifecycleService.Code.EPISODE_STARTED,lifecycle.process(data,TILE,100).code);
        }
        KOMEConflictRecord record(){return data.getConflictService().get(TILE);}
        KOMEOrdinaryBattleRecoveryRegistry registry(){return data.getOrdinaryBattleRecovery();}
        Episode episode(){return registry().get("CF1","CF1:E1");}
        UUID member(String company){return data.armyCompanies.get(company).units.get(0);}
    }
    private static ExpectedConflict expected(KOMEConflictRecord c){return ExpectedConflict.at(c.getConflictId(),c.getRevision());}
    private static Context context(long now){return new Context(now,"test","recovery foundation");}
    private static KOMEConflictRecord ok(KOMEConflictService.Result result){assertTrue(result.code+": "+result.reason,result.isSuccess());return result.record;}
    private static NBTTagCompound section(KOMEWorldData data){return KOMEConflictPersistence.write(data.getConflictService(),data.getJoinBattleDeploymentReceipts(),data.getOrdinaryBattleRecovery());}
    private static NBTTagCompound save(KOMEWorldData data){NBTTagCompound n=new NBTTagCompound();data.writeToNBT(n);return n;}
}
