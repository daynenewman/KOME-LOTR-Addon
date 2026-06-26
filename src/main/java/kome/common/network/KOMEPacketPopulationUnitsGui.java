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
    public String filterTile;
    public List units = new ArrayList();
    public int armyUsed;
    public int armyTotal;
    public int farmhandsUsed;
    public int farmhandsLimit;

    public KOMEPacketPopulationUnitsGui() {
    }

    public KOMEPacketPopulationUnitsGui(String playerName, String filterTile, List units, int armyUsed, int armyTotal, int farmhandsUsed, int farmhandsLimit) {
        this.playerName = playerName;
        this.filterTile = filterTile;
        this.units = units;
        this.armyUsed = armyUsed;
        this.armyTotal = armyTotal;
        this.farmhandsUsed = farmhandsUsed;
        this.farmhandsLimit = farmhandsLimit;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerName = ByteBufUtils.readUTF8String(buf);
        filterTile = ByteBufUtils.readUTF8String(buf);
        armyUsed = buf.readInt();
        armyTotal = buf.readInt();
        farmhandsUsed = buf.readInt();
        farmhandsLimit = buf.readInt();
        int count = buf.readInt();
        units = new ArrayList();
        for (int i = 0; i < count; i++) {
            KOMEUnitGuiEntry entry = new KOMEUnitGuiEntry();
            entry.fromBytes(buf);
            units.add(entry);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, playerName);
        ByteBufUtils.writeUTF8String(buf, filterTile == null ? "" : filterTile);
        buf.writeInt(armyUsed);
        buf.writeInt(armyTotal);
        buf.writeInt(farmhandsUsed);
        buf.writeInt(farmhandsLimit);
        buf.writeInt(units.size());
        for (Object object : units) {
            ((KOMEUnitGuiEntry) object).toBytes(buf);
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketPopulationUnitsGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketPopulationUnitsGui message, MessageContext ctx) {
            KOMEAddon.proxy.displayPopulationUnitsGui(message.playerName, message.filterTile, message.units, message.armyUsed, message.armyTotal, message.farmhandsUsed, message.farmhandsLimit);
            return null;
        }
    }
}
