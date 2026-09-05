package com.lotrcharactercreation.client.model;

import net.minecraft.entity.Entity;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.model.LOTRModelOrc;

/**
 * Adapts LOTR's Orc model for use as a player model without duplicating its
 * canonical head and body geometry.
 */
@SideOnly(Side.CLIENT)
public class PlayerOrcModelAdapter extends LOTRModelOrc {

    public PlayerOrcModelAdapter() {
        super();
    }

    public void resetPlayerPresentation() {
        heldItemLeft = 0;
        heldItemRight = 0;
        isSneak = false;
        aimedBow = false;
        isRiding = false;
        onGround = 0.0F;
        resetPlayerLimbState();
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scaleFactor, Entity entity) {
        resetPlayerLimbState();
        super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entity);
    }

    private void resetPlayerLimbState() {
        bipedRightArm.rotateAngleY = 0.0F;
        bipedRightArm.rotateAngleZ = 0.0F;
        bipedLeftArm.rotateAngleY = 0.0F;
        bipedLeftArm.rotateAngleZ = 0.0F;
        bipedRightLeg.rotateAngleY = 0.0F;
        bipedRightLeg.rotateAngleZ = 0.0F;
        bipedLeftLeg.rotateAngleY = 0.0F;
        bipedLeftLeg.rotateAngleZ = 0.0F;
    }
}
