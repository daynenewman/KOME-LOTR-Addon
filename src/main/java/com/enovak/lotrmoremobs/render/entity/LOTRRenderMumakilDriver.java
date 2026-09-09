package com.enovak.lotrmoremobs.render.entity;

import com.enovak.lotrmoremobs.model.mumakil.LOTRModelMumakilDriver;
import com.enovak.lotrmoremobs.handler.MumakilDriverControlEventHandler;
import lotr.client.render.entity.LOTRRenderNearHaradrim;
import net.minecraft.entity.EntityLiving;

/**
 * Preserves LOTR's Southron renderer while allowing the mounted driver model to
 * display its synchronized horn pose.
 */
public class LOTRRenderMumakilDriver extends LOTRRenderNearHaradrim {
    public LOTRRenderMumakilDriver() {
        super();
        LOTRModelMumakilDriver driverModel =
                new LOTRModelMumakilDriver();
        this.mainModel = driverModel;
        this.modelBipedMain = driverModel;
    }

    @Override
    public void doRender(
            EntityLiving entity,
            double x,
            double y,
            double z,
            float yaw,
            float partialTicks
    ) {
        if (!LOTRModelMumakilDriver.isMountedMumakDriver(entity)) {
            MumakilDriverControlEventHandler
                    .applyMountedDriverPoseIfValid(entity);
            super.doRender(entity, x, y, z, yaw, partialTicks);
            return;
        }

        float limbSwing = entity.limbSwing;
        float limbSwingAmount = entity.limbSwingAmount;
        float prevLimbSwingAmount = entity.prevLimbSwingAmount;
        entity.limbSwing = 0.0F;
        entity.limbSwingAmount = 0.0F;
        entity.prevLimbSwingAmount = 0.0F;

        try {
            MumakilDriverControlEventHandler
                    .applyMountedDriverPoseIfValid(entity);
            super.doRender(entity, x, y, z, yaw, partialTicks);
        } finally {
            entity.limbSwing = limbSwing;
            entity.limbSwingAmount = limbSwingAmount;
            entity.prevLimbSwingAmount = prevLimbSwingAmount;
        }
    }
}
