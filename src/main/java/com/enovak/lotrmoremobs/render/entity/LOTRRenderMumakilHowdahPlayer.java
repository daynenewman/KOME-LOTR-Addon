package com.enovak.lotrmoremobs.render.entity;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.EntityLivingBase;

/** Renders a mounted player standing when the Mumak's howdah is equipped. */
public class LOTRRenderMumakilHowdahPlayer extends RenderPlayer {
    @Override
    protected void renderModel(
            EntityLivingBase entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            float scaleFactor
    ) {
        if (entity.ridingEntity instanceof LOTREntityMumakil
                && ((LOTREntityMumakil)entity.ridingEntity)
                .hasMumakilSyncedHowdahEquipped()) {
            this.modelBipedMain.isRiding = false;
        }

        super.renderModel(
                entity,
                limbSwing,
                limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch,
                scaleFactor
        );
    }
}
