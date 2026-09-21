package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Injects three small delegating checks into LOTR quest info; vanilla generation stays untouched. */
public final class KOMEProgressionOfferTransformer implements IClassTransformer {
    static final String TARGET = "lotr.common.entity.npc.LOTREntityQuestInfo";
    static final String OWNER = "kome/common/data/KOMEProgressionOfferBridge";
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try { return patch(bytes); } catch (RuntimeException e) { throw new IllegalStateException("Incompatible LOTREntityQuestInfo progression offer bridge", e); }
    }
    byte[] patch(byte[] bytes) {
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        boolean offer = false, interact = false, response = false;
        for (MethodNode method : node.methods) {
            if ("canOfferQuestsTo".equals(method.name) && "(Lnet/minecraft/entity/player/EntityPlayer;)Z".equals(method.desc)) { injectCanOffer(method); offer = true; }
            if ("interact".equals(method.name) && "(Lnet/minecraft/entity/player/EntityPlayer;)Z".equals(method.desc)) { injectInteraction(method); interact = true; }
            if ("receiveOfferResponse".equals(method.name) && "(Lnet/minecraft/entity/player/EntityPlayer;Z)V".equals(method.desc)) { injectResponse(method); response = true; }
        }
        if (!offer || !interact || !response) throw new IllegalStateException("required v36.15 offer methods were not found");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private void injectCanOffer(MethodNode method) {
        if (has(method, "canOffer")) return;
        LabelNode vanilla = new LabelNode(); InsnList i = new InsnList();
        i.add(new VarInsnNode(Opcodes.ALOAD, 0)); i.add(new VarInsnNode(Opcodes.ALOAD, 1));
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "canOffer", "(Llotr/common/entity/npc/LOTREntityQuestInfo;Lnet/minecraft/entity/player/EntityPlayer;)Z", false));
        i.add(new JumpInsnNode(Opcodes.IFEQ, vanilla)); i.add(new InsnNode(Opcodes.ICONST_1)); i.add(new InsnNode(Opcodes.IRETURN)); i.add(vanilla); method.instructions.insert(i);
    }
    private void injectInteraction(MethodNode method) {
        if (has(method, "handleInteraction")) return;
        LabelNode vanilla = new LabelNode(); InsnList i = new InsnList();
        i.add(new VarInsnNode(Opcodes.ALOAD, 0)); i.add(new VarInsnNode(Opcodes.ALOAD, 1));
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "handleInteraction", "(Llotr/common/entity/npc/LOTREntityQuestInfo;Lnet/minecraft/entity/player/EntityPlayer;)Z", false));
        i.add(new JumpInsnNode(Opcodes.IFEQ, vanilla)); i.add(new InsnNode(Opcodes.ICONST_1)); i.add(new InsnNode(Opcodes.IRETURN)); i.add(vanilla); method.instructions.insert(i);
    }
    private void injectResponse(MethodNode method) {
        if (has(method, "handleResponse")) return;
        LabelNode vanilla = new LabelNode(); InsnList i = new InsnList();
        i.add(new VarInsnNode(Opcodes.ALOAD, 0)); i.add(new VarInsnNode(Opcodes.ALOAD, 1)); i.add(new VarInsnNode(Opcodes.ILOAD, 2));
        i.add(new MethodInsnNode(Opcodes.INVOKESTATIC, OWNER, "handleResponse", "(Llotr/common/entity/npc/LOTREntityQuestInfo;Lnet/minecraft/entity/player/EntityPlayer;Z)Z", false));
        i.add(new JumpInsnNode(Opcodes.IFEQ, vanilla)); i.add(new InsnNode(Opcodes.RETURN)); i.add(vanilla); method.instructions.insert(i);
    }
    private boolean has(MethodNode method, String name) { for (org.objectweb.asm.tree.AbstractInsnNode n=method.instructions.getFirst();n!=null;n=n.getNext()) if(n instanceof MethodInsnNode && OWNER.equals(((MethodInsnNode)n).owner) && name.equals(((MethodInsnNode)n).name)) return true; return false; }
}
