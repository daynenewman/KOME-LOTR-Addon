package kome.common.data;

import kome.common.command.KOMECommandBuild;
import kome.common.config.KOMEConfigRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.Assert.*;

/** Checkpoint D production service/persistence regressions, without a client or live clock. */
public class KOMEPreciseBuildTest {
    @org.junit.Rule public final KOMETileTestResources tileGeometry = new KOMETileTestResources();
    @Test public void ordinaryDecimalInputAndFormattingAreExactAtEveryBoundary() {
        String[] inputs = {"0", "0.01", "0.50", "1", "1.00", "10.25", "24.50", "92233720368547758.07"};
        long[] expected = {0L, 1L, 50L, 100L, 100L, 1025L, 2450L, Long.MAX_VALUE};
        for (int i = 0; i < inputs.length; i++) {
            assertEquals(expected[i], KOMEBuildTime.parseHours(inputs[i]));
            String display = KOMEBuildTime.formatHours(expected[i]);
            assertTrue(display.matches("[0-9]+\\.[0-9]{2}"));
            assertEquals(expected[i], KOMEBuildTime.parseHours(display));
        }
        assertEquals("0.01", KOMEBuildTime.formatHours(1L));
        assertEquals("24.50", KOMEBuildTime.formatHours(2450L));
        assertEquals("92233720368547758.07", KOMEBuildTime.formatHours(Long.MAX_VALUE));
    }

    @Test public void malformedPreciseAndOverflowingInputIsRejected() {
        for (String value : new String[] {null, "", " ", "-1", "-0.00", "+1", "NaN", "Infinity",
                "-Infinity", "1e3", "0x1", "1,00", ".5", "1.", "1.001", "1.000", "92233720368547758.08"}) {
            rejects(() -> KOMEBuildTime.parseHours(value));
        }
        rejects(() -> KOMEBuildTime.add(Long.MAX_VALUE, 1L));
        rejects(() -> KOMEBuildTime.add(0L, -1L));
    }

    @Test public void normalAndDefensiveReviewLifecycleIsExactIdempotentAndAudited() {
        for (KOMEBuildType type : KOMEBuildType.values()) {
            KOMEWorldData data = world();
            KOMEPlayerBuild build = create(data, type, 1025L);
            KOMEBuildContribution contribution = build.contributions.get(0);
            assertEquals(KOMEBuildContribution.APPROVED, contribution.status);
            assertEquals(1025L, build.approvedCentiHours());
            assertEquals(type == KOMEBuildType.NORMAL ? 1_025_000L : 0L, rate(data, "gondor"));
            assertTrue(build.auditHistory().get(0).contains("|REGISTER|"));
            assertTrue(build.auditHistory().get(1).contains("|SUBMIT|"));
            assertTrue(build.auditHistory().get(1).contains("hours=10.25"));
            assertTrue(build.auditHistory().get(2).contains("|APPROVE|"));
            assertTrue(build.auditHistory().get(2).contains("Manager contribution approved immediately"));
            assertTrue(build.auditHistory().get(2).contains("approvedCentiHoursDelta=1025"));
            assertEquals(type == KOMEBuildType.DEFENSIVE ? 1025L : 0L, build.approvedDefensiveCentiHours());
            String afterApproval = saved(data).toString();
            data.setDirty(false);
            approve(data, build, contribution);
            assertEquals(afterApproval, saved(data).toString());
            assertFalse(data.isDirty());
            assertTrue(KOMEBuildService.adjustSubmission(data, build, contribution.id, null, "Admin",
                true, "24.50", "Reviewed measured time", 30L).allowed);
            assertEquals(2450L, build.approvedCentiHours());
            assertEquals("Admin", contribution.decidedByName);
            assertTrue(last(build).contains("priorHours=10.25"));
            assertTrue(last(build).contains("hours=24.50"));
            String adjusted = saved(data).toString();
            assertTrue(KOMEBuildService.adjustSubmission(data, build, contribution.id, null, "Admin",
                true, "24.50", "Same", 40L).allowed);
            assertEquals(adjusted, saved(data).toString());
            assertTrue(KOMEBuildService.decideSubmission(data, build, contribution.id, build.managerUuid,
                "Builder", false, "Rejected after review", 50L).allowed);
            assertEquals(0L, build.approvedCentiHours());
            assertEquals(0L, rate(data, "gondor"));
            assertEquals(2450L, contribution.centiHours);
            String rejected = saved(data).toString();
            assertTrue(KOMEBuildService.decideSubmission(data, build, contribution.id, build.managerUuid,
                "Builder", false, "Repeated", 60L).allowed);
            assertEquals(rejected, saved(data).toString());
            assertFalse(KOMEBuildService.decideSubmission(data, build, contribution.id, build.managerUuid,
                "Builder", true, "Illegal reapproval", 70L).allowed);
            assertEquals(rejected, saved(data).toString());
            assertEquals(type, build.type);
            assertEquals("gondor", build.originalBuilderFaction);
            assertEquals("gondor", build.populationFaction);
            assertEquals("T100", build.tileId);
            assertEquals(64D, build.y, 0D);
            assertEquals("Hall", build.displayName);
            assertEquals("Hall", build.markerLabel);
            assertEquals(10L, build.createdAtMillis);
            assertEquals(10L, contribution.submittedAtMillis);
            assertTrue(data.centralAudit.stream().anyMatch(e -> "ADJUST".equals(e.action)));
        }
    }

