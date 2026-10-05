package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketCompanyMoveConfirmGui implements IMessage {
    public String companyId = "";
    public String companyName = "";
    public String originTile = "";
    public String destinationTile = "";
    public int distanceTiles;
    public int unitCount;
    public int population;
    public int mountedPopulation;
    public int groundPopulation;
    public int tilesPerDay;
    public long travelMillis;
    public String routeSummary = "";
    public int remainingAllowance;
    public boolean immediateMovementPossible;
    public boolean requiresFutureBoundary;
    public boolean exhausted;
    public int requiredMovementBoundaries;
    public long nextMovementBoundaryMillis;
    public String nextMovementBoundaryText = "";
    public long estimatedCompletionMillis;
    public String estimatedCompletionText = "";
    public String movementStatusCode = "";
    public String movementStatusText = "";
    public String previewToken = "";
    public int arrivalDimension;
    public double arrivalX;
    public double arrivalY;
    public double arrivalZ;
    public String arrivalSource = "";
    public final List<String> routeTiles = new ArrayList<String>();

    public KOMEPacketCompanyMoveConfirmGui() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        companyId = read(buf);
        companyName = read(buf);
        originTile = read(buf);
        destinationTile = read(buf);
        distanceTiles = buf.readInt();
        unitCount = buf.readInt();
        population = buf.readInt();
        mountedPopulation = buf.readInt();
        groundPopulation = buf.readInt();
        tilesPerDay = buf.readInt();
        travelMillis = buf.readLong();
        routeSummary = read(buf);
        remainingAllowance = buf.readInt();
        immediateMovementPossible = buf.readBoolean();
        requiresFutureBoundary = buf.readBoolean();
        exhausted = buf.readBoolean();
        requiredMovementBoundaries = buf.readInt();
        nextMovementBoundaryMillis = buf.readLong();
        nextMovementBoundaryText = read(buf);
        estimatedCompletionMillis = buf.readLong();
        estimatedCompletionText = read(buf);
        movementStatusCode = read(buf);
        movementStatusText = read(buf);
        previewToken = read(buf);
        arrivalDimension = buf.readInt();
        arrivalX = buf.readDouble();
        arrivalY = buf.readDouble();
        arrivalZ = buf.readDouble();
        arrivalSource = read(buf);
        routeTiles.clear();
        int routeCount = Math.max(0, Math.min(512, buf.readInt()));
        for (int i = 0; i < routeCount; i++) {
            routeTiles.add(read(buf));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        write(buf, companyId);
        write(buf, companyName);
        write(buf, originTile);
        write(buf, destinationTile);
        buf.writeInt(distanceTiles);
        buf.writeInt(unitCount);
        buf.writeInt(population);
        buf.writeInt(mountedPopulation);
        buf.writeInt(groundPopulation);
        buf.writeInt(tilesPerDay);
        buf.writeLong(travelMillis);
        write(buf, routeSummary);
        buf.writeInt(remainingAllowance);
        buf.writeBoolean(immediateMovementPossible);
        buf.writeBoolean(requiresFutureBoundary);
        buf.writeBoolean(exhausted);
        buf.writeInt(requiredMovementBoundaries);
        buf.writeLong(nextMovementBoundaryMillis);
        write(buf, nextMovementBoundaryText);
        buf.writeLong(estimatedCompletionMillis);
        write(buf, estimatedCompletionText);
        write(buf, movementStatusCode);
        write(buf, movementStatusText);
        write(buf, previewToken);
        buf.writeInt(arrivalDimension);
        buf.writeDouble(arrivalX);
        buf.writeDouble(arrivalY);
        buf.writeDouble(arrivalZ);
        write(buf, arrivalSource);
        buf.writeInt(routeTiles.size());
        for (String tile : routeTiles) {
            write(buf, tile);
        }
    }

    public static class Handler implements IMessageHandler<KOMEPacketCompanyMoveConfirmGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketCompanyMoveConfirmGui message, MessageContext ctx) {
            final KOMEPacketCompanyMoveConfirmGui snapshot = message.copyForPublication();
            KOMEAddon.proxy.enqueueClientTask(() -> KOMEAddon.proxy.displayCompanyMoveConfirmGui(snapshot));
            return null;
        }
    }

    public KOMEPacketCompanyMoveConfirmGui copyForPublication() {
        KOMEPacketCompanyMoveConfirmGui copy = new KOMEPacketCompanyMoveConfirmGui();
        copy.companyId = companyId; copy.companyName = companyName; copy.originTile = originTile;
        copy.destinationTile = destinationTile; copy.distanceTiles = distanceTiles; copy.unitCount = unitCount;
        copy.population = population; copy.mountedPopulation = mountedPopulation; copy.groundPopulation = groundPopulation;
        copy.tilesPerDay = tilesPerDay; copy.travelMillis = travelMillis; copy.routeSummary = routeSummary;
        copy.remainingAllowance = remainingAllowance; copy.immediateMovementPossible = immediateMovementPossible;
        copy.requiresFutureBoundary = requiresFutureBoundary; copy.exhausted = exhausted;
        copy.requiredMovementBoundaries = requiredMovementBoundaries;
        copy.nextMovementBoundaryMillis = nextMovementBoundaryMillis; copy.estimatedCompletionMillis = estimatedCompletionMillis;
        copy.nextMovementBoundaryText = nextMovementBoundaryText;
        copy.estimatedCompletionText = estimatedCompletionText;
        copy.movementStatusCode = movementStatusCode; copy.movementStatusText = movementStatusText;
        copy.previewToken = previewToken; copy.arrivalDimension = arrivalDimension; copy.arrivalX = arrivalX;
        copy.arrivalY = arrivalY; copy.arrivalZ = arrivalZ; copy.arrivalSource = arrivalSource;
        copy.routeTiles.addAll(routeTiles);
        return copy;
    }

    private static String read(ByteBuf buf) {
        return ByteBufUtils.readUTF8String(buf);
    }

    private static void write(ByteBuf buf, String value) {
        ByteBufUtils.writeUTF8String(buf, value == null ? "" : value);
    }
}
