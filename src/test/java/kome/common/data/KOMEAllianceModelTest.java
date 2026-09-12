package kome.common.data;

import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

/** Schema-7 alliance model, migration, authority, and persistence contract. */
public class KOMEAllianceModelTest {
    @Test public void canonicalPairIsSymmetricAndRejectsSelfPair() {
        assertEquals("gondor|rohan", KOMEAlliance.pairKey("Rohan", "Gondor"));
        KOMEWorldData data = new KOMEWorldData("test");
        assertSame(data.getAlliance("gondor", "rohan", true), data.getAlliance("rohan", "gondor", false));
        assertNull(data.getAlliance("gondor", "GONDOR", true));
    }

    @Test public void acceptedRequestStartsBothDirectionsAtStageZero() {
        KOMEAlliance alliance = active("gondor", "rohan");
        assertEquals(0, alliance.getFactionStage("gondor"));
        assertEquals(0, alliance.getFactionStage("rohan"));
        assertEquals(0, alliance.getSharedRelationStage());
    }

    @Test public void pendingRequestStoresCanonicalParties() {
        KOMEAlliance alliance = new KOMEAlliance("rohan", "gondor");
        alliance.requestTrack(KOMEAlliance.CIVIL, "king", 1L, true);
        alliance.setPendingParties(KOMEAlliance.CIVIL, "rohan", "gondor");
        assertEquals(KOMEAllianceTrackStatus.PENDING, alliance.getRelationshipStatus());
        assertEquals("rohan", alliance.getRequestedBy(KOMEAlliance.TRADE));
        assertEquals("gondor", alliance.getPendingReceiver(KOMEAlliance.MILITARY));
    }

    @Test public void directionalStagesAdvanceIndependently() {
        KOMEAlliance alliance = active("gondor", "rohan");
        assertTrue(alliance.setFactionStage("gondor", 3, "king", 2L, 20L));
        assertEquals(3, alliance.getFactionStage("gondor"));
        assertEquals(0, alliance.getFactionStage("rohan"));
    }

    @Test public void sharedRelationStageUsesLowerDirection() {
        KOMEAlliance alliance = active("gondor", "rohan");
        alliance.setFactionStage("gondor", 4, "test", 0L, 10L);
        alliance.setFactionStage("rohan", 2, "test", 0L, 11L);
        assertEquals(2, alliance.getSharedRelationStage());
    }

