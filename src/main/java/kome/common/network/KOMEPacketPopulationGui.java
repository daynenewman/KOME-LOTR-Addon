package kome.common.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import kome.common.KOMEAddon;

import java.util.ArrayList;
import java.util.List;

public class KOMEPacketPopulationGui implements IMessage {
    public String playerName = "";
    public String viewerFaction = "";
    public boolean canManageAllocations;

    // Legacy names retained for older constructors/client references.
    public int offensiveTotal;
    public int offensiveUsed;
    public int defensiveTotal;
    public int defensiveUsed;
    public int farmhandsUsed;
    public int farmhandsLimit;
    public int armyUsed;
    public int armyTotal;
    public int tileOffensiveTotal;
    public int tileOffensiveUsed;
    public int tileDefensiveTotal;
    public int tileDefensiveUsed;
    public int controlledTiles;
    public int allocatedOffensive;
    public int allocatedOffensiveUsed;
    public int allocatedDefensive;
    public int allocatedDefensiveUsed;
    public String allocationSummary = "";
    public int combinedOffensiveTotal;
    public int combinedOffensiveUsed;
    public int combinedOffensiveAvailable;
    public int combinedDefensiveTotal;
    public int combinedDefensiveUsed;
    public int combinedDefensiveAvailable;

    public int personalReserveOffensiveTotal;
    public int personalReserveOffensiveUsed;
    public int personalReserveOffensiveAvailable;
    public int personalReserveDefensiveTotal;
    public int personalReserveDefensiveUsed;
    public int personalReserveDefensiveAvailable;

    public int assignedTileOffensiveTotal;
    public int assignedTileOffensiveUsed;
    public int assignedTileOffensiveAvailable;
    public int assignedTileDefensiveTotal;
    public int assignedTileDefensiveUsed;
    public int assignedTileDefensiveAvailable;

    public int viewerTotalOffensiveTotal;
    public int viewerTotalOffensiveUsed;
    public int viewerTotalOffensiveAvailable;
    public int viewerTotalDefensiveTotal;
    public int viewerTotalDefensiveUsed;
    public int viewerTotalDefensiveAvailable;

    public int factionControlledTileCount;
    public int factionOffensiveTotal;
    public int factionOffensiveUsed;
    public int factionOffensiveAvailable;
    public int factionDefensiveTotal;
    public int factionDefensiveUsed;
    public int factionDefensiveAvailable;
    public int factionFarmhandUsed;
    public int factionFarmhandTotal;

    public List playerBreakdowns = new ArrayList();
    public CapacityBreakdown unallocatedBreakdown = new CapacityBreakdown();
    public List tileBreakdowns = new ArrayList();

    public KOMEPacketPopulationGui() {
    }

    public KOMEPacketPopulationGui(String playerName, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandsUsed, int farmhandsLimit, int armyUsed, int armyTotal, int tileOffensiveTotal, int tileOffensiveUsed, int tileDefensiveTotal, int tileDefensiveUsed, int controlledTiles, int allocatedOffensive, int allocatedOffensiveUsed, int allocatedDefensive, int allocatedDefensiveUsed, String allocationSummary, boolean canManageAllocations) {
        this(playerName, offensiveTotal, offensiveUsed, defensiveTotal, defensiveUsed, farmhandsUsed, farmhandsLimit, armyUsed, armyTotal, tileOffensiveTotal, tileOffensiveUsed, tileDefensiveTotal, tileDefensiveUsed, controlledTiles, allocatedOffensive, allocatedOffensiveUsed, allocatedDefensive, allocatedDefensiveUsed, allocationSummary, canManageAllocations, Math.max(0, offensiveTotal + allocatedOffensive), Math.max(0, offensiveUsed + allocatedOffensiveUsed), Math.max(0, offensiveTotal + allocatedOffensive - offensiveUsed - allocatedOffensiveUsed), Math.max(0, defensiveTotal + allocatedDefensive), Math.max(0, defensiveUsed + allocatedDefensiveUsed), Math.max(0, defensiveTotal + allocatedDefensive - defensiveUsed - allocatedDefensiveUsed), new ArrayList(), new CapacityBreakdown());
    }

