package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Before;
import org.junit.Test;
import java.util.UUID;
import static org.junit.Assert.*;

public class KOMEProgressionCleanupRecruitmentTest {
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    @Before public void resetReplayGuard() { KOMECampaignRecruitmentService.clearDuplicateGuardForTests(); }

    @Test public void historicalKnightMarkerAndBankDoNotAuthorizeCampaignRecruitment() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            Fixture f = new Fixture(); f.rank(KOMEProgressionRank.KNIGHT);
            f.data.getProgression(f.owner).grant("serf.title_knight");
            f.data.getProgression(f.owner).grant("baseline.hire_units");
            long before = f.bank();
            KOMECampaignRecruitmentService.Result result = f.recruit(false);
            assertEquals(KOMECampaignRecruitmentService.Code.UNAUTHORIZED, result.code);
            assertTrue(result.reason.contains("Lord standing"));
            assertEquals(before, f.bank()); assertEquals(0, f.effect.nativeHires);
            assertTrue(f.data.armyCompanies.isEmpty()); assertTrue(f.data.hiredUnits.isEmpty());
        }
    }

    @Test public void lordWithoutCrownAuthorityFailsBeforeAnyPopulationOrNativeSideEffect() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            Fixture f = new Fixture(); f.rank(KOMEProgressionRank.LORD);
            long before = f.bank(); KOMECampaignRecruitmentService.Result result = f.recruit(false);
            assertEquals(KOMECampaignRecruitmentService.Code.UNAUTHORIZED, result.code);
            assertTrue(result.reason.contains("Royal recruitment commissions are not available"));
            assertEquals(0, f.effect.deployments); assertEquals(0, f.effect.nativeHires);
            assertEquals(before, f.bank()); assertFalse(f.data.progressions.get(f.owner)
                .isCompleted(KOMEProgressionAchievement.forID("baseline.hire_units")));
        }
    }

    @Test public void pledgedLordRulerRecruitsWithoutLegacyFlagAndPaysExactlyOnce() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            Fixture f = new Fixture(); f.rank(KOMEProgressionRank.LORD);
            KOMERulerService.assignRuler(f.data, "gondor", f.owner, "Ruler");
            long before = f.bank(); KOMECampaignRecruitmentService.Result hired = f.recruit(false);
            assertTrue(hired.reason, hired.success);
            assertEquals(before - 2000L, f.bank()); assertEquals(1, f.effect.nativeHires);
            assertEquals(1, f.effect.spawns); assertEquals(87, f.effect.coins);
            assertTrue(KOMEHiredUnitClassification.isCampaignUnit(f.unit));
            assertNotNull(f.data.armyCompanies.get(f.unit.companyId));
            assertFalse(f.recruit(false).success);
            assertEquals(before - 2000L, f.bank()); assertEquals(1, f.effect.nativeHires);
        }
    }

    @Test public void adminAuthorityDoesNotBypassBankLocationOrClassification() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            Fixture f = new Fixture(); f.rank(KOMEProgressionRank.WANDERER);
            KOMECampaignRecruitmentService.Result hired = f.recruit(true);
            assertTrue(hired.reason, hired.success); assertEquals(1, f.effect.nativeHires);
            Fixture noMoney = new Fixture(); noMoney.data.factionPopulations.clear();
            assertEquals(KOMECampaignRecruitmentService.Code.INSUFFICIENT_POPULATION,
                noMoney.recruit(true).code); assertEquals(0, noMoney.effect.nativeHires);
            Fixture invalidLocation = new Fixture(); invalidLocation.data.factionCapitals.clear();
            assertEquals(KOMECampaignRecruitmentService.Code.LOCATION_NOT_ALLOWED,
                invalidLocation.recruit(true).code); assertEquals(0, invalidLocation.effect.nativeHires);
            assertFalse(KOMEHiredUnitClassification.isCampaignUnit(invalidLocation.unit));
        }
    }

    @Test public void factionSwitchAndDemotionCannotRetainNewHireAuthorityButExistingArmySurvives() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            Fixture f = new Fixture(); f.rank(KOMEProgressionRank.LORD);
            KOMERulerService.assignRuler(f.data, "gondor", f.owner, "Ruler");
            KOMECampaignRecruitmentService.Result hired = f.recruit(false); assertTrue(hired.reason, hired.success);
            KOMEArmyCompany company = f.data.armyCompanies.get(f.unit.companyId);
            NBTTagCompound army = company.writeToNBT();
            f.data.lastKnownPlayerFactions.put(f.owner, "rohan");
            assertFalse(KOMEProgressionPermissions.campaignRecruitmentDenial(f.data, f.owner, "gondor", false).isEmpty());
            f.rank(KOMEProgressionRank.SERF);
            f.data.getProgression(f.owner).grant("baseline.hire_units");
            assertTrue(KOMEProgressionPermissions.campaignRecruitmentDenial(f.data, f.owner, "gondor", false).contains("Lord standing"));
            assertSame(f.unit, f.data.hiredUnits.get(f.unit.entity)); assertSame(company, f.data.armyCompanies.get(company.id));
            assertEquals(army, company.writeToNBT()); assertEquals(f.owner, company.owner);
            assertTrue(KOMEHiredUnitClassification.isCampaignUnit(f.unit));
        }
    }

    @Test public void oldSurvivalAndHireRecordsRemainHistoryWithoutPromotionOrGrant() {
        KOMEPlayerProgression p = new KOMEPlayerProgression();
        for (KOMEProgressionAchievement a : KOMEProgressionAchievement.ALL) p.grant(a.id);
        NBTTagCompound saved = p.writeToNBT(); KOMEPlayerProgression restored = new KOMEPlayerProgression();
        restored.readFromNBT(saved); assertEquals(saved, restored.writeToNBT());
        assertEquals(KOMEProgressionRank.WANDERER, restored.getCanonicalRank());
        assertEquals(96, KOMEProgressionAchievement.ALL.size());
        assertEquals(35, KOMEProgressionAchievement.forGroup("baseline").size());
        assertTrue(KOMEProgressionAchievement.forAdvancementsGroup("baseline").isEmpty());
        KOMEProgressionAutoCompleter.recomputeUnlocks(restored);
        assertTrue("Historical hire flag is not revoked by unrelated recomputation",
            restored.isCompleted(KOMEProgressionAchievement.forID("baseline.hire_units")));
        assertEquals(KOMEProgressionRank.WANDERER, restored.getCanonicalRank());
    }

    private static final class Fixture {
        final KOMEWorldData data = new KOMEWorldData("recruit-cleanup");
        final UUID owner = UUID.randomUUID();
        final KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        final Effect effect = new Effect();
        Fixture() {
            KOMEConquestTile tile = new KOMEConquestTile("T100"); tile.defaultRulingFaction = "gondor";
            tile.claim("gondor", 0L);
            data.conquestTiles.put(tile.id, tile);
            data.factionCapitals.put("gondor", new KOMEFactionCapitalRecord("gondor", tile.id,
                KOMETileTestResources.dimension(), KOMETileTestResources.x(), 64D, KOMETileTestResources.z(), 1L, "TEST", "SERVER"));
            data.grantFactionPopulationCenti("gondor", 100000L);
            data.lastKnownPlayerFactions.put(owner, "gondor");
            unit.entity = UUID.randomUUID(); unit.owner = owner; unit.sourcePlayer = owner;
            unit.type = KOMEPopulationType.OFFENSIVE; unit.cost = unit.baseCost = unit.populationSpent = 20;
            unit.populationOwningFaction = unit.sourceFaction = unit.unitFaction = "gondor";
            unit.unitName = "Campaign Guard";
        }
        void rank(KOMEProgressionRank rank) { KOMECanonicalRankService.setCanonicalRank(data, owner, rank); }
        long bank() { return KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"); }
        KOMECampaignRecruitmentService.Result recruit(boolean administrative) {
            return KOMECampaignRecruitmentService.recruit(new KOMECampaignRecruitmentService.Request(
                owner, "Owner", KOMECampaignRecruitmentService.SourceKind.NATIVE_TRADER, "native-trader:1", "trade:1"),
                request -> new KOMECampaignRecruitmentService.PreparedRecruitment(data, unit, "gondor", 13, effect, administrative));
        }
    }
    private static final class Effect implements KOMECampaignRecruitmentService.RecruitmentEffect {
        int deployments, nativeHires, spawns, coins = 100;
        public void prepareDeployment(KOMEWorldData data, String faction, String tile) { deployments++; }
        public void performNativeHire() { nativeHires++; coins -= 13; }
        public void applyCampaignState(KOMEHiredUnitRecord record) { record.stationedEntityData = new NBTTagCompound(); }
        public void spawn() { spawns++; }
        public void rollback() { }
    }
}
