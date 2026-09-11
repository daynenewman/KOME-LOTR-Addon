package com.lotrcharactercreation.client.appearance;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearanceSourceType;
import com.lotrcharactercreation.appearance.CustomSkinEntry;
import com.lotrcharactercreation.appearance.CustomSkinScanLimits;
import com.lotrcharactercreation.appearance.ExternalAppearancePresetScanner;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Connection-scoped external appearance metadata, independent of pixel availability. */
@SideOnly(Side.CLIENT)
public final class ClientExternalSkinDefinition {

    private final AppearancePreset preset;
    private final ClientCustomSkinIdentity identity;
    private final int byteSize;
    private final int width;
    private final int height;

    public static ClientExternalSkinDefinition fromValidatedEntry(CustomSkinEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("custom skin entry cannot be null");
        }
        return new ClientExternalSkinDefinition(
            entry.getAppearancePreset(),
            entry.getSha256(),
            entry.getByteSize(),
            entry.getWidth(),
            entry.getHeight());
    }

    public ClientExternalSkinDefinition(AppearancePreset preset, String sha256, int byteSize, int width, int height) {
        if (preset == null || preset.getSourceType() != AppearanceSourceType.EXTERNAL) {
            throw new IllegalArgumentException("client external skin definition requires an external preset");
        }
        if (byteSize <= 0 || byteSize > CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES) {
            throw new IllegalArgumentException("client external skin byte size is invalid");
        }
        if (!ExternalAppearancePresetScanner.hasExpectedDimensions(preset.getRace(), width, height)) {
            throw new IllegalArgumentException("client external skin dimensions do not match its race");
        }
        this.preset = preset;
        identity = new ClientCustomSkinIdentity(preset.getId(), sha256);
        this.byteSize = byteSize;
        this.width = width;
        this.height = height;
    }

    public AppearancePreset getPreset() {
        return preset;
    }

    public ClientCustomSkinIdentity getIdentity() {
        return identity;
    }

    public String getPresetId() {
        return identity.getPresetId();
    }

    public String getSha256() {
        return identity.getSha256();
    }

    public int getByteSize() {
        return byteSize;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