    public KOMEPacketPopulationGui(String playerName, int offensiveTotal, int offensiveUsed, int defensiveTotal, int defensiveUsed, int farmhandsUsed, int farmhandsLimit, int armyUsed, int armyTotal, int tileOffensiveTotal, int tileOffensiveUsed, int tileDefensiveTotal, int tileDefensiveUsed, int controlledTiles, int allocatedOffensive, int allocatedOffensiveUsed, int allocatedDefensive, int allocatedDefensiveUsed, String allocationSummary, boolean canManageAllocations, int combinedOffensiveTotal, int combinedOffensiveUsed, int combinedOffensiveAvailable, int combinedDefensiveTotal, int combinedDefensiveUsed, int combinedDefensiveAvailable, List playerBreakdowns, CapacityBreakdown unallocatedBreakdown) {
        this.playerName = safe(playerName);
        this.offensiveTotal = Math.max(0, offensiveTotal);
        this.offensiveUsed = clamp(offensiveUsed, 0, this.offensiveTotal);
        this.defensiveTotal = Math.max(0, defensiveTotal);
        this.defensiveUsed = clamp(defensiveUsed, 0, this.defensiveTotal);
        this.farmhandsUsed = Math.max(0, farmhandsUsed);
        this.farmhandsLimit = Math.max(0, farmhandsLimit);
        this.armyUsed = Math.max(0, armyUsed);
        this.armyTotal = Math.max(0, armyTotal);
        this.tileOffensiveTotal = Math.max(0, tileOffensiveTotal);
        this.tileOffensiveUsed = clamp(tileOffensiveUsed, 0, this.tileOffensiveTotal);
        this.tileDefensiveTotal = Math.max(0, tileDefensiveTotal);
        this.tileDefensiveUsed = clamp(tileDefensiveUsed, 0, this.tileDefensiveTotal);
        this.controlledTiles = Math.max(0, controlledTiles);
        this.allocatedOffensive = Math.max(0, allocatedOffensive);
        this.allocatedOffensiveUsed = clamp(allocatedOffensiveUsed, 0, this.allocatedOffensive);
        this.allocatedDefensive = Math.max(0, allocatedDefensive);
        this.allocatedDefensiveUsed = clamp(allocatedDefensiveUsed, 0, this.allocatedDefensive);
        this.allocationSummary = safe(allocationSummary);
        this.canManageAllocations = canManageAllocations;
        this.combinedOffensiveTotal = Math.max(0, combinedOffensiveTotal);
        this.combinedOffensiveUsed = clamp(combinedOffensiveUsed, 0, this.combinedOffensiveTotal);
        this.combinedOffensiveAvailable = Math.max(0, combinedOffensiveAvailable);
        this.combinedDefensiveTotal = Math.max(0, combinedDefensiveTotal);
        this.combinedDefensiveUsed = clamp(combinedDefensiveUsed, 0, this.combinedDefensiveTotal);
        this.combinedDefensiveAvailable = Math.max(0, combinedDefensiveAvailable);
        this.playerBreakdowns = playerBreakdowns == null ? new ArrayList() : playerBreakdowns;
        this.unallocatedBreakdown = unallocatedBreakdown == null ? new CapacityBreakdown() : unallocatedBreakdown;
        mirrorLegacyToDto();
    }

