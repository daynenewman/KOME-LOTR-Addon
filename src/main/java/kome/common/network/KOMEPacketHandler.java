package kome.common.network;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import kome.common.KOMEAddon;
import org.apache.logging.log4j.Level;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class KOMEPacketHandler {
    public static SimpleNetworkWrapper network;
    private static final Queue<Runnable> SERVER_TASKS = new ConcurrentLinkedQueue<Runnable>();

    public static void enqueueServerTask(Runnable task) {
        if (task == null) {
            throw new IllegalArgumentException("Server packet task is required.");
        }
        SERVER_TASKS.add(task);
    }

    /** Drains only the queue snapshot present at the start of this server tick. */
    public static int runPendingServerTasks() {
        int remaining = SERVER_TASKS.size();
        int processed = 0;
        while (remaining-- > 0) {
            Runnable task = SERVER_TASKS.poll();
            if (task == null) {
                break;
            }
            processed++;
            try {
                task.run();
            } catch (RuntimeException error) {
                reportServerTaskFailure(error);
            }
        }
        return processed;
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
        SERVER_TASKS.clear();
    }

    static int pendingServerTaskCount() {
        return SERVER_TASKS.size();
    }

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
            enqueueServerTask(new Runnable() {
                @Override
                public void run() {
                    IMessage reply = delegate.onMessage(message, context);
                    if (reply != null) {
                        network.sendTo(reply, context.getServerHandler().playerEntity);
                    }
                }
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
        network.registerMessage(KOMEPacketSerfdomMasterMenu.Handler.class, KOMEPacketSerfdomMasterMenu.class, 37, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketSerfdomMasterAction>(new KOMEPacketSerfdomMasterAction.Handler()) {}, KOMEPacketSerfdomMasterAction.class, 38, Side.SERVER);
        network.registerMessage(new ServerThreadHandler<KOMEPacketProgressionRelationshipAction>(new KOMEPacketProgressionRelationshipAction.Handler()) {}, KOMEPacketProgressionRelationshipAction.class, 39, Side.SERVER);
        network.registerMessage(KOMEPacketRelationshipHub.Handler.class, KOMEPacketRelationshipHub.class, 40, Side.CLIENT);
        network.registerMessage(new ServerThreadHandler<KOMEPacketRelationshipAction>(new KOMEPacketRelationshipAction.Handler()) {}, KOMEPacketRelationshipAction.class, 41, Side.SERVER);
    }
}
