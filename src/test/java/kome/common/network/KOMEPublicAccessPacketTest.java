package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import kome.common.KOMEAccessFixture;
import kome.common.command.KOMECommandKome;
import kome.common.data.*;
import lotr.common.fac.LOTRFaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEPublicAccessPacketTest {
    private KOMEAccessFixture f;
    private SimpleNetworkWrapper previous;
    private KOMEConquestTile tile;

    @Before public void setup() throws Exception {
        f = new KOMEAccessFixture();
        previous = KOMEPacketHandler.network; KOMEPacketHandler.network = f.network;
        List<String> ids = new ArrayList<String>(KOMEConquestTileDefaults.getKnownTileIds());
        Collections.sort(ids); assertTrue(ids.size() > 100);
        tile = new KOMEConquestTile(ids.get(0)); tile.claim("gondor", 0L);
        f.data.conquestTiles.put(tile.id, tile);
        f.pledge(LOTRFaction.GONDOR);
        f.data.setDirty(false);
    }

    @After public void cleanup() {
        KOMEPacketHandler.clearPendingServerTasks();
        KOMEPacketHandler.network = previous;
    }

    @Test public void publicTileRequestIsQueuedAndPublishesExactPublicDataWithoutMutation() throws Exception {
        KOMEPopulationService.grantCenti(f.data, "gondor", 2450L);
        KOMEBuildService.create(f.data, "Hall", tile.id, 0, 0, 64, 0, UUID.randomUUID(), "Builder",
            "gondor", "gondor", KOMEBuildType.NORMAL, 125L, 1L);
        f.data.setDirty(false);
        KOMEPacketConquestOpenCapture request = new KOMEPacketConquestOpenCapture(tile.id);
        new KOMEPacketHandler.ServerThreadHandler<KOMEPacketConquestOpenCapture>(new KOMEPacketConquestOpenCapture.Handler()) {}
            .onMessage(request, f.context);
        assertTrue(f.network.messages.isEmpty());
        assertEquals(1, KOMEPacketHandler.runPendingServerTasks());
        assertEquals(1, f.network.messages.size());
        KOMEPacketConquestCaptureGui response = (KOMEPacketConquestCaptureGui) f.network.messages.get(0);
        assertEquals(tile.id, response.tileId);
        assertEquals(2450L, response.population.availablePopulationCenti);
        assertEquals(1, response.builds.size());
        assertEquals(125L, response.builds.get(0).approvedCentiHours);
        assertFalse(response.builds.get(0).canManage);
        assertFalse(response.canClaim);
        assertFalse(f.data.isDirty());
        assertTrue(f.data.progressions.isEmpty());
        new KOMECommandKome().processCommand(f.player, new String[] {"tile", tile.id});
        assertEquals(2, f.network.messages.size());
    }

    @Test public void unknownAndRetiredTilesAreNotCreatedOrPublishedEvenByForgedRequests() {
        for (String id : new String[] {"", "../bad", "T999999", "T045"}) {
            new KOMEPacketConquestOpenCapture.Handler().onMessage(new KOMEPacketConquestOpenCapture(id), f.context);
            new KOMEPacketConquestClaim.Handler().onMessage(new KOMEPacketConquestClaim(id), f.context);
            new KOMEPacketConquestTransfer.Handler().onMessage(new KOMEPacketConquestTransfer(id, "rohan"), f.context);
        }
        assertEquals(1, f.data.conquestTiles.size());
        assertTrue(f.network.messages.isEmpty()); assertFalse(f.data.isDirty());
        assertTrue(f.data.progressions.isEmpty()); assertTrue(f.data.centralAudit.isEmpty());
    }

    @Test public void ordinaryPopulationUnitsAndCompanyScreensDoNotRequireOperator() {
        new kome.common.command.KOMECommandPopulation().processCommand(f.player, new String[] {"units"});
        new kome.common.command.KOMECommandTroops().processCommand(f.player, new String[] {"companies"});
        assertEquals(2, f.network.messages.size());
        assertTrue(f.network.messages.get(0) instanceof KOMEPacketPopulationUnitsGui);
        assertTrue(f.network.messages.get(1) instanceof KOMEPacketCompanyListGui);
        new kome.common.command.KOMECommandConquest().processCommand(f.player, new String[] {"get", tile.id});
        assertTrue(f.player.messages.toString().contains(tile.id));
        assertFalse(f.data.isDirty());
    }

    @Test public void ordinaryMapMetadataIsPublicButUnknownAndRetiredRecordsAreNot() {
        for (String id : KOMEConquestTileDefaults.getKnownTileIds()) {
            KOMEConquestTile ordinary = new KOMEConquestTile(id);
            f.data.conquestTiles.put(id, ordinary);
            assertSame(ordinary, f.data.getPublicConquestTile(id));
        }
        f.data.conquestTiles.put("T045", new KOMEConquestTile("T045"));
        f.data.conquestTiles.put("T999999", new KOMEConquestTile("T999999"));
        assertNull(f.data.getPublicConquestTile("T045"));
        assertNull(f.data.getPublicConquestTile("T999999"));
        assertFalse(f.data.isDirty());
    }

    @Test public void operatorStatusDoesNotReplaceForeignConstructionOrPopulationRules() {
        f.player.operator = true;
        tile.claim("rohan", 0L); f.data.setDirty(false);
        KOMEPacketBuildAction request = new KOMEPacketBuildAction();
        request.action = "create"; request.tileId = tile.id; request.text = "Foreign";
        request.populationFaction = "gondor"; request.buildType = "NORMAL"; request.y = 64;
        new KOMEPacketBuildAction.Handler().onMessage(request, f.context);
        assertTrue(f.data.builds.isEmpty()); assertFalse(f.data.isDirty());
        assertFalse(KOMEPopulationService.trySpendCenti(f.data, "gondor", 1L));
        assertFalse(f.data.isDirty()); assertTrue(f.data.factionPopulations.isEmpty());
    }

    @Test public void forgedReviewRejectsWithoutAuditDirtyOrResponseAndRealManagerSucceeds() {
        UUID builder = UUID.randomUUID();
        KOMEPlayerBuild build = KOMEBuildService.create(f.data, "Hall", tile.id, 0, 0, 64, 0,
            builder, "Builder", "gondor", "gondor", KOMEBuildType.NORMAL, 0L, 1L);
        KOMEBuildContribution contribution = KOMEBuildService.addSubmission(f.data, build, f.player.id, "Player",
            "gondor", 125L, true, 2L); // claimed manager flag cannot grant review authority
        assertTrue(contribution.isPending());
        int history = build.auditHistory().size(), audit = f.data.centralAudit.size();
        f.data.setDirty(false);
        KOMEPacketBuildAction request = new KOMEPacketBuildAction();
        request.action = "approve"; request.tileId = tile.id; request.buildId = build.id;
        request.contributionId = contribution.id; request.text = "review";
        new KOMEPacketBuildAction.Handler().onMessage(request, f.context);
        assertTrue(contribution.isPending()); assertEquals(history, build.auditHistory().size());
        assertEquals(audit, f.data.centralAudit.size()); assertFalse(f.data.isDirty());
        assertTrue(f.network.messages.isEmpty());
        // Server-owned manager identity, never a client flag.
        build.managerUuid = f.player.id;
        new KOMEPacketBuildAction.Handler().onMessage(request, f.context);
        assertTrue(f.player.messages.toString(), contribution.isApproved()); assertEquals(125L, build.approvedCentiHours());
        assertTrue(f.data.isDirty()); assertTrue(build.auditHistory().size() > history);
    }

    @Test public void permissionDenialAndForeignConstructionDoNotRepairRawOwnership() {
        tile.currentRulingFaction = " ROHAN "; tile.ownerFaction = "gondor";
        f.data.setProgressionEnabled(true); f.data.setDirty(false);
        new KOMEPacketConquestClaim.Handler().onMessage(new KOMEPacketConquestClaim(tile.id), f.context);
        assertEquals(" ROHAN ", tile.currentRulingFaction); assertEquals("gondor", tile.ownerFaction);
        assertTrue(f.data.progressions.isEmpty()); assertTrue(f.data.conquestClaimConfirmations.isEmpty());
        assertFalse(KOMEForeignConstructionService.grant(f.data, tile.id, f.player.id, "gondor", 1L).allowed);
        assertFalse(KOMEForeignConstructionService.canConstruct(f.data, tile.id, f.player.id, "gondor").allowed);
        assertEquals(" ROHAN ", tile.currentRulingFaction); assertEquals("gondor", tile.ownerFaction);
        assertFalse(f.data.isDirty()); assertTrue(f.data.centralAudit.isEmpty());
    }

    @Test public void forgedTransferAndHistoryAuthorityDoNotMutateOrPublish() {
        tile.currentRulingFaction = " ROHAN "; tile.ownerFaction = "gondor";
        new KOMEPacketConquestTransfer.Handler().onMessage(new KOMEPacketConquestTransfer(tile.id, "gondor"), f.context);
        new KOMEPacketConquestTransfer.Handler().onMessage(new KOMEPacketConquestTransfer(tile.id, "rohan", "admin"), f.context);
        new KOMEPacketMovementHistoryRequest.Handler().onMessage(new KOMEPacketMovementHistoryRequest("gondor", true), f.context);
        new KOMEPacketMovementHistoryRequest.Handler().onMessage(new KOMEPacketMovementHistoryRequest("rohan", false), f.context);
        assertFalse(tile.hasPendingTransfer()); assertEquals(" ROHAN ", tile.currentRulingFaction);
        assertEquals("gondor", tile.ownerFaction); assertFalse(f.data.isDirty());
        assertTrue(f.data.centralAudit.isEmpty()); assertTrue(f.network.messages.isEmpty());
    }

    @Test public void administrativeServerHistoryIsRedactedWithoutRemovingPublicWarStatus() {
        KOMEWar war = new KOMEWar(); war.id = "W1"; war.displayName = "Public war";
        war.addAdministrativeEvent("Staff", "REPAIR", "private repair detail", 1L);
        f.data.wars.put(war.id, war); f.data.setDirty(false);
        List publicRows = KOMEServerRecordBuilder.build(f.world);
        assertTrue(publicRows.toString().contains("Public war"));
        assertFalse(publicRows.toString().contains("private repair detail"));
        assertTrue(publicRows.toString().contains("Operator-only"));
        assertTrue(KOMEServerRecordBuilder.build(f.world, true).toString().contains("private repair detail"));
        assertFalse(f.data.isDirty());
    }

    @Test public void forgedCompanyMovementAndDeniedRecruitmentDoNotReconcileState() {
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C1"; company.name = "Other company"; company.owner = UUID.randomUUID();
        company.faction = "gondor"; company.currentTile = " " + tile.id + " ";
        company.units.add(UUID.randomUUID()); // stale record must not be repaired by a denied request
        company.totalPopulation = 99;
        f.data.armyCompanies.put(company.id, company);
        tile.currentRulingFaction = " ROHAN "; tile.ownerFaction = "gondor";
        new KOMEPacketTroopGuiAction.Handler().onMessage(
            new KOMEPacketTroopGuiAction("move", company.id, tile.id, tile.id), f.context);
        new KOMEPacketTroopGuiAction.Handler().onMessage(
            new KOMEPacketTroopGuiAction("recruit", "", "", tile.id), f.context);
        assertSame(company, f.data.armyCompanies.get(company.id));
        assertEquals(1, company.units.size()); assertEquals(99, company.totalPopulation);
        assertEquals(" " + tile.id + " ", company.currentTile);
        assertEquals(" ROHAN ", tile.currentRulingFaction); assertEquals("gondor", tile.ownerFaction);
        assertTrue(f.data.armyMovements.isEmpty()); assertTrue(f.data.centralAudit.isEmpty());
        assertTrue(f.network.messages.isEmpty()); assertFalse(f.data.isDirty());
    }

    @Test public void unpledgedAllianceInspectionDoesNotCreateProgressionOrAuthority() throws Exception {
        f.pledge(null);
        assertFalse(KOMEAllianceRecordBuilder.build(f.data, f.player).isEmpty());
        assertTrue(f.data.progressions.isEmpty()); assertFalse(f.data.isDirty());
    }

    @Test public void ownedCompanyWithIllegalRouteIsRejectedWithoutRepairingTotalsOrTiles() {
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C1"; company.name = "Own company"; company.owner = f.player.id;
        company.faction = "gondor"; company.currentTile = " " + tile.id + " ";
        company.totalPopulation = 99;
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.randomUUID(); unit.owner = f.player.id; unit.companyId = company.id;
        unit.currentTile = tile.id; unit.cost = 25;
        company.units.add(unit.entity);
        f.data.hiredUnits.put(unit.entity, unit); f.data.armyCompanies.put(company.id, company);
        tile.currentRulingFaction = " GONDOR "; tile.ownerFaction = "rohan";
        new KOMEPacketTroopGuiAction.Handler().onMessage(
            new KOMEPacketTroopGuiAction("move", company.id, "T999999", tile.id), f.context);
        assertTrue(f.player.messages.toString().contains("Troop action rejected:"));
        assertEquals(99, company.totalPopulation); assertEquals(" " + tile.id + " ", company.currentTile);
        assertEquals(" GONDOR ", tile.currentRulingFaction); assertEquals("rohan", tile.ownerFaction);
        assertEquals(1, f.data.conquestTiles.size()); assertEquals(1, company.units.size());
        assertSame(unit, f.data.hiredUnits.get(unit.entity));
        assertTrue(f.data.armyMovements.isEmpty()); assertTrue(f.data.tileWaypoints.isEmpty());
        assertTrue(f.network.messages.isEmpty()); assertFalse(f.data.isDirty());
    }

    @Test public void malformedBuildAndTilePayloadsStillFailClosed() {
        try { new KOMEPacketBuildAction().fromBytes(Unpooled.buffer().writeInt(-1)); fail(); }
        catch (RuntimeException expected) { }
        try { new KOMEPacketConquestOpenCapture().fromBytes(Unpooled.buffer().writeInt(Integer.MAX_VALUE)); fail(); }
        catch (RuntimeException expected) { }
        assertFalse(f.data.isDirty()); assertTrue(f.network.messages.isEmpty());
    }

    @Test public void existingGuiAndProtocolBoundariesRemainPublicAndUnchanged() throws Exception {
        String population = source("kome/client/gui/KOMEGuiPopulation.java");
        assertTrue(population.contains("KOMEConquestMapOverlay.openPreservedMap()"));
        String menu = source("kome/client/KOMEProgressionMenuOverlay.java");
        assertTrue(menu.contains("KOMEGuiServerRecords.class")); assertFalse(menu.contains("canCommandSenderUseCommand"));
        String capture = source("kome/client/gui/KOMEGuiConquestCapture.java");
        assertTrue(capture.contains("Canonical Population")); assertTrue(capture.contains("Builds"));
        assertFalse(capture.contains("canCommandSenderUseCommand"));
        String registration = source("kome/common/network/KOMEPacketHandler.java");
        assertTrue(registration.contains("KOMEPacketConquestOpenCapture.class, 7, Side.SERVER"));
        assertTrue(registration.contains("KOMEPacketBuildAction.class, 36, Side.SERVER"));
        assertTrue(registration.contains("IDs 23 and 24 are retired"));
        assertTrue(source("kome/common/network/KOMEPopulationWire.java").contains("1.0.8-integration-g1"));
        assertEquals(3, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/" + path)), StandardCharsets.UTF_8);
    }
}
