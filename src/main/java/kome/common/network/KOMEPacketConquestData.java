package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEConquestRouteEdge;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEFactionCapitalRecord;
import kome.common.data.KOMEFactionCapitalService;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMETileWaypointLink;
import kome.common.data.KOMETileTroopSummary;
import kome.common.data.KOMEWorldData;
import net.minecraft.entity.player.EntityPlayerMP;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.world.World;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class KOMEPacketConquestData implements IMessage {
    private static final int MAX_ENTRIES_PER_PACKET = 48;

    public NBTTagCompound data = new NBTTagCompound();
    public boolean reset;
    public boolean complete = true;
    public long publicationSerial;
    // Receiver-owned copy: public packet fields can be changed by callers after decode.
    // Never exposed or mutated; handlers materialize independent records before queuing.
    private KOMEPacketConquestData decodedPublication;

    public KOMEPacketConquestData() {
    }

    public KOMEPacketConquestData(KOMEWorldData worldData) {
        this(worldData, null, "", false);
    }

    private KOMEPacketConquestData(KOMEWorldData worldData, UUID viewer,
            String viewerFaction, boolean operator) {
        KOMECompanyMapProjection.View visibility=KOMECompanyMapProjection.view(
            worldData,viewer,viewerFaction,operator);
        NBTTagList list = new NBTTagList();
        for (KOMEConquestTile tile : worldData.conquestTiles.values()) {
            if (!tile.projectRulingFaction().isEmpty()) {
                list.appendTag(tile.projectToNBT());
            }
        }
        data.setTag("ConquestTiles", list);
        NBTTagList capitalList = new NBTTagList();
        for (String faction : KOMEAlliance.allFactionKeys()) {
            KOMEFactionCapitalRecord capital =
                KOMEFactionCapitalService.getCapital(worldData, faction);
            if (capital == null) continue;
            NBTTagCompound summary = new NBTTagCompound();
            summary.setString("Faction", faction);
            summary.setString("Tile", capital.getCapitalTileId());
            capitalList.appendTag(summary);
        }
        data.setTag("FactionCapitals", capitalList);
        NBTTagList movementList = new NBTTagList();
        for (KOMEArmyMovementOrder order : worldData.armyMovements.values()) {
            KOMEArmyCompany company = order == null ? null : worldData.armyCompanies.get(order.companyId);
            if (order != null && order.isMoving() && company != null
                    && KOMEAlliance.normalizeFactionKey(order.ownerFaction).equals(
                        KOMEAlliance.normalizeFactionKey(company.faction))
                    && visibility.directlyVisible(company)) {
                movementList.appendTag(KOMECompanyMapProjection.movementTag(order));
            }
        }
        data.setTag("ArmyMovements", movementList);
        NBTTagList companyList = new NBTTagList();
        for (KOMEArmyCompany company : worldData.armyCompanies.values()) {
            if (visibility.visible(company)) {
                int present=visibility.stationedPopulation(company);
                companyList.appendTag(visibility.spotted(company)
                    ? KOMECompanyMapProjection.spottedTag(company,visibility.presentationId(company))
                    : KOMECompanyMapProjection.companyTag(company,
                        present>0?present:company.totalPopulation,visibility.soldierCount(company)>0));
            }
        }
        data.setTag("ArmyCompanies", companyList);
        NBTTagList troopList = new NBTTagList();
        for (KOMETileTroopSummary summary : buildTroopSummaries(worldData,visibility,operator).values()) {
            if (summary.hasAnyPopulation()) {
                troopList.appendTag(summary.writeToNBT());
            }
        }
        data.setTag("TroopSummaries", troopList);
        NBTTagList routeEdgeList = new NBTTagList();
        for (KOMEConquestRouteEdge edge : worldData.routeEdges.values()) {
            if (edge != null) {
                routeEdgeList.appendTag(edge.writeToNBT());
            }
        }
        data.setTag("RouteEdges", routeEdgeList);
        NBTTagList waypointLinkList = new NBTTagList();
        for (KOMETileWaypointLink link : worldData.tileWaypointLinksByTileId.values()) {
            if (link != null && link.tileId.length() > 0 && link.lotrWaypointKey.length() > 0) {
                waypointLinkList.appendTag(link.writeToNBT());
            }
        }
        data.setTag("TileWaypointLinks", waypointLinkList);
        NBTTagList buildList = new NBTTagList();
        for (KOMEPlayerBuild build : worldData.builds.values()) {
            if (build != null && build.active && build.markerVisible) buildList.appendTag(buildMarkerTag(build));
        }
        data.setTag("BuildMarkers", buildList);
    }

    private KOMEPacketConquestData(NBTTagCompound data, boolean reset, boolean complete, long serial) {
        this.data = data;
        this.reset = reset;
        this.complete = complete;
        this.publicationSerial = serial;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        decodedPublication = null;
        KOMEPopulationWire.readHeader(buf);
        reset = buf.readBoolean();
        complete = buf.readBoolean();
        publicationSerial = buf.readLong();
        data = KOMEPopulationWire.readNbt(buf);
        if (data == null) throw new IllegalArgumentException("Missing conquest packet data");
        KOMEPopulationWire.requireFullyRead(buf);
        decodedPublication = new KOMEPacketConquestData((NBTTagCompound) data.copy(), reset, complete, publicationSerial);
    }

    /** Wire-decoded packets have already passed all envelope and text validation. */
    private KOMEPacketConquestData copyForPublication() {
        if (decodedPublication != null) return decodedPublication;
        // Preserve validation for locally constructed/direct-handler packets as well.
        return KOMEPopulationWire.copyForPublication(this, KOMEPacketConquestData::new);
    }

    @Override
    public void toBytes(ByteBuf output) {
        byte[] compressed = KOMEPopulationWire.prepareNbt(data);
        KOMEPopulationWire.writePacket(output, buf -> {
            KOMEPopulationWire.writeHeader(buf);
            buf.writeBoolean(reset);
            buf.writeBoolean(complete);
            buf.writeLong(publicationSerial);
            KOMEPopulationWire.writePreparedNbt(buf, compressed);
        });
    }

    private static final String[] SECTIONS = {"ConquestTiles", "FactionCapitals", "ArmyMovements",
        "ArmyCompanies", "TroopSummaries", "RouteEdges", "TileWaypointLinks", "BuildMarkers"};
    // Player equality is entity-ID based: use object identity across reconnect/respawn.
    // Server-thread owned; eviction only causes an extra full publication, never lost updates.
    private static final int MAX_RECIPIENTS = 256;
    private static final Map<EntityPlayerMP, SentSnapshot> SENT =
        new IdentityHashMap<EntityPlayerMP, SentSnapshot>();
    private static long nextPublicationSerial;

    /** Explicit resynchronization, including client world/session replacement. */
    public static void sendChunked(KOMEWorldData worldData, EntityPlayerMP player) {
        send(worldData, player, true);
    }

    /** Broadcast path: compare the public projection, not dirty flags or mutable world records. */
    public static void sendIfChanged(KOMEWorldData worldData, EntityPlayerMP player) {
        send(worldData, player, false);
    }

    /** Server-thread correlation for a map-origin detail response. Ensures its source
     * snapshot is queued first and never reuses an older permission projection. */
    public static long mapDetailPublicationSerial(KOMEWorldData worldData, EntityPlayerMP player) {
        sendIfChanged(worldData, player);
        SentSnapshot current = SENT.get(player);
        return current != null && !current.dispatch.failed.get() ? current.serial : -1L;
    }

    public static void forgetRecipient(EntityPlayerMP player) { SENT.remove(player); }
    public static void clearSentSnapshots() { SENT.clear(); }

    private static void send(KOMEWorldData worldData, EntityPlayerMP player, boolean force) {
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        String faction = pledge == null ? "" : KOMEAlliance.normalizeFactionKey(pledge.codeName());
        UUID viewer = player.getUniqueID();
        boolean operator = KOMEAdminMapVision.enabled(player);
        NBTTagCompound projection = new KOMEPacketConquestData(worldData, viewer, faction, operator).data;
        SentSnapshot previous = SENT.get(player);
        if (!force && previous != null && !previous.dispatch.failed.get()
                && previous.matches(worldData, player, pledge, operator)
                && previous.data.equals(projection)) return;

        // A failed/partial dispatch must retry a complete reset, even if data later reverts.
        SENT.remove(player);
        if (nextPublicationSerial == Long.MAX_VALUE) throw new IllegalStateException("Conquest publication serial exhausted");
        SentSnapshot publication = new SentSnapshot(worldData, player, pledge, operator, projection,
            ++nextPublicationSerial);
        sendProjection(projection, publication);
        if (SENT.size() >= MAX_RECIPIENTS) SENT.remove(SENT.keySet().iterator().next());
        SENT.put(player, publication);
    }

    private static void sendProjection(NBTTagCompound projection, SentSnapshot publication) {
        int lastSection = -1;
        for (int i = 0; i < SECTIONS.length; i++)
            if (projection.getTagList(SECTIONS[i], 10).tagCount() > 0) lastSection = i;
        if (lastSection < 0) {
            dispatch(new KOMEPacketConquestData(new NBTTagCompound(), true, true, publication.serial), publication);
            return;
        }
        boolean first = true;
        for (int section = 0; section <= lastSection; section++) {
            NBTTagList rows = projection.getTagList(SECTIONS[section], 10);
            for (int start = 0; start < rows.tagCount(); start += MAX_ENTRIES_PER_PACKET) {
                int end = Math.min(rows.tagCount(), start + MAX_ENTRIES_PER_PACKET);
                NBTTagList chunkList = new NBTTagList();
                for (int i = start; i < end; i++) chunkList.appendTag(rows.getCompoundTagAt(i).copy());
                NBTTagCompound chunk = new NBTTagCompound();
                chunk.setTag(SECTIONS[section], chunkList);
                dispatch(new KOMEPacketConquestData(chunk, first,
                    section == lastSection && end == rows.tagCount(), publication.serial), publication);
                first = false;
            }
        }
    }

    /** Use Forge's encoder, but observe the real connection write, not its embedded handoff. */
    private static void dispatch(KOMEPacketConquestData packet, SentSnapshot publication) {
        NetHandlerPlayServer connection = publication.connection;
        if (connection == null || connection.netManager == null || !connection.netManager.isChannelOpen()) {
            publication.dispatch.failed.set(true);
            return;
        }
        Packet encoded = KOMEPacketHandler.network.getPacketFrom(packet);
        if (encoded == null) throw new IllegalStateException("Conquest packet encoding produced no packet");
        connection.netManager.scheduleOutboundPacket(encoded, publication.dispatch);
    }

    /**
     * Transport-only receipt: callbacks never access recipients, sessions or the server-owned map.
     * A receipt belongs to exactly one publication. Retired receipts cannot affect newer entries.
     * Pending identical requests coalesce; any observed failure permits the next request to retry.
     * Successful writes are NOT acknowledgements of client decoding/application.
     */
    private static final class DispatchReceipt implements GenericFutureListener<Future<? super Void>> {
        private final AtomicBoolean failed = new AtomicBoolean();
        @Override public void operationComplete(Future<? super Void> future) {
            if (!future.isSuccess()) failed.set(true);
        }
    }

    private static final class SentSnapshot {
        private final DispatchReceipt dispatch = new DispatchReceipt();
        private final KOMEWorldData worldData;
        private final World world;
        private final NetHandlerPlayServer connection;
        private final LOTRFaction pledge;
        private final int dimension;
        private final boolean operator;
        private final long serial;
        private final NBTTagCompound data;

        private SentSnapshot(KOMEWorldData worldData, EntityPlayerMP player,
                LOTRFaction pledge, boolean operator, NBTTagCompound data, long serial) {
            this.worldData = worldData; this.world = player.worldObj;
            this.connection = player.playerNetServerHandler; this.dimension = player.dimension;
            this.pledge = pledge; this.operator = operator;
            this.serial = serial;
            this.data = data; // Detached projection; chunks receive their own copies.
        }

        private boolean matches(KOMEWorldData worldData, EntityPlayerMP player,
                LOTRFaction pledge, boolean operator) {
            return this.worldData == worldData && world == player.worldObj
                && connection == player.playerNetServerHandler && dimension == player.dimension
                && this.pledge == pledge && this.operator == operator;
        }
    }

    /** Fully validated data from one ordered wire chunk; never mutates client state directly. */
    public static final class PublicationChunk {
        public final boolean reset;
        public final boolean complete;
        public final long publicationSerial;
        public final Map<String, KOMEArmyCompany> armyCompanies;
        public final Map<String, KOMEConquestTile> conquestTiles;
        public final Map<String, KOMEArmyMovementOrder> armyMovements;
        public final Map<String, KOMETileTroopSummary> troopSummaries;
        public final Map<String, KOMEConquestRouteEdge> routeEdges;
        public final Map<String, KOMETileWaypointLink> tileWaypointLinksByTileId;
        public final Map<String, KOMEPlayerBuild> builds;
        public final Map<String, String> capitalTilesByFaction;

        private PublicationChunk(boolean reset, boolean complete, long publicationSerial,
                Map<String, KOMEArmyCompany> armyCompanies,
                Map<String, KOMEConquestTile> conquestTiles,
                Map<String, KOMEArmyMovementOrder> armyMovements,
                Map<String, KOMETileTroopSummary> troopSummaries,
                Map<String, KOMEConquestRouteEdge> routeEdges,
                Map<String, KOMETileWaypointLink> tileWaypointLinksByTileId,
                Map<String, KOMEPlayerBuild> builds,
                Map<String, String> capitalTilesByFaction) {
            this.reset = reset;
            this.complete = complete;
            this.publicationSerial = publicationSerial;
            this.armyCompanies = armyCompanies;
            this.conquestTiles = conquestTiles;
            this.armyMovements = armyMovements;
            this.troopSummaries = troopSummaries;
            this.routeEdges = routeEdges;
            this.tileWaypointLinksByTileId = tileWaypointLinksByTileId;
            this.builds = builds;
            this.capitalTilesByFaction = capitalTilesByFaction;
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestData, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestData message, MessageContext ctx) {
            final KOMEPacketConquestData snapshot = message.copyForPublication();
            Map<String, KOMEArmyCompany> armyCompanies = new HashMap<String, KOMEArmyCompany>();
            Map<String, KOMEConquestTile> conquestTiles = new HashMap<String, KOMEConquestTile>();
            Map<String, KOMEArmyMovementOrder> armyMovements = new HashMap<String, KOMEArmyMovementOrder>();
            Map<String, KOMETileTroopSummary> troopSummaries = new HashMap<String, KOMETileTroopSummary>();
            Map<String, KOMEConquestRouteEdge> routeEdges = new HashMap<String, KOMEConquestRouteEdge>();
            Map<String, KOMETileWaypointLink> tileWaypointLinksByTileId = new HashMap<String, KOMETileWaypointLink>();
            Map<String, KOMEPlayerBuild> builds = new HashMap<String, KOMEPlayerBuild>();
            Map<String, String> capitalTilesByFaction = new HashMap<String, String>();
            NBTTagList capitalList = KOMEPopulationWire.compoundRows(snapshot.data, "FactionCapitals");
            for (int i = 0; i < KOMEPopulationWire.count(capitalList.tagCount()); i++) {
                NBTTagCompound row = capitalList.getCompoundTagAt(i);
                String faction = KOMEAlliance.normalizeFactionKey(row.getString("Faction"));
                String tile = KOMEConquestTile.normalizeId(row.getString("Tile"));
                if (KOMEAlliance.allFactionKeys().contains(faction)
                        && KOMEConquestTile.isCanonicalTileId(tile))
                    capitalTilesByFaction.put(faction, tile);
            }
            NBTTagList companyList = KOMEPopulationWire.compoundRows(snapshot.data, "ArmyCompanies");
            for (int i = 0; i < KOMEPopulationWire.count(companyList.tagCount()); i++) {
                KOMEArmyCompany company = KOMECompanyMapProjection.readCompany(companyList.getCompoundTagAt(i));
                if (company.id.length() > 0) {
                    armyCompanies.put(company.id, company);
                }
            }
            NBTTagList list = KOMEPopulationWire.compoundRows(snapshot.data, "ConquestTiles");
            for (int i = 0; i < KOMEPopulationWire.count(list.tagCount()); i++) {
                KOMEConquestTile tile = new KOMEConquestTile("");
                tile.readFromNBT(list.getCompoundTagAt(i));
                if (!tile.id.isEmpty() && !tile.projectRulingFaction().isEmpty()) {
                    conquestTiles.put(tile.id, tile);
                }
            }
            NBTTagList movementList = KOMEPopulationWire.compoundRows(snapshot.data, "ArmyMovements");
            for (int i = 0; i < KOMEPopulationWire.count(movementList.tagCount()); i++) {
                KOMEArmyMovementOrder order = KOMECompanyMapProjection.readMovement(movementList.getCompoundTagAt(i));
                if (order.id.length() > 0 && order.isMoving()) {
                    armyMovements.put(order.id, order);
                }
            }
            NBTTagList troopList = KOMEPopulationWire.compoundRows(snapshot.data, "TroopSummaries");
            for (int i = 0; i < KOMEPopulationWire.count(troopList.tagCount()); i++) {
                KOMETileTroopSummary summary = new KOMETileTroopSummary();
                summary.readFromNBT(troopList.getCompoundTagAt(i));
                if (summary.tileId.length() > 0) {
                    troopSummaries.put(summary.tileId, summary);
                }
            }
            NBTTagList routeEdgeList = KOMEPopulationWire.compoundRows(snapshot.data, "RouteEdges");
            for (int i = 0; i < KOMEPopulationWire.count(routeEdgeList.tagCount()); i++) {
                KOMEConquestRouteEdge edge = new KOMEConquestRouteEdge();
                edge.readFromNBT(routeEdgeList.getCompoundTagAt(i));
                if (edge.fromTile.length() > 0 && edge.toTile.length() > 0) {
                    routeEdges.put(KOMEConquestRouteEdge.key(edge.fromTile, edge.toTile), edge);
                }
            }
            NBTTagList waypointLinkList = KOMEPopulationWire.compoundRows(snapshot.data, "TileWaypointLinks");
            for (int i = 0; i < KOMEPopulationWire.count(waypointLinkList.tagCount()); i++) {
                KOMETileWaypointLink link = new KOMETileWaypointLink();
                link.readFromNBT(waypointLinkList.getCompoundTagAt(i));
                if (link.tileId.length() > 0 && link.lotrWaypointKey.length() > 0) {
                    tileWaypointLinksByTileId.put(link.tileId, link);
                }
            }
            NBTTagList buildList = KOMEPopulationWire.compoundRows(snapshot.data, "BuildMarkers");
            for (int i = 0; i < KOMEPopulationWire.count(buildList.tagCount()); i++) {
                KOMEPlayerBuild build = readBuildMarkerTag(buildList.getCompoundTagAt(i));
                if (build.id.length() > 0 && build.active && build.markerVisible) {
                    builds.put(build.id, build);
                }
            }
            kome.common.KOMEAddon.proxy.acceptConquestSnapshotChunk(new PublicationChunk(
                snapshot.reset, snapshot.complete, snapshot.publicationSerial, armyCompanies, conquestTiles,
                armyMovements, troopSummaries, routeEdges, tileWaypointLinksByTileId,
                builds, capitalTilesByFaction), ctx == null ? null : ctx.getClientHandler());
            return null;
        }
    }

    static NBTTagCompound buildMarkerTag(KOMEPlayerBuild build) {
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setString("Id", build.id);
        nbt.setString("BuildType", build.type.key);
        nbt.setString("DisplayName", build.displayName);
        nbt.setString("TileId", build.tileId);
        nbt.setInteger("Dimension", build.dimension);
        nbt.setDouble("X", build.x);
        nbt.setDouble("Y", build.y);
        nbt.setDouble("Z", build.z);
        nbt.setString("PopulationFaction", build.populationFaction);
        nbt.setBoolean("Active", true);
        nbt.setBoolean("MarkerVisible", true);
        nbt.setString("MarkerLabel", build.markerLabel);
        return nbt;
    }

    /** Marker-only projection. Never deserialize a partial marker through the strict persistence reader. */
    static KOMEPlayerBuild readBuildMarkerTag(NBTTagCompound nbt) {
        KOMEPlayerBuild marker = new KOMEPlayerBuild();
        marker.id = nbt.getString("Id");
        marker.type = kome.common.data.KOMEBuildType.forKey(nbt.getString("BuildType"));
        marker.displayName = nbt.getString("DisplayName");
        marker.tileId = nbt.getString("TileId");
        marker.dimension = nbt.getInteger("Dimension");
        marker.x = nbt.getDouble("X");
        marker.y = nbt.getDouble("Y");
        marker.z = nbt.getDouble("Z");
        marker.populationFaction = nbt.getString("PopulationFaction");
        marker.active = nbt.getBoolean("Active");
        marker.markerVisible = nbt.getBoolean("MarkerVisible");
        marker.markerLabel = nbt.getString("MarkerLabel");
        return marker;
    }

    private static Map<String, KOMETileTroopSummary> buildTroopSummaries(KOMEWorldData worldData,
            KOMECompanyMapProjection.View visibility,boolean operator) {
        Map<String, KOMETileTroopSummary> summaries = new HashMap<String, KOMETileTroopSummary>();
        Map<String, kome.common.data.KOMEPopulationProjection> populations = kome.common.data.KOMEPopulationProjection.all(worldData);
        for (KOMEConquestTile tile : worldData.conquestTiles.values()) {
            if (tile == null || tile.projectRulingFaction().isEmpty()) {
                continue;
            }
            String rulingFaction = tile.projectRulingFaction();
            KOMETileTroopSummary summary = getSummary(summaries, tile.id);
            summary.ownerFaction = rulingFaction;
            summary.population = populations.get(rulingFaction);
        }
        // Never serialize the former mixed, global tile census to an unobserving recipient.
        // Use verified committed company membership under the same disclosure view as shields.
        for (KOMEArmyCompany company : worldData.armyCompanies.values()) {
            if (company == null || !visibility.directlyVisible(company)) continue;
            KOMETileTroopSummary allowed=visibility.stationedSummary(company.currentTile);
            if(allowed==null)continue;
            KOMETileTroopSummary summary=getSummary(summaries,company.currentTile);
            summary.stationedPop=allowed.stationedPop;
            summary.stationedOffensivePop=allowed.stationedOffensivePop;
            summary.stationedMountedPop=allowed.stationedMountedPop;
            summary.stationedDefensivePop=allowed.stationedDefensivePop;
        }
        if(operator) {
            // Retain the administrative census for independent Campaign units, without
            // counting company members already supplied by the shared presence view.
            for(KOMEHiredUnitRecord record:worldData.hiredUnits.values()) {
                if(!kome.common.data.KOMEHiredUnitClassification.isCampaignUnit(record)
                        || record.farmhand || record.currentTile==null || record.currentTile.isEmpty()
                        || record.movementOrderId!=null && !record.movementOrderId.isEmpty())continue;
                KOMEArmyCompany recordCompany=worldData.armyCompanies.get(record.companyId);
                if(recordCompany!=null && KOMECompanyMapProjection.presentMember(worldData,recordCompany,record))
                    continue;
                KOMETileTroopSummary summary=getSummary(summaries,record.currentTile);
                summary.stationedPop+=record.cost;
                if(record.type==KOMEPopulationType.DEFENSIVE)summary.stationedDefensivePop+=record.cost;
                else {summary.stationedOffensivePop+=record.cost;if(record.mounted)summary.stationedMountedPop+=record.cost;}
            }
        }
        for (KOMEArmyMovementOrder order : worldData.armyMovements.values()) {
            if (order == null || !order.isMoving()) {
                continue;
            }
            KOMEArmyCompany company=worldData.armyCompanies.get(order.companyId);
            if(company==null || !visibility.directlyVisible(company)
                    || !KOMEAlliance.normalizeFactionKey(order.ownerFaction).equals(
                        KOMEAlliance.normalizeFactionKey(company.faction)))continue;
            kome.common.data.KOMECampaignUnitTotals campaign =
                kome.common.data.KOMECampaignUnitTotals.of(worldData, order.units);
            if (campaign.unitCount == 0) {
                continue;
            }
            if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)) {
                // Verified committed members were already counted by the tile-presence view.
                // Order.units is a route manifest, not a second stationed census.
                continue;
            }
            KOMETileTroopSummary destination = getSummary(summaries, activeStepDestination(order));
            destination.incomingPop += campaign.population;
            destination.incomingMovementCount++;
            KOMETileTroopSummary origin = getSummary(summaries, activeStepOrigin(order));
            origin.movingPop += campaign.population;
            origin.outgoingMovementCount++;
        }
        return summaries;
    }

    private static String activeStepOrigin(KOMEArmyMovementOrder order) {
        if (order == null) {
            return "";
        }
        String current = KOMEConquestTile.normalizeId(order.currentTile);
        if (current.length() > 0) {
            return current;
        }
        String value = KOMEConquestTile.normalizeId(order.currentStepOriginTile);
        if (value.length() > 0) {
            return value;
        }
        return order.routeTiles.size() > order.currentRouteIndex
            ? KOMEConquestTile.normalizeId(order.routeTiles.get(order.currentRouteIndex))
            : KOMEConquestTile.normalizeId(order.originTile);
    }

    private static String activeStepDestination(KOMEArmyMovementOrder order) {
        if (order == null) {
            return "";
        }
        String next = KOMEConquestTile.normalizeId(order.nextTile);
        if (next.length() > 0) {
            return next;
        }
        String value = KOMEConquestTile.normalizeId(order.currentStepDestinationTile);
        if (value.length() > 0) {
            return value;
        }
        return order.routeTiles.size() > order.nextRouteIndex
            ? KOMEConquestTile.normalizeId(order.routeTiles.get(order.nextRouteIndex))
            : KOMEConquestTile.normalizeId(order.destinationTile);
    }

    private static KOMETileTroopSummary getSummary(Map<String, KOMETileTroopSummary> summaries, String tileId) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        KOMETileTroopSummary summary = summaries.get(tile);
        if (summary == null) {
            summary = new KOMETileTroopSummary();
            summary.tileId = tile;
            summaries.put(tile, summary);
        }
        return summary;
    }
}
