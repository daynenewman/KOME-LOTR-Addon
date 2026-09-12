package com.lotrcharactercreation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.AppearanceSourceType;
import com.lotrcharactercreation.appearance.ExternalAppearancePresetScanner;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.appearance.ServerCustomSkinLibrary;
import com.lotrcharactercreation.creation.CharacterCreationFlowService;
import com.lotrcharactercreation.creation.CharacterRecreationService;
import com.lotrcharactercreation.creation.CharacterRecreationService.StartResult;
import com.lotrcharactercreation.creation.CharacterCreationStage;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

public class CharacterCreationLegacyCompatibilityTest {

    private static final String LEGACY_DATA_TAG = "lotrcharactercreation";
    private static final String CUSTOM_PRESET_ID = "custom_man_male_gondor_legacy_hero";

    @ClassRule
    public static final TemporaryFolder TEMPORARY_FOLDER = new TemporaryFolder();

    private static Path configurationDirectory;
    private static Path customSkinRoot;
    private static Path customSkinFile;

    @BeforeClass
    public static void initializeRegistryFromLegacyCustomSkinRoot() throws Exception {
        configurationDirectory = TEMPORARY_FOLDER.newFolder("config").toPath();
        customSkinRoot = configurationDirectory.resolve("lotrcharactercreation").resolve("custom_skins");
        customSkinFile = customSkinRoot.resolve("man/male/gondor/legacy_hero.png");
        Files.createDirectories(customSkinFile.getParent());

        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        assertTrue(ImageIO.write(image, "png", customSkinFile.toFile()));
        assertTrue(ServerCustomSkinLibrary.getInstance().reload(customSkinRoot.toFile(), null).isApplied());
    }

    @Test
    public void completedLegacyStateLoadsFromReleasedPlayerNbtHierarchyWithoutMigration() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        NBTTagCompound originalLegacyData = (NBTTagCompound) fixture.legacyData.copy();

        assertSame(fixture.forgeData, fixture.player.getEntityData());
        assertSame(
            fixture.legacyData,
            fixture.forgeData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag(LEGACY_DATA_TAG));
        assertEquals(PlayerRace.MAN, PlayerRaceData.getRace(fixture.player));
        assertEquals(PlayerSex.MALE, PlayerRaceData.getSex(fixture.player));
        assertEquals(StartingFaction.GONDOR, PlayerRaceData.getStartingFaction(fixture.player));
        assertEquals(CUSTOM_PRESET_ID, PlayerRaceData.getAppearancePresetId(fixture.player));
        assertEquals(CUSTOM_PRESET_ID, PlayerRaceData.getAppearancePreset(fixture.player).getId());
        assertEquals("minas_tirith", PlayerRaceData.getStartingWaypointCodeName(fixture.player));

        assertTrue(PlayerRaceData.isAppearanceInitialized(fixture.player));
        assertTrue(PlayerRaceData.isRaceSelectionComplete(fixture.player));
        assertTrue(PlayerRaceData.isFactionSelectionComplete(fixture.player));
        assertTrue(PlayerRaceData.isStartingFactionApplied(fixture.player));
        assertTrue(PlayerRaceData.isStartingWaypointApplied(fixture.player));
        assertTrue(PlayerRaceData.isCharacterCreationComplete(fixture.player));
        assertFalse(PlayerRaceData.isCharacterEditAuthorized(fixture.player));
        assertTrue(PlayerRaceData.hasDwarfResourceData(fixture.player));
        assertEquals(63.25F, PlayerRaceData.getDwarfStamina(fixture.player), 0.0F);
        assertEquals(17, PlayerRaceData.getDwarfFeast(fixture.player));
        assertTrue(PlayerRaceData.isDwarfStaminaExhausted(fixture.player));
        assertEquals(42, PlayerRaceData.getElfGrappleCooldownTicks(fixture.player));
        assertTrue(PlayerRaceData.isElfGrappleCleanupPending(fixture.player));
        assertEquals(CharacterCreationStage.COMPLETE, CharacterCreationFlowService.getNextRequiredStage(fixture.player));

