package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketCompanyMovePreviewResult implements IMessage {
    public String companyId = "";
    public String companyName = "";
    public String originTileId = "";
    public String destinationTileId = "";
    public boolean valid;
    public int distanceTiles;
    public String travelTimeText = "";
    public String failureTitle = "";
    public String failureSummary = "";
    public String suggestedAction = "";
    public int hiddenDetailCount;
    public final List<String> routeTiles = new ArrayList<String>();
    public final List<String> failureDetails = new ArrayList<String>();

    public KOMEPacketCompanyMovePreviewResult() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        companyId = read(buf);
        companyName = read(buf);
        originTileId = read(buf);
        destinationTileId = read(buf);
        valid = buf.readBoolean();
        distanceTiles = buf.readInt();
        travelTimeText = read(buf);
        failureTitle = read(buf);
        failureSummary = read(buf);
        suggestedAction = read(buf);
        hiddenDetailCount = buf.readInt();
        routeTiles.clear();
        int routeCount = Math.max(0, Math.min(512, buf.readInt()));
        for (int i = 0; i < routeCount; i++) {
            routeTiles.add(read(buf));
        }
        failureDetails.clear();
        int detailCount = Math.max(0, Math.min(64, buf.readInt()));
        for (int i = 0; i < detailCount; i++) {
            failureDetails.add(read(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        write(buf, companyId);
        write(buf, companyName);
        write(buf, originTileId);
        write(buf, destinationTileId);
        buf.writeBoolean(valid);
        buf.writeInt(distanceTiles);
        write(buf, travelTimeText);
        write(buf, failureTitle);
        write(buf, failureSummary);
        write(buf, suggestedAction);
        buf.writeInt(hiddenDetailCount);
        buf.writeInt(routeTiles.size());
        for (String tile : routeTiles) {
            write(buf, tile);
        }
        buf.writeInt(failureDetails.size());
        for (String detail : failureDetails) {
            write(buf, detail);
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketCompanyMovePreviewResult, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketCompanyMovePreviewResult message, MessageContext ctx) {
            KOMEAddon.proxy.displayCompanyMovePreviewResult(message);
            return null;
        }
    }

    private static String read(ByteBuf buf) {
        return ByteBufUtils.readUTF8String(buf);
    }

    private static void write(ByteBuf buf, String value) {
        ByteBufUtils.writeUTF8String(buf, value == null ? "" : value);
    }
}
