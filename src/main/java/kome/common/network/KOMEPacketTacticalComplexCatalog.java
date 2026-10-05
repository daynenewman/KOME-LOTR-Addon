package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import kome.common.KOMEAddon;
import kome.common.tactical.edit.*;
import kome.common.tactical.edit.KOMETacticalComplexCatalog.*;
import static kome.common.tactical.edit.KOMETacticalComplexCatalog.PAGE_SIZE;

/** Primitive bounded client pages; draft NBT continues through the existing secured session wire. */
public final class KOMEPacketTacticalComplexCatalog implements IMessage {
    public static final int MAX_BYTES = 32768;
    private KOMETacticalComplexCatalog catalog;
    public KOMEPacketTacticalComplexCatalog() { }
    public KOMEPacketTacticalComplexCatalog(KOMETacticalComplexCatalog catalog) { this.catalog = catalog; }
    public KOMETacticalComplexCatalog getCatalog() { return catalog; }
    @Override public void toBytes(ByteBuf buffer) {
        if (catalog == null) throw new IllegalStateException("Invalid editor page.");
        KOMEPopulationWire.writePacket(buffer, out -> {
            out.writeByte(1); out.writeByte(catalog.kind.ordinal());
            KOMETacticalEditWire.writeText(out, catalog.tileId, 128);
            out.writeBoolean(catalog.complexId != null);
            if (catalog.complexId != null) KOMETacticalEditWire.writeText(out, catalog.complexId, 128);
            out.writeInt(catalog.dimension); out.writeLong(catalog.revision); out.writeInt(catalog.page); out.writeInt(catalog.total);
            out.writeByte(catalog.rows.size());
            for (Row row : catalog.rows) {
                KOMETacticalEditWire.writeText(out, row.id, 128); KOMETacticalEditWire.writeText(out, row.label, 256);
                KOMETacticalEditWire.writeText(out, row.detail, 512); out.writeBoolean(row.relatedId != null);
                if (row.relatedId != null) KOMETacticalEditWire.writeText(out, row.relatedId, 128);
                out.writeLong(row.revision); out.writeInt(row.assignedBuildCount);
            }
        });
    }
    @Override public void fromBytes(ByteBuf buffer) {
        catalog = null;
        try {
            if (buffer.readableBytes() > MAX_BYTES || buffer.readUnsignedByte() != 1) throw new IllegalArgumentException("Invalid page size/protocol.");
            int type = buffer.readUnsignedByte();
            if (type >= Kind.values().length) throw new IllegalArgumentException("Invalid page kind.");
            String tile = KOMETacticalEditWire.readText(buffer, 128);
            String complex = buffer.readBoolean() ? KOMETacticalEditWire.readText(buffer, 128) : null;
            int dimension = buffer.readInt(); long revision = buffer.readLong(); int page = buffer.readInt(), total = buffer.readInt();
            int count = buffer.readUnsignedByte();
            if (count > PAGE_SIZE) throw new IllegalArgumentException("Too many editor rows.");
            List<Row> rows = new ArrayList<Row>();
            for (int i = 0; i < count; i++) {
                String id = KOMETacticalEditWire.readText(buffer, 128), label = KOMETacticalEditWire.readText(buffer, 256), detail = KOMETacticalEditWire.readText(buffer, 512);
                String related = buffer.readBoolean() ? KOMETacticalEditWire.readText(buffer, 128) : null;
                rows.add(new Row(id, label, detail, related, buffer.readLong(), buffer.readInt()));
            }
            KOMEPopulationWire.requireFullyRead(buffer);
            catalog = new KOMETacticalComplexCatalog(Kind.values()[type], tile, complex, dimension, revision, page, total, rows);
        } catch (RuntimeException invalid) { catalog = null; }
    }
    public static final class Handler implements IMessageHandler<KOMEPacketTacticalComplexCatalog, IMessage> {
        @Override public IMessage onMessage(KOMEPacketTacticalComplexCatalog packet, MessageContext context) {
            if (packet.catalog != null) KOMEAddon.proxy.acceptTacticalComplexCatalog(packet);
            return null;
        }
    }
}
