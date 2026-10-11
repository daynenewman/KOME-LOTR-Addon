import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.File;
import java.util.concurrent.*;
import com.sun.tools.attach.VirtualMachine;
public final class ClientPerformanceProbe {
 static final Path ROOT=Paths.get("C:/Users/dayne/.codex/worktrees/686a/KOME-LOTR-Addon/build/pr-review-20261007");
 static final Path PROFILE=Paths.get("C:/Users/dayne/AppData/Roaming/PrismLauncher/instances/kome-review-acceptance-20261007/minecraft");
 static Object hidden(Object o,String n)throws Exception{Class<?> c=o instanceof Class?(Class<?>)o:o.getClass();while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o instanceof Class?null:o);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
 static synchronized void log(String line)throws Exception{Files.write(ROOT.resolve("client-performance.log"),(new java.util.Date()+" "+line+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 public static void main(String[] a)throws Exception{VirtualMachine vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(a[1],a[2]);}finally{vm.detach();}}
 public static void agentmain(final String phase,Instrumentation ins)throws Exception{
   Class<?> mc=null;for(Class<?> c:ins.getAllLoadedClasses())if(c.getName().equals("net.minecraft.client.Minecraft"))mc=c;
   if(mc==null)throw new IllegalStateException("No client");final Class<?> minecraft=mc;
   final Object client=mc.getMethod("func_71410_x").invoke(null);
   if(!((File)hidden(client,"field_71412_D")).toPath().toRealPath().equals(PROFILE.toRealPath()))throw new IllegalStateException("Wrong isolated profile");
   final Object proxy=mc.getClassLoader().loadClass("kome.common.KOMEAddon").getField("proxy").get(null);
   final Method queue=proxy.getClass().getMethod("enqueueClientTask",Runnable.class);
   Thread worker=new Thread(new Runnable(){public void run(){try{
     for(int i=0;i<60;i++){
       final int index=i;final CountDownLatch done=new CountDownLatch(1);
       queue.invoke(proxy,new Runnable(){public void run(){try{
         Object screen=hidden(client,"field_71462_r"),settings=hidden(client,"field_71474_y"),player=hidden(client,"field_71439_g"),world=hidden(client,"field_71441_e");
         log("FPS_SAMPLE phase="+phase+" n="+index+" thread="+Thread.currentThread().getName()+" fps="+hidden(minecraft,"field_71470_ab")+" screen="+(screen==null?"WORLD":screen.getClass().getName())+" width="+hidden(client,"field_71443_c")+" height="+hidden(client,"field_71440_d")+" guiScale="+hidden(settings,"field_74335_Z")+" renderChunks="+hidden(settings,"field_151451_c")+" vsync="+hidden(settings,"field_74352_v")+" fpsLimit="+hidden(settings,"field_74350_i")+" connectedWorld="+(world!=null)+" dimension="+(player==null?"absent":hidden(player,"field_71093_bK"))+" mapZoom="+(screen!=null && screen.getClass().getName().equals("lotr.client.gui.LOTRGuiMap")?hidden(screen,"zoomScale"):"absent")+" playerX="+(player==null?"absent":hidden(player,"field_70165_t"))+" playerZ="+(player==null?"absent":hidden(player,"field_70161_v"))+" heapUsedBytes="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())+" maxHeapBytes="+Runtime.getRuntime().maxMemory());
       }catch(Throwable t){try{log("FAIL "+t);}catch(Exception e){}}finally{done.countDown();}}});
       if(!done.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Client sample timeout");
       if(i<59)Thread.sleep(1000);
     }
     log("CLIENT_SAMPLE_COMPLETE samples=60 readOnly=true");
   }catch(Throwable t){try{log("FAIL "+t);}catch(Exception e){}}}},"Disposable FPS observer");
   worker.setDaemon(true);worker.start();
 }
}


