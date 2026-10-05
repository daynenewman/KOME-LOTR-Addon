package kome.common.command;

import com.lotrcharactercreation.LOTRCharacterCreation;
import com.lotrcharactercreation.creation.CharacterRecreationService;
import com.lotrcharactercreation.creation.CharacterRecreationService.StartResult;
import com.lotrcharactercreation.network.ModNetwork;
import kome.common.KOMEReflection;
import kome.common.config.KOMEConfigInspection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEFactionCapitalRecord;
import kome.common.data.KOMEFactionCapitalService;
import kome.common.data.KOMEWorldData;
import kome.common.data.KOMEWar;
import kome.common.data.KOMEAuditService;
import kome.common.data.KOMEConflictContracts;
import kome.common.data.KOMEConflictLifecycleService;
import kome.common.data.KOMEConflictRecord;
import kome.common.data.KOMEConflictService;
import kome.common.data.KOMERulerService;
import kome.common.data.KOMETileOwnershipDefaults;
import kome.common.data.KOMEWaypointDefaults;
import kome.common.data.KOMEPlayerProgression;
import kome.common.data.KOMEProgressionNpcRankService;
import kome.common.data.KOMEProgressionNpcRef;
import kome.common.data.KOMEProgressionEncounterCleanup;
import kome.common.data.KOMESerfKnightProgression;
import kome.common.data.KOMESerfKnightDefenseService;
import kome.common.data.KOMESerfKnightEscortService;
import kome.common.data.KOMESerfKnightRecoveryService;
import kome.common.data.KOMESerfKnightRelationshipService;
import kome.common.data.KOMESerfKnightTrialAssignment;
import kome.common.network.KOMEPacketUnitMapMarkers;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import lotr.common.fac.LOTRFaction;
import lotr.common.entity.npc.LOTREntityNPC;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KOMECommandKome extends KOMEPublicCommand {
    private final KOMEAdminDiagnosticsCommands diagnostics = new KOMEAdminDiagnosticsCommands();
    @Override
    public String getCommandName() {
        return "kome";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        if (!hasStaffPermission(sender)) return sender instanceof EntityPlayerMP
                && kome.common.tactical.edit.KOMETacticalEditAccess.isAuthorized((EntityPlayerMP) sender)
            ? "/kome [gui|help|tile <tileId>|waypoint propose <name>|tactical [tileId]]"
            : "/kome [gui|help|tile <tileId>|waypoint propose <name>]";
        return "/kome tactical [tileId] | diagnostics <domain> <subject> | repair <preview domain subject|apply token> | conflict <inspect|end> ... | repair conflict <tile> <preview|apply> | waypoint help | capital <list|get faction|relocate faction here> | progression cooldown <on|off> | progression relationship <force <serf|knight|lord>|clear> | character recreate <player> | audit <list|summary> [page] | repair stewardship <faction> | repair war <warId> | config [category] | conquest <reset|balance> | waypointdefaults <reload|apply> | adminmarkers <on|off|status> | ruler <get|assign|remove|repair> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length >= 1 && "tactical".equalsIgnoreCase(args[0])) {
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            if (!kome.common.tactical.edit.KOMETacticalEditAccess.isAuthorized(player)) throw new WrongUsageException("Creative or operator level 2 is required.");
            if (args.length > 2) throw new WrongUsageException("/kome tactical [tileId]");
            String tile = args.length == 2 ? args[1] : kome.common.data.KOMETileWorldResolver.INSTANCE
                .resolveWorldPosition(player.dimension, player.posX, player.posZ).resolvedTileId()
                .orElseThrow(() -> new WrongUsageException("No known tile here. Use /kome tactical <tileId>."));
            try { kome.common.tactical.edit.KOMETacticalEditRuntime.browse(player, tile, 0); }
            catch (IllegalArgumentException invalid) { throw new WrongUsageException(invalid.getMessage()); }
            return;
        }
        if (args.length == 0 || args.length == 1 && "gui".equalsIgnoreCase(args[0])) {
            if (sender instanceof EntityPlayerMP) openOverview((EntityPlayerMP) sender);
            else sendPublicHelp(sender);
            return;
        }
        if (args.length == 1 && "help".equalsIgnoreCase(args[0])) {
            sendPublicHelp(sender);
            if (hasStaffPermission(sender)) sender.addChatMessage(new ChatComponentText(getCommandUsage(sender)));
            return;
        }
        if (args.length == 2 && "tile".equalsIgnoreCase(args[0])) {
            kome.common.network.KOMEPacketConquestOpenCapture.sendTileCommand(getCommandSenderAsPlayer(sender), args[1]);
            return;
        }
        if ("waypoint".equalsIgnoreCase(args[0])) {
            KOMEWaypointCommands.process(sender,args); return;
        }
        // Remaining root functions are administrative. Reject before accessing world state.
        requireStaff(sender);
        if (args.length == 3 && "progression".equalsIgnoreCase(args[0]) && "cooldown".equalsIgnoreCase(args[1])) {
            if (!(sender instanceof EntityPlayerMP)) throw new WrongUsageException("This testing override must be used by an authorized player.");
            if (!"on".equalsIgnoreCase(args[2]) && !"off".equalsIgnoreCase(args[2])) throw new WrongUsageException("/kome progression cooldown <on|off>");
            boolean disabled="off".equalsIgnoreCase(args[2]);
            kome.common.data.KOMESerfKnightCadenceOverride.set(((EntityPlayerMP)sender).getUniqueID(),disabled);
            sender.addChatMessage(new ChatComponentText(disabled?"Progression daily cooldown disabled for testing.":"Progression daily cooldown restored."));
            return;
        }
        if (args.length >= 2 && "progression".equalsIgnoreCase(args[0]) && "relationship".equalsIgnoreCase(args[1])) {
            processRelationshipCommand(sender, args);
            return;
        }
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        if ("diagnostics".equalsIgnoreCase(args[0]) || args.length >= 2
                && "repair".equalsIgnoreCase(args[0])
                && ("preview".equalsIgnoreCase(args[1]) || "apply".equalsIgnoreCase(args[1]))) {
            diagnostics.process(sender, args, data);
            return;
        }
        if (args.length == 3 && "conflict".equalsIgnoreCase(args[0])
                && "inspect".equalsIgnoreCase(args[1])) {
            for (String line : KOMEConflictLifecycleService.INSTANCE
                    .inspectionLines(data, args[2]))
                sender.addChatMessage(new ChatComponentText(line));
            return;
        }
        if (args.length >= 5 && "conflict".equalsIgnoreCase(args[0])
                && "end".equalsIgnoreCase(args[1])) {
            String tile = kome.common.data.KOMEConquestTile.normalizeId(args[2]);
            KOMEConflictRecord current = data.getConflictService().get(tile);
            if (current == null)
                throw new WrongUsageException("No current ConflictRecord exists at " + tile + ".");
            if (!current.getConflictId().equals(args[3]))
                throw new WrongUsageException("Expected Conflict ID " + args[3]
                    + " is stale; current record is " + current.getConflictId() + ".");
            String reason = joinArgs(args, 4);
            KOMEConflictService.EndResult result = data.getConflictService()
                .endWithMovementHandoff(data, tile,
                    KOMEConflictContracts.ExpectedConflict.at(
                        current.getConflictId(), current.getRevision()),
                    new KOMEConflictContracts.Context(System.currentTimeMillis(),
                        auditActor(sender), reason),
                    KOMEConflictService.EndSource.ADMIN_FORCED);
            if (!result.isSuccess())
                throw new WrongUsageException("Conflict end rejected: "
                    + result.conflictResult.code + " - " + result.conflictResult.reason);
            sender.addChatMessage(new ChatComponentText("Ended conflict "
                + result.conflictResult.record.getConflictId() + " at " + tile
                + "; released " + result.movementHoldsReleased
                + " route hold(s) into non-resuming pause. No winner or ownership change was inferred."));
            return;
        }
        if (args.length == 4 && "repair".equalsIgnoreCase(args[0])
                && "conflict".equalsIgnoreCase(args[1])) {
            boolean apply = "apply".equalsIgnoreCase(args[3]);
            if (!apply && !"preview".equalsIgnoreCase(args[3]))
                throw new WrongUsageException(
                    "/kome repair conflict <tile> <preview|apply>");
            KOMEConflictLifecycleService.RepairPlan plan =
                KOMEConflictLifecycleService.INSTANCE.previewRepair(data, args[2]);
            if (apply) {
                KOMEConflictLifecycleService.RepairResult result =
                    KOMEConflictLifecycleService.INSTANCE.applyRepair(data, args[2],
                        System.currentTimeMillis(), auditActor(sender));
                sender.addChatMessage(new ChatComponentText("Conflict repair applied: "
                    + result.changesApplied + " deterministic change(s)."));
                plan = result.plan;
            } else {
                sender.addChatMessage(new ChatComponentText(
                    "Conflict repair preview (no mutation): " + plan.actions.size()
                        + " deterministic change(s)."));
            }
            for (String action : plan.actions)
                sender.addChatMessage(new ChatComponentText("- " + action));
            for (String unresolved : plan.unresolved)
                sender.addChatMessage(new ChatComponentText("UNRESOLVED: " + unresolved));
            if (plan.actions.isEmpty() && plan.unresolved.isEmpty())
                sender.addChatMessage(new ChatComponentText("No deterministic repair is needed."));
            return;
        }
        if (args.length >= 2 && "capital".equalsIgnoreCase(args[0])) {
            processCapital(sender, args, data);
            return;
        }
        if (args.length >= 1 && "audit".equalsIgnoreCase(args[0])) {
            KOMEAdminDiagnosticsCommands.audit(sender, args, data);
            return;
        }
        if (args.length == 3 && "repair".equalsIgnoreCase(args[0]) && "stewardship".equalsIgnoreCase(args[1])) {
            requireStaff(sender);
            kome.common.data.KOMEWartimeStewardshipService.RepairResult result =
                kome.common.data.KOMEWartimeStewardshipService.reconcileDefensiveUnits(data, args[2], System.currentTimeMillis());
            if (!result.allowed) throw new WrongUsageException(result.reason);
            if (result.repairedLinks > 0 || result.repairedControllers > 0)
                kome.common.data.KOMEAuditService.record(data, System.currentTimeMillis(), "ADMIN", "REPAIR_STEWARDSHIP", auditActor(sender), args[2], result.reason,
                    "links=" + result.repairedLinks + ";controllers=" + result.repairedControllers);
            sender.addChatMessage(new ChatComponentText(result.reason + " Links repaired: " + result.repairedLinks
                + "; controller records repaired: " + result.repairedControllers + "."));
            return;
        }
        if (args.length == 3 && "repair".equalsIgnoreCase(args[0]) && "war".equalsIgnoreCase(args[1])) {
            requireStaff(sender);
            KOMEWar war = data.wars.get(args[2]);
            kome.common.data.KOMEWarService.RepairResult result =
                kome.common.data.KOMEWarService.repairConsistency(data, war, System.currentTimeMillis(), auditActor(sender));
            if (!result.allowed) throw new WrongUsageException(result.reason);
            sender.addChatMessage(new ChatComponentText(result.reason));
            return;
        }
        if (args.length == 3 && "character".equalsIgnoreCase(args[0])
            && "recreate".equalsIgnoreCase(args[1])) {
            requireStaff(sender);
            EntityPlayerMP target = getPlayer(sender, args[2]);
            StartResult result = CharacterRecreationService.begin(target);
            if (result == StartResult.ALREADY_IN_CREATION) {
                sender.addChatMessage(
                    new ChatComponentText(target.getCommandSenderName() + " is already in Character Creation."));
                return;
            }

            LOTRCharacterCreation.refreshPlayerStateAndSynchronize(target);
            ModNetwork.sendCharacterCreationRequired(target);
            if (result == StartResult.STARTED) {
                sender.addChatMessage(
                    new ChatComponentText("Started safe character recreation for " + target.getCommandSenderName() + "."));
                target.addChatMessage(
                    new ChatComponentText("[LOTR Character Creation] An administrator reopened Character Creation."));
            } else {
                sender.addChatMessage(
                    new ChatComponentText("Reopened character recreation for " + target.getCommandSenderName() + "."));
            }
            return;
        }
        if (args.length >= 3 && "ruler".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            processRuler(sender, args);
            return;
        }
        if (args.length == 1 && "config".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            sendConfig(sender, KOMEConfigInspection.getAllEffectiveValues());
            return;
        }
        if (args.length == 2 && "config".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            try {
                sendConfig(sender, KOMEConfigInspection.getEffectiveValues(args[1]));
            } catch (IllegalArgumentException e) {
                throw new WrongUsageException(e.getMessage() + ". Use /kome config [dailyBatch|population|movement|battle|muster|siege|battleSupport|encirclement|season]");
            }
            return;
        }
        if (args.length == 2 && "conquest".equalsIgnoreCase(args[0]) && "reset".equalsIgnoreCase(args[1])) {
            requireStaff(sender);
            KOMECommandConquest.resetConquestOwnership(sender, KOMEWorldData.get(sender.getEntityWorld()));
            return;
        }
        if (args.length == 2 && "conquest".equalsIgnoreCase(args[0]) && "balance".equalsIgnoreCase(args[1])) {
            requireStaff(sender);
            for (String line : data.buildConquestBalanceReportLines()) {
                sender.addChatMessage(new ChatComponentText(line));
            }
            return;
        }
        if (args.length == 2 && "waypointdefaults".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if ("reload".equalsIgnoreCase(args[1])) {
                KOMEWaypointDefaults.reload();
                KOMETileOwnershipDefaults.reload();
                sender.addChatMessage(new ChatComponentText("Reloaded " + KOMEWaypointDefaults.loadedEntryCount()
                    + " KOME waypoint default row(s) and " + KOMETileOwnershipDefaults.loadedEntryCount()
                    + " tile ownership default row(s)."));
                return;
            }
            if ("apply".equalsIgnoreCase(args[1])) {
                data.ensureAutomaticTileWaypointLinks();
                boolean changed = data.applyWaypointDefaults(false);
                if (changed) {
                    data.markDirty();
                }
                data.syncConquestTiles();
                sender.addChatMessage(new ChatComponentText("Applied waypoint and tile ownership default metadata"
                    + (changed ? "." : "; no changes were needed.")
                    + " Current conquest ownership was not reset."));
                return;
            }
        }
        if (args.length == 2 && "adminmarkers".equalsIgnoreCase(args[0])) {
            requireStaff(sender);
            if (!(sender instanceof EntityPlayerMP)) {
                throw new WrongUsageException("Only a player can change personal admin marker visibility.");
            }
            EntityPlayerMP player = (EntityPlayerMP) sender;
            if ("on".equalsIgnoreCase(args[1]) || "all".equalsIgnoreCase(args[1])) {
                data.setAdminUnitMapMarkersDisabled(KOMEReflection.getEntityUUID(player), false);
                KOMEPacketUnitMapMarkers.sendToPlayer(data, player);
                sender.addChatMessage(new ChatComponentText("Admin live unit markers: showing all tracked loaded units."));
                return;
            }
            if ("off".equalsIgnoreCase(args[1]) || "own".equalsIgnoreCase(args[1])) {
                data.setAdminUnitMapMarkersDisabled(KOMEReflection.getEntityUUID(player), true);
                KOMEPacketUnitMapMarkers.sendToPlayer(data, player);
                sender.addChatMessage(new ChatComponentText("Admin live unit markers disabled. Map now shows only your own loaded units."));
                return;
            }
            if ("status".equalsIgnoreCase(args[1])) {
                boolean disabled = data.isAdminUnitMapMarkersDisabled(KOMEReflection.getEntityUUID(player));
                sender.addChatMessage(new ChatComponentText(disabled
                    ? "Admin live unit markers are disabled; showing only your own loaded units."
                    : "Admin live unit markers are enabled; showing all tracked loaded units."));
                return;
            }
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private static String auditActor(ICommandSender sender) {
        if (sender == null) return "system";
        String name = sender.getCommandSenderName();
        return name == null || name.trim().length() == 0 ? "system" : name.trim();
    }

    private static String joinArgs(String[] args, int start) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (result.length() > 0) result.append(' ');
            result.append(args[i]);
        }
        String reason = result.toString().trim();
        if (reason.length() == 0)
            throw new WrongUsageException("Explicit conflict end reason is required.");
        return reason;
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        boolean tactical = sender instanceof EntityPlayerMP
            && (args.length == 1 || args.length == 2 && "tactical".equalsIgnoreCase(args[0]))
            && kome.common.tactical.edit.KOMETacticalEditAccess.isAuthorized((EntityPlayerMP) sender);
        if (tactical && args.length == 2 && "tactical".equalsIgnoreCase(args[0]))
            return getListOfStringsMatchingLastWord(args, kome.common.data.KOMEConquestTileDefaults.getKnownTileIds().toArray(new String[0]));
        if(args.length==2 && "waypoint".equalsIgnoreCase(args[0]))
            return hasStaffPermission(sender)?getListOfStringsMatchingLastWord(args,"help","propose","pending","list","inspect","history",
                "adjust","approve","reject","add","associate","rename","move","remove","level","migration")
                :getListOfStringsMatchingLastWord(args,"help","propose");
        if (args.length == 1) {
            List suggestions = new ArrayList(hasStaffPermission(sender) ? getListOfStringsMatchingLastWord(
                args,
                "gui", "help", "tile", "waypoint",
                "character",
                "config",
                "conquest",
                "waypointdefaults",
                "adminmarkers",
                "capital",
                "ruler",
                "audit",
                "diagnostics",
                "conflict",
                "repair",
                "progression") : getListOfStringsMatchingLastWord(args, "gui", "help", "tile", "waypoint"));
            if (tactical) suggestions.addAll(getListOfStringsMatchingLastWord(args, "tactical"));
            return suggestions;
        }
        if (!hasStaffPermission(sender)) return java.util.Collections.emptyList();
        if (args.length == 2 && "capital".equalsIgnoreCase(args[0]))
            return getListOfStringsMatchingLastWord(args, "list", "get", "relocate");
        if (args.length == 3 && "capital".equalsIgnoreCase(args[0])
                && ("get".equalsIgnoreCase(args[1])
                    || "relocate".equalsIgnoreCase(args[1])))
            return getListOfStringsMatchingLastWord(args, factionSuggestions());
        if (args.length == 4 && "capital".equalsIgnoreCase(args[0])
                && "relocate".equalsIgnoreCase(args[1]))
            return getListOfStringsMatchingLastWord(args, "here");
        if (args.length == 2 && "audit".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "list", "summary");
        if (args.length == 2 && "diagnostics".equalsIgnoreCase(args[0]))
            return getListOfStringsMatchingLastWord(args, "population", "ruler", "capital", "diplomacy", "ownership", "waypoint");
        if (args.length == 2 && "repair".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "preview", "apply", "conflict", "stewardship", "war");
        if (args.length == 3 && "repair".equalsIgnoreCase(args[0]) && "preview".equalsIgnoreCase(args[1]))
            return getListOfStringsMatchingLastWord(args, "ownership", "diplomacy", "ruler", "waypoint");
        if (args.length == 2 && "conflict".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "inspect", "end");
        if (args.length == 3 && "conflict".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "T");
        if (args.length == 4 && "repair".equalsIgnoreCase(args[0]) && "conflict".equalsIgnoreCase(args[1])) return getListOfStringsMatchingLastWord(args, "preview", "apply");
        if (args.length == 3 && "repair".equalsIgnoreCase(args[0]) && "stewardship".equalsIgnoreCase(args[1])) return getListOfStringsMatchingLastWord(args, factionSuggestions());
        if (args.length == 3 && "repair".equalsIgnoreCase(args[0]) && "war".equalsIgnoreCase(args[1])) return getListOfStringsMatchingLastWord(args, KOMEWorldData.get(sender.getEntityWorld()).wars.keySet().toArray(new String[0]));
        if (args.length == 2 && "ruler".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "get", "assign", "remove", "repair");
        }
        if (args.length == 3 && "ruler".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, factionSuggestions());
        }
        if (args.length == 4 && "ruler".equalsIgnoreCase(args[0])
            && ("assign".equalsIgnoreCase(args[1]) || "repair".equalsIgnoreCase(args[1]))) {
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        }
        if (args.length == 2 && "character".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "recreate");
        }
        if (args.length == 3 && "character".equalsIgnoreCase(args[0])
            && "recreate".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, MinecraftServer.getServer().getAllUsernames());
        }
        if (args.length == 2 && "config".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(
                args,
                "dailyBatch",
                "population",
                "movement",
                "battle",
                "muster",
                "siege",
                "battleSupport",
                "encirclement",
                "season");
        }
        if (args.length == 2 && "conquest".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "reset", "balance");
        }
        if (args.length == 2 && "waypointdefaults".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "reload", "apply");
        }
        if (args.length == 2 && "adminmarkers".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "on", "off", "status");
        }
        if (args.length == 2 && "progression".equalsIgnoreCase(args[0])) return getListOfStringsMatchingLastWord(args, "cooldown", "relationship");
        if (args.length == 3 && "progression".equalsIgnoreCase(args[0]) && "cooldown".equalsIgnoreCase(args[1])) return getListOfStringsMatchingLastWord(args, "on", "off");
        if (args.length == 3 && "progression".equalsIgnoreCase(args[0]) && "relationship".equalsIgnoreCase(args[1])) return getListOfStringsMatchingLastWord(args, "force", "clear");
        if (args.length == 4 && "progression".equalsIgnoreCase(args[0]) && "relationship".equalsIgnoreCase(args[1]) && "force".equalsIgnoreCase(args[2])) return getListOfStringsMatchingLastWord(args, "serf", "knight", "lord");
        return null;
    }

    /** Minecraft passes exactly the tokens after /kome. */
    private void processRelationshipCommand(ICommandSender sender, String[] args) {
        if (args.length == 3 && "clear".equalsIgnoreCase(args[2])) { clearRelationship(sender); return; }
        if (args.length == 4 && "force".equalsIgnoreCase(args[2])) { forceRelationship(sender, args[3]); return; }
        throw new WrongUsageException("/kome progression relationship <force <serf|knight|lord>|clear>");
    }

    private void forceRelationship(ICommandSender sender, String levelName) {
        if (!(sender instanceof EntityPlayerMP)) throw new WrongUsageException("This testing command must be used by an authorized player.");
        KOMESerfKnightRelationshipService.ForceLevel level = KOMESerfKnightRelationshipService.ForceLevel.forCommand(levelName);
        if (level == null) throw new WrongUsageException("/kome progression relationship force <serf|knight|lord>");
        EntityPlayerMP player = (EntityPlayerMP) sender;
        LOTREntityNPC npc = targetedNpc(player);
        if (npc == null) throw new WrongUsageException("Look at a valid living LOTR faction NPC within 8 blocks.");
        KOMEWorldData data = KOMEWorldData.get(player.worldObj);
        KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
        KOMEProgressionEncounterCleanup.cleanup(player,progression);
        KOMEProgressionNpcRef target = KOMEProgressionNpcRankService.referenceOf(npc);
        KOMESerfKnightRelationshipService.Result result = KOMESerfKnightRelationshipService.force(data, KOMEReflection.getEntityUUID(player), target, level);
        if (!result.success) throw new WrongUsageException(result.reason);
        npc.func_110163_bv();
        kome.common.data.KOMEProgressionAutoCompleter.syncPlayer(player, progression);
        sender.addChatMessage(new ChatComponentText("Forced relationship with targeted NPC to " + level.rank.displayName + "."));
    }

    private void clearRelationship(ICommandSender sender) {
        if (!(sender instanceof EntityPlayerMP)) throw new WrongUsageException("This testing command must be used by an authorized player.");
        EntityPlayerMP player = (EntityPlayerMP) sender;
        LOTREntityNPC npc = targetedNpc(player);
        if (npc == null) throw new WrongUsageException("Look at a valid living LOTR faction NPC within 8 blocks.");
        KOMEWorldData data = KOMEWorldData.get(player.worldObj);
        KOMEPlayerProgression progression = data.getProgression(KOMEReflection.getEntityUUID(player));
        String targetedId=KOMEReflection.getEntityUUID(npc).toString();
        KOMESerfKnightProgression relationshipState=progression.getSerfKnightProgression();
        boolean related=targetedId.equals(relationshipState.getSerfdomMaster().entityUuid)
            ||targetedId.equals(relationshipState.getLiege().entityUuid);
        if(!related)throw new WrongUsageException("The targeted NPC has no relationship to clear.");
        KOMEProgressionEncounterCleanup.cleanup(player,progression);
        KOMESerfKnightRelationshipService.Result result = KOMESerfKnightRelationshipService.clear(data, KOMEReflection.getEntityUUID(player), targetedId);
        if (!result.success) throw new WrongUsageException(result.reason);
        kome.common.data.KOMEProgressionAutoCompleter.syncPlayer(player, progression);
        sender.addChatMessage(new ChatComponentText("Cleared relationship with targeted NPC."));
    }
