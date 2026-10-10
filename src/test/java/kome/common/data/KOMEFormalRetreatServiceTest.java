package kome.common.data;

import kome.common.KOMEAccessFixture;
import kome.common.KOMEReflection;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import java.util.Collections;
import java.util.UUID;

import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.*;
import static org.junit.Assert.*;

public class KOMEFormalRetreatServiceTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    @Before public void configure() throws Exception { config = new KOMEPopulationTestConfig(); }
    @After public void cleanup() throws Exception {
        config.close();
        lotr.common.fac.LOTRFactionRelations.overrideRelations(
            lotr.common.fac.LOTRFaction.GONDOR,lotr.common.fac.LOTRFaction.MORDOR,
            lotr.common.fac.LOTRFactionRelations.Relation.NEUTRAL);
    }

    @Test public void exactCurrentConflictRetreatsOnlyPersonallyCommandedCompanyAndClosesReceipt()
            throws Exception {
        Fixture f = new Fixture(2);
        lotr.common.fac.LOTRFactionRelations.overrideRelations(
            lotr.common.fac.LOTRFaction.GONDOR,lotr.common.fac.LOTRFaction.MORDOR,
            lotr.common.fac.LOTRFactionRelations.Relation.ENEMY);
        KOMEArmyCompany elsewhere = f.company("C9", "T102", "M9", f.playerId);
        elsewhere.movementAllowance = 2;
        KOMEConflictRecord otherConflict = f.commitAt(elsewhere, "M9", "T102", 17L);
        KOMEConflictMovementService.applyConflictHold(f.data, f.data.armyMovements.get("M9"),
            otherConflict, 19L);
        KOMEArmyCompany otherPlayer = f.company("C2", "T101", "M2", UUID.randomUUID());
        otherPlayer.movementAllowance = 1;
        f.commit(otherPlayer, "M2", 20L);
        KOMEConflictMovementService.applyConflictHold(f.data, f.data.armyMovements.get("M2"),
            f.record(), 21L);

        KOMEFormalRetreatService.Result result = KOMEFormalRetreatService.INSTANCE.retreat(
            f.data, f.access.player, 30L, new SuccessfulEgress());

        assertEquals(result.message, KOMEFormalRetreatService.Code.RETREATED, result.code);
        assertEquals(Collections.singletonList("C1"), result.companyIds);
        assertFalse(f.record().getCommitments().containsKey("C1"));
        assertTrue(f.record().getCommitments().containsKey("C2"));
        assertTrue(f.record().isActive());
        assertEquals(KOMEArmyMovementOrder.ARRIVED,
            f.data.armyMovements.get("M1").status);
        assertEquals("T100", f.data.armyCompanies.get("C1").currentTile);
        assertEquals("T100", f.data.hiredUnits.get(
            f.data.armyCompanies.get("C1").units.get(0)).currentTile);
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,
            f.data.armyMovements.get("M2").status);
        assertEquals("T102", elsewhere.currentTile);
        assertEquals(2, elsewhere.movementAllowance);
        assertTrue(f.data.getConflictService().get("T102").getCommitments().containsKey("C9"));
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,f.data.armyMovements.get("M9").status);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED, f.receipt().getState());
        assertEquals(PlayerStatus.ACTIVE, f.record().getPlayers().get(f.playerId).status);
    }

    @Test public void everyPersonallyCommandedCompanyInExactConflictRetreatsTogether()
            throws Exception {
        Fixture f=new Fixture(1);
        KOMEArmyCompany second=f.company("C3","T101","M3",f.playerId);
        second.movementAllowance=0;
        KOMEConflictRecord conflict=f.commit(second,"M3",20L);
        KOMEConflictMovementService.applyConflictHold(f.data,f.data.armyMovements.get("M3"),conflict,21L);
        KOMEFormalRetreatService.Result result=KOMEFormalRetreatService.INSTANCE.retreat(
            f.data,f.access.player,30L,new SuccessfulEgress());
        assertEquals(result.message,KOMEFormalRetreatService.Code.RETREATED,result.code);
        assertEquals(java.util.Arrays.asList("C1","C3"),result.companyIds);
        assertFalse(f.record().getCommitments().containsKey("C1"));
        assertFalse(f.record().getCommitments().containsKey("C3"));
        assertTrue(KOMEMovementRetreatService.isAcceptedFormalRetreat(f.data.armyMovements.get("M1")));
        assertTrue(KOMEMovementRetreatService.isAcceptedFormalRetreat(f.data.armyMovements.get("M3")));
        assertEquals(0,second.movementAllowance);
    }

    @Test public void soleOffensiveRetreatEndsAbandonedAndNextAttackGetsFreshConflictId()
            throws Exception {
        Fixture f = new Fixture(1);
        String ownerBefore = f.data.conquestTiles.get("T101").projectRulingFaction();
        String firstId = f.record().getConflictId();

        KOMEFormalRetreatService.Result result = KOMEFormalRetreatService.INSTANCE.retreat(
            f.data, f.access.player, 30L, new SuccessfulEgress());

        assertEquals(result.message, KOMEFormalRetreatService.Code.RETREATED, result.code);
        KOMEConflictRecord ended = f.record();
        assertEquals(KOMEConflictRecord.State.ENDED, ended.getState());
        assertEquals(KOMEConflictLifecycleService.FORMAL_RETREAT_ABANDONMENT_REASON,
            ended.getLastTransition().reason);
        assertTrue(result.message.contains("battle ended"));
        assertEquals(ownerBefore,
            f.data.conquestTiles.get("T101").projectRulingFaction());

        KOMEConflictRecord fresh = Fixture.ok(f.data.getConflictService().start("T101",
            KOMEConflictRecord.State.ORDINARY, Fixture.expected(ended),
            Collections.<GarrisonSeed>emptyList(), Fixture.context(40L)));
        assertNotEquals(firstId, fresh.getConflictId());
        assertEquals("CF2", fresh.getConflictId());
    }

    @Test public void retreatConsumesAtMostOneAndZeroAllowanceStillMoves() throws Exception {
        for (int starting : new int[] {0, 1, 2}) {
            Fixture f = new Fixture(starting);
            KOMEFormalRetreatService.Result result = KOMEFormalRetreatService.INSTANCE.retreat(
                f.data, f.access.player, 30L, new SuccessfulEgress());
            assertEquals(result.message, KOMEFormalRetreatService.Code.RETREATED, result.code);
            KOMEArmyCompany company = f.data.armyCompanies.get("C1");
            assertEquals(Math.max(0, starting - 1), company.movementAllowance);
            KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
            assertTrue(KOMEMovementRetreatService.isAcceptedFormalRetreat(order));
            assertEquals(java.util.Arrays.asList("T101", "T100"), order.routeTiles);
            assertFalse("The completed immediate step no longer has a free formal-retreat debit",
                KOMEMovementRetreatService.isImmediateFormalRetreatStep(order));
            int afterImmediate = Math.max(0, starting - 1);
            assertEquals(afterImmediate, company.movementAllowance);
            boolean laterStep = KOMEMovementDayService.depart(f.data, order, true, () -> true);
            assertEquals(afterImmediate > 0, laterStep);
            assertEquals(Math.max(0, afterImmediate - 1), company.movementAllowance);
        }
    }

    @Test public void lowercaseConflictIdResolvesNativeOwnedHeldCompanyAtZeroMovement()
            throws Exception {
        Fixture f = new Fixture(0);
        KOMEArmyCompany company = f.data.armyCompanies.get("C1");
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        String ownerBefore = f.data.conquestTiles.get("T101").projectRulingFaction();

        assertEquals(f.playerId, company.owner);
        assertNull(company.temporaryController);
        assertEquals(KOMEArmyCompany.AUTHORITY_NATIVE, company.controllerAuthority);
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD, order.status);
        assertEquals(f.record().getConflictId(), order.conflictHoldId);
        assertTrue(KOMECompanyCommandAuthority.canPersonallyCommand(
            f.data, company, f.playerId));

        KOMEFormalRetreatService.Result result = KOMEFormalRetreatService.INSTANCE.retreat(
            f.data, f.access.player, "cf1", 30L, new SuccessfulEgress());

        assertEquals(result.message, KOMEFormalRetreatService.Code.RETREATED, result.code);
        assertEquals(Collections.singletonList("C1"), result.companyIds);
        assertEquals("T100", company.currentTile);
        assertEquals(0, company.movementAllowance);
        assertEquals(KOMEConflictRecord.State.ENDED, f.record().getState());
        assertEquals(ownerBefore,
            f.data.conquestTiles.get("T101").projectRulingFaction());
    }

    @Test public void personalCommandUsesExactUuidAndCanonicalTemporaryControlNotOperatorStatus()
            throws Exception {
        Fixture f = new Fixture(1);
        KOMEArmyCompany company = f.data.armyCompanies.get("C1");
        UUID nativeOwner = company.owner;
        UUID sameFactionOtherPlayer = UUID.randomUUID();
        f.data.lastKnownPlayerFactions.put(sameFactionOtherPlayer, company.faction);

        assertTrue(KOMECompanyCommandAuthority.canPersonallyCommand(
            f.data, company, nativeOwner));
        assertFalse(KOMECompanyCommandAuthority.canPersonallyCommand(
            f.data, company, sameFactionOtherPlayer));

        f.access.player.operator = true;
        company.owner = UUID.randomUUID();
        assertFalse("Operator status is not personal command authority",
            KOMECompanyCommandAuthority.canPersonallyCommand(
                f.data, company, f.playerId));

        UUID delegate = UUID.randomUUID();
        f.data.lastKnownPlayerFactions.put(delegate, "");
        company.temporaryController = delegate;
        company.controllerAuthority = KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;
        assertTrue("A currently valid canonical delegate remains authorized",
            KOMECompanyCommandAuthority.canPersonallyCommand(f.data, company, delegate));
    }

    @Test public void blockedRouteIsAllOrNothingAndPendingEntryIsNotRetreated() throws Exception {
        Fixture f = new Fixture(1);
        f.data.conquestTiles.get("T100").setCurrentRulingFaction("mordor");
        KOMEFormalRetreatService.Result blocked = KOMEFormalRetreatService.INSTANCE.retreat(
            f.data, f.access.player, 30L, new SuccessfulEgress());
        assertEquals(KOMEFormalRetreatService.Code.RETREAT_BLOCKED, blocked.code);
        assertTrue(f.record().getCommitments().containsKey("C1"));
        assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,
            f.data.armyMovements.get("M1").status);
        assertEquals(1, f.data.armyCompanies.get("C1").movementAllowance);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.DEPLOYED, f.receipt().getState());

        f = new Fixture(1, KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY);
        KOMEFormalRetreatService.Result recovering = KOMEFormalRetreatService.INSTANCE.retreat(
            f.data, f.access.player, 31L, new SuccessfulEgress());
        assertEquals(KOMEFormalRetreatService.Code.ENTRY_RECOVERY_PENDING, recovering.code);
    }

    @Test public void failedEgressKeepsDurablePendingObligationButCompaniesStayRetreated()
            throws Exception {
        Fixture f = new Fixture(1);
        KOMEFormalRetreatService.Result result = KOMEFormalRetreatService.INSTANCE.retreat(
            f.data, f.access.player, 30L, new FailedEgress());
        assertEquals(KOMEFormalRetreatService.Code.EGRESS_PENDING, result.code);
        assertFalse(f.record().getCommitments().containsKey("C1"));
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_EGRESS, f.receipt().getState());
        KOMEFormalRetreatService.Result replay = KOMEFormalRetreatService.INSTANCE.retreat(
            f.data, f.access.player, 60030L, new SuccessfulEgress());
        assertEquals(KOMEFormalRetreatService.Code.RETREATED, replay.code);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED, f.receipt().getState());
    }

    @Test public void incompleteLegacyRetreatPreservesStateWithoutInventingGroupAuthority()
            throws Exception {
        Fixture f = new Fixture(1);
        KOMEConflictRecord conflict = f.record();
        KOMEArmyCompany company = f.data.armyCompanies.get("C1");
        KOMEArmyMovementOrder order = f.data.armyMovements.get("M1");
        KOMEMovementRetreatService.Plan route =
            KOMEMovementRetreatService.prepare(f.data, order, true);
        ValidatedDepartureRequest departure = new ValidatedDepartureRequest("T101", "C1",
            "gondor", ExpectedConflict.at(conflict.getConflictId(), conflict.getRevision()));
        assertTrue(KOMEConflictMovementHandoff.releaseCommitment(f.data, departure, "M1",
            KOMEConflictMovementHandoff.Outcome.FORMAL_RETREAT,
            new Context(25L, "test", "legacy route-only formal retreat")).accepted());
        company.movementAllowance = 0; // Old implementation already charged the forced step.
        company.movementOrderId = order.id;
        KOMEMovementRetreatService.publish(f.data, order, route, 25L);
        assertEquals(KOMEArmyMovementOrder.WAITING_NEXT_STEP, order.status);
        assertEquals("T101", company.currentTile);
        // Both the ordinary tick and daily departure primitive must reject the missing group.
        kome.common.command.KOMECommandTroops.processMovementTick(f.data,f.access.world,26L);
        assertTrue(KOMEFormalRetreatAuthority.isQuarantined(order));
        assertFalse(KOMEMovementDayService.orderedRoutes(f.data).contains(order));
        assertFalse(KOMEMovementDayService.depart(f.data,order,true,()->{fail("No departure callback");return true;}));
        order.status=KOMEArmyMovementOrder.PENDING_SPAWN;
        kome.common.command.KOMECommandTroops.processArrivals(f.data,f.access.world,27L,false);
        assertEquals(KOMEArmyMovementOrder.PENDING_SPAWN,order.status);
        assertEquals(KOMEJoinBattleEgressService.Completion.PENDING,
            KOMEJoinBattleEgressService.INSTANCE.requestAndComplete(f.data, f.access.player,
                "JB1", 28L, "legacy retreat", new SuccessfulEgress()));

        KOMEFormalRetreatService.Result recovered = KOMEFormalRetreatService.INSTANCE.retreat(
            f.data, f.access.player, 60030L, new SuccessfulEgress());
        assertEquals(recovered.message, KOMEFormalRetreatService.Code.RETREAT_BLOCKED, recovered.code);
        assertTrue(recovered.message.contains("legacy"));
        assertEquals("T101", company.currentTile);
        assertEquals("Legacy recovery must not charge the forced step twice", 0,
            company.movementAllowance);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.PENDING_EGRESS, f.receipt().getState());
        // A late individual callback cannot erase already-latched unknown-group evidence.
        order.completedSteps=1;order.currentRouteIndex=1;
        net.minecraft.nbt.NBTTagCompound saved=new net.minecraft.nbt.NBTTagCompound();
        f.data.initializeIntegratedWorld();f.data.writeToNBT(saved);
        KOMEWorldData restored=new KOMEWorldData("legacy");restored.readFromNBT(saved);restored.ensureWritable();
        KOMEArmyMovementOrder after=restored.armyMovements.get("M1");
        assertTrue(KOMEFormalRetreatAuthority.isQuarantined(after));
        assertFalse(KOMEMovementDayService.orderedRoutes(restored).contains(after));
        assertEquals(KOMEJoinBattleEgressService.Completion.PENDING,
            KOMEJoinBattleEgressService.INSTANCE.requestAndComplete(restored,f.access.player,
                "JB1",60031L,"legacy retreat",new SuccessfulEgress()));
    }

    @Test public void schemasRemainOwnedByExistingAuthorities() {
        assertEquals(12, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        assertEquals(2, KOMEConflictPersistence.DATA_SCHEMA_VERSION);
        assertEquals(1, KOMEHiredUnitPhysicalLocator.DATA_SCHEMA_VERSION);
    }

    @Test public void remoteRetreatNeedsNoOpenDeploymentReceipt() throws Exception{
        Fixture f=new Fixture(2);f.closeReceipt();
        KOMEFormalRetreatService.Inspection inspection=KOMEFormalRetreatService.INSTANCE
            .inspectConflict(f.data,f.playerId,f.record().getConflictId());
        assertTrue(inspection.canRetreat);assertEquals(Collections.singletonList("C1"),
            inspection.companyIds);assertNull(inspection.receipt);
        SuccessfulEgress physical=new SuccessfulEgress();
        KOMEFormalRetreatService.Result result=KOMEFormalRetreatService.INSTANCE.retreat(
            f.data,f.access.player,f.record().getConflictId(),30L,physical);
        assertEquals(result.message,KOMEFormalRetreatService.Code.RETREATED,result.code);
        assertFalse(f.record().getCommitments().containsKey("C1"));
        assertEquals("T100",f.data.armyCompanies.get("C1").currentTile);
        assertEquals("Remote retreat must not teleport or egress the player",0,physical.egresses);
    }

    @Test public void noArgMultipleRemoteBattlesListsChoicesAndExactIdScopesMutation()
            throws Exception{
        Fixture f=new Fixture(2);f.closeReceipt();
        KOMEArmyCompany other=f.company("C9","T102","M9",f.playerId);
        KOMEConflictRecord otherConflict=f.commitAt(other,"M9","T102",20L);
        KOMEConflictMovementService.applyConflictHold(f.data,f.data.armyMovements.get("M9"),
            otherConflict,21L);
        KOMEFormalRetreatService.Result ambiguous=KOMEFormalRetreatService.INSTANCE.retreat(
            f.data,f.access.player,30L,new SuccessfulEgress());
        assertEquals(KOMEFormalRetreatService.Code.MULTIPLE_CONFLICTS,ambiguous.code);
        assertTrue(ambiguous.message.contains(f.record().getConflictId()));
        assertTrue(ambiguous.message.contains(otherConflict.getConflictId()));
        assertTrue(f.record().getCommitments().containsKey("C1"));
        assertTrue(f.data.getConflictService().get("T102").getCommitments().containsKey("C9"));

        KOMEFormalRetreatService.Result exact=KOMEFormalRetreatService.INSTANCE.retreat(
            f.data,f.access.player,f.record().getConflictId(),31L,new SuccessfulEgress());
        assertEquals(KOMEFormalRetreatService.Code.RETREATED,exact.code);
        assertFalse(f.record().getCommitments().containsKey("C1"));
        assertTrue("Other exact conflict remains untouched",
            f.data.getConflictService().get("T102").getCommitments().containsKey("C9"));
    }

    @Test public void pendingEntryCanBeSafelyCancelledThenRemotelyRetreated() throws Exception{
        Fixture f=new Fixture(1,KOMEJoinBattleDeploymentReceipt.State.PENDING_ENTRY);
        KOMEFormalRetreatService.Result result=KOMEFormalRetreatService.INSTANCE.retreat(
            f.data,f.access.player,f.record().getConflictId(),30L,new SuccessfulEgress(),
            new CancelEntryPhysical());
        assertEquals(result.message,KOMEFormalRetreatService.Code.RETREATED,result.code);
        assertEquals(KOMEJoinBattleDeploymentReceipt.State.CLOSED,f.receipt().getState());
        assertEquals(ClosureOutcome.ENTRY_CANCELLED,f.receipt().getClosureOutcome());
        assertFalse(f.record().getCommitments().containsKey("C1"));
    }

    @Test public void singleCompanyPendingArrivalSurvivesRestartAndFinalizesAtZeroMovement() throws Exception {
        assertPartialRestart(false, false);
    }

    @Test public void multiCompanyPartialArrivalResumesOnlyPendingMemberAfterRestart() throws Exception {
        assertPartialRestart(true, false);
    }

    @Test public void remotePartialBatchNeedsNoPhysicalReceiptAndNeverTeleports() throws Exception {
        assertPartialRestart(true, true);
    }

    @Test public void preparedManifestSurvivesRestartBeforeFirstRelease() throws Exception {
        Fixture f=new Fixture(2);
        KOMEArmyMovementOrder holder=f.data.armyMovements.get("M1");
        holder.formalRetreatBatch=new KOMEFormalRetreatBatch(UUID.randomUUID(),f.playerId,
            f.record().getConflictId(),"T101","JB1",30L,Collections.singletonList(
                new KOMEFormalRetreatBatch.Member("C1","M1",f.playerId,
                    java.util.Arrays.asList("T101","T100"),2,1,KOMEFormalRetreatBatch.Progress.PREPARED)),
            KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING,false);
        assertNull(holder.conflictRelease);assertTrue(f.record().getCommitments().containsKey("C1"));
        f.data.initializeIntegratedWorld();net.minecraft.nbt.NBTTagCompound n=new net.minecraft.nbt.NBTTagCompound();
        f.data.writeToNBT(n);KOMEWorldData restored=new KOMEWorldData("prepared");restored.readFromNBT(n);
        SuccessfulEgress physical=new SuccessfulEgress();
        KOMEFormalRetreatService.Result result=KOMEFormalRetreatService.INSTANCE.resumeBatch(restored,
            restored.armyMovements.get("M1"),f.access.player,40L,physical,KOMEFormalRetreatService.TEST_STRATEGIC,null);
        assertEquals(result.message,KOMEFormalRetreatService.Code.RETREATED,result.code);
        assertEquals(1,restored.armyCompanies.get("C1").movementAllowance);
        assertEquals(1,physical.egresses);
    }

    @Test public void completedStrategicBatchSurvivesPendingEgressRestartWithoutRepeatingSteps() throws Exception {
        Fixture f=new Fixture(0);
        KOMEFormalRetreatService.Result result=KOMEFormalRetreatService.INSTANCE.retreat(
            f.data,f.access.player,30L,new FailedEgress());
        assertEquals(result.message,KOMEFormalRetreatService.Code.EGRESS_PENDING,result.code);
        assertEquals(KOMEFormalRetreatBatch.Phase.EGRESS_HANDED_OFF,
            f.data.armyMovements.get("M1").formalRetreatBatch.phase);
        long revision=f.record().getRevision();
        net.minecraft.nbt.NBTTagCompound n=new net.minecraft.nbt.NBTTagCompound();
        f.data.initializeIntegratedWorld();f.data.writeToNBT(n);
        KOMEWorldData restored=new KOMEWorldData("return");restored.readFromNBT(n);
        SuccessfulEgress physical=new SuccessfulEgress();
        result=KOMEFormalRetreatService.INSTANCE.resumeBatch(restored,restored.armyMovements.get("M1"),
            f.access.player,60100L,physical,KOMEFormalRetreatService.TEST_STRATEGIC,null);
        assertEquals(result.message,KOMEFormalRetreatService.Code.RETREATED,result.code);
        assertEquals(revision,restored.getConflictService().get("T101").getRevision());
        assertEquals(0,restored.armyCompanies.get("C1").movementAllowance);assertEquals(1,physical.egresses);
    }

    @Test public void missingAcceptedOrderFailsClosedWithoutEgressOrAbandonment() throws Exception {
        Fixture f=new Fixture(0);PartialStrategic movement=new PartialStrategic("M1",1);
        SuccessfulEgress physical=new SuccessfulEgress();
        KOMEFormalRetreatService.INSTANCE.retreat(f.data,f.access.player,f.record().getConflictId(),
            30L,physical,movement,null);
        KOMEArmyMovementOrder holder=f.data.armyMovements.remove("M1");
        KOMEFormalRetreatService.Result result=KOMEFormalRetreatService.INSTANCE.resumeBatch(f.data,
            holder,f.access.player,40L,physical,movement,null);
        assertEquals(KOMEFormalRetreatService.Code.RETREAT_BLOCKED,result.code);
        assertTrue(result.message.contains("Exact accepted company/order"));
        assertTrue(f.record().isActive());assertEquals(0,physical.egresses);
    }

    @Test public void noArgumentRemoteSelectionCannotBypassLegacyGroupQuarantine() throws Exception {
        Fixture f=new Fixture(1);f.closeReceipt();
        KOMEArmyCompany second=f.company("C2","T101","M2",f.playerId);f.commit(second,"M2",22L);
        KOMEConflictMovementService.applyConflictHold(f.data,f.data.armyMovements.get("M2"),f.record(),24L);
        KOMEConflictRecord conflict=f.record();
        assertTrue(KOMEConflictMovementHandoff.releaseCommitment(f.data,
            new ValidatedDepartureRequest("T101","C1","gondor",
                ExpectedConflict.at(conflict.getConflictId(),conflict.getRevision())),
            "M1",KOMEConflictMovementHandoff.Outcome.FORMAL_RETREAT,
            new Context(25L,"test","interrupted legacy release")).accepted());
        long revision=f.record().getRevision();
        KOMEFormalRetreatService.Result result=KOMEFormalRetreatService.INSTANCE.retreat(
            f.data,f.access.player,30L,new SuccessfulEgress());
        assertEquals(result.message,KOMEFormalRetreatService.Code.RETREAT_BLOCKED,result.code);
        assertTrue(result.message.contains("legacy"));
        assertEquals(revision,f.record().getRevision());
        assertNull(f.data.armyMovements.get("M2").formalRetreatBatch);
        assertNull(f.data.armyMovements.get("M2").conflictRelease);
    }

    @Test public void pledgeCleanupRetainsCompletedHolderAndPendingMemberAcrossRestart() throws Exception {
        Fixture f=new Fixture(0);f.closeReceipt();
        KOMEArmyCompany second=f.company("C2","T101","M2",f.playerId);
        f.commit(second,"M2",22L);
        KOMEConflictMovementService.applyConflictHold(f.data,f.data.armyMovements.get("M2"),f.record(),24L);
        PartialStrategic movement=new PartialStrategic("M2",2);
        KOMEFormalRetreatService.INSTANCE.retreat(f.data,f.access.player,f.record().getConflictId(),
            30L,new SuccessfulEgress(),movement,null);
        KOMEFormalRetreatBatch batch=f.data.armyMovements.get("M1").formalRetreatBatch;
        assertEquals(KOMEFormalRetreatBatch.Progress.COMPLETE,batch.members.get(0).progress);
        KOMEPledgeReleaseService.release(f.data,f.playerId,"Owner","gondor","mordor",40L,"test");
        assertNotNull(f.data.armyMovements.get("M1"));
        assertNotNull(f.data.armyMovements.get("M2"));
        assertNotNull(f.data.armyCompanies.get("C1"));
        assertNotNull(f.data.armyCompanies.get("C2"));
        net.minecraft.nbt.NBTTagCompound saved=new net.minecraft.nbt.NBTTagCompound();
        f.data.initializeIntegratedWorld();f.data.writeToNBT(saved);
        KOMEWorldData restored=new KOMEWorldData("protected");restored.readFromNBT(saved);restored.ensureWritable();
        assertEquals(batch.id,restored.armyMovements.get("M1").formalRetreatBatch.id);
        movement.allowArrival=true;
        assertEquals(KOMEFormalRetreatService.Code.RETREATED,
            KOMEFormalRetreatService.INSTANCE.resumeBatch(restored,restored.armyMovements.get("M1"),
                f.access.player,50L,new SuccessfulEgress(),movement,null).code);
        KOMEPledgeReleaseService.release(restored,f.playerId,"Owner","gondor","mordor",60L,"test");
        assertFalse(restored.armyMovements.containsKey("M1"));
        assertFalse(restored.armyMovements.containsKey("M2"));
    }

    @Test public void ownerBatchRejectsDelegateBeforeMutation() throws Exception { assertCompetingCommander(false); }
    @Test public void delegateBatchRejectsOwnerBeforeMutation() throws Exception { assertCompetingCommander(true); }
    private void assertCompetingCommander(boolean delegateFirst) throws Exception {
        Fixture f=new Fixture(1);f.closeReceipt();
        KOMEAccessFixture delegate=new KOMEAccessFixture();
        KOMEArmyCompany company=f.data.armyCompanies.get("C1");
        company.temporaryController=delegate.player.id;
        company.controllerAuthority=KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;
        f.data.lastKnownPlayerFactions.put(delegate.player.id,"");
        assertTrue(KOMECompanyCommandAuthority.canPersonallyCommand(f.data,company,delegate.player.id));
        KOMEFormalRetreatBatch batch=preparedBatch(f,delegateFirst?delegate.player.id:f.playerId,"C1","M1");
        f.data.armyMovements.get("M1").formalRetreatBatch=batch;
        long revision=f.record().getRevision();
        KOMEFormalRetreatService.Result result=KOMEFormalRetreatService.INSTANCE.retreat(f.data,
            delegateFirst?f.access.player:delegate.player,f.record().getConflictId(),30L,
            new SuccessfulEgress(),KOMEFormalRetreatService.TEST_STRATEGIC,null);
        assertEquals(result.message,KOMEFormalRetreatService.Code.RETREAT_BLOCKED,result.code);
        assertTrue(result.message.contains("already part"));
        assertSame(batch,f.data.armyMovements.get("M1").formalRetreatBatch);
        assertNull(f.data.armyMovements.get("M1").conflictRelease);
        assertEquals(revision,f.record().getRevision());assertEquals(1,company.movementAllowance);
    }

    @Test public void sameCommanderResumesSameBatchWithoutAcceptingDuplicate() throws Exception {
        Fixture f=new Fixture(1);f.closeReceipt();
        KOMEFormalRetreatBatch batch=preparedBatch(f,f.playerId,"C1","M1");
        f.data.armyMovements.get("M1").formalRetreatBatch=batch;
        assertEquals(KOMEFormalRetreatService.Code.RETREATED,
            KOMEFormalRetreatService.INSTANCE.retreat(f.data,f.access.player,f.record().getConflictId(),
                30L,new SuccessfulEgress(),KOMEFormalRetreatService.TEST_STRATEGIC,null).code);
        assertEquals(batch.id,f.data.armyMovements.get("M1").formalRetreatBatch.id);
        assertEquals(0,f.data.armyCompanies.get("C1").movementAllowance);
    }

    @Test public void disjointCommanderBatchDoesNotBlockNewRetreat() throws Exception {
        Fixture f=new Fixture(1);f.closeReceipt();UUID other=UUID.randomUUID();
        KOMEArmyCompany second=f.company("C2","T101","M2",other);f.commit(second,"M2",22L);
        KOMEConflictMovementService.applyConflictHold(f.data,f.data.armyMovements.get("M2"),f.record(),24L);
        KOMEFormalRetreatBatch foreign=preparedBatch(f,other,"C2","M2");
        f.data.armyMovements.get("M2").formalRetreatBatch=foreign;
        assertEquals(KOMEFormalRetreatService.Code.RETREATED,
            KOMEFormalRetreatService.INSTANCE.retreat(f.data,f.access.player,f.record().getConflictId(),
                30L,new SuccessfulEgress(),KOMEFormalRetreatService.TEST_STRATEGIC,null).code);
        assertSame(foreign,f.data.armyMovements.get("M2").formalRetreatBatch);
        assertTrue(f.record().getCommitments().containsKey("C2"));
    }

    @Test public void overlappingAuthorityCannotSatisfyAnotherBatchRecovery() throws Exception {
        Fixture f=new Fixture(1);f.closeReceipt();
        KOMEArmyCompany second=f.company("C2","T101","M2",f.playerId);f.commit(second,"M2",22L);
        KOMEFormalRetreatBatch first=preparedBatch(f,f.playerId,"C1","M1");
        KOMEFormalRetreatBatch.Member overlap=first.members.get(0);
        KOMEFormalRetreatBatch foreign=new KOMEFormalRetreatBatch(UUID.randomUUID(),UUID.randomUUID(),
            f.record().getConflictId(),"T101","",25L,java.util.Arrays.asList(
                new KOMEFormalRetreatBatch.Member("C1","M2",f.playerId,overlap.route,1,0,overlap.progress)),
            KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING,false);
        f.data.armyMovements.get("M1").formalRetreatBatch=first;
        f.data.armyMovements.get("M2").formalRetreatBatch=foreign;
        long revision=f.record().getRevision();
        assertEquals(KOMEFormalRetreatService.Code.RETREAT_BLOCKED,
            KOMEFormalRetreatService.INSTANCE.resumeBatch(f.data,f.data.armyMovements.get("M1"),
                f.access.player,30L,new SuccessfulEgress(),KOMEFormalRetreatService.TEST_STRATEGIC,null).code);
        assertNull(f.data.armyMovements.get("M1").conflictRelease);
        assertEquals(revision,f.record().getRevision());
    }

    private KOMEFormalRetreatBatch preparedBatch(Fixture f,UUID commander,String company,String order){
        return new KOMEFormalRetreatBatch(UUID.randomUUID(),commander,f.record().getConflictId(),"T101","",
            25L,java.util.Arrays.asList(new KOMEFormalRetreatBatch.Member(company,order,
                f.data.armyCompanies.get(company).owner,java.util.Arrays.asList("T101","T100"),1,0,
                KOMEFormalRetreatBatch.Progress.PREPARED)),KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING,false);
    }

    private void assertPartialRestart(boolean multiple, boolean remote) throws Exception {
        Fixture f=new Fixture(0);
        if(remote)f.closeReceipt();
        if(multiple){
            KOMEArmyCompany second=f.company("C2","T101","M2",f.playerId);
            second.movementAllowance=1;
            f.commit(second,"M2",22L);
            KOMEConflictMovementService.applyConflictHold(f.data,f.data.armyMovements.get("M2"),f.record(),24L);
        }
        PartialStrategic movement=new PartialStrategic(multiple?"M2":"M1",multiple?2:1);
        SuccessfulEgress physical=new SuccessfulEgress();
        KOMEFormalRetreatService.Result first=KOMEFormalRetreatService.INSTANCE.retreat(
            f.data,f.access.player,f.record().getConflictId(),30L,physical,movement,null);
        assertEquals(first.message,KOMEFormalRetreatService.Code.RETREAT_BLOCKED,first.code);
        assertTrue(movement.manifestSeen);
        assertEquals(0,physical.egresses);
        assertTrue(f.record().isActive());
        assertTrue(f.record().getCommitments().isEmpty());
        if(multiple)assertEquals("T100",f.data.armyCompanies.get("C1").currentTile);
        net.minecraft.nbt.NBTTagCompound saved=new net.minecraft.nbt.NBTTagCompound();
        f.data.initializeIntegratedWorld();f.data.writeToNBT(saved);
        KOMEWorldData restarted=new KOMEWorldData("restart");restarted.readFromNBT(saved);
        restarted.ensureWritable();
        KOMEArmyMovementOrder holder=restarted.armyMovements.get("M1");
        assertNotNull(holder.formalRetreatBatch);
        assertEquals(remote?"":"JB1",holder.formalRetreatBatch.receiptId);
        movement.allowArrival=true;
        KOMEFormalRetreatService.Result resumed=KOMEFormalRetreatService.INSTANCE.resumeBatch(
            restarted,holder,f.access.player,40L,physical,movement,null);
        assertEquals(resumed.message,KOMEFormalRetreatService.Code.RETREATED,resumed.code);
        assertTrue(holder.formalRetreatBatch.finalized());
        assertFalse(restarted.getConflictService().get("T101").isActive());
        assertEquals("mordor",restarted.conquestTiles.get("T101").currentRulingFaction());
        assertEquals("T100",restarted.armyCompanies.get("C1").currentTile);
        assertEquals(0,restarted.armyCompanies.get("C1").movementAllowance);
        if(multiple){assertEquals("T100",restarted.armyCompanies.get("C2").currentTile);
            assertEquals(0,restarted.armyCompanies.get("C2").movementAllowance);}
        assertEquals(remote?0:1,physical.egresses);
        assertEquals(multiple?2:1,movement.executions);
        long revision=restarted.getConflictService().get("T101").getRevision();
        KOMEFormalRetreatService.INSTANCE.resumeBatch(restarted,holder,f.access.player,50L,physical,movement,null);
        assertEquals(revision,restarted.getConflictService().get("T101").getRevision());
        assertEquals(remote?0:1,physical.egresses);
        assertEquals(multiple?2:1,movement.executions);
    }

    private static final class PartialStrategic implements KOMEFormalRetreatService.StrategicAccess {
        final String pending; final int count; boolean allowArrival,manifestSeen; int executions;
        PartialStrategic(String id,int count){pending=id;this.count=count;}
        public KOMEFormalRetreatService.StrategicPreparation prepare(KOMEWorldData data,
                EntityPlayerMP player,KOMEArmyMovementOrder order,String tile,long now){
            return KOMEFormalRetreatService.TEST_STRATEGIC.prepare(data,player,order,tile,now);
        }
        public boolean execute(KOMEWorldData data,KOMEFormalRetreatService.StrategicPreparation step,long now){
            KOMEFormalRetreatBatch batch=data.armyMovements.get("M1").formalRetreatBatch;
            assertNotNull(batch);assertEquals(count,batch.members.size());manifestSeen=true;executions++;
            if(pending.equals(step.orderId)){
                data.armyMovements.get(pending).status=KOMEArmyMovementOrder.PENDING_SPAWN;return true;
            }
            return KOMEFormalRetreatService.TEST_STRATEGIC.execute(data,step,now);
        }
        public void complete(KOMEWorldData data,EntityPlayerMP player,long now,java.util.Set<String> acceptedOrders){
            if(!allowArrival)return;
            assertTrue(acceptedOrders.contains(pending));
            KOMEArmyMovementOrder order=data.armyMovements.get(pending);
            if(order.completedSteps>0)return;
            order.status=KOMEArmyMovementOrder.WAITING_NEXT_STEP;
            assertTrue(KOMEFormalRetreatService.TEST_STRATEGIC.execute(data,
                prepare(data,player,order,order.nextTile,now),now));
        }
        public void release(KOMEFormalRetreatService.StrategicPreparation step){}
    }

    private static class SuccessfulEgress implements KOMEJoinBattleEgressService.PhysicalAccess {
        int egresses;
        @Override public EntityPlayerMP findOnline(UUID playerId) { return null; }
        @Override public KOMEJoinBattleEgressService.Preparation prepare(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt) {
            return KOMEJoinBattleEgressService.Preparation.playerOnly(null);
        }
        @Override public KOMEJoinBattleEgressService.PhysicalResult egress(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt,
                KOMEJoinBattleEgressService.Preparation preparation) {
            egresses++;return KOMEJoinBattleEgressService.PhysicalResult.completed(null);
        }
    }

    private static final class CancelEntryPhysical implements KOMEJoinBattleEntryService.PhysicalAccess{
        @Override public long now(){return 30L;}
        @Override public KOMEJoinBattleEntryService.Preparation prepare(KOMEWorldData data,
                EntityPlayerMP player,KOMEArmyCompany company,String tile,String conflict,long revision){
            return KOMEJoinBattleEntryService.Preparation.denied(
                KOMEJoinBattleService.Reason.INVALID_REQUEST);
        }
        @Override public boolean isAtDestination(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt){return false;}
        @Override public KOMEJoinBattleService.Reason preflight(KOMEWorldData data,
                EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt){
            return KOMEJoinBattleService.Reason.ALLOWED;
        }
        @Override public KOMEJoinBattleEntryService.PhysicalResult deploy(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt){return KOMEJoinBattleEntryService.PhysicalResult
                    .pending(KOMEJoinBattleService.Reason.ENTRY_ALREADY_IN_PROGRESS);}
        @Override public KOMEJoinBattleEntryService.PhysicalResult rollback(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt){return KOMEJoinBattleEntryService.PhysicalResult
                    .rolledBack(MountDisposition.SEPARATED);}
    }
    private static final class FailedEgress extends SuccessfulEgress {
        @Override public KOMEJoinBattleEgressService.PhysicalResult egress(EntityPlayerMP player,
                KOMEJoinBattleDeploymentReceipt receipt,
                KOMEJoinBattleEgressService.Preparation preparation) {
            return KOMEJoinBattleEgressService.PhysicalResult.failed();
        }
    }

    private static final class Fixture {
        final KOMEAccessFixture access;
        final KOMEWorldData data;
        final UUID playerId;
        Fixture(int allowance) throws Exception {
            this(allowance, KOMEJoinBattleDeploymentReceipt.State.DEPLOYED);
        }
        Fixture(int allowance, KOMEJoinBattleDeploymentReceipt.State receiptState) throws Exception {
            access = new KOMEAccessFixture(); data = access.data; playerId = access.player.id;
            tile("T100", "gondor"); tile("T101", "mordor"); tile("T102", "gondor");
            KOMEArmyCompany primary = company("C1", "T101", "M1", playerId);
            primary.movementAllowance = allowance;
            if (allowance == 2) {
                primary.mountedPopulation = primary.totalPopulation;
                primary.groundPopulation = 0;
                data.hiredUnits.get(primary.units.get(0)).mounted = true;
            }
            KOMEConflictRecord conflict = ok(data.getConflictService().start("T101", KOMEConflictRecord.State.ORDINARY,
                ExpectedConflict.absent(), Collections.<GarrisonSeed>emptyList(), context(10L)));
            conflict = ok(data.getConflictService().beginFactionParticipation("T101",
                expected(conflict), "gondor", context(11L)));
            conflict = commit(primary, "M1", 12L);
            KOMEConflictMovementService.applyConflictHold(data, data.armyMovements.get("M1"),
                conflict, 13L);
            conflict = ok(data.getConflictService().registerPlayer("T101", expected(conflict),
                playerId, "gondor", context(14L)));
            KOMEJoinBattleDeploymentReceipt receipt = KOMEJoinBattleDeploymentReceipt.builder()
                .receiptId("JB1").actionToken("formal-retreat-token").playerId(playerId)
                .conflictId(conflict.getConflictId()).tileId("T101")
                .acceptedConflictRevision(conflict.getRevision()).factionId("gondor")
                .selectedCompanyId("C1").state(receiptState)
                .createdAtMillis(15L).updatedAtMillis(16L)
                .returnAnchor(new Pose(0, 1D, 65D, 1D, 0F, 0F))
                .deploymentDestination(new Pose(0, 2D, 65D, 2D, 0F, 0F))
                .participationRecovery(ParticipationRecovery.PREEXISTING_ACTIVE)
                .deployedAtMillis(receiptState == KOMEJoinBattleDeploymentReceipt.State.DEPLOYED
                    ? Long.valueOf(16L) : null).build();
            data.getJoinBattleDeploymentReceipts().publishNew(receipt);
        }
        KOMEConflictRecord commit(KOMEArmyCompany company, String movementId, long now) {
            KOMEConflictRecord current = record();
            if (!current.getFactionParticipation().containsKey(company.faction))
                current = ok(data.getConflictService().beginFactionParticipation("T101",
                    expected(current), company.faction, context(now)));
            return ok(data.getConflictService().commit("T101", expected(current),
                new CommitmentInput(company.id, KOMEHiredUnitClass.CAMPAIGN,
                    EntryOrigin.LEGAL_ARRIVAL, movementId), context(now + 1L)));
        }
        KOMEConflictRecord commitAt(KOMEArmyCompany company,String movementId,String tile,long now){
            KOMEConflictRecord conflict=ok(data.getConflictService().start(tile,
                KOMEConflictRecord.State.ORDINARY,ExpectedConflict.absent(),
                Collections.<GarrisonSeed>emptyList(),context(now)));
            conflict=ok(data.getConflictService().beginFactionParticipation(tile,expected(conflict),
                company.faction,context(now+1L)));
            return ok(data.getConflictService().commit(tile,expected(conflict),
                new CommitmentInput(company.id,KOMEHiredUnitClass.CAMPAIGN,
                    EntryOrigin.LEGAL_ARRIVAL,movementId),context(now+2L)));
        }
        KOMEArmyCompany company(String id, String tile, String movementId, UUID owner) {
            KOMEArmyCompany company = new KOMEArmyCompany();
            company.id=id;company.name=id;company.owner=owner;company.ownerName="Owner";
            company.faction=company.nativeFaction="gondor";company.currentTile=tile;
            company.status=KOMEArmyCompany.MOVING;company.movementOrderId=movementId;
            company.movementAllowanceInitialized=true;company.movementAllowance=1;
            company.movementBoundarySchedule="America/Chicago@06:00";
            company.movementBoundaryMillis=java.time.Instant.parse("2024-01-01T12:00:00Z").toEpochMilli();
            KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
            unit.entity=UUID.randomUUID();unit.owner=owner;unit.sourcePlayer=owner;
            unit.companyId=id;unit.companyName=id;unit.currentTile=tile;unit.sourceTileId="T100";
            unit.unitFaction=unit.populationOwningFaction="gondor";
            unit.type=KOMEPopulationType.OFFENSIVE;unit.assignPersistedUnitClass(KOMEHiredUnitClass.CAMPAIGN);
            unit.cost=unit.baseCost=unit.populationSpent=20;unit.movementOrderId=movementId;
            company.units.add(unit.entity);company.totalPopulation=company.groundPopulation=20;
            data.armyCompanies.put(id,company);data.hiredUnits.put(unit.entity,unit);
            data.lastKnownPlayerFactions.put(owner,"gondor");
            KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();
            order.id=movementId;order.companyId=id;order.companyName=id;order.owner=owner;
            order.ownerFaction="gondor";order.currentTile=tile;order.originTile="T100";
            order.destinationTile=order.finalDestinationTile=tile;order.status=KOMEArmyMovementOrder.WAITING_NEXT_STEP;
            order.hostileAttackDestination=tile;
            order.routeTiles.add("T100");order.routeTiles.add(tile);
            order.traveledRouteTiles.add("T100");order.traveledRouteTiles.add(tile);
            order.currentRouteIndex=order.nextRouteIndex=order.finalRouteIndex=1;
            order.currentStepOriginTile=tile;order.units.add(unit.entity);
            data.armyMovements.put(movementId,order);
            return company;
        }
        void tile(String id,String faction){KOMEConquestTile tile=new KOMEConquestTile(id);
            tile.setCurrentRulingFaction(faction);data.conquestTiles.put(id,tile);}
        KOMEConflictRecord record(){return data.getConflictService().get("T101");}
        KOMEJoinBattleDeploymentReceipt receipt(){return data.getJoinBattleDeploymentReceipts().get("JB1");}
        void closeReceipt(){
            KOMEJoinBattleDeploymentReceipt open=receipt();
            KOMEJoinBattleDeploymentReceipt egress=KOMEJoinBattleDeploymentReceipt.copyOf(open)
                .state(KOMEJoinBattleDeploymentReceipt.State.PENDING_EGRESS)
                .updatedAtMillis(20L).egressRequestedAtMillis(Long.valueOf(20L))
                .egressReason("test fixture closure").build();
            data.getJoinBattleDeploymentReceipts().replace(egress);
            KOMEJoinBattleDeploymentReceipt closed=KOMEJoinBattleDeploymentReceipt.copyOf(egress)
                .state(KOMEJoinBattleDeploymentReceipt.State.CLOSED).updatedAtMillis(21L)
                .closedAtMillis(Long.valueOf(21L)).closureOutcome(ClosureOutcome.EGRESS_COMPLETED)
                .build();
            data.getJoinBattleDeploymentReceipts().replace(closed);
        }
        static ExpectedConflict expected(KOMEConflictRecord record){return ExpectedConflict.at(record.getConflictId(),record.getRevision());}
        static Context context(long at){return new Context(at,"test","formal retreat test");}
        static KOMEConflictRecord ok(KOMEConflictService.Result result){assertTrue(result.reason,result.isSuccess());return result.record;}
    }
}
