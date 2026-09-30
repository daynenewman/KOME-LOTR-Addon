package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.data.KOMECampaignRecruitmentService;
import kome.common.data.KOMENativeTraderCampaignRecruitment;
import lotr.common.LOTRSquadrons;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** Client intent for the second action on the native LOTR unit-trade screen. */
public final class KOMEPacketCampaignHire implements IMessage {
    public int traderEntityId;
    public int tradeIndex;
    public String squadron = "";

    public KOMEPacketCampaignHire() { }

    public KOMEPacketCampaignHire(int traderEntityId, int tradeIndex, String squadron) {
        this.traderEntityId = traderEntityId;
        this.tradeIndex = tradeIndex;
        this.squadron = LOTRSquadrons.checkAcceptableLength(
            squadron == null ? "" : squadron);
    }

    @Override public void fromBytes(ByteBuf buf) {
        KOMEPopulationWire.readHeader(buf);
        traderEntityId = buf.readInt();
        tradeIndex = buf.readInt();
        squadron = LOTRSquadrons.checkAcceptableLength(KOMEPopulationWire.readText(buf));
        KOMEPopulationWire.requireFullyRead(buf);
    }

    @Override public void toBytes(ByteBuf output) {
        KOMEPopulationWire.writePacket(output, buf -> {
            KOMEPopulationWire.writeHeader(buf);
            buf.writeInt(traderEntityId);
            buf.writeInt(tradeIndex);
            KOMEPopulationWire.writeText(buf, squadron);
        });
    }

    public static final class Handler
            implements IMessageHandler<KOMEPacketCampaignHire, IMessage> {
        @Override public IMessage onMessage(KOMEPacketCampaignHire message,
                MessageContext context) {
            EntityPlayerMP player = context.getServerHandler().playerEntity;
            KOMECampaignRecruitmentService.Result result =
                KOMENativeTraderCampaignRecruitment.recruit(player,
                    message.traderEntityId, message.tradeIndex, message.squadron);
            if (result.success) {
                player.addChatMessage(new ChatComponentText(
                    "Campaign unit " + result.unitId + " hired for "
                        + result.nativeCoinCost + " coins and " + result.populationCost
                        + " population; admitted to company " + result.companyId
                        + " at " + result.strategicTile + "."));
            } else {
                player.addChatMessage(new ChatComponentText(
                    "Campaign Hire unavailable: " + result.reason));
            }
            return null;
        }
    }
}
