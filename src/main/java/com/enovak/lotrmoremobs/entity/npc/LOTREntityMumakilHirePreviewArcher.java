package com.enovak.lotrmoremobs.entity.npc;

import net.minecraft.world.World;

/**
 * Client-only cosmetic archer used by the Mumak unit-trade preview.
 *
 * It is deliberately unregistered and is rendered explicitly without ever
 * being added to a world or allowed to tick.
 */
public class LOTREntityMumakilHirePreviewArcher
        extends LOTREntityMumakilHowdahArcher {
    public LOTREntityMumakilHirePreviewArcher(World world) {
        super(world);
    }

    @Override
    public void onUpdate() {
    }

    @Override
    public void onLivingUpdate() {
    }

    @Override
    public void playSound(String sound, float volume, float pitch) {
    }
}
