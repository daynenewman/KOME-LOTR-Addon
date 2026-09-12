package com.enovak.lotrmoremobs.client;

import com.enovak.lotrmoremobs.handler.MumakilShankHeldEffectHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraftforge.client.event.FOVUpdateEvent;

/**
 * MUMAKIL_SHANK_SYSTEM_V1_1
 *
 * Removes only the visual FOV contribution of the hidden shank slowdown.
 * Sprinting, flying, bows, and unrelated speed modifiers retain their
 * normal FOV behavior.
 */
@SideOnly(Side.CLIENT)
public final class MumakilShankFovHandler {
    @SubscribeEvent
    public void onFovUpdate(FOVUpdateEvent event) {
        /*
         * MUMAKIL_ADULT_BREEDING_AND_FOV_TRANSITION_FIX_V1_1
         *
         * Key the visual correction to the actual speed modifier, not the
         * currently selected item. During a hotbar switch, selection changes
         * before the next player tick removes the modifier; covering that
         * brief interval prevents the one-frame FOV pulse.
         */
IAttributeInstance movementSpeed = event.entity.getEntityAttribute(
                SharedMonsterAttributes.movementSpeed
        );

        if (movementSpeed == null
                || movementSpeed.getModifier(
                        MumakilShankHeldEffectHandler.SLOWDOWN_MODIFIER_ID
                ) == null) {
            return;
        }

        float walkSpeed = event.entity.capabilities.getWalkSpeed();

        if (walkSpeed == 0.0F) {
            return;
        }

        double slowedAttributeValue = movementSpeed.getAttributeValue();
        double normalAttributeValue =
                slowedAttributeValue
                        / MumakilShankHeldEffectHandler
                                .MOVEMENT_SPEED_MULTIPLIER;

        double slowedFovFactor =
                (slowedAttributeValue / (double)walkSpeed + 1.0D) / 2.0D;
        double normalFovFactor =
                (normalAttributeValue / (double)walkSpeed + 1.0D) / 2.0D;

        if (slowedFovFactor == 0.0D
                || Double.isNaN(slowedFovFactor)
                || Double.isInfinite(slowedFovFactor)
                || Double.isNaN(normalFovFactor)
                || Double.isInfinite(normalFovFactor)) {
            return;
        }

        event.newfov = (float)(
                (double)event.newfov
                        * normalFovFactor
                        / slowedFovFactor
        );
    }
}