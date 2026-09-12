package kome.common.data;

import kome.common.config.KOMEConfigRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import io.netty.buffer.Unpooled;
import org.junit.Test;
import java.time.Instant;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.OptionalInt;
import static org.junit.Assert.*;

/** Deterministic KOM-7 Slice-2 payout regression coverage; no runtime clock is used. */
public class KOMEPopulationPayoutProcessorTest {
    @Test public void halfPopulationPerDayPaysOnSecondBoundaryAndIsIdempotent() {
        KOMEWorldData data = world("gondor", 10); Instant now = Instant.parse("2026-01-10T18:00:00Z");
        KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, now);
        Instant first = KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
        KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, first);
        assertEquals(0, KOMEPopulationService.getAvailablePopulation(data, "gondor")); assertEquals(Long.valueOf(500000L), data.populationPayoutRemainders.get("gondor"));
        Instant second = KOMEPopulationPayoutProcessor.nextBoundary(first);
        KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, second);
        assertEquals(1, KOMEPopulationService.getAvailablePopulation(data, "gondor")); assertFalse(data.populationPayoutRemainders.containsKey("gondor"));
        long boundary = data.lastPopulationPayoutBoundaryMillis; KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, second);
        assertEquals(1, KOMEPopulationService.getAvailablePopulation(data, "gondor")); assertEquals(boundary, data.lastPopulationPayoutBoundaryMillis);
    }

    @Test public void remaindersAndBoundarySurviveRestart() {
        KOMEWorldData data = world("gondor", 1); Instant now = Instant.parse("2026-01-10T18:00:00Z");
        KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, now); Instant due = KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
        KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, due); NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag);
        KOMEWorldData restored = new KOMEWorldData("restored"); restored.readFromNBT(tag);
        assertTrue(restored.populationPayoutInitialized); assertEquals(due.toEpochMilli(), restored.lastPopulationPayoutBoundaryMillis); assertEquals(Long.valueOf(50000L), restored.populationPayoutRemainders.get("gondor"));
        KOMEPopulationPayoutProcessor.processLiveDueBoundaries(restored, due); assertEquals(0, KOMEPopulationService.getAvailablePopulation(restored, "gondor"));
    }

    @Test public void multipleFactionsAndCapturedAndDefensiveRemainIndependent() {
        KOMEWorldData data = world("gondor", 10); add(data, "rohan", KOMEBuildType.NORMAL, 20); add(data, "mordor", KOMEBuildType.DEFENSIVE, 40);
        data.conquestTiles.get(KOMEConquestTile.normalizeId("T-rohan")).claim("gondor", 0L); Instant now = Instant.parse("2026-01-10T18:00:00Z");
        KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data, now); Instant due = KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis)); KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data, due);
        assertEquals(0, KOMEPopulationService.getAvailablePopulation(data,"gondor")); assertEquals(0, KOMEPopulationService.getAvailablePopulation(data,"rohan")); assertEquals(0, KOMEPopulationService.getAvailablePopulation(data,"mordor"));
        assertEquals(Long.valueOf(500000L), data.populationPayoutRemainders.get("gondor")); assertFalse(data.populationPayoutRemainders.containsKey("rohan"));
    }

    @Test public void freshInitializationNeverPaysRetroactivelyAndFutureBoundaryPays() {
        for (Instant now : new Instant[]{Instant.parse("2026-01-10T01:00:00Z"), Instant.parse("2026-01-10T23:00:00Z")}) {
            KOMEWorldData data = world("gondor",20); KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data,now); assertEquals(0,KOMEPopulationService.getAvailablePopulation(data,"gondor"));
            KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data,KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis))); assertEquals(1,KOMEPopulationService.getAvailablePopulation(data,"gondor"));
        }
    }

    @Test public void dstBoundariesFollowLocalCalendarRatherThanTwentyFourHours() {
        Instant spring = Instant.parse("2026-03-08T12:00:00Z"); Instant fall = Instant.parse("2026-11-01T12:00:00Z");
        Instant springBoundary = KOMEPopulationPayoutProcessor.latestBoundaryAtOrBefore(spring); Instant springNext = KOMEPopulationPayoutProcessor.nextBoundary(springBoundary);
        Instant fallBoundary = KOMEPopulationPayoutProcessor.latestBoundaryAtOrBefore(fall); Instant fallNext = KOMEPopulationPayoutProcessor.nextBoundary(fallBoundary);
        assertNotEquals(86400000L, springNext.toEpochMilli()-springBoundary.toEpochMilli()); assertNotEquals(86400000L, fallNext.toEpochMilli()-fallBoundary.toEpochMilli());
    }

    @Test public void overflowFailsBeforeAnyFactionMutation() {
        KOMEWorldData data = world("gondor",20); add(data,"rohan",KOMEBuildType.NORMAL,20); KOMEPopulationService.grant(data,"rohan",Integer.MAX_VALUE);
        Instant now=Instant.parse("2026-01-10T18:00:00Z"); KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data,now); Instant due=KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis)); long prior=data.lastPopulationPayoutBoundaryMillis;
        KOMEPopulationPayoutProcessor.Result result=KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data,due);
        assertFalse(result.success); assertEquals(0,KOMEPopulationService.getAvailablePopulation(data,"gondor")); assertEquals(Integer.MAX_VALUE,KOMEPopulationService.getAvailablePopulation(data,"rohan")); assertEquals(prior,data.lastPopulationPayoutBoundaryMillis);
    }

    @Test public void invalidPersistedRemaindersAreDiscardedDeterministically() {
        KOMEWorldData data=new KOMEWorldData("x"); NBTTagCompound nbt=new NBTTagCompound(); nbt.setBoolean("PopulationPayoutInitialized",true); NBTTagList list=new NBTTagList();
        for(long value:new long[]{-1L,KOMEPopulationRate.SCALE}) { NBTTagCompound e=new NBTTagCompound();e.setString("Faction","gondor");e.setLong("RemainderUnits",value);list.appendTag(e); } nbt.setTag("PopulationPayoutRemainders",list); data.readFromNBT(nbt);
        assertTrue(data.populationPayoutRemainders.isEmpty());
    }

    @Test public void startupCatchUpProcessesEachMissedBoundaryWithFractionalRemainders() throws Exception {
        withPopulationSettings(true, false, null, 10, new Checked() { public void run() {
            KOMEWorldData data=world("gondor",10); Instant first=Instant.parse("2026-01-10T02:00:00Z");
            KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data,first);
            Instant due1=KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
            Instant due2=KOMEPopulationPayoutProcessor.nextBoundary(due1);
            KOMEPopulationPayoutProcessor.Result result=KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data,due2);
            assertEquals(2,result.factions.size()); assertEquals(1,KOMEPopulationService.getAvailablePopulation(data,"gondor"));
            assertFalse(data.populationPayoutRemainders.containsKey("gondor")); assertEquals(due2.toEpochMilli(),data.lastPopulationPayoutBoundaryMillis);
        }});
    }

    @Test public void disabledStartupCatchUpSkipsButLaterLiveBoundaryPays() throws Exception {
        withPopulationSettings(false, false, null, 10, new Checked() { public void run() {
            KOMEWorldData data=world("gondor",20); Instant first=Instant.parse("2026-01-10T02:00:00Z");
            KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data,first);
            Instant missed1=KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
            Instant missed2=KOMEPopulationPayoutProcessor.nextBoundary(missed1);
            KOMEPopulationPayoutProcessor.Result skipped=KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data,missed2);
            assertTrue(skipped.skipped); assertEquals(0,KOMEPopulationService.getAvailablePopulation(data,"gondor")); assertEquals(missed2.toEpochMilli(),data.lastPopulationPayoutBoundaryMillis);
            Instant live=KOMEPopulationPayoutProcessor.nextBoundary(missed2);
            KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data,live);
            assertEquals(1,KOMEPopulationService.getAvailablePopulation(data,"gondor")); assertEquals(live.toEpochMilli(),data.lastPopulationPayoutBoundaryMillis);
        }});
    }

    @Test public void capBehaviorDiscardsBlockedWholePopulationAndRetainsFraction() throws Exception {
        withPopulationSettings(true, false, null, 10, new Checked() { public void run() {
            KOMEWorldData data=world("gondor",70); Instant due=initializeAndNext(data);
            KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data,due);
            assertEquals(3,KOMEPopulationService.getAvailablePopulation(data,"gondor"));
        }});
        withPopulationSettings(true, true, Integer.valueOf(10), 10, new Checked() { public void run() {
            KOMEWorldData data=world("gondor",70); KOMEPopulationService.grant(data,"gondor",9); Instant due=initializeAndNext(data);
            KOMEPopulationPayoutProcessor.Result result=KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data,due);
            KOMEPopulationPayoutProcessor.FactionResult row=result.factions.get(0);
            assertEquals(10,KOMEPopulationService.getAvailablePopulation(data,"gondor")); assertEquals(3500000L,row.rate); assertEquals(0L,row.priorRemainder); assertEquals(3,row.generated); assertEquals(1,row.granted); assertEquals(2L,row.capBlocked); assertEquals(500000L,row.remainder);
            assertEquals(Long.valueOf(500000L),data.populationPayoutRemainders.get("gondor")); assertEquals(due.toEpochMilli(),data.lastPopulationPayoutBoundaryMillis);
            KOMEWorldData above=world("rohan",70); KOMEPopulationService.grant(above,"rohan",12); Instant aboveDue=initializeAndNext(above);
            KOMEPopulationPayoutProcessor.processLiveDueBoundaries(above,aboveDue);
            assertEquals(12,KOMEPopulationService.getAvailablePopulation(above,"rohan"));
        }});
    }

    @Test public void aggregateFactionRateIsUsedInsteadOfIndividuallyRoundedBuildRows() throws Exception {
        withPopulationSettings(true, false, null, 3, new Checked() { public void run() {
            KOMEWorldData data=world("gondor",1); add(data,"gondor-two",KOMEBuildType.NORMAL,1);
            KOMEPlayerBuild second=data.builds.get("B-gondor-two"); second.populationFaction="gondor";
            data.conquestTiles.get(KOMEConquestTile.normalizeId("T-gondor-two")).claim("gondor",0L);
            assertEquals(333333L,KOMEPopulationService.getDailyPopulationRate(data,"gondor").getFixedUnitsPerDay());
            Instant due=initializeAndNext(data);
            for(int i=0;i<3;i++) { KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data,due); due=KOMEPopulationPayoutProcessor.nextBoundary(due); }
            assertEquals(0,KOMEPopulationService.getAvailablePopulation(data,"gondor")); assertEquals(Long.valueOf(999999L),data.populationPayoutRemainders.get("gondor"));
        }});
    }

    @Test public void payoutDoesNotMutateBuildOrContributionState() {
        KOMEWorldData data=world("gondor",20); KOMEPlayerBuild build=data.builds.get("B-gondor"); KOMEBuildContribution contribution=build.contributions.get(0);
        KOMEBuildType type=build.type; boolean active=build.active; String faction=build.populationFaction; int count=build.contributions.size(); int halfHours=contribution.halfHours; String status=contribution.status;
        Instant due=initializeAndNext(data); KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data,due);
        assertEquals(type,build.type); assertEquals(active,build.active); assertEquals(faction,build.populationFaction); assertEquals(count,build.contributions.size()); assertEquals(halfHours,contribution.halfHours); assertEquals(status,contribution.status);
    }

    @Test public void enabledCapPreventsOtherwiseOverflowingGrantWithoutFailure() throws Exception {
        withPopulationSettings(true, true, Integer.valueOf(Integer.MAX_VALUE), 10, new Checked() { public void run() {
            KOMEWorldData data=world("gondor",20); KOMEPopulationService.grant(data,"gondor",Integer.MAX_VALUE);
            KOMEPopulationPayoutProcessor.Result result=KOMEPopulationPayoutProcessor.processLiveDueBoundaries(data,initializeAndNext(data));
            assertTrue(result.success); assertEquals(Integer.MAX_VALUE,KOMEPopulationService.getAvailablePopulation(data,"gondor")); assertEquals(1L,result.factions.get(0).capBlocked);
        }});
    }

    @Test public void runtimeStartupRunsOnceAndStalledLiveChecksProcessEveryDueBoundary() {
        KOMEWorldData data=world("gondor",10); KOMEPopulationPayoutRuntime runtime=new KOMEPopulationPayoutRuntime();
        Instant start=Instant.parse("2026-01-10T02:00:00Z"); assertNotNull(runtime.onStartup(data,start)); assertNull(runtime.onStartup(data,start));
        Instant first=KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));
        Instant third=KOMEPopulationPayoutProcessor.nextBoundary(KOMEPopulationPayoutProcessor.nextBoundary(first));
        KOMEPopulationPayoutProcessor.Result result=runtime.onLiveCheck(data,third);
        assertEquals(3,result.factions.size()); assertEquals(1,KOMEPopulationService.getAvailablePopulation(data,"gondor")); assertEquals(Long.valueOf(500000L),data.populationPayoutRemainders.get("gondor"));
        long boundary=data.lastPopulationPayoutBoundaryMillis; runtime.onLiveCheck(data,third); assertEquals(boundary,data.lastPopulationPayoutBoundaryMillis);
    }

    @Test public void rateFormattingAndPopulationPacketPreserveFractionalCanonicalRate() {
        assertEquals("1",new KOMEPopulationRate(1000000L).formatPerDay()); assertEquals("0.5",new KOMEPopulationRate(500000L).formatPerDay()); assertEquals("0.05",new KOMEPopulationRate(50000L).formatPerDay());
        kome.common.network.KOMEPacketPopulationGui sent=new kome.common.network.KOMEPacketPopulationGui(); sent.viewerFaction="gondor"; sent.availablePopulation=4; sent.activePopulation=2; sent.dailyPopulationRateUnits=50000L;
        io.netty.buffer.ByteBuf bytes=Unpooled.buffer(); sent.toBytes(bytes); kome.common.network.KOMEPacketPopulationGui received=new kome.common.network.KOMEPacketPopulationGui(); received.fromBytes(bytes);
        assertEquals("gondor",received.viewerFaction); assertEquals(4,received.availablePopulation); assertEquals(2,received.activePopulation); assertEquals(50000L,received.dailyPopulationRateUnits);
    }

    private static KOMEWorldData world(String faction,int halfHours){KOMEWorldData d=new KOMEWorldData("payout");add(d,faction,KOMEBuildType.NORMAL,halfHours);return d;}
    private static void add(KOMEWorldData d,String faction,KOMEBuildType type,int hours){String tile="T-"+faction;KOMEConquestTile t=new KOMEConquestTile(tile);t.claim(faction,0L);d.conquestTiles.put(t.id,t);KOMEPlayerBuild b=new KOMEPlayerBuild();b.id="B-"+faction;b.tileId=t.id;b.populationFaction=faction;b.type=type;b.active=true;KOMEBuildContribution c=new KOMEBuildContribution();c.id="H";c.halfHours=hours;c.status=KOMEBuildContribution.APPROVED;b.contributions.add(c);d.builds.put(b.id,b);}
    private static Instant initializeAndNext(KOMEWorldData data){KOMEPopulationPayoutProcessor.initializeOrProcessStartup(data,Instant.parse("2026-01-10T02:00:00Z"));return KOMEPopulationPayoutProcessor.nextBoundary(Instant.ofEpochMilli(data.lastPopulationPayoutBoundaryMillis));}
    private interface Checked { void run() throws Exception; }
    private static void withPopulationSettings(boolean catchUp,boolean cap,Integer capValue,int hours,Checked body) throws Exception {
        KOMEConfigRegistry.ValidatedConfig config=KOMEConfigRegistry.currentValidated(); Field field=KOMEConfigRegistry.ValidatedConfig.class.getDeclaredField("population"); field.setAccessible(true); Object prior=field.get(config);
        Constructor<KOMEConfigRegistry.PopulationSettings> constructor=KOMEConfigRegistry.PopulationSettings.class.getDeclaredConstructor(int.class,double.class,boolean.class,boolean.class,OptionalInt.class,boolean.class); constructor.setAccessible(true);
        KOMEConfigRegistry.PopulationSettings old=(KOMEConfigRegistry.PopulationSettings)prior;
        try { field.set(config,constructor.newInstance(hours,old.getCapturedBuildMultiplier(),catchUp,cap,capValue==null?OptionalInt.empty():OptionalInt.of(capValue.intValue()),old.isEncirclementPopulationSuppressionEnabled())); body.run(); }
        finally { field.set(config,prior); }
    }
}
