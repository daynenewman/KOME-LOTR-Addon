package kome.client;

import java.io.*;
import cpw.mods.fml.common.eventhandler.*;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

/** Verify layer/depth ordering without a GL context or brittle pixel assertions. */
public class KOMECompanyDestinationPanelLayerTest {
    @Test public void selectorIsDrawnAfterStrategicGeometryAndIgnoresMarkerDepth() throws Exception {
        assertEquals(EventPriority.NORMAL,KOMEConquestMapOverlay.class.getMethod("onDrawMap",GuiScreenEvent.DrawScreenEvent.Post.class)
            .getAnnotation(SubscribeEvent.class).priority());
        assertEquals(EventPriority.LOWEST,KOMEConquestMapOverlay.class.getMethod("onDrawCompanySelector",GuiScreenEvent.DrawScreenEvent.Post.class)
            .getAnnotation(SubscribeEvent.class).priority());
        ClassNode node=new ClassNode();
        try(InputStream stream=KOMEConquestMapOverlay.class.getResourceAsStream("/kome/client/KOMEConquestMapOverlay.class")) {
            new ClassReader(stream).accept(node,0);
        }
        MethodNode foreground=null,geometry=null;
        for(MethodNode method:node.methods) {
            if(method.name.equals("onDrawCompanySelector"))foreground=method;
            if(method.name.equals("onDrawMap"))geometry=method;
        }
        assertNotNull(foreground);assertNotNull(geometry);
        int disable=-1,mask=-1,draw=-1,restore=-1;
        for(AbstractInsnNode n=foreground.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)n;int index=foreground.instructions.indexOf(n);
            if(call.owner.equals("org/lwjgl/opengl/GL11")&&call.name.equals("glDisable")) {
                assertEquals(2929,((IntInsnNode)previous(n)).operand);disable=index;
            }
            if(call.name.equals("glDepthMask")) {assertEquals(Opcodes.ICONST_0,previous(n).getOpcode());mask=index;}
            if(call.owner.equals("kome/client/KOMECompanyDestinationPanel")&&call.name.equals("draw"))draw=index;
            if(call.name.equals("glPopAttrib"))restore=index;
        }
        assertTrue(disable>=0&&mask>disable&&draw>mask&&restore>draw);
        boolean markers=false,route=false;
        for(AbstractInsnNode n=geometry.instructions.getFirst();n!=null;n=n.getNext())if(n instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)n;
            assertFalse("Panel is not drawn before the final layer",call.owner.equals("kome/client/KOMECompanyDestinationPanel"));
            if(call.name.equals("drawTroopMarkers"))markers=true;
            if(call.name.equals("drawCompanyDestinationPreview"))route=true;
        }
        assertTrue(markers);assertTrue(route);
    }
    private static AbstractInsnNode previous(AbstractInsnNode n) {do{n=n.getPrevious();}while(n!=null&&n.getOpcode()<0);return n;}
}
