package com.fuzs.aquaacrobatics.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.FrameNode;

public final class AquaServerPlayerTransformer implements IClassTransformer {

    private static final String ENTITY_PLAYER_MP = "net.minecraft.entity.player.EntityPlayerMP";
    private static final String LIFECYCLE = "com/fuzs/aquaacrobatics/entity/player/AquaPlayerLifecycleLogic";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENTITY_PLAYER_MP.equals(transformedName)) return basicClass;
        final ClassNode classNode = AquaAsmMappings.read("AquaServerPlayerTransformer", transformedName, basicClass);
        return AquaAsmMappings.finish("AquaServerPlayerTransformer", classNode, () -> {
            addEyeHeightBridges(classNode);
            addResizeRepair(classNode);
            MethodNode death = AquaAsmMappings.method("AquaServerPlayerTransformer", classNode,
                "(L" + AquaAsmMappings.type(classNode.name, "net/minecraft/util/DamageSource", "ro") + ";)V",
                "onDeath", "func_70645_a", "a");
            int returns = 0;
            for (AbstractInsnNode instruction = death.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
                if (instruction.getOpcode() != Opcodes.RETURN) continue;
                InsnList bridge = new InsnList();
                bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
                bridge.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LIFECYCLE, "onDeath",
                    "(L" + classNode.name + ";)V", false));
                death.instructions.insertBefore(instruction, bridge);
                returns++;
            }
            if (returns == 0) throw new IllegalStateException("Missing EntityPlayerMP onDeath return");
        });
    }

    private void addResizeRepair(ClassNode classNode) {
        MethodNode update = findExact(classNode, "h", "()V", "onUpdate", "func_70071_h_");
        int returns = 0;
        String width = AquaAsmMappings.member(classNode.name, "width", "field_70130_N", "M");
        String height = AquaAsmMappings.member(classNode.name, "height", "field_70131_O", "N");
        String entityOwner = AquaAsmMappings.type(classNode.name, "net/minecraft/entity/Entity", "sa");
        String setSize = AquaAsmMappings.member(classNode.name, "setSize", "func_70105_a", "a");
        for (AbstractInsnNode instruction = update.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.RETURN) continue;
            LabelNode checkHeight = new LabelNode();
            LabelNode apply = new LabelNode();
            LabelNode skip = new LabelNode();
            InsnList tail = new InsnList();
            tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
            tail.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE,
                "com/fuzs/aquaacrobatics/entity/player/IPlayerResizeable", "getPose",
                "()Lcom/fuzs/aquaacrobatics/entity/Pose;", true));
            tail.add(new VarInsnNode(Opcodes.ASTORE, 1));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 1));
            tail.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LIFECYCLE, "resizeSize",
                "(L" + classNode.name + ";Lcom/fuzs/aquaacrobatics/entity/Pose;)Lcom/fuzs/aquaacrobatics/entity/EntitySize;", false));
            tail.add(new VarInsnNode(Opcodes.ASTORE, 2));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
            tail.add(new FieldInsnNode(Opcodes.GETFIELD, entityOwner, width, "F"));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 2));
            tail.add(new FieldInsnNode(Opcodes.GETFIELD, "com/fuzs/aquaacrobatics/entity/EntitySize", "width", "F"));
            tail.add(new InsnNode(Opcodes.FCMPL));
            tail.add(new JumpInsnNode(Opcodes.IFEQ, checkHeight));
            tail.add(new JumpInsnNode(Opcodes.GOTO, apply));
            tail.add(checkHeight);
            tail.add(new FrameNode(
                    Opcodes.F_APPEND,
                    2,
                    new Object[] {
                            "com/fuzs/aquaacrobatics/entity/Pose",
                            "com/fuzs/aquaacrobatics/entity/EntitySize"
                    },
                    0,
                    null));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
            tail.add(new FieldInsnNode(Opcodes.GETFIELD, entityOwner, height, "F"));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 2));
            tail.add(new FieldInsnNode(Opcodes.GETFIELD, "com/fuzs/aquaacrobatics/entity/EntitySize", "height", "F"));
            tail.add(new InsnNode(Opcodes.FCMPL));
            tail.add(new JumpInsnNode(Opcodes.IFEQ, skip));
            tail.add(apply);
            tail.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 2));
            tail.add(new FieldInsnNode(Opcodes.GETFIELD, "com/fuzs/aquaacrobatics/entity/EntitySize", "width", "F"));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 2));
            tail.add(new FieldInsnNode(Opcodes.GETFIELD, "com/fuzs/aquaacrobatics/entity/EntitySize", "height", "F"));
            tail.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, entityOwner, setSize, "(FF)V", false));
            tail.add(skip);
            tail.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
            update.instructions.insertBefore(instruction, tail);
            returns++;
        }
        if (returns != 1) throw new IllegalStateException("Expected one EntityPlayerMP onUpdate return, found " + returns);
    }

    private void addEyeHeightBridges(ClassNode classNode) {
        MethodNode defaultEye = findExact(classNode, "getDefaultEyeHeight", "()F");
        defaultEye.instructions.clear();
        defaultEye.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        defaultEye.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LIFECYCLE, "defaultEyeHeight",
            "(L" + classNode.name + ";)F", false));
        defaultEye.instructions.add(new InsnNode(Opcodes.FRETURN));
        MethodNode eye = findExact(classNode, "g", "()F", "getEyeHeight", "func_70047_e");
        LabelNode original = new LabelNode();
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LIFECYCLE, "hasSwimmingEyeHeight",
            "(L" + classNode.name + ";)Z", false));
        head.add(new JumpInsnNode(Opcodes.IFEQ, original));
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LIFECYCLE, "swimmingEyeHeight",
            "(L" + classNode.name + ";)F", false));
        head.add(new InsnNode(Opcodes.FRETURN));
        head.add(original);
        head.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        eye.instructions.insert(head);
    }

    private MethodNode findExact(ClassNode classNode, String name, String descriptor, String... alternatives) {
        String[] names = new String[alternatives.length + 1];
        names[0] = name;
        System.arraycopy(alternatives, 0, names, 1, alternatives.length);
        return AquaAsmMappings.method("AquaServerPlayerTransformer", classNode, descriptor, names);
    }
}
