package com.lotrcharactercreation.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.AppearanceSelectionRules;
import com.lotrcharactercreation.appearance.ServerCustomSkinLibrary;
import com.lotrcharactercreation.appearance.DwarfAppearanceGroup;
import com.lotrcharactercreation.appearance.DwarfAppearanceInitializer;
import com.lotrcharactercreation.appearance.ElfAppearanceGroup;
import com.lotrcharactercreation.appearance.ElfAppearanceInitializer;
import com.lotrcharactercreation.appearance.HobbitAppearanceInitializer;
import com.lotrcharactercreation.appearance.OrcAppearanceInitializer;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.appearance.UrukHaiAppearanceGroup;
import com.lotrcharactercreation.appearance.UrukHaiAppearanceInitializer;
import com.lotrcharactercreation.body.PlayerRaceEyeService;
import com.lotrcharactercreation.body.PlayerRaceSizeService;
import com.lotrcharactercreation.creation.CharacterCreationFlowService;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;
import com.lotrcharactercreation.trait.RaceTraitService;

public class CommandLotrRace extends CommandBase {

    private static final String VALID_CHOICES = "man, elf, dwarf, hobbit, orc, uruk_hai (aliases: uruk, urukhai, uruk-hai)";
    private static final String[] TOP_LEVEL_OPTIONS = createTopLevelOptions();
    private static final String[] APPEARANCE_ACTION_OPTIONS = { "init", "reroll", "sex", "gui" };
    private static final String[] SEX_OPTIONS = { PlayerSex.MALE.getSerializedId(), PlayerSex.FEMALE.getSerializedId(),
        "gui" };
    private static final String[] DWARF_APPEARANCE_GROUP_OPTIONS = { DwarfAppearanceGroup.STANDARD.getSerializedId(),
        DwarfAppearanceGroup.BLUE_MOUNTAINS.getSerializedId() };
    private static final String[] ELF_APPEARANCE_GROUP_OPTIONS = { ElfAppearanceGroup.GALADHRIM.getSerializedId(),
        ElfAppearanceGroup.WOODLAND.getSerializedId(), ElfAppearanceGroup.HIGH_ELF.getSerializedId(),
        ElfAppearanceGroup.DORWINION.getSerializedId() };
    private static final String[] URUK_HAI_APPEARANCE_GROUP_OPTIONS = {
        UrukHaiAppearanceGroup.ISENGARD_URUK_HAI.getSerializedId(),
        UrukHaiAppearanceGroup.MORDOR_BLACK_URUK.getSerializedId(),
        UrukHaiAppearanceGroup.GUNDABAD_URUK.getSerializedId() };

