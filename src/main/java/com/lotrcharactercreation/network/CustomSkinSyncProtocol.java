package com.lotrcharactercreation.network;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lotrcharactercreation.appearance.CustomSkinManifestEntry;

import io.netty.buffer.ByteBuf;

/** Shared wire limits and bounded codecs for the custom-skin protocol. */
public final class CustomSkinSyncProtocol {

    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ENCODED_PACKET_BYTES = 24 * 1024;
    public static final int MAX_CHUNK_BYTES = 23 * 1024;
    public static final int MAX_MANIFEST_PAGE_ENTRIES = 64;
    public static final int MAX_REQUEST_IDENTITIES_PER_PAGE = 64;
    public static final int MAX_ACTIVE_TRANSFERS = 2;
    public static final int MAX_QUEUED_TRANSFERS = 512;
    public static final int MAX_FILE_RETRIES = 2;
    public static final int MAX_PENDING_SERVER_ACTIONS_PER_PLAYER = 128;

    static final int MAX_PRESET_ID_BYTES = 128;
    static final int MAX_RACE_ID_BYTES = 16;
    static final int MAX_SEX_ID_BYTES = 16;
    static final int MAX_GROUP_ID_BYTES = 32;
    static final int MAX_FILENAME_STEM_BYTES = 64;
    static final int SHA_256_BYTES = 64;
    static final int MAX_FAILURE_REASON_BYTES = 128;

    private static final Logger LOGGER = LogManager.getLogger("lotrcharactercreation");
    private static final Set<String> LOGGED_MALFORMED_TYPES = Collections.synchronizedSet(new HashSet<String>());

    private CustomSkinSyncProtocol() {}

    static void requirePacketSize(ByteBuf buffer) {
        if (buffer == null || buffer.readableBytes() < 0
            || buffer.readableBytes() > MAX_ENCODED_PACKET_BYTES) {
            throw new IllegalArgumentException("custom skin packet exceeds the encoded size limit");
        }
    }

