package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEWorldData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public class KOMEPacketConquestData implements IMessage {
    public NBTTagCompound data = new NBTTagCompound();

    public KOMEPacketConquestData() {
    }

    public KOMEPacketConquestData(KOMEWorldData worldData) {
        NBTTagList list = new NBTTagList();
        for (KOMEConquestTile tile : worldData.conquestTiles.values()) {
            if (tile.isClaimed()) {
                list.appendTag(tile.writeToNBT());
            }
        }
        data.setTag("ConquestTiles", list);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<KOMEPacketConquestData, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketConquestData message, MessageContext ctx) {
            KOMEClientData.INSTANCE.conquestTiles.clear();
            NBTTagList list = message.data.getTagList("ConquestTiles", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                KOMEConquestTile tile = new KOMEConquestTile("");
                tile.readFromNBT(list.getCompoundTagAt(i));
                if (!tile.id.isEmpty() && tile.isClaimed()) {
                    KOMEClientData.INSTANCE.conquestTiles.put(tile.id, tile);
                }
            }
            KOMEClientData.INSTANCE.conquestRevision++;
            return null;
        }
    }
}
