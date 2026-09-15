package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KOMEFactionPopulationTest {
    @Test
    public void factionKeysNormalizeAndShareOneBank() {
        KOMEWorldData data = new KOMEWorldData("test");

        KOMEFactionPopulation first = data.getFactionPopulation(" Gondor ");
        data.grantFactionPopulation(" Gondor ", 12);

        assertEquals(1200L, data.getFactionPopulation("gondor").getAvailablePopulationCenti());
        assertTrue(first == data.getFactionPopulation("GONDOR"));
    }

    @Test
    public void factionsHaveIndependentBalances() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPopulationService.grant(data, "gondor", 10);
        KOMEPopulationService.grant(data, "rohan", 20);

        assertEquals(10, KOMEPopulationService.getAvailablePopulation(data, "GONDOR"));
        assertEquals(20, KOMEPopulationService.getAvailablePopulation(data, "rohan"));
    }

    @Test
    public void spendGrantAndInputRulesAreExplicit() {
        KOMEFactionPopulation population = new KOMEFactionPopulation();
        population.grantCenti(1000L);

        assertTrue(population.trySpendCenti(700L));
        assertEquals(300L, population.getAvailablePopulationCenti());
        assertFalse(population.trySpendCenti(400L));
        assertEquals(300L, population.getAvailablePopulationCenti());
        assertTrue(population.trySpendCenti(0L));
        assertEquals(300L, population.getAvailablePopulationCenti());

        assertRejectedSpend(population, -1);
        assertRejectedGrant(population, -1);
        assertEquals(300L, population.getAvailablePopulationCenti());
    }

    @Test
    public void grantOverflowFailsWithoutMutation() {
        KOMEFactionPopulation population = new KOMEFactionPopulation();
        population.setAvailablePopulationCenti(Long.MAX_VALUE);

        try {
            population.grantCenti(1L);
            fail("Expected an overflowing population grant to fail");
        } catch (ArithmeticException expected) {
            assertTrue(expected.getMessage().contains("overflows"));
        }
        assertEquals(Long.MAX_VALUE, population.getAvailablePopulationCenti());
    }

    @Test
    public void factionPopulationPersistenceRoundTripsInDeterministicOrder() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulationCenti("rohan", 725L);
        data.grantFactionPopulationCenti("gondor", 1350L);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);

        assertEquals(KOMEWorldData.FACTION_POPULATION_DATA_SCHEMA_VERSION,
            saved.getInteger("FactionPopulationDataSchemaVersion"));
        NBTTagList entries = saved.getTagList("FactionPopulations", 10);
        assertEquals("gondor", entries.getCompoundTagAt(0).getString("Faction"));
        assertEquals("rohan", entries.getCompoundTagAt(1).getString("Faction"));

        KOMEWorldData restored = new KOMEWorldData("test");
        restored.readFromNBT(saved);
        for (int i = 0; i < 3; i++) {
            assertEquals(1350L, restored.getFactionPopulation("GONDOR").getAvailablePopulationCenti());
            assertEquals(725L, restored.getFactionPopulation("rohan").getAvailablePopulationCenti());
            NBTTagCompound roundTrip = new NBTTagCompound();
            restored.writeToNBT(roundTrip);
            restored = new KOMEWorldData("test-" + i);
            restored.readFromNBT(roundTrip);
        }
    }

    @Test
    public void legacySplitPopulationDoesNotCreateFactionBalances() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEPlayerPopulation legacy = data.getPopulation(java.util.UUID.randomUUID());
        legacy.offensiveTotal = 80;
        legacy.defensiveTotal = 20;

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("test");
        restored.readFromNBT(saved);

        assertTrue(restored.factionPopulations.isEmpty());
        assertEquals(0, KOMEPopulationService.getAvailablePopulation(restored, "gondor"));
        assertTrue(restored.factionPopulations.isEmpty());
    }

    @Test
    public void activePopulationUsesProvidedLivingRecordsOnlyAndDoesNotTouchBank() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulation("gondor", 50);

        KOMEHiredUnitRecord gondor = combatRecord("gondor", 12);
        KOMEHiredUnitRecord fallbackFaction = combatRecord("", 8);
        fallbackFaction.sourceFaction = "gondor";
        KOMEHiredUnitRecord farmhand = combatRecord("gondor", 99);
        farmhand.farmhand = true;
        KOMEHiredUnitRecord otherFaction = combatRecord("rohan", 25);

        assertEquals(20, KOMEPopulationService.getActivePopulation("GONDOR",
            Arrays.asList(gondor, fallbackFaction, farmhand, otherFaction)));
        assertEquals(5000L, data.getFactionPopulation("gondor").getAvailablePopulationCenti());
    }

    @Test
    public void readOnlyAvailablePopulationDoesNotCreateAnEmptyBank() {
        KOMEWorldData data = new KOMEWorldData("test");

        assertEquals(0, KOMEPopulationService.getAvailablePopulation(data, "gondor"));
        assertTrue(data.factionPopulations.isEmpty());
        assertFalse(data.isDirty());
    }

    @Test
    public void authoritativeMutationsDirtyOnlyWhenTheyChangeStateAndPersist() {
        KOMEWorldData data = new KOMEWorldData("test");

        assertTrue(KOMEPopulationService.trySpend(data, "gondor", 0));
        KOMEPopulationService.grant(data, "gondor", 0);
        assertTrue(data.factionPopulations.isEmpty());
        assertFalse(data.isDirty());

        KOMEPopulationService.grant(data, "gondor", 10);
        assertTrue(data.isDirty());
        data.setDirty(false);

        assertFalse(KOMEPopulationService.trySpend(data, "gondor", 11));
        assertEquals(10, KOMEPopulationService.getAvailablePopulation(data, "gondor"));
        assertFalse(data.isDirty());

        assertTrue(KOMEPopulationService.trySpend(data, "gondor", 4));
        assertTrue(data.isDirty());

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData restored = new KOMEWorldData("test");
        restored.readFromNBT(saved);
        assertEquals(6, KOMEPopulationService.getAvailablePopulation(restored, "gondor"));
    }

    @Test
    public void negativePersistedFactionPopulationIsRejected() {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        saved.setInteger("BuildDataSchemaVersion", KOMEWorldData.BUILD_DATA_SCHEMA_VERSION);
        saved.setTag("Builds", new net.minecraft.nbt.NBTTagList());
        saved.setInteger("FactionPopulationDataSchemaVersion", KOMEWorldData.FACTION_POPULATION_DATA_SCHEMA_VERSION);
        NBTTagList entries = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Faction", "gondor");
        entry.setLong("AvailablePopulationCenti", -1L);
        entries.appendTag(entry);
        saved.setTag("FactionPopulations", entries);

        try {
            new KOMEWorldData("test").readFromNBT(saved);
            fail("Expected negative canonical faction population to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("AvailablePopulationCenti"));
        }
    }

    @Test
    public void canonicalCentiApiPreservesHundredthsAndRejectsInvalidMutationAtomically() {
        KOMEWorldData data = new KOMEWorldData("test");
        assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));

        KOMEPopulationService.grantCenti(data, "gondor", 1L);
        KOMEPopulationService.grantCenti(data, "gondor", 2449L);
        assertEquals(2450L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertTrue(KOMEPopulationService.trySpendCenti(data, "gondor", 2450L));
        assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));

        KOMEPopulationService.grantCenti(data, "gondor", 2450L);
        data.setDirty(false);
        assertFalse(KOMEPopulationService.trySpendCenti(data, "gondor", 2451L));
        assertEquals(2450L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertFalse(data.isDirty());
        assertRejectedServiceSpend(data, -1L);
        assertRejectedServiceGrant(data, -1L);
        assertEquals(2450L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
    }

    @Test
    public void canonicalGrantOverflowFailsWithoutMutation() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.setFactionPopulationCenti("gondor", Long.MAX_VALUE);
        data.setDirty(false);
        try {
            KOMEPopulationService.grantCenti(data, "gondor", 1L);
            fail("Expected canonical population overflow");
        } catch (ArithmeticException expected) {
            assertTrue(expected.getMessage().contains("overflows"));
        }
        assertEquals(Long.MAX_VALUE, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertFalse(data.isDirty());
    }

    @Test
    public void nestedFactionPopulationSchemaAndLongCentiFieldAreRequired() {
        NBTTagCompound missingSchema = canonicalRoot();
        NBTTagList entries = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Faction", "gondor");
        entry.setLong("AvailablePopulationCenti", 100L);
        entries.appendTag(entry);
        missingSchema.setTag("FactionPopulations", entries);
        assertUnsupported(missingSchema, "Unsupported faction-population schema 0");

        NBTTagCompound oldSchema = canonicalRoot();
        oldSchema.setInteger("FactionPopulationDataSchemaVersion", 1);
        oldSchema.setTag("FactionPopulations", entries);
        assertUnsupported(oldSchema, "Unsupported faction-population schema");

        NBTTagCompound wrongType = canonicalRoot();
        wrongType.setInteger("FactionPopulationDataSchemaVersion", KOMEWorldData.FACTION_POPULATION_DATA_SCHEMA_VERSION);
        NBTTagList wrongEntries = new NBTTagList();
        NBTTagCompound wrongEntry = new NBTTagCompound();
        wrongEntry.setString("Faction", "gondor");
        wrongEntry.setInteger("AvailablePopulationCenti", 100);
        wrongEntries.appendTag(wrongEntry);
        wrongType.setTag("FactionPopulations", wrongEntries);
        assertUnsupported(wrongType, "must be a long");
    }

    @Test
    public void activePopulationSaturatesAtIntegerMaximum() {
        KOMEHiredUnitRecord first = combatRecord("gondor", Integer.MAX_VALUE);
        KOMEHiredUnitRecord second = combatRecord("gondor", 1);

        assertEquals(Integer.MAX_VALUE, KOMEPopulationService.getActivePopulation("gondor",
            Arrays.asList(first, second)));
    }

    private static KOMEHiredUnitRecord combatRecord(String faction, int cost) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.populationOwningFaction = faction;
        record.cost = cost;
        record.populationSpent = cost;
        return record;
    }

    private static void assertRejectedSpend(KOMEFactionPopulation population, long amount) {
        try {
            population.trySpendCenti(amount);
            fail("Expected negative spend to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("negative"));
        }
    }

    private static void assertRejectedGrant(KOMEFactionPopulation population, long amount) {
        try {
            population.grantCenti(amount);
            fail("Expected negative grant to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("negative"));
        }
    }

    private static void assertRejectedServiceSpend(KOMEWorldData data, long amount) {
        try {
            KOMEPopulationService.trySpendCenti(data, "gondor", amount);
            fail("Expected negative spend to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("negative"));
        }
    }

    private static void assertRejectedServiceGrant(KOMEWorldData data, long amount) {
        try {
            KOMEPopulationService.grantCenti(data, "gondor", amount);
            fail("Expected negative grant to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("negative"));
        }
    }

    private static NBTTagCompound canonicalRoot() {
        NBTTagCompound saved = new NBTTagCompound();
        saved.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        saved.setInteger("BuildDataSchemaVersion", KOMEWorldData.BUILD_DATA_SCHEMA_VERSION);
        saved.setTag("Builds", new net.minecraft.nbt.NBTTagList());
        return saved;
    }

    private static void assertUnsupported(NBTTagCompound saved, String messagePart) {
        KOMEWorldData data = new KOMEWorldData("test");
        try {
            data.readFromNBT(saved);
            fail("Expected unsupported faction population data to fail closed");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(messagePart));
        }
        assertTrue(data.isWriteBlocked());
        assertFalse(data.isDirty());
        assertTrue(data.factionPopulations.isEmpty());
    }
}
