package kome.core;

import java.io.*;
import java.util.Collections;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEProgressionBanditTransformerTest {
    @Test public void actualNativeBanditRollIsInstrumentedOnceWithoutChangingOtherSpawners()throws Exception {
        byte[] original;
        try(InputStream in=getClass().getResourceAsStream("/lotr/common/world/spawning/LOTREventSpawner.class");ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int read;while((read=in.read(buffer))>=0)out.write(buffer,0,read);original=out.toByteArray();
        }
        KOMEProgressionBanditTransformer transformer=new KOMEProgressionBanditTransformer();
        byte[] transformed=transformer.transform(KOMEProgressionBanditTransformer.TARGET,KOMEProgressionBanditTransformer.TARGET,original);
        ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);int hooks=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())
            if(insn instanceof MethodInsnNode&&"kome/common/data/KOMEProgressionBanditPressure".equals(((MethodInsnNode)insn).owner)){
                hooks++;assertEquals("spawnBandits",method.name);assertEquals("(DLnet/minecraft/world/World;II)D",((MethodInsnNode)insn).desc);
            }
        assertEquals(1,hooks);assertArrayEquals(transformed,transformer.transform(KOMEProgressionBanditTransformer.TARGET,KOMEProgressionBanditTransformer.TARGET,transformed));
        Class<?> executable=new ClassLoader(getClass().getClassLoader()){
            Class<?> define(){return defineClass(KOMEProgressionBanditTransformer.TARGET,transformed,0,transformed.length);}
        }.define();
        java.lang.reflect.Method method=executable.getDeclaredMethod("spawnBandits",net.minecraft.world.World.class,java.util.List.class);method.setAccessible(true);
        // JVM verifies the whole instrumented method; an empty native region performs no spawning.
        method.invoke(null,new kome.common.KOMEAccessFixture().world,Collections.emptyList());
    }
}
