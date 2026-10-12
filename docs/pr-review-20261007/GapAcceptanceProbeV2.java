import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.sun.tools.attach.VirtualMachine;
/** Protected unclassified gap fixture; never changes raster or exclusion metadata. */
public final class GapAcceptanceProbeV2 {
 static final Path ROOT=Paths.get("C:/Users/dayne/.codex/worktrees/686a/KOME-LOTR-Addon/build/pr-review-20261007/reload-fix-runtime");static ClassLoader cl;
 static Class<?> t(String s)throws Exception{return cl.loadClass(s);}
 static Object call(Object o,String n,Object...a)throws Exception{return MountedReloadAcceptanceProbe.call(o,n,a);}
 static Object f(Object o,String n)throws Exception{return MountedReloadAcceptanceProbe.f(o,n);}
 static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
 static synchronized void log(String s)throws Exception{Files.write(ROOT.resolve("gap-checks.log"),(new Date()+" "+s+"\n").getBytes("UTF-8"),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 static void run(String command)throws Exception{
  Object server=call(t("net.minecraft.server.MinecraftServer"),"func_71276_C"),manager=call(server,"func_71203_ab"),player=call(manager,"func_152612_a","_Danye_");
  require(player!=null&&UUID.fromString("a976a4b7-0614-49c0-b992-61a3a89bd773").equals(call(t("kome.common.KOMEReflection"),"getEntityUUID",player)),"Authenticated owner absent");
  require(((Integer)f(player,"field_71093_bK"))==100&&!(Boolean)call(player,"func_70003_b",2,"season"),"Expected non-operator in Middle-earth");
  if(command.equals("prepare")){
   Object resolver=t("kome.common.data.KOMETileWorldResolver").getField("INSTANCE").get(null),resolved=call(resolver,"resolveWorldPosition",100,34944.5d,640.5d);
   require("IN_BOUNDS_GAP".equals(String.valueOf(f(resolved,"status"))),"Protected control is no longer a gap");
   Object world=call(t("net.minecraftforge.common.DimensionManager"),"getWorld",100),bedrock=t("net.minecraft.init.Blocks").getField("field_150357_h").get(null);
   for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++){
    call(world,"func_147465_d",34944+dx,180,640+dz,bedrock,0,2);call(world,"func_147465_d",34944+dx,185,640+dz,bedrock,0,2);
    if(Math.abs(dx)==5||Math.abs(dz)==5)for(int y=181;y<185;y++)call(world,"func_147465_d",34944+dx,y,640+dz,bedrock,0,2);
   }
   Object connection=f(player,"field_71135_a");call(connection,"func_147364_a",34944.5d,181d,640.5d,180f,0f);
   log("GAP_READY "+resolved+" x=34944.5 y=181 z=640.5 noRasterOrExclusionChange=true");
  }else if(command.equals("query")){
   Object svc=t("kome.common.data.KOMEServerTileAwareness").getField("INSTANCE").get(null),q=call(svc,"current",UUID.fromString("a976a4b7-0614-49c0-b992-61a3a89bd773"));Optional<?> current=(Optional<?>)call(q,"observation");
   log("GAP_CURRENT query="+f(q,"availability")+" location="+(current.isPresent()?f(current.get(),"location"):"absent"));
  }else throw new IllegalArgumentException(command);
 }
 public static void main(String[] a)throws Exception{VirtualMachine vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(a[1],a[2]);}finally{vm.detach();}}
 public static void agentmain(final String command,Instrumentation ins)throws Exception{
  require(Paths.get(System.getProperty("user.dir")).toRealPath().equals(ROOT.resolve("server").toRealPath()),"Wrong disposable server");Class<?> bridge=null;for(Class<?> c:ins.getAllLoadedClasses())if(c.getName().equals("AcceptanceTaskBridge"))bridge=c;require(bridge!=null,"Bridge absent");cl=bridge.getClassLoader();MountedReloadAcceptanceProbe.cl=cl;
  final CountDownLatch done=new CountDownLatch(1);final Throwable[] error=new Throwable[1];bridge.getMethod("enqueueServerTask",Runnable.class).invoke(null,new Runnable(){public void run(){try{require(Thread.currentThread().getName().equals("Server thread"),"Wrong thread");GapAcceptanceProbeV2.run(command);}catch(Throwable e){error[0]=e;try{log("FIXTURE_FAIL "+e);}catch(Exception ignored){}}finally{done.countDown();}}});require(done.await(45,TimeUnit.SECONDS),"Queue timeout");if(error[0]!=null)throw new RuntimeException(error[0]);
 }
}
