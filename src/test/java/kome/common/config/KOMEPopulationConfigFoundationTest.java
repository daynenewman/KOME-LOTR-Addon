package kome.common.config;

import cpw.mods.fml.relauncher.FMLInjectionData;
import kome.common.data.KOMEAuditEntry;
import kome.common.data.KOMEBuildContribution;
import kome.common.data.KOMEBuildType;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMEPopulationPayoutProcessor;
import kome.common.data.KOMEPopulationPayoutRuntime;
import kome.common.data.KOMEPopulationRate;
import kome.common.data.KOMEPopulationRateContribution;
import kome.common.data.KOMEPopulationRateService;
import kome.common.data.KOMEPopulationService;
import kome.common.data.KOMEWorldData;
import net.minecraftforge.common.config.Configuration;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.BeforeClass;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class KOMEPopulationConfigFoundationTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    private KOMEConfigRegistry.ValidatedConfig original;
    private static final KOMEConfigRegistry.RuntimeActivity IDLE = new KOMEConfigRegistry.RuntimeActivity() {
        public boolean isDailyTransactionInProgress() { return false; }
        public boolean isActiveSiegeInProgress() { return false; }
        public java.util.Collection<String> getActiveSiegeLockedConfigKeys() { return Collections.emptyList(); }
    };

    @BeforeClass public static void initializeForge() throws Exception {
        Field field = FMLInjectionData.class.getDeclaredField("minecraftHome");
        field.setAccessible(true);
        field.set(null, new File(".").getAbsoluteFile());
    }

    @Before public void baseline() throws Exception {
        original = KOMEConfigRegistry.currentValidated();
        KOMEConfigRegistry.onServerStop();
        KOMEConfigRegistry.load(file());
    }

    @After public void restore() throws Exception {
        KOMEConfigRegistry.onServerStop();
        Field active = KOMEConfigRegistry.class.getDeclaredField("current");
        active.setAccessible(true);
        active.set(null, original);
    }

    @Test public void defaultsUseExistingKeysAndSingleValidatedSnapshot() {
        KOMEConfigRegistry.PopulationSettings p = KOMEConfigRegistry.population();
        assertTrue(KOMEConfigRegistry.isReady());
        assertEquals(1000L, p.getHoursPerPopulationPointCentiHours());
        assertEquals(5000L, p.getCapturedBuildMultiplierBasisPoints());
        assertFalse(p.isPopulationCapEnabled());
        assertFalse(p.getPopulationCapCenti().isPresent());
        assertEquals("TBD", p.formatPopulationCap());
        assertEquals("America/Chicago", KOMEConfigRegistry.dailyBatch().getTimezone().getId());
        assertEquals("20:00", KOMEConfigRegistry.dailyBatch().getLocalTime().toString());
        assertEquals(100L, KOMEConfigRegistry.POPULATION_HOURS_SCALE);
        assertEquals(100L, KOMEConfigRegistry.POPULATION_CAP_SCALE);
        assertEquals(10000L, KOMEConfigRegistry.CAPTURED_MULTIPLIER_SCALE);
        assertSame(p, KOMEConfigRegistry.currentValidated().getPopulation());
    }

    @Test public void exactCustomValuesAndDeterministicInspection() throws Exception {
        File f = file();
        put(f, "population", "hoursPerPopulationPoint", "24.50");
        put(f, "population", "capturedBuildMultiplier", "0.0125");
        put(f, "population", "populationCapValue", "24.50");
        put(f, "dailyBatch", "timezone", "Europe/Paris");
        KOMEConfigRegistry.load(f);
        KOMEConfigRegistry.PopulationSettings p = KOMEConfigRegistry.population();
        assertEquals(2450L, p.getHoursPerPopulationPointCentiHours());
        assertEquals(125L, p.getCapturedBuildMultiplierBasisPoints());
        assertEquals(2450L, p.getPopulationCapCenti().getAsLong());
        assertEquals(ZoneId.of("Europe/Paris"), KOMEConfigRegistry.dailyBatch().getTimezone());
        assertValue("hoursPerPopulationPoint", "24.50");
        assertValue("capturedBuildMultiplier", "0.0125");
        assertValue("populationCapValue", "24.50");
        assertValue("populationCapCenti", "2450");
        assertValue("registryReady", "true");
        put(f, "population", "hoursPerPopulationPoint", "999");
        assertValue("hoursPerPopulationPoint", "24.50"); // Active snapshot, not stale file.
    }

    @Test public void hundredthsAndMaximumLongValuesAreExact() throws Exception {
        assertEquals(1000L, parse("10.00", 2, 1L, Long.MAX_VALUE));
        assertEquals(50L, parse("0.50", 2, 0L, Long.MAX_VALUE));
        assertEquals(1L, parse("0.01", 2, 0L, Long.MAX_VALUE));
        assertEquals(2450L, parse("24.50", 2, 0L, Long.MAX_VALUE));
        String max = "92233720368547758.07";
        assertEquals(Long.MAX_VALUE, parse(max, 2, 1L, Long.MAX_VALUE));
        File f = file();
        put(f, "population", "hoursPerPopulationPoint", max);
        put(f, "population", "populationCapValue", max);
        KOMEConfigRegistry.load(f);
        assertEquals(Long.MAX_VALUE, KOMEConfigRegistry.population().getHoursPerPopulationPointCentiHours());
        assertEquals(Long.MAX_VALUE, KOMEConfigRegistry.population().getPopulationCapCenti().getAsLong());
        assertValue("hoursPerPopulationPoint", max);
        rejects("population", "hoursPerPopulationPoint", "92233720368547758.08");
        rejects("population", "populationCapValue", "92233720368547758.08");
        f = file();
        put(f, "population", "hoursPerPopulationPoint", "0.01");
        put(f, "population", "capturedBuildMultiplier", "0.0001");
        put(f, "population", "populationCapValue", "0.01");
        KOMEConfigRegistry.load(f);
        assertEquals(1L, KOMEConfigRegistry.population().getHoursPerPopulationPointCentiHours());
        assertEquals(1L, KOMEConfigRegistry.population().getCapturedBuildMultiplierBasisPoints());
        assertEquals(1L, KOMEConfigRegistry.population().getPopulationCapCenti().getAsLong());
    }

    @Test public void invalidDecimalsRejectWholeCandidateAndKeepActiveConfig() throws Exception {
        String[] invalid = {"", " ", "abc", "1.2.3", "NaN", "Infinity", "-Infinity", "+Infinity",
                "1e1", "1E+2", "0x10", "1,25", "10.001", "10.000", "1.", ".5"};
        for (String value : invalid) rejects("population", "hoursPerPopulationPoint", value);
        for (String value : new String[]{"-1", "0", "0.00", "92233720368547758.08"}) {
            rejects("population", "hoursPerPopulationPoint", value);
        }
        for (String value : new String[]{"-0.01", "1.01", "0.00001", "NaN", "1e-2"}) {
            rejects("population", "capturedBuildMultiplier", value);
        }
        // Preserve dev's tighter rule: configured caps are positive even when disabled.
        for (String value : new String[]{"0", "-1", "NaN", "1.001", "Infinity", ""}) {
            rejects("population", "populationCapValue", value);
        }
    }

    @Test public void validEarlyPopulationFieldsDoNotPublishOnLateFailure() throws Exception {
        KOMEConfigRegistry.ValidatedConfig prior = KOMEConfigRegistry.currentValidated();
        File f = file();
        put(f, "population", "hoursPerPopulationPoint", "24.50");
        put(f, "dailyBatch", "timezone", "Europe/Paris");
        put(f, "season", "automaticFinaleEnabled", "true");
        try { KOMEConfigRegistry.load(f); fail(); }
        catch (KOMEConfigValidationException expected) { assertEquals("season.automaticFinaleEnabled", expected.getKey()); }
        assertSame(prior, KOMEConfigRegistry.currentValidated());
        assertTrue(KOMEConfigRegistry.isReady()); // The last valid active snapshot remains ready.
        assertTrue(KOMEConfigRegistry.getLastApplyStatus().contains("REJECTED"));
        assertTrue(KOMEConfigRegistry.getLastApplyStatus().contains("season.automaticFinaleEnabled"));
    }

    @Test public void regionTimezonesRejectAbbreviationsAndNeverFallback() throws Exception {
        File f = file(); put(f, "dailyBatch", "timezone", "Asia/Tokyo"); KOMEConfigRegistry.load(f);
        for (String zone : new String[]{"CST", "EST", "PST", "UTC", "GMT", "+06:00", "Z", "", "Nowhere/Unknown"}) {
            rejects("dailyBatch", "timezone", zone);
            assertEquals("Asia/Tokyo", KOMEConfigRegistry.dailyBatch().getTimezone().getId());
        }
        f = file(); put(f, "dailyBatch", "timezone", "US/Central"); KOMEConfigRegistry.load(f);
        assertEquals(ZoneId.of("US/Central"), KOMEConfigRegistry.dailyBatch().getTimezone());
    }

    @Test public void equivalentDecimalTextDoesNotCreateChange() throws Exception {
        File f = file();
        put(f, "population", "hoursPerPopulationPoint", "10.00");
        put(f, "population", "capturedBuildMultiplier", "0.5000");
        assertTrue(KOMEConfigChangeSet.compare(KOMEConfigRegistry.currentValidated(),
                KOMEConfigRegistry.readValidated(f)).isEmpty());
    }

    @Test public void capFlagPreservesConfiguredCentiValue() throws Exception {
        File f = file(); put(f, "population", "populationCapValue", "24.50");
        KOMEConfigRegistry.load(f);
        assertFalse(KOMEConfigRegistry.population().isPopulationCapEnabled());
        assertEquals(2450L, KOMEConfigRegistry.population().getPopulationCapCenti().getAsLong());
        put(f, "population", "populationCapEnabled", "true"); KOMEConfigRegistry.load(f);
        assertTrue(KOMEConfigRegistry.population().isPopulationCapEnabled());
        assertEquals(2450L, KOMEConfigRegistry.population().getPopulationCapCenti().getAsLong());
        f = file(); put(f, "population", "populationCapEnabled", "true");
        try { KOMEConfigRegistry.load(f); fail(); }
        catch (KOMEConfigValidationException expected) { assertTrue(expected.getMessage().contains("must be configured")); }
    }

    @Test public void initializedWorldGuardsEveryGenerationAndClockKeyAtomically() throws Exception {
        KOMEWorldData data = bindWorld();
        KOMEConfigRegistry.ValidatedConfig before = KOMEConfigRegistry.currentValidated();
        String[][] edits = {{"population", "hoursPerPopulationPoint", "12.50"},
                {"population", "capturedBuildMultiplier", "0.75"},
                {"population", "populationCapEnabled", "true"},
                {"population", "populationCapValue", "24.50"},
                {"dailyBatch", "timezone", "Europe/Paris"},
                {"dailyBatch", "localTime", "21:00"}};
        NBTTagCompound initial = serializedGameplay(data);
        for (String[] edit : edits) {
            File f = file();
            put(f, edit[0], edit[1], edit[2]);
            if ("populationCapEnabled".equals(edit[1])) put(f, "population", "populationCapValue", "50");
            put(f, "movement", "footOrMixedTilesPerDay", "2"); // Must not partially publish.
            KOMEConfigRegistry.ConfigApplyResult result =
                    KOMEConfigRegistry.applyValidated(KOMEConfigRegistry.readValidated(f), IDLE);
            assertFalse(result.getDecision().isAllowed());
            assertTrue(result.getDecision().getBlockingKeys().contains(edit[0] + "." + edit[1]));
            assertSame(before, KOMEConfigRegistry.currentValidated());
            assertValue("worldConfigurationLocked", "true");
            assertTrue(KOMEConfigRegistry.getLastApplyStatus().contains("restart"));
            assertEquals("DEFERRED", data.centralAudit.get(data.centralAudit.size() - 1).action);
            assertEquals(initial, serializedGameplay(data));
            try { KOMEConfigRegistry.load(f); fail("load must not bypass the guard"); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("DEFERRED")); }
            assertSame(before, KOMEConfigRegistry.currentValidated());
        }
        assertTrue(KOMEConfigRegistry.applyValidated(before, IDLE).getDecision().isAllowed());
        KOMEConfigRegistry.onServerStop();
        assertFalse(KOMEConfigRegistry.isWorldConfigurationLocked());
        File f = file(); put(f, "population", "hoursPerPopulationPoint", "12.50");
        KOMEConfigRegistry.load(f); // Startup remains permitted.
        assertEquals(1250L, KOMEConfigRegistry.population().getHoursPerPopulationPointCentiHours());
    }

    @Test public void acceptedAndRejectedChangesUseCentralAuditAndInspection() throws Exception {
        KOMEWorldData data = bindWorld();
        int count = data.centralAudit.size();
        KOMEConfigRegistry.onWorldInitialized(data);
        assertEquals(count, data.centralAudit.size()); // Tick binding is idempotent.
        File f = file(); put(f, "movement", "footOrMixedTilesPerDay", "2");
        assertTrue(KOMEConfigRegistry.applyValidated(KOMEConfigRegistry.readValidated(f), IDLE).getDecision().isAllowed());
        KOMEAuditEntry accepted = data.centralAudit.get(data.centralAudit.size() - 1);
        assertEquals("CONFIG", accepted.domain);
        assertEquals("ACCEPTED", accepted.action);
        assertTrue(accepted.details.contains("10.00"));
        assertTrue(accepted.details.contains("America/Chicago"));
        rejects("population", "capturedBuildMultiplier", "NaN");
        assertEquals("REJECTED", data.centralAudit.get(data.centralAudit.size() - 1).action);
        List<KOMEConfigInspection.EffectiveValue> values = KOMEConfigInspection.getEffectiveValues("population");
        boolean diagnostic = false;
        for (KOMEConfigInspection.EffectiveValue v : values)
            if ("lastApplyStatus".equals(v.getKey())) diagnostic = v.getValue().contains("REJECTED");
        assertTrue(diagnostic);
        assertEquals(5000L, KOMEConfigRegistry.population().getCapturedBuildMultiplierBasisPoints());
    }

    @Test public void readinessIsDerivedFromOneValidatedSnapshot() throws Exception {
        KOMEConfigRegistry.ValidatedConfig v = KOMEConfigRegistry.currentValidated();
        Constructor<?> bootstrapConstructor = null;
        for (Constructor<?> constructor : KOMEConfigRegistry.ValidatedConfig.class.getDeclaredConstructors())
            if (constructor.getParameterTypes().length == 11) bootstrapConstructor = constructor;
        assertNotNull(bootstrapConstructor);
        bootstrapConstructor.setAccessible(true);
        Object bootstrap = bootstrapConstructor.newInstance(v.getDailyBatch(), v.getPopulation(), v.getMovement(),
                v.getBattle(), v.getMuster(), v.getSiege(), v.getBattleSupport(), v.getEncirclement(),
                v.getSeason(), v.getGear(), false);
        Field active = KOMEConfigRegistry.class.getDeclaredField("current");
        active.setAccessible(true); active.set(null, bootstrap);
        assertFalse(KOMEConfigRegistry.isReady());
        rejects("population", "hoursPerPopulationPoint", "NaN");
        assertFalse(KOMEConfigRegistry.isReady());
        assertValue("registryReady", "false");
        KOMEConfigRegistry.load(file());
        assertTrue(KOMEConfigRegistry.isReady());
    }

    @Test public void ownershipAndConsumerBoundariesRemainExplicit() throws Exception {
        assertFalse(Files.exists(Paths.get("src/main/java/kome/common/config/KOMEPopulationConfig.java")));
        String registry = source("src/main/java/kome/common/config/KOMEConfigRegistry.java");
        String population = registry.substring(registry.indexOf("public static final class PopulationSettings"),
                registry.indexOf("public static final class MovementSettings"));
        assertFalse(population.contains("private final double"));
        assertFalse(population.contains("private final float"));
        assertFalse(population.contains("static volatile"));
        assertFalse(population.contains("getHoursPerPopulationPoint()"));
        String rate = source("src/main/java/kome/common/data/KOMEPopulationRateService.java");
        String payout = source("src/main/java/kome/common/data/KOMEPopulationPayoutProcessor.java");
        assertTrue(rate.contains("getHoursPerPopulationPointCentiHours()"));
        assertTrue(rate.contains("getCapturedBuildMultiplierBasisPoints()"));
        assertTrue(payout.contains("getPopulationCapCenti()"));
        assertFalse(rate.matches("(?s).*\\b(double|float)\\b.*"));
        try (Stream<Path> paths = Files.walk(Paths.get("src/main/java/kome"))) {
            for (Path path : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String production = source(path.toString());
                assertFalse(path.toString(), production.matches("(?s).*\\.\\s*(getHoursPerPopulationPoint|getCapturedBuildMultiplier|getPopulationCapValue)\\s*\\(.*"));
            }
        }
        assertTrue(source("src/main/java/kome/common/data/KOMEEvents.java").contains(
                "KOMEConfigRegistry.onWorldInitialized(data)"));
        assertTrue(source("src/main/java/kome/common/KOMEAddon.java").contains("KOMEConfigRegistry.onServerStop()"));
    }

    @Test public void everyAcceptedHourRangeWorksThroughRatesAndLivePayout() throws Exception {
        String[] hours = {"10.00", "10.50", "24.50", "0.01", "92233720368547758.07", "2147483648.00"};
        long[] expectedRates = {1000000L, 952381L, 408163L, 1000000000L, 0L, 0L};
        for (int i = 0; i < hours.length; i++) {
            KOMEConfigRegistry.onServerStop();
            File f = file(); put(f, "population", "hoursPerPopulationPoint", hours[i]);
            KOMEConfigRegistry.load(f);
            assertTrue(hours[i], KOMEConfigRegistry.isReady());
            KOMEWorldData data = productionWorld(20, "gondor");
            data.initializeIntegratedWorld();
            KOMEConfigRegistry.onWorldInitialized(data);
            Map<String, java.math.BigInteger> rates = KOMEPopulationRateService.getExactDailyPopulationRates(data, kome.common.config.KOMEConfigRegistry.population());
            assertEquals(hours[i], expectedRates[i], rates.get("gondor").longValueExact());
            List<KOMEPopulationRateContribution> rows = KOMEPopulationRateService.getPopulationRateContributions(data);
            assertEquals(1, rows.size());
            assertEquals(expectedRates[i], rows.get(0).originalRateUnits.longValueExact());
            assertEquals(expectedRates[i], rows.get(0).currentRateUnits.longValueExact());
            // Command, GUI and server-record paths use these same production service entry points.
            assertEquals(expectedRates[i], kome.common.data.KOMEPopulationProjection.of(data, "gondor").dailyRateUnits.longValueExact());
            assertEquals(expectedRates[i], KOMEPopulationService.getPopulationRateContributions(data).get(0).currentRateUnits.longValueExact());
            KOMEPopulationPayoutProcessor.Result result = payOneBoundary(data);
            assertTrue(hours[i], result.success);
            assertEquals(java.math.BigInteger.valueOf(expectedRates[i]), result.factions.get(0).exactRateUnits);
            long expectedBank = expectedRates[i] / KOMEPopulationPayoutProcessor.RATE_UNITS_PER_CENTI;
            assertEquals(expectedBank, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
            assertEquals(expectedRates[i] % KOMEPopulationPayoutProcessor.RATE_UNITS_PER_CENTI,
                    data.populationPayoutRemainders.containsKey("gondor") ? data.populationPayoutRemainders.get("gondor").longValue() : 0L);
            Instant boundary = Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis);
            assertTrue(new KOMEPopulationPayoutRuntime().onStartup(data, boundary).success);
            assertEquals(expectedBank, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        }
    }

    @Test public void capturedBasisPointsAreExactForWholeAndFractionalHours() throws Exception {
        String[] multipliers = {"0.5000", "0.0125", "0.0001", "0", "1.0000"};
        String[] hours = {"10.00", "10.50"};
        long[][] expected = {{500000L, 12500L, 100L, 0L, 1000000L}, {476190L, 11905L, 95L, 0L, 952381L}};
        for (int h = 0; h < hours.length; h++) {
            for (int i = 0; i < multipliers.length; i++) {
                File f = file(); put(f, "population", "hoursPerPopulationPoint", hours[h]);
                put(f, "population", "capturedBuildMultiplier", multipliers[i]); KOMEConfigRegistry.load(f);
                assertTrue(KOMEConfigRegistry.isReady());
                KOMEWorldData data = productionWorld(20, "rohan");
                KOMEPopulationRateContribution row = KOMEPopulationRateService.getPopulationRateContributions(data).get(0);
                assertEquals("CAPTURED", row.status);
                assertEquals(expected[h][i], row.currentRateUnits.longValueExact());
                assertEquals(expected[h][i], KOMEPopulationRateService.getExactDailyPopulationRates(data, kome.common.config.KOMEConfigRegistry.population()).get("rohan").longValueExact());
                KOMEPopulationPayoutProcessor.Result payout = payOneBoundary(data);
                assertTrue(payout.success);
                assertEquals(java.math.BigInteger.valueOf(expected[h][i]), payout.factions.get(0).exactRateUnits);
                assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
                for (KOMEConquestTile tile : data.conquestTiles.values()) tile.claim("gondor", 0L);
                assertEquals(h == 0 ? 1000000L : 952381L,
                        kome.common.data.KOMEPopulationProjection.of(data, "gondor").dailyRateUnits.longValueExact());
            }
        }
    }

    @Test public void fractionalCapturedSourcesRoundOnlyAfterFactionAggregation() throws Exception {
        File f = file(); put(f, "population", "hoursPerPopulationPoint", "10.50");
        put(f, "population", "capturedBuildMultiplier", "0.0001"); KOMEConfigRegistry.load(f);
        KOMEWorldData data = productionWorld(1, "rohan");
        addProductionBuild(data, "second", 1, "rohan"); addProductionBuild(data, "third", 1, "rohan");
        for (KOMEPopulationRateContribution row : KOMEPopulationService.getPopulationRateContributions(data))
            assertEquals(5L, row.currentRateUnits.longValueExact());
        // 3 * (100 / 21) millionths rounds to 14, not the sum of three rounded rows (15).
        assertEquals(14L, kome.common.data.KOMEPopulationProjection.of(data, "rohan").dailyRateUnits.longValueExact());
        assertEquals(14L, payOneBoundary(data).factions.get(0).nextRemainderUnits);
        put(f, "population", "hoursPerPopulationPoint", "0.16"); KOMEConfigRegistry.load(f);
        // 0.5 / 0.16 * 0.0001 * SCALE = 312.5: a final exact tie rounds up.
        assertEquals(313L, kome.common.data.KOMEPopulationProjection.of(productionWorld(1, "rohan"), "rohan").dailyRateUnits.longValueExact());
    }

    @Test public void extremeApprovedTimeAndValidHoursHaveNoIntermediateOverflow() throws Exception {
        File f = file(); put(f, "population", "hoursPerPopulationPoint", "0.01");
        KOMEConfigRegistry.load(f);
        KOMEWorldData data = productionWorld(Integer.MAX_VALUE, "gondor");
        assertEquals(107374182350000000L,
                kome.common.data.KOMEPopulationProjection.of(data, "gondor").dailyRateUnits.longValueExact());
        KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime();
        runtime.onStartup(data, Instant.parse("2026-01-10T02:00:00Z"));
        NBTTagCompound before = new NBTTagCompound(); data.writeToNBT(before);
        Instant due = KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
        // Checkpoint E grants the exact representable centi amount without the former int ceiling.
        assertTrue(runtime.onLiveCheck(data, due).success);
        assertEquals(10737418235000L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        put(f, "population", "hoursPerPopulationPoint", "92233720368547758.07");
        KOMEConfigRegistry.load(f);
        assertEquals(0L, kome.common.data.KOMEPopulationProjection.of(data, "gondor").dailyRateUnits.longValueExact());
        assertTrue(runtime.onLiveCheck(data, due).success);
    }

    @Test public void exactCentiCapFillsFractionalRoom() throws Exception {
        File f = file(); put(f, "population", "populationCapEnabled", "true");
        put(f, "population", "populationCapValue", "24.50"); KOMEConfigRegistry.load(f);
        long[] banks = {2350L, 2400L, 2450L, 2500L};
        long[] expected = {2450L, 2450L, 2450L, 2500L};
        for (int i = 0; i < banks.length; i++) {
            KOMEWorldData data = productionWorld(20, "gondor");
            KOMEPopulationService.grantCenti(data, "gondor", banks[i]);
            KOMEPopulationPayoutProcessor.Result payout = payOneBoundary(data);
            assertTrue(payout.success);
            assertEquals(expected[i], KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
            assertEquals(expected[i] - banks[i], payout.factions.get(0).grantedCenti);
            assertEquals(java.math.BigInteger.valueOf(100L - (expected[i] - banks[i])), payout.factions.get(0).blockedCenti);
        }
        put(f, "population", "populationCapValue", "0.01"); KOMEConfigRegistry.load(f);
        assertEquals(1L, payOneBoundary(productionWorld(20, "gondor")).factions.get(0).grantedCenti);
    }

    @Test public void disabledFractionalCapsDoNotLimitPayouts() throws Exception {
        File f = file(); put(f, "population", "populationCapValue", "0.01"); KOMEConfigRegistry.load(f);
        KOMEWorldData data = productionWorld(20, "gondor");
        KOMEPopulationService.grantCenti(data, "gondor", 2350L);
        assertEquals(100L, payOneBoundary(data).factions.get(0).grantedCenti);
        assertEquals(2450L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
    }

    @Test public void maximumCentiCapIsNotClampedAndArithmeticRemainsAtomic() throws Exception {
        File f = file(); put(f, "population", "populationCapEnabled", "true");
        put(f, "population", "populationCapValue", "92233720368547758.07"); KOMEConfigRegistry.load(f);
        for (long bank : new long[]{214748364700L, Long.MAX_VALUE - 100L, Long.MAX_VALUE - 99L, Long.MAX_VALUE}) {
            KOMEWorldData data = productionWorld(20, "gondor"); KOMEPopulationService.grantCenti(data, "gondor", bank);
            KOMEPopulationPayoutProcessor.Result result = payOneBoundary(data);
            long grant = Math.min(100L, Long.MAX_VALUE - bank);
            assertTrue(result.success); assertEquals(grant, result.factions.get(0).grantedCenti);
            assertEquals(bank + grant, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        }
        put(f, "population", "populationCapEnabled", "false"); KOMEConfigRegistry.load(f);
        KOMEWorldData overflow = productionWorld(20, "gondor");
        addProductionBuild(overflow, "overflow", 20, "rohan").populationFaction = "rohan";
        KOMEPopulationService.grantCenti(overflow, "rohan", Long.MAX_VALUE - 50L);
        KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime();
        runtime.onStartup(overflow, Instant.parse("2026-01-10T02:00:00Z"));
        NBTTagCompound before = new NBTTagCompound(); overflow.writeToNBT(before);
        Instant due = KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(overflow.lastPopulationPayoutBoundaryMillis));
        assertFalse(runtime.onLiveCheck(overflow, due).success);
        NBTTagCompound after = new NBTTagCompound(); overflow.writeToNBT(after);
        assertEquals(before, after); // No faction grant, remainder or boundary commits on failure.
    }

    @Test public void configurationStopCleanupIsIdempotentAndAllowsNewWorldBinding() throws Exception {
        KOMEWorldData first = bindWorld(); int oldAuditSize = first.centralAudit.size();
        for (int i = 0; i < 5; i++) { // Shared data on repeated dimension START activity.
            KOMEConfigRegistry.onWorldInitialized(first);
            assertTrue(KOMEConfigRegistry.isWorldConfigurationLocked());
        }
        assertEquals(oldAuditSize, first.centralAudit.size());
        KOMEConfigRegistry.onServerStop(); assertFalse(KOMEConfigRegistry.isWorldConfigurationLocked());
        KOMEConfigRegistry.onServerStop(); assertFalse(KOMEConfigRegistry.isWorldConfigurationLocked());
        KOMEWorldData second = bindWorld();
        assertTrue(KOMEConfigRegistry.isWorldConfigurationLocked());
        assertEquals("WORLD_BOUND", second.centralAudit.get(second.centralAudit.size() - 1).action);
        File f = file(); put(f, "population", "capturedBuildMultiplier", "0.25");
        assertFalse(KOMEConfigRegistry.applyValidated(KOMEConfigRegistry.readValidated(f), IDLE).getDecision().isAllowed());
        assertEquals("DEFERRED", second.centralAudit.get(second.centralAudit.size() - 1).action);
        assertEquals(oldAuditSize, first.centralAudit.size());

        String addon = source("src/main/java/kome/common/KOMEAddon.java").replace("\r", "");
        assertTrue(addon.contains("@Mod.EventHandler\n    public void serverStopped(FMLServerStoppedEvent event) {\n        KOMEConfigRegistry.onServerStop();\n    }"));
        String stopping = addon.substring(addon.indexOf("public void serverStopping(FMLServerStoppingEvent event)"));
        stopping = stopping.substring(0, stopping.indexOf("\n    }"));
        assertTrue(stopping.contains("KOMEConfigRegistry.onServerStop();"));
        assertFalse(source("src/main/java/kome/common/data/KOMEEvents.java").contains("KOMEConfigRegistry.onServerStop()"));
    }

    private KOMEWorldData productionWorld(int halfHours, String controller) {
        KOMEWorldData data = new KOMEWorldData("production-config");
        data.warSeason.recordLegalConflict(0L, -1L);
        addProductionBuild(data, "first", halfHours, controller);
        return data;
    }

    private KOMEPlayerBuild addProductionBuild(KOMEWorldData data, String id, int halfHours, String controller) {
        KOMEConquestTile tile = new KOMEConquestTile("T-" + id); tile.claim(controller, 0L);
        data.conquestTiles.put(tile.id, tile);
        KOMEPlayerBuild build = new KOMEPlayerBuild(); build.id = id; build.tileId = tile.id;
        build.populationFaction = "gondor"; build.type = KOMEBuildType.NORMAL; build.active = true;
        KOMEBuildContribution contribution = new KOMEBuildContribution(); contribution.id = "H-" + id;
        contribution.centiHours = Math.multiplyExact((long) halfHours, 50L); contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution); data.builds.put(build.id, build); return build;
    }

    private KOMEPopulationPayoutProcessor.Result payOneBoundary(KOMEWorldData data) {
        KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime();
        runtime.onStartup(data, Instant.parse("2026-01-10T02:00:00Z"));
        Instant due = KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
        KOMEPopulationPayoutProcessor.Result result = runtime.onLiveCheck(data, due);
        assertEquals(due.toEpochMilli(), data.lastPopulationPayoutBoundaryMillis);
        return result;
    }

    private KOMEWorldData bindWorld() {
        KOMEWorldData data = new KOMEWorldData("config-foundation");
        data.initializeIntegratedWorld();
        KOMEConfigRegistry.onWorldInitialized(data);
        return data;
    }

    private NBTTagCompound serializedGameplay(KOMEWorldData data) {
        NBTTagCompound nbt = new NBTTagCompound(); data.writeToNBT(nbt);
        nbt.removeTag("CentralAudit"); return nbt;
    }

    private void rejects(String category, String key, String value) throws Exception {
        KOMEConfigRegistry.ValidatedConfig before = KOMEConfigRegistry.currentValidated();
        File f = file(); put(f, category, key, value);
        try { KOMEConfigRegistry.load(f); fail(category + "." + key + "=" + value); }
        catch (KOMEConfigValidationException expected) {
            assertEquals(category + "." + key, expected.getKey());
            assertTrue(expected.getMessage().contains(value));
        }
        assertSame(before, KOMEConfigRegistry.currentValidated());
    }

    private static void assertValue(String key, String expected) {
        for (KOMEConfigInspection.EffectiveValue value : KOMEConfigInspection.getEffectiveValues("population"))
            if (key.equals(value.getKey())) { assertEquals(expected, value.getValue()); return; }
        fail("Missing " + key);
    }

    private static long parse(String value, int scale, long min, long max) {
        return KOMEConfigRegistry.exactDecimal("population", "test", value, scale, min, max);
    }
    private File file() throws Exception { return new File(temporary.newFolder(), "kome.cfg"); }
    private static void put(File file, String category, String key, String value) {
        Configuration c = new Configuration(file); c.load(); c.get(category, key, "default").set(value); c.save();
    }
    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
