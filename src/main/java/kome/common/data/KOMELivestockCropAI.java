package kome.common.data;

import lotr.common.LOTRBannerProtection;
import lotr.common.LOTRMod;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRFarmhand;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.EntityAITasks;
import net.minecraft.entity.passive.*;
import net.minecraft.init.Blocks;
import net.minecraft.util.MathHelper;

/** Bounded adaptation of v36.15 rabbit crop approach/eating and farmhand deterrence. */
public final class KOMELivestockCropAI extends EntityAIBase {
    private final EntityAnimal animal;
    private int cooldown,elapsed,eating,x,y,z;
    private boolean attempting;
    private KOMELivestockCropAI(EntityAnimal animal){this.animal=animal;cooldown=100+animal.getRNG().nextInt(500);setMutexBits(3);}
    public static boolean eligible(Class<?> type){return type==EntityCow.class||type==EntitySheep.class||type==EntityPig.class;}
    public static boolean edible(Block block,int age){
        return block!=null&&age==7&&(block==Blocks.wheat||block==Blocks.carrots||block==Blocks.potatoes
            ||block==LOTRMod.lettuceCrop||block==LOTRMod.leekCrop||block==LOTRMod.turnipCrop||block==LOTRMod.yamCrop);
    }
    public static void install(EntityLiving entity){
        if(entity.worldObj==null||entity.worldObj.isRemote)return;
        if(eligible(entity.getClass())){
            for(Object value:entity.tasks.taskEntries)if(((EntityAITasks.EntityAITaskEntry)value).action instanceof KOMELivestockCropAI)return;
            entity.tasks.addTask(5,new KOMELivestockCropAI((EntityAnimal)entity));
        }else if(entity instanceof LOTREntityNPC&&entity instanceof LOTRFarmhand){
            for(Object value:entity.tasks.taskEntries)if(((EntityAITasks.EntityAITaskEntry)value).action instanceof Deterrence)return;
            entity.tasks.addTask(2,new Deterrence((LOTREntityNPC)entity));
        }
    }
    private boolean allowed(){return !animal.worldObj.isRemote&&animal.worldObj.getGameRules().getGameRuleBooleanValue("mobGriefing")&&LOTRMod.canGrief(animal.worldObj);}
    private boolean crop(){return allowed()&&animal.worldObj.blockExists(x,y,z)
        &&edible(animal.worldObj.getBlock(x,y,z),animal.worldObj.getBlockMetadata(x,y,z))
        &&!LOTRBannerProtection.isProtected(animal.worldObj,x,y,z,LOTRBannerProtection.anyBanner(),false);}
    @Override public boolean shouldExecute(){
        if(cooldown-- >0||!allowed()||animal.isChild()||animal.isInLove()||animal.getAttackTarget()!=null)return false;
        cooldown=200+animal.getRNG().nextInt(400);
        int ax=MathHelper.floor_double(animal.posX),ay=MathHelper.floor_double(animal.boundingBox.minY),az=MathHelper.floor_double(animal.posZ);
        // Native rabbit samples nearby plants. Thirty-two probes only after the cooldown.
        for(int i=0;i<32;i++){
            x=ax+animal.getRNG().nextInt(9)-4;y=ay+animal.getRNG().nextInt(3)-1;z=az+animal.getRNG().nextInt(9)-4;
            if(crop()&&animal.getNavigator().getPathToXYZ(x+0.5,y,z+0.5)!=null)return true;
        }
        return false;
    }
    @Override public void startExecuting(){attempting=true;elapsed=eating=0;}
    @Override public boolean continueExecuting(){return attempting&&elapsed<200&&eating<60&&crop()&&animal.getAttackTarget()==null;}
    @Override public void resetTask(){attempting=false;animal.getNavigator().clearPathEntity();cooldown=600+animal.getRNG().nextInt(600);}
    @Override public void updateTask(){
        elapsed++;
        animal.getLookHelper().setLookPosition(x+0.5,y+0.3,z+0.5,10,animal.getVerticalFaceSpeed());
        if(animal.getDistanceSq(x+0.5,y,z+0.5)>1.5D){if(elapsed%10==1)animal.getNavigator().tryMoveToXYZ(x+0.5,y,z+0.5,1D);eating=0;return;}
        if(!crop()){attempting=false;return;}
        if(++eating%6==0)animal.playSound("random.eat",0.5F,1F);
        if(eating>=60){animal.worldObj.func_147480_a(x,y,z,false);attempting=false;}
    }
    private void deter(){attempting=false;cooldown=1200;animal.getNavigator().clearPathEntity();}
    static KOMELivestockCropAI offender(EntityAnimal animal){
        for(Object value:animal.tasks.taskEntries){EntityAIBase task=((EntityAITasks.EntityAITaskEntry)value).action;
            if(task instanceof KOMELivestockCropAI){KOMELivestockCropAI crop=(KOMELivestockCropAI)task;if(crop.attempting&&crop.crop())return crop;}}
        return null;
    }
    /** Movement task, not a second hostile target selector. It never changes native faction targets. */
    static final class Deterrence extends EntityAIBase {
        private final LOTREntityNPC worker;
        private KOMELivestockCropAI offender;
        private int elapsed,cooldown;
        private double originX,originZ;
        Deterrence(LOTREntityNPC worker){this.worker=worker;setMutexBits(3);}
        private boolean available(){return worker.isEntityAlive()&&!worker.isChild()&&worker.getAttackTarget()==null
            &&worker.hiredNPCInfo!=null&&(!worker.hiredNPCInfo.isActive||worker.hiredNPCInfo.getTask()==LOTRHiredNPCInfo.Task.FARMER&&!worker.hiredNPCInfo.isHalted())
            &&KOMEProgressionEncounterMarker.read(worker)==null;}
        @Override public boolean shouldExecute(){
            if(cooldown-->0||!available())return false;cooldown=20;
            for(Object value:worker.worldObj.getEntitiesWithinAABB(EntityAnimal.class,worker.boundingBox.expand(8,3,8))){
                EntityAnimal animal=(EntityAnimal)value;
                if(!eligible(animal.getClass())||!worker.getEntitySenses().canSee(animal))continue;
                KOMELivestockCropAI candidate=offender(animal);
                if(candidate!=null){offender=candidate;return true;}
            }
            return false;
        }
        @Override public void startExecuting(){elapsed=0;originX=worker.posX;originZ=worker.posZ;}
        @Override public boolean continueExecuting(){return elapsed<60&&available()&&offender!=null&&offender.attempting
            &&offender.animal.isEntityAlive()&&worker.getDistanceSqToEntity(offender.animal)<=64
            &&(worker.posX-originX)*(worker.posX-originX)+(worker.posZ-originZ)*(worker.posZ-originZ)<=64;}
        @Override public void updateTask(){
            elapsed++;EntityAnimal animal=offender.animal;worker.getLookHelper().setLookPositionWithEntity(animal,30,30);
            if(worker.getDistanceSqToEntity(animal)<=4){
                double dx=worker.posX-animal.posX,dz=worker.posZ-animal.posZ;if(dx*dx+dz*dz<0.001D)dx=0.1D;
                worker.swingItem();animal.knockBack(worker,0.4F,dx,dz);offender.deter();
            }else if(elapsed%10==1)worker.getNavigator().tryMoveToEntityLiving(animal,1D);
        }
        @Override public void resetTask(){
            worker.getNavigator().clearPathEntity();offender=null;cooldown=100;
            // Native farming/wandering resumes at its next scheduler update.
        }
    }
}
