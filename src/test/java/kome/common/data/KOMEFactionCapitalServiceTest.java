package kome.common.data;

import lotr.common.LOTRDimension;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.*;

public class KOMEFactionCapitalServiceTest {
    @org.junit.Rule public final kome.common.data.KOMETileTestResources geometry =
        new kome.common.data.KOMETileTestResources();

    private static final List<String> EXPECTED = Arrays.asList(
        "hobbit", "bree", "dunedain", "bluemountains", "highelves", "gundabad",
        "angmar", "woodelf", "dolguldur", "dale", "durinsfolk", "lothlorien",
        "dunland", "isengard", "fangorn", "rohan", "gondor", "mordor",
        "dorwinion", "rhudel", "harad", "morwaith", "taurethrim", "halftroll");

    @Test public void approvedDefaultsAreExactCompleteAndResolved() {
        assertEquals(EXPECTED, KOMEAlliance.allFactionKeys());
        List<KOMEFactionCapitalDefaults.Definition> definitions =
            KOMEFactionCapitalDefaults.definitions();
        assertEquals(24, definitions.size());
        Map<String, String> tiles = new LinkedHashMap<String, String>();
        for (KOMEFactionCapitalDefaults.Definition definition : definitions) {
            assertEquals(definition.factionId,
                KOMEAlliance.normalizeFactionKey(definition.factionId));
            assertEquals("middle_earth", definition.dimension);
            assertNotNull(definition.resolveAndValidate());
            tiles.put(definition.factionId, definition.expectedTileId);
        }
        assertEquals("T320", tiles.get("dunland"));
        assertEquals("T516", tiles.get("morwaith"));
        assertEquals("T315", tiles.get("isengard"));
        assertEquals("T292", tiles.get("rhudel"));
        assertFalse(tiles.containsKey("wanderer"));
    }

    @Test public void defaultCandidateValidationIsAllOrNothing() {
        List<KOMEFactionCapitalDefaults.Definition> valid =
            new ArrayList<KOMEFactionCapitalDefaults.Definition>(
                KOMEFactionCapitalDefaults.definitions());
        List<KOMEFactionCapitalDefaults.Definition> missing =
            new ArrayList<KOMEFactionCapitalDefaults.Definition>(valid);
        missing.remove(0);
        rejectsDefaults(missing, "exactly");
        List<KOMEFactionCapitalDefaults.Definition> duplicate =
            new ArrayList<KOMEFactionCapitalDefaults.Definition>(valid);
        duplicate.add(valid.get(0));
        rejectsDefaults(duplicate, "Duplicate");
        List<KOMEFactionCapitalDefaults.Definition> unsupported =
            new ArrayList<KOMEFactionCapitalDefaults.Definition>(valid);
        unsupported.set(0, new KOMEFactionCapitalDefaults.Definition(
            "wanderer", "MICHEL_DELVING", "middle_earth", "T179"));
        rejectsDefaults(unsupported, "Unsupported");
        KOMEWorldData data = new KOMEWorldData("atomic");
        Map<String, KOMEFactionCapitalRecord> incomplete =
            KOMEFactionCapitalDefaults.metadataFixture(1L);
        incomplete.remove("hobbit");
        try {
            KOMEFactionCapitalService.initializeFresh(data, incomplete);
            fail("Expected incomplete initialization rejection");
        } catch (IllegalArgumentException expected) {
            assertNull(KOMEFactionCapitalService.getCapital(data, "gondor"));
            assertTrue(data.centralAudit.isEmpty());
            assertFalse(data.isDirty());
        }
    }

