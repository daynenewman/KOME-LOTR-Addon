package com.enovak.lotrmoremobs.handler;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import lotr.common.entity.item.LOTREntityOrcBomb;
import lotr.common.entity.npc.LOTREntityWargBombardier;
import net.minecraft.entity.Entity;
import net.minecraftforge.event.world.ExplosionEvent;

/** Suppresses terrain damage from LOTR NPC bombers when configured off. */
public final class NpcBomberBlockDamageHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onExplosionStart(ExplosionEvent.Start event) {
        if (MumakilConfig.npcBomberBlockDamage
                || event == null
                || event.world == null
                || event.world.isRemote
                || event.explosion == null) {
            return;
        }

        Entity exploder = event.explosion.exploder;
        if (exploder instanceof LOTREntityWargBombardier) {
            event.explosion.isSmoking = false;
            return;
        }

        if (exploder instanceof LOTREntityOrcBomb
                && !((LOTREntityOrcBomb) exploder).droppedByPlayer) {
            event.explosion.isSmoking = false;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (MumakilConfig.npcBomberBlockDamage
                || event == null
                || event.world == null
                || event.world.isRemote
                || event.explosion == null) {
            return;
        }

        Entity exploder = event.explosion.exploder;
        if (exploder instanceof LOTREntityWargBombardier) {
            event.getAffectedBlocks().clear();
            return;
        }

        if (exploder instanceof LOTREntityOrcBomb
                && !((LOTREntityOrcBomb) exploder).droppedByPlayer) {
            event.getAffectedBlocks().clear();
        }
    }
}