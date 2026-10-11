package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Narrow client-only LOTR v36.15 input/inspection seams. Forge 1.7.10 has no GUI mouse/key pre-events. */
public final class KOMEMapInputTransformer implements IClassTransformer {
    static final String TARGET = "lotr.client.gui.LOTRGuiMap";
    static final String HOOK = "kome/client/KOMEConquestMapOverlay";
    @Override public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
        MethodNode mouse = method(node, "mouseClicked", "func_73864_a", "(III)V");
        MethodNode wheel = method(node, "handleMouseInput", "func_146274_d", "()V");
        MethodNode key = method(node, "keyTyped", "func_73869_a", "(CI)V");
        MethodNode draw = method(node, "drawScreen", "func_73863_a", "(IIF)V");
        MethodNode tooltip = method(node, "renderWaypointTooltip", "renderWaypointTooltip", "(Llotr/common/world/map/LOTRAbstractWaypoint;ZII)V");
        if (hasHook(wheel,"mapMouseWheel") && hasHook(mouse, "onMapSelectorMouseClicked") && hasHook(mouse, "onMapMouseClicked") && hasHook(mouse, "onNativeWaypointClick") && hasHook(key, "onMapKeyTyped") && hasHook(draw, "isMapDragButtonDown") && hasHook(tooltip, "suppressMapInspection")) return bytes;
        if (!hasHook(mouse, "onMapSelectorMouseClicked")) {
            InsnList call = new InsnList(); call.add(new VarInsnNode(Opcodes.ALOAD, 0));
            for (int local = 1; local <= 3; local++) call.add(new VarInsnNode(Opcodes.ILOAD, local));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "onMapSelectorMouseClicked", "(Llotr/client/gui/LOTRGuiMap;III)Z", false));
            consumeOrContinue(mouse, call);
        }
        if (!hasHook(mouse, "onMapMouseClicked")) {
            InsnList call = new InsnList(); call.add(new VarInsnNode(Opcodes.ALOAD, 0));
            for (int local = 1; local <= 3; local++) call.add(new VarInsnNode(Opcodes.ILOAD, local));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "onMapMouseClicked", "(Llotr/client/gui/LOTRGuiMap;III)Z", false));
            consumeOrContinue(mouse, call, nativeBaseClickStart(mouse));
        }
        if (!hasHook(mouse, "onNativeWaypointClick")) {
            FieldInsnNode selected = null;
            for (AbstractInsnNode insn = mouse.instructions.getFirst(); insn != null; insn = insn.getNext())
                if (insn instanceof FieldInsnNode && insn.getOpcode() == Opcodes.PUTFIELD) {
                    FieldInsnNode field = (FieldInsnNode)insn;
                    if (node.name.equals(field.owner) && "selectedWaypoint".equals(field.name)
                            && "Llotr/common/world/map/LOTRAbstractWaypoint;".equals(field.desc)
                            && previous(insn).getOpcode() == Opcodes.ALOAD) {
                        if (selected != null) throw new IllegalStateException("LOTR map waypoint picker is ambiguous");
                        selected = field;
                    }
                }
            if (selected == null) throw new IllegalStateException("LOTR v36.15 waypoint picker assignment missing");
            InsnList call = new InsnList(); call.add(new VarInsnNode(Opcodes.ALOAD, 0));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "onNativeWaypointClick", "(Llotr/client/gui/LOTRGuiMap;)V", false));
            mouse.instructions.insert(selected, call);
        }
        if (!hasHook(wheel,"mapMouseWheel")) {
            MethodInsnNode poll=null;
            for (AbstractInsnNode insn=wheel.instructions.getFirst();insn!=null;insn=insn.getNext())
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call=(MethodInsnNode)insn;
                    if ("org/lwjgl/input/Mouse".equals(call.owner) && "getEventDWheel".equals(call.name) && "()I".equals(call.desc)) {
                        if (poll!=null) throw new IllegalStateException("LOTR map wheel poll is ambiguous");
                        poll=call;
                    }
                }
            if (poll==null) throw new IllegalStateException("LOTR v36.15 map wheel poll missing");
            wheel.instructions.insertBefore(poll,new VarInsnNode(Opcodes.ALOAD,0));
            poll.owner=HOOK;poll.name="mapMouseWheel";poll.desc="(Llotr/client/gui/LOTRGuiMap;)I";
        }
        if (!hasHook(key, "onMapKeyTyped")) {
            InsnList call = new InsnList(); call.add(new VarInsnNode(Opcodes.ALOAD, 0)); call.add(new VarInsnNode(Opcodes.ILOAD, 2));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "onMapKeyTyped", "(Llotr/client/gui/LOTRGuiMap;I)Z", false));
            consumeOrContinue(key, call);
        }
        if (!hasHook(draw, "isMapDragButtonDown")) {
            MethodInsnNode poll = null;
            for (AbstractInsnNode insn = draw.instructions.getFirst(); insn != null; insn = insn.getNext())
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode)insn;
                    if ("org/lwjgl/input/Mouse".equals(call.owner) && "isButtonDown".equals(call.name) && "(I)Z".equals(call.desc)) {
                        if (poll != null) throw new IllegalStateException("LOTR map drag poll is ambiguous");
                        poll = call;
                    }
                }
            if (poll == null) throw new IllegalStateException("LOTR v36.15 map drag poll missing");
            draw.instructions.insertBefore(poll, new VarInsnNode(Opcodes.ALOAD, 0));
            poll.owner = HOOK; poll.name = "isMapDragButtonDown"; poll.desc = "(ILlotr/client/gui/LOTRGuiMap;)Z";
        }
        if (!hasHook(tooltip, "suppressMapInspection")) {
            // Hide only the information block. Native waypoint markers, selection, and travel are untouched.
            InsnList call = new InsnList(); call.add(new VarInsnNode(Opcodes.ALOAD, 0));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "suppressMapInspection", "(Llotr/client/gui/LOTRGuiMap;)Z", false));
            consumeOrContinue(tooltip, call);
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
    private static MethodNode method(ClassNode node, String mcp, String srg, String desc) {
        MethodNode result = null;
        for (MethodNode method : node.methods) if ((mcp.equals(method.name) || srg.equals(method.name)) && desc.equals(method.desc)) {
            if (result != null) throw new IllegalStateException("Ambiguous LOTR map method " + mcp);
            result = method;
        }
        if (result == null) throw new IllegalStateException("LOTR v36.15 map method missing: " + mcp);
        return result;
    }
    private static boolean hasHook(MethodNode method, String name) {
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext())
            if (insn instanceof MethodInsnNode && HOOK.equals(((MethodInsnNode)insn).owner) && name.equals(((MethodInsnNode)insn).name)) return true;
        return false;
    }
    /** Tile context opening stays after native picking; actual radial cells are owned by the entry hook. */
    private static AbstractInsnNode nativeBaseClickStart(MethodNode mouse) {
        AbstractInsnNode start = null;
        for (AbstractInsnNode insn = mouse.instructions.getFirst(); insn != null; insn = insn.getNext())
            if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)insn;
                if (call.getOpcode() != Opcodes.INVOKESPECIAL || !mouse.name.equals(call.name) || !"(III)V".equals(call.desc)) continue;
                if (start != null) throw new IllegalStateException("LOTR map base click call is ambiguous");
                AbstractInsnNode cursor = insn;
                for (int local = 3; local >= 0; local--) {
                    cursor = previous(cursor);
                    if (!(cursor instanceof VarInsnNode) || ((VarInsnNode)cursor).var != local
                            || cursor.getOpcode() != (local == 0 ? Opcodes.ALOAD : Opcodes.ILOAD))
                        throw new IllegalStateException("LOTR v36.15 base click arguments changed");
                }
                start = cursor;
            }
        if (start == null) throw new IllegalStateException("LOTR v36.15 base click call missing");
        return start;
    }
    private static AbstractInsnNode previous(AbstractInsnNode n) {
        do { n = n.getPrevious(); } while (n != null && n.getOpcode() < 0);
        return n;
    }
    private static void consumeOrContinue(MethodNode method, InsnList call) { consumeOrContinue(method, call, null); }
    private static void consumeOrContinue(MethodNode method, InsnList call, AbstractInsnNode before) {
        LabelNode nativePath = new LabelNode();
        call.add(new JumpInsnNode(Opcodes.IFEQ, nativePath)); call.add(new InsnNode(Opcodes.RETURN));
        call.add(nativePath); call.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        if (before == null) method.instructions.insert(call); else method.instructions.insertBefore(before, call);
    }
}
