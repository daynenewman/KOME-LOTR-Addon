package kome.core;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Narrow v36.15 presentation/namespace hooks; original bodies remain intact for all native inputs. */
public final class KOMEPublicWaypointTransformer implements IClassTransformer {
    static final String BRIDGE="kome/common/data/KOMEPublicWaypointBridge";
    static final String CLIENT="kome/client/KOMEPublicWaypointMapHooks";
    private static volatile boolean playerInstalled,requestInstalled,bounceInstalled;
    @Override public byte[] transform(String name,String transformedName,byte[] bytes) {
        if(bytes==null) return null;
        if(!"lotr.common.LOTRPlayerData".equals(transformedName)
                && !"lotr.common.network.LOTRPacketFastTravel$Handler".equals(transformedName)
                && !"lotr.common.network.LOTRPacketFTBounceServer$Handler".equals(transformedName)
                && !"lotr.client.gui.LOTRGuiMap".equals(transformedName)) return bytes;
        ClassNode c=new ClassNode(); new ClassReader(bytes).accept(c,0); boolean changed=false;
        if("lotr.common.LOTRPlayerData".equals(transformedName)) {
            MethodNode list=method(c,"getAllAvailableWaypoints","()Ljava/util/List;");
            if(!hook(list,BRIDGE,"compose")) {
                expect(callCount(list,"lotr/common/world/map/LOTRWaypoint","listAllWaypoints","()Ljava/util/List;")==1,
                    "native waypoint list source changed");
                int adds=callCount(list,"java/util/List","addAll","(Ljava/util/Collection;)Z");
                expect(adds==2 && callCount(list,"java/util/ArrayList","<init>","(Ljava/util/Collection;)V")==1
                    || adds==3 && callCount(list,"java/util/ArrayList","<init>","()V")==1,"native list composition changed");
                AbstractInsnNode ret=singleOpcode(list,Opcodes.ARETURN);
                InsnList extra=new InsnList(); extra.add(new VarInsnNode(Opcodes.ALOAD,0));
                extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"compose","(Ljava/util/List;Llotr/common/LOTRPlayerData;)Ljava/util/List;",false));
                list.instructions.insertBefore(ret,extra); changed=true;
            }
            MethodNode lookup=method(c,"getSharedCustomWaypointByID","(Ljava/util/UUID;I)Llotr/common/world/map/LOTRCustomWaypoint;");
            if(!hook(lookup,BRIDGE,"lookup")) {
                expect(callCount(lookup,"lotr/common/world/map/LOTRCustomWaypoint","getSharingPlayerID","()Ljava/util/UUID;")==1,
                    "native shared lookup changed");
                LabelNode nativePath=new LabelNode(); InsnList extra=new InsnList();
                extra.add(new VarInsnNode(Opcodes.ALOAD,1));
                extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"isPublicOwner","(Ljava/util/UUID;)Z",false));
                extra.add(new JumpInsnNode(Opcodes.IFEQ,nativePath)); extra.add(new VarInsnNode(Opcodes.ALOAD,0));
                extra.add(new VarInsnNode(Opcodes.ALOAD,1)); extra.add(new VarInsnNode(Opcodes.ILOAD,2));
                extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"lookup","(Llotr/common/LOTRPlayerData;Ljava/util/UUID;I)Llotr/common/world/map/LOTRCustomWaypoint;",false));
                extra.add(new InsnNode(Opcodes.ARETURN)); extra.add(nativePath); extra.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                lookup.instructions.insert(extra); changed=true;
            }
            playerInstalled=true;
        } else if("lotr.common.network.LOTRPacketFastTravel$Handler".equals(transformedName)) {
            MethodNode request=method(c,"onMessage","(Llotr/common/network/LOTRPacketFastTravel;Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)Lcpw/mods/fml/common/network/simpleimpl/IMessage;");
            if(!hook(request,BRIDGE,"handleRequest")) {
                expect(callCount(request,"lotr/common/LOTRPlayerData","setTargetFTWaypoint","(Llotr/common/world/map/LOTRAbstractWaypoint;)V")==1,
                    "native request target assignment changed");
                LabelNode nativePath=new LabelNode(); InsnList extra=new InsnList();
                extra.add(new VarInsnNode(Opcodes.ALOAD,1)); extra.add(new VarInsnNode(Opcodes.ALOAD,2));
                extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"handleRequest",
                    "(Llotr/common/network/LOTRPacketFastTravel;Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)Z",false));
                extra.add(new JumpInsnNode(Opcodes.IFEQ,nativePath)); extra.add(new InsnNode(Opcodes.ACONST_NULL));
                extra.add(new InsnNode(Opcodes.ARETURN)); extra.add(nativePath); extra.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                request.instructions.insert(extra); changed=true;
            }
            requestInstalled=true;
        } else if("lotr.common.network.LOTRPacketFTBounceServer$Handler".equals(transformedName)) {
            MethodNode bounce=method(c,"onMessage","(Llotr/common/network/LOTRPacketFTBounceServer;Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)Lcpw/mods/fml/common/network/simpleimpl/IMessage;");
            if(!hook(bounce,BRIDGE,"handleBounce")) {
                expect(callCount(bounce,"lotr/common/LOTRPlayerData","receiveFTBouncePacket","()V")==1,"native completion path changed");
                LabelNode nativePath=new LabelNode(); InsnList extra=new InsnList(); extra.add(new VarInsnNode(Opcodes.ALOAD,2));
                extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC,BRIDGE,"handleBounce","(Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)Z",false));
                extra.add(new JumpInsnNode(Opcodes.IFEQ,nativePath)); extra.add(new InsnNode(Opcodes.ACONST_NULL));
                extra.add(new InsnNode(Opcodes.ARETURN)); extra.add(nativePath); extra.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                bounce.instructions.insert(extra); changed=true;
            }
            bounceInstalled=true;
        } else {
            for(String field:new String[]{"selectedWaypoint","widgetDelCWP","widgetRenameCWP","widgetShareCWP","widgetHideSWP","widgetUnhideSWP","mapWidgets"})
                expect(hasField(c,field,field.equals("selectedWaypoint")?"Llotr/common/world/map/LOTRAbstractWaypoint;"
                    :field.equals("mapWidgets")?"Ljava/util/List;":"Llotr/client/gui/LOTRGuiMapWidget;",field.equals("mapWidgets")),
                    "native map field type/static fingerprint changed: "+field);
            MethodNode widgets=method(c,"renderMapWidgets","(II)V");
            if(!hook(widgets,CLIENT,"beforeWidgets")) {
                AbstractInsnNode load=null;
                for(AbstractInsnNode n=widgets.instructions.getFirst();n!=null;n=n.getNext()) if(n instanceof FieldInsnNode) {
                    FieldInsnNode field=(FieldInsnNode)n;
                    if(field.getOpcode()==Opcodes.GETSTATIC && "lotr/client/gui/LOTRGuiMap".equals(field.owner) && "mapWidgets".equals(field.name) && "Ljava/util/List;".equals(field.desc)) {
                        expect(load==null,"multiple map widget loops"); load=n;
                    }
                }
                expect(load!=null,"native map widget loop absent");
                // Both verified jars use the static mapWidgets list. Inject immediately before its load.
                AbstractInsnNode before=load;
                InsnList extra=new InsnList(); extra.add(new VarInsnNode(Opcodes.ALOAD,0));
                extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC,CLIENT,"beforeWidgets","(Llotr/client/gui/LOTRGuiMap;)V",false));
                widgets.instructions.insertBefore(before,extra); changed=true;
            }
            MethodNode visible=method(c,"isWaypointVisible","(Llotr/common/world/map/LOTRAbstractWaypoint;)Z");
            if(!hook(visible,CLIENT,"isPublicVisible")) {
                expect(callCount(visible,"lotr/common/world/map/LOTRCustomWaypoint","isSharedHidden","()Z")==1,"native map visibility changed");
                LabelNode nativePath=new LabelNode(); InsnList extra=new InsnList(); extra.add(new VarInsnNode(Opcodes.ALOAD,1));
                extra.add(new TypeInsnNode(Opcodes.INSTANCEOF,"kome/common/data/KOMEPublicWaypointAdapter"));
                extra.add(new JumpInsnNode(Opcodes.IFEQ,nativePath)); extra.add(new VarInsnNode(Opcodes.ALOAD,1));
                extra.add(new MethodInsnNode(Opcodes.INVOKESTATIC,CLIENT,"isPublicVisible","(Llotr/common/world/map/LOTRAbstractWaypoint;)Z",false));
                extra.add(new InsnNode(Opcodes.IRETURN)); extra.add(nativePath); extra.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));
                visible.instructions.insert(extra); changed=true;
            }
        }
        if(!changed) return bytes;
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS); c.accept(writer); return writer.toByteArray();
    }
    public static void requireInstalled(ClassLoader loader) {
        // LOTRPlayerData may remain unloaded until the first player joins. Force definition,
        // without static initialization, so the transformer validates every server boundary.
        try {
            for(String name:new String[]{"lotr.common.LOTRPlayerData",
                    "lotr.common.network.LOTRPacketFastTravel$Handler",
                    "lotr.common.network.LOTRPacketFTBounceServer$Handler"})
                Class.forName(name,false,loader);
        } catch(ClassNotFoundException absent) {
            throw new IllegalStateException("Required v36.15 public waypoint target absent",absent);
        }
        expect(playerInstalled && requestInstalled && bounceInstalled,
            "public waypoint hooks were not installed (player="+playerInstalled+", request="+requestInstalled
                +", completion="+bounceInstalled+"); refusing incomplete server integration");
    }
    private static MethodNode method(ClassNode c,String name,String desc) {
        MethodNode result=null;
        for(MethodNode m:c.methods) if(m.name.equals(name) && m.desc.equals(desc)) { expect(result==null,"duplicate target "+name); result=m; }
        expect(result!=null,"v36.15 target absent: "+name+desc); return result;
    }
    private static boolean hasField(ClassNode c,String name,String desc,boolean isStatic){
        for(FieldNode f:c.fields) if(f.name.equals(name)) return f.desc.equals(desc) && ((f.access&Opcodes.ACC_STATIC)!=0)==isStatic; return false;
    }
    private static boolean hook(MethodNode m,String owner,String name) {
        int count=0; for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) if(n instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)n; if(call.owner.equals(owner) && call.name.equals(name)) {
                String expected=name.equals("compose")?"(Ljava/util/List;Llotr/common/LOTRPlayerData;)Ljava/util/List;"
                    :name.equals("lookup")?"(Llotr/common/LOTRPlayerData;Ljava/util/UUID;I)Llotr/common/world/map/LOTRCustomWaypoint;"
                    :name.equals("handleRequest")?"(Llotr/common/network/LOTRPacketFastTravel;Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)Z"
                    :name.equals("handleBounce")?"(Lcpw/mods/fml/common/network/simpleimpl/MessageContext;)Z"
                    :name.equals("beforeWidgets")?"(Llotr/client/gui/LOTRGuiMap;)V":"(Llotr/common/world/map/LOTRAbstractWaypoint;)Z";
                expect(call.getOpcode()==Opcodes.INVOKESTATIC && call.desc.equals(expected),"incompatible existing public hook "+name); count++;
            }
        }
        expect(count<=1,"duplicate public hook "+name); return count==1;
    }
    private static int callCount(MethodNode m,String owner,String name,String desc) {
        int count=0; for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) if(n instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)n; if(call.owner.equals(owner) && call.name.equals(name) && call.desc.equals(desc)) count++;
        } return count;
    }
    private static AbstractInsnNode singleOpcode(MethodNode m,int opcode) {
        AbstractInsnNode result=null;
        for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) if(n.getOpcode()==opcode) { expect(result==null,"multiple native returns"); result=n; }
        expect(result!=null,"native return absent"); return result;
    }
    private static void expect(boolean condition,String message){ if(!condition) throw new IllegalStateException("KOME public waypoint compatibility: "+message); }
}
