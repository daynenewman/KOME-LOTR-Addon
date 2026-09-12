package com.lotrcharactercreation.network;

import com.lotrcharactercreation.appearance.CustomSkinHashing;
import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;

/** One client request for the exact content identity advertised by a manifest. */
public final class CustomSkinRequestIdentity {

    private final String presetId;
    private final String sha256;

    public CustomSkinRequestIdentity(String presetId, String sha256) {
        if (!CustomSkinManifestEntry.isPossibleExternalPresetId(presetId)
            || !CustomSkinHashing.isCanonicalSha256(sha256)) {
            throw new IllegalArgumentException("custom skin request identity is invalid");
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
        if (!(other instanceof CustomSkinRequestIdentity)) {
            return false;
        }
        CustomSkinRequestIdentity identity = (CustomSkinRequestIdentity) other;
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
