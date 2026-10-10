package kome.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.*;

public class KOMEFactionTitleOptionsTransformerTest {
    @Test
    public void productionSrgOptionsInitAndActionAreHookedExactlyOnce() throws Exception {
        try (ZipFile jar = new ZipFile("libs/LOTRMod v36.15.jar")) {
            try (InputStream input = jar.getInputStream(
                    jar.getEntry("lotr/client/gui/LOTRGuiOptions.class"))) {
                byte[] original = readBytes(input);
                ClassNode node = new ClassNode();
                new ClassReader(original).accept(node, 0);
                assertTrue(node.methods.stream().anyMatch(m -> "func_73866_w_".equals(m.name)));
                assertTrue(node.methods.stream().anyMatch(m -> "func_146284_a".equals(m.name)));
                verifyHooks(original);
            }
        }
    }

    @Test
    public void optionsInitAndActionAreHookedExactlyOnce() throws Exception {
        verifyHooks(readResource("/lotr/client/gui/LOTRGuiOptions.class"));
    }

    private static void verifyHooks(byte[] original) {
        KOMEFactionTitleOptionsTransformer transformer =
            new KOMEFactionTitleOptionsTransformer();

        byte[] transformed = transformer.transform(
            KOMEFactionTitleOptionsTransformer.TARGET,
            KOMEFactionTitleOptionsTransformer.TARGET,
            original);

        assertEquals(1, hookCount(
            transformed,
            KOMEFactionTitleOptionsTransformer.INIT_HOOK_NAME,
            KOMEFactionTitleOptionsTransformer.INIT_HOOK_DESC));
        assertEquals(1, hookCount(
            transformed,
            KOMEFactionTitleOptionsTransformer.ACTION_HOOK_NAME,
            KOMEFactionTitleOptionsTransformer.ACTION_HOOK_DESC));

        assertArrayEquals(
            transformed,
            transformer.transform(
                KOMEFactionTitleOptionsTransformer.TARGET,
                KOMEFactionTitleOptionsTransformer.TARGET,
                transformed));
    }

    private static int hookCount(byte[] bytes, String hookName, String hookDesc) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int count = 0;

        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null;
                    instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (KOMEFactionTitleOptionsTransformer.BRIDGE_OWNER.equals(call.owner)
                        && hookName.equals(call.name)
                        && hookDesc.equals(call.desc)) {
                    count++;
                }
            }
        }
        return count;
    }

    private static byte[] readResource(String path) throws Exception {
        InputStream input = KOMEFactionTitleOptionsTransformerTest.class
            .getResourceAsStream(path);
        assertNotNull(path, input);

        try {
            return readBytes(input);
        } finally {
            input.close();
        }
    }

    private static byte[] readBytes(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toByteArray();
    }
}
