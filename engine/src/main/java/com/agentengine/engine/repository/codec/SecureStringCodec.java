package com.agentengine.engine.repository.codec;

import com.agentengine.engine.utils.EncryptionService;
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

  static final String ENCRYPTED_PREFIX = "enc::";
  private static final Logger LOG = LoggerFactory.getLogger(SecureStringCodec.class);

  private final EncryptionService encryptionService;

  public SecureStringCodec(final Instance<EncryptionService> encryptionService) {
    if (encryptionService != null && encryptionService.isResolvable()) {
      this.encryptionService = encryptionService.get();
    } else {
      this.encryptionService = null;
    }
  }

  @Override
  public void encode(
      final BsonWriter writer, final String value, final EncoderContext encoderContext) {
    if (value == null) {
      writer.writeNull();
      return;
    }
    if (encryptionService != null && encryptionService.isEncryptionEnabled()) {
      try {
        final String encrypted = encryptionService.encrypt(value);
        writer.writeString(ENCRYPTED_PREFIX + encrypted);
      } catch (Exception e) {
        LOG.debug("Failed to encrypt value; writing plaintext.", e);
        writer.writeString(value);
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
      if (!raw.startsWith(ENCRYPTED_PREFIX)) {
        return raw;
      }
      if (encryptionService == null || !encryptionService.isEncryptionEnabled()) {
        return raw;
      }
      final String payload = raw.substring(ENCRYPTED_PREFIX.length());
      try {
        return encryptionService.decrypt(payload);
      } catch (Exception e) {
        LOG.debug("Failed to decrypt secure value; returning raw value.", e);
        return raw;
      }
    }

    return null;
  }

  @Override
  public Class<String> getEncoderClass() {
    return String.class;
  }
}
