package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.*;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import kome.common.KOMEAddon;
import kome.common.tactical.edit.*;

/** Primitive, bounded browser data only. The shared session packets still own all edits. */
public final class KOMEPacketTacticalAreaCatalog implements IMessage {
    private KOMETacticalAreaCatalog catalog;
    public KOMEPacketTacticalAreaCatalog() { }
    public KOMEPacketTacticalAreaCatalog(KOMETacticalAreaCatalog catalog) { this.catalog = catalog; }
    public KOMETacticalAreaCatalog getCatalog() { return catalog; }
    @Override public void toBytes(ByteBuf buffer) {
        if (catalog == null) throw new IllegalStateException("Invalid area page.");
        KOMEPopulationWire.writePacket(buffer, out -> {
            out.writeByte(1); KOMETacticalEditWire.writeText(out, catalog.tileId, 128);
            out.writeInt(catalog.dimension); out.writeLong(catalog.revision); out.writeInt(catalog.page); out.writeInt(catalog.total);
            out.writeByte(catalog.rows.size());
            for (KOMETacticalAreaCatalog.Row row : catalog.rows) {
                KOMETacticalEditWire.writeText(out, row.id, 128); KOMETacticalEditWire.writeText(out, row.label, 256); out.writeLong(row.revision);
            }
        });
    }
    @Override public void fromBytes(ByteBuf buffer) {
        catalog = null;
        try {
            if (buffer.readableBytes() > 8192 || buffer.readUnsignedByte() != 1) throw new IllegalArgumentException("Invalid area page size.");
            String tile = KOMETacticalEditWire.readText(buffer, 128);
            int dimension = buffer.readInt(); long revision = buffer.readLong(); int page = buffer.readInt(), total = buffer.readInt();
            int count = buffer.readUnsignedByte();
            if (count > KOMETacticalAreaCatalog.PAGE_SIZE) throw new IllegalArgumentException("Too many area rows.");
            List<KOMETacticalAreaCatalog.Row> rows = new ArrayList<KOMETacticalAreaCatalog.Row>();
            for (int i = 0; i < count; i++) rows.add(new KOMETacticalAreaCatalog.Row(
                KOMETacticalEditWire.readText(buffer, 128), KOMETacticalEditWire.readText(buffer, 256), buffer.readLong()));
            KOMEPopulationWire.requireFullyRead(buffer);
            catalog = new KOMETacticalAreaCatalog(tile, dimension, revision, page, total, rows);
        } catch (RuntimeException invalid) { catalog = null; }
    }
    public static final class Handler implements IMessageHandler<KOMEPacketTacticalAreaCatalog, IMessage> {
        @Override public IMessage onMessage(KOMEPacketTacticalAreaCatalog packet, MessageContext context) {
            if (packet.catalog != null) KOMEAddon.proxy.acceptTacticalAreaCatalog(packet);
            return null;
        }
    }
}
