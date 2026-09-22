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
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private KOMEAccessFixture f;
    private SimpleNetworkWrapper previous;
    private KOMEConquestTile tile;

    @Before public void setup() throws Exception {
        f = new KOMEAccessFixture();
        previous = KOMEPacketHandler.network; KOMEPacketHandler.network = f.network;
        List<String> ids = new ArrayList<String>(KOMEConquestTileDefaults.getKnownTileIds());
        Collections.sort(ids); assertTrue(ids.size() > 100);
        assertTrue(ids.contains("T100"));
        tile = new KOMEConquestTile("T100"); tile.claim("gondor", 0L);
        f.world.provider.dimensionId = KOMETileTestResources.dimension();
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
        KOMEBuildService.create(f.data, "Hall", tile.id, KOMETileTestResources.dimension(),
            KOMETileTestResources.x(), 64, KOMETileTestResources.z(), UUID.randomUUID(), "Builder",
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

    @Test public void sameFactionPacketPublishesGeneratedOwnerChoicesAndRoundTrips() throws Exception {
        tile.claim("dunedain", 0L);
        f.pledge(LOTRFaction.RANGER_NORTH);
        f.data.setDirty(false);
        String before = savedWorld();
        KOMEPacketConquestCaptureGui packet = tilePacket();
        assertEquals("dunedain", packet.viewerFaction);
        assertEquals("dunedain", packet.selectablePopulationOwners.get(0));
        assertEquals(KOMEBuildService.selectablePopulationOwners(f.data, "dunedain", tile.id),
            packet.selectablePopulationOwners);
        assertFalse(packet.selectablePopulationOwners.contains("mordor"));
        assertEquals(packet.selectablePopulationOwners.size(),
            new java.util.HashSet<String>(packet.selectablePopulationOwners).size());
        assertFalse(packet.canInspectWaypoint);
        assertFalse(packet.canTransfer);
        io.netty.buffer.ByteBuf bytes = Unpooled.buffer();
        try {
            packet.toBytes(bytes);
            KOMEPacketConquestCaptureGui decoded = new KOMEPacketConquestCaptureGui();
            decoded.fromBytes(bytes);
            assertEquals(packet.selectablePopulationOwners, decoded.selectablePopulationOwners);
            assertEquals("dunedain", decoded.selectablePopulationOwners.get(0));
            assertEquals(0, bytes.readableBytes());
        } finally { bytes.release(); }
        assertEquals(before, savedWorld());
        assertFalse(f.data.isDirty());
        assertTrue(f.data.progressions.isEmpty());
    }

    @Test public void unpledgedPublicViewerReceivesNoCreationOwners() throws Exception {
        f.pledge(null);
        String before = savedWorld();
        KOMEPacketConquestCaptureGui packet = tilePacket();
        assertEquals(tile.id, packet.tileId);
        assertEquals("", packet.viewerFaction);
        assertTrue(packet.selectablePopulationOwners.isEmpty());
        assertFalse(packet.canInspectWaypoint);
        assertFalse(packet.canTransfer);
        assertFalse(packet.canClaim);
        assertEquals(before, savedWorld());
        assertFalse(f.data.isDirty());
    }

    @Test public void foreignPublicViewerCannotObtainCreationOwnersEvenAsOperator() {
        tile.claim("rohan", 0L);
        f.data.setDirty(false);
        String before = savedWorld();
        for (boolean operator : new boolean[] {false, true}) {
            f.player.operator = operator;
            KOMEPacketConquestCaptureGui packet = tilePacket();
            assertEquals(tile.id, packet.tileId);
            assertEquals("gondor", packet.viewerFaction);
            assertTrue(packet.selectablePopulationOwners.isEmpty());
            assertFalse(packet.canTransfer);
        }
        assertEquals(before, savedWorld());
        assertFalse(f.data.isDirty());
        assertTrue(f.data.centralAudit.isEmpty());
    }

    @Test public void repeatedProductionPopulationReplacesChoicesWithoutDuplicatesOrStaleOwners() throws Exception {
        KOMEPacketConquestCaptureGui packet = tilePacket();
        List<String> expected = new ArrayList<String>(packet.selectablePopulationOwners);
        assertEquals("gondor", expected.get(0));
        java.lang.reflect.Method populate = KOMEPacketConquestOpenCapture.class.getDeclaredMethod(
            "populateBuildViews", KOMEPacketConquestCaptureGui.class, KOMEWorldData.class,
            net.minecraft.entity.player.EntityPlayerMP.class, KOMEConquestTile.class,
            String.class, String.class, UUID.class);
        populate.setAccessible(true);
        populate.invoke(null, packet, f.data, f.player, tile, "gondor", "gondor", f.player.id);
        assertEquals(expected, packet.selectablePopulationOwners);
        f.pledge(null);
        populate.invoke(null, packet, f.data, f.player, tile, "", "gondor", f.player.id);
        assertTrue(packet.selectablePopulationOwners.isEmpty());
        assertFalse(f.data.isDirty());
    }

    private KOMEPacketConquestCaptureGui tilePacket() {
        int before = f.network.messages.size();
        new KOMEPacketConquestOpenCapture.Handler().onMessage(
            new KOMEPacketConquestOpenCapture(tile.id), f.context);
        assertEquals(before + 1, f.network.messages.size());
        return (KOMEPacketConquestCaptureGui) f.network.messages.get(before);
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
        request.dimension = KOMETileTestResources.dimension();
        request.x = KOMETileTestResources.x(); request.z = KOMETileTestResources.z();
        assertTrue(KOMEBuildService.validateCoordinates(tile.id, request.dimension, request.x, request.y, request.z).allowed);
        assertEquals(request.dimension, f.world.provider.dimensionId);
        String before = savedWorld();
        new KOMEPacketBuildAction.Handler().onMessage(request, f.context);
        assertTrue(f.data.builds.isEmpty()); assertFalse(f.data.isDirty());
        assertTrue(f.player.messages.toString(), f.player.messages.toString().contains("has not granted your faction construction permission"));
        assertEquals(before, savedWorld()); assertTrue(f.data.centralAudit.isEmpty());
        assertTrue(f.network.messages.isEmpty());
        assertFalse(KOMEPopulationService.trySpendCenti(f.data, "gondor", 1L));
        assertFalse(f.data.isDirty()); assertTrue(f.data.factionPopulations.isEmpty());
    }

    @Test public void forgedReviewRejectsWithoutAuditDirtyOrResponseAndRealManagerSucceeds() {
        UUID builder = UUID.randomUUID();
        KOMEPlayerBuild build = KOMEBuildService.create(f.data, "Hall", tile.id, KOMETileTestResources.dimension(),
            KOMETileTestResources.x(), 64, KOMETileTestResources.z(),
            builder, "Builder", "gondor", "gondor", KOMEBuildType.NORMAL, 0L, 1L);
        // Existing saved locations do not become spatial preconditions for lifecycle actions.
        build.dimension = 0; build.x = Integer.MIN_VALUE; build.z = Integer.MAX_VALUE;
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
        assertEquals(0, build.dimension); assertEquals(Integer.MIN_VALUE, build.x, 0);
        assertEquals(Integer.MAX_VALUE, build.z, 0);
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
        assertEquals(4, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
    }

    @Test public void validNonOperatorCreationPassesQueuedPacketAndExactGeometry() {
        KOMEPacketBuildAction request = validCreation();
        request.centiHours = 125L; request.x += 0.5; request.z += 0.75;
        assertFalse(f.player.operator);
        new KOMEPacketHandler.ServerThreadHandler<KOMEPacketBuildAction>(new KOMEPacketBuildAction.Handler()) {}
            .onMessage(request, f.context);
        assertTrue(f.data.builds.isEmpty()); assertFalse(f.data.isDirty());
        assertTrue(f.network.messages.isEmpty());
        assertEquals(1, KOMEPacketHandler.runPendingServerTasks());
        assertEquals(f.player.messages.toString(), 1, f.data.builds.size());
        KOMEPlayerBuild build = f.data.builds.values().iterator().next();
        assertEquals(tile.id, build.tileId); assertEquals(request.dimension, build.dimension);
        assertEquals(request.x, build.x, 0); assertEquals(request.z, build.z, 0);
        assertEquals(f.player.id, build.managerUuid); assertEquals(125L, build.approvedCentiHours());
        assertTrue(f.data.isDirty()); assertFalse(f.network.messages.isEmpty());
    }

    @Test public void spatialPacketRejectionsLeaveRecordsCountersAuditAndResponsesUnchanged() {
        // Include an existing Build to detect unintended reconciliation or deletion.
        KOMEBuildService.create(f.data, "Existing", tile.id, KOMETileTestResources.dimension(),
            KOMETileTestResources.x(), 64, KOMETileTestResources.z(), f.player.id, "Builder",
            "gondor", "gondor", KOMEBuildType.NORMAL, 25L, 1L);
        KOMEPacketBuildAction gap = validCreation();
        gap.x = KOMETileTestResources.worldX(2291); gap.z = KOMETileTestResources.worldZ(58);
        rejectWithoutMutation(gap, "IN_BOUNDS_GAP");
        KOMEPacketBuildAction outside = validCreation();
        outside.x = KOMETileTestResources.worldX(0) - 1;
        rejectWithoutMutation(outside, "OUTSIDE_MASK");
        KOMEPacketBuildAction unsupported = validCreation(); unsupported.dimension++;
        f.world.provider.dimensionId = unsupported.dimension; // Reach resolver, not player-dimension rejection.
        rejectWithoutMutation(unsupported, "UNSUPPORTED_DIMENSION");
        f.world.provider.dimensionId = KOMETileTestResources.dimension();
        rejectWithoutMutation(unsupported, "player's current dimension");
        KOMEPacketBuildAction mismatch = validCreation();
        mismatch.x = KOMETileTestResources.worldX(2292); mismatch.z = KOMETileTestResources.worldZ(58);
        rejectWithoutMutation(mismatch, "confirmed conquest tile");
        KOMETileWorldResolver.INSTANCE.invalidate();
        rejectWithoutMutation(validCreation(), "INVALID_SNAPSHOT");
    }

    @Test public void buildPacketRetainsPublicTileGuardBeforeCreationOrRefresh() {
        for (String id : new String[] {"T045", "T999999", "T001"}) {
            // Retired and unknown records exist; known T001 deliberately has no world record.
            if (!"T001".equals(id)) f.data.conquestTiles.put(id, new KOMEConquestTile(id));
            KOMEPacketBuildAction request = validCreation(); request.tileId = id;
            rejectWithoutMutation(request, "Unknown or unavailable conquest tile");
        }
    }

    @Test public void publicTileInspectionNeitherCreatesRecordsNorRepairsOwnership() {
        assertEquals(KOMEConquestTileDefaults.getKnownTileIds(),
            new java.util.HashSet<String>(KOMETileTestResources.real().idsByColor().values()));
        tile.currentRulingFaction = " GONDOR "; tile.ownerFaction = "rohan";
        assertNull(f.data.getPublicConquestTile("T001")); // Known geometry is not a creating accessor.
        assertSame(tile, f.data.getPublicConquestTile(" t100 "));
        assertNull(f.data.getPublicConquestTile("T045"));
        assertNull(f.data.getPublicConquestTile("T999999"));
        new kome.common.command.KOMECommandConquest().processCommand(f.player, new String[] {"get", tile.id});
        assertEquals(" GONDOR ", tile.currentRulingFaction); assertEquals("rohan", tile.ownerFaction);
        assertEquals(1, f.data.conquestTiles.size()); assertTrue(f.data.progressions.isEmpty());
        assertTrue(f.data.centralAudit.isEmpty()); assertTrue(f.network.messages.isEmpty());
        assertFalse(f.data.isDirty());
    }

    private KOMEPacketBuildAction validCreation() {
        return new KOMEPacketBuildAction("create", tile.id, "", "", "Hall", "gondor", "NORMAL",
            0L, KOMETileTestResources.dimension(), KOMETileTestResources.x(), 64D, KOMETileTestResources.z());
    }

    private void rejectWithoutMutation(KOMEPacketBuildAction request, String reason) {
        String before = savedWorld(); f.data.setDirty(false);
        int audit = f.data.centralAudit.size();
        f.player.messages.clear(); f.network.messages.clear();
        new KOMEPacketHandler.ServerThreadHandler<KOMEPacketBuildAction>(new KOMEPacketBuildAction.Handler()) {}
            .onMessage(request, f.context);
        assertEquals(before, savedWorld()); assertFalse(f.data.isDirty());
        assertTrue(f.network.messages.isEmpty());
        assertEquals(1, KOMEPacketHandler.runPendingServerTasks());
        assertTrue(f.player.messages.toString(), f.player.messages.toString().contains(reason));
        assertEquals(before, savedWorld()); assertEquals(audit, f.data.centralAudit.size());
        assertFalse(f.data.isDirty()); assertTrue(f.network.messages.isEmpty());
    }

    private String savedWorld() {
        // Read-only comparison needs schema-4 capital geometry; rejected requests do not get it.
        boolean unavailable = !KOMETileWorldResolver.INSTANCE.snapshot().isPresent();
        if (unavailable) assertTrue(KOMETileWorldResolver.INSTANCE.reloadBundled());
        try {
            net.minecraft.nbt.NBTTagCompound saved = new net.minecraft.nbt.NBTTagCompound();
            f.data.writeToNBT(saved); return saved.toString();
        } finally { if (unavailable) KOMETileWorldResolver.INSTANCE.invalidate(); }
    }

    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/" + path)), StandardCharsets.UTF_8);
    }
    @Test public void unpledgedAndForeignCreationRejectThroughQueuedHandlerAfterValidGeometry() throws Exception {
        KOMEPacketBuildAction request = validCreation();
        assertTrue(KOMEBuildService.validateCoordinates(request.tileId, request.dimension, request.x, request.y, request.z).allowed);
        f.pledge(null);
        rejectWithoutMutation(request, "must be pledged");
        f.pledge(LOTRFaction.ROHAN);
        request.populationFaction = "rohan";
        rejectWithoutMutation(request, "has not granted");
    }

    @Test public void invalidNumericBuildInputsRejectAtomicallyThroughQueuedHandler() {
        for (double input : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            KOMEPacketBuildAction request = validCreation(); request.x = input;
            rejectWithoutMutation(request, "finite");
            request = validCreation(); request.z = input;
            rejectWithoutMutation(request, "finite");
            request = validCreation(); request.y = input;
            rejectWithoutMutation(request, "finite");
        }
        KOMEPacketBuildAction request = validCreation(); request.x = 2147483648D;
        rejectWithoutMutation(request, "INVALID_COORDINATE");
    }
}
