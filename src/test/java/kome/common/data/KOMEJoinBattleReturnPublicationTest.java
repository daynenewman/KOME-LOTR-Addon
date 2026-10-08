package kome.common.data;

import java.lang.reflect.Field;
import java.util.*;
import kome.common.KOMEAccessFixture;
import net.minecraft.entity.*;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.util.IntHashMap;
import net.minecraft.world.*;
import net.minecraft.world.chunk.Chunk;
import org.junit.Test;
import static org.junit.Assert.*;

/** Real chunk packets and vanilla EntityTrackerEntry; no server or client is launched. */
public class KOMEJoinBattleReturnPublicationTest {
    @Test public void unmountedDistantTeleportRegistersNativeWatcherAndSendsTerrainBeforePosition() throws Exception {
        Fixture f=new Fixture();f.nativeManager();
        f.access.player.setPosition(1024D,65D,1024D);
        f.access.player.managedPosX=1024D;f.access.player.managedPosZ=1024D;
        KOMEJoinBattlePhysicalAccess.movePlayer(f.access.player,
            new KOMEJoinBattleDeploymentReceipt.Pose(0,8D,65D,8D,0F,0F));
        assertTrue(f.world.manager.isPlayerWatchingChunk(f.access.player,0,0));
        assertFalse(f.access.player.loadedChunks.contains(f.chunk.getChunkCoordIntPair()));
        assertTrue(index(f.access.player.vanillaPackets,S26PacketMapChunkBulk.class)>=0);
        assertTrue(index(f.access.player.vanillaPackets,S08PacketPlayerPosLook.class)>
            index(f.access.player.vanillaPackets,S26PacketMapChunkBulk.class));
        assertEquals(8D,f.access.player.managedPosX,0D);
        assertEquals(8D,f.access.player.posX,0D);
    }

    @Test public void orderedReturnTeleportsAndPublishesBeforeReplacementSpawnThenAttaches() throws Exception {
        Fixture f=new Fixture();f.nativeManager();
        f.access.player.setPosition(1024D,65D,1024D);
        f.access.player.managedPosX=1024D;f.access.player.managedPosZ=1024D;
        KOMEJoinBattlePhysicalAccess.movePlayer(f.access.player,
            new KOMEJoinBattleDeploymentReceipt.Pose(0,8D,65D,8D,0F,0F));
        try(kome.common.KOMETestServerSession session=new kome.common.KOMETestServerSession(f.access)){
            session.server.worldServers=new WorldServer[]{f.world};
            KOMEJoinBattlePhysicalAccess.RiderSyncResult result=
                KOMEJoinBattlePhysicalAccess.synchronizeMountedPlayerDetailed(f.access.player,f.mount,
                    KOMEJoinBattlePhysicalAccess.MountPublicationMode.ORDERED_EGRESS);
            assertTrue(result.stage.toString(),result.success());
        }
        List<Packet> packets=f.access.player.vanillaPackets;
        int terrain=index(packets,S26PacketMapChunkBulk.class),spawn=index(packets,S0FPacketSpawnMob.class);
        int attach=index(packets,S1BPacketEntityAttach.class);
        assertTrue(terrain>=0&&spawn>terrain&&attach>spawn);
        assertTrue(index(packets,S08PacketPlayerPosLook.class)<spawn);
        assertTrue(packets.get(packets.size()-1) instanceof S08PacketPlayerPosLook);
        for(int i=attach;i<packets.size();i++)assertFalse(
            "No full chunk reload may follow the new mount attachment",packets.get(i) instanceof S26PacketMapChunkBulk);
        assertSame(f.mount,f.access.player.ridingEntity);
        assertSame(f.access.player,f.mount.riddenByEntity);
        assertTrue(f.world.manager.isPlayerWatchingChunk(f.access.player,0,0));
        assertClientAttachmentSurvivesPositionPackets(f,packets);
    }

