package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Only main-model rendering; preserves native visibility, frustum, animation and armor order. */
public final class KOMEProgressionOutlineTransformer implements IClassTransformer {
    public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null||!"net.minecraft.client.renderer.entity.RendererLivingEntity".equals(transformedName))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int hooks=0;
        for(MethodNode m:node.methods)if(m.name.equals("renderModel")||m.name.equals("func_77036_a"))for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;
            if(call.owner.equals("kome/client/KOMEProgressionEnemyOutline"))return bytes;
            if((call.name.equals("render")||call.name.equals("func_78088_a"))&&call.desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V")){
                call.setOpcode(Opcodes.INVOKESTATIC);call.owner="kome/client/KOMEProgressionEnemyOutline";call.name="render";call.desc="(Lnet/minecraft/client/model/ModelBase;Lnet/minecraft/entity/Entity;FFFFFF)V";call.itf=false;hooks++;
            }
        }
        if(hooks!=2)throw new IllegalStateException("Native outline model hook count: "+hooks);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
