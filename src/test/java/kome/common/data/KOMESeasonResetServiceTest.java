package kome.common.data;

import java.util.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMEConflictContracts.*;

/** Real ownership, company, conflict, movement and root persistence; physical delivery is controlled. */
public class KOMESeasonResetServiceTest {
    @Rule public KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void nativeStaysForeignReturnsAfterOwnershipResetWithoutRepurchasing() {
        Fixture f = new Fixture();
        KOMEArmyCompany nativeCompany = f.company("C1", f.nativeTile);
        KOMEArmyCompany foreign = f.company("C2", f.foreignTile);
        // Pre-reset ownership is deliberately the opposite of the decision after reset.
        f.data.conquestTiles.get(f.nativeTile).claim("mordor", 1L);
        f.data.conquestTiles.get(f.foreignTile).claim("gondor", 1L);
        Map<UUID, NBTTagCompound> snapshots = snapshots(f.data);
        f.run();
        assertTrue(f.data.seasonReset.failure, f.data.seasonReset.complete());
        assertEquals(f.nativeTile, nativeCompany.currentTile);
        assertEquals(f.nativeTile, foreign.currentTile);
        assertEquals(2, f.data.hiredUnits.size());
        assertEquals(snapshots, snapshots(f.data));
        assertEquals(1, count(f.data, "COMPANY_RETURN"));
        KOMEAuditEntry audit = f.data.centralAudit.get(f.data.centralAudit.size()-1);
        assertEquals("C2", audit.subject);
        assertTrue(audit.details.contains("origin=" + f.foreignTile));
        assertTrue(audit.details.contains("destination=" + f.nativeTile));
        assertFalse(audit.reason.isEmpty());
        assertEquals(777L, KOMEPopulationService.getAvailablePopulationCenti(f.data, "gondor"));
    }

    @Test public void pendingUnloadedReturnDoesNotPublishLocationOrAuditAndResumesAfterRootReload() {
        Fixture f = new Fixture(); f.company("C1", f.foreignTile); f.delivery.available = false;
        f.run();
        assertFalse(f.data.seasonReset.complete());
        assertEquals(f.foreignTile, f.data.armyCompanies.get("C1").currentTile);
        assertEquals(0, count(f.data, "COMPANY_RETURN"));
        assertFalse(KOMESeasonResetService.finish(f.data, 30).allowed);
        f.reload(); f.delivery.available = true; f.run();
        assertTrue(f.data.seasonReset.failure, f.data.seasonReset.complete());
        assertEquals(f.nativeTile, f.data.armyCompanies.get("C1").currentTile);
        assertEquals(1, count(f.data, "COMPANY_RETURN"));
    }

    @Test public void missingCapitalAndUnavailableDeploymentRemainActionableAndPending() {
        Fixture f = new Fixture(); f.company("C1", f.foreignTile);
        KOMEFactionCapitalRecord original = KOMEFactionCapitalService.getCapital(f.data, "gondor");
        f.data.factionCapitals.remove("gondor"); f.run();
        assertFalse(f.data.seasonReset.complete());
        assertTrue(f.data.seasonReset.companies.get("C1").reason.contains("capital"));
        assertEquals(0, f.delivery.calls);
        f.data.factionCapitals.put("gondor", original); f.delivery.available = false; f.run();
        assertFalse(f.data.seasonReset.complete());
        assertEquals(0, count(f.data, "COMPANY_RETURN"));
        f.delivery.available = true; f.run();
        assertTrue(f.data.seasonReset.failure, f.data.seasonReset.complete());
    }

    @Test public void interruptionAfterPhysicalDeliveryReplaysWithoutDuplicateOutcomeOrOwnershipReset() {
        Fixture f = new Fixture(); f.company("C1", f.foreignTile);
        f.delivery.interrupt = true; f.run();
        assertFalse(f.data.seasonReset.complete());
        assertEquals(1, f.delivery.effects.size());
        assertEquals(0, count(f.data, "COMPANY_RETURN"));
        f.reload(); f.delivery.interrupt = false; f.run(); f.run();
        assertEquals(1, f.delivery.effects.size());
        assertEquals(1, count(f.data, "RESET_OWNERSHIP"));
        assertEquals(1, count(f.data, "COMPANY_RETURN"));
        assertTrue(KOMESeasonResetService.finish(f.data, 50).allowed);
        f.reload(); assertTrue(KOMESeasonResetService.finish(f.data, 60).allowed);
        assertEquals(2L, f.data.warSeason.seasonId);
        assertEquals(1, count(f.data, "RESET_COMPLETE"));
        assertEquals(1, count(f.data, "COMPANY_RETURN"));
    }

