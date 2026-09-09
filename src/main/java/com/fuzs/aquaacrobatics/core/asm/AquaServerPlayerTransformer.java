package com.fuzs.aquaacrobatics.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
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

public final class AquaServerPlayerTransformer implements IClassTransformer {

    private static final String ENTITY_PLAYER_MP = "net.minecraft.entity.player.EntityPlayerMP";
    private static final String LIFECYCLE = "com/fuzs/aquaacrobatics/entity/player/AquaPlayerLifecycleLogic";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENTITY_PLAYER_MP.equals(transformedName)) return basicClass;
        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        addEyeHeightBridges(classNode);
        addResizeRepair(classNode);
        MethodNode death = findMethod(classNode, "onDeath", "func_70645_a", "a");
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
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private void addResizeRepair(ClassNode classNode) {
        MethodNode update = findExact(classNode, "h", "()V", "onUpdate", "func_70071_h_");
        int returns = 0;
        String width = "mw".equals(classNode.name) ? "field_70130_N" : "width";
        String height = "mw".equals(classNode.name) ? "field_70131_O" : "height";
        String entityOwner = "mw".equals(classNode.name) ? "yz" : "net/minecraft/entity/Entity";
        String setSize = "mw".equals(classNode.name) ? "a" : "setSize";
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
            tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
            tail.add(new FieldInsnNode(Opcodes.GETFIELD, entityOwner, height, "F"));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 2));
            tail.add(new FieldInsnNode(Opcodes.GETFIELD, "com/fuzs/aquaacrobatics/entity/EntitySize", "height", "F"));
            tail.add(new InsnNode(Opcodes.FCMPL));
            tail.add(new JumpInsnNode(Opcodes.IFEQ, skip));
            tail.add(apply);
            tail.add(new VarInsnNode(Opcodes.ALOAD, 0));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 2));
            tail.add(new FieldInsnNode(Opcodes.GETFIELD, "com/fuzs/aquaacrobatics/entity/EntitySize", "width", "F"));
            tail.add(new VarInsnNode(Opcodes.ALOAD, 2));
            tail.add(new FieldInsnNode(Opcodes.GETFIELD, "com/fuzs/aquaacrobatics/entity/EntitySize", "height", "F"));
            tail.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, entityOwner, setSize, "(FF)V", false));
            tail.add(skip);
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
        eye.instructions.insert(head);
    }

    private MethodNode findExact(ClassNode classNode, String name, String descriptor, String... alternatives) {
        MethodNode result = null;
        for (MethodNode method : classNode.methods) {
            if (!descriptor.equals(method.desc)) continue;
            boolean match = name.equals(method.name);
            for (String alternative : alternatives) match |= alternative.equals(method.name);
            if (match) {
                if (result != null) throw new IllegalStateException("Ambiguous EntityPlayerMP eye-height method");
                result = method;
            }
        }
        if (result == null) throw new IllegalStateException("Missing EntityPlayerMP eye-height method");
        return result;
    }

    private MethodNode findMethod(ClassNode classNode, String... names) {
        MethodNode result = null;
        for (MethodNode method : classNode.methods) {
            if (!method.desc.startsWith("(L") || !method.desc.endsWith(")V")) continue;
            for (String name : names) if (name.equals(method.name)
                && (!"a".equals(name) || "(Lro;)V".equals(method.desc))) {
                if (result != null) throw new IllegalStateException("Ambiguous EntityPlayerMP onDeath");
                result = method;
            }
        }
        if (result == null) throw new IllegalStateException("Missing EntityPlayerMP onDeath");
        return result;
    }
}
