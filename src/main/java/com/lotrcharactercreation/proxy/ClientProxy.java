package com.lotrcharactercreation.proxy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

import com.lotrcharactercreation.appearance.AppearanceSelectionRules;
import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.client.appearance.ClientAppearanceTextureResolver;
import com.lotrcharactercreation.client.appearance.ClientCustomSkinManager;
import com.lotrcharactercreation.client.appearance.ClientCustomSkinSyncService;
import com.lotrcharactercreation.client.appearance.ClientMinecraftAccountSkinResolver;
import com.lotrcharactercreation.client.appearance.ClientPlayerAppearanceCache;
import com.lotrcharactercreation.client.body.ClientPlayerEyeCameraService;
import com.lotrcharactercreation.client.command.CommandManSkinReview;
import com.lotrcharactercreation.client.gui.GuiAppearanceSelection;
import com.lotrcharactercreation.client.gui.GuiCharacterConfirmation;
import com.lotrcharactercreation.client.gui.GuiRaceSelection;
import com.lotrcharactercreation.client.gui.GuiSexSelection;
import com.lotrcharactercreation.client.gui.GuiStartingFactionSelection;
import com.lotrcharactercreation.client.render.LOTRMapPlayerAppearanceHandler;
import com.lotrcharactercreation.client.render.RacePlayerRenderer;
import com.lotrcharactercreation.client.sound.ClientRacialPlayerSoundHandler;
import com.lotrcharactercreation.client.trait.ClientDwarfTraitState;
import com.lotrcharactercreation.client.trait.ClientElfGrappleState;
import com.lotrcharactercreation.client.trait.ClientHobbitTraitHandler;
import com.lotrcharactercreation.client.trait.ClientUrukRageState;
import com.lotrcharactercreation.creation.CharacterCreationStage;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.race.PlayerRace;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.common.FMLCommonHandler;
import lotr.common.world.map.LOTRWaypoint;

public class ClientProxy extends CommonProxy {

    @Override
    public void initialize(File customSkinRoot, File configurationDirectory) {
        ClientAppearanceTextureResolver.initialize(customSkinRoot, configurationDirectory);
        CommandManSkinReview manSkinReviewCommand = new CommandManSkinReview();
        ClientCommandHandler.instance.registerCommand(manSkinReviewCommand);
        FMLCommonHandler.instance()
            .bus()
            .register(manSkinReviewCommand);
        RacePlayerRenderer racePlayerRenderer = new RacePlayerRenderer();
        RenderingRegistry.registerEntityRenderingHandler(EntityPlayer.class, racePlayerRenderer);
        MinecraftForge.EVENT_BUS.register(racePlayerRenderer);
        FMLCommonHandler.instance()
            .bus()
            .register(racePlayerRenderer);
        ClientPlayerAppearanceCache cache = ClientPlayerAppearanceCache.getInstance();
        ClientPlayerEyeCameraService eyeCameraService = ClientPlayerEyeCameraService.getInstance();
        ClientDwarfTraitState dwarfTraitState = ClientDwarfTraitState.getInstance();
        ClientElfGrappleState elfGrappleState = ClientElfGrappleState.getInstance();
        ClientHobbitTraitHandler hobbitTraitHandler = ClientHobbitTraitHandler.getInstance();
        ClientUrukRageState urukRageState = ClientUrukRageState.getInstance();
        FMLCommonHandler.instance()
            .bus()
            .register(cache);
        FMLCommonHandler.instance()
            .bus()
            .register(ClientCustomSkinManager.getInstance());
        FMLCommonHandler.instance()
            .bus()
            .register(ClientCustomSkinSyncService.getInstance());
        FMLCommonHandler.instance()
            .bus()
            .register(eyeCameraService);
        FMLCommonHandler.instance()
            .bus()
            .register(dwarfTraitState);
        FMLCommonHandler.instance()
            .bus()
            .register(elfGrappleState);
        FMLCommonHandler.instance()
            .bus()
            .register(hobbitTraitHandler);
        FMLCommonHandler.instance()
            .bus()
            .register(urukRageState);
        MinecraftForge.EVENT_BUS.register(cache);
        MinecraftForge.EVENT_BUS.register(ClientMinecraftAccountSkinResolver.getInstance());
        MinecraftForge.EVENT_BUS.register(dwarfTraitState);
        MinecraftForge.EVENT_BUS.register(elfGrappleState);
        MinecraftForge.EVENT_BUS.register(hobbitTraitHandler);
        MinecraftForge.EVENT_BUS.register(urukRageState);
        MinecraftForge.EVENT_BUS.register(new LOTRMapPlayerAppearanceHandler());
        MinecraftForge.EVENT_BUS.register(new ClientRacialPlayerSoundHandler());
    }

