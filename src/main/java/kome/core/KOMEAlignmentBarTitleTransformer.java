package kome.core;

import java.util.ArrayList;
import java.util.List;
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

/** Replaces the text title rendered beneath LOTR alignment bars with KOME's selected title mode. */
public final class KOMEAlignmentBarTitleTransformer implements IClassTransformer {
    static final String TARGET = "lotr.client.LOTRTickHandlerClient";
    static final String RANK_OWNER = "lotr/common/fac/LOTRFactionRank";
    static final String RANK_METHOD = "getShortNameWithGender";
    static final String RANK_DESC = "(Llotr/common/LOTRPlayerData;)Ljava/lang/String;";
    static final String BRIDGE_OWNER = "kome/client/KOMEFactionTitleClientBridge";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) {
            return bytes;
        }

        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);

        int inserted = 0;
        for (MethodNode method : node.methods) {
            if (!"renderAlignmentBar".equals(method.name)) {
                continue;
            }

            List<MethodInsnNode> rankNameCalls = new ArrayList<MethodInsnNode>();
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null;
                    instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (RANK_OWNER.equals(call.owner)
                        && RANK_METHOD.equals(call.name)
                        && RANK_DESC.equals(call.desc)) {
                    rankNameCalls.add(call);
                }
            }

            // renderAlignmentBar resolves min title, max title, then the current title.
            if (rankNameCalls.size() != 3) {
                throw new IllegalStateException(
                    "KOME alignment-bar title transformer expected three LOTR rank-name calls, found "
                        + rankNameCalls.size() + ".");
            }

            MethodInsnNode currentTitleCall = rankNameCalls.get(2);
            InsnList hook = new InsnList();
            // Existing stack: vanilla title String. Append faction and other-player flag.
            hook.add(new VarInsnNode(Opcodes.ALOAD, 2));
            hook.add(new VarInsnNode(Opcodes.ILOAD, 1));
            hook.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                BRIDGE_OWNER,
                "resolveAlignmentBarTitle",
                "(Ljava/lang/String;Llotr/common/fac/LOTRFaction;Z)Ljava/lang/String;",
                false));
            method.instructions.insert(currentTitleCall, hook);
            inserted++;
        }

        if (inserted != 1) {
            throw new IllegalStateException(
                "KOME alignment-bar title transformer expected exactly one renderAlignmentBar hook, found "
                    + inserted + ".");
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }
}
