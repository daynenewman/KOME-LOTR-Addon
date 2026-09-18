package kome.common.data;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import kome.common.command.KOMECommandPopulation;
import net.minecraft.command.WrongUsageException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import static org.junit.Assert.*;

/** G retirement gates. Codec/threading and live lifecycle tests remain in their existing suites. */
public class KOMELegacyPopulationRetirementTest {
    private static final String[] RETIRED_TAGS = {
        "Populations", "TilePopulations", "PopulationAllocations", "PopulationDataSchemaVersion"
    };

    @Test public void legacyTypesMapsAndMutatingHelpersAreAbsent() throws Exception {
        for (String name : new String[] {"KOMEPlayerPopulation", "KOMETilePopulation",
                "KOMEPlayerTilePopulationAllocation", "KOMEPopulationGraph"}) {
            assertFalse(name, Files.exists(Paths.get("src/main/java/kome/common/data/" + name + ".java")));
            try { Class.forName("kome.common.data." + name); fail(name); }
            catch (ClassNotFoundException expected) { }
        }
        Set<String> fields = new HashSet<String>();
        for (Field field : KOMEWorldData.class.getDeclaredFields()) fields.add(field.getName());
        for (String name : new String[] {"populations", "tilePopulations", "populationAllocations",
                "activePopulation", "activePopulationCenti", "representedPopulationCenti"})
            assertFalse(name, fields.contains(name));
        Set<String> methods = new HashSet<String>();
        for (Method method : KOMEWorldData.class.getDeclaredMethods()) methods.add(method.getName());
        for (String name : new String[] {"getPopulation", "getPopulationIfPresent", "getTilePopulation",
                "getOrCreateTilePopulationPool", "getOrCreateAllocation", "getAllocation",
                "getNativePopulationTotal", "getEffectiveUsablePopulation", "getKinglessStewardshipAvailable",
                "getKinglessStewardshipGlobalCap", "getKinglessStewardshipReserved", "grantFactionPopulation",
                "releasePopulationForOrdinaryUnitRemoval"}) assertFalse(name, methods.contains(name));
        assertEquals(long.class, KOMEFactionPopulation.class.getDeclaredField("availablePopulationCenti").getType());
        assertEquals(BigInteger.class, KOMEPopulationService.class
            .getMethod("getActivePopulationCenti", KOMEWorldData.class, String.class).getReturnType());
    }

    @Test public void exactBalancesAndDerivedInvestmentSurviveThreeRestartsWithoutLegacyTags() {
        for (long amount : new long[] {0L, 1L, 50L, 1025L, 2450L, Long.MAX_VALUE}) {
            KOMEWorldData data = new KOMEWorldData("fresh");
            assertTrue(data.initializeIntegratedWorld());
            KOMEPopulationService.grantCenti(data, "gondor", amount);
            KOMEHiredUnitRecord combat = unit(data, 40, false);
            combat.cost = 25; // A lower current tactical cost does not lower permanent investment.
            unit(data, 0, true);
            for (int i = 0; i < 3; i++) {
                KOMEPopulationProjection projection = KOMEPopulationProjection.of(data, "gondor");
                assertEquals(amount, projection.availablePopulationCenti);
                assertEquals(BigInteger.valueOf(4000L), projection.activePopulationCenti);
                assertEquals(BigInteger.valueOf(amount).add(BigInteger.valueOf(4000L)), projection.representedPopulationCenti);
                NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag);
                for (String key : RETIRED_TAGS) assertFalse(key, tag.hasKey(key));
                assertFalse(tag.hasKey("ActivePopulationCenti")); assertFalse(tag.hasKey("RepresentedPopulationCenti"));
                assertEquals(3, tag.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
                assertEquals(2, tag.getInteger("FactionPopulationDataSchemaVersion"));
                KOMEWorldData restored = new KOMEWorldData("restart"); restored.readFromNBT(tag); data = restored;
            }
        }
    }

