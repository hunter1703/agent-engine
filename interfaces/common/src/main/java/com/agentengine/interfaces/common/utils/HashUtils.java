package com.agentengine.interfaces.common.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class HashUtils {
    private static final byte[] STATIC_KEY = "StaticKey".getBytes(StandardCharsets.UTF_8);

    private HashUtils(){}

    public static byte[] HMACSHA256(final byte[] key, final byte[] data) {
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static String HMACSHA256_Base64(final String data) {
        if (data == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(HMACSHA256(STATIC_KEY, data.getBytes(StandardCharsets.UTF_8)));
    }
}
