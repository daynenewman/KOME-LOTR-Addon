package kome.common.config;

import cpw.mods.fml.relauncher.FMLInjectionData;
import net.minecraftforge.common.config.Configuration;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class KOMEConfigChangeGuardTest {
    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @BeforeClass public static void initializeForge() throws Exception {
        Field field = FMLInjectionData.class.getDeclaredField("minecraftHome");
        field.setAccessible(true); field.set(null, new File(".").getAbsoluteFile());
    }

    @Test public void readValidatedNeverPublishesAndLoadStillPublishes() throws Exception {
        File baseline = config(); KOMEConfigRegistry.load(baseline); Object[] before = surfaces(); KOMEConfigRegistry.ValidatedConfig beforeSnapshot=KOMEConfigRegistry.currentValidated();
        File candidate = config(); write(candidate, "movement", "footOrMixedTilesPerDay", "2"); write(candidate, "movement", "fullyMountedTilesPerDay", "3");
        KOMEConfigRegistry.ValidatedConfig snapshot = KOMEConfigRegistry.readValidated(candidate);
        assertEquals(2, snapshot.getMovement().getFootOrMixedTilesPerDay()); assertSurfaces(before); assertSame(beforeSnapshot,KOMEConfigRegistry.currentValidated());
        try { File bad=config(); write(bad,"season","automaticFinaleEnabled","true"); KOMEConfigRegistry.readValidated(bad); fail(); } catch (KOMEConfigValidationException expected) { }
        assertSurfaces(before); assertSame(beforeSnapshot,KOMEConfigRegistry.currentValidated()); KOMEConfigRegistry.load(candidate); assertEquals(2,KOMEConfigRegistry.movement().getFootOrMixedTilesPerDay());
    }

    @Test public void diffAndOptionalTransitionsAreEffectiveAndImmutable() throws Exception {
        File base=config(); KOMEConfigRegistry.load(base); KOMEConfigRegistry.ValidatedConfig old=KOMEConfigRegistry.currentValidated();
        File change=config(); write(change,"population","populationCapValue","100"); write(change,"siege","gateHpPerApprovedHour","2.5"); write(change,"movement","footOrMixedTilesPerDay","2"); write(change,"movement","fullyMountedTilesPerDay","3");
        KOMEConfigChangeSet diff=KOMEConfigChangeSet.compare(old,KOMEConfigRegistry.readValidated(change));
        assertEquals(4,diff.getEntries().size()); assertEquals("movement",diff.getEntries().get(0).getCategory());
        assertEntry(diff,"population","populationCapValue","TBD","100"); assertEntry(diff,"siege","gateHpPerApprovedHour","TBD","2.5");
        try { diff.getEntries().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        File equivalent=config(); write(equivalent,"population","capturedBuildMultiplier","0.50"); assertTrue(KOMEConfigChangeSet.compare(old,KOMEConfigRegistry.readValidated(equivalent)).isEmpty());
        KOMEConfigRegistry.ValidatedConfig concrete=KOMEConfigRegistry.readValidated(change); assertEntry(KOMEConfigChangeSet.compare(concrete,old),"population","populationCapValue","100","TBD"); assertEntry(KOMEConfigChangeSet.compare(concrete,old),"siege","gateHpPerApprovedHour","2.5","TBD");
    }

    @Test public void guardsAndApplyAreAtomic() throws Exception {
        File base=config(); KOMEConfigRegistry.load(base); Object[] before=surfaces(); KOMEConfigRegistry.ValidatedConfig beforeSnapshot=KOMEConfigRegistry.currentValidated();
        File candidate=config(); write(candidate,"movement","footOrMixedTilesPerDay","2"); write(candidate,"movement","fullyMountedTilesPerDay","3");
        KOMEConfigRegistry.ValidatedConfig snapshot=KOMEConfigRegistry.readValidated(candidate);
        KOMEConfigRegistry.ConfigApplyResult daily=KOMEConfigRegistry.applyValidated(snapshot,new Activity(true,false));
        assertFalse(daily.getDecision().isAllowed()); assertTrue(daily.getDecision().getReasons().get(0).contains("in-progress daily transaction")); assertSurfaces(before); assertSame(beforeSnapshot,KOMEConfigRegistry.currentValidated());
        KOMEConfigRegistry.ConfigApplyResult allowed=KOMEConfigRegistry.applyValidated(snapshot,new Activity(false,false)); assertTrue(allowed.getDecision().isAllowed()); assertEquals(2,KOMEConfigRegistry.movement().getFootOrMixedTilesPerDay());
        assertSame(snapshot,KOMEConfigRegistry.currentValidated()); assertSame(snapshot.getDailyBatch(),KOMEConfigRegistry.dailyBatch()); assertSame(snapshot.getPopulation(),KOMEConfigRegistry.population()); assertSame(snapshot.getMovement(),KOMEConfigRegistry.movement()); assertSame(snapshot.getBattle(),KOMEConfigRegistry.battle()); assertSame(snapshot.getMuster(),KOMEConfigRegistry.muster()); assertSame(snapshot.getSiege(),KOMEConfigRegistry.siege()); assertSame(snapshot.getBattleSupport(),KOMEConfigRegistry.battleSupport()); assertSame(snapshot.getEncirclement(),KOMEConfigRegistry.encirclement()); assertSame(snapshot.getSeason(),KOMEConfigRegistry.season());
        assertValue(KOMEConfigInspection.getAllEffectiveValues(),"movement.footOrMixedTilesPerDay","2");
        KOMEConfigRegistry.ConfigApplyResult noop=KOMEConfigRegistry.applyValidated(KOMEConfigRegistry.currentValidated(),new Activity(true,true,"movement.footOrMixedTilesPerDay")); assertTrue(noop.getDecision().isAllowed());
    }

    @Test public void activeSiegeCheckInWindowIsOptionalAndValidated() throws Exception {
        File base=config(); KOMEConfigRegistry.load(base); assertFalse(KOMEConfigRegistry.siege().getActiveSiegeCheckInWindowMinutes().isPresent());
        File configured=config(); write(configured,"siege","activeSiegeCheckInWindowMinutes","45"); KOMEConfigRegistry.ValidatedConfig candidate=KOMEConfigRegistry.readValidated(configured); assertEquals(45,candidate.getSiege().getActiveSiegeCheckInWindowMinutes().getAsInt());
        assertEntry(KOMEConfigChangeSet.compare(KOMEConfigRegistry.currentValidated(),candidate),"siege","activeSiegeCheckInWindowMinutes","TBD","45");
        assertEntry(KOMEConfigChangeSet.compare(candidate,KOMEConfigRegistry.currentValidated()),"siege","activeSiegeCheckInWindowMinutes","45","TBD");
        try { File bad=config(); write(bad,"siege","activeSiegeCheckInWindowMinutes","0"); KOMEConfigRegistry.readValidated(bad); fail(); } catch (KOMEConfigValidationException expected) { }
        try { File bad=config(); write(bad,"siege","activeSiegeCheckInWindowMinutes","-1"); KOMEConfigRegistry.readValidated(bad); fail(); } catch (KOMEConfigValidationException expected) { }
    }

    @Test public void siegeLocksAreExactSortedAndImmutable() throws Exception {
        File base=config(); KOMEConfigRegistry.load(base); File candidate=config(); write(candidate,"movement","footOrMixedTilesPerDay","2"); write(candidate,"movement","fullyMountedTilesPerDay","3"); write(candidate,"battle","responseLevel1Minutes","10"); write(candidate,"battle","responseLevel2Minutes","20"); write(candidate,"battle","responseLevel3Minutes","30");
        KOMEConfigChangeSet diff=KOMEConfigChangeSet.compare(KOMEConfigRegistry.currentValidated(),KOMEConfigRegistry.readValidated(candidate));
        ChangeDecision blocked=KOMEConfigChangeGuard.evaluate(diff,new Activity(false,true,"movement.footOrMixedTilesPerDay","battle.responseLevel1Minutes"));
        assertFalse(blocked.isAllowed()); assertEquals(Arrays.asList("battle.responseLevel1Minutes","movement.footOrMixedTilesPerDay"),blocked.getBlockingKeys());
        try { blocked.getBlockingKeys().clear(); fail(); } catch (UnsupportedOperationException expected) { }
        assertTrue(KOMEConfigChangeGuard.evaluate(diff,new Activity(false,true,"siege.exteriorMarginBlocks")).isAllowed());
        assertTrue(KOMEConfigChangeGuard.evaluate(diff,new Activity(false,false,"movement.footOrMixedTilesPerDay")).isAllowed());
    }

    private Object[] surfaces(){return new Object[]{KOMEConfigRegistry.dailyBatch(),KOMEConfigRegistry.population(),KOMEConfigRegistry.movement(),KOMEConfigRegistry.battle(),KOMEConfigRegistry.muster(),KOMEConfigRegistry.siege(),KOMEConfigRegistry.battleSupport(),KOMEConfigRegistry.encirclement(),KOMEConfigRegistry.season()};}
    private void assertSurfaces(Object[] expected){assertArrayEquals(expected,surfaces());}
    private static void assertEntry(KOMEConfigChangeSet set,String c,String k,String o,String n){for(KOMEConfigChangeSet.Entry e:set.getEntries())if(c.equals(e.getCategory())&&k.equals(e.getKey())){assertEquals(o,e.getOldValue());assertEquals(n,e.getNewValue());return;}fail();}
    private static void assertValue(List<KOMEConfigInspection.EffectiveValue> values,String key,String expected){for(KOMEConfigInspection.EffectiveValue v:values)if(key.equals(v.getCategory()+"."+v.getKey())){assertEquals(expected,v.getValue());return;}fail();}
    private File config() throws Exception{return new File(temporaryFolder.newFolder(),"kome.cfg");}
    private static void write(File file,String category,String key,String value){Configuration c=new Configuration(file);c.load();c.get(category,key,"default").set(value);c.save();}
    private static final class Activity implements KOMEConfigRegistry.RuntimeActivity {final boolean daily,siege;final Collection<String> keys;Activity(boolean daily,boolean siege,String...keys){this.daily=daily;this.siege=siege;this.keys=Arrays.asList(keys);}public boolean isDailyTransactionInProgress(){return daily;}public boolean isActiveSiegeInProgress(){return siege;}public Collection<String> getActiveSiegeLockedConfigKeys(){return keys;}}
}
