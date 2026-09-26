package kome.common.command;

import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEPopulationService;
import kome.common.data.KOMEPopulationProjection;
import kome.common.data.KOMEAuditService;
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

public class KOMECommandPopulation extends KOMEPublicCommand {
    @Override
    public String getCommandName() {
        return "population";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        if (!isStaff(sender)) return "/population get | gui | units | tile <tile> | faction <faction> | rate [faction] (player details are self-only)";
        return "/population get [player] | gui [player] | units [player] [tile] | tile <tile> | faction <faction> | rate [faction] | grant <faction> <amount>";
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
            if (args.length > 2) throw new WrongUsageException(getCommandUsage(sender));
            EntityPlayerMP player = privateInspectionTarget(sender, args.length == 2 ? args[1] : null);
            sendStatus(sender, player, "gui".equalsIgnoreCase(args[0]));
            return;
        }
        if ("units".equalsIgnoreCase(args[0])) {
            if (args.length > 3) {
                throw new WrongUsageException("/population units [player] [tile]");
            }
            EntityPlayerMP player = privateInspectionTarget(sender, args.length >= 2 ? args[1] : null);
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
        if ("grant".equalsIgnoreCase(args[0])) {
            grantPopulation(sender, args);
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private void grantPopulation(ICommandSender sender, String[] args) {
        if (!isStaff(sender)) {
            throw new WrongUsageException("Only operators may grant Available Population.");
        }
        if (args.length != 3) {
            throw new WrongUsageException("/population grant <faction> <amount>");
        }
        LOTRFaction resolved = KOMEAlliance.findLotrFaction(args[1]);
        if (resolved == null || !resolved.isPlayableAlignmentFaction()) {
            throw new WrongUsageException("Unsupported playable faction: " + args[1]);
        }
        String faction = KOMEAlliance.normalizeFactionKey(resolved.codeName());
        if (!KOMEAlliance.allFactionKeys().contains(faction)) {
            throw new WrongUsageException("Unsupported playable faction: " + args[1]);
        }
        long amountCenti = parsePositivePopulationCenti(args[2]);
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        try {
            KOMEPopulationService.grantCenti(data, faction, amountCenti);
        } catch (ArithmeticException overflow) {
            throw new WrongUsageException("Population grant would overflow the faction's Available Population.");
        }
        long resulting = KOMEPopulationService.getAvailablePopulationCenti(data, faction);
        String actor = sender instanceof EntityPlayerMP
            ? KOMEReflection.getEntityUUID((EntityPlayerMP) sender).toString()
            : sender.getCommandSenderName();
        KOMEAuditService.record(data, System.currentTimeMillis(), "POPULATION",
            "ADMIN_GRANT", actor, faction, "Administrative population grant",
            "faction=" + faction + ";grantedCenti=" + amountCenti
                + ";availableCenti=" + resulting);
        data.syncConquestTiles();
        sender.addChatMessage(new ChatComponentText("Granted "
            + KOMEPopulationProjection.formatCenti(amountCenti) + " Available Population to "
            + displayFaction(faction) + "; resulting Available Population: "
            + KOMEPopulationProjection.formatCenti(resulting) + "."));
    }

    private static long parsePositivePopulationCenti(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new WrongUsageException("Population amount must be a positive exact number with at most two decimal places.");
        }
        try {
            long result = new java.math.BigDecimal(text).movePointRight(2).longValueExact();
            if (result <= 0L) {
                throw new WrongUsageException("Population amount must be greater than zero.");
            }
            return result;
        } catch (ArithmeticException invalid) {
            throw new WrongUsageException("Population amount is too large.");
        }
    }

    /** Canonical Build-rate audit plus operator-only persisted payout diagnostics. */
    private void sendRateAudit(ICommandSender sender, String[] args) {
        if (args.length > 2) throw new WrongUsageException("/population rate [faction]");
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        String requested = args.length == 2 ? KOMEAlliance.normalizeFactionKey(args[1]) : "";
        if (requested.length() > 0) sender.addChatMessage(new ChatComponentText("Faction " + displayFaction(requested)
                + " Daily Population Rate: " + kome.common.data.KOMEPopulationProjection.formatRate(kome.common.data.KOMEPopulationProjection.of(data, requested).dailyRateUnits)));
        for (kome.common.data.KOMEPopulationRateContribution row : kome.common.data.KOMEPopulationService.getPopulationRateContributions(data)) {
            if (requested.length() > 0 && !requested.equals(row.populationFaction)
                    && !requested.equals(row.receivingFaction)) continue;
            sender.addChatMessage(new ChatComponentText("Build " + row.buildId + " " + row.displayName + " tile " + row.tileId
                    + " " + row.populationFaction + " -> " + (row.currentController.length() == 0 ? "UNCONTROLLED" : row.currentController)
                    + ": approved/developed/pending "
                    + kome.common.data.KOMEBuildTime.formatHours(row.approvedCentiHours) + "/"
                    + kome.common.data.KOMEBuildTime.formatHours(row.developedNativeCentiHours) + "/"
                    + kome.common.data.KOMEBuildTime.formatHours(row.pendingNativeCentiHours)
                    + "h, native capacity (diagnostic only) " + row.formatOriginalRate()
                    + " x" + row.multiplier + ", " + row.status + ", current recipient "
                    + (row.receivingFaction.length() == 0 ? "NONE" : row.receivingFaction)
                    + ", current contribution " + row.formatCurrentRate()));
        }
        if (sender.canCommandSenderUseCommand(2, getCommandName())) {
            for (String line : kome.common.data.KOMEPopulationPayoutProcessor.inspection(data))
                sender.addChatMessage(new ChatComponentText(line));
            for (String line : kome.common.data.KOMEPopulationDevelopmentService.inspection(data))
                sender.addChatMessage(new ChatComponentText(line));
        }
    }

    private void sendTileStatus(ICommandSender sender, String[] args) {
        if (args.length != 2) throw new WrongUsageException("/population tile <tileId>");
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        String tileId = KOMEConquestTile.normalizeId(args[1]);
        KOMEConquestTile tile = data.getPublicConquestTile(tileId);
        if (tile == null) throw new WrongUsageException("Unknown or unavailable public tile: " + tileId);
        sender.addChatMessage(new ChatComponentText("Tile " + tileId + ": " + kome.common.data.KOMEPopulationProjection.of(
                data, tile.projectRulingFaction()).summary()));
    }

    private void sendFactionStatus(ICommandSender sender, String[] args) {
        if (args.length != 2) throw new WrongUsageException("/population faction <faction>");
        sender.addChatMessage(new ChatComponentText(kome.common.data.KOMEPopulationProjection.of(
                KOMEWorldData.get(sender.getEntityWorld()), args[1]).summary()));
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
            java.util.Map<String, KOMEPacketPopulationGui.PlayerInvestment> players =
                new java.util.TreeMap<String, KOMEPacketPopulationGui.PlayerInvestment>();
            for (KOMEHiredUnitRecord record : kome.common.data.KOMEPopulationService.livingRecords(data)) {
                if (record == null || record.farmhand || record.owner == null
                        || !faction.equals(kome.common.data.KOMEPopulationService.populationFaction(record))) continue;
                String id = record.owner.toString();
                KOMEPacketPopulationGui.PlayerInvestment row = players.get(id);
                if (row == null) {
                    row = new KOMEPacketPopulationGui.PlayerInvestment();
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
                KOMEConquestTile tile = data.getPublicConquestTile(tileId);
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

    private String getStoredPlayerName(KOMEWorldData data, UUID playerId) {
        String name = playerId == null ? "" : data.playerNames.get(playerId);
        return name == null || name.trim().length() == 0 ? "Unknown Player" : name;
    }

    private void sendUnitBreakdown(ICommandSender sender, EntityPlayerMP player, String filterTile) {
        KOMEWorldData data = KOMEWorldData.get(KOMEReflection.getWorld(player));
        if (!filterTile.isEmpty() && data.getPublicConquestTile(filterTile) == null)
            throw new WrongUsageException("Unknown or unavailable public tile: " + filterTile);
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
                    + ", hired from " + ("PLAYER_RESERVE".equals(unit.sourceType) ? "Historical PLAYER_RESERVE source" : tileLabel(unit.sourceTile))
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
        if (args.length == 1) return isStaff(sender)
            ? getListOfStringsMatchingLastWord(args, "get", "gui", "units", "tile", "faction", "rate", "grant")
            : getListOfStringsMatchingLastWord(args, "get", "gui", "units", "tile", "faction", "rate");
        if (args.length == 2 && ("get".equalsIgnoreCase(args[0]) || "gui".equalsIgnoreCase(args[0]) || "units".equalsIgnoreCase(args[0])))
            return isStaff(sender) ? getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames())
                : Collections.emptyList();
        if (args.length == 2 && "grant".equalsIgnoreCase(args[0]) && isStaff(sender))
            return getListOfStringsMatchingLastWord(args,
                KOMEAlliance.allFactionKeys().toArray(new String[KOMEAlliance.allFactionKeys().size()]));
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
