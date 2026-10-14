package kome.client;

import cpw.mods.fml.common.network.FMLNetworkEvent;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import kome.client.gui.KOMEGuiProgression;
import kome.common.KOMEAccessFixture;
import kome.common.data.*;
import kome.common.network.KOMEPacketProgressionData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.WorldProviderSurface;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual proxy intake/reset/queue publication with inert client objects; no GL or Minecraft launch. */
public class KOMEProgressionCleanupClientTest {
    @Test public void bookAndPermissionCacheShareAcceptedSnapshotsAcrossReconnectAndDimensionFences() throws Exception {
        Field singleton=Minecraft.class.getDeclaredField("theMinecraft"); singleton.setAccessible(true);
        Object previousMinecraft=singleton.get(null);
        Field[] proxyFields={com.enovak.lotrmoremobs.Main.class.getField("proxy"),
            com.fuzs.aquaacrobatics.AquaAcrobatics.class.getField("proxy")};
        Object[] previousProxies={proxyFields[0].get(null),proxyFields[1].get(null)};
        try {
            Minecraft mc=KOMEAccessFixture.allocate(Minecraft.class); singleton.set(null,mc);
            WorldClient world=KOMEAccessFixture.allocate(WorldClient.class); world.isRemote=true;
            Field provider=net.minecraft.world.World.class.getDeclaredField("provider");provider.setAccessible(true);
            provider.set(world,new WorldProviderSurface()); mc.theWorld=world;
            EntityClientPlayerMP player=KOMEAccessFixture.allocate(EntityClientPlayerMP.class);
            UUID id=UUID.randomUUID(); Field identity=net.minecraft.entity.Entity.class.getDeclaredField("entityUniqueID");
            identity.setAccessible(true);identity.set(player,id);player.worldObj=world;mc.thePlayer=player;
            KOMEClientData.INSTANCE.resetClientState();KOMEGuiProgression.resetData();
            KOMEClientProxy proxy=new KOMEClientProxy();
            Field tasks=KOMEClientProxy.class.getDeclaredField("clientTasks");tasks.setAccessible(true);
            KOMEClientTaskQueue queue=(KOMEClientTaskQueue)tasks.get(proxy);
            FMLNetworkEvent.ClientConnectedToServerEvent first=KOMEAccessFixture.clientConnected();
            proxy.onClientConnect(first);
            proxy.acceptProgressionData(packet(id,"knight",20L,0),first.handler);
            assertTrue(KOMEClientData.INSTANCE.progressions.isEmpty());queue.drain();
            assertRank(id,"knight");assertTrue(KOMEProgressionPermissions.has(player,KOMEProgressionPermissions.FAST_TRAVEL));
            proxy.acceptProgressionData(packet(id,"wanderer",19L,0),first.handler);queue.drain();assertRank(id,"knight");
            proxy.acceptProgressionData(packet(id,"lord",21L,0),first.handler);
            proxy.onClientDisconnect(KOMEAccessFixture.clientDisconnected());queue.drain();
            assertTrue(KOMEClientData.INSTANCE.progressions.isEmpty());assertEquals("",book().currentRank);
            proxy.acceptProgressionData(packet(id,"prince",100L,0),first.handler);assertEquals(0,queue.pendingTasks());
            FMLNetworkEvent.ClientConnectedToServerEvent second=KOMEAccessFixture.clientConnected();
            proxy.onClientConnect(second);
            proxy.acceptProgressionData(packet(id,"serf",1L,0),second.handler);queue.drain();assertRank(id,"serf");
            proxy.acceptProgressionData(packet(id,"lord",22L,0),first.handler);assertEquals(0,queue.pendingTasks());
            proxy.acceptProgressionData(packet(id,"lord",2L,0),second.handler);
            proxy.onClientWorldUnload(new net.minecraftforge.event.world.WorldEvent.Unload(world));
            player.dimension=100; world.provider.dimensionId=100;queue.drain();
            assertTrue(KOMEClientData.INSTANCE.progressions.isEmpty());
            proxy.acceptProgressionData(packet(id,"lord",3L,0),second.handler);queue.drain();
            assertTrue(KOMEClientData.INSTANCE.progressions.isEmpty());
            proxy.acceptProgressionData(packet(id,"lord",4L,100),second.handler);queue.drain();assertRank(id,"lord");
        } finally {
            singleton.set(null,previousMinecraft);
            for(int i=0;i<proxyFields.length;i++)proxyFields[i].set(null,previousProxies[i]);
            KOMEClientData.INSTANCE.resetClientState();KOMEGuiProgression.resetData();KOMEFactionTitleClientBridge.reset();
        }
    }
    private static KOMEPacketProgressionData packet(UUID id,String rank,long revision,int dimension) {
        KOMEProgressionRankSummary summary=new KOMEProgressionRankSummary("rohan",rank,"","",
            Collections.emptyList(),"","","");
        return new KOMEPacketProgressionData("Player",Arrays.asList("baseline.fast_travel"),Collections.emptyMap(),
            "summary","","","","",summary).authority(id,rank,revision,dimension,true);
    }
    private static void assertRank(UUID id,String rank) throws Exception {
        assertEquals(rank,KOMEClientData.INSTANCE.progressions.get(id).getCanonicalRank().key);
        assertEquals(rank,book().currentRank);
    }
    private static KOMEProgressionRankSummary book() throws Exception {
        Field rank=KOMEGuiProgression.class.getDeclaredField("rankSummary");rank.setAccessible(true);
        return (KOMEProgressionRankSummary)rank.get(null);
    }
}
