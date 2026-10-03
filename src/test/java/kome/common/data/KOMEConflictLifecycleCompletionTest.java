package kome.common.data;

import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static org.junit.Assert.*;

public class KOMEConflictLifecycleCompletionTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Before @After public void resetRelations() {
        relation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
    }

    @Test public void ordinaryEndClosesContinuityPreservesHistoryAndPausesRoute() {
        Fixture f = new Fixture(false);
        KOMEConflictRecord active = f.arriveAndHold();
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        List<String> route = new ArrayList<String>(order.routeTiles);
        int allowance = order.dailyStepsRemaining;
        String ownerBefore = f.data.conquestTiles.get("T101").projectRulingFaction();
        int commitments = active.getCommitments().size();

        KOMEConflictService.EndResult result = f.end(active, 50L,
            KOMEConflictService.EndSource.LIFECYCLE, "validated downstream end");

        assertTrue(result.conflictResult.reason, result.isSuccess());
        KOMEConflictRecord ended = result.conflictResult.record;
        assertEquals(State.ENDED, ended.getState());
        assertEquals(active.getRevision() + 1L, ended.getRevision());
        assertEquals(commitments, ended.getCommitments().size());
        for (FactionParticipation faction : ended.getFactionParticipation().values())
            assertEquals(Long.valueOf(50L), faction.endedAtMillis);
        assertEquals(ownerBefore,
            f.data.conquestTiles.get("T101").projectRulingFaction());
        assertEquals(1, result.movementHoldsReleased);
        assertEquals(KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED, order.status);
        assertEquals("", order.conflictHoldId);
        assertEquals(0L, order.conflictHeldAtMillis);
        assertEquals(0L, order.nextStepDepartureMillis);
        assertEquals(0L, order.nextStepAvailableMillis);
        assertEquals(route, order.routeTiles);
        assertEquals(allowance, order.dailyStepsRemaining);
        assertEquals("T101", f.data.armyCompanies.get("C1").currentTile);
        assertEquals("M1", f.data.armyCompanies.get("C1").movementOrderId);
        assertEquals(Operation.END, ended.getLastTransition().operation);
        assertEquals("validated downstream end", ended.getLastTransition().reason);
        assertEquals(1, countAudit(f.data, "CONFLICT", "END"));
        assertEquals(1, countAudit(f.data, "MOVEMENT",
            KOMEMovementHistoryRecord.CONFLICT_RELEASED_PAUSED));
    }

    @Test public void encirclementEndPreservesComplexTimerEpisodeAndGarrisonSnapshot() {
        Fixture f = new Fixture(true);
        f.company("C2", "mordor", "T101");
        KOMEConflictRecord record = f.arriveAndHold();
        record = ok(f.data.getConflictService().registerComplex("T101", expected(record),
            "castle", context(20L, "register")));
        record = ok(f.data.getConflictService().checkpointComplex("T101", expected(record),
            new ComplexSubstate("castle", ComplexState.ASSAULT_ACTIVE, "assault-1",
                new ProgressCheckpoint(Collections.singleton("segment-a")),
                new LeadAuthority("gondor", new UUID(7L, 7L))), context(25L, "checkpoint")));
        CombatEpisode episode = new CombatEpisode(1, record.getConflictId() + ":E1",
            EpisodeState.ACTIVE, 30L, null);
        TimerSlot response = new TimerSlot(TimerStatus.RUNNING, 1000L, 100L,
            35L, episode.episodeId);
        record = ok(f.data.getConflictService().checkpointCombat("T101", expected(record),
            episode, response, TimerSlot.unstarted(), context(35L, "combat")));
        int garrison = record.getOriginalGarrison().size();

        KOMEConflictRecord ended = f.end(record, 60L,
            KOMEConflictService.EndSource.LIFECYCLE, "encirclement ended")
            .conflictResult.record;

        assertEquals(State.ENDED, ended.getState());
        assertEquals(garrison, ended.getOriginalGarrison().size());
        assertSame(response, ended.getResponseTimer());
        assertSame(episode, ended.getCombatEpisode());
        assertEquals(ComplexState.ASSAULT_ACTIVE,
            ended.getComplexes().get("castle").state);
        assertEquals("assault-1", ended.getComplexes().get("castle").activeAssaultId);
        assertEquals(Collections.singleton("segment-a"),
            ended.getComplexes().get("castle").progress.securedSegmentIds);
        assertNotNull(ended.getComplexes().get("castle").lead);
        assertEquals(Long.valueOf(60L), ended.getEncirclement().endedAtMillis);
    }

    @Test public void staleAndRepeatedEndRejectWithoutAuditOrHoldMutation() {
        Fixture f = new Fixture(false);
        KOMEConflictRecord active = f.arriveAndHold();
        int audits = f.data.centralAudit.size();
        KOMEConflictService.EndResult staleId = f.data.getConflictService()
            .endWithMovementHandoff(f.data, "T101",
                ExpectedConflict.at("CF999", active.getRevision()),
                context(40L, "stale"), KOMEConflictService.EndSource.LIFECYCLE);
        assertEquals(Code.STALE_CONFLICT_ID, staleId.conflictResult.code);
        KOMEConflictService.EndResult staleRevision = f.data.getConflictService()
            .endWithMovementHandoff(f.data, "T101",
                ExpectedConflict.at(active.getConflictId(), active.getRevision() + 1L),
                context(40L, "stale"), KOMEConflictService.EndSource.LIFECYCLE);
        assertEquals(Code.STALE_REVISION, staleRevision.conflictResult.code);
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,
            f.data.armyMovements.get("M1").status);
        assertEquals(audits, f.data.centralAudit.size());

        KOMEConflictRecord ended = f.end(active, 50L,
            KOMEConflictService.EndSource.LIFECYCLE, "end once")
            .conflictResult.record;
        int endedAudits = f.data.centralAudit.size();
        KOMEConflictService.EndResult repeated = f.data.getConflictService()
            .endWithMovementHandoff(f.data, "T101", expected(ended),
                context(60L, "repeat"), KOMEConflictService.EndSource.LIFECYCLE);
        assertEquals(Code.CONFLICT_ENDED, repeated.conflictResult.code);
        assertSame(ended, f.data.getConflictService().get("T101"));
        assertEquals(endedAudits, f.data.centralAudit.size());
    }

    @Test public void incoherentHoldBlocksEntireEndTransaction() {
        Fixture f = new Fixture(false);
        KOMEConflictRecord active = f.arriveAndHold();
        f.data.armyCompanies.get("C1").movementOrderId = "M-other";
        int audits = f.data.centralAudit.size();

        KOMEConflictService.EndResult rejected = f.end(active, 50L,
            KOMEConflictService.EndSource.ADMIN_FORCED, "must remain atomic");

        assertEquals(Code.AMBIGUOUS_REFERENCE, rejected.conflictResult.code);
        assertSame(active, f.data.getConflictService().get("T101"));
        assertEquals(State.ORDINARY, f.data.getConflictService().get("T101").getState());
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,
            f.data.armyMovements.get("M1").status);
        assertEquals(audits, f.data.centralAudit.size());
    }

    @Test public void ordinaryHeldMemberRejectsEndAndRepairWithoutPartialMutationOrAudit() {
        Fixture f = new Fixture(false);
        KOMEConflictRecord active = f.arriveAndHold();
        KOMEArmyCompany company = f.data.armyCompanies.get("C1");
        KOMEHiredUnitRecord unit = f.data.hiredUnits.get(company.units.get(0));
        unit.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY);
        int audits = f.data.centralAudit.size();

        KOMEConflictService.EndResult rejected = f.end(active, 50L,
            KOMEConflictService.EndSource.ADMIN_FORCED, "ordinary cohort must fail closed");

        assertEquals(Code.AMBIGUOUS_REFERENCE, rejected.conflictResult.code);
        assertSame(active, f.data.getConflictService().get("T101"));
        assertEquals(State.ORDINARY, active.getState());
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,
            f.data.armyMovements.get("M1").status);
        assertEquals("M1", company.movementOrderId);
        assertEquals("M1", unit.movementOrderId);
        assertEquals(audits, f.data.centralAudit.size());

        company.movementOrderId = "";
        unit.movementOrderId = "";
        KOMEConflictLifecycleService.RepairPlan preview =
            KOMEConflictLifecycleService.INSTANCE.previewRepair(f.data, "T101");
        assertTrue(preview.actions.toString(), preview.actions.isEmpty());
        assertTrue(preview.unresolved.toString(), !preview.unresolved.isEmpty());
        KOMEConflictLifecycleService.RepairResult repair =
            KOMEConflictLifecycleService.INSTANCE.applyRepair(f.data, "T101", 60L, "admin");
        assertEquals(0, repair.changesApplied);
        assertEquals("", company.movementOrderId);
        assertEquals("", unit.movementOrderId);
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,
            f.data.armyMovements.get("M1").status);
        assertEquals(audits, f.data.centralAudit.size());
    }

    @Test public void incoherentCampaignCohortRejectsEndWithoutMutationOrAudit() {
        Fixture f = new Fixture(false);
        KOMEConflictRecord active = f.arriveAndHold();
        KOMEArmyCompany company = f.data.armyCompanies.get("C1");
        KOMEHiredUnitRecord unit = f.data.hiredUnits.get(company.units.get(0));
        unit.owner = new UUID(99L, 99L);
        int audits = f.data.centralAudit.size();

        KOMEConflictService.EndResult rejected = f.end(active, 50L,
            KOMEConflictService.EndSource.LIFECYCLE, "incoherent cohort must fail closed");

        assertEquals(Code.AMBIGUOUS_REFERENCE, rejected.conflictResult.code);
        assertSame(active, f.data.getConflictService().get("T101"));
        assertEquals(State.ORDINARY, active.getState());
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,
            f.data.armyMovements.get("M1").status);
        assertEquals("M1", company.movementOrderId);
        assertEquals("M1", unit.movementOrderId);
        assertEquals(audits, f.data.centralAudit.size());
    }

    @Test public void destinationMismatchRejectsEndAndEndedHoldRepair() {
        Fixture f = new Fixture(false);
        KOMEConflictRecord active = f.arriveAndHold();
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        order.hostileAttackDestination = "T100";
        int audits = f.data.centralAudit.size();

        KOMEConflictService.EndResult rejected = f.end(active, 50L,
            KOMEConflictService.EndSource.LIFECYCLE, "destination mismatch");
        assertEquals(Code.AMBIGUOUS_REFERENCE, rejected.conflictResult.code);
        assertSame(active, f.data.getConflictService().get("T101"));
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD, order.status);
        assertEquals(audits, f.data.centralAudit.size());

        KOMEConflictRecord legacyEnded = ok(f.data.getConflictService().end("T101",
            expected(active), context(55L, "legacy data-only end")));
        assertEquals(State.ENDED, legacyEnded.getState());
        KOMEConflictLifecycleService.RepairPlan preview =
            KOMEConflictLifecycleService.INSTANCE.previewRepair(f.data, "T101");
        assertTrue(preview.actions.toString(), preview.actions.isEmpty());
        assertTrue(preview.unresolved.toString(), !preview.unresolved.isEmpty());
        KOMEConflictLifecycleService.RepairResult repair =
            KOMEConflictLifecycleService.INSTANCE.applyRepair(f.data, "T101", 60L, "admin");
        assertEquals(0, repair.changesApplied);
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD, order.status);
        assertEquals(active.getConflictId(), order.conflictHoldId);
        assertEquals(audits, f.data.centralAudit.size());
    }

    @Test public void liveHostilityReadinessIsReadOnlyAndUnknownFailsConfidence() {
        Fixture f = new Fixture(false);
        KOMEConflictRecord record = f.arriveAndHold();
        long revision = record.getRevision();
        int audits = f.data.centralAudit.size();
        relation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        KOMEConflictLifecycleService.Diagnostics peaceful =
            KOMEConflictLifecycleService.INSTANCE.diagnose(f.data, "T101");
        assertFalse(peaceful.hostilePairPresent);
        assertTrue(peaceful.confidentlyNoHostilePair);
        assertEquals(State.ORDINARY, peaceful.record.getState());
        assertEquals(revision, peaceful.record.getRevision());
        assertEquals(audits, f.data.centralAudit.size());

        record = ok(f.data.getConflictService().beginFactionParticipation("T101",
            expected(record), "unresolvable_faction", context(20L, "late participant")));
        KOMEConflictLifecycleService.Diagnostics unknown =
            KOMEConflictLifecycleService.INSTANCE.diagnose(f.data, "T101");
        assertTrue(unknown.unknownHostilityPresent);
        assertFalse(unknown.confidentlyNoHostilePair);
        assertEquals(State.ORDINARY, f.data.getConflictService().get("T101").getState());
    }

    @Test public void inspectionAndRepairPreviewAreMutationFreeAndApplyIsIdempotent() {
        Fixture f = new Fixture(false);
        KOMEConflictRecord record = f.arriveAndHold();
        KOMEArmyCompany company = f.data.armyCompanies.get("C1");
        KOMEHiredUnitRecord unit = f.data.hiredUnits.get(company.units.get(0));
        company.movementOrderId = "";
        unit.movementOrderId = "";
        int audits = f.data.centralAudit.size();
        long revision = record.getRevision();

        List<String> lines = KOMEConflictLifecycleService.INSTANCE
            .inspectionLines(f.data, "T101");
        KOMEConflictLifecycleService.RepairPlan preview =
            KOMEConflictLifecycleService.INSTANCE.previewRepair(f.data, "T101");
        assertTrue(lines.get(0).contains(record.getConflictId()));
        assertTrue(lines.get(0).contains("revision=" + revision));
        assertEquals(2, preview.actions.size());
        assertEquals("", company.movementOrderId);
        assertEquals("", unit.movementOrderId);
        assertEquals(revision, f.data.getConflictService().get("T101").getRevision());
        assertEquals(audits, f.data.centralAudit.size());

        KOMEConflictLifecycleService.RepairResult applied =
            KOMEConflictLifecycleService.INSTANCE.applyRepair(f.data, "T101",
                40L, "admin");
        assertEquals(2, applied.changesApplied);
        assertEquals("M1", company.movementOrderId);
        assertEquals("M1", unit.movementOrderId);
        assertEquals(1, countAudit(f.data, "CONFLICT", "REPAIR"));
        int appliedAudits = f.data.centralAudit.size();
        assertEquals(0, KOMEConflictLifecycleService.INSTANCE.applyRepair(
            f.data, "T101", 41L, "admin").changesApplied);
        assertEquals(appliedAudits, f.data.centralAudit.size());
    }

    @Test public void ambiguousRepairIsReportedAndNeverGuessed() {
        Fixture f = new Fixture(false);
        f.arriveAndHold();
        KOMEArmyCompany company = f.data.armyCompanies.get("C1");
        company.movementOrderId = "M-other";
        int audits = f.data.centralAudit.size();
        KOMEConflictLifecycleService.RepairPlan plan =
            KOMEConflictLifecycleService.INSTANCE.previewRepair(f.data, "T101");
        assertTrue(plan.unresolved.toString(), !plan.unresolved.isEmpty());
        KOMEConflictLifecycleService.RepairResult applied =
            KOMEConflictLifecycleService.INSTANCE.applyRepair(f.data, "T101",
                40L, "admin");
        assertEquals(0, applied.changesApplied);
        assertEquals("M-other", company.movementOrderId);
        assertEquals(audits, f.data.centralAudit.size());
    }

    @Test public void forcedEndAndPostConflictPauseRoundTripWithoutAutoResume() {
        Fixture f = new Fixture(false);
        f.data.initializeIntegratedWorld();
        KOMEConflictRecord active = f.arriveAndHold();
        KOMEConflictRecord ended = f.end(active, 50L,
            KOMEConflictService.EndSource.ADMIN_FORCED, "operator maintenance")
            .conflictResult.record;
        assertEquals(1, countAudit(f.data, "CONFLICT", "FORCED_END"));

        NBTTagCompound saved = new NBTTagCompound();
        f.data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("phase5-restored");
        restored.readFromNBT(saved);

        KOMEConflictRecord restoredConflict = restored.getConflictService().get("T101");
        KOMEArmyMovementOrder restoredOrder = restored.armyMovements.get("M1");
        assertEquals(ended.getConflictId(), restoredConflict.getConflictId());
        assertEquals(State.ENDED, restoredConflict.getState());
        assertEquals(KOMEArmyMovementOrder.CONFLICT_RELEASED_PAUSED,
            restoredOrder.status);
        assertEquals("", restoredOrder.conflictHoldId);
        assertEquals(0L, restoredOrder.arrivalMillis);
        assertEquals(2, restoredOrder.routeTiles.size());
        assertEquals("M1", restored.armyCompanies.get("C1").movementOrderId);
        assertFalse(restoredOrder.hasArrived(Long.MAX_VALUE));

        KOMEConflictRecord fresh = ok(restored.getConflictService().start("T101",
            State.ORDINARY, expected(restoredConflict),
            Collections.<GarrisonSeed>emptyList(), context(70L, "fresh conflict")));
        assertNotEquals(ended.getConflictId(), fresh.getConflictId());
        assertTrue(fresh.getCommitments().isEmpty());
        assertEquals(TimerStatus.NOT_STARTED, fresh.getResponseTimer().status);
        assertTrue(fresh.getComplexes().isEmpty());
        KOMEConflictService.EndResult staleAdmin = restored.getConflictService()
            .endWithMovementHandoff(restored, "T101", expected(restoredConflict),
                context(80L, "stale operator request"),
                KOMEConflictService.EndSource.ADMIN_FORCED);
        assertEquals(Code.STALE_CONFLICT_ID, staleAdmin.conflictResult.code);
        assertSame(fresh, restored.getConflictService().get("T101"));
    }

    private static final class Fixture {
        final KOMEWorldData data = new KOMEWorldData("phase5");
        private int uuidSequence = 1;
        final boolean defensive;

        Fixture(boolean defensive) {
            this.defensive = defensive;
            tile("T100", "gondor");
            tile("T101", "mordor");
            relation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
            company("C1", "gondor", "T100");
            order();
            if (defensive) {
                KOMEPlayerBuild build = new KOMEPlayerBuild();
                build.id = "B1"; build.tileId = "T101";
                build.displayName = "Fort"; build.active = true;
                build.type = KOMEBuildType.DEFENSIVE;
                data.builds.put(build.id, build);
            }
        }

        KOMEConflictRecord arriveAndHold() {
            KOMEArmyMovementOrder order = data.armyMovements.get("M1");
            order.arrivalMillis = 10L;
            KOMEConflictMovementService.ArrivalPreparation preparation =
                KOMEConflictMovementService.prepareLegalArrival(data, order, "T101");
            assertTrue(preparation.reason, preparation.ready());
            KOMEArmyCompany company = data.armyCompanies.get("C1");
            order.status = KOMEArmyMovementOrder.SPAWNING;
            order.currentTile = "T101";
            order.currentRouteIndex = order.nextRouteIndex = 1;
            order.completedSteps = 1;
            company.currentTile = "T101";
            for (UUID unitId : company.units)
                data.hiredUnits.get(unitId).currentTile = "T101";
            KOMEConflictMovementService.ArrivalCommitment commitment =
                KOMEConflictMovementService.commitLegalArrival(data,
                    preparation.receipt, "server");
            assertTrue(commitment.reason, commitment.success());
            KOMEConflictRecord conflict = commitment.conflictResult.record;
            KOMEConflictMovementService.applyConflictHold(data, order, conflict, 10L);
            return conflict;
        }

        KOMEConflictService.EndResult end(KOMEConflictRecord record, long timestamp,
                KOMEConflictService.EndSource source, String reason) {
            return data.getConflictService().endWithMovementHandoff(data, "T101",
                expected(record), context(timestamp, reason), source);
        }

        void tile(String id, String faction) {
            KOMEConquestTile tile = new KOMEConquestTile(id);
            tile.setCurrentRulingFaction(faction);
            data.conquestTiles.put(id, tile);
        }

        KOMEArmyCompany company(String id, String faction, String tile) {
            KOMEArmyCompany company = new KOMEArmyCompany();
            company.id = id; company.owner = new UUID(1L, id.hashCode());
            company.ownerName = "Owner"; company.faction = company.nativeFaction = faction;
            company.currentTile = tile; company.name = id;
            company.status = KOMEArmyCompany.STATIONED;
            KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
            unit.entity = new UUID(2L, uuidSequence++); unit.owner = company.owner;
            unit.sourcePlayer = company.owner; unit.companyId = id; unit.companyName = id;
            unit.currentTile = tile; unit.sourceTileId = tile;
            unit.type = KOMEPopulationType.OFFENSIVE;
            unit.cost = unit.baseCost = unit.populationSpent = 10;
            unit.populationOwningFaction = faction; unit.sourceFaction = faction;
            unit.unitFaction = faction;
            unit.assignPersistedUnitClass(KOMEHiredUnitClass.CAMPAIGN);
            company.units.add(unit.entity);
            company.totalPopulation = company.groundPopulation = 10;
            data.hiredUnits.put(unit.entity, unit);
            data.armyCompanies.put(id, company);
            return company;
        }

        void order() {
            KOMEArmyCompany company = data.armyCompanies.get("C1");
            KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(1);
            order.id = "M1"; order.companyId = "C1"; order.owner = company.owner;
            order.ownerName = company.ownerName; order.ownerFaction = company.faction;
            order.originTile = "T100"; order.destinationTile = "T101";
            order.finalDestinationTile = "T101"; order.currentTile = "T100";
            order.nextTile = "T101"; order.currentStepOriginTile = "T100";
            order.currentStepDestinationTile = "T101";
            order.hostileAttackDestination = "T101";
            order.routeTiles.add("T100"); order.routeTiles.add("T101");
            order.traveledRouteTiles.add("T100");
            order.currentRouteIndex = 0; order.nextRouteIndex = 1;
            order.finalRouteIndex = 1; order.totalSteps = 1; order.distanceTiles = 1;
            order.dailyStepsRemaining = 1; order.units.addAll(company.units);
            for (UUID unitId : company.units)
                data.hiredUnits.get(unitId).movementOrderId = order.id;
            company.status = KOMEArmyCompany.MOVING;
            company.movementOrderId = order.id;
            data.armyMovements.put(order.id, order);
        }
    }

    private static Context context(long time, String reason) {
        return new Context(time, "tester", reason);
    }

    private static ExpectedConflict expected(KOMEConflictRecord record) {
        return ExpectedConflict.at(record.getConflictId(), record.getRevision());
    }

    private static KOMEConflictRecord ok(KOMEConflictService.Result result) {
        assertEquals(result.reason, Code.SUCCESS, result.code);
        return result.record;
    }

    private static int countAudit(KOMEWorldData data, String domain, String action) {
        int count = 0;
        for (KOMEAuditEntry entry : data.centralAudit)
            if (domain.equals(entry.domain) && action.equals(entry.action)) count++;
        return count;
    }

    private static void relation(String first, String second,
            LOTRFactionRelations.Relation relation) {
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction(first),
            KOMEAlliance.findLotrFaction(second), relation);
    }
}
