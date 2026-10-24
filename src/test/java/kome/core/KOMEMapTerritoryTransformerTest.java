package kome.core;

import java.io.*;
import java.util.*;
import java.util.zip.ZipFile;
import net.minecraft.launchwrapper.IClassTransformer;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.*;
import static org.junit.Assert.*;

public class KOMEMapTerritoryTransformerTest {
    private static final String TARGET="lotr.client.gui.LOTRGuiMap", OWNER=TARGET.replace('.','/');
    private static final String TRANSFORMER="kome.core.KOMEMapTerritoryTransformer";
    private static final String HOOK="onBeforeNativeMapLabels", DESC="(Llotr/client/gui/LOTRGuiMap;II)V";

    @Test public void developmentTerritoryPassPrecedesExactlyOneUntouchedNativeLabelPass() throws Exception {
        assertLabelOrder(KOMEVisualLocationTransformerTest.readResource("/lotr/client/gui/LOTRGuiMap.class"),false);
    }

    @Test public void bundledProductionSrgPassHasTheSameVerifiedOrderingAndContext() throws Exception {
        assertLabelOrder(production(),true);
    }

    @Test public void developmentTerritoryPrecedesNativePlayersAndBothWaypointPasses() throws Exception {
        assertMarkerOrder(KOMEVisualLocationTransformerTest.readResource("/lotr/client/gui/LOTRGuiMap.class"));
    }

    @Test public void productionTerritoryPrecedesNativePlayersAndBothWaypointPasses() throws Exception {
        assertMarkerOrder(production());
    }

    private static void assertMarkerOrder(byte[] original) throws Exception {
        ClassNode after=node(registeredTerritory(new KOMEMapInputTransformer().transform(TARGET,TARGET,original)));
        MethodNode draw=draw(after);Frame<BasicValue>[] frames=new Analyzer<BasicValue>(new BasicVerifier()).analyze(after.name,draw);
        List<MethodInsnNode> hooks=calls(draw,KOMEMapInputTransformer.HOOK,HOOK);assertEquals(1,hooks.size());
        MethodInsnNode hook=hooks.get(0);int at=draw.instructions.indexOf(hook);
        for(String name:new String[]{"renderRoads","renderPlayers","renderMiniQuests","renderWaypoints","renderLabels","renderWaypointTooltip","renderMapWidgets"}) {
            List<MethodInsnNode> nativePasses=calls(draw,OWNER,name);assertFalse(name,nativePasses.isEmpty());
            for(MethodInsnNode nativePass:nativePasses)
                assertTrue("Territory must precede native "+name,at<draw.instructions.indexOf(nativePass));
        }
        List<MethodInsnNode> compass=calls(draw,"lotr/client/LOTRTextures","drawMapCompassBottomLeft");assertEquals(1,compass.size());
        assertTrue("Use the common terrain exit before compass/roads/markers",at<draw.instructions.indexOf(compass.get(0)));
        for(MethodInsnNode terrain:calls(draw,OWNER,"renderMapAndOverlay"))assertTrue(draw.instructions.indexOf(terrain)<at);
        assertEquals(1,calls(draw,OWNER,"renderPlayers").size());assertEquals(1,calls(draw,OWNER,"renderLabels").size());
        assertEquals(2,calls(draw,OWNER,"renderWaypoints").size());
        AbstractInsnNode argument=previous(previous(previous(hook)));
        assertEquals("Native seam must have an empty operand stack",0,frames[draw.instructions.indexOf(argument)].getStackSize());
        int incoming=0;
        for(AbstractInsnNode step:draw.instructions.toArray())if(step instanceof JumpInsnNode
                && draw.instructions.indexOf(step)<draw.instructions.indexOf(argument)
                && next(((JumpInsnNode)step).label)==argument)incoming++;
        assertTrue("All terrain/control-zone branches must enter the hook, not jump past it",incoming>=3);
    }

