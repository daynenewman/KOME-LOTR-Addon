package kome.common.data;

import java.util.Arrays;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMECampaignTileConfinementService.*;
import static kome.common.data.KOMEServerTileAwareness.Availability.*;

public class KOMECampaignTileConfinementServiceTest {
    private KOMETileWorldResolver resolver;
    private KOMECampaignTileConfinementService service;
    private KOMEWorldData data;
    private KOMEHiredUnitRecord record;
    private KOMEArmyCompany company;
    private Unit unit;
    private long tick;

    @Before public void setup() throws Exception {
        resolver = new KOMETileWorldResolver(); resolver.publish(KOMEServerTileAwarenessTest.snapshot(1, 2, 0));
        service = new KOMECampaignTileConfinementService(resolver);
        data = new KOMEWorldData("confinement");
        data.conquestTiles.put("T001", new KOMEConquestTile("T001"));
        data.conquestTiles.put("T002", new KOMEConquestTile("T002"));
        record = record(); company = new KOMEArmyCompany();
        company.id = "C1"; company.owner = record.owner; company.currentTile = "T001";
        company.faction = company.nativeFaction = "gondor";
        company.sourceTileId = "T900"; company.units.add(record.entity);
        record.companyId = company.id; data.armyCompanies.put(company.id, company);
        data.lastKnownPlayerFactions.put(record.owner, "gondor");
        data.recalculateCampaignCompanyComposition(company);
        unit = new Unit();
    }

    private KOMEHiredUnitRecord record() {
        KOMEHiredUnitRecord r = new KOMEHiredUnitRecord(); r.entity = UUID.randomUUID(); r.owner = UUID.randomUUID();
        r.currentTile = "T001"; r.sourceTileId = "T900"; r.lotrCompanyValue = "Native Alpha";
        r.unitFaction = r.populationOwningFaction = "gondor";
        KOMEHiredUnitClassification.assignForCampaignWorkflow(r); data.hiredUnits.put(r.entity, r); return r;
    }
    private Result check() { return check(AVAILABLE, resolver.resolveWorldPosition(173, unit.p.x, unit.p.z)); }
    private Result check(KOMEServerTileAwareness.Availability a, KOMETileResolution p) {
        return service.evaluate(data, record, unit, a, p, ++tick);
    }
    private Result cross() { check(); unit.p = pos(64); return check(); }
    private static Position pos(double x) { return new Position(173, x, 64, -1); }

