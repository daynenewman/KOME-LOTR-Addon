package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEReflection;
import kome.common.command.KOMECommandAlliance;
import kome.common.command.KOMECommandTroops;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceRecordBuilder;
import kome.common.data.KOMEWorldData;
import net.minecraft.command.CommandException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** Typed GUI intent. Faction identity and authority are always recomputed by the server command path. */
public class KOMEPacketAllianceAction implements IMessage {
    public String action = "";
    public String track = "";
    public String firstFaction = "";
    public String secondFaction = "";

    public KOMEPacketAllianceAction() {
    }

    public KOMEPacketAllianceAction(String action, String track, String firstFaction, String secondFaction) {
        this.action = safe(action);
        this.track = safe(track);
        this.firstFaction = safe(firstFaction);
        this.secondFaction = safe(secondFaction);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        action = ByteBufUtils.readUTF8String(buf);
        track = ByteBufUtils.readUTF8String(buf);
        firstFaction = ByteBufUtils.readUTF8String(buf);
        secondFaction = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, action);
        ByteBufUtils.writeUTF8String(buf, track);
        ByteBufUtils.writeUTF8String(buf, firstFaction);
        ByteBufUtils.writeUTF8String(buf, secondFaction);
    }

    public static class Handler implements IMessageHandler<KOMEPacketAllianceAction, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketAllianceAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
            String action = normalizeAction(message.action);
            String track = KOMEAlliance.normalizeType(message.track);
            String first = KOMEAlliance.normalizeFactionKey(message.firstFaction);
            String second = KOMEAlliance.normalizeFactionKey(message.secondFaction);
            try {
                String[] command;
                if ("request".equals(action) || "accept".equals(action) || "roll".equals(action)
                        || "claimstage".equals(action)) {
                    command = new String[] {action, first, second};
                } else if ("break".equals(action)) {
                    command = new String[] {action, first, second};
                } else if ("ledger".equals(action)) {
                    command = new String[] {"goods", first, second};
                } else if ("claim".equals(action)) {
                    command = new String[] {"claimGoods", first, second};
                } else if ("companies".equals(action)) {
                    if (first.length() == 0 || second.length() == 0 || first.equals(second))
                        throw new IllegalArgumentException("Two different faction records are required.");
                    new KOMECommandTroops().processCommand(player, new String[] {"companies"});
                    command = null;
                } else {
                    throw new IllegalArgumentException("Unknown alliance GUI action.");
                }
                if (command != null) {
                    if (first.length() == 0 || second.length() == 0 || first.equals(second))
                        throw new IllegalArgumentException("Two different faction records are required.");
                    new KOMECommandAlliance().processCommand(player, command);
                }
            } catch (CommandException error) {
                player.addChatMessage(new ChatComponentText("Alliance action rejected: " + error.getMessage()));
            } catch (RuntimeException error) {
                player.addChatMessage(new ChatComponentText("Alliance action rejected: " + error.getMessage()));
            }
            KOMEPacketHandler.network.sendTo(new KOMEPacketAllianceData(KOMEAllianceRecordBuilder.build(data, player)), player);
            return null;
        }
    }

    private static String normalizeAction(String value) {
        return safe(value).trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
