package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** Cross-system regression coverage for Builds, split population, companies, and stage milestones. */
public class KOMERedesignSystemsTest {
    @Test public void hundredthAndQuarterHoursAreExact() {
        assertEquals(1L, KOMEBuildTime.parseHours("0.01"));
        assertEquals(10L, KOMEBuildTime.parseHours("0.10"));
        assertEquals(25L, KOMEBuildTime.parseHours("0.25"));
        assertEquals(75L, KOMEBuildTime.parseHours("0.75"));
    }

    @Test public void typedWholeAndHalfHoursUseCentiHours() {
        assertEquals(0L, KOMEBuildTime.parseHours("0"));
        assertEquals(50L, KOMEBuildTime.parseHours("0.5"));
        assertEquals(100L, KOMEBuildTime.parseHours("1"));
        assertEquals(100L, KOMEBuildTime.parseHours("1.00"));
        assertEquals(150L, KOMEBuildTime.parseHours(" 1.5 "));
    }

    @Test public void typedInvalidHoursAreRejected() {
        for (String value : new String[] {"", " ", "-0.5", "1.001", "1.000", "NaN", "Infinity", "1e0", "one", "1..5"}) {
            try {
                KOMEBuildTime.parseHours(value);
                fail("Expected invalid Build hours: " + value);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("Build hours"));
            }
        }
    }

    @Test public void hourButtonsRejectOverflowAndNegativeInsteadOfClamping() {
        assertEquals(50L, KOMEBuildTime.adjustHours(0, 50L));
        for (long[] values : new long[][] {{0L, -50L}, {Long.MAX_VALUE, 50L}}) {
            try { KOMEBuildTime.adjustHours(values[0], values[1]); fail("Expected rejection"); }
            catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
        }
    }

    @Test public void ownControlledTileAllowsBuildPlacement() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        assertTrue(KOMEBuildService.canPlace(data, "gondor", "T100", "gondor").allowed);
    }

    @Test public void alliedControlledTileStillRequiresExplicitBuildPermission() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        establishCanonicalDiplomacy(data, "gondor", "rohan", KOMEDiplomacyRelation.ALLIES);
        assertFalse(KOMEBuildService.canPlace(data, "gondor", "T100", "gondor").allowed);
        grantConstruction(data, "T100", "rohan", "gondor");
        assertTrue(KOMEBuildService.canPlace(data, "gondor", "T100", "gondor").allowed);
    }

    @Test public void friendlyControlledTileStillRequiresExplicitBuildPermission() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        establishCanonicalDiplomacy(data, "gondor", "rohan", KOMEDiplomacyRelation.FRIENDS);
        assertFalse(KOMEBuildService.canPlace(data, "gondor", "T100", "gondor").allowed);
    }

    @Test public void enemyControlledTileRejectsBuildPlacementEvenInDefaultHomeland() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "mordor");
        assertFalse(KOMEBuildService.canPlace(data, "gondor", "T100", "gondor").allowed);
    }

    @Test public void foreignPopulationOwnerMustBeSafeWithPlayerAndController() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        establishCanonicalDiplomacy(data, "gondor", "rohan", KOMEDiplomacyRelation.FRIENDS);
        grantConstruction(data, "T100", "rohan", "gondor");
        assertTrue(KOMEBuildService.canPlace(data, "gondor", "T100", "rohan").allowed);
        assertFalse(KOMEBuildService.canPlace(data, "gondor", "T100", "mordor").allowed);
    }

    @Test public void foreignPopulationOwnerCannotExploitAnUnsafeThirdFactionRelationship() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        establishCanonicalDiplomacy(data, "gondor", "rohan", KOMEDiplomacyRelation.FRIENDS);
        grantConstruction(data, "T100", "rohan", "gondor");
        establishCanonicalDiplomacy(data, "gondor", "bree", KOMEDiplomacyRelation.FRIENDS);
        assertFalse(KOMEBuildService.canPlace(data, "gondor", "T100", "bree").allowed);
        establishCanonicalDiplomacy(data, "rohan", "bree", KOMEDiplomacyRelation.FRIENDS);
        assertTrue(KOMEBuildService.canPlace(data, "gondor", "T100", "bree").allowed);
    }

    private static void grantConstruction(KOMEWorldData data, String tile, String owner, String grantee) {
        UUID ruler = UUID.randomUUID();
        assertTrue(KOMERulerService.assignRuler(data, owner, ruler, "Ruler"));
        assertTrue(KOMEForeignConstructionService.grant(data, tile, ruler, grantee, 1L).allowed);
    }

    @Test public void originalManagerContributionApprovesImmediately() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        UUID builder = UUID.randomUUID();
        KOMEPlayerBuild build = KOMEBuildService.create(data, "Citadel", "T100", 0, 0, 64, 0,
            builder, "Builder", "gondor", "gondor", KOMEBuildType.NORMAL, 100L, 10L);
        assertEquals(100L, build.approvedCentiHours());
        assertTrue(build.isNormal());
        assertEquals(KOMEBuildContribution.APPROVED, build.contributions.get(0).status);
        assertEquals(builder, build.contributions.get(0).decidedByUuid);
        assertEquals("Builder", build.contributions.get(0).decidedByName);
    }

    @Test public void otherPlayerContributionRemainsPending() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 0, 0);
        KOMEBuildContribution contribution = KOMEBuildService.addSubmission(data, build, UUID.randomUUID(),
            "Helper", "rohan", 100L, false, 20L);
        assertTrue(contribution.isPending());
        assertEquals(0L, build.approvedCentiHours());
    }

    @Test public void managerApprovalAppliesPopulationAndCredit() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 0, 0);
        UUID helper = UUID.randomUUID();
        KOMEBuildContribution contribution = KOMEBuildService.addSubmission(
            data, build, helper, "Helper", "rohan", 100L, false, 20L);
        assertTrue(KOMEBuildService.decideSubmission(data, build, contribution.id,
            build.managerUuid, build.managerName, true, "approved", 30L).allowed);
        assertEquals(100L, build.approvedCentiHours());
        assertEquals(Long.valueOf(100L), build.activeCentiHoursByPlayer().get(helper));
        assertEquals(Long.valueOf(100L), build.activeCentiHoursByFaction().get("rohan"));
    }

    @Test public void rejectionNeverAppliesPopulation() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 0, 0);
        KOMEBuildContribution contribution = KOMEBuildService.addSubmission(
            data, build, UUID.randomUUID(), "Helper", "rohan", 100L, false, 20L);
        assertTrue(KOMEBuildService.decideSubmission(data, build, contribution.id,
            build.managerUuid, build.managerName, false, "rejected", 30L).allowed);
        assertEquals(0L, build.approvedCentiHours());
    }

    @Test public void canonicalBuildHasOneHoursStream() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 4, 0);
        assertEquals(200L, build.approvedCentiHours());
        assertEquals(0L, build.approvedDefensiveCentiHours());
    }

    @Test public void managerRemovalReversesActiveCredit() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 2, 0);
        String id = build.contributions.get(0).id;
        assertTrue(KOMEBuildService.removeApprovedContribution(data, build, id,
            build.managerUuid, build.managerName, "remove", 40L).allowed);
        assertEquals(0L, build.approvedCentiHours());
        assertTrue(build.activeCentiHoursByFaction().isEmpty());
    }

    @Test public void removalDoesNotDependOnFormerUnitCommitments() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 4, 0);
        assertTrue(KOMEBuildService.removeApprovedContribution(data, build, build.contributions.get(0).id,
            build.managerUuid, build.managerName, "unsafe", 40L).allowed);
    }

    @Test public void buildDeletionRemovesPopulationAndMarker() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 4, 0);
        assertTrue(KOMEBuildService.deleteBuild(data, build, build.managerUuid,
            build.managerName, false, "delete", 40L).allowed);
        assertFalse(build.active);
        assertFalse(build.markerVisible);
    }

    @Test public void buildDeletionDoesNotDependOnCommittedPopulation() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 4, 0);
        assertTrue(KOMEBuildService.deleteBuild(data, build, build.managerUuid,
            build.managerName, false, "delete", 40L).allowed);
    }

    @Test public void managerTransfersToPopulationFactionKing() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 0, 0);
        KOMEPlayerProgression departed = new KOMEPlayerProgression();
        departed.setPledgedLord("x", "x", "rohan");
        data.progressions.put(build.managerUuid, departed);
        UUID king = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", king, "King");
        KOMEBuildService.reconcileManager(data, build);
        assertEquals(king, build.managerUuid);
        assertEquals("King", build.managerName);
    }

    @Test public void pendingSubmissionsRemainAttachedWhenManagementTransfersToKing() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 0, 0);
        KOMEBuildContribution pending = KOMEBuildService.addSubmission(data, build, UUID.randomUUID(),
            "Helper", "rohan", 50L, false, 20L);
        KOMEPlayerProgression departed = new KOMEPlayerProgression();
        departed.setPledgedLord("x", "x", "rohan");
        data.progressions.put(build.managerUuid, departed);
        UUID king = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", king, "King");
        KOMEBuildService.reconcileManager(data, build);
        assertEquals(king, build.managerUuid);
        assertSame(pending, build.getContribution(pending.id));
        assertTrue(build.getContribution(pending.id).isPending());
        assertTrue(KOMEBuildService.decideSubmission(data, build, pending.id,
            king, "King", true, "approved", 30L).allowed);
    }

    @Test public void managerFallbackPreservesUnmanageableBuildWhenNoKingExists() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 0, 0);
        KOMEPlayerProgression departed = new KOMEPlayerProgression();
        departed.setPledgedLord("x", "x", "rohan");
        data.progressions.put(build.managerUuid, departed);
        assertTrue(KOMEBuildService.reconcileManager(data, build));
        assertNull(build.managerUuid);
        assertTrue(build.active);
    }

    @Test public void enemyBuildDestructionWorksOnlyInControllersDefaultHomeland() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        UUID king = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", king, "King");
        KOMEPlayerBuild enemy = manualBuild(data, "mordor");
        assertNotNull(KOMEWarService.createWar(data, "gondor", "mordor", "Homeland defense", "test", 40L));
        assertTrue(KOMEBuildService.destroyEnemyBuild(data, enemy, king, "King", "gondor", false, 50L).allowed);
    }

    @Test public void destroyButtonPreflightSelectsExistingManagerDeletePermission() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 0, 0);
        assertTrue(KOMEBuildService.canDeleteBuild(data, build, build.managerUuid, false).allowed);
        assertFalse(KOMEBuildService.canDeleteBuild(data, build, UUID.randomUUID(), false).allowed);
    }

    @Test public void destroyButtonPreflightSelectsEligibleHostileKingPermission() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        UUID king = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", king, "King");
        KOMEPlayerBuild enemy = manualBuild(data, "mordor");
        KOMEWarService.createWar(data, "gondor", "mordor", "Homeland defense", "test", 40L);
        assertTrue(KOMEBuildService.canDestroyEnemyBuild(
            data, enemy, king, "gondor", false).allowed);
    }

    @Test public void destroyButtonPreflightReturnsExactInvalidKingReason() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild enemy = manualBuild(data, "mordor");
        KOMEWarService.createWar(data, "gondor", "mordor", "Homeland defense", "test", 40L);
        KOMEBuildService.Decision denied = KOMEBuildService.canDestroyEnemyBuild(
            data, enemy, UUID.randomUUID(), "gondor", false);
        assertFalse(denied.allowed);
        assertEquals("Only the current controller's king or an administrator may destroy an enemy Build.",
            denied.reason);
    }

    @Test public void enemyBuildDestructionIsRejectedInForeignConqueredLand() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "gondor");
        UUID king = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", king, "King");
        assertFalse(KOMEBuildService.destroyEnemyBuild(data, manualBuild(data, "mordor"),
            king, "King", "gondor", false, 50L).allowed);
    }

    @Test public void alliedBuildCannotBeDestroyed() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        UUID king = UUID.randomUUID();
        KOMERulerService.assignRuler(data, "gondor", king, "King");
        assertFalse(KOMEBuildService.destroyEnemyBuild(data, manualBuild(data, "rohan"),
            king, "King", "gondor", false, 50L).allowed);
    }

    @Test public void multipleBuildsAndOwnersRemainDistinct() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        manualBuild(data, "gondor");
        manualBuild(data, "rohan");
        manualBuild(data, "gondor");
        assertEquals(3, KOMEBuildService.buildsInTile(data, "T100", false).size());
    }

    @Test public void deletionReversesApprovedAndPendingContributionStatuses() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 2, 0);
        KOMEBuildContribution approved = build.contributions.get(0);
        KOMEBuildContribution pending = KOMEBuildService.addSubmission(data, build, UUID.randomUUID(),
            "Helper", "rohan", 50L, false, 20L);
        assertTrue(KOMEBuildService.deleteBuild(data, build, build.managerUuid,
            build.managerName, false, "delete", 40L).allowed);
        assertTrue(approved.isRemoved());
        assertEquals(KOMEBuildContribution.REJECTED, pending.status);
        assertTrue(build.activeCentiHoursByPlayer().isEmpty());
        assertTrue(build.activeCentiHoursByFaction().isEmpty());
    }

    @Test public void buildAndContributionPersistenceRoundTrip() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 3, 0);
        NBTTagCompound nbt = new NBTTagCompound();
        data.writeToNBT(nbt);
        KOMEWorldData restored = new KOMEWorldData("test");
        restored.readFromNBT(nbt);
        KOMEPlayerBuild loaded = restored.getBuild(build.id);
        assertNotNull(loaded);
        assertEquals(150L, loaded.approvedCentiHours());
        assertEquals(KOMEBuildType.NORMAL, loaded.type);
    }

    @Test public void canonicalBuildNbtContainsOnlyTypeAndSingleHoursValue() {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = "B1";
        build.type = KOMEBuildType.DEFENSIVE;
        KOMEBuildContribution contribution = approved("gondor", 7);
        build.contributions.add(contribution);
        NBTTagCompound nbt = build.writeToNBT();
        assertEquals("DEFENSIVE", nbt.getString("BuildType"));
        assertFalse(nbt.hasKey("OffensiveCommittedPopulation"));
        assertFalse(nbt.hasKey("DefensiveCommittedPopulation"));
        NBTTagCompound saved = nbt.getTagList("Contributions", 10).getCompoundTagAt(0);
        assertEquals(350L, saved.getLong("CentiHours"));
        assertFalse(saved.hasKey("HalfHours"));
        assertFalse(saved.hasKey("OffensiveHalfHours"));
        assertFalse(saved.hasKey("DefensiveHalfHours"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingBuildTypeIsRejectedRatherThanAssumedNormal() {
        new KOMEPlayerBuild().readFromNBT(new NBTTagCompound());
    }

    @Test public void worldLoadBlocksStaleBuildWithoutType() {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        saved.setInteger("BuildDataSchemaVersion", KOMEWorldData.BUILD_DATA_SCHEMA_VERSION);
        saved.setTag("Builds", new net.minecraft.nbt.NBTTagList());
        saved.setInteger("FactionPopulationDataSchemaVersion", KOMEWorldData.FACTION_POPULATION_DATA_SCHEMA_VERSION);
        saved.setTag("FactionPopulations", new NBTTagList());
        NBTTagList builds = new NBTTagList();
        NBTTagCompound stale = new NBTTagCompound();
        stale.setString("Id", "B-stale");
        stale.setString("TileId", "T100");
        stale.setString("PopulationFaction", "gondor");
        builds.appendTag(stale);
        saved.setTag("Builds", builds);
        KOMEWorldData data = new KOMEWorldData("test");
        try { data.readFromNBT(saved); fail("Expected malformed Build rejection"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("B-stale")); }
        assertTrue(data.isWriteBlocked());
        assertNull(data.getBuild("B-stale"));
    }

    @Test public void normalRateIsExactAndDefensiveRateIsZero() throws Exception {
        KOMEPlayerBuild normal = new KOMEPlayerBuild();
        normal.type = KOMEBuildType.NORMAL;
        normal.contributions.add(approved("gondor", 20));
        assertEquals(1_000_000L, KOMEPopulationTestConfig.rateFromApprovedCentiHours(normal.approvedCentiHours(), 1000L).longValueExact());
        assertEquals(0L, KOMEPopulationTestConfig.rateFromApprovedCentiHours(normal.approvedCentiHours(), (long) Integer.MAX_VALUE * 100L).longValueExact());
        KOMEPlayerBuild defensive = new KOMEPlayerBuild();
        defensive.type = KOMEBuildType.DEFENSIVE;
        defensive.contributions.add(approved("gondor", 20));
        assertTrue(defensive.isDefensive());
        assertEquals(1000L, defensive.approvedDefensiveCentiHours());
    }

    @Test public void downstreamBuildQueriesSeparateNormalAndDefensive() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild normal = KOMEBuildService.create(data, "Normal", "T100", 0, 0, 64, 0,
            UUID.randomUUID(), "Builder", "gondor", "gondor", KOMEBuildType.NORMAL, 400L, 10L);
        KOMEPlayerBuild defensive = KOMEBuildService.create(data, "Defensive", "T100", 0, 0, 64, 0,
            UUID.randomUUID(), "Builder", "gondor", "gondor", KOMEBuildType.DEFENSIVE, 300L, 10L);
        assertEquals(java.util.Collections.singletonList(normal), KOMEBuildService.activeNormalBuilds(data));
        assertEquals(java.util.Collections.singletonList(defensive), KOMEBuildService.activeDefensiveBuilds(data));
        assertTrue(KOMEBuildService.decideSubmission(data, normal, normal.contributions.get(0).id,
            normal.managerUuid, "Builder", true, "Reviewed", 11L).allowed);
        assertEquals(400_000L, kome.common.data.KOMEPopulationProjection.of(data, "gondor").dailyRateUnits.longValueExact());
        assertEquals(300L, defensive.approvedCentiHours());
    }

    @Test public void buildMutationsNeverChangeFactionAvailablePopulation() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        data.grantFactionPopulationCenti("gondor", 9100L);
        UUID manager = UUID.randomUUID();
        KOMEPlayerBuild build = KOMEBuildService.create(data, "Build", "T100", 0, 0, 64, 0,
            manager, "Manager", "gondor", "gondor", KOMEBuildType.NORMAL, 100L, 10L);
        KOMEBuildContribution pending = KOMEBuildService.addSubmission(data, build, UUID.randomUUID(),
            "Helper", "gondor", 150L, false, 20L);
        assertTrue(KOMEBuildService.decideSubmission(data, build, pending.id, manager, "Manager", true,
            "approved", 30L).allowed);
        assertTrue(KOMEBuildService.removeApprovedContribution(data, build, pending.id, manager, "Manager",
            "removed", 40L).allowed);
        assertTrue(KOMEBuildService.deleteBuild(data, build, manager, "Manager", false, "deleted", 50L).allowed);
        assertEquals(9100L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void canonicalCreationRejectsMissingType() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEBuildService.create(data, "Build", "T100", 0, 0, 64, 0, UUID.randomUUID(), "Builder",
            "gondor", "gondor", null, 1, 10L);
    }

    @Test public void hiredUnitsHaveNoBuildFundingReference() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 10, 0);
        assertEquals(500L, build.approvedCentiHours());
    }

    @Test public void firstHireCreatesOneSourceTileCompany() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID owner = UUID.randomUUID();
        setFaction(data, owner, "gondor");
        KOMEHiredUnitRecord unit = unit(owner, "gondor", "T100", 25);
        KOMEArmyCompany company = data.assignUnitToHiringTileCompany(unit, "Player");
        assertNotNull(company);
        assertEquals("T100", company.sourceTileId);
        assertEquals(1, company.units.size());
    }

    @Test public void laterHireFromSameTileJoinsSameCompany() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID owner = UUID.randomUUID();
        setFaction(data, owner, "gondor");
        KOMEArmyCompany first = data.assignUnitToHiringTileCompany(unit(owner, "gondor", "T100", 25), "Player");
        KOMEArmyCompany second = data.assignUnitToHiringTileCompany(unit(owner, "gondor", "T100", 25), "Player");
        assertSame(first, second);
        assertEquals(2, first.units.size());
        assertEquals(1, data.armyCompanies.size());
    }

    @Test public void renamedCompanyRetainsImmutableSourceTileIdentity() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID owner = UUID.randomUUID();
        setFaction(data, owner, "gondor");
        KOMEArmyCompany company = data.assignUnitToHiringTileCompany(unit(owner, "gondor", "T100", 25), "Player");
        assertTrue(data.renameHiringCompany(company.id, owner, "Northern Watch"));
        assertEquals("Northern Watch", company.name);
        assertEquals("T100", company.sourceTileId);
    }

    @Test public void awayCompanyStillReceivesHireWithoutDuplicateCompany() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID owner = UUID.randomUUID();
        setFaction(data, owner, "gondor");
        KOMEArmyCompany company = data.assignUnitToHiringTileCompany(unit(owner, "gondor", "T100", 25), "Player");
        company.currentTile = "T999";
        KOMEArmyCompany reused = data.assignUnitToHiringTileCompany(unit(owner, "gondor", "T100", 25), "Player");
        assertSame(company, reused);
        assertEquals("T999", reused.currentTile);
        assertEquals(1, data.armyCompanies.size());
    }

    @Test public void awayCompanyFallbackKeepsNewUnitAtHireTileButInTheSameCompany() {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID owner = UUID.randomUUID();
        setFaction(data, owner, "gondor");
        KOMEArmyCompany company = data.assignUnitToHiringTileCompany(
            unit(owner, "gondor", "T100", 25), "Player");
        company.currentTile = "T999";
        KOMEHiredUnitRecord hire = unit(owner, "gondor", "T100", 25);
        KOMEArmyCompany reused = data.assignUnitToHiringTileCompany(hire, "Player");
        assertSame(company, reused);
        assertEquals("T100", hire.currentTile);
        assertEquals("T999", company.currentTile);
        assertTrue(company.units.contains(hire.entity));
    }

    @Test public void stageThreeCountsApprovedPreAlliancePartnerBuildHours() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEPlayerBuild build = manualBuild(data, "rohan");
        UUID contributor = UUID.randomUUID();
        KOMEBuildContribution approved = new KOMEBuildContribution();
        approved.id = "H1"; approved.contributorUuid = contributor; approved.contributorFaction = "gondor";
        approved.centiHours = 1000L;
        approved.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(approved);
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        alliance.setFactionStage("gondor", 2, "test", 0L, 100L);
        assertTrue(KOMEAllianceProgressionService.fixedMilestoneComplete(data, alliance, "gondor", 3));
    }

    @Test public void pendingBuildHoursDoNotCountForStageThree() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEPlayerBuild build = manualBuild(data, "rohan");
        KOMEBuildContribution pending = new KOMEBuildContribution();
        pending.id = "H1"; pending.contributorFaction = "gondor"; pending.centiHours = 1000L;
        build.contributions.add(pending);
        assertEquals(java.math.BigInteger.valueOf(0L), KOMEBuildService.approvedCentiHoursForPartner(data, "gondor", "rohan"));
    }

    @Test public void deletedHoursRemoveUnclaimedStageThreeProgress() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEPlayerBuild build = manualBuild(data, "rohan");
        KOMEBuildContribution approved = approved("gondor", 20);
        build.contributions.add(approved);
        assertEquals(java.math.BigInteger.valueOf(1000L), KOMEBuildService.approvedCentiHoursForPartner(data, "gondor", "rohan"));
        build.active = false;
        assertEquals(java.math.BigInteger.valueOf(0L), KOMEBuildService.approvedCentiHoursForPartner(data, "gondor", "rohan"));
    }

    @Test public void removedHoursRemoveUnclaimedStageThreeProgress() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEPlayerBuild build = manualBuild(data, "rohan");
        KOMEBuildContribution approved = approved("gondor", 20);
        build.contributions.add(approved);
        assertEquals(java.math.BigInteger.valueOf(1000L), KOMEBuildService.approvedCentiHoursForPartner(data, "gondor", "rohan"));
        assertTrue(KOMEBuildService.removeApprovedContribution(data, build, approved.id,
            build.managerUuid, build.managerName, "removed", 50L).allowed);
        assertEquals(java.math.BigInteger.valueOf(0L), KOMEBuildService.approvedCentiHoursForPartner(data, "gondor", "rohan"));
    }

    @Test public void deletedHoursCannotPrecompleteStageThreeAfterAllianceBreakAndReformation() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEPlayerBuild build = manualBuild(data, "rohan");
        build.contributions.add(approved("gondor", 20));
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        alliance.setFactionStage("gondor", 3, "test", 0L, 100L);
        alliance.clearAllTracks("break", 0L);
        build.active = false;
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        alliance.setFactionStage("gondor", 2, "test", 0L, 200L);
        assertFalse(KOMEAllianceProgressionService.fixedMilestoneComplete(
            data, alliance, "gondor", 3));
    }

    @Test public void claimedStageThreeDoesNotDowngradeAfterBuildDeletion() {
        KOMEAlliance alliance = activeAlliance("gondor", "rohan");
        alliance.setFactionStage("gondor", 3, "test", 0L, 100L);
        assertEquals(3, alliance.getFactionStage("gondor"));
        assertEquals(100L, alliance.getStageProgress("gondor").claimedAtMillis[3]);
    }

    @Test public void stageFourRequiresNewDefensiveWarDeploymentAfterStageThree() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        alliance.setFactionStage("gondor", 3, "test", 0L, 100L);
        KOMEWar war = KOMEWarService.createWar(data, "mordor", "rohan", "Defense", "test", 200L);
        war.addFaction(2, "gondor");
        war.recordMembership("gondor", 2, "MANUAL", "", "test", 210L);
        KOMEArmyCompany company = company(data, "C1", "gondor", "T100");
        data.armyCompanies.put(company.id, company);
        assertTrue(KOMEAllianceProgressionService.scanQualifyingWarDeployments(data, 220L));
        assertEquals(war.id, alliance.getStageProgress("gondor").qualifyingWarId);
    }

    @Test public void stageFourRejectsEmptyCompanyAndOldMembership() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEAlliance alliance = activeAlliance("gondor", "rohan");
        KOMEWar war = KOMEWarService.createWar(data, "mordor", "rohan", "Old Defense", "test", 20L);
        war.addFaction(2, "gondor");
        war.recordMembership("gondor", 2, "MANUAL", "", "test", 30L);
        alliance.setFactionStage("gondor", 3, "test", 0L, 100L);
        KOMEArmyCompany empty = new KOMEArmyCompany();
        empty.id = "EMPTY"; empty.faction = "gondor"; empty.currentTile = "T100";
        data.armyCompanies.put(empty.id, empty);
        assertFalse(KOMEAllianceProgressionService.scanQualifyingWarDeployments(data, 120L));
    }

    @Test public void stageFourRequiresMembershipStrictlyAfterStageThreeClaim() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEAlliance alliance = activeAlliance("gondor", "rohan");
        alliance.setFactionStage("gondor", 3, "test", 0L, 100L);
        KOMEWar war = KOMEWarService.createWar(data, "mordor", "rohan", "Defense", "test", 90L);
        war.addFaction(2, "gondor");
        war.recordMembership("gondor", 2, "MANUAL", "", "test", 100L);
        KOMEArmyCompany company = company(data, "C1", "gondor", "T100");
        assertFalse(KOMEAllianceProgressionService.recordQualifyingWarDeployment(
            data, alliance, "gondor", "rohan", company, war, "T100", 110L));
    }

    @Test public void stageFourDeploymentCountsOnlyOnce() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEAlliance alliance = data.getAlliance("gondor", "rohan", true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        alliance.setFactionStage("gondor", 3, "test", 0L, 100L);
        KOMEWar war = KOMEWarService.createWar(data, "mordor", "rohan", "Defense", "test", 150L);
        war.addFaction(2, "gondor");
        war.recordMembership("gondor", 2, "MANUAL", "", "test", 160L);
        KOMEArmyCompany company = company(data, "C1", "gondor", "T100");
        data.armyCompanies.put(company.id, company);
        assertTrue(KOMEAllianceProgressionService.recordQualifyingWarDeployment(
            data, alliance, "gondor", "rohan", company, war, "T100", 170L));
        long first = alliance.getStageProgress("gondor").qualifyingDeploymentAtMillis;
        assertFalse(KOMEAllianceProgressionService.scanQualifyingWarDeployments(data, 180L));
        assertEquals(first, alliance.getStageProgress("gondor").qualifyingDeploymentAtMillis);
    }

    @Test public void stageFourRejectsCompanyWithoutARecordedLivingUnit() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEAlliance alliance = activeAlliance("gondor", "rohan");
        alliance.setFactionStage("gondor", 3, "test", 0L, 100L);
        KOMEWar war = KOMEWarService.createWar(data, "mordor", "rohan", "Defense", "test", 150L);
        war.addFaction(2, "gondor");
        war.recordMembership("gondor", 2, "MANUAL", "", "test", 160L);
        KOMEArmyCompany stale = new KOMEArmyCompany();
        stale.id = "STALE";
        stale.owner = UUID.randomUUID();
        stale.faction = "gondor";
        stale.currentTile = "T100";
        stale.units.add(UUID.randomUUID());
        setFaction(data, stale.owner, "gondor");
        assertFalse(KOMEAllianceProgressionService.recordQualifyingWarDeployment(
            data, alliance, "gondor", "rohan", stale, war, "T100", 170L));
    }

    @Test public void stageFourRejectsPartnerDefaultLandNotCurrentlyControlledByPartner() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "mordor");
        KOMEAlliance alliance = activeAlliance("gondor", "rohan");
        alliance.setFactionStage("gondor", 3, "test", 0L, 100L);
        KOMEWar war = KOMEWarService.createWar(data, "mordor", "rohan", "Defense", "test", 200L);
        war.addFaction(2, "gondor");
        war.recordMembership("gondor", 2, "MANUAL", "", "test", 210L);
        KOMEArmyCompany company = company(data, "C1", "gondor", "T100");
        assertFalse(KOMEAllianceProgressionService.recordQualifyingWarDeployment(
            data, alliance, "gondor", "rohan", company, war, "T100", 220L));
    }

    private static KOMEWorldData dataWithTile(String tileId, String defaultFaction, String controller) {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEConquestTile tile = new KOMEConquestTile(tileId);
        tile.defaultRulingFaction = defaultFaction;
        tile.claim(controller, 0L);
        data.conquestTiles.put(tile.id, tile);
        return data;
    }

    private static KOMEPlayerBuild build(KOMEWorldData data, String populationFaction, int off, int def) {
        if (off <= 0 && def <= 0) return manualBuild(data, populationFaction);
        UUID builder = UUID.randomUUID();
        KOMEPlayerBuild build = KOMEBuildService.create(data, "Build", "T100", 0, 0, 64, 0,
            builder, "Builder", "gondor", populationFaction,
            off > 0 ? KOMEBuildType.NORMAL : KOMEBuildType.DEFENSIVE, (off > 0 ? (long) off : def) * 50L, 10L);
        assertTrue(KOMEBuildService.decideSubmission(data, build, build.contributions.get(0).id,
            builder, "Builder", true, "Fixture approval", 11L).allowed);
        return build;
    }

    private static KOMEPlayerBuild manualBuild(KOMEWorldData data, String populationFaction) {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = data.nextBuildId();
        build.displayName = "Build " + build.id;
        build.type = KOMEBuildType.NORMAL;
        build.tileId = "T100";
        build.populationFaction = populationFaction;
        build.builderUuid = build.managerUuid = UUID.randomUUID();
        build.builderName = build.managerName = "Builder";
        build.originalBuilderFaction = "gondor";
        build.createdAtMillis = 1L;
        build.updatedAtMillis = 1L;
        data.builds.put(build.id, build);
        return build;
    }

    private static void setFaction(KOMEWorldData data, UUID owner, String faction) {
        KOMEPlayerProgression progression = new KOMEPlayerProgression();
        progression.setPledgedLord("lord", "Lord", faction);
        data.progressions.put(owner, progression);
    }

    private static KOMEHiredUnitRecord unit(UUID owner, String faction, String tile, int cost) {
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord();
        unit.entity = UUID.randomUUID();
        unit.owner = owner;
        unit.unitFaction = faction;
        unit.sourceFaction = faction;
        unit.sourceTileId = tile;
        unit.currentTile = tile;
        unit.cost = cost;
        unit.type = KOMEPopulationType.OFFENSIVE;
        return unit;
    }

    private static KOMEBuildContribution approved(String faction, int halfHours) {
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = "H1";
        contribution.contributorFaction = faction;
        contribution.centiHours = Math.multiplyExact((long) halfHours, 50L);
        contribution.status = KOMEBuildContribution.APPROVED;
        return contribution;
    }

    private static KOMEAlliance activeAlliance(String first, String second) {
        KOMEAlliance alliance = new KOMEAlliance(first, second);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        return alliance;
    }

    private static void establishCanonicalDiplomacy(
            KOMEWorldData data,
            String first,
            String second,
            KOMEDiplomacyRelation relation) {
        KOMEDiplomacyRecord record = new KOMEDiplomacyRecord(first, second);
        record.relation = relation;
        record.updatedAt = 1L;
        data.canonicalDiplomacyRecords.put(record.key(), record);
    }
    private static KOMEAlliance establishSharedStage(KOMEWorldData data, String first, String second, int stage) {
        KOMEAlliance alliance = data.getAlliance(first, second, true);
        alliance.requestTrack(KOMEAlliance.CIVIL, "test", 0L, false);
        alliance.setFactionStage(first, stage, "test", 0L, 1L);
        alliance.setFactionStage(second, stage, "test", 0L, 1L);
        return alliance;
    }

    private static KOMEArmyCompany company(KOMEWorldData data, String id, String faction, String tile) {
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = id;
        company.owner = UUID.randomUUID();
        company.faction = faction;
        company.currentTile = tile;
        setFaction(data, company.owner, faction);
        KOMEHiredUnitRecord record = unit(company.owner, faction, tile, 25);
        record.companyId = id;
        company.units.add(record.entity);
        data.hiredUnits.put(record.entity, record);
        return company;
    }
}
