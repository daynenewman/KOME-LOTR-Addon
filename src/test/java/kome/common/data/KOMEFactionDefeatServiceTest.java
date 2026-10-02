package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Rule;
import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class KOMEFactionDefeatServiceTest {
    @Rule public final KOMETileTestResources geometry = new KOMETileTestResources();

    @Test public void capitalAndEveryLiveProductiveTileMustBeCaptured() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); build(data, "B", "T200", "gondor", 1000L);
            assertFalse(evaluate(data).defeated);
            capture(data, "T388");
            assertEquals(Arrays.asList("T200"), evaluate(data).remainingObjectives);
            assertFalse(evaluate(data).defeated);
            capture(data, "T200"); assertTrue(evaluate(data).defeated);
            data.conquestTiles.get("T200").claim("gondor", 0L);
            assertFalse(evaluate(data).defeated); // recapture before reconciliation prevents defeat
            assertEquals(0, KOMEFactionDefeatService.reconcile(data, 10L));
            capture(data, "T200"); data.conquestTiles.get("T388").claim("gondor", 0L);
            assertFalse(evaluate(data).defeated);
        }
    }

    @Test public void ownershipAndRateChangesAreLiveWithoutWarStartSnapshot() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); capture(data, "T388");
            KOMEPlayerBuild build = build(data, "B", "T200", "gondor", 999L);
            assertTrue(evaluate(data).defeated);
            setHours(build, 1000L); assertFalse(evaluate(data).defeated);
            setHours(build, 1001L); assertFalse(evaluate(data).defeated);
            setHours(build, 999L); assertTrue(evaluate(data).defeated);
            setHours(build, 1000L); build.active = false; assertTrue(evaluate(data).defeated);
            build.active = true; assertFalse(evaluate(data).defeated);
            build.populationFaction = "rohan"; assertTrue(evaluate(data).defeated);
            build.populationFaction = "gondor"; assertFalse(evaluate(data).defeated);
        }
    }

    @Test public void tileAggregationAndForeignCapturedProductionUseExistingRatePolicy() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); capture(data, "T388");
            build(data, "B1", "T200", "gondor", 500L);
            build(data, "B2", "T200", "gondor", 500L);
            assertEquals(Arrays.asList("T200"), evaluate(data).remainingProductiveTiles);
            data.builds.clear();
            build(data, "FOREIGN", "T200", "rohan", 2000L); // Gondor receives captured half-rate
            assertFalse(evaluate(data).defeated);
            config.set("population.capturedBuildMultiplier", "0.49");
            assertTrue(evaluate(data).defeated);
            config.set("population.capturedBuildMultiplier", "0.50");
            assertFalse(evaluate(data).defeated);
        }
    }

    @Test public void thresholdDoesNotRoundUpSubOneProduction() throws Exception {
        try (KOMEPopulationTestConfig config = new KOMEPopulationTestConfig()) {
            config.set("population.hoursPerPopulationPoint", "100000");
            KOMEWorldData data = world(); capture(data, "T388");
            KOMEPlayerBuild build = build(data, "B", "T200", "gondor", 9999999L);
            assertEquals(BigInteger.valueOf(KOMEPopulationRate.SCALE),
                KOMEPopulationRateService.getExactTilePopulationRates(data, KOMEConfigRegistry.population())
                    .get("T200").get("gondor")); // rounded display is one, exact rate is below one
            assertTrue(evaluate(data).defeated);
            setHours(build, 10000000L); assertFalse(evaluate(data).defeated);
            setHours(build, 10000001L); assertFalse(evaluate(data).defeated);
        }
    }

    @Test public void pendingAndUnapprovedHoursAreNotCurrentProduction() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); capture(data, "T388");
            KOMEPlayerBuild build = build(data, "B", "T200", "gondor", 1000L);
            build.developedNativeCentiHours = 999L; assertTrue(evaluate(data).defeated);
            build.developedNativeCentiHours = 0L;
            build.contributions.get(0).status = KOMEBuildContribution.PENDING;
            assertTrue(evaluate(data).defeated);
        }
    }

    @Test public void encirclementPressureDoesNotRemoveProductiveObjective() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); capture(data, "T388");
            build(data, "B", "T200", "gondor", 1000L);
            KOMEWar war = new KOMEWar(); war.id = "W1";
            war.sideOneFactions.add("gondor"); war.sideTwoFactions.add("mordor");
            data.wars.put(war.id, war);
            KOMEWarService.recordHostilePressure(data, war, 100L, "ENCIRCLEMENT");
            assertEquals(Arrays.asList("T200"), evaluate(data).remainingObjectives);
            assertEquals(0, KOMEFactionDefeatService.reconcile(data, 101L));
        }
    }

    @Test public void defensiveOnlyTileIsExcludedButCapitalAndMixedTileCount() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world();
            build(data, "D", "T200", "gondor", 10000L).type = KOMEBuildType.DEFENSIVE;
            assertEquals(Arrays.asList("T388"), evaluate(data).remainingObjectives);
            capture(data, "T388"); assertTrue(evaluate(data).defeated);
            build(data, "N", "T200", "gondor", 1000L); assertFalse(evaluate(data).defeated);
        }
    }

    @Test public void capitalChangesReadAuthorityAndUnclaimedOrMissingCapitalFailsClosed() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); capture(data, "T388"); assertTrue(evaluate(data).defeated);
            KOMEFactionCapitalRecord replacement = KOMEFactionCapitalService.getCapital(data, "rohan");
            data.factionCapitals.put("gondor", new KOMEFactionCapitalRecord("gondor",
                replacement.getCapitalTileId(), replacement.getDeploymentDimensionId(), replacement.getDeploymentX(),
                replacement.getDeploymentY(), replacement.getDeploymentZ(), 1L, "TEST", "operator"));
            tile(data, replacement.getCapitalTileId(), "gondor"); assertFalse(evaluate(data).defeated);
            capture(data, replacement.getCapitalTileId()); assertTrue(evaluate(data).defeated);
            data.conquestTiles.get(replacement.getCapitalTileId()).clearOwnershipOnly();
            assertFalse(evaluate(data).defeated);
            data.conquestTiles.remove(replacement.getCapitalTileId()); assertFalse(evaluate(data).ready);
            data.factionCapitals.remove("gondor"); assertFalse(evaluate(data).ready);
        }
    }

    @Test public void transitionAndAuditAreOncePerSeasonAndIndependentOfLiveRecapture() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); capture(data, "T388");
            assertEquals(1, KOMEFactionDefeatService.reconcile(data, 123L));
            assertTrue(data.warSeason.isFactionDefeated("GONDOR"));
            assertEquals(123L, data.warSeason.factionDefeatedAt("gondor"));
            assertEquals(1, defeatAudits(data));
            assertEquals(0, KOMEFactionDefeatService.reconcile(data, 124L));
            tile(data, "T388", "gondor"); assertFalse(evaluate(data).defeated);
            capture(data, "T388"); assertEquals(0, KOMEFactionDefeatService.reconcile(data, 125L));
            assertEquals(1, defeatAudits(data));
            KOMEAuditEntry audit = data.centralAudit.get(data.centralAudit.size() - 1);
            assertTrue(audit.details.contains("capitalCaptured=true"));
            assertTrue(audit.details.contains("remainingProductiveTiles=[];predicate=true"));
            assertTrue(data.isDirty());
            data.warSeason.repair(KOMEWarSeasonState.Phase.RESET, 126L);
            assertEquals(0, KOMEFactionDefeatService.reconcile(data, 126L));
            data.warSeason.completeReset(127L); assertFalse(data.warSeason.isFactionDefeated("gondor"));
            data.warSeason.recordLegalConflict(128L, 0L);
            assertEquals(1, KOMEFactionDefeatService.reconcile(data, 129L));
            assertEquals(2, defeatAudits(data));
        }
    }

    @Test public void outcomeAndAuditSurviveCompressedSaveAndFreshWorldDataLoad() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); capture(data, "T388");
            data.grantFactionPopulationCenti("gondor", 1234L);
            assertEquals(1, KOMEFactionDefeatService.reconcile(data, 10L));
            File file = Files.createTempFile("kom29-world", ".dat").toFile();
            try {
                NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag);
                try (java.io.FileOutputStream output = new java.io.FileOutputStream(file)) {
                    CompressedStreamTools.writeCompressed(tag, output);
                }
                KOMEWorldData restored = new KOMEWorldData("cold-restart");
                try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
                    restored.readFromNBT(CompressedStreamTools.readCompressed(input));
                }
                assertFalse(restored.isWriteBlocked());
                assertTrue(restored.warSeason.isFactionDefeated("gondor"));
                assertEquals(10L, restored.warSeason.factionDefeatedAt("gondor"));
                assertEquals(1, defeatAudits(restored));
                assertEquals(0, KOMEFactionDefeatService.reconcile(restored, 11L));
                assertEquals(1234L, KOMEPopulationService.getAvailablePopulationCenti(restored, "gondor"));
            } finally { Files.deleteIfExists(file.toPath()); }
        }
    }

    @Test public void failedPublicationRestoresOutcomeAuditAndDirtyStateThenRetriesOnce() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            FailingWorld data = new FailingWorld(); initialize(data); capture(data, "T388");
            data.setDirty(false); data.fail = true;
            int audits = data.centralAudit.size();
            try { KOMEFactionDefeatService.reconcile(data, 10L); fail("Expected dirty hook rejection"); }
            catch (IllegalStateException expected) { assertEquals("test dirty failure", expected.getMessage()); }
            assertFalse(data.warSeason.isFactionDefeated("gondor"));
            assertEquals(audits, data.centralAudit.size()); assertFalse(data.isDirty());
            data.fail = false; assertEquals(1, KOMEFactionDefeatService.reconcile(data, 11L));
            assertEquals(0, KOMEFactionDefeatService.reconcile(data, 12L)); assertEquals(1, defeatAudits(data));
        }
    }

    @Test public void lifecycleGatesDoNotCreateDefeatInSetupOrReset() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); capture(data, "T388");
            for (KOMEWarSeasonState.Phase phase : Arrays.asList(KOMEWarSeasonState.Phase.MAINTENANCE,
                    KOMEWarSeasonState.Phase.PRE_WAR, KOMEWarSeasonState.Phase.RESET)) {
                data.warSeason.phase = phase; assertTrue(evaluate(data).defeated);
                assertEquals(0, KOMEFactionDefeatService.reconcile(data, 10L));
            }
            data.warSeason.phase = KOMEWarSeasonState.Phase.FINALE;
            assertEquals(1, KOMEFactionDefeatService.reconcile(data, 11L));
        }
    }

    @Test public void startTickEvaluatesAfterLiveDevelopmentCrossesThreshold() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); capture(data, "T388");
            KOMEPlayerBuild build = build(data, "B", "T200", "gondor", 1000L);
            build.developedNativeCentiHours = 999L;
            KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime();
            assertTrue(KOMEEvents.processCampaignTick(data, null, 1000L, runtime).success);
            assertFalse(data.warSeason.isFactionDefeated("gondor"));
            long next = data.populationDevelopment.schedule().nextBoundary(
                Instant.ofEpochMilli(data.populationDevelopment.lastLiveBoundaryMillis)).toEpochMilli();
            assertTrue(KOMEEvents.processCampaignTick(data, null, next, runtime).success);
            assertEquals(1000L, build.developedNativeCentiHours);
            assertFalse(data.warSeason.isFactionDefeated("gondor"));
            capture(data, "T200");
            assertTrue(KOMEEvents.processCampaignTick(data, null, next + 1L, runtime).success);
            assertTrue(data.warSeason.isFactionDefeated("gondor"));
        }
    }

    @Test public void publicProjectionIsLiveAndReadOnlyAndWireContainsObjectives() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); capture(data, "T388"); build(data, "B", "T200", "gondor", 1000L);
            KOMEWar war = new KOMEWar(); war.id = "W1"; war.sideOneFactions.add("gondor");
            war.sideTwoFactions.add("mordor");
            data.setDirty(false);
            String inspection = KOMEFactionDefeatService.inspect(data, "gondor");
            assertTrue(inspection.contains("remaining objectives=[T200]"));
            assertTrue(inspection.contains("live predicate=false"));
            java.lang.reflect.Method projection = KOMEServerRecordBuilder.class.getDeclaredMethod(
                "addWarLine", List.class, KOMEWorldData.class, KOMEWar.class, boolean.class);
            projection.setAccessible(true);
            List<String> lines = new ArrayList<String>(); projection.invoke(null, lines, data, war, false);
            String[] fields = lines.get(0).split("\t", -1);
            assertEquals(23, fields.length); assertTrue(fields[22].contains(inspection));
            Class<?> uiRecord = Class.forName("kome.client.gui.KOMEGuiServerRecords$WarRecord");
            java.lang.reflect.Constructor<?> constructor = uiRecord.getDeclaredConstructor(String[].class);
            constructor.setAccessible(true);
            java.lang.reflect.Field uiObjectives = uiRecord.getDeclaredField("defeatObjectives");
            uiObjectives.setAccessible(true);
            assertEquals(fields[22], uiObjectives.get(constructor.newInstance((Object) fields)));
            assertFalse(data.isDirty()); assertEquals(0, defeatAudits(data));
        }
    }

    @Test public void corruptOutcomeCannotLoseOnceOnlyLatchSilently() {
        KOMEWarSeasonState state = new KOMEWarSeasonState();
        NBTTagCompound tag = new NBTTagCompound(); state.writeToNBT(tag);
        NBTTagCompound bad = new NBTTagCompound(); bad.setString("Faction", "gondor");
        bad.setLong("DefeatedAtMillis", -1L);
        NBTTagList list = new NBTTagList(); list.appendTag(bad); tag.setTag("FactionDefeats", list);
        try { state.readFromNBT(tag); fail("Expected invalid defeat rejection"); }
        catch (IllegalArgumentException expected) { assertTrue(expected.getMessage().contains("defeat")); }
    }

    @Test public void wrongTypedAndDuplicateDefeatRowsRejectRootLoad() {
        KOMEWorldData data = world(); NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
        NBTTagList wrong = new NBTTagList(); wrong.appendTag(new net.minecraft.nbt.NBTTagString("gondor"));
        saved.getCompoundTag("WarSeason").setTag("FactionDefeats", wrong);
        KOMEWorldData restored = new KOMEWorldData("bad-defeat");
        try { restored.readFromNBT(saved); fail("Expected root rejection"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("defeat")); }
        assertTrue(restored.isWriteBlocked());
        assertEquals(0, KOMEFactionDefeatService.reconcile(restored, 1L));
        assertFalse(KOMEFactionDefeatService.evaluate(restored, "gondor").ready);
        NBTTagCompound row = new NBTTagCompound(); row.setString("Faction", "gondor");
        row.setLong("DefeatedAtMillis", 1L);
        NBTTagList duplicate = new NBTTagList(); duplicate.appendTag(row); duplicate.appendTag(row.copy());
        saved.getCompoundTag("WarSeason").setTag("FactionDefeats", duplicate);
        restored = new KOMEWorldData("duplicate-defeat");
        try { restored.readFromNBT(saved); fail("Expected duplicate rejection"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("defeat")); }
        assertTrue(restored.isWriteBlocked());
    }

    @Test public void trimmedAuditHistoryDoesNotReplayDefeat() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            KOMEWorldData data = world(); capture(data, "T388");
            assertEquals(1, KOMEFactionDefeatService.reconcile(data, 1L));
            for (int i = 0; i < KOMEAuditService.MAX_ENTRIES; i++)
                KOMEAuditService.record(data, i, "TEST", "FILL", "operator", "subject", "fixture", "");
            assertEquals(0, defeatAudits(data));
            assertTrue(data.warSeason.isFactionDefeated("gondor"));
            assertEquals(0, KOMEFactionDefeatService.reconcile(data, 2L));
        }
    }

    private static KOMEWorldData world() { KOMEWorldData data = new KOMEWorldData("defeat"); initialize(data); return data; }
    private static void initialize(KOMEWorldData data) {
        KOMEFactionCapitalService.initializeMetadataFixture(data);
        for (String faction : KOMEAlliance.allFactionKeys()) tile(data,
            KOMEFactionCapitalService.getCapitalTileId(data, faction), faction);
        tile(data, "T200", "gondor"); data.warSeason.phase = KOMEWarSeasonState.Phase.WAR;
    }
    private static KOMEFactionDefeatService.Evaluation evaluate(KOMEWorldData data) {
        return KOMEFactionDefeatService.evaluate(data, "gondor");
    }
    private static void capture(KOMEWorldData data, String tile) { data.conquestTiles.get(tile).claim("mordor", 0L); }
    private static void tile(KOMEWorldData data, String id, String faction) {
        KOMEConquestTile tile = new KOMEConquestTile(id); tile.defaultRulingFaction = faction;
        tile.claim(faction, 0L); data.conquestTiles.put(id, tile);
    }
    private static KOMEPlayerBuild build(KOMEWorldData data, String id, String tile, String faction, long hours) {
        KOMEPlayerBuild build = new KOMEPlayerBuild(); build.id = id; build.displayName = id;
        build.tileId = tile; build.populationFaction = faction; build.originalBuilderFaction = faction;
        build.type = KOMEBuildType.NORMAL; build.active = true;
        KOMEBuildContribution contribution = new KOMEBuildContribution(); contribution.id = "H-" + id;
        contribution.status = KOMEBuildContribution.APPROVED; build.contributions.add(contribution);
        setHours(build, hours); data.builds.put(id, build); return build;
    }
    private static void setHours(KOMEPlayerBuild build, long hours) {
        build.contributions.get(0).centiHours = hours; build.developedNativeCentiHours = hours;
        build.validateContributions();
    }
    private static int defeatAudits(KOMEWorldData data) {
        int count = 0; for (KOMEAuditEntry audit : data.centralAudit)
            if ("FACTION_DEFEAT".equals(audit.action)) count++; return count;
    }
    private static class FailingWorld extends KOMEWorldData {
        boolean fail;
        FailingWorld() { super("fail-defeat"); }
        @Override public void markDirty() { if (fail) throw new IllegalStateException("test dirty failure"); super.markDirty(); }
    }
}
