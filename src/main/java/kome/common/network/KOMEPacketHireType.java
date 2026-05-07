package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEPopulationType;

public class KOMEPacketHireType implements IMessage {
    public String hireType;

    public KOMEPacketHireType() {
    }

    public KOMEPacketHireType(KOMEPopulationType hireType) {
        this.hireType = (hireType == null ? KOMEPopulationType.OFFENSIVE : hireType).key;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        hireType = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, hireType);
    }

    public static class Handler implements IMessageHandler<KOMEPacketHireType, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketHireType message, MessageContext ctx) {
            KOMEPopulationType type = KOMEPopulationType.forName(message.hireType);
            KOMEClientData.INSTANCE.hireType = type == null ? KOMEPopulationType.OFFENSIVE : type;
            return null;
        }
    }
}
