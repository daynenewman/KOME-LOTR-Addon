package com.enovak.lotrmoremobs.coremod;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Redirects only EntitySenses' existing virtual sight call through the
 * server-side GatePart AI-sight context helper.
 */
public final class EntitySensesGateSightTransformer
        implements IClassTransformer {

    private static final String ENTITY_SENSES_CLASS =
            "net.minecraft.entity.ai.EntitySenses";
    private static final String ENTITY_SENSES_NOTCH_CLASS = "vw";
    private static final String METHOD_DESCRIPTOR =
            "(Lnet/minecraft/entity/Entity;)Z";
    private static final String METHOD_NOTCH_DESCRIPTOR = "(Lsa;)Z";
    private static final String HOOK_OWNER =
            "com/enovak/lotrmoremobs/siege/gate/"
                    + "SiegeGateNpcSightHelper";
    private static final String HOOK_METHOD = "canEntityBeSeen";
    private static final String HOOK_DESCRIPTOR =
            "(Ljava/lang/Object;Ljava/lang/Object;)Z";

    @Override
    public byte[] transform(
            String name,
            String transformedName,
            byte[] basicClass
    ) {
        if (basicClass == null
                || (!ENTITY_SENSES_CLASS.equals(name)
                && !ENTITY_SENSES_CLASS.equals(transformedName)
                && !ENTITY_SENSES_NOTCH_CLASS.equals(name))) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        MethodNode target = findTargetMethod(classNode);
        if (target == null) {
            System.err.println(
                    "[LOTRMoreMobs] Siege Gate AI sight compatibility "
                            + "could not uniquely identify "
                            + "EntitySenses.canSee; AI sight behavior is "
                            + "unchanged."
            );
            return basicClass;
        }
        if (containsHookCall(target)) {
            return basicClass;
        }

        MethodInsnNode sightCall = findSightCall(target);
        if (sightCall == null) {
            System.err.println(
                    "[LOTRMoreMobs] Siege Gate AI sight compatibility "
                            + "could not uniquely identify EntitySenses' "
                            + "virtual canEntityBeSeen call; AI sight "
                            + "behavior is unchanged."
            );
            return basicClass;
        }

        sightCall.setOpcode(Opcodes.INVOKESTATIC);
        sightCall.owner = HOOK_OWNER;
        sightCall.name = HOOK_METHOD;
        sightCall.desc = HOOK_DESCRIPTOR;
        sightCall.itf = false;

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        System.out.println(
                "[LOTRMoreMobs] Installed generic Siege Gate AI sight "
                        + "context in " + target.name + target.desc
        );
        return writer.toByteArray();
    }

    private static MethodNode findTargetMethod(ClassNode classNode) {
        MethodNode target = null;
        for (MethodNode method : classNode.methods) {
            if (!isTargetMethod(method)) {
                continue;
            }
            if (target != null) {
                return null;
            }
            target = method;
        }
        return target;
    }

    private static boolean isTargetMethod(MethodNode method) {
        boolean deobfuscated = METHOD_DESCRIPTOR.equals(method.desc)
                && ("canSee".equals(method.name)
                || "func_75522_a".equals(method.name));
        return deobfuscated
                || (METHOD_NOTCH_DESCRIPTOR.equals(method.desc)
                && "a".equals(method.name));
    }

    private static MethodInsnNode findSightCall(MethodNode method) {
        MethodInsnNode candidate = null;
        for (AbstractInsnNode instruction =
                method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) {
                continue;
            }
            MethodInsnNode call = (MethodInsnNode)instruction;
            if (!isSightCall(call)) {
                continue;
            }
            if (candidate != null) {
                return null;
            }
            candidate = call;
        }
        return candidate;
    }

    private static boolean isSightCall(MethodInsnNode call) {
        if (call.getOpcode() != Opcodes.INVOKEVIRTUAL
                || !(METHOD_DESCRIPTOR.equals(call.desc)
                || METHOD_NOTCH_DESCRIPTOR.equals(call.desc))) {
            return false;
        }
        boolean owner = "net/minecraft/entity/EntityLiving".equals(call.owner)
                || "sw".equals(call.owner);
        return owner
                && ("canEntityBeSeen".equals(call.name)
                || "func_70685_l".equals(call.name)
                || "p".equals(call.name));
    }

    private static boolean containsHookCall(MethodNode method) {
        for (AbstractInsnNode instruction =
                method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (HOOK_OWNER.equals(call.owner)
                        && HOOK_METHOD.equals(call.name)
                        && HOOK_DESCRIPTOR.equals(call.desc)) {
                    return true;
                }
            }
        }
        return false;
    }
}
