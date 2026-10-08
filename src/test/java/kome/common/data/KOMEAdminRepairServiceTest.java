package kome.common.data;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;
import lotr.common.fac.LOTRFaction;
import lotr.common.fac.LOTRFactionRelations;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEAdminRepairServiceTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private static final String ACTOR = "10000000-0000-0000-0000-000000000001";
    private final KOMEAdminRepairService service = new KOMEAdminRepairService();

    @After public void resetRelation() { relation(LOTRFactionRelations.Relation.NEUTRAL); }
    private void relation(LOTRFactionRelations.Relation r) {
        LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR, LOTRFaction.ROHAN, r);
    }
    private KOMEWorldData world() {
        KOMEWorldData data = new KOMEWorldData("disposable-kom40-fixture");
        data.initializeIntegratedWorld();
        return data;
    }
    private KOMEConquestTile missingAlias(KOMEWorldData data) {
        KOMEConquestTile t = data.conquestTiles.get("T100");
        t.setCurrentRulingFaction("gondor"); t.ownerFaction = "";
        return t;
    }
    private KOMEAdminRepairService.Result preview(KOMEWorldData data, String domain, String subject, String other) {
        return service.preview(data, true, ACTOR, domain, subject, other, null, null, 1000);
    }
    private KOMEAdminRepairService.Result apply(KOMEWorldData data, String token) {
        return service.apply(data, true, ACTOR, token, null, null, 1001);
    }
    private NBTTagCompound save(KOMEWorldData data) {
        NBTTagCompound n = new NBTTagCompound(); data.writeToNBT(n); return n;
    }

    @Test public void previewIsReadOnlyAndAliasRepairPreservesClaimAndValidStateThenPersists() {
        KOMEWorldData data = world(); KOMEConquestTile t = missingAlias(data);
        t.claimedByUuid = UUID.randomUUID(); t.claimedByName = "Claimant"; t.claimedAtMillis = 42;
        t.proposeTransfer("gondor", "rohan");
        KOMEPopulationService.grantCenti(data, "gondor", 12345);
        NBTTagCompound before = t.projectToNBT();
        KOMEAdminRepairService.Result plan = preview(data, "ownership", "T100", "");
        assertTrue(plan.reason, plan.allowed); assertEquals("", t.ownerFaction);
        assertEquals(before, t.projectToNBT());
        assertTrue(apply(data, plan.token).allowed);
        assertEquals("gondor", t.ownerFaction); assertEquals(42, t.claimedAtMillis);
        assertEquals(before, t.projectToNBT());
        assertEquals("Claimant", t.claimedByName); assertEquals("rohan", t.pendingTransferToFaction);
        assertEquals(12345, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        KOMEWorldData loaded = new KOMEWorldData("restart"); loaded.readFromNBT(save(data));
        assertFalse(loaded.getLoadFailureReason(), loaded.isWriteBlocked());
        assertEquals("gondor", loaded.conquestTiles.get("T100").ownerFaction);
        assertTrue(KOMEAuditService.entries(loaded).stream().anyMatch(e -> e.action.equals("REPAIR_APPLY")));
        assertFalse(apply(data, plan.token).allowed);
        assertFalse(apply(loaded, plan.token).allowed);
    }

    @Test public void permissionIsCheckedAtPreviewAndApplyWithoutChangingOwnership() {
        KOMEWorldData data = world(); KOMEConquestTile t = missingAlias(data);
        assertFalse(service.preview(data, false, ACTOR, "ownership", "T100", "", null, null, 1000).allowed);
        String token = preview(data, "ownership", "T100", "").token;
        assertFalse(service.apply(data, false, ACTOR, token, null, null, 1001).allowed);
        assertEquals("", t.ownerFaction);
        assertTrue(apply(data, token).allowed);
        assertTrue(KOMEAuditService.entries(data).stream().anyMatch(e -> e.action.equals("REPAIR_DENIED")));
    }

    @Test public void plansAreActorAndWorldBoundAndForeignAttemptsDoNotConsumeThem() {
        KOMEWorldData data = world(); missingAlias(data);
        String token = preview(data, "ownership", "T100", "").token;
        assertFalse(service.apply(data, true, "console", token, null, null, 1001).allowed);
        KOMEWorldData other = world(); missingAlias(other);
        assertFalse(apply(other, token).allowed);
        assertTrue(apply(data, token).allowed);
    }

    @Test public void staleOwnerAndClaimMetadataAreRejectedAndPlanConsumed() {
        for (boolean metadata : new boolean[] {true, false}) {
            KOMEWorldData data = world(); KOMEConquestTile t = missingAlias(data);
            String token = preview(data, "ownership", "T100", "").token;
            if (metadata) t.claimedAtMillis++; else t.setCurrentRulingFaction("rohan");
            assertFalse(apply(data, token).allowed);
            assertEquals(metadata ? "" : "rohan", t.ownerFaction);
            assertFalse(apply(data, token).allowed);
        }
    }

    @Test public void expiresAtBoundaryAndCapacityIsBoundedAndRecovers() {
        KOMEWorldData data = world(); missingAlias(data);
        String token = preview(data, "ownership", "T100", "").token;
        assertFalse(service.apply(data, true, ACTOR, token, null, null, 121000).allowed);
        for (int i = 0; i < KOMEAdminRepairService.MAX_PLANS; i++)
            assertTrue(preview(data, "ownership", "T100", "").allowed);
        assertFalse(preview(data, "ownership", "T100", "").allowed);
        assertTrue(service.preview(data, true, ACTOR, "ownership", "T100", "", null, null, 121000).allowed);
    }

    @Test public void validConflictingLegacyOnlyAndUnknownOwnershipArePreserved() {
        KOMEWorldData data = world(); KOMEConquestTile t = data.conquestTiles.get("T100");
        for (String[] state : new String[][] {{"gondor", "gondor"}, {"gondor", "rohan"}, {"", "gondor"}, {"unknown", ""}, {"", ""}}) {
            t.currentRulingFaction = state[0]; t.ownerFaction = state[1];
            assertFalse(preview(data, "ownership", "T100", "").allowed);
            assertEquals(state[0], t.currentRulingFaction); assertEquals(state[1], t.ownerFaction);
        }
        assertFalse(preview(data, "ownership", "T9999", "").allowed);
    }

    @Test public void obsoleteDiplomacyConsentIsRemovedWithoutChangingNativeRelationAndPersists() {
        KOMEWorldData data = world(); relation(LOTRFactionRelations.Relation.ALLY);
        KOMEDiplomacyRecord r = pending(data);
        String token = preview(data, "diplomacy", "gondor", "rohan").token;
        assertNotNull(r.pendingTarget); assertTrue(apply(data, token).allowed);
        assertNull(r.pendingTarget);
        assertEquals(KOMEDiplomacyRelation.ALLIES, KOMEDiplomacyService.getRelation(data, "gondor", "rohan"));
        KOMEWorldData loaded = new KOMEWorldData("restart"); loaded.readFromNBT(save(data));
        assertFalse(loaded.isWriteBlocked());
        assertNull(loaded.canonicalDiplomacyRecords.get(r.key()).pendingTarget);
    }
    private KOMEDiplomacyRecord pending(KOMEWorldData data) {
        KOMEDiplomacyRecord r = new KOMEDiplomacyRecord("gondor", "rohan");
        r.pendingTarget = KOMEDiplomacyRelation.FRIENDS; r.requestingFaction = "gondor";
        r.receivingFaction = "rohan"; r.requesterIdentity = ACTOR;
        data.canonicalDiplomacyRecords.put(r.key(), r); return r;
    }

    @Test public void diplomacyPlanRechecksNativeRelationAndWorkflowVersion() {
        for (boolean nativeChange : new boolean[] {true, false}) {
            KOMEWorldData data = world(); relation(LOTRFactionRelations.Relation.ALLY);
            KOMEDiplomacyRecord r = pending(data);
            String token = preview(data, "diplomacy", "gondor", "rohan").token;
            if (nativeChange) relation(LOTRFactionRelations.Relation.NEUTRAL); else r.requestedAt++;
            assertFalse(apply(data, token).allowed); assertNotNull(r.pendingTarget);
        }
    }

    @Test public void validPendingConsentAndMissingWorkflowArePreserved() {
        KOMEWorldData data = world(); relation(LOTRFactionRelations.Relation.NEUTRAL);
        assertFalse(preview(data, "diplomacy", "gondor", "rohan").allowed);
        KOMEDiplomacyRecord r = pending(data);
        assertFalse(preview(data, "diplomacy", "gondor", "rohan").allowed); assertNotNull(r.pendingTarget);
    }

    @Test public void rulerRepairRequiresSameOnlineUuidAndRechecksOnlineName() {
        KOMEWorldData data = world(); UUID ruler = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", ruler, "Old");
        assertFalse(service.preview(data, true, ACTOR, "ruler", "gondor", "", UUID.randomUUID(), "New", 1000).allowed);
        KOMEAdminRepairService.Result plan = service.preview(data, true, ACTOR, "ruler", "gondor", "", ruler, "New", 1000);
        assertTrue(plan.reason, plan.allowed);
        assertFalse(service.apply(data, true, ACTOR, plan.token, ruler, "ChangedAgain", 1001).allowed);
        assertEquals("Old", KOMERulerService.getRulerName(data, "gondor"));
        plan = service.preview(data, true, ACTOR, "ruler", "gondor", "", ruler, "New", 1000);
        assertTrue(service.apply(data, true, ACTOR, plan.token, ruler, "New", 1001).allowed);
        assertEquals(ruler, KOMERulerService.getRuler(data, "gondor"));
        assertEquals("New", KOMERulerService.getRulerName(data, "gondor"));
        assertFalse(KOMERulerService.repair(data, "gondor", null, "Invented").changed);
        assertFalse(service.preview(data, true, ACTOR, "ruler", "rohan", "", ruler, "New", 1000).allowed);
    }

    private KOMEPublicWaypoint brokenPublicLink(KOMEWorldData data) throws Exception {
        KOMEPublicWaypoint r = data.publicWaypoints.approve(data, "Fixture", KOMETileTestResources.dimension(),
            KOMETileTestResources.x(), 72, KOMETileTestResources.z(), 2, KOMEPublicWaypoint.Source.PUBLIC, "", ACTOR, 100, null);
        KOMEPublicWaypoint broken = r.changed("T442", r.name, r.dimension, r.x, r.y, r.z, r.level, 101);
        Field f = KOMEPublicWaypointRegistry.class.getDeclaredField("records"); f.setAccessible(true);
        @SuppressWarnings("unchecked") Map<UUID, KOMEPublicWaypoint> records = (Map<UUID, KOMEPublicWaypoint>) f.get(data.publicWaypoints);
        records.put(r.id, broken); return broken;
    }

    @Test public void publicLinkRepairPreservesGeometryIdentityLevelAndPersists() throws Exception {
        KOMEWorldData data = world(); KOMEPublicWaypoint r = brokenPublicLink(data);
        String token = preview(data, "waypoint", r.id.toString(), "").token;
        assertTrue(apply(data, token).allowed);
        KOMEPublicWaypoint fixed = data.publicWaypoints.get(r.id);
        assertEquals("T100", fixed.tileId); assertEquals(r.wireId, fixed.wireId);
        assertEquals(r.x, fixed.x); assertEquals(r.y, fixed.y); assertEquals(r.z, fixed.z);
        assertEquals(r.level, fixed.level); assertEquals(r.approvedBy, fixed.approvedBy);
        KOMEWorldData loaded = new KOMEWorldData("restart"); loaded.readFromNBT(save(data));
        assertFalse(loaded.isWriteBlocked()); assertEquals("T100", loaded.publicWaypoints.get(r.id).tileId);
        assertEquals("REPAIR_LINK", loaded.publicWaypoints.history().get(1).getString("Action"));
        assertFalse(preview(data, "waypoint", r.id.toString(), "").allowed);
    }

    @Test public void waypointRevisionAndDestinationOccupancyInvalidatePlans() throws Exception {
        KOMEWorldData data = world(); KOMEPublicWaypoint r = brokenPublicLink(data);
        String token = preview(data, "waypoint", r.id.toString(), "").token;
        data.publicWaypoints.approve(data, "Occupant", r.dimension, r.x, r.y, r.z, 0,
            KOMEPublicWaypoint.Source.PUBLIC, "", ACTOR, 102, null);
        assertFalse(apply(data, token).allowed); assertEquals("T442", data.publicWaypoints.get(r.id).tileId);
    }

    @Test public void derivedPopulationAndCapitalHaveNoInventedRepair() {
        KOMEWorldData data = world();
        assertFalse(preview(data, "population", "gondor", "").allowed);
        assertFalse(preview(data, "capital", "gondor", "").allowed);
        assertFalse(preview(KOMEClientData.INSTANCE, "ownership", "T100", "").allowed);
    }

    @Test public void writeBlockedWorldRejectsPlansAndApplyWithoutChangingAuditOrState() throws Exception {
        KOMEWorldData data = world(); KOMEConquestTile t = missingAlias(data);
        String token = preview(data, "ownership", "T100", "").token;
        int audits = KOMEAuditService.entries(data).size();
        Field blocked = KOMEWorldData.class.getDeclaredField("writeBlocked"); blocked.setAccessible(true);
        blocked.setBoolean(data, true);
        assertFalse(preview(data, "ownership", "T100", "").allowed);
        assertFalse(apply(data, token).allowed);
        assertEquals("", t.ownerFaction); assertEquals(audits, KOMEAuditService.entries(data).size());
    }

    @Test public void changedRulerOfficeInvalidatesPlanWithoutRestoringPreviousRuler() {
        KOMEWorldData data = world(); UUID ruler = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", ruler, "Old");
        String token = service.preview(data, true, ACTOR, "ruler", "gondor", "", ruler, "New", 1000).token;
        UUID replacement = UUID.randomUUID(); KOMERulerService.assignRuler(data, "gondor", replacement, "Replacement");
        assertFalse(service.apply(data, true, ACTOR, token, ruler, "New", 1001).allowed);
        assertEquals(replacement, KOMERulerService.getRuler(data, "gondor"));
    }

    @Test public void corruptLongCachedNameCannotHidePreviewTokenOrAuditAfterValue() {
        KOMEWorldData data = world(); UUID ruler = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", ruler, new String(new char[2000]).replace('\0', 'x'));
        KOMEAdminRepairService.Result p = service.preview(data, true, ACTOR, "ruler", "gondor", "", ruler, "Fixed", 1000);
        assertTrue(p.allowed); assertTrue(p.reason.contains(p.token));
        assertTrue(service.apply(data, true, ACTOR, p.token, ruler, "Fixed", 1001).allowed);
        KOMEAuditEntry last = KOMEAuditService.entries(data).get(KOMEAuditService.entries(data).size() - 1);
        assertTrue(last.details.contains("after=")); assertTrue(last.details.contains("name=Fixed"));
    }

    @Test public void unavailableGeometryRejectsApplyAndPreservesWaypoint() throws Exception {
        KOMEWorldData data = world(); KOMEPublicWaypoint r = brokenPublicLink(data);
        String token = preview(data, "waypoint", r.id.toString(), "").token;
        KOMETileWorldResolver.INSTANCE.invalidate();
        try {
            assertFalse(apply(data, token).allowed);
            assertEquals(r, data.publicWaypoints.get(r.id));
        } finally { KOMETileWorldResolver.INSTANCE.publish(KOMETileTestResources.real()); }
    }

    @Test public void quarantineReasonsAreInspectableWithoutRestoringOrDiscardingEvidence() throws Exception {
        KOMEWorldData data = world(); KOMEPublicWaypoint r = brokenPublicLink(data);
        NBTTagCompound registry = data.publicWaypoints.writeToNBT();
        data.publicWaypoints.replaceFrom(KOMEPublicWaypointRegistry.read(data, registry));
        assertNull(data.publicWaypoints.get(r.id));
        assertEquals(1, data.publicWaypoints.quarantine().size());
        NBTTagCompound before = data.publicWaypoints.writeToNBT();
        assertTrue(KOMEAdminDiagnostics.inspect(data, "waypoint", r.id.toString(), "", null)
            .toString().contains("Position does not belong"));
        assertFalse(preview(data, "waypoint", r.id.toString(), "").allowed);
        assertEquals(before, data.publicWaypoints.writeToNBT());
    }
}
