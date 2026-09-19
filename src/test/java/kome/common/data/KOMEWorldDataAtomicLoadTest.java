package kome.common.data;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import static org.junit.Assert.*;

/** Behavioral load transactions. Reflection below observes raw state only; production uses no reflection. */
public class KOMEWorldDataAtomicLoadTest {
    private static final UUID PLAYER = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID UNIT = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test public void factionContainerRejectsMissingWrongOuterAndWrongElementTypes() throws Exception {
        for (int kind = 0; kind < 4; kind++) {
            NBTTagCompound source = validDocument();
            if (kind == 0) source.removeTag("FactionPopulations");
            if (kind == 1) source.setString("FactionPopulations", "not a list");
            if (kind >= 2) {
                NBTTagList list = new NBTTagList();
                list.appendTag(kind == 2 ? new NBTTagString("not a record") : new NBTTagInt(5));
                source.setTag("FactionPopulations", list);
            }
            assertRejectedWithoutPublication(source, "FactionPopulations");
        }
    }

    @Test public void factionRecordsRequireTypedNonnegativeUniqueCanonicalValues() throws Exception {
        for (int kind = 0; kind < 7; kind++) {
            NBTTagCompound source = validDocument();
            NBTTagCompound record = bank("gondor", 2450L);
            if (kind == 0) record.removeTag("Faction");
            if (kind == 1) record.setInteger("Faction", 3);
            if (kind == 2) record.setString("Faction", "  ");
            if (kind == 3) record.removeTag("AvailablePopulationCenti");
            if (kind == 4) record.setInteger("AvailablePopulationCenti", 2450);
            if (kind == 5) record.setLong("AvailablePopulationCenti", -1L);
            source.setTag("FactionPopulations", kind == 6
                ? rows(record, bank(" GONDOR ", 7L)) : rows(record));
            assertRejectedWithoutPublication(source, "FactionPopulations[");
        }
    }

