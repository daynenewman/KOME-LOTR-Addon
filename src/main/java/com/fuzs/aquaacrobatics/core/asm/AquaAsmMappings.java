package com.fuzs.aquaacrobatics.core.asm;

import java.util.Arrays;

import com.fuzs.aquaacrobatics.core.AquaAcrobaticsCore;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Forge 1614 runs these transformers AFTER deobfuscation: class names are readable
 * in both environments, but production members are SRG. Never infer MCP members
 * from a readable class name. Literal ASM names are not reobfuscated by Gradle.
 * Raw names are retained for the explicitly supported pre-remap class fixtures.
 */
final class AquaAsmMappings {
    private AquaAsmMappings() {}

    static boolean raw(String owner) {
        return owner.indexOf('/') < 0;
    }

    static String member(String owner, String mcp, String srg, String notch) {
        return raw(owner) ? notch : AquaAcrobaticsCore.isDevEnv() ? mcp : srg;
    }

    static String type(String owner, String readable, String notch) {
        return raw(owner) ? notch : readable;
    }

    static MethodNode method(String transformer, ClassNode target, String descriptor, String... names) {
        MethodNode result = null;
        int matches = 0;
        for (MethodNode method : target.methods) {
            if (descriptor.equals(method.desc) && Arrays.asList(names).contains(method.name)) {
                result = method;
                matches++;
            }
        }
        if (matches != 1) {
            throw new IllegalStateException(transformer + " target=" + target.name + " expected="
                + Arrays.toString(names) + descriptor + " matches=" + matches);
        }
        return result;
    }

    static ClassNode read(String transformer, String target, byte[] bytes) {
        if (bytes == null) throw new IllegalStateException(transformer + " target=" + target + " missing bytecode");
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!raw(node.name) && !node.name.replace('/', '.').equals(target)) {
            throw new IllegalStateException(transformer + " target=" + target + " unexpected bytecode owner=" + node.name);
        }
        for (FieldNode field : node.fields) {
            if (marker(transformer).equals(field.name)) {
                throw new IllegalStateException(transformer + " target=" + target + " already transformed (marker matches=1)");
            }
        }
        return node;
    }

    static byte[] finish(String transformer, ClassNode node, Runnable patches) {
        try {
            patches.run();
            node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
                | Opcodes.ACC_SYNTHETIC, marker(transformer), "Z", null, Integer.valueOf(1)));
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            return writer.toByteArray();
        } catch (RuntimeException failure) {
            // Fail closed. No partially transformed byte array is returned.
            throw new IllegalStateException(transformer + " target=" + node.name + " required patch failed: "
                + failure.getMessage(), failure);
        }
    }

    private static String marker(String transformer) {
        return "aqua$patched$" + transformer;
    }
}