    @Test public void registeredMapTransformerChainKeepsAllNativeLabelAndWaypointPasses() throws Exception {
        for(byte[] original:new byte[][]{KOMEVisualLocationTransformerTest.readResource("/lotr/client/gui/LOTRGuiMap.class"),production()}) {
            byte[] bytes=original;
            for(String name:new KOMECorePlugin().getASMTransformerClass())if(name.startsWith("kome.core."))
                bytes=((IClassTransformer)Class.forName(name).getDeclaredConstructor().newInstance()).transform(TARGET,TARGET,bytes);
            ClassNode patched=node(bytes);MethodNode draw=draw(patched);
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(patched.name,draw);
            assertEquals(1,calls(draw,KOMEMapInputTransformer.HOOK,HOOK).size());
            assertEquals(1,calls(draw,OWNER,"renderLabels").size());assertEquals(2,calls(draw,OWNER,"renderWaypoints").size());
            assertEquals(text(method(node(original),"renderLabels","()V")),text(method(patched,"renderLabels","()V")));
        }
    }

    @Test public void eitherInputTransformerOrderIsCompatibleAndIdempotent() throws Exception {
        byte[] original=production();KOMEMapInputTransformer input=new KOMEMapInputTransformer();
        KOMEMapTerritoryTransformer territory=new KOMEMapTerritoryTransformer();
        byte[] first=input.transform(TARGET,TARGET,territory.transform(TARGET,TARGET,original));
        byte[] second=territory.transform(TARGET,TARGET,input.transform(TARGET,TARGET,original));
        assertEquals(text(draw(node(first))),text(draw(node(second))));
        assertArrayEquals(first,territory.transform(TARGET,TARGET,first));
        assertArrayEquals(first,input.transform(TARGET,TARGET,first));
    }

