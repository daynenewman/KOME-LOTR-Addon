package kome.common.config;
import cpw.mods.fml.relauncher.FMLInjectionData;
import net.minecraftforge.common.config.Configuration;
import java.io.File;
import java.lang.reflect.Field;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
public class KOMENetworkConfigTest {
    @Rule public final TemporaryFolder tmp=new TemporaryFolder();
    KOMEConfigRegistry.ValidatedConfig previous;
    @Before public void before()throws Exception{
        Field home=FMLInjectionData.class.getDeclaredField("minecraftHome");home.setAccessible(true);home.set(null,new File(".").getAbsoluteFile());
        previous=KOMEConfigRegistry.currentValidated();KOMEConfigRegistry.onServerStop();
    }
    @After public void after()throws Exception{KOMEConfigRegistry.onServerStop();Field active=KOMEConfigRegistry.class.getDeclaredField("current");active.setAccessible(true);active.set(null,previous);}
    File config(String token)throws Exception{File file=tmp.newFile();Configuration c=new Configuration(file);c.load();c.get("network","serverRecordCooldownMillis","2000").set(token);c.save();return file;}
    @Test public void defaultCustomInspectionAndChangeSetUseCanonicalTypedSnapshot()throws Exception{
        File base=tmp.newFile();KOMEConfigRegistry.load(base);assertEquals(2000,KOMEConfigRegistry.network().getServerRecordCooldownMillis());
        assertEquals("2000",KOMEConfigInspection.getEffectiveValues("network").get(0).getValue());
        KOMEConfigRegistry.ValidatedConfig old=KOMEConfigRegistry.currentValidated(),candidate=KOMEConfigRegistry.readValidated(config("3500"));
        assertEquals(2000,KOMEConfigRegistry.network().getServerRecordCooldownMillis());
        KOMEConfigChangeSet changes=KOMEConfigChangeSet.compare(old,candidate);assertEquals(1,changes.getEntries().size());
        assertEquals("network.serverRecordCooldownMillis",changes.getEntries().get(0).getCanonicalKey());
        KOMEConfigRegistry.load(config("3500"));assertEquals(3500,KOMEConfigRegistry.network().getServerRecordCooldownMillis());
        assertEquals("3500",KOMEConfigInspection.getEffectiveValues("network").get(0).getValue());
    }
    @Test public void invalidCandidateCannotPartiallyPublishOtherValuesOrReadiness()throws Exception{
        KOMEConfigRegistry.load(config("2000"));KOMEConfigRegistry.ValidatedConfig valid=KOMEConfigRegistry.currentValidated();
        for(String token:new String[]{"0","-1","60001","2147483648","2.5","NaN","", "2e3"}){
            File file=config(token);Configuration c=new Configuration(file);c.load();c.get("movement","footOrMixedTilesPerDay","1").set("2");c.save();
            try{KOMEConfigRegistry.load(file);fail(token);}catch(KOMEConfigValidationException expected){assertEquals("network.serverRecordCooldownMillis",expected.getKey());}
            assertSame(valid,KOMEConfigRegistry.currentValidated());assertTrue(KOMEConfigRegistry.isReady());assertEquals(2000,KOMEConfigRegistry.network().getServerRecordCooldownMillis());
        }
    }
    @Test public void boundaryValuesAreExact()throws Exception{
        assertEquals(1,KOMEConfigRegistry.readValidated(config("1")).getNetwork().getServerRecordCooldownMillis());
        assertEquals(60000,KOMEConfigRegistry.readValidated(config("60000")).getNetwork().getServerRecordCooldownMillis());
    }
}
