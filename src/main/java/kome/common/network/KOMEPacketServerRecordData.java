package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketServerRecordData implements IMessage {
    private static final int MAX_LINES_PER_PACKET = 24;
    private static final int MAX_LINE_LENGTH = 4096;

    public List lines = new ArrayList();
    public boolean reset;
    public boolean complete = true;

    public KOMEPacketServerRecordData() {
    }

    public KOMEPacketServerRecordData(List lines) {
        this.lines = lines;
    }

    public KOMEPacketServerRecordData(List lines, boolean reset, boolean complete) {
        this.lines = lines;
        this.reset = reset;
        this.complete = complete;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        lines = new ArrayList();
        reset = buf.readBoolean();
        complete = buf.readBoolean();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            lines.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(reset);
        buf.writeBoolean(complete);
        buf.writeInt(lines.size());
        for (Object line : lines) {
            ByteBufUtils.writeUTF8String(buf, limit(String.valueOf(line)));
        }
    }

    public static void sendChunked(List allLines, net.minecraft.entity.player.EntityPlayerMP player) {
        List safeLines = allLines == null ? new ArrayList() : allLines;
        if (safeLines.isEmpty()) {
            KOMEPacketHandler.network.sendTo(new KOMEPacketServerRecordData(safeLines, true, true), player);
            return;
        }
        for (int start = 0; start < safeLines.size(); start += MAX_LINES_PER_PACKET) {
            int end = Math.min(safeLines.size(), start + MAX_LINES_PER_PACKET);
            List chunk = new ArrayList(safeLines.subList(start, end));
            KOMEPacketHandler.network.sendTo(new KOMEPacketServerRecordData(chunk, start == 0, end >= safeLines.size()), player);
        }
    }

    private static String limit(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_LINE_LENGTH ? value : value.substring(0, MAX_LINE_LENGTH);
    }

    public static class Handler implements IMessageHandler<KOMEPacketServerRecordData, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketServerRecordData message, MessageContext ctx) {
            KOMEAddon.proxy.updateServerRecords(message.lines, message.reset, message.complete);
            return null;
        }
    }
}
