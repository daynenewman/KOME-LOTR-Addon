package kome.common.tactical.edit;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import kome.common.data.KOMEWorldData;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketTacticalEditSnapshot;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/** Separate FML/Forge lifecycle and bounded server-tick intake; no physical-gate configuration/domain state. */
public final class KOMETacticalEditRuntime {
    private static final KOMETacticalEditRequestQueue<WeakReference<EntityPlayerMP>> QUEUE = new KOMETacticalEditRequestQueue<WeakReference<EntityPlayerMP>>();
    private static final KOMETacticalEditRequestLimiter LIMITER = new KOMETacticalEditRequestLimiter();
    private static final Map<UUID, WeakReference<EntityPlayerMP>> OWNERS = new HashMap<UUID, WeakReference<EntityPlayerMP>>();
    private static KOMETacticalEditSessionManager sessions;
    private static Thread serverThread;

    /** Network thread only: bounded immutable intent, no permission/world inspection or NBT parsing here. */
    public static boolean enqueue(EntityPlayerMP player, KOMETacticalEditRequest request) {
        if (player == null || request == null) return false;
        if (!LIMITER.tryAcquire(player.getUniqueID(), request.getAction())
                || !QUEUE.offer(player.getUniqueID(), new WeakReference<EntityPlayerMP>(player), request)) return false;
        return true;
    }
    @SubscribeEvent public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (serverThread == null) { serverThread = Thread.currentThread(); sessions = new KOMETacticalEditSessionManager(); }
        if (Thread.currentThread() != serverThread) throw new IllegalStateException("Wrong tactical editor server thread.");
        for (KOMETacticalEditSessionManager.Result expired : sessions.tick()) {
            UUID owner = expired.getSnapshot().getPlayerId();
            WeakReference<EntityPlayerMP> reference = OWNERS.remove(owner);
            if (reference != null && reference.get() != null) send(reference.get(), expired);
        }
        for (int processed = 0; processed < KOMETacticalEditRequestQueue.MAX_PER_TICK; processed++) {
            KOMETacticalEditRequestQueue.Entry<WeakReference<EntityPlayerMP>> pending = QUEUE.poll();
            if (pending == null) break;
            EntityPlayerMP player = pending.getHandle().get();
            if (!hasConnection(player) || !pending.getPlayerId().equals(player.getUniqueID())) continue;
            if (!KOMETacticalEditAccess.isAuthorized(player) || player.isDead) {
                send(player, KOMETacticalEditSessionManager.Status.DENIED, null); continue;
            }
            try {
                KOMETacticalEditSessionManager.Result result = sessions.handle(new PlayerActor(player), KOMEWorldData.get(player.worldObj), pending.getRequest());
                KOMETacticalEditSnapshot snapshot = result.getSnapshot();
                if (snapshot != null) {
                    if (snapshot.isClosed()) OWNERS.remove(player.getUniqueID());
                    else OWNERS.put(player.getUniqueID(), pending.getHandle());
                }
                send(player, result);
            } catch (RuntimeException rejected) {
                cpw.mods.fml.common.FMLLog.warning("KOME tactical editor request rejected: %s", rejected.getMessage());
                send(player, KOMETacticalEditSessionManager.Status.REJECTED, null);
            }
        }
    }
    @SubscribeEvent public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) { close(event.player, false); }
    @SubscribeEvent public void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) { close(event.player, true); }
    @SubscribeEvent public void onRespawn(PlayerEvent.PlayerRespawnEvent event) { close(event.player, true); }
    @SubscribeEvent public void onDeath(LivingDeathEvent event) { if (event.entityLiving instanceof EntityPlayerMP) close((EntityPlayerMP) event.entityLiving, true); }
    private static void close(EntityPlayer player, boolean notify) {
        if (!(player instanceof EntityPlayerMP)) return;
        UUID id = player.getUniqueID(); QUEUE.clearPlayer(id); LIMITER.clearPlayer(id); OWNERS.remove(id);
        if (sessions != null) {
            KOMETacticalEditSessionManager.Result result = sessions.closePlayer(id);
            if (notify && result.getSnapshot() != null) send((EntityPlayerMP) player, result);
        }
    }
    /** Called at start, stop and failed-start cleanup; never retains a world/player across server sessions. */
    public static void resetServerState() { QUEUE.clear(); LIMITER.clear(); OWNERS.clear(); sessions = null; serverThread = null; }
    private static void send(EntityPlayerMP player, KOMETacticalEditSessionManager.Result result) { send(player, result.getStatus(), result.getSnapshot()); }
    private static void send(EntityPlayerMP player, KOMETacticalEditSessionManager.Status status, KOMETacticalEditSnapshot snapshot) {
        if (hasConnection(player) && KOMEPacketHandler.network != null) {
            KOMEPacketHandler.network.sendTo(new KOMEPacketTacticalEditSnapshot(status, snapshot), player);
        }
    }
    private static boolean hasConnection(EntityPlayerMP player) {
        return player != null && player.playerNetServerHandler != null && player.playerNetServerHandler.netManager != null
            && player.playerNetServerHandler.netManager.isChannelOpen();
    }
    private static final class PlayerActor implements KOMETacticalEditSessionManager.Actor {
        private final EntityPlayerMP player;
        PlayerActor(EntityPlayerMP player) { this.player = player; }
        public UUID getPlayerId() { return player.getUniqueID(); }
        public int getDimensionId() { return player.dimension; }
        public boolean isAuthorized() { return KOMETacticalEditAccess.isAuthorized(player); }
        public boolean isConnected() { return hasConnection(player) && !player.isDead; }
    }
}
