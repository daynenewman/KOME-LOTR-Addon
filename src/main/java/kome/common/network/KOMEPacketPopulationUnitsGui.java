package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketPopulationUnitsGui implements IMessage {
    public String playerName;
    public List lines = new ArrayList();
    public int armyUsed;
    public int armyTotal;
    public int farmhandsUsed;
    public int farmhandsLimit;

    public KOMEPacketPopulationUnitsGui() {
    }

    public KOMEPacketPopulationUnitsGui(String playerName, List lines, int armyUsed, int armyTotal, int farmhandsUsed, int farmhandsLimit) {
        this.playerName = playerName;
        this.lines = lines;
        this.armyUsed = armyUsed;
        this.armyTotal = armyTotal;
        this.farmhandsUsed = farmhandsUsed;
        this.farmhandsLimit = farmhandsLimit;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerName = ByteBufUtils.readUTF8String(buf);
        armyUsed = buf.readInt();
        armyTotal = buf.readInt();
        farmhandsUsed = buf.readInt();
        farmhandsLimit = buf.readInt();
        int count = buf.readInt();
        lines = new ArrayList();
        for (int i = 0; i < count; i++) {
            lines.add(ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, playerName);
        buf.writeInt(armyUsed);
        buf.writeInt(armyTotal);
        buf.writeInt(farmhandsUsed);
        buf.writeInt(farmhandsLimit);
        buf.writeInt(lines.size());
        for (Object line : lines) {
            ByteBufUtils.writeUTF8String(buf, String.valueOf(line));
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketPopulationUnitsGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketPopulationUnitsGui message, MessageContext ctx) {
            KOMEAddon.proxy.displayPopulationUnitsGui(message.playerName, message.lines, message.armyUsed, message.armyTotal, message.farmhandsUsed, message.farmhandsLimit);
            return null;
        }
    }
}
