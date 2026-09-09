package com.enovak.lotrmoremobs.pickupfilter;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent per-player storage for the pickup filter.
 *
 * The data lives under EntityPlayer.PERSISTED_NBT_TAG so it survives normal
 * player saves and is copied when the player respawns.
 */
public final class PlayerPickupFilterData {
    private static final String FILTER_TAG = "lotrmoremobsPickupFilter";
    private static final String EXCLUDED_ITEMS_TAG = "excludedItems";
    private static final int TAG_COMPOUND = 10;

    /**
     * Hard release-safety bound shared by persistence and network sync.
     * Normal GUI use is expected to stay far below this value.
     */
    public static final int MAX_EXCLUDED_ITEMS = 512;

    /*
     * Hot-path lookup contains only the semantics the filter actually uses:
     * Item identity + metadata. It is keyed by player UUID so no world/player
     * object is retained by the static cache.
     */
    private static final Map<UUID, Set<ItemKey>> EXCLUDED_CACHE =
            new HashMap<UUID, Set<ItemKey>>();

    private PlayerPickupFilterData() {
    }

    public static List<ItemStack> getExcludedItems(EntityPlayer player) {
        List<ItemStack> excludedItems = new ArrayList<ItemStack>();
        NBTTagCompound filterData = getFilterData(player, false);

        if (filterData == null) {
            return excludedItems;
        }

        NBTTagList itemList = filterData.getTagList(
                EXCLUDED_ITEMS_TAG,
                TAG_COMPOUND
        );

        Set<ItemKey> seen = new HashSet<ItemKey>();

        int entriesToScan = Math.min(
                itemList.tagCount(),
                MAX_EXCLUDED_ITEMS
        );
        for (int i = 0; i < entriesToScan; ++i) {
            try {
                ItemStack stack = ItemStack.loadItemStackFromNBT(
                        itemList.getCompoundTagAt(i)
                );
                ItemStack sanitized = sanitizeStack(stack);

                if (sanitized == null) {
                    continue;
                }

                ItemKey key = ItemKey.from(sanitized);
                if (key != null && seen.add(key)) {
                    excludedItems.add(sanitized);
                }
            } catch (RuntimeException ignored) {
                // One malformed legacy entry must not invalidate the filter.
            }
        }

        return excludedItems;
    }

    public static boolean isExcluded(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null) {
            return false;
        }

