package kome.client;

import java.util.*;
import java.lang.reflect.Constructor;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import kome.common.*;
import kome.common.data.KOMEUnitMapMarker;
import kome.common.network.KOMEPacketUnitMapMarkers;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.INetHandler;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMEUnitMapMarkerPublisherTest {
    @Test public void newerMarkerReplacementRunsAfterInterveningConquestPublication() {
        KOMEClientTaskQueue tasks=new KOMEClientTaskQueue();tasks.resetSession(true,()->{});
        List<String> order=new ArrayList<String>();
        KOMEUnitMapMarkerPublisher markers=new KOMEUnitMapMarkerPublisher(tasks,
            rows->order.add("markers:"+rows.get(0).entityId));
        Object connection=new Object();markers.start(connection);
        markers.accept(rows("old"),connection);
        tasks.enqueueLatest("conquest",()->order.add("filtered-conquest"));
        markers.accept(rows("filtered"),connection);
        tasks.drain();
        assertEquals(Arrays.asList("filtered-conquest","markers:filtered"),order);
    }
    @Test public void onlyClientDrainPublishesLatestFullReplacementIncludingEmptyRemoval() {
        KOMEClientTaskQueue tasks=new KOMEClientTaskQueue();tasks.resetSession(true,()->{});
        List<KOMEUnitMapMarker> visible=new ArrayList<>();
        KOMEUnitMapMarkerPublisher p=new KOMEUnitMapMarkerPublisher(tasks,rows->{visible.clear();visible.addAll(rows);});Object connection=new Object();p.start(connection);
        p.accept(rows("old"),connection);p.accept(rows("new"),connection);
        assertTrue(visible.isEmpty());assertEquals(2,tasks.pendingTasks());tasks.drain();assertEquals("new",visible.get(0).entityId);
        p.accept(Collections.emptyList(),connection);assertEquals(1,visible.size());tasks.drain();assertTrue(visible.isEmpty());
    }
    @Test public void oldConnectionQueuedEpochAndDisconnectedIntakeCannotReviveMarkers() {
        KOMEClientTaskQueue tasks=new KOMEClientTaskQueue();tasks.resetSession(true,()->{});
        List<String> published=new ArrayList<>();KOMEUnitMapMarkerPublisher p=new KOMEUnitMapMarkerPublisher(tasks,rows->published.add(rows.get(0).entityId));
        Object old=new Object(),current=new Object();p.start(old);p.accept(rows("old"),old);p.start(current);tasks.drain();assertTrue(published.isEmpty());
        p.accept(rows("late"),old);assertEquals(0,tasks.pendingTasks());p.accept(rows("new"),current);tasks.drain();assertEquals(Arrays.asList("new"),published);
        p.accept(rows("queued"),current);p.start(null);tasks.drain();p.accept(rows("disconnected"),current);tasks.drain();assertEquals(Arrays.asList("new"),published);
    }
    @Test public void worldInvalidationDropsPendingWorkAndNextSnapshotRecovers() {
        KOMEClientTaskQueue tasks=new KOMEClientTaskQueue();tasks.resetSession(true,()->{});List<String> published=new ArrayList<>();
        KOMEUnitMapMarkerPublisher p=new KOMEUnitMapMarkerPublisher(tasks,rows->published.add(rows.get(0).entityId));Object connection=new Object();p.start(connection);
        p.accept(rows("stale-world"),connection);p.invalidateWorld();tasks.drain();assertTrue(published.isEmpty());
        p.accept(rows("current-world"),connection);tasks.drain();assertEquals(Arrays.asList("current-world"),published);
    }
    @Test public void actualPacketHandlerDeepCopiesBeforeQueuedSessionPublication()throws Exception {
        KOMEClientTaskQueue tasks=new KOMEClientTaskQueue();tasks.resetSession(true,()->{});List<KOMEUnitMapMarker> visible=new ArrayList<>();
        KOMEUnitMapMarkerPublisher p=new KOMEUnitMapMarkerPublisher(tasks,rows->{visible.clear();visible.addAll(rows);});
        NetHandlerPlayClient connection=KOMEAccessFixture.allocate(NetHandlerPlayClient.class);p.start(connection);
        Constructor<MessageContext> ctor=MessageContext.class.getDeclaredConstructor(INetHandler.class,Side.class);ctor.setAccessible(true);
        RecordingProxy proxy=KOMEAccessFixture.allocate(RecordingProxy.class);proxy.publisher=p;KOMECommonProxy previous=KOMEAddon.proxy;
        try {
            KOMEAddon.proxy=proxy;KOMEPacketUnitMapMarkers packet=new KOMEPacketUnitMapMarkers(rows("original"));
            new KOMEPacketUnitMapMarkers.Handler().onMessage(packet,ctor.newInstance(connection,Side.CLIENT));
            ((KOMEUnitMapMarker)packet.markers.get(0)).entityId="mutated";packet.markers.clear();assertTrue(visible.isEmpty());
            tasks.drain();assertEquals("original",visible.get(0).entityId);
        }finally{KOMEAddon.proxy=previous;}
    }
    static List<KOMEUnitMapMarker> rows(String id){KOMEUnitMapMarker marker=new KOMEUnitMapMarker();marker.entityId=id;return new ArrayList<>(Arrays.asList(marker));}
    public static class RecordingProxy extends KOMECommonProxy {
        KOMEUnitMapMarkerPublisher publisher;
        @Override public void acceptUnitMapMarkers(List<KOMEUnitMapMarker> markers,Object connection){publisher.accept(markers,connection);}
    }
}
