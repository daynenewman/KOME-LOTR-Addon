package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Narrow input guards for native crafting, smelting and NPC selling. No item registry changes. */
public final class KOMEQuestPropertyTransformer implements IClassTransformer {
    public byte[] transform(String name,String transformedName,byte[] bytes){
        boolean crafting="net.minecraft.item.crafting.CraftingManager".equals(transformedName),
            smelting="net.minecraft.item.crafting.FurnaceRecipes".equals(transformedName),
            trading="lotr.common.entity.npc.LOTRTradeEntry".equals(transformedName),repair="net.minecraft.inventory.ContainerRepair".equals(transformedName);
        boolean nativeCrafting="lotr.common.inventory.LOTRContainerCraftingTable".equals(transformedName),nativeAnvil="lotr.common.inventory.LOTRContainerAnvil".equals(transformedName);
        if(bytes==null||!crafting&&!smelting&&!trading&&!repair&&!nativeCrafting&&!nativeAnvil)return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int count=0;
        for(MethodNode method:node.methods){
            boolean target=crafting&&(method.name.equals("findMatchingRecipe")||method.name.equals("func_82787_a"))
                &&method.desc.equals("(Lnet/minecraft/inventory/InventoryCrafting;Lnet/minecraft/world/World;)Lnet/minecraft/item/ItemStack;")
                ||smelting&&(method.name.equals("getSmeltingResult")||method.name.equals("func_151395_a"))&&method.desc.equals("(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/item/ItemStack;")
                ||trading&&method.name.equals("matches")&&method.desc.equals("(Lnet/minecraft/item/ItemStack;)Z")
                ||repair&&(method.name.equals("updateRepairOutput")||method.name.equals("func_82848_d"))&&method.desc.equals("()V")
                ||(nativeCrafting||nativeAnvil)&&(method.name.equals("onCraftMatrixChanged")||method.name.equals("func_75130_a"))&&method.desc.equals("(Lnet/minecraft/inventory/IInventory;)V");
            if(!target)continue;
            for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals("kome/common/data/KOMEQuestPropertyUseGuard"))return bytes;
            boolean container=repair||nativeCrafting||nativeAnvil;
            String helper=nativeCrafting?"boundNativeCrafting":nativeAnvil?"boundNativeAnvil":repair?"boundRepair":crafting?"boundRecipe":"bound";
            String parameter=nativeCrafting?"lotr/common/inventory/LOTRContainerCraftingTable":nativeAnvil?"lotr/common/inventory/LOTRContainerAnvil":repair?"net/minecraft/inventory/ContainerRepair":crafting?"net/minecraft/inventory/InventoryCrafting":"net/minecraft/item/ItemStack";
            InsnList guard=new InsnList();LabelNode ordinary=new LabelNode();guard.add(new VarInsnNode(Opcodes.ALOAD,container?0:1));
            guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/common/data/KOMEQuestPropertyUseGuard",helper,"(L"+parameter+";)Z",false));
            guard.add(new JumpInsnNode(Opcodes.IFEQ,ordinary));if(!container)guard.add(new InsnNode(trading?Opcodes.ICONST_0:Opcodes.ACONST_NULL));
            guard.add(new InsnNode(container?Opcodes.RETURN:trading?Opcodes.IRETURN:Opcodes.ARETURN));guard.add(ordinary);guard.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));method.instructions.insert(guard);count++;
        }
        if(count!=1)throw new IllegalStateException("Quest property input fingerprint changed: "+transformedName+" "+count);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
