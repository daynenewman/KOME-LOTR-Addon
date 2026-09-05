package com.lotrcharactercreation.appearance;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.race.PlayerRace;

public final class AppearancePresetRegistry {

    public static final String MAN_MINECRAFT_SKIN_MALE_ID = "man_minecraft_skin_m";
    public static final String MAN_MINECRAFT_SKIN_FEMALE_ID = "man_minecraft_skin_f";

    private static final String LOTR_TEXTURE_NAMESPACE = "lotr";
    private static Map<String, AppearancePreset> presets = Collections.emptyMap();
    private static boolean initialized;

    private AppearancePresetRegistry() {}

    public static synchronized void initialize(File customSkinRoot, Logger logger) {
        if (initialized) {
            return;
        }

        Map<String, AppearancePreset> initializedPresets = createPresets();
        List<AppearancePreset> externalPresets = ExternalAppearancePresetScanner.scan(customSkinRoot, logger);
        int registeredExternalPresets = 0;
        for (AppearancePreset externalPreset : externalPresets) {
            if (initializedPresets.containsKey(externalPreset.getId())) {
                if (logger != null) {
                    logger.warn(
                        "Skipping external appearance preset because its ID collides with a built-in preset: "
                            + externalPreset.getId());
                }
                continue;
            }
            initializedPresets.put(externalPreset.getId(), externalPreset);
            registeredExternalPresets++;
        }

        presets = Collections.unmodifiableMap(initializedPresets);
        initialized = true;
        if (logger != null) {
            logger.info("Registered " + registeredExternalPresets + " external appearance preset(s)");
        }
    }

    public static AppearancePreset findById(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        return getInitializedPresets().get(id);
    }

    public static List<AppearancePreset> getPresets(PlayerRace race, PlayerSex sex) {
        return getPresets(race, sex, null);
    }

    public static List<AppearancePreset> getPresets(PlayerRace race, PlayerSex sex, String groupId) {
        if (!isSexValidForRace(race, sex)) {
            return Collections.emptyList();
        }

        List<AppearancePreset> matches = new ArrayList<AppearancePreset>();
        for (AppearancePreset preset : getInitializedPresets().values()) {
            if (preset.getRace() == race && preset.getSex() == sex
                && (groupId == null || groupId.equals(preset.getGroupId()))) {
                matches.add(preset);
            }
        }

        return Collections.unmodifiableList(matches);
    }

    public static List<AppearancePreset> getDwarfPresets(PlayerSex sex, DwarfAppearanceGroup group) {
        if (!isSexValidForRace(PlayerRace.DWARF, sex) || group == null) {
            return Collections.emptyList();
        }

        return getPresets(PlayerRace.DWARF, sex, group.getSerializedId());
    }

    public static AppearancePreset getManAccountPreset(PlayerSex sex) {
        if (sex == PlayerSex.MALE) {
            return findById(MAN_MINECRAFT_SKIN_MALE_ID);
        }
        if (sex == PlayerSex.FEMALE) {
            return findById(MAN_MINECRAFT_SKIN_FEMALE_ID);
        }
        return null;
    }

    public static List<AppearancePreset> getOrcPresets(OrcAppearanceGroup group) {
        if (group == null) {
            return Collections.emptyList();
        }

        return getPresets(PlayerRace.ORC, PlayerSex.NONE, group.getSerializedId());
    }

    public static List<AppearancePreset> getUrukHaiPresets(UrukHaiAppearanceGroup group) {
        if (group == null) {
            return Collections.emptyList();
        }

        return getPresets(PlayerRace.URUK_HAI, PlayerSex.NONE, group.getSerializedId());
    }

    public static boolean isSexValidForRace(PlayerRace race, PlayerSex sex) {
        if (race == null || sex == null) {
            return false;
        }

        switch (race) {
            case MAN:
            case ELF:
            case DWARF:
            case HOBBIT:
                return sex == PlayerSex.MALE || sex == PlayerSex.FEMALE;
            case ORC:
            case URUK_HAI:
                return sex == PlayerSex.NONE;
            default:
                return false;
        }
    }