/** Server-side eight-block line-of-sight target selection; never trusts a client entity id. */
    private static LOTREntityNPC targetedNpc(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.boundingBox == null) return null;
        Vec3 start = Vec3.createVectorHelper(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        Vec3 look = player.getLookVec();
        if (look == null) return null;
        Vec3 end = start.addVector(look.xCoord * 8.0D, look.yCoord * 8.0D, look.zCoord * 8.0D);
        Entity best = null; double bestDistance = 64.0D;
        AxisAlignedBB box = player.boundingBox.addCoord(look.xCoord * 8.0D, look.yCoord * 8.0D, look.zCoord * 8.0D).expand(1.0D, 1.0D, 1.0D);
        for (Object value : player.worldObj.getEntitiesWithinAABBExcludingEntity(player, box)) {
            if (!(value instanceof Entity)) continue;
            Entity candidate = (Entity) value;
            if (!candidate.canBeCollidedWith()) continue;
            MovingObjectPosition hit = candidate.boundingBox.expand(candidate.getCollisionBorderSize(), candidate.getCollisionBorderSize(), candidate.getCollisionBorderSize()).calculateIntercept(start, end);
            if (hit == null) continue;
            double distance = start.distanceTo(hit.hitVec);
            if (distance <= bestDistance) { best = candidate; bestDistance = distance; }
        }
        if (!(best instanceof LOTREntityNPC)) return null;
        LOTREntityNPC npc = (LOTREntityNPC) best;
        return npc.isEntityAlive() && !npc.isChild() && KOMEProgressionNpcRankService.isValidFactionNpc(npc) ? npc : null;
    }

    protected void openOverview(EntityPlayerMP player) {
        // Existing G1 response opens Population, whose Tiles/Menu/Units buttons reach public screens.
        new KOMECommandPopulation().processCommand(player, new String[] {"gui"});
    }

    private void sendPublicHelp(ICommandSender sender) {
        if (sender instanceof EntityPlayerMP && kome.common.tactical.edit.KOMETacticalEditAccess.isAuthorized((EntityPlayerMP) sender))
            sender.addChatMessage(new ChatComponentText("/kome tactical [tileId] - Tactical Area Editor (Deployment Areas / Siege Complexes)."));
        sender.addChatMessage(new ChatComponentText("/kome gui - Population overview; Tiles opens the conquest map."));
        sender.addChatMessage(new ChatComponentText("/kome waypoint propose <name> - Submit your current position for public waypoint review."));
        sender.addChatMessage(new ChatComponentText("/kome tile <tileId> - Tile Command (Builds / Canonical Population)."));
        sender.addChatMessage(new ChatComponentText("Public commands: /population, /conquest list|get, /build list|inspect, /troops, /progression, /alliance, /war list|status, /season status."));
        sender.addChatMessage(new ChatComponentText("The LOTR menu also opens Progression, Server Records and Alliances. Gameplay actions still require their normal permissions."));
    }

    private void requireStaff(ICommandSender sender) {
        if (!hasStaffPermission(sender)) {
            throw new WrongUsageException("You do not have permission to use this KOME admin command.");
        }
    }

    boolean hasStaffPermission(ICommandSender sender) {
        return sender != null && sender.canCommandSenderUseCommand(2, getCommandName());
    }

    private void processCapital(ICommandSender sender, String[] args, KOMEWorldData data) {
        if (args.length == 2 && "list".equalsIgnoreCase(args[1])) {
            for (String faction : KOMEAlliance.allFactionKeys()) {
                KOMEFactionCapitalRecord record =
                    KOMEFactionCapitalService.getCapital(data, faction);
                sender.addChatMessage(new ChatComponentText(formatCapital(data, record)));
            }
            return;
        }
        if (args.length == 3 && "get".equalsIgnoreCase(args[1])) {
            String faction = resolveSupportedFaction(args[2]);
            KOMEFactionCapitalRecord record =
                KOMEFactionCapitalService.getCapital(data, faction);
            if (record == null) throw new WrongUsageException(
                "Authoritative capital is missing for " + faction + ".");
            sender.addChatMessage(new ChatComponentText(formatCapital(data, record)));
            return;
        }
        if (args.length == 4 && "relocate".equalsIgnoreCase(args[1])
                && "here".equalsIgnoreCase(args[3])) {
            if (!(sender instanceof EntityPlayerMP))
                throw new WrongUsageException("Capital relocation here requires an in-world operator.");
            String faction = resolveSupportedFaction(args[2]);
            KOMEFactionCapitalService.RelocationResult result =
                KOMEFactionCapitalService.relocateHere(data, (EntityPlayerMP) sender,
                    faction, System.currentTimeMillis());
            if (!result.success) throw new WrongUsageException(result.reason);
            sender.addChatMessage(new ChatComponentText("Relocated "
                + KOMEAlliance.displayFactionName(faction) + " capital from "
                + result.oldRecord.getCapitalTileId() + " to "
                + result.newRecord.getCapitalTileId() + " at "
                + coordinates(result.newRecord) + "."));
            return;
        }
        throw new WrongUsageException(
            "/kome capital <list|get <faction>|relocate <faction> here>");
    }

    private String formatCapital(KOMEWorldData data, KOMEFactionCapitalRecord record) {
        if (record == null) return "Missing capital record.";
        MinecraftServer server = MinecraftServer.getServer();
        World world = server == null ? null
            : server.worldServerForDimension(record.getDeploymentDimensionId());
        return record.getFactionId() + " (" + KOMEAlliance.displayFactionName(
            record.getFactionId()) + "): tile=" + record.getCapitalTileId()
            + ";anchor=" + coordinates(record) + ";source=" + record.getSource()
            + ";actor=" + record.getActor() + ";readiness="
            + KOMEFactionCapitalService.readiness(data, record.getFactionId(), world);
    }

    private String coordinates(KOMEFactionCapitalRecord record) {
        return "dim " + record.getDeploymentDimensionId() + " / "
            + record.getDeploymentX() + ", " + record.getDeploymentY()
            + ", " + record.getDeploymentZ();
    }

    private void processRuler(ICommandSender sender, String[] args) {
        if (args.length < 3) {
            throw new WrongUsageException("/kome ruler <get|assign|remove|repair> <faction> [player]");
        }
        String action = args[1].toLowerCase();
        String faction = resolveSupportedFaction(args[2]);
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        if ("get".equals(action) && args.length == 3) {
            UUID ruler = KOMERulerService.getRuler(data, faction);
            if (ruler == null) {
                sender.addChatMessage(new ChatComponentText("" + KOMEAlliance.displayFactionName(faction) + " has no recognized ruler."));
            } else {
                sender.addChatMessage(new ChatComponentText(KOMEAlliance.displayFactionName(faction)
                    + " ruler: " + ruler + " (" + KOMERulerService.getRulerName(data, faction) + ")."));
            }
            return;
        }
        if ("assign".equals(action) && args.length == 4) {
            EntityPlayerMP target = getPlayer(sender, args[3]);
            UUID previous = KOMERulerService.getRuler(data, faction);
            KOMERulerService.assignRuler(data, faction, KOMEReflection.getEntityUUID(target), target.getCommandSenderName());
            sender.addChatMessage(new ChatComponentText("Assigned " + target.getCommandSenderName() + " as ruler of "
                + KOMEAlliance.displayFactionName(faction) + (previous == null ? "." : "; replaced " + previous + ".")));
            return;
        }
        if ("remove".equals(action) && args.length == 3) {
            UUID previous = KOMERulerService.getRuler(data, faction);
            if (previous == null) {
                sender.addChatMessage(new ChatComponentText(KOMEAlliance.displayFactionName(faction) + " has no recognized ruler; no change made."));
            } else {
                KOMERulerService.removeRuler(data, faction);
                sender.addChatMessage(new ChatComponentText("Removed recognized ruler " + previous + " from "
                    + KOMEAlliance.displayFactionName(faction) + "."));
            }
            return;
        }
        if ("repair".equals(action) && (args.length == 3 || args.length == 4)) {
            if (args.length == 4) {
                diagnostics.process(sender, new String[] {"repair", "preview", "ruler", faction, args[3]}, data);
                return;
            }
            UUID authoritativeID = null;
            String authoritativeName = null;
            KOMERulerService.RepairResult result = KOMERulerService.repair(data, faction, authoritativeID, authoritativeName);
            if (result.changed) KOMEAuditService.record(data, System.currentTimeMillis(), "RULER", "REPAIR",
                sender.getCommandSenderName(), faction, result.reason, "");
            UUID ruler = KOMERulerService.getRuler(data, faction);
            sender.addChatMessage(new ChatComponentText("Ruler repair for " + KOMEAlliance.displayFactionName(faction)
                + ": " + (result.changed ? "changed" : "unchanged") + " - " + result.reason
                + ". Result: " + (ruler == null ? "no ruler" : ruler + " (" + KOMERulerService.getRulerName(data, faction) + ")")));
            return;
        }
        throw new WrongUsageException("/kome ruler <get|assign|remove|repair> <faction> [player]");
    }

    private String resolveSupportedFaction(String value) {
        LOTRFaction faction = KOMEAlliance.findLotrFaction(value);
        if (faction == null || !faction.isPlayableAlignmentFaction()) {
            throw new WrongUsageException("Unknown supported faction: " + value);
        }
        return KOMEAlliance.normalizeFactionKey(faction.codeName());
    }

    private String[] factionSuggestions() {
        List<String> suggestions = new ArrayList<String>();
        for (LOTRFaction faction : LOTRFaction.values()) {
            if (faction != null && faction.isPlayableAlignmentFaction()) {
                suggestions.add(faction.codeName());
            }
        }
        return suggestions.toArray(new String[suggestions.size()]);
    }

    private void sendConfig(ICommandSender sender,
            List<KOMEConfigInspection.EffectiveValue> values) {
        for (KOMEConfigInspection.EffectiveValue value : values) {
            sender.addChatMessage(new ChatComponentText(value.format()));
        }
    }
}
