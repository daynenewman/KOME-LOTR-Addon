package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * Keeps Mumakil war equipment restricted to the custom Mumakil Howdah item.
 *
 * The inherited LOTR/horse inventory is intentionally reused so the existing renderer can keep reading
 * the saddle and armor slots normally. Any non-howdah item that reaches the armor slot is ejected.
 */
public class MumakilEquipmentEventHandler {
    // BABY_HOWDAH_RIGHT_CLICK_BLOCK_V1
    private static final int WAR_EQUIPMENT_SLOT = 1;

    @SubscribeEvent
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        if (!(event.entityLiving instanceof LOTREntityMumakil) || event.entityLiving.worldObj.isRemote) {
            return;
        }

        LOTREntityMumakil mumakil = (LOTREntityMumakil) event.entityLiving;
        mumakil.enforceTamedAdultHowdahRequiresSaddle(null);
        IInventory inventory = mumakil.getMumakilMountInventory();
        if (inventory == null || WAR_EQUIPMENT_SLOT >= inventory.getSizeInventory()) {
            return;
        }

        ItemStack stack = inventory.getStackInSlot(WAR_EQUIPMENT_SLOT);
        if (stack == null || stack.getItem() == null) {
            return;
        }

        boolean illegalBabyHowdah = stack.getItem() == Main.mumakilHowdah
                && (mumakil.isChild() || mumakil.isBabyMumakil());

        if (stack.getItem() == Main.mumakilHowdah && !illegalBabyHowdah) {
            return;
        }

        ItemStack rejected = stack.copy();
        inventory.setInventorySlotContents(WAR_EQUIPMENT_SLOT, null);
        inventory.markDirty();

        Entity entity = mumakil;
        if (rejected.stackSize > 0) {
            entity.entityDropItem(rejected, entity.height * 0.5F);
        }
    }

}