    /** Execute the real 1.7.10 client attach/position handlers, not a reciprocal-server-only check. */
    private static void assertClientAttachmentSurvivesPositionPackets(Fixture f,List<Packet> packets) throws Exception {
        net.minecraft.client.Minecraft minecraft=KOMEAccessFixture.allocate(net.minecraft.client.Minecraft.class);
        net.minecraft.client.entity.EntityClientPlayerMP rider=KOMEAccessFixture.allocate(
            net.minecraft.client.entity.EntityClientPlayerMP.class);
        rider.setEntityId(f.access.player.getEntityId());rider.worldObj=f.access.world;
        rider.yOffset=1.62F;rider.width=.6F;rider.height=1.8F;
        set(Entity.class,rider,"boundingBox",net.minecraft.util.AxisAlignedBB.getBoundingBox(0,0,0,0,0,0));
        minecraft.thePlayer=rider;
        minecraft.gameSettings=KOMEAccessFixture.allocate(net.minecraft.client.settings.GameSettings.class);
        minecraft.gameSettings.keyBindSneak=new net.minecraft.client.settings.KeyBinding("test",-100,"test");
        minecraft.ingameGUI=KOMEAccessFixture.allocate(QuietGui.class);
        ClientWorld client=KOMEAccessFixture.allocate(ClientWorld.class);client.rider=rider;
        set(World.class,client,"isRemote",true);rider.worldObj=client;
        net.minecraft.client.network.NetHandlerPlayClient handler=KOMEAccessFixture.allocate(
            net.minecraft.client.network.NetHandlerPlayClient.class);
        set(net.minecraft.client.network.NetHandlerPlayClient.class,handler,"gameController",minecraft);
        set(net.minecraft.client.network.NetHandlerPlayClient.class,handler,"clientWorldController",client);
        set(net.minecraft.client.network.NetHandlerPlayClient.class,handler,"doneLoadingTerrain",true);
        set(net.minecraft.client.network.NetHandlerPlayClient.class,handler,"netManager",new SinkNetwork());
        Field locale=net.minecraft.client.resources.I18n.class.getDeclaredField("i18nLocale");
        locale.setAccessible(true);Object oldLocale=locale.get(null);
        locale.set(null,new net.minecraft.client.resources.Locale());
        try{
        boolean terrain=false;
        for(Packet packet:packets){
            if(packet instanceof S26PacketMapChunkBulk)terrain=true;
            else if(packet instanceof S0FPacketSpawnMob){
                assertTrue("Client terrain must exist before entity publication",terrain);
                client.mount=new EntityHorse(f.access.world);client.mount.setEntityId(f.mount.getEntityId());
                client.mount.setPosition(f.mount.posX,f.mount.posY,f.mount.posZ);
            }else if(packet instanceof S1BPacketEntityAttach){
                assertNotNull("Client must resolve the new transient ID",client.mount);
                handler.handleEntityAttach((S1BPacketEntityAttach)packet);
                assertSame(client.mount,rider.ridingEntity);
                assertSame(rider,client.mount.riddenByEntity);
            }else if(packet instanceof S08PacketPlayerPosLook){
                Entity before=rider.ridingEntity;
                handler.handlePlayerPosLook((S08PacketPlayerPosLook)packet);
                assertSame("Native position packets must not detach the client",before,rider.ridingEntity);
            }
        }
        client.mount.updateRiderPosition();
        assertSame(client.mount,rider.ridingEntity);assertSame(rider,client.mount.riddenByEntity);
        assertEquals(client.mount.posY+client.mount.getMountedYOffset()+rider.getYOffset(),rider.posY,0D);
        handler.handleEntityAttach(new S1BPacketEntityAttach(0,rider,null));
        assertNull("Normal subsequent dismount works without a repair",rider.ridingEntity);
        }finally{locale.set(null,oldLocale);}
    }

