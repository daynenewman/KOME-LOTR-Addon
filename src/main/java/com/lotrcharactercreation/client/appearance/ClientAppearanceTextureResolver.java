package com.lotrcharactercreation.client.appearance;

import java.io.File;

import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.AppearanceSourceType;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class ClientAppearanceTextureResolver {

    private static final String MAN_MALE_FALLBACK_PRESET_ID = "man_gondor_m_civilian_0";
    private static final String MAN_FEMALE_FALLBACK_PRESET_ID = "man_gondor_f_civilian_0";
    private static final String DWARF_MALE_FALLBACK_PRESET_ID = "dwarf_standard_m_0";
    private static final String DWARF_FEMALE_FALLBACK_PRESET_ID = "dwarf_standard_f_0";
    private static final String ELF_MALE_FALLBACK_PRESET_ID = "elf_galadhrim_m_0";
    private static final String ELF_FEMALE_FALLBACK_PRESET_ID = "elf_galadhrim_f_0";
    private static final String HOBBIT_MALE_FALLBACK_PRESET_ID = "hobbit_m_0";
    private static final String HOBBIT_FEMALE_FALLBACK_PRESET_ID = "hobbit_f_0";
    private static final String ORC_FALLBACK_PRESET_ID = "orc_common_0";
    private static final String URUK_HAI_FALLBACK_PRESET_ID = "uruk_hai_isengard_0";

    private static ExternalAppearanceTextureManager externalTextureManager;

    private ClientAppearanceTextureResolver() {}

    public static void initialize(File customSkinRoot) {
        if (externalTextureManager == null) {
            externalTextureManager = new ExternalAppearanceTextureManager(customSkinRoot);
        }
    }

    public static ResourceLocation resolve(EntityPlayer player, AppearancePreset preset) {
        return resolve(preset, getAccountSkin(player));
    }

    public static ResourceLocation resolveWithFallback(EntityPlayer player, PlayerRace race, PlayerSex sex,
        String presetId) {
        return resolveWithFallback(race, sex, presetId, getAccountSkin(player));
    }

    public static ResourceLocation resolveWithProfileSkinFallback(PlayerRace race, PlayerSex sex, String presetId,
        ResourceLocation profileSkin) {
        return resolveWithFallback(race, sex, presetId, profileSkin);
    }

    private static ResourceLocation resolveWithFallback(PlayerRace race, PlayerSex sex, String presetId,
        ResourceLocation accountSkin) {
        AppearancePreset preset = AppearancePresetRegistry.findById(presetId);
        if (AppearancePresetRegistry.isPresetValid(race, sex, presetId)) {
            ResourceLocation texture = resolve(preset, accountSkin);
            if (texture != null) {
                return texture;
            }
        }
        return resolveFallback(race, sex);
    }

    public static ResourceLocation resolveFallback(EntityPlayer player, PlayerRace race, PlayerSex sex) {
        return resolveFallback(race, sex);
    }

    private static ResourceLocation resolveFallback(PlayerRace race, PlayerSex sex) {
        String fallbackPresetId = getFallbackPresetId(race, sex);
        AppearancePreset fallbackPreset = AppearancePresetRegistry.findById(fallbackPresetId);
        return fallbackPreset == null || fallbackPreset.getSourceType() != AppearanceSourceType.RESOURCE ? null
            : fallbackPreset.getTexture();
    }

    private static ResourceLocation resolve(AppearancePreset preset, ResourceLocation accountSkin) {
        if (preset == null) {
            return null;
        }

        switch (preset.getSourceType()) {
            case RESOURCE:
                return preset.getTexture();
            case MINECRAFT_ACCOUNT:
                return accountSkin;
            case EXTERNAL:
                return externalTextureManager == null ? null : externalTextureManager.resolve(preset);
            default:
                return null;
        }
    }

    public static boolean isLotrCharacterTexture(AppearancePreset preset) {
        return preset != null && (preset.getSourceType() == AppearanceSourceType.RESOURCE
            || preset.getSourceType() == AppearanceSourceType.EXTERNAL);
    }

    private static ResourceLocation getAccountSkin(EntityPlayer player) {
        return player instanceof AbstractClientPlayer ? ((AbstractClientPlayer) player).getLocationSkin()
            : AbstractClientPlayer.locationStevePng;
    }

    static String getFallbackPresetId(PlayerRace race, PlayerSex sex) {
        if (race == PlayerRace.MAN && sex == PlayerSex.FEMALE) {
            return MAN_FEMALE_FALLBACK_PRESET_ID;
        }
        if (race == PlayerRace.MAN) {
            return MAN_MALE_FALLBACK_PRESET_ID;
        }
        if (race == PlayerRace.ELF && sex == PlayerSex.FEMALE) {
            return ELF_FEMALE_FALLBACK_PRESET_ID;
        }
        if (race == PlayerRace.ELF) {
            return ELF_MALE_FALLBACK_PRESET_ID;
        }
        if (race == PlayerRace.DWARF && sex == PlayerSex.FEMALE) {
            return DWARF_FEMALE_FALLBACK_PRESET_ID;
        }
        if (race == PlayerRace.DWARF) {
            return DWARF_MALE_FALLBACK_PRESET_ID;
        }
        if (race == PlayerRace.HOBBIT && sex == PlayerSex.FEMALE) {
            return HOBBIT_FEMALE_FALLBACK_PRESET_ID;
        }
        if (race == PlayerRace.HOBBIT) {
            return HOBBIT_MALE_FALLBACK_PRESET_ID;
        }
        if (race == PlayerRace.ORC) {
            return ORC_FALLBACK_PRESET_ID;
        }
        if (race == PlayerRace.URUK_HAI) {
            return URUK_HAI_FALLBACK_PRESET_ID;
        }
        return null;
    }
}
