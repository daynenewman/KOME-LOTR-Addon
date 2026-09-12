package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.Main;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.UUID;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * MUMAKIL_SHANK_SYSTEM_V1_1
 *
 * Applies a hidden 15% movement-speed reduction while either Mumakil shank
 * is held. This deliberately does not use PotionEffect, so there is no
 * potion icon and no potion particles.
 */
public final class MumakilShankHeldEffectHandler {
    public static final UUID SLOWDOWN_MODIFIER_ID =
            UUID.fromString("7763f8b2-45de-4f5d-b12a-c6c799f387c1");

    public static final double MOVEMENT_SPEED_MULTIPLIER = 0.85D;

    private static final AttributeModifier HELD_SLOWDOWN =
            new AttributeModifier(
                    SLOWDOWN_MODIFIER_ID,
                    "Mumakil shank held slowdown",
                    MOVEMENT_SPEED_MULTIPLIER - 1.0D,
                    2
            ).setSaved(false);

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        EntityPlayer player = event.player;
        IAttributeInstance movementSpeed = player.getEntityAttribute(
                SharedMonsterAttributes.movementSpeed
        );

        if (movementSpeed == null) {
            return;
        }

        AttributeModifier current =
                movementSpeed.getModifier(SLOWDOWN_MODIFIER_ID);

        if (isHoldingMumakilShank(player)) {
            if (current == null) {
                movementSpeed.applyModifier(HELD_SLOWDOWN);
            }
        } else if (current != null) {
            movementSpeed.removeModifier(current);
        }
    }

    public static boolean isHoldingMumakilShank(EntityPlayer player) {
        if (player == null) {
            return false;
        }

        ItemStack heldStack = player.getCurrentEquippedItem();

        if (heldStack == null) {
            return false;
        }

        Item heldItem = heldStack.getItem();
        return heldItem == Main.mumakilShank
                || heldItem == Main.mumakilCookedShank;
    }

    public MumakilShankHeldEffectHandler() {
    }
}
