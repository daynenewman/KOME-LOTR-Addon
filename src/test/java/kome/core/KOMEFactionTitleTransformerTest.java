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

public class KOMEFactionTitleTransformerTest {
    @Test
    public void nativeFactionAlignmentTitleIsReplacedExactlyOnce()
            throws Exception {
        byte[] original=
            readResource(
                "/lotr/client/gui/LOTRGuiFactions.class");

        KOMEFactionTitleTransformer transformer=
            new KOMEFactionTitleTransformer();

        byte[] transformed=
            transformer.transform(
                KOMEFactionTitleTransformer.TARGET,
                KOMEFactionTitleTransformer.TARGET,
                original);

        assertEquals(
            1,
            hookCount(transformed));

        assertArrayEquals(
            transformed,
            transformer.transform(
                KOMEFactionTitleTransformer.TARGET,
                KOMEFactionTitleTransformer.TARGET,
                transformed));
    }

    private static int hookCount(
            byte[] bytes) {
        ClassNode node=new ClassNode();

        new ClassReader(bytes).accept(node,0);

        int count=0;

        for(MethodNode method:node.methods) {
            for(AbstractInsnNode instruction=
                    method.instructions.getFirst();
                    instruction!=null;
                    instruction=instruction.getNext()) {
                if(instruction instanceof MethodInsnNode) {
                    MethodInsnNode call=
                        (MethodInsnNode)instruction;

                    if(KOMEFactionTitleTransformer.HOOK_OWNER.equals(
                                call.owner)
                            &&KOMEFactionTitleTransformer.HOOK_NAME.equals(
                                call.name)
                            &&KOMEFactionTitleTransformer.HOOK_DESC.equals(
                                call.desc)) {
                        count++;
                    }
                }
            }
        }

        return count;
    }

    private static byte[] readResource(
            String path)
            throws Exception {
        InputStream input=
            KOMEFactionTitleTransformerTest.class
                .getResourceAsStream(path);

        assertNotNull(path,input);

        try {
            ByteArrayOutputStream output=
                new ByteArrayOutputStream();

            byte[] buffer=new byte[8192];
            int read;

            while((read=input.read(buffer))!=-1) {
                output.write(
                    buffer,
                    0,
                    read);
            }

            return output.toByteArray();
        } finally {
            input.close();
        }
    }
}
