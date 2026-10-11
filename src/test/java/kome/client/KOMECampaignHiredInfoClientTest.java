package kome.client;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMECampaignMovementReconstructionTest.MovementNpc;
import kome.common.network.KOMEPacketCampaignHiredInfoRequest;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECampaignHiredInfoClientTest {
    @Test public void acknowledgmentWaitsForExactInstalledEntityWithoutRequiringServerUuid() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.rand=new Random(3);
        MovementNpc npc=new MovementNpc(f.world);f.world.isRemote=true;
        List<KOMEPacketCampaignHiredInfoRequest> requests=new ArrayList<>();
        KOMECampaignHiredInfoClient bridge=new KOMECampaignHiredInfoClient(()->f.world,requests::add);
        bridge.onJoined(new EntityJoinWorldEvent(npc,f.world));bridge.tick();assertTrue(requests.isEmpty());
        npc.setEntityId(724);f.world.loadedEntityList.add(npc);bridge.tick();
        assertEquals(1,requests.size());assertEquals(724,requests.get(0).entityId);
        assertEquals(f.world.provider.dimensionId,requests.get(0).dimension);
        assertNull(npc.hiredNPCInfo.getHiringPlayerUUID());bridge.tick();assertEquals(1,requests.size());
    }
    @Test public void acknowledgmentsAreBoundedAtEightPerTickAnd128Pending() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.rand=new Random(3);
        List<KOMEPacketCampaignHiredInfoRequest> requests=new ArrayList<>();
        KOMECampaignHiredInfoClient bridge=new KOMECampaignHiredInfoClient(()->f.world,requests::add);
        for(int i=0;i<200;i++){MovementNpc npc=new MovementNpc(f.world);f.world.isRemote=true;f.world.loadedEntityList.add(npc);bridge.joined(npc);}
        assertEquals(128,bridge.pendingCount());bridge.tick();assertEquals(8,requests.size());assertEquals(120,bridge.pendingCount());
        bridge.tick();assertEquals(16,requests.size());assertEquals(112,bridge.pendingCount());
    }
    @Test public void failedSpawnDeathAndExpiredWorkNeverRequestSynchronization() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.rand=new Random(3);MovementNpc npc=new MovementNpc(f.world);f.world.isRemote=true;
        List<KOMEPacketCampaignHiredInfoRequest> requests=new ArrayList<>();
        KOMECampaignHiredInfoClient bridge=new KOMECampaignHiredInfoClient(()->f.world,requests::add);
        bridge.joined(npc);for(int i=0;i<100;i++)bridge.tick();assertEquals(0,bridge.pendingCount());assertTrue(requests.isEmpty());
        bridge.joined(npc);f.world.loadedEntityList.add(npc);npc.isDead=true;bridge.tick();assertTrue(requests.isEmpty());
    }
    @Test public void connectionResetPrecedesNewSessionJoinAcknowledgment() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.rand=new Random(3);MovementNpc npc=new MovementNpc(f.world);f.world.isRemote=true;
        List<KOMEPacketCampaignHiredInfoRequest> requests=new ArrayList<>();KOMEClientTaskQueue tasks=new KOMEClientTaskQueue();
        KOMECampaignHiredInfoClient bridge=new KOMECampaignHiredInfoClient(()->f.world,requests::add,tasks::enqueue);
        tasks.resetSession(true,bridge::clear);
        bridge.onJoined(new EntityJoinWorldEvent(npc,f.world));f.world.loadedEntityList.add(npc);
        tasks.drain();bridge.tick();assertEquals(1,requests.size());
        tasks.resetSession(false,bridge::clear);bridge.onJoined(new EntityJoinWorldEvent(npc,f.world));
        tasks.drain();bridge.tick();assertEquals(1,requests.size());
    }
    @Test public void worldAndConnectionResetsDiscardOldAcknowledgments() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.rand=new Random(3);MovementNpc npc=new MovementNpc(f.world);f.world.isRemote=true;
        AtomicReference<World> world=new AtomicReference<>(f.world);List<KOMEPacketCampaignHiredInfoRequest> requests=new ArrayList<>();
        KOMECampaignHiredInfoClient bridge=new KOMECampaignHiredInfoClient(world::get,requests::add);
        bridge.joined(npc);world.set(null);bridge.tick();assertEquals(0,bridge.pendingCount());
        world.set(f.world);bridge.joined(npc);bridge.clear();f.world.loadedEntityList.add(npc);bridge.tick();assertTrue(requests.isEmpty());
    }
}
