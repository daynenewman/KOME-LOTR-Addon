package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** LOTR's native lookup must use the calling thread's side in an integrated server. */
public final class KOMEAlignmentSyncTransformer implements IClassTransformer {
    @Override public byte[] transform(String name,String transformedName,byte[] bytes) {
        if(bytes==null||!"lotr.common.LOTRPlayerData".equals(transformedName))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
        int changed=0;boolean pledgeAdded=false;
        for(MethodNode method:node.methods)if("setPledgeFaction".equals(method.name)&&"(Llotr/common/fac/LOTRFaction;)V".equals(method.desc)){
            boolean present=false;for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())if(insn instanceof MethodInsnNode&&"kome/common/data/KOMEProgressionPledgeBridge".equals(((MethodInsnNode)insn).owner))present=true;
            if(!present)for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())if(insn.getOpcode()==Opcodes.RETURN){
                pledgeAdded=true;
                InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));
                hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/common/data/KOMEProgressionPledgeBridge","changed","(Llotr/common/LOTRPlayerData;)V",false));method.instructions.insertBefore(insn,hook);
            }
        }
        for(MethodNode method:node.methods)if("getPlayer".equals(method.name))
            for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())
                if(insn instanceof MethodInsnNode&&"getEffectiveSide".equals(((MethodInsnNode)insn).name)){if(!pledgeAdded)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();}
        for(MethodNode method:node.methods)if("getPlayer".equals(method.name))
            for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())
                if(insn instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)insn;
                    if("lotr/common/LOTRCommonProxy".equals(call.owner)&&"isClient".equals(call.name)){
                        InsnList replacement=new InsnList();replacement.add(new InsnNode(Opcodes.POP));
                        replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"cpw/mods/fml/common/FMLCommonHandler","instance","()Lcpw/mods/fml/common/FMLCommonHandler;",false));
                        replacement.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/common/FMLCommonHandler","getEffectiveSide","()Lcpw/mods/fml/relauncher/Side;",false));
                        replacement.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,"cpw/mods/fml/relauncher/Side","isClient","()Z",false));
                        method.instructions.insertBefore(call,replacement);method.instructions.remove(call);changed++;break;
                    }
                }
        if(changed!=1)throw new IllegalStateException("LOTR player lookup fingerprint changed: "+changed);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