    @Test public void interruptionOfFirstCompanyPersistsReceiptsForLaterVirtualSurvivors() {
        Fixture f = new Fixture(); f.company("C1", f.foreignTile);
        KOMEArmyCompany later = f.company("C2", f.foreignTile); f.order(later, true);
        f.delivery.interrupt = true; f.run(); f.reload();
        KOMEHiredUnitRecord unit = f.data.hiredUnits.get(later.units.get(0));
        assertEquals("1:C2", unit.seasonReturnToken); assertTrue(unit.seasonReturnVirtual);
        assertFalse(f.data.seasonReset.companies.get("C2").complete);
    }

    @Test public void failedIntentCheckpointDoesNotPerformAnyPhysicalEffect() {
        Fixture f = new Fixture(); f.company("C1", f.foreignTile);
        f.delivery.failCheckpoint = true; f.run();
        assertTrue(f.delivery.effects.isEmpty()); assertFalse(f.data.seasonReset.complete());
        assertEquals(0, count(f.data, "COMPANY_RETURN"));
        f.reload(); f.delivery.failCheckpoint = false; f.run();
        assertTrue(f.data.seasonReset.failure, f.data.seasonReset.complete());
    }

    @Test public void canonicalEncirclementAndHeldMovementEndWithoutRemovingPurchasedUnits() {
        Fixture f = new Fixture(); KOMEArmyCompany company = f.company("C1", f.foreignTile);
        KOMEArmyMovementOrder order = f.order(company, false);
        KOMEConflictService.Result started = f.data.getConflictService().start(f.foreignTile,
            KOMEConflictRecord.State.ENCIRCLEMENT, ExpectedConflict.absent(),
            Collections.singletonList(new GarrisonSeed("C1", KOMEHiredUnitClass.CAMPAIGN, company.units)),
            new Context(1, "test", "fixture"));
        assertEquals(Code.SUCCESS, started.code);
        order.hostileAttackDestination=order.destinationTile=order.finalDestinationTile=f.foreignTile;
        order.routeTiles.clear(); order.routeTiles.add(f.nativeTile); order.routeTiles.add(f.foreignTile);
        order.currentRouteIndex=order.finalRouteIndex=order.nextRouteIndex=1;
        KOMEConflictMovementService.applyConflictHold(f.data, order, started.record, 2L);
        company.withdrawalState = KOMEArmyCompany.CLEANUP_WITHDRAWAL;
        f.run();
        assertTrue(f.data.seasonReset.failure, f.data.seasonReset.complete());
        assertFalse(f.data.getConflictService().get(f.foreignTile).isActive());
        assertTrue(f.data.getConflictService().get(f.foreignTile).getCommitments().containsKey("C1"));
        assertEquals(KOMEArmyMovementOrder.CANCELLED, order.status);
        assertEquals("", order.conflictHoldId); assertFalse(order.retreating);
        assertEquals("", company.movementOrderId); assertEquals(KOMEArmyCompany.CLEANUP_NONE, company.withdrawalState);
        assertEquals(1, f.data.hiredUnits.size());
        f.reload(); f.run();
        assertEquals(1, count(f.data, "END")); assertEquals(1, count(f.data, "COMPANY_RETURN"));
    }

