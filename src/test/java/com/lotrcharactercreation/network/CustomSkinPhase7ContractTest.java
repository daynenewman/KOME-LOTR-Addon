package com.lotrcharactercreation.network;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class CustomSkinPhase7ContractTest {

    @Test
    public void existingDiscriminatorsRemainAndPhaseSevenUsesTwentyThroughTwentyEight() throws Exception {
        String network = read(source("com/lotrcharactercreation/network/ModNetwork.java"));
        for (int discriminator = 0; discriminator <= 28; discriminator++) {
            assertEquals(
                "discriminator " + discriminator,
                1,
                occurrences(network, ", " + discriminator + ", Side.")
                    + occurrences(network, "\n            " + discriminator + ",\n            Side."));
        }
        assertTrue(network.contains("CustomSkinManifestBeginMessage.class"));
        assertTrue(network.contains("CustomSkinTransferResultMessage.class"));
        assertTrue(network.contains("newSimpleChannel(\"lotrcreation\")"));
    }

    @Test
    public void loginSendsManifestBeforeAppearanceAndReadyRefreshesState() throws Exception {
        String coordinator = read(source("com/lotrcharactercreation/LOTRCharacterCreation.java"));
        String login = between(coordinator, "public void playerLoggedIn", "public void playerRespawned");
        String network = read(source("com/lotrcharactercreation/network/ModNetwork.java"));
        String refresh = between(
            network,
            "static void refreshAppearanceStateAfterManifestReady",
            "public static void sendCharacterCreationRequired");

        assertBefore(login, "ModNetwork.beginCustomSkinSync(player);", "refreshPlayerStateAndSynchronize(player);");
        assertFalse(login.contains("ModNetwork.sendCharacterCreationRequired(player);"));
        assertTrue(refresh.contains("sendPlayerAppearanceToTrackingAndSelf(player);"));
        assertTrue(refresh.contains("sendAllPlayerAppearancesTo(player);"));
        assertTrue(refresh.contains("sendCharacterCreationRequired(player);"));
    }

    @Test
    public void clientActivatesOnlyAfterCompleteManifestAndRetainsEarlyExternalId() throws Exception {
        String service = read(source(
            "com/lotrcharactercreation/client/appearance/ClientCustomSkinSyncService.java"));
        String finish = between(service, "public void handleManifestEnd", "public void handleTransferStart");
        String playerCache = read(source(
            "com/lotrcharactercreation/client/appearance/ClientPlayerAppearanceCache.java"));

        assertBefore(finish, "manifestAssembly.finish(", "activateExternalCatalog(definitions)");
        assertBefore(finish, "activateExternalCatalog(definitions)", "sendCustomSkinManifestReady(");
        assertTrue(playerCache.contains("CustomSkinManifestEntry.isPossibleExternalPresetId(appearancePresetId)"));
        assertFalse(playerCache.contains("setAppearancePreset"));
    }

    @Test
    public void clientConnectionStateNeverReadsServerLibraryOrDeletesDiskCache() throws Exception {
        String manager = read(source("com/lotrcharactercreation/client/appearance/ClientCustomSkinManager.java"));
        String sync = read(source("com/lotrcharactercreation/client/appearance/ClientCustomSkinSyncService.java"));
        String disconnected = between(
            sync,
            "public void clearConnectionState()",
            "@SubscribeEvent");
        String transferClear = between(sync, "private void clearTransfersAndRetries()", "private boolean");

        assertFalse(manager.contains("ServerCustomSkinLibrary"));
        assertFalse(sync.contains("ServerCustomSkinLibrary"));
        assertTrue(disconnected.contains("clearTransfersAndRetries();"));
        assertTrue(transferClear.contains("activeTransfers.clear()"));
        assertTrue(transferClear.contains("requestedIdentities.clear()"));
        assertTrue(transferClear.contains("retryState.clear()"));
        assertFalse(disconnected.contains("Files.delete"));
        assertFalse(disconnected.contains("deleteCachePath"));
    }

    @Test
    public void serverAndCommonProtocolSourcesContainNoClientOnlyImports() throws Exception {
        String[] files = {
            "CustomSkinManifestAssembly.java",
            "CustomSkinManifestBeginMessage.java",
            "CustomSkinManifestEndMessage.java",
            "CustomSkinManifestPageMessage.java",
            "CustomSkinManifestReadyMessage.java",
            "CustomSkinManifestPages.java",
            "CustomSkinRequestIdentity.java",
            "CustomSkinRequestPageMessage.java",
            "CustomSkinSyncProtocol.java",
            "CustomSkinTransferChunkMessage.java",
            "CustomSkinTransferEndMessage.java",
            "CustomSkinTransferResultMessage.java",
            "CustomSkinTransferStartMessage.java",
            "CustomSkinTransferWindow.java",
            "ServerCustomSkinSyncService.java"
        };
        for (String filename : files) {
            String contents = read(source("com/lotrcharactercreation/network/" + filename));
            assertFalse(filename, contents.contains("net.minecraft.client"));
            assertFalse(filename, contents.contains("com.lotrcharactercreation.client"));
            assertFalse(filename, contents.contains("DynamicTexture"));
            assertFalse(filename, contents.contains("TextureManager"));
        }
    }

    @Test
    public void manifestAndTransfersNeverExposeServerFilesystemPaths() throws Exception {
        String entry = read(source("com/lotrcharactercreation/appearance/CustomSkinManifestEntry.java"));
        String protocol = read(source("com/lotrcharactercreation/network/CustomSkinSyncProtocol.java"));
        String server = read(source("com/lotrcharactercreation/network/ServerCustomSkinSyncService.java"));

        assertFalse(protocol.contains("getRelativePath"));
        assertFalse(protocol.contains("getAbsolutePath"));
        assertFalse(server.contains("getRelativePath"));
        assertFalse(server.contains("getAbsolutePath"));
        assertTrue(entry.contains("logicalRelativePath = parsedRace.getSerializedId()"));
    }

    @Test
    public void phaseSevenDoesNotImplementDeferredHotReloadOrFilesystemWatching() throws Exception {
        String network = read(source("com/lotrcharactercreation/network/ModNetwork.java"));
        String server = read(source("com/lotrcharactercreation/network/ServerCustomSkinSyncService.java"));

        assertFalse(network.contains("skins reload"));
        assertFalse(server.contains("WatchService"));
        assertFalse(server.contains("CommandKome"));
    }

    private static Path source(String relativePath) {
        return Paths.get("src/main/java").resolve(relativePath.replace('/', File.separatorChar));
    }

    private static String read(Path path) throws Exception {
        assertTrue("Missing source file: " + path, Files.isRegularFile(path));
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static int occurrences(String text, String value) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(value, offset)) >= 0) {
            count++;
            offset += value.length();
        }
        return count;
    }

    private static String between(String text, String start, String end) {
        int startIndex = text.indexOf(start);
        int endIndex = text.indexOf(end, startIndex);
        assertTrue("Missing start marker: " + start, startIndex >= 0);
        assertTrue("Missing end marker: " + end, endIndex >= 0);
        return text.substring(startIndex, endIndex);
    }

    private static void assertBefore(String text, String first, String second) {
        int firstIndex = text.indexOf(first);
        int secondIndex = text.indexOf(second);
        assertTrue("Missing first marker: " + first, firstIndex >= 0);
        assertTrue("Missing second marker: " + second, secondIndex >= 0);
        assertTrue(first + " must precede " + second, firstIndex < secondIndex);
    }
}