    @Test public void oldRootsAndInjectedLegacySectionsRejectBeforePublishingOrWriting() {
        for (String key : new String[] {"root", "Populations", "TilePopulations", "PopulationAllocations", "PopulationDataSchemaVersion"}) {
            KOMEWorldData source = new KOMEWorldData("source"); source.initializeIntegratedWorld();
            KOMEPopulationService.grantCenti(source, "rohan", 9999L);
            NBTTagCompound unsupported = new NBTTagCompound(); source.writeToNBT(unsupported);
            if ("root".equals(key)) unsupported.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 2);
            else if ("PopulationDataSchemaVersion".equals(key)) unsupported.setInteger(key, 2);
            else unsupported.setTag(key, new NBTTagList());
            String original = unsupported.toString();
            KOMEWorldData target = new KOMEWorldData("target");
            KOMEPopulationService.grantCenti(target, "gondor", 2450L);
            KOMEHiredUnitRecord sentinel = unit(target, 40, false);
            KOMEFactionPopulation bank = target.getFactionPopulationIfPresent("gondor");
            target.setDirty(false);
            try { target.readFromNBT(unsupported); fail(key); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("Reset")); }
            assertTrue(target.isWriteBlocked()); assertFalse(target.isDirty());
            assertFalse(target.isIntegratedRootInitialized());
            assertSame(bank, target.getFactionPopulationIfPresent("gondor")); assertEquals(2450L, bank.getAvailablePopulationCenti());
            assertNull(target.getFactionPopulationIfPresent("rohan")); assertSame(sentinel, target.hiredUnits.get(sentinel.entity));
            try { target.initializeIntegratedWorld(); fail("Rejected load cannot initialize"); }
            catch (IllegalStateException expected) { }
            try { target.markDirty(); fail("Rejected load cannot dirty"); }
            catch (IllegalStateException expected) { }
            try { target.writeToNBT(unsupported); fail("Rejected load cannot overwrite source"); }
            catch (IllegalStateException expected) { }
            assertEquals(original, unsupported.toString()); assertFalse(target.isDirty());
        }
    }

    @Test public void terminalRemovalDisbandAndPledgeCleanupKeepInvestmentPermanentlySpent() {
        for (String reason : new String[] {"Unit died", "Unit dismissed", "Company disbanded"}) {
            KOMEWorldData data = new KOMEWorldData("terminal");
            KOMEPopulationService.grantCenti(data, "gondor", 2450L);
            KOMEHiredUnitRecord unit = unit(data, 40, false);
            assertSame(unit, data.removeTerminatedHiredUnit(unit.entity, reason));
            assertNull(data.removeTerminatedHiredUnit(unit.entity, reason));
            assertEquals(2450L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
            assertEquals(BigInteger.ZERO, KOMEPopulationProjection.of(data, "gondor").activePopulationCenti);
        }
        KOMEWorldData data = new KOMEWorldData("pledge"); KOMEPopulationService.grantCenti(data, "gondor", 2450L);
        KOMEHiredUnitRecord unit = unit(data, 40, false);
        unit.sourceType = KOMEHiredUnitRecord.SOURCE_TILE_ALLOCATION;
        unit.allocationTileId = "T100"; unit.allocationFaction = "gondor"; unit.allocationPlayer = unit.owner;
        KOMEPledgeReleaseService.release(data, unit.owner, "Tester", "gondor", "", 10L, "Pledge departed");
        KOMEPledgeReleaseService.release(data, unit.owner, "Tester", "gondor", "", 11L, "Retry");
        assertEquals(2450L, KOMEPopulationService.getAvailablePopulationCenti(data, "gondor"));
        assertEquals(BigInteger.ZERO, KOMEPopulationProjection.of(data, "gondor").activePopulationCenti);
        KOMEPledgeReleaseTombstone tombstone = data.pledgeReleaseTombstones.get(unit.entity);
        assertNotNull(tombstone); assertTrue(tombstone.populationReturned);
        assertEquals(KOMEHiredUnitRecord.SOURCE_TILE_ALLOCATION, tombstone.fundingSource);
        NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag);
        KOMEWorldData restored = new KOMEWorldData("restart"); restored.readFromNBT(tag);
        assertTrue(restored.pledgeReleaseTombstones.get(unit.entity).populationReturned);
        assertEquals(2450L, KOMEPopulationService.getAvailablePopulationCenti(restored, "gondor"));
    }

    @Test public void obsoleteCommandsAndCompletionsCannotReachPopulationMutation() throws Exception {
        KOMECommandPopulation command = new KOMECommandPopulation();
        for (String[] args : new String[][] {{"add"}, {"remove"}, {"set"}, {"allocate"}, {"unallocate"}, {"reserve"},
                {"tile", "T100", "add", "25"}}) {
            try { command.processCommand(null, args); fail(Arrays.toString(args)); }
            catch (WrongUsageException expected) { }
        }
        assertEquals(new HashSet<String>(Arrays.asList("get", "gui", "units", "tile", "faction", "rate")),
            new HashSet<Object>(command.addTabCompletionOptions(null, new String[] {""})));
        String gui = source("src/main/java/kome/client/gui/KOMEGuiConquestCapture.java");
        for (String forbidden : new String[] {"ID_TAB_ALLOCATIONS", "PopulationPoolView", "sendPopulationUpdate",
                "sendAllocationUpdate", "drawPopulationPoolGraph", "allocationPlayerField", "if (false)"})
            assertFalse(forbidden, gui.contains(forbidden));
        assertTrue(gui.contains("drawCanonicalPopulationTab("));
        assertTrue(gui.contains("initBuildControls();")); assertTrue(gui.contains("private int activeTab;"));
    }

    @Test public void productionHasNoRetiredLedgerOrWholePopulationEntryPoints() throws Exception {
        try (java.util.stream.Stream<java.nio.file.Path> paths = Files.walk(Paths.get("src/main/java/kome"))) {
            for (java.nio.file.Path path : (Iterable<java.nio.file.Path>) paths.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                for (String forbidden : new String[] {"KOMEPlayerPopulation", "KOMETilePopulation",
                        "KOMEPlayerTilePopulationAllocation", "KOMEPacketTilePopulationUpdate", "KOMEPacketTileAllocationUpdate",
                        "getAvailablePopulation(", "getFixedUnitsPerDay(", "getHoursPerPopulationPoint()",
                        "getCapturedBuildMultiplier()", "getPopulationCapValue()", "if (false)"})
                    assertFalse(path + ": " + forbidden, text.contains(forbidden));
            }
        }
    }

    private static KOMEHiredUnitRecord unit(KOMEWorldData data, int invested, boolean farmhand) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID(); record.owner = UUID.randomUUID(); record.farmhand = farmhand;
        record.populationSpent = invested; record.cost = invested;
        record.sourceFaction = "gondor"; record.populationOwningFaction = "gondor";
        record.sourceType = KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK;
        data.hiredUnits.put(record.entity, record); return record;
    }
    private static String source(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
