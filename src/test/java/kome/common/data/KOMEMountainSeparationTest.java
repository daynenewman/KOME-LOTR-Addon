package kome.common.data;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static kome.common.data.KOMETileResolution.Status.*;

/** Exact approved delta, independent of historical V2 selections and loader annotations. */
public class KOMEMountainSeparationTest {
    static final String FIX = "kome/tile/mountain-separation-approved/";
    static final String MASK_HASH = "89bd8ccc9ceff3e7d3f9271d61125b07f5f9b1122fb51726ab2f2f5a95364a12";
    @Rule public final KOMETileTestResources resources = new KOMETileTestResources();
    static final class Cell {
        final int x, y; final String before, pair, zone;
        Cell(String[] f) { pair=f[0]; x=Integer.parseInt(f[1]); y=Integer.parseInt(f[2]); before=f[3]; zone=pair.toLowerCase(Locale.ROOT)+"-contact"; }
    }
    private static Map<Integer,Cell> approved;
    static synchronized Map<Integer,Cell> cells() throws Exception {
        if (approved != null) return approved;
        ClassLoader l=KOMEMountainSeparationTest.class.getClassLoader();
        assertEquals("f14472b0e865974634bc5b6e791e0ce872687f1fda6a56958ec038165a9b4b5e",
            KOMEV2GeometryTest.hash(l,FIX+"recommended-cell-manifest.csv"));
        Map<Integer,Cell> result=new LinkedHashMap<Integer,Cell>(); Map<String,Integer> counts=new TreeMap<String,Integer>();
        try(BufferedReader r=reader(FIX+"recommended-cell-manifest.csv")) {
            assertTrue(r.readLine().startsWith("pair,mask_x,mask_y,before_tile,after_tile,")); String line;
            while((line=r.readLine())!=null) {
                String[] f=line.split(",",6); Cell c=new Cell(f);
                assertEquals("GAP",f[4]); assertTrue(c.x>=0 && c.x<3200 && c.y>=0 && c.y<4000);
                assertNull(result.put(c.y*3200+c.x,c)); counts.put(c.before,counts.containsKey(c.before)?counts.get(c.before)+1:1);
            }
        }
        assertEquals(45,result.size());
        Map<String,Integer> expected=new TreeMap<String,Integer>();
        String[] ids={"T654","T420","T329","T355","T352","T356","T218"}; int[] n={2,10,4,8,2,4,15};
        for(int i=0;i<ids.length;i++) expected.put(ids[i],n[i]); assertEquals(expected,counts);
        approved=Collections.unmodifiableMap(result); return approved;
    }
    static BufferedReader reader(String name) { return new BufferedReader(new InputStreamReader(
        KOMEMountainSeparationTest.class.getClassLoader().getResourceAsStream(name),StandardCharsets.UTF_8)); }
    static BufferedImage image(String name) throws Exception {return ImageIO.read(KOMEMountainSeparationTest.class.getClassLoader().getResource(name));}

