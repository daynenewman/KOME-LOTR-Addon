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
    private Accumulator incoming;

    public KOMEConquestSnapshotPublisher(KOMEClientTaskQueue tasks) {
        if (tasks == null) throw new IllegalArgumentException("Client task queue is required");
        this.tasks = tasks;
    }

    /** Drops an incomplete snapshot from the previous connection. */
    public synchronized void resetSession() {
        incoming = null;
    }

    public synchronized void accept(KOMEPacketConquestData.PublicationChunk chunk) {
        if (chunk == null) throw new IllegalArgumentException("Conquest snapshot chunk is required");
        if (chunk.reset) incoming = new Accumulator();
        if (incoming == null) return;
        incoming.add(chunk);
        if (!chunk.complete) return;

        Snapshot completed = incoming.complete();
        incoming = null;
        try {
            tasks.enqueueLatest(PUBLICATION_KEY, completed::publish);
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

        private void add(KOMEPacketConquestData.PublicationChunk chunk) {
            armyCompanies.putAll(chunk.armyCompanies);
            conquestTiles.putAll(chunk.conquestTiles);
            armyMovements.putAll(chunk.armyMovements);
            troopSummaries.putAll(chunk.troopSummaries);
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

        private void publish() {
            KOMEClientData data = KOMEClientData.INSTANCE;
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
            data.conquestRevision++;
        }
    }
}
