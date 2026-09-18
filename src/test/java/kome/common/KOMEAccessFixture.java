package kome.common;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kome.common.data.KOMEWorldData;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;

/** Inert world/player fixtures: actual command, handler and service code, no live Forge connection. */
public final class KOMEAccessFixture {
    public final KOMEWorldData data = new KOMEWorldData();
    public final TestWorld world;
    public final Player player;
    public final RecordingNetwork network;
    public final MessageContext context;

    public KOMEAccessFixture() throws Exception {
        world = allocate(TestWorld.class);
        set(World.class, world, "provider", new WorldProviderSurface());
        world.mapStorage = new MapStorage(null);
        world.mapStorage.setData("KOME_ServerRules", data);
        world.loadedEntityList = new ArrayList<Entity>();
        world.playerEntities = new ArrayList();
        set(World.class, world, "worldScoreboard", new net.minecraft.scoreboard.Scoreboard());
        player = allocate(Player.class);
        player.id = UUID.randomUUID();
        set(Entity.class, player, "entityUniqueID", player.id);
        player.messages = new ArrayList<String>();
        player.worldObj = world;
        world.playerEntities.add(player);
        network = allocate(RecordingNetwork.class);
        network.messages = new ArrayList<IMessage>();
        NetHandlerPlayServer handler = allocate(NetHandlerPlayServer.class);
        handler.playerEntity = player;
        Constructor<MessageContext> constructor = MessageContext.class.getDeclaredConstructor(INetHandler.class, Side.class);
        constructor.setAccessible(true);
        context = constructor.newInstance(handler, Side.SERVER);
    }

    public void pledge(LOTRFaction faction) throws Exception {
        Object lotrData = LOTRLevelData.getData(player);
        set(lotrData.getClass(), lotrData, "pledgeFaction", faction);
    }

    public static <T> T allocate(Class<T> type) throws Exception {
        Class<?> unsafe = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafe.getDeclaredField("theUnsafe");
        singleton.setAccessible(true);
        return type.cast(unsafe.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type));
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    public static final class Player extends EntityPlayerMP {
        public UUID id;
        public String name;
        public boolean operator;
        public List<String> messages;
        private Player() { super(null, null, null, null); }
        @Override public UUID getUniqueID() { return id; }
        @Override public String getCommandSenderName() { return name == null ? "AccessTester" : name; }
        // Deliberately deny level 0 too, matching real 1.7.10 non-operator behavior.
        @Override public boolean canCommandSenderUseCommand(int level, String command) { return operator; }
        @Override public void addChatMessage(IChatComponent message) { messages.add(message.getUnformattedText()); }
    }

    public static final class RecordingNetwork extends SimpleNetworkWrapper {
        public List<IMessage> messages;
        private RecordingNetwork() { super("unused"); }
        @Override public void sendTo(IMessage message, EntityPlayerMP recipient) { messages.add(message); }
    }

    public static final class TestWorld extends World {
        private TestWorld() { super((ISaveHandler) null, "test", (WorldProvider) null, (WorldSettings) null, (Profiler) null); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) { return null; }
        @Override public long getTotalWorldTime() { return 0L; }
        @Override public EntityPlayer func_152378_a(UUID id) {
            for (Object value : playerEntities) if (((EntityPlayer) value).getUniqueID().equals(id)) return (EntityPlayer) value;
            return null;
        }
    }
}
