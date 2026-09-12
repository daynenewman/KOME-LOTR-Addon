package com.lotrcharactercreation.network;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

/** Wire-compatible bounds for pre-custom-skin Character Creation requests. */
final class LegacyC2SProtocol {

    static final int MAX_PENDING_ACTIONS_PER_PLAYER = 32;
    static final int MAX_PENDING_ACTIONS_TOTAL = 2048;

    static final int MAX_RACE_ID_BYTES = 16;
    static final int MAX_SEX_ID_BYTES = 16;
    static final int MAX_FACTION_ID_BYTES = 32;
    static final int MAX_STAGE_ID_BYTES = 16;
    static final int MAX_PLEDGE_CODE_BYTES = 32;
    static final int MAX_APPEARANCE_PRESET_ID_BYTES = 128;

    private static final Logger LOGGER = LogManager.getLogger("lotrcharactercreation");
    private static final Set<String> LOGGED_MALFORMED_TYPES = Collections.synchronizedSet(new HashSet<String>());

    private LegacyC2SProtocol() {}

    static String readRequiredString(ByteBuf buffer, int maximumBytes) {
        String value = readString(buffer, maximumBytes);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("required request string is empty");
        }
        return value;
    }

    static String readNullableString(ByteBuf buffer, int maximumBytes) {
        String value = readString(buffer, maximumBytes);
        return value.isEmpty() ? null : value;
    }

    static void requireFullyRead(ByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("request packet contains trailing data");
        }
    }

    static boolean isValidRequiredString(String value, int maximumBytes) {
        return value != null && !value.isEmpty() && isWithinUtf8Bound(value, maximumBytes);
    }

    static boolean isValidNullableString(String value, int maximumBytes) {
        return value == null || isWithinUtf8Bound(value, maximumBytes);
    }

    static void warnMalformedOnce(String packetType, RuntimeException exception) {
        if (LOGGED_MALFORMED_TYPES.add(packetType)) {
            String message = exception.getMessage();
            LOGGER.warn("Ignoring malformed Character Creation request " + packetType + ": "
                + (message == null || message.isEmpty() ? exception.getClass().getSimpleName() : message));
        }
    }

    private static String readString(ByteBuf buffer, int maximumBytes) {
        if (buffer == null || maximumBytes < 0) {
            throw new IllegalArgumentException("request string bound is invalid");
        }

        int length = ByteBufUtils.readVarInt(buffer, 2);
        if (length < 0 || length > maximumBytes || length > buffer.readableBytes()) {
            throw new IllegalArgumentException("request string exceeds its wire bound");
        }

        byte[] encoded = new byte[length];
        buffer.readBytes(encoded);
        String value = new String(encoded, StandardCharsets.UTF_8);
        if (!Arrays.equals(encoded, value.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("request string is not canonical UTF-8");
        }
        return value;
    }

    private static boolean isWithinUtf8Bound(String value, int maximumBytes) {
        if (value.length() > maximumBytes) {
            return false;
        }
        return value.getBytes(StandardCharsets.UTF_8).length <= maximumBytes;
    }
}
