package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Modifies only the world-name string after Forge's native visibility event. */
public final class KOMEPlayerNameplateTransformer implements IClassTransformer {
    public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null||!"net.minecraft.client.renderer.entity.RendererLivingEntity".equals(transformedName))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(MethodNode m:node.methods)for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals("kome/client/KOMEPlayerNameplate"))return bytes;
        int hooks=0;
        for(MethodNode m:node.methods)if(m.name.equals("passSpecialRender")||m.name.equals("func_77033_b")){
            for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;
                if((call.name.equals("getFormattedText")||call.name.equals("func_150254_d"))&&call.desc.equals("()Ljava/lang/String;")){
                    InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,1));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/client/KOMEPlayerNameplate","format","(Ljava/lang/String;Lnet/minecraft/entity/EntityLivingBase;)Ljava/lang/String;",false));m.instructions.insert(i,hook);hooks++;
                }
            }
        }
        if(hooks!=1)throw new IllegalStateException("Native player nameplate hook count: "+hooks);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
