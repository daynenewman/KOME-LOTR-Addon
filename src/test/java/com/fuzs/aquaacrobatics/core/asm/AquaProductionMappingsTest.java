package com.fuzs.aquaacrobatics.core.asm;

import static org.junit.Assert.*;
import static com.fuzs.aquaacrobatics.core.asm.AquaMappingFixtures.*;

import java.util.*;
import com.fuzs.aquaacrobatics.core.AquaAcrobaticsCore;
import kome.core.KOMECorePlugin;
import net.minecraft.launchwrapper.IClassTransformer;
import org.junit.After;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import org.objectweb.asm.util.CheckClassAdapter;

public class AquaProductionMappingsTest {
    @After public void restoreDevelopmentEnvironment() { environment(false); }
    private static void environment(boolean production) {
        new AquaAcrobaticsCore().injectData(Collections.<String,Object>singletonMap("runtimeDeobfuscationEnabled",production));
    }
    static byte[] bytes(ClassNode c) {
        ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);return w.toByteArray();
    }
    static ClassNode node(byte[] bytes) {ClassNode c=new ClassNode();new ClassReader(bytes).accept(c,0);return c;}
    static ClassNode apply(IClassTransformer t,ClassNode c,boolean production) throws Exception {
        environment(production);
        byte[] transformed=t.transform(c.name.replace('/','.'),c.name.replace('/','.'),bytes(c));
        verify(transformed);
        try {t.transform(c.name.replace('/','.'),c.name.replace('/','.'),transformed);fail("second application accepted");}
        catch(IllegalStateException expected){assertTrue(expected.getMessage(),expected.getMessage().contains("already transformed"));}
        ClassNode result=node(transformed);
        if(production) noDevelopmentReferences(result);
        return result;
    }
    static void verify(byte[] b) throws Exception {
        verifyStructure(b);
        ClassNode c=node(b);
        for(MethodNode m:c.methods)for(AbstractInsnNode i:m.instructions.toArray()) {
            if (!(i instanceof MethodInsnNode)) continue;
            MethodInsnNode call=(MethodInsnNode)i;
            if (!call.owner.startsWith("com/fuzs/aquaacrobatics/")) continue;
            java.io.InputStream stream=AquaProductionMappingsTest.class.getClassLoader().getResourceAsStream(call.owner+".class");
            assertNotNull(call.owner,stream);
            ClassNode helper=new ClassNode();try {new ClassReader(stream).accept(helper,ClassReader.SKIP_CODE);}finally{stream.close();}
            boolean found=false;
            for(MethodNode candidate:helper.methods)if(candidate.name.equals(call.name)&&candidate.desc.equals(call.desc)) {
                assertEquals(call.owner+"."+call.name,call.getOpcode()==INVOKESTATIC,(candidate.access&ACC_STATIC)!=0);found=true;
            }
            assertTrue("Unresolved helper link "+call.owner+"."+call.name+call.desc,found);
        }
    }
    static void verifyStructure(byte[] b) throws Exception {
        new ClassReader(b).accept(new CheckClassAdapter(new ClassWriter(0),true),0);
        ClassNode c=node(b);
        for(MethodNode m:c.methods)new Analyzer<BasicValue>(new BasicVerifier()).analyze(c.name,m);
    }
    static void noDevelopmentReferences(ClassNode c) {
        Set<String> forbidden=new HashSet<>(Arrays.asList("isJumping","rand","getSplashSound","width","height","setSize","setLivingAnimations","getFlag","setFlag","onEntityUpdate"));
        for(MethodNode m:c.methods){
            assertFalse("development override "+m.name,m.name.equals("setLivingAnimations")&&m.desc.equals("(L"+LIVING+";FFF)V"));
            for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext()){
                if(i instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)i;if(f.owner.startsWith("net/minecraft/"))assertFalse(f.owner+"."+f.name,forbidden.contains(f.name));}
                if(i instanceof MethodInsnNode){MethodInsnNode f=(MethodInsnNode)i;if(f.owner.startsWith("net/minecraft/"))assertFalse(f.owner+"."+f.name,forbidden.contains(f.name));}
            }
        }
    }
    static MethodNode methodNamed(ClassNode c,String name){for(MethodNode m:c.methods)if(m.name.equals(name))return m;throw new AssertionError(name);}
    static int calls(MethodNode m,String name){int count=0;for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).name.equals(name))count++;return count;}
    static void reference(ClassNode c,String owner,String name,String desc,int opcode) {
        int count=0;for(MethodNode m:c.methods)for(AbstractInsnNode i:m.instructions.toArray()){
            if(i instanceof FieldInsnNode){FieldInsnNode f=(FieldInsnNode)i;if(f.owner.equals(owner)&&f.name.equals(name)&&f.desc.equals(desc)&&i.getOpcode()==opcode)count++;}
            if(i instanceof MethodInsnNode){MethodInsnNode f=(MethodInsnNode)i;if(f.owner.equals(owner)&&f.name.equals(name)&&f.desc.equals(desc)&&i.getOpcode()==opcode)count++;}
        }assertTrue(owner+"."+name+desc,count>0);
    }
    @Test public void waterSelectsImplementationNotGetterInBothNamespaces() throws Exception {
        for(boolean p:new boolean[]{false,true}){
            ClassNode c=apply(new AquaCommonWorldTransformer(),entity(p),p);
            assertEquals(1,calls(methodNamed(c,n(p,"handleWaterMovement","func_70072_I")),"adjustWaterMovementY"));
            assertEquals(0,calls(methodNamed(c,n(p,"isInWater","func_70090_H")),"adjustWaterMovementY"));
        }
    }
    @Test public void waterMissingAndAmbiguousMethodsFailWithNamesDescriptorAndCount() {
        environment(true); ClassNode missing=entity(true);missing.methods.remove(methodNamed(missing,"func_70072_I"));
        failure(new AquaCommonWorldTransformer(),missing,"func_70072_I","()Z","matches=0");
        ClassNode ambiguous=entity(true);MethodNode duplicate=method(ambiguous,"handleWaterMovement","()Z");end(duplicate,ICONST_0);end(duplicate,IRETURN);
        failure(new AquaCommonWorldTransformer(),ambiguous,"handleWaterMovement","()Z","matches=2");
    }
    @Test public void waterRejectsWrongInvocationOwnerAndShape() {
        environment(true);ClassNode wrong=entity(true);
        for(AbstractInsnNode i:methodNamed(wrong,"func_70072_I").instructions.toArray())if(i instanceof MethodInsnNode)((MethodInsnNode)i).owner="unrelated/Box";
        failure(new AquaCommonWorldTransformer(),wrong,"AxisAlignedBB","INVOKEVIRTUAL");
        wrong=entity(true);
        for(AbstractInsnNode i:methodNamed(wrong,"func_70072_I").instructions.toArray())if(i instanceof LdcInsnNode)((LdcInsnNode)i).cst=0.5D;
        failure(new AquaCommonWorldTransformer(),wrong,"func_70072_I","matches=0");
    }
    @Test public void jumpingAndBoatAnchorsRejectWrongOwners() {
        environment(true);ClassNode wrong=living(true);
        for(AbstractInsnNode i:methodNamed(wrong,"func_70030_z").instructions.toArray())
            if(i instanceof MethodInsnNode&&((MethodInsnNode)i).name.equals("func_70055_a"))((MethodInsnNode)i).owner="unrelated/Entity";
        failure(new AquaCommonWorldTransformer(),wrong,"water-material");
        wrong=boat(true);
        for(AbstractInsnNode i:methodNamed(wrong,"func_70071_h_").instructions.toArray())
            if(i instanceof MethodInsnNode)((MethodInsnNode)i).owner="unrelated/Entity";
        failure(new AquaCommonWorldTransformer(),wrong,"setRotation(FF)","found 0");
    }
    @Test public void firstPersonArmUsesExactMethodAndModelOwner() throws Exception {
        for(boolean p:new boolean[]{false,true}){
            ClassNode c=renderer(p);
            MethodNode decoy=method(c,"unrelated", "(L"+PLAYER+";)V");end(decoy,RETURN);
            ClassNode result=apply(new AquaClientEntityTransformer(),c,p);
            assertEquals(1,calls(methodNamed(result,n(p,"renderFirstPersonArm","func_82441_a")),"firstPersonAngles"));
            assertEquals(0,calls(methodNamed(result,n(p,"doRender","func_76986_a")),"firstPersonAngles"));
        }
    }
    @Test public void firstPersonArmRejectsWrongNameDescriptorOwnerAndAmbiguity() {
        environment(true);
        ClassNode wrong=renderer(true);methodNamed(wrong,"func_82441_a").name="func_76986_a";
        failure(new AquaClientEntityTransformer(),wrong,"func_82441_a","matches=0");
        wrong=renderer(true);methodNamed(wrong,"func_82441_a").desc="(Ljava/lang/Object;)V";
        failure(new AquaClientEntityTransformer(),wrong,"func_82441_a","matches=0");
        wrong=renderer(true);for(AbstractInsnNode i:methodNamed(wrong,"func_82441_a").instructions.toArray())if(i instanceof MethodInsnNode)((MethodInsnNode)i).owner="unrelated/Model";
        failure(new AquaClientEntityTransformer(),wrong,"ModelBiped","matches=0");
        wrong=renderer(true);MethodNode duplicate=method(wrong,"renderFirstPersonArm","(L"+PLAYER+";)V");end(duplicate,RETURN);
        failure(new AquaClientEntityTransformer(),wrong,"func_82441_a","matches=2");
    }
    @Test public void livingAndPlayerJumpAccessorsUseSrgInProduction() throws Exception {
        for(boolean p:new boolean[]{false,true}){
            reference(apply(new AquaCommonWorldTransformer(),living(p),p),LIVING,n(p,"isJumping","field_70703_bu"),"Z",GETFIELD);
            ClassNode player=apply(new AquaEntityPlayerTransformer(),player(p),p);
            reference(player,PLAYER,n(p,"isJumping","field_70703_bu"),"Z",GETFIELD);
            reference(player,PLAYER,n(p,"getFlag","func_70083_f"),"(I)Z",INVOKEVIRTUAL);
            reference(player,PLAYER,n(p,"setFlag","func_70052_a"),"(IZ)V",INVOKEVIRTUAL);
            reference(player,LIVING,n(p,"onEntityUpdate","func_70030_z"),"()V",INVOKESPECIAL);
        }
    }
    @Test public void boatProtectedBridgesUseExactInheritedMembers() throws Exception {
        for(boolean p:new boolean[]{false,true}){
            ClassNode c=apply(new AquaCommonWorldTransformer(),boat(p),p);
            reference(c,c.name,n(p,"rand","field_70146_Z"),"Ljava/util/Random;",GETFIELD);
            reference(c,c.name,n(p,"getSplashSound","func_145777_O"),"()Ljava/lang/String;",INVOKEVIRTUAL);
        }
    }
    @Test public void serverResizeUsesEntityOwnerAndCorrectNames() throws Exception {
        for(boolean p:new boolean[]{false,true}){
            ClassNode c=apply(new AquaServerPlayerTransformer(),server(p),p);
            reference(c,ENTITY,n(p,"width","field_70130_N"),"F",GETFIELD);
            reference(c,ENTITY,n(p,"height","field_70131_O"),"F",GETFIELD);
            reference(c,ENTITY,n(p,"setSize","func_70105_a"),"(FF)V",INVOKEVIRTUAL);
        }
    }
    @Test public void modelOverrideUsesCorrectNameAndDescriptor() throws Exception {
        for(boolean p:new boolean[]{false,true}){
            ClassNode c=apply(new AquaClientEntityTransformer(),model(p),p);
            assertEquals("(L"+LIVING+";FFF)V",methodNamed(c,n(p,"setLivingAnimations","func_78086_a")).desc);
        }
    }
    @Test public void lateAndBiomePatchesVerifyAndRejectReapplication() throws Exception {
        for(boolean p:new boolean[]{false,true}){
            apply(new AquaLateClientPlayerTransformer(),late(p),p);
            apply(new AquaBiomeTransformer(),biome(),p);
        }
    }
    @Test public void grassAndMyceliumSelectFourArgumentSetBlockOverload() throws Exception {
        for(boolean p:new boolean[]{false,true})for(boolean mycelium:new boolean[]{false,true}) {
            ClassNode c=apply(new AquaCommonWorldTransformer(),grass(p,mycelium),p);
            MethodNode tick=methodNamed(c,n(p,"updateTick","func_149674_a"));
            assertEquals(1,calls(tick,n(p,"setBlock","func_147449_b")));
            assertEquals(1,calls(tick,"setBlockUnlessCoveredByLiquid"));
            assertEquals(0,calls(tick,"func_147465_d"));
            reference(c,"com/fuzs/aquaacrobatics/core/UnderwaterGrassLikeHandler","setBlockUnlessCoveredByLiquid",
                "(Lnet/minecraft/world/World;IIILnet/minecraft/block/Block;)Z",INVOKESTATIC);
        }
    }
    @Test public void throwableCollisionAndRayTraceBridgesResolve() throws Exception {
        for(boolean p:new boolean[]{false,true}) {
            ClassNode c=apply(new AquaCommonWorldTransformer(),throwable(p),p);
            reference(c,c.name,"func_145775_I","()V",INVOKEVIRTUAL);
            assertEquals(1,calls(methodNamed(c,n(p,"onUpdate","func_70071_h_")),"rayTraceThroughLiquid"));
        }
    }
    @Test public void optionalProductionJarRunLoadsDistributableTransformers() {
        String expected=System.getProperty("aqua.expectedJar");
        if(expected!=null)assertEquals(new java.io.File(expected).toURI(),
            java.net.URI.create(AquaCommonWorldTransformer.class.getProtectionDomain().getCodeSource().getLocation().toExternalForm()));
    }
    @Test public void rawEmittedMembersAreNotConfusedWithSrg() {
        assertEquals("bc",AquaAsmMappings.member("sv","isJumping","field_70703_bu","bc"));
        assertEquals("M",AquaAsmMappings.member("mw","width","field_70130_N","M"));
        assertEquals("N",AquaAsmMappings.member("mw","height","field_70131_O","N"));
        assertEquals("sa",AquaAsmMappings.type("mw",ENTITY,"sa"));
        assertEquals("a",AquaAsmMappings.member("bhm","setLivingAnimations","func_78086_a","a"));
    }
    @Test public void rawServerBoatAndModelUseObfuscatedMembersAndDescriptors() throws Exception {
        environment(true);
        Map<String,String> aliases=new HashMap<>();
        aliases.put(ENTITY,"sa");aliases.put(LIVING,"sv");aliases.put(PLAYER,"yz");
        aliases.put(MODEL,"bhm");aliases.put("net/minecraft/client/model/ModelBase","bhr");
        aliases.put("net/minecraft/util/DamageSource","ro");
        ClassNode[] fixtures={server(true),boat(true),model(true)};
        IClassTransformer[] transformers={new AquaServerPlayerTransformer(),new AquaCommonWorldTransformer(),new AquaClientEntityTransformer()};
        aliases.put(fixtures[0].name,"mw");aliases.put(fixtures[1].name,"xi");
        for(ClassNode c:fixtures)for(MethodNode m:c.methods) {
            if(m.name.equals("func_70071_h_"))aliases.put(c.name+"."+m.name+m.desc,"h");
            if(m.name.equals("func_70047_e"))aliases.put(c.name+"."+m.name+m.desc,"g");
            if(m.name.equals("func_70645_a")||m.name.equals("func_78088_a")||m.name.equals("func_78087_a"))aliases.put(c.name+"."+m.name+m.desc,"a");
        }
        aliases.put(fixtures[1].name+".func_70101_b(FF)V","b");
        aliases.put(MODEL+".field_78095_p","p");
        ClassNode[] results=new ClassNode[fixtures.length];
        for(int i=0;i<fixtures.length;i++) {
            ClassWriter w=new ClassWriter(0);
            fixtures[i].accept(new org.objectweb.asm.commons.RemappingClassAdapter(w,new org.objectweb.asm.commons.SimpleRemapper(aliases)));
            byte[] output=transformers[i].transform(aliases.get(fixtures[i].name),fixtures[i].name.replace('/','.'),w.toByteArray());
            verifyStructure(output);results[i]=node(output);
            try{transformers[i].transform(aliases.get(fixtures[i].name),fixtures[i].name.replace('/','.'),output);fail("duplicate raw transform");}
            catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("already transformed"));}
        }
        reference(results[0],"sa","M","F",GETFIELD);reference(results[0],"sa","N","F",GETFIELD);
        reference(results[0],"sa","a","(FF)V",INVOKEVIRTUAL);
        reference(results[1],"xi","Z","Ljava/util/Random;",GETFIELD);reference(results[1],"xi","O","()Ljava/lang/String;",INVOKEVIRTUAL);
        int override=0;for(MethodNode m:results[2].methods)if(m.name.equals("a")&&m.desc.equals("(Lsv;FFF)V"))override++;
        assertEquals(1,override);
    }
    @Test public void forgeMayQueryRegistryTwiceButEachReturnedListIsUniqueAndOrdered() {
        KOMECorePlugin plugin=new KOMECorePlugin();String[] first=plugin.getASMTransformerClass(),second=plugin.getASMTransformerClass();
        assertArrayEquals(first,second);assertEquals(first.length,new HashSet<>(Arrays.asList(first)).size());
        List<String> aqua=new ArrayList<>();for(String s:first)if(s.startsWith("com.fuzs.aquaacrobatics"))aqua.add(s.substring(s.lastIndexOf('.')+1));
        assertEquals(Arrays.asList("AquaEntityPlayerTransformer","AquaServerPlayerTransformer","AquaBiomeTransformer","AquaCommonWorldTransformer","AquaClientEntityTransformer","AquaLateClientPlayerTransformer"),aqua);
    }
    static void failure(IClassTransformer t,ClassNode c,String... parts) {
        byte[] original=bytes(c),copy=original.clone();
        try{t.transform(c.name.replace('/','.'),c.name.replace('/','.'),original);fail("expected rejection");}
        catch(IllegalStateException e){assertTrue(e.getMessage(),e.getMessage().contains(t.getClass().getSimpleName()));assertTrue(e.getMessage(),e.getMessage().contains(c.name));for(String part:parts)assertTrue(e.getMessage(),e.getMessage().contains(part));}
        assertArrayEquals(copy,original);
    }
}
