package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Native HP mutations and coherent serialization, MCP and SRG names at sorting index 1100. */
public final class KOMECampaignHealthTransformer implements IClassTransformer {
    private static final String BRIDGE = "kome/common/data/KOMECampaignHealthEntity";
    private static final String ENTITY = "Lnet/minecraft/entity/Entity;";
    private static final String NBT = "Lnet/minecraft/nbt/NBTTagCompound;";
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        boolean living = "net.minecraft.entity.EntityLivingBase".equals(transformedName);
        if (bytes == null || !living && !"net.minecraft.entity.Entity".equals(transformedName)) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        for (MethodNode m : node.methods) for (AbstractInsnNode i : m.instructions.toArray())
            if (i instanceof MethodInsnNode && BRIDGE.equals(((MethodInsnNode)i).owner)) return bytes;
        int count = 0;
        for (MethodNode m : node.methods) {
            if (living && ("setHealth".equals(m.name) || "func_70606_j".equals(m.name)) && "(F)V".equals(m.desc)) {
                returns(m, "changed", false);
                InsnList before = new InsnList(); before.add(new VarInsnNode(Opcodes.ALOAD, 0)); before.add(new VarInsnNode(Opcodes.FLOAD, 1));
                before.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "allowChange", "(" + ENTITY + "F)Z", false));
                LabelNode allowed = new LabelNode(); before.add(new JumpInsnNode(Opcodes.IFNE,allowed));
                before.add(new InsnNode(Opcodes.RETURN)); before.add(allowed);
                before.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                m.instructions.insert(before); count++;
            }
            if (!living && ("readFromNBT".equals(m.name) || "func_70020_e".equals(m.name)) && ("(" + NBT + ")V").equals(m.desc)) {
                m.instructions.insert(call("beginRead", true)); returns(m, "endRead", true); count++;
            }
            if (!living && ("writeToNBT".equals(m.name) || "func_70109_d".equals(m.name)) && ("(" + NBT + ")V").equals(m.desc)) {
                returns(m, "endWrite", true); count++;
            }
        }
        if (count != (living ? 1 : 2)) throw new IllegalStateException("KOME health hook fingerprint changed: " + transformedName + " matched=" + count);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static InsnList call(String method, boolean nbt) {
        InsnList list = new InsnList(); list.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if (nbt) list.add(new VarInsnNode(Opcodes.ALOAD, 1));
        list.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, method, "(" + ENTITY + (nbt ? NBT : "") + ")V", false)); return list;
    }
    private static void returns(MethodNode m, String method, boolean nbt) {
        for (AbstractInsnNode i : m.instructions.toArray()) if (i.getOpcode() == Opcodes.RETURN) m.instructions.insertBefore(i, call(method, nbt));
    }
}
