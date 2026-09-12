package kome.common.data;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class KOMEWaypointAccessServiceTest {
    @Test
    public void unpledgedPlayerRetainsNativeLotrAccess() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "mordor");
        data.conquestTiles.put(tile.id, tile);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "", false, tile.id, true);

        assertTrue(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DISABLED, decision.state);
    }

    @Test
    public void ownTerritoryUsesNativeEligibility() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "gondor");
        data.conquestTiles.put(tile.id, tile);

        KOMEWaypointAccessService.Decision allowed =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);

        assertTrue(allowed.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.OWN, allowed.state);

        KOMEWaypointAccessService.Decision nativeDenied =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, false);

        assertFalse(nativeDenied.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.OWN, nativeDenied.state);
    }

    @Test
    public void neutralForeignTerritoryIsDenied() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "rohan");
        data.conquestTiles.put(tile.id, tile);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);

        assertFalse(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DENIED, decision.state);
    }

    @Test
    public void friendsRelationAllowsForeignTerritory() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "rohan");
        data.conquestTiles.put(tile.id, tile);

        KOMEDiplomacyRecord diplomacy = new KOMEDiplomacyRecord("gondor", "rohan");
        diplomacy.relation = KOMEDiplomacyRelation.FRIENDS;
        data.canonicalDiplomacyRecords.put(diplomacy.key(), diplomacy);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);

        assertTrue(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.ALLY, decision.state);
    }

    @Test
    public void alliesRelationAllowsForeignTerritory() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "rohan");
        data.conquestTiles.put(tile.id, tile);

        KOMEDiplomacyRecord diplomacy = new KOMEDiplomacyRecord("gondor", "rohan");
        diplomacy.relation = KOMEDiplomacyRelation.ALLIES;
        data.canonicalDiplomacyRecords.put(diplomacy.key(), diplomacy);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);

        assertTrue(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.ALLY, decision.state);
    }

    @Test
    public void unclaimedTerritoryRetainsNativeLotrAccess() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = new KOMEConquestTile("A1");
        data.conquestTiles.put(tile.id, tile);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);

        assertTrue(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.UNCLAIMED, decision.state);
    }

    @Test
    public void operatorBypassStillPreservesNativeEligibility() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "mordor");
        data.conquestTiles.put(tile.id, tile);

        KOMEWaypointAccessService.Decision allowed =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", true, tile.id, true);

        assertTrue(allowed.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.BYPASS, allowed.state);

        KOMEWaypointAccessService.Decision nativeDenied =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", true, tile.id, false);

        assertFalse(nativeDenied.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.BYPASS, nativeDenied.state);
    }

    private static KOMEConquestTile claimedTile(String id, String faction) {
        KOMEConquestTile tile = new KOMEConquestTile(id);
        tile.claim(faction, 0L);
        return tile;
    }
}
