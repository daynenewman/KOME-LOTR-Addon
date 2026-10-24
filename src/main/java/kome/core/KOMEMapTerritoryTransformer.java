package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Optional v36.15 presentation seam. Reject changed layouts without changing native drawing. */
public final class KOMEMapTerritoryTransformer implements IClassTransformer {
    private static final String TARGET="lotr.client.gui.LOTRGuiMap", OWNER=TARGET.replace('.','/');
    private static final String HOOK="kome/client/KOMEConquestMapOverlay", NAME="onBeforeNativeMapLabels";
    private static final String DESC="(Llotr/client/gui/LOTRGuiMap;II)V";
    private boolean warned;

    @Override public byte[] transform(String name,String transformedName,byte[] bytes) {
        if(bytes==null || !TARGET.equals(transformedName))return bytes;
        try {
            ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);
            if(!OWNER.equals(node.name))throw incompatible("class identity");
            MethodNode draw=null,labels=null;
            for(MethodNode method:node.methods) {
                if((method.name.equals("drawScreen") || method.name.equals("func_73863_a")) && method.desc.equals("(IIF)V")) {
                    if(draw!=null)throw incompatible("ambiguous drawScreen");draw=method;
                }
                if(method.name.equals("renderLabels") && method.desc.equals("()V"))labels=method;
            }
            if(draw==null || (draw.access&Opcodes.ACC_STATIC)!=0 || labels==null
                    || (labels.access&Opcodes.ACC_PRIVATE)==0 || (labels.access&Opcodes.ACC_STATIC)!=0)
                throw incompatible("drawScreen/private labels method");
            for(String field:new String[]{"posX","posY","zoomScale","zoomScaleStable"})requireField(node,field,"F",false);
            requireField(node,"zoomTicks","I",false);
            for(String field:new String[]{"mapXMin","mapXMax","mapYMin","mapYMax"})requireField(node,field,"I",true);

            MethodInsnNode label=null,compass=null,existing=null;
            for(AbstractInsnNode step:draw.instructions.toArray())if(step instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)step;
                if(call.name.equals("renderLabels")) {
                    if(label!=null || !nativeCall(call,"renderLabels","()V"))throw incompatible("ambiguous labels call");label=call;
                }
                if(call.name.equals("drawMapCompassBottomLeft")) {
                    if(compass!=null || call.getOpcode()!=Opcodes.INVOKESTATIC || !call.owner.equals("lotr/client/LOTRTextures")
                            || !call.desc.equals("(DDDD)V"))throw incompatible("ambiguous compass call");compass=call;
                }
                if(call.owner.equals(HOOK) && call.name.equals(NAME)) {
                    if(existing!=null || call.getOpcode()!=Opcodes.INVOKESTATIC || !call.desc.equals(DESC))throw incompatible("existing territory hook");existing=call;
                }
            }
            if(label==null || compass==null)throw incompatible("labels/compass call missing");
            AbstractInsnNode labelReceiver=previous(label);requireLoad(labelReceiver,Opcodes.ALOAD,0);
            requireWaypoint(previous(labelReceiver),0); // Keep the original pass 0 / labels / pass 1 sequence intact.
            AbstractInsnNode receiver=compassStart(compass),insertion=receiver;
            AbstractInsnNode before=previous(receiver);
            if(existing!=null) {
                if(before!=existing)throw incompatible("misplaced territory hook");
                before=previous(before);requireLoad(before,Opcodes.ILOAD,2);
                before=previous(before);requireLoad(before,Opcodes.ILOAD,1);
                before=previous(before);requireLoad(before,Opcodes.ALOAD,0);
                insertion=before;before=previous(before);
            }
            AbstractInsnNode after=next(label);requireLoad(after,Opcodes.ALOAD,0);
            after=next(after);requireOpcode(after,Opcodes.ICONST_1);
            after=next(after);requireLoad(after,Opcodes.ILOAD,1);
            after=next(after);requireLoad(after,Opcodes.ILOAD,2);
            after=next(after);
            if(!(after instanceof MethodInsnNode) || !nativeCall((MethodInsnNode)after,"renderWaypoints","(III)V"))throw incompatible("waypoint pass 1");
            MethodInsnNode roads=uniqueNativeCall(draw,"renderRoads","()V");
            MethodInsnNode players=uniqueNativeCall(draw,"renderPlayers","(II)V");
            int waypoints=0;for(AbstractInsnNode step:draw.instructions.toArray())if(step instanceof MethodInsnNode
                    && ((MethodInsnNode)step).name.equals("renderWaypoints"))waypoints++;
            if(waypoints!=2 || draw.instructions.indexOf(players)>=draw.instructions.indexOf(labelReceiver))throw incompatible("marker pass order");
            validateCompassExit(compass,roads,players);
            validateContext(draw,insertion);
            validateContext(draw,labelReceiver); // Keep the original guarantee against bypassing native labels.
            if(existing!=null)return bytes;
            InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));
            hook.add(new VarInsnNode(Opcodes.ILOAD,1));hook.add(new VarInsnNode(Opcodes.ILOAD,2));
            hook.add(new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,NAME,DESC,false));
            draw.instructions.insertBefore(receiver,hook);
            ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
        } catch(RuntimeException failure) {
            if(!warned){warned=true;java.util.logging.Logger.getLogger("KOME").warning("Native map label ordering unavailable; keeping Post territory fallback: "+failure);}
            return bytes; // No partial write; native labels and all earlier transformer changes survive.
        }
    }

    /** Earliest common terrain exit: before zLevel +50 and compass, with an empty operand stack.
     * Match the native arguments and balanced zLevel adjustment, not a byte offset or local table. */
    private static AbstractInsnNode compassStart(MethodInsnNode compass) {
        AbstractInsnNode[] args=new AbstractInsnNode[18];AbstractInsnNode step=compass;
        for(int i=args.length-1;i>=0;i--){step=previous(step);args[i]=step;}
        requireLoad(args[0],Opcodes.ALOAD,0);requireOpcode(args[1],Opcodes.DUP);
        requireZLevel(args[2],Opcodes.GETFIELD);String z=((FieldInsnNode)args[2]).name;
        requireFifty(args[3]);requireOpcode(args[4],Opcodes.FADD);requireField(args[5],Opcodes.PUTFIELD,z,"F");
        requireField(args[6],Opcodes.GETSTATIC,"mapXMin","I");requireTwelve(args[7]);requireOpcode(args[8],Opcodes.IADD);requireOpcode(args[9],Opcodes.I2D);
        requireField(args[10],Opcodes.GETSTATIC,"mapYMax","I");requireTwelve(args[11]);requireOpcode(args[12],Opcodes.ISUB);requireOpcode(args[13],Opcodes.I2D);
        requireLoad(args[14],Opcodes.ALOAD,0);requireField(args[15],Opcodes.GETFIELD,z,"F");
        requireOpcode(args[16],Opcodes.F2D);requireOpcode(args[17],Opcodes.DCONST_1);
        return args[0];
    }
    private static void validateCompassExit(MethodInsnNode compass,MethodInsnNode roads,MethodInsnNode players) {
        AbstractInsnNode step=next(compass);requireLoad(step,Opcodes.ALOAD,0);step=next(step);requireOpcode(step,Opcodes.DUP);
        step=next(step);requireZLevel(step,Opcodes.GETFIELD);String z=((FieldInsnNode)step).name;
        requireField(previous(previous(previous(compass))),Opcodes.GETFIELD,z,"F");
        step=next(step);requireFifty(step);step=next(step);requireOpcode(step,Opcodes.FSUB);
        step=next(step);requireField(step,Opcodes.PUTFIELD,z,"F");step=next(step);requireLoad(step,Opcodes.ALOAD,0);
        if(next(step)!=roads)throw incompatible("roads after compass");
        step=next(roads);requireLoad(step,Opcodes.ALOAD,0);step=next(step);requireLoad(step,Opcodes.ILOAD,1);
        step=next(step);requireLoad(step,Opcodes.ILOAD,2);if(next(step)!=players)throw incompatible("players after roads");
    }
    private static MethodInsnNode uniqueNativeCall(MethodNode method,String name,String desc) {
        MethodInsnNode found=null;for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode
                && ((MethodInsnNode)step).name.equals(name)) {
            if(found!=null || !nativeCall((MethodInsnNode)step,name,desc))throw incompatible("ambiguous "+name);found=(MethodInsnNode)step;
        }
        if(found==null)throw incompatible("missing "+name);return found;
    }
    private static void requireZLevel(AbstractInsnNode step,int opcode) {
        requireOpcode(step,opcode);
        if(!(step instanceof FieldInsnNode))throw incompatible("compass zLevel field");
        String name=((FieldInsnNode)step).name;
        if(!name.equals("zLevel") && !name.equals("field_73735_i"))throw incompatible("compass zLevel name");
        requireField(step,opcode,name,"F");
    }
    private static void requireField(AbstractInsnNode step,int opcode,String name,String desc) {
        requireOpcode(step,opcode);
        if(!(step instanceof FieldInsnNode) || !((FieldInsnNode)step).owner.equals(OWNER)
                || !((FieldInsnNode)step).name.equals(name) || !((FieldInsnNode)step).desc.equals(desc))throw incompatible("compass field "+name);
    }
    private static void requireFifty(AbstractInsnNode step) {
        if(!(step instanceof LdcInsnNode) || !Float.valueOf(50).equals(((LdcInsnNode)step).cst))throw incompatible("compass zLevel delta");
    }
    private static void requireTwelve(AbstractInsnNode step) {
        if(!(step instanceof IntInsnNode) || step.getOpcode()!=Opcodes.BIPUSH || ((IntInsnNode)step).operand!=12)throw incompatible("compass inset");
    }

    private static void validateContext(MethodNode draw,AbstractInsnNode anchor) {
        int at=draw.instructions.indexOf(anchor),dimensions=-1,zoom=-1,terrain=-1,lastX=-1,lastY=-1;
        for(AbstractInsnNode step:draw.instructions.toArray()) {
            int index=draw.instructions.indexOf(step);
            if(step instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)step;
                if(nativeCall(call,"setupMapDimensions","()V"))dimensions=index;
                if(call.owner.equals(OWNER) && call.name.equals("setupZoomVariables") && call.desc.equals("(F)V"))zoom=index;
                if(call.owner.equals(OWNER) && call.name.equals("renderMapAndOverlay") && call.desc.equals("(ZFZ)V"))terrain=index;
            }
            if(step instanceof FieldInsnNode && step.getOpcode()==Opcodes.PUTFIELD && ((FieldInsnNode)step).owner.equals(OWNER)) {
                if(((FieldInsnNode)step).name.equals("posX"))lastX=index;
                if(((FieldInsnNode)step).name.equals("posY"))lastY=index;
            }
            if(step.getOpcode()==Opcodes.RETURN && index<at)throw incompatible("early draw return");
            if(step instanceof JumpInsnNode)checkEdge(draw,index,((JumpInsnNode)step).label,at);
            if(step instanceof TableSwitchInsnNode) {
                TableSwitchInsnNode jump=(TableSwitchInsnNode)step;checkEdge(draw,index,jump.dflt,at);
                for(LabelNode label:jump.labels)checkEdge(draw,index,label,at);
            }
            if(step instanceof LookupSwitchInsnNode) {
                LookupSwitchInsnNode jump=(LookupSwitchInsnNode)step;checkEdge(draw,index,jump.dflt,at);
                for(LabelNode label:jump.labels)checkEdge(draw,index,label,at);
            }
        }
        if(dimensions<0 || zoom<=dimensions || terrain<=zoom || terrain>=at || lastX<zoom || lastY<zoom || lastX>=terrain || lastY>=terrain)
            throw incompatible("native view setup/terrain order");
        for(TryCatchBlockNode handler:draw.tryCatchBlocks) {
            if(draw.instructions.indexOf(handler.start)<at && draw.instructions.indexOf(handler.end)>at)
                throw incompatible("exception region across labels seam");
            checkEdge(draw,draw.instructions.indexOf(handler.start),handler.handler,at);
        }
    }
    private static void checkEdge(MethodNode method,int from,LabelNode target,int anchor) {
        int to=method.instructions.indexOf(target);
        if((from<anchor && to>anchor) || (from>anchor && to<anchor))throw incompatible("control flow crossing labels seam");
    }
    private static void requireWaypoint(AbstractInsnNode step,int pass) {
        if(!(step instanceof MethodInsnNode) || !nativeCall((MethodInsnNode)step,"renderWaypoints","(III)V"))throw incompatible("waypoint pass 0");
        step=previous(step);requireLoad(step,Opcodes.ILOAD,2);step=previous(step);requireLoad(step,Opcodes.ILOAD,1);
        step=previous(step);requireOpcode(step,pass==0?Opcodes.ICONST_0:Opcodes.ICONST_1);
        requireLoad(previous(step),Opcodes.ALOAD,0);
    }
    private static boolean nativeCall(MethodInsnNode call,String name,String desc) {
        return call.getOpcode()==Opcodes.INVOKESPECIAL && call.owner.equals(OWNER) && call.name.equals(name) && call.desc.equals(desc);
    }
    private static void requireField(ClassNode node,String name,String desc,boolean isStatic) {
        for(FieldNode field:node.fields)if(field.name.equals(name) && field.desc.equals(desc) && ((field.access&Opcodes.ACC_STATIC)!=0)==isStatic)return;
        throw incompatible("view field "+name);
    }
    private static void requireLoad(AbstractInsnNode step,int opcode,int local) {
        requireOpcode(step,opcode);if(!(step instanceof VarInsnNode) || ((VarInsnNode)step).var!=local)throw incompatible("native arguments");
    }
    private static void requireOpcode(AbstractInsnNode step,int opcode){if(step==null || step.getOpcode()!=opcode)throw incompatible("native instruction sequence");}
    private static AbstractInsnNode previous(AbstractInsnNode step){do{step=step.getPrevious();}while(step!=null&&step.getOpcode()<0);return step;}
    private static AbstractInsnNode next(AbstractInsnNode step){do{step=step.getNext();}while(step!=null&&step.getOpcode()<0);return step;}
    private static IllegalStateException incompatible(String reason){return new IllegalStateException("LOTR v36.15 territory/labels fingerprint: "+reason);}
}
