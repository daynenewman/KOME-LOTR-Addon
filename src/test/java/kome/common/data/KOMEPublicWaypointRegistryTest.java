package kome.common.data;

import java.util.*;
import net.minecraft.nbt.*;
import lotr.common.world.map.LOTRWaypoint;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEPublicWaypointRegistryTest {
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private static final String ADMIN = "10000000-0000-0000-0000-000000000001";

    private KOMEWorldData world() {
        KOMEWorldData data = new KOMEWorldData("waypoint-fixture");
        data.initializeIntegratedWorld();
        return data;
    }
    private KOMEPublicWaypoint create(KOMEWorldData data) {
        return data.publicWaypoints.approve(data, "Fixture destination", KOMETileTestResources.dimension(),
            KOMETileTestResources.x(), 72, KOMETileTestResources.z(), 0,
            KOMEPublicWaypoint.Source.PUBLIC, "", ADMIN, 100, null);
    }
    private NBTTagCompound save(KOMEWorldData data) {
        NBTTagCompound n = new NBTTagCompound(); data.writeToNBT(n); return n;
    }
    private void denied(Runnable operation) {
        try { operation.run(); fail("Expected rejection"); }
        catch (IllegalArgumentException | IllegalStateException expected) { }
    }
    private NBTTagList rows(NBTTagCompound... rows) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound row : rows) list.appendTag(row);
        return list;
    }

    @Test public void emptyTileIsValidAndApprovalSurvivesWorldDataRestart() {
        KOMEWorldData data = world();
        assertNull(data.publicWaypoints.forTile("T100"));
        KOMEPublicWaypoint r = create(data);
        assertEquals("T100", r.tileId);
        denied(() -> create(data));
        assertEquals(1, data.publicWaypoints.records().size());
        KOMEWorldData restart = new KOMEWorldData("restart"); restart.readFromNBT(save(data));
        KOMEPublicWaypoint restored = restart.publicWaypoints.get(r.id);
        assertEquals(r.id, restored.id); assertEquals(r.wireId, restored.wireId);
        assertEquals(r.tileId, restored.tileId); assertEquals(r.name, restored.name);
        assertEquals(1, restart.publicWaypoints.history().size());
    }

    @Test public void ownershipIsDerivedAndIndependentLevelDoesNotChangeTileGameplayDefaults() {
        KOMEWorldData data = world(); KOMEPublicWaypoint r = create(data);
        KOMEConquestTile tile = data.conquestTiles.get(r.tileId);
        String originalDefault = tile.defaultRulingFaction;
        int gameplayLevel = tile.waypointLevel;
        tile.setCurrentRulingFaction("gondor");
        assertEquals("gondor", data.publicWaypoints.view(data, r.id).currentOwner);
        data.publicWaypoints.level(data, r.id, Integer.MAX_VALUE, ADMIN, 101);
        tile.setCurrentRulingFaction("rohan");
        KOMEPublicWaypointRegistry.View v = data.publicWaypoints.view(data, r.id);
        assertEquals("rohan", v.currentOwner); assertEquals(originalDefault, v.defaultOwner);
        assertEquals(Integer.MAX_VALUE, v.record.level); assertEquals(gameplayLevel, tile.waypointLevel);
        NBTTagCompound persisted = save(data);
        NBTTagCompound row = persisted.getCompoundTag("PublicWaypoints").getTagList("Records", 10).getCompoundTagAt(0);
        assertFalse(row.hasKey("CurrentOwner")); assertFalse(row.hasKey("DefaultOwner"));
        KOMEWorldData restart = new KOMEWorldData("restart"); restart.readFromNBT(persisted);
        assertEquals(Integer.MAX_VALUE, restart.publicWaypoints.get(r.id).level);
        assertEquals("rohan", restart.publicWaypoints.view(restart, r.id).currentOwner);
    }

    @Test public void invalidApprovalNeverChangesRegistryOrAudit() {
        KOMEWorldData data = world();
        int d = KOMETileTestResources.dimension(), x = KOMETileTestResources.x(), z = KOMETileTestResources.z();
        denied(() -> data.publicWaypoints.approve(data, "Gap", d, 189568, 72, -86016, 0,
            KOMEPublicWaypoint.Source.PUBLIC, "", ADMIN, 100, null));
        denied(() -> data.publicWaypoints.approve(data, "Outside", d, Integer.MIN_VALUE, 72, 0, 0,
            KOMEPublicWaypoint.Source.PUBLIC, "", ADMIN, 100, null));
        denied(() -> data.publicWaypoints.approve(data, "Wrong dimension", d + 1, x, 72, z, 0,
            KOMEPublicWaypoint.Source.PUBLIC, "", ADMIN, 100, null));
        for (String name : new String[] {"", " leading", "bad\nname", "bad\u00a7name",
                String.join("", Collections.nCopies(65, "a")), "\ud800"}) {
            denied(() -> data.publicWaypoints.approve(data, name, d, x, 72, z, 0,
                KOMEPublicWaypoint.Source.PUBLIC, "", ADMIN, 100, null));
        }
        for (int y : new int[] {-1, 256})
            denied(() -> data.publicWaypoints.approve(data, "Bad Y", d, x, y, z, 0,
                KOMEPublicWaypoint.Source.PUBLIC, "", ADMIN, 100, null));
        denied(() -> data.publicWaypoints.approve(data, "Bad level", d, x, 72, z, -1,
            KOMEPublicWaypoint.Source.PUBLIC, "", ADMIN, 100, null));
        denied(() -> data.publicWaypoints.approve(data, "Bad actor", d, x, 72, z, 0,
            KOMEPublicWaypoint.Source.PUBLIC, "", "not-an-actor", 100, null));
        denied(() -> data.publicWaypoints.approve(data, "Invalid native", d, x, 72, z, 0,
            KOMEPublicWaypoint.Source.NATIVE, "not-a-native-waypoint", ADMIN, 100, null));
        assertTrue(data.publicWaypoints.records().isEmpty()); assertTrue(data.publicWaypoints.history().isEmpty());
        assertEquals(0, data.publicWaypoints.revision());
    }

    @Test public void namesCanContainValidUnicodeButNotMalformedSurrogatePairs() {
        assertEquals("Village \ud83c\udf3f", KOMEPublicWaypoint.validName("Village \ud83c\udf3f"));
        denied(() -> KOMEPublicWaypoint.validName("Bad\udc00"));
        denied(() -> KOMEPublicWaypoint.validName("Bad\ud800X"));
    }

    @Test public void conflictingIdsWireIdsAndTilesWithholdAllValidContenders() {
        for (int kind = 0; kind < 3; kind++) {
            KOMEWorldData data = world(); KOMEPublicWaypoint r = create(data);
            NBTTagCompound section = data.publicWaypoints.writeToNBT();
            NBTTagCompound a = r.writeToNBT(), b = r.writeToNBT();
            b.setString("Id", UUID.randomUUID().toString());
            b.setInteger("WireId", -2);
            section.setLong("NextWire", 3);
            if (kind < 2) {
                b.setString("Tile", "T442"); b.setInteger("X", 237248); b.setInteger("Z", 87296);
            }
            if (kind == 0) b.setString("Id", r.id.toString());
            if (kind == 1) b.setInteger("WireId", -1);
            section.setTag("Records", rows(a,b));
            KOMEPublicWaypointRegistry loaded = KOMEPublicWaypointRegistry.read(data, section);
            assertTrue(loaded.records().isEmpty()); assertEquals(2, loaded.quarantine().size());
            section.setTag("Records", rows(b,a));
            loaded = KOMEPublicWaypointRegistry.read(data, section);
            assertTrue(loaded.records().isEmpty()); assertEquals(2, loaded.quarantine().size());
        }
    }

    @Test public void malformedOrphanedWrongPositionAndCounterRecordsKeepOriginalEvidence() {
        for (int kind = 0; kind < 6; kind++) {
            KOMEWorldData data = world(); KOMEPublicWaypoint r = create(data);
            NBTTagCompound section = data.publicWaypoints.writeToNBT(), bad = r.writeToNBT();
            if (kind == 0) bad.removeTag("Name");
            if (kind == 1) bad.setString("Level", "5");
            if (kind == 2) bad.setString("Id", "1-1-1-1-1");
            if (kind == 3) bad.setString("Tile", "T9999");
            if (kind == 4) bad.setInteger("X", 189568);
            if (kind == 5) bad.setInteger("WireId", -2);
            section.setTag("Records", rows(bad));
            KOMEPublicWaypointRegistry loaded = KOMEPublicWaypointRegistry.read(data, section);
            assertTrue(loaded.records().isEmpty()); assertEquals(1, loaded.quarantine().size());
            assertEquals(bad, loaded.quarantine().get(0).getCompoundTag("Original"));
            bad.setString("Name", "tampered");
            assertNotEquals(bad, loaded.quarantine().get(0).getCompoundTag("Original"));
            NBTTagCompound exposure = loaded.quarantine().get(0); exposure.setString("Reason", "tampered");
            assertNotEquals("tampered", loaded.quarantine().get(0).getString("Reason"));
        }
    }

    @Test public void unavailableGeometrySuspendsRatherThanDestroysPersistedRecords() {
        KOMEWorldData data = world(); KOMEPublicWaypoint r = create(data);
        NBTTagCompound n = data.publicWaypoints.writeToNBT();
        KOMETileWorldResolver.INSTANCE.invalidate();
        try {
            KOMEPublicWaypointRegistry loaded = KOMEPublicWaypointRegistry.read(data, n);
            assertNotNull(loaded.get(r.id));
            assertNull(loaded.view(data, r.id));
            assertTrue(loaded.quarantine().isEmpty());
            KOMETileWorldResolver.INSTANCE.publish(KOMETileTestResources.real());
            assertNotNull(loaded.view(data, r.id));
        } finally { KOMETileWorldResolver.INSTANCE.publish(KOMETileTestResources.real()); }
    }

    @Test public void existingCapitalGeometryLoadGuardStillBlocksWholeWorldAndRetainsRegistry() {
        KOMEWorldData data = world(); KOMEPublicWaypoint r = create(data);
        NBTTagCompound n = save(data);
        KOMETileWorldResolver.INSTANCE.invalidate();
        try {
            denied(() -> data.readFromNBT(n));
            assertTrue(data.isWriteBlocked());
            assertEquals(r.id, data.publicWaypoints.get(r.id).id);
            assertEquals(1, data.publicWaypoints.history().size());
        } finally { KOMETileWorldResolver.INSTANCE.publish(KOMETileTestResources.real()); }
    }

    @Test public void absentSectionAllowsExistingSchemaFiveWithoutGuessingLegacyApprovals() {
        KOMEWorldData data = world();
        NBTTagCompound n = save(data); n.removeTag("PublicWaypoints");
        KOMEWorldData loaded = new KOMEWorldData("existing-schema-five"); loaded.readFromNBT(n);
        assertTrue(loaded.publicWaypoints.records().isEmpty());
        assertEquals(data.tileWaypointLinksByTileId.size(), loaded.tileWaypointLinksByTileId.size());
        assertEquals(data.tileWaypoints.size(), loaded.tileWaypoints.size());
    }

    @Test public void corruptSectionTypesAndUnknownSchemaRejectAtomicLoad() {
        for (int kind = 0; kind < 4; kind++) {
            KOMEWorldData data = world(); KOMEPublicWaypoint r = create(data);
            NBTTagCompound n = save(data);
            if (kind == 0) n.setString("PublicWaypoints", "wrong type");
            if (kind == 1) n.getCompoundTag("PublicWaypoints").setInteger("Schema", 99);
            if (kind == 2) n.getCompoundTag("PublicWaypoints").setTag("Records", rowsWrongType());
            if (kind == 3) n.getCompoundTag("PublicWaypoints").setString("History", "wrong type");
            denied(() -> data.readFromNBT(n));
            assertTrue(data.isWriteBlocked());
            assertEquals(r.id, data.publicWaypoints.get(r.id).id);
            assertEquals(r.name, data.publicWaypoints.get(r.id).name);
            assertEquals(1, data.publicWaypoints.history().size());
        }
    }
    private NBTTagList rowsWrongType() { NBTTagList l = new NBTTagList(); l.appendTag(new NBTTagString("bad")); return l; }

    @Test public void administrativeMoveRenameRemovalPreserveIdentityAndNeverReuseWireIds() {
        KOMEWorldData data = world(); KOMEPublicWaypoint r = create(data);
        data.publicWaypoints.rename(data, r.id, "Renamed", ADMIN, 101);
        data.publicWaypoints.move(data, r.id, r.dimension, 237248, 72, 87296, ADMIN, 102);
        assertEquals("T442", data.publicWaypoints.get(r.id).tileId);
        assertEquals(r.wireId, data.publicWaypoints.get(r.id).wireId);
        assertEquals("Renamed", data.publicWaypoints.get(r.id).name);
        KOMEPublicWaypoint other = create(data);
        long revision = data.publicWaypoints.revision();
        int history = data.publicWaypoints.history().size();
        denied(() -> data.publicWaypoints.move(data, r.id, other.dimension, other.x, other.y, other.z, ADMIN, 103));
        assertEquals(revision, data.publicWaypoints.revision()); assertEquals(history, data.publicWaypoints.history().size());
        data.publicWaypoints.remove(data, other.id, ADMIN, 104);
        KOMEPublicWaypoint newer = create(data);
        assertTrue(newer.wireId < other.wireId);
        assertEquals(6, data.publicWaypoints.history().size());
    }

    @Test public void validNativeAssociationsKeepExactNativeGeometry() {
        KOMEWorldData data = world();
        LOTRWaypoint candidate = null;
        for (LOTRWaypoint wp : LOTRWaypoint.values()) {
            if (!wp.isHidden() && KOMETileWorldResolver.INSTANCE.resolve(KOMETileTestResources.dimension(),
                    wp.getXCoord(), wp.getZCoord()).status == KOMETileResolution.Status.RESOLVED) {
                candidate = wp; break;
            }
        }
        assertNotNull(candidate);
        KOMEPublicWaypoint r = data.publicWaypoints.approve(data, candidate.getDisplayName(),
            KOMETileTestResources.dimension(), candidate.getXCoord(), candidate.getYCoordSaved(),
            candidate.getZCoord(), 0, KOMEPublicWaypoint.Source.NATIVE, candidate.getCodeName(), ADMIN, 100, null);
        denied(() -> data.publicWaypoints.move(data, r.id, r.dimension, r.x + 1, r.y, r.z, ADMIN, 101));
        assertNotNull(data.publicWaypoints.view(data, r.id)); assertEquals(1, data.publicWaypoints.history().size());
    }

    @Test public void registryCannotMutateDetachedOrClientAuthority() {
        KOMEWorldData data = world();
        denied(() -> new KOMEPublicWaypointRegistry().approve(data, "Detached", KOMETileTestResources.dimension(),
            KOMETileTestResources.x(), 72, KOMETileTestResources.z(), 0, KOMEPublicWaypoint.Source.PUBLIC, "", ADMIN, 100, null));
        denied(() -> KOMEClientData.INSTANCE.publicWaypoints.approve(KOMEClientData.INSTANCE, "Client",
            KOMETileTestResources.dimension(), KOMETileTestResources.x(), 72, KOMETileTestResources.z(), 0,
            KOMEPublicWaypoint.Source.PUBLIC, "", ADMIN, 100, null));
        assertTrue(data.publicWaypoints.records().isEmpty());
    }
}
