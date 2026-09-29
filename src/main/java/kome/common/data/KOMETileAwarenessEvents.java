package kome.common.data;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

/** Both legacy event buses; discovery uses existing entity events, never world/chunk scans. */
public final class KOMETileAwarenessEvents {
    private final KOMEServerTileAwareness awareness;
    public KOMETileAwarenessEvents() { this(KOMEServerTileAwareness.INSTANCE); }
    KOMETileAwarenessEvents(KOMEServerTileAwareness awareness) { this.awareness = awareness; }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onJoin(EntityJoinWorldEvent event) {
        if (!event.isCanceled() && !event.world.isRemote)
            awareness.consider(event.entity, KOMEServerTileAwareness.Cause.FIRST_OBSERVATION);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onLivingUpdate(LivingEvent.LivingUpdateEvent event) {
        // Also discovers a hire of an already loaded NPC; KOME's normal hire/denial handler runs first.
        if (!(event.entityLiving instanceof EntityPlayerMP))
            awareness.consider(event.entityLiving, KOMEServerTileAwareness.Cause.FIRST_OBSERVATION);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        awareness.consider(event.player, KOMEServerTileAwareness.Cause.LOGIN);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP)
            awareness.remove(event.player, KOMEServerTileAwareness.Cause.DISCONNECTED);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) awareness.respawn(event.player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP)
            awareness.consider(event.player, KOMEServerTileAwareness.Cause.FIRST_OBSERVATION);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) awareness.sampleTick();
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.world.isRemote) return;
        // Only the chunk already supplied by Forge. It has queued these entities for removal.
        for (java.util.List section : event.getChunk().entityLists)
            for (Object value : section)
                if (value instanceof Entity)
                    awareness.remove((Entity) value, KOMEServerTileAwareness.Cause.CHUNK_UNLOADED);
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) awareness.unloadWorld(event.world);
    }
}
