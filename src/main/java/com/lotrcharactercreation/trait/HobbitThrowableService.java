package com.lotrcharactercreation.trait;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

import lotr.common.LOTRMod;
import lotr.common.entity.projectile.LOTREntityConker;
import lotr.common.entity.projectile.LOTREntityPebble;
import lotr.common.item.LOTRItemConker;
import lotr.common.item.LOTRItemPebble;
import lotr.common.item.LOTRItemSling;

public final class HobbitThrowableService {

    public static final int FULL_CHARGE_TICKS = 20;
    public static final int MINIMUM_CHARGE_TICKS = 5;
    public static final int USE_DURATION_TICKS = 72000;

    private static final float LOW_CHARGE_VELOCITY_MULTIPLIER = 0.50F;
    private static final float FULL_CHARGE_VELOCITY_MULTIPLIER = 2.00F;
    private static final String CHARGED_PROJECTILE_TAG = "lotrcharactercreationHobbitChargedProjectile";
    private static final String CHARGED_PROJECTILE_DAMAGE_TAG = "lotrcharactercreationHobbitChargedDamage";
    private static final Map<UUID, ChargedUseContext> ACTIVE_USES = new HashMap<UUID, ChargedUseContext>();

    private HobbitThrowableService() {}

    public static boolean isSupportedThrowable(ItemStack itemStack) {
        return findType(itemStack) != null;
    }

    public static boolean canBeginClientUse(EntityPlayer player, ItemStack itemStack) {
        ThrowableType type = findType(itemStack);
        return type != null && (type != ThrowableType.SLING_PEBBLE || hasSlingAmmunition(player));
    }

    public static boolean beginServerUse(EntityPlayerMP player) {
        if (!RaceTraitService.hasHobbitAdvancedTrait(player)) {
            return false;
        }

        ItemStack heldItem = player.getCurrentEquippedItem();
        ThrowableType type = findType(heldItem);
        if (type == null) {
            return false;
        }

        ACTIVE_USES.remove(player.getUniqueID());
        if (type == ThrowableType.SLING_PEBBLE && !hasSlingAmmunition(player)) {
            return true;
        }

        player.setItemInUse(heldItem, USE_DURATION_TICKS);
        if (player.isUsingItem()) {
            ACTIVE_USES.put(
                player.getUniqueID(),
                new ChargedUseContext(type, heldItem, player.inventory.currentItem, player.ticksExisted));
        }
        return true;
    }

    public static void releaseServerUse(EntityPlayerMP player, ItemStack usedItem) {
        ChargedUseContext context = ACTIVE_USES.remove(player.getUniqueID());
        if (context == null || !RaceTraitService.hasHobbitAdvancedTrait(player)
            || !context.matches(usedItem, player.inventory.currentItem)) {
            return;
        }

        int heldTicks = Math.max(0, player.ticksExisted - context.startTick);
        if (heldTicks < MINIMUM_CHARGE_TICKS
            || context.type == ThrowableType.SLING_PEBBLE && !hasSlingAmmunition(player)) {
            return;
        }

        float charge = MathHelper.clamp_float(heldTicks / (float) FULL_CHARGE_TICKS, 0.0F, 1.0F);
        fireChargedProjectile(player, usedItem, context.type, charge);
    }

    public static void maintainServerUse(EntityPlayerMP player) {
        ChargedUseContext context = ACTIVE_USES.get(player.getUniqueID());
        if (context == null) {
            return;
        }

        ItemStack heldItem = player.getCurrentEquippedItem();
        if (!context.matches(heldItem, player.inventory.currentItem)) {
            ACTIVE_USES.remove(player.getUniqueID());
            return;
        }

        // The 1.7.10 server copies zero-use-duration item stacks after processing C08. Reattach the
        // already-validated charged use to that equivalent replacement stack on the following tick.
        if (!player.isUsingItem()) {
            int elapsedTicks = Math.max(0, player.ticksExisted - context.startTick);
            player.setItemInUse(heldItem, Math.max(1, USE_DURATION_TICKS - elapsedTicks));
            if (!player.isUsingItem()) {
                ACTIVE_USES.remove(player.getUniqueID());
            }
        }
    }

    public static void handleItemUseStarted(EntityPlayerMP player, ItemStack itemStack) {
        ChargedUseContext context = ACTIVE_USES.get(player.getUniqueID());
        if (context != null && !context.matches(itemStack, player.inventory.currentItem)) {
            ACTIVE_USES.remove(player.getUniqueID());
        }
    }

    public static void clearServerUse(EntityPlayerMP player) {
        ACTIVE_USES.remove(player.getUniqueID());
    }