    public static class ClientWorld extends net.minecraft.client.multiplayer.WorldClient {
        Entity rider,mount;
        private ClientWorld(){super(null,null,0,null,null);}
        @Override public Entity getEntityByID(int id){return rider!=null&&id==rider.getEntityId()?rider:
            mount!=null&&id==mount.getEntityId()?mount:null;}
    }
    public static class QuietGui extends net.minecraft.client.gui.GuiIngame {
        private QuietGui(){super(null);}
        @Override public void func_110326_a(String text,boolean playing){}
    }
    private static class SinkNetwork extends net.minecraft.network.NetworkManager {
        SinkNetwork(){super(true);}
        @Override public void scheduleOutboundPacket(Packet packet,io.netty.util.concurrent.GenericFutureListener... listeners){}
    }
    @Test public void distantReturnPublishesBeforeSpawnDespiteUntickedChunkAndWatcherLag() throws Exception {
        Fixture f=new Fixture();
        assertFalse(f.chunk.func_150802_k());
        assertFalse(f.world.manager.isPlayerWatchingChunk(f.access.player,0,0));
        f.access.player.loadedChunks.add(f.chunk.getChunkCoordIntPair());
        assertEquals(KOMEJoinBattlePhysicalAccess.RiderSyncStage.SUCCESS,
            KOMEJoinBattlePhysicalAccess.publishPreparedMountToPlayer(f.access.player,f.mount,
                f.world,f.chunk,KOMEJoinBattlePhysicalAccess.MountPublicationMode.ORDERED_EGRESS));
        int chunkPacket=index(f.access.player.vanillaPackets,S26PacketMapChunkBulk.class);
        int spawn=index(f.access.player.vanillaPackets,S0FPacketSpawnMob.class);
        assertTrue(chunkPacket>=0);assertTrue("terrain must precede replacement spawn",spawn>chunkPacket);
        assertTrue(f.world.tracker.getTrackingPlayers(f.mount).contains(f.access.player));
        assertFalse("bounded forceSpawn must always be cleared",f.mount.forceSpawn);
        assertFalse(f.access.player.loadedChunks.contains(f.chunk.getChunkCoordIntPair()));
        assertFalse("do not fabricate light/tick completion",f.chunk.func_150802_k());
        f.access.player.mountEntity(f.mount);f.mount.updateRiderPosition();
        assertSame(f.mount,f.access.player.ridingEntity);assertSame(f.access.player,f.mount.riddenByEntity);
        assertTrue(index(f.access.player.vanillaPackets,S1BPacketEntityAttach.class)>spawn);
        assertEquals(f.mount.posY+f.mount.getMountedYOffset()+f.access.player.getYOffset(),
            f.access.player.posY,0D);
    }

    @Test public void returnChunkIsDeliveredEvenWhenNotYetInPlayersSendQueue() throws Exception {
        Fixture f=new Fixture();
        assertTrue(f.access.player.loadedChunks.isEmpty());
        assertTrue(KOMEJoinBattlePhysicalAccess.publishChunkToPlayerBeforeMount(f.access.player,f.world,f.chunk));
        assertTrue(index(f.access.player.vanillaPackets,S26PacketMapChunkBulk.class)>=0);
    }

    @Test public void ungeneratedReturnChunkFailsBeforeMountPublication() throws Exception {
        Fixture f=new Fixture();f.chunk.isTerrainPopulated=false;
        f.mount.forceSpawn=true;
        assertEquals(KOMEJoinBattlePhysicalAccess.RiderSyncStage.RETURN_CHUNK_DELIVERY_FAILED,
            KOMEJoinBattlePhysicalAccess.publishPreparedMountToPlayer(f.access.player,f.mount,
                f.world,f.chunk,KOMEJoinBattlePhysicalAccess.MountPublicationMode.ORDERED_EGRESS));
        assertTrue(f.access.player.vanillaPackets.isEmpty());
        assertTrue(f.world.tracker.getTrackingPlayers(f.mount).isEmpty());
        assertNull(f.access.player.ridingEntity);
        assertFalse(f.mount.forceSpawn);
    }