    @Override
    public String getCommandName() {
        return "lotrrace";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/lotrrace [race|body|faction [faction]|appearance [gui|sex <male|female|gui>|init [group]|reroll [group]]]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] arguments) {
        if (arguments.length == 0) {
            return null;
        }
        if (arguments.length == 1) {
            return getListOfStringsMatchingLastWord(arguments, TOP_LEVEL_OPTIONS);
        }
        if (arguments[0].equalsIgnoreCase("faction")) {
            if (arguments.length == 2 && sender instanceof EntityPlayerMP) {
                return getListOfStringsMatchingLastWord(
                    arguments,
                    getAllowedFactionIds(PlayerRaceData.getRace((EntityPlayerMP) sender)));
            }
            return null;
        }
        if (!arguments[0].equalsIgnoreCase("appearance")) {
            return null;
        }
        if (arguments.length == 2) {
            return getListOfStringsMatchingLastWord(arguments, APPEARANCE_ACTION_OPTIONS);
        }
        if (arguments.length != 3 || !(sender instanceof EntityPlayerMP)) {
            return null;
        }

        PlayerRace race = PlayerRaceData.getRace((EntityPlayerMP) sender);
        if (arguments[1].equalsIgnoreCase("sex")) {
            return supportsSelectableSex(race) ? getListOfStringsMatchingLastWord(arguments, SEX_OPTIONS) : null;
        }
        if (arguments[1].equalsIgnoreCase("init") || arguments[1].equalsIgnoreCase("reroll")) {
            String[] groupOptions = getAppearanceGroupOptions(race);
            return groupOptions == null ? null : getListOfStringsMatchingLastWord(arguments, groupOptions);
        }

        return null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] arguments) {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);

        if (arguments.length == 0) {
            reportStatus(sender, player);
            return;
        }

        if (arguments[0].equalsIgnoreCase("appearance")) {
            processAppearanceCommand(sender, player, arguments);
            return;
        }

        if (arguments[0].equalsIgnoreCase("faction")) {
            processFactionCommand(sender, player, arguments);
            return;
        }

        if (arguments.length == 1 && arguments[0].equalsIgnoreCase("body")) {
            reportBodyStatus(sender, player);
            return;
        }

        if (arguments.length > 1) {
            sendUsageError(sender);
            return;
        }

        PlayerRace race = PlayerRace.fromCommandArgument(arguments[0]);
        if (race == null) {
            sendError(sender, "Invalid race '" + arguments[0] + "'. Valid choices: " + VALID_CHOICES + ".");
            return;
        }

        PlayerRaceData.setRace(player, race);
        RaceTraitService.refreshDerivedAttributes(player);
        PlayerRaceSizeService.applyStoredRaceSize(player);
        PlayerRaceEyeService.applyStoredServerEyeHeight(player);
        ModNetwork.sendPlayerAppearanceToTrackingAndSelf(player);
        sender.addChatMessage(new ChatComponentText("Your race is now " + race.getDisplayName() + "."));
    }

    private static void processAppearanceCommand(ICommandSender sender, EntityPlayerMP player, String[] arguments) {
        if (arguments.length == 1) {
            reportStatus(sender, player);
            return;
        }

        if (arguments[1].equalsIgnoreCase("gui")) {
            if (arguments.length == 2) {
                ModNetwork.sendAppearanceSelectionOpen(player);
            } else {
                sendAppearanceUsageError(sender);
            }
            return;
        }

        if (arguments[1].equalsIgnoreCase("sex")) {
            if (arguments.length == 3) {
                if (arguments[2].equalsIgnoreCase("gui")) {
                    ModNetwork.sendSexSelectionOpen(player);
                } else {
                    setAppearanceSex(sender, player, arguments[2]);
                }
            } else {
                sendAppearanceUsageError(sender);
            }
            return;
        }

        boolean reroll;
        if (arguments[1].equalsIgnoreCase("init")) {
            reroll = false;
        } else if (arguments[1].equalsIgnoreCase("reroll")) {
            reroll = true;
        } else {
            sendAppearanceUsageError(sender);
            return;
        }

        PlayerRace race = PlayerRaceData.getRace(player);
        if (race == PlayerRace.ELF && arguments.length == 3) {
            initializeElfAppearance(sender, player, arguments[2], reroll);
        } else if (race == PlayerRace.DWARF && arguments.length == 3) {
            initializeDwarfAppearance(sender, player, arguments[2], reroll);
        } else if (race == PlayerRace.HOBBIT && arguments.length == 2) {
            initializeHobbitAppearance(sender, player, reroll);
        } else if (race == PlayerRace.ORC && arguments.length == 2) {
            initializeOrcAppearance(sender, player, reroll);
        } else if (race == PlayerRace.URUK_HAI && arguments.length == 3) {
            initializeUrukHaiAppearance(sender, player, arguments[2], reroll);
        } else {
            sendAppearanceUsageError(sender);
        }
    }

    private static void setAppearanceSex(ICommandSender sender, EntityPlayerMP player, String argument) {
        PlayerRace race = PlayerRaceData.getRace(player);
        if (race == PlayerRace.ORC) {
            sendError(sender, "Orc appearances use sex=none; male/female is not used.");
            return;
        }
        if (race == PlayerRace.URUK_HAI) {
            sendError(sender, "Uruk-hai appearances use sex=none; male/female is not used.");
            return;
        }
        if (race != PlayerRace.MAN && race != PlayerRace.ELF && race != PlayerRace.DWARF && race != PlayerRace.HOBBIT) {
            sendError(sender, "Appearance testing currently requires race Man, Elf, Dwarf, or Hobbit.");
            return;
        }

        PlayerSex sex = PlayerSex.fromCommandArgument(argument);
        if (sex != PlayerSex.MALE && sex != PlayerSex.FEMALE) {
            sendError(
                sender,
                "Invalid " + race.getDisplayName() + " sex '" + argument + "'. Valid choices: male, female.");
            return;
        }

        String oldPresetId = PlayerRaceData.getAppearancePresetId(player);
        PlayerRaceData.setSex(player, sex);
        if (!AppearancePresetRegistry.isPresetValid(
            ServerCustomSkinLibrary.getInstance().getCurrentCatalog(), race, sex, oldPresetId)) {
            PlayerRaceData.clearAppearancePreset(player);
            PlayerRaceData.setAppearanceInitialized(player, false);
        }
        ModNetwork.sendPlayerAppearanceToTrackingAndSelf(player);

        sender.addChatMessage(
            new ChatComponentText(race.getDisplayName() + " appearance sex is now " + sex.getDisplayName() + "."));
        reportAppearanceStatus(sender, player);
    }

    private static void processFactionCommand(ICommandSender sender, EntityPlayerMP player, String[] arguments) {
        PlayerRace race = PlayerRaceData.getRace(player);
        if (arguments.length == 1) {
            StartingFaction faction = PlayerRaceData.getStartingFaction(player);
            sender.addChatMessage(
                new ChatComponentText(
                    "Starting faction (development): " + faction.getSerializedId()
                        + " ("
                        + faction.getDisplayName()
                        + "), factionSelectionComplete="
                        + PlayerRaceData.isFactionSelectionComplete(player)
                        + "."));
            return;
        }

        if (arguments.length != 2) {
            sendFactionUsageError(sender, race);
            return;
        }

        StartingFaction faction = StartingFaction.findBySerializedId(arguments[1].toLowerCase(Locale.ROOT));
        if (faction == null || !faction.isAllowedFor(race)) {
            sendError(
                sender,
                "Invalid starting faction '" + arguments[1]
                    + "' for "
                    + race.getDisplayName()
                    + ". Valid choices: "
                    + joinFactionIds(race)
                    + ".");
            return;
        }

        PlayerRaceData.setStartingFaction(player, faction);
        PlayerRaceData.setFactionSelectionComplete(player, true);
        CharacterCreationFlowService.requireAppearanceConfirmation(player);
        ModNetwork.sendPlayerAppearanceToTrackingAndSelf(player);
        sender.addChatMessage(
            new ChatComponentText(
                "Starting faction (development) is now " + faction.getDisplayName()
                    + ". No allegiance or teleport was applied."));
    }

    private static void initializeElfAppearance(ICommandSender sender, EntityPlayerMP player, String groupArgument,
        boolean reroll) {
        if (PlayerRaceData.getRace(player) != PlayerRace.ELF) {
            sendError(sender, "Elf appearance testing requires race Elf. Use /lotrrace elf first.");
            return;
        }

        PlayerSex sex = PlayerRaceData.getSex(player);
        if (sex != PlayerSex.MALE && sex != PlayerSex.FEMALE) {
            sendError(sender, "Set an Elf sex first with /lotrrace appearance sex <male|female>.");
            return;
        }

        ElfAppearanceGroup group = ElfAppearanceGroup.fromCommandArgument(groupArgument);
        if (group == null) {
            sendError(
                sender,
                "Invalid Elf appearance group '" + groupArgument
                    + "'. Valid choices: galadhrim, woodland (alias: wood), high_elf (alias: high), dorwinion.");
            return;
        }

        AppearancePreset preset = reroll ? ElfAppearanceInitializer.reroll(player, sex, group)
            : ElfAppearanceInitializer.initializeIfNeeded(player, sex, group);
        ModNetwork.sendPlayerAppearanceToTrackingAndSelf(player);
        sender.addChatMessage(
            new ChatComponentText((reroll ? "Rerolled" : "Initialized") + " Elf appearance: " + preset.getId() + "."));
        reportAppearanceStatus(sender, player);
    }

    private static void initializeDwarfAppearance(ICommandSender sender, EntityPlayerMP player, String groupArgument,
        boolean reroll) {
        if (PlayerRaceData.getRace(player) != PlayerRace.DWARF) {
            sendError(sender, "Dwarf appearance testing requires race Dwarf. Use /lotrrace dwarf first.");
            return;
        }

        PlayerSex sex = PlayerRaceData.getSex(player);
        if (sex != PlayerSex.MALE && sex != PlayerSex.FEMALE) {
            sendError(sender, "Set a Dwarf sex first with /lotrrace appearance sex <male|female>.");
            return;
        }

        DwarfAppearanceGroup group = DwarfAppearanceGroup.fromCommandArgument(groupArgument);
        if (group == null) {
            sendError(
                sender,
                "Invalid Dwarf appearance group '" + groupArgument
                    + "'. Valid choices: standard, blue_mountains (alias: blue).");
            return;
        }

        AppearancePreset preset = reroll ? DwarfAppearanceInitializer.reroll(player, sex, group)
            : DwarfAppearanceInitializer.initializeIfNeeded(player, sex, group);
        ModNetwork.sendPlayerAppearanceToTrackingAndSelf(player);
        sender.addChatMessage(
            new ChatComponentText(
                (reroll ? "Rerolled" : "Initialized") + " Dwarf appearance: " + preset.getId() + "."));
        reportAppearanceStatus(sender, player);
    }

    private static void initializeHobbitAppearance(ICommandSender sender, EntityPlayerMP player, boolean reroll) {
        if (PlayerRaceData.getRace(player) != PlayerRace.HOBBIT) {
            sendError(sender, "Hobbit appearance testing requires race Hobbit. Use /lotrrace hobbit first.");
            return;
        }

        PlayerSex sex = PlayerRaceData.getSex(player);
        if (sex != PlayerSex.MALE && sex != PlayerSex.FEMALE) {
            sendError(sender, "Set a Hobbit sex first with /lotrrace appearance sex <male|female>.");
            return;
        }

        AppearancePreset preset = reroll ? HobbitAppearanceInitializer.reroll(player, sex)
            : HobbitAppearanceInitializer.initializeIfNeeded(player, sex);
        ModNetwork.sendPlayerAppearanceToTrackingAndSelf(player);
        sender.addChatMessage(
            new ChatComponentText(
                (reroll ? "Rerolled" : "Initialized") + " Hobbit appearance: " + preset.getId() + "."));
        reportAppearanceStatus(sender, player);
    }

    private static void initializeOrcAppearance(ICommandSender sender, EntityPlayerMP player, boolean reroll) {
        if (PlayerRaceData.getRace(player) != PlayerRace.ORC) {
            sendError(sender, "Orc appearance testing requires race Orc. Use /lotrrace orc first.");
            return;
        }

        AppearancePreset preset = reroll ? OrcAppearanceInitializer.reroll(player)
            : OrcAppearanceInitializer.initializeIfNeeded(player);
        ModNetwork.sendPlayerAppearanceToTrackingAndSelf(player);
        sender.addChatMessage(
            new ChatComponentText((reroll ? "Rerolled" : "Initialized") + " Orc appearance: " + preset.getId() + "."));
        reportAppearanceStatus(sender, player);
    }

    private static void initializeUrukHaiAppearance(ICommandSender sender, EntityPlayerMP player, String groupArgument,
        boolean reroll) {
        if (PlayerRaceData.getRace(player) != PlayerRace.URUK_HAI) {
            sendError(sender, "Uruk-hai appearance testing requires race Uruk-hai. Use /lotrrace uruk_hai first.");
            return;
        }

        UrukHaiAppearanceGroup group = UrukHaiAppearanceGroup.fromCommandArgument(groupArgument);
        if (group == null) {
            sendError(
                sender,
                "Invalid Uruk-hai appearance group '" + groupArgument
                    + "'. Valid choices: isengard_uruk_hai (aliases: isengard, uruk), "
                    + "mordor_black_uruk (aliases: mordor, black_uruk, black), "
                    + "gundabad_uruk (alias: gundabad).");
            return;
        }

        AppearancePreset preset = reroll ? UrukHaiAppearanceInitializer.reroll(player, group)
            : UrukHaiAppearanceInitializer.initializeIfNeeded(player, group);
        ModNetwork.sendPlayerAppearanceToTrackingAndSelf(player);
        sender.addChatMessage(
            new ChatComponentText(
                (reroll ? "Rerolled" : "Initialized") + " Uruk-hai appearance: " + preset.getId() + "."));
        reportAppearanceStatus(sender, player);
    }

    private static void reportStatus(ICommandSender sender, EntityPlayerMP player) {
        PlayerRace race = PlayerRaceData.getRace(player);
        sender.addChatMessage(new ChatComponentText("Your current race is " + race.getDisplayName() + "."));
        reportAppearanceStatus(sender, player);
    }

    private static void reportAppearanceStatus(ICommandSender sender, EntityPlayerMP player) {
        PlayerSex sex = PlayerRaceData.getSex(player);
        String presetId = PlayerRaceData.getAppearancePresetId(player);
        sender.addChatMessage(
            new ChatComponentText(
                "Appearance (development): sex=" + (sex == null ? "missing/invalid" : sex.getSerializedId())
                    + ", appearancePreset="
                    + (presetId == null ? "missing" : presetId)
                    + ", appearanceInitialized="
                    + PlayerRaceData.isAppearanceInitialized(player)
                    + "."));
    }

    private static void reportBodyStatus(ICommandSender sender, EntityPlayerMP player) {
        PlayerRace race = PlayerRaceData.getRace(player);
        double boundingBoxWidth = player.boundingBox.maxX - player.boundingBox.minX;
        double boundingBoxHeight = player.boundingBox.maxY - player.boundingBox.minY;
        sender.addChatMessage(
            new ChatComponentText(
                String.format(
                    Locale.ROOT,
                    "Body (development): race=%s, width=%.2f, height=%.2f, boundingBoxWidth=%.2f, boundingBoxHeight=%.2f, targetEyeHeight=%.3f, eyeHeight=%.3f, yOffset=%.3f.",
                    race.getSerializedId(),
                    player.width,
                    player.height,
                    boundingBoxWidth,
                    boundingBoxHeight,
                    PlayerRaceEyeService.getDesiredServerEyeHeight(player, race),
                    player.eyeHeight,
                    player.yOffset)));
    }

    private static void sendUsageError(ICommandSender sender) {
        sendError(
            sender,
            "Usage: /lotrrace [race|body|faction [faction]]. Valid races: " + VALID_CHOICES
                + ". Appearance testing: /lotrrace appearance.");
    }

    private static void sendFactionUsageError(ICommandSender sender, PlayerRace race) {
        sendError(sender, "Usage: /lotrrace faction [" + joinFactionIds(race) + "].");
    }

    private static void sendAppearanceUsageError(ICommandSender sender) {
        sendError(
            sender,
            "Usage: /lotrrace appearance gui. Sex GUI: /lotrrace appearance sex gui. Man: /lotrrace appearance [gui|sex <male|female>]. Elf: /lotrrace appearance [sex <male|female>|init <galadhrim|woodland|high_elf|dorwinion>|reroll <galadhrim|woodland|high_elf|dorwinion>]. Dwarf: /lotrrace appearance [sex <male|female>|init <standard|blue_mountains>|reroll <standard|blue_mountains>]. Hobbit: /lotrrace appearance [sex <male|female>|init|reroll]. Orc: /lotrrace appearance [init|reroll]; Orc uses sex=none. Uruk-hai: /lotrrace appearance [init <isengard_uruk_hai|mordor_black_uruk|gundabad_uruk>|reroll <isengard_uruk_hai|mordor_black_uruk|gundabad_uruk>]; Uruk-hai uses sex=none.");
    }

    private static void sendError(ICommandSender sender, String message) {
        ChatComponentText error = new ChatComponentText(message);
        error.getChatStyle()
            .setColor(EnumChatFormatting.RED);
        sender.addChatMessage(error);
    }

    private static String[] createTopLevelOptions() {
        PlayerRace[] races = PlayerRace.values();
        String[] options = new String[races.length + 3];
        for (int index = 0; index < races.length; index++) {
            options[index] = races[index].getSerializedId();
        }
        options[races.length] = "appearance";
        options[races.length + 1] = "body";
        options[races.length + 2] = "faction";
        return options;
    }

    private static boolean supportsSelectableSex(PlayerRace race) {
        return AppearanceSelectionRules.supportsSelectableSex(race);
    }

    private static String[] getAllowedFactionIds(PlayerRace race) {
        List<StartingFaction> factions = StartingFaction.getAllowedForRace(race);
        List<String> factionIds = new ArrayList<>(factions.size());
        for (StartingFaction faction : factions) {
            factionIds.add(faction.getSerializedId());
        }
        return factionIds.toArray(new String[factionIds.size()]);
    }

    private static String joinFactionIds(PlayerRace race) {
        String[] ids = getAllowedFactionIds(race);
        StringBuilder choices = new StringBuilder();
        for (String id : ids) {
            if (choices.length() > 0) {
                choices.append(", ");
            }
            choices.append(id);
        }
        return choices.toString();
    }

    private static String[] getAppearanceGroupOptions(PlayerRace race) {
        if (race == PlayerRace.DWARF) {
            return DWARF_APPEARANCE_GROUP_OPTIONS;
        }
        if (race == PlayerRace.ELF) {
            return ELF_APPEARANCE_GROUP_OPTIONS;
        }
        if (race == PlayerRace.URUK_HAI) {
            return URUK_HAI_APPEARANCE_GROUP_OPTIONS;
        }
        return null;
    }
}