    public void mirrorLegacyToDto() {
        personalReserveOffensiveTotal = offensiveTotal;
        personalReserveOffensiveUsed = offensiveUsed;
        personalReserveOffensiveAvailable = Math.max(0, offensiveTotal - offensiveUsed);
        personalReserveDefensiveTotal = defensiveTotal;
        personalReserveDefensiveUsed = defensiveUsed;
        personalReserveDefensiveAvailable = Math.max(0, defensiveTotal - defensiveUsed);
        assignedTileOffensiveTotal = allocatedOffensive;
        assignedTileOffensiveUsed = allocatedOffensiveUsed;
        assignedTileOffensiveAvailable = Math.max(0, allocatedOffensive - allocatedOffensiveUsed);
        assignedTileDefensiveTotal = allocatedDefensive;
        assignedTileDefensiveUsed = allocatedDefensiveUsed;
        assignedTileDefensiveAvailable = Math.max(0, allocatedDefensive - allocatedDefensiveUsed);
        viewerTotalOffensiveTotal = Math.max(0, offensiveTotal + allocatedOffensive);
        viewerTotalOffensiveUsed = clamp(offensiveUsed + allocatedOffensiveUsed, 0, viewerTotalOffensiveTotal);
        viewerTotalOffensiveAvailable = Math.max(0, viewerTotalOffensiveTotal - viewerTotalOffensiveUsed);
        viewerTotalDefensiveTotal = Math.max(0, defensiveTotal + allocatedDefensive);
        viewerTotalDefensiveUsed = clamp(defensiveUsed + allocatedDefensiveUsed, 0, viewerTotalDefensiveTotal);
        viewerTotalDefensiveAvailable = Math.max(0, viewerTotalDefensiveTotal - viewerTotalDefensiveUsed);
        factionControlledTileCount = controlledTiles;
        factionOffensiveTotal = combinedOffensiveTotal;
        factionOffensiveUsed = combinedOffensiveUsed;
        factionOffensiveAvailable = Math.max(0, factionOffensiveTotal - factionOffensiveUsed);
        factionDefensiveTotal = combinedDefensiveTotal;
        factionDefensiveUsed = combinedDefensiveUsed;
        factionDefensiveAvailable = Math.max(0, factionDefensiveTotal - factionDefensiveUsed);
        factionFarmhandUsed = farmhandsUsed;
        factionFarmhandTotal = farmhandsLimit;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerName = ByteBufUtils.readUTF8String(buf);
        viewerFaction = ByteBufUtils.readUTF8String(buf);
        canManageAllocations = buf.readBoolean();
        offensiveTotal = buf.readInt();
        offensiveUsed = buf.readInt();
        defensiveTotal = buf.readInt();
        defensiveUsed = buf.readInt();
        farmhandsUsed = buf.readInt();
        farmhandsLimit = buf.readInt();
        armyUsed = buf.readInt();
        armyTotal = buf.readInt();
        tileOffensiveTotal = buf.readInt();
        tileOffensiveUsed = buf.readInt();
        tileDefensiveTotal = buf.readInt();
        tileDefensiveUsed = buf.readInt();
        controlledTiles = buf.readInt();
        allocatedOffensive = buf.readInt();
        allocatedOffensiveUsed = buf.readInt();
        allocatedDefensive = buf.readInt();
        allocatedDefensiveUsed = buf.readInt();
        allocationSummary = ByteBufUtils.readUTF8String(buf);
        combinedOffensiveTotal = buf.readInt();
        combinedOffensiveUsed = buf.readInt();
        combinedOffensiveAvailable = buf.readInt();
        combinedDefensiveTotal = buf.readInt();
        combinedDefensiveUsed = buf.readInt();
        combinedDefensiveAvailable = buf.readInt();

        personalReserveOffensiveTotal = buf.readInt();
        personalReserveOffensiveUsed = buf.readInt();
        personalReserveOffensiveAvailable = buf.readInt();
        personalReserveDefensiveTotal = buf.readInt();
        personalReserveDefensiveUsed = buf.readInt();
        personalReserveDefensiveAvailable = buf.readInt();
        assignedTileOffensiveTotal = buf.readInt();
        assignedTileOffensiveUsed = buf.readInt();
        assignedTileOffensiveAvailable = buf.readInt();
        assignedTileDefensiveTotal = buf.readInt();
        assignedTileDefensiveUsed = buf.readInt();
        assignedTileDefensiveAvailable = buf.readInt();
        viewerTotalOffensiveTotal = buf.readInt();
        viewerTotalOffensiveUsed = buf.readInt();
        viewerTotalOffensiveAvailable = buf.readInt();
        viewerTotalDefensiveTotal = buf.readInt();
        viewerTotalDefensiveUsed = buf.readInt();
        viewerTotalDefensiveAvailable = buf.readInt();
        factionControlledTileCount = buf.readInt();
        factionOffensiveTotal = buf.readInt();
        factionOffensiveUsed = buf.readInt();
        factionOffensiveAvailable = buf.readInt();
        factionDefensiveTotal = buf.readInt();
        factionDefensiveUsed = buf.readInt();
        factionDefensiveAvailable = buf.readInt();
        factionFarmhandUsed = buf.readInt();
        factionFarmhandTotal = buf.readInt();

        playerBreakdowns = readCapacityList(buf);
        unallocatedBreakdown = new CapacityBreakdown();
        if (buf.readBoolean()) {
            unallocatedBreakdown.fromBytes(buf);
        }
        tileBreakdowns = readTileList(buf);
        sanitizeTopLevel();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        sanitizeTopLevel();
        ByteBufUtils.writeUTF8String(buf, playerName);
        ByteBufUtils.writeUTF8String(buf, viewerFaction);
        buf.writeBoolean(canManageAllocations);
        buf.writeInt(offensiveTotal);
        buf.writeInt(offensiveUsed);
        buf.writeInt(defensiveTotal);
        buf.writeInt(defensiveUsed);
        buf.writeInt(farmhandsUsed);
        buf.writeInt(farmhandsLimit);
        buf.writeInt(armyUsed);
        buf.writeInt(armyTotal);
        buf.writeInt(tileOffensiveTotal);
        buf.writeInt(tileOffensiveUsed);
        buf.writeInt(tileDefensiveTotal);
        buf.writeInt(tileDefensiveUsed);
        buf.writeInt(controlledTiles);
        buf.writeInt(allocatedOffensive);
        buf.writeInt(allocatedOffensiveUsed);
        buf.writeInt(allocatedDefensive);
        buf.writeInt(allocatedDefensiveUsed);
        ByteBufUtils.writeUTF8String(buf, allocationSummary);
        buf.writeInt(combinedOffensiveTotal);
        buf.writeInt(combinedOffensiveUsed);
        buf.writeInt(combinedOffensiveAvailable);
        buf.writeInt(combinedDefensiveTotal);
        buf.writeInt(combinedDefensiveUsed);
        buf.writeInt(combinedDefensiveAvailable);

        buf.writeInt(personalReserveOffensiveTotal);
        buf.writeInt(personalReserveOffensiveUsed);
        buf.writeInt(personalReserveOffensiveAvailable);
        buf.writeInt(personalReserveDefensiveTotal);
        buf.writeInt(personalReserveDefensiveUsed);
        buf.writeInt(personalReserveDefensiveAvailable);
        buf.writeInt(assignedTileOffensiveTotal);
        buf.writeInt(assignedTileOffensiveUsed);
        buf.writeInt(assignedTileOffensiveAvailable);
        buf.writeInt(assignedTileDefensiveTotal);
        buf.writeInt(assignedTileDefensiveUsed);
        buf.writeInt(assignedTileDefensiveAvailable);
        buf.writeInt(viewerTotalOffensiveTotal);
        buf.writeInt(viewerTotalOffensiveUsed);
        buf.writeInt(viewerTotalOffensiveAvailable);
        buf.writeInt(viewerTotalDefensiveTotal);
        buf.writeInt(viewerTotalDefensiveUsed);
        buf.writeInt(viewerTotalDefensiveAvailable);
        buf.writeInt(factionControlledTileCount);
        buf.writeInt(factionOffensiveTotal);
        buf.writeInt(factionOffensiveUsed);
        buf.writeInt(factionOffensiveAvailable);
        buf.writeInt(factionDefensiveTotal);
        buf.writeInt(factionDefensiveUsed);
        buf.writeInt(factionDefensiveAvailable);
        buf.writeInt(factionFarmhandUsed);
        buf.writeInt(factionFarmhandTotal);

        writeCapacityList(buf, playerBreakdowns);
        boolean hasUnallocated = unallocatedBreakdown != null && unallocatedBreakdown.hasAny();
        buf.writeBoolean(hasUnallocated);
        if (hasUnallocated) {
            unallocatedBreakdown.toBytes(buf);
        }
        writeTileList(buf, tileBreakdowns);
    }

