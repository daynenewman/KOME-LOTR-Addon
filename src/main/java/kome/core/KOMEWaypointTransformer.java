package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds the last possible server-side destination check to unmodified LOTR v36.15
 * bytecode. No LOTR source or class is distributed by the addon.
 */
public final class KOMEWaypointTransformer implements IClassTransformer {
    static final String TARGET_CLASS = "lotr.common.LOTRPlayerData";
    static final String TARGET_METHOD = "receiveFTBouncePacket";
    static final String TARGET_DESC = "()V";
    static final String HOOK_OWNER = "kome/common/data/KOMEWaypointAccessService";
    static final String HOOK_NAME = "allowFinalTravel";
    static final String HOOK_DESC = "(Llotr/common/LOTRPlayerData;)Z";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null || !TARGET_CLASS.equals(transformedName)) {
            return basicClass;
        }
        try {
            return transformPlayerData(basicClass);
        } catch (RuntimeException failure) {
            System.err.println("[KOME] Refusing to start with an unsecured or incompatible LOTRPlayerData: "
                + failure.getMessage());
            throw failure;
        }
    }

    byte[] transformPlayerData(byte[] original) {
        ClassNode classNode = new ClassNode();
        new ClassReader(original).accept(classNode, 0);
        MethodNode target = null;
        for (MethodNode method : classNode.methods) {
            if (TARGET_METHOD.equals(method.name) && TARGET_DESC.equals(method.desc)) {
                if (target != null) {
                    throw new IllegalStateException("multiple receiveFTBouncePacket() methods found");
                }
                target = method;
            }
        }
        if (target == null) {
            throw new IllegalStateException("LOTR v36.15 receiveFTBouncePacket() fingerprint was not found");
        }
        if (containsHook(target)) {
            return original;
        }
        if (!containsFastTravelCall(target)) {
            throw new IllegalStateException("receiveFTBouncePacket() does not contain the expected fastTravelTo call");
        }

        LabelNode permitted = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 0));
        guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK_OWNER, HOOK_NAME, HOOK_DESC, false));
        guard.add(new JumpInsnNode(Opcodes.IFNE, permitted));
        guard.add(new InsnNode(Opcodes.RETURN));
        guard.add(permitted);
        target.instructions.insert(guard);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        System.out.println("[KOME] Secured LOTRPlayerData.receiveFTBouncePacket with the final waypoint gate.");
        return writer.toByteArray();
    }

    private boolean containsFastTravelCall(MethodNode method) {
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) node;
                if ("lotr/common/LOTRPlayerData".equals(call.owner) && "fastTravelTo".equals(call.name)
                        && "(Llotr/common/world/map/LOTRAbstractWaypoint;)V".equals(call.desc)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean containsHook(MethodNode method) {
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) node;
                if (HOOK_OWNER.equals(call.owner) && HOOK_NAME.equals(call.name) && HOOK_DESC.equals(call.desc)) {
                    return true;
                }
            }
        }
        return false;
    }
}
