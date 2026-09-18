package kome.common.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;
import kome.common.data.KOMEPopulationProjection;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Canonical faction projection and informational player/tile rows; no legacy ledger slots. */
public class KOMEPacketPopulationGui implements IMessage {
    public KOMEPopulationProjection population = new KOMEPopulationProjection(
            "", 0L, BigInteger.ZERO, BigInteger.ZERO, false, 0L);
    public String playerName = "";
    public String viewerFaction = "";
    public List<PlayerInvestment> playerBreakdowns = new ArrayList<PlayerInvestment>();
    public List<TileBreakdown> tileBreakdowns = new ArrayList<TileBreakdown>();

    @Override
    public void fromBytes(ByteBuf buf) {
        KOMEPopulationWire.readHeader(buf);
        KOMEPopulationProjection projected = KOMEPopulationWire.readProjection(buf);
        String player = KOMEPopulationWire.readText(buf);
        String faction = KOMEPopulationWire.readText(buf);
        List<PlayerInvestment> players = new ArrayList<PlayerInvestment>();
        int count = KOMEPopulationWire.count(buf.readInt());
        for (int i = 0; i < count; i++) {
            PlayerInvestment row = new PlayerInvestment();
            row.fromBytes(buf);
            players.add(row);
        }
        List<TileBreakdown> tiles = new ArrayList<TileBreakdown>();
        count = KOMEPopulationWire.count(buf.readInt());
        for (int i = 0; i < count; i++) {
            TileBreakdown row = new TileBreakdown();
            row.fromBytes(buf);
            tiles.add(row);
        }
        KOMEPopulationWire.requireFullyRead(buf);
        population = projected;
        playerName = player;
        viewerFaction = faction;
        playerBreakdowns = players;
        tileBreakdowns = tiles;
    }

    @Override
    public void toBytes(ByteBuf output) {
        KOMEPopulationWire.writePacket(output, buf -> {
            KOMEPopulationWire.writeHeader(buf);
            KOMEPopulationWire.writeProjection(buf, population);
            KOMEPopulationWire.writeText(buf, playerName);
            KOMEPopulationWire.writeText(buf, viewerFaction);
            buf.writeInt(KOMEPopulationWire.count(playerBreakdowns.size()));
            for (PlayerInvestment row : playerBreakdowns) row.toBytes(buf);
            buf.writeInt(KOMEPopulationWire.count(tileBreakdowns.size()));
            for (TileBreakdown row : tileBreakdowns) row.toBytes(buf);
        });
    }

    public static class PlayerInvestment {
        public BigInteger activePopulationCenti = BigInteger.ZERO;
        public String playerName = "";
        public String playerUuid = "";

        public void fromBytes(ByteBuf buf) {
            activePopulationCenti = KOMEPopulationWire.readExact(buf);
            playerName = KOMEPopulationWire.readText(buf);
            playerUuid = KOMEPopulationWire.readText(buf);
        }

        public void toBytes(ByteBuf buf) {
            KOMEPopulationWire.writeExact(buf, activePopulationCenti);
            KOMEPopulationWire.writeText(buf, playerName);
            KOMEPopulationWire.writeText(buf, playerUuid);
        }
    }

    public static class TileBreakdown {
        public KOMEPopulationProjection population = new KOMEPopulationProjection(
                "", 0L, BigInteger.ZERO, BigInteger.ZERO, false, 0L);
        public String tileId = "";
        public String tileDisplayName = "";
        public String ownerFaction = "";

        public void fromBytes(ByteBuf buf) {
            population = KOMEPopulationWire.readProjection(buf);
            tileId = KOMEPopulationWire.readText(buf);
            tileDisplayName = KOMEPopulationWire.readText(buf);
            ownerFaction = KOMEPopulationWire.readText(buf);
        }

        public void toBytes(ByteBuf buf) {
            KOMEPopulationWire.writeProjection(buf, population);
            KOMEPopulationWire.writeText(buf, tileId);
            KOMEPopulationWire.writeText(buf, tileDisplayName);
            KOMEPopulationWire.writeText(buf, ownerFaction);
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketPopulationGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketPopulationGui message, MessageContext ctx) {
            final KOMEPacketPopulationGui snapshot = KOMEPopulationWire.copyForPublication(message, KOMEPacketPopulationGui::new);
            KOMEAddon.proxy.enqueueClientTask(() -> KOMEAddon.proxy.displayPopulationGui(snapshot));
            return null;
        }
    }
}
