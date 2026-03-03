package com.agentengine.engine.repository.codec;

import com.agentengine.engine.utils.EncryptionService;
import jakarta.enterprise.inject.Instance;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

public class SecureStringCodec implements Codec<String> {


  private final Instance<EncryptionService> encryptionService;

  public SecureStringCodec(Instance<EncryptionService> encryptionService) {
    this.encryptionService = encryptionService;
  }

  @Override
  public void encode(
      final BsonWriter writer, final String value, final EncoderContext encoderContext) {
    if (value == null) {
      writer.writeNull();
      return;
    }
    if (encryptionService != null && encryptionService.isResolvable()) {
      final String encrypted = encryptionService.get().encrypt(value);
      writer.writeString(encrypted);
    } else {
      writer.writeString(value);
    }
  }

  @Override
  public String decode(final BsonReader reader, final DecoderContext decoderContext) {
    if (reader.getCurrentBsonType() == BsonType.NULL) {
      reader.readNull();
      return null;
    }

    if (reader.getCurrentBsonType() == BsonType.STRING) {
      String raw = reader.readString();
      if (encryptionService != null && encryptionService.isResolvable()) {
        try {
          return encryptionService.get().decrypt(raw);
        } catch (Exception e) {
          return raw; // Fallback to raw string if decryption fails
        }
      }
      return raw;
    }

    return null;
  }

  @Override
  public Class<String> getEncoderClass() {
    return String.class;
  }
}
