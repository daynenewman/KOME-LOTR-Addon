package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import java.util.ArrayList;
import java.util.List;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.*;
import static org.junit.Assert.*;

/** Exercises the actual server projection, chunk dispatch and registered lifecycle handlers. */
public class KOMEConquestSyncTest {
    private KOMEAccessFixture fixture;
    private SimpleNetworkWrapper previous;
    private KOMEPopulationTestConfig config;

    @Before public void setUp() throws Exception {
        config = new KOMEPopulationTestConfig();
        fixture = new KOMEAccessFixture();
        previous = KOMEPacketHandler.network;
        KOMEPacketHandler.network = fixture.network;
        KOMEPacketConquestData.clearSentSnapshots();
        KOMEConquestTile tile = new KOMEConquestTile("T132");
        tile.claim("dunedain", 1); fixture.data.conquestTiles.put(tile.id, tile);
    }
    @After public void tearDown() throws Exception {
        KOMEPacketConquestData.clearSentSnapshots();
        KOMEPacketHandler.network = previous; config.close();
    }
    private void send() { KOMEPacketConquestData.sendIfChanged(fixture.data, fixture.player); }
    private List<KOMEPacketConquestData> take() {
        List<KOMEPacketConquestData> result = new ArrayList<KOMEPacketConquestData>();
        for (IMessage m : fixture.network.messages) result.add((KOMEPacketConquestData)m);
        fixture.network.messages.clear(); return result;
    }
    private static NBTTagCompound assembled(List<KOMEPacketConquestData> packets) {
        assertFalse(packets.isEmpty());
        NBTTagCompound result = new NBTTagCompound(); int resets = 0, completes = 0;
        for (KOMEPacketConquestData packet : packets) {
            if (packet.reset) resets++; if (packet.complete) completes++;
            for (Object keyObject : packet.data.func_150296_c()) {
                String key = (String) keyObject;
                NBTTagList rows = packet.data.getTagList(key, 10);
                assertTrue(rows.tagCount() <= 48);
                NBTTagList all = result.getTagList(key, 10);
                for (int i=0;i<rows.tagCount();i++) all.appendTag(rows.getCompoundTagAt(i).copy());
                result.setTag(key, all);
            }
        }
        assertEquals(1, resets); assertEquals(1, completes);
        assertTrue(packets.get(0).reset); assertTrue(packets.get(packets.size()-1).complete);
        return result;
    }
    @Test public void repeatedIdenticalBroadcastsSendNothingAndDoNotDirtyWorld() {
        fixture.data.setDirty(false); send(); assembled(take());
        for(int i=0;i<100;i++) send();
        assertTrue(take().isEmpty()); assertFalse(fixture.data.isDirty());
    }
    @Test public void ownershipChangeProducesCompleteSnapshotOnce() {
        send(); take(); fixture.data.conquestTiles.get("T132").claim("gondor", 2);
        send(); NBTTagCompound actual = assembled(take());
        assertEquals(new KOMEPacketConquestData(fixture.data).data.getTag("ConquestTiles"), actual.getTag("ConquestTiles"));
        assertEquals(new KOMEPacketConquestData(fixture.data).data.getTag("TroopSummaries"), actual.getTag("TroopSummaries"));
        send(); assertTrue(take().isEmpty());
    }
    @Test public void companyMovementAndRouteUpdatesAreNotSuppressed() {
        send(); take();
        KOMEArmyCompany company = new KOMEArmyCompany(); company.id = "C1";
        company.name = "Test company"; company.currentTile = "T132";
        fixture.data.armyCompanies.put(company.id, company); send();
        assertEquals(1, assembled(take()).getTagList("ArmyCompanies", 10).tagCount());
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder(); order.id = "M1";
        order.originTile = "T132"; order.destinationTile = "T149";
        fixture.data.armyMovements.put(order.id, order); send();
        assertEquals(1, assembled(take()).getTagList("ArmyMovements", 10).tagCount());
        order.status = KOMEArmyMovementOrder.ARRIVED; send();
        assertEquals(0, assembled(take()).getTagList("ArmyMovements", 10).tagCount());
        KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge("T132", "T149", KOMEConquestRouteEdge.OPEN);
        fixture.data.routeEdges.put(KOMEConquestRouteEdge.key(edge.fromTile, edge.toTile), edge);
        send(); assembled(take()); edge.edgeType = KOMEConquestRouteEdge.BLOCKED;
        send(); assertEquals(edge.writeToNBT(), assembled(take()).getTagList("RouteEdges", 10).getCompoundTagAt(0));
    }

