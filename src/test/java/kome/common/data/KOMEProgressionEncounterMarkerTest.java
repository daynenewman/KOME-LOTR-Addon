package kome.common.data;

import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEProgressionEncounterMarkerTest {
    @Test
    public void markerRoundTripsAndClears() {
        UUID owner = UUID.randomUUID();
        NBTTagCompound entityData = new NBTTagCompound();

        KOMEProgressionEncounterMarker.mark(
            entityData,
            KOMEProgressionEncounterMarker.ESCORT,
            owner,
            "assignment-token");

        KOMEProgressionEncounterMarker.Marker marker =
            KOMEProgressionEncounterMarker.read(entityData);

        assertNotNull(marker);
        assertEquals(KOMEProgressionEncounterMarker.ESCORT, marker.kind);
        assertEquals(owner, marker.owner);
        assertEquals("assignment-token", marker.token);

        KOMEProgressionEncounterMarker.clear(entityData);
        assertNull(KOMEProgressionEncounterMarker.read(entityData));
    }

    @Test
    public void invalidOrIncompleteMarkerFailsClosed() {
        NBTTagCompound entityData = new NBTTagCompound();

        assertNull(KOMEProgressionEncounterMarker.read(entityData));
        assertNull(KOMEProgressionEncounterMarker.read((NBTTagCompound)null));

        KOMEProgressionEncounterMarker.mark(
            entityData,
            null,
            UUID.randomUUID(),
            "token");

        assertNull(KOMEProgressionEncounterMarker.read(entityData));

        KOMEProgressionEncounterMarker.mark(
            entityData,
            KOMEProgressionEncounterMarker.DEFENSE,
            UUID.randomUUID(),
            "");

        assertNull(KOMEProgressionEncounterMarker.read(entityData));
    }
}