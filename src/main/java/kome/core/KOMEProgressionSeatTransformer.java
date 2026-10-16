package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Observes native structure spawns without placing structures or manufacturing NPCs. */
public final class KOMEProgressionSeatTransformer implements IClassTransformer {
    static final String BRIDGE = "kome/common/data/KOMEProgressionServiceLocation";
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || transformedName == null
                || !(transformedName.startsWith("lotr.common.world.structure.LOTRWorldGen")
                    || transformedName.startsWith("lotr.common.world.structure2.LOTRWorldGen"))) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) for (AbstractInsnNode i : method.instructions.toArray())
            if (i instanceof MethodInsnNode && BRIDGE.equals(((MethodInsnNode)i).owner)) return bytes;
        int count = 0;
        for (MethodNode method : node.methods) {
            if ((method.access & Opcodes.ACC_STATIC) != 0) continue;
            for (AbstractInsnNode i : method.instructions.toArray()) if (i instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)i;
                if (call.getOpcode() != Opcodes.INVOKEVIRTUAL || !"net/minecraft/world/World".equals(call.owner)
                        || !("spawnEntityInWorld".equals(call.name) || "func_72838_d".equals(call.name))
                        || !"(Lnet/minecraft/entity/Entity;)Z".equals(call.desc)) continue;
                method.instructions.insertBefore(call, new InsnNode(Opcodes.DUP2));
                InsnList after = new InsnList(); after.add(new VarInsnNode(Opcodes.ALOAD, 0));
                after.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "generated",
                    "(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;ZLjava/lang/Object;)Z", false));
                method.instructions.insert(call, after); count++;
            }
        }
        if (count == 0) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