    @Test public void invalidReviewAndOverflowLeaveAllGameplayAndAuditUnchanged() {
        KOMEWorldData data = world(); KOMEPlayerBuild build = create(data, KOMEBuildType.NORMAL, Long.MAX_VALUE);
        KOMEBuildContribution pending = KOMEBuildService.addSubmission(data, build, UUID.randomUUID(),
            "Helper", "gondor", 1L, false, 30L);
        data.setDirty(false); String before = saved(data).toString();
        rejects(() -> KOMEBuildService.addSubmission(data, build, build.managerUuid, "Builder",
            "gondor", 1L, true, 40L));
        assertFalse(KOMEBuildService.decideSubmission(data, build, pending.id, build.managerUuid,
            "Builder", true, "Overflow", 40L).allowed);
        assertFalse(KOMEBuildService.adjustSubmission(data, build, pending.id, null, "Admin",
            true, "0.01", "Overflow", 40L).allowed);
        assertFalse(KOMEBuildService.adjustSubmission(data, build, pending.id, null, "Admin",
            true, "1.001", "Invalid", 40L).allowed);
        assertFalse(KOMEBuildService.adjustSubmission(data, build, pending.id, build.managerUuid, "Builder",
            false, "0.00", "Unauthorized adjustment", 40L).allowed);
        assertFalse(KOMEBuildService.decideSubmission(data, build, pending.id, UUID.randomUUID(),
            "Other", true, "Unauthorized approval", 40L).allowed);
        rejects(() -> KOMEBuildService.addSubmission(data, build, build.managerUuid, "Builder",
            "gondor", -1L, true, 40L));
        assertEquals(before, saved(data).toString()); assertFalse(data.isDirty());
    }

