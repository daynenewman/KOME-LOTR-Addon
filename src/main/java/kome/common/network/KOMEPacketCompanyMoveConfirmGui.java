package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketCompanyMoveConfirmGui implements IMessage {
    public String companyId = "";
    public String companyName = "";
    public String originTile = "";
    public String destinationTile = "";
    public int distanceTiles;
    public int unitCount;
    public int population;
    public int mountedPopulation;
    public int groundPopulation;
    public int tilesPerDay;
    public long travelMillis;
    public String routeSummary = "";
    public int arrivalDimension;
    public double arrivalX;
    public double arrivalY;
    public double arrivalZ;
    public String arrivalSource = "";
    public final List<String> routeTiles = new ArrayList<String>();

    public KOMEPacketCompanyMoveConfirmGui() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        companyId = read(buf);
        companyName = read(buf);
        originTile = read(buf);
        destinationTile = read(buf);
        distanceTiles = buf.readInt();
        unitCount = buf.readInt();
        population = buf.readInt();
        mountedPopulation = buf.readInt();
        groundPopulation = buf.readInt();
        tilesPerDay = buf.readInt();
        travelMillis = buf.readLong();
        routeSummary = read(buf);
        arrivalDimension = buf.readInt();
        arrivalX = buf.readDouble();
        arrivalY = buf.readDouble();
        arrivalZ = buf.readDouble();
        arrivalSource = read(buf);
        routeTiles.clear();
        int routeCount = Math.max(0, Math.min(512, buf.readInt()));
        for (int i = 0; i < routeCount; i++) {
            routeTiles.add(read(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        write(buf, companyId);
        write(buf, companyName);
        write(buf, originTile);
        write(buf, destinationTile);
        buf.writeInt(distanceTiles);
        buf.writeInt(unitCount);
        buf.writeInt(population);
        buf.writeInt(mountedPopulation);
        buf.writeInt(groundPopulation);
        buf.writeInt(tilesPerDay);
        buf.writeLong(travelMillis);
        write(buf, routeSummary);
        buf.writeInt(arrivalDimension);
        buf.writeDouble(arrivalX);
        buf.writeDouble(arrivalY);
        buf.writeDouble(arrivalZ);
        write(buf, arrivalSource);
        buf.writeInt(routeTiles.size());
        for (String tile : routeTiles) {
            write(buf, tile);
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketCompanyMoveConfirmGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketCompanyMoveConfirmGui message, MessageContext ctx) {
            KOMEAddon.proxy.displayCompanyMoveConfirmGui(message);
            return null;
        }
    }

    private static String read(ByteBuf buf) {
        return ByteBufUtils.readUTF8String(buf);
    }

    private static void write(ByteBuf buf, String value) {
        ByteBufUtils.writeUTF8String(buf, value == null ? "" : value);
    }
}
