package com.enovak.lotrmoremobs.siege.creation;

import com.enovak.lotrmoremobs.siege.network.SiegeRequestLifecycle;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.ChunkPosition;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ExplosionEvent;

public class GateCreationEventHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            GateCreationManager.processQueuedRequests();
        } else if (event.phase == TickEvent.Phase.END) {
            GateCreationManager.processSelectionInvalidations();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled()) {
            queueSelectionRevalidation(event.world, event.x, event.y, event.z);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockEvent.PlaceEvent event) {
        if (!event.isCanceled()
                && !(event instanceof BlockEvent.MultiPlaceEvent)) {
            queueSelectionRevalidation(event.world, event.x, event.y, event.z);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockMultiPlace(BlockEvent.MultiPlaceEvent event) {
        if (event.isCanceled()) {
            return;
        }
        for (BlockSnapshot snapshot : event.getReplacedBlockSnapshots()) {
            queueSelectionRevalidation(
                    snapshot.world,
                    snapshot.x,
                    snapshot.y,
                    snapshot.z
            );
        }
    }

    @SubscribeEvent
    public void onExplosionDetonate(ExplosionEvent.Detonate event) {
        for (ChunkPosition position : event.getAffectedBlocks()) {
            queueSelectionRevalidation(
                    event.world,
                    position.chunkPosX,
                    position.chunkPosY,
                    position.chunkPosZ
            );
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END
                && event.player instanceof EntityPlayerMP
                && event.player.ticksExisted % 20 == 0) {
            GateCreationManager.validatePlayerSession(
                    (EntityPlayerMP)event.player
            );
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            GateCreationManager.cancelForPlayer(
                    (EntityPlayerMP)event.player,
                    false
            );
            SiegeRequestLifecycle.clearPlayer(
                    (EntityPlayerMP)event.player
            );
        }
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(
            PlayerEvent.PlayerChangedDimensionEvent event
    ) {
        if (event.player instanceof EntityPlayerMP) {
            GateCreationManager.cancelForPlayer(
                    (EntityPlayerMP)event.player
            );
            SiegeRequestLifecycle.clearPlayer(
                    (EntityPlayerMP)event.player
            );
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            GateCreationManager.cancelForPlayer(
                    (EntityPlayerMP)event.player
            );
            SiegeRequestLifecycle.clearPlayer(
                    (EntityPlayerMP)event.player
            );
        }
    }

    private static void queueSelectionRevalidation(
            net.minecraft.world.World world,
            int x,
            int y,
            int z
    ) {
        GateCreationManager.queueSelectionRevalidation(world, x, y, z);
    }
}
