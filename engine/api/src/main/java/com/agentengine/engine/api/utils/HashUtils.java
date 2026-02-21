package com.agentengine.engine.api.utils;

import java.nio.charset.StandardCharsets;
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
      return Base64.getEncoder()
          .encodeToString(sha256Hmac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate HMACSHA256 hash", e);
    }
  }
}
