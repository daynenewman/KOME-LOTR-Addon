package kome.core;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
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
        assertGuardDominatesEveryFastTravelCall(transformed);
        assertTrue(KOMEWaypointTransformer.isFinalTravelGuardInstalled());
        assertArrayEquals(transformed,
            transformer.transform("lotr.common.LOTRPlayerData", KOMEWaypointTransformer.TARGET_CLASS, transformed));
    }

    @Test
    public void nativeLotrRequestDenialReturnsBeforeTargetAssignment() throws Exception {
        byte[] original = readResource(
            "/lotr/common/network/LOTRPacketFastTravel$Handler.class");
        KOMEWaypointTransformer transformer = new KOMEWaypointTransformer();
        byte[] transformed = transformer.transform(
            KOMEWaypointTransformer.REQUEST_TARGET_CLASS,
            KOMEWaypointTransformer.REQUEST_TARGET_CLASS, original);
        assertEquals(1, requestHookCount(transformed));
        assertRequestGateDominatesUnlockAndTargetAssignment(transformed);
        assertTrue(KOMEWaypointTransformer.isNativeRequestGuardInstalled());
        assertArrayEquals(transformed, transformer.transform(
            KOMEWaypointTransformer.REQUEST_TARGET_CLASS,
            KOMEWaypointTransformer.REQUEST_TARGET_CLASS, transformed));
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
            ZipEntry requestEntry = zip.getEntry(
                "lotr/common/network/LOTRPacketFastTravel$Handler.class");
            assertNotNull(requestEntry);
            byte[] requestOriginal = readStream(zip.getInputStream(requestEntry));
            assertEquals(1, requestHookCount(
                new KOMEWaypointTransformer().transform(
                    KOMEWaypointTransformer.REQUEST_TARGET_CLASS,
                    KOMEWaypointTransformer.REQUEST_TARGET_CLASS,
                    requestOriginal)));
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

    @Test
    public void declaredDevDependencyHasExactRuntimeFingerprintsAndDescriptors() throws Exception {
        File jar = new File("libs/LOTRMod v36.15.jar");
        assertTrue("Declared LOTR v36.15 dev dependency is missing", jar.isFile());
        ZipFile zip = new ZipFile(jar);
        try {
            byte[] playerData = readEntry(zip, "lotr/common/LOTRPlayerData.class");
            byte[] requestHandler = readEntry(
                zip, "lotr/common/network/LOTRPacketFastTravel$Handler.class");
            assertEquals(
                "67b3303bf84d66fee4f5284c0e34d630817a1c366f7a592aab3455edc6df439e",
                sha256(playerData));
            assertEquals(
                "03c613c6b67e3875c7d43e2a985b8c698032b49708e61e31e54d30de8bfe4552",
                sha256(requestHandler));
            assertMethodPresent(playerData,
                KOMEWaypointTransformer.TARGET_METHOD,
                KOMEWaypointTransformer.TARGET_DESC);
            assertMethodPresent(requestHandler,
                KOMEWaypointTransformer.REQUEST_TARGET_METHOD,
                KOMEWaypointTransformer.REQUEST_TARGET_DESC);

            KOMEWaypointTransformer transformer = new KOMEWaypointTransformer();
            byte[] transformedPlayer = transformer.transform(
                KOMEWaypointTransformer.TARGET_CLASS,
                KOMEWaypointTransformer.TARGET_CLASS, playerData);
            byte[] transformedRequest = transformer.transform(
                KOMEWaypointTransformer.REQUEST_TARGET_CLASS,
                KOMEWaypointTransformer.REQUEST_TARGET_CLASS, requestHandler);
            assertGuardDominatesEveryFastTravelCall(transformedPlayer);
            assertRequestGateDominatesUnlockAndTargetAssignment(transformedRequest);
        } finally {
            zip.close();
        }
    }

    private static int requestHookCount(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (KOMEWaypointTransformer.HOOK_OWNER.equals(call.owner)
                            && KOMEWaypointTransformer.REQUEST_HOOK_NAME.equals(call.name)
                            && KOMEWaypointTransformer.REQUEST_HOOK_DESC.equals(call.desc)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static void assertGuardDominatesEveryFastTravelCall(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int allFastTravelCalls = 0;
        for (MethodNode method : node.methods) {
            int index = 0;
            int hookIndex = -1;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext(), index++) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (KOMEWaypointTransformer.HOOK_OWNER.equals(call.owner)
                        && KOMEWaypointTransformer.HOOK_NAME.equals(call.name)) {
                    hookIndex = index;
                    AbstractInsnNode branch = nextExecutable(instruction);
                    assertNotNull(branch);
                    assertEquals(Opcodes.IFNE, branch.getOpcode());
                    AbstractInsnNode denial = nextExecutable(branch);
                    assertNotNull(denial);
                    assertEquals("A false final guard must return without teleporting",
                        Opcodes.RETURN, denial.getOpcode());
                }
                if ("lotr/common/LOTRPlayerData".equals(call.owner)
                        && "fastTravelTo".equals(call.name)
                        && "(Llotr/common/world/map/LOTRAbstractWaypoint;)V".equals(call.desc)) {
                    allFastTravelCalls++;
                    assertEquals(KOMEWaypointTransformer.TARGET_METHOD, method.name);
                    assertEquals(KOMEWaypointTransformer.TARGET_DESC, method.desc);
                    assertTrue("Final KOME guard must execute before fastTravelTo",
                        hookIndex >= 0 && hookIndex < index);
                }
            }
        }
        assertEquals("Every LOTRPlayerData fastTravelTo path must be known and guarded",
            1, allFastTravelCalls);
    }

    private static void assertRequestGateDominatesUnlockAndTargetAssignment(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (!KOMEWaypointTransformer.REQUEST_TARGET_METHOD.equals(method.name)
                    || !KOMEWaypointTransformer.REQUEST_TARGET_DESC.equals(method.desc)) {
                continue;
            }
            int index = 0;
            int hookIndex = -1;
            int permittedLabelIndex = -1;
            int unlockChecks = 0;
            int targetAssignments = 0;
            for (AbstractInsnNode instruction = method.instructions.getFirst();
                    instruction != null; instruction = instruction.getNext(), index++) {
                if (!(instruction instanceof MethodInsnNode)) {
                    continue;
                }
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (KOMEWaypointTransformer.HOOK_OWNER.equals(call.owner)
                        && KOMEWaypointTransformer.REQUEST_HOOK_NAME.equals(call.name)) {
                    hookIndex = index;
                    AbstractInsnNode branch = nextExecutable(instruction);
                    assertEquals(Opcodes.IFNE, branch.getOpcode());
                    permittedLabelIndex = method.instructions.indexOf(
                        ((JumpInsnNode) branch).label);
                    assertEquals(Opcodes.ACONST_NULL,
                        nextExecutable(branch).getOpcode());
                    assertEquals(Opcodes.ARETURN,
                        nextExecutable(nextExecutable(branch)).getOpcode());
                }
                if ("lotr/common/world/map/LOTRAbstractWaypoint".equals(call.owner)
                        && "hasPlayerUnlocked".equals(call.name)) {
                    unlockChecks++;
                    assertTrue(
                        "KOME request gate must execute before native unlock/target assignment",
                        hookIndex >= 0 && hookIndex < index);
                }
                if ("lotr/common/LOTRPlayerData".equals(call.owner)
                        && "setTargetFTWaypoint".equals(call.name)
                        && "(Llotr/common/world/map/LOTRAbstractWaypoint;)V".equals(call.desc)) {
                    targetAssignments++;
                    assertTrue("KOME request gate must execute before target assignment",
                        hookIndex >= 0 && hookIndex < index);
                    assertTrue("Only the allowed branch may reach target assignment",
                        permittedLabelIndex > hookIndex && permittedLabelIndex < index);
                }
            }
            assertEquals(1, unlockChecks);
            assertEquals(1, targetAssignments);
            return;
        }
        fail("Transformed native fast-travel request handler was not found");
    }

    private static byte[] readEntry(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        assertNotNull(name, entry);
        return readStream(zip.getInputStream(entry));
    }

    private static void assertMethodPresent(byte[] bytes, String name, String desc) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int matches = 0;
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && desc.equals(method.desc)) {
                matches++;
            }
        }
        assertEquals(name + desc, 1, matches);
    }

    private static AbstractInsnNode nextExecutable(AbstractInsnNode instruction) {
        AbstractInsnNode next = instruction == null ? null : instruction.getNext();
        while (next != null && next.getOpcode() < 0) {
            next = next.getNext();
        }
        return next;
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
