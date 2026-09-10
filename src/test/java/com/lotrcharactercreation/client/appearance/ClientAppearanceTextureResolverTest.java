package com.lotrcharactercreation.client.appearance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.race.PlayerRace;

public class ClientAppearanceTextureResolverTest {

    @Test
    public void dwarfFallbackMatchesExplicitSex() {
        assertEquals(
            "dwarf_standard_m_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.DWARF, PlayerSex.MALE));
        assertEquals(
            "dwarf_standard_f_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.DWARF, PlayerSex.FEMALE));
    }

    @Test
    public void dwarfFallbackRetainsConservativeBehaviorForMissingOrMalformedSex() {
        assertEquals(
            "dwarf_standard_m_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.DWARF, PlayerSex.NONE));
        assertEquals(
            "dwarf_standard_m_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.DWARF, null));
    }

    @Test
    public void otherRaceFallbacksRemainUnchanged() {
        assertEquals(
            "man_gondor_m_civilian_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.MAN, PlayerSex.MALE));
        assertEquals(
            "man_gondor_f_civilian_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.MAN, PlayerSex.FEMALE));
        assertEquals(
            "elf_galadhrim_m_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.ELF, PlayerSex.MALE));
        assertEquals(
            "elf_galadhrim_f_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.ELF, PlayerSex.FEMALE));
        assertEquals(
            "hobbit_m_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.HOBBIT, PlayerSex.MALE));
        assertEquals(
            "hobbit_f_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.HOBBIT, PlayerSex.FEMALE));
        assertEquals(
            "orc_common_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.ORC, PlayerSex.NONE));
        assertEquals(
            "uruk_hai_isengard_0",
            ClientAppearanceTextureResolver.getFallbackPresetId(PlayerRace.URUK_HAI, PlayerSex.NONE));
        assertNull(ClientAppearanceTextureResolver.getFallbackPresetId(null, PlayerSex.MALE));
    }
}
