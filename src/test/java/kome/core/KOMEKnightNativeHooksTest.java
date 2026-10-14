package kome.core;

import java.io.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEKnightNativeHooksTest {
    private static byte[] bytes(String type)throws IOException {
        try(InputStream input=KOMEKnightNativeHooksTest.class.getClassLoader().getResourceAsStream(type.replace('.','/')+".class");ByteArrayOutputStream output=new ByteArrayOutputStream()){
            assertNotNull(type,input);byte[] block=new byte[8192];int n;while((n=input.read(block))>=0)output.write(block,0,n);return output.toByteArray();
        }
    }
    private static int calls(byte[] data,String owner,String method){
        ClassNode node=new ClassNode();new ClassReader(data).accept(node,0);int count=0;
        for(MethodNode m:node.methods)for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;if(owner.equals(call.owner)&&method.equals(call.name))count++;}return count;
    }
    @Test public void actualV3615OutcomeMethodIsHookedOnceAndIdempotently()throws Exception {
        String type="lotr.common.entity.LOTREntityInvasionSpawner";byte[] original=bytes(type);KOMEObjectiveInvasionTransformer transformer=new KOMEObjectiveInvasionTransformer();byte[] transformed=transformer.transform(type,type,original);
        assertEquals(1,calls(transformed,"kome/common/data/KOMEObjectiveInvasionBridge","ended"));assertArrayEquals(transformed,transformer.transform(type,type,transformed));
        ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);for(MethodNode m:node.methods)if(m.name.equals("endInvasion")){assertEquals("(Z)V",m.desc);assertEquals(Opcodes.ALOAD,m.instructions.getFirst().getOpcode());}
    }
    @Test public void actualNativeAndMinecraftPropertyProcessingInputsHaveNarrowHooks()throws Exception {
        KOMEQuestPropertyTransformer transformer=new KOMEQuestPropertyTransformer();
        for(String type:new String[]{"net.minecraft.item.crafting.CraftingManager","net.minecraft.item.crafting.FurnaceRecipes","lotr.common.entity.npc.LOTRTradeEntry","net.minecraft.inventory.ContainerRepair","lotr.common.inventory.LOTRContainerCraftingTable","lotr.common.inventory.LOTRContainerAnvil"}){
            byte[] transformed=transformer.transform(type,type,bytes(type));String method=type.endsWith("LOTRContainerCraftingTable")?"boundNativeCrafting":type.endsWith("LOTRContainerAnvil")?"boundNativeAnvil":type.endsWith("ContainerRepair")?"boundRepair":type.endsWith("CraftingManager")?"boundRecipe":"bound";
            assertEquals(type,1,calls(transformed,"kome/common/data/KOMEQuestPropertyUseGuard",method));assertArrayEquals(transformed,transformer.transform(type,type,transformed));
        }
        byte[] ordinary=bytes("lotr.common.entity.npc.LOTREntityNPC");assertArrayEquals(ordinary,transformer.transform("lotr.common.entity.npc.LOTREntityNPC","lotr.common.entity.npc.LOTREntityNPC",ordinary));
    }
}
