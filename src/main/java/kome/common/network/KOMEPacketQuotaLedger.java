package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketQuotaLedger implements IMessage {
    public List lines = new ArrayList();

    public KOMEPacketQuotaLedger() {
    }

    public KOMEPacketQuotaLedger(List lines) {
        this.lines = lines == null ? new ArrayList() : lines;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int count = buf.readInt();
        lines = new ArrayList();
        for (int i = 0; i < count; i++) {
            lines.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(lines.size());
        for (Object line : lines) {
            ByteBufUtils.writeUTF8String(buf, String.valueOf(line));
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketQuotaLedger, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketQuotaLedger message, MessageContext ctx) {
            KOMEAddon.proxy.updateQuotaLedger(message.lines);
            return null;
        }
    }
}