    private static void rejectsDefaults(
            List<KOMEFactionCapitalDefaults.Definition> definitions, String text) {
        try {
            KOMEFactionCapitalDefaults.validateComplete(definitions);
            fail("Expected strict default rejection");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains(text));
        }
    }

    @Test public void schemaFiveRoundTripsCompleteCapitalAuthority() {
        KOMEWorldData source = initialized();
        NBTTagCompound saved = new NBTTagCompound();
        source.writeToNBT(saved);
        assertEquals(5, saved.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertEquals(1, saved.getInteger("FactionCapitalDataSchemaVersion"));
        assertEquals(24, saved.getTagList("FactionCapitals", 10).tagCount());
        KOMEWorldData restored = new KOMEWorldData("restored");
        restored.readFromNBT(saved);
        for (String faction : EXPECTED) {
            KOMEFactionCapitalRecord before =
                KOMEFactionCapitalService.getCapital(source, faction);
            KOMEFactionCapitalRecord after =
                KOMEFactionCapitalService.getCapital(restored, faction);
            assertNotNull(after);
            assertEquals(before.getCapitalTileId(), after.getCapitalTileId());
            assertEquals(before.getDeploymentDimensionId(), after.getDeploymentDimensionId());
            assertEquals(before.getDeploymentX(), after.getDeploymentX(), 0.0D);
            assertEquals(before.getDeploymentY(), after.getDeploymentY(), 0.0D);
            assertEquals(before.getDeploymentZ(), after.getDeploymentZ(), 0.0D);
        }
    }

    @Test public void lowercaseTileCanonicalizesAndRecordRoundTrips() {
        KOMEFactionCapitalRecord record = new KOMEFactionCapitalRecord(
            "Gondor", "t388", LOTRDimension.MIDDLE_EARTH.dimensionID,
            78016.5D, 80.0D, 66240.5D, 7L, "ADMIN_RELOCATION_HERE", "operator");
        assertEquals("gondor", record.getFactionId());
        assertEquals("T388", record.getCapitalTileId());
        KOMEFactionCapitalRecord restored =
            KOMEFactionCapitalRecord.readFromNBT(record.writeToNBT());
        assertEquals(record.getCapitalTileId(), restored.getCapitalTileId());
        assertEquals(record.getDeploymentX(), restored.getDeploymentX(), 0.0D);
    }

    @Test public void schemaThreeAndMalformedCapitalSectionsFailClosed() {
        NBTTagCompound schemaThree = saved();
        schemaThree.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY, 3);
        rejectsLoad(schemaThree, "Unsupported KOME world-data schema 3");
        NBTTagCompound missing = saved();
        missing.removeTag("FactionCapitals");
        rejectsLoad(missing, "capital");
        NBTTagCompound duplicate = saved();
        NBTTagList capitals = duplicate.getTagList("FactionCapitals", 10);
        capitals.appendTag(capitals.getCompoundTagAt(0).copy());
        rejectsLoad(duplicate, "Duplicate");
        NBTTagCompound mismatch = saved();
        mismatch.getTagList("FactionCapitals", 10).getCompoundTagAt(0)
            .setString("Key", "mordor");
        rejectsLoad(mismatch, "does not match");
    }

    @Test public void deploymentMetadataRejectsUnsafeStrategicInputs() {
        assertFalse(KOMEStrategicDeploymentResolver.validateMetadata(
            "T388", LOTRDimension.MIDDLE_EARTH.dimensionID,
            Double.NaN, 80.0D, 66240.5D).valid);
        assertFalse(KOMEStrategicDeploymentResolver.validateMetadata(
            "T388", LOTRDimension.MIDDLE_EARTH.dimensionID + 1,
            78016.5D, 80.0D, 66240.5D).valid);
        assertFalse(KOMEStrategicDeploymentResolver.validateMetadata(
            "T388", LOTRDimension.MIDDLE_EARTH.dimensionID,
            78016.5D, 0.0D, 66240.5D).valid);
        assertFalse(KOMEStrategicDeploymentResolver.validateMetadata(
            "", LOTRDimension.MIDDLE_EARTH.dimensionID,
            78016.5D, 80.0D, 66240.5D).valid);
        assertFalse(KOMEStrategicDeploymentResolver.validateMetadata(
            "T388", LOTRDimension.MIDDLE_EARTH.dimensionID,
            97728.5D, 80.0D, 59712.5D).valid);
    }

    @Test public void lookupsAreReadOnlyAndCaptureDoesNotMoveCapital() {
        KOMEWorldData data = initialized();
        NBTTagCompound before = new NBTTagCompound();
        data.writeToNBT(before);
        KOMEFactionCapitalRecord gondor =
            KOMEFactionCapitalService.getCapital(data, "GONDOR");
        assertEquals("T388", gondor.getCapitalTileId());
        assertTrue(KOMEFactionCapitalService.isCapitalTile(data, "gondor", "t388"));
        assertEquals(Arrays.asList("gondor"),
            KOMEFactionCapitalService.getCapitalFactionsForTile(data, "T388"));
        assertNull(KOMEFactionCapitalService.getCapital(data, "wanderer"));
        NBTTagCompound afterLookup = new NBTTagCompound();
        data.writeToNBT(afterLookup);
        assertEquals(before, afterLookup);
        KOMEConquestTile captured = data.getConquestTile("T388");
        captured.setCurrentRulingFaction("mordor");
        assertEquals("mordor", captured.projectRulingFaction());
        assertEquals("T388",
            KOMEFactionCapitalService.getCapitalTileId(data, "gondor"));
    }

    @Test public void warRelocationBlockCoversBothSidesAndEndingButNotEnded() {
        KOMEWorldData data = initialized();
        KOMEWar active = war("active", KOMEWar.ACTIVE, "gondor", "mordor");
        data.wars.put(active.id, active);
        assertTrue(KOMEFactionCapitalService.hasRelocationBlockingWar(data, "gondor"));
        assertTrue(KOMEFactionCapitalService.hasRelocationBlockingWar(data, "mordor"));
        assertFalse(KOMEFactionCapitalService.hasRelocationBlockingWar(data, "rohan"));
        KOMEFactionCapitalRecord unchanged =
            KOMEFactionCapitalService.getCapital(data, "gondor");
        KOMEFactionCapitalService.RelocationResult blocked =
            KOMEFactionCapitalService.relocateValidated(data, "gondor", "T378",
                new KOMEStrategicDeploymentResolver.Anchor(
                    LOTRDimension.MIDDLE_EARTH.dimensionID,
                    97728.5D, 80.0D, 59712.5D),
                true, "operator", "ADMIN_RELOCATION_HERE", 3L);
        assertFalse(blocked.success);
        assertSame(unchanged, KOMEFactionCapitalService.getCapital(data, "gondor"));
        active.status = KOMEWar.ENDING;
        assertTrue(KOMEFactionCapitalService.hasRelocationBlockingWar(data, "gondor"));
        assertTrue(KOMEFactionCapitalService.hasRelocationBlockingWar(data, "mordor"));
        active.status = KOMEWar.ENDED;
        assertFalse(KOMEFactionCapitalService.hasRelocationBlockingWar(data, "gondor"));
        KOMEWar second = war("second", KOMEWar.ACTIVE, "rohan", "gondor");
        data.wars.put(second.id, second);
        assertTrue(KOMEFactionCapitalService.hasRelocationBlockingWar(data, "gondor"));
        second.status = "STALE";
        assertTrue(KOMEFactionCapitalService.hasRelocationBlockingWar(data, "halftroll"));
    }

    @Test public void validatedRelocationIsAtomicDirtyAuditedAndWaypointIndependent() {
        KOMEWorldData data = initialized();
        KOMEFactionCapitalRecord original =
            KOMEFactionCapitalService.getCapital(data, "gondor");
        KOMEStrategicDeploymentResolver.Anchor custom =
            new KOMEStrategicDeploymentResolver.Anchor(
                LOTRDimension.MIDDLE_EARTH.dimensionID, 97728.5D, 80.0D, 59712.5D);
        data.setDirty(false);
        KOMEFactionCapitalService.RelocationResult denied =
            KOMEFactionCapitalService.relocateValidated(data, "gondor", "T378",
                custom, false, "player", "ADMIN_RELOCATION_HERE", 10L);
        assertFalse(denied.success);
        assertSame(original, KOMEFactionCapitalService.getCapital(data, "gondor"));
        KOMEFactionCapitalService.RelocationResult moved =
            KOMEFactionCapitalService.relocateValidated(data, "gondor", "T378",
                custom, true, "operator", "ADMIN_RELOCATION_HERE", 11L);
        assertTrue(moved.success);
        assertEquals("T378", moved.newRecord.getCapitalTileId());
        assertEquals("ADMIN_RELOCATION_HERE", moved.newRecord.getSource());
        assertTrue(data.isDirty());
        KOMEAuditEntry audit = data.centralAudit.get(data.centralAudit.size() - 1);
        assertEquals("CAPITAL", audit.domain);
        assertEquals("RELOCATE", audit.action);
        assertEquals("operator", audit.actor);
        assertTrue(audit.details.contains("old=T388"));
        assertTrue(audit.details.contains("new=T378"));
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        KOMEWorldData restarted = new KOMEWorldData("restarted");
        restarted.readFromNBT(saved);
        assertEquals("T378",
            KOMEFactionCapitalService.getCapitalTileId(restarted, "gondor"));
    }

    @Test public void initializationAuditsAndPublicPacketsNeverExposeAnchors() {
        KOMEWorldData data = initialized();
        int initialized = 0;
        for (KOMEAuditEntry entry : data.centralAudit) {
            if ("CAPITAL".equals(entry.domain) && "INITIALIZE".equals(entry.action)) {
                initialized++;
                assertEquals("SERVER", entry.actor);
            }
        }
        assertEquals(24, initialized);
        kome.common.network.KOMEPacketConquestData packet =
            new kome.common.network.KOMEPacketConquestData(data);
        io.netty.buffer.ByteBuf wire = io.netty.buffer.Unpooled.buffer();
        packet.toBytes(wire);
        kome.common.network.KOMEPacketConquestData decoded =
            new kome.common.network.KOMEPacketConquestData();
        decoded.fromBytes(wire);
        packet = decoded;
        NBTTagList publicRows = packet.data.getTagList("FactionCapitals", 10);
        assertEquals(24, publicRows.tagCount());
        for (int i = 0; i < publicRows.tagCount(); i++) {
            NBTTagCompound row = publicRows.getCompoundTagAt(i);
            assertTrue(row.hasKey("Faction"));
            assertTrue(row.hasKey("Tile"));
            assertFalse(row.hasKey("DeploymentX"));
            assertFalse(row.hasKey("DeploymentY"));
            assertFalse(row.hasKey("DeploymentZ"));
            assertFalse(row.hasKey("Actor"));
        }
        kome.common.network.KOMEPacketConquestCaptureGui tile =
            new kome.common.network.KOMEPacketConquestCaptureGui();
        tile.tileId = "T388";
        tile.ownerFaction = "mordor";
        tile.capitalFactions.add("gondor");
        io.netty.buffer.ByteBuf tileWire = io.netty.buffer.Unpooled.buffer();
        tile.toBytes(tileWire);
        kome.common.network.KOMEPacketConquestCaptureGui tileDecoded =
            new kome.common.network.KOMEPacketConquestCaptureGui();
        tileDecoded.fromBytes(tileWire);
        assertEquals("mordor", tileDecoded.ownerFaction);
        assertEquals(Arrays.asList("gondor"), tileDecoded.capitalFactions);
    }

    @Test public void productionUsesLiveYAndDocumentsNeutralMusterBoundary() throws Exception {
        String defaults = new String(Files.readAllBytes(Paths.get(
            "src/main/java/kome/common/data/KOMEFactionCapitalDefaults.java")),
            StandardCharsets.UTF_8);
        assertTrue(defaults.contains("waypoint.getYCoord(middleEarth, x, z)"));
        assertFalse(defaults.contains("getYCoordSaved"));
        String service = new String(Files.readAllBytes(Paths.get(
            "src/main/java/kome/common/data/KOMEFactionCapitalService.java")),
            StandardCharsets.UTF_8);
        assertTrue(service.contains("before Encirclement"));
        assertTrue(service.contains("after Encirclement starts"));
        assertFalse(service.contains("Siege Complex"));
    }

    private static KOMEWar war(String id, String status, String sideOne, String sideTwo) {
        KOMEWar war = new KOMEWar();
        war.id = id; war.status = status;
        war.sideOneFactions.add(sideOne);
        war.sideTwoFactions.add(sideTwo);
        return war;
    }

    private static KOMEWorldData initialized() {
        KOMEWorldData data = new KOMEWorldData("capital-test");
        assertTrue(data.initializeIntegratedWorld());
        return data;
    }

    private static NBTTagCompound saved() {
        NBTTagCompound saved = new NBTTagCompound();
        initialized().writeToNBT(saved);
        return saved;
    }

    private static void rejectsLoad(NBTTagCompound nbt, String text) {
        KOMEWorldData data = new KOMEWorldData("invalid");
        try {
            data.readFromNBT(nbt);
            fail("Expected fail-closed schema rejection");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().toLowerCase()
                .contains(text.toLowerCase()));
            assertTrue(data.isWriteBlocked());
        }
    }
}
