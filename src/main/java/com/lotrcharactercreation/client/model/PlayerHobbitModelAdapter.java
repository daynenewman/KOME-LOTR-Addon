package com.lotrcharactercreation.client.model;

import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;

import org.lwjgl.opengl.GL11;

import com.lotrcharactercreation.appearance.PlayerSex;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import lotr.client.model.LOTRModelHobbit;

@SideOnly(Side.CLIENT)
public class PlayerHobbitModelAdapter extends LOTRModelHobbit {

    private static final float FIRST_PERSON_MODEL_SCALE = 0.0625F;
    private static final float HOBBIT_LIMB_Y_SCALE = 5.0F / 6.0F;
    private static final float VANILLA_ARM_PIVOT_Y = 2.0F;

    private final ModelRenderer playerChest;

    public PlayerHobbitModelAdapter() {
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

    public void renderFirstPersonRightArm(EntityPlayer player) {
        float previousOnGround = onGround;
        GL11.glColor3f(1.0F, 1.0F, 1.0F);
        GL11.glPushMatrix();
        try {
            onGround = 0.0F;
            setRotationAngles(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, FIRST_PERSON_MODEL_SCALE, player);

            GL11.glTranslatef(0.0F, VANILLA_ARM_PIVOT_Y * FIRST_PERSON_MODEL_SCALE, 0.0F);
            GL11.glScalef(1.0F, HOBBIT_LIMB_Y_SCALE, 1.0F);
            GL11.glTranslatef(0.0F, -bipedRightArm.rotationPointY * FIRST_PERSON_MODEL_SCALE, 0.0F);
            bipedRightArm.render(FIRST_PERSON_MODEL_SCALE);
        } finally {
            onGround = previousOnGround;
            GL11.glPopMatrix();
        }
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