    @Test public void factionListRejectsInconsistentDeclaredAndActualElementTypes() throws Exception {
        // Deliberately corrupt the in-memory NBT representation, not just a typed getter projection.
        for (int kind = 0; kind < 3; kind++) {
            NBTTagList list = rows(bank("gondor", 1L));
            Field contents = null, declaredType = null;
            for (Field field : NBTTagList.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(field.getType())) contents = field;
                if (field.getType() == byte.class) declaredType = field;
            }
            assertNotNull(contents); assertNotNull(declaredType);
            contents.setAccessible(true); declaredType.setAccessible(true);
            if (kind > 0) {
                declaredType.setByte(list, (byte) 8);
                if (kind == 2) ((List<?>) contents.get(list)).clear();
            }
            else {
                @SuppressWarnings("unchecked") List<NBTBase> actual = (List<NBTBase>) contents.get(list);
                actual.set(0, new NBTTagString("wrong actual element"));
            }
            NBTTagCompound source = validDocument();
            source.setTag("FactionPopulations", list);
            assertRejectedWithoutPublication(source, "FactionPopulations");
        }
    }

    @Test public void emptySingleAndMultipleBanksIncludingMaximumLongLoadExactly() {
        for (int count = 0; count <= 2; count++) {
            NBTTagCompound source = validDocument();
            NBTTagList list = new NBTTagList();
            if (count > 0) list.appendTag(bank(" GONDOR ", Long.MAX_VALUE));
            if (count > 1) list.appendTag(bank("rohan", 1L));
            source.setTag("FactionPopulations", list);
            KOMEWorldData target = new KOMEWorldData("target");
            target.readFromNBT(source);
            assertEquals(count, target.factionPopulations.size());
            if (count > 0) assertEquals(Long.MAX_VALUE, target.getFactionPopulationIfPresent("gondor").getAvailablePopulationCenti());
            if (count > 1) assertEquals(1L, target.getFactionPopulationIfPresent("rohan").getAvailablePopulationCenti());
            assertFalse(target.isWriteBlocked());
        }
    }

    @Test public void laterMalformedHiredRecordNeverPublishesTheValidPrefix() throws Exception {
        NBTTagCompound source = validDocument();
        KOMEHiredUnitRecord first = hired(UUID.fromString("30000000-0000-0000-0000-000000000001"));
        NBTTagCompound bad = hired(UUID.fromString("30000000-0000-0000-0000-000000000002")).writeToNBT();
        bad.setString("Owner", "not-a-uuid");
        source.setTag("HiredUnits", rows(first.writeToNBT(), bad));
        assertRejectedWithoutPublication(source, "HiredUnits[1]");
    }

    @Test public void strictUuidFailuresAtEarlyMiddleAndLatePositionsAreAtomic() throws Exception {
        for (String section : new String[] {"Progressions", "PlayerNames", "AdminUnitMapMarkerOptOuts",
                "ActiveRecruitmentTiles", "ArmyMovements", "ArmyCompanies"}) {
            NBTTagCompound source = validDocument();
            NBTTagCompound record = new NBTTagCompound();
            record.setString("Player", "not-a-uuid");
            record.setString("Name", "Bad player");
            record.setString("Faction", "rohan");
            record.setString("Tile", "T100");
            record.setString("Id", "new-rejected-record");
            record.setString("Owner", "not-a-uuid");
            source.setTag(section, rows(record));
            assertRejectedWithoutPublication(source, section + "[0]");
        }
    }

    @Test public void reconciliationOnRejectedCandidateCannotChangeLiveBuildAuditOrManager() throws Exception {
        NBTTagCompound source = validDocument();
        KOMEPlayerBuild build = build();
        build.managerUuid = PLAYER; build.managerName = "Stale manager";
        source.setTag("Builds", rows(build.writeToNBT()));
        NBTTagCompound bad = new NBTTagCompound();
        bad.setString("Id", "late"); bad.setString("Owner", "not-a-uuid");
        source.setTag("ArmyCompanies", rows(bad));
        assertRejectedWithoutPublication(source, "ArmyCompanies[0]");
    }

    @Test public void invalidFirstLoadStaysUninitializedAndWriteBlocked() {
        KOMEWorldData target = new KOMEWorldData("first");
        NBTTagCompound source = validDocument(); source.removeTag("FactionPopulations");
        reject(target, source, "FactionPopulations");
        assertFalse(target.isIntegratedRootInitialized()); assertFalse(target.isDirty());
        assertTrue(target.conquestTiles.isEmpty()); assertTrue(target.isWriteBlocked());
        try { target.initializeIntegratedWorld(); fail("blocked initialization"); }
        catch (IllegalStateException expected) { }
    }

    @Test public void completeValidLoadReplacesAllPriorStateAndKeepsContainerIdentities() throws Exception {
        KOMEWorldData target = populated();
        Map<String, KOMEFactionPopulation> banks = target.factionPopulations;
        Map<UUID, KOMEHiredUnitRecord> units = target.hiredUnits;
        KOMEWarSeasonState season = target.warSeason;
        NBTTagCompound source = stableDocument();
        source.setTag("FactionPopulations", rows(bank("rohan", 24L)));
        KOMEWorldData expected = new KOMEWorldData("expected"); expected.readFromNBT(source);
        target.setDirty(false);
        target.readFromNBT(source);
        assertEquals(freeze(expected), freeze(target));
        assertEquals(expected.isDirty(), target.isDirty());
        assertSame(banks, target.factionPopulations); assertSame(units, target.hiredUnits);
        assertSame(season, target.warSeason);
        assertFalse(target.factionPopulations.containsKey("gondor"));
        assertTrue(target.hiredUnits.isEmpty()); assertTrue(target.builds.isEmpty());
    }

    @Test public void successfulLoadPreservesExistingDirtyAndReconciliationDirtySemantics() {
        NBTTagCompound stable = stableDocument();
        KOMEWorldData clean = new KOMEWorldData("clean");
        clean.readFromNBT(stable); assertFalse(clean.isDirty());
        KOMEWorldData dirty = new KOMEWorldData("dirty");
        dirty.setDirty(true); dirty.readFromNBT(stable); assertTrue(dirty.isDirty());
        KOMEWorldData reconciled = new KOMEWorldData("reconciled");
        NBTTagCompound needsReconciliation = (NBTTagCompound) stable.copy();
        needsReconciliation.setBoolean("ConquestDefaultsInitialized", false);
        reconciled.readFromNBT(needsReconciliation); assertTrue(reconciled.isDirty());
        KOMEWorldData buildReconciled = new KOMEWorldData("manager");
        KOMEPlayerBuild stale = build(); stale.managerUuid = PLAYER; stale.managerName = "Old";
        stable.setTag("Builds", rows(stale.writeToNBT()));
        buildReconciled.readFromNBT(stable);
        assertNull(buildReconciled.builds.get(stale.id).managerUuid);
        assertTrue(buildReconciled.isDirty());
        assertFalse(buildReconciled.centralAudit.isEmpty());
    }

    @Test public void schemaFourSaveSurvivesThreeRealRoundTripsAndDoesNotAliasSourceNBT() throws Exception {
        KOMEWorldData data = populated();
        data.hiredUnits.get(UNIT).stationedEntityData = new NBTTagCompound();
        data.hiredUnits.get(UNIT).stationedEntityData.setString("Sentinel", "original");
        for (int i = 0; i < 3; i++) {
            NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
            NBTTagCompound original = (NBTTagCompound) saved.copy();
            KOMEWorldData loaded = new KOMEWorldData("restart"); loaded.readFromNBT(saved);
            assertEquals(original, saved);
            assertEquals(4, saved.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
            assertEquals(2450L, loaded.getFactionPopulationIfPresent("gondor").getAvailablePopulationCenti());
            assertEquals(Long.valueOf(17L), loaded.populationPayoutRemainders.get("gondor"));
            assertEquals(data.lastPopulationPayoutBoundaryMillis, loaded.lastPopulationPayoutBoundaryMillis);
            assertEquals(1025L, loaded.builds.get("build-sentinel").approvedCentiHours());
            assertEquals(40, loaded.hiredUnits.get(UNIT).populationSpent);
            assertEquals("Sentinel player", loaded.playerNames.get(PLAYER));
            assertEquals(PLAYER, loaded.getFactionKingId("gondor"));
            assertEquals(data.activeRecruitmentTiles, loaded.activeRecruitmentTiles);
            assertEquals(data.armyMovements.keySet(), loaded.armyMovements.keySet());
            assertEquals(data.armyCompanies.keySet(), loaded.armyCompanies.keySet());
            loaded.hiredUnits.get(UNIT).stationedEntityData.setString("Sentinel", "changed after load");
            assertEquals(original, saved);
            data = loaded;
        }
    }

    @Test public void freshEmptyRootStillInitializesAndSaves() {
        KOMEWorldData data = new KOMEWorldData("fresh");
        data.readFromNBT(new NBTTagCompound());
        assertFalse(data.isIntegratedRootInitialized()); assertFalse(data.isDirty());
        assertTrue(data.initializeIntegratedWorld()); assertFalse(data.initializeIntegratedWorld());
        NBTTagCompound saved = new NBTTagCompound(); data.writeToNBT(saved);
        assertTrue(saved.hasKey("FactionPopulations", 9));
        assertEquals(0, saved.getTagList("FactionPopulations", 10).tagCount());
        KOMEWorldData restored = new KOMEWorldData("restored"); restored.readFromNBT(saved);
        assertTrue(restored.isIntegratedRootInitialized());
    }

    @Test public void optionalInvalidRecordsStillSkipAndMalformedAllianceStillQuarantines() {
        NBTTagCompound source = stableDocument();
        NBTTagCompound optional = new NBTTagCompound();
        optional.setString("Player", "invalid"); optional.setString("Faction", "gondor");
        optional.setString("Name", "invalid optional");
        source.setTag("LastKnownPlayerFactions", rows(optional));
        source.setTag("FactionKings", rows(optional));
        source.setTag("Alliances", rows(new NBTTagCompound()));
        KOMEWorldData target = new KOMEWorldData("optional"); target.readFromNBT(source);
        assertTrue(target.lastKnownPlayerFactions.isEmpty());
        assertFalse(target.hasFactionKing("gondor"));
        assertEquals(1, target.quarantinedAllianceRecords.size());
        assertFalse(target.isWriteBlocked()); assertTrue(target.isDirty());
    }

    @Test public void jvmErrorsAreNotConvertedToCorruptWorldRejections() {
        final AssertionError fatal = new AssertionError("injected JVM error");
        NBTTagCompound source = new NBTTagCompound() {
            @Override public NBTBase copy() { throw fatal; }
        };
        source.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 3);
        KOMEWorldData data = new KOMEWorldData("error");
        try { data.readFromNBT(source); fail("must propagate"); }
        catch (AssertionError actual) { assertSame(fatal, actual); }
        assertFalse(data.isWriteBlocked());
    }

    private static void assertRejectedWithoutPublication(NBTTagCompound source, String section) throws Exception {
        for (boolean dirty : new boolean[] {false, true}) {
            KOMEWorldData target = populated();
            target.setDirty(dirty);
            Object before = freeze(target);
            NBTTagCompound original = (NBTTagCompound) source.copy();
            Map<String, KOMEFactionPopulation> banks = target.factionPopulations;
            KOMEFactionPopulation bank = banks.get("gondor");
            KOMEHiredUnitRecord unit = target.hiredUnits.get(UNIT);
            reject(target, source, section);
            assertTrue(target.isWriteBlocked());
            assertEquals(dirty, target.isDirty());
            assertEquals(before, freeze(target)); // every raw live field, not a normalizing serializer
            assertSame(banks, target.factionPopulations); assertSame(bank, banks.get("gondor"));
            assertSame(unit, target.hiredUnits.get(UNIT));
            assertEquals(2450L, bank.getAvailablePopulationCenti());
            assertEquals(original, source);
            try { target.writeToNBT(source); fail("blocked overwrite"); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("write-blocked")); }
            try { target.readFromNBT(validDocument()); fail("blocked second read"); }
            catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("write-blocked")); }
            try { target.markDirty(); fail("blocked dirty"); }
            catch (IllegalStateException expected) { }
            assertEquals(before, freeze(target)); assertEquals(dirty, target.isDirty());
            assertEquals(original, source);
        }
    }

    private static void reject(KOMEWorldData target, NBTTagCompound source, String section) {
        try { target.readFromNBT(source); fail("Expected rejection: " + section); }
        catch (IllegalStateException expected) {
            assertEquals(IllegalStateException.class, expected.getClass());
            assertTrue(expected.getMessage(), expected.getMessage().contains(section));
            assertEquals(expected.getMessage(), target.getLoadFailureReason());
            assertNotNull(expected.getCause());
        }
    }

    /** Test-only deep snapshot, including private collections/scalars and raw tile ownership fields. */
    private static Object freeze(Object value) throws Exception {
        if (value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean || value instanceof UUID || value instanceof Enum) return value;
        if (value instanceof NBTBase) return ((NBTBase) value).copy();
        if (value instanceof Map) {
            Map<Object, Object> copy = new HashMap<Object, Object>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) copy.put(freeze(entry.getKey()), freeze(entry.getValue()));
            return copy;
        }
        if (value instanceof Collection) {
            Collection<Object> copy = value instanceof Set ? new HashSet<Object>() : new ArrayList<Object>();
            for (Object item : (Collection<?>) value) copy.add(freeze(item));
            return copy;
        }
        Map<String, Object> fields = new TreeMap<String, Object>();
        for (Field field : value.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (value instanceof KOMEWorldData && Arrays.asList("writeBlocked", "loadFailureReason", "loadSection").contains(field.getName())) continue;
            field.setAccessible(true); fields.put(field.getName(), freeze(field.get(value)));
        }
        return fields;
    }

    private static NBTTagCompound bank(String faction, long balance) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Faction", faction); entry.setLong("AvailablePopulationCenti", balance); return entry;
    }
    private static NBTTagList rows(NBTTagCompound... records) {
        NBTTagList list = new NBTTagList(); for (NBTTagCompound record : records) list.appendTag(record); return list;
    }
    private static NBTTagCompound validDocument() {
        NBTTagCompound tag = new NBTTagCompound(); new KOMEWorldData("document").writeToNBT(tag); return tag;
    }
    private static NBTTagCompound stableDocument() {
        KOMEWorldData initialized = new KOMEWorldData("stable"); initialized.initializeIntegratedWorld();
        NBTTagCompound tag = new NBTTagCompound(); initialized.writeToNBT(tag); return tag;
    }
    private static KOMEHiredUnitRecord hired(UUID id) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = id; record.owner = PLAYER; record.controller = PLAYER;
        record.cost = 25; record.populationSpent = 40; record.populationOwningFaction = "gondor";
        record.sourceFaction = "gondor"; record.sourceType = KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK;
        return record;
    }
    private static KOMEPlayerBuild build() {
        KOMEPlayerBuild build = new KOMEPlayerBuild();
        build.id = "build-sentinel"; build.tileId = "T100"; build.type = KOMEBuildType.NORMAL;
        build.populationFaction = "gondor"; build.originalBuilderFaction = "gondor";
        KOMEBuildContribution contribution = new KOMEBuildContribution();
        contribution.id = "contribution"; contribution.centiHours = 1025L; contribution.status = KOMEBuildContribution.APPROVED;
        build.contributions.add(contribution); build.appendAudit("sentinel audit"); return build;
    }
    private static KOMEWorldData populated() {
        KOMEWorldData data = new KOMEWorldData("populated");
        data.initializeIntegratedWorld();
        data.grantFactionPopulationCenti("gondor", 2450L);
        data.populationPayoutInitialized = true;
        data.populationPayoutTimezone = "America/Chicago"; data.populationPayoutLocalTime = "20:00";
        data.lastPopulationPayoutBoundaryMillis = java.time.Instant.parse("2026-01-10T02:00:00Z").toEpochMilli();
        data.populationPayoutRemainders.put("gondor", 17L);
        data.populationPayoutLastFailure = "prior transient diagnostic";
        data.builds.put("build-sentinel", build());
        KOMEHiredUnitRecord record = hired(UNIT); record.companyId = "company-sentinel";
        data.hiredUnits.put(UNIT, record);
        data.progressions.put(PLAYER, new KOMEPlayerProgression());
        KOMEConquestTile tile = data.getConquestTile("T100"); tile.claim("gondor", 7L);
        data.activeRecruitmentTiles.put("gondor|" + PLAYER, "T100");
        KOMETileWaypoint waypoint = new KOMETileWaypoint("T100", KOMETileWaypoint.RALLY);
        waypoint.set(0, 1, 2, 3, "sentinel", false);
        data.tileWaypoints.put("T100|rally", waypoint);
        KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge("T100", "T101", KOMEConquestRouteEdge.OPEN);
        data.routeEdges.put(KOMEConquestRouteEdge.key("T100", "T101"), edge);
        KOMEDiplomacyRecord diplomacy = new KOMEDiplomacyRecord("gondor", "rohan");
        diplomacy.relation = KOMEDiplomacyRelation.FRIENDS;
        data.canonicalDiplomacyRecords.put(diplomacy.key(), diplomacy);
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = "order-sentinel"; order.owner = PLAYER; order.status = KOMEArmyMovementOrder.STOPPED;
        data.armyMovements.put(order.id, order);
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "company-sentinel"; company.owner = PLAYER; company.faction = "gondor";
        company.nativeFaction = "gondor"; company.units.add(UNIT);
        data.armyCompanies.put(company.id, company);
        data.playerNames.put(PLAYER, "Sentinel player");
        data.writeFactionKingRecord("gondor", PLAYER, "Sentinel king");
        data.lastKnownPlayerFactions.put(PLAYER, "gondor");
        data.centralAudit.add(new KOMEAuditEntry(9, "TEST", "SENTINEL", "test", "world", "before", ""));
        data.pledgeReleaseAudit.add("sentinel pledge audit"); data.companyDelegationAudit.add("sentinel delegation audit");
        data.allianceAdminAudit.add("sentinel admin audit");
        data.allianceRequirementOverrides.put("sentinel", 7);
        data.warSeason.phase = KOMEWarSeasonState.Phase.WAR; data.warSeason.seasonId = 8;
        data.warSeason.finaleTriggerActor = PLAYER; data.warSeason.finaleTriggerActorName = "Sentinel actor";
        data.movementSecondsPerTileOverride = 17; data.movementTotalSecondsOverride = 23;
        data.movementStepDelaySeconds = 11; data.nextBuildSequence = 19; data.nextWarSequence = 21;
        data.setProgressionEnabled(false);
        return data;
    }
}