    @Test public void virtualMovementRetainsExactSnapshotAndUuidAcrossCancellationAndRestart() {
        Fixture f = new Fixture(); KOMEArmyCompany company = f.company("C1", f.foreignTile);
        f.order(company, true); Map<UUID,NBTTagCompound> before = snapshots(f.data);
        UUID id = company.units.get(0); NBTTagCompound moving = (NBTTagCompound) f.data.hiredUnits.get(id).movingEntityData.copy();
        f.delivery.available = false; f.run(); f.reload();
        assertTrue(f.data.seasonReset.companies.get("C1").virtualUnits.contains(id));
        assertEquals(moving, f.data.hiredUnits.get(id).movingEntityData);
        assertEquals(before, snapshots(f.data));
        f.delivery.available = true; f.run(); f.reload();
        assertEquals(id, f.data.armyCompanies.get("C1").units.get(0));
        assertEquals(moving, f.data.hiredUnits.get(id).movingEntityData);
        assertEquals(3.25F, f.data.hiredUnits.get(id).movingEntityData.getFloat("Health"), 0F);
        assertEquals(before, snapshots(f.data));
    }

    @Test public void corruptJournalRejectsWholeWorldWithoutDiscardingLiveAuthority() {
        Fixture f = new Fixture(); f.company("C1", f.foreignTile); f.delivery.available=false; f.run();
        NBTTagCompound saved = save(f.data); saved.getCompoundTag("SeasonReset").setLong("Season", 999);
        KOMEWorldData target = new KOMEWorldData("target"); target.initializeIntegratedWorld();
        try { target.readFromNBT(saved); fail("Accepted a future reset"); } catch (IllegalStateException expected) { }
        assertEquals(0, target.seasonReset.seasonId); assertTrue(target.armyCompanies.isEmpty());
    }

    @Test public void schemaSevenUpgradesWithEmptyJournalAndKeepsSeasonState() {
        Fixture f = new Fixture(); NBTTagCompound saved = save(f.data);
        saved.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 7); saved.removeTag("SeasonReset");
        for (String key : new String[] {KOMEEmergencyDefensePersistence.SCHEMA_KEY,
                KOMEEmergencyDefensePersistence.RECORDS_KEY, KOMEEmergencyDefensePersistence.COMMITMENTS_KEY,
                KOMEEmergencyDefensePersistence.OBSERVATIONS_KEY}) saved.removeTag(key);
        KOMEWorldData loaded = new KOMEWorldData("old"); loaded.readFromNBT(saved);
        assertEquals(KOMEWarSeasonState.Phase.RESET, loaded.warSeason.phase);
        assertEquals(0, loaded.seasonReset.seasonId);
        assertEquals(KOMEWorldData.KOME_DATA_SCHEMA_VERSION, save(loaded).getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
    }

    @Test public void checkpointWritesReloadableCanonicalRootAndIoFailureStopsTheBoundary() throws Exception {
        Fixture f=new Fixture(); f.company("C1",f.foreignTile); f.delivery.available=false; f.run();
        java.nio.file.Path directory=java.nio.file.Files.createTempDirectory("kom28-checkpoint-");
        java.nio.file.Path file=directory.resolve("KOME_ServerRules.dat");
        try {
            f.data.checkpointResetFile(file);
            NBTTagCompound disk;
            try(java.io.FileInputStream input=new java.io.FileInputStream(file.toFile())) {
                disk=net.minecraft.nbt.CompressedStreamTools.readCompressed(input);
            }
            KOMEWorldData loaded=new KOMEWorldData("disk"); loaded.readFromNBT(disk.getCompoundTag("data"));
            assertEquals(f.foreignTile,loaded.armyCompanies.get("C1").currentTile);
            assertTrue(loaded.seasonReset.ownershipReset);
            assertFalse(loaded.seasonReset.complete());
            byte[] before=java.nio.file.Files.readAllBytes(file);
            try { f.data.checkpointResetFile(directory.resolve("missing").resolve("data.dat")); fail("Write should fail"); }
            catch(IllegalStateException expected) { assertTrue(expected.getMessage().contains("pending")); }
            assertArrayEquals(before,java.nio.file.Files.readAllBytes(file));
            assertTrue(f.data.isDirty());
        } finally { java.nio.file.Files.deleteIfExists(file); java.nio.file.Files.deleteIfExists(directory); }
    }

