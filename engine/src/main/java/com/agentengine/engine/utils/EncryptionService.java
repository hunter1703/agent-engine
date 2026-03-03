package com.agentengine.engine.utils;

import io.quarkus.arc.Unremovable;
import jakarta.inject.Singleton;
import jakarta.enterprise.event.Observes;
import io.quarkus.runtime.StartupEvent;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import com.agentengine.engine.infra.EncryptionInfraConfig;
import com.agentengine.engine.repository.InfraMongoRepository;

@Singleton
@Unremovable
public class EncryptionService {
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12; // 96 bits recommended for GCM
  private static final int GCM_TAG_LENGTH = 128; // in bits

  @Inject
  InfraMongoRepository infraMongoRepository;

  private SecretKey secretKey;
  private final SecureRandom secureRandom = new SecureRandom();

  void init(@Observes StartupEvent ev) {
    EncryptionInfraConfig config = infraMongoRepository.findOneByType(EncryptionInfraConfig.TYPE);
    String keyToUse;

    if (config != null && config.getKey() != null && !config.getKey().isBlank()) {
      keyToUse = config.getKey();
    } else {
      byte[] newKeyBytes = new byte[32];
      secureRandom.nextBytes(newKeyBytes);
      keyToUse = Base64.getEncoder().encodeToString(newKeyBytes);
      
      if (config == null) {
        config = new EncryptionInfraConfig();
      }
      config.setKey(keyToUse);
      infraMongoRepository.save(config);
    }

    byte[] decodedKey = Base64.getDecoder().decode(keyToUse);
    if (decodedKey.length != 32) {
      throw new IllegalArgumentException("Encryption key must be exactly 32 bytes (256 bits)");
    }
    this.secretKey = new SecretKeySpec(decodedKey, "AES");
  }

  public String encrypt(final String plaintext) {
    if (plaintext == null) {
      return null;
    }
    try {
      final Cipher cipher = Cipher.getInstance(ALGORITHM);
      final byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);
      final GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

      cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
      final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      // Combine IV and Ciphertext
      final byte[] ivAndCiphertext = new byte[GCM_IV_LENGTH + ciphertext.length];
      System.arraycopy(iv, 0, ivAndCiphertext, 0, GCM_IV_LENGTH);
      System.arraycopy(ciphertext, 0, ivAndCiphertext, GCM_IV_LENGTH, ciphertext.length);

      return Base64.getEncoder().encodeToString(ivAndCiphertext);
    } catch (Exception e) {
      throw new RuntimeException("Encryption failed", e);
    }
  }

  public String decrypt(final String base64IvAndCiphertext) {
    if (base64IvAndCiphertext == null) {
      return null;
    }
    try {
      final byte[] ivAndCiphertext = Base64.getDecoder().decode(base64IvAndCiphertext);
      if (ivAndCiphertext.length < GCM_IV_LENGTH) {
        throw new IllegalArgumentException("Invalid ciphertext length");
      }

      final byte[] iv = new byte[GCM_IV_LENGTH];
      System.arraycopy(ivAndCiphertext, 0, iv, 0, GCM_IV_LENGTH);

      final byte[] ciphertext = new byte[ivAndCiphertext.length - GCM_IV_LENGTH];
      System.arraycopy(ivAndCiphertext, GCM_IV_LENGTH, ciphertext, 0, ciphertext.length);

      final Cipher cipher = Cipher.getInstance(ALGORITHM);
      final GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);

      final byte[] plaintextBytes = cipher.doFinal(ciphertext);
      return new String(plaintextBytes, StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new RuntimeException("Decryption failed", e);
    }
  }
}
