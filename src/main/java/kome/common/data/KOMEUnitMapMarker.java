package kome.common.data;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import kome.common.network.KOMEPopulationWire;

public class KOMEUnitMapMarker {
    private static final java.util.regex.Pattern ITEM_ID =
        java.util.regex.Pattern.compile("[A-Za-z0-9_.-]+:[A-Za-z0-9_./-]+");
    public String entityId = "";
    public String unitName = "";
    public String companyName = "";
    public String currentTile = "";
    /** Loaded NPC count. Zero explicitly denotes strategic tile presentation, not a physical fix. */
    public int unitCount;
    public int dimensionId;
    public double x;
    public double y;
    public double z;
    public int population;
    /** Surviving committed Campaign member records; independent of loaded NPC count and cost. */
    public int soldierCount;
    public boolean mounted;
    public boolean haltedProtected;
    /** Optional saved-equipment examples. Empty means unknown; never carries item NBT. */
    public String meleeItemId = "";
    public int meleeItemMetadata;
    public String rangedItemId = "";
    public int rangedItemMetadata;
    /** Optional saved boot/horse-armor example; empty selects the client fallback. */
    public String movementItemId = "";
    public int movementItemMetadata;
    public static final byte ENTITLEMENT_UNKNOWN = 0, ENTITLEMENT_FOOT_OR_MIXED = 1, ENTITLEMENT_MOUNTED = 2;
    public static final byte CREDIT_UNKNOWN = 0, CREDIT_AVAILABLE = 1, CREDIT_EXHAUSTED = 2;
    public byte movementEntitlement = ENTITLEMENT_UNKNOWN;
    public byte movementCredit = CREDIT_UNKNOWN;

    public void clearDescriptors() {
        meleeItemId = "";
        meleeItemMetadata = 0;
        rangedItemId = "";
        rangedItemMetadata = 0;
        movementItemId = "";
        movementItemMetadata = 0;
        movementEntitlement = ENTITLEMENT_UNKNOWN;
        movementCredit = CREDIT_UNKNOWN;
    }

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
        soldierCount = buf.readInt();
        mounted = buf.readBoolean();
        haltedProtected = buf.readBoolean();
        meleeItemId = readItemId(buf);
        meleeItemMetadata = readMetadata(buf, meleeItemId);
        rangedItemId = readItemId(buf);
        rangedItemMetadata = readMetadata(buf, rangedItemId);
        movementItemId = readItemId(buf);
        movementItemMetadata = readMetadata(buf, movementItemId);
        movementEntitlement = buf.readByte();
        movementCredit = buf.readByte();
        if (movementEntitlement < ENTITLEMENT_UNKNOWN || movementEntitlement > ENTITLEMENT_MOUNTED
                || movementCredit < CREDIT_UNKNOWN || movementCredit > CREDIT_EXHAUSTED)
            throw new IllegalArgumentException("Invalid company descriptor state");
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
        buf.writeInt(soldierCount);
        buf.writeBoolean(mounted);
        buf.writeBoolean(haltedProtected);
        writeItemId(buf, meleeItemId, meleeItemMetadata);
        writeItemId(buf, rangedItemId, rangedItemMetadata);
        writeItemId(buf, movementItemId, movementItemMetadata);
        if (movementEntitlement < ENTITLEMENT_UNKNOWN || movementEntitlement > ENTITLEMENT_MOUNTED
                || movementCredit < CREDIT_UNKNOWN || movementCredit > CREDIT_EXHAUSTED)
            throw new IllegalArgumentException("Invalid company descriptor state");
        buf.writeByte(movementEntitlement);
        buf.writeByte(movementCredit);
    }

    public static boolean validItemId(String id) {
        return id != null && (id.isEmpty() || id.length() <= 128
            && ITEM_ID.matcher(id).matches());
    }

    private static String readItemId(ByteBuf buf) {
        String id = KOMEPopulationWire.readText(buf);
        if (!validItemId(id)) throw new IllegalArgumentException("Invalid company descriptor item ID");
        return id;
    }

    private static int readMetadata(ByteBuf buf, String id) {
        int metadata = buf.readShort();
        if (metadata < 0 || id.isEmpty() && metadata != 0)
            throw new IllegalArgumentException("Invalid company descriptor metadata");
        return metadata;
    }

    private static void writeItemId(ByteBuf buf, String id, int metadata) {
        if (!validItemId(id) || metadata < 0 || metadata > Short.MAX_VALUE
                || (id.isEmpty() && metadata != 0))
            throw new IllegalArgumentException("Invalid company descriptor item");
        KOMEPopulationWire.writeText(buf, id);
        buf.writeShort(metadata);
    }
}
