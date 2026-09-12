package com.lotrcharactercreation;

import java.io.File;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;

import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.appearance.ServerCustomSkinLibrary;
import com.lotrcharactercreation.body.PlayerRaceEyeService;
import com.lotrcharactercreation.body.PlayerRaceSizeService;
import com.lotrcharactercreation.command.CommandCharacter;
import com.lotrcharactercreation.command.CommandLotrCreation;
import com.lotrcharactercreation.command.CommandLotrRace;
import com.lotrcharactercreation.config.ModConfiguration;
import com.lotrcharactercreation.creation.CharacterCreationFlowService;
import com.lotrcharactercreation.creation.CharacterRecreationService;
import com.lotrcharactercreation.network.ModNetwork;
import com.lotrcharactercreation.proxy.CommonProxy;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;
import com.lotrcharactercreation.sound.RacialPlayerSoundHandler;
import com.lotrcharactercreation.trait.CommonRaceTraitEventHandler;
import com.lotrcharactercreation.trait.DwarfTraitService;
import com.lotrcharactercreation.trait.ElfGrappleService;
import com.lotrcharactercreation.trait.ElfLightnessService;
import com.lotrcharactercreation.trait.HobbitStealthService;
import com.lotrcharactercreation.trait.OrcEnvironmentService;
import com.lotrcharactercreation.trait.RaceTraitService;
import com.lotrcharactercreation.trait.UrukHaiTraitService;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

public class LOTRCharacterCreation {

    private final CommonRaceTraitEventHandler raceTraitEventHandler = new CommonRaceTraitEventHandler();
    private final RacialPlayerSoundHandler racialPlayerSoundHandler = new RacialPlayerSoundHandler();
    private File customSkinRoot;
    private File configurationDirectory;
    private Logger logger;

    public static CommonProxy proxy;

    public void commonPreInitialize(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        configurationDirectory = event.getModConfigurationDirectory();
        File configFile = new File(event.getModConfigurationDirectory(), "lotrcharactercreation.cfg");
        ModConfiguration.load(configFile);
        customSkinRoot = new File(
            new File(event.getModConfigurationDirectory(), "lotrcharactercreation"),
            "custom_skins");
        try {
            if (!customSkinRoot.isDirectory() && !customSkinRoot.mkdirs()) {
                event.getModLog()
                    .warn("Could not create LOTR Character Creation custom skin root: " + customSkinRoot);
            }
        } catch (SecurityException exception) {
            event.getModLog()
                .warn("Could not access LOTR Character Creation custom skin root: " + customSkinRoot, exception);
        }
        event.getModLog()
            .info("LOTR Character Creation custom skin root: " + customSkinRoot.getAbsolutePath());
        ModNetwork.initialize();
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(raceTraitEventHandler);
        MinecraftForge.EVENT_BUS.register(racialPlayerSoundHandler);
    }

    public void initializeSidedProxy() {
        if (proxy == null) {
            throw new IllegalStateException("Character Creation proxy was not assigned by KOME");
        }
        if (customSkinRoot == null) {
            throw new IllegalStateException("Character Creation common pre-initialization has not completed");
        }
        proxy.initialize(customSkinRoot, configurationDirectory);
    }

    @SubscribeEvent
    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        UrukHaiTraitService.prepareForLogin(player);
        if (!PlayerRaceData.isCharacterCreationComplete(player)) {
            CharacterCreationFlowService.ensureInherentSex(player);
        }
        ModNetwork.beginCustomSkinSync(player);
        refreshPlayerStateAndSynchronize(player);
        ModNetwork.sendAllPlayerAppearancesTo(player);
    }

    @SubscribeEvent
    public void playerRespawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            resumeCreationAndSynchronize((EntityPlayerMP) event.player);
        }
    }

    @SubscribeEvent
    public void playerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            ElfGrappleService.handleDimensionChange(player);
            ElfLightnessService.removeEntity(player);
            UrukHaiTraitService.removeEntity(player);
            HobbitStealthService.removePlayer(player);
            resumeCreationAndSynchronize(player);
        }
    }

    @SubscribeEvent
    public void playerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            ElfGrappleService.handleLogout(player);
            ElfLightnessService.removeEntity(player);
            UrukHaiTraitService.clearTransientState(player);
            raceTraitEventHandler.clearTransientConsumptionState(player);
            HobbitStealthService.removePlayer(player);
            OrcEnvironmentService.clearTransientState(player);
            DwarfTraitService.clearTransientState(player);
            PlayerRaceSizeService.removeScheduledServerReapply(player);
            ModNetwork.clearPendingLegacyRequests(player);
            ModNetwork.clearCustomSkinSync(player);
        }
    }

    @SubscribeEvent
    public void playerStartedTracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event) {
        if (event.entityPlayer instanceof EntityPlayerMP && event.target instanceof EntityPlayerMP) {
            ModNetwork.sendPlayerAppearance((EntityPlayerMP) event.target, (EntityPlayerMP) event.entityPlayer);
            ElfGrappleService.synchronizeTo((EntityPlayerMP) event.target, (EntityPlayerMP) event.entityPlayer);
        }
    }

    @SubscribeEvent
    public void playerWokeUp(PlayerWakeUpEvent event) {
        if (event.entityPlayer instanceof EntityPlayerMP && !event.entityPlayer.worldObj.isRemote) {
            PlayerRaceSizeService.scheduleServerReapplyAfterWake((EntityPlayerMP) event.entityPlayer);
        }
    }

    @SubscribeEvent
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ModNetwork.processPendingSelections();
        } else {
            ElfLightnessService.processPendingShoves();
            UrukHaiTraitService.processPendingHeavyBlows();
            ElfGrappleService.processServerTick();
            PlayerRaceSizeService.processScheduledServerReapplies();
        }
    }

    public void registerServerCommands(FMLServerStartingEvent event) {
        ModNetwork.clearAllPendingLegacyRequests();
        ServerCustomSkinLibrary.getInstance().reload(customSkinRoot, logger);
        event.registerServerCommand(new CommandCharacter());
        event.registerServerCommand(new CommandLotrCreation());
        event.registerServerCommand(new CommandLotrRace());
    }

    public static void refreshPlayerStateAndSynchronize(EntityPlayerMP player) {
        PlayerRace presentationRace = CharacterRecreationService.isAwaitingRaceSelection(player) ? PlayerRace.MAN
            : PlayerRaceData.getRace(player);
        PlayerRaceSizeService.applyRaceSize(player, presentationRace);
        PlayerRaceEyeService.applyServerEyeHeight(player, presentationRace);
        RaceTraitService.refreshDerivedAttributes(player);
        ModNetwork.sendPlayerAppearanceToTrackingAndSelf(player);
        ElfGrappleService.synchronizeOwner(player);
    }

    private static void resumeCreationAndSynchronize(EntityPlayerMP player) {
        if (!PlayerRaceData.isCharacterCreationComplete(player)) {
            CharacterCreationFlowService.ensureInherentSex(player);
        }
        refreshPlayerStateAndSynchronize(player);
        if (!PlayerRaceData.isCharacterCreationComplete(player)) {
            ModNetwork.sendCharacterCreationRequired(player);
        }
    }
}
