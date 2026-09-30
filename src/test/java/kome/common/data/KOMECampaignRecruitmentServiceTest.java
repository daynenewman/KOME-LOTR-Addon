package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMECampaignRecruitmentServiceTest {
    private static final String TILE = "T100";
    private static final String OTHER_TILE = "T101";
    private static final String CAPTAIN_TILE = "T200";
    @org.junit.Rule public final KOMETileTestResources tileGeometry =
        new KOMETileTestResources();

    @Test public void futureSourceCreatesCampaignBeforeCompanyAndChargesEachEconomyOnce()
            throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            OrderingData data = new OrderingData();
            KOMEHiredUnitRecord record = record(data);
            FakeEffect effect = new FakeEffect(record, 17, false);
            long before = KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");
            KOMECampaignRecruitmentService.Request request = request(record.owner,
                KOMECampaignRecruitmentService.SourceKind.FUTURE_RECRUITMENT_SOURCE);

            KOMECampaignRecruitmentService.Result result = recruit(request,
                prepared(data, record, effect));

            assertTrue(result.reason, result.success);
            assertEquals(KOMECampaignRecruitmentService.Code.SUCCESS, result.code);
            assertTrue(data.classSeenBeforeAdmission);
            assertTrue(effect.classSeenBeforeCampaignState);
            assertTrue(effect.companySeenBeforeCampaignState);
            assertEquals(1, effect.deploymentCalls);
            assertEquals(TILE, effect.deploymentTile);
            assertEquals(1, effect.nativeHireCalls);
            assertEquals(1, effect.coinCharges);
            assertEquals(1, effect.spawnCalls);
            assertEquals(83, effect.coins);
            assertTrue(KOMEHiredUnitClassification.isCampaignUnit(record));
            assertSame(record, data.hiredUnits.get(record.entity));
            assertNotNull(data.armyCompanies.get(record.companyId));
            assertEquals(TILE, record.currentTile);
            assertNotNull(record.stationedEntityData);
            assertEquals(before - 2500L,
                KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
            assertEquals(17, result.nativeCoinCost);
            assertEquals(25, result.populationCost);
            assertEquals(1, data.centralAudit.size());
            assertEquals("CAMPAIGN_HIRE", data.centralAudit.get(0).action);
        }
    }

    @Test public void illegalTileRejectsCampaignWithoutNativeOrPopulationMutation()
            throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = controlledTile(false);
            KOMEHiredUnitRecord record = record(data);
            FakeEffect effect = new FakeEffect(record, 11, false);
            long before = KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");

            KOMECampaignRecruitmentService.Result result = recruit(
                request(record.owner, KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER),
                prepared(data, record, effect));

            assertFalse(result.success);
            assertEquals(KOMECampaignRecruitmentService.Code.LOCATION_NOT_ALLOWED, result.code);
            assertTrue(result.reason.contains("No legal KOME campaign recruitment tile"));
            assertEquals(0, effect.nativeHireCalls);
            assertEquals(0, effect.coinCharges);
            assertEquals(0, effect.deploymentCalls);
            assertTrue(effect.rollbackCalls > 0);
            assertEquals(before, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
            assertFalse(data.hiredUnits.containsKey(record.entity));
        }
    }

    @Test public void developedNonCapitalAtThresholdSucceeds() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = controlledTile(false);
            KOMEPlayerBuild build = new KOMEPlayerBuild();
            build.id = "B"; build.displayName = "B"; build.tileId = TILE;
            build.populationFaction = "gondor"; build.originalBuilderFaction = "gondor";
            build.type = KOMEBuildType.NORMAL; build.active = true;
            KOMEBuildContribution contribution = new KOMEBuildContribution();
            contribution.id = "H"; contribution.centiHours = 5000L;
            contribution.status = KOMEBuildContribution.APPROVED;
            build.contributions.add(contribution);
            build.developedNativeCentiHours = 5000L;
            build.validateContributions(); data.builds.put(build.id, build);
            KOMEHiredUnitRecord record = record(data);

            KOMECampaignRecruitmentService.Result result = recruit(
                request(record.owner, KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER),
                prepared(data, record, new FakeEffect(record, 9, false)));

            assertTrue(result.reason, result.success);
        }
    }

    @Test public void selectedCapitalOverridesIllegalCaptainTile() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = controlledTile(false);
            addControlledTile(data, CAPTAIN_TILE);
            makeLegalCapital(data);
            KOMEHiredUnitRecord record = record(data);
            record.sourceTileId = CAPTAIN_TILE;
            assertTrue(data.setActiveRecruitmentTile(record.owner, "gondor", TILE));
            FakeEffect effect = new FakeEffect(record, 17, false);

            KOMECampaignRecruitmentService.Result result = recruit(
                request(record.owner, "native-trader:42@" + CAPTAIN_TILE),
                prepared(data, record, effect));

            assertTrue(result.reason, result.success);
            assertEquals(TILE, result.strategicTile);
            assertEquals(TILE, record.currentTile);
            assertEquals(TILE, effect.deploymentTile);
            assertNotEquals(CAPTAIN_TILE, result.strategicTile);
            assertEquals(TILE, data.armyCompanies.get(record.companyId).currentTile);
        }
    }

    @Test public void selectedDevelopedTileOverridesIllegalCaptainTile() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = controlledTile(false);
            addControlledTile(data, CAPTAIN_TILE);
            addDevelopedTile(data, OTHER_TILE);
            KOMEHiredUnitRecord record = record(data);
            assertTrue(data.setActiveRecruitmentTile(record.owner, "gondor", OTHER_TILE));
            FakeEffect effect = new FakeEffect(record, 17, false);

            KOMECampaignRecruitmentService.Result result = recruit(
                request(record.owner, "native-trader:42@" + CAPTAIN_TILE),
                prepared(data, record, effect));

            assertTrue(result.reason, result.success);
            assertEquals(OTHER_TILE, result.strategicTile);
            assertEquals(OTHER_TILE, record.currentTile);
            assertEquals(OTHER_TILE, effect.deploymentTile);
            assertEquals(OTHER_TILE, data.armyCompanies.get(record.companyId).currentTile);
        }
    }

    @Test public void selectedLegalTileWinsOverDifferentLegalCaptainTile() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = legalCapitalWorld();
            addDevelopedTile(data, OTHER_TILE);
            KOMEHiredUnitRecord record = record(data);
            assertTrue(data.setActiveRecruitmentTile(record.owner, "gondor", OTHER_TILE));
            FakeEffect effect = new FakeEffect(record, 17, false);

            KOMECampaignRecruitmentService.Result result = recruit(
                request(record.owner, "native-trader:42@" + TILE),
                prepared(data, record, effect));

            assertTrue(result.reason, result.success);
            assertEquals(OTHER_TILE, result.strategicTile);
            assertEquals(OTHER_TILE, effect.deploymentTile);
        }
    }

    @Test public void noSelectionUsesSortedLegalDefaultAndStaleSelectionRevalidates()
            throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = legalCapitalWorld();
            addDevelopedTile(data, OTHER_TILE);
            KOMEHiredUnitRecord fallbackRecord = record(data);
            FakeEffect fallbackEffect = new FakeEffect(fallbackRecord, 8, false);
            KOMECampaignRecruitmentService.Result fallback = recruit(
                request(fallbackRecord.owner, "native-trader:42@" + CAPTAIN_TILE),
                prepared(data, fallbackRecord, fallbackEffect));
            assertTrue(fallback.reason, fallback.success);
            assertEquals(TILE, fallback.strategicTile);

            KOMECampaignRecruitmentService.clearDuplicateGuardForTests();
            KOMEHiredUnitRecord staleRecord = record(data);
            assertTrue(data.setActiveRecruitmentTile(staleRecord.owner, "gondor", OTHER_TILE));
            data.conquestTiles.get(OTHER_TILE).claim("rohan", 1L);
            FakeEffect staleEffect = new FakeEffect(staleRecord, 8, false);
            KOMECampaignRecruitmentService.Result stale = recruit(
                request(staleRecord.owner, "native-trader:42@" + CAPTAIN_TILE),
                prepared(data, staleRecord, staleEffect));
            assertTrue(stale.reason, stale.success);
            assertEquals(TILE, stale.strategicTile);
            assertEquals(TILE, staleEffect.deploymentTile);
        }
    }

    @Test public void deploymentFailureOccursBeforeChargesAndLeavesNoCampaignState()
            throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = legalCapitalWorld();
            KOMEHiredUnitRecord record = record(data);
            FakeEffect effect = new FakeEffect(record, 19, false, true);
            long before = KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");

            KOMECampaignRecruitmentService.Result result = recruit(
                request(record.owner, KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER),
                prepared(data, record, effect));

            assertEquals(KOMECampaignRecruitmentService.Code.DEPLOYMENT_FAILED, result.code);
            assertTrue(result.reason.contains(TILE));
            assertEquals(1, effect.deploymentCalls);
            assertEquals(0, effect.nativeHireCalls);
            assertEquals(0, effect.coinCharges);
            assertEquals(100, effect.coins);
            assertFalse(effect.npcAlive);
            assertFalse(effect.mountAlive);
            assertEquals(before, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
            assertFalse(data.hiredUnits.containsKey(record.entity));
            assertFalse(KOMEHiredUnitClassification.isCampaignUnit(record));
            assertEquals("", record.currentTile);
            assertEquals("", record.companyId);
            assertNull(record.stationedEntityData);
            assertTrue(data.armyCompanies.isEmpty());
        }
    }

    @Test public void insufficientPopulationPreventsCoinChargeAndLeavesNoRecord()
            throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = legalCapitalWorld();
            assertTrue(KOMEPopulationService.trySpendCenti(data, "gondor",
                KOMEPopulationService.getAvailablePopulationCenti(data, "gondor")));
            KOMEHiredUnitRecord record = record(data);
            FakeEffect effect = new FakeEffect(record, 13, false);

            KOMECampaignRecruitmentService.Result result = recruit(
                request(record.owner, KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER),
                prepared(data, record, effect));

            assertEquals(KOMECampaignRecruitmentService.Code.INSUFFICIENT_POPULATION, result.code);
            assertEquals(0, effect.coinCharges);
            assertEquals(100, effect.coins);
            assertFalse(data.hiredUnits.containsKey(record.entity));
            assertTrue(data.armyCompanies.isEmpty());
        }
    }

    @Test public void companyOrSpawnFailureRestoresPopulationCoinsClassAndCompanies()
            throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            FailingAdmissionData companyFailure = new FailingAdmissionData();
            makeLegalCapital(companyFailure);
            KOMEHiredUnitRecord first = record(companyFailure);
            FakeEffect firstEffect = new FakeEffect(first, 12, false);
            long beforeCompany = KOMEPopulationService.getAvailablePopulationCenti(companyFailure, "gondor");
            KOMECampaignRecruitmentService.Result rejected = recruit(
                request(first.owner, KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER),
                prepared(companyFailure, first, firstEffect));
            assertEquals(KOMECampaignRecruitmentService.Code.COMPANY_ADMISSION_FAILED, rejected.code);
            assertFalse(KOMEHiredUnitClassification.isCampaignUnit(first));
            assertFalse(companyFailure.hiredUnits.containsKey(first.entity));
            assertTrue(companyFailure.armyCompanies.isEmpty());
            assertEquals(100, firstEffect.coins);
            assertEquals(beforeCompany,
                KOMEPopulationService.getAvailablePopulationCenti(companyFailure, "gondor"));

            KOMEWorldData spawnFailure = legalCapitalWorld();
            KOMEHiredUnitRecord second = record(spawnFailure);
            FakeEffect secondEffect = new FakeEffect(second, 14, true);
            long beforeSpawn = KOMEPopulationService.getAvailablePopulationCenti(spawnFailure, "gondor");
            KOMECampaignRecruitmentService.Result failedSpawn = recruit(
                request(second.owner, KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER),
                prepared(spawnFailure, second, secondEffect));
            assertEquals(KOMECampaignRecruitmentService.Code.NATIVE_HIRE_FAILED, failedSpawn.code);
            assertFalse(spawnFailure.hiredUnits.containsKey(second.entity));
            assertTrue(spawnFailure.armyCompanies.isEmpty());
            assertEquals(100, secondEffect.coins);
            assertEquals(beforeSpawn,
                KOMEPopulationService.getAvailablePopulationCenti(spawnFailure, "gondor"));
        }
    }

    @Test public void farmhandAndInsufficientCoinsFailBeforePopulationMutation()
            throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = legalCapitalWorld();
            KOMEHiredUnitRecord farmhand = record(data);
            farmhand.farmhand = true;
            farmhand.cost = farmhand.baseCost = farmhand.populationSpent = 0;
            FakeEffect effect = new FakeEffect(farmhand, 5, false);
            long before = KOMEPopulationService.getAvailablePopulationCenti(data, "gondor");
            KOMECampaignRecruitmentService.Result farmResult = recruit(
                request(farmhand.owner, KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER),
                prepared(data, farmhand, effect));
            assertEquals(KOMECampaignRecruitmentService.Code.FARMHAND_UNSUPPORTED, farmResult.code);
            assertEquals(before, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));

            KOMECampaignRecruitmentService.clearDuplicateGuardForTests();
            KOMECampaignRecruitmentService.Result coinResult =
                KOMECampaignRecruitmentService.recruit(
                    request(farmhand.owner, KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER),
                    request -> { throw new KOMECampaignRecruitmentService.RecruitmentFailure(
                        KOMECampaignRecruitmentService.Code.INSUFFICIENT_COINS,
                        "Not enough native LOTR coins."); });
            assertEquals(KOMECampaignRecruitmentService.Code.INSUFFICIENT_COINS, coinResult.code);
            assertEquals(before, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
            KOMECampaignRecruitmentService.clearDuplicateGuardForTests();
        }
    }

    @Test public void duplicateInFlightRequestIsRejectedAndAlwaysCleared() {
        KOMECampaignRecruitmentService.clearDuplicateGuardForTests();
        final UUID owner = UUID.randomUUID();
        final KOMECampaignRecruitmentService.Request request = request(owner,
            KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER);
        final KOMECampaignRecruitmentService.Result[] nested = new KOMECampaignRecruitmentService.Result[1];
        KOMECampaignRecruitmentService.Result outer =
            KOMECampaignRecruitmentService.recruit(request, intent -> {
                nested[0] = KOMECampaignRecruitmentService.recruit(request, ignored -> {
                    throw new AssertionError("duplicate source must not prepare");
                });
                throw new KOMECampaignRecruitmentService.RecruitmentFailure(
                    KOMECampaignRecruitmentService.Code.INVALID_SOURCE, "stop outer request");
            });
        assertEquals(KOMECampaignRecruitmentService.Code.DUPLICATE_REQUEST, nested[0].code);
        assertEquals(KOMECampaignRecruitmentService.Code.INVALID_SOURCE, outer.code);
        KOMECampaignRecruitmentService.Result rapidRetry =
            KOMECampaignRecruitmentService.recruit(request, intent -> {
                throw new AssertionError("rapid duplicate must not prepare");
            });
        assertEquals(KOMECampaignRecruitmentService.Code.DUPLICATE_REQUEST, rapidRetry.code);
        KOMECampaignRecruitmentService.clearDuplicateGuardForTests();
        KOMECampaignRecruitmentService.Result retry =
            KOMECampaignRecruitmentService.recruit(request, intent -> {
                throw new KOMECampaignRecruitmentService.RecruitmentFailure(
                    KOMECampaignRecruitmentService.Code.INVALID_SOURCE, "retry reached source");
            });
        assertEquals(KOMECampaignRecruitmentService.Code.INVALID_SOURCE, retry.code);
        KOMECampaignRecruitmentService.clearDuplicateGuardForTests();
    }

    private static KOMECampaignRecruitmentService.Result recruit(
            KOMECampaignRecruitmentService.Request request,
            KOMECampaignRecruitmentService.PreparedRecruitment prepared) {
        return KOMECampaignRecruitmentService.recruit(request, ignored -> prepared);
    }

    private static KOMECampaignRecruitmentService.PreparedRecruitment prepared(
            KOMEWorldData data, KOMEHiredUnitRecord record, FakeEffect effect) {
        return new KOMECampaignRecruitmentService.PreparedRecruitment(
            data, record, "gondor", effect.coinCost, effect);
    }

    private static KOMECampaignRecruitmentService.Request request(UUID owner,
            KOMECampaignRecruitmentService.SourceKind source) {
        return request(owner, source, source == KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER
            ? "native-trader:42" : "future:test-source");
    }

    private static KOMECampaignRecruitmentService.Request request(UUID owner,
            String sourceReference) {
        return request(owner, KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER,
            sourceReference);
    }

    private static KOMECampaignRecruitmentService.Request request(UUID owner,
            KOMECampaignRecruitmentService.SourceKind source, String sourceReference) {
        return new KOMECampaignRecruitmentService.Request(owner, "Owner", source,
            sourceReference, "trade:3");
    }

    private static KOMEHiredUnitRecord record(KOMEWorldData data) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID(); record.owner = UUID.randomUUID();
        record.sourcePlayer = record.owner; record.sourceTileId = TILE;
        record.type = KOMEPopulationType.OFFENSIVE;
        record.cost = record.baseCost = record.populationSpent = 25;
        record.populationOwningFaction = record.sourceFaction = "gondor";
        record.unitFaction = "gondor"; record.unitName = "Guard";
        data.lastKnownPlayerFactions.put(record.owner, "gondor");
        return record;
    }

    private static KOMEWorldData legalCapitalWorld() {
        KOMEWorldData data = controlledTile(true);
        return data;
    }

    private static KOMEWorldData controlledTile(boolean capital) {
        KOMEWorldData data = new KOMEWorldData("campaign-recruitment");
        addControlledTile(data, TILE);
        if (capital) makeLegalCapital(data);
        data.grantFactionPopulationCenti("gondor", 100000L);
        return data;
    }

    private static void addControlledTile(KOMEWorldData data, String tileId) {
        KOMEConquestTile tile = new KOMEConquestTile(tileId);
        tile.defaultRulingFaction = "gondor";
        tile.claim("gondor", 0L);
        data.conquestTiles.put(tile.id, tile);
    }

    private static void addDevelopedTile(KOMEWorldData data, String tileId) {
        addControlledTile(data, tileId);
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = "B-" + tileId; build.displayName = build.id; build.tileId = tileId;
        build.populationFaction = "gondor"; build.originalBuilderFaction = "gondor";
        build.type = KOMEBuildType.NORMAL; build.active = true;
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = "H-" + tileId; contribution.centiHours = 5000L;
        contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution);
        build.developedNativeCentiHours = 5000L;
        build.validateContributions();
        data.builds.put(build.id, build);
    }

    private static void makeLegalCapital(KOMEWorldData data) {
        if (!data.conquestTiles.containsKey(TILE)) {
            KOMEConquestTile tile = new KOMEConquestTile(TILE);
            tile.defaultRulingFaction = "gondor"; tile.claim("gondor", 0L);
            data.conquestTiles.put(TILE, tile);
        }
        data.factionCapitals.put("gondor", new KOMEFactionCapitalRecord(
            "gondor", TILE, KOMETileTestResources.dimension(),
            KOMETileTestResources.x(), 64.0D, KOMETileTestResources.z(),
            1L, "TEST", "SERVER"));
        data.grantFactionPopulationCenti("gondor", 100000L);
    }

    private static class OrderingData extends KOMEWorldData {
        boolean classSeenBeforeAdmission;
        OrderingData() { super("ordering"); makeLegalCapital(this); }
        @Override KOMEArmyCompany assignUnitToCampaignCompanyAtTile(
                KOMEHiredUnitRecord record, String ownerName, String tile) {
            classSeenBeforeAdmission = KOMEHiredUnitClassification.isCampaignUnit(record);
            return super.assignUnitToCampaignCompanyAtTile(record, ownerName, tile);
        }
    }

    private static final class FailingAdmissionData extends KOMEWorldData {
        FailingAdmissionData() { super("failure"); }
        @Override KOMEArmyCompany assignUnitToCampaignCompanyAtTile(
                KOMEHiredUnitRecord record, String ownerName, String tile) {
            KOMEArmyCompany orphan = new KOMEArmyCompany();
            orphan.id = "ORPHAN"; orphan.owner = record.owner; orphan.units.add(record.entity);
            armyCompanies.put(orphan.id, orphan); record.companyId = orphan.id;
            return null;
        }
    }

    private static final class FakeEffect
            implements KOMECampaignRecruitmentService.RecruitmentEffect {
        final KOMEHiredUnitRecord record;
        final int coinCost;
        final boolean failSpawn;
        final boolean failDeployment;
        int coins = 100;
        int deploymentCalls;
        int nativeHireCalls;
        int coinCharges;
        int spawnCalls;
        int rollbackCalls;
        boolean charged;
        boolean npcAlive = true;
        boolean mountAlive = true;
        String deploymentTile = "";
        boolean classSeenBeforeCampaignState;
        boolean companySeenBeforeCampaignState;

        FakeEffect(KOMEHiredUnitRecord record, int coinCost, boolean failSpawn) {
            this(record, coinCost, failSpawn, false);
        }

        FakeEffect(KOMEHiredUnitRecord record, int coinCost, boolean failSpawn,
                boolean failDeployment) {
            this.record = record; this.coinCost = coinCost;
            this.failSpawn = failSpawn; this.failDeployment = failDeployment;
        }

        @Override public void prepareDeployment(KOMEWorldData data,
                String payingFaction, String strategicTile) {
            deploymentCalls++;
            deploymentTile = KOMEConquestTile.normalizeId(strategicTile);
            assertFalse(KOMEHiredUnitClassification.isCampaignUnit(record));
            if (failDeployment) throw new IllegalStateException("simulated deployment failure");
        }

        @Override public void performNativeHire() {
            assertEquals(1, deploymentCalls);
            nativeHireCalls++; coinCharges++; coins -= coinCost; charged = true;
        }

        @Override public void applyCampaignState(KOMEHiredUnitRecord candidate) {
            assertSame(record, candidate);
            classSeenBeforeCampaignState = KOMEHiredUnitClassification.isCampaignUnit(candidate);
            companySeenBeforeCampaignState = candidate.companyId != null
                && candidate.companyId.length() > 0;
            candidate.stationedEntityData = new NBTTagCompound();
        }

        @Override public void spawn() {
            spawnCalls++;
            if (failSpawn) throw new IllegalStateException("simulated native spawn failure");
        }

        @Override public void rollback() {
            rollbackCalls++;
            npcAlive = false;
            mountAlive = false;
            if (charged) { coins += coinCost; charged = false; }
        }
    }
}
