package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

public class KOMEPacketTerritoryGui implements IMessage {
    public String waypoint;
    public String waypointName;
    public String faction;
    public String ruler;
    public String displayName;

    public KOMEPacketTerritoryGui() {
    }

    public KOMEPacketTerritoryGui(String waypoint, String waypointName, String faction, String ruler, String displayName) {
        this.waypoint = waypoint;
        this.waypointName = waypointName;
        this.faction = faction;
        this.ruler = ruler;
        this.displayName = displayName;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        waypoint = ByteBufUtils.readUTF8String(buf);
        waypointName = ByteBufUtils.readUTF8String(buf);
        faction = ByteBufUtils.readUTF8String(buf);
        ruler = ByteBufUtils.readUTF8String(buf);
        displayName = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, waypoint);
        ByteBufUtils.writeUTF8String(buf, waypointName);
        ByteBufUtils.writeUTF8String(buf, faction);
        ByteBufUtils.writeUTF8String(buf, ruler);
        ByteBufUtils.writeUTF8String(buf, displayName);
    }

    public static class Handler implements IMessageHandler<KOMEPacketTerritoryGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketTerritoryGui message, MessageContext ctx) {
            KOMEAddon.proxy.displayTerritoryGui(message.waypoint, message.waypointName, message.faction, message.ruler, message.displayName);
            return null;
        }
    }
}
