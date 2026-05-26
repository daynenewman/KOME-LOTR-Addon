package kome.common.data;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.item.ItemStack;
import java.util.HashMap;
import java.util.Map;

public class KOMEAlliance {
    public static final String CIVIL = "civil";
    public static final String MILITARY = "military";
    public static final String TRADE = "trade";
    public static final int STORAGE_SLOTS = 54;
    public static final int NONE = -1;
    public static final int PENDING = -2;

    public String factionA = "";
    public String factionB = "";
    public int civilTier = -1;
    public int militaryTier = -1;
    public int tradeTier = -1;
    public String lastUpdatedBy = "";
    public long updatedWorldTime;
    private final ItemStack[] storage = new ItemStack[STORAGE_SLOTS];
    private final Map<String, String> assignments = new HashMap<String, String>();
    private final Map<String, Integer> delivered = new HashMap<String, Integer>();

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
        return civilTier != NONE || militaryTier != NONE || tradeTier != NONE;
    }

    public boolean hasAccepted(String type) {
        return getTier(normalizeType(type)) >= 0;
    }

    public String getAssignment(String id) {
        String value = assignments.get(id);
        return value == null ? "" : value;
    }

    public void setAssignment(String id, String value) {
        if (value == null || value.trim().isEmpty()) {
            assignments.remove(id);
        } else {
            assignments.put(id, value);
        }
    }

    public int getDelivered(String id) {
        Integer value = delivered.get(id);
        return value == null ? 0 : value.intValue();
    }

    public void addDelivered(String id, int amount) {
        if (amount > 0) {
            delivered.put(id, Integer.valueOf(getDelivered(id) + amount));
        }
    }

    public ItemStack getStorage(int slot) {
        return slot >= 0 && slot < storage.length ? storage[slot] : null;
    }

    public void setStorage(int slot, ItemStack stack) {
        if (slot >= 0 && slot < storage.length) {
            storage[slot] = stack;
        }
    }

    public ItemStack decrStorage(int slot, int count) {
        ItemStack stack = getStorage(slot);
        if (stack == null) {
            return null;
        }
        if (stack.stackSize <= count) {
            setStorage(slot, null);
            return stack;
        }
        ItemStack split = stack.splitStack(count);
        if (stack.stackSize <= 0) {
            setStorage(slot, null);
        }
        return split;
    }

    public void readFromNBT(NBTTagCompound nbt) {
        factionA = normalizeFactionKey(nbt.getString("FactionA"));
        factionB = normalizeFactionKey(nbt.getString("FactionB"));
        civilTier = nbt.hasKey("CivilTier") ? nbt.getInteger("CivilTier") : -1;
        militaryTier = nbt.hasKey("MilitaryTier") ? nbt.getInteger("MilitaryTier") : -1;
        tradeTier = nbt.hasKey("TradeTier") ? nbt.getInteger("TradeTier") : -1;
        lastUpdatedBy = nbt.getString("LastUpdatedBy");
        updatedWorldTime = nbt.getLong("UpdatedWorldTime");
        assignments.clear();
        delivered.clear();
        NBTTagList assignmentList = nbt.getTagList("Assignments", 10);
        for (int i = 0; i < assignmentList.tagCount(); i++) {
            NBTTagCompound entry = assignmentList.getCompoundTagAt(i);
            assignments.put(entry.getString("ID"), entry.getString("Value"));
        }
        NBTTagList deliveredList = nbt.getTagList("Delivered", 10);
        for (int i = 0; i < deliveredList.tagCount(); i++) {
            NBTTagCompound entry = deliveredList.getCompoundTagAt(i);
            delivered.put(entry.getString("ID"), Integer.valueOf(entry.getInteger("Amount")));
        }
        for (int i = 0; i < storage.length; i++) {
            storage[i] = null;
        }
        NBTTagList storageList = nbt.getTagList("Storage", 10);
        for (int i = 0; i < storageList.tagCount(); i++) {
            NBTTagCompound entry = storageList.getCompoundTagAt(i);
            int slot = entry.getByte("Slot") & 255;
            if (slot >= 0 && slot < storage.length) {
                storage[slot] = ItemStack.loadItemStackFromNBT(entry);
            }
        }
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
        NBTTagList assignmentList = new NBTTagList();
        for (Map.Entry<String, String> entry : assignments.entrySet()) {
            NBTTagCompound item = new NBTTagCompound();
            item.setString("ID", entry.getKey());
            item.setString("Value", entry.getValue());
            assignmentList.appendTag(item);
        }
        nbt.setTag("Assignments", assignmentList);
        NBTTagList deliveredList = new NBTTagList();
        for (Map.Entry<String, Integer> entry : delivered.entrySet()) {
            NBTTagCompound item = new NBTTagCompound();
            item.setString("ID", entry.getKey());
            item.setInteger("Amount", entry.getValue().intValue());
            deliveredList.appendTag(item);
        }
        nbt.setTag("Delivered", deliveredList);
        NBTTagList storageList = new NBTTagList();
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] != null) {
                NBTTagCompound entry = new NBTTagCompound();
                entry.setByte("Slot", (byte) i);
                storage[i].writeToNBT(entry);
                storageList.appendTag(entry);
            }
        }
        nbt.setTag("Storage", storageList);
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
        return directionKey(factionA, factionB);
    }

    public static String directionKey(String senderFaction, String receiverFaction) {
        return normalizeFactionKey(senderFaction) + ">" + normalizeFactionKey(receiverFaction);
    }

    public static String normalizeFactionKey(String value) {
        return value == null ? "" : value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}