    public static boolean isPresetValid(PlayerRace race, PlayerSex sex, String presetId) {
        AppearancePreset preset = findById(presetId);
        if (preset == null || preset.getRace() != race || preset.getSex() != sex) {
            return false;
        }

        if (race == PlayerRace.MAN) {
            if (preset.getSourceType() == AppearanceSourceType.MINECRAFT_ACCOUNT) {
                return preset.getGroupId() == null;
            }
            return isLotrCharacterTexture(preset) && ManAppearanceGroup.findBySerializedId(preset.getGroupId()) != null;
        }
        if (!isLotrCharacterTexture(preset)) {
            return false;
        }
        if (race == PlayerRace.ELF) {
            return ElfAppearanceGroup.findBySerializedId(preset.getGroupId()) != null;
        }
        if (race == PlayerRace.DWARF) {
            return isDwarfGroupId(preset.getGroupId());
        }
        if (race == PlayerRace.HOBBIT) {
            return preset.getGroupId() == null;
        }
        if (race == PlayerRace.ORC) {
            return OrcAppearanceGroup.findBySerializedId(preset.getGroupId()) != null;
        }
        if (race == PlayerRace.URUK_HAI) {
            return UrukHaiAppearanceGroup.findBySerializedId(preset.getGroupId()) != null;
        }

        return false;
    }

    public static boolean isAppearanceValid(PlayerRace race, PlayerSex sex, String presetId) {
        if (!isSexValidForRace(race, sex)) {
            return false;
        }

        if (race == PlayerRace.MAN || race == PlayerRace.ELF
            || race == PlayerRace.DWARF
            || race == PlayerRace.HOBBIT
            || race == PlayerRace.ORC
            || race == PlayerRace.URUK_HAI) {
            return isPresetValid(race, sex, presetId);
        }

        if (presetId == null || presetId.isEmpty()) {
            return true;
        }

        return isPresetValid(race, sex, presetId);
    }

    public static boolean isDwarfPresetValid(String presetId, PlayerSex sex, DwarfAppearanceGroup group) {
        AppearancePreset preset = findById(presetId);
        return preset != null && preset.getRace() == PlayerRace.DWARF
            && preset.getSex() == sex
            && group != null
            && group.getSerializedId()
                .equals(preset.getGroupId());
    }

    public static boolean isElfPresetValid(String presetId, PlayerSex sex, ElfAppearanceGroup group) {
        AppearancePreset preset = findById(presetId);
        return group != null && isPresetValid(PlayerRace.ELF, sex, presetId)
            && group.getSerializedId()
                .equals(preset.getGroupId());
    }

    public static boolean isOrcPresetValid(String presetId, OrcAppearanceGroup group) {
        AppearancePreset preset = findById(presetId);
        return group != null && isPresetValid(PlayerRace.ORC, PlayerSex.NONE, presetId)
            && group.getSerializedId()
                .equals(preset.getGroupId());
    }

    public static boolean isUrukHaiPresetValid(String presetId, UrukHaiAppearanceGroup group) {
        AppearancePreset preset = findById(presetId);
        return group != null && isPresetValid(PlayerRace.URUK_HAI, PlayerSex.NONE, presetId)
            && group.getSerializedId()
                .equals(preset.getGroupId());
    }

    public static PlayerSex getConservativeMigrationSex(PlayerRace race) {
        if (race == PlayerRace.DWARF) {
            return PlayerSex.MALE;
        }
        if (race == PlayerRace.ORC || race == PlayerRace.URUK_HAI) {
            return PlayerSex.NONE;
        }
        return null;
    }

