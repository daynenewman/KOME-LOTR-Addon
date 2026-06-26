package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;
import kome.common.data.KOMEMovementHistoryRecord;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketMovementHistoryData implements IMessage {
    public String title = "";
    public String requestFaction = "";
    public boolean allFactions;
    public final List<KOMEMovementHistoryRecord> records = new ArrayList<KOMEMovementHistoryRecord>();

    public KOMEPacketMovementHistoryData() {
    }

    public KOMEPacketMovementHistoryData(String title, String requestFaction, boolean allFactions, List<KOMEMovementHistoryRecord> records) {
        this.title = title == null ? "" : title;
        this.requestFaction = requestFaction == null ? "" : requestFaction;
        this.allFactions = allFactions;
        if (records != null) {
            this.records.addAll(records);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        title = ByteBufUtils.readUTF8String(buf);
        requestFaction = ByteBufUtils.readUTF8String(buf);
        allFactions = buf.readBoolean();
        records.clear();
        NBTTagCompound root = ByteBufUtils.readTag(buf);
        NBTTagList list = root.getTagList("Records", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            KOMEMovementHistoryRecord record = new KOMEMovementHistoryRecord();
            record.readFromNBT(list.getCompoundTagAt(i));
            records.add(record);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, title == null ? "" : title);
        ByteBufUtils.writeUTF8String(buf, requestFaction == null ? "" : requestFaction);
        buf.writeBoolean(allFactions);
        NBTTagCompound root = new NBTTagCompound();
        NBTTagList list = new NBTTagList();
        for (KOMEMovementHistoryRecord record : records) {
            if (record != null) {
                list.appendTag(record.writeToNBT());
            }
        }
        root.setTag("Records", list);
        ByteBufUtils.writeTag(buf, root);
    }

    public static class Handler implements IMessageHandler<KOMEPacketMovementHistoryData, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketMovementHistoryData message, MessageContext ctx) {
            KOMEAddon.proxy.displayMovementHistory(message.title, message.requestFaction, message.allFactions, message.records);
            return null;
        }
    }
}