    public void sanitizeTopLevel() {
        playerName = safe(playerName);
        viewerFaction = safe(viewerFaction);
        allocationSummary = safe(allocationSummary);
        offensiveTotal = Math.max(0, offensiveTotal);
        offensiveUsed = clamp(offensiveUsed, 0, offensiveTotal);
        defensiveTotal = Math.max(0, defensiveTotal);
        defensiveUsed = clamp(defensiveUsed, 0, defensiveTotal);
        farmhandsLimit = Math.max(0, farmhandsLimit);
        farmhandsUsed = clamp(farmhandsUsed, 0, farmhandsLimit);
        personalReserveOffensiveTotal = Math.max(0, personalReserveOffensiveTotal);
        personalReserveOffensiveUsed = clamp(personalReserveOffensiveUsed, 0, personalReserveOffensiveTotal);
        personalReserveOffensiveAvailable = Math.max(0, personalReserveOffensiveTotal - personalReserveOffensiveUsed);
        personalReserveDefensiveTotal = Math.max(0, personalReserveDefensiveTotal);
        personalReserveDefensiveUsed = clamp(personalReserveDefensiveUsed, 0, personalReserveDefensiveTotal);
        personalReserveDefensiveAvailable = Math.max(0, personalReserveDefensiveTotal - personalReserveDefensiveUsed);
        assignedTileOffensiveTotal = Math.max(0, assignedTileOffensiveTotal);
        assignedTileOffensiveUsed = clamp(assignedTileOffensiveUsed, 0, assignedTileOffensiveTotal);
        assignedTileOffensiveAvailable = Math.max(0, assignedTileOffensiveTotal - assignedTileOffensiveUsed);
        assignedTileDefensiveTotal = Math.max(0, assignedTileDefensiveTotal);
        assignedTileDefensiveUsed = clamp(assignedTileDefensiveUsed, 0, assignedTileDefensiveTotal);
        assignedTileDefensiveAvailable = Math.max(0, assignedTileDefensiveTotal - assignedTileDefensiveUsed);
        viewerTotalOffensiveTotal = Math.max(0, viewerTotalOffensiveTotal);
        viewerTotalOffensiveUsed = clamp(viewerTotalOffensiveUsed, 0, viewerTotalOffensiveTotal);
        viewerTotalOffensiveAvailable = Math.max(0, viewerTotalOffensiveTotal - viewerTotalOffensiveUsed);
        viewerTotalDefensiveTotal = Math.max(0, viewerTotalDefensiveTotal);
        viewerTotalDefensiveUsed = clamp(viewerTotalDefensiveUsed, 0, viewerTotalDefensiveTotal);
        viewerTotalDefensiveAvailable = Math.max(0, viewerTotalDefensiveTotal - viewerTotalDefensiveUsed);
        factionControlledTileCount = Math.max(0, factionControlledTileCount);
        factionOffensiveTotal = Math.max(0, factionOffensiveTotal);
        factionOffensiveUsed = clamp(factionOffensiveUsed, 0, factionOffensiveTotal);
        factionOffensiveAvailable = Math.max(0, factionOffensiveTotal - factionOffensiveUsed);
        factionDefensiveTotal = Math.max(0, factionDefensiveTotal);
        factionDefensiveUsed = clamp(factionDefensiveUsed, 0, factionDefensiveTotal);
        factionDefensiveAvailable = Math.max(0, factionDefensiveTotal - factionDefensiveUsed);
        factionFarmhandTotal = Math.max(0, factionFarmhandTotal);
        factionFarmhandUsed = clamp(factionFarmhandUsed, 0, factionFarmhandTotal);
        combinedOffensiveTotal = factionOffensiveTotal;
        combinedOffensiveUsed = factionOffensiveUsed;
        combinedOffensiveAvailable = factionOffensiveAvailable;
        combinedDefensiveTotal = factionDefensiveTotal;
        combinedDefensiveUsed = factionDefensiveUsed;
        combinedDefensiveAvailable = factionDefensiveAvailable;
    }

