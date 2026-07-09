package kome.common.data;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class KOMEUnitMapMarker {
    public String entityId = "";
    public String unitName = "";
    public String companyName = "";
    public String currentTile = "";
    public int unitCount;
    public int dimensionId;
    public double x;
    public double y;
    public double z;
    public int population;
    public boolean mounted;
    public boolean haltedProtected;

    public void readFromBytes(ByteBuf buf) {
        entityId = ByteBufUtils.readUTF8String(buf);
        unitName = ByteBufUtils.readUTF8String(buf);
        companyName = ByteBufUtils.readUTF8String(buf);
        currentTile = ByteBufUtils.readUTF8String(buf);
        unitCount = buf.readInt();
        dimensionId = buf.readInt();
        x = buf.readDouble();
        y = buf.readDouble();
        z = buf.readDouble();
        population = buf.readInt();
        mounted = buf.readBoolean();
        haltedProtected = buf.readBoolean();
    }

    public void writeToBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, entityId == null ? "" : entityId);
        ByteBufUtils.writeUTF8String(buf, unitName == null ? "" : unitName);
        ByteBufUtils.writeUTF8String(buf, companyName == null ? "" : companyName);
        ByteBufUtils.writeUTF8String(buf, currentTile == null ? "" : currentTile);
        buf.writeInt(unitCount);
        buf.writeInt(dimensionId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeInt(population);
        buf.writeBoolean(mounted);
        buf.writeBoolean(haltedProtected);
    }
}
