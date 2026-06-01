package kome.common.data;

import cpw.mods.fml.common.FMLCommonHandler;
import kome.common.KOMEReflection;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketConquestData;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class KOMEWorldData extends WorldSavedData {
    private static final String DATA_NAME = "KOME_ServerRules";

    public final Map<UUID, KOMEPlayerPopulation> populations = new HashMap<>();
    public final Map<UUID, KOMEPlayerProgression> progressions = new HashMap<>();
    public final Map<UUID, KOMEHiredUnitRecord> hiredUnits = new HashMap<>();
    public final Map<String, KOMEConquestTile> conquestTiles = new HashMap<>();
    public final Map<String, KOMEAlliance> alliances = new HashMap<>();
    public final Map<UUID, String> playerNames = new HashMap<>();
    private final Map<String, UUID> kingsByFaction = new HashMap<>();
    private final Map<String, String> kingNamesByFaction = new HashMap<>();
    private boolean progressionEnabled = true;

    public KOMEWorldData() {
        super(DATA_NAME);
    }

    public KOMEWorldData(String name) {
        super(name);
    }

    public static KOMEWorldData get(World world) {
        if (KOMEReflection.isRemote(world)) {
            return KOMEClientData.INSTANCE;
        }
        MapStorage storage = KOMEReflection.getMapStorage(world);
        KOMEWorldData data = (KOMEWorldData) storage.loadData(KOMEWorldData.class, DATA_NAME);
        if (data == null) {
            data = new KOMEWorldData();
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    public KOMEPlayerPopulation getPopulation(UUID player) {
        KOMEPlayerPopulation pop = populations.get(player);
        if (pop == null) {
            pop = new KOMEPlayerPopulation();
            populations.put(player, pop);
        }
        return pop;
    }

    public KOMEPlayerProgression getProgression(UUID player) {
        KOMEPlayerProgression progression = progressions.get(player);
        if (progression == null) {
            progression = new KOMEPlayerProgression();
            progressions.put(player, progression);
        }
        return progression;
    }

    public KOMEConquestTile getConquestTile(String tileId) {
        String normalized = KOMEConquestTile.normalizeId(tileId);
        KOMEConquestTile tile = conquestTiles.get(normalized);
        if (tile == null) {
            tile = new KOMEConquestTile(normalized);
            conquestTiles.put(normalized, tile);
        }
        return tile;
    }

    public KOMEAlliance getAlliance(String factionA, String factionB, boolean create) {
        String key = KOMEAlliance.directionKey(factionA, factionB);
        KOMEAlliance alliance = alliances.get(key);
        if (alliance == null && create) {
            alliance = new KOMEAlliance(factionA, factionB);
            alliances.put(key, alliance);
        }
        return alliance;
    }

    public int getAllianceTier(String type, String factionA, String factionB) {
        KOMEAlliance alliance = getAlliance(factionA, factionB, false);
        return alliance == null ? -1 : alliance.getTier(KOMEAlliance.normalizeType(type));
    }

    public boolean clearAlliance(String factionA, String factionB) {
        String key = KOMEAlliance.directionKey(factionA, factionB);
        boolean removed = alliances.remove(key) != null;
        if (removed) {
            markDirty();
        }
        return removed;
    }

    public void rememberPlayerName(UUID playerID, String playerName) {
        if (playerID == null || playerName == null || playerName.trim().isEmpty()) {
            return;
        }
        String previous = playerNames.put(playerID, playerName);
        if (!playerName.equals(previous)) {
            markDirty();
        }
    }

    public boolean claimFactionKing(String factionKey, String factionName, UUID playerID, String playerName) {
        String key = normalizeFactionKey(factionKey);
        if (key.length() == 0 || playerID == null) {
            return false;
        }
        UUID existing = kingsByFaction.get(key);
        if (existing == null) {
            kingsByFaction.put(key, playerID);
            kingNamesByFaction.put(key, playerName == null ? "" : playerName);
            markDirty();
            return true;
        }
        if (existing.equals(playerID)) {
            String currentName = kingNamesByFaction.get(key);
            if (playerName != null && playerName.length() > 0 && !playerName.equals(currentName)) {
                kingNamesByFaction.put(key, playerName);
                markDirty();
            }
            return true;
        }
        return false;
    }

    public boolean isFactionKing(String factionKey, UUID playerID) {
        String key = normalizeFactionKey(factionKey);
        return key.length() > 0 && playerID != null && playerID.equals(kingsByFaction.get(key));
    }

    public boolean hasFactionKing(String factionKey) {
        String key = normalizeFactionKey(factionKey);
        return key.length() > 0 && kingsByFaction.containsKey(key);
    }

    public String getFactionKingName(String factionKey) {
        String key = normalizeFactionKey(factionKey);
        String name = kingNamesByFaction.get(key);
        return name == null ? "" : name;
    }

    public int getFactionFarmerPop(String factionKey) {
        String key = normalizeFactionKey(factionKey);
        if (key.length() == 0) {
            return 0;
        }
        int total = 0;
        for (Map.Entry<UUID, KOMEPlayerPopulation> entry : populations.entrySet()) {
            UUID playerID = entry.getKey();
            KOMEPlayerProgression progression = progressions.get(playerID);
            boolean member = progression != null && key.equals(normalizeFactionKey(progression.getPledgedLordFaction()));
            if (!member && playerID != null && playerID.equals(kingsByFaction.get(key))) {
                member = true;
            }
            if (member && entry.getValue() != null) {
                total += entry.getValue().getFarmhandLimit() * 25;
            }
        }
        return Math.max(0, total - getFactionFarmerPopSpent(key));
    }

    public int getFarmhandLimit(UUID owner) {
        KOMEPlayerPopulation pop = getPopulation(owner);
        return Math.max(0, pop.getFarmhandLimit() - getFactionFarmerSlotsSpent(getPlayerFactionKey(owner)));
    }

    private String getPlayerFactionKey(UUID playerID) {
        KOMEPlayerProgression progression = progressions.get(playerID);
        String key = progression == null ? "" : normalizeFactionKey(progression.getPledgedLordFaction());
        if (key.length() > 0) {
            return key;
        }
        for (Map.Entry<String, UUID> entry : kingsByFaction.entrySet()) {
            if (playerID != null && playerID.equals(entry.getValue())) {
                return entry.getKey();
            }
        }
        return "";
    }

    private int getFactionFarmerSlotsSpent(String factionKey) {
        return getFactionFarmerPopSpent(factionKey) / 25;
    }

    private int getFactionFarmerPopSpent(String factionKey) {
        String key = normalizeFactionKey(factionKey);
        if (key.length() == 0) {
            return 0;
        }
        int spent = 0;
        for (KOMEAlliance alliance : alliances.values()) {
            if (alliance != null && alliance.tradeTier >= 2 && key.equals(normalizeFactionKey(alliance.factionA))) {
                spent += 50;
            }
        }
        return spent;
    }

    public boolean isProgressionEnabled() {
        return progressionEnabled;
    }

    public void setProgressionEnabled(boolean enabled) {
        if (progressionEnabled != enabled) {
            progressionEnabled = enabled;
            markDirty();
        }
    }

    private static String normalizeFactionKey(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    public int getFarmhandsUsed(UUID owner) {
        int count = 0;
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record.farmhand && owner.equals(record.owner)) {
                count++;
            }
        }
        return count;
    }

    public int getArmyPopulationUsed(UUID owner) {
        return getArmyPopulationUsed(owner, null);
    }

    public int getArmyPopulationUsed(UUID owner, KOMEPopulationType type) {
        int used = 0;
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (!record.farmhand && owner.equals(record.owner) && (type == null || record.type == type)) {
                used += Math.max(0, record.cost);
            }
        }
        return used;
    }

    public int getTrackedUnitCount(UUID owner, boolean farmhands) {
        int count = 0;
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (record.farmhand == farmhands && owner.equals(record.owner)) {
                count++;
            }
        }
        return count;
    }

    public void removeInactiveLoadedHiredUnits(World world, UUID owner) {
        Set<UUID> inactiveUnits = new HashSet<>();
        for (Object object : world.loadedEntityList) {
            if (!(object instanceof LOTREntityNPC)) {
                continue;
            }
            LOTREntityNPC npc = (LOTREntityNPC) object;
            UUID entityID = KOMEReflection.getEntityUUID(npc);
            KOMEHiredUnitRecord record = hiredUnits.get(entityID);
            if (record == null || owner != null && !owner.equals(record.owner)) {
                continue;
            }
            if (!npc.isEntityAlive() || !npc.hiredNPCInfo.isActive) {
                inactiveUnits.add(entityID);
            }
        }
        for (UUID entityID : inactiveUnits) {
            hiredUnits.remove(entityID);
        }
        if (!inactiveUnits.isEmpty()) {
            markDirty();
        }
    }

    public void syncConquestTiles() {
        KOMEPacketConquestData packet = new KOMEPacketConquestData(this);
        for (Object player : FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().playerEntityList) {
            KOMEPacketHandler.network.sendTo(packet, (EntityPlayerMP) player);
        }
    }

    public void syncConquestTiles(EntityPlayerMP player) {
        KOMEPacketHandler.network.sendTo(new KOMEPacketConquestData(this), player);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        populations.clear();
        progressions.clear();
        hiredUnits.clear();
        conquestTiles.clear();
        alliances.clear();
        playerNames.clear();
        kingsByFaction.clear();
        kingNamesByFaction.clear();
        progressionEnabled = !nbt.hasKey("ProgressionEnabled") || nbt.getBoolean("ProgressionEnabled");

        NBTTagList popList = nbt.getTagList("Populations", 10);
        for (int i = 0; i < popList.tagCount(); i++) {
            NBTTagCompound entry = popList.getCompoundTagAt(i);
            KOMEPlayerPopulation pop = new KOMEPlayerPopulation();
            pop.readFromNBT(entry);
            populations.put(UUID.fromString(entry.getString("Player")), pop);
        }

        NBTTagList progressionList = nbt.getTagList("Progressions", 10);
        for (int i = 0; i < progressionList.tagCount(); i++) {
            NBTTagCompound entry = progressionList.getCompoundTagAt(i);
            KOMEPlayerProgression progression = new KOMEPlayerProgression();
            progression.readFromNBT(entry);
            progressions.put(UUID.fromString(entry.getString("Player")), progression);
        }

        NBTTagList playerNameList = nbt.getTagList("PlayerNames", 10);
        for (int i = 0; i < playerNameList.tagCount(); i++) {
            NBTTagCompound entry = playerNameList.getCompoundTagAt(i);
            String player = entry.getString("Player");
            String name = entry.getString("Name");
            if (player.length() > 0 && name.length() > 0) {
                playerNames.put(UUID.fromString(player), name);
            }
        }

        NBTTagList hiredList = nbt.getTagList("HiredUnits", 10);
        for (int i = 0; i < hiredList.tagCount(); i++) {
            KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
            record.readFromNBT(hiredList.getCompoundTagAt(i));
            hiredUnits.put(record.entity, record);
        }

        NBTTagList conquestList = nbt.getTagList("ConquestTiles", 10);
        for (int i = 0; i < conquestList.tagCount(); i++) {
            KOMEConquestTile tile = new KOMEConquestTile("");
            tile.readFromNBT(conquestList.getCompoundTagAt(i));
            if (!tile.id.isEmpty()) {
                conquestTiles.put(tile.id, tile);
            }
        }

        NBTTagList allianceList = nbt.getTagList("Alliances", 10);
        for (int i = 0; i < allianceList.tagCount(); i++) {
            KOMEAlliance alliance = new KOMEAlliance("", "");
            alliance.readFromNBT(allianceList.getCompoundTagAt(i));
            if (alliance.factionA.length() > 0 && alliance.factionB.length() > 0 && alliance.hasAnyAlliance()) {
                alliances.put(KOMEAlliance.directionKey(alliance.factionA, alliance.factionB), alliance);
            }
        }

        NBTTagList kingList = nbt.getTagList("FactionKings", 10);
        for (int i = 0; i < kingList.tagCount(); i++) {
            NBTTagCompound entry = kingList.getCompoundTagAt(i);
            String faction = normalizeFactionKey(entry.getString("Faction"));
            String player = entry.getString("Player");
            if (faction.length() > 0 && player.length() > 0) {
                kingsByFaction.put(faction, UUID.fromString(player));
                kingNamesByFaction.put(faction, entry.getString("Name"));
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setBoolean("ProgressionEnabled", progressionEnabled);

        NBTTagList popList = new NBTTagList();
        for (Map.Entry<UUID, KOMEPlayerPopulation> entry : populations.entrySet()) {
            NBTTagCompound pop = entry.getValue().writeToNBT();
            pop.setString("Player", entry.getKey().toString());
            popList.appendTag(pop);
        }
        nbt.setTag("Populations", popList);

        NBTTagList progressionList = new NBTTagList();
        for (Map.Entry<UUID, KOMEPlayerProgression> entry : progressions.entrySet()) {
            NBTTagCompound progression = entry.getValue().writeToNBT();
            progression.setString("Player", entry.getKey().toString());
            progressionList.appendTag(progression);
        }
        nbt.setTag("Progressions", progressionList);

        NBTTagList playerNameList = new NBTTagList();
        for (Map.Entry<UUID, String> entry : playerNames.entrySet()) {
            NBTTagCompound playerName = new NBTTagCompound();
            playerName.setString("Player", entry.getKey().toString());
            playerName.setString("Name", entry.getValue() == null ? "" : entry.getValue());
            playerNameList.appendTag(playerName);
        }
        nbt.setTag("PlayerNames", playerNameList);

        NBTTagList hiredList = new NBTTagList();
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            hiredList.appendTag(record.writeToNBT());
        }
        nbt.setTag("HiredUnits", hiredList);

        NBTTagList conquestList = new NBTTagList();
        for (KOMEConquestTile tile : conquestTiles.values()) {
            conquestList.appendTag(tile.writeToNBT());
        }
        nbt.setTag("ConquestTiles", conquestList);

        NBTTagList allianceList = new NBTTagList();
        for (KOMEAlliance alliance : alliances.values()) {
            if (alliance != null && alliance.hasAnyAlliance()) {
                allianceList.appendTag(alliance.writeToNBT());
            }
        }
        nbt.setTag("Alliances", allianceList);

        NBTTagList kingList = new NBTTagList();
        for (Map.Entry<String, UUID> entry : kingsByFaction.entrySet()) {
            NBTTagCompound king = new NBTTagCompound();
            king.setString("Faction", entry.getKey());
            king.setString("Player", entry.getValue().toString());
            String name = kingNamesByFaction.get(entry.getKey());
            king.setString("Name", name == null ? "" : name);
            kingList.appendTag(king);
        }
        nbt.setTag("FactionKings", kingList);
    }
}
