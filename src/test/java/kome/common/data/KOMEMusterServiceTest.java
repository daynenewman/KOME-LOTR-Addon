package kome.common.data;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEMusterServiceTest {
    @org.junit.Rule public final KOMETileTestResources geometry = new KOMETileTestResources();
    private final UUID ruler = UUID.fromString("00000000-0000-0000-0000-000000000011");
    private static final long NOW = 1000L;
    private static final BigInteger SCALE = BigInteger.valueOf(KOMEPopulationRate.SCALE);

    private static final class TestWorld extends KOMEWorldData {
        boolean observeTick, failPopulation, sawMusterAfterMovement, sawPopulationAfterMuster;
        KOMEArmyMovementOrder observedMovement;
        TestWorld() { super("muster"); }
        @Override public Set<String> getRouteNeighbors(String tile) {
            Set<String> result = new HashSet<String>();
            for (KOMEConquestRouteEdge edge : routeEdges.values()) if (edge.connects(tile)) result.add(edge.other(tile));
            return result;
        }
        @Override public void markDirty() {
            if (observeTick) {
                KOMEMusterRecord muster = civilianMusters.get("gondor|" + warSeason.seasonId);
                if (muster.getStatus() == KOMEMusterRecord.Status.PENDING_TBD) {
                    if (observedMovement != null) {
                        assertEquals(0, observedMovement.dailyStepsRemaining); // no company authority in this arrival-only fixture
                        assertEquals(KOMEArmyMovementOrder.ARRIVED, observedMovement.status);
                    }
                    sawMusterAfterMovement = true;
                }
                if (KOMEPopulationService.getAvailablePopulationCenti(this, "mordor") > 0L) {
                    assertTrue("Muster must run before live population publication", sawMusterAfterMovement);
                    sawPopulationAfterMuster = true;
                    if (failPopulation) throw new IllegalStateException("injected population failure");
                }
                if (warSeason.isFactionDefeated("gondor"))
                    assertTrue("Defeat must run after successful population publication", sawPopulationAfterMuster);
            }
            super.markDirty();
        }
    }
    private TestWorld world(String faction) {
        TestWorld data = new TestWorld(); data.initializeIntegratedWorld();
        data.routeEdges.clear();
        data.writeFactionKingRecord(faction, ruler, "Ruler");
        data.warSeason.recordLegalConflict(NOW, 0L);
        KOMEWar war = new KOMEWar(); war.id = "W1";
        war.sideOneFactions.add(faction); war.sideTwoFactions.add("mordor"); data.wars.put(war.id, war);
        String capital = KOMEFactionCapitalService.getCapitalTileId(data, faction);
        KOMEConquestTile tile = data.conquestTiles.get(capital); tile.claim(faction, NOW);
        KOMEPlayerBuild build = new KOMEPlayerBuild(); build.id = "B1"; build.active = true;
        build.type = KOMEBuildType.NORMAL; build.tileId = capital; build.populationFaction = faction;
        KOMEBuildContribution c = new KOMEBuildContribution(); c.id = "H1"; c.centiHours = 10_000L;
        c.status = KOMEBuildContribution.APPROVED; build.contributions.add(c);
        build.developedNativeCentiHours = c.centiHours; data.builds.put(build.id, build);
        enemy(data, "T001", "mordor");
        edge(data, capital, "T002", KOMEConquestRouteEdge.OPEN);
        edge(data, "T002", "T001", KOMEConquestRouteEdge.BRIDGE);
        return data;
    }
    private static void edge(KOMEWorldData data, String a, String b, String type) {
        KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge(a, b, type);
        data.routeEdges.put(KOMEConquestRouteEdge.key(a,b), edge);
    }
    private static KOMEArmyCompany enemy(KOMEWorldData data, String tile, String faction) {
        KOMEHiredUnitRecord unit = new KOMEHiredUnitRecord(); unit.entity = UUID.randomUUID(); unit.owner = UUID.randomUUID();
        KOMEHiredUnitClassification.assignForCampaignWorkflow(unit);
        unit.unitFaction = faction; unit.populationOwningFaction = faction; unit.currentTile = tile;
        unit.companyId = "C1"; data.hiredUnits.put(unit.entity, unit);
        KOMEArmyCompany company = new KOMEArmyCompany(); company.id = "C1"; company.owner = unit.owner;
        company.faction = faction; company.nativeFaction = faction; company.currentTile = tile;
        company.totalPopulation = unit.cost; company.groundPopulation = unit.cost; company.units.add(unit.entity);
        data.armyCompanies.put(company.id, company); return company;
    }
    private static KOMEMusterRoster.Unit unit(String key, String faction, int cost, boolean mounted, int weight) {
        return new KOMEMusterRoster.Unit(key, faction, "lotr." + key, mounted ? "lotr.Horse" : "", cost, weight);
    }
    private static KOMEMusterService.RosterSource source(final String faction) {
        return new KOMEMusterService.RosterSource() {
            public List<KOMEMusterRoster.Unit> resolve(String ignored) {
                return Arrays.asList(unit("reserve", faction, 20, "rohan".equals(faction), 5),
                    unit("archer", faction, 30, "rohan".equals(faction), 3));
            }
        };
    }
    private KOMEMusterService.Result call(KOMEWorldData data, String faction) {
        return KOMEMusterService.call(data, faction, ruler, NOW, source(faction), 42L);
    }
    private static KOMEWorldData restart(KOMEWorldData data) {
        NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag);
        KOMEWorldData restored = new KOMEWorldData("restart"); restored.readFromNBT(tag); return restored;
    }
    @Test public void canonicalRateBudgetAndCallDoNotChangeBankOrPlayerCompanies() {
        TestWorld data = world("gondor"); data.grantFactionPopulationCenti("gondor", 12345L);
        int companies = data.armyCompanies.size(), units = data.hiredUnits.size();
        KOMEMusterService.Result result = call(data, "gondor");
        assertEquals(result.reason, KOMEMusterService.Code.SUCCESS, result.code);
        assertEquals(SCALE.multiply(BigInteger.TEN), result.record.rateUnits);
        assertEquals(SCALE.multiply(BigInteger.valueOf(210)), result.record.budgetUnits);
        assertTrue(result.record.spentUnits.compareTo(result.record.budgetUnits) <= 0);
        assertEquals(12345L, KOMEPopulationService.getAvailablePopulationCenti(data,"gondor"));
        assertEquals(companies, data.armyCompanies.size()); assertEquals(units, data.hiredUnits.size());
        KOMEAuditEntry audit = data.centralAudit.get(data.centralAudit.size()-1);
        assertTrue(audit.details.contains("seed=42")); assertTrue(audit.details.contains("roster=" + result.record.rosterSummary()));
        assertEquals(KOMEMusterService.Code.ALREADY_USED, call(data,"gondor").code);
    }
    @Test public void eligibilityRejectsWrongActorPhaseMissingCapitalAndNoEnemy() {
        TestWorld data = world("gondor");
        assertEquals(KOMEMusterService.Code.NOT_AUTHORIZED,
            KOMEMusterService.call(data,"gondor",UUID.randomUUID(),NOW,source("gondor")).code);
        for (KOMEWarSeasonState.Phase phase : Arrays.asList(KOMEWarSeasonState.Phase.MAINTENANCE,
                KOMEWarSeasonState.Phase.PRE_WAR,KOMEWarSeasonState.Phase.RESET)) {
            data.warSeason.phase=phase; assertEquals(KOMEMusterService.Code.WRONG_PHASE,call(data,"gondor").code);
        }
        data.warSeason.phase=KOMEWarSeasonState.Phase.WAR;
        KOMEFactionCapitalRecord capital=data.factionCapitals.remove("gondor");
        assertEquals(KOMEMusterService.Code.NO_CAPITAL,call(data,"gondor").code); data.factionCapitals.put("gondor",capital);
        data.wars.clear(); assertEquals(KOMEMusterService.Code.NO_THREAT,call(data,"gondor").code);
        assertTrue(data.civilianMusters.isEmpty());
    }
    @Test public void strategicGraphCountsZeroOneTwoButNotThreeAndBlockedEdges() {
        TestWorld data=world("gondor"); String capital=KOMEFactionCapitalService.getCapitalTileId(data,"gondor");
        assertTrue(KOMEMusterService.hasThreat(data,"gondor",capital,2));
        assertFalse(KOMEMusterService.hasThreat(data,"gondor",capital,1));
        edge(data,"T002","T001",KOMEConquestRouteEdge.BLOCKED);
        assertFalse(KOMEMusterService.hasThreat(data,"gondor",capital,2));
        edge(data,"T002","T001",KOMEConquestRouteEdge.MOUNTAIN_PASS);
        edge(data,"T001","T003",KOMEConquestRouteEdge.OPEN);
        relocateEnemy(data,"T003"); assertFalse(KOMEMusterService.hasThreat(data,"gondor",capital,2));
        relocateEnemy(data,"T002"); assertTrue(KOMEMusterService.hasThreat(data,"gondor",capital,1));
        relocateEnemy(data,capital); assertTrue(KOMEMusterService.hasThreat(data,"gondor",capital,0));
    }
    private static void relocateEnemy(KOMEWorldData data,String tile) {
        KOMEArmyCompany c=data.armyCompanies.get("C1");c.currentTile=tile;data.hiredUnits.get(c.units.get(0)).currentTile=tile;
    }
    @Test public void invalidCompanyAndOrdinaryHiresCannotTriggerMuster() {
        TestWorld data=world("gondor");String capital=KOMEFactionCapitalService.getCapitalTileId(data,"gondor");
        KOMEArmyCompany company=data.armyCompanies.get("C1");KOMEHiredUnitRecord unit=data.hiredUnits.get(company.units.get(0));
        unit.currentTile="T003";assertFalse(KOMEMusterService.hasThreat(data,"gondor",capital,2));
        unit.currentTile=company.currentTile;unit.assignPersistedUnitClass(KOMEHiredUnitClass.ORDINARY);
        assertFalse(KOMEMusterService.hasThreat(data,"gondor",capital,2));
        company.units.clear();assertFalse(KOMEMusterService.hasThreat(data,"gondor",capital,2));
    }
    @Test public void exactFractionalBudgetNeverRoundsUpAndStopsOnlyWhenNoUnitFits() {
        KOMEMusterRoster.Unit u=unit("reserve","gondor",20,false,1);
        BigInteger cost=SCALE.multiply(BigInteger.valueOf(20));
        assertTrue(KOMEMusterRoster.select("gondor",cost.subtract(BigInteger.ONE),7,Arrays.asList(u)).counts.isEmpty());
        assertEquals(BigInteger.ONE,KOMEMusterRoster.select("gondor",cost,7,Arrays.asList(u)).counts.get(u));
        for (int seed=0;seed<200;seed++) {
            BigInteger budget=BigInteger.valueOf(210_123_456L);
            KOMEMusterRoster.Selection selection=KOMEMusterRoster.select("gondor",budget,seed,
                Arrays.asList(u,unit("archer","gondor",30,false,7)));
            assertTrue(selection.spentUnits.compareTo(budget)<=0);
            assertTrue(budget.subtract(selection.spentUnits).compareTo(cost)<0);
        }
    }
    @Test public void weightsSeedOrderingAndFactionRestrictionsAreDeterministic() {
        KOMEMusterRoster.Unit foot=unit("foot","rohan",20,false,100);
        KOMEMusterRoster.Unit rider=unit("rider","rohan",45,true,1);
        KOMEMusterRoster.Unit foreign=unit("foreign","gondor",1,true,100);
        KOMEMusterRoster.Unit disabled=unit("disabled","rohan",1,true,0);
        KOMEMusterRoster.Selection result=KOMEMusterRoster.select("rohan",SCALE.multiply(BigInteger.valueOf(100)),42,
            Arrays.asList(foot,rider,foreign,disabled));
        assertEquals(1,result.counts.size());assertEquals(BigInteger.valueOf(2),result.counts.get(rider));
        KOMEMusterRoster.Unit a=unit("a","gondor",1,false,9),b=unit("b","gondor",1,false,1);
        KOMEMusterRoster.Selection first=KOMEMusterRoster.select("gondor",SCALE.multiply(BigInteger.valueOf(1000)),42,Arrays.asList(a,b));
        assertEquals(first.counts,KOMEMusterRoster.select("gondor",SCALE.multiply(BigInteger.valueOf(1000)),42,Arrays.asList(b,a)).counts);
        assertTrue(first.counts.get(a).compareTo(first.counts.get(b))>0);
    }
    @Test public void enormousExactBudgetRetainsFullPrecisionWithoutAnImplicitPopulationCap() {
        BigInteger budget=BigInteger.TEN.pow(35).add(BigInteger.valueOf(123));
        KOMEMusterRoster.Selection s=KOMEMusterRoster.select("gondor",budget,42,
            Arrays.asList(unit("a","gondor",20,false,5),unit("b","gondor",30,false,3)));
        assertTrue(s.spentUnits.compareTo(budget)<=0);
        assertTrue(budget.subtract(s.spentUnits).compareTo(SCALE.multiply(BigInteger.valueOf(20)))<0);
        assertEquals(budget.multiply(BigInteger.valueOf(21)),KOMEPopulationService.musterBudgetUnits(budget,21));
    }
    @Test public void zeroRateOrUnaffordableRosterDoesNotConsumeSeason() {
        TestWorld data=world("gondor"); data.builds.clear();
        assertEquals(KOMEMusterService.Code.NO_AFFORDABLE_ROSTER,call(data,"gondor").code);
        assertTrue(data.civilianMusters.isEmpty());
        data=world("gondor");
        assertEquals(KOMEMusterService.Code.NO_AFFORDABLE_ROSTER,
            KOMEMusterService.call(data,"gondor",ruler,NOW,new KOMEMusterService.RosterSource(){
                public List<KOMEMusterRoster.Unit> resolve(String faction){return Arrays.asList(unit("expensive",faction,211,false,1));}
            }).code);
        assertTrue(data.civilianMusters.isEmpty());assertTrue(call(data,"gondor").success());
    }
    @Test public void simultaneousCallsProduceExactlyOneRosterAuditAndSeasonUse() throws Exception {
        final TestWorld data=world("gondor"); final AtomicInteger successes=new AtomicInteger(),failures=new AtomicInteger();
        final CountDownLatch ready=new CountDownLatch(12),start=new CountDownLatch(1),done=new CountDownLatch(12);
        for(int i=0;i<12;i++)new Thread(new Runnable(){public void run(){
            ready.countDown();try{start.await();if(call(data,"gondor").success())successes.incrementAndGet();}
            catch(Throwable e){failures.incrementAndGet();}finally{done.countDown();}
        }}).start();
        assertTrue(ready.await(10,java.util.concurrent.TimeUnit.SECONDS));start.countDown();
        assertTrue(done.await(30,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,failures.get());
        assertEquals(1,successes.get());assertEquals(1,data.civilianMusters.size());
        assertEquals(1,data.centralAudit.stream().filter(e->"CALL".equals(e.action)&&"MUSTER".equals(e.domain)).count());
    }
    @Test public void snapshotsAndUsedSeasonPersistWithoutRerollAcrossRealRootRestart() {
        TestWorld data=world("gondor");KOMEMusterRecord original=call(data,"gondor").record;
        KOMEWorldData restored=restart(data);KOMEMusterRecord record=KOMEMusterService.records(restored,"gondor").get(0);
        assertEquals(original.writeToNBT(),record.writeToNBT());
        assertEquals(KOMEMusterService.Code.ALREADY_USED,call(restored,"gondor").code);
        restored.builds.clear();restored.factionCapitals.remove("gondor");
        assertEquals(original.budgetUnits,record.budgetUnits);assertEquals(original.capital.writeToNBT(),record.capital.writeToNBT());
    }

    @Test public void campaignTickOrdersMovementMusterPopulationAndDefeatAndRestartsWithoutDuplicates() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            TestWorld data = world("gondor"); KOMEMusterRecord muster = call(data, "gondor").record;
            data.conquestTiles.get(muster.capital.getCapitalTileId()).claim("mordor", NOW);
            KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime();
            assertTrue(KOMEEvents.processCampaignTick(data, null, NOW, runtime).success);
            assertFalse(data.warSeason.isFactionDefeated("gondor"));
            data.observedMovement = dueMovement(data, muster.dueAtMillis);
            data.observeTick = true;
            assertTrue(KOMEEvents.processCampaignTick(data, movementWorld(), muster.dueAtMillis, runtime).success);
            assertTrue(data.sawMusterAfterMovement); assertTrue(data.sawPopulationAfterMuster);
            assertEquals(500L, KOMEPopulationService.getAvailablePopulationCenti(data, "mordor"));
            assertEquals("CAPITAL_CONFLICT_STATE_UNKNOWN", muster.getPendingReason());
            assertEquals(muster.dueAtMillis, data.warSeason.factionDefeatedAt("gondor"));
            assertEquals(1L, auditCount(data, "MUSTER", "CALL"));
            assertEquals(1L, auditCount(data, "MUSTER", "PENDING"));
            assertEquals(1L, auditCount(data, "CAMPAIGN", "FACTION_DEFEAT"));

            KOMEWorldData restored = restartCombined(data, muster);
            KOMEPopulationPayoutRuntime restartedRuntime = new KOMEPopulationPayoutRuntime();
            assertTrue(KOMEEvents.processCampaignTick(restored, null, muster.dueAtMillis + 1L, restartedRuntime).success);
            assertTrue(KOMEEvents.processCampaignTick(restored, null, muster.dueAtMillis + 2L, restartedRuntime).success);
            assertEquals(muster.writeToNBT(), KOMEMusterService.records(restored, "gondor").get(0).writeToNBT());
            assertEquals(KOMEMusterService.Code.ALREADY_USED, call(restored, "gondor").code);
            assertEquals(1L, auditCount(restored, "MUSTER", "PENDING"));
            assertEquals(1L, auditCount(restored, "CAMPAIGN", "FACTION_DEFEAT"));

            // Existing test receipt authority; production deployment remains unavailable.
            Delivery delivery = new Delivery();
            assertEquals(1, KOMEMusterService.processDue(restored, muster.dueAtMillis + 3L, delivery));
            KOMEMusterRecord arrived = KOMEMusterService.records(restored, "gondor").get(0);
            KOMEWorldData secondRestart = restartCombined(restored, arrived);
            assertEquals(0, KOMEMusterService.processDue(secondRestart, muster.dueAtMillis + 4L, delivery));
            assertEquals(0, KOMEFactionDefeatService.reconcile(secondRestart, muster.dueAtMillis + 4L));
            assertEquals(1, delivery.deliveries);
            assertEquals(1L, auditCount(secondRestart, "MUSTER", "ARRIVE"));
            assertEquals(1L, auditCount(secondRestart, "CAMPAIGN", "FACTION_DEFEAT"));
            assertEquals(KOMEMusterService.Code.ALREADY_USED, call(secondRestart, "gondor").code);
        }
    }

    @Test public void failedLivePopulationRetainsProcessedMusterButDefersDefeatUntilSuccessfulRetry() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            TestWorld data = world("gondor"); KOMEMusterRecord muster = call(data, "gondor").record;
            data.conquestTiles.get(muster.capital.getCapitalTileId()).claim("mordor", NOW);
            KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime(message -> { });
            assertTrue(KOMEEvents.processCampaignTick(data, null, NOW, runtime).success);
            data.observeTick = true; data.failPopulation = true;
            KOMEPopulationPayoutProcessor.Result failed = KOMEEvents.processCampaignTick(data, null, muster.dueAtMillis, runtime);
            assertFalse(failed.success); assertTrue(failed.message.contains("injected population failure"));
            assertEquals("CAPITAL_CONFLICT_STATE_UNKNOWN", muster.getPendingReason());
            assertEquals(0L, KOMEPopulationService.getAvailablePopulationCenti(data, "mordor"));
            assertFalse(data.warSeason.isFactionDefeated("gondor"));
            assertEquals(0L, auditCount(data, "CAMPAIGN", "FACTION_DEFEAT"));
            data.failPopulation = false;
            assertTrue(KOMEEvents.processCampaignTick(data, null, muster.dueAtMillis, runtime).success);
            assertTrue(KOMEEvents.processCampaignTick(data, null, muster.dueAtMillis + 1L, runtime).success);
            assertEquals(500L, KOMEPopulationService.getAvailablePopulationCenti(data, "mordor"));
            assertEquals(muster.dueAtMillis, data.warSeason.factionDefeatedAt("gondor"));
            assertEquals(1L, auditCount(data, "MUSTER", "PENDING"));
            assertEquals(1L, auditCount(data, "CAMPAIGN", "FACTION_DEFEAT"));
            restartCombined(data, muster);
        }
    }

    @Test public void startupTickLeavesOverdueMovementMusterAndDefeatForTheLivePath() throws Exception {
        try (KOMEPopulationTestConfig ignored = new KOMEPopulationTestConfig()) {
            TestWorld data = world("gondor"); KOMEMusterRecord muster = call(data, "gondor").record;
            data.conquestTiles.get(muster.capital.getCapitalTileId()).claim("mordor", NOW);
            KOMEArmyMovementOrder movement = dueMovement(data, muster.dueAtMillis);
            KOMEPopulationPayoutRuntime runtime = new KOMEPopulationPayoutRuntime();
            assertTrue(KOMEEvents.processCampaignTick(data, movementWorld(), muster.dueAtMillis, runtime).success);
            assertTrue(runtime.hasStarted(data));
            assertEquals(0, movement.dailyStepsRemaining);
            assertEquals(KOMEArmyMovementOrder.MOVING, movement.status);
            assertTrue(movement.nextDailyStepMillis > muster.dueAtMillis);
            assertEquals(KOMEMusterRecord.Status.SCHEDULED, muster.getStatus());
            assertFalse(data.warSeason.isFactionDefeated("gondor"));
            assertEquals(0L, auditCount(data, "MUSTER", "PENDING"));
            assertEquals(0L, auditCount(data, "CAMPAIGN", "FACTION_DEFEAT"));
            assertTrue(KOMEEvents.processCampaignTick(data, movementWorld(), muster.dueAtMillis + 1L, runtime).success);
            assertEquals(KOMEArmyMovementOrder.ARRIVED, movement.status);
            assertEquals(KOMEMusterRecord.Status.PENDING_TBD, muster.getStatus());
            assertTrue(data.warSeason.isFactionDefeated("gondor"));
        }
    }

    private static long auditCount(KOMEWorldData data, String domain, String action) {
        return data.centralAudit.stream().filter(e -> domain.equals(e.domain) && action.equals(e.action)).count();
    }

    private static KOMEWorldData restartCombined(KOMEWorldData data, KOMEMusterRecord muster) {
        NBTTagCompound tag = new NBTTagCompound(); data.writeToNBT(tag);
        assertEquals(KOMEWorldData.KOME_DATA_SCHEMA_VERSION, tag.getInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY));
        assertEquals(1, tag.getInteger("MusterDataSchemaVersion"));
        assertEquals(1, tag.getTagList("CivilianMusters", 10).tagCount());
        assertEquals(1, tag.getCompoundTag("WarSeason").getTagList("FactionDefeats", 10).tagCount());
        KOMEWorldData restored = new KOMEWorldData("combined-restart"); restored.readFromNBT(tag);
        assertFalse(restored.isWriteBlocked());
        assertEquals(muster.writeToNBT(), KOMEMusterService.records(restored, "gondor").get(0).writeToNBT());
        assertEquals(data.warSeason.factionDefeatedAt("gondor"), restored.warSeason.factionDefeatedAt("gondor"));
        assertTrue(restored.warSeason.isFactionDefeated("gondor"));
        return restored;
    }

    private static KOMEArmyMovementOrder dueMovement(KOMEWorldData data, long due) {
        for (String id : Arrays.asList("T010", "T011")) {
            KOMEConquestTile tile = new KOMEConquestTile(id); tile.claim("gondor", NOW);
            tile.setAnchor(0, 10.0D, 64.0D, 20.0D); data.conquestTiles.put(id, tile);
        }
        KOMEArmyMovementOrder order = KOMEArmyMovementOrder.newRoute(1);
        order.id = "M1"; order.ownerFaction = "gondor"; order.status = KOMEArmyMovementOrder.MOVING;
        order.originTile = "T010"; order.currentTile = "T010"; order.currentStepOriginTile = "T010";
        order.destinationTile = "T011"; order.nextTile = "T011"; order.currentStepDestinationTile = "T011";
        order.routeTiles.add("T010"); order.routeTiles.add("T011"); order.traveledRouteTiles.add("T010");
        order.nextRouteIndex = 1; order.distanceTiles = 1; order.dailyStepsRemaining = 0;
        order.nextDailyStepMillis = due; order.arrivalMillis = due;
        order.arrivalX = 10.0D; order.arrivalY = 64.0D; order.arrivalZ = 20.0D;
        data.armyMovements.put(order.id, order); return order;
    }

    /** Inert world matching the existing movement integration fixture; no physical entities are spawned. */
    private static net.minecraft.world.World movementWorld() throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field singleton = unsafe.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        MovementWorld world = (MovementWorld) unsafe.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), MovementWorld.class);
        java.lang.reflect.Field provider = net.minecraft.world.World.class.getDeclaredField("provider"); provider.setAccessible(true);
        provider.set(world, new net.minecraft.world.WorldProviderSurface());
        world.loadedEntityList = new java.util.ArrayList<net.minecraft.entity.Entity>();
        world.playerEntities = new java.util.ArrayList(); return world;
    }
    private static final class MovementWorld extends net.minecraft.world.World {
        private MovementWorld() {
            super(null, "test", (net.minecraft.world.WorldProvider) null,
                (net.minecraft.world.WorldSettings) null, null);
        }
        @Override protected net.minecraft.world.chunk.IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public net.minecraft.entity.Entity getEntityByID(int id) { return null; }
        @Override public net.minecraft.world.chunk.IChunkProvider getChunkProvider() { return null; }
        @Override public net.minecraft.world.chunk.Chunk getChunkFromChunkCoords(int x, int z) { return null; }
        @Override public boolean blockExists(int x, int y, int z) { return true; }
    }
    @Test public void configDefaultsOverridesAndPersistedScheduleAreCallTimeValues() throws Exception {
        try(KOMEPopulationTestConfig config=new KOMEPopulationTestConfig()) {
            assertEquals(21,kome.common.config.KOMEConfigRegistry.muster().getBudgetDailyPopulationMultiplier());
            assertEquals(24,kome.common.config.KOMEConfigRegistry.muster().getArrivalDelayHours());
            config.set("muster.budgetDailyPopulationMultiplier","28","muster.arrivalDelayHours","12");
            KOMEMusterRecord record=call(world("gondor"),"gondor").record;
            assertEquals(SCALE.multiply(BigInteger.valueOf(280)),record.budgetUnits);assertEquals(NOW+43_200_000L,record.dueAtMillis);
            config.set("muster.arrivalDelayHours","24");assertEquals(NOW+43_200_000L,record.dueAtMillis);
        }
    }
    private static class Delivery implements KOMEMusterService.ArrivalAuthority {
        KOMEMusterService.CapitalState state=KOMEMusterService.CapitalState.CLEAR; int deliveries;
        public KOMEMusterService.CapitalState capitalState(KOMEWorldData data,KOMEFactionCapitalRecord capital){return state;}
        public String deliver(KOMEWorldData data,KOMEMusterRecord record){deliveries++;return "test-receipt:"+record.key();}
    }
    @Test public void arrivalIsNotEarlyAndIsExactlyOnceIncludingAfterRestart() {
        TestWorld data=world("gondor");KOMEMusterRecord record=call(data,"gondor").record;Delivery delivery=new Delivery();
        assertEquals(0,KOMEMusterService.processDue(data,record.dueAtMillis-1,delivery));
        assertEquals(1,KOMEMusterService.processDue(data,record.dueAtMillis,delivery));
        assertEquals(0,KOMEMusterService.processDue(data,record.dueAtMillis+1,delivery));
        assertEquals(0,KOMEMusterService.processDue(restart(data),record.dueAtMillis+2,delivery));assertEquals(1,delivery.deliveries);
    }
    @Test public void unknownOrEncircledArrivalRemainsPendingWithSameRosterAndDeadline() {
        TestWorld data=world("gondor");KOMEMusterRecord record=call(data,"gondor").record;
        String roster=record.rosterSummary();long due=record.dueAtMillis;Delivery delivery=new Delivery();
        assertEquals(0,KOMEMusterService.processDue(data,due));assertEquals("CAPITAL_CONFLICT_STATE_UNKNOWN",record.getPendingReason());
        int audits=data.centralAudit.size();KOMEMusterService.processDue(data,due+1);assertEquals(audits,data.centralAudit.size());
        delivery.state=KOMEMusterService.CapitalState.ENCIRCLED;
        assertEquals(0,KOMEMusterService.processDue(data,due,delivery));assertEquals(0,delivery.deliveries);
        assertTrue(record.getPendingReason().startsWith("ENCIRCLED_CAPITAL_POLICY_TBD"));
        KOMEWorldData restored=restart(data);KOMEMusterRecord pending=KOMEMusterService.records(restored,"gondor").get(0);
        assertEquals(roster,pending.rosterSummary());assertEquals(due,pending.dueAtMillis);assertEquals(KOMEMusterRecord.Status.PENDING_TBD,pending.getStatus());
        delivery.state=KOMEMusterService.CapitalState.CLEAR;assertEquals(1,KOMEMusterService.processDue(restored,due+1,delivery));
    }
    @Test public void evenConfiguredGarrisonOrReliefDoesNotInventEncircledDeployment() throws Exception {
        try(KOMEPopulationTestConfig config=new KOMEPopulationTestConfig()) {
            for(String policy:Arrays.asList("GARRISON","RELIEF")) {
                config.set("muster.encircledCapitalArrivalPolicy",policy);
                TestWorld data=world("gondor");KOMEMusterRecord record=call(data,"gondor").record;
                Delivery d=new Delivery();d.state=KOMEMusterService.CapitalState.ENCIRCLED;
                assertEquals(0,KOMEMusterService.processDue(data,record.dueAtMillis,d));assertEquals(0,d.deliveries);
                assertEquals("ENCIRCLED_CAPITAL_POLICY_TBD:"+policy,record.getPendingReason());
            }
        }
    }
    @Test public void seasonChangesAllowNewCallAndRetainOldPendingMusterWithoutWrongSeasonDelivery() {
        TestWorld data=world("gondor");KOMEMusterRecord old=call(data,"gondor").record;
        data.warSeason.phase=KOMEWarSeasonState.Phase.FINALE;assertTrue(data.warSeason.beginReset(NOW).allowed);
        assertTrue(data.warSeason.completeReset(NOW).allowed);assertTrue(data.warSeason.recordLegalConflict(NOW,0).allowed);
        assertTrue(call(data,"gondor").success());assertEquals(2,data.civilianMusters.size());
        Delivery delivery=new Delivery();assertEquals(1,KOMEMusterService.processDue(data,old.dueAtMillis,delivery));
        assertEquals("SEASON_POLICY_TBD",old.getPendingReason());assertEquals(1,delivery.deliveries);
        assertEquals(2,KOMEMusterService.records(restart(data),"gondor").size());
    }
    @Test public void invalidOrDuplicatePersistedRecordsFailClosedWithoutPublishing() {
        TestWorld data=world("gondor");call(data,"gondor");NBTTagCompound tag=new NBTTagCompound();data.writeToNBT(tag);
        NBTTagList list=tag.getTagList("CivilianMusters",10);list.appendTag(list.getCompoundTagAt(0).copy());
        KOMEWorldData loaded=new KOMEWorldData("bad");
        try{loaded.readFromNBT(tag);fail("duplicate accepted");}catch(IllegalStateException expected){assertTrue(loaded.isWriteBlocked());}
        assertTrue(loaded.civilianMusters.isEmpty());
        data.writeToNBT(tag);tag.getTagList("CivilianMusters",10).getCompoundTagAt(0).setString("SpentUnits","1");
        try{new KOMEWorldData("bad").readFromNBT(tag);fail("bad cost accepted");}catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("Muster cost"));}
    }
    @Test public void nativeDataCoversEveryFactionAndRohanOnlyReferencesMountedNativeTrades() {
        Set<String> factions=new HashSet<String>();
        for(KOMEMusterNativeRoster.Definition row:KOMEMusterNativeRoster.definitions()) {
            factions.add(row.faction);assertTrue(row.weight>0);
            if("rohan".equals(row.faction)){assertEquals("ROHIRRIM_MARSHAL",row.source);assertTrue(row.index==2||row.index==3);}
        }
        assertEquals(new HashSet<String>(KOMEAlliance.allFactionKeys()),factions);
    }
    @Test public void configurableWeightsAcceptDisableAndRejectForeignUnknownDuplicateOrNegativeKeys() throws Exception {
        try(KOMEPopulationTestConfig config=new KOMEPopulationTestConfig()) {
            String key="rohan:rohirrim_marshal:2";
            config.set("muster.rosterWeightOverrides",key+"=0,gondor:gondorian_captain:0=9");
            assertEquals(Integer.valueOf(0),kome.common.config.KOMEConfigRegistry.muster().getRosterWeightOverrides().get(key));
            assertEquals(Integer.valueOf(9),kome.common.config.KOMEConfigRegistry.muster().getRosterWeightOverrides().get("gondor:gondorian_captain:0"));
            for(String invalid:Arrays.asList(key+"=-1",key+"=1,"+key+"=2","foreign:rogue:0=1",key+"=1,",""+key+"=bad")) {
                Object prior=kome.common.config.KOMEConfigRegistry.currentValidated();
                try{config.set("muster.rosterWeightOverrides",invalid);fail("Invalid weights accepted: "+invalid);}
                catch(kome.common.config.KOMEConfigValidationException expected){assertSame(prior,kome.common.config.KOMEConfigRegistry.currentValidated());}
            }
        }
    }
    @Test public void failedOrUnconfirmedDeliveryLatchesAndRequiresExplicitReconciliation() {
        TestWorld data=world("gondor");KOMEMusterRecord record=call(data,"gondor").record;
        final AtomicInteger attempts=new AtomicInteger();
        KOMEMusterService.ArrivalAuthority failing=new Delivery(){
            @Override public String deliver(KOMEWorldData ignored,KOMEMusterRecord record){attempts.incrementAndGet();return "";}
        };
        assertEquals(0,KOMEMusterService.processDue(data,record.dueAtMillis,failing));
        assertEquals("DEPLOYMENT_CONFIRMATION_REQUIRED",record.getPendingReason());
        assertEquals(0,KOMEMusterService.processDue(data,record.dueAtMillis+1,failing));
        assertEquals(0,KOMEMusterService.processDue(restart(data),record.dueAtMillis+2,failing));assertEquals(1,attempts.get());
        assertTrue(KOMEMusterService.retryConfirmedDelivery(data,record.key()));
        Delivery delivery=new Delivery();assertEquals(1,KOMEMusterService.processDue(data,record.dueAtMillis+3,delivery));
        assertFalse(KOMEMusterService.retryConfirmedDelivery(data,record.key()));
    }
    @Test public void concurrentArrivalChecksDeliverOnlyOnce() throws Exception {
        final TestWorld data=world("gondor");final KOMEMusterRecord record=call(data,"gondor").record;
        final Delivery delivery=new Delivery();final CountDownLatch start=new CountDownLatch(1),done=new CountDownLatch(8);
        final AtomicInteger count=new AtomicInteger(),errors=new AtomicInteger();
        for(int i=0;i<8;i++)new Thread(new Runnable(){public void run(){try {
            start.await();count.addAndGet(KOMEMusterService.processDue(data,record.dueAtMillis,delivery));
        }catch(Throwable failure){errors.incrementAndGet();}finally{done.countDown();}}}).start();
        start.countDown();assertTrue(done.await(30,java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0,errors.get());assertEquals(1,count.get());assertEquals(1,delivery.deliveries);
    }
    @Test public void reentrantArrivalAndMidDeliveryRestartCannotDuplicateExternalEffects() {
        final TestWorld data=world("gondor");final KOMEMusterRecord record=call(data,"gondor").record;
        Delivery delivery=new Delivery(){@Override public String deliver(KOMEWorldData ignored,KOMEMusterRecord record){
            assertEquals("DEPLOYMENT_CONFIRMATION_REQUIRED",record.getPendingReason());
            assertEquals(0,KOMEMusterService.processDue(data,record.dueAtMillis,this));
            assertEquals(0,KOMEMusterService.processDue(restart(data),record.dueAtMillis,this));
            deliveries++;return "test-receipt:"+record.key();
        }};
        assertEquals(1,KOMEMusterService.processDue(data,record.dueAtMillis,delivery));assertEquals(1,delivery.deliveries);
    }
    @Test public void malformedListMissingSchemaAndFutureSeasonCannotResetSeasonUsage() {
        TestWorld data=world("gondor");call(data,"gondor");
        for(int malformed=0;malformed<3;malformed++) {
            NBTTagCompound tag=new NBTTagCompound();data.writeToNBT(tag);
            if(malformed==0){NBTTagList list=new NBTTagList();list.appendTag(new net.minecraft.nbt.NBTTagString("bad"));tag.setTag("CivilianMusters",list);}
            if(malformed==1)tag.removeTag("MusterDataSchemaVersion");
            if(malformed==2)tag.getTagList("CivilianMusters",10).getCompoundTagAt(0).setLong("Season",2L);
            KOMEWorldData loaded=new KOMEWorldData("invalid");
            try{loaded.readFromNBT(tag);fail("Malformed muster loaded");}catch(IllegalStateException expected){assertTrue(loaded.isWriteBlocked());}
            assertTrue(loaded.civilianMusters.isEmpty());
        }
    }
    @Test public void oldRootCannotSilentlyLoseMusterUseAndInvalidSaveLeavesDestinationUntouched() {
        TestWorld data=world("gondor");KOMEMusterRecord record=call(data,"gondor").record;
        NBTTagCompound root=new NBTTagCompound();data.writeToNBT(root);root.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY,5);
        try{new KOMEWorldData("old").readFromNBT(root);fail("old root accepted");}catch(IllegalStateException expected){assertTrue(expected.getMessage().contains("schema 5"));}
        data.civilianMusters.put("bad-key",record);NBTTagCompound destination=new NBTTagCompound();destination.setString("Sentinel","keep");
        NBTTagCompound before=(NBTTagCompound)destination.copy();
        try{data.writeToNBT(destination);fail("invalid identity saved");}catch(IllegalStateException expected){assertEquals(before,destination);}
    }
    @Test public void schemaSixConflictUpgradePreservesConsumedMusterAuthority() {
        TestWorld data=world("gondor");KOMEMusterRecord record=call(data,"gondor").record;
        NBTTagCompound root=new NBTTagCompound();data.writeToNBT(root);
        root.setInteger(KOMEWorldData.KOME_DATA_SCHEMA_KEY,6);
        root.removeTag(KOMEConflictPersistence.SCHEMA_KEY);
        root.removeTag(KOMEConflictPersistence.SEQUENCE_KEY);
        root.removeTag(KOMEConflictPersistence.RECORDS_KEY);
        KOMEWorldData upgraded=new KOMEWorldData("upgraded");upgraded.readFromNBT(root);
        assertTrue(upgraded.isDirty());
        assertEquals(record.rosterSummary(),upgraded.civilianMusters.get(record.key()).rosterSummary());
        assertTrue(upgraded.getConflictService().records().isEmpty());
        assertEquals(1L,upgraded.getConflictService().getNextConflictSequence());
    }
    @Test public void rosterFailureAndScheduleOverflowNeverConsumeUsageOrTouchBank() {
        TestWorld data=world("gondor");
        assertEquals(KOMEMusterService.Code.ROSTER_UNAVAILABLE,KOMEMusterService.call(data,"gondor",ruler,NOW,
            new KOMEMusterService.RosterSource(){public List<KOMEMusterRoster.Unit> resolve(String faction){throw new IllegalStateException("unavailable native registry");}}).code);
        assertEquals(KOMEMusterService.Code.ROSTER_UNAVAILABLE,
            KOMEMusterService.call(data,"gondor",ruler,Long.MAX_VALUE,source("gondor")).code);
        assertTrue(data.civilianMusters.isEmpty());assertEquals(0,KOMEPopulationService.getAvailablePopulationCenti(data,"gondor"));
    }
}
