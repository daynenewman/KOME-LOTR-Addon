package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

public class KOMETilePopulation {
    public String tileId = "";
    public String sourceFaction = "";
    // Retained for old saves and client compatibility. New code uses sourceFaction.
    public String faction = "";
    public int offensiveTotal;
    public int offensiveUsed;
    public int defensiveTotal;
    public int defensiveUsed;
    public int farmhandTotal;
    public int farmhandUsed;

    public KOMETilePopulation() {
    }

    public KOMETilePopulation(String tileId) {
        this.tileId = KOMEConquestTile.normalizeId(tileId);
    }

    public KOMETilePopulation(String tileId, String sourceFaction) {
        this.tileId = KOMEConquestTile.normalizeId(tileId);
        this.sourceFaction = KOMEAlliance.normalizeFactionKey(sourceFaction);
        this.faction = this.sourceFaction;
    }

    public int getTotal(KOMEPopulationType type) {
        return type == KOMEPopulationType.DEFENSIVE ? defensiveTotal : offensiveTotal;
    }

    public int getUsed(KOMEPopulationType type) {
        return type == KOMEPopulationType.DEFENSIVE ? defensiveUsed : offensiveUsed;
    }

    public int getAvailable(KOMEPopulationType type) {
        return Math.max(0, getTotal(type) - getUsed(type));
    }

    public int getEffectiveTotal(KOMEPopulationType type, String controllingFaction) {
        int total = getTotal(type);
        return sourceFactionMatches(controllingFaction) ? total : total / 2;
    }

    public int getEffectiveAvailable(KOMEPopulationType type, String controllingFaction) {
        return Math.max(0, getEffectiveTotal(type, controllingFaction) - getUsed(type));
    }

    public boolean tryUseEffective(KOMEPopulationType type, int amount, String controllingFaction) {
        if (amount <= 0) {
            return true;
        }
        if (getEffectiveAvailable(type, controllingFaction) < amount) {
            return false;
        }
        addUsed(type, amount);
        return true;
    }

    public void setTotal(KOMEPopulationType type, int value) {
        int total = Math.max(0, value);
        if (type == KOMEPopulationType.DEFENSIVE) {
            defensiveTotal = total;
            defensiveUsed = Math.min(defensiveUsed, defensiveTotal);
        } else {
            offensiveTotal = total;
            offensiveUsed = Math.min(offensiveUsed, offensiveTotal);
        }
    }

    public void addTotal(KOMEPopulationType type, int amount) {
        setTotal(type, getTotal(type) + amount);
    }

    public boolean tryUse(KOMEPopulationType type, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (getAvailable(type) < amount) {
            return false;
        }
        addUsed(type, amount);
        return true;
    }

    public void release(KOMEPopulationType type, int amount) {
        if (amount <= 0) {
            return;
        }
        addUsed(type, -amount);
    }

    public void addUsed(KOMEPopulationType type, int amount) {
        if (type == KOMEPopulationType.DEFENSIVE) {
            defensiveUsed = clamp(defensiveUsed + amount, 0, defensiveTotal);
        } else {
            offensiveUsed = clamp(offensiveUsed + amount, 0, offensiveTotal);
        }
    }

    public int getFarmhandAvailable() {
        return Math.max(0, farmhandTotal - farmhandUsed);
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Tile", KOMEConquestTile.normalizeId(tileId));
        String source = KOMEAlliance.normalizeFactionKey(sourceFaction);
        nbt.setString("SourceFaction", source);
        nbt.setString("Faction", source);
        nbt.setInteger("OffensiveTotal", offensiveTotal);
        nbt.setInteger("OffensiveUsed", offensiveUsed);
        nbt.setInteger("DefensiveTotal", defensiveTotal);
        nbt.setInteger("DefensiveUsed", defensiveUsed);
        nbt.setInteger("FarmhandTotal", farmhandTotal);
        nbt.setInteger("FarmhandUsed", farmhandUsed);
        return nbt;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        tileId = KOMEConquestTile.normalizeId(nbt.getString("Tile"));
        String savedFaction = nbt.hasKey("SourceFaction") ? nbt.getString("SourceFaction") : nbt.getString("Faction");
        sourceFaction = KOMEAlliance.normalizeFactionKey(savedFaction);
        faction = sourceFaction;
        offensiveTotal = Math.max(0, nbt.getInteger("OffensiveTotal"));
        defensiveTotal = Math.max(0, nbt.getInteger("DefensiveTotal"));
        farmhandTotal = Math.max(0, nbt.getInteger("FarmhandTotal"));
        offensiveUsed = clamp(nbt.getInteger("OffensiveUsed"), 0, offensiveTotal);
        defensiveUsed = clamp(nbt.getInteger("DefensiveUsed"), 0, defensiveTotal);
        farmhandUsed = clamp(nbt.getInteger("FarmhandUsed"), 0, farmhandTotal);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean sourceFactionMatches(String controllingFaction) {
        return KOMEAlliance.normalizeFactionKey(sourceFaction).equals(KOMEAlliance.normalizeFactionKey(controllingFaction));
    }
}
