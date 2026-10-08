package kome.core;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEAlignmentSyncTransformerTest {
    @Test public void actualNativeLookupUsesEffectiveSideAndRetainsNativeSynchronization()throws Exception {
        byte[] nativeBytes=KOMEVisualLocationTransformerTest.readResource("/lotr/common/LOTRPlayerData.class");
        KOMEAlignmentSyncTransformer transformer=new KOMEAlignmentSyncTransformer();
        byte[] bytes=transformer.transform("lotr.common.LOTRPlayerData","lotr.common.LOTRPlayerData",nativeBytes);
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int effective=0,sends=0;
        for(MethodNode method:node.methods)for(AbstractInsnNode insn=method.instructions.getFirst();insn!=null;insn=insn.getNext())if(insn instanceof MethodInsnNode){
            MethodInsnNode call=(MethodInsnNode)insn;
            if(method.name.equals("getPlayer")&&call.name.equals("getEffectiveSide"))effective++;
            assertFalse(method.name.equals("getPlayer")&&call.owner.equals("lotr/common/LOTRCommonProxy")&&call.name.equals("isClient"));
            if(method.name.equals("setAlignment")&&call.name.equals("sendAlignmentToAllPlayersInWorld"))sends++;
        }
        assertEquals(1,effective);assertEquals(1,sends);assertArrayEquals(bytes,transformer.transform("lotr.common.LOTRPlayerData","lotr.common.LOTRPlayerData",bytes));
    }
}
