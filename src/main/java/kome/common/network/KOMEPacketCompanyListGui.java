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
    public final List<KOMECompanyGuiEntry> companies = new ArrayList<KOMECompanyGuiEntry>();
    public boolean canCreate;

    public KOMEPacketCompanyListGui() {
    }

    public KOMEPacketCompanyListGui(String tileId, List<KOMECompanyGuiEntry> companies, boolean canCreate) {
        this.tileId = tileId == null ? "" : tileId;
        if (companies != null) {
            this.companies.addAll(companies);
        }
        this.canCreate = canCreate;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        tileId = ByteBufUtils.readUTF8String(buf);
        canCreate = buf.readBoolean();
        int count = buf.readInt();
        companies.clear();
        for (int i = 0; i < count; i++) {
            KOMECompanyGuiEntry entry = new KOMECompanyGuiEntry();
            entry.fromBytes(buf);
            companies.add(entry);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, tileId);
        buf.writeBoolean(canCreate);
        buf.writeInt(companies.size());
        for (KOMECompanyGuiEntry entry : companies) {
            entry.toBytes(buf);
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketCompanyListGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketCompanyListGui message, MessageContext ctx) {
            KOMEAddon.proxy.displayCompanyListGui(message.tileId, message.companies, message.canCreate);
            return null;
        }
    }
}
