package com.fuzs.aquaacrobatics.network.message;

import com.enovak.lotrmoremobs.config.PlayerMovementMode;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;

import com.fuzs.aquaacrobatics.entity.player.IPlayerResizeable;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;
import cpw.mods.fml.common.gameevent.TickEvent.ServerTickEvent;
import io.netty.buffer.ByteBuf;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.fuzs.aquaacrobatics.config.ConfigHandler.MovementConfig.effectsWhileCrawling;

public class PacketSendKey implements IMessage {

    private static final Queue<PendingKeybind> SERVER_TASKS = new ConcurrentLinkedQueue<PendingKeybind>();

    public enum KeybindPacket {
        UNKNOWN,
        TOGGLE_CRAWLING
    }

    private KeybindPacket keybind = KeybindPacket.UNKNOWN;

    @Override
    public void fromBytes(ByteBuf buf) {
        int idx = buf.readInt();
        if (idx >= KeybindPacket.values().length) keybind = KeybindPacket.UNKNOWN;
        else keybind = KeybindPacket.values()[idx];
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(keybind.ordinal());
    }

    public PacketSendKey() {

    }

    public PacketSendKey(KeybindPacket keybind) {
        this.keybind = keybind;
    }

    public static void registerServerTaskHandler() {
        FMLCommonHandler.instance().bus().register(new ServerTaskHandler());
    }

    public static class Handler implements IMessageHandler<PacketSendKey, IMessage> {

        @Override
        public IMessage onMessage(PacketSendKey message, MessageContext ctx) {
            // SimpleImpl invokes this directly from its Netty channel handler. Capture only
            // immutable packet data and the authenticated server handler here; the actual
            // player/world mutation runs from the server tick queue below.
            SERVER_TASKS.offer(new PendingKeybind(ctx.getServerHandler(), message.keybind));
            return null;
        }

    }

    public static class ServerTaskHandler {

        @SubscribeEvent
        public void onServerTick(ServerTickEvent event) {
            if (event.phase != Phase.END) return;

            PendingKeybind pendingKeybind;
            while ((pendingKeybind = SERVER_TASKS.poll()) != null) {
                // Resolve the player again on the server thread. A respawn replaces
                // NetHandlerPlayServer.playerEntity, while a closed connection has no work
                // left to apply.
                if (!pendingKeybind.serverHandler.netManager.isChannelOpen()) continue;
                EntityPlayerMP playerEntity = pendingKeybind.serverHandler.playerEntity;
                if (playerEntity == null || playerEntity.isDead || playerEntity.worldObj == null) continue;

                handle(pendingKeybind.keybind, playerEntity);
            }
        }

        private void handle(KeybindPacket keybind, EntityPlayerMP playerEntity) {

            if (keybind == KeybindPacket.TOGGLE_CRAWLING) {
                if (!PlayerMovementMode.useModernPlayerMovement(playerEntity)) return;

                IPlayerResizeable resizeable = (IPlayerResizeable) playerEntity;

                // flip crawl state
                boolean newState = !resizeable.isForcingCrawling();
                resizeable.setForcingCrawling(newState);

                if (effectsWhileCrawling) {

                    if (newState ) { //ENSURE WE ARE ACTUALLY CRAWLING, NOT JUST FORCING IT?
                        //newState is the keybind. If it's true, then we are forcing crawl pose, which means we should apply debuffs.
                        //ensure we are on server
                        if (!playerEntity.worldObj.isRemote) {
                            // Apply debuffs while crawling
                            playerEntity.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, Integer.MAX_VALUE, 1, false)); // Slowness II
                            playerEntity.addPotionEffect(new PotionEffect(Potion.digSlowdown.id, Integer.MAX_VALUE, 0, false)); // Mining Fatigue I
                        }
                    }
                }
            }
        }
    }

    private static class PendingKeybind {

        private final NetHandlerPlayServer serverHandler;
        private final KeybindPacket keybind;

        private PendingKeybind(NetHandlerPlayServer serverHandler, KeybindPacket keybind) {
            this.serverHandler = serverHandler;
            this.keybind = keybind;
        }
    }
}
