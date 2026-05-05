package kome.common.data;

import cpw.mods.fml.common.FMLCommonHandler;
import kome.common.KOMEReflection;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketTerritoryData;
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
    public final Map<String, KOMETerritory> territories = new HashMap<>();
    public final Map<UUID, KOMEHiredUnitRecord> hiredUnits = new HashMap<>();

    public KOMEWorldData() {
        super(DATA_NAME);
    }

    public KOMEWorldData(String name) {
        super(name);
    }

    public static KOMEWorldData get(World world) {
        if (world.isRemote) {
            return KOMEClientData.INSTANCE;
        }
        MapStorage storage = world.mapStorage;
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

    public KOMETerritory getTerritory(String waypoint) {
        KOMETerritory territory = territories.get(waypoint);
        if (territory == null) {
            territory = new KOMETerritory(waypoint);
            territories.put(waypoint, territory);
        }
        return territory;
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
        int used = 0;
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            if (!record.farmhand && owner.equals(record.owner)) {
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

    public void syncTerritories() {
        KOMEPacketTerritoryData packet = new KOMEPacketTerritoryData(this);
        for (Object player : FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().playerEntityList) {
            KOMEPacketHandler.network.sendTo(packet, (EntityPlayerMP) player);
        }
    }

    public void syncTerritories(EntityPlayerMP player) {
        KOMEPacketHandler.network.sendTo(new KOMEPacketTerritoryData(this), player);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        populations.clear();
        territories.clear();
        hiredUnits.clear();

        NBTTagList popList = nbt.getTagList("Populations", 10);
        for (int i = 0; i < popList.tagCount(); i++) {
            NBTTagCompound entry = popList.getCompoundTagAt(i);
            KOMEPlayerPopulation pop = new KOMEPlayerPopulation();
            pop.readFromNBT(entry);
            populations.put(UUID.fromString(entry.getString("Player")), pop);
        }

        NBTTagList territoryList = nbt.getTagList("Territories", 10);
        for (int i = 0; i < territoryList.tagCount(); i++) {
            KOMETerritory territory = new KOMETerritory("");
            territory.readFromNBT(territoryList.getCompoundTagAt(i));
            territories.put(territory.waypoint, territory);
        }

        NBTTagList hiredList = nbt.getTagList("HiredUnits", 10);
        for (int i = 0; i < hiredList.tagCount(); i++) {
            KOMEHiredUnitRecord record = new KOMEHiredUnitRecord();
            record.readFromNBT(hiredList.getCompoundTagAt(i));
            hiredUnits.put(record.entity, record);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList popList = new NBTTagList();
        for (Map.Entry<UUID, KOMEPlayerPopulation> entry : populations.entrySet()) {
            NBTTagCompound pop = entry.getValue().writeToNBT();
            pop.setString("Player", entry.getKey().toString());
            popList.appendTag(pop);
        }
        nbt.setTag("Populations", popList);

        NBTTagList territoryList = new NBTTagList();
        for (KOMETerritory territory : territories.values()) {
            territoryList.appendTag(territory.writeToNBT());
        }
        nbt.setTag("Territories", territoryList);

        NBTTagList hiredList = new NBTTagList();
        for (KOMEHiredUnitRecord record : hiredUnits.values()) {
            hiredList.appendTag(record.writeToNBT());
        }
        nbt.setTag("HiredUnits", hiredList);
    }
}
