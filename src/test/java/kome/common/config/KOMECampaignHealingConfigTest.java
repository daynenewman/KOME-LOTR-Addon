package kome.common.config;

import java.io.File;
import java.util.*;
import net.minecraftforge.common.config.Configuration;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class KOMECampaignHealingConfigTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Rule public kome.common.data.KOMETileTestResources geometry=new kome.common.data.KOMETileTestResources();
    private KOMEConfigRegistry.ValidatedConfig previous;
    private static final KOMEConfigRegistry.RuntimeActivity IDLE=new KOMEConfigRegistry.RuntimeActivity(){
        public boolean isDailyTransactionInProgress(){return false;}
        public boolean isActiveSiegeInProgress(){return false;}
        public Collection<String> getActiveSiegeLockedConfigKeys(){return Collections.emptyList();}
    };
    @Before public void setup() throws Exception {
        java.lang.reflect.Field home=cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");home.setAccessible(true);home.set(null,new File(".").getAbsoluteFile());
        KOMEConfigRegistry.onServerStop(); if(!KOMEConfigRegistry.isReady())KOMEConfigRegistry.load(new File(temporary.getRoot(),"initial.cfg"));
        previous=KOMEConfigRegistry.currentValidated();
    }
    @After public void restore(){KOMEConfigRegistry.applyValidated(previous,IDLE);}
    private File config(String battle,String lifespan){
        File file=new File(temporary.getRoot(),"policy.cfg");Configuration c=new Configuration(file);c.load();
        c.get("battle",KOMEConfigRegistry.DISABLE_NORMAL_HEALING_IN_BATTLE,"false").set(battle);
        c.get("battle",KOMEConfigRegistry.DISABLE_NORMAL_HEALING_DURING_CAMPAIGN_COMMITMENT,"false").set(lifespan);c.save();return file;
    }
    @Test public void existingConfigWithoutHealingKeysDefaultsBothOff() throws Exception {
        File file=new File(temporary.getRoot(),"existing.cfg"); Configuration c=new Configuration(file);c.get("battle","postBattleRecoveryAmount","5");c.save();
        KOMEConfigRegistry.load(file);assertFalse(KOMEConfigRegistry.battle().isNormalHealingDisabledInBattle());assertFalse(KOMEConfigRegistry.battle().isNormalHealingDisabledDuringCampaignCommitment());
        String saved = new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(saved.contains("active battle"));
        assertTrue(saved.contains("committed Campaign lifespan"));
    }
    @Test public void allFourCombinationsAreTypedAndIndependent() {
        for(boolean battle:new boolean[]{false,true})for(boolean lifespan:new boolean[]{false,true}){
            KOMEConfigRegistry.load(config(Boolean.toString(battle),Boolean.toString(lifespan)));
            assertEquals(battle,KOMEConfigRegistry.battle().isNormalHealingDisabledInBattle());assertEquals(lifespan,KOMEConfigRegistry.battle().isNormalHealingDisabledDuringCampaignCommitment());
        }
    }
    @Test public void changesAreTrackedAndApplyLiveAfterWorldInitialization() {
        KOMEConfigRegistry.load(config("false","false"));
        kome.common.data.KOMEWorldData data=new kome.common.data.KOMEWorldData();data.initializeIntegratedWorld();KOMEConfigRegistry.onWorldInitialized(data);
        try {
            KOMEConfigRegistry.ConfigApplyResult result=KOMEConfigRegistry.applyValidated(KOMEConfigRegistry.readValidated(config("true","true")),IDLE);
            assertTrue(result.getDecision().isAllowed());Set<String> keys=new TreeSet<String>();for(KOMEConfigChangeSet.Entry e:result.getChanges().getEntries())keys.add(e.getCanonicalKey());
            assertEquals(new TreeSet<String>(Arrays.asList("battle.disableNormalHealingInBattle","battle.disableNormalHealingDuringCampaignCommitment")),keys);
        } finally {KOMEConfigRegistry.onServerStop();}
    }
    @Test public void inspectionExplainsBothScopesAndAuthorizedBypasses() {
        KOMEConfigRegistry.load(config("true","false"));int explained=0;
        for(KOMEConfigInspection.EffectiveValue v:KOMEConfigInspection.getEffectiveValues("battle"))if(!v.getDescription().isEmpty()){
            assertTrue(v.format().contains(v.getDescription()));assertTrue(v.getDescription().contains("Authorized"));assertFalse(v.getDescription().contains("entire Campaign lifecycle"));explained++;
        }
        assertEquals(2,explained);
    }
    @Test public void malformedBooleansRejectWithoutReplacingActiveSettings() {
        KOMEConfigRegistry.load(config("true","false"));
        for(String key:new String[]{KOMEConfigRegistry.DISABLE_NORMAL_HEALING_IN_BATTLE,KOMEConfigRegistry.DISABLE_NORMAL_HEALING_DURING_CAMPAIGN_COMMITMENT}){
            File file=config("true","false");Configuration c=new Configuration(file);c.load();c.getCategory("battle").get(key).set("yes");c.save();
            assertThrows(KOMEConfigValidationException.class,()->KOMEConfigRegistry.load(file));assertTrue(KOMEConfigRegistry.battle().isNormalHealingDisabledInBattle());assertFalse(KOMEConfigRegistry.battle().isNormalHealingDisabledDuringCampaignCommitment());
        }
    }
}
