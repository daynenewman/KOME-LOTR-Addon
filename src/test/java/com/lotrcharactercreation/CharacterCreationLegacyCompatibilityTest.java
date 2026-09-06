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
import com.lotrcharactercreation.creation.CharacterCreationFlowService;
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
        AppearancePresetRegistry.initialize(customSkinRoot.toFile(), null);
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

        AppearancePreset registeredPreset = AppearancePresetRegistry.findById(CUSTOM_PRESET_ID);
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
