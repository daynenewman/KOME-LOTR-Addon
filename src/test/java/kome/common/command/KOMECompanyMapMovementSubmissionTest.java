package kome.common.command;

import java.util.*;
import kome.common.data.*;
import kome.common.command.KOMECompanyMovementService.*;
import lotr.common.fac.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import kome.common.KOMEAccessFixture;
import kome.common.network.*;
import org.junit.*;
import static org.junit.Assert.*;
import static kome.common.command.KOMECompanyMovementServiceTest.*;

/** Transport evidence goes through real canonical validation/departure, not a mock packet executor. */
public class KOMECompanyMapMovementSubmissionTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    private KOMEPopulationTestConfig config;
    @Before public void before() throws Exception {config=new KOMEPopulationTestConfig();}
    @After public void after() throws Exception {
        LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.MORDOR,LOTRFactionRelations.Relation.NEUTRAL);
        LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.ROHAN,LOTRFactionRelations.Relation.NEUTRAL);config.close();
    }
    private KOMECompanyMovementResult submit(Fixture f,Evaluation e) {
        return KOMECompanyMapMovementSubmission.submit(f.service,f.data,f.player,new UUID(1,3),1,1,"C1",f.destination,
            e.confirmation.legacyFingerprint,e.confirmation.serviceFingerprint);
    }
    @Test public void legalFootAndMountedUseCanonicalDepartureWithoutPromisingSpawn() throws Exception {
        for(boolean mounted:new boolean[]{false,true})try(Fixture f=new Fixture(mounted,mounted?2:1)) {
            Evaluation e=f.evaluate();assertTrue(e.canArriveNow);KOMECompanyMovementResult result=submit(f,e);
            assertTrue(result.detail,result.accepted());assertEquals(e.remainingAllowance-1,f.company.movementAllowance);
            int remaining=f.company.movementAllowance;
            KOMEArmyMovementOrder order=f.data.armyMovements.get(result.orderId);assertNotNull(order);
            assertEquals(e.routeTiles,order.routeTiles);assertEquals(order.status,result.orderStatus);assertEquals(order.currentTile,result.currentTile);
            assertEquals(KOMEArmyMovementOrder.ARRIVED.equals(order.status),result.outcome==KOMECompanyMovementResult.Outcome.ARRIVED);
            assertEquals(order.id,f.unit.movementOrderId);assertEquals(1,f.data.armyMovements.size());
            assertFalse(submit(f,e).accepted());assertEquals(1,f.data.armyMovements.size());assertEquals(remaining,f.company.movementAllowance);
        }
    }
    @Test public void legalMultiDayStartsSameCanonicalOrder() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            Evaluation e=f.evaluate();assertFalse(e.canArriveNow);KOMECompanyMovementResult result=submit(f,e);assertTrue(result.accepted());
            assertEquals(KOMECompanyMovementResult.Outcome.ORDER_ACCEPTED,result.outcome);assertEquals(2,result.distance);
            assertEquals(0,f.company.movementAllowance);assertEquals(e.routeTiles,f.data.armyMovements.get(result.orderId).routeTiles);
        }
    }
    @Test public void canonicalTimedOverrideSupportsConfirmedSingleCompanyOrders() throws Exception {
        try(Fixture f=new Fixture(false,2)) {
            f.data.movementSecondsPerTileOverride=30;Evaluation e=f.evaluate();assertEquals(ScheduleMode.TIMED_OVERRIDE,e.scheduleMode);
            assertNotNull(e.confirmation);assertTrue(submit(f,e).accepted());assertEquals(1,f.data.armyMovements.size());
        }
    }
    @Test public void richGuardReconstitutedFromWireIsAcceptedAndForgedGuardRejected() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            Evaluation e=f.evaluate();KOMECompanyMovementResult result=KOMECompanyMapMovementSubmission.submit(f.service,f.data,f.player,
                UUID.randomUUID(),1,1,"C1",f.destination,e.previewFingerprint,String.join("",Collections.nCopies(64,"0")));
            assertEquals(KOMECompanyMovementResult.Outcome.STALE_PREVIEW,result.outcome);assertTrue(f.data.armyMovements.isEmpty());
            assertTrue(submit(f,e).accepted());
        }
    }
    @Test public void controllerAndAllowanceRevalidatedBeforeMutation() throws Exception {
        for(boolean controller:new boolean[]{true,false})try(Fixture f=new Fixture(false,1)) {
            Evaluation e=f.evaluate();if(controller)f.company.owner=new UUID(9,9);else f.company.movementAllowance=0;
            KOMECompanyMovementResult result=submit(f,e);assertFalse(result.accepted());
            assertEquals(controller?"UNAUTHORIZED":"NO_ALLOWANCE",result.code);assertTrue(f.data.armyMovements.isEmpty());
            assertTrue(f.unit.movementOrderId.isEmpty());
        }
    }
    @Test public void membershipAndDestinationChangeRejectSameConfirmation() throws Exception {
        for(boolean member:new boolean[]{true,false})try(Fixture f=new Fixture(false,1)) {
            Evaluation e=f.evaluate();if(member)f.unit.companyId="C2";else f.data.conquestTiles.get(f.destination).setAnchor(0,30,64,20);
            KOMECompanyMovementResult result=submit(f,e);assertFalse(result.accepted());assertTrue(f.data.armyMovements.isEmpty());assertEquals(1,f.company.movementAllowance);
        }
    }
    @Test public void changedMountainAndDiplomacyReturnLiveDenialNotExecution() throws Exception {
        for(boolean terrain:new boolean[]{true,false})try(Fixture f=new Fixture(false,1)) {
            if(!terrain)f.hostileDestination();Evaluation e=f.evaluate();assertTrue(e.allowed());
            if(terrain)f.edge("T001",f.destination,KOMEConquestRouteEdge.MOUNTAIN);
            else LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.MORDOR,LOTRFactionRelations.Relation.NEUTRAL);
            KOMECompanyMovementResult result=submit(f,e);assertFalse(result.accepted());assertFalse(result.code.isEmpty());assertFalse(result.detail.isEmpty());
            assertTrue(f.data.armyMovements.isEmpty());assertEquals(1,f.company.movementAllowance);
        }
    }
    @Test public void hostileCreateAndJoinUseSameCanonicalHostileOrder() throws Exception {
        for(boolean join:new boolean[]{false,true})try(Fixture f=new Fixture(false,1)) {
            if(join)f.hostileConflict();else f.hostileDestination();Evaluation e=f.evaluate();
            assertEquals(join?Consequence.EXPECTED_JOIN_CONFLICT:Consequence.EXPECTED_CREATE_CONFLICT,e.consequence);
            KOMECompanyMovementResult result=submit(f,e);assertTrue(result.detail,result.accepted());
            assertEquals(f.destination,f.data.armyMovements.get(result.orderId).hostileAttackDestination);
        }
    }
    @Test public void materiallyChangedConflictRevisionRejectsTransportConfirmation() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            KOMEConflictRecord conflict=f.hostileConflict();Evaluation e=f.evaluate();
            KOMEConflictService.Result changed=f.data.getConflictService().registerPlayer(f.destination,
                kome.common.data.KOMEConflictContracts.ExpectedConflict.at(conflict.getConflictId(),conflict.getRevision()),
                new UUID(8,8),"mordor",new kome.common.data.KOMEConflictContracts.Context(e.evaluatedAtMillis+1,"test","new defender"));
            assertEquals(kome.common.data.KOMEConflictContracts.Code.SUCCESS,changed.code);
            KOMECompanyMovementResult result=submit(f,e);assertEquals(KOMECompanyMovementResult.Outcome.STALE_PREVIEW,result.outcome);
            assertEquals(1,f.company.movementAllowance);assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    @Test public void appearedConflictMakesCreateConfirmationStale() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            f.hostileDestination();Evaluation e=f.evaluate();f.hostileConflict();KOMECompanyMovementResult result=submit(f,e);
            assertEquals(KOMECompanyMovementResult.Outcome.STALE_PREVIEW,result.outcome);assertTrue(f.data.armyMovements.isEmpty());assertEquals(1,f.company.movementAllowance);
        }
    }
    @Test public void registeredHandlerReplaysCannotDoubleSpendOrCreateAnotherOrder() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            Evaluation e=f.evaluate();net.minecraft.network.NetHandlerPlayServer connection=f.player.playerNetServerHandler;
            java.lang.reflect.Constructor<cpw.mods.fml.common.network.simpleimpl.MessageContext> constructor=
                cpw.mods.fml.common.network.simpleimpl.MessageContext.class.getDeclaredConstructor(net.minecraft.network.INetHandler.class,cpw.mods.fml.relauncher.Side.class);
            constructor.setAccessible(true);cpw.mods.fml.common.network.simpleimpl.MessageContext context=constructor.newInstance(connection,cpw.mods.fml.relauncher.Side.SERVER);
            kome.common.network.KOMEPacketCompanyMovementRequest packet=new kome.common.network.KOMEPacketCompanyMovementRequest(UUID.randomUUID(),1,1,"C1",f.destination,e.previewFingerprint,e.confirmation.serviceFingerprint);
            kome.common.network.KOMEPacketCompanyMovementRequest.Handler handler=new kome.common.network.KOMEPacketCompanyMovementRequest.Handler();
            KOMECompanyMovementResult first=((kome.common.network.KOMEPacketCompanyMovementResult)handler.onMessage(packet,context)).result();
            assertTrue(first.detail,first.accepted());assertEquals(1,f.data.armyMovements.size());assertEquals(0,f.company.movementAllowance);
            KOMECompanyMovementResult replay=((kome.common.network.KOMEPacketCompanyMovementResult)handler.onMessage(packet,context)).result();
            assertEquals("REPLAY_REJECTED",replay.code);assertEquals(1,f.data.armyMovements.size());assertEquals(0,f.company.movementAllowance);
            KOMECompanyMovementResult roundTrip=kome.common.network.KOMEPopulationWire.copyForPublication(new kome.common.network.KOMEPacketCompanyMovementResult(first),kome.common.network.KOMEPacketCompanyMovementResult::new).result();
            assertEquals(first.outcome,roundTrip.outcome);
        } finally {kome.common.network.KOMEPacketHandler.clearPendingServerTasks();}
    }
    @Test public void newFormalRetreatRestrictionIsEnforcedAtSubmission() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            Evaluation e=f.evaluate();f.reserve(KOMEFormalRetreatBatch.Progress.DEPARTED,KOMEFormalRetreatBatch.Progress.DEPARTED);
            KOMECompanyMovementResult result=submit(f,e);assertFalse(result.accepted());assertEquals("FORMAL_RETREAT_BLOCKED",result.code);
            assertEquals(1,f.company.movementAllowance);assertEquals(1,f.data.armyMovements.size());
        }
    }
    @Test public void mapAdapterCannotUseLegacyOperatorPrivilegeForAnotherOwnersNativeCompany() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            Evaluation e=f.evaluate();f.company.owner=new UUID(9,9);f.company.temporaryController=f.player.id;f.player.operator=true;
            KOMECompanyMovementResult result=submit(f,e);assertEquals("UNAUTHORIZED",result.code);assertTrue(f.data.armyMovements.isEmpty());
        }
    }
    private static NBTTagCompound snapshot(Fixture f) {
        NBTTagCompound saved=new NBTTagCompound();f.data.writeToNBT(saved);return saved;
    }
    private void assertStaleWithoutMutation(Fixture f,Evaluation preview) {
        NBTTagCompound before=snapshot(f);List<?> physical=new ArrayList<Object>(f.world.loadedEntityList);
        f.data.setDirty(false);
        KOMECompanyMovementResult result=submit(f,preview);
        assertEquals(result.detail,KOMECompanyMovementResult.Outcome.STALE_PREVIEW,result.outcome);
        assertEquals(before,snapshot(f));assertFalse(f.data.isDirty());assertEquals(physical,f.world.loadedEntityList);
    }
    @Test public void serializedOldConnectionConfirmationCannotExecuteAfterSamePlayerReconnect() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            Evaluation preview=f.evaluate();UUID oldNonce=KOMECompanyMovementSessions.current(f.player);
            KOMEPacketCompanyMovementRequest original=new KOMEPacketCompanyMovementRequest(UUID.randomUUID(),1,1,"C1",f.destination,
                preview.previewFingerprint,preview.confirmation.serviceFingerprint);
            io.netty.buffer.ByteBuf bytes=io.netty.buffer.Unpooled.buffer();
            try {
                original.toBytes(bytes);
                NetHandlerPlayServer old=f.player.playerNetServerHandler;
                KOMECompanyMovementSessions.forget(old);
                NetHandlerPlayServer replacement=KOMEAccessFixture.allocate(NetHandlerPlayServer.class);
                replacement.playerEntity=f.player;f.player.playerNetServerHandler=replacement;
                Evaluation current=f.evaluate();assertEquals(preview.previewFingerprint,current.previewFingerprint);
                assertNotEquals(oldNonce,KOMECompanyMovementSessions.current(f.player));
                KOMEPacketCompanyMovementRequest replay=new KOMEPacketCompanyMovementRequest();replay.fromBytes(bytes);
                java.lang.reflect.Constructor<cpw.mods.fml.common.network.simpleimpl.MessageContext> constructor=
                    cpw.mods.fml.common.network.simpleimpl.MessageContext.class.getDeclaredConstructor(net.minecraft.network.INetHandler.class,cpw.mods.fml.relauncher.Side.class);
                constructor.setAccessible(true);
                cpw.mods.fml.common.network.simpleimpl.MessageContext context=constructor.newInstance(replacement,cpw.mods.fml.relauncher.Side.SERVER);
                NBTTagCompound before=snapshot(f);f.data.setDirty(false);
                KOMEPacketCompanyMovementRequest.Handler handler=new KOMEPacketCompanyMovementRequest.Handler();
                KOMECompanyMovementResult result=((KOMEPacketCompanyMovementResult)handler.onMessage(replay,context)).result();
                assertEquals(KOMECompanyMovementResult.Outcome.STALE_PREVIEW,result.outcome);
                assertEquals(before,snapshot(f));assertFalse(f.data.isDirty());assertTrue(f.world.loadedEntityList.isEmpty());
                assertEquals("REPLAY_REJECTED",((KOMEPacketCompanyMovementResult)handler.onMessage(replay,context)).result().code);
                assertEquals(before,snapshot(f));
                replay=new KOMEPacketCompanyMovementRequest(original.session,2,1,"C1",f.destination,original.legacyFingerprint,original.confirmationFingerprint);
                assertEquals(KOMECompanyMovementResult.Outcome.STALE_PREVIEW,((KOMEPacketCompanyMovementResult)handler.onMessage(replay,context)).result().outcome);
                assertEquals(before,snapshot(f)); // Changing client correlation cannot repair the old guard.
                KOMEPacketCompanyMovementRequest fresh=new KOMEPacketCompanyMovementRequest(UUID.randomUUID(),3,2,"C1",f.destination,
                    current.previewFingerprint,current.confirmation.serviceFingerprint);
                assertTrue(((KOMEPacketCompanyMovementResult)handler.onMessage(fresh,context)).result().accepted());
                assertEquals(1,f.data.armyMovements.size());assertEquals(0,f.company.movementAllowance);
            } finally {bytes.release();KOMEPacketHandler.clearPendingServerTasks();}
        }
    }
    @Test public void createDefenderChangeStaysCreateButRejectsBeforeAnyMutation() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            f.hostileDestination();Evaluation preview=f.evaluate();
            LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.ROHAN,LOTRFactionRelations.Relation.ENEMY);
            f.data.conquestTiles.get(f.destination).setCurrentRulingFaction("rohan");
            Evaluation current=f.evaluate();assertTrue(current.allowed());assertEquals(Consequence.EXPECTED_CREATE_CONFLICT,current.consequence);
            assertEquals(preview.previewFingerprint,current.previewFingerprint);
            assertStaleWithoutMutation(f,preview);
        }
    }
    @Test public void joinDefenderChangeRejectsEvenWithSameHostileParticipantAndConflictRevision() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            f.hostileConflict();Evaluation preview=f.evaluate();
            f.data.conquestTiles.get(f.destination).setCurrentRulingFaction("rohan");
            Evaluation current=f.evaluate();assertTrue(current.allowed());assertEquals(Consequence.EXPECTED_JOIN_CONFLICT,current.consequence);
            assertEquals(preview.destinationConflictRevision,current.destinationConflictRevision);
            assertEquals(preview.previewFingerprint,current.previewFingerprint);
            assertEquals("mordor",KOMEConflictMovementService.evaluateHostileDestination(f.data,f.company,f.destination,true).authorityFactionId);
            assertStaleWithoutMutation(f,preview);
        }
    }
    @Test public void joinHostileAuthorityChangeRejectsWithoutAnEnumOrConflictRevisionChange() throws Exception {
        try(Fixture f=new Fixture(false,1)) {
            KOMEConflictRecord conflict=f.hostileConflict();
            KOMEConflictService.Result added=f.data.getConflictService().beginFactionParticipation(f.destination,
                kome.common.data.KOMEConflictContracts.ExpectedConflict.at(conflict.getConflictId(),conflict.getRevision()),"rohan",
                new kome.common.data.KOMEConflictContracts.Context(f.evaluate().evaluatedAtMillis+1,"test","another active side"));
            assertEquals(kome.common.data.KOMEConflictContracts.Code.SUCCESS,added.code);
            LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.ROHAN,LOTRFactionRelations.Relation.ENEMY);
            assertEquals("mordor",KOMEConflictMovementService.evaluateHostileDestination(f.data,f.company,f.destination,true).authorityFactionId);
            Evaluation preview=f.evaluate();
            LOTRFactionRelations.overrideRelations(LOTRFaction.GONDOR,LOTRFaction.MORDOR,LOTRFactionRelations.Relation.NEUTRAL);
            assertEquals("rohan",KOMEConflictMovementService.evaluateHostileDestination(f.data,f.company,f.destination,true).authorityFactionId);
            Evaluation current=f.evaluate();assertTrue(current.allowed());assertEquals(Consequence.EXPECTED_JOIN_CONFLICT,current.consequence);
            assertEquals(preview.destinationConflictRevision,current.destinationConflictRevision);
            assertEquals(preview.previewFingerprint,current.previewFingerprint);
            assertStaleWithoutMutation(f,preview);
        }
    }
    @Test public void typedPlayerDenialsHideIdsButCanonicalDiagnosticsRetainEvidence() throws Exception {
        for(boolean deleted:new boolean[]{true,false})try(Fixture f=new Fixture(false,1)) {
            Evaluation preview=f.evaluate();if(deleted)f.data.armyCompanies.remove("C1");else f.unit.companyId="C2";
            KOMECompanyMovementResult result=submit(f,preview);
            assertEquals(deleted?"UNKNOWN_COMPANY":"INELIGIBLE_MEMBER",result.code);
            assertFalse(result.detail.contains("C1"));assertFalse(result.detail.contains("C2"));
            assertEquals(deleted?"Company is no longer available.":KOMECompanyMovementResult.playerDenialDetail("INELIGIBLE_MEMBER"),result.detail);
            Evaluation denied=f.evaluate();assertTrue(denied.denial.reason.contains(deleted?"C1":"C2"));
            KOMECompanyDestinationOptions.Row row=new KOMECompanyDestinationOptions.Row(denied);
            assertFalse(row.denialDetail.contains("C1"));assertFalse(row.denialDetail.contains("C2"));
            KOMECompanyMovementResult copy=KOMEPopulationWire.copyForPublication(new KOMEPacketCompanyMovementResult(result),KOMEPacketCompanyMovementResult::new).result();
            assertEquals("C1",copy.companyId);assertEquals(result.detail,copy.detail);
        }
    }
}
