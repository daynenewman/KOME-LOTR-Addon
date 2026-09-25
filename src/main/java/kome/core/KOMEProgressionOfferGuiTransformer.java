package kome.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Separates passive GUI close from LOTR's explicit miniquest rejection for KOME offers. */
public final class KOMEProgressionOfferGuiTransformer implements IClassTransformer {
    static final String TARGET="lotr.client.gui.LOTRGuiMiniquestOffer", OWNER="kome/client/KOMEProgressionOfferClientBridge";
    public byte[] transform(String name,String transformedName,byte[] bytes){if(bytes==null||!TARGET.equals(transformedName))return bytes;ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);for(MethodNode method:node.methods)if("onGuiClosed".equals(method.name)&&"()V".equals(method.desc)){InsnList i=new InsnList();LabelNode vanilla=new LabelNode();i.add(new VarInsnNode(Opcodes.ALOAD,0));i.add(new MethodInsnNode(Opcodes.INVOKESTATIC,OWNER,"preservePassiveClose","(Ljava/lang/Object;)Z",false));i.add(new JumpInsnNode(Opcodes.IFEQ,vanilla));i.add(new InsnNode(Opcodes.RETURN));i.add(vanilla);method.instructions.insert(i);}ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();}
}
