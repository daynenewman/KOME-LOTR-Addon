package com.fuzs.aquaacrobatics.core.asm;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;


/** Common targeted ASM bridges for the projectile and underwater grass-like boundaries. */
public final class AquaCommonWorldTransformer implements IClassTransformer {

    private static final String ENTITY_THROWABLE = "net.minecraft.entity.projectile.EntityThrowable";
    private static final String BLOCK_GRASS = "net.minecraft.block.BlockGrass";
    private static final String BLOCK_MYCELIUM = "net.minecraft.block.BlockMycelium";
    private static final String ENTITY_ITEM = "net.minecraft.entity.item.EntityItem";
    private static final String ENTITY_BOAT = "net.minecraft.entity.item.EntityBoat";
    private static final String ENTITY = "net.minecraft.entity.Entity";
    private static final String ENTITY_LIVING_BASE = "net.minecraft.entity.EntityLivingBase";
    private static final String BLOCK_LIQUID = "net.minecraft.block.BlockLiquid";
    private static final String THROWABLE_LOGIC = "com/fuzs/aquaacrobatics/entity/projectile/AquaThrowableLogic";
    private static final String GRASS_LOGIC = "com/fuzs/aquaacrobatics/core/UnderwaterGrassLikeHandler";
    private static final String NEW_PROJECTILE_FIELD = "aqua$isNewProjectile";
    private static final String ITEM_LOGIC = "com/fuzs/aquaacrobatics/entity/item/AquaItemWaterPhysicsLogic";
    private static final String LIQUID_LOGIC = "com/fuzs/aquaacrobatics/block/AquaLiquidLightingLogic";
    private static final String BOAT_LOGIC = "com/fuzs/aquaacrobatics/entity/item/AquaBoatRockingLogic";
    private static final String ROCKABLE_BOAT = "com/fuzs/aquaacrobatics/entity/IRockableBoat";
    private static final String BOAT_VANILLA_ACCESS = "com/fuzs/aquaacrobatics/entity/item/IAquaBoatVanillaAccess";
    private static final String ENTITY_LOGIC = "com/fuzs/aquaacrobatics/entity/AquaEntityPrimitiveLogic";
    private static final String BUBBLE_INTERACTABLE = "com/fuzs/aquaacrobatics/entity/IBubbleColumnInteractable";
    private static final String LIVING_LOGIC = "com/fuzs/aquaacrobatics/entity/AquaLivingEntityLogic";
    private static final String LIVING_JUMPING_ACCESS = "com/fuzs/aquaacrobatics/entity/IAquaLivingJumpingAccess";
    private static final String ON_UPDATE_MCP = "onUpdate";
    private static final String ON_UPDATE_SRG = "func_70071_h_";
    private static final String ON_UPDATE_NOTCH = "h";
    private static final String UPDATE_TICK_MCP = "updateTick";
    private static final String UPDATE_TICK_SRG = "func_149674_a";
    private static final String UPDATE_TICK_NOTCH = "a";
    private static final String RAY_TRACE_MCP = "rayTraceBlocks";
    private static final String RAY_TRACE_SRG = "func_72933_a";
    private static final String RAY_TRACE_NOTCH = "a";
    private static final String SET_BLOCK_MCP = "setBlock";
    private static final String SET_BLOCK_SRG = "func_147465_d";
    private static final String SET_BLOCK_NOTCH = "b";
    private static final String POS_X_MCP = "posX";
    private static final String POS_X_SRG = "field_70165_t";
    private static final String POS_X_NOTCH = "s";
    private static final String WORLD_MCP = "net/minecraft/world/World";
    private static final String WORLD_NOTCH = "ahb";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENTITY.equals(transformedName) && !ENTITY_LIVING_BASE.equals(transformedName) && !ENTITY_THROWABLE.equals(transformedName) && !BLOCK_GRASS.equals(transformedName)
                && !BLOCK_MYCELIUM.equals(transformedName) && !ENTITY_ITEM.equals(transformedName) && !ENTITY_BOAT.equals(transformedName)
                && !BLOCK_LIQUID.equals(transformedName)) return basicClass;
        if (ENTITY_ITEM.equals(transformedName) && this.isItemPhysicPresent()) return basicClass;
        if (basicClass == null) {
            throw new IllegalStateException("Aqua common world transformer received null bytecode for " + transformedName);
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        if (ENTITY.equals(transformedName)) {
            this.transformEntity(classNode);
        } else if (ENTITY_LIVING_BASE.equals(transformedName)) {
            this.transformLivingBase(classNode);
        } else if (ENTITY_THROWABLE.equals(transformedName)) {
            this.transformThrowable(classNode);
        } else if (ENTITY_ITEM.equals(transformedName)) {
            this.transformItem(classNode);
        } else if (BLOCK_LIQUID.equals(transformedName)) {
            this.transformLiquid(classNode);
        } else if (ENTITY_BOAT.equals(transformedName)) {
            this.transformBoat(classNode);
        } else {
            this.transformGrassLike(classNode, BLOCK_GRASS.equals(transformedName));
        }
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }

    private void transformLivingBase(ClassNode classNode) {
        if (classNode.interfaces.contains(LIVING_JUMPING_ACCESS)) {
            throw new IllegalStateException("Duplicate EntityLivingBase jumping interface attachment");
        }
        classNode.interfaces.add(LIVING_JUMPING_ACCESS);
        this.addLivingJumpAccessor(classNode);
        MethodNode entityUpdate = this.findLivingMethod(classNode, "()V", "onEntityUpdate", "func_70030_z", "C");
        this.replaceLivingWaterCheck(classNode, entityUpdate);
        this.replaceLivingAirArgument(classNode, entityUpdate);
        MethodNode movement = this.findLivingMethod(classNode, "(FF)V", "moveEntityWithHeading", "func_70612_e", "e");
        this.replaceSecondHorizontalCollision(classNode, movement);
        this.verifyLivingBase(classNode, entityUpdate, movement);
    }

    private void addLivingJumpAccessor(ClassNode classNode) {
        this.requireAbsent(classNode, "aqua$isJumping", "()Z");
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "aqua$isJumping", "()Z", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, classNode.name, this.livingJumpFieldName(classNode), "Z"));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        classNode.methods.add(method);
    }

    // Verified dev/SRG/raw forms: isJumping / field_70703_bu / sv.bc.
    private String livingJumpFieldName(ClassNode classNode) {
        if (ENTITY_LIVING_BASE.replace('.', '/').equals(classNode.name)) return "isJumping";
        if ("sv".equals(classNode.name)) return "bc";
        return "field_70703_bu";
    }

    private MethodNode findLivingMethod(ClassNode classNode, String descriptor, String... names) {
        MethodNode result = null;
        for (MethodNode method : classNode.methods) {
            if (!descriptor.equals(method.desc)) continue;
            boolean matched = false;
            for (String name : names) if (name.equals(method.name)) matched = true;
            if (!matched) continue;
            if (result != null) throw new IllegalStateException("Aqua EntityLivingBase found multiple target methods for " + descriptor);
            result = method;
        }
        if (result == null) throw new IllegalStateException("Aqua EntityLivingBase could not find target method " + descriptor);
        return result;
    }

    private void replaceLivingWaterCheck(ClassNode classNode, MethodNode entityUpdate) {
        MethodInsnNode target = null;
        for (AbstractInsnNode instruction = entityUpdate.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL || !("(" + this.livingMaterialDescriptor(classNode) + ")Z").equals(call.desc)
                    || !("isInsideOfMaterial".equals(call.name) || "func_70055_a".equals(call.name)
                    || "a".equals(call.name))) continue;
            if (target != null) throw new IllegalStateException("Aqua EntityLivingBase found multiple water-material checks");
            target = call;
        }
        if (target == null) throw new IllegalStateException("Aqua EntityLivingBase could not find water-material check");
        target.setOpcode(Opcodes.INVOKESTATIC);
        target.owner = LIVING_LOGIC;
        target.name = "checkBubbleBreathing";
        target.desc = "(L" + classNode.name + ";" + target.desc.substring(1);
        target.itf = false;
    }

    // Verified complete material descriptors: dev/SRG Material and raw awt.
    private String livingMaterialDescriptor(ClassNode classNode) {
        return "sv".equals(classNode.name) ? "Lawt;" : "Lnet/minecraft/block/material/Material;";
    }

    private void replaceLivingAirArgument(ClassNode classNode, MethodNode entityUpdate) {
        List<MethodInsnNode> targets = new ArrayList<>();
        for (AbstractInsnNode instruction = entityUpdate.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (call.getOpcode() != Opcodes.INVOKEVIRTUAL || !"(I)V".equals(call.desc)
                    || !("setAir".equals(call.name) || "func_70050_g".equals(call.name) || "h".equals(call.name))) continue;
            targets.add(call);
        }
        if (targets.isEmpty()) throw new IllegalStateException("Aqua EntityLivingBase could not find setAir call");
        for (MethodInsnNode target : targets) {
            int local = entityUpdate.maxLocals++;
            InsnList bridge = new InsnList();
            bridge.add(new VarInsnNode(Opcodes.ISTORE, local));
            bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
            bridge.add(new VarInsnNode(Opcodes.ILOAD, local));
            bridge.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LIVING_LOGIC, "getNewAirValue", "(L" + classNode.name + ";I)I", false));
            entityUpdate.instructions.insertBefore(target, bridge);
        }
    }

    private void replaceSecondHorizontalCollision(ClassNode classNode, MethodNode movement) {
        List<FieldInsnNode> fields = new ArrayList<>();
        for (AbstractInsnNode instruction = movement.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (field.getOpcode() == Opcodes.GETFIELD && "Z".equals(field.desc)
                    && ("isCollidedHorizontally".equals(field.name) || "field_70123_F".equals(field.name) || "E".equals(field.name))) fields.add(field);
        }
        if (fields.size() < 2) throw new IllegalStateException("Aqua EntityLivingBase could not find ordinal-1 horizontal collision read");
        FieldInsnNode target = fields.get(1);
        MethodInsnNode bridge = new MethodInsnNode(Opcodes.INVOKESTATIC, LIVING_LOGIC, "isJumpingOnLadder", "(L" + classNode.name + ";)Z", false);
        movement.instructions.set(target, bridge);
    }

    private void verifyLivingBase(ClassNode classNode, MethodNode entityUpdate, MethodNode movement) {
        if (count(classNode.interfaces, LIVING_JUMPING_ACCESS) != 1) throw new IllegalStateException("Aqua EntityLivingBase interface verification failed");
        int accessor = 0, water = 0, air = 0, climbing = 0;
        for (MethodNode method : classNode.methods) {
            if ("aqua$isJumping".equals(method.name) && "()Z".equals(method.desc)) ++accessor;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!LIVING_LOGIC.equals(call.owner)) continue;
                if ("checkBubbleBreathing".equals(call.name)) ++water;
                if ("getNewAirValue".equals(call.name)) ++air;
                if ("isJumpingOnLadder".equals(call.name)) ++climbing;
            }
        }
        if (accessor != 1 || water != 1 || air != 3 || climbing != 1) {
            throw new IllegalStateException("Aqua EntityLivingBase bridge verification failed");
        }
    }

    private void transformEntity(ClassNode classNode) {
        if (classNode.interfaces.contains(BUBBLE_INTERACTABLE)) {
            throw new IllegalStateException("Duplicate IBubbleColumnInteractable attachment");
        }
        classNode.interfaces.add(BUBBLE_INTERACTABLE);
        this.addEntityBubbleBridge(classNode, "onEnterBubbleColumn");
        this.addEntityBubbleBridge(classNode, "onEnterBubbleColumnWithAirAbove");
        MethodNode water = this.findEntityMethod(classNode, "()Z", "handleWaterMovement", "func_70090_H", "N");
        this.replaceWaterMovementConstant(classNode, water);
        MethodNode movement = this.findEntityMethod(classNode, "(DDD)V", "moveEntity", "func_70091_d", "d");
        this.addClimbingBlockBridge(movement);
        this.verifyEntity(classNode, water, movement);
    }

    private void addEntityBubbleBridge(ClassNode classNode, String name) {
        this.requireAbsent(classNode, name, "(Z)V");
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, "(Z)V", null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                ENTITY_LOGIC,
                name,
                "(L" + classNode.name + ";Z)V",
                false));
        method.instructions.add(new InsnNode(Opcodes.RETURN));
        classNode.methods.add(method);
    }

    private MethodNode findEntityMethod(ClassNode classNode, String descriptor, String... names) {
        MethodNode result = null;
        for (MethodNode method : classNode.methods) {
            if (!descriptor.equals(method.desc)) continue;
            boolean matched = false;
            for (String name : names) if (name.equals(method.name)) matched = true;
            if (!matched) continue;
            if (result != null) throw new IllegalStateException("Aqua Entity found multiple target methods for " + descriptor);
            result = method;
        }
        if (result == null) throw new IllegalStateException("Aqua Entity could not find target method " + descriptor);
        return result;
    }

    private void replaceWaterMovementConstant(ClassNode classNode, MethodNode water) {
        LdcInsnNode target = null;
        for (AbstractInsnNode instruction = water.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof LdcInsnNode) || !( ((LdcInsnNode) instruction).cst instanceof Double)
                    || ((Double) ((LdcInsnNode) instruction).cst).doubleValue() != -0.4000000059604645D) continue;
            if (target != null) throw new IllegalStateException("Aqua Entity found multiple water Y constants");
            target = (LdcInsnNode) instruction;
        }
        if (target == null) throw new IllegalStateException("Aqua Entity could not find water Y constant");
        InsnList bridge = new InsnList();
        bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.add(new LdcInsnNode(-0.4000000059604645D));
        bridge.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                ENTITY_LOGIC,
                "adjustWaterMovementY",
                "(L" + classNode.name + ";D)D",
                false));
        water.instructions.insertBefore(target, bridge);
        water.instructions.remove(target);
    }

    private void addClimbingBlockBridge(MethodNode movement) {
        VarInsnNode load = null;
        String blockDescriptor = null;
        for (AbstractInsnNode instruction = movement.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            Type[] arguments = Type.getArgumentTypes(invocation.desc);
            if (arguments.length != 3 || arguments[0].getSort() != Type.INT || arguments[1].getSort() != Type.INT
                    || arguments[2].getSort() != Type.INT || Type.getReturnType(invocation.desc).getSort() != Type.OBJECT
                    || !("getBlock".equals(invocation.name) || "func_147439_a".equals(invocation.name) || "a".equals(invocation.name))) continue;
            AbstractInsnNode next = invocation.getNext();
            while (next != null && next.getOpcode() < 0) next = next.getNext();
            if (!(next instanceof VarInsnNode) || next.getOpcode() != Opcodes.ASTORE) continue;
            int local = ((VarInsnNode) next).var;
            for (AbstractInsnNode candidate = next.getNext(); candidate != null; candidate = candidate.getNext()) {
                if (candidate instanceof VarInsnNode && candidate.getOpcode() == Opcodes.ALOAD
                        && ((VarInsnNode) candidate).var == local) {
                    if (load != null && load != candidate) {
                        throw new IllegalStateException("Aqua Entity found multiple climbing block loads");
                    }
                    load = (VarInsnNode) candidate;
                    blockDescriptor = Type.getReturnType(invocation.desc).getDescriptor();
                    break;
                }
            }
        }
        if (load == null) throw new IllegalStateException("Aqua Entity could not find walking block LOAD");
        movement.instructions.insert(load, new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                ENTITY_LOGIC,
                "getFakeClimbingBlock",
                "(" + blockDescriptor + ")" + blockDescriptor,
                false));
    }

    private void verifyEntity(ClassNode classNode, MethodNode water, MethodNode movement) {
        if (count(classNode.interfaces, BUBBLE_INTERACTABLE) != 1) {
            throw new IllegalStateException("Aqua Entity bubble interface attachment verification failed");
        }
        int bubbleBridges = 0, waterBridges = 0, climbingBridges = 0;
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (!ENTITY_LOGIC.equals(invocation.owner)) continue;
                if ("onEnterBubbleColumn".equals(invocation.name) || "onEnterBubbleColumnWithAirAbove".equals(invocation.name)) ++bubbleBridges;
                if ("adjustWaterMovementY".equals(invocation.name)) ++waterBridges;
                if ("getFakeClimbingBlock".equals(invocation.name)) ++climbingBridges;
            }
        }
        if (bubbleBridges != 2 || waterBridges != 1 || climbingBridges != 1) {
            throw new IllegalStateException("Aqua Entity bridge verification failed");
        }
    }

    private void transformBoat(ClassNode classNode) {
        if (classNode.interfaces.contains(ROCKABLE_BOAT) || classNode.interfaces.contains(BOAT_VANILLA_ACCESS)) {
            throw new IllegalStateException("Duplicate EntityBoat Aqua interface attachment");
        }
        classNode.interfaces.add(ROCKABLE_BOAT);
        classNode.interfaces.add(BOAT_VANILLA_ACCESS);
        this.addBoatBridge(classNode, "onEnterBubbleColumnWithAirAbove", "(Z)V", "onEnterBubbleColumnWithAirAbove");
        this.addBoatBridge(classNode, "aqua$doRegisterData", "()V", "registerData");
        this.addBoatBridge(classNode, "getRockingAngle", "(F)F", "getRockingAngle");
        this.addBoatProtectedAccessors(classNode);
        this.addBoatUpdateBridge(classNode);
        this.verifyBoat(classNode);
    }

    private void addBoatProtectedAccessors(ClassNode classNode) {
        this.requireAbsent(classNode, "aqua$getRandom", "()Ljava/util/Random;");
        MethodNode random = new MethodNode(Opcodes.ACC_PUBLIC, "aqua$getRandom", "()Ljava/util/Random;", null, null);
        random.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        random.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, classNode.name, this.boatRandName(classNode), "Ljava/util/Random;"));
        random.instructions.add(new InsnNode(Opcodes.ARETURN));
        classNode.methods.add(random);

        this.requireAbsent(classNode, "aqua$getSplashSound", "()Ljava/lang/String;");
        MethodNode splash = new MethodNode(Opcodes.ACC_PUBLIC, "aqua$getSplashSound", "()Ljava/lang/String;", null, null);
        splash.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        splash.instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                classNode.name,
                this.boatSplashSoundName(classNode),
                "()Ljava/lang/String;",
                false));
        splash.instructions.add(new InsnNode(Opcodes.ARETURN));
        classNode.methods.add(splash);
    }

    // Forge dev sees MCP; production sees raw. The SRG alternatives document the complete mapping.
    private String boatRandName(ClassNode classNode) {
        if ("net/minecraft/entity/item/EntityBoat".equals(classNode.name)) return "rand"; // field_70146_Z / raw Z
        if ("xi".equals(classNode.name)) return "Z"; // MCP rand / SRG field_70146_Z
        return "field_70146_Z";
    }

    private String boatSplashSoundName(ClassNode classNode) {
        if ("net/minecraft/entity/item/EntityBoat".equals(classNode.name)) return "getSplashSound"; // func_145777_O / raw O
        if ("xi".equals(classNode.name)) return "O"; // MCP getSplashSound / SRG func_145777_O
        return "func_145777_O";
    }

    private void addBoatBridge(ClassNode classNode, String name, String descriptor, String helperName) {
        this.requireAbsent(classNode, name, descriptor);
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, name, descriptor, null, null);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        if ("(Z)V".equals(descriptor)) method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
        if ("(F)F".equals(descriptor)) method.instructions.add(new VarInsnNode(Opcodes.FLOAD, 1));
        method.instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                BOAT_LOGIC,
                helperName,
                "(L" + classNode.name + ";" + descriptor.substring(1),
                false));
        method.instructions.add(new InsnNode("(F)F".equals(descriptor) ? Opcodes.FRETURN : Opcodes.RETURN));
        classNode.methods.add(method);
    }

    private void addBoatUpdateBridge(ClassNode classNode) {
        MethodNode onUpdate = this.findSingleOnUpdate(classNode);
        List<MethodInsnNode> rotations = new ArrayList<>();
        for (AbstractInsnNode instruction = onUpdate.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (invocation.getOpcode() == Opcodes.INVOKEVIRTUAL && "(FF)V".equals(invocation.desc)
                    && ("setRotation".equals(invocation.name) || "func_70101_b".equals(invocation.name)
                    || "b".equals(invocation.name))) rotations.add(invocation);
        }
        if (rotations.size() != 2) {
            throw new IllegalStateException("Aqua EntityBoat expected exactly two setRotation(FF) calls, found " + rotations.size());
        }
        InsnList bridge = new InsnList();
        bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bridge.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BOAT_LOGIC, "updateRocking", "(L" + classNode.name + ";)V", false));
        onUpdate.instructions.insert(rotations.get(1), bridge);
    }

    private void verifyBoat(ClassNode classNode) {
        if (count(classNode.interfaces, ROCKABLE_BOAT) != 1 || count(classNode.interfaces, BOAT_VANILLA_ACCESS) != 1) {
            throw new IllegalStateException("Aqua EntityBoat interface attachment verification failed");
        }
        String[] names = { "onEnterBubbleColumnWithAirAbove", "aqua$doRegisterData", "getRockingAngle", "aqua$getRandom", "aqua$getSplashSound" };
        String[] descriptors = { "(Z)V", "()V", "(F)F", "()Ljava/util/Random;", "()Ljava/lang/String;" };
        for (int i = 0; i < names.length; ++i) {
            int methods = 0;
            for (MethodNode method : classNode.methods) if (names[i].equals(method.name) && descriptors[i].equals(method.desc)) ++methods;
            if (methods != 1) throw new IllegalStateException("Aqua EntityBoat bridge verification failed for " + names[i]);
        }
        MethodNode onUpdate = this.findSingleOnUpdate(classNode);
        int updateHelpers = 0;
        for (AbstractInsnNode instruction = onUpdate.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode && BOAT_LOGIC.equals(((MethodInsnNode) instruction).owner)
                    && "updateRocking".equals(((MethodInsnNode) instruction).name)
                    && ("(L" + classNode.name + ";)V").equals(((MethodInsnNode) instruction).desc)) ++updateHelpers;
        }
        if (updateHelpers != 1) throw new IllegalStateException("Aqua EntityBoat expected one rocking update bridge");
    }

    private int count(List<String> values, String value) {
        int count = 0;
        for (String candidate : values) if (value.equals(candidate)) ++count;
        return count;
    }

    private boolean isItemPhysicPresent() {
        return AquaCommonWorldTransformer.class.getClassLoader()
                .getResource("com/creativemd/itemphysic/asm/ItemPhysicEarlyMixins.class") != null;
    }

    private void transformThrowable(ClassNode classNode) {
        this.addNewProjectileField(classNode);
        this.addNewProjectileConstructorInitialization(classNode);
        MethodNode onUpdate = this.findSingleOnUpdate(classNode);
        this.replaceRayTrace(classNode, onUpdate);
        this.addBlockCollisionCheck(classNode, onUpdate);
        this.verifyThrowable(classNode);
    }

    private void transformItem(ClassNode classNode) {
        MethodNode onUpdate = this.findSingleOnUpdate(classNode);
        FieldInsnNode target = null;
        for (AbstractInsnNode instruction = onUpdate.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (field.getOpcode() == Opcodes.GETFIELD && "D".equals(field.desc)
                    && ("motionY".equals(field.name) || "field_70181_x".equals(field.name) || "w".equals(field.name))) {
                target = field;
                break;
            }
        }
        if (target == null) throw new IllegalStateException("Aqua EntityItem could not find ordinal-0 motionY GETFIELD");
        onUpdate.instructions.set(target, new MethodInsnNode(Opcodes.INVOKESTATIC, ITEM_LOGIC, "getMotionYForUpdate",
                "(L" + classNode.name + ";)D", false));
        this.verifyItem(classNode, onUpdate);
    }

    private void verifyItem(ClassNode classNode, MethodNode onUpdate) {
        int bridges = 0;
        for (AbstractInsnNode instruction = onUpdate.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (instruction instanceof MethodInsnNode && ITEM_LOGIC.equals(((MethodInsnNode) instruction).owner)
                    && "getMotionYForUpdate".equals(((MethodInsnNode) instruction).name)
                    && ("(L" + classNode.name + ";)D").equals(((MethodInsnNode) instruction).desc)) ++bridges;
        }
        if (bridges != 1) throw new IllegalStateException("Aqua EntityItem expected one motionY bridge, found " + bridges);
    }

    private void transformLiquid(ClassNode classNode) {
        String access = this.findBlockAccessDescriptor(classNode);
        String desc = "(" + access + "III)I";
        this.requireAbsent(classNode, "getLightOpacity", desc);
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "getLightOpacity", desc, null, null);
        LabelNode fallback = new LabelNode();
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, LIQUID_LOGIC, "hasBrighterWaterOpacity",
                "(L" + classNode.name + ";)Z", false));
        method.instructions.add(new JumpInsnNode(Opcodes.IFEQ, fallback));
        method.instructions.add(new InsnNode(Opcodes.ICONST_1));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        method.instructions.add(fallback);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 3));
        method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 4));
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, classNode.superName, "getLightOpacity", desc, false));
        method.instructions.add(new InsnNode(Opcodes.IRETURN));
        classNode.methods.add(method);
        this.verifyLiquid(classNode, method, desc);
    }

    private String findBlockAccessDescriptor(ClassNode classNode) {
        String found = null;
        for (MethodNode method : classNode.methods) {
            Type[] args = Type.getArgumentTypes(method.desc);

            // Do not derive IBlockAccess from getMixedBrightnessForBlock: that method is
            // client-only in 1.7.10 and is stripped from dedicated-server BlockLiquid.
            // Instead identify the common-side (IBlockAccess, int, int, int) -> boolean
            // signature used by BlockLiquid#getBlocksMovement. Descriptor-shape matching
            // keeps this valid across MCP, SRG, and raw obfuscated runtime bytecode.
            if (args.length == 4 && args[0].getSort() == Type.OBJECT && args[1].getSort() == Type.INT
                    && args[2].getSort() == Type.INT && args[3].getSort() == Type.INT
                    && Type.getReturnType(method.desc).getSort() == Type.BOOLEAN) {
                String descriptor = args[0].getDescriptor();
                if (found != null && !found.equals(descriptor)) {
                    throw new IllegalStateException("Aqua BlockLiquid found conflicting IBlockAccess descriptors");
                }
                found = descriptor;
            }
        }
        if (found != null) return found;
        throw new IllegalStateException("Aqua BlockLiquid could not derive IBlockAccess descriptor");
    }

    private void requireAbsent(ClassNode classNode, String name, String desc) {
        for (MethodNode method : classNode.methods) if (name.equals(method.name) && desc.equals(method.desc)) throw new IllegalStateException("Aqua BlockLiquid method collision: " + name + desc);
    }

    private void verifyLiquid(ClassNode classNode, MethodNode method, String desc) {
        int helpers = 0, supers = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (LIQUID_LOGIC.equals(call.owner) && "hasBrighterWaterOpacity".equals(call.name) && ("(L" + classNode.name + ";)Z").equals(call.desc)) ++helpers;
            if (call.getOpcode() == Opcodes.INVOKESPECIAL && classNode.superName.equals(call.owner) && "getLightOpacity".equals(call.name) && desc.equals(call.desc)) ++supers;
        }
        if (helpers != 1 || supers != 1) throw new IllegalStateException("Aqua BlockLiquid bridge verification failed");
    }

    private void addNewProjectileField(ClassNode classNode) {
        for (FieldNode field : classNode.fields) {
            if (NEW_PROJECTILE_FIELD.equals(field.name) && "Z".equals(field.desc)) {
                throw new IllegalStateException("Aqua EntityThrowable field collision: " + NEW_PROJECTILE_FIELD);
            }
        }
        classNode.fields.add(new FieldNode(Opcodes.ACC_PRIVATE, NEW_PROJECTILE_FIELD, "Z", null, null));
    }

    private void addNewProjectileConstructorInitialization(ClassNode classNode) {
        int constructors = 0;
        String descriptor = "(L" + classNode.name + ";)Z";
        for (MethodNode method : classNode.methods) {
            if (!"<init>".equals(method.name)) continue;
            int returns = 0;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                 instruction = instruction.getNext()) {
                if (instruction.getOpcode() != Opcodes.RETURN) continue;
                InsnList initialization = new InsnList();
                initialization.add(new VarInsnNode(Opcodes.ALOAD, 0));
                initialization.add(new VarInsnNode(Opcodes.ALOAD, 0));
                initialization.add(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        THROWABLE_LOGIC,
                        "isNewProjectile",
                        descriptor,
                        false));
                initialization.add(new FieldInsnNode(Opcodes.PUTFIELD, classNode.name, NEW_PROJECTILE_FIELD, "Z"));
                method.instructions.insertBefore(instruction, initialization);
                ++returns;
            }
            if (returns != 1) {
                throw new IllegalStateException(
                        "Aqua EntityThrowable constructor " + method.desc + " expected one RETURN, found " + returns);
            }
            ++constructors;
        }
        if (constructors == 0) throw new IllegalStateException("Aqua EntityThrowable has no constructors");
    }

    private MethodNode findSingleOnUpdate(ClassNode classNode) {
        MethodNode result = null;
        for (MethodNode method : classNode.methods) {
            if (!"()V".equals(method.desc) || !(ON_UPDATE_MCP.equals(method.name) || ON_UPDATE_SRG.equals(method.name)
                    || ON_UPDATE_NOTCH.equals(method.name))) continue;
            if (result != null) {
                throw new IllegalStateException("Aqua EntityThrowable found multiple onUpdate candidates");
            }
            result = method;
        }
        if (result == null) throw new IllegalStateException("Aqua EntityThrowable could not find onUpdate()V");
        return result;
    }

    private void replaceRayTrace(ClassNode classNode, MethodNode onUpdate) {
        MethodInsnNode rayTrace = this.findSingleRayTrace(onUpdate);
        int worldLocal = onUpdate.maxLocals;
        int startLocal = worldLocal + 1;
        int endLocal = startLocal + 1;
        onUpdate.maxLocals = endLocal + 1;

        InsnList replacement = new InsnList();
        replacement.add(new VarInsnNode(Opcodes.ASTORE, endLocal));
        replacement.add(new VarInsnNode(Opcodes.ASTORE, startLocal));
        replacement.add(new VarInsnNode(Opcodes.ASTORE, worldLocal));
        replacement.add(new VarInsnNode(Opcodes.ALOAD, 0));
        replacement.add(new FieldInsnNode(Opcodes.GETFIELD, classNode.name, NEW_PROJECTILE_FIELD, "Z"));
        replacement.add(new VarInsnNode(Opcodes.ALOAD, worldLocal));
        replacement.add(new VarInsnNode(Opcodes.ALOAD, startLocal));
        replacement.add(new VarInsnNode(Opcodes.ALOAD, endLocal));
        replacement.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                THROWABLE_LOGIC,
                "rayTraceThroughLiquid",
                "(ZL" + rayTrace.owner + ";" + rayTrace.desc.substring(1),
                false));
        onUpdate.instructions.insertBefore(rayTrace, replacement);
        onUpdate.instructions.remove(rayTrace);
    }

    private MethodInsnNode findSingleRayTrace(MethodNode method) {
        MethodInsnNode result = null;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            Type[] arguments = Type.getArgumentTypes(invocation.desc);
            if (invocation.getOpcode() != Opcodes.INVOKEVIRTUAL || !this.isWorldOwner(invocation.owner) || !(RAY_TRACE_MCP.equals(invocation.name)
                    || RAY_TRACE_SRG.equals(invocation.name) || RAY_TRACE_NOTCH.equals(invocation.name))
                    || arguments.length != 2 || arguments[0].getSort() != Type.OBJECT || arguments[1].getSort() != Type.OBJECT
                    || Type.getReturnType(invocation.desc).getSort() != Type.OBJECT) continue;
            if (result != null) throw new IllegalStateException("Aqua EntityThrowable found multiple rayTraceBlocks targets");
            result = invocation;
        }
        if (result == null) throw new IllegalStateException("Aqua EntityThrowable could not find World.rayTraceBlocks(Vec3,Vec3)");
        return result;
    }

    private void addBlockCollisionCheck(ClassNode classNode, MethodNode onUpdate) {
        FieldInsnNode posXWrite = this.findSinglePosXWrite(classNode, onUpdate);
        LabelNode skip = new LabelNode();
        InsnList hook = new InsnList();
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        hook.add(new FieldInsnNode(Opcodes.GETFIELD, classNode.name, NEW_PROJECTILE_FIELD, "Z"));
        hook.add(new JumpInsnNode(Opcodes.IFEQ, skip));
        hook.add(new VarInsnNode(Opcodes.ALOAD, 0));
        // Stable Forge SRG is required for this inherited protected Entity method; raw is sa.I()V.
        hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, classNode.name, "func_145775_I", "()V", false));
        hook.add(skip);
        onUpdate.instructions.insert(posXWrite, hook);
    }

    private FieldInsnNode findSinglePosXWrite(ClassNode classNode, MethodNode method) {
        FieldInsnNode result = null;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode)) continue;
            FieldInsnNode field = (FieldInsnNode) instruction;
            if (field.getOpcode() != Opcodes.PUTFIELD || !"D".equals(field.desc) || !(POS_X_MCP.equals(field.name)
                    || POS_X_SRG.equals(field.name) || POS_X_NOTCH.equals(field.name))) continue;
            if (!classNode.name.equals(field.owner)) continue;
            if (result != null) throw new IllegalStateException("Aqua EntityThrowable found multiple posX PUTFIELD targets");
            result = field;
        }
        if (result == null) throw new IllegalStateException("Aqua EntityThrowable could not find first posX PUTFIELD");
        return result;
    }

    private void verifyThrowable(ClassNode classNode) {
        MethodNode onUpdate = this.findSingleOnUpdate(classNode);
        int rayHelpers = 0;
        int collisionCalls = 0;
        for (AbstractInsnNode instruction = onUpdate.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (THROWABLE_LOGIC.equals(invocation.owner) && "rayTraceThroughLiquid".equals(invocation.name)) ++rayHelpers;
            if (classNode.name.equals(invocation.owner) && "func_145775_I".equals(invocation.name)
                    && "()V".equals(invocation.desc)) ++collisionCalls;
        }
        if (rayHelpers != 1 || collisionCalls != 1) {
            throw new IllegalStateException(
                    "Aqua EntityThrowable verification failed: rayHelpers=" + rayHelpers + ", collisionCalls=" + collisionCalls);
        }
    }

    private void transformGrassLike(ClassNode classNode, boolean grass) {
        MethodNode updateTick = this.findSingleUpdateTick(classNode);
        this.addGrassHeadBridge(updateTick, grass ? "handleUnderwaterGrassBlock" : "handleUnderwaterGrassLikeBlock");
        this.replaceOptionalSecondSetBlock(updateTick);
        this.verifyGrassLike(classNode, grass);
    }

    private MethodNode findSingleUpdateTick(ClassNode classNode) {
        MethodNode result = null;
        for (MethodNode method : classNode.methods) {
            Type[] arguments = Type.getArgumentTypes(method.desc);
            if (!(UPDATE_TICK_MCP.equals(method.name) || UPDATE_TICK_SRG.equals(method.name)
                    || UPDATE_TICK_NOTCH.equals(method.name)) || Type.getReturnType(method.desc).getSort() != Type.VOID
                    || arguments.length != 5 || arguments[0].getSort() != Type.OBJECT || arguments[1].getSort() != Type.INT
                    || arguments[2].getSort() != Type.INT || arguments[3].getSort() != Type.INT
                    || arguments[4].getSort() != Type.OBJECT) continue;
            if (result != null) throw new IllegalStateException("Aqua grass-like block found multiple updateTick candidates");
            result = method;
        }
        if (result == null) throw new IllegalStateException("Aqua grass-like block could not find updateTick(World,III,Random)V");
        return result;
    }

    private void addGrassHeadBridge(MethodNode updateTick, String helper) {
        LabelNode continueVanilla = new LabelNode();
        InsnList bridge = new InsnList();
        bridge.add(new VarInsnNode(Opcodes.ALOAD, 1));
        bridge.add(new VarInsnNode(Opcodes.ILOAD, 2));
        bridge.add(new VarInsnNode(Opcodes.ILOAD, 3));
        bridge.add(new VarInsnNode(Opcodes.ILOAD, 4));
        bridge.add(new VarInsnNode(Opcodes.ALOAD, 5));
        bridge.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                GRASS_LOGIC,
                helper,
                updateTick.desc.substring(0, updateTick.desc.length() - 1) + "Z",
                false));
        bridge.add(new JumpInsnNode(Opcodes.IFEQ, continueVanilla));
        bridge.add(new InsnNode(Opcodes.RETURN));
        bridge.add(continueVanilla);
        updateTick.instructions.insert(bridge);
    }

    private void replaceOptionalSecondSetBlock(MethodNode updateTick) {
        List<MethodInsnNode> calls = new ArrayList<>();
        for (AbstractInsnNode instruction = updateTick.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            Type[] arguments = Type.getArgumentTypes(invocation.desc);
            if (invocation.getOpcode() != Opcodes.INVOKEVIRTUAL || !this.isWorldOwner(invocation.owner) || !(SET_BLOCK_MCP.equals(invocation.name)
                    || SET_BLOCK_SRG.equals(invocation.name) || SET_BLOCK_NOTCH.equals(invocation.name))
                    || arguments.length != 4 || arguments[0].getSort() != Type.INT || arguments[1].getSort() != Type.INT
                    || arguments[2].getSort() != Type.INT || arguments[3].getSort() != Type.OBJECT
                    || Type.getReturnType(invocation.desc).getSort() != Type.BOOLEAN) continue;
            calls.add(invocation);
        }
        // Mixin require=0 made ordinal 1 optional. Preserve that behavior while never selecting ordinal 0.
        if (calls.size() < 2) return;
        MethodInsnNode target = calls.get(1);
        String worldOwner = target.owner;
        target.setOpcode(Opcodes.INVOKESTATIC);
        target.owner = GRASS_LOGIC;
        target.name = "setBlockUnlessCoveredByLiquid";
        target.desc = "(L" + worldOwner + ";" + target.desc.substring(1);
        target.itf = false;
    }

    private boolean isWorldOwner(String owner) {
        return WORLD_MCP.equals(owner) || WORLD_NOTCH.equals(owner);
    }

    private void verifyGrassLike(ClassNode classNode, boolean grass) {
        MethodNode updateTick = this.findSingleUpdateTick(classNode);
        String headHelper = grass ? "handleUnderwaterGrassBlock" : "handleUnderwaterGrassLikeBlock";
        int headHelpers = 0;
        int setBlockHelpers = 0;
        for (AbstractInsnNode instruction = updateTick.instructions.getFirst(); instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (GRASS_LOGIC.equals(invocation.owner) && headHelper.equals(invocation.name)) ++headHelpers;
            if (GRASS_LOGIC.equals(invocation.owner) && "setBlockUnlessCoveredByLiquid".equals(invocation.name)) ++setBlockHelpers;
        }
        if (headHelpers != 1 || setBlockHelpers > 1) {
            throw new IllegalStateException(
                    "Aqua grass-like verification failed: headHelpers=" + headHelpers + ", setBlockHelpers="
                            + setBlockHelpers);
        }
    }
}
