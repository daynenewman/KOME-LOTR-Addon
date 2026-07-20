package kome.common.data;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/**
 * Read-only schema migration shape for removed trade posts.
 *
 * This is deliberately not a live world record: it has no status, approval, product,
 * production, insertion, or removal behavior. Its sole purpose is to recover legacy
 * input/output stacks into the operating faction's alliance ledger.
 */
final class KOMELegacyTradePostRecord {
    private static final int LEGACY_INPUT_SLOTS = 9;
    private static final int LEGACY_OUTPUT_SLOTS = 9;

    String id = "";
    String operatingFaction = "";
    String hostFaction = "";
    boolean legacyFarmerReservation;
    final ItemStack[] inputs = new ItemStack[LEGACY_INPUT_SLOTS];
    final ItemStack[] outputs = new ItemStack[LEGACY_OUTPUT_SLOTS];

    void readFromNBT(NBTTagCompound nbt) {
        id = nbt.getString("Id");
        operatingFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("OperatingFaction"));
        hostFaction = KOMEAlliance.normalizeFactionKey(nbt.getString("HostFaction"));
        legacyFarmerReservation = nbt.getBoolean("FarmerReserved");
        readStacks(nbt.getTagList("Inputs", 10), inputs);
        readStacks(nbt.getTagList("Outputs", 10), outputs);
    }

    private static void readStacks(NBTTagList list, ItemStack[] destination) {
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            int slot = entry.getInteger("Slot");
            if (slot >= 0 && slot < destination.length) {
                destination[slot] = ItemStack.loadItemStackFromNBT(entry);
            }
        }
    }
}
