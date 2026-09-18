package com.fuzs.aquaacrobatics.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** getWaterColorMultiplier is Forge-added and is not renamed in production. */
public final class AquaBiomeTransformer implements IClassTransformer {
    private static final String TARGET = "net.minecraft.world.biome.BiomeGenBase";
    private static final String LOGIC = "com/fuzs/aquaacrobatics/biome/AquaBiomeLogic";

    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (!TARGET.equals(transformedName)) return bytes;
        final ClassNode node = AquaAsmMappings.read("AquaBiomeTransformer", transformedName, bytes);
        return AquaAsmMappings.finish("AquaBiomeTransformer", node, () -> {
            MethodNode water = AquaAsmMappings.method("AquaBiomeTransformer", node, "()I", "getWaterColorMultiplier");
            AbstractInsnNode tail = null;
            int returns = 0;
            for (AbstractInsnNode i = water.instructions.getFirst(); i != null; i = i.getNext()) {
                if (i.getOpcode() == Opcodes.IRETURN) { tail = i; returns++; }
            }
            if (returns != 1) throw new IllegalStateException("getWaterColorMultiplier()I expected IRETURN matches=1, found=" + returns);
            InsnList bridge = new InsnList();
            bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
            bridge.add(new InsnNode(Opcodes.SWAP));
            bridge.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LOGIC, "c", "(L" + node.name + ";I)I", false));
            water.instructions.insertBefore(tail, bridge);
            for (MethodNode m : node.methods) if (m.name.equals("aqua$waterColorMultiplier"))
                throw new IllegalStateException("aqua$waterColorMultiplier()I already exists");
            MethodNode facade = new MethodNode(Opcodes.ACC_PUBLIC, "aqua$waterColorMultiplier", "()I", null, null);
            facade.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            facade.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            facade.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, "getWaterColorMultiplier", "()I", false));
            facade.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LOGIC, "c", "(L" + node.name + ";I)I", false));
            facade.instructions.add(new InsnNode(Opcodes.IRETURN));
            node.methods.add(facade);
        });
    }
}
