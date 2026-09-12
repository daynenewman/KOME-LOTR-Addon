package com.enovak.lotrmoremobs.render.entity;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/**
 * Suppresses LOTR's generic rider preview pass. The Mumak preview renderer draws
 * this cosmetic driver at the real howdah anchor together with the archers.
 */
public class LOTRRenderMumakilHirePreviewDriver extends Render {
    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return null;
    }
}
