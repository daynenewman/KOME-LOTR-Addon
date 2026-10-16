package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Preserve the shipped theft algorithm; adapt its interface only inside an authenticated attempt. */
public final class KOMEKnightPickpocketTransformer implements IClassTransformer {
    private static final String BRIDGE="kome/common/data/KOMEKnightEspionage";
    @Override public byte[] transform(String name,String transformedName,byte[] bytes){
        if(bytes!=null&&"lotr.common.LOTREventHandler".equals(transformedName))return deathRewards(bytes);
        if(bytes!=null&&transformedName!=null&&transformedName.startsWith("lotr.common.entity.npc."))return rewards(bytes);
        if(bytes==null||!"lotr.common.quest.LOTRMiniQuestPickpocket".equals(transformedName))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int sites=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&BRIDGE.equals(((MethodInsnNode)i).owner))return bytes;
        for(MethodNode method:node.methods)if("onInteractOther".equals(method.name))for(AbstractInsnNode i:method.instructions.toArray()){
            if(i instanceof TypeInsnNode&&"lotr/common/quest/IPickpocketable".equals(((TypeInsnNode)i).desc)){
                String hook=i.getOpcode()==Opcodes.INSTANCEOF?"pickpocketable":"adapter";
                String result=i.getOpcode()==Opcodes.INSTANCEOF?"Z":"Llotr/common/quest/IPickpocketable;";
                method.instructions.set(i,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,hook,"(Llotr/common/entity/npc/LOTREntityNPC;)"+result,false));sites++;
            }else if(i instanceof MethodInsnNode&&"lotr/common/LOTRPlayerData".equals(((MethodInsnNode)i).owner)&&"addAchievement".equals(((MethodInsnNode)i).name)){
                method.instructions.set(i,new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"achievement","(Llotr/common/LOTRPlayerData;Llotr/common/LOTRAchievement;)V",false));sites++;
            }
        }
        if(sites!=3)throw new IllegalStateException("LOTR v36.15 pickpocket sites changed: "+sites);
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private byte[] rewards(byte[] bytes){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int hooks=0;
        for(MethodNode m:node.methods)for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode&&"kome/common/data/KOMEProgressionDevelopment".equals(((MethodInsnNode)i).owner))return bytes;
        for(MethodNode m:node.methods){String hook=null,desc=null;int opcode=0;
            if(node.name.equals("lotr/common/entity/npc/LOTREntityNPC")&&(m.name.equals("onDeath")||m.name.equals("func_70645_a")))for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;
                if(call.owner.equals("lotr/common/LOTRPlayerData")&&call.name.equals("addAchievement")&&call.desc.equals("(Llotr/common/LOTRAchievement;)V")){
                    m.instructions.insertBefore(i,new VarInsnNode(Opcodes.ALOAD,0));m.instructions.set(i,new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/common/data/KOMEProgressionDevelopment","nativeAchievement","(Llotr/common/LOTRPlayerData;Llotr/common/LOTRAchievement;Llotr/common/entity/npc/LOTREntityNPC;)V",false));hooks++;
                }
            }
            if((m.name.equals("getExperiencePoints")||m.name.equals("func_70693_a"))&&m.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;)I")){hook="experience";desc="(ILlotr/common/entity/npc/LOTREntityNPC;)I";opcode=Opcodes.IRETURN;}
            if(m.name.equals("getAlignmentBonus")&&m.desc.equals("()F")){hook="alignment";desc="(FLlotr/common/entity/npc/LOTREntityNPC;)F";opcode=Opcodes.FRETURN;}
            if(m.name.equals("getKillAchievement")&&m.desc.equals("()Llotr/common/LOTRAchievement;")){hook="killAchievement";desc="(Llotr/common/LOTRAchievement;Llotr/common/entity/npc/LOTREntityNPC;)Llotr/common/LOTRAchievement;";opcode=Opcodes.ARETURN;}
            if(hook==null)continue;
            for(AbstractInsnNode i:m.instructions.toArray())if(i.getOpcode()==opcode){InsnList code=new InsnList();code.add(new VarInsnNode(Opcodes.ALOAD,0));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/common/data/KOMEProgressionDevelopment",hook,desc,false));m.instructions.insertBefore(i,code);hooks++;}
        }
        if(hooks==0)return bytes;ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private byte[] deathRewards(byte[] bytes){
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean found=false;
        for(MethodNode m:node.methods)if(m.name.equals("onLivingDeath")&&m.desc.equals("(Lnet/minecraftforge/event/entity/living/LivingDeathEvent;)V")){
            for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals("kome/common/data/KOMEProgressionDevelopment"))return bytes;
            InsnList code=new InsnList();LabelNode normal=new LabelNode();code.add(new VarInsnNode(Opcodes.ALOAD,1));code.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/common/data/KOMEProgressionDevelopment","testDeath","(Lnet/minecraftforge/event/entity/living/LivingDeathEvent;)Z",false));code.add(new JumpInsnNode(Opcodes.IFEQ,normal));code.add(new InsnNode(Opcodes.RETURN));code.add(normal);code.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));m.instructions.insert(code);found=true;
        }
        if(!found)throw new IllegalStateException("LOTR v36.15 reward observer changed");ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
