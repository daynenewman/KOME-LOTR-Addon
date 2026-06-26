package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KOMEArmyCompany {
    public static final String STATIONED = "stationed";
    public static final String MOVING = "moving";
    public static final String SOURCE_MANUAL_LEGACY = "manual_legacy";
    public static final String SOURCE_LEGACY_MIGRATED = "legacy_migrated";
    public static final String SOURCE_AUTO_UNIT_ASSIGNMENT = "auto_unit_assignment";
    public static final String SOURCE_LOTR_COMPANY_ASSIGNMENT = "lotr_company_assignment";

    public String id = "";
    public UUID owner;
    public String ownerName = "";
    public String faction = "";
    public String name = "";
    public String lotrCompanyValue = "";
    public String currentTile = "";
    public final List<UUID> units = new ArrayList<UUID>();
    public int totalPopulation;
    public int mountedPopulation;
    public int groundPopulation;
    public String status = STATIONED;
    public String movementOrderId = "";
    public String source = SOURCE_AUTO_UNIT_ASSIGNMENT;
    public long createdAtMillis;
    public long updatedAtMillis;

    public boolean isMoving() {
        return MOVING.equals(status) || movementOrderId != null && movementOrderId.length() > 0;
    }

    public int getTilesPerDay() {
        return groundPopulation == 0 && mountedPopulation > 0 ? 2 : 1;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", id == null ? "" : id);
        nbt.setString("Owner", owner == null ? "" : owner.toString());
        nbt.setString("OwnerName", ownerName == null ? "" : ownerName);
        nbt.setString("Faction", KOMEAlliance.normalizeFactionKey(faction));
        nbt.setString("Name", name == null ? "" : name);
        nbt.setString("LotrCompanyValue", lotrCompanyValue == null ? "" : lotrCompanyValue);
        nbt.setString("CurrentTile", KOMEConquestTile.normalizeId(currentTile));
        nbt.setInteger("TotalPopulation", totalPopulation);
        nbt.setInteger("MountedPopulation", mountedPopulation);
        nbt.setInteger("GroundPopulation", groundPopulation);
        nbt.setString("Status", status == null ? STATIONED : status);
        nbt.setString("MovementOrderId", movementOrderId == null ? "" : movementOrderId);
        nbt.setString("Source", source == null ? SOURCE_AUTO_UNIT_ASSIGNMENT : source);
        nbt.setLong("CreatedAtMillis", createdAtMillis);
        nbt.setLong("UpdatedAtMillis", updatedAtMillis);
        NBTTagList unitList = new NBTTagList();
        for (UUID unit : units) {
            if (unit != null) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("Unit", unit.toString());
                unitList.appendTag(entry);
            }
        }
        nbt.setTag("Units", unitList);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        id = nbt.getString("Id");
        String ownerValue = nbt.getString("Owner");
        owner = ownerValue.length() == 0 ? null : UUID.fromString(ownerValue);
        ownerName = nbt.getString("OwnerName");
        faction = KOMEAlliance.normalizeFactionKey(nbt.getString("Faction"));
        name = nbt.getString("Name");
        lotrCompanyValue = nbt.getString("LotrCompanyValue");
        currentTile = KOMEConquestTile.normalizeId(nbt.getString("CurrentTile"));
        totalPopulation = Math.max(0, nbt.getInteger("TotalPopulation"));
        mountedPopulation = Math.max(0, nbt.getInteger("MountedPopulation"));
        groundPopulation = Math.max(0, nbt.getInteger("GroundPopulation"));
        status = nbt.getString("Status");
        if (!MOVING.equals(status)) {
            status = STATIONED;
        }
        movementOrderId = nbt.getString("MovementOrderId");
        source = nbt.hasKey("Source") ? nbt.getString("Source") : SOURCE_MANUAL_LEGACY;
        if (source == null || source.length() == 0) {
            source = SOURCE_MANUAL_LEGACY;
        }
        createdAtMillis = nbt.getLong("CreatedAtMillis");
        updatedAtMillis = nbt.getLong("UpdatedAtMillis");
        units.clear();
        NBTTagList unitList = nbt.getTagList("Units", 10);
        for (int i = 0; i < unitList.tagCount(); i++) {
            String unit = unitList.getCompoundTagAt(i).getString("Unit");
            if (unit.length() > 0) {
                units.add(UUID.fromString(unit));
            }
        }
    }
}
