package com.lotrcharactercreation.appearance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.race.PlayerRace;

/**
 * Central race/faction rules for selecting an appearance preset. Both the
 * client preview and server confirmation use these rules, while the server
 * remains authoritative.
 */
public final class AppearanceSelectionRules {

    private AppearanceSelectionRules() {}

    public static PlayerSex getSelectionSex(PlayerRace race, PlayerSex storedSex) {
        if (race == PlayerRace.ORC || race == PlayerRace.URUK_HAI) {
            return PlayerSex.NONE;
        }
        return AppearancePresetRegistry.isSexValidForRace(race, storedSex) ? storedSex : null;
    }

    public static boolean supportsSelectableSex(PlayerRace race) {
        return race == PlayerRace.MAN || race == PlayerRace.DWARF
            || race == PlayerRace.ELF
            || race == PlayerRace.HOBBIT;
    }

    public static List<AppearancePreset> getCandidates(PlayerRace race, PlayerSex sex, StartingFaction faction) {
        return getCandidates(AppearancePresetRegistry.getBuiltInCatalog(), race, sex, faction);
    }

    public static List<AppearancePreset> getCandidates(AppearancePresetCatalog catalog, PlayerRace race,
        PlayerSex sex, StartingFaction faction) {
        if (race == null || faction == null
            || !faction.isAllowedFor(race)
            || !AppearancePresetRegistry.isSexValidForRace(race, sex)) {
            return Collections.emptyList();
        }

        if (race == PlayerRace.MAN) {
            return getManCandidates(catalog, sex, faction);
        }

        if (race == PlayerRace.HOBBIT) {
            return catalog.getPresets(race, sex);
        }

        if (faction == StartingFaction.WANDERER
            && (race == PlayerRace.DWARF || race == PlayerRace.ELF || race == PlayerRace.URUK_HAI)) {
            return catalog.getPresets(race, sex);
        }

        String groupId = getAllowedGroupId(race, faction);
        return groupId == null ? Collections.<AppearancePreset>emptyList()
            : catalog.getPresets(race, sex, groupId);
    }

    public static boolean isPresetAllowed(PlayerRace race, PlayerSex sex, StartingFaction faction, String presetId) {
        return isPresetAllowed(AppearancePresetRegistry.getBuiltInCatalog(), race, sex, faction, presetId);
    }

    public static boolean isPresetAllowed(AppearancePresetCatalog catalog, PlayerRace race, PlayerSex sex,
        StartingFaction faction, String presetId) {
        if (!AppearancePresetRegistry.isPresetValid(catalog, race, sex, presetId)) {
            return false;
        }

        for (AppearancePreset candidate : getCandidates(catalog, race, sex, faction)) {
            if (candidate.getId()
                .equals(presetId)) {
                return true;
            }
        }
        return false;
    }

    public static String getAllowedGroupId(PlayerRace race, StartingFaction faction) {
        if (race == null || faction == null || !faction.isAllowedFor(race)) {
            return null;
        }

        switch (race) {
            case MAN:
                return getManGroupId(faction);
            case DWARF:
                return getDwarfGroupId(faction);
            case ELF:
                return getElfGroupId(faction);
            case ORC:
                return OrcAppearanceGroup.COMMON_ORC.getSerializedId();
            case URUK_HAI:
                return getUrukHaiGroupId(faction);
            default:
                return null;
        }
    }

    public static String getGroupDisplayName(PlayerRace race, StartingFaction faction) {
        String groupId = getAllowedGroupId(race, faction);
        if (groupId == null) {
            return null;
        }

        if (race == PlayerRace.MAN) {
            if (faction == StartingFaction.WANDERER) {
                return null;
            }
            ManAppearanceGroup group = ManAppearanceGroup.findBySerializedId(groupId);
            return group == null ? null : group.getDisplayName();
        }
        if (race == PlayerRace.DWARF) {
            DwarfAppearanceGroup group = DwarfAppearanceGroup.fromCommandArgument(groupId);
            return group == null ? null : group.getDisplayName();
        }
        if (race == PlayerRace.ELF) {
            ElfAppearanceGroup group = ElfAppearanceGroup.findBySerializedId(groupId);
            return group == null ? null : group.getDisplayName();
        }
        if (race == PlayerRace.ORC) {
            OrcAppearanceGroup group = OrcAppearanceGroup.findBySerializedId(groupId);
            return group == null ? null : group.getDisplayName();
        }
        if (race == PlayerRace.URUK_HAI) {
            UrukHaiAppearanceGroup group = UrukHaiAppearanceGroup.findBySerializedId(groupId);
            return group == null ? null : group.getDisplayName();
        }
        return null;
    }

    private static List<AppearancePreset> getManCandidates(AppearancePresetCatalog catalog, PlayerSex sex,
        StartingFaction faction) {
        Map<String, AppearancePreset> candidates = new LinkedHashMap<String, AppearancePreset>();
        if (faction == StartingFaction.WANDERER) {
            addPresets(candidates, catalog.getPresets(PlayerRace.MAN, sex));
        } else {
            String groupId = getManGroupId(faction);
            if (groupId != null) {
                addPresets(candidates, catalog.getPresets(PlayerRace.MAN, sex, groupId));
            }
        }

        AppearancePreset accountPreset = AppearancePresetRegistry.getManAccountPreset(catalog, sex);
        if (accountPreset != null) {
            candidates.put(accountPreset.getId(), accountPreset);
        }
        return Collections.unmodifiableList(new ArrayList<AppearancePreset>(candidates.values()));
    }

    private static void addPresets(Map<String, AppearancePreset> destination, List<AppearancePreset> presets) {
        for (AppearancePreset preset : presets) {
            destination.put(preset.getId(), preset);
        }
    }

    private static String getManGroupId(StartingFaction faction) {
        ManAppearanceGroup group = ManAppearanceGroup.findBySerializedId(faction.getSerializedId());
        return group == null ? null : group.getSerializedId();
    }

    private static String getElfGroupId(StartingFaction faction) {
        switch (faction) {
            case HIGH_ELVES:
                return ElfAppearanceGroup.HIGH_ELF.getSerializedId();
            case WOODLAND_REALM:
                return ElfAppearanceGroup.WOODLAND.getSerializedId();
            case DORWINION:
                return ElfAppearanceGroup.DORWINION.getSerializedId();
            case LOTHLORIEN:
                return ElfAppearanceGroup.GALADHRIM.getSerializedId();
            default:
                return null;
        }
    }

    private static String getDwarfGroupId(StartingFaction faction) {
        switch (faction) {
            case BLUE_MOUNTAINS:
                return DwarfAppearanceGroup.BLUE_MOUNTAINS.getSerializedId();
            case DUNLAND:
            case DURINS_FOLK:
                return DwarfAppearanceGroup.STANDARD.getSerializedId();
            default:
                return null;
        }
    }

    private static String getUrukHaiGroupId(StartingFaction faction) {
        switch (faction) {
            case MORDOR:
                return UrukHaiAppearanceGroup.MORDOR_BLACK_URUK.getSerializedId();
            case GUNDABAD:
                return UrukHaiAppearanceGroup.GUNDABAD_URUK.getSerializedId();
            case ISENGARD:
                return UrukHaiAppearanceGroup.ISENGARD_URUK_HAI.getSerializedId();
            default:
                return null;
        }
    }
}