    @Test public void managerAutoApprovalIsExactServerDerivedAuditedAndPersistent() {
        for (long amount : new long[] {1L, 25L, 1025L, 2450L, Long.MAX_VALUE}) {
            KOMEWorldData data = world(); KOMEPlayerBuild build = create(data, KOMEBuildType.NORMAL, amount);
            KOMEBuildContribution initial = build.contributions.get(0);
            assertTrue(initial.isApproved()); assertEquals(amount, build.approvedCentiHours());
            assertEquals(build.managerUuid, initial.decidedByUuid); assertEquals("Builder", initial.decidedByName);
            assertEquals("Manager contribution approved immediately", initial.decisionReason);
            assertEquals(3, build.auditHistory().size());
            assertTrue(build.auditHistory().get(0).contains("|REGISTER|"));
            assertTrue(build.auditHistory().get(1).contains("|SUBMIT|"));
            assertTrue(build.auditHistory().get(2).contains("|APPROVE|"));
            assertEquals(1L, data.centralAudit.stream().filter(e -> "APPROVE".equals(e.action)).count());
            KOMEWorldData loaded = new KOMEWorldData("reload"); loaded.readFromNBT(saved(data));
            KOMEBuildContribution restored = loaded.getBuild(build.id).contributions.get(0);
            assertTrue(restored.isApproved()); assertEquals(amount, restored.centiHours);
            assertEquals(initial.decidedByUuid, restored.decidedByUuid);
            assertEquals(initial.decidedByName, restored.decidedByName);
            assertEquals(initial.decisionReason, restored.decisionReason);
        }

        KOMEWorldData data = world(); KOMEPlayerBuild build = create(data, KOMEBuildType.NORMAL, 0L);
        KOMEBuildContribution manager = KOMEBuildService.addSubmission(data, build, build.managerUuid,
            "Builder", "gondor", 25L, true, 20L);
        assertTrue(manager.isApproved()); assertEquals(25L, build.approvedCentiHours());
        assertTrue(build.auditHistory().get(3).contains("|SUBMIT|"));
        assertTrue(build.auditHistory().get(4).contains("|APPROVE|"));
        assertTrue(build.auditHistory().get(4).contains("approvedCentiHoursDelta=25"));
        assertEquals(2L, data.centralAudit.stream().filter(e -> "APPROVE".equals(e.action)).count());
        assertTrue(data.centralAudit.stream().anyMatch(e -> "APPROVE".equals(e.action)
            && e.details.contains("contribution=" + manager.id)
            && e.details.contains("approvedCentiHoursDelta=25")));
        UUID nonManager = UUID.randomUUID();
        KOMEBuildContribution pending = KOMEBuildService.addSubmission(data, build, nonManager,
            "Helper", "gondor", 2450L, true, 30L);
        assertTrue(pending.isPending()); assertEquals(25L, build.approvedCentiHours());
        assertEquals(0L, rate(data, "gondor"));
        assertTrue(KOMEBuildService.decideSubmission(data, build, pending.id, build.managerUuid,
            "Builder", true, "Reviewed", 40L).allowed);
        assertEquals(2475L, build.approvedCentiHours()); assertEquals(0L, rate(data, "gondor"));
    }

    @Test public void failedRegistrationIsAtomicAndZeroSubmissionIsValid() {
        KOMEWorldData data = world(); data.setDirty(false); String before = saved(data).toString();
        rejects(() -> create(data, null, 1L));
        rejects(() -> create(data, KOMEBuildType.NORMAL, -1L));
        rejects(() -> KOMEBuildService.create(data, "Bad", "missing", 0, 0D, 64D, 0D,
            UUID.randomUUID(), "Builder", "gondor", "gondor", KOMEBuildType.NORMAL, 1L, 10L));
        assertEquals(before, saved(data).toString()); assertFalse(data.isDirty()); assertTrue(data.builds.isEmpty());
        KOMEPlayerBuild zero = create(data, KOMEBuildType.NORMAL, 0L);
        assertTrue(zero.contributions.get(0).isApproved()); assertEquals(0L, zero.approvedCentiHours());
        assertEquals(3, zero.auditHistory().size());
    }

    @Test public void administrativeMetadataRepairPreservesInactiveBuildSemantics() {
        KOMEWorldData data = world(); KOMEPlayerBuild build = create(data, KOMEBuildType.DEFENSIVE, 25L);
        approve(data, build, build.contributions.get(0));
        assertTrue(KOMEBuildService.deleteBuild(data, build, build.managerUuid, "Builder", false, "Deleted", 30L).allowed);
        UUID manager = UUID.randomUUID(); String before = saved(data).toString();
        assertFalse(KOMEBuildService.reassignManager(data, build, null, "Other", false, manager, "Manager", 40L).allowed);
        assertEquals(before, saved(data).toString());
        assertTrue(KOMEBuildService.reassignManager(data, build, null, "Admin", true, manager, "Manager", 40L).allowed);
        assertEquals(manager, build.managerUuid); assertFalse(build.active);
        assertEquals(KOMEBuildType.DEFENSIVE, build.type); assertEquals(25L, build.contributions.get(0).centiHours);
        assertTrue(last(build).contains("|MANAGER_REASSIGN|"));
    }

