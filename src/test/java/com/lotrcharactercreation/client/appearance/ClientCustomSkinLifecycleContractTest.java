package com.lotrcharactercreation.client.appearance;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

public class ClientCustomSkinLifecycleContractTest {

    @Test
    public void clientCacheAndCatalogRemainClientOnly() throws Exception {
        for (String filename : new String[] {
            "ClientCustomSkinCache.java",
            "ClientCustomSkinIdentity.java",
            "ClientCustomSkinManager.java",
            "ClientExternalSkinDefinition.java",
            "ClientExternalTextureState.java",
            "ExternalAppearanceTextureManager.java"
        }) {
            String source = read(clientAppearanceSource(filename));
            assertTrue(filename, source.contains("@SideOnly(Side.CLIENT)"));
        }

        String commonProxy = read(source("com/lotrcharactercreation/proxy/CommonProxy.java"));
        String coordinator = read(source("com/lotrcharactercreation/LOTRCharacterCreation.java"));
        assertFalse(commonProxy.contains("ClientCustomSkin"));
        assertFalse(commonProxy.contains("net.minecraft.client"));
        assertFalse(coordinator.contains("ClientCustomSkin"));
        assertFalse(coordinator.contains("net.minecraft.client"));
    }

    @Test
    public void textureLifecycleUsesContentIdentityAndRenderThreadScheduling() throws Exception {
        String manager = read(clientAppearanceSource("ExternalAppearanceTextureManager.java"));
        String resolve = between(manager, "ResourceLocation resolve(", "void contentAvailable");

        assertTrue(manager.contains("Map<ClientCustomSkinIdentity, ResourceLocation> loadedLocations"));
        assertTrue(manager.contains("Minecraft.getMinecraft().func_152345_ab()"));
        assertTrue(manager.contains("Minecraft.getMinecraft().func_152344_a("));
        assertTrue(manager.contains("getTextureManager().deleteTexture(location)"));
        assertTrue(manager.contains("state.contentAvailable(definition.getIdentity())"));
        assertTrue(manager.contains("state.clear()"));
        assertFalse(manager.contains("failedPresetIds"));
        assertFalse(manager.contains("Map<String, ResourceLocation>"));
        assertBefore(resolve, "scheduleDelete(staleLocation);", "scheduleLoad(definition);");
    }

    @Test
    public void connectionLifecycleClearsMetadataButNeverDeletesDiskCache() throws Exception {
        String manager = read(clientAppearanceSource("ClientCustomSkinManager.java"));
        String clear = between(manager, "public synchronized void clearConnectionState()", "@SubscribeEvent");
        String disconnect = between(
            manager,
            "public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event)",
            "boolean isActive");

        assertTrue(clear.contains("CatalogState.empty(connectionGeneration)"));
        assertTrue(clear.contains("textureManager.clearConnectionState()"));
        assertFalse(clear.contains("Files.delete"));
        assertFalse(clear.contains("deleteCachePath"));
        assertTrue(disconnect.contains("clearConnectionState();"));
    }

    @Test
    public void legacyCompatibilityImportsToCacheWithoutRetainingFileAuthority() throws Exception {
        String manager = read(clientAppearanceSource("ClientCustomSkinManager.java"));
        String textureManager = read(clientAppearanceSource("ExternalAppearanceTextureManager.java"));

        assertTrue(manager.contains("cache.importLegacyFile(definition, source)"));
        assertTrue(manager.contains("entry.getRelativePath()"));
        assertTrue(textureManager.contains("cache.find(definition)"));
        assertFalse(textureManager.contains("getExternalRelativePath()"));
        assertFalse(textureManager.contains("ImageIO.read"));
    }

    @Test
    public void cacheRootIsKomeContentAddressedAndHashDerived() throws Exception {
        String manager = read(clientAppearanceSource("ClientCustomSkinManager.java"));
        String cache = read(clientAppearanceSource("ClientCustomSkinCache.java"));

        assertTrue(manager.contains("new File(new File(configurationDirectory, \"kome\"), \"client_skin_cache\")"));
        assertTrue(cache.contains("private static final String HASH_DIRECTORY = \"sha256\";"));
        assertTrue(cache.contains("resolve(sha256.substring(0, 2))"));
        assertTrue(cache.contains("resolve(sha256 + suffix)"));
        assertFalse(cache.contains("getExternalRelativePath"));
    }

    private static Path clientAppearanceSource(String filename) {
        return source("com/lotrcharactercreation/client/appearance/" + filename);
    }

    private static Path source(String relativePath) {
        return Paths.get("src/main/java").resolve(relativePath.replace('/', File.separatorChar));
    }

    private static String read(Path path) throws Exception {
        assertTrue("Missing source file: " + path, Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
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
        int secondIndex = text.indexOf(second, firstIndex);
        assertTrue("Missing first marker: " + first, firstIndex >= 0);
        assertTrue("Missing second marker: " + second, secondIndex >= 0);
        assertTrue(first + " must precede " + second, firstIndex < secondIndex);
    }
}
