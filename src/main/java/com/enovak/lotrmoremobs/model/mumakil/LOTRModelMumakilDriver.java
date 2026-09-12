package com.enovak.lotrmoremobs.model.mumakil;

import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import lotr.client.model.LOTRModelHuman;
import lotr.common.entity.npc.LOTREntitySouthronChampion;
import net.minecraft.entity.Entity;

/**
 * Native Southron human model with one isolated pose for a Mumak driver's
 * synchronized horn timer.
 */
public class LOTRModelMumakilDriver extends LOTRModelHuman {
    public LOTRModelMumakilDriver() {
        super();
    }

    public LOTRModelMumakilDriver(float scale, boolean smallArms) {
        super(scale, smallArms);
    }

    @Override
    public void setRotationAngles(
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            float scaleFactor,
            Entity entity
    ) {
        boolean mountedDriver = isMountedMumakDriver(entity);
        if (mountedDriver) {
            this.isRiding = false;
        }
        super.setRotationAngles(
                mountedDriver ? 0.0F : limbSwing,
                mountedDriver ? 0.0F : limbSwingAmount,
                ageInTicks,
                netHeadYaw,
                headPitch,
                scaleFactor,
                entity
        );

        if (!mountedDriver) {
            return;
        }

        /*
         * The entity remains genuinely mounted. Only its leg model is held in
         * a restrained standing stance on the howdah platform.
         */
        this.bipedRightLeg.rotateAngleX = 0.0F;
        this.bipedRightLeg.rotateAngleZ = 0.0F;
        this.bipedLeftLeg.rotateAngleX = 0.0F;
        this.bipedLeftLeg.rotateAngleZ = 0.0F;

        /*
         * LOTR's human model keeps headwear as a sibling of the head and
         * copies the head transform each frame. Repeat that native relationship
         * after the custom pose so helmets cannot retain a body-level offset.
         */
        this.bipedHeadwear.rotationPointX = this.bipedHead.rotationPointX;
        this.bipedHeadwear.rotationPointY = this.bipedHead.rotationPointY;
        this.bipedHeadwear.rotationPointZ = this.bipedHead.rotationPointZ;
        this.bipedHeadwear.rotateAngleX = this.bipedHead.rotateAngleX;
        this.bipedHeadwear.rotateAngleY = this.bipedHead.rotateAngleY;
        this.bipedHeadwear.rotateAngleZ = this.bipedHead.rotateAngleZ;

        LOTREntityMumakil mumakil = (LOTREntityMumakil)entity.ridingEntity;
        if (mumakil.getMountedDriverHornTicks() <= 0) {
            return;
        }

        this.bipedRightArm.rotateAngleX = -1.45F;
        this.bipedRightArm.rotateAngleY = -0.25F;
        this.bipedRightArm.rotateAngleZ = 0.10F;
        this.bipedLeftArm.rotateAngleX = -1.35F;
        this.bipedLeftArm.rotateAngleY = 0.35F;
        this.bipedLeftArm.rotateAngleZ = -0.15F;
    }

    public static boolean isMountedMumakDriver(Entity entity) {
        if (!(entity instanceof LOTREntitySouthronChampion)
                || !(entity.ridingEntity instanceof LOTREntityMumakil)) {
            return false;
        }

        LOTREntityMumakil mumakil =
                (LOTREntityMumakil)entity.ridingEntity;
        return entity.worldObj == mumakil.worldObj
                && !entity.isDead
                && entity.isEntityAlive()
                && !mumakil.isDead
                && mumakil.isEntityAlive()
                && mumakil.riddenByEntity == entity
                && mumakil.hasMumakilSyncedHowdahEquipped();
    }
}
