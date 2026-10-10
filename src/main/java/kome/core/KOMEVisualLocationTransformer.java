package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Hooks KOME presentation into LOTR's native overhead-offer and main-map marker passes. */
public final class KOMEVisualLocationTransformer implements IClassTransformer {
    static final String NPC_RENDERING = "lotr.client.render.entity.LOTRNPCRendering";
    static final String MAP = "lotr.client.gui.LOTRGuiMap";
    static final String QUEST_INFO = "lotr.common.entity.npc.LOTREntityQuestInfo";
    static final String BRIDGE = "kome/client/KOMEVisualRenderBridge";
    static final String QUEST_GUARD = "kome/common/data/KOMEMiniquestOfferGuard";
    static final String NATIVE_RELATIONSHIP_RENDERER = "kome$renderRelationshipIcon";
    static final String NPC_RENDER_DESC = "(Llotr/common/entity/npc/LOTREntityNPC;DDD)V";

    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || (!NPC_RENDERING.equals(transformedName) && !MAP.equals(transformedName)
                && !QUEST_INFO.equals(transformedName))) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean found = false;
        MethodNode nativeQuestBook = null;
        boolean hasRelationshipRenderer = false;
        for (MethodNode method : node.methods) {
            if (NPC_RENDERING.equals(transformedName) && NATIVE_RELATIONSHIP_RENDERER.equals(method.name)
                    && NPC_RENDER_DESC.equals(method.desc)) hasRelationshipRenderer = true;
            if (NPC_RENDERING.equals(transformedName) && "renderQuestBook".equals(method.name)
                    && NPC_RENDER_DESC.equals(method.desc)) nativeQuestBook = method;
            if (NPC_RENDERING.equals(transformedName)
                    && "renderQuestOffer".equals(method.name)
                    && NPC_RENDER_DESC.equals(method.desc)) {
                found = true;
                injectBeforeReturns(method, overheadCall());
            }
            if (MAP.equals(transformedName)
                    && "renderMiniQuests".equals(method.name)
                    && "(Lnet/minecraft/entity/player/EntityPlayer;II)V".equals(method.desc)) {
                found = true;
                injectBeforeFinalReturn(method, mapCall());
            }
            if (QUEST_INFO.equals(transformedName) && "canOfferQuestsTo".equals(method.name)
                    && "(Lnet/minecraft/entity/player/EntityPlayer;)Z".equals(method.desc)) {
                found = true;
                injectQuestGuard(method);
            }
        }
        if (NPC_RENDERING.equals(transformedName)) {
            if (nativeQuestBook == null)
                throw new IllegalStateException("LOTR v36.15 accepted-miniquest renderer was not found");
            if (!hasRelationshipRenderer) node.methods.add(relationshipRenderer(nativeQuestBook));
            // Clone first: native indicators are hidden, the supplied relationship icon is not.
            for(MethodNode method:node.methods)
                if(NPC_RENDER_DESC.equals(method.desc)&&("renderQuestBook".equals(method.name)||"renderQuestOffer".equals(method.name)))
                    suppressNative(method,"renderQuestOffer".equals(method.name));
            for(MethodNode method:node.methods)if(method.name.startsWith("render")&&(method.access&Opcodes.ACC_STATIC)!=0&&method.desc.endsWith(")V")){
                boolean existing=false;for(AbstractInsnNode i=method.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode&&"isRendering".equals(((MethodInsnNode)i).name))existing=true;
                if(!existing){InsnList guard=new InsnList();org.objectweb.asm.tree.LabelNode ordinary=new org.objectweb.asm.tree.LabelNode();
                    guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"kome/client/KOMEProgressionEnemyOutline","isRendering","()Z",false));guard.add(new JumpInsnNode(Opcodes.IFEQ,ordinary));guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));guard.add(ordinary);method.instructions.insert(guard);}
            }
        }
        if (!found) throw new IllegalStateException("LOTR v36.15 visual rendering fingerprint was not found in " + transformedName);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static void suppressNative(MethodNode method,boolean overhead) {
        for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())
            if(insn instanceof MethodInsnNode&&"suppressNativeIndicator".equals(((MethodInsnNode)insn).name))return;
        InsnList hook=new InsnList();org.objectweb.asm.tree.LabelNode nativePath=new org.objectweb.asm.tree.LabelNode();
        hook.add(new VarInsnNode(Opcodes.ALOAD,0));
        hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"suppressNativeIndicator","(Llotr/common/entity/npc/LOTREntityNPC;)Z",false));
        hook.add(new JumpInsnNode(Opcodes.IFEQ,nativePath));
        if(overhead)hook.add(overheadCall());
        hook.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));hook.add(nativePath);method.instructions.insert(hook);
    }

    /** Clone the audited LOTR renderer, changing only its eligibility gate and supplied item. */
    private static MethodNode relationshipRenderer(MethodNode nativeMethod) {
        MethodNode copy = new MethodNode(Opcodes.ASM5, nativeMethod.access,
            NATIVE_RELATIONSHIP_RENDERER, nativeMethod.desc, nativeMethod.signature,
            nativeMethod.exceptions == null ? null : nativeMethod.exceptions.toArray(new String[0]));
        nativeMethod.accept(copy);
        // Pouches use getIcon(stack, pass); getIconIndex() returns null for this native item.
        for(AbstractInsnNode insn=copy.instructions.getFirst();insn!=null;insn=insn.getNext())
            if(insn instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)insn;
                if("net/minecraft/item/ItemStack".equals(call.owner)
                        &&("getIconIndex".equals(call.name)||"func_77954_c".equals(call.name))){
                    call.setOpcode(Opcodes.INVOKESTATIC);call.owner=BRIDGE;call.name="itemIcon";
                    call.desc="(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/util/IIcon;";
                }
            }
        boolean gatePatched = false, speechPatched = false, itemPatched = false;
        for (AbstractInsnNode instruction = copy.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (!gatePatched && instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                AbstractInsnNode next = instruction.getNext();
                if ("java/util/List".equals(call.owner) && "isEmpty".equals(call.name)
                        && next instanceof JumpInsnNode && next.getOpcode() == Opcodes.IFNE) {
                    copy.instructions.insertBefore(next, new org.objectweb.asm.tree.InsnNode(Opcodes.POP));
                    copy.instructions.remove(next);
                    gatePatched = true;
                }
            }
            if (!speechPatched && instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)instruction;
                AbstractInsnNode next = instruction.getNext();
                if ("lotr/client/LOTRSpeechClient".equals(call.owner) && "hasSpeech".equals(call.name)
                        && next instanceof JumpInsnNode && next.getOpcode() == Opcodes.IFNE) {
                    copy.instructions.insertBefore(next, new org.objectweb.asm.tree.InsnNode(Opcodes.POP));
                    copy.instructions.remove(next);
                    speechPatched = true;
                }
            }
            if (!itemPatched && instruction instanceof org.objectweb.asm.tree.FieldInsnNode) {
                org.objectweb.asm.tree.FieldInsnNode field = (org.objectweb.asm.tree.FieldInsnNode)instruction;
                if ("lotr/common/LOTRMod".equals(field.owner) && "redBook".equals(field.name)) {
                    for (AbstractInsnNode cursor = instruction.getNext(); cursor != null; cursor = cursor.getNext()) {
                        if (cursor instanceof VarInsnNode && cursor.getOpcode() == Opcodes.ASTORE) {
                            int local = ((VarInsnNode)cursor).var;
                            InsnList replacement = new InsnList();
                            replacement.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE,
                                "relationshipIconForNative", "()Lnet/minecraft/item/ItemStack;", false));
                            replacement.add(new VarInsnNode(Opcodes.ASTORE, local));
                            copy.instructions.insert(cursor, replacement);
                            itemPatched = true;
                            break;
                        }
                    }
                }
            }
        }
        if (!gatePatched || !speechPatched || !itemPatched)
            throw new IllegalStateException("LOTR v36.15 accepted-miniquest renderer fingerprint changed");
        return copy;
    }

    private static void injectQuestGuard(MethodNode method) {
        for (AbstractInsnNode instruction=method.instructions.getFirst();instruction!=null;instruction=instruction.getNext())
            if (instruction instanceof MethodInsnNode && QUEST_GUARD.equals(((MethodInsnNode)instruction).owner)) return;
        for(AbstractInsnNode instruction=method.instructions.getFirst();instruction!=null;){AbstractInsnNode next=instruction.getNext();
            if(instruction.getOpcode()==Opcodes.IRETURN){InsnList guard=new InsnList();guard.add(new VarInsnNode(Opcodes.ALOAD,0));guard.add(new VarInsnNode(Opcodes.ALOAD,1));guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC,QUEST_GUARD,"allowOffer","(Llotr/common/entity/npc/LOTREntityQuestInfo;Lnet/minecraft/entity/player/EntityPlayer;)Z",false));guard.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IAND));method.instructions.insertBefore(instruction,guard);}instruction=next;}
    }

    private static void injectBeforeReturns(MethodNode method, InsnList template) {
        if (containsBridge(method)) return;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; ) {
            AbstractInsnNode next = instruction.getNext();
            if (instruction.getOpcode() == Opcodes.RETURN)
                method.instructions.insertBefore(instruction, clone(template));
            instruction = next;
        }
    }

    private static boolean containsBridge(MethodNode method) {
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext())
            if (instruction instanceof MethodInsnNode && BRIDGE.equals(((MethodInsnNode)instruction).owner)) return true;
        return false;
    }

    private static void injectBeforeFinalReturn(MethodNode method, InsnList template) {
        if (containsBridge(method)) return;
        for (AbstractInsnNode instruction = method.instructions.getLast(); instruction != null;
                instruction = instruction.getPrevious()) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                method.instructions.insertBefore(instruction, clone(template));
                return;
            }
        }
        throw new IllegalStateException("LOTR v36.15 visual rendering method has no final return: " + method.name);
    }

    private static InsnList overheadCall() {
        InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new VarInsnNode(Opcodes.DLOAD, 1));
        call.add(new VarInsnNode(Opcodes.DLOAD, 3));
        call.add(new VarInsnNode(Opcodes.DLOAD, 5));
        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "renderRelationshipMarker",
            "(Llotr/common/entity/npc/LOTREntityNPC;DDD)V", false));
        return call;
    }

    private static InsnList mapCall() {
        InsnList call = new InsnList();
        call.add(new VarInsnNode(Opcodes.ALOAD, 0));
        call.add(new VarInsnNode(Opcodes.ALOAD, 1));
        call.add(new VarInsnNode(Opcodes.ILOAD, 2));
        call.add(new VarInsnNode(Opcodes.ILOAD, 3));
        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BRIDGE, "renderMapMarkers",
            "(Ljava/lang/Object;Lnet/minecraft/entity/player/EntityPlayer;II)V", false));
        return call;
    }

    private static InsnList clone(InsnList source) {
        InsnList copy = new InsnList();
        for (AbstractInsnNode instruction = source.getFirst(); instruction != null; instruction = instruction.getNext())
            copy.add(instruction.clone(new java.util.HashMap<org.objectweb.asm.tree.LabelNode,org.objectweb.asm.tree.LabelNode>()));
        return copy;
    }
}
