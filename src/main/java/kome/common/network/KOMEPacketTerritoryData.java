package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.data.KOMEClientData;
import kome.common.data.KOMETerritory;
import kome.common.data.KOMEWorldData;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

public class KOMEPacketTerritoryData implements IMessage {
    public NBTTagCompound data = new NBTTagCompound();

    public KOMEPacketTerritoryData() {
    }

    public KOMEPacketTerritoryData(KOMEWorldData worldData) {
        NBTTagList list = new NBTTagList();
        for (KOMETerritory territory : worldData.territories.values()) {
            list.appendTag(territory.writeToNBT());
        }
        data.setTag("Territories", list);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        data = ByteBufUtils.readTag(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeTag(buf, data);
    }

    public static class Handler implements IMessageHandler<KOMEPacketTerritoryData, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketTerritoryData message, MessageContext ctx) {
            KOMEClientData.INSTANCE.territories.clear();
            NBTTagList list = message.data.getTagList("Territories", 10);
            for (int i = 0; i < list.tagCount(); i++) {
                KOMETerritory territory = new KOMETerritory("");
                territory.readFromNBT(list.getCompoundTagAt(i));
                KOMEClientData.INSTANCE.territories.put(territory.waypoint, territory);
            }
            return null;
        }
    }
}
