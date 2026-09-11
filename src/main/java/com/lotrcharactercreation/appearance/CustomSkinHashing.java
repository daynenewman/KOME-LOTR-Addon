package com.lotrcharactercreation.appearance;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

public final class CustomSkinHashing {

    private static final char[] LOWERCASE_HEX = "0123456789abcdef".toCharArray();
    private static final Pattern CANONICAL_SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private CustomSkinHashing() {}

    public static String sha256Hex(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }
        return toLowercaseHex(newSha256().digest(bytes));
    }

    public static boolean isCanonicalSha256(String hash) {
        return hash != null && CANONICAL_SHA_256.matcher(hash).matches();
    }

    static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }

    static String toLowercaseHex(byte[] bytes) {
        char[] characters = new char[bytes.length * 2];
        for (int index = 0; index < bytes.length; index++) {
            int value = bytes[index] & 0xFF;
            characters[index * 2] = LOWERCASE_HEX[value >>> 4];
            characters[index * 2 + 1] = LOWERCASE_HEX[value & 0x0F];
        }
        return new String(characters);
    }
}
