package kome.common.command;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMECompanyCoherenceService;
import kome.common.data.KOMECompanyReconciliationService;
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

    @Test public void companyAndUnitDiagnosticFormattingExposeStatusAndIssues() {
        KOMEWorldData data = new KOMEWorldData("diagnostics");
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C1";
        company.owner = UUID.randomUUID();
        company.currentTile = "T100";
        KOMEHiredUnitRecord ordinary = record();
        ordinary.owner = company.owner;
        ordinary.companyId = company.id;
        ordinary.currentTile = "T100";
        company.units.add(ordinary.entity);
        data.hiredUnits.put(ordinary.entity, ordinary);
        data.armyCompanies.put(company.id, company);

        KOMECompanyCoherenceService.Assessment assessment =
            KOMECompanyCoherenceService.INSTANCE.assess(data, company);
        List<String> companyLines = KOMECommandTroops.formatCompanyCoherence(assessment);
        List<String> unitLines = KOMECommandTroops.formatUnitDetachmentCoherence(
            assessment, ordinary.entity);

        assertTrue(companyLines.toString().contains(
            "Campaign Detachment coherence: INCOHERENT"));
        assertTrue(companyLines.toString().contains("ORDINARY_MEMBER"));
        assertTrue(unitLines.toString().contains("Campaign Detachment C1"));
        assertTrue(unitLines.toString().contains("SERVER_STOPPED"));
        assertTrue(unitLines.toString().contains("unit agreement UNKNOWN"));
    }

    @Test public void rebuildDiagnosticSummarizesCanonicalReconciliation() {
        KOMEWorldData data = new KOMEWorldData("rebuild-diagnostics");
        KOMEArmyCompany empty = new KOMEArmyCompany();
        empty.id = "C1";
        empty.owner = UUID.randomUUID();
        empty.currentTile = "T100";
        data.armyCompanies.put(empty.id, empty);

        KOMECompanyReconciliationService.Result result =
            KOMECompanyReconciliationService.INSTANCE.reconcile(data);
        String line = KOMECommandTroops.formatCompanyReconciliation(result);

        assertTrue(line.contains("empty detachments removed 1"));
        assertTrue(line.contains("ambiguous units left unassigned 0"));
        assertFalse(line.contains("LOTR Unit Overview"));
        assertFalse(line.contains("source tile"));
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