    private static List readCapacityList(ByteBuf buf) {
        List result = new ArrayList();
        int count = Math.max(0, Math.min(256, buf.readInt()));
        for (int i = 0; i < count; i++) {
            CapacityBreakdown row = new CapacityBreakdown();
            row.fromBytes(buf);
            result.add(row);
        }
        return result;
    }

    private static void writeCapacityList(ByteBuf buf, List rows) {
        int count = rows == null ? 0 : Math.min(256, rows.size());
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            Object object = rows.get(i);
            CapacityBreakdown row = object instanceof CapacityBreakdown ? (CapacityBreakdown) object : new CapacityBreakdown();
            row.toBytes(buf);
        }
    }

    private static List readTileList(ByteBuf buf) {
        List result = new ArrayList();
        int count = Math.max(0, Math.min(512, buf.readInt()));
        for (int i = 0; i < count; i++) {
            TileBreakdown row = new TileBreakdown();
            row.fromBytes(buf);
            result.add(row);
        }
        return result;
    }

    private static void writeTileList(ByteBuf buf, List rows) {
        int count = rows == null ? 0 : Math.min(512, rows.size());
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            Object object = rows.get(i);
            TileBreakdown row = object instanceof TileBreakdown ? (TileBreakdown) object : new TileBreakdown();
            row.toBytes(buf);
        }
    }

    public static class CapacityBreakdown {
        public String playerName = "";
        public String playerUuid = "";
        public boolean unallocated;
        public boolean canManage;
        public int offensiveTotal;
        public int offensiveUsed;
        public int offensiveAvailable;
        public int defensiveTotal;
        public int defensiveUsed;
        public int defensiveAvailable;
        public int reserveOffensiveTotal;
        public int reserveDefensiveTotal;
        public int assignedOffensiveTotal;
        public int assignedDefensiveTotal;

        public boolean hasAny() {
            return offensiveTotal > 0 || offensiveUsed > 0 || defensiveTotal > 0 || defensiveUsed > 0;
        }

        public void sanitize() {
            playerName = safe(playerName);
            playerUuid = safe(playerUuid);
            offensiveTotal = Math.max(0, offensiveTotal);
            offensiveUsed = clamp(offensiveUsed, 0, offensiveTotal);
            offensiveAvailable = Math.max(0, offensiveTotal - offensiveUsed);
            defensiveTotal = Math.max(0, defensiveTotal);
            defensiveUsed = clamp(defensiveUsed, 0, defensiveTotal);
            defensiveAvailable = Math.max(0, defensiveTotal - defensiveUsed);
            reserveOffensiveTotal = Math.max(0, reserveOffensiveTotal);
            reserveDefensiveTotal = Math.max(0, reserveDefensiveTotal);
            assignedOffensiveTotal = Math.max(0, assignedOffensiveTotal);
            assignedDefensiveTotal = Math.max(0, assignedDefensiveTotal);
        }

        public void fromBytes(ByteBuf buf) {
            playerName = ByteBufUtils.readUTF8String(buf);
            playerUuid = ByteBufUtils.readUTF8String(buf);
            unallocated = buf.readBoolean();
            canManage = buf.readBoolean();
            offensiveTotal = buf.readInt();
            offensiveUsed = buf.readInt();
            offensiveAvailable = buf.readInt();
            defensiveTotal = buf.readInt();
            defensiveUsed = buf.readInt();
            defensiveAvailable = buf.readInt();
            reserveOffensiveTotal = buf.readInt();
            reserveDefensiveTotal = buf.readInt();
            assignedOffensiveTotal = buf.readInt();
            assignedDefensiveTotal = buf.readInt();
            sanitize();
        }

        public void toBytes(ByteBuf buf) {
            sanitize();
            ByteBufUtils.writeUTF8String(buf, playerName);
            ByteBufUtils.writeUTF8String(buf, playerUuid);
            buf.writeBoolean(unallocated);
            buf.writeBoolean(canManage);
            buf.writeInt(offensiveTotal);
            buf.writeInt(offensiveUsed);
            buf.writeInt(offensiveAvailable);
            buf.writeInt(defensiveTotal);
            buf.writeInt(defensiveUsed);
            buf.writeInt(defensiveAvailable);
            buf.writeInt(reserveOffensiveTotal);
            buf.writeInt(reserveDefensiveTotal);
            buf.writeInt(assignedOffensiveTotal);
            buf.writeInt(assignedDefensiveTotal);
        }
    }

    public static class TileBreakdown {
        public String tileId = "";
        public String ownerFaction = "";
        public int offensiveTotal;
        public int offensiveAllocated;
        public int offensiveUnallocated;
        public int defensiveTotal;
        public int defensiveAllocated;
        public int defensiveUnallocated;
        public int farmhandUsed;
        public int farmhandTotal;

        public void sanitize() {
            tileId = safe(tileId);
            ownerFaction = safe(ownerFaction);
            offensiveTotal = Math.max(0, offensiveTotal);
            offensiveAllocated = clamp(offensiveAllocated, 0, offensiveTotal);
            offensiveUnallocated = Math.max(0, offensiveTotal - offensiveAllocated);
            defensiveTotal = Math.max(0, defensiveTotal);
            defensiveAllocated = clamp(defensiveAllocated, 0, defensiveTotal);
            defensiveUnallocated = Math.max(0, defensiveTotal - defensiveAllocated);
            farmhandTotal = Math.max(0, farmhandTotal);
            farmhandUsed = clamp(farmhandUsed, 0, farmhandTotal);
        }

        public void fromBytes(ByteBuf buf) {
            tileId = ByteBufUtils.readUTF8String(buf);
            ownerFaction = ByteBufUtils.readUTF8String(buf);
            offensiveTotal = buf.readInt();
            offensiveAllocated = buf.readInt();
            offensiveUnallocated = buf.readInt();
            defensiveTotal = buf.readInt();
            defensiveAllocated = buf.readInt();
            defensiveUnallocated = buf.readInt();
            farmhandUsed = buf.readInt();
            farmhandTotal = buf.readInt();
            sanitize();
        }

        public void toBytes(ByteBuf buf) {
            sanitize();
            ByteBufUtils.writeUTF8String(buf, tileId);
            ByteBufUtils.writeUTF8String(buf, ownerFaction);
            buf.writeInt(offensiveTotal);
            buf.writeInt(offensiveAllocated);
            buf.writeInt(offensiveUnallocated);
            buf.writeInt(defensiveTotal);
            buf.writeInt(defensiveAllocated);
            buf.writeInt(defensiveUnallocated);
            buf.writeInt(farmhandUsed);
            buf.writeInt(farmhandTotal);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static class Handler implements IMessageHandler<KOMEPacketPopulationGui, IMessage> {
        @Override
        public IMessage onMessage(KOMEPacketPopulationGui message, MessageContext ctx) {
            KOMEAddon.proxy.displayPopulationGui(message);
            return null;
        }
    }
}
