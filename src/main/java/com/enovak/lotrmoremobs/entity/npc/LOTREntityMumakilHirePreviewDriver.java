package com.enovak.lotrmoremobs.entity.npc;

import lotr.common.entity.npc.LOTREntitySouthronChampion;
import net.minecraft.world.World;

/**
 * Client-only cosmetic driver used by LOTR's unit-hiring preview.
 *
 * This class is deliberately not registered as an entity. The preview object is
 * never added to a world, saved, ticked, or allowed to emit sounds.
 */
public class LOTREntityMumakilHirePreviewDriver extends LOTREntitySouthronChampion {
    public LOTREntityMumakilHirePreviewDriver(World world) {
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
