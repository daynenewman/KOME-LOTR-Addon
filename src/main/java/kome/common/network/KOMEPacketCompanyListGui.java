package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketCompanyListGui implements IMessage {
    public String tileId = "";
    public String tileDisplayName = "";
    public final List<KOMECompanyGuiEntry> companies = new ArrayList<KOMECompanyGuiEntry>();
    public boolean canCreate;

    public KOMEPacketCompanyListGui() {
    }

    public KOMEPacketCompanyListGui(String tileId, List<KOMECompanyGuiEntry> companies, boolean canCreate) {
        this(tileId, "", companies, canCreate);
    }

    public KOMEPacketCompanyListGui(String tileId, String tileDisplayName, List<KOMECompanyGuiEntry> companies, boolean canCreate) {
        this.tileId = tileId == null ? "" : tileId;
        this.tileDisplayName = tileDisplayName == null ? "" : tileDisplayName;
        if (companies != null) {
            this.companies.addAll(companies);
        }
        this.canCreate = canCreate;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        KOMEPopulationWire.readHeader(buf);
        tileId = KOMEPopulationWire.readText(buf);
        tileDisplayName = KOMEPopulationWire.readText(buf);
        canCreate = buf.readBoolean();
        int count = KOMEPopulationWire.count(buf.readInt());
        companies.clear();
        for (int i = 0; i < count; i++) {
            KOMECompanyGuiEntry entry = new KOMECompanyGuiEntry();
            entry.fromBytes(buf);
            companies.add(entry);
        }
        KOMEPopulationWire.requireFullyRead(buf);
    }

    @Override
    public void toBytes(ByteBuf output) {
        KOMEPopulationWire.writePacket(output, buf -> {
            KOMEPopulationWire.writeHeader(buf);
            KOMEPopulationWire.writeText(buf, tileId);
            KOMEPopulationWire.writeText(buf, tileDisplayName);
            buf.writeBoolean(canCreate);
            buf.writeInt(KOMEPopulationWire.count(companies.size()));
            for (KOMECompanyGuiEntry entry : companies) {
                entry.toBytes(buf);
            }
        });
    }

    public static class Handler implements IMessageHandler<KOMEPacketCompanyListGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketCompanyListGui message, MessageContext ctx) {
            final KOMEPacketCompanyListGui snapshot = KOMEPopulationWire.copyForPublication(message, KOMEPacketCompanyListGui::new);
            KOMEAddon.proxy.enqueueClientTask(() -> KOMEAddon.proxy.displayCompanyListGui(snapshot.tileId, snapshot.tileDisplayName, snapshot.companies, snapshot.canCreate));
            return null;
        }
    }
}
