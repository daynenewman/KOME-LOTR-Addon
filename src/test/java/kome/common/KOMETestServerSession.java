package kome.common;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import kome.common.network.KOMEPacketHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.dedicated.DedicatedPlayerList;
import net.minecraft.server.management.ServerConfigurationManager;
import net.minecraft.world.WorldServer;

/** Real server/handler registration surfaces, inert transport, restored after each queue integration test. */
public final class KOMETestServerSession implements AutoCloseable {
    public final MinecraftServer server;
    public final List players = new ArrayList();
    private final Field singleton;
    private final Object previous;
    public KOMETestServerSession(KOMEAccessFixture... fixtures) throws Exception {
        Field found = null;
        for (Field field : MinecraftServer.class.getDeclaredFields())
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == MinecraftServer.class) found = field;
        if (found == null) throw new AssertionError("Missing server singleton");
        singleton = found; singleton.setAccessible(true); previous = singleton.get(null);
        server = KOMEAccessFixture.allocate(DedicatedServer.class);
        ServerConfigurationManager manager = KOMEAccessFixture.allocate(DedicatedPlayerList.class);
        Field list = ServerConfigurationManager.class.getDeclaredField("playerEntityList");
        list.setAccessible(true); list.set(manager, players);
        for (Field field : MinecraftServer.class.getDeclaredFields())
            if (field.getType() == ServerConfigurationManager.class) { field.setAccessible(true); field.set(server, manager); }
        for (KOMEAccessFixture fixture : fixtures) players.add(fixture.player);
        WorldServer world = KOMEAccessFixture.allocate(WorldServer.class);
        world.playerEntities = players; server.worldServers = new WorldServer[]{world};
        singleton.set(null, server); KOMEPacketHandler.startServerSession(server);
    }
    @Override public void close() throws Exception { KOMEPacketHandler.clearPendingServerTasks(); singleton.set(null, previous); }
}
