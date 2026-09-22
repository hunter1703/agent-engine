package com.agentengine.util.crypto;

import com.agentengine.util.common.ExceptionUtils;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class CryptoClient implements AutoCloseable {
  public static final char SEPARATOR = '_';
  public static final String PREFIX = "encr" + SEPARATOR;
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final ThreadLocal<Cipher> CIPHER_CACHE =
      ThreadLocal.withInitial(
          () -> {
            try {
              return Cipher.getInstance(ALGORITHM);
            } catch (Exception exception) {
              throw new RuntimeException("Failed to initialize cipher", exception);
            }
          });

  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 128;
  private final SecretKey key;
  private final String keyId;
  private final SecureRandom secureRandom = new SecureRandom();

  public CryptoClient(SecretKey key, String keyId) {
    this.key = key;
    this.keyId = keyId;
  }

  @Override
  public void close() throws Exception {}

  public String encrypt(final String plaintext) {
    if (plaintext == null) {
      return null;
    }
    try {
      final Cipher cipher = CIPHER_CACHE.get();
      final byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);
      cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      final byte[] ivAndCiphertext = new byte[GCM_IV_LENGTH + ciphertext.length];
      System.arraycopy(iv, 0, ivAndCiphertext, 0, GCM_IV_LENGTH);
      System.arraycopy(ciphertext, 0, ivAndCiphertext, GCM_IV_LENGTH, ciphertext.length);

      return PREFIX + keyId + SEPARATOR + Base64.getEncoder().encodeToString(ivAndCiphertext);
    } catch (Exception exception) {
      throw ExceptionUtils.wrapInRuntimeException(exception);
    }
  }

  public String decrypt(final String ciphertext) {
    if (ciphertext == null || !ciphertext.startsWith(PREFIX)) {
      return ciphertext;
    }
    try {
      final int keyIdEnd = ciphertext.indexOf(SEPARATOR, PREFIX.length());
      final byte[] ivAndCiphertext = Base64.getDecoder().decode(ciphertext.substring(keyIdEnd + 1));
      if (ivAndCiphertext.length < GCM_IV_LENGTH) {
        throw new IllegalArgumentException("Invalid ciphertext length");
      }

      final byte[] iv = new byte[GCM_IV_LENGTH];
      System.arraycopy(ivAndCiphertext, 0, iv, 0, GCM_IV_LENGTH);

      final byte[] encrypted = new byte[ivAndCiphertext.length - GCM_IV_LENGTH];
      System.arraycopy(ivAndCiphertext, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

      final Cipher cipher = CIPHER_CACHE.get();
      cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
      return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw new RuntimeException("Decryption failed", exception);
    }
  }
}
