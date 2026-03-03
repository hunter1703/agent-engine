package com.agentengine.engine.repository.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
  void writesPlaintextWhenEncryptionFails() {
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
    codec.encode(writer, "secret", EncoderContext.builder().build());
    writer.writeEndDocument();

    assertThat(document.getString("value").getValue()).isEqualTo("secret");
  }

  @Test
  void returnsRawValueWhenDecryptionFails() {
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
    final String decoded = codec.decode(reader, DecoderContext.builder().build());
    reader.readEndDocument();

    assertThat(decoded).isEqualTo(SecureStringCodec.ENCRYPTED_PREFIX + "cipher");
  }
}
