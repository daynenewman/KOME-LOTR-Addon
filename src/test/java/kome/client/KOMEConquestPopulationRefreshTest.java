package kome.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.math.BigInteger;
import java.util.*;
import kome.common.KOMEAddon;
import kome.common.KOMECommonProxy;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import kome.common.network.KOMEPacketConquestData;
import net.minecraft.nbt.*;
import org.junit.*;
import static org.junit.Assert.*;

public class KOMEConquestPopulationRefreshTest {
    private KOMECommonProxy previous;
    private TestProxy proxy;
    private KOMEClientData client;
    @Before public void setup() throws Exception {
        previous=KOMEAddon.proxy; proxy=KOMEAccessFixture.allocate(TestProxy.class);
        proxy.queue=new KOMEClientTaskQueue(); proxy.queue.resetSession(true, () -> {}); proxy.queue.drain();
        KOMEAddon.proxy=proxy; client=KOMEClientData.INSTANCE; client.resetClientState();
    }
    @After public void cleanup(){client.resetClientState();KOMEAddon.proxy=previous;}
    @Test public void repeatedPartialRefreshDoesNotExposeAbsentPopulation() {
        send(packet(true,true,summary("gondor",2500)));
        for(int i=0;i<25;i++) {
            send(packet(true,false)); assertPopulation(2500);
            send(packet(false,false,summary("gondor",2500))); assertPopulation(2500);
            send(packet(false,true)); assertPopulation(2500);
        }
    }
    @Test public void changedAndZeroValuesPublishOnlyAtCompletionAndRenderAsNumbers() {
        send(packet(true,true,summary("gondor",2500)));
        send(packet(true,false)); send(packet(false,false,summary("gondor",1250)));
        assertPopulation(2500); send(packet(false,true)); assertPopulation(1250);
        assertTrue(lines().contains("Faction Available Population: 12.50"));
        send(packet(true,true,summary("gondor",0))); assertPopulation(0);
        assertTrue(lines().contains("Faction Available Population: 0.00"));
        assertTrue(lines().contains("Faction Active Population: 0.00"));
        assertTrue(lines().contains("Faction Daily Population Rate: 0"));
    }
    @Test public void omittedSectionIsNotClearButCompletedReplacementAndEmptyRowAre() {
        send(packet(true,true,summary("gondor",2500)));
        send(packet(false,true)); assertPopulation(2500); // No reset: unrelated delta.
        KOMEPacketConquestData empty=packet(false,true);empty.data.setTag("TroopSummaries",new NBTTagList());
        send(empty);assertPopulation(2500); // Empty section is zero addressed rows.
        send(packet(false,true,summary("",0)));assertTrue(lines().isEmpty()); // Addressed clear.
        send(packet(true,true,summary("gondor",2500)));
        send(packet(true,false));assertPopulation(2500);send(packet(false,true));
        assertTrue(client.troopSummaries.isEmpty());assertTrue(lines().isEmpty());
    }
    @Test public void withdrawnPublicProjectionNeverBecomesAnInventedZeroOrRetainedSecret() {
        send(packet(true,true,summary("gondor",2500)));
        KOMETileTroopSummary tacticalOnly=summary("",0);tacticalOnly.stationedPop=20;tacticalOnly.stationedOffensivePop=20;
        send(packet(true,true,tacticalOnly));
        assertEquals(Collections.singletonList("Stationed: Off 20, Mounted 0, Def 0"),lines());
        send(packet(true,true));assertTrue(lines().isEmpty());
    }
    @Test public void missingProjectionFieldsFailBeforePublicationRatherThanEraseOrPatchData() {
        send(packet(true,true,summary("gondor",2500)));
        KOMEPacketConquestData invalid=packet(false,true,summary("gondor",0));
        invalid.data.getTagList("TroopSummaries",10).getCompoundTagAt(0).removeTag("FactionPopulationProjection");
        try{send(invalid);fail("missing complete projection");}catch(IllegalArgumentException expected){}
        assertPopulation(2500);
    }
    @Test public void newResetAbandonsPartialRowsAndCompletionPublishesOnlyLatestBatch() {
        send(packet(true,true,summary("gondor",2500)));
        send(packet(true,false,summary("gondor",9999)));assertPopulation(2500);
        send(packet(true,false));send(packet(false,true,summary("rohan",100)));
        assertPopulation(100);assertEquals("rohan",client.troopSummaries.get("T001").population.faction);
    }
    @Test public void worldUnloadClearsPublishedPendingAndPreviouslyQueuedRows() throws Exception {
        send(packet(true,true,summary("gondor",2500)));send(packet(true,false,summary("gondor",9999)));
        new KOMEPacketConquestData.Handler().onMessage(packet(true,true,summary("gondor",8888)),null);
        KOMEClientProxy lifecycle=KOMEAccessFixture.allocate(KOMEClientProxy.class);
        net.minecraft.world.World remote=KOMEAccessFixture.allocate(KOMEAccessFixture.TestWorld.class);
        java.lang.reflect.Field flag=net.minecraft.world.World.class.getDeclaredField("isRemote");flag.setAccessible(true);flag.setBoolean(remote,true);
        lifecycle.onClientWorldUnload(new net.minecraftforge.event.world.WorldEvent.Unload(remote));
        assertTrue(lines().isEmpty());proxy.queue.drain();assertTrue(lines().isEmpty());
        send(packet(false,true,summary("gondor",7777)));assertTrue(lines().isEmpty());
        send(packet(true,true,summary("rohan",0)));assertPopulation(0);
    }
    @Test public void productionDisconnectReconnectDropsPendingAndOldSessionData() throws Exception {
        send(packet(true,true,summary("gondor",2500)));send(packet(true,false,summary("gondor",9999)));
        KOMEClientProxy lifecycle=KOMEAccessFixture.allocate(KOMEClientProxy.class);
        java.lang.reflect.Field queue=KOMEClientProxy.class.getDeclaredField("clientTasks");queue.setAccessible(true);queue.set(lifecycle,proxy.queue);
        lifecycle.onClientDisconnect(null);proxy.queue.drain();assertTrue(lines().isEmpty());
        lifecycle.onClientConnect(null);proxy.queue.drain();assertTrue(lines().isEmpty());
        send(packet(false,true,summary("gondor",9999)));assertTrue(lines().isEmpty());
        send(packet(true,true,summary("rohan",100)));assertPopulation(100);
    }
    @Test public void actualServerChunkBuilderPreservesPopulationAcrossEveryIntermediatePacket() throws Exception {
        KOMEAccessFixture fixture=new KOMEAccessFixture();
        cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper old=kome.common.network.KOMEPacketHandler.network;
        try {
            kome.common.network.KOMEPacketHandler.network=fixture.network;
            for(int i=1;i<=60;i++){String id=String.format(java.util.Locale.ROOT,"T%03d",i);KOMEConquestTile tile=new KOMEConquestTile(id);tile.claim("gondor",0);fixture.data.conquestTiles.put(id,tile);}
            fixture.data.tileWaypointLinksByTileId.put("T001",waypoint("Old waypoint"));
            KOMEPopulationService.grantCenti(fixture.data,"gondor",2500);
            KOMEPacketConquestData.sendChunked(fixture.data,fixture.player);
            for(cpw.mods.fml.common.network.simpleimpl.IMessage p:fixture.network.messages)send((KOMEPacketConquestData)p);
            assertPopulation(2500);assertWaypoint("Old waypoint");fixture.data.tileWaypointLinksByTileId.put("T001",waypoint("New waypoint"));fixture.network.messages.clear();KOMEPopulationService.grantCenti(fixture.data,"gondor",100);
            KOMEPacketConquestData.sendChunked(fixture.data,fixture.player);assertTrue(fixture.network.messages.size()>2);
            for(cpw.mods.fml.common.network.simpleimpl.IMessage p:fixture.network.messages){KOMEPacketConquestData packet=(KOMEPacketConquestData)p;send(packet);assertPopulation(packet.complete?2600:2500);assertWaypoint(packet.complete?"New waypoint":"Old waypoint");}
            // Removing the public source projection is replacement, not permission to retain old values.
            fixture.network.messages.clear();fixture.data.conquestTiles.clear();fixture.data.tileWaypointLinksByTileId.clear();KOMEPacketConquestData.sendChunked(fixture.data,fixture.player);
            for(cpw.mods.fml.common.network.simpleimpl.IMessage p:fixture.network.messages)send((KOMEPacketConquestData)p);
            assertTrue(lines().isEmpty());assertTrue(client.tileWaypointLinksByTileId.isEmpty());
        } finally {kome.common.network.KOMEPacketHandler.network=old;}
    }
    @Test public void waypointStaysPublishedAcrossPartialSnapshotsAndChangesOnlyAtCompletion() {
        send(waypointPacket(true,true,"Old waypoint"));
        for(int i=0;i<25;i++) {
            send(packet(true,false)); assertWaypoint("Old waypoint");
            send(waypointPacket(false,false,"Old waypoint")); assertWaypoint("Old waypoint");
            send(packet(false,true)); assertWaypoint("Old waypoint");
        }
        send(packet(true,false));send(waypointPacket(false,false,"New waypoint"));
        assertWaypoint("Old waypoint");send(packet(false,true));assertWaypoint("New waypoint");
    }
    @Test public void waypointOmissionIsOnlyRemovalInACompletedReplacement() {
        send(waypointPacket(true,true,"Old waypoint"));send(packet(false,true));assertWaypoint("Old waypoint");
        send(packet(true,false));assertWaypoint("Old waypoint");send(packet(false,true));
        assertFalse(client.tileWaypointLinksByTileId.containsKey("T001"));
        send(waypointPacket(true,false,"Abandoned waypoint"));send(packet(true,false));send(packet(false,true));
        assertTrue(client.tileWaypointLinksByTileId.isEmpty());
    }
    @Test public void waypointWorldUnloadAndReconnectDiscardPublishedPendingAndQueuedOldLinks() throws Exception {
        send(waypointPacket(true,true,"Old waypoint"));send(waypointPacket(true,false,"Pending waypoint"));
        new KOMEPacketConquestData.Handler().onMessage(waypointPacket(true,true,"Queued waypoint"),null);
        KOMEClientProxy lifecycle=KOMEAccessFixture.allocate(KOMEClientProxy.class);
        net.minecraft.world.World remote=KOMEAccessFixture.allocate(KOMEAccessFixture.TestWorld.class);
        java.lang.reflect.Field flag=net.minecraft.world.World.class.getDeclaredField("isRemote");flag.setAccessible(true);flag.setBoolean(remote,true);
        lifecycle.onClientWorldUnload(new net.minecraftforge.event.world.WorldEvent.Unload(remote));
        assertTrue(client.tileWaypointLinksByTileId.isEmpty());proxy.queue.drain();assertTrue(client.tileWaypointLinksByTileId.isEmpty());
        send(waypointPacket(false,true,"Old tail"));assertTrue(client.tileWaypointLinksByTileId.isEmpty());
        send(waypointPacket(true,true,"New world"));assertWaypoint("New world");
        java.lang.reflect.Field queue=KOMEClientProxy.class.getDeclaredField("clientTasks");queue.setAccessible(true);queue.set(lifecycle,proxy.queue);
        send(waypointPacket(true,false,"Pending disconnect"));lifecycle.onClientDisconnect(null);proxy.queue.drain();
        assertTrue(client.tileWaypointLinksByTileId.isEmpty());lifecycle.onClientConnect(null);proxy.queue.drain();
        send(waypointPacket(false,true,"Old connection"));assertTrue(client.tileWaypointLinksByTileId.isEmpty());
        send(waypointPacket(true,true,"New connection"));assertWaypoint("New connection");
    }
    private KOMETileWaypointLink waypoint(String name) {KOMETileWaypointLink link=new KOMETileWaypointLink();link.tileId="T001";link.lotrWaypointKey="test_waypoint";link.waypointDisplayName=name;return link;}
    private KOMEPacketConquestData waypointPacket(boolean reset,boolean complete,String name) {KOMEPacketConquestData p=packet(reset,complete);NBTTagList list=new NBTTagList();list.appendTag(waypoint(name).writeToNBT());p.data.setTag("TileWaypointLinks",list);return p;}
    private void assertWaypoint(String name) {KOMETileWaypointLink link=client.tileWaypointLinksByTileId.get("T001");assertNotNull("Published waypoint vanished",link);assertEquals(name,link.displayName());}
    private List<String> lines(){List<String> lines=new ArrayList<>();KOMEConquestMapOverlay.appendPopulationTooltip(lines,client.troopSummaries.get("T001"));return lines;}
    private void assertPopulation(long value){assertNotNull("Published hover population vanished",client.troopSummaries.get("T001"));assertEquals(value,client.troopSummaries.get("T001").population.availablePopulationCenti);}
    private KOMETileTroopSummary summary(String faction,long available){KOMETileTroopSummary s=new KOMETileTroopSummary();s.tileId="T001";s.ownerFaction=faction;s.population=new KOMEPopulationProjection(faction,available,BigInteger.ZERO,BigInteger.ZERO,false,0);return s;}
    private KOMEPacketConquestData packet(boolean reset,boolean complete,KOMETileTroopSummary... rows){KOMEPacketConquestData p=new KOMEPacketConquestData();p.reset=reset;p.complete=complete;if(rows.length>0){NBTTagList list=new NBTTagList();for(KOMETileTroopSummary s:rows)list.appendTag(s.writeToNBT());p.data.setTag("TroopSummaries",list);}return p;}
    private void send(KOMEPacketConquestData p){ByteBuf b=Unpooled.buffer();try{p.toBytes(b);KOMEPacketConquestData decoded=new KOMEPacketConquestData();decoded.fromBytes(b);new KOMEPacketConquestData.Handler().onMessage(decoded,null);}finally{b.release();}proxy.queue.drain();}
    public static class TestProxy extends KOMECommonProxy {KOMEClientTaskQueue queue;@Override public void enqueueClientTask(Runnable task){queue.enqueue(task);}}
}
