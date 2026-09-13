package kome.common.command;

import java.util.UUID;
import kome.common.KOMEReflection;
import kome.common.data.KOMEAlliance;
import kome.common.data.KOMERulerAuthorization;
import kome.common.data.KOMEWar;
import kome.common.data.KOMEWarSeasonState;
import kome.common.data.KOMEWarService;
import kome.common.data.KOMEWorldData;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;

/** Player-facing season status and explicit Finale action; lifecycle repair remains staff-only. */
public final class KOMECommandSeason extends CommandBase {
    public String getCommandName() { return "season"; }
    public String getCommandUsage(ICommandSender sender) { return "/season status | finale | prewar | reset | complete-reset | repair <maintenance|pre_war|war|finale|reset>"; }
    public int getRequiredPermissionLevel() { return 0; }

    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length != 1 && args.length != 2) throw new WrongUsageException(getCommandUsage(sender));
        KOMEWorldData data = KOMEWorldData.get(sender.getEntityWorld());
        String action = args[0].toLowerCase(java.util.Locale.ROOT);
        long now = System.currentTimeMillis();
        if ("status".equals(action)) { status(sender, data, now); return; }
        KOMEWarSeasonState.TransitionResult result;
        if ("finale".equals(action)) {
            if (!(sender instanceof EntityPlayer)) throw new WrongUsageException("Finale must be triggered by an eligible player.");
            EntityPlayer player = (EntityPlayer) sender;
            UUID actor = KOMEReflection.getEntityUUID(player);
            String faction = KOMEAlliance.normalizeFactionKey(data.getPlayerFactionKey(actor));
            boolean eligible = faction.length() > 0 && KOMERulerAuthorization.canActAsRuler(data, faction, actor) && hasActiveWar(data, faction);
            result = data.warSeason.triggerFinale(actor, sender.getCommandSenderName(), eligible, now);
        } else {
            requireStaff(sender);
            if ("prewar".equals(action)) result = data.warSeason.beginPreWar(now);
            else if ("reset".equals(action)) result = data.warSeason.beginReset(now);
            else if ("complete-reset".equals(action)) result = data.warSeason.completeReset(now);
            else if ("repair".equals(action) && args.length == 2) result = repair(data, args[1], now);
            else throw new WrongUsageException(getCommandUsage(sender));
        }
        if (!result.allowed) throw new WrongUsageException(result.reason);
        kome.common.data.KOMEAuditService.record(data, now, "SEASON", action.toUpperCase(java.util.Locale.ROOT),
            sender.getCommandSenderName(), "season:" + data.warSeason.seasonId,
            "Season transition completed", data.warSeason.phase.name());
        if ("finale".equals(action)) kome.common.data.KOMENotificationService.timeSensitive(
            "Season " + data.warSeason.seasonId + " entered Finale.");
        data.recordAllianceAdminAction("season", action + " by " + sender.getCommandSenderName());
        data.markDirty();
        sender.addChatMessage(new ChatComponentText("Season " + data.warSeason.seasonId + " is now " + data.warSeason.phase + "."));
        status(sender, data, now);
    }

    private static KOMEWarSeasonState.TransitionResult repair(KOMEWorldData data, String value, long now) {
        try { return data.warSeason.repair(KOMEWarSeasonState.Phase.valueOf(value.toUpperCase(java.util.Locale.ROOT)), now); }
        catch (IllegalArgumentException e) { throw new WrongUsageException("Unknown season phase: " + value); }
    }
    private static boolean hasActiveWar(KOMEWorldData data, String faction) {
        for (KOMEWar war : KOMEWarService.sortedWars(data)) if (war.isActive() && war.sideOf(faction) != 0) return true;
        return false;
    }
    private static void requireStaff(ICommandSender sender) {
        if (!sender.canCommandSenderUseCommand(2, "season")) throw new WrongUsageException("Only administrators may change or repair season lifecycle state.");
    }
    private static void status(ICommandSender sender, KOMEWorldData data, long now) {
        KOMEWarSeasonState state = data.warSeason;
        String countdown = state.phase == KOMEWarSeasonState.Phase.WAR && state.minimumWarEndMillis >= 0L
            ? (now >= state.minimumWarEndMillis ? "Finale may now be explicitly triggered by an eligible player."
                : "Finale eligibility opens in " + (state.minimumWarEndMillis - now) + " ms.")
            : state.minimumWarEndMillis < 0L && state.phase == KOMEWarSeasonState.Phase.WAR
                ? "Finale is unavailable until the minimum War duration is configured." : "";
        sender.addChatMessage(new ChatComponentText("Season " + state.seasonId + ": " + state.phase
            + "; population payout " + (state.isPopulationPayoutEnabled() ? "ENABLED" : "FROZEN")
            + "; reset " + state.resetStatus + "."));
        if (countdown.length() > 0) sender.addChatMessage(new ChatComponentText(countdown));
        if (state.finaleTriggerTimeMillis >= 0L) sender.addChatMessage(new ChatComponentText("Finale triggered by "
            + state.finaleTriggerActorName + " at " + state.finaleTriggerTimeMillis + "."));
    }
}
