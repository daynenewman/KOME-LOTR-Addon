package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class KOMEWorldDataSchemaTest {
    private static final Set<String> REQUIRED_CURRENT_DEV_ROOT_TAGS = new HashSet<String>(Arrays.asList(
        "KOMEDataSchemaVersion", "AllianceDataSchemaVersion", "BuildDataSchemaVersion",
        "PopulationDataSchemaVersion", "FactionPopulationDataSchemaVersion", "ProgressionEnabled",
        "MovementSecondsPerTileOverride", "MovementTotalSecondsOverride", "MovementStepDelaySeconds",
        "MovementDailyResetTime", "MovementDailyResetTimezone", "NextWarSequence", "NextBuildSequence",
        "WarSeason", "CentralAudit", "AllianceRequirementOverrides", "AllianceQuotaItemOverrides",
        "AllianceAdminAudit", "AllianceMigrationQuarantine", "ConquestDefaultsInitialized", "Populations",
        "FactionPopulations", "PopulationPayoutInitialized", "LastPopulationPayoutBoundaryMillis",
        "PopulationPayoutRemainders", "CanonicalDiplomacyRecords", "Progressions", "PlayerNames",
        "LastKnownPlayerFactions", "PledgeReleaseTombstones", "PledgeReleaseQuarantine",
        "PledgeReleaseLastResults", "PledgeReleaseAudit", "CompanyDelegationAudit",
        "AdminUnitMapMarkerOptOuts", "HiredUnits", "ConquestTiles", "TilePopulations", "Builds",
        "ForeignConstructionPermissions", "PopulationAllocations", "ActiveRecruitmentTiles", "TileWaypoints",
        "TileWaypointLinks", "RouteEdges", "Alliances", "RecoveredLegacyTradePostIds",
        "TradePostMigrationQuarantine", "Wars", "ConquestClaimConfirmations", "ArmyMovements",
        "MovementHistory", "ArmyCompanies", "FactionKings"
    ));

    @Test
    public void freshRootInitializesOnlyThroughTheLifecycleEntryPoint() throws Exception {
        KOMEWorldData data = new KOMEWorldData("test");

        assertFalse(data.isIntegratedRootInitialized());
        assertFalse(data.isDirty());
        assertTrue(data.conquestTiles.isEmpty());

        String source = read(Paths.get("src/main/java/kome/common/data/KOMEWorldData.java"));
        String accessor = between(source, "public static KOMEWorldData get(World world)",
            "/**\n     * Runs once from the authoritative server-tick START lifecycle");
        assertFalse(accessor.contains("initializeIntegratedWorld"));

        assertTrue(data.initializeIntegratedWorld());
        assertTrue(data.isIntegratedRootInitialized());
        assertTrue(data.isDirty());
        assertFalse(data.conquestTiles.isEmpty());

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        assertEquals("KOMEDataSchemaVersion", KOMEWorldData.KOME_DATA_SCHEMA_KEY);
        assertEquals(2, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        assertEquals(KOMEWorldData.KOME_DATA_SCHEMA_VERSION,
            saved.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
    }

    @Test
    public void emptyPersistedRootWaitsForAuthoritativeInitialization() {
        KOMEWorldData data = new KOMEWorldData("test");
        data.readFromNBT(new NBTTagCompound());

        assertFalse(data.isIntegratedRootInitialized());
        assertFalse(data.isDirty());
        assertTrue(data.conquestTiles.isEmpty());
        assertTrue(data.initializeIntegratedWorld());
    }

    @Test
    public void initializationIsThreadSafeAndIdempotent() throws Exception {
        final KOMEWorldData data = new KOMEWorldData("test");
        final int callers = 8;
        final CountDownLatch ready = new CountDownLatch(callers);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(callers);
        final AtomicInteger initialized = new AtomicInteger();
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        try {
            for (int i = 0; i < callers; i++) {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        ready.countDown();
                        try {
                            start.await();
                            if (data.initializeIntegratedWorld()) {
                                initialized.incrementAndGet();
                            }
                        } catch (Throwable problem) {
                            failure.compareAndSet(null, problem);
                        } finally {
                            done.countDown();
                        }
                    }
                });
            }
            assertTrue(ready.await(5L, TimeUnit.SECONDS));
            start.countDown();
            assertTrue(done.await(10L, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }

        assertNull(failure.get());
        assertEquals(1, initialized.get());
        int tileCount = data.conquestTiles.size();
        assertFalse(data.initializeIntegratedWorld());
        assertEquals(tileCount, data.conquestTiles.size());
    }

    @Test
    public void unsupportedRootVersionFailsClosedAndCannotOverwrite() {
        NBTTagCompound unsupported = new NBTTagCompound();
        unsupported.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 1);
        unsupported.setString("Sentinel", "preserve");
        KOMEWorldData data = new KOMEWorldData("test");

        IllegalStateException failure = expectReadFailure(data, unsupported);
        assertTrue(failure.getMessage().contains("schema 1"));
        assertTrue(failure.getMessage().contains("expected 2"));
        assertTrue(data.isWriteBlocked());
        assertFalse(data.isDirty());
        assertTrue(data.conquestTiles.isEmpty());

        expectMutationFailure(data);
        NBTTagCompound target = new NBTTagCompound();
        target.setString("Sentinel", "preserve");
        try {
            data.writeToNBT(target);
            fail("Write-blocked data must not serialize.");
        } catch (IllegalStateException expected) {
            assertEquals("preserve", target.getString("Sentinel"));
            assertFalse(target.hasKey(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        }
    }

    @Test
    public void nonEmptyMissingRootSchemaFailsClosed() {
        NBTTagCompound unsupported = new NBTTagCompound();
        unsupported.setBoolean("PopulationPayoutInitialized", true);
        KOMEWorldData data = new KOMEWorldData("test");

        IllegalStateException failure = expectReadFailure(data, unsupported);
        assertTrue(failure.getMessage().contains("non-empty"));
        assertTrue(failure.getMessage().contains(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertTrue(data.isWriteBlocked());
        assertFalse(data.isDirty());
        expectMutationFailure(data);
    }

    @Test
    public void nestedSchemasRemainIndependentAndCurrentDevStateSurvivesRoundTrip() {
        KOMEWorldData source = new KOMEWorldData("test");
        source.initializeIntegratedWorld();
        source.movementSecondsPerTileOverride = 17;
        source.nextWarSequence = 9;
        source.nextBuildSequence = 11;
        source.populationPayoutInitialized = true;
        source.lastPopulationPayoutBoundaryMillis = 123456789L;
        source.populationPayoutRemainders.put("gondor", Long.valueOf(7L));
        source.grantFactionPopulation("gondor", 42);
        source.warSeason.phase = KOMEWarSeasonState.Phase.WAR;
        KOMEDiplomacyRecord diplomacy = new KOMEDiplomacyRecord("gondor", "rohan");
        diplomacy.relation = KOMEDiplomacyRelation.FRIENDS;
        source.canonicalDiplomacyRecords.put(diplomacy.key(), diplomacy);
        KOMEForeignConstructionPermission permission = new KOMEForeignConstructionPermission();
        permission.tileId = "T100";
        permission.grantingFaction = "gondor";
        permission.granteeFaction = "rohan";
        source.foreignConstructionPermissions.put(permission.key(), permission);

        NBTTagCompound first = new NBTTagCompound();
        source.writeToNBT(first);
        assertTrue(first.func_150296_c().containsAll(REQUIRED_CURRENT_DEV_ROOT_TAGS));
        assertEquals(KOMEWorldData.KOME_DATA_SCHEMA_VERSION,
            first.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertEquals(KOMEWorldData.ALLIANCE_DATA_SCHEMA_VERSION,
            first.getInteger("AllianceDataSchemaVersion"));
        assertEquals(KOMEWorldData.BUILD_DATA_SCHEMA_VERSION, first.getInteger("BuildDataSchemaVersion"));
        assertEquals(KOMEWorldData.POPULATION_DATA_SCHEMA_VERSION,
            first.getInteger("PopulationDataSchemaVersion"));
        assertEquals(KOMEWorldData.FACTION_POPULATION_DATA_SCHEMA_VERSION,
            first.getInteger("FactionPopulationDataSchemaVersion"));

        KOMEWorldData restored = new KOMEWorldData("restored");
        restored.readFromNBT(first);
        assertTrue(restored.isIntegratedRootInitialized());
        assertEquals(17, restored.movementSecondsPerTileOverride);
        assertEquals(9, restored.nextWarSequence);
        assertEquals(11, restored.nextBuildSequence);
        assertEquals(4200L, restored.getFactionPopulationIfPresent("gondor").getAvailablePopulationCenti());
        assertEquals(123456789L, restored.lastPopulationPayoutBoundaryMillis);
        assertEquals(Long.valueOf(7L), restored.populationPayoutRemainders.get("gondor"));
        assertEquals(KOMEWarSeasonState.Phase.WAR, restored.warSeason.phase);
        assertEquals(KOMEDiplomacyRelation.FRIENDS,
            restored.canonicalDiplomacyRecords.get("gondor|rohan").relation);
        assertTrue(restored.foreignConstructionPermissions.containsKey(permission.key()));

        NBTTagCompound second = new NBTTagCompound();
        restored.writeToNBT(second);
        assertTrue(second.func_150296_c().containsAll(REQUIRED_CURRENT_DEV_ROOT_TAGS));
        assertEquals(first.getInteger("BuildDataSchemaVersion"), second.getInteger("BuildDataSchemaVersion"));
        assertEquals(first.getInteger("PopulationDataSchemaVersion"),
            second.getInteger("PopulationDataSchemaVersion"));
        assertEquals(first.getInteger("FactionPopulationDataSchemaVersion"),
            second.getInteger("FactionPopulationDataSchemaVersion"));
    }

    @Test
    public void tileCommandProjectionLookupsDoNotCreateGameplayState() throws Exception {
        KOMEWorldData data = new KOMEWorldData("test");
        data.initializeIntegratedWorld();
        KOMEConquestTile tile = data.getConquestTile("T999");
        tile.claim("gondor", 1L);
        UUID viewer = UUID.randomUUID();
        data.setDirty(false);

        NBTTagCompound before = new NBTTagCompound();
        data.writeToNBT(before);
        int tileCount = data.conquestTiles.size();
        int playerPopulationCount = data.populations.size();

        assertSame(tile, data.getConquestTileIfPresent("T999"));
        assertNull(data.getConquestTileIfPresent("T998"));
        assertFalse(data.canUseRecruitmentTile(viewer, "gondor", "T999"));

        NBTTagCompound after = new NBTTagCompound();
        data.writeToNBT(after);
        assertEquals(tileCount, data.conquestTiles.size());
        assertEquals(playerPopulationCount, data.populations.size());
        assertFalse(data.isDirty());
        assertEquals(before.toString(), after.toString());

        String packet = read(Paths.get(
            "src/main/java/kome/common/network/KOMEPacketConquestOpenCapture.java"));
        String projection = between(packet, "public static void sendTileCommand(EntityPlayerMP player, String requestedTileId, String focusBuildId)",
            "private static void populateBuildAndPoolViews");
        assertTrue(projection.contains("getConquestTileIfPresent"));
        assertFalse(projection.contains("rebuildArmyCompaniesForPlayer"));
        assertFalse(projection.contains("getConquestTile(tileId)"));
    }

    private static IllegalStateException expectReadFailure(KOMEWorldData data, NBTTagCompound source) {
        try {
            data.readFromNBT(source);
            fail("Unsupported root data must fail closed.");
            return null;
        } catch (IllegalStateException expected) {
            return expected;
        }
    }

    private static void expectMutationFailure(KOMEWorldData data) {
        try {
            data.markDirty();
            fail("Write-blocked data must not become dirty.");
        } catch (IllegalStateException expected) {
            assertFalse(data.isDirty());
        }
        try {
            data.initializeIntegratedWorld();
            fail("Write-blocked data must not initialize.");
        } catch (IllegalStateException expected) {
            assertFalse(data.isDirty());
        }
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        assertTrue("Missing start marker: " + start, startIndex >= 0);
        assertTrue("Missing end marker: " + end, endIndex > startIndex);
        return source.substring(startIndex, endIndex);
    }

    private static String read(Path path) throws Exception {
        assertTrue("Missing source: " + path, Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
