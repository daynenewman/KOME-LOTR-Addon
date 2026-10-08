package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** v36.15's local probability gate; native eligibility, group sizes and caps remain intact. */
public final class KOMEProgressionBanditTransformer implements IClassTransformer {
    static final String TARGET="lotr.common.world.spawning.LOTREventSpawner";
    @Override public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes==null||!TARGET.equals(transformedName))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int sites=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())
            if(insn instanceof MethodInsnNode&&"kome/common/data/KOMEProgressionBanditPressure".equals(((MethodInsnNode)insn).owner))return bytes;
        for(MethodNode method:node.methods){
            if(!"spawnBandits".equals(method.name)||!"(Lnet/minecraft/world/World;Ljava/util/List;)V".equals(method.desc))continue;
            for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext()){
                if(insn.getOpcode()!=Opcodes.DALOAD||!(insn.getNext() instanceof VarInsnNode)||insn.getNext().getOpcode()!=Opcodes.DSTORE
                        ||((VarInsnNode)insn.getNext()).var!=11)continue;
                // Audited spawnBandits locals: world=0, x/z=6/7, regional probability=11.
                InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));
                hook.add(new VarInsnNode(Opcodes.ILOAD,6));hook.add(new VarInsnNode(Opcodes.ILOAD,7));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/common/data/KOMEProgressionBanditPressure","chance",
                    "(DLnet/minecraft/world/World;II)D",false));
                method.instructions.insert(insn,hook);sites++;
            }
        }
        if(sites!=1)throw new IllegalStateException("LOTR v36.15 bandit probability site changed: "+sites);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
