package kome.common.data;

import lotr.common.LOTRPlayerData;
import lotr.common.fac.LOTRFactionRelations;
import lotr.common.world.map.LOTRWaypoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class KOMEWaypointAccessServiceTest {
    private LOTRFactionRelations.Relation gondorRohanBefore;
    private LOTRFactionRelations.Relation gondorHobbitBefore;
    private LOTRFactionRelations.Relation gondorMordorBefore;
    private LOTRFactionRelations.Relation rohanMordorBefore;

    @Before
    public void setUpRelations() {
        gondorRohanBefore = relation("gondor", "rohan");
        gondorHobbitBefore = relation("gondor", "hobbit");
        gondorMordorBefore = relation("gondor", "mordor");
        rohanMordorBefore = relation("rohan", "mordor");
        setRelation("gondor", "rohan", LOTRFactionRelations.Relation.NEUTRAL);
        setRelation("gondor", "hobbit", LOTRFactionRelations.Relation.NEUTRAL);
        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        setRelation("rohan", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
    }

    @After
    public void restoreRelations() {
        setRelation("gondor", "rohan", gondorRohanBefore);
        setRelation("gondor", "hobbit", gondorHobbitBefore);
        setRelation("gondor", "mordor", gondorMordorBefore);
        setRelation("rohan", "mordor", rohanMordorBefore);
    }

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
    public void neutralForeignTerritoryIsAllowed() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "rohan");
        data.conquestTiles.put(tile.id, tile);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);

        assertTrue(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DIPLOMATIC, decision.state);
    }

    @Test
    public void activeWarOverridesAcceptedDiplomacy() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "rohan");
        data.conquestTiles.put(tile.id, tile);

        setRelation("gondor", "rohan", LOTRFactionRelations.Relation.ALLY);

        KOMEWarService.createWar(
            data, "gondor", "rohan", "Test War", "test", 1L);

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

        setRelation("gondor", "rohan", LOTRFactionRelations.Relation.FRIEND);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);

        assertTrue(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DIPLOMATIC, decision.state);
    }

    @Test
    public void alliesRelationAllowsForeignTerritory() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "rohan");
        data.conquestTiles.put(tile.id, tile);

        setRelation("gondor", "rohan", LOTRFactionRelations.Relation.ALLY);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);

        assertTrue(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DIPLOMATIC, decision.state);
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
    public void operatorCannotBypassHostileCurrentController() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "mordor");
        data.conquestTiles.put(tile.id, tile);

        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        KOMEWaypointAccessService.Decision enemy =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", true, tile.id, true);
        assertFalse(enemy.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DENIED, enemy.state);
        assertEquals("mordor", enemy.diplomaticOwner);

        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.MORTAL_ENEMY);
        KOMEWaypointAccessService.Decision mortalEnemy =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", true, tile.id, true);
        assertFalse(mortalEnemy.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DENIED, mortalEnemy.state);
    }

    @Test
    public void finalRevalidationDeniesRelationChangedAfterRequestAcceptance() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "rohan");
        data.conquestTiles.put(tile.id, tile);

        setRelation("gondor", "rohan", LOTRFactionRelations.Relation.NEUTRAL);
        KOMEWaypointAccessService.Decision requestDecision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);
        assertTrue(requestDecision.finalAllowed);

        setRelation("gondor", "rohan", LOTRFactionRelations.Relation.ENEMY);
        KOMEWaypointAccessService.Decision finalDecision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);
        assertFalse(finalDecision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DENIED, finalDecision.state);
    }

    @Test
    public void staleFriendlyRecordCannotOverrideHostileLotrRelation() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("A1", "rohan");
        data.conquestTiles.put(tile.id, tile);
        KOMEDiplomacyRecord stale = new KOMEDiplomacyRecord("gondor", "rohan");
        stale.relation = KOMEDiplomacyRelation.ALLIES;
        data.canonicalDiplomacyRecords.put(stale.key(), stale);
        setRelation("gondor", "rohan", LOTRFactionRelations.Relation.ENEMY);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluateResolvedTile(
                data, UUID.randomUUID(), "gondor", false, tile.id, true);

        assertFalse(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DENIED, decision.state);
    }

    @Test
    public void unresolvedServerPlayerWithActiveTargetFailsClosedAndClearsTarget() {
        LOTRPlayerData playerData = new LOTRPlayerData(UUID.randomUUID());
        playerData.setTargetFTWaypoint(LOTRWaypoint.BREE);

        assertFalse(KOMEWaypointAccessService.allowFinalTravel(playerData));
        assertEquals(null, playerData.getTargetFTWaypoint());
    }

    @Test
    public void nativeWaypointFactionUsesTheFiveRungNeutralThresholdWithoutATile() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID playerId = UUID.randomUUID();

        assertWaypointRelation(data, playerId,
            LOTRFactionRelations.Relation.MORTAL_ENEMY, false);
        assertWaypointRelation(data, playerId,
            LOTRFactionRelations.Relation.ENEMY, false);
        assertWaypointRelation(data, playerId,
            LOTRFactionRelations.Relation.NEUTRAL, true);
        assertWaypointRelation(data, playerId,
            LOTRFactionRelations.Relation.FRIEND, true);
        assertWaypointRelation(data, playerId,
            LOTRFactionRelations.Relation.ALLY, true);
    }

    @Test
    public void nativeWaypointFactionDoesNotVetoAValidCurrentController() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("T152", "gondor");
        data.conquestTiles.put(tile.id, tile);
        data.linkTileWaypoint(tile.id, LOTRWaypoint.HOBBITON, null, "test");
        setRelation("gondor", "hobbit", LOTRFactionRelations.Relation.MORTAL_ENEMY);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluate(
                data, UUID.randomUUID(), "gondor", false, LOTRWaypoint.HOBBITON, true);

        assertTrue(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.OWN, decision.state);
        assertEquals("hobbit", decision.waypointFaction);
        assertEquals("gondor", decision.tileOwner);
        assertEquals("gondor", decision.diplomaticOwner);
    }

    @Test
    public void hostileTerritorialControllerRemainsAnIndependentVeto() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("T152", "rohan");
        data.conquestTiles.put(tile.id, tile);
        data.linkTileWaypoint(tile.id, LOTRWaypoint.HOBBITON, null, "test");
        setRelation("gondor", "hobbit", LOTRFactionRelations.Relation.NEUTRAL);
        setRelation("gondor", "rohan", LOTRFactionRelations.Relation.ENEMY);

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluate(
                data, UUID.randomUUID(), "gondor", false, LOTRWaypoint.HOBBITON, true);

        assertFalse(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DENIED, decision.state);
        assertEquals("hobbit", decision.waypointFaction);
        assertEquals("rohan", decision.tileOwner);
        assertEquals("rohan", decision.diplomaticOwner);
    }

    @Test
    public void nativeMordorWaypointUsesMordorUntilGondorConquersItsTile() {
        LOTRWaypoint waypoint = waypointForFaction("mordor");
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("T277", "mordor");
        data.conquestTiles.put(tile.id, tile);
        data.linkTileWaypoint(tile.id, waypoint, null, "test");

        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        KOMEWaypointAccessService.Decision originalOwner =
            KOMEWaypointAccessService.evaluate(
                data, UUID.randomUUID(), "gondor", false, waypoint, true);
        assertFalse(originalOwner.finalAllowed);
        assertEquals("mordor", originalOwner.diplomaticOwner);

        tile.claim("gondor", 1L);
        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.MORTAL_ENEMY);
        KOMEWaypointAccessService.Decision conquered =
            KOMEWaypointAccessService.evaluate(
                data, UUID.randomUUID(), "gondor", false, waypoint, true);
        assertTrue(conquered.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.OWN, conquered.state);
        assertEquals("gondor", conquered.diplomaticOwner);
        assertEquals("mordor", conquered.waypointFaction);
    }

    @Test
    public void conqueredNativeMordorWaypointUsesOnlyCurrentGondorRelation() {
        LOTRWaypoint waypoint = waypointForFaction("mordor");
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = claimedTile("T277", "gondor");
        data.conquestTiles.put(tile.id, tile);
        data.linkTileWaypoint(tile.id, waypoint, null, "test");
        setRelation("rohan", "mordor", LOTRFactionRelations.Relation.ALLY);

        setRelation("gondor", "rohan", LOTRFactionRelations.Relation.ENEMY);
        KOMEWaypointAccessService.Decision hostileOwner =
            KOMEWaypointAccessService.evaluate(
                data, UUID.randomUUID(), "rohan", false, waypoint, true);
        assertFalse(hostileOwner.finalAllowed);
        assertEquals("gondor", hostileOwner.diplomaticOwner);
        assertTrue(hostileOwner.reason.contains("requires a Neutral-or-better"));

        setRelation("gondor", "rohan", LOTRFactionRelations.Relation.MORTAL_ENEMY);
        assertFalse(KOMEWaypointAccessService.evaluate(
            data, UUID.randomUUID(), "rohan", false, waypoint, true).finalAllowed);

        setRelation("rohan", "mordor", LOTRFactionRelations.Relation.MORTAL_ENEMY);
        for (LOTRFactionRelations.Relation allowed : new LOTRFactionRelations.Relation[] {
                LOTRFactionRelations.Relation.NEUTRAL,
                LOTRFactionRelations.Relation.FRIEND,
                LOTRFactionRelations.Relation.ALLY}) {
            setRelation("gondor", "rohan", allowed);
            KOMEWaypointAccessService.Decision decision =
                KOMEWaypointAccessService.evaluate(
                    data, UUID.randomUUID(), "rohan", false, waypoint, true);
            assertTrue(allowed + " should permit current-owner access",
                decision.finalAllowed);
            assertEquals("gondor", decision.diplomaticOwner);
            assertEquals(KOMEWaypointAccessService.State.DIPLOMATIC, decision.state);
        }
    }

    @Test
    public void unclaimedLinkedTileUsesNativeFactionAsExplicitFallback() {
        LOTRWaypoint waypoint = waypointForFaction("mordor");
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = new KOMEConquestTile("T277");
        data.conquestTiles.put(tile.id, tile);
        data.linkTileWaypoint(tile.id, waypoint, null, "test");

        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.ENEMY);
        KOMEWaypointAccessService.Decision denied =
            KOMEWaypointAccessService.evaluate(
                data, UUID.randomUUID(), "gondor", false, waypoint, true);
        assertFalse(denied.finalAllowed);
        assertEquals("mordor", denied.diplomaticOwner);
        assertTrue(denied.reason.contains("ownership is the fallback"));

        setRelation("gondor", "mordor", LOTRFactionRelations.Relation.NEUTRAL);
        KOMEWaypointAccessService.Decision allowed =
            KOMEWaypointAccessService.evaluate(
                data, UUID.randomUUID(), "gondor", false, waypoint, true);
        assertTrue(allowed.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.NATIVE_FALLBACK, allowed.state);
        assertEquals("mordor", allowed.diplomaticOwner);
    }

    @Test
    public void ambiguousCanonicalWaypointLinksFailClosedForOverlayAndServer() {
        LOTRWaypoint waypoint = waypointForFaction("mordor");
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile first = claimedTile("T277", "gondor");
        KOMEConquestTile second = claimedTile("T278", "mordor");
        data.conquestTiles.put(first.id, first);
        data.conquestTiles.put(second.id, second);
        data.linkTileWaypoint(first.id, waypoint, null, "test");
        data.linkTileWaypoint(second.id, waypoint, null, "test");

        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluate(
                data, UUID.randomUUID(), "gondor", false, waypoint, true);
        assertFalse(decision.finalAllowed);
        assertEquals(KOMEWaypointAccessService.State.DENIED, decision.state);
        assertTrue(decision.reason.contains("multiple canonical conquest-tile links"));
    }

    private static KOMEConquestTile claimedTile(String id, String faction) {
        KOMEConquestTile tile = new KOMEConquestTile(id);
        tile.claim(faction, 0L);
        return tile;
    }

    private static void assertWaypointRelation(KOMEWorldData data, UUID playerId,
            LOTRFactionRelations.Relation relation, boolean expectedAllowed) {
        setRelation("gondor", "hobbit", relation);
        KOMEWaypointAccessService.Decision decision =
            KOMEWaypointAccessService.evaluate(
                data, playerId, "gondor", false, LOTRWaypoint.HOBBITON, true);
        assertEquals(expectedAllowed, decision.finalAllowed);
        assertEquals("hobbit", decision.waypointFaction);
        assertEquals(expectedAllowed ? KOMEWaypointAccessService.State.NATIVE_FALLBACK
            : KOMEWaypointAccessService.State.DENIED, decision.state);
    }

    private static LOTRWaypoint waypointForFaction(String faction) {
        for (LOTRWaypoint waypoint : LOTRWaypoint.values()) {
            if (faction.equals(
                    KOMEWaypointAccessService.resolveWaypointFaction(waypoint))) {
                return waypoint;
            }
        }
        assertNotNull("No native waypoint found for " + faction, null);
        return null;
    }

    private static void setRelation(String first, String second,
            LOTRFactionRelations.Relation relation) {
        LOTRFactionRelations.overrideRelations(
            KOMEAlliance.findLotrFaction(first),
            KOMEAlliance.findLotrFaction(second),
            relation);
    }

    private static LOTRFactionRelations.Relation relation(String first, String second) {
        return LOTRFactionRelations.getRelations(
            KOMEAlliance.findLotrFaction(first),
            KOMEAlliance.findLotrFaction(second));
    }
}