    private static Map<String, AppearancePreset> createPresets() {
        Map<String, AppearancePreset> presets = new LinkedHashMap<String, AppearancePreset>();
        registerManPresets(presets);

        for (int index = 0; index < 3; index++) {
            registerDwarfPreset(
                presets,
                "dwarf_standard_m_" + index,
                PlayerSex.MALE,
                DwarfAppearanceGroup.STANDARD,
                "mob/dwarf/dwarf_male/" + index + ".png");
            registerDwarfPreset(
                presets,
                "dwarf_standard_f_" + index,
                PlayerSex.FEMALE,
                DwarfAppearanceGroup.STANDARD,
                "mob/dwarf/dwarf_female/" + index + ".png");
            registerDwarfPreset(
                presets,
                "dwarf_blue_m_" + index,
                PlayerSex.MALE,
                DwarfAppearanceGroup.BLUE_MOUNTAINS,
                "mob/dwarf/blueMountains_male/" + index + ".png");
            registerDwarfPreset(
                presets,
                "dwarf_blue_f_" + index,
                PlayerSex.FEMALE,
                DwarfAppearanceGroup.BLUE_MOUNTAINS,
                "mob/dwarf/blueMountains_female/" + index + ".png");
        }

        for (int index = 0; index < 13; index++) {
            registerPreset(
                presets,
                "hobbit_m_" + index,
                PlayerRace.HOBBIT,
                PlayerSex.MALE,
                null,
                "mob/hobbit/hobbit_male/" + index + ".png");
            registerPreset(
                presets,
                "hobbit_f_" + index,
                PlayerRace.HOBBIT,
                PlayerSex.FEMALE,
                null,
                "mob/hobbit/hobbit_female/" + index + ".png");
        }

        registerElfPresets(presets, ElfAppearanceGroup.GALADHRIM, "galadhrim", "galadhrim", 4, 3);
        registerElfPresets(presets, ElfAppearanceGroup.WOODLAND, "woodland", "woodElf", 4, 3);
        registerElfPresets(presets, ElfAppearanceGroup.HIGH_ELF, "high", "highElf", 18, 11);
        registerElfPresets(presets, ElfAppearanceGroup.DORWINION, "dorwinion", "dorwinion", 3, 3);

        for (int index = 0; index < 8; index++) {
            registerPreset(
                presets,
                "orc_common_" + index,
                PlayerRace.ORC,
                PlayerSex.NONE,
                OrcAppearanceGroup.COMMON_ORC.getSerializedId(),
                "mob/orc/orc/" + index + ".png");
        }

        for (int index = 0; index < 3; index++) {
            registerPreset(
                presets,
                "uruk_hai_isengard_" + index,
                PlayerRace.URUK_HAI,
                PlayerSex.NONE,
                UrukHaiAppearanceGroup.ISENGARD_URUK_HAI.getSerializedId(),
                "mob/orc/urukHai/" + index + ".png");
            registerPreset(
                presets,
                "uruk_hai_mordor_black_uruk_" + index,
                PlayerRace.URUK_HAI,
                PlayerSex.NONE,
                UrukHaiAppearanceGroup.MORDOR_BLACK_URUK.getSerializedId(),
                "mob/orc/blackUruk/" + index + ".png");
        }

        for (int index = 0; index < 8; index++) {
            registerPreset(
                presets,
                "uruk_hai_gundabad_" + index,
                PlayerRace.URUK_HAI,
                PlayerSex.NONE,
                UrukHaiAppearanceGroup.GUNDABAD_URUK.getSerializedId(),
                "mob/orc/orc/" + index + ".png");
        }

        return presets;
    }

