package kome.common.network;

import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.ByteBufUtils;

public class KOMECompanyGuiEntry {
    public String id = "";
    public String name = "";
    public String tile = "";
    public int unitCount;
    public int population;
    public int mountedPopulation;
    public int groundPopulation;
    public String status = "";
    public String movementOrderId = "";
    public String destinationTile = "";
    public long etaMillis;
    public boolean canMove;
    public String cannotMoveReason = "";

    public void fromBytes(ByteBuf buf) {
        id = read(buf);
        name = read(buf);
        tile = read(buf);
        unitCount = buf.readInt();
        population = buf.readInt();
        mountedPopulation = buf.readInt();
        groundPopulation = buf.readInt();
        status = read(buf);
        movementOrderId = read(buf);
        destinationTile = read(buf);
        etaMillis = buf.readLong();
        canMove = buf.readBoolean();
        cannotMoveReason = read(buf);
    }

    public void toBytes(ByteBuf buf) {
        write(buf, id);
        write(buf, name);
        write(buf, tile);
        buf.writeInt(unitCount);
        buf.writeInt(population);
        buf.writeInt(mountedPopulation);
        buf.writeInt(groundPopulation);
        write(buf, status);
        write(buf, movementOrderId);
        write(buf, destinationTile);
        buf.writeLong(etaMillis);
        buf.writeBoolean(canMove);
        write(buf, cannotMoveReason);
    }

    private static String read(ByteBuf buf) {
        return ByteBufUtils.readUTF8String(buf);
    }

    private static void write(ByteBuf buf, String value) {
        ByteBufUtils.writeUTF8String(buf, value == null ? "" : value);
    }
}
