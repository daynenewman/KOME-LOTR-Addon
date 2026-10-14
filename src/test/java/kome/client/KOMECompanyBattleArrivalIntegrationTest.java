package kome.client;

import java.util.*;
import kome.client.gui.*;
import kome.common.command.KOMECompanyMapMovementArrivalTest;
import kome.common.data.*;
import kome.common.network.*;
import lotr.common.fac.*;
import net.minecraft.entity.player.EntityPlayerMP;
import org.junit.*;
import static org.junit.Assert.*;

/** Exact map confirmation -> native physical/conflict arrival -> real KOM-19 view/selection authority. */
public class KOMECompanyBattleArrivalIntegrationTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    private cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previousNetwork;
    private KOMEMapRadialInteractionTest.RecordingNetwork network;
    @Before public void before() throws Exception {
        config=new KOMEPopulationTestConfig();previousNetwork=KOMEPacketHandler.network;
        network=kome.common.KOMEAccessFixture.allocate(KOMEMapRadialInteractionTest.RecordingNetwork.class);network.sent=new ArrayList<cpw.mods.fml.common.network.simpleimpl.IMessage>();KOMEPacketHandler.network=network;
    }
    @After public void after() throws Exception {KOMEPacketHandler.network=previousNetwork;LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.MORDOR,LOTRFactionRelations.Relation.NEUTRAL);config.close();}
    @Test public void createMovementCreatesActualConflictBeforeExistingJoinScreenUsesIt() throws Exception {exercise(false,false);}
    @Test public void joinMovementKeepsExistingConflictBeforeExistingJoinScreenUsesIt() throws Exception {exercise(true,false);}
    @Test public void withdrawnJoinDenialAfterCreateKeepsSuccessfulPhysicalMovementAndCommitment() throws Exception {exercise(false,true);}
    @Test public void withdrawnJoinDenialAfterJoinKeepsSuccessfulPhysicalMovementAndCommitment() throws Exception {exercise(true,true);}
    private void exercise(boolean join,boolean withdraw) throws Exception {
        Object connection=new Object();KOMECompanyDestinationClientState state=new KOMECompanyDestinationClientState();state.start(connection);
        KOMECompanyDestinationSelector selector=new KOMECompanyDestinationSelector(state);
        KOMEPacketCompanyDestinationRequest destination=selector.open("T002");
        new KOMECompanyMapMovementArrivalTest().exercise(join,new KOMECompanyMapMovementArrivalTest.Scenario() {
            @Override public KOMEPacketCompanyMovementRequest confirm(KOMECompanyDestinationOptions.Row row) {
                assertEquals(join?kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_JOIN_CONFLICT:
                    kome.common.command.KOMECompanyMovementService.Consequence.EXPECTED_CREATE_CONFLICT,row.consequence);
                KOMECompanyDestinationOptions page=new KOMECompanyDestinationOptions(destination.session,destination.sequence,destination.destination,
                    KOMECompanyDestinationOptions.Result.OK,Collections.singletonList(row),UUID.randomUUID().toString(),row.companyId,false);
                page=KOMEPacketCompanyDestinationResponse.validated(page).snapshot();assertTrue(state.accept(page,connection));assertTrue(selector.accept(page,0));selector.select("C1");
                assertTrue(selector.canConfirmAndJoin());return KOMEPopulationWire.copyForPublication(selector.confirm(true),KOMEPacketCompanyMovementRequest::new);
            }
            @Override public KOMEPacketJoinBattleViewRequest afterMovement(KOMECompanyMovementResult result,KOMEWorldData data,EntityPlayerMP actor) {
                assertEquals(KOMECompanyMovementResult.Outcome.ARRIVED,result.outcome);assertEquals(KOMEArmyMovementOrder.CONFLICT_HELD,result.orderStatus);
                assertTrue(state.acceptMovement(result,connection));assertNull(selector.acceptMovement(result));
                KOMEConflictRecord actual=data.getConflictService().get(result.destination);assertNotNull(actual.getCommitments().get("C1"));
                if(withdraw) {
                    KOMEConflictService service=data.getConflictService();long now=actual.getLastTransition().timestampMillis+1;
                    KOMEConflictService.Result registered=service.registerPlayer(actual.getTileId(),KOMEConflictContracts.ExpectedConflict.at(actual.getConflictId(),actual.getRevision()),
                        actor.getUniqueID(),"gondor",new KOMEConflictContracts.Context(now,"test","register"));assertTrue(registered.isSuccess());
                    KOMEConflictRecord current=registered.record;
                    assertTrue(service.withdrawPlayer(current.getTileId(),KOMEConflictContracts.ExpectedConflict.at(current.getConflictId(),current.getRevision()),
                        actor.getUniqueID(),new KOMEConflictContracts.Context(now+1,"test","withdraw")).isSuccess());
                }
                return KOMEPopulationWire.copyForPublication(selector.joinViewRequest(),KOMEPacketJoinBattleViewRequest::new);
            }
            @Override public void afterView(KOMEPacketJoinBattleViewResponse view) {
                view=KOMEPopulationWire.copyForPublication(view,KOMEPacketJoinBattleViewResponse::new);
                assertFalse(view.conflictId.isEmpty());String preferred=selector.acceptJoinView(view);assertEquals("C1",preferred);
                KOMEGuiJoinBattle screen=new KOMEGuiJoinBattle(view,preferred);
                assertEquals(!withdraw,screen.beginPreferredEntry());assertFalse(screen.beginPreferredEntry());
                if(withdraw)assertTrue(network.sent.isEmpty());
                else {
                    assertEquals(1,network.sent.size());KOMEPacketJoinBattleSelectionRequest joinRequest=(KOMEPacketJoinBattleSelectionRequest)network.sent.get(0);
                    assertEquals(view.conflictId,joinRequest.conflictId);assertEquals(view.conflictRevision,joinRequest.conflictRevision);
                    assertEquals(view.actionToken,joinRequest.actionToken);assertEquals("C1",joinRequest.companyId);
                }
                try {
                    java.lang.reflect.Field field=KOMEGuiJoinBattle.class.getDeclaredField("model");field.setAccessible(true);
                    KOMEJoinBattleViewModel model=(KOMEJoinBattleViewModel)field.get(screen);
                    if(withdraw) {assertEquals(KOMEJoinBattleService.Reason.WITHDRAWN,view.reason);assertEquals("",model.selectedCompanyId());assertTrue(model.status().contains("Could not join"));}
                    else {assertTrue(view.isAllowed());assertEquals("C1",model.selectedCompanyId());assertTrue(model.canSubmit());}
                } catch(ReflectiveOperationException failure) {throw new AssertionError(failure);}
                assertNull(selector.joinViewRequest());
            }
        });
    }
}