    private static void registerManPresets(Map<String, AppearancePreset> presets) {
        registerManPool(
            presets,
            ManAppearanceGroup.ANGMAR,
            PlayerSex.MALE,
            "man_angmar_m_hillman",
            "mob/hillman/hillman_male/",
            3);
        registerManPool(
            presets,
            ManAppearanceGroup.ANGMAR,
            PlayerSex.FEMALE,
            "man_angmar_f_hillman",
            "mob/hillman/hillman_female/",
            4);

        registerManPool(
            presets,
            ManAppearanceGroup.BREE_LAND,
            PlayerSex.MALE,
            "man_bree_land_m_civilian",
            "mob/bree/bree_male/",
            30);
        registerManPool(
            presets,
            ManAppearanceGroup.BREE_LAND,
            PlayerSex.MALE,
            "man_bree_land_m_ruffian",
            "mob/bree/ruffian/",
            5);
        registerManPool(
            presets,
            ManAppearanceGroup.BREE_LAND,
            PlayerSex.FEMALE,
            "man_bree_land_f_civilian",
            "mob/bree/bree_female/",
            9);

        registerManPool(
            presets,
            ManAppearanceGroup.DALE,
            PlayerSex.MALE,
            "man_dale_m_civilian",
            "mob/dale/dale_male/",
            3);
        registerManPool(
            presets,
            ManAppearanceGroup.DALE,
            PlayerSex.MALE,
            "man_dale_m_soldier",
            "mob/dale/dale_soldier/",
            3);
        registerManPool(
            presets,
            ManAppearanceGroup.DALE,
            PlayerSex.FEMALE,
            "man_dale_f_civilian",
            "mob/dale/dale_female/",
            2);

        registerManPool(
            presets,
            ManAppearanceGroup.DORWINION,
            PlayerSex.MALE,
            "man_dorwinion_m_civilian",
            "mob/dorwinion/dorwinion_male/",
            4);
        registerManPool(
            presets,
            ManAppearanceGroup.DORWINION,
            PlayerSex.FEMALE,
            "man_dorwinion_f_civilian",
            "mob/dorwinion/dorwinion_female/",
            4);

        registerManPool(
            presets,
            ManAppearanceGroup.DUNEDAIN_NORTH,
            PlayerSex.MALE,
            "man_dunedain_north_m_ranger",
            "mob/ranger/ranger_male/",
            5);
        registerManPool(
            presets,
            ManAppearanceGroup.DUNEDAIN_NORTH,
            PlayerSex.FEMALE,
            "man_dunedain_north_f_ranger",
            "mob/ranger/ranger_female/",
            3);

        registerManPool(
            presets,
            ManAppearanceGroup.DUNLAND,
            PlayerSex.MALE,
            "man_dunland_m_civilian",
            "mob/dunland/dunlending_male/",
            4);
        registerManPool(
            presets,
            ManAppearanceGroup.DUNLAND,
            PlayerSex.MALE,
            "man_dunland_m_berserker",
            "mob/dunland/berserker/",
            1);
        registerManPool(
            presets,
            ManAppearanceGroup.DUNLAND,
            PlayerSex.FEMALE,
            "man_dunland_f_civilian",
            "mob/dunland/dunlending_female/",
            3);

        registerManPool(
            presets,
            ManAppearanceGroup.GONDOR,
            PlayerSex.MALE,
            "man_gondor_m_civilian",
            "mob/gondor/gondor_male/",
            10);
        registerManPool(
            presets,
            ManAppearanceGroup.GONDOR,
            PlayerSex.MALE,
            "man_gondor_m_soldier",
            "mob/gondor/gondorSoldier/",
            6);
        registerManPool(
            presets,
            ManAppearanceGroup.GONDOR,
            PlayerSex.MALE,
            "man_gondor_m_ithilien",
            "mob/gondor/ranger/",
            3);
        registerManPool(
            presets,
            ManAppearanceGroup.GONDOR,
            PlayerSex.MALE,
            "man_gondor_m_swan_knight",
            "mob/gondor/swanKnight/",
            3);
        registerManPool(
            presets,
            ManAppearanceGroup.GONDOR,
            PlayerSex.MALE,
            "man_gondor_m_harad_slave",
            "mob/nearHarad/slave/gondor_male/",
            2);
        registerManPool(
            presets,
            ManAppearanceGroup.GONDOR,
            PlayerSex.FEMALE,
            "man_gondor_f_civilian",
            "mob/gondor/gondor_female/",
            14);

        registerManPool(
            presets,
            ManAppearanceGroup.MORDOR,
            PlayerSex.MALE,
            "man_mordor_m_nurn",
            "mob/nurn/slave_male/",
            4);
        registerManPool(
            presets,
            ManAppearanceGroup.MORDOR,
            PlayerSex.FEMALE,
            "man_mordor_f_nurn",
            "mob/nurn/slave_female/",
            3);

        registerManPool(
            presets,
            ManAppearanceGroup.MORWAITH,
            PlayerSex.MALE,
            "man_morwaith_m_civilian",
            "mob/moredain/moredain_male/",
            5);
        registerManPool(
            presets,
            ManAppearanceGroup.MORWAITH,
            PlayerSex.MALE,
            "man_morwaith_m_harad_slave",
            "mob/nearHarad/slave/morwaith_male/",
            1);
        registerManPool(
            presets,
            ManAppearanceGroup.MORWAITH,
            PlayerSex.FEMALE,
            "man_morwaith_f_civilian",
            "mob/moredain/moredain_female/",
            4);

        registerManPool(
            presets,
            ManAppearanceGroup.NEAR_HARAD,
            PlayerSex.MALE,
            "man_near_harad_m_haradrim",
            "mob/nearHarad/haradrim_male/",
            5);
        registerManPool(
            presets,
            ManAppearanceGroup.NEAR_HARAD,
            PlayerSex.FEMALE,
            "man_near_harad_f_haradrim",
            "mob/nearHarad/haradrim_female/",
            3);
        registerManPool(
            presets,
            ManAppearanceGroup.NEAR_HARAD,
            PlayerSex.MALE,
            "man_near_harad_m_harnedor",
            "mob/nearHarad/harnedor_male/",
            5);
        registerManPool(
            presets,
            ManAppearanceGroup.NEAR_HARAD,
            PlayerSex.FEMALE,
            "man_near_harad_f_harnedor",
            "mob/nearHarad/harnedor_female/",
            3);
        registerManPool(
            presets,
            ManAppearanceGroup.NEAR_HARAD,
            PlayerSex.MALE,
            "man_near_harad_m_nomad",
            "mob/nearHarad/nomad_male/",
            5);
        registerManPool(
            presets,
            ManAppearanceGroup.NEAR_HARAD,
            PlayerSex.FEMALE,
            "man_near_harad_f_nomad",
            "mob/nearHarad/nomad_female/",
            3);
        registerManPool(
            presets,
            ManAppearanceGroup.NEAR_HARAD,
            PlayerSex.MALE,
            "man_near_harad_m_warrior",
            "mob/nearHarad/warrior/",
            1);
        registerManPool(
            presets,
            ManAppearanceGroup.NEAR_HARAD,
            PlayerSex.MALE,
            "man_near_harad_m_harnedor_warrior",
            "mob/nearHarad/harnedorWarrior/",
            5);
        registerManResourcePreset(
            presets,
            "man_near_harad_m_warlord_0",
            PlayerSex.MALE,
            ManAppearanceGroup.NEAR_HARAD,
            "mob/nearHarad/warlord.png");
        registerManPool(
            presets,
            ManAppearanceGroup.NEAR_HARAD,
            PlayerSex.MALE,
            "man_near_harad_m_harad_slave",
            "mob/nearHarad/slave/nearHarad_male/",
            2);

        registerManPool(
            presets,
            ManAppearanceGroup.EASTERLINGS,
            PlayerSex.MALE,
            "man_easterlings_m_civilian",
            "mob/rhun/easterling_male/",
            5);
        registerManPool(
            presets,
            ManAppearanceGroup.EASTERLINGS,
            PlayerSex.FEMALE,
            "man_easterlings_f_civilian",
            "mob/rhun/easterling_female/",
            5);

        registerManPool(
            presets,
            ManAppearanceGroup.ROHAN,
            PlayerSex.MALE,
            "man_rohan_m_civilian",
            "mob/rohan/rohan_male/",
            6);
        registerManPool(
            presets,
            ManAppearanceGroup.ROHAN,
            PlayerSex.MALE,
            "man_rohan_m_warrior",
            "mob/rohan/warrior/",
            6);
        registerManPool(
            presets,
            ManAppearanceGroup.ROHAN,
            PlayerSex.FEMALE,
            "man_rohan_f_civilian",
            "mob/rohan/rohan_female/",
            7);
        registerManPool(
            presets,
            ManAppearanceGroup.ROHAN,
            PlayerSex.FEMALE,
            "man_rohan_f_shieldmaiden",
            "mob/rohan/shieldmaiden/",
            3);

        registerManPool(
            presets,
            ManAppearanceGroup.TAURETHRIM,
            PlayerSex.MALE,
            "man_taurethrim_m_civilian",
            "mob/tauredain/tauredain_male/",
            4);
        registerManPool(
            presets,
            ManAppearanceGroup.TAURETHRIM,
            PlayerSex.MALE,
            "man_taurethrim_m_harad_slave",
            "mob/nearHarad/slave/taurethrim_male/",
            1);
        registerManPool(
            presets,
            ManAppearanceGroup.TAURETHRIM,
            PlayerSex.FEMALE,
            "man_taurethrim_f_civilian",
            "mob/tauredain/tauredain_female/",
            3);

        registerManPool(
            presets,
            ManAppearanceGroup.WANDERER,
            PlayerSex.MALE,
            "man_wanderer_m_bandit",
            "mob/bandit/bandit/",
            6);
        registerManPool(
            presets,
            ManAppearanceGroup.WANDERER,
            PlayerSex.MALE,
            "man_wanderer_m_harad_bandit",
            "mob/bandit/harad/",
            5);
        registerManPool(
            presets,
            ManAppearanceGroup.WANDERER,
            PlayerSex.MALE,
            "man_wanderer_m_scrap_trader",
            "mob/scrapTrader/",
            2);

        registerManAccountPreset(presets, MAN_MINECRAFT_SKIN_MALE_ID, PlayerSex.MALE);
        registerManAccountPreset(presets, MAN_MINECRAFT_SKIN_FEMALE_ID, PlayerSex.FEMALE);
    }

