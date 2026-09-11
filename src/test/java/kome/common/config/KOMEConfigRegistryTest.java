package kome.common.config;

import cpw.mods.fml.relauncher.FMLInjectionData;
import net.minecraftforge.common.config.Configuration;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KOMEConfigRegistryTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void initializeForgeConfigurationBasePath() throws Exception {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, new File(".").getAbsoluteFile());
    }

    @Test public void defaultsAreLoadedAndTyped() throws Exception {
        KOMEConfigRegistry.load(configFile());
        KOMEConfigRegistry.DailyBatchSettings dailyBatch = KOMEConfigRegistry.dailyBatch();
        KOMEConfigRegistry.PopulationSettings population = KOMEConfigRegistry.population();
        assertEquals(LocalTime.of(20, 0), dailyBatch.getLocalTime());
        assertEquals(ZoneId.of("America/Chicago"), dailyBatch.getTimezone());
        assertEquals(10, population.getHoursPerPopulationPoint());
        assertEquals(0.50D, population.getCapturedBuildMultiplier(), 0.0D);
        assertTrue(population.isOfflinePopulationCatchUp());
        assertEquals(false, population.isPopulationCapEnabled());
        assertEquals(false, population.isEncirclementPopulationSuppressionEnabled());
        assertEquals(1, KOMEConfigRegistry.movement().getFootOrMixedTilesPerDay());
        assertEquals(2, KOMEConfigRegistry.movement().getFullyMountedTilesPerDay());
        assertEquals(20, KOMEConfigRegistry.battle().getResponseLevel1Minutes());
        assertEquals(35, KOMEConfigRegistry.battle().getResponseLevel2Minutes());
        assertEquals(50, KOMEConfigRegistry.battle().getResponseLevel3Minutes());
    }

    @Test
    public void zeroHoursFailWithKeyAndValue() throws Exception {
        invalid(KOMEConfigRegistry.POPULATION_CATEGORY,
                KOMEConfigRegistry.HOURS_PER_POPULATION_POINT, "0");
    }

    @Test
    public void negativeHoursFailWithKeyAndValue() throws Exception {
        invalid(KOMEConfigRegistry.POPULATION_CATEGORY,
                KOMEConfigRegistry.HOURS_PER_POPULATION_POINT, "-1");
    }

    @Test
    public void capturedMultiplierBelowZeroFailsWithKeyAndValue() throws Exception {
        invalid(KOMEConfigRegistry.POPULATION_CATEGORY,
                KOMEConfigRegistry.CAPTURED_BUILD_MULTIPLIER, "-0.01");
    }

    @Test
    public void capturedMultiplierAboveOneFailsWithKeyAndValue() throws Exception {
        invalid(KOMEConfigRegistry.POPULATION_CATEGORY,
                KOMEConfigRegistry.CAPTURED_BUILD_MULTIPLIER, "1.01");
    }

    @Test
    public void capturedMultiplierBoundariesAreAccepted() throws Exception {
        assertEquals(0.0D, load(KOMEConfigRegistry.POPULATION_CATEGORY,
                KOMEConfigRegistry.CAPTURED_BUILD_MULTIPLIER, "0.0")
                .getCapturedBuildMultiplier(), 0.0D);
        assertEquals(1.0D, load(KOMEConfigRegistry.POPULATION_CATEGORY,
                KOMEConfigRegistry.CAPTURED_BUILD_MULTIPLIER, "1.0")
                .getCapturedBuildMultiplier(), 0.0D);
    }
    @Test
    public void dailyTimeWithSecondsFailsWithKeyAndValue() throws Exception {
        invalid(KOMEConfigRegistry.DAILY_BATCH_CATEGORY,
                KOMEConfigRegistry.LOCAL_TIME, "20:00:30");
    }

    @Test
    public void dailyTimeOutsideRangeFailsWithKeyAndValue() throws Exception {
        invalid(KOMEConfigRegistry.DAILY_BATCH_CATEGORY,
                KOMEConfigRegistry.LOCAL_TIME, "24:00");
    }

    @Test
    public void invalidTimezoneFailsWithKeyAndValue() throws Exception {
        invalid(KOMEConfigRegistry.DAILY_BATCH_CATEGORY,
                KOMEConfigRegistry.TIMEZONE, "Middle-earth/Shire");
    }

    @Test
    public void fixedOffsetTimezoneFailsWithKeyAndValue() throws Exception {
        invalid(KOMEConfigRegistry.DAILY_BATCH_CATEGORY,
                KOMEConfigRegistry.TIMEZONE, "+06:00");
    }

    @Test
    public void invalidBooleanFailsWithKeyAndValue() throws Exception {
        invalid(KOMEConfigRegistry.POPULATION_CATEGORY,
                KOMEConfigRegistry.OFFLINE_POPULATION_CATCH_UP, "sometimes");
    }

    @Test
    public void movementValuesAtOrBelowZeroFail() throws Exception {
        invalid(KOMEConfigRegistry.MOVEMENT_CATEGORY,
                KOMEConfigRegistry.FOOT_OR_MIXED_TILES_PER_DAY, "0");
        invalid(KOMEConfigRegistry.MOVEMENT_CATEGORY,
                KOMEConfigRegistry.FULLY_MOUNTED_TILES_PER_DAY, "-1");
    }
    @Test
    public void fullyMountedAllowanceBelowFootFails() throws Exception {
        invalid(new String[][] {{KOMEConfigRegistry.MOVEMENT_CATEGORY, KOMEConfigRegistry.FOOT_OR_MIXED_TILES_PER_DAY, "3"}, {KOMEConfigRegistry.MOVEMENT_CATEGORY, KOMEConfigRegistry.FULLY_MOUNTED_TILES_PER_DAY, "2"}}, KOMEConfigRegistry.MOVEMENT_CATEGORY, KOMEConfigRegistry.FULLY_MOUNTED_TILES_PER_DAY, "2");
    }
    @Test
    public void battleTimersAtOrBelowZeroFail() throws Exception {
        invalid(KOMEConfigRegistry.BATTLE_CATEGORY, KOMEConfigRegistry.RESPONSE_LEVEL_1_MINUTES, "0");
        invalid(KOMEConfigRegistry.BATTLE_CATEGORY, KOMEConfigRegistry.RESPONSE_LEVEL_2_MINUTES, "-1");
    }

    @Test
    public void responseLevel3TimerAtOrBelowZeroFails() throws Exception {
        invalid(KOMEConfigRegistry.BATTLE_CATEGORY,
                KOMEConfigRegistry.RESPONSE_LEVEL_3_MINUTES, "0");
    }

    @Test
    public void nonIncreasingBattleTimerSequenceFails() throws Exception {
        invalid(new String[][] {{KOMEConfigRegistry.BATTLE_CATEGORY, KOMEConfigRegistry.RESPONSE_LEVEL_1_MINUTES, "20"}, {KOMEConfigRegistry.BATTLE_CATEGORY, KOMEConfigRegistry.RESPONSE_LEVEL_2_MINUTES, "20"}}, KOMEConfigRegistry.BATTLE_CATEGORY, KOMEConfigRegistry.RESPONSE_LEVEL_2_MINUTES, "20");
        invalid(new String[][] {{KOMEConfigRegistry.BATTLE_CATEGORY, KOMEConfigRegistry.RESPONSE_LEVEL_2_MINUTES, "35"}, {KOMEConfigRegistry.BATTLE_CATEGORY, KOMEConfigRegistry.RESPONSE_LEVEL_3_MINUTES, "35"}}, KOMEConfigRegistry.BATTLE_CATEGORY, KOMEConfigRegistry.RESPONSE_LEVEL_3_MINUTES, "35");
    }
    @Test public void validCustomValuesLoadCorrectly() throws Exception {
        File file = configFile();
        write(file, KOMEConfigRegistry.DAILY_BATCH_CATEGORY, KOMEConfigRegistry.LOCAL_TIME, "06:30");
        write(file, KOMEConfigRegistry.DAILY_BATCH_CATEGORY, KOMEConfigRegistry.TIMEZONE, "Europe/London");
        write(file, KOMEConfigRegistry.MOVEMENT_CATEGORY, KOMEConfigRegistry.FOOT_OR_MIXED_TILES_PER_DAY, "2");
        write(file, KOMEConfigRegistry.MOVEMENT_CATEGORY, KOMEConfigRegistry.FULLY_MOUNTED_TILES_PER_DAY, "4");
        write(file, KOMEConfigRegistry.BATTLE_CATEGORY, KOMEConfigRegistry.RESPONSE_LEVEL_1_MINUTES, "15");
        write(file, KOMEConfigRegistry.BATTLE_CATEGORY, KOMEConfigRegistry.RESPONSE_LEVEL_2_MINUTES, "30");
        write(file, KOMEConfigRegistry.BATTLE_CATEGORY, KOMEConfigRegistry.RESPONSE_LEVEL_3_MINUTES, "45");
        KOMEConfigRegistry.load(file);
        assertEquals(LocalTime.of(6, 30), KOMEConfigRegistry.dailyBatch().getLocalTime());
        assertEquals(ZoneId.of("Europe/London"), KOMEConfigRegistry.dailyBatch().getTimezone());
        assertEquals(2, KOMEConfigRegistry.movement().getFootOrMixedTilesPerDay());
        assertEquals(4, KOMEConfigRegistry.movement().getFullyMountedTilesPerDay());
        assertEquals(15, KOMEConfigRegistry.battle().getResponseLevel1Minutes());
        assertEquals(30, KOMEConfigRegistry.battle().getResponseLevel2Minutes());
        assertEquals(45, KOMEConfigRegistry.battle().getResponseLevel3Minutes());
    }

    private void invalid(String category, String key, String value) throws Exception {
        invalid(new String[][] {{category, key, value}}, category, key, value);
    }

    private void invalid(String[][] entries, String expectedCategory, String expectedKey,
            String expectedValue) throws Exception {
        File file = configFile();
        for (String[] entry : entries) {
            write(file, entry[0], entry[1], entry[2]);
        }
        try {
            KOMEConfigRegistry.load(file);
            fail("Expected invalid configuration to fail startup");
        } catch (KOMEConfigValidationException expected) {
            assertTrue(expected.getMessage().contains(expectedCategory + "." + expectedKey));
            assertTrue(expected.getMessage().contains("'" + expectedValue + "'"));
        }
    }

    private KOMEConfigRegistry.PopulationSettings load(String category, String key, String value) throws Exception {
        File file = configFile();
        write(file, category, key, value);
        KOMEConfigRegistry.load(file);
        return KOMEConfigRegistry.population();
    }

    private void write(File file, String category, String key, String value) {
        Configuration c = new Configuration(file);
        c.load();
        c.get(category, key, "default").set(value);
        c.save();
    }

    private File configFile() throws Exception {
        return new File(temporaryFolder.newFolder(), "kome.cfg");
    }
}
