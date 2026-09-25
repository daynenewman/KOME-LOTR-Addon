package kome.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.InsnNode;

import static org.junit.Assert.*;

public class KOMEVisualLocationTransformerTest {
    @Test public void overheadPassKeepsNativeQuestRenderingAndAddsOneRelationshipCall() throws Exception {
        assertNativePassPreserved("/lotr/client/render/entity/LOTRNPCRendering.class",
            KOMEVisualLocationTransformer.NPC_RENDERING, "renderQuestOffer",
            "(Llotr/common/entity/npc/LOTREntityNPC;DDD)V", "renderRelationshipMarker");
    }

    @Test public void mapPassKeepsNativeMiniquestsAndAddsOneMarkerCall() throws Exception {
        assertNativePassPreserved("/lotr/client/gui/LOTRGuiMap.class",
            KOMEVisualLocationTransformer.MAP, "renderMiniQuests",
            "(Lnet/minecraft/entity/player/EntityPlayer;II)V", "renderMapMarkers");
    }

    @Test public void nativeOfferPredicateGetsPlayerSpecificRelationshipGuard() throws Exception {
        byte[] original=readResource("/lotr/common/entity/npc/LOTREntityQuestInfo.class");
        KOMEVisualLocationTransformer transformer=new KOMEVisualLocationTransformer();
        byte[] transformed=transformer.transform(KOMEVisualLocationTransformer.QUEST_INFO,KOMEVisualLocationTransformer.QUEST_INFO,original);
        MethodNode method=method(transformed,"canOfferQuestsTo","(Lnet/minecraft/entity/player/EntityPlayer;)Z");
        int guards=0,ands=0;for(AbstractInsnNode instruction=method.instructions.getFirst();instruction!=null;instruction=instruction.getNext()){
            if(instruction instanceof MethodInsnNode&&KOMEVisualLocationTransformer.QUEST_GUARD.equals(((MethodInsnNode)instruction).owner))guards++;
            if(instruction.getOpcode()==org.objectweb.asm.Opcodes.IAND)ands++;
        }
        assertTrue(guards>0);assertEquals(guards,ands);
        assertArrayEquals(transformed,transformer.transform(KOMEVisualLocationTransformer.QUEST_INFO,KOMEVisualLocationTransformer.QUEST_INFO,transformed));
    }

    @Test public void relationshipRendererRetainsNativeDistanceFadeAndThroughWallPath() throws Exception {
        byte[] original = readResource("/lotr/client/render/entity/LOTRNPCRendering.class");
        byte[] transformed = new KOMEVisualLocationTransformer().transform(
            KOMEVisualLocationTransformer.NPC_RENDERING,
            KOMEVisualLocationTransformer.NPC_RENDERING, original);
        MethodNode nativeBook = method(original, "renderQuestBook",
            KOMEVisualLocationTransformer.NPC_RENDER_DESC);
        MethodNode relationship = method(transformed,
            KOMEVisualLocationTransformer.NATIVE_RELATIONSHIP_RENDERER,
            KOMEVisualLocationTransformer.NPC_RENDER_DESC);
        assertEquals("The native renderer body must be cloned, not approximated",
            nativeBook.instructions.size() + 2, relationship.instructions.size());
        List<String> calls = calls(transformed,
            KOMEVisualLocationTransformer.NATIVE_RELATIONSHIP_RENDERER,
            KOMEVisualLocationTransformer.NPC_RENDER_DESC, true);
        assertTrue(contains(calls, "java/lang/Math.pow"));
        assertTrue(contains(calls, "org/lwjgl/opengl/GL11.glDepthMask"));
        assertTrue(contains(calls, "net/minecraft/client/renderer/ItemRenderer.renderItemIn2D"));
        assertTrue(contains(calls, KOMEVisualLocationTransformer.BRIDGE + ".relationshipIconForNative"));
        boolean distanceField = false;
        for (AbstractInsnNode instruction = relationship.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext())
            if (instruction instanceof FieldInsnNode
                    && "RENDER_HEAD_DISTANCE".equals(((FieldInsnNode)instruction).name)) distanceField = true;
        assertTrue("Native LOTRMiniQuest distance threshold must remain in use", distanceField);
        boolean speechBypassed=false;
        for(AbstractInsnNode instruction=relationship.instructions.getFirst();instruction!=null;instruction=instruction.getNext())
            if(instruction instanceof MethodInsnNode&&"hasSpeech".equals(((MethodInsnNode)instruction).name)
                    &&instruction.getNext()!=null&&instruction.getNext().getOpcode()==org.objectweb.asm.Opcodes.POP)speechBypassed=true;
        assertTrue("KOME clone must retain the native speech query but bypass its suppression jump",speechBypassed);
    }

    private static boolean contains(List<String> values, String text) {
        for (String value : values) if (value.contains(text)) return true;
        return false;
    }

    private static void assertNativePassPreserved(String resource, String targetClass,
            String methodName, String descriptor, String hookName) throws Exception {
        byte[] original = readResource(resource);
        KOMEVisualLocationTransformer transformer = new KOMEVisualLocationTransformer();
        byte[] transformed = transformer.transform(targetClass, targetClass, original);
        List<String> originalCalls = calls(original, methodName, descriptor, false);
        List<String> transformedNativeCalls = calls(transformed, methodName, descriptor, false);
        assertFalse("The audited LOTR renderer must contain its native rendering calls", originalCalls.isEmpty());
        assertEquals("KOME must not remove or replace native quest/map rendering", originalCalls,
            transformedNativeCalls);
        assertEquals(1, hookCount(transformed, methodName, descriptor, hookName));
        assertArrayEquals("Transforming twice must not duplicate visual markers", transformed,
            transformer.transform(targetClass, targetClass, transformed));
    }

    private static List<String> calls(byte[] bytes, String methodName, String descriptor,
            boolean includeBridge) {
        MethodNode method = method(bytes, methodName, descriptor);
        List<String> calls = new ArrayList<String>();
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (includeBridge || !KOMEVisualLocationTransformer.BRIDGE.equals(call.owner))
                    calls.add(call.owner + '.' + call.name + call.desc);
            }
        }
        return calls;
    }

    private static int hookCount(byte[] bytes, String methodName, String descriptor, String hookName) {
        int count = 0;
        for (String call : calls(bytes, methodName, descriptor, true))
            if (call.startsWith(KOMEVisualLocationTransformer.BRIDGE + '.' + hookName)) count++;
        return count;
    }

    private static MethodNode method(byte[] bytes, String name, String descriptor) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods)
            if (name.equals(method.name) && descriptor.equals(method.desc)) return method;
        fail("Missing audited LOTR method " + name + descriptor);
        return null;
    }

    private static byte[] readResource(String name) throws Exception {
        InputStream stream = KOMEVisualLocationTransformerTest.class.getResourceAsStream(name);
        assertNotNull(name, stream);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) >= 0) output.write(buffer, 0, read);
            return output.toByteArray();
        } finally {
            stream.close();
        }
    }
}
