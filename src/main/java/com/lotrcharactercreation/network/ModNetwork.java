package com.lotrcharactercreation.network;

import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.AppearancePresetCatalog;
import com.lotrcharactercreation.appearance.AppearanceSelectionRules;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.appearance.ServerCustomSkinLibrary;
import com.lotrcharactercreation.body.PlayerRaceEyeService;
import com.lotrcharactercreation.body.PlayerRaceSizeService;
import com.lotrcharactercreation.config.ModConfiguration;
import com.lotrcharactercreation.creation.CharacterCreationFlowService;
import com.lotrcharactercreation.creation.CharacterRecreationService;
import com.lotrcharactercreation.creation.CharacterCreationStage;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.faction.StartingFactionApplication;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;
import com.lotrcharactercreation.trait.ElfGrappleService;
import com.lotrcharactercreation.trait.RaceTraitService;
import com.lotrcharactercreation.waypoint.StartingWaypointApplication;
import com.lotrcharactercreation.waypoint.StartingWaypointApplication.Result;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.relauncher.Side;
import lotr.common.LOTRLevelData;
import lotr.common.fac.LOTRFaction;

public final class ModNetwork {

    private static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("lotrcreation");
    private static final Queue<PendingRaceSelection> PENDING_RACE_SELECTIONS = new ConcurrentLinkedQueue<>();
    private static final Queue<PendingStartingFactionSelection> PENDING_FACTION_SELECTIONS = new ConcurrentLinkedQueue<>();
    private static final Queue<PendingAppearanceSelection> PENDING_APPEARANCE_SELECTIONS = new ConcurrentLinkedQueue<>();
    private static final Queue<PendingSexSelection> PENDING_SEX_SELECTIONS = new ConcurrentLinkedQueue<>();
    private static final Queue<PendingCharacterCreationBack> PENDING_CHARACTER_CREATION_BACKS = new ConcurrentLinkedQueue<>();
    private static final Queue<PendingCharacterFinalization> PENDING_CHARACTER_FINALIZATIONS = new ConcurrentLinkedQueue<>();
    private static final Queue<PendingElfGrappleAttack> PENDING_ELF_GRAPPLE_ATTACKS = new ConcurrentLinkedQueue<>();
    private static final Set<UUID> PENDING_CHARACTER_FINALIZATION_IDS = Collections
        .newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private static final Object LEGACY_REQUEST_QUEUE_LOCK = new Object();
    private static final LegacyRequestAdmission LEGACY_REQUEST_ADMISSION = new LegacyRequestAdmission(
        LegacyC2SProtocol.MAX_PENDING_ACTIONS_PER_PLAYER,
        LegacyC2SProtocol.MAX_PENDING_ACTIONS_TOTAL);

    private ModNetwork() {}

