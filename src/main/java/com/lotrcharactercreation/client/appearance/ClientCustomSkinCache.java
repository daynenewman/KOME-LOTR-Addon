package com.lotrcharactercreation.client.appearance;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.lotrcharactercreation.appearance.CustomSkinHashing;
import com.lotrcharactercreation.appearance.CustomSkinScanLimits;
import com.lotrcharactercreation.appearance.ExternalAppearancePresetScanner;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Client-only content-addressed storage for fully validated custom skin PNGs. */
@SideOnly(Side.CLIENT)
public final class ClientCustomSkinCache {

    private static final String HASH_DIRECTORY = "sha256";
    private static final String PNG_SUFFIX = ".png";
    private static final String PART_SUFFIX = ".part";

    private final Path root;

    public ClientCustomSkinCache(File cacheRoot) throws IOException {
        if (cacheRoot == null) {
            throw new IllegalArgumentException("client custom skin cache root cannot be null");
        }
        Path requestedRoot = cacheRoot.toPath().toAbsolutePath().normalize();
        if (Files.isSymbolicLink(requestedRoot)) {
            throw new IOException("client custom skin cache root cannot be a symbolic link");
        }
        Files.createDirectories(requestedRoot);
        root = requestedRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
    }

    public Path getRoot() {
        return root;
    }

    public Path getCachedPath(String sha256) {
        return resolveHashPath(sha256, PNG_SUFFIX);
    }

    public Path getPartPath(String sha256) {
        return resolveHashPath(sha256, PNG_SUFFIX + PART_SUFFIX);
    }

    public CachedContent find(ClientExternalSkinDefinition definition) {
        if (definition == null) {
            return null;
        }
        return readValidated(
            getCachedPath(definition.getSha256()),
            definition,
            true);
    }

