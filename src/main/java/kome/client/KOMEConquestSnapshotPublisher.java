package kome.client;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEConquestRouteEdge;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMETileTroopSummary;
import kome.common.data.KOMETileWaypointLink;
import kome.common.network.KOMEPacketConquestData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Assembles ordered wire chunks and publishes only completed authoritative snapshots. */
public final class KOMEConquestSnapshotPublisher {
    private static final Logger LOGGER = LogManager.getLogger("KOMEConquestSnapshotPublisher");
    private static final Object PUBLICATION_KEY = new Object();

    private final KOMEClientTaskQueue tasks;
    private final Runnable onSensitiveRemoval;
    private Accumulator incoming;
    private Snapshot lastComplete;
    private long generation;
    private Object connection;
    private long latestPublicationSerial = -1L;
    private long completedPublicationSerial = -1L;
    private long appliedPublicationSerial = -1L;

    public KOMEConquestSnapshotPublisher(KOMEClientTaskQueue tasks) {
        this(tasks, () -> { });
    }

    public KOMEConquestSnapshotPublisher(KOMEClientTaskQueue tasks, Runnable onSensitiveRemoval) {
        if (tasks == null) throw new IllegalArgumentException("Client task queue is required");
        this.tasks = tasks;
        this.onSensitiveRemoval = onSensitiveRemoval;
    }

    /** Drops an incomplete snapshot from the previous connection. */
    public synchronized void resetSession() {
        incoming = null;
        lastComplete = null;
        generation++;
        latestPublicationSerial = -1L;
        completedPublicationSerial = -1L;
        appliedPublicationSerial = -1L;
    }

    /** The exact NetHandlerPlayClient object is the session identity, as for unit markers. */
    public synchronized void start(Object connection) {
        this.connection = connection;
        resetSession();
    }

    /** A world replacement invalidates pending work while retaining the connection serial floor. */
    public synchronized void invalidateWorld() {
        incoming = null;
        lastComplete = null;
        completedPublicationSerial = latestPublicationSerial;
        appliedPublicationSerial = -1L;
        generation++;
    }

    /** Checked on the client thread immediately before opening a map-origin detail view. */
    public synchronized boolean allowsMapDetail(long serial, Object source) {
        return source != null && source == connection && serial > 0L
            && serial == latestPublicationSerial && serial == completedPublicationSerial
            && serial == appliedPublicationSerial
            && lastComplete != null;
    }

    /** Only a fully applied newer snapshot can justify a current-authority re-query. */
    public synchronized long appliedPublicationSerial(Object source) {
        return source != null && source == connection && appliedPublicationSerial == latestPublicationSerial
            ? appliedPublicationSerial : -1L;
    }

    public synchronized void accept(KOMEPacketConquestData.PublicationChunk chunk,Object source) {
        if (connection == null || connection != source) return;
        accept(chunk);
    }

    synchronized void accept(KOMEPacketConquestData.PublicationChunk chunk) {
        if (chunk == null) throw new IllegalArgumentException("Conquest snapshot chunk is required");
        if (chunk.publicationSerial < latestPublicationSerial) return;
        if (chunk.publicationSerial > 0L
                && chunk.publicationSerial == completedPublicationSerial) return;
        if (chunk.publicationSerial > latestPublicationSerial) {
            if (!chunk.reset) return;
            latestPublicationSerial = chunk.publicationSerial;
            incoming = null;
        }
        if (chunk.reset) incoming = new Accumulator();
        if (incoming == null) {
            // Continuations cannot initialize a session; after a completed baseline, retain
            // the existing non-reset delta contract without coalescing incomplete state.
            if (lastComplete == null) return;
            incoming = new Accumulator(lastComplete);
        }
        incoming.add(chunk);
        if (!chunk.complete) return;

        Snapshot completed = incoming.complete();
        incoming = null;
        lastComplete = completed;
        completedPublicationSerial = chunk.publicationSerial;
        final long publicationGeneration = generation;
        final long publicationSerial = chunk.publicationSerial;
        try {
            tasks.enqueueLatest(PUBLICATION_KEY, () -> {
                synchronized (KOMEConquestSnapshotPublisher.this) {
                    if (publicationGeneration == generation
                            && publicationSerial == latestPublicationSerial) {
                        boolean removed = completed.publish();
                        appliedPublicationSerial = publicationSerial;
                        if (removed) onSensitiveRemoval.run();
                    }
                }
            });
        } catch (RejectedExecutionException full) {
            // A queue overload must never escape a Forge packet handler. Normal
            // conquest traffic cannot reach this path because it occupies one
            // coalesced slot regardless of chunk or refresh count.
            LOGGER.warn("Discarding completed KOME conquest snapshot because the client task queue is full");
        }
    }

