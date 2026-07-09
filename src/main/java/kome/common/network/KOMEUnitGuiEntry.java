package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class KOMEUnitGuiEntry {
    public String entityId = "";
    public String unitName = "";
    public String ownerName = "";
    public String factionName = "";
    public String populationType = "";
    public int populationCost;
    public boolean farmhand;
    public boolean mounted;
    public String currentTile = "";
    public String sourceType = "";
    public String sourceTile = "";
    public String sourceFaction = "";
    public String sourcePlayer = "";
    public String allocationTile = "";
    public String allocationPlayer = "";
    public String movementStatus = "Stationed";
    public String movementOrderId = "";
    public String destinationTile = "";
    public long etaMillis;
    public boolean canMove;
    public String cannotMoveReason = "";
    public String releasesTo = "";
    public int levelCap;
    public String companyId = "";
    public String companyName = "";
    public String companyStatus = "";
    public boolean haltedProtected;

    public void fromBytes(ByteBuf buf) {
        entityId = read(buf);
        unitName = read(buf);
        ownerName = read(buf);
        factionName = read(buf);
        populationType = read(buf);
        populationCost = buf.readInt();
        farmhand = buf.readBoolean();
        mounted = buf.readBoolean();
        currentTile = read(buf);
        sourceType = read(buf);
        sourceTile = read(buf);
        sourceFaction = read(buf);
        sourcePlayer = read(buf);
        allocationTile = read(buf);
        allocationPlayer = read(buf);
        movementStatus = read(buf);
        movementOrderId = read(buf);
        destinationTile = read(buf);
        etaMillis = buf.readLong();
        canMove = buf.readBoolean();
        cannotMoveReason = read(buf);
        releasesTo = read(buf);
        levelCap = buf.readInt();
        companyId = read(buf);
        companyName = read(buf);
        companyStatus = read(buf);
        haltedProtected = buf.readBoolean();
    }

    public void toBytes(ByteBuf buf) {
        write(buf, entityId);
        write(buf, unitName);
        write(buf, ownerName);
        write(buf, factionName);
        write(buf, populationType);
        buf.writeInt(populationCost);
        buf.writeBoolean(farmhand);
        buf.writeBoolean(mounted);
        write(buf, currentTile);
        write(buf, sourceType);
        write(buf, sourceTile);
        write(buf, sourceFaction);
        write(buf, sourcePlayer);
        write(buf, allocationTile);
        write(buf, allocationPlayer);
        write(buf, movementStatus);
        write(buf, movementOrderId);
        write(buf, destinationTile);
        buf.writeLong(etaMillis);
        buf.writeBoolean(canMove);
        write(buf, cannotMoveReason);
        write(buf, releasesTo);
        buf.writeInt(levelCap);
        write(buf, companyId);
        write(buf, companyName);
        write(buf, companyStatus);
        buf.writeBoolean(haltedProtected);
    }

    private static String read(ByteBuf buf) {
        return ByteBufUtils.readUTF8String(buf);
    }

    private static void write(ByteBuf buf, String value) {
        ByteBufUtils.writeUTF8String(buf, value == null ? "" : value);
    }
}
