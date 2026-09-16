package kome.common.command;

import kome.common.data.KOMEPlayerPopulation;
import kome.common.data.KOMEPlayerTilePopulationAllocation;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEProgressionPermissions;
import kome.common.data.KOMETilePopulation;
import kome.common.data.KOMETileWaypointLink;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMERulerAuthorization;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketPopulationGui;
import kome.common.network.KOMEPacketPopulationUnitsGui;
import kome.common.network.KOMEUnitGuiEntry;
import kome.common.KOMEReflection;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class KOMECommandPopulation extends CommandBase {
    @Override
    public String getCommandName() {
        return "population";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/population get [player] | gui [player] | units [player] [tile] | faction <faction> | rate [faction]";
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
        if ("get".equalsIgnoreCase(args[0]) || "gui".equalsIgnoreCase(args[0])) {
            EntityPlayerMP player = args.length >= 2 ? getPlayer(sender, args[1]) : getCommandSenderAsPlayer(sender);
            sendStatus(sender, player, "gui".equalsIgnoreCase(args[0]));
            return;
        }
        if ("units".equalsIgnoreCase(args[0])) {
            if (args.length > 3) {
                throw new WrongUsageException("/population units [player] [tile]");
            }
            EntityPlayerMP player = args.length >= 2 ? getPlayer(sender, args[1]) : getCommandSenderAsPlayer(sender);
            String filterTile = args.length >= 3 ? KOMEConquestTile.normalizeId(args[2]) : "";
            if (filterTile.length() > 0 && !KOMEConquestTile.isCanonicalTileId(filterTile)) {
                throw new WrongUsageException("Invalid conquest tile: " + args[2]);
            }
            sendUnitBreakdown(sender, player, filterTile);
            return;
        }
        if ("tile".equalsIgnoreCase(args[0])) {
            sendTileStatus(sender, args);
            return;
        }
        if ("faction".equalsIgnoreCase(args[0])) {
            sendFactionStatus(sender, args);
            return;
        }
        if ("rate".equalsIgnoreCase(args[0])) {
            sendRateAudit(sender, args);
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    /** Canonical Build-rate audit plus operator-only persisted payout diagnostics. */
    private void sendRateAudit(ICommandSender sender, String[] args) {
        if (args.length > 2) throw new WrongUsageException("/population rate [faction]");
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        String requested = args.length == 2 ? KOMEAlliance.normalizeFactionKey(args[1]) : "";
        if (requested.length() > 0) sender.addChatMessage(new ChatComponentText("Faction " + displayFaction(requested)
                + " Daily Population Rate: " + kome.common.data.KOMEPopulationProjection.formatRate(kome.common.data.KOMEPopulationProjection.of(data, requested).dailyRateUnits)));
        for (kome.common.data.KOMEPopulationRateContribution row : kome.common.data.KOMEPopulationService.getPopulationRateContributions(data)) {
            if (requested.length() > 0 && !requested.equals(row.populationFaction)) continue;
            sender.addChatMessage(new ChatComponentText("Build " + row.buildId + " " + row.displayName + " tile " + row.tileId
                    + " " + row.populationFaction + " -> " + (row.currentController.length() == 0 ? "UNCONTROLLED" : row.currentController)
                    + ": approved " + (kome.common.data.KOMEBuildTime.formatHours(row.approvedCentiHours)) + "h, original " + row.formatOriginalRate()
                    + " x" + row.multiplier + ", " + row.status + ", current " + row.formatCurrentRate()));
        }
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            for (String line : kome.common.data.KOMEPopulationPayoutProcessor.inspection(data))
                sender.addChatMessage(new ChatComponentText(line));
        }
    }

    private void manageTilePopulation(ICommandSender sender, String[] args) {
        if (args.length != 4) {
            throw new WrongUsageException("/population addtile/removetile <tileId> <offensive|defensive> <amount>");
        }
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            sender.addChatMessage(new ChatComponentText("You do not have permission to change tile population."));
            return;
        }
        String tileId = KOMEConquestTile.normalizeId(args[1]);
        if (!KOMEConquestTile.isCanonicalTileId(tileId)) {
            throw new WrongUsageException("Tile ID must be a valid conquest tile ID.");
        }
        KOMEPopulationType type = KOMEPopulationType.forName(args[2]);
        if (type == null) {
            throw new WrongUsageException("Population type must be offensive or defensive");
        }
        int amount = Math.max(0, parseInt(sender, args[3]));
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        KOMEConquestTile tile = data.getConquestTile(tileId);
        if (!tile.isClaimed()) {
            sender.addChatMessage(new ChatComponentText("Population can only be added to a claimed tile."));
            return;
        }
        KOMETilePopulation population = data.getOrCreateTilePopulationPool(tileId, tile.currentRulingFaction());
        int before = population.getTotal(type);
        boolean changed = data.adjustTilePopulationTotal(tileId, type, "removetile".equalsIgnoreCase(args[0]) ? -amount : amount);
        if (!changed) {
            sender.addChatMessage(new ChatComponentText("Tile population was not changed. It cannot be reduced below active usage or king-granted allocations."));
        } else {
            sender.addChatMessage(new ChatComponentText(("removetile".equalsIgnoreCase(args[0]) ? "Removed " : "Added ") + Math.abs(population.getTotal(type) - before) + " " + type.key + " tile population on " + tileId + "."));
        }
        data.syncConquestTiles();
        sendTileStatus(sender, new String[] {"tile", tileId});
    }

    private void sendTileStatus(ICommandSender sender, String[] args) {
        if (args.length != 2) throw new WrongUsageException("/population tile <tileId>");
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        String tileId = KOMEConquestTile.normalizeId(args[1]);
        KOMEConquestTile tile = data.conquestTiles.get(tileId);
        sender.addChatMessage(new ChatComponentText("Tile " + tileId + ": " + kome.common.data.KOMEPopulationProjection.of(
                data, tile == null ? "" : tile.projectRulingFaction()).summary()));
    }

    private void sendFactionStatus(ICommandSender sender, String[] args) {
        if (args.length != 2) throw new WrongUsageException("/population faction <faction>");
        sender.addChatMessage(new ChatComponentText(kome.common.data.KOMEPopulationProjection.of(
                KOMEWorldData.get(sender.getEntityWorld()), args[1]).summary()));
    }

    private void sendAllocationStatus(ICommandSender sender, String[] args) {
        if (args.length != 2) {
            throw new WrongUsageException("/population allocations <tileId>");
        }
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        String tileId = KOMEConquestTile.normalizeId(args[1]);
        KOMEConquestTile tile = data.conquestTiles.get(tileId);
        if (tile == null || !tile.isClaimed()) {
            throw new WrongUsageException("Tile " + tileId + " is unclaimed.");
        }
        String rulingFaction = tile.currentRulingFaction();
        int offTotal = data.getEffectiveUsablePopulation(tileId, rulingFaction, KOMEPopulationType.OFFENSIVE);
        int defTotal = data.getEffectiveUsablePopulation(tileId, rulingFaction, KOMEPopulationType.DEFENSIVE);
        int offAllocated = data.getTotalAllocated(tileId, rulingFaction, KOMEPopulationType.OFFENSIVE);
        int defAllocated = data.getTotalAllocated(tileId, rulingFaction, KOMEPopulationType.DEFENSIVE);
        sender.addChatMessage(new ChatComponentText("Tile " + tileId + " allocations for " + displayFaction(rulingFaction) + ":"));
        sender.addChatMessage(new ChatComponentText("Offensive allocated " + offAllocated + "/" + offTotal + ", unallocated " + Math.max(0, offTotal - offAllocated) + ". Defensive allocated " + defAllocated + "/" + defTotal + ", unallocated " + Math.max(0, defTotal - defAllocated) + "."));
        List<KOMEPlayerTilePopulationAllocation> allocations = data.getAllocationsForTile(tileId, rulingFaction);
        if (allocations.isEmpty()) {
            sender.addChatMessage(new ChatComponentText("No player allocations."));
        }
        for (KOMEPlayerTilePopulationAllocation allocation : allocations) {
            sender.addChatMessage(new ChatComponentText((allocation.playerName.length() == 0 ? allocation.playerUuid.toString() : allocation.playerName)
                + ": Off " + allocation.offensiveUsed + "/" + allocation.offensiveAllocated
                + ", Def " + allocation.defensiveUsed + "/" + allocation.defensiveAllocated));
        }
    }

    private void manageAllocation(ICommandSender sender, String[] args) {
        if (args.length != 5) {
            throw new WrongUsageException("/population allocate/unallocate <tileId> <player> <offensive|defensive> <amount>");
        }
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        String tileId = KOMEConquestTile.normalizeId(args[1]);
        KOMEConquestTile tile = data.conquestTiles.get(tileId);
        if (tile == null || !tile.isClaimed()) {
            throw new WrongUsageException("Tile " + tileId + " is unclaimed.");
        }
        boolean admin = sender.canCommandSenderUseCommand(2, getCommandName());
        String rulingFaction = tile.currentRulingFaction();
        if (!admin) {
            EntityPlayerMP actor = getCommandSenderAsPlayer(sender);
            if (!KOMERulerAuthorization.canActAsRuler(data, rulingFaction, KOMEReflection.getEntityUUID(actor))) {
                throw new WrongUsageException("Only the owning faction's king or an admin can manage tile allocations.");
            }
        }
        EntityPlayerMP target = getPlayer(sender, args[2]);
        String targetFaction = getPlayerFaction(data, target);
        if (!admin && !KOMEAlliance.normalizeFactionKey(rulingFaction).equals(KOMEAlliance.normalizeFactionKey(targetFaction))) {
            throw new WrongUsageException("The target player must belong to the tile owner's faction.");
        }
        KOMEPopulationType type = KOMEPopulationType.forName(args[3]);
        if (type == null) {
            throw new WrongUsageException("Population type must be offensive or defensive.");
        }
        int amount = Math.max(0, parseInt(sender, args[4]));
        UUID targetId = KOMEReflection.getEntityUUID(target);
        boolean allocate = "allocate".equalsIgnoreCase(args[0]);
        boolean changed = allocate
            ? data.allocatePopulation(tileId, rulingFaction, targetId, target.getCommandSenderName(), type, amount)
            : data.unallocatePopulation(tileId, rulingFaction, targetId, type, amount);
        if (!changed) {
            sender.addChatMessage(new ChatComponentText(allocate
                ? "Allocation failed: not enough unallocated effective tile population."
                : "Unallocation failed: allocation cannot be reduced below population used by active units."));
            return;
        }
        sender.addChatMessage(new ChatComponentText((allocate ? "Allocated " : "Unallocated ") + amount + " " + type.key + " tile population " + (allocate ? "to " : "from ") + target.getCommandSenderName() + " on " + tileId + "."));
        sendAllocationStatus(sender, new String[] {"allocations", tileId});
    }

    private boolean canManagePopulation(ICommandSender sender, EntityPlayerMP target) {
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            return true;
        }
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        if (!KOMEReflection.getEntityUUID(player).equals(KOMEReflection.getEntityUUID(target))) {
            KOMEProgressionPermissions.deny(player, "You can only change your own population.");
            return false;
        }
        return KOMEProgressionPermissions.require(player, KOMEProgressionPermissions.GROW_POPULATION);
    }

    private void sendStatus(ICommandSender sender, EntityPlayerMP player, boolean gui) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        UUID playerID = KOMEReflection.getEntityUUID(player);
        String faction = data.getPlayerFactionKey(playerID);
        kome.common.data.KOMEPopulationProjection projection = kome.common.data.KOMEPopulationProjection.of(data, faction);
        if (gui && sender instanceof EntityPlayerMP) {
            KOMEPacketPopulationGui packet = new KOMEPacketPopulationGui();
            packet.playerName = player.getCommandSenderName();
            packet.viewerFaction = faction;
            packet.population = projection;
            packet.canManageAllocations = ((EntityPlayerMP) sender).canCommandSenderUseCommand(2, getCommandName())
                || KOMERulerAuthorization.canActAsRuler(data, faction, KOMEReflection.getEntityUUID((EntityPlayerMP) sender));
            java.util.Map<String, KOMEPacketPopulationGui.CapacityBreakdown> players =
                new java.util.TreeMap<String, KOMEPacketPopulationGui.CapacityBreakdown>();
            for (KOMEHiredUnitRecord record : kome.common.data.KOMEPopulationService.livingRecords(data)) {
                if (record == null || record.farmhand || record.owner == null
                        || !faction.equals(kome.common.data.KOMEPopulationService.populationFaction(record))) continue;
                String id = record.owner.toString();
                KOMEPacketPopulationGui.CapacityBreakdown row = players.get(id);
                if (row == null) {
                    row = new KOMEPacketPopulationGui.CapacityBreakdown();
                    row.playerUuid = id;
                    row.playerName = getStoredPlayerName(data, record.owner);
                    players.put(id, row);
                }
                row.activePopulationCenti = row.activePopulationCenti.add(java.math.BigInteger.valueOf(
                    kome.common.data.KOMEPopulationService.getInvestmentCenti(record)));
            }
            packet.playerBreakdowns.addAll(players.values());
            java.util.List<String> tiles = new java.util.ArrayList<String>(data.conquestTiles.keySet());
            java.util.Collections.sort(tiles);
            for (String tileId : tiles) {
                KOMEConquestTile tile = data.conquestTiles.get(tileId);
                if (tile == null || faction.isEmpty() || !faction.equals(tile.projectRulingFaction())) continue;
                KOMEPacketPopulationGui.TileBreakdown row = new KOMEPacketPopulationGui.TileBreakdown();
                row.tileId = tileId;
                row.ownerFaction = faction;
                KOMETileWaypointLink link = data.getTileWaypointLink(tileId);
                row.tileDisplayName = link == null ? tileId : link.displayName();
                row.population = projection;
                packet.tileBreakdowns.add(row);
            }
            KOMEPacketHandler.network.sendTo(packet, (EntityPlayerMP) sender);
        } else {
            sender.addChatMessage(new ChatComponentText(projection.summary()));
            sender.addChatMessage(new ChatComponentText("Farmhands: 0.00 population; excluded from Active Population. Combat investment is permanently spent."));
        }
    }

    private List buildFactionMilitaryCapacityRows(KOMEWorldData data, String faction, boolean canManageRows) {
        List rows = new ArrayList();
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        if (normalizedFaction.length() == 0) {
            return rows;
        }
        Set<UUID> playerIds = new HashSet<UUID>();
        for (Map.Entry<UUID, KOMEPlayerPopulation> entry : data.populations.entrySet()) {
            if (entry.getKey() != null && normalizedFaction.equals(data.getPlayerFactionKey(entry.getKey()))) {
                playerIds.add(entry.getKey());
            }
        }
        for (Map.Entry<UUID, String> entry : data.playerNames.entrySet()) {
            if (entry.getKey() != null && normalizedFaction.equals(data.getPlayerFactionKey(entry.getKey()))) {
                playerIds.add(entry.getKey());
            }
        }
        for (KOMEPlayerTilePopulationAllocation allocation : data.getAllocationsForFaction(normalizedFaction)) {
            if (allocation != null && allocation.playerUuid != null) {
                playerIds.add(allocation.playerUuid);
            }
        }
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record != null && record.owner != null && normalizedFaction.equals(data.getPlayerFactionKey(record.owner))) {
                playerIds.add(record.owner);
            }
        }
        for (UUID playerId : playerIds) {
            KOMEPacketPopulationGui.CapacityBreakdown row = new KOMEPacketPopulationGui.CapacityBreakdown();
            row.playerUuid = playerId == null ? "" : playerId.toString();
            row.playerName = getStoredPlayerName(data, playerId);
            row.canManage = canManageRows;
            KOMEPlayerPopulation population = playerId == null ? null : data.populations.get(playerId);
            row.offensiveTotal = population == null ? 0 : population.offensiveTotal;
            row.offensiveUsed = playerId == null ? 0 : data.getPlayerReservePopulationUsed(playerId, KOMEPopulationType.OFFENSIVE);
            row.defensiveTotal = population == null ? 0 : population.defensiveTotal;
            row.defensiveUsed = playerId == null ? 0 : data.getPlayerReservePopulationUsed(playerId, KOMEPopulationType.DEFENSIVE);
            row.reserveOffensiveTotal = row.offensiveTotal;
            row.reserveDefensiveTotal = row.defensiveTotal;
            for (KOMEPlayerTilePopulationAllocation allocation : data.getAllocationsForFaction(normalizedFaction)) {
                if (allocation != null && playerId != null && playerId.equals(allocation.playerUuid)) {
                    row.offensiveTotal += allocation.offensiveAllocated;
                    row.offensiveUsed += allocation.offensiveUsed;
                    row.defensiveTotal += allocation.defensiveAllocated;
                    row.defensiveUsed += allocation.defensiveUsed;
                    row.assignedOffensiveTotal += allocation.offensiveAllocated;
                    row.assignedDefensiveTotal += allocation.defensiveAllocated;
                }
            }
            row.sanitize();
            if (row.offensiveTotal > 0 || row.defensiveTotal > 0 || row.offensiveUsed > 0 || row.defensiveUsed > 0) {
                rows.add(row);
            }
        }
        Collections.sort(rows, new Comparator() {
            @Override
            public int compare(Object firstObject, Object secondObject) {
                KOMEPacketPopulationGui.CapacityBreakdown first = (KOMEPacketPopulationGui.CapacityBreakdown) firstObject;
                KOMEPacketPopulationGui.CapacityBreakdown second = (KOMEPacketPopulationGui.CapacityBreakdown) secondObject;
                return first.playerName.compareToIgnoreCase(second.playerName);
            }
        });
        return rows;
    }

    private List buildFactionTileCapacityRows(KOMEWorldData data, String faction) {
        List rows = new ArrayList();
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        if (normalizedFaction.length() == 0) {
            return rows;
        }
        List<KOMEConquestTile> tiles = new ArrayList<KOMEConquestTile>();
        for (KOMEConquestTile tile : data.conquestTiles.values()) {
            if (tile != null && tile.isClaimed() && normalizedFaction.equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
                tiles.add(tile);
            }
        }
        Collections.sort(tiles, new Comparator<KOMEConquestTile>() {
            @Override
            public int compare(KOMEConquestTile first, KOMEConquestTile second) {
                return first.id.compareTo(second.id);
            }
        });
        for (KOMEConquestTile tile : tiles) {
            KOMEPacketPopulationGui.TileBreakdown row = new KOMEPacketPopulationGui.TileBreakdown();
            row.tileId = KOMEConquestTile.normalizeId(tile.id);
            KOMETileWaypointLink waypointLink = data.getTileWaypointLink(row.tileId);
            row.tileDisplayName = waypointLink == null ? "" : waypointLink.displayName();
            row.ownerFaction = displayFaction(tile.currentRulingFaction());
            row.offensiveTotal = data.getEffectiveUsablePopulation(tile.id, normalizedFaction, KOMEPopulationType.OFFENSIVE);
            row.offensiveAllocated = data.getTotalAllocated(tile.id, normalizedFaction, KOMEPopulationType.OFFENSIVE);
            row.defensiveTotal = data.getEffectiveUsablePopulation(tile.id, normalizedFaction, KOMEPopulationType.DEFENSIVE);
            row.defensiveAllocated = data.getTotalAllocated(tile.id, normalizedFaction, KOMEPopulationType.DEFENSIVE);
            for (KOMETilePopulation population : data.getTilePopulationPools(tile.id)) {
                boolean ownerPool = KOMEAlliance.normalizeFactionKey(population.sourceFaction).equals(normalizedFaction);
                int effectiveFarmhands = ownerPool ? population.farmhandTotal : population.farmhandTotal / 2;
                row.farmhandTotal += effectiveFarmhands;
                row.farmhandUsed += Math.min(population.farmhandUsed, effectiveFarmhands);
            }
            row.sanitize();
            rows.add(row);
        }
        return rows;
    }

    private KOMEPacketPopulationGui.CapacityBreakdown buildUnallocatedCapacityRow(KOMEWorldData data, String faction, KOMEWorldData.EffectivePopulationSummary tileSummary) {
        KOMEPacketPopulationGui.CapacityBreakdown row = new KOMEPacketPopulationGui.CapacityBreakdown();
        row.playerName = "Unallocated";
        row.unallocated = true;
        row.offensiveTotal = Math.max(0, tileSummary.offensiveTotal - getFactionAllocatedTotal(data, faction, KOMEPopulationType.OFFENSIVE));
        row.defensiveTotal = Math.max(0, tileSummary.defensiveTotal - getFactionAllocatedTotal(data, faction, KOMEPopulationType.DEFENSIVE));
        row.offensiveUsed = 0;
        row.defensiveUsed = 0;
        row.sanitize();
        return row;
    }

    private int getFactionFarmhandTotal(KOMEWorldData data, String faction) {
        int total = 0;
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        Set<UUID> playerIds = new HashSet<UUID>();
        for (Map.Entry<UUID, KOMEPlayerPopulation> entry : data.populations.entrySet()) {
            if (entry.getKey() != null && normalizedFaction.equals(data.getPlayerFactionKey(entry.getKey()))) {
                playerIds.add(entry.getKey());
            }
        }
        for (UUID playerId : playerIds) {
            total += data.getFarmhandLimit(playerId);
        }
        return Math.max(0, total);
    }

    private int getFactionFarmhandsUsed(KOMEWorldData data, String faction) {
        int used = 0;
        String normalizedFaction = KOMEAlliance.normalizeFactionKey(faction);
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record != null && record.farmhand && record.owner != null && normalizedFaction.equals(data.getPlayerFactionKey(record.owner))) {
                used += Math.max(0, record.cost);
            }
        }
        return Math.max(0, used);
    }

    private int getFactionAllocatedTotal(KOMEWorldData data, String faction, KOMEPopulationType type) {
        int total = 0;
        for (KOMEPlayerTilePopulationAllocation allocation : data.getAllocationsForFaction(faction)) {
            if (allocation != null) {
                total += allocation.getAllocated(type);
            }
        }
        return total;
    }

    private int getFactionAllocatedUsed(KOMEWorldData data, String faction, KOMEPopulationType type) {
        int used = 0;
        for (KOMEPlayerTilePopulationAllocation allocation : data.getAllocationsForFaction(faction)) {
            if (allocation != null) {
                used += allocation.getUsed(type);
            }
        }
        return used;
    }

    private String getStoredPlayerName(KOMEWorldData data, UUID playerId) {
        String name = playerId == null ? "" : data.playerNames.get(playerId);
        return name == null || name.trim().length() == 0 ? "Unknown Player" : name;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void sendUnitBreakdown(ICommandSender sender, EntityPlayerMP player, String filterTile) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        UUID playerID = KOMEReflection.getEntityUUID(player);
        int farmhandsUsed = data.getFarmhandsUsed(playerID);
        String faction = data.getPlayerFactionKey(playerID);
        List units = new ArrayList();
        for (KOMEHiredUnitRecord record : kome.common.data.KOMEPopulationService.livingRecords(data)) {
            if (!playerID.equals(record.owner)) {
                continue;
            }
            if (filterTile.length() > 0) {
                if (!filterTile.equals(KOMEConquestTile.normalizeId(record.currentTile))
                        || record.movementOrderId != null && record.movementOrderId.length() > 0) {
                    continue;
                }
            }
            units.add(buildUnitGuiEntry(data, record));
        }
        Collections.sort(units, new Comparator() {
            @Override
            public int compare(Object left, Object right) {
                KOMEUnitGuiEntry a = (KOMEUnitGuiEntry) left;
                KOMEUnitGuiEntry b = (KOMEUnitGuiEntry) right;
                int tileCompare = a.currentTile.compareToIgnoreCase(b.currentTile);
                return tileCompare != 0 ? tileCompare : a.unitName.compareToIgnoreCase(b.unitName);
            }
        });
        if (sender instanceof EntityPlayerMP) {
            KOMEPacketHandler.network.sendTo(new KOMEPacketPopulationUnitsGui(player.getCommandSenderName(), filterTile, units, kome.common.data.KOMEPopulationProjection.of(data, faction), farmhandsUsed), (EntityPlayerMP) sender);
            return;
        }
        sender.addChatMessage(new ChatComponentText(player.getCommandSenderName() + " tracked units:"));
        if (units.isEmpty()) {
            sender.addChatMessage(new ChatComponentText("No tracked hired units."));
        } else {
            for (Object object : units) {
                KOMEUnitGuiEntry unit = (KOMEUnitGuiEntry) object;
                sender.addChatMessage(new ChatComponentText(unit.unitName + ": current " + tileLabel(unit.currentTile)
                    + ", hired from " + ("PLAYER_RESERVE".equals(unit.sourceType) ? "Player Reserve" : tileLabel(unit.sourceTile))
                    + ", permanently invested " + kome.common.data.KOMEPopulationProjection.formatCenti(unit.populationSpentCenti)
                    + (unit.farmhand ? " (farmhand: excluded)" : " (no refund)") + ", " + unit.movementStatus + "."));
            }
        }
    }

    private KOMEUnitGuiEntry buildUnitGuiEntry(KOMEWorldData data, KOMEHiredUnitRecord record) {
        KOMEUnitGuiEntry unit = new KOMEUnitGuiEntry();
        unit.entityId = record.entity == null ? "" : record.entity.toString();
        unit.unitName = record.farmhand ? getFarmhandDisplayName(record) : getUnitDisplayName(record);
        unit.ownerName = knownPlayerName(data, record.owner);
        String ownerFaction = data.getPlayerFactionKey(record.owner);
        unit.factionName = displayFaction(kome.common.data.KOMEPopulationService.populationFaction(record));
        unit.populationType = record.farmhand ? "Farmhand" : record.type == KOMEPopulationType.DEFENSIVE ? "Defensive" : "Offensive";
        unit.populationCost = record.farmhand ? 0 : Math.max(0, record.cost);
        unit.populationSpentCenti = kome.common.data.KOMEPopulationService.getInvestmentCenti(record);
        unit.farmhand = record.farmhand;
        unit.mounted = record.mounted;
        unit.currentTile = KOMEConquestTile.normalizeId(record.currentTile);
        unit.sourceType = record.sourceType;
        unit.sourceTile = KOMEConquestTile.normalizeId(record.sourceTileId);
        unit.sourceFaction = displayFaction(record.sourceFaction);
        unit.sourcePlayer = knownPlayerName(data, record.sourcePlayer == null ? record.owner : record.sourcePlayer);
        unit.allocationTile = KOMEConquestTile.normalizeId(record.allocationTileId);
        unit.allocationPlayer = knownPlayerName(data, record.allocationPlayer);
        unit.levelCap = Math.max(0, record.levelCap);
        unit.companyId = record.companyId == null ? "" : record.companyId;
        unit.companyName = record.lotrCompanyValue == null ? "" : record.lotrCompanyValue;
        unit.haltedProtected = kome.common.data.KOMEHaltedUnitProtection.isProtectedRecord(data, null, record);
        kome.common.data.KOMEArmyCompany company = data.armyCompanies.get(unit.companyId);
        if (company != null) {
            unit.companyName = company.name;
            unit.companyStatus = company.isMoving() ? "Moving" : "Stationed";
        } else if (unit.companyName.length() > 0) {
            unit.companyStatus = record.isMoving() ? "Moving" : "Stationed";
        }

        KOMEArmyMovementOrder order = record.movementOrderId == null ? null : data.armyMovements.get(record.movementOrderId);
        if (order != null && order.isMoving()) {
            unit.movementStatus = order.isPendingSpawn() ? "Pending Spawn" : "Moving";
            unit.movementOrderId = order.id;
            unit.destinationTile = order.destinationTile;
            unit.etaMillis = order.getRemainingMillis(System.currentTimeMillis());
        } else if (order != null) {
            unit.movementStatus = "Arrival Pending";
            unit.movementOrderId = order.id;
            unit.destinationTile = order.destinationTile;
        } else {
            unit.movementStatus = "Stationed";
        }

        if (unit.haltedProtected) {
            unit.canMove = false;
            unit.cannotMoveReason = "Halted: Protected / Inactive";
        } else if (record.farmhand) {
            unit.canMove = false;
            unit.cannotMoveReason = "Farmhands are not military units";
            unit.releasesTo = "0.00 population; excluded";
        } else if (record.type == KOMEPopulationType.DEFENSIVE) {
            unit.canMove = false;
            unit.cannotMoveReason = "Defensive units cannot move";
        } else if (!"Stationed".equals(unit.movementStatus)) {
            unit.canMove = false;
            unit.cannotMoveReason = "Unit is assigned to movement order " + unit.movementOrderId;
        } else if (unit.currentTile.length() == 0) {
            unit.canMove = false;
            unit.cannotMoveReason = "Unit is not stationed in a tile";
        } else if (!canCompanyStandOnTile(data, unit.currentTile, ownerFaction)) {
            unit.canMove = false;
            unit.cannotMoveReason = "Current tile is not controlled by the unit owner's faction or an allied Military passage";
        } else {
            unit.canMove = true;
        }

        if (!record.farmhand) unit.releasesTo = "No refund (permanently spent)";
        return unit;
    }

    private boolean canCompanyStandOnTile(KOMEWorldData data, String tileId, String factionKey) {
        return data.canFactionStandOnTile(tileId, factionKey);
    }

    private String knownPlayerName(KOMEWorldData data, UUID playerId) {
        if (playerId == null) {
            return "";
        }
        String name = data.playerNames.get(playerId);
        if (name != null && name.length() > 0) {
            return name;
        }
        return playerId.toString().substring(0, 8);
    }

    private String tileLabel(String tile) {
        String normalized = KOMEConquestTile.normalizeId(tile);
        return normalized.length() == 0 ? "Unstationed" : "Tile " + normalized;
    }

    private String shortID(UUID id) {
        String value = id == null ? "Unknown unit" : id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }

    private String getUnitDisplayName(kome.common.data.KOMEHiredUnitRecord record) {
        return record.unitName == null || record.unitName.trim().isEmpty() ? shortID(record.entity) : record.unitName;
    }

    private String getFarmhandDisplayName(kome.common.data.KOMEHiredUnitRecord record) {
        String name = getUnitDisplayName(record);
        return isNumberOnly(name) || shortID(record.entity).equals(name) ? "Farmhand" : name;
    }

    private boolean isNumberOnly(String value) {
        return value != null && value.trim().matches("[0-9]+");
    }

    @Override
    public java.util.List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) return getListOfStringsMatchingLastWord(args, "get", "gui", "units", "tile", "faction", "rate");
        if (args.length == 2 && ("get".equalsIgnoreCase(args[0]) || "gui".equalsIgnoreCase(args[0]) || "units".equalsIgnoreCase(args[0])))
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        return null;
    }

    private String displayFaction(String key) {
        return KOMEAlliance.displayFactionName(key);
    }

    private String getPlayerFaction(KOMEWorldData data, EntityPlayerMP player) {
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        return pledge == null ? data.getPlayerFactionKey(KOMEReflection.getEntityUUID(player)) : KOMEAlliance.normalizeFactionKey(pledge.codeName());
    }

    private String formatName(String key) {
        if (key == null || key.trim().length() == 0) {
            return "None";
        }
        String[] words = key.replace('_', ' ').replace('-', ' ').trim().split("\\s+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.length() == 0) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