    @Test public void administrativeTotalRepairPreservesHistoryAndDoesNotDoubleCount() {
        KOMEWorldData data = world(); KOMEPlayerBuild build = create(data, KOMEBuildType.NORMAL, 1025L);
        KOMEBuildContribution original = build.contributions.get(0); approve(data, build, original);
        assertTrue(KOMEBuildService.setApprovedHours(data, build, null, "Admin", true, "24.50", 40L).allowed);
        assertEquals(2450L, build.approvedCentiHours()); assertEquals(1025L, original.centiHours);
        assertEquals(KOMEBuildContribution.REMOVED, original.status);
        assertEquals(2, build.contributions.size());
        String before = saved(data).toString();
        assertTrue(KOMEBuildService.setApprovedHours(data, build, null, "Admin", true, "24.50", 50L).allowed);
        assertFalse(KOMEBuildService.setApprovedHours(data, build, null, "Admin", true, "1.001", 50L).allowed);
        assertEquals(before, saved(data).toString());
        assertTrue(KOMEBuildService.setApprovedHours(data, build, null, "Admin", true, "0.00", 60L).allowed);
        assertEquals(0L, build.approvedCentiHours()); assertEquals(0L, rate(data, "gondor"));
    }

    @Test public void inactiveAndResubmittedBuildsCannotRetainStaleRates() {
        KOMEWorldData data = world(); KOMEPlayerBuild build = create(data, KOMEBuildType.NORMAL, 1025L);
        approve(data, build, build.contributions.get(0));
        assertTrue(KOMEBuildService.decideSubmission(data, build, build.contributions.get(0).id,
            build.managerUuid, "Builder", false, "Rejected", 30L).allowed);
        KOMEBuildContribution replacement = KOMEBuildService.addSubmission(data, build, build.managerUuid,
            "Builder", "gondor", 25L, true, 40L);
        assertTrue(replacement.isApproved()); assertEquals(25L, build.pendingNativeCentiHours());
        assertEquals(0L, rate(data, "gondor"));
        String autoApproved = saved(data).toString(); approve(data, build, replacement);
        assertEquals(autoApproved, saved(data).toString());
        assertTrue(KOMEBuildService.deleteBuild(data, build, build.managerUuid, "Builder", false, "Delete", 50L).allowed);
        assertEquals(0L, rate(data, "gondor")); assertFalse(build.active);
        String before = saved(data).toString();
        assertFalse(KOMEBuildService.adjustSubmission(data, build, replacement.id, null, "Admin",
            true, "1.00", "Inactive", 60L).allowed);
        assertEquals(before, saved(data).toString());
    }

