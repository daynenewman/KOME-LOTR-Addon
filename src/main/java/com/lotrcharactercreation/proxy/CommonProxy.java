package com.lotrcharactercreation.proxy;

import java.io.File;
import java.util.UUID;

public class CommonProxy {

    public void initialize(File customSkinRoot) {}

    public void handleCharacterCreationRequired(String serializedStageId, String serializedRaceId,
        String serializedSexId, String serializedFactionId, String appearancePresetId, String currentPledgeCode,
        boolean automaticStartingAllegiance) {}

    public void handleRaceSelectionAccepted(String serializedRaceId) {}

    public void handleStartingFactionSelectionAccepted(String serializedFactionId) {}

    public void handleStartingFactionApplied(String serializedFactionId) {}

    public void handleStartingWaypointApplied(String serializedFactionId, String waypointCodeName) {}

    public void handlePlayerAppearanceSync(UUID playerId, int entityId, String serializedRaceId, String serializedSexId,
        String appearancePresetId, boolean characterCreationComplete) {}

    public void handleOpenAppearanceSelection(String serializedRaceId, String serializedSexId,
        String serializedFactionId, String currentPresetId) {}

    public void handleAppearanceSelectionResult(boolean accepted, String presetId) {}

    public void handleOpenSexSelection(String serializedRaceId, String serializedSexId) {}

    public void handleSexSelectionResult(boolean accepted, String serializedSexId) {}

    public void handleDwarfTraitState(boolean active, float stamina, int feast, boolean exhausted) {}

    public void handleUrukRageState(boolean active, float rage) {}

    public void handleElfGrappleState(int playerEntityId, int targetEntityId, boolean active, boolean ready) {}
}
