package kome.common.data;

import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class KOMECampaignHaltedUnitProtectionTest {
    @Test public void haltedSnapshotProtectionRejectsOrdinaryAndAcceptsCampaign() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEHiredUnitRecord ordinary = unit();
        ordinary.stationedEntityData = haltedSnapshot();
        assertFalse(KOMEHaltedUnitProtection.isProtectedRecord(data, null, ordinary));

        KOMEHiredUnitRecord campaign = unit();
        KOMEHiredUnitClassification.assignForCampaignWorkflow(campaign);
        campaign.stationedEntityData = haltedSnapshot();
        assertTrue(KOMEHaltedUnitProtection.isProtectedRecord(data, null, campaign));
    }

    @Test public void ordinaryMovementSnapshotCannotCreateVirtualCampaignAuthority() {
        KOMEWorldData data = new KOMEWorldData("test");
        KOMEHiredUnitRecord ordinary = unit();
        ordinary.companyId = "C1";
        ordinary.movementOrderId = "M1";
        ordinary.movingEntityData = haltedSnapshot();
        data.hiredUnits.put(ordinary.entity, ordinary);
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = "C1";
        company.units.add(ordinary.entity);
        data.armyCompanies.put(company.id, company);
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = "M1";
        order.companyId = "C1";
        order.status = KOMEArmyMovementOrder.MOVING;
        order.units.add(ordinary.entity);
        data.armyMovements.put(order.id, order);

        assertFalse(data.hasValidHiredUnitMovementLink(ordinary));
        assertFalse(data.isVirtualMovingHiredUnit(ordinary));
    }

    private static NBTTagCompound haltedSnapshot() {
        NBTTagCompound root = new NBTTagCompound();
        NBTTagCompound info = new NBTTagCompound();
        info.setBoolean("IsActive", true);
        info.setInteger("Task", LOTRHiredNPCInfo.Task.WARRIOR.ordinal());
        info.setBoolean("GuardMode", false);
        info.setBoolean("CanMove", false);
        root.setTag("HiredNPCInfo", info);
        return root;
    }

    private static KOMEHiredUnitRecord unit() {
        KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
        record.entity = UUID.randomUUID();
        record.owner = UUID.randomUUID();
        record.sourcePlayer = record.owner;
        return record;
    }
}
