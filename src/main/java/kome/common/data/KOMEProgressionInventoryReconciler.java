package kome.common.data;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;

/** Removes assignment-owned inventory objects whose canonical assignment no longer exists. */
public final class KOMEProgressionInventoryReconciler {
    private KOMEProgressionInventoryReconciler() {
    }

    public static int reconcile(
            EntityPlayerMP player,
            KOMEPlayerProgression progression) {
        if(player==null||player.inventory==null||progression==null)return 0;

        int removed=reconcile(
            player.inventory.mainInventory,
            progression,
            player.getUniqueID());

        if(removed>0&&player.inventoryContainer!=null)
            player.inventoryContainer.detectAndSendChanges();

        return removed;
    }

    static int reconcile(
            ItemStack[] inventory,
            KOMEPlayerProgression progression,
            UUID owner) {
        if(inventory==null||progression==null||owner==null)return 0;

        int removed=0;

        for(int i=0;i<inventory.length;i++) {
            ItemStack stack=inventory[i];

            boolean staleRecovery=
                KOMESerfKnightRecoveryService.isRecoveryTagged(stack)
                &&!KOMESerfKnightRecoveryService.activeInventoryItem(
                    stack,progression,owner);

            boolean staleCourier=
                KOMECourierService.isCourierTagged(stack)
                &&!KOMECourierService.activeInventoryDispatch(
                    stack,progression,owner);

            if(staleRecovery||staleCourier) {
                inventory[i]=null;
                removed++;
            }
        }

        return removed;
    }
}