    @Test public void exactMaximumAndFractionalRecordsSurviveThreeWorldRoundTrips() {
        KOMEWorldData data = world();
        for (KOMEBuildType type : KOMEBuildType.values()) for (long hours : new long[] {1L, 2450L, Long.MAX_VALUE}) {
            KOMEPlayerBuild build = create(data, type, hours); approve(data, build, build.contributions.get(0));
        }
        data.conquestTiles.get("T100").claim("rohan", 20L);
        NBTTagCompound initial = saved(data);
        String builds = initial.getTag("Builds").toString();
        BigInteger expectedRate = KOMEPopulationProjection.of(data, "rohan").dailyRateUnits;
        for (int i = 0; i < 3; i++) {
            KOMEWorldData loaded = new KOMEWorldData("reload"); loaded.readFromNBT(saved(data));
            assertEquals(builds, saved(loaded).getTag("Builds").toString());
            assertEquals(expectedRate, KOMEPopulationProjection.of(loaded, "rohan").dailyRateUnits);
            assertEquals("rohan", loaded.conquestTiles.get("T100").currentRulingFaction());
            data = loaded;
        }
        assertEquals(KOMEPlayerBuild.DATA_SCHEMA_VERSION, initial.getInteger("BuildDataSchemaVersion"));
        assertEquals(KOMEWorldData.KOME_DATA_SCHEMA_VERSION, initial.getInteger("KOMEDataSchemaVersion"));
        assertEquals(2, initial.getInteger("FactionPopulationDataSchemaVersion"));
        for (KOMEPlayerBuild build : data.builds.values()) {
            NBTTagCompound contribution = build.writeToNBT().getTagList("Contributions", 10).getCompoundTagAt(0);
            assertTrue(contribution.hasKey("CentiHours", 4)); assertFalse(contribution.hasKey("HalfHours"));
        }
    }

    @Test public void buildAuditIsBoundedAndKeepsMostRecentEntriesAcrossReload() {
        KOMEWorldData data = world(); KOMEPlayerBuild build = create(data, KOMEBuildType.NORMAL, 1L);
        for (int i = 0; i < 300; i++)
            assertTrue(KOMEBuildService.rename(data, build, build.managerUuid, false, "Hall " + i, i + 20L).allowed);
        assertEquals(250, build.auditHistory().size()); assertTrue(last(build).contains("name=Hall 299"));
        assertFalse(build.auditHistory().toString().contains("|REGISTER|"));
        assertTrue(build.auditHistory().get(0).contains("name=Hall 50;"));
        KOMEPlayerBuild loaded = new KOMEPlayerBuild(); loaded.readFromNBT(build.writeToNBT());
        assertEquals(build.auditHistory(), loaded.auditHistory());
        NBTTagCompound tag = build.writeToNBT(); NBTTagList history = new NBTTagList();
        for (int i = 0; i < 300; i++) history.appendTag(new NBTTagString("entry-" + i));
        tag.setTag("AuditHistory", history); loaded.readFromNBT(tag);
        assertEquals(250, loaded.auditHistory().size());
        assertEquals("entry-50", loaded.auditHistory().get(0)); assertEquals("entry-299", last(loaded));
    }

    @Test public void malformedSecondBuildFailsClosedWithoutPartialStateOrOverwrite() {
        KOMEWorldData source = world(); create(source, KOMEBuildType.NORMAL, 1L);
        create(source, KOMEBuildType.DEFENSIVE, 25L);
        for (Consumer<NBTTagCompound> corrupt : java.util.Arrays.<Consumer<NBTTagCompound>>asList(
                tag -> tag.removeTag("BuildType"), tag -> tag.setString("BuildType", "UNKNOWN"),
                tag -> tag.removeTag("Active"), tag -> tag.setInteger("BuildSchemaVersion", 1),
                tag -> tag.getTagList("Contributions", 10).getCompoundTagAt(0).setLong("CentiHours", -1L),
                tag -> tag.getTagList("Contributions", 10).getCompoundTagAt(0).setInteger("CentiHours", 1),
                tag -> tag.getTagList("Contributions", 10).getCompoundTagAt(0).setString("Status", "ADJUSTED"),
                tag -> tag.getTagList("Contributions", 10).getCompoundTagAt(0).setInteger("HalfHours", 1))) {
            NBTTagCompound saved = saved(source); NBTTagCompound bad = saved.getTagList("Builds", 10).getCompoundTagAt(1);
            String id = bad.getString("Id"); corrupt.accept(bad); String original = saved.toString();
            KOMEWorldData target = new KOMEWorldData("failed");
            try { target.readFromNBT(saved); fail("Expected load failure"); }
            catch (IllegalStateException failure) {
                assertTrue(failure.getMessage().contains("index 1")); assertTrue(failure.getMessage().contains(id));
            }
            assertTrue(target.builds.isEmpty()); assertTrue(target.isWriteBlocked());
            assertFalse(target.isIntegratedRootInitialized()); assertFalse(target.isDirty());
            blocked(target::initializeIntegratedWorld); blocked(target::markDirty);
            blocked(() -> target.writeToNBT(saved));
            assertEquals(original, saved.toString()); assertFalse(target.isDirty());
        }
    }

