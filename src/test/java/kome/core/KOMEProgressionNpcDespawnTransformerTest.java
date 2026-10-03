package kome.core;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import java.util.jar.JarFile;
import static org.junit.Assert.*;

public class KOMEProgressionNpcDespawnTransformerTest {
    @Test public void lotrNativeDespawnDecisionReceivesOneRoleVeto() throws Exception {
        String name="lotr/common/entity/npc/LOTREntityNPC.class";
        InputStream in=getClass().getClassLoader().getResourceAsStream(name);assertNotNull(in);
        byte[] original=read(in),transformed=new KOMEProgressionNpcDespawnTransformer().transform("lotr.common.entity.npc.LOTREntityNPC","lotr.common.entity.npc.LOTREntityNPC",original);
        ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);int hooks=0;
        for(MethodNode method:node.methods)if(("canDespawn".equals(method.name)||"func_70692_ba".equals(method.name))&&"()Z".equals(method.desc))
            for(AbstractInsnNode instruction=method.instructions.getFirst();instruction!=null;instruction=instruction.getNext())
                if(instruction instanceof MethodInsnNode&&"kome/common/data/KOMEProgressionNpcRoles".equals(((MethodInsnNode)instruction).owner))hooks++;
        assertEquals(1,hooks);
        assertArrayEquals(transformed,new KOMEProgressionNpcDespawnTransformer().transform("","lotr.common.entity.npc.LOTREntityNPC",transformed));
    }
    @Test public void shippedLotrJarSrgMethodReceivesVeto() throws Exception {
        JarFile jar=new JarFile("libs/LOTRMod v36.15.jar");
        try {
            byte[] original=read(jar.getInputStream(jar.getJarEntry("lotr/common/entity/npc/LOTREntityNPC.class")));
            byte[] transformed=new KOMEProgressionNpcDespawnTransformer().transform("lotr.common.entity.npc.LOTREntityNPC","lotr.common.entity.npc.LOTREntityNPC",original);
            ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);
            int hooks=0;
            for(MethodNode method:node.methods)if("func_70692_ba".equals(method.name)&&"()Z".equals(method.desc))
                for(AbstractInsnNode instruction=method.instructions.getFirst();instruction!=null;instruction=instruction.getNext())
                    if(instruction instanceof MethodInsnNode&&"kome/common/data/KOMEProgressionNpcRoles".equals(((MethodInsnNode)instruction).owner))hooks++;
            assertEquals(1,hooks);
        } finally { jar.close(); }
    }
    private static byte[] read(InputStream in) throws Exception {try{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];for(int n;(n=in.read(buffer))!=-1;)out.write(buffer,0,n);return out.toByteArray();}finally{in.close();}}
}
