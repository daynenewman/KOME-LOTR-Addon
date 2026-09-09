package com.enovak.lotrmoremobs.network;

import com.enovak.lotrmoremobs.config.MumakilConfig;
import com.enovak.lotrmoremobs.entity.animal.LOTREntityMumakil;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

public class MumakilOpenGuiPacket implements IMessage {

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class Handler
            implements IMessageHandler<MumakilOpenGuiPacket, IMessage> {

        private static final int MAX_PENDING_PLAYERS = 64;
        private static final int MAX_OPENS_PER_TICK = 8;
        private static final double REQUESTS_PER_SECOND = 4.0D;
        private static final int REQUEST_BURST = 4;

        private static final Deque<PendingPlayer> PENDING_PLAYERS =
                new ArrayDeque<PendingPlayer>();
        private static final Set<UUID> PENDING_PLAYER_SET =
                new HashSet<UUID>();
        private static final Map<UUID, TokenBucket> RATE_BUCKETS =
                new HashMap<UUID, TokenBucket>();

        public Handler() {
            FMLCommonHandler.instance().bus().register(this);
        }

        @Override
        public IMessage onMessage(
                MumakilOpenGuiPacket message,
                MessageContext ctx
        ) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player != null) {
                enqueue(player);
            }
            return null;
        }

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.START) {
                return;
            }

            for (int processed = 0;
                 processed < MAX_OPENS_PER_TICK;
                 ++processed) {
                PendingPlayer pending = pollPending();
                if (pending == null) {
                    return;
                }

                if (!MumakilConfig.enableMumakil) {
                    continue;
                }

                EntityPlayerMP player = pending.player.get();
                if (player == null
                        || !pending.playerUuid.equals(player.getUniqueID())
                        || player.isDead
                        || !player.isEntityAlive()
                        || player.worldObj == null
                        || player.worldObj.isRemote
                        || player.worldObj.getEntityByID(
                        player.getEntityId()
                ) != player
                        || !(player.ridingEntity
                        instanceof LOTREntityMumakil)) {
                    continue;
                }

                LOTREntityMumakil mumakil =
                        (LOTREntityMumakil)player.ridingEntity;
                if (mumakil.isDead
                        || !mumakil.isEntityAlive()
                        || mumakil.worldObj != player.worldObj
                        || mumakil.worldObj.getEntityByID(
                        mumakil.getEntityId()
                ) != mumakil
                        || !mumakil.canPlayerUseMumakilInventory(player)) {
                    continue;
                }

                mumakil.openGUI(player);
            }
        }

        @SubscribeEvent
        public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            clearLifecyclePlayer(event.player);
        }

        @SubscribeEvent
        public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
            clearLifecyclePlayer(event.player);
        }

        @SubscribeEvent
        public void onPlayerChangedDimension(
                PlayerEvent.PlayerChangedDimensionEvent event
        ) {
            clearLifecyclePlayer(event.player);
        }

        private static void clearLifecyclePlayer(
                net.minecraft.entity.player.EntityPlayer player
        ) {
            if (player != null) {
                clearPlayer(player.getUniqueID());
            }
        }

        private static synchronized void enqueue(EntityPlayerMP player) {
            UUID playerUuid = player == null ? null : player.getUniqueID();
            if (!MumakilConfig.enableMumakil
                    || playerUuid == null
                    || PENDING_PLAYER_SET.contains(playerUuid)
                    || PENDING_PLAYERS.size() >= MAX_PENDING_PLAYERS
                    || !tryAcquire(playerUuid)) {
                return;
            }

            PENDING_PLAYER_SET.add(playerUuid);
            PENDING_PLAYERS.addLast(new PendingPlayer(playerUuid, player));
        }

        private static synchronized PendingPlayer pollPending() {
            PendingPlayer pending = PENDING_PLAYERS.pollFirst();
            if (pending != null) {
                PENDING_PLAYER_SET.remove(pending.playerUuid);
            }
            return pending;
        }

        private static synchronized void clearPlayer(UUID playerUuid) {
            if (playerUuid == null) {
                return;
            }
            java.util.Iterator<PendingPlayer> iterator = PENDING_PLAYERS.iterator();
            while (iterator.hasNext()) {
                if (playerUuid.equals(iterator.next().playerUuid)) {
                    iterator.remove();
                }
            }
            PENDING_PLAYER_SET.remove(playerUuid);
            RATE_BUCKETS.remove(playerUuid);
        }

        private static synchronized boolean tryAcquire(UUID playerUuid) {
            TokenBucket bucket = RATE_BUCKETS.get(playerUuid);
            if (bucket == null) {
                bucket = new TokenBucket(
                        REQUESTS_PER_SECOND,
                        REQUEST_BURST
                );
                RATE_BUCKETS.put(playerUuid, bucket);
            }
            return bucket.tryAcquire(System.nanoTime());
        }

        private static final class PendingPlayer {
            private final UUID playerUuid;
            private final WeakReference<EntityPlayerMP> player;

            private PendingPlayer(UUID playerUuid, EntityPlayerMP player) {
                this.playerUuid = playerUuid;
                this.player = new WeakReference<EntityPlayerMP>(player);
            }
        }

        private static synchronized void resetServerState() {
            PENDING_PLAYERS.clear();
            PENDING_PLAYER_SET.clear();
            RATE_BUCKETS.clear();
        }

        private static final class TokenBucket {
            private final double refillPerNanosecond;
            private final int capacity;
            private double tokens;
            private long lastRefillNanos;

            private TokenBucket(double requestsPerSecond, int capacity) {
                refillPerNanosecond = requestsPerSecond / 1000000000.0D;
                this.capacity = capacity;
                tokens = capacity;
                lastRefillNanos = System.nanoTime();
            }

            private boolean tryAcquire(long nowNanos) {
                long elapsed = nowNanos - lastRefillNanos;
                if (elapsed > 0L) {
                    tokens = Math.min(
                            capacity,
                            tokens + elapsed * refillPerNanosecond
                    );
                    lastRefillNanos = nowNanos;
                }
                if (tokens < 1.0D) {
                    return false;
                }
                tokens -= 1.0D;
                return true;
            }
        }
    }

    public static void resetServerState() {
        Handler.resetServerState();
    }
}
