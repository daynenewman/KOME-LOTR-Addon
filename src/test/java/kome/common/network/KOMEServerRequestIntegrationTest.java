package kome.common.network;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import kome.common.KOMEAccessFixture;
import kome.common.KOMETestServerSession;
import kome.common.data.KOMETileTestResources;
import kome.common.data.KOMEServerRecordBuilder;
import org.junit.*;
import static org.junit.Assert.*;

public class KOMEServerRequestIntegrationTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    KOMEAccessFixture a,b;KOMETestServerSession server;cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper previous;
    @Before public void before()throws Exception {
        a=new KOMEAccessFixture();b=new KOMEAccessFixture();a.data.initializeIntegratedWorld();b.data.initializeIntegratedWorld();
        server=new KOMETestServerSession(a,b);previous=KOMEPacketHandler.network;KOMEPacketHandler.network=a.network;
    }
    @After public void after()throws Exception {if(server!=null)server.close();KOMEPacketHandler.network=previous;}
    void flush(){while(KOMEPacketHandler.runPendingServerTasks()>0){}}
    KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest> mutation(AtomicInteger calls,boolean reply){
        return new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>((message,context)->{
            calls.incrementAndGet();a.data.setDirty(true);return reply?new KOMEPacketServerRecordRequest(0L):null;
        });
    }
    @Test public void capturedRequesterCannotBeReboundBetweenCaptureAndAdmission(){
        KOMEPacketHandler.Requester original=KOMEPacketHandler.captureRequester(a.context);
        AtomicInteger calls=new AtomicInteger();a.context.getServerHandler().playerEntity=b.player;
        b.player.playerNetServerHandler=a.context.getServerHandler();server.players.remove(a.player);
        assertTrue(KOMEPacketHandler.enqueueServerTask(original,calls::incrementAndGet));flush();assertEquals(0,calls.get());
        assertTrue(KOMEPacketHandler.enqueueServerTask(a.context,calls::incrementAndGet));flush();assertEquals(1,calls.get());
    }
    @Test public void capturedOldSessionCannotAdmitIntoRestartedQueue(){
        KOMEPacketHandler.Requester original=KOMEPacketHandler.captureRequester(a.context);
        KOMEPacketHandler.startServerSession(server.server);AtomicInteger calls=new AtomicInteger();
        assertFalse(KOMEPacketHandler.enqueueServerTask(original,calls::incrementAndGet));assertFalse(original.isCurrent());flush();assertEquals(0,calls.get());
    }
    @Test public void disconnectedQueuedMutationAndReplyAreSkipped(){
        AtomicInteger calls=new AtomicInteger();a.data.setDirty(false);mutation(calls,true).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);
        a.player.connected=false;flush();assertEquals(0,calls.get());assertFalse(a.data.isDirty());assertTrue(a.network.messages.isEmpty());
    }
    @Test public void unregisteredPlayerIsSkippedEvenWhileChannelRemainsOpen(){
        AtomicInteger calls=new AtomicInteger();mutation(calls,true).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);
        server.players.remove(a.player);flush();assertEquals(0,calls.get());assertTrue(a.network.messages.isEmpty());
    }
    @Test public void replacementHandlerOrPlayerInvalidatesCapturedRequester()throws Exception{
        AtomicInteger calls=new AtomicInteger();mutation(calls,true).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);
        a.player.playerNetServerHandler=b.player.playerNetServerHandler;flush();assertEquals(0,calls.get());
        a.player.playerNetServerHandler=a.context.getServerHandler();mutation(calls,true).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);
        a.context.getServerHandler().playerEntity=b.player;flush();assertEquals(0,calls.get());assertTrue(a.network.messages.isEmpty());
    }
    @Test public void sameUuidReconnectRunsOnlyReplacementConnection(){
        AtomicInteger calls=new AtomicInteger();mutation(calls,true).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);
        b.player.id=a.player.id;server.players.remove(a.player);a.player.connected=false;
        mutation(calls,true).onMessage(new KOMEPacketServerRecordRequest(0L),b.context);flush();
        assertEquals(1,calls.get());assertEquals(1,a.network.messages.size());
    }
    @Test public void changedServerSingletonInvalidatesOldSession()throws Exception{
        AtomicInteger calls=new AtomicInteger();mutation(calls,true).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);
        try(KOMETestServerSession replacement=new KOMETestServerSession(b)){
            assertFalse(KOMEPacketHandler.captureRequester(a.context).isCurrent());flush();assertEquals(0,calls.get());
        }
    }
    @Test public void noReplyAfterDelegateDisconnectsAndLaterIndependentTaskStillRuns(){
        AtomicInteger calls=new AtomicInteger();
        new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>((m,c)->{
            calls.incrementAndGet();a.player.connected=false;return new KOMEPacketServerRecordRequest(0L);
        }).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);
        mutation(calls,false).onMessage(new KOMEPacketServerRecordRequest(0L),b.context);flush();
        assertEquals(2,calls.get());assertTrue(a.network.messages.isEmpty());
    }
    @Test public void failedDelegateExecutesOnceAndIndependentReplySurvives(){
        AtomicInteger failed=new AtomicInteger(),later=new AtomicInteger();
        new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>((m,c)->{
            failed.incrementAndGet();throw new IllegalStateException("expected isolated delegate failure");
        }).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);
        mutation(later,true).onMessage(new KOMEPacketServerRecordRequest(0L),b.context);flush();flush();
        assertEquals(1,failed.get());assertEquals(1,later.get());assertEquals(1,a.network.messages.size());
    }
    @Test public void nettyAdmissionDoesNotExecuteProjectionOrWorldReads()throws Exception{
        AtomicInteger calls=new AtomicInteger();Thread sender=new Thread(()->mutation(calls,false).onMessage(new KOMEPacketServerRecordRequest(0L),a.context));
        sender.start();sender.join(5000);assertFalse(sender.isAlive());assertEquals(0,calls.get());assertEquals(1,KOMEPacketHandler.pendingServerTaskCount());
        flush();assertEquals(1,calls.get());
    }
    @Test public void offThreadDrainFailsBeforeExecutingCallbacks()throws Exception{
        AtomicInteger calls=new AtomicInteger(),rejected=new AtomicInteger();mutation(calls,true).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);
        Thread other=new Thread(()->{try{KOMEPacketHandler.runPendingServerTasks();}catch(IllegalStateException expected){rejected.incrementAndGet();}});
        other.start();other.join(5000);assertEquals(1,rejected.get());assertEquals(0,calls.get());flush();assertEquals(1,calls.get());
    }
    @Test public void recordFloodBuildsOnceIndependentPlayerAndExpiryWork(){
        long[] clock={0};AtomicInteger projections=new AtomicInteger();
        KOMEPacketServerRecordRequest.Handler delegate=new KOMEPacketServerRecordRequest.Handler(()->clock[0],player->{projections.incrementAndGet();return Arrays.asList("current public projection");});
        KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest> handler=new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>(delegate);
        for(int n=0;n<100;n++)handler.onMessage(new KOMEPacketServerRecordRequest(clock[0]),a.context);
        assertEquals(32,KOMEPacketHandler.pendingServerTaskCount());flush();assertEquals(1,projections.get());assertEquals(1,a.network.messages.size());
        assertTrue(a.player.messages.toString().contains("received during cooldown"));assertTrue(a.player.messages.toString().contains("newest request dropped"));
        handler.onMessage(new KOMEPacketServerRecordRequest(clock[0]),b.context);flush();assertEquals(2,projections.get());
        clock[0]=1_999_000_000L;handler.onMessage(new KOMEPacketServerRecordRequest(clock[0]),a.context);flush();assertEquals(2,projections.get());
        clock[0]=2_000_000_000L;handler.onMessage(new KOMEPacketServerRecordRequest(clock[0]),a.context);flush();assertEquals(3,projections.get());assertEquals(3,a.network.messages.size());
    }
    @Test public void delayedFloodCannotRebuildAgainWhenDrainCrossesCooldownExpiry(){
        long[] clock={0};AtomicInteger projections=new AtomicInteger();
        KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest> handler=new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>(
            new KOMEPacketServerRecordRequest.Handler(()->clock[0],p->{projections.incrementAndGet();clock[0]+=3_000_000_000L;return Arrays.asList("line");}));
        for(int n=0;n<100;n++)handler.onMessage(new KOMEPacketServerRecordRequest(0L),a.context);
        flush();assertEquals(1,projections.get());assertEquals(1,a.network.messages.size());
        handler.onMessage(new KOMEPacketServerRecordRequest(clock[0]),a.context);flush();assertEquals(2,projections.get());
    }
    @Test public void currentPermissionIsUsedAndPrivateProjectionNeverReused(){
        long[] clock={0};a.player.operator=true;
        KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest> handler=new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>(
            new KOMEPacketServerRecordRequest.Handler(()->clock[0],player->Arrays.asList(player.canCommandSenderUseCommand(2,"kome")?"private operator data":"public data")));
        handler.onMessage(new KOMEPacketServerRecordRequest(clock[0]),a.context);a.player.operator=false;flush();
        assertEquals("[public data]",((KOMEPacketServerRecordData)a.network.messages.get(0)).lines.toString());
        a.player.operator=true;clock[0]=2_000_000_000L;handler.onMessage(new KOMEPacketServerRecordRequest(clock[0]),a.context);flush();
        assertEquals("[private operator data]",((KOMEPacketServerRecordData)a.network.messages.get(1)).lines.toString());
        a.player.operator=false;handler.onMessage(new KOMEPacketServerRecordRequest(clock[0]),a.context);flush();assertEquals(2,a.network.messages.size());
        clock[0]=4_000_000_000L;handler.onMessage(new KOMEPacketServerRecordRequest(clock[0]),a.context);flush();
        assertEquals("[public data]",((KOMEPacketServerRecordData)a.network.messages.get(2)).lines.toString());
    }
    @Test public void revokedOperatorPermissionDuringProjectionPreventsPrivateChunks(){
        a.player.operator=true;
        new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>(new KOMEPacketServerRecordRequest.Handler(()->0L,player->{
            a.player.operator=false;return Collections.nCopies(100,"private operator rows");
        })).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);flush();assertTrue(a.network.messages.isEmpty());
    }
    @Test public void disconnectDuringProjectionPreventsEveryChunk(){
        new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>(new KOMEPacketServerRecordRequest.Handler(()->0L,player->{
            a.player.connected=false;return Collections.nCopies(100,"sensitive projected line");
        })).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);flush();assertTrue(a.network.messages.isEmpty());
    }
    @Test public void sessionRestartDuringProjectionPreventsStaleChunks(){
        new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>(new KOMEPacketServerRecordRequest.Handler(()->0L,player->{
            KOMEPacketHandler.startServerSession(server.server);return Collections.nCopies(100,"old session");
        })).onMessage(new KOMEPacketServerRecordRequest(0L),a.context);flush();assertTrue(a.network.messages.isEmpty());
    }
    @Test public void chunkSendStopsAsSoonAsConnectionBecomesInvalid(){
        AtomicInteger checks=new AtomicInteger();
        KOMEPacketServerRecordData.sendChunked(Collections.nCopies(100,"line"),a.player,()->checks.incrementAndGet()<=2);
        assertEquals(1,a.network.messages.size());assertFalse(((KOMEPacketServerRecordData)a.network.messages.get(0)).complete);
    }
    @Test public void stopClearsAdmissionPendingAndRateLimitsAndRestartCanProjectImmediately(){
        AtomicInteger projections=new AtomicInteger();
        KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest> handler=new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>(
            new KOMEPacketServerRecordRequest.Handler(()->0L,player->{projections.incrementAndGet();return Arrays.asList("line");}));
        handler.onMessage(new KOMEPacketServerRecordRequest(0L),a.context);flush();assertEquals(1,KOMEPacketHandler.SERVER_RECORD_COOLDOWNS.size());
        handler.onMessage(new KOMEPacketServerRecordRequest(0L),a.context);KOMEPacketHandler.clearPendingServerTasks();
        assertEquals(0,KOMEPacketHandler.pendingServerTaskCount());assertEquals(0,KOMEPacketHandler.SERVER_RECORD_COOLDOWNS.size());
        handler.onMessage(new KOMEPacketServerRecordRequest(0L),a.context);assertEquals(0,KOMEPacketHandler.pendingServerTaskCount());
        KOMEPacketHandler.startServerSession(server.server);handler.onMessage(new KOMEPacketServerRecordRequest(0L),a.context);flush();assertEquals(2,projections.get());
    }
    @Test public void logoutClearsOnlyThatConnectionsQueueAndCooldown(){
        AtomicInteger projections=new AtomicInteger();
        KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest> handler=new KOMEPacketHandler.ServerThreadHandler<KOMEPacketServerRecordRequest>(
            new KOMEPacketServerRecordRequest.Handler(()->0L,p->{projections.incrementAndGet();return Arrays.asList("line");}));
        handler.onMessage(new KOMEPacketServerRecordRequest(0L),a.context);handler.onMessage(new KOMEPacketServerRecordRequest(0L),b.context);flush();
        handler.onMessage(new KOMEPacketServerRecordRequest(0L),a.context);handler.onMessage(new KOMEPacketServerRecordRequest(0L),b.context);
        KOMEPacketHandler.forgetRequester(a.player);assertEquals(1,KOMEPacketHandler.pendingServerTaskCount());assertEquals(1,KOMEPacketHandler.SERVER_RECORD_COOLDOWNS.size());
        flush();assertEquals(2,projections.get());
    }
}
