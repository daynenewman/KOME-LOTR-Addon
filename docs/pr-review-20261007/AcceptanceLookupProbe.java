import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import com.sun.tools.attach.VirtualMachine;
public final class AcceptanceLookupProbe {
 static final Path ROOT=Paths.get("C:/Users/dayne/.codex/worktrees/686a/KOME-LOTR-Addon/build/pr-review-20261007");
 static ClassLoader cl;
 static final List<Object> fixtures=new ArrayList<Object>();
 static final UUID owner=UUID.fromString("be657063-0057-4060-8063-000000000001");
 static Object world, server, ticket;
 static Object field(Object o,String n)throws Exception{return o.getClass().getField(n).get(o);}
 static Object hidden(Object o,String n)throws Exception {Class<?> c=o.getClass();while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
 static Object call(Object o,String n)throws Exception{return o.getClass().getMethod(n).invoke(o);}
 static Class<?> type(String n)throws Exception{return cl.loadClass(n);}
 static void log(String s)throws Exception {Files.write(ROOT.resolve("native-checks.log"),(new Date()+" "+s+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 static void require(boolean v,String s){if(!v)throw new IllegalStateException(s);}
 static Object awareness()throws Exception{return type("kome.common.data.KOMEServerTileAwareness").getField("INSTANCE").get(null);}
 static UUID id(Object e)throws Exception{return (UUID)type("kome.common.KOMEReflection").getMethod("getEntityUUID",type("net.minecraft.entity.Entity")).invoke(null,e);}
 static void queries()throws Exception {
   Object svc=awareness();
   for(Object e:fixtures){Object q=svc.getClass().getMethod("current",UUID.class).invoke(svc,id(e));Optional<?> o=(Optional<?>)call(q,"observation");
     log("FIXTURE_QUERY uuid="+id(e)+" active="+field(field(e,"hiredNPCInfo"),"isActive")+" dead="+hidden(e,"field_70128_L")+" availability="+field(q,"availability")+" location="+(o.isPresent()?field(o.get(),"location"):"absent")+" incarnation="+(o.isPresent()?field(o.get(),"incarnation"):"absent"));
   }
 }
 static void metrics(String label)throws Exception {
   long[] times=(long[])hidden(server,"field_71311_j");long[] sorted=times.clone();Arrays.sort(sorted);
   long sum=0;for(long v:sorted)sum+=v;
   log("METRIC label="+label+" tick="+call(awareness(),"currentTick")+" tracked="+call(awareness(),"trackedCount")+" lookups="+call(awareness(),"resolutionCount")+" samples="+sorted.length+" meanMs="+(sum/(double)sorted.length/1e6)+" p95Ms="+(sorted[94]/1e6)+" maxMs="+(sorted[99]/1e6)+" heapUsedBytes="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())+" maxHeapBytes="+Runtime.getRuntime().maxMemory());
 }
 static Object spawn(int index)throws Exception {
   Class<?> wc=type("net.minecraft.world.World"),entity=type("net.minecraft.entity.Entity"),ep=type("net.minecraft.entity.player.EntityPlayer");
   Object npc=type("lotr.common.entity.npc.LOTREntityAngmarHillman").getConstructor(wc).newInstance(world);
   entity.getMethod("func_70012_b",double.class,double.class,double.class,float.class,float.class).invoke(npc,237248.5+index%4,181d,87294.5+index/4,0f,0f);
   npc.getClass().getMethod("func_110161_a",type("net.minecraft.entity.IEntityLivingData")).invoke(npc,new Object[]{null});
   Object profile=type("com.mojang.authlib.GameProfile").getConstructor(UUID.class,String.class).newInstance(owner,"TileFixture");
   Object fake=type("net.minecraftforge.common.util.FakePlayerFactory").getMethod("get",type("net.minecraft.world.WorldServer"),type("com.mojang.authlib.GameProfile")).invoke(null,world,profile);
   Object info=field(npc,"hiredNPCInfo");info.getClass().getMethod("setHiringPlayer",ep).invoke(info,fake);
   info.getClass().getField("isActive").setBoolean(info,true);
   Class<?> task=type("lotr.common.entity.npc.LOTRHiredNPCInfo$Task");info.getClass().getMethod("setTask",task).invoke(info,task.getField("WARRIOR").get(null));
   require((Boolean)wc.getMethod("func_72838_d",entity).invoke(world,npc),"Native spawn rejected");
   fixtures.add(npc);log("FIXTURE_SPAWN uuid="+id(npc)+" owner="+owner+" nativeOwnerApi=true realPlayerHireTransaction=false");
   return npc;
 }
 public static void main(String[] a)throws Exception{VirtualMachine vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(a[1],a[2]);}finally{vm.detach();}}
 public static void agentmain(final String command,Instrumentation ins)throws Exception {
   require(Paths.get(System.getProperty("user.dir")).toRealPath().equals(ROOT.resolve("server").toRealPath()),"Wrong disposable server");
   Class<?> queue=null;for(Class<?> c:ins.getAllLoadedClasses())if(c.getName().equals("AcceptanceTaskBridge"))queue=c;
   require(queue!=null,"Production queue absent");cl=queue.getClassLoader();
   final CountDownLatch done=new CountDownLatch(1);final Throwable[] error=new Throwable[1];
   queue.getMethod("enqueueServerTask",Runnable.class).invoke(null,new Runnable(){public void run(){try {
    require(Thread.currentThread().getName().equals("Server thread"),"Wrong thread");
    server=type("net.minecraft.server.MinecraftServer").getMethod("func_71276_C").invoke(null);
    Object me=type("lotr.common.LOTRDimension").getField("MIDDLE_EARTH").get(null);int dim=(Integer)field(me,"dimensionID");
    world=type("net.minecraftforge.common.DimensionManager").getMethod("getWorld",int.class).invoke(null,dim);
    if(command.equals("build-setup")) {
      Object manager=call(server,"func_71203_ab");Object player=manager.getClass().getMethod("func_152612_a",String.class).invoke(manager,"_Danye_");
      require(player!=null&&id(player).equals(UUID.fromString("a976a4b7-0614-49c0-b992-61a3a89bd773")),"Wrong test player");
      require(!((Boolean)player.getClass().getMethod("func_70003_b",int.class,String.class).invoke(player,2,"build")),"Test player is operator");
      Object data=type("kome.common.data.KOMEWorldData").getMethod("get",type("net.minecraft.world.World")).invoke(null,world);
      Object tile=((Map)field(data,"conquestTiles")).get("T401");String controlling=(String)call(tile,"projectRulingFaction");
      require(controlling.equals("gondor"),"T401 is not Gondor: "+controlling);
      Object nativeData=type("lotr.common.LOTRLevelData").getMethod("getData",type("net.minecraft.entity.player.EntityPlayer")).invoke(null,player);
      Object previous=call(nativeData,"getPledgeFaction");Object faction=type("lotr.common.fac.LOTRFaction").getField("GONDOR").get(null);
      nativeData.getClass().getMethod("setPledgeFaction",type("lotr.common.fac.LOTRFaction")).invoke(nativeData,faction);
      log("BUILD_SETUP disposableCharacterOnly=true previousPledge="+previous+" newPledge="+call(nativeData,"getPledgeFaction")+" tile=T401 controller="+controlling+" operator=false builds="+((Map)field(data,"builds")).size());
    } else if(command.equals("controls")) {
      Object resolver=type("kome.common.data.KOMETileWorldResolver").getField("INSTANCE").get(null);
      double[][] points={{dim,237248.5,87295.5},{dim,237248.5,87296.5},{dim,34944.5,640.5},{dim,189696,-86016},{dim,-10000000,-10000000},{0,237248.5,87295.5}};
      String[] expected={"RESOLVED","RESOLVED","IN_BOUNDS_GAP","RESOLVED","OUTSIDE_MASK","UNSUPPORTED_DIMENSION"};
      String[] tiles={"T401","T442","","T001","",""};
      for(int i=0;i<points.length;i++) {
       Object r=resolver.getClass().getMethod("resolveWorldPosition",int.class,double.class,double.class).invoke(resolver,(int)points[i][0],points[i][1],points[i][2]);
       log("CONTROL "+r);require(field(r,"status").toString().equals(expected[i]),"Unexpected state "+r);
       if(!tiles[i].isEmpty())require(field(r,"tileId").equals(tiles[i]),"Unexpected tile "+r);
      }
      log("PASS resolver_controls dimension="+dim+" geometryDiagnostic="+call(resolver,"loadDiagnostic"));
    } else if(command.equals("lookup-benchmark")) {
      Object resolver=type("kome.common.data.KOMETileWorldResolver").getField("INSTANCE").get(null);
      Method resolve=resolver.getClass().getMethod("resolveWorldPosition",int.class,double.class,double.class);
      for(int i=0;i<10000;i++)resolve.invoke(resolver,dim,237248.5,87295.5);
      long heapBefore=Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory();
      for(int run=0;run<5;run++) {
        long start=System.nanoTime();Object last=null;
        for(int i=0;i<100000;i++)last=resolve.invoke(resolver,dim,237248.5,(i&1)==0?87295.5:87296.5);
        long elapsed=System.nanoTime()-start;require(field(last,"tileId").equals("T442"),"Lookup result wrong");
        log("LOOKUP_BENCHMARK run="+run+" warmup=10000 count=100000 elapsedNs="+elapsed+" nsPerCall="+(elapsed/100000d)+" reflectionAndResultAllocationIncluded=true singleServerThread=true");
      }
      log("LOOKUP_HEAP beforeBytes="+heapBefore+" afterBytes="+(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())+" gcNotForced=true retainedSnapshotBytesNotIsolated=true");
    } else if(command.equals("pin")) {
      Object modLoader=type("cpw.mods.fml.common.Loader").getMethod("instance").invoke(null);
      Map<?,?> mods=(Map<?,?>)call(modLoader,"getIndexedModList");Object mod=call(mods.get("tileacceptanceprobe"),"getMod");
      Class<?> manager=type("net.minecraftforge.common.ForgeChunkManager"),tt=type("net.minecraftforge.common.ForgeChunkManager$Type"),tc=type("net.minecraftforge.common.ForgeChunkManager$Ticket"),pair=type("net.minecraft.world.ChunkCoordIntPair");
      Class<?> callbackType=type("net.minecraftforge.common.ForgeChunkManager$LoadingCallback"); Object callback=Proxy.newProxyInstance(cl,new Class<?>[]{callbackType},new InvocationHandler(){public Object invoke(Object p,Method m,Object[] a){return null;}}); manager.getMethod("setForcedChunkLoadingCallback",Object.class,callbackType).invoke(null,mod,callback); ticket=manager.getMethod("requestTicket",Object.class,type("net.minecraft.world.World"),tt).invoke(null,mod,world,tt.getField("NORMAL").get(null));require(ticket!=null,"Chunk ticket unavailable");
      for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++) {
        int x=(237248>>4)+dx,z=(87296>>4)+dz;
        type("net.minecraft.world.World").getMethod("func_72964_e",int.class,int.class).invoke(world,x,z);
        manager.getMethod("forceChunk",tc,pair).invoke(null,ticket,pair.getConstructor(int.class,int.class).newInstance(x,z));
      }
      log("PINNED disposable-native-workload chunks=25");
    } else if(command.equals("unpin")) {
      if(ticket!=null)type("net.minecraftforge.common.ForgeChunkManager").getMethod("releaseTicket",type("net.minecraftforge.common.ForgeChunkManager$Ticket")).invoke(null,ticket);
      log("UNPINNED");    } else if(command.equals("platform")) {
      require(world!=null,"Middle-earth unavailable");Class<?> wc=type("net.minecraft.world.World"),block=type("net.minecraft.block.Block");
      Object stone=type("net.minecraft.init.Blocks").getField("field_150357_h").get(null);
      Method set=wc.getMethod("func_147465_d",int.class,int.class,int.class,block,int.class,int.class);
      for(int x=237244;x<=237255;x++)for(int z=87290;z<=87302;z++)set.invoke(world,x,180,z,stone,0,2);
      log("PLATFORM disposable-world-only x=237244..237255 y=180 z=87290..87302 boundaryZ=87296");
    } else if(command.startsWith("spawn:")) {
      require(world!=null,"Middle-earth unavailable");int n=Integer.parseInt(command.substring(6));require(n>=1&&n<=20,"Fixture limit");
      for(int i=0;i<n;i++)spawn(i);log("PASS native_fixture_spawn count="+n);
    } else if(command.equals("move")) {
      require(!fixtures.isEmpty(),"No fixture");Object e=fixtures.get(0);
      type("net.minecraft.entity.Entity").getMethod("func_70107_b",double.class,double.class,double.class).invoke(e,237248.5,181d,87296.5);
      log("FIXTURE_MOVE uuid="+id(e)+" requested=T442 x=237248.5 z=87296.5");queries();
    } else if(command.equals("dismiss")) {
      Object e=fixtures.get(0);field(e,"hiredNPCInfo").getClass().getField("isActive").setBoolean(field(e,"hiredNPCInfo"),false);log("FIXTURE_DISMISS uuid="+id(e)+" nativeActive=false");
    } else if(command.equals("kill")) {
      Object e=fixtures.get(fixtures.size()-1);type("net.minecraft.entity.Entity").getMethod("func_70106_y").invoke(e);log("FIXTURE_KILL uuid="+id(e));
    } else if(command.equals("cleanup")) {
      for(Object e:fixtures)type("net.minecraft.entity.Entity").getMethod("func_70106_y").invoke(e);log("FIXTURE_CLEANUP count="+fixtures.size());
    } else if(command.equals("adopt")) {
      require(fixtures.isEmpty(),"Fixtures already adopted");
      for(Object e:new ArrayList<Object>((List<Object>)hidden(world,"field_72996_f"))) {
        if(!type("lotr.common.entity.npc.LOTREntityNPC").isInstance(e))continue;
        Object info=field(e,"hiredNPCInfo");
        if(owner.equals(call(info,"getHiringPlayerUUID"))) {fixtures.add(e);log("ADOPT_RELOADED uuid="+id(e)+" active="+field(info,"isActive"));}
      }
      require(fixtures.size()>=18,"Insufficient reloaded native fixtures");queries();log("PASS cold_reload_native_fixtures count="+fixtures.size());    } else if(command.equals("query")) {queries();
    } else if(command.startsWith("metrics:")) {metrics(command.substring(8));
    } else if(command.equals("player")) {
      Object manager=call(server,"func_71203_ab");Object player=manager.getClass().getMethod("func_152612_a",String.class).invoke(manager,"_Danye_");
      require(player!=null,"Player not connected");
      log("PLAYER uuid="+id(player)+" dimension="+hidden(player,"field_71093_bK")+" x="+hidden(player,"field_70165_t")+" y="+hidden(player,"field_70163_u")+" z="+hidden(player,"field_70161_v"));
    } else if(command.equals("stage-player")) {
      Object manager=call(server,"func_71203_ab");Object player=manager.getClass().getMethod("func_152612_a",String.class).invoke(manager,"_Danye_");
      require(player!=null,"Player not connected");
      require(((Integer)hidden(player,"field_71093_bK")).intValue()==dim,"Player must already be in Middle-earth");
      Object connection=hidden(player,"field_71135_a");
      connection.getClass().getMethod("func_147364_a",double.class,double.class,double.class,float.class,float.class).invoke(connection,237248.5,181d,87295.5,0f,0f);
      log("STAGED_PLAYER uuid="+id(player)+" nonOperatorExpected=true boundary=T401/T442");
    } else throw new IllegalArgumentException(command);
   }catch(Throwable t){error[0]=t;try{log("FAIL command="+command+" error="+t);}catch(Exception ignored){}t.printStackTrace();}finally{done.countDown();}}});
   require(done.await(45,TimeUnit.SECONDS),"Queue timed out");if(error[0]!=null)throw new RuntimeException(error[0]);
 }
}







