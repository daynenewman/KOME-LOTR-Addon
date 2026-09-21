package kome.core;

import static org.junit.Assert.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Verifies both the dev classpath shape and the shipped v36.15 jar shape fail closed. */
public class KOMEProgressionOfferTransformerTest {
    @Test public void devShapeReceivesEachNarrowOfferHook() throws Exception {
        InputStream in=getClass().getResourceAsStream("/lotr/common/entity/npc/LOTREntityQuestInfo.class");
        assertNotNull(in); assertHooks(new KOMEProgressionOfferTransformer().transform("lotr.common.entity.npc.LOTREntityQuestInfo",KOMEProgressionOfferTransformer.TARGET,read(in)));
    }
    @Test public void rawV3615JarReceivesEachNarrowOfferHook() throws Exception {
        File jar=new File("libs/LOTRMod v36.15.jar");assertTrue(jar.isFile());ZipFile zip=new ZipFile(jar);try{assertHooks(new KOMEProgressionOfferTransformer().transform("lotr.common.entity.npc.LOTREntityQuestInfo",KOMEProgressionOfferTransformer.TARGET,read(zip.getInputStream(zip.getEntry("lotr/common/entity/npc/LOTREntityQuestInfo.class")))));}finally{zip.close();}
    }
    private static void assertHooks(byte[] bytes) { ClassNode n=new ClassNode();new ClassReader(bytes).accept(n,0);int count=0;for(MethodNode m:n.methods)for(AbstractInsnNode i=m.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode&&KOMEProgressionOfferTransformer.OWNER.equals(((MethodInsnNode)i).owner))count++;assertEquals(3,count); }
    private static byte[] read(InputStream in)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))>=0;)out.write(b,0,n);in.close();return out.toByteArray();}
}