    @Test public void actingFactionKeepsHigherDirectionalBenefits() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        alliance.setFactionStage("gondor", 3, "test", 0L, 10L);
        KOMEAllianceAuthority authority = new KOMEAllianceAuthority(data);
        assertTrue(authority.canFactionHireAlliedFarmhand("gondor", "rohan"));
        assertFalse(authority.canFactionHireAlliedFarmhand("rohan", "gondor"));
    }

    @Test public void waypointUseRequiresCanonicalDiplomacyForForeignTerritory() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = new KOMEConquestTile("T001");
        tile.claim("mordor", 0L);
        data.conquestTiles.put(tile.id, tile);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);

        assertFalse(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DENIED, decision.state);
    }
    @Test public void stageTwoMerchantEntitlementPersistsAcrossBreak() {
        KOMEAlliance alliance = active("gondor", "rohan");
        alliance.setFactionStage("gondor", 2, "test", 0L, 10L);
        assertTrue(alliance.hasProduceMerchantSlot("gondor"));
        alliance.clearAllTracks("break", 20L);
        assertFalse(alliance.hasAnyAlliance());
        assertTrue(alliance.hasProduceMerchantSlot("gondor"));
        assertTrue(alliance.hasPersistentEntitlements());
    }

    @Test public void breakResetsBothDirectionsAndFixedMilestones() {
        KOMEAlliance alliance = active("gondor", "rohan");
        alliance.setFactionStage("gondor", 4, "test", 0L, 10L);
        alliance.setFactionStage("rohan", 3, "test", 0L, 11L);
        alliance.getStageProgress("gondor").markFixedComplete(4, 12L);
        alliance.clearAllTracks("break", 20L);
        assertEquals(0, alliance.getFactionStage("gondor"));
        assertEquals(0, alliance.getFactionStage("rohan"));
        assertFalse(alliance.getStageProgress("gondor").isFixedComplete(4));
    }

    @Test public void stageProgressRoundTrips() {
        KOMEAlliance alliance = active("gondor", "rohan");
        alliance.setFactionStage("gondor", 3, "test", 8L, 100L);
        alliance.getStageProgress("gondor").qualifyingWarId = "W1";
        alliance.getStageProgress("gondor").qualifyingCompanyId = "C1";
        alliance.getStageProgress("gondor").qualifyingDeploymentAtMillis = 101L;
        KOMEAlliance restored = new KOMEAlliance("", "");
        restored.readFromNBT(alliance.writeToNBT());
        assertEquals(3, restored.getFactionStage("gondor"));
        assertEquals("W1", restored.getStageProgress("gondor").qualifyingWarId);
        assertEquals("C1", restored.getStageProgress("gondor").qualifyingCompanyId);
    }

    @Test public void legacyCivilTwoMapsConservativelyToStageOne() {
        KOMEAlliance migrated = readLegacy(2, 0, 0, 0, 0, 0);
        assertEquals(1, migrated.getFactionStage("gondor"));
        assertEquals(0, migrated.getFactionStage("rohan"));
    }

    @Test public void legacyTradeTwoMapsToStageTwoAndMerchantEntitlement() {
        KOMEAlliance migrated = readLegacy(0, 2, 0, 0, 0, 0);
        assertEquals(2, migrated.getFactionStage("gondor"));
        assertTrue(migrated.hasProduceMerchantSlot("gondor"));
        assertEquals(0, migrated.getFactionStage("rohan"));
    }

    @Test public void legacyMilitaryTwoMapsToStageThree() {
        assertEquals(3, readLegacy(0, 0, 2, 0, 0, 0).getFactionStage("gondor"));
    }

    @Test public void legacyMilitaryThreeMapsToStageFour() {
        assertEquals(4, readLegacy(0, 0, 3, 0, 0, 0).getFactionStage("gondor"));
    }

    @Test public void legacyDirectionsRemainIndependent() {
        KOMEAlliance migrated = readLegacy(2, 0, 0, 0, 2, 0);
        assertEquals(1, migrated.getFactionStage("gondor"));
        assertEquals(2, migrated.getFactionStage("rohan"));
    }

    @Test public void kingLossAndReplacementDoNotChangeStagesOrStartGrace() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        alliance.setFactionStage("rohan", 3, "test", 0L, 5L);
        UUID king = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "rohan", king, "First");
        KOMERulerService.removeRulerHeldBy(data, king);
        assertEquals(3, alliance.getFactionStage("rohan"));
        KOMERulerService.assignRuler(data, "rohan", UUID.randomUUID(), "Second");
        assertEquals(3, alliance.getFactionStage("rohan"));
        NBTTagCompound ledger = alliance.getFactionLedger("rohan").writeToNBT();
        assertFalse(ledger.hasKey("SuccessionEndMillis"));
        assertFalse(ledger.hasKey("GraceEndMillis"));
    }

    @Test public void stageNamesAreStableAndAsciiSafe() {
        assertEquals("Formal Neutrality", KOMEAlliance.stageName(0));
        assertEquals("Cooperation", KOMEAlliance.stageName(1));
        assertEquals("Friends", KOMEAlliance.stageName(2));
        assertEquals("Allies", KOMEAlliance.stageName(3));
        assertEquals("Military Partnership", KOMEAlliance.stageName(4));
    }

    private static KOMEAlliance active(String first, String second) {
        KOMEAlliance alliance = new KOMEAlliance(first, second);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        return alliance;
    }

    private static KOMEAlliance readLegacy(int aCivil, int aTrade, int aMilitary,
            int bCivil, int bTrade, int bMilitary) {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger("SchemaVersion", 6);
        saved.setString("FactionA", "gondor");
        saved.setString("FactionB", "rohan");
        saved.setString("CivilStatus", "active");
        saved.setString("TradeStatus", "active");
        saved.setString("MilitaryStatus", "active");
        NBTTagList ledgers = new NBTTagList();
        KOMEAllianceFactionLedger a = new KOMEAllianceFactionLedger("gondor");
        a.setCompletedTier(KOMEAlliance.CIVIL, aCivil);
        a.setCompletedTier(KOMEAlliance.TRADE, aTrade);
        a.setCompletedTier(KOMEAlliance.MILITARY, aMilitary);
        KOMEAllianceFactionLedger b = new KOMEAllianceFactionLedger("rohan");
        b.setCompletedTier(KOMEAlliance.CIVIL, bCivil);
        b.setCompletedTier(KOMEAlliance.TRADE, bTrade);
        b.setCompletedTier(KOMEAlliance.MILITARY, bMilitary);
        ledgers.appendTag(a.writeToNBT());
        ledgers.appendTag(b.writeToNBT());
        saved.setTag("FactionLedgers", ledgers);
        KOMEAlliance result = new KOMEAlliance("", "");
        result.readFromNBT(saved);
        return result;
    }
}
