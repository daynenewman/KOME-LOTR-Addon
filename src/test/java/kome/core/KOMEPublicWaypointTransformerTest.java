package kome.core;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

public class KOMEPublicWaypointTransformerTest {
    static final String[] TARGETS={"lotr.common.LOTRPlayerData","lotr.common.network.LOTRPacketFastTravel$Handler","lotr.client.gui.LOTRGuiMap","lotr.common.network.LOTRPacketFTBounceServer$Handler"};
    static byte[] read(InputStream in)throws Exception { ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] b=new byte[8192]; int n; while((n=in.read(b))!=-1)out.write(b,0,n); return out.toByteArray(); }
    @Test public void buildClassesInstallAllHooksExactlyOnceAfterExistingGuards()throws Exception{
        for(String name:TARGETS) try(InputStream in=getClass().getResourceAsStream("/"+name.replace('.','/')+".class")) { assertNotNull(in); verify(name,read(in)); }
    }
    @Test public void originalProductionAndBuildJarsHaveExactCompatibleHooks()throws Exception{
        String configured=System.getenv("KOME_LOTR_VALIDATION_DIRECTORY");
        Path base=configured==null?Paths.get("../LOTR-Test-Server/mods"):Paths.get(configured);
        if(configured==null) org.junit.Assume.assumeTrue("Original LOTR validation jars unavailable; supply KOME_LOTR_VALIDATION_DIRECTORY",
            Files.isRegularFile(base.resolve("LOTRMod v36.15.jar")) && Files.isRegularFile(base.resolve("LOTRMod v36.15.jar.original")));
        for(String file:new String[]{"LOTRMod v36.15.jar","LOTRMod v36.15.jar.original"}) {
            assertTrue("Explicit required compatibility input is absent: "+base.resolve(file),Files.isRegularFile(base.resolve(file)));
            try(ZipFile jar=new ZipFile(base.resolve(file).toFile())) {
                for(String name:TARGETS) try(InputStream in=jar.getInputStream(jar.getEntry(name.replace('.','/')+".class"))) { verify(name,read(in)); }
            }
        }
    }
    static void verify(String name,byte[] original)throws Exception{
        byte[] guarded=new KOMEWaypointTransformer().transform(name,name,original);
        KOMEPublicWaypointTransformer t=new KOMEPublicWaypointTransformer(); byte[] transformed=t.transform(name,name,guarded);
        assertFalse(Arrays.equals(guarded,transformed)); assertArrayEquals(transformed,t.transform(name,name,transformed));
        ClassNode c=new ClassNode(); new ClassReader(transformed).accept(c,0);
        int expected=name.equals(TARGETS[1]) || name.equals(TARGETS[3])?1:2,count=0;
        for(MethodNode m:c.methods) for(AbstractInsnNode n=m.instructions.getFirst();n!=null;n=n.getNext()) if(n instanceof MethodInsnNode) {
            MethodInsnNode call=(MethodInsnNode)n;
            if(call.owner.equals(KOMEPublicWaypointTransformer.BRIDGE) || call.owner.equals(KOMEPublicWaypointTransformer.CLIENT)) count++;
        }
        // Lookup has namespace predicate plus lookup hook.
        if(name.equals(TARGETS[0])) expected=3;
        assertEquals(expected,count);
        for(MethodNode m:c.methods) if(m.name.equals("getAllAvailableWaypoints") || m.name.equals("getSharedCustomWaypointByID")
                || m.name.equals("renderMapWidgets") || m.name.equals("isWaypointVisible")
                || m.name.equals("onMessage") && m.desc.contains("MessageContext;"))
            new org.objectweb.asm.tree.analysis.Analyzer(new org.objectweb.asm.tree.analysis.BasicVerifier()).analyze(c.name,m);
        if(name.equals(TARGETS[1])) {
            MethodNode request=null; for(MethodNode m:c.methods) if(m.name.equals("onMessage") && m.desc.contains("LOTRPacketFastTravel;")) request=m;
            AbstractInsnNode first=request.instructions.getFirst(); while(first.getOpcode()<0)first=first.getNext();
            assertEquals(Opcodes.ALOAD,first.getOpcode()); assertEquals(1,((VarInsnNode)first).var);
            assertEquals("handleRequest",((MethodInsnNode)first.getNext().getNext()).name);
            assertEquals(Opcodes.IFEQ,first.getNext().getNext().getNext().getOpcode());
            assertEquals(Opcodes.ARETURN,first.getNext().getNext().getNext().getNext().getNext().getOpcode());
        }
    }
    @Test public void exactMapControlFieldsHaveVerifiedTypes()throws Exception {
        Class<?> map=Class.forName("lotr.client.gui.LOTRGuiMap",false,getClass().getClassLoader());
        assertEquals(lotr.common.world.map.LOTRAbstractWaypoint.class,map.getDeclaredField("selectedWaypoint").getType());
        for(String name:new String[]{"widgetDelCWP","widgetRenameCWP","widgetShareCWP","widgetHideSWP","widgetUnhideSWP"}) {
            Class<?> widget=map.getDeclaredField(name).getType(); assertEquals("lotr.client.gui.LOTRGuiMapWidget",widget.getName());
            assertEquals(boolean.class,widget.getDeclaredField("visible").getType());
        }
        assertTrue(java.lang.reflect.Modifier.isStatic(map.getDeclaredField("mapWidgets").getModifiers()));
    }
    @Test public void startupDefinesLazyTargetsAndStillRejectsAbsentHooks()throws Exception {
        List<String> loaded=new ArrayList<String>();
        ClassLoader loader=new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(name.equals(TARGETS[0]) || name.equals(TARGETS[1]) || name.equals(TARGETS[3])) loaded.add(name);
                return super.loadClass(name,resolve);
            }
        };
        java.lang.reflect.Field flag=KOMEPublicWaypointTransformer.class.getDeclaredField("playerInstalled");
        flag.setAccessible(true); boolean old=flag.getBoolean(null); flag.setBoolean(null,false);
        try {
            try { KOMEPublicWaypointTransformer.requireInstalled(loader); fail("Startup accepted absent player hook"); }
            catch(IllegalStateException expected) { assertTrue(expected.getMessage().contains("player=false")); }
            assertEquals(Arrays.asList(TARGETS[0],TARGETS[1],TARGETS[3]),loaded);
        } finally { flag.setBoolean(null,old); }
    }
    @Test(expected=IllegalStateException.class) public void incompletePlayerFingerprintFailsClosed(){
        ClassWriter w=new ClassWriter(0); w.visit(Opcodes.V1_6,Opcodes.ACC_PUBLIC,TARGETS[0].replace('.','/'),null,"java/lang/Object",null); w.visitEnd();
        new KOMEPublicWaypointTransformer().transform(TARGETS[0],TARGETS[0],w.toByteArray());
    }
    @Test(expected=IllegalStateException.class) public void changedNativeMapFingerprintFailsClosed()throws Exception{
        ClassNode c=new ClassNode(); try(InputStream in=KOMEPublicWaypointTransformerTest.class.getResourceAsStream("/lotr/client/gui/LOTRGuiMap.class")) { new ClassReader(read(in)).accept(c,0); }
        c.fields.removeIf(f->f.name.equals("widgetHideSWP")); ClassWriter w=new ClassWriter(0); c.accept(w);
        new KOMEPublicWaypointTransformer().transform(TARGETS[2],TARGETS[2],w.toByteArray());
    }
}