    static void requireFullyRead(ByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("custom skin packet contains trailing data");
        }
    }

    static void writeRequiredString(ByteBuf buffer, String value, int maximumBytes) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("required custom skin string is empty");
        }
        writeStringBytes(buffer, value, maximumBytes);
    }

    static String readRequiredString(ByteBuf buffer, int maximumBytes) {
        String value = readStringBytes(buffer, maximumBytes);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("required custom skin string is empty");
        }
        return value;
    }

    static void writeNullableString(ByteBuf buffer, String value, int maximumBytes) {
        writeStringBytes(buffer, value == null ? "" : value, maximumBytes);
    }

    static String readNullableString(ByteBuf buffer, int maximumBytes) {
        String value = readStringBytes(buffer, maximumBytes);
        return value.isEmpty() ? null : value;
    }

    static int encodedRequiredStringBytes(String value, int maximumBytes) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("required custom skin string is empty");
        }
        return 2 + encodedBytes(value, maximumBytes).length;
    }

    static int encodedNullableStringBytes(String value, int maximumBytes) {
        return 2 + encodedBytes(value == null ? "" : value, maximumBytes).length;
    }

    static void writeManifestEntry(ByteBuf buffer, CustomSkinManifestEntry entry) {
        writeRequiredString(buffer, entry.getPresetId(), MAX_PRESET_ID_BYTES);
        writeRequiredString(buffer, entry.getSerializedRaceId(), MAX_RACE_ID_BYTES);
        writeRequiredString(buffer, entry.getSerializedSexId(), MAX_SEX_ID_BYTES);
        writeRequiredString(buffer, entry.getGroupToken(), MAX_GROUP_ID_BYTES);
        writeNullableString(buffer, entry.getGroupId(), MAX_GROUP_ID_BYTES);
        writeRequiredString(buffer, entry.getFilenameStem(), MAX_FILENAME_STEM_BYTES);
        writeRequiredString(buffer, entry.getSha256(), SHA_256_BYTES);
        buffer.writeInt(entry.getByteSize());
        buffer.writeInt(entry.getWidth());
        buffer.writeInt(entry.getHeight());
    }

    static CustomSkinManifestEntry readManifestEntry(ByteBuf buffer) {
        return new CustomSkinManifestEntry(
            readRequiredString(buffer, MAX_PRESET_ID_BYTES),
            readRequiredString(buffer, MAX_RACE_ID_BYTES),
            readRequiredString(buffer, MAX_SEX_ID_BYTES),
            readRequiredString(buffer, MAX_GROUP_ID_BYTES),
            readNullableString(buffer, MAX_GROUP_ID_BYTES),
            readRequiredString(buffer, MAX_FILENAME_STEM_BYTES),
            readRequiredString(buffer, SHA_256_BYTES),
            buffer.readInt(),
            buffer.readInt(),
            buffer.readInt());
    }

    public static int encodedManifestEntryBytes(CustomSkinManifestEntry entry) {
        return encodedRequiredStringBytes(entry.getPresetId(), MAX_PRESET_ID_BYTES)
            + encodedRequiredStringBytes(entry.getSerializedRaceId(), MAX_RACE_ID_BYTES)
            + encodedRequiredStringBytes(entry.getSerializedSexId(), MAX_SEX_ID_BYTES)
            + encodedRequiredStringBytes(entry.getGroupToken(), MAX_GROUP_ID_BYTES)
            + encodedNullableStringBytes(entry.getGroupId(), MAX_GROUP_ID_BYTES)
            + encodedRequiredStringBytes(entry.getFilenameStem(), MAX_FILENAME_STEM_BYTES)
            + encodedRequiredStringBytes(entry.getSha256(), SHA_256_BYTES)
            + 12;
    }

    static void writeIdentity(ByteBuf buffer, String presetId, String sha256) {
        writeRequiredString(buffer, presetId, MAX_PRESET_ID_BYTES);
        writeRequiredString(buffer, sha256, SHA_256_BYTES);
    }

    static String[] readIdentity(ByteBuf buffer) {
        return new String[] {
            readRequiredString(buffer, MAX_PRESET_ID_BYTES),
            readRequiredString(buffer, SHA_256_BYTES)
        };
    }

    public static int encodedIdentityBytes(String presetId, String sha256) {
        return encodedRequiredStringBytes(presetId, MAX_PRESET_ID_BYTES)
            + encodedRequiredStringBytes(sha256, SHA_256_BYTES);
    }

    public static int chunkCountForSize(int byteSize) {
        if (byteSize <= 0 || byteSize > com.lotrcharactercreation.appearance.CustomSkinScanLimits.DEFAULT_MAX_PNG_BYTES) {
            throw new IllegalArgumentException("custom skin byte size is invalid");
        }
        return (byteSize + MAX_CHUNK_BYTES - 1) / MAX_CHUNK_BYTES;
    }

    static void warnMalformedOnce(String packetType, RuntimeException exception) {
        if (LOGGED_MALFORMED_TYPES.add(packetType)) {
            LOGGER.warn("Ignoring malformed custom skin packet " + packetType + ": " + safeMessage(exception));
        }
    }

    private static void writeStringBytes(ByteBuf buffer, String value, int maximumBytes) {
        byte[] encoded = encodedBytes(value, maximumBytes);
        buffer.writeShort(encoded.length);
        buffer.writeBytes(encoded);
    }

    private static String readStringBytes(ByteBuf buffer, int maximumBytes) {
        if (buffer.readableBytes() < 2) {
            throw new IllegalArgumentException("custom skin string length is truncated");
        }
        int length = buffer.readUnsignedShort();
        if (length > maximumBytes || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("custom skin string exceeds its wire bound");
        }
        byte[] encoded = new byte[length];
        buffer.readBytes(encoded);
        String value = new String(encoded, StandardCharsets.UTF_8);
        if (!java.util.Arrays.equals(encoded, value.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("custom skin string is not canonical UTF-8");
        }
        return value;
    }

    private static byte[] encodedBytes(String value, int maximumBytes) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length > maximumBytes) {
            throw new IllegalArgumentException("custom skin string exceeds its wire bound");
        }
        return encoded;
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}
