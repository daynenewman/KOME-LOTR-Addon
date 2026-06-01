package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMETileTroopSummary;
import kome.common.data.KOMEWorldData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.HashMap;
import java.util.Map;

public class KOMEPacketConquestData implements IMessage {
    public NBTTagCompound data = new NBTTagCompound();

    public KOMEPacketConquestData() {
    }

    public KOMEPacketConquestData(KOMEWorldData worldData) {
        NBTTagList list = new NBTTagList();
        for (KOMEConquestTile tile : worldData.conquestTiles.values()) {
            if (tile.isClaimed()) {
                list.appendTag(tile.writeToNBT());
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
        NBTTagList troopList = new NBTTagList();
        for (KOMETileTroopSummary summary : buildTroopSummaries(worldData).values()) {
            if (summary.hasAnyTroops()) {
                troopList.appendTag(summary.writeToNBT());
            }
        }
        data.setTag("TroopSummaries", troopList);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestData, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestData message, MessageContext ctx) {
            KOMEClientData.INSTANCE.conquestTiles.clear();
            NBTTagList list = message.data.getTagList("ConquestTiles", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                KOMEConquestTile tile = new KOMEConquestTile("");
                tile.readFromNBT(list.getCompoundTagAt(i));
                if (!tile.id.isEmpty() && tile.isClaimed()) {
                    KOMEClientData.INSTANCE.conquestTiles.put(tile.id, tile);
                }
            }
            KOMEClientData.INSTANCE.armyMovements.clear();
            NBTTagList movementList = message.data.getTagList("ArmyMovements", 10);
            for (int i = 0; i < movementList.tagCount(); i++) {
                KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
                order.readFromNBT(movementList.getCompoundTagAt(i));
                if (order.id.length() > 0 && order.isMoving()) {
                    KOMEClientData.INSTANCE.armyMovements.put(order.id, order);
                }
            }
            KOMEClientData.INSTANCE.troopSummaries.clear();
            NBTTagList troopList = message.data.getTagList("TroopSummaries", 10);
            for (int i = 0; i < troopList.tagCount(); i++) {
                KOMETileTroopSummary summary = new KOMETileTroopSummary();
                summary.readFromNBT(troopList.getCompoundTagAt(i));
                if (summary.tileId.length() > 0 && summary.hasAnyTroops()) {
                    KOMEClientData.INSTANCE.troopSummaries.put(summary.tileId, summary);
                }
            }
            KOMEClientData.INSTANCE.conquestRevision++;
            return null;
        }
    }

    private static Map<String, KOMETileTroopSummary> buildTroopSummaries(KOMEWorldData worldData) {
        Map<String, KOMETileTroopSummary> summaries = new HashMap<String, KOMETileTroopSummary>();
        for (KOMEHiredUnitRecord record : worldData.hiredUnits.values()) {
            if (record == null || record.currentTile == null || record.currentTile.length() == 0 || record.farmhand) {
                continue;
            }
            String tile = KOMEConquestTile.normalizeId(record.currentTile);
            KOMETileTroopSummary summary = getSummary(summaries, tile);
            if (record.movementOrderId != null && record.movementOrderId.length() > 0) {
                summary.movingPop += record.cost;
            } else {
                summary.stationedPop += record.cost;
            }
        }
        for (KOMEArmyMovementOrder order : worldData.armyMovements.values()) {
            if (order == null || !order.isMoving()) {
                continue;
            }
            getSummary(summaries, order.destinationTile).incomingPop += order.population;
            getSummary(summaries, order.originTile).movingPop += order.population;
        }
        return summaries;
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
