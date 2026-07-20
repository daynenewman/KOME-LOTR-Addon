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
    public static final String AGGRESSIVE = "AGGRESSIVE";
    public static final String CONSERVATIVE = "CONSERVATIVE";
    public static final String AUTHORITY_NATIVE = "NATIVE";
    public static final String AUTHORITY_ALLIANCE_DELEGATE = "ALLIANCE_DELEGATE";
    public static final String AUTHORITY_STEWARDSHIP = "KINGLESS_STEWARDSHIP";
    public static final String AUTHORITY_NATIVE_RECLAIM = "NATIVE_RECLAIM";
    public static final String WAR_ENDED_HALTED = "WAR_ENDED_HALTED";
    public static final String CLEANUP_NONE = "NONE";
    public static final String CLEANUP_WITHDRAWAL = "WITHDRAWAL_REQUIRED";
    public static final String CLEANUP_DEMOBILIZATION = "PENDING_DEMOBILIZATION";
    public static final String CLEANUP_ADMIN = "PENDING_ADMIN_RESOLUTION";
    public String nativeFaction = "";
    public final List<String> authorizedWarIds = new ArrayList<String>();
    public String populationSource = "";
    public int stewardshipReservation;
    public String authorizationReason = "";
    public boolean stewardshipCreated;
    public String withdrawalState = CLEANUP_NONE;
    public UUID transferRecipient;
    public String transferRecipientName = "";
    public UUID transferOfferedBy;
    public long transferOfferedAtMillis;
    public long transferExpiresAtMillis;
    public String tendency = CONSERVATIVE;
    public UUID temporaryController;
    public String temporaryControllerName = "";
    public UUID delegatedBy;
    public String delegatedByName = "";
    public String controllerAuthority = AUTHORITY_NATIVE;
    public String delegationAlliancePair = "";
    public long delegatedAtMillis;
    public String delegationRevocationReason = "";

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
        nbt.setString("Tendency", AGGRESSIVE.equals(tendency) ? AGGRESSIVE : CONSERVATIVE);
        nbt.setString("TemporaryController", temporaryController == null ? "" : temporaryController.toString());
        nbt.setString("TemporaryControllerName", temporaryControllerName == null ? "" : temporaryControllerName);
        nbt.setString("DelegatedBy", delegatedBy == null ? "" : delegatedBy.toString());
        nbt.setString("DelegatedByName", delegatedByName == null ? "" : delegatedByName);
        nbt.setString("ControllerAuthority", controllerAuthority == null ? AUTHORITY_NATIVE : controllerAuthority);
        nbt.setString("DelegationAlliancePair", delegationAlliancePair == null ? "" : delegationAlliancePair);
        nbt.setLong("DelegatedAtMillis", delegatedAtMillis);
        nbt.setString("DelegationRevocationReason", delegationRevocationReason == null ? "" : delegationRevocationReason);
        nbt.setString("NativeFaction", KOMEAlliance.normalizeFactionKey(nativeFaction.length() == 0 ? faction : nativeFaction));
        nbt.setString("PopulationSource", populationSource == null ? "" : populationSource);
        nbt.setInteger("StewardshipReservation", Math.max(0, stewardshipReservation));
        nbt.setString("AuthorizationReason", authorizationReason == null ? "" : authorizationReason);
        nbt.setBoolean("StewardshipCreated", stewardshipCreated);
        nbt.setString("WithdrawalState", withdrawalState == null ? CLEANUP_NONE : withdrawalState);
        nbt.setString("TransferRecipient", transferRecipient == null ? "" : transferRecipient.toString());
        nbt.setString("TransferRecipientName", transferRecipientName == null ? "" : transferRecipientName);
        nbt.setString("TransferOfferedBy", transferOfferedBy == null ? "" : transferOfferedBy.toString());
        nbt.setLong("TransferOfferedAtMillis", transferOfferedAtMillis);
        nbt.setLong("TransferExpiresAtMillis", transferExpiresAtMillis);
        NBTTagList warList = new NBTTagList();
        for (String warId : authorizedWarIds) {
            if (warId != null && warId.length() > 0) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setString("War", warId);
                warList.appendTag(entry);
            }
        }
        nbt.setTag("AuthorizedWars", warList);
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
        if (!MOVING.equals(status) && !WAR_ENDED_HALTED.equals(status)) {
            status = STATIONED;
        }
        movementOrderId = nbt.getString("MovementOrderId");
        source = nbt.hasKey("Source") ? nbt.getString("Source") : SOURCE_MANUAL_LEGACY;
        if (source == null || source.length() == 0) {
            source = SOURCE_MANUAL_LEGACY;
        }
        createdAtMillis = nbt.getLong("CreatedAtMillis");
        updatedAtMillis = nbt.getLong("UpdatedAtMillis");
        tendency = AGGRESSIVE.equals(nbt.getString("Tendency")) ? AGGRESSIVE : CONSERVATIVE;
        temporaryController = parseUuid(nbt.getString("TemporaryController"));
        temporaryControllerName = nbt.getString("TemporaryControllerName");
        delegatedBy = parseUuid(nbt.getString("DelegatedBy"));
        delegatedByName = nbt.getString("DelegatedByName");
        controllerAuthority = nbt.hasKey("ControllerAuthority") ? nbt.getString("ControllerAuthority") : AUTHORITY_NATIVE;
        delegationAlliancePair = nbt.getString("DelegationAlliancePair");
        delegatedAtMillis = nbt.getLong("DelegatedAtMillis");
        delegationRevocationReason = nbt.getString("DelegationRevocationReason");
        nativeFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("NativeFaction"));
        if (nativeFaction.length() == 0) nativeFaction = faction;
        populationSource = nbt.getString("PopulationSource");
        stewardshipReservation = Math.max(0, nbt.getInteger("StewardshipReservation"));
        authorizationReason = nbt.getString("AuthorizationReason");
        stewardshipCreated = nbt.getBoolean("StewardshipCreated");
        withdrawalState = nbt.hasKey("WithdrawalState") ? nbt.getString("WithdrawalState") : CLEANUP_NONE;
        transferRecipient = parseUuid(nbt.getString("TransferRecipient"));
        transferRecipientName = nbt.getString("TransferRecipientName");
        transferOfferedBy = parseUuid(nbt.getString("TransferOfferedBy"));
        transferOfferedAtMillis = Math.max(0L, nbt.getLong("TransferOfferedAtMillis"));
        transferExpiresAtMillis = Math.max(0L, nbt.getLong("TransferExpiresAtMillis"));
        authorizedWarIds.clear();
        NBTTagList warList = nbt.getTagList("AuthorizedWars", 10);
        for (int i = 0; i < warList.tagCount(); i++) {
            String warId = warList.getCompoundTagAt(i).getString("War");
            if (warId.length() > 0 && !authorizedWarIds.contains(warId)) authorizedWarIds.add(warId);
        }
        units.clear();
        NBTTagList unitList = nbt.getTagList("Units", 10);
        for (int i = 0; i < unitList.tagCount(); i++) {
            String unit = unitList.getCompoundTagAt(i).getString("Unit");
            if (unit.length() > 0) {
                units.add(UUID.fromString(unit));
            }
        }
    }

    public boolean isTemporarilyControlledBy(UUID player) {
        return player != null && player.equals(temporaryController);
    }

    public void clearTemporaryController(String reason) {
        temporaryController = null;
        temporaryControllerName = "";
        delegatedBy = null;
        delegatedByName = "";
        controllerAuthority = AUTHORITY_NATIVE;
        delegationAlliancePair = "";
        delegatedAtMillis = 0L;
        delegationRevocationReason = reason == null ? "" : reason;
    }

    public void clearTransferOffer() {
        transferRecipient = null;
        transferRecipientName = "";
        transferOfferedBy = null;
        transferOfferedAtMillis = 0L;
        transferExpiresAtMillis = 0L;
    }

    private static UUID parseUuid(String value) {
        try {
            return value == null || value.length() == 0 ? null : UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
