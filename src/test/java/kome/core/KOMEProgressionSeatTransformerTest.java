package kome.core;

import java.io.*;
import java.util.jar.*;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class KOMEProgressionSeatTransformerTest {
    @Test public void shippedNativeGenerationReceivesBalancedIdempotentServerHook() throws Exception {
        try(JarFile jar=new JarFile("libs/LOTRMod v36.15.jar")){
            for(String path:new String[]{"lotr/common/world/structure2/LOTRWorldGenStructureBase2", "lotr/common/world/structure/LOTRWorldGenElfLordHouse",
                    "lotr/common/entity/item/LOTREntityTraderRespawn"}){
                ByteArrayOutputStream out=new ByteArrayOutputStream();try(InputStream in=jar.getInputStream(jar.getJarEntry(path+".class"))){byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);}
                KOMEProgressionSeatTransformer transformer=new KOMEProgressionSeatTransformer();byte[] original=out.toByteArray(),changed=transformer.transform(path.replace('/','.'),path.replace('/','.'),original);
                assertFalse(java.util.Arrays.equals(original,changed));assertArrayEquals(changed,transformer.transform("",path.replace('/','.'),changed));
                ClassNode node=new ClassNode();new ClassReader(changed).accept(node,0);int hooks=0,captures=0,replacements=0;
                for(MethodNode method:node.methods){boolean hooked=false;for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&KOMEProgressionSeatTransformer.BRIDGE.equals(((MethodInsnNode)i).owner)){hooks++;hooked=true;}
                    for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;
                        if(KOMEProgressionSeatTransformer.BRIDGE.equals(call.owner)&&"captureReplacement".equals(call.name)){
                            assertEquals("copyTraderDataFrom",method.name);captures++;
                        }
                        if(KOMEProgressionSeatTransformer.BRIDGE.equals(call.owner)&&"replacementSpawned".equals(call.name)){
                            assertTrue("func_70071_h_".equals(method.name)||"onUpdate".equals(method.name));replacements++;
                        }
                    }
                    if(hooked)new Analyzer<BasicValue>(new BasicVerifier()).analyze(node.name,method);
                }assertTrue(hooks>0);
                if(path.endsWith("LOTREntityTraderRespawn")){assertEquals(1,captures);assertEquals(1,replacements);assertEquals(2,hooks);}
            }
        }
    }
    @Test public void unrelatedClassesAndNonGenerationBytesAreUntouched(){byte[] bytes={1,2,3};assertSame(bytes,new KOMEProgressionSeatTransformer().transform("","net.minecraft.world.World",bytes));}
}
