package com.agentengine.util.mongodb.encryption;

import com.agentengine.util.common.EncryptionService;
import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.mongodb.infra.EncryptionInfraConfig;
import com.agentengine.util.mongodb.infra.InfraMongoRepository;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class EncryptionServiceImpl implements EncryptionService {
  private static final String ENCRYPTED_PREFIX = "enc";
  private static final String ENCRYPTED_PREFIX_STR = ENCRYPTED_PREFIX + "::";
  private static final Logger LOG = LoggerFactory.getLogger(EncryptionServiceImpl.class);
  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int GCM_IV_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 128;
  private static final ThreadLocal<Cipher> CIPHER_CACHE = ThreadLocal.withInitial(() -> {
    try {
      return Cipher.getInstance(ALGORITHM);
    } catch (Exception exception) {
      throw new RuntimeException("Failed to initialize cipher", exception);
    }
  });
  private LazyLoader<SecretKey> secretKey;
  private final SecureRandom secureRandom = new SecureRandom();

  public EncryptionServiceImpl(InfraMongoRepository infraMongoRepository) {
    this.secretKey = new LazyLoader<>(() -> {
      final EncryptionInfraConfig config = infraMongoRepository.findOneByType(EncryptionInfraConfig.TYPE);
      if (config == null || config.getKey() == null || config.getKey().isBlank()) {
        LOG.warn("Encryption config missing or empty; persisting secure fields in plaintext.");
        return null;
      }

      final byte[] decodedKey;
      try {
        decodedKey = Base64.getDecoder().decode(config.getKey());
      } catch (IllegalArgumentException exception) {
        throw new IllegalArgumentException("Encryption key stored in database is not valid Base64", exception);
      }
      if (decodedKey.length != 32) {
        throw new IllegalArgumentException("Encryption key must be exactly 32 bytes (256 bits)");
      }
      return new SecretKeySpec(decodedKey, "AES");
    });
  }

  public void init(@Observes final StartupEvent event) {

  }

  @Override
  public boolean isEncryptionEnabled() {
    return secretKey.get() != null;
  }

  @Override
  public String encrypt(final String plaintext) {
    if (plaintext == null) {
      return null;
    }
    if (!isEncryptionEnabled()) {
      return plaintext;
    }
    try {
      final Cipher cipher = CIPHER_CACHE.get();
      final byte[] iv = new byte[GCM_IV_LENGTH];
      secureRandom.nextBytes(iv);
      final GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

      cipher.init(Cipher.ENCRYPT_MODE, secretKey.get(), parameterSpec);
      final byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      final byte[] ivAndCiphertext = new byte[GCM_IV_LENGTH + ciphertext.length];
      System.arraycopy(iv, 0, ivAndCiphertext, 0, GCM_IV_LENGTH);
      System.arraycopy(ciphertext, 0, ivAndCiphertext, GCM_IV_LENGTH, ciphertext.length);

      return ENCRYPTED_PREFIX_STR + Base64.getEncoder().encodeToString(ivAndCiphertext);
    } catch (Exception exception) {
      throw new RuntimeException("Encryption failed", exception);
    }
  }

  @Override
  public String decrypt(final String ciphertext) {
    if (ciphertext == null) {
      return null;
    }
    if (!isEncryptionEnabled() || !ciphertext.startsWith(ENCRYPTED_PREFIX_STR)) {
      return ciphertext;
    }
    try {
      final String base64Only = ciphertext.substring(ENCRYPTED_PREFIX_STR.length());
      final byte[] ivAndCiphertext = Base64.getDecoder().decode(base64Only);
      if (ivAndCiphertext.length < GCM_IV_LENGTH) {
        throw new IllegalArgumentException("Invalid ciphertext length");
      }

      final byte[] iv = new byte[GCM_IV_LENGTH];
      System.arraycopy(ivAndCiphertext, 0, iv, 0, GCM_IV_LENGTH);

      final byte[] encrypted = new byte[ivAndCiphertext.length - GCM_IV_LENGTH];
      System.arraycopy(ivAndCiphertext, GCM_IV_LENGTH, encrypted, 0, encrypted.length);

      final Cipher cipher = CIPHER_CACHE.get();
      final GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
      cipher.init(Cipher.DECRYPT_MODE, secretKey.get(), parameterSpec);

      final byte[] plaintextBytes = cipher.doFinal(encrypted);
      return new String(plaintextBytes, StandardCharsets.UTF_8);
    } catch (Exception exception) {
      throw new RuntimeException("Decryption failed", exception);
    }
  }
}
