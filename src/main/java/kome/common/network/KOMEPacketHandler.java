package kome.common.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import kome.common.KOMEAddon;
import org.apache.logging.log4j.Level;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

public class KOMEPacketHandler {
    public static SimpleNetworkWrapper network;
    public static final int MAX_PENDING_SERVER_TASKS = 1024;
    public static final int MAX_PENDING_PER_CONNECTION = 32;
    public static final int MAX_SERVER_TASKS_PER_TICK = 64;
    public static final long SERVER_TASK_TICK_BUDGET_NANOS = 2_000_000L;
    private static final KOMEServerTaskQueue SERVER_TASKS = new KOMEServerTaskQueue(
        MAX_PENDING_SERVER_TASKS, MAX_PENDING_PER_CONNECTION, MAX_SERVER_TASKS_PER_TICK, SERVER_TASK_TICK_BUDGET_NANOS);
    static final KOMEServerRecordCooldown SERVER_RECORD_COOLDOWNS = new KOMEServerRecordCooldown(1024);
    private static volatile ServerSession session;

    /** Called on the server thread after native guards succeed, before accepting player intents. */
    public static void startServerSession(MinecraftServer server) {
        if (server == null) throw new IllegalArgumentException("Server required");
        clearPendingServerTasks();
        session = new ServerSession(server, Thread.currentThread(), SERVER_TASKS.open());
    }
    public static Requester captureRequester(MessageContext context) {
        ServerSession captured = session;
        if (captured == null || context == null || context.side != Side.SERVER) return null;
        NetHandlerPlayServer connection = context.getServerHandler();
        EntityPlayerMP player = connection == null ? null : connection.playerEntity;
        if (player == null) return null;
        return new Requester(captured, connection, player);
    }
    /** Admission reads only connection identity; registration/world access stays on the server thread. */
    public static boolean enqueueServerTask(MessageContext context, Runnable task) {
        return enqueueServerTask(captureRequester(context), task);
    }
    /** Share one captured identity across the queue fence and the caller's immutable intent. */
    public static boolean enqueueServerTask(Requester requester, Runnable task) {
        if (task == null) throw new IllegalArgumentException("Server packet task required");
        if (requester == null) return false;
        return SERVER_TASKS.offer(requester.session.generation, requester.connection,
            () -> { if (requester.isCurrent()) task.run(); },
            () -> { if (requester.isCurrent()) requester.player.addChatMessage(new ChatComponentText(
                "KOME server busy: newest request dropped; retry after pending requests finish.")); })
            == KOMEServerTaskQueue.Admission.ACCEPTED;
    }
    /** Tick-start snapshot, round-robin lanes, count limit and soft elapsed-time budget. */
    public static int runPendingServerTasks() {
        ServerSession current = session;
        if (current == null) return 0;
        current.requireServerThread();
        return SERVER_TASKS.drain(KOMEPacketHandler::reportServerTaskFailure, System::nanoTime);
    }
    public static void forgetRequester(EntityPlayerMP player) {
        ServerSession current = session;
        if (current != null) current.requireServerThread();
        SERVER_TASKS.forget(player.playerNetServerHandler);
        SERVER_RECORD_COOLDOWNS.forget(player.playerNetServerHandler);
    }
    public static final class Requester {
        private final ServerSession session;
        final NetHandlerPlayServer connection;
        public final EntityPlayerMP player;
        private Requester(ServerSession session, NetHandlerPlayServer connection, EntityPlayerMP player) {
            this.session = session; this.connection = connection; this.player = player;
        }
        public boolean isCurrent() {
            if (KOMEPacketHandler.session != session) return false;
            session.requireServerThread();
            if (MinecraftServer.getServer() != session.server
                || connection.playerEntity != player || player.playerNetServerHandler != connection
                || connection.netManager == null || !connection.netManager.isChannelOpen()
                || player.worldObj == null || player.worldObj.isRemote || player.isDead
                || session.server.getConfigurationManager() == null) return false;
            for (Object registered : session.server.getConfigurationManager().playerEntityList)
                if (registered == player) return true;
            return false;
        }
    }
    private static final class ServerSession {
        final MinecraftServer server; final Thread owner; final long generation;
        ServerSession(MinecraftServer server, Thread owner, long generation) {
            this.server = server; this.owner = owner; this.generation = generation;
        }
        void requireServerThread() {
            if (Thread.currentThread() != owner) throw new IllegalStateException("KOME task execution requires server thread");
        }
    }

