package com.lotrcharactercreation.appearance;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class CustomSkinHashing {

    private static final char[] LOWERCASE_HEX = "0123456789abcdef".toCharArray();

    private CustomSkinHashing() {}

    public static String sha256Hex(byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }
        return toLowercaseHex(newSha256().digest(bytes));
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
