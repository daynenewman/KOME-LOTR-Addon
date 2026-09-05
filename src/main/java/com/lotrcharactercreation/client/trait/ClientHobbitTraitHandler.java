package com.lotrcharactercreation.client.trait;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

import com.lotrcharactercreation.trait.HobbitThrowableService;
import com.lotrcharactercreation.trait.RaceTraitService;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public final class ClientHobbitTraitHandler {

    private static final float HOBBIT_SNEAK_INPUT_MULTIPLIER = 0.75F / 0.30F;
    private static final float HOBBIT_CHARGING_INPUT_MULTIPLIER = 0.75F / 0.20F;
    private static final float HOBBIT_SNEAK_CHARGING_INPUT_MULTIPLIER = 0.75F / (0.30F * 0.20F);
    private static final ClientHobbitTraitHandler INSTANCE = new ClientHobbitTraitHandler();

    private ClientHobbitTraitHandler() {}

    public static ClientHobbitTraitHandler getInstance() {
        return INSTANCE;
    }

    @SubscribeEvent
    public void clientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }

        EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
        if (player != null && player.movementInput != null && !(player.movementInput instanceof HobbitMovementInput)) {
            player.movementInput = new HobbitMovementInput(player.movementInput);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void playerInteracted(PlayerInteractEvent event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityClientPlayerMP player = minecraft.thePlayer;
        if (event.isCanceled() || player == null
            || event.entityPlayer != player
            || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_AIR
            || event.useItem == Event.Result.DENY
            || !RaceTraitService.hasHobbitAdvancedTrait(player)
            || !HobbitThrowableService.isSupportedThrowable(player.getCurrentEquippedItem())) {
            return;
        }

        event.setCanceled(true);
        if (player.isUsingItem()
            || !HobbitThrowableService.canBeginClientUse(player, player.getCurrentEquippedItem())) {
            return;
        }

        player.setItemInUse(player.getCurrentEquippedItem(), HobbitThrowableService.USE_DURATION_TICKS);
        if (player.isUsingItem()) {
            player.sendQueue.addToSendQueue(new C09PacketHeldItemChange(player.inventory.currentItem));
            player.sendQueue.addToSendQueue(
                new C08PacketPlayerBlockPlacement(
                    -1,
                    -1,
                    -1,
                    255,
                    player.inventory.getCurrentItem(),
                    0.0F,
                    0.0F,
                    0.0F));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void fovUpdated(FOVUpdateEvent event) {
        if (!isChargingThrowable(event.entity)) {
            return;
        }

        float draw = getChargeFraction(event.entity);
        if (draw < 1.0F) {
            draw *= draw;
        }
        event.newfov *= 1.0F - draw * 0.15F;
    }

    private static boolean isChargingThrowable(EntityPlayerSP player) {
        return player != null && player.isUsingItem()
            && RaceTraitService.hasHobbitAdvancedTrait(player)
            && HobbitThrowableService.isSupportedThrowable(player.getItemInUse());
    }

    private static float getChargeFraction(EntityPlayerSP player) {
        int heldTicks = Math.max(0, HobbitThrowableService.USE_DURATION_TICKS - player.getItemInUseCount());
        return MathHelper.clamp_float(heldTicks / (float) HobbitThrowableService.FULL_CHARGE_TICKS, 0.0F, 1.0F);
    }

    private static final class HobbitMovementInput extends MovementInput {

        private final MovementInput delegate;

        private HobbitMovementInput(MovementInput delegate) {
            this.delegate = delegate;
        }

        @Override
        public void updatePlayerMoveState() {
            delegate.updatePlayerMoveState();
            moveStrafe = delegate.moveStrafe;
            moveForward = delegate.moveForward;
            jump = delegate.jump;
            sneak = delegate.sneak;

            EntityClientPlayerMP player = Minecraft.getMinecraft().thePlayer;
            if (player == null || !player.onGround || player.isInWater() || player.capabilities.isFlying) {
                return;
            }

            float multiplier = 1.0F;
            if (!player.isRiding() && isChargingThrowable(player)) {
                multiplier = sneak ? HOBBIT_SNEAK_CHARGING_INPUT_MULTIPLIER : HOBBIT_CHARGING_INPUT_MULTIPLIER;
            } else if (sneak && RaceTraitService.hasHobbitAdvancedTrait(player)) {
                multiplier = HOBBIT_SNEAK_INPUT_MULTIPLIER;
            }
            moveStrafe *= multiplier;
            moveForward *= multiplier;
        }
    }
}