    @Test public void startupRemainsObservationOnlyAndLiveResetDoesNotScheduleOldMovement() throws Exception {
        try(KOMEPopulationTestConfig config=new KOMEPopulationTestConfig()) {
            Fixture f=new Fixture(); KOMEArmyCompany company=f.company("C1",f.foreignTile);
            KOMEArmyMovementOrder order=f.order(company,true);
            KOMEPopulationPayoutRuntime runtime=new KOMEPopulationPayoutRuntime();
            assertTrue(KOMEEvents.processCampaignTick(f.data,null,100L,runtime).success);
            assertEquals(0L,f.data.seasonReset.seasonId);
            assertEquals(KOMEArmyMovementOrder.MOVING,order.status);
            KOMEEvents.processCampaignTick(f.data,null,101L,runtime);
            assertEquals(KOMEArmyMovementOrder.CANCELLED,order.status);
            assertFalse(f.data.seasonReset.complete());
            assertEquals(f.foreignTile,company.currentTile);
            assertEquals(0,count(f.data,"COMPANY_RETURN"));
            assertEquals(0,count(f.data,"FACTION_DEFEAT"));
        }
    }

    @Test public void restoringCapitalCoordinatesCanResumeWithoutMatchingHistoricalAuditMetadata() {
        Fixture f=new Fixture();f.company("C1",f.foreignTile);f.delivery.available=false;f.run();
        KOMEFactionCapitalRecord original=KOMEFactionCapitalService.getCapital(f.data,"gondor");
        NBTTagCompound tag=original.writeToNBT();tag.setLong("UpdatedAtMillis",999);tag.setString("Actor","repairing operator");
        f.data.factionCapitals.put("gondor",KOMEFactionCapitalRecord.readFromNBT(tag));
        f.delivery.available=true;f.run();assertTrue(f.data.seasonReset.failure,f.data.seasonReset.complete());
        assertEquals(1,count(f.data,"COMPANY_RETURN"));
    }

    @Test public void completionCheckpointFailureRetainsResetGovernanceAndOneCompletionAfterRetry() {
        Fixture f = new Fixture(); KOMEArmyCompany company = f.company("C1", f.foreignTile); f.run();
        KOMEWar war = new KOMEWar(); war.id="W-reset"; war.initiatingFaction="gondor"; war.defendingFaction="mordor";
        war.sideOneFactions.add("gondor"); war.sideTwoFactions.add("mordor"); f.data.wars.put(war.id,war);
        f.data.warSeason.factionDefeats.put("gondor", 1L);
        KOMEGovernanceService.retainDefeat(f.data, company.owner, "gondor", 2L);
        assertFalse(KOMEGovernanceService.militaryAction(f.data, company.owner, "gondor").allowed);
        assertFalse(KOMESeasonResetService.finish(f.data, 200, value -> { throw new java.io.IOException("disk rejected"); }).allowed);
        assertEquals(KOMEWarSeasonState.Phase.RESET, f.data.warSeason.phase);
        assertEquals(1, f.data.warSeason.seasonId); assertEquals(0, count(f.data, "RESET_COMPLETE"));
        assertFalse(KOMEGovernanceService.militaryAction(f.data, company.owner, "gondor").allowed);
        f.reload();
        final NBTTagCompound[] disk = {null};
        assertTrue(KOMESeasonResetService.finish(f.data, 201, value -> disk[0]=save(value)).allowed);
        assertEquals(2, f.data.warSeason.seasonId);
        f.data = new KOMEWorldData("cold"); f.data.readFromNBT(disk[0]);
        assertTrue(KOMESeasonResetService.finish(f.data, 202, value -> disk[0]=save(value)).allowed);
        assertEquals(2, f.data.warSeason.seasonId); assertEquals(1, count(f.data, "RESET_COMPLETE"));
        assertEquals(1, KOMEGovernanceService.records(f.data, company.owner).size());
        assertEquals(1, count(f.data,"COMPANY_RETURN"));
    }

