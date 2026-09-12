package com.lotrcharactercreation.client.appearance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Pure identity/failure state kept separate from Minecraft texture operations for testing. */
@SideOnly(Side.CLIENT)
final class ClientExternalTextureState {

    private final Map<String, ClientCustomSkinIdentity> loadedByPresetId =
        new HashMap<String, ClientCustomSkinIdentity>();
    private final Set<ClientCustomSkinIdentity> failedIdentities = new HashSet<ClientCustomSkinIdentity>();

    ClientCustomSkinIdentity getLoadedIdentity(String presetId) {
        return loadedByPresetId.get(presetId);
    }

    ClientCustomSkinIdentity markLoaded(ClientCustomSkinIdentity identity) {
        failedIdentities.remove(identity);
        return loadedByPresetId.put(identity.getPresetId(), identity);
    }

    boolean markFailed(ClientCustomSkinIdentity identity) {
        return failedIdentities.add(identity);
    }

    boolean canAttempt(ClientCustomSkinIdentity identity) {
        return !failedIdentities.contains(identity);
    }

    boolean contentAvailable(ClientCustomSkinIdentity identity) {
        return failedIdentities.remove(identity);
    }

    ClientCustomSkinIdentity invalidatePreset(String presetId) {
        ClientCustomSkinIdentity removed = loadedByPresetId.remove(presetId);
        for (ClientCustomSkinIdentity failed : new ArrayList<ClientCustomSkinIdentity>(failedIdentities)) {
            if (failed.getPresetId().equals(presetId)) {
                failedIdentities.remove(failed);
            }
        }
        return removed;
    }

    Collection<ClientCustomSkinIdentity> clear() {
        Collection<ClientCustomSkinIdentity> removed = new ArrayList<ClientCustomSkinIdentity>(
            loadedByPresetId.values());
        loadedByPresetId.clear();
        failedIdentities.clear();
        return removed;
    }

    Collection<ClientCustomSkinIdentity> retainOnly(Map<String, ClientCustomSkinIdentity> expectedByPresetId) {
        Collection<ClientCustomSkinIdentity> removed = new ArrayList<ClientCustomSkinIdentity>();
        for (Map.Entry<String, ClientCustomSkinIdentity> loaded :
            new ArrayList<Map.Entry<String, ClientCustomSkinIdentity>>(loadedByPresetId.entrySet())) {
            ClientCustomSkinIdentity expected = expectedByPresetId.get(loaded.getKey());
            if (!loaded.getValue().equals(expected)) {
                loadedByPresetId.remove(loaded.getKey());
                removed.add(loaded.getValue());
            }
        }
        for (ClientCustomSkinIdentity failed : new ArrayList<ClientCustomSkinIdentity>(failedIdentities)) {
            if (!failed.equals(expectedByPresetId.get(failed.getPresetId()))) {
                failedIdentities.remove(failed);
            }
        }
        return removed;
    }
}
