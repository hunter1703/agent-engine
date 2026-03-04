package com.agentengine.engine.utils;

import io.quarkus.arc.Unremovable;
import jakarta.inject.Singleton;
import jakarta.enterprise.event.Observes;
import io.quarkus.runtime.StartupEvent;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import com.agentengine.engine.infra.EncryptionInfraConfig;
import com.agentengine.engine.repository.InfraMongoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class EncryptionService {
  private static final String ENCRYPTED_PREFIX = "enc";
  private static final String ENCRYPTED_PREFIX_STR = ENCRYPTED_PREFIX + "::";
  private static final Logger LOG = LoggerFactory.getLogger(EncryptionService.class);
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12; // 96 bits recommended for GCM
  private static final int GCM_TAG_LENGTH = 128; // in bits
  private static final ThreadLocal<Cipher> CIPHER_CACHE = ThreadLocal.withInitial(() -> {
    try {
      return Cipher.getInstance(ALGORITHM);
    } catch (Exception e) {
      throw new RuntimeException("Failed to initialize cipher", e);
    }
  });

  // package-private for testing
  @Inject
  InfraMongoRepository infraMongoRepository;

  private SecretKey secretKey;
  private boolean encryptionEnabled;
  private final SecureRandom secureRandom = new SecureRandom();

  void init(@Observes StartupEvent ev) {
    final EncryptionInfraConfig config = infraMongoRepository.findOneByType(EncryptionInfraConfig.TYPE);
    if (config == null || config.getKey() == null || config.getKey().isBlank()) {
      LOG.warn("Encryption config missing or empty; persisting secure fields in plaintext.");
      encryptionEnabled = false;
      secretKey = null;
      return;
    }

    final byte[] decodedKey;
    try {
      decodedKey = Base64.getDecoder().decode(config.getKey());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Encryption key stored in database is not valid Base64", e);
    }
    if (decodedKey.length != 32) {
      throw new IllegalArgumentException("Encryption key must be exactly 32 bytes (256 bits)");
    }
    this.secretKey = new SecretKeySpec(decodedKey, "AES");
    this.encryptionEnabled = true;
  }

  public boolean isEncryptionEnabled() {
    return encryptionEnabled;
  }

  public String encrypt(final String plaintext) {
    if (plaintext == null) {
      return null;
    }
    if (!encryptionEnabled) {
      return plaintext;
    }
    try {
      final Cipher cipher = CIPHER_CACHE.get();
      final byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);
      final GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

      cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
      final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      // Combine IV and Ciphertext
      final byte[] ivAndCiphertext = new byte[GCM_IV_LENGTH + ciphertext.length];
      System.arraycopy(iv, 0, ivAndCiphertext, 0, GCM_IV_LENGTH);
      System.arraycopy(ciphertext, 0, ivAndCiphertext, GCM_IV_LENGTH, ciphertext.length);

      return ENCRYPTED_PREFIX_STR + Base64.getEncoder().encodeToString(ivAndCiphertext);
    } catch (Exception e) {
      throw new RuntimeException("Encryption failed", e);
    }
  }

  public String decrypt(final String base64IvAndCiphertext) {
    if (base64IvAndCiphertext == null) {
      return null;
    }
    if (!encryptionEnabled || !base64IvAndCiphertext.startsWith(ENCRYPTED_PREFIX_STR)) {
      return base64IvAndCiphertext;
    }
    try {
      final String base64Only = base64IvAndCiphertext.substring(ENCRYPTED_PREFIX_STR.length());
      final byte[] ivAndCiphertext = Base64.getDecoder().decode(base64Only);
      if (ivAndCiphertext.length < GCM_IV_LENGTH) {
        throw new IllegalArgumentException("Invalid ciphertext length");
      }

      final byte[] iv = new byte[GCM_IV_LENGTH];
      System.arraycopy(ivAndCiphertext, 0, iv, 0, GCM_IV_LENGTH);

      final byte[] ciphertext = new byte[ivAndCiphertext.length - GCM_IV_LENGTH];
      System.arraycopy(ivAndCiphertext, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

      final Cipher cipher = CIPHER_CACHE.get();
      final GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

      final byte[] plaintextBytes = cipher.doFinal(ciphertext);
      return new String(plaintextBytes, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("Decryption failed", e);
    }
  }
}
