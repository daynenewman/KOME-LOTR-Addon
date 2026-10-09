package kome.core;

import java.io.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMECampaignHealthTransformerTest {
    @Test public void nativeMutationAndSerializationEndpointsAreHookedExactlyOnce() throws Exception {
        check("net.minecraft.entity.EntityLivingBase",2); check("net.minecraft.entity.Entity",3);
    }
    @Test public void srgEndpointsAreRecognized() throws Exception {
        for(String target:new String[]{"net.minecraft.entity.EntityLivingBase","net.minecraft.entity.Entity"}) {
            ClassNode n=new ClassNode(); new ClassReader(bytes(target)).accept(n,0);
            for(MethodNode m:n.methods) {
                if(m.name.equals("setHealth"))m.name="func_70606_j";
                if(m.name.equals("readFromNBT"))m.name="func_70020_e";
                if(m.name.equals("writeToNBT"))m.name="func_70109_d";
            }
            ClassWriter w=new ClassWriter(0); n.accept(w);
            assertNotNull(new KOMECampaignHealthTransformer().transform("",target,w.toByteArray()));
        }
    }
    @Test public void changedFingerprintFailsClosed() throws Exception {
        ClassNode n=new ClassNode(); new ClassReader(bytes("net.minecraft.entity.EntityLivingBase")).accept(n,0);
        n.methods.removeIf(m -> m.name.equals("setHealth")); ClassWriter w=new ClassWriter(0); n.accept(w);
        try {new KOMECampaignHealthTransformer().transform("","net.minecraft.entity.EntityLivingBase",w.toByteArray());fail();}
        catch(IllegalStateException expected) {assertTrue(expected.getMessage().contains("fingerprint"));}
    }
    @Test public void transformedNativeSetterTracksActualDamageAndHealing() throws Exception {
        Class<?> cls=executable(false); kome.common.KOMEAccessFixture f=new kome.common.KOMEAccessFixture();
        net.minecraft.entity.passive.EntityPig pig=(net.minecraft.entity.passive.EntityPig)cls.getConstructor(net.minecraft.world.World.class).newInstance(f.world);
        pig.setHealth(4F); kome.common.data.KOMEHiredUnitRecord r=new kome.common.data.KOMEHiredUnitRecord();
        r.entity=pig.getUniqueID(); campaign(r);
        kome.common.data.KOMECampaignHealth.observe(r,pig);
        pig.setHealth(2F); pig.heal(1F); pig.setHealth(3F);
        assertEquals(2,kome.common.data.KOMECampaignHealthEntity.projection(pig).revision);
        assertEquals(3F,kome.common.data.KOMECampaignHealthEntity.projection(pig).hp,0F);
    }
    @Test public void transformedEntitySerializationRoundTripsCoherentDetachedMetadata() throws Exception {
        Class<?> cls=executable(true); kome.common.KOMEAccessFixture f=new kome.common.KOMEAccessFixture();
        net.minecraft.entity.passive.EntityPig pig=(net.minecraft.entity.passive.EntityPig)cls.getConstructor(net.minecraft.world.World.class).newInstance(f.world);
        pig.setHealth(4F); kome.common.data.KOMEHiredUnitRecord r=new kome.common.data.KOMEHiredUnitRecord();
        r.entity=pig.getUniqueID(); campaign(r);
        kome.common.data.KOMECampaignHealth.observe(r,pig);
        net.minecraft.nbt.NBTTagCompound saved=new net.minecraft.nbt.NBTTagCompound();pig.writeToNBT(saved);
        net.minecraft.entity.passive.EntityPig reload=(net.minecraft.entity.passive.EntityPig)cls.getConstructor(net.minecraft.world.World.class).newInstance(f.world);
        reload.readFromNBT(saved);
        assertEquals(kome.common.data.KOMECampaignHealthEntity.projection(pig).write(),kome.common.data.KOMECampaignHealthEntity.projection(reload).write());
        pig.setHealth(2F);kome.common.data.KOMECampaignHealthEntity.changed(pig);
        assertEquals(4F,saved.getCompoundTag(kome.common.data.KOMECampaignHealthProjection.ENTITY_TAG).getFloat("CurrentHP"),0F);
    }
    private static void campaign(kome.common.data.KOMEHiredUnitRecord r) throws Exception {
        java.lang.reflect.Method assign=kome.common.data.KOMEHiredUnitClassification.class.getDeclaredMethod("assignForCampaignWorkflow",kome.common.data.KOMEHiredUnitRecord.class);
        assign.setAccessible(true);assign.invoke(null,r);
    }
    private Class<?> executable(boolean serialization) throws Exception {
        String name="kome/core/HealthExecutable"+(serialization?"Nbt":"Mutation");
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS); w.visit(Opcodes.V1_8,Opcodes.ACC_PUBLIC,name,null,"net/minecraft/entity/passive/EntityPig",null);
        MethodVisitor c=w.visitMethod(Opcodes.ACC_PUBLIC,"<init>","(Lnet/minecraft/world/World;)V",null,null);c.visitCode();
        c.visitVarInsn(Opcodes.ALOAD,0);c.visitVarInsn(Opcodes.ALOAD,1);c.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/entity/passive/EntityPig","<init>","(Lnet/minecraft/world/World;)V",false);c.visitInsn(Opcodes.RETURN);c.visitMaxs(0,0);c.visitEnd();
        for(String method:serialization?new String[]{"readFromNBT","writeToNBT"}:new String[]{"setHealth"}) {
            String desc=serialization?"(Lnet/minecraft/nbt/NBTTagCompound;)V":"(F)V";
            MethodVisitor m=w.visitMethod(Opcodes.ACC_PUBLIC,method,desc,null,null);m.visitCode();m.visitVarInsn(Opcodes.ALOAD,0);
            m.visitVarInsn(serialization?Opcodes.ALOAD:Opcodes.FLOAD,1);m.visitMethodInsn(Opcodes.INVOKESPECIAL,"net/minecraft/entity/passive/EntityPig",method,desc,false);
            m.visitInsn(Opcodes.RETURN);m.visitMaxs(0,0);m.visitEnd();
        }
        w.visitEnd(); final byte[] transformed=new KOMECampaignHealthTransformer().transform("",serialization?"net.minecraft.entity.Entity":"net.minecraft.entity.EntityLivingBase",w.toByteArray());
        return new ClassLoader(getClass().getClassLoader()){Class<?> load(){return defineClass(null,transformed,0,transformed.length);}}.load();
    }
    private void check(String target,int expected) throws Exception {
        KOMECampaignHealthTransformer t=new KOMECampaignHealthTransformer();byte[] result=t.transform("",target,bytes(target));
        ClassNode n=new ClassNode();new ClassReader(result).accept(n,0);int count=0;
        for(MethodNode m:n.methods)for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode
            && ((MethodInsnNode)i).owner.equals("kome/common/data/KOMECampaignHealthEntity"))count++;
        for (MethodNode m:n.methods) if(m.instructions.size()>0) new org.objectweb.asm.tree.analysis.Analyzer(new org.objectweb.asm.tree.analysis.BasicVerifier()).analyze(n.name,m);
        assertEquals(expected,count);assertArrayEquals(result,t.transform("",target,result));
    }
    private byte[] bytes(String name)throws Exception {
        InputStream in=getClass().getClassLoader().getResourceAsStream(name.replace('.','/')+".class");assertNotNull(in);
        try {ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int k;(k=in.read(b))!=-1;)out.write(b,0,k);return out.toByteArray();}finally{in.close();}
    }
}
