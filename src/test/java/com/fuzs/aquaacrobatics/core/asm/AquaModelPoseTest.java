package com.fuzs.aquaacrobatics.core.asm;

import static org.junit.Assert.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.Collections;
import com.fuzs.aquaacrobatics.core.AquaAcrobaticsCore;
import com.enovak.lotrmoremobs.client.config.ClientServerGameplayState;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import org.junit.Test;
import org.junit.After;
import org.junit.Before;
import net.minecraft.entity.EntityLivingBase;
import com.fuzs.aquaacrobatics.client.model.IModelBipedSwimming;
import com.fuzs.aquaacrobatics.client.model.FirstPersonArmRenderContext;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.RemappingClassAdapter;
import com.fuzs.aquaacrobatics.entity.Pose;
import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;
import com.fuzs.aquaacrobatics.integration.charactercreation.CharacterCreationIntegration;
import net.minecraft.entity.player.EntityPlayer;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.tree.*;

/** Executes the real model math without an OpenGL render or game launch. */
public class AquaModelPoseTest {
    private static final List<ModelRenderer> drawn = new ArrayList<ModelRenderer>();
    public static void recordDraw(ModelRenderer part, float scale) {
        if (part.showModel && !part.isHidden) drawn.add(part);
    }
    private boolean previousModern;
    @Before public void initializeEnvironment() {
        previousModern = ClientServerGameplayState.useModernPlayerAnimations();
        new AquaAcrobaticsCore().injectData(Collections.<String,Object>singletonMap("runtimeDeobfuscationEnabled", false));
        ClientServerGameplayState.setModernPlayerAnimations(true);
    }
    static final class Models extends ClassLoader {
        final boolean aqua;
        final boolean production;
        Models(boolean aqua) { this(aqua, false); }
        Models(boolean aqua, boolean production) {
            super(AquaModelPoseTest.class.getClassLoader()); this.aqua = aqua; this.production = production;
        }
        Class<?> fixture(byte[] bytes) { return defineClass(null, bytes, 0, bytes.length); }
        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            boolean local = name.equals("net.minecraft.client.model.ModelBiped")
                || name.equals("com.fuzs.aquaacrobatics.client.model.AquaModelBipedLogic")
                || name.startsWith("com.fuzs.aquaacrobatics.client.model.AquaLotrSpecialArmorPoseBridge")
                || name.startsWith("com.lotrcharactercreation.client.model.")
                || name.startsWith("lotr.client.model.");
            if (!local) return super.loadClass(name, resolve);
            Class<?> c = findLoadedClass(name);
            if (c == null) {
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (in == null) throw new ClassNotFoundException(name);
                        byte[] buffer = new byte[4096]; int n;
                        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                    }
                    byte[] bytes = out.toByteArray();
                    if (aqua) {
                        if (production) bytes = names(bytes, true);
                        bytes = new AquaClientEntityTransformer().transform(name, name, bytes);
                        AquaProductionMappingsTest.verifyStructure(bytes);
                        if (name.equals("net.minecraft.client.model.ModelBiped")) {
                            org.objectweb.asm.tree.ClassNode node = AquaProductionMappingsTest.node(bytes);
                            assertNotNull(AquaProductionMappingsTest.methodNamed(node,
                                production ? "func_78086_a" : "setLivingAnimations"));
                        }
                        // Execute the SRG transformation against the test runtime's MCP superclass.
                        // Name/descriptor correctness is asserted above, before this test-only adapter.
                        if (production) bytes = names(bytes, false);
                    }
                    if (name.equals("net.minecraft.client.model.ModelBiped") || name.startsWith("lotr.client.model."))
                        bytes = renderSink(bytes);
                    c = defineClass(name, bytes, 0, bytes.length);
                } catch (Exception e) { throw new ClassNotFoundException(name, e); }
            }
            if (resolve) resolveClass(c);
            return c;
        }
    }
    // Keep actual model render/setup dispatch; replace only OpenGL output with a
    // recorder. No game/Forge classes or patched class files are stored in the repo.
    static byte[] renderSink(byte[] bytes) {
        ClassNode c = AquaProductionMappingsTest.node(bytes);
        for (MethodNode m : c.methods) for (AbstractInsnNode i : m.instructions.toArray()) {
            if (!(i instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode)i;
            if (call.owner.equals("net/minecraft/client/model/ModelRenderer")
                && call.name.equals("render") && call.desc.equals("(F)V")) {
                call.setOpcode(Opcodes.INVOKESTATIC);
                call.owner = Type.getInternalName(AquaModelPoseTest.class);
                call.name = "recordDraw";
                call.desc = "(Lnet/minecraft/client/model/ModelRenderer;F)V";
            } else if (call.owner.equals("org/lwjgl/opengl/GL11") && Type.getReturnType(call.desc).getSort() == Type.VOID) {
                Type[] args = Type.getArgumentTypes(call.desc);
                for (int a = args.length-1; a >= 0; --a)
                    m.instructions.insertBefore(call, new InsnNode(args[a].getSize() == 2 ? Opcodes.POP2 : Opcodes.POP));
                m.instructions.remove(call);
            }
        }
        return AquaProductionMappingsTest.bytes(c);
    }
    static byte[] names(byte[] bytes, final boolean srg) {
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(bytes).accept(new RemappingClassAdapter(writer, new Remapper() {
            @Override public String mapMethodName(String owner, String name, String desc) {
                String[][] pairs = {{"setRotationAngles", "func_78087_a"},
                    {"setLivingAnimations", "func_78086_a"}, {"render", "func_78088_a"}};
                // Only model method signatures, not ModelRenderer.render(float).
                if (!desc.equals("(FFFFFFLnet/minecraft/entity/Entity;)V")
                    && !desc.equals("(Lnet/minecraft/entity/EntityLivingBase;FFF)V")
                    && !desc.equals("(Lnet/minecraft/entity/Entity;FFFFFF)V")) return name;
                for (String[] pair : pairs) if (name.equals(pair[srg ? 0 : 1])) return pair[srg ? 1 : 0];
                return name;
            }
        }), ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }
    @After public void restoreEnvironment() {
        new AquaAcrobaticsCore().injectData(Collections.<String,Object>singletonMap("runtimeDeobfuscationEnabled", false));
        ClientServerGameplayState.setModernPlayerAnimations(previousModern);
    }
    static void angles(Object model, Entity entity) throws Exception {
        angles(model, entity, 0F);
    }
    static void angles(Object model, Entity entity, float limb) throws Exception {
        model.getClass().getMethod("setRotationAngles", float.class, float.class, float.class,
            float.class, float.class, float.class, Entity.class).invoke(model, limb, 0F, 0F, 0F, 0F, .0625F, entity);
    }
    static ModelRenderer arm(Object m, String side) throws Exception {
        return (ModelRenderer)m.getClass().getField("biped" + side + "Arm").get(m);
    }
    @Test public void idleActualModelsBeforeAndAfterAquaHaveTwoNeutralArms() throws Exception {
        new AquaAcrobaticsCore().injectData(Collections.<String,Object>singletonMap("runtimeDeobfuscationEnabled", false));
        ClientServerGameplayState.setModernPlayerAnimations(true);
        for (boolean aqua : new boolean[]{false, true}) {
            Models loader = new Models(aqua);
            for (String name : new String[]{"net.minecraft.client.model.ModelBiped",
                "com.lotrcharactercreation.client.model.PlayerManModelAdapter",
                "com.lotrcharactercreation.client.model.PlayerDwarfModelAdapter",
                "com.lotrcharactercreation.client.model.PlayerElfModelAdapter",
                "com.lotrcharactercreation.client.model.PlayerHobbitModelAdapter",
                "com.lotrcharactercreation.client.model.PlayerOrcModelAdapter"}) {
                Object model = loader.loadClass(name).newInstance();
                angles(model, null);
                for (String side : new String[]{"Right", "Left"}) {
                    ModelRenderer a = arm(model, side);
                    assertTrue(a.showModel); assertFalse(a.isHidden);
                    assertEquals(name + side, 0F, a.rotateAngleX, .11F);
                    assertEquals(name + side, 0F, a.rotateAngleZ, .11F);
                }
            }
        }
    }
    @Test public void missingEntityClearsPriorSwimmingOnGeneratedLivingOverride() throws Exception {
        for (boolean production : new boolean[]{false, true}) {
            new AquaAcrobaticsCore().injectData(Collections.<String,Object>singletonMap("runtimeDeobfuscationEnabled", production));
            Object model = new Models(true, production).loadClass("net.minecraft.client.model.ModelBiped").newInstance();
            IModelBipedSwimming state = (IModelBipedSwimming)model;
            state.setSwimAnimation(1F);
            // A model reused for a non-Aqua/null preview must not retain the previous player.
            model.getClass().getMethod("setLivingAnimations", EntityLivingBase.class, float.class, float.class, float.class)
                .invoke(model, null, 0F, 0F, .5F);
            assertEquals("stale swim state", 0F, state.getSwimAnimation(), 0F);
            angles(model, null);
            assertEquals(0F, arm(model, "Right").rotateAngleX, .11F);
            assertEquals(0F, arm(model, "Left").rotateAngleX, .11F);
            assertEquals(.1F, arm(model, "Right").rotateAngleZ, .001F);
            assertEquals(-.1F, arm(model, "Left").rotateAngleZ, .001F);
        }
    }
    @Test public void racialModelsRetainTheirOwnArmGeometryAfterSkinModReplacement() throws Exception {
        for (int mode = 0; mode < 3; mode++) {
            boolean production = mode == 2;
            new AquaAcrobaticsCore().injectData(Collections.<String,Object>singletonMap("runtimeDeobfuscationEnabled", production));
            Models loader = new Models(mode != 0, production);
            for (String race : new String[]{"Man", "Elf", "Dwarf", "Hobbit", "Orc"}) {
                Object model = loader.loadClass("com.lotrcharactercreation.client.model.Player" + race + "ModelAdapter").newInstance();
                ModelRenderer right = arm(model, "Right"), left = arm(model, "Left");
                // Equivalent to FoamFix/Ears.beforeRender's two arm assignments. Its
                // modern left arm uses (32,48), not the LOTR mirrored (40,16) region.
                ModelRenderer modernLeft = new ModelRenderer((net.minecraft.client.model.ModelBase)model, 32, 48);
                modernLeft.addBox(-1F, -2F, -2F, 4, 12, 4);
                modernLeft.setRotationPoint(5F, 2F, 0F);
                ModelRenderer modernRight = new ModelRenderer((net.minecraft.client.model.ModelBase)model, 40, 16);
                modernRight.addBox(-3F, -2F, -2F, 4, 12, 4);
                modernRight.setRotationPoint(-5F, 2F, 0F);
                model.getClass().getField("bipedLeftArm").set(model, modernLeft);
                model.getClass().getField("bipedRightArm").set(model, modernRight);
                angles(model, null);
                assertSame(race + " left arm ownership", left, arm(model, "Left"));
                assertSame(race + " right arm ownership", right, arm(model, "Right"));
                assertTrue(left.mirror);
                assertEquals(1, left.cubeList.size());
                assertEquals(1, right.cubeList.size());
                assertTrue(left.showModel && right.showModel);
                assertFalse(left.isHidden || right.isHidden);
            }
        }
    }

    // Inert entity fixture: no Minecraft singleton, world, spawn, or OpenGL context.
    // Only the entity presentation API is simulated; model and animation code are real.
    static EntityPlayer player(Models loader) throws Exception {
        ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String name = "aqua/test/PosePlayer";
        w.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, name, null, "net/minecraft/entity/player/EntityPlayer",
            new String[]{Type.getInternalName(IPlayerResizeable.class)});
        w.visitField(Opcodes.ACC_PUBLIC, "swim", "F", null, null).visitEnd();
        w.visitField(Opcodes.ACC_PUBLIC, "pose", Type.getDescriptor(Pose.class), null, null).visitEnd();
        for (Method m : IPlayerResizeable.class.getMethods()) {
            MethodVisitor v = w.visitMethod(Opcodes.ACC_PUBLIC, m.getName(), Type.getMethodDescriptor(m), null, null);
            v.visitCode();
            if (m.getName().equals("getSwimAnimation")) {
                v.visitVarInsn(Opcodes.ALOAD, 0); v.visitFieldInsn(Opcodes.GETFIELD, name, "swim", "F");
                v.visitInsn(Opcodes.FRETURN);
            } else if (m.getName().equals("getPose")) {
                v.visitVarInsn(Opcodes.ALOAD, 0); v.visitFieldInsn(Opcodes.GETFIELD, name, "pose", Type.getDescriptor(Pose.class));
                v.visitInsn(Opcodes.ARETURN);
            } else if (m.getReturnType() == void.class) v.visitInsn(Opcodes.RETURN);
            else if (m.getReturnType() == float.class) { v.visitInsn(Opcodes.FCONST_0); v.visitInsn(Opcodes.FRETURN); }
            else if (m.getReturnType() == boolean.class) { v.visitInsn(Opcodes.ICONST_0); v.visitInsn(Opcodes.IRETURN); }
            else { v.visitInsn(Opcodes.ACONST_NULL); v.visitInsn(Opcodes.ARETURN); }
            v.visitMaxs(0, 0); v.visitEnd();
        }
        MethodVisitor held = w.visitMethod(Opcodes.ACC_PUBLIC, "getHeldItem", "()Lnet/minecraft/item/ItemStack;", null, null);
        held.visitCode(); held.visitInsn(Opcodes.ACONST_NULL); held.visitInsn(Opcodes.ARETURN); held.visitMaxs(0,0); held.visitEnd();
        MethodVisitor sneak = w.visitMethod(Opcodes.ACC_PUBLIC, "isSneaking", "()Z", null, null);
        sneak.visitCode(); sneak.visitInsn(Opcodes.ICONST_0); sneak.visitInsn(Opcodes.IRETURN); sneak.visitMaxs(0,0); sneak.visitEnd();
        w.visitEnd();
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field f = unsafe.getDeclaredField("theUnsafe"); f.setAccessible(true);
        EntityPlayer p = (EntityPlayer)unsafe.getMethod("allocateInstance", Class.class).invoke(f.get(null), loader.fixture(w.toByteArray()));
        p.getClass().getField("pose").set(p, Pose.STANDING);
        return p;
    }
    @SuppressWarnings("unchecked")
    static Map<EntityPlayer, Object> profiles() throws Exception {
        Field f = CharacterCreationIntegration.class.getDeclaredField("PLAYER_PROFILE_CACHE"); f.setAccessible(true);
        return (Map<EntityPlayer,Object>)f.get(null);
    }
    static void racialProfile(EntityPlayer p) throws Exception {
        Constructor<?> c = CharacterCreationIntegration.BodyProfile.class.getDeclaredConstructors()[0]; c.setAccessible(true);
        profiles().put(p, c.newInstance(true, "ELF", .6F, 1.8F, 1.62F, 1F, false));
    }
    static void living(Object model, EntityLivingBase entity) throws Exception {
        model.getClass().getMethod("setLivingAnimations", EntityLivingBase.class, float.class, float.class, float.class)
            .invoke(model, entity, 0F, 0F, .5F);
    }
    static void finiteArms(Object model) throws Exception {
        for (String side : new String[]{"Left", "Right"}) {
            ModelRenderer a = arm(model, side);
            assertTrue(a.showModel); assertFalse(a.isHidden);
            for (float f : new float[]{a.rotateAngleX,a.rotateAngleY,a.rotateAngleZ,a.rotationPointX,a.rotationPointY,a.rotationPointZ})
                assertTrue("nonfinite arm pose", Float.isFinite(f));
        }
    }
    @Test public void swimCrawlAndReturnToLandKeepBothOwnedArmsInBothNamespaces() throws Exception {
        for (boolean production : new boolean[]{false,true}) {
            new AquaAcrobaticsCore().injectData(Collections.<String,Object>singletonMap("runtimeDeobfuscationEnabled", production));
            Models loader = new Models(true, production);
            EntityPlayer p = player(loader); racialProfile(p);
            try {
                for (String race : new String[]{"Man","Elf","Dwarf","Hobbit","Orc"}) {
                    Object m = loader.loadClass("com.lotrcharactercreation.client.model.Player"+race+"ModelAdapter").newInstance();
                    for (String transition : new String[]{"swimming", "crawling"}) {
                        // Both use the canonical SWIMMING presentation pose; water status differs
                        // in the entity logic, not the limb blend under test here.
                        p.getClass().getField("pose").set(p, Pose.SWIMMING);
                        p.getClass().getField("swim").setFloat(p, 1F);
                        living(m,p); angles(m,p,18F); finiteArms(m);
                        assertEquals(transition, (float)Math.PI/4, arm(m,"Right").rotateAngleX, .001F);
                        assertEquals(transition, (float)Math.PI/4, arm(m,"Left").rotateAngleX, .001F);
                        p.getClass().getField("pose").set(p, Pose.STANDING);
                        p.getClass().getField("swim").setFloat(p, 0F);
                        living(m,p); angles(m,p); finiteArms(m);
                        assertEquals(0F,arm(m,"Left").rotateAngleX,.001F);
                        assertEquals(0F,arm(m,"Right").rotateAngleX,.001F);
                        assertEquals(-.1F,arm(m,"Left").rotateAngleZ,.001F);
                        assertEquals(.1F,arm(m,"Right").rotateAngleZ,.001F);
                        m.getClass().getField("heldItemRight").setInt(m,1);
                        angles(m,p); finiteArms(m);
                        assertEquals(-(float)Math.PI/10,arm(m,"Right").rotateAngleX,.001F);
                        assertEquals(0F,arm(m,"Left").rotateAngleX,.001F);
                        m.getClass().getField("heldItemRight").setInt(m,0);
                    }
                }
            } finally { profiles().remove(p); }
        }
    }
    @Test public void firstPersonRacialAnglesDoNotReapplyThirdPersonSwimming() throws Exception {
        Models loader = new Models(true);
        EntityPlayer p = player(loader); racialProfile(p);
        try {
            Object m = loader.loadClass("com.lotrcharactercreation.client.model.PlayerElfModelAdapter").newInstance();
            p.getClass().getField("swim").setFloat(p, 1F);
            FirstPersonArmRenderContext.push();
            try { angles(m,p,18F); } finally { FirstPersonArmRenderContext.pop(); }
            assertEquals(0F,arm(m,"Right").rotateAngleX,.001F);
            assertEquals(0F,((IModelBipedSwimming)m).getSwimAnimation(),0F);
        } finally { profiles().remove(p); }
    }
    @Test public void directPreviewAnglesWithoutLivingCallbackDiscardPreviousEntityBlend() throws Exception {
        Models loader = new Models(true);
        Object m = loader.loadClass("net.minecraft.client.model.ModelBiped").newInstance();
        ((IModelBipedSwimming)m).setSwimAnimation(1F);
        angles(m,null,18F);
        assertEquals(0F,arm(m,"Left").rotateAngleX,.001F);
        assertEquals(0F,((IModelBipedSwimming)m).getSwimAnimation(),0F);
    }
    @Test public void previewPresentationResetRestoresGeometryAndNeutralPose() throws Exception {
        Models loader = new Models(true);
        for (String race : new String[]{"Man","Elf","Dwarf","Hobbit","Orc"}) {
            Object m = loader.loadClass("com.lotrcharactercreation.client.model.Player"+race+"ModelAdapter").newInstance();
            ModelRenderer original = arm(m,"Left");
            m.getClass().getField("bipedLeftArm").set(m,new ModelRenderer((net.minecraft.client.model.ModelBase)m));
            m.getClass().getField("heldItemRight").setInt(m,3);
            m.getClass().getField("isSneak").setBoolean(m,true);
            m.getClass().getMethod("resetPlayerPresentation").invoke(m);
            angles(m,null); finiteArms(m);
            assertSame(original,arm(m,"Left"));
            assertEquals(0F,arm(m,"Left").rotateAngleX,.001F);
            assertEquals(0F,arm(m,"Right").rotateAngleX,.001F);
        }
    }
    @Test public void actualModelRenderEntryDrawsBothArmsForWorldAndPreviewSetup() throws Exception {
        for (boolean production : new boolean[]{false,true}) {
            new AquaAcrobaticsCore().injectData(Collections.<String,Object>singletonMap("runtimeDeobfuscationEnabled", production));
            Models loader = new Models(true,production);
            for (String race : new String[]{"Man","Elf","Dwarf","Hobbit","Orc"}) {
                Object m = loader.loadClass("com.lotrcharactercreation.client.model.Player"+race+"ModelAdapter").newInstance();
                m.getClass().getField("isChild").setBoolean(m,false);
                for (boolean preview : new boolean[]{false,true}) {
                    if (preview) m.getClass().getMethod("resetPlayerPresentation").invoke(m);
                    drawn.clear();
                    m.getClass().getMethod("render",Entity.class,float.class,float.class,float.class,float.class,float.class,float.class)
                        .invoke(m,null,0F,0F,0F,0F,0F,.0625F);
                    assertTrue(race + " left arm draw", drawn.contains(arm(m,"Left")));
                    assertTrue(race + " right arm draw", drawn.contains(arm(m,"Right")));
                    finiteArms(m);
                }
            }
        }
        drawn.clear();
    }
}
