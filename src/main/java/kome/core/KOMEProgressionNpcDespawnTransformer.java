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
        // The development class is MCP named; the shipped LOTR jar uses the SRG name.
        for(MethodNode method:node.methods)if(("canDespawn".equals(method.name)||"func_70692_ba".equals(method.name))&&"()Z".equals(method.desc)){
            for(AbstractInsnNode i=method.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode&&BRIDGE.equals(((MethodInsnNode)i).owner))return bytes;
            InsnList hook=new InsnList();LabelNode nativePath=new LabelNode();
            hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"preventDespawn","(Llotr/common/entity/npc/LOTREntityNPC;)Z",false));
            hook.add(new JumpInsnNode(Opcodes.IFEQ,nativePath));hook.add(new InsnNode(Opcodes.ICONST_0));hook.add(new InsnNode(Opcodes.IRETURN));hook.add(nativePath);
            method.instructions.insert(hook);for(MethodNode spawn:node.methods)if(("onSpawnWithEgg".equals(spawn.name)||"func_110161_a".equals(spawn.name))){
                for(AbstractInsnNode insn=spawn.instructions.getFirst();insn!=null;insn=insn.getNext())if(insn.getOpcode()==Opcodes.ARETURN){InsnList rankHook=new InsnList();rankHook.add(new VarInsnNode(Opcodes.ALOAD,0));rankHook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/common/data/KOMEProgressionNpcRankService","assignNatural","(Llotr/common/entity/npc/LOTREntityNPC;)V",false));spawn.instructions.insertBefore(insn,rankHook);}}
            for(MethodNode load:node.methods)if(("readEntityFromNBT".equals(load.name)||"func_70037_a".equals(load.name))&&"(Lnet/minecraft/nbt/NBTTagCompound;)V".equals(load.desc))
                for(AbstractInsnNode insn=load.instructions.getFirst();insn!=null;insn=insn.getNext())if(insn.getOpcode()==Opcodes.RETURN){InsnList migration=new InsnList();migration.add(new VarInsnNode(Opcodes.ALOAD,0));migration.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/common/data/KOMEProgressionNpcRankService","migrateFromSave","(Llotr/common/entity/npc/LOTREntityNPC;)V",false));load.instructions.insertBefore(insn,migration);}
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
        }
        throw new IllegalStateException("LOTR v36.15 NPC canDespawn/func_70692_ba fingerprint changed");
    }
}