    private static int index(List<Packet> packets,Class<?> type){
        for(int i=0;i<packets.size();i++)if(type.isInstance(packets.get(i)))return i;
        return -1;
    }
    private static void set(Class<?> type,Object object,String name,Object value) throws Exception{
        Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);
    }
    private static final class Fixture {
        final KOMEAccessFixture access=new KOMEAccessFixture();
        final ReturnWorld world=KOMEAccessFixture.allocate(ReturnWorld.class);
        final EntityHorse mount=new EntityHorse(access.world);
        final Chunk chunk;
        Fixture() throws Exception {
            set(World.class,world,"provider",new WorldProviderSurface());
            world.manager=KOMEAccessFixture.allocate(ReturnManager.class);
            world.tracker=KOMEAccessFixture.allocate(EntityTracker.class);
            access.player.worldObj=world;
            set(EntityPlayerMP.class,access.player,"loadedChunks",new LinkedList());
            set(Entity.class,access.player,"boundingBox",
                net.minecraft.util.AxisAlignedBB.getBoundingBox(0,0,0,0,0,0));
            access.player.setPosition(8D,65D,8D);
            mount.worldObj=world;mount.setPosition(8D,65D,8D);mount.setEntityId(88);
            chunk=new Chunk(world,0,0);chunk.isTerrainPopulated=true;
            EntityTrackerEntry entry=new EntityTrackerEntry(mount,64,3,true);
            set(EntityTracker.class,world.tracker,"trackedEntities",
                new HashSet<EntityTrackerEntry>(Collections.singleton(entry)));
            IntHashMap ids=new IntHashMap();ids.addKey(mount.getEntityId(),entry);
            set(EntityTracker.class,world.tracker,"trackedEntityIDs",ids);
            world.chunk=chunk;
        }
        void nativeManager() throws Exception {
            PlayerManager manager=KOMEAccessFixture.allocate(PlayerManager.class);
            set(PlayerManager.class,manager,"theWorldServer",world);
            for(String name:new String[]{"players","chunkWatcherWithPlayers","playerInstanceList"})
                set(PlayerManager.class,manager,name,new ArrayList());
            set(PlayerManager.class,manager,"playerInstances",new net.minecraft.util.LongHashMap());
            set(PlayerManager.class,manager,"playerViewRadius",1);
            set(PlayerManager.class,manager,"xzDirectionsConst",new int[][]{{1,0},{0,1},{-1,0},{0,-1}});
            world.manager=manager;
            world.theChunkProviderServer=new net.minecraft.world.gen.ChunkProviderServer(world,null,null){
                @Override public Chunk loadChunk(int x,int z,Runnable callback){
                    Chunk result=world.getChunkFromChunkCoords(x,z);
                    if(callback!=null)callback.run();return result;
                }
                @Override public boolean chunkExists(int x,int z){return true;}
            };
        }
    }
    public static class ReturnWorld extends WorldServer {
        PlayerManager manager;EntityTracker tracker;Chunk chunk;
        private ReturnWorld(){super(null,null,"unused",0,null,null);}
        @Override public PlayerManager getPlayerManager(){return manager;}
        @Override public EntityTracker getEntityTracker(){return tracker;}
        @Override public long getTotalWorldTime(){return 0L;}
        @Override public boolean blockExists(int x,int y,int z){return y>=0&&y<256;}
        @Override public net.minecraft.world.chunk.IChunkProvider getChunkProvider(){return theChunkProviderServer;}
        @Override public Chunk getChunkFromChunkCoords(int x,int z){
            if(x==0&&z==0)return chunk;
            Chunk other=new Chunk(this,x,z);other.isTerrainPopulated=true;return other;
        }
        @Override public List func_147486_a(int a,int b,int c,int d,int e,int f){return Collections.emptyList();}
    }
    public static class ReturnManager extends PlayerManager {
        private ReturnManager(){super(null);}
        @Override public boolean isPlayerWatchingChunk(EntityPlayerMP player,int x,int z){return false;}
    }
}
