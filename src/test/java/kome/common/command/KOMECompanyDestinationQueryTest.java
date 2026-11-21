package kome.common.command;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import kome.common.data.*;
import kome.common.network.*;
import lotr.common.fac.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMECompanyDestinationOptions.*;
import static kome.common.command.KOMECompanyMovementService.Status.*;
import static kome.common.command.KOMECompanyMovementService.Consequence.*;
import kome.common.command.KOMECompanyMovementServiceTest.Fixture;

public class KOMECompanyDestinationQueryTest {
    /** Shared deterministic catalogue for testing the full query/codec/client paging path. */
    public static final class Catalogue implements AutoCloseable {
        private final Fixture fixture;
        private int inspections;
        public Catalogue(int count) throws Exception {
            this(count,0);
        }
        public Catalogue(int count,int routeLength) throws Exception {
            fixture=new Fixture(false,1);
            if(routeLength>0)new KOMECompanyDestinationQueryTest().chain(fixture,routeLength);
            for(int i=2;i<=count;i++)add("C"+i);
            fixture.world.arrivalInspection=()->inspections++;
        }
        public void add(String id) {new KOMECompanyDestinationQueryTest().add(fixture,id,"T001");}
        public int inspections() {return inspections;}
        public KOMECompanyDestinationOptions query(KOMEPacketCompanyDestinationRequest request) {
            KOMECompanyDestinationOptions page=new KOMECompanyDestinationQuery(fixture.service).query(fixture.data,fixture.player,
                request.session,request.sequence,request.destination,request.limit,request.afterCompanyId,request.catalogueToken);
            return KOMEPacketCompanyDestinationResponse.forQuery(page).snapshot();
        }
        @Override public void close() {fixture.close();}
    }
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    private final UUID session=new UUID(7,7);
    @Before public void before() throws Exception {config=new KOMEPopulationTestConfig();relation(LOTRFactionRelations.Relation.NEUTRAL);}
    @After public void after() throws Exception {relation(LOTRFactionRelations.Relation.NEUTRAL);config.close();}
    private void relation(LOTRFactionRelations.Relation r) {LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.MORDOR,r);}
    private KOMECompanyDestinationOptions query(Fixture f) {
        return new KOMECompanyDestinationQuery(f.service).query(f.data,f.player,session,13,f.destination,MAX_ROWS,"","");
    }
    private Row row(Fixture f) {KOMECompanyDestinationOptions p=query(f);assertEquals(Result.OK,p.result);assertEquals(1,p.rows.size());return p.rows.get(0);}
    @Test public void formerOwnerCannotReadDestinationCatalogueAfterPledgeChange() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            assertEquals(1,query(f).rows.size());
            f.pledge(LOTRFaction.ROHAN);
            assertTrue(query(f).rows.isEmpty());
        }
    }
    @Test public void operatorMapCatalogueNeedsAdministrativeVisionOptIn() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            f.player.operator=true;
            f.pledge(LOTRFaction.ROHAN);
            assertTrue(query(f).rows.isEmpty());
            assertTrue(KOMEAdminMapVision.setEnabled(f.player,true));
            assertEquals(1,query(f).rows.size());
            KOMEAdminMapVision.forget(f.player);
            assertTrue(query(f).rows.isEmpty());
        }
    }
    private KOMEArmyCompany add(Fixture f,String id,String origin) {
        KOMEArmyCompany c=new KOMEArmyCompany();c.readFromNBT(f.company.writeToNBT());c.id=id;c.name=id;c.currentTile=origin;c.units.clear();
        KOMEHiredUnitRecord u=new KOMEHiredUnitRecord();u.readFromNBT(f.unit.writeToNBT());u.entity=UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));u.companyId=id;u.currentTile=origin;
        c.units.add(u.entity);f.data.armyCompanies.put(id,c);f.data.hiredUnits.put(u.entity,u);return c;
    }
    @Test public void populationUsesCanonicalPermanentInvestmentNotNpcCountOrCurrentHealthCost() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            f.unit.populationSpent=18;f.unit.cost=3;f.company.totalPopulation=999;
            Row r=row(f);assertEquals(1800,r.populationCenti);assertEquals(1,r.unitCount);
            assertEquals(KOMEPopulationService.getCompanyInvestmentCenti(f.data,f.company).longValueExact(),r.populationCenti);
        }
    }
    @Test public void missingMembershipProjectsUnknownPopulationWithoutRepairOrPartialSum() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            f.company.units.add(new UUID(8,8));Row r=row(f);
            assertEquals(-1,r.populationCenti);assertEquals(-1,r.unitCount);assertEquals(2,f.company.units.size());
        }
    }
    @Test public void etaUsesPostValidationServerSampleAndCanonicalAllowanceAdjustedCompletion() throws Exception {
        try(Fixture f=new Fixture(true,2)) {
            f.company.movementAllowance=1;KOMECompanyMovementService.Evaluation e=f.evaluate();Row r=row(f);
            assertEquals(e.dailyTiming.estimatedCompletionMillis-e.evaluatedAtMillis,r.etaMillis);assertTrue(r.etaMillis>0);
            f.company.movementAllowance=2;assertEquals(0,row(f).etaMillis);
            f.data.movementSecondsPerTileOverride=10;assertEquals(-1,row(f).etaMillis);
            f.company.units.add(new UUID(9,9));assertEquals(-1,row(f).etaMillis);
        }
    }
    @Test public void controlledCampaignCompaniesAcrossOriginsAndCorrelation() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            add(f,"C2","T002");KOMECompanyDestinationOptions p=query(f);
            assertEquals(2,p.rows.size());assertEquals("T001",p.rows.get(0).currentTile);assertEquals("T002",p.rows.get(1).currentTile);
            assertEquals(session,p.session);assertEquals(13,p.sequence);assertEquals("T003",p.destination);
            assertEquals(CAN_START_BUT_CONTINUES_LATER,p.rows.get(0).status);assertEquals(CAN_START_AND_ARRIVE_NOW,p.rows.get(1).status);
        }
    }
    @Test public void ordinaryOnlyCompanyAndLooseHiresAreExcluded() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            NBTTagCompound n=f.unit.writeToNBT();n.setString("UnitClass","ORDINARY");f.unit.readFromNBT(n);
            assertTrue(query(f).rows.isEmpty());
            f.company.units.clear();assertTrue(query(f).rows.isEmpty());
        }
    }
    @Test public void operatorCatalogueDoesNotGrantControl() throws Exception {
        try(Fixture f=new Fixture(false,1)) {f.company.owner=f.unit.owner=new UUID(9,9);f.player.operator=true;assertTrue(query(f).rows.isEmpty());}
    }
    @Test public void unauthorizedNonOperatorIsExcluded() throws Exception {
        try(Fixture f=new Fixture(false,1)) {f.company.owner=f.unit.owner=new UUID(9,9);assertTrue(query(f).rows.isEmpty());}
    }
    @Test public void validDelegatedCompanyIncluded() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            f.company.owner=f.unit.owner=new UUID(9,9);f.company.controllerAuthority=KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;
            f.company.temporaryController=f.player.id;f.data.lastKnownPlayerFactions.put(f.company.owner,"gondor");
            f.data.lastKnownPlayerFactions.put(f.player.id,"gondor");assertTrue(row(f).canStartNow);
        }
    }
    @Test public void retiredStewardshipCannotGrantAccess() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            f.company.controllerAuthority=KOMEArmyCompany.AUTHORITY_STEWARDSHIP;f.company.temporaryController=f.player.id;
            f.data.lastKnownPlayerFactions.put(f.player.id,"gondor");assertTrue(query(f).rows.isEmpty());
        }
    }
    @Test public void immediateFootProjectionMatchesAuthority() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            Row r=row(f);assertEquals(CAN_START_AND_ARRIVE_NOW,r.status);assertEquals(Entitlement.FOOT_OR_MIXED,r.entitlement);
            assertEquals(1,r.tilesPerDay);assertEquals(1,r.remainingAllowance);assertEquals(f.evaluate().routeTiles,r.route);
            assertEquals(f.evaluate().confirmation.serviceFingerprint,r.confirmationFingerprint);
        }
    }
    @Test public void immediateMountedAndPartialAllowance() throws Exception {
        try(Fixture f=new Fixture(true,2)) {
            Row r=row(f);assertEquals(Entitlement.MOUNTED,r.entitlement);assertEquals(2,r.tilesPerDay);assertTrue(r.canArriveNow);
            f.company.movementAllowance=1;r=row(f);assertTrue(r.canStartNow);assertFalse(r.canArriveNow);
            assertEquals(CAN_START_BUT_CONTINUES_LATER,r.status);assertEquals(1,r.futureBoundaries);
        }
    }
    @Test public void multidayTimingIsServerDerived() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            Row r=row(f);assertTrue(r.timingAvailable);assertTrue(r.canStartNow);assertFalse(r.canArriveNow);
            assertEquals(f.evaluate().dailyTiming.nextBoundaryMillis,r.nextBoundaryMillis);
            assertEquals(f.evaluate().dailyTiming.estimatedCompletionMillis,r.estimatedCompletionMillis);
        }
    }
    @Test public void exhaustedRetainsRouteAndTypedDenial() throws Exception {
        try(Fixture f=new Fixture(false,2)) {f.company.movementAllowance=0;Row r=row(f);assertEquals("NO_ALLOWANCE",r.denialCode);assertTrue(r.legalRoute);assertFalse(r.canStartNow);assertEquals("",r.confirmationFingerprint);}
    }
    @Test public void conflictHoldIdentityAndRevisionProjected() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            KOMEConflictService conflicts=f.data.getConflictService();
            KOMEConflictRecord c=conflicts.start("T001",KOMEConflictRecord.State.ORDINARY,KOMEConflictContracts.ExpectedConflict.absent(),
                Collections.<KOMEConflictContracts.GarrisonSeed>emptyList(),new KOMEConflictContracts.Context(1,"test","fixture")).record;
            c=conflicts.commit("T001",KOMEConflictContracts.ExpectedConflict.at(c.getConflictId(),c.getRevision()),
                new KOMEConflictContracts.CommitmentInput("C1",KOMEHiredUnitClass.CAMPAIGN,KOMEConflictRecord.EntryOrigin.LEGAL_ARRIVAL,"M0"),
                new KOMEConflictContracts.Context(1,"test","fixture")).record;
            Row r=row(f);assertEquals("CONFLICT_HELD",r.denialCode);assertEquals(c.getConflictId(),r.heldConflictId);assertEquals(c.getRevision(),r.heldConflictRevision);
        }
    }
    @Test public void completedAndUnfinishedRetreatReservationsAreBlocked() throws Exception {
        for(KOMEFormalRetreatBatch.Progress progress:Arrays.asList(KOMEFormalRetreatBatch.Progress.PREPARED,KOMEFormalRetreatBatch.Progress.COMPLETE))
        try(Fixture f=new Fixture(false,1)) {
            f.reserve(progress,KOMEFormalRetreatBatch.Progress.PREPARED);assertEquals("FORMAL_RETREAT_BLOCKED",row(f).denialCode);
        }
    }
    @Test public void legacyQuarantineIsBlocked() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            KOMEArmyMovementOrder o=new KOMEArmyMovementOrder();o.id="M1";o.companyId="C1";o.retreating=true;o.status=KOMEArmyMovementOrder.WAITING_NEXT_STEP;
            NBTTagCompound n=new NBTTagCompound();n.setInteger("SchemaVersion",1);n.setString("ConflictId","CF1");n.setLong("ConflictRevision",1);
            n.setString("OrderId","M1");n.setString("CompanyId","C1");n.setString("Outcome","FORMAL_RETREAT");n.setLong("AppliedAtMillis",1);
            n.setString("Code","CANCELED_BY_FORMAL_RETREAT");n.setString("Reason","legacy");o.conflictRelease=KOMEConflictMovementHandoff.Receipt.read(n);
            f.data.armyMovements.put(o.id,o);KOMEFormalRetreatAuthority.quarantineIncompleteLegacy(f.data);
            assertEquals("FORMAL_RETREAT_BLOCKED",row(f).denialCode);
        }
    }
    @Test public void mountainAndRiverDenialsAndLegalPassages() throws Exception {
        for(String edge:Arrays.asList(KOMEConquestRouteEdge.MOUNTAIN,KOMEConquestRouteEdge.RIVER))
        try(Fixture f=new Fixture(false,1)) {
            f.edge("T001","T002",edge);assertEquals(BLOCKED,row(f).status);
            f.edge("T001","T002",edge.equals(KOMEConquestRouteEdge.MOUNTAIN)?KOMEConquestRouteEdge.MOUNTAIN_PASS:KOMEConquestRouteEdge.BRIDGE);
            assertTrue(row(f).canArriveNow);
        }
    }
    @Test public void hostileCreateAndJoinWithConflictRevision() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            f.hostileDestination();assertEquals(EXPECTED_CREATE_CONFLICT,row(f).consequence);
            KOMEConflictRecord conflict=f.hostileConflict();Row r=row(f);assertEquals(EXPECTED_JOIN_CONFLICT,r.consequence);
            assertEquals(conflict.getConflictId(),r.destinationConflictId);assertEquals(conflict.getRevision(),r.destinationConflictRevision);
        }
    }
    @Test public void pagingHasExplicitContinuationAndRejectsChangedCatalogue() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            for(int i=2;i<=10;i++)add(f,"C"+i,"T001");KOMECompanyDestinationQuery q=new KOMECompanyDestinationQuery(f.service);
            KOMECompanyDestinationOptions first=query(f);assertEquals(MAX_ROWS,first.rows.size());assertTrue(first.hasMore);
            KOMECompanyDestinationOptions next=q.query(f.data,f.player,session,14,f.destination,MAX_ROWS,first.nextCompanyId,first.catalogueToken);
            assertEquals(2,next.rows.size());assertFalse(next.hasMore);assertEquals("C8",next.rows.get(0).companyId);
            add(f,"C11","T001");assertEquals(Result.CATALOGUE_CHANGED,q.query(f.data,f.player,session,15,f.destination,MAX_ROWS,first.nextCompanyId,first.catalogueToken).result);
        }
    }
    @Test public void catalogueLimitFailsExplicitlyBeforeEvaluation() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            for(int i=0;i<=MAX_CATALOGUE;i++)f.data.armyCompanies.put("X"+i,new KOMEArmyCompany());
            assertEquals(Result.CATALOGUE_LIMIT,query(f).result);
        }
    }
    @Test public void queryIsNonMutatingAndRowsImmutable() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            NBTTagCompound company=f.company.writeToNBT(),unit=f.unit.writeToNBT();f.data.setDirty(false);
            KOMECompanyDestinationOptions p=query(f);assertEquals(company,f.company.writeToNBT());assertEquals(unit,f.unit.writeToNBT());
            assertTrue(f.data.armyMovements.isEmpty());assertNull(f.data.getConflictService().get(f.destination));assertFalse(f.data.isDirty());assertTrue(f.player.messages.isEmpty());
            try{p.rows.clear();fail();}catch(UnsupportedOperationException expected) { }
            try{p.rows.get(0).route.clear();fail();}catch(UnsupportedOperationException expected) { }
        }
    }
    private void chain(Fixture f,int length) {
        for(int i=1;i<=length;i++) {
            String id=String.format(java.util.Locale.ROOT,"T%03d",i);KOMEConquestTile tile=new KOMEConquestTile(id);
            tile.claim("gondor",1);tile.setAnchor(0,i*16,64,0);f.data.conquestTiles.put(id,tile);
        }
        // Override native shortcuts, leaving one deterministic authoritative chain.
        for(int i=1;i<=length;i++)for(int j=i+1;j<=length;j++)
            f.edge(String.format(java.util.Locale.ROOT,"T%03d",i),String.format(java.util.Locale.ROOT,"T%03d",j),
                j==i+1?KOMEConquestRouteEdge.OPEN:KOMEConquestRouteEdge.RIVER);
    }
    @Test public void pageStopsAtSharedArrivalBudgetAndCanContinue() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            chain(f,20);for(int i=2;i<=4;i++)add(f,"C"+i,"T001");
            AtomicInteger checks=new AtomicInteger();f.world.arrivalInspection=checks::incrementAndGet;
            KOMECompanyDestinationQuery q=new KOMECompanyDestinationQuery(f.service);
            KOMECompanyDestinationOptions p=q.query(f.data,f.player,session,1,"T020",4,"","");
            assertEquals(Result.ARRIVAL_BUDGET_EXCEEDED,p.result);assertEquals(3,p.rows.size());assertTrue(p.hasMore);assertEquals("C3",p.nextCompanyId);
            assertTrue(checks.get()>0);assertTrue(checks.get()<=MAX_ARRIVAL_CHECKS);
            KOMECompanyDestinationOptions next=q.query(f.data,f.player,session,2,"T020",1,p.nextCompanyId,p.catalogueToken);
            assertEquals(Result.OK,next.result);assertEquals("C4",next.rows.get(0).companyId);assertFalse(next.hasMore);
        }
    }
    @Test public void excessiveFirstRouteIsDeferredAndItsCursorReachesLaterCompanies() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            chain(f,65);add(f,"C2","T064");AtomicInteger checks=new AtomicInteger();f.world.arrivalInspection=checks::incrementAndGet;
            KOMECompanyDestinationQuery q=new KOMECompanyDestinationQuery(f.service);
            KOMECompanyDestinationOptions p=q.query(f.data,f.player,session,1,"T065",1,"","");
            assertEquals(Result.OK,p.result);assertEquals(1,p.rows.size());assertTrue(p.hasMore);assertEquals("C1",p.nextCompanyId);assertEquals(0,checks.get());
            Row deferred=p.rows.get(0);assertEquals(EvaluationState.DEFERRED_ROUTE_LIMIT,deferred.evaluationState);
            assertNull(deferred.status);assertNull(deferred.consequence);assertEquals("",deferred.denialCode);assertEquals("",deferred.confirmationFingerprint);
            assertFalse(deferred.canStartNow);assertFalse(deferred.canArriveNow);assertEquals(-1,deferred.distance);assertTrue(deferred.route.isEmpty());
            KOMECompanyDestinationOptions copy=KOMEPacketCompanyDestinationResponse.validated(p).snapshot();
            KOMEPacketCompanyDestinationRequest cursor=new KOMEPacketCompanyDestinationRequest(copy.session,2,copy.destination,8,copy.nextCompanyId,copy.catalogueToken);
            io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();
            try {
                cursor.toBytes(bytes);KOMEPacketCompanyDestinationRequest decoded=new KOMEPacketCompanyDestinationRequest();decoded.fromBytes(bytes);
                KOMECompanyDestinationOptions next=q.query(f.data,f.player,decoded.session,decoded.sequence,decoded.destination,decoded.limit,decoded.afterCompanyId,decoded.catalogueToken);
                assertEquals(1,next.rows.size());assertEquals("C2",next.rows.get(0).companyId);assertTrue(next.rows.get(0).canStartNow);assertFalse(next.hasMore);
            }finally{bytes.release();}
            assertTrue(f.data.armyMovements.isEmpty());assertEquals(1,f.company.movementAllowance);
        }
    }
    @Test public void excessiveMiddleRouteDoesNotHideFollowingCompany() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            chain(f,65);f.company.currentTile=f.unit.currentTile="T064";add(f,"C2","T001");add(f,"C3","T064");
            KOMECompanyDestinationOptions p=new KOMECompanyDestinationQuery(f.service).query(f.data,f.player,session,1,"T065",8,"","");
            assertEquals(3,p.rows.size());assertEquals(EvaluationState.COMPLETE,p.rows.get(0).evaluationState);
            assertEquals(EvaluationState.DEFERRED_ROUTE_LIMIT,p.rows.get(1).evaluationState);
            assertEquals(EvaluationState.COMPLETE,p.rows.get(2).evaluationState);assertTrue(p.rows.get(2).canStartNow);
            assertEquals("C3",p.nextCompanyId);assertFalse(p.hasMore);KOMEPacketCompanyDestinationResponse.validated(p);
        }
    }

    @Test public void staleNativeControllerMetadataCannotCombineWithOperatorPrivileges() throws Exception {
        for(String authority:Arrays.asList(KOMEArmyCompany.AUTHORITY_NATIVE,KOMEArmyCompany.AUTHORITY_NATIVE_RECLAIM,"UNKNOWN"))
        try(Fixture f=new Fixture(false,1)) {
            f.company.owner=f.unit.owner=new UUID(9,9);f.company.controllerAuthority=authority;
            f.company.temporaryController=f.player.id;f.player.operator=true;
            f.data.lastKnownPlayerFactions.put(f.company.owner,"gondor");
            assertTrue(f.service.canPlayerControlCompany(f.data,f.player,f.company)); // Legacy command privilege remains.
            assertTrue(f.evaluate().allowed());assertFalse(f.service.canPlayerConsiderCompany(f.data,f.player,f.company));
            assertTrue(query(f).rows.isEmpty());
        }
    }
    @Test public void configuredFootTwoMountedFourRoundTripWithoutDefaultAssumptions() throws Exception {
        config.set("movement.footOrMixedTilesPerDay","2","movement.fullyMountedTilesPerDay","4");
        for(boolean mounted:Arrays.asList(false,true))try(Fixture f=new Fixture(mounted,2)) {
            KOMECompanyDestinationOptions p=KOMEPacketCompanyDestinationResponse.validated(query(f)).snapshot();Row r=p.rows.get(0);
            assertEquals(mounted?4:2,r.tilesPerDay);assertEquals(mounted?4:2,r.remainingAllowance);
            assertEquals(mounted?Entitlement.MOUNTED:Entitlement.FOOT_OR_MIXED,r.entitlement);
            assertTrue(r.canArriveNow);assertEquals(f.evaluate().confirmation.serviceFingerprint,r.confirmationFingerprint);
        }
    }
    @Test public void equalConfiguredEntitlementsStillPreserveCompositionClassification() throws Exception {
        config.set("movement.footOrMixedTilesPerDay","1","movement.fullyMountedTilesPerDay","1");
        for(boolean mounted:Arrays.asList(false,true))try(Fixture f=new Fixture(mounted,1)) {
            Row r=KOMEPacketCompanyDestinationResponse.validated(query(f)).snapshot().rows.get(0);
            assertEquals(1,r.tilesPerDay);assertEquals(mounted?Entitlement.MOUNTED:Entitlement.FOOT_OR_MIXED,r.entitlement);
        }
    }
    @Test public void alreadyAtDestinationIsAnExplanatoryBlockedRow() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            f.company.currentTile=f.unit.currentTile=f.destination;Row r=row(f);
            assertEquals(BLOCKED,r.status);assertEquals("INVALID_DESTINATION",r.denialCode);
            assertFalse(r.canStartNow);assertFalse(r.canArriveNow);assertEquals("",r.confirmationFingerprint);
            KOMEPacketCompanyDestinationResponse.validated(query(f));
        }
    }
    @Test public void knownCampaignPlusMissingMemberIsBlockedButUnclassifiedMembershipIsExcluded() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            f.company.units.add(new UUID(88,88));Row r=row(f);assertEquals("INELIGIBLE_MEMBER",r.denialCode);assertEquals(BLOCKED,r.status);
            f.data.hiredUnits.remove(f.unit.entity);assertTrue(query(f).rows.isEmpty());
            f.company.units.clear();assertTrue(query(f).rows.isEmpty());
        }
    }
    @Test public void timedOverrideDoesNotPublishDailyEtaAsTimedAuthority() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            f.data.movementSecondsPerTileOverride=5;f.company.movementAllowance=0;
            Row r=KOMEPacketCompanyDestinationResponse.validated(query(f)).snapshot().rows.get(0);
            assertEquals(KOMECompanyMovementService.ScheduleMode.TIMED_OVERRIDE,r.scheduleMode);assertTrue(r.canStartNow);assertFalse(r.canArriveNow);
            assertFalse(r.timingAvailable);assertEquals(0,r.futureBoundaries);assertEquals(0,r.nextBoundaryMillis);assertEquals(0,r.estimatedCompletionMillis);
        }
    }
    @Test public void oversizedControlledMembershipFailsBeforeArrivalWork() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            for(int i=0;i<MAX_MEMBERS;i++)f.company.units.add(new UUID(88,i));
            AtomicInteger checks=new AtomicInteger();f.world.arrivalInspection=checks::incrementAndGet;
            assertEquals(Result.COMPANY_LIMIT,query(f).result);assertEquals(0,checks.get());assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void normalEightRowPagesReachTwentyCompaniesWithoutDuplicates() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            for(int i=2;i<=20;i++)add(f,"C"+i,"T001");
            KOMECompanyDestinationQuery q=new KOMECompanyDestinationQuery(f.service);String after="",token="";Set<String> seen=new HashSet<String>();
            int[] counts={8,8,4};
            for(int i=0;i<counts.length;i++) {
                KOMECompanyDestinationOptions p=q.query(f.data,f.player,session,i+1,f.destination,8,after,token);
                assertEquals(Result.OK,p.result);assertEquals(counts[i],p.rows.size());for(Row r:p.rows)assertTrue(seen.add(r.companyId));
                assertEquals(i<2,p.hasMore);after=p.nextCompanyId;token=p.catalogueToken;
                KOMEPacketCompanyDestinationResponse.forQuery(p);
            }
            assertEquals(20,seen.size());
        }
    }
    @Test public void rapidPagingUsesBurstAndStillReturnsLiveCanonicalRows() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            add(f,"C2","T002");kome.common.KOMEAccessFixture connection=new kome.common.KOMEAccessFixture();connection.context.getServerHandler().playerEntity=f.player;
            try {
                KOMEPacketCompanyDestinationRequest.Handler handler=new KOMEPacketCompanyDestinationRequest.Handler();
                KOMECompanyDestinationOptions first=((KOMEPacketCompanyDestinationResponse)handler.onMessage(
                    new KOMEPacketCompanyDestinationRequest(session,1,f.destination,1,"",""),connection.context)).snapshot();
                KOMECompanyDestinationOptions next=((KOMEPacketCompanyDestinationResponse)handler.onMessage(
                    new KOMEPacketCompanyDestinationRequest(session,2,f.destination,1,first.nextCompanyId,first.catalogueToken),connection.context)).snapshot();
                assertEquals(Result.OK,first.result);assertEquals(Result.OK,next.result);assertEquals("C2",next.rows.get(0).companyId);assertTrue(next.rows.get(0).canArriveNow);
            }finally{KOMEPacketHandler.clearPendingServerTasks();}
        }
    }
    @Test public void rapidDestinationChangeUsesBurstAndRejectsSupersededReply() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            kome.common.KOMEAccessFixture connection=new kome.common.KOMEAccessFixture();connection.context.getServerHandler().playerEntity=f.player;
            kome.client.KOMECompanyDestinationClientState client=new kome.client.KOMECompanyDestinationClientState();Object clientConnection=new Object();client.start(clientConnection);
            try {
                KOMEPacketCompanyDestinationRequest.Handler handler=new KOMEPacketCompanyDestinationRequest.Handler();
                KOMECompanyDestinationOptions old=((KOMEPacketCompanyDestinationResponse)handler.onMessage(client.request("T002",8,"",""),connection.context)).snapshot();
                KOMECompanyDestinationOptions current=((KOMEPacketCompanyDestinationResponse)handler.onMessage(client.request("T003",8,"",""),connection.context)).snapshot();
                assertEquals(Result.OK,old.result);assertEquals(Result.OK,current.result);assertFalse(client.accept(old,clientConnection));assertTrue(client.accept(current,clientConnection));
                assertEquals("T003",client.latest().destination);
            }finally{KOMEPacketHandler.clearPendingServerTasks();}
        }
    }

    @Test public void sharedArrivalBudgetStopsBeforeChunkInspectionAndDoesNotChangeSuccessfulEvaluation() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            AtomicInteger inspections=new AtomicInteger();f.world.arrivalInspection=inspections::incrementAndGet;
            KOMECompanyMovementService.ArrivalBudget budget=new KOMECompanyMovementService.ArrivalBudget(3);
            KOMECompanyMovementService.Evaluation bounded=f.service.evaluate(f.data,f.player,"C1",f.destination,budget);
            assertEquals(f.evaluate().confirmation.serviceFingerprint,bounded.confirmation.serviceFingerprint);
            int before=inspections.get();
            try{f.service.evaluate(f.data,f.player,"C1",f.destination,budget);fail();}catch(KOMECompanyMovementService.EvaluationLimitExceeded expected) { }
            assertEquals(before,inspections.get());assertEquals(1,f.company.movementAllowance);assertTrue(f.data.armyMovements.isEmpty());
        }
    }
}
