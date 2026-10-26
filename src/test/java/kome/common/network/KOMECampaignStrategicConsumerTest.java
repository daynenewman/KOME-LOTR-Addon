package kome.common.network;

import kome.common.data.KOMEHiredUnitClassification;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEPopulationService;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMETileTroopSummary;
import kome.common.data.KOMEWorldData;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

public class KOMECampaignStrategicConsumerTest {
    @Test public void captureDoesNotPublishEnemyIncomingTiming() {
        assertEquals(0L,KOMEPacketConquestOpenCapture.visibleIncomingEta(1234L,"gondor","mordor",false));
        assertEquals(0L,KOMEPacketConquestOpenCapture.visibleIncomingEta(1234L,"","mordor",false));
        assertEquals(1234L,KOMEPacketConquestOpenCapture.visibleIncomingEta(1234L,"mordor","mordor",false));
        assertEquals(1234L,KOMEPacketConquestOpenCapture.visibleIncomingEta(1234L,"gondor","mordor",true));
        assertEquals(0L,KOMEPacketConquestOpenCapture.visibleIncomingEta(1234L,"gondor","",true));
    }
    @Test public void strategicMapMarkerEligibilityExcludesOrdinary() {
        UUID owner = UUID.randomUUID();
        KOMEHiredUnitRecord ordinary = unit(owner);
        KOMEHiredUnitRecord campaign = unit(owner);
        campaign.readFromNBT(campaignTag(campaign));

        assertFalse(KOMEPacketUnitMapMarkers.isStrategicMarkerRecord(ordinary));
        assertTrue(KOMEPacketUnitMapMarkers.isStrategicMarkerRecord(campaign));
    }

    @Test public void conquestAndCaptureSummariesCountCampaignOnly() throws Exception {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID owner = UUID.randomUUID();
        KOMEHiredUnitRecord ordinary = unit(owner);
        KOMEHiredUnitRecord campaign = unit(owner);
        campaign.readFromNBT(campaignTag(campaign));
        data.hiredUnits.put(ordinary.entity, ordinary);
        data.hiredUnits.put(campaign.entity, campaign);
        KOMEArmyCompany company=new KOMEArmyCompany();company.id="C1";company.owner=owner;
        company.faction="gondor";company.currentTile="T100";company.units.add(campaign.entity);
        data.armyCompanies.put(company.id,company);

        Method conquest = KOMEPacketConquestData.class.getDeclaredMethod(
            "buildTroopSummaries", KOMEWorldData.class,KOMECompanyMapProjection.View.class,boolean.class);
        conquest.setAccessible(true);
        Map summaries = (Map) conquest.invoke(null, data,
            KOMECompanyMapProjection.view(data,owner,"gondor",true),true);
        KOMETileTroopSummary tile = (KOMETileTroopSummary) summaries.get("T100");
        assertNotNull(tile);
        assertEquals(25, tile.stationedPop);
        assertEquals(25, tile.stationedOffensivePop);

        Method capture = KOMEPacketConquestOpenCapture.class.getDeclaredMethod(
            "summarizeTroops", KOMEWorldData.class, String.class, String.class, UUID.class,
            KOMECompanyMapProjection.View.class);
        capture.setAccessible(true);
        Object result = capture.invoke(null, data, "gondor", "T100", owner,
            KOMECompanyMapProjection.view(data,owner,"gondor",true));
        assertEquals(25, intField(result, "offensivePop"));
        assertEquals(25, intField(result, "myOffensivePop"));

        assertEquals(java.math.BigInteger.valueOf(5000L),
            KOMEPopulationService.getActivePopulationCenti(data, "gondor"));
    }

    @Test public void cachedMovementTotalsCannotReintroduceOrdinaryUnits() throws Exception {
        KOMEWorldData data = new KOMEWorldData("test");
        UUID owner = UUID.randomUUID();
        KOMEHiredUnitRecord ordinary = unit(owner);
        KOMEHiredUnitRecord campaign = unit(owner);
        campaign.readFromNBT(campaignTag(campaign));
        ordinary.movementOrderId = campaign.movementOrderId = "M1";
        data.hiredUnits.put(ordinary.entity, ordinary);
        data.hiredUnits.put(campaign.entity, campaign);

        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = "M1";
        order.companyId="C1";order.owner=owner;order.ownerFaction="gondor";
        order.status = KOMEArmyMovementOrder.MOVING;
        order.currentTile = "T100";
        order.nextTile = order.destinationTile = "T101";
        order.population = 999;
        order.mountedPopulation = 999;
        order.units.add(ordinary.entity);
        order.units.add(campaign.entity);
        data.armyMovements.put(order.id, order);
        KOMEArmyCompany company=new KOMEArmyCompany();company.id="C1";company.owner=owner;
        company.faction="gondor";company.currentTile="T100";
        company.status=KOMEArmyCompany.MOVING;company.movementOrderId=order.id;
        company.units.add(campaign.entity);
        data.armyCompanies.put(company.id,company);

        Method conquest = KOMEPacketConquestData.class.getDeclaredMethod(
            "buildTroopSummaries", KOMEWorldData.class,KOMECompanyMapProjection.View.class,boolean.class);
        conquest.setAccessible(true);
        Map summaries = (Map) conquest.invoke(null, data,
            KOMECompanyMapProjection.view(data,owner,"gondor",true),true);
        assertEquals(25, ((KOMETileTroopSummary) summaries.get("T100")).movingPop);
        assertEquals(25, ((KOMETileTroopSummary) summaries.get("T101")).incomingPop);

        Method capture = KOMEPacketConquestOpenCapture.class.getDeclaredMethod(
            "summarizeTroops", KOMEWorldData.class, String.class, String.class, UUID.class,
            KOMECompanyMapProjection.View.class);
        capture.setAccessible(true);
        KOMECompanyMapProjection.View readView=KOMECompanyMapProjection.view(data,owner,"gondor",true);
        Object origin = capture.invoke(null, data, "gondor", "T100", owner,readView);
        Object destination = capture.invoke(null, data, "gondor", "T101", owner,readView);
        assertEquals(25, intField(origin, "outgoingPop"));
        assertEquals(25, intField(destination, "incomingPop"));
    }

    private static int intField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static net.minecraft.nbt.NBTTagCompound campaignTag(KOMEHiredUnitRecord record) {
        net.minecraft.nbt.NBTTagCompound tag = record.writeToNBT();
        tag.setString("UnitClass", "CAMPAIGN");
        return tag;
    }

    private static KOMEHiredUnitRecord unit(UUID owner) {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        record.owner = owner;
        record.sourcePlayer = owner;
        record.type = KOMEPopulationType.OFFENSIVE;
        record.cost = record.baseCost = record.populationSpent = 25;
        record.sourceTileId = record.currentTile = "T100";
        record.companyId = "C1";
        record.unitFaction = record.sourceFaction = record.populationOwningFaction = "gondor";
        record.sourceType = KOMEHiredUnitRecord.SOURCE_FACTION_POPULATION_BANK;
        return record;
    }
}
