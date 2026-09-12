package com.fuzs.aquaacrobatics.client.model;

import com.enovak.lotrmoremobs.client.config.ClientServerGameplayState;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import com.fuzs.aquaacrobatics.config.ConfigHandler;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.fuzs.aquaacrobatics.integration.charactercreation.CharacterCreationIntegration;
import com.fuzs.aquaacrobatics.integration.efr.EFRIntegration;
import com.fuzs.aquaacrobatics.util.math.MathHelperNew;

/** Exact former ModelBipedMixin animation and pose policy. */
public final class AquaModelBipedLogic {
    private AquaModelBipedLogic() {}
    public static void render(ModelBiped m,float a,float b,float c,float d,float pitch,float f,Entity e) {
        if(ClientServerGameplayState.useModernPlayerAnimations()&&e instanceof IPlayerResizeable) { boolean elytra=EFRIntegration.getTicksElytraFlying((EntityPlayer)e)>4; boolean swim=((IPlayerResizeable)e).isActuallySwimming(); float s=((IModelBipedSwimming)m).getSwimAnimation(); if(!elytra&&s>0) pitch=rotLerp(s,m.bipedHead.rotateAngleX,swim?-(float)Math.PI/4F:pitch*(float)Math.PI/180F)/.017453292F; }
        m.setRotationAngles(a,b,c,d,pitch,f,e);
    }
    public static void pre(ModelBiped m,float limb,float amount,float age,float yaw,float pitch,float scale,Entity e) {
        boolean modern=ClientServerGameplayState.useModernPlayerAnimations();
        if(FirstPersonArmRenderContext.isActive()) m.isSneak=false; else if(e instanceof EntityPlayer&&e instanceof IPlayerResizeable)m.isSneak=e.isSneaking()||(modern&&((IPlayerResizeable)e).getPose()==Pose.CROUCHING);
        if(!modern||!ConfigHandler.MiscellaneousConfig.eatingAnimation||!(e instanceof EntityLivingBase))return; EntityLivingBase living=(EntityLivingBase)e;
        if(living instanceof EntityPlayer&&((EntityPlayer)living).getHeldItem()!=null&&((EntityPlayer)living).getItemInUseCount()>0){EntityPlayer player=(EntityPlayer)living;ItemStack stack=living.getHeldItem(); if(stack!=null&&(stack.getItemUseAction()==EnumAction.eat||stack.getItemUseAction()==EnumAction.drink)){float partial=age-(float)Math.floor(age),count=player.getItemInUseCount()-partial+1F,ratio=count/(float)stack.getMaxItemUseDuration(),x=1F-(float)Math.pow(ratio,27D); if(ratio<.8F)x+=MathHelper.abs(MathHelper.cos(count/4F*(float)Math.PI)*.1F); m.bipedRightArm.rotateAngleX=x*(m.bipedRightArm.rotateAngleX*.5F-(float)Math.PI*4F/10F);m.bipedRightArm.rotateAngleY=x*(float)Math.PI/6F*-1F;m.bipedLeftArm.rotateAngleX=x*(m.bipedLeftArm.rotateAngleX*.5F-(float)Math.PI*4F/10F);m.bipedLeftArm.rotateAngleY=x*(float)Math.PI/6F;}}
    }
    public static void post(ModelBiped m,float limb,float amount,float age,float yaw,float pitch,float scale,Entity e) {
        if(!ClientServerGameplayState.useModernPlayerAnimations()){
            ((IModelBipedSwimming)m).setSwimAnimation(0);
            return;
        }

        // LOTR Character Creation adapters call into LOTRModel* classes after the
        // transformed ModelBiped method. Those subclasses can overwrite the arm/leg
        // rotations Aqua just applied, so their final swimming pose is applied by a
        // second, adapter-level ASM hook after the complete LOTR model method returns.
        if(isCharacterCreationModel(m)) return;

        if(!FirstPersonArmRenderContext.isActive()&&e instanceof EntityPlayer&&e instanceof IPlayerResizeable) {
            AquaPlayerRenderLogic.applyPosePivots(
                ((IPlayerResizeable)e).getPose(),
                m.bipedHead,m.bipedHeadwear,m.bipedBody,m.bipedRightArm,m.bipedLeftArm,m.bipedRightLeg,m.bipedLeftLeg);
        }

        applySwimmingAnimation(m,limb,((IModelBipedSwimming)m).getSwimAnimation());
    }

    /**
     * Stable Object-signature entry point used by the optional Character Creation
     * adapter transformer. Object parameters keep the injected descriptor valid in
     * both dev and raw-obfuscated 1.7.10 environments.
     */
    public static void postCharacterCreation(Object model,float limb,float amount,float age,float yaw,float pitch,float scale,Object entity) {
        if(!(model instanceof ModelBiped)||!(entity instanceof EntityPlayer)) return;

        ModelBiped m=(ModelBiped)model;
        EntityPlayer player=(EntityPlayer)entity;
        if(!(player instanceof IPlayerResizeable)||!CharacterCreationIntegration.hasCharacterCreationRace(player)) return;

        if(!ClientServerGameplayState.useModernPlayerAnimations()){
            ((IModelBipedSwimming)m).setSwimAnimation(0);
            return;
        }

        // Do not rely on LOTRModel* calling ModelBiped#setLivingAnimations: custom
        // adapters may override that inheritance path. Derive the same interpolated
        // value directly from the player after the final model angles are established.
        float partial=age-(float)Math.floor(age);
        float swim=((IPlayerResizeable)player).getSwimAnimation(partial);
        ((IModelBipedSwimming)m).setSwimAnimation(swim);
        applySwimmingAnimation(m,limb,swim);
    }

