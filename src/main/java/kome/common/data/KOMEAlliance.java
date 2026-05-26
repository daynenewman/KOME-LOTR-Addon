package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;

public class KOMEAlliance {
    public static final String CIVIL = "civil";
    public static final String MILITARY = "military";
    public static final String TRADE = "trade";

    public String factionA = "";
    public String factionB = "";
    public int civilTier = -1;
    public int militaryTier = -1;
    public int tradeTier = -1;
    public String lastUpdatedBy = "";
    public long updatedWorldTime;

    public KOMEAlliance(String factionA, String factionB) {
        this.factionA = normalizeFactionKey(factionA);
        this.factionB = normalizeFactionKey(factionB);
    }

    public int getTier(String type) {
        if (CIVIL.equals(type)) {
            return civilTier;
        }
        if (MILITARY.equals(type)) {
            return militaryTier;
        }
        if (TRADE.equals(type)) {
            return tradeTier;
        }
        return -1;
    }

    public void setTier(String type, int tier, String updatedBy, long worldTime) {
        if (CIVIL.equals(type)) {
            civilTier = tier;
        } else if (MILITARY.equals(type)) {
            militaryTier = tier;
        } else if (TRADE.equals(type)) {
            tradeTier = tier;
        }
        lastUpdatedBy = updatedBy == null ? "" : updatedBy;
        updatedWorldTime = worldTime;
    }

    public boolean hasAnyAlliance() {
        return civilTier >= 0 || militaryTier >= 0 || tradeTier >= 0;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        factionA = normalizeFactionKey(nbt.getString("FactionA"));
        factionB = normalizeFactionKey(nbt.getString("FactionB"));
        civilTier = nbt.hasKey("CivilTier") ? nbt.getInteger("CivilTier") : -1;
        militaryTier = nbt.hasKey("MilitaryTier") ? nbt.getInteger("MilitaryTier") : -1;
        tradeTier = nbt.hasKey("TradeTier") ? nbt.getInteger("TradeTier") : -1;
        lastUpdatedBy = nbt.getString("LastUpdatedBy");
        updatedWorldTime = nbt.getLong("UpdatedWorldTime");
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("FactionA", normalizeFactionKey(factionA));
        nbt.setString("FactionB", normalizeFactionKey(factionB));
        nbt.setInteger("CivilTier", civilTier);
        nbt.setInteger("MilitaryTier", militaryTier);
        nbt.setInteger("TradeTier", tradeTier);
        nbt.setString("LastUpdatedBy", lastUpdatedBy == null ? "" : lastUpdatedBy);
        nbt.setLong("UpdatedWorldTime", updatedWorldTime);
        return nbt;
    }

    public static String normalizeType(String type) {
        String value = type == null ? "" : type.trim().toLowerCase();
        if ("civ".equals(value)) {
            return CIVIL;
        }
        if ("mil".equals(value)) {
            return MILITARY;
        }
        return value;
    }

    public static boolean isValidType(String type) {
        String value = normalizeType(type);
        return CIVIL.equals(value) || MILITARY.equals(value) || TRADE.equals(value);
    }

    public static int maxTier(String type) {
        String value = normalizeType(type);
        if (MILITARY.equals(value)) {
            return 4;
        }
        if (CIVIL.equals(value) || TRADE.equals(value)) {
            return 2;
        }
        return -1;
    }

    public static String pairKey(String factionA, String factionB) {
        String a = normalizeFactionKey(factionA);
        String b = normalizeFactionKey(factionB);
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    public static String normalizeFactionKey(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
