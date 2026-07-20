package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEMovementHistoryRecord;
import kome.common.data.KOMEWorldData;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class KOMEPacketMovementHistoryRequest implements IMessage {
    public String faction = "";
    public boolean allFactions;

    public KOMEPacketMovementHistoryRequest() {
    }

    public KOMEPacketMovementHistoryRequest(String faction, boolean allFactions) {
        this.faction = faction == null ? "" : faction;
        this.allFactions = allFactions;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        faction = ByteBufUtils.readUTF8String(buf);
        allFactions = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, faction == null ? "" : faction);
        buf.writeBoolean(allFactions);
    }

    public static class Handler implements IMessageHandler<KOMEPacketMovementHistoryRequest, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketMovementHistoryRequest message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            boolean admin = player.canCommandSenderUseCommand(2, "troops");
            String viewerFaction = playerFaction(data, player);
            String requestedFaction = KOMEAlliance.normalizeFactionKey(message.faction);
            boolean all = message.allFactions;
            if (all && !admin) {
                player.addChatMessage(new ChatComponentText("Only admins can view all faction troop movement records."));
                all = false;
            }
            if (!all) {
                if (requestedFaction.length() == 0) {
                    requestedFaction = viewerFaction;
                }
                if (!admin && !requestedFaction.equals(viewerFaction)) {
                    player.addChatMessage(new ChatComponentText("You can only view your own faction's troop movement records."));
                    requestedFaction = viewerFaction;
                }
            }
            for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
                if (order != null && order.id != null && order.id.length() > 0) {
                    data.updateMovementHistory(order, order.isPendingSpawn() ? KOMEMovementHistoryRecord.FAILED
                        : order.isMoving() ? KOMEMovementHistoryRecord.ACTIVE
                        : KOMEMovementHistoryRecord.ARRIVED.equals(order.status) ? KOMEMovementHistoryRecord.ARRIVED
                        : KOMEArmyMovementOrder.STOPPED.equals(order.status) ? KOMEMovementHistoryRecord.STOPPED : null);
                }
            }
            List<KOMEMovementHistoryRecord> records = new ArrayList<KOMEMovementHistoryRecord>();
            for (KOMEMovementHistoryRecord record : data.movementHistory.values()) {
                if (record == null) {
                    continue;
                }
                if (all || requestedFaction.equals(KOMEAlliance.normalizeFactionKey(record.faction))) {
                    records.add(record);
                }
            }
            Collections.sort(records, new Comparator<KOMEMovementHistoryRecord>() {
                @Override
                public int compare(KOMEMovementHistoryRecord left, KOMEMovementHistoryRecord right) {
                    long leftTime = displayTime(left);
                    long rightTime = displayTime(right);
                    if (leftTime != rightTime) {
                        return leftTime > rightTime ? -1 : 1;
                    }
                    String rightId = right == null || right.movementOrderId == null ? "" : right.movementOrderId;
                    String leftId = left == null || left.movementOrderId == null ? "" : left.movementOrderId;
                    return rightId.compareTo(leftId);
                }
            });
            String title = all ? "All Faction Movement Records" : displayFaction(requestedFaction) + " Movement Records";
            KOMEPacketHandler.network.sendTo(new KOMEPacketMovementHistoryData(title, requestedFaction, all, records), player);
            return null;
        }

        private static long displayTime(KOMEMovementHistoryRecord record) {
            if (record == null) {
                return 0L;
            }
            return record.getLatestActivityMillis();
        }

        private static String playerFaction(KOMEWorldData data, EntityPlayerMP player) {
            LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
            String key = pledge == null ? "" : pledge.codeName();
            return KOMEAlliance.normalizeFactionKey(key);
        }

        private static String displayFaction(String key) {
            return KOMEAlliance.displayFactionName(key);
        }
    }
}
