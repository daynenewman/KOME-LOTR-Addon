package kome.common.command;

import kome.common.KOMEReflection;
import kome.common.data.KOMEArmyMovementOrder;
import kome.common.data.KOMEArmyCompany;
import kome.common.data.KOMECompanyDiplomacyAuthorization;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceAuthority;
import kome.common.data.KOMEAllianceProgressionService;
import kome.common.data.KOMEAllianceTemporaryCommandPolicy;
import kome.common.data.KOMEConquestRouteEdge;
import kome.common.data.KOMEConquestTile;
import kome.common.data.KOMEConquestTileDefaults;
import kome.common.data.KOMEEntitySnapshots;
import kome.common.data.KOMEHaltedUnitProtection;
import kome.common.data.KOMEHiredUnitRecord;
import kome.common.data.KOMEMovementHistoryRecord;
import kome.common.data.KOMEMovementAccessService;
import kome.common.data.KOMEMovementRecoveryOptions;
import kome.common.data.KOMEPopulationType;
import kome.common.data.KOMEPledgeReleaseService;
import kome.common.data.KOMETileWaypointLink;
import kome.common.data.KOMETileWaypoint;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMEWartimeStewardshipService;
import kome.common.data.KOMEWarService;
import kome.common.data.KOMECompanyTransferService;
import kome.common.KOMEAddon;
import kome.common.network.KOMECompanyGuiEntry;
import kome.common.network.KOMEPacketCompanyListGui;
import kome.common.network.KOMEPacketCompanyMoveConfirmGui;
import kome.common.network.KOMEPacketCompanyMovePreviewResult;
import kome.common.network.KOMEPacketHandler;
import kome.common.network.KOMEPacketMovementHistoryData;
import lotr.common.LOTRLevelData;
import lotr.common.entity.npc.LOTREntityNPC;
import lotr.common.entity.npc.LOTRHiredNPCInfo;
import lotr.common.fac.LOTRFaction;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraftforge.common.ForgeChunkManager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;