    @Test public void oldDevelopmentAndOverflowingBuildSchemaAreNotMigrated() {
        KOMEWorldData source = world(); KOMEPlayerBuild build = create(source, KOMEBuildType.NORMAL, Long.MAX_VALUE);
        approve(source, build, build.contributions.get(0));
        NBTTagCompound old = saved(source);
        old.setInteger("BuildDataSchemaVersion", KOMEPlayerBuild.DATA_SCHEMA_VERSION - 1);
        blocked(() -> new KOMEWorldData("old").readFromNBT(old));
        NBTTagCompound record = build.writeToNBT();
        KOMEBuildContribution excess = new KOMEBuildContribution(); excess.id = "excess";
        excess.centiHours = 1L; excess.status = KOMEBuildContribution.APPROVED;
        record.getTagList("Contributions", 10).appendTag(excess.writeToNBT());
        rejects(() -> new KOMEPlayerBuild().readFromNBT(record));
    }

    @Test public void emptyWorldHasCurrentEmptyBuildSchema() {
        KOMEWorldData empty = new KOMEWorldData("fresh");
        empty.initializeIntegratedWorld(); NBTTagCompound tag = saved(empty);
        assertEquals(KOMEPlayerBuild.DATA_SCHEMA_VERSION, tag.getInteger("BuildDataSchemaVersion"));
        assertEquals(0, tag.getTagList("Builds", 10).tagCount());
        KOMEWorldData loaded = new KOMEWorldData("loaded"); loaded.readFromNBT(tag);
        assertTrue(loaded.builds.isEmpty()); assertFalse(loaded.initializeIntegratedWorld());
    }

    @Test public void exactNativeAndCapturedRatesAggregateBeforeRounding() throws Exception {
        for (long configHours : new long[] {1L, 1000L, 1050L, 2450L, Long.MAX_VALUE}) {
            for (long multiplier : new long[] {0L, 1L, 125L, 5000L, 10000L}) {
                withSettings(configHours, multiplier, () -> {
                    KOMEWorldData data = world(); BigInteger total = BigInteger.ZERO;
                    for (long amount : new long[] {1L, 25L, 1025L}) {
                        KOMEPlayerBuild build = create(data, KOMEBuildType.NORMAL, amount);
                        approve(data, build, build.contributions.get(0)); total = total.add(BigInteger.valueOf(amount));
                    }
                    assertEquals(expected(total, configHours, 10000L), rate(data, "gondor"));
                    KOMEPlayerBuild defensive = create(data, KOMEBuildType.DEFENSIVE, Long.MAX_VALUE);
                    approve(data, defensive, defensive.contributions.get(0));
                    data.conquestTiles.get("T100").claim("rohan", 30L);
                    assertEquals(expected(total, configHours, multiplier), rate(data, "rohan"));
                    assertEquals(3, KOMEPopulationService.getPopulationRateContributions(data).size());
                    for (KOMEPopulationRateContribution row : KOMEPopulationService.getPopulationRateContributions(data)) {
                        assertEquals(expected(BigInteger.valueOf(row.approvedCentiHours), configHours, multiplier), row.currentRateUnits.longValueExact());
                        assertEquals(expected(BigInteger.valueOf(row.approvedCentiHours), configHours, 10000L), row.originalRateUnits.longValueExact());
                    }
                    data.conquestTiles.get("T100").claim("gondor", 40L);
                    assertEquals(expected(total, configHours, 10000L), rate(data, "gondor"));
                });
            }
        }
    }

