package kome.common.command;

import java.util.*;
import kome.common.data.*;
import kome.common.network.*;
import kome.common.command.KOMECompanyMovementServiceTest.Fixture;
import lotr.common.fac.*;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.data.KOMECompanyDestinationOptions.*;

public class KOMECompanyOriginQueryTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    private final UUID session=new UUID(71,84);
    @Before public void before()throws Exception{config=new KOMEPopulationTestConfig();relation(LOTRFactionRelations.Relation.NEUTRAL);}
    @After public void after()throws Exception{relation(LOTRFactionRelations.Relation.NEUTRAL);config.close();}
    private void relation(LOTRFactionRelations.Relation r){LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.MORDOR,r);}
    private KOMECompanySourceOptions source(Fixture f){return new KOMECompanyOriginQuery(f.service).source(f.data,f.player,session,1,"T001","","");}
    private KOMECompanyDestinationOptions target(Fixture f){return new KOMECompanyOriginQuery(f.service).target(f.data,f.player,session,2,"T001","C1",f.destination);}
    private KOMEArmyCompany add(Fixture f,String id,String tile){
        KOMEArmyCompany c=new KOMEArmyCompany();c.readFromNBT(f.company.writeToNBT());c.id=id;c.name=id;c.currentTile=tile;c.units.clear();
        KOMEHiredUnitRecord u=new KOMEHiredUnitRecord();u.readFromNBT(f.unit.writeToNBT());
        u.entity=UUID.nameUUIDFromBytes(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));u.companyId=id;u.currentTile=tile;
        c.units.add(u.entity);f.data.armyCompanies.put(id,c);f.data.hiredUnits.put(u.entity,u);return c;
    }
    @Test public void sourceIsTileLocalCampaignControlledAndMetadataOnly()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            assertEquals(1,source(f).rows.size());add(f,"C2","T001");add(f,"C3","T002");
            KOMEArmyCompany other=add(f,"C4","T001");other.owner=new UUID(9,9);
            KOMEArmyCompany ordinary=add(f,"C5","T001");KOMEHiredUnitRecord unit=f.data.hiredUnits.get(ordinary.units.get(0));
            NBTTagCompound n=unit.writeToNBT();n.setString("UnitClass","ORDINARY");unit.readFromNBT(n);
            KOMECompanySourceOptions p=source(f);assertEquals(2,p.rows.size());
            assertEquals("C1",p.rows.get(0).companyId);assertEquals("C2",p.rows.get(1).companyId);
            assertEquals(f.company.units.size(),p.rows.get(0).units);assertEquals(f.company.movementAllowance,p.rows.get(0).allowance);
            assertTrue(f.data.armyMovements.isEmpty());
            f.data.armyCompanies.clear();assertTrue(source(f).rows.isEmpty());
        }
    }
    @Test public void delegatedControlUsesCanonicalAuthorityAndRevocationRejectsTarget()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            f.company.owner=f.unit.owner=new UUID(9,9);f.company.controllerAuthority=KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;
            f.company.temporaryController=f.player.id;f.data.lastKnownPlayerFactions.put(f.company.owner,"gondor");
            f.data.lastKnownPlayerFactions.put(f.player.id,"gondor");
            assertEquals(1,source(f).rows.size());assertTrue(target(f).rows.get(0).canStartNow);
            f.company.temporaryController=null;assertTrue(source(f).rows.isEmpty());assertEquals(Result.SOURCE_CHANGED,target(f).result);
        }
    }
    @Test public void exactTargetBeyondFirstEightNeverScansCatalogueOrEvaluatesOtherRoutes()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            for(int i=2;i<=20;i++)add(f,String.format(Locale.ROOT,"C%02d",i),"T001");
            add(f,"Z99","T001");
            KOMECompanyDestinationOptions first=new KOMECompanyDestinationQuery(f.service).query(f.data,f.player,session,1,f.destination,8,"","");
            for(Row row:first.rows)assertNotEquals("Z99",row.companyId);
            final int[] inspections={0};f.world.arrivalInspection=()->inspections[0]++;
            KOMECompanyDestinationOptions result=new KOMECompanyOriginQuery(f.service).target(f.data,f.player,session,2,"T001","Z99",f.destination);
            assertEquals(1,result.rows.size());assertEquals("Z99",result.rows.get(0).companyId);assertTrue(result.rows.get(0).canStartNow);
            assertFalse(result.hasMore);assertTrue(inspections[0]<=4);assertEquals("",result.catalogueToken);
        }
    }
    @Test public void staleLocationMissingCompanyAndChangedOwnerCannotBecomeAnotherCompany()throws Exception{
        for(int change=0;change<3;change++)try(Fixture f=new Fixture(false,1)){
            if(change==0)f.company.currentTile="T003";if(change==1)f.data.armyCompanies.clear();if(change==2)f.company.owner=new UUID(9,9);
            assertEquals(Result.SOURCE_CHANGED,target(f).result);assertTrue(target(f).rows.isEmpty());assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void targetSharesCanonicalExhaustionTerrainAndSameTileDenials()throws Exception{
        for(int failure=0;failure<4;failure++)try(Fixture f=new Fixture(false,1)){
            String destination=f.destination;
            if(failure==0)f.company.movementAllowance=0;
            if(failure==1)f.edge("T001","T002",KOMEConquestRouteEdge.MOUNTAIN);
            if(failure==2)f.edge("T001","T002",KOMEConquestRouteEdge.RIVER);
            if(failure==3)destination="T001";
            Row row=new KOMECompanyOriginQuery(f.service).target(f.data,f.player,session,1,"T001","C1",destination).rows.get(0);
            assertFalse(row.canStartNow);assertEquals(f.service.evaluate(f.data,f.player,"C1",destination).denial.code,row.denialCode);
            assertTrue(row.confirmationFingerprint.isEmpty());assertTrue(f.data.armyMovements.isEmpty());assertEquals(1,f.company.movementAllowance+(failure==0?1:0));
        }
    }
    @Test public void exactRouteUsesCanonicalPassBridgeAndDiplomacyRules()throws Exception{
        for(String obstacle:Arrays.asList(KOMEConquestRouteEdge.MOUNTAIN,KOMEConquestRouteEdge.RIVER))try(Fixture f=new Fixture(false,1)){
            f.edge("T001","T002",obstacle);assertFalse(target(f).rows.get(0).canStartNow);
            f.edge("T001","T002",obstacle.equals(KOMEConquestRouteEdge.MOUNTAIN)?KOMEConquestRouteEdge.MOUNTAIN_PASS:KOMEConquestRouteEdge.BRIDGE);
            assertTrue(target(f).rows.get(0).canArriveNow);
            f.data.conquestTiles.get(f.destination).setCurrentRulingFaction("mordor");
            Row row=target(f).rows.get(0);assertFalse(row.canStartNow);assertEquals(f.evaluate().denial.code,row.denialCode);
        }
    }
    @Test public void friendlyMultidayCreateAndJoinUseSameProjectionAndSubmission()throws Exception{
        for(int kind=0;kind<4;kind++)try(Fixture f=new Fixture(false,kind==1?2:1)){
            if(kind==2)f.hostileDestination();if(kind==3)f.hostileConflict();
            Row row=target(f).rows.get(0);KOMECompanyMovementService.Evaluation expected=f.evaluate();
            assertEquals(expected.routeTiles,row.route);assertEquals(expected.status,row.status);assertEquals(expected.consequence,row.consequence);
            assertEquals(expected.confirmation.serviceFingerprint,row.confirmationFingerprint);
            KOMECompanyMovementResult result=KOMECompanyMapMovementSubmission.submit(f.service,f.data,f.player,session,1,2,"C1",f.destination,row.legacyFingerprint,row.confirmationFingerprint);
            assertTrue(result.detail,result.accepted());assertEquals(1,f.data.armyMovements.size());
        }
    }
    @Test public void staleConfirmationStillRejectedByExistingExecutor()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            Row row=target(f).rows.get(0);f.company.movementBoundaryMillis++;
            KOMECompanyMovementResult result=KOMECompanyMapMovementSubmission.submit(f.service,f.data,f.player,session,1,2,"C1",f.destination,row.legacyFingerprint,row.confirmationFingerprint);
            assertFalse(result.accepted());assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void destinationAwareSourcePageExcludesOtherTilesOrdinaryAndUnauthorizedAndMatchesCanonicalRows()throws Exception{
        for(int kind=0;kind<5;kind++)try(Fixture f=new Fixture(false,kind==1?2:1)){
            add(f,"C2","T001");add(f,"C3","T002");KOMEArmyCompany other=add(f,"C4","T001");other.owner=new UUID(9,9);
            KOMEArmyCompany ordinary=add(f,"C5","T001");KOMEHiredUnitRecord u=f.data.hiredUnits.get(ordinary.units.get(0));NBTTagCompound n=u.writeToNBT();n.setString("UnitClass","ORDINARY");u.readFromNBT(n);
            if(kind==2)f.company.movementAllowance=0;if(kind==3)f.hostileDestination();if(kind==4)f.hostileConflict();
            KOMECompanyDestinationOptions p=new KOMECompanyOriginQuery(f.service).destination(f.data,f.player,session,1,"T001",f.destination,"","");
            assertEquals(Result.OK,p.result);assertEquals(2,p.rows.size());assertEquals("C1",p.rows.get(0).companyId);assertEquals("C2",p.rows.get(1).companyId);
            for(Row row:p.rows){KOMECompanyMovementService.Evaluation canonical=f.service.evaluate(f.data,f.player,row.companyId,f.destination);
                assertEquals("T001",row.currentTile);assertEquals(f.destination,row.destinationTile);assertEquals(canonical.status,row.status);assertEquals(canonical.routeTiles,row.route);assertEquals(canonical.consequence,row.consequence);
                assertEquals(canonical.denial==null?"":canonical.denial.code,row.denialCode);assertEquals(canonical.confirmation==null?"":canonical.confirmation.serviceFingerprint,row.confirmationFingerprint);assertEquals(1,row.unitCount);assertTrue(row.populationCenti>0);
            }
            assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void sourceDestinationPagingCompletesBeyondEightAndInvalidatesChangedSourceCatalogue()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            for(int i=2;i<=20;i++)add(f,String.format(Locale.ROOT,"C%02d",i),"T001");add(f,"OTHER_TILE","T002");
            KOMECompanyOriginQuery query=new KOMECompanyOriginQuery(f.service);Set<String> seen=new HashSet<>();String after="",token="";KOMECompanyDestinationOptions page;
            do{page=query.destination(f.data,f.player,session,1,"T001",f.destination,after,token);assertEquals(Result.OK,page.result);assertTrue(page.rows.size()<=8);
                for(Row row:page.rows){assertEquals("T001",row.currentTile);assertTrue(seen.add(row.companyId));}after=page.nextCompanyId;token=page.catalogueToken;
            }while(page.hasMore);
            assertEquals(20,seen.size());add(f,"NEW","T001");assertEquals(Result.CATALOGUE_CHANGED,query.destination(f.data,f.player,session,2,"T001",f.destination,after,token).result);
        }
    }
    @Test public void sourceDestinationPageSharesArrivalBudgetAndContinuationAdvancesWithoutSkippingCompanies()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            for(int i=1;i<=20;i++){String id=String.format(Locale.ROOT,"T%03d",i);KOMEConquestTile tile=new KOMEConquestTile(id);tile.claim("gondor",1);tile.setAnchor(0,i*16,64,0);f.data.conquestTiles.put(id,tile);}
            for(int i=1;i<=20;i++)for(int j=i+1;j<=20;j++)f.edge(String.format(Locale.ROOT,"T%03d",i),String.format(Locale.ROOT,"T%03d",j),j==i+1?KOMEConquestRouteEdge.OPEN:KOMEConquestRouteEdge.RIVER);
            for(int i=2;i<=8;i++)add(f,"C"+i,"T001");final int[] checks={0};f.world.arrivalInspection=()->checks[0]++;
            KOMECompanyOriginQuery query=new KOMECompanyOriginQuery(f.service);Set<String> ids=new HashSet<>();String after="",token="";KOMECompanyDestinationOptions page;
            do{checks[0]=0;page=query.destination(f.data,f.player,session,1,"T001","T020",after,token);assertEquals(Result.OK,page.result);assertFalse(page.rows.isEmpty());assertTrue(page.rows.size()<=3);assertTrue(checks[0]<=MAX_ARRIVAL_CHECKS);
                for(Row row:page.rows)assertTrue(ids.add(row.companyId));after=page.nextCompanyId;token=page.catalogueToken;
            }while(page.hasMore);
            assertEquals(8,ids.size());assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void sourcePagesAreBoundedAndInvalidatedWhenCatalogueChanges()throws Exception{
        try(Fixture f=new Fixture(false,1)){
            for(int i=2;i<=20;i++)add(f,String.format(Locale.ROOT,"C%02d",i),"T001");
            KOMECompanyOriginQuery q=new KOMECompanyOriginQuery(f.service);KOMECompanySourceOptions first=source(f);
            assertEquals(8,first.rows.size());assertTrue(first.more);
            KOMECompanySourceOptions next=q.source(f.data,f.player,session,2,"T001",first.next,first.token);
            assertEquals(8,next.rows.size());Set<String> ids=new HashSet<String>();for(KOMECompanySourceOptions.Row r:first.rows)assertTrue(ids.add(r.companyId));
            for(KOMECompanySourceOptions.Row r:next.rows)assertTrue(ids.add(r.companyId));
            add(f,"NEW","T001");assertEquals(Result.CATALOGUE_CHANGED,q.source(f.data,f.player,session,3,"T001",first.next,first.token).result);
        }
    }
}