    public static void initialize() {
        CHANNEL.registerMessage(
            CharacterCreationRequiredMessage.Handler.class,
            CharacterCreationRequiredMessage.class,
            0,
            Side.CLIENT);
        CHANNEL.registerMessage(RaceSelectionMessage.Handler.class, RaceSelectionMessage.class, 1, Side.SERVER);
        CHANNEL.registerMessage(
            RaceSelectionAcceptedMessage.Handler.class,
            RaceSelectionAcceptedMessage.class,
            2,
            Side.CLIENT);
        CHANNEL.registerMessage(
            StartingFactionSelectionMessage.Handler.class,
            StartingFactionSelectionMessage.class,
            3,
            Side.SERVER);
        CHANNEL.registerMessage(
            StartingFactionSelectionAcceptedMessage.Handler.class,
            StartingFactionSelectionAcceptedMessage.class,
            4,
            Side.CLIENT);
        CHANNEL.registerMessage(
            StartingFactionAppliedMessage.Handler.class,
            StartingFactionAppliedMessage.class,
            5,
            Side.CLIENT);
        CHANNEL.registerMessage(
            StartingWaypointAppliedMessage.Handler.class,
            StartingWaypointAppliedMessage.class,
            6,
            Side.CLIENT);
        CHANNEL.registerMessage(
            PlayerAppearanceSyncMessage.Handler.class,
            PlayerAppearanceSyncMessage.class,
            7,
            Side.CLIENT);
        CHANNEL.registerMessage(
            OpenAppearanceSelectionMessage.Handler.class,
            OpenAppearanceSelectionMessage.class,
            8,
            Side.CLIENT);
        CHANNEL.registerMessage(
            AppearanceSelectionMessage.Handler.class,
            AppearanceSelectionMessage.class,
            9,
            Side.SERVER);
        CHANNEL.registerMessage(
            AppearanceSelectionResultMessage.Handler.class,
            AppearanceSelectionResultMessage.class,
            10,
            Side.CLIENT);
        CHANNEL.registerMessage(OpenSexSelectionMessage.Handler.class, OpenSexSelectionMessage.class, 11, Side.CLIENT);
        CHANNEL.registerMessage(SexSelectionMessage.Handler.class, SexSelectionMessage.class, 12, Side.SERVER);
        CHANNEL
            .registerMessage(SexSelectionResultMessage.Handler.class, SexSelectionResultMessage.class, 13, Side.CLIENT);
        CHANNEL.registerMessage(
            CharacterFinalizationMessage.Handler.class,
            CharacterFinalizationMessage.class,
            14,
            Side.SERVER);
        CHANNEL.registerMessage(
            CharacterCreationBackMessage.Handler.class,
            CharacterCreationBackMessage.class,
            15,
            Side.SERVER);
        CHANNEL.registerMessage(DwarfTraitStateMessage.Handler.class, DwarfTraitStateMessage.class, 16, Side.CLIENT);
        CHANNEL.registerMessage(UrukRageStateMessage.Handler.class, UrukRageStateMessage.class, 17, Side.CLIENT);
        CHANNEL.registerMessage(ElfGrappleStateMessage.Handler.class, ElfGrappleStateMessage.class, 18, Side.CLIENT);
        CHANNEL.registerMessage(ElfGrappleAttackMessage.Handler.class, ElfGrappleAttackMessage.class, 19, Side.SERVER);
        CHANNEL.registerMessage(
            CustomSkinManifestBeginMessage.Handler.class,
            CustomSkinManifestBeginMessage.class,
            20,
            Side.CLIENT);
        CHANNEL.registerMessage(
            CustomSkinManifestPageMessage.Handler.class,
            CustomSkinManifestPageMessage.class,
            21,
            Side.CLIENT);
        CHANNEL.registerMessage(
            CustomSkinManifestEndMessage.Handler.class,
            CustomSkinManifestEndMessage.class,
            22,
            Side.CLIENT);
        CHANNEL.registerMessage(
            CustomSkinTransferStartMessage.Handler.class,
            CustomSkinTransferStartMessage.class,
            23,
            Side.CLIENT);
        CHANNEL.registerMessage(
            CustomSkinTransferChunkMessage.Handler.class,
            CustomSkinTransferChunkMessage.class,
            24,
            Side.CLIENT);
        CHANNEL.registerMessage(
            CustomSkinTransferEndMessage.Handler.class,
            CustomSkinTransferEndMessage.class,
            25,
            Side.CLIENT);
        CHANNEL.registerMessage(
            CustomSkinManifestReadyMessage.Handler.class,
            CustomSkinManifestReadyMessage.class,
            26,
            Side.SERVER);
        CHANNEL.registerMessage(
            CustomSkinRequestPageMessage.Handler.class,
            CustomSkinRequestPageMessage.class,
            27,
            Side.SERVER);
        CHANNEL.registerMessage(
            CustomSkinTransferResultMessage.Handler.class,
            CustomSkinTransferResultMessage.class,
            28,
            Side.SERVER);
    }

    public static void beginCustomSkinSync(EntityPlayerMP player) {
        ServerCustomSkinSyncService.getInstance().beginSession(player);
    }

    public static void clearCustomSkinSync(EntityPlayerMP player) {
        ServerCustomSkinSyncService.getInstance().clearPlayer(player);
    }

    public static void sendCustomSkinManifestReady(int schemaVersion, long epoch, long revision, String digest) {
        CHANNEL.sendToServer(new CustomSkinManifestReadyMessage(schemaVersion, epoch, revision, digest));
    }

    public static void sendCustomSkinRequests(long epoch, long revision,
        List<CustomSkinRequestIdentity> identities) {
        CHANNEL.sendToServer(new CustomSkinRequestPageMessage(epoch, revision, identities));
    }

    public static void sendCustomSkinTransferResult(long transferId, long epoch, String presetId, String sha256,
        int resultCode) {
        CHANNEL.sendToServer(new CustomSkinTransferResultMessage(
            transferId,
            epoch,
            presetId,
            sha256,
            resultCode));
    }

    static void sendTo(IMessage message, EntityPlayerMP player) {
        CHANNEL.sendTo(message, player);
    }