    @Test public void ordinaryFollowerCrossesWithoutConfinementEvenWithLegacyCompanyAndSource() {
        record.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY);
        unit.p = pos(64); assertEquals(Code.NOT_CAMPAIGN, check().code);
        assertEquals(0, unit.returns); assertEquals(0, service.inspectionCount()); assertEquals(64, unit.p.x, 0);
        assertEquals(0, unit.halts);
    }
    @Test public void fullTileMovementDoesNotImmobilizeOrLeash() {
        for (double x : new double[] {-127.5, -64, -1, -0.01}) {
            unit.p = pos(x); assertEquals(Code.IN_TILE, check().code);
        }
        assertEquals(0, unit.returns); assertEquals(0, unit.placements);
        assertEquals(0, unit.halts);
    }
    @Test public void adjacentTileCrossingIgnoresBorderSafePositionAndStationsThenHalts() {
        unit.p = pos(-0.01); assertEquals(Code.IN_TILE, check().code);
        unit.p = pos(64); assertEquals(Code.RETURNED_TO_PLACEMENT, check().code);
        assertStationedAndHalted(); assertEquals(1, unit.returns); assertEquals(1, unit.placements);
    }
    @Test public void correctionPreservesAllStrategicCompanyMembershipRouteAndEconomics() {
        KOMEArmyMovementOrder order = route();
        NBTTagCompound unitBefore = record.writeToNBT(), companyBefore = company.writeToNBT(), orderBefore = order.writeToNBT();
        long population = KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");
        NBTTagCompound seasonBefore = new NBTTagCompound(); data.warSeason.writeToNBT(seasonBefore);
        int histories = data.movementHistory.size();
        assertTrue(cross().corrected());
        assertEquals(unitBefore, record.writeToNBT());
        assertEquals(companyBefore, company.writeToNBT());
        assertEquals(orderBefore, order.writeToNBT());
        assertEquals(population, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        NBTTagCompound seasonAfter = new NBTTagCompound(); data.warSeason.writeToNBT(seasonAfter);
        assertEquals(seasonBefore, seasonAfter);
        assertEquals(histories, data.movementHistory.size());
        assertEquals("", data.conquestTiles.get("T002").ownerFaction);
        assertStationedAndHalted();
    }
    @Test public void positiveAvailableKom60ContradictionIsCorrected() {
        unit.p = pos(64); assertEquals(Code.RETURNED_TO_PLACEMENT, check().code);
        assertStationedAndHalted();
    }
    @Test public void everyUnknownAvailabilityIgnoresEvenAnOldResolvedContradiction() {
        unit.p = pos(64); KOMETileResolution old = resolver.resolveWorldPosition(173, 64, -1);
        for (KOMEServerTileAwareness.Availability a : KOMEServerTileAwareness.Availability.values())
            if (a != AVAILABLE) assertEquals(Code.UNKNOWN, check(a, old).code);
        assertEquals(0, unit.returns); assertEquals(0, unit.placements);
        assertEquals(0, unit.halts);
    }
    @Test public void everyUnresolvedGeometryStateIsUnknown() {
        for (KOMETileResolution.Status status : KOMETileResolution.Status.values()) {
            if (status == KOMETileResolution.Status.RESOLVED) continue;
            assertEquals(Code.UNKNOWN, check(AVAILABLE,
                KOMETileResolution.unavailable(status, 173, 64, -1, "test")).code);
        }
        assertEquals(0, unit.returns);
    }
    @Test public void oldSafeTerrainDoesNotChooseCorrectionDestination() {
        check(); unit.unsafeOldPosition = true; unit.p = pos(64);
        assertEquals(Code.RETURNED_TO_PLACEMENT, check().code); assertEquals(-32, unit.p.x, 0);
        assertStationedAndHalted();
    }
    @Test public void longWithinTileStayDoesNotHaltAndLaterCrossingUsesStation() {
        check(); tick += 5000; assertEquals(Code.IN_TILE, check().code); assertEquals(0, unit.halts);
        unit.p = pos(64);
        assertEquals(Code.RETURNED_TO_PLACEMENT, check().code);
        assertStationedAndHalted();
    }
    @Test public void stationIsValidatedAgainstCurrentGeometry() throws Exception {
        check(); resolver.publish(KOMEServerTileAwarenessTest.snapshot(2, 1, 0));
        unit.p = pos(-64); unit.station = pos(32);
        assertEquals(Code.RETURNED_TO_PLACEMENT, check().code); assertEquals(32, unit.p.x, 0);
        assertEquals(1, unit.halts);
    }
    @Test public void failedPlacementIsLatchedWithoutDeletionOrRepeatedSearchTeleportOrAudit() {
        unit.p = pos(64); unit.station = null;
        assertEquals(Code.NO_SAFE_RETURN, check().code);
        for (int i = 0; i < 500; i++) assertEquals(Code.RETRY_BLOCKED, check().code);
        assertEquals(1, unit.placements); assertEquals(0, unit.returns); assertEquals(1, data.centralAudit.size());
        assertSame(record, data.hiredUnits.get(record.entity)); assertEquals("T001", record.currentTile);
        assertEquals(0, unit.halts);
    }
    @Test public void outsideTileFallbackIsRejectedBeforeTeleport() {
        unit.p = pos(64); unit.station = pos(32);
        assertEquals(Code.NO_SAFE_RETURN, check().code); assertEquals(0, unit.returns);
        assertEquals(0, unit.halts);
    }
    @Test public void failedReturnVerificationBlocksLoop() {
        unit.p = pos(64); unit.ignoreReturn = true;
        assertEquals(Code.RETURN_NOT_VERIFIED, check().code);
        assertEquals(Code.RETRY_BLOCKED, check().code); assertEquals(1, unit.returns);
        assertEquals(0, unit.halts);
    }
    @Test public void exceptionalPlacementFailsClosedWithoutInterruptingOtherUnitSampling() {
        unit.p = pos(64); unit.throwPlacement = true;
        assertEquals(Code.NO_SAFE_RETURN, check().code);
        assertEquals(Code.RETRY_BLOCKED, check().code); assertEquals(0, unit.returns);
        assertEquals(0, unit.halts);
    }
    @Test public void followerPathfindingCrossingStationsAndHalts() {
        assertTrue(cross().corrected()); assertStationedAndHalted();
    }
    @Test public void knockbackAndCollisionCrossingIsCorrected() {
        check(); unit.p = pos(0.01); assertTrue(check().corrected());
        assertStationedAndHalted();
    }
    @Test public void crossTileNativeSummonAndOtherTeleportIsCorrected() {
        check(); unit.p = new Position(173, 120, 80, -1); assertTrue(check().corrected());
        assertStationedAndHalted();
    }
    @Test public void sameTileTeleportRemainsLegal() {
        check(); unit.p = new Position(173, -120, 80, -1);
        assertEquals(Code.IN_TILE, check().code); assertEquals(0, unit.returns);
        assertEquals(0, unit.halts);
    }
    @Test public void waitingNextStepAndSpawningLabelsDoNotAuthorizePhysicalCrossing() {
        KOMEArmyMovementOrder order = route(); NBTTagCompound before = order.writeToNBT();
        assertTrue(cross().corrected()); assertStationedAndHalted(); assertEquals(before, order.writeToNBT());
        order.status = KOMEArmyMovementOrder.SPAWNING;
        unit.p = pos(64); assertTrue(check().corrected());
        assertEquals("T001", record.currentTile); assertEquals("T001", company.currentTile);
        assertEquals(2, unit.halts);
    }
    @Test public void committedLegalHopAcceptsNewTileAndConfinesThereWithoutAnExemption() {
        check(); route();
        // The synchronous arrival commits both strategic tiles before the END sampler is eligible.
        record.currentTile = company.currentTile = "T002";
        data.armyMovements.get("M1").currentTile = "T002";
        unit.p = pos(64); assertEquals(Code.IN_TILE, check().code); assertEquals(0, unit.returns);
        assertEquals(0, unit.halts);
        unit.station = pos(32); unit.p = pos(-64); assertEquals(Code.RETURNED_TO_PLACEMENT, check().code);
        assertEquals(32, unit.p.x, 0); assertEquals("T002", record.currentTile); assertEquals(1, unit.halts);
    }
    @Test public void unassignedCampaignRecordIsStillConfined() {
        record.companyId = ""; company.units.clear(); assertTrue(cross().corrected());
    }
    @Test public void strategicDisagreementDoesNotPickPhysicalCompanyOrRecordTile() {
        company.currentTile = "T002"; unit.p = pos(64);
        assertEquals(Code.STRATEGIC_CONTRADICTION, check().code);
        assertEquals(0, unit.returns); assertEquals(0, unit.placements);
        assertEquals(0, unit.halts);
        assertEquals("T001", record.currentTile); assertEquals("T002", company.currentTile);
        for (int i = 0; i < 50; i++) check(); assertEquals(1, data.centralAudit.size());
    }
    @Test public void missingStrategicRecordTileOrCompanyFailsClosed() {
        record.currentTile = ""; assertEquals(Code.STRATEGIC_CONTRADICTION, check().code);
        record.currentTile = "T001"; data.armyCompanies.clear();
        assertEquals(Code.STRATEGIC_CONTRADICTION, check().code); assertEquals(0, unit.returns);
    }
    @Test public void repeatedPressureHasOneCorrectionPerSampleAndBoundedCoalescedAudit() {
        check();
        for (int i = 0; i < 400; i++) { unit.p = pos(64); assertTrue(check().corrected()); }
        assertEquals(400, unit.returns); assertEquals(400, service.correctionAttemptCount());
        assertEquals(400, unit.halts);
        assertEquals(4, data.centralAudit.size());
        assertTrue(data.centralAudit.get(1).details.contains("coalesced=99"));
    }
    @Test public void transientStateResetStillStationsWithoutAnyStrategicChange() {
        check(); NBTTagCompound before = record.writeToNBT();
        service.reset(); unit.p = pos(64); assertEquals(Code.RETURNED_TO_PLACEMENT, check().code);
        assertEquals(before, record.writeToNBT());
        assertStationedAndHalted();
    }
    @Test public void freshCorrectedEvidenceMakesPhase1Coherent() {
        KOMECompanyCoherenceService coherence = new KOMECompanyCoherenceService(id ->
            KOMECompanyCoherenceService.PhysicalEvidence.resolved(resolver.resolveWorldPosition(173, unit.p.x, unit.p.z).tileId));
        unit.p = pos(64); assertEquals(KOMECompanyCoherenceService.Status.INCOHERENT, coherence.assess(data, company).status);
        check(); assertEquals(KOMECompanyCoherenceService.Status.COHERENT, coherence.assess(data, company).status);
    }
    @Test public void splitChildrenAndMergedDetachmentsRetainConfinementAndSaneSourceSummary() {
        KOMEHiredUnitRecord second = record(), third = record(); second.owner = third.owner = record.owner;
        second.sourceTileId = "T901"; third.sourceTileId = "T902";
        for (KOMEHiredUnitRecord r : Arrays.asList(second, third)) { r.companyId = company.id; company.units.add(r.entity); }
        data.recalculateCampaignCompanyComposition(company); assertEquals("", company.sourceTileId);
        KOMECompanyReorganizationService reorganize = new KOMECompanyReorganizationService(
            new KOMECompanyCoherenceService(id -> KOMECompanyCoherenceService.PhysicalEvidence.resolved("T001")),
            (operation, primary, secondary) -> { });
        KOMECompanyReorganizationService.Result split = reorganize.split(data, record.owner, company.id,
            Arrays.asList(second.entity, third.entity)); assertTrue(split.reason, split.success);
        assertEquals("T900", company.sourceTileId); assertEquals("", split.secondaryCompany.sourceTileId);
        for (KOMEHiredUnitRecord r : Arrays.asList(second, third)) {
            unit.p = pos(64);
            assertTrue(service.evaluate(data, r, unit, AVAILABLE, resolver.resolveWorldPosition(173, 64, -1), ++tick).corrected());
            assertEquals("T001", r.currentTile);
        }
        KOMECompanyReorganizationService.Result merge = reorganize.merge(data, record.owner, company.id, split.secondaryCompanyId);
        assertTrue(merge.reason, merge.success); assertEquals("", company.sourceTileId);
        assertTrue(cross().corrected()); assertEquals("T901", second.sourceTileId); assertEquals("T902", third.sourceTileId);
    }
    @Test public void missingSourceAndNativeSquadronNeverSupplyConfinementAuthority() {
        record.currentTile = ""; record.sourceTileId = "T002"; record.lotrCompanyValue = "T002";
        unit.p = pos(64); assertEquals(Code.STRATEGIC_CONTRADICTION, check().code); assertEquals(0, unit.returns);
    }
    @Test public void repairedStrategicMetadataRecoversFromPlacementFailureWithoutTimerExemption() {
        unit.p = pos(64); unit.station = null; assertEquals(Code.NO_SAFE_RETURN, check().code);
        record.currentTile = company.currentTile = "T002"; assertEquals(Code.IN_TILE, check().code);
        unit.station = pos(32); unit.p = pos(-64); assertTrue(check().corrected());
    }

    private void assertStationedAndHalted() {
        assertEquals(-32, unit.p.x, 0); assertEquals(1, unit.halts);
        assertTrue(unit.halted); assertTrue(unit.haltedAfterVerifiedReturn);
    }

    private KOMEArmyMovementOrder route() {
        KOMEArmyMovementOrder o = KOMEArmyMovementOrder.newRoute(2); o.id = "M1"; o.companyId = company.id;
        o.owner = record.owner; o.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        o.currentTile = "T001"; o.nextTile = o.destinationTile = "T002";
        o.routeTiles.addAll(Arrays.asList("T001", "T002")); o.units.add(record.entity);
        company.movementOrderId = record.movementOrderId = o.id; company.status = KOMEArmyCompany.MOVING;
        data.armyMovements.put(o.id, o); return o;
    }
    private static class Unit implements PhysicalUnit {
        Position p = pos(-64), station = pos(-32);
        int returns, placements, halts;
        boolean unsafeOldPosition, ignoreReturn, throwPlacement, halted, haltedAfterVerifiedReturn;
        public Position position() { return p; }
        public boolean safe(Position position) { return !unsafeOldPosition || position.x != -64; }
        public Position placement(String tile) {
            placements++; if (throwPlacement) throw new IllegalStateException("test adapter failure"); return station;
        }
        public void returnTo(Position position) { returns++; if (!ignoreReturn) p = position; }
        public void halt() { halts++; halted = true; haltedAfterVerifiedReturn = returns > 0 && p == station; }
    }
}