    private static void registerManPool(Map<String, AppearancePreset> presets, ManAppearanceGroup group, PlayerSex sex,
        String idPrefix, String textureDirectory, int count) {
        for (int index = 0; index < count; index++) {
            registerManResourcePreset(presets, idPrefix + "_" + index, sex, group, textureDirectory + index + ".png");
        }
    }

    private static void registerManResourcePreset(Map<String, AppearancePreset> presets, String id, PlayerSex sex,
        ManAppearanceGroup group, String texturePath) {
        registerPreset(presets, id, PlayerRace.MAN, sex, group.getSerializedId(), texturePath);
    }

    private static void registerManAccountPreset(Map<String, AppearancePreset> presets, String id, PlayerSex sex) {
        AppearancePreset preset = new AppearancePreset(
            id,
            PlayerRace.MAN,
            sex,
            null,
            AppearanceSourceType.MINECRAFT_ACCOUNT,
            "Use Minecraft Skin",
            null);
        putPreset(presets, preset);
    }

    private static void registerDwarfPreset(Map<String, AppearancePreset> presets, String id, PlayerSex sex,
        DwarfAppearanceGroup group, String texturePath) {
        registerPreset(presets, id, PlayerRace.DWARF, sex, group.getSerializedId(), texturePath);
    }

