package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Observe the actual native outcome; despawn/removal must never masquerade as victory. */
public final class KOMEObjectiveInvasionTransformer implements IClassTransformer {
    public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null||!"lotr.common.entity.LOTREntityInvasionSpawner".equals(transformedName))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean found=false;
        for(MethodNode method:node.methods)if(method.name.equals("endInvasion")&&method.desc.equals("(Z)V")){
            for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals("kome/common/data/KOMEObjectiveInvasionBridge"))return bytes;
            InsnList hook=new InsnList();LabelNode normal=new LabelNode();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ILOAD,1));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/common/data/KOMEObjectiveInvasionBridge","endTest","(Llotr/common/entity/LOTREntityInvasionSpawner;Z)Z",false));
            hook.add(new JumpInsnNode(Opcodes.IFEQ,normal));hook.add(new InsnNode(Opcodes.RETURN));hook.add(normal);hook.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
            hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new VarInsnNode(Opcodes.ILOAD,1));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/common/data/KOMEObjectiveInvasionBridge","ended","(Llotr/common/entity/LOTREntityInvasionSpawner;Z)V",false));
            method.instructions.insert(hook);found=true;
        }
        if(!found)throw new IllegalStateException("LOTR v36.15 native invasion outcome hook missing");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