    @Test public void roundedBuildRowsAreNotSummedAsFactionAuthority() throws Exception {
        withSettings(1_000_000L, 5000L, () -> {
            KOMEWorldData data = world();
            for (int i = 0; i < 2; i++) {
                KOMEPlayerBuild build = create(data, KOMEBuildType.NORMAL, 1L);
                approve(data, build, build.contributions.get(0));
            }
            data.conquestTiles.get("T100").claim("rohan", 30L);
            // Each row is exactly 0.5 fixed units and rounds up to 1. The aggregate is 1, not 2.
            for (KOMEPopulationRateContribution row : KOMEPopulationService.getPopulationRateContributions(data))
                assertEquals(1L, row.currentRateUnits.longValueExact());
            assertEquals(1L, KOMEPopulationRateService.getExactDailyPopulationRates(data, kome.common.config.KOMEConfigRegistry.population()).get("rohan").longValueExact());
        });
    }

    @Test public void oldHalfHourRepresentableRatesAreUnchanged() throws Exception {
        for (long halfHours : new long[] {0L, 1L, 3L, 20L, Integer.MAX_VALUE}) {
            for (long config : new long[] {1L, 1000L, 1050L, Long.MAX_VALUE}) {
                long amount = Math.multiplyExact(halfHours, 50L);
                assertEquals(expected(BigInteger.valueOf(amount), config, 10000L),
                    KOMEPopulationTestConfig.rateFromApprovedCentiHours(amount, config).longValueExact());
            }
        }
    }

