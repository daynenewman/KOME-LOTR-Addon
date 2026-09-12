package com.lotrcharactercreation.client.appearance;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Render-thread owner of DynamicTextures keyed by preset ID and exact SHA-256. */
@SideOnly(Side.CLIENT)
final class ExternalAppearanceTextureManager {

    private static final Logger LOGGER = LogManager.getLogger("lotrcharactercreation");

    private final ClientCustomSkinCache cache;
    private final ClientExternalTextureState state = new ClientExternalTextureState();
    private final Map<ClientCustomSkinIdentity, ResourceLocation> loadedLocations =
        new HashMap<ClientCustomSkinIdentity, ResourceLocation>();
    private final Set<ClientCustomSkinIdentity> pendingLoads = new HashSet<ClientCustomSkinIdentity>();

    ExternalAppearanceTextureManager(ClientCustomSkinCache cache) {
        this.cache = cache;
    }

    ResourceLocation resolve(ClientExternalSkinDefinition definition) {
        if (definition == null) {
            return null;
        }

        ClientCustomSkinIdentity identity = definition.getIdentity();
        ResourceLocation staleLocation = null;
        synchronized (this) {
            ClientCustomSkinIdentity loadedIdentity = state.getLoadedIdentity(identity.getPresetId());
            if (identity.equals(loadedIdentity)) {
                ResourceLocation loaded = loadedLocations.get(identity);
                if (loaded != null) {
                    return loaded;
                }
                state.invalidatePreset(identity.getPresetId());
            } else if (loadedIdentity != null) {
                state.invalidatePreset(identity.getPresetId());
                staleLocation = loadedLocations.remove(loadedIdentity);
            }

            if (!state.canAttempt(identity) || pendingLoads.contains(identity)) {
                return null;
            }
            if (!Minecraft.getMinecraft().func_152345_ab()) {
                pendingLoads.add(identity);
                scheduleDelete(staleLocation);
                scheduleLoad(definition);
                return null;
            }
        }

        deleteOnRenderThread(staleLocation);
        return loadOnRenderThread(definition);
    }

    void contentAvailable(ClientExternalSkinDefinition definition) {
        if (definition == null) {
            return;
        }
        synchronized (this) {
            if (!state.contentAvailable(definition.getIdentity())) {
                return;
            }
            if (pendingLoads.contains(definition.getIdentity())) {
                return;
            }
            pendingLoads.add(definition.getIdentity());
        }
        scheduleLoad(definition);
    }

    void retainOnly(Map<String, ClientCustomSkinIdentity> expectedByPresetId) {
        List<ResourceLocation> staleLocations = new ArrayList<ResourceLocation>();
        synchronized (this) {
            Collection<ClientCustomSkinIdentity> removed = state.retainOnly(expectedByPresetId);
            for (ClientCustomSkinIdentity identity : removed) {
                ResourceLocation location = loadedLocations.remove(identity);
                if (location != null) {
                    staleLocations.add(location);
                }
            }
            for (ClientCustomSkinIdentity pending : new ArrayList<ClientCustomSkinIdentity>(pendingLoads)) {
                if (!pending.equals(expectedByPresetId.get(pending.getPresetId()))) {
                    pendingLoads.remove(pending);
                }
            }
        }
        scheduleDeletes(staleLocations);
    }

    void invalidatePreset(String presetId) {
        ResourceLocation staleLocation;
        synchronized (this) {
            ClientCustomSkinIdentity removed = state.invalidatePreset(presetId);
            staleLocation = removed == null ? null : loadedLocations.remove(removed);
            for (ClientCustomSkinIdentity pending : new ArrayList<ClientCustomSkinIdentity>(pendingLoads)) {
                if (pending.getPresetId().equals(presetId)) {
                    pendingLoads.remove(pending);
                }
            }
        }
        scheduleDelete(staleLocation);
    }

    void clearConnectionState() {
        List<ResourceLocation> staleLocations;
        synchronized (this) {
            state.clear();
            pendingLoads.clear();
            staleLocations = new ArrayList<ResourceLocation>(loadedLocations.values());
            loadedLocations.clear();
        }
        scheduleDeletes(staleLocations);
    }

    private ResourceLocation loadOnRenderThread(ClientExternalSkinDefinition definition) {
        ClientCustomSkinIdentity identity = definition.getIdentity();
        synchronized (this) {
            pendingLoads.remove(identity);
            if (!state.canAttempt(identity)) {
                return null;
            }
            ClientCustomSkinIdentity loadedIdentity = state.getLoadedIdentity(identity.getPresetId());
            if (identity.equals(loadedIdentity)) {
                return loadedLocations.get(identity);
            }
        }
        if (!ClientCustomSkinManager.getInstance().isActive(definition)) {
            return null;
        }
        if (cache == null) {
            return fail(definition, "client custom skin cache is unavailable", null);
        }

        ClientCustomSkinCache.CachedContent content = cache.find(definition);
        if (content == null) {
            return fail(definition, "validated content is not available in the client cache", null);
        }

        ResourceLocation location;
        try {
            BufferedImage image = content.getImage();
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            location = Minecraft.getMinecraft()
                .getTextureManager()
                .getDynamicTextureLocation(
                    "kome_custom_skin_" + identity.getSha256().substring(0, 16),
                    dynamicTexture);
        } catch (RuntimeException exception) {
            return fail(definition, "PNG texture registration failed", exception);
        }

        ResourceLocation replacedLocation = null;
        boolean stillActive = ClientCustomSkinManager.getInstance().isActive(definition);
        synchronized (this) {
            if (stillActive) {
                ClientCustomSkinIdentity replaced = state.markLoaded(identity);
                loadedLocations.put(identity, location);
                if (replaced != null && !replaced.equals(identity)) {
                    replacedLocation = loadedLocations.remove(replaced);
                }
            }
        }
        if (!stillActive) {
            deleteOnRenderThread(location);
            return null;
        }
        deleteOnRenderThread(replacedLocation);
        return location;
    }

    private ResourceLocation fail(ClientExternalSkinDefinition definition, String reason, Throwable cause) {
        boolean shouldLog;
        synchronized (this) {
            pendingLoads.remove(definition.getIdentity());
            shouldLog = state.markFailed(definition.getIdentity());
        }
        if (shouldLog) {
            String message = "Could not load external appearance texture " + definition.getIdentity()
                + " (" + reason + ")";
            if (cause == null) {
                LOGGER.warn(message);
            } else {
                LOGGER.warn(message, cause);
            }
        }
        return null;
    }

    private void scheduleLoad(final ClientExternalSkinDefinition definition) {
        Minecraft.getMinecraft().func_152344_a(new Runnable() {

            @Override
            public void run() {
                loadOnRenderThread(definition);
            }
        });
    }

    private static void scheduleDeletes(final Collection<ResourceLocation> locations) {
        if (locations.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        Runnable deletion = new Runnable() {

            @Override
            public void run() {
                for (ResourceLocation location : locations) {
                    deleteOnRenderThread(location);
                }
            }
        };
        if (minecraft.func_152345_ab()) {
            deletion.run();
        } else {
            minecraft.func_152344_a(deletion);
        }
    }

    private static void scheduleDelete(ResourceLocation location) {
        if (location != null) {
            scheduleDeletes(java.util.Collections.singletonList(location));
        }
    }

    private static void deleteOnRenderThread(ResourceLocation location) {
        if (location != null) {
            Minecraft.getMinecraft().getTextureManager().deleteTexture(location);
        }
    }
}