        ItemKey key = ItemKey.from(stack);
        return key != null && getOrBuildCache(player).contains(key);
    }

    public static boolean addExcludedItem(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null) {
            return false;
        }

        ItemStack sanitized = sanitizeStack(stack);
        ItemKey key = ItemKey.from(sanitized);
        if (sanitized == null || key == null) {
            return false;
        }

        Set<ItemKey> cached = getOrBuildCache(player);
        if (cached.contains(key) || cached.size() >= MAX_EXCLUDED_ITEMS) {
            return false;
        }

        List<ItemStack> excludedItems = getExcludedItems(player);
        if (excludedItems.size() >= MAX_EXCLUDED_ITEMS) {
            return false;
        }

        excludedItems.add(sanitized);
        writeExcludedItems(player, excludedItems);
        return true;
    }

    public static boolean removeExcludedItem(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null) {
            return false;
        }

        ItemKey target = ItemKey.from(stack);
        if (target == null || !getOrBuildCache(player).contains(target)) {
            return false;
        }

        List<ItemStack> excludedItems = getExcludedItems(player);
        boolean removed = false;
        Iterator<ItemStack> iterator = excludedItems.iterator();

        while (iterator.hasNext()) {
            ItemStack existing = iterator.next();
            if (sameItem(existing, stack)) {
                iterator.remove();
                removed = true;
            }
        }

        if (removed) {
            writeExcludedItems(player, excludedItems);
        }

        return removed;
    }

    public static void clearExcludedItems(EntityPlayer player) {
        if (player != null) {
            writeExcludedItems(player, new ArrayList<ItemStack>());
        }
    }

    /**
     * Normalizes one filter identity. Matching intentionally ignores stack NBT,
     * so new/rewritten entries do not persist arbitrary item tag data.
     */
    public static ItemStack sanitizeStack(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }

        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        copy.setTagCompound(null);
        return copy;
    }

    public static synchronized void clearCache(EntityPlayer player) {
        if (player != null) {
            EXCLUDED_CACHE.remove(player.getUniqueID());
        }
    }

    public static synchronized void clearCache(UUID playerUuid) {
        if (playerUuid != null) {
            EXCLUDED_CACHE.remove(playerUuid);
        }
    }

    public static synchronized void clearAllCaches() {
        EXCLUDED_CACHE.clear();
    }

    private static boolean sameItem(ItemStack first, ItemStack second) {
        return first != null && second != null && first.isItemEqual(second);
    }

    private static void writeExcludedItems(
            EntityPlayer player,
            List<ItemStack> excludedItems
    ) {
        NBTTagCompound filterData = getFilterData(player, true);
        NBTTagList itemList = new NBTTagList();
        List<ItemStack> normalized = new ArrayList<ItemStack>();
        Set<ItemKey> seen = new HashSet<ItemKey>();

        if (excludedItems != null) {
            for (ItemStack stack : excludedItems) {
                if (normalized.size() >= MAX_EXCLUDED_ITEMS) {
                    break;
                }

                ItemStack storedStack = sanitizeStack(stack);
                ItemKey key = ItemKey.from(storedStack);
                if (storedStack == null || key == null || !seen.add(key)) {
                    continue;
                }

                NBTTagCompound stackTag = new NBTTagCompound();
                storedStack.writeToNBT(stackTag);
                itemList.appendTag(stackTag);
                normalized.add(storedStack);
            }
        }

        filterData.setTag(EXCLUDED_ITEMS_TAG, itemList);
        replaceCache(player, normalized);
    }

    private static synchronized Set<ItemKey> getOrBuildCache(
            EntityPlayer player
    ) {
        UUID playerUuid = player.getUniqueID();
        Set<ItemKey> cached = EXCLUDED_CACHE.get(playerUuid);
        if (cached != null) {
            return cached;
        }

        cached = new HashSet<ItemKey>();
        for (ItemStack stack : getExcludedItems(player)) {
            ItemKey key = ItemKey.from(stack);
            if (key != null) {
                cached.add(key);
            }
        }
        EXCLUDED_CACHE.put(playerUuid, cached);
        return cached;
    }

    private static synchronized void replaceCache(
            EntityPlayer player,
            List<ItemStack> items
    ) {
        if (player == null) {
            return;
        }

        Set<ItemKey> cached = new HashSet<ItemKey>();
        if (items != null) {
            for (ItemStack stack : items) {
                ItemKey key = ItemKey.from(stack);
                if (key != null && cached.size() < MAX_EXCLUDED_ITEMS) {
                    cached.add(key);
                }
            }
        }
        EXCLUDED_CACHE.put(player.getUniqueID(), cached);
    }

    private static NBTTagCompound getFilterData(EntityPlayer player, boolean create) {
        if (player == null) {
            return null;
        }

        NBTTagCompound entityData = player.getEntityData();
        NBTTagCompound persistedData;

        if (entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, TAG_COMPOUND)) {
            persistedData = entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        } else {
            if (!create) {
                return null;
            }

            persistedData = new NBTTagCompound();
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persistedData);
        }

        if (persistedData.hasKey(FILTER_TAG, TAG_COMPOUND)) {
            return persistedData.getCompoundTag(FILTER_TAG);
        }

        if (!create) {
            return null;
        }

        NBTTagCompound filterData = new NBTTagCompound();
        persistedData.setTag(FILTER_TAG, filterData);
        return filterData;
    }

    private static final class ItemKey {
        private final Item item;
        private final int metadata;

        private ItemKey(Item item, int metadata) {
            this.item = item;
            this.metadata = metadata;
        }

        private static ItemKey from(ItemStack stack) {
            return stack == null || stack.getItem() == null
                    ? null
                    : new ItemKey(stack.getItem(), stack.getItemDamage());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ItemKey)) {
                return false;
            }
            ItemKey key = (ItemKey)other;
            return item == key.item && metadata == key.metadata;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(item) + metadata;
        }
    }
}
