package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import com.enovak.lotrmoremobs.entity.animal.MumakilFormationOrigin;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.Field;
import java.util.Iterator;
import lotr.common.entity.ai.LOTREntityAIHorseFollowHiringPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;

public class MumakilHiredMountEventHandler {
    private static final int SADDLE_SLOT = 0;
    private static final int HOWDAH_SLOT = 1;

    private static final float MUMAKIL_MIN_FOLLOW_DIST = 30.0F;
    private static final float MUMAKIL_MAX_NEAR_DIST = 18.0F;

    @SubscribeEvent
    public void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (event == null || event.world == null || event.world.isRemote) {
            return;
        }

        if (!(event.entity instanceof LOTREntityMumakil)) {
            return;
        }

        LOTREntityMumakil mumakil = (LOTREntityMumakil)event.entity;

        if (!mumakil.getBelongsToNPC()) {
            return;
        }

        /*
         * Explicit autonomous formations are fully equipped transactionally
         * before their mount enters the world. Do not apply player-hired
         * follow tuning to them.
         */
        MumakilFormationOrigin origin =
                mumakil.getFormationOrigin();
        if (origin != MumakilFormationOrigin.NONE
                && origin != MumakilFormationOrigin.PLAYER_HIRED) {
            return;
        }

        this.equipHiredMumakil(mumakil);
    }

    private void equipHiredMumakil(LOTREntityMumakil mumakil) {
        mumakil.setHiredWarMumakil(true);
        mumakil.saddleMountForWorldGen();
        this.setInventoryStack(mumakil, SADDLE_SLOT, new ItemStack(Items.saddle));
        this.setInventoryStack(mumakil, HOWDAH_SLOT, new ItemStack(Main.mumakilHowdah));
        mumakil.setMumakilHowdahEquipped(true);

        this.tuneHiredMumakilFollowDistance(mumakil);

        System.out.println("[LOTRMoreMobs] Equipped hired Mumakil with saddle and howdah.");
    }

    private void tuneHiredMumakilFollowDistance(LOTREntityMumakil mumakil) {
        if (mumakil.tasks == null || mumakil.tasks.taskEntries == null) {
            return;
        }

        boolean tuned = false;
        Iterator iterator = mumakil.tasks.taskEntries.iterator();

        while (iterator.hasNext()) {
            Object taskEntry = iterator.next();
            Object aiTask = this.getFieldValue(taskEntry, "action");

            if (aiTask == null) {
                continue;
            }

            if (aiTask instanceof LOTREntityAIHorseFollowHiringPlayer) {
                if (this.setFloatField(aiTask, "minFollowDist", MUMAKIL_MIN_FOLLOW_DIST)) {
                    tuned = true;
                }

                if (this.setFloatField(aiTask, "maxNearDist", MUMAKIL_MAX_NEAR_DIST)) {
                    tuned = true;
                }
            }
        }

        if (tuned) {
            System.out.println("[LOTRMoreMobs] Tuned hired Mumakil follow distance to "
                    + MUMAKIL_MIN_FOLLOW_DIST
                    + "/"
                    + MUMAKIL_MAX_NEAR_DIST
                    + " blocks.");
        }
    }

    private Object getFieldValue(Object object, String name) {
        try {
            Field field = this.findField(object.getClass(), name);
            return field == null ? null : field.get(object);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean setFloatField(Object object, String name, float value) {
        try {
            Field field = this.findField(object.getClass(), name);

            if (field != null) {
                field.setFloat(object, value);
                return true;
            }
        } catch (Exception e) {
        }

        return false;
    }

    private boolean setInventoryStack(LOTREntityMumakil mumakil, int slot, ItemStack stack) {
        IInventory inventory = mumakil.getMumakilMountInventory();

        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) {
            System.out.println("[LOTRMoreMobs] Could not set hired Mumakil inventory slot " + slot);
            return false;
        }

        inventory.setInventorySlotContents(slot, stack);
        inventory.markDirty();
        return true;
    }

    private Field findField(Class type, String name) {
        Class current = type;

        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }

        return null;
    }
}
