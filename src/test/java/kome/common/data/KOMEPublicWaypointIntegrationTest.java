package kome.common.data;
import java.util.*;
import java.lang.reflect.*;
import kome.common.KOMEAccessFixture;
import kome.common.command.KOMECommandKome;
import kome.common.network.KOMEPacketHandler;
import lotr.common.*;
import lotr.common.fac.*;
import lotr.common.world.map.*;
import lotr.common.network.LOTRPacketFastTravel;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.WorldServer;
import net.minecraft.util.AxisAlignedBB;
import org.junit.*;
import static org.junit.Assert.*;

public class KOMEPublicWaypointIntegrationTest {
    @Rule public final KOMETileTestResources geometry=new KOMETileTestResources();
    KOMEAccessFixture fixture; NativeData nativeData; Map<UUID,LOTRPlayerData> playerMap; LOTRPlayerData previous;
    kome.common.KOMETestServerSession serverSession;
    LOTRCommonProxy previousProxy; boolean travelConfig;
    @Before public void before()throws Exception {
        previousProxy=LOTRMod.proxy; LOTRMod.proxy=KOMEAccessFixture.allocate(LOTRCommonProxy.class);
        fixture=new KOMEAccessFixture(); fixture.data.initializeIntegratedWorld(); fixture.data.setProgressionEnabled(false);
        fixture.player.dimension=KOMETileTestResources.dimension(); fixture.player.posX=KOMETileTestResources.x(); fixture.player.posZ=KOMETileTestResources.z();
        Field bounds=net.minecraft.entity.Entity.class.getDeclaredField("boundingBox"); bounds.setAccessible(true); bounds.set(fixture.player,AxisAlignedBB.getBoundingBox(fixture.player.posX,72,fixture.player.posZ,fixture.player.posX+1,74,fixture.player.posZ+1));
        Field map=LOTRLevelData.class.getDeclaredField("playerDataMap"); map.setAccessible(true); playerMap=(Map<UUID,LOTRPlayerData>)map.get(null);
        nativeData=new NativeData(fixture.player); previous=playerMap.put(fixture.player.id,nativeData);
        travelConfig=LOTRConfig.enableFastTravel; LOTRConfig.enableFastTravel=true; KOMEPacketHandler.clearPendingServerTasks();
        serverSession = new kome.common.KOMETestServerSession(fixture);
    }
    @After public void after()throws Exception {
        if(previous==null)playerMap.remove(fixture.player.id);else playerMap.put(fixture.player.id,previous);
        LOTRMod.proxy=previousProxy; LOTRConfig.enableFastTravel=travelConfig; serverSession.close(); KOMEPacketHandler.clearPendingServerTasks();
        KOMEPublicWaypointClientState.INSTANCE.start(null);
    }
    KOMEPublicWaypoint create(){return fixture.data.publicWaypoints.approve(fixture.data,"Public",fixture.player.dimension,
        KOMETileTestResources.x(),72,KOMETileTestResources.z(),0,KOMEPublicWaypoint.Source.PUBLIC,"","console",100,null);}
    KOMEPublicWaypointAdapter adapter(KOMEPublicWaypoint r){ return new KOMEPublicWaypointAdapter(fixture.data.publicWaypoints.view(fixture.data,r.id)); }
    void request(KOMEPublicWaypointAdapter target){assertTrue(KOMEPublicWaypointBridge.handleRequest(new LOTRPacketFastTravel(target),fixture.context));}
    @Test public void normalPlayerCommandProposesAndAdminApprovalCreatesOneAuthoritativeDestination(){
        new KOMECommandKome().processCommand(fixture.player,new String[]{"waypoint","propose","Village","Square"});
        KOMEWaypointProposal q=fixture.data.publicWaypoints.proposals().get(0); assertEquals(fixture.player.id,q.submitter);
        assertEquals(72,q.y); assertEquals("Village Square",q.name); assertTrue(fixture.data.publicWaypoints.views(fixture.data).isEmpty());
        try {new KOMECommandKome().processCommand(fixture.player,new String[]{"waypoint","approve",q.id.toString(),"0","Reviewed"});fail();}
        catch(net.minecraft.command.WrongUsageException expected){}
        fixture.player.operator=true; new KOMECommandKome().processCommand(fixture.player,new String[]{"waypoint","approve",q.id.toString(),"0","Reviewed"});
        assertEquals(1,fixture.data.publicWaypoints.views(fixture.data).size()); assertEquals(KOMEWaypointProposal.Status.APPROVED,fixture.data.publicWaypoints.proposal(q.id).status);
    }
    @Test public void nativeRequestNamespaceIsQueuedAndResolvesOnlyCurrentApproval(){
        KOMEPublicWaypoint r=create(); KOMEPublicWaypointAdapter target=adapter(r); request(target);
        assertNull(nativeData.target); assertEquals(1,KOMEPacketHandler.runPendingServerTasks()); assertTrue(nativeData.target instanceof KOMEPublicWaypointAdapter);
        assertEquals(r.id,((KOMEPublicWaypointAdapter)nativeData.target).view.record.id);
        nativeData.target=null; request(target); fixture.data.publicWaypoints.remove(fixture.data,r.id,"console",101);
        KOMEPacketHandler.runPendingServerTasks(); assertNull(nativeData.target);
        assertFalse(KOMEPublicWaypointBridge.handleRequest(new LOTRPacketFastTravel(LOTRWaypoint.HOBBITON),fixture.context));
    }
    @Test public void requestCompletionAndCancellationStayFifoOnOneConnection() {
        KOMEPublicWaypointAdapter target=adapter(create()); request(target);
        KOMEPublicWaypointBridge.handleBounce(fixture.context);
        KOMEPacketHandler.enqueueServerTask(fixture.context,()->nativeData.setTargetFTWaypoint(null));
        KOMEPublicWaypointBridge.handleBounce(fixture.context);
        while(KOMEPacketHandler.runPendingServerTasks()>0) { }
        assertEquals(2,nativeData.bounces);
        assertEquals(target.view.record.id,nativeData.completedTargets.get(0).view.record.id);
        assertNull(nativeData.completedTargets.get(1)); assertNull(nativeData.target);
    }
    @Test public void disconnectedNativeRequestAndCompletionDoNotExecute() {
        request(adapter(create())); KOMEPublicWaypointBridge.handleBounce(fixture.context);
        serverSession.players.clear();
        while(KOMEPacketHandler.runPendingServerTasks()>0) { }
        assertNull(nativeData.target); assertEquals(0,nativeData.bounces); assertTrue(fixture.player.messages.isEmpty());
    }
    @Test public void changedApprovalBeforeQueuedCompletionCancelsFinalTravel() {
        KOMEPublicWaypoint r=create(); request(adapter(r));
        while(KOMEPacketHandler.runPendingServerTasks()>0) { }
        fixture.data.publicWaypoints.remove(fixture.data,r.id,"console",102);
        KOMEPacketHandler.enqueueServerTask(fixture.context,()->{
            if(KOMEWaypointAccessService.allowFinalTravel(nativeData)) nativeData.receiveFTBouncePacket();
        });
        while(KOMEPacketHandler.runPendingServerTasks()>0) { }
        assertNull(nativeData.target); assertEquals(0,nativeData.bounces);
    }
    @Test public void nativeCompletionIsQueuedAndRunsSameNativeReceivePath(){
        assertTrue(KOMEPublicWaypointBridge.handleBounce(fixture.context)); assertEquals(0,nativeData.bounces);
        KOMEPacketHandler.runPendingServerTasks(); assertEquals(1,nativeData.bounces);
    }
    @Test public void cooldownCombatSleepDimensionConfigAndProgressionRemainRequired(){
        KOMEPublicWaypointAdapter target=adapter(create());
        nativeData.elapsed=0; request(target); KOMEPacketHandler.runPendingServerTasks(); assertNull(nativeData.target);
        nativeData.elapsed=1000; nativeData.combat=true; request(target); KOMEPacketHandler.runPendingServerTasks(); assertNull(nativeData.target);
        nativeData.combat=false;
        try {
            Field sleep=EntityPlayer.class.getDeclaredField("sleeping"); sleep.setAccessible(true); sleep.setBoolean(fixture.player,true);
            request(target); KOMEPacketHandler.runPendingServerTasks(); assertNull(nativeData.target); sleep.setBoolean(fixture.player,false);
        } catch(ReflectiveOperationException failure) { throw new AssertionError(failure); }
        fixture.player.dimension++; request(target); KOMEPacketHandler.runPendingServerTasks(); assertNull(nativeData.target); fixture.player.dimension--;
        LOTRConfig.enableFastTravel=false; request(target); KOMEPacketHandler.runPendingServerTasks(); assertNull(nativeData.target); LOTRConfig.enableFastTravel=true;
        fixture.data.setProgressionEnabled(true); request(target); KOMEPacketHandler.runPendingServerTasks(); assertNull(nativeData.target);
        fixture.data.setProgressionEnabled(false); request(target); KOMEPacketHandler.runPendingServerTasks(); assertNotNull(nativeData.target);
    }
    @Test public void finalGuardRechecksMoveRemovalAndCurrentOwnerWithoutLevelGates(){
        KOMEPublicWaypoint r=create(); KOMEPublicWaypointAdapter target=adapter(r); nativeData.target=target;
        assertTrue(KOMEWaypointAccessService.allowFinalTravel(nativeData));
        fixture.data.publicWaypoints.level(fixture.data,r.id,Integer.MAX_VALUE,"console",101); assertTrue(KOMEWaypointAccessService.allowFinalTravel(nativeData));
        fixture.data.publicWaypoints.move(fixture.data,r.id,r.dimension,r.x,r.y+1,r.z,"console",102);
        assertFalse(KOMEWaypointAccessService.allowFinalTravel(nativeData)); assertNull(nativeData.target);
        nativeData.target=adapter(fixture.data.publicWaypoints.get(r.id)); fixture.data.publicWaypoints.remove(fixture.data,r.id,"console",103);
        assertFalse(KOMEWaypointAccessService.allowFinalTravel(nativeData)); assertNull(nativeData.target);
    }
    @Test public void publicPolicyUsesCurrentCanonicalOwnerAndActiveWarIncludingOperators(){
        KOMEPublicWaypoint r=create(); KOMEPublicWaypointAdapter target=adapter(r);
        fixture.data.conquestTiles.get(r.tileId).setCurrentRulingFaction("gondor");
        assertTrue(KOMEWaypointAccessService.evaluate(fixture.data,fixture.player.id,"gondor",true,target,true).finalAllowed);
        fixture.data.conquestTiles.get(r.tileId).setCurrentRulingFaction("mordor");
        KOMEWar war=new KOMEWar(); war.id="W1"; war.sideOneFactions.add("gondor"); war.sideTwoFactions.add("mordor"); fixture.data.wars.put(war.id,war);
        assertFalse(KOMEWaypointAccessService.evaluate(fixture.data,fixture.player.id,"gondor",true,target,true).finalAllowed);
        assertTrue(KOMEWaypointAccessService.evaluate(fixture.data,fixture.player.id,"",false,target,true).finalAllowed);
        assertFalse(KOMEWaypointAccessService.evaluate(fixture.data,fixture.player.id,"",false,target,false).finalAllowed);
    }
    @Test public void publicMarkersDoNotMutateNativeListsAndExactLegacyCutoverIsPersisted(){
        UUID sharer=UUID.randomUUID(); LOTRCustomWaypoint legacy=new LOTRCustomWaypoint("Legacy",1,1,KOMETileTestResources.x(),72,KOMETileTestResources.z(),4);
        LOTRCustomWaypoint unrelated=new LOTRCustomWaypoint("Other",1,1,0,72,0,5);
        List<LOTRAbstractWaypoint> original=new ArrayList<LOTRAbstractWaypoint>(Arrays.asList(LOTRWaypoint.HOBBITON,legacy,unrelated));
        KOMEWaypointMigration.Entry e=new KOMEWaypointMigration.Entry(fixture.player.id,4,"Legacy",fixture.player.dimension,legacy.getXCoord(),72,legacy.getZCoord());
        fixture.data.publicWaypoints.importLegacy(fixture.data,Collections.singletonList(e),"console",100);
        KOMEPublicWaypointSnapshot snapshot=KOMEPublicWaypointSnapshot.from(fixture.data);
        List<LOTRAbstractWaypoint> visible=KOMEPublicWaypointBridge.compose(original,fixture.player.id,snapshot);
        assertEquals(3,original.size()); assertSame(legacy,original.get(1)); assertEquals("Legacy",legacy.getCodeName());
        assertFalse(visible.contains(legacy)); assertTrue(visible.contains(unrelated)); assertTrue(visible.contains(LOTRWaypoint.HOBBITON));
        assertEquals(3,visible.size()); assertEquals(3,KOMEPublicWaypointBridge.compose(visible,fixture.player.id,snapshot).size());
        KOMEPublicWaypoint r=fixture.data.publicWaypoints.records().get(0); fixture.data.publicWaypoints.remove(fixture.data,r.id,"console",101);
        assertFalse(KOMEPublicWaypointBridge.compose(original,fixture.player.id,KOMEPublicWaypointSnapshot.from(fixture.data)).contains(legacy));
        fixture.data.publicWaypoints.rollbackLegacy(fixture.data,e.identity,"console",102);
        assertTrue(KOMEPublicWaypointBridge.compose(original,fixture.player.id,KOMEPublicWaypointSnapshot.from(fixture.data)).contains(legacy));
        assertNull(KOMEPublicWaypointBridge.lookup(nativeData,KOMEPublicWaypointAdapter.NAMESPACE,r.wireId));
    }
    @Test public void unavailableGeometryAndWriteBlockedStateNeverFallBackToNativeForPublicTargets(){
        KOMEPublicWaypoint r=create(); KOMEPublicWaypointAdapter target=adapter(r); KOMETileWorldResolver.INSTANCE.invalidate();
        assertFalse(KOMEWaypointAccessService.evaluate(fixture.data,fixture.player.id,"",false,target,true).finalAllowed);
        assertTrue(KOMEPublicWaypointSnapshot.from(fixture.data).entries.isEmpty()); KOMETileWorldResolver.INSTANCE.publish(KOMETileTestResources.real());
        assertNotNull(fixture.data.publicWaypoints.view(fixture.data,r.id));
        NBTTagCompound invalid=new NBTTagCompound(); fixture.data.writeToNBT(invalid); invalid.setString("PublicWaypoints","corrupt"); try { fixture.data.readFromNBT(invalid); fail("Corrupt candidate accepted"); } catch(IllegalStateException expected) { }
        assertTrue(fixture.data.isWriteBlocked()); assertFalse(KOMEWaypointAccessService.evaluate(fixture.data,fixture.player.id,"",false,target,true).finalAllowed);
    }
    @Test public void associatedNativePointsRetainNativeRegionProgressionAndDefaultAccess(){
        LOTRWaypoint chosen=null;
        for(LOTRWaypoint point:LOTRWaypoint.values()) if(!point.isHidden() && KOMETileWorldResolver.INSTANCE.resolve(fixture.player.dimension,point.getXCoord(),point.getZCoord()).status==KOMETileResolution.Status.RESOLVED) { chosen=point; break; }
        assertNotNull(chosen);
        KOMEPublicWaypoint r=fixture.data.publicWaypoints.approve(fixture.data,"Associated native",fixture.player.dimension,chosen.getXCoord(),
            chosen.getYCoordSaved(),chosen.getZCoord(),0,KOMEPublicWaypoint.Source.NATIVE,chosen.getCodeName(),"console",100,null);
        KOMEPublicWaypointAdapter adapter=adapter(r); nativeData.region=false; assertFalse(adapter.nativeEligible(fixture.player,"gondor"));
        nativeData.region=true; assertTrue(adapter.nativeEligible(fixture.player,"gondor"));
        assertEquals(chosen.hasPlayerUnlocked(fixture.player),adapter.nativeEligible(fixture.player,""));
        List<LOTRAbstractWaypoint> originals=new ArrayList<LOTRAbstractWaypoint>(LOTRWaypoint.listAllWaypoints());
        List<LOTRAbstractWaypoint> composed=KOMEPublicWaypointBridge.compose(originals,fixture.player.id,KOMEPublicWaypointSnapshot.from(fixture.data));
        assertTrue(originals.contains(chosen)); assertFalse(composed.contains(chosen)); assertEquals(originals.size(),composed.size());
    }
    @Test public void clientAccessUsesSnapshotOwnerEvenBeforeConquestPacketArrives(){
        KOMEPublicWaypoint r=create(); fixture.data.conquestTiles.get(r.tileId).setCurrentRulingFaction("gondor");
        KOMEConquestTile previous=KOMEClientData.INSTANCE.conquestTiles.get(r.tileId);
        try {
            KOMEConquestTile stale=new KOMEConquestTile(r.tileId); stale.setCurrentRulingFaction("mordor"); KOMEClientData.INSTANCE.conquestTiles.put(r.tileId,stale);
            Object connection=new Object(); KOMEPublicWaypointClientState.INSTANCE.start(connection);
            for(kome.common.network.KOMEPacketPublicWaypoints.Chunk chunk:kome.common.network.KOMEPacketPublicWaypoints.split(UUID.randomUUID(),1,KOMEPublicWaypointSnapshot.from(fixture.data)))
                KOMEPublicWaypointClientState.INSTANCE.accept(connection,chunk);
            KOMEWaypointAccessService.Decision decision=KOMEWaypointAccessService.evaluate(KOMEClientData.INSTANCE,fixture.player.id,"gondor",false,adapter(r),true);
            assertTrue(decision.finalAllowed); assertEquals("gondor",decision.tileOwner); assertEquals(KOMEWaypointAccessService.State.OWN,decision.state);
        } finally { if(previous==null)KOMEClientData.INSTANCE.conquestTiles.remove(r.tileId);else KOMEClientData.INSTANCE.conquestTiles.put(r.tileId,previous); }
    }
    static class NativeData extends LOTRPlayerData {
        final EntityPlayer player; LOTRAbstractWaypoint target; int elapsed=1000,bounces; boolean combat,region;
        final List<KOMEPublicWaypointAdapter> completedTargets=new ArrayList<KOMEPublicWaypointAdapter>();
        NativeData(EntityPlayer player){super(player.getUniqueID());this.player=player;}
        @Override public boolean isFTRegionUnlocked(LOTRWaypoint.Region value){return region;}
        @Override public boolean canFastTravel(){return !combat;}
        @Override public int getTimeSinceFT(){return elapsed;}
        @Override public int getWaypointFTTime(LOTRAbstractWaypoint point,EntityPlayer p){return 100;}
        @Override public LOTRAbstractWaypoint getTargetFTWaypoint(){return target;}
        @Override public void setTargetFTWaypoint(LOTRAbstractWaypoint value){target=value;}
        @Override public void receiveFTBouncePacket(){bounces++;completedTargets.add((KOMEPublicWaypointAdapter)target);}
    }
}
