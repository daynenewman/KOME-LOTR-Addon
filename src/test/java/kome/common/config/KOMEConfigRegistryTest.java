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
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass
    public static void initializeForgeConfigurationBasePath() throws Exception {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, new File(".").getAbsoluteFile());
    }

    @Test
    public void defaultsAreLoadedAndTyped() throws Exception {
        KOMEConfigRegistry.load(configFile());

        KOMEConfigRegistry.PopulationSettings settings = KOMEConfigRegistry.population();
        assertEquals(10, settings.getHoursPerPopulationPoint());
        assertEquals(0.50D, settings.getCapturedBuildMultiplier(), 0.0D);
        assertEquals(LocalTime.of(20, 0), settings.getDailyBatchLocalTime());
        assertEquals(ZoneId.of("America/Chicago"), settings.getTimezone());
        assertTrue(settings.isOfflinePopulationCatchUp());
        assertEquals(false, settings.isPopulationCapEnabled());
        assertEquals(false, settings.isEncirclementPopulationSuppressionEnabled());
    }

    @Test public void zeroHoursFailWithKeyAndValue() throws Exception {
        assertInvalid(KOMEConfigRegistry.HOURS_PER_POPULATION_POINT, "0");
    }

    @Test public void negativeHoursFailWithKeyAndValue() throws Exception {
        assertInvalid(KOMEConfigRegistry.HOURS_PER_POPULATION_POINT, "-1");
    }

    @Test public void capturedMultiplierBelowZeroFailsWithKeyAndValue() throws Exception {
        assertInvalid(KOMEConfigRegistry.CAPTURED_BUILD_MULTIPLIER, "-0.01");
    }

    @Test public void capturedMultiplierAboveOneFailsWithKeyAndValue() throws Exception {
        assertInvalid(KOMEConfigRegistry.CAPTURED_BUILD_MULTIPLIER, "1.01");
    }

    @Test public void capturedMultiplierBoundariesAreAccepted() throws Exception {
        assertEquals(0.0D, loadValue(KOMEConfigRegistry.CAPTURED_BUILD_MULTIPLIER, "0.0")
                .getCapturedBuildMultiplier(), 0.0D);
        assertEquals(1.0D, loadValue(KOMEConfigRegistry.CAPTURED_BUILD_MULTIPLIER, "1.0")
                .getCapturedBuildMultiplier(), 0.0D);
    }

    @Test public void dailyTimeWithSecondsFailsWithKeyAndValue() throws Exception {
        assertInvalid(KOMEConfigRegistry.DAILY_BATCH_LOCAL_TIME, "20:00:30");
    }

    @Test public void dailyTimeOutsideRangeFailsWithKeyAndValue() throws Exception {
        assertInvalid(KOMEConfigRegistry.DAILY_BATCH_LOCAL_TIME, "24:00");
    }

    @Test public void invalidTimezoneFailsWithKeyAndValue() throws Exception {
        assertInvalid(KOMEConfigRegistry.TIMEZONE, "Middle-earth/Shire");
    }

    @Test public void fixedOffsetTimezoneFailsWithKeyAndValue() throws Exception {
        assertInvalid(KOMEConfigRegistry.TIMEZONE, "+06:00");
    }

    @Test public void invalidBooleanFailsWithKeyAndValue() throws Exception {
        assertInvalid(KOMEConfigRegistry.OFFLINE_POPULATION_CATCH_UP, "sometimes");
    }

    private void assertInvalid(String key, String value) throws Exception {
        File file = configFile();
        writeValue(file, key, value);
        try {
            KOMEConfigRegistry.load(file);
            fail("Expected invalid configuration to fail startup");
        } catch (KOMEConfigValidationException expected) {
            assertTrue(expected.getMessage().contains("population." + key));
            assertTrue(expected.getMessage().contains("'" + value + "'"));
        }
    }

    private KOMEConfigRegistry.PopulationSettings loadValue(String key, String value)
            throws Exception {
        File file = configFile();
        writeValue(file, key, value);
        KOMEConfigRegistry.load(file);
        return KOMEConfigRegistry.population();
    }

    private void writeValue(File file, String key, String value) {
        Configuration configuration = new Configuration(file);
        configuration.load();
        configuration.get(KOMEConfigRegistry.POPULATION_CATEGORY, key, "default").set(value);
        configuration.save();
    }

    private File configFile() throws Exception {
        return new File(temporaryFolder.newFolder(), "kome.cfg");
    }
}
