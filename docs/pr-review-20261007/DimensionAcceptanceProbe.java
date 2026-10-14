import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import com.sun.tools.attach.VirtualMachine;

/** Disposable-world dimension fixture using the existing direct waypoint teleporter. */
public final class DimensionAcceptanceProbe {
 static final Path ROOT=Paths.get("C:/Users/dayne/.codex/worktrees/686a/KOME-LOTR-Addon/build/pr-review-20261007/reload-fix-runtime");
 static final UUID OWNER=UUID.fromString("a976a4b7-0614-49c0-b992-61a3a89bd773");
 static ClassLoader cl; static Object subscription;
 static Class<?> t(String n)throws Exception{return cl.loadClass(n);}
 static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
 static Object f(Object o,String n)throws Exception{Class<?> c=o.getClass();while(c!=null){try{Field q=c.getDeclaredField(n);q.setAccessible(true);return q.get(o);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
 static Object call(Object o,String n,Object...args)throws Exception{return MountedReloadAcceptanceProbe.call(o,n,args);}
 static synchronized void log(String s)throws Exception{Files.write(ROOT.resolve("dimension-checks.log"),(new Date()+" "+s+"\n").getBytes("UTF-8"),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 static Object svc()throws Exception{return t("kome.common.data.KOMEServerTileAwareness").getField("INSTANCE").get(null);}
 static void subscribe()throws Exception{
  require(subscription==null,"Observer already active");
  Object listener=Proxy.newProxyInstance(cl,new Class<?>[]{t("kome.common.data.KOMEServerTileAwareness$Listener")},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] a)throws Throwable{
    if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
    if(method.getName().equals("equals"))return proxy==a[0];
    if(method.getName().equals("toString"))return "Disposable dimension observer";
    if(method.getName().equals("onTransition")&&OWNER.equals(f(a[0],"entityId"))){
      Object event=a[0],q=call(svc(),"current",OWNER);Optional<?> current=(Optional<?>)call(q,"observation");Optional<?> after=(Optional<?>)call(event,"current");
      boolean fresh=current.isPresent()&&after.isPresent()&&f(current.get(),"session").equals(f(after.get(),"session"))&&f(current.get(),"incarnation").equals(f(after.get(),"incarnation"))&&f(current.get(),"observedTick").equals(f(after.get(),"observedTick"));
      log("PLAYER_EVENT type="+f(event,"type")+" cause="+f(event,"cause")+" query="+f(q,"availability")+" freshTokens="+fresh+" location="+(current.isPresent()?f(current.get(),"location"):"absent")+" thread="+Thread.currentThread().getName());
    }return null;
  }});
  subscription=call(svc(),"subscribe",listener);log("OBSERVER_INSTALLED owner="+OWNER+" readOnly=true");
 }
 static void run(String command)throws Exception{
  require(Thread.currentThread().getName().equals("Server thread"),"Wrong thread");
  Object server=call(t("net.minecraft.server.MinecraftServer"),"func_71276_C"),manager=call(server,"func_71203_ab"),player=call(manager,"func_152612_a","_Danye_");
  require(player!=null&&OWNER.equals(call(t("kome.common.KOMEReflection"),"getEntityUUID",player)),"Authenticated owner absent");
  require(!(Boolean)call(player,"func_70003_b",2,"season"),"Player must remain non-operator");
  if(command.equals("query")){
    Object q=call(svc(),"current",OWNER);Optional<?> current=(Optional<?>)call(q,"observation");
    log("CURRENT dimension="+f(player,"field_71093_bK")+" x="+f(player,"field_70165_t")+" y="+f(player,"field_70163_u")+" z="+f(player,"field_70161_v")+" query="+f(q,"availability")+" location="+(current.isPresent()?f(current.get(),"location"):"absent"));return;
  }
  if(command.equals("close")){require(subscription!=null,"Observer absent");call(subscription,"close");subscription=null;log("OBSERVER_CLOSED");return;}
  int dim,x,y,z;
  if(command.equals("out")){
    require(((Integer)f(player,"field_71093_bK"))==100,"Expected Middle-earth start");subscribe();dim=0;x=16;y=181;z=16;
  }else if(command.equals("back")){
    require(((Integer)f(player,"field_71093_bK"))==0,"Expected Overworld start");dim=100;x=78017;y=200;z=66242;
  }else throw new IllegalArgumentException(command);
  Object world=call(t("net.minecraftforge.common.DimensionManager"),"getWorld",dim);require(world!=null,"Expected already-loaded target world absent");
  call(world,"func_72964_e",x>>4,z>>4);
  if(dim==0){
    Object bedrock=t("net.minecraft.init.Blocks").getField("field_150357_h").get(null);
    for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++){
      call(world,"func_147465_d",x+dx,y-1,z+dz,bedrock,0,2);
      if(Math.abs(dx)==5||Math.abs(dz)==5)for(int dy=0;dy<4;dy++)call(world,"func_147465_d",x+dx,y+dy,z+dz,bedrock,0,2);
    }
  }
  Class<?> teleporter=t("com.lotrcharactercreation.waypoint.StartingWaypointApplication$DirectWaypointTeleporter");
  Constructor<?> ctor=teleporter.getDeclaredConstructor(t("net.minecraft.world.WorldServer"),int.class,int.class,int.class);ctor.setAccessible(true);
  Object direct=ctor.newInstance(world,x,y,z);
  call(player,"func_70078_a",new Object[]{null});
  call(manager,"transferPlayerToDimension",player,dim,direct);
  require(((Integer)f(player,"field_71093_bK"))==dim&&f(player,"field_70170_p")==world,"Dimension transfer did not complete");
  log("DIMENSION_FIXTURE command="+command+" dimension="+dim+" x="+f(player,"field_70165_t")+" y="+f(player,"field_70163_u")+" z="+f(player,"field_70161_v")+" existingDirectWaypointTeleporter=true noPaidOrPortalTravelAcceptance=true");
 }
 public static void main(String[] a)throws Exception{VirtualMachine vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(a[1],a[2]);}finally{vm.detach();}}
 public static void agentmain(final String command,Instrumentation ins)throws Exception{
  require(Paths.get(System.getProperty("user.dir")).toRealPath().equals(ROOT.resolve("server").toRealPath()),"Wrong owned disposable server");
  Class<?> bridge=null;for(Class<?> c:ins.getAllLoadedClasses())if(c.getName().equals("AcceptanceTaskBridge"))bridge=c;require(bridge!=null,"Bridge absent");cl=bridge.getClassLoader();MountedReloadAcceptanceProbe.cl=cl;
  final CountDownLatch done=new CountDownLatch(1);final Throwable[] error=new Throwable[1];
  bridge.getMethod("enqueueServerTask",Runnable.class).invoke(null,new Runnable(){public void run(){try{DimensionAcceptanceProbe.run(command);}catch(Throwable e){error[0]=e;try{log("FIXTURE_FAIL "+e);}catch(Exception ignored){}}finally{done.countDown();}}});
  require(done.await(45,TimeUnit.SECONDS),"Queue timeout");if(error[0]!=null)throw new RuntimeException(error[0]);
 }
}
