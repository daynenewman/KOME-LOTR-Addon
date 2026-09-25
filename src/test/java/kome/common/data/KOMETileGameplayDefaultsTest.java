package kome.common.data;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class KOMETileGameplayDefaultsTest {
    static final String ROOT="assets/kome/config/";
    static ClassLoader changed(final String name, final String text, final boolean rehash) throws Exception {
        final Properties manifest=new Properties();
        try(InputStream in=KOMETileGameplayDefaultsTest.class.getClassLoader().getResourceAsStream(ROOT+"kome_tile_gameplay_manifest.properties")){manifest.load(in);}
        if(rehash && text!=null) {
            StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.ROOT,"%02x",b&255));
            manifest.setProperty(name,hash.toString());
        }
        final ByteArrayOutputStream output=new ByteArrayOutputStream();manifest.store(output,"test fixture");
        return new ClassLoader(KOMETileGameplayDefaultsTest.class.getClassLoader()) {
            @Override public InputStream getResourceAsStream(String path) {
                if(path.equals(ROOT+name))return text==null?null:new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
                if(path.equals(ROOT+"kome_tile_gameplay_manifest.properties"))return new ByteArrayInputStream(output.toByteArray());
                return super.getResourceAsStream(path);
            }
        };
    }
    static String resource(String name)throws Exception{return new String(Files.readAllBytes(Paths.get("src/main/resources",ROOT,name)),StandardCharsets.UTF_8);}
    static void rejected(String file,String text,boolean rehash,String message)throws Exception{
        KOMETileGameplayDefaults previous=KOMETileGameplayDefaults.get();
        try{KOMETileGameplayDefaults.load(changed(file,text,rehash));fail("Must reject "+message);}catch(IOException expected){assertTrue(expected.getMessage(),expected.getMessage().contains(message));}
        assertSame(previous,KOMETileGameplayDefaults.get());assertEquals(1319,previous.routeKeys().size());
    }
    @Test public void malformedVersionMissingMixedAndOversizedResourcesFailWithoutReplacingDefaults()throws Exception{
        rejected("kome_tile_route_defaults.csv",null,false,"missing resource");
        String r=resource("kome_tile_route_defaults.csv");
        rejected("kome_tile_route_defaults.csv",r+"T001,T002,open\n",false,"SHA-256 mismatch");
        rejected("kome_tile_route_defaults.csv",r.replace("defaults 1","defaults 2"),true,"unsupported");
        char[] large=new char[1048577];Arrays.fill(large,'x');rejected("kome_tile_route_defaults.csv",new String(large),false,"1 MiB");
    }
    @Test public void structuralValidationRejectsBadRowsEvenWithUpdatedChecksums()throws Exception{
        String r=resource("kome_tile_route_defaults.csv");
        rejected("kome_tile_route_defaults.csv",r+"T030,T033,open\n",true,"duplicate route");
        rejected("kome_tile_route_defaults.csv",r.replaceFirst("T030,T033,open","T030,T999,open"),true,"unknown/noncanonical");
        rejected("kome_tile_route_defaults.csv",r.replaceFirst("T030,T033,open","T030,T033,flying"),true,"unknown route type");
        String p=resource("kome_tile_gameplay_points.csv");
        rejected("kome_tile_gameplay_points.csv",p.replaceFirst("190006.85714285716","NaN"),true,"invalid finite");
        rejected("kome_tile_gameplay_points.csv",p.replaceFirst("T001[^\n]*\n",""),true,"cover every active tile");
        String w=resource("kome_tile_waypoint_candidates.csv");
        rejected("kome_tile_waypoint_candidates.csv",w.replaceFirst(",0,",",2,"),true,"priorities");
        rejected("kome_tile_waypoint_candidates.csv",w.replace("MITHLOND_SOUTH","NOT_A_WAYPOINT"),true,"unknown/duplicate waypoint");
        String m=resource("kome_tile_route_markers.csv");
        rejected("kome_tile_route_markers.csv",m.replaceFirst(",bridge,",",open,"),true,"matching canonical route");
    }
    @Test public void immutableValuesDefensiveEdgesAndExplicitLegacyExceptions()throws Exception{
        KOMETileGameplayDefaults d=KOMETileGameplayDefaults.get();
        assertEquals(19,d.legacyDestinationExceptions().size());assertEquals("IN_BOUNDS_GAP:",d.legacyDestinationExceptions().get("T423"));
        try{d.neighbors("T030").clear();fail();}catch(UnsupportedOperationException expected){}
        try{d.routeKeys().clear();fail();}catch(UnsupportedOperationException expected){}
        try{d.waypointCandidates("T168").clear();fail();}catch(UnsupportedOperationException expected){}
        KOMEConquestRouteEdge e=d.route("T041","T042");assertFalse(e.isPassable());e.edgeType="open";
        assertEquals("river",d.route("T041","T042").edgeType);
        assertEquals(56,KOMEConquestTileDefaults.getAutomaticBridgeEdges().size());
        int old=lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID;
        try{lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID=123;assertEquals(123,d.getArrivalDefault("T132").dimensionId());assertEquals(123,KOMEConquestTileDefaults.getAutomaticBridgeMarkers().get(0).dimensionId);}finally{lotr.common.LOTRDimension.MIDDLE_EARTH.dimensionID=old;}
    }
    static URL[] classpath()throws Exception{
        String[] paths=System.getProperty("java.class.path").split(File.pathSeparator);URL[] urls=new URL[paths.length];
        for(int i=0;i<paths.length;i++)urls[i]=new File(paths[i]).toURI().toURL();return urls;
    }
    @Test public void dedicatedServerConcurrentFirstLoadNeedsNeitherClientNorRaster()throws Exception{
        try(URLClassLoader loader=new URLClassLoader(classpath(),ClassLoader.getSystemClassLoader().getParent()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(name.startsWith("kome.client.")||name.startsWith("lotr.client.")||name.startsWith("net.minecraft.client.")||name.startsWith("org.lwjgl."))throw new ClassNotFoundException("Client forbidden: "+name);
                return super.loadClass(name,resolve);
            }
            @Override public InputStream getResourceAsStream(String path){
                if(path.endsWith(".png"))throw new AssertionError("Gameplay may not decode geometry: "+path);
                return super.getResourceAsStream(path);
            }
        }){
            final Class<?> type=loader.loadClass("kome.common.data.KOMETileGameplayDefaults");
            ExecutorService pool=Executors.newFixedThreadPool(8);final CountDownLatch start=new CountDownLatch(1);
            try{
                List<Future<Object>> calls=new ArrayList<Future<Object>>();
                for(int i=0;i<8;i++)calls.add(pool.submit(new Callable<Object>(){public Object call()throws Exception{start.await();Object d=type.getMethod("get").invoke(null);assertEquals(1319,((Set<?>)type.getMethod("routeKeys").invoke(d)).size());return d;}}));
                start.countDown();Object first=calls.get(0).get(30,TimeUnit.SECONDS);for(Future<Object> f:calls)assertSame(first,f.get(30,TimeUnit.SECONDS));
            }finally{pool.shutdownNow();}
        }
    }
    @Test public void initialFailureIsActionableAndDoesNotPublishPartialDefaults()throws Exception{
        try(URLClassLoader loader=new URLClassLoader(classpath(),ClassLoader.getSystemClassLoader().getParent()){
            @Override public InputStream getResourceAsStream(String path){if(path.equals(ROOT+"kome_tile_waypoint_candidates.csv"))return null;return super.getResourceAsStream(path);}
        }){
            Class<?> type=loader.loadClass("kome.common.data.KOMETileGameplayDefaults");
            for(int i=0;i<2;i++)try{type.getMethod("get").invoke(null);fail();}catch(java.lang.reflect.InvocationTargetException e){assertTrue(e.getCause() instanceof IllegalStateException);assertTrue(e.getCause().getMessage().contains("kome_tile_waypoint_candidates.csv"));assertTrue(e.getCause().getMessage().contains("inference is disabled"));}
        }
    }
    @Test public void baselineAndSelectedV2HaveIdenticalActualGameplayPaths()throws Exception{
        List<String> expected=Files.readAllLines(Paths.get("src/test/resources/kome/tile/gameplay-baseline/production-paths.tsv"),StandardCharsets.UTF_8);
        for(final String mask:new String[]{"src/test/resources/kome/tile/gameplay-baseline/mask.png","src/main/resources/assets/kome/map/reset_conquest_tile_ids.png"}){
            try(URLClassLoader loader=new URLClassLoader(classpath(),ClassLoader.getSystemClassLoader().getParent()){
                @Override public InputStream getResourceAsStream(String path){
                    if(path.equals("assets/kome/map/reset_conquest_tile_ids.png"))try{return Files.newInputStream(Paths.get(mask));}catch(IOException e){throw new IllegalStateException(e);}
                    return super.getResourceAsStream(path);
                }
            }){
                Class<?> probe=loader.loadClass("kome.common.data.KOMETileGameplayParityTest");
                java.lang.reflect.Method prepare=probe.getDeclaredMethod("prepare"),capture=probe.getDeclaredMethod("capture");prepare.setAccessible(true);capture.setAccessible(true);prepare.invoke(null);
                List<?> actual=(List<?>)capture.invoke(null);assertEquals(mask,expected.size(),actual.size());
                for(int i=0;i<expected.size();i++)assertEquals(mask+" line "+i,expected.get(i),actual.get(i));
            }
        }
    }
}
