package kome.common.data;

import java.util.UUID;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEGovernanceServiceTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private final UUID player = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private LOTRFactionRelations.Relation original;

    @Before public void allies() {
        original = KOMEAllianceAuthority.getCurrentRelation("gondor", "rohan");
        relation(LOTRFactionRelations.Relation.ALLY);
    }
    @After public void restore() { relation(original); }
    private void relation(LOTRFactionRelations.Relation relation) {
        LOTRFactionRelations.overrideRelations(KOMEAlliance.findLotrFaction("gondor"), KOMEAlliance.findLotrFaction("rohan"), relation);
    }
    private KOMEWorldData world(boolean defeated) {
        KOMEWorldData data = new KOMEWorldData("governance");
        data.warSeason.phase = KOMEWarSeasonState.Phase.WAR;
        data.lastKnownPlayerFactions.put(player, "gondor");
        KOMEWar war = new KOMEWar(); war.id = "W1";
        war.sideOneFactions.add("gondor"); war.sideOneFactions.add("rohan"); war.sideTwoFactions.add("mordor");
        data.wars.put(war.id, war);
        if (defeated) data.warSeason.factionDefeats.put("gondor", 10L);
        return data;
    }
    private KOMEGovernanceService.Decision choose(KOMEWorldData data, KOMEPlayerGovernance.State state, String host, long now) {
        return KOMEGovernanceService.choose(data, player, "W1", state, host, now);
    }

    @Test public void requiresAuthoritativeDefeatAndSelfIdentity() {
        KOMEWorldData data = world(false);
        assertFalse(choose(data, KOMEPlayerGovernance.State.SUBMITTED, "", 20).allowed);
        data.warSeason.factionDefeats.put("gondor", 10L);
        assertFalse(KOMEGovernanceService.choose(data, UUID.randomUUID(), "W1", KOMEPlayerGovernance.State.SUBMITTED, "", 20).allowed);
        assertFalse(KOMEGovernanceService.choose(data, player, "missing", KOMEPlayerGovernance.State.SUBMITTED, "", 20).allowed);
        assertTrue(data.playerGovernance.isEmpty());
    }

    @Test public void defeatedWithoutChoiceCannotBypassThroughHostActions() {
        KOMEWorldData data = world(true);
        assertFalse(KOMEGovernanceService.militaryAction(data, player, "gondor").allowed);
        assertFalse(KOMEGovernanceService.militaryAction(data, player, "rohan").allowed);
        assertFalse(KOMECompanyDiplomacyAuthorization.canContinueDelegation(data, "rohan", player).allowed);
        assertTrue(KOMEGovernanceService.militaryAction(data, player, "dale").allowed);
    }

    @Test public void submittedBlocksIndirectActionsAndRepeatedChoiceDoesNotDuplicateAudit() {
        KOMEWorldData data = world(true);
        assertTrue(choose(data, KOMEPlayerGovernance.State.SUBMITTED, "", 20).allowed);
        int audit = data.centralAudit.size();
        assertTrue(choose(data, KOMEPlayerGovernance.State.SUBMITTED, "", 21).allowed);
        assertEquals(audit, data.centralAudit.size());
        assertEquals(1L, KOMEGovernanceService.record(data, player, "W1").revision);
        assertFalse(KOMEGovernanceService.militaryAction(data, player, "mordor").allowed);
        assertFalse(KOMEGovernanceService.effectiveFaction(data, player, "mordor").allowed);
        KOMEArmyCompany company = new KOMEArmyCompany(); company.owner = player; company.faction = "gondor";
        assertFalse(KOMECompanyTransferService.offer(data, company, player, UUID.randomUUID(), "recipient", 22).success);
        assertEquals("gondor", data.getPlayerFactionKey(player));
        assertTrue(data.pledgeReleaseTombstones.isEmpty());
    }

    @Test public void exileRequiresRealAlliedUndefeatedHost() {
        KOMEWorldData data = world(true);
        assertFalse(choose(data, KOMEPlayerGovernance.State.EXILED, "gondor", 20).allowed);
        assertFalse(choose(data, KOMEPlayerGovernance.State.EXILED, "mordor", 20).allowed);
        relation(LOTRFactionRelations.Relation.FRIEND);
        assertFalse(choose(data, KOMEPlayerGovernance.State.EXILED, "rohan", 20).allowed);
        relation(LOTRFactionRelations.Relation.ALLY);
        data.warSeason.factionDefeats.put("rohan", 10L);
        assertFalse(choose(data, KOMEPlayerGovernance.State.EXILED, "rohan", 20).allowed);
        data.warSeason.factionDefeats.remove("rohan");
        assertTrue(choose(data, KOMEPlayerGovernance.State.EXILED, "rohan", 20).allowed);
        assertTrue(KOMECompanyDiplomacyAuthorization.canContinueDelegation(data, "rohan", player).allowed);
        assertFalse(KOMEGovernanceService.militaryAction(data, player, "gondor").allowed);
        assertEquals("rohan", KOMEGovernanceService.effectiveFaction(data, player, "mordor").faction);
        assertEquals("gondor", data.getPlayerFactionKey(player));
    }

    @Test public void choosingAlliedHostDoesNotInventWarMembershipOrLaunderNativeTroops() {
        KOMEWorldData data = world(true); data.wars.get("W1").sideOneFactions.remove("rohan");
        assertTrue(choose(data, KOMEPlayerGovernance.State.EXILED, "rohan", 20).allowed);
        assertFalse(KOMEGovernanceService.participation(data, player, data.wars.get("W1"), "rohan").allowed);
        assertEquals(0, KOMEGovernanceService.reconcile(data, 21));
        data.wars.get("W1").sideOneFactions.add("rohan");
        assertTrue(KOMEGovernanceService.participation(data, player, data.wars.get("W1"), "rohan").allowed);
        assertFalse(KOMEGovernanceCombat.unitDenial(data, player, "gondor", "mordor").isEmpty());
        assertTrue(KOMEGovernanceCombat.unitDenial(data, player, "rohan", "mordor").isEmpty());
    }

    @Test public void governanceAndSaveLoadPreservePurchasedIdentityHealthAndProgression() {
        KOMEWorldData data = world(true);
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord(); unit.entity = UUID.randomUUID(); unit.owner = player; unit.controller = player;
        unit.unitEntityId = "LOTR.GondorSoldier"; unit.unitFaction = "gondor"; unit.populationOwningFaction = "gondor";
        unit.populationSpent = 37; unit.level = 8; unit.sourceType = KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK;
        unit.stationedEntityData = new NBTTagCompound(); unit.stationedEntityData.setFloat("Health", 7.5F);
        unit.stationedEntityData.setString("NativeIdentity", "survivor"); data.hiredUnits.put(unit.entity, unit);
        KOMEPlayerProgression progression = new KOMEPlayerProgression(); data.progressions.put(player, progression);
        NBTTagCompound unitBefore = unit.writeToNBT(); NBTTagCompound progressionBefore = progression.writeToNBT();
        long population = KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");
        choose(data, KOMEPlayerGovernance.State.SUBMITTED, "", 20); choose(data, KOMEPlayerGovernance.State.EXILED, "rohan", 21);
        assertEquals(unitBefore, unit.writeToNBT()); assertEquals(progressionBefore, progression.writeToNBT());
        NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
        KOMEWorldData loaded = new KOMEWorldData("survivors"); loaded.readFromNBT(saved);
        assertEquals(unitBefore, loaded.hiredUnits.get(unit.entity).writeToNBT());
        assertEquals(progressionBefore, loaded.progressions.get(player).writeToNBT());
        assertEquals(population, KOMEPopulationService.getAvailablePopulationCenti(loaded, "gondor"));
        assertTrue(loaded.pledgeReleaseTombstones.isEmpty());
    }

    @Test public void relationLossDeniesImmediatelyAndPersistsSubmissionWithoutAssetCleanup() {
        KOMEWorldData data = world(true);
        assertTrue(choose(data, KOMEPlayerGovernance.State.EXILED, "rohan", 20).allowed);
        relation(LOTRFactionRelations.Relation.NEUTRAL);
        assertFalse(KOMEGovernanceService.militaryAction(data, player, "rohan").allowed);
        assertEquals(1, KOMEGovernanceService.reconcile(data, 21));
        assertEquals(0, KOMEGovernanceService.reconcile(data, 22));
        relation(LOTRFactionRelations.Relation.ALLY);
        assertFalse(KOMEGovernanceService.militaryAction(data, player, "rohan").allowed);
        assertTrue(choose(data, KOMEPlayerGovernance.State.EXILED, "rohan", 23).allowed);
        assertEquals(3, KOMEGovernanceService.record(data, player, "W1").revision);
        assertTrue(data.pledgeReleaseTombstones.isEmpty());
    }

    @Test public void restartPreservesAuthorityAndOldWarDoesNotRestrictNextSeason() {
        KOMEWorldData data = world(true);
        assertTrue(choose(data, KOMEPlayerGovernance.State.SUBMITTED, "", 20).allowed);
        NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
        KOMEWorldData loaded = new KOMEWorldData("reconnected"); loaded.readFromNBT(saved);
        assertFalse(KOMEGovernanceService.militaryAction(loaded, player, "rohan").allowed);
        assertEquals("gondor", loaded.getPlayerFactionKey(player));
        loaded.warSeason.phase = KOMEWarSeasonState.Phase.RESET;
        assertTrue(loaded.warSeason.completeReset(30).allowed);
        assertNull(KOMEGovernanceService.record(loaded, player, "W1"));
        assertEquals(1, KOMEGovernanceService.records(loaded, player).size());
        assertTrue(KOMEGovernanceService.militaryAction(loaded, player, "rohan").allowed);
    }

    @Test public void malformedMissingAndDuplicateGovernanceFailClosed() {
        KOMEWorldData data = world(true); choose(data, KOMEPlayerGovernance.State.SUBMITTED, "", 20);
        NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
        saved.removeTag("GovernanceSchema");
        assertLoadRejected(saved);
        data.writeToNBT(saved);
        NBTTagList list = saved.getTagList("PlayerGovernance", 10);
        list.appendTag(list.getCompoundTagAt(0).copy()); assertLoadRejected(saved);
    }

    private void assertLoadRejected(NBTTagCompound saved) {
        try { new KOMEWorldData("bad").readFromNBT(saved); fail("Malformed governance accepted"); }
        catch (RuntimeException expected) { assertTrue(expected.getMessage().contains("PlayerGovernance")); }
    }

    @Test public void clockRegressionCannotRewriteChoice() {
        KOMEWorldData data = world(true); choose(data, KOMEPlayerGovernance.State.SUBMITTED, "", 20);
        assertFalse(choose(data, KOMEPlayerGovernance.State.EXILED, "rohan", 19).allowed);
        assertEquals(KOMEPlayerGovernance.State.SUBMITTED, KOMEGovernanceService.record(data, player, "W1").state);
    }

    @Test public void actualDefeatPublicationCapturesOriginBeforePledgeCanChange() {
        KOMEWorldData data = world(false); data.initializeIntegratedWorld();
        data.conquestTiles.get("T388").claim("mordor", 0L);
        assertEquals(1, KOMEFactionDefeatService.reconcile(data, 30));
        KOMEPlayerGovernance record = KOMEGovernanceService.record(data, player, "W1");
        assertNotNull(record); assertEquals("gondor", record.origin);
        assertEquals(KOMEPlayerGovernance.State.SUBMITTED, record.state);
        data.lastKnownPlayerFactions.put(player, "rohan");
        assertFalse(KOMEGovernanceService.militaryAction(data, player, "rohan").allowed);
        assertEquals(0, KOMEFactionDefeatService.reconcile(data, 31));
    }

    @Test public void oldSchemaRetainsConflictDefeatAndGovernanceIfPresent() {
        KOMEWorldData data = world(true); choose(data, KOMEPlayerGovernance.State.SUBMITTED, "", 20);
        NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
        saved.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 7);
        KOMEWorldData loaded = new KOMEWorldData("schema7"); loaded.readFromNBT(saved);
        assertTrue(loaded.warSeason.isFactionDefeated("gondor"));
        assertNotNull(KOMEGovernanceService.record(loaded, player, "W1"));
        saved.removeTag("GovernanceSchema"); saved.removeTag("PlayerGovernance");
        loaded = new KOMEWorldData("original-schema7"); loaded.readFromNBT(saved);
        assertTrue(loaded.playerGovernance.isEmpty());
        assertFalse(KOMEGovernanceService.militaryAction(loaded, player, "rohan").allowed);
    }

    @Test public void savedTransferCannotLaunderSubmittedAssets() {
        KOMEWorldData data = world(true);
        UUID recipient = UUID.randomUUID(); data.lastKnownPlayerFactions.put(recipient, "gondor");
        KOMEArmyCompany company = new KOMEArmyCompany(); company.owner = player; company.faction = "gondor";
        company.transferRecipient = recipient; company.transferExpiresAtMillis = 100;
        choose(data, KOMEPlayerGovernance.State.SUBMITTED, "", 20);
        assertFalse(KOMECompanyTransferService.accept(data, company, recipient, "recipient", 21).success);
        assertEquals(player, company.owner);
    }

    @Test public void endedWarAndUnrelatedWarKeepSeparatePermissions() {
        KOMEWorldData data = world(true); choose(data, KOMEPlayerGovernance.State.SUBMITTED, "", 20);
        KOMEWar unrelated = new KOMEWar(); unrelated.id = "W2";
        unrelated.sideOneFactions.add("dale"); unrelated.sideTwoFactions.add("durinsfolk"); data.wars.put("W2", unrelated);
        assertTrue(KOMEGovernanceService.participation(data, player, unrelated, "dale").allowed);
        data.wars.get("W1").status = KOMEWar.ENDED;
        assertTrue(KOMEGovernanceService.militaryAction(data, player, "gondor").allowed);
    }
}
