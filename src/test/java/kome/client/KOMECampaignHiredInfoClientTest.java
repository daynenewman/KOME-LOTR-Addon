package kome.client;

import io.netty.buffer.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import kome.common.KOMEAccessFixture;
import kome.common.data.KOMECampaignMovementReconstructionTest.MovementNpc;
import kome.common.network.KOMEPacketCampaignHiredInfo;
import lotr.common.LOTRMod;
import lotr.common.LOTRCommonProxy;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.network.LOTRPacketHiredInfo;
import net.minecraft.world.World;
import org.junit.Test;
import static org.junit.Assert.*;

public class KOMECampaignHiredInfoClientTest {
    private KOMEPacketCampaignHiredInfo publication(MovementNpc npc,UUID owner){
        KOMEPacketCampaignHiredInfo p=new KOMEPacketCampaignHiredInfo(npc);
        p.nativeInfo=new LOTRPacketHiredInfo(p.entityId,owner,LOTRHiredNPCInfo.Task.WARRIOR,"Guard",7);
        ByteBuf bytes=Unpooled.buffer();p.toBytes(bytes);
        KOMEPacketCampaignHiredInfo decoded=new KOMEPacketCampaignHiredInfo();decoded.fromBytes(bytes);assertFalse(bytes.isReadable());return decoded;
    }
    @Test public void nativeBaselineRequiresExistingClientEntityAndQueuedCampaignDeliveryRestoresSameState() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.rand=new Random(5);
        MovementNpc npc=new MovementNpc(f.world);npc.setHealth(13F);f.world.isRemote=true;
        KOMEPacketCampaignHiredInfo p=publication(npc,f.player.getUniqueID());
        LOTRCommonProxy previous=LOTRMod.proxy;
        try {
            LOTRMod.proxy=new LOTRCommonProxy(){@Override public World getClientWorld(){return f.world;}};
            new LOTRPacketHiredInfo.Handler().onMessage(p.nativeInfo,null); // native handler drops pre-spawn data
            assertNull(npc.hiredNPCInfo.getHiringPlayerUUID());
            KOMECampaignHiredInfoClient bridge=new KOMECampaignHiredInfoClient(()->f.world);
            bridge.accept(p);assertEquals(1,bridge.pendingCount());
            f.world.loadedEntityList.add(npc);bridge.tick();assertEquals(0,bridge.pendingCount());
            assertSame(f.player,npc.hiredNPCInfo.getHiringPlayer());assertEquals(LOTRHiredNPCInfo.Task.WARRIOR,npc.hiredNPCInfo.getTask());
            assertEquals("Guard",npc.hiredNPCInfo.getSquadron());assertEquals(7,npc.hiredNPCInfo.xpLevel);
            assertEquals(13F,npc.getHealth(),0);assertEquals(20F,npc.getMaxHealth(),0);
            new LOTRPacketHiredInfo.Handler().onMessage(p.nativeInfo,null); // ordinary post-spawn baseline
            assertSame(f.player,npc.hiredNPCInfo.getHiringPlayer());assertEquals(13F,npc.getHealth(),0);
        } finally {LOTRMod.proxy=previous;}
    }
    @Test public void waitsForNativePhysicalUuidSynchronizationBeforeAdoptingHiredInfo() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.rand=new Random(3);
        MovementNpc server=new MovementNpc(f.world),client=new MovementNpc(f.world);f.world.isRemote=true;
        KOMEPacketCampaignHiredInfo p=publication(server,f.player.getUniqueID());p.entityId=client.getEntityId();
        f.world.loadedEntityList.add(client);
        KOMECampaignHiredInfoClient bridge=new KOMECampaignHiredInfoClient(()->f.world);bridge.accept(p);
        assertNull(client.hiredNPCInfo.getHiringPlayerUUID());assertEquals(1,bridge.pendingCount());
        LOTRCommonProxy previous=LOTRMod.proxy;
        try {
            LOTRMod.proxy=new LOTRCommonProxy(){@Override public World getClientWorld(){return f.world;}};
            new lotr.common.network.LOTRPacketEntityUUID.Handler().onMessage(
                new lotr.common.network.LOTRPacketEntityUUID(client.getEntityId(),server.getUniqueID()),null);
            bridge.tick();assertEquals(0,bridge.pendingCount());
            assertEquals(server.getUniqueID(),client.getUniqueID());assertSame(f.player,client.hiredNPCInfo.getHiringPlayer());
        } finally {LOTRMod.proxy=previous;}
    }
    @Test public void newerNativePublicationRemovesOlderPendingState() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.rand=new Random(3);
        MovementNpc npc=new MovementNpc(f.world);f.world.isRemote=true;
        KOMECampaignHiredInfoClient bridge=new KOMECampaignHiredInfoClient(()->f.world);
        bridge.accept(publication(npc,f.player.getUniqueID()));assertEquals(1,bridge.pendingCount());
        f.world.loadedEntityList.add(npc);
        KOMEPacketCampaignHiredInfo latest=publication(npc,f.player.getUniqueID());latest.nativeInfo.squadron="New";
        bridge.accept(latest);assertEquals(0,bridge.pendingCount());bridge.tick();
        assertEquals("New",npc.hiredNPCInfo.getSquadron());
    }
    @Test public void reusedEntityIdCannotGiveUnrelatedNpcHiredPresentation() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.rand=new Random(3);
        MovementNpc intended=new MovementNpc(f.world),unrelated=new MovementNpc(f.world);
        f.world.isRemote=true;KOMEPacketCampaignHiredInfo p=publication(intended,f.player.getUniqueID());p.entityId=unrelated.getEntityId();
        f.world.loadedEntityList.add(unrelated);
        KOMECampaignHiredInfoClient bridge=new KOMECampaignHiredInfoClient(()->f.world);bridge.accept(p);bridge.tick();
        assertNull(unrelated.hiredNPCInfo.getHiringPlayerUUID());assertEquals(1,bridge.pendingCount());
        for(int i=0;i<100;i++)bridge.tick();assertEquals(0,bridge.pendingCount());
    }
    @Test public void pendingNativePresentationIsBoundedAndClearedAcrossWorldSessions() throws Exception {
        KOMEAccessFixture f=new KOMEAccessFixture();f.world.rand=new Random(3);MovementNpc npc=new MovementNpc(f.world);f.world.isRemote=true;
        AtomicReference<World> world=new AtomicReference<>(f.world);
        KOMECampaignHiredInfoClient bridge=new KOMECampaignHiredInfoClient(world::get);
        for(int i=0;i<200;i++){
            KOMEPacketCampaignHiredInfo p=publication(npc,f.player.getUniqueID());p.physicalId=UUID.randomUUID();bridge.accept(p);
        }
        assertEquals(128,bridge.pendingCount());world.set(null);bridge.tick();assertEquals(0,bridge.pendingCount());
        world.set(f.world);bridge.tick();assertEquals(0,bridge.pendingCount());
    }
}
