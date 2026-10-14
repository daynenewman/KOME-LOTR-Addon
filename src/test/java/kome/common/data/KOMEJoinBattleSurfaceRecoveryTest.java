package kome.common.data;

import java.lang.reflect.Field;
import java.util.*;
import kome.common.KOMEAccessFixture;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.management.PlayerManager;
import org.junit.*;
import static org.junit.Assert.*;
import kome.common.data.KOMEJoinBattleDeploymentReceipt.State;
import static kome.common.data.KOMEConflictContracts.*;
import static kome.common.data.KOMEConflictRecord.*;
import static kome.common.data.KOMEJoinBattleDeploymentReceipt.*;

/** Real placement/application with production receipt/retry services; no server/client launch. */
public class KOMEJoinBattleSurfaceRecoveryTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    @After public void reset(){KOMEJoinBattleEgressService.INSTANCE.resetSession();KOMEJoinBattleActionTokenService.INSTANCE.clearForTests();}

    @Test public void partialApplicationFailureCannotCloseAndLaterRetryClosesExactlyOnce() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();KOMEWorldData data=f.access.data;pending(data,f);
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding binding=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            Physical physical=new Physical(f);f.world.publicationReady=false;
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(data,f.access.player,100L,physical));
            assertEquals("The native move occurred before terrain publication rejected the attempt",74D,f.access.player.posY,0D);
            assertEquals(State.PENDING_EGRESS,data.getJoinBattleDeploymentReceipts().get("JB1").getState());
            assertNull(data.getJoinBattleDeploymentReceipts().get("JB1").getClosureOutcome());
            f.world.publicationReady=true;
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.tick(data,f.access.player,5100L,physical));assertEquals(1,physical.applies);
            assertTrue(KOMEJoinBattleEgressService.INSTANCE.tick(data,f.access.player,60100L,physical));
            assertEquals(2,physical.applies);assertClosed(data);
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(data,f.access.player,120100L,physical));assertEquals(2,physical.applies);
        }
    }
    @Test public void failedSurfacePreparationSurvivesRestartAndLoginThenUsesAvailableTerrain() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();KOMEWorldData data=f.access.data;pending(data,f);
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding binding=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            f.world.noGround=true;Physical physical=new Physical(f);
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(data,f.access.player,100L,physical));assertEquals(0,physical.applies);
            data.initializeIntegratedWorld();NBTTagCompound saved=new NBTTagCompound();data.writeToNBT(saved);
            KOMEWorldData loaded=new KOMEWorldData("surface-restart");loaded.readFromNBT(saved);
            assertEquals(State.PENDING_EGRESS,loaded.getJoinBattleDeploymentReceipts().get("JB1").getState());
            KOMEJoinBattleEgressService.INSTANCE.resetSession();f.world.noGround=false;
            KOMEJoinBattleEgressService.INSTANCE.onLogin(loaded,f.access.player);
            assertTrue(KOMEJoinBattleEgressService.INSTANCE.tick(loaded,f.access.player,200L,physical));assertClosed(loaded);
            assertEquals(1,physical.applies);
        }
    }
    @Test public void strategicallyPendingFormalRetreatCannotUseAnOtherwiseReadySurface() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();KOMEWorldData data=f.access.data;pending(data,f);
        KOMEArmyMovementOrder order=new KOMEArmyMovementOrder();order.id="M1";order.companyId="C1";
        order.formalRetreatBatch=new KOMEFormalRetreatBatch(UUID.randomUUID(),f.access.player.id,"CF1","T100","JB1",3L,
            Arrays.asList(new KOMEFormalRetreatBatch.Member("C1","M1",f.access.player.id,Arrays.asList("T100","T101"),1,0,
                KOMEFormalRetreatBatch.Progress.PREPARED)),KOMEFormalRetreatBatch.Phase.STRATEGIC_PENDING,false);
        data.armyMovements.put(order.id,order);
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding binding=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            assertTrue(KOMEJoinBattlePhysicalAccess.prepareEgress(f.access.player,data.getJoinBattleDeploymentReceipts().get("JB1")).ready);
            Physical physical=new Physical(f);
            KOMEJoinBattleEgressService.CompletionResult result=KOMEJoinBattleEgressService.INSTANCE.requestAndCompleteDetailed(
                data,f.access.player,"JB1",100L,"Formal retreat",physical);
            assertEquals(KOMEJoinBattleEgressService.Completion.PENDING,result.completion);assertTrue(result.reason.contains("first departure"));
            assertEquals(0,physical.prepares);assertEquals(0,physical.applies);assertEquals(State.PENDING_EGRESS,data.getJoinBattleDeploymentReceipts().get("JB1").getState());
        }
    }
    @Test public void oldConflictCallbackAndAnotherPlayerCannotCloseNewDeployment() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=fixture();KOMEWorldData data=f.access.data;pending(data,f);
        try(KOMEJoinBattleReturnGeometryTest.ReturnBinding binding=new KOMEJoinBattleReturnGeometryTest.ReturnBinding(f)){
            Physical physical=new Physical(f);
            KOMEAccessFixture other=new KOMEAccessFixture();
            assertEquals(KOMEJoinBattleEgressService.Completion.INVALID,KOMEJoinBattleEgressService.INSTANCE.requestAndCompleteDetailed(
                data,other.player,"JB1",100L,"wrong player",physical).completion);assertEquals(0,physical.prepares);
            assertTrue(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(data,f.access.player,100L,physical));
            KOMEConflictRecord old=data.getConflictService().get("T100");
            KOMEConflictRecord next=KOMEConflictServiceTest.ok(data.getConflictService().start("T100",KOMEConflictRecord.State.ORDINARY,
                ExpectedConflict.at(old.getConflictId(),old.getRevision()),Collections.<GarrisonSeed>emptyList(),context(110L)));
            KOMEJoinBattleDeploymentReceipt newer=KOMEJoinBattleDeploymentReceipt.copyOf(data.getJoinBattleDeploymentReceipts().get("JB1"))
                .receiptId("JB2").actionToken("new-battle").conflictId(next.getConflictId()).acceptedConflictRevision(next.getRevision())
                .createdAtMillis(111L).updatedAtMillis(112L).state(State.DEPLOYED).deployedAtMillis(112L)
                .egressRequestedAtMillis(null).egressReason("").closedAtMillis(null).closureOutcome(null).build();
            data.getJoinBattleDeploymentReceipts().publishNew(newer);
            assertEquals(0,KOMEJoinBattleEgressService.INSTANCE.onConflictEnded(data,old.getConflictId(),120L,physical));
            assertFalse(KOMEJoinBattleEgressService.INSTANCE.reconcilePlayer(data,f.access.player,120L,physical));
            assertSame(newer,data.getJoinBattleDeploymentReceipts().get("JB2"));assertEquals(1,physical.applies);
        }
    }
    static KOMEJoinBattleReturnGeometryTest.Fixture fixture() throws Exception {
        KOMEJoinBattleReturnGeometryTest.Fixture f=new KOMEJoinBattleReturnGeometryTest.Fixture();f.world.loadedEntityList.clear();
        f.access.pledge(LOTRFaction.GONDOR);
        f.world.manager=KOMEAccessFixture.allocate(ReturnManager.class);f.world.publicationReady=true;
        Field chunks=EntityPlayerMP.class.getDeclaredField("loadedChunks");chunks.setAccessible(true);chunks.set(f.access.player,new LinkedList());
        return f;
    }
    static void pending(KOMEWorldData data,KOMEJoinBattleReturnGeometryTest.Fixture f){
        KOMEConflictRecord c=KOMEConflictServiceTest.ok(data.getConflictService().start("T100",KOMEConflictRecord.State.ORDINARY,
            ExpectedConflict.absent(),Collections.<GarrisonSeed>emptyList(),context(1L)));
        KOMEConflictServiceTest.ok(data.getConflictService().end("T100",ExpectedConflict.at(c.getConflictId(),c.getRevision()),context(4L)));
        data.getJoinBattleDeploymentReceipts().publishNew(KOMEJoinBattleReturnGeometryTest.returnReceipt(f,new Pose(0,8.5D,82.5D,9.5D,0F,0F)));
    }
    static void assertUnrelatedJoin(KOMEWorldData data,KOMEJoinBattleReturnGeometryTest.Fixture f) throws Exception {
        assertNull(KOMEJoinBattleEgressService.INSTANCE.findOpen(data,f.access.player.id));
        KOMEConflictRecord conflict=KOMEConflictServiceTest.ok(data.getConflictService().start("T101",KOMEConflictRecord.State.ORDINARY,
            ExpectedConflict.absent(),Collections.<GarrisonSeed>emptyList(),context(1000L)));
        conflict=KOMEConflictServiceTest.ok(data.getConflictService().beginFactionParticipation("T101",
            ExpectedConflict.at(conflict.getConflictId(),conflict.getRevision()),"gondor",context(1001L)));
        KOMEArmyCompany company=new KOMEArmyCompany();company.id="C2";company.name="Relief Company";
        company.owner=java.util.UUID.randomUUID();company.ownerName="Owner";company.faction=company.nativeFaction="gondor";
        company.currentTile="T101";company.status=KOMEArmyCompany.STATIONED;
        KOMEHiredUnitRecord unit=new KOMEHiredUnitRecord();unit.entity=UUID.randomUUID();unit.owner=unit.sourcePlayer=company.owner;
        unit.companyId=company.id;unit.companyName=company.name;unit.currentTile=unit.sourceTileId="T101";
        unit.unitFaction=unit.populationOwningFaction="gondor";unit.type=KOMEPopulationType.OFFENSIVE;
        unit.assignPersistedUnitClass(KOMEHiredUnitClass.CAMPAIGN);unit.cost=unit.baseCost=unit.populationSpent=20;
        company.units.add(unit.entity);company.totalPopulation=company.groundPopulation=20;
        data.lastKnownPlayerFactions.put(company.owner,"gondor");data.armyCompanies.put(company.id,company);data.hiredUnits.put(unit.entity,unit);
        conflict=KOMEConflictServiceTest.ok(data.getConflictService().commit("T101",ExpectedConflict.at(conflict.getConflictId(),conflict.getRevision()),
            new CommitmentInput("C2",KOMEHiredUnitClass.CAMPAIGN,EntryOrigin.LEGAL_ARRIVAL,"M-C2"),context(1002L)));
        KOMEJoinBattleService.Projection view=KOMEJoinBattleService.INSTANCE.evaluate(data,f.access.player,"T101");assertTrue(view.reason.toString(),view.isAllowed());
        String token=KOMEJoinBattleActionTokenService.INSTANCE.issue(f.access.player.id,view);
        KOMEJoinBattleEntryService.PhysicalAccess physical=new KOMEJoinBattleEntryService.PhysicalAccess(){
            boolean deployed;
            public long now(){return 100000L;}
            public KOMEJoinBattleEntryService.Preparation prepare(KOMEWorldData ignored,EntityPlayerMP player,KOMEArmyCompany selected,String tile,String id,long revision){
                return KOMEJoinBattleEntryService.Preparation.unmounted(new Pose(0,8.5D,74D,9.5D,0F,0F),new Pose(0,100D,74D,100D,0F,0F));
            }
            public boolean isAtDestination(EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt){return deployed;}
            public KOMEJoinBattleService.Reason preflight(KOMEWorldData ignored,EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt){return KOMEJoinBattleService.Reason.ALLOWED;}
            public KOMEJoinBattleEntryService.PhysicalResult deploy(EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt){deployed=true;return KOMEJoinBattleEntryService.PhysicalResult.deployed();}
            public KOMEJoinBattleEntryService.PhysicalResult rollback(EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt){return KOMEJoinBattleEntryService.PhysicalResult.rolledBack(null);}
        };
        KOMEJoinBattleEntryService.Result joined=KOMEJoinBattleEntryService.INSTANCE.enter(data,f.access.player,
            new KOMEJoinBattleEntryService.Request("T101",conflict.getConflictId(),conflict.getRevision(),"C2",token),physical);
        assertEquals(joined.message,KOMEJoinBattleEntryService.Status.DEPLOYED,joined.status);
        assertClosed(data);assertNotEquals("JB1",joined.receiptId);
    }
    static void assertClosed(KOMEWorldData data){
        KOMEJoinBattleDeploymentReceipt receipt=data.getJoinBattleDeploymentReceipts().get("JB1");
        assertEquals(State.CLOSED,receipt.getState());assertEquals(ClosureOutcome.EGRESS_COMPLETED,receipt.getClosureOutcome());
    }
    static final class Physical implements KOMEJoinBattleEgressService.PhysicalAccess {
        final KOMEJoinBattleReturnGeometryTest.Fixture f;int prepares,applies;
        Physical(KOMEJoinBattleReturnGeometryTest.Fixture f){this.f=f;}
        public EntityPlayerMP findOnline(UUID id){return f.access.player.id.equals(id)?f.access.player:null;}
        public KOMEJoinBattleEgressService.Preparation prepare(EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt){prepares++;return KOMEJoinBattlePhysicalAccess.prepareEgress(player,receipt);}
        public KOMEJoinBattleEgressService.PhysicalResult egress(EntityPlayerMP player,KOMEJoinBattleDeploymentReceipt receipt,KOMEJoinBattleEgressService.Preparation prepared){applies++;return KOMEJoinBattlePhysicalAccess.egress(player,receipt,prepared);}
    }
    public static class ReturnManager extends PlayerManager {
        private ReturnManager(){super(null);}
        @Override public void updatePlayerPertinentChunks(EntityPlayerMP player){}
        @Override public boolean isPlayerWatchingChunk(EntityPlayerMP player,int x,int z){return true;}
    }
    static Context context(long now){return new Context(now,"surface-return-test","test transition");}
}