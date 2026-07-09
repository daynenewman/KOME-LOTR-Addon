package kome.common.network;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEHaltedUnitProtection;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEUnitMapMarker;
import kome.common.data.KOMEWorldData;
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
        Map companyMarkers = new LinkedHashMap();
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null || server.worldServers == null) {
            return new ArrayList();
        }
        UUID viewer = KOMEReflection.getEntityUUID(player);
        boolean admin = player.canCommandSenderUseCommand(2, "troops") && !data.isAdminUnitMapMarkersDisabled(viewer);
        for (WorldServer world : server.worldServers) {
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
                if (record == null || record.farmhand || record.isMoving() || record.companyId == null || record.companyId.length() == 0
                        || !admin && !viewer.equals(record.owner)) {
                    continue;
                }
                String key = record.companyId;
                CompanyMarkerBuilder builder = (CompanyMarkerBuilder) companyMarkers.get(key);
                if (builder == null) {
                    builder = new CompanyMarkerBuilder(world.provider.dimensionId, key, data.armyCompanies.get(record.companyId));
                    companyMarkers.put(key, builder);
                }
                builder.add(npc, record);
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

    private static class CompanyMarkerBuilder {
        private final int dimensionId;
        private final String companyId;
        private final KOMEArmyCompany company;
        private double weightedX;
        private double weightedY;
        private double weightedZ;
        private int weight;
        private int loadedPopulation;
        private int unitCount;
        private int mountedUnits;
        private boolean allHaltedProtected = true;
        private String fallbackName = "";
        private String fallbackTile = "";

        CompanyMarkerBuilder(int dimensionId, String companyId, KOMEArmyCompany company) {
            this.dimensionId = dimensionId;
            this.companyId = companyId == null ? "" : companyId;
            this.company = company;
        }

        void add(LOTREntityNPC npc, KOMEHiredUnitRecord record) {
            int cost = Math.max(1, record.cost);
            weightedX += npc.posX * cost;
            weightedY += npc.posY * cost;
            weightedZ += npc.posZ * cost;
            weight += cost;
            loadedPopulation += Math.max(0, record.cost);
            unitCount++;
            if (record.mounted) {
                mountedUnits++;
            }
            if (!KOMEHaltedUnitProtection.isProtected(npc)) {
                allHaltedProtected = false;
            }
            if (fallbackName.length() == 0) {
                fallbackName = record.companyName == null || record.companyName.length() == 0 ? record.lotrCompanyValue : record.companyName;
            }
            if (fallbackTile.length() == 0) {
                fallbackTile = record.currentTile;
            }
        }

        KOMEUnitMapMarker toMarker() {
            if (unitCount <= 0 || weight <= 0) {
                return null;
            }
            KOMEUnitMapMarker marker = new KOMEUnitMapMarker();
            marker.entityId = companyId;
            marker.companyName = company == null || company.name == null || company.name.length() == 0 ? fallbackName : company.name;
            if (marker.companyName == null || marker.companyName.length() == 0) {
                marker.companyName = "Company";
            }
            marker.currentTile = company == null || company.currentTile == null || company.currentTile.length() == 0
                    ? fallbackTile
                    : company.currentTile;
            marker.unitName = marker.companyName;
            marker.unitCount = unitCount;
            marker.dimensionId = dimensionId;
            marker.x = weightedX / weight;
            marker.y = weightedY / weight;
            marker.z = weightedZ / weight;
            marker.population = company == null || company.totalPopulation <= 0 ? loadedPopulation : company.totalPopulation;
            marker.mounted = mountedUnits > 0 && mountedUnits == unitCount;
            marker.haltedProtected = allHaltedProtected;
            return marker;
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketUnitMapMarkers, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketUnitMapMarkers message, MessageContext ctx) {
            KOMEClientData.INSTANCE.unitMapMarkers.clear();
            KOMEClientData.INSTANCE.unitMapMarkers.addAll(message.markers);
            return null;
        }
    }
}
