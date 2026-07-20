package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import lotr.common.world.map.LOTRWaypoint;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMEAllianceModelTest {
    @Test
    public void canonicalPairIsSymmetricAndRejectsSelfPair() {
        assertEquals("gondor|rohan", KOMEAlliance.pairKey("Rohan", "Gondor"));
        assertEquals(KOMEAlliance.pairKey("gondor", "rohan"), KOMEAlliance.pairKey("rohan", "gondor"));
        KOMEWorldData data = new KOMEWorldData("test");
        assertSame(data.getAlliance("gondor", "rohan", true), data.getAlliance("rohan", "gondor", false));
        assertNull(data.getAlliance("gondor", "GONDOR", true));
    }

    @Test
    public void hierarchyAndStatusAreIndependentFromTier() {
        KOMEAlliance alliance = new KOMEAlliance("gondor", "rohan");
        alliance.requestTrack(KOMEAlliance.MILITARY, "king", 12L, true);
        assertEquals(KOMEAllianceTrackStatus.PENDING, alliance.getStatus(KOMEAlliance.CIVIL));
        assertEquals(KOMEAllianceTrackStatus.PENDING, alliance.getStatus(KOMEAlliance.TRADE));
        assertEquals(KOMEAllianceTrackStatus.PENDING, alliance.getStatus(KOMEAlliance.MILITARY));
        alliance.acceptTrack(KOMEAlliance.MILITARY, "king2", 13L);
        assertTrue(alliance.hasAccepted(KOMEAlliance.CIVIL));
        assertTrue(alliance.hasAccepted(KOMEAlliance.TRADE));
        assertTrue(alliance.hasAccepted(KOMEAlliance.MILITARY));
        assertEquals(0, alliance.getTier(KOMEAlliance.MILITARY));
    }

    @Test
    public void sharedTierRequiresBothFactionLedgersOrWaiver() {
        KOMEAlliance alliance = new KOMEAlliance("gondor", "rohan");
        alliance.requestTrack(KOMEAlliance.CIVIL, "king", 1L, false);
        alliance.getFactionLedger("gondor").setCompletedTier(KOMEAlliance.CIVIL, 1);
        assertFalse(KOMEAllianceProgressionService.recomputeSharedTier(alliance, KOMEAlliance.CIVIL, 2L));
        assertEquals(0, alliance.civilTier);
        alliance.getFactionLedger("rohan").kinglessWaived = true;
        assertTrue(KOMEAllianceProgressionService.recomputeSharedTier(alliance, KOMEAlliance.CIVIL, 3L));
        assertEquals(1, alliance.civilTier);
    }

    @Test
    public void pendingPartiesAndLedgersRoundTrip() {
        KOMEAlliance source = new KOMEAlliance("rohan", "gondor");
        source.requestTrack(KOMEAlliance.TRADE, "Theoden", 50L, true);
        source.setPendingParties(KOMEAlliance.TRADE, "rohan", "gondor");
        source.setAssignment("rohan", "trade.t1", "REQ2|minecraft:wheat|0|32|Wheat");
        source.addDelivered("rohan", "trade.t1", 12);
        KOMEAlliance loaded = new KOMEAlliance("", "");
        loaded.readFromNBT(source.writeToNBT());
        assertEquals("rohan", loaded.getRequestedBy(KOMEAlliance.TRADE));
        assertEquals("gondor", loaded.getPendingReceiver(KOMEAlliance.TRADE));
        assertEquals(12, loaded.getDelivered("rohan", "trade.t1"));
        assertEquals(source.getPairKey(), loaded.getPairKey());
    }

    @Test
    public void legacyDirectionsMergeWithoutLosingSideProgress() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        list.appendTag(legacy("rohan", "gondor", 1, 7));
        list.appendTag(legacy("gondor", "rohan", 2, 11));
        root.setTag("Alliances", list);
        KOMEWorldData data = new KOMEWorldData("test");
        data.readFromNBT(root);
        assertEquals(1, data.alliances.size());
        KOMEAlliance merged = data.getAlliance("gondor", "rohan", false);
        assertNotNull(merged);
        assertEquals(2, merged.civilTier);
        assertEquals(1, merged.getFactionLedger("rohan").getCompletedTier(KOMEAlliance.CIVIL));
        assertEquals(2, merged.getFactionLedger("gondor").getCompletedTier(KOMEAlliance.CIVIL));
        assertEquals(7, merged.getDelivered("rohan", "civil.trade"));
        assertEquals(11, merged.getDelivered("gondor", "civil.trade"));
        assertEquals(7, merged.getDelivered("rohan", KOMEAllianceProgressionService.ALLIED_TRADES));
        assertEquals(11, merged.getDelivered("gondor", KOMEAllianceProgressionService.ALLIED_TRADES));
    }

    @Test
    public void malformedSelfAllianceIsQuarantined() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        list.appendTag(legacy("gondor", "gondor", 1, 0));
        root.setTag("Alliances", list);
        KOMEWorldData data = new KOMEWorldData("test");
        data.readFromNBT(root);
        assertTrue(data.alliances.isEmpty());
        assertEquals(1, data.quarantinedAllianceRecords.size());
    }

    @Test
    public void waypointGateUsesCurrentOwnerCivilTierToggleAndBypass() {
        String tileId = "T001";
        UUID player = UUID.randomUUID();
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = new KOMEConquestTile(tileId);
        tile.claim("rohan", 0L);
        data.conquestTiles.put(tile.id, tile);

        KOMEWaypointAccessService.Decision denied = KOMEWaypointAccessService.evaluateResolvedTile(data, player, "gondor", false, tileId, true);
        assertFalse(denied.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DENIED, denied.state);

        tile.claim("gondor", 1L);
        assertEquals(KOMEWaypointAccessService.State.OWN,
            KOMEWaypointAccessService.evaluateResolvedTile(data, player, "gondor", false, tileId, true).state);
        tile.clearOwnershipOnly();
        assertEquals(KOMEWaypointAccessService.State.UNCLAIMED,
            KOMEWaypointAccessService.evaluateResolvedTile(data, player, "gondor", false, tileId, true).state);
        tile.claim("rohan", 2L);

        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.setTier(KOMEAlliance.CIVIL, 1, "test", 0L);
        KOMEWaypointAccessService.Decision allied = KOMEWaypointAccessService.evaluateResolvedTile(data, player, "gondor", false, tileId, true);
        assertTrue(allied.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.ALLY, allied.state);

        tile.claim("mordor", 3L);
        assertEquals(KOMEWaypointAccessService.State.DENIED,
            KOMEWaypointAccessService.evaluateResolvedTile(data, player, "gondor", false, tileId, true).state);
        tile.claim("rohan", 4L);

        alliance.setTier(KOMEAlliance.CIVIL, 0, "test", 0L);
        data.setWaypointRestrictionBypass(player, true);
        KOMEWaypointAccessService.Decision bypass = KOMEWaypointAccessService.evaluateResolvedTile(data, player, "gondor", true, tileId, true);
        assertEquals(KOMEWaypointAccessService.State.BYPASS, bypass.state);
        assertTrue(bypass.finalAllowed);
        assertFalse(KOMEWaypointAccessService.evaluateResolvedTile(data, player, "gondor", true, tileId, false).finalAllowed);
        data.setWaypointRestrictionBypass(player, false);
        assertEquals(KOMEWaypointAccessService.State.UNMAPPED,
            KOMEWaypointAccessService.evaluateResolvedTile(data, player, "gondor", false, "T999", true).state);
        data.waypointRestrictionEnabled = false;
        assertEquals(KOMEWaypointAccessService.State.DISABLED,
            KOMEWaypointAccessService.evaluateResolvedTile(data, player, "gondor", false, tileId, true).state);
    }

    @Test
    public void graceAdministrationChangesAndPersistsOnlyTheAffectedSide() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        alliance.setTier(KOMEAlliance.CIVIL, 2, "test", 0L);
        alliance.getFactionLedger("gondor").setCompletedTier(KOMEAlliance.CIVIL, 2);
        alliance.getFactionLedger("rohan").setCompletedTier(KOMEAlliance.CIVIL, 1);
        long now = 10_000L;
        KOMEAllianceGraceService.set(alliance, "gondor", KOMEAllianceGraceService.CONTRIBUTION, now, 60_000L);
        KOMEAllianceGraceService.set(alliance, "rohan", KOMEAllianceGraceService.CONTRIBUTION, now, 120_000L);
        long gondorDeadline = alliance.getFactionLedger("gondor").graceEndMillis;
        long rohanDeadline = alliance.getFactionLedger("rohan").graceEndMillis;
        assertNotEquals(gondorDeadline, rohanDeadline);

        KOMEAllianceGraceService.set(alliance, "rohan", KOMEAllianceGraceService.CONTRIBUTION, now, 180_000L);
        assertEquals(gondorDeadline, alliance.getFactionLedger("gondor").graceEndMillis);
        assertEquals(now + 180_000L, alliance.getFactionLedger("rohan").graceEndMillis);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData loaded = new KOMEWorldData("test");
        loaded.readFromNBT(saved);
        KOMEAlliance restored = loaded.getAlliance("gondor", "rohan", false);
        assertEquals(gondorDeadline, restored.getFactionLedger("gondor").graceEndMillis);
        assertEquals(now + 180_000L, restored.getFactionLedger("rohan").graceEndMillis);

        KOMEAllianceGraceService.expire(loaded, restored, "rohan", KOMEAllianceGraceService.CONTRIBUTION,
            now + 1L, 20L);
        assertEquals(0L, restored.getFactionLedger("rohan").graceEndMillis);
        assertEquals(gondorDeadline, restored.getFactionLedger("gondor").graceEndMillis);
        assertEquals(1, restored.civilTier);
    }

    @Test
    public void successionExpirationIsSideSpecificAndRejectsThirdParties() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "mordor", true);
        alliance.requestTrack(KOMEAlliance.MILITARY, "test", 0L, false);
        long now = 50_000L;
        KOMEAllianceGraceService.set(alliance, "gondor", KOMEAllianceGraceService.SUCCESSION, now, 60_000L);
        KOMEAllianceGraceService.set(alliance, "mordor", KOMEAllianceGraceService.SUCCESSION, now, 120_000L);
        long gondorDeadline = alliance.getFactionLedger("gondor").successionEndMillis;
        KOMEAllianceGraceService.expire(data, alliance, "mordor", KOMEAllianceGraceService.SUCCESSION,
            now + 1L, 30L);
        assertEquals(gondorDeadline, alliance.getFactionLedger("gondor").successionEndMillis);
        assertEquals(0L, alliance.getFactionLedger("mordor").successionEndMillis);
        KOMEAlliance fallback = new KOMEAlliance("gondor", "mordor");
        fallback.requestTrack(KOMEAlliance.MILITARY, "test", 0L, false);
        KOMEWorldData.applySuccessionRelationCap(fallback,
            lotr.common.fac.LOTRFactionRelations.Relation.ENEMY, 31L);
        assertFalse(fallback.hasAnyAlliance());
        try {
            KOMEAllianceGraceService.set(alliance, "rohan", KOMEAllianceGraceService.SUCCESSION, now, 1000L);
            fail("Third-party affected factions must be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("canonical alliance pair"));
        }
    }

    @Test
    public void allianceHierarchyBreaksRetainOnlyPermittedLowerTracks() {
        KOMEAlliance alliance = new KOMEAlliance("gondor", "rohan");
        alliance.requestTrack(KOMEAlliance.MILITARY, "test", 0L, false);
        assertTrue(alliance.hasAccepted(KOMEAlliance.CIVIL));
        assertTrue(alliance.hasAccepted(KOMEAlliance.TRADE));
        assertTrue(alliance.hasAccepted(KOMEAlliance.MILITARY));
        alliance.breakTrack(KOMEAlliance.MILITARY, "test", 1L);
        assertTrue(alliance.hasAccepted(KOMEAlliance.CIVIL));
        assertTrue(alliance.hasAccepted(KOMEAlliance.TRADE));
        assertFalse(alliance.hasAccepted(KOMEAlliance.MILITARY));
        alliance.requestTrack(KOMEAlliance.MILITARY, "test", 2L, false);
        alliance.breakTrack(KOMEAlliance.TRADE, "test", 3L);
        assertTrue(alliance.hasAccepted(KOMEAlliance.CIVIL));
        assertFalse(alliance.hasAccepted(KOMEAlliance.TRADE));
        assertFalse(alliance.hasAccepted(KOMEAlliance.MILITARY));
        alliance.requestTrack(KOMEAlliance.MILITARY, "test", 4L, false);
        alliance.breakTrack(KOMEAlliance.CIVIL, "test", 5L);
        assertFalse(alliance.hasAnyAlliance());
    }

    @Test
    public void kingLossAndReplacementAffectOnlyThatFactionLedger() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        alliance.getFactionLedger("rohan").setCompletedTier(KOMEAlliance.CIVIL, 1);
        UUID firstKing = UUID.randomUUID();
        assertTrue(data.claimFactionKing("rohan", "Rohan", firstKing, "First King"));
        assertFalse(data.reconcilePlayerKingship("rohan", firstKing, "First King", false));
        assertTrue(alliance.getFactionLedger("rohan").successionEndMillis > 0L);
        assertEquals(0L, alliance.getFactionLedger("gondor").successionEndMillis);
        UUID replacement = UUID.randomUUID();
        assertTrue(data.claimFactionKing("rohan", "Rohan", replacement, "Replacement"));
        assertEquals(0L, alliance.getFactionLedger("rohan").successionEndMillis);
        assertEquals(1, alliance.getFactionLedger("rohan").getCompletedTier(KOMEAlliance.CIVIL));

        alliance.getFactionLedger("rohan").kinglessWaived = true;
        data.onFactionKingGained("rohan", 1000L);
        assertTrue(alliance.getFactionLedger("rohan").graceEndMillis > 1000L);
        assertEquals(0L, alliance.getFactionLedger("gondor").graceEndMillis);
    }

    private static NBTTagCompound legacy(String from, String to, int civilTier, int tradeProgress) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("FactionA", from);
        nbt.setString("FactionB", to);
        nbt.setInteger("CivilTier", civilTier);
        nbt.setInteger("TradeTier", KOMEAlliance.NONE);
        nbt.setInteger("MilitaryTier", KOMEAlliance.NONE);
        NBTTagList delivered = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("ID", "civil.trade");
        entry.setInteger("Amount", tradeProgress);
        delivered.appendTag(entry);
        nbt.setTag("Delivered", delivered);
        return nbt;
    }
}