        assertEquals(originalLegacyData, fixture.legacyData);
        assertFalse(fixture.playerNbt.hasKey("kome"));
        assertFalse(fixture.forgeData.hasKey("kome"));
        assertFalse(fixture.forgeData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).hasKey("kome"));
        assertSame(
            fixture.legacyData,
            fixture.forgeData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getCompoundTag(LEGACY_DATA_TAG));
    }

    @Test
    public void incompleteLegacyStatesResumeAtEveryExistingFlowStage() throws Exception {
        for (CharacterCreationStage expectedStage : CharacterCreationStage.values()) {
            LegacyFixture fixture = fixtureForStage(expectedStage);
            NBTTagCompound originalLegacyData = (NBTTagCompound) fixture.legacyData.copy();

            assertEquals(
                "Unexpected stage for legacy " + expectedStage.getSerializedId() + " fixture",
                expectedStage,
                CharacterCreationFlowService.getNextRequiredStage(fixture.player));
            assertEquals(originalLegacyData, fixture.legacyData);
            assertFalse(fixture.forgeData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).hasKey("kome"));
        }
    }

    @Test
    public void customSkinDiscoveryAndSavedPresetResolutionRemainLegacyCompatible() throws Exception {
        List<AppearancePreset> scanned = ExternalAppearancePresetScanner.scan(customSkinRoot.toFile(), null);
        assertEquals(1, scanned.size());

        AppearancePreset scannedPreset = scanned.get(0);
        assertEquals(CUSTOM_PRESET_ID, scannedPreset.getId());
        assertEquals(PlayerRace.MAN, scannedPreset.getRace());
        assertEquals(PlayerSex.MALE, scannedPreset.getSex());
        assertEquals("gondor", scannedPreset.getGroupId());
        assertEquals(AppearanceSourceType.EXTERNAL, scannedPreset.getSourceType());
        assertEquals("man/male/gondor/legacy_hero.png", scannedPreset.getExternalRelativePath());

        AppearancePreset registeredPreset = ServerCustomSkinLibrary.getInstance()
            .getCurrentCatalog()
            .findById(CUSTOM_PRESET_ID);
        assertNotNull(registeredPreset);
        assertEquals("man/male/gondor/legacy_hero.png", registeredPreset.getExternalRelativePath());

        LegacyFixture fixture = completedLegacyFixture();
        assertSame(registeredPreset, PlayerRaceData.getAppearancePreset(fixture.player));
        assertEquals(CUSTOM_PRESET_ID, PlayerRaceData.getAppearancePresetId(fixture.player));

        assertTrue(Files.isRegularFile(customSkinFile));
        assertFalse(Files.exists(configurationDirectory.resolve("kome")));
        try (Stream<Path> files = Files.walk(configurationDirectory)) {
            assertEquals(1L, files.filter(path -> path.getFileName().toString().endsWith(".png")).count());
        }
    }

    @Test
    public void incompletePlayerCanSelectSexAtTheRequiredStage() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        fixture.legacyData.setBoolean("characterCreationComplete", false);
        fixture.legacyData.setBoolean("startingFactionApplied", false);
        fixture.legacyData.setBoolean("startingWaypointApplied", false);
        fixture.legacyData.removeTag("sex");
        fixture.legacyData.setBoolean("factionSelectionComplete", false);
        fixture.legacyData.removeTag("appearancePreset");
        fixture.legacyData.setBoolean("appearanceInitialized", false);

        assertEquals(CharacterCreationStage.SEX, CharacterCreationFlowService.getNextRequiredStage(fixture.player));
        assertTrue(CharacterCreationFlowService.selectSex(fixture.player, PlayerSex.FEMALE));
        assertEquals(PlayerSex.FEMALE, PlayerRaceData.getSex(fixture.player));
        assertEquals(CharacterCreationStage.FACTION, CharacterCreationFlowService.getNextRequiredStage(fixture.player));
    }

    @Test
    public void incompletePlayerCanSelectAppearanceAtTheRequiredStage() throws Exception {
        LegacyFixture fixture = fixtureForStage(CharacterCreationStage.APPEARANCE);

        assertTrue(CharacterCreationFlowService.selectAppearance(fixture.player, CUSTOM_PRESET_ID));
        assertEquals(CUSTOM_PRESET_ID, PlayerRaceData.getAppearancePresetId(fixture.player));
        assertTrue(PlayerRaceData.isAppearanceInitialized(fixture.player));
        assertEquals(
            CharacterCreationStage.CONFIRMATION,
            CharacterCreationFlowService.getNextRequiredStage(fixture.player));
    }

    @Test
    public void completedPlayerCannotChangeSex() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();

        assertFalse(CharacterCreationFlowService.selectSex(fixture.player, PlayerSex.FEMALE));
        assertEquals(PlayerSex.MALE, PlayerRaceData.getSex(fixture.player));
        assertEquals(CUSTOM_PRESET_ID, PlayerRaceData.getAppearancePresetId(fixture.player));
        assertTrue(PlayerRaceData.isAppearanceInitialized(fixture.player));
    }

    @Test
    public void completedPlayerCannotChangeAppearance() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        String replacementPresetId = "man_gondor_m_civilian_0";
        assertNotNull(AppearancePresetRegistry.findById(replacementPresetId));

        assertFalse(CharacterCreationFlowService.selectAppearance(fixture.player, replacementPresetId));
        assertEquals(CUSTOM_PRESET_ID, PlayerRaceData.getAppearancePresetId(fixture.player));
        assertTrue(PlayerRaceData.isAppearanceInitialized(fixture.player));
    }

    @Test
    public void completedPlayerCannotChangeRace() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();

        assertFalse(CharacterCreationFlowService.selectRace(fixture.player, PlayerRace.ELF));
        assertEquals(PlayerRace.MAN, PlayerRaceData.getRace(fixture.player));
        assertTrue(PlayerRaceData.isRaceSelectionComplete(fixture.player));
    }

    @Test
    public void authorizationAloneDoesNotBypassTheCompletedStage() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        PlayerRaceData.setCharacterEditAuthorized(fixture.player, true);

        assertEquals(CharacterCreationStage.COMPLETE, CharacterCreationFlowService.getNextRequiredStage(fixture.player));
        assertFalse(CharacterCreationFlowService.selectRace(fixture.player, PlayerRace.ELF));
        assertFalse(CharacterCreationFlowService.selectSex(fixture.player, PlayerSex.FEMALE));
        assertFalse(CharacterCreationFlowService.selectAppearance(fixture.player, "man_gondor_m_civilian_0"));
        assertEquals(PlayerRace.MAN, PlayerRaceData.getRace(fixture.player));
        assertEquals(PlayerSex.MALE, PlayerRaceData.getSex(fixture.player));
        assertEquals(CUSTOM_PRESET_ID, PlayerRaceData.getAppearancePresetId(fixture.player));
    }

    @Test
    public void incompletePlayerCannotMutateSelectionsOutsideTheRequiredStage() throws Exception {
        LegacyFixture fixture = fixtureForStage(CharacterCreationStage.CONFIRMATION);

        assertFalse(CharacterCreationFlowService.selectRace(fixture.player, PlayerRace.ELF));
        assertFalse(CharacterCreationFlowService.selectSex(fixture.player, PlayerSex.FEMALE));
        assertFalse(CharacterCreationFlowService.selectStartingFaction(fixture.player, StartingFaction.WANDERER));
        assertFalse(CharacterCreationFlowService.selectAppearance(fixture.player, "man_gondor_m_civilian_0"));
        assertEquals(CharacterCreationStage.CONFIRMATION, CharacterCreationFlowService.getNextRequiredStage(fixture.player));
        assertEquals(PlayerRace.MAN, PlayerRaceData.getRace(fixture.player));
        assertEquals(PlayerSex.MALE, PlayerRaceData.getSex(fixture.player));
        assertEquals(StartingFaction.GONDOR, PlayerRaceData.getStartingFaction(fixture.player));
        assertEquals(CUSTOM_PRESET_ID, PlayerRaceData.getAppearancePresetId(fixture.player));
    }

    @Test
    public void authorizedRecreationCanFollowSelectionStagesWithoutResettingOneTimeFlags() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        PlayerRaceData.setCharacterEditAuthorized(fixture.player, true);
        PlayerRaceData.setCharacterCreationComplete(fixture.player, false);
        PlayerRaceData.setRaceSelectionComplete(fixture.player, false);

        assertTrue(PlayerRaceData.isStartingFactionApplied(fixture.player));
        assertTrue(PlayerRaceData.isStartingWaypointApplied(fixture.player));
        assertEquals(CharacterCreationStage.RACE, CharacterCreationFlowService.getNextRequiredStage(fixture.player));

        assertTrue(CharacterCreationFlowService.selectRace(fixture.player, PlayerRace.ELF));
        assertEquals(CharacterCreationStage.SEX, CharacterCreationFlowService.getNextRequiredStage(fixture.player));
        assertTrue(CharacterCreationFlowService.selectSex(fixture.player, PlayerSex.FEMALE));
        assertEquals(CharacterCreationStage.FACTION, CharacterCreationFlowService.getNextRequiredStage(fixture.player));
        assertTrue(CharacterCreationFlowService.selectStartingFaction(fixture.player, StartingFaction.LOTHLORIEN));
        assertEquals(CharacterCreationStage.APPEARANCE, CharacterCreationFlowService.getNextRequiredStage(fixture.player));
        assertTrue(CharacterCreationFlowService.selectAppearance(fixture.player, "elf_galadhrim_f_0"));
        assertEquals(
            CharacterCreationStage.CONFIRMATION,
            CharacterCreationFlowService.getNextRequiredStage(fixture.player));

        assertTrue(PlayerRaceData.isCharacterEditAuthorized(fixture.player));
        assertTrue(PlayerRaceData.isStartingFactionApplied(fixture.player));
        assertTrue(PlayerRaceData.isStartingWaypointApplied(fixture.player));

        PlayerRaceData.setCharacterCreationComplete(fixture.player, true);
        assertFalse(PlayerRaceData.isCharacterEditAuthorized(fixture.player));
        assertTrue(PlayerRaceData.isStartingFactionApplied(fixture.player));
        assertTrue(PlayerRaceData.isStartingWaypointApplied(fixture.player));
    }

    @Test
    public void safeRecreationReopensRaceWithoutChangingPositionOrOneTimeState() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        fixture.player.dimension = 7;
        fixture.player.posX = 123.25D;
        fixture.player.posY = 64.5D;
        fixture.player.posZ = -456.75D;
        fixture.player.rotationYaw = 91.0F;
        fixture.player.rotationPitch = -17.5F;

        assertEquals(StartResult.STARTED, CharacterRecreationService.begin(fixture.player));

        assertFalse(PlayerRaceData.isCharacterCreationComplete(fixture.player));
        assertTrue(PlayerRaceData.isCharacterEditAuthorized(fixture.player));
        assertFalse(PlayerRaceData.isRaceSelectionComplete(fixture.player));
        assertEquals(CharacterCreationStage.RACE, CharacterCreationFlowService.getNextRequiredStage(fixture.player));
        assertTrue(PlayerRaceData.isStartingFactionApplied(fixture.player));
        assertTrue(PlayerRaceData.isStartingWaypointApplied(fixture.player));
        assertEquals("minas_tirith", PlayerRaceData.getStartingWaypointCodeName(fixture.player));
        assertEquals(7, fixture.player.dimension);
        assertEquals(123.25D, fixture.player.posX, 0.0D);
        assertEquals(64.5D, fixture.player.posY, 0.0D);
        assertEquals(-456.75D, fixture.player.posZ, 0.0D);
        assertEquals(91.0F, fixture.player.rotationYaw, 0.0F);
        assertEquals(-17.5F, fixture.player.rotationPitch, 0.0F);
    }

    @Test
    public void repeatedRecreationCommandResumesCurrentStageWithoutResettingProgress() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        assertEquals(StartResult.STARTED, CharacterRecreationService.begin(fixture.player));
        assertTrue(CharacterCreationFlowService.selectRace(fixture.player, PlayerRace.ELF));
        assertEquals(CharacterCreationStage.SEX, CharacterCreationFlowService.getNextRequiredStage(fixture.player));

        assertEquals(StartResult.RESUMED, CharacterRecreationService.begin(fixture.player));
        assertEquals(CharacterCreationStage.SEX, CharacterCreationFlowService.getNextRequiredStage(fixture.player));
        assertTrue(PlayerRaceData.isCharacterEditAuthorized(fixture.player));
    }

    @Test
    public void incompleteFirstTimePlayerIsNotConvertedIntoARecreationSession() throws Exception {
        LegacyFixture fixture = fixtureForStage(CharacterCreationStage.SEX);

        assertEquals(StartResult.ALREADY_IN_CREATION, CharacterRecreationService.begin(fixture.player));
        assertFalse(PlayerRaceData.isCharacterEditAuthorized(fixture.player));
        assertEquals(CharacterCreationStage.SEX, CharacterCreationFlowService.getNextRequiredStage(fixture.player));
    }

    @Test
    public void recreationCompletionClearsAuthorizationAndPreservesOneTimeState() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        fixture.player.dimension = 3;
        fixture.player.posX = 8.0D;
        fixture.player.posY = 72.0D;
        fixture.player.posZ = 14.0D;

        assertEquals(StartResult.STARTED, CharacterRecreationService.begin(fixture.player));
        assertTrue(CharacterCreationFlowService.selectRace(fixture.player, PlayerRace.ELF));
        assertTrue(CharacterCreationFlowService.selectSex(fixture.player, PlayerSex.FEMALE));
        assertTrue(CharacterCreationFlowService.selectStartingFaction(fixture.player, StartingFaction.LOTHLORIEN));
        assertTrue(CharacterCreationFlowService.selectAppearance(fixture.player, "elf_galadhrim_f_0"));
        assertTrue(CharacterCreationFlowService.isReadyForFinalization(fixture.player));

        assertTrue(CharacterRecreationService.complete(fixture.player));
        assertTrue(PlayerRaceData.isCharacterCreationComplete(fixture.player));
        assertFalse(PlayerRaceData.isCharacterEditAuthorized(fixture.player));
        assertEquals(PlayerRace.ELF, PlayerRaceData.getRace(fixture.player));
        assertEquals(PlayerSex.FEMALE, PlayerRaceData.getSex(fixture.player));
        assertEquals("elf_galadhrim_f_0", PlayerRaceData.getAppearancePresetId(fixture.player));
        assertEquals(StartingFaction.LOTHLORIEN, PlayerRaceData.getStartingFaction(fixture.player));
        assertTrue(PlayerRaceData.isStartingFactionApplied(fixture.player));
        assertTrue(PlayerRaceData.isStartingWaypointApplied(fixture.player));
        assertEquals("minas_tirith", PlayerRaceData.getStartingWaypointCodeName(fixture.player));
        assertEquals(3, fixture.player.dimension);
        assertEquals(8.0D, fixture.player.posX, 0.0D);
        assertEquals(72.0D, fixture.player.posY, 0.0D);
        assertEquals(14.0D, fixture.player.posZ, 0.0D);
    }

    @Test
    public void recreationStillUsesServerCatalogValidationForExternalAndAccountSkins() throws Exception {
        LegacyFixture externalFixture = completedLegacyFixture();
        assertEquals(StartResult.STARTED, CharacterRecreationService.begin(externalFixture.player));
        assertTrue(CharacterCreationFlowService.selectRace(externalFixture.player, PlayerRace.MAN));
        assertTrue(CharacterCreationFlowService.selectSex(externalFixture.player, PlayerSex.MALE));
        assertTrue(CharacterCreationFlowService.selectStartingFaction(externalFixture.player, StartingFaction.GONDOR));
        assertTrue(CharacterCreationFlowService.selectAppearance(externalFixture.player, CUSTOM_PRESET_ID));
        assertEquals(CUSTOM_PRESET_ID, PlayerRaceData.getAppearancePresetId(externalFixture.player));

        LegacyFixture accountFixture = completedLegacyFixture();
        assertEquals(StartResult.STARTED, CharacterRecreationService.begin(accountFixture.player));
        assertTrue(CharacterCreationFlowService.selectRace(accountFixture.player, PlayerRace.MAN));
        assertTrue(CharacterCreationFlowService.selectSex(accountFixture.player, PlayerSex.FEMALE));
        assertTrue(CharacterCreationFlowService.selectStartingFaction(accountFixture.player, StartingFaction.GONDOR));
        assertTrue(
            CharacterCreationFlowService
                .selectAppearance(accountFixture.player, AppearancePresetRegistry.MAN_MINECRAFT_SKIN_FEMALE_ID));
        assertEquals(
            AppearancePresetRegistry.MAN_MINECRAFT_SKIN_FEMALE_ID,
            PlayerRaceData.getAppearancePresetId(accountFixture.player));
    }

    @Test
    public void onePlayersRecreationAuthorizationDoesNotAuthorizeAnotherPlayer() throws Exception {
        LegacyFixture authorized = completedLegacyFixture();
        LegacyFixture blocked = completedLegacyFixture();
        assertEquals(StartResult.STARTED, CharacterRecreationService.begin(authorized.player));

        assertTrue(CharacterCreationFlowService.selectRace(authorized.player, PlayerRace.ELF));
        assertFalse(CharacterCreationFlowService.selectRace(blocked.player, PlayerRace.ELF));
        assertFalse(PlayerRaceData.isCharacterEditAuthorized(blocked.player));
        assertEquals(PlayerRace.MAN, PlayerRaceData.getRace(blocked.player));
    }

    @Test
    public void authorizedRecreationSurvivesPersistedDataReloadAndResumesItsStage() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        assertEquals(StartResult.STARTED, CharacterRecreationService.begin(fixture.player));
        assertTrue(CharacterCreationFlowService.selectRace(fixture.player, PlayerRace.DWARF));
        assertEquals(CharacterCreationStage.SEX, CharacterCreationFlowService.getNextRequiredStage(fixture.player));

        NBTTagCompound reloadedForgeData = (NBTTagCompound) fixture.forgeData.copy();
        LegacyPlayer reloadedPlayer = allocatePlayer(reloadedForgeData);
        assertTrue(CharacterRecreationService.isInProgress(reloadedPlayer));
        assertFalse(PlayerRaceData.isCharacterCreationComplete(reloadedPlayer));
        assertTrue(PlayerRaceData.isCharacterEditAuthorized(reloadedPlayer));
        assertEquals(CharacterCreationStage.SEX, CharacterCreationFlowService.getNextRequiredStage(reloadedPlayer));
        assertTrue(PlayerRaceData.isStartingFactionApplied(reloadedPlayer));
        assertTrue(PlayerRaceData.isStartingWaypointApplied(reloadedPlayer));
    }

    @Test
    public void persistedAuthorizationOnACompletedPlayerIsReopenedWithoutStackingState() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        PlayerRaceData.setCharacterEditAuthorized(fixture.player, true);

        assertEquals(StartResult.RESUMED, CharacterRecreationService.begin(fixture.player));
        assertTrue(CharacterRecreationService.isInProgress(fixture.player));
        assertEquals(CharacterCreationStage.RACE, CharacterCreationFlowService.getNextRequiredStage(fixture.player));
        assertTrue(PlayerRaceData.isStartingFactionApplied(fixture.player));
        assertTrue(PlayerRaceData.isStartingWaypointApplied(fixture.player));
    }

    @Test
    public void missingAuthorizationDefaultsToUnauthorizedWithoutMutatingLegacyNbt() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        NBTTagCompound originalLegacyData = (NBTTagCompound) fixture.legacyData.copy();

        assertFalse(PlayerRaceData.isCharacterEditAuthorized(fixture.player));
        assertFalse(fixture.legacyData.hasKey("characterEditAuthorized"));
        assertEquals(originalLegacyData, fixture.legacyData);

        PlayerRaceData.setCharacterCreationComplete(fixture.player, false);
        PlayerRaceData.setRaceSelectionComplete(fixture.player, false);
        assertFalse(
            CharacterCreationFlowService
                .isSelectionMutationAuthorized(fixture.player, CharacterCreationStage.RACE));
        assertFalse(CharacterCreationFlowService.selectRace(fixture.player, PlayerRace.ELF));
        assertTrue(PlayerRaceData.isStartingFactionApplied(fixture.player));
        assertTrue(PlayerRaceData.isStartingWaypointApplied(fixture.player));
    }

    @Test
    public void editAuthorizationPersistsInTheLegacyCharacterCreationNbtHierarchy() throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        PlayerRaceData.setCharacterEditAuthorized(fixture.player, true);

        assertTrue(fixture.legacyData.getBoolean("characterEditAuthorized"));
        NBTTagCompound reloadedForgeData = (NBTTagCompound) fixture.forgeData.copy();
        LegacyPlayer reloadedPlayer = allocatePlayer(reloadedForgeData);
        assertTrue(PlayerRaceData.isCharacterEditAuthorized(reloadedPlayer));
        assertTrue(PlayerRaceData.isStartingFactionApplied(reloadedPlayer));
        assertTrue(PlayerRaceData.isStartingWaypointApplied(reloadedPlayer));

        PlayerRaceData.setCharacterEditAuthorized(reloadedPlayer, false);
        assertFalse(PlayerRaceData.isCharacterEditAuthorized(reloadedPlayer));
        assertFalse(
            reloadedForgeData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                .getCompoundTag(LEGACY_DATA_TAG)
                .hasKey("characterEditAuthorized"));
        assertTrue(PlayerRaceData.isStartingFactionApplied(reloadedPlayer));
        assertTrue(PlayerRaceData.isStartingWaypointApplied(reloadedPlayer));
    }

    private static LegacyFixture fixtureForStage(CharacterCreationStage stage) throws Exception {
        LegacyFixture fixture = completedLegacyFixture();
        NBTTagCompound data = fixture.legacyData;
        data.setBoolean("characterCreationComplete", stage == CharacterCreationStage.COMPLETE);
        data.setBoolean("startingFactionApplied", false);
        data.setBoolean("startingWaypointApplied", false);

        switch (stage) {
            case RACE:
                data.setBoolean("raceSelectionComplete", false);
                break;
            case SEX:
                data.removeTag("sex");
                break;
            case FACTION:
                data.setBoolean("factionSelectionComplete", false);
                break;
            case APPEARANCE:
                data.setBoolean("appearanceInitialized", false);
                break;
            case CONFIRMATION:
            case COMPLETE:
                break;
            default:
                throw new AssertionError("Unhandled Character Creation stage: " + stage);
        }
        return fixture;
    }

    private static LegacyFixture completedLegacyFixture() throws Exception {
        NBTTagCompound playerNbt = new NBTTagCompound();
        NBTTagCompound forgeData = new NBTTagCompound();
        NBTTagCompound persistedData = new NBTTagCompound();
        NBTTagCompound legacyData = new NBTTagCompound();

        legacyData.setString("race", "man");
        legacyData.setString("sex", "male");
        legacyData.setString("appearancePreset", CUSTOM_PRESET_ID);
        legacyData.setBoolean("appearanceInitialized", true);
        legacyData.setBoolean("raceSelectionComplete", true);
        legacyData.setString("startingFaction", "gondor");
        legacyData.setBoolean("factionSelectionComplete", true);
        legacyData.setBoolean("startingFactionApplied", true);
        legacyData.setString("startingWaypoint", "minas_tirith");
        legacyData.setBoolean("startingWaypointApplied", true);
        legacyData.setBoolean("characterCreationComplete", true);
        legacyData.setFloat("dwarfStamina", 63.25F);
        legacyData.setInteger("dwarfFeast", 17);
        legacyData.setBoolean("dwarfStaminaExhausted", true);
        legacyData.setInteger("elfGrappleCooldownTicks", 42);
        legacyData.setBoolean("elfGrappleCleanupPending", true);

        persistedData.setTag(LEGACY_DATA_TAG, legacyData);
        forgeData.setTag(EntityPlayer.PERSISTED_NBT_TAG, persistedData);
        playerNbt.setTag("ForgeData", forgeData);
        return new LegacyFixture(playerNbt, forgeData, legacyData, allocatePlayer(forgeData));
    }

    private static LegacyPlayer allocatePlayer(NBTTagCompound forgeData) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        LegacyPlayer player = (LegacyPlayer) unsafeClass.getMethod("allocateInstance", Class.class)
            .invoke(unsafe, LegacyPlayer.class);
        player.forgeData = forgeData;
        return player;
    }

    private static final class LegacyFixture {

        private final NBTTagCompound playerNbt;
        private final NBTTagCompound forgeData;
        private final NBTTagCompound legacyData;
        private final LegacyPlayer player;

        private LegacyFixture(NBTTagCompound playerNbt, NBTTagCompound forgeData, NBTTagCompound legacyData,
            LegacyPlayer player) {
            this.playerNbt = playerNbt;
            this.forgeData = forgeData;
            this.legacyData = legacyData;
            this.player = player;
        }
    }

    private static final class LegacyPlayer extends EntityPlayerMP {

        private NBTTagCompound forgeData;

        private LegacyPlayer() {
            super(null, null, null, null);
        }

        @Override
        public NBTTagCompound getEntityData() {
            return forgeData;
        }
    }
}
