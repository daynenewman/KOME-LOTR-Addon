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
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Client-target router intentionally free of client Minecraft class references.
 * It is safe to instantiate on dedicated servers because it transforms only the
 * client-only EntityOtherPlayerMP transformed name.
 */
public final class AquaClientEntityTransformer implements IClassTransformer {

    private static final String ENTITY_OTHER_PLAYER = "net.minecraft.client.entity.EntityOtherPlayerMP";
    private static final String ITEM_RENDERER = "net.minecraft.client.renderer.ItemRenderer";
    private static final String CLIENT_PLAYER = "net.minecraft.client.entity.EntityClientPlayerMP";
    private static final String ENTITY_PLAYER_SP = "net.minecraft.client.entity.EntityPlayerSP";
    private static final String PLAYER_MOVEMENT_POLICY =
        "com/fuzs/aquaacrobatics/client/entity/AquaClientPlayerMovementPolicy";
    private static final String MOVEMENT_STORAGE_ACCESS =
        "com/fuzs/aquaacrobatics/client/entity/IAquaClientPlayerMovementStorageAccess";
    private static final String MOVEMENT_STORAGE = "com/fuzs/aquaacrobatics/util/MovementInputStorage";
    private static final String CLIENT_PLAYER_LOGIC = "com/fuzs/aquaacrobatics/client/entity/AquaClientPlayerSwimmingLogic";
    private static final String CLIENT_PLAYER_INTERFACE = "com/fuzs/aquaacrobatics/client/entity/IPlayerSPSwimming";
    private static final String RENDER_BOAT = "net.minecraft.client.renderer.entity.RenderBoat";
    private static final String MODEL_BIPED = "net.minecraft.client.model.ModelBiped";
    private static final String RENDER_PLAYER = "net.minecraft.client.renderer.entity.RenderPlayer";
    private static final String ENTITY_RENDERER = "net.minecraft.client.renderer.EntityRenderer";
    private static final String CHARACTER_CREATION_RENDERER =
        "com.lotrcharactercreation.client.render.RacePlayerRenderer";
    private static final String CHARACTER_CREATION_MODEL_PREFIX =
        "com.lotrcharactercreation.client.model.Player";
    private static final String BOAT_LOGIC = "com/fuzs/aquaacrobatics/client/render/AquaBoatRenderLogic";
    private static final String REMOTE_PRESENTATION_LOGIC =
        "com/fuzs/aquaacrobatics/client/entity/AquaRemotePlayerPresentationLogic";
    private static final String WATER_OVERLAY_RENDER_LOGIC =
        "com/fuzs/aquaacrobatics/client/render/AquaWaterOverlayRenderLogic";
    private static final String MODEL_LOGIC = "com/fuzs/aquaacrobatics/client/model/AquaModelBipedLogic";
    private static final String MODEL_INTERFACE = "com/fuzs/aquaacrobatics/client/model/IModelBipedSwimming";
    private static final String RENDER_PLAYER_LOGIC = "com/fuzs/aquaacrobatics/client/render/AquaRenderPlayerLogic";
    private static final String PLAYER_LIGHTING_LOGIC = "com/fuzs/aquaacrobatics/client/render/AquaPlayerLightingLogic";
    private static final String CAMERA_RENDER_LOGIC = "com/fuzs/aquaacrobatics/client/AquaCameraRenderLogic";
    private static final String CAMERA_COLLISION_LOGIC = "com/fuzs/aquaacrobatics/client/AquaCameraCollisionLogic";
    private static final String ON_UPDATE_MCP = "onUpdate";
    private static final String ON_UPDATE_SRG = "func_70071_h_";
    private static final String ON_UPDATE_NOTCH = "h";
    private static final String WARPED_OVERLAY_MCP = "renderWarpedTextureOverlay";
    private static final String WARPED_OVERLAY_SRG = "func_78448_c";
    private static final String WARPED_OVERLAY_NOTCH = "c";
    private static final String GL11 = "org/lwjgl/opengl/GL11";
    private static final String GL_COLOR_4F = "glColor4f";
    private static final String GL_COLOR_4F_DESCRIPTOR = "(FFFF)V";

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (!ENTITY_OTHER_PLAYER.equals(transformedName) && !ITEM_RENDERER.equals(transformedName)
            && !CLIENT_PLAYER.equals(transformedName) && !ENTITY_PLAYER_SP.equals(transformedName)
            && !RENDER_BOAT.equals(transformedName) && !MODEL_BIPED.equals(transformedName)
            && !RENDER_PLAYER.equals(transformedName) && !ENTITY_RENDERER.equals(transformedName)
            && !CHARACTER_CREATION_RENDERER.equals(transformedName)
            && !this.isCharacterCreationModelAdapter(transformedName)) return basicClass;
        if (basicClass == null) {
            throw new IllegalStateException("Aqua client transformer received null bytecode for " + transformedName);
        }

        ClassNode classNode = new ClassNode();
        new ClassReader(basicClass).accept(classNode, 0);
        if (ENTITY_OTHER_PLAYER.equals(transformedName)) {
            this.addRemotePlayerPresentationBridge(classNode);
            this.verifyRemotePlayerPresentationBridge(classNode);
        } else if (ITEM_RENDERER.equals(transformedName)) {
            this.addWarpedWaterOverlayAlphaBridge(classNode);
            this.verifyWarpedWaterOverlayAlphaBridge(classNode);
        } else if(ENTITY_PLAYER_SP.equals(transformedName)) {
            this.addMovementStorageAccess(classNode);
            this.addClientPlayerPushOutHooks(classNode);
        } else if(CLIENT_PLAYER.equals(transformedName)) {
            this.addClientPlayerMethods(classNode);
        } else if(RENDER_BOAT.equals(transformedName)) {
            this.addBoatBridge(classNode);
        } else if(MODEL_BIPED.equals(transformedName)) {
            this.addModelBiped(classNode);
        } else if(RENDER_PLAYER.equals(transformedName)) {
            this.addRenderPlayer(classNode);
        } else if(CHARACTER_CREATION_RENDERER.equals(transformedName)) {
            this.addCharacterCreationRendererLightingBridge(classNode);
            this.verifyCharacterCreationRendererLightingBridge(classNode);
        } else if(this.isCharacterCreationModelAdapter(transformedName)) {
            this.addCharacterCreationModelPost(classNode);
        } else if(ENTITY_RENDERER.equals(transformedName)) {
            this.addCameraBridge(classNode);
            this.addCameraCollisionBridge(classNode);
            this.verifyCameraBridge(classNode);
            this.verifyCameraCollisionBridge(classNode);
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        byte[] result = writer.toByteArray();
        return result;
    }

