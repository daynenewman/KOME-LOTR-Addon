package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.Main;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.EntityInteractEvent;

/**
 * Safe first-pass Mumakil hiring hook.
 *
 * Sneak-right-clicking a Near Harad Warlord with at least 3000 Near Harad
 * alignment spawns an owned Mumakil nearby with a vanilla saddle and the custom
 * howdah equipped. Normal right-clicks are left alone so the LOTR unit-trade GUI
 * remains untouched.
 */
public class MumakilHireEventHandler {
    private static final double REQUIRED_NEAR_HARAD_ALIGNMENT = 0.0D;
    private static final int HIRE_CLICK_COOLDOWN_TICKS = 20;
    private static final int SADDLE_SLOT = 0;
    private static final int WAR_EQUIPMENT_SLOT = 1;
    private static final String[] INVENTORY_FIELDS = new String[] {
            "horseChest",
            "mountInventory",
            "horseInventory",
            "inventory"
    };

    private final Map<String, Long> lastHireAttemptTicks = new HashMap<String, Long>();

    @SubscribeEvent
    public void onEntityInteract(EntityInteractEvent event) {
        if (event == null || event.entityPlayer == null || event.target == null) {
            return;
        }

        EntityPlayer player = event.entityPlayer;
        Entity target = event.target;
        World world = player.worldObj;

        if (world == null || world.isRemote) {
            return;
        }

        if (!this.isNearHaradWarlord(target)) {
            return;
        }

        // Keep normal Warlord right-click behavior / hire GUI available.
        if (!player.isSneaking()) {
            return;
        }

        if (!this.canProcessHireAttempt(player, world)) {
            event.setCanceled(true);
            return;
        }

        double alignment = this.getNearHaradAlignment(player);
        if (alignment < REQUIRED_NEAR_HARAD_ALIGNMENT) {
            this.sendMessage(player, "You need at least 3000 Near Harad alignment to hire a Mumak. Current: "
                    + this.formatAlignment(alignment));
            event.setCanceled(true);
            return;
        }

        LOTREntityMumakil mumakil = this.createHiredMumakil(world, player, target);
        if (!world.spawnEntityInWorld(mumakil)) {
            this.sendMessage(player, "The Mumak could not be hired here. Try standing in a more open area.");
            event.setCanceled(true);
            return;
        }

        this.sendMessage(player, "A saddled Mumak with a howdah has joined your warband.");
        System.out.println("[LOTRMoreMobs] Hired Mumakil spawned from Near Harad Warlord for player="
                + player.getCommandSenderName() + " alignment=" + alignment
                + " entityId=" + mumakil.getEntityId());
        event.setCanceled(true);
    }

    private boolean canProcessHireAttempt(EntityPlayer player, World world) {
        String key = player.getCommandSenderName();
        long now = world.getTotalWorldTime();
        Long last = this.lastHireAttemptTicks.get(key);
        if (last != null && now - last.longValue() < HIRE_CLICK_COOLDOWN_TICKS) {
            return false;
        }
        this.lastHireAttemptTicks.put(key, Long.valueOf(now));
        return true;
    }

    private boolean isNearHaradWarlord(Entity target) {
        String className = target.getClass().getName().toLowerCase();
        return className.indexOf("warlord") >= 0
                && (className.indexOf("nearharad") >= 0
                || className.indexOf("near_harad") >= 0
                || className.indexOf("haradrim") >= 0);
    }

    private LOTREntityMumakil createHiredMumakil(World world, EntityPlayer player, Entity warlord) {
        LOTREntityMumakil mumakil = new LOTREntityMumakil(world);

        float yaw = warlord.rotationYaw;
        float yawRadians = yaw * 3.1415927F / 180.0F;
        double forwardX = -MathHelper.sin(yawRadians);
        double forwardZ = MathHelper.cos(yawRadians);
        double spawnX = warlord.posX + forwardX * 6.0D;
        double spawnY = warlord.posY;
        double spawnZ = warlord.posZ + forwardZ * 6.0D;

        mumakil.setLocationAndAngles(spawnX, spawnY, spawnZ, yaw, 0.0F);
        this.makeMumakilOwnedBy(mumakil, player);
        this.equipHiredMumakil(mumakil);
        return mumakil;
    }

    private void makeMumakilOwnedBy(LOTREntityMumakil mumakil, EntityPlayer player) {
        if (this.invokeMethod(mumakil, "setTamedBy", new Class[] { EntityPlayer.class }, new Object[] { player })) {
            return;
        }

        this.invokeBooleanSetter(mumakil, true,
                "setHorseTamed",
                "setTamed",
                "func_110234_j");

        this.invokeStringSetter(mumakil, player.getCommandSenderName(),
                "setOwnerName",
                "func_110263_g");
    }

    private void equipHiredMumakil(LOTREntityMumakil mumakil) {
        this.invokeBooleanSetter(mumakil, true,
                "setMountSaddled",
                "setHorseSaddled",
                "func_110251_o");

        this.setInventoryStack(mumakil, SADDLE_SLOT, new ItemStack(Items.saddle));
        this.setInventoryStack(mumakil, WAR_EQUIPMENT_SLOT, new ItemStack(Main.mumakilHowdah));

        this.invokeItemStackSetter(mumakil, new ItemStack(Main.mumakilHowdah),
                "setMountArmor",
                "setMountArmorItem",
                "setMountArmorItemStack",
                "setHorseArmorStack",
                "setHorseArmor",
                "setArmorItemStack");

        this.invokeBooleanSetter(mumakil, true,
                "setMumakilHowdahEquipped");
    }

