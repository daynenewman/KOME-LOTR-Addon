package kome.core;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.Assert.*;

public class KOMEFactionTitleOptionsTransformerTest {
    @Test
    public void optionsInitAndActionAreHookedExactlyOnce() throws Exception {
        byte[] original = readResource("/lotr/client/gui/LOTRGuiOptions.class");

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
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
