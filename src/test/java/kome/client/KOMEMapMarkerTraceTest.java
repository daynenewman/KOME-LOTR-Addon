package kome.client;

import java.io.InputStream;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.Assert.*;

public class KOMEMapMarkerTraceTest {
    @Test public void traceIsOptInAndCapturesOnlyTheSameIdentityAcrossOneNativeZoom() {
        assertFalse(KOMEMapMarkerTrace.ENABLED);
        KOMEMapMarkerTrace.Capture capture=new KOMEMapMarkerTrace.Capture();
        capture.observe(.25,0,-2,-2);
        assertFalse(capture.wants(KOMEMapMarkerTrace.Kind.COMPANY_SHIELD,"company-A",.25));
        capture.observe(.36,3,-2,-1);
        assertTrue(capture.wants(KOMEMapMarkerTrace.Kind.COMPANY_SHIELD,"company-A",.36));
        capture.accept(observation(capture,KOMEMapMarkerTrace.Kind.COMPANY_SHIELD,"company-A",.36));
        assertFalse("Another shield may not replace the selected identity",
            capture.wants(KOMEMapMarkerTrace.Kind.COMPANY_SHIELD,"company-B",.37));
        capture.observe(.374,2,-2,-1);
        capture.accept(observation(capture,KOMEMapMarkerTrace.Kind.COMPANY_SHIELD,"company-A",.374));
        capture.accept(observation(capture,KOMEMapMarkerTrace.Kind.NORMAL_BANNER,"normal-1",.374));
        capture.accept(observation(capture,KOMEMapMarkerTrace.Kind.DEFENSIVE_CASTLE,"fort-1",.374));
        assertEquals(.374,capture.chosen[0][0].zoom,0);
        capture.observe(.41,1,-2,-1);
        assertFalse(capture.wants(KOMEMapMarkerTrace.Kind.COMPANY_SHIELD,"company-A",.41));
        capture.observe(.5,0,-2,-1);
        assertTrue(capture.ready());
        assertFalse(capture.wants(KOMEMapMarkerTrace.Kind.COMPANY_SHIELD,"company-B",.5));
        for(KOMEMapMarkerTrace.Kind kind:KOMEMapMarkerTrace.Kind.values()) {
            String id=capture.selectedIds[kind.ordinal()];
            capture.accept(observation(capture,kind,id,.5));
            assertEquals(id,capture.chosen[0][kind.ordinal()].id);
            assertEquals(id,capture.chosen[1][kind.ordinal()].id);
            assertFalse(capture.wants(kind,id,.5));
        }
        capture.finish();
        capture.observe(.375,3,-2,-1);
        assertFalse(capture.wants(KOMEMapMarkerTrace.Kind.COMPANY_SHIELD,"company-A",.375));
    }

    @Test public void transientWindowAndUpdatesAreBoundedWithoutZoomStopGuessing() {
        KOMEMapMarkerTrace.Capture capture=new KOMEMapMarkerTrace.Capture();
        capture.observe(.349,3,-2,-1);assertEquals(KOMEMapMarkerTrace.Phase.NONE,capture.phase);
        capture.observe(.401,3,-2,-1);assertEquals(KOMEMapMarkerTrace.Phase.NONE,capture.phase);
        capture.observe(.375,3,-1,-2);assertEquals(KOMEMapMarkerTrace.Phase.NONE,capture.phase);
        for(int i=0;i<100;i++) {
            double zoom=.35+i*.00025;
            capture.observe(zoom,3,-2,-1);
            capture.accept(observation(capture,KOMEMapMarkerTrace.Kind.COMPANY_SHIELD,"same",zoom));
        }
        assertTrue(capture.animatedUpdates[0]<=KOMEMapMarkerTrace.Capture.MAX_UPDATES_PER_KIND);
        capture.observe(.5,0,-2,-1);
        assertTrue(capture.ready());
    }

