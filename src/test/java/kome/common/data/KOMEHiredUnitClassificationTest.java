package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMEHiredUnitClassificationTest {
    @Test public void explicitOrdinaryRoundTrips() {
        KOMEHiredUnitRecord restored = roundTrip(unit());
        assertEquals(KOMEHiredUnitClass.ORDINARY,
            KOMEHiredUnitClassification.getUnitClass(restored));
        assertFalse(KOMEHiredUnitClassification.isCampaignUnit(restored));
        assertEquals("ORDINARY", restored.writeToNBT().getString("UnitClass"));
    }

    @Test public void explicitCampaignRoundTrips() {
        KOMEHiredUnitRecord original = unit();
        KOMEHiredUnitClassification.assignForCampaignWorkflow(original);
        KOMEHiredUnitRecord restored = roundTrip(original);
        assertEquals(KOMEHiredUnitClass.CAMPAIGN,
            KOMEHiredUnitClassification.getUnitClass(restored));
        assertTrue(KOMEHiredUnitClassification.isCampaignUnit(restored));
        assertSame(restored, KOMEHiredUnitClassification.requireCampaignUnit(restored));
    }

    @Test public void missingAndInvalidValuesFailClosedToOrdinary() {
        NBTTagCompound missing = unit().writeToNBT();
        missing.removeTag("UnitClass");
        assertOrdinary(read(missing));

        NBTTagCompound invalid = unit().writeToNBT();
        invalid.setString("UnitClass", "campaign");
        assertOrdinary(read(invalid));
        invalid.setString("UnitClass", "UNKNOWN_FUTURE_VALUE");
        assertOrdinary(read(invalid));
        assertFalse(KOMEHiredUnitClassification.isCampaignUnit((KOMEHiredUnitRecord) null));
    }

    @Test public void strategicAndPopulationFieldsNeverInferCampaign() {
        KOMEHiredUnitRecord record = unit();
        record.type = KOMEPopulationType.OFFENSIVE;
        record.companyId = "C1";
        record.movementOrderId = "M1";
        record.currentTile = "T100";
        record.movingEntityData = new NBTTagCompound();

        KOMEHiredUnitRecord restored = roundTrip(record);
        assertOrdinary(restored);
        assertEquals(KOMEPopulationType.OFFENSIVE, restored.type);
        assertEquals("C1", restored.companyId);
        assertEquals("M1", restored.movementOrderId);
        assertEquals("T100", restored.currentTile);
    }

    @Test public void worldLookupAndRequirementAlsoFailClosed() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEHiredUnitRecord ordinary = unit();
        data.hiredUnits.put(ordinary.entity, ordinary);
        assertFalse(KOMEHiredUnitClassification.isCampaignUnit(data, ordinary.entity));
        assertFalse(KOMEHiredUnitClassification.isCampaignUnit(data, UUID.randomUUID()));
        try {
            KOMEHiredUnitClassification.requireCampaignUnit(ordinary);
            fail("Ordinary unit must not pass a campaign requirement");
        } catch (IllegalArgumentException expected) {
            // Expected fail-closed permission boundary.
        }
    }

    private static void assertOrdinary(KOMEHiredUnitRecord record) {
        assertEquals(KOMEHiredUnitClass.ORDINARY,
            KOMEHiredUnitClassification.getUnitClass(record));
        assertFalse(KOMEHiredUnitClassification.isCampaignUnit(record));
    }

    private static KOMEHiredUnitRecord roundTrip(KOMEHiredUnitRecord record) {
        return read(record.writeToNBT());
    }

    private static KOMEHiredUnitRecord read(NBTTagCompound tag) {
        KOMEHiredUnitRecord restored = new KOMEHiredUnitRecord();
        restored.readFromNBT(tag);
        return restored;
    }

    private static KOMEHiredUnitRecord unit() {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        record.owner = UUID.randomUUID();
        record.sourcePlayer = record.owner;
        return record;
    }
}