    private static final class Accumulator {
        private final Map<String, KOMEArmyCompany> armyCompanies = new HashMap<String, KOMEArmyCompany>();
        private final Map<String, KOMEConquestTile> conquestTiles = new HashMap<String, KOMEConquestTile>();
        private final Map<String, KOMEArmyMovementOrder> armyMovements = new HashMap<String, KOMEArmyMovementOrder>();
        private final Map<String, KOMETileTroopSummary> troopSummaries = new HashMap<String, KOMETileTroopSummary>();
        private final Map<String, KOMEConquestRouteEdge> routeEdges = new HashMap<String, KOMEConquestRouteEdge>();
        private final Map<String, KOMETileWaypointLink> waypointLinks = new HashMap<String, KOMETileWaypointLink>();
        private final Map<String, KOMEPlayerBuild> builds = new HashMap<String, KOMEPlayerBuild>();
        private final Map<String, String> capitalTiles = new HashMap<String, String>();

        private Accumulator() { }

        private Accumulator(Snapshot base) {
            armyCompanies.putAll(base.armyCompanies);
            conquestTiles.putAll(base.conquestTiles);
            armyMovements.putAll(base.armyMovements);
            troopSummaries.putAll(base.troopSummaries);
            routeEdges.putAll(base.routeEdges);
            waypointLinks.putAll(base.waypointLinks);
            builds.putAll(base.builds);
            capitalTiles.putAll(base.capitalTiles);
        }

        private void add(KOMEPacketConquestData.PublicationChunk chunk) {
            armyCompanies.putAll(chunk.armyCompanies);
            conquestTiles.putAll(chunk.conquestTiles);
            armyMovements.putAll(chunk.armyMovements);
            for (KOMETileTroopSummary row : chunk.troopSummaries.values()) {
                if (row.hasAnyPopulation()) troopSummaries.put(row.tileId, row);
                else troopSummaries.remove(row.tileId);
            }
            routeEdges.putAll(chunk.routeEdges);
            waypointLinks.putAll(chunk.tileWaypointLinksByTileId);
            builds.putAll(chunk.builds);
            capitalTiles.putAll(chunk.capitalTilesByFaction);
        }

        private Snapshot complete() {
            return new Snapshot(armyCompanies, conquestTiles, armyMovements, troopSummaries,
                routeEdges, waypointLinks, builds, capitalTiles);
        }
    }

    private static final class Snapshot {
        private final Map<String, KOMEArmyCompany> armyCompanies;
        private final Map<String, KOMEConquestTile> conquestTiles;
        private final Map<String, KOMEArmyMovementOrder> armyMovements;
        private final Map<String, KOMETileTroopSummary> troopSummaries;
        private final Map<String, KOMEConquestRouteEdge> routeEdges;
        private final Map<String, KOMETileWaypointLink> waypointLinks;
        private final Map<String, KOMEPlayerBuild> builds;
        private final Map<String, String> capitalTiles;

        private Snapshot(Map<String, KOMEArmyCompany> armyCompanies,
                Map<String, KOMEConquestTile> conquestTiles,
                Map<String, KOMEArmyMovementOrder> armyMovements,
                Map<String, KOMETileTroopSummary> troopSummaries,
                Map<String, KOMEConquestRouteEdge> routeEdges,
                Map<String, KOMETileWaypointLink> waypointLinks,
                Map<String, KOMEPlayerBuild> builds,
                Map<String, String> capitalTiles) {
            this.armyCompanies = new HashMap<String, KOMEArmyCompany>(armyCompanies);
            this.conquestTiles = new HashMap<String, KOMEConquestTile>(conquestTiles);
            this.armyMovements = new HashMap<String, KOMEArmyMovementOrder>(armyMovements);
            this.troopSummaries = new HashMap<String, KOMETileTroopSummary>(troopSummaries);
            this.routeEdges = new HashMap<String, KOMEConquestRouteEdge>(routeEdges);
            this.waypointLinks = new HashMap<String, KOMETileWaypointLink>(waypointLinks);
            this.builds = new HashMap<String, KOMEPlayerBuild>(builds);
            this.capitalTiles = new HashMap<String, String>(capitalTiles);
        }

        private boolean publish() {
            KOMEClientData data = KOMEClientData.INSTANCE;
            boolean removed = !armyCompanies.keySet().containsAll(data.armyCompanies.keySet())
                || !armyMovements.keySet().containsAll(data.armyMovements.keySet());
            if (!removed) for (KOMETileTroopSummary previous : data.troopSummaries.values()) {
                KOMETileTroopSummary current = troopSummaries.get(previous.tileId);
                if (current == null || current.stationedPop < previous.stationedPop
                        || current.movingPop < previous.movingPop
                        || current.incomingPop < previous.incomingPop) { removed = true; break; }
            }
            data.armyCompanies.clear();
            data.armyCompanies.putAll(armyCompanies);
            data.conquestTiles.clear();
            data.conquestTiles.putAll(conquestTiles);
            data.capitalTilesByFaction.clear();
            data.capitalTilesByFaction.putAll(capitalTiles);
            data.armyMovements.clear();
            data.armyMovements.putAll(armyMovements);
            data.troopSummaries.clear();
            data.troopSummaries.putAll(troopSummaries);
            data.routeEdges.clear();
            data.routeEdges.putAll(routeEdges);
            data.tileWaypointLinksByTileId.clear();
            data.tileWaypointLinksByTileId.putAll(waypointLinks);
            data.builds.clear();
            data.builds.putAll(builds);
            data.completeConquestUpdate();
            return removed;
        }
    }
}
