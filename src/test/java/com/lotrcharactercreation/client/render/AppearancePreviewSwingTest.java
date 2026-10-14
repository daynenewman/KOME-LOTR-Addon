package com.lotrcharactercreation.client.render;
import org.junit.Test;
import static org.junit.Assert.*;
public class AppearancePreviewSwingTest {
    @Test public void swingUsesNativeSixTickTimingAndSafeRepeatedClicks(){
        AppearancePreviewSwing s=new AppearancePreviewSwing();assertEquals(0,s.progress(0),0);s.click();s.update();assertEquals(1F/6,s.progress(0),0.001);
        s.click();assertEquals(1F/6,s.progress(0),0.001);s.update();s.update();s.click();assertEquals(0,s.progress(0),0);
        for(int i=0;i<6;i++)s.update();assertEquals(0,s.progress(0),0);s.click();s.update();assertTrue(s.progress(0)>0);
    }
    @Test public void avatarBoundsExcludeControlsAndLabels(){
        assertTrue(AppearancePreviewSwing.hitsAvatar(400,250,400,250));assertFalse(AppearancePreviewSwing.hitsAvatar(500,250,400,250));
        assertFalse(AppearancePreviewSwing.hitsAvatar(400,322,400,250));assertFalse(AppearancePreviewSwing.hitsAvatar(400,310,400,250));
    }
    @Test public void actualClickHandlerHasNoAttackDamageOrNetworkCalls()throws Exception{
        org.objectweb.asm.tree.ClassNode node=new org.objectweb.asm.tree.ClassNode();new org.objectweb.asm.ClassReader("com/lotrcharactercreation/client/gui/GuiAppearanceSelection").accept(node,0);int swings=0;
        for(org.objectweb.asm.tree.MethodNode m:node.methods)if(m.name.equals("mouseClicked"))for(org.objectweb.asm.tree.AbstractInsnNode i:m.instructions.toArray())if(i instanceof org.objectweb.asm.tree.MethodInsnNode){
            org.objectweb.asm.tree.MethodInsnNode call=(org.objectweb.asm.tree.MethodInsnNode)i;assertFalse(call.owner,call.owner.contains("network"));assertFalse(call.name,call.name.toLowerCase().contains("attack")||call.name.toLowerCase().contains("damage"));if(call.name.equals("swing"))swings++;
        }
        assertEquals(1,swings);
    }
    @Test public void allSupportedNativePlayerModelsRespondToSwingField()throws Exception{
        kome.common.KOMEAccessFixture f=new kome.common.KOMEAccessFixture();
        net.minecraft.client.model.ModelBiped[] models={new net.minecraft.client.model.ModelBiped(),new com.lotrcharactercreation.client.model.PlayerManModelAdapter(),new com.lotrcharactercreation.client.model.PlayerDwarfModelAdapter(),new com.lotrcharactercreation.client.model.PlayerElfModelAdapter(),new com.lotrcharactercreation.client.model.PlayerHobbitModelAdapter(),new com.lotrcharactercreation.client.model.PlayerOrcModelAdapter()};
        for(net.minecraft.client.model.ModelBiped model:models){model.onGround=0;model.setRotationAngles(0,0,0,0,0,0.0625F,f.player);float idle=model.bipedRightArm.rotateAngleX;
            model.onGround=0.4F;model.setRotationAngles(0,0,0,0,0,0.0625F,f.player);assertTrue(model.getClass().getName(),Math.abs(model.bipedRightArm.rotateAngleX-idle)>0.1F);model.onGround=0;
        }
        assertEquals(0F,f.player.swingProgress,0);
    }
}
