package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Vetoes only natural LOTR NPC despawn while canonical KOME roles are active. */
public final class KOMEProgressionNpcDespawnTransformer implements IClassTransformer {
    static final String TARGET="lotr.common.entity.npc.LOTREntityNPC";
    static final String BRIDGE="kome/common/data/KOMEProgressionNpcRoles";
    @Override public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null||!TARGET.equals(transformedName))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        for(MethodNode method:node.methods)if("canDespawn".equals(method.name)&&"()Z".equals(method.desc)){
            for(AbstractInsnNode i=method.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode&&BRIDGE.equals(((MethodInsnNode)i).owner))return bytes;
            InsnList hook=new InsnList();LabelNode nativePath=new LabelNode();
            hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"preventDespawn","(Llotr/common/entity/npc/LOTREntityNPC;)Z",false));
            hook.add(new JumpInsnNode(Opcodes.IFEQ,nativePath));hook.add(new InsnNode(Opcodes.ICONST_0));hook.add(new InsnNode(Opcodes.IRETURN));hook.add(nativePath);
            method.instructions.insert(hook);ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
        }
        throw new IllegalStateException("LOTR v36.15 NPC canDespawn fingerprint changed");
    }
}
