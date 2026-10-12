package kome.common.data;
import lotr.common.LOTRMod;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.passive.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class KOMELivestockCropAITest {
    @Test public void onlyNativeFarmLivestockAreEligible(){
        assertTrue(KOMELivestockCropAI.eligible(EntityCow.class));assertTrue(KOMELivestockCropAI.eligible(EntitySheep.class));assertTrue(KOMELivestockCropAI.eligible(EntityPig.class));
        assertFalse(KOMELivestockCropAI.eligible(EntityAnimal.class));assertFalse(KOMELivestockCropAI.eligible(EntityWolf.class));assertFalse(KOMELivestockCropAI.eligible(EntityHorse.class));assertFalse(KOMELivestockCropAI.eligible(lotr.common.entity.animal.LOTREntityRabbit.class));
    }
    @Test public void onlyMatureExplicitEdibleCropsAreAccepted(){
        Block old=LOTRMod.lettuceCrop,crop=new Block(Material.plants){};
        try{LOTRMod.lettuceCrop=crop;for(int age=0;age<7;age++)assertFalse(KOMELivestockCropAI.edible(crop,age));assertTrue(KOMELivestockCropAI.edible(crop,7));
            assertFalse(KOMELivestockCropAI.edible(new Block(Material.wood){},7));assertFalse(KOMELivestockCropAI.edible(null,7));assertFalse(KOMELivestockCropAI.edible(crop,8));
        }finally{LOTRMod.lettuceCrop=old;}
    }
}
