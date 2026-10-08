import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import com.sun.tools.attach.VirtualMachine;

/** Guarded disposable fixture. Not an ordinary recruitment or human acceptance result. */
public final class MountedAcceptanceProbeV10 {
 static final Path ROOT=Paths.get("C:/Users/dayne/.codex/worktrees/686a/KOME-LOTR-Addon/build/pr-review-20261007/reset-runtime");
 static final UUID OWNER=UUID.fromString("a976a4b7-0614-49c0-b992-61a3a89bd773");
 static ClassLoader cl;
 static Class<?> t(String n)throws Exception{return cl.loadClass(n);}
 static void require(boolean v,String s){if(!v)throw new IllegalStateException(s);}
 static Object f(Object o,String n)throws Exception {Class<?> c=o.getClass();while(c!=null){try{Field q=c.getDeclaredField(n);q.setAccessible(true);return q.get(o);}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
 static void set(Object o,String n,Object v)throws Exception {Field q=o.getClass().getField(n);Class<?> k=q.getType();if(v instanceof Number){Number x=(Number)v;if(k==long.class)v=x.longValue();else if(k==int.class)v=x.intValue();else if(k==float.class)v=x.floatValue();else if(k==double.class)v=x.doubleValue();}q.set(o,v);}
 static Class<?> boxed(Class<?> c){if(!c.isPrimitive())return c;if(c==int.class)return Integer.class;if(c==long.class)return Long.class;if(c==double.class)return Double.class;if(c==float.class)return Float.class;if(c==boolean.class)return Boolean.class;return c;}
 static Object call(Object o,String n,Object...args)throws Exception {Class<?> k=o instanceof Class?(Class<?>)o:o.getClass();Method hit=null;for(Method m:k.getMethods()){if(m.isBridge()||m.isSynthetic()||!m.getName().equals(n)||m.getParameterTypes().length!=args.length)continue;boolean ok=true;Class<?>[] p=m.getParameterTypes();for(int i=0;i<p.length;i++)if(args[i]!=null&&!boxed(p[i]).isInstance(args[i]))ok=false;if(ok){require(hit==null,"Ambiguous method "+n);hit=m;}}require(hit!=null,"Missing method "+n);return hit.invoke(o instanceof Class?null:o,args);}
 static synchronized void log(String s)throws Exception {Files.write(ROOT.resolve("mounted-checks.log"),(new Date()+" "+s+"\n").getBytes("UTF-8"),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 static UUID id(Object e)throws Exception{return (UUID)call(t("kome.common.KOMEReflection"),"getEntityUUID",e);}
 static Object cap(Object data,String faction)throws Exception{return call(t("kome.common.data.KOMEFactionCapitalService"),"getCapital",data,faction);}
 static Object anchor(Object old)throws Exception{return t("kome.common.data.KOMEFactionCapitalRecord").getConstructor(String.class,String.class,int.class,double.class,double.class,double.class,long.class,String.class,String.class).newInstance(call(old,"getFactionId"),call(old,"getCapitalTileId"),100,call(old,"getDeploymentX"),200d,call(old,"getDeploymentZ"),System.currentTimeMillis(),"disposable mounted acceptance","system");}
 static void platform(Object world,Object capital)throws Exception {int x=(int)Math.floor((Double)call(capital,"getDeploymentX")),z=(int)Math.floor((Double)call(capital,"getDeploymentZ"));Object bedrock=t("net.minecraft.init.Blocks").getField("field_150357_h").get(null);for(int dx=-8;dx<=8;dx++)for(int dz=-8;dz<=8;dz++){call(world,"func_147465_d",x+dx,199,z+dz,bedrock,0,2);call(world,"func_147465_d",x+dx,206,z+dz,bedrock,0,2);if(Math.abs(dx)==8||Math.abs(dz)==8)for(int y=200;y<206;y++)call(world,"func_147465_d",x+dx,y,z+dz,bedrock,0,2);} Object light=t("net.minecraft.init.Blocks").getField("field_150426_aN").get(null); for(int dx:new int[]{-6,6})for(int dz:new int[]{-6,6})call(world,"func_147465_d",x+dx,199,z+dz,light,0,2);}
 static void teleport(Object player,Object capital)throws Exception{Object c=f(player,"field_71135_a");call(c,"func_147364_a",(Double)call(capital,"getDeploymentX")+1d,200d,(Double)call(capital,"getDeploymentZ")+2d,180f,0f);}
 static Properties fixture()throws Exception{Properties p=new Properties();try(InputStream in=Files.newInputStream(ROOT.resolve("mounted-safe-fixture.properties"))){p.load(in);}return p;}
 static void snapshot(Object data,String name)throws Exception {
  Object tag=t("net.minecraft.nbt.NBTTagCompound").newInstance();call(data,"func_76187_b",tag);
  Method writer=null;for(Method m:t("net.minecraft.nbt.CompressedStreamTools").getMethods())if(Modifier.isStatic(m.getModifiers())&&m.getReturnType()==void.class&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{tag.getClass(),OutputStream.class})){require(writer==null,"Ambiguous NBT writer");writer=m;}
  require(writer!=null,"NBT writer absent");try(OutputStream out=Files.newOutputStream(ROOT.resolve(name+".nbt"))){writer.invoke(null,tag,out);}
 }
 @SuppressWarnings({"unchecked","rawtypes"}) static void run(String command)throws Exception {
  require(Thread.currentThread().getName().equals("Server thread"),"Wrong thread");
  Object server=call(t("net.minecraft.server.MinecraftServer"),"func_71276_C");
  Object world=call(t("net.minecraftforge.common.DimensionManager"),"getWorld",100);if(world==null&&command.equals("cold-inspect")){call(t("net.minecraftforge.common.DimensionManager"),"initDimension",100);world=call(t("net.minecraftforge.common.DimensionManager"),"getWorld",100);}require(world!=null,"Middle-earth absent");
  Object data=call(t("kome.common.data.KOMEWorldData"),"get",world);
  Object manager=call(server,"func_71203_ab"),player=call(manager,"func_152612_a","_Danye_");
  if(command.equals("status")){
   log("STATUS player="+(player==null?"absent":id(player)+"@"+f(player,"field_70165_t")+","+f(player,"field_70163_u")+","+f(player,"field_70161_v"))+" companies="+((Map)f(data,"armyCompanies")).keySet()+" units="+((Map)f(data,"hiredUnits")).keySet());
   for(Object e:(List)f(world,"field_72996_f")){if(id(e).equals(UUID.fromString("869334e0-706c-4d84-96f9-3ddb101a72a4"))||id(e).equals(UUID.fromString("4772c3bf-e6a7-4a2c-bfc3-b7c2f09cf357")))log("OLD_LOADED id="+id(e)+" dead="+f(e,"field_70128_L")+" hp="+call(e,"func_110143_aJ"));}
   for(Object c:((Map)f(data,"armyCompanies")).values())log("COMPANY id="+f(c,"id")+" units="+f(c,"units")+" current="+f(c,"currentTile")+" population="+f(c,"totalPopulation"));
  }else if(command.equals("prepare")){
   require(player!=null&&OWNER.equals(id(player)),"Expected authenticated player absent");
   require(!(Boolean)call(player,"func_70003_b",2,"season"),"Player must remain non-operator");
   require(!((Map)f(data,"armyCompanies")).containsKey("C-mounted-acceptance-safe"),"Safe company already exists");
   require(!Files.exists(ROOT.resolve("mounted-safe-fixture.properties")),"Fixture already prepared");
   Object origin=anchor(cap(data,"rohan")),destination=anchor(cap(data,"gondor"));
   ((Map)f(data,"factionCapitals")).put("rohan",origin);((Map)f(data,"factionCapitals")).put("gondor",destination);
   call(call(world,"func_82736_K"),"func_82764_b","doMobSpawning","false");platform(world,origin);platform(world,destination);
   Object season=f(data,"warSeason");set(season,"phase",Enum.valueOf((Class)f(season,"phase").getClass(),"FINALE"));
   ((Map)f(data,"lastKnownPlayerFactions")).put(OWNER,"gondor");
   Object rider=null,mount=null; for(Object e:(List)f(world,"field_72996_f")){if(t("lotr.common.entity.npc.LOTREntityNPC").isInstance(e)&&"Acceptance safe rider".equals(call(e,"func_94057_bL"))){require(rider==null,"Duplicate partial fixture");rider=e;mount=f(e,"field_70154_o");}} boolean reuse=rider!=null; if(!reuse){rider=call(t("net.minecraft.entity.EntityList"),"func_75620_a","lotr.GondorSoldier",world);mount=call(t("net.minecraft.entity.EntityList"),"func_75620_a","lotr.Horse",world);} if(reuse)log("RECOVERED partial fixture before canonical registration rider="+id(rider)+" mount="+id(mount));
   require(rider!=null&&mount!=null,"Native registry entries absent");
   double x=(Double)call(origin,"getDeploymentX"),z=(Double)call(origin,"getDeploymentZ");
   call(rider,"func_70012_b",x,200d,z,0f,0f);call(mount,"func_70012_b",x,200d,z,0f,0f);
   call(rider,"func_70606_j",3.25f);call(mount,"func_70606_j",7.125f);call(rider,"func_110163_bv");call(rider,"func_94058_c","Acceptance safe rider");
   Object hire=f(rider,"hiredNPCInfo");call(hire,"setHiringPlayer",player);set(hire,"isActive",true);call(hire,"setTask",t("lotr.common.entity.npc.LOTRHiredNPCInfo$Task").getField("WARRIOR").get(null));
   if(!reuse){require((Boolean)call(world,"func_72838_d",mount),"Mount spawn rejected");require((Boolean)call(world,"func_72838_d",rider),"Rider spawn rejected");}call(rider,"func_70078_a",mount);
   Object company=t("kome.common.data.KOMEArmyCompany").newInstance();set(company,"id","C-mounted-acceptance-safe");set(company,"name","Mounted acceptance");set(company,"owner",OWNER);set(company,"faction","gondor");set(company,"nativeFaction","gondor");set(company,"currentTile",call(origin,"getCapitalTileId"));set(company,"totalPopulation",50);set(company,"mountedPopulation",50);((Collection)f(company,"units")).add(id(rider));
   Object unit=t("kome.common.data.KOMEHiredUnitRecord").newInstance();set(unit,"entity",id(rider));set(unit,"owner",OWNER);set(unit,"companyId","C-mounted-acceptance-safe");set(unit,"currentTile",call(origin,"getCapitalTileId"));set(unit,"sourceTileId",call(destination,"getCapitalTileId"));set(unit,"mounted",true);for(String n:new String[]{"cost","baseCost","populationSpent"})set(unit,n,50);for(String n:new String[]{"unitFaction","populationOwningFaction","sourceFaction"})set(unit,n,"gondor");set(unit,"sourceType",t("kome.common.data.KOMEHiredUnitRecord").getField("SOURCE_FACTION_POPULATION_BANK").get(null));Method classify=t("kome.common.data.KOMEHiredUnitClassification").getDeclaredMethod("assignForCampaignWorkflow",t("kome.common.data.KOMEHiredUnitRecord"));classify.setAccessible(true);classify.invoke(null,unit);set(unit,"stationedEntityData",call(t("kome.common.data.KOMEEntitySnapshots"),"snapshot",rider));
   ((Map)f(data,"armyCompanies")).put("C-mounted-acceptance-safe",company);((Map)f(data,"hiredUnits")).put(id(rider),unit);call(data,"func_76185_a");
   Properties p=new Properties();p.setProperty("rider",id(rider).toString());p.setProperty("mount",id(mount).toString());p.setProperty("origin",call(origin,"getCapitalTileId")+" "+x+" 200 "+z);p.setProperty("destination",call(destination,"getCapitalTileId")+" "+call(destination,"getDeploymentX")+" 200 "+call(destination,"getDeploymentZ"));try(OutputStream out=Files.newOutputStream(ROOT.resolve("mounted-safe-fixture.properties"))){p.store(out,"Disposable direct API fixture; no paid recruitment pass");}
   teleport(player,origin);call(call(server,"func_71187_D"),"func_71556_a",server,"save-all");log("PREPARED owner="+OWNER+" origin="+p.getProperty("origin")+" destination="+p.getProperty("destination")+" rider="+id(rider)+" mount="+id(mount)+" expectedHP=3.25/7.125 fixtureNotPaidRecruitment=true");
  }else if(command.equals("destination")){require(player!=null,"Player absent");teleport(player,cap(data,"gondor"));}
  else if((command.equals("inspect")||command.equals("remount")||command.equals("reset")||command.equals("finish")||command.equals("stop-for-restart")||command.equals("cold-inspect"))){
   Properties p=fixture();if(command.equals("cold-inspect")){Object capital=cap(data,"gondor");call(world,"func_72964_e",((int)Math.floor((Double)call(capital,"getDeploymentX")))>>4,((int)Math.floor((Double)call(capital,"getDeploymentZ")))>>4);}UUID riderId=UUID.fromString(p.getProperty("rider")),mountId=UUID.fromString(p.getProperty("mount"));Object rider=null,mount=null;int riders=0,mounts=0;
   for(Object e:(List)f(world,"field_72996_f")){UUID u=id(e);if(u.equals(riderId)){rider=e;riders++;}if(u.equals(mountId)){mount=e;mounts++;}}
   require(riders==1&&mounts==1,"Expected exactly one loaded original rider/mount: "+riders+"/"+mounts);if(command.equals("remount")){
    require((Float)call(rider,"func_110143_aJ")>0f&&(Float)call(mount,"func_110143_aJ")>0f,"Refuse to revive a dead entity");
    Object origin=cap(data,"rohan");double x=(Double)call(origin,"getDeploymentX"),z=(Double)call(origin,"getDeploymentZ");
    call(rider,"func_70012_b",x,200d,z,0f,0f);call(mount,"func_70012_b",x,200d,z,0f,0f);
    call(mount,"setBelongsToNPC",true);call(rider,"func_70078_a",mount);call(rider,"setRidingHorse",true);
    call(t("lotr.common.entity.LOTRMountFunctions"),"setNavigatorRangeFromNPC",mount,rider);
    Object hire=f(rider,"hiredNPCInfo");if(!(Boolean)call(hire,"isHalted"))call(hire,"halt");call(hire,"setGuardMode",false);
    call(rider,"func_70606_j",3.25f);call(mount,"func_70606_j",7.125f);
    Object unit=((Map)f(data,"hiredUnits")).get(riderId);require(unit!=null,"Canonical safe unit absent");set(unit,"stationedEntityData",call(t("kome.common.data.KOMEEntitySnapshots"),"snapshot",rider));call(data,"func_76185_a");teleport(player,origin);
    log("FIXTURE_CORRECTED untamed direct-API horse now setBelongsToNPC=true; original safe UUIDs retained; halted; baselineHP=3.25/7.125; not product repair");
   }
   require(f(rider,"field_70154_o")==mount,"Original mounted tree broken");
   if(command.equals("reset")){
    require("FINALE".equals(String.valueOf(f(f(data,"warSeason"),"phase"))),"Refuse reset outside Finale");
    snapshot(data,"mounted-before-reconciliation");
    Object repair=call(data,"rebuildArmyCompaniesForPlayer",OWNER);log("EXISTING_CANONICAL_RECONCILIATION result="+repair+" companies="+((Map)f(data,"armyCompanies")).keySet());
    require(!((Map)f(data,"armyCompanies")).containsKey("C-mounted-acceptance"),"Old empty fixture remains unresolved");
    call(rider,"func_70606_j",3.25f);call(mount,"func_70606_j",7.125f);
    Object unit=((Map)f(data,"hiredUnits")).get(riderId);require(unit!=null,"Canonical rider absent");
    call(t("kome.common.data.KOMECampaignHealth"),"observe",unit,rider);set(unit,"stationedEntityData",call(t("kome.common.data.KOMEEntitySnapshots"),"snapshot",rider));call(data,"func_76185_a");
    snapshot(data,"mounted-reset-before");log("RESET_BASELINE rider="+riderId+" mount="+mountId+" actualHP=3.25/7.125 canonicalHealth="+f(unit,"survivingHealth"));
    int result=(Integer)call(call(server,"func_71187_D"),"func_71556_a",server,"season reset");
    Object state=f(data,"seasonReset");log("RESET_COMMAND result="+result+" phase="+f(f(data,"warSeason"),"phase")+" failure="+f(state,"failure")+" complete="+call(state,"complete"));
    snapshot(data,"mounted-reset-after");
    require(result==1&&"RESET".equals(String.valueOf(f(f(data,"warSeason"),"phase")))&&(Boolean)call(state,"complete"),"Reset did not complete its return work");
    require(f(rider,"field_70154_o")==mount,"Mount lost during reset");
    require(3.25f==(Float)call(rider,"func_110143_aJ")&&7.125f==(Float)call(mount,"func_110143_aJ"),"Survivor HP changed during reset");
    teleport(player,cap(data,"gondor"));
   }else if(command.equals("finish")){
    int result=(Integer)call(call(server,"func_71187_D"),"func_71556_a",server,"season complete-reset");
    require(result==1&&"MAINTENANCE".equals(String.valueOf(f(f(data,"warSeason"),"phase"))),"Reset completion failed");snapshot(data,"mounted-reset-completed");
   }
   if(command.equals("stop-for-restart")){
    require("MAINTENANCE".equals(String.valueOf(f(f(data,"warSeason"),"phase")))&&(Boolean)call(f(data,"seasonReset"),"complete"),"Completed reset is required");
    Object unit=((Map)f(data,"hiredUnits")).get(riderId);require(unit!=null,"Canonical rider absent");
    call(rider,"func_70606_j",3.25f);call(mount,"func_70606_j",7.125f);call(t("kome.common.data.KOMECampaignHealth"),"observe",unit,rider);set(unit,"stationedEntityData",call(t("kome.common.data.KOMEEntitySnapshots"),"snapshot",rider));call(data,"func_76185_a");
    snapshot(data,"mounted-restart-before");
    call(call(server,"func_71187_D"),"func_71556_a",server,"save-all");
    require(3.25f==(Float)call(rider,"func_110143_aJ")&&7.125f==(Float)call(mount,"func_110143_aJ"),"HP changed during save");
    log("RESTART_BASELINE damaged surviving original fixture HP=3.25/7.125; save-all complete; stop requested");
    call(call(server,"func_71187_D"),"func_71556_a",server,"stop");
   }else if(command.equals("cold-inspect")){
    require(3.25f==(Float)call(rider,"func_110143_aJ")&&7.125f==(Float)call(mount,"func_110143_aJ"),"Saved partial HP not preserved on first cold load");
    snapshot(data,"mounted-restart-after");log("COLD_LOAD_CHECK originalUUIDs=true mountedTree=true savedHP=3.25/7.125 phase="+f(f(data,"warSeason"),"phase")+" firstServerThreadLoad=true noClientRequired=true");
   }
   log("OBSERVED rider="+riderId+" mount="+mountId+" riderHP="+call(rider,"func_110143_aJ")+" mountHP="+call(mount,"func_110143_aJ")+" x="+f(rider,"field_70165_t")+" y="+f(rider,"field_70163_u")+" z="+f(rider,"field_70161_v")+" reset="+f(data,"seasonReset")+" phase="+f(f(data,"warSeason"),"phase")+" originalMountedTree=true");
  }else throw new IllegalArgumentException(command);
 }
 public static void main(String[] a)throws Exception{VirtualMachine v=VirtualMachine.attach(a[0]);try{v.loadAgent(a[1],a[2]);}finally{v.detach();}}
 public static void agentmain(final String command,Instrumentation ins)throws Exception {
  require(Paths.get(System.getProperty("user.dir")).toRealPath().equals(ROOT.resolve("server").toRealPath()),"Wrong disposable server");Class<?> bridge=null;for(Class<?> c:ins.getAllLoadedClasses())if(c.getName().equals("AcceptanceTaskBridge"))bridge=c;require(bridge!=null,"Bridge absent");cl=bridge.getClassLoader();final CountDownLatch done=new CountDownLatch(1);final Throwable[] error=new Throwable[1];
  bridge.getMethod("enqueueServerTask",Runnable.class).invoke(null,new Runnable(){public void run(){try{MountedAcceptanceProbeV10.run(command);}catch(Throwable e){error[0]=e;try{log("FIXTURE_FAIL "+e);}catch(Exception ignored){}}finally{done.countDown();}}});require(done.await(45,TimeUnit.SECONDS),"Server queue timeout");if(error[0]!=null)throw new RuntimeException(error[0]);
 }
}


