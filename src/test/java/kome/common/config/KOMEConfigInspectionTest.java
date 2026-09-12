package kome.common.config;

import cpw.mods.fml.relauncher.FMLInjectionData;
import net.minecraftforge.common.config.Configuration;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KOMEConfigInspectionTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass public static void initializeForgeConfigurationBasePath() throws Exception {
        Field minecraftHome = FMLInjectionData.class.getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, new File(".").getAbsoluteFile());
    }

    @Test public void allCanonicalKeysAndCategoriesArePresentInOrder() throws Exception {
        KOMEConfigRegistry.load(configFile());
        List<KOMEConfigInspection.EffectiveValue> values = KOMEConfigInspection.getAllEffectiveValues();
        Set<String> categories = new HashSet<String>();
        Set<String> keys = new HashSet<String>();
        String previous = "";
        for (KOMEConfigInspection.EffectiveValue value : values) {
            categories.add(value.getCategory());
            assertTrue("duplicate key", keys.add(value.getCategory() + "." + value.getKey()));
            assertTrue(previous.compareTo(value.format()) <= 0);
            previous = value.format();
        }
        assertEquals(new HashSet<String>(Arrays.asList("dailyBatch", "population", "movement",
                "battle", "muster", "siege", "battleSupport", "encirclement", "season")), categories);
        assertEquals(expectedDefaults().keySet(), keys);
        assertEquals(expectedDefaults(), valuesByName(values));
    }

    @Test public void typedCustomValuesAndNotRawStaleFileAreInspected() throws Exception {
        File file = configFile();
        write(file, "population", "populationCapValue", "250");
        write(file, "siege", "gateHpPerApprovedHour", "4.5");
        KOMEConfigRegistry.load(file);
        write(file, "population", "populationCapValue", "999");
        List<KOMEConfigInspection.EffectiveValue> values = KOMEConfigInspection.getAllEffectiveValues();
        assertValue(values, "population.populationCapValue", "250");
        assertValue(values, "siege.gateHpPerApprovedHour", "4.5");
    }

    @Test public void publicCollectionsAreImmutableAndUnknownIsPrecise() {
        assertImmutable(KOMEConfigInspection.getAllEffectiveValues());
        assertImmutable(KOMEConfigInspection.getEffectiveValues("population"));
        try { KOMEConfigInspection.getSupportedCategories().clear(); fail("Expected immutable categories"); }
        catch (UnsupportedOperationException expected) { }
        try { KOMEConfigInspection.getEffectiveValues("unknown"); fail("Expected unknown category failure"); }
        catch (IllegalArgumentException expected) { assertEquals("Unknown KOME config category: unknown", expected.getMessage()); }
    }

    private static void assertImmutable(List<?> values) {
        try { values.clear(); fail("Expected immutable values"); }
        catch (UnsupportedOperationException expected) { }
    }

    private static Map<String, String> valuesByName(
            List<KOMEConfigInspection.EffectiveValue> values) {
        Map<String, String> result = new TreeMap<String, String>();
        for (KOMEConfigInspection.EffectiveValue value : values) {
            result.put(value.getCategory() + "." + value.getKey(), value.getValue());
        }
        return result;
    }

    private static Map<String, String> expectedDefaults() {
        String[] entries = {
                "dailyBatch.localTime=20:00", "dailyBatch.timezone=America/Chicago",
                "population.hoursPerPopulationPoint=10", "population.capturedBuildMultiplier=0.5",
                "population.offlinePopulationCatchUp=true", "population.populationCapEnabled=false",
                "population.populationCapValue=TBD", "population.encirclementPopulationSuppressionEnabled=false",
                "population.unitPopulationCostOverrides={}",
                "movement.footOrMixedTilesPerDay=1", "movement.fullyMountedTilesPerDay=2",
                "battle.responseLevel1Minutes=20", "battle.responseLevel2Minutes=35", "battle.responseLevel3Minutes=50",
                "muster.threatDistanceTiles=2", "muster.budgetDailyPopulationMultiplier=21",
                "muster.arrivalDelayHours=24", "muster.encircledCapitalArrivalPolicy=TBD",
                "siege.gateHpPerApprovedHour=TBD", "siege.normalSegmentSupportMinimumTroops=1",
                "siege.supportFallbackGraceSeconds=15", "siege.preBreachRepair=TBD",
                "siege.postBreachRepairEnabled=false", "siege.exteriorMarginBlocks=192",
                "siege.activeSiegeCheckInWindowMinutes=TBD",
                "battleSupport.mode=CURVE", "battleSupport.fullDamageDistanceBlocks=32",
                "battleSupport.halfDamageDistanceBlocks=48", "battleSupport.lowDamageDistanceBlocks=64",
                "battleSupport.minimumDamageDistanceBlocks=70", "battleSupport.halfDamageMultiplier=0.5",
                "battleSupport.lowDamageMultiplier=0.1", "battleSupport.minimumDamageMultiplier=0.01",
                "battleSupport.hardFallbackDistanceBlocks=48", "battleSupport.openBattleRadiusBlocks=192",
                "encirclement.starvationGraceDays=10", "encirclement.announcedAssaultNoticeHours=48",
                "encirclement.offlineStarvationCatchUp=false", "season.minimumWarSeasonLengthDays=TBD",
                "season.automaticFinaleEnabled=false"
        };
        Map<String, String> result = new TreeMap<String, String>();
        for (String entry : entries) {
            int separator = entry.indexOf('=');
            result.put(entry.substring(0, separator), entry.substring(separator + 1));
        }
        return result;
    }

    private static void assertValue(List<KOMEConfigInspection.EffectiveValue> values,
            String name, String expected) {
        for (KOMEConfigInspection.EffectiveValue value : values) {
            if (name.equals(value.getCategory() + "." + value.getKey())) {
                assertEquals(expected, value.getValue());
                return;
            }
        }
        fail("Missing " + name);
    }

    private void write(File file, String category, String key, String value) {
        Configuration configuration = new Configuration(file);
        configuration.load();
        configuration.get(category, key, "default").set(value);
        configuration.save();
    }

    private File configFile() throws Exception {
        return new File(temporaryFolder.newFolder(), "kome.cfg");
    }
}
