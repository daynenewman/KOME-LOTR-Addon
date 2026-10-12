import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import com.sun.tools.attach.VirtualMachine;

/** Seeds prerequisites only; the human must use the actual native Campaign Hire screen. */
public final class CampaignHireAcceptanceProbe {
 static final Path ROOT=Paths.get("C:/Users/dayne/.codex/worktrees/686a/KOME-LOTR-Addon/build/pr-review-20261007/reload-fix-runtime");
 static final UUID OWNER=UUID.fromString("a976a4b7-0614-49c0-b992-61a3a89bd773");static ClassLoader cl;
 static Class<?> t(String n)throws Exception{return cl.loadClass(n);}
 static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
 static Object f(Object o,String n)throws Exception{return MountedReloadAcceptanceProbe.f(o,n);}
 static Object call(Object o,String n,Object...a)throws Exception{return MountedReloadAcceptanceProbe.call(o,n,a);}
 static synchronized void log(String s)throws Exception{Files.write(ROOT.resolve("campaign-hire-checks.log"),(new Date()+" "+s+"\n").getBytes("UTF-8"),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 static long bank(Object data)throws Exception{return (Long)call(call(data,"getFactionPopulation","gondor"),"getAvailablePopulationCenti");}
 static int coins(Object player)throws Exception{return (Integer)call(t("lotr.common.item.LOTRItemCoin"),"getInventoryValue",player,false);}
 static void run(String command)throws Exception{
  require(Thread.currentThread().getName().equals("Server thread"),"Wrong thread");
  Object server=call(t("net.minecraft.server.MinecraftServer"),"func_71276_C"),manager=call(server,"func_71203_ab"),player=call(manager,"func_152612_a","_Danye_");
  require(player!=null&&OWNER.equals(call(t("kome.common.KOMEReflection"),"getEntityUUID",player)),"Authenticated player absent");
  require(((Integer)f(player,"field_71093_bK"))==100&&!(Boolean)call(player,"func_70003_b",2,"season"),"Expected non-operator in Middle-earth");
  Object world=call(t("net.minecraftforge.common.DimensionManager"),"getWorld",100),data=call(t("kome.common.data.KOMEWorldData"),"get",world);
  Path fixture=ROOT.resolve("campaign-hire-fixture.properties");
  if(command.equals("prepare")){
   require(!Files.exists(fixture),"Hire prerequisites already seeded; preserve existing fixture");
   MountedReloadAcceptanceProbe.snapshot(data,"campaign-hire-before-fixture");
   Object pd=call(t("lotr.common.LOTRLevelData"),"getData",player),gondor=t("lotr.common.fac.LOTRFaction").getField("GONDOR").get(null);
   require(call(pd,"getPledgeFaction")==gondor,"Existing acceptance pledge must be Gondor");
   float alignment=(Float)call(pd,"getAlignment",gondor);int coinBefore=coins(player);long bankBefore=bank(data);
   call(pd,"setAlignment",gondor,1000f);call(t("lotr.common.item.LOTRItemCoin"),"giveCoins",500,player);
   for(String permission:new String[]{"baseline.npc_trade","baseline.hire_units"}){
    int result=(Integer)call(call(server,"func_71187_D"),"func_71556_a",server,"progression grant _Danye_ "+permission);require(result==1,"Staff prerequisite grant failed: "+permission);
   }
   Method grant=data.getClass().getDeclaredMethod("grantFactionPopulationCenti",String.class,long.class);grant.setAccessible(true);grant.invoke(data,"gondor",100000L);call(data,"func_76185_a");
   Object trader=call(t("net.minecraft.entity.EntityList"),"func_75620_a","lotr.GondorCaptain",world);require(trader!=null,"Gondor captain registry entry absent");
   call(trader,"func_70012_b",78017.5d,200d,66238.5d,0f,0f);call(trader,"func_110161_a",new Object[]{null});call(trader,"func_94058_c","Acceptance Recruit Captain");call(trader,"func_110163_bv");
   require((Boolean)call(world,"func_72838_d",trader),"Native captain spawn rejected");
   require((Boolean)call(trader,"canTradeWith",player),"Captain native trade is not eligible");
   Object[] entries=(Object[])f(call(trader,"getUnits"),"tradeEntries");require(entries.length>0,"No native warrior trade");
   Object entry=entries[0];require((Boolean)call(t("kome.common.data.KOMENativeTraderCampaignRecruitment"),"supportsDirectCampaignHire",entry),"First trade needs custom adapter");
   require((Boolean)call(entry,"hasRequiredCostAndAlignment",player,trader),"First native trade prerequisites not met");
   Object location=call(t("kome.common.data.KOMERecruitmentLocationService"),"evaluate",data,"gondor","T388");require((Boolean)f(location,"legal"),"Current capital is not legally recruitable");
   Properties p=new Properties();p.setProperty("trader",call(t("kome.common.KOMEReflection"),"getEntityUUID",trader).toString());p.setProperty("coinBeforeFixture",String.valueOf(coinBefore));p.setProperty("alignmentBeforeFixture",String.valueOf(alignment));p.setProperty("bankBeforeFixture",String.valueOf(bankBefore));p.setProperty("coinBeforePurchase",String.valueOf(coins(player)));p.setProperty("bankBeforePurchase",String.valueOf(bank(data)));p.setProperty("firstTradeCost",String.valueOf(call(entry,"getCost",player,trader)));p.setProperty("unitsBefore",String.valueOf(((Map)f(data,"hiredUnits")).size()));
   try(OutputStream out=Files.newOutputStream(fixture)){p.store(out,"Only prerequisites seeded; no paid hire has occurred");}
   MountedReloadAcceptanceProbe.snapshot(data,"campaign-hire-ready");
   log("HIRE_READY captain="+p.getProperty("trader")+" firstTradeCost="+p.getProperty("firstTradeCost")+" coins="+coins(player)+" populationCenti="+bank(data)+" alignment=1000 staffUnlockedTradeAndHire=true onlyPrerequisitesSeeded=true purchaseNotPerformed=true");
  }else if(command.equals("inspect")){
   require(Files.exists(fixture),"Hire fixture absent");Properties p=new Properties();try(InputStream in=Files.newInputStream(fixture)){p.load(in);}
   MountedReloadAcceptanceProbe.snapshot(data,"campaign-hire-observed");
   log("HIRE_OBSERVED coins="+coins(player)+" coinBefore="+p.getProperty("coinBeforePurchase")+" populationCenti="+bank(data)+" populationBefore="+p.getProperty("bankBeforePurchase")+" units="+((Map)f(data,"hiredUnits")).keySet()+" companies="+((Map)f(data,"armyCompanies")).keySet());
   Object awareness=t("kome.common.data.KOMEServerTileAwareness").getField("INSTANCE").get(null);
   for(Object key:((Map)f(data,"hiredUnits")).keySet()){
    Object q=call(awareness,"current",key);Optional<?> observed=(Optional<?>)call(q,"observation");
    log("HIRE_QUERY unit="+key+" query="+f(q,"availability")+" location="+(observed.isPresent()?f(observed.get(),"location"):"absent"));
   }
  }else throw new IllegalArgumentException(command);
 }
 public static void main(String[] a)throws Exception{VirtualMachine vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(a[1],a[2]);}finally{vm.detach();}}
 public static void agentmain(final String command,Instrumentation ins)throws Exception{
  require(Paths.get(System.getProperty("user.dir")).toRealPath().equals(ROOT.resolve("server").toRealPath()),"Wrong owned disposable server");Class<?> bridge=null;for(Class<?> c:ins.getAllLoadedClasses())if(c.getName().equals("AcceptanceTaskBridge"))bridge=c;require(bridge!=null,"Bridge absent");cl=bridge.getClassLoader();MountedReloadAcceptanceProbe.cl=cl;
  final CountDownLatch done=new CountDownLatch(1);final Throwable[] error=new Throwable[1];bridge.getMethod("enqueueServerTask",Runnable.class).invoke(null,new Runnable(){public void run(){try{CampaignHireAcceptanceProbe.run(command);}catch(Throwable e){error[0]=e;try{log("FIXTURE_FAIL "+e);}catch(Exception ignored){}}finally{done.countDown();}}});require(done.await(45,TimeUnit.SECONDS),"Queue timeout");if(error[0]!=null)throw new RuntimeException(error[0]);
 }
}
