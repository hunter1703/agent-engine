package com.agentengine.engine.repository.codec;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.agentengine.engine.utils.EncryptionService;
import jakarta.enterprise.inject.Instance;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.BsonDocumentReader;
import org.bson.BsonString;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.junit.jupiter.api.Test;

class SecureStringCodecTest {

  @Test
  void constructorDoesNotResolveEncryptionService() {
    final Instance<EncryptionService> encryptionInstance = mock(Instance.class);

    new SecureStringCodec(encryptionInstance);

    verifyNoInteractions(encryptionInstance);
  }

  @Test
  void throwsWhenEncryptionFails() {
    final Instance<EncryptionService> encryptionInstance = mock(Instance.class);
    final EncryptionService encryptionService = mock(EncryptionService.class);
    when(encryptionInstance.isResolvable()).thenReturn(true);
    when(encryptionInstance.get()).thenReturn(encryptionService);
    when(encryptionService.isEncryptionEnabled()).thenReturn(true);
    when(encryptionService.encrypt("secret"))
        .thenThrow(new RuntimeException("boom"));

    final SecureStringCodec codec = new SecureStringCodec(encryptionInstance);
    final BsonDocument document = new BsonDocument();
    final BsonDocumentWriter writer = new BsonDocumentWriter(document);
    writer.writeStartDocument();
    writer.writeName("value");

    assertThatThrownBy(() -> codec.encode(writer, "secret", EncoderContext.builder().build()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to encrypt value");
  }

  @Test
  void throwsWhenDecryptionFails() {
    final Instance<EncryptionService> encryptionInstance = mock(Instance.class);
    final EncryptionService encryptionService = mock(EncryptionService.class);
    when(encryptionInstance.isResolvable()).thenReturn(true);
    when(encryptionInstance.get()).thenReturn(encryptionService);
    when(encryptionService.isEncryptionEnabled()).thenReturn(true);
    when(encryptionService.decrypt("cipher"))
        .thenThrow(new RuntimeException("boom"));

    final SecureStringCodec codec = new SecureStringCodec(encryptionInstance);
    final BsonDocument document = new BsonDocument();
    document.put("value", new BsonString(SecureStringCodec.ENCRYPTED_PREFIX + "cipher"));

    final BsonDocumentReader reader = new BsonDocumentReader(document);
    reader.readStartDocument();
    reader.readName("value");

    assertThatThrownBy(() -> codec.decode(reader, DecoderContext.builder().build()))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("Failed to decrypt secure value");
  }
}
