package kome.common.command;

import kome.common.KOMEReflection;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEWorldData;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KOMECommandTroops extends CommandBase {
    @Override
    public String getCommandName() {
        return "troops";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/troops list [tile] | station <tile> [all|unstationed] | move <fromTile> <toTile> <population> <distanceTiles> [all|mounted|ground] | arrive";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length < 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        UUID owner = KOMEReflection.getEntityUUID(player);
        if ("list".equalsIgnoreCase(args[0])) {
            String tile = args.length >= 2 ? KOMEConquestTile.normalizeId(args[1]) : "";
            listTroops(sender, data, owner, tile);
            return;
        }
        if ("station".equalsIgnoreCase(args[0])) {
            if (args.length < 2 || args.length > 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String tile = parseTile(args[1]);
            boolean all = args.length >= 3 && "all".equalsIgnoreCase(args[2]);
            stationTroops(sender, data, owner, tile, all);
            return;
        }
        if ("move".equalsIgnoreCase(args[0])) {
            if (args.length < 5 || args.length > 6) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String fromTile = parseTile(args[1]);
            String toTile = parseTile(args[2]);
            int population = Math.max(1, parseInt(sender, args[3]));
            int distanceTiles = Math.max(1, parseInt(sender, args[4]));
            String filter = args.length >= 6 ? args[5].toLowerCase() : "all";
            moveTroops(sender, player, data, owner, fromTile, toTile, population, distanceTiles, filter);
            return;
        }
        if ("arrive".equalsIgnoreCase(args[0])) {
            processArrivals(data, System.currentTimeMillis(), true);
            sender.addChatMessage(new ChatComponentText("Processed due troop movements."));
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private void listTroops(ICommandSender sender, KOMEWorldData data, UUID owner, String tile) {
        int stationedPop = 0;
        int stationedUnits = 0;
        int movingPop = 0;
        int movingUnits = 0;
        List<String> details = new ArrayList<String>();
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record == null || !owner.equals(record.owner)) {
                continue;
            }
            if (tile.length() > 0 && !tile.equals(KOMEConquestTile.normalizeId(record.currentTile))) {
                continue;
            }
            if (record.movementOrderId != null && record.movementOrderId.length() > 0) {
                movingPop += record.cost;
                movingUnits++;
            } else {
                stationedPop += record.cost;
                stationedUnits++;
            }
        }
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order != null && owner.equals(order.owner) && order.isMoving()) {
                if (tile.length() == 0 || tile.equals(order.originTile) || tile.equals(order.destinationTile)) {
                    details.add(order.id + ": " + order.originTile + " -> " + order.destinationTile + ", pop " + order.population + ", ETA " + formatDuration(order.getRemainingMillis(System.currentTimeMillis())));
                }
            }
        }
        Collections.sort(details);
        sender.addChatMessage(new ChatComponentText("Troops" + (tile.length() > 0 ? " at " + tile : "") + ": stationed " + stationedUnits + " units / " + stationedPop + " pop, moving " + movingUnits + " units / " + movingPop + " pop."));
        for (String line : details) {
            sender.addChatMessage(new ChatComponentText(line));
        }
    }

    private void stationTroops(ICommandSender sender, KOMEWorldData data, UUID owner, String tile, boolean all) {
        int units = 0;
        int pop = 0;
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record == null || !owner.equals(record.owner)) {
                continue;
            }
            if (record.movementOrderId != null && record.movementOrderId.length() > 0) {
                continue;
            }
            if (!all && record.currentTile != null && record.currentTile.length() > 0) {
                continue;
            }
            record.currentTile = tile;
            units++;
            pop += record.cost;
        }
        data.markDirty();
        data.syncConquestTiles();
        sender.addChatMessage(new ChatComponentText("Stationed " + units + " tracked units (" + pop + " pop) at " + tile + "."));
    }

    private void moveTroops(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, UUID owner, String fromTile, String toTile, int population, int distanceTiles, String filter) {
        if (fromTile.equals(toTile)) {
            throw new WrongUsageException("Origin and destination must be different tiles.");
        }
        List<KOMEHiredUnitRecord> selected = new ArrayList<KOMEHiredUnitRecord>();
        int selectedPop = 0;
        int mounted = 0;
        int ground = 0;
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record == null || !owner.equals(record.owner) || !fromTile.equals(KOMEConquestTile.normalizeId(record.currentTile))) {
                continue;
            }
            if (record.farmhand || record.type == KOMEPopulationType.DEFENSIVE) {
                continue;
            }
            if (record.movementOrderId != null && record.movementOrderId.length() > 0) {
                continue;
            }
            if ("mounted".equals(filter) && !record.mounted) {
                continue;
            }
            if ("ground".equals(filter) && record.mounted) {
                continue;
            }
            selected.add(record);
            selectedPop += record.cost;
            if (record.mounted) {
                mounted++;
            } else {
                ground++;
            }
            if (selectedPop >= population) {
                break;
            }
        }
        if (selected.isEmpty()) {
            throw new WrongUsageException("No movable offensive units found at " + fromTile + ".");
        }
        if (selectedPop < population) {
            throw new WrongUsageException("Only " + selectedPop + " movable population is available at " + fromTile + ".");
        }
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = nextOrderId(data);
        order.owner = owner;
        order.ownerName = player.getCommandSenderName();
        order.ownerFaction = getPlayerFaction(player);
        order.originTile = fromTile;
        order.destinationTile = toTile;
        order.population = selectedPop;
        order.mountedUnits = mounted;
        order.groundUnits = ground;
        order.distanceTiles = distanceTiles;
        order.tilesPerDay = ground == 0 && mounted > 0 ? 2 : 1;
        order.departureMillis = System.currentTimeMillis();
        order.arrivalMillis = order.departureMillis + getTravelMillis(distanceTiles, order.tilesPerDay);
        for (KOMEHiredUnitRecord record : selected) {
            order.units.add(record.entity);
            record.movementOrderId = order.id;
        }
        data.armyMovements.put(order.id, order);
        data.markDirty();
        data.syncConquestTiles();
        sender.addChatMessage(new ChatComponentText("Movement order " + order.id + ": " + selected.size() + " units / " + selectedPop + " pop from " + fromTile + " to " + toTile + ". ETA " + formatDuration(order.getRemainingMillis(System.currentTimeMillis())) + "."));
    }

    public static void processArrivals(KOMEWorldData data, long nowMillis, boolean announce) {
        boolean changed = false;
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order == null || !order.hasArrived(nowMillis)) {
                continue;
            }
            for (UUID unit : order.units) {
                KOMEHiredUnitRecord record = data.hiredUnits.get(unit);
                if (record != null && order.id.equals(record.movementOrderId)) {
                    record.currentTile = order.destinationTile;
                    record.movementOrderId = "";
                }
            }
            order.markArrived();
            changed = true;
        }
        if (changed) {
            data.markDirty();
            data.syncConquestTiles();
        }
    }

    private String parseTile(String value) {
        String tile = KOMEConquestTile.normalizeId(value);
        if (tile.length() == 0 || !KOMEConquestTile.isCanonicalTileId(tile)) {
            throw new WrongUsageException("Invalid conquest tile: " + value);
        }
        return tile;
    }

    private long getTravelMillis(int distanceTiles, int tilesPerDay) {
        return (distanceTiles * KOMEArmyMovementOrder.REAL_DAY_MILLIS + tilesPerDay - 1L) / tilesPerDay;
    }

    private String nextOrderId(KOMEWorldData data) {
        int next = data.armyMovements.size() + 1;
        String id;
        do {
            id = "M" + next++;
        } while (data.armyMovements.containsKey(id));
        return id;
    }

    private String getPlayerFaction(EntityPlayerMP player) {
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        return pledge == null ? "" : pledge.codeName();
    }

    private String formatDuration(long millis) {
        long minutes = Math.max(0L, (millis + 59999L) / 60000L);
        long days = minutes / 1440L;
        long hours = (minutes % 1440L) / 60L;
        long mins = minutes % 60L;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + mins + "m";
        }
        return mins + "m";
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "list", "station", "move", "arrive");
        }
        if (args.length == 6 && "move".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "all", "mounted", "ground");
        }
        return null;
    }
}
