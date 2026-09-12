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

        assertEquals(12, data.getFactionPopulation("gondor").getAvailablePopulation());
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
        population.grant(10);

        assertTrue(population.trySpend(7));
        assertEquals(3, population.getAvailablePopulation());
        assertFalse(population.trySpend(4));
        assertEquals(3, population.getAvailablePopulation());
        assertTrue(population.trySpend(0));
        assertEquals(3, population.getAvailablePopulation());

        assertRejectedSpend(population, -1);
        assertRejectedGrant(population, -1);
        assertEquals(3, population.getAvailablePopulation());
    }

    @Test
    public void grantOverflowFailsWithoutMutation() {
        KOMEFactionPopulation population = new KOMEFactionPopulation();
        population.setAvailablePopulation(Integer.MAX_VALUE);

        try {
            population.grant(1);
            fail("Expected an overflowing population grant to fail");
        } catch (ArithmeticException expected) {
            assertTrue(expected.getMessage().contains("overflows"));
        }
        assertEquals(Integer.MAX_VALUE, population.getAvailablePopulation());
    }

    @Test
    public void factionPopulationPersistenceRoundTripsInDeterministicOrder() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.grantFactionPopulation("rohan", 7);
        data.grantFactionPopulation("gondor", 13);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);

        assertEquals(KOMEWorldData.FACTION_POPULATION_DATA_SCHEMA_VERSION,
            saved.getInteger("FactionPopulationDataSchemaVersion"));
        NBTTagList entries = saved.getTagList("FactionPopulations", 10);
        assertEquals("gondor", entries.getCompoundTagAt(0).getString("Faction"));
        assertEquals("rohan", entries.getCompoundTagAt(1).getString("Faction"));

        KOMEWorldData restored = new KOMEWorldData("test");
        restored.readFromNBT(saved);
        assertEquals(13, restored.getFactionPopulation("GONDOR").getAvailablePopulation());
        assertEquals(7, restored.getFactionPopulation("rohan").getAvailablePopulation());
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
        assertEquals(50, data.getFactionPopulation("gondor").getAvailablePopulation());
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
        NBTTagList entries = new NBTTagList();
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Faction", "gondor");
        entry.setInteger("AvailablePopulation", -1);
        entries.appendTag(entry);
        saved.setTag("FactionPopulations", entries);

        try {
            new KOMEWorldData("test").readFromNBT(saved);
            fail("Expected negative canonical faction population to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Available population"));
        }
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
        return record;
    }

    private static void assertRejectedSpend(KOMEFactionPopulation population, int amount) {
        try {
            population.trySpend(amount);
            fail("Expected negative spend to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("negative"));
        }
    }

    private static void assertRejectedGrant(KOMEFactionPopulation population, int amount) {
        try {
            population.grant(amount);
            fail("Expected negative grant to fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("negative"));
        }
    }
}
