package com.agentengine.util.common;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class HashUtils {

    private HashUtils() {}

    public static String HMACSHA256_Base64(final String input) {
        if (input == null) {
            return null;
        }
        try {
            final Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            final byte[] secretKey = "agentengine".getBytes(StandardCharsets.UTF_8);
            final SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, "HmacSHA256");
            sha256Hmac.init(secretKeySpec);
            return Base64.getEncoder().encodeToString(sha256Hmac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new RuntimeException("Failed to generate HMACSHA256 hash", exception);
        }
    }

    public static String sha256(final Path file) {
        if (file == null || !Files.exists(file)) {
            return "error";
        }
        try (InputStream inputStream = Files.newInputStream(file)) {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            final byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return Base64.getEncoder().encodeToString(digest.digest());
        } catch (Exception exception) {
            return "error";
        }
    }
}