    private static void reportServerTaskFailure(RuntimeException error) {
        try {
            cpw.mods.fml.common.FMLLog.log(Level.ERROR, error, "KOME server packet task failed");
        } catch (Throwable loggingFailure) {
            System.err.println("[KOME] Server packet task failed: " + error.getMessage());
            error.printStackTrace(System.err);
        }
    }

    public static void clearPendingServerTasks() {
        session = null;
        SERVER_TASKS.close();
        SERVER_RECORD_COOLDOWNS.clear();
    }

    static int pendingServerTaskCount() { return SERVER_TASKS.pending(); }

    /** Forge 1.7.10 has no MinecraftServer task scheduler; delegates run at ServerTick START. */
    public static class ServerThreadHandler<T extends IMessage> implements IMessageHandler<T, IMessage> {
        private final IMessageHandler<T, IMessage> delegate;

        public ServerThreadHandler(IMessageHandler<T, IMessage> delegate) {
            if (delegate == null) {
                throw new IllegalArgumentException("Server packet delegate is required.");
            }
            this.delegate = delegate;
        }

        @Override
        public IMessage onMessage(final T message, final MessageContext context) {
            final Requester requester = captureRequester(context);
            if (requester == null) return null;
            enqueueServerTask(requester, () -> {
                if (!requester.isCurrent()) return;
                IMessage reply = delegate.onMessage(message, context);
                if (reply != null && requester.isCurrent()) network.sendTo(reply, requester.player);
            });
            return null;
        }
    }

