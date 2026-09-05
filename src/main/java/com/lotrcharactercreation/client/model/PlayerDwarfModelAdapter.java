package com.lotrcharactercreation.client.model;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

import com.lotrcharactercreation.appearance.PlayerSex;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.model.LOTRModelDwarf;

@SideOnly(Side.CLIENT)
public class PlayerDwarfModelAdapter extends LOTRModelDwarf {

    private final ModelRenderer playerChest;

    public PlayerDwarfModelAdapter() {
        super(0.0F, 64, 64);
        playerChest = new ModelRenderer(this, 24, 0);
        playerChest.addBox(-3.0F, 2.0F, -4.0F, 6, 3, 2, 0.0F);
        playerChest.setRotationPoint(0.0F, 0.0F, 0.0F);
        playerChest.showModel = false;
        bipedBody.addChild(playerChest);
    }

    public void configurePlayerPresentation(PlayerSex sex, boolean wearingChestArmor) {
        playerChest.showModel = sex == PlayerSex.FEMALE && !wearingChestArmor;
    }

    public void resetPlayerPresentation() {
        playerChest.showModel = false;
        heldItemLeft = 0;
        heldItemRight = 0;
        isSneak = false;
        aimedBow = false;
        isRiding = false;
        resetPlayerLegState();
    }

    @Override
    public void setRotationAngles(float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
        float headPitch, float scaleFactor, Entity entity) {
        resetPlayerLegState();
        super.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scaleFactor, entity);
    }

    private void resetPlayerLegState() {
        bipedRightLeg.rotateAngleY = 0.0F;
        bipedRightLeg.rotateAngleZ = 0.0F;
        bipedLeftLeg.rotateAngleY = 0.0F;
        bipedLeftLeg.rotateAngleZ = 0.0F;
    }
}