    static void refreshAppearanceStateAfterManifestReady(EntityPlayerMP player) {
        sendPlayerAppearanceToTrackingAndSelf(player);
        sendAllPlayerAppearancesTo(player);
        if (!PlayerRaceData.isCharacterCreationComplete(player)) {
            sendCharacterCreationRequired(player);
        }
    }

    public static void sendCharacterCreationRequired(EntityPlayerMP player) {
        CharacterCreationStage stage = CharacterCreationFlowService.getNextRequiredStage(player);
        if (stage == CharacterCreationStage.COMPLETE) {
            return;
        }

        PlayerRace race = PlayerRaceData.getRace(player);
        PlayerSex sex = PlayerRaceData.getSex(player);
        StartingFaction faction = PlayerRaceData.getStartingFaction(player);
        LOTRFaction currentPledge = LOTRLevelData.getData(player)
            .getPledgeFaction();
        CHANNEL.sendTo(
            new CharacterCreationRequiredMessage(
                stage.getSerializedId(),
                race.getSerializedId(),
                sex == null ? null : sex.getSerializedId(),
                faction.getSerializedId(),
                PlayerRaceData.getAppearancePresetId(player),
                StartingFactionApplication.pledgeCode(currentPledge),
                ModConfiguration.isAutomaticStartingAllegianceEnabled()
                    && !CharacterRecreationService.isInProgress(player)),
            player);
    }

    public static void sendCharacterRecreationCompleted(EntityPlayerMP player) {
        PlayerRace race = PlayerRaceData.getRace(player);
        PlayerSex sex = PlayerRaceData.getSex(player);
        StartingFaction faction = PlayerRaceData.getStartingFaction(player);
        CHANNEL.sendTo(
            new CharacterCreationRequiredMessage(
                CharacterCreationStage.COMPLETE.getSerializedId(),
                race.getSerializedId(),
                sex == null ? null : sex.getSerializedId(),
                faction.getSerializedId(),
                PlayerRaceData.getAppearancePresetId(player),
                "",
                false),
            player);
    }

    public static void sendRaceSelection(PlayerRace race) {
        CHANNEL.sendToServer(new RaceSelectionMessage(race.getSerializedId()));
    }

    public static void sendDwarfTraitState(EntityPlayerMP player, boolean active, float stamina, int feast,
        boolean exhausted) {
        CHANNEL.sendTo(new DwarfTraitStateMessage(active, stamina, feast, exhausted), player);
    }

    public static void sendUrukRageState(EntityPlayerMP player, boolean active, float rage) {
        CHANNEL.sendTo(new UrukRageStateMessage(active, rage), player);
    }

    public static void sendElfGrappleState(EntityPlayerMP subject, EntityPlayerMP recipient, int targetEntityId,
        boolean active, boolean ready) {
        CHANNEL.sendTo(new ElfGrappleStateMessage(subject.getEntityId(), targetEntityId, active, ready), recipient);
    }

    public static void sendElfGrappleAttack() {
        CHANNEL.sendToServer(new ElfGrappleAttackMessage());
    }

    public static void enqueueElfGrappleAttack(EntityPlayerMP player) {
        enqueueLegacyRequest(PENDING_ELF_GRAPPLE_ATTACKS, new PendingElfGrappleAttack(player));
    }

    public static void sendPlayerAppearance(EntityPlayerMP subject, EntityPlayerMP recipient) {
        CHANNEL.sendTo(createPlayerAppearanceMessage(subject), recipient);
    }

    public static void sendPlayerAppearanceToTrackingAndSelf(EntityPlayerMP subject) {
        CHANNEL.sendToAll(createPlayerAppearanceMessage(subject));
    }

    public static void sendAllPlayerAppearancesTo(EntityPlayerMP recipient) {
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) {
            return;
        }