    public static void init() {
        network = NetworkRegistry.INSTANCE.newSimpleChannel(KOMEAddon.MODID);
        network.registerMessage(KOMEPacketPopulationGui.Handler.class, KOMEPacketPopulationGui.class, 0, Side.CLIENT);
        network.registerMessage(KOMEPacketPopulationUnitsGui.Handler.class, KOMEPacketPopulationUnitsGui.class, 3, Side.CLIENT);
        network.registerMessage(KOMEPacketConquestCaptureGui.Handler.class, KOMEPacketConquestCaptureGui.class, 5, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketConquestClaim>(new KOMEPacketConquestClaim.Handler()) {}, KOMEPacketConquestClaim.class, 6, Side.SERVER);
        network.registerMessage(new ServerThreadHandler<KOMEPacketConquestOpenCapture>(new KOMEPacketConquestOpenCapture.Handler()) {}, KOMEPacketConquestOpenCapture.class, 7, Side.SERVER);
        network.registerMessage(KOMEPacketProgressionData.Handler.class, KOMEPacketProgressionData.class, 9, Side.CLIENT);
        network.registerMessage(KOMEPacketQuotaLedger.Handler.class, KOMEPacketQuotaLedger.class, 10, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketServerRecordRequest>(new KOMEPacketServerRecordRequest.Handler()) {}, KOMEPacketServerRecordRequest.class, 11, Side.SERVER);
        network.registerMessage(KOMEPacketServerRecordData.Handler.class, KOMEPacketServerRecordData.class, 12, Side.CLIENT);
        network.registerMessage(KOMEPacketConquestData.Handler.class, KOMEPacketConquestData.class, 13, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketUnitCapRequest>(new KOMEPacketUnitCapRequest.Handler()) {}, KOMEPacketUnitCapRequest.class, 14, Side.SERVER);
        network.registerMessage(new ServerThreadHandler<KOMEPacketUnitCapUpdate>(new KOMEPacketUnitCapUpdate.Handler()) {}, KOMEPacketUnitCapUpdate.class, 15, Side.SERVER);
        network.registerMessage(KOMEPacketUnitCapSync.Handler.class, KOMEPacketUnitCapSync.class, 16, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketAllianceRequest>(new KOMEPacketAllianceRequest.Handler()) {}, KOMEPacketAllianceRequest.class, 17, Side.SERVER);
        network.registerMessage(KOMEPacketAllianceData.Handler.class, KOMEPacketAllianceData.class, 18, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketConquestTransfer>(new KOMEPacketConquestTransfer.Handler()) {}, KOMEPacketConquestTransfer.class, 19, Side.SERVER);
        network.registerMessage(KOMEPacketLordMenu.Handler.class, KOMEPacketLordMenu.class, 20, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketLordAction>(new KOMEPacketLordAction.Handler()) {}, KOMEPacketLordAction.class, 21, Side.SERVER);
        network.registerMessage(KOMEPacketLordHighlight.Handler.class, KOMEPacketLordHighlight.class, 22, Side.CLIENT);
        // IDs 23 and 24 are retired population/allocation mutation packets; do not reuse.
        network.registerMessage(KOMEPacketCompanyListGui.Handler.class, KOMEPacketCompanyListGui.class, 25, Side.CLIENT);
        network.registerMessage(KOMEPacketCompanyMoveConfirmGui.Handler.class, KOMEPacketCompanyMoveConfirmGui.class, 26, Side.CLIENT);
        network.registerMessage(KOMEPacketCompanyMovePreviewResult.Handler.class, KOMEPacketCompanyMovePreviewResult.class, 27, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketMovementHistoryRequest>(new KOMEPacketMovementHistoryRequest.Handler()) {}, KOMEPacketMovementHistoryRequest.class, 28, Side.SERVER);
        network.registerMessage(KOMEPacketMovementHistoryData.Handler.class, KOMEPacketMovementHistoryData.class, 29, Side.CLIENT);
        network.registerMessage(KOMEPacketUnitMapMarkers.Handler.class, KOMEPacketUnitMapMarkers.class, 30, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketWaypointTravelRequest>(new KOMEPacketWaypointTravelRequest.Handler()) {}, KOMEPacketWaypointTravelRequest.class, 31, Side.SERVER);
        network.registerMessage(new ServerThreadHandler<KOMEPacketAllianceAction>(new KOMEPacketAllianceAction.Handler()) {}, KOMEPacketAllianceAction.class, 32, Side.SERVER);
        network.registerMessage(new ServerThreadHandler<KOMEPacketPledgeDepartureRequest>(new KOMEPacketPledgeDepartureRequest.Handler()) {}, KOMEPacketPledgeDepartureRequest.class, 33, Side.SERVER);
        network.registerMessage(KOMEPacketPledgeDepartureData.Handler.class, KOMEPacketPledgeDepartureData.class, 34, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketTroopGuiAction>(new KOMEPacketTroopGuiAction.Handler()) {}, KOMEPacketTroopGuiAction.class, 35, Side.SERVER);
        network.registerMessage(new ServerThreadHandler<KOMEPacketBuildAction>(new KOMEPacketBuildAction.Handler()) {}, KOMEPacketBuildAction.class, 36, Side.SERVER);
        network.registerMessage(new ServerThreadHandler<KOMEPacketCampaignHire>(new KOMEPacketCampaignHire.Handler()) {}, KOMEPacketCampaignHire.class, 37, Side.SERVER);
        network.registerMessage(KOMEPacketSerfdomMasterMenu.Handler.class, KOMEPacketSerfdomMasterMenu.class, 38, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketSerfdomMasterAction>(new KOMEPacketSerfdomMasterAction.Handler()) {}, KOMEPacketSerfdomMasterAction.class, 39, Side.SERVER);
        network.registerMessage(new ServerThreadHandler<KOMEPacketProgressionRelationshipAction>(new KOMEPacketProgressionRelationshipAction.Handler()) {}, KOMEPacketProgressionRelationshipAction.class, 40, Side.SERVER);
        network.registerMessage(KOMEPacketRelationshipHub.Handler.class, KOMEPacketRelationshipHub.class, 41, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketRelationshipAction>(new KOMEPacketRelationshipAction.Handler()) {}, KOMEPacketRelationshipAction.class, 42, Side.SERVER);
        network.registerMessage(KOMEPacketVisualMarkers.Handler.class, KOMEPacketVisualMarkers.class, 43, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketProgressionRequest>(new KOMEPacketProgressionRequest.Handler()) {}, KOMEPacketProgressionRequest.class, 44, Side.SERVER);
        network.registerMessage(KOMEPacketProgressionTracker.Handler.class, KOMEPacketProgressionTracker.class, 45, Side.CLIENT);
        network.registerMessage(KOMEPacketStandingTrialEligibility.Handler.class, KOMEPacketStandingTrialEligibility.class, 46, Side.CLIENT);
        // Both branches used 38; retain progression IDs and append public waypoints at the next free ID.
        network.registerMessage(KOMEPacketPublicWaypoints.Handler.class, KOMEPacketPublicWaypoints.class, 47, Side.CLIENT);
    }
}
