package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;
import kome.common.data.KOMEPledgeReleaseService;

public class KOMEPacketPledgeDepartureData implements IMessage {
    public String playerName = "";
    public String formerFaction = "";
    public int units;
    public int farmhands;
    public int companies;
    public int movements;
    public int transferOffers;
    public int offensivePopulation;
    public int defensivePopulation;
    public int pendingUnloaded;
    public String fundingSources = "";

    public KOMEPacketPledgeDepartureData() { }

    public KOMEPacketPledgeDepartureData(String playerName, KOMEPledgeReleaseService.Preview preview) {
        this.playerName = playerName == null ? "" : playerName;
        formerFaction = preview.formerFaction;
        units = preview.units;
        farmhands = preview.farmhands;
        companies = preview.companies;
        movements = preview.movements;
        transferOffers = preview.transferOffers;
        offensivePopulation = preview.offensivePopulation;
        defensivePopulation = preview.defensivePopulation;
        pendingUnloaded = preview.pendingUnloaded;
        fundingSources = preview.fundingSources.toString();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerName = ByteBufUtils.readUTF8String(buf);
        formerFaction = ByteBufUtils.readUTF8String(buf);
        units = buf.readInt(); farmhands = buf.readInt(); companies = buf.readInt(); movements = buf.readInt();
        transferOffers = buf.readInt(); offensivePopulation = buf.readInt(); defensivePopulation = buf.readInt();
        pendingUnloaded = buf.readInt();
        fundingSources = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, playerName);
        ByteBufUtils.writeUTF8String(buf, formerFaction);
        buf.writeInt(units); buf.writeInt(farmhands); buf.writeInt(companies); buf.writeInt(movements);
        buf.writeInt(transferOffers); buf.writeInt(offensivePopulation); buf.writeInt(defensivePopulation);
        buf.writeInt(pendingUnloaded);
        ByteBufUtils.writeUTF8String(buf, fundingSources);
    }

    public static class Handler implements IMessageHandler<KOMEPacketPledgeDepartureData, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketPledgeDepartureData message, MessageContext ctx) {
            KOMEAddon.proxy.displayPledgeDeparture(message);
            return null;
        }
    }
}