        for (Object entry : server.getConfigurationManager().playerEntityList) {
            if (entry instanceof EntityPlayerMP && entry != recipient) {
                sendPlayerAppearance((EntityPlayerMP) entry, recipient);
            }
        }
    }

    public static void enqueueRaceSelection(EntityPlayerMP player, String serializedRaceId) {
        if (LegacyC2SProtocol.isValidRequiredString(serializedRaceId, LegacyC2SProtocol.MAX_RACE_ID_BYTES)) {
            enqueueLegacyRequest(PENDING_RACE_SELECTIONS, new PendingRaceSelection(player, serializedRaceId));
        }
    }

    public static void sendStartingFactionSelection(StartingFaction faction) {
        CHANNEL.sendToServer(new StartingFactionSelectionMessage(faction.getSerializedId()));
    }

    public static void enqueueStartingFactionSelection(EntityPlayerMP player, String serializedFactionId) {
        if (LegacyC2SProtocol.isValidRequiredString(serializedFactionId, LegacyC2SProtocol.MAX_FACTION_ID_BYTES)) {
            enqueueLegacyRequest(
                PENDING_FACTION_SELECTIONS,
                new PendingStartingFactionSelection(player, serializedFactionId));
        }
    }

    public static void sendAppearanceSelectionOpen(EntityPlayerMP player) {
        PlayerRace race = PlayerRaceData.getRace(player);
        PlayerSex sex = AppearanceSelectionRules.getSelectionSex(race, PlayerRaceData.getSex(player));
        StartingFaction faction = PlayerRaceData.getStartingFaction(player);
        AppearancePresetCatalog catalog = ServerCustomSkinLibrary.getInstance().getCurrentCatalog();
        if (AppearanceSelectionRules.getCandidates(catalog, race, sex, faction)
            .isEmpty()) {
            String detail = sex == null ? "Set a valid appearance sex first."
                : "No appearances match the current race and faction.";
            player.addChatMessage(new ChatComponentText("[LOTR Character Creation] " + detail));
            return;
        }

        String currentPresetId = PlayerRaceData.getAppearancePresetId(player);
        if (!AppearanceSelectionRules.isPresetAllowed(catalog, race, sex, faction, currentPresetId)) {
            currentPresetId = null;
        }
        CHANNEL.sendTo(
            new OpenAppearanceSelectionMessage(
                race.getSerializedId(),
                sex.getSerializedId(),
                faction.getSerializedId(),
                currentPresetId),
            player);
    }

    public static void sendAppearanceSelection(String presetId) {
        CHANNEL.sendToServer(new AppearanceSelectionMessage(presetId));
    }

    public static void enqueueAppearanceSelection(EntityPlayerMP player, String presetId) {
        if (LegacyC2SProtocol.isValidRequiredString(presetId, LegacyC2SProtocol.MAX_APPEARANCE_PRESET_ID_BYTES)) {
            enqueueLegacyRequest(PENDING_APPEARANCE_SELECTIONS, new PendingAppearanceSelection(player, presetId));
        }
    }

    public static void sendSexSelectionOpen(EntityPlayerMP player) {
        PlayerRace race = PlayerRaceData.getRace(player);
        if (!AppearanceSelectionRules.supportsSelectableSex(race)) {
            String detail = race == PlayerRace.ORC || race == PlayerRace.URUK_HAI
                ? race.getDisplayName() + " uses sex=none and cannot choose male/female."
                : race.getDisplayName() + " does not use the mod's sex-selection screen.";
            player.addChatMessage(new ChatComponentText("[LOTR Character Creation] " + detail));
            return;
        }

        PlayerSex currentSex = PlayerRaceData.getSex(player);
        CHANNEL.sendTo(
            new OpenSexSelectionMessage(
                race.getSerializedId(),
                currentSex == null ? null : currentSex.getSerializedId()),
            player);
    }

    public static void sendSexSelection(PlayerSex sex) {
        CHANNEL.sendToServer(new SexSelectionMessage(sex.getSerializedId()));
    }

    public static void enqueueSexSelection(EntityPlayerMP player, String serializedSexId) {
        if (LegacyC2SProtocol.isValidRequiredString(serializedSexId, LegacyC2SProtocol.MAX_SEX_ID_BYTES)) {
            enqueueLegacyRequest(PENDING_SEX_SELECTIONS, new PendingSexSelection(player, serializedSexId));
        }
    }

    public static void sendCharacterFinalization(boolean replacementConfirmed, String expectedExistingPledgeCode) {
        CHANNEL.sendToServer(new CharacterFinalizationMessage(replacementConfirmed, expectedExistingPledgeCode));
    }

    public static void sendCharacterCreationBack(CharacterCreationStage sourceStage) {
        CHANNEL.sendToServer(new CharacterCreationBackMessage(sourceStage.getSerializedId()));
    }

    public static void enqueueCharacterCreationBack(EntityPlayerMP player, String serializedSourceStageId) {
        if (LegacyC2SProtocol.isValidRequiredString(serializedSourceStageId, LegacyC2SProtocol.MAX_STAGE_ID_BYTES)) {
            enqueueLegacyRequest(
                PENDING_CHARACTER_CREATION_BACKS,
                new PendingCharacterCreationBack(player, serializedSourceStageId));
        }
    }

    public static void enqueueCharacterFinalization(EntityPlayerMP player, boolean replacementConfirmed,
        String expectedExistingPledgeCode) {
        if (player == null || !LegacyC2SProtocol
            .isValidNullableString(expectedExistingPledgeCode, LegacyC2SProtocol.MAX_PLEDGE_CODE_BYTES)) {
            return;
        }

        synchronized (LEGACY_REQUEST_QUEUE_LOCK) {
            UUID playerId = player.getUniqueID();
            if (PENDING_CHARACTER_FINALIZATION_IDS.add(playerId)) {
                PendingCharacterFinalization request = new PendingCharacterFinalization(
                    player,
                    replacementConfirmed,
                    expectedExistingPledgeCode);
                if (admitLegacyRequest(player)) {
                    PENDING_CHARACTER_FINALIZATIONS.add(request);
                } else {
                    PENDING_CHARACTER_FINALIZATION_IDS.remove(playerId);
                }
            }
        }
    }

    public static void clearPendingLegacyRequests(EntityPlayerMP player) {
        if (player == null) {
            return;
        }

        synchronized (LEGACY_REQUEST_QUEUE_LOCK) {
            UUID playerId = player.getUniqueID();
            discardPendingRequests(PENDING_RACE_SELECTIONS, playerId);
            discardPendingRequests(PENDING_FACTION_SELECTIONS, playerId);
            discardPendingRequests(PENDING_APPEARANCE_SELECTIONS, playerId);
            discardPendingRequests(PENDING_SEX_SELECTIONS, playerId);
            discardPendingRequests(PENDING_CHARACTER_CREATION_BACKS, playerId);
            discardPendingRequests(PENDING_CHARACTER_FINALIZATIONS, playerId);
            discardPendingRequests(PENDING_ELF_GRAPPLE_ATTACKS, playerId);
            PENDING_CHARACTER_FINALIZATION_IDS.remove(playerId);
            LEGACY_REQUEST_ADMISSION.clearPlayer(playerId);
        }
    }

    public static void clearAllPendingLegacyRequests() {
        synchronized (LEGACY_REQUEST_QUEUE_LOCK) {
            PENDING_RACE_SELECTIONS.clear();
            PENDING_FACTION_SELECTIONS.clear();
            PENDING_APPEARANCE_SELECTIONS.clear();
            PENDING_SEX_SELECTIONS.clear();
            PENDING_CHARACTER_CREATION_BACKS.clear();
            PENDING_CHARACTER_FINALIZATIONS.clear();
            PENDING_ELF_GRAPPLE_ATTACKS.clear();
            PENDING_CHARACTER_FINALIZATION_IDS.clear();
            LEGACY_REQUEST_ADMISSION.clearAll();
        }
    }

    private static void finalizeCharacterCreation(PendingCharacterFinalization request) {
        EntityPlayerMP player = request.player;
        if (!CharacterCreationFlowService.isReadyForFinalization(player)) {
            sendCharacterCreationRequired(player);
            return;
        }

        if (CharacterRecreationService.isInProgress(player)) {
            if (!CharacterRecreationService.complete(player)) {
                sendCharacterCreationRequired(player);
                return;
            }

            PlayerRaceSizeService.applyStoredRaceSize(player);
            PlayerRaceEyeService.applyStoredServerEyeHeight(player);
            RaceTraitService.refreshDerivedAttributes(player);
            sendPlayerAppearanceToTrackingAndSelf(player);
            sendCharacterRecreationCompleted(player);
            return;
        }

        StartingFaction selectedFaction = PlayerRaceData.getStartingFaction(player);
        LOTRFaction actualExistingPledge = LOTRLevelData.getData(player)
            .getPledgeFaction();
        String actualExistingPledgeCode = StartingFactionApplication.pledgeCode(actualExistingPledge);
        String selectedPledgeCode = StartingFactionApplication.pledgeCode(selectedFaction.getLotrFaction());
        boolean automaticStartingAllegiance = ModConfiguration.isAutomaticStartingAllegianceEnabled();
        if (!StartingFactionApplication.isFinalizationAuthorized(
            automaticStartingAllegiance,
            PlayerRaceData.isCharacterCreationComplete(player),
            actualExistingPledgeCode,
            selectedPledgeCode,
            request.replacementConfirmed,
            request.expectedExistingPledgeCode)) {
            boolean pledgeStateChanged = automaticStartingAllegiance
                && !actualExistingPledgeCode.equals(normalizePledgeCode(request.expectedExistingPledgeCode));
            String detail = pledgeStateChanged
                ? "Your LOTR pledge changed before confirmation. Review the updated pledge warning and confirm again."
                : "You must explicitly confirm the displayed LOTR pledge replacement.";
            player.addChatMessage(new ChatComponentText("[LOTR Character Creation] " + detail));
            sendCharacterCreationRequired(player);
            return;
        }

        StartingFaction appliedFaction = StartingFactionApplication.tryApply(
            player,
            request.replacementConfirmed,
            request.expectedExistingPledgeCode);
        if (appliedFaction != null) {
            CHANNEL.sendTo(new StartingFactionAppliedMessage(appliedFaction.getSerializedId()), player);
        }

        applyStartingWaypointIfReady(player);
        if (PlayerRaceData.isCharacterCreationComplete(player)) {
            // LOTR's incremental alignment packet is keyed by the server player UUID. In
            // offline-mode development/multiplayer that UUID can differ from the client's
            // session UUID, leaving the local HUD cache stale. Reuse LOTR's login refresh,
            // which applies the authoritative data directly to the local client player.
            LOTRLevelData.sendPlayerData(player);
            sendPlayerAppearanceToTrackingAndSelf(player);
        } else {
            if (!PlayerRaceData.isStartingFactionApplied(player)) {
                player.addChatMessage(
                    new ChatComponentText(
                        "[LOTR Character Creation] Starting allegiance could not be applied. Character creation remains incomplete."));
            }
            sendCharacterCreationRequired(player);
        }
    }

    private static void applyStartingWaypointIfReady(EntityPlayerMP player) {
        Result result = StartingWaypointApplication.tryApply(player);
        if (result == null) {
            return;
        }

        if (result.isSuccessful()) {
            String waypointCodeName = result.getWaypoint() == null ? ""
                : result.getWaypoint()
                    .getCodeName();
            CHANNEL.sendTo(
                new StartingWaypointAppliedMessage(
                    result.getStartingFaction()
                        .getSerializedId(),
                    waypointCodeName),
                player);
        } else {
            player.addChatMessage(
                new ChatComponentText(
                    "[LOTR Character Creation] " + result.getFailureMessage()
                        + " Character creation remains incomplete."));
        }
    }

    public static void processPendingSelections() {
        MinecraftServer server = MinecraftServer.getServer();
        ServerCustomSkinSyncService.getInstance().processPending(server);
        processPendingCharacterCreationBacks(server);
        processPendingRaceSelections(server);
        processPendingStartingFactionSelections(server);
        processPendingSexSelections(server);
        processPendingAppearanceSelections(server);
        processPendingCharacterFinalizations(server);
        processPendingElfGrappleAttacks(server);
    }

    private static void processPendingElfGrappleAttacks(MinecraftServer server) {
        PendingElfGrappleAttack request;
        while ((request = PENDING_ELF_GRAPPLE_ATTACKS.poll()) != null) {
            try {
                if (isConnected(server, request.player)) {
                    ElfGrappleService.handleAttackRequest(request.player);
                }
            } finally {
                releaseLegacyRequest(request.player);
            }
        }
    }

    private static void processPendingRaceSelections(MinecraftServer server) {
        PendingRaceSelection selection;
        while ((selection = PENDING_RACE_SELECTIONS.poll()) != null) {
            try {
                if (!isConnected(server, selection.player)) {
                    continue;
                }

                PlayerRace race = PlayerRace.findBySerializedId(selection.serializedRaceId);
                if (race == null || !CharacterCreationFlowService.selectRace(selection.player, race)) {
                    if (!PlayerRaceData.isCharacterCreationComplete(selection.player)) {
                        sendCharacterCreationRequired(selection.player);
                    }
                    continue;
                }

                PlayerRaceSizeService.applyStoredRaceSize(selection.player);
                PlayerRaceEyeService.applyStoredServerEyeHeight(selection.player);
                sendPlayerAppearanceToTrackingAndSelf(selection.player);
                CHANNEL.sendTo(new RaceSelectionAcceptedMessage(race.getSerializedId()), selection.player);
                sendCharacterCreationRequired(selection.player);
            } finally {
                releaseLegacyRequest(selection.player);
            }
        }
    }

    private static void processPendingCharacterCreationBacks(MinecraftServer server) {
        PendingCharacterCreationBack request;
        while ((request = PENDING_CHARACTER_CREATION_BACKS.poll()) != null) {
            try {
                if (!isConnected(server, request.player)) {
                    continue;
                }

                CharacterCreationStage sourceStage = CharacterCreationStage
                    .findBySerializedId(request.serializedSourceStageId);
                boolean movedBack = CharacterCreationFlowService.goBackFrom(request.player, sourceStage);
                boolean finalizationStarted = PlayerRaceData.isStartingFactionApplied(request.player)
                    || PlayerRaceData.isStartingWaypointApplied(request.player);
                if (movedBack) {
                    sendPlayerAppearanceToTrackingAndSelf(request.player);
                } else if (PlayerRaceData.isCharacterCreationComplete(request.player)) {
                    request.player.addChatMessage(new ChatComponentText("Your character has already been created."));
                    continue;
                } else if (finalizationStarted) {
                    request.player.addChatMessage(new ChatComponentText("Character creation is not available."));
                }

                sendCharacterCreationRequired(request.player);
            } finally {
                releaseLegacyRequest(request.player);
            }
        }
    }

    private static void processPendingStartingFactionSelections(MinecraftServer server) {
        PendingStartingFactionSelection selection;
        while ((selection = PENDING_FACTION_SELECTIONS.poll()) != null) {
            try {
                if (!isConnected(server, selection.player)) {
                    continue;
                }

                StartingFaction faction = StartingFaction.findBySerializedId(selection.serializedFactionId);
                if (!CharacterCreationFlowService.selectStartingFaction(selection.player, faction)) {
                    if (!PlayerRaceData.isCharacterCreationComplete(selection.player)) {
                        sendCharacterCreationRequired(selection.player);
                    }
                    continue;
                }

                sendPlayerAppearanceToTrackingAndSelf(selection.player);
                CHANNEL.sendTo(
                    new StartingFactionSelectionAcceptedMessage(faction.getSerializedId()),
                    selection.player);
                sendCharacterCreationRequired(selection.player);
            } finally {
                releaseLegacyRequest(selection.player);
            }
        }
    }

    private static void processPendingAppearanceSelections(MinecraftServer server) {
        PendingAppearanceSelection selection;
        while ((selection = PENDING_APPEARANCE_SELECTIONS.poll()) != null) {
            try {
                if (!isConnected(server, selection.player)) {
                    continue;
                }

                boolean accepted = CharacterCreationFlowService.selectAppearance(selection.player, selection.presetId);
                if (accepted) {
                    sendPlayerAppearanceToTrackingAndSelf(selection.player);
                }

                CHANNEL.sendTo(
                    new AppearanceSelectionResultMessage(accepted, accepted ? selection.presetId : ""),
                    selection.player);
                if (!PlayerRaceData.isCharacterCreationComplete(selection.player)) {
                    sendCharacterCreationRequired(selection.player);
                }
            } finally {
                releaseLegacyRequest(selection.player);
            }
        }
    }

    private static void processPendingSexSelections(MinecraftServer server) {
        PendingSexSelection selection;
        while ((selection = PENDING_SEX_SELECTIONS.poll()) != null) {
            try {
                if (!isConnected(server, selection.player)) {
                    continue;
                }

                PlayerSex sex = PlayerSex.findBySerializedId(selection.serializedSexId);
                boolean accepted = CharacterCreationFlowService.selectSex(selection.player, sex);
                if (accepted) {
                    sendPlayerAppearanceToTrackingAndSelf(selection.player);
                }

                CHANNEL.sendTo(
                    new SexSelectionResultMessage(accepted, accepted ? sex.getSerializedId() : ""),
                    selection.player);
                if (!PlayerRaceData.isCharacterCreationComplete(selection.player)) {
                    sendCharacterCreationRequired(selection.player);
                }
            } finally {
                releaseLegacyRequest(selection.player);
            }
        }
    }

    private static void processPendingCharacterFinalizations(MinecraftServer server) {
        PendingCharacterFinalization request;
        while ((request = PENDING_CHARACTER_FINALIZATIONS.poll()) != null) {
            try {
                if (isConnected(server, request.player)
                    && !PlayerRaceData.isCharacterCreationComplete(request.player)) {
                    finalizeCharacterCreation(request);
                }
            } finally {
                PENDING_CHARACTER_FINALIZATION_IDS.remove(request.player.getUniqueID());
                releaseLegacyRequest(request.player);
            }
        }
    }

    private static String normalizePledgeCode(String pledgeCode) {
        return pledgeCode == null ? "" : pledgeCode;
    }

    private static <T extends PendingLegacyRequest> void enqueueLegacyRequest(Queue<T> queue, T request) {
        if (request.player == null) {
            return;
        }

        synchronized (LEGACY_REQUEST_QUEUE_LOCK) {
            if (admitLegacyRequest(request.player)) {
                queue.add(request);
            }
        }
    }

    private static boolean admitLegacyRequest(EntityPlayerMP player) {
        return player != null && LEGACY_REQUEST_ADMISSION.tryAcquire(player.getUniqueID());
    }

    private static void releaseLegacyRequest(EntityPlayerMP player) {
        if (player != null) {
            LEGACY_REQUEST_ADMISSION.release(player.getUniqueID());
        }
    }

    private static <T extends PendingLegacyRequest> void discardPendingRequests(Queue<T> queue, UUID playerId) {
        for (T request : queue) {
            if (playerId.equals(request.player.getUniqueID()) && queue.remove(request)) {
                releaseLegacyRequest(request.player);
            }
        }
    }

    private static boolean isConnected(MinecraftServer server, EntityPlayerMP player) {
        return server != null && server.getConfigurationManager().playerEntityList.contains(player);
    }

    private static PlayerAppearanceSyncMessage createPlayerAppearanceMessage(EntityPlayerMP player) {
        boolean awaitingRecreationRace = CharacterRecreationService.isAwaitingRaceSelection(player);
        PlayerRace race = awaitingRecreationRace ? PlayerRace.MAN : PlayerRaceData.getRace(player);
        PlayerSex sex = awaitingRecreationRace ? null : PlayerRaceData.getSex(player);
        String presetId = awaitingRecreationRace ? null : PlayerRaceData.getAppearancePresetId(player);
        if (!awaitingRecreationRace && (!PlayerRaceData.isAppearanceInitialized(player)
            || !AppearancePresetRegistry.isPresetValid(
                ServerCustomSkinLibrary.getInstance().getCurrentCatalog(), race, sex, presetId))) {
            presetId = null;
        }

        return new PlayerAppearanceSyncMessage(
            player.getUniqueID(),
            player.getEntityId(),
            race.getSerializedId(),
            sex == null ? null : sex.getSerializedId(),
            presetId,
            PlayerRaceData.isCharacterCreationComplete(player));
    }

    private abstract static class PendingLegacyRequest {

        protected final EntityPlayerMP player;

        private PendingLegacyRequest(EntityPlayerMP player) {
            this.player = player;
        }
    }

    private static final class PendingRaceSelection extends PendingLegacyRequest {

        private final String serializedRaceId;

        private PendingRaceSelection(EntityPlayerMP player, String serializedRaceId) {
            super(player);
            this.serializedRaceId = serializedRaceId;
        }
    }

    private static final class PendingStartingFactionSelection extends PendingLegacyRequest {

        private final String serializedFactionId;

        private PendingStartingFactionSelection(EntityPlayerMP player, String serializedFactionId) {
            super(player);
            this.serializedFactionId = serializedFactionId;
        }
    }

    private static final class PendingAppearanceSelection extends PendingLegacyRequest {

        private final String presetId;

        private PendingAppearanceSelection(EntityPlayerMP player, String presetId) {
            super(player);
            this.presetId = presetId;
        }
    }

    private static final class PendingSexSelection extends PendingLegacyRequest {

        private final String serializedSexId;

        private PendingSexSelection(EntityPlayerMP player, String serializedSexId) {
            super(player);
            this.serializedSexId = serializedSexId;
        }
    }

    private static final class PendingCharacterCreationBack extends PendingLegacyRequest {

        private final String serializedSourceStageId;

        private PendingCharacterCreationBack(EntityPlayerMP player, String serializedSourceStageId) {
            super(player);
            this.serializedSourceStageId = serializedSourceStageId;
        }
    }

    private static final class PendingCharacterFinalization extends PendingLegacyRequest {

        private final boolean replacementConfirmed;
        private final String expectedExistingPledgeCode;

        private PendingCharacterFinalization(EntityPlayerMP player, boolean replacementConfirmed,
            String expectedExistingPledgeCode) {
            super(player);
            this.replacementConfirmed = replacementConfirmed;
            this.expectedExistingPledgeCode = expectedExistingPledgeCode;
        }
    }

    private static final class PendingElfGrappleAttack extends PendingLegacyRequest {

        private PendingElfGrappleAttack(EntityPlayerMP player) {
            super(player);
        }
    }
}
