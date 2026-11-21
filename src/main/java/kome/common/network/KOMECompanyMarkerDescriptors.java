package kome.common.network;

import java.util.List;
import kome.common.config.KOMEConfigRegistry;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEUnitMapMarker;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemArmor;
import net.minecraft.init.Items;
import lotr.common.item.LOTRItemMountArmor;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

/** Best-effort descriptor projection from existing saved NPC data at marker publication time. */
final class KOMECompanyMarkerDescriptors {
    private KOMECompanyMarkerDescriptors() { }

    static void projectWeapons(KOMEUnitMapMarker marker, List<KOMEHiredUnitRecord> members) {
        ItemRef bestMelee = null, bestRanged = null, bestBoots = null, bestMountArmor = null;
        for (KOMEHiredUnitRecord member : members) {
            if (member == null) continue;
            NBTTagCompound snapshot = member.isMoving() && member.movingEntityData != null
                ? member.movingEntityData : member.stationedEntityData;
            if (snapshot == null) continue;
            if (marker.movementEntitlement == KOMEUnitMapMarker.ENTITLEMENT_FOOT_OR_MIXED)
                bestBoots = smaller(bestBoots, boots(snapshot));
            else if (marker.movementEntitlement == KOMEUnitMapMarker.ENTITLEMENT_MOUNTED)
                bestMountArmor = smaller(bestMountArmor, mountArmor(snapshot));
            if (!snapshot.hasKey("NPCItemsInv", 9)) continue;
            NBTTagList inventory = snapshot.getTagList("NPCItemsInv", 10);
            if (inventory.tagCount() > 12) continue;
            ItemRef melee = null, ranged = null, mountedMelee = null;
            boolean badRanged = false;
            boolean[] seen = new boolean[7];
            boolean duplicate = false;
            for (int i = 0; i < inventory.tagCount(); i++) {
                NBTTagCompound entry = inventory.getCompoundTagAt(i);
                if (!entry.hasKey("Slot", 1)) continue;
                int slot = entry.getByte("Slot");
                if (slot != 1 && slot != 2 && slot != 6) continue;
                if (seen[slot]) { duplicate = true; break; }
                seen[slot] = true;
                ItemRef item = item(entry);
                if (slot == 1) melee = item;
                else if (slot == 2) { ranged = item; badRanged = item == null; }
                else mountedMelee = item;
            }
            if (duplicate) continue;
            // A ranged NPC's fallback melee slot does not establish a melee combatant.
            if (ranged != null) bestRanged = smaller(bestRanged, ranged);
            else if (!badRanged) bestMelee = smaller(bestMelee, melee != null ? melee : mountedMelee);
        }
        if (bestMelee != null) { marker.meleeItemId = bestMelee.id; marker.meleeItemMetadata = bestMelee.metadata; }
        if (bestRanged != null) { marker.rangedItemId = bestRanged.id; marker.rangedItemMetadata = bestRanged.metadata; }
        ItemRef movement = marker.movementEntitlement == KOMEUnitMapMarker.ENTITLEMENT_MOUNTED
            ? bestMountArmor : marker.movementEntitlement == KOMEUnitMapMarker.ENTITLEMENT_FOOT_OR_MIXED
                ? bestBoots : null;
        if (movement != null) { marker.movementItemId = movement.id; marker.movementItemMetadata = movement.metadata; }
    }

    static void projectMovement(KOMEUnitMapMarker marker, KOMEArmyCompany company) {
        if (company == null || company.totalPopulation <= 0 || !KOMEConfigRegistry.isReady()) return;
        marker.movementEntitlement = company.groundPopulation == 0 && company.mountedPopulation > 0
            ? KOMEUnitMapMarker.ENTITLEMENT_MOUNTED : KOMEUnitMapMarker.ENTITLEMENT_FOOT_OR_MIXED;
        if (company.movementAllowanceInitialized)
            marker.movementCredit = Math.min(company.movementAllowance, company.getTilesPerDay()) > 0
                ? KOMEUnitMapMarker.CREDIT_AVAILABLE : KOMEUnitMapMarker.CREDIT_EXHAUSTED;
    }

    private static ItemRef item(NBTTagCompound entry) {
        try {
            return item(stack(entry));
        } catch (RuntimeException malformed) { return null; }
    }

    private static ItemRef item(ItemStack stack) {
        if (stack == null || stack.getItem() == null || stack.stackSize <= 0) return null;
        Object key = Item.itemRegistry.getNameForObject(stack.getItem());
        String id = key == null ? "" : key.toString();
        int metadata = stack.getItemDamage();
        return KOMEUnitMapMarker.validItemId(id) && !id.isEmpty()
            && metadata >= 0 && metadata <= Short.MAX_VALUE ? new ItemRef(id, metadata) : null;
    }

    private static ItemStack stack(NBTTagCompound entry) {
        return entry == null ? null : ItemStack.loadItemStackFromNBT(entry);
    }

    /** EntityLiving equipment index 1 is boots; NPCItemsInv has unrelated slot numbers. */
    private static ItemRef boots(NBTTagCompound snapshot) {
        if (!snapshot.hasKey("Equipment", 9)) return null;
        NBTTagList equipment = snapshot.getTagList("Equipment", 10);
        if (equipment.tagCount() != 5) return null;
        try {
            NBTTagCompound entry = equipment.getCompoundTagAt(1);
            ItemStack stack = stack(entry);
            return stack != null && stack.getItem() instanceof ItemArmor
                && ((ItemArmor)stack.getItem()).armorType == 3 ? item(stack) : null;
        } catch (RuntimeException malformed) { return null; }
    }

    /** writeMountToNBT nests the ridden horse under Riding; LOTR adds its own armor key. */
    private static ItemRef mountArmor(NBTTagCompound snapshot) {
        if (!snapshot.hasKey("Riding", 10)) return null;
        NBTTagCompound mount = snapshot.getCompoundTag("Riding");
        try {
            if (mount.hasKey("LOTRMountArmorItem", 10)) {
                NBTTagCompound entry = mount.getCompoundTag("LOTRMountArmorItem");
                ItemStack stack = stack(entry);
                if (stack != null && stack.getItem() instanceof LOTRItemMountArmor) return item(stack);
            }
            if (mount.hasKey("ArmorItem", 10)) {
                NBTTagCompound entry = mount.getCompoundTag("ArmorItem");
                ItemStack stack = stack(entry);
                if (stack != null && (stack.getItem() == Items.iron_horse_armor
                    || stack.getItem() == Items.golden_horse_armor
                    || stack.getItem() == Items.diamond_horse_armor)) return item(stack);
            }
        } catch (RuntimeException malformed) { return null; }
        return null;
    }

    private static ItemRef smaller(ItemRef current, ItemRef candidate) {
        if (candidate == null) return current;
        if (current == null) return candidate;
        int names = candidate.id.compareTo(current.id);
        return names < 0 || names == 0 && candidate.metadata < current.metadata ? candidate : current;
    }

    private static final class ItemRef {
        final String id;
        final int metadata;
        ItemRef(String id, int metadata) { this.id = id; this.metadata = metadata; }
    }
}