    @Override
    public void handleCustomSkinManifestBegin(final int schemaVersion, final long epoch, final long revision,
        final String digest, final int entryCount, final long totalBytes, final int pageCount) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {

            @Override
            public void run() {
                ClientCustomSkinSyncService.getInstance().handleManifestBegin(
                    schemaVersion,
                    epoch,
                    revision,
                    digest,
                    entryCount,
                    totalBytes,
                    pageCount);
            }
        });
    }

    @Override
    public void handleCustomSkinManifestPage(final long epoch, final int pageIndex, final int pageCount,
        List<CustomSkinManifestEntry> entries) {
        final List<CustomSkinManifestEntry> copiedEntries = new ArrayList<CustomSkinManifestEntry>(entries);
        Minecraft.getMinecraft().func_152344_a(new Runnable() {

            @Override
            public void run() {
                ClientCustomSkinSyncService.getInstance()
                    .handleManifestPage(epoch, pageIndex, pageCount, copiedEntries);
            }
        });
    }

    @Override
    public void handleCustomSkinManifestEnd(final long epoch, final long revision, final String digest) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {

            @Override
            public void run() {
                ClientCustomSkinSyncService.getInstance().handleManifestEnd(epoch, revision, digest);
            }
        });
    }

    @Override
    public void handleCustomSkinTransferStart(final long transferId, final long epoch, final long revision,
        final String presetId, final String sha256, final int byteSize, final int chunkCount, final int width,
        final int height) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {

            @Override
            public void run() {
                ClientCustomSkinSyncService.getInstance().handleTransferStart(
                    transferId,
                    epoch,
                    revision,
                    presetId,
                    sha256,
                    byteSize,
                    chunkCount,
                    width,
                    height);
            }
        });
    }

    @Override
    public void handleCustomSkinTransferChunk(final long transferId, final int chunkIndex, byte[] data) {
        final byte[] copiedData = java.util.Arrays.copyOf(data, data.length);
        Minecraft.getMinecraft().func_152344_a(new Runnable() {

            @Override
            public void run() {
                ClientCustomSkinSyncService.getInstance()
                    .handleTransferChunk(transferId, chunkIndex, copiedData);
            }
        });
    }

    @Override
    public void handleCustomSkinTransferEnd(final long transferId, final long epoch, final String presetId,
        final String sha256) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {

            @Override
            public void run() {
                ClientCustomSkinSyncService.getInstance()
                    .handleTransferEnd(transferId, epoch, presetId, sha256);
            }
        });
    }

    @Override
    public void handleCharacterCreationRequired(final String serializedStageId, final String serializedRaceId,
        final String serializedSexId, final String serializedFactionId, final String appearancePresetId,
        final String currentPledgeCode, final boolean automaticStartingAllegiance) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    if (minecraft.thePlayer == null) {
                        return;
                    }

                    CharacterCreationStage stage = CharacterCreationStage.findBySerializedId(serializedStageId);
                    if (stage == CharacterCreationStage.RACE) {
                        minecraft.displayGuiScreen(new GuiRaceSelection());
                        return;
                    }

                    PlayerRace race = PlayerRace.findBySerializedId(serializedRaceId);
                    PlayerSex sex = PlayerSex.findBySerializedId(serializedSexId);
                    StartingFaction faction = StartingFaction.findBySerializedId(serializedFactionId);
                    GuiScreen screen = createMandatoryScreen(
                        stage,
                        race,
                        sex,
                        faction,
                        appearancePresetId,
                        currentPledgeCode,
                        automaticStartingAllegiance);
                    if (screen != null) {
                        minecraft.displayGuiScreen(screen);
                    }
                }
            });
    }

    @Override
    public void handleRaceSelectionAccepted(final String serializedRaceId) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    PlayerRace race = PlayerRace.findBySerializedId(serializedRaceId);
                    if (minecraft.thePlayer == null || race == null) {
                        return;
                    }

                    minecraft.thePlayer.addChatMessage(
                        new ChatComponentText("[LOTR Character Creation] Race selected: " + race.getDisplayName()));
                }
            });
    }

    @Override
    public void handleStartingFactionSelectionAccepted(final String serializedFactionId) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    StartingFaction faction = StartingFaction.findBySerializedId(serializedFactionId);
                    if (minecraft.thePlayer == null || faction == null) {
                        return;
                    }

                    if (minecraft.currentScreen instanceof GuiStartingFactionSelection) {
                        minecraft.displayGuiScreen(null);
                    }
                }
            });
    }

    @Override
    public void handleStartingFactionApplied(final String serializedFactionId) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    StartingFaction faction = StartingFaction.findBySerializedId(serializedFactionId);
                    if (minecraft.thePlayer == null || faction == null) {
                        return;
                    }

                    String message;
                    if (faction == StartingFaction.WANDERER) {
                        message = "You begin your journey as a Wanderer.";
                    } else {
                        message = "Starting faction established: " + faction.getDisplayName() + ".";
                    }
                    minecraft.thePlayer.addChatMessage(new ChatComponentText("[LOTR Character Creation] " + message));
                }
            });
    }

    @Override
    public void handleStartingWaypointApplied(final String serializedFactionId, final String waypointCodeName) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    StartingFaction faction = StartingFaction.findBySerializedId(serializedFactionId);
                    if (minecraft.thePlayer == null || faction == null) {
                        return;
                    }

                    if (minecraft.currentScreen instanceof GuiCharacterConfirmation) {
                        minecraft.displayGuiScreen(null);
                    }

                    String message;
                    if (faction == StartingFaction.WANDERER) {
                        message = "Character creation complete. Your journey begins as a Wanderer.";
                    } else {
                        LOTRWaypoint waypoint = LOTRWaypoint.waypointForName(waypointCodeName);
                        if (waypoint == null) {
                            message = "Character creation complete.";
                        } else {
                            message = "Your journey begins near " + waypoint.getDisplayName()
                                + ". Character creation complete.";
                        }
                    }

                    minecraft.thePlayer.addChatMessage(new ChatComponentText("[LOTR Character Creation] " + message));
                }
            });
    }

    @Override
    public void handlePlayerAppearanceSync(final UUID playerId, final int entityId, final String serializedRaceId,
        final String serializedSexId, final String appearancePresetId, final boolean characterCreationComplete) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    PlayerRace race = PlayerRace.findBySerializedId(serializedRaceId);
                    if (race == null) {
                        return;
                    }

                    PlayerSex sex = PlayerSex.findBySerializedId(serializedSexId);
                    ClientPlayerAppearanceCache.getInstance()
                        .update(playerId, entityId, race, sex, appearancePresetId, characterCreationComplete);
                }
            });
    }

    @Override
    public void handleOpenAppearanceSelection(final String serializedRaceId, final String serializedSexId,
        final String serializedFactionId, final String currentPresetId) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    PlayerRace race = PlayerRace.findBySerializedId(serializedRaceId);
                    PlayerSex sex = PlayerSex.findBySerializedId(serializedSexId);
                    StartingFaction faction = StartingFaction.findBySerializedId(serializedFactionId);
                    if (minecraft.thePlayer == null || race == null || sex == null || faction == null) {
                        return;
                    }

                    minecraft.displayGuiScreen(new GuiAppearanceSelection(race, sex, faction, currentPresetId));
                }
            });
    }

    @Override
    public void handleAppearanceSelectionResult(final boolean accepted, final String presetId) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    if (minecraft.thePlayer == null) {
                        return;
                    }

                    if (minecraft.currentScreen instanceof GuiAppearanceSelection) {
                        ((GuiAppearanceSelection) minecraft.currentScreen).handleSelectionResult(accepted, presetId);
                    }
                    String message = accepted ? "Appearance confirmed."
                        : "Appearance selection was rejected because your character state changed.";
                    minecraft.thePlayer.addChatMessage(new ChatComponentText("[LOTR Character Creation] " + message));
                }
            });
    }

    @Override
    public void handleOpenSexSelection(final String serializedRaceId, final String serializedSexId) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    PlayerRace race = PlayerRace.findBySerializedId(serializedRaceId);
                    PlayerSex sex = PlayerSex.findBySerializedId(serializedSexId);
                    if (minecraft.thePlayer == null || race == null) {
                        return;
                    }

                    minecraft.displayGuiScreen(new GuiSexSelection(race, sex));
                }
            });
    }

    @Override
    public void handleSexSelectionResult(final boolean accepted, final String serializedSexId) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    Minecraft minecraft = Minecraft.getMinecraft();
                    if (minecraft.thePlayer == null) {
                        return;
                    }

                    if (minecraft.currentScreen instanceof GuiSexSelection) {
                        ((GuiSexSelection) minecraft.currentScreen).handleSelectionResult(accepted, serializedSexId);
                    }
                    PlayerSex sex = PlayerSex.findBySerializedId(serializedSexId);
                    String message = accepted && sex != null ? "Sex selected: " + sex.getDisplayName() + "."
                        : "Sex selection was rejected because your character state changed.";
                    minecraft.thePlayer.addChatMessage(new ChatComponentText("[LOTR Character Creation] " + message));
                }
            });
    }

    @Override
    public void handleDwarfTraitState(final boolean active, final float stamina, final int feast,
        final boolean exhausted) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    ClientDwarfTraitState.getInstance()
                        .update(active, stamina, feast, exhausted);
                }
            });
    }

    @Override
    public void handleUrukRageState(final boolean active, final float rage) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    ClientUrukRageState.getInstance()
                        .update(active, rage);
                }
            });
    }

    @Override
    public void handleElfGrappleState(final int playerEntityId, final int targetEntityId, final boolean active,
        final boolean ready) {
        Minecraft.getMinecraft()
            .func_152344_a(new Runnable() {

                @Override
                public void run() {
                    ClientElfGrappleState.getInstance()
                        .update(playerEntityId, targetEntityId, active, ready);
                }
            });
    }

    private static GuiScreen createMandatoryScreen(CharacterCreationStage stage, PlayerRace race, PlayerSex sex,
        StartingFaction faction, String appearancePresetId, String currentPledgeCode,
        boolean automaticStartingAllegiance) {
        if (stage == null || race == null) {
            return null;
        }

        GuiSexSelection sexScreen = null;
        if (AppearanceSelectionRules.supportsSelectableSex(race)) {
            sexScreen = new GuiSexSelection(null, race, sex, true);
        }
        if (stage == CharacterCreationStage.SEX) {
            return sexScreen;
        }

        if (faction == null) {
            return null;
        }
        GuiStartingFactionSelection factionScreen = new GuiStartingFactionSelection(sexScreen, race, faction, true);
        if (stage == CharacterCreationStage.FACTION) {
            return factionScreen;
        }

        if (sex == null) {
            return null;
        }
        GuiAppearanceSelection appearanceScreen = new GuiAppearanceSelection(
            factionScreen,
            race,
            sex,
            faction,
            appearancePresetId,
            true);
        if (stage == CharacterCreationStage.APPEARANCE) {
            return appearanceScreen;
        }
        if (stage == CharacterCreationStage.CONFIRMATION) {
            return new GuiCharacterConfirmation(
                appearanceScreen,
                race,
                sex,
                faction,
                appearancePresetId,
                currentPledgeCode,
                automaticStartingAllegiance);
        }
        return null;
    }
}
