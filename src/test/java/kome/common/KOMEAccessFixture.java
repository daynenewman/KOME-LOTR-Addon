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
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import io.netty.util.concurrent.GenericFutureListener;
import io.netty.util.concurrent.ImmediateEventExecutor;
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
    static {
        try { initializeVanillaItems(); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    /** Plain JUnit skips Minecraft bootstrap. Install immutable item identities before live service calls. */
    private static void initializeVanillaItems() throws Exception {
        java.lang.reflect.Method raw=net.minecraft.item.Item.itemRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);
        raw.setAccessible(true);int id=31000;
        Field modifiers=Field.class.getDeclaredField("modifiers");modifiers.setAccessible(true);
        for(String key:new String[]{"grass","planks","stonebrick"}){
            Field field=net.minecraft.init.Blocks.class.getField(key);if(field.get(null)!=null)continue;
            net.minecraft.block.Block block;
            if(key.equals("grass")){java.lang.reflect.Constructor<?> c=net.minecraft.block.BlockGrass.class.getDeclaredConstructor();c.setAccessible(true);block=(net.minecraft.block.Block)c.newInstance();}
            else block=new net.minecraft.block.Block(net.minecraft.block.material.Material.wood){};
            java.lang.reflect.Method blocksRaw=net.minecraft.block.Block.blockRegistry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);
            blocksRaw.setAccessible(true);blocksRaw.invoke(net.minecraft.block.Block.blockRegistry,3000+key.length(),"fixture:"+key,block);
            field.setAccessible(true);modifiers.setInt(field,field.getModifiers()&~java.lang.reflect.Modifier.FINAL);field.set(null,block);
        }
        for(Field field:net.minecraft.init.Items.class.getFields())if((field.getType()==net.minecraft.item.Item.class||field.getType()==net.minecraft.item.ItemArmor.class||field.getType()==net.minecraft.item.ItemBow.class||field.getType()==net.minecraft.item.ItemPotion.class)&&field.get(null)==null) {
            net.minecraft.item.Item item=field.getType()==net.minecraft.item.ItemArmor.class?new net.minecraft.item.ItemArmor(net.minecraft.item.ItemArmor.ArmorMaterial.CLOTH,0,field.getName().contains("helmet")?0:1):field.getType()==net.minecraft.item.ItemBow.class?new net.minecraft.item.ItemBow():field.getType()==net.minecraft.item.ItemPotion.class?new net.minecraft.item.ItemPotion():new net.minecraft.item.ItemFood(8,0.8F,false);
            if(field.getName().equals("glass_bottle"))item=new net.minecraft.item.Item(){
                @Override public String getItemStackDisplayName(net.minecraft.item.ItemStack stack){return "Bottle";}
            };
            if(field.getName().matches(".*sword|bow|.*helmet|.*chestplate|written_book|mushroom_stew"))item.setMaxStackSize(1);
            raw.invoke(net.minecraft.item.Item.itemRegistry,id++,"kome:fixture_vanilla_"+field.getName(),item);
            field.setAccessible(true);modifiers.setInt(field,field.getModifiers()&~java.lang.reflect.Modifier.FINAL);field.set(null,item);
        }
    }
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
        world.playedSounds = new ArrayList<String>();
        world.structureBlocks=new java.util.HashMap<String,net.minecraft.block.Block>();
        set(World.class, world, "worldScoreboard", new net.minecraft.scoreboard.Scoreboard());
        player = allocate(Player.class);
        player.id = UUID.randomUUID(); player.connected = true;
        set(Entity.class, player, "entityUniqueID", player.id);
        player.messages = new ArrayList<String>();
        player.worldObj = world;
        net.minecraft.entity.DataWatcher taskWatcher=new net.minecraft.entity.DataWatcher(player);taskWatcher.addObject(0,(byte)0);set(Entity.class,player,"dataWatcher",taskWatcher);
        world.playerEntities.add(player);
        network = allocate(RecordingNetwork.class);
        network.messages = new ArrayList<IMessage>();
        NetHandlerPlayServer handler = allocate(NetHandlerPlayServer.class);
        handler.playerEntity = player;
        RecordingManager manager = new RecordingManager(); manager.recipient = player;
        set(NetHandlerPlayServer.class, handler, "netManager", manager);
        player.playerNetServerHandler = handler;
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

    /** Real FML lifecycle event with an inert native handler; no socket or live client. */
    public static cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent clientConnected() throws Exception {
        NetworkManager manager = new NetworkManager(false);
        manager.setNetHandler(allocate(net.minecraft.client.network.NetHandlerPlayClient.class));
        return new cpw.mods.fml.common.network.FMLNetworkEvent.ClientConnectedToServerEvent(manager, "MODDED");
    }

    public static cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent clientDisconnected() throws Exception {
        NetworkManager manager = new NetworkManager(false);
        manager.setNetHandler(allocate(net.minecraft.client.network.NetHandlerPlayClient.class));
        return new cpw.mods.fml.common.network.FMLNetworkEvent.ClientDisconnectionFromServerEvent(manager);
    }

    private static void set(Class<?> type, Object target, String name, Object value) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    public static final class Player extends EntityPlayerMP {
        public UUID id;
        public String name;
        public boolean operator;
        public boolean connected = true;
        public List<String> messages;
        public net.minecraft.inventory.IInventory openedInventory;
        private Player() { super(null, null, null, null); }
        @Override public UUID getUniqueID() { return id; }
        @Override public String getCommandSenderName() { return name == null ? "AccessTester" : name; }
        @Override public boolean isEntityAlive(){return !isDead;}
        // Deliberately deny level 0 too, matching real 1.7.10 non-operator behavior.
        @Override public boolean canCommandSenderUseCommand(int level, String command) { return operator; }
        @Override public void addChatMessage(IChatComponent message) { messages.add(message.getUnformattedText()); }
        @Override public void displayGUIChest(net.minecraft.inventory.IInventory inventory){openedInventory=inventory;}
    }

    public static final class RecordingNetwork extends SimpleNetworkWrapper {
        public List<IMessage> messages;
        private RecordingNetwork() { super("unused"); }
        @Override public void sendTo(IMessage message, EntityPlayerMP recipient) { messages.add(message); }
        @Override public Packet getPacketFrom(IMessage message) { return new RecordedPacket(message); }
    }

    /** Inert analogue of getPacketFrom + NetworkManager, preserving recipient-aware test recording. */
    public static final class RecordedPacket extends Packet {
        final IMessage message;
        public RecordedPacket(IMessage message) { this.message = message; }
        @Override public void readPacketData(PacketBuffer buffer) { throw new UnsupportedOperationException(); }
        @Override public void writePacketData(PacketBuffer buffer) { throw new UnsupportedOperationException(); }
        @Override public void processPacket(INetHandler handler) { throw new UnsupportedOperationException(); }
    }
    private static final class RecordingManager extends NetworkManager {
        EntityPlayerMP recipient;
        RecordingManager() { super(false); }
        @Override public boolean isChannelOpen() { return ((Player)recipient).connected; }
        @Override public void scheduleOutboundPacket(Packet packet, GenericFutureListener... listeners) {
            kome.common.network.KOMEPacketHandler.network.sendTo(((RecordedPacket) packet).message, recipient);
            for (GenericFutureListener listener : listeners) {
                try { listener.operationComplete(ImmediateEventExecutor.INSTANCE.newSucceededFuture(null)); }
                catch (Exception error) { throw new AssertionError(error); }
            }
        }
    }

    public static final class TestWorld extends World {
        private static final net.minecraft.block.Block TEST_GROUND=new net.minecraft.block.Block(net.minecraft.block.material.Material.ground){};
        private static final net.minecraft.block.Block TEST_AIR=new net.minecraft.block.Block(net.minecraft.block.material.Material.air){@Override public net.minecraft.util.AxisAlignedBB getCollisionBoundingBoxFromPool(World world,int x,int y,int z){return null;}};
        public long testWorldTime;
        public boolean flatTerrain,spawnSucceeds,unsafeSurface;
        public java.util.Map<String,net.minecraft.block.Block> structureBlocks;
        public void shelter(int x,int z){
            for(int dx=0;dx<=1;dx++)for(int dz=0;dz<=1;dz++)structureBlocks.put((x+dx)+",68,"+(z+dz),net.minecraft.init.Blocks.planks);
            structureBlocks.put(x+",66,"+z,net.minecraft.init.Blocks.planks);structureBlocks.put(x+",67,"+z,net.minecraft.init.Blocks.planks);
        }
        public int terrainProbes;
        public IChunkProvider testChunkProvider;
        public List<String> playedSounds;
        private TestWorld() { super((ISaveHandler) null, "test", (WorldProvider) null, (WorldSettings) null, (Profiler) null); }
        @Override protected IChunkProvider createChunkProvider() { return null; }
        @Override public IChunkProvider getChunkProvider(){return testChunkProvider==null?super.getChunkProvider():testChunkProvider;}
        @Override public int getTopSolidOrLiquidBlock(int x,int z){terrainProbes++;return flatTerrain?65:super.getTopSolidOrLiquidBlock(x,z);}
        @Override public net.minecraft.block.Block getBlock(int x,int y,int z){if(flatTerrain&&structureBlocks!=null&&structureBlocks.containsKey(x+","+y+","+z))return structureBlocks.get(x+","+y+","+z);return flatTerrain?(y==64&&!unsafeSurface?net.minecraft.init.Blocks.grass:TEST_AIR):super.getBlock(x,y,z);}
        @Override public boolean isAirBlock(int x,int y,int z){return flatTerrain?getBlock(x,y,z).getMaterial()==net.minecraft.block.material.Material.air:super.isAirBlock(x,y,z);}
        @Override public net.minecraft.tileentity.TileEntity getTileEntity(int x,int y,int z){return flatTerrain?null:super.getTileEntity(x,y,z);}
        @Override public java.util.List getCollidingBoundingBoxes(Entity entity,net.minecraft.util.AxisAlignedBB box){return flatTerrain?new ArrayList():super.getCollidingBoundingBoxes(entity,box);}
        @Override public java.util.List func_147461_a(net.minecraft.util.AxisAlignedBB box){return flatTerrain?new ArrayList():super.func_147461_a(box);}
        @Override public boolean checkNoEntityCollision(net.minecraft.util.AxisAlignedBB box,Entity entity){return flatTerrain||super.checkNoEntityCollision(box,entity);}
        @Override public boolean spawnEntityInWorld(Entity entity){if(!flatTerrain)return super.spawnEntityInWorld(entity);if(spawnSucceeds)loadedEntityList.add(entity);return spawnSucceeds;}
        @Override public void playSoundAtEntity(Entity entity,String sound,float volume,float pitch){if(playedSounds!=null)playedSounds.add(sound);}
        @Override protected int func_152379_p() { return 0; }
        @Override public Entity getEntityByID(int id) {
            for(Object value:loadedEntityList)if(((Entity)value).getEntityId()==id)return (Entity)value;
            return null;
        }
        @Override public java.util.List getEntitiesWithinAABB(Class type,net.minecraft.util.AxisAlignedBB box) {
            java.util.List result=new ArrayList();
            for(Object value:loadedEntityList)if(type.isInstance(value)) {
                Entity entity=(Entity)value;
                if(entity.posX>=box.minX&&entity.posX<=box.maxX&&entity.posY>=box.minY
                        &&entity.posY<=box.maxY&&entity.posZ>=box.minZ&&entity.posZ<=box.maxZ)result.add(entity);
            }
            return result;
        }
        @Override public long getTotalWorldTime() { return testWorldTime; }
        @Override public EntityPlayer func_152378_a(UUID id) {
            for (Object value : playerEntities) if (((EntityPlayer) value).getUniqueID().equals(id)) return (EntityPlayer) value;
            return null;
        }
    }
}