public class KOMECommandTroops extends CommandBase {
    @Override
    public String getCommandName() {
        return "troops";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/troops ... | company <id> [tendency <aggressive|conservative>|delegate <player>|reclaim|transfer <player>|acceptTransfer|rejectTransfer|cancelTransfer] | pledgeRelease <preview|status|retry|resolve> ... | previewmove|movecompany ... | movement <stay|retreat|stop|...> <orderId> | ...";
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
        if ("pledgeRelease".equalsIgnoreCase(args[0])) {
            handlePledgeRelease(sender, data, owner, args);
            return;
        }
        if ("list".equalsIgnoreCase(args[0])) {
            String tile = args.length >= 2 ? KOMEConquestTile.normalizeId(args[1]) : "";
            listTroops(sender, data, owner, tile);
            return;
        }
        if ("tile".equalsIgnoreCase(args[0])) {
            if (args.length == 3) {
                handleCompanyProtectionCommand(sender, data, KOMEReflection.getWorld(player), owner, args[1], args[2]);
                return;
            }
            if (args.length != 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            listTileTroops(sender, data, parseTile(args[1]));
            return;
        }
        if ("debugtile".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            debugTile(sender, player, data, args[1]);
            return;
        }
        if ("route".equalsIgnoreCase(args[0])) {
            handleRoute(sender, player, data, owner, args);
            return;
        }
        if ("unit".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            listUnit(sender, data, KOMEReflection.getWorld(player), args[1]);
            return;
        }
        if ("companies".equalsIgnoreCase(args[0])) {
            String tile = args.length >= 2 ? parseTile(args[1]) : "";
            listCompanies(sender, player, data, owner, tile);
            return;
        }
        if ("company".equalsIgnoreCase(args[0])) {
            if (args.length >= 2 && "rebuild".equalsIgnoreCase(args[1])) {
                handleCompanyAssignmentCommand(sender, player, data, owner, args);
                return;
            }
            if (args.length >= 3) {
                handleCompanyAuthorityCommand(sender, player, data, args);
                return;
            }
            if (args.length != 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            listCompany(sender, player, data, KOMEReflection.getWorld(player), args[1]);
            return;
        }
        if ("arrivals".equalsIgnoreCase(args[0])) {
            if (args.length > 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String tile = args.length == 2 ? parseTile(args[1]) : "";
            listArrivals(sender, data, KOMEReflection.getWorld(player), owner, tile);
            return;
        }
        if ("locate".equalsIgnoreCase(args[0])) {
            if (args.length != 3) {
                throw new WrongUsageException("/troops locate <order|company|unit> <id>");
            }
            locateTroops(sender, data, KOMEReflection.getWorld(player), owner, args[1], args[2]);
            return;
        }
        if ("createcompany".equalsIgnoreCase(args[0])) {
            throw new WrongUsageException("Manual company creation was retired. Each combat hire automatically joins the owner's persistent hiring-tile company.");
        }
        if ("snapshotcompany".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                throw new WrongUsageException("/troops snapshotcompany <companyId>");
            }
            snapshotCompany(sender, player, data, owner, args[1]);
            return;
        }
        if ("previewmove".equalsIgnoreCase(args[0])) {
            if (args.length != 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            previewCompanyMove(sender, player, data, owner, args[1], parseTile(args[2]));
            return;
        }
        if ("movecompany".equalsIgnoreCase(args[0])) {
            if (args.length != 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            moveCompany(sender, player, data, owner, args[1], parseTile(args[2]));
            return;
        }
        if ("moving".equalsIgnoreCase(args[0])) {
            listMoving(sender, data, KOMEReflection.getWorld(player), owner);
            return;
        }
        if ("history".equalsIgnoreCase(args[0])) {
            handleMovementHistory(sender, player, data, args);
            return;
        }
        if ("movetime".equalsIgnoreCase(args[0])) {
            handleMoveTime(sender, data, args);
            return;
        }
        if ("movement".equalsIgnoreCase(args[0])) {
            handleMovementAdmin(sender, player, data, args);
            return;
        }
        if ("arrival".equalsIgnoreCase(args[0])) {
            handleArrival(sender, player, data, args);
            return;
        }
        if ("waypoint".equalsIgnoreCase(args[0])) {
            handleWaypoint(sender, player, data, args);
            return;
        }
        if ("anchor".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            setArrivalPoint(sender, player, data, parseTile(args[1]));
            return;
        }
        if ("recruit".equalsIgnoreCase(args[0])) {
            if (args.length != 2) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            setRecruitmentTile(sender, player, data, owner, args[1]);
            return;
        }
        if ("station".equalsIgnoreCase(args[0])) {
            if (args.length < 2 || args.length > 3) {
                throw new WrongUsageException(getCommandUsage(sender));
            }
            String tile = parseTile(args[1]);
            boolean all = args.length >= 3 && "all".equalsIgnoreCase(args[2]);
            stationTroops(sender, player, data, owner, tile, all);
            return;
        }
        if ("arrive".equalsIgnoreCase(args[0])) {
            processArrivals(data, KOMEReflection.getWorld(player), System.currentTimeMillis(), true);
            sender.addChatMessage(new ChatComponentText("Processed due troop movements."));
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private void handlePledgeRelease(ICommandSender sender, KOMEWorldData data, UUID actor, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException("/troops pledgeRelease <preview|status|retry|resolve> ...");
        }
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        if ("resolve".equals(action)) {
            if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                throw new WrongUsageException("Pledge-release resolution is operator-only.");
            }
            if (args.length != 4) throw new WrongUsageException("/troops pledgeRelease resolve <unitUuid> <removed|quarantine>");
            UUID unit;
            try { unit = UUID.fromString(args[2]); }
            catch (IllegalArgumentException error) { throw new WrongUsageException("Invalid unit UUID."); }
            KOMEPledgeReleaseService.Result result = KOMEPledgeReleaseService.resolve(data, unit, args[3],
                sender.getCommandSenderName(), System.currentTimeMillis());
            if (result.failed) throw new WrongUsageException(result.summary);
            sender.addChatMessage(new ChatComponentText(result.summary));
            return;
        }
        if (args.length != 3) throw new WrongUsageException("/troops pledgeRelease " + action + " <player>");
        UUID target = findKnownPlayerId(data, args[2]);
        if (target == null) throw new WrongUsageException("Unknown player. The player must have joined this world at least once.");
        if (!actor.equals(target) && !sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("You may only inspect your own pledge-departure state.");
        }
        String targetName = data.playerNames.get(target);
        String faction = KOMEAlliance.normalizeFactionKey(data.lastKnownPlayerFactions.get(target));
        if (faction.length() == 0) faction = data.getPlayerFactionKey(target);
        if ("preview".equals(action)) {
            sender.addChatMessage(new ChatComponentText(KOMEPledgeReleaseService.preview(data, target, faction).describe(targetName)));
            return;
        }
        if ("status".equals(action)) {
            sender.addChatMessage(new ChatComponentText(KOMEPledgeReleaseService.status(data, target)));
            return;
        }
        if ("retry".equals(action)) {
            if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                throw new WrongUsageException("Pledge-release retry is operator-only.");
            }
            sender.addChatMessage(new ChatComponentText(KOMEPledgeReleaseService.retry(data, target,
                System.currentTimeMillis()).summary));
            return;
        }
        throw new WrongUsageException("/troops pledgeRelease <preview|status|retry|resolve> ...");
    }

    private UUID findKnownPlayerId(KOMEWorldData data, String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException ignored) {
        }
        EntityPlayerMP online = MinecraftServer.getServer().getConfigurationManager().func_152612_a(value);
        if (online != null) return KOMEReflection.getEntityUUID(online);
        for (Map.Entry<UUID, String> entry : data.playerNames.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equalsIgnoreCase(value)) return entry.getKey();
        }
        return null;
    }

    private void listTroops(ICommandSender sender, KOMEWorldData data, UUID owner, String tile) {
        int stationedPop = 0;
        int stationedUnits = 0;
        int movingPop = 0;
        int movingUnits = 0;
        List<String> details = new ArrayList<String>();
        for (KOMEHiredUnitRecord record : new ArrayList<KOMEHiredUnitRecord>(data.hiredUnits.values())) {
            if (record == null || record.farmhand || !owner.equals(record.owner)) {
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
                details.add(formatUnitLine(data, record));
            }
        }
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order != null && owner.equals(order.owner) && order.isMoving()) {
                String stepOrigin = activeStepOrigin(order);
                String stepDestination = activeStepDestination(order);
                if (tile.length() == 0 || tile.equals(stepOrigin) || tile.equals(stepDestination)) {
                    details.add(order.id + ": " + stepOrigin + " -> " + stepDestination
                        + " (final " + order.destinationTile + "), pop "
                        + order.population + " (mounted " + order.mountedPopulation + ", ground " + order.groundPopulation
                        + "), filter " + order.filter + ", " + formatMovementClock(order, System.currentTimeMillis()));
                }
            }
        }
        Collections.sort(details);
        sender.addChatMessage(new ChatComponentText("Troops" + (tile.length() > 0 ? " at " + tile : "") + ": stationed " + stationedUnits + " units / " + stationedPop + " pop, moving " + movingUnits + " units / " + movingPop + " pop."));
        for (String line : details) {
            sender.addChatMessage(new ChatComponentText(line));
        }
    }

    private void listTileTroops(ICommandSender sender, KOMEWorldData data, String tile) {
        KOMEConquestTile conquestTile = data.conquestTiles.get(tile);
        String ownerFaction = conquestTile == null ? "" : conquestTile.currentRulingFaction();
        List<String> details = new ArrayList<String>();
        int offensive = 0;
        int defensive = 0;
        int mounted = 0;
        int ground = 0;
        List<String> stationedIds = new ArrayList<String>();
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record == null || record.farmhand || !tile.equals(KOMEConquestTile.normalizeId(record.currentTile))
                    || record.movementOrderId != null && record.movementOrderId.length() > 0) {
                continue;
            }
            if (record.type == KOMEPopulationType.DEFENSIVE) {
                defensive += record.cost;
            } else {
                offensive += record.cost;
                if (record.mounted) {
                    mounted += record.cost;
                } else {
                    ground += record.cost;
                }
            }
            details.add(formatUnitLine(data, record));
            stationedIds.add(displayUnitId(record));
        }
        Collections.sort(details);
        Collections.sort(stationedIds);
        sender.addChatMessage(new ChatComponentText("Tile " + tile + " stationed units for " + displayFaction(ownerFaction)
            + ": Offensive " + offensive + " (mounted " + mounted + ", ground " + ground + "), Defensive " + defensive + " (immobile)."));
        sender.addChatMessage(new ChatComponentText("Stationed records counted: "
            + (stationedIds.isEmpty() ? "none" : joinDebugList(stationedIds)) + "."));
        if (details.isEmpty()) {
            sender.addChatMessage(new ChatComponentText("No tracked warriors are stationed at this tile."));
        } else {
            for (String line : details) {
                sender.addChatMessage(new ChatComponentText(line));
            }
        }
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order == null || !order.isMoving()) {
                continue;
            }
            String stepOrigin = activeStepOrigin(order);
            String stepDestination = activeStepDestination(order);
            if (tile.equals(stepOrigin) || tile.equals(stepDestination)) {
                String direction = tile.equals(stepOrigin) ? "Outgoing" : "Incoming";
                sender.addChatMessage(new ChatComponentText(direction + " movement " + order.id + ": " + order.ownerName + ", "
                    + stepOrigin + " -> " + stepDestination + " (final " + order.destinationTile + "), " + order.population + " pop (mounted "
                    + order.mountedPopulation + ", ground " + order.groundPopulation + "), filter " + order.filter
                    + ", " + order.units.size() + " unit records, " + formatMovementClock(order, System.currentTimeMillis()) + "."));
            }
        }
    }

    private void debugTile(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, String rawTile) {
        String normalizedTile = parseTile(rawTile);
        KOMEConquestTile tile = KOMEConquestTile.isCanonicalTileId(normalizedTile) ? data.getConquestTile(normalizedTile) : data.conquestTiles.get(normalizedTile);
        String playerFaction = normalizeFaction(getPlayerFaction(data, player));
        sender.addChatMessage(new ChatComponentText("Tile debug: raw " + rawTile + ", normalized " + normalizedTile + "."));
        sender.addChatMessage(new ChatComponentText("Player faction: " + displayFaction(playerFaction) + " (" + emptyKey(playerFaction) + ")."));
        if (tile == null) {
            sender.addChatMessage(new ChatComponentText("Server conquest data: tile record not found."));
            return;
        }
        String owner = normalizeFaction(tile.currentRulingFaction());
        String defaultOwner = normalizeFaction(tile.defaultRulingFaction);
        sender.addChatMessage(new ChatComponentText("Current Ruling Faction: " + (owner.length() == 0 ? "Unclaimed" : displayFaction(owner))
            + " (" + emptyKey(owner) + "). Claimed: " + (tile.isClaimed() ? "yes" : "no") + "."));
        sender.addChatMessage(new ChatComponentText("Default Ruling Faction: " + (defaultOwner.length() == 0 ? "Unclaimed" : displayFaction(defaultOwner))
            + " (" + emptyKey(defaultOwner) + "). Level " + (tile.waypointLevel >= 1 && tile.waypointLevel <= 3 ? tile.waypointLevel : 0)
            + ", region " + emptyKey(tile.mapRegion) + "."));
        sender.addChatMessage(new ChatComponentText("Current player owns tile: " + (owner.length() > 0 && owner.equals(playerFaction) ? "yes" : "no") + "."));
        sender.addChatMessage(new ChatComponentText("Claimant: " + (tile.claimedByName == null || tile.claimedByName.length() == 0 ? "Unknown" : tile.claimedByName)
            + (tile.claimedByUuid == null ? "" : " (" + tile.claimedByUuid + ")") + "."));
        if (tile.hasPendingTransfer()) {
            sender.addChatMessage(new ChatComponentText("Pending transfer: " + displayFaction(tile.pendingTransferFromFaction)
                + " -> " + displayFaction(tile.pendingTransferToFaction) + "."));
        }
        KOMETileWaypointLink waypointLink = data.getTileWaypointLink(normalizedTile);
        if (waypointLink == null) {
            sender.addChatMessage(new ChatComponentText("LOTR waypoint link: missing."));
        } else {
            sender.addChatMessage(new ChatComponentText("LOTR waypoint link: " + waypointLink.displayName() + " (" + waypointLink.lotrWaypointKey
                + "), region " + emptyKey(waypointLink.waypointRegion) + ", map " + Math.round(waypointLink.waypointMapX)
                + ", " + Math.round(waypointLink.waypointMapZ) + "."));
        }
        sender.addChatMessage(new ChatComponentText("Anchor: " + (tile.hasAnchor ? "yes, dim " + tile.anchorDimension + " at "
            + MathHelper.floor_double(tile.anchorX) + ", " + MathHelper.floor_double(tile.anchorY) + ", "
            + MathHelper.floor_double(tile.anchorZ) : "no") + "."));
        KOMETileWaypoint arrival = getArrivalPoint(data, normalizedTile);
        sender.addChatMessage(new ChatComponentText("Arrival Point: " + (arrival == null ? "missing; use /troops arrival set " + normalizedTile
            : "dim " + arrival.dimensionId + " at " + formatBlockPos(arrival.x, arrival.y, arrival.z) + " (" + arrivalSource(arrival) + ")") + "."));
        sender.addChatMessage(new ChatComponentText("Arrival waypoints: " + waypointSummary(data, normalizedTile) + "."));
        sender.addChatMessage(new ChatComponentText("Route neighbors: " + routeNeighborSummary(data, normalizedTile) + "."));
    }

    private void handleRoute(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, UUID owner, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException("/troops route <adjacent|edge|addedge|removeedge|bridge|passage|block|unblock|find|validate|trace|graphstats> ...");
        }
        String action = args[1].toLowerCase();
        if ("graphstats".equals(action)) {
            int autoEdges = countAutoRouteEdges(data);
            int autoRivers = countAutoRiverRouteEdges(data);
            int autoBridges = countAutoBridgeRouteEdges(data);
            int autoBridgeMarkers = kome.common.data.KOMEConquestTileDefaults.getAutomaticBridgeMarkerCount();
            int bridge = 0;
            int passage = 0;
            int blocked = 0;
            for (KOMEConquestRouteEdge edge : data.routeEdges.values()) {
                if (edge == null) {
                    continue;
                }
                if (KOMEConquestRouteEdge.BRIDGE.equals(edge.edgeType)) {
                    bridge++;
                } else if (KOMEConquestRouteEdge.MOUNTAIN_PASS.equals(edge.edgeType)) {
                    passage++;
                } else if (!edge.isPassable()) {
                    blocked++;
                }
            }
            sender.addChatMessage(new ChatComponentText("Route graph: " + data.getRouteGraphTiles().size()
                + " tile(s), " + autoEdges + " automatic edge(s), " + data.routeEdges.size()
                + " override(s), automatic rivers " + autoRivers + ", automatic bridge edges " + autoBridges
                + ", automatic bridge markers " + autoBridgeMarkers + ", bridges " + bridge + ", passages " + passage
                + ", blocked " + blocked + "."));
            return;
        }
        if ("adjacent".equals(action)) {
            if (args.length != 3) {
                throw new WrongUsageException("/troops route adjacent <tileId>");
            }
            String tile = parseTile(args[2]);
            sender.addChatMessage(new ChatComponentText("Adjacent to " + tile + ": " + routeNeighborSummary(data, tile) + "."));
            return;
        }
        if ("edge".equals(action)) {
            if (args.length != 4) {
                throw new WrongUsageException("/troops route edge <tileA> <tileB>");
            }
            String tileA = parseTile(args[2]);
            String tileB = parseTile(args[3]);
            KOMEConquestRouteEdge edge = data.getRouteEdge(tileA, tileB);
            sender.addChatMessage(new ChatComponentText(edge == null ? "No route edge between " + tileA + " and " + tileB + "."
                : describeRouteEdge(edge, data.getRouteEdgeOverride(tileA, tileB) != null)));
            return;
        }
        if ("find".equals(action)) {
            if (args.length != 4) {
                throw new WrongUsageException("/troops route find <companyId> <destinationTile>");
            }
            KOMEArmyCompany company = data.armyCompanies.get(args[2]);
            if (company == null) {
                throw new WrongUsageException("Unknown company " + args[2] + ".");
            }
            if (!owner.equals(company.owner) && !player.canCommandSenderUseCommand(2, getCommandName())) {
                throw new WrongUsageException("You do not own " + company.name + ".");
            }
            refreshCompany(data, company);
            RouteResult route = findLegalRoute(data, company.currentTile, parseTile(args[3]), company.faction, company);
            if (!route.valid) {
                throw new WrongUsageException(route.failureReason);
            }
            sender.addChatMessage(new ChatComponentText("Route for " + company.name + ": " + formatRouteTiles(route.routeTiles)
                + " (" + route.distance() + " tile step(s))."));
            if (!route.edgeNotes.isEmpty()) {
                sender.addChatMessage(new ChatComponentText("Special crossings: " + joinDebugList(route.edgeNotes) + "."));
            }
            return;
        }
        if ("validate".equals(action)) {
            if (args.length != 5) {
                throw new WrongUsageException("/troops route validate <originTile> <destinationTile> <faction>");
            }
            RouteResult route = findLegalRoute(data, parseTile(args[2]), parseTile(args[3]), args[4]);
            if (!route.valid) {
                throw new WrongUsageException(route.failureReason);
            }
            sender.addChatMessage(new ChatComponentText("Route valid for " + displayFaction(args[4]) + ": "
                + formatRouteTiles(route.routeTiles) + " (" + route.distance() + " tile step(s))."));
            if (!route.edgeNotes.isEmpty()) {
                sender.addChatMessage(new ChatComponentText("Special crossings: " + joinDebugList(route.edgeNotes) + "."));
            }
            return;
        }
        if ("trace".equals(action)) {
            if (args.length != 5) {
                throw new WrongUsageException("/troops route trace <originTile> <destinationTile> <faction>");
            }
            traceRoute(sender, data, parseTile(args[2]), parseTile(args[3]), args[4]);
            return;
        }
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("Only operators can edit route edges.");
        }
        if ("addedge".equals(action)) {
            if (args.length != 4) {
                throw new WrongUsageException("/troops route addedge <tileA> <tileB>");
            }
            setRouteEdge(sender, player, data, parseTile(args[2]), parseTile(args[3]), KOMEConquestRouteEdge.OPEN, "");
            return;
        }
        if ("removeedge".equals(action)) {
            if (args.length != 4) {
                throw new WrongUsageException("/troops route removeedge <tileA> <tileB>");
            }
            setRouteEdge(sender, player, data, parseTile(args[2]), parseTile(args[3]), KOMEConquestRouteEdge.BLOCKED, "Removed adjacency");
            return;
        }
        if ("unblock".equals(action)) {
            if (args.length != 4) {
                throw new WrongUsageException("/troops route unblock <tileA> <tileB>");
            }
            String tileA = parseTile(args[2]);
            String tileB = parseTile(args[3]);
            setRouteEdge(sender, player, data, tileA, tileB, KOMEConquestRouteEdge.OPEN, "Manual open crossing");
            return;
        }
        if ("block".equals(action)) {
            if (args.length != 5) {
                throw new WrongUsageException("/troops route block <tileA> <tileB> <river|mountain|blocked>");
            }
            String blockType = KOMEConquestRouteEdge.normalizeEdgeType(args[4]);
            if (KOMEConquestRouteEdge.OPEN.equals(blockType) || KOMEConquestRouteEdge.BRIDGE.equals(blockType)
                    || KOMEConquestRouteEdge.MOUNTAIN_PASS.equals(blockType)) {
                throw new WrongUsageException("Block type must be river, mountain, or blocked.");
            }
            setRouteEdge(sender, player, data, parseTile(args[2]), parseTile(args[3]), blockType, "");
            return;
        }
        if ("bridge".equals(action) || "passage".equals(action)) {
            if (args.length < 5) {
                throw new WrongUsageException("/troops route " + action + " <add|remove> <tileA> <tileB> [name]");
            }
            String sub = args[2].toLowerCase();
            String tileA = parseTile(args[3]);
            String tileB = parseTile(args[4]);
            if ("remove".equals(sub)) {
                boolean removed = data.removeRouteEdgeOverride(tileA, tileB);
                sender.addChatMessage(new ChatComponentText((removed ? "Removed" : "No") + " " + action
                    + " override for " + tileA + " <-> " + tileB + "."));
                if (removed) {
                    data.syncConquestTiles();
                }
                return;
            }
            if (!"add".equals(sub)) {
                throw new WrongUsageException("/troops route " + action + " <add|remove> <tileA> <tileB> [name]");
            }
            String name = args.length >= 6 ? joinName(args, 5) : "";
            setRouteEdge(sender, player, data, tileA, tileB,
                "bridge".equals(action) ? KOMEConquestRouteEdge.BRIDGE : KOMEConquestRouteEdge.MOUNTAIN_PASS, name);
            return;
        }
        throw new WrongUsageException("/troops route <adjacent|edge|addedge|removeedge|bridge|passage|block|unblock|find|validate|trace|graphstats> ...");
    }

    private void setRouteEdge(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, String tileA, String tileB, String type, String name) {
        if (tileA.equals(tileB)) {
            throw new WrongUsageException("Route edge endpoints must be different tiles.");
        }
        KOMEConquestRouteEdge edge = data.setRouteEdge(tileA, tileB, type, name, player.dimension, player.posX, player.posY, player.posZ, player.getCommandSenderName());
        sender.addChatMessage(new ChatComponentText("Set route edge " + describeRouteEdge(edge, true) + "."));
        data.syncConquestTiles();
    }

    private void listUnit(ICommandSender sender, KOMEWorldData data, World world, String id) {
        KOMEHiredUnitRecord found = null;
        String lookup = id == null ? "" : id.toLowerCase();
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record == null || record.entity == null) {
                continue;
            }
            String uuid = record.entity.toString().toLowerCase();
            if (uuid.equals(lookup) || uuid.startsWith(lookup)) {
                if (found != null) {
                    throw new WrongUsageException("More than one unit matches " + id + ". Use a longer unit ID.");
                }
                found = record;
            }
        }
        if (found == null) {
            throw new WrongUsageException("No tracked unit matches " + id + ".");
        }
        String ownerName = data.playerNames.get(found.owner);
        if (ownerName == null || ownerName.length() == 0) {
            ownerName = found.owner == null ? "Unknown player" : found.owner.toString().substring(0, 8);
        }
        String role = found.farmhand ? "Farmhand"
            : found.type == KOMEPopulationType.DEFENSIVE ? "Defensive (immobile)"
            : found.mounted ? "Offensive mounted" : "Offensive ground";
        String sourcePlayer = found.sourcePlayer == null ? ownerName : data.playerNames.get(found.sourcePlayer);
        if (sourcePlayer == null || sourcePlayer.length() == 0) {
            sourcePlayer = found.sourcePlayer == null ? ownerName : found.sourcePlayer.toString().substring(0, 8);
        }
        String source = found.isPlayerReserveFunded() ? "Player Reserve / " + sourcePlayer
            : "Tile " + KOMEConquestTile.normalizeId(found.sourceTileId) + " / " + displayFaction(found.sourceFaction);
        String releases = found.isPlayerReserveFunded() ? sourcePlayer + " player reserve"
            : "Tile " + KOMEConquestTile.normalizeId(found.sourceTileId) + " " + displayFaction(found.sourceFaction) + " pool";
        sender.addChatMessage(new ChatComponentText((found.unitName == null || found.unitName.length() == 0 ? found.entity.toString().substring(0, 8) : found.unitName)
            + " [" + found.entity + "]"));
        sender.addChatMessage(new ChatComponentText("Owner: " + ownerName + " / " + displayFaction(data.getPlayerFactionKey(found.owner))
            + ". Type: " + role + ". Population cost: " + (found.farmhand ? "farmhand capacity" : found.cost) + "."));
        sender.addChatMessage(new ChatComponentText("Current tile: " + KOMEConquestTile.normalizeId(found.currentTile)
            + ". Funding: " + source + ". Releases to: " + releases + "."));
        Entity physicalEntity = findLoadedEntity(world, found.entity);
        boolean physicallySpawned = physicalEntity != null;
        sender.addChatMessage(new ChatComponentText("Physical spawned: " + (physicallySpawned ? "yes" : "no")
            + (physicallySpawned ? " at dim " + physicalEntity.dimension + " "
                + formatBlockPos(physicalEntity.posX, physicalEntity.posY, physicalEntity.posZ) : "")
            + ". Entity UUID: " + found.entity + "."));
        if (found.companyId != null && found.companyId.length() > 0) {
            KOMEArmyCompany company = data.armyCompanies.get(found.companyId);
            sender.addChatMessage(new ChatComponentText("Company: "
                + (company == null ? found.companyId : company.name + " (" + company.id + ")")
                + (company != null && company.isMoving() ? " / Moving" : " / Stationed") + "."));
            KOMEArmyMovementOrder lastArrival = findLastArrivedOrderForCompany(data, found.companyId);
            if (lastArrival != null) {
                sender.addChatMessage(new ChatComponentText("Last company arrival: " + lastArrival.destinationTile
                    + formatOrderSpawnLocation(lastArrival) + "."));
            }
        } else {
            sender.addChatMessage(new ChatComponentText("Company: Unassigned."));
        }
        if (found.allocationTileId != null && found.allocationTileId.length() > 0) {
            sender.addChatMessage(new ChatComponentText("Allocation: " + found.allocationTileId + " / "
                + displayFaction(found.allocationFaction) + " / " + (found.allocationPlayer == null ? "Unknown player" : found.allocationPlayer) + "."));
        }
        KOMEArmyMovementOrder order = found.movementOrderId == null ? null : data.armyMovements.get(found.movementOrderId);
        if (order != null && order.isMoving()) {
            sender.addChatMessage(new ChatComponentText("Movement: " + order.status + " " + order.id + ", " + order.originTile + " -> "
                + order.destinationTile + ", ETA " + formatDuration(order.getRemainingMillis(System.currentTimeMillis())) + "."));
        } else if (order != null && KOMEArmyMovementOrder.ARRIVED.equals(order.status)) {
            sender.addChatMessage(new ChatComponentText("Movement: Arrived" + formatOrderSpawnLocation(order) + "."));
        } else {
            sender.addChatMessage(new ChatComponentText("Movement: Stationed."
                + (found.type == KOMEPopulationType.DEFENSIVE ? " Defensive units cannot move." : "")));
        }
    }

    private void listMoving(ICommandSender sender, KOMEWorldData data, World world, UUID owner) {
        int count = 0;
        long now = System.currentTimeMillis();
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order == null || !owner.equals(order.owner) || !order.isMoving()) {
                continue;
            }
            count++;
            long remaining = order.getRemainingMillis(now);
            String stepOrigin = activeStepOrigin(order);
            String stepDestination = activeStepDestination(order);
            sender.addChatMessage(new ChatComponentText(order.id + ": " + order.status + ", company "
                + (order.companyId.length() == 0 ? "none" : order.companyId) + ", " + order.ownerName + ", "
                + order.originTile + " -> " + order.destinationTile + "."));
            sender.addChatMessage(new ChatComponentText("  current " + stepOrigin + ", next "
                + (stepDestination.length() == 0 ? "none" : stepDestination) + ", final " + order.destinationTile
                + ", progress " + order.completedSteps + "/" + order.totalSteps()
                + ", next step arrival " + order.arrivalMillis + ", final estimate " + order.finalArrivalMillis
                + ", now " + now + ", "
                + formatMovementClock(order, now) + "."));
            sender.addChatMessage(new ChatComponentText("  route: " + formatRouteTiles(order.routeTiles)
                + ", completed " + order.completedSteps + "/" + order.distanceTiles
                + ", schedule " + (order.movementScheduleMode == null || order.movementScheduleMode.length() == 0
                ? movementScheduleMode(data) : order.movementScheduleMode) + "."));
            sender.addChatMessage(new ChatComponentText("  step departure " + order.stepDepartureMillis
                + ", step arrival " + order.stepArrivalMillis + ", next departure " + order.nextStepDepartureMillis + "."));
            sender.addChatMessage(new ChatComponentText("  stored Arrival Point: dim " + order.arrivalDimension + " at "
                + formatBlockPos(order.arrivalX, order.arrivalY, order.arrivalZ)
                + (order.arrivalPointSource.length() > 0 ? " (" + order.arrivalPointSource + ")" : "") + "."));
            sender.addChatMessage(new ChatComponentText("  destination chunk: " + movementChunkStatus(world, order) + "."));
            sender.addChatMessage(new ChatComponentText("  temporary chunk load: last attempt " + order.lastChunkLoadAttemptMillis
                + ", chunk " + order.lastChunkLoadChunkX + ", " + order.lastChunkLoadChunkZ
                + ", ticket " + (order.lastChunkLoadTicketAcquired ? "acquired" : "not acquired") + "."));
            sender.addChatMessage(new ChatComponentText("  " + order.units.size() + " units / " + order.population
                + " pop, mounted " + order.mountedPopulation + ", ground " + order.groundPopulation + "."));
            if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)) {
                sender.addChatMessage(new ChatComponentText("  stop available: /troops movement stop " + order.id + "."));
            }
            sender.addChatMessage(new ChatComponentText("  spawn attempts " + order.spawnAttemptCount + ", last "
                + order.lastSpawnAttemptMillis + ", next retry " + order.nextSpawnRetryMillis
                + (order.spawnRetryPaused ? ", automatic retries paused" : "") + "."));
            if (order.lastSpawnFailureCode != null && order.lastSpawnFailureCode.length() > 0) {
                sender.addChatMessage(new ChatComponentText("  last spawn failure: " + order.lastSpawnFailureCode
                    + (order.lastSpawnFailureDetails.length() > 0 ? " - " + order.lastSpawnFailureDetails : "")));
            }
            if (order.pendingSpawnReason != null && order.pendingSpawnReason.length() > 0) {
                sender.addChatMessage(new ChatComponentText("  pending spawn: " + order.pendingSpawnReason));
            }
            int stalePhysical = 0;
            for (UUID unitId : order.units) {
                KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                if (record != null && findLoadedEntity(world, record.entity) != null) {
                    stalePhysical++;
                }
            }
            sender.addChatMessage(new ChatComponentText("  physical spawned while moving: " + stalePhysical + "/" + order.units.size()
                + (order.companyId.length() > 0 ? ", company " + order.companyId : "") + "."));
        }
        if (count == 0) {
            sender.addChatMessage(new ChatComponentText("You have no active troop movements."));
        }
    }

    private void handleMovementHistory(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, String[] args) {
        boolean admin = player.canCommandSenderUseCommand(2, getCommandName());
        String viewerFaction = normalizeFaction(getPlayerFaction(data, player));
        if (args.length >= 2 && "reset".equalsIgnoreCase(args[1])) {
            if (!admin) {
                throw new WrongUsageException("Only admins can reset troop movement history.");
            }
            String faction = args.length >= 3 ? normalizeFaction(args[2]) : "";
            int removed = data.clearCompletedMovementHistory(faction);
            sender.addChatMessage(new ChatComponentText("Cleared " + removed + " completed troop movement history record(s)"
                + (faction.length() == 0 ? " for all factions" : " for " + displayFaction(faction)) + ". Active movements were not deleted."));
            return;
        }
        boolean all = args.length >= 2 && "all".equalsIgnoreCase(args[1]);
        if (all && !admin) {
            throw new WrongUsageException("Only admins can view all faction troop movement records.");
        }
        String requestedFaction = "";
        if (!all) {
            requestedFaction = args.length >= 2 ? normalizeFaction(args[1]) : viewerFaction;
            if (requestedFaction.length() == 0) {
                requestedFaction = viewerFaction;
            }
            if (!admin && !requestedFaction.equals(viewerFaction)) {
                throw new WrongUsageException("You can only view your own faction's troop movement records.");
            }
        }
        sendMovementHistoryGui(player, data, requestedFaction, all);
    }

    private void sendMovementHistoryGui(EntityPlayerMP player, KOMEWorldData data, String requestedFaction, boolean all) {
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order != null && order.id != null && order.id.length() > 0) {
                data.updateMovementHistory(order, order.isPendingSpawn() ? KOMEMovementHistoryRecord.FAILED
                    : order.isMoving() ? KOMEMovementHistoryRecord.ACTIVE
                    : KOMEMovementHistoryRecord.ARRIVED.equals(order.status) ? KOMEMovementHistoryRecord.ARRIVED
                    : KOMEArmyMovementOrder.STOPPED.equals(order.status) ? KOMEMovementHistoryRecord.STOPPED : null);
            }
        }
        final List<KOMEMovementHistoryRecord> records = new ArrayList<KOMEMovementHistoryRecord>();
        String normalizedFaction = normalizeFaction(requestedFaction);
        for (KOMEMovementHistoryRecord record : data.movementHistory.values()) {
            if (record != null && (all || normalizedFaction.equals(normalizeFaction(record.faction)))) {
                records.add(record);
            }
        }
        Collections.sort(records, new Comparator<KOMEMovementHistoryRecord>() {
            @Override
            public int compare(KOMEMovementHistoryRecord left, KOMEMovementHistoryRecord right) {
                long leftTime = left.getLatestActivityMillis();
                long rightTime = right.getLatestActivityMillis();
                if (leftTime != rightTime) {
                    return leftTime > rightTime ? -1 : 1;
                }
                String rightId = right.movementOrderId == null ? "" : right.movementOrderId;
                String leftId = left.movementOrderId == null ? "" : left.movementOrderId;
                return rightId.compareTo(leftId);
            }
        });
        String title = all ? "All Faction Movement Records" : displayFaction(normalizedFaction) + " Movement Records";
        KOMEPacketHandler.network.sendTo(new KOMEPacketMovementHistoryData(title, normalizedFaction, all, records), player);
    }

    private void listArrivals(ICommandSender sender, KOMEWorldData data, World world, UUID owner, String tile) {
        List<KOMEArmyMovementOrder> arrivals = new ArrayList<KOMEArmyMovementOrder>();
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order == null || !owner.equals(order.owner)) {
                continue;
            }
            if (tile.length() > 0 && !tile.equals(order.originTile) && !tile.equals(order.destinationTile)) {
                continue;
            }
            if (KOMEArmyMovementOrder.ARRIVED.equals(order.status) || order.isPendingSpawn()) {
                arrivals.add(order);
            }
        }
        Collections.sort(arrivals, new Comparator<KOMEArmyMovementOrder>() {
            @Override
            public int compare(KOMEArmyMovementOrder left, KOMEArmyMovementOrder right) {
                if (right.arrivalMillis == left.arrivalMillis) {
                    return 0;
                }
                return right.arrivalMillis > left.arrivalMillis ? 1 : -1;
            }
        });
        if (arrivals.isEmpty()) {
            sender.addChatMessage(new ChatComponentText("No completed or pending company arrivals"
                + (tile.length() > 0 ? " involving " + tile : "") + "."));
            return;
        }
        sender.addChatMessage(new ChatComponentText("Company arrivals" + (tile.length() > 0 ? " involving " + tile : "") + ":"));
        int shown = 0;
        for (KOMEArmyMovementOrder order : arrivals) {
            if (shown >= 8) {
                sender.addChatMessage(new ChatComponentText("+" + (arrivals.size() - shown) + " older arrival(s) not shown."));
                break;
            }
            shown++;
            KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
            String companyName = company == null ? order.companyName : company.name;
            sender.addChatMessage(new ChatComponentText(order.id + " / " + (companyName == null || companyName.length() == 0 ? order.companyId : companyName)
                + " (" + order.companyId + "): " + order.originTile + " -> " + order.destinationTile
                + ", status " + order.status + ", " + order.units.size() + " units / " + order.population + " pop."));
            if (KOMEArmyMovementOrder.ARRIVED.equals(order.status)) {
                sender.addChatMessage(new ChatComponentText("  Spawned" + formatOrderSpawnLocation(order) + "."));
                sender.addChatMessage(new ChatComponentText("  Last attempted physical spawn: " + formatOrderAttemptLocation(order) + "."));
                sender.addChatMessage(new ChatComponentText("  Physical check: " + physicalCompanySummary(data, world, order)));
            } else if (order.isPendingSpawn()) {
                sender.addChatMessage(new ChatComponentText("  Last attempted physical spawn: " + formatOrderAttemptLocation(order) + "."));
                sender.addChatMessage(new ChatComponentText("  Pending spawn: " + order.pendingSpawnReason));
            }
        }
    }

    private void locateTroops(ICommandSender sender, KOMEWorldData data, World world, UUID owner, String type, String id) {
        if ("order".equalsIgnoreCase(type)) {
            KOMEArmyMovementOrder order = data.armyMovements.get(id);
            if (order == null || !owner.equals(order.owner) && !sender.canCommandSenderUseCommand(2, getCommandName())) {
                throw new WrongUsageException("No movement order " + id + " was found for you.");
            }
            locateOrder(sender, data, world, order);
            return;
        }
        if ("company".equalsIgnoreCase(type)) {
            KOMEArmyCompany company = data.armyCompanies.get(id);
            if (company == null || !owner.equals(company.owner) && !sender.canCommandSenderUseCommand(2, getCommandName())) {
                throw new WrongUsageException("No company " + id + " was found for you.");
            }
            locateCompany(sender, data, world, company);
            return;
        }
        if ("unit".equalsIgnoreCase(type)) {
            KOMEHiredUnitRecord record = findUnitByPrefix(data, id, owner, sender.canCommandSenderUseCommand(2, getCommandName()));
            if (record == null) {
                throw new WrongUsageException("No unit matching " + id + " was found for you.");
            }
            locateUnit(sender, data, world, record);
            return;
        }
        throw new WrongUsageException("/troops locate <order|company|unit> <id>");
    }

    private void locateOrder(ICommandSender sender, KOMEWorldData data, World world, KOMEArmyMovementOrder order) {
        sender.addChatMessage(new ChatComponentText("Order " + order.id + ": " + order.status + ", company "
            + order.companyId + ", " + order.originTile + " -> " + order.destinationTile + ", "
            + order.units.size() + " units / " + order.population + " pop."));
        sender.addChatMessage(new ChatComponentText("Route progress: step " + activeStepOrigin(order) + " -> "
            + activeStepDestination(order) + ", completed " + order.completedSteps + "/" + order.distanceTiles
            + ", intended spawn" + formatOrderSpawnLocation(order) + "."));
        sender.addChatMessage(new ChatComponentText("Stored Arrival Point: dim " + order.arrivalDimension + " at "
            + formatBlockPos(order.arrivalX, order.arrivalY, order.arrivalZ)
            + (order.arrivalPointSource.length() > 0 ? " (" + order.arrivalPointSource + ")" : "") + "."));
        sender.addChatMessage(new ChatComponentText("Destination chunk: " + movementChunkStatus(world, order) + "."));
        sender.addChatMessage(new ChatComponentText("Temporary chunk load: last attempt " + order.lastChunkLoadAttemptMillis
            + ", dim " + order.lastChunkLoadDimension + ", chunk " + order.lastChunkLoadChunkX + ", "
            + order.lastChunkLoadChunkZ + ", ticket " + (order.lastChunkLoadTicketAcquired ? "acquired" : "not acquired") + "."));
        sender.addChatMessage(new ChatComponentText("Last attempted physical spawn: " + formatOrderAttemptLocation(order) + "."));
        if (order.isPendingSpawn()) {
            sender.addChatMessage(new ChatComponentText("Pending spawn reason: " + order.pendingSpawnReason));
        }
        sender.addChatMessage(new ChatComponentText("Spawn retry debug: attempts " + order.spawnAttemptCount
            + ", last " + order.lastSpawnAttemptMillis + ", next " + order.nextSpawnRetryMillis
            + (order.spawnRetryPaused ? ", paused" : "") + "."));
        if (order.lastSpawnFailureCode != null && order.lastSpawnFailureCode.length() > 0) {
            sender.addChatMessage(new ChatComponentText("Last failure: " + order.lastSpawnFailureCode
                + " - " + order.lastSpawnFailureDetails));
        }
        int verified = 0;
        int failed = 0;
        for (UUID unitId : order.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record == null) {
                failed++;
                sender.addChatMessage(new ChatComponentText("  " + unitId + ": missing hired-unit record."));
                continue;
            }
            Entity entity = findLoadedEntity(world, record.entity);
            String status = describePhysicalEntity(world, entity, record.entity, order.lastSpawnDimension, order.lastSpawnX, order.lastSpawnY, order.lastSpawnZ);
            if (entity != null && !entity.isDead) {
                verified++;
            } else {
                failed++;
            }
            sender.addChatMessage(new ChatComponentText("  " + displayUnitId(record) + " [" + record.entity + "]: " + status));
        }
        sender.addChatMessage(new ChatComponentText("Verified physical entities: " + verified + "/" + order.units.size()
            + (failed > 0 ? ", failed " + failed : "") + "."));
    }

    private void locateCompany(ICommandSender sender, KOMEWorldData data, World world, KOMEArmyCompany company) {
        KOMEArmyMovementOrder order = company.movementOrderId == null ? null : data.armyMovements.get(company.movementOrderId);
        if (order == null) {
            order = findLastArrivedOrderForCompany(data, company.id);
        }
        sender.addChatMessage(new ChatComponentText("Company " + company.id + " / " + company.name + ": "
            + company.status + ", tile " + company.currentTile + ", " + company.units.size() + " units."));
        if (order != null) {
            sender.addChatMessage(new ChatComponentText("Related order: " + order.id + " / " + order.status + ", "
                + order.originTile + " -> " + order.destinationTile + formatOrderSpawnLocation(order) + "."));
            sender.addChatMessage(new ChatComponentText("Last attempted physical spawn: " + formatOrderAttemptLocation(order) + "."));
            if (order.isPendingSpawn()) {
                sender.addChatMessage(new ChatComponentText("Pending spawn reason: " + order.pendingSpawnReason));
            }
        }
        int loaded = 0;
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record == null) {
                sender.addChatMessage(new ChatComponentText("  " + unitId + ": missing hired-unit record."));
                continue;
            }
            Entity entity = findLoadedEntity(world, record.entity);
            if (entity != null && !entity.isDead) {
                loaded++;
            }
            int expectedDim = order == null ? (world == null || world.provider == null ? 0 : world.provider.dimensionId) : order.lastSpawnDimension;
            double expectedX = order == null ? 0.0D : order.lastSpawnX;
            double expectedY = order == null ? 0.0D : order.lastSpawnY;
            double expectedZ = order == null ? 0.0D : order.lastSpawnZ;
            sender.addChatMessage(new ChatComponentText("  " + displayUnitId(record) + " [" + record.entity + "]: "
                + describePhysicalEntity(world, entity, record.entity, expectedDim, expectedX, expectedY, expectedZ)));
        }
        sender.addChatMessage(new ChatComponentText("Loaded physical entities: " + loaded + "/" + company.units.size() + "."));
    }

    private void locateUnit(ICommandSender sender, KOMEWorldData data, World world, KOMEHiredUnitRecord record) {
        Entity entity = findLoadedEntity(world, record.entity);
        KOMEArmyMovementOrder order = record.movementOrderId == null ? null : data.armyMovements.get(record.movementOrderId);
        if (order == null && record.companyId != null && record.companyId.length() > 0) {
            order = findLastArrivedOrderForCompany(data, record.companyId);
        }
        sender.addChatMessage(new ChatComponentText(displayUnitId(record) + " [" + record.entity + "]: tile "
            + record.currentTile + ", company " + (record.companyId == null || record.companyId.length() == 0 ? "none" : record.companyId)
            + ", movement " + (record.movementOrderId == null || record.movementOrderId.length() == 0 ? "none" : record.movementOrderId) + "."));
        int expectedDim = order == null ? (world == null || world.provider == null ? 0 : world.provider.dimensionId) : order.lastSpawnDimension;
        double expectedX = order == null ? 0.0D : order.lastSpawnX;
        double expectedY = order == null ? 0.0D : order.lastSpawnY;
        double expectedZ = order == null ? 0.0D : order.lastSpawnZ;
        sender.addChatMessage(new ChatComponentText("Physical: "
            + describePhysicalEntity(world, entity, record.entity, expectedDim, expectedX, expectedY, expectedZ)));
        if (order != null) {
            sender.addChatMessage(new ChatComponentText("Last attempted physical spawn: " + formatOrderAttemptLocation(order) + "."));
        }
        sender.addChatMessage(new ChatComponentText("Funding: " + (record.isPlayerReserveFunded() ? "Player Reserve" : "Tile " + record.sourceTileId)
            + ", source faction " + displayFaction(record.sourceFaction) + "."));
    }

    private void listCompanies(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, UUID owner, String tile) {
        data.rebuildArmyCompaniesForPlayer(KOMEReflection.getWorld(player), owner);
        boolean admin = sender.canCommandSenderUseCommand(2, getCommandName());
        String playerFaction = getPlayerFaction(data, player);
        List<KOMECompanyGuiEntry> entries = new ArrayList<KOMECompanyGuiEntry>();
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (company == null || !admin && !owner.equals(company.owner) && !company.isTemporarilyControlledBy(owner)
                    && !(KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)
                        && data.isFactionKing(company.faction, owner))) {
                continue;
            }
            if (tile.length() > 0 && !companyHasPresenceAtTile(data, company, tile)) {
                continue;
            }
            refreshCompany(data, company);
            KOMECompanyGuiEntry entry = new KOMECompanyGuiEntry();
            entry.id = company.id;
            entry.name = company.name;
            entry.tile = companyDisplayTile(data, company);
            entry.tileDisplayName = companyTileDisplayName(data, entry.tile);
            entry.unitCount = company.units.size();
            entry.population = company.totalPopulation;
            entry.mountedPopulation = company.mountedPopulation;
            entry.groundPopulation = company.groundPopulation;
            entry.status = KOMEArmyCompany.WAR_ENDED_HALTED.equals(company.status)
                ? KOMEArmyCompany.WAR_ENDED_HALTED : company.isMoving() ? "Moving" : "Stationed";
            entry.movementOrderId = company.movementOrderId;
            KOMEArmyMovementOrder order = data.armyMovements.get(company.movementOrderId);
            if (order != null && order.isMoving()) {
                entry.destinationTile = order.destinationTile;
                entry.etaMillis = order.getRemainingMillis(System.currentTimeMillis());
            }
            boolean canControl = canPlayerControlCompany(data, player, company);
            String standReason = companyStandBlockReason(data, company, entry.tile);
            entry.canMove = canControl && !company.isMoving() && !company.units.isEmpty() && standReason.length() == 0;
            entry.cannotMoveReason = !canControl ? "You do not control this company"
                : company.isMoving() ? "Company is already moving"
                : company.units.isEmpty() ? "Company has no eligible units"
                : standReason.length() > 0 ? standReason : "";
            entry.faction = company.faction;
            entry.ownerName = company.ownerName;
            entry.controllerName = company.temporaryController == null ? company.ownerName : company.temporaryControllerName;
            entry.controllerAuthority = company.controllerAuthority;
            entry.tendency = company.tendency;
            entry.delegationAlliancePair = company.delegationAlliancePair;
            entry.movementStatus = order == null ? "" : order.status;
            entry.nativeFaction = KOMEWartimeStewardshipService.nativeFaction(company);
            entry.authorizedWarIds = joinValues(company.authorizedWarIds);
            entry.legalTargets = joinValues(new ArrayList<String>(KOMEWartimeStewardshipService.authorizedOpponents(data, company)));
            entry.populationSource = company.populationSource;
            entry.withdrawalState = company.withdrawalState;
            entry.authorizationReason = company.authorizationReason;
            entry.canSetTendency = admin || owner.equals(company.owner);
            entry.canReclaim = admin || owner.equals(company.owner) || owner.equals(company.delegatedBy)
                || KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority) && data.isFactionKing(company.faction, owner);
            entry.canChooseAccessResponse = canControl && order != null
                && (KOMEArmyMovementOrder.ACCESS_HALTED.equals(order.status) || KOMEArmyMovementOrder.HOLDING.equals(order.status)
                    || KOMEArmyMovementOrder.STOPPED.equals(order.status)
                    || KOMEArmyMovementOrder.WAR_ENDED_HALTED.equals(order.status));
            KOMEMovementRecoveryOptions recovery = KOMEMovementRecoveryOptions.forOrder(data, order);
            entry.canStay = canControl && recovery.canStay;
            entry.canRetreat = canControl && recovery.canRetreat;
            entry.canResume = canControl && recovery.canResume;
            entry.accessLossReason = recovery.accessLossReason;
            entry.resumeBlockedReason = recovery.resumeBlockedReason;
            entry.retreatTargetTile = recovery.retreatTargetTile;
            entry.currentTile = recovery.currentTile;
            entry.nextTile = recovery.nextTile;
            entry.intendedDestinationTile = recovery.destinationTile;
            entry.retreatBlockedReason = recovery.retreatBlockedReason;
            boolean stewardshipCompany = false;
            for (UUID unitId : company.units) {
                KOMEHiredUnitRecord unitRecord = data.hiredUnits.get(unitId);
                stewardshipCompany = stewardshipCompany || unitRecord != null
                    && "MILITARY_T3_STEWARDSHIP".equals(unitRecord.benefitSource);
            }
            entry.canDisband = admin || stewardshipCompany && (owner.equals(company.owner) || data.isFactionKing(company.faction, owner));
            entry.stewardshipUnallocated = data.getKinglessStewardshipUnallocated(company.faction);
            entry.stewardshipGlobalCap = data.getKinglessStewardshipGlobalCap(company.faction);
            entry.stewardshipReserved = data.getKinglessStewardshipReserved(company.faction);
            entry.stewardshipAvailable = data.getKinglessStewardshipAvailable(company.faction);
            entries.add(entry);
        }
        Collections.sort(entries, new Comparator<KOMECompanyGuiEntry>() {
            @Override
            public int compare(KOMECompanyGuiEntry left, KOMECompanyGuiEntry right) {
                return left.name.compareToIgnoreCase(right.name);
            }
        });
        if (sender instanceof EntityPlayerMP) {
            boolean canCreate = admin && tile.length() > 0 && hasUnassignedOffensiveUnits(data, owner, tile)
                && data.canFactionStandOnTile(tile, playerFaction);
            KOMEPacketHandler.network.sendTo(new KOMEPacketCompanyListGui(tile, companyTileDisplayName(data, tile), entries, canCreate), player);
            return;
        }
        sender.addChatMessage(new ChatComponentText("Companies" + (tile.length() == 0 ? "" : " at " + tile) + ": " + entries.size()));
        for (KOMECompanyGuiEntry entry : entries) {
            sender.addChatMessage(new ChatComponentText(entry.id + " " + entry.name + ": " + entry.unitCount + " units / "
                + entry.population + " pop, mounted " + entry.mountedPopulation + ", ground " + entry.groundPopulation
                + ", " + entry.status + "."));
        }
    }

    private void handleCompanyAssignmentCommand(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, UUID owner, String[] args) {
        boolean admin = sender.canCommandSenderUseCommand(2, getCommandName());
        if ("rebuild".equalsIgnoreCase(args[1])) {
            if (args.length > 3) {
                throw new WrongUsageException("/troops company rebuild [all]");
            }
            if (args.length == 3 && "all".equalsIgnoreCase(args[2])) {
                if (!admin) {
                    throw new WrongUsageException("Only operators can rebuild all troop companies.");
                }
                data.rebuildArmyCompanies(KOMEReflection.getWorld(player));
                sender.addChatMessage(new ChatComponentText("Rebuilt all auto-tracked companies from LOTR Unit Overview company assignments."));
            } else {
                data.rebuildArmyCompaniesForPlayer(KOMEReflection.getWorld(player), owner);
                sender.addChatMessage(new ChatComponentText("Rebuilt your auto-tracked companies from LOTR Unit Overview company assignments."));
            }
            return;
        }
        throw new WrongUsageException("/troops company <id|rebuild> ...");
    }

    private void handleCompanyAuthorityCommand(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, String[] args) {
        KOMEArmyCompany company = data.armyCompanies.get(args[1]);
        if (company == null) {
            throw new WrongUsageException("Unknown company " + args[1] + ".");
        }
        UUID actor = KOMEReflection.getEntityUUID(player);
        boolean admin = sender.canCommandSenderUseCommand(2, getCommandName());
        String action = args[2].toLowerCase(java.util.Locale.ROOT);
        if (!admin && company.isTemporarilyControlledBy(actor) && !actor.equals(company.owner)
                && !KOMEAllianceTemporaryCommandPolicy.allows(action)) {
            throw new WrongUsageException("Temporary delegated command permits only View, Dispatch, Continue, Halt, Stay, Retreat, and Resume; structural and owner-only administration is forbidden.");
        }
        if ("tendency".equals(action)) {
            if (args.length != 4 || !admin && !actor.equals(company.owner)) {
                throw new WrongUsageException("/troops company <id> tendency <aggressive|conservative> (owner only)");
            }
            if ("aggressive".equalsIgnoreCase(args[3])) company.tendency = KOMEArmyCompany.AGGRESSIVE;
            else if ("conservative".equalsIgnoreCase(args[3])) company.tendency = KOMEArmyCompany.CONSERVATIVE;
            else throw new WrongUsageException("Tendency must be aggressive or conservative.");
            data.markDirty();
            sender.addChatMessage(new ChatComponentText(company.name + " tendency set to " + company.tendency + "."));
            return;
        }
        if ("transfer".equals(action)) {
            if (args.length != 4 || !actor.equals(company.owner)) {
                throw new WrongUsageException("/troops company <id> transfer <onlinePlayer> (actual owner only)");
            }
            EntityPlayerMP target = MinecraftServer.getServer().getConfigurationManager().func_152612_a(args[3]);
            if (target == null) throw new WrongUsageException("The transfer recipient must be online.");
            String nativeFaction = normalizeFaction(company.faction);
            String recipientFaction = normalizeFaction(getPlayerFaction(data, target));
            if (!nativeFaction.equals(recipientFaction)) throw new WrongUsageException("The recipient must be pledged to the company's native faction.");
            KOMECompanyTransferService.Result result = KOMECompanyTransferService.offer(data, company, actor,
                KOMEReflection.getEntityUUID(target), target.getCommandSenderName(), System.currentTimeMillis());
            if (!result.success) throw new WrongUsageException(result.message);
            sender.addChatMessage(new ChatComponentText(result.message + " The recipient must use /troops company " + company.id + " acceptTransfer."));
            target.addChatMessage(new ChatComponentText(player.getCommandSenderName() + " offered permanent ownership of "
                + company.name + ". Accept with /troops company " + company.id + " acceptTransfer."));
            return;
        }
        if ("accepttransfer".equals(action)) {
            if (args.length != 3 || !actor.equals(company.transferRecipient)) throw new WrongUsageException("No transfer offer is pending for you.");
            String recipientFaction = normalizeFaction(getPlayerFaction(data, player));
            if (!normalizeFaction(company.faction).equals(recipientFaction)) throw new WrongUsageException("You are no longer pledged to the company's native faction.");
            KOMECompanyTransferService.Result result = KOMECompanyTransferService.accept(data, company, actor,
                player.getCommandSenderName(), System.currentTimeMillis());
            if (!result.success) throw new WrongUsageException(result.message);
            applyLoadedCompanyOwnership(data, company, player);
            sender.addChatMessage(new ChatComponentText(result.message));
            return;
        }
        if ("rejecttransfer".equals(action)) {
            if (args.length != 3 || !actor.equals(company.transferRecipient)) throw new WrongUsageException("No transfer offer is pending for you.");
            company.clearTransferOffer();
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Rejected the permanent transfer of " + company.name + "."));
            return;
        }
        if ("canceltransfer".equals(action)) {
            if (args.length != 3 || !actor.equals(company.owner)) throw new WrongUsageException("Only the actual owner may cancel the transfer offer.");
            company.clearTransferOffer();
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Cancelled the permanent transfer offer for " + company.name + "."));
            return;
        }
        if ("delegate".equals(action)) {
            if (args.length != 4) {
                throw new WrongUsageException("/troops company <id> delegate <onlinePlayer>");
            }
            String nativeFaction = KOMEWartimeStewardshipService.nativeFaction(company);
            EntityPlayerMP target = MinecraftServer.getServer().getConfigurationManager().func_152612_a(args[3]);
            if (target == null) {
                throw new WrongUsageException("The temporary commander must be online.");
            }
            UUID targetId = KOMEReflection.getEntityUUID(target);
            KOMECompanyDiplomacyAuthorization.Decision delegation =
                KOMECompanyDiplomacyAuthorization.canStartDelegation(
                    data, nativeFaction, actor, targetId);
            if (!delegation.allowed) {
                throw new WrongUsageException(delegation.reason);
            }
            long delegationNow = System.currentTimeMillis();
            if (KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)
                    && company.temporaryController != null) {
                data.recordCompanyDelegationAudit(
                    delegationNow, "REVOKED_REDELEGATED", company,
                    actor, player.getCommandSenderName(),
                    company.temporaryController, company.temporaryControllerName,
                    "Replaced by a new delegation");
            }
            company.temporaryController = targetId;            company.temporaryControllerName = target.getCommandSenderName();
            company.delegatedBy = actor;
            company.delegatedByName = player.getCommandSenderName();
            company.controllerAuthority = KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE;
            company.delegationAlliancePair = "";
            company.delegatedAtMillis = delegationNow;
            company.delegationRevocationReason = "";
            data.recordCompanyDelegationAudit(
                delegationNow, "DELEGATED", company,
                actor, player.getCommandSenderName(),
                targetId, target.getCommandSenderName(), "");
            data.markDirty();            sender.addChatMessage(new ChatComponentText("Delegated movement command of " + company.name + " to " + company.temporaryControllerName
                + ". Ownership and population sources remain unchanged."));
            return;
        }
        if ("reclaim".equals(action) || "revoke".equals(action)) {
            boolean nativeStewardshipReclaim = KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)
                && data.isFactionKing(company.faction, actor);
            if (!admin && !actor.equals(company.owner) && !actor.equals(company.delegatedBy) && !nativeStewardshipReclaim) {
                throw new WrongUsageException("Only the native owner or delegating king may reclaim this company.");
            }
            if (nativeStewardshipReclaim) {
                company.temporaryController = actor;
                company.temporaryControllerName = player.getCommandSenderName();
                company.controllerAuthority = KOMEArmyCompany.AUTHORITY_NATIVE_RECLAIM;
                company.delegationRevocationReason = "Reclaimed by new native king";
                for (UUID unitId : company.units) {
                    KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                    if (record != null && "MILITARY_T3_STEWARDSHIP".equals(record.benefitSource)) {
                        record.controller = actor;
                        record.controllerAuthority = KOMEArmyCompany.AUTHORITY_NATIVE_RECLAIM;
                    }
                }
            } else {
                if (KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)
                        && company.temporaryController != null) {
                    data.recordCompanyDelegationAudit(
                        System.currentTimeMillis(), "REVOKED_MANUAL", company,
                        actor, player.getCommandSenderName(),
                        company.temporaryController, company.temporaryControllerName,
                        "Reclaimed by native authority");
                }
                company.clearTemporaryController("Reclaimed by native authority");
            }            KOMEArmyMovementOrder order = data.armyMovements.get(company.movementOrderId);
            if (order != null && KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)) {
                haltForAccessLoss(data, order, System.currentTimeMillis(), "Temporary command reclaimed by native authority.");
            } else if (order != null && order.isMoving()) {
                order.haltAfterArrival = true;
                order.accessLossReason = "Temporary command reclaimed by native authority.";
            }
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Native control of " + company.name + " reclaimed."));
            return;
        }
        throw new WrongUsageException("/troops company <id> tendency|delegate|reclaim|disband|transfer|acceptTransfer|rejectTransfer|cancelTransfer ...");
    }

    private void applyLoadedCompanyOwnership(KOMEWorldData data, KOMEArmyCompany company, EntityPlayerMP recipient) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null || server.worldServers == null) return;
        for (UUID unitId : company.units) {
            for (WorldServer world : server.worldServers) {
                Entity entity = findLoadedEntity(world, unitId);
                if (entity instanceof LOTREntityNPC) {
                    LOTREntityNPC npc = (LOTREntityNPC) entity;
                    npc.hiredNPCInfo.setHiringPlayer(recipient);
                    KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                    if (record != null) record.stationedEntityData = KOMEEntitySnapshots.snapshot(npc);
                    break;
                }
            }
        }
        data.markDirty();
    }

    private void disbandStewardshipCompany(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data,
            KOMEArmyCompany company, UUID actor, boolean admin) {
        List<UUID> unitIds = new ArrayList<UUID>(company.units);
        boolean stewardship = false;
        for (UUID unitId : unitIds) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            stewardship = stewardship || record != null && "MILITARY_T3_STEWARDSHIP".equals(record.benefitSource);
        }
        boolean nativeKing = stewardship && data.isFactionKing(company.faction, actor);
        if (!admin && !actor.equals(company.owner) && !nativeKing) {
            throw new WrongUsageException("Only the native owner, native faction king, or an operator may disband a stewardship company.");
        }
        if (!stewardship && !admin) {
            throw new WrongUsageException("This safe return path is restricted to stewardship-created companies.");
        }
        java.util.Map<UUID, Entity> loaded = new java.util.HashMap<UUID, Entity>();
        MinecraftServer server = MinecraftServer.getServer();
        if (server != null && server.worldServers != null) {
            for (WorldServer world : server.worldServers) {
                if (world == null) continue;
                for (Object object : world.loadedEntityList) {
                    if (object instanceof Entity) {
                        Entity entity = (Entity) object;
                        loaded.put(KOMEReflection.getEntityUUID(entity), entity);
                    }
                }
            }
        }
        for (UUID unitId : unitIds) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null && !record.isMoving() && !loaded.containsKey(unitId)) {
                throw new WrongUsageException("Load all stationed stewardship units before disbanding so no untracked entity can remain.");
            }
        }
        KOMEArmyMovementOrder order = data.armyMovements.get(company.movementOrderId);
        if (order != null && order.isMoving()) {
            order.status = KOMEArmyMovementOrder.CANCELLED;
            order.stopped = true;
            order.accessChoice = "NATIVE_DISBAND";
            order.accessLossReason = "Disbanded by native authority; population returned to recorded sources.";
        }
        int returned = 0;
        for (UUID unitId : unitIds) {
            KOMEHiredUnitRecord record = data.hiredUnits.remove(unitId);
            if (record == null) continue;
            if (data.releasePopulationForOrdinaryUnitRemoval(record)) {
                returned += Math.max(0, record.cost);
            }
            Entity entity = loaded.get(unitId);
            if (entity != null) KOMEReflection.setDead(entity);
        }
        data.armyCompanies.remove(company.id);
        data.markDirty();
        data.syncConquestTiles();
        sender.addChatMessage(new ChatComponentText("Disbanded stewardship company " + company.name + " and returned " + returned
            + " population to its recorded native sources."));
    }

    private void listCompany(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, World world, String companyId) {
        KOMEArmyCompany company = data.armyCompanies.get(companyId);
        if (company == null) {
            throw new WrongUsageException("Unknown company " + companyId + ".");
        }
        UUID actor = KOMEReflection.getEntityUUID(player);
        boolean nativeSteward = KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)
            && data.isFactionKing(company.faction, actor);
        if (!sender.canCommandSenderUseCommand(2, getCommandName()) && !canPlayerControlCompany(data, player, company) && !nativeSteward) {
            throw new WrongUsageException("You do not have authority to view that company record.");
        }
        refreshCompany(data, company);
        sender.addChatMessage(new ChatComponentText(company.id + " - " + company.name + " / " + company.ownerName
            + " / " + displayFaction(company.faction)));
        sender.addChatMessage(new ChatComponentText("Source: "
            + (KOMEArmyCompany.SOURCE_LOTR_COMPANY_ASSIGNMENT.equals(company.source) ? "LOTR Unit Overview company assignment" : company.source)
            + ". LOTR company value: " + (company.lotrCompanyValue == null || company.lotrCompanyValue.length() == 0 ? "none" : company.lotrCompanyValue) + "."));
        KOMEConquestTile currentTile = data.getConquestTile(company.currentTile);
        String tileOwner = currentTile == null ? "" : normalizeFaction(currentTile.currentRulingFaction());
        String relation = tileOwner.length() == 0 ? "unclaimed/unknown"
            : tileOwner.equals(normalizeFaction(company.faction)) ? "own"
            : data.canFactionUseMilitaryPassage(company.faction, tileOwner) ? "allied Military passage" : "not passable";
        boolean canControl = sender instanceof EntityPlayerMP && canPlayerControlCompany(data, (EntityPlayerMP) sender, company);
        sender.addChatMessage(new ChatComponentText("Current tile owner: "
            + (tileOwner.length() == 0 ? "none" : displayFaction(tileOwner)) + " (" + relation + "). Control for requester: "
            + (canControl ? "yes" : "no") + "."));
        sender.addChatMessage(new ChatComponentText("Tile " + company.currentTile + ", " + company.units.size() + " units / "
            + company.totalPopulation + " pop, mounted " + company.mountedPopulation + ", ground "
            + company.groundPopulation + ", speed " + company.getTilesPerDay() + " tile(s)/day."));
        sender.addChatMessage(new ChatComponentText("Status: " + (company.isMoving() ? "Moving / " + company.movementOrderId : "Stationed") + "."));
        sender.addChatMessage(new ChatComponentText("Authority: owner " + company.ownerName + ", controller "
            + (company.temporaryController == null ? company.ownerName : company.temporaryControllerName)
            + " (" + company.controllerAuthority + "), tendency " + company.tendency + "."));
        sender.addChatMessage(new ChatComponentText("Native faction: " + displayFaction(KOMEWartimeStewardshipService.nativeFaction(company))
            + "; authorized wars: " + (company.authorizedWarIds.isEmpty() ? "none" : joinValues(company.authorizedWarIds))
            + "; legal targets: " + joinValues(new ArrayList<String>(KOMEWartimeStewardshipService.authorizedOpponents(data, company))) + "."));
        sender.addChatMessage(new ChatComponentText("Population source: " + (company.populationSource.length() == 0 ? "native recorded unit sources" : company.populationSource)
            + "; withdrawal/demobilization: " + company.withdrawalState + "; reason: " + company.authorizationReason + "."));
        KOMEArmyMovementOrder order = company.movementOrderId == null ? null : data.armyMovements.get(company.movementOrderId);
        if (order == null) {
            order = findLastArrivedOrderForCompany(data, company.id);
        }
        if (order != null && KOMEArmyMovementOrder.ARRIVED.equals(order.status)) {
            sender.addChatMessage(new ChatComponentText("Last arrival: " + order.destinationTile + formatOrderSpawnLocation(order) + "."));
        }
        int physical = 0;
        String firstPhysical = "";
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            Entity physicalEntity = record == null ? null : findLoadedEntity(world, record.entity);
            if (physicalEntity != null) {
                physical++;
                if (firstPhysical.length() == 0) {
                    firstPhysical = " first at dim " + physicalEntity.dimension + " "
                        + formatBlockPos(physicalEntity.posX, physicalEntity.posY, physicalEntity.posZ);
                }
            }
        }
        sender.addChatMessage(new ChatComponentText("Physical spawned: " + physical + "/" + company.units.size()
            + (company.isMoving() ? " (moving companies should show 0 until arrival)" : firstPhysical) + "."));
    }

    private void handleMoveTime(ICommandSender sender, KOMEWorldData data, String[] args) {
        if (args.length < 2) {
            throw new WrongUsageException("/troops movetime <get|set <secondsPerTile>|settotal <seconds>|reset|daily <get|set HH:mm timezone>|stepdelay <get|set seconds>>");
        }
        if ("get".equalsIgnoreCase(args[1])) {
            if (data.movementTotalSecondsOverride > 0) {
                sender.addChatMessage(new ChatComponentText("Movement test speed: " + data.movementTotalSecondsOverride
                    + " seconds total per new movement order."));
            } else if (data.movementSecondsPerTileOverride > 0) {
                sender.addChatMessage(new ChatComponentText("Movement test speed: " + data.movementSecondsPerTileOverride
                    + " seconds per tile."));
            } else {
                sender.addChatMessage(new ChatComponentText("Movement timing: Normal daily reset at "
                    + data.movementDailyResetTime + " " + data.movementDailyResetTimezone
                    + ". Mounted-only 2 tiles/reset, ground or mixed 1 tile/reset."));
            }
            sender.addChatMessage(new ChatComponentText("Movement step delay: " + data.movementStepDelaySeconds
                + " second(s) at intermediate route tiles."));
            return;
        }
        if ("stepdelay".equalsIgnoreCase(args[1])) {
            if (args.length == 3 && "get".equalsIgnoreCase(args[2])) {
                sender.addChatMessage(new ChatComponentText("Movement step delay: " + data.movementStepDelaySeconds
                    + " second(s) at intermediate route tiles."));
                return;
            }
            if (args.length == 4 && "set".equalsIgnoreCase(args[2])) {
                if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                    throw new WrongUsageException("Only operators can change troop movement step delay.");
                }
                int seconds = parseInt(sender, args[3]);
                if (seconds < 0) {
                    throw new WrongUsageException("Step delay seconds cannot be negative.");
                }
                data.movementStepDelaySeconds = seconds;
                data.markDirty();
                sender.addChatMessage(new ChatComponentText("Movement step delay set to " + seconds
                    + " second(s)."));
                return;
            }
            throw new WrongUsageException("/troops movetime stepdelay <get|set seconds>");
        }
        if ("daily".equalsIgnoreCase(args[1])) {
            if (args.length == 3 && "get".equalsIgnoreCase(args[2])) {
                sender.addChatMessage(new ChatComponentText("Movement daily reset: " + data.movementDailyResetTime
                    + " " + data.movementDailyResetTimezone + "."));
                return;
            }
            if (args.length == 5 && "set".equalsIgnoreCase(args[2])) {
                if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                    throw new WrongUsageException("Only operators can change troop movement daily timing.");
                }
                int minuteOfDay = parseDailyResetMinute(args[3]);
                if (minuteOfDay < 0) {
                    throw new WrongUsageException("Daily reset time must be HH:mm, for example 20:00.");
                }
                TimeZone timezone = TimeZone.getTimeZone(args[4]);
                data.movementDailyResetTime = args[3];
                data.movementDailyResetTimezone = timezone.getID();
                data.markDirty();
                sender.addChatMessage(new ChatComponentText("Movement daily reset set to " + data.movementDailyResetTime
                    + " " + data.movementDailyResetTimezone + "."));
                return;
            }
            throw new WrongUsageException("/troops movetime daily <get|set HH:mm timezone>");
        }
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("Only operators can change troop movement timing.");
        }
        if ("reset".equalsIgnoreCase(args[1])) {
            data.movementSecondsPerTileOverride = 0;
            data.movementTotalSecondsOverride = 0;
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Movement timing reset to normal."));
            return;
        }
        if ("set".equalsIgnoreCase(args[1]) && args.length == 3) {
            int seconds = parseInt(sender, args[2]);
            if (seconds <= 0) {
                throw new WrongUsageException("Seconds per tile must be greater than zero.");
            }
            data.movementSecondsPerTileOverride = seconds;
            data.movementTotalSecondsOverride = 0;
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Movement timing override set to " + seconds
                + " seconds per tile for new movement orders."));
            return;
        }
        if ("settotal".equalsIgnoreCase(args[1]) && args.length == 3) {
            int seconds = parseInt(sender, args[2]);
            if (seconds <= 0) {
                throw new WrongUsageException("Total seconds must be greater than zero.");
            }
            data.movementTotalSecondsOverride = seconds;
            data.movementSecondsPerTileOverride = 0;
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Movement timing override set to " + seconds
                + " total seconds for each new movement order."));
            return;
        }
        throw new WrongUsageException("/troops movetime <get|set <secondsPerTile>|settotal <seconds>|reset|daily <get|set HH:mm timezone>|stepdelay <get|set seconds>>");
    }

    private void handleMovementAdmin(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, String[] args) {
        boolean admin = sender.canCommandSenderUseCommand(2, getCommandName());
        if (args.length < 2) {
            throw new WrongUsageException("/troops movement <complete|retry|resume|pause|cancelspawn|retarget|advance|advanceall|ticknow|next|stop> ...");
        }
        if ("continue".equalsIgnoreCase(args[1])) {
            args[1] = "resume";
        } else if ("halt".equalsIgnoreCase(args[1])) {
            args[1] = "stop";
        }
        if ("ticknow".equalsIgnoreCase(args[1])) {
            if (!admin) {
                throw new WrongUsageException("Only operators can advance movement timing.");
            }
            if (args.length != 2) {
                throw new WrongUsageException("/troops movement ticknow");
            }
            int advanced = 0;
            long now = System.currentTimeMillis();
            for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
                if (order != null && order.isMoving()) {
                    advanceMovementOrder(data, order, Math.max(1, order.tilesPerDay), now);
                    advanced++;
                }
            }
            data.markDirty();
            processMovementTick(data, KOMEReflection.getWorld(player), now);
            sender.addChatMessage(new ChatComponentText("Advanced " + advanced + " active movement order(s) by their daily speed."));
            return;
        }
        if ("advanceall".equalsIgnoreCase(args[1])) {
            if (!admin) {
                throw new WrongUsageException("Only operators can advance all movement orders.");
            }
            if (args.length != 3) {
                throw new WrongUsageException("/troops movement advanceall <steps>");
            }
            int steps = Math.max(1, parseInt(sender, args[2]));
            int advanced = 0;
            long now = System.currentTimeMillis();
            for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
                if (order != null && order.isMoving()) {
                    advanceMovementOrder(data, order, steps, now);
                    advanced++;
                }
            }
            data.markDirty();
            processMovementTick(data, KOMEReflection.getWorld(player), now);
            sender.addChatMessage(new ChatComponentText("Advanced " + advanced + " active movement order(s) by " + steps + " route step(s)."));
            return;
        }
        if (args.length < 3) {
            throw new WrongUsageException("/troops movement <complete|retry|resume|pause|cancelspawn|retarget|advance|next|stop> <orderId> [x y z|steps]");
        }
        KOMEArmyMovementOrder order = data.armyMovements.get(args[2]);
        boolean retreatRequest = "retreat".equalsIgnoreCase(args[1]);
        boolean resumeRequest = "resume".equalsIgnoreCase(args[1]);
        if (order == null || !order.isMoving() && !((retreatRequest || resumeRequest) && KOMEArmyMovementOrder.STOPPED.equals(order.status))) {
            throw new WrongUsageException("No active movement order " + args[2] + ".");
        }
        boolean controllerAction = KOMEAllianceTemporaryCommandPolicy.allows(args[1]);
        if (!controllerAction && !admin) {
            throw new WrongUsageException("Only operators can manage movement order spawning.");
        }
        if (controllerAction && !canPlayerControlMovementOrder(player, data, order)) {
            throw new WrongUsageException("You do not control movement order " + order.id + ".");
        }
        if ("next".equalsIgnoreCase(args[1])) {
            if (args.length != 3) {
                throw new WrongUsageException("/troops movement next <orderId>");
            }
            if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)) {
                order.spawnRetryPaused = false;
                order.nextStepDepartureMillis = System.currentTimeMillis();
            } else {
                order.status = KOMEArmyMovementOrder.MOVING;
                order.arrivalMillis = System.currentTimeMillis();
                order.stepArrivalMillis = order.arrivalMillis;
                order.spawnRetryPaused = false;
                order.nextSpawnRetryMillis = 0L;
            }
            data.markDirty();
            processMovementTick(data, KOMEReflection.getWorld(player), System.currentTimeMillis());
            sender.addChatMessage(new ChatComponentText("Advanced movement order " + order.id + " to its next route step. "
                + formatMovementClock(order, System.currentTimeMillis()) + "."));
            return;
        }
        if ("stop".equalsIgnoreCase(args[1]) || "stay".equalsIgnoreCase(args[1])) {
            if (KOMEArmyMovementOrder.WAR_ENDED_HALTED.equals(order.status)) {
                throw new WrongUsageException("This war-ended stewardship company may retreat only.");
            }
            if (args.length != 3) {
                throw new WrongUsageException("/troops movement stay <orderId>");
            }
            if (!canPlayerControlMovementOrder(player, data, order)) {
                throw new WrongUsageException("You do not control movement order " + order.id + ".");
            }
            order.accessChoice = "stay".equalsIgnoreCase(args[1]) ? "STAY" : "STOP";
            stopMovementOrder(sender, data, order, System.currentTimeMillis());
            return;
        }
        if ("retreat".equalsIgnoreCase(args[1])) {
            if (args.length != 3 || !canPlayerControlMovementOrder(player, data, order)) {
                throw new WrongUsageException("/troops movement retreat <orderId> (controller only)");
            }
            beginRetreat(sender, data, order, System.currentTimeMillis());
            processMovementTick(data, KOMEReflection.getWorld(player), System.currentTimeMillis());
            return;
        }
        if ("resume".equalsIgnoreCase(args[1]) && (KOMEArmyMovementOrder.ACCESS_HALTED.equals(order.status)
                || KOMEArmyMovementOrder.HOLDING.equals(order.status)
                || KOMEArmyMovementOrder.STOPPED.equals(order.status) && order.accessLossReason.length() > 0)) {
            if (!canPlayerControlMovementOrder(player, data, order)) {
                throw new WrongUsageException("You do not control movement order " + order.id + ".");
            }
            resumeAccessHaltedRoute(sender, data, order, System.currentTimeMillis());
            processMovementTick(data, KOMEReflection.getWorld(player), System.currentTimeMillis());
            return;
        }
        if (KOMEArmyMovementOrder.WAR_ENDED_HALTED.equals(order.status)) {
            throw new WrongUsageException("This war-ended stewardship company may retreat only.");
        }
        if ("advance".equalsIgnoreCase(args[1])) {
            if (args.length != 4) {
                throw new WrongUsageException("/troops movement advance <orderId> <steps>");
            }
            int steps = Math.max(1, parseInt(sender, args[3]));
            advanceMovementOrder(data, order, steps, System.currentTimeMillis());
            data.markDirty();
            processMovementTick(data, KOMEReflection.getWorld(player), System.currentTimeMillis());
            sender.addChatMessage(new ChatComponentText("Advanced " + order.id + " by " + steps + " route step(s). "
                + formatMovementClock(order, System.currentTimeMillis()) + "."));
            return;
        }
        if ("retarget".equalsIgnoreCase(args[1])) {
            if (args.length != 3 && args.length != 6) {
                throw new WrongUsageException("/troops movement retarget <orderId> [x y z]");
            }
            order.arrivalPointTileId = activeStepDestination(order);
            order.arrivalPointSource = "Manual retarget";
            order.arrivalDimension = player.dimension;
            if (args.length == 6) {
                order.arrivalX = parseDouble(sender, args[3]);
                order.arrivalY = parseDouble(sender, args[4]);
                order.arrivalZ = parseDouble(sender, args[5]);
            } else {
                order.arrivalX = player.posX;
                order.arrivalY = player.posY;
                order.arrivalZ = player.posZ;
            }
            order.status = KOMEArmyMovementOrder.SPAWN_BLOCKED;
            order.spawnRetryPaused = true;
            order.pendingSpawnReason = "Retargeted to " + formatBlockPos(order.arrivalX, order.arrivalY, order.arrivalZ)
                + ". Use /troops movement retry " + order.id + ".";
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Retargeted " + order.id + " to dim " + order.arrivalDimension
                + " at " + formatBlockPos(order.arrivalX, order.arrivalY, order.arrivalZ) + "."));
            return;
        }
        if ("pause".equalsIgnoreCase(args[1]) || "cancelspawn".equalsIgnoreCase(args[1])) {
            order.status = KOMEArmyMovementOrder.SPAWN_BLOCKED;
            order.spawnRetryPaused = true;
            order.pendingSpawnReason = "Manual spawn retry pause. Use /troops movement retry " + order.id + " to try again.";
            data.markDirty();
            sender.addChatMessage(new ChatComponentText("Paused automatic spawn retries for movement order " + order.id + "."));
            return;
        }
        if (!"complete".equalsIgnoreCase(args[1]) && !"retry".equalsIgnoreCase(args[1]) && !"resume".equalsIgnoreCase(args[1])) {
            throw new WrongUsageException("/troops movement <complete|retry|resume|pause|cancelspawn|retarget|next|stop> <orderId>");
        }
        if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)
                && ("retry".equalsIgnoreCase(args[1]) || "resume".equalsIgnoreCase(args[1]))) {
            order.spawnRetryPaused = false;
            order.pendingSpawnReason = "";
            order.nextStepDepartureMillis = System.currentTimeMillis();
            data.markDirty();
            processMovementTick(data, KOMEReflection.getWorld(player), System.currentTimeMillis());
            sender.addChatMessage(new ChatComponentText("Resumed next-step departure for movement order " + order.id + ". "
                + formatMovementClock(order, System.currentTimeMillis()) + "."));
            return;
        }
        if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)
                && "complete".equalsIgnoreCase(args[1])) {
            long now = System.currentTimeMillis();
            order.spawnRetryPaused = false;
            order.nextStepDepartureMillis = now;
            data.markDirty();
            processMovementTick(data, KOMEReflection.getWorld(player), now);
            if (KOMEArmyMovementOrder.MOVING.equals(order.status)) {
                order.arrivalMillis = System.currentTimeMillis();
                order.stepArrivalMillis = order.arrivalMillis;
            }
        }
        order.status = KOMEArmyMovementOrder.MOVING;
        order.pendingSpawnReason = "";
        order.spawnRetryPaused = false;
        order.nextSpawnRetryMillis = 0L;
        if ("retry".equalsIgnoreCase(args[1])) {
            order.spawnAttemptCount = 0;
            order.lastSpawnFailureCode = "";
            order.lastSpawnFailureDetails = "";
        }
        if (!"resume".equalsIgnoreCase(args[1])) {
            order.arrivalMillis = System.currentTimeMillis();
        }
        data.markDirty();
        processArrivals(data, KOMEReflection.getWorld(player), System.currentTimeMillis(), true);
        if (KOMEArmyMovementOrder.ARRIVED.equals(order.status)) {
            sender.addChatMessage(new ChatComponentText("Forced movement order " + order.id + " completed and spawned"
                + formatOrderSpawnLocation(order) + "."));
        } else if (order.isPendingSpawn()) {
            sender.addChatMessage(new ChatComponentText("Forced movement order " + order.id
                + " reached arrival but is pending spawn: " + order.pendingSpawnReason));
        } else {
            sender.addChatMessage(new ChatComponentText("Forced movement order " + order.id
                + " attempted arrival but is still " + order.status + "."));
        }
    }

    private void handleCompanyProtectionCommand(ICommandSender sender, KOMEWorldData data, World world, UUID actor, String companyId, String action) {
        KOMEArmyCompany company = data.armyCompanies.get(companyId);
        if (company == null) {
            throw new WrongUsageException("Unknown company " + companyId + ".");
        }
        if (!actor.equals(company.owner) && !sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("You can only manage your own companies.");
        }
        boolean halt = "halt".equalsIgnoreCase(action);
        boolean resume = "resume".equalsIgnoreCase(action) || "ready".equalsIgnoreCase(action) || "deploy".equalsIgnoreCase(action);
        boolean status = "status".equalsIgnoreCase(action) || "debug".equalsIgnoreCase(action);
        if (!halt && !resume && !status) {
            throw new WrongUsageException("/troops company <companyId> <halt|resume|status>");
        }
        int protectedCount = 0;
        int activeCount = 0;
        int changed = 0;
        int blocked = 0;
        for (UUID unitId : new ArrayList<UUID>(company.units)) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record == null) {
                continue;
            }
            Entity entity = findLoadedEntity(world, record.entity);
            if (halt) {
                if (entity instanceof LOTREntityNPC && ((LOTREntityNPC) entity).isEntityAlive()) {
                    LOTREntityNPC npc = (LOTREntityNPC) entity;
                    if (KOMEHaltedUnitProtection.canApplyHornHalt(npc)) {
                        npc.hiredNPCInfo.halt();
                        record.stationedEntityData = KOMEEntitySnapshots.snapshot(entity);
                        changed++;
                    } else {
                        blocked++;
                    }
                } else if (setSavedHaltState(record, true)) {
                    changed++;
                }
            } else if (resume) {
                if (entity instanceof LOTREntityNPC && ((LOTREntityNPC) entity).isEntityAlive()) {
                    ((LOTREntityNPC) entity).hiredNPCInfo.ready();
                    record.stationedEntityData = KOMEEntitySnapshots.snapshot(entity);
                    changed++;
                } else if (setSavedHaltState(record, false)) {
                    changed++;
                }
            }
            if (KOMEHaltedUnitProtection.isProtectedRecord(data, world, record)) {
                protectedCount++;
            } else {
                activeCount++;
            }
        }
        if (halt || resume) {
            data.markDirty();
        }
        sender.addChatMessage(new ChatComponentText(company.name + " (" + company.id + "): protected/inactive "
            + protectedCount + ", active/vulnerable " + activeCount + "."));
        if (halt) {
            sender.addChatMessage(new ChatComponentText("Horn-style halted " + changed + " unit(s)"
                + (blocked > 0 ? "; " + blocked + " blocked by combat cooldown." : ".")));
        } else if (resume) {
            sender.addChatMessage(new ChatComponentText("Readied " + changed + " unit(s): active and vulnerable."));
        }
    }

    private static boolean setSavedHaltState(KOMEHiredUnitRecord record, boolean halted) {
        NBTTagCompound snapshot = record == null ? null : record.stationedEntityData;
        if (snapshot == null || !snapshot.hasKey("HiredNPCInfo", 10)) {
            return false;
        }
        NBTTagCompound info = snapshot.getCompoundTag("HiredNPCInfo");
        boolean changed = info.getBoolean("CanMove") == halted || info.getBoolean("GuardMode");
        info.setBoolean("CanMove", !halted);
        info.setBoolean("GuardMode", false);
        return changed;
    }

    private void advanceMovementOrder(KOMEWorldData data, KOMEArmyMovementOrder order, int steps, long nowMillis) {
        if (order == null || !order.isMoving()) {
            return;
        }
        int distance = Math.max(1, order.distanceTiles);
        order.completedSteps = Math.min(distance, Math.max(0, order.completedSteps) + Math.max(1, steps));
        order.currentRouteIndex = Math.min(order.completedSteps, Math.max(0, order.routeTiles.size() - 1));
        order.nextDailyStepMillis = nextDailyResetMillis(data, nowMillis);
        if (order.completedSteps >= distance) {
            order.arrivalMillis = nowMillis;
            order.status = KOMEArmyMovementOrder.MOVING;
            order.spawnRetryPaused = false;
            order.nextSpawnRetryMillis = 0L;
            return;
        }
        int remaining = Math.max(1, distance - order.completedSteps);
        order.arrivalMillis = nowMillis + getTravelMillis(data, remaining, Math.max(1, order.tilesPerDay));
    }

    private void stopMovementOrder(ICommandSender sender, KOMEWorldData data, KOMEArmyMovementOrder order, long nowMillis) {
        if (!KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)
                && !KOMEArmyMovementOrder.ACCESS_HALTED.equals(order.status)) {
            throw new WrongUsageException("Movement order " + order.id
                + " can only be stopped after the company has reached a route tile. Current status: " + order.status + ".");
        }
        String stoppedTile = activeStepOrigin(order);
        for (UUID unitId : new ArrayList<UUID>(order.units)) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record == null) {
                continue;
            }
            record.currentTile = stoppedTile;
            record.movementOrderId = "";
            record.movingEntityData = null;
        }
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        if (company != null) {
            company.currentTile = stoppedTile;
            company.status = KOMEArmyCompany.STATIONED;
            company.movementOrderId = "";
            company.updatedAtMillis = nowMillis;
            refreshCompany(data, company);
        }
        boolean holding = "STAY".equals(order.accessChoice) && order.accessLossReason.length() > 0;
        order.status = holding ? KOMEArmyMovementOrder.HOLDING : KOMEArmyMovementOrder.STOPPED;
        order.stopped = true;
        order.pendingSpawnReason = "Stopped at " + stoppedTile + ".";
        if (!holding) {
            order.nextTile = "";
            order.currentStepDestinationTile = "";
        }
        order.nextStepDepartureMillis = 0L;
        order.nextStepAvailableMillis = 0L;
        order.spawnRetryPaused = false;
        UUID stoppedBy = sender instanceof EntityPlayer ? KOMEReflection.getEntityUUID((EntityPlayer) sender) : null;
        data.markMovementHistoryStopped(order, stoppedBy, sender.getCommandSenderName(), nowMillis);
        data.markDirty();
        data.syncConquestTiles();
        sender.addChatMessage(new ChatComponentText((holding ? "Company is holding/stranded" : "Stopped movement order " + order.id)
            + " at " + stoppedTile + ". Population funding and ownership are unchanged."
            + (holding ? " Retreat remains available; recommendation: "
                + (company != null && KOMEArmyCompany.AGGRESSIVE.equals(company.tendency) ? "Stay (Aggressive)" : "Retreat (Conservative)") + "." : "")));
    }

    void beginRetreat(ICommandSender sender, KOMEWorldData data, KOMEArmyMovementOrder order, long nowMillis) {
        if (!KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)
                && !KOMEArmyMovementOrder.ACCESS_HALTED.equals(order.status)
                && !KOMEArmyMovementOrder.STOPPED.equals(order.status)
                && !KOMEArmyMovementOrder.HOLDING.equals(order.status)
                && !KOMEArmyMovementOrder.WAR_ENDED_HALTED.equals(order.status)) {
            throw new WrongUsageException("Retreat is available only after the company has physically reached a route tile.");
        }
        String current = KOMEConquestTile.normalizeId(order.currentTile);
        List<String> traveled = new ArrayList<String>(order.traveledRouteTiles);
        if (traveled.isEmpty()) {
            traveled.add(current);
        }
        int currentIndex = traveled.lastIndexOf(current);
        if (currentIndex < 0) {
            traveled.add(current);
            currentIndex = traveled.size() - 1;
        }
        KOMEMovementRecoveryOptions recovery = KOMEMovementRecoveryOptions.forOrder(data, order);
        if (!recovery.canRetreat) {
            KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
            if (company != null && company.stewardshipCreated) {
                company.withdrawalState = KOMEArmyCompany.CLEANUP_ADMIN;
                order.pendingSpawnReason = recovery.retreatBlockedReason;
                order.accessLossReason = recovery.retreatBlockedReason;
                data.markDirty();
            }
            throw new WrongUsageException(recovery.retreatBlockedReason
                + " The halted company is preserved for admin resolution.");
        }
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        int safeIndex = recovery.retreatRouteIndex;
        order.routeTiles.clear();
        for (int i = currentIndex; i >= safeIndex; i--) {
            String tile = KOMEConquestTile.normalizeId(traveled.get(i));
            if (order.routeTiles.isEmpty() || !tile.equals(order.routeTiles.get(order.routeTiles.size() - 1))) {
                order.routeTiles.add(tile);
            }
        }
        if (order.routeTiles.size() < 2) {
            throw new WrongUsageException("The company is already at the nearest legal retreat tile.");
        }
        order.originTile = current;
        order.destinationTile = order.routeTiles.get(order.routeTiles.size() - 1);
        order.finalDestinationTile = order.destinationTile;
        order.currentRouteIndex = 0;
        order.nextRouteIndex = 1;
        order.finalRouteIndex = order.routeTiles.size() - 1;
        order.totalSteps = order.routeTiles.size() - 1;
        order.distanceTiles = order.totalSteps;
        order.completedSteps = 0;
        order.currentTile = current;
        order.currentStepOriginTile = current;
        order.currentStepDestinationTile = order.routeTiles.get(1);
        order.nextTile = order.currentStepDestinationTile;
        order.retreating = true;
        order.haltAfterArrival = false;
        order.accessChoice = "RETREAT";
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.nextStepDepartureMillis = nowMillis;
        order.nextStepAvailableMillis = nowMillis;
        order.spawnRetryPaused = false;
        for (UUID unitId : order.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null) {
                record.movementOrderId = order.id;
            }
        }
        if (company != null) {
            company.status = KOMEArmyCompany.MOVING;
            company.movementOrderId = order.id;
            company.updatedAtMillis = nowMillis;
        }
        data.updateMovementHistory(order, KOMEMovementHistoryRecord.ACTIVE);
        data.markDirty();
        sender.addChatMessage(new ChatComponentText("Retreat ordered along traveled route " + formatRouteTiles(order.routeTiles)
            + " to " + order.destinationTile + "."));
    }

    void resumeAccessHaltedRoute(ICommandSender sender, KOMEWorldData data, KOMEArmyMovementOrder order, long nowMillis) {
        String prospectiveDestination = KOMEMovementAccessService.resolveProspectiveForwardStep(order);
        if (prospectiveDestination.length() == 0) {
            throw new WrongUsageException("No remaining forward route or movement step is available; Resume is not legal.");
        }
        if (!isMovementStepAuthorized(data, order, activeStepOrigin(order), prospectiveDestination, false)) {
            throw new WrongUsageException("The route is still unauthorized: "
                + movementAccessReason(data, order, prospectiveDestination));
        }
        if (order.currentStepDestinationTile.length() == 0) {
            order.currentStepDestinationTile = prospectiveDestination;
            order.nextTile = prospectiveDestination;
        }
        order.haltAfterArrival = false;
        order.retreating = false;
        for (UUID unitId : order.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null) {
                record.movementOrderId = order.id;
            }
        }
        KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
        if (company != null) {
            company.status = KOMEArmyCompany.MOVING;
            company.movementOrderId = order.id;
            company.updatedAtMillis = nowMillis;
        }
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.accessChoice = "RESUME";
        order.pendingSpawnReason = "";
        order.nextStepDepartureMillis = nowMillis;
        order.nextStepAvailableMillis = nowMillis;
        order.spawnRetryPaused = false;
        data.updateMovementHistory(order, KOMEMovementHistoryRecord.ACTIVE);
        data.markDirty();
        sender.addChatMessage(new ChatComponentText("Resuming movement order " + order.id + " after passage was restored."));
    }

    private void handleArrival(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, String[] args) {
        if (args.length == 2 && "missing".equalsIgnoreCase(args[1])) {
            listMissingArrivalPoints(sender, data);
            return;
        }
        if (args.length == 2 && "backfill".equalsIgnoreCase(args[1])) {
            backfillArrivalPoints(sender, data);
            return;
        }
        if (args.length != 3) {
            throw new WrongUsageException("/troops arrival <set|get|clear|validate|tp> <tileId> | missing | backfill");
        }
        String action = args[1].toLowerCase();
        String tileId = parseTile(args[2]);
        if ("set".equals(action)) {
            setArrivalPoint(sender, player, data, tileId);
            return;
        }
        if ("get".equals(action)) {
            listArrivalPoint(sender, data, tileId);
            return;
        }
        if ("validate".equals(action)) {
            SpawnTarget target = requireArrivalTarget(data, KOMEReflection.getWorld(player), tileId);
            sender.addChatMessage(new ChatComponentText("Arrival Point for " + tileId + ": "
                + (target.valid ? "valid at dim " + target.dimensionId + " " + formatBlockPos(target.x, target.y, target.z)
                : "invalid - " + target.failureReason) + "."));
            return;
        }
        if ("clear".equals(action)) {
            if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                throw new WrongUsageException("Only operators can clear arrival points.");
            }
            boolean removed = data.clearTileWaypoint(tileId, KOMETileWaypoint.RALLY);
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText((removed ? "Cleared" : "No") + " Arrival Point for " + tileId + "."));
            return;
        }
        if ("tp".equals(action)) {
            if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
                throw new WrongUsageException("Only operators can teleport to arrival points.");
            }
            KOMETileWaypoint point = getArrivalPoint(data, tileId);
            if (point == null) {
                throw new WrongUsageException("Tile " + tileId + " has no Arrival Point.");
            }
            player.travelToDimension(point.dimensionId);
            player.setPositionAndUpdate(point.x, point.y, point.z);
            sender.addChatMessage(new ChatComponentText("Teleported to Arrival Point for " + tileId + "."));
            return;
        }
        throw new WrongUsageException("/troops arrival <set|get|clear|validate|tp> <tileId> | missing | backfill");
    }

    private void listMissingArrivalPoints(ICommandSender sender, KOMEWorldData data) {
        int missing = 0;
        StringBuilder shown = new StringBuilder();
        for (KOMEConquestTile tile : data.conquestTiles.values()) {
            if (tile != null) {
                data.ensureDefaultArrivalPoint(tile);
            }
            if (tile == null || !tile.isClaimed() || getArrivalPoint(data, tile.id) != null) {
                continue;
            }
            missing++;
            if (shown.length() < 180) {
                if (shown.length() > 0) {
                    shown.append(", ");
                }
                shown.append(tile.id);
            }
        }
        sender.addChatMessage(new ChatComponentText("Claimed tiles missing Arrival Points: " + missing
            + (shown.length() > 0 ? " (" + shown + (missing > 12 ? ", ..." : "") + ")" : "") + "."));
    }

    private void backfillArrivalPoints(ICommandSender sender, KOMEWorldData data) {
        int filled = 0;
        int missing = 0;
        for (KOMEConquestTile tile : data.conquestTiles.values()) {
            if (tile == null || !tile.isClaimed() || getArrivalPoint(data, tile.id) != null) {
                continue;
            }
            data.ensureDefaultArrivalPoint(tile);
            if (getArrivalPoint(data, tile.id) != null) {
                filled++;
            } else {
                missing++;
            }
        }
        if (filled > 0) {
            data.markDirty();
            data.syncConquestTiles();
        }
        sender.addChatMessage(new ChatComponentText("Backfilled " + filled + " automatic Arrival Points. "
            + missing + " claimed tile(s) have no automatic default and still need /troops arrival set <tileId>."));
    }

    private void handleWaypoint(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, String[] args) {
        if (args.length < 3) {
            throw new WrongUsageException("/troops waypoint <set|get|clear|validate> <tileId> [rally|north|south|east|west]");
        }
        String action = args[1].toLowerCase();
        String tileId = parseTile(args[2]);
        if ("get".equals(action)) {
            listWaypoints(sender, data, tileId);
            return;
        }
        if ("validate".equals(action)) {
            validateWaypoints(sender, data, KOMEReflection.getWorld(player), tileId);
            return;
        }
        if (args.length != 4) {
            throw new WrongUsageException("/troops waypoint " + action + " <tileId> <rally|north|south|east|west>");
        }
        String type = KOMETileWaypoint.normalizeType(args[3]);
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            KOMEConquestTile tile = data.conquestTiles.get(tileId);
            String playerFaction = normalizeFaction(getPlayerFaction(data, player));
            String rulingFaction = tile == null ? "" : tile.currentRulingFaction();
            if (tile == null || !playerFaction.equals(normalizeFaction(rulingFaction)) || !data.isFactionKing(rulingFaction, KOMEReflection.getEntityUUID(player))) {
                throw new WrongUsageException("Only operators or the owning faction's king can manage troop waypoints.");
            }
        }
        if ("set".equals(action)) {
            data.setTileWaypoint(tileId, type, player.dimension, player.posX, player.posY, player.posZ, player.getCommandSenderName(), true);
            if (KOMETileWaypoint.RALLY.equals(type)) {
                KOMEConquestTile tile = data.getConquestTile(tileId);
                tile.setAnchor(player.dimension, player.posX, player.posY, player.posZ);
            }
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText("Set " + KOMETileWaypoint.displayType(type) + " for " + tileId + " at "
                + MathHelper.floor_double(player.posX) + ", " + MathHelper.floor_double(player.posY) + ", "
                + MathHelper.floor_double(player.posZ) + "."));
            return;
        }
        if ("clear".equals(action)) {
            boolean removed = data.clearTileWaypoint(tileId, type);
            data.syncConquestTiles();
            sender.addChatMessage(new ChatComponentText((removed ? "Cleared " : "No ") + KOMETileWaypoint.displayType(type)
                + (removed ? " for " : " exists for ") + tileId + "."));
            return;
        }
        throw new WrongUsageException("/troops waypoint <set|get|clear|validate> <tileId> [rally|north|south|east|west]");
    }

    private void listWaypoints(ICommandSender sender, KOMEWorldData data, String tileId) {
        sender.addChatMessage(new ChatComponentText("Waypoints for " + tileId + ":"));
        String[] types = waypointTypes();
        for (String type : types) {
            KOMETileWaypoint waypoint = data.getTileWaypoint(tileId, type);
            if (waypoint == null) {
                sender.addChatMessage(new ChatComponentText("  " + KOMETileWaypoint.displayType(type) + ": missing"));
            } else {
                sender.addChatMessage(new ChatComponentText("  " + KOMETileWaypoint.displayType(type) + ": dim " + waypoint.dimensionId
                    + " at " + MathHelper.floor_double(waypoint.x) + ", " + MathHelper.floor_double(waypoint.y)
                    + ", " + MathHelper.floor_double(waypoint.z) + (waypoint.manualOverride ? " (manual)" : " (auto/legacy)") + "."));
            }
        }
    }

    private void validateWaypoints(ICommandSender sender, KOMEWorldData data, World world, String tileId) {
        sender.addChatMessage(new ChatComponentText("Arrival validation for " + tileId + ":"));
        String[] types = waypointTypes();
        for (String type : types) {
            SpawnTarget target = resolveArrivalTarget(data, world, null, data.conquestTiles.get(tileId), type);
            sender.addChatMessage(new ChatComponentText("  " + KOMETileWaypoint.displayType(type) + ": "
                + (target.valid ? "valid via " + target.label : "invalid - " + target.failureReason) + "."));
        }
    }

    private String waypointSummary(KOMEWorldData data, String tileId) {
        StringBuilder text = new StringBuilder();
        String[] types = waypointTypes();
        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                text.append(", ");
            }
            KOMETileWaypoint waypoint = data.getTileWaypoint(tileId, types[i]);
            text.append(KOMETileWaypoint.displayType(types[i])).append("=");
            text.append(waypoint == null ? "missing" : waypoint.manualOverride ? "manual" : "legacy");
        }
        return text.toString();
    }

    private void createCompany(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, UUID owner, String tile, String requestedName) {
        String faction = getPlayerFaction(data, player);
        requireFactionStandableTile(data, faction, tile, "Company origin");
        KOMEArmyCompany company = new KOMEArmyCompany();
        company.id = nextCompanyId(data);
        company.owner = owner;
        company.ownerName = player.getCommandSenderName();
        company.faction = faction;
        company.name = requestedName.length() == 0 ? "Company " + company.id : requestedName;
        company.source = KOMEArmyCompany.SOURCE_MANUAL_LEGACY;
        company.currentTile = tile;
        company.createdAtMillis = System.currentTimeMillis();
        company.updatedAtMillis = company.createdAtMillis;
        ForgeChunkManager.Ticket originTicket = acquireTemporaryTileChunk(data, KOMEReflection.getWorld(player), tile, company.createdAtMillis);
        int snapshotsSaved = 0;
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record == null || record.entity == null || record.farmhand || record.type != KOMEPopulationType.OFFENSIVE
                    || !owner.equals(record.owner) || !tile.equals(KOMEConquestTile.normalizeId(record.currentTile))
                    || record.isMoving() || record.companyId != null && record.companyId.length() > 0) {
                continue;
            }
            record.companyId = company.id;
            record.companyName = KOMEHiredUnitRecord.normalizeCompanyName(company.name);
            record.companyAssignedAtMillis = company.createdAtMillis;
            record.companyAssignedBy = owner;
            record.companyAssignedByName = player.getCommandSenderName();
            Entity entity = findLoadedEntity(KOMEReflection.getWorld(player), record.entity);
            if (!(entity instanceof LOTREntityNPC) || !entity.isEntityAlive()) {
                entity = reconcileLoadedStationedUnit(data, KOMEReflection.getWorld(player), company, record);
            }
            if (entity instanceof LOTREntityNPC && entity.isEntityAlive()) {
                record.stationedEntityData = KOMEEntitySnapshots.snapshot(entity);
                if (record.stationedEntityData != null) {
                    snapshotsSaved++;
                }
            }
            company.units.add(record.entity);
        }
        releaseTemporaryArrivalChunk(originTicket);
        refreshCompany(data, company);
        if (company.units.isEmpty()) {
            throw new WrongUsageException("No unassigned offensive units are stationed at " + tile + ".");
        }
        data.armyCompanies.put(company.id, company);
        data.markDirty();
        data.syncConquestTiles();
        sender.addChatMessage(new ChatComponentText("Created " + company.name + " (" + company.id + ") from "
            + company.units.size() + " offensive units at " + tile + ". Saved stationary data for "
            + snapshotsSaved + "/" + company.units.size() + " units."));
    }

    private void snapshotCompany(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, UUID owner, String companyId) {
        KOMEArmyCompany company = data.armyCompanies.get(companyId);
        if (company == null) {
            throw new WrongUsageException("No company " + companyId + ".");
        }
        if (!owner.equals(company.owner) && !sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("You can only snapshot your own companies.");
        }
        World world = KOMEReflection.getWorld(player);
        ForgeChunkManager.Ticket originTicket = acquireTemporaryTileChunk(data, world, company.currentTile, System.currentTimeMillis());
        int saved = 0;
        List<String> missing = new ArrayList<String>();
        try {
            for (UUID unitId : new ArrayList<UUID>(company.units)) {
                KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                if (record == null || record.isMoving()) {
                    continue;
                }
                Entity entity = findLoadedEntity(world, record.entity);
                if (!(entity instanceof LOTREntityNPC) || !entity.isEntityAlive()) {
                    entity = reconcileLoadedStationedUnit(data, world, company, record);
                }
                if (entity instanceof LOTREntityNPC && entity.isEntityAlive()) {
                    record.stationedEntityData = KOMEEntitySnapshots.snapshot(entity);
                    if (record.stationedEntityData != null) {
                        saved++;
                    }
                } else if (record.stationedEntityData == null) {
                    missing.add(displayUnitId(record));
                }
            }
        } finally {
            releaseTemporaryArrivalChunk(originTicket);
        }
        data.markDirty();
        sender.addChatMessage(new ChatComponentText("Saved stationary data for " + saved + "/" + company.units.size()
            + " units in " + company.name + "."));
        if (!missing.isEmpty()) {
            sender.addChatMessage(new ChatComponentText("Still missing live/saved data for: " + joinDebugList(missing)
                + ". Stand near those physical units and run /troops snapshotcompany " + company.id + " again."));
        }
    }

    private void previewCompanyMove(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, UUID owner, String companyId, String destination) {
        KOMEArmyCompany company = validateCompanyMove(player, data, owner, companyId, destination);
        refreshCompany(data, company);
        RouteResult route = findLegalRoute(data, company.currentTile, destination, company.faction, company);
        validateStewardshipRoute(data, company, route);
        if (!route.valid) {
            sendMovePreviewFailure(player, company, destination, route);
            sender.addChatMessage(new ChatComponentText("Route blocked to " + destination + ". See map panel for details."));
            return;
        }
        SpawnTarget routeArrival = validateRouteArrivalTargets(data, KOMEReflection.getWorld(player), route);
        if (!routeArrival.valid) {
            sendMovePreviewFailure(player, company, destination, routeArrival.failureReason);
            sender.addChatMessage(new ChatComponentText("Route blocked to " + destination + ". See map panel for details."));
            return;
        }
        SpawnTarget arrival = requireArrivalTarget(data, KOMEReflection.getWorld(player), destination);
        if (!arrival.valid) {
            sendMovePreviewFailure(player, company, destination, arrival.failureReason);
            sender.addChatMessage(new ChatComponentText("Route blocked to " + destination + ". See map panel for details."));
            return;
        }
        int distance = route.distance();
        int speed = company.getTilesPerDay();
        KOMEPacketCompanyMoveConfirmGui message = new KOMEPacketCompanyMoveConfirmGui();
        message.companyId = company.id;
        message.companyName = company.name;
        message.originTile = company.currentTile;
        message.destinationTile = destination;
        message.distanceTiles = distance;
        message.unitCount = company.units.size();
        message.population = company.totalPopulation;
        message.mountedPopulation = company.mountedPopulation;
        message.groundPopulation = company.groundPopulation;
        message.tilesPerDay = speed;
        message.travelMillis = getRouteCompletionMillis(data, distance, speed);
        message.routeSummary = routeSummary(route) + ". First move is immediate; cooldown before each next step is "
            + formatDuration(getStepCooldownMillis(data, null, System.currentTimeMillis())) + ".";
        message.arrivalDimension = arrival.dimensionId;
        message.arrivalX = arrival.x;
        message.arrivalY = arrival.y;
        message.arrivalZ = arrival.z;
        message.arrivalSource = arrival.label;
        message.routeTiles.addAll(route.routeTiles);
        KOMEPacketHandler.network.sendTo(message, player);
        sender.addChatMessage(new ChatComponentText("Previewed company " + company.name + " (" + company.id + ") route "
            + formatRouteTiles(route.routeTiles) + " to " + destination + "."));
    }

    private void sendMovePreviewFailure(EntityPlayerMP player, KOMEArmyCompany company, String destination, RouteResult route) {
        KOMEPacketCompanyMovePreviewResult message = basePreviewFailure(company, destination);
        message.failureSummary = routeFailureSummary(route, company);
        List<RouteBlocker> blockers = sortedRouteBlockers(route);
        int shown = Math.min(5, blockers.size());
        for (int i = 0; i < shown; i++) {
            RouteBlocker blocker = blockers.get(i);
            message.failureDetails.add(blocker.fromTile + " -> " + blocker.toTile + ": " + stripTrailingPeriod(blocker.reason));
        }
        if (blockers.size() > shown) {
            message.hiddenDetailCount = blockers.size() - shown;
        }
        if (message.failureDetails.isEmpty() && route.failureReason != null && route.failureReason.length() > 0) {
            message.failureDetails.add(KOMEConquestTile.normalizeId(destination) + ": " + stripTrailingPeriod(route.failureReason));
        }
        KOMEPacketHandler.network.sendTo(message, player);
    }

    private void sendMovePreviewFailure(EntityPlayerMP player, KOMEArmyCompany company, String destination, String reason) {
        KOMEPacketCompanyMovePreviewResult message = basePreviewFailure(company, destination);
        message.failureSummary = reason == null || reason.length() == 0 ? "No legal route to this tile." : reason;
        KOMEPacketHandler.network.sendTo(message, player);
    }

    private KOMEPacketCompanyMovePreviewResult basePreviewFailure(KOMEArmyCompany company, String destination) {
        KOMEPacketCompanyMovePreviewResult message = new KOMEPacketCompanyMovePreviewResult();
        message.companyId = company.id;
        message.companyName = company.name;
        message.originTileId = company.currentTile;
        message.destinationTileId = KOMEConquestTile.normalizeId(destination);
        message.valid = false;
        message.failureTitle = "Route Blocked";
        message.suggestedAction = "Right-click another tile to choose a different destination. Esc cancels.";
        return message;
    }

    private String routeFailureSummary(RouteResult route, KOMEArmyCompany company) {
        if (route == null) {
            return "No legal route to this tile.";
        }
        if (route.blockers.isEmpty() && route.failureReason != null && route.failureReason.length() > 0) {
            return route.failureReason;
        }
        return "No legal route to this tile. Destination is not reachable through your claimed "
            + displayFaction(company.faction) + " tiles or partner tiles unlocked by canonical Allies passage.";
    }

    private List<RouteBlocker> sortedRouteBlockers(RouteResult route) {
        List<RouteBlocker> blockers = new ArrayList<RouteBlocker>();
        if (route != null) {
            blockers.addAll(route.blockers);
        }
        Collections.sort(blockers, new Comparator<RouteBlocker>() {
            @Override
            public int compare(RouteBlocker first, RouteBlocker second) {
                if (first.priority != second.priority) {
                    return second.priority - first.priority;
                }
                int from = first.fromTile.compareTo(second.fromTile);
                return from != 0 ? from : first.toTile.compareTo(second.toTile);
            }
        });
        return blockers;
    }

    private void moveCompany(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, UUID owner, String companyId, String destination) {
        KOMEArmyCompany company = validateCompanyMove(player, data, owner, companyId, destination);
        refreshCompany(data, company);
        RouteResult route = findLegalRoute(data, company.currentTile, destination, company.faction, company);
        validateStewardshipRoute(data, company, route);
        if (!route.valid) {
            throw new WrongUsageException(route.failureReason);
        }
        SpawnTarget routeArrival = validateRouteArrivalTargets(data, KOMEReflection.getWorld(player), route);
        if (!routeArrival.valid) {
            throw new WrongUsageException(routeArrival.failureReason);
        }
        SpawnTarget arrival = requireArrivalTarget(data, KOMEReflection.getWorld(player), destination);
        if (!arrival.valid) {
            throw new WrongUsageException(arrival.failureReason);
        }
        List<KOMEHiredUnitRecord> selected = new ArrayList<KOMEHiredUnitRecord>();
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            String blocked = movementBlockReasonForCompanyUnit(record, company);
            if (blocked.length() > 0) {
                throw new WrongUsageException("Company " + company.name + " cannot move: " + blocked);
            }
            selected.add(record);
        }
        int distance = route.distance();
        String firstStepDestination = route.routeTiles.size() > 1 ? KOMEConquestTile.normalizeId(route.routeTiles.get(1)) : destination;
        SpawnTarget firstStepArrival = requireArrivalTarget(data, KOMEReflection.getWorld(player), firstStepDestination);
        if (!firstStepArrival.valid) {
            throw new WrongUsageException(firstStepArrival.failureReason);
        }
        KOMEArmyMovementOrder order = new KOMEArmyMovementOrder();
        order.id = nextOrderId(data);
        order.companyId = company.id;
        order.companyName = company.name;
        order.owner = owner;
        order.ownerName = company.ownerName;
        order.ownerFaction = company.faction;
        order.originTile = company.currentTile;
        order.destinationTile = destination;
        order.finalDestinationTile = destination;
        order.currentStepOriginTile = company.currentTile;
        order.currentStepDestinationTile = firstStepDestination;
        order.arrivalPointTileId = firstStepDestination;
        order.arrivalPointSource = firstStepArrival.label;
        order.arrivalDimension = firstStepArrival.dimensionId;
        order.arrivalX = firstStepArrival.x;
        order.arrivalY = firstStepArrival.y;
        order.arrivalZ = firstStepArrival.z;
        order.population = company.totalPopulation;
        order.mountedPopulation = company.mountedPopulation;
        order.groundPopulation = company.groundPopulation;
        order.mountedUnits = countMounted(selected);
        order.groundUnits = selected.size() - order.mountedUnits;
        order.distanceTiles = distance;
        order.tilesPerDay = company.getTilesPerDay();
        order.currentRouteIndex = 0;
        order.nextRouteIndex = 1;
        order.finalRouteIndex = Math.max(1, route.routeTiles.size() - 1);
        order.totalSteps = distance;
        order.currentTile = company.currentTile;
        order.nextTile = firstStepDestination;
        order.completedSteps = 0;
        order.filter = "company";
        order.routeTiles.clear();
        order.routeTiles.addAll(route.routeTiles);
        order.traveledRouteTiles.clear();
        order.traveledRouteTiles.add(company.currentTile);
        order.createdAtMillis = System.currentTimeMillis();
        order.departureMillis = order.createdAtMillis;
        order.totalDepartureMillis = order.departureMillis;
        order.stepDepartureMillis = order.departureMillis;
        order.stepArrivalMillis = order.departureMillis;
        order.arrivalMillis = order.stepArrivalMillis;
        order.lastStepMillis = order.departureMillis;
        order.nextStepAvailableMillis = order.departureMillis;
        order.finalArrivalMillis = order.departureMillis + getRouteCompletionMillis(data, distance, order.tilesPerDay);
        order.nextDailyStepMillis = nextDailyResetMillis(data, order.departureMillis);
        order.movementScheduleMode = movementScheduleMode(data);
        World world = KOMEReflection.getWorld(player);
        List<Entity> entities = new ArrayList<Entity>();
        List<NBTTagCompound> snapshots = new ArrayList<NBTTagCompound>();
        ForgeChunkManager.Ticket originTicket = acquireTemporaryTileChunk(data, world, company.currentTile, order.createdAtMillis);
        try {
            List<String> missingUnits = new ArrayList<String>();
            for (KOMEHiredUnitRecord record : selected) {
                Entity entity = findLoadedEntity(world, record.entity);
                if (!(entity instanceof LOTREntityNPC) || !entity.isEntityAlive()) {
                    entity = reconcileLoadedStationedUnit(data, world, company, record);
                }
                if (!(entity instanceof LOTREntityNPC) || !entity.isEntityAlive()) {
                    if (record.stationedEntityData != null) {
                        setSavedHaltState(record, false);
                        snapshots.add((NBTTagCompound) record.stationedEntityData.copy());
                    } else {
                        missingUnits.add(displayUnitId(record));
                    }
                    continue;
                }
                ((LOTREntityNPC) entity).hiredNPCInfo.ready();
                NBTTagCompound snapshot = snapshotEntity(entity);
                if (snapshot == null) {
                    throw new WrongUsageException("Could not save " + displayUnitId(record) + " for movement.");
                }
                entities.add(entity);
                snapshots.add(snapshot);
            }
            if (!missingUnits.isEmpty()) {
                throw new WrongUsageException("Could not load or restore " + missingUnits.size() + " company unit(s) at origin "
                    + company.currentTile + ": " + joinDebugList(missingUnits)
                    + ". Stand near the company once so KOME can save their stationary data, or set/check the tile Arrival Point with /troops arrival set " + company.currentTile + ".");
            }
            for (int i = 0; i < selected.size(); i++) {
                KOMEHiredUnitRecord record = selected.get(i);
                record.movingEntityData = snapshots.get(i);
                record.movementOrderId = order.id;
                order.units.add(record.entity);
            }
            company.status = KOMEArmyCompany.MOVING;
            company.movementOrderId = order.id;
            company.updatedAtMillis = System.currentTimeMillis();
            data.armyMovements.put(order.id, order);
            data.recordMovementStarted(order);
            data.markDirty();
            for (Entity entity : entities) {
                removeMovementEntityTree(world, entity);
            }
            processArrivals(data, world, order.createdAtMillis, true);
        } finally {
            releaseTemporaryArrivalChunk(originTicket);
        }
        data.syncConquestTiles();
        sender.addChatMessage(new ChatComponentText("Movement order " + order.id + " started for company "
            + company.name + " (" + company.id + ") route " + formatRouteTiles(order.routeTiles)
            + ". First step resolves immediately; current status " + order.status + ", progress "
            + order.completedSteps + "/" + order.distanceTiles + "."));
    }

    private void setArrivalPoint(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, String tileId) {
        KOMEConquestTile tile = data.conquestTiles.get(tileId);
        if (tile == null || !tile.isClaimed()) {
            throw new WrongUsageException("Tile " + tileId + " must be claimed before setting its Arrival Point.");
        }
        String playerFaction = getPlayerFaction(data, player);
        if (!player.canCommandSenderUseCommand(2, getCommandName())
                && !KOMEAlliance.normalizeFactionKey(playerFaction).equals(KOMEAlliance.normalizeFactionKey(tile.currentRulingFaction()))) {
            throw new WrongUsageException("You can only set an Arrival Point for a tile controlled by your faction.");
        }
        tile.setAnchor(player.dimension, player.posX, player.posY, player.posZ);
        data.setTileWaypoint(tileId, KOMETileWaypoint.RALLY, player.dimension, player.posX, player.posY, player.posZ, player.getCommandSenderName(), true);
        data.markDirty();
        data.syncConquestTiles();
        sender.addChatMessage(new ChatComponentText("Arrival Point for " + tileId + " set at dim " + player.dimension + " "
            + MathHelper.floor_double(player.posX) + ", " + MathHelper.floor_double(player.posY) + ", "
            + MathHelper.floor_double(player.posZ) + "."));
    }

    private void listArrivalPoint(ICommandSender sender, KOMEWorldData data, String tileId) {
        if (KOMEConquestTile.isCanonicalTileId(tileId)) {
            data.getConquestTile(tileId);
        }
        KOMETileWaypoint point = getArrivalPoint(data, tileId);
        if (point == null) {
            sender.addChatMessage(new ChatComponentText("Tile " + tileId + " has no Arrival Point. Stand where troops should arrive and run /troops arrival set " + tileId + "."));
            return;
        }
        sender.addChatMessage(new ChatComponentText("Arrival Point for " + tileId + ": dim " + point.dimensionId + " at "
            + formatBlockPos(point.x, point.y, point.z) + " (" + arrivalSource(point) + ")."));
    }

    private void setRecruitmentTile(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, UUID owner, String value) {
        String faction = getPlayerFaction(data, player);
        if ("clear".equalsIgnoreCase(value)) {
            data.clearActiveRecruitmentTile(owner, faction);
            sender.addChatMessage(new ChatComponentText("Active recruitment tile cleared. Hiring will use automatic tile selection."));
            return;
        }
        String tile = parseTile(value);
        if (!data.isFactionControlledTile(tile, faction)) {
            throw new WrongUsageException("Your faction does not control " + tile + ".");
        }
        if (!data.setActiveRecruitmentTile(owner, faction, tile)) {
            throw new WrongUsageException("You need population allocated on " + tile + " or player reserve population before using it as a recruitment origin.");
        }
        sender.addChatMessage(new ChatComponentText("Active recruitment tile set to " + tile + ". New hires will prefer this tile."));
    }

    private void stationTroops(ICommandSender sender, EntityPlayerMP player, KOMEWorldData data, UUID owner, String tile, boolean all) {
        String faction = getPlayerFaction(data, player);
        if (!data.isFactionControlledTile(tile, faction)) {
            throw new WrongUsageException("Your faction does not control " + tile + ".");
        }
        int units = 0;
        int pop = 0;
        int alreadyStationed = 0;
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record == null || record.farmhand || !owner.equals(record.owner)) {
                continue;
            }
            if (record.movementOrderId != null && record.movementOrderId.length() > 0) {
                continue;
            }
            if (record.currentTile != null && record.currentTile.length() > 0) {
                alreadyStationed++;
                continue;
            }
            record.currentTile = tile;
            units++;
            pop += record.cost;
        }
        data.markDirty();
        data.syncConquestTiles();
        sender.addChatMessage(new ChatComponentText("Stationed " + units + " previously unstationed warriors (" + pop + " pop) at " + tile
            + ". " + alreadyStationed + " already-stationed units were left in place; defensive units cannot be relocated."));
    }

    public static void processArrivals(KOMEWorldData data, World world, long nowMillis, boolean announce) {
        boolean changed = false;
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order == null || !order.hasArrived(nowMillis)) {
                continue;
            }
            String stepDestinationTile = activeStepDestination(order);
            if (!isMovementStepAuthorized(data, order)) {
                // A step that has already departed is committed.  Preserve its
                // original arrival target and finish that step, but do not let
                // the order schedule another step afterward.
                changed |= markCommittedStepForAccessLoss(data, order, nowMillis);
                // Do not rewrite stepDestinationTile, currentStepDestinationTile,
                // arrivalPointTileId, or the spawn target.  This is the committed
                // destination of the in-flight step.
            }
            World arrivalWorld = worldForOrder(world, order);
            if (arrivalWorld == null) {
                changed |= markPendingSpawn(order, "ARRIVAL_DIMENSION_UNAVAILABLE",
                    "arrival dimension " + order.arrivalDimension + " is not loaded on the server", nowMillis);
                continue;
            }
            order.status = KOMEArmyMovementOrder.SPAWNING;
            order.lastSpawnAttemptMillis = nowMillis;
            order.spawnAttemptCount++;
            ForgeChunkManager.Ticket arrivalTicket = acquireTemporaryArrivalChunk(arrivalWorld, order, nowMillis);
            try {
                KOMEConquestTile destination = data.conquestTiles.get(stepDestinationTile);
                String entrySide = entrySideFor(data, activeStepOrigin(order), stepDestinationTile);
                List<SpawnTarget> spawnTargets = resolveArrivalTargets(data, arrivalWorld, order, destination, entrySide);
                if (spawnTargets.isEmpty()) {
                    SpawnTarget failedTarget = resolveArrivalTarget(data, arrivalWorld, order, destination, entrySide);
                    changed |= markPendingSpawn(order, failureCode(failedTarget.failureReason), failedTarget.failureReason, nowMillis);
                    continue;
                }
                if (announce) {
                    notifyMovementOwner(arrivalWorld, order, "Trying arrival spawn for " + order.id + " at " + describeTargets(spawnTargets) + ".");
                }
                boolean allSpawned = true;
                String spawnedAt = "";
                int spawnedDimension = arrivalWorld.provider == null ? 0 : arrivalWorld.provider.dimensionId;
                double spawnedX = 0.0D;
                double spawnedY = 0.0D;
                double spawnedZ = 0.0D;
                List<SpawnAttempt> verifiedSpawns = new ArrayList<SpawnAttempt>();
                for (int index = 0; index < order.units.size(); index++) {
                    UUID unitId = order.units.get(index);
                    KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                    if (record == null) {
                        allSpawned = false;
                        changed |= markPendingSpawn(order, "MISSING_UNIT_RECORD", "unit record " + unitId + " is missing", nowMillis);
                        continue;
                    }
                    if (!order.id.equals(record.movementOrderId)) {
                        continue;
                    }
                    if (record.movingEntityData == null) {
                        Entity legacyEntity = findLoadedEntity(arrivalWorld, record.entity);
                        record.movingEntityData = legacyEntity == null ? null : snapshotEntity(legacyEntity);
                        if (legacyEntity != null) {
                            removeMovementEntityTree(arrivalWorld, legacyEntity);
                        }
                    }
                    if (record.movingEntityData == null) {
                        allSpawned = false;
                        changed |= markPendingSpawn(order, "MISSING_MOVING_ENTITY_DATA", "unit " + displayRecordId(record) + " has no saved moving entity data", nowMillis);
                        continue;
                    }
                    SpawnAttempt attempt = respawnMovingUnit(data, arrivalWorld, record, spawnTargets, index);
                    rememberSpawnAttempt(order, attempt);
                    UUID newId = attempt.newId;
                    if (newId == null) {
                        allSpawned = false;
                        changed |= markPendingSpawn(order, failureCode(attempt.failureReason), "could not recreate or safely place unit " + displayRecordId(record)
                            + " near any arrival point for " + stepDestinationTile + ": " + attempt.failureReason, nowMillis);
                        continue;
                    }
                    if (spawnedAt.length() == 0) {
                        spawnedAt = attempt.targetLabel;
                        spawnedDimension = attempt.dimensionId;
                        spawnedX = attempt.spawnX;
                        spawnedY = attempt.spawnY;
                        spawnedZ = attempt.spawnZ;
                    }
                    attempt.orderIndex = index;
                    attempt.record = record;
                    verifiedSpawns.add(attempt);
                }
                if (!allSpawned) {
                    rollbackVerifiedSpawns(data, arrivalWorld, order, verifiedSpawns);
                    changed = true;
                    continue;
                }
                boolean finalStep = isFinalStep(order);
                for (SpawnAttempt attempt : verifiedSpawns) {
                    KOMEHiredUnitRecord record = attempt.record;
                    UUID unitId = attempt.oldId;
                    UUID newId = attempt.newId;
                    order.units.set(attempt.orderIndex, newId);
                    KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
                    if (company != null) {
                        int companyIndex = company.units.indexOf(unitId);
                        if (companyIndex >= 0) {
                            company.units.set(companyIndex, newId);
                        }
                    }
                    record.currentTile = stepDestinationTile;
                    if (finalStep && !order.haltAfterArrival) {
                        record.movementOrderId = "";
                    }
                    record.movingEntityData = null;
                    Entity arrivedEntity = findLoadedEntity(arrivalWorld, newId);
                    record.stationedEntityData = snapshotEntity(arrivedEntity);
                    changed = true;
                }
                order.lastSpawnLabel = spawnedAt;
                order.lastSpawnDimension = spawnedDimension;
                order.lastSpawnX = spawnedX;
                order.lastSpawnY = spawnedY;
                order.lastSpawnZ = spawnedZ;
                order.lastAttemptLabel = spawnedAt;
                order.lastAttemptDimension = spawnedDimension;
                order.lastAttemptX = spawnedX;
                order.lastAttemptY = spawnedY;
                order.lastAttemptZ = spawnedZ;
                order.spawnRetryPaused = false;
                order.nextSpawnRetryMillis = 0L;
                order.lastSpawnFailureCode = "";
                order.lastSpawnFailureDetails = "";
                KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
                order.currentRouteIndex = Math.max(order.currentRouteIndex, order.nextRouteIndex);
                order.completedSteps = Math.max(order.completedSteps, order.currentRouteIndex);
                order.currentTile = stepDestinationTile;
                if (order.traveledRouteTiles.isEmpty() || !stepDestinationTile.equals(order.traveledRouteTiles.get(order.traveledRouteTiles.size() - 1))) {
                    order.traveledRouteTiles.add(stepDestinationTile);
                }
                order.currentStepOriginTile = stepDestinationTile;
                order.currentStepDestinationTile = nextRouteTile(order);
                order.nextTile = order.currentStepDestinationTile;
                order.lastStepMillis = nowMillis;
                if (company != null) {
                    company.currentTile = stepDestinationTile;
                    company.updatedAtMillis = nowMillis;
                    KOMEAllianceProgressionService.scanQualifyingWarDeployments(data, nowMillis);
                }
                if (finalStep) {
                    if (order.haltAfterArrival) {
                        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
                        order.pendingSpawnReason = order.accessLossReason;
                        order.accessChoice = "PENDING";
                    } else {
                        order.markArrived();
                    }
                    order.finalArrivalMillis = nowMillis;
                    order.nextRouteIndex = order.currentRouteIndex;
                    order.nextTile = "";
                    order.nextStepAvailableMillis = 0L;
                    order.nextStepDepartureMillis = 0L;
                    if (company != null) {
                        company.status = KOMEArmyCompany.STATIONED;
                        if (!order.haltAfterArrival) {
                            company.movementOrderId = "";
                        }
                        refreshCompany(data, company);
                    }
                    if (announce) {
                        notifyMovementOwner(arrivalWorld, order, order.haltAfterArrival
                            ? "Movement " + order.id + " halted at " + stepDestinationTile + " after access loss. Choose Stay or Retreat."
                            : "Movement " + order.id + " arrived at " + order.destinationTile + formatOrderSpawnLocation(order) + ".");
                    }
                    data.updateMovementHistory(order, order.haltAfterArrival
                        ? KOMEMovementHistoryRecord.FAILED : KOMEMovementHistoryRecord.ARRIVED);
                    if (company != null && !order.haltAfterArrival && order.retreating) {
                        KOMEWartimeStewardshipService.demobilizeIfSafe(data, company, arrivalWorld, nowMillis);
                    }
                } else {
                    if (order.haltAfterArrival && "PENDING".equals(order.accessChoice)) {
                        order.status = KOMEArmyMovementOrder.ACCESS_HALTED;
                        order.pendingSpawnReason = order.accessLossReason;
                        order.nextStepAvailableMillis = 0L;
                        order.nextStepDepartureMillis = 0L;
                        order.arrivalMillis = 0L;
                        if (company != null) {
                            company.status = KOMEArmyCompany.STATIONED;
                            company.movementOrderId = order.id;
                            refreshCompany(data, company);
                        }
                        if (announce) {
                            notifyMovementOwner(arrivalWorld, order, "Movement " + order.id + " halted at "
                                + stepDestinationTile + " after access loss. Choose Stay or Retreat.");
                        }
                        data.updateMovementHistory(order, KOMEMovementHistoryRecord.FAILED);
                    } else {
                        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
                        order.pendingSpawnReason = "";
                        long cooldown = nextStepCooldownMillis(data, order, nowMillis);
                        order.nextStepAvailableMillis = nowMillis + cooldown;
                        order.nextStepDepartureMillis = order.nextStepAvailableMillis;
                        order.arrivalMillis = order.nextStepAvailableMillis;
                        order.nextDailyStepMillis = data != null && data.movementSecondsPerTileOverride <= 0
                            && data.movementTotalSecondsOverride <= 0 ? order.nextStepAvailableMillis : order.nextDailyStepMillis;
                        if (company != null) {
                            company.status = KOMEArmyCompany.MOVING;
                            company.movementOrderId = order.id;
                        }
                        if (announce) {
                            notifyMovementOwner(arrivalWorld, order, "Movement " + order.id + " reached route step "
                                + stepDestinationTile + " (" + order.completedSteps + "/" + order.distanceTiles + ")"
                                + "; next step available in " + formatDuration(cooldown) + ".");
                        }
                        data.updateMovementHistory(order, KOMEMovementHistoryRecord.ACTIVE);
                    }
                }
                changed = true;
            } finally {
                releaseTemporaryArrivalChunk(arrivalTicket);
            }
        }
        if (changed) {
            for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
                if (order != null && order.isPendingSpawn()) {
                    data.updateMovementHistory(order, KOMEMovementHistoryRecord.FAILED);
                }
            }
            data.markDirty();
            data.syncConquestTiles();
        }
    }

    public static void processMovementTick(KOMEWorldData data, World world, long nowMillis) {
        if (data == null || world == null || data.armyMovements.isEmpty()) {
            return;
        }
        reconcileTemporaryControllers(data, nowMillis);
        processWaitingStepDepartures(data, world, nowMillis);
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            World orderWorld = worldForOrder(world, order);
            if (orderWorld != null) {
                removeStaleMovingEntities(data, orderWorld);
            }
        }
        processArrivals(data, world, nowMillis, false);
    }

    static boolean processWaitingStepDepartures(KOMEWorldData data, World world, long nowMillis) {
        boolean changed = false;
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order == null || !KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)
                    || order.spawnRetryPaused || nowMillis < order.nextStepDepartureMillis) {
                continue;
            }
            if (!isMovementStepAuthorized(data, order)) {
                haltForAccessLoss(data, order, nowMillis, movementAccessReason(data, order));
                changed = true;
                continue;
            }
            if (isDailyMovementMode(data)) {
                order.dailyStepsRemaining = Math.max(order.dailyStepsRemaining, Math.max(0, order.tilesPerDay - 1));
            }
            World orderWorld = worldForOrder(world, order);
            if (orderWorld == null) {
                changed |= markStepDepartureBlocked(order, "DEPARTURE_DIMENSION_UNAVAILABLE",
                    "step departure dimension " + order.arrivalDimension + " is not loaded on the server", nowMillis);
                continue;
            }
            ForgeChunkManager.Ticket ticket = acquireTemporaryArrivalChunk(orderWorld, order, nowMillis);
            try {
                List<Entity> physicalEntities = new ArrayList<Entity>();
                boolean ready = true;
                for (UUID unitId : new ArrayList<UUID>(order.units)) {
                    KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                    if (record == null || !order.id.equals(record.movementOrderId)) {
                        ready = false;
                        changed |= markStepDepartureBlocked(order, "MISSING_UNIT_RECORD",
                            "unit record " + unitId + " is missing before next route step", nowMillis);
                        continue;
                    }
                    Entity entity = findLoadedEntity(orderWorld, record.entity);
                    if (entity != null && !entity.isDead) {
                        NBTTagCompound snapshot = snapshotEntity(entity);
                        if (snapshot == null) {
                            ready = false;
                            changed |= markStepDepartureBlocked(order, "INTERMEDIATE_SNAPSHOT_FAILED",
                                "could not save " + displayRecordId(record) + " before departing " + activeStepOrigin(order), nowMillis);
                            continue;
                        }
                        record.movingEntityData = snapshot;
                        physicalEntities.add(entity);
                    } else if (record.stationedEntityData != null) {
                        record.movingEntityData = (NBTTagCompound) record.stationedEntityData.copy();
                    } else {
                        ready = false;
                        changed |= markStepDepartureBlocked(order, "INTERMEDIATE_ENTITY_NOT_LOADED",
                            "could not load " + displayRecordId(record) + " at intermediate tile " + activeStepOrigin(order)
                                + " before next route step", nowMillis);
                    }
                }
                if (!ready) {
                    continue;
                }
                for (Entity entity : physicalEntities) {
                    removeMovementEntityTree(orderWorld, entity);
                }
                if (scheduleNextRouteStep(data, order, orderWorld, nowMillis)) {
                    KOMEArmyCompany company = data.armyCompanies.get(order.companyId);
                    if (company != null) {
                        company.status = KOMEArmyCompany.MOVING;
                        company.movementOrderId = order.id;
                        company.updatedAtMillis = nowMillis;
                    }
                    data.updateMovementHistory(order, KOMEMovementHistoryRecord.ACTIVE);
                    changed = true;
                }
            } finally {
                releaseTemporaryArrivalChunk(ticket);
            }
        }
        if (changed) {
            for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
                if (order != null && order.isPendingSpawn()) {
                    data.updateMovementHistory(order, KOMEMovementHistoryRecord.FAILED);
                }
            }
            data.markDirty();
            data.syncConquestTiles();
        }
        return changed;
    }

    public static void revalidateTemporaryControllers(KOMEWorldData data, long nowMillis, String reason) {
        reconcileTemporaryControllers(data, nowMillis, reason);
    }

    private static void reconcileTemporaryControllers(KOMEWorldData data, long nowMillis) {
        reconcileTemporaryControllers(data, nowMillis, "Stage 4 temporary command is no longer valid");
    }

    private static void reconcileTemporaryControllers(KOMEWorldData data, long nowMillis, String reason) {
        KOMEWarService.reconcileAutomaticMilitarySupport(data, nowMillis, reason);
        KOMEWartimeStewardshipService.revalidateAll(data, nowMillis, reason);
        for (KOMEArmyCompany company : data.armyCompanies.values()) {
            if (company == null || company.temporaryController == null) {
                continue;
            }
            if (KOMEArmyCompany.AUTHORITY_NATIVE_RECLAIM.equals(company.controllerAuthority)) {
                continue;
            }
            if (KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)) {
                KOMECompanyDiplomacyAuthorization.Decision continuation =
                    KOMECompanyDiplomacyAuthorization.canContinueDelegation(
                        data, KOMEWartimeStewardshipService.nativeFaction(company), company.temporaryController);
                if (continuation.allowed) {
                    continue;
                }
                String revocation = continuation.reason;
                if (revocation.length() == 0) {
                    revocation = reason == null || reason.length() == 0
                        ? "Canonical company delegation is no longer valid"
                        : reason;
                }
                data.recordCompanyDelegationAudit(
                    nowMillis, "REVOKED_AUTOMATIC", company,
                    null, "",
                    company.temporaryController, company.temporaryControllerName,
                    revocation);
                company.clearTemporaryController(revocation);
                data.markDirty();
                continue;            }
            String controllerFaction = KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(company.temporaryController));
            if (controllerFaction.length() > 0
                    && new KOMEAllianceAuthority(data).canControlTemporaryCompany(company, company.temporaryController).allowed) {
                if (KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) KOMEWartimeStewardshipService.authorizeCompany(data, company, controllerFaction,
                    "Active same-side war, pledged supporting king, and Stage 4 revalidated", nowMillis);
                continue;
            }
            String revocation = reason == null || reason.length() == 0
                ? "Stage 4 temporary command is no longer valid" : reason;
            if (KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
                KOMEWartimeStewardshipService.revalidateCompany(data, company, nowMillis, revocation);
            } else {
                company.clearTemporaryController(revocation);
            }
            KOMEArmyMovementOrder order = data.armyMovements.get(company.movementOrderId);
            if (order != null && KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)) {
                haltForAccessLoss(data, order, nowMillis, company.delegationRevocationReason);
            } else if (order != null && order.isMoving()) {
                order.haltAfterArrival = true;
                order.accessLossReason = company.delegationRevocationReason;
                order.accessLostAtMillis = nowMillis;
            }
            data.markDirty();
        }
    }

    private static boolean isMovementStepAuthorized(KOMEWorldData data, KOMEArmyMovementOrder order) {
        return KOMEMovementAccessService.isMovementStepAuthorized(data, order);
    }

    private static boolean isMovementStepAuthorized(KOMEWorldData data, KOMEArmyMovementOrder order,
            String origin, String destination, boolean retreat) {
        return KOMEMovementAccessService.isMovementStepAuthorized(data, order, origin, destination, retreat);
    }

    private static boolean isTileStandableForOrder(KOMEWorldData data, KOMEArmyMovementOrder order, String tileId, boolean retreat) {
        return KOMEMovementAccessService.isTileStandableForOrder(data, order, tileId, retreat);
    }

    private static String movementAccessReason(KOMEWorldData data, KOMEArmyMovementOrder order) {
        return KOMEMovementAccessService.movementAccessReason(data, order);
    }

    private static String movementAccessReason(KOMEWorldData data, KOMEArmyMovementOrder order, String destination) {
        return KOMEMovementAccessService.movementAccessReason(data, order, destination);
    }

    static boolean markCommittedStepForAccessLoss(KOMEWorldData data, KOMEArmyMovementOrder order, long nowMillis) {
        return KOMEMovementAccessService.markCommittedStepForAccessLoss(data, order, nowMillis);
    }

    static void haltForAccessLoss(KOMEWorldData data, KOMEArmyMovementOrder order, long nowMillis, String reason) {
        KOMEMovementAccessService.haltForAccessLoss(data, order, nowMillis, reason);
    }

    private static boolean scheduleNextRouteStep(KOMEWorldData data, KOMEArmyMovementOrder order, World world, long nowMillis) {
        if (order == null || order.routeTiles.size() < 2) {
            return false;
        }
        int finalIndex = order.routeTiles.size() - 1;
        int originIndex = Math.max(0, Math.min(order.currentRouteIndex, finalIndex));
        if (originIndex >= finalIndex) {
            return false;
        }
        int nextIndex = originIndex + 1;
        String origin = KOMEConquestTile.normalizeId(order.routeTiles.get(originIndex));
        String destination = KOMEConquestTile.normalizeId(order.routeTiles.get(nextIndex));
        if (!isTileStandableForOrder(data, order, destination, order.retreating)) {
            haltForAccessLoss(data, order, nowMillis, "No current passage into " + destination + ".");
            return false;
        }
        SpawnTarget target = requireArrivalTarget(data, world, destination);
        if (!target.valid) {
            markPendingSpawn(order, failureCode(target.failureReason), target.failureReason, nowMillis);
            return false;
        }
        order.currentStepOriginTile = origin;
        order.currentStepDestinationTile = destination;
        order.currentTile = origin;
        order.nextTile = destination;
        order.nextRouteIndex = nextIndex;
        order.arrivalPointTileId = destination;
        order.arrivalPointSource = target.label;
        order.arrivalDimension = target.dimensionId;
        order.arrivalX = target.x;
        order.arrivalY = target.y;
        order.arrivalZ = target.z;
        order.stepDepartureMillis = nowMillis;
        order.stepArrivalMillis = nowMillis;
        order.arrivalMillis = order.stepArrivalMillis;
        order.status = KOMEArmyMovementOrder.MOVING;
        order.pendingSpawnReason = "";
        order.spawnRetryPaused = false;
        order.nextSpawnRetryMillis = 0L;
        order.spawnAttemptCount = 0;
        order.lastSpawnFailureCode = "";
        order.lastSpawnFailureDetails = "";
        return true;
    }

    private static String activeStepOrigin(KOMEArmyMovementOrder order) {
        if (order == null) {
            return "";
        }
        String current = KOMEConquestTile.normalizeId(order.currentTile);
        if (current.length() > 0) {
            return current;
        }
        String value = KOMEConquestTile.normalizeId(order.currentStepOriginTile);
        if (value.length() > 0) {
            return value;
        }
        return order.routeTiles.size() > order.currentRouteIndex
            ? KOMEConquestTile.normalizeId(order.routeTiles.get(order.currentRouteIndex))
            : KOMEConquestTile.normalizeId(order.originTile);
    }

    private static String activeStepDestination(KOMEArmyMovementOrder order) {
        if (order == null) {
            return "";
        }
        String next = KOMEConquestTile.normalizeId(order.nextTile);
        if (next.length() > 0) {
            return next;
        }
        String value = KOMEConquestTile.normalizeId(order.currentStepDestinationTile);
        if (value.length() > 0) {
            return value;
        }
        return order.routeTiles.size() > order.nextRouteIndex
            ? KOMEConquestTile.normalizeId(order.routeTiles.get(order.nextRouteIndex))
            : KOMEConquestTile.normalizeId(order.destinationTile);
    }

    private static String nextRouteTile(KOMEArmyMovementOrder order) {
        if (order == null || order.routeTiles.isEmpty()) {
            return "";
        }
        int nextIndex = Math.max(0, order.currentRouteIndex + 1);
        return order.routeTiles.size() > nextIndex ? KOMEConquestTile.normalizeId(order.routeTiles.get(nextIndex)) : "";
    }

    private static boolean isFinalStep(KOMEArmyMovementOrder order) {
        if (order == null) {
            return true;
        }
        int finalIndex = order.routeTiles.size() >= 2 ? order.routeTiles.size() - 1 : Math.max(1, order.distanceTiles);
        return order.nextRouteIndex >= finalIndex || activeStepDestination(order).equals(KOMEConquestTile.normalizeId(order.destinationTile));
    }

    private static SpawnTarget resolveArrivalTarget(KOMEWorldData data, World world, KOMEArmyMovementOrder order, KOMEConquestTile destination, String entrySide) {
        SpawnTarget result = new SpawnTarget();
        if (destination == null) {
            result.failureReason = "destination tile " + (order == null ? "" : activeStepDestination(order)) + " was not found in conquest data";
            return result;
        }
        SpawnTarget stored = targetFromOrderArrival(world, order);
        if (stored.valid) {
            return stored;
        }
        if (data != null) {
            data.ensureRallyWaypointFromLegacyAnchor(destination);
        }
        SpawnTarget rally = targetFromWaypointOrAutomatic(data, world, destination, KOMETileWaypoint.RALLY);
        if (rally.valid) {
            return rally;
        }
        result.failureReason = "no safe stored Arrival Point or Rally Point spawn for "
            + destination.id + " (stored: " + stored.failureReason + "; rally: " + rally.failureReason + ")";
        return result;
    }

    private static List<SpawnTarget> resolveArrivalTargets(KOMEWorldData data, World world, KOMEArmyMovementOrder order, KOMEConquestTile destination, String entrySide) {
        List<SpawnTarget> targets = new ArrayList<SpawnTarget>();
        if (destination == null) {
            return targets;
        }
        addValidTarget(targets, targetFromOrderArrival(world, order));
        if (data != null) {
            data.ensureRallyWaypointFromLegacyAnchor(destination);
        }
        addValidTarget(targets, targetFromWaypointOrAutomatic(data, world, destination, KOMETileWaypoint.RALLY));
        return targets;
    }

    private static SpawnTarget targetFromOrderArrival(World world, KOMEArmyMovementOrder order) {
        SpawnTarget result = new SpawnTarget();
        if (order == null || !hasStoredArrivalPoint(order)) {
            result.failureReason = "movement order has no stored Arrival Point";
            return result;
        }
        return validateSpawnTarget(world, order.arrivalDimension, order.arrivalX, order.arrivalY, order.arrivalZ,
            "stored Arrival Point for " + activeStepDestination(order) + sourceSuffix(order.arrivalPointSource));
    }

    private static boolean hasStoredArrivalPoint(KOMEArmyMovementOrder order) {
        return order != null && (Math.abs(order.arrivalX) > 0.001D
            || Math.abs(order.arrivalY) > 0.001D
            || Math.abs(order.arrivalZ) > 0.001D);
    }

    private static SpawnTarget validateRouteArrivalTargets(KOMEWorldData data, World world, RouteResult route) {
        SpawnTarget result = new SpawnTarget();
        result.valid = true;
        if (route == null || route.routeTiles.size() < 2) {
            result.valid = false;
            result.failureReason = "Movement route has no tile steps.";
            return result;
        }
        for (int i = 1; i < route.routeTiles.size(); i++) {
            String tile = KOMEConquestTile.normalizeId(route.routeTiles.get(i));
            SpawnTarget target = requireArrivalTarget(data, world, tile);
            if (!target.valid) {
                target.failureReason = "Route step " + i + " destination " + tile + " cannot receive troops: "
                    + target.failureReason;
                return target;
            }
        }
        return result;
    }

    private static SpawnTarget requireArrivalTarget(KOMEWorldData data, World world, String tileId) {
        SpawnTarget result = new SpawnTarget();
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        KOMEConquestTile tile = data == null ? null : data.getConquestTile(normalizedTile);
        if (tile == null) {
            result.failureReason = "Destination tile " + normalizedTile + " was not found in conquest data.";
            return result;
        }
        if (data != null) {
            data.ensureRallyWaypointFromLegacyAnchor(tile);
        }
        KOMETileWaypoint point = getArrivalPoint(data, tile.id);
        if (point == null) {
            result.failureReason = "Destination tile " + tile.id + " has no Arrival Point. Claiming or accepting a tile should create one automatically; otherwise stand where troops should arrive and run /troops arrival set " + tile.id + ".";
            return result;
        }
        return validateSpawnTarget(world, point.dimensionId, point.x, point.y, point.z, "Arrival Point for " + tile.id + sourceSuffix(arrivalSource(point)));
    }

    private static KOMETileWaypoint getArrivalPoint(KOMEWorldData data, String tileId) {
        return data == null ? null : data.getTileWaypoint(tileId, KOMETileWaypoint.RALLY);
    }

    private static String arrivalSource(KOMETileWaypoint point) {
        if (point == null) {
            return "";
        }
        if (point.manualOverride) {
            return "Manual";
        }
        if ("Legacy anchor".equalsIgnoreCase(point.createdBy)) {
            return "Legacy Anchor";
        }
        if (point.createdBy != null && point.createdBy.toLowerCase().startsWith("auto lotr waypoint")) {
            return "LOTR Waypoint";
        }
        return "Auto Claim";
    }

    private static String sourceSuffix(String source) {
        return source == null || source.length() == 0 ? "" : " (" + source + ")";
    }

    private static void addValidTarget(List<SpawnTarget> targets, SpawnTarget target) {
        if (target == null || !target.valid) {
            return;
        }
        for (SpawnTarget existing : targets) {
            if (existing.dimensionId == target.dimensionId
                    && MathHelper.floor_double(existing.x) == MathHelper.floor_double(target.x)
                    && MathHelper.floor_double(existing.z) == MathHelper.floor_double(target.z)) {
                return;
            }
        }
        targets.add(target);
    }

    private static SpawnTarget targetFromWaypointOrAutomatic(KOMEWorldData data, World world, KOMEConquestTile destination, String type) {
        KOMETileWaypoint waypoint = data.getTileWaypoint(destination.id, type);
        if (waypoint != null) {
            return validateSpawnTarget(world, waypoint.dimensionId, waypoint.x, waypoint.y, waypoint.z,
                KOMETileWaypoint.displayType(type) + (waypoint.manualOverride ? " manual waypoint" : " waypoint"));
        }
        SpawnTarget result = new SpawnTarget();
        result.failureReason = destination.id + " has no saved " + KOMETileWaypoint.displayType(type)
            + ". Set the tile Arrival Point with /troops arrival set " + destination.id + ".";
        return result;
    }

    private static SpawnTarget validateSpawnTarget(World world, int dimensionId, double x, double y, double z, String label) {
        SpawnTarget result = new SpawnTarget();
        result.dimensionId = dimensionId;
        result.x = x;
        result.y = y;
        result.z = z;
        result.label = label == null ? "spawn point" : label;
        if (world == null) {
            result.failureReason = "server world is unavailable";
            return result;
        }
        if (dimensionId != world.provider.dimensionId) {
            result.failureReason = result.label + " is in dimension " + dimensionId
                + " but processor is running dimension " + world.provider.dimensionId;
            return result;
        }
        int blockX = MathHelper.floor_double(x);
        int blockZ = MathHelper.floor_double(z);
        result.chunkX = blockX >> 4;
        result.chunkZ = blockZ >> 4;
        result.chunkLoadedBeforeAttempt = isChunkLoaded(world, blockX, blockZ);
        result.chunkLoadedForSpawn = ensureChunkLoaded(world, blockX, blockZ);
        if (!result.chunkLoadedForSpawn) {
            result.failureReason = result.label + " destination chunk could not be loaded (" + result.chunkX + ", " + result.chunkZ + ")";
            return result;
        }
        if (!world.blockExists(blockX, 64, blockZ)) {
            result.failureReason = result.label + " destination chunk is not available after load (" + result.chunkX + ", " + result.chunkZ + ")";
            return result;
        }
        result.valid = true;
        return result;
    }

    private static ForgeChunkManager.Ticket acquireTemporaryArrivalChunk(World world, KOMEArmyMovementOrder order, long nowMillis) {
        if (!(world instanceof WorldServer) || order == null || world.provider == null || world.provider.dimensionId != order.arrivalDimension) {
            return null;
        }
        int chunkX = MathHelper.floor_double(order.arrivalX) >> 4;
        int chunkZ = MathHelper.floor_double(order.arrivalZ) >> 4;
        order.lastChunkLoadDimension = order.arrivalDimension;
        order.lastChunkLoadChunkX = chunkX;
        order.lastChunkLoadChunkZ = chunkZ;
        order.lastChunkLoadAttemptMillis = nowMillis;
        order.lastChunkLoadTicketAcquired = false;
        ForgeChunkManager.Ticket ticket = null;
        try {
            ticket = ForgeChunkManager.requestTicket(KOMEAddon.instance, world, ForgeChunkManager.Type.NORMAL);
            if (ticket == null) {
                return null;
            }
            ChunkCoordIntPair chunk = new ChunkCoordIntPair(chunkX, chunkZ);
            ForgeChunkManager.forceChunk(ticket, chunk);
            ensureChunkLoaded(world, MathHelper.floor_double(order.arrivalX), MathHelper.floor_double(order.arrivalZ));
            order.lastChunkLoadTicketAcquired = true;
            return ticket;
        } catch (Throwable ignored) {
            if (ticket != null) {
                try {
                    ForgeChunkManager.releaseTicket(ticket);
                } catch (Throwable ignoredRelease) {
                }
            }
            return null;
        }
    }

    private static World worldForOrder(World fallback, KOMEArmyMovementOrder order) {
        if (order == null) {
            return fallback;
        }
        if (fallback != null && fallback.provider != null && fallback.provider.dimensionId == order.arrivalDimension) {
            return fallback;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return fallback;
        }
        try {
            return server.worldServerForDimension(order.arrivalDimension);
        } catch (Throwable ignored) {
            return fallback != null && fallback.provider != null && fallback.provider.dimensionId == order.arrivalDimension ? fallback : null;
        }
    }

    private static ForgeChunkManager.Ticket acquireTemporaryTileChunk(KOMEWorldData data, World world, String tileId, long nowMillis) {
        if (!(world instanceof WorldServer) || data == null) {
            return null;
        }
        KOMEConquestTile tile = data.conquestTiles.get(KOMEConquestTile.normalizeId(tileId));
        if (tile == null) {
            return null;
        }
        KOMETileWaypoint point = getArrivalPoint(data, tile.id);
        int dimension = point != null ? point.dimensionId : tile.anchorDimension;
        double x = point != null ? point.x : tile.anchorX;
        double z = point != null ? point.z : tile.anchorZ;
        if (point == null && !tile.hasAnchor) {
            return null;
        }
        KOMEArmyMovementOrder temp = new KOMEArmyMovementOrder();
        temp.arrivalDimension = dimension;
        temp.arrivalX = x;
        temp.arrivalZ = z;
        return acquireTemporaryArrivalChunk(world, temp, nowMillis);
    }

    private static void releaseTemporaryArrivalChunk(ForgeChunkManager.Ticket ticket) {
        if (ticket == null) {
            return;
        }
        try {
            ForgeChunkManager.releaseTicket(ticket);
        } catch (Throwable ignored) {
        }
    }

    private static boolean isChunkLoaded(World world, int x, int z) {
        if (world == null) {
            return false;
        }
        IChunkProvider provider = world.getChunkProvider();
        return provider != null && provider.chunkExists(x >> 4, z >> 4);
    }

    private static boolean ensureChunkLoaded(World world, int x, int z) {
        if (world == null) {
            return false;
        }
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        try {
            IChunkProvider provider = world.getChunkProvider();
            if (provider != null && !provider.chunkExists(chunkX, chunkZ)) {
                Chunk chunk = provider.provideChunk(chunkX, chunkZ);
                if (chunk == null) {
                    return false;
                }
            }
            world.getChunkFromChunkCoords(chunkX, chunkZ);
        } catch (Throwable ignored) {
            return false;
        }
        return world.blockExists(x, 64, z);
    }

    private static boolean markPendingSpawn(KOMEArmyMovementOrder order, String code, String reason, long nowMillis) {
        String cleanCode = code == null || code.length() == 0 ? "SPAWN_FAILED" : code;
        String cleanReason = reason == null ? "" : reason;
        boolean repeated = cleanCode.equals(order.lastSpawnFailureCode);
        order.lastSpawnFailureCode = cleanCode;
        order.lastSpawnFailureDetails = cleanReason;
        order.markPendingSpawn(cleanCode + ": " + cleanReason);
        if (repeated && order.spawnAttemptCount >= 3) {
            order.status = KOMEArmyMovementOrder.SPAWN_BLOCKED;
            order.spawnRetryPaused = true;
            order.pendingSpawnReason = cleanCode + ": " + cleanReason
                + " Automatic retries paused after " + order.spawnAttemptCount + " attempts. Use /troops movement retry " + order.id + ".";
        } else {
            order.spawnRetryPaused = false;
            order.nextSpawnRetryMillis = nowMillis + retryDelayMillis(order.spawnAttemptCount);
        }
        return true;
    }

    private static boolean markStepDepartureBlocked(KOMEArmyMovementOrder order, String code, String reason, long nowMillis) {
        String cleanCode = code == null || code.length() == 0 ? "STEP_DEPARTURE_FAILED" : code;
        String cleanReason = reason == null ? "" : reason;
        boolean repeated = cleanCode.equals(order.lastSpawnFailureCode);
        order.status = KOMEArmyMovementOrder.WAITING_NEXT_STEP;
        order.pendingSpawnReason = cleanCode + ": " + cleanReason;
        order.lastSpawnFailureCode = cleanCode;
        order.lastSpawnFailureDetails = cleanReason;
        order.spawnAttemptCount++;
        if (repeated && order.spawnAttemptCount >= 3) {
            order.spawnRetryPaused = true;
            order.pendingSpawnReason = cleanCode + ": " + cleanReason
                + " Automatic next-step departure retries paused after " + order.spawnAttemptCount
                + " attempts. Use /troops movement resume " + order.id + ".";
        } else {
            order.spawnRetryPaused = false;
            order.nextStepDepartureMillis = nowMillis + retryDelayMillis(order.spawnAttemptCount);
        }
        return true;
    }

    private static long retryDelayMillis(int attemptCount) {
        return attemptCount <= 1 ? 15000L : 30000L;
    }

    private static String failureCode(String reason) {
        String value = reason == null ? "" : reason.toLowerCase();
        if (value.contains("chunk could not be loaded")) {
            return "DESTINATION_CHUNK_NOT_LOADED";
        }
        if (value.contains("chunk is not available")) {
            return "DESTINATION_CHUNK_UNAVAILABLE";
        }
        if (value.contains("immediately marked dead")) {
            return "ENTITY_SPAWNED_THEN_DEAD";
        }
        if (value.contains("spawn was rejected")) {
            return "SPAWN_REJECTED_BY_WORLD";
        }
        if (value.contains("no safe")) {
            return "UNSAFE_SPAWN_POSITION";
        }
        if (value.contains("did not recreate")) {
            return "ENTITY_RECREATE_FAILED";
        }
        if (value.contains("uuid lookup failed")) {
            return "UUID_LOOKUP_FAILED";
        }
        return "SPAWN_FAILED";
    }

    private static void rollbackVerifiedSpawns(KOMEWorldData data, World world, KOMEArmyMovementOrder order, List<SpawnAttempt> attempts) {
        if (data == null || attempts == null) {
            return;
        }
        for (SpawnAttempt attempt : attempts) {
            if (attempt == null || attempt.record == null || attempt.newId == null || attempt.oldId == null) {
                continue;
            }
            Entity spawned = findLoadedEntity(world, attempt.newId);
            if (spawned != null) {
                removeEntityTree(world, spawned);
            }
            data.hiredUnits.remove(attempt.newId);
            attempt.record.entity = attempt.oldId;
            data.hiredUnits.put(attempt.oldId, attempt.record);
            if (order != null && attempt.orderIndex >= 0 && attempt.orderIndex < order.units.size()) {
                order.units.set(attempt.orderIndex, attempt.oldId);
            }
        }
    }

    private static SpawnAttempt respawnMovingUnit(KOMEWorldData data, World world, KOMEHiredUnitRecord record, List<SpawnTarget> targets, int index) {
        SpawnAttempt attempt = new SpawnAttempt();
        if (record == null || record.movingEntityData == null) {
            attempt.failureReason = "missing saved entity data";
            return attempt;
        }
        if (targets == null || targets.isEmpty()) {
            attempt.failureReason = "no valid arrival targets";
            return attempt;
        }
        StringBuilder failures = new StringBuilder();
        for (SpawnTarget target : targets) {
            NBTTagCompound snapshot = (NBTTagCompound) record.movingEntityData.copy();
            clearEntityUuids(snapshot);
            sanitizeMovingEntitySnapshot(snapshot);
            Entity root = createEntityTree(snapshot, world);
            if (!(root instanceof LOTREntityNPC)) {
                attempt.failureReason = "saved entity data did not recreate a LOTR NPC";
                return attempt;
            }
            prepareMovementRespawnEntity(root);
            double[] safePosition = findSafeSpawn(world, root, target, index);
            if (safePosition == null) {
                if (failures.length() > 0) {
                    failures.append("; ");
                }
                failures.append(describeTarget(target)).append(" had no safe nearby space");
                continue;
            }
            attempt.attemptX = safePosition[0];
            attempt.attemptY = safePosition[1];
            attempt.attemptZ = safePosition[2];
            attempt.dimensionId = world.provider.dimensionId;
            attempt.targetLabel = target.label + " at " + formatBlockPos(safePosition[0], safePosition[1], safePosition[2]);
            positionEntityTree(root, safePosition[0], safePosition[1], safePosition[2]);
            UUID oldId = record.entity;
            UUID newId = KOMEReflection.getEntityUUID(root);
            data.hiredUnits.remove(oldId);
            record.entity = newId;
            data.hiredUnits.put(newId, record);
            if (spawnEntityTree(world, root)) {
                String verificationFailure = verifySpawnedEntity(data, world, record, root, newId, safePosition);
                if (verificationFailure.length() > 0) {
                    removeEntityTree(world, root);
                    data.hiredUnits.remove(newId);
                    record.entity = oldId;
                    data.hiredUnits.put(oldId, record);
                    if (failures.length() > 0) {
                        failures.append("; ");
                    }
                    failures.append(describeTarget(target)).append(" selected ")
                        .append(formatBlockPos(safePosition[0], safePosition[1], safePosition[2]))
                        .append(" but physical verification failed: ").append(verificationFailure);
                    continue;
                }
                attempt.oldId = oldId;
                attempt.newId = newId;
                attempt.spawnX = root.posX;
                attempt.spawnY = root.posY;
                attempt.spawnZ = root.posZ;
                ((LOTREntityNPC) root).hiredNPCInfo.halt();
                return attempt;
            }
            data.hiredUnits.remove(newId);
            record.entity = oldId;
            data.hiredUnits.put(oldId, record);
            if (failures.length() > 0) {
                failures.append("; ");
            }
            failures.append(describeTarget(target)).append(" selected ")
                .append(formatBlockPos(safePosition[0], safePosition[1], safePosition[2]))
                .append(" but spawn was rejected by the world");
        }
        attempt.failureReason = failures.length() == 0 ? "no arrival target accepted the unit" : failures.toString();
        return attempt;
    }

    private static String verifySpawnedEntity(KOMEWorldData data, World world, KOMEHiredUnitRecord record, Entity spawned, UUID expectedId, double[] expectedPosition) {
        if (world == null || record == null || spawned == null || expectedId == null) {
            return "missing world, record, entity, or UUID";
        }
        if (!expectedId.equals(record.entity)) {
            return "hired-unit record points to " + record.entity + " instead of spawned UUID " + expectedId;
        }
        if (!data.hiredUnits.containsKey(expectedId) || data.hiredUnits.get(expectedId) != record) {
            return "hired-unit map does not contain the spawned UUID";
        }
        Entity loaded = findLoadedEntity(world, expectedId);
        if (loaded == null) {
            return "spawnEntityInWorld returned true but UUID lookup failed";
        }
        if (loaded != spawned) {
            return "loaded UUID points to a different entity instance";
        }
        if (loaded.isDead) {
            return "entity spawned but is immediately marked dead";
        }
        int expectedDimension = world.provider == null ? loaded.dimension : world.provider.dimensionId;
        if (loaded.dimension != expectedDimension) {
            return "entity spawned in dimension " + loaded.dimension + " but expected " + expectedDimension;
        }
        if (expectedPosition != null && expectedPosition.length >= 3) {
            double dx = loaded.posX - expectedPosition[0];
            double dy = loaded.posY - expectedPosition[1];
            double dz = loaded.posZ - expectedPosition[2];
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq > 64.0D) {
                return "entity spawned at " + formatBlockPos(loaded.posX, loaded.posY, loaded.posZ)
                    + " too far from expected " + formatBlockPos(expectedPosition[0], expectedPosition[1], expectedPosition[2]);
            }
        }
        boolean inLoadedList = false;
        for (Object object : world.loadedEntityList) {
            if (object == loaded) {
                inLoadedList = true;
                break;
            }
        }
        if (!inLoadedList) {
            return "entity UUID lookup succeeded but entity is not in loadedEntityList";
        }
        return "";
    }

    private static void notifyMovementOwner(World world, KOMEArmyMovementOrder order, String message) {
        if (world == null || order == null || order.owner == null || message == null || message.length() == 0) {
            return;
        }
        EntityPlayer player = world.func_152378_a(order.owner);
        if (player != null) {
            player.addChatMessage(new ChatComponentText(message));
        }
    }

    private static String describeTargets(List<SpawnTarget> targets) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < targets.size(); i++) {
            if (i > 0) {
                text.append("; ");
            }
            text.append(describeTarget(targets.get(i)));
        }
        return text.toString();
    }

    private static String describeTarget(SpawnTarget target) {
        if (target == null) {
            return "unknown spawn point";
        }
        return target.label + " dim " + target.dimensionId + " at "
            + MathHelper.floor_double(target.x) + ", " + MathHelper.floor_double(target.y) + ", "
            + MathHelper.floor_double(target.z);
    }

    private static String formatBlockPos(double x, double y, double z) {
        return MathHelper.floor_double(x) + ", " + MathHelper.floor_double(y) + ", " + MathHelper.floor_double(z);
    }

    private static String formatOrderSpawnLocation(KOMEArmyMovementOrder order) {
        if (order == null || order.lastSpawnLabel == null || order.lastSpawnLabel.length() == 0) {
            return "";
        }
        return " near " + order.lastSpawnLabel + " (dim " + order.lastSpawnDimension + " at "
            + formatBlockPos(order.lastSpawnX, order.lastSpawnY, order.lastSpawnZ) + ")";
    }

    private static String formatOrderAttemptLocation(KOMEArmyMovementOrder order) {
        if (order == null || order.lastAttemptLabel == null || order.lastAttemptLabel.length() == 0) {
            return "none recorded";
        }
        return order.lastAttemptLabel + " (dim " + order.lastAttemptDimension + " at "
            + formatBlockPos(order.lastAttemptX, order.lastAttemptY, order.lastAttemptZ) + ")";
    }

    private static void rememberSpawnAttempt(KOMEArmyMovementOrder order, SpawnAttempt attempt) {
        if (order == null || attempt == null || attempt.targetLabel == null || attempt.targetLabel.length() == 0) {
            return;
        }
        order.lastAttemptLabel = attempt.targetLabel;
        order.lastAttemptDimension = attempt.dimensionId;
        order.lastAttemptX = attempt.attemptX;
        order.lastAttemptY = attempt.attemptY;
        order.lastAttemptZ = attempt.attemptZ;
    }

    private static String displayRecordId(KOMEHiredUnitRecord record) {
        if (record == null || record.entity == null) {
            return "unknown";
        }
        return record.entity.toString().substring(0, 8);
    }

    private static NBTTagCompound snapshotEntity(Entity entity) {
        if (entity == null || entity.isDead) {
            return null;
        }
        NBTTagCompound snapshot = new NBTTagCompound();
        return entity.writeMountToNBT(snapshot) ? snapshot : null;
    }

    private static void clearEntityUuids(NBTTagCompound nbt) {
        if (nbt == null) {
            return;
        }
        nbt.removeTag("UUIDMost");
        nbt.removeTag("UUIDLeast");
        nbt.removeTag("PersistentIDMSB");
        nbt.removeTag("PersistentIDLSB");
        if (nbt.hasKey("Riding", 10)) {
            clearEntityUuids(nbt.getCompoundTag("Riding"));
        }
    }

    private static void sanitizeMovingEntitySnapshot(NBTTagCompound nbt) {
        if (nbt == null) {
            return;
        }
        nbt.removeTag("Dead");
        nbt.removeTag("DeathTime");
        nbt.removeTag("HurtTime");
        nbt.removeTag("FallDistance");
        nbt.setShort("Fire", (short) 0);
        if (nbt.hasKey("Health")) {
            float health = nbt.getFloat("Health");
            if (health <= 0.0F) {
                nbt.setFloat("Health", 1.0F);
            }
        }
        if (nbt.hasKey("HealF")) {
            float health = nbt.getFloat("HealF");
            if (health <= 0.0F) {
                nbt.setFloat("HealF", 1.0F);
            }
        }
        if (nbt.hasKey("Riding", 10)) {
            sanitizeMovingEntitySnapshot(nbt.getCompoundTag("Riding"));
        }
    }

    private static Entity createEntityTree(NBTTagCompound nbt, World world) {
        Entity entity = EntityList.createEntityFromNBT(nbt, world);
        if (entity != null && nbt.hasKey("Riding", 10)) {
            Entity mount = createEntityTree(nbt.getCompoundTag("Riding"), world);
            if (mount != null) {
                entity.mountEntity(mount);
            }
        }
        return entity;
    }

    private static boolean spawnEntityTree(World world, Entity entity) {
        Entity mount = KOMEReflection.getRidingEntity(entity);
        if (mount != null) {
            entity.mountEntity(null);
            if (!spawnEntityTree(world, mount)) {
                return false;
            }
            prepareMovementRespawnEntity(entity);
            if (world.spawnEntityInWorld(entity)) {
                entity.mountEntity(mount);
                return true;
            }
            removeEntityTree(world, mount);
            return false;
        }
        prepareMovementRespawnEntity(entity);
        return world.spawnEntityInWorld(entity);
    }

    private static void prepareMovementRespawnEntity(Entity entity) {
        if (entity == null) {
            return;
        }
        entity.isDead = false;
        entity.forceSpawn = true;
        entity.fallDistance = 0.0F;
        if (entity instanceof EntityLivingBase) {
            EntityLivingBase living = (EntityLivingBase) entity;
            if (living.getHealth() <= 0.0F) {
                living.setHealth(Math.max(1.0F, living.getMaxHealth()));
            }
            living.deathTime = 0;
            living.hurtTime = 0;
        }
        prepareMovementRespawnEntity(KOMEReflection.getRidingEntity(entity));
    }

    private static double[] findSafeSpawn(World world, Entity entity, SpawnTarget target, int unitIndex) {
        for (int attempt = 0; attempt < 48; attempt++) {
            double angle = (unitIndex + attempt) * 2.399963229728653D;
            double radius = 1.5D + (attempt / 4) * 2.0D;
            int x = MathHelper.floor_double(target.x + Math.cos(angle) * radius);
            int z = MathHelper.floor_double(target.z + Math.sin(angle) * radius);
            if (!ensureChunkLoaded(world, x, z) || !world.blockExists(x, 64, z)) {
                continue;
            }
            int y = Math.max(1, world.getTopSolidOrLiquidBlock(x, z));
            for (int yOffset = -1; yOffset <= 4; yOffset++) {
                int spawnY = y + yOffset;
                if (spawnY < 1) {
                    continue;
                }
                entity.setLocationAndAngles(x + 0.5D, spawnY, z + 0.5D, entity.rotationYaw, entity.rotationPitch);
                if (entity.boundingBox != null && world.getCollidingBoundingBoxes(entity, entity.boundingBox).isEmpty()) {
                    return new double[] {x + 0.5D, spawnY, z + 0.5D};
                }
            }
        }
        return null;
    }

    private static void positionEntityTree(Entity entity, double x, double y, double z) {
        if (entity == null) {
            return;
        }
        entity.setLocationAndAngles(x, y, z, entity.rotationYaw, entity.rotationPitch);
        positionEntityTree(KOMEReflection.getRidingEntity(entity), x, y, z);
    }

    private static void removeEntityTree(World world, Entity entity) {
        if (entity == null) {
            return;
        }
        Entity mount = KOMEReflection.getRidingEntity(entity);
        entity.mountEntity(null);
        world.removeEntity(entity);
        if (mount != null) {
            removeEntityTree(world, mount);
        }
    }

    private static void removeMovementEntityTree(World world, Entity entity) {
        if (entity == null) {
            return;
        }
        Entity mount = KOMEReflection.getRidingEntity(entity);
        entity.mountEntity(null);
        KOMEReflection.setDead(entity);
        world.removeEntity(entity);
        if (mount != null) {
            removeMovementEntityTree(world, mount);
        }
    }

    public static int removeStaleMovingEntities(KOMEWorldData data, World world) {
        if (data == null || world == null) {
            return 0;
        }
        int removed = 0;
        List<Entity> stale = new ArrayList<Entity>();
        for (Object object : world.loadedEntityList) {
            if (!(object instanceof Entity)) {
                continue;
            }
            Entity entity = (Entity) object;
            KOMEHiredUnitRecord record = data.hiredUnits.get(KOMEReflection.getEntityUUID(entity));
            if (record != null && record.isMoving() && !isArrivalSpawnInProgress(data, record)) {
                stale.add(entity);
            }
        }
        for (Entity entity : stale) {
            removeMovementEntityTree(world, entity);
            removed++;
        }
        if (removed > 0) {
            data.markDirty();
            data.syncConquestTiles();
        }
        return removed;
    }

    public static boolean isArrivalSpawnInProgress(KOMEWorldData data, KOMEHiredUnitRecord record) {
        if (data == null || record == null || record.movementOrderId == null || record.movementOrderId.length() == 0) {
            return false;
        }
        KOMEArmyMovementOrder order = data.armyMovements.get(record.movementOrderId);
        return order != null && (KOMEArmyMovementOrder.SPAWNING.equals(order.status)
            || KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status));
    }

    private static Entity findLoadedEntity(World world, UUID entityId) {
        if (world == null || entityId == null) {
            return null;
        }
        for (Object object : world.loadedEntityList) {
            if (object instanceof Entity && entityId.equals(KOMEReflection.getEntityUUID((Entity) object))) {
                return (Entity) object;
            }
        }
        return null;
    }

    private static Entity reconcileLoadedStationedUnit(KOMEWorldData data, World world, KOMEArmyCompany company, KOMEHiredUnitRecord record) {
        if (data == null || world == null || company == null || record == null || record.owner == null) {
            return null;
        }
        String expectedName = normalizeUnitName(record.unitName);
        for (Object object : world.loadedEntityList) {
            if (!(object instanceof LOTREntityNPC)) {
                continue;
            }
            LOTREntityNPC npc = (LOTREntityNPC) object;
            if (!npc.isEntityAlive() || npc.hiredNPCInfo == null || !npc.hiredNPCInfo.isActive) {
                continue;
            }
            LOTRHiredNPCInfo info = npc.hiredNPCInfo;
            if (info.getHiringPlayerUUID() == null || !record.owner.equals(info.getHiringPlayerUUID())) {
                continue;
            }
            if (info.getTask() != LOTRHiredNPCInfo.Task.WARRIOR || record.type != KOMEPopulationType.OFFENSIVE || record.farmhand) {
                continue;
            }
            UUID npcId = KOMEReflection.getEntityUUID(npc);
            KOMEHiredUnitRecord alreadyTracked = data.hiredUnits.get(npcId);
            if (alreadyTracked != null && alreadyTracked != record) {
                continue;
            }
            String npcName = normalizeUnitName(getDisplayUnitName(npc));
            if (expectedName.length() > 0 && !expectedName.equals(npcName)) {
                continue;
            }
            UUID oldId = record.entity;
            if (oldId != null && !oldId.equals(npcId)) {
                data.hiredUnits.remove(oldId);
                record.entity = npcId;
                data.hiredUnits.put(npcId, record);
                replaceCompanyUnitId(company, oldId, npcId);
            }
            record.unitName = getDisplayUnitName(npc);
            record.stationedEntityData = KOMEEntitySnapshots.snapshot(npc);
            data.markDirty();
            return npc;
        }
        return null;
    }

    private static void replaceCompanyUnitId(KOMEArmyCompany company, UUID oldId, UUID newId) {
        if (company == null || oldId == null || newId == null) {
            return;
        }
        for (int i = 0; i < company.units.size(); i++) {
            if (oldId.equals(company.units.get(i))) {
                company.units.set(i, newId);
                return;
            }
        }
    }

    private static String getDisplayUnitName(LOTREntityNPC npc) {
        String name = npc == null ? "" : npc.getCommandSenderName();
        return name == null || name.trim().isEmpty() ? (npc == null ? "" : npc.getClass().getSimpleName()) : name;
    }

    private static String normalizeUnitName(String name) {
        return name == null ? "" : name.trim().toLowerCase();
    }

    private static KOMEHiredUnitRecord findUnitByPrefix(KOMEWorldData data, String id, UUID owner, boolean admin) {
        KOMEHiredUnitRecord found = null;
        String lookup = id == null ? "" : id.toLowerCase();
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record == null || record.entity == null || !admin && !owner.equals(record.owner)) {
                continue;
            }
            String uuid = record.entity.toString().toLowerCase();
            if (uuid.equals(lookup) || uuid.startsWith(lookup)
                    || record.unitName != null && record.unitName.toLowerCase().contains(lookup)) {
                if (found != null) {
                    throw new WrongUsageException("More than one unit matches " + id + ". Use a longer unit ID or name.");
                }
                found = record;
            }
        }
        return found;
    }

    private static String describePhysicalEntity(World world, Entity entity, UUID expectedId, int expectedDimension, double expectedX, double expectedY, double expectedZ) {
        if (world == null) {
            return "not loaded; server world is unavailable";
        }
        if (entity == null) {
            return "not loaded; no entity with UUID " + expectedId + " exists in loadedEntityList";
        }
        boolean inLoadedList = false;
        for (Object object : world.loadedEntityList) {
            if (object == entity) {
                inLoadedList = true;
                break;
            }
        }
        double dx = entity.posX - expectedX;
        double dy = entity.posY - expectedY;
        double dz = entity.posZ - expectedZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return (entity.isDead ? "dead" : "alive") + ", loadedList " + (inLoadedList ? "yes" : "no")
            + ", dim " + entity.dimension + (expectedDimension != 0 || expectedX != 0.0D || expectedY != 0.0D || expectedZ != 0.0D
                ? " expected dim " + expectedDimension : "")
            + ", at " + formatBlockPos(entity.posX, entity.posY, entity.posZ)
            + (expectedX != 0.0D || expectedY != 0.0D || expectedZ != 0.0D
                ? ", expected " + formatBlockPos(expectedX, expectedY, expectedZ) + ", distance " + MathHelper.floor_double(distance)
                : "");
    }

    private static KOMEArmyMovementOrder findLastArrivedOrderForCompany(KOMEWorldData data, String companyId) {
        KOMEArmyMovementOrder result = null;
        if (data == null || companyId == null || companyId.length() == 0) {
            return null;
        }
        for (KOMEArmyMovementOrder order : data.armyMovements.values()) {
            if (order == null || !companyId.equals(order.companyId) || !KOMEArmyMovementOrder.ARRIVED.equals(order.status)) {
                continue;
            }
            if (result == null || order.arrivalMillis > result.arrivalMillis) {
                result = order;
            }
        }
        return result;
    }

    private static String physicalCompanySummary(KOMEWorldData data, World world, KOMEArmyMovementOrder order) {
        if (data == null || order == null) {
            return "no order data";
        }
        int physical = 0;
        int total = 0;
        String first = "";
        for (UUID unitId : order.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record == null) {
                continue;
            }
            total++;
            Entity entity = findLoadedEntity(world, record.entity);
            if (entity != null) {
                physical++;
                if (first.length() == 0) {
                    first = " first loaded at dim " + entity.dimension + " "
                        + formatBlockPos(entity.posX, entity.posY, entity.posZ);
                }
            }
        }
        return physical + "/" + total + " loaded" + first;
    }

    private String displayUnitId(KOMEHiredUnitRecord record) {
        if (record.unitName != null && record.unitName.length() > 0) {
            return record.unitName;
        }
        return record.entity == null ? "unknown" : record.entity.toString().substring(0, 8);
    }

    private KOMEArmyCompany validateCompanyMove(EntityPlayerMP player, KOMEWorldData data, UUID owner, String companyId, String destination) {
        data.rebuildArmyCompaniesForPlayer(KOMEReflection.getWorld(player), owner);
        KOMEArmyCompany company = data.armyCompanies.get(companyId);
        if (company == null && player.canCommandSenderUseCommand(2, getCommandName())) {
            data.rebuildArmyCompanies(KOMEReflection.getWorld(player));
            company = data.armyCompanies.get(companyId);
        }
        if (company == null) {
            throw new WrongUsageException("Unknown company " + companyId + ".");
        }
        if (!canPlayerControlCompany(data, player, company)) {
            throw new WrongUsageException("You do not own " + company.name + ".");
        }
        String playerFaction = normalizeFaction(getPlayerFaction(data, player));
        String companyFaction = normalizeFaction(company.faction);
        boolean admin = player.canCommandSenderUseCommand(2, getCommandName());
        boolean temporary = company.isTemporarilyControlledBy(KOMEReflection.getEntityUUID(player));
        if (!admin && !playerFaction.equals(companyFaction) && !temporary) {
            throw new WrongUsageException("Company faction does not match your faction.");
        }
        if (temporary && KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
            if (!KOMEWartimeStewardshipService.revalidateCompany(data, company, System.currentTimeMillis(),
                    "Wartime Stewardship authorization changed before movement")) {
                throw new WrongUsageException("Wartime Stewardship is no longer authorized; this company is halted for withdrawal/demobilization.");
            }
        } else if (temporary && KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)
                && !canUseDelegatedCompanyControl(data, company, owner)) {
            data.recordCompanyDelegationAudit(
                System.currentTimeMillis(), "REVOKED_AUTOMATIC", company,
                null, "",
                company.temporaryController, company.temporaryControllerName,
                "Canonical company delegation is no longer valid");
            company.clearTemporaryController("Canonical company delegation is no longer valid");
            data.markDirty();            throw new WrongUsageException("Temporary command expired because canonical company delegation is no longer valid.");
        }
        if (company.isMoving()) {
            throw new WrongUsageException(company.name + " is already moving.");
        }
        if (company.units.isEmpty()) {
            throw new WrongUsageException(company.name + " has no units.");
        }
        company.currentTile = KOMEConquestTile.normalizeId(company.currentTile);
        destination = KOMEConquestTile.normalizeId(destination);
        if (company.currentTile.equals(destination)) {
            throw new WrongUsageException("Origin and destination must be different tiles.");
        }
        requireCompanyStandableTile(data, company, company.currentTile, "Origin");
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            String blocked = movementBlockReasonForCompanyUnit(record, company);
            if (blocked.length() > 0) {
                throw new WrongUsageException(company.name + " cannot move: " + blocked);
            }
        }
        return company;
    }

    private void validateStewardshipRoute(KOMEWorldData data, KOMEArmyCompany company, RouteResult route) {
        if (company == null || route == null || !KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
            return;
        }
        for (String tileId : route.routeTiles) {
            KOMEConquestTile tile = data.conquestTiles.get(KOMEConquestTile.normalizeId(tileId));
            String owner = tile == null ? "" : normalizeFaction(tile.currentRulingFaction());
            if (!KOMEWartimeStewardshipService.canEnter(data, company, owner, false)) {
                route.valid = false;
                route.failureReason = "Wartime Stewardship cannot enter " + tileId + " ("
                    + displayFaction(owner) + "). Legal territory is native land, Stage 3 partner passage, or an opposing side of an authorized active war.";
                return;
            }
        }
    }

    private String movementBlockReasonForCompanyUnit(KOMEHiredUnitRecord record, KOMEArmyCompany company) {
        if (company == null) {
            return "company data is missing.";
        }
        if (record == null) {
            return "a tracked unit record is missing.";
        }
        String unit = displayUnitId(record);
        if (record.farmhand) {
            return unit + " is a farmhand; farmhands cannot move in companies.";
        }
        if (record.type != KOMEPopulationType.OFFENSIVE) {
            return unit + " is defensive; only offensive units can move.";
        }
        if (!company.id.equals(record.companyId)) {
            return unit + " is assigned to company " + record.companyId + " instead of " + company.id + ".";
        }
        if (!KOMEWorldData.normalizeLotrCompanyValue(company.lotrCompanyValue).equals(KOMEWorldData.normalizeLotrCompanyValue(record.lotrCompanyValue))) {
            return unit + " no longer matches the LOTR Unit Overview company column.";
        }
        if (company.owner == null || !company.owner.equals(record.owner)) {
            return unit + " is owned by a different player than company " + company.id + ".";
        }
        if (record.isMoving()) {
            return unit + " is already attached to movement order " + record.movementOrderId + ".";
        }
        if (!KOMEConquestTile.normalizeId(company.currentTile).equals(KOMEConquestTile.normalizeId(record.currentTile))) {
            return unit + " is stationed at " + KOMEConquestTile.normalizeId(record.currentTile)
                + " instead of company tile " + KOMEConquestTile.normalizeId(company.currentTile) + ".";
        }
        return "";
    }

    private static boolean canUseDelegatedCompanyControl(
            KOMEWorldData data, KOMEArmyCompany company, UUID actor) {
        if (data == null || company == null || actor == null
                || !KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)
                || !company.isTemporarilyControlledBy(actor)) {
            return false;
        }
        return KOMECompanyDiplomacyAuthorization.canContinueDelegation(
            data, KOMEWartimeStewardshipService.nativeFaction(company), actor).allowed;
    }
    private boolean canPlayerControlCompany(KOMEWorldData data, EntityPlayerMP player, KOMEArmyCompany company) {
        if (player == null || company == null) {
            return false;
        }
        UUID actor = KOMEReflection.getEntityUUID(player);
        boolean temporary = KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)
            || KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority);
        if (KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)) {
            return canUseDelegatedCompanyControl(data, company, actor);
        }
        if (temporary) return new KOMEAllianceAuthority(data).canControlTemporaryCompany(company, actor).allowed;
        return player.canCommandSenderUseCommand(2, getCommandName()) || actor.equals(company.owner);
    }

    private boolean canPlayerControlMovementOrder(EntityPlayerMP player, KOMEWorldData data, KOMEArmyMovementOrder order) {
        if (player == null || order == null) {
            return false;
        }
        KOMEArmyCompany company = data == null ? null : data.armyCompanies.get(order.companyId);
        UUID actor = KOMEReflection.getEntityUUID(player);
        if (company != null && KOMEArmyCompany.AUTHORITY_ALLIANCE_DELEGATE.equals(company.controllerAuthority)) {
            return canUseDelegatedCompanyControl(data, company, actor);
        }
        if (company != null && KOMEArmyCompany.AUTHORITY_STEWARDSHIP.equals(company.controllerAuthority)) {
            return new KOMEAllianceAuthority(data).canControlTemporaryCompany(company, actor).allowed;
        }
        return player.canCommandSenderUseCommand(2, getCommandName()) || actor.equals(order.owner);
    }

    private boolean companyHasPresenceAtTile(KOMEWorldData data, KOMEArmyCompany company, String tileId) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        if (company == null || tile.length() == 0) {
            return false;
        }
        if (tile.equals(KOMEConquestTile.normalizeId(company.currentTile))) {
            return true;
        }
        KOMEArmyMovementOrder order = data.armyMovements.get(company.movementOrderId);
        if (order != null && KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)
                && tile.equals(activeStepOrigin(order))) {
            return true;
        }
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record != null && tile.equals(KOMEConquestTile.normalizeId(record.currentTile))) {
                return true;
            }
        }
        return false;
    }

    private String companyDisplayTile(KOMEWorldData data, KOMEArmyCompany company) {
        String tile = KOMEConquestTile.normalizeId(company == null ? "" : company.currentTile);
        if (tile.length() > 0) {
            return tile;
        }
        if (company != null) {
            for (UUID unitId : company.units) {
                KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
                tile = KOMEConquestTile.normalizeId(record == null ? "" : record.currentTile);
                if (tile.length() > 0) {
                    return tile;
                }
            }
        }
        return "";
    }

    private String companyTileDisplayName(KOMEWorldData data, String tileId) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        if (data == null || tile.length() == 0) {
            return "";
        }
        KOMETileWaypointLink waypointLink = data.getTileWaypointLink(tile);
        if (waypointLink == null) {
            return "";
        }
        String displayName = waypointLink.displayName();
        return displayName == null ? "" : displayName.trim();
    }

    private KOMEConquestTile requireControlledTile(KOMEWorldData data, String tileId, String factionKey, String role) {
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        String normalizedFaction = normalizeFaction(factionKey);
        KOMEConquestTile tile = data.conquestTiles.get(normalizedTile);
        if (tile == null) {
            throw new WrongUsageException(role + " tile " + normalizedTile + " was not found in conquest data.");
        }
        String owner = normalizeFaction(tile.currentRulingFaction());
        if (!tile.isClaimed() || owner.length() == 0) {
            throw new WrongUsageException(role + " tile " + normalizedTile + " is not claimed.");
        }
        if (!owner.equals(normalizedFaction)) {
            throw new WrongUsageException(role + " tile " + normalizedTile + " is owned by " + displayFaction(owner)
                + " (" + emptyKey(owner) + "), but your faction is " + displayFaction(normalizedFaction)
                + " (" + emptyKey(normalizedFaction) + ").");
        }
        return tile;
    }

    private KOMEConquestTile requireCompanyStandableTile(KOMEWorldData data, KOMEArmyCompany company, String tileId, String role) {
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        KOMEConquestTile tile = data.conquestTiles.get(normalizedTile);
        String owner = tile == null ? "" : normalizeFaction(tile.currentRulingFaction());
        if (company != null && tile != null && tile.isClaimed()
                && KOMEWartimeStewardshipService.canEnter(data, company, owner, false)) return tile;
        return requireFactionStandableTile(data, company == null ? "" : company.faction, tileId, role);
    }

    private KOMEConquestTile requireFactionStandableTile(KOMEWorldData data, String factionKey, String tileId, String role) {
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        KOMEConquestTile tile = data.conquestTiles.get(normalizedTile);
        if (tile == null) {
            throw new WrongUsageException(role + " tile " + normalizedTile + " was not found in conquest data.");
        }
        String owner = normalizeFaction(tile.currentRulingFaction());
        String companyFaction = normalizeFaction(factionKey);
        if (!tile.isClaimed() || owner.length() == 0) {
            throw new WrongUsageException(role + " tile " + normalizedTile + " is not claimed.");
        }
        if (owner.equals(companyFaction) || data.canFactionUseMilitaryPassage(companyFaction, owner)) {
            return tile;
        }
        throw new WrongUsageException(role + " tile " + normalizedTile + " is owned by " + displayFaction(owner)
            + " (" + emptyKey(owner) + "), and " + displayFaction(companyFaction) + " (" + emptyKey(companyFaction)
            + ") has no canonical Allies passage there.");
    }

    private String companyStandBlockReason(KOMEWorldData data, KOMEArmyCompany company, String tileId) {
        String normalizedTile = KOMEConquestTile.normalizeId(tileId);
        KOMEConquestTile tile = data.conquestTiles.get(normalizedTile);
        if (tile == null) {
            return "Current tile is missing from conquest data";
        }
        String owner = normalizeFaction(tile.currentRulingFaction());
        String companyFaction = normalizeFaction(company == null ? "" : company.faction);
        if (!tile.isClaimed() || owner.length() == 0) {
            return "Current tile is not claimed";
        }
        if (owner.equals(companyFaction) || data.canFactionUseMilitaryPassage(companyFaction, owner)) {
            return "";
        }
        return displayFaction(companyFaction) + " has no canonical Allies passage through " + displayFaction(owner);
    }

    private static void refreshCompany(KOMEWorldData data, KOMEArmyCompany company) {
        if (company == null) {
            return;
        }
        int total = 0;
        int mounted = 0;
        int ground = 0;
        List<UUID> missing = new ArrayList<UUID>();
        String sharedTile = "";
        boolean mixedTiles = false;
        for (UUID unitId : company.units) {
            KOMEHiredUnitRecord record = data.hiredUnits.get(unitId);
            if (record == null || record.farmhand || record.type != KOMEPopulationType.OFFENSIVE) {
                missing.add(unitId);
                continue;
            }
            String recordTile = KOMEConquestTile.normalizeId(record.currentTile);
            if (!record.isMoving() && recordTile.length() > 0) {
                if (sharedTile.length() == 0) {
                    sharedTile = recordTile;
                } else if (!sharedTile.equals(recordTile)) {
                    mixedTiles = true;
                }
            }
            total += Math.max(0, record.cost);
            if (record.mounted) {
                mounted += Math.max(0, record.cost);
            } else {
                ground += Math.max(0, record.cost);
            }
        }
        company.units.removeAll(missing);
        company.totalPopulation = total;
        company.mountedPopulation = mounted;
        company.groundPopulation = ground;
        if (!company.isMoving() && !mixedTiles && sharedTile.length() > 0) {
            company.currentTile = sharedTile;
        }
    }

    private boolean hasUnassignedOffensiveUnits(KOMEWorldData data, UUID owner, String tile) {
        for (KOMEHiredUnitRecord record : data.hiredUnits.values()) {
            if (record != null && owner.equals(record.owner) && !record.farmhand
                    && record.type == KOMEPopulationType.OFFENSIVE && !record.isMoving()
                    && tile.equals(KOMEConquestTile.normalizeId(record.currentTile))
                    && (record.companyId == null || record.companyId.length() == 0)) {
                return true;
            }
        }
        return false;
    }

    private int countMounted(List<KOMEHiredUnitRecord> records) {
        int count = 0;
        for (KOMEHiredUnitRecord record : records) {
            if (record.mounted) {
                count++;
            }
        }
        return count;
    }

    private int estimateRouteDistance(KOMEWorldData data, String origin, String destination) {
        RouteResult route = findLegalRoute(data, origin, destination, "");
        return route.valid ? route.distance() : 1;
    }

    private RouteResult findLegalRoute(KOMEWorldData data, String origin, String destination, String factionKey) {
        return findLegalRoute(data, origin, destination, factionKey, null);
    }

    private RouteResult findLegalRoute(KOMEWorldData data, String origin, String destination, String factionKey,
            KOMEArmyCompany company) {
        RouteResult result = new RouteResult();
        String start = KOMEConquestTile.normalizeId(origin);
        String goal = KOMEConquestTile.normalizeId(destination);
        String faction = normalizeFaction(factionKey);
        if (faction.length() == 0) {
            KOMEConquestTile originTile = data == null ? null : data.getConquestTile(start);
            faction = originTile == null ? "" : normalizeFaction(originTile.currentRulingFaction());
        }
        if (data == null) {
            result.failureReason = "Conquest data is unavailable.";
            return result;
        }
        if (start.length() == 0 || goal.length() == 0) {
            result.failureReason = "Origin and destination tiles are required.";
            return result;
        }
        if (start.equals(goal)) {
            result.failureReason = "Origin and destination must be different tiles.";
            return result;
        }
        KOMEConquestTile originTile = data.getConquestTile(start);
        if (originTile == null) {
            result.failureReason = "Origin tile " + start + " was not found in conquest data.";
            return result;
        }
        String originOwner = normalizeFaction(originTile.currentRulingFaction());
        if (!originTile.isClaimed() || originOwner.length() == 0) {
            result.failureReason = "Origin tile " + start + " is not claimed.";
            return result;
        }
        if (!originOwner.equals(faction) && !data.canFactionUseMilitaryPassage(faction, originOwner)
                && (company == null || !KOMEWartimeStewardshipService.canEnter(data, company, originOwner, false))) {
            result.failureReason = "Origin tile " + start + " is owned by " + displayFaction(originOwner)
                + " (" + emptyKey(originOwner) + "), but moving faction is " + displayFaction(faction)
                + " (" + emptyKey(faction) + ") and has no canonical Allies passage there.";
            return result;
        }
        String destinationBlock = routeTileBlockReason(data, goal, faction, true, company);
        if (destinationBlock != null) {
            result.failureReason = destinationBlock;
            return result;
        }

        Queue<String> queue = new LinkedList<String>();
        Set<String> visited = new HashSet<String>();
        Map<String, String> previous = new HashMap<String, String>();
        queue.add(start);
        visited.add(start);
        result.visitedTiles.add(start);
        while (!queue.isEmpty()) {
            String current = queue.remove();
            List<String> neighbors = new ArrayList<String>(data.getRouteNeighbors(current));
            sortRouteNeighborsForGoal(data, current, neighbors, goal);
            for (String neighbor : neighbors) {
                String next = KOMEConquestTile.normalizeId(neighbor);
                if (next.length() == 0 || visited.contains(next)) {
                    continue;
                }
                KOMEConquestRouteEdge edge = data.getRouteEdge(current, next);
                if (edge == null) {
                    continue;
                }
                if (!edge.isPassable()) {
                    addRouteBlocker(result, current, next, edge.describeBlock(), next.equals(goal) ? 100 : routeBlockPriority(edge));
                    continue;
                }
                String tileBlock = routeTileBlockReason(data, next, faction, next.equals(goal), company);
                if (tileBlock != null) {
                    addRouteBlocker(result, current, next, tileBlock, next.equals(goal) ? 100 : 70);
                    continue;
                }
                previous.put(next, current);
                if (next.equals(goal)) {
                    result.valid = true;
                    buildRoute(result, previous, start, goal);
                    addRouteEdgeNotes(data, result);
                    return result;
                }
                visited.add(next);
                result.visitedTiles.add(next);
                queue.add(next);
            }
        }
        result.failureReason = buildRouteFailureReason(result, start, goal, faction, visited);
        return result;
    }

    private void sortRouteNeighborsForGoal(final KOMEWorldData data, final String current, List<String> neighbors, final String goal) {
        Collections.sort(neighbors, new Comparator<String>() {
            @Override
            public int compare(String first, String second) {
                String a = KOMEConquestTile.normalizeId(first);
                String b = KOMEConquestTile.normalizeId(second);
                int passable = routeEdgePassRank(data, current, a) - routeEdgePassRank(data, current, b);
                if (passable != 0) {
                    return passable;
                }
                double distance = routeCenterDistanceSq(a, goal) - routeCenterDistanceSq(b, goal);
                if (distance < 0.0D) {
                    return -1;
                }
                if (distance > 0.0D) {
                    return 1;
                }
                return a.compareTo(b);
            }
        });
    }

    private int routeEdgePassRank(KOMEWorldData data, String fromTile, String toTile) {
        KOMEConquestRouteEdge edge = data == null ? null : data.getRouteEdge(fromTile, toTile);
        if (edge == null) {
            return 3;
        }
        if (edge.isPassable()) {
            return edge.isSpecialPassage() ? 1 : 0;
        }
        return 2;
    }

    private double routeCenterDistanceSq(String tileId, String goalTileId) {
        KOMEConquestTileDefaults.TileCenter tile = KOMEConquestTileDefaults.getTileCenter(tileId);
        KOMEConquestTileDefaults.TileCenter goal = KOMEConquestTileDefaults.getTileCenter(goalTileId);
        if (tile == null || goal == null) {
            return Double.MAX_VALUE;
        }
        double dx = tile.x - goal.x;
        double dz = tile.z - goal.z;
        return dx * dx + dz * dz;
    }

    private void addRouteBlocker(RouteResult result, String fromTile, String toTile, String reason, int priority) {
        if (result == null || reason == null || reason.length() == 0) {
            return;
        }
        String from = KOMEConquestTile.normalizeId(fromTile);
        String to = KOMEConquestTile.normalizeId(toTile);
        for (RouteBlocker blocker : result.blockers) {
            if (blocker.fromTile.equals(from) && blocker.toTile.equals(to) && blocker.reason.equals(reason)) {
                blocker.priority = Math.max(blocker.priority, priority);
                return;
            }
        }
        if (result.blockers.size() >= 32 && priority < 100) {
            return;
        }
        result.blockers.add(new RouteBlocker(from, to, reason, priority));
    }

    private int routeBlockPriority(KOMEConquestRouteEdge edge) {
        if (edge == null) {
            return 0;
        }
        if (KOMEConquestRouteEdge.RIVER.equals(edge.edgeType) || KOMEConquestRouteEdge.MOUNTAIN.equals(edge.edgeType)) {
            return 50;
        }
        return 20;
    }

    private String buildRouteFailureReason(RouteResult result, String start, String goal, String faction, Set<String> visited) {
        StringBuilder message = new StringBuilder();
        message.append("No legal route from ").append(start).append(" to ").append(goal).append(".");
        message.append(" Destination ").append(goal).append(" was not reachable through claimed ")
            .append(displayFaction(faction)).append(" tiles or partner tiles unlocked by canonical Allies passage.");
        if (visited == null || visited.size() <= 1) {
            message.append(" The origin has no legal outgoing route steps.");
        }
        List<RouteBlocker> blockers = new ArrayList<RouteBlocker>(result.blockers);
        Collections.sort(blockers, new Comparator<RouteBlocker>() {
            @Override
            public int compare(RouteBlocker first, RouteBlocker second) {
                if (first.priority != second.priority) {
                    return second.priority - first.priority;
                }
                int from = first.fromTile.compareTo(second.fromTile);
                return from != 0 ? from : first.toTile.compareTo(second.toTile);
            }
        });
        if (!blockers.isEmpty()) {
            message.append(" Blocked checks: ");
            int shown = Math.min(3, blockers.size());
            for (int i = 0; i < shown; i++) {
                if (i > 0) {
                    message.append("; ");
                }
                RouteBlocker blocker = blockers.get(i);
                message.append(blocker.fromTile).append(" -> ").append(blocker.toTile).append(": ")
                    .append(stripTrailingPeriod(blocker.reason));
            }
            if (blockers.size() > shown) {
                message.append("; +").append(blockers.size() - shown).append(" more.");
            }
        } else {
            message.append(" No adjacent path was found in the route graph.");
        }
        return message.toString();
    }

    private String stripTrailingPeriod(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        while (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String routeTileBlockReason(KOMEWorldData data, String tileId, String factionKey, boolean destination) {
        return routeTileBlockReason(data, tileId, factionKey, destination, null);
    }

    private String routeTileBlockReason(KOMEWorldData data, String tileId, String factionKey, boolean destination,
            KOMEArmyCompany company) {
        String tileKey = KOMEConquestTile.normalizeId(tileId);
        KOMEConquestTile tile = data.getConquestTile(tileKey);
        if (tile == null) {
            return "Tile " + tileKey + " was not found in conquest data.";
        }
        String owner = normalizeFaction(tile.currentRulingFaction());
        String faction = normalizeFaction(factionKey);
        if (!tile.isClaimed() || owner.length() == 0) {
            return (destination ? "Destination tile " : "Tile ") + tileKey + " is not claimed.";
        }
        if (owner.equals(faction)) {
            return null;
        }
        if (data.canFactionUseMilitaryPassage(faction, owner)) {
            return null;
        }
        if (company != null && KOMEWartimeStewardshipService.canEnter(data, company, owner, false)) {
            return null;
        }
        if (destination) {
            return "Enemy tile attack movement is not implemented yet. Destination tile " + tileKey + " is owned by "
                + displayFaction(owner) + " (" + emptyKey(owner) + "), and " + displayFaction(faction)
                + " (" + emptyKey(faction) + ") has no canonical Allies passage.";
        }
        return "Tile " + tileKey + " is controlled by " + displayFaction(owner) + " and no military passage permission exists.";
    }

    private void buildRoute(RouteResult result, Map<String, String> previous, String start, String goal) {
        List<String> route = new ArrayList<String>();
        String cursor = goal;
        route.add(cursor);
        while (!cursor.equals(start)) {
            cursor = previous.get(cursor);
            if (cursor == null || cursor.length() == 0) {
                result.valid = false;
                result.failureReason = "Route reconstruction failed.";
                result.routeTiles.clear();
                return;
            }
            route.add(cursor);
        }
        Collections.reverse(route);
        result.routeTiles.addAll(route);
    }

    private void addRouteEdgeNotes(KOMEWorldData data, RouteResult result) {
        for (int i = 1; i < result.routeTiles.size(); i++) {
            KOMEConquestRouteEdge edge = data.getRouteEdge(result.routeTiles.get(i - 1), result.routeTiles.get(i));
            if (edge != null && edge.isSpecialPassage()) {
                String name = edge.name == null || edge.name.length() == 0 ? "" : " " + edge.name;
                result.edgeNotes.add(KOMEConquestRouteEdge.displayEdgeType(edge.edgeType) + name
                    + " (" + edge.fromTile + " <-> " + edge.toTile + ")");
            }
        }
    }

    private String formatRouteTiles(List<String> routeTiles) {
        if (routeTiles == null || routeTiles.isEmpty()) {
            return "none";
        }
        StringBuilder route = new StringBuilder();
        for (int i = 0; i < routeTiles.size(); i++) {
            if (i > 0) {
                route.append(" -> ");
            }
            route.append(KOMEConquestTile.normalizeId(routeTiles.get(i)));
            if (i >= 7 && routeTiles.size() > 9) {
                route.append(" -> ... -> ").append(KOMEConquestTile.normalizeId(routeTiles.get(routeTiles.size() - 1)));
                break;
            }
        }
        return route.toString();
    }

    private String routeSummary(RouteResult route) {
        if (route == null || !route.valid) {
            return route == null ? "No route." : route.failureReason;
        }
        String summary = "Route Valid. Distance: " + route.distance() + " tiles. " + formatRouteTiles(route.routeTiles);
        if (!route.edgeNotes.isEmpty()) {
            summary += ". Uses: " + joinDebugList(route.edgeNotes);
        }
        return summary;
    }

    private void traceRoute(ICommandSender sender, KOMEWorldData data, String origin, String destination, String factionKey) {
        String start = KOMEConquestTile.normalizeId(origin);
        String goal = KOMEConquestTile.normalizeId(destination);
        String faction = normalizeFaction(factionKey);
        RouteResult route = findLegalRoute(data, start, goal, faction);
        sender.addChatMessage(new ChatComponentText("Route trace " + start + " -> " + goal + " for "
            + displayFaction(faction) + " (" + emptyKey(faction) + ")."));
        if (route.valid) {
            sender.addChatMessage(new ChatComponentText("VALID: " + formatRouteTiles(route.routeTiles)
                + " (" + route.distance() + " tile step(s))."));
            if (!route.edgeNotes.isEmpty()) {
                sender.addChatMessage(new ChatComponentText("Special crossings: " + joinDebugList(route.edgeNotes) + "."));
            }
            return;
        }
        sender.addChatMessage(new ChatComponentText("BLOCKED: " + route.failureReason));
        sender.addChatMessage(new ChatComponentText("Reachable legal tiles checked: " + route.visitedTiles.size()
            + ". Closest reached to destination: " + closestRouteTiles(route.visitedTiles, goal, 8) + "."));
        List<RouteBlocker> blockers = sortedRouteBlockers(route);
        if (blockers.isEmpty()) {
            sender.addChatMessage(new ChatComponentText("No blocked edge details were recorded. This usually means the route graph is missing adjacency before it reaches the destination."));
            return;
        }
        int shown = Math.min(10, blockers.size());
        for (int i = 0; i < shown; i++) {
            RouteBlocker blocker = blockers.get(i);
            sender.addChatMessage(new ChatComponentText("Blocked " + blocker.fromTile + " -> " + blocker.toTile
                + ": " + stripTrailingPeriod(blocker.reason) + "."));
        }
        if (blockers.size() > shown) {
            sender.addChatMessage(new ChatComponentText("+" + (blockers.size() - shown) + " more blocked checks hidden."));
        }
    }

    private String closestRouteTiles(Set<String> tileIds, final String goal, int limit) {
        List<String> tiles = new ArrayList<String>();
        if (tileIds != null) {
            for (String tileId : tileIds) {
                String tile = KOMEConquestTile.normalizeId(tileId);
                if (tile.length() > 0) {
                    tiles.add(tile);
                }
            }
        }
        Collections.sort(tiles, new Comparator<String>() {
            @Override
            public int compare(String first, String second) {
                double distance = routeCenterDistanceSq(first, goal) - routeCenterDistanceSq(second, goal);
                if (distance < 0.0D) {
                    return -1;
                }
                if (distance > 0.0D) {
                    return 1;
                }
                return first.compareTo(second);
            }
        });
        if (tiles.isEmpty()) {
            return "none";
        }
        if (tiles.size() > limit) {
            return joinDebugList(tiles.subList(0, limit)) + ", +" + (tiles.size() - limit) + " more";
        }
        return joinDebugList(tiles);
    }

    private String routeNeighborSummary(KOMEWorldData data, String tileId) {
        List<String> neighbors = new ArrayList<String>(data.getRouteNeighbors(tileId));
        Collections.sort(neighbors);
        return neighbors.isEmpty() ? "none" : joinDebugList(neighbors);
    }

    private String describeRouteEdge(KOMEConquestRouteEdge edge, boolean override) {
        String description = edge.fromTile + " <-> " + edge.toTile + ": " + KOMEConquestRouteEdge.displayEdgeType(edge.edgeType)
            + (edge.isPassable() ? " passable" : " blocked");
        if (edge.name != null && edge.name.length() > 0) {
            description += " (" + edge.name + ")";
        }
        if (override && (KOMEConquestRouteEdge.BRIDGE.equals(edge.edgeType) || KOMEConquestRouteEdge.MOUNTAIN_PASS.equals(edge.edgeType))) {
            description += ", marker dim " + edge.markerDimension + " at " + formatBlockPos(edge.markerX, edge.markerY, edge.markerZ);
        }
        return description + (override ? " [manual override]" : " [automatic mask edge]");
    }

    private int countAutoRouteEdges(KOMEWorldData data) {
        Set<String> edges = new HashSet<String>();
        for (String tile : data.getRouteGraphTiles()) {
            for (String neighbor : kome.common.data.KOMEConquestTileDefaults.getAdjacentTiles(tile)) {
                edges.add(KOMEConquestRouteEdge.key(tile, neighbor));
            }
        }
        return edges.size();
    }

    private int countAutoRiverRouteEdges(KOMEWorldData data) {
        Set<String> edges = new HashSet<String>();
        for (String tile : data.getRouteGraphTiles()) {
            for (String neighbor : kome.common.data.KOMEConquestTileDefaults.getAdjacentTiles(tile)) {
                String key = KOMEConquestRouteEdge.key(tile, neighbor);
                if (edges.contains(key) || data.getRouteEdgeOverride(tile, neighbor) != null) {
                    continue;
                }
                KOMEConquestRouteEdge edge = data.getRouteEdge(tile, neighbor);
                if (edge != null && KOMEConquestRouteEdge.RIVER.equals(edge.edgeType)) {
                    edges.add(key);
                }
            }
        }
        return edges.size();
    }

    private int countAutoBridgeRouteEdges(KOMEWorldData data) {
        Set<String> edges = new HashSet<String>();
        for (String tile : data.getRouteGraphTiles()) {
            for (String neighbor : kome.common.data.KOMEConquestTileDefaults.getAdjacentTiles(tile)) {
                String key = KOMEConquestRouteEdge.key(tile, neighbor);
                if (edges.contains(key) || data.getRouteEdgeOverride(tile, neighbor) != null) {
                    continue;
                }
                KOMEConquestRouteEdge edge = data.getRouteEdge(tile, neighbor);
                if (edge != null && KOMEConquestRouteEdge.BRIDGE.equals(edge.edgeType)) {
                    edges.add(key);
                }
            }
        }
        return edges.size();
    }

    private static class RouteResult {
        public boolean valid;
        public String failureReason = "";
        public final List<String> routeTiles = new ArrayList<String>();
        public final List<String> edgeNotes = new ArrayList<String>();
        public final List<RouteBlocker> blockers = new ArrayList<RouteBlocker>();
        public final Set<String> visitedTiles = new HashSet<String>();

        public int distance() {
            return Math.max(0, routeTiles.size() - 1);
        }
    }

    private static class RouteBlocker {
        public final String fromTile;
        public final String toTile;
        public final String reason;
        public int priority;

        private RouteBlocker(String fromTile, String toTile, String reason, int priority) {
            this.fromTile = fromTile;
            this.toTile = toTile;
            this.reason = reason;
            this.priority = priority;
        }
    }

    private static String previousTileFor(KOMEArmyMovementOrder order) {
        if (order == null) {
            return "";
        }
        if (order.routeTiles.size() >= 2) {
            return KOMEConquestTile.normalizeId(order.routeTiles.get(order.routeTiles.size() - 2));
        }
        return KOMEConquestTile.normalizeId(order.originTile);
    }

    private static String entrySideFor(KOMEWorldData data, String previousTile, String destinationTile) {
        int[] previous = approximateTileCoords(previousTile);
        int[] destination = approximateTileCoords(destinationTile);
        int dx = destination[0] - previous[0];
        int dz = destination[1] - previous[1];
        if (Math.abs(dx) >= Math.abs(dz)) {
            return dx < 0 ? KOMETileWaypoint.ENTRY_EAST : KOMETileWaypoint.ENTRY_WEST;
        }
        return dz < 0 ? KOMETileWaypoint.ENTRY_SOUTH : KOMETileWaypoint.ENTRY_NORTH;
    }

    private static int[] approximateTileCoords(String tileId) {
        String tile = KOMEConquestTile.normalizeId(tileId);
        int split = 0;
        while (split < tile.length() && Character.isLetter(tile.charAt(split))) {
            split++;
        }
        int column = 0;
        for (int i = 0; i < split; i++) {
            column = column * 26 + (tile.charAt(i) - 'A' + 1);
        }
        int row = 0;
        if (split < tile.length()) {
            try {
                row = Integer.parseInt(tile.substring(split));
            } catch (NumberFormatException ignored) {
                row = 0;
            }
        }
        return new int[] {column, row};
    }

    private static String[] waypointTypes() {
        return new String[] {
            KOMETileWaypoint.RALLY,
            KOMETileWaypoint.ENTRY_NORTH,
            KOMETileWaypoint.ENTRY_SOUTH,
            KOMETileWaypoint.ENTRY_EAST,
            KOMETileWaypoint.ENTRY_WEST
        };
    }

    private String nextCompanyId(KOMEWorldData data) {
        int next = data.armyCompanies.size() + 1;
        String id;
        do {
            id = "C" + next++;
        } while (data.armyCompanies.containsKey(id));
        return id;
    }

    private String joinName(String[] args, int start) {
        StringBuilder name = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(args[i].replace('_', ' '));
        }
        return name.toString().trim();
    }

    private String joinDebugList(List<String> values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(values.get(i));
            if (i >= 7 && values.size() > 8) {
                text.append(", +").append(values.size() - i - 1).append(" more");
                break;
            }
        }
        return text.toString();
    }

    private static String joinValues(List<String> values) {
        if (values == null || values.isEmpty()) return "none";
        Collections.sort(values);
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.length() == 0) continue;
            if (result.length() > 0) result.append(", ");
            result.append(value);
        }
        return result.length() == 0 ? "none" : result.toString();
    }

    private String parseTile(String value) {
        String tile = KOMEConquestTile.normalizeId(value);
        if (tile.length() == 0 || !KOMEConquestTile.isCanonicalTileId(tile)) {
            throw new WrongUsageException("Invalid conquest tile: " + value);
        }
        return tile;
    }

    private long getTravelMillis(KOMEWorldData data, int distanceTiles, int tilesPerDay) {
        if (data != null && data.movementTotalSecondsOverride > 0) {
            return (long) data.movementTotalSecondsOverride * 1000L;
        }
        if (data != null && data.movementSecondsPerTileOverride > 0) {
            return Math.max(1, distanceTiles) * (long) data.movementSecondsPerTileOverride * 1000L;
        }
        long now = System.currentTimeMillis();
        int resets = Math.max(1, (Math.max(1, distanceTiles) + Math.max(1, tilesPerDay) - 1) / Math.max(1, tilesPerDay));
        return Math.max(1L, dailyResetAfter(data, now, resets) - now);
    }

    private long getRouteCompletionMillis(KOMEWorldData data, int distanceTiles, int tilesPerDay) {
        int cooldownSteps = Math.max(0, Math.max(1, distanceTiles) - 1);
        if (cooldownSteps == 0) {
            return 0L;
        }
        if (data != null && data.movementTotalSecondsOverride > 0) {
            return (long) data.movementTotalSecondsOverride * 1000L;
        }
        if (data != null && data.movementSecondsPerTileOverride > 0) {
            return cooldownSteps * (long) data.movementSecondsPerTileOverride * 1000L;
        }
        long now = System.currentTimeMillis();
        int resets = Math.max(1, (cooldownSteps + Math.max(1, tilesPerDay) - 1) / Math.max(1, tilesPerDay));
        return Math.max(1L, dailyResetAfter(data, now, resets) - now);
    }

    private static long getStepCooldownMillis(KOMEWorldData data, KOMEArmyMovementOrder order, long nowMillis) {
        int distance = Math.max(1, order == null ? 1 : order.distanceTiles);
        if (data != null && data.movementTotalSecondsOverride > 0) {
            return Math.max(1L, ((long) data.movementTotalSecondsOverride * 1000L + distance - 1L) / distance);
        }
        if (data != null && data.movementSecondsPerTileOverride > 0) {
            return Math.max(1L, (long) data.movementSecondsPerTileOverride * 1000L);
        }
        return Math.max(1L, nextDailyResetMillis(data, nowMillis) - nowMillis);
    }

    private static long nextStepCooldownMillis(KOMEWorldData data, KOMEArmyMovementOrder order, long nowMillis) {
        if (order != null && isDailyMovementMode(data) && order.dailyStepsRemaining > 0) {
            order.dailyStepsRemaining--;
            return 0L;
        }
        return getStepCooldownMillis(data, order, nowMillis);
    }

    private static boolean isDailyMovementMode(KOMEWorldData data) {
        return data == null || data.movementSecondsPerTileOverride <= 0 && data.movementTotalSecondsOverride <= 0;
    }

    private static long nextDailyResetMillis(KOMEWorldData data, long nowMillis) {
        return dailyResetAfter(data, nowMillis, 1);
    }

    private static long dailyResetAfter(KOMEWorldData data, long nowMillis, int resetCount) {
        TimeZone timezone = TimeZone.getTimeZone(data == null || data.movementDailyResetTimezone == null
            || data.movementDailyResetTimezone.length() == 0 ? "America/Chicago" : data.movementDailyResetTimezone);
        int minuteOfDay = parseDailyResetMinute(data == null ? "20:00" : data.movementDailyResetTime);
        if (minuteOfDay < 0) {
            minuteOfDay = 20 * 60;
        }
        Calendar calendar = Calendar.getInstance(timezone);
        calendar.setTimeInMillis(nowMillis);
        calendar.set(Calendar.HOUR_OF_DAY, minuteOfDay / 60);
        calendar.set(Calendar.MINUTE, minuteOfDay % 60);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (calendar.getTimeInMillis() <= nowMillis) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }
        calendar.add(Calendar.DAY_OF_YEAR, Math.max(1, resetCount) - 1);
        return calendar.getTimeInMillis();
    }

    private static int parseDailyResetMinute(String value) {
        if (value == null) {
            return -1;
        }
        String[] parts = value.trim().split(":");
        if (parts.length != 2) {
            return -1;
        }
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return -1;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String movementScheduleMode(KOMEWorldData data) {
        if (data != null && data.movementTotalSecondsOverride > 0) {
            return "test_total";
        }
        if (data != null && data.movementSecondsPerTileOverride > 0) {
            return "test_seconds_per_tile";
        }
        return "daily_reset";
    }

    private String nextOrderId(KOMEWorldData data) {
        int next = data.armyMovements.size() + 1;
        String id;
        do {
            id = "M" + next++;
        } while (data.armyMovements.containsKey(id));
        return id;
    }

    private String getPlayerFaction(KOMEWorldData data, EntityPlayerMP player) {
        LOTRFaction pledge = LOTRLevelData.getData(player).getPledgeFaction();
        return pledge == null ? "" : KOMEAlliance.normalizeFactionKey(pledge.codeName());
    }

    private String normalizeFaction(String key) {
        return KOMEAlliance.normalizeFactionKey(key);
    }

    private String emptyKey(String key) {
        return key == null || key.length() == 0 ? "none" : key;
    }

    private String formatUnitLine(KOMEWorldData data, KOMEHiredUnitRecord record) {
        String ownerName = data.playerNames.get(record.owner);
        if (ownerName == null || ownerName.length() == 0) {
            ownerName = record.owner == null ? "Unknown player" : record.owner.toString().substring(0, 8);
        }
        String faction = data.getPlayerFactionKey(record.owner);
        String role = record.type == KOMEPopulationType.DEFENSIVE ? "Defensive (immobile)"
            : record.mounted ? "Offensive mounted" : "Offensive ground";
        String source = record.isPlayerReserveFunded() ? "Player Reserve"
            : "Tile " + KOMEConquestTile.normalizeId(record.sourceTileId) + " / " + displayFaction(record.sourceFaction);
        String name = record.unitName == null || record.unitName.trim().length() == 0
            ? record.entity.toString().substring(0, 8) : record.unitName;
        return name + " - " + ownerName + " / " + displayFaction(faction) + " / " + role + " / cost " + record.cost
            + " / stationed " + KOMEConquestTile.normalizeId(record.currentTile) + " / funded by " + source;
    }

    private String displayFaction(String key) {
        return KOMEAlliance.displayFactionName(key);
    }

    private String formatMovementClock(KOMEArmyMovementOrder order, long nowMillis) {
        if (order == null) {
            return "ETA unknown";
        }
        if (KOMEArmyMovementOrder.WAITING_NEXT_STEP.equals(order.status)) {
            if (order.spawnRetryPaused) {
                return "waiting at " + activeStepOrigin(order) + "; next-step departure paused";
            }
            long wait = Math.max(0L, order.nextStepDepartureMillis - nowMillis);
            return wait > 0L ? "waiting at " + activeStepOrigin(order) + "; departs in " + formatDuration(wait)
                : "waiting at " + activeStepOrigin(order) + "; departing next step";
        }
        long remaining = order.getRemainingMillis(nowMillis);
        if (remaining > 0L) {
            return "ETA " + formatDuration(remaining);
        }
        if (order.spawnRetryPaused) {
            return "arrival time reached; spawn retries paused";
        }
        if (order.nextSpawnRetryMillis > nowMillis) {
            return "arrival time reached; retry in " + formatDuration(order.nextSpawnRetryMillis - nowMillis);
        }
        if (order.isPendingSpawn()) {
            return "arrival time reached; pending spawn";
        }
        if (KOMEArmyMovementOrder.SPAWNING.equals(order.status)) {
            return "arrival time reached; spawning";
        }
        return "arrival time reached";
    }

    private static String movementChunkStatus(World world, KOMEArmyMovementOrder order) {
        if (order == null) {
            return "unknown";
        }
        int chunkX = MathHelper.floor_double(order.arrivalX) >> 4;
        int chunkZ = MathHelper.floor_double(order.arrivalZ) >> 4;
        if (world == null || world.provider == null) {
            return chunkX + ", " + chunkZ + " (world unavailable)";
        }
        if (world.provider.dimensionId != order.arrivalDimension) {
            return chunkX + ", " + chunkZ + " (arrival dim " + order.arrivalDimension
                + ", checked dim " + world.provider.dimensionId + ")";
        }
        return chunkX + ", " + chunkZ + (isChunkLoaded(world, MathHelper.floor_double(order.arrivalX), MathHelper.floor_double(order.arrivalZ))
            ? " loaded" : " not loaded");
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0L, (millis + 999L) / 1000L);
        if (seconds < 60L) {
            return seconds + "s";
        }
        long minutes = (seconds + 59L) / 60L;
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
            return getListOfStringsMatchingLastWord(args, "list", "tile", "debugtile", "unit", "companies", "company", "snapshotcompany",
                "arrivals", "locate", "previewmove", "movecompany", "moving", "movetime", "movement", "pledgeRelease", "route", "arrival", "waypoint", "anchor", "recruit", "station", "arrive");
        }
        if (args.length == 2 && "route".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "adjacent", "edge", "addedge", "removeedge", "bridge", "passage", "block", "unblock", "find", "validate", "trace", "graphstats");
        }
        if (args.length == 3 && "route".equalsIgnoreCase(args[0])
                && ("bridge".equalsIgnoreCase(args[1]) || "passage".equalsIgnoreCase(args[1]))) {
            return getListOfStringsMatchingLastWord(args, "add", "remove");
        }
        if (args.length == 5 && "route".equalsIgnoreCase(args[0]) && "block".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "river", "mountain", "blocked");
        }
        if (args.length == 2 && "locate".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "order", "company", "unit");
        }
        if (args.length == 2 && "company".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "rebuild");
        }
        if (args.length == 3 && "company".equalsIgnoreCase(args[0]) && "rebuild".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "all");
        }
        if (args.length == 2 && "waypoint".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "set", "get", "clear", "validate");
        }
        if (args.length == 2 && "arrival".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "set", "get", "clear", "validate", "tp", "missing", "backfill");
        }
        if (args.length == 4 && "waypoint".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "rally", "north", "south", "east", "west");
        }
        if (args.length == 2 && "movetime".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "get", "set", "settotal", "reset", "daily");
        }
        if (args.length == 3 && "movetime".equalsIgnoreCase(args[0]) && "daily".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "get", "set");
        }
        if (args.length == 2 && "movement".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "complete", "retry", "resume", "pause", "cancelspawn", "retarget", "advance", "advanceall", "ticknow");
        }
        if (args.length == 2 && "pledgeRelease".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "preview", "status", "retry", "resolve");
        }
        if (args.length == 4 && "pledgeRelease".equalsIgnoreCase(args[0]) && "resolve".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, "removed", "quarantine");
        }
        if (args.length == 2 && "recruit".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "clear");
        }
        return null;
    }

}