    @Test public void duePayoutConsumesPreciseBuildRatesAsCentiPopulation() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
        KOMEWorldData data = world(); data.warSeason.recordLegalConflict(0L, -1L);
        KOMEPlayerBuild normal = create(data, KOMEBuildType.NORMAL, 1025L); approve(data, normal, normal.contributions.get(0));
        KOMEPlayerBuild defensive = create(data, KOMEBuildType.DEFENSIVE, 2450L); approve(data, defensive, defensive.contributions.get(0));
        KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, Instant.parse("2026-01-10T18:00:00Z"));
        Instant due = KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
        assertTrue(KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, due).success);
        assertEquals(102L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertEquals(Long.valueOf(5000L), data.populationPayoutRemainders.get("gondor"));
        assertTrue(KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, due).success);
        assertEquals(102L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        }
    }

    @Test public void commandAndGuiUseExactServiceBoundaries() throws Exception {
        KOMEWorldData data = world(); KOMEPlayerBuild build = create(data, KOMEBuildType.NORMAL, 2450L);
        approve(data, build, build.contributions.get(0));
        Method summary = KOMECommandBuild.class.getDeclaredMethod("summary", KOMEWorldData.class, KOMEPlayerBuild.class);
        summary.setAccessible(true); assertTrue(((String) summary.invoke(null, data, build))
            .contains("approved/developed/pending=24.50/24.50/0.00"));
        String command = read("command/KOMECommandBuild.java");
        assertTrue(command.contains("KOMEBuildService.adjustSubmission"));
        assertTrue(command.contains("KOMEBuildService.setApprovedHours"));
        assertTrue(command.contains("requireStaff(sender)"));
        assertFalse(command.contains("Double.parseDouble")); assertFalse(command.contains("halfHours"));
        String gui = new String(Files.readAllBytes(Paths.get("src/main/java/kome/client/gui/KOMEGuiConquestCapture.java")), StandardCharsets.UTF_8);
        assertTrue(gui.contains("KOMEBuildTime.parseHours(buildHoursField.getText())"));
        assertTrue(gui.contains("KOMEBuildTime.formatHours(contribution.centiHours)"));
        assertTrue(gui.contains("Current-manager submissions are approved immediately"));
        String packet = read("network/KOMEPacketBuildAction.java");
        assertTrue(packet.contains("KOMEBuildService.isManager(build, actorId)"));
        assertFalse(packet.contains("message.contributorIsManager"));
        String rate = read("data/KOMEPopulationRateService.java");
        assertFalse(rate.contains("halfHours")); assertFalse(rate.matches("(?s).*\\b(double|float)\\b.*"));
        assertEquals(long.class, KOMEBuildContribution.class.getField("centiHours").getType());
        for (Field field : KOMEPlayerBuild.class.getFields()) assertFalse(field.getName().contains("approvedHours"));
    }

    private static KOMEWorldData world() {
        KOMEWorldData data = new KOMEWorldData("precise");
        KOMEConquestTile tile = new KOMEConquestTile("T100"); tile.defaultRulingFaction = "gondor";
        tile.claim("gondor", 0L); data.conquestTiles.put(tile.id, tile); return data;
    }
    private static KOMEPlayerBuild create(KOMEWorldData data, KOMEBuildType type, long amount) {
        KOMEPlayerBuild build = KOMEBuildService.create(data, "Hall", "T100", KOMETileTestResources.dimension(),
            KOMETileTestResources.x(), 64D, KOMETileTestResources.z(),
            UUID.randomUUID(), "Builder", "gondor", "gondor", type, amount, 10L);
        if (type == KOMEBuildType.NORMAL) build.developedNativeCentiHours = amount;
        KOMEPlayerProgression progression = new KOMEPlayerProgression();
        progression.setPledgedLord("lord", "Lord", "gondor");
        data.progressions.put(build.managerUuid, progression);
        return build;
    }
    private static void approve(KOMEWorldData data, KOMEPlayerBuild build, KOMEBuildContribution contribution) {
        assertTrue(KOMEBuildService.decideSubmission(data, build, contribution.id, build.managerUuid,
            "Builder", true, "Reviewed", 20L).allowed);
    }
    private static String last(KOMEPlayerBuild build) { return build.auditHistory().get(build.auditHistory().size() - 1); }
    private static long rate(KOMEWorldData data, String faction) { return kome.common.data.KOMEPopulationProjection.of(data, faction).dailyRateUnits.longValueExact(); }
    private static NBTTagCompound saved(KOMEWorldData data) { NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag); return tag; }
    private static void rejects(Runnable action) { try { action.run(); fail("Expected invalid Build input"); } catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); } }
    private static void blocked(Runnable action) { try { action.run(); fail("Expected write-blocked schema"); } catch (IllegalStateException expected) { assertNotNull(expected.getMessage()); } }
    private static long expected(BigInteger amount, long config, long multiplier) {
        BigInteger denominator = BigInteger.valueOf(config).multiply(BigInteger.valueOf(10000L));
        BigInteger numerator = amount.multiply(BigInteger.valueOf(KOMEPopulationRate.SCALE)).multiply(BigInteger.valueOf(multiplier));
        return numerator.add(denominator.divide(BigInteger.valueOf(2L))).divide(denominator).longValueExact();
    }
    private static void withSettings(long hours, long multiplier, Runnable action) throws Exception {
        KOMEConfigRegistry.ValidatedConfig config = KOMEConfigRegistry.currentValidated();
        Field field = KOMEConfigRegistry.ValidatedConfig.class.getDeclaredField("population"); field.setAccessible(true);
        Object original = field.get(config);
        Constructor<KOMEConfigRegistry.PopulationSettings> constructor = KOMEConfigRegistry.PopulationSettings.class
            .getDeclaredConstructor(long.class, long.class, long.class, long.class,
                boolean.class, boolean.class, boolean.class, OptionalLong.class, boolean.class);
        constructor.setAccessible(true);
        KOMEConfigRegistry.PopulationSettings old = (KOMEConfigRegistry.PopulationSettings) original;
        try { field.set(config, constructor.newInstance(hours, multiplier,
            old.getBottleneckRateUnitsPerActiveServerDay(), old.getRecruitmentTileActiveRateThresholdUnits(),
            true, old.isPauseRateCeilingWhenNoPendingHours(), false, OptionalLong.empty(), false)); action.run(); }
        finally { field.set(config, original); }
    }
    private static String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get("src/main/java/kome/common/" + path)), StandardCharsets.UTF_8);
    }
}
