package com.lotrcharactercreation.client.appearance;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.appearance.AppearancePreset;
import com.lotrcharactercreation.appearance.AppearanceSourceType;
import com.lotrcharactercreation.appearance.ExternalAppearancePresetScanner;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
final class ExternalAppearanceTextureManager {

    private static final Logger LOGGER = LogManager.getLogger("lotrcharactercreation");
    private static final Pattern SAFE_RELATIVE_PATH = Pattern.compile("(?:[a-z0-9_-]+/){3}[a-z0-9_-]+\\.png");

    private final File canonicalRoot;
    private final Map<String, ResourceLocation> loadedTextures = new HashMap<String, ResourceLocation>();
    private final Set<String> failedPresetIds = new HashSet<String>();

    ExternalAppearanceTextureManager(File root) {
        File resolvedRoot = null;
        if (root != null) {
            try {
                resolvedRoot = root.getCanonicalFile();
            } catch (IOException | SecurityException exception) {
                LOGGER.warn("Could not resolve external appearance texture root: " + root, exception);
            }
        }
        canonicalRoot = resolvedRoot;
    }

    ResourceLocation resolve(AppearancePreset preset) {
        if (preset == null || preset.getSourceType() != AppearanceSourceType.EXTERNAL) {
            return null;
        }

        ResourceLocation loaded = loadedTextures.get(preset.getId());
        if (loaded != null) {
            return loaded;
        }
        if (failedPresetIds.contains(preset.getId())) {
            return null;
        }

        AppearancePreset registeredPreset = ClientLocalAppearancePresetCatalog.get().findById(preset.getId());
        if (registeredPreset != preset) {
            return fail(preset, "preset is not the locally registered definition", null);
        }
        if (canonicalRoot == null) {
            return fail(preset, "custom skin root is unavailable", null);
        }

        String relativePath = preset.getExternalRelativePath();
        if (relativePath == null || !SAFE_RELATIVE_PATH.matcher(relativePath)
            .matches()) {
            return fail(preset, "relative path is invalid", null);
        }

        File target = new File(canonicalRoot, relativePath.replace('/', File.separatorChar));
        File canonicalTarget;
        try {
            if (Files.isSymbolicLink(target.toPath())) {
                return fail(preset, "symbolic links are not allowed", null);
            }
            canonicalTarget = target.getCanonicalFile();
            if (!canonicalTarget.toPath()
                .startsWith(canonicalRoot.toPath())) {
                return fail(preset, "resolved path leaves the custom skin root", null);
            }
        } catch (IOException | SecurityException exception) {
            return fail(preset, "path validation failed", exception);
        }

        if (!canonicalTarget.isFile()) {
            return fail(preset, "file is missing or unreadable", null);
        }

        try {
            BufferedImage image = ImageIO.read(canonicalTarget);
            if (image == null) {
                return fail(preset, "PNG decoding returned no image", null);
            }
            if (!ExternalAppearancePresetScanner
                .hasExpectedDimensions(preset.getRace(), image.getWidth(), image.getHeight())) {
                return fail(
                    preset,
                    "decoded dimensions are no longer valid: " + image.getWidth() + "x" + image.getHeight(),
                    null);
            }

            Minecraft minecraft = Minecraft.getMinecraft();
            DynamicTexture dynamicTexture = new DynamicTexture(image);
            ResourceLocation location = minecraft.getTextureManager()
                .getDynamicTextureLocation("lotrcharactercreation_" + preset.getId(), dynamicTexture);
            loadedTextures.put(preset.getId(), location);
            return location;
        } catch (IOException | RuntimeException exception) {
            return fail(preset, "PNG decoding or texture registration failed", exception);
        }
    }

    private ResourceLocation fail(AppearancePreset preset, String reason, Throwable cause) {
        failedPresetIds.add(preset.getId());
        String message = "Could not load external appearance texture " + preset.getExternalRelativePath()
            + " ("
            + reason
            + ")";
        if (cause == null) {
            LOGGER.warn(message);
        } else {
            LOGGER.warn(message, cause);
        }
        return null;
    }
}
