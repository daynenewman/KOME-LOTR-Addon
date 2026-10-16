package kome.core;

import java.io.*;
import java.util.jar.JarFile;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class KOMEKnightPickpocketTransformerTest {
    @Test public void nativeStaffRewardFiltersAreIdempotentAndStackSafe()throws Exception{
        KOMEKnightPickpocketTransformer t=new KOMEKnightPickpocketTransformer();
        try(JarFile jar=new JarFile("libs/LOTRMod v36.15.jar")){
            for(String name:new String[]{"lotr.common.LOTREventHandler","lotr.common.entity.npc.LOTREntityNPC","lotr.common.entity.npc.LOTREntityRohirrimMarshal","lotr.common.entity.npc.LOTREntityMordorOrcMercenaryCaptain"}){
                byte[] original;try(InputStream in=jar.getInputStream(jar.getJarEntry(name.replace('.','/')+".class"));ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] block=new byte[8192];int n;while((n=in.read(block))>=0)out.write(block,0,n);original=out.toByteArray();}
                byte[] changed=t.transform(name,name,original);assertFalse(name,java.util.Arrays.equals(original,changed));assertArrayEquals(changed,t.transform(name,name,changed));ClassNode node=new ClassNode();new ClassReader(changed).accept(node,0);
                for(MethodNode method:node.methods){boolean hooked=false;for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode&&((MethodInsnNode)i).owner.equals("kome/common/data/KOMEProgressionDevelopment"))hooked=true;if(hooked)new Analyzer<BasicValue>(new BasicInterpreter()).analyze(node.name,method);}
            }
        }
    }
    @Test public void actualShippedTheftKeepsNativeAlgorithmAndAdaptsExactlyThreeSites()throws Exception{
        String name="lotr.common.quest.LOTRMiniQuestPickpocket";byte[] original;
        try(JarFile jar=new JarFile("libs/LOTRMod v36.15.jar");InputStream in=jar.getInputStream(jar.getJarEntry(name.replace('.','/')+".class"));ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] block=new byte[8192];int n;while((n=in.read(block))>=0)out.write(block,0,n);original=out.toByteArray();
        }
        KOMEKnightPickpocketTransformer t=new KOMEKnightPickpocketTransformer();byte[] changed=t.transform(name,name,original);assertFalse(java.util.Arrays.equals(original,changed));assertArrayEquals(changed,t.transform(name,name,changed));
        ClassNode node=new ClassNode();new ClassReader(changed).accept(node,0);int hooks=0,nativeThefts=0,witnesses=0;
        for(MethodNode m:node.methods){if(m.name.equals("onInteractOther"))new Analyzer<BasicValue>(new BasicInterpreter()).analyze(node.name,m);
            for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;if(call.owner.equals("kome/common/data/KOMEKnightEspionage"))hooks++;if(call.name.equals("createPickpocketItem"))nativeThefts++;if(call.name.equals("isEntityWatching"))witnesses++;}
        }
        assertEquals(3,hooks);assertEquals(1,nativeThefts);assertTrue(witnesses>=2);assertFalse(node.interfaces.contains("lotr/common/quest/IPickpocketable"));
        assertSame(original,t.transform(name,"net.minecraft.world.World",original));
    }
}