    private static void applySwimmingAnimation(ModelBiped m,float limb,float s) {
        if(s<=0)return;
        float f1=limb%26F,f2=m.onGround>0?0:s,f3=f2;
        if(f1<14F){
            m.bipedLeftArm.rotateAngleX=rotLerp(f3,m.bipedLeftArm.rotateAngleX,0);
            m.bipedRightArm.rotateAngleX=MathHelperNew.lerp(f2,m.bipedRightArm.rotateAngleX,0);
            m.bipedLeftArm.rotateAngleY=rotLerp(f3,m.bipedLeftArm.rotateAngleY,(float)Math.PI);
            m.bipedRightArm.rotateAngleY=MathHelperNew.lerp(f2,m.bipedRightArm.rotateAngleY,(float)Math.PI);
            m.bipedLeftArm.rotateAngleZ=rotLerp(f3,m.bipedLeftArm.rotateAngleZ,(float)Math.PI+1.8707964F*arm(f1)/arm(14));
            m.bipedRightArm.rotateAngleZ=MathHelperNew.lerp(f2,m.bipedRightArm.rotateAngleZ,(float)Math.PI-1.8707964F*arm(f1)/arm(14));
        } else if(f1<22F){
            float x=(f1-14)/8F;
            m.bipedLeftArm.rotateAngleX=rotLerp(f3,m.bipedLeftArm.rotateAngleX,(float)Math.PI/2F*x);
            m.bipedRightArm.rotateAngleX=MathHelperNew.lerp(f2,m.bipedRightArm.rotateAngleX,(float)Math.PI/2F*x);
            m.bipedLeftArm.rotateAngleY=rotLerp(f3,m.bipedLeftArm.rotateAngleY,(float)Math.PI);
            m.bipedRightArm.rotateAngleY=MathHelperNew.lerp(f2,m.bipedRightArm.rotateAngleY,(float)Math.PI);
            m.bipedLeftArm.rotateAngleZ=rotLerp(f3,m.bipedLeftArm.rotateAngleZ,5.012389F-1.8707964F*x);
            m.bipedRightArm.rotateAngleZ=MathHelperNew.lerp(f2,m.bipedRightArm.rotateAngleZ,1.2707963F+1.8707964F*x);
        } else {
            float x=(f1-22)/4F;
            m.bipedLeftArm.rotateAngleX=rotLerp(f3,m.bipedLeftArm.rotateAngleX,(float)Math.PI/2F-(float)Math.PI/2F*x);
            m.bipedRightArm.rotateAngleX=MathHelperNew.lerp(f2,m.bipedRightArm.rotateAngleX,(float)Math.PI/2F-(float)Math.PI/2F*x);
            m.bipedLeftArm.rotateAngleY=rotLerp(f3,m.bipedLeftArm.rotateAngleY,(float)Math.PI);
            m.bipedRightArm.rotateAngleY=MathHelperNew.lerp(f2,m.bipedRightArm.rotateAngleY,(float)Math.PI);
            m.bipedLeftArm.rotateAngleZ=rotLerp(f3,m.bipedLeftArm.rotateAngleZ,(float)Math.PI);
            m.bipedRightArm.rotateAngleZ=MathHelperNew.lerp(f2,m.bipedRightArm.rotateAngleZ,(float)Math.PI);
        }
        m.bipedLeftLeg.rotateAngleX=MathHelperNew.lerp(s,m.bipedLeftLeg.rotateAngleX,.3F*MathHelper.cos(limb*.33333334F+(float)Math.PI));
        m.bipedRightLeg.rotateAngleX=MathHelperNew.lerp(s,m.bipedRightLeg.rotateAngleX,.3F*MathHelper.cos(limb*.33333334F));
    }

    public static void living(ModelBiped m,EntityLivingBase e,float a,float b,float partial){if(!ClientServerGameplayState.useModernPlayerAnimations()){((IModelBipedSwimming)m).setSwimAnimation(0);return;}if(e instanceof IPlayerResizeable)((IModelBipedSwimming)m).setSwimAnimation(((IPlayerResizeable)e).getSwimAnimation(partial));}
    private static boolean isCharacterCreationModel(ModelBiped model){String name=model.getClass().getName();return name.startsWith("com.lotrcharactercreation.client.model.");}
    private static float arm(float x){return -65F*x+x*x;} private static float rotLerp(float a,float max,float target){float f=(target-max)%((float)Math.PI*2F);if(f<-(float)Math.PI)f+=(float)Math.PI*2F;if(f>=(float)Math.PI)f-=(float)Math.PI*2F;return max+a*f;}
}
