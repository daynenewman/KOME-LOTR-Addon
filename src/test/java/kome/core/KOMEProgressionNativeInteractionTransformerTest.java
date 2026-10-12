package kome.core;
import java.io.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;
public class KOMEProgressionNativeInteractionTransformerTest {
    @Test public void actualNativeNameAndDismissalHooksAreScopedAndIdempotent()throws Exception{
        KOMEProgressionNativeInteractionTransformer t=new KOMEProgressionNativeInteractionTransformer();
        for(String target:new String[]{"lotr.common.entity.npc.LOTREntityNPC","lotr.common.entity.npc.LOTRHiredNPCInfo"}){
            InputStream in=getClass().getResourceAsStream("/"+target.replace('.','/')+".class");assertNotNull(in);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);in.close();
            byte[] transformed=t.transform(target,target,out.toByteArray());ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);int hooks=0;
            for(MethodNode m:node.methods)for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;
                if(call.owner.startsWith("kome/common/data/")){hooks++;assertTrue(m.name.equals("getNPCFormattedName")||m.name.equals("dismissUnit"));}
            }
            assertEquals(target.endsWith("NPC")?1:2,hooks);assertArrayEquals(transformed,t.transform(target,target,transformed));assertArrayEquals(transformed,t.transform("other","other",transformed));
        }
    }
    @Test public void actualNativePouchHasBothNearAndFarLayeredRenderAdapters()throws Exception{
        String target="lotr.client.render.entity.LOTRNPCRendering";org.objectweb.asm.ClassReader reader=new org.objectweb.asm.ClassReader(target);byte[] transformed=new KOMEVisualLocationTransformer().transform(target,target,read(reader));
        ClassNode node=new ClassNode();new ClassReader(transformed).accept(node,0);boolean near=false,far=false;
        for(MethodNode m:node.methods)if(m.name.equals("kome$renderRelationshipIcon"))for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;near|=call.name.equals("renderRelationshipItemIn2D");far|=call.name.equals("renderRelationshipIcon");}
        assertTrue(near);assertTrue(far);
    }
    private byte[] read(ClassReader reader){ClassWriter writer=new ClassWriter(0);reader.accept(writer,0);return writer.toByteArray();}
}