    /** Writes complete candidate bytes to the deterministic temporary path. */
    public Path writePart(ClientExternalSkinDefinition definition, byte[] bytes) throws IOException {
        if (definition == null || bytes == null) {
            throw new IllegalArgumentException("custom skin definition and bytes cannot be null");
        }
        if (bytes.length > CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES) {
            throw new IOException("custom skin temporary content exceeds the maximum PNG size");
        }
        Path part = getPartPath(definition.getSha256());
        ensureSafeParent(part);
        try {
            Files.write(
                part,
                bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
            return part;
        } catch (IOException | RuntimeException exception) {
            deleteCachePathQuietly(part);
            throw exception;
        }
    }

    /** Validates a completed .part file and promotes it within the same shard directory. */
    public CachedContent promotePart(ClientExternalSkinDefinition definition) throws IOException {
        if (definition == null) {
            throw new IllegalArgumentException("custom skin definition cannot be null");
        }
        Path part = getPartPath(definition.getSha256());
        Path target = getCachedPath(definition.getSha256());
        CachedContent candidate = readValidated(part, definition, true);
        if (candidate == null) {
            deleteCachePathQuietly(part);
            return null;
        }

        CachedContent existing = readValidated(target, definition, true);
        if (existing != null) {
            deleteCachePathQuietly(part);
            return existing;
        }
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            deleteCachePath(target);
        }

        ensureSafeParent(target);
        try {
            Files.move(part, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(part, target);
        } catch (FileAlreadyExistsException exception) {
            CachedContent raced = readValidated(target, definition, true);
            if (raced == null) {
                deleteCachePathQuietly(part);
                throw exception;
            }
            deleteCachePathQuietly(part);
            return raced;
        }

        CachedContent promoted = readValidated(target, definition, true);
        if (promoted == null) {
            deleteCachePathQuietly(target);
        }
        return promoted;
    }

    /** Imports, but never modifies or deletes, an old manually installed PNG. */
    public CachedContent importLegacyFile(ClientExternalSkinDefinition definition, File source) throws IOException {
        if (definition == null || source == null) {
            throw new IllegalArgumentException("custom skin definition and legacy source cannot be null");
        }
        CachedContent existing = find(definition);
        if (existing != null) {
            return existing;
        }

        CachedContent validatedSource = readValidated(source.toPath(), definition, false);
        if (validatedSource == null) {
            return null;
        }
        writePart(definition, validatedSource.copyBytes());
        return promotePart(definition);
    }

    public void discardPart(String sha256) {
        deleteCachePathQuietly(getPartPath(sha256));
    }

    private CachedContent readValidated(Path path, ClientExternalSkinDefinition definition,
        boolean requireCacheContainment) {
        try {
            Path normalized = path.toAbsolutePath().normalize();
            if (requireCacheContainment && !normalized.startsWith(root)) {
                return null;
            }
            if (Files.isSymbolicLink(normalized)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                return null;
            }
            Path realPath = normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (requireCacheContainment && !realPath.startsWith(root)) {
                return null;
            }
            if (Files.size(realPath) != definition.getByteSize()) {
                return null;
            }

            byte[] bytes = readBounded(realPath, CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES);
            if (bytes.length != definition.getByteSize()
                || !definition.getSha256().equals(CustomSkinHashing.sha256Hex(bytes))) {
                return null;
            }
            BufferedImage image = ExternalAppearancePresetScanner.decodeFullyValidatedPng(
                bytes,
                definition.getWidth(),
                definition.getHeight());
            return image == null ? null : new CachedContent(definition.getIdentity(), realPath, bytes, image);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private Path resolveHashPath(String sha256, String suffix) {
        requireCanonicalHash(sha256);
        Path path = root.resolve(HASH_DIRECTORY)
            .resolve(sha256.substring(0, 2))
            .resolve(sha256 + suffix)
            .normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("resolved custom skin cache path leaves the cache root");
        }
        return path;
    }

    private void ensureSafeParent(Path target) throws IOException {
        Path hashDirectory = root.resolve(HASH_DIRECTORY);
        Path shardDirectory = target.getParent();
        ensureSafeDirectory(hashDirectory);
        ensureSafeDirectory(shardDirectory);
        Path realParent = shardDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!realParent.startsWith(root)) {
            throw new IOException("custom skin cache shard leaves the cache root");
        }
    }

    private void ensureSafeDirectory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory)) {
            throw new IOException("symbolic links are not allowed in the custom skin cache");
        }
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)
            || !directory.toRealPath(LinkOption.NOFOLLOW_LINKS).startsWith(root)) {
            throw new IOException("custom skin cache directory is unsafe");
        }
    }

    private void deleteCachePath(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IOException("refusing to delete outside the custom skin cache");
        }
        Files.deleteIfExists(normalized);
    }

    private void deleteCachePathQuietly(Path path) {
        try {
            deleteCachePath(path);
        } catch (IOException | SecurityException ignored) {
            // Best-effort cleanup of cache-owned temporary or corrupt content.
        }
    }

    private static byte[] readBounded(Path path, int maximumBytes) throws IOException {
        try (InputStream input = Files.newInputStream(path);
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192))) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) >= 0) {
                if (count == 0) {
                    continue;
                }
                total += count;
                if (total > maximumBytes) {
                    throw new IOException("custom skin content exceeds the maximum PNG size");
                }
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private static void requireCanonicalHash(String sha256) {
        if (!CustomSkinHashing.isCanonicalSha256(sha256)) {
            throw new IllegalArgumentException("SHA-256 must be lowercase 64-character hexadecimal");
        }
    }

    public static final class CachedContent {

        private final ClientCustomSkinIdentity identity;
        private final Path path;
        private final byte[] bytes;
        private final BufferedImage image;

        private CachedContent(ClientCustomSkinIdentity identity, Path path, byte[] bytes, BufferedImage image) {
            this.identity = identity;
            this.path = path;
            this.bytes = java.util.Arrays.copyOf(bytes, bytes.length);
            this.image = image;
        }

        public ClientCustomSkinIdentity getIdentity() {
            return identity;
        }

        public Path getPath() {
            return path;
        }

        public byte[] copyBytes() {
            return java.util.Arrays.copyOf(bytes, bytes.length);
        }

        BufferedImage getImage() {
            return image;
        }
    }
}
