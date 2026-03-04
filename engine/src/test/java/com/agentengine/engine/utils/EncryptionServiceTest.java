package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.agentengine.engine.infra.EncryptionInfraConfig;
import com.agentengine.engine.repository.InfraMongoRepository;

class EncryptionServiceTest {

  private EncryptionService encryptionService;

  @BeforeEach
  void setUp() {
    encryptionService = new EncryptionService();
    encryptionService.infraMongoRepository = mock(InfraMongoRepository.class);
    when(encryptionService.infraMongoRepository.findOneByType(EncryptionInfraConfig.TYPE))
        .thenReturn(buildConfig());

    encryptionService.init(null);
  }

  @Test
  void testEncryptDecrypt() {
    final String plaintext = "This is a strictly confidential system prompt with 🚀";
    final String ciphertext = encryptionService.encrypt(plaintext);
    
    assertThat(ciphertext).isNotNull().isNotEqualTo(plaintext);
    
    final String decrypted = encryptionService.decrypt(ciphertext);
    assertThat(decrypted).isEqualTo(plaintext);
  }

  @Test
  void testRandomizationOfIv() {
    final String plaintext = "Hello World";
    final String ciphertext1 = encryptionService.encrypt(plaintext);
    final String ciphertext2 = encryptionService.encrypt(plaintext);
    
    assertThat(ciphertext1).isNotEqualTo(ciphertext2);
    
    assertThat(encryptionService.decrypt(ciphertext1)).isEqualTo(plaintext);
    assertThat(encryptionService.decrypt(ciphertext2)).isEqualTo(plaintext);
  }

  @Test
  void testNullHandling() {
    assertThat(encryptionService.encrypt(null)).isNull();
    assertThat(encryptionService.decrypt(null)).isNull();
  }

  @Test
  void testMissingConfigFallsBackToPlaintext() {
    final EncryptionService service = new EncryptionService();
    service.infraMongoRepository = mock(InfraMongoRepository.class);
    when(service.infraMongoRepository.findOneByType(EncryptionInfraConfig.TYPE)).thenReturn(null);

    service.init(null);

    final String plaintext = "No encryption";
    assertThat(service.encrypt(plaintext)).isEqualTo(plaintext);
    assertThat(service.decrypt(plaintext)).isEqualTo(plaintext);
  }

  @Test
  void testTamperingFails() {
    final String plaintext = "Sensitive Data";
    final String ciphertext = encryptionService.encrypt(plaintext);
    
    // Tamper with the base64 payload (strip "enc::" first)
    final String prefix = "enc::";
    final String base64 = ciphertext.substring(prefix.length());
    byte[] decoded = Base64.getDecoder().decode(base64);
    decoded[decoded.length - 1] = (byte) (decoded[decoded.length - 1] + 1); // Mutate tag
    final String tampered = prefix + Base64.getEncoder().encodeToString(decoded);
    
    assertThatThrownBy(() -> encryptionService.decrypt(tampered))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Decryption failed");
  }

  @Test
  void testInvalidKeyLengthThrows() {
    final EncryptionService badService = new EncryptionService();
    badService.infraMongoRepository = mock(InfraMongoRepository.class);
    final EncryptionInfraConfig badConfig = new EncryptionInfraConfig();
    badConfig.setKey(Base64.getEncoder().encodeToString(new byte[16])); // 128 bit key
    when(badService.infraMongoRepository.findOneByType(EncryptionInfraConfig.TYPE)).thenReturn(badConfig);
    
    assertThatThrownBy(() -> badService.init(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static EncryptionInfraConfig buildConfig() {
    final EncryptionInfraConfig config = new EncryptionInfraConfig();
    config.setKey(Base64.getEncoder().encodeToString(new byte[32]));
    return config;
  }
}
