package com.upcraft.stay.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Cryptographic digest and secure token/code generation helpers.
 */
public final class HashUtils {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int DEFAULT_TOKEN_BYTES = 32;
    private static final int DEFAULT_INVITE_CODE_LENGTH = 8;

    private HashUtils() {
    }

    /**
     * Generates a URL-safe random token for invitation links.
     *
     * @return raw URL-safe token string
     */
    public static String generateUrlSafeToken() {
        byte[] randomBytes = new byte[DEFAULT_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * Generates a human-friendly uppercase alphanumeric one-time invitation code.
     *
     * @return 8-character invitation code excluding ambiguous characters
     */
    public static String generateInviteCode() {
        StringBuilder builder = new StringBuilder(DEFAULT_INVITE_CODE_LENGTH);
        for (int index = 0; index < DEFAULT_INVITE_CODE_LENGTH; index++) {
            builder.append(CODE_ALPHABET[SECURE_RANDOM.nextInt(CODE_ALPHABET.length)]);
        }
        return builder.toString();
    }

    /**
     * Computes deterministic SHA-256 hex digest for storing tokens/codes safely.
     *
     * @param rawValue plaintext token or code
     * @return lowercase 64-char hex digest
     */
    public static String sha256Hex(String rawValue) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", exception);
        }
    }
}

