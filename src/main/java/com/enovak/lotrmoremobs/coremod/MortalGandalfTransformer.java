package com.enovak.lotrmoremobs.coremod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Replaces only Gandalf's forced zero damage value with a config-aware
 * selector. The original DamageSource and the original superclass call are
 * left untouched.
 */
public final class MortalGandalfTransformer
        implements IClassTransformer {
    private static final String GANDALF_CLASS =
            "lotr.common.entity.npc.LOTREntityGandalf";
    private static final String DAMAGE_HOOK =
            "com/enovak/lotrmoremobs/compat/"
                    + "MortalGandalfDamageHook";

    @Override
    public byte[] transform(
            String name,
            String transformedName,
            byte[] basicClass
    ) {
        if (basicClass == null
                || (!GANDALF_CLASS.equals(name)
                && !GANDALF_CLASS.equals(transformedName))) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        List<DamageAssignment> candidates =
                new ArrayList<DamageAssignment>();

        for (MethodNode method : classNode.methods) {
            if (!isDamageMethodDescriptor(method.desc)) {
                continue;
            }
            findForcedZeroAssignments(method, candidates);
        }

        if (candidates.size() != 1) {
            System.err.println(
                    "[LOTRMoreMobs] Mortal Gandalf compatibility could not "
                            + "identify LOTREntityGandalf's unique forced-"
                            + "zero damage assignment; found "
                            + candidates.size()
                            + ". Standard LOTR immortality is unchanged."
            );
            return basicClass;
        }

        DamageAssignment assignment = candidates.get(0);
        InsnList replacement = new InsnList();
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
        replacement.add(new VarInsnNode(Opcodes.FLOAD, 2));
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                DAMAGE_HOOK,
                "selectDamage",
                "(Ljava/lang/Object;F)F",
                false
        ));
        assignment.method.instructions.insertBefore(
                assignment.zeroInstruction,
                replacement
        );
        assignment.method.instructions.remove(
                assignment.zeroInstruction
        );

        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_MAXS
        );
        classNode.accept(writer);
        System.out.println(
                "[LOTRMoreMobs] Installed Mortal Gandalf damage "
                        + "compatibility in "
                        + assignment.method.name
                        + assignment.method.desc
        );
        return writer.toByteArray();
    }

    private static boolean isDamageMethodDescriptor(
            String descriptor
    ) {
        Type[] arguments = Type.getArgumentTypes(descriptor);
        return arguments.length == 2
                && arguments[0].getSort() == Type.OBJECT
                && arguments[1].getSort() == Type.FLOAT
                && Type.getReturnType(descriptor).getSort()
                == Type.BOOLEAN;
    }

    private static void findForcedZeroAssignments(
            MethodNode method,
            List<DamageAssignment> candidates
    ) {
        for (AbstractInsnNode instruction =
                method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.FCONST_0) {
                continue;
            }

            AbstractInsnNode next = nextRealInstruction(
                    instruction.getNext()
            );
            if (next instanceof VarInsnNode
                    && next.getOpcode() == Opcodes.FSTORE
                    && ((VarInsnNode)next).var == 2
                    && callsSuperclassDamageMethod(
                    method,
                    method.desc
            )) {
                candidates.add(
                        new DamageAssignment(
                                method,
                                instruction
                        )
                );
            }
        }
    }

    private static boolean callsSuperclassDamageMethod(
            MethodNode method,
            String descriptor
    ) {
        for (AbstractInsnNode instruction =
                method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call =
                        (MethodInsnNode)instruction;
                if (call.getOpcode() == Opcodes.INVOKESPECIAL
                        && descriptor.equals(call.desc)
                        && call.owner.endsWith(
                        "/LOTREntityNPC"
                )) {
                    return true;
                }
            }
        }
        return false;
    }

    private static AbstractInsnNode nextRealInstruction(
            AbstractInsnNode instruction
    ) {
        while (instruction != null
                && instruction.getOpcode() < 0) {
            instruction = instruction.getNext();
        }
        return instruction;
    }

    private static final class DamageAssignment {
        private final MethodNode method;
        private final AbstractInsnNode zeroInstruction;

        private DamageAssignment(
                MethodNode method,
                AbstractInsnNode zeroInstruction
        ) {
            this.method = method;
            this.zeroInstruction = zeroInstruction;
        }
    }
}
