package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

public class KOMEPacketPopulationGui implements IMessage {
    public String playerName;
    public int offensiveTotal;
    public int offensiveUsed;
    public int defensiveTotal;
    public int defensiveUsed;
    public int farmhandsUsed;
    public int farmhandsLimit;

    public KOMEPacketPopulationGui() {
    }

    public KOMEPacketPopulationGui(String playerName, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandsUsed, int farmhandsLimit) {
        this.playerName = playerName;
        this.offensiveTotal = offensiveTotal;
        this.offensiveUsed = offensiveUsed;
        this.defensiveTotal = defensiveTotal;
        this.defensiveUsed = defensiveUsed;
        this.farmhandsUsed = farmhandsUsed;
        this.farmhandsLimit = farmhandsLimit;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerName = ByteBufUtils.readUTF8String(buf);
        offensiveTotal = buf.readInt();
        offensiveUsed = buf.readInt();
        defensiveTotal = buf.readInt();
        defensiveUsed = buf.readInt();
        farmhandsUsed = buf.readInt();
        farmhandsLimit = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, playerName);
        buf.writeInt(offensiveTotal);
        buf.writeInt(offensiveUsed);
        buf.writeInt(defensiveTotal);
        buf.writeInt(defensiveUsed);
        buf.writeInt(farmhandsUsed);
        buf.writeInt(farmhandsLimit);
    }

    public static class Handler implements IMessageHandler<KOMEPacketPopulationGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketPopulationGui message, MessageContext ctx) {
            KOMEAddon.proxy.displayPopulationGui(message.playerName, message.offensiveTotal, message.offensiveUsed, message.defensiveTotal, message.defensiveUsed, message.farmhandsUsed, message.farmhandsLimit);
            return null;
        }
    }
}
