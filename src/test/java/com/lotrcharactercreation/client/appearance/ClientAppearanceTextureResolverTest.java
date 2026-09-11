package com.lotrcharactercreation.client.appearance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.CustomSkinEntry;
import com.lotrcharactercreation.appearance.CustomSkinScanResult;
import com.lotrcharactercreation.appearance.ExternalAppearancePresetScanner;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.race.PlayerRace;

import net.minecraft.util.ResourceLocation;

public class ClientAppearanceTextureResolverTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

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

    @Test
    public void manMinecraftAccountSkinBehaviorRemainsUnchanged() {
        ResourceLocation profileSkin = new ResourceLocation("test", "account_skin");

        assertSame(
            profileSkin,
            ClientAppearanceTextureResolver.resolveWithProfileSkinFallback(
                PlayerRace.MAN,
                PlayerSex.MALE,
                AppearancePresetRegistry.MAN_MINECRAFT_SKIN_MALE_ID,
                profileSkin));
    }

    @Test
    public void missingExternalContentFallsBackWithoutDiscardingLogicalIdentity() throws Exception {
        Path root = temporaryFolder.newFolder("external-metadata").toPath();
        Path file = root.resolve("dwarf/female/blue_mountains/fallback_test.png");
        Files.createDirectories(file.getParent());
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xFF445566);
        assertTrue(ImageIO.write(image, "png", file.toFile()));
        CustomSkinScanResult scan = ExternalAppearancePresetScanner.scanSnapshot(root.toFile(), 1L, null);
        CustomSkinEntry entry = scan.getSnapshot().getEntries().get(0);
        ClientExternalSkinDefinition definition = ClientExternalSkinDefinition.fromValidatedEntry(entry);
        ClientCustomSkinManager manager = ClientCustomSkinManager.getInstance();

        try {
            manager.activateExternalCatalog(java.util.Collections.singletonList(definition));

            ResourceLocation resolved = ClientAppearanceTextureResolver.resolveWithProfileSkinFallback(
                PlayerRace.DWARF,
                PlayerSex.FEMALE,
                definition.getPresetId(),
                null);

            assertEquals(
                AppearancePresetRegistry.findById("dwarf_standard_f_0").getTexture(),
                resolved);
            assertSame(definition, manager.getExternalDefinition(definition.getPresetId()));
            assertSame(definition.getPreset(), manager.getCatalog().findById(definition.getPresetId()));
        } finally {
            manager.clearConnectionState();
        }
    }
}
