package kome.common.data;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import kome.common.network.KOMEPacketCompanyMoveConfirmGui;
import kome.common.network.KOMECompanyGuiEntry;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEMovementRoutePreviewTest {
    private static final long NOW = Instant.parse("2026-01-10T12:00:00Z").toEpochMilli();

    @Test public void allowanceIsConsumedBeforeFutureBoundariesForFootAndMountedCompanies() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEArmyCompany foot = company(false, 1);
            KOMEMovementRoutePreview.Timing footImmediate = KOMEMovementRoutePreview.timing(foot, 1, NOW);
            assertTrue(footImmediate.immediateMovementPossible);
            assertEquals(0, footImmediate.requiredBoundaries);
            KOMEMovementRoutePreview.Timing footMulti = KOMEMovementRoutePreview.timing(foot, 3, NOW);
            assertEquals(2, footMulti.requiredBoundaries);
            assertTrue(footMulti.requiresFutureBoundary);

            KOMEArmyCompany mounted = company(true, 1);
            KOMEMovementRoutePreview.Timing oneCredit = KOMEMovementRoutePreview.timing(mounted, 2, NOW);
            assertEquals(2, oneCredit.entitlement);
            assertEquals(1, oneCredit.requiredBoundaries);
            mounted.movementAllowance = 2;
            assertEquals(0, KOMEMovementRoutePreview.timing(mounted, 2, NOW).requiredBoundaries);
        }
    }

    @Test public void exhaustedCompanyUsesCanonicalConfiguredBoundaryWithoutCatchup() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEArmyCompany company = company(false, 0);
            KOMEMovementRoutePreview.Timing timing = KOMEMovementRoutePreview.timing(company, 3, NOW);
            assertTrue(timing.exhausted);
            assertFalse(timing.immediateMovementPossible);
            assertEquals(3, timing.requiredBoundaries);
            long expected = KOMEMovementDayService.schedule().nextBoundary(
                KOMEMovementDayService.schedule().latestBoundaryAtOrBefore(Instant.ofEpochMilli(NOW))).toEpochMilli();
            assertEquals(expected, timing.nextBoundaryMillis);
            assertTrue(timing.estimatedCompletionMillis > timing.nextBoundaryMillis);
        }
    }

    @Test public void alternateEntitlementsDriveEtaRatherThanHardCodedOneAndTwo() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            config.set("movement.footOrMixedTilesPerDay", "3", "movement.fullyMountedTilesPerDay", "5");
            KOMEArmyCompany foot = company(false, 2);
            KOMEArmyCompany mounted = company(true, 1);
            assertEquals(3, KOMEMovementRoutePreview.timing(foot, 8, NOW).entitlement);
            assertEquals(2, KOMEMovementRoutePreview.timing(foot, 8, NOW).requiredBoundaries);
            assertEquals(5, KOMEMovementRoutePreview.timing(mounted, 6, NOW).entitlement);
            assertEquals(1, KOMEMovementRoutePreview.timing(mounted, 6, NOW).requiredBoundaries);
        }
    }

    @Test public void fingerprintIsStableForDuplicatePreviewAndChangesWithCompanyRouteOrBoundary() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEArmyCompany company = company(false, 1);
            String first = token(company, Arrays.asList("T388", "T379", "T376"), 10L);
            assertEquals(first, token(company, Arrays.asList("T388", "T379", "T376"), 10L));
            company.updatedAtMillis++;
            assertNotEquals(first, token(company, Arrays.asList("T388", "T379", "T376"), 10L));
            company.updatedAtMillis--;
            assertNotEquals(first, token(company, Arrays.asList("T388", "T379", "T370"), 10L));
            assertNotEquals(first, token(company, Arrays.asList("T388", "T379", "T376"), 11L));
        }
    }

    @Test public void authoritativePreviewDiagnosticsRoundTripWithoutRouteTruncation() {
        KOMEPacketCompanyMoveConfirmGui source = new KOMEPacketCompanyMoveConfirmGui();
        source.companyId = "C1"; source.companyName = "Rangers"; source.originTile = "T388";
        source.destinationTile = "T376"; source.distanceTiles = 2; source.tilesPerDay = 1;
        source.remainingAllowance = 1; source.immediateMovementPossible = true;
        source.requiresFutureBoundary = true; source.requiredMovementBoundaries = 1;
        source.nextMovementBoundaryMillis = 1234L; source.estimatedCompletionMillis = 1234L;
        source.nextMovementBoundaryText = "2026-01-11T06:00-06:00[America/Chicago]";
        source.estimatedCompletionText = source.nextMovementBoundaryText;
        source.movementStatusCode = KOMEMovementRoutePreview.READY_NOW;
        source.movementStatusText = "Moves now, then waits."; source.previewToken = "token";
        source.routeTiles.addAll(Arrays.asList("T388", "T379", "T376"));
        ByteBuf bytes = Unpooled.buffer(); source.toBytes(bytes);
        KOMEPacketCompanyMoveConfirmGui restored = new KOMEPacketCompanyMoveConfirmGui(); restored.fromBytes(bytes);
        assertEquals(source.routeTiles, restored.routeTiles);
        assertEquals(1, restored.remainingAllowance); assertTrue(restored.immediateMovementPossible);
        assertTrue(restored.requiresFutureBoundary); assertEquals(1, restored.requiredMovementBoundaries);
        assertEquals(1234L, restored.nextMovementBoundaryMillis); assertEquals("token", restored.previewToken);
        assertEquals(source.nextMovementBoundaryText, restored.nextMovementBoundaryText);
        assertEquals(source.estimatedCompletionText, restored.estimatedCompletionText);
    }

    @Test public void companyCatalogueMovementDiagnosticsRoundTripExactly() {
        KOMECompanyGuiEntry source = new KOMECompanyGuiEntry();
        source.id = "C1"; source.tilesPerDay = 2; source.remainingMovementAllowance = 1;
        source.remainingRouteSteps = 3; source.requiredMovementBoundaries = 1;
        source.nextMovementBoundaryMillis = 1234L; source.nextMovementBoundaryText = "America/Chicago boundary";
        source.movementEtaText = "estimated completion tomorrow"; source.movementDiagnosticCode = "ROUTE_READY";
        ByteBuf bytes = Unpooled.buffer(); source.toBytes(bytes);
        KOMECompanyGuiEntry restored = new KOMECompanyGuiEntry(); restored.fromBytes(bytes);
        assertEquals(2, restored.tilesPerDay); assertEquals(1, restored.remainingMovementAllowance);
        assertEquals(3, restored.remainingRouteSteps); assertEquals(1, restored.requiredMovementBoundaries);
        assertEquals(1234L, restored.nextMovementBoundaryMillis);
        assertEquals(source.nextMovementBoundaryText, restored.nextMovementBoundaryText);
        assertEquals(source.movementEtaText, restored.movementEtaText);
        assertEquals("ROUTE_READY", restored.movementDiagnosticCode);
    }

    private static KOMEArmyCompany company(boolean mounted, int allowance) {
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C1"; company.name = "Company"; company.currentTile = "T388";
        company.status = KOMEArmyCompany.STATIONED; company.movementOrderId = "";
        company.totalPopulation = 10; company.mountedPopulation = mounted ? 10 : 0;
        company.groundPopulation = mounted ? 0 : 10; company.movementAllowance = allowance;
        company.movementAllowanceInitialized = true; company.movementBoundaryMillis = 10L;
        company.movementBoundarySchedule = "America/Chicago@06:00";
        return company;
    }

    private static String token(KOMEArmyCompany company, java.util.List<String> route, long boundary) {
        return KOMEMovementRoutePreview.fingerprint(company, route.get(route.size() - 1), route,
            Collections.singletonList("open"), 100, 1.5, 64, 2.5, "Rally", boundary,
            "America/Chicago@06:00");
    }
}
