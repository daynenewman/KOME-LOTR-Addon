package com.enovak.lotrmoremobs.coremod;

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

/**
 * Filters collision-query results only when a projectile asks the world for
 * nearby entities. This keeps LOTR marker selection and administrative
 * interaction intact while making the exact marker classes projectile-
 * transparent on both logical sides.
 */
public final class RespawnMarkerProjectileCollisionTransformer
        implements IClassTransformer {
    private static final String WORLD_CLASS =
            "net.minecraft.world.World";
    private static final String TARGET_METHOD_MCP =
            "getEntitiesWithinAABBExcludingEntity";
    private static final String TARGET_METHOD_SRG =
            "func_94576_a";
    private static final String TARGET_METHOD_NOTCH = "a";
    private static final String TARGET_DESCRIPTOR_DEOBFUSCATED =
            "(Lnet/minecraft/entity/Entity;"
                    + "Lnet/minecraft/util/AxisAlignedBB;"
                    + "Lnet/minecraft/command/IEntitySelector;)"
                    + "Ljava/util/List;";
    private static final String TARGET_DESCRIPTOR_NOTCH =
            "(Lsa;Lazt;Lsj;)Ljava/util/List;";
    private static final String HOOK_OWNER =
            "com/enovak/lotrmoremobs/compat/"
                    + "RespawnMarkerProjectileCollisionHook";
    private static final String HOOK_METHOD =
            "filterProjectileMarkerCollisions";
    private static final String HOOK_DESCRIPTOR_DEOBFUSCATED =
            "(Ljava/util/List;Lnet/minecraft/entity/Entity;)"
                    + "Ljava/util/List;";
    private static final String HOOK_DESCRIPTOR_NOTCH =
            "(Ljava/util/List;Lsa;)Ljava/util/List;";

    @Override
    public byte[] transform(
            String name,
            String transformedName,
            byte[] basicClass
    ) {
        if (basicClass == null
                || (!WORLD_CLASS.equals(name)
                && !WORLD_CLASS.equals(transformedName))) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        MethodNode target = null;

        for (MethodNode method : classNode.methods) {
            if (isTargetMethod(method)) {
                if (target != null) {
                    System.err.println(
                            "[LOTRMoreMobs] Respawn-marker projectile "
                                    + "compatibility found multiple target "
                                    + "World methods; collision behavior is "
                                    + "unchanged."
                    );
                    return basicClass;
                }
                target = method;
            }
        }

        String hookDescriptor = target == null
                ? null
                : getHookDescriptor(target);
        if (target == null
                || containsHookCall(target, hookDescriptor)) {
            if (target == null) {
                System.err.println(
                        "[LOTRMoreMobs] Respawn-marker projectile "
                                + "compatibility could not find World's "
                                + "base entity query; collision "
                                + "behavior is unchanged."
                );
            }
            return basicClass;
        }

        int returnCount = 0;
        AbstractInsnNode returnInstruction = null;
        for (AbstractInsnNode instruction =
                     target.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction.getOpcode() == Opcodes.ARETURN) {
                ++returnCount;
                returnInstruction = instruction;
            }
        }

        if (returnCount != 1) {
            System.err.println(
                    "[LOTRMoreMobs] Respawn-marker projectile "
                            + "compatibility expected one World query return "
                            + "but found " + returnCount
                            + "; collision behavior is unchanged."
            );
            return basicClass;
        }

        InsnList filterCall = new InsnList();
        filterCall.add(new VarInsnNode(Opcodes.ALOAD, 1));
        filterCall.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOK_OWNER,
                HOOK_METHOD,
                hookDescriptor,
                false
        ));
        target.instructions.insertBefore(
                returnInstruction,
                filterCall
        );

        ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_MAXS
        );
        classNode.accept(writer);
        System.out.println(
                "[LOTRMoreMobs] Installed projectile transparency for "
                        + "LOTR respawn markers in "
                        + target.name + target.desc
        );
        return writer.toByteArray();
    }

    private static boolean isTargetMethod(MethodNode method) {
        boolean deobfuscatedTarget =
                TARGET_DESCRIPTOR_DEOBFUSCATED.equals(method.desc)
                        && (TARGET_METHOD_MCP.equals(method.name)
                || TARGET_METHOD_SRG.equals(method.name));
        boolean notchTarget = TARGET_DESCRIPTOR_NOTCH.equals(method.desc)
                && TARGET_METHOD_NOTCH.equals(method.name);
        return deobfuscatedTarget || notchTarget;
    }

    private static String getHookDescriptor(MethodNode method) {
        return TARGET_DESCRIPTOR_NOTCH.equals(method.desc)
                ? HOOK_DESCRIPTOR_NOTCH
                : HOOK_DESCRIPTOR_DEOBFUSCATED;
    }

    private static boolean containsHookCall(
            MethodNode method,
            String hookDescriptor
    ) {
        for (AbstractInsnNode instruction =
                     method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (HOOK_OWNER.equals(call.owner)
                        && HOOK_METHOD.equals(call.name)
                        && hookDescriptor.equals(call.desc)) {
                    return true;
                }
            }
        }
        return false;
    }
}
