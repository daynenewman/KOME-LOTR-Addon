package kome.client;

import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.resources.*;
import net.minecraft.util.ResourceLocation;
import org.junit.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import static org.junit.Assert.*;

/** Actual TextureManager/SimpleTexture/cache loops. Only driver upload/bind/delete, Forge's
 * progress UI and crash-report environment collection are replaced in an isolated test loader. */
public class KOMEMapMarkerTextureRecoveryTest implements Opcodes {
    private EngineLoader loader;
    private Resources resources;
    private final ResourceLocation gondor=new ResourceLocation("lotr:shield/alignment_gondor.png");
    @Before public void setup()throws Exception{Probe.reset();loader=new EngineLoader();resources=new Resources();}

    @Test public void nativeIOExceptionInstallsStickyMissingObjectAfterRepair()throws Exception {
        Object manager=manager();call(manager,"bindTexture",gondor);Object original=call(manager,"getTexture",gondor);assertNotNull(original);
        resources.broken.put(gondor,null);call(manager,"onResourceManagerReload",resources);
        Object missing=loader.loadClass("net.minecraft.client.renderer.texture.TextureUtil").getField("missingTexture").get(null);
        assertSame(missing,call(manager,"getTexture",gondor));resources.broken.clear();
        call(manager,"onResourceManagerReload",resources);call(manager,"bindTexture",gondor);
        assertSame("Native DynamicTexture.loadTexture does not reread the repaired path",missing,call(manager,"getTexture",gondor));
    }

    @Test public void nativeNullImageAbortsReloadBeforeLaterKomeListener()throws Exception {
        Object manager=manager();call(manager,"bindTexture",gondor);resources.broken.put(gondor,new byte[]{1,2,3});int later=0;
        try{call(manager,"onResourceManagerReload",resources);later++;fail("Null image must reproduce native reload failure");}
        catch(InvocationTargetException failure){assertEquals("net.minecraft.util.ReportedException",failure.getCause().getClass().getName());assertTrue(failure.getCause().getCause() instanceof NullPointerException);}
        assertEquals(0,later);
    }

    @Test public void ownedIOExceptionFallsBackAndRepairsPreviouslyBoundTexture()throws Exception{ownedRecovery(null);}
    @Test public void ownedNullImageNeverAbortsReloadAndRepairsPreviouslyBoundTexture()throws Exception{ownedRecovery(new byte[]{1,2,3});}
    private void ownedRecovery(byte[] broken)throws Exception {
        Object sprite=sprite(),owned=owned();List<Object> sprites=Collections.singletonList(sprite);
        call(owned,"reload",resources,sprites);assertEquals(true,call(owned,"ready",sprite));call(owned,"bind",gondor);
        resources.broken.put(gondor,broken);call(owned,"reload",resources,sprites);
        assertEquals("Failed owned registrations must not remain drawable as missing textures",false,call(owned,"ready",sprite));
        resources.broken.put(gondor,hd());call(owned,"reload",resources,sprites);assertEquals(true,call(owned,"ready",sprite));call(owned,"bind",gondor);
        assertEquals(128,Probe.width);assertEquals(.5,number(sprite,"u1"),0);call(owned,"close");
    }

    @Test public void validPackSwitchAndHdUploadDoNotReplaceGlobalNativeObjects()throws Exception {
        Object global=manager();ResourceLocation rohan=new ResourceLocation("lotr:shield/alignment_rohan.png");call(global,"bindTexture",gondor);call(global,"bindTexture",rohan);
        Object oldGondor=call(global,"getTexture",gondor),oldRohan=call(global,"getTexture",rohan);Object owned=owned(),sprite=sprite();
        call(owned,"reload",resources,Collections.singletonList(sprite));call(owned,"bind",gondor);int first=Probe.bound;
        resources.broken.put(gondor,hd());call(owned,"reload",resources,Collections.singletonList(sprite));call(owned,"bind",gondor);
        assertEquals(128,Probe.width);assertNotEquals(first,Probe.bound);assertSame(oldGondor,call(global,"getTexture",gondor));assertSame(oldRohan,call(global,"getTexture",rohan));
        call(owned,"close");
    }

