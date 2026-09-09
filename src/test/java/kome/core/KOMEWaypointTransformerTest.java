package kome.core;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.File;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.Assert.*;

public class KOMEWaypointTransformerTest {
    @Test
    public void corePluginRegistersKomeAndLotrMoreMobsTransformers() {
        assertArrayEquals(new String[] {
                KOMEWaypointTransformer.class.getName(),
                com.enovak.lotrmoremobs.coremod.MortalGandalfTransformer.class.getName(),
                com.enovak.lotrmoremobs.coremod.RespawnMarkerProjectileCollisionTransformer.class.getName(),
                com.enovak.lotrmoremobs.coremod.EntitySensesGateSightTransformer.class.getName(),
                com.enovak.lotrmoremobs.coremod.PathFinderGatePartTransformer.class.getName(),
"com.fuzs.aquaacrobatics.core.asm.AquaEntityPlayerTransformer",
"com.fuzs.aquaacrobatics.core.asm.AquaServerPlayerTransformer",
"com.fuzs.aquaacrobatics.core.asm.AquaBiomeTransformer",
"com.fuzs.aquaacrobatics.core.asm.AquaCommonWorldTransformer",
"com.fuzs.aquaacrobatics.core.asm.AquaClientEntityTransformer",
"com.fuzs.aquaacrobatics.core.asm.AquaLateClientPlayerTransformer"
            },
            new KOMECorePlugin().getASMTransformerClass());
    }

    @Test
    public void exactLotrMethodReceivesFinalTravelGuardOnce() throws Exception {
        byte[] original = readResource("/lotr/common/LOTRPlayerData.class");
        KOMEWaypointTransformer transformer = new KOMEWaypointTransformer();
        byte[] transformed = transformer.transform("lotr.common.LOTRPlayerData", KOMEWaypointTransformer.TARGET_CLASS, original);
        assertEquals(1, hookCount(transformed));
        assertArrayEquals(transformed,
            transformer.transform("lotr.common.LOTRPlayerData", KOMEWaypointTransformer.TARGET_CLASS, transformed));
    }

    @Test(expected = IllegalStateException.class)
    public void incompatibleTargetFailsClosedWithClearFingerprintError() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, "lotr/common/LOTRPlayerData", null, "java/lang/Object", null);
        org.objectweb.asm.MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC,
            KOMEWaypointTransformer.TARGET_METHOD, KOMEWaypointTransformer.TARGET_DESC, null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();
        new KOMEWaypointTransformer().transform("lotr.common.LOTRPlayerData", KOMEWaypointTransformer.TARGET_CLASS, writer.toByteArray());
    }

    @Test
    public void configuredBuildDependencyV3615JarHasVerifiedClassFingerprintAndTransforms() throws Exception {
        File jar = new File("../LOTR-Test-Server/mods/LOTRMod v36.15.jar");
        org.junit.Assume.assumeTrue("Configured build LOTR v36.15 jar is unavailable", jar.isFile());
        assertExternalJarTransforms(jar,
            "d495524e27358296fbc584756d9ce3edefb1ad3836d96955473d6876edfb2321");
    }

    @Test
    public void configuredProductionV3615JarHasVerifiedClassFingerprintAndTransforms() throws Exception {
        File jar = new File("../LOTR-Test-Server/mods/LOTRMod v36.15.jar.original");
        org.junit.Assume.assumeTrue("Configured production LOTR v36.15 jar is unavailable", jar.isFile());
        assertExternalJarTransforms(jar,
            "67b3303bf84d66fee4f5284c0e34d630817a1c366f7a592aab3455edc6df439e");
    }

    private static void assertExternalJarTransforms(File jar, String expectedClassHash) throws Exception {
        ZipFile zip = new ZipFile(jar);
        try {
            ZipEntry entry = zip.getEntry("lotr/common/LOTRPlayerData.class");
            assertNotNull(entry);
            byte[] original = readStream(zip.getInputStream(entry));
            assertEquals(expectedClassHash, sha256(original));
            assertEquals(1, hookCount(new KOMEWaypointTransformer().transform(
                "lotr.common.LOTRPlayerData", KOMEWaypointTransformer.TARGET_CLASS, original)));
        } finally {
            zip.close();
        }
    }

    private static int hookCount(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (KOMEWaypointTransformer.HOOK_OWNER.equals(call.owner)
                            && KOMEWaypointTransformer.HOOK_NAME.equals(call.name)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static byte[] readResource(String name) throws Exception {
        InputStream stream = KOMEWaypointTransformerTest.class.getResourceAsStream(name);
        assertNotNull(name, stream);
        return readStream(stream);
    }

    private static byte[] readStream(InputStream stream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            output.write(buffer, 0, read);
        }
        stream.close();
        return output.toByteArray();
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder();
        for (byte value : digest) {
            result.append(String.format("%02x", value & 0xff));
        }
        return result.toString();
    }
}
