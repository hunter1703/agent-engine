package com.agentengine.engine.repository.codec;

import com.agentengine.engine.utils.EncryptionService;
import com.agentengine.engine.utils.LazyLoader;
import jakarta.enterprise.inject.Instance;
import org.bson.BsonReader;
import org.bson.BsonType;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecureStringCodec implements Codec<String> {
  private static final Logger LOG = LoggerFactory.getLogger(SecureStringCodec.class);

  private final LazyLoader<EncryptionService> encryptionService;

  public SecureStringCodec(final Instance<EncryptionService> encryptionService) {
    this.encryptionService = new LazyLoader<>(() -> resolveEncryptionService(encryptionService));
  }

  @Override
  public void encode(
      final BsonWriter writer, final String value, final EncoderContext encoderContext) {
    if (value == null) {
      writer.writeNull();
      return;
    }
    final EncryptionService resolvedService = encryptionService.getInstance();
    if (resolvedService != null && resolvedService.isEncryptionEnabled()) {
      try {
        writer.writeString(resolvedService.encrypt(value));
      } catch (Exception e) {
        LOG.error("Failed to encrypt value; aborting write to prevent storing plaintext.", e);
        throw new RuntimeException("Failed to encrypt value", e);
      }
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
      final String raw = reader.readString();
      final EncryptionService resolvedService = encryptionService.getInstance();
      if (resolvedService == null || !resolvedService.isEncryptionEnabled()) {
        return raw;
      }
      try {
        return resolvedService.decrypt(raw);
      } catch (Exception e) {
        LOG.error("Failed to decrypt secure value; aborting read to prevent data corruption.", e);
        throw new RuntimeException("Failed to decrypt secure value", e);
      }
    }

    return null;
  }

  @Override
  public Class<String> getEncoderClass() {
    return String.class;
  }

  private static EncryptionService resolveEncryptionService(
      final Instance<EncryptionService> encryptionService) {
    if (encryptionService == null || !encryptionService.isResolvable()) {
      return null;
    }
    return encryptionService.get();
  }
}
