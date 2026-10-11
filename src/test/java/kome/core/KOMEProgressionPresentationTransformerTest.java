package kome.core;
import java.io.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEProgressionPresentationTransformerTest {
    @Test public void actualMinecraftRendererReceivesOneWorldNameAndTwoModelHooksIdempotently()throws Exception {
        String target="net.minecraft.client.renderer.entity.RendererLivingEntity";InputStream in=getClass().getResourceAsStream("/net/minecraft/client/renderer/entity/RendererLivingEntity.class");assertNotNull(in);ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];for(int n;(n=in.read(b))!=-1;)out.write(b,0,n);in.close();
        KOMEPlayerNameplateTransformer name=new KOMEPlayerNameplateTransformer();KOMEProgressionOutlineTransformer outline=new KOMEProgressionOutlineTransformer();byte[] named=name.transform(target,target,out.toByteArray()),both=outline.transform(target,target,named);
        ClassNode node=new ClassNode();new ClassReader(both).accept(node,0);int names=0,models=0;for(MethodNode method:node.methods)for(AbstractInsnNode i=method.instructions.getFirst();i!=null;i=i.getNext())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;if(call.owner.equals("kome/client/KOMEPlayerNameplate")){names++;assertTrue(method.name.equals("passSpecialRender")||method.name.equals("func_77033_b"));}if(call.owner.equals("kome/client/KOMEProgressionEnemyOutline")){models++;assertTrue(method.name.equals("renderModel")||method.name.equals("func_77036_a"));}}
        assertEquals(1,names);assertEquals(2,models);assertArrayEquals(both,name.transform(target,target,both));assertArrayEquals(both,outline.transform(target,target,both));assertArrayEquals(both,name.transform("other","other",both));
    }
}