    @Test public void repeatedOwnedReloadsAndFailuresReleaseEveryOwnedTexture()throws Exception {
        Object owned=owned(),sprite=sprite();List<Object> sprites=Collections.singletonList(sprite);
        for(int i=0;i<40;i++) {
            if(i%3==0)resources.broken.put(gondor,new byte[]{1,2,3});else resources.broken.clear();
            call(owned,"reload",resources,sprites);if(Boolean.TRUE.equals(call(owned,"ready",sprite)))call(owned,"bind",gondor);
            assertTrue("Only one marker plus the engine's shared missing sentinel may remain",Probe.live.size()<=2);
        }
        call(owned,"close");assertEquals(1,Probe.live.size()); // Engine sentinel, never owned/deleted by KOME.
    }

    @Test public void markerReloadRecoversEvenWhileGlobalNativeEntryRemainsMissing()throws Exception {
        Object global=manager();call(global,"bindTexture",gondor);Object owned=owned(),sprite=sprite();
        Class<?> art=loader.loadClass("kome.client.KOMEMapMarkerArt");Field field=art.getDeclaredField("textures");field.setAccessible(true);field.set(null,owned);
        staticCall(art,"reload",resources);assertSame(sprite,staticCall(art,"displayed",sprite));assertEquals(true,staticCall(art,"bind",gondor));
        resources.broken.put(gondor,null);call(global,"onResourceManagerReload",resources);staticCall(art,"reload",resources);
        Object fallback=staticCall(art,"displayed",sprite);assertEquals(new ResourceLocation("lotr:shield/achievement_bronze.png"),location(fallback));assertEquals(true,staticCall(art,"bind",location(fallback)));
        resources.broken.put(gondor,hd());call(global,"onResourceManagerReload",resources);staticCall(art,"reload",resources);
        Object missing=loader.loadClass("net.minecraft.client.renderer.texture.TextureUtil").getField("missingTexture").get(null);
        assertSame(missing,call(global,"getTexture",gondor));assertSame(sprite,staticCall(art,"displayed",sprite));assertEquals(true,staticCall(art,"bind",gondor));assertEquals(128,Probe.width);
        int reads=resources.reads;for(int i=0;i<1000;i++)assertEquals(true,staticCall(art,"bind",gondor));assertEquals("Prepared marker binds perform no image IO",reads,resources.reads);
        assertEquals(resources.opened,resources.closed);call(owned,"close");assertEquals(1,Probe.live.size());
    }

    @Test public void uploadFollowedByStreamCloseFailureReleasesTheNewId()throws Exception {
        Object owned=owned(),sprite=sprite();resources.failClose=true;
        call(owned,"reload",resources,Collections.singletonList(sprite));assertEquals(false,call(owned,"ready",sprite));assertEquals(0,Probe.live.size());
        resources.failClose=false;call(owned,"reload",resources,Collections.singletonList(sprite));assertEquals(true,call(owned,"ready",sprite));
        call(owned,"close");assertEquals(0,Probe.live.size());assertEquals(resources.opened,resources.closed);
    }

