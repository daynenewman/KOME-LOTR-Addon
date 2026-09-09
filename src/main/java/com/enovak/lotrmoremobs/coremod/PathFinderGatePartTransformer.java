package com.enovak.lotrmoremobs.coremod;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Skips only valid nonclosed Siege GateParts in vanilla PathFinder's existing
 * per-node block classification loop.
 */
public final class PathFinderGatePartTransformer
        implements IClassTransformer {

    private static final String PATH_FINDER_CLASS =
            "net.minecraft.pathfinding.PathFinder";
    private static final String PATH_FINDER_NOTCH_CLASS = "ayg";
    private static final String TARGET_DESCRIPTOR =
            "(Lnet/minecraft/entity/Entity;III"
                    + "Lnet/minecraft/pathfinding/PathPoint;ZZZ)I";
    private static final String TARGET_NOTCH_DESCRIPTOR =
            "(Lsa;IIILaye;ZZZ)I";
    private static final String BLOCK_DESCRIPTOR =
            "(III)Lnet/minecraft/block/Block;";
    private static final String BLOCK_NOTCH_DESCRIPTOR = "(III)Laji;";
    private static final String SIEGE_REGISTRY_OWNER =
            "com/enovak/lotrmoremobs/siege/SiegeRegistry";
    private static final String GATE_PART_FIELD = "gatePart";
    private static final String GATE_PART_DESCRIPTOR =
            "Lnet/minecraft/block/Block;";
    private static final String GATE_PART_NOTCH_DESCRIPTOR = "Laji;";
    private static final String HOOK_OWNER =
            "com/enovak/lotrmoremobs/siege/gate/"
                    + "SiegeGateAiPathHelper";
    private static final String HOOK_METHOD =
            "shouldTreatKnownGatePartAsClear";
    private static final String HOOK_DESCRIPTOR =
            "(Ljava/lang/Object;III)Z";

    @Override
    public byte[] transform(
            String name,
            String transformedName,
            byte[] basicClass
    ) {
        if (basicClass == null
                || (!PATH_FINDER_CLASS.equals(name)
                && !PATH_FINDER_CLASS.equals(transformedName)
                && !PATH_FINDER_NOTCH_CLASS.equals(name))) {
            return basicClass;
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        MethodNode target = findTargetMethod(classNode);
        if (target == null) {
            warn("could not uniquely identify PathFinder.func_82565_a");
            return basicClass;
        }
        if (containsHookCall(target)) {
            return basicClass;
        }

        BlockLoadSite blockLoad = findBlockLoadSite(target);
        if (blockLoad == null) {
            warn("could not uniquely identify PathFinder's node block load");
            return basicClass;
        }
        IincInsnNode zIncrement = findInnerLoopIncrement(
                blockLoad.storeInstruction,
                blockLoad.zLocal
        );
        if (zIncrement == null) {
            warn("could not uniquely identify PathFinder's inner-loop continue");
            return basicClass;
        }

        LabelNode advanceInnerZ = new LabelNode();
        target.instructions.insertBefore(zIncrement, advanceInnerZ);
        LabelNode useVanillaClassification = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, blockLoad.blockLocal));
        hook.add(new FieldInsnNode(
                Opcodes.GETSTATIC,
                SIEGE_REGISTRY_OWNER,
                GATE_PART_FIELD,
                isNotchTarget(target)
                        ? GATE_PART_NOTCH_DESCRIPTOR
                        : GATE_PART_DESCRIPTOR
        ));
        hook.add(new JumpInsnNode(Opcodes.IF_ACMPNE, useVanillaClassification));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new VarInsnNode(Opcodes.ILOAD, blockLoad.xLocal));
        hook.add(new VarInsnNode(Opcodes.ILOAD, blockLoad.yLocal));
        hook.add(new VarInsnNode(Opcodes.ILOAD, blockLoad.zLocal));
        hook.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                HOOK_OWNER,
                HOOK_METHOD,
                HOOK_DESCRIPTOR,
                false
        ));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, useVanillaClassification));
        hook.add(new JumpInsnNode(Opcodes.GOTO, advanceInnerZ));
        hook.add(useVanillaClassification);
        target.instructions.insert(blockLoad.storeInstruction, hook);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        System.out.println(
                "[LOTRMoreMobs] Installed Siege Gate AI path classification "
                        + "in " + target.name + target.desc
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
        return TARGET_DESCRIPTOR.equals(method.desc)
                && "func_82565_a".equals(method.name)
                || TARGET_NOTCH_DESCRIPTOR.equals(method.desc)
                && "a".equals(method.name);
    }

    private static boolean isNotchTarget(MethodNode method) {
        return TARGET_NOTCH_DESCRIPTOR.equals(method.desc)
                && "a".equals(method.name);
    }

    private static BlockLoadSite findBlockLoadSite(MethodNode method) {
        List<BlockLoadSite> matches = new ArrayList<BlockLoadSite>();
        for (AbstractInsnNode instruction =
                method.instructions.getFirst();
                instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)
                    || !isWorldGetBlock((MethodInsnNode)instruction)) {
                continue;
            }
            AbstractInsnNode next = nextRealInstruction(instruction.getNext());
            if (!(next instanceof VarInsnNode)
                    || next.getOpcode() != Opcodes.ASTORE) {
                continue;
            }
            int[] coordinates = findCoordinateLocals(instruction);
            if (coordinates == null) {
                return null;
            }
            matches.add(new BlockLoadSite(
                    (VarInsnNode)next,
                    coordinates[0],
                    coordinates[1],
                    coordinates[2]
            ));
        }
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private static boolean isWorldGetBlock(MethodInsnNode call) {
        boolean owner = "net/minecraft/world/World".equals(call.owner)
                || "ahb".equals(call.owner);
        boolean name = "getBlock".equals(call.name)
                || "func_147439_a".equals(call.name)
                || "a".equals(call.name);
        return owner
                && name
                && (BLOCK_DESCRIPTOR.equals(call.desc)
                || BLOCK_NOTCH_DESCRIPTOR.equals(call.desc));
    }

    private static int[] findCoordinateLocals(
            AbstractInsnNode instruction
    ) {
        int[] reverse = new int[3];
        int count = 0;
        for (AbstractInsnNode previous = instruction.getPrevious();
                previous != null && count < reverse.length;
                previous = previous.getPrevious()) {
            if (previous instanceof VarInsnNode
                    && previous.getOpcode() == Opcodes.ILOAD) {
                reverse[count++] = ((VarInsnNode)previous).var;
            }
        }
        if (count != reverse.length) {
            return null;
        }
        return new int[] {reverse[2], reverse[1], reverse[0]};
    }

    private static IincInsnNode findInnerLoopIncrement(
            AbstractInsnNode after,
            int zLocal
    ) {
        IincInsnNode increment = null;
        for (AbstractInsnNode instruction = after.getNext();
                instruction != null;
                instruction = instruction.getNext()) {
            if (!(instruction instanceof IincInsnNode)
                    || ((IincInsnNode)instruction).var != zLocal) {
                continue;
            }
            AbstractInsnNode next = nextRealInstruction(instruction.getNext());
            if (!(next instanceof JumpInsnNode)
                    || next.getOpcode() != Opcodes.GOTO) {
                return null;
            }
            if (increment != null) {
                return null;
            }
            increment = (IincInsnNode)instruction;
        }
        return increment;
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

    private static AbstractInsnNode nextRealInstruction(
            AbstractInsnNode instruction
    ) {
        while (instruction != null && instruction.getOpcode() < 0) {
            instruction = instruction.getNext();
        }
        return instruction;
    }

    private static void warn(String reason) {
        System.err.println(
                "[LOTRMoreMobs] Siege Gate AI path compatibility " + reason
                        + "; AI path behavior is unchanged."
        );
    }

    private static final class BlockLoadSite {
        private final VarInsnNode storeInstruction;
        private final int blockLocal;
        private final int xLocal;
        private final int yLocal;
        private final int zLocal;

        private BlockLoadSite(
                VarInsnNode storeInstruction,
                int xLocal,
                int yLocal,
                int zLocal
        ) {
            this.storeInstruction = storeInstruction;
            blockLocal = storeInstruction.var;
            this.xLocal = xLocal;
            this.yLocal = yLocal;
            this.zLocal = zLocal;
        }
    }
}