    private double getNearHaradAlignment(EntityPlayer player) {
        try {
            Class factionClass = Class.forName("lotr.common.fac.LOTRFaction");
            Object nearHaradFaction = this.getStaticFieldValue(factionClass,
                    "NEAR_HARAD",
                    "NEARHARAD",
                    "NearHarad");
            if (nearHaradFaction == null) {
                System.out.println("[LOTRMoreMobs] Could not find LOTRFaction.NEAR_HARAD for Mumakil hire alignment check.");
                return Double.NEGATIVE_INFINITY;
            }

            Class levelDataClass = Class.forName("lotr.common.LOTRLevelData");
            Method getData = this.findStaticCompatibleMethod(levelDataClass, "getData", new Object[] { player });
            if (getData == null) {
                System.out.println("[LOTRMoreMobs] Could not find LOTRLevelData.getData(EntityPlayer) for Mumakil hire alignment check.");
                return Double.NEGATIVE_INFINITY;
            }

            Object playerData = getData.invoke(null, new Object[] { player });
            Method getAlignment = this.findCompatibleMethod(playerData.getClass(), "getAlignment", new Object[] { nearHaradFaction });
            if (getAlignment == null) {
                System.out.println("[LOTRMoreMobs] Could not find LOTR playerData.getAlignment(LOTRFaction) for Mumakil hire alignment check.");
                return Double.NEGATIVE_INFINITY;
            }

            Object value = getAlignment.invoke(playerData, new Object[] { nearHaradFaction });
            if (value instanceof Number) {
                return ((Number)value).doubleValue();
            }
        } catch (Exception e) {
            System.out.println("[LOTRMoreMobs] Mumakil hire alignment lookup failed: " + e.getClass().getName() + ": " + e.getMessage());
        }

        return Double.NEGATIVE_INFINITY;
    }

    private String formatAlignment(double alignment) {
        if (alignment == Double.NEGATIVE_INFINITY) {
            return "unknown";
        }
        return Integer.toString((int)Math.floor(alignment));
    }

    private void sendMessage(EntityPlayer player, String message) {
        player.addChatMessage(new ChatComponentText(message));
    }

    private Object getStaticFieldValue(Class type, String... names) {
        for (int i = 0; i < names.length; ++i) {
            Field field = this.findField(type, names[i]);
            if (field != null) {
                try {
                    return field.get(null);
                } catch (Exception e) {
                }
            }
        }
        return null;
    }

    private boolean setInventoryStack(LOTREntityMumakil mumakil, int slot, ItemStack stack) {
        IInventory inventory = this.findMountInventory(mumakil);
        if (inventory == null || slot < 0 || slot >= inventory.getSizeInventory()) {
            return false;
        }

        inventory.setInventorySlotContents(slot, stack);
        inventory.markDirty();
        this.invokeMethod(mumakil, "onInventoryChanged", new Class[] { IInventory.class }, new Object[] { inventory });
        return true;
    }

    private IInventory findMountInventory(LOTREntityMumakil mumakil) {
        for (int i = 0; i < INVENTORY_FIELDS.length; ++i) {
            Field field = this.findField(mumakil.getClass(), INVENTORY_FIELDS[i]);
            if (field != null) {
                try {
                    Object value = field.get(mumakil);
                    if (value instanceof IInventory) {
                        return (IInventory)value;
                    }
                } catch (Exception e) {
                }
            }
        }
        return null;
    }

    private boolean invokeBooleanSetter(Object target, boolean value, String... methodNames) {
        for (int i = 0; i < methodNames.length; ++i) {
            if (this.invokeMethod(target, methodNames[i], new Class[] { Boolean.TYPE }, new Object[] { Boolean.valueOf(value) })) {
                return true;
            }
        }
        return false;
    }

    private boolean invokeStringSetter(Object target, String value, String... methodNames) {
        for (int i = 0; i < methodNames.length; ++i) {
            if (this.invokeMethod(target, methodNames[i], new Class[] { String.class }, new Object[] { value })) {
                return true;
            }
        }
        return false;
    }

    private boolean invokeItemStackSetter(Object target, ItemStack value, String... methodNames) {
        for (int i = 0; i < methodNames.length; ++i) {
            if (this.invokeMethod(target, methodNames[i], new Class[] { ItemStack.class }, new Object[] { value })) {
                return true;
            }
        }
        return false;
    }

    private boolean invokeMethod(Object target, String name, Class[] parameterTypes, Object[] args) {
        Method method = this.findMethod(target.getClass(), name, parameterTypes);
        if (method == null) {
            return false;
        }

        try {
            method.invoke(target, args);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Method findStaticCompatibleMethod(Class type, String name, Object[] args) {
        return this.findCompatibleMethod(type, name, args, true);
    }

    private Method findCompatibleMethod(Class type, String name, Object[] args) {
        return this.findCompatibleMethod(type, name, args, false);
    }

    private Method findCompatibleMethod(Class type, String name, Object[] args, boolean requireStatic) {
        Class current = type;
        while (current != null && current != Object.class) {
            Method[] methods = current.getDeclaredMethods();
            for (int i = 0; i < methods.length; ++i) {
                Method method = methods[i];
                if (!method.getName().equals(name)) {
                    continue;
                }
                if (requireStatic && !Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                Class[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length != args.length) {
                    continue;
                }
                if (this.areParametersCompatible(parameterTypes, args)) {
                    method.setAccessible(true);
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private boolean areParametersCompatible(Class[] parameterTypes, Object[] args) {
        for (int i = 0; i < parameterTypes.length; ++i) {
            if (args[i] != null && !parameterTypes[i].isAssignableFrom(args[i].getClass())) {
                return false;
            }
        }
        return true;
    }

    private Method findMethod(Class type, String name, Class[] parameterTypes) {
        Class current = type;
        while (current != null && current != Object.class) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
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
