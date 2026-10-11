package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Two narrow native adapters; no replacement rendering or follower implementation. */
public final class KOMEProgressionNativeInteractionTransformer implements IClassTransformer {
    @Override public byte[] transform(String name,String transformedName,byte[] bytes){
        boolean npc="lotr.common.entity.npc.LOTREntityNPC".equals(transformedName);
        boolean hired="lotr.common.entity.npc.LOTRHiredNPCInfo".equals(transformedName);
        if(bytes==null||!npc&&!hired)return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int hooks=0;
        for(MethodNode m:node.methods){
            if(npc&&"getNPCFormattedName".equals(m.name)&&"(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;".equals(m.desc)){
                for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode&&"nativeOccupation".equals(((MethodInsnNode)i).name))return bytes;
                InsnList h=new InsnList();h.add(new VarInsnNode(Opcodes.ALOAD,0));h.add(new VarInsnNode(Opcodes.ALOAD,1));h.add(new VarInsnNode(Opcodes.ALOAD,2));
                h.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/common/data/KOMEProgressionNativeAuthority","nativeOccupation","(Llotr/common/entity/npc/LOTREntityNPC;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;",false));
                h.add(new VarInsnNode(Opcodes.ASTORE,2));m.instructions.insert(h);hooks++;
            }
            if(hired&&"dismissUnit".equals(m.name)&&"(Z)V".equals(m.desc))for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){
                MethodInsnNode call=(MethodInsnNode)i;
                if("kome/common/data/KOMEProgressionEscortFollowing".equals(call.owner))return bytes;
                if("net/minecraft/entity/player/EntityPlayer".equals(call.owner)&&("addChatMessage".equals(call.name)||"func_145747_a".equals(call.name))){
                    call.setOpcode(Opcodes.INVOKESTATIC);call.owner="kome/common/data/KOMEProgressionEscortFollowing";call.name="nativeDismissalMessage";
                    call.desc="(Lnet/minecraft/entity/player/EntityPlayer;Lnet/minecraft/util/IChatComponent;)V";hooks++;
                }
            }
        }
        if(hooks!=(npc?1:2))throw new IllegalStateException("LOTR v36.15 native interaction fingerprint changed: "+transformedName);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
