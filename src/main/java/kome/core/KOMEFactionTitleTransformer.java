package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Replaces the native LOTR alignment-rank line in LOTRGuiFactions with
 * KOME's progression/diplomacy presentation.
 *
 * The alignment number and alignment bar remain native and untouched.
 */
public final class KOMEFactionTitleTransformer
        implements IClassTransformer {
    static final String TARGET=
        "lotr.client.gui.LOTRGuiFactions";

    static final String TARGET_OWNER=
        "lotr/client/gui/LOTRGuiFactions";

    static final String HOOK_OWNER=
        "kome/client/KOMEFactionTitleClientBridge";

    static final String HOOK_NAME=
        "resolveFactionStatus";

    static final String HOOK_DESC=
        "(Llotr/common/fac/LOTRFaction;ZLjava/lang/String;)"
            +"Ljava/lang/String;";

    private static final String TITLE_TRANSLATION_KEY=
        "lotr.gui.factions.alignment.state";

    private static final String ALIGNMENT_TITLE_HEADER_KEY=
        "lotr.gui.factions.rankHeader";

    private static final String KOME_ALIGNMENT_TITLE_HEADER_KEY=
        "kome.gui.factions.alignmentTitleHeader";

    @Override
    public byte[] transform(
            String name,
            String transformedName,
            byte[] bytes) {
        if(bytes==null||!TARGET.equals(transformedName)) {
            return bytes;
        }

        ClassNode node=new ClassNode();

        new ClassReader(bytes).accept(node,0);

        boolean alreadyHooked=hasHook(node);
        int inserted=0;
        int headerReplacements=0;

        for(MethodNode method:node.methods) {
            for(AbstractInsnNode instruction=
                    method.instructions.getFirst();
                    instruction!=null;
                    instruction=instruction.getNext()) {
                if(instruction instanceof LdcInsnNode
                        &&ALIGNMENT_TITLE_HEADER_KEY.equals(
                            ((LdcInsnNode)instruction).cst)) {
                    ((LdcInsnNode)instruction).cst=KOME_ALIGNMENT_TITLE_HEADER_KEY;
                    headerReplacements++;
                    continue;
                }

                if(alreadyHooked
                        ||!(instruction instanceof LdcInsnNode)
                        ||!TITLE_TRANSLATION_KEY.equals(
                            ((LdcInsnNode)instruction).cst)) {
                    continue;
                }

                VarInsnNode rankStore=
                    findRankStore(instruction);

                if(rankStore==null) {
                    continue;
                }

                InsnList hook=new InsnList();

                hook.add(
                    new VarInsnNode(
                        Opcodes.ALOAD,
                        0));

                hook.add(
                    new FieldInsnNode(
                        Opcodes.GETFIELD,
                        TARGET_OWNER,
                        "currentFaction",
                        "Llotr/common/fac/LOTRFaction;"));

                hook.add(
                    new VarInsnNode(
                        Opcodes.ALOAD,
                        0));

                hook.add(
                    new FieldInsnNode(
                        Opcodes.GETFIELD,
                        TARGET_OWNER,
                        "isOtherPlayer",
                        "Z"));

                hook.add(
                    new VarInsnNode(
                        Opcodes.ALOAD,
                        rankStore.var));

                hook.add(
                    new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        HOOK_OWNER,
                        HOOK_NAME,
                        HOOK_DESC,
                        false));

                hook.add(
                    new VarInsnNode(
                        Opcodes.ASTORE,
                        rankStore.var));

                method.instructions.insert(
                    rankStore,
                    hook);

                inserted++;
            }
        }

        if(!alreadyHooked&&inserted!=1) {
            throw new IllegalStateException(
                "KOME faction-title transformer expected exactly one "
                    +"LOTRGuiFactions alignment-title site, found "
                    +inserted+".");
        }

        if(headerReplacements!=1&&!hasAlignmentTitleHeader(node)) {
            throw new IllegalStateException(
                "KOME faction-title transformer expected exactly one "
                    +"LOTRGuiFactions alignment-title header, found "
                    +headerReplacements+".");
        }

        ClassWriter writer=
            new ClassWriter(
                ClassWriter.COMPUTE_MAXS);

        node.accept(writer);

        return writer.toByteArray();
    }

    private static VarInsnNode findRankStore(
            AbstractInsnNode start) {
        AbstractInsnNode cursor=start;

        for(int i=0;
                i<20&&cursor!=null;
                i++) {
            cursor=cursor.getNext();

            if(cursor instanceof VarInsnNode
                    &&cursor.getOpcode()==Opcodes.ASTORE) {
                return (VarInsnNode)cursor;
            }
        }

        return null;
    }

    private static boolean hasAlignmentTitleHeader(
            ClassNode node) {
        for(MethodNode method:node.methods) {
            for(AbstractInsnNode instruction=
                    method.instructions.getFirst();
                    instruction!=null;
                    instruction=instruction.getNext()) {
                if(instruction instanceof LdcInsnNode
                        &&KOME_ALIGNMENT_TITLE_HEADER_KEY.equals(
                            ((LdcInsnNode)instruction).cst)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasHook(
            ClassNode node) {
        for(MethodNode method:node.methods) {
            for(AbstractInsnNode instruction=
                    method.instructions.getFirst();
                    instruction!=null;
                    instruction=instruction.getNext()) {
                if(instruction instanceof MethodInsnNode) {
                    MethodInsnNode call=
                        (MethodInsnNode)instruction;

                    if(HOOK_OWNER.equals(call.owner)
                            &&HOOK_NAME.equals(call.name)
                            &&HOOK_DESC.equals(call.desc)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}
