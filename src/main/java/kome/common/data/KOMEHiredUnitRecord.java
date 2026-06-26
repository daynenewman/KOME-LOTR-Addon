package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

public class KOMEHiredUnitRecord {
    public static final String SOURCE_TILE_POOL = "TILE_POOL";
    public static final String SOURCE_PLAYER_RESERVE = "PLAYER_RESERVE";

    public UUID entity;
    public UUID owner;
    public KOMEPopulationType type = KOMEPopulationType.OFFENSIVE;
    public int cost = 25;
    public int baseCost = 25;
    public int level = 1;
    public int levelCap;
    public boolean farmhand;
    public boolean mounted;
    public String unitName = "";
    public String sourceType = SOURCE_TILE_POOL;
    public UUID sourcePlayer;
    public String sourceTileId = "";
    public String sourceFaction = "";
    public String allocationTileId = "";
    public String allocationFaction = "";
    public UUID allocationPlayer;
    public String currentTile = "";
    public String companyId = "";
    public String companyName = "";
    public String lotrCompanyValue = "";
    public long companyAssignedAtMillis;
    public UUID companyAssignedBy;
    public String companyAssignedByName = "";
    public String movementOrderId = "";
    public NBTTagCompound movingEntityData;
    public NBTTagCompound stationedEntityData;

    public void readFromNBT(NBTTagCompound nbt) {
        entity = UUID.fromString(nbt.getString("Entity"));
        owner = UUID.fromString(nbt.getString("Owner"));
        KOMEPopulationType readType = KOMEPopulationType.forName(nbt.getString("Type"));
        type = readType == null ? KOMEPopulationType.OFFENSIVE : readType;
        cost = nbt.getInteger("Cost");
        baseCost = nbt.hasKey("BaseCost") ? nbt.getInteger("BaseCost") : 25;
        level = nbt.hasKey("Level") ? Math.max(1, nbt.getInteger("Level")) : 1;
        levelCap = Math.max(0, nbt.getInteger("LevelCap"));
        farmhand = nbt.getBoolean("Farmhand");
        mounted = nbt.getBoolean("Mounted");
        unitName = nbt.getString("UnitName");
        sourceType = nbt.hasKey("SourceType") ? nbt.getString("SourceType") : SOURCE_TILE_POOL;
        if (!SOURCE_PLAYER_RESERVE.equals(sourceType)) {
            sourceType = SOURCE_TILE_POOL;
        }
        String savedSourcePlayer = nbt.getString("SourcePlayer");
        sourcePlayer = savedSourcePlayer.length() == 0 ? owner : UUID.fromString(savedSourcePlayer);
        sourceTileId = KOMEConquestTile.normalizeId(nbt.getString("SourceTile"));
        sourceFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("SourceFaction"));
        allocationTileId = KOMEConquestTile.normalizeId(nbt.getString("AllocationTile"));
        allocationFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("AllocationFaction"));
        String savedAllocationPlayer = nbt.getString("AllocationPlayer");
        allocationPlayer = savedAllocationPlayer.length() == 0 ? null : UUID.fromString(savedAllocationPlayer);
        currentTile = KOMEConquestTile.normalizeId(nbt.getString("CurrentTile"));
        companyId = nbt.getString("CompanyId");
        companyName = normalizeCompanyName(nbt.getString("CompanyName"));
        lotrCompanyValue = normalizeCompanyName(nbt.getString("LotrCompanyValue"));
        companyAssignedAtMillis = nbt.getLong("CompanyAssignedAtMillis");
        String assignedBy = nbt.getString("CompanyAssignedBy");
        companyAssignedBy = assignedBy.length() == 0 ? null : UUID.fromString(assignedBy);
        companyAssignedByName = nbt.getString("CompanyAssignedByName");
        if (sourceTileId.length() == 0) {
            sourceTileId = currentTile;
        }
        movementOrderId = nbt.getString("MovementOrderId");
        movingEntityData = nbt.hasKey("MovingEntityData", 10) ? nbt.getCompoundTag("MovingEntityData") : null;
        stationedEntityData = nbt.hasKey("StationedEntityData", 10) ? nbt.getCompoundTag("StationedEntityData") : null;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Entity", entity.toString());
        nbt.setString("Owner", owner.toString());
        nbt.setString("Type", type.key);
        nbt.setInteger("Cost", cost);
        nbt.setInteger("BaseCost", baseCost);
        nbt.setInteger("Level", level);
        nbt.setInteger("LevelCap", levelCap);
        nbt.setBoolean("Farmhand", farmhand);
        nbt.setBoolean("Mounted", mounted);
        nbt.setString("UnitName", unitName == null ? "" : unitName);
        nbt.setString("SourceType", sourceType == null ? SOURCE_TILE_POOL : sourceType);
        nbt.setString("SourcePlayer", (sourcePlayer == null ? owner : sourcePlayer).toString());
        nbt.setString("SourceTile", KOMEConquestTile.normalizeId(sourceTileId));
        nbt.setString("SourceFaction", KOMEAlliance.normalizeFactionKey(sourceFaction));
        nbt.setString("AllocationTile", KOMEConquestTile.normalizeId(allocationTileId));
        nbt.setString("AllocationFaction", KOMEAlliance.normalizeFactionKey(allocationFaction));
        nbt.setString("AllocationPlayer", allocationPlayer == null ? "" : allocationPlayer.toString());
        nbt.setString("CurrentTile", KOMEConquestTile.normalizeId(currentTile));
        nbt.setString("CompanyId", companyId == null ? "" : companyId);
        nbt.setString("CompanyName", companyName == null ? "" : companyName);
        nbt.setString("LotrCompanyValue", lotrCompanyValue == null ? "" : lotrCompanyValue);
        nbt.setLong("CompanyAssignedAtMillis", companyAssignedAtMillis);
        nbt.setString("CompanyAssignedBy", companyAssignedBy == null ? "" : companyAssignedBy.toString());
        nbt.setString("CompanyAssignedByName", companyAssignedByName == null ? "" : companyAssignedByName);
        nbt.setString("MovementOrderId", movementOrderId == null ? "" : movementOrderId);
        if (movingEntityData != null) {
            nbt.setTag("MovingEntityData", movingEntityData);
        }
        if (stationedEntityData != null) {
            nbt.setTag("StationedEntityData", stationedEntityData);
        }
        return nbt;
    }

    public boolean isPlayerReserveFunded() {
        return SOURCE_PLAYER_RESERVE.equals(sourceType);
    }

    public boolean isMoving() {
        return movementOrderId != null && movementOrderId.length() > 0;
    }

    public static String normalizeCompanyName(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder();
        boolean skipColorCode = false;
        String trimmed = value.trim();
        for (int i = 0; i < trimmed.length() && out.length() < 24; i++) {
            char c = trimmed.charAt(i);
            if (skipColorCode) {
                skipColorCode = false;
                continue;
            }
            if (c == '\u00a7') {
                skipColorCode = true;
                continue;
            }
            if (Character.isLetterOrDigit(c) || c == ' ' || c == '-' || c == '\'') {
                out.append(c);
            }
        }
        return out.toString().trim().replaceAll(" +", " ");
    }
}
