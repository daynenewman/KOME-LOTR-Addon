package com.lotrcharactercreation.client.appearance;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearancePresetCatalog;
import com.lotrcharactercreation.appearance.AppearancePresetRegistry;
import com.lotrcharactercreation.appearance.CustomSkinEntry;
import com.lotrcharactercreation.appearance.CustomSkinScanResult;
import com.lotrcharactercreation.appearance.CustomSkinSnapshot;
import com.lotrcharactercreation.appearance.ExternalAppearancePresetScanner;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Owns connection-scoped client metadata, cache access, and external texture lifecycle. */
@SideOnly(Side.CLIENT)
public final class ClientCustomSkinManager {

    private static final Logger LOGGER = LogManager.getLogger("lotrcharactercreation");
    private static final ClientCustomSkinManager INSTANCE = new ClientCustomSkinManager();

    private volatile CatalogState activeCatalog = CatalogState.empty(0L);
    private List<ClientExternalSkinDefinition> localCompatibilityDefinitions = Collections.emptyList();
    private ClientCustomSkinCache cache;
    private ExternalAppearanceTextureManager textureManager;
    private long connectionGeneration;

    private ClientCustomSkinManager() {}

    public static ClientCustomSkinManager getInstance() {
        return INSTANCE;
    }

    public synchronized void initialize(File legacyCustomSkinRoot, File configurationDirectory) {
        if (legacyCustomSkinRoot == null || configurationDirectory == null) {
            throw new IllegalArgumentException("custom skin and configuration roots cannot be null");
        }

        File cacheRoot = new File(new File(configurationDirectory, "kome"), "client_skin_cache");
        try {
            cache = new ClientCustomSkinCache(cacheRoot);
        } catch (IOException | SecurityException exception) {
            cache = null;
            LOGGER.error("Could not initialize client custom skin cache at " + cacheRoot, exception);
        }
        textureManager = new ExternalAppearanceTextureManager(cache);
        localCompatibilityDefinitions = loadAndImportLegacyDefinitions(legacyCustomSkinRoot);
        publishCatalog(localCompatibilityDefinitions);
    }

    public AppearancePresetCatalog getCatalog() {
        return activeCatalog.catalog;
    }

    public ClientExternalSkinDefinition getExternalDefinition(String presetId) {
        return presetId == null ? null : activeCatalog.externalByPresetId.get(presetId);
    }

    public long getConnectionGeneration() {
        return activeCatalog.generation;
    }

    public ResourceLocation resolveExternal(AppearancePreset preset) {
        if (preset == null || textureManager == null) {
            return null;
        }
        ClientExternalSkinDefinition definition = getExternalDefinition(preset.getId());
        return definition == null ? null : textureManager.resolve(definition);
    }

