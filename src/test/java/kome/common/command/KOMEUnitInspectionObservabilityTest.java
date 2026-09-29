package kome.common.command;

import java.lang.reflect.Method;
import java.util.UUID;

import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEWorldData;
import kome.common.network.KOMEUnitGuiEntry;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.*;

public class KOMEUnitInspectionObservabilityTest {
    @Test public void ordinaryAndCampaignDisplayFullUuidAndCanonicalClass() {
        KOMEHiredUnitRecord ordinary = record();
        KOMEHiredUnitRecord campaign = persistedClass(record(), "CAMPAIGN");

        assertEquals("UUID: " + ordinary.entity + ". Class: ORDINARY.",
            KOMECommandTroops.formatUnitIdentity(ordinary));
        assertEquals("UUID: " + campaign.entity + ". Class: CAMPAIGN.",
            KOMECommandTroops.formatUnitIdentity(campaign));
    }

    @Test public void missingAndInvalidPersistedClassDisplayOrdinary() {
        KOMEHiredUnitRecord missing = record();
        KOMEHiredUnitRecord invalid = persistedClass(record(), "UNKNOWN_FUTURE_CLASS");

        assertTrue(KOMECommandTroops.formatUnitIdentity(missing).endsWith("Class: ORDINARY."));
        assertTrue(KOMECommandTroops.formatUnitIdentity(invalid).endsWith("Class: ORDINARY."));
    }

    @Test public void companyAndMovementFieldsNeverBecomeDisplayClassificationAuthority() {
        KOMEHiredUnitRecord ordinary = record();
        ordinary.companyId = "C-existing";
        ordinary.movementOrderId = "M-existing";

        assertEquals("UUID: " + ordinary.entity + ". Class: ORDINARY.",
            KOMECommandTroops.formatUnitIdentity(ordinary));
    }

    @Test public void populationUnitsDtoCarriesFullStableUuidWithoutMutation() throws Exception {
        KOMEWorldData data = new KOMEWorldData("inspection");
        KOMEHiredUnitRecord record = record();
        record.owner = UUID.randomUUID();
        record.companyId = "C-existing";
        record.movementOrderId = "M-existing";
        data.hiredUnits.put(record.entity, record);
        data.setDirty(false);
        NBTTagCompound before = record.writeToNBT();

        Method build = KOMECommandPopulation.class.getDeclaredMethod(
            "buildUnitGuiEntry", KOMEWorldData.class, KOMEHiredUnitRecord.class);
        build.setAccessible(true);
        KOMEUnitGuiEntry entry = (KOMEUnitGuiEntry) build.invoke(
            new KOMECommandPopulation(), data, record);

        assertEquals(record.entity.toString(), entry.entityId);
        NBTTagCompound after = record.writeToNBT();
        assertEquals(before.toString(), after.toString());
        assertFalse(data.isDirty());
        assertTrue(data.centralAudit.isEmpty());
        assertSame(record, data.hiredUnits.get(record.entity));
    }

    private static KOMEHiredUnitRecord record() {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        record.owner = UUID.randomUUID();
        return record;
    }

    private static KOMEHiredUnitRecord persistedClass(KOMEHiredUnitRecord record, String value) {
        NBTTagCompound tag = record.writeToNBT();
        tag.setString("UnitClass", value);
        record.readFromNBT(tag);
        return record;
    }
}
