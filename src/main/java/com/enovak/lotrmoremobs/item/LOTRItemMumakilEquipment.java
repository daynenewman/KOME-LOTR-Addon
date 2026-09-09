package com.enovak.lotrmoremobs.item;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Shared right-click helpers for Mumakil equipment items.
 *
 * Forge/LOTR 1.7.10 mount equipment method names vary a little between vanilla
 * horses and LOTR mounts, so these helpers use reflection and the existing
 * mount inventory when possible instead of hard-requiring one exact setter name.
 */
public abstract class LOTRItemMumakilEquipment extends Item {
    protected static final int SADDLE_SLOT = 0;
    protected static final int WAR_EQUIPMENT_SLOT = 1;

    private static final String[] INVENTORY_FIELDS = new String[] {
            "horseChest",
            "mountInventory",
            "horseInventory",
            "inventory"
    };

    protected LOTRItemMumakilEquipment() {
    }

    protected boolean isMumakil(EntityLivingBase target) {
        return target instanceof LOTREntityMumakil;
    }

    protected LOTREntityMumakil asMumakil(EntityLivingBase target) {
        return (LOTREntityMumakil) target;
    }

    protected void consumeOne(ItemStack stack, EntityPlayer player) {
        if (player.capabilities.isCreativeMode || stack == null) {
            return;
        }

        --stack.stackSize;
        if (stack.stackSize <= 0) {
            player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
        }
    }

    protected void setMountSaddled(LOTREntityMumakil mumakil, boolean saddled) {
        if (!this.invokeBooleanSetter(mumakil, "setMountSaddled", saddled)) {
            this.invokeBooleanSetter(mumakil, "setHorseSaddled", saddled);
        }
    }

    protected void equipSaddleSlot(LOTREntityMumakil mumakil, ItemStack saddleStack) {
        this.setInventoryStack(mumakil, SADDLE_SLOT, saddleStack);
    }

    protected boolean hasWarEquipmentStack(LOTREntityMumakil mumakil) {
        ItemStack stack = this.getInventoryStack(mumakil, WAR_EQUIPMENT_SLOT);
        return stack != null && stack.getItem() != null;
    }

    protected void equipWarEquipmentSlot(LOTREntityMumakil mumakil, ItemStack equipmentStack) {
        this.setInventoryStack(mumakil, WAR_EQUIPMENT_SLOT, equipmentStack);
        this.invokeItemStackSetter(mumakil, equipmentStack,
                "setMountArmor",
                "setMountArmorItem",
                "setMountArmorItemStack",
                "setHorseArmorStack",
                "setHorseArmor",
                "setArmorItemStack");
    }

    private ItemStack getInventoryStack(LOTREntityMumakil mumakil, int slot) {
        IInventory inventory = this.findMountInventory(mumakil);
        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) {
            return null;
        }
        return inventory.getStackInSlot(slot);
    }

    private void setInventoryStack(LOTREntityMumakil mumakil, int slot, ItemStack stack) {
        IInventory inventory = this.findMountInventory(mumakil);
        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) {
            return;
        }

        inventory.setInventorySlotContents(slot, stack);
        inventory.markDirty();
        this.invokeInventoryChanged(mumakil, inventory);
    }

    private IInventory findMountInventory(LOTREntityMumakil mumakil) {
        for (int i = 0; i < INVENTORY_FIELDS.length; ++i) {
            Field field = this.findField(mumakil.getClass(), INVENTORY_FIELDS[i]);
            if (field != null) {
                try {
                    Object value = field.get(mumakil);
                    if (value instanceof IInventory) {
                        return (IInventory) value;
                    }
                } catch (Exception e) {
                }
            }
        }
        return null;
    }

    private boolean invokeBooleanSetter(Object target, String methodName, boolean value) {
        Method method = this.findMethod(target.getClass(), methodName, Boolean.TYPE);
        if (method == null) {
            return false;
        }

        try {
            method.invoke(target, Boolean.valueOf(value));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean invokeItemStackSetter(Object target, ItemStack stack, String... methodNames) {
        for (int i = 0; i < methodNames.length; ++i) {
            Method method = this.findMethod(target.getClass(), methodNames[i], ItemStack.class);
            if (method != null) {
                try {
                    method.invoke(target, stack);
                    return true;
                } catch (Exception e) {
                }
            }
        }
        return false;
    }

    private boolean invokeInventoryChanged(Object target, IInventory inventory) {
        Method method = this.findMethod(target.getClass(), "onInventoryChanged", IInventory.class);
        if (method == null) {
            return false;
        }

        try {
            method.invoke(target, inventory);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Method findMethod(Class type, String name, Class parameterType) {
        Class current = type;
        while (current != null && current != Object.class) {
            try {
                Method method = current.getDeclaredMethod(name, new Class[] { parameterType });
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
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