package com.fuzs.aquaacrobatics.core.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Inserts the Phase 2B pose-update bridge immediately after Forge's player
 * post-tick callback. The FML call is stable in both dev and runtime bytecode;
 * its descriptor also supplies the correct dev/obfuscated player descriptor.
 */
public final class AquaEntityPlayerTransformer implements IClassTransformer {

    private static final String ENTITY_PLAYER = "net.minecraft.entity.player.EntityPlayer";
    private static final String FML_COMMON_HANDLER = "cpw/mods/fml/common/FMLCommonHandler";
    private static final String POST_TICK = "onPlayerPostTick";
    private static final String HOOK_OWNER = "com/fuzs/aquaacrobatics/core/asm/AquaPlayerAsmHooks";
    private static final String PRE_POST_TICK_HOOK = "onPlayerPrePostTick";
    private static final String POST_TICK_POSE_HOOK = "onPlayerPoseUpdate";
    private static final String STATE_HOLDER = "com/fuzs/aquaacrobatics/entity/player/IAquaPlayerStateHolder";
    private static final String STATE_OWNER = "com/fuzs/aquaacrobatics/entity/player/AquaPlayerState";
    private static final String STATE_DESCRIPTOR = "L" + STATE_OWNER + ";";
    private static final String ENTITY_SIZE_OWNER = "com/fuzs/aquaacrobatics/entity/EntitySize";
    private static final String ENTITY_SIZE_DESCRIPTOR = "L" + ENTITY_SIZE_OWNER + ";";
    private static final String STATE_FIELD = "aqua$playerState";
    private static final String STATE_GETTER = "getAquaPlayerState";
    private static final String GET_WIDTH = "getWidth";
    private static final String GET_HEIGHT = "getHeight";
    private static final String RESIZE_LOGIC = "com/fuzs/aquaacrobatics/entity/player/AquaPlayerResizeLogic";
    private static final String RESIZEABLE_OWNER = "com/fuzs/aquaacrobatics/entity/player/IPlayerResizeable";
    private static final String RESIZEABLE_DESCRIPTOR = "L" + RESIZEABLE_OWNER + ";";
    private static final String RECALCULATE_SIZE = "recalculateSize";
    private static final String IS_RESIZING_ALLOWED = "isResizingAllowed";
    private static final String LIFECYCLE_LOGIC =
        "com/fuzs/aquaacrobatics/entity/player/AquaPlayerLifecycleLogic";
    private static final String PREPARE_PLAYER_TO_SPAWN_MCP = "preparePlayerToSpawn";
    private static final String PREPARE_PLAYER_TO_SPAWN_SRG = "func_70065_x";
    private static final String PREPARE_PLAYER_TO_SPAWN_NOTCH = "A";
    private static final String SLEEP_IN_BED_MCP = "sleepInBedAt";
    private static final String SLEEP_IN_BED_SRG = "func_71018_a";
    private static final String SLEEP_IN_BED_NOTCH = "a";
    private static final String SET_SIZE_MCP = "setSize";
    private static final String SET_SIZE_SRG = "func_70105_a";
    private static final String SET_SIZE_NOTCH = "a";
    private static final String LEGACY_BOB_LOGIC =
        "com/fuzs/aquaacrobatics/entity/player/AquaPlayerLegacyBobLogic";
    private static final String ON_LIVING_UPDATE_MCP = "onLivingUpdate";
    private static final String ON_LIVING_UPDATE_SRG = "func_70636_d";
    private static final String ON_LIVING_UPDATE_NOTCH = "e";
    private static final String SIZE_METADATA_LOGIC =
        "com/fuzs/aquaacrobatics/entity/player/AquaPlayerSizeMetadataLogic";
    private static final String INITIALIZE_SIZE_METADATA = "initialize";
    private static final String GET_AQUA_EYE_HEIGHT = "getEyeHeight";
    private static final String VANILLA_EYE_HEIGHT_MCP = "getEyeHeight";
    private static final String VANILLA_EYE_HEIGHT_SRG = "func_70047_e";
    private static final String VANILLA_EYE_HEIGHT_NOTCH = "g";
    private static final String GET_STANDING_EYE_HEIGHT = "getStandingEyeHeight";
    private static final String GET_SIZE = "getSize";
    private static final String PRESENTATION_LOGIC =
        "com/fuzs/aquaacrobatics/entity/player/AquaPlayerPresentationLogic";
    private static final String GET_EYES_IN_WATER_PLAYER = "getEyesInWaterPlayer";
    private static final String GET_SWIM_ANIMATION = "getSwimAnimation";
    private static final String IS_ACTUALLY_SWIMMING = "isActuallySwimming";
    private static final String IS_VISUALLY_SWIMMING = "isVisuallySwimming";
    private static final String DATA_WATCHER_LOGIC = "com/fuzs/aquaacrobatics/entity/player/AquaPlayerDataWatcherLogic";
    private static final String POSE_OWNER = "com/fuzs/aquaacrobatics/entity/Pose";
    private static final String POSE_DESCRIPTOR = "L" + POSE_OWNER + ";";
    private static final String GET_POSE = "getPose";
    private static final String SET_POSE = "setPose";
    private static final String IS_FORCING_CRAWLING = "isForcingCrawling";
    private static final String SET_FORCING_CRAWLING = "setForcingCrawling";
    private static final String COMPATIBILITY_LOGIC =
        "com/fuzs/aquaacrobatics/entity/player/AquaPlayerCompatibilityLogic";
    private static final String IS_ACTUALLY_SNEAKING = "isActuallySneaking";
    private static final String GET_SHOULD_BE_DEAD = "getShouldBeDead";
    private static final String CAN_FORCE_CRAWLING = "canForceCrawling";
    private static final String IS_POSE_CLEAR = "isPoseClear";
    private static final String WATER_LOGIC = "com/fuzs/aquaacrobatics/entity/player/AquaPlayerWaterLogic";
    private static final String ON_ENTITY_UPDATE_MCP = "onEntityUpdate";
    private static final String ON_ENTITY_UPDATE_SRG = "func_70030_z";
    private static final String GET_FLAG_SRG = "func_70083_f";
    private static final String SET_FLAG_SRG = "func_70052_a";
    private static final String CAN_SWIM = "canSwim";
    private static final String UPDATE_SWIMMING = "updateSwimming";
    private static final String GET_WATER_VISION = "getWaterVision";
    private static final String IS_SWIMMING = "isSwimming";
    private static final String SET_SWIMMING = "setSwimming";
    private static final String DATA_WATCHER_CALLBACK_SRG = "func_145781_i";
    private static final String DATA_WATCHER_CALLBACK_NOTCH = "i";
    private static final String SWIM_TRAVEL_LOGIC =
        "com/fuzs/aquaacrobatics/entity/player/AquaSwimmingTravelLogic";
    private static final String JUMPING_ACCESS = "com/fuzs/aquaacrobatics/entity/player/IAquaJumpingAccess";
    private static final String SWIM_TRAVEL_MCP = "moveEntityWithHeading";
    private static final String SWIM_TRAVEL_SRG = "func_70612_e";
    private static final String SWIM_TRAVEL_NOTCH = "e";
    private static final String JUMPING_ACCESSOR = "aqua$isJumping";
    private static final String JUMPING_FIELD_MCP = "isJumping";
    private static final String JUMPING_FIELD_NOTCH = "bc";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENTITY_PLAYER.equals(transformedName)) return basicClass;
        if (basicClass == null) throw new IllegalStateException("Aqua EntityPlayer transformer received null bytecode");

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        this.addResizeableInterface(classNode);
        this.addStateHolder(classNode);
        this.verifyStateHolder(classNode);
        this.addSizeFacades(classNode);
        this.verifySizeFacades(classNode);
        this.addResizeFacades(classNode);
        this.verifyResizeFacades(classNode);
        this.addPresentationFacades(classNode);
        this.verifyPresentationFacades(classNode);
        this.addDataWatcherPlumbing(classNode);
        this.verifyDataWatcherPlumbing(classNode);
        this.addCompatibilityFacades(classNode);
        this.verifyCompatibilityFacades(classNode);
        this.addWaterStatePlumbing(classNode);
        this.verifyWaterStatePlumbing(classNode);
        this.addLifecycleSleepPlumbing(classNode);
        this.verifyLifecycleSleepPlumbing(classNode);
        this.addVanillaEyeHeightBridge(classNode);
        this.verifyVanillaEyeHeightBridge(classNode);
        this.addLegacyBobBridge(classNode);
        this.verifyLegacyBobBridge(classNode);
        this.addSwimTravelPlumbing(classNode);
        this.verifySwimTravelPlumbing(classNode);
        // Add this after every other constructor augmentation so the Phase 2M
        // metadata bridge remains immediately before the constructor RETURN.
        this.addSizeMetadataPlumbing(classNode);
        this.verifySizeMetadataPlumbing(classNode);
        this.verifyResizeableInterface(classNode);
        this.insertPostTickBridges(classNode, name, transformedName);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    /**
     * Preserves the accepted order around the single Forge callback:
     * Aqua presentation, Forge post tick, then Aqua pose update.
     */
    private void insertPostTickBridges(ClassNode classNode, String name, String transformedName) {
        int matches = 0;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {

                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (!FML_COMMON_HANDLER.equals(invocation.owner) || !POST_TICK.equals(invocation.name)
                    || !invocation.desc.endsWith(")V")) continue;

                InsnList preHook = new InsnList();
                preHook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                preHook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HOOK_OWNER,
                    PRE_POST_TICK_HOOK,
                    invocation.desc,
                    false));
                method.instructions.insertBefore(instruction, preHook);

                InsnList poseHook = new InsnList();
                poseHook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                // The post-tick invocation descriptor contains EntityPlayer in its
                // current namespace (MCP in dev, notch at runtime), so reuse it.
                poseHook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    HOOK_OWNER,
                    POST_TICK_POSE_HOOK,
                    invocation.desc,
                    false));
                method.instructions.insert(instruction, poseHook);
                ++matches;
            }
        }
        if (matches != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer transformer expected exactly one FML onPlayerPostTick call in " + name
                    + " (transformed " + transformedName + "), found " + matches);
        }
        this.verifyPostTickBridgeOrder(classNode);
    }

    private void verifyPostTickBridgeOrder(ClassNode classNode) {
        int preIndex = -1;
        int forgeIndex = -1;
        int poseIndex = -1;
        int index = 0;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext(), ++index) {

                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (HOOK_OWNER.equals(invocation.owner) && PRE_POST_TICK_HOOK.equals(invocation.name)) {
                    if (preIndex != -1) throw new IllegalStateException("Aqua EntityPlayer has duplicate pre-post-tick hooks");
                    preIndex = index;
                } else if (FML_COMMON_HANDLER.equals(invocation.owner) && POST_TICK.equals(invocation.name)) {
                    if (forgeIndex != -1) throw new IllegalStateException("Aqua EntityPlayer has duplicate Forge post-tick hooks");
                    forgeIndex = index;
                } else if (HOOK_OWNER.equals(invocation.owner) && POST_TICK_POSE_HOOK.equals(invocation.name)) {
                    if (poseIndex != -1) throw new IllegalStateException("Aqua EntityPlayer has duplicate post-tick pose hooks");
                    poseIndex = index;
                }
            }
        }
        if (preIndex == -1 || forgeIndex == -1 || poseIndex == -1 || preIndex >= forgeIndex || forgeIndex >= poseIndex) {
            throw new IllegalStateException(
                "Aqua EntityPlayer pre/Forge/pose hook order invalid: pre=" + preIndex + ", forge=" + forgeIndex
                    + ", pose=" + poseIndex);
        }
    }

    private void addStateHolder(ClassNode classNode) {
        if (classNode.interfaces.contains(STATE_HOLDER)) {
            throw new IllegalStateException("Aqua EntityPlayer already implements " + STATE_HOLDER);
        }
        for (FieldNode field : classNode.fields) {
            if (STATE_FIELD.equals(field.name)) {
                throw new IllegalStateException("Aqua EntityPlayer already contains field " + STATE_FIELD);
            }
        }
        for (MethodNode method : classNode.methods) {
            if (STATE_GETTER.equals(method.name)) {
                throw new IllegalStateException(
                    "Aqua EntityPlayer already contains method " + STATE_GETTER + method.desc);
            }
        }

        classNode.interfaces.add(STATE_HOLDER);
        classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, STATE_FIELD, STATE_DESCRIPTOR, null, null));
        classNode.methods.add(this.createStateGetter(classNode.name));
    }

    /** Phase 2U: replaces EntityPlayerMixin's final, interface-only responsibility. */
    private void addResizeableInterface(ClassNode classNode) {
        if (classNode.interfaces.contains(RESIZEABLE_OWNER)) {
            throw new IllegalStateException("Aqua EntityPlayer already implements " + RESIZEABLE_OWNER);
        }
        classNode.interfaces.add(RESIZEABLE_OWNER);
    }

    private void addDataWatcherPlumbing(ClassNode classNode) {
        this.requireMissingMethod(classNode, GET_POSE, "()" + POSE_DESCRIPTOR);
        this.requireMissingMethod(classNode, SET_POSE, "(" + POSE_DESCRIPTOR + ")V");
        this.requireMissingMethod(classNode, IS_FORCING_CRAWLING, "()Z");
        this.requireMissingMethod(classNode, SET_FORCING_CRAWLING, "(Z)V");

        String callbackName = this.getDataWatcherCallbackName(classNode);
        this.requireMissingMethod(classNode, callbackName, "(I)V");
        String playerDescriptor = "L" + classNode.name + ";";
        int constructorReturns = 0;
        for (MethodNode method : classNode.methods) {
            if (!"<init>".equals(method.name)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {

                if (instruction.getOpcode() != Opcodes.RETURN) continue;
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    DATA_WATCHER_LOGIC,
                    "register",
                    "(" + playerDescriptor + ")V",
                    false));
                method.instructions.insertBefore(instruction, hook);
                ++constructorReturns;
            }
        }
        if (constructorReturns != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer transformer expected exactly one constructor return for DataWatcher registration, found "
                    + constructorReturns);
        }

        classNode.methods.add(this.createDataWatcherGetter(classNode.name, playerDescriptor, GET_POSE, "()" + POSE_DESCRIPTOR));
        classNode.methods.add(this.createDataWatcherSetter(
            classNode.name,
            playerDescriptor,
            SET_POSE,
            "(" + POSE_DESCRIPTOR + ")V"));
        classNode.methods.add(
            this.createDataWatcherGetter(classNode.name, playerDescriptor, IS_FORCING_CRAWLING, "()Z"));
        classNode.methods.add(
            this.createDataWatcherSetter(classNode.name, playerDescriptor, SET_FORCING_CRAWLING, "(Z)V"));
        classNode.methods.add(this.createDataWatcherCallback(classNode.superName, playerDescriptor, callbackName));
    }

    /** Phase 2F: adds the pure IPlayerResizeable facades after Mixin has stopped supplying them. */
    private void addCompatibilityFacades(ClassNode classNode) {
        this.requireMissingMethod(classNode, IS_ACTUALLY_SNEAKING, "()Z");
        this.requireMissingMethod(classNode, GET_SHOULD_BE_DEAD, "()Z");
        this.requireMissingMethod(classNode, CAN_FORCE_CRAWLING, "()Z");
        this.requireMissingMethod(classNode, IS_POSE_CLEAR, "(" + POSE_DESCRIPTOR + ")Z");

        String playerDescriptor = "L" + classNode.name + ";";
        classNode.methods.add(this.createCompatibilityGetter(classNode.name, playerDescriptor, IS_ACTUALLY_SNEAKING));
        classNode.methods.add(this.createCompatibilityGetter(classNode.name, playerDescriptor, GET_SHOULD_BE_DEAD));
        classNode.methods.add(this.createCompatibilityGetter(classNode.name, playerDescriptor, CAN_FORCE_CRAWLING));
        classNode.methods.add(this.createPoseClearFacade(classNode.name, playerDescriptor));
    }

    /** Phase 2H: owns the inherited water tick override and pure water-state facades. */
    private void addWaterStatePlumbing(ClassNode classNode) {
        String playerDescriptor = "L" + classNode.name + ";";
        String onEntityUpdate = this.getOnEntityUpdateName(classNode);
        this.requireMissingMethod(classNode, onEntityUpdate, "()V");
        this.requireMissingMethod(classNode, CAN_SWIM, "()Z");
        this.requireMissingMethod(classNode, UPDATE_SWIMMING, "()V");
        this.requireMissingMethod(classNode, GET_WATER_VISION, "()F");
        this.requireMissingMethod(classNode, IS_SWIMMING, "()Z");
        this.requireMissingMethod(classNode, SET_SWIMMING, "(Z)V");

        classNode.methods.add(this.createWaterStateUpdate(classNode.superName, playerDescriptor, onEntityUpdate));
        classNode.methods.add(this.createWaterLogicGetter(playerDescriptor, CAN_SWIM, "()Z"));
        classNode.methods.add(this.createWaterLogicVoidMethod(playerDescriptor, UPDATE_SWIMMING));
        classNode.methods.add(this.createWaterLogicGetter(playerDescriptor, GET_WATER_VISION, "()F"));
        // Forge's runtime deobfuscation uses SRG member names. Do not infer a member
        // namespace from the ClassNode superclass: class and member namespaces can differ.
        classNode.methods.add(this.createSwimmingGetter(classNode.name, playerDescriptor, com.fuzs.aquaacrobatics.core.AquaAcrobaticsCore.isDevEnv() ? "getFlag" : GET_FLAG_SRG));
        classNode.methods.add(this.createSwimmingSetter(classNode.name, com.fuzs.aquaacrobatics.core.AquaAcrobaticsCore.isDevEnv() ? "setFlag" : SET_FLAG_SRG));
    }

    private MethodNode createWaterStateUpdate(String superName, String playerDescriptor, String onEntityUpdate) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, onEntityUpdate, "()V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, superName, onEntityUpdate, "()V", false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            HOOK_OWNER,
            "onPlayerWaterStateUpdate",
            "(" + playerDescriptor + ")V",
            false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private MethodNode createWaterLogicGetter(String playerDescriptor, String name, String descriptor) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            WATER_LOGIC,
            name,
            "(" + playerDescriptor + ")" + descriptor.substring(2),
            false));
        method.instructions.add(new InsnNode("()F".equals(descriptor) ? Opcodes.FRETURN : Opcodes.IRETURN));
        return method;
    }

    private MethodNode createWaterLogicVoidMethod(String playerDescriptor, String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, "()V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            WATER_LOGIC,
            name,
            "(" + playerDescriptor + ")V",
            false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private MethodNode createSwimmingGetter(String owner, String playerDescriptor, String getFlagName) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, IS_SWIMMING, "()Z", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, getFlagName, "(I)Z", false));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            WATER_LOGIC,
            IS_SWIMMING,
            "(" + playerDescriptor + "Z)Z",
            false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    private MethodNode createSwimmingSetter(String owner, String setFlagName) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, SET_SWIMMING, "(Z)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new IntInsnNode(Opcodes.BIPUSH, 6));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, setFlagName, "(IZ)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private MethodNode createCompatibilityGetter(String owner, String playerDescriptor, String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, "()Z", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            COMPATIBILITY_LOGIC,
            name,
            "(" + playerDescriptor + ")Z",
            false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    private MethodNode createPoseClearFacade(String owner, String playerDescriptor) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, IS_POSE_CLEAR, "(" + POSE_DESCRIPTOR + ")Z", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            COMPATIBILITY_LOGIC,
            IS_POSE_CLEAR,
            "(" + playerDescriptor + POSE_DESCRIPTOR + ")Z",
            false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    private MethodNode createDataWatcherGetter(String owner, String playerDescriptor, String name, String descriptor) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            DATA_WATCHER_LOGIC,
            name,
            "(" + playerDescriptor + ")" + descriptor.substring(2),
            false));
        method.instructions.add(new InsnNode("()Z".equals(descriptor) ? Opcodes.IRETURN : Opcodes.ARETURN));
        return method;
    }

    private MethodNode createDataWatcherSetter(String owner, String playerDescriptor, String name, String descriptor) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode("(Z)V".equals(descriptor) ? Opcodes.ILOAD : Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            DATA_WATCHER_LOGIC,
            name,
            "(" + playerDescriptor + descriptor.substring(1),
            false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private MethodNode createDataWatcherCallback(String superName, String playerDescriptor, String callbackName) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, callbackName, "(I)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            DATA_WATCHER_LOGIC,
            "onDataWatcherChanged",
            "(" + playerDescriptor + "I)V",
            false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, superName, callbackName, "(I)V", false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        return method;
    }

    private String getDataWatcherCallbackName(ClassNode classNode) {
        return "net/minecraft/entity/EntityLivingBase".equals(classNode.superName)
            ? DATA_WATCHER_CALLBACK_SRG
            : DATA_WATCHER_CALLBACK_NOTCH;
    }

    private String getOnEntityUpdateName(ClassNode classNode) {
        // runClient uses MCP/deobfuscated members; production uses Forge SRG members.
        return com.fuzs.aquaacrobatics.core.AquaAcrobaticsCore.isDevEnv()
            ? ON_ENTITY_UPDATE_MCP
            : ON_ENTITY_UPDATE_SRG;
    }

    private void requireMissingMethod(ClassNode classNode, String name, String descriptor) {
        for (MethodNode method : classNode.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                throw new IllegalStateException("Aqua EntityPlayer already contains method " + name + descriptor);
            }
        }
    }

    private MethodNode createStateGetter(String owner) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, STATE_GETTER, "()" + STATE_DESCRIPTOR, null, null);
        LabelNode initialized = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, owner, STATE_FIELD, STATE_DESCRIPTOR));
        method.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL, initialized));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new TypeInsnNode(Opcodes.NEW, STATE_OWNER));
        method.instructions.add(new InsnNode(Opcodes.DUP));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, STATE_OWNER, "<init>", "()V", false));
        method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, STATE_FIELD, STATE_DESCRIPTOR));
        method.instructions.add(initialized);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, owner, STATE_FIELD, STATE_DESCRIPTOR));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    /** Phase 2J: owns the pure width/height reads from the existing ASM player state. */
    private void addSizeFacades(ClassNode classNode) {
        this.requireMissingMethod(classNode, GET_WIDTH, "()F");
        this.requireMissingMethod(classNode, GET_HEIGHT, "()F");
        classNode.methods.add(this.createSizeFacade(classNode.name, GET_WIDTH, "width"));
        classNode.methods.add(this.createSizeFacade(classNode.name, GET_HEIGHT, "height"));
    }

    private MethodNode createSizeFacade(String playerOwner, String name, String sizeField) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL, name, "()F", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKEVIRTUAL,
            playerOwner,
            STATE_GETTER,
            "()" + STATE_DESCRIPTOR,
            false));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, STATE_OWNER, "size", ENTITY_SIZE_DESCRIPTOR));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, ENTITY_SIZE_OWNER, sizeField, "F"));
        method.instructions.add(new InsnNode(Opcodes.FRETURN));
        return method;
    }

    /** Phase 2Q: exposes shared physical resize policy without ASM vanilla-member references. */
    private void addResizeFacades(ClassNode classNode) {
        this.requireMissingMethod(classNode, RECALCULATE_SIZE, "()V");
        this.requireMissingMethod(classNode, IS_RESIZING_ALLOWED, "()Z");
        String playerDescriptor = "L" + classNode.name + ";";
        classNode.methods.add(this.createResizeFacade(playerDescriptor, RECALCULATE_SIZE, "()V"));
        classNode.methods.add(this.createResizeFacade(playerDescriptor, IS_RESIZING_ALLOWED, "()Z"));
    }

    private MethodNode createResizeFacade(String playerDescriptor, String name, String descriptor) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, RESIZEABLE_OWNER));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            RESIZE_LOGIC,
            name,
            "(" + playerDescriptor + RESIZEABLE_DESCRIPTOR + ")" + descriptor.substring(2),
            false));
        method.instructions.add(new InsnNode("()Z".equals(descriptor) ? Opcodes.IRETURN : Opcodes.RETURN));
        return method;
    }

    /** Phase 2M: owns constructor metadata setup and its pure size/eye façade methods. */
    private void addSizeMetadataPlumbing(ClassNode classNode) {
        this.requireMissingMethod(classNode, GET_AQUA_EYE_HEIGHT, "(" + POSE_DESCRIPTOR + ENTITY_SIZE_DESCRIPTOR + ")F");
        this.requireMissingMethod(
            classNode,
            GET_STANDING_EYE_HEIGHT,
            "(" + POSE_DESCRIPTOR + ENTITY_SIZE_DESCRIPTOR + ")F");
        this.requireMissingMethod(classNode, GET_SIZE, "(" + POSE_DESCRIPTOR + ")" + ENTITY_SIZE_DESCRIPTOR);

        String playerDescriptor = "L" + classNode.name + ";";
        int constructorReturns = 0;
        for (MethodNode method : classNode.methods) {
            if (!"<init>".equals(method.name)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {

                if (instruction.getOpcode() != Opcodes.RETURN) continue;
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
                hook.add(new MethodInsnNode(
                    Opcodes.INVOKESTATIC,
                    SIZE_METADATA_LOGIC,
                    INITIALIZE_SIZE_METADATA,
                    "(" + playerDescriptor + ")V",
                    false));
                method.instructions.insertBefore(instruction, hook);
                ++constructorReturns;
            }
        }
        if (constructorReturns != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer transformer expected exactly one constructor return for size metadata initialization, found "
                    + constructorReturns);
        }

        classNode.methods.add(this.createSizeMetadataEyeHeightFacade(
            playerDescriptor,
            GET_AQUA_EYE_HEIGHT,
            Opcodes.ACC_PROTECTED));
        classNode.methods.add(this.createSizeMetadataEyeHeightFacade(
            playerDescriptor,
            GET_STANDING_EYE_HEIGHT,
            Opcodes.ACC_PUBLIC));
        classNode.methods.add(this.createSizeMetadataFacade(playerDescriptor));
    }

    private MethodNode createSizeMetadataEyeHeightFacade(String playerDescriptor, String name, int access) {
        MethodNode method = new MethodNode(access, name, "(" + POSE_DESCRIPTOR + ENTITY_SIZE_DESCRIPTOR + ")F", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            SIZE_METADATA_LOGIC,
            GET_AQUA_EYE_HEIGHT,
            "(" + playerDescriptor + POSE_DESCRIPTOR + ENTITY_SIZE_DESCRIPTOR + ")F",
            false));
        method.instructions.add(new InsnNode(Opcodes.FRETURN));
        return method;
    }

    private MethodNode createSizeMetadataFacade(String playerDescriptor) {
        MethodNode method = new MethodNode(
            Opcodes.ACC_PUBLIC,
            GET_SIZE,
            "(" + POSE_DESCRIPTOR + ")" + ENTITY_SIZE_DESCRIPTOR,
            null,
            null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            SIZE_METADATA_LOGIC,
            GET_SIZE,
            "(" + playerDescriptor + POSE_DESCRIPTOR + ")" + ENTITY_SIZE_DESCRIPTOR,
            false));
        method.instructions.add(new InsnNode(Opcodes.ARETURN));
        return method;
    }

    /** Phase 2N: replaces the client-only lifecycle override and the one sleep setSize call. */
    private void addLifecycleSleepPlumbing(ClassNode classNode) {
        String playerDescriptor = "L" + classNode.name + ";";
        MethodNode prepare = this.findSingleNamedMethod(
            classNode,
            PREPARE_PLAYER_TO_SPAWN_MCP,
            PREPARE_PLAYER_TO_SPAWN_SRG,
            PREPARE_PLAYER_TO_SPAWN_NOTCH,
            "()V",
            true);
        if (prepare != null) {
            this.rewritePreparePlayerToSpawn(prepare, classNode.superName, playerDescriptor);
        }

        MethodNode sleep = this.findSleepInBedMethod(classNode);
        int replacements = 0;
        for (AbstractInsnNode instruction = sleep.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {

            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (!"(FF)V".equals(invocation.desc)
                || !(SET_SIZE_MCP.equals(invocation.name) || SET_SIZE_SRG.equals(invocation.name)
                    || SET_SIZE_NOTCH.equals(invocation.name))
                || !(classNode.name.equals(invocation.owner) || "net/minecraft/entity/Entity".equals(invocation.owner))) {
                continue;
            }
            sleep.instructions.set(instruction, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                LIFECYCLE_LOGIC,
                "onSleepSetSize",
                "(" + playerDescriptor + "FF)V",
                false));
            ++replacements;
        }
        if (replacements != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer transformer expected exactly one sleepInBedAt setSize call, found " + replacements);
        }
    }

    private void rewritePreparePlayerToSpawn(MethodNode method, String superName, String playerDescriptor) {
        method.instructions.clear();
        method.tryCatchBlocks.clear();
        if (method.localVariables != null) method.localVariables.clear();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            LIFECYCLE_LOGIC,
            "beforePreparePlayerToSpawn",
            "(" + playerDescriptor + ")V",
            false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESPECIAL,
            superName,
            method.name,
            "()V",
            false));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            LIFECYCLE_LOGIC,
            "afterPreparePlayerToSpawn",
            "(" + playerDescriptor + ")V",
            false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
    }

    /** Finds an existing member by its own verified MCP, SRG, or raw name, never by class namespace. */
    private MethodNode findSingleNamedMethod(
        ClassNode classNode,
        String mcpName,
        String srgName,
        String notchName,
        String descriptor,
        boolean optional) {

        MethodNode result = null;
        for (MethodNode method : classNode.methods) {
            if (!descriptor.equals(method.desc)
                || !(mcpName.equals(method.name) || srgName.equals(method.name) || notchName.equals(method.name))) {
                continue;
            }
            if (result != null) {
                throw new IllegalStateException(
                    "Aqua EntityPlayer found multiple lifecycle candidates for " + mcpName + "/" + srgName + "/"
                        + notchName + descriptor);
            }
            result = method;
        }
        if (result == null && !optional) {
            throw new IllegalStateException(
                "Aqua EntityPlayer could not find lifecycle target " + mcpName + "/" + srgName + "/" + notchName
                    + descriptor);
        }
        return result;
    }

    /**
     * The raw return class for EntityPlayer$EnumStatus is standalone {@code za}, not a synthetic
     * {@code yz$EnumStatus}.  Match the actual ClassNode descriptor instead of constructing one.
     */
    private MethodNode findSleepInBedMethod(ClassNode classNode) {
        MethodNode result = null;
        for (MethodNode method : classNode.methods) {
            if (!(SLEEP_IN_BED_MCP.equals(method.name) || SLEEP_IN_BED_SRG.equals(method.name)
                || SLEEP_IN_BED_NOTCH.equals(method.name)) || !this.isSleepInBedDescriptor(method.desc)) {
                continue;
            }
            if (result != null) {
                throw new IllegalStateException(
                    "Aqua EntityPlayer found multiple sleepInBedAt candidates with three int arguments: " + result.name
                        + result.desc + " and " + method.name + method.desc);
            }
            result = method;
        }
        if (result == null) {
            throw new IllegalStateException(
                "Aqua EntityPlayer could not find sleepInBedAt by MCP/SRG/raw name and actual three-int object descriptor");
        }
        return result;
    }

    private boolean isSleepInBedDescriptor(String descriptor) {
        return descriptor.startsWith("(III)L") && descriptor.endsWith(";");
    }

    /**
     * Keep the real legacy EntityPlayer#getEyeHeight() aligned with Aqua's
     * canonical swim/crawl origin. Render-camera interpolation is separate, but
     * vanilla block/entity ray traces still use this virtual method.
     */
    private void addVanillaEyeHeightBridge(ClassNode classNode) {
        MethodNode eyeHeight = this.findSingleNamedMethod(
            classNode,
            VANILLA_EYE_HEIGHT_MCP,
            VANILLA_EYE_HEIGHT_SRG,
            VANILLA_EYE_HEIGHT_NOTCH,
            "()F",
            false);

        LabelNode vanilla = new LabelNode();
        InsnList head = new InsnList();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            LIFECYCLE_LOGIC,
            "hasSwimmingEyeHeight",
            "(L" + classNode.name + ";)Z",
            false));
        head.add(new JumpInsnNode(Opcodes.IFEQ, vanilla));
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            LIFECYCLE_LOGIC,
            "swimmingEyeHeight",
            "(L" + classNode.name + ";)F",
            false));
        head.add(new InsnNode(Opcodes.FRETURN));
        head.add(vanilla);
        eyeHeight.instructions.insert(head);
    }

    private void verifyVanillaEyeHeightBridge(ClassNode classNode) {
        MethodNode eyeHeight = this.findSingleNamedMethod(
            classNode,
            VANILLA_EYE_HEIGHT_MCP,
            VANILLA_EYE_HEIGHT_SRG,
            VANILLA_EYE_HEIGHT_NOTCH,
            "()F",
            false);

        int predicate = 0;
        int value = 0;
        for (AbstractInsnNode instruction = eyeHeight.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {

            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (!LIFECYCLE_LOGIC.equals(invocation.owner)) continue;
            if ("hasSwimmingEyeHeight".equals(invocation.name)
                && ("(L" + classNode.name + ";)Z").equals(invocation.desc)) ++predicate;
            if ("swimmingEyeHeight".equals(invocation.name)
                && ("(L" + classNode.name + ";)F").equals(invocation.desc)) ++value;
        }
        if (predicate != 1 || value != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer legacy eye-height bridge verification failed: predicate=" + predicate
                    + ", value=" + value);
        }
    }

    /** Phase 2O: preserves the former EntityPlayerMixin onLivingUpdate TAIL bob update. */
    private void addLegacyBobBridge(ClassNode classNode) {
        MethodNode livingUpdate = this.findSingleNamedMethod(
            classNode,
            ON_LIVING_UPDATE_MCP,
            ON_LIVING_UPDATE_SRG,
            ON_LIVING_UPDATE_NOTCH,
            "()V",
            false);
        AbstractInsnNode tailReturn = null;
        int returns = 0;
        for (AbstractInsnNode instruction = livingUpdate.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {

            if (instruction.getOpcode() != Opcodes.RETURN) continue;
            tailReturn = instruction;
            ++returns;
        }
        if (returns != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer expected exactly one onLivingUpdate RETURN for Phase 2O TAIL, found " + returns);
        }
        String playerDescriptor = "L" + classNode.name + ";";
        InsnList bridge = new InsnList();
        bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            LEGACY_BOB_LOGIC,
            "update",
            "(" + playerDescriptor + ")V",
            false));
        livingUpdate.instructions.insertBefore(tailReturn, bridge);
    }

    /**
     * Phase 2S: preserves the former HEAD-cancellable EntityPlayerMixin swim
     * replacement while keeping all movement policy in ordinary Java.
     */
    private void addSwimTravelPlumbing(ClassNode classNode) {
        if (classNode.interfaces.contains(JUMPING_ACCESS)) {
            throw new IllegalStateException("Aqua EntityPlayer already implements " + JUMPING_ACCESS);
        }
        this.requireMissingMethod(classNode, JUMPING_ACCESSOR, "()Z");

        MethodNode travel = this.findSingleNamedMethod(
            classNode,
            SWIM_TRAVEL_MCP,
            SWIM_TRAVEL_SRG,
            SWIM_TRAVEL_NOTCH,
            "(FF)V",
            false);
        if (travel.instructions.getFirst() == null) {
            throw new IllegalStateException("Aqua EntityPlayer moveEntityWithHeading has no instructions");
        }

        classNode.interfaces.add(JUMPING_ACCESS);
        classNode.methods.add(this.createJumpingAccessor(classNode.name));

        String playerDescriptor = "L" + classNode.name + ";";
        LabelNode continueVanilla = new LabelNode();
        InsnList bridge = new InsnList();
        bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.add(new TypeInsnNode(Opcodes.CHECKCAST, RESIZEABLE_OWNER));
        bridge.add(new VarInsnNode(Opcodes.FLOAD, 1));
        bridge.add(new VarInsnNode(Opcodes.FLOAD, 2));
        bridge.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            SWIM_TRAVEL_LOGIC,
            "travelIfActive",
            "(" + playerDescriptor + RESIZEABLE_DESCRIPTOR + "FF)Z",
            false));
        bridge.add(new JumpInsnNode(Opcodes.IFEQ, continueVanilla));
        bridge.add(new InsnNode(Opcodes.RETURN));
        bridge.add(continueVanilla);
        travel.instructions.insertBefore(travel.instructions.getFirst(), bridge);
    }

    private MethodNode createJumpingAccessor(String playerOwner) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, JUMPING_ACCESSOR, "()Z", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(
            Opcodes.GETFIELD,
            playerOwner,
            this.getJumpingFieldName(playerOwner),
            "Z"));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    /**
     * This is an observed-class-shape selection, not a superclass-name
     * namespace heuristic: the raw 1.7.10 class is yz with inherited bc,
     * while recompiled development bytecode exposes isJumping.
     */
    private String getJumpingFieldName(String playerOwner) {
        if ("yz".equals(playerOwner)) return JUMPING_FIELD_NOTCH;
        if ("net/minecraft/entity/player/EntityPlayer".equals(playerOwner)) return JUMPING_FIELD_MCP;
        throw new IllegalStateException("Aqua EntityPlayer has unsupported jump-access owner " + playerOwner);
    }

    /** Phase 2K: owns the read-only presentation API while shared Java owns its semantics. */
    private void addPresentationFacades(ClassNode classNode) {
        this.requireMissingMethod(classNode, GET_EYES_IN_WATER_PLAYER, "()Z");
        this.requireMissingMethod(classNode, GET_SWIM_ANIMATION, "(F)F");
        this.requireMissingMethod(classNode, IS_ACTUALLY_SWIMMING, "()Z");
        this.requireMissingMethod(classNode, IS_VISUALLY_SWIMMING, "()Z");

        String playerDescriptor = "L" + classNode.name + ";";
        classNode.methods.add(this.createPresentationBooleanFacade(playerDescriptor, GET_EYES_IN_WATER_PLAYER));
        classNode.methods.add(this.createPresentationFloatFacade(playerDescriptor, GET_SWIM_ANIMATION));
        classNode.methods.add(this.createPresentationBooleanFacade(playerDescriptor, IS_ACTUALLY_SWIMMING));
        classNode.methods.add(this.createPresentationBooleanFacade(playerDescriptor, IS_VISUALLY_SWIMMING));
    }

    private MethodNode createPresentationBooleanFacade(String playerDescriptor, String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, "()Z", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            PRESENTATION_LOGIC,
            name,
            "(" + playerDescriptor + ")Z",
            false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        return method;
    }

    private MethodNode createPresentationFloatFacade(String playerDescriptor, String name) {
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, "(F)F", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.FLOAD, 1));
        method.instructions.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            PRESENTATION_LOGIC,
            name,
            "(" + playerDescriptor + "F)F",
            false));
        method.instructions.add(new InsnNode(Opcodes.FRETURN));
        return method;
    }

    private void verifyStateHolder(ClassNode classNode) {
        int interfaces = 0;
        for (String interfaceName : classNode.interfaces) {
            if (STATE_HOLDER.equals(interfaceName)) ++interfaces;
        }
        int fields = 0;
        for (FieldNode field : classNode.fields) {
            if (STATE_FIELD.equals(field.name) && STATE_DESCRIPTOR.equals(field.desc)) ++fields;
        }
        int methods = 0;
        for (MethodNode method : classNode.methods) {
            if (STATE_GETTER.equals(method.name) && ("()" + STATE_DESCRIPTOR).equals(method.desc)) ++methods;
        }
        if (interfaces != 1 || fields != 1 || methods != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer state holder verification failed: interfaces=" + interfaces + ", fields=" + fields
                    + ", methods=" + methods);
        }
    }

    private void verifyResizeableInterface(ClassNode classNode) {
        int interfaces = 0;
        for (String interfaceName : classNode.interfaces) {
            if (RESIZEABLE_OWNER.equals(interfaceName)) ++interfaces;
        }
        if (interfaces != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer IPlayerResizeable verification failed: interfaces=" + interfaces);
        }

        this.requireExactlyOneMethod(classNode, STATE_GETTER, "()" + STATE_DESCRIPTOR);
        this.requireExactlyOneMethod(classNode, CAN_SWIM, "()Z");
        this.requireExactlyOneMethod(classNode, UPDATE_SWIMMING, "()V");
        this.requireExactlyOneMethod(classNode, GET_EYES_IN_WATER_PLAYER, "()Z");
        this.requireExactlyOneMethod(classNode, GET_WATER_VISION, "()F");
        this.requireExactlyOneMethod(classNode, GET_WIDTH, "()F");
        this.requireExactlyOneMethod(classNode, GET_HEIGHT, "()F");
        this.requireExactlyOneMethod(classNode, GET_SIZE, "(" + POSE_DESCRIPTOR + ")" + ENTITY_SIZE_DESCRIPTOR);
        this.requireExactlyOneMethod(classNode, RECALCULATE_SIZE, "()V");
        this.requireExactlyOneMethod(classNode, IS_RESIZING_ALLOWED, "()Z");
        this.requireExactlyOneMethod(classNode, IS_ACTUALLY_SNEAKING, "()Z");
        this.requireExactlyOneMethod(
            classNode,
            GET_STANDING_EYE_HEIGHT,
            "(" + POSE_DESCRIPTOR + ENTITY_SIZE_DESCRIPTOR + ")F");
        this.requireExactlyOneMethod(classNode, SET_POSE, "(" + POSE_DESCRIPTOR + ")V");
        this.requireExactlyOneMethod(classNode, GET_POSE, "()" + POSE_DESCRIPTOR);
        this.requireExactlyOneMethod(classNode, IS_POSE_CLEAR, "(" + POSE_DESCRIPTOR + ")Z");
        this.requireExactlyOneMethod(classNode, GET_SHOULD_BE_DEAD, "()Z");
        this.requireExactlyOneMethod(classNode, IS_SWIMMING, "()Z");
        this.requireExactlyOneMethod(classNode, IS_ACTUALLY_SWIMMING, "()Z");
        this.requireExactlyOneMethod(classNode, IS_VISUALLY_SWIMMING, "()Z");
        this.requireExactlyOneMethod(classNode, SET_SWIMMING, "(Z)V");
        this.requireExactlyOneMethod(classNode, GET_SWIM_ANIMATION, "(F)F");
        this.requireExactlyOneMethod(classNode, CAN_FORCE_CRAWLING, "()Z");
        this.requireExactlyOneMethod(classNode, IS_FORCING_CRAWLING, "()Z");
        this.requireExactlyOneMethod(classNode, SET_FORCING_CRAWLING, "(Z)V");
    }

    private void verifySizeFacades(ClassNode classNode) {
        this.requireExactlyOneMethod(classNode, GET_WIDTH, "()F");
        this.requireExactlyOneMethod(classNode, GET_HEIGHT, "()F");
    }

    private void verifyResizeFacades(ClassNode classNode) {
        this.requireExactlyOneMethod(classNode, RECALCULATE_SIZE, "()V");
        this.requireExactlyOneMethod(classNode, IS_RESIZING_ALLOWED, "()Z");
        this.verifyResizeFacade(classNode, RECALCULATE_SIZE, "()V");
        this.verifyResizeFacade(classNode, IS_RESIZING_ALLOWED, "()Z");
    }

    private void verifyResizeFacade(ClassNode classNode, String name, String descriptor) {
        int helperCalls = 0;
        for (MethodNode method : classNode.methods) {
            if (!name.equals(method.name) || !descriptor.equals(method.desc)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {

                if (instruction instanceof FieldInsnNode) {
                    throw new IllegalStateException("Aqua EntityPlayer Phase 2Q facade directly accesses a field: " + name);
                }
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (!RESIZE_LOGIC.equals(invocation.owner) || !name.equals(invocation.name)) {
                    throw new IllegalStateException("Aqua EntityPlayer Phase 2Q facade has unexpected call: " + name);
                }
                ++helperCalls;
            }
        }
        if (helperCalls != 1) {
            throw new IllegalStateException("Aqua EntityPlayer Phase 2Q facade verification failed for " + name
                + descriptor + ": helperCalls=" + helperCalls);
        }
    }

    private void verifySizeMetadataPlumbing(ClassNode classNode) {
        this.requireExactlyOneMethod(classNode, GET_AQUA_EYE_HEIGHT, "(" + POSE_DESCRIPTOR + ENTITY_SIZE_DESCRIPTOR + ")F");
        this.requireExactlyOneMethod(
            classNode,
            GET_STANDING_EYE_HEIGHT,
            "(" + POSE_DESCRIPTOR + ENTITY_SIZE_DESCRIPTOR + ")F");
        this.requireExactlyOneMethod(classNode, GET_SIZE, "(" + POSE_DESCRIPTOR + ")" + ENTITY_SIZE_DESCRIPTOR);

        int initializationCalls = 0;
        for (MethodNode method : classNode.methods) {
            if (!"<init>".equals(method.name)) continue;
            AbstractInsnNode previous = null;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                previous = instruction, instruction = instruction.getNext()) {

                if (instruction.getOpcode() != Opcodes.RETURN) continue;
                if (!(previous instanceof MethodInsnNode)) {
                    throw new IllegalStateException("Aqua EntityPlayer constructor RETURN lacks size metadata bridge");
                }
                MethodInsnNode invocation = (MethodInsnNode) previous;
                if (!SIZE_METADATA_LOGIC.equals(invocation.owner) || !INITIALIZE_SIZE_METADATA.equals(invocation.name)) {
                    throw new IllegalStateException("Aqua EntityPlayer constructor size metadata bridge is not immediately before RETURN");
                }
                ++initializationCalls;
            }
        }
        if (initializationCalls != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer size metadata verification failed: initialization calls=" + initializationCalls);
        }
    }

    private void verifyLifecycleSleepPlumbing(ClassNode classNode) {
        MethodNode prepare = this.findSingleNamedMethod(
            classNode,
            PREPARE_PLAYER_TO_SPAWN_MCP,
            PREPARE_PLAYER_TO_SPAWN_SRG,
            PREPARE_PLAYER_TO_SPAWN_NOTCH,
            "()V",
            true);
        if (prepare != null) {
            int beforeCalls = 0;
            int superCalls = 0;
            int afterCalls = 0;
            for (AbstractInsnNode instruction = prepare.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {

                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (!LIFECYCLE_LOGIC.equals(invocation.owner)) {
                    if (classNode.superName.equals(invocation.owner) && prepare.name.equals(invocation.name)
                        && invocation.getOpcode() == Opcodes.INVOKESPECIAL) ++superCalls;
                    continue;
                }
                if ("beforePreparePlayerToSpawn".equals(invocation.name)) ++beforeCalls;
                if ("afterPreparePlayerToSpawn".equals(invocation.name)) ++afterCalls;
            }
            if (beforeCalls != 1 || superCalls != 1 || afterCalls != 1) {
                throw new IllegalStateException(
                    "Aqua EntityPlayer preparePlayerToSpawn verification failed: before=" + beforeCalls + ", super="
                        + superCalls + ", after=" + afterCalls);
            }
        }

        MethodNode sleep = this.findSleepInBedMethod(classNode);
        int replacements = 0;
        for (AbstractInsnNode instruction = sleep.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {

            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (LIFECYCLE_LOGIC.equals(invocation.owner) && "onSleepSetSize".equals(invocation.name)
                && ("(L" + classNode.name + ";FF)V").equals(invocation.desc)) ++replacements;
        }
        if (replacements != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer sleepInBedAt verification failed: replacement calls=" + replacements);
        }
    }

    private void verifyLegacyBobBridge(ClassNode classNode) {
        MethodNode livingUpdate = this.findSingleNamedMethod(
            classNode,
            ON_LIVING_UPDATE_MCP,
            ON_LIVING_UPDATE_SRG,
            ON_LIVING_UPDATE_NOTCH,
            "()V",
            false);
        int bridges = 0;
        AbstractInsnNode previous = null;
        for (AbstractInsnNode instruction = livingUpdate.instructions.getFirst(); instruction != null;
            previous = instruction, instruction = instruction.getNext()) {

            if (instruction.getOpcode() != Opcodes.RETURN) continue;
            if (!(previous instanceof MethodInsnNode)) {
                throw new IllegalStateException("Aqua EntityPlayer onLivingUpdate RETURN lacks Phase 2O bob bridge");
            }
            MethodInsnNode invocation = (MethodInsnNode) previous;
            if (!LEGACY_BOB_LOGIC.equals(invocation.owner) || !"update".equals(invocation.name)) {
                throw new IllegalStateException("Aqua EntityPlayer Phase 2O bob bridge is not immediately before RETURN");
            }
            ++bridges;
        }
        if (bridges != 1) {
            throw new IllegalStateException("Aqua EntityPlayer legacy bob verification failed: bridges=" + bridges);
        }
    }

    private void verifySwimTravelPlumbing(ClassNode classNode) {
        int interfaces = 0;
        for (String interfaceName : classNode.interfaces) {
            if (JUMPING_ACCESS.equals(interfaceName)) ++interfaces;
        }
        this.requireExactlyOneMethod(classNode, JUMPING_ACCESSOR, "()Z");

        int fieldReads = 0;
        int returns = 0;
        for (MethodNode method : classNode.methods) {
            if (!JUMPING_ACCESSOR.equals(method.name) || !"()Z".equals(method.desc)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {

                if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    if (instruction.getOpcode() != Opcodes.GETFIELD || !classNode.name.equals(field.owner)
                        || !this.getJumpingFieldName(classNode.name).equals(field.name) || !"Z".equals(field.desc)) {
                        throw new IllegalStateException("Aqua EntityPlayer jump accessor has unexpected field access");
                    }
                    ++fieldReads;
                }
                if (instruction.getOpcode() == Opcodes.IRETURN) ++returns;
            }
        }
        if (interfaces != 1 || fieldReads != 1 || returns != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer jump accessor verification failed: interfaces=" + interfaces + ", fieldReads="
                    + fieldReads + ", returns=" + returns);
        }

        MethodNode travel = this.findSingleNamedMethod(
            classNode,
            SWIM_TRAVEL_MCP,
            SWIM_TRAVEL_SRG,
            SWIM_TRAVEL_NOTCH,
            "(FF)V",
            false);
        int bridges = 0;
        int helperCalls = 0;
        AbstractInsnNode first = travel.instructions.getFirst();
        for (AbstractInsnNode instruction = travel.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {

            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (!SWIM_TRAVEL_LOGIC.equals(invocation.owner) || !"travelIfActive".equals(invocation.name)) continue;
            ++helperCalls;
            AbstractInsnNode jump = instruction.getNext();
            AbstractInsnNode returnInstruction = jump == null ? null : jump.getNext();
            if (!(jump instanceof JumpInsnNode) || jump.getOpcode() != Opcodes.IFEQ
                || returnInstruction == null || returnInstruction.getOpcode() != Opcodes.RETURN) {
                throw new IllegalStateException("Aqua EntityPlayer swim travel bridge does not preserve HEAD cancellation");
            }
            ++bridges;
        }
        if (first == null || bridges != 1 || helperCalls != 1) {
            throw new IllegalStateException("Aqua EntityPlayer swim travel verification failed: bridges=" + bridges
                + ", helperCalls=" + helperCalls);
        }
    }

    private void verifyPresentationFacades(ClassNode classNode) {
        this.requireExactlyOneMethod(classNode, GET_EYES_IN_WATER_PLAYER, "()Z");
        this.requireExactlyOneMethod(classNode, GET_SWIM_ANIMATION, "(F)F");
        this.requireExactlyOneMethod(classNode, IS_ACTUALLY_SWIMMING, "()Z");
        this.requireExactlyOneMethod(classNode, IS_VISUALLY_SWIMMING, "()Z");
    }

    private void verifyDataWatcherPlumbing(ClassNode classNode) {
        String callbackName = this.getDataWatcherCallbackName(classNode);
        int registrations = 0;
        for (MethodNode method : classNode.methods) {
            if (!"<init>".equals(method.name)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {

                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (DATA_WATCHER_LOGIC.equals(invocation.owner) && "register".equals(invocation.name)) ++registrations;
            }
        }
        this.requireExactlyOneMethod(classNode, GET_POSE, "()" + POSE_DESCRIPTOR);
        this.requireExactlyOneMethod(classNode, SET_POSE, "(" + POSE_DESCRIPTOR + ")V");
        this.requireExactlyOneMethod(classNode, IS_FORCING_CRAWLING, "()Z");
        this.requireExactlyOneMethod(classNode, SET_FORCING_CRAWLING, "(Z)V");
        this.requireExactlyOneMethod(classNode, callbackName, "(I)V");
        if (registrations != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer DataWatcher verification failed: registration calls=" + registrations);
        }
    }

    private void verifyCompatibilityFacades(ClassNode classNode) {
        this.requireExactlyOneMethod(classNode, IS_ACTUALLY_SNEAKING, "()Z");
        this.requireExactlyOneMethod(classNode, GET_SHOULD_BE_DEAD, "()Z");
        this.requireExactlyOneMethod(classNode, CAN_FORCE_CRAWLING, "()Z");
        this.requireExactlyOneMethod(classNode, IS_POSE_CLEAR, "(" + POSE_DESCRIPTOR + ")Z");
    }

    private void verifyWaterStatePlumbing(ClassNode classNode) {
        String onEntityUpdate = this.getOnEntityUpdateName(classNode);
        this.requireExactlyOneMethod(classNode, onEntityUpdate, "()V");
        this.requireExactlyOneMethod(classNode, CAN_SWIM, "()Z");
        this.requireExactlyOneMethod(classNode, UPDATE_SWIMMING, "()V");
        this.requireExactlyOneMethod(classNode, GET_WATER_VISION, "()F");
        this.requireExactlyOneMethod(classNode, IS_SWIMMING, "()Z");
        this.requireExactlyOneMethod(classNode, SET_SWIMMING, "(Z)V");

        int superCalls = 0;
        int waterCalls = 0;
        int flagGetterCalls = 0;
        int flagSetterCalls = 0;
        for (MethodNode method : classNode.methods) {
            if (!onEntityUpdate.equals(method.name) || !"()V".equals(method.desc)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {

                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (classNode.superName.equals(invocation.owner) && onEntityUpdate.equals(invocation.name)
                    && "()V".equals(invocation.desc) && invocation.getOpcode() == Opcodes.INVOKESPECIAL) {
                    ++superCalls;
                }
                if (HOOK_OWNER.equals(invocation.owner) && "onPlayerWaterStateUpdate".equals(invocation.name)) {
                    ++waterCalls;
                }
            }
        }
        for (MethodNode method : classNode.methods) {
            if (!IS_SWIMMING.equals(method.name) && !SET_SWIMMING.equals(method.name)) continue;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {

                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (IS_SWIMMING.equals(method.name) && (com.fuzs.aquaacrobatics.core.AquaAcrobaticsCore.isDevEnv() ? "getFlag" : GET_FLAG_SRG).equals(invocation.name)
                    && "(I)Z".equals(invocation.desc)) ++flagGetterCalls;
                if (SET_SWIMMING.equals(method.name) && (com.fuzs.aquaacrobatics.core.AquaAcrobaticsCore.isDevEnv() ? "setFlag" : SET_FLAG_SRG).equals(invocation.name)
                    && "(IZ)V".equals(invocation.desc)) ++flagSetterCalls;
            }
        }
        if (superCalls != 1 || waterCalls != 1 || flagGetterCalls != 1 || flagSetterCalls != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer water update verification failed: superCalls=" + superCalls + ", waterCalls="
                    + waterCalls + ", flagGetterCalls=" + flagGetterCalls + ", flagSetterCalls=" + flagSetterCalls);
        }
    }

    private void requireExactlyOneMethod(ClassNode classNode, String name, String descriptor) {
        int matches = 0;
        for (MethodNode method : classNode.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) ++matches;
        }
        if (matches != 1) {
            throw new IllegalStateException(
                "Aqua EntityPlayer DataWatcher verification failed for " + name + descriptor + ": matches=" + matches);
        }
    }
}
