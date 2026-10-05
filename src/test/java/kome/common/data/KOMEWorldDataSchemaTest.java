package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import kome.common.siege.KOMEDefensiveGateRef;
import kome.common.siege.KOMENormalSegment;
import kome.common.siege.KOMESiegeAreaRef;
import kome.common.siege.KOMESiegeComplex;
import kome.common.siege.KOMESiegeComplexValidator;
import kome.common.siege.KOMESiegeConnection;
import kome.common.siege.geometry.KOMEPolygon;
import kome.common.siege.geometry.KOMEPolygonPrism;
import kome.common.siege.geometry.KOMEXZPoint;
import kome.common.tactical.KOMEForceDeploymentArea;
import kome.common.tactical.KOMEForceDeploymentAreaValidator;
import kome.common.tactical.KOMETacticalConfiguration;
import kome.common.tactical.KOMETacticalConfigurationCodec;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
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
    @org.junit.Rule public final kome.common.data.KOMETileTestResources geometry =
        new kome.common.data.KOMETileTestResources();

    private static final Set<String> REQUIRED_CURRENT_DEV_ROOT_TAGS = new HashSet<String>(Arrays.asList(
        "KOMEDataSchemaVersion", "TacticalConfiguration", "TacticalConfigurationRequired", "AllianceDataSchemaVersion", "BuildDataSchemaVersion",
        "ConflictDataSchemaVersion", "NextConflictSequence", "ConflictRecords",
        "PopulationDevelopmentDataSchemaVersion", "PopulationDevelopment",
        "MusterDataSchemaVersion", "CivilianMusters",
        "FactionCapitalDataSchemaVersion", "FactionCapitals",
        "FactionPopulationDataSchemaVersion", "ProgressionEnabled",
        "MovementSecondsPerTileOverride", "MovementTotalSecondsOverride", "MovementStepDelaySeconds",
        "PopulationPayoutDataSchemaVersion", "PopulationPayoutTimezone", "PopulationPayoutLocalTime", "NextWarSequence", "NextBuildSequence",
        "NextCompanySequence",
        "WarSeason", "CentralAudit", "AllianceRequirementOverrides", "AllianceQuotaItemOverrides",
        "AllianceAdminAudit", "AllianceMigrationQuarantine", "ConquestDefaultsInitialized",
        "FactionPopulations", "PopulationPayoutInitialized", "LastPopulationPayoutBoundaryMillis",
        "PopulationPayoutRemainders", "CanonicalDiplomacyRecords", "Progressions", "PlayerNames",
        "LastKnownPlayerFactions", "PledgeReleaseTombstones", "PledgeReleaseQuarantine",
        "PledgeReleaseLastResults", "PledgeReleaseAudit", "CompanyDelegationAudit",
        "AdminUnitMapMarkerOptOuts", "HiredUnits", "ConquestTiles", "Builds",
        "ForeignConstructionPermissions", "ActiveRecruitmentTiles", "TileWaypoints",
        "TileWaypointLinks", "RouteEdges", "Alliances", "RecoveredLegacyTradePostIds",
        "TradePostMigrationQuarantine", "Wars", "ConquestClaimConfirmations", "ArmyMovements",
        "MovementHistory", "ArmyCompanies", "FactionKings", "ProgressionNpcRanks", "PublicWaypoints"
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
        assertEquals(8, KOMEWorldData.KOME_DATA_SCHEMA_VERSION);
        assertEquals(1, KOMEWorldData.CONFLICT_DATA_SCHEMA_VERSION);
        assertEquals(4, KOMEWorldData.BUILD_DATA_SCHEMA_VERSION);
        assertEquals(1, KOMEWorldData.POPULATION_DEVELOPMENT_DATA_SCHEMA_VERSION);
        assertEquals(KOMEWorldData.KOME_DATA_SCHEMA_VERSION,
            saved.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertEmptyTacticalSection(saved);
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
        assertTrue(failure.getMessage().contains("schema 6 or 7 -> 8"));
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
        source.populationPayoutTimezone = "America/Chicago";
        source.populationPayoutLocalTime = "20:00";
        source.lastPopulationPayoutBoundaryMillis = java.time.Instant.parse("2026-01-10T02:00:00Z").toEpochMilli();
        source.populationPayoutRemainders.put("gondor", Long.valueOf(7L));
        source.grantFactionPopulationCenti("gondor", 4200L);
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
        assertFalse(first.hasKey("PopulationDataSchemaVersion"));
        assertEquals(KOMEWorldData.FACTION_POPULATION_DATA_SCHEMA_VERSION,
            first.getInteger("FactionPopulationDataSchemaVersion"));

        KOMEWorldData restored = new KOMEWorldData("restored");
        restored.readFromNBT(first);
        assertTrue(restored.isIntegratedRootInitialized());
        assertEquals(17, restored.movementSecondsPerTileOverride);
        assertEquals(9, restored.nextWarSequence);
        assertEquals(11, restored.nextBuildSequence);
        assertEquals(4200L, restored.getFactionPopulationIfPresent("gondor").getAvailablePopulationCenti());
        assertEquals(source.lastPopulationPayoutBoundaryMillis, restored.lastPopulationPayoutBoundaryMillis);
        assertEquals("America/Chicago@20:00", restored.populationPayoutSchedule().signature());
        assertEquals(KOMEWorldData.POPULATION_PAYOUT_DATA_SCHEMA_VERSION, first.getInteger("PopulationPayoutDataSchemaVersion"));
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

        assertSame(tile, data.getConquestTileIfPresent("T999"));
        assertNull(data.getConquestTileIfPresent("T998"));
        assertFalse(data.canUseRecruitmentTile(viewer, "gondor", "T999"));

        NBTTagCompound after = new NBTTagCompound();
        data.writeToNBT(after);
        assertEquals(tileCount, data.conquestTiles.size());
        assertFalse(data.isDirty());
        assertEquals(before.toString(), after.toString());

        String packet = read(Paths.get(
            "src/main/java/kome/common/network/KOMEPacketConquestOpenCapture.java"));
        String projection = between(packet, "public static void sendTileCommand(EntityPlayerMP player, String requestedTileId, String focusBuildId)",
            "private static void populateBuildViews");
        assertTrue(projection.contains("getPublicConquestTile"));
        String worldSource = read(Paths.get("src/main/java/kome/common/data/KOMEWorldData.java"));
        String publicLookup = between(worldSource, "public KOMEConquestTile getPublicConquestTile(",
            "public KOMETileWaypoint getTileWaypoint(");
        assertTrue(publicLookup.contains("getConquestTileIfPresent"));
        assertFalse(publicLookup.contains("markDirty"));
        assertFalse(projection.contains("rebuildArmyCompaniesForPlayer"));
        assertFalse(projection.contains("getConquestTile(tileId)"));
    }

    @Test public void newlyCreatedWorldWritesCurrentSchemaWithAnEmptyTacticalSectionWithoutInitializingOrDirtying() {
        KOMEWorldData data = new KOMEWorldData("empty");
        NBTTagCompound root = saved(data);
        assertEquals(8, root.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertTrue(root.getBoolean(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY));
        assertEquals(4, root.getInteger("BuildDataSchemaVersion"));
        assertEmptyTacticalSection(root);
        assertEquals(0L, data.getTacticalConfigurationSnapshot().getRevision());
        assertFalse(data.isDirty());
        assertFalse(data.isIntegratedRootInitialized());
        assertTrue(data.conquestTiles.isEmpty());
    }

    @Test public void schemaSixUpgradePreservesBuildGateTilePopulationDiplomacyAndCapitalData() {
        KOMEWorldData source = new KOMEWorldData("schema-six"); source.initializeIntegratedWorld();
        KOMEPlayerBuild build = persistenceBuild("B1", KOMEBuildType.DEFENSIVE);
        KOMEDefensiveGateRecord gate = new KOMEDefensiveGateRecord();
        gate.id = build.allocateDefensiveGateRecordId(); gate.createdAtMillis = 17L;
        build.addDefensiveGateRecord(gate);
        source.builds.put(build.id, build);
        source.getConquestTile("T100").claim("gondor", 77L);
        source.grantFactionPopulationCenti("gondor", 2450L);
        source.movementSecondsPerTileOverride = 17;
        source.nextBuildSequence = 8;
        KOMEDiplomacyRecord diplomacy = new KOMEDiplomacyRecord("gondor", "rohan");
        diplomacy.relation = KOMEDiplomacyRelation.FRIENDS;
        source.canonicalDiplomacyRecords.put(diplomacy.key(), diplomacy);
        NBTTagCompound legacy = saved(source);
        legacy.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 6);
        legacy.removeTag("TacticalConfiguration");
        legacy.removeTag(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY);
        legacy.removeTag(KOMEConflictPersistence.SCHEMA_KEY);
        legacy.removeTag(KOMEConflictPersistence.SEQUENCE_KEY);
        legacy.removeTag(KOMEConflictPersistence.RECORDS_KEY);
        NBTTagCompound original = (NBTTagCompound) legacy.copy();
        KOMEWorldData upgraded = new KOMEWorldData("upgraded"); upgraded.readFromNBT(legacy);
        assertFalse(upgraded.isWriteBlocked());
        assertTrue(upgraded.isDirty()); // Schedule the supported root-version upgrade for saving.
        assertEquals(original, legacy);
        assertEquals(1250L, upgraded.getBuild("B1").approvedCentiHours());
        assertEquals(build.writeToNBT(), upgraded.getBuild("B1").writeToNBT());
        assertEquals(source.conquestTiles.get("T100").writeToNBT(), upgraded.conquestTiles.get("T100").writeToNBT());
        assertEquals(2450L, upgraded.getFactionPopulationIfPresent("gondor").getAvailablePopulationCenti());
        assertEquals(KOMEDiplomacyRelation.FRIENDS, upgraded.canonicalDiplomacyRecords.get(diplomacy.key()).relation);
        assertEquals(17, upgraded.movementSecondsPerTileOverride);
        assertEquals(8, upgraded.nextBuildSequence);
        NBTTagCompound current = saved(upgraded);
        assertEquals(8, current.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertEmptyTacticalSection(current);
        for (String key : Arrays.asList("BuildDataSchemaVersion", "Builds", "ConquestTiles", "FactionPopulations",
                "PopulationDevelopment", "FactionCapitals", "CanonicalDiplomacyRecords", "TileWaypoints", "TileWaypointLinks", "RouteEdges")) {
            assertEquals(key, original.getTag(key), current.getTag(key));
        }
        assertTrue(upgraded.getTacticalConfigurationSnapshot().getBuildAssignmentsByBuildId().isEmpty());
        assertEquals(0L, upgraded.getTacticalConfigurationSnapshot().getRevision());
    }

    @Test public void supportedDevRootsWithoutTacticsNeverInferThemFromBuildsOrGates() {
        for (int schema : new int[]{6, 7}) {
            KOMEWorldData target = loadedTacticalWorld();
            NBTTagCompound legacy = saved(target);
            legacy.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, schema);
            legacy.removeTag("TacticalConfiguration");
            legacy.removeTag(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY);
            KOMEPlayerBuild build = persistenceBuild("B1", KOMEBuildType.DEFENSIVE);
            KOMEDefensiveGateRecord gate = new KOMEDefensiveGateRecord(); gate.id = build.allocateDefensiveGateRecordId();
            build.addDefensiveGateRecord(gate);
            NBTTagList builds = new NBTTagList(); builds.appendTag(build.writeToNBT()); legacy.setTag("Builds", builds);
            target.setDirty(false);
            target.readFromNBT(legacy);
            assertNotNull(target.getBuild("B1").getDefensiveGateRecord("G1"));
            NBTTagCompound upgraded = saved(target);
            assertEquals(8, upgraded.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
            assertEmptyTacticalSection(upgraded);
            assertEquals(0L, target.getTacticalConfigurationSnapshot().getRevision());
            assertTrue(target.isDirty());
        }
    }

    @Test public void schemaSixStillRequiresAllExistingMandatorySections() {
        NBTTagCompound legacy = saved(new KOMEWorldData("legacy"));
        legacy.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 6); legacy.removeTag("TacticalConfiguration");
        legacy.removeTag(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY);
        for (String section : Arrays.asList("Builds", "FactionPopulations", "PopulationDevelopment", "FactionCapitals", "CivilianMusters")) {
            NBTTagCompound bad = (NBTTagCompound) legacy.copy(); bad.removeTag(section);
            KOMEWorldData target = new KOMEWorldData("rejected");
            assertTrue(expectReadFailure(target, bad).getMessage().contains(section));
            assertTrue(target.isWriteBlocked()); assertFalse(target.isDirty());
        }
    }

    @Test public void schemasOlderThanSixAndFutureSchemasRetainFailClosedBehavior() {
        for (int version : new int[]{0, 1, 2, 3, 4, 5, 9, Integer.MAX_VALUE}) {
            NBTTagCompound source = saved(new KOMEWorldData("source"));
            source.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, version);
            NBTTagCompound original = (NBTTagCompound) source.copy();
            KOMEWorldData target = new KOMEWorldData("rejected");
            assertTrue(expectReadFailure(target, source).getMessage().contains("schema " + version));
            assertTrue(target.isWriteBlocked()); assertFalse(target.isDirty());
            assertEquals(original, source);
            assertThrows(IllegalStateException.class, () -> target.writeToNBT(source));
            assertEquals(original, source);
        }
    }

    @Test public void currentSchemaRoundTripsIndependentSameTileComplexesAreasAndAssignments() {
        KOMEWorldData data = loadedTacticalWorld();
        KOMETacticalConfiguration tactical = data.getTacticalConfigurationSnapshot();
        assertEquals(2, tactical.listComplexesForTile("T277").size());
        assertEquals(2, tactical.listForceDeploymentAreasForTile("T277").size());
        assertEquals(1, tactical.listForceDeploymentAreasForTile("T278").size());
        assertTrue(tactical.listComplexesForTile("T278").isEmpty());
        assertEquals("WEST", tactical.findAssignedComplexId("b1").get());
        assertEquals("EAST", tactical.findAssignedComplexId("b2").get());
        assertEquals(Arrays.asList("B-MISSING", "B1"), tactical.listAssignedBuildIds("WEST"));
        assertFalse(tactical.findAssignedComplexId("UNASSIGNED").isPresent());
        assertNotSame(tactical.findComplex("WEST").findNormalSegment("a"), tactical.findComplex("EAST").findNormalSegment("a"));
        assertNotEquals(tactical.findComplex("WEST").findNormalSegment("a").getPrism(), tactical.findComplex("EAST").findNormalSegment("a").getPrism());
        assertEquals(1, tactical.findComplex("WEST").getConnectionsFor(KOMESiegeAreaRef.exterior()).size());
        assertEquals(KOMETacticalWorldDataFixtures.section(), saved(data).getCompoundTag("TacticalConfiguration"));
        KOMEWorldData restarted = new KOMEWorldData("restart"); restarted.readFromNBT(saved(data));
        assertEquals(KOMETacticalConfigurationCodec.encode(tactical),
            KOMETacticalConfigurationCodec.encode(restarted.getTacticalConfigurationSnapshot()));
    }

    @Test public void preMergeTacticalSchemaSixPreservesTacticsAndInitializesAbsentNewDevSections() {
        NBTTagCompound legacy = rootWithTactics();
        legacy.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 6);
        legacy.removeTag(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY);
        for (String key : Arrays.asList("MusterDataSchemaVersion", "CivilianMusters", "PublicWaypoints",
                KOMEConflictPersistence.SCHEMA_KEY, KOMEConflictPersistence.SEQUENCE_KEY, KOMEConflictPersistence.RECORDS_KEY))
            legacy.removeTag(key);
        NBTTagCompound original = (NBTTagCompound) legacy.copy();
        KOMEWorldData loaded = new KOMEWorldData("pre-merge-kom25"); loaded.readFromNBT(legacy);
        assertTrue(loaded.isDirty()); assertFalse(loaded.isWriteBlocked());
        assertEquals(original, legacy);
        NBTTagCompound current = saved(loaded);
        assertEquals(8, current.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertEquals(original.getCompoundTag("TacticalConfiguration"), current.getCompoundTag("TacticalConfiguration"));
        assertEquals(731L, loaded.getTacticalConfigurationSnapshot().getRevision());
        assertTrue(loaded.getConflictService().records().isEmpty());
        assertEquals(1L, loaded.getConflictService().getNextConflictSequence());
        assertEquals(0, current.getTagList("CivilianMusters", 10).tagCount());
        assertTrue(current.hasKey("PublicWaypoints", 10));
    }

    @Test public void preMergeTacticalUpgradeDoesNotExcusePartialOrMalformedMusterSections() {
        for (int kind = 0; kind < 3; kind++) {
            NBTTagCompound legacy = rootWithTactics(); legacy.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 6);
            if (kind == 0) legacy.removeTag("CivilianMusters");
            if (kind == 1) legacy.removeTag("MusterDataSchemaVersion");
            if (kind == 2) legacy.setString("CivilianMusters", "corrupt");
            KOMEWorldData target = new KOMEWorldData("invalid-muster");
            assertTrue(expectReadFailure(target, legacy).getMessage().contains("CivilianMusters"));
            assertTrue(target.isWriteBlocked()); assertFalse(target.isDirty());
        }
    }

    @Test public void transitionalSchemaSevenPreservesTacticsAndEveryDevSectionWhileUpgrading() {
        for (boolean markerPresent : new boolean[]{false, true}) {
            NBTTagCompound transitional = rootWithTactics();
            transitional.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 7);
            if (!markerPresent) transitional.removeTag(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY);
            NBTTagCompound original = (NBTTagCompound) transitional.copy();
            KOMEWorldData target = new KOMEWorldData("transitional-seven"); target.readFromNBT(transitional);
            assertTrue(target.isDirty()); assertFalse(target.isWriteBlocked()); assertEquals(original, transitional);
            NBTTagCompound current = saved(target);
            assertEquals(8, current.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
            assertEquals(1, current.getCompoundTag("TacticalConfiguration").getInteger("SchemaVersion"));
            assertEquals(731L, target.getTacticalConfigurationSnapshot().getRevision());
            for (String key : REQUIRED_CURRENT_DEV_ROOT_TAGS) {
                if (!KOMEWorldData.KOME_DATA_SCHEMA_KEY.equals(key)
                        && !KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY.equals(key))
                    assertEquals(key, original.getTag(key), current.getTag(key));
            }
        }
    }

    @Test public void mergedRootsFailClosedOnMissingTacticsOrMalformedRequirementMarker() {
        for (int kind = 0; kind < 3; kind++) {
            NBTTagCompound root = rootWithTactics();
            if (kind == 0) root.removeTag("TacticalConfiguration");
            if (kind == 1) root.setString(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY, "true");
            if (kind == 2) root.setBoolean(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY, false);
            NBTTagCompound original = (NBTTagCompound) root.copy();
            KOMEWorldData target = new KOMEWorldData("invalid-merged-root");
            assertTrue(expectReadFailure(target, root).getMessage().contains("TacticalConfiguration"));
            assertTrue(target.isWriteBlocked()); assertFalse(target.isDirty()); assertEquals(original, root);
        }
    }

    @Test public void combinedSchemaEightPreservesConflictWaypointsAndTacticsAndTransientLockProvider() {
        KOMEWorldData source = loadedTacticalWorld();
        KOMEConflictService.Result started = source.getConflictService().start("T100", KOMEConflictRecord.State.ORDINARY,
            KOMEConflictContracts.ExpectedConflict.absent(), Collections.<KOMEConflictContracts.GarrisonSeed>emptyList(),
            new KOMEConflictContracts.Context(100L, "test", "existing dev conflict"));
        assertTrue(started.reason, started.isSuccess());
        KOMEPublicWaypoint waypoint = source.publicWaypoints.approve(source, "Merge waypoint", KOMETileTestResources.dimension(),
            KOMETileTestResources.x(), 72, KOMETileTestResources.z(), 0, KOMEPublicWaypoint.Source.PUBLIC, "", "console", 100L, null);
        NBTTagCompound root = saved(source), original = (NBTTagCompound) root.copy();
        assertEquals(8, root.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        KOMEWorldData loaded = new KOMEWorldData("combined");
        kome.common.tactical.KOMETacticalActivityLock.Provider provider = (tile, dimension, complex) -> "WEST".equals(complex);
        loaded.setTacticalActivityLockProvider(provider); loaded.readFromNBT(root);
        assertEquals(original, root);
        assertEquals(started.record.getConflictId(), loaded.getConflictService().get("T100").getConflictId());
        assertEquals(waypoint.writeToNBT(), loaded.publicWaypoints.get(waypoint.id).writeToNBT());
        NBTTagCompound rewritten = saved(loaded);
        for (String key : REQUIRED_CURRENT_DEV_ROOT_TAGS)
            assertEquals(key, root.getTag(key), rewritten.getTag(key));
        assertTrue(loaded.isTacticalActivityLocked("T277", 0, "WEST"));
        assertFalse(rewritten.hasKey("tacticalActivityLockProvider"));

        // A real pre-KOM-25 schema-7 root still owns its conflict/waypoint authorities, even without tactics.
        NBTTagCompound legacy = (NBTTagCompound) root.copy(); legacy.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 7);
        legacy.removeTag("TacticalConfiguration"); legacy.removeTag(KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY);
        KOMEWorldData upgraded = new KOMEWorldData("dev-seven"); upgraded.readFromNBT(legacy);
        assertTrue(upgraded.isDirty());
        NBTTagCompound current = saved(upgraded);
        assertEquals(8, current.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY)); assertEmptyTacticalSection(current);
        for (String key : REQUIRED_CURRENT_DEV_ROOT_TAGS) {
            if (!KOMEWorldData.KOME_DATA_SCHEMA_KEY.equals(key) && !"TacticalConfiguration".equals(key)
                    && !KOMEWorldData.TACTICAL_CONFIGURATION_REQUIRED_KEY.equals(key))
                assertEquals(key, root.getTag(key), current.getTag(key));
        }
    }

    @Test public void tacticalStoreAndObjectRevisionsSurviveRootRoundTripsExactlyIncludingMaximumLong() {
        for (long revision : new long[]{0L, 731L, Long.MAX_VALUE}) {
            NBTTagCompound root = rootWithTactics(); root.getCompoundTag("TacticalConfiguration").setLong("Revision", revision);
            KOMEWorldData data = new KOMEWorldData("revisions"); data.readFromNBT(root);
            KOMETacticalConfiguration tactical = data.getTacticalConfigurationSnapshot();
            assertEquals(revision, tactical.getRevision());
            assertEquals(7L, tactical.findComplex("WEST").getRevision());
            assertEquals(19L, tactical.findComplex("EAST").getRevision());
            assertEquals(11L, tactical.findForceDeploymentArea("FIELD").getRevision());
            NBTTagCompound saved = saved(data);
            assertEquals(root.getCompoundTag("TacticalConfiguration"), saved.getCompoundTag("TacticalConfiguration"));
            KOMEWorldData restarted = new KOMEWorldData("restart"); restarted.readFromNBT(saved);
            assertEquals(revision, restarted.getTacticalConfigurationSnapshot().getRevision());
        }
    }

    @Test public void explicitUnresolvedPreferredAreaSurvivesRootPersistence() {
        KOMEWorldData data = loadedTacticalWorld();
        KOMETacticalConfiguration tactical = data.getTacticalConfigurationSnapshot();
        assertEquals("UNRESOLVED", tactical.findComplex("EAST").getPreferredForceDeploymentAreaId().get());
        assertNull(tactical.findForceDeploymentArea("UNRESOLVED"));
        KOMEWorldData restarted = new KOMEWorldData("restart"); restarted.readFromNBT(saved(data));
        assertEquals("UNRESOLVED", restarted.getTacticalConfigurationSnapshot().findComplex("EAST").getPreferredForceDeploymentAreaId().get());
    }

    @Test public void incompleteAndMalformedAuthoredGeometryRemainLoadableAndDiagnosable() {
        KOMETacticalConfiguration tactical = new KOMETacticalConfiguration();
        KOMEPolygonPrism bad = new KOMEPolygonPrism(new KOMEPolygon(Collections.<KOMEXZPoint>emptyList()), 20, 10);
        tactical.addForceDeploymentArea(new KOMEForceDeploymentArea("FIELD", "T277", -1, "Unfinished", bad, 3));
        KOMESiegeConnection unfinished = new KOMESiegeConnection("", KOMESiegeAreaRef.exterior(), KOMESiegeAreaRef.normal(""),
            "", new KOMEDefensiveGateRef("", ""));
        tactical.addComplex(new KOMESiegeComplex("MALFORMED", "T277", -1, 2,
            Collections.singletonList(new KOMENormalSegment("", "Unfinished", bad)), null, null, "MISSING", Collections.singletonList(unfinished)));
        tactical.addComplex(new KOMESiegeComplex("EMPTY", "T277", -1, 0, null, null, null, null, null));
        NBTTagCompound root = saved(new KOMEWorldData("draft"));
        root.setTag("TacticalConfiguration", KOMETacticalConfigurationCodec.encode(tactical));
        KOMEWorldData data = new KOMEWorldData("loaded-draft"); data.readFromNBT(root);
        assertFalse(data.isWriteBlocked());
        KOMETacticalConfiguration loaded = data.getTacticalConfigurationSnapshot();
        assertEquals(bad, loaded.findForceDeploymentArea("FIELD").getPrism());
        assertTrue(loaded.findComplex("EMPTY").getNormalSegments().isEmpty());
        assertEquals(Collections.singletonList(unfinished), loaded.findComplex("MALFORMED").getConnections());
        assertFalse(new KOMESiegeComplexValidator().validate(loaded.findComplex("MALFORMED")).isValid());
        assertFalse(new KOMEForceDeploymentAreaValidator().validate(loaded.findForceDeploymentArea("FIELD")).isValid());
        assertEquals(root.getCompoundTag("TacticalConfiguration"), saved(data).getCompoundTag("TacticalConfiguration"));
    }

    @Test public void worldAwareBuildAndGateProblemsDoNotRejectOrEraseTacticalReferences() {
        NBTTagCompound root = rootWithTactics();
        KOMEPlayerBuild normal = persistenceBuild("B1", KOMEBuildType.NORMAL); normal.active = false;
        KOMEPlayerBuild defensive = persistenceBuild("B2", KOMEBuildType.DEFENSIVE); defensive.active = false;
        NBTTagList builds = new NBTTagList(); builds.appendTag(normal.writeToNBT()); builds.appendTag(defensive.writeToNBT());
        root.setTag("Builds", builds);
        KOMEWorldData data = new KOMEWorldData("unresolved-builds"); data.readFromNBT(root);
        assertFalse(data.getBuild("B1").active); assertFalse(data.getBuild("B1").isDefensive());
        assertEquals("T100", data.getBuild("B1").tileId);
        assertTrue(data.getBuild("B2").getDefensiveGateRecords().isEmpty());
        assertNull(data.getBuild("B-MISSING"));
        assertEquals(3, data.getTacticalConfigurationSnapshot().getBuildAssignmentsByBuildId().size());
        assertEquals(new KOMEDefensiveGateRef("B-MISSING", "gone-gate"),
            data.getTacticalConfigurationSnapshot().findComplex("WEST").getConnections().get(0).getGateRef().get());
        assertEquals(root.getCompoundTag("TacticalConfiguration"), saved(data).getCompoundTag("TacticalConfiguration"));
    }

    @Test public void detachedReadAccessCannotMutateLiveWorldAuthorityOrDirtyState() {
        KOMEWorldData data = loadedTacticalWorld(); data.setDirty(false);
        NBTTagCompound before = saved(data);
        KOMETacticalConfiguration snapshot = data.getTacticalConfigurationSnapshot();
        assertNotSame(snapshot, data.getTacticalConfigurationSnapshot());
        snapshot.reassignBuild("B1", "WEST", "EAST");
        snapshot.unassignBuild("B-MISSING");
        snapshot.removeComplex("WEST");
        snapshot.addForceDeploymentArea(KOMETacticalWorldDataFixtures.area("NEW_AREA", "T277", -1, 100));
        snapshot.replaceComplex(new KOMESiegeComplex("EAST", "T277", -1, 20, null, null, null, null, null));
        snapshot.removeForceDeploymentArea("FIELD");
        assertFalse(data.isDirty());
        assertEquals(before, saved(data));
        assertEquals(731L, data.getTacticalConfigurationSnapshot().getRevision());
        assertEquals("WEST", data.getTacticalConfigurationSnapshot().findAssignedComplexId("B1").get());
        assertThrows(UnsupportedOperationException.class, () -> data.getTacticalConfigurationSnapshot().getComplexesById().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.getTacticalConfigurationSnapshot().findComplex("WEST").getNormalSegments().clear());
        assertThrows(UnsupportedOperationException.class, () -> data.getTacticalConfigurationSnapshot().findForceDeploymentArea("FIELD").getPrism().getPolygon().getVertices().clear());
    }

    @Test public void readSnapshotRemainsDetachedAfterSuccessfulWorldReload() {
        KOMEWorldData data = loadedTacticalWorld();
        KOMETacticalConfiguration snapshot = data.getTacticalConfigurationSnapshot();
        NBTTagCompound root = saved(data);
        root.setTag("TacticalConfiguration", KOMETacticalConfigurationCodec.encode(new KOMETacticalConfiguration()));
        data.readFromNBT(root);
        assertTrue(data.getTacticalConfigurationSnapshot().getComplexesById().isEmpty());
        assertNotNull(snapshot.findComplex("WEST")); assertEquals(731L, snapshot.getRevision());
        snapshot.unassignBuild("B1");
        assertEmptyTacticalSection(saved(data));
    }

    @Test public void repeatedWritesPreserveTacticalRevisionsDirtyStateAndEquivalentRootOutput() {
        for (boolean dirty : new boolean[]{false, true}) {
            KOMEWorldData data = loadedTacticalWorld(); data.setDirty(dirty);
            NBTTagCompound before = KOMETacticalConfigurationCodec.encode(data.getTacticalConfigurationSnapshot());
            NBTTagCompound first = saved(data); NBTTagCompound second = saved(data);
            assertEquals(first, second);
            assertEquals(before, first.getCompoundTag("TacticalConfiguration"));
            assertEquals(before, KOMETacticalConfigurationCodec.encode(data.getTacticalConfigurationSnapshot()));
            assertEquals(dirty, data.isDirty());
        }
    }

    @Test public void tacticalEncodeFailureRejectsBeforeChangingTheDestinationRoot() throws Exception {
        KOMEWorldData data = loadedTacticalWorld(); data.setDirty(false);
        // Test-only invalid private authority simulates an encoder failure without exposing a setter.
        KOMETacticalConfiguration invalid = new KOMETacticalConfiguration();
        invalid.addComplex(new KOMESiegeComplex("BAD", "T277", -1, -1, null, null, null, null, null));
        java.lang.reflect.Field field = KOMEWorldData.class.getDeclaredField("tacticalConfiguration");
        field.setAccessible(true); field.set(data, invalid);
        NBTTagCompound destination = new NBTTagCompound(); destination.setString("Sentinel", "untouched");
        destination.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 5);
        NBTTagCompound before = (NBTTagCompound) destination.copy();
        assertThrows(IllegalArgumentException.class, () -> data.writeToNBT(destination));
        assertEquals(before, destination); assertFalse(data.isDirty());
        assertEquals(invalid.getRevision(), data.getTacticalConfigurationSnapshot().getRevision());
    }

    private static NBTTagCompound saved(KOMEWorldData data) {
        NBTTagCompound root = new NBTTagCompound(); data.writeToNBT(root); return root;
    }

    private static NBTTagCompound rootWithTactics() {
        KOMEWorldData source = new KOMEWorldData("tactical-fixture"); source.initializeIntegratedWorld();
        NBTTagCompound root = saved(source); root.setTag("TacticalConfiguration", KOMETacticalWorldDataFixtures.section()); return root;
    }

    private static KOMEWorldData loadedTacticalWorld() {
        KOMEWorldData data = new KOMEWorldData("loaded-tactical"); data.readFromNBT(rootWithTactics()); return data;
    }

    private static KOMEPlayerBuild persistenceBuild(String id, KOMEBuildType type) {
        KOMEPlayerBuild build = new KOMEPlayerBuild(); build.id = id; build.tileId = "T100"; build.type = type;
        build.populationFaction = "gondor"; build.originalBuilderFaction = "gondor";
        KOMEBuildContribution contribution = new KOMEBuildContribution(); contribution.id = "C1";
        contribution.centiHours = 1250L; contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution); return build;
    }

    private static void assertEmptyTacticalSection(NBTTagCompound root) {
        assertTrue(root.hasKey("TacticalConfiguration", 10));
        NBTTagCompound section = root.getCompoundTag("TacticalConfiguration");
        assertEquals(1, section.getInteger("SchemaVersion"));
        assertEquals(0L, section.getLong("Revision"));
        KOMETacticalConfiguration tactical = KOMETacticalConfigurationCodec.decode(section);
        assertTrue(tactical.getComplexesById().isEmpty());
        assertTrue(tactical.getForceDeploymentAreasById().isEmpty());
        assertTrue(tactical.getBuildAssignmentsByBuildId().isEmpty());
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
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
