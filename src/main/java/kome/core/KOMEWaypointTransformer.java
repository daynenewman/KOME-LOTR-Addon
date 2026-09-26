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
import org.objectweb.asm.tree.FrameNode;

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
    static final String REQUEST_TARGET_CLASS =
        "lotr.common.network.LOTRPacketFastTravel$Handler";
    static final String REQUEST_TARGET_METHOD = "onMessage";
    static final String REQUEST_TARGET_DESC =
        "(Llotr/common/network/LOTRPacketFastTravel;"
            + "Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)"
            + "Lcpw/mods/fml/common/network/simpleimpl/IMessage;";
    static final String REQUEST_HOOK_NAME = "allowNativeRequest";
    static final String REQUEST_HOOK_DESC =
        "(Lnet/minecraft/entity/player/EntityPlayerMP;"
            + "Llotr/common/world/map/LOTRAbstractWaypoint;)Z";
    private static volatile boolean nativeRequestGuardInstalled;
    private static volatile boolean finalTravelGuardInstalled;

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) {
            return basicClass;
        }
        try {
            if (TARGET_CLASS.equals(transformedName)) {
                return transformPlayerData(basicClass);
            }
            if (REQUEST_TARGET_CLASS.equals(transformedName)) {
                return transformNativeRequestHandler(basicClass);
            }
            return basicClass;
        } catch (RuntimeException failure) {
            System.err.println("[KOME] Refusing to start with an unsecured or incompatible LOTR fast-travel pipeline: "
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
        if (containsHook(target, HOOK_NAME, HOOK_DESC)) {
            finalTravelGuardInstalled = true;
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
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        target.instructions.insert(guard);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        finalTravelGuardInstalled = true;
        System.out.println("[KOME] Secured LOTRPlayerData.receiveFTBouncePacket with the final waypoint gate.");
        return writer.toByteArray();
    }

    byte[] transformNativeRequestHandler(byte[] original) {
        ClassNode classNode = new ClassNode();
        new ClassReader(original).accept(classNode, 0);
        MethodNode target = null;
        for (MethodNode method : classNode.methods) {
            if (REQUEST_TARGET_METHOD.equals(method.name)
                    && REQUEST_TARGET_DESC.equals(method.desc)) {
                if (target != null) {
                    throw new IllegalStateException(
                        "multiple native fast-travel request handlers found");
                }
                target = method;
            }
        }
        if (target == null) {
            throw new IllegalStateException(
                "LOTR v36.15 native fast-travel request fingerprint was not found");
        }
        if (containsHook(target, REQUEST_HOOK_NAME, REQUEST_HOOK_DESC)) {
            nativeRequestGuardInstalled = true;
            return original;
        }

        MethodInsnNode nativeUnlockCall = null;
        for (AbstractInsnNode node = target.instructions.getFirst();
                node != null; node = node.getNext()) {
            if (node instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) node;
                if ("lotr/common/world/map/LOTRAbstractWaypoint".equals(call.owner)
                        && "hasPlayerUnlocked".equals(call.name)
                        && "(Lnet/minecraft/entity/player/EntityPlayer;)Z".equals(call.desc)) {
                    if (nativeUnlockCall != null) {
                        throw new IllegalStateException(
                            "multiple native waypoint unlock checks found");
                    }
                    nativeUnlockCall = call;
                }
            }
        }
        if (nativeUnlockCall == null) {
            throw new IllegalStateException(
                "native waypoint unlock check was not found");
        }

        AbstractInsnNode playerLoad = previousExecutable(nativeUnlockCall);
        AbstractInsnNode waypointLoad = previousExecutable(playerLoad);
        if (!(waypointLoad instanceof VarInsnNode)
                || waypointLoad.getOpcode() != Opcodes.ALOAD
                || ((VarInsnNode) waypointLoad).var != 7
                || !(playerLoad instanceof VarInsnNode)
                || playerLoad.getOpcode() != Opcodes.ALOAD
                || ((VarInsnNode) playerLoad).var != 3) {
            throw new IllegalStateException(
                "native waypoint/player local-variable fingerprint changed");
        }

        LabelNode permitted = new LabelNode();
        InsnList guard = new InsnList();
        guard.add(new VarInsnNode(Opcodes.ALOAD, 3));
        guard.add(new VarInsnNode(Opcodes.ALOAD, 7));
        guard.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC, HOOK_OWNER,
            REQUEST_HOOK_NAME, REQUEST_HOOK_DESC, false));
        guard.add(new JumpInsnNode(Opcodes.IFNE, permitted));
        guard.add(new InsnNode(Opcodes.ACONST_NULL));
        guard.add(new InsnNode(Opcodes.ARETURN));
        guard.add(permitted);
        guard.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        target.instructions.insertBefore(waypointLoad, guard);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        nativeRequestGuardInstalled = true;
        System.out.println(
            "[KOME] Secured LOTRPacketFastTravel.Handler with the authoritative waypoint gate.");
        return writer.toByteArray();
    }

    /** Refuse to run a server where native F-key requests can bypass KOME. */
    public static void requireNativeRequestGuardInstalled() {
        if (!nativeRequestGuardInstalled) {
            throw new IllegalStateException(
                "KOME native fast-travel request guard was not installed; refusing an unsecured server startup");
        }
    }

    static boolean isNativeRequestGuardInstalled() {
        return nativeRequestGuardInstalled;
    }

    static boolean isFinalTravelGuardInstalled() {
        return finalTravelGuardInstalled;
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

    private AbstractInsnNode previousExecutable(AbstractInsnNode node) {
        AbstractInsnNode previous = node == null ? null : node.getPrevious();
        while (previous != null && previous.getOpcode() < 0) {
            previous = previous.getPrevious();
        }
        return previous;
    }

    private boolean containsHook(
            MethodNode method, String hookName, String hookDesc) {
        for (AbstractInsnNode node = method.instructions.getFirst(); node != null; node = node.getNext()) {
            if (node instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) node;
                if (HOOK_OWNER.equals(call.owner)
                        && hookName.equals(call.name)
                        && hookDesc.equals(call.desc)) {
                    return true;
                }
            }
        }
        return false;
    }
}
