package kome.core;

import java.io.*;
import java.util.*;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class KOMEMapInputTransformerTest {
    @Test public void developmentHooksVerifyAndPreserveNativeWaypointKeyboardAndDrawingCalls() throws Exception {
        assertHooks(KOMEVisualLocationTransformerTest.readResource("/lotr/client/gui/LOTRGuiMap.class"),false);
    }
    @Test public void bundledProductionSrgMethodsReceiveTheSameVerifiedHooks() throws Exception {
        try (ZipFile jar=new ZipFile("libs/LOTRMod v36.15.jar")) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try (InputStream in=jar.getInputStream(jar.getEntry("lotr/client/gui/LOTRGuiMap.class"))) {
                byte[] chunk=new byte[8192];for(int n;(n=in.read(chunk))!=-1;)bytes.write(chunk,0,n);
            }
            assertHooks(bytes.toByteArray(),true);
        }
    }
    @Test public void unrelatedAndDedicatedServerClassesAreUntouched() {
        KOMEMapInputTransformer transformer=new KOMEMapInputTransformer();byte[] bytes={1,2,3};
        assertSame(bytes,transformer.transform("net.minecraft.server.MinecraftServer","net.minecraft.server.MinecraftServer",bytes));
        assertNull(transformer.transform(KOMEMapInputTransformer.TARGET,KOMEMapInputTransformer.TARGET,null));
    }
    @Test(expected=IllegalStateException.class) public void incompatibleNativeMapFailsWithExplicitFingerprintError() {
        ClassWriter writer=new ClassWriter(0);writer.visit(Opcodes.V1_6,Opcodes.ACC_PUBLIC,"lotr/client/gui/LOTRGuiMap",null,"java/lang/Object",null);writer.visitEnd();
        new KOMEMapInputTransformer().transform(KOMEMapInputTransformer.TARGET,KOMEMapInputTransformer.TARGET,writer.toByteArray());
    }
    private static void assertHooks(byte[] original,boolean srg) throws Exception {
        KOMEMapInputTransformer transformer=new KOMEMapInputTransformer();
        byte[] transformed=transformer.transform(KOMEMapInputTransformer.TARGET,KOMEMapInputTransformer.TARGET,original);
        assertArrayEquals(transformed,transformer.transform(KOMEMapInputTransformer.TARGET,KOMEMapInputTransformer.TARGET,transformed));
        ClassNode before=node(original),after=node(transformed);
        String[] methods=srg?new String[]{"func_73864_a","func_73869_a","func_73863_a","renderWaypointTooltip","func_146274_d"}:new String[]{"mouseClicked","keyTyped","drawScreen","renderWaypointTooltip","handleMouseInput"};
        String[] hooks={"onMapMouseClicked","onMapKeyTyped","isMapDragButtonDown","suppressMapInspection","mapMouseWheel"};
        for(int i=0;i<methods.length;i++) {
            MethodNode old=method(before,methods[i]),current=method(after,methods[i]);
            new Analyzer<BasicValue>(new BasicVerifier()).analyze(after.name,current);
            List<String> callsBefore=nativeCalls(old),callsAfter=nativeCalls(current);
            if(i==2) assertTrue(callsBefore.remove("org/lwjgl/input/Mouse.isButtonDown(I)Z"));
            if(i==4) assertTrue(callsBefore.remove("org/lwjgl/input/Mouse.getEventDWheel()I"));
            assertEquals("Existing waypoint, zoom, and keyboard calls remain intact",callsBefore,callsAfter);
            int count=0;
            for(AbstractInsnNode instruction=current.instructions.getFirst();instruction!=null;instruction=instruction.getNext())
                if(instruction instanceof MethodInsnNode && KOMEMapInputTransformer.HOOK.equals(((MethodInsnNode)instruction).owner)) {
                    MethodInsnNode hook=(MethodInsnNode)instruction;count++;
                    if (i == 0 && "onNativeWaypointClick".equals(hook.name)) {
                        AbstractInsnNode receiver=previous(instruction);
                        assertEquals(Opcodes.ALOAD,receiver.getOpcode());assertEquals(0,((VarInsnNode)receiver).var);
                        AbstractInsnNode assignment=previous(receiver);
                        assertEquals(Opcodes.PUTFIELD,assignment.getOpcode());
                        assertEquals("selectedWaypoint",((FieldInsnNode)assignment).name);
                        assertEquals("The native candidate, not a null clear",Opcodes.ALOAD,previous(assignment).getOpcode());
                        continue;
                    }
                    assertEquals(i == 0 && "onMapSelectorMouseClicked".equals(hook.name) ? "onMapSelectorMouseClicked" : hooks[i],hook.name);
                    if(i!=2 && i!=4) {
                        AbstractInsnNode branch=next(instruction);assertEquals(Opcodes.IFEQ,branch.getOpcode());assertEquals(Opcodes.RETURN,next(branch).getOpcode());
                        assertTrue(current.instructions.indexOf(instruction)<current.instructions.indexOf(((JumpInsnNode)branch).label));
                    }
                }
            assertEquals(i == 0 ? 3 : 1,count);
        }
        MethodNode click=method(after,methods[0]);
        int selector=-1,pick=-1,radial=-1,base=-1;
        for (AbstractInsnNode instruction=click.instructions.getFirst();instruction!=null;instruction=instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call=(MethodInsnNode)instruction;
            if (KOMEMapInputTransformer.HOOK.equals(call.owner)) {
                if ("onMapSelectorMouseClicked".equals(call.name)) selector=click.instructions.indexOf(call);
                if ("onNativeWaypointClick".equals(call.name)) pick=click.instructions.indexOf(call);
                if ("onMapMouseClicked".equals(call.name)) radial=click.instructions.indexOf(call);
            } else if (call.getOpcode()==Opcodes.INVOKESPECIAL && methods[0].equals(call.name)) base=click.instructions.indexOf(call);
        }
        assertTrue("Only panel rectangles and rendered radial cells precede native picking",selector>=0 && selector<pick);
        assertTrue("Unowned native waypoint selection precedes tile-context handling",pick>=0 && pick<radial && radial<base);
        assertEquals(before.methods.size(),after.methods.size());
        for (MethodNode old : before.methods) {
            if (Arrays.asList(methods).contains(old.name)) continue;
            MethodNode current = null;
            for (MethodNode candidate : after.methods) if (old.name.equals(candidate.name) && old.desc.equals(candidate.desc)) current = candidate;
            assertNotNull(current);
            assertEquals("Other native controls remain intact: "+old.name,nativeCalls(old),nativeCalls(current));
            assertEquals("No changes to wheel, widgets, waypoint markers or other native bodies: "+old.name,
                old.instructions.size(),current.instructions.size());
        }
    }
    private static AbstractInsnNode previous(AbstractInsnNode n) {do{n=n.getPrevious();}while(n!=null&&n.getOpcode()<0);return n;}
    private static AbstractInsnNode next(AbstractInsnNode n) {do{n=n.getNext();}while(n!=null&&n.getOpcode()<0);return n;}
    private static ClassNode node(byte[] bytes) {ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);return c;}
    private static MethodNode method(ClassNode node,String name) {for(MethodNode m:node.methods)if(m.name.equals(name))return m;throw new AssertionError(name);}
    private static List<String> nativeCalls(MethodNode method) {
        List<String> calls=new ArrayList<String>();
        for(AbstractInsnNode n=method.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)n;if(!c.owner.startsWith("kome/"))calls.add(c.owner+"."+c.name+c.desc);}
        return calls;
    }
}
