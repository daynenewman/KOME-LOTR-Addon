package com.fuzs.aquaacrobatics.core.asm;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class AquaLotrSpecialArmorTransformerTest {

    private static final String ARMOR_MODELS_ENTRY = "lotr/client/model/LOTRArmorModels.class";
    private static final String HEAD_PLATE_ENTRY = "lotr/client/model/LOTRModelHeadPlate.class";
    private static final String MODEL_BIPED_ENTRY = "lotr/client/model/LOTRModelBiped.class";

    @Test
    public void devAndRawArmorSelectorDescriptorsReceiveOneAssociationHook() {
        assertEquals(1, associationHookCount(transformArmorSelectorFixture(
            "(Lnet/minecraft/item/ItemStack;ILnet/minecraft/entity/EntityLivingBase;"
                + "Lnet/minecraft/client/model/ModelBiped;)Lnet/minecraft/client/model/ModelBiped;")));
        assertEquals(1, associationHookCount(transformArmorSelectorFixture("(Ladd;ILsv;Lbhm;)Lbhm;")));
    }

    @Test
    public void devAndRawHeadPlateDescriptorsReceiveOneFinalPoseHook() {
        assertEquals(1, poseHookCount(transformHeadPlateFixture(
            "render",
            "(Lnet/minecraft/entity/Entity;FFFFFF)V",
            "setRotationAngles",
            "(FFFFFFLnet/minecraft/entity/Entity;)V")));
        assertEquals(1, poseHookCount(transformHeadPlateFixture(
            "func_78088_a", "(Lsa;FFFFFF)V", "func_78087_a", "(FFFFFFLsa;)V")));
    }

    @Test(expected = IllegalStateException.class)
    public void incompatibleArmorSelectorFingerprintFailsClosed() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, "lotr/client/model/LOTRArmorModels", null,
            "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getSpecialArmorModel",
            "(Ljava/lang/Object;ILjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ACONST_NULL);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 5);
        method.visitEnd();
        writer.visitEnd();

        new AquaClientEntityTransformer().transform(
            AquaClientEntityTransformer.LOTR_ARMOR_MODELS,
            AquaClientEntityTransformer.LOTR_ARMOR_MODELS,
            writer.toByteArray());
    }

    @Test(expected = IllegalStateException.class)
    public void incompatibleHeadPlateFingerprintFailsClosed() {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, "lotr/client/model/LOTRModelHeadPlate", null,
            "java/lang/Object", null);
        MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, "render",
            "(Ljava/lang/Object;FFFFFF)V", null, null);
        render.visitCode();
        render.visitInsn(Opcodes.RETURN);
        render.visitMaxs(0, 8);
        render.visitEnd();
        writer.visitEnd();

        new AquaClientEntityTransformer().transform(
            AquaClientEntityTransformer.LOTR_HEAD_PLATE,
            AquaClientEntityTransformer.LOTR_HEAD_PLATE,
            writer.toByteArray());
    }

    @Test
    public void configuredV3615ClassesHaveExpectedFingerprintsAndTransformOnce() throws Exception {
        File jar = new File("libs/LOTRMod v36.15.jar");
        ZipFile zip = new ZipFile(jar);
        try {
            byte[] armorModels = readEntry(zip, ARMOR_MODELS_ENTRY);
            assertEquals("19e42b3e25fe4d31969da6ca4614ee3b30689b1a6c7244be960b72542f5c9a0c",
                sha256(armorModels));
            byte[] transformedArmorModels = transform(
                AquaClientEntityTransformer.LOTR_ARMOR_MODELS, armorModels);
            assertEquals(1, associationHookCount(transformedArmorModels));
            assertEquals(1, associationHookCount(transform(
                AquaClientEntityTransformer.LOTR_ARMOR_MODELS, transformedArmorModels)));

            byte[] headPlate = readEntry(zip, HEAD_PLATE_ENTRY);
            assertEquals("9d5b5187d204cc472a96daed8491c182352be82d7ee467dec0ffe0d0ef8b1daf",
                sha256(headPlate));
            byte[] transformedHeadPlate = transform(AquaClientEntityTransformer.LOTR_HEAD_PLATE, headPlate);
            assertEquals(1, poseHookCount(transformedHeadPlate));
            assertEquals(1, poseHookCount(transform(
                AquaClientEntityTransformer.LOTR_HEAD_PLATE, transformedHeadPlate)));
        } finally {
            zip.close();
        }
    }

    @Test
    public void lotrModelBipedAndNpcLikeClassesAreNotTransformerTargets() throws Exception {
        ZipFile zip = new ZipFile(new File("libs/LOTRMod v36.15.jar"));
        try {
            byte[] modelBiped = readEntry(zip, MODEL_BIPED_ENTRY);
            byte[] transformed = new AquaClientEntityTransformer().transform(
                "lotr.client.model.LOTRModelBiped",
                "lotr.client.model.LOTRModelBiped",
                modelBiped);
            assertArrayEquals(modelBiped, transformed);
        } finally {
            zip.close();
        }

        byte[] npcLike = emptyClass("lotr/client/model/LOTRModelNpcLike");
        assertArrayEquals(npcLike, new AquaClientEntityTransformer().transform(
            "lotr.client.model.LOTRModelNpcLike",
            "lotr.client.model.LOTRModelNpcLike",
            npcLike));
    }

    private static byte[] transformArmorSelectorFixture(String descriptor) {
        String owner = "lotr/client/model/LOTRArmorModels";
        String modelDescriptor = org.objectweb.asm.Type.getArgumentTypes(descriptor)[3].getDescriptor();
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
            Opcodes.ACC_PUBLIC, "getSpecialArmorModel", descriptor, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 4);
        method.visitVarInsn(Opcodes.ASTORE, 5);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitVarInsn(Opcodes.ALOAD, 4);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "copyModelRotations",
            "(" + modelDescriptor + modelDescriptor + ")V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitVarInsn(Opcodes.ILOAD, 2);
        method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, "setupArmorForSlot",
            "(" + modelDescriptor + "I)V", false);
        method.visitVarInsn(Opcodes.ALOAD, 5);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(3, 6);
        method.visitEnd();
        writer.visitEnd();
        return transform(AquaClientEntityTransformer.LOTR_ARMOR_MODELS, writer.toByteArray());
    }

    private static byte[] transformHeadPlateFixture(
        String renderName, String renderDescriptor, String angleName, String angleDescriptor) {

        String owner = "lotr/client/model/LOTRModelHeadPlate";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, owner, null, "java/lang/Object", null);
        MethodVisitor render = writer.visitMethod(Opcodes.ACC_PUBLIC, renderName, renderDescriptor, null, null);
        render.visitCode();
        render.visitVarInsn(Opcodes.ALOAD, 0);
        for (int i = 2; i <= 7; ++i) render.visitVarInsn(Opcodes.FLOAD, i);
        render.visitVarInsn(Opcodes.ALOAD, 1);
        render.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, angleName, angleDescriptor, false);
        render.visitInsn(Opcodes.RETURN);
        render.visitMaxs(8, 8);
        render.visitEnd();
        writer.visitEnd();
        return transform(AquaClientEntityTransformer.LOTR_HEAD_PLATE, writer.toByteArray());
    }

    private static byte[] transform(String transformedName, byte[] original) {
        return new AquaClientEntityTransformer().transform(transformedName, transformedName, original);
    }

    private static int associationHookCount(byte[] bytes) {
        return hookCount(bytes, AquaClientEntityTransformer.ASSOCIATE_SPECIAL_ARMOR);
    }

    private static int poseHookCount(byte[] bytes) {
        return hookCount(bytes, AquaClientEntityTransformer.APPLY_AFTER_LOTR_ANGLES);
    }

    private static int hookCount(byte[] bytes, String name) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        int count = 0;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (AquaClientEntityTransformer.LOTR_ARMOR_POSE_BRIDGE.equals(call.owner)
                    && name.equals(call.name)) ++count;
            }
        }
        return count;
    }

    private static byte[] readEntry(ZipFile zip, String name) throws Exception {
        ZipEntry entry = zip.getEntry(name);
        assertNotNull(name, entry);
        return read(zip.getInputStream(entry));
    }

    private static byte[] read(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        } finally {
            input.close();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder();
        for (byte value : digest) result.append(String.format("%02x", value & 0xff));
        return result.toString();
    }

    private static byte[] emptyClass(String name) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }
}
