package kome.common.network;
import java.text.Normalizer;
import java.util.*;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import kome.common.data.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class KOMEMapHotPathParityTest {
    private static String chars(int... points){StringBuilder s=new StringBuilder();for(int c:points)s.appendCodePoint(c);return s.toString();}
    @Test public void utf8ValidatorMatchesActualWireEncoderForEveryBmpCodeUnitAndBoundaries() {
        for(int c=0;c<=0xffff;c++)sameText(String.valueOf((char)c));
        sameText(null);sameText("");
        for(String unit:Arrays.asList("a",chars(0),chars(0x7ff),chars(0x800),chars(0x1f600))) {
            for(int n:new int[]{1023,1024,1025,1365,1366,2048,2049,4096,4097}) {
                StringBuilder b=new StringBuilder();for(int i=0;i<n;i++)b.append(unit);sameText(b.toString());
            }
        }
        for(String bad:Arrays.asList(chars(0xd800,97),chars(97,0xdc00),chars(0xd800,0xd800),chars(0xdc00,0xd800)))sameText(bad);
    }
    private static void sameText(String s) {
        boolean wire=true,fast=true;ByteBuf b=Unpooled.buffer();
        try{KOMEPopulationWire.writeText(b,s);}catch(IllegalArgumentException e){wire=false;}finally{b.release();}
        try{KOMEPopulationWire.validateText(s);}catch(IllegalArgumentException e){fast=false;}
        assertEquals("UTF-8 acceptance changed",wire,fast);
    }
    @Test public void normalizedKeysPreserveLegacyAliasesUnicodeAndLocaleBehavior() {
        Locale saved=Locale.getDefault();Random random=new Random(67060);
        try{for(Locale locale:Arrays.asList(Locale.US,new Locale("tr","TR"))) {
            Locale.setDefault(locale);
            List<String> values=new ArrayList<>(Arrays.asList(null,"","angmar","gondor","none","neutral","neutralzone","unclaimed","unaligned","hobbits","breeland","rangernorth","rangersnorth","rangerofthenorth","rangersofthenorth","dunedainnorth","dunedainofthenorth","northerndunedain","highelf","highelves","highelven","lindon","rivendell","imladris","nearharad","harad","haradwaith","southron","southrons","woodelf","woodelves","woodlandrealm","mirkwoodelves","D"+chars(0xfa)+"nedain","DUNE"+chars(0x301)+"DAIN"," Imladris! ","ANGMAR",chars(0x131,0x130),"test_91"));
            for(int n=0;n<1000;n++){StringBuilder b=new StringBuilder();for(int j=0;j<20;j++)b.append((char)random.nextInt(65536));values.add(b.toString());}
            for(String value:values)assertEquals(legacyFactionKey(value),KOMEAlliance.normalizeFactionKey(value));
        }}finally{Locale.setDefault(saved);}
    }
    @Test public void completedRenderProjectionIsImmutableAndClearsOnSessionReset() throws Exception {
        java.lang.reflect.Constructor<KOMEClientData> c=KOMEClientData.class.getDeclaredConstructor();c.setAccessible(true);KOMEClientData client=c.newInstance();
        KOMEConquestTile tile=new KOMEConquestTile("T001");tile.claim("angmar",0);client.conquestTiles.put(tile.id,tile);client.completeConquestUpdate();
        Map<String,String> published=client.conquestRenderOwners();assertEquals("angmar",published.get("T001"));
        tile.claim("gondor",0);assertEquals("angmar",published.get("T001"));
        try{published.put("T002","rohan");fail();}catch(UnsupportedOperationException expected){}
        client.completeConquestUpdate();assertEquals("gondor",client.conquestRenderOwners().get("T001"));
        client.resetClientState();assertTrue(client.conquestRenderOwners().isEmpty());
    }
    private static String legacyFactionKey(String value) {
        String normalized = value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String key = normalized.toLowerCase().replaceAll("[^a-z0-9]", "");
        if ("".equals(key) || "none".equals(key) || "neutral".equals(key) || "neutralzone".equals(key)
                || "unclaimed".equals(key) || "unaligned".equals(key)) {
            return "";
        }
        if ("hobbits".equals(key)) {
            return "hobbit";
        }
        if ("breeland".equals(key)) {
            return "bree";
        }
        if ("rangernorth".equals(key) || "rangersnorth".equals(key) || "rangerofthenorth".equals(key)
                || "rangersofthenorth".equals(key) || "dunedainnorth".equals(key)
                || "dunedainofthenorth".equals(key) || "northerndunedain".equals(key)) {
            return "dunedain";
        }
        if ("highelf".equals(key) || "highelves".equals(key) || "highelven".equals(key)
                || "lindon".equals(key) || "rivendell".equals(key) || "imladris".equals(key)) {
            return "highelves";
        }
        if ("nearharad".equals(key) || "harad".equals(key) || "haradwaith".equals(key)
                || "southron".equals(key) || "southrons".equals(key)) {
            return "harad";
        }
        if ("woodelf".equals(key) || "woodelves".equals(key) || "woodlandrealm".equals(key)
                || "mirkwoodelves".equals(key)) {
            return "woodelf";
        }
        return key;
    }

}
