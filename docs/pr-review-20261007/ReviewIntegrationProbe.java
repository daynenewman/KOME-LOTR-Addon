import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.time.Instant;
import com.sun.tools.attach.VirtualMachine;

/** Production classloader and server thread; isolated data fixtures, never live repairs. */
public final class ReviewIntegrationProbe {
 static final Path ROOT=Paths.get("C:/Users/dayne/.codex/worktrees/686a/KOME-LOTR-Addon/build/pr-review-20261007");
 static ClassLoader cl;
 static Class<?> type(String n)throws Exception{return cl.loadClass(n);}
 static Object field(Object o,String n)throws Exception{return o.getClass().getField(n).get(o);}
 static void set(Object o,String n,Object v)throws Exception{o.getClass().getField(n).set(o,v);}
 static void require(boolean b,String s){if(!b)throw new IllegalStateException(s);}
 static void log(String s)throws Exception{Files.write(ROOT.resolve("integration-probe.log"),(new Date()+" "+s+"\n").getBytes(StandardCharsets.UTF_8),StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
 static Object save(Object data)throws Exception{Object tag=type("net.minecraft.nbt.NBTTagCompound").newInstance();data.getClass().getMethod("func_76187_b",tag.getClass()).invoke(data,tag);return tag;}
 static List<?> events(Object data)throws Exception{return (List<?>)type("kome.common.data.KOMEAuditService").getMethod("entries",data.getClass()).invoke(null,data);}
 public static void main(String[] a)throws Exception{VirtualMachine vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(a[1],"");}finally{vm.detach();}}
 public static void agentmain(String ignored,Instrumentation ins)throws Exception{
  require(Paths.get(System.getProperty("user.dir")).toRealPath().equals(ROOT.resolve("server").toRealPath()),"Wrong disposable server");
  Class<?> bridge=null;for(Class<?> c:ins.getAllLoadedClasses())if(c.getName().equals("AcceptanceTaskBridge"))bridge=c;
  require(bridge!=null,"Test bridge missing");cl=bridge.getClassLoader();
  final CountDownLatch done=new CountDownLatch(1);final Throwable[] failure=new Throwable[1];
  bridge.getMethod("enqueueServerTask",Runnable.class).invoke(null,new Runnable(){public void run(){try{
   require(Thread.currentThread().getName().equals("Server thread"),"Wrong thread");
   Class<?> dataType=type("kome.common.data.KOMEWorldData"),companyType=type("kome.common.data.KOMEArmyCompany");
   Object data=dataType.getConstructor(String.class).newInstance("native-review-fixture");Object company=companyType.newInstance();
   set(company,"id","C-review");set(company,"owner",UUID.fromString("82cb47c7-fa4c-4be8-ba9b-000000000082"));
   set(company,"faction","gondor");set(company,"nativeFaction","gondor");set(company,"currentTile","T401");
   ((Map)field(data,"armyCompanies")).put("C-review",company);
   for(int i=0;i<31;i++)((List)field(company,"units")).add(new UUID(82,i));
   Object before=save(data);
   List<String> lines=(List<String>)type("kome.common.data.KOMEAdminDiagnostics").getMethod("inspect",dataType,String.class,String.class,String.class,type("net.minecraft.world.World")).invoke(null,data,"company","C-review","",null);
   require(lines.size()<=20,"Unbounded diagnostics");require(before.equals(save(data)),"Inspection mutated data");
   for(String line:lines){require(line.length()<=512,"Unbounded line");log("COMPANY "+line);}
   require(lines.toString().contains("Cached physical observations"),"Cached evidence missing");
   require(lines.toString().contains("Additional coherence issues omitted="),"Omission count missing");
   log("PASS production_company_diagnostics nativeLoader=true serverThread=true isolatedData=true noAuthoritativeMutation=true");
   Object root=type("kome.common.command.KOMECommandKome").newInstance();Class<?> senderType=type("net.minecraft.command.ICommandSender");
   Object denied=Proxy.newProxyInstance(cl,new Class<?>[]{senderType},new InvocationHandler(){public Object invoke(Object proxy,Method method,Object[] args){
    String name=method.getName();if(name.equals("func_70003_b"))return false;
    if(name.equals("func_70005_c_"))return "ordinary-review-fixture";
    if(name.equals("toString"))return "ordinary-review-fixture";
    if(name.equals("hashCode"))return 82;
    if(name.equals("equals"))return proxy==args[0];
    throw new AssertionError("Unauthorized command reached unexpected sender/world method "+name);
   }});
   boolean rejected=false;try{root.getClass().getMethod("func_71515_b",senderType,String[].class).invoke(root,denied,new String[]{"diagnostics","company","C-review"});}
   catch(InvocationTargetException e){require(!(e.getCause() instanceof AssertionError),"Permission check accessed world");rejected=true;log("ROOT_DENIAL "+e.getCause());}
   require(rejected,"Unauthorized root accepted");log("PASS native_root_denial_before_world_access syntheticSender=true humanPermissionAcceptance=false");
   ((List)field(company,"units")).clear();
   Class<?> day=type("kome.common.data.KOMEMovementDayService");long now=System.currentTimeMillis();
   day.getMethod("anchor",dataType,long.class).invoke(null,data,now);
   Object schedule=day.getMethod("schedule").invoke(null);long boundary=((Instant)schedule.getClass().getMethod("nextBoundary",Instant.class).invoke(schedule,Instant.ofEpochMilli(now))).toEpochMilli();
   require((Boolean)day.getMethod("applyBoundary",dataType,long.class).invoke(null,data,boundary),"Boundary rejected");
   require(events(data).size()==1,"Audit not once-only");require(field(events(data).get(0),"action").equals("ALLOWANCE_RESTORED"),"Wrong audit action");
   require(!(Boolean)day.getMethod("applyBoundary",dataType,long.class).invoke(null,data,boundary),"Repeated boundary accepted");
   Object saved=save(data),restored=dataType.getConstructor(String.class).newInstance("native-restored");
   dataType.getMethod("func_76184_a",saved.getClass()).invoke(restored,saved);
   require(!(Boolean)day.getMethod("applyBoundary",dataType,long.class).invoke(null,restored,boundary),"Reload repeated boundary");require(events(restored).size()==1,"Reload duplicated audit");
   log("PASS production_movement_credit_audit onceOnly=true nativeNBTRoundTrip=true liveWorldBoundaryOrColdJVMRestart=false event="+events(restored).get(0));
  }catch(Throwable t){failure[0]=t;try{log("FAIL "+t);if(t instanceof InvocationTargetException)log("CAUSE "+((InvocationTargetException)t).getCause());}catch(Exception ignored){}t.printStackTrace();}finally{done.countDown();}}});
  require(done.await(45,TimeUnit.SECONDS),"Queue timeout");if(failure[0]!=null)throw new RuntimeException(failure[0]);
 }
}