    private boolean isCharacterCreationModelAdapter(String transformedName) {
        if (transformedName == null || !transformedName.startsWith(CHARACTER_CREATION_MODEL_PREFIX)) return false;
        return transformedName.endsWith("DwarfModelAdapter")
            || transformedName.endsWith("ElfModelAdapter")
            || transformedName.endsWith("HobbitModelAdapter")
            || transformedName.endsWith("ManModelAdapter")
            || transformedName.endsWith("OrcModelAdapter");
    }

    /**
     * Character Creation's adapters invoke the complete LOTRModel* angle method after
     * ModelBiped's transformed method has run. Apply Aqua's swimming limbs once more
     * at the adapter RETURN so LOTR's final rotations cannot overwrite them.
     */
    private void addCharacterCreationModelPost(ClassNode classNode) {
        MethodNode target = null;
        for (MethodNode method : classNode.methods) {
            if (!("setRotationAngles".equals(method.name) || "func_78087_a".equals(method.name)
                || "a".equals(method.name))) continue;

            org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(method.desc);
            if (args.length != 7 || org.objectweb.asm.Type.getReturnType(method.desc).getSort() != org.objectweb.asm.Type.VOID) continue;
            boolean shape = true;
            for (int i = 0; i < 6; ++i) {
                if (args[i].getSort() != org.objectweb.asm.Type.FLOAT) {
                    shape = false;
                    break;
                }
            }
            if (!shape || args[6].getSort() != org.objectweb.asm.Type.OBJECT) continue;
            if (target != null) {
                throw new IllegalStateException("Ambiguous Character Creation model angle target in " + classNode.name);
            }
            target = method;
        }
        if (target == null) {
            throw new IllegalStateException("Missing Character Creation model angle target in " + classNode.name);
        }

        int returns = 0;
        for (AbstractInsnNode instruction = target.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {

            if (instruction.getOpcode() != Opcodes.RETURN) continue;
            InsnList post = new InsnList();
            post.add(new VarInsnNode(Opcodes.ALOAD, 0));
            for (int i = 1; i <= 6; ++i) post.add(new VarInsnNode(Opcodes.FLOAD, i));
            post.add(new VarInsnNode(Opcodes.ALOAD, 7));
            post.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                MODEL_LOGIC,
                "postCharacterCreation",
                "(Ljava/lang/Object;FFFFFFLjava/lang/Object;)V",
                false));
            target.instructions.insertBefore(instruction, post);
            ++returns;
        }
        if (returns != 1) {
            throw new IllegalStateException(
                "Character Creation model angle target expected one RETURN in " + classNode.name + ", found " + returns);
        }
    }


    /**
     * Vanilla 1.7.10 third-person camera tracing uses World#rayTraceBlocks(Vec3, Vec3),
     * which explicitly includes non-collidable blocks. Route the one orientCamera
     * ray-trace call through a helper that asks World to ignore blocks without a
     * collision box so tall grass, flowers, and similar permeable blocks do not
     * collapse the camera into the player's head.
     */
    private void addCameraCollisionBridge(ClassNode c) {
        MethodNode camera = findMethod(c, "(F)V", "orientCamera", "func_78467_g", "h");
        MethodInsnNode rayTrace = null;
        for (AbstractInsnNode i = camera.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof MethodInsnNode)) continue;
            MethodInsnNode x = (MethodInsnNode) i;
            if (x.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
            if (!("rayTraceBlocks".equals(x.name) || "func_72933_a".equals(x.name) || "a".equals(x.name))) continue;
            org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(x.desc);
            org.objectweb.asm.Type result = org.objectweb.asm.Type.getReturnType(x.desc);
            if (args.length != 2 || args[0].getSort() != org.objectweb.asm.Type.OBJECT
                || args[1].getSort() != org.objectweb.asm.Type.OBJECT
                || result.getSort() != org.objectweb.asm.Type.OBJECT) continue;
            if (rayTrace != null) throw new IllegalStateException("ambiguous EntityRenderer third-person ray trace");
            rayTrace = x;
        }
        if (rayTrace == null) throw new IllegalStateException("missing EntityRenderer third-person ray trace");

        String worldOwner = rayTrace.owner;
        String originalDescriptor = rayTrace.desc;
        rayTrace.setOpcode(Opcodes.INVOKESTATIC);
        rayTrace.owner = CAMERA_COLLISION_LOGIC;
        rayTrace.name = "rayTraceCameraBlocks";
        rayTrace.desc = "(L" + worldOwner + ";" + originalDescriptor.substring(1);
        rayTrace.itf = false;
    }

    private void verifyCameraCollisionBridge(ClassNode c) {
        MethodNode camera = findMethod(c, "(F)V", "orientCamera", "func_78467_g", "h");
        int bridges = 0;
        for (AbstractInsnNode i = camera.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof MethodInsnNode)) continue;
            MethodInsnNode x = (MethodInsnNode) i;
            if (!CAMERA_COLLISION_LOGIC.equals(x.owner)) continue;
            if (x.getOpcode() != Opcodes.INVOKESTATIC || !"rayTraceCameraBlocks".equals(x.name)) {
                throw new IllegalStateException("unexpected EntityRenderer camera collision bridge");
            }
            org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(x.desc);
            if (args.length != 3 || args[0].getSort() != org.objectweb.asm.Type.OBJECT
                || args[1].getSort() != org.objectweb.asm.Type.OBJECT
                || args[2].getSort() != org.objectweb.asm.Type.OBJECT
                || org.objectweb.asm.Type.getReturnType(x.desc).getSort() != org.objectweb.asm.Type.OBJECT) {
                throw new IllegalStateException("invalid EntityRenderer camera collision bridge descriptor");
            }
            ++bridges;
        }
        if (bridges != 1) {
            throw new IllegalStateException("EntityRenderer camera collision bridge verification failed: " + bridges);
        }
    }

    /**
     * RenderManager has already chosen a lightmap before Character Creation enters
     * its RacePlayerRenderer. Character Creation temporarily changes local yOffset
     * during that same render tick, so reapply the physical-player lightmap directly
     * before each RacePlayerRenderer -> RenderPlayer delegation.
     */
    private void addCharacterCreationRendererLightingBridge(ClassNode c) {
        MethodNode render = null;
        for (MethodNode method : c.methods) {
            org.objectweb.asm.Type[] args = org.objectweb.asm.Type.getArgumentTypes(method.desc);
            if (args.length != 6 || args[0].getSort() != org.objectweb.asm.Type.OBJECT
                || args[1].getSort() != org.objectweb.asm.Type.DOUBLE
                || args[2].getSort() != org.objectweb.asm.Type.DOUBLE
                || args[3].getSort() != org.objectweb.asm.Type.DOUBLE
                || args[4].getSort() != org.objectweb.asm.Type.FLOAT
                || args[5].getSort() != org.objectweb.asm.Type.FLOAT
                || org.objectweb.asm.Type.getReturnType(method.desc).getSort() != org.objectweb.asm.Type.VOID) continue;
            if (!("doRender".equals(method.name) || "func_76986_a".equals(method.name) || "a".equals(method.name))) continue;
            if (render != null) throw new IllegalStateException("ambiguous Character Creation player render target");
            render = method;
        }
        if (render == null) throw new IllegalStateException("missing Character Creation player render target");

        int delegates = 0;
        for (AbstractInsnNode i = render.instructions.getFirst(); i != null; i = i.getNext()) {
            if (!(i instanceof MethodInsnNode)) continue;
            MethodInsnNode x = (MethodInsnNode) i;
            if (x.getOpcode() != Opcodes.INVOKESPECIAL || !c.superName.equals(x.owner) || !render.desc.equals(x.desc)) continue;

            InsnList light = new InsnList();
            light.add(new VarInsnNode(Opcodes.ALOAD, 1));
            light.add(new VarInsnNode(Opcodes.FLOAD, 9));
            light.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                PLAYER_LIGHTING_LOGIC,
                "applyCharacterCreationLightmap",
                "(Ljava/lang/Object;F)V",
                false));
            render.instructions.insertBefore(x, light);
            ++delegates;
        }
        if (delegates != 2) {
            throw new IllegalStateException(
                "Character Creation renderer expected two RenderPlayer delegates, found " + delegates);
        }
    }

    private void verifyCharacterCreationRendererLightingBridge(ClassNode c) {
        int bridges = 0;
        for (MethodNode method : c.methods) {
            for (AbstractInsnNode i = method.instructions.getFirst(); i != null; i = i.getNext()) {
                if (!(i instanceof MethodInsnNode)) continue;
                MethodInsnNode x = (MethodInsnNode) i;
                if (!PLAYER_LIGHTING_LOGIC.equals(x.owner)) continue;
                if (x.getOpcode() != Opcodes.INVOKESTATIC
                    || !"applyCharacterCreationLightmap".equals(x.name)
                    || !"(Ljava/lang/Object;F)V".equals(x.desc)) {
                    throw new IllegalStateException("unexpected Character Creation player lighting bridge");
                }
                ++bridges;
            }
        }
        if (bridges != 2) {
            throw new IllegalStateException(
                "Character Creation player lighting bridge verification failed: " + bridges);
        }
    }

    private MethodNode findMethod(ClassNode c,String desc,String... names){MethodNode r=null;for(MethodNode m:c.methods){if(!m.desc.equals(desc))continue;boolean ok=false;for(String n:names)if(n.equals(m.name))ok=true;if(!ok)continue;if(r!=null)throw new IllegalStateException("Ambiguous Aqua client target "+desc);r=m;}if(r==null)throw new IllegalStateException("Missing Aqua client target "+desc);return r;}
    private void addClientPlayerPushOutHooks(ClassNode c){MethodNode m=findMethod(c,"(DDD)Z","func_145771_j","j");LabelNode continueOriginal=new LabelNode();InsnList h=new InsnList();h.add(new VarInsnNode(Opcodes.ALOAD,0));h.add(new VarInsnNode(Opcodes.DLOAD,1));h.add(new VarInsnNode(Opcodes.DLOAD,5));h.add(new MethodInsnNode(Opcodes.INVOKESTATIC,PLAYER_MOVEMENT_POLICY,"handleExactPlayerBlockCollision","(L"+c.name+";DD)Z",false));h.add(new JumpInsnNode(Opcodes.IFEQ,continueOriginal));h.add(new InsnNode(Opcodes.ICONST_0));h.add(new InsnNode(Opcodes.IRETURN));h.add(continueOriginal);m.instructions.insert(h);MethodInsnNode round=null;for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if("java/lang/Math".equals(x.owner)&&"round".equals(x.name)&&"(F)I".equals(x.desc)){if(round!=null)throw new IllegalStateException("Ambiguous EntityPlayerSP Math.round redirect");round=x;}}if(round==null)throw new IllegalStateException("Missing EntityPlayerSP Math.round redirect");round.setOpcode(Opcodes.INVOKESTATIC);round.owner=PLAYER_MOVEMENT_POLICY;round.name="roundPlayerBlockCollisionOffset";round.itf=false;MethodNode action=findMethod(
        c,
        "()V",
        "updateEntityActionState",
        "func_70626_be",
        "bq");
        int tails=0;for(AbstractInsnNode i=action.instructions.getFirst();i!=null;i=i.getNext())if(i.getOpcode()==Opcodes.RETURN){InsnList tail=new InsnList();tail.add(new VarInsnNode(Opcodes.ALOAD,0));tail.add(new MethodInsnNode(Opcodes.INVOKESTATIC,PLAYER_MOVEMENT_POLICY,"applyForcedLandCrawlMovement","(L"+c.name+";)V",false));action.instructions.insertBefore(i,tail);tails++;}if(tails!=1)throw new IllegalStateException("EntityPlayerSP expected one updateEntityActionState return, found "+tails);}
    private void addMovementStorageAccess(ClassNode c){if(c.interfaces.contains(MOVEMENT_STORAGE_ACCESS))throw new IllegalStateException("Duplicate movement storage access interface");c.interfaces.add(MOVEMENT_STORAGE_ACCESS);String d="L"+MOVEMENT_STORAGE+";";for(FieldNode f:c.fields)if("aqua$movementStorage".equals(f.name)&&d.equals(f.desc))throw new IllegalStateException("Duplicate movement storage field");c.fields.add(new FieldNode(Opcodes.ACC_PRIVATE,"aqua$movementStorage",d,null,null));absent(c,"aqua$getMovementStorage","()"+d);MethodNode g=new MethodNode(Opcodes.ACC_PUBLIC,"aqua$getMovementStorage","()"+d,null,null);LabelNode ready=new LabelNode();g.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));g.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,c.name,"aqua$movementStorage",d));g.instructions.add(new JumpInsnNode(Opcodes.IFNONNULL,ready));g.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));g.instructions.add(new org.objectweb.asm.tree.TypeInsnNode(Opcodes.NEW,MOVEMENT_STORAGE));g.instructions.add(new InsnNode(Opcodes.DUP));g.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,MOVEMENT_STORAGE,"<init>","()V",false));g.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,c.name,"aqua$movementStorage",d));g.instructions.add(ready);g.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));g.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,c.name,"aqua$movementStorage",d));g.instructions.add(new InsnNode(Opcodes.ARETURN));c.methods.add(g);}
    private void addCameraBridge(ClassNode c) {
      MethodNode camera=findMethod(c,"(F)V","orientCamera","func_78467_g","h"); String entity=viewEntityDesc(c); VarInsnNode store=null;
      for(AbstractInsnNode i=camera.instructions.getFirst();i!=null;i=i.getNext()) if(i instanceof VarInsnNode&&i.getOpcode()==Opcodes.FSTORE&&((VarInsnNode)i).var==3) { AbstractInsnNode a=previousOpcode(i),b=previousOpcode(a),d=previousOpcode(b),n=nextOpcode(i),n2=nextOpcode(n); if(a!=null&&a.getOpcode()==Opcodes.FSUB&&b instanceof org.objectweb.asm.tree.LdcInsnNode&&((org.objectweb.asm.tree.LdcInsnNode)b).cst instanceof Float&&((Float)((org.objectweb.asm.tree.LdcInsnNode)b).cst).floatValue()==1.62F&&d instanceof FieldInsnNode&&d.getOpcode()==Opcodes.GETFIELD&&"F".equals(((FieldInsnNode)d).desc)&&isCameraYOffset(((FieldInsnNode)d).name)&&n instanceof VarInsnNode&&n.getOpcode()==Opcodes.ALOAD&&((VarInsnNode)n).var==2&&n2 instanceof FieldInsnNode&&n2.getOpcode()==Opcodes.GETFIELD&&"D".equals(((FieldInsnNode)n2).desc)&&isCameraPrevX(((FieldInsnNode)n2).name)) {if(store!=null)throw new IllegalStateException("ambiguous EntityRenderer camera local");store=(VarInsnNode)i;} }
      if(store==null)throw new IllegalStateException("missing EntityRenderer yOffset-1.62 local 3 before prevPosX"); InsnList h=new InsnList();h.add(new VarInsnNode(Opcodes.ALOAD,2));h.add(new VarInsnNode(Opcodes.FLOAD,1));h.add(new VarInsnNode(Opcodes.FLOAD,3));h.add(new MethodInsnNode(Opcodes.INVOKESTATIC,CAMERA_RENDER_LOGIC,"getCameraHeight","(L"+entity+";FF)F",false));h.add(new VarInsnNode(Opcodes.FSTORE,3));camera.instructions.insert(store,h);
    }
    private AbstractInsnNode previousOpcode(AbstractInsnNode i){for(i=i==null?null:i.getPrevious();i!=null&&i.getOpcode()<0;i=i.getPrevious());return i;} private AbstractInsnNode nextOpcode(AbstractInsnNode i){for(i=i==null?null:i.getNext();i!=null&&i.getOpcode()<0;i=i.getNext());return i;} private boolean isCameraYOffset(String n){return "yOffset".equals(n)||"field_70129_M".equals(n)||"L".equals(n);} private boolean isCameraPrevX(String n){return "prevPosX".equals(n)||"field_70169_q".equals(n)||"p".equals(n);}
    private String viewEntityDesc(ClassNode c){return "blt".equals(c.name)?"sv":"net/minecraft/entity/EntityLivingBase";} private void verifyCameraBridge(ClassNode c){MethodNode camera=findMethod(c,"(F)V","orientCamera","func_78467_g","h");int bridges=0;for(AbstractInsnNode i=camera.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode&&CAMERA_RENDER_LOGIC.equals(((MethodInsnNode)i).owner)){MethodInsnNode x=(MethodInsnNode)i;if(!"getCameraHeight".equals(x.name)||!("(L"+viewEntityDesc(c)+";FF)F").equals(x.desc))throw new IllegalStateException("unexpected EntityRenderer camera bridge");AbstractInsnNode p=i.getPrevious(),p2=p==null?null:p.getPrevious(),p3=p2==null?null:p2.getPrevious(),n=i.getNext();if(!(p instanceof VarInsnNode)||p.getOpcode()!=Opcodes.FLOAD||((VarInsnNode)p).var!=3||!(p2 instanceof VarInsnNode)||p2.getOpcode()!=Opcodes.FLOAD||((VarInsnNode)p2).var!=1||!(p3 instanceof VarInsnNode)||p3.getOpcode()!=Opcodes.ALOAD||((VarInsnNode)p3).var!=2||!(n instanceof VarInsnNode)||n.getOpcode()!=Opcodes.FSTORE||((VarInsnNode)n).var!=3)throw new IllegalStateException("invalid EntityRenderer camera bridge locals");bridges++;}if(bridges!=1)throw new IllegalStateException("EntityRenderer camera bridge verification failed: "+bridges);}
    private void absent(ClassNode c,String n,String d){for(MethodNode m:c.methods)if(m.name.equals(n)&&m.desc.equals(d))throw new IllegalStateException("Aqua client method collision "+n+d);}
    private void addModelBiped(ClassNode c){if(c.interfaces.contains(MODEL_INTERFACE))throw new IllegalStateException("Duplicate ModelBiped interface");c.interfaces.add(MODEL_INTERFACE);c.fields.add(new FieldNode(Opcodes.ACC_PUBLIC,"swimAnimation","F",null,null)); absent(c,"setSwimAnimation","(F)V");MethodNode set=new MethodNode(Opcodes.ACC_PUBLIC,"setSwimAnimation","(F)V",null,null);set.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));set.instructions.add(new VarInsnNode(Opcodes.FLOAD,1));set.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD,c.name,"swimAnimation","F"));set.instructions.add(new InsnNode(Opcodes.RETURN));c.methods.add(set); absent(c,"getSwimAnimation","()F");MethodNode get=new MethodNode(Opcodes.ACC_PUBLIC,"getSwimAnimation","()F",null,null);get.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));get.instructions.add(new FieldInsnNode(Opcodes.GETFIELD,c.name,"swimAnimation","F"));get.instructions.add(new InsnNode(Opcodes.FRETURN));c.methods.add(get);
      MethodNode render=findMethod(c,"(L"+entityDesc(c)+";FFFFFF)V","render","func_78088_a","a");MethodInsnNode call=null;for(AbstractInsnNode i=render.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).name.equals("setRotationAngles")||i instanceof MethodInsnNode&&((MethodInsnNode)i).name.equals("func_78087_a")||i instanceof MethodInsnNode&&((MethodInsnNode)i).name.equals("a")){MethodInsnNode x=(MethodInsnNode)i;if(x.desc.endsWith(")V")&&x.desc.contains("FFFFFF")){if(call!=null)throw new IllegalStateException("multiple biped render angle calls");call=x;}}if(call==null)throw new IllegalStateException("missing biped render angle call");call.setOpcode(Opcodes.INVOKESTATIC);call.owner=MODEL_LOGIC;call.name="render";call.desc="(L"+c.name+";"+call.desc.substring(1);call.itf=false;
      MethodNode angles=findMethod(c,"(FFFFFFL"+entityDesc(c)+";)V","setRotationAngles","func_78087_a","a");FieldInsnNode on=null;for(AbstractInsnNode i=angles.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof FieldInsnNode&&i.getOpcode()==Opcodes.GETFIELD&&"F".equals(((FieldInsnNode)i).desc)&&("onGround".equals(((FieldInsnNode)i).name)||"field_78095_p".equals(((FieldInsnNode)i).name)||"p".equals(((FieldInsnNode)i).name))){on=(FieldInsnNode)i;break;}if(on==null)throw new IllegalStateException("missing biped onGround ordinal 0");InsnList pre=new InsnList();pre.add(new VarInsnNode(Opcodes.ALOAD,0));for(int x=1;x<=6;x++)pre.add(new VarInsnNode(Opcodes.FLOAD,x));pre.add(new VarInsnNode(Opcodes.ALOAD,7));pre.add(new MethodInsnNode(Opcodes.INVOKESTATIC,MODEL_LOGIC,"pre","(L"+c.name+";FFFFFFL"+entityDesc(c)+";)V",false));angles.instructions.insertBefore(on,pre);for(AbstractInsnNode i=angles.instructions.getFirst();i!=null;i=i.getNext())if(i.getOpcode()==Opcodes.RETURN){InsnList post=new InsnList();post.add(new VarInsnNode(Opcodes.ALOAD,0));for(int x=1;x<=6;x++)post.add(new VarInsnNode(Opcodes.FLOAD,x));post.add(new VarInsnNode(Opcodes.ALOAD,7));post.add(new MethodInsnNode(Opcodes.INVOKESTATIC,MODEL_LOGIC,"post","(L"+c.name+";FFFFFFL"+entityDesc(c)+";)V",false));angles.instructions.insertBefore(i,post);}
      String livingName = "bhm".equals(c.name) ? "func_78086_a" : "setLivingAnimations"; MethodNode living=new MethodNode(Opcodes.ACC_PUBLIC,livingName,"(L"+livingDesc(c)+";FFF)V",null,null);living.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));living.instructions.add(new VarInsnNode(Opcodes.ALOAD,1));living.instructions.add(new VarInsnNode(Opcodes.FLOAD,2));living.instructions.add(new VarInsnNode(Opcodes.FLOAD,3));living.instructions.add(new VarInsnNode(Opcodes.FLOAD,4));living.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,MODEL_LOGIC,"living","(L"+c.name+";L"+livingDesc(c)+";FFF)V",false));living.instructions.add(new InsnNode(Opcodes.RETURN));c.methods.add(living);verifyModel(c);}
    private String entityDesc(ClassNode c){return "bhm".equals(c.name)?"sa":"net/minecraft/entity/Entity";} private String livingDesc(ClassNode c){return "bhm".equals(c.name)?"sv":"net/minecraft/entity/EntityLivingBase";}
    private void verifyModel(ClassNode c){int iface=0,set=0,get=0,living=0;String livingName="bhm".equals(c.name)?"func_78086_a":"setLivingAnimations";String livingDesc="(L"+livingDesc(c)+";FFF)V";for(String x:c.interfaces)if(MODEL_INTERFACE.equals(x))iface++;for(MethodNode m:c.methods){if(m.name.equals("setSwimAnimation")&&m.desc.equals("(F)V"))set++;if(m.name.equals("getSwimAnimation")&&m.desc.equals("()F"))get++;if(m.name.equals(livingName)&&m.desc.equals(livingDesc))living++;}if(iface!=1||set!=1||get!=1||living!=1)throw new IllegalStateException("ModelBiped verification failed: iface="+iface+", set="+set+", get="+get+", living="+living);}
    private void addRenderPlayer(ClassNode c){
      MethodNode arm=findMethod(c,"(L"+("bop".equals(c.name)?"yz":"net/minecraft/entity/player/EntityPlayer")+";)V","renderFirstPersonArm","func_76986_a","a"); MethodInsnNode angle=null;for(AbstractInsnNode i=arm.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if((x.name.equals("setRotationAngles")||x.name.equals("func_78087_a")||x.name.equals("a"))&&x.desc.contains("FFFFFF")){if(angle!=null)throw new IllegalStateException("ambiguous first person angles");angle=x;}}if(angle==null)throw new IllegalStateException("missing first person angles");String modelOwner=angle.owner;angle.setOpcode(Opcodes.INVOKESTATIC);angle.owner=RENDER_PLAYER_LOGIC;angle.name="firstPersonAngles";angle.desc="(L"+modelOwner+";"+angle.desc.substring(1);angle.itf=false;
      String player=("bop".equals(c.name)?"blg":"net/minecraft/client/entity/AbstractClientPlayer"); MethodNode render=findMethod(c,"(L"+player+";DDDFF)V","doRender","func_76986_a","a"); MethodInsnNode superRender=null;for(AbstractInsnNode i=render.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode){MethodInsnNode x=(MethodInsnNode)i;if((x.name.equals("doRender")||x.name.equals("func_76986_a")||x.name.equals("a"))&&x.desc.endsWith("DDDFF)V")&&x.desc.startsWith("(L")){if(superRender!=null)throw new IllegalStateException("ambiguous RenderPlayer super doRender");superRender=x;}}if(superRender==null)throw new IllegalStateException("missing RenderPlayer super doRender"); int l=render.maxLocals;render.maxLocals+=10;InsnList offset=new InsnList();offset.add(new VarInsnNode(Opcodes.FSTORE,l+9));offset.add(new VarInsnNode(Opcodes.FSTORE,l+8));offset.add(new VarInsnNode(Opcodes.DSTORE,l+6));offset.add(new VarInsnNode(Opcodes.DSTORE,l+4));offset.add(new VarInsnNode(Opcodes.DSTORE,l+2));offset.add(new VarInsnNode(Opcodes.ASTORE,l+1));offset.add(new VarInsnNode(Opcodes.ASTORE,l));offset.add(new VarInsnNode(Opcodes.ALOAD,l+1));offset.add(new VarInsnNode(Opcodes.DLOAD,l+4));offset.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RENDER_PLAYER_LOGIC,"crouchingY","(L"+player+";D)D",false));offset.add(new VarInsnNode(Opcodes.DSTORE,l+4));offset.add(new VarInsnNode(Opcodes.ALOAD,l));offset.add(new VarInsnNode(Opcodes.ALOAD,l+1));offset.add(new VarInsnNode(Opcodes.DLOAD,l+2));offset.add(new VarInsnNode(Opcodes.DLOAD,l+4));offset.add(new VarInsnNode(Opcodes.DLOAD,l+6));offset.add(new VarInsnNode(Opcodes.FLOAD,l+8));offset.add(new VarInsnNode(Opcodes.FLOAD,l+9));render.instructions.insertBefore(superRender,offset);
      MethodNode rotate=findMethod(c,"(L"+player+";FFF)V","rotateCorpse","func_77043_a","a");for(AbstractInsnNode i=rotate.instructions.getFirst();i!=null;i=i.getNext())if(i.getOpcode()==Opcodes.RETURN){InsnList h=new InsnList();h.add(new VarInsnNode(Opcodes.ALOAD,1));h.add(new VarInsnNode(Opcodes.FLOAD,2));h.add(new VarInsnNode(Opcodes.FLOAD,3));h.add(new VarInsnNode(Opcodes.FLOAD,4));h.add(new MethodInsnNode(Opcodes.INVOKESTATIC,RENDER_PLAYER_LOGIC,"rotations","(L"+player+";FFF)V",false));rotate.instructions.insertBefore(i,h);} verifyRenderPlayer(c);
    }
    private void verifyRenderPlayer(ClassNode c){int b=0;for(MethodNode m:c.methods)for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode&&RENDER_PLAYER_LOGIC.equals(((MethodInsnNode)i).owner))b++;if(b!=3)throw new IllegalStateException("RenderPlayer verification failed");}
    private void addBoatBridge(ClassNode classNode) {
        MethodNode target = null;
        MethodInsnNode secondScale = null;

        for (MethodNode method : classNode.methods) {
            if (!(method.name.equals("doRender") || method.name.equals("func_76986_a") || method.name.equals("a")) ||
                !method.desc.startsWith("(L") || !method.desc.endsWith("DDDFF)V")) continue;

            int scales = 0;
            MethodInsnNode candidateSecondScale = null;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null; instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode invocation = (MethodInsnNode) instruction;
                if (invocation.owner.equals("org/lwjgl/opengl/GL11") && invocation.name.equals("glScalef") && invocation.desc.equals("(FFF)V") && scales++ == 1) {
                    candidateSecondScale = invocation;
                }
            }
            if (candidateSecondScale == null) continue;
            if (target != null) throw new IllegalStateException("Ambiguous RenderBoat doRender scale target");
            target = method;
            secondScale = candidateSecondScale;
        }
        if (target == null) throw new IllegalStateException("No RenderBoat doRender second scale target");

        InsnList bridge = new InsnList();
        bridge.add(new VarInsnNode(Opcodes.ALOAD, 1));
        bridge.add(new VarInsnNode(Opcodes.FLOAD, 9));
        bridge.add(new MethodInsnNode(Opcodes.INVOKESTATIC, BOAT_LOGIC, "addRockingRotation",
            "(" + org.objectweb.asm.Type.getArgumentTypes(target.desc)[0].getDescriptor() + "F)V", false));
        target.instructions.insertBefore(secondScale, bridge);
    }

    private void addClientPlayerMethods(ClassNode c) {
        if (c.interfaces.contains(CLIENT_PLAYER_INTERFACE)) throw new IllegalStateException("Duplicate IPlayerSPSwimming");
        c.interfaces.add(CLIENT_PLAYER_INTERFACE);
        addClient(c,"isActuallySneaking","()Z"); addClient(c,"isForcedDown","()Z"); addClient(c,"isUsingSwimmingAnimation","()Z"); addClient(c,"isUsingSwimmingAnimation","(FF)Z"); addClient(c,"canSwim","()Z"); addClient(c,"isMovingForward","(FF)Z"); addClient(c,"canPerformElytraTakeoff","()Z");
        addClientPlayerNetworkStanceGuard(c);
        verifyClientPlayerNetworkStanceGuard(c);
    }
    private void addClient(ClassNode c,String name,String desc) { for(MethodNode old:c.methods)if(old.name.equals(name)&&old.desc.equals(desc))throw new IllegalStateException("Client method collision "+name+desc); MethodNode m=new MethodNode(Opcodes.ACC_PUBLIC,name,desc,null,null); m.instructions.add(new VarInsnNode(Opcodes.ALOAD,0)); if("(FF)Z".equals(desc)){m.instructions.add(new VarInsnNode(Opcodes.FLOAD,1));m.instructions.add(new VarInsnNode(Opcodes.FLOAD,2));} m.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC,CLIENT_PLAYER_LOGIC,name,"(L"+c.name+";"+desc.substring(1),false)); m.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.IRETURN)); c.methods.add(m); }

    private void addClientPlayerNetworkStanceGuard(ClassNode c) {
        MethodNode method = findSendMotionUpdates(c);
        int savedPosY = method.maxLocals;
        method.maxLocals += 2;

        AbstractInsnNode first = method.instructions.getFirst();
        while (first != null && first.getOpcode() < 0) first = first.getNext();
        if (first == null) throw new IllegalStateException("Empty EntityClientPlayerMP sendMotionUpdates");

        InsnList begin = new InsnList();
        begin.add(new VarInsnNode(Opcodes.ALOAD, 0));
        begin.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_PLAYER_LOGIC, "beginLegalNetworkStance",
            "(L" + c.name + ";)D", false));
        begin.add(new VarInsnNode(Opcodes.DSTORE, savedPosY));
        method.instructions.insertBefore(first, begin);

        int returns = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {
            if (instruction.getOpcode() != Opcodes.RETURN) continue;
            InsnList end = new InsnList();
            end.add(new VarInsnNode(Opcodes.ALOAD, 0));
            end.add(new VarInsnNode(Opcodes.DLOAD, savedPosY));
            end.add(new MethodInsnNode(Opcodes.INVOKESTATIC, CLIENT_PLAYER_LOGIC, "endLegalNetworkStance",
                "(L" + c.name + ";D)V", false));
            method.instructions.insertBefore(instruction, end);
            ++returns;
        }
        if (returns != 1) throw new IllegalStateException(
            "EntityClientPlayerMP sendMotionUpdates expected one RETURN, found " + returns);
    }

    /**
     * Namespace-independent target: sendMotionUpdates is the only ()V method in
     * EntityClientPlayerMP that constructs both position-only (DDDDZ) and
     * position+look (DDDDFFZ) movement packets.
     */
    private MethodNode findSendMotionUpdates(ClassNode c) {
        MethodNode result = null;
        for (MethodNode method : c.methods) {
            if (!"()V".equals(method.desc)) continue;
            boolean positionPacket = false;
            boolean positionLookPacket = false;
            for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
                instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode) instruction;
                if (!"<init>".equals(call.name)) continue;
                if ("(DDDDZ)V".equals(call.desc)) positionPacket = true;
                if ("(DDDDFFZ)V".equals(call.desc)) positionLookPacket = true;
            }
            if (!positionPacket || !positionLookPacket) continue;
            if (result != null) throw new IllegalStateException("Ambiguous EntityClientPlayerMP sendMotionUpdates");
            result = method;
        }
        if (result == null) throw new IllegalStateException("Missing EntityClientPlayerMP sendMotionUpdates");
        return result;
    }

    private void verifyClientPlayerNetworkStanceGuard(ClassNode c) {
        MethodNode method = findSendMotionUpdates(c);
        int begin = 0, end = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) instruction;
            if (!CLIENT_PLAYER_LOGIC.equals(call.owner)) continue;
            if ("beginLegalNetworkStance".equals(call.name) && ("(L" + c.name + ";)D").equals(call.desc)) ++begin;
            if ("endLegalNetworkStance".equals(call.name) && ("(L" + c.name + ";D)V").equals(call.desc)) ++end;
        }
        if (begin != 1 || end != 1) throw new IllegalStateException(
            "EntityClientPlayerMP network stance guard verification failed: begin=" + begin + ", end=" + end);
    }

    private void addWarpedWaterOverlayAlphaBridge(ClassNode classNode) {
        MethodNode warpedOverlay = this.findSingleWarpedOverlay(classNode);
        MethodInsnNode colorCall = this.findFirstGlColor4f(warpedOverlay);
        InsnList bridge = new InsnList();
        bridge.add(new MethodInsnNode(
            Opcodes.INVOKESTATIC,
            WATER_OVERLAY_RENDER_LOGIC,
            "getWarpedOverlayAlpha",
            "(F)F",
            false));
        warpedOverlay.instructions.insertBefore(colorCall, bridge);
    }

    private MethodNode findSingleWarpedOverlay(ClassNode classNode) {
        MethodNode result = null;
        for (MethodNode method : classNode.methods) {
            if (!"(F)V".equals(method.desc)
                || !(WARPED_OVERLAY_MCP.equals(method.name) || WARPED_OVERLAY_SRG.equals(method.name)
                    || WARPED_OVERLAY_NOTCH.equals(method.name))) continue;
            if (result != null) {
                throw new IllegalStateException(
                    "Aqua ItemRenderer found multiple renderWarpedTextureOverlay candidates: " + result.name
                        + result.desc + " and " + method.name + method.desc);
            }
            result = method;
        }
        if (result == null) {
            throw new IllegalStateException("Aqua ItemRenderer could not find renderWarpedTextureOverlay/(F)V");
        }
        return result;
    }

    private MethodInsnNode findFirstGlColor4f(MethodNode method) {
        int matches = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {

            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (invocation.getOpcode() != Opcodes.INVOKESTATIC || !GL11.equals(invocation.owner)
                || !GL_COLOR_4F.equals(invocation.name) || !GL_COLOR_4F_DESCRIPTOR.equals(invocation.desc)) continue;
            if (matches++ == 0) return invocation;
        }
        throw new IllegalStateException(
            "Aqua ItemRenderer could not find ordinal-0 GL11.glColor4f(FFFF)V in " + method.name + method.desc);
    }

    private void verifyWarpedWaterOverlayAlphaBridge(ClassNode classNode) {
        MethodNode warpedOverlay = this.findSingleWarpedOverlay(classNode);
        MethodInsnNode colorCall = this.findFirstGlColor4f(warpedOverlay);
        AbstractInsnNode previous = colorCall.getPrevious();
        if (!(previous instanceof MethodInsnNode)) {
            throw new IllegalStateException("Aqua ItemRenderer alpha bridge is not immediately before ordinal-0 GL11.glColor4f");
        }
        MethodInsnNode bridge = (MethodInsnNode) previous;
        if (bridge.getOpcode() != Opcodes.INVOKESTATIC || !WATER_OVERLAY_RENDER_LOGIC.equals(bridge.owner)
            || !"getWarpedOverlayAlpha".equals(bridge.name) || !"(F)F".equals(bridge.desc)) {
            throw new IllegalStateException("Aqua ItemRenderer has an unexpected warped-water alpha bridge");
        }
        int bridges = 0;
        for (AbstractInsnNode instruction = warpedOverlay.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {

            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (WATER_OVERLAY_RENDER_LOGIC.equals(invocation.owner) && "getWarpedOverlayAlpha".equals(invocation.name)
                && "(F)F".equals(invocation.desc)) ++bridges;
        }
        if (bridges != 1) {
            throw new IllegalStateException("Aqua ItemRenderer alpha bridge verification failed: bridges=" + bridges);
        }
    }

    private void addRemotePlayerPresentationBridge(ClassNode classNode) {
        MethodNode onUpdate = this.findSingleOnUpdate(classNode);
        String playerDescriptor = "L" + classNode.name + ";";
        int anchors = 0;
        for (AbstractInsnNode instruction = onUpdate.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {

            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (invocation.getOpcode() != Opcodes.INVOKESPECIAL || !classNode.superName.equals(invocation.owner)
                || !onUpdate.name.equals(invocation.name) || !"()V".equals(invocation.desc)) continue;

            InsnList bridge = new InsnList();
            bridge.add(new VarInsnNode(Opcodes.ALOAD, 0));
            bridge.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                REMOTE_PRESENTATION_LOGIC,
                "afterSuperOnUpdate",
                "(" + playerDescriptor + ")V",
                false));
            onUpdate.instructions.insert(instruction, bridge);
            ++anchors;
        }
        if (anchors != 1) {
            throw new IllegalStateException(
                "Aqua EntityOtherPlayerMP expected exactly one AbstractClientPlayer onUpdate super call, found "
                    + anchors);
        }
    }

    private MethodNode findSingleOnUpdate(ClassNode classNode) {
        MethodNode result = null;
        for (MethodNode method : classNode.methods) {
            if (!"()V".equals(method.desc) || !(ON_UPDATE_MCP.equals(method.name) || ON_UPDATE_SRG.equals(method.name)
                || ON_UPDATE_NOTCH.equals(method.name))) continue;
            if (result != null) {
                throw new IllegalStateException(
                    "Aqua EntityOtherPlayerMP found multiple onUpdate candidates: " + result.name + result.desc
                        + " and " + method.name + method.desc);
            }
            result = method;
        }
        if (result == null) {
            throw new IllegalStateException("Aqua EntityOtherPlayerMP could not find onUpdate()V");
        }
        return result;
    }

    private void verifyRemotePlayerPresentationBridge(ClassNode classNode) {
        MethodNode onUpdate = this.findSingleOnUpdate(classNode);
        int bridges = 0;
        for (AbstractInsnNode instruction = onUpdate.instructions.getFirst(); instruction != null;
            instruction = instruction.getNext()) {

            if (!(instruction instanceof MethodInsnNode)) continue;
            MethodInsnNode invocation = (MethodInsnNode) instruction;
            if (invocation.getOpcode() != Opcodes.INVOKESPECIAL || !classNode.superName.equals(invocation.owner)
                || !onUpdate.name.equals(invocation.name) || !"()V".equals(invocation.desc)) continue;

            AbstractInsnNode next = instruction.getNext();
            if (!(next instanceof VarInsnNode) || next.getOpcode() != Opcodes.ALOAD
                || ((VarInsnNode) next).var != 0 || !(next.getNext() instanceof MethodInsnNode)) {
                throw new IllegalStateException("Aqua EntityOtherPlayerMP bridge is not immediately after super onUpdate");
            }
            MethodInsnNode bridge = (MethodInsnNode) next.getNext();
            if (!REMOTE_PRESENTATION_LOGIC.equals(bridge.owner) || !"afterSuperOnUpdate".equals(bridge.name)
                || bridge.getOpcode() != Opcodes.INVOKESTATIC) {
                throw new IllegalStateException("Aqua EntityOtherPlayerMP has an unexpected after-super bridge");
            }
            ++bridges;
        }
        if (bridges != 1) {
            throw new IllegalStateException(
                "Aqua EntityOtherPlayerMP bridge verification failed: bridges=" + bridges);
        }
    }
}
