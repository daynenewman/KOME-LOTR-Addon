import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import com.sun.tools.attach.VirtualMachine;

/** Disposable authenticated packet check and server-thread serialized authority snapshots. */
public final class BuildRejectionProbe {
 static final Path ROOT=Paths.get("C:/Users/dayne/.codex/worktrees/686a/KOME-LOTR-Addon/build/tile-acceptance-20261006");
 static final Path PROFILE=Paths.get("C:/Users/dayne/AppData/Roaming/PrismLauncher/instances/kome-tile-acceptance-pr26-20261006/minecraft");
 static final UUID PLAYER=UUID.fromString("a976a4b7-0614-49c0-b992-61a3a89bd773");
 static ClassLoader cl;
 static Class<?> type(String n)throws Exception{return cl.loadClass(n);}
 static Object hidden(Object o,String n)throws Exception{Class<?> c=o.getClass();while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
 static void require(boolean b,String s){if(!b)throw new IllegalStateException(s);}
 static synchronized void log(String s)throws Exception{Files.write(ROOT.resolve("build-rejection.log"),(new Date()+" "+s+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 static void snapshot(String label)throws Exception{
  require(Thread.currentThread().getName().equals("Server thread"),"Wrong server thread");
  Object server=type("net.minecraft.server.MinecraftServer").getMethod("func_71276_C").invoke(null);
  Object manager=server.getClass().getMethod("func_71203_ab").invoke(server);
  Object player=manager.getClass().getMethod("func_152612_a",String.class).invoke(manager,"_Danye_");
  require(player!=null,"Player absent");
  Object uuid=type("kome.common.KOMEReflection").getMethod("getEntityUUID",type("net.minecraft.entity.Entity")).invoke(null,player);
  require(PLAYER.equals(uuid),"Wrong player");
  boolean admin=(Boolean)player.getClass().getMethod("func_70003_b",int.class,String.class).invoke(player,2,"build");
  require(!admin,"Test must remain non-operator");
  Object world=type("net.minecraftforge.common.DimensionManager").getMethod("getWorld",int.class).invoke(null,100);
  require(world!=null,"Middle-earth not loaded");
  Object data=type("kome.common.data.KOMEWorldData").getMethod("get",type("net.minecraft.world.World")).invoke(null,world);
  Class<?> tag=type("net.minecraft.nbt.NBTTagCompound");Object nbt=tag.newInstance();
  data.getClass().getMethod("func_76187_b",tag).invoke(data,nbt);
  Method compressed=null;
  for(Method m:type("net.minecraft.nbt.CompressedStreamTools").getMethods())if(Modifier.isStatic(m.getModifiers())&&m.getReturnType()==void.class&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{tag,OutputStream.class})){require(compressed==null,"Ambiguous compressed writer");compressed=m;}
  require(compressed!=null,"Compressed writer absent");
  try(OutputStream out=Files.newOutputStream(ROOT.resolve("build-authority-"+label+".nbt"))){compressed.invoke(null,nbt,out);}
  log("SNAPSHOT label="+label+" thread="+Thread.currentThread().getName()+" player="+uuid+" admin="+admin+" dimension="+hidden(player,"field_71093_bK")+" x="+hidden(player,"field_70165_t")+" y="+hidden(player,"field_70163_u")+" z="+hidden(player,"field_70161_v")+" serializer=KOMEWorldData.func_76187_b writer="+compressed.getName());
 }
 static void send()throws Exception{
  Object mc=type("net.minecraft.client.Minecraft").getMethod("func_71410_x").invoke(null);
  Object player=hidden(mc,"field_71439_g");require(player!=null,"Player absent");
  Object uuid=type("kome.common.KOMEReflection").getMethod("getEntityUUID",type("net.minecraft.entity.Entity")).invoke(null,player);require(PLAYER.equals(uuid),"Wrong player");
  int dim=(Integer)hidden(player,"field_71093_bK");require(dim==100,"Wrong dimension");
  double x=(Double)hidden(player,"field_70165_t"),y=(Double)hidden(player,"field_70163_u"),z=(Double)hidden(player,"field_70161_v");
  Object location=type("kome.common.data.KOMETileWorldResolver").getField("INSTANCE").get(null);
  Object resolved=location.getClass().getMethod("resolveWorldPosition",int.class,double.class,double.class).invoke(location,dim,x,z);
  require(!"T091".equals(resolved.getClass().getField("tileId").get(resolved)),"Coordinates must mismatch selected tile");
  Object packet=type("kome.common.network.KOMEPacketBuildAction").getConstructor(String.class,String.class,String.class,String.class,String.class,String.class,String.class,long.class,int.class,double.class,double.class,double.class).newInstance("create","T091","","","Acceptance invalid coordinates","DALE","NORMAL",1L,dim,x,y,z);
  Object network=type("kome.common.network.KOMEPacketHandler").getField("network").get(null);
  network.getClass().getMethod("sendToServer",type("cpw.mods.fml.common.network.simpleimpl.IMessage")).invoke(network,packet);
  Object screen=hidden(mc,"field_71462_r");
  log("SENT authenticated production Build packet action=create selectedTile=T091 populationFaction=DALE buildType=NORMAL centiHours=1 actual="+resolved+" player="+uuid+" thread="+Thread.currentThread().getName()+" gui="+(screen==null?"none":screen.getClass().getName()));
 }
 public static void main(String[] a)throws Exception{VirtualMachine vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(a[1],a[2]);}finally{vm.detach();}}
 public static void agentmain(final String command,Instrumentation ins)throws Exception{
  require(command.equals("send")||command.equals("before")||command.equals("after"),"Unknown command");
  Object target;Method queue;
  if(command.equals("send")){
   Class<?> mc=null;for(Class<?> c:ins.getAllLoadedClasses())if(c.getName().equals("net.minecraft.client.Minecraft"))mc=c;require(mc!=null,"Client absent");cl=mc.getClassLoader();Object client=mc.getMethod("func_71410_x").invoke(null);
   require(((File)mc.getField("field_71412_D").get(client)).toPath().toRealPath().equals(PROFILE.toRealPath()),"Wrong disposable profile");
   target=type("kome.common.KOMEAddon").getField("proxy").get(null);queue=target.getClass().getMethod("enqueueClientTask",Runnable.class);
  }else{
   require(Paths.get(System.getProperty("user.dir")).toRealPath().equals(ROOT.resolve("server").toRealPath()),"Wrong disposable server");
   Class<?> bridge=null;for(Class<?> c:ins.getAllLoadedClasses())if(c.getName().equals("AcceptanceTaskBridge"))bridge=c;require(bridge!=null,"Bridge absent");cl=bridge.getClassLoader();target=null;queue=bridge.getMethod("enqueueServerTask",Runnable.class);
  }
  final CountDownLatch done=new CountDownLatch(1);final Throwable[] error=new Throwable[1];
  queue.invoke(target,new Runnable(){public void run(){try{if(command.equals("send"))send();else snapshot(command);}catch(Throwable t){error[0]=t;try{log("FAIL command="+command+" error="+t);}catch(Exception ignored){}}finally{done.countDown();}}});
  require(done.await(20,TimeUnit.SECONDS),"Queue timeout");if(error[0]!=null)throw new RuntimeException(error[0]);
 }
}
