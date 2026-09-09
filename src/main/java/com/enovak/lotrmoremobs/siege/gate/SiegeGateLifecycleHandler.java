package com.enovak.lotrmoremobs.siege.gate;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.siege.SiegeRegistry;
import com.enovak.lotrmoremobs.siege.banner.SiegeGateBannerAttachmentData;
import com.enovak.lotrmoremobs.siege.creation.GateCreationManager;
import com.enovak.lotrmoremobs.siege.repair.GateManagementManager;
import com.enovak.lotrmoremobs.siege.tile.TileEntitySiegeGate;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

/** Server transaction reconciliation and player-removal protection. */
public final class SiegeGateLifecycleHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.world == null || event.world.isRemote) {
            return;
        }
        if (event.block == SiegeRegistry.gatePart) {
            EntityPlayer player = event.getPlayer();

            /*
             * Finalized GateParts are normally protected from direct player
             * removal, but Creative mode intentionally supports surgical
             * structure editing. Do not cancel the Forge break event here;
             * BlockSiegeGatePart.removedByPlayer(...) owns the authoritative
             * server-side transaction and will reject unsafe/non-closed edits.
             */
            if (player != null
                    && player.capabilities.isCreativeMode) {
                return;
            }

            event.setCanceled(true);
            sendMessage(
                    player,
                    "GateParts can only be removed through their controller."
            );
            return;
        }
        if (event.block != SiegeRegistry.gateController) {
            return;
        }

        TileEntity tileEntity = event.world.getTileEntity(
                event.x,
                event.y,
                event.z
        );
        if (!(tileEntity instanceof TileEntitySiegeGate)) {
            if (GateRegistry.hasDurableController(
                    event.world,
                    event.x,
                    event.y,
                    event.z
            )) {
                event.setCanceled(true);
                sendMessage(
                        event.getPlayer(),
                        "This controller has durable gate ownership but no "
                                + "valid controller data; manual recovery is required."
                );
            }
            return;
        }

        TileEntitySiegeGate gate = (TileEntitySiegeGate)tileEntity;
        boolean protectedGate = gate.isFinalized()
                || gate.isGateStructureQuarantined()
                || GateRegistry.hasDurableController(
                        event.world,
                        event.x,
                        event.y,
                        event.z
                );
        if (!protectedGate) {
            return;
        }
        EntityPlayer player = event.getPlayer();
        if (gate.isGateStructureQuarantined()) {
            event.setCanceled(true);
            sendMessage(
                    player,
                    "This quarantined controller requires manual recovery "
                            + "and cannot be dismantled automatically."
            );
            return;
        }
        if (!(player instanceof EntityPlayerMP)
                || !gate.canDismantle((EntityPlayerMP)player)) {
            event.setCanceled(true);
            sendMessage(
                    player,
                    "Only the gate owner or an administrator may dismantle "
                            + gate.getGateName() + "."
            );
            return;
        }
        if (!gate.canPrepareControllerRemovalTransaction()) {
            event.setCanceled(true);
            sendMessage(
                    player,
                    "The durable gate-removal journal is unavailable or full; "
                            + "the controller was preserved."
            );
        }
    }

    /**
     * Siege Gate interaction is authoritative for Siege Gate blocks.
     *
     * LOTR banner protection and other generic area-protection handlers may
     * cancel PlayerInteractEvent before vanilla reaches Block#onBlockActivated.
     * Gate operation must not inherit those external permissions: the gate's
     * own canOperate/canManage rules already decide what each player may do.
     *
     * Run at LOWEST and receive canceled events so the gate can consume its own
     * interaction after generic protection handlers have had their turn.
     */
    @SubscribeEvent(
            priority = EventPriority.LOWEST,
            receiveCanceled = true
    )
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.world == null
                || event.world.isRemote
                || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                || !MumakilConfig.enableSiegeGates) {
            return;
        }

        EntityPlayer player = event.entityPlayer;
        if (!(player instanceof EntityPlayerMP)) {
            return;
        }

        boolean clickedController =
                event.world.getBlock(
                        event.x,
                        event.y,
                        event.z
                ) == SiegeRegistry.gateController;

        boolean clickedPart =
                event.world.getBlock(
                        event.x,
                        event.y,
                        event.z
                ) == SiegeRegistry.gatePart;

        if (!clickedController
                && !clickedPart) {
            return;
        }

        TileEntitySiegeGate controller = null;

        if (clickedController) {
            TileEntity tileEntity =
                    event.world.getTileEntity(
                            event.x,
                            event.y,
                            event.z
                    );

            if (tileEntity instanceof TileEntitySiegeGate) {
                controller =
                        (TileEntitySiegeGate)tileEntity;
            }

        } else {
            controller =
                    GateRegistry.getController(
                            event.world,
                            event.x,
                            event.y,
                            event.z
                    );
        }

        if (controller == null) {
            return;
        }

        /*
         * Preserve the controller's existing sneak-placement behavior.
         * Placing a held block against the controller is ordinary world
         * placement, not a gate command, so normal LOTR banner protection may
         * still govern that placement.
         *
         * GateParts intentionally keep their existing behavior: sneaking on a
         * part opens Gate Management rather than placing against the part.
         */
        ItemStack held =
                player.getCurrentEquippedItem();

        if (clickedController
                && player.isSneaking()
                && held != null
                && held.getItem() instanceof ItemBlock) {

            return;
        }

        /*
         * Consume the gate command even if another handler already canceled
         * this event. We invoke the gate's authoritative server path directly,
         * so external banner protection cannot grant or deny gate access.
         */
        event.setCanceled(true);

        EntityPlayerMP serverPlayer =
                (EntityPlayerMP)player;

        if (clickedPart
                && !player.isSneaking()) {

            controller.tryToggleOpenState(
                    serverPlayer
            );

            return;
        }

        if (controller.isFinalized()) {
            GateManagementManager.open(
                    serverPlayer,
                    controller
            );

        } else {
            GateCreationManager.openControls(
                    serverPlayer,
                    controller
            );
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        World world = event.world;
        if (world == null) {
            return;
        }
        GateRegistry.notifyPartChunkAvailabilityChanged(
                world,
                event.getChunk().xPosition,
                event.getChunk().zPosition
        );
        if (!world.isRemote) {
            SiegeGateOwnershipData data =
                    SiegeGateOwnershipData.get(world, false);
            if (data != null) {
                data.onChunkLoaded(
                        event.getChunk().xPosition,
                        event.getChunk().zPosition
                );
            }
        }
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        GateRegistry.notifyPartChunkAvailabilityChanged(
                event.world,
                event.getChunk().xPosition,
                event.getChunk().zPosition
        );
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        if (event.world != null && !event.world.isRemote) {
            SiegeGateOwnershipData.get(event.world, false);
            SiegeGateBannerAttachmentData.get(event.world, false);
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        SiegeGateOwnershipData data = event.world == null
                || event.world.isRemote
                ? null
                : SiegeGateOwnershipData.get(event.world, false);
        if (data != null) {
            data.clearTransientQueue();
        }
        SiegeGateBreachRallyManager.clearWorld(event.world);
        GateRegistry.clearWorld(event.world);
    }

    @SubscribeEvent
    public void onWorldTick(TickEvent.WorldTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || event.world == null
                || event.world.isRemote) {
            return;
        }
        SiegeGateOwnershipData data =
                SiegeGateOwnershipData.get(event.world, false);
        if (data != null) {
            data.processReconciliation(event.world);
            data.processEditCommitReconciliation(event.world);
        }

        /*
         * Banner restoration must run after the ordinary gate journal so a
         * native entity never respawns before its support block is back.
         */
        SiegeGateBannerAttachmentData.process(event.world);

        /*
         * Breach rally movement is intentionally transient. It only reinforces
         * a short navigator destination while an enrolled NPC is still idle;
         * normal LOTR combat targeting immediately takes priority.
         */
        SiegeGateBreachRallyManager.process(event.world);
    }

    private static void sendMessage(EntityPlayer player, String message) {
        if (player != null && message != null) {
            player.addChatMessage(new ChatComponentText(message));
        }
    }
}
