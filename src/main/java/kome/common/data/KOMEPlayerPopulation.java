package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

public class KOMEPlayerPopulation {
    public int offensiveTotal;
    public int offensiveUsed;
    public int defensiveTotal;
    public int defensiveUsed;
    public KOMEPopulationType hireType = KOMEPopulationType.OFFENSIVE;

    public int getTotal(KOMEPopulationType type) {
        return type == KOMEPopulationType.DEFENSIVE ? defensiveTotal : offensiveTotal;
    }

    public int getUsed(KOMEPopulationType type) {
        return type == KOMEPopulationType.DEFENSIVE ? defensiveUsed : offensiveUsed;
    }

    public int getAvailable(KOMEPopulationType type) {
        return Math.max(0, getTotal(type) - getUsed(type));
    }

    public int getCombinedTotal() {
        return offensiveTotal + defensiveTotal;
    }

    public int getFarmhandLimit() {
        return getCombinedTotal() / 25;
    }

    public void setTotal(KOMEPopulationType type, int amount) {
        amount = Math.max(0, amount);
        if (type == KOMEPopulationType.DEFENSIVE) {
            defensiveTotal = amount;
            defensiveUsed = Math.min(defensiveUsed, defensiveTotal);
        } else {
            offensiveTotal = amount;
            offensiveUsed = Math.min(offensiveUsed, offensiveTotal);
        }
    }

    public void addTotal(KOMEPopulationType type, int amount) {
        setTotal(type, getTotal(type) + amount);
    }

    public boolean tryUse(KOMEPopulationType type, int amount) {
        amount = Math.max(0, amount);
        if (getAvailable(type) < amount) {
            return false;
        }
        if (type == KOMEPopulationType.DEFENSIVE) {
            defensiveUsed += amount;
        } else {
            offensiveUsed += amount;
        }
        return true;
    }

    public void release(KOMEPopulationType type, int amount) {
        amount = Math.max(0, amount);
        if (type == KOMEPopulationType.DEFENSIVE) {
            defensiveUsed = Math.max(0, defensiveUsed - amount);
        } else {
            offensiveUsed = Math.max(0, offensiveUsed - amount);
        }
    }

    public void adjustUsed(KOMEPopulationType type, int amount) {
        if (type == KOMEPopulationType.DEFENSIVE) {
            defensiveUsed = Math.max(0, defensiveUsed + amount);
        } else {
            offensiveUsed = Math.max(0, offensiveUsed + amount);
        }
    }

    public void readFromNBT(NBTTagCompound nbt) {
        offensiveTotal = nbt.getInteger("OffensiveTotal");
        offensiveUsed = nbt.getInteger("OffensiveUsed");
        defensiveTotal = nbt.getInteger("DefensiveTotal");
        defensiveUsed = nbt.getInteger("DefensiveUsed");
        KOMEPopulationType readHireType = KOMEPopulationType.forName(nbt.getString("HireType"));
        hireType = readHireType == null ? KOMEPopulationType.OFFENSIVE : readHireType;
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setInteger("OffensiveTotal", offensiveTotal);
        nbt.setInteger("OffensiveUsed", offensiveUsed);
        nbt.setInteger("DefensiveTotal", defensiveTotal);
        nbt.setInteger("DefensiveUsed", defensiveUsed);
        nbt.setString("HireType", (hireType == null ? KOMEPopulationType.OFFENSIVE : hireType).key);
        return nbt;
    }
}
