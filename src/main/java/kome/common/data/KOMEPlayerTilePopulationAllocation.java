package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

import java.util.UUID;

public class KOMEPlayerTilePopulationAllocation {
    public String tileId = "";
    public String faction = "";
    public UUID playerUuid;
    public String playerName = "";
    public int offensiveAllocated;
    public int offensiveUsed;
    public int defensiveAllocated;
    public int defensiveUsed;

    public int getAllocated(KOMEPopulationType type) {
        return type == KOMEPopulationType.DEFENSIVE ? defensiveAllocated : offensiveAllocated;
    }

    public int getUsed(KOMEPopulationType type) {
        return type == KOMEPopulationType.DEFENSIVE ? defensiveUsed : offensiveUsed;
    }

    public int getAvailable(KOMEPopulationType type) {
        return Math.max(0, getAllocated(type) - getUsed(type));
    }

    public void setAllocated(KOMEPopulationType type, int amount) {
        int value = Math.max(getUsed(type), amount);
        if (type == KOMEPopulationType.DEFENSIVE) {
            defensiveAllocated = value;
        } else {
            offensiveAllocated = value;
        }
    }

    public boolean tryUse(KOMEPopulationType type, int amount) {
        if (amount < 0 || getAvailable(type) < amount) {
            return false;
        }
        addUsed(type, amount);
        return true;
    }

    public void release(KOMEPopulationType type, int amount) {
        addUsed(type, -Math.max(0, amount));
    }

    public void addUsed(KOMEPopulationType type, int amount) {
        if (type == KOMEPopulationType.DEFENSIVE) {
            defensiveUsed = clamp(defensiveUsed + amount, 0, defensiveAllocated);
        } else {
            offensiveUsed = clamp(offensiveUsed + amount, 0, offensiveAllocated);
        }
    }

    public void readFromNBT(NBTTagCompound nbt) {
        tileId = KOMEConquestTile.normalizeId(nbt.getString("Tile"));
        faction = KOMEAlliance.normalizeFactionKey(nbt.getString("Faction"));
        String player = nbt.getString("Player");
        playerUuid = player.length() == 0 ? null : UUID.fromString(player);
        playerName = nbt.getString("PlayerName");
        offensiveAllocated = Math.max(0, nbt.getInteger("OffensiveAllocated"));
        offensiveUsed = clamp(nbt.getInteger("OffensiveUsed"), 0, offensiveAllocated);
        defensiveAllocated = Math.max(0, nbt.getInteger("DefensiveAllocated"));
        defensiveUsed = clamp(nbt.getInteger("DefensiveUsed"), 0, defensiveAllocated);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Tile", KOMEConquestTile.normalizeId(tileId));
        nbt.setString("Faction", KOMEAlliance.normalizeFactionKey(faction));
        nbt.setString("Player", playerUuid == null ? "" : playerUuid.toString());
        nbt.setString("PlayerName", playerName == null ? "" : playerName);
        nbt.setInteger("OffensiveAllocated", offensiveAllocated);
        nbt.setInteger("OffensiveUsed", offensiveUsed);
        nbt.setInteger("DefensiveAllocated", defensiveAllocated);
        nbt.setInteger("DefensiveUsed", defensiveUsed);
        return nbt;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