    @Test public void incompatibleLabelCallAndViewFingerprintsLeaveEveryInputByteUntouched() throws Exception {
        for(int mutation=0;mutation<8;mutation++) {
            ClassNode changed=node(production());MethodNode draw=draw(changed);
            MethodInsnNode label=calls(draw,OWNER,"renderLabels").get(0);
            if(mutation==0)label.name="unknownLabels";
            if(mutation==1) {
                InsnList duplicate=new InsnList();duplicate.add(new VarInsnNode(Opcodes.ALOAD,0));
                duplicate.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,OWNER,"renderLabels","()V",false));
                draw.instructions.insert(label,duplicate);
            }
            if(mutation==2)label.owner="other/Map";
            if(mutation==3)label.desc="(I)V";
            if(mutation==4)((VarInsnNode)previous(label)).var=1;
            if(mutation==5)calls(draw,OWNER,"setupZoomVariables").get(0).name="unknownSetup";
            if(mutation==6)for(FieldNode field:changed.fields)if(field.name.equals("zoomScaleStable"))field.name="unknownScale";
            if(mutation==7) {
                List<MethodInsnNode> waypoints=calls(draw,OWNER,"renderWaypoints");
                LabelNode bypass=new LabelNode();draw.instructions.insert(waypoints.get(1),bypass);
                AbstractInsnNode first=waypoints.get(0);for(int i=0;i<4;i++)first=previous(first);
                draw.instructions.insertBefore(first,new JumpInsnNode(Opcodes.GOTO,bypass));
            }
            ClassWriter writer=new ClassWriter(0);changed.accept(writer);byte[] bytes=writer.toByteArray();
            assertSame("Reject changed fingerprint "+mutation,bytes,new KOMEMapTerritoryTransformer().transform(TARGET,TARGET,bytes));
            assertEquals(0,calls(draw(node(bytes)),KOMEMapInputTransformer.HOOK,HOOK).size());
        }
    }

    @Test public void absentAmbiguousDrawMethodsAndUnrelatedClassesDoNotPartiallyTransform() throws Exception {
        ClassNode changed=node(production());MethodNode original=draw(changed);
        changed.methods.remove(original);ClassWriter writer=new ClassWriter(0);changed.accept(writer);byte[] missing=writer.toByteArray();
        KOMEMapTerritoryTransformer transformer=new KOMEMapTerritoryTransformer();assertSame(missing,transformer.transform(TARGET,TARGET,missing));
        original.accept(changed.visitMethod(original.access,"drawScreen",original.desc,null,null));
        original.accept(changed.visitMethod(original.access,"func_73863_a",original.desc,null,null));
        writer=new ClassWriter(0);changed.accept(writer);byte[] ambiguous=writer.toByteArray();
        assertSame(ambiguous,transformer.transform(TARGET,TARGET,ambiguous));
        byte[] unrelated={1,2,3};assertSame(unrelated,transformer.transform("net.minecraft.server.MinecraftServer","net.minecraft.server.MinecraftServer",unrelated));
        assertNull(transformer.transform(TARGET,TARGET,null));
        assertSame(unrelated,transformer.transform(TARGET,TARGET,unrelated));
    }

    @Test public void rejectedLabelLayoutPreservesAlreadyInstalledNativeInputHooks() throws Exception {
        ClassNode changed=node(new KOMEMapInputTransformer().transform(TARGET,TARGET,production()));
        calls(draw(changed),OWNER,"renderLabels").get(0).name="otherLabelPass";
        ClassWriter writer=new ClassWriter(0);changed.accept(writer);byte[] bytes=writer.toByteArray();
        assertSame(bytes,new KOMEMapTerritoryTransformer().transform(TARGET,TARGET,bytes));
        assertEquals(1,calls(draw(node(bytes)),KOMEMapInputTransformer.HOOK,"isMapDragButtonDown").size());
        assertEquals(1,calls(method(node(bytes),"func_73864_a","(III)V"),KOMEMapInputTransformer.HOOK,"onMapSelectorMouseClicked").size());
    }

    @Test public void incompatibleTerrainCompassAndPlayerSeamsRetainTheCompletePostFallback() throws Exception {
        for(int mutation=0;mutation<9;mutation++) {
            ClassNode changed=node(production());MethodNode draw=draw(changed);
            MethodInsnNode compass=calls(draw,"lotr/client/LOTRTextures","drawMapCompassBottomLeft").get(0);
            if(mutation==0)compass.name="otherCompass";
            if(mutation==1)compass.owner="other/Textures";
            if(mutation==2)compass.desc="(DDD)V";
            if(mutation==3) {
                InsnList duplicate=new InsnList();for(int i=0;i<4;i++)duplicate.add(new InsnNode(Opcodes.DCONST_0));
                duplicate.add(new MethodInsnNode(Opcodes.INVOKESTATIC,compass.owner,compass.name,compass.desc,false));draw.instructions.insert(compass,duplicate);
            }
            if(mutation==4) {
                AbstractInsnNode delta=compass;for(int i=0;i<15;i++)delta=previous(delta);((LdcInsnNode)delta).cst=51F;
            }
            if(mutation==5) {
                AbstractInsnNode subtract=compass;for(int i=0;i<5;i++)subtract=next(subtract);
                draw.instructions.set(subtract,new InsnNode(Opcodes.FADD));
            }
            if(mutation==6)calls(draw,OWNER,"renderRoads").get(0).name="otherRoads";
            if(mutation==7)((VarInsnNode)previous(calls(draw,OWNER,"renderPlayers").get(0))).var=1;
            if(mutation==8) {
                AbstractInsnNode start=compass;for(int i=0;i<18;i++)start=previous(start);
                LabelNode bypass=new LabelNode();draw.instructions.insertBefore(start,bypass);
                // Jump from earlier terrain code into the compass's interior, bypassing the proposed hook.
                LabelNode target=new LabelNode();draw.instructions.insert(compass,target);
                draw.instructions.insertBefore(bypass,new JumpInsnNode(Opcodes.GOTO,target));
            }
            ClassWriter writer=new ClassWriter(0);changed.accept(writer);byte[] bytes=writer.toByteArray();
            assertSame("Reject terrain seam mutation "+mutation,bytes,new KOMEMapTerritoryTransformer().transform(TARGET,TARGET,bytes));
            assertEquals(0,calls(draw(node(bytes)),KOMEMapInputTransformer.HOOK,HOOK).size());
        }
    }

    private static void assertLabelOrder(byte[] original,boolean srg) throws Exception {
        // Exercise registration, not merely an unconnected transformer instance. Input hooks compose first.
        byte[] input=new KOMEMapInputTransformer().transform(TARGET,TARGET,original);
        byte[] patched=registeredTerritory(input);
        ClassNode before=node(input),after=node(patched);MethodNode draw=draw(after);
        new Analyzer<BasicValue>(new BasicVerifier()).analyze(after.name,draw);
        List<MethodInsnNode> labels=calls(draw,OWNER,"renderLabels"),hooks=calls(draw,KOMEMapInputTransformer.HOOK,HOOK);
        assertEquals("Exactly one original region-label invocation",1,labels.size());
        assertEquals("Territory must be moved ahead of native labels",1,hooks.size());
        MethodInsnNode hook=hooks.get(0),label=labels.get(0);
        assertEquals(DESC,hook.desc);assertEquals(Opcodes.INVOKESTATIC,hook.getOpcode());
        assertEquals(Opcodes.INVOKESPECIAL,label.getOpcode());
        assertEquals("()V",label.desc);
        assertLoad(next(hook),Opcodes.ALOAD,0);assertEquals(Opcodes.DUP,next(next(hook)).getOpcode());
        assertLoad(previous(hook),Opcodes.ILOAD,2);
        assertLoad(previous(previous(hook)),Opcodes.ILOAD,1);
        assertLoad(previous(previous(previous(hook))),Opcodes.ALOAD,0);
        List<MethodInsnNode> waypoints=calls(draw,OWNER,"renderWaypoints");assertEquals(2,waypoints.size());
        assertTrue(draw.instructions.indexOf(hook)<draw.instructions.indexOf(waypoints.get(0)));
        assertTrue(draw.instructions.indexOf(label)<draw.instructions.indexOf(waypoints.get(1)));
        MethodInsnNode nativeZoom=calls(draw,OWNER,"setupZoomVariables").get(0);
        assertLoad(previous(nativeZoom),Opcodes.FLOAD,3);assertLoad(previous(previous(nativeZoom)),Opcodes.ALOAD,0);
        assertTrue(draw.instructions.indexOf(nativeZoom)<draw.instructions.indexOf(hook));
        assertTrue(draw.instructions.indexOf(calls(draw,OWNER,"renderMapWidgets").get(0))>draw.instructions.indexOf(label));
        // Remove only the added call and its three arguments: every original draw instruction must survive.
        AbstractInsnNode cursor=hook;
        for(int i=0;i<3;i++){AbstractInsnNode argument=previous(cursor);draw.instructions.remove(cursor);cursor=argument;}
        draw.instructions.remove(cursor);
        assertEquals(text(draw(before)),text(draw));
        assertEquals(before.methods.size(),after.methods.size());
        for(MethodNode method:before.methods)if(!method.name.equals(srg?"func_73863_a":"drawScreen"))
            assertEquals("Preserve native labels/preferences, waypoint labels/tooltips, widgets and navigation: "+method.name,
                text(method),text(method(after,method.name,method.desc)));
        assertArrayEquals(patched,registeredTerritory(patched));
    }

    private static byte[] registeredTerritory(byte[] bytes) throws Exception {
        for(String name:new KOMECorePlugin().getASMTransformerClass())if(name.equals(TRANSFORMER))
            bytes=((IClassTransformer)Class.forName(name).getDeclaredConstructor().newInstance()).transform(TARGET,TARGET,bytes);
        return bytes;
    }
    private static byte[] production() throws Exception {
        try(ZipFile jar=new ZipFile("libs/LOTRMod v36.15.jar");InputStream in=jar.getInputStream(jar.getEntry(OWNER+".class"))) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] chunk=new byte[8192];
            for(int n;(n=in.read(chunk))!=-1;)bytes.write(chunk,0,n);return bytes.toByteArray();
        }
    }
    private static ClassNode node(byte[] bytes){ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);return node;}
    private static MethodNode draw(ClassNode node){for(MethodNode method:node.methods)if((method.name.equals("drawScreen")||method.name.equals("func_73863_a"))&&method.desc.equals("(IIF)V"))return method;throw new AssertionError("drawScreen");}
    private static MethodNode method(ClassNode node,String name,String desc){for(MethodNode method:node.methods)if(method.name.equals(name)&&method.desc.equals(desc))return method;throw new AssertionError(name);}
    private static List<MethodInsnNode> calls(MethodNode method,String owner,String name){List<MethodInsnNode> result=new ArrayList<>();for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)step;if(call.owner.equals(owner)&&call.name.equals(name))result.add(call);}return result;}
    private static AbstractInsnNode previous(AbstractInsnNode step){do{step=step.getPrevious();}while(step!=null&&step.getOpcode()<0);return step;}
    private static AbstractInsnNode next(AbstractInsnNode step){do{step=step.getNext();}while(step!=null&&step.getOpcode()<0);return step;}
    private static void assertLoad(AbstractInsnNode step,int opcode,int local){assertEquals(opcode,step.getOpcode());assertEquals(local,((VarInsnNode)step).var);}
    private static String text(MethodNode method){Textifier text=new Textifier();method.accept(new TraceMethodVisitor(text));StringWriter buffer=new StringWriter();text.print(new PrintWriter(buffer));return buffer.toString().replaceAll("MAXSTACK = \\d+","MAXSTACK");}
}
