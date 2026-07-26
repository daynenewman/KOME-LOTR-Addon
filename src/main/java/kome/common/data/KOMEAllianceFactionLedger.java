package kome.common.data;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class KOMEAllianceFactionLedger {
    public String faction = "";

    private final ItemStack[] storage = new ItemStack[KOMEAlliance.STORAGE_SLOTS];
    private final List<ItemStack> recoveryStorage = new ArrayList<ItemStack>();
    private final Map<String, String> assignments = new HashMap<String, String>();
    private final Map<String, Integer> delivered = new HashMap<String, Integer>();
    private final Map<String, Integer> completedTiers = new HashMap<String, Integer>();
    private final Map<String, Integer> unlockedTiers = new HashMap<String, Integer>();
    private final Map<String, ItemStack> claimSamples = new HashMap<String, ItemStack>();
    private final Map<String, Integer> claimAmounts = new HashMap<String, Integer>();

    public KOMEAllianceFactionLedger(String faction) {
        this.faction = KOMEAlliance.normalizeFactionKey(faction);
    }

    public String getAssignment(String id) {
        String value = assignments.get(normalizeId(id));
        return value == null ? "" : value;
    }

    public void setAssignment(String id, String value) {
        String key = normalizeId(id);
        if (key.length() == 0 || value == null || value.trim().length() == 0) {
            assignments.remove(key);
        } else {
            assignments.put(key, value.trim());
        }
    }

    public int getDelivered(String id) {
        Integer value = delivered.get(normalizeId(id));
        return value == null ? 0 : Math.max(0, value.intValue());
    }

    public void addDelivered(String id, int amount) {
        String key = normalizeId(id);
        if (key.length() > 0 && amount > 0) {
            long next = (long) getDelivered(key) + (long) amount;
            delivered.put(key, Integer.valueOf((int) Math.min(Integer.MAX_VALUE, next)));
        }
    }

    public void setDelivered(String id, int amount) {
        String key = normalizeId(id);
        if (key.length() == 0 || amount <= 0) {
            delivered.remove(key);
        } else {
            delivered.put(key, Integer.valueOf(amount));
        }
    }

    public int getCompletedTier(String type) {
        Integer value = completedTiers.get(KOMEAlliance.normalizeType(type));
        return value == null ? 0 : Math.max(0, Math.min(KOMEAlliance.maxTier(type), value.intValue()));
    }

    public void setCompletedTier(String type, int tier) {
        String normalizedType = KOMEAlliance.normalizeType(type);
        if (!KOMEAlliance.isValidType(normalizedType)) {
            return;
        }
        int bounded = Math.max(0, Math.min(KOMEAlliance.maxTier(normalizedType), tier));
        completedTiers.put(normalizedType, Integer.valueOf(bounded));
    }

    public boolean hasUnlockedTier(String type) {
        return unlockedTiers.containsKey(KOMEAlliance.normalizeType(type));
    }

    public int getUnlockedTier(String type) {
        Integer value = unlockedTiers.get(KOMEAlliance.normalizeType(type));
        return value == null ? KOMEAlliance.NONE
            : Math.max(0, Math.min(KOMEAlliance.maxTier(type), value.intValue()));
    }

    public void setUnlockedTier(String type, int tier) {
        String normalizedType = KOMEAlliance.normalizeType(type);
        if (!KOMEAlliance.isValidType(normalizedType)) {
            return;
        }
        if (tier < 0) {
            unlockedTiers.remove(normalizedType);
            return;
        }
        unlockedTiers.put(normalizedType, Integer.valueOf(
            Math.max(0, Math.min(KOMEAlliance.maxTier(normalizedType), tier))));
    }

    public ItemStack getClaimSample(String id) {
        ItemStack stack = claimSamples.get(normalizeId(id));
        return stack == null ? null : stack.copy();
    }

    public int getClaimAmount(String id) {
        Integer amount = claimAmounts.get(normalizeId(id));
        return amount == null ? 0 : Math.max(0, amount.intValue());
    }

    public Set<String> getClaimIds() {
        return new HashSet<String>(claimAmounts.keySet());
    }

    public void addClaimGoods(String id, ItemStack sample, int amount) {
        String key = normalizeId(id);
        if (key.length() == 0 || sample == null || amount <= 0) {
            return;
        }
        ItemStack stored = sample.copy();
        stored.stackSize = 1;
        claimSamples.put(key, stored);
        long next = (long) getClaimAmount(key) + (long) amount;
        claimAmounts.put(key, Integer.valueOf((int) Math.min(Integer.MAX_VALUE, next)));
    }

    public void clearClaimGoods(String id) {
        String key = normalizeId(id);
        claimSamples.remove(key);
        claimAmounts.remove(key);
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
        if (stack == null || count <= 0) {
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

    public int getRecoveryStackCount() {
        return recoveryStorage.size();
    }

    public boolean hasStoredOrClaimableGoods() {
        if (!recoveryStorage.isEmpty()) {
            return true;
        }
        for (ItemStack stack : storage) {
            if (stack != null && stack.stackSize > 0) {
                return true;
            }
        }
        for (Integer amount : claimAmounts.values()) {
            if (amount != null && amount.intValue() > 0) {
                return true;
            }
        }
        return false;
    }

    /** Clears relationship-scoped progress while retaining physical/recovery goods for return. */
    public void resetFormalProgress() {
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] != null && storage[i].stackSize > 0) {
                recoveryStorage.add(storage[i].copy());
                storage[i] = null;
            }
        }
        assignments.clear();
        delivered.clear();
        completedTiers.clear();
        unlockedTiers.clear();
    }

    public ItemStack removeRecoveryStack(int index) {
        return index >= 0 && index < recoveryStorage.size() ? recoveryStorage.remove(index) : null;
    }

    public void addRecoveryStack(ItemStack stack) {
        if (stack != null && stack.stackSize > 0) {
            recoveryStorage.add(stack.copy());
        }
    }

    public void mergeFrom(KOMEAllianceFactionLedger other, boolean combineStoredGoods) {
        if (other == null) {
            return;
        }
        for (Map.Entry<String, String> entry : other.assignments.entrySet()) {
            String existing = getAssignment(entry.getKey());
            String candidate = entry.getValue() == null ? "" : entry.getValue();
            if (existing.length() == 0 || candidate.length() > 0 && candidate.compareTo(existing) < 0) {
                setAssignment(entry.getKey(), candidate);
            }
        }
        for (Map.Entry<String, Integer> entry : other.delivered.entrySet()) {
            setDelivered(entry.getKey(), Math.max(getDelivered(entry.getKey()), entry.getValue().intValue()));
        }
        for (Map.Entry<String, Integer> entry : other.completedTiers.entrySet()) {
            setCompletedTier(entry.getKey(), Math.max(getCompletedTier(entry.getKey()), entry.getValue().intValue()));
        }
        for (Map.Entry<String, Integer> entry : other.unlockedTiers.entrySet()) {
            setUnlockedTier(entry.getKey(), Math.max(getUnlockedTier(entry.getKey()), entry.getValue().intValue()));
        }
        if (combineStoredGoods) {
            for (int i = 0; i < other.storage.length; i++) {
                mergeStoredStack(other.storage[i]);
            }
            for (ItemStack stack : other.recoveryStorage) {
                addRecoveryStack(stack);
            }
            for (Map.Entry<String, Integer> entry : other.claimAmounts.entrySet()) {
                ItemStack sample = other.claimSamples.get(entry.getKey());
                if (sample != null && entry.getValue().intValue() > 0) {
                    addClaimGoods(entry.getKey(), sample, entry.getValue().intValue());
                }
            }
        } else {
            for (int i = 0; i < other.storage.length; i++) {
                if (storage[i] == null && other.storage[i] != null) {
                    storage[i] = other.storage[i].copy();
                }
            }
            for (Map.Entry<String, Integer> entry : other.claimAmounts.entrySet()) {
                if (entry.getValue().intValue() > getClaimAmount(entry.getKey())) {
                    ItemStack sample = other.claimSamples.get(entry.getKey());
                    if (sample != null) {
                        claimSamples.put(entry.getKey(), sample.copy());
                        claimAmounts.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }

    public void readFromNBT(NBTTagCompound nbt) {
        faction = KOMEAlliance.normalizeFactionKey(nbt.getString("Faction"));
        assignments.clear();
        delivered.clear();
        completedTiers.clear();
        unlockedTiers.clear();
        claimSamples.clear();
        claimAmounts.clear();
        recoveryStorage.clear();
        for (int i = 0; i < storage.length; i++) {
            storage[i] = null;
        }
        readAssignments(nbt.getTagList("Assignments", 10));
        readDelivered(nbt.getTagList("Delivered", 10));
        readCompleted(nbt.getTagList("CompletedTiers", 10));
        readUnlocked(nbt.getTagList("UnlockedTiers", 10));
        readStorage(nbt.getTagList("Storage", 10));
        readRecovery(nbt.getTagList("RecoveryStorage", 10));
        readClaims(nbt.getTagList("ClaimGoods", 10));
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Faction", KOMEAlliance.normalizeFactionKey(faction));
        nbt.setTag("Assignments", writeStringMap(assignments));
        nbt.setTag("Delivered", writeIntMap(delivered, "Amount"));
        nbt.setTag("CompletedTiers", writeIntMap(completedTiers, "Tier"));
        nbt.setTag("UnlockedTiers", writeIntMap(unlockedTiers, "Tier"));
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
        NBTTagList recoveryList = new NBTTagList();
        for (ItemStack stack : recoveryStorage) {
            if (stack != null && stack.stackSize > 0) {
                NBTTagCompound entry = new NBTTagCompound();
                stack.writeToNBT(entry);
                recoveryList.appendTag(entry);
            }
        }
        nbt.setTag("RecoveryStorage", recoveryList);
        NBTTagList claimList = new NBTTagList();
        for (Map.Entry<String, Integer> entry : claimAmounts.entrySet()) {
            ItemStack sample = claimSamples.get(entry.getKey());
            if (sample == null || entry.getValue().intValue() <= 0) {
                continue;
            }
            NBTTagCompound item = new NBTTagCompound();
            item.setString("ID", entry.getKey());
            item.setInteger("Amount", entry.getValue().intValue());
            NBTTagCompound stack = new NBTTagCompound();
            sample.writeToNBT(stack);
            item.setTag("Stack", stack);
            claimList.appendTag(item);
        }
        nbt.setTag("ClaimGoods", claimList);
        return nbt;
    }

    private void mergeStoredStack(ItemStack incoming) {
        if (incoming == null || incoming.stackSize <= 0) {
            return;
        }
        ItemStack remaining = incoming.copy();
        for (int i = 0; i < storage.length && remaining.stackSize > 0; i++) {
            ItemStack existing = storage[i];
            if (existing != null && ItemStack.areItemStacksEqual(existing, remaining)) {
                int limit = Math.min(existing.getMaxStackSize(), 64);
                int moved = Math.min(limit - existing.stackSize, remaining.stackSize);
                if (moved > 0) {
                    existing.stackSize += moved;
                    remaining.stackSize -= moved;
                }
            }
        }
        for (int i = 0; i < storage.length && remaining.stackSize > 0; i++) {
            if (storage[i] == null) {
                int moved = Math.min(Math.min(remaining.getMaxStackSize(), 64), remaining.stackSize);
                ItemStack placed = remaining.copy();
                placed.stackSize = moved;
                storage[i] = placed;
                remaining.stackSize -= moved;
            }
        }
        if (remaining.stackSize > 0) {
            addRecoveryStack(remaining);
        }
    }

    private void readAssignments(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            setAssignment(entry.getString("ID"), entry.getString("Value"));
        }
    }

    private void readDelivered(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            setDelivered(entry.getString("ID"), Math.max(0, entry.getInteger("Amount")));
        }
    }

    private void readCompleted(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            setCompletedTier(entry.getString("ID"), entry.getInteger("Tier"));
        }
    }

    private void readUnlocked(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            setUnlockedTier(entry.getString("ID"), entry.getInteger("Tier"));
        }
    }

    private void readStorage(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getByte("Slot") & 255;
            ItemStack stack = ItemStack.loadItemStackFromNBT(entry);
            if (slot >= 0 && slot < storage.length && stack != null) {
                storage[slot] = stack;
            } else if (stack != null) {
                addRecoveryStack(stack);
            }
        }
    }

    private void readRecovery(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); i++) {
            addRecoveryStack(ItemStack.loadItemStackFromNBT(list.getCompoundTagAt(i)));
        }
    }

    private void readClaims(NBTTagList list) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            ItemStack sample = ItemStack.loadItemStackFromNBT(entry.getCompoundTag("Stack"));
            int amount = Math.max(0, entry.getInteger("Amount"));
            if (sample != null && amount > 0) {
                addClaimGoods(entry.getString("ID"), sample, amount);
            }
        }
    }

    private NBTTagList writeStringMap(Map<String, String> values) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey().length() == 0 || entry.getValue() == null || entry.getValue().length() == 0) {
                continue;
            }
            NBTTagCompound item = new NBTTagCompound();
            item.setString("ID", entry.getKey());
            item.setString("Value", entry.getValue());
            list.appendTag(item);
        }
        return list;
    }

    private NBTTagList writeIntMap(Map<String, Integer> values, String valueKey) {
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            if (entry.getKey().length() == 0 || entry.getValue() == null || entry.getValue().intValue() < 0) {
                continue;
            }
            NBTTagCompound item = new NBTTagCompound();
            item.setString("ID", entry.getKey());
            item.setInteger(valueKey, entry.getValue().intValue());
            list.appendTag(item);
        }
        return list;
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase();
    }
}
