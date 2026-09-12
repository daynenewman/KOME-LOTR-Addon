package com.enovak.lotrmoremobs.render.entity;

import com.enovak.lotrmoremobs.entity.npc.LOTREntityMumakilHowdahArcher;
import java.util.Iterator;
import java.util.Map;
import lotr.common.entity.npc.LOTREntityNearHaradrimArcher;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/**
 * Renderer for the custom howdah archer passenger.
 *
 * It first tries to delegate to LOTR's normal archer/NPC renderer at actual
 * render time. If that renderer is still unavailable, it falls back to a biped
 * renderer using a LOTR Southron/Near Harad texture path instead of Steve.
 */
public class LOTRRenderMumakilHowdahArcher extends RenderBiped {
    private static final ResourceLocation SOUTHON_FALLBACK_TEXTURE = new ResourceLocation("lotr:mob/nearHarad/southron.png");

    private Render deferredLotrRenderer;
    private boolean attemptedDeferredLookup;

    public LOTRRenderMumakilHowdahArcher() {
        super(new ModelBiped(), 0.5F);
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z, float yaw, float partialTicks) {
        if (entity instanceof LOTREntityMumakilHowdahArcher) {
            ((LOTREntityMumakilHowdahArcher)entity).resetAttachedLocomotionAnimation();
        }

        Render lotrRenderer = this.getDeferredLotrRenderer();

        if (lotrRenderer != null && lotrRenderer != this) {
            lotrRenderer.doRender(entity, x, y, z, yaw, partialTicks);
            return;
        }

        super.doRender(entity, x, y, z, yaw, partialTicks);
    }

    private Render getDeferredLotrRenderer() {
        if (!this.attemptedDeferredLookup) {
            this.attemptedDeferredLookup = true;
            this.deferredLotrRenderer = this.findCompatibleLotrRenderer();

            if (this.deferredLotrRenderer != null) {
                System.out.println("[LOTRMoreMobs] Deferred Mumakil howdah archer renderer lookup found compatible LOTR renderer: " + this.deferredLotrRenderer.getClass().getName());
            } else {
                System.out.println("[LOTRMoreMobs] Deferred Mumakil howdah archer renderer lookup using Southron texture fallback.");
            }
        }

        return this.deferredLotrRenderer;
    }

    private Render findCompatibleLotrRenderer() {
        Render exact = (Render)RenderManager.instance.entityRenderMap.get(LOTREntityNearHaradrimArcher.class);
        if (exact != null && exact != this) {
            return exact;
        }

        Class customClass = LOTREntityMumakilHowdahArcher.class;
        Map renderMap = RenderManager.instance.entityRenderMap;
        Iterator iterator = renderMap.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry entry = (Map.Entry)iterator.next();
            Object key = entry.getKey();
            Object value = entry.getValue();

            if (!(key instanceof Class) || !(value instanceof Render) || value == this) {
                continue;
            }

            Class entityClass = (Class)key;
            Render render = (Render)value;
            String renderName = render.getClass().getName();

            if (entityClass.isAssignableFrom(customClass) && renderName.startsWith("lotr.")) {
                return render;
            }
        }

        return null;
    }

    protected ResourceLocation getHowdahArcherTexture(LOTREntityMumakilHowdahArcher entity) {
        return SOUTHON_FALLBACK_TEXTURE;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return this.getHowdahArcherTexture((LOTREntityMumakilHowdahArcher)entity);
    }
}
