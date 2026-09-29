package kome.common.data;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMEHiredUnitCompanyAdmissionTest {
    @Test public void directAutoAdmissionRejectsOrdinaryAndAcceptsCampaign() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEHiredUnitRecord ordinary = unit();
        assertNull(data.assignUnitToHiringTileCompany(ordinary, "Owner"));
        assertFalse(data.hiredUnits.containsKey(ordinary.entity));
        assertTrue(data.armyCompanies.isEmpty());

        KOMEHiredUnitRecord campaign = unit();
        KOMEHiredUnitClassification.assignForCampaignWorkflow(campaign);
        KOMEArmyCompany company = data.assignUnitToHiringTileCompany(campaign, "Owner");
        assertNotNull(company);
        assertTrue(company.units.contains(campaign.entity));
        assertEquals(company.id, campaign.companyId);
        assertSame(campaign, data.hiredUnits.get(campaign.entity));
    }

    @Test public void rebuildRemovesOrdinaryMembershipWithoutConvertingClass() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEHiredUnitRecord ordinary = unit();
        ordinary.companyId = "C1";
        data.hiredUnits.put(ordinary.entity, ordinary);
        KOMEArmyCompany company = company(ordinary.owner, "C1");
        company.units.add(ordinary.entity);
        data.armyCompanies.put(company.id, company);

        data.rebuildArmyCompaniesForPlayer(ordinary.owner);

        assertFalse(company.units.contains(ordinary.entity));
        assertEquals("", ordinary.companyId);
        assertEquals(KOMEHiredUnitClass.ORDINARY,
            KOMEHiredUnitClassification.getUnitClass(ordinary));
    }

    @Test public void rebuildAdmitsOnlyExplicitCampaignRecord() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEHiredUnitRecord ordinary = unit();
        KOMEHiredUnitRecord campaign = unit();
        campaign.owner = ordinary.owner;
        KOMEHiredUnitClassification.assignForCampaignWorkflow(campaign);
        data.hiredUnits.put(ordinary.entity, ordinary);
        data.hiredUnits.put(campaign.entity, campaign);

        data.rebuildArmyCompaniesForPlayer(ordinary.owner);

        assertEquals("", ordinary.companyId);
        assertFalse(KOMEHiredUnitClassification.isCampaignUnit(ordinary));
        assertFalse(campaign.companyId.isEmpty());
        assertTrue(KOMEHiredUnitClassification.isCampaignUnit(campaign));
        assertTrue(data.armyCompanies.get(campaign.companyId).units.contains(campaign.entity));
    }

    @Test public void companyAndMovementLabelsNeverBecomeClassificationAuthority() {
        KOMEHiredUnitRecord ordinary = unit();
        ordinary.companyId = "C1";
        ordinary.companyName = "First Company";
        ordinary.movementOrderId = "M1";
        assertFalse(KOMEHiredUnitClassification.isCampaignUnit(ordinary));

        KOMEWorldData data = new KOMEWorldData("test");
        data.hiredUnits.put(ordinary.entity, ordinary);
        assertFalse(data.hasValidHiredUnitMovementLink(ordinary));
    }

    private static KOMEHiredUnitRecord unit() {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        record.owner = UUID.randomUUID();
        record.sourcePlayer = record.owner;
        record.sourceTileId = "T100";
        record.currentTile = "T100";
        record.cost = record.baseCost = record.populationSpent = 25;
        record.type = KOMEPopulationType.OFFENSIVE;
        return record;
    }

    private static KOMEArmyCompany company(UUID owner, String id) {
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = id;
        company.owner = owner;
        company.source = KOMEArmyCompany.SOURCE_AUTO_UNIT_ASSIGNMENT;
        company.sourceTileId = "T100";
        company.currentTile = "T100";
        return company;
    }
}
