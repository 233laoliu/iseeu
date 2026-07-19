package com.iseeu.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Hash / HMAC helpers. All methods are static; no state; no logging of inputs.
 */
public final class HashUtils {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private HashUtils() {}

    public static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS — should never happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        return toHex(sha256(data));
    }

    public static String sha256Hex(String s) {
        return sha256Hex(s.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    public static String hmacSha256Hex(String key, String data) {
        return toHex(hmacSha256(
                key.getBytes(StandardCharsets.UTF_8),
                data.getBytes(StandardCharsets.UTF_8)));
    }

    public static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            out[i * 2]     = HEX[v >>> 4];
            out[i * 2 + 1] = HEX[v & 0x0F];
        }
        return new String(out);
    }

    /** Constant-time string compare — do not use {@code String.equals} on secrets. */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) {
            r |= a.charAt(i) ^ b.charAt(i);
        }
        return r == 0;
    }
}
