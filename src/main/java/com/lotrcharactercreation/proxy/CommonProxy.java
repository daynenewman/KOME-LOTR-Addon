package com.lotrcharactercreation.proxy;

import java.io.File;
import java.util.List;
import java.util.UUID;

import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;

public class CommonProxy {

    public void initialize(File customSkinRoot, File configurationDirectory) {}

    public void handleCustomSkinManifestBegin(int schemaVersion, long epoch, long revision, String digest,
        int entryCount, long totalBytes, int pageCount) {}

    public void handleCustomSkinManifestPage(long epoch, int pageIndex, int pageCount,
        List<CustomSkinManifestEntry> entries) {}

    public void handleCustomSkinManifestEnd(long epoch, long revision, String digest) {}

    public void handleCustomSkinTransferStart(long transferId, long epoch, long revision, String presetId,
        String sha256, int byteSize, int chunkCount, int width, int height) {}

    public void handleCustomSkinTransferChunk(long transferId, int chunkIndex, byte[] data) {}

    public void handleCustomSkinTransferEnd(long transferId, long epoch, String presetId, String sha256) {}

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
