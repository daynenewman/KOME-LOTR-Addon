package com.lotrcharactercreation.client.sound;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.PlaySoundAtEntityEvent;

import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache;
import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache.SynchronizedPlayerAppearance;
import com.lotrcharactercreation.sound.RacialPlayerSoundHandler;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class ClientRacialPlayerSoundHandler {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void replaceClientPlayerHurtSound(PlaySoundAtEntityEvent event) {
        if (!(event.entity instanceof EntityPlayer) || !event.entity.worldObj.isRemote
            || !RacialPlayerSoundHandler.VANILLA_PLAYER_HURT_SOUND.equals(event.name)) {
            return;
        }

        SynchronizedPlayerAppearance appearance = ClientPlayerAppearanceCache.getInstance()
            .get((EntityPlayer) event.entity);
        if (appearance == null || !appearance.isCharacterCreationComplete()) {
            return;
        }

        String racialHurtSound = RacialPlayerSoundHandler.getHurtSoundForRace(appearance.getRace());
        if (racialHurtSound != null) {
            event.name = racialHurtSound;
        }
    }
}
