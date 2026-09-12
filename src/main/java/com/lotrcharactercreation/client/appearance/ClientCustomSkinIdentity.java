package com.lotrcharactercreation.client.appearance;

import com.lotrcharactercreation.appearance.CustomSkinHashing;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Immutable render/cache identity for one logical external appearance revision. */
@SideOnly(Side.CLIENT)
public final class ClientCustomSkinIdentity {

    private final String presetId;
    private final String sha256;

    public ClientCustomSkinIdentity(String presetId, String sha256) {
        if (presetId == null || presetId.isEmpty()) {
            throw new IllegalArgumentException("custom skin preset ID cannot be null or empty");
        }
        if (!CustomSkinHashing.isCanonicalSha256(sha256)) {
            throw new IllegalArgumentException("custom skin SHA-256 must be lowercase 64-character hexadecimal");
        }
        this.presetId = presetId;
        this.sha256 = sha256;
    }

    public String getPresetId() {
        return presetId;
    }

    public String getSha256() {
        return sha256;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ClientCustomSkinIdentity)) {
            return false;
        }
        ClientCustomSkinIdentity identity = (ClientCustomSkinIdentity) other;
        return presetId.equals(identity.presetId) && sha256.equals(identity.sha256);
    }

    @Override
    public int hashCode() {
        return 31 * presetId.hashCode() + sha256.hashCode();
    }

    @Override
    public String toString() {
        return presetId + "@" + sha256;
    }
}