    @Test public void ownedUploadAndRenderingHaveExceptionSafeGlScopes()throws Exception {
        ClassNode node=new ClassNode();try(InputStream in=getClass().getResourceAsStream("/kome/client/KOMEMapMarkerTextures$Driver.class")){new ClassReader(in).accept(node,0);}
        boolean protectedUpload=false;
        for(MethodNode m:node.methods)if(m.name.equals("upload")) {
            Set<String> calls=new HashSet<>();for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode)calls.add(((MethodInsnNode)step).name);
            assertTrue(calls.contains("glPushAttrib"));assertTrue(calls.contains("glPopAttrib"));assertTrue(calls.contains("deleteTexture"));assertFalse(m.tryCatchBlocks.isEmpty());protectedUpload=true;
        }
        assertTrue(protectedUpload);
    }

    @Test public void engineReloadPrecedesModInitializationAndRuntimeFailureRemovesSelectedPacks()throws Exception {
        ClassNode minecraft=bytecode("net/minecraft/client/Minecraft"),forge=bytecode("cpw/mods/fml/client/FMLClientHandler");
        boolean ordered=false,packRemoval=false,initializes=false;
        for(MethodNode m:minecraft.methods) {
            int engine=-1,finish=-1,index=0;
            for(AbstractInsnNode step:m.instructions.toArray()) {
                if(step instanceof TypeInsnNode&&((TypeInsnNode)step).desc.equals("net/minecraft/client/renderer/texture/TextureManager"))engine=index;
                if(step instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)step;if(c.name.equals("finishMinecraftLoading"))finish=index;if(m.name.equals("refreshResources")&&c.name.equals("func_148527_a"))packRemoval=true;}
                index++;
            }
            if(engine>=0&&finish>engine)ordered=true;
        }
        for(MethodNode m:forge.methods)if(m.name.equals("finishMinecraftLoading"))for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode&&((MethodInsnNode)step).name.equals("initializeMods"))initializes=true;
        assertTrue(ordered);assertTrue(initializes);assertTrue(packRemoval);
    }

    private ClassNode bytecode(String name)throws IOException{ClassNode node=new ClassNode();try(InputStream in=getClass().getResourceAsStream("/"+name+".class")){assertNotNull(name,in);new ClassReader(in).accept(node,0);}return node;}

    @Test public void productionSpriteBindingUsesOwnedBoundaryRatherThanGlobalNativeRegistration()throws Exception {
        ClassNode node=new ClassNode();try(InputStream in=KOMEConquestMapOverlay.class.getResourceAsStream("KOMEConquestMapOverlay.class")){new ClassReader(in).accept(node,0);}
        boolean isolated=false;
        for(MethodNode m:node.methods)if(m.name.equals("drawTexturedMarker"))for(AbstractInsnNode step:m.instructions.toArray())if(step instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)step;if(c.owner.equals("kome/client/KOMEMapMarkerArt")&&c.name.equals("bind"))isolated=true;}
        assertTrue("Native faction art must not register SimpleTexture in the shared manager",isolated);
    }

    private Object manager()throws Exception{return loader.loadClass("net.minecraft.client.renderer.texture.TextureManager").getConstructor(IResourceManager.class).newInstance(resources);}
    private Object sprite()throws Exception{Class<?> art=loader.loadClass("kome.client.KOMEMapMarkerArt");Method m=art.getDeclaredMethod("shield",String.class);m.setAccessible(true);return m.invoke(null,"gondor");}
    private Object owned()throws Exception {
        Class<?> gpu=loader.loadClass("kome.client.KOMEMapMarkerTextures$Gpu");Object driver=Proxy.newProxyInstance(loader,new Class[]{gpu},(p,m,args)->{
            if(m.getName().equals("upload"))return Probe.upload(Probe.gen(),(BufferedImage)args[0]);if(m.getName().equals("delete")){Probe.delete((Integer)args[0]);return null;}return null;});
        Constructor<?> c=loader.loadClass("kome.client.KOMEMapMarkerTextures").getDeclaredConstructor(gpu);c.setAccessible(true);return c.newInstance(driver);
    }
    private static Object call(Object object,String name,Object... args)throws Exception{for(Method m:object.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(object,args);}throw new NoSuchMethodException(name);}
    private static Object staticCall(Class<?> type,String name,Object... args)throws Exception{for(Method m:type.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){m.setAccessible(true);return m.invoke(null,args);}throw new NoSuchMethodException(name);}
    private static ResourceLocation location(Object sprite)throws Exception{Field f=sprite.getClass().getDeclaredField("texture");f.setAccessible(true);return (ResourceLocation)f.get(sprite);}
    private static double number(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return ((Number)f.get(o)).doubleValue();}
    private static byte[] hd()throws Exception {BufferedImage original;try(InputStream in=KOMEMapMarkerTextureRecoveryTest.class.getResourceAsStream("/assets/lotr/shield/alignment_gondor.png")){original=ImageIO.read(in);}BufferedImage hd=new BufferedImage(128,64,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<128;x++)hd.setRGB(x,y,original.getRGB(x/2,y/2));ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(hd,"png",out);return out.toByteArray();}
    public static final class Probe {
        static final Set<Integer> live=new HashSet<>();static final Map<Integer,Integer> widths=new HashMap<>();static int next,bound,width;
        static void reset(){live.clear();widths.clear();next=bound=width=0;}
        public static int gen(){live.add(++next);return next;}
        public static void delete(int id){live.remove(id);widths.remove(id);}
        public static void bind(int id){bound=id;width=widths.getOrDefault(id,0);}
        public static int upload(int id,BufferedImage image){width=image.getWidth();widths.put(id,width);return id;}
    }
    private static final class Resources implements IResourceManager {
        final Map<ResourceLocation,byte[]> broken=new HashMap<>();
        int reads,opened,closed;boolean failClose;
        public Set getResourceDomains(){return new HashSet<>(Arrays.asList("lotr","kome"));}
        public List getAllResources(ResourceLocation r)throws IOException{return Collections.singletonList(getResource(r));}
        public IResource getResource(ResourceLocation r)throws IOException {
            reads++;
            InputStream stream;if(broken.containsKey(r)){byte[] bytes=broken.get(r);if(bytes==null)throw new IOException("Injected missing replacement");stream=new ByteArrayInputStream(bytes);}else stream=getClass().getResourceAsStream("/assets/"+r.getResourceDomain()+"/"+r.getResourcePath());
            if(stream==null)throw new FileNotFoundException(r.toString());opened++;
            final InputStream in=new FilterInputStream(stream){boolean done;@Override public void close()throws IOException{if(!done){done=true;closed++;super.close();if(failClose)throw new IOException("Injected close failure");}}};
            return (IResource)Proxy.newProxyInstance(IResource.class.getClassLoader(),new Class[]{IResource.class},(p,m,a)->m.getName().equals("getInputStream")?in:m.getName().equals("hasMetadata")?false:null);
        }
    }

    private static final class EngineLoader extends ClassLoader {
        EngineLoader(){super(KOMEMapMarkerTextureRecoveryTest.class.getClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            boolean child=name.startsWith("kome.client.KOMEMapMarkerArt")||name.startsWith("kome.client.KOMEMapMarkerTextures")||name.startsWith("net.minecraft.crash.CrashReport")||name.equals("net.minecraft.util.ReportedException")||name.startsWith("cpw.mods.fml.common.ProgressManager");
            for(String type:new String[]{"TextureManager","TextureUtil","SimpleTexture","AbstractTexture","DynamicTexture"})if(name.startsWith("net.minecraft.client.renderer.texture."+type))child=true;
            if(!child)return super.loadClass(name,resolve);
            Class<?> c=findLoadedClass(name);if(c==null)try {
                byte[] bytes;
                if(name.startsWith("cpw.mods.fml.common.ProgressManager"))bytes=progress(name);
                else try(InputStream in=getParent().getResourceAsStream(name.replace('.','/')+".class")) {
                    if(in==null)throw new ClassNotFoundException(name);ClassNode node=new ClassNode();new ClassReader(in).accept(node,0);
                    if(name.endsWith("TextureUtil"))patchTextureUtil(node);
                    if(name.equals("net.minecraft.crash.CrashReport"))for(MethodNode m:node.methods)if(m.name.equals("populateEnvironment")){empty(m);m.instructions.add(new InsnNode(RETURN));}
                    ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);bytes=writer.toByteArray();
                }
                c=defineClass(name,bytes,0,bytes.length);
            }catch(IOException e){throw new ClassNotFoundException(name,e);}
            if(resolve)resolveClass(c);return c;
        }
        private static void empty(MethodNode m){m.instructions.clear();m.tryCatchBlocks.clear();m.localVariables=null;}
        private static void patchTextureUtil(ClassNode node) {
            String probe=org.objectweb.asm.Type.getInternalName(Probe.class),self=node.name;
            for(MethodNode m:node.methods) {
                if(m.name.equals("<clinit>")){empty(m);m.instructions.add(new TypeInsnNode(NEW,"net/minecraft/client/renderer/texture/DynamicTexture"));m.instructions.add(new InsnNode(DUP));m.instructions.add(new IntInsnNode(BIPUSH,16));m.instructions.add(new IntInsnNode(BIPUSH,16));m.instructions.add(new MethodInsnNode(INVOKESPECIAL,"net/minecraft/client/renderer/texture/DynamicTexture","<init>","(II)V",false));m.instructions.add(new FieldInsnNode(PUTSTATIC,self,"missingTexture","Lnet/minecraft/client/renderer/texture/DynamicTexture;"));m.instructions.add(new InsnNode(RETURN));}
                else if(m.name.equals("glGenTextures")){empty(m);m.instructions.add(new MethodInsnNode(INVOKESTATIC,probe,"gen","()I",false));m.instructions.add(new InsnNode(IRETURN));}
                else if(m.name.equals("bindTexture")||m.name.equals("deleteTexture")){empty(m);m.instructions.add(new VarInsnNode(ILOAD,0));m.instructions.add(new MethodInsnNode(INVOKESTATIC,probe,m.name.equals("bindTexture")?"bind":"delete","(I)V",false));m.instructions.add(new InsnNode(RETURN));}
                else if(m.name.equals("allocateTexture")){empty(m);m.instructions.add(new InsnNode(RETURN));}
                else if(m.name.equals("uploadTextureImageSub")){empty(m);m.instructions.add(new VarInsnNode(ILOAD,0));m.instructions.add(new VarInsnNode(ALOAD,1));m.instructions.add(new MethodInsnNode(INVOKESTATIC,probe,"upload","(ILjava/awt/image/BufferedImage;)I",false));m.instructions.add(new InsnNode(IRETURN));}
            }
            // uploadTextureImageAllocate remains ACTUAL engine bytecode, including its null dereference.
        }
        private static byte[] progress(String name) {
            String owner=name.replace('.','/'),bar="cpw/mods/fml/common/ProgressManager$ProgressBar";ClassWriter w=new ClassWriter(0);w.visit(V1_8,ACC_PUBLIC,owner,null,"java/lang/Object",null);
            MethodVisitor m=w.visitMethod(ACC_PUBLIC,"<init>","()V",null,null);m.visitCode();m.visitVarInsn(ALOAD,0);m.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);m.visitInsn(RETURN);m.visitMaxs(1,1);m.visitEnd();
            if(name.endsWith("$ProgressBar")){m=w.visitMethod(ACC_PUBLIC,"step","(Ljava/lang/String;)V",null,null);m.visitCode();m.visitInsn(RETURN);m.visitMaxs(0,2);m.visitEnd();}
            else {m=w.visitMethod(ACC_PUBLIC|ACC_STATIC,"push","(Ljava/lang/String;IZ)L"+bar+";",null,null);m.visitCode();m.visitTypeInsn(NEW,bar);m.visitInsn(DUP);m.visitMethodInsn(INVOKESPECIAL,bar,"<init>","()V",false);m.visitInsn(ARETURN);m.visitMaxs(2,3);m.visitEnd();m=w.visitMethod(ACC_PUBLIC|ACC_STATIC,"pop","(L"+bar+";)V",null,null);m.visitCode();m.visitInsn(RETURN);m.visitMaxs(0,1);m.visitEnd();}
            w.visitEnd();return w.toByteArray();
        }
    }
}
