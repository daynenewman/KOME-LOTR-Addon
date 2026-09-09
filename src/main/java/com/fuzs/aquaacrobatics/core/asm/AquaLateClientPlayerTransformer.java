package com.fuzs.aquaacrobatics.core.asm;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

@IFMLLoadingPlugin.SortingIndex(1100)
public final class AquaLateClientPlayerTransformer implements IClassTransformer {

    private static final String ENTITY_PLAYER_SP = "net.minecraft.client.entity.EntityPlayerSP";
    private static final String POLICY =
        "com/fuzs/aquaacrobatics/client/entity/AquaClientPlayerMovementPolicy";
    private static final String STORAGE_ACCESS =
        "com/fuzs/aquaacrobatics/client/entity/IAquaClientPlayerMovementStorageAccess";
    private static final String STORAGE = "com/fuzs/aquaacrobatics/util/MovementInputStorage";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENTITY_PLAYER_SP.equals(transformedName)) return basicClass;
        if (basicClass == null) throw new IllegalStateException("Missing EntityPlayerSP bytecode");
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        addLivingUpdateHead(classNode);
        addPreTravelSprintSuppression(classNode);
        addLivingUpdateTail(classNode);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private void addLivingUpdateTail(ClassNode classNode) {
        MethodNode living = findLivingUpdate(classNode);
        int returns = 0;
        for (AbstractInsnNode instruction = living.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.RETURN) continue;
            InsnList bridge = new InsnList();
            bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
            bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
            bridge.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, STORAGE_ACCESS, "aqua$getMovementStorage",
                "()L" + STORAGE + ";", true));
            bridge.add(new MethodInsnNode(Opcodes.INVOKESTATIC, POLICY, "applyLivingUpdateTail",
                "(L" + classNode.name + ";L" + STORAGE + ";)V", false));
            living.instructions.insertBefore(instruction, bridge);
            returns++;
        }
        if (returns != 1) throw new IllegalStateException("Expected one EntityPlayerSP onLivingUpdate return, found " + returns);
    }

    private void addLivingUpdateHead(ClassNode classNode) {
        MethodNode living = findLivingUpdate(classNode);
        AbstractInsnNode first = living.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        if (first == null) throw new IllegalStateException("Empty EntityPlayerSP onLivingUpdate");
        InsnList bridge = new InsnList();
        bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, STORAGE_ACCESS, "aqua$getMovementStorage",
            "()L" + STORAGE + ";", true));
        bridge.add(new MethodInsnNode(Opcodes.INVOKESTATIC, POLICY, "captureLivingUpdateHead",
            "(L" + classNode.name + ";L" + STORAGE + ";)V", false));
        living.instructions.insertBefore(first, bridge);
    }

    private void addPreTravelSprintSuppression(ClassNode classNode) {
        MethodNode living = findLivingUpdate(classNode);
        MethodInsnNode superCall = null;
        for (AbstractInsnNode instruction = living.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if ("()V".equals(call.desc) && ("onLivingUpdate".equals(call.name)
                || "func_70636_d".equals(call.name) || "e".equals(call.name))
                && !classNode.name.equals(call.owner)) {
                if (superCall != null) {
                    throw new IllegalStateException("Ambiguous late EntityPlayerSP onLivingUpdate call");
                }
                superCall = call;
            }
        }
        if (superCall == null) throw new IllegalStateException("Missing late EntityPlayerSP super call");
        AbstractInsnNode receiver = previousMeaningful(superCall);
        if (!(receiver instanceof VarInsnNode) || receiver.getOpcode() != Opcodes.ALOAD
            || ((VarInsnNode) receiver).var != 0) {
            throw new IllegalStateException("Late EntityPlayerSP super call is not preceded by ALOAD 0");
        }
        InsnList bridge = new InsnList();
        bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.add(new MethodInsnNode(Opcodes.INVOKESTATIC, POLICY, "suppressForcedLandCrawlSprint",
            "(L" + classNode.name + ";)V", false));
        living.instructions.insertBefore(receiver, bridge);
    }

    private MethodNode findLivingUpdate(ClassNode classNode) {
        MethodNode result = null;
        for (MethodNode method : classNode.methods) {
            if (!"()V".equals(method.desc) || !("onLivingUpdate".equals(method.name)
                || "func_70636_d".equals(method.name) || "e".equals(method.name))) continue;
            if (result != null) throw new IllegalStateException("Ambiguous EntityPlayerSP onLivingUpdate");
            result = method;
        }
        if (result == null) throw new IllegalStateException("Missing EntityPlayerSP onLivingUpdate");
        return result;
    }

    private AbstractInsnNode previousMeaningful(AbstractInsnNode instruction) {
        AbstractInsnNode previous = instruction.getPrevious();
        while (previous != null && previous.getOpcode() < 0) previous = previous.getPrevious();
        return previous;
    }
}