    @Test public void framebufferProjectionDetectsAdditionalModelviewScale() {
        float[] model=identity(),projection=identity();
        projection[0]=2F/400;projection[5]=-2F/300;projection[12]=-1;projection[13]=1;
        int[] viewport={0,0,800,600};
        double[] ordinary=KOMEMapMarkerTrace.framebufferBounds(10,20,9,6,model,projection,viewport);
        assertEquals(18,ordinary[2]-ordinary[0],.001);
        assertEquals(12,ordinary[3]-ordinary[1],.001);
        model[0]=model[5]=2;
        double[] doubled=KOMEMapMarkerTrace.framebufferBounds(10,20,9,6,model,projection,viewport);
        assertEquals(36,doubled[2]-doubled[0],.001);
        assertEquals(24,doubled[3]-doubled[1],.001);
        model=identity();model[0]=model[5]=0;model[1]=1;model[4]=-1;
        double[] rotated=KOMEMapMarkerTrace.framebufferBounds(10,20,9,6,model,projection,viewport);
        assertEquals(12,rotated[2]-rotated[0],.001);
        assertEquals(18,rotated[3]-rotated[1],.001);
    }

    @Test public void productionSamplesAtBoundTextureSubmissionBeforeFourVertices()throws Exception {
        MethodNode draw=method("drawTexturedMarker","FDDDD)V");
        int bind=-1,sample=-1,firstVertex=-1,pop=-1,index=0;
        for(AbstractInsnNode step:draw.instructions.toArray()) {
            if(step instanceof MethodInsnNode) {
                MethodInsnNode call=(MethodInsnNode)step;
                if(call.name.equals("bind"))bind=index;
                if(call.owner.equals("kome/client/KOMEMapMarkerTrace")&&call.name.equals("submitted"))sample=index;
                if(call.name.equals("addVertexWithUV")&&firstVertex<0)firstVertex=index;
                if(call.name.equals("glPopAttrib"))pop=index;
            }
            index++;
        }
        assertTrue(bind>=0&&sample>bind&&firstVertex>sample&&pop>firstVertex);
        assertTrue(calls(method("onMapDrawStart",null),"beginFrame")>0);
        assertTrue(calls(method("onBeforeNativeMapLabels",null),"observe")>0);
        assertTrue(calls(method("onDrawMap",null),"observe")>0);
        assertTrue(calls(method("onMapProfileEnd",null),"endFrame")>0);
        assertTrue(calls(method("drawLiveUnitMarkers",null),"renderCompanyMarkers")>0);
        assertTrue(callsAcrossPrefix("lambda$drawLiveUnitMarkers$","drawTexturedMarker")>0);
        assertTrue(callsAcrossPrefix("lambda$drawLiveUnitMarkers$","drawCompanyCluster")>0);
        assertTrue(calls(method("drawBuildMarker",null),"drawTexturedMarker")>0);
    }

    private static KOMEMapMarkerTrace.Observation observation(KOMEMapMarkerTrace.Capture capture,
            KOMEMapMarkerTrace.Kind kind,String id,double zoom) {
        return new KOMEMapMarkerTrace.Observation(capture.phase,kind,id,zoom,"");
    }
    private static float[] identity(){float[] m=new float[16];m[0]=m[5]=m[10]=m[15]=1;return m;}
    private static MethodNode method(String name,String descriptorSuffix)throws Exception {
        ClassNode node=new ClassNode();
        try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){
            new ClassReader(in).accept(node,0);
        }
        for(MethodNode method:node.methods)if(method.name.equals(name)
                &&(descriptorSuffix==null||method.desc.endsWith(descriptorSuffix)))return method;
        throw new AssertionError("Missing method "+name+" "+descriptorSuffix);
    }
    private static int callsAcrossPrefix(String prefix,String name)throws Exception {
        ClassNode node=new ClassNode();
        try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){
            new ClassReader(in).accept(node,0);
        }
        int count=0;
        for(MethodNode method:node.methods)if(method.name.startsWith(prefix))count+=calls(method,name);
        return count;
    }
    private static int calls(MethodNode method,String name) {
        int count=0;
        for(AbstractInsnNode step:method.instructions.toArray())if(step instanceof MethodInsnNode
                &&((MethodInsnNode)step).name.equals(name))count++;
        return count;
    }
}
