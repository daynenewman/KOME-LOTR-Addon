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
        MethodNode widgets = method(node,"renderMapWidgets","renderMapWidgets","(II)V");
        MethodNode quests = method(node,"renderMiniQuests","renderMiniQuests","(Lnet/minecraft/entity/player/EntityPlayer;II)V");
        MethodNode players = method(node,"renderPlayers","renderPlayers","(II)V");
        if (hasHook(wheel,"mapMouseWheel") && hasHook(mouse, "onMapSelectorMouseClicked") && hasHook(mouse, "onMapMouseClicked") && hasHook(mouse, "onNativeWaypointClick") && hasHook(key, "onMapKeyTyped") && hasHook(draw, "isMapDragButtonDown") && hasHook(tooltip, "suppressNativeWaypointTooltip") && hasHook(widgets,"allowNativeWidgetTooltip") && hasHook(quests,"allowNativeQuestTooltip") && hasHook(players,"allowNativePlayerTooltip")) return bytes;
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
        if (!hasHook(tooltip, "suppressNativeWaypointTooltip")) {
            // The native boolean distinguishes selected/click-opened information from hover text.
            InsnList call = new InsnList(); call.add(new VarInsnNode(Opcodes.ALOAD, 0));
            call.add(new VarInsnNode(Opcodes.ALOAD,1));call.add(new VarInsnNode(Opcodes.ILOAD,2));
            call.add(new VarInsnNode(Opcodes.ILOAD,3));call.add(new VarInsnNode(Opcodes.ILOAD,4));
            call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "suppressNativeWaypointTooltip", "(Llotr/client/gui/LOTRGuiMap;Llotr/common/world/map/LOTRAbstractWaypoint;ZII)Z", false));
            consumeOrContinue(tooltip, call);
        }
        if(!hasHook(widgets,"allowNativeWidgetTooltip")){
            MethodInsnNode help=null;
            for(AbstractInsnNode insn=widgets.instructions.getFirst();insn!=null;insn=insn.getNext())
                if(insn instanceof MethodInsnNode){MethodInsnNode m=(MethodInsnNode)insn;
                    if("func_146283_a".equals(m.name)&&"(Ljava/util/List;II)V".equals(m.desc)){
                        if(help!=null)throw new IllegalStateException("LOTR map widget help ambiguous");help=m;
                    }
                }
            if(help==null)throw new IllegalStateException("LOTR v36.15 widget help missing");
            AbstractInsnNode start=previous(previous(previous(previous(help))));
            if(!(start instanceof VarInsnNode)||start.getOpcode()!=Opcodes.ALOAD||((VarInsnNode)start).var!=0)
                throw new IllegalStateException("LOTR v36.15 widget help stack changed");
            LabelNode skip=new LabelNode();InsnList gate=new InsnList();
            gate.add(new VarInsnNode(Opcodes.ALOAD,0));gate.add(new VarInsnNode(Opcodes.ALOAD,3));
            gate.add(new VarInsnNode(Opcodes.ILOAD,1));gate.add(new VarInsnNode(Opcodes.ILOAD,2));
            gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"allowNativeWidgetTooltip","(Llotr/client/gui/LOTRGuiMap;Llotr/client/gui/LOTRGuiMapWidget;II)Z",false));
            gate.add(new JumpInsnNode(Opcodes.IFEQ,skip));widgets.instructions.insertBefore(start,gate);
            widgets.instructions.insert(help,skip);
        }
        if(!hasHook(quests,"allowNativeQuestTooltip")){
            FieldInsnNode questName=null;AbstractInsnNode end=null,progressionDraw=null;
            for(AbstractInsnNode insn=quests.instructions.getFirst();insn!=null;insn=insn.getNext()){
                if(insn instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)insn;
                    if("lotr/common/quest/LOTRMiniQuest".equals(f.owner)&&"entityName".equals(f.name)){
                        if(questName!=null)throw new IllegalStateException("LOTR map quest hover is ambiguous");questName=f;
                    }
                }
                if(insn instanceof MethodInsnNode){MethodInsnNode m=(MethodInsnNode)insn;
                    if("kome/client/KOMEVisualRenderBridge".equals(m.owner)&&"renderMapMarkers".equals(m.name))
                        progressionDraw=insn;
                }
                if(insn.getOpcode()==Opcodes.RETURN)end=insn;
            }
            AbstractInsnNode start=questName==null?null:previous(questName);
            if(!(start instanceof VarInsnNode)||start.getOpcode()!=Opcodes.ALOAD||((VarInsnNode)start).var!=4||end==null)
                throw new IllegalStateException("LOTR v36.15 quest hover seam changed");
            // Keep progression icons on both paths regardless of transformer order;
            // only native quest hover text is skipped.
            boolean progressionAfterHover=progressionDraw!=null
                && quests.instructions.indexOf(progressionDraw)>quests.instructions.indexOf(start);
            AbstractInsnNode continuation=progressionAfterHover?progressionDraw:end;
            if(progressionAfterHover){
                for(int local=3;local>=0;local--){continuation=previous(continuation);
                    if(!(continuation instanceof VarInsnNode)||((VarInsnNode)continuation).var!=local
                            ||continuation.getOpcode()!=(local<2?Opcodes.ALOAD:Opcodes.ILOAD))
                        throw new IllegalStateException("KOME progression marker continuation changed");
                }
            }
            LabelNode skip=new LabelNode();quests.instructions.insertBefore(continuation,skip);
            InsnList gate=new InsnList();gate.add(new VarInsnNode(Opcodes.ALOAD,0));
            gate.add(new VarInsnNode(Opcodes.ALOAD,4));gate.add(new VarInsnNode(Opcodes.ILOAD,2));
            gate.add(new VarInsnNode(Opcodes.ILOAD,3));
            gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"allowNativeQuestTooltip",
                "(Llotr/client/gui/LOTRGuiMap;Llotr/common/quest/LOTRMiniQuest;II)Z",false));
            gate.add(new JumpInsnNode(Opcodes.IFEQ,skip));quests.instructions.insertBefore(start,gate);
        }
        if(!hasHook(players,"allowNativePlayerTooltip")){
            FieldInsnNode loading=null;AbstractInsnNode end=null;
            for(AbstractInsnNode insn=players.instructions.getFirst();insn!=null;insn=insn.getNext()){
                if(insn instanceof FieldInsnNode&&"loadingConquestGrid".equals(((FieldInsnNode)insn).name)){
                    if(loading!=null)throw new IllegalStateException("LOTR player hover gate is ambiguous");
                    loading=(FieldInsnNode)insn;
                }
                if(insn.getOpcode()==Opcodes.RETURN)end=insn;
            }
            AbstractInsnNode branch=loading==null?null:loading.getNext();
            while(branch!=null&&branch.getOpcode()<0)branch=branch.getNext();
            if(!(branch instanceof JumpInsnNode)||branch.getOpcode()!=Opcodes.IFNE||end==null)
                throw new IllegalStateException("LOTR v36.15 player hover seam changed");
            AbstractInsnNode start=branch.getNext();while(start!=null&&start.getOpcode()<0)start=start.getNext();
            if(!(start instanceof VarInsnNode)||start.getOpcode()!=Opcodes.ALOAD||((VarInsnNode)start).var!=0)
                throw new IllegalStateException("LOTR v36.15 player hover body changed");
            LabelNode skip=new LabelNode();players.instructions.insertBefore(end,skip);
            InsnList gate=new InsnList();gate.add(new VarInsnNode(Opcodes.ALOAD,0));
            gate.add(new VarInsnNode(Opcodes.ALOAD,3));gate.add(new VarInsnNode(Opcodes.ILOAD,1));
            gate.add(new VarInsnNode(Opcodes.ILOAD,2));
            gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,"allowNativePlayerTooltip",
                "(Llotr/client/gui/LOTRGuiMap;Ljava/lang/String;II)Z",false));
            gate.add(new JumpInsnNode(Opcodes.IFEQ,skip));players.instructions.insertBefore(start,gate);
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