    public static void applyChargedProjectileDamage(LivingHurtEvent event) {
        Entity immediateSource = event.source.getSourceOfDamage();
        if (!(immediateSource instanceof LOTREntityPebble) && !(immediateSource instanceof LOTREntityConker)) {
            return;
        }
        if (immediateSource.getEntityData()
            .getBoolean(CHARGED_PROJECTILE_TAG)) {
            event.ammount = immediateSource.getEntityData()
                .getFloat(CHARGED_PROJECTILE_DAMAGE_TAG);
        }
    }

    public static float getFullChargeVelocityMultiplier() {
        return FULL_CHARGE_VELOCITY_MULTIPLIER;
    }

    private static void fireChargedProjectile(EntityPlayerMP player, ItemStack usedItem, ThrowableType type,
        float charge) {
        EntityThrowable projectile;
        if (type == ThrowableType.CONKER) {
            projectile = new LOTREntityConker(player.worldObj, player);
        } else {
            LOTREntityPebble pebble = new LOTREntityPebble(player.worldObj, player);
            projectile = type == ThrowableType.SLING_PEBBLE ? pebble.setSling() : pebble;
        }

        float velocityMultiplier = LOW_CHARGE_VELOCITY_MULTIPLIER
            + (getFullChargeVelocityMultiplier() - LOW_CHARGE_VELOCITY_MULTIPLIER) * charge;
        projectile.motionX *= velocityMultiplier;
        projectile.motionY *= velocityMultiplier;
        projectile.motionZ *= velocityMultiplier;

        float damageMultiplier = 0.5F + 0.5F * charge;
        projectile.getEntityData()
            .setBoolean(CHARGED_PROJECTILE_TAG, true);
        projectile.getEntityData()
            .setFloat(CHARGED_PROJECTILE_DAMAGE_TAG, type.fullChargeDamage * damageMultiplier);

        if (type == ThrowableType.SLING_PEBBLE) {
            consumeSlingUse(player, usedItem);
            playThrowSound(player);
            player.worldObj.spawnEntityInWorld(projectile);
        } else {
            player.worldObj.spawnEntityInWorld(projectile);
            playThrowSound(player);
            consumeThrownItem(player, usedItem);
        }
    }

    private static void consumeSlingUse(EntityPlayerMP player, ItemStack sling) {
        sling.damageItem(1, player);
        if (!player.capabilities.isCreativeMode) {
            player.inventory.consumeInventoryItem(LOTRMod.pebble);
        }
        if (sling.stackSize <= 0) {
            player.destroyCurrentEquippedItem();
        }
    }

    private static void consumeThrownItem(EntityPlayerMP player, ItemStack itemStack) {
        if (!player.capabilities.isCreativeMode) {
            --itemStack.stackSize;
            if (itemStack.stackSize <= 0) {
                player.destroyCurrentEquippedItem();
            }
        }
    }

    private static void playThrowSound(EntityPlayerMP player) {
        player.worldObj.playSoundAtEntity(
            player,
            "random.bow",
            0.5F,
            0.4F / (player.getRNG()
                .nextFloat() * 0.4F + 0.8F));
    }

    private static boolean hasSlingAmmunition(EntityPlayer player) {
        return player.capabilities.isCreativeMode || player.inventory.hasItem(LOTRMod.pebble);
    }

    private static ThrowableType findType(ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }

        Item item = itemStack.getItem();
        if (item instanceof LOTRItemPebble) {
            return ThrowableType.HAND_PEBBLE;
        }
        if (item instanceof LOTRItemSling) {
            return ThrowableType.SLING_PEBBLE;
        }
        if (item instanceof LOTRItemConker) {
            return ThrowableType.CONKER;
        }
        return null;
    }

    private enum ThrowableType {

        HAND_PEBBLE(5.0F),
        SLING_PEBBLE(10.0F),
        CONKER(5.0F);

        private final float fullChargeDamage;

        ThrowableType(float fullChargeDamage) {
            this.fullChargeDamage = fullChargeDamage;
        }
    }

    private static final class ChargedUseContext {

        private final ThrowableType type;
        private final Item item;
        private final int itemDamage;
        private final int inventorySlot;
        private final int startTick;

        private ChargedUseContext(ThrowableType type, ItemStack itemStack, int inventorySlot, int startTick) {
            this.type = type;
            item = itemStack.getItem();
            itemDamage = itemStack.getItemDamage();
            this.inventorySlot = inventorySlot;
            this.startTick = startTick;
        }

        private boolean matches(ItemStack itemStack, int currentSlot) {
            return itemStack != null && currentSlot == inventorySlot
                && itemStack.getItem() == item
                && itemStack.getItemDamage() == itemDamage;
        }
    }
}
