package kome.common.command;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import kome.common.data.*;
import kome.common.network.*;
import kome.common.command.KOMECompanyMovementServiceTest.Fixture;
import lotr.common.fac.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMECompanyRallySnapshot.*;

public class KOMECompanyRallyServiceTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    private Object oldClass,oldName;
    private LOTRFactionRelations.Relation oldRohanGondor,oldRohanMordor;
    private static final String SOLDIER="kome.test.RallyArrivalSoldier";
    @Before public void before()throws Exception{
        oldRohanGondor=KOMEAllianceAuthority.getCurrentRelation("gondor","rohan");oldRohanMordor=KOMEAllianceAuthority.getCurrentRelation("rohan","mordor");
        config=new KOMEPopulationTestConfig();relation(LOTRFactionRelations.Relation.NEUTRAL);
        oldClass=net.minecraft.entity.EntityList.stringToClassMapping.put(SOLDIER,KOMECompanyMapMovementArrivalTest.ArrivalSoldier.class);
        oldName=net.minecraft.entity.EntityList.classToStringMapping.put(KOMECompanyMapMovementArrivalTest.ArrivalSoldier.class,SOLDIER);
    }
    @After public void after()throws Exception{
        relation(LOTRFactionRelations.Relation.NEUTRAL);config.close();
        LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.ROHAN,oldRohanGondor);
        LOTRFactionRelations.overrideRelations(LOTRFaction.ROHAN,LOTRFaction.MORDOR,oldRohanMordor);
        if(oldClass==null)net.minecraft.entity.EntityList.stringToClassMapping.remove(SOLDIER);else net.minecraft.entity.EntityList.stringToClassMapping.put(SOLDIER,oldClass);
        if(oldName==null)net.minecraft.entity.EntityList.classToStringMapping.remove(KOMECompanyMapMovementArrivalTest.ArrivalSoldier.class);else net.minecraft.entity.EntityList.classToStringMapping.put(KOMECompanyMapMovementArrivalTest.ArrivalSoldier.class,oldName);
    }
    private void physical(Fixture f)throws Exception{
        kome.common.KOMEAccessFixture physical=new kome.common.KOMEAccessFixture();
        physical.world.flatTerrain=true;physical.world.spawnSucceeds=true;physical.world.mapStorage.setData("KOME_ServerRules",f.data);
        f.player.worldObj=physical.world;
        physical.world.testChunkProvider=(net.minecraft.world.chunk.IChunkProvider)java.lang.reflect.Proxy.newProxyInstance(
            net.minecraft.world.chunk.IChunkProvider.class.getClassLoader(),new Class[]{net.minecraft.world.chunk.IChunkProvider.class},(proxy,method,args)->{
                if(method.getReturnType()==boolean.class)return true;
                if(method.getReturnType()==net.minecraft.world.chunk.Chunk.class)return new net.minecraft.world.chunk.Chunk(physical.world,(Integer)args[0],(Integer)args[1]);
                if(method.getReturnType()==int.class)return 1;return null;
            });
        java.lang.reflect.Field provider=net.minecraft.world.World.class.getDeclaredField("chunkProvider");provider.setAccessible(true);provider.set(physical.world,physical.world.testChunkProvider);
        physical.world.isRemote=true;
        KOMECompanyMapMovementArrivalTest.ArrivalSoldier seed=new KOMECompanyMapMovementArrivalTest.ArrivalSoldier(physical.world);
        seed.setPosition(10,65,20);NBTTagCompound snapshot=KOMEEntitySnapshots.snapshot(seed);assertNotNull(snapshot);physical.world.isRemote=false;
        for(KOMEHiredUnitRecord record:f.data.hiredUnits.values())record.stationedEntityData=(NBTTagCompound)snapshot.copy();
        UUID ruler=new UUID(9,7);f.data.lastKnownPlayerFactions.put(ruler,"mordor");KOMERulerService.assignRuler(f.data,"mordor",ruler,"Fixture ruler");
    }
    private void relation(LOTRFactionRelations.Relation r){LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.MORDOR,r);}
    private KOMEArmyCompany add(Fixture f,String id,String origin){
        KOMEArmyCompany c=new KOMEArmyCompany();c.readFromNBT(f.company.writeToNBT());c.id=id;c.name="Company "+id;c.currentTile=origin;c.units.clear();
        KOMEHiredUnitRecord u=new KOMEHiredUnitRecord();u.readFromNBT(f.unit.writeToNBT());
        u.entity=UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));u.companyId=id;u.currentTile=origin;
        c.units.add(u.entity);f.data.armyCompanies.put(id,c);f.data.hiredUnits.put(u.entity,u);return c;
    }
    private final class Run {
        final Fixture f;final KOMECompanyRallyService service;final List<KOMECompanyRallySnapshot> publications=new ArrayList<>();
        final AtomicBoolean connected=new AtomicBoolean(true);final UUID client=UUID.randomUUID();
        KOMECompanyRallySnapshot latest;
        Run(Fixture f){this.f=f;service=new KOMECompanyRallyService(f.service);}
        void open()throws Exception{open(f.destination);}
        void open(String destination)throws Exception{
            physical(f);
            latest=service.open(f.player.playerNetServerHandler,f.data,f.player,client,destination,connected::get,s->{
                latest=KOMEPopulationWire.copyForPublication(new KOMEPacketCompanyRallyResponse(s),KOMEPacketCompanyRallyResponse::new).snapshot();
                publications.add(latest);
            },0);
        }
        void preview(){for(int n=0;latest.phase==Phase.LOADING&&n<=MAX_CANDIDATES;n++)service.tick(n);assertEquals(latest.message,Phase.READY,latest.phase);}
        void confirm(){latest=service.confirm(f.player.playerNetServerHandler,f.player,client,latest.token,f.destination,100);assertNotNull(latest);assertEquals(Phase.EXECUTING,latest.phase);}
        void done(){for(int n=0;latest.phase==Phase.EXECUTING&&n<=MAX_CANDIDATES;n++)service.tick(101+n);assertEquals(latest.message,Phase.DONE,latest.phase);}
        List<Entry> finalRows(){Map<Integer,Entry> rows=new TreeMap<>();for(KOMECompanyRallySnapshot s:publications)if(s.entry!=null)rows.put(s.entry.index,s.entry);return new ArrayList<>(rows.values());}
    }
    @Test public void formerOwnerCannotReadRallyCandidatesAfterPledgeChange()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            f.pledge(LOTRFaction.ROHAN);
            Run run=new Run(f);run.open();
            assertEquals(0,run.latest.total);
        }
    }
    @Test public void pledgeRevocationClearsAnOpenRallyBeforeAnyDetailPublication()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            Run run=new Run(f);run.open();assertEquals(1,run.latest.total);
            f.pledge(LOTRFaction.ROHAN);
            run.service.tick(1);
            assertEquals(Phase.ERROR,run.latest.phase);
            assertNull(run.latest.entry);
            assertEquals(0,run.service.size());
        }
    }
    @Test public void zeroOneAndCompleteTwentyCompanyPreviewNeverExecutesFirstPage()throws Exception{
        for(int count:new int[]{0,1,20})try(Fixture f=new Fixture(false,1)){
            if(count==0)f.data.armyCompanies.clear();for(int i=2;i<=count;i++)add(f,String.format(Locale.ROOT,"C%02d",i),"T001");
            Run run=new Run(f);run.open();assertEquals(count,run.latest.total);assertEquals(0,run.latest.evaluated);
            assertTrue(f.data.armyMovements.isEmpty());
            if(count>8){
                for(int i=0;i<8;i++)run.service.tick(i);
                assertEquals(8,run.latest.evaluated);assertEquals(Phase.LOADING,run.latest.phase);
                assertNull(run.service.confirm(f.player.playerNetServerHandler,f.player,run.client,run.latest.token,f.destination,10));
            }
            run.preview();assertEquals(count,run.latest.evaluated);assertEquals(count,run.latest.included);
            assertEquals(count,run.service.retainedRows());assertTrue(f.data.armyMovements.isEmpty());
            if(count==0)assertNull(run.service.confirm(f.player.playerNetServerHandler,f.player,run.client,run.latest.token,f.destination,100));
        }
    }
    @Test public void exactlySixtyFourCandidatesCompleteWithExactCountsAndAvailableConfirmation()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            Set<String> expected=new HashSet<>();expected.add("C1");
            for(int i=2;i<=MAX_CANDIDATES;i++){
                KOMEArmyCompany company=add(f,"C"+i,"T001");expected.add(company.id);
                if(i%2==0)company.movementAllowance=0;
            }
            Run run=new Run(f);run.open();assertEquals(Phase.LOADING,run.latest.phase);assertEquals(64,run.latest.total);
            for(int i=0;i<63;i++)run.service.tick(i);
            assertEquals(Phase.LOADING,run.latest.phase);assertEquals(63,run.latest.evaluated);
            assertNull(run.service.confirm(f.player.playerNetServerHandler,f.player,run.client,run.latest.token,f.destination,63));
            run.service.tick(64);assertEquals(Phase.READY,run.latest.phase);assertEquals(64,run.latest.evaluated);
            assertEquals(32,run.latest.included);assertEquals("32 will rally. 32 skipped.",run.latest.message);
            List<Entry> rows=run.finalRows();assertEquals(64,rows.size());Set<String> actual=new HashSet<>();
            for(int i=0;i<rows.size();i++){
                Entry entry=rows.get(i);assertEquals(i,entry.index);assertTrue(actual.add(entry.company.companyId));
                assertEquals(Integer.parseInt(entry.company.companyId.substring(1))%2==1,entry.included);
            }
            assertEquals(expected,actual);assertEquals(1,run.service.size());assertEquals(64,run.service.retainedRows());
            assertTrue(run.service.retainedRows()<=MAX_SESSIONS*MAX_CANDIDATES);assertTrue(f.data.armyMovements.isEmpty());
            run.confirm();assertEquals(Phase.EXECUTING,run.latest.phase);assertTrue(f.data.armyMovements.isEmpty());
            run.service.clear();assertEquals(0,run.service.size());assertEquals(0,run.service.retainedRows());
        }
    }
    @Test public void stalePlannedCreatorIsSkippedAndNextArrivalCreatesBeforeLaterJoin()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            KOMEArmyCompany second=add(f,"C2","T001"),third=add(f,"C3","T001");f.hostileDestination();
            Run run=new Run(f);run.open();run.preview();assertEquals(3,run.latest.included);
            List<Entry> preview=run.finalRows();assertEquals("C1",preview.get(0).company.companyId);
            for(Entry entry:preview)assertEquals(KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT,entry.company.consequence);
            assertTrue(f.data.getConflictService().records().isEmpty());
            f.company.movementAllowance=0; // Canonical exhaustion invalidates only the planned first arrival.
            run.confirm();run.done();assertEquals(2,run.latest.rallied);assertEquals("2 companies rallied. 1 skipped.",run.latest.message);
            List<Entry> results=run.finalRows();Entry skipped=results.get(0),creator=results.get(1),joined=results.get(2);
            assertEquals(Outcome.SKIPPED,skipped.outcome);assertEquals(KOMECompanyMovementResult.playerDenialDetail("NO_ALLOWANCE"),skipped.reason);
            assertFalse(skipped.reason.contains("CREATE"));assertFalse(skipped.reason.contains("Rallied"));
            assertEquals("C2",creator.company.companyId);assertEquals(Outcome.RALLIED,creator.outcome);assertEquals("Rallied: CREATE CONFLICT",creator.reason);
            assertEquals("C3",joined.company.companyId);assertEquals(Outcome.RALLIED,joined.outcome);assertEquals("Rallied: JOIN CONFLICT",joined.reason);
            assertEquals(1,f.data.getConflictService().records().size());KOMEConflictRecord conflict=f.data.getConflictService().get(f.destination);
            assertEquals(Arrays.asList("C2","C3"),new ArrayList<>(conflict.getCommitments().keySet()));
            assertTrue(conflict.getCommitments().get("C2").validatedEvent.createdConflict);assertFalse(conflict.getCommitments().get("C3").validatedEvent.createdConflict);
            assertEquals(conflict.getConflictId(),KOMEConflictMovementService.activeConflictForDetachment(f.data,"C3").getConflictId());
            assertEquals("T001",f.company.currentTile);assertTrue(f.company.movementOrderId.isEmpty());
            assertEquals(f.destination,second.currentTile);assertEquals(f.destination,third.currentTile);
            assertEquals(0,second.movementAllowance);assertEquals(0,third.movementAllowance);assertEquals(2,f.data.armyMovements.size());
        }
    }
    @Test public void acceptedUnconfirmedArrivalRemainsPendingWithoutInflatingRalliedOrSkippedOrRetrying()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            KOMEArmyCompany pending=add(f,"C2","T001");Run run=new Run(f);run.open();run.preview();assertEquals(2,run.latest.included);
            // The canonical executor accepts the order but cannot recreate this saved NPC at arrival.
            KOMEHiredUnitRecord member=f.data.hiredUnits.get(pending.units.get(0));member.stationedEntityData.setString("id","kome.test.MissingRallySoldier");
            run.confirm();run.done();assertEquals(1,run.latest.rallied);assertEquals(2,run.latest.processed);
            assertEquals("1 companies rallied. 0 skipped. 1 orders awaiting arrival.",run.latest.message);
            List<Entry> rows=run.finalRows();assertEquals(Outcome.RALLIED,rows.get(0).outcome);assertEquals(Outcome.PENDING,rows.get(1).outcome);
            assertEquals("Order accepted; arrival not confirmed",rows.get(1).reason);
            assertEquals(f.destination,f.company.currentTile);assertEquals("T001",pending.currentTile);
            assertEquals(2,f.data.armyMovements.size());KOMEArmyMovementOrder order=f.data.armyMovements.get(pending.movementOrderId);assertNotNull(order);
            assertEquals(KOMEArmyMovementOrder.PENDING_SPAWN,order.status);assertEquals(0,order.completedSteps);assertFalse(order.pendingSpawnReason.isEmpty());
            assertEquals(0,f.company.movementAllowance);assertEquals(0,pending.movementAllowance);
            String orderId=order.id;long attempts=order.lastSpawnAttemptMillis;
            assertNull(run.service.confirm(f.player.playerNetServerHandler,f.player,run.client,run.latest.token,f.destination,200));
            for(int tick=0;tick<10;tick++)run.service.tick(201+tick);
            assertEquals(2,f.data.armyMovements.size());assertSame(order,f.data.armyMovements.get(orderId));assertEquals(attempts,order.lastSpawnAttemptMillis);
            assertEquals(0,pending.movementAllowance);assertEquals(1,run.latest.rallied);assertEquals(Outcome.PENDING,run.finalRows().get(1).outcome);
        }
    }
    @Test public void mixedImmediateLaterExhaustedBlockedAlreadyHereAndOrdinaryArePreciselyClassified()throws Exception{
        try(Fixture f=new Fixture(false,2)){
            add(f,"IMMEDIATE","T002");
            KOMEArmyCompany exhausted=add(f,"EXHAUSTED","T002");exhausted.movementAllowance=0;
            add(f,"ALREADY","T003");
            KOMEArmyCompany blocked=add(f,"BLOCKED","T002");blocked.status=KOMEArmyCompany.MOVING;
            KOMEArmyCompany ordinary=add(f,"ORDINARY","T002");KOMEHiredUnitRecord record=f.data.hiredUnits.get(ordinary.units.get(0));
            NBTTagCompound n=record.writeToNBT();n.setString("UnitClass","ORDINARY");record.readFromNBT(n);
            KOMEArmyCompany unauthorized=add(f,"OTHER","T002");unauthorized.owner=new UUID(9,9);
            f.player.operator=true;Run run=new Run(f);run.open();run.preview();
            assertEquals(5,run.latest.total);assertEquals(1,run.latest.included);
            Map<String,Entry> rows=new HashMap<>();for(Entry e:run.finalRows())rows.put(e.company.companyId,e);
            assertEquals("Already here",rows.get("ALREADY").reason);assertTrue(rows.get("C1").reason.contains("later"));
            assertTrue(rows.get("IMMEDIATE").included);assertFalse(rows.get("EXHAUSTED").included);assertFalse(rows.get("BLOCKED").included);
            assertTrue(f.data.armyMovements.isEmpty());
            run.confirm();run.done();assertEquals(1,run.latest.rallied);assertEquals(1,f.data.armyMovements.size());
        }
    }
    @Test public void allIncludedMoveThroughCanonicalExecutorWithoutPlayerDeploymentAndNoReplay()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            add(f,"C2","T001");add(f,"C3","T001");
            double x=f.player.posX,y=f.player.posY,z=f.player.posZ;Run run=new Run(f);run.open();run.preview();UUID token=run.latest.token;
            run.confirm();assertNull(run.service.confirm(f.player.playerNetServerHandler,f.player,run.client,token,f.destination,101));
            run.done();assertEquals(3,run.latest.rallied);assertEquals(3,f.data.armyMovements.size());
            assertNull(run.service.confirm(f.player.playerNetServerHandler,f.player,run.client,token,f.destination,200));
            for(int i=0;i<10;i++)run.service.tick(201+i);assertEquals(3,f.data.armyMovements.size());
            assertEquals(x,f.player.posX,0);assertEquals(y,f.player.posY,0);assertEquals(z,f.player.posZ,0);
            assertTrue(f.data.getJoinBattleDeploymentReceipts().records().isEmpty());
            for(Entry e:run.finalRows()){assertEquals(Outcome.RALLIED,e.outcome);assertEquals("T002",f.data.armyCompanies.get(e.company.companyId).currentTile);}
        }
    }
    @Test public void staleSourceUnauthorizedExhaustedAndDeniedCompaniesDoNotCancelValidCompanies()throws Exception{
        for(int stale=0;stale<4;stale++)try(Fixture f=new Fixture(false,1)){
            KOMEArmyCompany other=add(f,"C2","T001");Run run=new Run(f);run.open();run.preview();
            if(stale==0)other.currentTile="T003";if(stale==1)other.owner=new UUID(9,9);if(stale==2)other.movementAllowance=0;
            if(stale==3)other.status=KOMEArmyCompany.MOVING;
            run.confirm();run.done();assertEquals(1,run.latest.rallied);assertEquals(1,f.data.armyMovements.size());
            assertEquals(Outcome.SKIPPED,run.finalRows().get(1).outcome);assertEquals("T002",f.company.currentTile);
        }
    }
    @Test public void newlyAddedAndPreviouslySkippedCompaniesStayOutsideConfirmedSet()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            KOMEArmyCompany skipped=add(f,"C2","T001");skipped.movementAllowance=0;Run run=new Run(f);run.open();run.preview();assertEquals(1,run.latest.included);
            add(f,"NEW","T001");skipped.movementAllowance=1;run.confirm();run.done();
            assertEquals(1,run.latest.rallied);assertEquals("T001",skipped.currentTile);assertEquals("T001",f.data.armyCompanies.get("NEW").currentTile);
            assertEquals(2,run.latest.total);assertEquals(1,f.data.armyMovements.size());
        }
    }
    @Test public void refreshDestinationConnectionExpiryAndCapacityInvalidateOldTokens()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            Run run=new Run(f);run.open();run.preview();UUID token=run.latest.token;
            assertEquals(Phase.ERROR,run.service.confirm(f.player.playerNetServerHandler,f.player,run.client,token,"T003",100).phase);
            UUID replacement=UUID.randomUUID();
            run.service.open(f.player.playerNetServerHandler,f.data,f.player,replacement,f.destination,run.connected::get,s->{},100);
            assertEquals(Phase.ERROR,run.service.confirm(f.player.playerNetServerHandler,f.player,run.client,token,f.destination,101).phase);
            run.service.tick(100+LIFETIME_MILLIS);assertEquals(0,run.service.size());assertEquals(0,run.service.retainedRows());
            run.open();run.preview();run.connected.set(false);run.service.tick(1);assertEquals(0,run.service.size());
            assertEquals(Phase.ERROR,run.service.confirm(f.player.playerNetServerHandler,f.player,run.client,token,f.destination,2).phase);
            assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void hardCandidateCeilingFailsWholePreviewAndMemberBoundCannotBecomePartialRally()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            for(int i=2;i<=MAX_CANDIDATES+1;i++)add(f,"C"+i,"T001");
            Run run=new Run(f);run.open();assertEquals(Phase.ERROR,run.latest.phase);assertTrue(run.latest.message.contains("No partial"));
            assertEquals(0,run.service.size());assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void oneCompanyWorkPerGlobalTickAndGlobalSessionMemoryIsBounded()throws Exception{
        List<Fixture> fixtures=new ArrayList<>();try{
            KOMECompanyRallyService service=null;final List<KOMECompanyRallySnapshot> events=new ArrayList<>();
            for(int i=0;i<=MAX_SESSIONS;i++){
                Fixture f=new Fixture(false,1);fixtures.add(f);if(service==null)service=new KOMECompanyRallyService(f.service);
                KOMECompanyRallySnapshot s=service.open(f.player.playerNetServerHandler,f.data,f.player,UUID.randomUUID(),f.destination,()->true,events::add,0);
                assertEquals(i==MAX_SESSIONS?Phase.ERROR:Phase.LOADING,s.phase);
            }
            assertEquals(MAX_SESSIONS,service.size());service.tick(1);assertEquals(1,events.size());assertEquals(1,service.retainedRows());
            for(int i=0;i<MAX_SESSIONS;i++)service.tick(2+i);
            assertEquals(MAX_SESSIONS,service.retainedRows());assertTrue(service.retainedRows()<=MAX_SESSIONS*MAX_CANDIDATES);
            service.clear();assertEquals(0,service.size());
        }finally{Collections.reverse(fixtures);for(Fixture f:fixtures)f.close();}
    }
    @Test public void hostileCreateThenJoinAndExistingConflictRemainSingleConflictInDeterministicOrder()throws Exception{
        for(boolean exists:new boolean[]{false,true})try(Fixture f=new Fixture(false,1)){
            add(f,"C3","T001");add(f,"C2","T001");if(exists)f.hostileConflict();else f.hostileDestination();
            Run run=new Run(f);run.open();run.preview();assertEquals(3,run.latest.included);run.confirm();run.done();
            assertEquals(3,run.latest.rallied);KOMEConflictRecord conflict=f.data.getConflictService().get(f.destination);
            assertEquals(Arrays.asList("C1","C2","C3"),new ArrayList<>(conflict.getCommitments().keySet()));
            assertEquals(!exists,conflict.getCommitments().get("C1").validatedEvent.createdConflict);
            assertFalse(conflict.getCommitments().get("C2").validatedEvent.createdConflict);
            assertFalse(conflict.getCommitments().get("C3").validatedEvent.createdConflict);
            assertEquals(conflict.getConflictId(),KOMEConflictMovementService.activeConflictForDetachment(f.data,"C3").getConflictId());
        }
    }
    @Test public void mixedDelegatedFactionCanArriveFirstAndItsIdentityAndOrderAreProjected()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            f.hostileDestination();LOTRFactionRelations.overrideRelations(LOTRFaction.ROHAN,LOTRFaction.MORDOR,LOTRFactionRelations.Relation.ENEMY);
            try{
                f.data.armyCompanies.remove("C1");f.company.id="C2";f.unit.companyId="C2";f.data.armyCompanies.put("C2",f.company);
                KOMEArmyCompany rohan=add(f,"C1","T003");rohan.faction=rohan.nativeFaction="rohan";rohan.owner=new UUID(9,9);
                rohan.controllerAuthority=KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;rohan.temporaryController=f.player.id;
                KOMEHiredUnitRecord member=f.data.hiredUnits.get(rohan.units.get(0));member.owner=rohan.owner;
                member.unitFaction=member.sourceFaction=member.populationOwningFaction="rohan";
                f.data.conquestTiles.get("T003").setCurrentRulingFaction("rohan");f.data.lastKnownPlayerFactions.put(f.player.id,"gondor");
                Run run=new Run(f);run.open();run.preview();assertEquals(2,run.latest.included);
                assertEquals("C1",run.finalRows().get(0).company.companyId);assertEquals("rohan",run.finalRows().get(0).faction);
                run.confirm();run.done();assertEquals(run.finalRows().stream().map(e->e.company.companyId+": "+e.outcome+" "+e.reason).collect(java.util.stream.Collectors.joining("; "))+f.data.armyMovements.values().stream().map(o->o.status+": "+o.pendingSpawnReason).collect(java.util.stream.Collectors.joining("; ")),2,run.latest.rallied);
                KOMEConflictRecord conflict=f.data.getConflictService().get(f.destination);
                assertEquals(Arrays.asList("C1","C2"),new ArrayList<>(conflict.getCommitments().keySet()));
                assertEquals("rohan",conflict.getCommitments().get("C1").validatedEvent.detachmentFactionId);
            }finally{LOTRFactionRelations.overrideRelations(LOTRFaction.ROHAN,LOTRFaction.MORDOR,LOTRFactionRelations.Relation.ENEMY);}
        }
    }
    @Test public void deferredRoutesAreSkippedWithoutStoppingLaterImmediateCompany()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            for(int i=1;i<=65;i++){
                String id=String.format(Locale.ROOT,"T%03d",i);KOMEConquestTile tile=new KOMEConquestTile(id);
                tile.claim("gondor",1);tile.setAnchor(0,i*16,64,0);f.data.conquestTiles.put(id,tile);
            }
            for(int i=1;i<=65;i++)for(int j=i+1;j<=65;j++)f.edge(String.format(Locale.ROOT,"T%03d",i),String.format(Locale.ROOT,"T%03d",j),j==i+1?KOMEConquestRouteEdge.OPEN:KOMEConquestRouteEdge.RIVER);
            add(f,"C2","T064");Run run=new Run(f);run.open("T065");run.preview();
            assertEquals(2,run.latest.total);assertEquals(1,run.latest.included);
            assertEquals(KOMECompanyDestinationOptions.EvaluationState.DEFERRED_ROUTE_LIMIT,run.finalRows().get(0).company.evaluationState);
            assertEquals("Route evaluation deferred",run.finalRows().get(0).reason);assertTrue(run.finalRows().get(1).included);
            assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void canonicalConflictHoldAndTerrainAndDiplomacyDenialsRemainSkipped()throws Exception{
        for(int denial=0;denial<4;denial++)try(Fixture f=new Fixture(false,1)){
            if(denial==0){
                KOMEConflictService conflicts=f.data.getConflictService();
                KOMEConflictRecord c=conflicts.start("T001",KOMEConflictRecord.State.ORDINARY,KOMEConflictContracts.ExpectedConflict.absent(),Collections.<KOMEConflictContracts.GarrisonSeed>emptyList(),new KOMEConflictContracts.Context(1,"test","fixture")).record;
                conflicts.commit("T001",KOMEConflictContracts.ExpectedConflict.at(c.getConflictId(),c.getRevision()),new KOMEConflictContracts.CommitmentInput("C1",KOMEHiredUnitClass.CAMPAIGN,KOMEConflictRecord.EntryOrigin.LEGAL_ARRIVAL,"M0"),new KOMEConflictContracts.Context(1,"test","fixture"));
            }else if(denial<3)f.edge("T001","T002",denial==1?KOMEConquestRouteEdge.RIVER:KOMEConquestRouteEdge.MOUNTAIN);
            else {f.data.conquestTiles.get("T002").setCurrentRulingFaction("rohan");LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.ROHAN,LOTRFactionRelations.Relation.NEUTRAL);}
            Run run=new Run(f);run.open();run.preview();assertEquals(0,run.latest.included);
            assertFalse(run.finalRows().get(0).reason.isEmpty());assertNull(run.service.confirm(f.player.playerNetServerHandler,f.player,run.client,run.latest.token,f.destination,100));
            assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void retiredStewardshipMetadataDoesNotOverrideCanonicalAuthority()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            f.company.owner=f.unit.owner=new UUID(9,9);f.company.controllerAuthority=KOMEArmyCompany.AUTHORITY_STEWARDSHIP;f.company.temporaryController=f.player.id;
            f.data.lastKnownPlayerFactions.put(f.player.id,"gondor");Run run=new Run(f);run.open();run.preview();assertEquals(0,run.latest.total);
        }
    }
    @Test public void oversizedMemberSetFailsClearlyAndDisconnectStopsRemainingConfirmedWork()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            for(int i=0;i<256;i++)f.company.units.add(new UUID(8,i));Run run=new Run(f);run.open();
            assertEquals(Phase.ERROR,run.latest.phase);assertEquals(0,run.service.size());assertTrue(f.data.armyMovements.isEmpty());
        }
        try(Fixture f=new Fixture(false,1)){
            add(f,"C2","T001");Run run=new Run(f);run.open();run.preview();run.confirm();run.service.tick(101);
            assertEquals(1,run.latest.rallied);run.connected.set(false);run.service.tick(102);
            assertEquals(0,run.service.size());assertEquals(1,f.data.armyMovements.size());assertEquals("T001",f.data.armyCompanies.get("C2").currentTile);
        }
    }
}
