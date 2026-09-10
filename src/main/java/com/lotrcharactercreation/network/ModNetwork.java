package com.lotrcharactercreation.network;

import java.util.Collections;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.AppearanceSelectionRules;
import com.lotrcharactercreation.appearance.PlayerSex;
import com.lotrcharactercreation.body.PlayerRaceEyeService;
import com.lotrcharactercreation.body.PlayerRaceSizeService;
import com.lotrcharactercreation.config.ModConfiguration;
import com.lotrcharactercreation.creation.CharacterCreationFlowService;
import com.lotrcharactercreation.creation.CharacterCreationStage;
import com.lotrcharactercreation.faction.StartingFaction;
import com.lotrcharactercreation.faction.StartingFactionApplication;
import com.lotrcharactercreation.race.PlayerRace;
import com.lotrcharactercreation.race.PlayerRaceData;
import com.lotrcharactercreation.trait.ElfGrappleService;
import com.lotrcharactercreation.waypoint.StartingWaypointApplication;
import com.lotrcharactercreation.waypoint.StartingWaypointApplication.Result;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
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
    private static final Queue<EntityPlayerMP> PENDING_ELF_GRAPPLE_ATTACKS = new ConcurrentLinkedQueue<>();
    private static final Set<UUID> PENDING_CHARACTER_FINALIZATION_IDS = Collections
        .newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

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
                ModConfiguration.isAutomaticStartingAllegianceEnabled()),
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
        PENDING_ELF_GRAPPLE_ATTACKS.add(player);
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
        PENDING_RACE_SELECTIONS.add(new PendingRaceSelection(player, serializedRaceId));
    }

    public static void sendStartingFactionSelection(StartingFaction faction) {
        CHANNEL.sendToServer(new StartingFactionSelectionMessage(faction.getSerializedId()));
    }

    public static void enqueueStartingFactionSelection(EntityPlayerMP player, String serializedFactionId) {
        PENDING_FACTION_SELECTIONS.add(new PendingStartingFactionSelection(player, serializedFactionId));
    }

    public static void sendAppearanceSelectionOpen(EntityPlayerMP player) {
        PlayerRace race = PlayerRaceData.getRace(player);
        PlayerSex sex = AppearanceSelectionRules.getSelectionSex(race, PlayerRaceData.getSex(player));
        StartingFaction faction = PlayerRaceData.getStartingFaction(player);
        if (AppearanceSelectionRules.getCandidates(race, sex, faction)
            .isEmpty()) {
            String detail = sex == null ? "Set a valid appearance sex first."
                : "No appearances match the current race and faction.";
            player.addChatMessage(new ChatComponentText("[LOTR Character Creation] " + detail));
            return;
        }

        String currentPresetId = PlayerRaceData.getAppearancePresetId(player);
        if (!AppearanceSelectionRules.isPresetAllowed(race, sex, faction, currentPresetId)) {
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
        PENDING_APPEARANCE_SELECTIONS.add(new PendingAppearanceSelection(player, presetId));
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
        PENDING_SEX_SELECTIONS.add(new PendingSexSelection(player, serializedSexId));
    }

    public static void sendCharacterFinalization(boolean replacementConfirmed, String expectedExistingPledgeCode) {
        CHANNEL.sendToServer(new CharacterFinalizationMessage(replacementConfirmed, expectedExistingPledgeCode));
    }

    public static void sendCharacterCreationBack(CharacterCreationStage sourceStage) {
        CHANNEL.sendToServer(new CharacterCreationBackMessage(sourceStage.getSerializedId()));
    }

    public static void enqueueCharacterCreationBack(EntityPlayerMP player, String serializedSourceStageId) {
        PENDING_CHARACTER_CREATION_BACKS.add(new PendingCharacterCreationBack(player, serializedSourceStageId));
    }

    public static void enqueueCharacterFinalization(EntityPlayerMP player, boolean replacementConfirmed,
        String expectedExistingPledgeCode) {
        if (PENDING_CHARACTER_FINALIZATION_IDS.add(player.getUniqueID())) {
            PENDING_CHARACTER_FINALIZATIONS
                .add(new PendingCharacterFinalization(player, replacementConfirmed, expectedExistingPledgeCode));
        }
    }

    private static void finalizeCharacterCreation(PendingCharacterFinalization request) {
        EntityPlayerMP player = request.player;
        if (!CharacterCreationFlowService.isReadyForFinalization(player)) {
            sendCharacterCreationRequired(player);
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
        processPendingCharacterCreationBacks(server);
        processPendingRaceSelections(server);
        processPendingStartingFactionSelections(server);
        processPendingSexSelections(server);
        processPendingAppearanceSelections(server);
        processPendingCharacterFinalizations(server);
        processPendingElfGrappleAttacks(server);
    }

    private static void processPendingElfGrappleAttacks(MinecraftServer server) {
        EntityPlayerMP player;
        while ((player = PENDING_ELF_GRAPPLE_ATTACKS.poll()) != null) {
            if (isConnected(server, player)) {
                ElfGrappleService.handleAttackRequest(player);
            }
        }
    }

    private static void processPendingRaceSelections(MinecraftServer server) {
        PendingRaceSelection selection;
        while ((selection = PENDING_RACE_SELECTIONS.poll()) != null) {
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
        }
    }

    private static void processPendingCharacterCreationBacks(MinecraftServer server) {
        PendingCharacterCreationBack request;
        while ((request = PENDING_CHARACTER_CREATION_BACKS.poll()) != null) {
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
        }
    }

    private static void processPendingStartingFactionSelections(MinecraftServer server) {
        PendingStartingFactionSelection selection;
        while ((selection = PENDING_FACTION_SELECTIONS.poll()) != null) {
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
            CHANNEL.sendTo(new StartingFactionSelectionAcceptedMessage(faction.getSerializedId()), selection.player);
            sendCharacterCreationRequired(selection.player);
        }
    }

    private static void processPendingAppearanceSelections(MinecraftServer server) {
        PendingAppearanceSelection selection;
        while ((selection = PENDING_APPEARANCE_SELECTIONS.poll()) != null) {
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
        }
    }

    private static void processPendingSexSelections(MinecraftServer server) {
        PendingSexSelection selection;
        while ((selection = PENDING_SEX_SELECTIONS.poll()) != null) {
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
            }
        }
    }

    private static String normalizePledgeCode(String pledgeCode) {
        return pledgeCode == null ? "" : pledgeCode;
    }

    private static boolean isConnected(MinecraftServer server, EntityPlayerMP player) {
        return server != null && server.getConfigurationManager().playerEntityList.contains(player);
    }

    private static PlayerAppearanceSyncMessage createPlayerAppearanceMessage(EntityPlayerMP player) {
        PlayerRace race = PlayerRaceData.getRace(player);
        PlayerSex sex = PlayerRaceData.getSex(player);
        String presetId = PlayerRaceData.getAppearancePresetId(player);
        if (!PlayerRaceData.isAppearanceInitialized(player)
            || !AppearancePresetRegistry.isPresetValid(race, sex, presetId)) {
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

    private static final class PendingRaceSelection {

        private final EntityPlayerMP player;
        private final String serializedRaceId;

        private PendingRaceSelection(EntityPlayerMP player, String serializedRaceId) {
            this.player = player;
            this.serializedRaceId = serializedRaceId;
        }
    }

    private static final class PendingStartingFactionSelection {

        private final EntityPlayerMP player;
        private final String serializedFactionId;

        private PendingStartingFactionSelection(EntityPlayerMP player, String serializedFactionId) {
            this.player = player;
            this.serializedFactionId = serializedFactionId;
        }
    }

    private static final class PendingAppearanceSelection {

        private final EntityPlayerMP player;
        private final String presetId;

        private PendingAppearanceSelection(EntityPlayerMP player, String presetId) {
            this.player = player;
            this.presetId = presetId;
        }
    }

    private static final class PendingSexSelection {

        private final EntityPlayerMP player;
        private final String serializedSexId;

        private PendingSexSelection(EntityPlayerMP player, String serializedSexId) {
            this.player = player;
            this.serializedSexId = serializedSexId;
        }
    }

    private static final class PendingCharacterCreationBack {

        private final EntityPlayerMP player;
        private final String serializedSourceStageId;

        private PendingCharacterCreationBack(EntityPlayerMP player, String serializedSourceStageId) {
            this.player = player;
            this.serializedSourceStageId = serializedSourceStageId;
        }
    }

    private static final class PendingCharacterFinalization {

        private final EntityPlayerMP player;
        private final boolean replacementConfirmed;
        private final String expectedExistingPledgeCode;

        private PendingCharacterFinalization(EntityPlayerMP player, boolean replacementConfirmed,
            String expectedExistingPledgeCode) {
            this.player = player;
            this.replacementConfirmed = replacementConfirmed;
            this.expectedExistingPledgeCode = expectedExistingPledgeCode;
        }
    }
}
