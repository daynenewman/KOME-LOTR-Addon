package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/** Cross-system regression coverage for Builds, split population, companies, and stage milestones. */
public class KOMERedesignSystemsTest {
    @Test public void invalidQuarterHourIncrementsAreRejected() {
        assertFalse(KOMEHalfHourService.isValidHours(0.1D));
        assertFalse(KOMEHalfHourService.isValidHours(0.25D));
        assertFalse(KOMEHalfHourService.isValidHours(0.75D));
    }

    @Test public void typedWholeAndHalfHoursNormalizeToCanonicalHalfHours() {
        assertEquals(0, KOMEHalfHourService.parseHalfHours("0"));
        assertEquals(1, KOMEHalfHourService.parseHalfHours(".5"));
        assertEquals(1, KOMEHalfHourService.parseHalfHours("0.5"));
        assertEquals(2, KOMEHalfHourService.parseHalfHours("1"));
        assertEquals(2, KOMEHalfHourService.parseHalfHours("1.0"));
        assertEquals(3, KOMEHalfHourService.parseHalfHours(" 1.5 "));
    }

    @Test public void typedInvalidHoursAreRejected() {
        String[] invalid = {"", " ", "-0.5", "0.1", "0.25", "0.75", "NaN", "Infinity", "1e0", "one", "1..5"};
        for (String value : invalid) {
            try {
                KOMEHalfHourService.parseHalfHours(value);
                fail("Expected invalid Build hours: " + value);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("whole/half-hour"));
            }
        }
    }

    @Test public void halfHourButtonsClampAtSupportedBounds() {
        assertEquals(0, KOMEHalfHourService.adjustHalfHours(0, -1));
        assertEquals(1, KOMEHalfHourService.adjustHalfHours(0, 1));
        assertEquals(Integer.MAX_VALUE,
            KOMEHalfHourService.adjustHalfHours(Integer.MAX_VALUE, 1));
    }

    @Test public void populationGraphSegmentsPreservePhysicalCapacity() {
        KOMEPopulationGraph.Segments segments = KOMEPopulationGraph.segments(100, 50, 20);
        assertEquals(100, segments.physical);
        assertEquals(20, segments.used);
        assertEquals(30, segments.available);
        assertEquals(50, segments.inaccessible);
    }

    @Test public void populationGraphHandlesControllerForeignAndZeroPools() {
        assertEquals("100% Controller-Owned Access", KOMEPopulationGraph.accessLabel(true, 100, 100));
        assertEquals("50% Captured Access", KOMEPopulationGraph.accessLabel(false, 100, 50));
        assertEquals("0% Owner Access While Occupied", KOMEPopulationGraph.accessLabel(false, 1, 0));
        KOMEPopulationGraph.Segments zero = KOMEPopulationGraph.segments(0, 0, 0);
        assertEquals(0, zero.used + zero.available + zero.inaccessible);
    }

    @Test public void populationGraphClampsInvalidAndOverflowingPresentationTotals() {
        KOMEPopulationGraph.Segments segments = KOMEPopulationGraph.segments(10, 50, 99);
        assertEquals(10, segments.used);
        assertEquals(0, segments.available);
        assertEquals(0, segments.inaccessible);
        assertEquals(Integer.MAX_VALUE,
            KOMEPopulationGraph.saturatingAdd(Integer.MAX_VALUE - 2, 10));
    }

    @Test public void ownControlledTileAllowsBuildPlacement() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        assertTrue(KOMEBuildService.canPlace(data, "gondor", "T100", "gondor").allowed);
    }

    @Test public void alliedControlledTileAllowsBuildPlacement() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        establishCanonicalDiplomacy(data, "gondor", "rohan", KOMEDiplomacyRelation.ALLIES);
        assertTrue(KOMEBuildService.canPlace(data, "gondor", "T100", "gondor").allowed);
    }

    @Test public void friendlyControlledTileAllowsBuildPlacement() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        establishCanonicalDiplomacy(data, "gondor", "rohan", KOMEDiplomacyRelation.FRIENDS);
        assertTrue(KOMEBuildService.canPlace(data, "gondor", "T100", "gondor").allowed);
    }

    @Test public void enemyControlledTileRejectsBuildPlacementEvenInDefaultHomeland() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "mordor");
        assertFalse(KOMEBuildService.canPlace(data, "gondor", "T100", "gondor").allowed);
    }

    @Test public void foreignPopulationOwnerMustBeSafeWithPlayerAndController() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        establishCanonicalDiplomacy(data, "gondor", "rohan", KOMEDiplomacyRelation.FRIENDS);
        assertTrue(KOMEBuildService.canPlace(data, "gondor", "T100", "rohan").allowed);
        assertFalse(KOMEBuildService.canPlace(data, "gondor", "T100", "mordor").allowed);
    }

    @Test public void foreignPopulationOwnerCannotExploitAnUnsafeThirdFactionRelationship() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        establishCanonicalDiplomacy(data, "gondor", "rohan", KOMEDiplomacyRelation.FRIENDS);
        establishCanonicalDiplomacy(data, "gondor", "bree", KOMEDiplomacyRelation.FRIENDS);
        assertFalse(KOMEBuildService.canPlace(data, "gondor", "T100", "bree").allowed);
        establishCanonicalDiplomacy(data, "rohan", "bree", KOMEDiplomacyRelation.FRIENDS);
        assertTrue(KOMEBuildService.canPlace(data, "gondor", "T100", "bree").allowed);
    }

    @Test public void originalManagerContributionApprovesImmediately() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        UUID builder = UUID.randomUUID();
        KOMEPlayerBuild build = KOMEBuildService.create(data, "Citadel", "T100", 0, 0, 64, 0,
            builder, "Builder", "gondor", "gondor", KOMEBuildType.NORMAL, 2, 10L);
        assertEquals(2, build.approvedHalfHours());
        assertTrue(build.isNormal());
        assertEquals(KOMEBuildContribution.APPROVED, build.contributions.get(0).status);
    }

    @Test public void otherPlayerContributionRemainsPending() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 0, 0);
        KOMEBuildContribution contribution = KOMEBuildService.addSubmission(data, build, UUID.randomUUID(),
            "Helper", "rohan", 2, false, 20L);
        assertTrue(contribution.isPending());
        assertEquals(0, build.approvedHalfHours());
    }

    @Test public void managerApprovalAppliesPopulationAndCredit() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 0, 0);
        UUID helper = UUID.randomUUID();
        KOMEBuildContribution contribution = KOMEBuildService.addSubmission(
            data, build, helper, "Helper", "rohan", 2, false, 20L);
        assertTrue(KOMEBuildService.decideSubmission(data, build, contribution.id,
            build.managerUuid, build.managerName, true, "approved", 30L).allowed);
        assertEquals(2, build.approvedHalfHours());
        assertEquals(Integer.valueOf(2), build.activeHalfHoursByPlayer().get(helper));
        assertEquals(Integer.valueOf(2), build.activeHalfHoursByFaction().get("rohan"));
    }

    @Test public void rejectionNeverAppliesPopulation() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 0, 0);
        KOMEBuildContribution contribution = KOMEBuildService.addSubmission(
            data, build, UUID.randomUUID(), "Helper", "rohan", 2, false, 20L);
        assertTrue(KOMEBuildService.decideSubmission(data, build, contribution.id,
            build.managerUuid, build.managerName, false, "rejected", 30L).allowed);
        assertEquals(0, build.approvedHalfHours());
    }

    @Test public void canonicalBuildHasOneHoursStream() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 4, 0);
        assertEquals(4, build.approvedHalfHours());
        assertEquals(0, build.approvedDefensiveHalfHours());
    }

    @Test public void managerRemovalReversesActiveCredit() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 2, 0);
        String id = build.contributions.get(0).id;
        assertTrue(KOMEBuildService.removeApprovedContribution(data, build, id,
            build.managerUuid, build.managerName, "remove", 40L).allowed);
        assertEquals(0, build.approvedHalfHours());
        assertTrue(build.activeHalfHoursByFaction().isEmpty());
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

    @Test public void buildDeletionDoesNotDependOnFormerAllocationCapacity() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 4, 0);
        KOMEPlayerTilePopulationAllocation allocation = data.getOrCreateAllocation(
            "T100", "gondor", UUID.randomUUID(), "Player");
        allocation.offensiveAllocated = 20;
        KOMEBuildService.Decision decision = KOMEBuildService.deleteBuild(data, build,
            build.managerUuid, build.managerName, false, "delete", 40L);
        assertTrue(decision.allowed);
        assertFalse(build.active);
    }

    @Test public void nativeCapacityMayCoverBuildDeletionWithoutOrphaningAllocation() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMETilePopulation pool = data.getOrCreateTilePopulationPool("T100", "gondor");
        pool.nativeOffensiveTotal = pool.offensiveTotal = 50;
        pool.nativeBaselineInitialized = true;
        KOMEPlayerBuild build = build(data, "gondor", 4, 0);
        KOMEPlayerTilePopulationAllocation allocation = data.getOrCreateAllocation(
            "T100", "gondor", UUID.randomUUID(), "Player");
        allocation.offensiveAllocated = 20;
        assertTrue(KOMEBuildService.deleteBuild(data, build,
            build.managerUuid, build.managerName, false, "delete", 40L).allowed);
        assertEquals(50, pool.offensiveTotal);
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
            "Helper", "rohan", 1, false, 20L);
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
            "Helper", "rohan", 1, false, 20L);
        assertTrue(KOMEBuildService.deleteBuild(data, build, build.managerUuid,
            build.managerName, false, "delete", 40L).allowed);
        assertTrue(approved.isRemoved());
        assertEquals(KOMEBuildContribution.REJECTED, pending.status);
        assertTrue(build.activeHalfHoursByPlayer().isEmpty());
        assertTrue(build.activeHalfHoursByFaction().isEmpty());
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
        assertEquals(3, loaded.approvedHalfHours());
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
        assertEquals(7, saved.getInteger("HalfHours"));
        assertFalse(saved.hasKey("OffensiveHalfHours"));
        assertFalse(saved.hasKey("DefensiveHalfHours"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missingBuildTypeIsRejectedRatherThanAssumedNormal() {
        new KOMEPlayerBuild().readFromNBT(new NBTTagCompound());
    }

    @Test public void worldLoadDiscardsStaleBuildWithoutType() {
        NBTTagCompound saved = new NBTTagCompound();
        NBTTagList builds = new NBTTagList();
        NBTTagCompound stale = new NBTTagCompound();
        stale.setString("Id", "B-stale");
        stale.setString("TileId", "T100");
        stale.setString("PopulationFaction", "gondor");
        builds.appendTag(stale);
        saved.setTag("Builds", builds);
        KOMEWorldData data = new KOMEWorldData("test");
        data.readFromNBT(saved);
        assertNull(data.getBuild("B-stale"));
    }

    @Test public void normalRateIsExactAndDefensiveRateIsZero() {
        KOMEPlayerBuild normal = new KOMEPlayerBuild();
        normal.type = KOMEBuildType.NORMAL;
        normal.contributions.add(approved("gondor", 20));
        assertArrayEquals(new long[] {20L, 20L}, normal.originalPopulationRate(10));
        assertArrayEquals(new long[] {20L, 4294967294L}, normal.originalPopulationRate(Integer.MAX_VALUE));
        KOMEPlayerBuild defensive = new KOMEPlayerBuild();
        defensive.type = KOMEBuildType.DEFENSIVE;
        defensive.contributions.add(approved("gondor", 20));
        assertArrayEquals(new long[] {0L, 1L}, defensive.originalPopulationRate(10));
        assertEquals(20, defensive.approvedDefensiveHalfHours());
    }

    @Test public void downstreamBuildQueriesSeparateNormalAndDefensive() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild normal = KOMEBuildService.create(data, "Normal", "T100", 0, 0, 64, 0,
            UUID.randomUUID(), "Builder", "gondor", "gondor", KOMEBuildType.NORMAL, 8, 10L);
        KOMEPlayerBuild defensive = KOMEBuildService.create(data, "Defensive", "T100", 0, 0, 64, 0,
            UUID.randomUUID(), "Builder", "gondor", "gondor", KOMEBuildType.DEFENSIVE, 6, 10L);
        assertEquals(java.util.Collections.singletonList(normal), KOMEBuildService.activeNormalBuilds(data));
        assertEquals(java.util.Collections.singletonList(defensive), KOMEBuildService.activeDefensiveBuilds(data));
        assertArrayEquals(new long[] {8L, 20L}, normal.originalPopulationRate(10));
        assertArrayEquals(new long[] {0L, 1L}, defensive.originalPopulationRate(10));
    }

    @Test public void buildMutationsNeverChangeFactionAvailablePopulation() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        data.grantFactionPopulation("gondor", 91);
        UUID manager = UUID.randomUUID();
        KOMEPlayerBuild build = KOMEBuildService.create(data, "Build", "T100", 0, 0, 64, 0,
            manager, "Manager", "gondor", "gondor", KOMEBuildType.NORMAL, 2, 10L);
        KOMEBuildContribution pending = KOMEBuildService.addSubmission(data, build, UUID.randomUUID(),
            "Helper", "gondor", 3, false, 20L);
        assertTrue(KOMEBuildService.decideSubmission(data, build, pending.id, manager, "Manager", true,
            "approved", 30L).allowed);
        assertTrue(KOMEBuildService.removeApprovedContribution(data, build, pending.id, manager, "Manager",
            "removed", 40L).allowed);
        assertTrue(KOMEBuildService.deleteBuild(data, build, manager, "Manager", false, "deleted", 50L).allowed);
        assertEquals(91, KOMEPopulationService.getAvailablePopulation(data, "gondor"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void canonicalCreationRejectsMissingType() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEBuildService.create(data, "Build", "T100", 0, 0, 64, 0, UUID.randomUUID(), "Builder",
            "gondor", "gondor", null, 1, 10L);
    }

    @Test public void ownPopulationPoolIsUsableAtOneHundredPercent() {
        KOMETilePopulation pool = pool("T100", "gondor", 100, 20);
        assertEquals(100, pool.getEffectiveTotal(KOMEPopulationType.OFFENSIVE, "gondor"));
        assertEquals(80, pool.getEffectiveAvailable(KOMEPopulationType.OFFENSIVE, "gondor"));
    }

    @Test public void foreignPopulationPoolIsUsableAtFiftyPercent() {
        KOMETilePopulation pool = pool("T100", "rohan", 100, 20);
        assertEquals(50, pool.getEffectiveTotal(KOMEPopulationType.OFFENSIVE, "gondor"));
        assertEquals(30, pool.getEffectiveAvailable(KOMEPopulationType.OFFENSIVE, "gondor"));
    }

    @Test public void populationOwnerHasNoUseWhileNotController() {
        KOMETilePopulation pool = pool("T100", "gondor", 100, 0);
        assertEquals(50, pool.getEffectiveTotal(KOMEPopulationType.OFFENSIVE, "mordor"));
        assertNotEquals(100, pool.getEffectiveTotal(KOMEPopulationType.OFFENSIVE, "mordor"));
    }

    @Test public void reclaimRestoresFullUseWithoutChangingOwnership() {
        KOMETilePopulation pool = pool("T100", "gondor", 100, 0);
        assertEquals(50, pool.getEffectiveTotal(KOMEPopulationType.OFFENSIVE, "mordor"));
        assertEquals(100, pool.getEffectiveTotal(KOMEPopulationType.OFFENSIVE, "gondor"));
        assertEquals("gondor", pool.sourceFaction);
    }

    @Test public void seasonalResetPreservesForeignPoolProvenance() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "mordor");
        KOMETilePopulation gondor = data.getOrCreateTilePopulationPool("T100", "gondor");
        gondor.nativeOffensiveTotal = gondor.offensiveTotal = 50;
        gondor.nativeBaselineInitialized = true;
        KOMETilePopulation mordor = data.getOrCreateTilePopulationPool("T100", "mordor");
        mordor.nativeOffensiveTotal = mordor.offensiveTotal = 40;
        mordor.nativeBaselineInitialized = true;
        data.resetConquestOwnershipToDefaults(20L);
        assertNotNull(data.getTilePopulationPool("T100", "gondor"));
        assertNotNull(data.getTilePopulationPool("T100", "mordor"));
        assertEquals(50, gondor.getEffectiveTotal(KOMEPopulationType.OFFENSIVE, "gondor"));
        assertEquals(20, mordor.getEffectiveTotal(KOMEPopulationType.OFFENSIVE, "gondor"));
    }

    @Test public void buildHoursDoNotMutateLegacyTilePopulation() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMETilePopulation gondor = data.getOrCreateTilePopulationPool("T100", "gondor");
        gondor.nativeOffensiveTotal = gondor.offensiveTotal = 50;
        gondor.nativeBaselineInitialized = true;
        build(data, "gondor", 10, 0);
        KOMEPlayerBuild rohanBuild = manualBuild(data, "rohan");
        rohanBuild.contributions.add(approved("gondor", 8));
        assertEquals(50, data.getNativePopulationTotal("T100", "gondor", KOMEPopulationType.OFFENSIVE));
        assertEquals(50, data.getTilePopulationPool("T100", "gondor").offensiveTotal);
        assertNull(data.getTilePopulationPool("T100", "rohan"));
        assertEquals(50, data.getEffectiveUsablePopulation("T100", "gondor", KOMEPopulationType.OFFENSIVE));
    }

    @Test public void hiredUnitsHaveNoBuildFundingReference() {
        KOMEWorldData data = dataWithTile("T100", "gondor", "gondor");
        KOMEPlayerBuild build = build(data, "gondor", 10, 0);
        assertEquals(10, build.approvedHalfHours());
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
        approved.halfHours = 20;
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
        pending.id = "H1"; pending.contributorFaction = "gondor"; pending.halfHours = 20;
        build.contributions.add(pending);
        assertEquals(0, KOMEBuildService.approvedHalfHoursForPartner(data, "gondor", "rohan"));
    }

    @Test public void deletedHoursRemoveUnclaimedStageThreeProgress() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEPlayerBuild build = manualBuild(data, "rohan");
        KOMEBuildContribution approved = approved("gondor", 20);
        build.contributions.add(approved);
        assertEquals(20, KOMEBuildService.approvedHalfHoursForPartner(data, "gondor", "rohan"));
        build.active = false;
        assertEquals(0, KOMEBuildService.approvedHalfHoursForPartner(data, "gondor", "rohan"));
    }

    @Test public void removedHoursRemoveUnclaimedStageThreeProgress() {
        KOMEWorldData data = dataWithTile("T100", "rohan", "rohan");
        KOMEPlayerBuild build = manualBuild(data, "rohan");
        KOMEBuildContribution approved = approved("gondor", 20);
        build.contributions.add(approved);
        assertEquals(20, KOMEBuildService.approvedHalfHoursForPartner(data, "gondor", "rohan"));
        assertTrue(KOMEBuildService.removeApprovedContribution(data, build, approved.id,
            build.managerUuid, build.managerName, "removed", 50L).allowed);
        assertEquals(0, KOMEBuildService.approvedHalfHoursForPartner(data, "gondor", "rohan"));
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
        return KOMEBuildService.create(data, "Build", "T100", 0, 0, 64, 0,
            builder, "Builder", "gondor", populationFaction,
            off > 0 ? KOMEBuildType.NORMAL : KOMEBuildType.DEFENSIVE, off > 0 ? off : def, 10L);
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

    private static KOMETilePopulation pool(String tile, String owner, int total, int used) {
        KOMETilePopulation pool = new KOMETilePopulation(tile, owner);
        pool.offensiveTotal = total;
        pool.offensiveUsed = used;
        return pool;
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
        contribution.halfHours = halfHours;
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
