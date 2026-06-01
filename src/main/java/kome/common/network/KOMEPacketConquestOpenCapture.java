package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEWorldData;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

public class KOMEPacketConquestOpenCapture implements IMessage {
    public String tileId;

    public KOMEPacketConquestOpenCapture() {
    }

    public KOMEPacketConquestOpenCapture(String tileId) {
        this.tileId = tileId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tileId = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, tileId);
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestOpenCapture, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestOpenCapture message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            String tileId = KOMEConquestTile.normalizeId(message.tileId);
            if (tileId.isEmpty() || !KOMEConquestTile.isCanonicalTileId(tileId)) {
                return null;
            }
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            KOMEConquestTile tile = data.getConquestTile(tileId);
            TroopSummary summary = summarizeTroops(data, KOMEReflection.getEntityUUID(player), tileId);
            KOMEPacketHandler.network.sendTo(new KOMEPacketConquestCaptureGui(tile.id, tile.ownerFaction, tile.pendingTransferFromFaction, tile.pendingTransferToFaction, summary.offensivePop, summary.defensivePop, summary.mountedPop, summary.groundPop, summary.incomingPop, summary.outgoingPop, summary.incomingEtaMillis), player);
            return null;
        }

        private TroopSummary summarizeTroops(KOMEWorldData data, UUID owner, String tileId) {
            TroopSummary summary = new TroopSummary();
            for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
                if (record == null || !owner.equals(record.owner) || !tileId.equals(KOMEConquestTile.normalizeId(record.currentTile))) {
                    continue;
                }
                if (record.movementOrderId != null && record.movementOrderId.length() > 0) {
                    continue;
                }
                if (record.type == KOMEPopulationType.DEFENSIVE) {
                    summary.defensivePop += record.cost;
                } else if (!record.farmhand) {
                    summary.offensivePop += record.cost;
                    if (record.mounted) {
                        summary.mountedPop += record.cost;
                    } else {
                        summary.groundPop += record.cost;
                    }
                }
            }
            long now = System.currentTimeMillis();
            long eta = Long.MAX_VALUE;
            for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
                if (order == null || !owner.equals(order.owner) || !order.isMoving()) {
                    continue;
                }
                if (tileId.equals(order.originTile)) {
                    summary.outgoingPop += order.population;
                }
                if (tileId.equals(order.destinationTile)) {
                    summary.incomingPop += order.population;
                    eta = Math.min(eta, order.getRemainingMillis(now));
                }
            }
            summary.incomingEtaMillis = eta == Long.MAX_VALUE ? 0L : eta;
            return summary;
        }
    }

    private static class TroopSummary {
        int offensivePop;
        int defensivePop;
        int mountedPop;
        int groundPop;
        int incomingPop;
        int outgoingPop;
        long incomingEtaMillis;
    }
}