    @Test public void waypointAdditionAndRemovalAreCompleteReplacements() {
        send(); take(); KOMETileWaypointLink link = new KOMETileWaypointLink();
        link.tileId="T132"; link.lotrWaypointKey="weathertop";
        fixture.data.tileWaypointLinksByTileId.put(link.tileId,link);
        send(); assertEquals(1,assembled(take()).getTagList("TileWaypointLinks",10).tagCount());
        fixture.data.tileWaypointLinksByTileId.clear(); send();
        assertEquals(0,assembled(take()).getTagList("TileWaypointLinks",10).tagCount());
    }
    @Test public void populationChangeAndReturnToZeroAreNotSuppressed() {
        KOMEPlayerBuild build=new KOMEPlayerBuild(); build.id="B1"; build.tileId="T132";
        build.populationFaction="dunedain"; build.type=KOMEBuildType.NORMAL; build.active=true;
        KOMEBuildContribution contribution=new KOMEBuildContribution();
        contribution.id="H1"; contribution.status=KOMEBuildContribution.APPROVED; contribution.centiHours=10000;
        build.contributions.add(contribution);
        fixture.data.builds.put(build.id,build); send(); NBTTagCompound zero=assembled(take());
        build.developedNativeCentiHours=10000; send(); NBTTagCompound positive=assembled(take());
        assertNotEquals(zero.getTag("TroopSummaries"),positive.getTag("TroopSummaries"));
        build.developedNativeCentiHours=0; send();
        assertEquals(zero.getTag("TroopSummaries"),assembled(take()).getTag("TroopSummaries"));
    }
    @Test public void buildMarkerVisibilityRemovalAndPrivateFieldsKeepProjectionContract() {
        KOMEPlayerBuild build=new KOMEPlayerBuild();build.id="B1";build.tileId="T132";
        build.type=KOMEBuildType.NORMAL;build.active=true;build.markerVisible=true;
        fixture.data.builds.put(build.id,build); send();
        NBTTagCompound marker=assembled(take()).getTagList("BuildMarkers",10).getCompoundTagAt(0);
        assertFalse(marker.hasKey("Contributions")); assertFalse(marker.hasKey("Audit"));
        build.markerVisible=false; send();
        assertEquals(0,assembled(take()).getTagList("BuildMarkers",10).tagCount());
    }
    @Test public void eachRecipientGetsIndependentInitialSnapshot() throws Exception {
        send(); take(); KOMEAccessFixture other=new KOMEAccessFixture();
        KOMEPacketConquestData.sendIfChanged(fixture.data,other.player); assembled(take());
        send(); assertTrue(take().isEmpty());
    }
    @Test public void permissionAndPledgeChangesInvalidateEvenWhenPublicProjectionIsEqual() throws Exception {
        send(); take(); fixture.player.operator=true; send(); assembled(take());
        fixture.player.operator=false; send(); assembled(take());
        fixture.pledge(LOTRFaction.RANGER_NORTH); send(); assembled(take());
        fixture.pledge(null); send(); assembled(take());
        send(); assertTrue(take().isEmpty());
    }
    @Test public void replacementPlayerWithSameUuidAndLogoutReconnectGetBaseline() throws Exception {
        send(); take(); KOMEAccessFixture other=new KOMEAccessFixture();other.player.id=fixture.player.id;
        KOMEPacketConquestData.sendIfChanged(fixture.data,other.player);assembled(take());
        new KOMEEvents().onPlayerLogout(new PlayerEvent.PlayerLoggedOutEvent(fixture.player));
        send(); assembled(take());
    }
    @Test public void worldDimensionAndDataIdentityChangesInvalidate() throws Exception {
        send(); take(); fixture.player.dimension=100;send();assembled(take());
        fixture.player.worldObj=new KOMEAccessFixture().world;send();assembled(take());
        KOMEWorldData replacement=new KOMEWorldData();replacement.conquestTiles.putAll(fixture.data.conquestTiles);
        KOMEPacketConquestData.sendIfChanged(replacement,fixture.player);assembled(take());
    }
    @Test public void actualRespawnAndDimensionHooksForceCompleteInitialization() {
        send();take();KOMEEvents events=new KOMEEvents();
        events.onPlayerRespawn(new PlayerEvent.PlayerRespawnEvent(fixture.player));assembled(take());
        events.onPlayerChangedDimension(new PlayerEvent.PlayerChangedDimensionEvent(fixture.player,0,100));assembled(take());
    }
    @Test public void sessionResetAndExplicitResyncAreNeverSuppressed() {
        send();take();new KOMEEvents().resetSessionState();send();assembled(take());
        fixture.data.syncConquestTiles(fixture.player);assembled(take());
    }
    @Test public void outgoingPacketsCannotMutateCachedProjectionAndWorldChangesCannotMutatePackets() {
        send();List<KOMEPacketConquestData> first=take();
        NBTTagCompound copy=assembled(first);
        // A transport observer changing its message must not poison the private baseline.
        first.get(0).data.setString("Unexpected","value");send();assertTrue(take().isEmpty());
        first.get(0).data.removeTag("Unexpected");
        fixture.data.conquestTiles.get("T132").claim("gondor",2);
        assertEquals(copy,assembled(first));send();assembled(take());
        send();assertTrue(take().isEmpty());
    }
    @Test public void failedDispatchRetriesFullResetEvenAfterWorldReverts() throws Exception {
        send();take();long originalClaimTime=fixture.data.conquestTiles.get("T132").claimedAtMillis;
        fixture.data.conquestTiles.get("T132").claim("gondor",2);
        FailingNetwork fail=KOMEAccessFixture.allocate(FailingNetwork.class);
        KOMEPacketHandler.network=fail;
        try { send(); fail("Expected dispatch failure"); } catch(IllegalStateException expected) { }
        finally { KOMEPacketHandler.network=fixture.network; }
        fixture.data.conquestTiles.get("T132").claim("dunedain",1);
        fixture.data.conquestTiles.get("T132").claimedAtMillis=originalClaimTime;
        send();assembled(take());
    }
    @Test public void boundedRecipientCacheEvictionResendsInsteadOfDroppingUpdates() throws Exception {
        send();take();
        for(int i=0;i<256;i++) {
            KOMEAccessFixture other=new KOMEAccessFixture();
            KOMEPacketConquestData.sendIfChanged(fixture.data,other.player);take();
        }
        java.lang.reflect.Field cache=KOMEPacketConquestData.class.getDeclaredField("SENT");
        cache.setAccessible(true);assertTrue(((java.util.Map<?,?>)cache.get(null)).size()<=256);
        fixture.data.conquestTiles.get("T132").claim("gondor",2);
        send();assembled(take());
    }
    @Test public void clearingLastVisibleRecordStillSendsResetAndCompletion() {
        send();take();fixture.data.conquestTiles.clear();send();assembled(take());
        send();assertTrue(take().isEmpty());
    }
    public static final class FailingNetwork extends SimpleNetworkWrapper {
        private FailingNetwork(){super("unused");}
        @Override public net.minecraft.network.Packet getPacketFrom(IMessage message) {
            throw new IllegalStateException("test dispatch failure");
        }
    }
}
