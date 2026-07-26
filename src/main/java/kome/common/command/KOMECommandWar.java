package kome.common.command;

import kome.common.data.KOMEAlliance;
import kome.common.data.KOMEAllianceProgressionService;
import kome.common.data.KOMEWar;
import kome.common.data.KOMEWarService;
import kome.common.data.KOMEWartimeStewardshipService;
import kome.common.data.KOMEWorldData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

import java.util.ArrayList;
import java.util.List;

/** Operator-managed coalition war lifecycle. */
public class KOMECommandWar extends CommandBase {
    @Override
    public String getCommandName() {
        return "war";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/war create <factionA> <factionB> [name] | rename <warId> <name> | side <rename|add|remove|move> ... | status <warId> | list [active|ending|ended|all] | end|finalize|cancel <warId> [reason]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 0) throw new WrongUsageException(getCommandUsage(sender));
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        long now = System.currentTimeMillis();
        if ("create".equals(action)) {
            requireStaff(sender);
            if (args.length < 3) throw new WrongUsageException("/war create <factionA> <factionB> [name]");
            String first = faction(args[1]);
            String second = faction(args[2]);
            if (first.equals(second)) throw new WrongUsageException("A faction cannot oppose itself.");
            KOMEWar existing = KOMEWarService.findActiveOpposition(data, first, second);
            if (existing != null) {
                sender.addChatMessage(new ChatComponentText("Those factions already oppose each other in " + display(existing) + "; no duplicate war was created."));
                return;
            }
            KOMEWar war = KOMEWarService.createWar(data, first, second, join(args, 3), sender.getCommandSenderName(), now);
            if (war == null) throw new WrongUsageException("Could not create the war record.");
            refresh(data, "War created");
            sender.addChatMessage(new ChatComponentText("Created " + display(war) + ": " + side(war, 1) + " versus " + side(war, 2) + "."));
            warnContradictions(sender, data, first, second);
            return;
        }
        if ("list".equals(action)) {
            String filter = args.length >= 2 ? args[1].toLowerCase(java.util.Locale.ROOT) : "active";
            if (!"active".equals(filter) && !"ending".equals(filter) && !"ended".equals(filter) && !"all".equals(filter)) {
                throw new WrongUsageException("/war list [active|ending|ended|all]");
            }
            int shown = 0;
            for (KOMEWar war : KOMEWarService.sortedWars(data)) {
                if ("active".equals(filter) && !war.isActive() || "ending".equals(filter) && !war.isEnding()
                        || "ended".equals(filter) && !war.isEnded()) continue;
                sender.addChatMessage(new ChatComponentText(summary(war)));
                shown++;
            }
            if (shown == 0) sender.addChatMessage(new ChatComponentText("No " + filter + " war records."));
            return;
        }
        if ("status".equals(action)) {
            if (args.length != 2) throw new WrongUsageException("/war status <warId>");
            KOMEWar war = war(data, args[1]);
            sender.addChatMessage(new ChatComponentText(summary(war)));
            sender.addChatMessage(new ChatComponentText(war.sideOneName + ": " + join(new ArrayList<String>(war.sideOneFactions))));
            sender.addChatMessage(new ChatComponentText(war.sideTwoName + ": " + join(new ArrayList<String>(war.sideTwoFactions))));
            sender.addChatMessage(new ChatComponentText("Tile events: " + war.tileCaptureHistory.size() + ", stewardship records: "
                + war.stewardshipAuthorizations.size() + ", admin events: " + war.administrativeHistory.size() + "."));
            for (KOMEWar.MembershipRecord membership : war.membershipHistory) {
                sender.addChatMessage(new ChatComponentText("Membership: " + KOMEAlliance.displayFactionName(membership.faction)
                    + " side " + membership.side + " via " + membership.source
                    + (membership.nativeFaction.length() == 0 ? "" : " for " + KOMEAlliance.displayFactionName(membership.nativeFaction))
                    + " [" + (membership.active ? "active" : "ended: " + membership.endReason) + "]."));
            }
            for (KOMEWar.MilitarySupportEnrollment enrollment : war.militarySupportEnrollments) {
                sender.addChatMessage(new ChatComponentText("Stage 4 support: "
                    + KOMEAlliance.displayFactionName(enrollment.supportingFaction) + " for "
                    + KOMEAlliance.displayFactionName(enrollment.nativeFaction) + " = " + enrollment.state
                    + (enrollment.authorizedKing == null ? "" : ", king " + enrollment.authorizedKingName + " (" + enrollment.authorizedKing + ")")
                    + (enrollment.reason.length() == 0 ? "" : ": " + enrollment.reason)));
            }
            for (String warning : contradictions(data, war)) sender.addChatMessage(new ChatComponentText("WARNING: " + warning));
            return;
        }
        if ("rename".equals(action)) {
            requireStaff(sender);
            if (args.length < 3) throw new WrongUsageException("/war rename <warId> <name>");
            KOMEWar war = war(data, args[1]);
            war.displayName = nonempty(join(args, 2), "War name");
            audit(war, sender, "RENAME", war.displayName, now);
            changed(data, "War renamed");
            sender.addChatMessage(new ChatComponentText("Renamed " + war.id + " to " + war.displayName + "."));
            return;
        }
        if ("side".equals(action)) {
            requireStaff(sender);
            handleSide(sender, data, args, now);
            return;
        }
        if ("end".equals(action) || "finalize".equals(action) || "cancel".equals(action)) {
            requireStaff(sender);
            if (args.length < 2) throw new WrongUsageException("/war " + action + " <warId> [reason]");
            KOMEWar war = war(data, args[1]);
            String reason = join(args, 2);
            if ("end".equals(action)) {
                if (!war.isActive()) throw new WrongUsageException("Only an ACTIVE war may enter ENDING.");
                war.status = KOMEWar.ENDING;
                war.endingAtMillis = now;
                war.endingReason = reason;
                audit(war, sender, "END", reason, now);
                KOMEWartimeStewardshipService.beginWarEnding(data, war,
                    "War " + war.id + " entered ENDING" + (reason.length() == 0 ? "" : ": " + reason), now);
                changed(data, "War entered ENDING");
                sender.addChatMessage(new ChatComponentText(display(war) + " is ENDING. New stewardship hiring and offensive movement under this war are disabled."));
            } else if ("finalize".equals(action)) {
                if (!war.isEnding()) throw new WrongUsageException("Only an ENDING war may be finalized.");
                if (!KOMEWartimeStewardshipService.cleanupSafeForFinalize(data, war)) {
                    throw new WrongUsageException("Stewardship withdrawal/demobilization is not yet safe. Preserve unresolved exceptions with an admin-resolution state before finalizing.");
                }
                war.status = KOMEWar.ENDED;
                war.endedAtMillis = now;
                if (reason.length() > 0) war.endingReason = reason;
                audit(war, sender, "FINALIZE", reason, now);
                changed(data, "War finalized");
                sender.addChatMessage(new ChatComponentText(display(war) + " is ENDED."));
            } else {
                if (reason.length() == 0) throw new WrongUsageException("/war cancel <warId> <reason>");
                war.status = KOMEWar.ENDED;
                war.cancelled = true;
                if (war.endingAtMillis == 0L) war.endingAtMillis = now;
                war.endedAtMillis = now;
                war.endingReason = reason;
                audit(war, sender, "CANCEL", reason, now);
                KOMEWartimeStewardshipService.beginWarEnding(data, war, "War record cancelled: " + reason, now);
                changed(data, "War cancelled");
                sender.addChatMessage(new ChatComponentText("Cancelled " + display(war) + ". History and cleanup records were preserved."));
            }
            return;
        }
        throw new WrongUsageException(getCommandUsage(sender));
    }

    private void requireStaff(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, getCommandName())) {
            throw new WrongUsageException("Only administrators may modify war records.");
        }
    }

    private void handleSide(ICommandSender sender, KOMEWorldData data, String[] args, long now) {
        if (args.length < 4) throw new WrongUsageException("/war side rename|add|remove|move ...");
        String action = args[1].toLowerCase(java.util.Locale.ROOT);
        KOMEWar war = war(data, args[2]);
        if (!war.isActive()) throw new WrongUsageException("War sides can be edited only while ACTIVE.");
        if ("rename".equals(action)) {
            if (args.length < 5) throw new WrongUsageException("/war side rename <warId> <1|2> <name>");
            int side = sideNumber(args[3]);
            String name = nonempty(join(args, 4), "Side name");
            if (side == 1) war.sideOneName = name; else war.sideTwoName = name;
            audit(war, sender, "SIDE_RENAME", side + "=" + name, now);
        } else if ("add".equals(action)) {
            if (args.length != 5) throw new WrongUsageException("/war side add <warId> <1|2> <faction>");
            int side = sideNumber(args[3]);
            String faction = faction(args[4]);
            int existingSide = war.sideOf(faction);
            if (existingSide != 0) throw new WrongUsageException(existingSide == side
                ? "That faction is already on this side." : "That faction is on the other side; use /war side move explicitly.");
            war.addFaction(side, faction);
            war.recordMembership(faction, side, "MANUAL", "", sender.getCommandSenderName(), now);
            audit(war, sender, "SIDE_ADD", faction + " to " + side, now);
        } else if ("remove".equals(action)) {
            if (args.length != 4) throw new WrongUsageException("/war side remove <warId> <faction>");
            String faction = faction(args[3]);
            int side = war.sideOf(faction);
            if (side == 0) throw new WrongUsageException("That faction is not in this war.");
            if (war.getSide(side).size() <= 1) throw new WrongUsageException("An active war must retain at least one faction on each side.");
            war.removeFaction(faction);
            war.endMembership(faction, "Removed by operator", now);
            for (KOMEWar.MilitarySupportEnrollment enrollment : war.militarySupportEnrollments) {
                if (faction.equals(enrollment.supportingFaction)) {
                    enrollment.state = "OPERATOR_REMOVED";
                    enrollment.authorizedKing = null;
                    enrollment.authorizedKingName = "";
                    enrollment.reason = "Automatic support membership was explicitly removed by " + sender.getCommandSenderName();
                    enrollment.updatedAtMillis = now;
                }
            }
            audit(war, sender, "SIDE_REMOVE", faction, now);
        } else if ("move".equals(action)) {
            if (args.length != 5) throw new WrongUsageException("/war side move <warId> <faction> <1|2>");
            String faction = faction(args[3]);
            int target = sideNumber(args[4]);
            int source = war.sideOf(faction);
            if (source == 0) throw new WrongUsageException("That faction is not in this war; use side add.");
            if (source == target) throw new WrongUsageException("That faction is already on the requested side.");
            if (war.getSide(source).size() <= 1) throw new WrongUsageException("Moving the last faction would leave an empty war side.");
            war.moveFaction(faction, target);
            war.endMembership(faction, "Moved by operator", now);
            war.recordMembership(faction, target, "MANUAL", "", sender.getCommandSenderName(), now);
            audit(war, sender, "SIDE_MOVE", faction + " to " + target, now);
        } else {
            throw new WrongUsageException("/war side rename|add|remove|move ...");
        }
        refresh(data, "War side membership changed");
        changed(data, "War side " + action);
        sender.addChatMessage(new ChatComponentText("Updated " + display(war) + ": " + side(war, 1) + " versus " + side(war, 2) + "."));
        for (String warning : contradictions(data, war)) sender.addChatMessage(new ChatComponentText("WARNING: " + warning));
    }

    private void refresh(KOMEWorldData data, String reason) {
        KOMEWarService.reconcileAutomaticMilitarySupport(data, System.currentTimeMillis(), reason);
        KOMEAllianceProgressionService.scanQualifyingWarDeployments(data, System.currentTimeMillis());
        KOMEWartimeStewardshipService.revalidateAll(data, System.currentTimeMillis(), reason);
        KOMECommandTroops.revalidateTemporaryControllers(data, System.currentTimeMillis(), reason);
    }

    private void changed(KOMEWorldData data, String action) {
        data.recordAllianceAdminAction("war", action);
        data.markDirty();
        KOMECommandAlliance.sendAllianceRefreshToAll(data);
    }

    private static void audit(KOMEWar war, ICommandSender sender, String action, String detail, long now) {
        war.addAdministrativeEvent(sender.getCommandSenderName(), action, detail, now);
    }

    private static KOMEWar war(KOMEWorldData data, String id) {
        KOMEWar war = data.wars.get(id);
        if (war == null) throw new WrongUsageException("Unknown war: " + id);
        return war;
    }

    private static int sideNumber(String value) {
        if ("1".equals(value)) return 1;
        if ("2".equals(value)) return 2;
        throw new WrongUsageException("War side must be 1 or 2.");
    }

    private static String faction(String value) {
        String key = KOMEAlliance.normalizeFactionKey(value);
        if (key.length() == 0 || KOMEAlliance.findLotrFaction(key) == null) throw new WrongUsageException("Unknown faction: " + value);
        return key;
    }

    private static String nonempty(String value, String label) {
        if (value == null || value.trim().length() == 0) throw new WrongUsageException(label + " cannot be empty.");
        return value.trim();
    }

    private static String join(String[] values, int start) {
        StringBuilder result = new StringBuilder();
        for (int i = start; i < values.length; i++) {
            if (result.length() > 0) result.append(' ');
            result.append(values[i]);
        }
        return result.toString().trim();
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) result.append(", ");
            result.append(KOMEAlliance.displayFactionName(value));
        }
        return result.length() == 0 ? "None" : result.toString();
    }

    private static String display(KOMEWar war) {
        return war.displayName == null || war.displayName.length() == 0 ? war.id : war.displayName + " (" + war.id + ")";
    }

    private static String side(KOMEWar war, int side) {
        return (side == 1 ? war.sideOneName : war.sideTwoName) + " [" + join(new ArrayList<String>(war.getSide(side))) + "]";
    }

    private static String summary(KOMEWar war) {
        String latest = war.tileCaptureHistory.isEmpty() ? "no tile captures"
            : "latest tile " + war.tileCaptureHistory.get(war.tileCaptureHistory.size() - 1).tileId;
        return display(war) + " - " + war.status + ": " + side(war, 1) + " vs " + side(war, 2)
            + "; " + latest + "; stewardship " + war.stewardshipAuthorizations.size() + ".";
    }

    private static List<String> contradictions(KOMEWorldData data, KOMEWar war) {
        List<String> result = new ArrayList<String>();
        for (String faction : war.sideOneFactions) result.addAll(KOMEWarService.contradictoryMemberships(data, faction));
        for (String faction : war.sideTwoFactions) result.addAll(KOMEWarService.contradictoryMemberships(data, faction));
        return result;
    }

    private static void warnContradictions(ICommandSender sender, KOMEWorldData data, String first, String second) {
        for (String warning : KOMEWarService.contradictoryMemberships(data, first)) sender.addChatMessage(new ChatComponentText("WARNING: " + warning));
        for (String warning : KOMEWarService.contradictoryMemberships(data, second)) sender.addChatMessage(new ChatComponentText("WARNING: " + warning));
    }
}
