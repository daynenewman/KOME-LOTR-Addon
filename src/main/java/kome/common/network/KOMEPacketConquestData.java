package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEConquestRouteEdge;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEPlayerBuild;
import kome.common.data.KOMETilePopulation;
import kome.common.data.KOMETileWaypointLink;
import kome.common.data.KOMETileTroopSummary;
import kome.common.data.KOMEWorldData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KOMEPacketConquestData implements IMessage {
    private static final int MAX_ENTRIES_PER_PACKET = 48;

    public NBTTagCompound data = new NBTTagCompound();
    public boolean reset;
    public boolean complete = true;

    public KOMEPacketConquestData() {
    }

    public KOMEPacketConquestData(KOMEWorldData worldData) {
        NBTTagList list = new NBTTagList();
        for (KOMEConquestTile tile : worldData.conquestTiles.values()) {
            if (!tile.projectRulingFaction().isEmpty()) {
                list.appendTag(tile.projectToNBT());
            }
        }
        data.setTag("ConquestTiles", list);
        NBTTagList movementList = new NBTTagList();
        for (KOMEArmyMovementOrder order : worldData.armyMovements.values()) {
            if (order != null && order.isMoving()) {
                movementList.appendTag(order.writeToNBT());
            }
        }
        data.setTag("ArmyMovements", movementList);
        NBTTagList companyList = new NBTTagList();
        for (KOMEArmyCompany company : worldData.armyCompanies.values()) {
            if (company != null) {
                companyList.appendTag(company.writeToNBT());
            }
        }
        data.setTag("ArmyCompanies", companyList);
        NBTTagList troopList = new NBTTagList();
        for (KOMETileTroopSummary summary : buildTroopSummaries(worldData).values()) {
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

    private KOMEPacketConquestData(NBTTagCompound data, boolean reset, boolean complete) {
        this.data = data;
        this.reset = reset;
        this.complete = complete;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        KOMEPopulationWire.readHeader(buf);
        reset = buf.readBoolean();
        complete = buf.readBoolean();
        data = KOMEPopulationWire.readNbt(buf);
        if (data == null) throw new IllegalArgumentException("Missing conquest packet data");
        KOMEPopulationWire.requireFullyRead(buf);
    }

    @Override
    public void toBytes(ByteBuf output) {
        byte[] compressed = KOMEPopulationWire.prepareNbt(data);
        KOMEPopulationWire.writePacket(output, buf -> {
            KOMEPopulationWire.writeHeader(buf);
            buf.writeBoolean(reset);
            buf.writeBoolean(complete);
            KOMEPopulationWire.writePreparedNbt(buf, compressed);
        });
    }

    public static void sendChunked(KOMEWorldData worldData, net.minecraft.entity.player.EntityPlayerMP player) {
        List tileTags = new ArrayList();
        for (KOMEConquestTile tile : worldData.conquestTiles.values()) {
            if (!tile.projectRulingFaction().isEmpty()) {
                tileTags.add(tile.projectToNBT());
            }
        }

        List movementTags = new ArrayList();
        for (KOMEArmyMovementOrder order : worldData.armyMovements.values()) {
            if (order != null && order.isMoving()) {
                movementTags.add(order.writeToNBT());
            }
        }

        List companyTags = new ArrayList();
        for (KOMEArmyCompany company : worldData.armyCompanies.values()) {
            if (company != null) {
                companyTags.add(company.writeToNBT());
            }
        }

        List troopTags = new ArrayList();
        for (KOMETileTroopSummary summary : buildTroopSummaries(worldData).values()) {
            if (summary.hasAnyPopulation()) {
                troopTags.add(summary.writeToNBT());
            }
        }
        List routeEdgeTags = new ArrayList();
        for (KOMEConquestRouteEdge edge : worldData.routeEdges.values()) {
            if (edge != null) {
                routeEdgeTags.add(edge.writeToNBT());
            }
        }
        List waypointLinkTags = new ArrayList();
        for (KOMETileWaypointLink link : worldData.tileWaypointLinksByTileId.values()) {
            if (link != null && link.tileId.length() > 0 && link.lotrWaypointKey.length() > 0) {
                waypointLinkTags.add(link.writeToNBT());
            }
        }
        List buildMarkerTags = new ArrayList();
        for (KOMEPlayerBuild build : worldData.builds.values()) {
            if (build != null && build.active && build.markerVisible) buildMarkerTags.add(buildMarkerTag(build));
        }

        int total = tileTags.size() + movementTags.size() + companyTags.size() + troopTags.size()
            + routeEdgeTags.size() + waypointLinkTags.size() + buildMarkerTags.size();
        if (total == 0) {
            KOMEPacketHandler.network.sendTo(new KOMEPacketConquestData(new NBTTagCompound(), true, true), player);
            return;
        }

        boolean first = true;
        first = sendListChunks(player, "ConquestTiles", tileTags, first, movementTags.isEmpty() && companyTags.isEmpty() && troopTags.isEmpty() && routeEdgeTags.isEmpty() && waypointLinkTags.isEmpty() && buildMarkerTags.isEmpty());
        first = sendListChunks(player, "ArmyMovements", movementTags, first, companyTags.isEmpty() && troopTags.isEmpty() && routeEdgeTags.isEmpty() && waypointLinkTags.isEmpty() && buildMarkerTags.isEmpty());
        first = sendListChunks(player, "ArmyCompanies", companyTags, first, troopTags.isEmpty() && routeEdgeTags.isEmpty() && waypointLinkTags.isEmpty() && buildMarkerTags.isEmpty());
        first = sendListChunks(player, "TroopSummaries", troopTags, first, routeEdgeTags.isEmpty() && waypointLinkTags.isEmpty() && buildMarkerTags.isEmpty());
        first = sendListChunks(player, "RouteEdges", routeEdgeTags, first, waypointLinkTags.isEmpty() && buildMarkerTags.isEmpty());
        first = sendListChunks(player, "TileWaypointLinks", waypointLinkTags, first, buildMarkerTags.isEmpty());
        sendListChunks(player, "BuildMarkers", buildMarkerTags, first, true);
    }

    private static boolean sendListChunks(net.minecraft.entity.player.EntityPlayerMP player, String key, List tags, boolean first, boolean finalSection) {
        if (tags.isEmpty()) {
            return first;
        }
        for (int start = 0; start < tags.size(); start += MAX_ENTRIES_PER_PACKET) {
            int end = Math.min(tags.size(), start + MAX_ENTRIES_PER_PACKET);
            NBTTagCompound chunkData = new NBTTagCompound();
            NBTTagList chunkList = new NBTTagList();
            for (int i = start; i < end; i++) {
                chunkList.appendTag((NBTTagCompound) tags.get(i));
            }
            chunkData.setTag(key, chunkList);
            boolean complete = finalSection && end >= tags.size();
            KOMEPacketHandler.network.sendTo(new KOMEPacketConquestData(chunkData, first, complete), player);
            first = false;
        }
        return first;
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestData, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestData message, MessageContext ctx) {
            final KOMEPacketConquestData snapshot = KOMEPopulationWire.copyForPublication(message, KOMEPacketConquestData::new);
            Map<String, KOMEArmyCompany> armyCompanies = new HashMap<String, KOMEArmyCompany>();
            Map<String, KOMEConquestTile> conquestTiles = new HashMap<String, KOMEConquestTile>();
            Map<String, KOMEArmyMovementOrder> armyMovements = new HashMap<String, KOMEArmyMovementOrder>();
            Map<String, KOMETileTroopSummary> troopSummaries = new HashMap<String, KOMETileTroopSummary>();
            Map<String, KOMEConquestRouteEdge> routeEdges = new HashMap<String, KOMEConquestRouteEdge>();
            Map<String, KOMETileWaypointLink> tileWaypointLinksByTileId = new HashMap<String, KOMETileWaypointLink>();
            Map<String, KOMEPlayerBuild> builds = new HashMap<String, KOMEPlayerBuild>();
            NBTTagList companyList = KOMEPopulationWire.compoundRows(snapshot.data, "ArmyCompanies");
            for (int i = 0; i < KOMEPopulationWire.count(companyList.tagCount()); i++) {
                KOMEArmyCompany company = new KOMEArmyCompany();
                company.readFromNBT(companyList.getCompoundTagAt(i));
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
                KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
                order.readFromNBT(movementList.getCompoundTagAt(i));
                if (order.id.length() > 0 && order.isMoving()) {
                    armyMovements.put(order.id, order);
                }
            }
            NBTTagList troopList = KOMEPopulationWire.compoundRows(snapshot.data, "TroopSummaries");
            for (int i = 0; i < KOMEPopulationWire.count(troopList.tagCount()); i++) {
                KOMETileTroopSummary summary = new KOMETileTroopSummary();
                summary.readFromNBT(troopList.getCompoundTagAt(i));
                if (summary.tileId.length() > 0 && summary.hasAnyPopulation()) {
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
            kome.common.KOMEAddon.proxy.enqueueClientTask(() -> {
                if (snapshot.reset) KOMEClientData.INSTANCE.armyCompanies.clear();
                KOMEClientData.INSTANCE.armyCompanies.putAll(armyCompanies);
                if (snapshot.reset) KOMEClientData.INSTANCE.conquestTiles.clear();
                KOMEClientData.INSTANCE.conquestTiles.putAll(conquestTiles);
                if (snapshot.reset) KOMEClientData.INSTANCE.armyMovements.clear();
                KOMEClientData.INSTANCE.armyMovements.putAll(armyMovements);
                if (snapshot.reset) KOMEClientData.INSTANCE.troopSummaries.clear();
                KOMEClientData.INSTANCE.troopSummaries.putAll(troopSummaries);
                if (snapshot.reset) KOMEClientData.INSTANCE.routeEdges.clear();
                KOMEClientData.INSTANCE.routeEdges.putAll(routeEdges);
                if (snapshot.reset) KOMEClientData.INSTANCE.tileWaypointLinksByTileId.clear();
                KOMEClientData.INSTANCE.tileWaypointLinksByTileId.putAll(tileWaypointLinksByTileId);
                if (snapshot.reset) KOMEClientData.INSTANCE.builds.clear();
                KOMEClientData.INSTANCE.builds.putAll(builds);
                if (snapshot.complete) {
                    KOMEClientData.INSTANCE.conquestRevision++;
                }
            });
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

    private static Map<String, KOMETileTroopSummary> buildTroopSummaries(KOMEWorldData worldData) {
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
        for (KOMEHiredUnitRecord record : worldData.hiredUnits.values()) {
            if (record == null || record.currentTile == null || record.currentTile.length() == 0 || record.farmhand) {
                continue;
            }
            String tile = KOMEConquestTile.normalizeId(record.currentTile);
            KOMETileTroopSummary summary = getSummary(summaries, tile);
            if (record.movementOrderId != null && record.movementOrderId.length() > 0) {
                continue;
            } else {
                summary.stationedPop += record.cost;
                if (record.type == KOMEPopulationType.DEFENSIVE) {
                    summary.stationedDefensivePop += record.cost;
                } else {
                    summary.stationedOffensivePop += record.cost;
                    if (record.mounted) {
                        summary.stationedMountedPop += record.cost;
                    }
                }
            }
        }
        for (KOMEArmyMovementOrder order : worldData.armyMovements.values()) {
            if (order == null || !order.isMoving()) {
                continue;
            }
            if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)) {
                KOMETileTroopSummary waiting = getSummary(summaries, activeStepOrigin(order));
                waiting.stationedPop += order.population;
                waiting.stationedOffensivePop += order.population;
                waiting.stationedMountedPop += order.mountedPopulation;
                continue;
            }
            KOMETileTroopSummary destination = getSummary(summaries, activeStepDestination(order));
            destination.incomingPop += order.population;
            destination.incomingMovementCount++;
            KOMETileTroopSummary origin = getSummary(summaries, activeStepOrigin(order));
            origin.movingPop += order.population;
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