    static class Delivery implements KOMESeasonResetService.Deployment {
        boolean available=true, interrupt, failCheckpoint; int calls;
        final Set<String> effects = new HashSet<String>();
        public void checkpoint(KOMEWorldData data) { if (failCheckpoint) throw new IllegalStateException("Storage unavailable"); save(data); }
        public String apply(KOMEWorldData data, KOMESeasonResetState.Return entry, String token) {
            calls++;
            if (!entry.returnRequired && entry.virtualUnits.isEmpty()) return "";
            if (!available) return "Load company units or restore safe deployment";
            effects.add(token);
            if (interrupt) throw new IllegalStateException("Interrupted after physical delivery");
            return "";
        }
    }
    static class Fixture {
        KOMEWorldData data = new KOMEWorldData("reset");
        final Delivery delivery = new Delivery(); final String nativeTile, foreignTile;
        Fixture() {
            data.initializeIntegratedWorld();
            nativeTile=KOMEFactionCapitalService.getCapitalTileId(data,"gondor");
            foreignTile=KOMEFactionCapitalService.getCapitalTileId(data,"mordor");
            data.grantFactionPopulationCenti("gondor",777L);
            data.warSeason.phase=KOMEWarSeasonState.Phase.FINALE;
            assertTrue(KOMESeasonResetService.begin(data,10).allowed);
        }
        KOMEArmyCompany company(String id, String tile) {
            KOMEArmyCompany c=new KOMEArmyCompany(); c.id=id; c.name=id; c.owner=UUID.randomUUID();
            c.faction=c.nativeFaction="gondor"; c.currentTile=tile; c.totalPopulation=c.groundPopulation=25;
            data.lastKnownPlayerFactions.put(c.owner,"gondor");
            KOMEHiredUnitRecord unit=new KOMEHiredUnitRecord(); unit.entity=UUID.randomUUID(); unit.owner=c.owner;
            unit.companyId=id; unit.currentTile=tile; unit.sourceTileId=nativeTile;
            unit.unitFaction=unit.populationOwningFaction=unit.sourceFaction="gondor";
            unit.sourceType=KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK;
            KOMEHiredUnitClassification.assignForCampaignWorkflow(unit);
            unit.stationedEntityData=new NBTTagCompound(); unit.stationedEntityData.setFloat("Health",3.25F);
            unit.stationedEntityData.setString("Sentinel","preserve exact snapshot");
            c.units.add(unit.entity); data.hiredUnits.put(unit.entity,unit); data.armyCompanies.put(id,c); return c;
        }
        KOMEArmyMovementOrder order(KOMEArmyCompany c, boolean virtual) {
            KOMEArmyMovementOrder o=KOMEArmyMovementOrder.newRoute(1); o.id="M1"; o.companyId=c.id; o.owner=c.owner;
            o.ownerFaction=c.faction; o.currentTile=o.originTile=o.currentStepOriginTile=c.currentTile;
            o.destinationTile=o.finalDestinationTile=o.currentStepDestinationTile=nativeTile;
            o.routeTiles.add(c.currentTile); o.routeTiles.add(nativeTile); o.units.addAll(c.units);
            o.retreating=true; o.status=virtual ? KOMEArmyMovementOrder.MOVING : KOMEArmyMovementOrder.WAITING_NEXT_STEP;
            c.movementOrderId=o.id; c.status=virtual ? KOMEArmyCompany.MOVING : KOMEArmyCompany.STATIONED;
            KOMEHiredUnitRecord u=data.hiredUnits.get(c.units.get(0)); u.movementOrderId=o.id;
            if(virtual) u.movingEntityData=(NBTTagCompound)u.stationedEntityData.copy();
            data.armyMovements.put(o.id,o); return o;
        }
        void run() { KOMESeasonResetService.process(data,100L,100L,delivery); }
        void reload() { KOMEWorldData copy=new KOMEWorldData("reload"); copy.readFromNBT(save(data)); data=copy; }
    }
    static NBTTagCompound save(KOMEWorldData data) { NBTTagCompound saved=new NBTTagCompound(); data.writeToNBT(saved); return saved; }
    static int count(KOMEWorldData data,String action) { int n=0; for(KOMEAuditEntry a:data.centralAudit)if(action.equals(a.action))n++; return n; }
    static Map<UUID,NBTTagCompound> snapshots(KOMEWorldData data) {
        Map<UUID,NBTTagCompound> result=new HashMap<UUID,NBTTagCompound>();
        for(KOMEHiredUnitRecord u:data.hiredUnits.values())result.put(u.entity,(NBTTagCompound)u.stationedEntityData.copy());
        return result;
    }
}
