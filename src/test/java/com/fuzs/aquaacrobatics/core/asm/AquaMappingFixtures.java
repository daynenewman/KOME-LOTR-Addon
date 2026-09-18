package com.fuzs.aquaacrobatics.core.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Repository-authored, minimal executable instruction layouts; no game class bytes. */
final class AquaMappingFixtures implements Opcodes {
    static final String ENTITY = "net/minecraft/entity/Entity";
    static final String LIVING = "net/minecraft/entity/EntityLivingBase";
    static final String PLAYER = "net/minecraft/entity/player/EntityPlayer";
    static final String MODEL = "net/minecraft/client/model/ModelBiped";
    static final String ABSTRACT_PLAYER = "net/minecraft/client/entity/AbstractClientPlayer";
    static final String ANGLES = "(FFFFFFL" + ENTITY + ";)V";

    static String n(boolean srg, String mcp, String production) { return srg ? production : mcp; }

    static ClassNode type(String name, String parent) {
        ClassNode c = new ClassNode(); c.version = V1_8; c.access = ACC_PUBLIC;
        c.name = name; c.superName = parent;
        MethodNode init = method(c, "<init>", "()V");
        init.instructions.add(new VarInsnNode(ALOAD, 0));
        call(init, INVOKESPECIAL, parent, "<init>", "()V"); end(init, RETURN);
        return c;
    }
    static MethodNode method(ClassNode c, String name, String desc) {
        MethodNode m = new MethodNode(ACC_PUBLIC, name, desc, null, null);
        m.maxLocals = 1;
        for (Type t : Type.getArgumentTypes(desc)) m.maxLocals += t.getSize();
        m.maxStack = 24; c.methods.add(m); return m;
    }
    static void end(MethodNode m, int op) { m.instructions.add(new InsnNode(op)); }
    static void load(MethodNode m, int op, int slot) { m.instructions.add(new VarInsnNode(op, slot)); }
    static void call(MethodNode m, int opcode, String owner, String name, String desc) {
        m.instructions.add(new MethodInsnNode(opcode, owner, name, desc, opcode == INVOKEINTERFACE));
    }
    static void field(MethodNode m, int opcode, String owner, String name, String desc) {
        m.instructions.add(new FieldInsnNode(opcode, owner, name, desc));
    }
    static ClassNode entity(boolean srg) {
        ClassNode c = type(ENTITY, "java/lang/Object");
        MethodNode water = method(c, n(srg,"handleWaterMovement","func_70072_I"), "()Z");
        load(water, ALOAD, 0);
        field(water, GETFIELD, ENTITY, n(srg,"boundingBox","field_70121_D"), "Lnet/minecraft/util/AxisAlignedBB;");
        end(water, DCONST_0); water.instructions.add(new LdcInsnNode(-0.4000000059604645D)); end(water, DCONST_0);
        call(water, INVOKEVIRTUAL, "net/minecraft/util/AxisAlignedBB", n(srg,"expand","func_72314_b"), "(DDD)Lnet/minecraft/util/AxisAlignedBB;");
        end(water, POP); end(water, ICONST_0); end(water, IRETURN);
        MethodNode getter = method(c,n(srg,"isInWater","func_70090_H"),"()Z");
        load(getter, ALOAD,0); field(getter,GETFIELD,ENTITY,n(srg,"inWater","field_70171_ac"),"Z");end(getter,IRETURN);
        MethodNode move = method(c,n(srg,"moveEntity","func_70091_d"),"(DDD)V");
        load(move,ALOAD,0);field(move,GETFIELD,ENTITY,n(srg,"worldObj","field_70170_p"),"Lnet/minecraft/world/World;");
        end(move,ICONST_0);end(move,ICONST_0);end(move,ICONST_0);
        call(move,INVOKEVIRTUAL,"net/minecraft/world/World",n(srg,"getBlock","func_147439_a"),"(III)Lnet/minecraft/block/Block;");
        load(move,ASTORE,7);load(move,ALOAD,7);end(move,POP);end(move,RETURN);move.maxLocals=8;
        return c;
    }
    static ClassNode living(boolean srg) {
        ClassNode c=type(LIVING,ENTITY);
        c.fields.add(new FieldNode(ACC_PROTECTED,n(srg,"isJumping","field_70703_bu"),"Z",null,null));
        MethodNode tick=method(c,n(srg,"onEntityUpdate","func_70030_z"),"()V");
        load(tick,ALOAD,0);field(tick,GETSTATIC,"net/minecraft/block/material/Material",n(srg,"water","field_151586_h"),"Lnet/minecraft/block/material/Material;");
        call(tick,INVOKEVIRTUAL,LIVING,n(srg,"isInsideOfMaterial","func_70055_a"),"(Lnet/minecraft/block/material/Material;)Z");end(tick,POP);
        for(int i=0;i<3;i++){load(tick,ALOAD,0);end(tick,ICONST_0);call(tick,INVOKEVIRTUAL,LIVING,n(srg,"setAir","func_70050_g"),"(I)V");}end(tick,RETURN);
        MethodNode move=method(c,n(srg,"moveEntityWithHeading","func_70612_e"),"(FF)V");
        for(int i=0;i<2;i++){load(move,ALOAD,0);field(move,GETFIELD,LIVING,n(srg,"isCollidedHorizontally","field_70123_F"),"Z");end(move,POP);}end(move,RETURN);
        return c;
    }
    static ClassNode boat(boolean srg) {
        ClassNode c=type("net/minecraft/entity/item/EntityBoat",ENTITY);
        MethodNode tick=method(c,n(srg,"onUpdate","func_70071_h_"),"()V");
        for(int i=0;i<2;i++){load(tick,ALOAD,0);end(tick,FCONST_0);end(tick,FCONST_0);call(tick,INVOKEVIRTUAL,c.name,n(srg,"setRotation","func_70101_b"),"(FF)V");}end(tick,RETURN);
        return c;
    }
    static ClassNode server(boolean srg) {
        ClassNode c=type("net/minecraft/entity/player/EntityPlayerMP",PLAYER);
        end(method(c,n(srg,"onUpdate","func_70071_h_"),"()V"),RETURN);
        for(String name:new String[]{"getDefaultEyeHeight",n(srg,"getEyeHeight","func_70047_e")}){
            MethodNode m=method(c,name,"()F");end(m,FCONST_1);end(m,FRETURN);
        }
        end(method(c,n(srg,"onDeath","func_70645_a"),"(Lnet/minecraft/util/DamageSource;)V"),RETURN);
        return c;
    }
    static ClassNode model(boolean srg) {
        ClassNode c=type(MODEL,"net/minecraft/client/model/ModelBase");
        MethodNode render=method(c,n(srg,"render","func_78088_a"),"(L"+ENTITY+";FFFFFF)V");
        load(render,ALOAD,0); for(int i=2;i<=7;i++)load(render,FLOAD,i);load(render,ALOAD,1);
        call(render,INVOKEVIRTUAL,MODEL,n(srg,"setRotationAngles","func_78087_a"),ANGLES);end(render,RETURN);
        MethodNode angles=method(c,n(srg,"setRotationAngles","func_78087_a"),ANGLES);
        load(angles,ALOAD,0);field(angles,GETFIELD,MODEL,n(srg,"onGround","field_78095_p"),"F");end(angles,POP);end(angles,RETURN);
        return c;
    }
    static ClassNode renderer(boolean srg) {
        ClassNode c=type("net/minecraft/client/renderer/entity/RenderPlayer","net/minecraft/client/renderer/entity/RendererLivingEntity");
        MethodNode arm=method(c,n(srg,"renderFirstPersonArm","func_82441_a"),"(L"+PLAYER+";)V");
        end(arm,ACONST_NULL);for(int i=0;i<6;i++)end(arm,FCONST_0);load(arm,ALOAD,1);
        call(arm,INVOKEVIRTUAL,MODEL,n(srg,"setRotationAngles","func_78087_a"),ANGLES);end(arm,RETURN);
        MethodNode render=method(c,n(srg,"doRender","func_76986_a"),"(L"+ABSTRACT_PLAYER+";DDDFF)V");
        load(render,ALOAD,0);load(render,ALOAD,1);load(render,DLOAD,2);load(render,DLOAD,4);load(render,DLOAD,6);load(render,FLOAD,8);load(render,FLOAD,9);
        call(render,INVOKESPECIAL,c.superName,n(srg,"doRender","func_76986_a"),"(L"+LIVING+";DDDFF)V");end(render,RETURN);
        end(method(c,n(srg,"rotateCorpse","func_77043_a"),"(L"+ABSTRACT_PLAYER+";FFF)V"),RETURN);
        return c;
    }
    static ClassNode player(boolean srg) {
        ClassNode c=type(PLAYER,LIVING);
        MethodNode tick=method(c,n(srg,"onUpdate","func_70071_h_"),"()V");
        end(tick,ACONST_NULL);load(tick,ALOAD,0);
        call(tick,INVOKEVIRTUAL,"cpw/mods/fml/common/FMLCommonHandler","onPlayerPostTick","(L"+PLAYER+";)V");end(tick,RETURN);
        MethodNode sleep=method(c,n(srg,"sleepInBedAt","func_71018_a"),"(III)Lnet/minecraft/entity/player/EntityPlayer$EnumStatus;");
        load(sleep,ALOAD,0);end(sleep,FCONST_0);end(sleep,FCONST_0);call(sleep,INVOKEVIRTUAL,PLAYER,n(srg,"setSize","func_70105_a"),"(FF)V");end(sleep,ACONST_NULL);end(sleep,ARETURN);
        MethodNode eye=method(c,n(srg,"getEyeHeight","func_70047_e"),"()F");end(eye,FCONST_1);end(eye,FRETURN);
        end(method(c,n(srg,"onLivingUpdate","func_70636_d"),"()V"),RETURN);
        end(method(c,n(srg,"moveEntityWithHeading","func_70612_e"),"(FF)V"),RETURN);
        end(method(c,n(srg,"preparePlayerToSpawn","func_70065_x"),"()V"),RETURN);
        return c;
    }
    static ClassNode late(boolean srg) {
        ClassNode c=type("net/minecraft/client/entity/EntityPlayerSP",ABSTRACT_PLAYER);
        MethodNode update=method(c,n(srg,"onLivingUpdate","func_70636_d"),"()V");load(update,ALOAD,0);
        call(update,INVOKESPECIAL,c.superName,update.name,"()V");end(update,RETURN);return c;
    }
    static ClassNode grass(boolean srg, boolean mycelium) {
        ClassNode c=type("net/minecraft/block/"+(mycelium?"BlockMycelium":"BlockGrass"),"net/minecraft/block/Block");
        MethodNode tick=method(c,n(srg,"updateTick","func_149674_a"),"(Lnet/minecraft/world/World;IIILjava/util/Random;)V");
        for(int i=0;i<2;i++) {
            load(tick,ALOAD,1);load(tick,ILOAD,2);load(tick,ILOAD,3);load(tick,ILOAD,4);end(tick,ACONST_NULL);
            call(tick,INVOKEVIRTUAL,"net/minecraft/world/World",n(srg,"setBlock","func_147449_b"),"(IIILnet/minecraft/block/Block;)Z");end(tick,POP);
        }
        end(tick,RETURN);return c;
    }
    static ClassNode throwable(boolean srg) {
        ClassNode c=type("net/minecraft/entity/projectile/EntityThrowable",ENTITY);
        MethodNode tick=method(c,n(srg,"onUpdate","func_70071_h_"),"()V");
        load(tick,ALOAD,0);field(tick,GETFIELD,c.name,n(srg,"worldObj","field_70170_p"),"Lnet/minecraft/world/World;");
        end(tick,ACONST_NULL);end(tick,ACONST_NULL);
        call(tick,INVOKEVIRTUAL,"net/minecraft/world/World",n(srg,"rayTraceBlocks","func_72933_a"),"(Lnet/minecraft/util/Vec3;Lnet/minecraft/util/Vec3;)Lnet/minecraft/util/MovingObjectPosition;");end(tick,POP);
        load(tick,ALOAD,0);end(tick,DCONST_0);field(tick,PUTFIELD,c.name,n(srg,"posX","field_70165_t"),"D");
        end(tick,RETURN);return c;
    }
    static ClassNode biome() {
        ClassNode c=type("net/minecraft/world/biome/BiomeGenBase","java/lang/Object");
        MethodNode m=method(c,"getWaterColorMultiplier","()I");end(m,ICONST_1);end(m,IRETURN);return c;
    }
}
