package com.lotrcharactercreation.client.model;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

import com.lotrcharactercreation.appearance.PlayerSex;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.model.LOTRModelHuman;

/**
 * Adapts LOTR's human model for player entities. The model's built-in female
 * chest is NPC-driven, so the adapter supplies an equivalent player-controlled
 * chest part.
 */
@SideOnly(Side.CLIENT)
public class PlayerManModelAdapter extends LOTRModelHuman {

    private final ModelRenderer playerChest;

    public PlayerManModelAdapter() {
        super();
        playerChest = new ModelRenderer(this, 24, 0);
        playerChest.addBox(-3.0F, 2.0F, -4.0F, 6, 3, 2, 0.0F);
        playerChest.setRotationPoint(0.0F, 0.0F, 0.0F);
        playerChest.showModel = false;
        bipedBody.addChild(playerChest);
    }

    public void configurePlayerPresentation(PlayerSex sex) {
        configurePlayerPresentation(sex, false);
    }

    public void configurePlayerPresentation(PlayerSex sex, boolean wearingChestArmor) {
        playerChest.showModel = sex == PlayerSex.FEMALE && !wearingChestArmor;
        bipedHeadwear.showModel = true;
    }

    public void resetPlayerPresentation() {
        playerChest.showModel = false;
        bipedChest.showModel = false;
        bipedHeadwear.showModel = true;
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
