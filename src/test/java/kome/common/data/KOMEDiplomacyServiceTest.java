package kome.common.data;

import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMEDiplomacyServiceTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    private static final String GONDOR = "gondor";
    private static final String ROHAN = "rohan";
    private static final String MORDOR = "mordor";

    @Before
    @After
    public void resetRelations() {
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.NEUTRAL);
        setRelation(GONDOR, MORDOR, LOTRFactionRelations.Relation.NEUTRAL);
        setRelation(ROHAN, MORDOR, LOTRFactionRelations.Relation.NEUTRAL);
    }

    @Test
    public void nativeRelationsAreAuthoritativeWithoutWorkflowRecordsAndSymmetric() {
        KOMEWorldData data = new KOMEWorldData("d");
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ENEMY);

        assertEquals(KOMEDiplomacyRelation.ENEMIES,
            KOMEDiplomacyService.getRelation(data, GONDOR, ROHAN));
        assertEquals(KOMEDiplomacyRelation.ENEMIES,
            KOMEDiplomacyService.getRelation(data, ROHAN, GONDOR));
        assertTrue(data.canonicalDiplomacyRecords.isEmpty());

        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ALLY);
        assertEquals(KOMEDiplomacyRelation.ALLIES,
            KOMEDiplomacyService.getRelation(data, GONDOR, ROHAN));
        assertTrue(data.canonicalDiplomacyRecords.isEmpty());
        assertEquals(KOMEDiplomacyRelation.ALLIES,
            KOMEDiplomacyService.getRelation(data, GONDOR, GONDOR));
    }

    @Test
    public void pendingImprovementDoesNotChangeGameplayUntilReceivingKingAccepts() {
        KOMEWorldData data = crownedWorld();
        UUID gondorKing = data.getFactionKingId(GONDOR);
        UUID rohanKing = data.getFactionKingId(ROHAN);
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ENEMY);

        assertTrue(KOMEDiplomacyService.requestIncrease(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.NEUTRAL, gondorKing, 10L).accepted);
        assertEquals(LOTRFactionRelations.Relation.ENEMY, lotrRelation(GONDOR, ROHAN));
        assertFalse(KOMEDiplomacyService.acceptPendingIncrease(
            data, GONDOR, ROHAN, gondorKing, 11L).accepted);
        assertTrue(KOMEDiplomacyService.acceptPendingIncrease(
            data, ROHAN, GONDOR, rohanKing, 12L).accepted);
        assertEquals(LOTRFactionRelations.Relation.NEUTRAL, lotrRelation(GONDOR, ROHAN));

        assertTrue(KOMEDiplomacyService.requestIncrease(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.FRIENDS, gondorKing, 13L).accepted);
        assertEquals(LOTRFactionRelations.Relation.NEUTRAL, lotrRelation(GONDOR, ROHAN));
        assertTrue(KOMEDiplomacyService.acceptPendingIncrease(
            data, ROHAN, GONDOR, rohanKing, 14L).accepted);
        assertEquals(LOTRFactionRelations.Relation.FRIEND, lotrRelation(GONDOR, ROHAN));

        assertTrue(KOMEDiplomacyService.requestIncrease(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.ALLIES, gondorKing, 15L).accepted);
        assertTrue(KOMEDiplomacyService.acceptPendingIncrease(
            data, ROHAN, GONDOR, rohanKing, 16L).accepted);
        assertEquals(LOTRFactionRelations.Relation.ALLY, lotrRelation(GONDOR, ROHAN));
        assertNull(data.canonicalDiplomacyRecords.get("gondor|rohan").pendingTarget);
    }

    @Test
    public void mortalEnemyToEnemyAlsoRequiresConsent() {
        KOMEWorldData data = crownedWorld();
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.MORTAL_ENEMY);

        assertTrue(KOMEDiplomacyService.requestIncrease(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.ENEMIES,
            data.getFactionKingId(GONDOR), 1L).accepted);
        assertEquals(LOTRFactionRelations.Relation.MORTAL_ENEMY, lotrRelation(GONDOR, ROHAN));
        assertTrue(KOMEDiplomacyService.acceptPendingIncrease(
            data, ROHAN, GONDOR, data.getFactionKingId(ROHAN), 2L).accepted);
        assertEquals(LOTRFactionRelations.Relation.ENEMY, lotrRelation(GONDOR, ROHAN));
    }

    @Test
    public void recognizedKingMayUnilaterallyWorsenEveryRung() {
        KOMEWorldData data = crownedWorld();
        UUID gondorKing = data.getFactionKingId(GONDOR);
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ALLY);

        worsen(data, KOMEDiplomacyRelation.FRIENDS, gondorKing, LOTRFactionRelations.Relation.FRIEND, 1L);
        worsen(data, KOMEDiplomacyRelation.NEUTRAL, gondorKing, LOTRFactionRelations.Relation.NEUTRAL, 2L);
        worsen(data, KOMEDiplomacyRelation.ENEMIES, gondorKing, LOTRFactionRelations.Relation.ENEMY, 3L);
        worsen(data, KOMEDiplomacyRelation.MORTAL_ENEMIES, gondorKing,
            LOTRFactionRelations.Relation.MORTAL_ENEMY, 4L);

        KOMEDiplomacyService.Result noOp = KOMEDiplomacyService.worsenRelation(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.MORTAL_ENEMIES, gondorKing, 5L);
        assertFalse(noOp.accepted);
        assertTrue(noOp.reason.contains("already"));
        KOMEDiplomacyService.Result wrongDirection = KOMEDiplomacyService.worsenRelation(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.NEUTRAL, gondorKing, 6L);
        assertFalse(wrongDirection.accepted);
        assertTrue(wrongDirection.reason.contains("requires a request"));
        assertFalse(KOMEDiplomacyService.worsenRelation(
            data, ROHAN, GONDOR, KOMEDiplomacyRelation.ENEMIES,
            UUID.randomUUID(), 7L).accepted);
        assertTrue(data.wars.isEmpty());
    }

    @Test
    public void cancelAndReloadPreservePendingMetadataWithoutProjectingIt() {
        KOMEWorldData data = crownedWorld();
        UUID gondorKing = data.getFactionKingId(GONDOR);
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ENEMY);
        assertTrue(KOMEDiplomacyService.requestIncrease(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.NEUTRAL, gondorKing, 42L).accepted);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData loaded = new KOMEWorldData("loaded");
        loaded.readFromNBT(saved);
        assertEquals(KOMEDiplomacyRelation.NEUTRAL,
            loaded.canonicalDiplomacyRecords.get("gondor|rohan").pendingTarget);
        assertEquals(LOTRFactionRelations.Relation.ENEMY, lotrRelation(GONDOR, ROHAN));
        loaded.reconcileAllianceLifecycle(43L, 1L);
        assertEquals(LOTRFactionRelations.Relation.ENEMY, lotrRelation(GONDOR, ROHAN));

        assertTrue(KOMEDiplomacyService.cancelPendingRequest(
            loaded, GONDOR, ROHAN, gondorKing, false).accepted);
        assertEquals(LOTRFactionRelations.Relation.ENEMY, lotrRelation(GONDOR, ROHAN));
        assertNull(loaded.canonicalDiplomacyRecords.get("gondor|rohan").pendingTarget);
    }

    @Test
    public void staleAcceptedRecordNeverOverwritesLotrDuringReload() {
        KOMEWorldData data = new KOMEWorldData("d");
        KOMEDiplomacyRecord record = new KOMEDiplomacyRecord(GONDOR, ROHAN);
        record.relation = KOMEDiplomacyRelation.ALLIES;
        data.canonicalDiplomacyRecords.put(record.key(), record);
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ENEMY);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData loaded = new KOMEWorldData("loaded");
        loaded.readFromNBT(saved);
        loaded.reconcileAllianceLifecycle(1L, 1L);

        assertEquals(KOMEDiplomacyRelation.ENEMIES,
            KOMEDiplomacyService.getRelation(loaded, GONDOR, ROHAN));
        assertEquals(LOTRFactionRelations.Relation.ENEMY, lotrRelation(GONDOR, ROHAN));
    }

    @Test
    public void warDeclarationSetsMortalEnemyButMortalEnemyAloneCreatesNoWar() {
        KOMEWorldData data = new KOMEWorldData("d");
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ALLY);
        KOMEWar war = KOMEWarService.createWar(data, GONDOR, ROHAN, "Test", "king", 10L);
        assertNotNull(war);
        assertEquals(LOTRFactionRelations.Relation.MORTAL_ENEMY, lotrRelation(GONDOR, ROHAN));
        assertSame(war, KOMEWarService.findActiveOpposition(data, GONDOR, ROHAN));

        KOMEWorldData separate = new KOMEWorldData("separate");
        setRelation(GONDOR, MORDOR, LOTRFactionRelations.Relation.MORTAL_ENEMY);
        assertNull(KOMEWarService.findActiveOpposition(separate, GONDOR, MORDOR));
        assertTrue(separate.wars.isEmpty());
    }

    @Test
    public void acceptedPeaceEndsDirectWarAndPreservesOtherBelligerents() {
        KOMEWorldData data = crownedWorld();
        KOMEWar war = KOMEWarService.createWar(data, GONDOR, MORDOR, "Coalition", "king", 10L);
        assertTrue(war.addFaction(1, ROHAN));
        war.recordMembership(ROHAN, 1, "MANUAL", "", "king", 11L);
        UUID mordorKing = crown(data, MORDOR);

        assertTrue(KOMEDiplomacyService.requestIncrease(
            data, GONDOR, MORDOR, KOMEDiplomacyRelation.ENEMIES,
            data.getFactionKingId(GONDOR), 12L).accepted);
        assertTrue(KOMEDiplomacyService.acceptPendingIncrease(
            data, MORDOR, GONDOR, mordorKing, 13L).accepted);

        assertNull(KOMEWarService.findActiveOpposition(data, GONDOR, MORDOR));
        assertSame(war, KOMEWarService.findActiveOpposition(data, ROHAN, MORDOR));
        assertTrue(war.isActive());
        assertEquals(0, war.sideOf(GONDOR));
    }

    @Test
    public void acceptedImprovementEndsDirectTwoFactionWar() {
        KOMEWorldData data = crownedWorld();
        UUID mordorKing = crown(data, MORDOR);
        KOMEWar war = KOMEWarService.createWar(
            data, GONDOR, MORDOR, "Direct", "king", 10L);

        assertTrue(KOMEDiplomacyService.requestIncrease(
            data, GONDOR, MORDOR, KOMEDiplomacyRelation.ENEMIES,
            data.getFactionKingId(GONDOR), 11L).accepted);
        assertTrue(KOMEDiplomacyService.acceptPendingIncrease(
            data, MORDOR, GONDOR, mordorKing, 12L).accepted);

        assertTrue(war.isEnded());
        assertNull(KOMEWarService.findActiveOpposition(data, GONDOR, MORDOR));
        assertEquals(LOTRFactionRelations.Relation.ENEMY, lotrRelation(GONDOR, MORDOR));
    }

    @Test
    public void recordProjectionShowsLiveLotrStateAndSeparatePendingMetadata() {
        KOMEWorldData data = crownedWorld();
        for (KOMEDiplomacyRelation relation : KOMEDiplomacyRelation.values()) {
            setRelation(GONDOR, ROHAN, relation.toLotrRelation());
            String[] projected = projectedPair(data, GONDOR, ROHAN);
            assertEquals(relation.key, projected[4]);
            assertEquals("0", projected[5]);
        }

        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ENEMY);
        assertTrue(KOMEDiplomacyService.requestIncrease(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.NEUTRAL,
            data.getFactionKingId(GONDOR), 20L).accepted);

        String[] projected = projectedPair(data, GONDOR, ROHAN);
        assertEquals("enemy", projected[4]);
        assertEquals("1", projected[5]);
        assertEquals("neutral", projected[6]);
        assertEquals(GONDOR, projected[7]);
        assertEquals(ROHAN, projected[8]);
        assertEquals(LOTRFactionRelations.Relation.ENEMY, lotrRelation(GONDOR, ROHAN));
    }

    @Test
    public void externalLotrChangeOverridesStaleHistoryAndWorsenStartsFromLiveRung() {
        KOMEWorldData data = crownedWorld();
        UUID gondorKing = data.getFactionKingId(GONDOR);
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ALLY);
        assertTrue(KOMEDiplomacyService.worsenRelation(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.MORTAL_ENEMIES,
            gondorKing, 30L).accepted);
        KOMEDiplomacyRecord history =
            data.canonicalDiplomacyRecords.get("gondor|rohan");
        assertEquals(KOMEDiplomacyRelation.MORTAL_ENEMIES, history.relation);

        // Same authoritative mutation performed by /facRelations.
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ALLY);

        assertEquals(KOMEDiplomacyRelation.ALLIES,
            KOMEDiplomacyService.getRelation(data, GONDOR, ROHAN));
        assertEquals("allies", projectedPair(data, GONDOR, ROHAN)[4]);
        assertEquals(KOMEDiplomacyRelation.MORTAL_ENEMIES, history.relation);

        KOMEDiplomacyRelation target =
            KOMEDiplomacyService.getRelation(data, GONDOR, ROHAN).oneStepWorse();
        assertEquals(KOMEDiplomacyRelation.FRIENDS, target);
        assertTrue(KOMEDiplomacyService.worsenRelation(
            data, GONDOR, ROHAN, target, gondorKing, 31L).accepted);
        assertEquals(LOTRFactionRelations.Relation.FRIEND,
            lotrRelation(GONDOR, ROHAN));
    }

    @Test
    public void requestValidationAndMetadataStartFromLiveRelationAfterExternalChange() {
        KOMEWorldData data = crownedWorld();
        UUID gondorKing = data.getFactionKingId(GONDOR);
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ALLY);
        assertTrue(KOMEDiplomacyService.worsenRelation(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.MORTAL_ENEMIES,
            gondorKing, 32L).accepted);
        KOMEDiplomacyRecord history = data.canonicalDiplomacyRecords
            .values().iterator().next();
        assertEquals(KOMEDiplomacyRelation.MORTAL_ENEMIES, history.relation);

        // A native /facRelations improvement makes Friend a worsening, even
        // though the persisted KOME history would have treated it as friendlier.
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ALLY);
        assertFalse(KOMEDiplomacyService.requestIncrease(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.FRIENDS,
            gondorKing, 33L).accepted);
        assertNull(history.pendingTarget);
        assertEquals(KOMEDiplomacyRelation.MORTAL_ENEMIES, history.relation);

        // A later native change to Enemy makes Neutral a valid improvement;
        // newly recorded request metadata starts at the live Enemy rung.
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ENEMY);
        assertTrue(KOMEDiplomacyService.requestIncrease(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.NEUTRAL,
            gondorKing, 34L).accepted);
        assertEquals(KOMEDiplomacyRelation.ENEMIES, history.relation);
        assertEquals(KOMEDiplomacyRelation.NEUTRAL, history.pendingTarget);
        assertEquals(LOTRFactionRelations.Relation.ENEMY,
            lotrRelation(GONDOR, ROHAN));
        assertEquals(KOMEDiplomacyRelation.ENEMIES,
            KOMEDiplomacyService.getRelation(data, GONDOR, ROHAN));
    }

    @Test
    public void externalAllyRelationImmediatelyAllowsWaypointDespiteStaleKomeHistory() {
        KOMEWorldData data = crownedWorld();
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.NEUTRAL);
        assertTrue(KOMEDiplomacyService.worsenRelation(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.MORTAL_ENEMIES,
            data.getFactionKingId(GONDOR), 40L).accepted);

        KOMEConquestTile tile = new KOMEConquestTile("T277");
        tile.claim(ROHAN, 0L);
        data.conquestTiles.put(tile.id, tile);
        assertFalse(KOMEWaypointAccessService.evaluateResolvedTile(
            data, UUID.randomUUID(), GONDOR, false, tile.id, true).finalAllowed);

        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ALLY);
        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), GONDOR, false, tile.id, true);
        assertTrue(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DIPLOMATIC, decision.state);
        assertEquals("allies", projectedPair(data, GONDOR, ROHAN)[4]);
    }

    @Test
    public void externallySatisfiedPendingRequestIsRejectedAndInvalidatedSafely() {
        KOMEWorldData data = crownedWorld();
        UUID gondorKing = data.getFactionKingId(GONDOR);
        UUID rohanKing = data.getFactionKingId(ROHAN);
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ENEMY);
        assertTrue(KOMEDiplomacyService.requestIncrease(
            data, GONDOR, ROHAN, KOMEDiplomacyRelation.NEUTRAL,
            gondorKing, 50L).accepted);

        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ALLY);
        KOMEDiplomacyService.Result result =
            KOMEDiplomacyService.acceptPendingIncrease(
                data, ROHAN, GONDOR, rohanKing, 51L);

        assertFalse(result.accepted);
        assertTrue(result.reason.contains("no longer improves")
            || result.reason.contains("already active"));
        assertEquals(LOTRFactionRelations.Relation.ALLY,
            lotrRelation(GONDOR, ROHAN));
        KOMEDiplomacyRecord record =
            data.canonicalDiplomacyRecords.get("gondor|rohan");
        assertNull(record.pendingTarget);
        assertEquals(KOMEDiplomacyRelation.ALLIES, record.relation);
        assertEquals("allies", projectedPair(data, GONDOR, ROHAN)[4]);
        assertEquals("0", projectedPair(data, GONDOR, ROHAN)[5]);
    }

    @Test
    public void allyLossRemovesOnlyExplicitSupportMembershipAndDoesNotTouchCompanies() {
        KOMEWorldData data = crownedWorld();
        setRelation(GONDOR, ROHAN, LOTRFactionRelations.Relation.ALLY);
        KOMEWar war = KOMEWarService.createWar(data, GONDOR, MORDOR, "War", "king", 1L);
        assertTrue(war.addFaction(1, ROHAN));
        war.recordMembership(ROHAN, 1, "SUPPORT", GONDOR, "king", 2L);
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "support-company";
        company.faction = ROHAN;
        data.armyCompanies.put(company.id, company);

        assertTrue(KOMEDiplomacyService.worsenRelation(
            data, ROHAN, GONDOR, KOMEDiplomacyRelation.FRIENDS,
            data.getFactionKingId(ROHAN), 3L).accepted);

        assertEquals(0, war.sideOf(ROHAN));
        assertTrue(war.isActive());
        assertTrue(data.armyCompanies.containsKey(company.id));
    }

    @Test
    public void allyDiplomacyDoesNotAutoEnlistOrChangeThirdPartyRelations() {
        KOMEWorldData data = crownedWorld();
        crown(data, MORDOR);
        setRelation(ROHAN, MORDOR, LOTRFactionRelations.Relation.ENEMY);
        KOMEWar war = KOMEWarService.createWar(data, GONDOR, MORDOR, "War", "king", 1L);

        assertTrue(KOMEDiplomacyService.requestIncrease(
            data, ROHAN, GONDOR, KOMEDiplomacyRelation.ALLIES,
            data.getFactionKingId(ROHAN), 2L).accepted);
        assertTrue(KOMEDiplomacyService.acceptPendingIncrease(
            data, GONDOR, ROHAN, data.getFactionKingId(GONDOR), 3L).accepted);

        assertEquals(LOTRFactionRelations.Relation.ALLY, lotrRelation(GONDOR, ROHAN));
        assertEquals(LOTRFactionRelations.Relation.ENEMY, lotrRelation(ROHAN, MORDOR));
        assertEquals(0, war.sideOf(ROHAN));
    }

    @Test
    public void malformedSelfPairIsQuarantinedOnLoad() {
        NBTTagCompound malformed = new NBTTagCompound();
        malformed.setString("FactionA", GONDOR);
        malformed.setString("FactionB", GONDOR);
        malformed.setString("Relation", "allies");
        KOMEWorldData bad = new KOMEWorldData("bad");
        NBTTagCompound root = new NBTTagCompound();
        new KOMEWorldData("fixture").writeToNBT(root);
        net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
        list.appendTag(malformed);
        root.setTag("CanonicalDiplomacyRecords", list);
        bad.readFromNBT(root);
        assertTrue(bad.canonicalDiplomacyRecords.isEmpty());
    }

    private static KOMEWorldData crownedWorld() {
        KOMEWorldData data = new KOMEWorldData("d");
        crown(data, GONDOR);
        crown(data, ROHAN);
        return data;
    }

    private static UUID crown(KOMEWorldData data, String faction) {
        UUID king = UUID.randomUUID();
        data.lastKnownPlayerFactions.put(king, faction);
        assertTrue(KOMERulerService.assignRuler(data, faction, king, faction + " king"));
        return king;
    }

    private static void worsen(KOMEWorldData data, KOMEDiplomacyRelation target,
            UUID king, LOTRFactionRelations.Relation expected, long now) {
        assertTrue(KOMEDiplomacyService.worsenRelation(
            data, GONDOR, ROHAN, target, king, now).accepted);
        assertEquals(expected, lotrRelation(GONDOR, ROHAN));
        assertEquals(expected, lotrRelation(ROHAN, GONDOR));
    }

    private static void setRelation(String first, String second,
            LOTRFactionRelations.Relation relation) {
        LOTRFactionRelations.overrideRelations(
            KOMEAlliance.findLotrFaction(first), KOMEAlliance.findLotrFaction(second), relation);
    }

    private static LOTRFactionRelations.Relation lotrRelation(String first, String second) {
        LOTRFaction a = KOMEAlliance.findLotrFaction(first);
        LOTRFaction b = KOMEAlliance.findLotrFaction(second);
        return LOTRFactionRelations.getRelations(a, b);
    }

    private static String[] projectedPair(KOMEWorldData data, String first, String second) {
        String key = KOMEDiplomacyRecord.pairKey(first, second);
        for (Object value : KOMEAllianceRecordBuilder.build(data, null)) {
            String[] parts = String.valueOf(value).split("\\t", -1);
            if (parts.length >= 13 && "DIPLOMACY_RELATION".equals(parts[0])
                    && key.equals(parts[1])) {
                return parts;
            }
        }
        fail("Missing projected diplomacy pair " + key);
        return new String[0];
    }
}