    private static void registerElfPresets(Map<String, AppearancePreset> presets, ElfAppearanceGroup group,
        String presetGroupName, String textureGroupName, int maleCount, int femaleCount) {
        for (int index = 0; index < maleCount; index++) {
            registerPreset(
                presets,
                "elf_" + presetGroupName + "_m_" + index,
                PlayerRace.ELF,
                PlayerSex.MALE,
                group.getSerializedId(),
                "mob/elf/" + textureGroupName + "_male/" + index + ".png");
        }
        for (int index = 0; index < femaleCount; index++) {
            registerPreset(
                presets,
                "elf_" + presetGroupName + "_f_" + index,
                PlayerRace.ELF,
                PlayerSex.FEMALE,
                group.getSerializedId(),
                "mob/elf/" + textureGroupName + "_female/" + index + ".png");
        }
    }

    private static void registerPreset(Map<String, AppearancePreset> presets, String id, PlayerRace race, PlayerSex sex,
        String groupId, String texturePath) {
        AppearancePreset preset = new AppearancePreset(id, race, sex, groupId, LOTR_TEXTURE_NAMESPACE, texturePath);
        putPreset(presets, preset);
    }

    private static void putPreset(Map<String, AppearancePreset> presets, AppearancePreset preset) {
        String id = preset.getId();
        if (presets.put(id, preset) != null) {
            throw new IllegalStateException("Duplicate appearance preset ID: " + id);
        }
    }

    private static Map<String, AppearancePreset> getInitializedPresets() {
        if (!initialized) {
            throw new IllegalStateException("AppearancePresetRegistry has not been initialized");
        }
        return presets;
    }

    private static boolean isLotrCharacterTexture(AppearancePreset preset) {
        return preset.getSourceType() == AppearanceSourceType.RESOURCE
            || preset.getSourceType() == AppearanceSourceType.EXTERNAL;
    }

    private static boolean isDwarfGroupId(String groupId) {
        for (DwarfAppearanceGroup group : DwarfAppearanceGroup.values()) {
            if (group.getSerializedId()
                .equals(groupId)) {
                return true;
            }
        }
        return false;
    }
}
