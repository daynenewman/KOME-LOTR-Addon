package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KOMEPacketProgressionData implements IMessage {
    public String playerName;
    public List completed = new ArrayList();
    public Map assignments = new HashMap();

    public KOMEPacketProgressionData() {
    }

    public KOMEPacketProgressionData(String playerName, List completed) {
        this(playerName, completed, new HashMap());
    }

    public KOMEPacketProgressionData(String playerName, List completed, Map assignments) {
        this.playerName = playerName;
        this.completed = completed;
        this.assignments = assignments;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerName = ByteBufUtils.readUTF8String(buf);
        int count = buf.readInt();
        completed = new ArrayList();
        for (int i = 0; i < count; i++) {
            completed.add(ByteBufUtils.readUTF8String(buf));
        }
        int assignmentCount = buf.readInt();
        assignments = new HashMap();
        for (int i = 0; i < assignmentCount; i++) {
            assignments.put(ByteBufUtils.readUTF8String(buf), ByteBufUtils.readUTF8String(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, playerName);
        buf.writeInt(completed.size());
        for (Object id : completed) {
            ByteBufUtils.writeUTF8String(buf, String.valueOf(id));
        }
        buf.writeInt(assignments.size());
        for (Object entryObject : assignments.entrySet()) {
            Map.Entry entry = (Map.Entry) entryObject;
            ByteBufUtils.writeUTF8String(buf, String.valueOf(entry.getKey()));
            ByteBufUtils.writeUTF8String(buf, String.valueOf(entry.getValue()));
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketProgressionData, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketProgressionData message, MessageContext ctx) {
            KOMEAddon.proxy.updateProgressionData(message.playerName, message.completed, message.assignments);
            return null;
        }
    }
}
