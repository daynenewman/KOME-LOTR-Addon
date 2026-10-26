package kome.common.network;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.KOMEAddon;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEHaltedUnitProtection;
import kome.common.data.KOMEHiredUnitClassification;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEUnitMapMarker;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMEAlliance;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import lotr.common.LOTRDimension;
import lotr.common.entity.npc.LOTREntityNPC;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KOMEPacketUnitMapMarkers implements IMessage {
    public List markers = new ArrayList();

    public KOMEPacketUnitMapMarkers() {
    }

    public KOMEPacketUnitMapMarkers(List markers) {
        this.markers = markers == null ? new ArrayList() : markers;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        markers = new ArrayList();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            KOMEUnitMapMarker marker = new KOMEUnitMapMarker();
            marker.readFromBytes(buf);
            markers.add(marker);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(markers.size());
        for (Object object : markers) {
            ((KOMEUnitMapMarker) object).writeToBytes(buf);
        }
    }

    public static void sendToAll(KOMEWorldData data) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.getConfigurationManager() == null) {
            return;
        }
        for (Object object : server.getConfigurationManager().playerEntityList) {
            if (object instanceof EntityPlayerMP) {
                sendToPlayer(data, (EntityPlayerMP) object);
            }
        }
    }

    public static void sendToPlayer(KOMEWorldData data, EntityPlayerMP player) {
        if (data == null || player == null) {
            return;
        }
        KOMEPacketHandler.network.sendTo(new KOMEPacketUnitMapMarkers(buildMarkers(data, player)), player);
    }

    private static List buildMarkers(KOMEWorldData data, EntityPlayerMP player) {
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.worldServers == null) {
            return new ArrayList();
        }
        UUID viewer = KOMEReflection.getEntityUUID(player);
        boolean admin = KOMEAdminMapVision.enabled(player);
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        String faction = pledge == null ? "" : KOMEAlliance.normalizeFactionKey(pledge.codeName());
        return buildMarkers(data, viewer, faction, admin, server.worldServers);
    }

    /** The publication builder, separated from transport for deterministic unload/reload fixtures. */
    static List buildMarkers(KOMEWorldData data, UUID viewer, String faction, boolean admin, WorldServer[] worlds) {
        Map companyMarkers = new LinkedHashMap();
        KOMECompanyMapProjection.View visibility=KOMECompanyMapProjection.view(data,viewer,faction,admin);
        // Presence is a recipient-filtered projection of committed members, never an NPC census.
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (visibility.soldierCount(company)<=0
                    || !visibility.visible(company)) continue;
            CompanyMarkerBuilder builder = new CompanyMarkerBuilder(
                LOTRDimension.MIDDLE_EARTH.dimensionID, visibility.presentationId(company), company,
                visibility.spotted(company));
            for (UUID id : new java.util.HashSet<UUID>(company.units)) {
                KOMEHiredUnitRecord record = data.hiredUnits.get(id);
                if (isCurrentCompanyMember(data, company, record)) builder.include(record);
            }
            if (builder.soldierCount > 0) companyMarkers.put(company.id, builder);
        }
        for (WorldServer world : worlds) {
            if (world == null || world.provider == null || world.provider.dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID) {
                continue;
            }
            for (Object object : world.loadedEntityList) {
                if (!(object instanceof LOTREntityNPC)) {
                    continue;
                }
                LOTREntityNPC npc = (LOTREntityNPC) object;
                UUID entityId = KOMEReflection.getEntityUUID(npc);
                KOMEHiredUnitRecord record = data.hiredUnits.get(entityId);
                KOMEArmyCompany company = record == null ? null : data.armyCompanies.get(record.companyId);
                if (!visibility.directlyVisible(company)
                        || !isCurrentCompanyMember(data, company, record) || npc.isDead) {
                    continue;
                }
                String key = record.companyId;
                CompanyMarkerBuilder builder = (CompanyMarkerBuilder) companyMarkers.get(key);
                if (builder != null) builder.add(npc, record);
            }
        }
        List markers = new ArrayList();
        for (Object object : companyMarkers.values()) {
            KOMEUnitMapMarker marker = ((CompanyMarkerBuilder) object).toMarker();
            if (marker != null) {
                markers.add(marker);
            }
        }
        return markers;
    }

    static boolean isStrategicMarkerRecord(KOMEHiredUnitRecord record) {
        return KOMEHiredUnitClassification.isCampaignUnit(record)
            && !record.farmhand && !record.isMoving()
            && record.companyId != null && record.companyId.length() > 0;
    }

    private static boolean isCurrentCompanyMember(KOMEWorldData data, KOMEArmyCompany company,
            KOMEHiredUnitRecord record) {
        return KOMECompanyMapProjection.presentMember(data,company,record);
    }

    private static class CompanyMarkerBuilder {
        private final int dimensionId;
        private final String companyId;
        private final KOMEArmyCompany company;
        private final boolean spotted;
        private int soldierCount;
        private int campaignPopulation;
        private int campaignMountedPopulation;
        private final java.util.Set<UUID> loaded = new java.util.HashSet<UUID>();
        private double weightedX;
        private double weightedY;
        private double weightedZ;
        private double weight;
        private int unitCount;
        private boolean allHaltedProtected = true;

        CompanyMarkerBuilder(int dimensionId, String companyId, KOMEArmyCompany company,boolean spotted) {
            this.dimensionId = dimensionId;
            this.companyId = companyId == null ? "" : companyId;
            this.company = company;
            this.spotted = spotted;
        }

        void include(KOMEHiredUnitRecord record) {
            soldierCount++;
            campaignPopulation = (int)Math.min(Integer.MAX_VALUE, (long)campaignPopulation + Math.max(0,record.cost));
            if(record.mounted) campaignMountedPopulation = (int)Math.min(Integer.MAX_VALUE,
                (long)campaignMountedPopulation + Math.max(0,record.cost));
        }

        void add(LOTREntityNPC npc, KOMEHiredUnitRecord record) {
            if (!loaded.add(record.entity)) return;
            int cost = Math.max(1, record.cost);
            weightedX += npc.posX * cost;
            weightedY += npc.posY * cost;
            weightedZ += npc.posZ * cost;
            weight += cost;
            unitCount++;
            if (!KOMEHaltedUnitProtection.isProtected(npc)) {
                allHaltedProtected = false;
            }
        }

        KOMEUnitMapMarker toMarker() {
            if (soldierCount <= 0) {
                return null;
            }
            KOMEUnitMapMarker marker = new KOMEUnitMapMarker();
            marker.entityId = companyId;
            marker.companyName = spotted ? "" : company.name;
            if (!spotted && (marker.companyName == null || marker.companyName.length() == 0)) {
                marker.companyName = "Company";
            }
            marker.currentTile = KOMEConquestTile.normalizeId(company.currentTile);
            marker.unitName = spotted ? "" : marker.companyName;
            marker.unitCount = spotted ? 0 : unitCount;
            marker.soldierCount = soldierCount;
            marker.dimensionId = dimensionId;
            // Zero loaded units means tile-level presence. The client uses its existing
            // tile presentation anchor and must never interpret these defaults as a physical fix.
            if (weight > 0) {
                marker.x = weightedX / weight;
                marker.y = weightedY / weight;
                marker.z = weightedZ / weight;
            }
            marker.population = spotted ? 0 : campaignPopulation;
            marker.mounted = !spotted && campaignMountedPopulation == campaignPopulation;
            marker.haltedProtected = !spotted && unitCount > 0 && allHaltedProtected;
            return marker;
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketUnitMapMarkers, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketUnitMapMarkers message, MessageContext ctx) {
            KOMEPacketUnitMapMarkers copy=KOMEPopulationWire.copyForPublication(message,KOMEPacketUnitMapMarkers::new);
            try {KOMEAddon.proxy.acceptUnitMapMarkers(copy.markers,ctx.getClientHandler());}
            catch(java.util.concurrent.RejectedExecutionException closed) { /* Never publish into a closed/full session. */ }
            return null;
        }
    }
}