    @Test public void onlyThe45ApprovedRgbaCellsChangeAndActiveIdsStayStable() throws Exception {
        ClassLoader l=getClass().getClassLoader(); Map<Integer,Cell> cells=cells();
        assertEquals("ab792277f61882d415963bf5af1b8d2705458c68de80f5b3cbb9102e30d1b4a7",KOMEV2GeometryTest.hash(l,FIX+"before-mask.png"));
        assertEquals(MASK_HASH,KOMEV2GeometryTest.hash(l,KOMETileWorldResolver.MASK));
        BufferedImage a=image(FIX+"before-mask.png"), b=image(KOMETileWorldResolver.MASK);
        assertEquals(3200,b.getWidth()); assertEquals(4000,b.getHeight()); int changed=0;
        Set<String> beforeIds=new HashSet<String>(), afterIds=new HashSet<String>();
        for(int y=0;y<4000;y++) {
            int[] old=a.getRGB(0,y,3200,1,null,0,3200), now=b.getRGB(0,y,3200,1,null,0,3200);
            for(int x=0;x<3200;x++) {
                String oi=tile(old[x]), ni=tile(now[x]); if(oi!=null)beforeIds.add(oi);if(ni!=null)afterIds.add(ni);
                Cell c=cells.get(y*3200+x);
                if(c==null) {if(old[x]!=now[x])fail("Unapproved RGBA change at "+x+","+y);}
                else {assertEquals(c.before,oi);assertEquals(255,old[x]>>>24);assertEquals(0,now[x]);changed++;}
            }
        }
        assertEquals(45,changed);assertEquals(beforeIds,afterIds);assertEquals(621,afterIds.size());
    }
    static String tile(int argb) {
        if((argb>>>24)<=24)return null;
        String id=KOMEConquestTileDefaults.getTileIdsByColor().get(argb&0xFFFFFF);
        return KOMEConquestTileDefaults.getRetiredTileIds().contains(id)?null:id;
    }
    @Test public void exact25RunsReachOnlyApprovedMountainCellsThroughProductionAdapters() throws Exception {
        Map<Integer,Cell> cells=cells(); List<String> expected=new ArrayList<String>(); Set<String> zones=new HashSet<String>();
        try(BufferedReader r=reader(FIX+"recommended-exclusion-manifest.csv")) {r.readLine();String line;
            while((line=r.readLine())!=null) {String[] f=line.split(",");zones.add(f[1]);expected.add("run\t"+f[4]+"\t"+f[5]+"\t"+f[6]+"\t"+f[1]);}}
        List<String> actual=new ArrayList<String>();
        try(BufferedReader r=reader(KOMETileWorldResolver.EXCLUSIONS)) {String line;while((line=r.readLine())!=null)if(line.startsWith("run\t"))actual.add(line);}
        assertEquals(25,expected.size());assertEquals(expected,actual);assertEquals(5,zones.size());
        KOMETileRasterSnapshot s=KOMETileTestResources.real(); assertEquals(45,s.exclusions.classifiedCells);assertEquals(5,s.exclusions.zoneCount());
        int d=s.transform.dimension;
        for(Cell c:cells.values()) {
            double x=(c.x-810)*128D,z=(c.y-730)*128D;
            for(double dx:new double[]{0,127,Math.nextDown(128D)})for(double dz:new double[]{0,127}) {
                // nextDown at the world boundary avoids addition rounding back into the next cell.
                double wx=dx==Math.nextDown(128D)?Math.nextDown(x+128):x+dx;
                assertMountain(KOMETileWorldResolver.INSTANCE.resolveWorldPosition(d,wx,z+dz),c.zone);
            }
            assertMountain(KOMETileWorldResolver.INSTANCE.resolveMapPosition(d,c.x+0.5,c.y+0.5),c.zone);
        }
    }
    static void assertMountain(KOMETileResolution r,String zone) {
        assertEquals(CLASSIFIED_EXCLUSION,r.status);assertEquals("",r.tileId);assertEquals(zone,r.exclusion().get().id);
        assertEquals("mountain",r.exclusion().get().type);assertEquals(Boolean.FALSE,r.capturable().get());assertFalse(r.traversable().isPresent());
    }
    @Test public void mismatchedMaskBindingCannotReplaceAValidProductionSnapshot() throws Exception {
        ClassLoader l=getClass().getClassLoader();KOMETileWorldResolver r=new KOMETileWorldResolver();assertTrue(r.reloadBundled());
        KOMETileRasterSnapshot valid=r.snapshot().get();
        assertFalse(r.reload(l.getResourceAsStream(FIX+"before-mask.png"),l.getResourceAsStream(KOMETileWorldResolver.MAPPING),
            l.getResourceAsStream(KOMETileWorldResolver.EXCLUSIONS),valid.transform,
            KOMEConquestTileDefaults.getKnownTileIds(),KOMEConquestTileDefaults.getRetiredTileIds()));
        assertSame(valid,r.snapshot().get());assertTrue(r.loadDiagnostic().contains("SHA-256 mismatch"));
        Cell c=cells().values().iterator().next();assertMountain(r.resolve(valid.transform.dimension,(c.x-810)*128,(c.y-730)*128),c.zone);
    }
    @Test public void affectedTilesStayConnectedAndAuthoredPassCorridorsStayIntact() throws Exception {
        BufferedImage image=image(KOMETileWorldResolver.MASK);Map<String,Set<Integer>> positions=new HashMap<String,Set<Integer>>();
        for(Cell c:cells().values())for(String id:c.pair.split("-"))positions.put(id,new HashSet<Integer>());
        for(int y=0;y<4000;y++) {int[] row=image.getRGB(0,y,3200,1,null,0,3200);for(int x=0;x<3200;x++) {
            Set<Integer> p=positions.get(tile(row[x]));if(p!=null)p.add(y*3200+x);
        }}
        Set<String> pairs=new HashSet<String>();for(Cell c:cells().values())pairs.add(c.pair);
        for(Map.Entry<String,Set<Integer>> e:positions.entrySet()) {
            Set<Integer> remaining=new HashSet<Integer>(e.getValue());assertFalse(remaining.isEmpty());
            ArrayDeque<Integer> queue=new ArrayDeque<Integer>();int seed=remaining.iterator().next();remaining.remove(seed);queue.add(seed);
            while(!queue.isEmpty()) {int p=queue.remove();for(int next:new int[]{p-1,p+1,p-3200,p+3200})
                if(next>=0 && next<12800000 && (next/3200==p/3200 || next%3200==p%3200) && remaining.remove(next))queue.add(next);}
            assertTrue("Disconnected "+e.getKey(),remaining.isEmpty());
        }
        for(String pair:pairs) {String[] ids=pair.split("-");assertArrayEquals(pair,new int[]{0,0},contacts(image,ids[0],ids[1]));
            assertNull(KOMEConquestTileDefaults.getAutomaticRouteEdge(ids[0],ids[1]));}
        try(BufferedReader r=reader(FIX+"protected-corridors.csv")) {r.readLine();String line;int count=0;
            while((line=r.readLine())!=null) {String[] f=line.split(","),ids=f[0].split("-");
                assertArrayEquals(f[0],new int[]{Integer.parseInt(f[3]),Integer.parseInt(f[5])},contacts(image,ids[0],ids[1]));
                assertEquals(f[1],KOMEConquestTileDefaults.getAutomaticRouteEdge(ids[0],ids[1]).edgeType);count++;}assertEquals(10,count);}
    }
    static int[] contacts(BufferedImage image,String a,String b) {
        int ca=KOMETileTestResources.real().colorsById().get(a)|0xFF000000,cb=KOMETileTestResources.real().colorsById().get(b)|0xFF000000;
        int[] out={0,0};for(int y=1;y<3999;y++){int[] row=image.getRGB(0,y,3200,1,null,0,3200);for(int x=1;x<3199;x++)if(row[x]==ca)
            for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++)if((dx!=0||dy!=0)&&image.getRGB(x+dx,y+dy)==cb)out[dx==0||dy==0?0:1]++;}return out;
    }
    @Test public void allOtherMapAndGameplayResourcesRetainCanonicalBytes() throws Exception {
        try(BufferedReader r=reader(FIX+"unchanged-resources.tsv")) {String line;int count=0;
            while((line=r.readLine())!=null) {String[] f=line.split("\t");
                try(InputStream in=getClass().getClassLoader().getResourceAsStream(f[0])) {
                    byte[] bytes=KOMETileExclusions.readBounded(in,64*1024*1024,f[0]);
                    if(f.length==3 && "text".equals(f[2]))bytes=new String(bytes,StandardCharsets.UTF_8).replace("\r\n","\n").getBytes(StandardCharsets.UTF_8);
                    assertEquals(f[0],f[1],KOMETileExclusions.sha256(bytes));count++;
                }
            }
            assertTrue(count>=6);}
    }
}