    /** Future manifest entry point: atomically replaces all active external metadata. */
    public synchronized void activateExternalCatalog(Collection<ClientExternalSkinDefinition> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("client external catalog cannot be null");
        }
        publishCatalog(definitions);
    }

    /** Future transfer-completion entry point. Invalid or inactive content is ignored safely. */
    public void contentAvailable(String presetId, String sha256) {
        ClientExternalSkinDefinition definition = getExternalDefinition(presetId);
        if (definition == null || !definition.getSha256().equals(sha256) || cache == null
            || cache.find(definition) == null || textureManager == null) {
            return;
        }
        textureManager.contentAvailable(definition);
    }

    /** Removes active metadata and loaded texture state for one logical preset. */
    public synchronized void invalidatePreset(String presetId) {
        if (presetId == null || presetId.isEmpty()) {
            return;
        }
        Map<String, ClientExternalSkinDefinition> retained = new TreeMap<String, ClientExternalSkinDefinition>(
            activeCatalog.externalByPresetId);
        retained.remove(presetId);
        publishCatalog(retained.values());
    }

    /** Clears all connection metadata and transient texture/failure state, but keeps disk cache content. */
    public synchronized void clearConnectionState() {
        connectionGeneration++;
        activeCatalog = CatalogState.empty(connectionGeneration);
        if (textureManager != null) {
            textureManager.clearConnectionState();
        }
    }

    @SubscribeEvent
    public void connected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        synchronized (this) {
            if (textureManager != null) {
                textureManager.clearConnectionState();
            }
            publishCatalog(localCompatibilityDefinitions);
        }
    }

    @SubscribeEvent
    public void disconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        clearConnectionState();
    }

    boolean isActive(ClientExternalSkinDefinition definition) {
        ClientExternalSkinDefinition active = getExternalDefinition(definition.getPresetId());
        return active != null && active.getIdentity().equals(definition.getIdentity());
    }

    ClientCustomSkinCache getCache() {
        return cache;
    }

    private List<ClientExternalSkinDefinition> loadAndImportLegacyDefinitions(File legacyRoot) {
        CustomSkinScanResult scan = ExternalAppearancePresetScanner.scanSnapshot(legacyRoot, 0L, LOGGER);
        if (!scan.isSuccessful()) {
            return Collections.emptyList();
        }

        CustomSkinSnapshot snapshot = scan.getSnapshot();
        List<ClientExternalSkinDefinition> definitions = new ArrayList<ClientExternalSkinDefinition>(
            snapshot.getEntries().size());
        for (CustomSkinEntry entry : snapshot.getEntries()) {
            ClientExternalSkinDefinition definition = ClientExternalSkinDefinition.fromValidatedEntry(entry);
            definitions.add(definition);
            if (cache == null) {
                continue;
            }
            File source = new File(legacyRoot, entry.getRelativePath().replace('/', File.separatorChar));
            try {
                if (cache.importLegacyFile(definition, source) == null) {
                    LOGGER.warn("Could not import legacy custom skin into the KOME cache: "
                        + entry.getRelativePath());
                }
            } catch (IOException | SecurityException exception) {
                LOGGER.warn("Could not import legacy custom skin into the KOME cache: "
                    + entry.getRelativePath(), exception);
            }
        }
        return Collections.unmodifiableList(definitions);
    }

    private void publishCatalog(Collection<ClientExternalSkinDefinition> definitions) {
        Map<String, ClientExternalSkinDefinition> indexed = new TreeMap<String, ClientExternalSkinDefinition>();
        List<AppearancePreset> externalPresets = new ArrayList<AppearancePreset>();
        for (ClientExternalSkinDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("client external catalog entry cannot be null");
            }
            if (indexed.put(definition.getPresetId(), definition) != null) {
                throw new IllegalArgumentException("duplicate client external preset ID: " + definition.getPresetId());
            }
        }
        for (ClientExternalSkinDefinition definition : indexed.values()) {
            externalPresets.add(definition.getPreset());
        }

        AppearancePresetCatalog combined = AppearancePresetCatalog.combine(
            AppearancePresetRegistry.getBuiltInCatalog(),
            externalPresets);
        connectionGeneration++;
        CatalogState replacement = new CatalogState(connectionGeneration, combined, indexed);
        activeCatalog = replacement;
        if (textureManager != null) {
            textureManager.retainOnly(replacement.identitiesByPresetId);
        }
    }

    private static final class CatalogState {

        private final long generation;
        private final AppearancePresetCatalog catalog;
        private final Map<String, ClientExternalSkinDefinition> externalByPresetId;
        private final Map<String, ClientCustomSkinIdentity> identitiesByPresetId;

        private static CatalogState empty(long generation) {
            return new CatalogState(
                generation,
                AppearancePresetRegistry.getBuiltInCatalog(),
                Collections.<String, ClientExternalSkinDefinition>emptyMap());
        }

        private CatalogState(long generation, AppearancePresetCatalog catalog,
            Map<String, ClientExternalSkinDefinition> definitions) {
            this.generation = generation;
            this.catalog = catalog;
            externalByPresetId = Collections.unmodifiableMap(
                new TreeMap<String, ClientExternalSkinDefinition>(definitions));
            Map<String, ClientCustomSkinIdentity> identities = new TreeMap<String, ClientCustomSkinIdentity>();
            for (ClientExternalSkinDefinition definition : externalByPresetId.values()) {
                identities.put(definition.getPresetId(), definition.getIdentity());
            }
            identitiesByPresetId = Collections.unmodifiableMap(identities);
        }
    }
}
