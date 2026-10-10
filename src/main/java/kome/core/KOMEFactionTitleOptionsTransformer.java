package kome.core;

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

/** Adds KOME's rank/alignment title display preference to LOTRGuiOptions. */
public final class KOMEFactionTitleOptionsTransformer implements IClassTransformer {
    static final String TARGET = "lotr.client.gui.LOTRGuiOptions";
    static final String TARGET_OWNER = "lotr/client/gui/LOTRGuiOptions";
    static final String BRIDGE_OWNER = "kome/client/KOMEFactionTitleOptionsBridge";

    static final String INIT_HOOK_NAME = "onOptionsInit";
    static final String INIT_HOOK_DESC = "(Llotr/client/gui/LOTRGuiOptions;)V";
    static final String ACTION_HOOK_NAME = "handleButton";
    static final String ACTION_HOOK_DESC = "(Lnet/minecraft/client/gui/GuiButton;)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) {
            return bytes;
        }

        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);

        boolean initAlreadyHooked = hasHook(node, INIT_HOOK_NAME, INIT_HOOK_DESC);
        boolean actionAlreadyHooked = hasHook(node, ACTION_HOOK_NAME, ACTION_HOOK_DESC);
        int initHooks = 0;
        int actionHooks = 0;

        for (MethodNode method : node.methods) {
            if (!initAlreadyHooked
                    && ("initGui".equals(method.name) || "func_73866_w_".equals(method.name))
                    && "()V".equals(method.desc)) {
                initHooks += hookInitReturns(method);
            } else if (!actionAlreadyHooked
                    && ("actionPerformed".equals(method.name) || "func_146284_a".equals(method.name))
                    && "(Lnet/minecraft/client/gui/GuiButton;)V".equals(method.desc)) {
                actionHooks += hookAction(method);
            }
        }

        if (!initAlreadyHooked && initHooks != 1) {
            throw new IllegalStateException(
                    "KOME faction-title options transformer expected one init hook, found "
                            + initHooks + ".");
        }

        if (!actionAlreadyHooked && actionHooks != 1) {
            throw new IllegalStateException(
                    "KOME faction-title options transformer expected one action hook, found "
                            + actionHooks + ".");
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static int hookInitReturns(MethodNode method) {
        int returns = 0;

        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.RETURN) {
                continue;
            }

            InsnList hook = new InsnList();
            hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
            hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    BRIDGE_OWNER,
                    INIT_HOOK_NAME,
                    INIT_HOOK_DESC,
                    false));

            method.instructions.insertBefore(instruction, hook);
            returns++;
        }

        return returns;
    }

    private static int hookAction(MethodNode method) {
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 1));
        hook.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                BRIDGE_OWNER,
                ACTION_HOOK_NAME,
                ACTION_HOOK_DESC,
                false));

        method.instructions.insert(hook);
        return 1;
    }

    private static boolean hasHook(ClassNode node, String name, String desc) {
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                 instruction != null;
                 instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }

                MethodInsnNode call = (MethodInsnNode) instruction;

                if (BRIDGE_OWNER.equals(call.owner)
                        && name.equals(call.name)
                        && desc.equals(call.desc)) {
                    return true;
                }
            }
        }

        return false;
    }
}
