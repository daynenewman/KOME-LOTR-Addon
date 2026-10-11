package kome.common.data;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import kome.common.KOMEAccessFixture;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import org.junit.After;
import org.junit.Test;

import static kome.common.data.KOMEConflictContracts.Context;
import static kome.common.data.KOMEConflictContracts.ExpectedConflict;
import static kome.common.data.KOMEConflictContracts.GarrisonSeed;
import static kome.common.data.KOMEConflictRecord.EntryOrigin;
import static kome.common.data.KOMEConflictRecord.PlayerStatus;
import static kome.common.data.KOMEConflictRecord.State;
import static kome.common.data.KOMEJoinBattleService.Reason;
import static org.junit.Assert.*;
import kome.common.network.KOMEPacketJoinBattleSelectionResult;

public class KOMEJoinBattleServiceTest {
    @After public void resetRelations() {
        LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR, LOTRFaction.ROHAN,
            LOTRFactionRelations.Relation.NEUTRAL);
    }

    @Test public void coherentOwnFactionCommittedCampaignCompanyIsAllowed() throws Exception {
        Fixture f = new Fixture(LOTRFaction.GONDOR);
        f.participate("gondor");
        f.company("C1", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        f.commit("C1", EntryOrigin.LEGAL_ARRIVAL);

        KOMEJoinBattleService.Projection result = f.evaluate();

        assertTrue(result.isAllowed());
        assertEquals(Reason.ALLOWED, result.reason);
        assertEquals("gondor", result.playerFactionId);
        assertEquals(f.record.getConflictId(), result.conflictId);
        assertEquals(f.record.getRevision(), result.conflictRevision);
        assertEquals(1, result.eligibleCompanies.size());
        assertEquals("C1", result.eligibleCompanies.get(0).companyId);
        assertEquals(1, result.eligibleCompanies.get(0).campaignCombatMemberCount);
    }

    @Test public void noCommittedEmptyOrdinaryFarmhandAndNoncombatCompaniesDeny()
            throws Exception {
        Fixture none = new Fixture(LOTRFaction.GONDOR);
        none.participate("gondor");
        assertReason(Reason.NO_ELIGIBLE_COMPANY, none.evaluate());

        Fixture empty = new Fixture(LOTRFaction.GONDOR);
        empty.participate("gondor");
        empty.emptyCompany("C1", "gondor", "T100");
        empty.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.NO_ELIGIBLE_COMPANY, empty.evaluate());
        assertEquals(Reason.COMPANY_HAS_NO_CAMPAIGN_COMBAT_UNITS,
            empty.select("C1").reason);

        for (UnitShape shape : Arrays.asList(
                new UnitShape(KOMEHiredUnitClass.ORDINARY,
                    KOMEPopulationType.OFFENSIVE, false),
                new UnitShape(KOMEHiredUnitClass.CAMPAIGN,
                    KOMEPopulationType.OFFENSIVE, true),
                new UnitShape(KOMEHiredUnitClass.CAMPAIGN,
                    KOMEPopulationType.DEFENSIVE, false))) {
            Fixture f = new Fixture(LOTRFaction.GONDOR);
            f.participate("gondor");
            f.company("C1", "gondor", "T100", shape.unitClass,
                shape.type, shape.farmhand);
            f.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
            assertReason(Reason.NO_ELIGIBLE_COMPANY, f.evaluate());
            assertEquals(Reason.COMPANY_HAS_NO_CAMPAIGN_COMBAT_UNITS,
                f.select("C1").reason);
        }
    }

    @Test public void malformedTileAndAbsentConflictDenyWithoutCreatingAuthority()
            throws Exception {
        KOMEAccessFixture access = new KOMEAccessFixture();
        access.pledge(LOTRFaction.GONDOR);
        access.data.setDirty(false);

        assertReason(Reason.INVALID_TILE, KOMEJoinBattleService.INSTANCE.evaluate(
            access.data, access.player, "not-a-tile"));
        assertReason(Reason.NO_ACTIVE_CONFLICT, KOMEJoinBattleService.INSTANCE.evaluate(
            access.data, access.player, "T100"));
        assertTrue(access.data.getConflictService().records().isEmpty());
        assertFalse(access.data.isDirty());
    }

    @Test public void physicalOrRegistryPresenceWithoutConflictCommitmentNeverQualifies()
            throws Exception {
        Fixture f = new Fixture(LOTRFaction.GONDOR);
        f.participate("gondor");
        f.company("C1", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);

        assertReason(Reason.NO_ELIGIBLE_COMPANY, f.evaluate());
        assertEquals(Reason.COMPANY_NOT_COMMITTED, f.select("C1").reason);
    }

    @Test public void factionMustOwnItsCommitmentButDiplomaticSideIsIrrelevant()
            throws Exception {
        LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR, LOTRFaction.ROHAN,
            LOTRFactionRelations.Relation.ALLY);
        Fixture noOwnCompany = new Fixture(LOTRFaction.ROHAN);
        noOwnCompany.participate("rohan");
        noOwnCompany.participate("gondor");
        noOwnCompany.company("C1", "gondor", "T100",
            KOMEHiredUnitClass.CAMPAIGN, KOMEPopulationType.OFFENSIVE, false);
        noOwnCompany.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.NO_ELIGIBLE_COMPANY, noOwnCompany.evaluate());
        assertEquals(Reason.WRONG_FACTION_COMPANY,
            noOwnCompany.select("C1").reason);

        Fixture relief = new Fixture(LOTRFaction.ROHAN, false);
        relief.start(State.ENCIRCLEMENT, Collections.<GarrisonSeed>emptyList());
        relief.participate("rohan");
        relief.company("C2", "rohan", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        relief.commit("C2", EntryOrigin.RELIEF);
        assertReason(Reason.ALLOWED, relief.evaluate());

        Fixture attacker = new Fixture(LOTRFaction.GONDOR);
        attacker.participate("gondor");
        attacker.company("C3", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        attacker.commit("C3", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.ALLOWED, attacker.evaluate());
    }

    @Test public void originalGarrisonDefenderCommitmentQualifies() throws Exception {
        Fixture f = new Fixture(LOTRFaction.GONDOR, false);
        KOMEArmyCompany company = f.company("C1", "gondor", "T100",
            KOMEHiredUnitClass.CAMPAIGN, KOMEPopulationType.OFFENSIVE, false);
        f.start(State.ENCIRCLEMENT, Collections.singletonList(new GarrisonSeed(
            "C1", KOMEHiredUnitClass.CAMPAIGN,
            Collections.singleton(company.units.get(0)))));
        f.participate("gondor");

        assertReason(Reason.ALLOWED, f.evaluate());
    }

    @Test public void missingIncoherentWrongTileAndMixedCompaniesFailClosed()
            throws Exception {
        Fixture missing = new Fixture(LOTRFaction.GONDOR);
        missing.participate("gondor");
        missing.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.NO_ELIGIBLE_COMPANY, missing.evaluate());
        assertEquals(Reason.COMPANY_INCOHERENT, missing.select("C1").reason);

        Fixture incoherent = new Fixture(LOTRFaction.GONDOR);
        incoherent.participate("gondor");
        KOMEArmyCompany broken = incoherent.company("C1", "gondor", "T100",
            KOMEHiredUnitClass.CAMPAIGN, KOMEPopulationType.OFFENSIVE, false);
        incoherent.data.hiredUnits.get(broken.units.get(0)).owner = UUID.randomUUID();
        incoherent.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.NO_ELIGIBLE_COMPANY, incoherent.evaluate());
        assertEquals(Reason.COMPANY_INCOHERENT, incoherent.select("C1").reason);

        Fixture wrongTile = new Fixture(LOTRFaction.GONDOR);
        wrongTile.participate("gondor");
        wrongTile.company("C1", "gondor", "T101", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        wrongTile.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.NO_ELIGIBLE_COMPANY, wrongTile.evaluate());
        assertEquals(Reason.COMPANY_INCOHERENT, wrongTile.select("C1").reason);

        Fixture mixed = new Fixture(LOTRFaction.GONDOR);
        mixed.participate("gondor");
        KOMEArmyCompany company = mixed.company("C1", "gondor", "T100",
            KOMEHiredUnitClass.CAMPAIGN, KOMEPopulationType.OFFENSIVE, false);
        KOMEHiredUnitRecord ordinary = mixed.member(company, "gondor", "T100",
            KOMEHiredUnitClass.ORDINARY, KOMEPopulationType.OFFENSIVE, false);
        mixed.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.NO_ELIGIBLE_COMPANY, mixed.evaluate());
        assertEquals(Reason.COMPANY_INCOHERENT, mixed.select("C1").reason);
        company.units.remove(ordinary.entity);
        mixed.data.hiredUnits.remove(ordinary.entity);
        company.totalPopulation -= ordinary.cost;
        company.groundPopulation -= ordinary.cost;
        assertReason(Reason.ALLOWED, mixed.evaluate());
    }

    @Test public void unpledgedAndNonparticipatingPlayersAreDenied() throws Exception {
        Fixture unpledged = new Fixture(null);
        unpledged.participate("gondor");
        unpledged.company("C1", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        unpledged.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.UNPLEDGED, unpledged.evaluate());

        Fixture notParticipating = new Fixture(LOTRFaction.GONDOR);
        notParticipating.company("C1", "gondor", "T100",
            KOMEHiredUnitClass.CAMPAIGN, KOMEPopulationType.OFFENSIVE, false);
        notParticipating.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.FACTION_NOT_PARTICIPATING,
            notParticipating.evaluate());
    }

    @Test public void currentPledgeOverridesHistoricalRegistrationFaction()
            throws Exception {
        Fixture f = new Fixture(LOTRFaction.GONDOR);
        f.participate("gondor");
        f.participate("rohan");
        f.registerPlayer("gondor");
        f.company("C1", "rohan", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        f.commit("C1", EntryOrigin.EXTERIOR_ARRIVAL);
        f.access.pledge(LOTRFaction.ROHAN);

        KOMEJoinBattleService.Projection result = f.evaluate();
        assertReason(Reason.ALLOWED, result);
        assertEquals("rohan", result.playerFactionId);
        assertEquals("gondor", f.record.getPlayers().get(f.access.player.id)
            .factionAtRegistration);
    }

    @Test public void activeParticipationReevaluatesButWithdrawnIsTerminal()
            throws Exception {
        Fixture active = new Fixture(LOTRFaction.GONDOR);
        active.participate("gondor");
        active.registerPlayer("gondor");
        active.company("C1", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        active.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.ALLOWED, active.evaluate());

        Fixture withdrawn = new Fixture(LOTRFaction.GONDOR);
        withdrawn.participate("gondor");
        withdrawn.registerPlayer("gondor");
        withdrawn.withdrawPlayer();
        withdrawn.company("C1", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        withdrawn.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.WITHDRAWN, withdrawn.evaluate());
    }

    @Test public void lossAndLaterReplacementOfCompanyNeverWritesWithdrawn()
            throws Exception {
        Fixture f = new Fixture(LOTRFaction.GONDOR);
        f.participate("gondor");
        f.registerPlayer("gondor");
        KOMEArmyCompany first = f.company("C1", "gondor", "T100",
            KOMEHiredUnitClass.CAMPAIGN, KOMEPopulationType.OFFENSIVE, false);
        f.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertReason(Reason.ALLOWED, f.evaluate());
        long revision = f.record.getRevision();

        UUID lost = first.units.remove(0);
        f.data.hiredUnits.remove(lost);
        first.totalPopulation = first.groundPopulation = 0;
        assertReason(Reason.NO_ELIGIBLE_COMPANY, f.evaluate());
        assertEquals(PlayerStatus.ACTIVE,
            f.record.getPlayers().get(f.access.player.id).status);
        assertEquals(revision, f.record.getRevision());

        f.company("C2", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        f.commit("C2", EntryOrigin.EXTERIOR_ARRIVAL);
        assertReason(Reason.ALLOWED, f.evaluate());
        assertEquals(PlayerStatus.ACTIVE,
            f.record.getPlayers().get(f.access.player.id).status);
    }

    @Test public void emergencyDefenseAloneDoesNotQualifyButCampaignCompanyDoes()
            throws Exception {
        Fixture f = new Fixture(LOTRFaction.GONDOR);
        f.participate("gondor");
        f.addEmergencyDefender("gondor");
        assertReason(Reason.NO_ELIGIBLE_COMPANY, f.evaluate());

        f.company("C1", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        f.commit("C1", EntryOrigin.EXTERIOR_ARRIVAL);
        assertReason(Reason.ALLOWED, f.evaluate());
    }

    @Test public void endedAndStaleConflictRequestsDeny() throws Exception {
        Fixture stale = new Fixture(LOTRFaction.GONDOR);
        stale.participate("gondor");
        stale.company("C1", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        ExpectedConflict old = stale.expected();
        stale.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertEquals(Reason.STALE_CONFLICT,
            stale.select(old, "C1").reason);

        Fixture ended = new Fixture(LOTRFaction.GONDOR);
        ended.participate("gondor");
        ended.company("C1", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        ended.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        ended.record = ok(ended.data.getConflictService().end("T100",
            ended.expected(), ended.context()));
        assertReason(Reason.NO_ACTIVE_CONFLICT, ended.evaluate());
    }

    @Test public void selectedCompanyValidationIsExactAndProjectionIsStableImmutable()
            throws Exception {
        Fixture f = new Fixture(LOTRFaction.GONDOR);
        f.participate("gondor");
        f.company("C2", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        f.commit("C2", EntryOrigin.LEGAL_ARRIVAL);
        f.company("C10", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        f.commit("C10", EntryOrigin.EXTERIOR_ARRIVAL);

        KOMEJoinBattleService.SelectionResult selected = f.select("C2");
        assertTrue(selected.isAllowed());
        assertEquals("C2", selected.selectedCompany.companyId);
        assertEquals(Reason.COMPANY_NOT_COMMITTED, f.select("C999").reason);

        KOMEJoinBattleService.Projection projection = f.evaluate();
        assertEquals(Arrays.asList("C10", "C2"), Arrays.asList(
            projection.eligibleCompanies.get(0).companyId,
            projection.eligibleCompanies.get(1).companyId));
        try {
            projection.eligibleCompanies.clear();
            fail("Eligible-company projection must be immutable.");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    @Test public void evaluationIsReadOnlyAndDoesNotRegisterOrWithdrawPlayer()
            throws Exception {
        Fixture f = new Fixture(LOTRFaction.GONDOR);
        f.participate("gondor");
        f.company("C1", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        f.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        f.data.setDirty(false);
        int audits = f.data.centralAudit.size();
        long revision = f.record.getRevision();

        assertReason(Reason.ALLOWED, f.evaluate());

        assertTrue(f.record.getPlayers().isEmpty());
        assertEquals(revision, f.data.getConflictService().get("T100").getRevision());
        assertEquals(audits, f.data.centralAudit.size());
        assertFalse(f.data.isDirty());
    }

    @Test public void defeatedAndSubmittedPlayersCannotUseAnotherOwnersCommittedCompany() throws Exception {
        Fixture f = new Fixture(LOTRFaction.GONDOR);
        f.participate("gondor");
        f.company("C1", "gondor", "T100", KOMEHiredUnitClass.CAMPAIGN,
            KOMEPopulationType.OFFENSIVE, false);
        f.commit("C1", EntryOrigin.LEGAL_ARRIVAL);
        assertTrue(f.evaluate().isAllowed());
        KOMEWar war = new KOMEWar(); war.id = "W1";
        war.sideOneFactions.add("gondor"); war.sideTwoFactions.add("mordor");
        f.data.wars.put(war.id, war);
        f.data.lastKnownPlayerFactions.put(f.access.player.id, "gondor");
        f.data.warSeason.phase = KOMEWarSeasonState.Phase.WAR;
        f.data.warSeason.factionDefeats.put("gondor", 1L);
        assertFalse("Defeated player must not bypass governance via Join Battle", f.evaluate().isAllowed());
        assertFalse(f.select("C1").isAllowed());
        assertTrue(KOMEGovernanceService.choose(f.data, f.access.player.id, "W1",
            KOMEPlayerGovernance.State.SUBMITTED, "", 20L).allowed);
        int audits = f.data.centralAudit.size();
        f.data.setDirty(false);
        assertFalse(f.evaluate().isAllowed());
        assertFalse(f.select("C1").isAllowed());
        assertEquals(audits, f.data.centralAudit.size());
        assertFalse(f.data.isDirty());
        war.status = KOMEWar.ENDED;
        assertTrue("Ended war does not restrict another battle", f.evaluate().isAllowed());
    }

    private static void assertReason(Reason expected,
            KOMEJoinBattleService.Projection actual) {
        assertEquals(expected, actual.reason);
        assertEquals(expected == Reason.ALLOWED, actual.isAllowed());
    }

    private static KOMEConflictRecord ok(KOMEConflictService.Result result) {
        assertTrue(result.code + ": " + result.reason, result.isSuccess());
        return result.record;
    }

    private static final class UnitShape {
        final KOMEHiredUnitClass unitClass;
        final KOMEPopulationType type;
        final boolean farmhand;

        UnitShape(KOMEHiredUnitClass unitClass, KOMEPopulationType type,
                boolean farmhand) {
            this.unitClass = unitClass;
            this.type = type;
            this.farmhand = farmhand;
        }
    }

    private static final class Fixture {
        final KOMEAccessFixture access;
        final KOMEWorldData data;
        KOMEConflictRecord record;
        long now = 10L;

        Fixture(LOTRFaction pledge) throws Exception {
            this(pledge, true);
        }

        Fixture(LOTRFaction pledge, boolean start) throws Exception {
            access = new KOMEAccessFixture();
            data = access.data;
            access.pledge(pledge);
            if (start) start(State.ORDINARY, Collections.<GarrisonSeed>emptyList());
        }

        void start(State state, java.util.Collection<GarrisonSeed> garrison) {
            record = ok(data.getConflictService().start("T100", state,
                ExpectedConflict.absent(), garrison, context()));
        }

        void participate(String faction) {
            record = ok(data.getConflictService().beginFactionParticipation("T100",
                expected(), faction, context()));
        }

        void registerPlayer(String faction) {
            record = ok(data.getConflictService().registerPlayer("T100", expected(),
                access.player.id, faction, context()));
        }

        void withdrawPlayer() {
            record = ok(data.getConflictService().withdrawPlayer("T100", expected(),
                access.player.id, context()));
        }

        void commit(String companyId, EntryOrigin origin) {
            record = ok(data.getConflictService().commit("T100", expected(),
                new KOMEConflictContracts.CommitmentInput(companyId,
                    KOMEHiredUnitClass.CAMPAIGN, origin, "M-" + companyId), context()));
        }

        KOMEArmyCompany emptyCompany(String id, String faction, String tile) {
            UUID owner = UUID.randomUUID();
            data.lastKnownPlayerFactions.put(owner, faction);
            KOMEArmyCompany company = new KOMEArmyCompany();
            company.id = id;
            company.owner = owner;
            company.ownerName = "Owner " + id;
            company.faction = faction;
            company.name = "Company " + id;
            company.currentTile = tile;
            data.armyCompanies.put(id, company);
            return company;
        }

        KOMEArmyCompany company(String id, String faction, String tile,
                KOMEHiredUnitClass unitClass, KOMEPopulationType type,
                boolean farmhand) {
            KOMEArmyCompany company = emptyCompany(id, faction, tile);
            member(company, faction, tile, unitClass, type, farmhand);
            return company;
        }

        KOMEHiredUnitRecord member(KOMEArmyCompany company, String faction,
                String tile, KOMEHiredUnitClass unitClass,
                KOMEPopulationType type, boolean farmhand) {
            KOMEHiredUnitRecord member = new KOMEHiredUnitRecord();
            member.entity = UUID.randomUUID();
            member.owner = company.owner;
            member.companyId = company.id;
            member.companyName = company.name;
            member.currentTile = tile;
            member.sourceTileId = "T900";
            member.unitFaction = faction;
            member.populationOwningFaction = faction;
            member.type = type;
            member.farmhand = farmhand;
            member.cost = member.baseCost = member.populationSpent = farmhand ? 0 : 20;
            member.assignPersistedUnitClass(unitClass);
            data.hiredUnits.put(member.entity, member);
            company.units.add(member.entity);
            company.totalPopulation += member.cost;
            company.groundPopulation += member.cost;
            return member;
        }

        void addEmergencyDefender(String faction) {
            String intent = "ED-" + record.getConflictId().substring(2) + "-1";
            KOMEEmergencyDefenseCommitment.Defender defender =
                new KOMEEmergencyDefenseCommitment.Defender(intent, UUID.randomUUID(),
                    20, now, KOMEEmergencyDefenseCommitment.Disposition.ACTIVE,
                    false, "autonomous native defender");
            Map<String, KOMEEmergencyDefenseCommitment.Defender> defenders =
                new LinkedHashMap<String, KOMEEmergencyDefenseCommitment.Defender>();
            defenders.put(intent, defender);
            data.emergencyDefenseCommitments.put(record.getConflictId(),
                new KOMEEmergencyDefenseCommitment(record.getConflictId(), "T100",
                    faction, 1L, 1L, 2000L, "test-template", 20, 1, 2000L,
                    now, KOMEEmergencyDefenseCommitment.State.ACTIVE, defenders,
                    "test"));
        }

        KOMEJoinBattleService.Projection evaluate() {
            return KOMEJoinBattleService.INSTANCE.evaluate(data, access.player, "T100");
        }

        KOMEJoinBattleService.SelectionResult select(String companyId) {
            return select(expected(), companyId);
        }

        KOMEJoinBattleService.SelectionResult select(ExpectedConflict expected,
                String companyId) {
            return KOMEJoinBattleService.INSTANCE.validateSelectedCompany(data,
                access.player, "T100", expected, companyId);
        }

        ExpectedConflict expected() {
            return ExpectedConflict.at(record.getConflictId(), record.getRevision());
        }

        Context context() {
            return new Context(now++, "test", "KOM-19 Phase 1 fixture");
        }
    }
}
