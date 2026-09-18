package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketPopulationUnitsGui implements IMessage {
    public kome.common.data.KOMEPopulationProjection population = new kome.common.data.KOMEPopulationProjection(
            "", 0L, java.math.BigInteger.ZERO, java.math.BigInteger.ZERO, false, 0L);
    public String playerName;
    public String filterTile;
    public List units = new ArrayList();
    public int farmhandsUsed;

    public KOMEPacketPopulationUnitsGui() {
    }

    public KOMEPacketPopulationUnitsGui(String playerName, String filterTile, List units,
            kome.common.data.KOMEPopulationProjection population, int farmhandsUsed) {
        this.playerName = playerName;
        this.filterTile = filterTile;
        this.units = units;
        this.population = population;
        this.farmhandsUsed = farmhandsUsed;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        KOMEPopulationWire.readHeader(buf);
        population = KOMEPopulationWire.readProjection(buf);
        playerName = KOMEPopulationWire.readText(buf);
        filterTile = KOMEPopulationWire.readText(buf);
        farmhandsUsed = buf.readInt();
        int count = KOMEPopulationWire.count(buf.readInt());
        units = new ArrayList();
        for (int i = 0; i < count; i++) {
            KOMEUnitGuiEntry entry = new KOMEUnitGuiEntry();
            entry.fromBytes(buf);
            units.add(entry);
        }
        KOMEPopulationWire.requireFullyRead(buf);
    }

    @Override
    public void toBytes(ByteBuf output) {
        KOMEPopulationWire.writePacket(output, buf -> {
            KOMEPopulationWire.writeHeader(buf);
            KOMEPopulationWire.writeProjection(buf, population);
            KOMEPopulationWire.writeText(buf, playerName);
            KOMEPopulationWire.writeText(buf, filterTile == null ? "" : filterTile);
            buf.writeInt(farmhandsUsed);
            buf.writeInt(KOMEPopulationWire.count(units.size()));
            for (Object object : units) {
                ((KOMEUnitGuiEntry) object).toBytes(buf);
            }
        });
    }

    public static class Handler implements IMessageHandler<KOMEPacketPopulationUnitsGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketPopulationUnitsGui message, MessageContext ctx) {
            final KOMEPacketPopulationUnitsGui snapshot = KOMEPopulationWire.copyForPublication(message, KOMEPacketPopulationUnitsGui::new);
            KOMEAddon.proxy.enqueueClientTask(() -> KOMEAddon.proxy.displayPopulationUnitsGui(snapshot));
            return null;
        }
    }
}
